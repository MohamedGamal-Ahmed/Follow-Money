package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** Native, RTL controls. Heights are minimums so font scaling can grow their content. */
final class UiComponents {
    private final Context context;
    final UiTheme theme;
    private final Typeface font;

    UiComponents(Context context, UiTheme theme, Typeface font) {
        this.context = context; this.theme = theme; this.font = font;
    }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    android.graphics.drawable.Drawable interactive(int fill, int border, int radius) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focus = shape(fill, 0, radius);
        focus.setStroke(dp(2), fill == theme.accent ? theme.onPrimary : fill == theme.danger ? theme.onDanger : theme.accent);
        states.addState(new int[]{android.R.attr.state_focused}, focus);
        states.addState(new int[]{}, shape(fill, border, radius));
        return new RippleDrawable(ColorStateList.valueOf((theme.accent & 0x00ffffff) | 0x33000000), states, null);
    }

    TextView label(String title, int size, int color) {
        TextView label = new TextView(context); label.setText(title); label.setTextSize(size);
        label.setTypeface(font); label.setTextColor(color); label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        label.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        return label;
    }

    EditText field(String title, LinearLayout parent) {
        // A full-width caption inside a horizontal row takes the whole row and pushes the input (and any
        // sibling such as a currency picker) off screen. A row gets one column per field instead.
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout column = new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
            parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
            parent = column;
        }
        TextView caption = label(title, 13, theme.muted); caption.setTypeface(font, Typeface.BOLD);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(14);
        parent.addView(caption, cp);
        EditText input = new EditText(context); input.setId(View.generateViewId()); input.setTag(title);
        // Page models own drafts; framework state restore could map stale text onto a regenerated id.
        input.setSaveEnabled(false);
        caption.setLabelFor(input.getId()); input.setHint(title); input.setSingleLine(true);
        input.setTypeface(font); input.setTextSize(UiTheme.BODY); input.setTextColor(theme.ink);
        input.setHintTextColor(theme.muted); input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); input.setMinHeight(dp(UiTheme.FIELD_HEIGHT));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(interactive(theme.surface, theme.fieldOutline, UiTheme.FIELD_RADIUS));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2); ip.topMargin = dp(6);
        parent.addView(input, ip);
        return input;
    }

    void caption(String title, LinearLayout parent) {
        TextView caption = label(title, UiTheme.CAPTION, theme.muted);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16); lp.bottomMargin = dp(6); parent.addView(caption, lp);
        if (android.os.Build.VERSION.SDK_INT >= 28) caption.setAccessibilityHeading(true);
    }

    /** Scroll instead of squeezing the labels at 200%; native radio semantics include checked state. */
    RadioGroup choices(String[] labels, int[] icons, int selected, java.util.function.IntConsumer changed, LinearLayout parent) {
        HorizontalScrollView scroll = new HorizontalScrollView(context); scroll.setHorizontalScrollBarEnabled(false);
        RadioGroup group = new RadioGroup(context); group.setOrientation(LinearLayout.HORIZONTAL);
        group.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); scroll.addView(group);
        // Redesign: one rounded track; the chosen option is a raised pill inside it.
        group.setPadding(dp(4), dp(4), dp(4), dp(4)); group.setBackground(shape(theme.segment, 0, 99));
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(context); option.setId(View.generateViewId()); option.setText(labels[i]); option.setSaveEnabled(false);
            option.setTag(i); option.setButtonDrawable(null); option.setTypeface(font); option.setTextSize(UiTheme.BODY);
            option.setMinHeight(dp(UiTheme.TOUCH)); option.setGravity(Gravity.CENTER); option.setPadding(dp(16),dp(12),dp(16),dp(12));
            if (icons != null) {
                android.graphics.drawable.Drawable icon = context.getDrawable(icons[i]);
                if (icon != null) { icon = icon.mutate(); icon.setBounds(0,0,dp(22),dp(22));
                    option.setCompoundDrawablesRelative(null,icon,null,null); option.setCompoundDrawablePadding(dp(6)); }
            }
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(-2,-2); lp.setMargins(dp(2),0,dp(2),0);
            group.addView(option,lp);
        }
        Runnable style = () -> {
            for (int i = 0; i < group.getChildCount(); i++) {
                RadioButton option = (RadioButton) group.getChildAt(i); boolean checked = option.isChecked();
                int ink = checked ? theme.segmentOnInk : theme.muted;
                option.setTextColor(ink); option.setTypeface(font, checked ? Typeface.BOLD : Typeface.NORMAL);
                option.setBackground(interactive(checked ? theme.segmentOn : theme.segment, 0, 99));
                for (android.graphics.drawable.Drawable icon : option.getCompoundDrawablesRelative()) if (icon != null) icon.setTint(ink);
            }
        };
        if (selected >= 0 && selected < labels.length) ((RadioButton)group.getChildAt(selected)).setChecked(true);
        style.run(); group.setOnCheckedChangeListener((g,id)->{style.run();View option=g.findViewById(id);
            if(option!=null)changed.accept((Integer)option.getTag());});
        parent.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        if (selected >= 0 && selected < labels.length) scroll.post(()->scroll.scrollTo(
                Math.max(0,group.getChildAt(selected).getRight()-scroll.getWidth()),0));
        return group;
    }

    EditText amount(String value, LinearLayout parent) {
        EditText amount = field("المبلغ",parent); amount.setTag("transaction_amount"); amount.setText(value);
        amount.setTextSize(UiTheme.AMOUNT); amount.setGravity(Gravity.CENTER); amount.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return amount;
    }

    /** The entire card is a single named keyboard/screen-reader action; children are decorative. */
    LinearLayout choiceCard(String title, String value, View badge, Runnable action, LinearLayout parent) {
        LinearLayout card = new LinearLayout(context); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setMinimumHeight(dp(UiTheme.TOUCH));
        card.setBackground(interactive(theme.surface,theme.fieldOutline,UiTheme.FIELD_RADIUS));card.setMinimumHeight(dp(56));
        if(badge!=null){badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(badge,new LinearLayout.LayoutParams(dp(40),dp(40)));}
        LinearLayout copy = new LinearLayout(context); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(10),0,dp(10),0);
        copy.addView(label(title,UiTheme.CAPTION,theme.muted)); copy.addView(label(value,UiTheme.BODY,theme.ink));
        card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView arrow=label("‹",UiTheme.TITLE,theme.muted);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(arrow);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setContentDescription(title+"، "+value);card.setFocusable(true);card.setOnClickListener(v->action.run());
        card.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);parent.addView(card,lp);return card;
    }

    Button button(String title, boolean primary, boolean destructive, Runnable action) {
        Button button = new Button(context); button.setText(title);
        styleButton(button, primary, destructive); button.setOnClickListener(v -> action.run());
        return button;
    }

    void styleButton(Button button, boolean primary, boolean destructive) {
        int fill = primary ? destructive ? theme.danger : theme.accent : theme.soft;
        int ink = primary ? destructive ? theme.onDanger : theme.onPrimary : theme.ink;
        button.setAllCaps(false); button.setTypeface(font, Typeface.BOLD);
        button.setTextSize(UiTheme.BODY); button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{theme.muted, ink}));
        button.setSingleLine(false); button.setMinHeight(dp(UiTheme.FIELD_HEIGHT)); button.setMinimumHeight(dp(UiTheme.FIELD_HEIGHT));
        button.setPadding(dp(16), dp(12), dp(16), dp(12)); button.setStateListAnimator(null); button.setElevation(0);
        button.setBackground(interactive(fill, 0, UiTheme.BUTTON_RADIUS));
        button.setFocusable(true);
    }

    ImageButton icon(int resource, String description, Runnable action) {
        ImageButton button = new ImageButton(context); button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(theme.ink)); button.setContentDescription(description);
        button.setPadding(dp(12), dp(12), dp(12), dp(12)); button.setMinimumWidth(dp(UiTheme.TOUCH));
        button.setMinimumHeight(dp(UiTheme.TOUCH)); button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setBackground(interactive(theme.surface, 0, 14));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    LinearLayout header(String title, Runnable back, Runnable save) {
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        header.addView(icon(R.drawable.ic_chevron_back, "رجوع", back), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView caption = label(title, UiTheme.TITLE, theme.ink); caption.setTypeface(font, Typeface.BOLD);
        caption.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (android.os.Build.VERSION.SDK_INT >= 28) caption.setAccessibilityHeading(true);
        header.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        if (save != null) header.addView(icon(R.drawable.ic_check, "حفظ", save), new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }
}
