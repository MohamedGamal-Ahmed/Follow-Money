package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;

/** «What's new» card lifecycle in a disposable package: first install, update, dismissal, relaunch, missing notes. */
final class WhatsNewUiTest {
    private static final String STATE = "app_state", KEY = "last_seen_version_code";
    private final Instrumentation runner;
    private Context target;
    private MainActivity main;
    private int checked;
    WhatsNewUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String why){if(!value)throw new AssertionError(why);checked++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private boolean has(String text){AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private int lastSeen(){return target.getSharedPreferences(STATE,0).getInt(KEY,-99);}
    private void click(String text){
        long until=android.os.SystemClock.elapsedRealtime()+4000;
        do{
            AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(text))
                for(AccessibilityNodeInfo node=found;node!=null;node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            android.os.SystemClock.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Cannot click "+text);
    }
    private void launch(String notes){
        if(main!=null)runner.runOnMainSync(main::finish);
        runner.runOnMainSync(()->{});
        main=(MainActivity)runner.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        if(notes!=null)runner.runOnMainSync(()->{try{
            Method load=MainActivity.class.getDeclaredMethod("loadWhatsNew",String.class);load.setAccessible(true);load.invoke(main,notes);
            Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(main);
        }catch(Exception error){throw new RuntimeException(error);}});
        idle();
    }
    private void shot(String name)throws Exception{
        idle();android.os.SystemClock.sleep(350);
        File folder=new File(target.getCacheDir(),"whats-new");ok(folder.isDirectory()||folder.mkdirs(),"No screenshot folder");
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    void run(){
        Bundle result=new Bundle();int status=-1;
        try{
            target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".whatsNewQa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))throw new SecurityException("Isolated whatsNewQa emulator required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            String code=String.valueOf(BuildConfig.VERSION_CODE);
            String notes="## "+WhatsNew.baseVersion(BuildConfig.VERSION_NAME)+"\n- QA POINT ONE\n- QA POINT TWO\n";

            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",false).commit();
            target.getSharedPreferences(STATE,0).edit().clear().commit();
            launch(null);
            ok(!has("جديد في النسخة"),"Card shown on a first install");
            ok(lastSeen()==BuildConfig.VERSION_CODE,"First install did not record the version code");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true).commit();

            target.getSharedPreferences(STATE,0).edit().clear().commit();
            launch(null);
            ok(has("جديد في النسخة "+WhatsNew.baseVersion(BuildConfig.VERSION_NAME)),"Update from before the feature (no key, intro done) did not show the card");
            ok(lastSeen()==-99,"Number recorded before «تمام» on a pre-feature update");

            target.getSharedPreferences(STATE,0).edit().putInt(KEY,BuildConfig.VERSION_CODE-1).commit();
            launch(null);
            ok(has("جديد في النسخة "+WhatsNew.baseVersion(BuildConfig.VERSION_NAME)),"Shipped notes did not produce a card after an update");
            ok(lastSeen()==BuildConfig.VERSION_CODE-1,"Number recorded before the user dismissed the card");
            launch(notes);
            ok(has("QA POINT ONE")&&has("QA POINT TWO"),"Notes points missing");shot("05-whats-new-card");
            click("تمام");
            ok(!has("جديد في النسخة"),"Card stayed after «تمام»");
            ok(lastSeen()==BuildConfig.VERSION_CODE,"«تمام» did not record the version code");
            launch(notes);
            ok(!has("جديد في النسخة"),"Card returned after relaunch");

            target.getSharedPreferences(STATE,0).edit().putInt(KEY,BuildConfig.VERSION_CODE-1).commit();
            launch("## 0.0.1\n- another release\n");
            ok(!has("جديد في النسخة"),"Card shown without a section for this version");
            ok(lastSeen()==BuildConfig.VERSION_CODE,"Missing section did not record the version code");

            target.getSharedPreferences(STATE,0).edit().putInt(KEY,BuildConfig.VERSION_CODE+1).commit();
            launch(notes);
            ok(!has("جديد في النسخة"),"Card shown after a downgrade");

            runner.runOnMainSync(()->{try{
                java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.set(main,5);
                java.lang.reflect.Field section=MainActivity.class.getDeclaredField("settingsSection");section.setAccessible(true);section.set(main,"help");
                Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(main);
            }catch(Exception error){throw new RuntimeException(error);}});idle();
            click("ما الجديد");
            ok(has("النسخة "+WhatsNew.baseVersion(BuildConfig.VERSION_NAME)),"Settings row does not show the notes");
            result.putString("stream","What's new UI checks passed: "+checked+"; code="+code+"\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","What's new UI FAILED: "+trace);}
        finally{if(main!=null)runner.runOnMainSync(main::finish);runner.finish(status,result);}
    }
}
