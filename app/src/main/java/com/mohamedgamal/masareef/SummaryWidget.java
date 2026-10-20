package com.mohamedgamal.masareef;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Home widgets and the transparent lock-screen variant share the daily summary. */
public class SummaryWidget extends AppWidgetProvider {
    static void refresh(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,SummaryWidget.class));
        if(ids.length>0)new SummaryWidget().onUpdate(context,manager,ids);
        int[] small=manager.getAppWidgetIds(new ComponentName(context,SmallSummaryWidget.class));
        if(small.length>0)new SummaryWidget().onUpdate(context,manager,small);
        int[] large=manager.getAppWidgetIds(new ComponentName(context,LargeSummaryWidget.class));
        if(large.length>0)new SummaryWidget().onUpdate(context,manager,large);
        int[] lock=manager.getAppWidgetIds(new ComponentName(context,LockSummaryWidget.class));
        if(lock.length>0)new LockSummaryWidget().onUpdate(context,manager,lock);
    }

    @Override public void onReceive(Context context,Intent intent) {
        super.onReceive(context,intent);
    }

    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,android.os.Bundle options) {
        onUpdate(context,manager,new int[]{id});
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        draw(context, manager, ids);
        // Normalization writes to the ledger, so it belongs on the one capture worker, never on the main thread
        // the widget is updated from. The widget shows what it has, then redraws only if settling moved a row.
        final Context app = context.getApplicationContext();
        CaptureQueue.submit(() -> {
            int moved = 0;
            try (ExpenseDb db = new ExpenseDb(app)) { moved = MainActivity.normalizeEarlyPaymentsOnWorker(db); }
            catch (RuntimeException ignored) { }
            if (moved > 0) draw(app, AppWidgetManager.getInstance(app), ids);
        });
    }

    /** Read-only render from whatever the ledger currently holds; it never writes and never normalizes. */
    static void draw(Context context, AppWidgetManager manager, int[] ids) {
        try (ExpenseDb db = new ExpenseDb(context)) {
            DailySummary snapshot=DailySummary.load(context,db);LocalDate today=snapshot.date;
            boolean hidden=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getBoolean("hide_amounts",false);
            String currency=context.getSharedPreferences("widget_settings",Context.MODE_PRIVATE).getString("currency","EGP");
            DailyAllowance.Result result=snapshot.currencies.get(currency);
            // Same function as the home card: the statement from the pay day to today (MoneyFlow.statement).
            boolean needsStats=false;for(int id:ids)if(isLarge(manager,id)||isWideSmall(context,manager,id)){needsStats=true;break;}
            int salaryDay=context.getSharedPreferences(DailySummary.SETTINGS,Context.MODE_PRIVATE).getInt("salary_"+currency,0);
            LocalDate[] period=MoneyFlow.statementPeriod(salaryDay,DailySummary.monthlySalary(context,currency),currency,snapshot.zone,today,(from,to)->db.flowItems(from,to,snapshot.zone));
            MoneyFlow.Statement statement=needsStats?MoneyFlow.statement(db.flowItems(period[0],period[1],snapshot.zone),period,snapshot.zone,currency):null;
            String time=snapshot.updated.atZone(snapshot.zone).format(DateTimeFormatter.ofPattern("h:mm a",new Locale("ar")));
            for (int id : ids) {
                boolean small=isSmall(manager,id);
                boolean large=isLarge(manager,id);
                RemoteViews views = new RemoteViews(context.getPackageName(),layout(context,manager,id));
                boolean valid=result!=null&&result.valid;
                boolean over=valid&&result.remaining<0;
                String remaining=!valid?"حدد ميزانية اليوم":hidden?result.status():
                        over?"تجاوزت بـ "+MainActivity.money(-result.remaining,currency):"متبقي "+MainActivity.money(result.remaining,currency);
                if(!large&&valid&&!hidden)remaining=(over?"تجاوزت ":"متبقي ")+String.format(Locale.ROOT,"%,.2f",Math.abs(result.remaining)/100.0);
                views.setTextViewText(R.id.widget_remaining,remaining);
                String spent=hidden?"صرف النهارده مخفي":"صرفت النهارده "+MainActivity.money(result==null?0:result.expense,currency)+
                        (valid?" من "+MainActivity.money(result.plan.limit,currency):"");
                if(!large&&!hidden)spent="صرفت "+String.format(Locale.ROOT,"%,.2f",(result==null?0:result.expense)/100.0)+
                        (valid?" من "+String.format(Locale.ROOT,"%,.2f",result.plan.limit/100.0):"");
                views.setTextViewText(R.id.widget_amount,spent);
                views.setViewVisibility(R.id.widget_progress,valid&&!over&&!hidden?android.view.View.VISIBLE:android.view.View.GONE);
                views.setViewVisibility(R.id.widget_progress_over,valid&&over&&!hidden?android.view.View.VISIBLE:android.view.View.GONE);
                views.setProgressBar(R.id.widget_progress,100,valid?result.progress:0,false);
                Intent open = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_transactions",true);
                PendingIntent pending = PendingIntent.getActivity(context, 2000102, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent home=PendingIntent.getActivity(context,2000105,new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                views.setOnClickPendingIntent(R.id.widget_root, home);
                if(large||isWideSmall(context,manager,id)) {
                    views.setTextViewText(R.id.widget_week_label,"خرج من "+period[0].format(DateTimeFormatter.ofPattern("d MMMM",new Locale("ar"))));
                    views.setTextViewText(R.id.widget_week,hidden?"••••":MainActivity.money(statement==null?0:statement.out,currency));
                    views.setTextViewText(R.id.widget_month_label,"باقي من دخل الفترة");
                    long left=statement==null?0:statement.remaining;
                    views.setTextViewText(R.id.widget_month,hidden?"••••":(left<0?"−":"")+MainActivity.money(Math.abs(left),currency));
                }
                if(small) {
                    views.setTextViewText(R.id.widget_updated,"النهارده · "+currency);
                } else {
                    views.setTextViewText(R.id.widget_title,"النهارده · "+currency);
                    views.setTextViewText(R.id.widget_updated,"تحديث "+time);
                    views.setOnClickPendingIntent(R.id.widget_open,pending);
                    PendingIntent add=PendingIntent.getActivity(context,2000103,new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_add_transaction",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    views.setOnClickPendingIntent(R.id.widget_add,add);
                }
                if(!valid) {
                    PendingIntent plan=PendingIntent.getActivity(context,2000104,new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("open_daily_budget",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    views.setOnClickPendingIntent(R.id.widget_remaining,plan);
                }
                manager.updateAppWidget(id, views);
            }
        } catch(RuntimeException error) {
            // Never leave visible financial values when a refresh/permission/privacy change fails.
            for(int id:ids) {
                RemoteViews views=new RemoteViews(context.getPackageName(),layout(context,manager,id));
                views.setTextViewText(R.id.widget_remaining,"الملخص غير متاح");
                views.setTextViewText(R.id.widget_amount,"افتح التطبيق لتحديثه");
                views.setViewVisibility(R.id.widget_progress,android.view.View.GONE);
                views.setViewVisibility(R.id.widget_progress_over,android.view.View.GONE);
                if(isLarge(manager,id)||isWideSmall(context,manager,id)){views.setTextViewText(R.id.widget_week,"—");views.setTextViewText(R.id.widget_month,"—");}
                views.setTextViewText(R.id.widget_updated,"");manager.updateAppWidget(id,views);
            }
            android.util.Log.e("FollowMoney","Widget refresh failed: "+error.getClass().getSimpleName());
        }
    }
    private static boolean isSmall(AppWidgetManager manager,int id) {
        android.appwidget.AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        return info!=null&&SmallSummaryWidget.class.getName().equals(info.provider.getClassName());
    }
    private static boolean isLarge(AppWidgetManager manager,int id) {
        android.appwidget.AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        return info!=null&&LargeSummaryWidget.class.getName().equals(info.provider.getClassName());
    }
    private static boolean isWideSmall(Context context,AppWidgetManager manager,int id) {
        if(!isSmall(manager,id))return false;
        boolean portrait=context.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        String key=portrait?AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH:AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH;
        return manager.getAppWidgetOptions(id).getInt(key,0)>=260;
    }
    private static int layout(Context context,AppWidgetManager manager,int id) {
        return isSmall(manager,id)?isWideSmall(context,manager,id)?R.layout.widget_summary_small_wide:R.layout.widget_summary_small:isLarge(manager,id)?R.layout.widget_summary_large:R.layout.widget_summary;
    }
}
