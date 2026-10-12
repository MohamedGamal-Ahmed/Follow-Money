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
