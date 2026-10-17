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
import java.util.List;
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
    private void ok(boolean value,String detail){if(!value)throw new AssertionError(detail);}
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
    private long entry(long when,long amount,String merchant){
        ok(db.addManualEntry(when,amount,"EGP",merchant,"تسوق",null,"expense",null,null));
        return db.latestInserted().id;
    }
    private long scalar(String sql){try(Cursor cursor=db.getReadableDatabase().rawQuery(sql,null)){ok(cursor.moveToFirst());return cursor.getLong(0);}}
    private String archivePayload(long originalId){
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT payload FROM transaction_archive WHERE original_id=?",
                new String[]{Long.toString(originalId)})){ok(cursor.moveToFirst());return cursor.getString(0);}
    }

    public void testSmsImportRepairFailureRollsBackBatchAndIsReported(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        long rows=db.transactionCount();
        SmsPreview bankCopy=SmsPreview.parse("BANK","Card purchase EGP 100 at QA SHOP",t+1000);ok(bankCopy!=null);
        // The repair that follows the import merges the bank copy into the notification row; make that step fail.
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_repair_fault BEFORE DELETE ON transactions BEGIN SELECT RAISE(ABORT,'qa repair fault'); END");
        try{SmsImportActivity.applyBatchWithRepair(db,Collections.singletonList(bankCopy));throw new AssertionError("Failed repair was reported as a saved import");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
    }

    public void testSavedSmsImportSurvivesFailureReadingReviewList(){
        long t=at(LocalDate.now(),10);
        SmsPreview card=SmsPreview.parse("BANK","Card purchase EGP 55 at QA SHOP",t);ok(card!=null);
        eq(1,SmsImportActivity.applyBatchWithRepair(db,Collections.singletonList(card)));
        // Reading the review list happens after the commit; if it fails the saved import must still be reported as saved.
        db.getWritableDatabase().execSQL("DROP TABLE capture_events");
        eq(0,SmsImportActivity.pendingAfterCommit(db,0,Long.MAX_VALUE).size());
        eq(1,scalar("SELECT COUNT(*) FROM transactions"));
    }

    public void testCsvBatchFaultAtRowLeavesNothingSavedAndDuplicatesAreCounted(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA ONE,-1.00\n2026-10-15,QA TWO,-2.00\n2026-10-15,QA FAULT,-3.00\n2026-10-15,QA FOUR,-4.00");
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_csv_fault BEFORE INSERT ON transactions WHEN NEW.merchant='QA FAULT' BEGIN SELECT RAISE(ABORT,'qa csv fault'); END");
        long before=db.transactionCount();
        try{db.importCsvBatch("qa-file",rows);throw new AssertionError("Faulty CSV batch was reported as imported");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(before,db.transactionCount());
        db.getWritableDatabase().execSQL("DROP TRIGGER qa_csv_fault");
        ExpenseDb.CsvBatchResult first=db.importCsvBatch("qa-file",rows);eq(4,first.saved);eq(0,first.duplicates);
        ExpenseDb.CsvBatchResult again=db.importCsvBatch("qa-file",rows);eq(0,again.saved);eq(4,again.duplicates);
    }

    public void testRefusedTransferFeeCannotSilentlyShrinkTheDebit(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA debit","تحويلات",from,"expense",null,null));long debit=db.latestInserted().id;
        // The fee row would be refused (its key is excluded); the split must not go ahead without it.
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+debit});
        try{db.reconcileOwnTransfer(debit,10000,50,from,to,Collections.<Long>emptyList());throw new AssertionError("Transfer was split without saving its fee");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        ok("expense".equals(db.transactionById(debit).kind));eq(10050,db.transactionById(debit).amountMinor);eq(0,db.transferFee(debit));
    }

    public void testAutomaticRepairWithRefusedFeeKeepsImportCommittedAndPairForReview(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA out",MoneyFlow.OPEN_TRANSFER_CATEGORY,from,"expense",null,null));long out=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",to,"income",null,null));long in=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id IN (?,?)",new Object[]{out,in});
        // The repair after an SMS import would pair these legs; the fee row it needs is refused, so the pair must wait for review.
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+out});
        SmsPreview sms=SmsPreview.parse("BANK","Card purchase EGP 55 at QA SHOP",t+120000);ok(sms!=null);
        long rows=db.transactionCount();
        eq(1,SmsImportActivity.applyBatchWithRepair(db,Collections.singletonList(sms)));
        eq(rows+1,db.transactionCount());
        ok("expense".equals(db.transactionById(out).kind));eq(10050,db.transactionById(out).amountMinor);eq(0,db.transferFee(out));ok(db.transactionById(in)!=null);
    }

    public void testUnexpectedRepairFailureFailsTheWholeSmsImport(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA out",MoneyFlow.OPEN_TRANSFER_CATEGORY,from,"expense",null,null));long out=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",to,"income",null,null));
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0");
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_fee_fault BEFORE INSERT ON transactions WHEN NEW.unique_key LIKE 'fee:%' BEGIN SELECT RAISE(ABORT,'qa fee fault'); END");
        SmsPreview sms=SmsPreview.parse("BANK","Card purchase EGP 55 at QA SHOP",t+120000);ok(sms!=null);
        long rows=db.transactionCount();
        try{SmsImportActivity.applyBatchWithRepair(db,Collections.singletonList(sms));throw new AssertionError("Import reported success although repair failed");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());ok("expense".equals(db.transactionById(out).kind));
    }

    public void testTransactionNotificationActionsHaveDistinctIdentities(){
        android.app.PendingIntent open5=CaptureNotifier.transactionIntent(context,5,false),edit5=CaptureNotifier.transactionIntent(context,5,true);
        android.app.PendingIntent open6=CaptureNotifier.transactionIntent(context,6,false),edit6=CaptureNotifier.transactionIntent(context,6,true);
        ok(!open5.equals(edit5),"open and edit of one transaction share an identity");
        ok(!edit5.equals(open6),"the edit action of one transaction is the open action of the next");
        ok(!open5.equals(open6)&&!edit5.equals(edit6),"two transactions share an identity");
        ok(open5.equals(CaptureNotifier.transactionIntent(context,5,false)),"same transaction and action must keep one identity");
    }

    public void testPaidActionReportsWhenNothingChanged(){
        ok(db.addRecurring("QA monthly",10000,"EGP",LocalDate.now()));
        long id=scalar("SELECT id FROM recurring ORDER BY id DESC LIMIT 1");
        ok(ReminderReceiver.markPaid(context,id,LocalDate.now()));
        ok(!ReminderReceiver.markPaid(context,id,LocalDate.now()),"A repeated tap was reported as a new payment");
        ok(!ReminderReceiver.markPaid(context,id+999,LocalDate.now()),"A missing commitment was reported as paid");
    }

    private static final String CANCEL_DEBIT="تم تحويل مبلغ 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222";
    private static final String CANCEL_NOTICE="تم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 1111";

    public void testCaptureOutcomeNewDuplicateCancellationAndMergeFollowed(){
        long t=at(LocalDate.now(),10);
        // New: one row, no event.
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        ExpenseDb.Transaction fresh=db.lastCaptured();ok(fresh!=null);eq(10000,fresh.amountMinor);ok(db.lastCaptureEvent()==null);
        // Duplicate: nothing saved, nothing to report.
        ok(!db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        ok(db.lastCaptured()==null);ok(db.lastCaptureEvent()==null);
        // Merge-followed: the bank copy merges into the existing row and the result follows it.
        long rows=db.transactionCount();
        ok(db.addSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000));
        ExpenseDb.Transaction merged=db.lastCaptured();ok(merged!=null);eq(10000,merged.amountMinor);eq(rows,db.transactionCount());ok(db.transactionById(merged.id)!=null);
        // Cancellation: the debit is archived, an event is reported and no row is returned.
        long cancelAt=t+3600000;
        ok(db.addSms("BANK",CANCEL_DEBIT,cancelAt));ok(db.lastCaptured()!=null);
        ok(db.addSms("BANK",CANCEL_NOTICE,cancelAt+3000));
        ok(db.lastCaptureEvent()!=null);ok(db.lastCaptureEvent().cancelled());ok(db.lastCaptured()==null);
    }

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
        db=new ExpenseDb(context);eq(23,db.getWritableDatabase().getVersion());eq(2,db.transactionCount());
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
    public void testArchiveEntryArchivesFeeChildAndRecordsRemovedReimbursement(){
        long t=at(LocalDate.now(),10),buy=manual(t,10000,"expense"),back=manual(t+1000,4000,"income"),fee=manual(t,200,"expense");
        db.getWritableDatabase().execSQL("UPDATE transactions SET fee_parent_id=? WHERE id=?",new Object[]{buy,fee});
        db.linkReimbursement(back,buy);
        eq(1,scalar("SELECT COUNT(*) FROM reimbursements"));
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id="+buy));
        db.archiveEntry(buy,"qa archive",true);
        ok(db.transactionById(buy)==null);
        ok(db.transactionById(fee)==null);
        ok(db.transactionById(back)!=null);
        eq(0,scalar("SELECT COUNT(*) FROM reimbursements"));
        eq(0,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id="+buy));
        eq(2,scalar("SELECT COUNT(*) FROM transaction_archive WHERE reason='qa archive'"));
        eq(1,scalar("SELECT COUNT(*) FROM transaction_archive WHERE original_id="+buy));
        eq(1,scalar("SELECT COUNT(*) FROM transaction_archive WHERE original_id="+fee));
        String payload=archivePayload(buy);
        ok(payload.contains("\"removed_reimbursements\""),payload);
        ok(payload.contains("\"expense_id\":"+buy)&&payload.contains("\"income_id\":"+back),payload);
        ok(payload.contains("\"amount_minor\":4000"),payload);
    }
    /** The archive and the reimbursement record survive a reload of the same database file. */
    public void testArchivedFeeChildAndReimbursementRecordPersist(){
        long t=at(LocalDate.now(),10),buy=manual(t,10000,"expense"),back=manual(t+1000,4000,"income"),fee=manual(t,200,"expense");
        db.getWritableDatabase().execSQL("UPDATE transactions SET fee_parent_id=? WHERE id=?",new Object[]{buy,fee});
        db.linkReimbursement(back,buy);
        db.archiveEntry(buy,"qa archive",false);
        db.close();db=new ExpenseDb(context);
        eq(2,scalar("SELECT COUNT(*) FROM transaction_archive WHERE reason='qa archive'"));
        ok(archivePayload(buy).contains("\"amount_minor\":4000"));
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
    /** Captures and normalization go through one worker, so a write is never lost to a locked database. */
    public void testCaptureQueueSerialisesCapturesAndOneNormalization(){
        LocalDate yesterday=LocalDate.now().minusDays(1);
        long plan=db.putCommitment(null,"قسط QA",500000,"EGP",yesterday,"monthly","installment",12,1,1,"قسط QA",null);
        ContentValues paid=new ContentValues();paid.put("last_paid_date",yesterday.minusDays(1).toString());
        db.getWritableDatabase().update("recurring",paid,"id=?",new String[]{Long.toString(plan)});
        final int captures=4;
        final java.util.List<Throwable> errors=java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(captures+1);
        final long day=at(LocalDate.now(),9);
        for(int index=0;index<captures;index++){
            final int value=index;
            CaptureQueue.submit(()->{
                try(ExpenseDb capture=new ExpenseDb(context)){
                    capture.addSms("BANK","Card purchase EGP "+(value+11)+" at QA SHOP "+value,day+value*1000L);
                }catch(Throwable error){errors.add(error);}
                finally{done.countDown();}
            });
        }
        CaptureQueue.submit(()->{
            try(ExpenseDb normalization=new ExpenseDb(context)){MainActivity.normalizeEarlyPaymentsOnWorker(normalization);}
            catch(Throwable error){errors.add(error);}
            finally{done.countDown();}
        });
        try{ok(done.await(30,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
        if(!errors.isEmpty())throw new AssertionError(errors.get(0));
        eq(captures,db.transactionCount());
        eq(captures,db.transactions().size());
        ok(!MainActivity.dueWord(db.recurringById(plan).nextDue).contains("متأخر"));
        eq(0,db.normalizeEarlyPayments());
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
    /** F-07 on the page level: the suggestion card for a merchant older than the newest 500 rows must exist. */
    public void testCommitmentSuggestionSurvivesBeyondTheNewestFiveHundredRows(){
        long base=at(LocalDate.now().minusDays(62),10);
        entry(base,12000,"QA X");
        long newer=entry(base+30L*86400000,12000,"QA X");
        for(int i=0;i<510;i++) entry(base+31L*86400000+i*60000L,3000,"QA FILLER "+i%7);
        eq(512,db.transactionCount());
        eq(500,db.transactions().size());
        ok(!db.transactions().stream().anyMatch(item->item.id==newer));
        List<ExpenseDb.Transaction> rows=db.recurringCandidateTransactions();
        Set<Long> ids=MainActivity.recurringCandidateIds(rows,db.recurring());
        List<ExpenseDb.Transaction> suggestions=MainActivity.recurringSuggestions(ids,rows);
        ok(ids.contains(newer));
        ok(suggestions.stream().anyMatch(item->item.id==newer&&"QA X".equals(item.merchant)));
    }
    public void testUnreadableEvidenceIsReportedAndNeverArchivesARow(){
        long t=at(LocalDate.now(),10),buy=manual(t,10000,"expense");
        db.getWritableDatabase().execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,source,received_at,encrypted_body) VALUES(?,?,?,?,?)",
                new Object[]{"qa-unreadable",buy,"SMS",t,"not-a-real-keystore-body"});
        List<ExpenseDb.SourceEvidence> evidence=db.sourceEvidence(buy);
        eq(1,evidence.size());
        ok(evidence.get(0).failed);
        ok(evidence.get(0).body==null);
        ok(MainActivity.originalMessageText(evidence.get(0)).equals("النص الأصلي غير متاح على هذا الجهاز"));
        ExpenseDb.RepairReport report=db.repairAll();
        eq(0,report.promotions);
        eq(1,report.evidenceFailures);
        ok(db.transactionById(buy)!=null);
    }
    /**
     * Deleting a purchase that is being suggested must move the ledger's change counter, so the cached
     * suggestion set can be reloaded instead of continuing to advertise a row that no longer exists.
     */
    public void testLocalEditMovesTheChangeCounterAndTheSuggestionScan(){
        long older=at(LocalDate.now().minusDays(30),10),newer=older+30L*86400000;
        entry(older,7000,"QA SUB");
        long id=entry(newer,7000,"QA SUB");
        Set<Long> before=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(before.contains(id),"the newest monthly match must be suggested");
        long counter=db.changes();
        ok(!MainActivity.ledgerChanged(db.changes(),counter),"reading the ledger must not look like an edit");
        db.deleteTransaction(id);
        ok(MainActivity.ledgerChanged(db.changes(),counter),"a local delete must move the change counter");
        Set<Long> after=MainActivity.recurringCandidateIds(db.recurringCandidateTransactions(),db.recurring());
        ok(!after.contains(id),"the deleted purchase must leave the suggestion scan");
    }
    /** The snapshot generation and redraw-identity rules the page uses to reject a stale or pointless redraw. */
    public void testSnapshotGenerationAndRedrawIdentityRules(){
        ok(MainActivity.ledgerSnapshotIsCurrent(4,4),"a snapshot from the current generation is applied");
        ok(!MainActivity.ledgerSnapshotIsCurrent(4,5),"a snapshot read before a local edit is discarded");
        ok(!MainActivity.ledgerChanged(7,7),"an unchanged ledger needs no reload");
        ok(MainActivity.ledgerChanged(8,7),"a changed ledger needs a reload");
        ok(MainActivity.isEditingSurface(12,false,false),"the commitment editor holds unsaved input");
        ok(MainActivity.isEditingSurface(2,false,true),"a focused field holds unsaved input");
        ok(MainActivity.isEditingSurface(2,true,false),"an intro form holds unsaved input");
        ok(!MainActivity.isEditingSurface(2,false,false),"the commitments page can be redrawn");
        ok(!MainActivity.isEditingSurface(0,false,false),"home can be redrawn");
        long t=at(LocalDate.now(),10);
        entry(t,1000,"QA A");
        long id=entry(t+1000,2000,"QA B");
        List<ExpenseDb.Transaction> rows=db.recurringCandidateTransactions();
        ok(MainActivity.sameRows(rows,db.recurringCandidateTransactions()),"unchanged rows need no redraw");
        db.updateTransaction(id,9999,"EGP","QA B","تسوق",null,"expense",null,null,null);
        ok(!MainActivity.sameRows(rows,db.recurringCandidateTransactions()),"an edited amount forces a redraw");
        rows=db.recurringCandidateTransactions();
        db.deleteTransaction(id);
        ok(!MainActivity.sameRows(rows,db.recurringCandidateTransactions()),"a deleted row forces a redraw");
    }
    /**
     * One snapshot redraw that lands while the screen is in the requested state. The ledger gains a row after
     * the page is drawn, so the pass must want a redraw, and whether that redraw happens is the thing under
     * test. Returns {draft before, draft after, page before, page after}; the page is the drawn ScrollView
     * instance, so a rebuilt page is a different object and a skipped redraw keeps the same one.
     */
    private Object[] heldSnapshotRedraw(boolean openEditor){
        long t=at(LocalDate.now(),10);
        entry(t,7000,"QA DRAFT");
        entry(t+30L*86400000,7000,"QA DRAFT");
        MainActivity activity=null;
        java.lang.reflect.Field dbField=null;ExpenseDb original=null;
        try{
            dbField=MainActivity.class.getDeclaredField("db");dbField.setAccessible(true);
            java.lang.reflect.Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);
            final java.lang.reflect.Field scroll=MainActivity.class.getDeclaredField("pageScroll");scroll.setAccessible(true);
            final java.lang.reflect.Field rows=MainActivity.class.getDeclaredField("recurringCandidateRows");rows.setAccessible(true);
            final java.lang.reflect.Field refreshing=MainActivity.class.getDeclaredField("refreshingLedger");refreshing.setAccessible(true);
            final java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);
            final java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
            final java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refreshLedger",boolean.class);refresh.setAccessible(true);
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(runner.getTargetContext(),MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            original=(ExpenseDb)dbField.get(activity);
            final MainActivity target=activity;
            dbField.set(target,db);
            runner.runOnMainSync(()->{
                try{
                    intro.setBoolean(target,false);
                    tab.setInt(target,2);
                    render.invoke(target);
                    refresh.invoke(target,true);   // settle the cache on this test's isolated ledger
                }catch(Exception error){throw new RuntimeException(error);}
            });
            ok(settled(target,rows,refreshing,2),"the initial page must settle on the seeded ledger");
            final Object[] pageBefore={null};
            final String[] draft={"",null};
            runner.runOnMainSync(()->{
                try{
                    if(openEditor)tab.setInt(target,12);            // the user opens the commitment editor
                    render.invoke(target);
                    android.widget.EditText field=firstEditText(target.getWindow().getDecorView());
                    if(openEditor&&field!=null)field.setText("مسودة");// and types before the pass lands
                    draft[0]=field==null?"":field.getText().toString();
                    pageBefore[0]=scroll.get(target);
                }catch(Exception error){throw new RuntimeException(error);}
            });
            runner.waitForIdleSync();
            entry(t-100L*86400000,7000,"QA DRAFT");   // the ledger moves, so the snapshot wants a redraw
            runner.runOnMainSync(()->{try{refresh.invoke(target,true);}catch(Exception error){throw new RuntimeException(error);}});
            ok(settled(target,rows,refreshing,3),"the moved ledger must settle into a new snapshot");
            final Object[] pageAfter={null};
            runner.runOnMainSync(()->{
                try{
                    pageAfter[0]=scroll.get(target);
                    android.widget.EditText field=firstEditText(target.getWindow().getDecorView());
                    draft[1]=field==null?null:field.getText().toString();
                }catch(Exception error){throw new RuntimeException(error);}
            });
            return new Object[]{draft[0],draft[1],pageBefore[0],pageAfter[0]};
        }catch(Exception error){throw new AssertionError(error);}
        finally{
            if(activity!=null){
                final MainActivity closing=activity;final java.lang.reflect.Field restore=dbField;final ExpenseDb previous=original;
                runner.runOnMainSync(()->{try{restore.set(closing,previous);closing.finish();}catch(Exception ignored){}});
            }
        }
    }
    /** Waits for the capture worker's snapshot to be applied on the UI thread, so the test never races it. */
    private boolean settled(MainActivity target,java.lang.reflect.Field rows,java.lang.reflect.Field refreshing,int expected){
        for(int attempt=0;attempt<100;attempt++){
            final boolean[] done={false};
            runner.runOnMainSync(()->{
                try{
                    done[0]=!((java.util.concurrent.atomic.AtomicBoolean)refreshing.get(target)).get()
                            &&((java.util.List<?>)rows.get(target)).size()==expected;
                }catch(Exception error){throw new RuntimeException(error);}
            });
            if(done[0])return true;
            try{Thread.sleep(100);}catch(InterruptedException interrupted){throw new AssertionError(interrupted);}
        }
        return false;
    }
    private static android.widget.EditText firstEditText(android.view.View view){
        if(view instanceof android.widget.EditText)return (android.widget.EditText)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int index=0;index<group.getChildCount();index++){
                android.widget.EditText found=firstEditText(group.getChildAt(index));
                if(found!=null)return found;
            }
        }
        return null;
    }
    /**
     * The delayed snapshot redraw must not rebuild a page the user is typing in. The refresh is requested while
     * the commitments page is open and the editor is opened (and typed into) before the worker's snapshot lands.
     */
    public void testDelayedRedrawKeepsTheCommitmentEditorDraft(){
        Object[] result=heldSnapshotRedraw(true);
        ok("مسودة".equals(result[0]),"the editor must accept the typed draft, saw: "+result[0]);
        ok("مسودة".equals(result[1]),"the typed draft must survive the delayed redraw, saw: "+result[1]);
        ok(result[2]!=null&&result[2]==result[3],"an open editor holds unsaved input, so the page must not be rebuilt");
    }
    /**
     * Control for the test above: the very same delayed refresh must rebuild the page when no editor is open,
     * which proves the redraw really is attempted and that the draft case is decided by the editor guard.
     */
    public void testDelayedRedrawRebuildsThePageWhenNoEditorIsOpen(){
        Object[] result=heldSnapshotRedraw(false);
        ok(result[2]!=null&&result[2]!=result[3],"the delayed refresh must rebuild a page with no unsaved input");
    }
    /**
     * A local delete must leave the cached suggestions. The row is deleted straight from the ledger, so the only
     * thing that can drop it from the cache is the reload the app schedules on its next render.
     */
    public void testDeletedRowLeavesTheCachedSuggestions(){
        long t=at(LocalDate.now(),10);
        entry(t,7000,"QA CACHE");
        long newest=entry(t+30L*86400000,7000,"QA CACHE");
        MainActivity activity=null;
        java.lang.reflect.Field dbField=null;ExpenseDb original=null;
        try{
            dbField=MainActivity.class.getDeclaredField("db");dbField.setAccessible(true);
            final java.lang.reflect.Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);
            final java.lang.reflect.Field rows=MainActivity.class.getDeclaredField("recurringCandidateRows");rows.setAccessible(true);
            final java.lang.reflect.Field refreshing=MainActivity.class.getDeclaredField("refreshingLedger");refreshing.setAccessible(true);
            final java.lang.reflect.Field candidates=MainActivity.class.getDeclaredField("recurringCandidates");candidates.setAccessible(true);
            final java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);
            final java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
            final java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refreshLedger",boolean.class);refresh.setAccessible(true);
            activity=(MainActivity)runner.startActivitySync(new android.content.Intent(runner.getTargetContext(),MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            original=(ExpenseDb)dbField.get(activity);
            final MainActivity target=activity;
            dbField.set(target,db);
            runner.runOnMainSync(()->{
                try{
                    intro.setBoolean(target,false);
                    tab.setInt(target,2);
                    render.invoke(target);
                    refresh.invoke(target,true);   // settle the cache on this test's isolated ledger
                }catch(Exception error){throw new RuntimeException(error);}
            });
            ok(settled(target,rows,refreshing,2),"the cache must settle on the seeded ledger");
            ok(cachedCandidates(target,candidates).contains(newest),"the seeded monthly match must be suggested");
            db.deleteTransaction(newest);   // the same local edit the delete button makes
            runner.runOnMainSync(()->{try{render.invoke(target);}catch(Exception error){throw new RuntimeException(error);}});
            ok(settled(target,rows,refreshing,1),"the cache must reload once the ledger changes under it");
            ok(!cachedCandidates(target,candidates).contains(newest),"the deleted purchase must leave the cache");
        }catch(Exception error){throw new AssertionError(error);}
        finally{
            if(activity!=null){
                final MainActivity closing=activity;final java.lang.reflect.Field restore=dbField;final ExpenseDb previous=original;
                runner.runOnMainSync(()->{try{restore.set(closing,previous);closing.finish();}catch(Exception ignored){}});
            }
        }
    }
    private static Set<Long> cachedCandidates(MainActivity target,java.lang.reflect.Field field){
        try{
            @SuppressWarnings("unchecked") Set<Long> value=(Set<Long>)field.get(target);
            return value;
        }catch(Exception error){throw new AssertionError(error);}
    }
    public void testIncomeCaptureRedrawsWithoutChangedExpenseSuggestions(){
        new LedgerRefreshRegressionTest(runner,context,db).check("income",false,false);
    }
    public void testBalanceOnlyBroadcastRedrawsWithoutChangedExpenseSuggestions(){
        new LedgerRefreshRegressionTest(runner,context,db).check("balance",false,false);
    }
    public void testCommitmentOnlyBroadcastRedrawsWithoutChangedExpenseSuggestions(){
        new LedgerRefreshRegressionTest(runner,context,db).check("commitment",false,false);
    }
    public void testFinancialBroadcastDuringSnapshotStillRedraws(){
        new LedgerRefreshRegressionTest(runner,context,db).check("income",false,true);
    }
    public void testIncomeBroadcastKeepsTheCommitmentEditorDraft(){
        new LedgerRefreshRegressionTest(runner,context,db).check("income",true,false);
    }
    private int archivePromotions(){
        try{java.lang.reflect.Method method=ExpenseDb.class.getDeclaredMethod("archivePromotions",ExpenseDb.RepairReport.class);
            method.setAccessible(true);return (Integer)method.invoke(db,new ExpenseDb.RepairReport());}
        catch(NoSuchMethodException legacySignature){
            try{java.lang.reflect.Method method=ExpenseDb.class.getDeclaredMethod("archivePromotions");method.setAccessible(true);return (Integer)method.invoke(db);}
            catch(Exception error){throw new RuntimeException(error);}
        }
        catch(Exception error){throw new RuntimeException(error);}
    }
}
