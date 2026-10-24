package com.mohamedgamal.masareef;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Keep native controls clear of system bars, cutouts and the keyboard when edge-to-edge is mandatory. */
final class SystemUi {
    private SystemUi() {}

    /** Modern icon appearance, with legacy flags/colors confined to the platform versions that need them. */
    @SuppressWarnings("deprecation")
    static void appearance(android.view.Window window, boolean dark, int statusColor, int navigationColor) {
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 27) mask |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility((decor.getSystemUiVisibility() & ~mask) | (dark ? 0 : mask));
        }
        if (Build.VERSION.SDK_INT < 35) {
            window.setStatusBarColor(statusColor);
            window.setNavigationBarColor(navigationColor);
        }
    }

    static void apply(Activity activity,View root) {
        apply(activity.getWindow(), root);
    }

    static void apply(android.view.Window window,View root) {
        if(Build.VERSION.SDK_INT<35) return;
        window.setDecorFitsSystemWindows(false);
        int left=root.getPaddingLeft(),top=root.getPaddingTop(),right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
