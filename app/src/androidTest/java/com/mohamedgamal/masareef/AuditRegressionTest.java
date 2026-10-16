package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.app.Notification;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/** Focused audit regressions. All ledger/settings writes use disposable emulator fixtures. */
final class AuditRegressionTest {
    private final Instrumentation runner;
    private ExpenseDb db;
    private Context context;
    private File file;
    AuditRegressionTest(Instrumentation runner){this.runner=runner;}
    private long at(LocalDate day,int hour){return day.atTime(hour,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    private void eq(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private void ok(boolean value){if(!value)throw new AssertionError("expected true");}
    private void setup(){
        Context target=runner.getTargetContext();file=new File(target.getCacheDir(),"audit-regression.db");SQLiteDatabase.deleteDatabase(file);
        context=new ContextWrapper(target){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.getPath(),factory,handler);}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences("audit-regression-"+name,mode);}
        };
        context.getSharedPreferences("widget_settings",0).edit().clear().commit();
        db=new ExpenseDb(context);db.getWritableDatabase();
    }
    void run(){
        if(!"ranchu".equals(android.os.Build.HARDWARE)&&!"goldfish".equals(android.os.Build.HARDWARE))throw new SecurityException("Emulator only");
        StringBuilder report=new StringBuilder();int passed=0,failed=0;
        for(java.lang.reflect.Method method:getClass().getDeclaredMethods()){
            if(!method.getName().startsWith("test"))continue;
            try{setup();method.invoke(this);passed++;report.append(method.getName()).append(": passed\n");}
            catch(Throwable error){failed++;report.append(method.getName()).append(": FAILED ").append(error.getCause()==null?error:error.getCause()).append('\n');}
            finally{if(db!=null)db.close();if(file!=null)SQLiteDatabase.deleteDatabase(file);}
        }
        Bundle result=new Bundle();result.putString("stream",report+"Passed: "+passed+"; failed: "+failed+"\n");runner.finish(failed==0?-1:0,result);
    }
    private long manual(long when,long amount,String kind){
        ok(db.addManualEntry(when,amount,"EGP","QA "+kind,"income".equals(kind)?"دخل":"تسوق",null,kind,null,null));
        return db.latestInserted().id;
    }
    private long scalar(String sql){try(Cursor cursor=db.getReadableDatabase().rawQuery(sql,null)){ok(cursor.moveToFirst());return cursor.getLong(0);}}

