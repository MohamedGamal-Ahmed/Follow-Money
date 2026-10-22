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
    private void eq(long expected,long actual,String detail){if(expected!=actual)throw new AssertionError(detail+": "+expected+" != "+actual);}
    private void eq(String expected,String actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
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
    private String text(String sql){try(Cursor cursor=db.getReadableDatabase().rawQuery(sql,null)){ok(cursor.moveToFirst());return cursor.getString(0);}}
    private String archivePayload(long originalId){
        try(Cursor cursor=db.getReadableDatabase().rawQuery("SELECT payload FROM transaction_archive WHERE original_id=?",
                new String[]{Long.toString(originalId)})){ok(cursor.moveToFirst());return cursor.getString(0);}
    }

    public void testRevisionSeesCommittedExternalWritesAndSurvivesReopen(){
        long before=db.changes();
        try(ExpenseDb external=new ExpenseDb(context)){
            ok(external.addManual(at(LocalDate.now(),10),1000,"EGP","QA external","تسوق"));
        }
        ok(db.changes()!=before,"Committed external write was invisible without a broadcast");
        long committed=db.changes();db.close();db=new ExpenseDb(context);
        eq(committed,db.changes());eq(1,db.transactionCount());
        try(ExpenseDb external=new ExpenseDb(context)){
            long account=external.addPaymentSource("QA revision","حساب",null);
            external.putManualBalance(account,"EGP",30000,System.currentTimeMillis());
            ok(external.putCommitment(null,"QA revision plan",2000,"EGP",LocalDate.now().plusMonths(1),"monthly","bill",null,0,1,null,null)>0);
        }
        ok(db.changes()>committed,"Non-transaction financial state did not invalidate the ledger");
    }

    public void testRevisionRollsBackWithItsCallerAndDoesNotResetOnClear(){
        long before=db.changes();SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{manual(at(LocalDate.now(),10),1000,"expense");}finally{sql.endTransaction();}
        eq(0,db.transactionCount());eq(before,db.changes());
        manual(at(LocalDate.now(),11),2000,"expense");long committed=db.changes();ok(committed>before);
        db.clearAllData();ok(db.changes()>committed,"Deleting data reset/reused the revision");
        long cleared=db.changes();db.close();db=new ExpenseDb(context);eq(cleared,db.changes());
    }

    public void testMissedBroadcastStillRedrawsAfterExternalCommit(){
        new LedgerRefreshRegressionTest(runner,context,db).checkMissedBroadcast(false);
    }

    public void testCommitAfterSnapshotReadCannotApplyTheOldSnapshot(){
        new LedgerRefreshRegressionTest(runner,context,db).checkMissedBroadcast(true);
    }

    private String revisionData(SQLiteDatabase sql)throws Exception{
        org.json.JSONArray snapshot=new org.json.JSONArray();
        try(Cursor tables=sql.rawQuery("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','ledger_revision') ORDER BY name",null)){
            while(tables.moveToNext()){
                String table=tables.getString(0);org.json.JSONArray rows=new org.json.JSONArray();
                try(Cursor data=sql.rawQuery("SELECT * FROM \""+table.replace("\"","\"\"")+"\" ORDER BY rowid",null)){
                    while(data.moveToNext()){
                        org.json.JSONArray row=new org.json.JSONArray();
                        for(int i=0;i<data.getColumnCount();i++)row.put(data.isNull(i)?org.json.JSONObject.NULL:data.getString(i));
                        rows.put(row);
                    }
                }
                snapshot.put(new org.json.JSONArray().put(table).put(tables.getString(1)).put(rows));
            }
        }
        return snapshot.toString();
    }

    public void testV23RevisionUpgradePreservesEveryUserTableAndEvidence()throws Exception{
        long t=at(LocalDate.now(),10);
        ok(db.addSms("BM","Card xxx4636 purchase EGP15 at QA SHOP. Available balance EGP7240.01",t));long purchase=db.lastCaptured().id;
        long income=manual(t+1000,500,"income");eq(500,db.linkReimbursement(income,purchase));
        ok(db.addRecurring("QA upgrade plan",2000,"EGP",LocalDate.now().plusMonths(1)));
        SQLiteDatabase sql=db.getWritableDatabase();String before=revisionData(sql);
        java.util.List<String> triggers=new java.util.ArrayList<>();
        try(Cursor c=sql.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'ledger_revision_%'",null)){
            while(c.moveToNext())triggers.add(c.getString(0));
        }
        for(String trigger:triggers)sql.execSQL("DROP TRIGGER \""+trigger.replace("\"","\"\"")+"\"");
        sql.execSQL("DROP TABLE ledger_revision");sql.setVersion(23);db.close();db=new ExpenseDb(context);
        eq(25,db.getWritableDatabase().getVersion());ok(before.equals(revisionData(db.getReadableDatabase())),"v23 upgrade changed financial tables/schema/evidence");
        eq(0,db.changes());eq(500,db.reimbursedOf(purchase));ok(!db.sourceEvidence(purchase).isEmpty());
        try(ExpenseDb external=new ExpenseDb(context)){ok(external.addManual(t+2000,700,"EGP","QA after upgrade","تسوق"));}
        long committed=db.changes();ok(committed>0);db.close();db=new ExpenseDb(context);eq(committed,db.changes());
        try(Cursor c=db.getReadableDatabase().rawQuery("PRAGMA integrity_check",null)){ok(c.moveToFirst()&&"ok".equals(c.getString(0)));}
    }

    public void testRevisionCoversUserTablesAndIgnoresReadsAndUnmatchedWrites(){
        SQLiteDatabase sql=db.getWritableDatabase();
        try(Cursor tables=sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata','ledger_revision')",null)){
            while(tables.moveToNext())for(String operation:new String[]{"INSERT","UPDATE","DELETE"}){
                try(Cursor trigger=sql.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND tbl_name=? AND name=?",
                        new String[]{tables.getString(0),"ledger_revision_"+tables.getString(0)+"_"+operation})){
                    ok(trigger.moveToFirst()&&trigger.getLong(0)==1,"Untracked user-table write: "+tables.getString(0)+" "+operation);
                }
            }
        }
        long before=db.changes();db.transactions();db.recurring();eq(before,db.changes());
        sql.execSQL("UPDATE transactions SET merchant='QA unmatched' WHERE id=-1");eq(before,db.changes());
        sql.execSQL("INSERT OR IGNORE INTO excluded_messages(unique_key) VALUES('qa-revision')");long inserted=db.changes();ok(inserted>before);
        sql.execSQL("INSERT OR IGNORE INTO excluded_messages(unique_key) VALUES('qa-revision')");eq(inserted,db.changes());
    }

    public void testRevisionWriteFailureCannotCommitAnUnstampedFinancialRow(){
        long before=db.changes();
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_revision_fault BEFORE UPDATE ON ledger_revision BEGIN SELECT RAISE(ABORT,'qa revision fault'); END");
        try{ok(!db.addManual(at(LocalDate.now(),10),1000,"EGP","QA refused revision","تسوق"));}
        catch(android.database.sqlite.SQLiteConstraintException expected){/* The original insert API propagates this injected failure. */}
        eq(0,db.transactionCount());eq(before,db.changes());
        db.getWritableDatabase().execSQL("DROP TRIGGER qa_revision_fault");
        ok(db.addManual(at(LocalDate.now(),10),1000,"EGP","QA revision retry","تسوق"));eq(1,db.transactionCount());ok(db.changes()>before);
    }

    public void testSmsImportRepairFailureRollsBackBatchAndIsReported(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        long rows=db.transactionCount();
        SmsPreview bankCopy=SmsPreview.parse("BANK","Card purchase EGP 100 at QA SHOP",t+1000);ok(bankCopy!=null);
        // The repair that follows the import merges the bank copy into the notification row; make that step fail.
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_repair_fault BEFORE DELETE ON transactions BEGIN SELECT RAISE(ABORT,'qa repair fault'); END");
        try{ImportRun.added(db,Collections.singletonList(bankCopy));throw new AssertionError("Failed repair was reported as a saved import");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
    }

    public void testSavedSmsImportSurvivesFailureReadingReviewList(){
        long t=at(LocalDate.now(),10);
        SmsPreview card=SmsPreview.parse("BANK","Card purchase EGP 55 at QA SHOP",t);ok(card!=null);
        eq(1,ImportRun.added(db,Collections.singletonList(card)));
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
        eq(1,ImportRun.added(db,Collections.singletonList(sms)));
        eq(rows+1,db.transactionCount());
        ok("expense".equals(db.transactionById(out).kind));eq(10050,db.transactionById(out).amountMinor);eq(0,db.transferFee(out));ok(db.transactionById(in)!=null);
        ok(db.transactionById(out).needsReview,"TR2: skipped debit has no visible review marker");
    }

    public void testRefusedFeeRepairOnlyInvalidatesWhenReviewStateChanges(){
        long[] p=tr2Pair();SQLiteDatabase sql=db.getWritableDatabase();
        String receiptKey;
        try(Cursor receipt=sql.rawQuery("SELECT unique_key FROM transactions WHERE id=?",new String[]{Long.toString(p[3])})){
            ok(receipt.moveToFirst());receiptKey=receipt.getString(0);
        }
        sql.execSQL("UPDATE transactions SET user_edited=0 WHERE id IN (?,?)",new Object[]{p[2],p[3]});
        sql.execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+p[2]});
        long before=db.changes();ExpenseDb.RepairReport first=db.repairBetween(p[4]-1000,p[4]+120000);
        eq(0,first.ownTransfers);ok(db.transactionById(p[2]).needsReview,"First refusal marker missing");ok(db.changes()>before,"First marker did not invalidate");
        eq(10050,db.transactionById(p[2]).amountMinor);eq(10000,db.transactionById(p[3]).amountMinor);eq(0,db.transferFee(p[2]));
        long marked=db.changes();db.repairBetween(p[4]-1000,p[4]+120000);
        eq(marked,db.changes()); // Rechecking an already marked refusal must not manufacture an invalidation.
        eq(2,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM excluded_messages"));
        sql.execSQL("UPDATE transactions SET needs_review=0 WHERE id=?",new Object[]{p[2]});long cleared=db.changes();
        sql.beginTransaction();
        try{
            db.repairBetweenInCallerTransaction(p[4]-1000,p[4]+120000);
            ok(db.transactionById(p[2]).needsReview,"Caller transaction did not set marker");ok(db.changes()>cleared,"Caller marker did not invalidate");
        }finally{sql.endTransaction();} // A new marker and its revision remain owned by this caller.
        eq(cleared,db.changes());ok(!db.transactionById(p[2]).needsReview,"Caller rollback retained marker");ok(sql.isOpen()&&!sql.inTransaction(),"Caller helper closed or transaction leaked");
        db.repairBetween(p[4]-1000,p[4]+120000);ok(db.transactionById(p[2]).needsReview,"Cleared marker was not restored");ok(db.changes()>cleared,"Restored marker did not invalidate");
        long remarked=db.changes();db.repairBetween(p[4]-1000,p[4]+120000);eq(remarked,db.changes());
        sql.execSQL("DELETE FROM excluded_messages WHERE unique_key=?",new Object[]{"fee:"+p[2]});long unblocked=db.changes();
        eq(1,db.repairBetween(p[4]-1000,p[4]+120000).ownTransfers);
        ok("transfer".equals(db.transactionById(p[2]).kind),"Unblocked debit was not reconciled");eq(10000,db.transactionById(p[2]).amountMinor);
        eq(50,db.transferFee(p[2]));
        // The new fee may reuse the deleted receipt's numeric row id; test the original source identity.
        try(Cursor receipt=sql.rawQuery("SELECT 1 FROM transactions WHERE unique_key=?",new String[]{receiptKey})){
            ok(!receipt.moveToFirst(),"Unblocked receipt identity was not removed");
        }
        ok(db.changes()>unblocked,"Reconciliation did not invalidate");
    }

    public void testUnexpectedRepairFailureFailsTheWholeSmsImport(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA out",MoneyFlow.OPEN_TRANSFER_CATEGORY,from,"expense",null,null));long out=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",to,"income",null,null));
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0");
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_fee_fault BEFORE INSERT ON transactions WHEN NEW.unique_key LIKE 'fee:%' BEGIN SELECT RAISE(ABORT,'qa fee fault'); END");
        SmsPreview sms=SmsPreview.parse("BANK","Card purchase EGP 55 at QA SHOP",t+120000);ok(sms!=null);
        long rows=db.transactionCount();
        try{ImportRun.added(db,Collections.singletonList(sms));throw new AssertionError("Import reported success although repair failed");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());ok("expense".equals(db.transactionById(out).kind));
    }

    private long[] tr2Pair(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("QA A","حساب","1111"),to=db.addPaymentSource("QA B","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA out","تحويلات",from,"expense",null,"QA original note"));long out=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",to,"income",null,null));long in=db.latestInserted().id;
        return new long[]{from,to,out,in,t};
    }

    public void testExplicitExcludedFeeOmissionRecordsConsentAndDoesNotRepeat(){
        long[] p=tr2Pair();db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+p[2]});
        try{db.reconcileOwnTransfer(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("TR1: fee silently omitted");}
        catch(ExpenseDb.TransferFeeUnavailable expected){ok(expected.reason==ExpenseDb.FeeBlockReason.EXCLUDED);ok(expected.mayOmit());}
        eq(10050,db.transactionById(p[2]).amountMinor);ok(db.transactionById(p[3])!=null);
        db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));
        ExpenseDb.Transaction linked=db.transactionById(p[2]);eq(10000,linked.amountMinor);ok("transfer".equals(linked.kind));ok(!linked.needsReview);
        ok(linked.note.contains("QA original note")&&linked.note.contains("0.50 EGP")&&linked.note.contains("بموافقتك"));
        eq(0,db.transferFee(p[2]));ok(db.transactionById(p[3])==null);eq(1,scalar("SELECT COUNT(*) FROM excluded_messages"));
        try{db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.emptyList());throw new AssertionError("Repeated omission accepted");}
        catch(IllegalArgumentException expected){}eq(10000,db.transactionById(p[2]).amountMinor);
    }

    public void testIgnoredTransferFeeMarksAutoSkipAndCanBeExplicitlyOmitted(){
        long[] p=tr2Pair();db.putMerchantRule("رسوم تحويل QA A","رسوم تحويل",true);
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0");db.repairAround(p[4]);
        ok(db.transactionById(p[2]).needsReview);eq(10050,db.transactionById(p[2]).amountMinor);ok(db.transactionById(p[3])!=null);
        try{db.reconcileOwnTransfer(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Ignored fee silently omitted");}
        catch(ExpenseDb.TransferFeeUnavailable expected){ok(expected.reason==ExpenseDb.FeeBlockReason.IGNORED);ok(expected.mayOmit());}
        db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));
        eq(10000,db.transactionById(p[2]).amountMinor);eq(0,db.transferFee(p[2]));eq(1,db.merchantRules().size());
    }

    public void testStaleFeeOmissionCannotDiscardAnAvailableFee(){
        long[] p=tr2Pair();
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+p[2]});
        try{db.reconcileOwnTransfer(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Expected unavailable fee");}
        catch(ExpenseDb.TransferFeeUnavailable expected){}
        db.getWritableDatabase().execSQL("DELETE FROM excluded_messages WHERE unique_key=?",new Object[]{"fee:"+p[2]});
        try{db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Available fee omitted");}
        catch(IllegalArgumentException expected){}
        eq(10050,db.transactionById(p[2]).amountMinor);ok(db.transactionById(p[3])!=null);
        db.reconcileOwnTransfer(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));eq(50,db.transferFee(p[2]));
    }

    public void testRecordedFeeCollisionCannotBeDeclaredUncounted(){
        long[] p=tr2Pair();long other=entry(p[4],50,"QA existing fee");
        db.getWritableDatabase().execSQL("UPDATE transactions SET unique_key=? WHERE id=?",new Object[]{"fee:"+p[2],other});
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+p[2]});
        try{db.reconcileOwnTransfer(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Recorded fee accepted");}
        catch(ExpenseDb.TransferFeeUnavailable expected){ok(expected.reason==ExpenseDb.FeeBlockReason.RECORDED);ok(!expected.mayOmit());}
        try{db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Recorded fee declared uncounted");}
        catch(IllegalArgumentException expected){}
        eq(10050,db.transactionById(p[2]).amountMinor);ok(db.transactionById(other)!=null&&db.transactionById(p[3])!=null);
    }

    public void testRefusedOmissionUpdateRollsBackReceiptMerge(){
        long[] p=tr2Pair();db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES (?)",new Object[]{"fee:"+p[2]});
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_omit_refused BEFORE UPDATE OF amount_minor ON transactions WHEN OLD.id="+p[2]+" BEGIN SELECT RAISE(IGNORE); END");
        try{db.reconcileOwnTransferWithoutFee(p[2],10000,50,p[0],p[1],Collections.singletonList(p[3]));throw new AssertionError("Refused update succeeded");}
        catch(IllegalArgumentException expected){}
        eq(10050,db.transactionById(p[2]).amountMinor);ok(db.transactionById(p[3])!=null);eq(0,db.transferFee(p[2]));
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

    public void testTransactionEditAndMerchantLearningRollBackTogether(){
        long t=at(LocalDate.now(),10),id=manual(t,10000,"expense");
        TransactionMutation mutation=new TransactionMutation(db);
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_rule_fault BEFORE INSERT ON merchant_rules BEGIN SELECT RAISE(ABORT,'qa rule fault'); END");
        try{mutation.update(id,5500,"EGP","QA MUTATION","صحة",null,"expense",null,"reviewed",t,true);throw new AssertionError("Edit committed although merchant learning failed");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(10000,db.transactionById(id).amountMinor);ok("QA expense".equals(db.transactionById(id).merchant));eq(0,db.merchantRules().size());
        db.getWritableDatabase().execSQL("DROP TRIGGER qa_rule_fault");
        mutation.update(id,5500,"EGP","QA MUTATION","صحة",null,"expense",null,"reviewed",t,true);
        eq(5500,db.transactionById(id).amountMinor);ok("صحة".equals(db.transactionById(id).category));eq(1,db.merchantRules().size());
        ok("صحة".equals(db.merchantRules().get(0).category));
    }

    public void testMissingEditedTransactionNeverLearnsMerchant(){
        MutationResult result=new TransactionMutation(db).update(999999,5500,"EGP","QA MISSING","صحة",null,"expense",null,"reviewed",at(LocalDate.now(),10),true);
        ok(!result.changed());ok(result.outcome==MutationResult.Outcome.NOT_FOUND);eq(999999,result.transactionId);
        eq(0,db.transactionCount());eq(0,db.merchantRules().size());
    }

    public void testRefusedEditNeverLearnsMerchantOrClaimsAnUpdatedRow(){
        long t=at(LocalDate.now(),10),id=manual(t,10000,"expense");
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_edit_refusal BEFORE UPDATE ON transactions BEGIN SELECT RAISE(IGNORE); END");
        MutationResult result=new TransactionMutation(db).update(id,5500,"EGP","QA REFUSED","صحة",null,"expense",null,"reviewed",t,true);
        ok(!result.changed());ok(result.outcome==MutationResult.Outcome.NOT_APPLIED);eq(10000,db.transactionById(id).amountMinor);eq(0,db.merchantRules().size());
    }

    public void testEditOutcomeCannotEscapeAnUncommittedCallerTransaction(){
        long t=at(LocalDate.now(),10),id=manual(t,10000,"expense");
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            try{new TransactionMutation(db).update(id,5500,"EGP","QA OWNER","صحة",null,"expense",null,"reviewed",t,true);throw new AssertionError("Outcome escaped before the caller's commit");}
            catch(IllegalStateException expected){}
        }finally{sql.endTransaction();}
        eq(10000,db.transactionById(id).amountMinor);eq(0,db.merchantRules().size());
    }

    private void assertReviewedSourceRejected(){
        try{new SourceMutation(db).createReviewed("HSBC","حساب","9988");throw new AssertionError("Incomplete source reported as saved");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(0,scalar("SELECT COUNT(*) FROM payment_sources"));eq(0,scalar("SELECT COUNT(*) FROM source_aliases"));
    }

    public void testReviewedSourceAliasFailureRollsBackAndCanBeRetried(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_alias_abort BEFORE INSERT ON source_aliases BEGIN SELECT RAISE(ABORT,'qa alias fault'); END");
        assertReviewedSourceRejected();
        db.getWritableDatabase().execSQL("DROP TRIGGER qa_alias_abort");
        long id=new SourceMutation(db).createReviewed("HSBC","حساب","9988");
        ok(id>0);ok(db.paymentSource(id).reviewed);eq(1,db.sourceAliasCount(id));eq(1,db.paymentSources().size());
    }

    public void testReviewedSourceRefusedAliasRollsBack(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_alias_ignore BEFORE INSERT ON source_aliases BEGIN SELECT RAISE(IGNORE); END");
        assertReviewedSourceRejected();
    }

    public void testReviewedSourceProfileFailureRollsBackRowAndAlias(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_profile_abort BEFORE UPDATE ON payment_sources BEGIN SELECT RAISE(ABORT,'qa profile fault'); END");
        assertReviewedSourceRejected();
    }

    public void testReviewedSourceRefusedProfileRollsBackRowAndAlias(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_profile_ignore BEFORE UPDATE ON payment_sources BEGIN SELECT RAISE(IGNORE); END");
        assertReviewedSourceRejected();
    }

    public void testReviewedSourceRefusedInsertNeverReportsAnId(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_source_ignore BEFORE INSERT ON payment_sources BEGIN SELECT RAISE(IGNORE); END");
        assertReviewedSourceRejected();
    }

    public void testReviewedSourceDuplicateIdentityKeepsItsExistingOwner(){
        long existing=db.addPaymentSource("HSBC","حساب","9988");
        long id=new SourceMutation(db).createReviewed("HSBC","بطاقة","9988");
        ok(id>0&&id!=existing);ok(db.paymentSource(id).reviewed);eq(2,db.paymentSources().size());
        eq(1,db.sourceAliasCount(existing));eq(0,db.sourceAliasCount(id));
    }

    public void testReviewedSourceWithoutIdentityIsAllowed(){
        long id=new SourceMutation(db).createReviewed("QA cash","كاش",null);
        ExpenseDb.PaymentSource source=db.paymentSource(id);ok(source!=null&&source.reviewed);ok(source.last4==null);
        eq(0,db.sourceAliasCount(id));
    }

    public void testReviewedSourceCannotReportSuccessInsideAnotherTransaction(){
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            try{new SourceMutation(db).createReviewed("HSBC","حساب","9988");throw new AssertionError("Source ID escaped before caller commit");}
            catch(IllegalStateException expected){}
        }finally{sql.endTransaction();}
        eq(0,db.paymentSources().size());eq(0,scalar("SELECT COUNT(*) FROM source_aliases"));
    }

    public void testExplicitSourceBankCorrectionPreservesIdentityAndFinancialRows(){
        long card=db.addPaymentSource("10001","بطاقة","4101"),account=db.addPaymentSource("20002","حساب","4102");
        long when=at(LocalDate.now(),10);ok(db.addManualEntry(when,12345,"EGP","QA shop","بقالة",card,"expense",null,"original"));long tx=db.latestInserted().id;
        ok(new SourceMutation(db).correctBank(card,"HSBC","كارت المشتريات"));ok(new SourceMutation(db).correctBank(account,"HSBC","حساب المرتب"));
        eq("HSBC",db.paymentSource(card).name);eq("كارت المشتريات",db.paymentSource(card).displayName);eq("4101",db.paymentSource(card).last4);
        ok(db.sourceAliasOwner("10001:4101")==card);ok(db.sourceAliasOwner("HSBC:4101")==card);ok(db.paymentSource(card).parentSourceId==null);
        eq(12345,db.transactionById(tx).amountMinor);eq(when,db.transactionById(tx).when);eq(card,db.transactionById(tx).paymentSourceId);eq("original",db.transactionById(tx).note);
        db.linkCardToAccount(card,account);eq(account,db.accountId(card));eq(1,db.transactionCount());
    }

    public void testSourceBankCorrectionAliasFailureRollsBackEverything(){
        long id=db.addPaymentSource("10001","بطاقة","4101");
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_bank_alias_abort BEFORE INSERT ON source_aliases BEGIN SELECT RAISE(ABORT,'qa bank alias fault'); END");
        try{new SourceMutation(db).correctBank(id,"HSBC","QA label");throw new AssertionError("Failed correction reported saved");}catch(android.database.sqlite.SQLiteException expected){}
        eq("10001",db.paymentSource(id).name);ok(db.paymentSource(id).displayName==null);eq(1,db.sourceAliasCount(id));ok(db.sourceAliasOwner("HSBC:4101")==null);
    }

    public void testSourceBankCorrectionDoesNotStealAliasOrChangeConfirmedLinks(){
        long owner=db.addPaymentSource("HSBC","حساب","4101"),card=db.addPaymentSource("10001","بطاقة","4101");
        try{new SourceMutation(db).correctBank(card,"HSBC","QA label");throw new AssertionError("Alias was stolen");}catch(IllegalArgumentException expected){}
        eq("10001",db.paymentSource(card).name);ok(db.sourceAliasOwner("HSBC:4101")==owner);
        long goodCard=db.addPaymentSource("HSBC","بطاقة","5566");db.linkCardToAccount(goodCard,owner);
        try{new SourceMutation(db).correctBank(goodCard,"بنك مصر","QA label");throw new AssertionError("Confirmed bank changed");}catch(IllegalArgumentException expected){}
        eq("HSBC",db.paymentSource(goodCard).name);eq(owner,db.accountId(goodCard));
    }

    public void testSourceBankCorrectionRejectsDigitsAndMissingAndNestedWrites(){
        long id=db.addPaymentSource("10001","بطاقة","4101");
        try{new SourceMutation(db).correctBank(id,"20002","QA");throw new AssertionError("Digits accepted as bank");}catch(IllegalArgumentException expected){}
        ok(!new SourceMutation(db).correctBank(999999,"HSBC","QA"));
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
            try{new SourceMutation(db).correctBank(id,"HSBC","QA");throw new AssertionError("Correction escaped outer transaction");}catch(IllegalStateException expected){}
        }finally{sql.endTransaction();}
        eq("10001",db.paymentSource(id).name);eq(1,db.paymentSources().size());
    }

    public void testNewSourceBankAndFriendlyNameAreSeparate(){
        long id=new SourceMutation(db).createReviewed("HSBC","حساب","9988","حساب المرتب");
        eq("HSBC",db.paymentSource(id).name);eq("حساب المرتب",db.paymentSource(id).displayName);ok(db.sourceAliasOwner("HSBC:9988")==id);
    }

    public void testSourceRecognitionQueryUsesExactIdentityAndIncludesIncomingTransfers(){
        long source=db.addPaymentSource("HSBC","حساب","1111"),other=db.addPaymentSource("HSBC","حساب","2222");long when=at(LocalDate.now(),10);
        for(int i=0;i<4;i++)ok(db.addManualEntry(when+i,100+i,"EGP","QA source "+i,"بقالة",source,"expense",null,null));
        ok(db.addManualEntry(when+10,500,"EGP","QA incoming","تحويلات",other,"transfer",source,null));
        ok(db.addManualEntry(when+20,600,"EGP","QA unrelated","بقالة",other,"expense",null,null));
        List<ExpenseDb.Transaction> rows=db.recentSourceTransactions(source);eq(3,rows.size());eq("QA incoming",rows.get(0).merchant);eq("QA source 3",rows.get(1).merchant);
        eq(6,db.transactionCount());
    }

    public void testCaptureResultReportsCommittedDuplicateEvidenceChange(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 100 at QA CONTRACT";
        ok(db.captureSms("BANK",body,t).dbChanged);long revision=db.changes();
        CaptureResult duplicate=db.captureSms("BANK",body,t);
        ok(db.changes()>revision);ok(duplicate.dbChanged,"Committed duplicate evidence write was reported as unchanged");
        ok(duplicate.transaction==null);eq(1,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM capture_evidence"));
    }

    public void testCaptureResultReportsCommittedBalanceOnlyChange(){
        long t=at(LocalDate.now(),10),revision=db.changes();
        CaptureResult balance=db.captureSms("BANK","Account ending 1234 available balance EGP 1000",t);
        ok(db.changes()>revision);ok(balance.dbChanged,"Committed balance-only write was reported as unchanged");
        ok(balance.transaction==null);eq(0,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM balance_snapshots"));
    }

    public void testCaptureContractIdsReasonsEvidenceAndSnapshots(){
        long t=at(LocalDate.now(),10),before=db.changes();String body="Card purchase EGP 100 at QA CONTRACT";
        CaptureResult fresh=db.captureSms("BANK",body,t);long id=fresh.transaction.id;
        ok(fresh.completion==CaptureResult.Completion.COMMITTED&&fresh.accepted&&fresh.dbChanged);
        ok(fresh.reason==CaptureResult.Reason.INSERTED);ok(fresh.evidence==CaptureResult.Evidence.SAVED_WITH_BODY);
        eq(id,fresh.transactionId);eq(id,fresh.capturedTransactionId);eq(before,fresh.revisionBefore);eq(db.changes(),fresh.revisionAfter);ok(!fresh.needsReview);
        CaptureResult duplicate=db.captureSms("BANK",body,t);
        ok(!duplicate.accepted&&duplicate.dbChanged);ok(duplicate.reason==CaptureResult.Reason.EXISTING_MATCH);
        eq(id,duplicate.transactionId);ok(duplicate.capturedTransactionId==null&&duplicate.transaction==null);
        ok(duplicate.evidence==CaptureResult.Evidence.SAVED_WITH_BODY);eq(fresh.revisionAfter,duplicate.revisionBefore);
        CaptureResult missing=db.captureSms("BANK",null,t);ok(missing.reason==CaptureResult.Reason.MISSING_BODY);
        ok(!missing.accepted&&!missing.dbChanged);ok(missing.evidence==CaptureResult.Evidence.NOT_ATTEMPTED);
        ok(db.captureSms("BANK","Your OTP is 123456",t).reason==CaptureResult.Reason.VERIFICATION);
        eq(id,fresh.transaction.id);eq(10000,fresh.transaction.amountMinor);eq(before,fresh.revisionBefore);
    }

    public void testCaptureContractPendingCallerNeverClaimsCommittedChange(){
        long t=at(LocalDate.now(),10),before=db.changes();CaptureResult pending;
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            pending=db.captureSms("BANK","Card purchase EGP 100 at QA CONTRACT",t);
            ok(pending.accepted&&!pending.dbChanged);ok(pending.completion==CaptureResult.Completion.PENDING_CALLER);
            ok(pending.transaction!=null&&pending.revisionAfter>before);eq(pending.transaction.id,pending.transactionId);
        }finally{sql.endTransaction();}
        eq(0,db.transactionCount());eq(before,db.changes());
        ok(pending.completion==CaptureResult.Completion.PENDING_CALLER&&!pending.dbChanged);
    }

    public void testCaptureContractEvidenceRefusalDoesNotInventSuccessfulEvidence(){
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_capture_evidence_ignore BEFORE INSERT ON capture_evidence BEGIN SELECT RAISE(IGNORE); END");
        CaptureResult result=db.captureSms("BANK","Card purchase EGP 100 at QA CONTRACT",at(LocalDate.now(),10));
        ok(result.accepted&&result.dbChanged);ok(result.reason==CaptureResult.Reason.INSERTED);
        ok(result.evidence==CaptureResult.Evidence.NOT_APPLIED);eq(1,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM capture_evidence"));
        ok(!result.needsReview,"Result invented a stored review marker");
    }

    public void testCaptureContractReviewMarkerAndRefusalHaveMatchedIds(){
        long t=at(LocalDate.now(),10),id=manual(t,10000,"expense");
        String future="Your payment EGP 100 will be processed tomorrow";
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0,message_hash=? WHERE id=?",new Object[]{CaptureIdentity.hash("BANK\n"+future),id});
        CaptureResult review=db.captureSms("BANK",future,t);
        ok(!review.accepted&&review.dbChanged&&review.needsReview);ok(review.reason==CaptureResult.Reason.FUTURE_ONLY);
        eq(id,review.transactionId);ok(review.transaction==null&&review.capturedTransactionId==null);eq(10000,db.transactionById(id).amountMinor);
        db.putMerchantRule("QA REFUSED","تسوق",true);
        CaptureResult refused=db.captureSms("BANK","Card purchase EGP 20 at QA REFUSED",t+10000);
        ok(!refused.accepted&&!refused.dbChanged);ok(refused.reason==CaptureResult.Reason.INSERT_NOT_APPLIED);
        ok(refused.transactionId==null&&refused.evidence==CaptureResult.Evidence.NOT_ATTEMPTED);eq(1,db.transactionCount());
    }

    public void testCaptureContractMergeAndCancellationCarrySurvivingIdentities(){
        long t=at(LocalDate.now(),10);CaptureResult note=db.captureNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t);
        CaptureResult bank=db.captureSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000);
        eq(bank.transaction.id,bank.transactionId);eq(bank.transaction.id,db.lastCaptured().id);
        ok(db.transactionById(note.transactionId)==null);eq(1,db.transactionCount());eq(2,scalar("SELECT COUNT(*) FROM capture_evidence"));
        long cancelAt=t+3600000;CaptureResult review=db.captureSms("BANK",CANCEL_NOTICE,cancelAt+3000);
        ok(review.needsReview&&review.reason==CaptureResult.Reason.NOTICE_RECORDED&&review.evidence==CaptureResult.Evidence.EVENT_BODY);
        CaptureResult debit=db.captureSms("BANK",CANCEL_DEBIT,cancelAt);
        ok(debit.event!=null&&debit.event.cancelled());ok(debit.transaction==null&&debit.transactionId==null&&!debit.needsReview);
        eq(debit.capturedTransactionId,debit.event.originalId);ok(debit.event.archiveId!=null);eq(1,db.transactionCount());
    }

    public void testCaptureContractIncomingOwnTransferFollowsMergeBeforeFeeIdReuse(){
        long t=at(LocalDate.now(),10),from=db.addPaymentSource("BankMisr","حساب","1111");
        db.addPaymentSource("BankMisr","حساب","2222");
        ok(db.addManualEntry(t,10050,"EGP","QA OUT","تحويلات",from,"expense",null,null));long out=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{out});
        CaptureResult receipt=db.captureSms("BankMisr","تم اضافة مبلغ 100 EGP لحساب رقم 2222 عن طريق التحويل اللحظي",t+60000);
        ok(receipt.accepted&&receipt.dbChanged);ok(receipt.transaction!=null);
        eq(out,receipt.transactionId);eq(out,receipt.transaction.id);ok(receipt.capturedTransactionId!=out);
        eq(10000,receipt.transaction.amountMinor);ok("transfer".equals(receipt.transaction.kind));
        eq(50,db.transferFee(out));eq(2,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM transaction_archive"));
        eq(out,scalar("SELECT transaction_id FROM capture_evidence"));eq(0,scalar("SELECT COUNT(*) FROM reimbursements"));
    }

    public void testCaptureContractFailedRepairDiscardsRolledBackMergeAliases(){
        long t=at(LocalDate.now(),10);ok(db.addSms("BANK","Card purchase EGP 100 at QA SHOP",t));
        long[] pair=tr2Pair();db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id IN (?,?)",new Object[]{pair[2],pair[3]});
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_late_capture_repair_fault BEFORE INSERT ON transactions WHEN NEW.unique_key LIKE 'fee:%' BEGIN SELECT RAISE(ABORT,'qa late repair fault'); END");
        CaptureResult result=db.captureNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t+2000);
        ok(result.accepted&&result.dbChanged);ok(result.completion==CaptureResult.Completion.COMMITTED_REPAIR_FAILED);
        ok(result.transaction!=null);eq(result.capturedTransactionId,result.transactionId);eq(result.transaction.id,result.capturedTransactionId);
        eq(4,db.transactionCount());eq(2,scalar("SELECT COUNT(*) FROM capture_evidence"));
        eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));eq(0,db.transferFee(pair[2]));
        eq(10050,db.transactionById(pair[2]).amountMinor);eq(10000,db.transactionById(pair[3]).amountMinor);
    }

    public void testCaptureConsumerBroadcastsCommittedBalanceOnlyChange(){
        final int[] broadcasts={0};Context observed=new ContextWrapper(context){
            @Override public void sendBroadcast(android.content.Intent intent){if(SmsReceiver.ACTION_DATA_CHANGED.equals(intent.getAction()))broadcasts[0]++;}
        };
        SmsParts parts=new SmsParts();parts.add("BANK",at(LocalDate.now(),10),"Account ending 1234 available balance EGP 1000");
        SmsReceiver.captureNow(observed,parts);
        eq(1,broadcasts[0]);eq(0,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM balance_snapshots"));
    }

    // P2-A baseline: these assertions precede result-contract changes. Boolean means legacy
    // acceptance, not every database write; duplicate evidence replacement also advances revision.
    public void testCaptureBaselineFreshDuplicateAndIgnored(){
        long t=at(LocalDate.now(),10),before=db.changes();String body="Card purchase EGP 100 at QA CONTRACT";
        CaptureResult fresh=db.captureSms("BANK",body,t);
        ok(fresh.accepted);ok(fresh.transaction!=null);eq(10000,fresh.transaction.amountMinor);
        ok("EGP".equals(fresh.transaction.currency));eq(1,db.transactionCount());
        eq(1,scalar("SELECT COUNT(*) FROM capture_evidence"));eq(0,scalar("SELECT COUNT(*) FROM reimbursements"));
        long id=fresh.transaction.id,revision=db.changes();ok(revision>before);
        CaptureResult duplicate=db.captureSms("BANK",body,t);
        ok(!duplicate.accepted);ok(duplicate.transaction==null&&duplicate.event==null);
        eq(id,db.transactions().get(0).id);eq(1,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM capture_evidence"));
        ok(db.changes()>revision,"Same-source evidence replacement currently invalidates revision");
        revision=db.changes();ok(!db.captureSms("BANK",null,t).accepted);
        ok(!db.captureSms("BANK","Your OTP is 123456",t).accepted);eq(revision,db.changes());
        String excluded="Card purchase EGP 20 at QA EXCLUDED";
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages VALUES(?)",new Object[]{CaptureIdentity.smsKey("BANK",excluded,t)});
        revision=db.changes();ok(!db.captureSms("BANK",excluded,t).accepted);eq(revision,db.changes());
        eq(1,db.transactionCount());eq(id,fresh.transaction.id);eq(10000,fresh.transaction.amountMinor);
    }

    public void testCaptureBaselineSideEffectWithoutLedgerRow(){
        long t=at(LocalDate.now(),10),revision=db.changes();
        CaptureResult balance=db.captureSms("BANK","Account ending 1234 available balance EGP 1000",t);
        ok(!balance.accepted);ok(balance.transaction==null&&balance.event==null);
        eq(0,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM balance_snapshots"));
        eq(100000,scalar("SELECT balance_minor FROM balance_snapshots"));
        eq(0,scalar("SELECT COUNT(*) FROM capture_evidence"));ok(db.changes()>revision);
    }

    public void testCaptureBaselineReviewAndHistoricalConsent(){
        long t=at(LocalDate.now(),10),revision=db.changes();
        CaptureResult review=db.captureSms("BANK",CANCEL_NOTICE,t);
        ok(review.accepted);ok(review.transaction==null);ok(review.event!=null&&!review.event.cancelled());
        eq(0,db.transactionCount());eq(1,scalar("SELECT COUNT(*) FROM capture_events WHERE status='needs_review'"));
        eq(0,scalar("SELECT COUNT(*) FROM capture_evidence"));eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        ok(db.changes()>revision);revision=db.changes();
        ok(!db.captureSms("BANK",CANCEL_NOTICE,t).accepted);eq(revision,db.changes());
        SmsPreview history=SmsPreview.parse("BANK",CANCEL_NOTICE,t+3600000);ok(history!=null);
        eq(0,ImportRun.added(db,Collections.singletonList(history)));
        eq(1,scalar("SELECT COUNT(*) FROM capture_events WHERE status='needs_confirmation'"));
        eq(1,scalar("SELECT COUNT(*) FROM sms_import_receipts"));eq(0,db.transactionCount());
    }

    public void testCaptureBaselineCallerRollbackRestoresAllRowsAndRevision()throws Exception{
        long t=at(LocalDate.now(),10),revision=db.changes();String before=revisionData(db.getWritableDatabase());
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            CaptureResult pending=db.captureSms("BM","Card xxx4636 purchase EGP15 at QA CONTRACT. Available balance EGP7240.01",t);
            ok(pending.accepted);ok(pending.transaction!=null);eq(1,db.transactionCount());ok(db.changes()>revision);
        }finally{sql.endTransaction();}
        eq(revision,db.changes());ok(before.equals(revisionData(sql)));ok(sql.isOpen());
    }

    public void testCaptureBaselineEvidenceFailureRollsBackStorage()throws Exception{
        long t=at(LocalDate.now(),10),revision=db.changes();String before=revisionData(db.getWritableDatabase());
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_capture_evidence_fault BEFORE INSERT ON capture_evidence BEGIN SELECT RAISE(ABORT,'qa evidence fault'); END");
        try{db.captureSms("BM","Card xxx4636 purchase EGP15 at QA CONTRACT. Available balance EGP7240.01",t);throw new AssertionError("Evidence failure reported success");}
        catch(android.database.sqlite.SQLiteConstraintException expected){}
        eq(revision,db.changes());ok(before.equals(revisionData(db.getWritableDatabase())));ok(db.getWritableDatabase().isOpen());
    }

    public void testCaptureBaselineLiveRepairFailureRetainsCommittedCapture(){
        long t=at(LocalDate.now(),10);ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        long revision=db.changes();
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_live_repair_fault BEFORE DELETE ON transactions BEGIN SELECT RAISE(ABORT,'qa repair fault'); END");
        CaptureResult legacy=db.captureSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000);ok(legacy.accepted);
        // Live storage and repair are separate owners today. P2-C must address this explicitly.
        eq(2,db.transactionCount());eq(2,scalar("SELECT COUNT(*) FROM capture_evidence"));
        eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));ok(db.changes()>revision);
    }

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

    public void testCaptureResultMatchesLegacyAccessorsForEveryOutcome(){
        long t=at(LocalDate.now(),10);
        CaptureResult fresh=db.captureNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t);
        ok(fresh.accepted);ok(fresh.transaction!=null);eq(db.lastCaptured().id,fresh.transaction.id);ok(fresh.event==null);
        CaptureResult duplicate=db.captureNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t);
        ok(!duplicate.accepted);ok(duplicate.transaction==null);ok(duplicate.event==null);
        long rows=db.transactionCount();
        CaptureResult merged=db.captureSms("BANK","Card purchase EGP 100 at QA SHOP",t+2000);
        ok(merged.accepted);ok(merged.transaction!=null);eq(db.lastCaptured().id,merged.transaction.id);eq(rows,db.transactionCount());ok(db.transactionById(merged.transaction.id)!=null);
        long cancelAt=t+3600000;
        ok(db.captureSms("BANK",CANCEL_DEBIT,cancelAt).accepted);
        CaptureResult cancelled=db.captureSms("BANK",CANCEL_NOTICE,cancelAt+3000);
        ok(cancelled.accepted);ok(cancelled.transaction==null);ok(cancelled.event!=null&&cancelled.event.cancelled());eq(db.lastCaptureEvent().id,cancelled.event.id);
        CaptureResult otp=db.captureSms("BANK","Your OTP is 123456 for payment EGP 227",cancelAt+4000);
        ok(!otp.accepted);ok(otp.transaction==null);ok(otp.event==null);
    }

    public void testCaptureUseCaseKeepsEarlierSnapshotsAndSourceIdentities(){
        long t=at(LocalDate.now(),10);
        CaptureTransaction capture=new CaptureTransaction(db);
        CaptureResult fresh=capture.execute(CaptureRequest.notification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        ok(fresh.accepted);ok(fresh.transaction!=null);ok(fresh.event==null);
        long id=fresh.transaction.id;
        CaptureResult merged=capture.execute(CaptureRequest.sms("BANK","Card purchase EGP 100 at QA SHOP",t+2000));
        ok(merged.accepted);ok(merged.transaction!=null);eq(db.lastCaptured().id,merged.transaction.id);
        ok(db.transactionById(id)==null);eq(1,db.transactionCount());
        long cancelAt=t+3600000;
        ok(capture.execute(CaptureRequest.sms("BANK",CANCEL_DEBIT,cancelAt)).accepted);
        CaptureResult cancelled=capture.execute(CaptureRequest.sms("BANK",CANCEL_NOTICE,cancelAt+3000));
        ok(cancelled.event!=null&&cancelled.event.cancelled());ok(cancelled.transaction==null);
        long eventId=cancelled.event.id;
        CaptureResult ignored=capture.execute(CaptureRequest.sms("BANK","Your OTP is 123456",cancelAt+4000));
        ok(!ignored.accepted);ok(ignored.transaction==null);ok(ignored.event==null);
        eq(id,fresh.transaction.id);eq(10000,fresh.transaction.amountMinor);ok(fresh.event==null);
        eq(eventId,cancelled.event.id);ok(cancelled.event.cancelled());
        String body="Card purchase EGP 37 at QA SECOND SHOP";
        ok(capture.apply(CaptureRequest.inboxSms("BANK",body,t+20000,t+10000,true)));
        eq(1,db.smsCaptureStatus("BANK",body,t+20000,t+10000,true));
        ok(!db.addSms("BANK",body,t+20000,t+10000,true));
        eq(2,db.transactionCount());
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
        eq(2,ImportRun.added(db,Arrays.asList(a,b)));eq(503,db.transactionCount());
        eq(0,ImportRun.added(db,Arrays.asList(a,b)));eq(503,db.transactionCount());
        SmsPreview c=SmsPreview.parse("BANK","Card purchase EGP 7 at QA SHOP C",t+3000);
        try{ImportRun.added(db,Arrays.asList(c,null));throw new AssertionError("Failed batch committed");}
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
        db=new ExpenseDb(context);eq(25,db.getWritableDatabase().getVersion());eq(2,db.transactionCount());
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
    // ---- P2-A import baseline. These fixtures run against unmodified production code and record what the
    // historical SMS/CSV entry points do today: the legacy number is a ledger row-count delta, so a committed
    // row that the selection never named, a refused insert and a side-effect-only import all report 0. ----
    private long receipts(){return scalar("SELECT COUNT(*) FROM sms_import_receipts");}
    private long evidenceCount(){return scalar("SELECT COUNT(*) FROM capture_evidence");}
    private long imported(SmsPreview... rows){return ImportRun.added(db,Arrays.asList(rows));}
    private long selectedDirect(SmsPreview... rows){return ImportRun.added(db,Arrays.asList(rows));}

    public void testImportBaselineFreshRowCommitsOneRowWithReceiptAndEvidence(){
        long t=at(LocalDate.now(),10),rows=db.transactionCount(),revision=db.changes();
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 100 at QA IMPORT FRESH",t);ok(row!=null);
        eq(1,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows+1,db.transactionCount());eq(1,receipts());eq(1,evidenceCount());ok(db.changes()>revision);
        ExpenseDb.Transaction created=db.latestInserted();
        eq(10000,created.amountMinor);ok("expense".equals(created.kind));ok(!created.needsReview);
        eq(created.id,scalar("SELECT transaction_id FROM capture_evidence"));
    }

    public void testImportBaselineIdenticalRowTwiceIsReceiptedOnceAndReplacesNoEvidence(){
        long t=at(LocalDate.now(),10),rows=db.transactionCount(),revision=db.changes();
        String body="Card purchase EGP 120 at QA DUPLICATE";
        SmsPreview first=SmsPreview.parse("BANK",body,t),second=SmsPreview.parse("BANK",body,t);
        ok(first!=null&&second!=null);ok(first.body.equals(second.body));
        eq(1,ImportRun.added(db,Arrays.asList(first,second)));
        eq(rows+1,db.transactionCount());eq(1,receipts());eq(1,evidenceCount());ok(db.changes()>revision);
    }

    public void testImportBaselineDistinctKeyMatchingTheSamePaymentReportsNoSecondRow(){
        long t=at(LocalDate.now(),10);
        SmsPreview first=SmsPreview.parse("BANK","Card purchase EGP 200 at QA SEMANTIC",t);
        SmsPreview second=SmsPreview.parse("BANK","Card  purchase EGP 200 at QA SEMANTIC",t+1000);
        ok(first!=null&&second!=null);ok(!first.body.equals(second.body));
        long rows=db.transactionCount(),revision=db.changes();
        eq(1,ImportRun.added(db,Arrays.asList(first,second)));
        eq(rows+1,db.transactionCount());eq(2,receipts());eq(2,evidenceCount());ok(db.changes()>revision);
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE amount_minor=20000"));
    }

    public void testImportBaselineRowCapturedEarlierIsSkippedWithoutNewReceipt(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 300 at QA ALREADY";
        ok(db.addSms("BANK",body,t));
        SmsPreview row=SmsPreview.parse("BANK",body,t);ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows,db.transactionCount());eq(0,receipts());eq(1,evidenceCount());eq(revision,db.changes());
        eq(1,db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt));
    }

    public void testImportBaselineExcludedKeyIsSkippedWithoutReceiptOrRow(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 400 at QA EXCLUDED IMPORT";
        SmsPreview row=SmsPreview.parse("BANK",body,t);ok(row!=null);
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES(?)",
                new Object[]{CaptureIdentity.smsKey(row.sender,row.body,t)});
        long rows=db.transactionCount(),revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());eq(revision,db.changes());
        eq(2,db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt));
    }

    public void testImportBaselineOtpAndNullBodiesNeverReachTheBatch(){
        long t=at(LocalDate.now(),10);
        ok(SmsPreview.parse("BANK","Your OTP is 123456 for payment EGP 227",t)==null);
        ok(SmsPreview.parse("BANK",null,t)==null);
        SmsPreview kept=SmsPreview.parse("BANK","Card purchase EGP 500 at QA NULL GUARD",t);ok(kept!=null);
        long rows=db.transactionCount(),revision=db.changes();
        try{ImportRun.added(db,Arrays.asList(kept,null));throw new AssertionError("A null row was accepted into the batch");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());eq(0,receipts());eq(revision,db.changes());
        ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportBaselineBalanceOnlyRowWritesSnapshotAndReceiptWithoutALedgerRow(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK","Account ending 1234 available balance EGP 1000",t);
        ok(row!=null);ok("balance".equals(row.kind));
        long revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(0,db.transactionCount());eq(0,evidenceCount());eq(1,receipts());
        eq(1,scalar("SELECT COUNT(*) FROM balance_snapshots"));eq(100000,scalar("SELECT balance_minor FROM balance_snapshots"));
        ok(db.changes()>revision);eq(1,db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt));
    }

    public void testImportBaselineUnmatchedCancellationRecordsReviewWithoutArchive(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK",CANCEL_NOTICE,t);ok(row!=null);ok("correction".equals(row.kind));
        long revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(0,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        eq(1,scalar("SELECT COUNT(*) FROM capture_events WHERE status='needs_confirmation'"));
        eq(0,evidenceCount());eq(1,receipts());ok(db.changes()>revision);
    }

    public void testImportBaselineMatchedCancellationStillWaitsForReviewWithoutArchiving(){
        long t=at(LocalDate.now(),10),account=db.addPaymentSource("BANK","حساب","1111");
        ok(db.addManualEntry(t-120000,22700,"EGP","تحويل QA","تحويلات",account,"expense",null,null));
        long debit=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{debit});
        SmsPreview row=SmsPreview.parse("BANK",CANCEL_NOTICE,t);ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        ok(db.transactionById(debit)!=null,"A historical cancellation archived the debit it should only flag");
        eq(22700,db.transactionById(debit).amountMinor);ok("expense".equals(db.transactionById(debit).kind));
        eq(1,scalar("SELECT COUNT(*) FROM capture_events WHERE status='needs_confirmation'"));
        eq(1,receipts());ok(db.changes()>revision);
    }

    public void testImportBaselineRepairTouchesARowTheSelectionNeverNamed(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        long incomingAccount=db.addPaymentSource("BankMisr","حساب","2222");
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",incomingAccount,"income",null,null));
        long incoming=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{incoming});
        SmsPreview row=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        ok(row!=null);ok(row.isTransaction(),"the selected row is not a ledger candidate");
        long rows=db.transactionCount(),revision=db.changes();
        eq(1,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows+1,db.transactionCount());
        long transfer=scalar("SELECT id FROM transactions WHERE kind='transfer' LIMIT 1");
        eq(10000,db.transactionById(transfer).amountMinor);eq(50,db.transferFee(transfer));
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id="+transfer));
        ok(db.transactionById(incoming)==null,"repair removed a row the selection never named");
        eq(1,scalar("SELECT COUNT(*) FROM transaction_archive WHERE original_id="+incoming));
        eq(2,db.transactionCount());ok(db.changes()>revision);
    }

    public void testImportBaselineCrossChannelDuplicateIsMergedAfterTheSelectedRowIsSaved(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 100 at QA SHOP",t+150000);ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows,db.transactionCount());
        eq(1,scalar("SELECT COUNT(*) FROM transaction_archive"));eq(1,scalar("SELECT COUNT(*) FROM transactions"));
        ok(db.changes()>revision);
    }

    public void testImportBaselineFaultOnTheLastSelectedRowLeavesNothingSaved(){
        long t=at(LocalDate.now(),10);
        SmsPreview first=SmsPreview.parse("BANK","Card purchase EGP 10 at QA KEEP",t);
        SmsPreview fault=SmsPreview.parse("BANK","Card purchase EGP 20 at QA FAULT",t+1000);
        ok(first!=null&&fault!=null);
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_import_fault BEFORE INSERT ON transactions WHEN NEW.merchant='QA FAULT' BEGIN SELECT RAISE(ABORT,'qa import fault'); END");
        long rows=db.transactionCount(),revision=db.changes();
        try{ImportRun.added(db,Arrays.asList(first,fault));throw new AssertionError("A faulty batch was reported as imported");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());eq(revision,db.changes());
        ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportBaselineRepairFaultAfterAnEarlierRepairWriteRollsBackEverything(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        long incomingAccount=db.addPaymentSource("BankMisr","حساب","2222");
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",incomingAccount,"income",null,null));
        long incoming=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{incoming});
        // The fee row is written before the incoming leg is archived, so the archive fault lands after a repair write.
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_import_archive_fault BEFORE INSERT ON transaction_archive BEGIN SELECT RAISE(ABORT,'qa archive fault'); END");
        SmsPreview row=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        try{ImportRun.added(db,Collections.singletonList(row));throw new AssertionError("A failed repair was reported as a saved import");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        eq(0,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id IS NOT NULL"));
        ok(db.transactionById(incoming)!=null&&"income".equals(db.transactionById(incoming).kind));
        eq(revision,db.changes());ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportBaselineCallerTransactionNeverReportsACommittedImport(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 600 at QA CALLER",t);ok(row!=null); // not "pending": the pipeline reads that word as an unexecuted operation
        SQLiteDatabase sql=db.getWritableDatabase();
        long rows=db.transactionCount(),revision=db.changes(),value;
        sql.beginTransaction();
        try{
            value=ImportRun.added(db,Collections.singletonList(row));
            eq(1,value);eq(rows+1,db.transactionCount());eq(1,receipts());ok(sql.inTransaction());
        }finally{sql.endTransaction();}
        eq(rows,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());eq(revision,db.changes());
        ok(sql.isOpen()&&!sql.inTransaction());
    }

    public void testImportBaselineEmptySelectionWritesNothing(){
        long revision=db.changes();
        eq(0,ImportRun.added(db,java.util.Collections.<SmsPreview>emptyList()));
        eq(0,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());eq(revision,db.changes());
    }

    public void testImportBaselineIgnoredMerchantRuleRefusesTheRowButStillWritesAReceipt(){
        long t=at(LocalDate.now(),10);db.putMerchantRule("QA IGNORED IMPORT","تسوق",true);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 40 at QA IGNORED IMPORT",t);ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows,db.transactionCount());eq(0,evidenceCount());eq(1,receipts());
        ok(db.changes()>revision,"a refused insert still committed its receipt");
        eq(1,db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt));
    }

    public void testImportBaselineHintOnlyRowWritesASuggestionWithoutALedgerRow(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("AnotherFinance","نذكرك بسداد القسط الشهري، يرجى مراجعة التطبيق للمبلغ",t);
        ok(row!=null);ok("commitment".equals(row.kind));
        long revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(0,db.transactionCount());eq(0,evidenceCount());eq(1,receipts());
        eq(1,scalar("SELECT COUNT(*) FROM commitment_suggestions"));
        eq(1,scalar("SELECT COUNT(*) FROM commitment_observations"));ok(db.changes()>revision);
    }

    public void testImportServiceOnlyRowStoresAReviewWithoutAdvancingTheLegacyBaseline(){
        long t=at(LocalDate.now(),10);
        ok(db.addRecurring("Netflix",5000,"EGP",LocalDate.now()));
        long commitment=scalar("SELECT id FROM recurring ORDER BY id DESC LIMIT 1");
        SmsPreview row=SmsPreview.parse("Billing","Netflix bill paid successfully",t);ok(row!=null);ok(!row.isTransaction());
        long revision=db.changes();
        eq(0,ImportRun.added(db,Collections.singletonList(row)));
        eq(0,db.transactionCount());eq(0,evidenceCount());eq(1,receipts());
        eq(0,scalar("SELECT COUNT(*) FROM recurring WHERE id="+commitment+" AND last_paid_date IS NOT NULL"));
        eq(1,db.pendingCommitmentConfirmations().size());eq(0,db.recurringById(commitment).paidCount);
        ok(db.changes()>revision,"a saved provider review is not a no-op import");
    }

    public void testImportBaselineApplySelectedStillCountsOnlyLedgerRows(){
        long t=at(LocalDate.now(),10);
        SmsPreview fresh=SmsPreview.parse("BANK","Card purchase EGP 90 at QA APPLY",t);
        SmsPreview balance=SmsPreview.parse("BANK","Account ending 1234 available balance EGP 1000",t+1000);
        ok(fresh!=null&&balance!=null);
        eq(1,selectedDirect(fresh,balance));
        eq(1,db.transactionCount());eq(2,receipts());eq(1,evidenceCount());
        eq(1,scalar("SELECT COUNT(*) FROM balance_snapshots"));
    }

    public void testImportBaselineCsvFreshFileThenReimportCountsTheSameRowsBothWays(){
        String csv="Date,Merchant,Amount\n2026-10-15,QA ONE,-1.00\n2026-10-15,QA TWO,-2.00\n2026-10-15,QA THREE,-3.00\n2026-10-15,QA FOUR,-4.00";
        List<CsvImport.Row> rows=CsvImport.parse(csv);eq(4,rows.size());
        long revision=db.changes();
        ExpenseDb.CsvBatchResult first=db.importCsvBatch("qa-baseline-file",rows);
        eq(4,first.saved);eq(0,first.duplicates);
        eq(4,db.transactionCount());eq(0,evidenceCount());eq(0,receipts());
        eq(4,scalar("SELECT COUNT(*) FROM transactions WHERE time_precision='date_only'"));
        ok(db.changes()>revision);
        long afterFirst=db.changes();
        ExpenseDb.CsvBatchResult again=db.importCsvBatch("qa-baseline-file",rows);
        eq(0,again.saved);eq(4,again.duplicates);eq(4,db.transactionCount());eq(afterFirst,db.changes());
    }

    public void testImportBaselineCsvExcludedKeyIsCountedAsADuplicateForLegacy(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA EXCLUDED CSV,-5.00\n2026-10-15,QA CSV TWO,-6.00");
        eq(2,rows.size());
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES(?)",
                new Object[]{"csv:qa-excluded-file:"+rows.get(0).line});
        long revision=db.changes();
        ExpenseDb.CsvBatchResult result=db.importCsvBatch("qa-excluded-file",rows);
        eq(1,result.saved);eq(1,result.duplicates);
        eq(1,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transactions WHERE merchant='QA EXCLUDED CSV'"));
        ok(db.changes()>revision,"the saved row still invalidated the ledger");
    }

    public void testImportBaselineCsvIgnoredMerchantRuleIsBypassedAndTheRowIsSaved(){
        db.putMerchantRule("QA CSV IGNORED","تسوق",true);
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CSV IGNORED,-7.00");
        eq(1,rows.size());
        ExpenseDb.CsvBatchResult result=db.importCsvBatch("qa-ignored-file",rows);
        eq(1,result.saved);eq(0,result.duplicates);
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE merchant='QA CSV IGNORED'"));
        eq(0,evidenceCount());eq(0,receipts());
    }

    public void testImportBaselineCsvInvalidAmountIsRejectedBeforeTheBatchOwnsIt(){
        try{CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA ZERO,0.00");throw new AssertionError("A non-positive CSV row was parsed");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(0,db.transactionCount());eq(0,receipts());
    }

    public void testImportBaselineCsvInvalidRowsAreRejectedWithNothingSaved(){
        // The parser is the failure surface for an unsaveable row: a non-positive amount and a blank merchant
        // never reach the batch, so "not saved" cannot be misreported as a duplicate or a refusal.
        for(String row:new String[]{"2026-10-15,QA ZERO,0.00","2026-10-15,,-9.00"}){
            try{CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA FINE CSV,-8.00\n"+row);
                throw new AssertionError("An invalid CSV row was accepted: "+row);}
            catch(AssertionError wrong){throw wrong;}
            catch(RuntimeException expected){}
        }
        eq(0,db.transactionCount());eq(0,receipts());eq(0,evidenceCount());
        ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportBaselineCsvFaultMidFileLeavesNothingSavedAndTheHelperOpen(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA ONE CSV,-1.00\n2026-10-15,QA CSV FAULT,-2.00\n2026-10-15,QA CSV THREE,-3.00");
        eq(3,rows.size());
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_baseline_csv_fault BEFORE INSERT ON transactions WHEN NEW.merchant='QA CSV FAULT' BEGIN SELECT RAISE(ABORT,'qa baseline csv fault'); END");
        long revision=db.changes();
        try{db.importCsvBatch("qa-fault-file",rows);throw new AssertionError("A faulty CSV batch was reported as imported");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(0,db.transactionCount());eq(revision,db.changes());
        ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportBaselineCsvBatchInsideACallerTransactionIsNeverReportedAsCommitted(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CSV PENDING,-1.00\n2026-10-15,QA CSV PENDING TWO,-2.00");
        eq(2,rows.size());
        SQLiteDatabase sql=db.getWritableDatabase();
        long revision=db.changes();
        sql.beginTransaction();
        try{
            ExpenseDb.CsvBatchResult result=db.importCsvBatch("qa-pending-file",rows);
            eq(2,result.saved);eq(2,db.transactionCount());ok(sql.inTransaction());
        }finally{sql.endTransaction();}
        eq(0,db.transactionCount());eq(revision,db.changes());ok(sql.isOpen()&&!sql.inTransaction());
    }

    // ---- P2-A import contract (stage 02): the same batches through the new detailed entry points. Every
    // fixture states the primary outcome of each selected row, and asserts the invariant that the primary
    // outcomes add up to offered; side effects and repair are asserted separately, never folded into them. ----
    private ImportResult detailed(SmsPreview... rows){return new ImportBatch(db).executeDetailed(Arrays.asList(rows));}
    private void invariant(ImportResult result){
        eq(result.offered,result.rows.size());
        eq(result.offered,result.created+result.matchedExisting+result.skippedDuplicate+result.excluded+result.refused+result.noLedgerChange);
        ok(result.survivingCreated<=result.created);
    }

    /**
     * The owner's day, 22 Oct 2026 (tools/fixtures/owner-day-2026-10-22.tsv): messages 1-3 and 5 imported from the inbox,
     * then 4 and 6 delivered live. The import decides with the new pipeline, discovers the sources silently, and ends with
     * the same ledger as live capture: two internal transfers, one fee and one income, no standalone income.
     */
    public void testOwnerDayImportThenLiveEndsInTwoInternalTransfers(){
        long morning=LocalDate.of(2026,10,22).atTime(6,47).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long september=LocalDate.of(2026,9,25).atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String[][] inbox={
                {"HSBC","Your HSBC Account ********0001 was debited with IPN outward transfer for EGP 4.50 on 22-10-2026 06:46 to 7392 with reference 12fb34cc. For further details, please contact HSBC call centre",Long.toString(morning)},
                {"BankMisr","تم اضافة مبلغ 4EGP الى حساب رقم xxx7392 فى 22-Oct-2026 عن طريق التحويل اللحظي من MOHAMED GAMAL ABDELAZIZ",Long.toString(morning+30_000L)},
                {"BankMisr","تم تحويل 30.00 جنيه بنجاح إلى talda من حساب رقم xxx7392 عن طريق التحويل اللحظي",Long.toString(morning+10*60_000L)},
                {"HSBC","From HSBC: 25SEP26 Phone Banking Transfer to ********0001 from HEBATALLAH SAMI EGP 87,650.00+ Avl Bal EGP 87,700.00",Long.toString(september)}};
        PipelineDecider.Context batch=PipelineDecider.context(db,"import");
        List<SmsPreview> rows=new java.util.ArrayList<>();
        for(String[] m:inbox){
            SmsPreview row=PipelineDecider.importRow(batch,m[0],m[1],Long.parseLong(m[2]),Long.parseLong(m[2]));
            ok(row!=null&&row.isTransaction(),"Imported as a movement: "+m[1]);
            ok(row.decision!=null&&row.decision.records,"Decided by the new pipeline: "+m[1]);
            row.captureStatus=db.smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt,row.receiptKeySafe);rows.add(row);
        }
        SmsPreview.selectForImport(rows);
        ImportResult result=new ImportBatch(db).executeDetailed(rows);ok(result.committed,"Batch committed");
        db.captureNotification("io.telda.app","Telda","تم استلام 30 ج.م. من خلال شبكة المدفوعات اللحظية",morning+34*60_000L);
        db.captureNotification("com.egyptianbanks.instapay","InstaPay","إستلام نقود لقد استلمت 4.00 جنيه من mo.gamalhsbc@instapay",morning+5*60_000L);
        Long hsbc=null,misr=null,telda=null;
        for(ExpenseDb.PaymentSource s:db.paymentSources()){
            if("0001".equals(s.last4))hsbc=s.id;
            if("7392".equals(s.last4))misr=s.id;
            if("TELDA".equals(SourceIdentity.bank(s.name)))telda=s.id;
        }
        ok(hsbc!=null&&misr!=null&&telda!=null,"Sources HSBC 0001, Banque Misr 7392 and Telda discovered: "+db.paymentSources().size());
        int transfers=0,fees=0,incomes=0;StringBuilder ledger=new StringBuilder();
        for(ExpenseDb.Transaction tx:db.transactions()){
            ledger.append(" | ").append(tx.kind).append(' ').append(tx.amountMinor).append(' ').append(tx.paymentSourceId).append("->").append(tx.destinationSourceId);
            if("transfer".equals(tx.kind)&&tx.amountMinor==400&&hsbc.equals(tx.paymentSourceId)&&misr.equals(tx.destinationSourceId))transfers++;
            else if("transfer".equals(tx.kind)&&tx.amountMinor==3000&&misr.equals(tx.paymentSourceId)&&telda.equals(tx.destinationSourceId))transfers++;
            else if("expense".equals(tx.kind)&&tx.amountMinor==50)fees++;
            else if("income".equals(tx.kind)&&tx.amountMinor==8765000)incomes++;
            else throw new AssertionError("Unexpected row:"+ledger);
        }
        ok(transfers==2&&fees==1&&incomes==1&&db.transactionCount()==4,"Two internal transfers, a 0.50 fee and the 87,650 income:"+ledger);
    }

    /**
     * Owner acceptance, 22 Oct 2026: rows the older build recorded that morning had no source, so the repair never saw the
     * transfer. The data check reads each automatic row's saved message, attaches its source and pairs the transfer.
     */
    public void testDataCheckInfersMissingSourcesAndPairsTheTransfer(){
        long morning=LocalDate.of(2026,10,22).atTime(6,47).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        CaptureResult debit=db.captureSms("HSBC","Your HSBC Account ********0001 was debited with IPN outward transfer for EGP 4.50 on 22-10-2026 06:46 to 7392 with reference 12fb34cc. For further details, please contact HSBC call centre",morning);
        db.getWritableDatabase().execSQL("UPDATE transactions SET payment_source_id=NULL,destination_source_id=NULL WHERE id="+debit.transaction.id);
        CaptureResult receipt=db.captureSms("BankMisr","تم اضافة مبلغ 4EGP الى حساب رقم xxx7392 فى 22-Oct-2026 عن طريق التحويل اللحظي من MOHAMED GAMAL ABDELAZIZ",morning+30_000L);
        db.getWritableDatabase().execSQL("UPDATE transactions SET payment_source_id=NULL,destination_source_id=NULL WHERE id="+receipt.transaction.id);
        eq(2,db.transactionCount(),"Two rows without a source before the check");
        ExpenseDb.RepairReport report=db.repairAll();
        eq(2,report.sourcesInferred,"Sources read from the saved messages");
        int transfers=0,fees=0;StringBuilder ledger=new StringBuilder();
        for(ExpenseDb.Transaction tx:db.transactions()){
            ledger.append(" | ").append(tx.kind).append(' ').append(tx.amountMinor).append(' ').append(tx.paymentSourceId).append("->").append(tx.destinationSourceId);
            if("transfer".equals(tx.kind)&&tx.amountMinor==400)transfers++;else if("expense".equals(tx.kind)&&tx.amountMinor==50)fees++;
        }
        ok(transfers==1&&fees==1&&db.transactionCount()==2,"One transfer of 4.00 and its 0.50 fee:"+ledger);
        eq(0,db.repairAll().sourcesInferred,"Nothing left to infer the second time");
    }

    /** Owner acceptance (dark mode): no white plate or ring around a logo; the circle is the logo's own colour. */
    public void testDarkLogoBadgeHasNoLightPlate(){
        int surface=0xff1c1f24, light=context.getColor(R.color.ui_surface_light);
        int checked=0;
        for(String brand:new String[]{"hsbc","bank_misr","instapay","google_one","vodafone","netflix","telda"}){
            int logo=MainActivity.brandLogo(brand);if(logo==0)continue;checked++;
            android.widget.ImageView dark=UiKit.logoBadge(context,logo,brand,brand,40,0,true,surface);
            android.graphics.drawable.GradientDrawable plate=(android.graphics.drawable.GradientDrawable)dark.getBackground();
            int color=plate.getColor().getDefaultColor();
            int edge=UiKit.edgeColor(context.getDrawable(logo),surface);
            eq(edge,color,"Dark plate of "+brand+" is the logo's own edge colour");
            ok(color!=light||edge==light,"Dark plate of "+brand+" is the light surface around a logo that is not white");
            ok(dark.getClipToOutline(),"Dark badge of "+brand+" is clipped to its circle");
            android.widget.ImageView day=UiKit.logoBadge(context,logo,brand,brand,40,0);
            eq(UiKit.whiteOnlyLogo(brand)?context.getColor(R.color.brand_forest):light,((android.graphics.drawable.GradientDrawable)day.getBackground()).getColor().getDefaultColor(),
                    "Light mode keeps its plate for "+brand);
        }
        ok(checked>=3,"Too few brand logos checked: "+checked);
    }

    public void testImportContractFreshRowIsCreatedWithItsSideEffects(){
        long t=at(LocalDate.now(),10),revision=db.changes();
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 100 at QA CONTRACT FRESH",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.offered);eq(1,result.created);eq(1,result.survivingCreated);eq(0,result.matchedExisting);
        eq(0,result.skippedDuplicate);eq(0,result.excluded);eq(0,result.refused);eq(0,result.noLedgerChange);
        eq(1,result.evidenceWritten);eq(1,result.receiptWritten);eq(0,result.hintWritten);
        eq(0,result.balanceWritten);eq(0,result.eventRecorded);eq(0,result.linksChanged);
        ok(result.completion==CaptureResult.Completion.COMMITTED);ok(result.committed);ok(result.dbChanged);
        ImportResult.Row only=result.rows.get(0);
        ok(only.outcome==ImportResult.Outcome.CREATED);ok(only.reason==ImportResult.Reason.INSERTED);
        ok(only.knownBy==null);ok(only.transactionId!=null);ok(only.surviving);ok(!only.effects.none());
        eq(only.transactionId.longValue(),db.latestInserted().id);
        eq(0,result.repair.ownTransfers);eq(0,result.repair.feesCreated);eq(0,result.repair.archivedRows);
        eq(0,result.repair.mergedRows);eq(0,result.repair.movedMinor);eq(0,result.repair.feesMinor);
        ok(result.revisionAfter>revision);eq(1,db.transactionCount());
    }

    public void testImportContractIdenticalRowIsSkippedWithoutItsOwnWrites(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 120 at QA CONTRACT DUP";
        SmsPreview first=SmsPreview.parse("BANK",body,t),second=SmsPreview.parse("BANK",body,t);
        ok(first!=null&&second!=null);
        ImportResult result=detailed(first,second);
        invariant(result);
        eq(1,result.created);eq(1,result.skippedDuplicate);eq(1,result.receiptWritten);eq(1,result.evidenceWritten);
        ImportResult.Row skipped=result.rows.get(1);
        ok(skipped.outcome==ImportResult.Outcome.SKIPPED_DUPLICATE);
        ok(skipped.reason==ImportResult.Reason.SKIPPED_DUPLICATE);
        ok(skipped.knownBy==ImportResult.Known.EVIDENCE);
        ok(skipped.effects.none(),"a duplicate short-circuits before any write, so it must claim none");
        ok(skipped.transactionId==null);
        eq(1,scalar("SELECT COUNT(*) FROM sms_import_receipts"));eq(1,evidenceCount());
    }

    public void testImportContractDistinctKeyMatchIsOneMatchedRowWithItsOwnEvidence(){
        long t=at(LocalDate.now(),10);
        SmsPreview first=SmsPreview.parse("BANK","Card purchase EGP 200 at QA CONTRACT SEMANTIC",t);
        SmsPreview second=SmsPreview.parse("BANK","Card  purchase EGP 200 at QA CONTRACT SEMANTIC",t+1000);
        ok(first!=null&&second!=null&&!first.body.equals(second.body));
        ImportResult result=detailed(first,second);
        invariant(result);
        eq(1,result.created);eq(1,result.matchedExisting);eq(0,result.skippedDuplicate);eq(0,result.refused);
        ImportResult.Row matched=result.rows.get(1);
        ok(matched.outcome==ImportResult.Outcome.MATCHED_EXISTING);
        ok(matched.reason==ImportResult.Reason.EXISTING_MATCH);
        ok(matched.transactionId!=null&&result.rows.get(0).transactionId!=null);
        ok(matched.transactionId.longValue()==result.rows.get(0).transactionId.longValue());
        ok(matched.effects.evidenceWritten,"a semantic match records its own evidence");
        ok(matched.effects.receiptWritten);
        eq(1,db.transactionCount());eq(2,evidenceCount());
    }

    public void testImportContractAlreadyCapturedRowIsSkippedWithoutNewWrites(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 300 at QA CONTRACT ALREADY";
        ok(db.addSms("BANK",body,t));
        SmsPreview row=SmsPreview.parse("BANK",body,t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.skippedDuplicate);eq(0,result.created);
        eq(0,result.receiptWritten);eq(0,result.evidenceWritten);
        ok(result.rows.get(0).knownBy==ImportResult.Known.EVIDENCE);
        ok(result.rows.get(0).effects.none());
        ok(result.committed);ok(!result.dbChanged,"nothing was written, so the revision must not move");
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
    }

    public void testImportContractExcludedKeyIsExcludedNotDuplicated(){
        long t=at(LocalDate.now(),10);String body="Card purchase EGP 400 at QA CONTRACT EXCLUDED";
        SmsPreview row=SmsPreview.parse("BANK",body,t);ok(row!=null);
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES(?)",
                new Object[]{CaptureIdentity.smsKey(row.sender,row.body,t)});
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.excluded);eq(0,result.skippedDuplicate);eq(0,result.created);
        ImportResult.Row only=result.rows.get(0);
        ok(only.reason==ImportResult.Reason.EXCLUDED);ok(only.knownBy==ImportResult.Known.EXCLUDED);
        ok(only.effects.none());ok(!result.dbChanged);
    }

    public void testImportContractBalanceOnlyRowReportsTheBalanceNotALedgerRow(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK","Account ending 1234 available balance EGP 1000",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.noLedgerChange);eq(0,result.created);eq(0,result.refused);
        eq(1,result.balanceWritten);eq(1,result.receiptWritten);eq(0,result.evidenceWritten);eq(0,result.hintWritten);
        ImportResult.Row only=result.rows.get(0);
        ok(only.outcome==ImportResult.Outcome.NO_LEDGER_CHANGE);
        ok(only.reason==ImportResult.Reason.NO_LEDGER_CANDIDATE);
        ok(only.transactionId==null);ok(result.dbChanged);
    }

    public void testImportContractCancellationRecordsReviewWithoutArchiving(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK",CANCEL_NOTICE,t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.noLedgerChange);eq(0,result.created);eq(1,result.eventRecorded);eq(1,result.receiptWritten);
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        eq(0,result.repair.archivedRows);eq(0,result.repair.mergedRows);eq(0,result.repair.ownTransfers);
        eq(1,scalar("SELECT COUNT(*) FROM capture_events WHERE status='needs_confirmation'"));
    }

    public void testImportContractMatchedCancellationWaitsForReviewWithoutArchiving(){
        long t=at(LocalDate.now(),10),account=db.addPaymentSource("BANK","حساب","1111");
        ok(db.addManualEntry(t-120000,22700,"EGP","تحويل QA","تحويلات",account,"expense",null,null));
        long debit=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{debit});
        SmsPreview row=SmsPreview.parse("BANK",CANCEL_NOTICE,t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.noLedgerChange);eq(1,result.eventRecorded);eq(0,result.repair.archivedRows);
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        ok(db.transactionById(debit)!=null,"a historical cancellation must not archive the debit it only flags");
    }

    public void testImportContractRepairReportsTheRowsItMovedInsteadOfTheDelta(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        long incomingAccount=db.addPaymentSource("BankMisr","حساب","2222");
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",incomingAccount,"income",null,null));
        long incoming=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{incoming});
        SmsPreview row=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        ok(row!=null);
        long rows=db.transactionCount();
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.created);eq(1,result.survivingCreated);
        eq(1,result.repair.ownTransfers);eq(1,result.repair.feesCreated);
        eq(1,result.repair.archivedRows);eq(1,result.repair.mergedRows);
        eq(0,result.archivedSelectedRows(),"the archived row is pre-existing, not one the selection named");
        eq(10000,result.repair.movedMinor);eq(50,result.repair.feesMinor);
        long delta=db.transactionCount()-rows;
        eq(1,delta);
        // The legacy number equals this composition, not the selection: it coincided with the selected count.
        eq(delta,result.created+result.repair.feesCreated-result.repair.archivedRows);
    }

    public void testImportContractCrossChannelMergeIsRepairNotSelection(){
        long t=at(LocalDate.now(),10);
        ok(db.addNotification("qa.bank","QA PAY","You paid EGP 100 to QA SHOP",t));
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 100 at QA SHOP",t+150000);ok(row!=null);
        long rows=db.transactionCount();
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.created);eq(1,result.survivingCreated);
        eq(1,result.repair.mergedRows);eq(1,result.repair.archivedRows);
        eq(0,result.archivedSelectedRows(),"the archived notification is not one the selection named");
        eq(0,result.repair.ownTransfers);eq(0,result.repair.feesCreated);
        ok(0==db.transactionCount()-rows,"repair removed a row the selection never named");
    }

    public void testImportContractIgnoredMerchantIsRefusedByItsStoredRule(){
        long t=at(LocalDate.now(),10);db.putMerchantRule("QA CONTRACT IGNORED","تسوق",true);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 40 at QA CONTRACT IGNORED",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.refused);eq(0,result.noLedgerChange);
        ImportResult.Row only=result.rows.get(0);
        ok(only.reason==ImportResult.Reason.MERCHANT_IGNORED);
        ok(only.effects.receiptWritten);ok(!only.effects.evidenceWritten);ok(!only.effects.storedEffect());
        ok(result.dbChanged,"the unconditional receipt still advances the revision");
    }

    public void testImportContractHintOnlyRowReportsTheHintNotARefusal(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("AnotherFinance","نذكرك بسداد القسط الشهري، يرجى مراجعة التطبيق للمبلغ",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.noLedgerChange);eq(0,result.refused);
        eq(1,result.hintWritten);eq(1,result.receiptWritten);eq(0,result.balanceWritten);
        ok(result.rows.get(0).reason==ImportResult.Reason.NO_LEDGER_CANDIDATE);
        eq(0,result.created);
    }

    public void testImportContractServiceReviewReportsTheStoredCommitmentEffect(){
        long t=at(LocalDate.now(),10);
        ok(db.addRecurring("Netflix",5000,"EGP",LocalDate.now()));
        SmsPreview row=SmsPreview.parse("Billing","Netflix bill paid successfully",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(1,result.noLedgerChange);eq(1,result.linksChanged);eq(1,result.receiptWritten);eq(0,result.created);
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        eq(1,db.pendingCommitmentConfirmations().size());
    }

    public void testImportContractOutOfWindowServiceKeepsReviewEvidenceWithoutPaying(){
        long t=at(LocalDate.now(),10);
        ok(db.addRecurring("QA INELIGIBLE",5000,"EGP",LocalDate.now().plusDays(20)));
        long commitment=scalar("SELECT id FROM recurring ORDER BY id DESC LIMIT 1");
        SmsPreview row=SmsPreview.parse("Billing","QA INELIGIBLE bill paid successfully",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(0,result.refused);eq(1,result.noLedgerChange);eq(1,result.linksChanged);
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        ok(result.rows.get(0).effects.receiptWritten);
        eq(0,scalar("SELECT COUNT(*) FROM recurring WHERE id="+commitment+" AND last_paid_date IS NOT NULL"));
    }

    public void testImportContractPendingCallerDetailCannotLookSuccessful(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 600 at QA CONTRACT CALLER",t);ok(row!=null); // not "pending": the pipeline reads that word as an unexecuted operation
        SQLiteDatabase sql=db.getWritableDatabase();
        long rows=db.transactionCount(),revision=db.changes();ImportResult result;
        sql.beginTransaction();
        try{
            result=detailed(row);
            invariant(result);
            eq(1,result.created);
            ok(!result.committed);ok(result.completion==CaptureResult.Completion.PENDING_CALLER);
            ok(!result.dbChanged,"writes still in flight are not a committed change");
            eq(rows+1,db.transactionCount());
        }finally{sql.endTransaction();}
        invariant(result);
        eq(rows,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));eq(revision,db.changes());
        ok(sql.isOpen()&&!sql.inTransaction());
    }

    public void testImportContractEmptySelectionReportsNothing(){
        ImportResult result=detailed();
        invariant(result);
        eq(0,result.offered);eq(0,result.created);
        ok(result.committed);ok(!result.dbChanged);
        eq(0,result.repair.archivedRows);eq(0,result.repair.feesCreated);eq(0,result.repair.ownTransfers);
        eq(0,db.transactionCount());
    }


    public void testImportContractLegacyValueStaysTheLedgerDeltaAfterTheNewEntryPointExists(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 150 at QA CONTRACT LEGACY",t);ok(row!=null);
        long rows=db.transactionCount();
        eq(1,ImportRun.added(db,Collections.singletonList(row)));
        eq(rows+1,db.transactionCount());
        // The same row is now known: the detailed result reports a skip and still no ledger change.
        ImportResult again=detailed(row);
        invariant(again);
        eq(1,again.skippedDuplicate);eq(0,again.created);
        eq(rows+1,db.transactionCount());
    }

    public void testImportContractFaultPublishesNoResult(){
        long t=at(LocalDate.now(),10);
        SmsPreview first=SmsPreview.parse("BANK","Card purchase EGP 10 at QA CONTRACT KEEP",t);
        SmsPreview fault=SmsPreview.parse("BANK","Card purchase EGP 20 at QA CONTRACT FAULT",t+1000);
        ok(first!=null&&fault!=null);
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_contract_fault BEFORE INSERT ON transactions WHEN NEW.merchant='QA CONTRACT FAULT' BEGIN SELECT RAISE(ABORT,'qa contract fault'); END");
        long rows=db.transactionCount(),revision=db.changes();
        try{detailed(first,fault);throw new AssertionError("A faulty batch published a result");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
        eq(revision,db.changes());ok(db.getWritableDatabase().isOpen()&&!db.getWritableDatabase().inTransaction());
    }

    public void testImportContractRepairFaultPublishesNoResult(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        long incomingAccount=db.addPaymentSource("BankMisr","حساب","2222");
        ok(db.addManualEntry(t+60000,10000,"EGP","QA in","دخل",incomingAccount,"income",null,null));
        long incoming=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=0 WHERE id=?",new Object[]{incoming});
        db.getWritableDatabase().execSQL("CREATE TRIGGER qa_contract_archive_fault BEFORE INSERT ON transaction_archive BEGIN SELECT RAISE(ABORT,'qa contract archive fault'); END");
        SmsPreview row=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        ok(row!=null);
        long rows=db.transactionCount(),revision=db.changes();
        try{detailed(row);throw new AssertionError("A failed repair published a result");}
        catch(AssertionError wrong){throw wrong;}
        catch(RuntimeException expected){}
        eq(rows,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        eq(0,scalar("SELECT COUNT(*) FROM transactions WHERE fee_parent_id IS NOT NULL"));
        ok(db.transactionById(incoming)!=null&&"income".equals(db.transactionById(incoming).kind));
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));eq(revision,db.changes());
    }

    public void testImportContractCsvFreshFileSplitsCreatedFromAlreadyPresent(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CS ONE,-1.00\n2026-10-15,QA CS TWO,-2.00\n2026-10-15,QA CS THREE,-3.00\n2026-10-15,QA CS FOUR,-4.00");
        eq(4,rows.size());
        CsvImportResult result=db.importCsvBatchDetailed("qa-contract-file",rows);
        eq(4,result.created);eq(0,result.alreadyPresent);eq(0,result.excluded);eq(4,result.rows.size());
        for(CsvImportResult.Row row:result.rows){ok(row.outcome==CsvImportResult.Outcome.CREATED);ok(row.transactionId!=null);}
        ok(result.committed);ok(result.dbChanged);ok(result.completion==CaptureResult.Completion.COMMITTED);
        eq(4,db.transactionCount());eq(0,evidenceCount());eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
        eq(4,scalar("SELECT COUNT(*) FROM transactions WHERE time_precision='date_only'"));
        ExpenseDb.CsvBatchResult legacy=db.importCsvBatch("qa-contract-file",rows);
        eq(0,legacy.saved);eq(4,legacy.duplicates);
    }

    public void testImportContractCsvExcludedRowIsSplitFromAlreadyPresent(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CS EXCLUDED,-5.00\n2026-10-15,QA CS TWO,-6.00");
        eq(2,rows.size());
        db.getWritableDatabase().execSQL("INSERT INTO excluded_messages(unique_key) VALUES(?)",
                new Object[]{"csv:qa-contract-excluded:"+rows.get(0).line});
        CsvImportResult result=db.importCsvBatchDetailed("qa-contract-excluded",rows);
        eq(1,result.created);eq(1,result.excluded);eq(0,result.alreadyPresent);
        ok(result.rows.get(0).outcome==CsvImportResult.Outcome.EXCLUDED);
        eq(1,db.transactionCount());
        // The legacy number still reports both as duplicates; only the detailed result splits them.
        ExpenseDb.CsvBatchResult legacy=db.importCsvBatch("qa-contract-excluded",rows);
        eq(0,legacy.saved);eq(2,legacy.duplicates);
    }

    public void testImportContractCsvPendingCallerIsNeverCommitted(){
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CS PENDING,-1.00");
        SQLiteDatabase sql=db.getWritableDatabase();long revision=db.changes();CsvImportResult result;
        sql.beginTransaction();
        try{
            result=db.importCsvBatchDetailed("qa-contract-pending",rows);
            eq(1,result.created);ok(!result.committed);
            ok(result.completion==CaptureResult.Completion.PENDING_CALLER);ok(!result.dbChanged);
            eq(1,db.transactionCount());
        }finally{sql.endTransaction();}
        eq(0,db.transactionCount());eq(revision,db.changes());ok(sql.isOpen()&&!sql.inTransaction());
    }

    public void testImportContractCsvIgnoredMerchantRuleStaysBypassed(){
        db.putMerchantRule("QA CS IGNORED","تسوق",true);
        List<CsvImport.Row> rows=CsvImport.parse("Date,Merchant,Amount\n2026-10-15,QA CS IGNORED,-7.00");
        CsvImportResult result=db.importCsvBatchDetailed("qa-contract-ignored",rows);
        eq(1,result.created);eq(0,result.excluded);eq(0,result.alreadyPresent);
        eq(1,scalar("SELECT COUNT(*) FROM transactions WHERE merchant='QA CS IGNORED'"));
    }

    // ---- P2-A review corrections: regressions for the reported defects (R1/R2), written against the
    // existing API so they can be shown failing on the reviewed head before the correction lands. ----
    public void testImportContractSecondCycleServiceIsSavedForCycleReviewNotRefused(){
        long t=at(LocalDate.now(),10);LocalDate previous=LocalDate.now().minusMonths(1);
        ok(db.addRecurring("Netflix",5000,"EGP",LocalDate.now()));
        db.getWritableDatabase().execSQL("UPDATE recurring SET last_paid_date=?,last_paid_due=?,paid_count=1",
                new Object[]{previous.toString(),previous.toString()});
        long beforePaid=scalar("SELECT paid_count FROM recurring");
        SmsPreview row=SmsPreview.parse("Billing","Netflix bill paid successfully",t);
        ok(row!=null&&!row.isTransaction());
        ImportResult result=detailed(row);
        invariant(result);
        eq(beforePaid,scalar("SELECT paid_count FROM recurring"),"history must not advance a schedule without a reviewed cycle");
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        eq(1,result.linksChanged,"the saved provider review must be reported as a stored effect");
        eq(1,result.noLedgerChange);
        eq(0,result.refused,"an accepted service payment is not a refusal");
    }

    public void testImportContractPairSelectedTransferReportsOneSurvivingRow(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        db.addPaymentSource("BankMisr","حساب","2222");
        SmsPreview out=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        SmsPreview in=SmsPreview.parse("BankMisr","تم اضافة مبلغ 100 EGP لحساب رقم 2222 عن طريق التحويل اللحظي",t+60000);
        ok(out!=null&&in!=null);
        ImportResult result=detailed(out,in);
        invariant(result);
        eq(2,result.created);eq(1,result.repair.ownTransfers);eq(1,result.repair.archivedRows);
        eq(1,result.survivingCreated,"only the leg that still exists may count as surviving");
        long archived=scalar("SELECT original_id FROM transaction_archive ORDER BY id DESC LIMIT 1");
        long fee=scalar("SELECT id FROM transactions WHERE fee_parent_id IS NOT NULL");
        eq(archived,fee,"the fixture must exercise the reused rowid: the fee takes the archived leg's id");
        ImportResult.Row incoming=result.rows.get(1);
        ok(!incoming.surviving,"a leg repair archived is not a surviving selected row");
        ok(incoming.transactionId==null||incoming.transactionId.longValue()!=fee,
                "the reused fee rowid must never be reported as the selected leg");
    }

    // Historical provider evidence now waits for an explicit cycle; it is still observed truthfully and
    // restored by caller rollback. An unknown provider is preserved for review rather than silently dropped.

    public void testImportContractUnmatchedServiceIsSavedForReviewWithoutInventingAPlan(){
        long t=at(LocalDate.now(),10);
        SmsPreview row=SmsPreview.parse("Billing","QA UNKNOWN SERVICE bill paid successfully",t);ok(row!=null);
        ImportResult result=detailed(row);
        invariant(result);
        eq(0,scalar("SELECT COUNT(*) FROM recurring"));
        eq(0,result.refused);eq(1,result.noLedgerChange);eq(1,result.linksChanged);
        ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);eq(1,db.pendingCommitmentConfirmations().size());
    }

    public void testImportContractSecondCycleServicePaymentRollsBackWithItsCaller(){
        long t=at(LocalDate.now(),10);LocalDate previous=LocalDate.now().minusMonths(1);
        ok(db.addRecurring("Netflix",5000,"EGP",LocalDate.now()));
        db.getWritableDatabase().execSQL("UPDATE recurring SET last_paid_date=?,last_paid_due=?,paid_count=1",
                new Object[]{previous.toString(),previous.toString()});
        SmsPreview row=SmsPreview.parse("Billing","Netflix bill paid successfully",t);ok(row!=null);
        SQLiteDatabase sql=db.getWritableDatabase();ImportResult result;
        sql.beginTransaction();
        try{
            result=detailed(row);
            invariant(result);
            eq(1,result.linksChanged);eq(1,result.noLedgerChange);
            ok(!result.committed&&result.completion==CaptureResult.Completion.PENDING_CALLER);
            ok(!result.dbChanged,"a caller-owned transaction is not a committed change");
            eq(1,scalar("SELECT paid_count FROM recurring"),"the historical baseline does not change even inside the caller");
            eq(1,db.pendingCommitmentConfirmations().size());
        }finally{sql.endTransaction();}
        invariant(result);
        eq(1,scalar("SELECT paid_count FROM recurring"));
        eq(previous.toString(),text("SELECT last_paid_date FROM recurring"));
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
        eq(0,db.pendingCommitmentConfirmations().size());
    }

    // ---- R2: identity after repair is the stored key, never a row number a new row can inherit ----

    public void testImportContractArchivedLegKeepsItsCreationAndNamesItsMergeTarget(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        db.addPaymentSource("BankMisr","حساب","2222");
        SmsPreview out=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        SmsPreview in=SmsPreview.parse("BankMisr","تم اضافة مبلغ 100 EGP لحساب رقم 2222 عن طريق التحويل اللحظي",t+60000);
        ok(out!=null&&in!=null);
        ImportResult result=detailed(out,in);
        invariant(result);
        ImportResult.Row outgoing=result.rows.get(0),incoming=result.rows.get(1);
        ok(outgoing.surviving);ok(!incoming.surviving);
        eq(1,result.survivingCreated);eq(1,result.archivedSelectedRows());
        eq(incoming.initialTransactionId.longValue(),scalar("SELECT original_id FROM transaction_archive ORDER BY id DESC LIMIT 1"));
        ok(incoming.identityKey!=null&&!incoming.identityKey.equals(text("SELECT unique_key FROM transactions WHERE id="+incoming.initialTransactionId)),
                "the row that now holds the archived leg's id is the fee, not the leg");
        ok(incoming.transactionId!=null&&incoming.transactionId.longValue()==outgoing.transactionId.longValue(),
                "the archived leg names the row it was merged into");
    }

    public void testImportContractPairSelectedTransferRollsBackWithItsCaller(){
        long t=at(LocalDate.now(),10);
        db.addPaymentSource("BankMisr","حساب","1111");
        db.addPaymentSource("BankMisr","حساب","2222");
        SmsPreview out=SmsPreview.parse("BankMisr","تم تحويل مبلغ 100.50 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222",t);
        SmsPreview in=SmsPreview.parse("BankMisr","تم اضافة مبلغ 100 EGP لحساب رقم 2222 عن طريق التحويل اللحظي",t+60000);
        ok(out!=null&&in!=null);
        SQLiteDatabase sql=db.getWritableDatabase();long rows=db.transactionCount();ImportResult result;
        sql.beginTransaction();
        try{
            result=detailed(out,in);
            invariant(result);
            eq(2,result.created);eq(1,result.survivingCreated);eq(1,result.archivedSelectedRows());
            eq(rows+2,db.transactionCount(),"the kept leg and the fee are live while the caller is open");
            ok(!result.committed);
        }finally{sql.endTransaction();}
        invariant(result);
        eq(rows,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
        eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
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
