package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;

/** Real automatic-points journey in the disposable pointsqa emulator package. */
final class DailyPointsUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File screenshots;
    private int checks;
    DailyPointsUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private void pause(){try{Thread.sleep(100);}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}}
    private String text(CharSequence value){return value==null?"":value.toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","");}
    private AccessibilityNodeInfo node(String wanted){
        return find(runner.getUiAutomation().getRootInActiveWindow(),wanted);
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String wanted){
        if(root==null)return null;if(text(root.getText()).contains(wanted))return root;
        for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),wanted);if(found!=null)return found;}
        return null;
    }
    private void click(String wanted){
        for(int attempt=0;attempt<40;attempt++){
            AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(wanted)){
                if(!text(found.getText()).contains(wanted))continue;
                found.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                AccessibilityNodeInfo n=found;
                for(int depth=0;n!=null&&depth<8;depth++,n=n.getParent())if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            }
            pause();
        }throw new AssertionError("Cannot click "+wanted);
    }
    private void dump(AccessibilityNodeInfo n,StringBuilder out){
        if(n==null)return;if(n.getText()!=null)out.append(text(n.getText())).append(" | ");
        for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),out);
    }
    private void open(String code){
        runner.runOnMainSync(()->{try{
            Method method=MainActivity.class.getDeclaredMethod("pointsDialog",String.class);method.setAccessible(true);method.invoke(activity,code);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void await(String wanted){for(int i=0;i<100;i++){if(node(wanted)!=null){idle();return;}pause();}throw new AssertionError("Missing "+wanted);}
    private void screen(String name)throws Exception{
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(screenshots,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    void run(){
        Bundle result=new Bundle();int status=-1;boolean owned=false;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".pointsqa")||!("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE)))throw new SecurityException("Disposable pointsqa emulator required");
            db=AppResources.database(target);
            ok(db.transactionCount()==0&&db.recurring().isEmpty()&&target.getSharedPreferences(DailySummary.SETTINGS,0).getAll().isEmpty()&&target.getSharedPreferences(DailyPointsStore.SETTINGS,0).getAll().isEmpty(),"Empty QA target required; existing data retained");owned=true;
            screenshots=new File(target.getCacheDir(),"points-ui");ok(screenshots.isDirectory()||screenshots.mkdirs(),"Screenshot folder unavailable");
            LocalDate today=LocalDate.now(),first=today.minusDays(3);long at=first.atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            DailySummary.save(target,new DailyAllowance.Plan("EGP",25000,first,0),first);DailyPointsStore.enable(target,"EGP",first);
            db.addManual(at,15000,"EGP","QA late shop","بقالة");long tx=db.latestInserted().id;
            DailySummary.save(target,new DailyAllowance.Plan("USD",1000,today.minusDays(6),0),today);
            target.getSharedPreferences(DailyPointsStore.SETTINGS,0).edit().putBoolean("enabled_USD",true)
                    .putLong("limit_USD_"+today.minusDays(6),1000).putLong("limit_USD_"+today.minusDays(3),1000).commit();
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            open("EGP");await("+60 نقطة");ok(node("تأكيد اكتمال")==null,"Manual close still required");ok(node("مؤقتة")!=null,"Today not provisional");screen("01-automatic-balance");
            // A committed late edit must refresh the already open sheet without closing/reopening it.
            db.updateTransaction(tx,16000,"EGP","QA late shop","بقالة",null);SmsReceiver.notifyDataChanged(target);
            await("+59 نقطة");screen("02-live-recalculation");
            db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{tx});SmsReceiver.notifyDataChanged(target);
            await("+50 نقطة");ok(node("محتاجة مراجعة")!=null,"Uncertain past day not explained");screen("03-review-hold");
            click("سجل الأيام");ok(node("تأكيد اكتمال")==null&&node("إلغاء تأكيد")==null,"History still has manual day closure");screen("04-history");
            click("إغلاق");open("USD");await("4 يوم قديم خارج الحساب");ok(node("+2 نقطة")!=null,"Legacy known days not automatically finalized");screen("05-unknown-history");click("إغلاق");
            ok(db.transactionCount()==1,"Points created financial rows");
            result.putString("stream","Automatic points UI checks passed: "+checks+"; automatic close/live edit/review hold/history/unknown gaps; screenshots in cache/points-ui\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            StringBuilder visible=new StringBuilder();dump(runner.getUiAutomation().getRootInActiveWindow(),visible);
            try{if(screenshots!=null)screen("failure");}catch(Exception ignored){}
            result.putString("stream","Points UI FAILED: "+trace+"\nVisible: "+visible);
        }finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null){db.clearAllData();runner.getTargetContext().getSharedPreferences(DailySummary.SETTINGS,0).edit().clear().commit();runner.getTargetContext().getSharedPreferences(DailyPointsStore.SETTINGS,0).edit().clear().commit();}
            runner.finish(status,result);
        }
    }
}