    public void testAutomaticPurchaseMergePreservesRepaymentFeesOriginAndEvidence(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));long note=db.latestInserted().id;
        long back=manual(t+1000,4000,"income");eq(4000,db.linkReimbursement(back,note));
        long fee=manual(t,200,"expense");
        db.getWritableDatabase().execSQL("UPDATE transactions SET fee_parent_id=? WHERE id=?",new Object[]{note,fee});
        ok(db.addRecurring("QA recurring",10000,"EGP",LocalDate.now().plusMonths(1)));
        db.getWritableDatabase().execSQL("UPDATE recurring SET origin_transaction_id=?",new Object[]{note});
        ok(db.addSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000));
        ExpenseDb.Transaction kept=db.lastCaptured();ok(kept!=null);ok(db.transactionById(note)==null);
        eq(6000,ExpenseDb.effectiveAmount(kept,db.reimbursementOffsets()));
        eq(0,ExpenseDb.effectiveAmount(db.transactionById(back),db.reimbursementOffsets()));
        eq(kept.id,scalar("SELECT fee_parent_id FROM transactions WHERE id="+fee));
        eq(kept.id,scalar("SELECT origin_transaction_id FROM recurring"));
        eq(2,db.sourceEvidence(kept.id).size());eq(1,scalar("SELECT COUNT(*) FROM reimbursements"));
        db.close();db=new ExpenseDb(context);eq(4000,db.reimbursedOf(kept.id));
    }
    public void testIncomeMergeMovesRepaymentAndRejectsConflictingTargets(){
        long t=at(LocalDate.now(),10),buy=manual(t,10000,"expense");
        long note=manual(t+1000,4000,"income"),bank=manual(t+2000,4000,"income");
        db.linkReimbursement(note,buy);db.reconcileIncome(bank,Collections.singletonList(note));
        ok(db.transactionById(note)==null);eq(4000,db.reimbursedOf(buy));
        eq(0,ExpenseDb.effectiveAmount(db.transactionById(bank),db.reimbursementOffsets()));
        long otherBuy=manual(t,10000,"expense"),otherIncome=manual(t+3000,4000,"income");
        db.linkReimbursement(otherIncome,otherBuy);
        try{db.reconcileIncome(bank,Collections.singletonList(otherIncome));throw new AssertionError("Conflicting repayments merged");}
        catch(IllegalArgumentException expected){/* Entire confirmation rolls back. */}
        ok(db.transactionById(otherIncome)!=null);eq(4000,db.reimbursedOf(buy));eq(4000,db.reimbursedOf(otherBuy));
    }
    public void testOwnTransferRejectsLinkedRepaymentWithoutChangingRows(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10000,"EGP","QA debit","تحويلات",from,"expense",null,null));long debit=db.latestInserted().id;
        long buy=manual(t,20000,"expense"),receipt=manual(t+1000,10000,"income");db.linkReimbursement(receipt,buy);
        try{db.reconcileOwnTransfer(debit,10000,0,from,to,Collections.singletonList(receipt));throw new AssertionError("Linked repayment became transfer");}
        catch(IllegalArgumentException expected){/* Preserve explicit repayment intent. */}
        ok(db.transactionById(receipt)!=null);ok("expense".equals(db.transactionById(debit).kind));eq(10000,db.reimbursedOf(buy));
        long cleanReceipt=manual(t+2000,10000,"income");
        db.reconcileOwnTransfer(debit,10000,0,from,to,Collections.singletonList(cleanReceipt));
        ok(db.transactionById(cleanReceipt)==null);ok("transfer".equals(db.transactionById(debit).kind));eq(10000,db.reimbursedOf(buy));
    }
    public void testAutomaticConflictingPurchaseLinksRemainForReview(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));long note=db.latestInserted().id;
        long a=manual(t+1000,8000,"income");db.linkReimbursement(a,note);
        // Defer automatic repair, allowing a second explicit link to exist on the other copy.
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();long bank;
        try{ok(db.addSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000));bank=db.lastCaptured().id;
            long b=manual(t+3000,8000,"income");db.linkReimbursement(b,bank);sql.setTransactionSuccessful();}
        finally{sql.endTransaction();}
        eq(0,db.repairAround(t).duplicates);
        ok(db.transactionById(note)!=null&&db.transactionById(bank)!=null);
        ok(db.transactionById(note).needsReview&&db.transactionById(bank).needsReview);
        eq(8000,db.reimbursedOf(note));eq(8000,db.reimbursedOf(bank));
    }
    public void testHistoricalImportCountBeyondFiveHundredAndRollback(){
        long t=at(LocalDate.now(),10);SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{for(int i=0;i<501;i++)manual(t-i*60000,100,"expense");sql.setTransactionSuccessful();}finally{sql.endTransaction();}
        eq(500,db.transactions().size());eq(501,db.transactionCount());
        SmsPreview a=SmsPreview.parse("BANK","Card purchase EGP 8 at QA SHOP A",t+1000);
        SmsPreview b=SmsPreview.parse("BANK","Card purchase EGP 9 at QA SHOP B",t+2000);ok(a!=null&&b!=null);
        eq(2,SmsImportActivity.applySelected(db,Arrays.asList(a,b)));eq(503,db.transactionCount());
        eq(0,SmsImportActivity.applySelected(db,Arrays.asList(a,b)));eq(503,db.transactionCount());
        SmsPreview c=SmsPreview.parse("BANK","Card purchase EGP 7 at QA SHOP C",t+3000);
        try{SmsImportActivity.applySelected(db,Arrays.asList(c,null));throw new AssertionError("Failed batch committed");}
        catch(NullPointerException expected){/* Regression fixture injects a failure midway. */}
        eq(503,db.transactionCount());eq(0,db.smsCaptureStatus(c.sender,c.body,c.captureAt));
    }
    public void testCaptureNotificationHidesAmountsAndPublicIdentity(){
        long id=manual(at(LocalDate.now(),10),12345,"expense");ExpenseDb.Transaction tx=db.transactionById(id);
        Notification visible=CaptureNotifier.build(context,tx);
        eq(Notification.VISIBILITY_PRIVATE,visible.visibility);ok(visible.publicVersion!=null);
        ok(visible.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("123.45"));
        String publicCopy=visible.publicVersion.extras.toString();ok(!publicCopy.contains("123.45")&&!publicCopy.contains(tx.merchant));
        context.getSharedPreferences("widget_settings",0).edit().putBoolean("hide_amounts",true).commit();
        Notification hidden=CaptureNotifier.build(context,tx);
        ok(!hidden.extras.toString().contains("123.45")&&!hidden.extras.toString().contains(tx.merchant));
        ok(hidden.publicVersion!=null);eq(Notification.VISIBILITY_PUBLIC,hidden.publicVersion.visibility);
    }
    public void testCsvTextEscapingKeepsNumericAmountsAndQuotedText(){
        ok("\"'=1+1\"".equals(MainActivity.csvCell("=1+1")));
        for(String value:new String[]{"+SUM(A1)","-2+3","@SUM(A1)","  =1+1","\t=1+1","＝1+1"})
            ok(MainActivity.csvCell(value).startsWith("\"'"));
        ok("\"QA, \"\"SHOP\"\"\"".equals(MainActivity.csvCell("QA, \"SHOP\"")));
        String csv="Date,Merchant,Amount,Type,Currency\n2026-10-15,"+MainActivity.csvCell("=1+1")+",-123.45,expense,EGP\n";
        CsvImport.Row row=CsvImport.parse(csv).get(0);eq(12345,row.amountMinor);ok("expense".equals(row.kind));ok("'=1+1".equals(row.merchant));
    }
    public void testDateOnlyEligibilityUsesDayInPreviewAndConfirmation(){
        LocalDate day=LocalDate.now();long received=at(day,16);
        ok(db.addSms("BANK","14OCT26 QA SHOP Purchase EGP 100",at(LocalDate.of(2026,10,15),16)));
        ExpenseDb.Transaction purchase=db.lastCaptured();ok("date_only".equals(purchase.timePrecision));
        long morning=manual(at(LocalDate.of(2026,10,14),9),4000,"income");
        ok(db.reimbursementCandidates(db.transactionById(morning)).stream().anyMatch(tx->tx.id==purchase.id));
        eq(4000,db.confirmReimbursement(db.previewReimbursement(morning,purchase.id)));
        ok("تاريخ فقط".equals(MainActivity.transactionTimeLabel(purchase,false)));
        ok(!MainActivity.transactionTimeLabel(purchase,true).contains("12:00"));
        ok(db.addSms("BANK","Card purchase EGP 101 at QA NOON on "+day+" 12:00",received));long exactNoon=db.lastCaptured().id;
        long early=manual(at(day,9),3000,"income");
        ok("exact".equals(db.transactionById(exactNoon).timePrecision));
        ok(!db.reimbursementCandidates(db.transactionById(early)).stream().anyMatch(tx->tx.id==exactNoon));
        try{db.previewReimbursement(early,exactNoon);throw new AssertionError("Exact later purchase allowed");}catch(IllegalArgumentException expected){}
        ok(!MainActivity.transactionTimeLabel(db.transactionById(exactNoon),false).equals("تاريخ فقط"));
        long laterDay=manual(at(day.plusDays(1),9),6000,"expense");
        ContentValues precision=new ContentValues();precision.put("time_precision","date_only");db.getWritableDatabase().update("transactions",precision,"id=?",new String[]{Long.toString(laterDay)});
        try{db.previewReimbursement(early,laterDay);throw new AssertionError("Future date allowed");}catch(IllegalArgumentException expected){}
        CsvImport.Row row=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CSV,-4.00").get(0);
        ok(db.addCsvEntry("qa-file",row));ok("date_only".equals(db.latestInserted().timePrecision));
        // When only the incoming payment has date precision, its noon must not exclude same-day purchases.
        ok(db.addSms("BANK","You received EGP 50 from QA FRIEND on "+day,received));ExpenseDb.Transaction income=db.lastCaptured();
        long evening=manual(at(day,20),9000,"expense");
        eq(5000,db.previewReimbursement(income.id,evening).applied);
    }
    public void testV20UpgradePreservesLedgerLinksEvidenceAndUnknownPrecision() throws Exception {
        db.close();SQLiteDatabase.deleteDatabase(file);
        try(SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(file,null)){
            String schema;try(java.io.InputStream input=runner.getContext().getAssets().open("schema-v20.sql")){
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[4096];int count;
                while((count=input.read(chunk))!=-1)bytes.write(chunk,0,count);schema=bytes.toString(StandardCharsets.UTF_8.name());
            }
            for(String statement:schema.replaceAll("(?m)^--.*$","").split(";"))if(!statement.isBlank())old.execSQL(statement);
            eq(20,old.getVersion());
            old.execSQL("INSERT INTO transactions(id,unique_key,occurred_at,received_at,amount_minor,currency,merchant,category,source) VALUES(1,'legacy-buy',1000,2000,10000,'EGP','QA OLD','تسوق','SMS')");
            old.execSQL("INSERT INTO transactions(id,unique_key,occurred_at,received_at,amount_minor,currency,merchant,category,source,kind,user_edited) VALUES(2,'legacy-back',3000,3000,4000,'EGP','QA BACK','دخل','يدوي','income',1)");
            old.execSQL("INSERT INTO reimbursements VALUES(2,1,4000)");
            old.execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,source,received_at,encrypted_body) VALUES('qa-evidence',1,'SMS',2000,'opaque-legacy-evidence')");
        }
        db=new ExpenseDb(context);eq(21,db.getWritableDatabase().getVersion());eq(2,db.transactionCount());
        eq(4000,db.reimbursedOf(1));eq(6000,ExpenseDb.effectiveAmount(db.transactionById(1),db.reimbursementOffsets()));
        ok(db.transactionById(1).timePrecision==null&&db.transactionById(2).timePrecision==null);
        eq(1,scalar("SELECT user_edited FROM transactions WHERE id=2"));
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT encrypted_body FROM capture_evidence",null)){ok(cursor.moveToFirst());ok("opaque-legacy-evidence".equals(cursor.getString(0)));}
        eq(1,scalar("SELECT COUNT(*) FROM pragma_integrity_check WHERE integrity_check='ok'"));
        // A legacy noon remains unknown on read; no migration guessing or data backfill.
        db.close();db=new ExpenseDb(context);ok(db.transactionById(1).timePrecision==null);eq(4000,db.reimbursedOf(1));
    }

    public void testLegacyPrecisionStaysUnknownOnReimportAndFreshNoonIsExact(){
        long t=at(LocalDate.now(),16);String message="Card purchase EGP 8 at QA OLD on "+LocalDate.now();
        ok(db.addSms("BANK",message,t));long id=db.lastCaptured().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET time_precision=NULL WHERE id=?",new Object[]{id});
        ok(!db.addSms("BANK",message,t));ok(db.transactionById(id).timePrecision==null);
        ok(!MainActivity.transactionTimeLabel(db.transactionById(id),false).equals("تاريخ فقط"));
        try{db.getWritableDatabase().execSQL("UPDATE transactions SET time_precision='guessed' WHERE id=?",new Object[]{id});
            throw new AssertionError("Invalid precision accepted");}catch(android.database.sqlite.SQLiteConstraintException expected){}
    }
    public void testArchiveEntryRemovesFeeChildrenAndReimbursementLinks(){
        long t=at(LocalDate.now(),10),buy=manual(t,10000,"expense"),back=manual(t+1000,4000,"income"),fee=manual(t,200,"expense");
        db.getWritableDatabase().execSQL("UPDATE transactions SET fee_parent_id=? WHERE id=?",new Object[]{buy,fee});
        db.linkReimbursement(back,buy);
        eq(1,scalar("SELECT COUNT(*) FROM reimbursements"));
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id="+buy));
        db.archiveEntry(buy,"qa archive",true);
        ok(db.transactionById(buy)==null);
        eq(0,scalar("SELECT COUNT(*) FROM reimbursements"));
        eq(0,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id="+buy));
    }
    public void testArchivePromotionsSkipsReimbursementLinkedRows(){
        long t=at(LocalDate.now(),10);
        String promo="Get up to 20% cash back when you pay with your card at AMAZON. Offer valid till 30 Oct. T&Cs apply. EGP 500 max";
        ok(ExpenseParser.isPromotion(promo));
        long linked=manual(t,10000,"expense"),linkedBack=manual(t+1000,4000,"income"),plain=manual(t,7000,"expense");
        db.linkReimbursement(linkedBack,linked);
        String sealed=EvidenceVault.encrypt(promo);
        db.getWritableDatabase().execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,source,received_at,encrypted_body) VALUES(?,?,?,?,?)",
                new Object[]{"qa-promo-linked",linked,"SMS",t,sealed});
        db.getWritableDatabase().execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,source,received_at,encrypted_body) VALUES(?,?,?,?,?)",
                new Object[]{"qa-promo-plain",plain,"SMS",t,sealed});
        eq(1,archivePromotions());
        ok(db.transactionById(linked)!=null);
        ok(db.transactionById(plain)==null);
        eq(1,scalar("SELECT COUNT(*) FROM reimbursements"));
    }
    public void testEarlyPaymentDueLabelIsNotOverdueAfterNormalization(){
        LocalDate yesterday=LocalDate.now().minusDays(1);
        long id=db.putCommitment(null,"قسط الشقة",506000,"EGP",yesterday,"monthly","installment",12,1,1,"قسط الشقة",null);
        ContentValues paid=new ContentValues();paid.put("last_paid_date",yesterday.minusDays(1).toString());
        db.getWritableDatabase().update("recurring",paid,"id=?",new String[]{Long.toString(id)});
        ok(MainActivity.dueWord(db.recurringById(id).nextDue).contains("متأخر"));
        eq(1,db.normalizeEarlyPayments());
        ok(!MainActivity.dueWord(db.recurringById(id).nextDue).contains("متأخر"));
    }
    public void testCaptureQueueRunsWorkInOrderOnOneBackgroundThread(){
        final java.util.List<String> threads=java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        final java.util.List<Integer> order=java.util.Collections.synchronizedList(new java.util.ArrayList<Integer>());
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(3);
        for(int index=1;index<=3;index++){
            final int value=index;
            CaptureQueue.submit(()->{threads.add(Thread.currentThread().getName());order.add(value);done.countDown();});
        }
        try{ok(done.await(10,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
        eq(3,order.size());
        ok(order.get(0)==1&&order.get(1)==2&&order.get(2)==3);
        for(String name:threads) ok("capture-worker".equals(name));
    }
    public void testSmsCaptureNowWritesOnceAndDeduplicates(){
        SmsParts parts=new SmsParts();
        parts.add("BANK",at(LocalDate.now(),10),"Card purchase EGP 100 at QA SHOP");
        SmsReceiver.captureNow(context,parts);
        eq(1,db.transactionCount());
        eq(10000,db.transactions().get(0).amountMinor);
        SmsReceiver.captureNow(context,parts);
        eq(1,db.transactionCount());
    }
    public void testRecurringCandidatesGroupByMerchantInsideTheMonthlyWindow(){
        long t=at(LocalDate.now().minusDays(30),10);
        long older=manual(t,5000,"expense"),newer=manual(t+30L*86400000,5000,"expense"),tooClose=manual(t+14L*86400000,5000,"expense");
        Set<Long> ids=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(ids.contains(newer));
        ok(!ids.contains(tooClose));
        ok(!ids.contains(older));
        long stale=at(LocalDate.now().minusDays(430),10);
        manual(stale,5000,"expense");
        long staleNew=manual(stale+30L*86400000,5000,"expense");
        ids=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(!ids.contains(staleNew));
        db.putCommitment(null,"QA expense",5000,"EGP",LocalDate.now().plusMonths(1),"monthly","installment",12,0,1,"QA expense",null);
        ids=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(!ids.contains(newer));
    }
    public void testRecurringCandidateScanReachesBeyondTheNewestFiveHundredRows(){
        long base=at(LocalDate.now().minusDays(60),10);
        manual(base,4000,"expense");
        long newer=manual(base+30L*86400000,4000,"expense");
        for(int i=0;i<510;i++) manual(base+31L*86400000+i*60000L,3000,"expense");
        eq(512,db.transactionCount());
        eq(500,db.transactions().size());
        ok(!db.transactions().stream().anyMatch(item->item.id==newer));
        Set<Long> ids=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(ids.contains(newer));
    }
    private int archivePromotions(){
        try{java.lang.reflect.Method method=ExpenseDb.class.getDeclaredMethod("archivePromotions");method.setAccessible(true);return (Integer)method.invoke(db);}
        catch(Exception error){throw new RuntimeException(error);}
    }
}
