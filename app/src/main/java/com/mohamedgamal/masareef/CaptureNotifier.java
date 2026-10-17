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

/** Posts "recorded" notifications seconds after a bank message becomes a transaction. */
final class CaptureNotifier {
    private static final String CHANNEL_ID = "captured_transactions";
    static final String EXTRA_TRANSACTION = "transaction_id";
    static final String EXTRA_CAPTURE_EVENT = "capture_event_id";
    static final String EXTRA_EDIT = "edit_transaction";

    private CaptureNotifier() {}

    static boolean enabled(Context context) {
        return context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE).getBoolean("enabled", true);
    }

    static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
    }

    static void notify(Context context, ExpenseDb.Transaction item) {
        if (item == null || !enabled(context)) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "العمليات الجديدة",
                NotificationManager.IMPORTANCE_DEFAULT));
        int id = 3_000_000 + (int) (item.id % 1_000_000);
        manager.notify(id, build(context,item));
    }

    static int eventNotificationId(CaptureEvents.Event event){return event.originalId==null?4_000_000+(int)(event.id%1_000_000):3_000_000+(int)(event.originalId%1_000_000);}
    static void notifyEvent(Context context,CaptureEvents.Event event){
        if(event==null||!enabled(context))return;
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"العمليات الجديدة",NotificationManager.IMPORTANCE_DEFAULT));
        manager.cancel(4_000_000+(int)(event.id%1_000_000));
        manager.notify(eventNotificationId(event),buildEvent(context,event));
    }
    static Notification buildEvent(Context context,CaptureEvents.Event event){
        boolean hidden=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getBoolean("hide_amounts",false);
        String title=event.cancelled()?"البنك ألغى التحويل":"رسالة إلغاء تحتاج مراجعة";
        String text=event.cancelled()?"المحاولة محفوظة في سجل الإلغاء، ومش محسوبة في الإجماليات":"لم نغيّر أي عملية؛ افتح الرسالة للمراجعة";
        if(!hidden&&event.amount!=null)title+=" · "+MainActivity.money(event.amount,event.currency);
        Intent open=new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).setData(android.net.Uri.parse("followmoney://capture-event/"+event.id)).putExtra(EXTRA_CAPTURE_EVENT,event.id);
        PendingIntent content=PendingIntent.getActivity(context,eventNotificationId(event),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context,CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(title).setContentText(text)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(new Notification.Builder(context,CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_notify)
                        .setContentTitle("تحديث حالة عملية").setContentText("افتح Follow Money للمراجعة").setVisibility(Notification.VISIBILITY_PUBLIC).build())
                .setContentIntent(content).setAutoCancel(true).build();
    }

    static Notification build(Context context,ExpenseDb.Transaction item) {
        boolean hidden=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getBoolean("hide_amounts",false);
        String amount = MainActivity.money(item.amountMinor, item.currency);
        String who = UiKit.displayName(item.merchant);
        String title = "income".equals(item.kind) ? "دخل جديد " + amount + " من " + who :
                "transfer".equals(item.kind) ? "تحويل " + amount :
                        "اتسجلت " + amount + " في " + who;
        String category = "income".equals(item.kind) ? "دخل" : "transfer".equals(item.kind) ? "تحويلات" : item.category;
        String text = category + (UiKit.isInstaPay(item.merchant) ? " · إنستاباي" : "") +
                (item.cardLast4 == null ? "" : " · •••• " + item.cardLast4);
        String publicTitle="تم تسجيل عملية جديدة";
        String publicText="افتح Follow Money لعرض التفاصيل";
        if(hidden){title=publicTitle;text=publicText;}
        int id = 3_000_000 + (int) (item.id % 1_000_000);
        Intent open = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TRANSACTION, item.id);
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent edit = new Intent(open).putExtra(EXTRA_EDIT, true);
        PendingIntent editIntent = PendingIntent.getActivity(context, id + 1, edit,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(0xff0b3d2e)
                .setContentTitle(title)
                .setContentText(text)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(context,CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(publicTitle)
                        .setContentText(publicText).setVisibility(Notification.VISIBILITY_PUBLIC).build())
                .setContentIntent(content)
                .setAutoCancel(true);
        if ("expense".equals(item.kind))
            builder.addAction(new Notification.Action.Builder(null, "غيّر التصنيف", editIntent).build());
        return builder.build();
    }
}
