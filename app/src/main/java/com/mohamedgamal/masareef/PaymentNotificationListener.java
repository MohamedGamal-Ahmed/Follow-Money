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
    private static volatile java.lang.ref.WeakReference<PaymentNotificationListener> connected = new java.lang.ref.WeakReference<>(null);
    @Override public void onListenerConnected() {
        connected = new java.lang.ref.WeakReference<>(this);
        replaySelectedActive();
    }

    @Override public void onListenerDisconnected() {
        if (connected.get() == this) connected.clear();
    }

    @Override public void onDestroy() {
        if (connected.get() == this) connected.clear();
        super.onDestroy();
    }

    /** Selection can happen after the service connected and ignored an already-visible notification. */
    static void refreshSelectedSources(Context context) {
        try {
            PaymentNotificationListener listener = connected.get();
            if (listener != null) listener.replaySelectedActive();
            else requestRebind(new android.content.ComponentName(context, PaymentNotificationListener.class));
        } catch (RuntimeException unavailable) {
            android.util.Log.w("FollowMoney", "Notification reconnect unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

    private void replaySelectedActive() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) submit(item);
        } catch (RuntimeException unavailable) {
            android.util.Log.w("FollowMoney", "Notification replay unavailable: " + unavailable.getClass().getSimpleName());
        }
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

    /** Custom notifications (including Telda) can put the entire payment in the title. */
    static String notificationBody(Notification notification) {
        if (notification == null || notification.extras == null ||
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        String title = notificationText(notification, Notification.EXTRA_TITLE);
        String text = notificationText(notification, Notification.EXTRA_BIG_TEXT);
        if (text.isEmpty()) text = notificationText(notification, Notification.EXTRA_TEXT);
        if (title.isEmpty()) return text.isEmpty() ? null : text;
        return text.isEmpty() || title.equals(text) ? title : title + " " + text;
    }

    /** The six separate extras for the shadow pipeline; absent stays null, empty stays "". Legacy uses notificationBody. */
    static MessageNormalizer.Raw shadowInput(String packageName, Notification notification) {
        if (notification == null || notification.extras == null) return null;
        CharSequence[] lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        String joined = null;
        if (lines != null) {
            StringBuilder out = new StringBuilder();
            for (CharSequence line : lines) { if (out.length() > 0) out.append('\n'); out.append(line == null ? "" : line); }
            joined = out.toString();
        }
        return MessageNormalizer.Raw.notification(packageName, notification.flags,
                extra(notification, Notification.EXTRA_TITLE), extra(notification, Notification.EXTRA_TEXT),
                extra(notification, Notification.EXTRA_BIG_TEXT), joined,
                extra(notification, Notification.EXTRA_SUB_TEXT), extra(notification, Notification.EXTRA_SUMMARY_TEXT));
    }

    private static String extra(Notification notification, String key) {
        CharSequence value = notification.extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    private static String notificationText(Notification notification, String key) {
        CharSequence value = notification.extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }

    /** The shadow input with this delivery's identity (Android key + original post time), or null. */
    private static MessageNormalizer.Raw shadowDelivery(StatusBarNotification item, String packageName) {
        try {
            MessageNormalizer.Raw raw = shadowInput(packageName, item.getNotification());
            return raw == null ? null : raw.withIdentity(MessageNormalizer.Raw.notificationIdentity(item.getKey(), item.getPostTime()));
        } catch (RuntimeException ignored) { return null; } // Shadow input must never block legacy capture.
    }

    private static void process(Context context, StatusBarNotification item, String packageName) {
        try {
            String body = notificationBody(item.getNotification());
            String label;
            try { label = context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
            catch (Exception ignored) { label = packageName; }
            if (body == null) {
                // Legacy skips it (group summary or no title/text); shadow still observes it and records nothing.
                MessageNormalizer.Raw shadow = shadowDelivery(item, packageName);
                if (shadow == null) return;
                Notification n = item.getNotification();
                String reason = n != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0 ? "LEGACY_GROUP_SUMMARY" : "LEGACY_NO_BODY";
                try (ExpenseDb db = new ExpenseDb(context)) { CaptureTransaction.observeWithoutCapture(db, shadow, label, item.getPostTime(), reason); }
                return;
            }
            try (ExpenseDb db = new ExpenseDb(context)) {
                ExpenseDb.Transaction before = db.latestInserted();
                MessageNormalizer.Raw shadow = shadowDelivery(item, packageName);
                CaptureResult result = db.captureNotification(packageName, label, body, item.getPostTime(), shadow);
                boolean added = result.dbChanged;
                if (added)
                    SmsReceiver.notifyDataChanged(context);
                else DailyRefreshReceiver.refresh(context);
                if(result.completion!=CaptureResult.Completion.COMMITTED)return;
                if(System.currentTimeMillis()-item.getPostTime()<10*60*1000)CaptureNotifier.notifyEvent(context,result.event);
                if (added) {
                    ExpenseDb.Transaction after = result.transaction;
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
