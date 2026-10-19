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
        ok(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)).signum()==0);
        DailyPointsStore.confirm(context,db,"EGP",yesterday,today);DailyPointsStore.confirm(context,db,"EGP",yesterday,today);
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
        DailyPointsStore.revoke(context,"EGP",yesterday);ok(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)).signum()==0);
    }

    public void testPointsRequireCoverageAndKeepCurrenciesAndUnknownDaysSeparate() {
        LocalDate today=LocalDate.now(),yesterday=today.minusDays(1);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",25000,yesterday.minusDays(1),0));
        DailySummary.save(context,new DailyAllowance.Plan("USD",1000,yesterday,0));
        DailyPointsStore.enable(context,"EGP",yesterday);DailyPointsStore.enable(context,"USD",yesterday);
        db.addManual(at(yesterday),100,"USD","USD coffee","مقاهي");
        eq(1,DailyPointsStore.load(context,db,"EGP",today).size());eq(0,DailyPointsStore.load(context,db,"EGP",today).get(0).spending);
        ok(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)).signum()==0);
        DailyPointsStore.confirm(context,db,"EGP",yesterday,today);
        ok("+25".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)))));
        DailyPointsStore.confirm(context,db,"USD",yesterday,today);
        ok("+1".equals(DailyPoints.format(DailyPoints.total(DailyPointsStore.load(context,db,"USD",today)))));
        db.addManual(at(yesterday),100,"EGP","needs review","أخرى");long id=db.latestInserted().id;
        db.getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=?",new Object[]{id});
        ok(DailyPoints.total(DailyPointsStore.load(context,db,"EGP",today)).signum()==0);
        try{DailyPointsStore.confirm(context,db,"EGP",yesterday,today);throw new AssertionError("Uncertain day finalized");}catch(IllegalArgumentException expected){}
        try{DailyPointsStore.confirm(context,db,"EGP",yesterday.minusDays(1),today);throw new AssertionError("Unobserved day finalized");}catch(IllegalArgumentException expected){}
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
        db.addManual(t+5000,2000,"USD","Espresso Lab","أكل وشرب");long manual=db.latestInserted().id;
        int count=db.transactions().size();eq(2,db.refreshMerchantCategories());eq(0,db.refreshMerchantCategories());
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
