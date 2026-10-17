package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;

/** Captures real native UI with synthetic data in a separate releasecheck.journeyqa install. */
final class ReleaseVisualTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private Context target;
    private File captures;
    private int checks;
    ReleaseVisualTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private interface Action{void run()throws Exception;}
    private void main(Action action){runner.runOnMainSync(()->{try{action.run();}catch(Exception error){throw new RuntimeException(error);}});idle();}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(350,5000);}catch(java.util.concurrent.TimeoutException error){throw new AssertionError("UI did not settle",error);}}
    private void shell(String command)throws Exception{
        try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(runner.getUiAutomation().executeShellCommand(command))){
            byte[] buffer=new byte[1024];while(input.read(buffer)!=-1){/* Drain command output. */}
        }
        idle();
    }
    private void set(String name,Object value)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object... values)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(activity,values);}
    private boolean has(String text){AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void capture(String name)throws Exception{
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"Screenshot missing");
        try(FileOutputStream out=new FileOutputStream(new File(captures,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot failed");}finally{bitmap.recycle();}
    }
    private long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            target=runner.getTargetContext();
            ok(target.getPackageName().endsWith(".releasecheck.journeyqa"),"Dedicated visual QA install required");
            ok("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE),"Emulator required");
            db=new ExpenseDb(target);ok(db.transactions().isEmpty(),"Fresh empty QA ledger required");
            captures=new File(target.getCacheDir(),"release-visual-captures");captures.mkdirs();
            LocalDate today=LocalDate.now();long received=at(today,16);
            target.getSharedPreferences(DailySummary.SETTINGS,0).edit().clear().commit();
            target.getSharedPreferences(DailyPointsStore.SETTINGS,0).edit().clear().commit();
            target.getSharedPreferences("widget_settings",0).edit().clear().putString("currency","EGP").commit();
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putInt("repair_version",ExpenseDb.REPAIR_VERSION).putBoolean("dark_mode",false).commit();
            ok(db.addSms("QA BANK","Card purchase EGP 15 at QA DATE SHOP on "+today,received),"Date-only capture failed");long dateOnly=db.lastCaptured().id;
            ok(db.addSms("QA BANK","Card purchase EGP 10 at QA NOON SHOP on "+today+" 12:00",received),"Exact-noon capture failed");long exact=db.lastCaptured().id;
            db.addManualEntry(at(today,9),1000,"EGP","QA MORNING REPAYMENT","دخل",null,"income",null,null);long income=db.latestInserted().id;
            int amount=100;for(String merchant:new String[]{"=1+1","+SUM(A1)","-2+3","@SUM(A1)"}){
                db.addManualEntry(at(today,8),amount,"EGP",merchant,"تسوق",null,"expense",null,"=1+1");amount+=100;
            }
            DailySummary.save(target,new DailyAllowance.Plan("EGP",25000,today.minusDays(1),0));
            DailyPointsStore.enable(target,"EGP",today);DailyPointsStore.observe(target,"EGP",today.minusDays(1));
            DailyPointsStore.confirm(target,db,"EGP",today.minusDays(1),today);
            ok(db.reimbursementCandidates(db.transactionById(income)).stream().anyMatch(tx->tx.id==dateOnly),"Date-only purchase excluded by morning receipt");
            ok(db.reimbursementCandidates(db.transactionById(income)).stream().noneMatch(tx->tx.id==exact),"Exact noon purchase incorrectly treated as date-only");
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class)
                    .putExtra("open_transactions",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            ok(has("تاريخ فقط"),"Date-only list label missing");capture("11-date-only-list-light");
            main(()->{set("selectedTransaction",db.transactionById(dateOnly));set("selectedTab",10);invoke("render",new Class<?>[0]);});
            ok(has("تاريخ فقط"),"Date-only detail label missing");capture("12-date-only-detail-light");
            main(()->invoke("reimbursementDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(income)));
            ok(has("QA DATE SHOP"),"Date-only candidate missing from actual dialog");capture("13-date-only-repayment-candidates");
            shell("input keyevent KEYCODE_BACK");
            main(()->invoke("pointsDialog",new Class<?>[]{String.class},"EGP"));ok(has("رصيدك المعتمد"),"Points summary missing");
            ok(has("+25"),"Confirmed points missing");capture("14-points-light");
            shell("input keyevent KEYCODE_BACK");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",true).commit();
            Instrumentation.ActivityMonitor monitor=runner.addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());MainActivity dark=(MainActivity)runner.waitForMonitorWithTimeout(monitor,5000);runner.removeMonitor(monitor);
            ok(dark!=null,"Dark activity recreation failed");activity=dark;idle();
            ok(has("تاريخ فقط")&&has("تفاصيل العملية")&&!has("رجعتلك عن أنهي عملية؟"),"Dark detail date-only label missing or covered by a dialog");capture("15-date-only-detail-dark");
            main(()->{set("selectedTab",6);invoke("render",new Class<?>[0]);});ok(has("تاريخ فقط"),"Dark list label missing");capture("16-date-only-list-dark");
            main(()->invoke("pointsDialog",new Class<?>[]{String.class},"EGP"));ok(has("رصيدك المعتمد"),"Dark points summary missing");capture("17-points-dark");
            shell("input keyevent KEYCODE_BACK");
            // A real RemoteViews rendering at compact host dimensions; this is not Samsung LockStar.
            main(()->{
                FrameLayout preview=new FrameLayout(activity);preview.setBackgroundColor(0xff34404d);
                View widget=LockSummaryWidget.render(target,DailySummary.load(target,db)).apply(target,preview);
                int width=(int)(96*target.getResources().getDisplayMetrics().density),height=(int)(48*target.getResources().getDisplayMetrics().density);
                preview.addView(widget,new FrameLayout.LayoutParams(width,height,android.view.Gravity.CENTER));activity.setContentView(preview);
            });ok(has("215.00"),"Compact widget remaining amount missing");capture("18-transparent-widget-native-preview");
            main(()->{
                FrameLayout preview=new FrameLayout(activity);preview.setBackgroundColor(0xffffffff);
                View widget=LockSummaryWidget.render(target,DailySummary.load(target,db)).apply(target,preview);
                preview.addView(widget,new FrameLayout.LayoutParams((int)(96*target.getResources().getDisplayMetrics().density),(int)(48*target.getResources().getDisplayMetrics().density),android.view.Gravity.CENTER));activity.setContentView(preview);
            });ok(has("215.00"),"Light-background widget remaining amount missing");capture("18b-transparent-widget-light-background");
            target.getSharedPreferences("widget_settings",0).edit().putBoolean("hide_amounts",true).commit();
            main(()->{
                FrameLayout preview=new FrameLayout(activity);preview.setBackgroundColor(0xff34404d);
                View widget=LockSummaryWidget.render(target,DailySummary.load(target,db)).apply(target,preview);
                preview.addView(widget,new FrameLayout.LayoutParams((int)(96*target.getResources().getDisplayMetrics().density),(int)(48*target.getResources().getDisplayMetrics().density),android.view.Gravity.CENTER));activity.setContentView(preview);
            });ok(has("••••"),"Widget amount masking missing");capture("19-transparent-widget-hidden");
            main(()->invoke("render",new Class<?>[0]));
            // Export after UI captures: its toast must not cover the points dialog buttons.
            File csv=new File(target.getCacheDir(),"release-export.csv");
            main(()->{Field request=MainActivity.class.getDeclaredField("CSV_REQUEST");request.setAccessible(true);
                invoke("onActivityResult",new Class<?>[]{int.class,int.class,android.content.Intent.class},request.getInt(null),android.app.Activity.RESULT_OK,
                        new android.content.Intent().setData(android.net.Uri.fromFile(csv)));});
            ok(csv.isFile()&&csv.length()>100,"Actual CSV export missing");
            CaptureNotifier.notify(target,db.transactionById(dateOnly));
            android.app.NotificationManager manager=(android.app.NotificationManager)target.getSystemService(Context.NOTIFICATION_SERVICE);
            android.app.Notification posted=null;
            for(android.service.notification.StatusBarNotification entry:manager.getActiveNotifications())
                if(entry.getId()==3_000_000+(int)(dateOnly%1_000_000))posted=entry.getNotification();
            ok(posted!=null,"Capture notification was not posted to Android");
            ok("تم تسجيل عملية جديدة".contentEquals(posted.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)),"Posted notification title exposed details");
            ok(!posted.extras.toString().contains("15.00")&&!posted.extras.toString().contains("QA DATE SHOP"),"Posted notification exposed amount or merchant");
            ok(posted.publicVersion!=null,"Public lock-screen version missing");
            shell("input swipe 250 10 250 1500 500");
            long deadline=android.os.SystemClock.uptimeMillis()+5000;
            while(android.os.SystemClock.uptimeMillis()<deadline){
                AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
                if(root!=null&&"com.android.systemui".contentEquals(root.getPackageName()))break;
                android.os.SystemClock.sleep(100);
            }
            capture("20-hidden-capture-notification"); // SystemUI pixels are manually reviewed, independent of accessibility text.
            shell("input keyevent KEYCODE_BACK");
            result.putString("stream","Release visual checks passed: "+checks+"; failed: 0\nCaptures: "+captures+"\nCSV: "+csv+"\n");
        }catch(Throwable error){status=0;try{if(captures!=null)capture("failure");}catch(Exception ignored){}
            java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Release visual FAILED: "+trace);}
        finally{if(db!=null)db.close();runner.finish(status,result);}
    }
}
