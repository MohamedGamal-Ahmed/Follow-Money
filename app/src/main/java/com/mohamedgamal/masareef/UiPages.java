package com.mohamedgamal.masareef;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.ArrayList;

/** In-activity form stack. Models own drafts; Views can be rebuilt without becoming the source of truth. */
final class UiPages {
    abstract static class Page {
        final String route, title;
        final Bundle draft, original;
        int scrollY;
        String focusTag;
        int selectionStart = -1, selectionEnd = -1;
        Page(String route, String title, Bundle draft, Bundle original) {
            this.route = route; this.title = title; this.draft = new Bundle(draft); this.original = new Bundle(original);
        }
        abstract void build(LinearLayout body, UiComponents ui);
        abstract boolean save();
        String saveLabel() { return "حفظ"; }
        void onSaved() {}
        // Implementations opt in only for non-sensitive drafts. Password forms must never opt in.
        boolean restoreAfterRecreation() { return false; }
        /** Long text: no ✓ in the header; the footer button just closes. */
        boolean readOnly() { return false; }
        boolean dirty() {
            if (!draft.keySet().equals(original.keySet())) return true;
            for (String key : draft.keySet()) if (!java.util.Objects.equals(draft.get(key), original.get(key))) return true;
            return false;
        }
    }
    interface Factory { Page restore(String route, Bundle draft, Bundle original); }

    private final Activity activity;
    private final UiComponents ui;
    private final Typeface font;
    private final Runnable returnToParent;
    private final ArrayList<Page> stack = new ArrayList<>();
    private ScrollView scroller;
    private View root;
    private boolean confirming;

    UiPages(Activity activity, UiTheme theme, Typeface font, Runnable returnToParent) {
        this.activity = activity; this.ui = new UiComponents(activity, theme, font);
        this.font = font; this.returnToParent = returnToParent;
    }
    boolean isOpen() { return !stack.isEmpty(); }
    private Page current() { return stack.get(stack.size() - 1); }
    private void captureScroll() {
        if (!isOpen() || scroller == null) return;
        Page page = current(); page.scrollY = scroller.getScrollY();
        View focused = root == null ? null : root.findFocus();
        if (focused != null && focused.getTag() instanceof String) {
            page.focusTag = (String) focused.getTag();
            if (focused instanceof android.widget.EditText) {
                page.selectionStart = ((android.widget.EditText) focused).getSelectionStart();
                page.selectionEnd = ((android.widget.EditText) focused).getSelectionEnd();
            }
        }
    }

    void push(Page page) { captureScroll(); stack.add(page); root = null; scroller = null; show(); }
    void rebuild() { captureScroll();root=null;scroller=null;show(); }

    void show() {
        if (!isOpen()) return;
        // A normal ledger render or unlock does not replace the attached form or its input focus/cursor.
        if (root != null && root.isAttachedToWindow()) return;
        captureScroll();
        Page page = current();
        LinearLayout layout = new LinearLayout(activity); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); layout.setBackgroundColor(ui.theme.background);
        layout.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        layout.setTag("ui_form_page:" + page.route);
        LinearLayout header = ui.header(page.title, this::back, page.readOnly() ? null : this::save);
        if (!page.readOnly()) header.getChildAt(header.getChildCount()-1).setContentDescription(page.saveLabel());
        layout.addView(header);
        scroller = new ScrollView(activity); scroller.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(16)); scroller.addView(body);
        page.build(body, ui);
        LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, -2); footer.topMargin = ui.dp(20);
        android.widget.Button submit = ui.button(page.saveLabel(), true, false, this::save);
        submit.setId(android.R.id.button1); submit.setTag("ui_page_save");
        body.addView(submit, footer);
        layout.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        activity.setContentView(layout); root = layout; SystemUi.apply(activity, layout);
        if (android.os.Build.VERSION.SDK_INT >= 28) layout.setAccessibilityPaneTitle(page.title);
        ScrollView currentScroller = scroller;
        currentScroller.post(() -> currentScroller.scrollTo(0, page.scrollY));
        if (page.focusTag != null) {
            View focused = layout.findViewWithTag(page.focusTag);
            if (focused != null) focused.requestFocus();
            if (focused instanceof android.widget.EditText && page.selectionStart >= 0 && page.selectionEnd >= 0) {
                android.widget.EditText field = (android.widget.EditText) focused;
                field.setSelection(Math.min(page.selectionStart, field.length()), Math.min(page.selectionEnd, field.length()));
            }
        }
    }

    void save() {
        if (!isOpen()) return;
        Page page = current();
        if (page.save()) { pop(); page.onSaved(); }
    }

    void back() { leave(false, this::pop); }

    /** Finish an explicitly committed flow, including its selection page, without a discard prompt. */
    void finishThrough(String route) {
        int index=-1;for(int i=stack.size()-1;i>=0;i--)if(route.equals(stack.get(i).route)){index=i;break;}
        if(index<0)return;
        while(stack.size()>index)stack.remove(stack.size()-1);
        root=null;scroller=null;if(isOpen())show();else returnToParent.run();
    }

    void leaveAll(Runnable next) {
        leave(true, () -> { stack.clear(); root = null; scroller = null; returnToParent.run(); next.run(); });
    }

    private void leave(boolean all, Runnable accept) {
        boolean dirty = false;
        for (Page page : stack) if ((all || page == current()) && page.dirty()) dirty = true;
        if (!dirty) { accept.run(); return; }
        if (confirming) return;
        confirming = true;
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("تجاهل التعديلات؟")
                .setMessage("فيه تعديلات لسه ما اتحفظتش.")
                .setPositiveButton("تجاهل التعديلات", (d, which) -> accept.run())
                .setNegativeButton("كمّل التعديل", null).create();
        dialog.setOnDismissListener(d -> confirming = false);
        UiSheets.prepare(activity, dialog, ui.theme, font).show();
    }

    private void pop() {
        if (!isOpen()) return;
        stack.remove(stack.size() - 1); root = null; scroller = null;
        if (isOpen()) show(); else returnToParent.run();
    }

    Bundle snapshot() {
        captureScroll(); Bundle out = new Bundle(); ArrayList<Bundle> pages = new ArrayList<>();
        for (Page page : stack) {
            // A non-restorable parent ends the restorable prefix, so children never restore without it.
            if (!page.restoreAfterRecreation()) break;
            Bundle item = new Bundle(); item.putString("route", page.route);
            item.putBundle("draft", new Bundle(page.draft)); item.putBundle("original", new Bundle(page.original));
            item.putInt("scroll", page.scrollY); pages.add(item);
            item.putString("focus", page.focusTag); item.putInt("selection_start", page.selectionStart); item.putInt("selection_end", page.selectionEnd);
        }
        out.putParcelableArrayList("pages", pages); return out;
    }

    void restore(Bundle saved, Factory factory) {
        if (saved == null) return;
        ArrayList<Bundle> pages = saved.getParcelableArrayList("pages");
        if (pages == null) return;
        for (Bundle item : pages) {
            Bundle draft = item.getBundle("draft"), original = item.getBundle("original");
            if (draft == null || original == null) break;
            Page page = factory.restore(item.getString("route", ""), draft, original);
            if (page == null || !page.restoreAfterRecreation()) break;
            page.scrollY = item.getInt("scroll", 0); page.focusTag = item.getString("focus");
            page.selectionStart = item.getInt("selection_start", -1); page.selectionEnd = item.getInt("selection_end", -1); stack.add(page);
        }
    }

}
