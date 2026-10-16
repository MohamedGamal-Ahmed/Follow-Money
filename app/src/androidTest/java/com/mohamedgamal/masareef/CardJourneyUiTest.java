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
final class CardJourneyUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File captures;
    private android.content.res.Configuration originalConfiguration;
    private int checked;
    CardJourneyUiTest(Instrumentation runner){this.runner=runner;}
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
    private void awaitText(String text)throws Exception {
        for(int i=0;i<50;i++){idle();if(has(text))return;Thread.sleep(100);}throw new AssertionError("Screen did not arrive: "+text);
    }
    private void screen(String name)throws Exception {
        // Accessibility can settle before SurfaceFlinger has presented the replacement window.
        idle();Thread.sleep(name.equals("06-history")?2200:800);runner.waitForIdleSync();
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(captures,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private boolean enabled(String text){for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text))if(node.getText()!=null&&plain(node.getText()).equals(text))return node.isEnabled();throw new AssertionError("Missing button "+text);}
    private void clickContaining(String text){
        for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text)){
            while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
        }throw new AssertionError("Cannot click containing "+text);
    }
    void run(boolean dark){run(dark,1f);}
    void run(boolean dark,float fontScale){
        Bundle result=new Bundle();Context target=runner.getTargetContext();int status=-1;
        try{
            if(!target.getPackageName().endsWith(".journeyqa")||!("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE)))throw new SecurityException("Isolated emulator package required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0)throw new IllegalStateException("QA ledger must be empty");
            captures=new File(target.getCacheDir(),(dark?"card-dark":"card-light")+(fontScale>1?"-large":""));captures.mkdirs();
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("dark_mode",dark).putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            long t=System.currentTimeMillis()-10000,from=db.addPaymentSource("QA BANK","حساب","1111"),card=db.addPaymentSource("QA BANK","بطاقة ائتمان","2222");
            db.addManualEntry(t-172800000,22000,"EGP","QA SERVICE","اشتراكات",card,"expense",null,null);long purchase=db.latestInserted().id;
            CardSettlements store=new CardSettlements(db);store.addStatementFee(purchase,660,"FX markup verified from synthetic statement");
            db.addManualEntry(t,22700,"EGP","QA REPAYMENT","تحويلات",from,"expense",null,null);long payment=db.latestInserted().id;
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            if(fontScale>1)main(()->{originalConfiguration=new android.content.res.Configuration(activity.getResources().getConfiguration());android.content.res.Configuration enlarged=new android.content.res.Configuration(originalConfiguration);enlarged.fontScale=fontScale;activity.getResources().updateConfiguration(enlarged,activity.getResources().getDisplayMetrics());});
            main(()->{((ExpenseDb)get("db")).close();set("db",db);set("introVisible",false);set("selectedTab",6);set("selectedMonth",LocalDate.now().withDayOfMonth(1));invoke("render",new Class<?>[0]);});
            main(()->invoke("cardRepaymentDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(payment)));ok(has("اختيار حسابات السداد"));ok(has("حساب الخصم"));ok(has("بطاقة الائتمان"));ok(purchaseCheckedByContains("1111"));ok(!purchaseCheckedByContains("2222"));ok(!enabled("اختيار المشتريات"));screen("01-accounts");
            clickContaining("2222");ok(enabled("اختيار المشتريات"));click("اختيار المشتريات");ok(has("ربط المشتريات بالسداد"));ok(has("226.60"));ok(!store.isSettlement(payment));screen("02-purchases");
            clickContaining("QA SERVICE");click("مراجعة");ok(has("مراجعة سداد البطاقة"));ok(has("0.40"));ok(!store.isSettlement(payment));screen("03-review");
            click("رجوع للاختيار");ok(has("ربط المشتريات بالسداد"));ok(purchaseCheckedByContains("QA SERVICE"));click("مراجعة");click("تأكيد السداد");ok(has("تفاصيل سداد البطاقة"));ok(store.isSettlement(payment));ok(has("0.40"));screen("04-result");
            click("التراجع عن الربط");ok(has("التراجع عن سداد البطاقة؟"));click("تراجع");ok(!store.isSettlement(payment));ok("expense".equals(db.transactionById(payment).kind));
            main(()->invoke("statementFeeDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(purchase)));ok(has("رسوم كشف البطاقة"));ok(has("قيمة الرسوم"));screen("05-fee");click("إلغاء");
            ok(db.addSms("BANK","تم تحويل مبلغ 227 جنيه من حساب رقم 3333 إلى بطاقة ائتمان رقم 4444",t+1000));
            ok(db.addSms("BANK","تم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 3333",t+4000));CaptureEvents.Event event=db.lastCaptureEvent();ok(event!=null&&event.cancelled());
            main(()->{set("selectedTab",6);invoke("resetTransactionFilters",new Class<?>[0]);invoke("render",new Class<?>[0]);((ScrollView)get("pageScroll")).scrollTo(0,0);});ok(!has("سجل الإلغاء"));ok(has("ملغاة"));ok(has("مش محسوبة في الإجماليات"));screen("06-history");
            main(()->{set("transactionKindFilter",event.original.kind);invoke("render",new Class<?>[0]);});ok(has("ملغاة"));
            main(()->{set("transactionKindFilter","income");invoke("render",new Class<?>[0]);});ok(!has("ملغاة"));
            main(()->{invoke("resetTransactionFilters",new Class<?>[0]);invoke("render",new Class<?>[0]);});
            main(()->invoke("showCaptureEvent",new Class<?>[]{CaptureEvents.Event.class},event));ok(has("محاولة ملغاة"));screen("07-canceled");click("عرض المصدر المحفوظ");ok(has("تم الغاء"));ok(has("تم تحويل"));screen("08-evidence");click("إغلاق");
            CaptureNotifier.buildEvent(target,event).contentIntent.send();awaitText("محاولة ملغاة");ok(has("محاولة ملغاة"));ok((Integer)get("selectedTab")==6);click("إغلاق");
            result.putString("stream","Card journey UI checks passed: "+checked+"; failed: 0; dark="+dark+"\nCaptures: "+captures+"\n");
        }catch(Throwable error){status=0;try{screen("failure");}catch(Exception ignored){}java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Card journey UI FAILED: "+trace+"\n");}
        finally{try{if(activity!=null)main(()->{if(originalConfiguration!=null)activity.getResources().updateConfiguration(originalConfiguration,activity.getResources().getDisplayMetrics());activity.finish();});if(db!=null){db.clearAllData();db.close();}}catch(Throwable error){status=0;result.putString("stream",result.getString("stream","")+"cleanup failed: "+error);}runner.finish(status,result);}
    }
    private boolean purchaseCheckedByContains(String text){
        for(AccessibilityNodeInfo node:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(text)){
            while(node!=null&&!node.isCheckable())node=node.getParent();if(node!=null)return node.isChecked();
        }return false;
    }
}
