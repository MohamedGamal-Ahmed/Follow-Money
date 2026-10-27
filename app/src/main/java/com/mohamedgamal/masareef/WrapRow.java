package com.mohamedgamal.masareef;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Two natural-width groups: leading copy and trailing value/actions. Overflow moves the latter below. */
final class WrapRow extends ViewGroup {
    private final int gap, lineGap;
    private int squeeze;
    private int minimumLeadingWidth;
    private boolean stacked;
    WrapRow(Context context) { this(context,8); }
    WrapRow(Context context,int horizontalGapDp) {
        super(context);float density=getResources().getDisplayMetrics().density;
        gap=Math.round(horizontalGapDp*density);lineGap=Math.round(8*density);
    }
    /** Width the leading group can give up (e.g. a flexible bar) before the trailing group is moved below. */
    void setSqueeze(int pixels) { squeeze = Math.max(0, pixels); requestLayout(); }
    /** Required leading content (such as an uncut time) takes priority over keeping the value on the same line. */
    void setMinimumLeadingWidth(int pixels) { minimumLeadingWidth = Math.max(0, pixels); requestLayout(); }
    @Override protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(-2, -2); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int limit = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        View first = getChildAt(0), last = getChildAt(1);
        first.measure(limit, free); last.measure(limit, free);
        stacked = first.getMeasuredWidth() + last.getMeasuredWidth() + gap > available;
        if (stacked && minimumLeadingWidth > 0 && available - last.getMeasuredWidth() - gap >= minimumLeadingWidth) {
            first.measure(MeasureSpec.makeMeasureSpec(available - last.getMeasuredWidth() - gap, MeasureSpec.AT_MOST), free);
            stacked = first.getMeasuredWidth() + last.getMeasuredWidth() + gap > available;
        }
        if (stacked && squeeze > 0 && first.getMeasuredWidth() - squeeze + last.getMeasuredWidth() + gap <= available) {
            first.measure(MeasureSpec.makeMeasureSpec(available - last.getMeasuredWidth() - gap, MeasureSpec.AT_MOST), free);
            stacked = first.getMeasuredWidth() + last.getMeasuredWidth() + gap > available;
        }
        int height = stacked ? first.getMeasuredHeight() + lineGap + last.getMeasuredHeight()
                : Math.max(first.getMeasuredHeight(), last.getMeasuredHeight());
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(Math.max(getSuggestedMinimumHeight(),
                height + getPaddingTop() + getPaddingBottom()), heightSpec));
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View first = getChildAt(0), last = getChildAt(1);
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x1 = rtl ? getWidth() - getPaddingRight() - first.getMeasuredWidth() : getPaddingLeft();
        int x2 = rtl ? getPaddingLeft() : getWidth() - getPaddingRight() - last.getMeasuredWidth();
        int y1 = getPaddingTop() + (stacked ? 0 : (height - first.getMeasuredHeight()) / 2);
        int y2 = stacked ? y1 + first.getMeasuredHeight() + lineGap : getPaddingTop() + (height - last.getMeasuredHeight()) / 2;
        first.layout(x1, y1, x1 + first.getMeasuredWidth(), y1 + first.getMeasuredHeight());
        last.layout(x2, y2, x2 + last.getMeasuredWidth(), y2 + last.getMeasuredHeight());
    }
}
