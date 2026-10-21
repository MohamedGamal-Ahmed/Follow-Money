package com.mohamedgamal.masareef;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

/** Keeps AlertDialog's button/cancel/listener contract while presenting a bottom sheet. */
final class UiSheets {
    private UiSheets() {}

    static AlertDialog prepare(Activity activity, AlertDialog dialog, UiTheme theme, Typeface font) {
        Window window = dialog.getWindow();
        window.setGravity(Gravity.BOTTOM);
        window.setBackgroundDrawable(new UiComponents(activity, theme, font).shape(theme.surface, 0, UiTheme.SHEET_RADIUS));
        window.setNavigationBarColor(theme.surface);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View opener = activity.getCurrentFocus();
        View decor = window.getDecorView();
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                // The framework installs its content before invoking OnShowListener. Never replace that listener:
                // callers use it to keep invalid forms open and install neutral-button actions.
                decor.post(() -> {
                    if (!dialog.isShowing()) return;
                    UiComponents ui = new UiComponents(activity, theme, font);
                    android.widget.FrameLayout content = decor.findViewById(android.R.id.content);
                    if (content != null && content.getChildCount() == 1 && !(content.getChildAt(0) instanceof HeightLimit)) {
                        View original = content.getChildAt(0); ViewGroup.LayoutParams params = original.getLayoutParams();
                        content.removeView(original);
                        HeightLimit capped = new HeightLimit(activity); capped.addView(original, params);
                        content.addView(capped, new android.widget.FrameLayout.LayoutParams(-1, -2));
                    }
                    styleText(decor, theme, font);
                    int panelId = activity.getResources().getIdentifier("buttonPanel", "id", "android");
                    View oldPanel = decor.findViewById(panelId);
                    LinearLayout footer = new ActionFooter(activity); footer.setOrientation(LinearLayout.VERTICAL);
                    footer.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(12)); footer.setId(panelId);
                    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE}) {
                        Button button = dialog.getButton(which);
                        if (button == null) continue;
                        boolean primary = which == AlertDialog.BUTTON_POSITIVE;
                        String text = button.getText().toString();
                        ui.styleButton(button, primary, primary && (text.startsWith("حذف") || text.startsWith("أرشفة")));
                        if (oldPanel != null && button.getParent() instanceof ViewGroup) {
                            ((ViewGroup) button.getParent()).removeView(button);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = ui.dp(6);
                            footer.addView(button, lp);
                        }
                    }
                    if (oldPanel != null && oldPanel.getParent() instanceof ViewGroup) {
                        ViewGroup parent = (ViewGroup) oldPanel.getParent();
                        int index = parent.indexOfChild(oldPanel); ViewGroup.LayoutParams params = oldPanel.getLayoutParams();
                        footer.setVisibility(oldPanel.getVisibility()); parent.removeView(oldPanel); parent.addView(footer, index, params);
                    }
                    decor.setPadding(0, 0, 0, 0);
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    SystemUi.apply(window, decor);
                    if (Build.VERSION.SDK_INT >= 28) decor.setAccessibilityPaneTitle(dialogTitle(decor));
                });
            }
            @Override public void onViewDetachedFromWindow(View view) {
                if (opener != null && opener.isAttachedToWindow()) opener.requestFocus();
            }
        });
        return dialog;
    }

    /** AlertDialogLayout must reserve every action before giving the remaining height to scrolling content. */
    private static final class ActionFooter extends LinearLayout {
        private int requiredHeight;
        ActionFooter(Activity activity) { super(activity); }
        @Override public int getMinimumHeight() { return requiredHeight; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            requiredHeight = getMeasuredHeight();
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    /** Measure against this dialog's available window, including IME, rather than a fixed screen height. */
    private static final class HeightLimit extends android.widget.FrameLayout {
        private final Activity activity;
        HeightLimit(Activity activity) { super(activity); this.activity = activity; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int available = getResources().getDisplayMetrics().heightPixels;
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                android.graphics.Insets safe = metrics.getWindowInsets().getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                available = metrics.getBounds().height() - safe.top - safe.bottom;
            }
            int maximum = Math.round(available * .80f);
            if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED)
                maximum = Math.min(maximum, View.MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(Math.max(1, maximum), View.MeasureSpec.AT_MOST));
        }
    }

    private static CharSequence dialogTitle(View view) {
        int titleId = view.getResources().getIdentifier("alertTitle", "id", "android");
        android.widget.TextView title = view.findViewById(titleId);
        return title == null ? "خيارات" : title.getText();
    }

    private static void styleText(View view, UiTheme theme, Typeface font) {
        if (view instanceof android.widget.TextView && !(view instanceof Button)) {
            android.widget.TextView text = (android.widget.TextView) view;
            text.setTypeface(font, text.getTypeface() == null ? Typeface.NORMAL : text.getTypeface().getStyle());
            // Custom content already has semantic colors. Only style framework-owned title/message text.
            if (view.getId() == android.R.id.message || "alertTitle".equals(resourceName(view))) text.setTextColor(theme.ink);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleText(group.getChildAt(i), theme, font);
        }
    }

    private static String resourceName(View view) {
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (android.content.res.Resources.NotFoundException ignored) { return ""; }
    }
}
