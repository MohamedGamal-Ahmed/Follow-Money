package com.mohamedgamal.masareef;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "recurring_due";
    static final String ACTION_MARK_PAID = "com.mohamedgamal.masareef.MARK_PAID";
    static final String EXTRA_ID = "commitment_id";
    static final String EXTRA_DUE = "commitment_due", EXTRA_KEY="commitment_key";
    private static final int BRAND = 0xff0b3d2e;

    public static void schedule(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Evening check: "tomorrow" reminders arrive the night before, early enough to move money.
        long first = LocalDate.now().atTime(20, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (first <= System.currentTimeMillis()) first += AlarmManager.INTERVAL_DAY;
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first,
                AlarmManager.INTERVAL_DAY, pending);
    }

    /** Marks one commitment paid for the day. False when nothing changed (already paid, finished or missing). */
    static boolean markPaid(Context context, long id, LocalDate day) {
        try (ExpenseDb db = new ExpenseDb(context)) {
            return db.markRecurringPaid(id, day);
        }
    }
    static boolean markPaid(Context context,long id,LocalDate day,String due,String key){
        if(due==null||key==null)return false;
        try(ExpenseDb db=new ExpenseDb(context)){return db.confirmCommitmentCycle(id,LocalDate.parse(due),key,day);}
    }
    /** Read-only output reconciliation after a committed capture/edit/refund/undo. */
    static void refreshExisting(Context context){
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
        android.service.notification.StatusBarNotification[] active=manager.getActiveNotifications();
        boolean any=false;for(android.service.notification.StatusBarNotification entry:active)if(CHANNEL_ID.equals(entry.getNotification().getChannelId()))any=true;
        if(!any)return;
        try(ExpenseDb db=new ExpenseDb(context)){
            for(android.service.notification.StatusBarNotification entry:active){
                Notification notification=entry.getNotification();if(!CHANNEL_ID.equals(notification.getChannelId()))continue;
                ExpenseDb.Recurring item=db.recurringById(entry.getId());
                if(item==null||item.isDone()||item.needsReview||!item.nextDue.toString().equals(notification.extras.getString(EXTRA_DUE))||
                        !java.util.Objects.equals(db.commitmentIdentity(item.id),notification.extras.getString(EXTRA_KEY))){manager.cancel(entry.getTag(),entry.getId());continue;}
                long remaining=item.amountMinor;for(CommitmentPayments.Cycle cycle:db.commitmentCycles(item.id))if(cycle.due.equals(item.nextDue))remaining=cycle.remaining();
                manager.notify(entry.getTag(),entry.getId(),Notification.Builder.recoverBuilder(context,notification)
                        .setContentText("متبقي "+MainActivity.money(remaining,item.currency)).setOnlyAlertOnce(true).build());
            }
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION_MARK_PAID.equals(intent.getAction())) {
            long id = intent.getLongExtra(EXTRA_ID, -1);
            String due=intent.getStringExtra(EXTRA_DUE),key=intent.getStringExtra(EXTRA_KEY);
            PendingResult pending=goAsync();Context app=context.getApplicationContext();
            CaptureQueue.submit(()->{
                try{
                    boolean changed;
                    try{changed=id>=0&&markPaid(app,id,LocalDate.now(),due,key);}
                    catch(RuntimeException failed){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->android.widget.Toast.makeText(app,"تعذر تسجيل الدفع. افتح التطبيق وجرّب تاني",android.widget.Toast.LENGTH_LONG).show());return;}
                    NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
                    if(manager!=null)manager.cancel((int)id);
                    try{SmsReceiver.notifyDataChanged(app);}catch(RuntimeException output){android.util.Log.w("FollowMoney","Commitment output refresh failed: "+output.getClass().getSimpleName());}
                    if(!changed)new android.os.Handler(android.os.Looper.getMainLooper()).post(()->android.widget.Toast.makeText(app,"ما اتسجلش دفع جديد. افتح التطبيق وراجع حالة الالتزام.",android.widget.Toast.LENGTH_LONG).show());
                }finally{pending.finish();}
            });
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "مواعيد الأقساط والاشتراكات",
                NotificationManager.IMPORTANCE_DEFAULT));
        LocalDate today = LocalDate.now();
        try (ExpenseDb db = new ExpenseDb(context)) {
            try { db.normalizeEarlyPayments(); } catch (RuntimeException ignored) { }
            List<ExpenseDb.Recurring> all=db.recurring();
            for (ExpenseDb.Recurring item : all) {
                if (item.isDone() || item.needsReview ||
                        item.remindDays < 0 || today.toString().equals(item.lastAlertDate)) continue;
                long days = ChronoUnit.DAYS.between(today, item.nextDue);
                // Remind on the chosen lead day, on the due day, and for up to three days after if still unpaid.
                if (days != item.remindDays && days != 0 && (days > 0 || days < -3)) continue;
                String what = "installment".equals(item.kind) ? "قسط " + item.merchant : item.merchant;
                String when = days > 1 ? "بعد " + days + " أيام" : days == 1 ? "بكرة" : days == 0 ? "النهارده" :
                        "متأخر " + (-days) + " يوم";
                String detail = MainActivity.money(item.amountMinor, item.currency);
                if ("installment".equals(item.kind) && item.totalCount != null)
                    detail += " · قسط " + (item.paidCount + 1) + " من " + item.totalCount;
                Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent content = PendingIntent.getActivity(context, (int) item.id, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Intent paid = new Intent(context, ReminderReceiver.class).setAction(ACTION_MARK_PAID)
                        .setData(android.net.Uri.parse("followmoney://commitment/" + item.id + "/due/"+item.nextDue+"/paid"))
                        .putExtra(EXTRA_ID, item.id).putExtra(EXTRA_DUE,item.nextDue.toString()).putExtra(EXTRA_KEY,db.commitmentIdentity(item.id));
                PendingIntent markPaid = PendingIntent.getBroadcast(context, 10000 + (int) item.id, paid,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notification = new Notification.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setColor(BRAND)
                        .setContentTitle(when + ": " + what)
                        .setContentText(detail)
                        .setContentIntent(content)
                        .addExtras(paid.getExtras()).setSubText("تأكيد السداد لا يضيف مصروفًا")
                        .addAction(new Notification.Action.Builder(null, "تأكيد السداد فقط", markPaid).build())
                        .setAutoCancel(true).build();
                manager.notify((int) item.id, notification);
                db.markAlerted(item.id, today);
            }
            DailySummaryNotifier.refresh(context);
        }
    }
}
