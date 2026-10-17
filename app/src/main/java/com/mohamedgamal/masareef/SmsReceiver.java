package com.mohamedgamal.masareef;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class SmsReceiver extends BroadcastReceiver {
    static final String ACTION_DATA_CHANGED = "com.mohamedgamal.masareef.DATA_CHANGED";

    static void notifyDataChanged(Context context) {
        DailyRefreshReceiver.refresh(context);
        context.sendBroadcast(new Intent(ACTION_DATA_CHANGED).setPackage(context.getPackageName()));
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null) return;
        // Parts in one broadcast can have different network timestamps. Do not parse fragments.
        SmsParts parts=new SmsParts();
        for (SmsMessage message : messages) {
            if (message == null) continue;
            String sender = message.getDisplayOriginatingAddress();
            long when = message.getTimestampMillis();
            parts.add(sender,when,message.getDisplayMessageBody());
        }
        // Writing the ledger can be slow; keep it off the main thread and hold the broadcast open until it is done.
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        CaptureQueue.submit(() -> {
            boolean refreshed = false;
            try { captureNow(app, parts); refreshed = true; }
            catch (RuntimeException error) {
                android.util.Log.e("FollowMoney", "SMS skipped: " + error.getClass().getSimpleName());
            } finally {
                // captureNow() refreshes the summaries itself; a failed capture must not lose that refresh.
                if (!refreshed) DailyRefreshReceiver.refresh(app);
                pending.finish();
            }
        });
    }

    /** Writes one broadcast's messages; runs on the capture worker, never on the main thread. */
    static void captureNow(Context context, SmsParts parts) {
        boolean changed;
        try (ExpenseDb db = new ExpenseDb(context)) {
            changed = handle(db, context, parts);
        }
        if (changed) notifyDataChanged(context);
        else DailyRefreshReceiver.refresh(context);
    }

    private static boolean handle(ExpenseDb db, Context context, SmsParts parts) {
        boolean changed = false;
        {
            for (SmsParts.Message message : parts.messages.values()) {
                String sender=message.sender,body=message.body.toString();
                ExpenseDb.Transaction before = db.latestInserted();
                CaptureResult result = db.captureSms(sender,body,message.when);
                boolean added = result.dbChanged;
                changed |= added || CommitmentHint.parse(sender, body,message.when) != null ||
                        CommitmentHint.completedPlanKey(sender, body) != null;
                CaptureNotifier.notifyEvent(context,result.event);
                ExpenseDb.Transaction after = result.transaction;
                if (after != null && (before == null || after.id > before.id)) CaptureNotifier.notify(context, after);
            }
        }
        return changed;
    }
}
