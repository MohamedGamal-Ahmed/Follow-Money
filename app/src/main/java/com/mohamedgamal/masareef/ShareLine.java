package com.mohamedgamal.masareef;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * A statement sub-row's second line: detail, share bar, percent. The bar flexes between its minimum and maximum width
 * so the line fits next to the amount; only when even the minimum does not fit do the bar and percent move below.
 * Children, in order: detail (may be GONE), bar, percent.
 */
final class ShareLine extends ViewGroup {
    private final int gap, lineGap, barHeight, minBar, maxBar;
    private boolean stacked;
    static final int MIN_BAR_DP = 48, MAX_BAR_DP = 90;
    ShareLine(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        gap = Math.round(6 * density); lineGap = Math.round(2 * density); barHeight = Math.round(5 * density);
        minBar = Math.round(MIN_BAR_DP * density); maxBar = Math.round(MAX_BAR_DP * density);
    }
    /** How much narrower the line can get by shrinking the bar; a WrapRow uses it before stacking the amount. */
    int slack() { return maxBar - minBar; }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View detail = getChildAt(0), bar = getChildAt(1), percent = getChildAt(2);
        int available = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE / 2
                : Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
        int limit = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        boolean hasDetail = detail.getVisibility() != GONE;
        if (hasDetail) detail.measure(limit, free);
        percent.measure(limit, free);
        int dw = hasDetail ? detail.getMeasuredWidth() : 0, dh = hasDetail ? detail.getMeasuredHeight() : 0;
        int pw = percent.getMeasuredWidth(), ph = percent.getMeasuredHeight();
        int fixed = (hasDetail ? dw + gap : 0) + gap + pw;
        int barWidth, width, height;
        stacked = available - fixed < minBar;
        if (!stacked) {
            barWidth = Math.min(maxBar, available - fixed); width = fixed + barWidth; height = Math.max(Math.max(dh, ph), barHeight);
        } else {
            barWidth = Math.min(maxBar, Math.max(minBar, available - gap - pw)); width = Math.max(dw, barWidth + gap + pw);
            height = dh + (hasDetail ? lineGap : 0) + Math.max(ph, barHeight);
        }
        bar.measure(MeasureSpec.makeMeasureSpec(barWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSize(width + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightSpec));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View detail = getChildAt(0), bar = getChildAt(1), percent = getChildAt(2);
        boolean hasDetail = detail.getVisibility() != GONE;
        int dh = hasDetail ? detail.getMeasuredHeight() : 0;
        int lineTop = getPaddingTop(), cursor = 0;
        int lineHeight = stacked ? Math.max(percent.getMeasuredHeight(), barHeight) : getHeight() - getPaddingTop() - getPaddingBottom();
        if (hasDetail) {
            place(detail, cursor, lineTop + (stacked ? 0 : (lineHeight - dh) / 2));
            if (stacked) lineTop += dh + lineGap; else cursor += detail.getMeasuredWidth() + gap;
        }
        if (stacked) cursor = 0;
        place(bar, cursor, lineTop + (lineHeight - barHeight) / 2); cursor += bar.getMeasuredWidth() + gap;
        place(percent, cursor, lineTop + (lineHeight - percent.getMeasuredHeight()) / 2);
    }
    private void place(View child, int offset, int top) {
        int w = child.getMeasuredWidth();
        int x = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - getPaddingRight() - offset - w : getPaddingLeft() + offset;
        child.layout(x, top, x + w, top + child.getMeasuredHeight());
    }
}
