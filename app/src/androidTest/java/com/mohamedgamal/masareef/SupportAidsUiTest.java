package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Synthetic support flows in a disposable package. Opens no mail app and sends nothing. */
final class SupportAidsUiTest {
    private final Instrumentation runner;
    private MainActivity main;
    private SmsImportActivity sms;
    private int checked;
    SupportAidsUiTest(Instrumentation runner){this.runner=runner;}
    private void ok(boolean value,String why){if(!value)throw new AssertionError(why);checked++;}
    private void idle(){runner.waitForIdleSync();try{runner.getUiAutomation().waitForIdle(250,3000);}catch(java.util.concurrent.TimeoutException ignored){}}
    private boolean has(String text){AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();}
    private void click(String text){
        long until=android.os.SystemClock.elapsedRealtime()+4000;
        do{
            AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(text))
                for(AccessibilityNodeInfo node=found;node!=null;node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
            android.os.SystemClock.sleep(100);
        }while(android.os.SystemClock.elapsedRealtime()<until);
        StringBuilder diagnostic=new StringBuilder();texts(runner.getUiAutomation().getRootInActiveWindow(),diagnostic);
        throw new AssertionError("Cannot click "+text+"; active="+diagnostic);
    }
    private void texts(AccessibilityNodeInfo node,StringBuilder out){if(node==null)return;if(node.getText()!=null)out.append('[').append(node.getText()).append(']');for(int i=0;i<node.getChildCount();i++)texts(node.getChild(i),out);}
    private Object get(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private void set(Object object,String name,Object value)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private void call(Object object,String name)throws Exception{Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    private void mainCall(String name){runner.runOnMainSync(()->{try{call(main,name);}catch(Exception error){throw new RuntimeException(error);}});idle();}
    private static long scalar(ExpenseDb db,String query){try(android.database.Cursor c=db.getReadableDatabase().rawQuery(query,null)){return c.moveToFirst()?c.getLong(0):-1;}}
    /** The number on the transactions list's «N تحويل لناس غرضها مش محدد» banner (0 when there is none), after a fresh ledger read. */
    private int openTransfers(){
        // Only render(), as the screens do after a save: the list must notice the change on its own (no forced refresh).
        runner.runOnMainSync(()->{try{set(main,"transactionBucketFilter","");set(main,"bulkMode",false);set(main,"selectedTab",6);call(main,"render");}
            catch(Exception error){throw new RuntimeException(error);}});
        for(int i=0;i<12;i++){android.os.SystemClock.sleep(150);idle();}
        java.util.regex.Matcher count=java.util.regex.Pattern.compile("\\[(\\d+) تحويل لناس غرضها مش محدد").matcher(dump());
        return count.find()?Integer.parseInt(count.group(1)):0;
    }
    /** Clicks the action inside the same card as the anchor text (several cards can carry the same action). */
    private void clickIn(String anchor,String action){
        AccessibilityNodeInfo root=runner.getUiAutomation().getRootInActiveWindow();
        for(AccessibilityNodeInfo found:root.findAccessibilityNodeInfosByText(anchor))
            for(AccessibilityNodeInfo card=found.getParent();card!=null;card=card.getParent())
                for(AccessibilityNodeInfo target:card.findAccessibilityNodeInfosByText(action))
                    for(AccessibilityNodeInfo node=target;node!=null;node=node.getParent())
                        if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}
        throw new AssertionError("Cannot click "+action+" next to "+anchor+"; active="+dump());
    }
    private String dump(){StringBuilder out=new StringBuilder();texts(runner.getUiAutomation().getRootInActiveWindow(),out);return out.toString();}
    /** The commitments tab after the ledger snapshot (and its recurring candidates) is rebuilt. */
    private void recurringTab(){
        runner.runOnMainSync(()->{try{Method refresh=MainActivity.class.getDeclaredMethod("refreshLedger",boolean.class);refresh.setAccessible(true);refresh.invoke(main,true);
            set(main,"selectedTab",2);call(main,"render");}catch(Exception error){throw new RuntimeException(error);}});
        for(int i=0;i<40;i++){android.os.SystemClock.sleep(150);idle();runner.runOnMainSync(()->{try{call(main,"render");}catch(Exception error){throw new RuntimeException(error);}});if(has("لقينا خصم متكرر")||i>10)break;}
        idle();
    }
    private void settings(String section){runner.runOnMainSync(()->{try{set(main,"selectedTab",5);set(main,"settingsSection",section);call(main,"render");}catch(Exception error){throw new RuntimeException(error);}});idle();}
    private void shot(Context context,String name)throws Exception{
        idle();android.os.SystemClock.sleep(350);
        File folder=new File(context.getCacheDir(),"support-aids");ok(folder.isDirectory()||folder.mkdirs(),"No screenshot folder");
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();ok(bitmap!=null,"No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){ok(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out),"Screenshot save failed");}
        finally{bitmap.recycle();}
    }
    private boolean setEdit(AccessibilityNodeInfo node,String value){
        if(node==null)return false;
        if("android.widget.EditText".contentEquals(node.getClassName())){
            Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text);
        }
        for(int i=0;i<node.getChildCount();i++)if(setEdit(node.getChild(i),value))return true;
        return false;
    }
    private Intent interceptMail(){
        Intent[] captured={null};
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(Intent.ACTION_SENDTO.equals(intent.getAction())){captured[0]=intent;return new Instrumentation.ActivityResult(0,null);}
                return null;
            }
        };
        runner.addMonitor(monitor);
        try{click("فتح مسودة إيميل");}finally{runner.removeMonitor(monitor);}
        return captured[0];
    }
    @SuppressWarnings("unchecked") void run(String mode){
        Bundle result=new Bundle();int status=-1;
        try{
            Context target=runner.getTargetContext();
            if(!target.getPackageName().endsWith(".supportQa")||!android.os.Build.HARDWARE.matches("ranchu|goldfish"))throw new SecurityException("Isolated supportQa emulator required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true)
                    .putString("opening_mode","off").putBoolean("initial_import_prompted",true).putBoolean("sms_permission_prompted",true).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
            main=(MainActivity)runner.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
            if("support_crash".equals(mode)){
                // 0.4.0: no crash button in the app any more; the QA package crashes its own main thread.
                Bundle marker=new Bundle();marker.putString("stream","Triggering expected debug crash\n");runner.sendStatus(0,marker);
                main.runOnUiThread(()->{throw new IllegalStateException("Synthetic debug crash");});
                android.os.SystemClock.sleep(5000);throw new AssertionError("Debug crash did not terminate the process");
            }
            if("support_battery_allowed".equals(mode)){
                android.os.PowerManager power=(android.os.PowerManager)target.getSystemService(Context.POWER_SERVICE);
                ok(power.isIgnoringBatteryOptimizations(target.getPackageName()),"QA battery exemption not active");
                settings("capture");ok(!has("إعدادات البطارية"),"Battery card stayed after exemption");shot(target,"03-battery-exempt");
            }else{
                String crash=CrashLog.latest(target);ok(crash!=null&&crash.contains("IllegalStateException"),"Actual debug crash was not persisted");
                ok(!crash.contains("Synthetic debug crash"),"Exception message leaked");
                settings("help");click("الإبلاغ عن مشكلة");
                ok(has("إرفاق آخر عطل"),"Crash attachment option absent");
                ok(setEdit(runner.getUiAutomation().getRootInActiveWindow(),"Synthetic support QA"),"Description edit failed");idle();
                Intent defaultDraft=interceptMail();
                ok(defaultDraft!=null&&!defaultDraft.getStringExtra(Intent.EXTRA_TEXT).contains("IllegalStateException"),"Crash attached by default");
                click("إرفاق آخر عطل");ok(has("message omitted"),"Crash preview not visible");shot(target,"01-crash-preview");
                Intent draft=interceptMail();
                ok(draft!=null&&draft.getStringExtra(Intent.EXTRA_TEXT).contains("IllegalStateException")
                        &&draft.getStringExtra(Intent.EXTRA_TEXT).contains("Synthetic support QA"),"Mail draft omitted selected crash");
                runner.runOnMainSync(main::onBackPressed);idle();click("تجاهل التعديلات");
                ok(!has("فتح مسودة إيميل"),"Discarding the report page returned to settings");
                for(int i=0;i<4;i++){CrashLog.save(target,new NumberFormatException("SECRET SMS EGP 1250.00 card 1234567890"));android.os.SystemClock.sleep(5);}
                File[] files=new File(target.getFilesDir(),"crashes").listFiles();ok(files!=null&&files.length==3,"Crash retention did not keep exactly three files");
                String latest=CrashLog.latest(target);ok(!latest.contains("SECRET")&&!latest.contains("1250")&&!latest.contains("1234567890"),"Crash log contains a financial exception message");
                settings("capture");ok(has("إعدادات البطارية"),"Battery card absent before exemption");shot(target,"02-battery-restricted");
                sms=(SmsImportActivity)runner.startActivitySync(new Intent(target,SmsImportActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
                Object session=get(sms,"session");runner.runOnMainSync(()->{try{
                    set(session,"reviewBeforeImport",true);
                    Method scan=SmsImportActivity.class.getDeclaredMethod("scan",java.time.LocalDate.class);scan.setAccessible(true);scan.invoke(sms,java.time.LocalDate.now().minusDays(1));
                }catch(Exception error){throw new RuntimeException(error);}});
                for(int i=0;i<100&&!"ready".equals(get(session,"phase"));i++){android.os.SystemClock.sleep(100);idle();}
                ok("ready".equals(get(session,"phase")),"Synthetic inbox preview did not finish");
                // 0.4.0: the import lists only what the new pipeline reads; unknown formats are no longer offered from here.
                ok(!has("أرسل صيغتها")&&!has("صيغة غير متعرّفة"),"The import still offers unknown formats for support");
                ok(AppResources.database(target).transactionCount()==0,"Support preview wrote a financial transaction");
                runner.runOnMainSync(sms::finish);sms=null;idle();
                // m6 (0.4.0): a message from a known bank that the pipeline did not record, and that names an amount, waits on
                // «رسائل من بنوكك ما اتسجلتش» with its text redacted, to report or dismiss. Other senders never appear there.
                ExpenseDb store=AppResources.database(target);long now=System.currentTimeMillis();
                String unread="Payment format SUPPORT_QA ref 44556677 from SECRET PERSON amount EGP 7000.00";
                store.captureSms("HSBC",unread,now);
                store.captureSms("QASHOP","QASHOP offer: EGP 100 off your next order today only",now+1000);
                // Review of task C: offers (a Banque Misr loan with an amount), codes, balances and reminders never land there.
                store.captureSms("BankMisr","احصل على قرض شخصي حتى 1,000,000 جنيه بفائدة مميزة. اتصل الآن 19888",now+2000);
                store.captureSms("HSBC","Your card ****4321 statement is issued with total EGP 7000, minimum due is EGP 6000, due before 26-11-2026",now+3000);
                ok(store.transactionCount()==0,"A message the pipeline did not record became an operation");
                settings("capture");ok(has("رسائل من بنوكك ما اتسجلتش"),"Missed bank messages row absent from capture settings");
                click("رسائل من بنوكك ما اتسجلتش");
                ok(has("Payment format")&&!has("44556677")&&!has("7000"),"Missed message not shown redacted");
                ok(!has("QASHOP")&&!has("قرض شخصي")&&!has("minimum due"),"An offer, a reminder or a non-bank sender listed as a missed bank message");
                ok(has("بلّغ عنها")&&has("تجاهل"),"Report and dismiss actions absent");shot(target,"04-missed-bank-messages");
                click("بلّغ عنها");ok(has("الرسالة اتقرت غلط؟")&&has("المرسل: HSBC"),"Report draft did not open with the message");
                Intent missed=interceptMail();
                ok(missed!=null&&missed.getStringExtra(Intent.EXTRA_TEXT).contains("Payment format")&&!missed.getStringExtra(Intent.EXTRA_TEXT).contains("44556677"),"Report draft not redacted");
                settings("missed");click("تجاهل");
                ok(!has("Payment format")&&has("مفيش رسائل"),"Dismissed message still listed");
                // «تجاهل» teaches the shape: the same message delivered again does not come back.
                store.captureSms("HSBC",unread,now+60_000L);settings("missed");
                ok(!has("Payment format")&&has("مفيش رسائل"),"A dismissed message came back when delivered again");
                // Task D2: «لقينا خصم متكرر» offers «مش التزام · تجاهل» for good, and «أضفه» opens the monthly-commitment page
                // filled in (editable before saving) instead of a two-button dialog.
                store.addManualEntry(now-30L*86_400_000L,9900,"EGP","QA GYM","اشتراكات",null,"expense",null,null);
                store.addManualEntry(now-60_000L,9900,"EGP","QA GYM","اشتراكات",null,"expense",null,null);
                recurringTab();
                ok(has("لقينا خصم متكرر من QA GYM"),"Recurring suggestion card absent");
                ok(has("مش التزام · تجاهل"),"Recurring card has no dismiss action");shot(target,"05-recurring-card");
                clickIn("لقينا خصم متكرر من QA GYM","أضفه");
                ok(has("حوّلها لالتزام شهري")&&has("QA GYM")&&has("99.00")&&has("حفظ الالتزام"),"«أضفه» did not open the filled monthly-commitment page: "+dump());
                ok(!has("نسجّله التزام شهري؟"),"The old two-button dialog opened");
                runner.runOnMainSync(main::onBackPressed);idle();
                recurringTab();clickIn("لقينا خصم متكرر من QA GYM","مش التزام · تجاهل");
                ok(!has("لقينا خصم متكرر من QA GYM"),"Dismissed recurring card still shown");
                ok(store.dismissedRecurringKeys().contains(ExpenseDb.recurringKey("QA GYM",9900,"EGP")),"Dismissal not stored under its recurring key");
                recurringTab();ok(!has("لقينا خصم متكرر من QA GYM"),"Dismissed recurring card came back after a refresh");
                // Task D3: a transfer to a person leaves «تحويلات غرضها مش محدد» once its purpose is set, by each of the three
                // ways, and stays out after the data check and reopening the app.
                long sent=now-120_000L;
                CaptureResult a=store.captureSms("QABANK","You sent EGP 101.00 to QA FRIEND A",sent);
                CaptureResult b=store.captureSms("QABANK","You sent EGP 102.00 to QA FRIEND B",sent+1000);
                CaptureResult c=store.captureSms("QABANK","You sent EGP 103.00 to QA FRIEND C",sent+2000);
                ok(a.transaction!=null&&b.transaction!=null&&c.transaction!=null&&"تحويلات".equals(a.transaction.category),"Transfers to people not recorded as open transfers");
                ok(openTransfers()==3,"Banner before: "+openTransfers());
                // (a) the transaction's edit page: TransactionMutation, as EditTransactionPage saves it.
                ExpenseDb.Transaction first=a.transaction;
                ok(new TransactionMutation(store).update(first.id,first.amountMinor,first.currency,first.merchant,"أكل وشرب",first.paymentSourceId,"expense",null,"",first.when,false).changed(),"Edit not saved");
                ok(openTransfers()==2,"Edited transfer still open: "+openTransfers());
                // (b) «حدد الغرض بس»: the purpose list.
                runner.runOnMainSync(()->{try{Method purpose=MainActivity.class.getDeclaredMethod("purposeDialog",List.class);purpose.setAccessible(true);
                    purpose.invoke(main,java.util.Collections.singletonList(b.transaction.id));}catch(Exception error){throw new RuntimeException(error);}});idle();
                click("عائلة ومساعدات");
                ok(openTransfers()==1,"Purpose-set transfer still open: "+openTransfers());
                // (c) the banner's bulk selection: «حدد» → select → «تغيير تصنيف المحدد» → a category → «اعتماد التصنيف».
                click("حدد");
                runner.runOnMainSync(()->{try{@SuppressWarnings("unchecked") java.util.Map<Long,android.widget.CheckBox> checks=(java.util.Map<Long,android.widget.CheckBox>)get(main,"bulkChecks");
                    checks.get(c.transaction.id).setChecked(true);}catch(Exception error){throw new RuntimeException(error);}});idle();
                click("تغيير تصنيف المحدد");click("غير مصنف");click("بقالة");click("اعتماد التصنيف");
                ok(openTransfers()==0,"Bulk-categorized transfer still open: "+openTransfers());
                // (d) The owner's case: the app re-reads a still-shown InstaPay notification on resume (0.3.2). Its purpose,
                // once set, must survive the same delivery read again.
                CaptureResult d=store.captureNotification("com.egyptianbanks.instapay","InstaPay","إرسال نقود لقد أرسلت 104.00 جنيه إلى qa.friend.d@instapay",sent+3000);
                ok(d.transaction!=null&&openTransfers()==1,"InstaPay transfer not open: "+openTransfers());
                long ledgerRows=store.transactionCount();
                runner.runOnMainSync(()->{try{Method purpose=MainActivity.class.getDeclaredMethod("purposeDialog",List.class);purpose.setAccessible(true);
                    purpose.invoke(main,java.util.Collections.singletonList(d.transaction.id));}catch(Exception error){throw new RuntimeException(error);}});idle();
                click("عائلة ومساعدات");ok(openTransfers()==0,"Purpose-set InstaPay transfer still open");
                store.captureNotification("com.egyptianbanks.instapay","InstaPay","إرسال نقود لقد أرسلت 104.00 جنيه إلى qa.friend.d@instapay",sent+3000); // a replay keeps the original post time
                ok(store.transactionCount()==ledgerRows&&openTransfers()==0,"The re-read notification brought the transfer back: rows+"+(store.transactionCount()-ledgerRows)+", open "+openTransfers());
                for(long id:new long[]{a.transaction.id,b.transaction.id,c.transaction.id,d.transaction.id})
                    ok(scalar(store,"SELECT user_edited FROM transactions WHERE id="+id)==1,"Purpose not marked as the user's: "+id);
                store.repairAll();
                ok(openTransfers()==0,"Open transfers came back after the data check: "+openTransfers());
                runner.runOnMainSync(main::finish);idle();
                main=(MainActivity)runner.startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));idle();
                ok(openTransfers()==0,"Open transfers came back after reopening: "+openTransfers());
            }
            result.putString("stream","Support aids UI checks passed: "+checked+"; mode="+mode+"\n");
        }catch(Throwable error){status=0;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","Support aids UI FAILED: "+trace);}
        finally{if(sms!=null)runner.runOnMainSync(sms::finish);if(main!=null)runner.runOnMainSync(main::finish);runner.finish(status,result);}
    }
}
