package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** Financial assertions use a private database and private test preferences, never personal records. */
public final class PhaseOneTest {
    private final Instrumentation runner;
    private ExpenseDb db;
    private Context context;
    private File file;
    private boolean capture;
    private static final String[] PREFS={"daily_budget","widget_settings","reminder_settings","MainActivity"};
    PhaseOneTest(Instrumentation runner,boolean capture){this.runner=runner;this.capture=capture;}
    private Context getTargetContext(){return runner.getTargetContext();}
    void run() {
        int passed=0,failed=0;StringBuilder report=new StringBuilder();
        for(java.lang.reflect.Method method:getClass().getDeclaredMethods()) {
            if(!method.getName().startsWith("test"))continue;
            try{setup();method.invoke(this);passed++;report.append(method.getName()).append(": passed\n");}
            catch(Throwable error){failed++;report.append(method.getName()).append(": FAILED ").append(error.getCause()==null?error:error.getCause()).append('\n');}
            finally{cleanup();}
        }
        if(capture&&failed==0)try{setup();captureScreens();report.append("Synthetic UI captures saved\n");}catch(Throwable error){failed++;report.append("Capture FAILED ").append(error).append('\n');for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().startsWith("com.mohamedgamal.masareef"))report.append(frame).append('\n');}finally{cleanup();}
        Bundle result=new Bundle();result.putString("stream",report+"Passed: "+passed+"; failed: "+failed+"\n");runner.finish(failed==0?-1:0,result);
    }
    private void setup() {
        Context target=getTargetContext();file=new File(target.getCacheDir(),"phase-one-test.db");SQLiteDatabase.deleteDatabase(file);
        for(String name:PREFS)target.getSharedPreferences("phase-one-test-"+name,0).edit().clear().commit();
        context=new ContextWrapper(target){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences("phase-one-test-"+name,mode);}
            @Override public File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.getPath(),factory,handler);}
        };db=new ExpenseDb(context);db.getWritableDatabase();
    }
    private void cleanup(){if(db!=null)db.close();if(file!=null)SQLiteDatabase.deleteDatabase(file);for(String name:PREFS)getTargetContext().deleteSharedPreferences("phase-one-test-"+name);}
    private static void eq(Object expected,Object actual){if(!java.util.Objects.equals(expected,actual))throw new AssertionError("Expected "+expected+", actual "+actual);}
    private static void ok(boolean value){if(!value)throw new AssertionError("Expected true");}
    private long count(String table){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
    private long at(LocalDate date){return date.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
    public void testPreviewIsReadOnlyAndExcludedStaysExcluded() {
        long when=at(LocalDate.now());String body="Card purchase at CARREFOUR EGP 250.00. Available balance EGP 300";
        long tx=count("transactions"),balances=count("balance_snapshots"),plans=count("recurring"),evidence=count("capture_evidence");
        SmsPreview candidate=SmsPreview.parse("BANK",body,when);ok(candidate!=null&&!candidate.selected);eq(0,db.smsCaptureStatus("BANK",body,when));
        eq(tx,count("transactions"));eq(balances,count("balance_snapshots"));eq(plans,count("recurring"));eq(evidence,count("capture_evidence"));
        ok(db.addSms("BANK",body,when));eq(1,db.smsCaptureStatus("BANK",body,when));
        db.deleteTransaction(db.latestInserted().id);eq(2,db.smsCaptureStatus("BANK",body,when));ok(!db.addSms("BANK",body,when));eq(0L,count("transactions"));
    }
    public void testSelectiveImportAndAtomicRollback() {
        long when=at(LocalDate.now());SQLiteDatabase sql=db.getWritableDatabase();
        sql.beginTransaction();try{ok(db.addSms("BANK","Card purchase at IKEA EGP 100",when));ok(db.addSms("BANK","You paid 99.99 EGP to Netflix",when+1000));}finally{sql.endTransaction();}
        eq(0L,count("transactions"));eq(0L,count("capture_evidence"));eq(0L,count("payment_sources"));
        SmsPreview first=SmsPreview.parse("BANK","Card purchase at IKEA EGP 100",when);
        SmsPreview second=SmsPreview.parse("BANK","You paid 99.99 EGP to Netflix",when+1000);first.selected=true;
        sql.beginTransaction();try{for(SmsPreview r:Arrays.asList(first,second))if(r.selected)db.addSms(r.sender,r.body,r.receivedAt);sql.setTransactionSuccessful();}finally{sql.endTransaction();}
        eq(1L,count("transactions"));eq("IKEA",db.latestInserted().merchant);
    }
    public void testLiveSmsAndImportShareIdentityDespiteDifferentReceiptTime() {
        long sent=at(LocalDate.now()),received=sent-3600000;
        String body="Card purchase at SAMEBILL EGP 250.00";
        ok(db.addSms("BANK",body,sent));long origin=db.latestInserted().id;
        db.updateCategories(Arrays.asList(origin),"تسوق");
        eq(1,db.smsCaptureStatus("BANK",body,received,sent));
        ok(!db.addSms("BANK",body,received,sent));ok(!db.addSms("BANK",body,received,sent));
        eq(1L,count("transactions"));eq(25000L,db.transactionById(origin).amountMinor);eq("تسوق",db.transactionById(origin).category);
        // An unrelated purchase with identical text after the existing time window survives.
        ok(db.addSms("BANK",body,sent+600000,sent+600000));eq(2L,count("transactions"));
        db.deleteTransaction(origin);eq(2,db.smsCaptureStatus("BANK",body,received,sent));
        ok(!db.addSms("BANK",body,received,sent));eq(1L,count("transactions"));
    }
    public void testLegacyImportAndExclusionKeepWorkingWithSentTimestamp() {
        long received=at(LocalDate.now()),sent=received+3600000;
        String body="Card purchase at LEGACYBILL EGP 250.00";
        ok(db.addSms("BANK",body,received));long origin=db.latestInserted().id;
        eq(1,db.smsCaptureStatus("BANK",body,received,sent,true));
        ok(!db.addSms("BANK",body,received,sent,true));eq(1L,count("transactions"));eq(origin,db.latestInserted().id);
        db.deleteTransaction(origin);eq(2,db.smsCaptureStatus("BANK",body,received,sent,true));
        ok(!db.addSms("BANK",body,received,sent,true));eq(0L,count("transactions"));
        ok(db.addSms("BANK","Card purchase at FALLBACK EGP 20",received,0));
        ok(!db.addSms("BANK","Card purchase at FALLBACK EGP 20",received,0));eq(1L,count("transactions"));
    }
    public void testReceiptKeyCollisionDoesNotDropASeparatePurchase() {
        long when=at(LocalDate.now());String body="Card purchase at SEPARATE EGP 250.00";
        ok(db.addSms("BANK",body,when));long first=db.latestInserted().id;
        eq(0,db.smsCaptureStatus("BANK",body,when,when+3600000,false));
        ok(db.addSms("BANK",body,when,when+3600000,false));eq(2L,count("transactions"));
        eq(25000L,db.transactionById(first).amountMinor);eq(25000L,db.latestInserted().amountMinor);
        ok(!db.addSms("BANK",body,when,when+3600000,false));eq(2L,count("transactions"));
        db.deleteTransaction(first);
        ok(db.addSms("BANK",body,when,when+7200000,false));eq(2L,count("transactions"));
    }
    public void testSplitDoesNotChangeOrDuplicateRecordedBankDebit() {
        long when=at(LocalDate.now());String body="Card purchase at DINNER EGP 900.00";
        ok(db.addSms("BANK",body,when));long origin=db.latestInserted().id;
        ok(!db.addSplitShare(when,90000,3,"EGP"," dinner ","أكل وشرب",null,"share"));
        eq(1L,count("transactions"));eq(90000L,db.transactionById(origin).amountMinor);
        ok(db.addSplitShare(when,90000,3,"USD","DINNER","أكل وشرب",null,"share"));
        eq(30000L,db.latestInserted().amountMinor);eq("USD",db.latestInserted().currency);
        ok(db.addSplitShare(at(LocalDate.now().plusDays(1)),90000,3,"EGP","DINNER","أكل وشرب",null,"share"));
        eq(30000L,db.latestInserted().amountMinor);
        ok(db.addSplitShare(when,10000,3,"EGP","OTHER","أكل وشرب",null,"share"));eq(3333L,db.latestInserted().amountMinor);
        long before=count("transactions");
        for(int people:new int[]{0,1,-1}) {
            try{db.addSplitShare(when,90000,people,"EGP","INVALID","أخرى",null,null);throw new AssertionError("Invalid people accepted");}
            catch(IllegalArgumentException expected){}
        }
        eq(before,count("transactions"));eq(90000L,db.transactionById(origin).amountMinor);
    }
    public void testBulkCategoriesDoNotChangeMoneyOrIncomeAndPersistOnReplay() {
        long when=at(LocalDate.now());String body="Card purchase at IKEA EGP 100";ok(db.addSms("BANK",body,when));long expense=db.latestInserted().id;
        ok(db.addManualEntry(when,20000,"EGP","salary","دخل",null,"income",null,null));long income=db.latestInserted().id;
        long from=db.addPaymentSource("A","بنك",null),to=db.addPaymentSource("B","بنك",null);
        ok(db.addManualEntry(when,5000,"EGP","own","تحويلات",from,"transfer",to,null));long transfer=db.latestInserted().id;
        eq(1,db.updateCategories(Arrays.asList(expense,expense,income,transfer),"تسوق"));
        eq("تسوق",db.transactionById(expense).category);eq(10000L,db.transactionById(expense).amountMinor);
        eq("income",db.transactionById(income).kind);eq("دخل",db.transactionById(income).category);eq("transfer",db.transactionById(transfer).kind);
        db.addSms("BANK",body,when);eq("تسوق",db.transactionById(expense).category);eq(3L,count("transactions"));
    }
    public void testCommitmentFromExpenseLinksOriginWithoutExtraExpense() {
        long when=at(LocalDate.now());ok(db.addManual(when,10000,"EGP","Netflix","اشتراكات"));long origin=db.latestInserted().id;
        long id=db.putCommitmentFromTransaction(origin,"Netflix",12000,"EGP",LocalDate.now().plusMonths(1),"subscription");ok(id>=0);
        eq(Long.valueOf(origin),db.commitmentOriginId(id));eq(1L,count("transactions"));eq(1L,count("recurring"));eq(0,db.recurring().get(0).paidCount);
        eq(-1L,db.putCommitmentFromTransaction(origin,"Netflix",12000,"EGP",LocalDate.now().plusMonths(1),"subscription"));eq(1L,count("recurring"));
        try{db.putCommitmentFromTransaction(origin,"Netflix",12000,"USD",LocalDate.now().plusMonths(1),"bill");throw new AssertionError("Changed currency");}catch(IllegalArgumentException expected){}
    }
    public void testDailySummaryCurrencyBoundariesEditDeleteAndPrivacy() {
        LocalDate today=LocalDate.now();long noon=at(today);
        DailySummary.save(context,new DailyAllowance.Plan("EGP",50000,today,25));DailySummary.save(context,new DailyAllowance.Plan("USD",1000,today,0));
        ok(db.addManual(noon,25000,"EGP","Carrefour","بقالة"));long egp=db.latestInserted().id;
        ok(db.addManual(noon,200,"USD","QA USD SHOP","تسوق"));
        ok(db.addManual(noon,300,"USD","Apple","اشتراكات")); // commitment must not consume the daily allowance
        ok(db.addManual(at(today.minusDays(1)),40000,"EGP","yesterday","أخرى"));
        ok(db.addManual(at(today.plusDays(1)),45000,"EGP","tomorrow","أخرى"));
        ok(db.addManualEntry(noon,999999,"EGP","Salary","دخل",null,"income",null,null));
        DailySummary summary=DailySummary.load(context,db);eq(25000L,summary.currencies.get("EGP").remaining);eq(800L,summary.currencies.get("USD").remaining);
        eq("داخل ميزانية اليوم",summary.remaining("EGP",true));
        db.updateTransaction(egp,65000,"EGP","Carrefour","بقالة",null);eq(-15000L,DailySummary.load(context,db).currencies.get("EGP").remaining);
        db.deleteTransaction(egp);eq(50000L,DailySummary.load(context,db).currencies.get("EGP").remaining);
        DailySummary.remove(context,"EGP");eq("حدد ميزانيتك اليومية",DailySummary.load(context,db).remaining("EGP",false));
    }
    public void testSalaryOnlyAndLastDayDoNotCreateIncome() {
        DailySummary.saveSalary(context,"EGP",3000000,DailyAllowance.MONTH_END);
        DailySummary summary=DailySummary.load(context,db);eq(0L,count("transactions"));eq(0L,summary.currencies.get("EGP").income);
        eq(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),summary.currencies.get("EGP").nextSalary);
        eq("حدد ميزانيتك اليومية",summary.remaining("EGP",false));
        DailySummary.save(context,new DailyAllowance.Plan("EGP",50000,LocalDate.now(),DailyAllowance.MONTH_END));
        eq(50000L,DailySummary.load(context,db).currencies.get("EGP").remaining);
        DailySummary.remove(context,"EGP");eq(3000000L,DailySummary.monthlySalary(context,"EGP"));
        ok(DailySummary.load(context,db).currencies.get("EGP").daysToSalary>=0);eq(0L,count("transactions"));
    }
    public void testDailySummaryAfterCsvAndTransferSettlement() {
        LocalDate today=LocalDate.now();DailySummary.save(context,new DailyAllowance.Plan("EGP",50000,today,0));
        CsvImport.Row row=CsvImport.parse("Date,Type,Merchant,Amount,Currency,Category\n"+today+",expense,IKEA,100.00,EGP,تسوق").get(0);
        ok(db.addCsvEntry("synthetic-fixture",row));eq(40000L,DailySummary.load(context,db).currencies.get("EGP").remaining);
        ok(!db.addCsvEntry("synthetic-fixture",row));eq(40000L,DailySummary.load(context,db).currencies.get("EGP").remaining);
        long when=at(today);long from=db.addPaymentSource("A","بنك","1111"),to=db.addPaymentSource("B","بنك","2222");
        ok(db.addManual(when,30300,"EGP","own transfer debit","غير مصنف",from));long debit=db.latestInserted().id;
        db.reconcileOwnTransfer(debit,30000,300,from,to,java.util.Collections.emptyList());
        DailySummary summary=DailySummary.load(context,db);eq(10300L,summary.currencies.get("EGP").expense);eq(39700L,summary.currencies.get("EGP").remaining);
    }
    public void testLocalDayHasExclusiveMidnightAndDstBounds() {
        java.time.ZoneId cairo=java.time.ZoneId.of("Africa/Cairo");LocalDate day=LocalDate.of(2026,10,29);
        long begin=day.atStartOfDay(cairo).toInstant().toEpochMilli(),end=day.plusDays(1).atStartOfDay(cairo).toInstant().toEpochMilli();
        ok(db.addManual(begin-1,100,"EGP","before","أخرى"));ok(db.addManual(begin,200,"EGP","start","أخرى"));
        ok(db.addManual(end-1,300,"EGP","last","أخرى"));ok(db.addManual(end,400,"EGP","next","أخرى"));
        List<ExpenseDb.Transaction> rows=db.transactionsBetween(day,day.plusDays(1),cairo);eq(2,rows.size());eq(500L,rows.get(0).amountMinor+rows.get(1).amountMinor);
        eq(25L,(end-begin)/3600000);
    }
    public void testSchema16MigrationPreservesLedger() {
        ok(db.addManual(at(LocalDate.now()),12345,"EGP","fixture","أخرى"));
        SQLiteDatabase sql=db.getWritableDatabase();sql.execSQL("ALTER TABLE recurring DROP COLUMN origin_transaction_id");
        sql.execSQL("CREATE TABLE legacy_sources (id INTEGER PRIMARY KEY,name TEXT NOT NULL,kind TEXT NOT NULL,last4 TEXT)");
        sql.execSQL("INSERT INTO legacy_sources SELECT id,name,kind,last4 FROM payment_sources");
        sql.execSQL("DROP TABLE payment_sources");sql.execSQL("ALTER TABLE legacy_sources RENAME TO payment_sources");
        sql.execSQL("ALTER TABLE recurring DROP COLUMN last_paid_due");
        sql.execSQL("DROP TABLE reimbursements");sql.execSQL("DROP TABLE dismissed_patterns");sql.execSQL("DROP TABLE repair_log");
        sql.execSQL("ALTER TABLE transactions DROP COLUMN time_precision");
        sql.setVersion(16);db.close();
        db=new ExpenseDb(context);eq(22,db.getReadableDatabase().getVersion());eq(1L,count("transactions"));eq(12345L,db.latestInserted().amountMinor);
        ok(db.latestInserted().timePrecision==null);
        long linked=db.putCommitmentFromTransaction(db.latestInserted().id,"fixture",12345,"EGP",LocalDate.now().plusMonths(1),"bill");ok(linked>=0);ok(db.commitmentOriginId(linked)!=null);
    }
    public void testConfirmedAbbreviatedSmsPreservesEvidenceAndReplay() {
        long when=at(LocalDate.now());String part="شكرًا لاستخدامك بطاقة بنك مصر ***5555، تم الآن خصم 255.01 EGPعند ES";
        String full=part+"PRESSO LAB يوم "+LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))+" ، الرصيد المتاح EGP 500.00";
        ok(db.addSms("Banque Misr",part,when));long partial=db.latestInserted().id;
        ok(db.addSms("Banque Misr",full,when+25000));long complete=db.latestInserted().id;eq(2L,count("transactions"));eq(1,db.abbreviatedSmsCopies(complete).size());
        long plan=db.putCommitmentFromTransaction(partial,"espresso fixture",25501,"EGP",LocalDate.now().plusMonths(1),"bill");
        db.confirmAbbreviatedSmsCopy(complete,partial);eq(1L,count("transactions"));eq(2L,count("capture_evidence"));eq(1L,count("transaction_archive"));eq(complete,db.commitmentOriginId(plan));
        ok(!db.addSms("Banque Misr",part,when));ok(!db.addSms("Banque Misr",full,when+25000));eq("Espresso Lab",db.transactionById(complete).merchant);eq(25501L,DailySummary.load(context,db).currencies.get("EGP").expense);
        ok(db.addSms("Banque Misr",full,when+600000));eq(2L,count("transactions"));
        try{db.confirmAbbreviatedSmsCopy(complete,db.latestInserted().id);throw new AssertionError("Unrelated purchase merged");}catch(IllegalArgumentException expected){}
    }
    private void saveScreen(File folder,String name) throws Exception {
        android.graphics.Bitmap bitmap=runner.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("No screenshot");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private void captureScreens() throws Exception {
        if(!"ranchu".equals(android.os.Build.HARDWARE)&&!"goldfish".equals(android.os.Build.HARDWARE))throw new SecurityException("Synthetic UI fixture is emulator-only");
        Context target=getTargetContext();
        if(!target.getPackageName().endsWith(".categoryqa"))throw new SecurityException("Captures require the isolated categoryqa app");
        long captureCard,captureUnlinked;
        try(ExpenseDb fixture=new ExpenseDb(target)) {
            if(!fixture.transactions().isEmpty())throw new IllegalStateException("UI fixture requires an empty emulator ledger");
            long when=at(LocalDate.now());long bank=fixture.addPaymentSource("بنك مصر","حساب","1111");
            long debit=fixture.addPaymentSource("بنك مصر","بطاقة","2222");captureCard=debit;fixture.linkCardToAccount(debit,bank);
            long separate=fixture.addPaymentSource("بنك مصر","بطاقة","5555");captureUnlinked=separate;
            long other=fixture.addPaymentSource("HSBC","حساب","3333");
            fixture.addManual(when,18550,"EGP","Carrefour","بقالة",debit);
            fixture.addManual(when+1000,6450,"EGP","Uber","مواصلات",debit);
            fixture.addManual(when+1100,8825,"EGP","Butlers","أكل وشرب",debit);
            fixture.addManual(when+1200,4525,"EGP","Espresso Lab","مقاهي",debit);
            fixture.addManual(when+1300,16000,"EGP","Cleopatra Hospital","رعاية صحية",debit);
            fixture.addManual(when+1400,3000,"EGP","QA SHOP","غير مصنف",separate);
            fixture.addManualEntry(when+1500,7000,"EGP","QA friend","تحويلات",bank,"expense",null,null);
            fixture.addManualEntry(when+1600,30000,"EGP","QA own transfer","تحويلات",other,"transfer",debit,null);
            fixture.addManual(when+2000,1000,"USD","Apple","اشتراكات");
            fixture.addManualEntry(when+3000,200000,"EGP","راتب تجريبي","دخل",bank,"income",null,null);
            fixture.addManual(at(LocalDate.now().minusDays(1)),9950,"EGP","Netflix","اشتراكات");
            LocalDate previous=LocalDate.now().minusMonths(1).withDayOfMonth(1);
            fixture.addManual(at(previous),20000,"EGP","Carrefour","بقالة",debit);
            fixture.addManual(at(previous.plusDays(10)),6000,"EGP","Espresso Lab","مقاهي",debit);
            fixture.addManualEntry(at(previous.plusDays(2)),90000,"EGP","QA previous inflow","دخل",bank,"income",null,null);
            fixture.addManual(at(previous.minusMonths(1)),40000,"EGP","Carrefour","بقالة",debit);
        }
        DailySummary.save(target,new DailyAllowance.Plan("EGP",50000,LocalDate.now(),25));
        DailySummary.save(target,new DailyAllowance.Plan("USD",2500,LocalDate.now(),0));
        String activityClass=MainActivity.class.getName(),prefix=target.getPackageName()+".";
        String activityPrefs=activityClass.startsWith(prefix)?activityClass.substring(prefix.length()):activityClass;
        target.getSharedPreferences(activityPrefs,0).edit().putString("opening_mode","off").putBoolean("intro_completed",true).putBoolean("sms_permission_prompted",true).putBoolean("initial_import_prompted",true).commit();
        target.getSharedPreferences("notification_settings",0).edit().putBoolean("access_prompted",true).commit();
        MainActivity activity=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        runner.waitForIdleSync();
        File captures=new File(target.getCacheDir(),"phase-one-captures");captures.mkdirs();
        saveScreen(captures,"home");
        java.lang.reflect.Method monthlyName=MainActivity.class.getDeclaredMethod("monthlyName",java.time.YearMonth.class);monthlyName.setAccessible(true);
        String readyTitle="ملخص "+monthlyName.invoke(activity,java.time.YearMonth.now().minusMonths(1))+" جاهز";
        ok(!runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(readyTitle).isEmpty());
        java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);
        java.lang.reflect.Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);
        java.lang.reflect.Field step=MainActivity.class.getDeclaredField("introStep");step.setAccessible(true);
        java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
        for(int i=0;i<3;i++){final int index=i;runner.runOnMainSync(()->{try{intro.setBoolean(activity,true);step.setInt(activity,index);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});runner.waitForIdleSync();android.os.SystemClock.sleep(300);saveScreen(captures,"intro-"+(i+1));}
        runner.runOnMainSync(()->{try{intro.setBoolean(activity,false);tab.setInt(activity,6);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});runner.waitForIdleSync();android.os.SystemClock.sleep(400);saveScreen(captures,"transactions");
        java.lang.reflect.Field report=MainActivity.class.getDeclaredField("insightsSection");report.setAccessible(true);
        for(String name:new String[]{"","categories","spending","flow","living"}) {
            runner.runOnMainSync(()->{try{tab.setInt(activity,4);report.set(activity,name);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,name.isEmpty()?"analytics":name);
        }
        java.lang.reflect.Field monthly=MainActivity.class.getDeclaredField("monthlyMonth");monthly.setAccessible(true);
        java.time.YearMonth previousMonth=java.time.YearMonth.now().minusMonths(1);
        runner.runOnMainSync(()->{try{tab.setInt(activity,15);monthly.set(activity,previousMonth);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"monthly-top");
        java.lang.reflect.Field monthPage=MainActivity.class.getDeclaredField("page");monthPage.setAccessible(true);
        runner.runOnMainSync(()->{try{((android.widget.ScrollView)((android.view.View)monthPage.get(activity)).getParent()).fullScroll(android.view.View.FOCUS_DOWN);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"monthly-bottom");
        android.view.accessibility.AccessibilityNodeInfo monthlyRoot=runner.getUiAutomation().getRootInActiveWindow();
        List<android.view.accessibility.AccessibilityNodeInfo> confirmCoverage=monthlyRoot.findAccessibilityNodeInfosByText("تأكيد اكتمال تسجيل الشهرين");
        confirmCoverage.removeIf(node->!node.isClickable());
        ok(confirmCoverage.size()==1 && confirmCoverage.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));runner.waitForIdleSync();
        monthlyRoot=runner.getUiAutomation().getRootInActiveWindow();
        List<android.view.accessibility.AccessibilityNodeInfo> acceptCoverage=monthlyRoot.findAccessibilityNodeInfosByText("التسجيل كامل");
        acceptCoverage.removeIf(node->!node.isClickable());
        ok(acceptCoverage.size()==1 && acceptCoverage.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));runner.waitForIdleSync();
        runner.runOnMainSync(()->{try{((android.widget.ScrollView)((android.view.View)monthPage.get(activity)).getParent()).fullScroll(android.view.View.FOCUS_DOWN);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"monthly-comparison");
        monthlyRoot=runner.getUiAutomation().getRootInActiveWindow();ok(!monthlyRoot.findAccessibilityNodeInfosByText("الصرف اليومي المسجل قل 35%").isEmpty());
        runner.runOnMainSync(()->{try{tab.setInt(activity,0);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);
        ok(runner.getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText(readyTitle).isEmpty());
        saveScreen(captures,"home-after-monthly");
        // Real NotificationManager + PendingIntent, entirely in the synthetic app; never capture the owner's notification shade.
        runner.getUiAutomation().grantRuntimePermission(target.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        android.app.NotificationManager notifications=target.getSystemService(android.app.NotificationManager.class);
        android.content.SharedPreferences monthPrefs=MonthlySummaryReceiver.settings(target);
        monthPrefs.edit().putBoolean("enabled",true).putString("start_month",previousMonth.toString()).remove("seen_month").remove("notified_month").commit();
        java.time.LocalDateTime due=java.time.YearMonth.now().atDay(1).atTime(9,0);
        MonthlySummaryReceiver.refreshAt(target,due);
        android.service.notification.StatusBarNotification monthlyNotification=null;
        for(android.service.notification.StatusBarNotification entry:notifications.getActiveNotifications())if(entry.getId()==MonthlySummaryReceiver.ID)monthlyNotification=entry;
        ok(monthlyNotification!=null);
        String notificationTitle=monthlyNotification.getNotification().extras.getString(android.app.Notification.EXTRA_TITLE);
        String notificationText=monthlyNotification.getNotification().extras.getString(android.app.Notification.EXTRA_TEXT);
        ok(notificationTitle.contains("ملخص") && !notificationText.contains("900") && !notificationText.contains("260") && !notificationText.contains("ج.م"));
        monthlyNotification.getNotification().contentIntent.send();runner.waitForIdleSync();
        eq(15,tab.getInt(activity));eq(previousMonth,monthly.get(activity));
        eq(previousMonth.toString(),monthPrefs.getString("seen_month",""));
        notifications.cancel(MonthlySummaryReceiver.ID);MonthlySummaryReceiver.refreshAt(target,due.plusDays(2));
        for(android.service.notification.StatusBarNotification entry:notifications.getActiveNotifications())ok(entry.getId()!=MonthlySummaryReceiver.ID);
        monthPrefs.edit().putBoolean("enabled",false).remove("seen_month").remove("notified_month").commit();MonthlySummaryReceiver.refreshAt(target,due);
        for(android.service.notification.StatusBarNotification entry:notifications.getActiveNotifications())ok(entry.getId()!=MonthlySummaryReceiver.ID);
        monthPrefs.edit().putBoolean("enabled",true).remove("seen_month").remove("notified_month").commit();
        MonthlySummaryReceiver.refreshAt(target,java.time.LocalDateTime.of(2030,1,1,9,0));
        ok(!monthPrefs.contains("notified_month"));
        monthPrefs.edit().putBoolean("enabled",true).putString("start_month",java.time.YearMonth.now().toString()).commit();
        runner.runOnMainSync(()->{try{tab.setInt(activity,5);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"settings");
        java.lang.reflect.Field pageField=MainActivity.class.getDeclaredField("page");pageField.setAccessible(true);
        runner.runOnMainSync(()->{try{((android.widget.ScrollView)((android.view.View)pageField.get(activity)).getParent()).fullScroll(android.view.View.FOCUS_DOWN);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"settings-signature");
        java.lang.reflect.Field flowSource=MainActivity.class.getDeclaredField("flowSourceId");flowSource.setAccessible(true);
        for(long source:new long[]{captureCard,captureUnlinked}) {
            runner.runOnMainSync(()->{try{tab.setInt(activity,14);flowSource.setLong(activity,source);render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,source==captureCard?"account-linked":"account-unlinked");
        }
        runner.runOnMainSync(()->{try{tab.setInt(activity,4);report.set(activity,"flow");render.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();
        runner.runOnMainSync(()->{try{((android.widget.ScrollView)((android.view.View)pageField.get(activity)).getParent()).fullScroll(android.view.View.FOCUS_DOWN);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"accounts");
        java.lang.reflect.Method opening=MainActivity.class.getDeclaredMethod("playOpening",boolean.class);opening.setAccessible(true);
        runner.runOnMainSync(()->{try{opening.invoke(activity,true);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(1800);saveScreen(captures,"opening-signature");
        java.lang.reflect.Field openingDialog=MainActivity.class.getDeclaredField("openingDialog");openingDialog.setAccessible(true);
        runner.runOnMainSync(()->{try{android.app.Dialog dialog=(android.app.Dialog)openingDialog.get(activity);if(dialog!=null)dialog.dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();
        ExpenseDb.Transaction coffee;
        try(ExpenseDb live=new ExpenseDb(target)) {
            coffee=live.transactions().stream().filter(tx->"Espresso Lab".equals(tx.merchant)).findFirst().orElseThrow(()->new IllegalStateException("Missing coffee fixture"));
        }
        java.lang.reflect.Method edit=MainActivity.class.getDeclaredMethod("editTransactionDialog",ExpenseDb.Transaction.class);edit.setAccessible(true);
        runner.runOnMainSync(()->{try{edit.invoke(activity,coffee);}catch(Exception e){throw new RuntimeException(e);}});runner.waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo dialog=runner.getUiAutomation().getRootInActiveWindow();
        List<android.view.accessibility.AccessibilityNodeInfo> choices=dialog.findAccessibilityNodeInfosByText("افتكر التصنيف للتاجر ده في العمليات الجديدة");
        ok(choices.size()==1 && choices.get(0).isCheckable());
        ok(choices.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));runner.waitForIdleSync();
        dialog=runner.getUiAutomation().getRootInActiveWindow();
        ok(dialog.findAccessibilityNodeInfosByText("افتكر التصنيف للتاجر ده في العمليات الجديدة").get(0).isChecked());
        saveScreen(captures,"learning");
        List<android.view.accessibility.AccessibilityNodeInfo> save=dialog.findAccessibilityNodeInfosByViewId("android:id/button1");
        ok(save.size()==1 && save.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));runner.waitForIdleSync();
        try(ExpenseDb live=new ExpenseDb(target)) {
            ok(live.merchantRules().stream().anyMatch(rule->"=espresso lab".equals(rule.matchText) && "مقاهي".equals(rule.category)));
        }
        android.os.SystemClock.sleep(2300); // Keep transient save toasts out of the marketing QA captures.
        runner.runOnMainSync(()->activity.finish());
        runner.waitForIdleSync();target.getSharedPreferences(activityPrefs,0).edit().putBoolean("dark_mode",true).commit();
        MainActivity night=(MainActivity)runner.startActivitySync(new android.content.Intent(target,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        runner.waitForIdleSync();android.os.SystemClock.sleep(300);saveScreen(captures,"home-dark");
        runner.runOnMainSync(()->{try{tab.setInt(night,4);report.set(night,"categories");render.invoke(night);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"categories-dark");
        runner.runOnMainSync(()->{try{tab.setInt(night,15);monthly.set(night,previousMonth);render.invoke(night);}catch(Exception e){throw new RuntimeException(e);}});
        runner.waitForIdleSync();android.os.SystemClock.sleep(250);saveScreen(captures,"monthly-dark");
        runner.runOnMainSync(()->night.finish());
        target.getSharedPreferences(activityPrefs,0).edit().putBoolean("dark_mode",false).commit();

    }
}
