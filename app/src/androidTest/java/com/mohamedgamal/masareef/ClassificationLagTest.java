package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Imported synthetic ledger only. Keeps all failures so baseline evidence includes every mutation path. */
final class ClassificationLagTest {
    private final Instrumentation runner;
    private ExpenseDb db;
    private MainActivity activity;
    private final StringBuilder report=new StringBuilder();
    private int checks, failures;
    ClassificationLagTest(Instrumentation runner){this.runner=runner;}
    private interface Action{void run()throws Exception;}
    private void main(Action action){Throwable[] error={null};runner.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});runner.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private Object invoke(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private void log(String text){report.append(text).append('\n');android.util.Log.i("FM-classify-test",text);}
    private void check(boolean value,String text){checks++;if(!value){failures++;log("FAIL "+text);}else log("PASS "+text);}
    private View text(View node,String value){if(node instanceof TextView&&value.contentEquals(((TextView)node).getText()))return node;if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){View found=text(((ViewGroup)node).getChildAt(i),value);if(found!=null)return found;}return null;}
    private void click(String value){main(()->{View found=text(activity.getWindow().getDecorView(),value);if(found==null)throw new AssertionError("Missing action "+value);found.performClick();});}
    private void choice(String value)throws Exception{runner.getUiAutomation().waitForIdle(100,3000);for(android.view.accessibility.AccessibilityNodeInfo n:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(value)){if(n.getText()!=null&&value.contentEquals(n.getText()))for(;n!=null;n=n.getParent())if(n.isClickable()&&n.performAction(16)){runner.waitForIdleSync();return;}}throw new AssertionError("Missing choice "+value);}
    private void drain()throws Exception{for(int i=0;i<4;i++){CountDownLatch done=new CountDownLatch(1);CaptureQueue.submit(done::countDown);if(!done.await(30,TimeUnit.SECONDS))throw new AssertionError("Worker stalled");runner.waitForIdleSync();}}
    private long capture(String sender,String body,long time){CaptureResult result=db.captureSms(sender,body,time);if(result.transaction==null)throw new AssertionError("Fixture rejected: "+body+" / "+result.reason);return result.transaction.id;}
    private void state(String stage,long...ids){for(long id:ids)try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,category,kind,needs_review,user_edited,unique_key,(SELECT COUNT(*) FROM transactions d WHERE d.amount_minor=t.amount_minor AND ABS(d.occurred_at-t.occurred_at)<120000) FROM transactions t WHERE id=?",new String[]{""+id})){if(c.moveToFirst())log(stage+" id="+id+" category="+c.getString(1)+" kind="+c.getString(2)+" review="+c.getInt(3)+" edited="+c.getInt(4)+" key="+c.getString(5)+" sameAmountTime="+c.getInt(6));}}
    private long scalar(String query){try(Cursor c=db.getReadableDatabase().rawQuery(query,null)){c.moveToFirst();return c.getLong(0);}}
    private int attention(){int count=0;for(ExpenseDb.Transaction t:db.transactionsForMonth(LocalDate.now().withDayOfMonth(1)))if("expense".equals(t.kind)&&MoneyFlow.UNCATEGORIZED.equals(t.category)||t.needsReview)count++;return count;}
    private void tab(int tab){main(()->{set("selectedTab",tab);invoke("render",new Class<?>[0]);});}
    private void timings()throws Exception{
        LocalDate start=LocalDate.now().withDayOfMonth(1),end=start.plusMonths(1);ZoneId zone=ZoneId.systemDefault();
        long at=SystemClock.elapsedRealtime();db.flowItems(start,end,zone);log("TIMING flowItems="+(SystemClock.elapsedRealtime()-at));
        at=SystemClock.elapsedRealtime();db.reimbursementOffsets();log("TIMING reimbursementOffsets="+(SystemClock.elapsedRealtime()-at));
        at=SystemClock.elapsedRealtime();db.recurringCandidateTransactions();log("TIMING recurringCandidates="+(SystemClock.elapsedRealtime()-at));
        at=SystemClock.elapsedRealtime();new CaptureEvents(db).timelineBetween(start.atStartOfDay(zone).toInstant().toEpochMilli(),end.atStartOfDay(zone).toInstant().toEpochMilli());log("TIMING CaptureEvents.timelineBetween="+(SystemClock.elapsedRealtime()-at));
        for(int i=0;i<4;i++)for(int target:new int[]{0,6}){final long[] ms={0};main(()->{set("selectedTab",target);long before=SystemClock.elapsedRealtime();invoke("render",new Class<?>[0]);ms[0]=SystemClock.elapsedRealtime()-before;});log("TIMING render tab="+target+" sample="+i+" ms="+ms[0]);}
        for(String method:new String[]{"homeScreen","transactionsScreen","homeSourceCards"})main(()->{((ViewGroup)get("page")).removeAllViews();long before=SystemClock.elapsedRealtime();invoke(method,new Class<?>[0]);log("TIMING "+method+"="+(SystemClock.elapsedRealtime()-before));});
        main(()->{long before=SystemClock.elapsedRealtime();invoke("transactionTimeline",new Class<?>[]{List.class},db.transactionsForMonth(start));log("TIMING transactionTimeline="+(SystemClock.elapsedRealtime()-before));});
    }
    void run(){Bundle result=new Bundle();Context target=runner.getTargetContext();int status=0;
        try{
            if(!target.getPackageName().endsWith(".journeyqa")||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException("Disposable isolated emulator required");
            db=new ExpenseDb(target);if(db.transactionCount()!=0)throw new IllegalStateException("Empty QA ledger required");
            target.getSharedPreferences(MainActivity.class.getName(),0).edit().putBoolean("intro_completed",true).putString("opening_mode","off").putBoolean("sms_permission_prompted",true).putBoolean("initial_import_prompted",true).putInt("repair_version",ExpenseDb.REPAIR_VERSION).commit();
            target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).putBoolean("source_picker_shown",true).commit();CaptureNotifier.setEnabled(target,false);
            long now=System.currentTimeMillis()-3600000;
            List<SmsPreview> rows=new ArrayList<>();for(int i=0;i<600;i++){long at=LocalDate.now().withDayOfMonth(1).minusMonths(i%3).plusDays((i/3)%20).atTime(9,(i/60)%60,i%60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();rows.add(SmsPreview.parse("BANK","Card purchase EGP "+(20+i)+" at QA SHOP "+i,at));}ImportRun.added(db,rows);check(db.transactionCount()==600,"600 transactions imported across three months");
            long single=capture("BANK","Card purchase EGP 901.11 at QA SINGLE",now),bulk=capture("BANK","Card purchase EGP 902.22 at QA BULK",now+1000);
            long transfer=capture("InstaPay","You have sent EGP 903.33 to qa.friend@instapay",now+2000);
            long income=capture("BANK","Refund EGP 904.44 from QA RETURN",now+3000);
            state("seed",single,bulk,transfer,income);check(db.transactionById(income).needsReview,"Unlinked captured refund needs review");
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));drain();timings();
            tab(6);int before=attention();
            main(()->{set("selectedTab",10);set("selectedTransaction",db.transactionById(single));set("transactionReturnTab",6);invoke("render",new Class<?>[0]);invoke("editTransactionDialog",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(single));});
            click("كل التصنيفات");choice("صحة");main(()->activity.findViewById(android.R.id.content).findViewWithTag("ui_page_save").performClick());drain();state("single-save",single);check(attention()==before-1,"Single UI edit decreases attention banner");
            main(()->invoke("purposeDialog",new Class<?>[]{List.class},Collections.singletonList(transfer)));choice("عائلة ومساعدات");state("purpose-save",transfer);check("عائلة ومساعدات".equals(db.transactionById(transfer).category),"Purpose persists immediately");
            tab(6);main(()->{set("bulkMode",true);invoke("render",new Class<?>[0]);});
            main(()->{@SuppressWarnings("unchecked") Map<Long,CheckBox> boxes=(Map<Long,CheckBox>)get("bulkChecks");for(long id:new long[]{bulk,income,transfer}){CheckBox box=boxes.get(id);check(box!=null&&box.isEnabled(),"Bulk can select attention row "+id);if(box!=null&&box.isEnabled())box.performClick();}});
            click("تغيير تصنيف المحدد");click("غير مصنف");choice("بقالة");click("اعتماد التصنيف");drain();state("bulk-save",bulk,income,transfer);check(!db.transactionById(income).needsReview,"Bulk clears income review without changing kind/category");
            // Isolate the automatic conflict marker, used by mergeEntries and canReconcile. No synthetic SQL writes.
            Method marker=ExpenseDb.class.getDeclaredMethod("markMergeReview",long.class,long.class);marker.setAccessible(true);marker.invoke(db,single,bulk);state("automatic-conflict-marker",single,bulk);check(!db.transactionById(single).needsReview&&!db.transactionById(bulk).needsReview,"Automatic conflict marker respects explicit user review");
            tab(0);tab(6);main(()->{runner.callActivityOnPause(activity);runner.callActivityOnResume(activity);});drain();capture("BANK","Card purchase EGP 905.55 at QA NEW SMS",now+4000);SmsReceiver.notifyDataChanged(target);drain();state("navigate-resume-new-sms",single,bulk,income,transfer);
            check("صحة".equals(db.transactionById(single).category),"Single category survives navigation/resume/SMS");check("بقالة".equals(db.transactionById(bulk).category),"Bulk category survives navigation/resume/SMS");log("attention-after="+attention());
            status=failures==0?-1:0;
        }catch(Throwable error){failures++;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));log(trace.toString());}
        finally{try{if(activity!=null)main(activity::finish);if(db!=null){db.clearAllData();db.close();}}catch(Throwable error){failures++;log(error.toString());}result.putString("stream",report+"Classification/lag checks passed: "+(checks-failures)+"; failed: "+failures+"\n");runner.finish(failures==0?status:0,result);}
    }
}
