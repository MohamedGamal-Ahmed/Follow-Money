package com.mohamedgamal.masareef;

import android.app.Notification;
import android.content.Context;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Set;
import java.util.Locale;

/** Reads only notifications from apps explicitly selected by the user. */
public final class PaymentNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) for (StatusBarNotification item : active) submit(item);
    }

    @Override public void onNotificationPosted(StatusBarNotification item) {
        submit(item);
    }

    /** Only the cheap package filter stays on the callback thread; the ledger work is queued. */
    private void submit(StatusBarNotification item) {
        String packageName = acceptedPackage(item);
        if (packageName == null) return;
        final Context app = getApplicationContext();
        CaptureQueue.submit(() -> process(app, item, packageName));
    }

    /** The package the user selected for this notification, or null when it must be ignored. */
    private String acceptedPackage(StatusBarNotification item) {
        if (item == null || item.getPackageName().equals(getPackageName())) return null;
        String packageName = item.getPackageName();
        String smsApp = Telephony.Sms.getDefaultSmsPackage(this);
        if (packageName.equals(smsApp) || packageName.toLowerCase(Locale.ROOT).contains("truecaller")) return null;
        Set<String> selected = getSharedPreferences("notification_settings", MODE_PRIVATE)
                .getStringSet("sources", java.util.Collections.emptySet());
        if (!selected.contains(packageName)) return null;
        return packageName;
    }

    private static void process(Context context, StatusBarNotification item, String packageName) {
        try {
            Notification notification = item.getNotification();
            if (notification == null || notification.extras == null ||
                    (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text == null) return;
            String body = (title == null ? "" : title + " ") + text.toString();
            String label;
            try { label = context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
            catch (Exception ignored) { label = packageName; }
            try (ExpenseDb db = new ExpenseDb(context)) {
                ExpenseDb.Transaction before = db.latestInserted();
                boolean added = db.addNotification(packageName, label, body, item.getPostTime());
                if (added || CommitmentHint.parse(label, body, item.getPostTime()) != null ||
                        CommitmentHint.completedPlanKey(label, body) != null)
                    SmsReceiver.notifyDataChanged(context);
                else DailyRefreshReceiver.refresh(context);
                if(System.currentTimeMillis()-item.getPostTime()<10*60*1000)CaptureNotifier.notifyEvent(context,db.lastCaptureEvent());
                if (added) {
                    ExpenseDb.Transaction after = db.lastCaptured();
                    // Notifications already on screen when access is granted are old; only announce new ones.
                    if (after != null && (before == null || after.id > before.id) &&
                            System.currentTimeMillis() - item.getPostTime() < 10 * 60 * 1000)
                        CaptureNotifier.notify(context, after);
                }
            }
        } catch (RuntimeException error) {
            // A message the app cannot handle must never crash the listener (and with it the whole app).
            android.util.Log.e("FollowMoney", "Notification skipped: " + error.getClass().getSimpleName());
        }
    }
}
