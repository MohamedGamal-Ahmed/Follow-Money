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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/** One neutral notification per completed month; catch up on app open/reboot without financial lock-screen text. */
public final class MonthlySummaryReceiver extends BroadcastReceiver {
    static final String SETTINGS="monthly_summary", EXTRA_MONTH="monthly_report_month", CHANNEL="monthly_summary";
    static final int ID=2000200;
    static SharedPreferences settings(Context context){return context.getSharedPreferences(SETTINGS,Context.MODE_PRIVATE);}
    static synchronized void initialize(Context context,YearMonth now) {
        SharedPreferences prefs=settings(context);
        if(!prefs.contains("start_month"))prefs.edit().putString("start_month",now.toString()).commit();
    }
    static void schedule(Context context) {
        AlarmManager alarm=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarm==null)return;
        PendingIntent pending=PendingIntent.getBroadcast(context,ID,new Intent(context,MonthlySummaryReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        if(!settings(context).getBoolean("enabled",true)){alarm.cancel(pending);return;}
        long next=MonthlyReport.nextCheck(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending);
    }
    static void refresh(Context context){refreshAt(context,LocalDateTime.now());schedule(context);}
    static synchronized void refreshAt(Context context,LocalDateTime now) {
        YearMonth current=YearMonth.from(now);initialize(context,current);
        SharedPreferences prefs=settings(context);NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        if(!prefs.getBoolean("enabled",true)){manager.cancel(ID);return;}
        if(!MonthlyReport.notificationDue(now,MonthlyReport.parseMonth(prefs.getString("start_month",null),current),
                MonthlyReport.parseMonth(prefs.getString("notified_month",null),null),MonthlyReport.parseMonth(prefs.getString("seen_month",null),null)))return;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        if(!manager.areNotificationsEnabled())return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"ملخص الشهر",NotificationManager.IMPORTANCE_DEFAULT));
        if(manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        YearMonth month=current.minusMonths(1);
        try(ExpenseDb db=new ExpenseDb(context)) {
            if(db.transactionsBetween(month.atDay(1),month.plusMonths(1).atDay(1)).isEmpty())return;
            Intent open=new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_MONTH,month.toString());
            PendingIntent content=PendingIntent.getActivity(context,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            String name=month.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy",new java.util.Locale("ar")));
            manager.notify(ID,new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_stat_notify).setColor(0xff0b3d2e)
                    .setContentTitle("ملخص "+name+" جاهز").setContentText("راجع صرفك والتزاماتك وسلوك الإنفاق المسجل")
                    .setContentIntent(content).setVisibility(Notification.VISIBILITY_PUBLIC).setAutoCancel(true).build());
            prefs.edit().putString("notified_month",month.toString()).commit();
        }catch(RuntimeException error){android.util.Log.e("FollowMoney","Monthly summary unavailable: "+error.getClass().getSimpleName());}
    }
    static void markSeen(Context context,YearMonth month) {
        SharedPreferences prefs=settings(context);YearMonth seen=MonthlyReport.parseMonth(prefs.getString("seen_month",null),null);
        if(seen==null || seen.isBefore(month))prefs.edit().putString("seen_month",month.toString()).apply();
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        YearMonth notified=MonthlyReport.parseMonth(prefs.getString("notified_month",null),null);
        if(manager!=null && month.equals(notified))manager.cancel(ID);
    }
    @Override public void onReceive(Context context,Intent intent) {
        PendingResult pending=goAsync();
        new Thread(()->{try{refresh(context);}finally{pending.finish();}},"monthly-summary").start();
    }
}
