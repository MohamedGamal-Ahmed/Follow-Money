package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** UI and state checks run only against a disposable, separately installed emulator package. */
public final class AppLockUiTest {
    /** Registered only by run-app-lock.ps1 in the separately installed test APK. */
    public static final class OwnResultActivity extends android.app.Activity {
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            android.widget.Button done=new android.widget.Button(this);
            done.setText("APP_LOCK_QA_RESULT");
            done.setOnClickListener(v->{setResult(RESULT_OK);finish();});
            setContentView(done);
        }
    }
    private final Instrumentation runner;
    private final String mode;
    private MainActivity activity;
    private SmsImportActivity importActivity;
    private int checks;
    AppLockUiTest(Instrumentation runner,String mode){this.runner=runner;this.mode=mode;}
    private void ok(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private boolean has(String text){AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private AccessibilityNodeInfo appRoot(){
        for(AccessibilityWindowInfo window:runner.getUiAutomation().getWindows()){
            AccessibilityNodeInfo node=window.getRoot();
            if(node!=null&&node.getPackageName()!=null&&node.getPackageName().toString().endsWith(".appLockQa"))return node;
        }
        return null;
    }
    private boolean appHas(String text){AccessibilityNodeInfo root=appRoot();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private String activeTexts(){StringBuilder out=new StringBuilder();appendTexts(runner.getUiAutomation().getRootInActiveWindow(),out);return out.toString();}
    private void waitForPrompt(){
        long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(android.os.SystemClock.elapsedRealtime()<until){
            if(activeTexts().contains("PIN area"))return;
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Device credential prompt did not appear: "+activeTexts());
    }
    private void waitForUnlock(){
        long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(android.os.SystemClock.elapsedRealtime()<until){
            if(!gateUnchecked().locked())return;
            android.os.SystemClock.sleep(100);
        }
    }
    private void waitForCover(){
        long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(android.os.SystemClock.elapsedRealtime()<until){
            if(has("التطبيق مقفول"))return;
            android.os.SystemClock.sleep(100);
        }
    }
    private void shellInput(String command)throws Exception{
        try(android.os.ParcelFileDescriptor.AutoCloseInputStream stream=
                    new android.os.ParcelFileDescriptor.AutoCloseInputStream(runner.getUiAutomation().executeShellCommand(command))){
            while(stream.read()!=-1){}
        }
    }
    private void appendTexts(AccessibilityNodeInfo node,StringBuilder out){
        if(node==null)return;
        if(node.getText()!=null)out.append('[').append(node.getText()).append(']');
        if(node.getContentDescription()!=null)out.append('{').append(node.getContentDescription()).append('}');
        for(int i=0;i<node.getChildCount();i++)appendTexts(node.getChild(i),out);
    }
    private boolean clickDescription(AccessibilityNodeInfo node,String wanted){
        if(node==null)return false;
        if(node.getContentDescription()!=null&&wanted.contentEquals(node.getContentDescription())){
            android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
            if(!bounds.isEmpty()){
                long when=android.os.SystemClock.uptimeMillis();float x=bounds.centerX(),y=bounds.centerY();
                runner.getUiAutomation().injectInputEvent(MotionEvent.obtain(when,when,MotionEvent.ACTION_DOWN,x,y,0),true);
                runner.getUiAutomation().injectInputEvent(MotionEvent.obtain(when,when+80,MotionEvent.ACTION_UP,x,y,0),true);
                return true;
            }
        }
        for(int i=0;i<node.getChildCount();i++)if(clickDescription(node.getChild(i),wanted))return true;
        return false;
    }
    private AppLock gate()throws Exception{Field field=MainActivity.class.getDeclaredField("appLock");field.setAccessible(true);return (AppLock)field.get(activity);}
    private void ownResultRoundTrip(Context target)throws Exception{
        runner.runOnMainSync(()->activity.startActivityForResult(new Intent().setClassName(
                target.getPackageName()+".test",OwnResultActivity.class.getName()),8199));
        long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(!has("APP_LOCK_QA_RESULT")&&android.os.SystemClock.elapsedRealtime()<until)android.os.SystemClock.sleep(100);
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        ok(root!=null&&!root.findAccessibilityNodeInfosByText("APP_LOCK_QA_RESULT").isEmpty(),"Own-result QA activity did not open");
        long stoppedAt=target.getSharedPreferences(AppLock.SETTINGS,0).getLong("stopped_at_elapsed",-1);
        root.findAccessibilityNodeInfosByText("APP_LOCK_QA_RESULT").get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        until=android.os.SystemClock.elapsedRealtime()+5000;
        while(!activity.hasWindowFocus()&&android.os.SystemClock.elapsedRealtime()<until)android.os.SystemClock.sleep(100);
        idle();
        ok(stoppedAt<0,"Our own result activity recorded a background timeout");
        ok(!gate().locked()&&!has("التطبيق مقفول"),"Own-result return relocked the app at timeout zero");
        ok(!activeTexts().contains("PIN area"),"Own-result return opened another authentication prompt");
    }
    private void showSettings()throws Exception{
        runner.runOnMainSync(()->{try{
            Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,5);
            Field section=MainActivity.class.getDeclaredField("settingsSection");section.setAccessible(true);section.set(activity,"data");
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
        }catch(Exception error){throw new RuntimeException(error);}});idle();
    }
    private void screenshot(Context target,String name)throws Exception{
        File folder=new File(target.getCacheDir(),"app-lock");ok(folder.isDirectory()||folder.mkdirs(),"Screenshot folder unavailable");
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".appLockQa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))
                throw new SecurityException("Disposable appLockQa emulator required");
            ExpenseDb db=AppResources.database(target);
            ok(db.transactionCount()==0,"Isolated QA package must start with an empty ledger");
            ok(db.addManualEntry(System.currentTimeMillis(),98765,"EGP","LOCK QA 98765",
                    "أكل وشرب",null,"expense",null,null),"Synthetic financial marker was not saved");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true)
                    .putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            target.getSharedPreferences(AppLock.SETTINGS,0).edit().putBoolean("enabled",true)
                    .putInt("timeout_seconds",0).putBoolean("secure_preview",true).commit();
            activity=(MainActivity)runner.startActivitySync(new Intent(target,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open_transactions",true)
                    .putExtra("app_lock_qa_manual_prompt",true));idle();
            if("app_lock_no_secure".equals(mode)) {
                ok(!gate().enabled(),"Lock must disable itself when the device credential was removed");
                ok(!gate().locked(),"Missing device credential trapped the user");
                showSettings();ok(has("قفل التطبيق: متوقف"),"Settings did not explain disabled app lock");
                screenshot(target,"03-no-device-lock");
            } else {
                ok(gate().locked(),"Protected activity did not start locked");
                ok(has("التطبيق مقفول"),"Cover is absent before authentication; active="+activeTexts()+" finishing="+activity.isFinishing());
                ok(!appHas("LOCK QA 98765")&&!appHas("987.65"),"Financial text leaked into the accessibility tree");
                screenshot(target,"01-cover");
                runner.runOnMainSync(()->gateUnchecked().unlockWithDevice());idle();
                waitForPrompt();
                String credentialUi=activeTexts();
                clickDescription(runner.getUiAutomation().getRootInActiveWindow(),"PIN area");
                shellInput("input keyevent 8");shellInput("input keyevent 9");
                shellInput("input keyevent 10");shellInput("input keyevent 11");
                shellInput("input keyevent 66");idle();
                waitForUnlock();
                idle();
                ok(!gate().locked(),"Correct device PIN did not unlock the activity; before="+credentialUi+" after="+activeTexts());
                showSettings();ok(has("قفل التطبيق: مفعل"),"Settings do not show enabled app lock");
                screenshot(target,"02-settings");
                ownResultRoundTrip(target);
                importActivity=(SmsImportActivity)runner.startActivitySync(new Intent(target,SmsImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("app_lock_qa_manual_prompt",true));idle();
                ok(!has("التطبيق مقفول"),"Moving to SMS import relocked the foreground app");
                runner.runOnMainSync(importActivity::finish);idle();importActivity=null;
                ok(!gate().locked(),"Returning from SMS import relocked the foreground app");
                runner.runOnMainSync(()->{AppLock lock=gateUnchecked();lock.setTimeoutSeconds(60);lock.onStop();});
                runner.runOnMainSync(()->gateUnchecked().guard());
                ok(!gate().locked(),"One-minute grace period relocked immediately");
                target.getSharedPreferences(AppLock.SETTINGS,0).edit().putLong("stopped_at_elapsed",android.os.SystemClock.elapsedRealtime()-61000).commit();
                runner.runOnMainSync(()->gateUnchecked().guard());idle();
                ok(gate().locked()&&has("التطبيق مقفول"),"Expired elapsed-time timeout did not restore cover");
                runner.runOnMainSync(()->gateUnchecked().unlockWithDevice());waitForPrompt();
                runner.runOnMainSync(()->gateUnchecked().cancelAuthentication());idle();
                waitForCover();
                ok(gate().locked(),"Cancel unlocked the activity");
                ok(has("التطبيق مقفول"),"Cancel did not leave the cover visible: "+activeTexts());
                ok(has("حاول مرة أخرى"),"Cancel did not offer another attempt");
                importActivity=(SmsImportActivity)runner.startActivitySync(new Intent(target,SmsImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("app_lock_qa_manual_prompt",true));idle();
                ok(has("التطبيق مقفول")&&!has("استيراد رسائل البنك"),
                        "SMS import showed content before authentication; active="+activeTexts()
                                +" stoppedAt="+target.getSharedPreferences(AppLock.SETTINGS,0).getLong("stopped_at_elapsed",-1)
                                +" now="+android.os.SystemClock.elapsedRealtime()
                                +" enabled="+target.getSharedPreferences(AppLock.SETTINGS,0).getBoolean("enabled",false)
                                +" importLocked="+importLock().locked());
            }
            result.putString("stream","App lock UI checks passed: "+checks+"; screenshots in cache/app-lock\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","App lock UI FAILED: "+trace);}
        finally{
            if(importActivity!=null)runner.runOnMainSync(importActivity::finish);
            if(activity!=null)runner.runOnMainSync(activity::finish);
            runner.finish(status,result);
        }
    }
    private AppLock gateUnchecked(){try{return gate();}catch(Exception error){throw new RuntimeException(error);}}
    private AppLock importLock()throws Exception{Field field=SmsImportActivity.class.getDeclaredField("appLock");field.setAccessible(true);return (AppLock)field.get(importActivity);}
}
