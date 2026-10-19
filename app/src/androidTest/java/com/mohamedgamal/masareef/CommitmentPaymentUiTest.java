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

/** Real dialog journey in the disposable commitmentqa package; never a user's installed package. */
final class CommitmentPaymentUiTest {
    private final Instrumentation runner;
    private MainActivity activity;
    private ExpenseDb db;
    private File screenshots;
    private int checks;
    CommitmentPaymentUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private void pause(){try{Thread.sleep(100);}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}}
    private String text(CharSequence value){return value==null?"":value.toString().replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","");}
    private AccessibilityNodeInfo node(String wanted){
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();if(root==null)return null;
        for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(wanted))if(text(n.getText()).contains(wanted))return n;
        return null;
    }
    private void click(String wanted){
        for(int attempt=0;attempt<40;attempt++){
            AccessibilityNodeInfo n=node(wanted);
            if(n!=null){n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                for(int depth=0;n!=null&&depth<8;depth++,n=n.getParent())if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            }
            pause();
        }throw new AssertionError("Cannot click "+wanted);
    }
    private void show(long commitmentId,int tab){
        runner.runOnMainSync(()->{try{
            Field selected=MainActivity.class.getDeclaredField("selectedCommitmentId");selected.setAccessible(true);selected.setLong(activity,commitmentId);
            Field page=MainActivity.class.getDeclaredField("selectedTab");page.setAccessible(true);page.setInt(activity,tab);
            Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void waitPaid(long id,int count){
        for(int i=0;i<100;i++){if(db.recurringById(id).paidCount==count){idle();return;}pause();}
        throw new AssertionError("Payment projection did not reach "+count);
    }
    private void screen(String name)throws Exception{
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(screenshots,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    void run(){
        Bundle result=new Bundle();int status=-1;boolean owned=false;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".commitmentqa")||!("ranchu".equals(android.os.Build.HARDWARE)||"goldfish".equals(android.os.Build.HARDWARE)))throw new SecurityException("Disposable commitmentqa emulator required");
            db=AppResources.database(target);
            ok(db.transactionCount()==0&&db.recurring().isEmpty()&&db.pendingCommitmentConfirmations().isEmpty(),"UI fixture requires an empty disposable target; existing data retained");owned=true;
            screenshots=new File(target.getCacheDir(),"commitment-ui");ok(screenshots.isDirectory()||screenshots.mkdirs(),"Screenshot folder unavailable");
            LocalDate today=LocalDate.now();long at=today.atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long plan=db.putCommitment(null,"QA UI provider",10000,"EGP",today,"monthly","installment",12,0,1,"qa ui provider",null);
            db.addManual(at,6000,"EGP","QA UI provider","أقساط");db.linkCommitmentPayment(plan,today,db.latestInserted().id,6000);
            db.addManual(at+1000,4000,"EGP","QA UI provider","أقساط");
            db.putCommitment(null,"Netflix",10000,"EGP",today,"monthly","subscription",null,0,1,"netflix",null);
            db.putCommitment(null,"Netflix dollars",10000,"USD",today,"monthly","subscription",null,0,1,"netflix",null);
            db.captureSms("Billing","Netflix bill paid successfully",at+2000);
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            show(plan,11);ok(db.recurringById(plan).paidCount==0,"Partial payment finalized a cycle");screen("01-partial");
            click("علّمه مدفوع");click("اربط عملية مسجلة");click("اختار العملية");click("QA UI provider");
            ok(node("راجع ربط الدفعة")!=null,"Allocation preview missing");ok(db.transactionCount()==2,"Preview created an expense");screen("02-preview");
            click("اعتماد الربط");waitPaid(plan,1);ok(db.transactionCount()==2,"Link duplicated spending");screen("03-paid");
            click("عرض التسويات وفك الربط");click("ربط راجعته");click("فك الربط");waitPaid(plan,0);ok(db.transactionCount()==2,"Undo deleted the payment");screen("04-undo");
            show(plan,2);click("راجع التأكيدات");click("Netflix");
            ok(node("Netflix bill paid successfully")!=null,"Saved provider source not shown");ok(db.pendingCommitmentConfirmations().size()==1,"Ambiguous confirmation disappeared");screen("05-provider-review");
            click("مش متعلق بالتزاماتي");for(int i=0;i<100&&!db.pendingCommitmentConfirmations().isEmpty();i++)pause();
            ok(db.pendingCommitmentConfirmations().isEmpty(),"Review dismissal did not persist");ok(db.transactionCount()==2,"Provider review invented an expense");
            result.putString("stream","Commitment UI checks passed: "+checks+"; link/partial/preview/undo/provider review; screenshots in cache/commitment-ui\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Commitment UI FAILED: "+trace);}
        finally{
            if(activity!=null)runner.runOnMainSync(activity::finish);
            if(owned&&db!=null)db.clearAllData(); // The empty QA target was verified before creating any rows.
            runner.finish(status,result);
        }
    }
}
