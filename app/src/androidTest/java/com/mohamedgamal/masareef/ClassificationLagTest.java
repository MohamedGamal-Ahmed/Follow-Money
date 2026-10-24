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
    private void click(String value){main(()->{View found=text(activity.getWindow().getDecorView(),value);if(found==null)throw new AssertionError("Missing action "+value);while(!found.hasOnClickListeners()&&found.getParent() instanceof View)found=(View)found.getParent();if(!found.performClick())throw new AssertionError("Action did not click "+value);});}
    private void choice(String value)throws Exception{runner.getUiAutomation().waitForIdle(100,3000);for(android.view.accessibility.AccessibilityNodeInfo n:runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(value)){if(n.getText()!=null&&value.contentEquals(n.getText()))for(;n!=null;n=n.getParent())if(n.isClickable()&&n.performAction(16)){runner.waitForIdleSync();return;}}throw new AssertionError("Missing choice "+value);}
    private void drain()throws Exception{for(int i=0;i<4;i++){CountDownLatch done=new CountDownLatch(1);CaptureQueue.submit(done::countDown);if(!done.await(30,TimeUnit.SECONDS))throw new AssertionError("Worker stalled");runner.waitForIdleSync();}}
    private java.io.File captures;
    private void screen(String name)throws Exception{runner.waitForIdleSync();runner.getUiAutomation().waitForIdle(500,5000);android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(captures,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    /** The transactions list scrolled to its end, where «عرض عمليات أقدم» waits, at the given font scale. */
    private void olderScreen(float scale,String name)throws Exception{
        main(()->{android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());config.fontScale=scale;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
            set("bulkMode",false);invoke("resetTransactionFilters",new Class<?>[0]);set("selectedTab",6);invoke("render",new Class<?>[0]);});
        main(()->{android.widget.ScrollView scroll=(android.widget.ScrollView)get("pageScroll");scroll.scrollTo(0,scroll.getChildAt(0).getHeight());});
        main(()->check(findMore(activity.getWindow().getDecorView())!=null,"Older operations action shown at font scale "+scale));screen(name);
    }
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
        for(int i=0;i<4;i++)for(int target:new int[]{0,6}){
            final long[] ms={0,0};CountDownLatch drawn=new CountDownLatch(1);
            main(()->{set("selectedTab",target);long before=SystemClock.elapsedRealtime();invoke("render",new Class<?>[0]);ms[0]=SystemClock.elapsedRealtime()-before;
                View root=activity.findViewById(android.R.id.content);root.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){root.getViewTreeObserver().removeOnPreDrawListener(this);ms[1]=SystemClock.elapsedRealtime()-before;drawn.countDown();return true;}});root.invalidate();});
            if(!drawn.await(5,TimeUnit.SECONDS))throw new AssertionError("Tab did not draw");
            log("TIMING render tab="+target+" sample="+i+" ms="+ms[0]+" preDrawMs="+ms[1]);
        }
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
            captures=new java.io.File(target.getCacheDir(),"classification-lag");captures.mkdirs();
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
            long replayCount=db.transactionCount();db.captureSms("BANK","Card purchase EGP 901.11 at QA SINGLE",now);db.captureSms("InstaPay","You have sent EGP 903.33 to qa.friend@instapay",now+2000);
            check(db.transactionCount()==replayCount&&"صحة".equals(db.transactionById(single).category)&&"بقالة".equals(db.transactionById(transfer).category)&&!db.transactionById(single).needsReview,"Rereading original SMS preserves explicit choices without duplicates");state("original-sms-replay",single,transfer);
            // Revision cache observes a different helper, protects rollback, and does not expose mutable cached lists.
            LocalDate month=LocalDate.now().withDayOfMonth(1);int initial=db.transactionsForMonth(month).size();
            db.transactionsForMonth(month).clear();check(db.transactionsForMonth(month).size()==initial,"Cached list cannot be corrupted by callers");
            try(ExpenseDb external=new ExpenseDb(target)){external.updateCategories(Collections.singletonList(single),"مقاهي");}
            check(db.transactionsForMonth(month).stream().anyMatch(t->t.id==single&&"مقاهي".equals(t.category)),"Cache invalidates after another helper commits a category-only edit");
            db.getWritableDatabase().beginTransaction();try{db.updateCategories(Collections.singletonList(single),"تسوق");check(db.transactionsForMonth(month).stream().anyMatch(t->t.id==single&&"تسوق".equals(t.category)),"Transaction reads bypass committed cache");}finally{db.getWritableDatabase().endTransaction();}
            check(db.transactionsForMonth(month).stream().anyMatch(t->t.id==single&&"مقاهي".equals(t.category)),"Rolled-back rows never enter cache");
            ExpenseDb.ClassificationResult noop=db.classifyReviewed(Arrays.asList(bulk,bulk,999999L),"بقالة");check(noop.categorized==0&&noop.reviewed==0&&noop.unchanged==2,"Bulk reports unchanged/missing rows honestly and deduplicates IDs");
            long reviewed=capture("BANK","Refund EGP 906.66 from QA ACKNOWLEDGE",now+5000);ExpenseDb.Transaction original=db.transactionById(reviewed);
            main(()->{set("selectedTab",10);set("selectedTransaction",original);invoke("render",new Class<?>[0]);});screen("20-acknowledge-page");click("تمام، دي صح");drain();
            ExpenseDb.Transaction acknowledged=db.transactionById(reviewed);check(!acknowledged.needsReview&&original.category.equals(acknowledged.category)&&original.kind.equals(acknowledged.kind)&&original.amountMinor==acknowledged.amountMinor,"Acknowledgement clears review without changing financial fields");state("acknowledged",reviewed);
            long from=db.addPaymentSource("QA Bank","حساب","5678"),to=db.addPaymentSource("QA OTHER","حساب","2222"),card=db.addPaymentSource("QA Bank Card","بطاقة ائتمان","1234");
            long debit=capture("BANK","You sent EGP 100.50 to QA OWN ACCOUNT",now+6000);db.reconcileOwnTransfer(debit,10000,50,from,to,Collections.emptyList());
            check("transfer".equals(db.transactionById(debit).kind)&&db.transferFee(debit)==50,"Imported own-account debit retains principal and separate fee");state("own-transfer-with-fee",debit);
            long repayment=capture("QA Bank","Transfer of EGP 4,000.00 from your account ****5678 to credit card ****1234",now+7000);
            tab(6);main(()->check(!(Boolean)invoke("isSentTransfer",new Class<?>[]{ExpenseDb.Transaction.class},db.transactionById(repayment)),"Own-card repayment is excluded from both open-transfer banner and filter"));
            long person=capture("BANK","You sent EGP 907.77 to QA PERSON",now+8000),foreign=capture("BANK","You sent USD 908.88 to QA FOREIGN",now+9000);
            main(()->{invoke("resetTransactionFilters",new Class<?>[0]);set("bulkMode",false);set("transactionKindFilter","income");set("transactionCategoryFilter","صحة");set("transactionCurrencyFilter","USD");set("transactionSourceFilter",from);set("transactionSearch","stale search");invoke("render",new Class<?>[0]);});click("حدد");
            main(()->{@SuppressWarnings("unchecked") Map<Long,CheckBox> boxes=(Map<Long,CheckBox>)get("bulkChecks");check(boxes.containsKey(person)&&!boxes.containsKey(foreign)&&!boxes.containsKey(repayment),"Transfer banner clears stale filters and lists exactly its EGP person transfers");});
            main(()->invoke("resetTransactionFilters",new Class<?>[0]));tab(6);click("عرض الكل");main(()->{int matches=0;for(ExpenseDb.Transaction tx:db.transactionsForMonth(month))if((Boolean)invoke("matchesTransactionFilters",new Class<?>[]{ExpenseDb.Transaction.class,String.class},tx,""))matches++;check(matches==attention(),"Attention banner count equals its list predicate");});
            main(()->{invoke("resetTransactionFilters",new Class<?>[0]);set("selectedTab",6);invoke("render",new Class<?>[0]);});drain();Object rendered=get("pageScroll");main(()->invoke("refreshLedger",new Class<?>[]{boolean.class},true));drain();check(rendered==get("pageScroll"),"Unchanged refresh does not render a second time");
            main(()->{set("bulkMode",true);invoke("render",new Class<?>[0]);});click("اختيار النتائج كلها");main(()->{check(((Set<?>)get("bulkSelected")).size()>((Map<?,?>)get("bulkChecks")).size(),"Select all includes matches beyond rendered page");check(((Map<?,?>)get("bulkChecks")).size()<=12,"Initial list creation is bounded");});click("إلغاء التحديد");
            android.widget.Button more=findMore(activity.getWindow().getDecorView());if(more==null)throw new AssertionError("Missing older operations action");main(more::performClick);main(()->check(((Map<?,?>)get("bulkChecks")).size()==24,"Older rows load without losing logical filters"));
            main(()->{set("bulkMode",false);invoke("resetTransactionFilters",new Class<?>[0]);});log("TIMING with bank/card sources and review fixtures");timings();
            // REPAIR_VERSION 5: an edited row that 0.4.2 flagged again gets its review back; nothing else moves.
            long edited=capture("BANK","Card purchase EGP 911.11 at QA REPAIR EDITED",now+10000),pending=capture("BANK","Refund EGP 912.12 from QA REPAIR PENDING",now+11000);
            long survivor=capture("BANK","Card purchase EGP 913.13 at QA REPAIR SURVIVOR",now+12000);
            db.updateCategories(Arrays.asList(edited,survivor),"صحة");
            // The 0.4.2 state cannot be produced by current code (that is the fix), so it is written as the old marker did.
            db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id IN (?,?)",new Object[]{edited,survivor});
            String survivorKey;try(Cursor c=db.getReadableDatabase().rawQuery("SELECT unique_key FROM capture_evidence WHERE transaction_id=? LIMIT 1",new String[]{""+survivor})){check(c.moveToFirst(),"Survivor has evidence");survivorKey=c.getString(0);}
            android.content.ContentValues archived=new android.content.ContentValues();archived.put("original_id",999998L);archived.put("reason",ExpenseDb.ABBREVIATED_COPY_REASON);
            archived.put("archived_at",System.currentTimeMillis());archived.put("payload",new org.json.JSONObject().put("unique_key",survivorKey).toString());
            db.getWritableDatabase().insert("transaction_archive",null,archived);
            ExpenseDb.Transaction editedBefore=db.transactionById(edited);check(editedBefore.needsReview&&db.transactionById(pending).needsReview&&db.transactionById(survivor).needsReview,"Repair fixtures flagged");
            ExpenseDb.RepairReport repaired=db.repairAll();ExpenseDb.Transaction editedAfter=db.transactionById(edited);state("repair-v5",edited,pending,survivor);
            check(repaired.reviewsRestored==1&&!editedAfter.needsReview,"Edited row flagged by 0.4.2 is cleared: "+repaired.reviewsRestored);
            check(editedBefore.category.equals(editedAfter.category)&&editedBefore.kind.equals(editedAfter.kind)&&editedBefore.amountMinor==editedAfter.amountMinor&&"صحة".equals(editedAfter.category),"Repair touches only needs_review");
            check(db.transactionById(pending).needsReview,"Unedited flagged row stays flagged");
            check(db.transactionById(survivor).needsReview,"Abbreviated-merge survivor keeps its own flag");
            check(db.repairAll().reviewsRestored==0&&!db.transactionById(edited).needsReview&&db.transactionById(pending).needsReview,"Second repair run changes nothing");
            check(ExpenseDb.REPAIR_VERSION==5,"Repair version 5 re-runs once on 0.4.2 phones");
            olderScreen(1f,"21-transactions-older-1.0");olderScreen(2f,"22-transactions-older-2.0");olderScreen(1f,"23-transactions-restored");
            status=failures==0?-1:0;
        }catch(Throwable error){failures++;java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));log(trace.toString());}
        finally{try{if(activity!=null)main(activity::finish);if(db!=null){db.clearAllData();db.close();}}catch(Throwable error){failures++;log(error.toString());}result.putString("stream",report+"Classification/lag checks passed: "+(checks-failures)+"; failed: "+failures+"\n");runner.finish(failures==0?status:0,result);}
    }
    private android.widget.Button findMore(View node){if(node instanceof android.widget.Button&&((TextView)node).getText().toString().startsWith("عرض عمليات أقدم"))return (android.widget.Button)node;if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){android.widget.Button found=findMore(((ViewGroup)node).getChildAt(i));if(found!=null)return found;}return null;}
}
