package com.mohamedgamal.masareef;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

final class DailySummaryNotifier {
    static final int ID=2000001;
    static void refresh(Context context) {
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
        if(!context.getSharedPreferences("reminder_settings",Context.MODE_PRIVATE).getBoolean("daily_enabled",false)){manager.cancel(ID);return;}
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        manager.createNotificationChannel(new NotificationChannel("daily_summary","ملخص الميزانية اليومية",NotificationManager.IMPORTANCE_LOW));
        try(ExpenseDb db=new ExpenseDb(context)) {
            DailySummary snapshot=DailySummary.load(context,db);
            String currency=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getString("currency","EGP");
            boolean hidden=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getBoolean("hide_amounts",false);
            DailyAllowance.Result result=snapshot.currencies.get(currency);
            String detail=hidden?(result==null?"حدد ميزانيتك اليومية":result.status()):"مصروف اليوم: "+MainActivity.money(result==null?0:result.expense,currency);
            PendingIntent open=PendingIntent.getActivity(context,2000101,new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_transactions",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            manager.notify(ID,new Notification.Builder(context,"daily_summary").setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle(snapshot.remaining(currency,hidden)).setContentText(detail).setContentIntent(open)
                    .setVisibility(hidden?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(true).build());
        }catch(RuntimeException error){manager.cancel(ID);android.util.Log.e("FollowMoney","Daily summary unavailable",error);}
    }
}
