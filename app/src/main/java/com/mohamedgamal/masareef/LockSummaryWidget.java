package com.mohamedgamal.masareef;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Transparent daily remaining only; positioning and notification space belong to the widget host. */
public final class LockSummaryWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        RemoteViews views;
        try(ExpenseDb db=new ExpenseDb(context)) {
            views=render(context,DailySummary.load(context,db));
        }catch(RuntimeException error) {
            // Replace old financial text on failure, including its accessibility description.
            views=render(context,null);
            android.util.Log.e("FollowMoney","Lock widget unavailable: "+error.getClass().getSimpleName());
        }
        for(int id:ids)manager.updateAppWidget(id,views);
    }

    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,android.os.Bundle options) {
        onUpdate(context,manager,new int[]{id});
    }

    static RemoteViews render(Context context,DailySummary snapshot) {
        android.content.SharedPreferences settings=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE);
        String currency=settings.getString("currency","EGP");
        boolean hidden=settings.getBoolean("hide_amounts",false);
        DailyAllowance.Result result=snapshot==null?null:snapshot.currencies.get(currency);
        boolean valid=result!=null&&result.valid;
        boolean over=valid&&result.remaining<0;
        String amount=snapshot==null||!valid?"—":hidden?"••••":BigDecimal.valueOf(result.remaining,2).abs().toPlainString();
        String caption=snapshot==null?"افتح للتحديث":!valid?"حدد ميزانية اليوم":
                hidden?"الأرقام مخفية · "+currency:(over?"تجاوز اليوم · ":"متبقي اليوم · ")+currency;
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_lock_transparent);
        views.setTextViewText(R.id.lock_remaining,amount);
        views.setTextViewText(R.id.lock_caption,caption);
        views.setTextColor(R.id.lock_remaining,over&&!hidden?0xffffb4a9:android.graphics.Color.WHITE);
        String description=caption+"، "+amount;
        if(snapshot!=null)description+="، تحديث "+snapshot.updated.atZone(snapshot.zone)
                .format(DateTimeFormatter.ofPattern("h:mm a",new Locale("ar")));
        views.setContentDescription(R.id.lock_widget_root,description);
        Intent open=new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(snapshot!=null&&!valid)open.putExtra("open_daily_budget",true);
        PendingIntent pending=PendingIntent.getActivity(context,2000106,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.lock_widget_root,pending);
        return views;
    }
}
