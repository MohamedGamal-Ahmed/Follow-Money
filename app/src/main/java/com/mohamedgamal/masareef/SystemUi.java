package com.mohamedgamal.masareef;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Keep native controls clear of system bars, cutouts and the keyboard when edge-to-edge is mandatory. */
final class SystemUi {
    private SystemUi() {}

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
