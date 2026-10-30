package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/** Independent verification fixtures. Live fixture mode is explicitly emulator-only. */
final class FeatureAuditTest {
    private final Instrumentation runner;
    private ExpenseDb db;
    private Context context;
    private File file;
    FeatureAuditTest(Instrumentation runner) { this.runner = runner; }
    private long at(LocalDate d) { return d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    private void eq(long expected, long actual) { if (expected != actual) throw new AssertionError("expected " + expected + ", actual " + actual); }
    private void ok(boolean value) { if (!value) throw new AssertionError("expected true"); }
    private void setup() {
        Context target = runner.getTargetContext(); file = new File(target.getCacheDir(), "feature-audit-isolated.db"); SQLiteDatabase.deleteDatabase(file);
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getDatabasePath(String name) { return file; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory f) { return SQLiteDatabase.openOrCreateDatabase(file, f); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler h) { return SQLiteDatabase.openOrCreateDatabase(file.getPath(), f, h); }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return target.getSharedPreferences("feature-audit-" + name, mode); }
        };
        db = new ExpenseDb(context); db.getWritableDatabase();
        context.getSharedPreferences(DailySummary.SETTINGS,0).edit().clear().commit();
        context.getSharedPreferences(DailyPointsStore.SETTINGS,0).edit().clear().commit();
    }
    void run(String mode) {
        if (!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE)) throw new SecurityException("Emulator only");
        Bundle result = new Bundle(); StringBuilder report = new StringBuilder(); int passed = 0, failed = 0;
        if ("recent_benchmark".equals(mode)) {
            try { setup(); report.append(benchmarkRecentTransactions()); passed++; }
            catch (Throwable e) { failed++; report.append("recent_benchmark: FAILED ").append(e).append('\n'); }
            finally { if (db != null) db.close(); if (file != null) SQLiteDatabase.deleteDatabase(file); }
            result.putString("stream", report + "Passed: " + passed + "; failed: " + failed + "\n");
            runner.finish(failed == 0 ? -1 : 0, result);
            return;
        }
        if ("recent_live_seed".equals(mode) || "recent_home_render".equals(mode)) {
            try {
                if("recent_live_seed".equals(mode)) {
                    try(ExpenseDb live=new ExpenseDb(runner.getTargetContext())) {
                        if(live.transactionCount()!=0)throw new IllegalStateException("Live benchmark requires an empty disposable emulator app");
                        seedRecentRows(live);report.append("Seeded 3000 synthetic live rows\n");
                    }
                } else report.append(benchmarkHomeRender());
                passed++;
            } catch(Throwable e) { failed++;report.append(mode).append(": FAILED ").append(e).append('\n'); }
            result.putString("stream",report+"Passed: "+passed+"; failed: "+failed+"\n");
            runner.finish(failed==0?-1:0,result);
            return;
        }
        if ("seed".equals(mode)) {
            try { seed(); result.putString("stream", "Synthetic scenario seeded; original emulator rows retained\n"); runner.finish(-1, result); }
            catch (Throwable e) { result.putString("stream", e.toString()); runner.finish(0, result); }
            return;
        }
        for (java.lang.reflect.Method m : getClass().getDeclaredMethods()) {
            if (!m.getName().startsWith("test")) continue;
            try { setup(); m.invoke(this); passed++; report.append(m.getName()).append(": passed\n"); }
            catch (Throwable e) { failed++; report.append(m.getName()).append(": FAILED ").append(e.getCause() == null ? e : e.getCause()).append('\n'); }
            finally { if (db != null) db.close(); if (file != null) SQLiteDatabase.deleteDatabase(file); }
        }
        result.putString("stream", report + "Passed: " + passed + "; failed: " + failed + "\n"); runner.finish(failed == 0 ? -1 : 0, result);
    }
    private long paymentPlan(LocalDate due){
        return db.putCommitment(null,"QA provider",10000,"EGP",due,"monthly","installment",12,2,1,"qa provider",null);
    }
    public void testMatchedAutomaticPaymentClassificationPreservesEvidenceAndUserChoices(){
        LocalDate due=LocalDate.now();long plan=paymentPlan(due);long t=at(due);
        String body="Card purchase EGP 100 at QA provider";
        ok(db.addSms("BANK",body,t));ExpenseDb.Transaction tx=db.lastCaptured();
        eq("أقساط",db.transactionById(tx.id).category);eq(3,db.recurringById(plan).paidCount);
        eq(body,db.sourceEvidence(tx.id).get(0).body);eq(1,db.transactionCount());
        DailySummary.save(context,new DailyAllowance.Plan("EGP",20000,due,0));
        eq(0,DailySummary.load(context,db).currencies.get("EGP").expense);
        eq(10000,db.monthlyTotalForKind(due,"EGP","expense"));
        ok(db.undoCommitmentPayment(db.commitmentPayments(plan).get(0).id));
        eq("أقساط",db.transactionById(tx.id).category);eq(2,db.recurringById(plan).paidCount);
        ok(db.addManual(t+1000,10000,"EGP","QA provider",MoneyFlow.UNCATEGORIZED));
        eq(MoneyFlow.UNCATEGORIZED,db.latestInserted().category);
        db.removeRecurring(plan);long reviewed=paymentPlan(due);
        ok(db.addSms("BANK","Card purchase EGP 60 at QA provider",t+2000));long partial=db.lastCaptured().id;
        db.updateTransaction(partial,6000,"EGP","QA provider","مطاعم",null);
        db.linkCommitmentPayment(reviewed,due,partial,6000);
        eq("مطاعم",db.transactionById(partial).category);
    }
    /** Owner, 29 Oct 2026: one transfer pays several installments; the payment's own amount caps what it can cover. */
    public void testOnePaymentCoversSeveralInstallments(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due),t=at(due);
        ok(db.addManual(t,25000,"EGP","QA OTHER NAME","أقساط"));long tx=db.latestInserted().id;
        eq(2,db.recurringById(plan).paidCount);
        CommitmentPayments.AllocationPreview preview=db.commitmentPaymentPreview(plan,due,tx);
        eq(10000,preview.max);eq(25000,preview.available);
        java.util.List<CommitmentPayments.Cycle> open=db.openCommitmentCycles(plan,due,3);eq(3,open.size());ok(open.get(0).due.equals(due));
        eq(2,db.linkCommitmentPayments(preview,java.util.Arrays.asList(open.get(1).due,open.get(0).due)));
        eq(4,db.recurringById(plan).paidCount);eq(20000,db.commitmentAllocated(tx));
        eq(1,db.transactionCount());
        // 5,000 is still free: the payment stays a candidate and pays part of the next cycle, never more than it has.
        LocalDate third=db.recurringById(plan).nextDue;
        ok(db.commitmentPaymentCandidates(due).stream().anyMatch(c->c.id==tx));
        CommitmentPayments.AllocationPreview rest=db.commitmentPaymentPreview(plan,third,tx);
        eq(5000,rest.max);eq(5000,rest.available);
        try{db.linkCommitmentPayment(rest,6000);throw new AssertionError("Payment over-allocated");}catch(IllegalArgumentException expected){}
        db.linkCommitmentPayment(rest,5000);eq(25000,db.commitmentAllocated(tx));
        ok(db.commitmentPaymentCandidates(due).stream().noneMatch(c->c.id==tx));
        eq(5000,db.commitmentCycles(plan).get(2).remaining());eq(4,db.recurringById(plan).paidCount);
        // Undoing one cycle frees exactly its part.
        ok(db.undoCommitmentPayment(db.commitmentPayments(plan).get(0).id));eq(20000,db.commitmentAllocated(tx));
        eq(10000,CommitmentPayments.split(25000,new long[]{10000,10000,10000})[1]);
        eq(5000,CommitmentPayments.split(25000,new long[]{10000,10000,10000})[2]);
        eq(0,CommitmentPayments.split(5000,new long[]{10000,10000})[1]);
    }
    public void testReviewedAutomaticTransferClassificationAndFailureAreAtomic(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);
        ok(db.addHistoricalSms(SmsPreview.parse("BANK","Card purchase EGP 60 at QA provider",at(due))));
        long id=db.lastCaptured().id;SQLiteDatabase sql=db.getWritableDatabase();
        sql.execSQL("UPDATE transactions SET category=? WHERE id=?",new Object[]{MoneyFlow.OPEN_TRANSFER_CATEGORY,id});
        long revision=db.changes();
        sql.execSQL("CREATE TRIGGER reject_payment_category BEFORE UPDATE OF category ON transactions BEGIN SELECT RAISE(ABORT,'QA category fault'); END");
        try{db.linkCommitmentPayment(plan,due,id,6000);throw new AssertionError("Classification fault swallowed");}
        catch(android.database.sqlite.SQLiteException expected){}
        eq(revision,db.changes());eq(0,db.commitmentCycles(plan).size());eq(0,db.commitmentPayments(plan).size());
        eq(MoneyFlow.OPEN_TRANSFER_CATEGORY,db.transactionById(id).category);
        sql.execSQL("DROP TRIGGER reject_payment_category");db.linkCommitmentPayment(plan,due,id,6000);
        eq("أقساط",db.transactionById(id).category);eq(4000,db.commitmentCycles(plan).get(0).remaining());eq(1,db.transactionCount());
    }
    public void testAutomaticCommitmentAllocationFollowsLiveIdentityAndUndo(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due),t=at(due);
        ok(db.addManual(t,10000,"EGP","QA provider","أقساط"));long tx=db.latestInserted().id;
        eq(1,db.transactionCount());eq(3,db.recurringById(plan).paidCount);
        ok(db.recurringById(plan).nextDue.equals(LocalDate.of(2026,11,30)));
        eq(2,db.storedRecurringById(plan).paidCount); // Declared historical baseline remains unchanged.
        long link=db.commitmentPayments(plan).get(0).id;
        db.updateTransaction(tx,10000,"USD","QA provider","أقساط",null);
        eq(2,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(due));
        db.updateTransaction(tx,10000,"EGP","QA provider","أقساط",null);
        eq(3,db.recurringById(plan).paidCount);
        ok(db.undoCommitmentPayment(link));ok(!db.undoCommitmentPayment(link));
        eq(2,db.recurringById(plan).paidCount);eq(1,db.transactionCount());
        ok(!db.commitmentPayments(plan).get(0).active);
    }
    public void testPartialCommitmentPaymentsRefundAndAmountEditRecompute(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due),t=at(due);
        ok(db.addManual(t,6000,"EGP","QA provider","أقساط"));long first=db.latestInserted().id;
        eq(2,db.recurringById(plan).paidCount); // No amount-only partial guess.
        db.linkCommitmentPayment(plan,due,first,6000);
        eq(4000,db.commitmentCycles(plan).get(0).remaining());
        ok(db.recurringById(plan).lastPaidDate==null); // A partial transfer is not a completed installment date.
        ok(db.addManual(t+1000,4000,"EGP","QA provider","أقساط"));long second=db.latestInserted().id;
        db.linkCommitmentPayment(plan,due,second,4000);
        eq(3,db.recurringById(plan).paidCount);eq(2,db.transactionCount());
        eq(due.toString(),db.recurringById(plan).lastPaidDate);
        db.updateTransaction(first,5000,"EGP","QA provider","أقساط",null);
        eq(2,db.recurringById(plan).paidCount);eq(1000,db.commitmentCycles(plan).get(0).remaining());
        ok(db.addManualEntry(t+2000,2000,"EGP","QA refund","دخل",null,"income",null,null));
        db.linkReimbursement(db.latestInserted().id,first);
        eq(3000,db.commitmentCycles(plan).get(0).remaining());
        db.unlinkReimbursement(first);eq(1000,db.commitmentCycles(plan).get(0).remaining());
    }
    public void testCommitmentDeleteAndCancellationCannotFollowAReusedRowId(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due),t=at(due);
        ok(db.addManual(t,10000,"EGP","QA provider","أقساط"));long id=db.latestInserted().id;
        eq(3,db.recurringById(plan).paidCount);
        db.archiveEntry(id,"bank confirmed cancellation",true);
        eq(2,db.recurringById(plan).paidCount);eq(0,db.transactionCount());
        ok(db.addManual(t+1000,10000,"EGP","QA unrelated","أخرى"));
        eq(id,db.latestInserted().id);eq(2,db.recurringById(plan).paidCount);
        ok(!db.commitmentPayments(plan).get(0).valid);
    }
    public void testCommitmentMatchingRequiresUniqueNameAmountCurrencyAccountAndCycle(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due),t=at(due);
        long other=db.putCommitment(null,"QA other",10000,"EGP",due,"monthly","bill",null,0,1,"qa provider",null);
        ok(db.addManual(t,10000,"EGP","QA provider","أقساط"));
        eq(2,db.recurringById(plan).paidCount);eq(0,db.recurringById(other).paidCount);
        db.finishRecurring(other,false);
        ok(db.addManual(t+1000,10000,"USD","QA provider","أقساط"));
        ok(db.addManual(t+2000,10001,"EGP","QA provider","أقساط"));
        ok(db.addManual(at(due.minusDays(13)),10000,"EGP","QA provider","أقساط"));
        eq(2,db.recurringById(plan).paidCount);
        long account=db.addPaymentSource("QA account","حساب","1234");
        db.getWritableDatabase().execSQL("UPDATE recurring SET payment_source_id=? WHERE id=?",new Object[]{account,plan});
        ok(db.addManual(t+3000,10000,"EGP","QA provider","أقساط"));eq(2,db.recurringById(plan).paidCount);
        ok(db.addManual(t+4000,10000,"EGP","QA provider","أقساط",account));eq(3,db.recurringById(plan).paidCount);
        ok(db.addManual(t+5000,10000,"EGP","QA provider","أقساط",account));eq(3,db.recurringById(plan).paidCount);
        eq(1,db.commitmentPayments(plan).size());
    }
    public void testOutOfOrderCommitmentPaymentLeavesEarlierCycleDue(){
        LocalDate october=LocalDate.of(2026,10,31),november=LocalDate.of(2026,11,30);long plan=paymentPlan(october);
        ok(db.addManual(at(november),10000,"EGP","QA provider","أقساط"));
        eq(3,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(october));
        ok(db.addManual(at(october),10000,"EGP","QA provider","أقساط"));
        eq(4,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(LocalDate.of(2026,12,31)));
    }
    public void testCommitmentAllocationAndScheduleRollbackWithFinancialWriter(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);long revision=db.changes();
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{ok(db.addManual(at(due),10000,"EGP","QA provider","أقساط"));eq(3,db.recurringById(plan).paidCount);}
        finally{sql.endTransaction();}
        eq(revision,db.changes());eq(0,db.transactionCount());eq(0,db.commitmentCycles(plan).size());
        sql.execSQL("CREATE TRIGGER reject_commitment_payment BEFORE INSERT ON commitment_payments BEGIN SELECT RAISE(ABORT,'QA settlement fault'); END");
        try{db.addManual(at(due),10000,"EGP","QA provider","أقساط");throw new AssertionError("Settlement fault swallowed");}
        catch(android.database.sqlite.SQLiteException expected){}
        eq(revision,db.changes());eq(0,db.transactionCount());eq(2,db.recurringById(plan).paidCount);
        ok(db.recurringById(plan).nextDue.equals(due));
    }
    public void testHistoricalCommitmentPaymentIsStoredWithoutAdvancingSchedule(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);
        SmsPreview row=SmsPreview.parse("BANK","Card purchase EGP 100 at QA provider",at(due));ok(row!=null);
        ok(db.addHistoricalSms(row));eq(1,db.transactionCount());eq(2,db.recurringById(plan).paidCount);
        eq(0,db.commitmentCycles(plan).size());
        db.linkCommitmentPayment(plan,due,db.lastCaptured().id,10000);eq(3,db.recurringById(plan).paidCount);
    }
    public void testAmountlessConfirmationIsUniqueAndActualPaymentReplacesIt(){
        LocalDate due=LocalDate.of(2026,10,31);long t=at(due);
        long a=db.putCommitment(null,"Netflix",10000,"EGP",due,"monthly","subscription",null,0,1,"netflix",null);
        long b=db.putCommitment(null,"Netflix dollars",10000,"USD",due,"monthly","subscription",null,0,1,"netflix",null);
        String body="Netflix bill paid successfully";
        CaptureResult ambiguous=db.captureSms("Billing",body,t);ok(ambiguous.accepted&&ambiguous.needsReview);
        eq(0,db.recurringById(a).paidCount);eq(0,db.recurringById(b).paidCount);eq(1,db.pendingCommitmentConfirmations().size());
        db.finishRecurring(b,false);
        ok(db.resolveCommitmentConfirmation(db.pendingCommitmentConfirmations().get(0).id,a,due));
        eq(1,db.recurringById(a).paidCount);eq(0,db.transactionCount());
        ok(db.addSms("BANK","Card purchase EGP 100 at Netflix",t+2000));long tx=db.lastCaptured().id;
        eq(1,db.recurringById(a).paidCount);eq(1,db.transactionCount());eq(2,db.commitmentPayments(a).size());
        db.archiveEntry(tx,"bank confirmed cancellation",true);eq(0,db.recurringById(a).paidCount);
        ok(!db.addSms("Billing",body,t+1000));eq(0,db.recurringById(a).paidCount);
    }
    public void testDeletedCommitmentIdentityDoesNotLeakIntoAReplacement(){
        LocalDate due=LocalDate.of(2026,10,31);long first=paymentPlan(due);
        ok(db.addManual(at(due),10000,"EGP","QA provider","أقساط"));eq(3,db.recurringById(first).paidCount);
        db.removeRecurring(first);long replacement=paymentPlan(due);eq(first,replacement);
        eq(2,db.recurringById(replacement).paidCount);eq(0,db.commitmentCycles(replacement).size());
        eq(1,db.transactionCount());db.clearAllData();eq(0,countRows("commitment_payments"));eq(0,countRows("commitment_cycles"));
    }
    public void testCommitmentPreviewRejectsReusedIdentitiesAndChangedRowsAtomically(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);
        ok(db.addManual(at(due),6000,"EGP","QA provider","أقساط"));long id=db.latestInserted().id;
        CommitmentPayments.AllocationPreview preview=db.commitmentPaymentPreview(plan,due,id);
        db.updateTransaction(id,5000,"EGP","QA provider","أقساط",null);long revision=db.changes();
        try{db.linkCommitmentPayment(preview,5000);throw new AssertionError("Stale amount accepted");}catch(IllegalArgumentException expected){}
        eq(revision,db.changes());eq(0,db.commitmentCycles(plan).size());
        preview=db.commitmentPaymentPreview(plan,due,id);db.deleteTransaction(id);
        ok(db.addManual(at(due),5000,"EGP","QA provider","أقساط"));eq(id,db.latestInserted().id);
        revision=db.changes();try{db.linkCommitmentPayment(preview,5000);throw new AssertionError("Reused transaction id accepted");}catch(IllegalArgumentException expected){}
        eq(revision,db.changes());
        preview=db.commitmentPaymentPreview(plan,due,id);db.removeRecurring(plan);eq(plan,paymentPlan(due));
        revision=db.changes();try{db.linkCommitmentPayment(preview,5000);throw new AssertionError("Reused commitment id accepted");}catch(IllegalArgumentException expected){}
        eq(revision,db.changes());eq(0,db.commitmentCycles(plan).size());
    }
    public void testAutomaticCommitmentCompletionRefundReopensAndReadsDoNotWrite(){
        LocalDate due=LocalDate.of(2026,10,31);
        long plan=db.putCommitment(null,"QA final",10000,"EGP",due,"monthly","installment",3,2,1,"qa final",null);
        ok(db.addManual(at(due),10000,"EGP","QA final","أقساط"));long paid=db.latestInserted().id;
        ok(db.recurringById(plan).isDone());String key=db.commitmentIdentity(plan);long revision=db.changes();
        for(int i=0;i<3;i++){db.recurring();db.commitmentCycles(plan);db.commitmentPayments(plan);}eq(revision,db.changes());
        ok(!db.confirmCommitmentCycle(plan,due,key,due.plusMonths(1)));eq(revision,db.changes());
        ok(db.addManualEntry(at(due)+1000,10000,"EGP","QA refund","دخل",null,"income",null,null));
        long refund=db.latestInserted().id;db.linkReimbursement(refund,paid);
        ok(!db.recurringById(plan).isDone());eq(2,db.recurringById(plan).paidCount);eq(10000,db.commitmentCycles(plan).get(0).remaining());
        db.unlinkReimbursement(paid);ok(db.recurringById(plan).isDone());eq(3,db.recurringById(plan).paidCount);
    }
    public void testV24CommitmentUpgradeKeepsLegacyStateWithoutInventingPayments(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);
        ok(db.addSms("BANK","Card purchase EGP 50 at QA old shop",at(due)));long tx=db.lastCaptured().id;
        String identity=db.rowIdentityKey(tx),body=db.sourceEvidence(tx).get(0).body;
        SQLiteDatabase sql=db.getWritableDatabase();
        sql.execSQL("DROP TABLE commitment_payments");sql.execSQL("DROP TABLE commitment_cycles");
        sql.execSQL("ALTER TABLE recurring DROP COLUMN settlement_key");sql.setVersion(24);db.close();db=new ExpenseDb(context);
        eq(ExpenseDb.SCHEMA_VERSION,db.getWritableDatabase().getVersion());eq(2,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(due));
        eq("legacy:"+plan,db.commitmentIdentity(plan));eq(identity,db.rowIdentityKey(tx));eq(body,db.sourceEvidence(tx).get(0).body);
        eq(5000,db.transactionById(tx).amountMinor);eq(0,countRows("commitment_cycles"));eq(0,countRows("commitment_payments"));
    }
    public void testPendingProviderEvidenceIsEncryptedAndHistoricalConfirmationNeedsACycle(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);
        String body="تم دفع خدمة QA provider بنجاح";SmsPreview row=SmsPreview.parse("InstaPay",body,at(due));ok(row!=null);
        ImportResult result=new ImportBatch(db).executeDetailed(Collections.singletonList(row));
        eq(1,result.noLedgerChange);eq(0,result.refused);ok(result.rows.get(0).reason==ImportResult.Reason.REVIEW_REQUIRED);
        eq(2,db.recurringById(plan).paidCount);eq(0,db.transactionCount());
        CommitmentPayments.ProviderNotice notice=db.pendingCommitmentConfirmations().get(0);eq(body,notice.body);
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT amount_minor,encrypted_body FROM commitment_payments",null)){
            ok(c.moveToFirst());eq(0,c.getLong(0));ok(!body.equals(c.getString(1)));ok(c.getString(1).contains(":"));
        }
        long revision=db.changes();
        try{db.resolveCommitmentConfirmation(notice.id,plan,due,"stale-key");throw new AssertionError("Stale commitment accepted");}catch(IllegalArgumentException expected){}
        eq(revision,db.changes());eq(1,db.pendingCommitmentConfirmations().size());
        ok(db.resolveCommitmentConfirmation(notice.id,plan,due,db.commitmentIdentity(plan)));
        eq(3,db.recurringById(plan).paidCount);eq(0,db.pendingCommitmentConfirmations().size());
        eq(0,db.transactionCount());
    }
    public void testCommitmentAliasCannotMatchInsideAnotherPayeesName(){
        LocalDate due=LocalDate.of(2026,10,31);
        long plan=db.putCommitment(null,"TRU",10000,"EGP",due,"monthly","installment",3,0,1,"tru|ترو",null);
        ok(db.addManual(at(due),10000,"EGP","Truecaller","اشتراكات"));eq(0,db.recurringById(plan).paidCount);
        ok(db.addManual(at(due)+1000,10000,"EGP","TRU installment","أقساط"));eq(1,db.recurringById(plan).paidCount);
    }
    public void testStaleCommitmentReminderCannotPayTheNextCycleOrAReplacement(){
        LocalDate due=LocalDate.of(2026,10,31);long plan=paymentPlan(due);String key=db.commitmentIdentity(plan);
        ok(ReminderReceiver.markPaid(context,plan,due,due.toString(),key));eq(3,db.recurringById(plan).paidCount);
        long revision=db.changes();ok(!ReminderReceiver.markPaid(context,plan,due.plusMonths(1),due.toString(),key));
        eq(revision,db.changes());eq(3,db.recurringById(plan).paidCount);eq(0,db.transactionCount());
        db.removeRecurring(plan);eq(plan,paymentPlan(due));revision=db.changes();
        ok(!ReminderReceiver.markPaid(context,plan,due,due.toString(),key));eq(revision,db.changes());eq(2,db.recurringById(plan).paidCount);
        ok(!ReminderReceiver.markPaid(context,plan,due,null,null));eq(0,db.transactionCount());
    }
    public void testProviderAfterTheBankPaymentIsOnlyEvidenceAndCannotOverrideCancellation(){
        LocalDate due=LocalDate.of(2026,10,31);long t=at(due);
        long plan=db.putCommitment(null,"Netflix",10000,"EGP",due,"monthly","subscription",null,0,1,"netflix",null);
        ok(db.addSms("BANK","Card purchase EGP 100 at Netflix",t));long tx=db.lastCaptured().id;
        CaptureResult proof=db.captureSms("Billing","Netflix bill paid successfully",t+1000);
        ok(proof.accepted&&!proof.needsReview);eq(1,db.recurringById(plan).paidCount);eq(1,db.transactionCount());
        eq(2,db.commitmentPayments(plan).size());eq(0,db.pendingCommitmentConfirmations().size());
        db.archiveEntry(tx,"bank confirmed cancellation",true);eq(0,db.recurringById(plan).paidCount);
        CaptureResult conflict=db.captureSms("Billing","Netflix bill paid successfully",t+180000);
        ok(conflict.accepted&&conflict.needsReview);eq(0,db.recurringById(plan).paidCount);eq(1,db.pendingCommitmentConfirmations().size());
    }
    public void testProviderPaymentForALaterCycleDoesNotSkipTheUnpaidCycle(){
        LocalDate october=LocalDate.of(2026,10,31),november=LocalDate.of(2026,11,30);long plan=paymentPlan(october);
        ok(db.addSms("InstaPay","تم دفع خدمة QA provider بنجاح",at(november)));
        eq(3,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(october));eq(2,db.commitmentCycles(plan).size());eq(0,db.transactionCount());
    }
    public void testCommitmentEditsPreserveBaselineAnchorsAndStartedCyclePrices(){
        LocalDate october=LocalDate.of(2026,10,31),november=LocalDate.of(2026,11,30);long plan=paymentPlan(october);
        ok(db.addManual(at(october),10000,"EGP","QA provider","أقساط"));
        ExpenseDb.Recurring view=db.recurringById(plan);
        db.putCommitment(plan,"QA provider",11000,"EGP",view.nextDue,"monthly","installment",12,view.paidCount,2,"qa provider",null);
        eq(2,db.storedRecurringById(plan).paidCount);eq(31,db.recurringById(plan).dueDay);eq(10000,db.commitmentCycles(plan).get(0).expected);
        ok(db.addManual(at(november),11000,"EGP","QA provider","أقساط"));eq(4,db.recurringById(plan).paidCount);
        eq(11000,db.commitmentCycles(plan).get(1).expected);ok(db.recurringById(plan).nextDue.equals(LocalDate.of(2026,12,31)));
        LocalDate newDue=LocalDate.of(2026,12,15);
        db.putCommitment(plan,"QA provider",12000,"USD",newDue,"monthly","installment",12,4,2,"qa provider",null);
        eq(4,db.recurringById(plan).paidCount);ok(db.recurringById(plan).nextDue.equals(newDue));eq("USD",db.recurringById(plan).currency);
        long revision=db.changes();
        try{db.putCommitment(plan,"QA provider",12000,"USD",newDue,"monthly","installment",3,4,2,"qa provider",null);throw new AssertionError("Historical cycles truncated");}
        catch(IllegalArgumentException expected){}eq(revision,db.changes());eq(2,db.transactionCount());
    }
    /** The commitment editor's save, as MainActivity runs it: decision and plan terms from CommitmentEdit. */
    private long editorSave(long plan,String merchant,long typed,String currency,boolean onlyThis){
        ExpenseDb.Recurring view=db.recurringById(plan),stored=db.storedRecurringById(plan);
        int action=CommitmentEdit.action(true,!view.isDone(),view.amountMinor,view.currency,typed,currency);
        CommitmentEdit.Price terms=CommitmentEdit.planTerms(action,onlyThis,new CommitmentEdit.Price(stored.amountMinor,stored.currency),new CommitmentEdit.Price(typed,currency));
        if(action!=CommitmentEdit.ASK_SCOPE)return db.putCommitment(plan,merchant,terms.amount,terms.currency,view.nextDue,"monthly","installment",12,view.paidCount,1,"qa provider",null);
        return db.putCommitmentWithCycleAmount(plan,merchant,terms.amount,terms.currency,view.nextDue,"monthly","installment",12,view.paidCount,1,"qa provider",null,typed,currency,!onlyThis);
    }
    /** Owner, 30 Oct 2026: a card installment's amount drops each month; the edit must reach the due installment. */
    public void testEditedAmountReachesTheDueCycleOnlyOrForward(){
        LocalDate october=LocalDate.of(2026,10,31),november=LocalDate.of(2026,11,30);long plan=paymentPlan(october);
        // The due cycle is stored first at another price (as a partial payment or a provider notice would do).
        editorSave(plan,"QA provider",9000,"EGP",true);eq(9000,db.commitmentCycles(plan).get(0).expected);
        // A second edit of the same stored cycle used to be ignored; now it changes the amount shown and confirmed.
        editorSave(plan,"QA provider",4439,"EGP",true);
        eq(4439,db.recurringById(plan).amountMinor);eq(10000,db.storedRecurringById(plan).amountMinor);
        ok(db.confirmCommitmentDue(plan,october,db.commitmentIdentity(plan),october));
        ok(db.recurringById(plan).nextDue.equals(november));eq(10000,db.recurringById(plan).amountMinor);eq(3,db.recurringById(plan).paidCount);
        // Forward: the plan and every later cycle follow.
        editorSave(plan,"QA provider",4200,"EGP",false);
        eq(4200,db.recurringById(plan).amountMinor);eq(4200,db.storedRecurringById(plan).amountMinor);
        ok(db.confirmCommitmentDue(plan,november,db.commitmentIdentity(plan),november));eq(4200,db.recurringById(plan).amountMinor);
    }
    /** Review of PR #52: a second save with the price untouched keeps the plan's amount and the due installment's. */
    public void testSecondSaveAfterAnOnlyThisEditKeepsBothAmounts(){
        LocalDate october=LocalDate.of(2026,10,31);long plan=paymentPlan(october);
        editorSave(plan,"QA provider",4439,"EGP",true);
        ExpenseDb.Recurring view=db.recurringById(plan);CommitmentEdit.Price shown=CommitmentEdit.field(view.amountMinor,view.currency);
        eq(4439,shown.amount);eq("EGP",shown.currency);
        editorSave(plan,"QA provider renamed",shown.amount,shown.currency,false);
        eq(10000,db.storedRecurringById(plan).amountMinor);eq(4439,db.recurringById(plan).amountMinor);
        eq("QA provider renamed",db.recurringById(plan).merchant);
    }
    /** Review of PR #52: an open 4,439 EGP installment and a 100 USD plan; a name-only save keeps both, never 4,439 USD. */
    public void testNameOnlySaveKeepsAPlanInAnotherCurrency(){
        LocalDate october=LocalDate.of(2026,10,31);long plan=paymentPlan(october);
        editorSave(plan,"QA provider",4439,"EGP",true);
        // New installments in dollars: a new currency is a plan change, the open EGP installment keeps its price.
        editorSave(plan,"QA provider",10000,"USD",false);
        eq(10000,db.storedRecurringById(plan).amountMinor);eq("USD",db.storedRecurringById(plan).currency);
        ExpenseDb.Recurring view=db.recurringById(plan);eq(4439,view.amountMinor);eq("EGP",view.currency);
        CommitmentEdit.Price shown=CommitmentEdit.field(view.amountMinor,view.currency);eq(4439,shown.amount);eq("EGP",shown.currency);
        long id=editorSave(plan,"QA provider renamed",shown.amount,shown.currency,false);eq(plan,id);
        eq(10000,db.storedRecurringById(plan).amountMinor);eq("USD",db.storedRecurringById(plan).currency);
        eq(4439,db.recurringById(plan).amountMinor);eq("EGP",db.recurringById(plan).currency);
        eq("QA provider renamed",db.recurringById(plan).merchant);
        // The due cycle never takes an amount in another currency.
        try{db.putCommitmentWithCycleAmount(plan,"QA provider",10000,"USD",view.nextDue,"monthly","installment",12,view.paidCount,1,"qa provider",null,4000,"USD",false);throw new AssertionError("Cross-currency cycle amount accepted");}
        catch(IllegalArgumentException expected){}
        eq(4439,db.recurringById(plan).amountMinor);
    }
    public void testEditedAmountNeverDropsBelowWhatTheCycleReceived(){
        LocalDate october=LocalDate.of(2026,10,31);long plan=paymentPlan(october);
        ok(db.addManual(at(october),6000,"EGP","QA other payee","تحويلات"));long tx=db.latestInserted().id;
        db.linkCommitmentPayment(plan,october,tx,6000);eq(4000,db.commitmentCycles(plan).get(0).remaining());
        long revision=db.changes();
        try{editorSave(plan,"QA provider",5000,"EGP",true);throw new AssertionError("Amount below paid accepted");}
        catch(IllegalArgumentException expected){eq(CommitmentPayments.BELOW_PAID,expected.getMessage());}
        eq(revision,db.changes());eq(10000,db.commitmentCycles(plan).get(0).expected);
    }
    /** Owner, 30 Oct 2026: "علّمه مدفوع" settles the installment the user picks, not silently the oldest open one. */
    public void testManualConfirmationSettlesTheChosenCycle(){
        LocalDate october=LocalDate.of(2026,10,31),november=LocalDate.of(2026,11,30);long plan=paymentPlan(october);
        String key=db.commitmentIdentity(plan);
        ok(db.confirmCommitmentDue(plan,november,key,october.minusDays(1)));
        ok(db.recurringById(plan).nextDue.equals(october));eq(3,db.recurringById(plan).paidCount);
        ok(!db.confirmCommitmentDue(plan,november,key,october));
        ok(db.confirmCommitmentDue(plan,october,key,october));ok(db.recurringById(plan).nextDue.equals(LocalDate.of(2026,12,31)));
        ok(!db.confirmCommitmentDue(plan,october,"another key",october));eq(0,db.transactionCount());
    }
    /** Owner, 30 Oct 2026: card installments are paid by repaying the card; that repayment can settle the commitment. */
    public void testCardRepaymentCanSettleACardCommitmentWithoutBecomingAnInstallment(){
        LocalDate day=LocalDate.now().minusDays(1);long t=at(day);
        String dmy=day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        long account=db.addPaymentSource("QA Bank","حساب","5678");long card=db.addPaymentSource("QA Bank Card","بطاقة ائتمان","1234");
        long plan=db.putCommitment(null,"QA card installments",443900,"EGP",day,"monthly","installment",null,0,1,"qa card installments",card);
        ok(db.addSms("QA Bank","Transfer of EGP 4,439.00 from your account ****5678 to credit card ****1234 on "+dmy,t));
        ExpenseDb.Transaction repayment=db.lastCaptured();ok(repayment.destinationSourceId!=null&&repayment.destinationSourceId==card);
        eq(0,db.recurringById(plan).paidCount);
        ok(db.commitmentPaymentCandidates(day).stream().anyMatch(tx->tx.id==repayment.id));
        db.linkCommitmentPayment(db.commitmentPaymentPreview(plan,day,repayment.id),443900);
        eq(1,db.recurringById(plan).paidCount);eq(repayment.category,db.transactionById(repayment.id).category);
        ok(MoneyFlow.bucket(repayment.kind,repayment.category,true)==MoneyFlow.Bucket.INTERNAL);eq(1,db.transactionCount());
    }
    /** Owner, 30 Oct 2026: a deleted commitment came back with nothing; undo restores it with its settlements. */
    public void testDeletedCommitmentRestoresWithItsSettlements(){
        LocalDate october=LocalDate.of(2026,10,31);long plan=paymentPlan(october);
        ok(db.confirmCommitmentDue(plan,october,db.commitmentIdentity(plan),october));
        ExpenseDb.Recurring before=db.recurringById(plan);android.content.ContentValues row=db.commitmentSnapshot(plan);
        db.removeRecurring(plan);ok(db.recurringById(plan)==null);
        ok(db.restoreCommitment(row));ExpenseDb.Recurring back=db.recurringById(plan);
        ok(back!=null);eq(before.paidCount,back.paidCount);ok(before.nextDue.equals(back.nextDue));eq(1,db.commitmentPayments(plan).size());
        ok(!db.restoreCommitment(null));
    }
    public void testSupportFormEncodesOnlyEnvironmentMetadata() {
        android.net.Uri uri = GitHubSupport.bugUri(context);
        ok("https".equals(uri.getScheme()));
        ok("github.com".equals(uri.getHost()));
        ok("/MohamedGamal-Ahmed/Follow-Money-Support/issues/new".equals(uri.getPath()));
        ok(uri.getQueryParameterNames().equals(new java.util.HashSet<>(Arrays.asList("template", "app_version", "device", "android_version", "diagnostics"))));
        ok("bug_report.yml".equals(uri.getQueryParameter("template")));
        ok(GitHubSupport.details(context).equals(uri.getQueryParameter("diagnostics")));
        ok(GitHubSupport.version(context).equals(uri.getQueryParameter("app_version")));
        ok("feature_request.yml".equals(GitHubSupport.featureUri().getQueryParameter("template")));
    }

    public void testTransparentLockWidgetPreservesDailyAmountsCurrencyAndPrivacy() {
        android.content.SharedPreferences settings=context.getSharedPreferences("widget_settings",0);
        settings.edit().clear().putString("currency","EGP").commit();
        try {
            LocalDate today=LocalDate.now();
            DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,today,0));
            DailySummary.save(context,new DailyAllowance.Plan("USD",10000,today,0));
            db.addManual(at(today),1500,"EGP","QA coffee","مقاهي");
            db.addManual(at(today),1250,"USD","QA USD coffee","مقاهي");
            db.addManualEntry(at(today)+1000,50000,"EGP","QA incoming","دخل",null,"income",null,null);
            DailySummary snapshot=DailySummary.load(context,db);
            android.view.View view=applyLockWidget(LockSummaryWidget.render(context,snapshot));
            ok("235.00".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            ok(((android.widget.TextView)view.findViewById(R.id.lock_caption)).getText().toString().contains("EGP"));
            ok(view.getBackground() instanceof android.graphics.drawable.ColorDrawable);
            eq(0,android.graphics.Color.alpha(((android.graphics.drawable.ColorDrawable)view.getBackground()).getColor()));
            settings.edit().putString("currency","USD").commit();
            view=applyLockWidget(LockSummaryWidget.render(context,snapshot));
            ok("87.50".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            settings.edit().putString("currency","EGP").putBoolean("hide_amounts",true).commit();
            view=applyLockWidget(LockSummaryWidget.render(context,snapshot));
            ok("••••".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            ok(!view.getContentDescription().toString().contains("235"));
            settings.edit().putBoolean("hide_amounts",false).commit();
            db.addManual(at(today)+2000,25000,"EGP","QA over budget","بقالة");
            view=applyLockWidget(LockSummaryWidget.render(context,DailySummary.load(context,db)));
            ok("15.00".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            ok(((android.widget.TextView)view.findViewById(R.id.lock_caption)).getText().toString().startsWith("تجاوز"));
            DailySummary.remove(context,"EGP");
            view=applyLockWidget(LockSummaryWidget.render(context,DailySummary.load(context,db)));
            ok("—".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            ok(!view.getContentDescription().toString().contains("15.00"));
            view=applyLockWidget(LockSummaryWidget.render(context,null));
            ok("—".contentEquals(((android.widget.TextView)view.findViewById(R.id.lock_remaining)).getText()));
            ok(!view.getContentDescription().toString().contains("235"));
            eq(4,countRows("transactions")); // Rendering never rewrites the financial ledger.
        }finally {settings.edit().clear().commit();}
    }

    private android.view.View applyLockWidget(android.widget.RemoteViews views) {
        android.view.View[] result=new android.view.View[1];
        runner.runOnMainSync(()->result[0]=views.apply(context,new android.widget.FrameLayout(context)));
        return result[0];
    }
    public void testAccountMovementIncludesLinkedCardsWithoutGuessingOrDuplication() {
        LocalDate day=LocalDate.now();long t=at(day);
        long account=db.addPaymentSource("Banque Misr","حساب","1111");
        long card=db.addPaymentSource("Banque Misr","بطاقة","2222");
        long oldCard=db.addPaymentSource("Banque Misr","بطاقة","4444");
        long other=db.addPaymentSource("Banque Misr","حساب","3333");
        long unlinked=db.addPaymentSource("Banque Misr","بطاقة","5555");
        db.linkCardToAccount(card,account);db.linkCardToAccount(oldCard,account);
        db.updateSourceProfile(oldCard,"",true,true);
        ok(db.addManual(t,10000,"EGP","QA groceries","بقالة",card));
        long purchase=db.latestInserted().id;
        ok(db.addManual(t+1000,2000,"EGP","QA old card","مقاهي",oldCard));
        ok(db.addManualEntry(t+2000,80000,"EGP","QA inflow","دخل",account,"income",null,null));
        long inflow=db.latestInserted().id;
        ok(db.addManualEntry(t+3000,15000,"EGP","QA external","تحويلات",account,"expense",null,null));
        ok(db.addManualEntry(t+4000,30000,"EGP","QA own transfer","تحويلات",other,"transfer",card,null));
        long transfer=db.latestInserted().id;
        ok(db.addManual(t+5000,7000,"EGP","QA separate","بقالة",unlinked));
        ok(db.addManual(t+6000,99999,"USD","QA other currency","بقالة",card));
        ok(db.addManual(at(day.minusDays(1)),88888,"EGP","QA other day","بقالة",card));
        Map<Long,java.util.List<ExpenseDb.Transaction>> groups=db.accountTransactionGroups(day,day.plusDays(1),"EGP");
        eq(3,groups.size());eq(5,groups.get(account).size());eq(1,groups.get(other).size());eq(1,groups.get(unlinked).size());
        ok(!groups.containsKey(card) && !groups.containsKey(oldCard));
        eq(5,new java.util.HashSet<>(groups.get(account).stream().map(tx->tx.id).collect(java.util.stream.Collectors.toList())).size());
        AccountMovement movement=new AccountMovement();
        for(ExpenseDb.Transaction tx:groups.get(account)) movement.add(tx.kind,tx.category,tx.amountMinor,
                tx.paymentSourceId!=null && db.accountId(tx.paymentSourceId)==account,
                tx.destinationSourceId!=null && db.accountId(tx.destinationSourceId)==account);
        eq(12000,movement.spent);eq(15000,movement.sent);eq(80000,movement.incoming);eq(30000,movement.ownIncoming);eq(0,movement.ownOutgoing);
        // Reimbursements do not rewrite actual bank movements, and grouping is read-only.
        eq(10000,db.linkReimbursement(inflow,purchase));
        eq(5,db.accountTransactionGroups(day,day.plusDays(1),"EGP").get(account).size());
        eq(10000,db.transactionById(purchase).amountMinor);eq(card,db.transactionById(purchase).paymentSourceId);
        eq(30000,db.transactionById(transfer).amountMinor);eq(8,countRows("transactions"));
        db.unlinkCard(card);
        groups=db.accountTransactionGroups(day,day.plusDays(1),"EGP");
        eq(3,groups.get(account).size());eq(2,groups.get(card).size());
        eq(10000,db.transactionById(purchase).amountMinor);eq(card,db.transactionById(purchase).paymentSourceId);
    }
    public void testHistoricalTransferWithinOneLinkedAccountAppearsOnce() {
        LocalDate day=LocalDate.now();long account=db.addPaymentSource("HSBC","حساب","0001");
        long card=db.addPaymentSource("HSBC","بطاقة","0002");
        ok(db.addManualEntry(at(day),5000,"EGP","QA old internal","تحويلات",card,"transfer",account,null));
        db.linkCardToAccount(card,account);
        Map<Long,java.util.List<ExpenseDb.Transaction>> groups=db.accountTransactionGroups(day,day.plusDays(1),"EGP");
        eq(1,groups.size());eq(1,groups.get(account).size());
        AccountMovement movement=new AccountMovement();movement.add("transfer","تحويلات",5000,true,true);
        eq(0,movement.ownIncoming);eq(0,movement.ownOutgoing);eq(0,movement.outgoing());eq(1,countRows("transactions"));
    }
    public void testCashWithdrawalIsNeitherSpendingNorBudgetUse() {
        LocalDate day=LocalDate.now();
        ok(db.addManual(at(day),50000,"EGP","QA ATM","سحب نقدي"));
        ok(db.addManual(at(day),10000,"EGP","QA grocery","بقالة"));
        ok(db.addManual(at(day),500,"EGP","QA fee","رسوم تحويل"));
        long planId=db.putBudgetPlan(null,"QA cash budget",100000,"EGP","monthly",day.withDayOfMonth(1),30,
                Arrays.asList("سحب نقدي","بقالة","رسوم تحويل"));
        ok(planId>0);
        ExpenseDb.BudgetPlan plan=null;
        for(ExpenseDb.BudgetPlan item:db.budgetPlans())if(item.id==planId)plan=item;
        ok(plan!=null);
        eq(10500,db.spentForBudget(plan,day));
        eq(10500,db.monthlyTotals(day).get("EGP"));
        eq(10500,db.monthlyTotalForKind(day,"EGP","expense"));
        long categoryTotal=0;
        for(ExpenseDb.CategoryTotal item:db.monthlyCategoryTotals(day))categoryTotal+=item.amountMinor;
        eq(10500,categoryTotal);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",100000,day,0));
        DailySummary summary=DailySummary.load(context,db);
        eq(10500,summary.currencies.get("EGP").expense);
        eq(89500,summary.currencies.get("EGP").remaining);
        eq(3,countRows("transactions")); // Historical rows are not rewritten or re-categorized.
    }
    public void testCapturedCashPrincipalAndStandaloneFeeUseEventWording() {
        LocalDate day=LocalDate.now();long t=at(day);
        ok(db.addSms("BANK","تم سحب 2000 جنيه من حسابك",t));
        ExpenseDb.Transaction cash=db.lastCaptured();
        ok(cash!=null && "سحب نقدي".equals(cash.category));eq(200000,cash.amountMinor);
        ok(db.addSms("BANK","Your account has been charged EGP 5.00 as a cash withdrawal fee at ATM",t+60000));
        ExpenseDb.Transaction fee=db.lastCaptured();
        ok(fee!=null && !"سحب نقدي".equals(fee.category));eq(500,fee.amountMinor);
        // The withdrawal principal is visible but not spending; the standalone fee stays spending.
        eq(500,db.monthlyTotals(day).get("EGP"));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",100000,day,0));
        DailySummary summary=DailySummary.load(context,db);
        eq(500,summary.currencies.get("EGP").expense);
        eq(99500,summary.currencies.get("EGP").remaining);
        eq(2,countRows("transactions"));
    }
    public void testMixedPrincipalAndNegatedFeesStayCash() {
        LocalDate day=LocalDate.now();long t=at(day);
        String[] withdrawals={
                "تم سحب 2000 جنيه من حسابك برسوم 5 جنيه",
                "Cash withdrawal of EGP 2,000.00 from account ****1234 with a fee of EGP 5.00",
                "Cash withdrawal of EGP 500 from account ****1234 without commission",
                "تم سحب 500 جنيه من حسابك بدون رسوم",
                "Cash withdrawal of EGP 1,000.00 from account ****1234 with no fees"
        };
        long cashMinor=0;
        for(int i=0;i<withdrawals.length;i++) {
            ok(db.addSms("BANK",withdrawals[i],t+i*60000L));
            ExpenseDb.Transaction row=db.lastCaptured();
            ok(row!=null && "سحب نقدي".equals(row.category));
            cashMinor+=row.amountMinor;
        }
        eq(600000,cashMinor);
        ok(db.addSms("BANK","Your account has been charged EGP 5.00 as a cash withdrawal fee at ATM",t+10*60000L));
        ExpenseDb.Transaction fee=db.lastCaptured();
        ok(fee!=null && !"سحب نقدي".equals(fee.category));eq(500,fee.amountMinor);
        // One row per message: a stated fee never splits or inflates the principal.
        eq(6,countRows("transactions"));
        eq(500,db.monthlyTotals(day).get("EGP"));
        eq(500,db.monthlyTotalForKind(day,"EGP","expense"));
        Map<Long,Long> offsets=db.reimbursementOffsets();
        java.util.List<MoneyFlow.Item> items=new java.util.ArrayList<>();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(day,day.plusDays(1))) {
            long amount=ExpenseDb.effectiveAmount(tx,offsets);
            if(amount>0)items.add(new MoneyFlow.Item(tx.id,tx.when,amount,tx.currency,tx.kind,tx.category,tx.merchant));
        }
        MoneyFlow.Summary summary=MoneyFlow.summarize(items,
                MoneyFlow.period(MoneyFlow.Range.MONTH,day,day),ZoneId.systemDefault()).get("EGP");
        eq(600000,summary.of(MoneyFlow.Bucket.CASH_WITHDRAWAL));
        eq(500,summary.of(MoneyFlow.Bucket.LIVING));
        eq(500,summary.totalOut());
        DailySummary.save(context,new DailyAllowance.Plan("EGP",100000,day,0));
        DailySummary daily=DailySummary.load(context,db);
        eq(500,daily.currencies.get("EGP").expense);
        eq(99500,daily.currencies.get("EGP").remaining);
    }
    public void testCashFeeOwnershipIsStableAcrossWordOrder() {
        LocalDate day=LocalDate.now();long t=at(day);
        String[] messages={
                "Cash withdrawal at ATM for EGP 2000 from account ****1234 with a fee",
                "تم سحب من حسابك مبلغ 2000 جنيه مع رسوم",
                "Your account was debited EGP 2000 at ATM with a fee",
                "ATM cash withdrawal fee of EGP 5 from account ****1234",
                "Cash withdrawal at ATM for EGP 5 from account ****1234 with a fee of EGP 5",
                "Your account was charged EGP 5 from account ****1234 as a cash withdrawal fee"
        };
        long cash=0,fees=0;
        for(int i=0;i<messages.length;i++) {
            ok(db.addSms("BANK",messages[i],t+i*3600000L));
            ExpenseDb.Transaction row=db.lastCaptured();ok(row!=null);
            if(i==3 || i==5) {
                ok(MoneyFlow.BANK_FEE_CATEGORY.equals(row.category));fees+=row.amountMinor;
            } else {
                ok(MoneyFlow.CASH_WITHDRAWAL_CATEGORY.equals(row.category));cash+=row.amountMinor;
            }
        }
        eq(600500,cash);eq(1000,fees);eq(6,countRows("transactions"));
        eq(1000,db.monthlyTotals(day).get("EGP"));
        eq(1000,db.monthlyTotalForKind(day,"EGP","expense"));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",100000,day,0));
        DailySummary summary=DailySummary.load(context,db);
        eq(1000,summary.currencies.get("EGP").expense);
        eq(99000,summary.currencies.get("EGP").remaining);
    }
    public void testWithdrawnVerbDoesNotHideNamedPayments() {
        LocalDate day=LocalDate.now();long t=at(day);
        String[][] cases={
                {"تم سحب مبلغ 1500 جنيه من حسابك قسط القرض","أقساط"},
                {"EGP 1,500.00 withdrawn from your account for loan installment","أقساط"},
                {"EGP 500 withdrawn from your account as transfer to Mohamed","تحويلات"},
                {"تم سحب 250 جنيه من حسابك لصالح Vodafone","فواتير"},
                {"EGP 220 withdrawn from your account at OPENAI *CHATGPT SUBSCR","اشتراكات"},
                {"Cash withdrawal of EGP 2000 at Vodafone ATM","سحب نقدي"}
        };
        for(int i=0;i<cases.length;i++) {
            ok(db.addSms("BANK",cases[i][0],t+i*3600000L));
            ExpenseDb.Transaction row=db.lastCaptured();ok(row!=null);eq(cases[i][1],row.category);
        }
        eq(6,countRows("transactions"));
        eq(397000,db.monthlyTotals(day).get("EGP"));
        eq(397000,db.monthlyTotalForKind(day,"EGP","expense"));
        ok(db.addSms("BANK","EGP 100 withdrawn from your account for purchase at CARREFOUR",t+7*3600000L));
        ok(!MoneyFlow.isCashWithdrawal(db.lastCaptured().kind,db.lastCaptured().category));
        eq(407000,db.monthlyTotalForKind(day,"EGP","expense"));
    }
    public void testLegacyReimbursementLinkToCashWithdrawalIsKeptButNotApplied() {
        LocalDate day=LocalDate.now();long t=at(day);
        ok(db.addManual(t,200000,"EGP","QA ATM","سحب نقدي"));long withdrawal=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,50000,"EGP","QA refund","دخل",null,"income",null,null));long income=db.latestInserted().id;
        // An earlier version could link a withdrawal; the stored link stays as audit history.
        db.getWritableDatabase().execSQL("INSERT INTO reimbursements(income_id,expense_id,amount_minor) VALUES(?,?,?)",
                new Object[]{income,withdrawal,50000});
        Map<Long,Long> offsets=db.reimbursementOffsets();
        eq(0,offsets.getOrDefault(withdrawal,0L));eq(0,offsets.getOrDefault(income,0L));
        eq(200000,ExpenseDb.effectiveAmount(db.transactionById(withdrawal),offsets));
        eq(50000,ExpenseDb.effectiveAmount(db.transactionById(income),offsets));
        java.util.List<MoneyFlow.Item> items=new java.util.ArrayList<>();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(day,day.plusDays(1))) {
            long amount=ExpenseDb.effectiveAmount(tx,offsets);
            if(amount>0)items.add(new MoneyFlow.Item(tx.id,tx.when,amount,tx.currency,tx.kind,tx.category,tx.merchant));
        }
        MoneyFlow.Summary summary=MoneyFlow.summarize(items,
                MoneyFlow.period(MoneyFlow.Range.MONTH,day,day),ZoneId.systemDefault()).get("EGP");
        eq(200000,summary.of(MoneyFlow.Bucket.CASH_WITHDRAWAL));
        eq(0,summary.totalOut());
        eq(50000,summary.of(MoneyFlow.Bucket.INFLOW));
        // Stored movements and the link itself are unchanged; undo removes only the link.
        eq(1,countRows("reimbursements"));
        eq(200000,db.transactionById(withdrawal).amountMinor);eq(50000,db.transactionById(income).amountMinor);
        db.unlinkReimbursement(withdrawal);
        eq(0,countRows("reimbursements"));
        eq(2,countRows("transactions"));
    }
    public void testPurchaseReclassifiedToCashStopsApplyingItsReimbursement() {
        LocalDate day=LocalDate.now();long t=at(day);
        ok(db.addManual(t,50000,"EGP","QA shop","بقالة"));long purchase=db.latestInserted().id;
        ok(db.addManualEntry(t+60000,20000,"EGP","QA refund","دخل",null,"income",null,null));long income=db.latestInserted().id;
        eq(20000,db.linkReimbursement(income,purchase));
        eq(20000,db.reimbursementOffsets().get(purchase));
        db.updateCategory(purchase,"سحب نقدي");
        Map<Long,Long> offsets=db.reimbursementOffsets();
        eq(0,offsets.getOrDefault(purchase,0L));eq(0,offsets.getOrDefault(income,0L));
        eq(50000,ExpenseDb.effectiveAmount(db.transactionById(purchase),offsets));
        eq(20000,ExpenseDb.effectiveAmount(db.transactionById(income),offsets));
        eq(1,countRows("reimbursements"));
        eq(2,countRows("transactions"));
    }
    public void testMonthlySummaryUsesEffectiveAmountsAndNeverWritesLedger() {
        LocalDate day=LocalDate.of(2026,9,10);
        ok(db.addManual(at(day),10000,"EGP","QA purchase","بقالة"));long purchase=db.latestInserted().id;
        ok(db.addManualEntry(at(day.plusDays(1)),20000,"EGP","QA recorded inflow","دخل",null,"income",null,null));
        ok(db.addManualEntry(at(LocalDate.of(2026,10,2)),5000,"EGP","QA repayment","دخل",null,"income",null,null));long refund=db.latestInserted().id;
        db.linkReimbursement(refund,purchase);
        ok(db.addManual(at(LocalDate.of(2026,10,3)),12000,"EGP","QA October","مقاهي"));long octoberBuy=db.latestInserted().id;
        java.util.List<MoneyFlow.Item> effective=new java.util.ArrayList<>();Map<Long,Long> offsets=db.reimbursementOffsets();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(day.withDayOfMonth(1),LocalDate.of(2026,11,1))) {
            long amount=ExpenseDb.effectiveAmount(tx,offsets);
            if(amount>0)effective.add(new MoneyFlow.Item(tx.id,tx.when,amount,tx.currency,tx.kind,tx.category,tx.merchant));
        }
        MonthlyReport report=MonthlyReport.build(effective,java.time.YearMonth.of(2026,10),ZoneId.systemDefault());
        MonthlyReport.Currency egp=report.currencies.get("EGP");
        eq(12000,egp.current.totalOut());eq(0,egp.current.of(MoneyFlow.Bucket.INFLOW));eq(5000,egp.previous.of(MoneyFlow.Bucket.LIVING));
        ok(egp.livingChangePercent(false)==null);eq(140,egp.livingChangePercent(true));
        eq(4,countRows("transactions"));eq(10000,db.transactionById(purchase).amountMinor);eq(5000,db.transactionById(refund).amountMinor);
        eq(5000,db.reimbursementOffsets().get(purchase));
        ok(db.addManualEntry(at(LocalDate.of(2026,10,4)),12000,"EGP","QA full repayment","دخل",null,"income",null,null));
        eq(12000,db.linkReimbursement(db.latestInserted().id,octoberBuy));
        long remaining=0;offsets=db.reimbursementOffsets();
        for(ExpenseDb.Transaction tx:db.transactionsBetween(LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)))remaining+=ExpenseDb.effectiveAmount(tx,offsets);
        eq(0,remaining);eq(3,db.transactionsBetween(LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)).size());
    }
    public void testEmailSupportDraftAndCopyContainReviewedTextOnly() {
        db.addManual(at(LocalDate.now()),987654,"EGP","QA PRIVATE MERCHANT","تسوق");
        String description="اقتراح عربي & + ? #\nسطر تاني";
        android.content.Intent draft=EmailSupport.draft(context,true,description);
        ok(android.content.Intent.ACTION_SENDTO.equals(draft.getAction()));
        android.net.Uri uri=draft.getData();ok("mailto".equals(uri.getScheme()));
        ok(uri.toString().startsWith("mailto:"+EmailSupport.ADDRESS+"?"));
        // MailTo.parse decodes the whole query before splitting '&'; validate RFC encoding field by field instead.
        android.net.Uri query=android.net.Uri.parse("https://example.invalid/?"+uri.toString().split("\\?",2)[1]);
        ok(EmailSupport.subject(true).equals(query.getQueryParameter("subject")));
        ok(EmailSupport.body(context,description).equals(query.getQueryParameter("body")));
        ok(EmailSupport.body(context,description).equals(draft.getStringExtra(android.content.Intent.EXTRA_TEXT)));
        ok(!query.getQueryParameter("body").contains("QA PRIVATE MERCHANT") && !query.getQueryParameter("body").contains("987654"));
        ok(EmailSupport.copyText(context,true,description).contains(description));
        ok(EmailSupport.copyText(context,true,description).startsWith(EmailSupport.ADDRESS+"\n"));
        ok(!EmailSupport.subject(true).equals(EmailSupport.subject(false)));
    }
    public void testPurchaseAndUnrelatedTransferAreNotDuplicates() {
        long t = at(LocalDate.now());
        ok(db.addSms("HSBC", "Purchase EGP100 at QA SHOP ONE", t));
        ok(db.addNotification("bank.app", "HSBC", "You sent EGP100 to qa.other@instapay", t + 1000));
        eq(2, db.transactions().size());
    }
    public void testFuturePaymentIsNotBookedAsCompleted() {
        long t = at(LocalDate.now());
        db.addSms("BanqueMisr", "Your payment EGP700 to QA FUTURE will be processed tomorrow", t);
        eq(0, db.transactions().size());
    }
    private long legacyCapture(String message,long t,boolean edited) {
        db.addManual(t,70000,"EGP","QA legacy","غير مصنف");long id=db.latestInserted().id;
        ContentValues row=new ContentValues();row.put("source","SMS");row.put("user_edited",edited?1:0);
        row.put("message_hash",CaptureIdentity.hash("BANK\n"+message.trim()));
        db.getWritableDatabase().update("transactions",row,"id=?",new String[]{Long.toString(id)});
        return id;
    }
    private long countRows(String table) {
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}
    }
    public void testRecentTransactionsMatchLedgerOrderAndExclusions() {
        long base=at(LocalDate.of(2026,10,20));
        for(int i=0;i<52;i++) {
            ContentValues row=new ContentValues();
            row.put("unique_key","qa-recent-"+i);
            row.put("occurred_at",base+(i%7)*60000L);
            row.put("received_at",base+(i%7)*60000L);
            row.put("amount_minor",1000+i);
            row.put("currency",i%2==0?"EGP":"USD");
            row.put("merchant","QA recent "+i);
            row.put("category","غير مصنف");
            row.put("source","يدوي");
            row.put("kind","expense");
            long id=db.getWritableDatabase().insertOrThrow("transactions",null,row);
            if(i==50) db.archiveEntry(id,"QA archived",true);
            if(i==51) db.archiveEntry(id,"QA cancelled",false);
        }
        java.util.List<ExpenseDb.Transaction> all=db.transactions();
        java.util.List<ExpenseDb.Transaction> recent=db.recentTransactions(5);
        eq(50,all.size());eq(5,recent.size());eq(0,db.recentTransactions(0).size());
        eq(50,db.recentTransactions(1000).size());
        for(int i=0;i<recent.size();i++) eq(all.get(i).id,recent.get(i).id);
        for(int i=1;i<all.size();i++) if(all.get(i-1).when==all.get(i).when)
            ok(all.get(i-1).id<all.get(i).id);
    }
    private void seedRecentRows(ExpenseDb target) {
        SQLiteDatabase sql=target.getWritableDatabase();long base=at(LocalDate.of(2026,10,20));
        sql.beginTransaction();
        try {
            for(int i=0;i<3000;i++) {
                ContentValues row=new ContentValues();row.put("unique_key","qa-benchmark-"+i);
                row.put("occurred_at",base+i*1000L);row.put("received_at",base+i*1000L);
                row.put("amount_minor",1000+i);row.put("currency",i%2==0?"EGP":"USD");
                row.put("merchant","QA benchmark "+i);row.put("category","غير مصنف");
                row.put("source","يدوي");row.put("kind","expense");
                sql.insertOrThrow("transactions",null,row);
            }
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }
    private String benchmarkRecentTransactions() {
        seedRecentRows(db);
        for(int i=0;i<5;i++){db.transactions();db.recentTransactions(5);}
        long allNanos=0,recentNanos=0;
        for(int i=0;i<30;i++) {
            long start=System.nanoTime();eq(500,db.transactions().size());allNanos+=System.nanoTime()-start;
            start=System.nanoTime();eq(5,db.recentTransactions(5).size());recentNanos+=System.nanoTime()-start;
        }
        return "3000 synthetic rows; 30-call mean, transactions(500): "+(allNanos/30/1000000.0)
                +" ms; recentTransactions(5): "+(recentNanos/30/1000000.0)+" ms\n";
    }
    private String benchmarkHomeRender() throws Exception {
        MainActivity activity=(MainActivity)runner.startActivitySync(new android.content.Intent(runner.getTargetContext(),MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        final long[] elapsed={0};final Throwable[] failure={null};
        try {
            runner.runOnMainSync(()->{
                try {
                    java.lang.reflect.Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
                    java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,0);
                    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
                    render.invoke(activity);
                    for(int i=0;i<20;i++){long start=System.nanoTime();render.invoke(activity);elapsed[0]+=System.nanoTime()-start;}
                } catch(Throwable e){failure[0]=e;}
            });
            if(failure[0]!=null)throw new AssertionError(failure[0]);
            return "3000 synthetic live rows; 20-call mean home render: "+(elapsed[0]/20/1000000.0)+" ms\n";
        } finally { runner.runOnMainSync(activity::finish); }
    }
    public void testRescanPreservesHistoricalFutureAndFailedRows() {
        long t=at(LocalDate.now());
        for(String message:Arrays.asList("هيتم خصم ٧٠٠ جنيه من حسابك بكرة","Purchase EGP 700 declined")) {
            long id=legacyCapture(message,t,false);
            ok(!db.addSms("BANK",message,t));
            ExpenseDb.Transaction retained=db.transactionById(id);ok(retained!=null && retained.needsReview);eq(70000,retained.amountMinor);
            eq(0,countRows("transaction_archive"));eq(0,countRows("excluded_messages"));
            t+=600000;
        }
        eq(2,db.transactions().size());
    }
    public void testRescanDoesNotTouchEditedHistoricalRows() {
        String message="هيتخصم ٧٠٠ جنيه من حسابك بكرة";long t=at(LocalDate.now());
        long id=legacyCapture(message,t,true);ok(!db.addSms("BANK",message,t));
        ExpenseDb.Transaction retained=db.transactionById(id);ok(retained!=null && !retained.needsReview);eq(70000,retained.amountMinor);
        eq(0,countRows("transaction_archive"));eq(0,countRows("excluded_messages"));
    }
    public void testMixedFutureAlertBooksOnlyCompletedPurchase() {
        long t=at(LocalDate.now());
        ok(db.addSms("BANK","تم خصم ١٠٠٠ جنيه لدى كارفور، وسيتم خصم رسوم ٥ جنيه",t));
        eq(100000,db.latestInserted().amountMinor);eq(1,db.transactions().size());
        ok(db.addSms("BANK","Your card was debited EGP 500 at AMAZON. Installment will be debited monthly",t+600000));
        eq(50000,db.latestInserted().amountMinor);ok(!"أقساط".equals(db.latestInserted().category));
    }
    public void testRefundAmountAndBudgetRoundTrip() {
        LocalDate today = LocalDate.now(); long t = at(today);
        DailySummary.save(context, new DailyAllowance.Plan("EGP", 25000, today, 0));
        db.addManual(t, 111288, "EGP", "QA SEOUDI", "أكل وشرب"); long buy = db.latestInserted().id;
        db.addManualEntry(t + 1000, 120000, "EGP", "QA friend", "دخل", null, "income", null, null); long back = db.latestInserted().id;
        eq(111288, db.linkReimbursement(back, buy)); Map<Long, Long> offsets = db.reimbursementOffsets();
        eq(0, ExpenseDb.effectiveAmount(db.transactionById(buy), offsets));
        eq(8712, ExpenseDb.effectiveAmount(db.transactionById(back), offsets));
        eq(25000, DailySummary.load(context, db).currencies.get("EGP").remaining);
        eq(2, db.transactions().size());
        db.unlinkReimbursement(back); eq(-86288, DailySummary.load(context, db).currencies.get("EGP").remaining);
    }
    public void testPartialReimbursementsNeverExceedPurchase() {
        long t = at(LocalDate.now()); db.addManual(t, 111288, "EGP", "QA SEOUDI", "أكل وشرب"); long buy = db.latestInserted().id;
        db.addManualEntry(t + 1000, 50000, "EGP", "QA A", "دخل", null, "income", null, null); long a = db.latestInserted().id;
        db.addManualEntry(t + 2000, 70000, "EGP", "QA B", "دخل", null, "income", null, null); long b = db.latestInserted().id;
        eq(50000, db.linkReimbursement(a, buy)); eq(61288, db.linkReimbursement(b, buy));
        eq(111288, db.reimbursedOf(buy)); eq(8712, ExpenseDb.effectiveAmount(db.transactionById(b), db.reimbursementOffsets()));
    }
    public void testFailedReimbursementRelinkAndCallerRollbackKeepOriginalLinks() {
        long t=at(LocalDate.now());
        ok(db.addManual(t,10000,"EGP","QA original purchase","تسوق"));long original=db.latestInserted().id;
        ok(db.addManual(t,5000,"EGP","QA fully repaid","تسوق"));long full=db.latestInserted().id;
        ok(db.addManualEntry(t+1000,4000,"EGP","QA refund A","دخل",null,"income",null,null));long a=db.latestInserted().id;
        ok(db.addManualEntry(t+2000,5000,"EGP","QA refund B","دخل",null,"income",null,null));long b=db.latestInserted().id;
        eq(4000,db.linkReimbursement(a,original));eq(5000,db.linkReimbursement(b,full));
        try{db.linkReimbursement(a,full);throw new AssertionError("Relink to repaid purchase succeeded");}
        catch(IllegalArgumentException expected){}
        eq(original,db.reimbursedExpense(a).id);eq(4000,db.reimbursedOf(original));eq(5000,db.reimbursedOf(full));
        eq(1,db.reimbursingIncomes(original).size());eq(a,db.reimbursingIncomes(original).get(0).id);
        ok(db.reimbursedExpense(999999)==null);ok(db.reimbursingIncomes(999999).isEmpty());
        SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try{
            db.unlinkReimbursement(original);ok(db.undoReimbursement(b,full,5000));
            ok(db.reimbursedExpense(a)==null);ok(db.reimbursedExpense(b)==null);
            // The caller deliberately rolls back both removals.
        }finally{connection.endTransaction();}
        ok(connection.isOpen());ok(!connection.inTransaction());
        eq(original,db.reimbursedExpense(a).id);eq(full,db.reimbursedExpense(b).id);
        ok(!db.undoReimbursement(a,original,1));eq(4000,db.reimbursedOf(original));
        ok(db.undoReimbursement(a,original,4000));eq(0,db.reimbursedOf(original));eq(5000,db.reimbursedOf(full));
        eq(4,db.transactionCount());eq(10000,db.transactionById(original).amountMinor);eq(4000,db.transactionById(a).amountMinor);
    }
    public void testLegacyReimbursementProjectionSkipsInvalidLinksAndClipsChronologically() {
        long t=at(LocalDate.now());
        ok(db.addManual(t,10000,"EGP","QA purchase","تسوق"));long buy=db.latestInserted().id;
        ok(db.addManualEntry(t+3000,6000,"EGP","QA later refund","دخل",null,"income",null,null));long later=db.latestInserted().id;
        ok(db.addManualEntry(t+1000,5000,"EGP","QA earlier refund","دخل",null,"income",null,null));long earlier=db.latestInserted().id;
        ok(db.addManualEntry(t+500,4000,"USD","QA wrong currency","دخل",null,"income",null,null));long currency=db.latestInserted().id;
        ok(db.addManual(t+250,5000,"EGP","QA wrong kind","تسوق"));long kind=db.latestInserted().id;
        ok(db.addManualEntry(t+750,1000,"EGP","QA negative link","دخل",null,"income",null,null));long negative=db.latestInserted().id;
        // Legacy/malformed persisted links must not consume capacity or erase gross ledger rows.
        long[][] links={{later,6000},{earlier,5000},{currency,4000},{kind,5000},{999999,10000},{negative,-5}};
        for(long[] link:links)db.getWritableDatabase().execSQL("INSERT INTO reimbursements(income_id,expense_id,amount_minor) VALUES (?,?,?)",
                new Object[]{link[0],buy,link[1]});
        Map<Long,Long> offsets=db.reimbursementOffsets();eq(3,offsets.size());
        eq(10000,offsets.get(buy));eq(5000,offsets.get(earlier));eq(5000,offsets.get(later));
        ok(!offsets.containsKey(currency));ok(!offsets.containsKey(kind));ok(!offsets.containsKey(999999L));ok(!offsets.containsKey(negative));
        eq(0,ExpenseDb.effectiveAmount(db.transactionById(buy),offsets));eq(1000,ExpenseDb.effectiveAmount(db.transactionById(later),offsets));
        eq(6,db.transactionCount());eq(10000,db.transactionById(buy).amountMinor);
        try(android.database.Cursor count=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM reimbursements",null)){
            ok(count.moveToFirst());eq(6,count.getLong(0));
        }
    }
    public void testDifferentCurrencyRefundIsRejected() {
        long t = at(LocalDate.now()); db.addManual(t, 10000, "EGP", "QA SHOP", "تسوق"); long buy = db.latestInserted().id;
        db.addManualEntry(t + 1000, 10000, "USD", "QA friend", "دخل", null, "income", null, null); long back = db.latestInserted().id;
        try { db.linkReimbursement(back, buy); throw new AssertionError("Currency mixing accepted"); } catch (IllegalArgumentException expected) { }
    }
    public void testEditedPurchaseKeepsReimbursementWithinItsAmount() {
        long t = at(LocalDate.now()); db.addManual(t, 111288, "EGP", "QA SHOP", "تسوق"); long buy = db.latestInserted().id;
        db.addManualEntry(t + 1000, 120000, "EGP", "QA friend", "دخل", null, "income", null, null); long back = db.latestInserted().id;
        db.linkReimbursement(back, buy);
        db.updateTransaction(buy, 50000, "EGP", "QA SHOP", "تسوق", null, "expense", null, null, t);
        // The same persisted edit the transaction editor makes must not leave a larger offset than the purchase.
        ok(db.reimbursedOf(buy) <= db.transactionById(buy).amountMinor);
        eq(50000,db.reimbursedOf(buy));
        eq(70000,ExpenseDb.effectiveAmount(db.transactionById(back),db.reimbursementOffsets()));
        db.close();db=new ExpenseDb(context);
        db.updateTransaction(buy,111288,"EGP","QA SHOP","تسوق",null,"expense",null,null,t);
        eq(111288,db.reimbursedOf(buy));eq(8712,ExpenseDb.effectiveAmount(db.transactionById(back),db.reimbursementOffsets()));
    }
    public void testEditedIncomeAndCurrencyRevalidateRefund() {
        long t=at(LocalDate.now());db.addManual(t,111288,"EGP","QA SHOP","تسوق");long buy=db.latestInserted().id;
        db.addManualEntry(t+1000,120000,"EGP","QA friend","دخل",null,"income",null,null);long back=db.latestInserted().id;
        db.linkReimbursement(back,buy);
        db.updateTransaction(back,40000,"EGP","QA friend","دخل",null,"income",null,null,t+1000);
        eq(40000,db.reimbursedOf(buy));eq(71288,ExpenseDb.effectiveAmount(db.transactionById(buy),db.reimbursementOffsets()));
        db.updateTransaction(buy,111288,"USD","QA SHOP","تسوق",null,"expense",null,null,t);
        eq(0,db.reimbursedOf(buy));eq(40000,ExpenseDb.effectiveAmount(db.transactionById(back),db.reimbursementOffsets()));
        db.updateTransaction(buy,111288,"EGP","QA SHOP","تسوق",null,"expense",null,null,t);
        db.updateTransaction(back,120000,"EGP","QA friend","دخل",null,"income",null,null,t+1000);
        eq(111288,db.reimbursedOf(buy));
    }
    public void testMultipleRefundsRemainBoundedAfterEditAndReopen() {
        long t=at(LocalDate.now());db.addManual(t,100000,"EGP","QA SHOP","تسوق");long buy=db.latestInserted().id;
        db.addManualEntry(t+1000,60000,"EGP","QA A","دخل",null,"income",null,null);long a=db.latestInserted().id;
        db.addManualEntry(t+2000,50000,"EGP","QA B","دخل",null,"income",null,null);long b=db.latestInserted().id;
        db.linkReimbursement(a,buy);db.linkReimbursement(b,buy);
        db.updateTransaction(buy,70000,"EGP","QA SHOP","تسوق",null,"expense",null,null,t);
        eq(70000,db.reimbursedOf(buy));eq(0,ExpenseDb.effectiveAmount(db.transactionById(a),db.reimbursementOffsets()));
        eq(40000,ExpenseDb.effectiveAmount(db.transactionById(b),db.reimbursementOffsets()));
        // Reading/reopening a database must bound offsets without changing saved links.
        db.getWritableDatabase().execSQL("UPDATE transactions SET amount_minor=30000 WHERE id="+buy);
        db.close();db=new ExpenseDb(context);eq(30000,db.reimbursedOf(buy));
        eq(30000,ExpenseDb.effectiveAmount(db.transactionById(a),db.reimbursementOffsets()));
        eq(50000,ExpenseDb.effectiveAmount(db.transactionById(b),db.reimbursementOffsets()));
        db.updateTransaction(buy,100000,"EGP","QA SHOP","تسوق",null,"expense",null,null,t);
        eq(100000,db.reimbursedOf(buy));eq(0,ExpenseDb.effectiveAmount(db.transactionById(a),db.reimbursementOffsets()));
        eq(10000,ExpenseDb.effectiveAmount(db.transactionById(b),db.reimbursementOffsets()));
    }
    public void testMonthPaymentMovesCardOnce() {
        long id = db.putCommitment(null, "QA card", 547872, "EGP", LocalDate.of(2026, 10, 26), "monthly", "installment", 24, 14, 1, "QA card", null);
        ContentValues paid = new ContentValues(); paid.put("last_paid_date", "2026-10-10");
        db.getWritableDatabase().update("recurring", paid, "id=?", new String[]{Long.toString(id)});
        eq(1, db.normalizeEarlyPayments()); eq(0, db.normalizeEarlyPayments()); eq(14, db.recurringById(id).paidCount);
        ok(db.recurringById(id).nextDue.equals(LocalDate.of(2026, 11, 26)));
    }
    public void testConfirmedApartmentEndsOnChosenLastDate() {
        long account = db.addPaymentSource("BM", "حساب", "7392"); long last = at(LocalDate.of(2026, 9, 30));
        db.addManual(last, 5060000, "EGP", "QA transfer", "تحويلات", account); long tx = db.latestInserted().id;
        RepairRules.RepeatingTransfer pattern = new RepairRules.RepeatingTransfer(account, 5060000, "EGP", Collections.singletonList(tx), 1, at(LocalDate.of(2025, 7, 1)), last);
        long id = db.confirmRepeatingTransfer(pattern, "QA apartment", LocalDate.of(2030, 12, 1), 15, LocalDate.of(2026, 10, 11));
        ExpenseDb.Recurring plan = db.recurringById(id);
        ok(plan.nextDue.equals(LocalDate.of(2026, 11, 1)));
        eq(50, plan.totalCount - plan.paidCount);
    }
    public void testMerchantCorrectionLearnsBranchesWithoutChangingMoneyOrTransfers() {
        long t=at(LocalDate.now());
        db.rememberMerchantCategory("KASHIER*BUTLERS & CO C","مقاهي");
        ok(db.addSms("BANK","Card purchase EGP 88.25 at BUTLERS & CO CAIRO",t));
        ExpenseDb.Transaction purchase=db.latestInserted();eq("مقاهي",purchase.category);eq(8825L,purchase.amountMinor);eq("EGP",purchase.currency);
        db.close();db=new ExpenseDb(context);
        ok(db.addNotification("qa.bank","BANK","Card purchase EGP 91.25 at KASHIER*BUTLERS & CO MALL",t+1000));
        eq("مقاهي",db.latestInserted().category);eq(9125L,db.latestInserted().amountMinor);
        db.addManual(t+2000,5000,"USD","Butlers","تسوق");eq("تسوق",db.latestInserted().category);
        ok(db.addSms("BANK","You sent EGP 42.00 to Butlers",t+3000));eq("تحويلات",db.latestInserted().category);
        ok(!ExpenseDb.canRememberMerchant("HSBC","تسوق"));ok(!ExpenseDb.canRememberMerchant("Butlers","تحويلات"));
    }

    public void testReimbursementJourneyKeepsTodayChronologicalAndUncapped() {
        LocalDate day=LocalDate.now();long t=at(day);
        db.addManual(at(day.minusDays(2)),29500,"EGP","older exact match","بقالة");long old=db.latestInserted().id;
        db.addManual(t,41900,"EGP","Seoudi","بقالة");long seoudi=db.latestInserted().id;
        for(int i=0;i<12;i++)db.addManual(t+1000+i,100+i,"EGP","more "+i,"مقاهي");
        db.addManual(t+3000,500,"USD","other currency","بقالة");
        db.addManual(t+4000,100,"EGP","unresolved transfer","تحويلات");
        db.addManual(t+120000,29500,"EGP","future","بقالة");
        db.addManualEntry(t+10000,29500,"EGP","repayment","دخل",null,"income",null,null);ExpenseDb.Transaction income=db.latestInserted();
        java.util.List<ExpenseDb.Transaction> today=db.reimbursementCandidates(income);
        eq(13,today.size());eq(seoudi,today.get(12).id);
        for(int i=1;i<today.size();i++)ok(today.get(i-1).when>=today.get(i).when);
        java.util.List<ExpenseDb.Transaction> older=db.reimbursementCandidates(income,true);eq(1,older.size());eq(old,older.get(0).id);
    }

    public void testReimbursementPreviewIsReadOnlyAtTheExactTimeBoundary() {
        LocalDate day=LocalDate.now();long t=at(day);
        ok(db.addManual(t+60000,3000,"EGP","QA boundary A","بقالة"));long a=db.latestInserted().id;
        ok(db.addManual(t+60000,5000,"EGP","QA boundary B","بقالة"));long b=db.latestInserted().id;
        ok(db.addManual(t+60001,7000,"EGP","QA too late","بقالة"));long later=db.latestInserted().id;
        ok(db.addManual(at(day.minusDays(1)),1000,"EGP","QA older","بقالة"));long older=db.latestInserted().id;
        ok(db.addManual(t,9000,"USD","QA other currency","بقالة"));
        ok(db.addManual(t,1100,"EGP","QA unresolved","تحويلات"));
        ok(db.addManualEntry(t,2000,"EGP","QA refund","دخل",null,"income",null,null));
        ExpenseDb.Transaction income=db.latestInserted();long revision=db.changes();
        java.util.List<ExpenseDb.Transaction> candidates=db.reimbursementCandidates(income);
        eq(2,candidates.size());eq(b,candidates.get(0).id);eq(a,candidates.get(1).id);
        java.util.List<ExpenseDb.Transaction> previous=db.reimbursementCandidates(income,true);
        eq(1,previous.size());eq(older,previous.get(0).id);
        ok(db.reimbursementCandidates(null).isEmpty());ok(db.reimbursementCandidates(db.transactionById(a)).isEmpty());
        ExpenseDb.ReimbursementPreview preview=db.previewReimbursement(income.id,a);
        eq(2000,preview.applied);eq(1000,preview.personal);eq(13000,preview.daySpending);ok(day.equals(preview.day));
        try{db.previewReimbursement(income.id,later);throw new AssertionError("Purchase beyond the one-minute boundary allowed");}
        catch(IllegalArgumentException expected){}
        eq(revision,db.changes());eq(0,countRows("reimbursements"));eq(7,db.transactionCount());
    }

    public void testReimbursementConfirmationBorrowsRollbackAndRechecksExternalDaySpending() {
        long t=at(LocalDate.now());
        ok(db.addManual(t,5000,"EGP","QA purchase","بقالة"));long buy=db.latestInserted().id;
        ok(db.addManualEntry(t+1000,2000,"EGP","QA refund","دخل",null,"income",null,null));long income=db.latestInserted().id;
        ExpenseDb.ReimbursementPreview preview=db.previewReimbursement(income,buy);long revision=db.changes();
        SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try{eq(2000,db.confirmReimbursement(preview));eq(2000,db.reimbursedOf(buy));}
        finally{connection.endTransaction();} // The outer caller deliberately rolls the confirmation back.
        eq(revision,db.changes());eq(0,countRows("reimbursements"));ok(connection.isOpen()&&!connection.inTransaction());
        try(ExpenseDb external=new ExpenseDb(context)){
            ok(external.addManual(t+2000,300,"EGP","QA changed day total","بقالة"));
        }
        long externalRevision=db.changes();ok(externalRevision>revision);
        try{db.confirmReimbursement(preview);throw new AssertionError("Stale day-spending preview confirmed");}
        catch(IllegalArgumentException expected){}
        eq(externalRevision,db.changes());eq(0,countRows("reimbursements"));ok(!connection.inTransaction());
        ExpenseDb.ReimbursementPreview fresh=db.previewReimbursement(income,buy);
        eq(3300,fresh.daySpending);eq(2000,db.confirmReimbursement(fresh));eq(2000,db.reimbursedOf(buy));
        eq(5000,db.transactionById(buy).amountMinor);eq(2000,db.transactionById(income).amountMinor);
    }

    public void testSeoudiCoffeePreviewConfirmUndoAndOldDay() {
        LocalDate day=LocalDate.now();long t=at(day);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,day.minusDays(3),0));
        db.addManual(t,41900,"EGP","Seoudi","بقالة");long buy=db.latestInserted().id;
        db.addManual(t+1000,2500,"EGP","Coffee","مقاهي");
        db.addManualEntry(t+2000,29500,"EGP","colleague","دخل",null,"income",null,null);long back=db.latestInserted().id;
        ExpenseDb.ReimbursementPreview preview=db.previewReimbursement(back,buy);
        eq(12400,preview.personal);eq(14900,preview.daySpending);eq(0,db.reimbursedOf(buy)); // Preview is read-only.
        eq(29500,db.confirmReimbursement(preview));eq(10100,DailySummary.load(context,db).currencies.get("EGP").remaining);
        eq(41900,db.transactionById(buy).amountMinor);eq(29500,db.transactionById(back).amountMinor);eq(3,db.transactions().size());
        ok(!db.undoReimbursement(back,buy,100));ok(db.undoReimbursement(back,buy,29500));
        eq(-19400,DailySummary.load(context,db).currencies.get("EGP").remaining);
        db.addManual(at(day.minusDays(1)),41900,"EGP","yesterday","بقالة");long old=db.latestInserted().id;
        eq(29500,db.confirmReimbursement(db.previewReimbursement(back,old)));
        eq(-19400,DailySummary.load(context,db).currencies.get("EGP").remaining); // Yesterday refund must not fund today.
        eq(12400,ExpenseDb.effectiveAmount(db.transactionById(old),db.reimbursementOffsets()));
    }

    public void testConfirmationRejectsStalePreviewWithoutMovingTheLink() {
        long t=at(LocalDate.now());db.addManual(t,41900,"EGP","Seoudi","بقالة");long buy=db.latestInserted().id;
        db.addManualEntry(t+2000,29500,"EGP","colleague","دخل",null,"income",null,null);long back=db.latestInserted().id;
        ExpenseDb.ReimbursementPreview preview=db.previewReimbursement(back,buy);
        db.updateTransaction(buy,50000,"EGP","Seoudi","بقالة",null);
        try{db.confirmReimbursement(preview);throw new AssertionError("Stale confirmation saved");}catch(IllegalArgumentException expected){}
        eq(0,db.reimbursedOf(buy));eq(29500,db.confirmReimbursement(db.previewReimbursement(back,buy)));
        ok(!db.undoReimbursement(back,buy,10000));eq(29500,db.reimbursedOf(buy));
    }

    public void testDailyPointsRecomputeRefundDayWithoutFundingTodayOrDoubling() {
        LocalDate today=LocalDate.now(),yesterday=today.minusDays(1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,yesterday,0));
        DailyPointsStore.enable(context,"EGP",yesterday);DailyPointsStore.observe(context,"EGP",today);
        db.addManual(at(yesterday),41900,"EGP","Seoudi","بقالة");long buy=db.latestInserted().id;
        db.addManual(at(yesterday)+1000,2500,"EGP","Coffee","مقاهي");long coffee=db.latestInserted().id;
        db.addManual(at(today),1000,"EGP","today","مقاهي");
        db.addManualEntry(at(today)+1000,29500,"EGP","colleague","دخل",null,"income",null,null);long back=db.latestInserted().id;
        // Closed days derive automatically, without a confirmation write.
        ok("-19".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)))));
        db.linkReimbursement(back,buy);
        java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",today);
        ok("+10".equals(DailyPoints.format(DailyPoints.total(days))));eq(1000,days.get(1).spending);ok(!days.get(1).finalized);
        db.updateTransaction(coffee,3500,"EGP","Coffee","مقاهي",null);
        ok("+9".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)))));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",50000,yesterday,0));DailyPointsStore.observe(context,"EGP",today);
        days=DailyPointsStore.load(context,db,"EGP",today);eq(25000,days.get(0).limit);eq(25000,days.get(1).limit);
        DailyPointsStore.disable(context,"EGP");DailyPointsStore.enable(context,"EGP",today);eq(25000,DailyPointsStore.load(context,db,"EGP",today).get(1).limit);
        try{DailyPointsStore.confirm(context,db,"EGP",today,today);throw new AssertionError("Open day finalized");}catch(IllegalArgumentException expected){}
        ok("+9".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)))));
    }

    public void testPointsCloseAutomaticallyAndKeepCurrenciesAndUnknownDaysSeparate() {
        LocalDate today=LocalDate.now(),yesterday=today.minusDays(1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,yesterday.minusDays(1),0));
        DailySummary.save(context,new DailyAllowance.Plan("USD",1000,yesterday,0));
        DailyPointsStore.enable(context,"EGP",yesterday);DailyPointsStore.enable(context,"USD",yesterday);
        db.addManual(at(yesterday),100,"USD","USD coffee","مقاهي");
        eq(2,DailyPointsStore.load(context,db,"EGP",today).size());eq(0,DailyPointsStore.load(context,db,"EGP",today).get(0).spending);
        ok("+25".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)))));
        ok("+1".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"USD",today)))));
        db.addManual(at(yesterday),100,"EGP","needs review","أخرى");long id=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{id});
        ok(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)).signum()==0);
        try{DailyPointsStore.confirm(context,db,"EGP",yesterday,today);throw new AssertionError("Uncertain day finalized");}catch(IllegalArgumentException expected){}
        try{DailyPointsStore.confirm(context,db,"EGP",yesterday.minusDays(1),today);throw new AssertionError("Unobserved day finalized");}catch(IllegalArgumentException expected){}
    }

    public void testAutomaticPointsCatchUpWithoutOpeningAndRecomputeLateChanges(){
        LocalDate first=LocalDate.of(2026,10,1),today=first.plusDays(10);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,first,0),first);DailyPointsStore.enable(context,"EGP",first);
        long revision=db.changes();java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",today);
        eq(11,days.size());eq("+250",DailyPoints.format(DailyPoints.total(days)));ok(!days.get(10).finalized);eq(revision,db.changes());
        eq("+250",DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today))));
        ok(db.addManual(at(first.plusDays(2)),15000,"EGP","QA late shop","بقالة"));long id=db.latestInserted().id;
        eq("+235",DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today))));
        db.archiveEntry(id,"bank confirmed cancellation",true);
        eq("+250",DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today))));
    }
    public void testAutomaticPointsRespectDatedLimitsRemovalAndFutureStart(){
        LocalDate first=LocalDate.of(2026,10,1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",10000,first,0),first);DailyPointsStore.enable(context,"EGP",first);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",20000,first,0),first.plusDays(3));
        java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",first.plusDays(7));
        eq(10000,days.get(3).limit);eq(20000,days.get(4).limit);eq("+100",DailyPoints.format(DailyPoints.total(days)));
        DailySummary.remove(context,"EGP",first.plusDays(7));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",30000,first.plusDays(10),0),first.plusDays(10));
        days=DailyPointsStore.load(context,db,"EGP",first.plusDays(13));
        eq("+180",DailyPoints.format(DailyPoints.total(days)));
        for(DailyPoints.Day day:days)ok(day.date.isBefore(first.plusDays(8))||!day.date.isBefore(first.plusDays(11)));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",40000,first.plusDays(15),0),first.plusDays(13));
        days=DailyPointsStore.load(context,db,"EGP",first.plusDays(17));
        for(DailyPoints.Day day:days){ok(!day.date.equals(first.plusDays(14)));if(!day.date.isBefore(first.plusDays(15)))eq(40000,day.limit);}
    }
    public void testAutomaticPointsPauseDoesNotAwardDisabledDays(){
        LocalDate first=LocalDate.of(2026,10,1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",10000,first,0),first);DailyPointsStore.enable(context,"EGP",first);
        DailyPointsStore.disable(context,"EGP",first.plusDays(2));
        java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",first.plusDays(6));
        eq(3,days.size());eq("+30",DailyPoints.format(DailyPoints.total(days)));
        DailyPointsStore.enable(context,"EGP",first.plusDays(6));DailyPointsStore.enable(context,"EGP",first.plusDays(6));
        days=DailyPointsStore.load(context,db,"EGP",first.plusDays(9));eq(7,days.size());eq("+60",DailyPoints.format(DailyPoints.total(days)));
        eq(0,DailyPointsStore.unknownDays(context,"EGP",first.plusDays(9)));
    }
    public void testLegacyPointsCloseKnownDaysButDoNotInventGapLimits(){
        LocalDate first=LocalDate.of(2026,10,1),today=first.plusDays(5);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",10000,first,0),today);
        SharedPreferences prefs=context.getSharedPreferences(DailyPointsStore.SETTINGS,0);
        prefs.edit().putBoolean("enabled_EGP",true).putLong("limit_EGP_"+first,10000).putLong("limit_EGP_"+first.plusDays(2),10000)
                .putBoolean("complete_EGP_"+first,false).commit();
        java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",today);
        eq(3,days.size());eq("+20",DailyPoints.format(DailyPoints.total(days)));eq(3,DailyPointsStore.unknownDays(context,"EGP",today));
        eq("+40",DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today.plusDays(2)))));
        ok(prefs.contains("complete_EGP_"+first));ok(!prefs.getBoolean("complete_EGP_"+first,true));
    }
    public void testAutomaticPointsUseLocalCalendarAcrossShortDstDay(){
        java.util.TimeZone original=java.util.TimeZone.getDefault();
        try{
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"));LocalDate first=LocalDate.of(2026,3,28);
            DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,first,0),first);DailyPointsStore.enable(context,"EGP",first);
            db.addManual(first.plusDays(1).atTime(23,45).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),1000,"EGP","QA DST late","بقالة");
            db.addManual(first.plusDays(2).atTime(0,15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),2000,"EGP","QA DST early","بقالة");
            java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",first.plusDays(3));
            eq(4,days.size());eq(1000,days.get(1).spending);eq(2000,days.get(2).spending);eq("+72",DailyPoints.format(DailyPoints.total(days)));
        }finally{java.util.TimeZone.setDefault(original);}
    }
    public void testUntrackedBudgetChangeLeavesMissingDaysUnknownAndClockCannotRewriteHistory(){
        LocalDate first=LocalDate.of(2026,10,1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",10000,first,0),first);DailyPointsStore.enable(context,"EGP",first);
        // Simulate an interrupted settings/history update. The new limit does not prove the intervening days.
        context.getSharedPreferences(DailySummary.SETTINGS,0).edit().putLong("amount_EGP",30000).commit();
        java.util.List<DailyPoints.Day> days=DailyPointsStore.load(context,db,"EGP",first.plusDays(6));
        eq(2,days.size());eq("+10",DailyPoints.format(DailyPoints.total(days)));eq(5,DailyPointsStore.unknownDays(context,"EGP",first.plusDays(6)));
        eq("+40",DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",first.plusDays(7)))));
        java.util.Map<String,?> before=context.getSharedPreferences(DailyPointsStore.SETTINGS,0).getAll();
        DailyPointsStore.observe(context,"EGP",first.plusDays(2));ok(before.equals(context.getSharedPreferences(DailyPointsStore.SETTINGS,0).getAll()));
    }
    /** A row saved by the parser before 2د, with its original HSBC message kept as evidence. */
    private long oldHsbcRow(long t,long amount,String merchant,String category,long source,boolean edited,String body) {
        ok(db.addManual(t,amount,"EGP",merchant,category,source));long id=db.latestInserted().id;
        ContentValues values=new ContentValues();values.put("source","SMS");values.put("user_edited",edited?1:0);
        db.getWritableDatabase().update("transactions",values,"id=?",new String[]{Long.toString(id)});
        db.getWritableDatabase().execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,source,sender,received_at,direction,encrypted_body) VALUES(?,?,?,?,?,?,?)",
                new Object[]{"qa-hsbc-"+id,id,"SMS","HSBC",t,"expense",EvidenceVault.encrypt(body)});
        return id;
    }
    private String evidenceDirection(long id) {
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT direction FROM capture_evidence WHERE transaction_id=?",new String[]{Long.toString(id)})){
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    public void testTrailingSignRepairFlipsOldHsbcCreditsOnceAndSkipsEditedOrLinkedRows() {
        long t=at(LocalDate.now().minusDays(3));
        long hsbc=db.addPaymentSource("HSBC","حساب بنكي","678");
        String credit="From HSBC: 25SEP26 Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74";
        String cash="From HSBC: 17OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00";
        long flipped=oldHsbcRow(t,8765000,"HSBC: 25SEP26 Phone Banking Transfer","تحويلات",hsbc,false,credit);
        long edited=oldHsbcRow(t+1000,8765000,"HSBC: 25SEP26 Phone Banking Transfer","تحويلات",hsbc,true,credit);
        long renamed=oldHsbcRow(t+2000,200000,"HSBC: 17OCT26 ATM Cash Withdrawal fr","غير مصنف",hsbc,false,cash);
        long refunded=oldHsbcRow(t+3000,8765000,"HSBC: 25SEP26 Phone Banking Transfer","تحويلات",hsbc,false,credit);
        ok(db.addManualEntry(t+4000,8765000,"EGP","QA refund","دخل",null,"income",null,null));long refund=db.latestInserted().id;
        db.getWritableDatabase().execSQL("INSERT INTO reimbursements(income_id,expense_id,amount_minor) VALUES(?,?,?)",new Object[]{refund,refunded,8765000});
        long count=db.transactionCount();
        ExpenseDb.RepairReport report=db.repairAll();
        eq(2,report.trailingSigns);ok(report.changed());eq(count,db.transactionCount());
        ExpenseDb.Transaction income=db.transactionById(flipped);
        eq("income",income.kind);eq("Phone Banking Transfer",income.merchant);eq("دخل",income.category);
        eq(8765000L,income.amountMinor);eq("EGP",income.currency);eq(t,income.when);eq(hsbc,income.paymentSourceId);
        eq("income",evidenceDirection(flipped));
        ExpenseDb.Transaction untouched=db.transactionById(edited);
        eq("expense",untouched.kind);eq("HSBC: 25SEP26 Phone Banking Transfer",untouched.merchant);eq("expense",evidenceDirection(edited));
        ExpenseDb.Transaction atm=db.transactionById(renamed);
        eq("expense",atm.kind);eq("ATM Cash Withdrawal",atm.merchant);eq("سحب نقدي",atm.category);eq(200000L,atm.amountMinor);
        eq("expense",db.transactionById(refunded).kind);eq("HSBC: 25SEP26 Phone Banking Transfer",db.transactionById(refunded).merchant);
        eq(0,db.repairAll().trailingSigns);
        eq("income",db.transactionById(flipped).kind);eq("ATM Cash Withdrawal",db.transactionById(renamed).merchant);
    }

    public void testHsbcTrailingSignCaptureBooksCreditAsIncomeWithBankSource() {
        long t=at(LocalDate.now().minusDays(2));
        ok(db.addSms("HSBC","From HSBC: 25SEP26 Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74",t));
        ExpenseDb.Transaction credit=db.lastCaptured();
        eq("income",credit.kind);eq("Phone Banking Transfer",credit.merchant);eq("دخل",credit.category);eq(8765000L,credit.amountMinor);
        ok(credit.paymentSourceId!=null);ExpenseDb.PaymentSource source=db.paymentSource(credit.paymentSourceId);
        eq("HSBC",source.name);eq("678",source.last4);eq("hsbc",BrandCatalog.bank(source.name));
        ok(db.addSms("HSBC","From HSBC: 17OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00",t+1000));
        ExpenseDb.Transaction cash=db.lastCaptured();
        eq("expense",cash.kind);eq("ATM Cash Withdrawal",cash.merchant);eq("سحب نقدي",cash.category);eq(200000L,cash.amountMinor);eq(credit.paymentSourceId,cash.paymentSourceId);
        ok(db.addSms("HSBC","From HSBC: 24SEP26 TT Payment from 012-345***-679 USD 2,083.85+ Your available balance is USD 2,150.10",t+2000));
        ExpenseDb.Transaction salary=db.lastCaptured();
        eq("income",salary.kind);eq("TT Payment",salary.merchant);eq(208385L,salary.amountMinor);eq("USD",salary.currency);
        ok(db.addSms("HSBC","From HSBC: 25SEP26 Phone Banking Transfer from 012-345***-679 USD 1,700.00- Your available balance is USD 450.10",t+3000));
        ExpenseDb.Transaction out=db.lastCaptured();
        eq("expense",out.kind);eq("Phone Banking Transfer",out.merchant);eq("تحويلات",out.category);eq(170000L,out.amountMinor);eq("USD",out.currency);
        eq(4,db.transactionCount());eq(0,db.repairAll().trailingSigns);
    }

    /** 2ج: a debit to one of the user's own credit cards is captured with that card as destination, so the statement treats it as internal. */
    public void testKnownCardRepaymentIsCapturedWithTheCardAsDestination() {
        long t=at(LocalDate.now().minusDays(1));
        long account=db.addPaymentSource("QA Bank","حساب","5678");
        long card=db.addPaymentSource("QA Bank Card","بطاقة ائتمان","1234");
        ok(db.addSms("QA Bank","Transfer of EGP 4,000.00 from your account ****5678 to credit card ****1234 on 10/10/2026",t));
        ExpenseDb.Transaction repayment=db.lastCaptured();
        eq("expense",repayment.kind);eq(400000L,repayment.amountMinor);
        ok(repayment.destinationSourceId!=null&&repayment.destinationSourceId==card);
        ok(repayment.paymentSourceId!=null&&repayment.paymentSourceId==account);
        ok(MoneyFlow.bucket(repayment.kind,repayment.category,true)==MoneyFlow.Bucket.INTERNAL);
        // An unknown card keeps today's behaviour: no destination, still an outgoing transfer.
        ok(db.addSms("QA Bank","Transfer of EGP 1,000.00 from your account ****5678 to credit card ****9999 on 11/10/2026",t+1000));
        ExpenseDb.Transaction unknown=db.lastCaptured();
        ok(unknown.destinationSourceId==null);eq("expense",unknown.kind);
        ok(MoneyFlow.bucket(unknown.kind,unknown.category,false)==MoneyFlow.Bucket.OPEN_TRANSFER);
    }

    /**
     * Owner report, 29 Oct 2026 (synthetic names and digits): an instant transfer from the user's own name, a bank debit
     * "الى كارت ائتمان" that names no card digits, and the card's "تم إيداع … بالبطاقة الائتمانية" receipt. None of them
     * is income or spending, and the debit and receipt are one repayment.
     */
    public void testOwnNameTransferAndCardRepaymentLegsAreNeitherIncomeNorSpending() {
        LocalDate day = LocalDate.now().minusDays(1);
        long t = at(day);
        String dmy = day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), ymd = day.toString();
        String mon = day.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)).toUpperCase(java.util.Locale.ROOT);
        long account = db.addPaymentSource("QA Bank", "حساب", "1111");
        long card = db.addPaymentSource("QA Bank", "بطاقة ائتمان", "2222");

        // Own name: before the user confirms it, the sender is income; after, a move into the user's own account.
        ok(db.addSms("QA Bank", "تم إضافة تحويل لحظي لحسابكم رقم xxx1111 بمبلغ 500 جم من QA STRANGER PERSON رقم مرجعي 900000000001 يوم " + ymd + " الساعه 10:00", t));
        eq("income", db.lastCaptured().kind);
        db.addOwnHandle("QA OWNER NAME");
        ok(db.addSms("QA Bank", "تم إضافة تحويل لحظي لحسابكم رقم xxx1111 بمبلغ 20000 جم من QA OWNER NAME رقم مرجعي 900000000002 يوم " + ymd + " الساعه 10:05", t + 1000));
        ExpenseDb.Transaction own = db.lastCaptured();
        eq("transfer", own.kind); ok(own.paymentSourceId == null); ok(own.destinationSourceId != null && own.destinationSourceId == account);
        ok(MoneyFlow.bucket(own.kind, own.category) == MoneyFlow.Bucket.INTERNAL);

        // The bank's debit names no card digits: the only credit card is the destination, no review needed.
        long before = db.transactionCount();
        ok(db.addSms("QA Bank", "تم تحويل مبلغ 3218EGP      من حساب رقم xxx1111      الى كارت ائتمان / مسبق الدفع فى " + mon, t + 60_000));
        ExpenseDb.Transaction debit = db.lastCaptured();
        eq("expense", debit.kind); eq(321800L, debit.amountMinor); ok(!debit.needsReview);
        ok(debit.paymentSourceId != null && debit.paymentSourceId == account); ok(debit.destinationSourceId != null && debit.destinationSourceId == card);
        ok(MoneyFlow.bucket(debit.kind, debit.category, true) == MoneyFlow.Bucket.INTERNAL);
        // The card's receipt of the same repayment joins the debit: no second row, never income.
        db.addSms("QA Bank", "عميلنا العزيز، تم إيداع EGP 3218  بالبطاقة الائتمانية المنتهية بـ ****2222، فى QA-Online يوم  " + dmy + " ، ورصيدكم الحالي 10920.95 EGP", t + 90_000);
        eq(before + 1, db.transactionCount());
        eq("expense", db.transactionById(debit.id).kind);

        // A receipt with no debit seen (another bank paid it): a transfer into the card, never income.
        db.addSms("QA Bank", "عميلنا العزيز، تم إيداع EGP 1500  بالبطاقة الائتمانية المنتهية بـ ****2222، فى QA-Online يوم  " + dmy, t + 200_000);
        ExpenseDb.Transaction deposit = db.lastCaptured();
        eq("transfer", deposit.kind); eq(150000L, deposit.amountMinor); ok(deposit.paymentSourceId == null);
        ok(deposit.destinationSourceId != null && deposit.destinationSourceId == card); ok(!deposit.needsReview);
        ok(MoneyFlow.bucket(deposit.kind, deposit.category) == MoneyFlow.Bucket.INTERNAL);
        // Its debit arriving afterwards completes that same row: one transfer from the account to the card.
        before = db.transactionCount();
        db.addSms("QA Bank", "تم تحويل مبلغ 1500EGP من حساب رقم xxx1111 الى كارت ائتمان / مسبق الدفع فى " + mon, t + 230_000);
        eq(before, db.transactionCount());
        ExpenseDb.Transaction completed = db.transactionById(deposit.id);
        eq("transfer", completed.kind); ok(completed.paymentSourceId != null && completed.paymentSourceId == account);
        ok(completed.destinationSourceId != null && completed.destinationSourceId == card);

        // A refund on the card is not a repayment.
        ok(!CaptureNotice.cardDeposit("تم رد مبلغ EGP 50 للبطاقة الائتمانية المنتهية بـ ****2222 من QA SHOP"));
        ok(CaptureNotice.cardDeposit("Your credit card ****2222 payment of EGP 700.00 has been received. Thank you"));
        // Two credit cards and no digits in the debit: no guess.
        db.addPaymentSource("QA Bank", "بطاقة ائتمان", "3333");
        ok(db.repaidCreditCard("تم تحويل مبلغ 900EGP من حساب رقم xxx1111 الى كارت ائتمان / مسبق الدفع") == null);
        ok(db.repaidCreditCard("تم تحويل مبلغ 900EGP من حساب رقم xxx1111 الى كارت ائتمان رقم 3333") != null);
    }

    private long oldPurchase(String merchant,String category,long t,boolean edited) {
        db.addManual(t,8250,"EGP",merchant,category);long id=db.latestInserted().id;
        ContentValues values=new ContentValues();values.put("source","SMS");values.put("user_edited",edited?1:0);
        db.getWritableDatabase().update("transactions",values,"id=?",new String[]{Long.toString(id)});return id;
    }

    public void testCatalogRefreshPreservesUserEditsRawNamesAndFinancialFields() {
        long t=at(LocalDate.now());
        long coffee=oldPurchase("ESPRESSO LAB-CENTER ALMA","أكل وشرب",t,false);
        long hospital=oldPurchase("CLEOPATRA HOSPITAL","غير مصنف",t+1000,false);
        long edited=oldPurchase("SEOUDI MARKET SODIC E","تسوق",t+2000,true);
        long transfer=oldPurchase("Butlers","تحويلات",t+3000,false);
        long installment=oldPurchase("Seoudi","أقساط",t+4000,false);
        long telda=oldPurchase("Kashier Butlers","غير مصنف",t+4500,false);
        db.addManual(t+5000,2000,"USD","Espresso Lab","أكل وشرب");long manual=db.latestInserted().id;
        int count=db.transactions().size();eq(3,db.refreshMerchantCategories());eq(0,db.refreshMerchantCategories());
        eq("أكل وشرب",db.transactionById(telda).category);eq("Kashier Butlers",db.transactionById(telda).merchant);
        ExpenseDb.Transaction row=db.transactionById(coffee);eq("مقاهي",row.category);eq("ESPRESSO LAB-CENTER ALMA",row.merchant);
        eq(8250L,row.amountMinor);eq("EGP",row.currency);eq(t,row.when);eq("expense",row.kind);
        eq("رعاية صحية",db.transactionById(hospital).category);eq("تسوق",db.transactionById(edited).category);
        eq("تحويلات",db.transactionById(transfer).category);eq("أقساط",db.transactionById(installment).category);
        eq("أكل وشرب",db.transactionById(manual).category);eq(count,db.transactions().size());
        db.addManual(t+6000,1500,"EGP","CLEOPATRA HOSPITAL","غير مصنف");long unknownManual=db.latestInserted().id;
        db.repairAll();eq("غير مصنف",db.transactionById(unknownManual).category);
        eq("ESPRESSO LAB-CENTER ALMA",db.transactionById(coffee).merchant);
    }

    public void testPastMerchantPreviewIsReadOnlyAndAcceptanceRechecksEdits() {
        long t=at(LocalDate.now());
        long first=oldPurchase("SEOUDI MARKET SODIC E","غير مصنف",t,false);
        long second=oldPurchase("SEOUDI-Sodic East","تسوق",t+1000,false);
        long edited=oldPurchase("SEOUDI MARKET","أخرى",t+2000,true);
        long transfer=oldPurchase("SEOUDI MARKET","تحويلات",t+3000,false);
        eq(2,db.merchantCategoryCandidates("Seoudi","بقالة").size());eq("غير مصنف",db.transactionById(first).category);
        db.updateCategories(Collections.singletonList(second),"عائلة ومساعدات");
        eq(1,db.applyMerchantCategory("Seoudi","بقالة"));eq("بقالة",db.transactionById(first).category);
        eq("عائلة ومساعدات",db.transactionById(second).category);eq("أخرى",db.transactionById(edited).category);
        eq("تحويلات",db.transactionById(transfer).category);eq(8250L,db.transactionById(first).amountMinor);
        eq(0,db.applyMerchantCategory("Seoudi","بقالة"));
    }

    private void eq(String expected,String actual) {
        if(!java.util.Objects.equals(expected,actual)) throw new AssertionError("Expected "+expected+", got "+actual);
    }

    private void seed() {
        Context target = runner.getTargetContext();
        try (ExpenseDb live = new ExpenseDb(target)) {
            for (ExpenseDb.Transaction tx : live.transactions()) if ("QA living fixture".equals(tx.merchant)) throw new IllegalStateException("Already seeded");
            long bm = live.addPaymentSource("Banque Misr", "حساب", "7392"), hsbc = live.addPaymentSource("HSBC", "حساب", "0001");
            LocalDate today = LocalDate.now();
            for (int i = 6; i >= 1; i--) {
                LocalDate month = today.withDayOfMonth(1).minusMonths(i);
                live.addManual(at(month.plusDays(4)), 15000 + i * 1000, "EGP", "QA living fixture", "أكل وشرب", bm);
                live.addManual(at(month.plusDays(7)), 5000, "EGP", "QA small fixture", "بقالة", bm);
                live.addManualEntry(at(month.plusDays(1)), 8000000, "EGP", "QA salary fixture", "دخل", bm, "income", null, null);
                live.addManual(at(month.plusDays(12)), 22000, "EGP", "ChatGPT", "اشتراكات", bm);
            }
            live.addManual(at(today.minusDays(2)), 90000, "EGP", "QA unusual fixture", "أكل وشرب", bm);
            live.putCommitment(null, "QA apartment", 5060000, "EGP", today.withDayOfMonth(1).plusMonths(1), "monthly", "installment", 65, 15, 1, "QA apartment", bm);
            for (int i = 0; i < 3; i++) live.putCommitment(null, "QA TRU " + (i + 1), new long[]{55300,159300,188500}[i], "EGP", today.withDayOfMonth(1).plusMonths(1), "monthly", "installment", 24, 14, 1, "QA TRU " + i, null);
            long card = live.putCommitment(null, "QA card 2700", 547872, "EGP", today.withDayOfMonth(26), "monthly", "installment", 24, 14, 1, "QA card", null);
            ContentValues paid = new ContentValues(); paid.put("last_paid_date", today.withDayOfMonth(10).toString()); live.getWritableDatabase().update("recurring", paid, "id=?", new String[]{Long.toString(card)});
            live.putCommitment(null, "Claude", 2280, "USD", today.withDayOfMonth(27), "monthly", "subscription", null, 0, 1, "Claude", null);
            live.putCommitment(null, "Google One", 17999, "EGP", today.withDayOfMonth(22), "monthly", "subscription", null, 0, 1, "Google One", null);
            live.putCommitment(null, "Vodafone", 189771, "EGP", today.withDayOfMonth(17), "monthly", "bill", null, 0, 1, "Vodafone", null);
            live.putCommitment(null, "Truecaller", 2249, "EGP", today.withDayOfMonth(15), "monthly", "subscription", null, 0, 1, "Truecaller", null);
        }
        DailySummary.save(target, new DailyAllowance.Plan("EGP", 25000, LocalDate.now(), 31));
        target.getSharedPreferences(DailySummary.SETTINGS, 0).edit().putLong("amount_EGP", 25000).putString("start_EGP", LocalDate.now().toString()).putInt("salary_EGP", 31).commit();
        target.getSharedPreferences("MainActivity", 0).edit().putBoolean("intro_completed", true).putBoolean("sms_permission_prompted", true).putBoolean("initial_import_prompted", true).commit();
        target.getSharedPreferences("notification_settings", 0).edit().putBoolean("access_prompted", true).commit();
    }
}
