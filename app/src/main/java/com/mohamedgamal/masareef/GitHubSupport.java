package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.util.Locale;

/** User-reviewed GitHub links containing environment metadata only; no ledger or identifiers. */
final class GitHubSupport {
    static final String REPOSITORY="https://github.com/MohamedGamal-Ahmed/Follow-Money-Support";
    private GitHubSupport() {}

    static String version(Context context) {
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            long code=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
            return info.versionName+" ("+code+")";
        } catch(PackageManager.NameNotFoundException error) { return "غير متاح"; }
    }

    static String details(Context context) {
        return "Follow Money: "+version(context)+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+
                "\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nLocale: "+Locale.getDefault().toLanguageTag();
    }

    static Uri bugUri(Context context) {
        return Uri.parse(REPOSITORY+"/issues/new").buildUpon()
                .appendQueryParameter("template","bug_report.yml")
                .appendQueryParameter("app_version",version(context))
                .appendQueryParameter("device",Build.MANUFACTURER+" "+Build.MODEL)
                .appendQueryParameter("android_version",Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")")
                .appendQueryParameter("diagnostics",details(context)).build();
    }

    static Uri featureUri() {
        return Uri.parse(REPOSITORY+"/issues/new").buildUpon().appendQueryParameter("template","feature_request.yml").build();
    }
}
