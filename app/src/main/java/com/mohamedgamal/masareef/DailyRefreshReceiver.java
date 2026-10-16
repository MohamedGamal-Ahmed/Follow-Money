package com.mohamedgamal.masareef;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.LocalDate;
import java.time.ZoneId;

/** Recompute at local midnight and reschedule after clock/zone changes and reboot. */
public final class DailyRefreshReceiver extends BroadcastReceiver {
    static boolean precise(Context context) {
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        return alarm!=null&&(Build.VERSION.SDK_INT<31||alarm.canScheduleExactAlarms());
    }
    static void schedule(Context context) {
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarm==null)return;
        PendingIntent pending=PendingIntent.getBroadcast(context,2000100,new Intent(context,DailyRefreshReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long midnight=LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            if(precise(context))alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,midnight,pending);
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,midnight,pending);
        } catch(SecurityException denied) {alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,midnight,pending);}
    }
    static void refresh(Context context) {
        DailyPointsStore.observeToday(context);
        SummaryWidget.refresh(context);DailySummaryNotifier.refresh(context);MonthlySummaryReceiver.refresh(context);schedule(context);
    }
    private static final java.util.concurrent.ExecutorService NORMALIZE_QUEUE =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "early-payment-normalize");
                thread.setDaemon(true);
                return thread;
            });
    @Override public void onReceive(Context context,Intent intent) {
        refresh(context);
        // Normalization writes; the receiver runs on the main thread, so hand it to a worker and keep the broadcast alive.
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        NORMALIZE_QUEUE.execute(() -> {
            try (ExpenseDb db = new ExpenseDb(app)) { db.normalizeEarlyPayments(); }
            catch (RuntimeException ignored) { }
            finally { pending.finish(); }
        });
        String action=intent.getAction();
        if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action))ReminderReceiver.schedule(context);
        context.sendBroadcast(new Intent(SmsReceiver.ACTION_DATA_CHANGED).setPackage(context.getPackageName()));
    }
}
