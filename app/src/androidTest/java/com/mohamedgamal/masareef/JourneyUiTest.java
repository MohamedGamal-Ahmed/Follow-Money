package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Actual views, dialogs and Back, using a disposable QA package and a synthetic private DB. */
final class JourneyUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File file,captures;
    private final java.util.List<Long> syntheticIds=new java.util.ArrayList<>();
    private int checked;
    JourneyUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value){if(!value)throw new AssertionError("UI check "+checked);checked++;}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private interface Action{void run()throws Exception;}
    private void main(Action action){runner.runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});idle();}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(500,3000);}catch(java.util.concurrent.TimeoutException e){throw new AssertionError("UI never became idle",e);}}
    private boolean has(String text){return containsVisibleText(runner.getUiAutomation().getRootInActiveWindow(),plain(text));}
    private String plain(CharSequence text){return text==null?"":text.toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","");}
    private boolean containsVisibleText(AccessibilityNodeInfo node,String text){
        if(node==null)return false;
        if(node.getText()!=null&&plain(node.getText()).contains(text))return true;
        for(int i=0;i<node.getChildCount();i++)if(containsVisibleText(node.getChild(i),text))return true;
        return false;
    }
    private boolean purchaseChecked(String name) {
        for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(name)) {
            if(node.getText()==null||!node.getText().toString().equals(name))continue;
            while(node!=null&&!node.isCheckable())node=node.getParent();
            if(node!=null)return node.isChecked();
        }
        return false;
    }
    private void click(String text){
        List<AccessibilityNodeInfo> nodes=runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text);
        for(AccessibilityNodeInfo node:nodes){
            if(node.getText()==null||!node.getText().toString().equals(text))continue;
            while(node!=null&&!node.isClickable())node=node.getParent();
            if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
        }
        throw new AssertionError("Cannot click "+text);
    }
    private void screen(String name)throws Exception {
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(captures,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    void run() {
        Bundle result=new Bundle();Context target=runner.getTargetContext();int status=-1;
        try {
            if(!target.getPackageName().endsWith(".journeyqa") || !("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE)))
                throw new SecurityException("Emulator and isolated journeyqa package required");
            captures=new File(target.getCacheDir(),"journey-captures");captures.mkdirs();
            db=new ExpenseDb(target);if(!db.transactions().isEmpty())throw new IllegalStateException("UI fixture requires an empty QA ledger");
            LocalDate today=LocalDate.now();long t=today.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            db.addManual(t,41900,"EGP","Seoudi","بقالة");long buy=db.latestInserted().id;
            db.addManual(t+1000,2500,"EGP","Coffee","مقاهي");
            db.addManualEntry(t+2000,29500,"EGP","QA colleague","دخل",null,"income",null,null);long back=db.latestInserted().id;
            for(int i=1;i<=12;i++)db.addManual(t-i*86400000L,29500,"EGP","Seoudi QA older "+i,"بقالة");
            for(ExpenseDb.Transaction row:db.transactions())syntheticIds.add(row.id);
            target.getSharedPreferences(DailySummary.SETTINGS,0).edit().clear().commit();target.getSharedPreferences(DailyPointsStore.SETTINGS,0).edit().clear().commit();
            DailySummary.save(target,new DailyAllowance.Plan("EGP",25000,today.minusDays(30),0));
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).putExtra("open_transactions",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            main(()->{((ExpenseDb)get("db")).close();set("db",db);set("introVisible",false);set("selectedTab",6);set("transactionSearch","Seoudi");set("searchOpen",true);invoke("render",new Class<?>[0]);});
            main(()->((ScrollView)get("pageScroll")).scrollTo(0,250));int y=((ScrollView)get("pageScroll")).getScrollY();ok(y>0);
            main(()->{set("transactionReturnTab",6);set("selectedTransaction",db.transactionById(back));set("selectedTab",10);invoke("render",new Class<?>[0]);});
            main(()->invoke("reimbursementDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(back)));
            ok(has("رجعتلك عن أنهي عملية؟"));ok(has("Coffee"));ok(!has("QA older"));ok(db.reimbursedOf(buy)==0);screen("01-choose");
            click("عن عملية أقدم");ok(has("QA older"));screen("02-older");click("مشتريات يوم التحويل");
            click("Seoudi");ok(has("رجعتلك عن أنهي عملية؟"));ok(purchaseChecked("Seoudi"));ok(db.reimbursedOf(buy)==0);screen("01-selected");click("راجع الربط");
            ok(has("راجع النتيجة"));ok(has("101.00"));ok(has("149.00"));ok(db.reimbursedOf(buy)==0);screen("03-preview");
            click("رجوع للاختيار");ok(has("رجعتلك عن أنهي عملية؟"));ok(purchaseChecked("Seoudi"));click("راجع الربط");click("تأكيد الربط");
            ok(has("تم ربط الفلوس الراجعة"));ok(db.reimbursedOf(buy)==29500);screen("04-success");click("تراجع عن الربط");ok(db.reimbursedOf(buy)==0);
            main(()->invoke("reimbursementDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(back)));
            click("Seoudi");click("راجع الربط");click("تأكيد الربط");click("عرض الشراء");ok(((ExpenseDb.Transaction)get("selectedTransaction")).id==buy);screen("05-purchase");
            Instrumentation.ActivityMonitor recreated=runner.addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());MainActivity restored=(MainActivity)runner.waitForMonitorWithTimeout(recreated,5000);runner.removeMonitor(recreated);
            ok(restored!=null);activity=restored;idle();ok(((ExpenseDb.Transaction)get("selectedTransaction")).id==buy);
            main(()->activity.onBackPressed());ok(((ExpenseDb.Transaction)get("selectedTransaction")).id==back);
            ok(has("نصيبك في Seoudi: 124.00"));screen("05-income-share");
            main(()->activity.onBackPressed());ok((Integer)get("selectedTab")==6);ok("Seoudi".equals(get("transactionSearch")));ok(((ScrollView)get("pageScroll")).getScrollY()==y);
            // The state bundle carries both nested parents and list scroll positions.
            Bundle saved=new Bundle();main(()->invoke("onSaveInstanceState",new Class<?>[]{Bundle.class},saved));ok("Seoudi".equals(saved.getString("search")));ok(saved.getBundle("scroll_positions")!=null);
            // Home now shows the month statement; the daily allowance remains available in its detail sheet.
            main(()->{set("selectedTab",0);invoke("render",new Class<?>[0]);});ok(has("كشف الشهر"));screen("06-home");
            main(()->((ScrollView)get("pageScroll")).scrollTo(0,((android.view.View)get("page")).getHeight()));
            ok(((ScrollView)get("pageScroll")).getScrollY()>0);ok(has("نصيبك في Seoudi: 124.00"));screen("06-transfers");
            main(()->invoke("dailyDetailsDialog",new Class<?>[0]));ok(has("101.00"));click("صرف النهارده");ok((Integer)get("selectedTab")==6);ok(((LocalDate[])get("transactionPeriod"))[0].equals(today));
            main(()->activity.onBackPressed());ok(has("ميزانية النهارده"));click("إغلاق");
            main(()->invoke("pointsDialog",new Class<?>[]{String.class},"EGP"));click("تفعيل النقاط");ok(has("+10 نقطة"));ok(!has("+10.1"));ok(has("مؤقتة"));ok(has("رصيدك المعتمد"));ok(has("محطتك الجاية: 100 نقطة"));ok(has("امبارح"));screen("07-points");
            click("النقاط بتتحسب إزاي؟");ok(has("طريقة حساب النقاط"));click("تمام");
            click("سجل الأيام");ok(has("حد اليوم"));click("رجوع للملخص");ok(has("رصيدك المعتمد"));
            click("راجع عمليات اليوم ←");ok((Integer)get("selectedTab")==6);main(()->activity.onBackPressed());ok(has("نقاط الالتزام"));click("إغلاق");
            // Seed explicit historical-limit evidence; observe(yesterday) deliberately cannot rewind today's clock.
            target.getSharedPreferences(DailyPointsStore.SETTINGS,0).edit()
                    .putLong("limit_EGP_"+today.minusDays(1),25000L).commit();
            main(()->invoke("pointsDialog",new Class<?>[]{String.class},"EGP"));ok(has("معتمدة"));ok(has("-5 نقطة"));ok(has("فاضلك 105 نقطة"));ok(!has("تأكيد اكتمال تسجيل اليوم"));screen("08-points-confirmed");click("إغلاق");
            result.putString("stream","Journey UI checks passed: "+checked+"; failed: 0\nSynthetic captures: "+captures+"\n");
        } catch(Throwable e){status=0;try{if(captures!=null)screen("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Journey UI FAILED: "+trace+"\n");}
        finally{
            try{if(activity!=null)main(()->activity.finish());if(db!=null){for(long id:syntheticIds)db.deleteTransaction(id);db.close();}}
            catch(Throwable error){status=0;result.putString("stream",result.getString("stream","")+"QA cleanup FAILED: "+error+"\n");}
            runner.finish(status,result);
        }
    }
}
