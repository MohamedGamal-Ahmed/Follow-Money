package com.mohamedgamal.masareef;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Short whole items (chips, a time) in reading order. An item that does not fit moves to the next line as a whole;
 * it is never split. An item wider than the row gets the row's width, so its own ellipsis applies.
 */
final class FlowRow extends ViewGroup {
    private final int gap, lineGap;
    FlowRow(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        gap = Math.round(6 * density); lineGap = Math.round(2 * density);
    }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int available = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 2
                : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int limit = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int x = 0, lineHeight = 0, height = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(limit, free);
            int w = child.getMeasuredWidth();
            if (x > 0 && x + gap + w > available) { height += lineHeight + lineGap; x = 0; lineHeight = 0; }
            x += (x > 0 ? gap : 0) + w; widest = Math.max(widest, x); lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
        }
        height += lineHeight;
        setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = getWidth() - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int start = 0, x = 0, y = getPaddingTop(), lineHeight = 0;
        // Lay out line by line so each line's items can be centred on that line's height.
        for (int i = 0; i <= getChildCount(); i++) {
            View child = i < getChildCount() ? getChildAt(i) : null;
            if (child != null && child.getVisibility() == GONE) continue;
            int w = child == null ? 0 : child.getMeasuredWidth();
            if (child == null || x > 0 && x + gap + w > available) {
                int cursor = 0;
                for (int j = start; j < i; j++) {
                    View placed = getChildAt(j);
                    if (placed.getVisibility() == GONE) continue;
                    int pw = placed.getMeasuredWidth(), ph = placed.getMeasuredHeight();
                    int px = rtl ? getWidth() - getPaddingRight() - cursor - pw : getPaddingLeft() + cursor;
                    int py = y + (lineHeight - ph) / 2;
                    placed.layout(px, py, px + pw, py + ph); cursor += pw + gap;
                }
                if (child == null) break;
                y += lineHeight + lineGap; start = i; x = 0; lineHeight = 0;
            }
            x += (x > 0 ? gap : 0) + w; lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
        }
    }
}
