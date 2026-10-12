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
        boolean changed = false;
        try (ExpenseDb db = new ExpenseDb(context)) {
            changed = handle(db, context, parts);
        } catch (RuntimeException error) {
            android.util.Log.e("FollowMoney", "SMS skipped: " + error.getClass().getSimpleName());
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
                boolean added = db.addSms(sender,body,message.when);
                changed |= added || CommitmentHint.parse(sender, body,message.when) != null ||
                        CommitmentHint.completedPlanKey(sender, body) != null;
                ExpenseDb.Transaction after = added ? db.lastCaptured() : null;
                if (after != null && (before == null || after.id > before.id)) CaptureNotifier.notify(context, after);
            }
        }
        return changed;
    }
}
