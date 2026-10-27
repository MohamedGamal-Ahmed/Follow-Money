package com.mohamedgamal.masareef;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.Telephony;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Android side of CaptureDiagnostics: records when each selected notification app was last seen by the listener, and
 * reads the flags and counts for the support text. Stores times, counters and counts only, never message content.
 */
final class CaptureDiagnosticsReader {
    private static final String SEEN_PREFS = "capture_seen";

    /**
     * Called by the listener for every live notification of a selected app, before any processing: if this never moves,
     * the system is not delivering the app's notifications to Follow Money. A failure here never blocks capture.
     */
    static void recordSeen(Context context, String packageName) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE);
            String today = LocalDate.now().toString();
            prefs.edit().putLong("last_" + packageName, System.currentTimeMillis())
                    .putString("day_" + packageName, CaptureDiagnostics.bumpDaily(prefs.getString("day_" + packageName, null), today)).apply();
        } catch (RuntimeException ignored) { /* diagnostics only */ }
    }

    /** The current capture state. Each part is read on its own: one that fails shows as unavailable, never as a crash. */
    static CaptureDiagnostics.Snapshot collect(Context context) {
        CaptureDiagnostics.Snapshot snapshot = new CaptureDiagnostics.Snapshot();
        try { snapshot.notificationAccess = hasNotificationAccess(context); } catch (RuntimeException ignored) { /* stays off */ }
        snapshot.listenerConnected = PaymentNotificationListener.isConnected();
        try {
            Set<String> selected = new TreeSet<>(context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
                    .getStringSet("sources", Collections.emptySet()));
            SharedPreferences seen = context.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE);
            String today = LocalDate.now().toString();
            PackageManager packages = context.getPackageManager();
            for (String packageName : selected) {
                String label = packageName;
                boolean installed = true;
                try { label = packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString(); }
                catch (PackageManager.NameNotFoundException missing) { installed = false; }
                snapshot.sources.add(new CaptureDiagnostics.Source(packageName, label, installed, seen.getLong("last_" + packageName, 0),
                        CaptureDiagnostics.countOn(seen.getString("day_" + packageName, null), today)));
            }
        } catch (RuntimeException ignored) { /* the list stays as far as it got */ }
        try { snapshot.defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context); } catch (RuntimeException ignored) { /* none */ }
        try {
            snapshot.smsPermission = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException ignored) { /* stays off */ }
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) snapshot.batteryIgnored = power.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (RuntimeException ignored) { /* unknown */ }
        try {
            android.database.sqlite.SQLiteDatabase sql = AppResources.database(context).getReadableDatabase();
            int[] missed = ShadowStore.ununderstoodHealth(sql);
            snapshot.missedRows = missed[0]; snapshot.missedUnreadable = missed[1];
            int[] live = ShadowStore.liveCounts(sql, System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000);
            snapshot.liveSms = live[0]; snapshot.liveNotification = live[1]; snapshot.liveFailed = live[2];
        } catch (RuntimeException ignored) { /* the two lines say unavailable */ }
        return snapshot;
    }

    static String text(Context context) {
        return CaptureDiagnostics.format(collect(context), ZoneId.systemDefault());
    }

    /** The same check the settings screen uses for «الوصول للإشعارات». */
    static boolean hasNotificationAccess(Context context) {
        ComponentName component = new ComponentName(context, PaymentNotificationListener.class);
        if (Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.isNotificationListenerAccessGranted(component);
        }
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(component.flattenToString());
    }

    private CaptureDiagnosticsReader() {}
}
