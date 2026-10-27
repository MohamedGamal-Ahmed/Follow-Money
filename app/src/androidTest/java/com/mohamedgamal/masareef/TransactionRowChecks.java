package com.mohamedgamal.masareef;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Transaction rows at any font scale: every chip and time is one whole line, inside the screen. */
final class TransactionRowChecks {
    interface Check { void ok(boolean value, String reason); }
    private TransactionRowChecks() {}

    /** Returns how many chip/time views were checked. */
    static int verify(View root, Check check) {
        int[] count = {0};
        walk(root, root.getWidth(), check, count);
        return count[0];
    }

    private static void walk(View view, int screenWidth, Check check, int[] count) {
        if ("transaction_meta".equals(view.getTag())) {
            ViewGroup meta = (ViewGroup) view;
            for (int i = 0; i < meta.getChildCount(); i++) {
                if (!(meta.getChildAt(i) instanceof TextView)) continue;
                TextView item = (TextView) meta.getChildAt(i);
                android.text.Layout layout = item.getLayout();
                check.ok(layout != null && layout.getLineCount() == 1, "Row chip/time on one line: " + item.getText());
                int[] xy = new int[2]; item.getLocationOnScreen(xy);
                check.ok(xy[0] >= 0 && xy[0] + item.getWidth() <= screenWidth, "Row chip/time inside the screen: " + item.getText());
                int fullWidth = meta.getWidth();
                View copy = (View)meta.getParent(), leading = (View)copy.getParent();
                if (leading.getParent() instanceof WrapRow) {
                    WrapRow row = (WrapRow)leading.getParent();
                    fullWidth = row.getWidth() - row.getPaddingLeft() - row.getPaddingRight()
                            - (leading.getWidth() - copy.getWidth());
                }
                int desired = (int)Math.ceil(android.text.Layout.getDesiredWidth(item.getText(),item.getPaint()))
                        + item.getCompoundPaddingLeft() + item.getCompoundPaddingRight();
                if (layout != null && ("transaction_time".equals(item.getTag()) || desired <= fullWidth))
                    check.ok(layout.getEllipsisCount(0) == 0, "Row item that fits the full row is never cut: " + item.getText());
                count[0]++;
            }
            return;
        }
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) walk(((ViewGroup) view).getChildAt(i), screenWidth, check, count);
    }
}
