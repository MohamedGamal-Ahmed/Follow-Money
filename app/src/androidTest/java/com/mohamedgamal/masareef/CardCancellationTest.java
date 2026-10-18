package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.app.Notification;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Synthetic fixtures only. Never runs on a physical device or touches the app ledger. */
final class CardCancellationTest {
    private final Instrumentation runner;
    private ExpenseDb db;private Context context;private File file;
    private final long time=System.currentTimeMillis()-60000;
    CardCancellationTest(Instrumentation runner){this.runner=runner;}
    private static void ok(boolean value){if(!value)throw new AssertionError("expected true");}
    private static void eq(long expected,long actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
    private interface Attempt{void run();}
    private static void rejects(Attempt action){try{action.run();throw new AssertionError("Invalid change accepted");}catch(IllegalArgumentException expected){}}
    private void setup(){
        Context target=runner.getTargetContext();file=new File(target.getCacheDir(),"card-cancellation.db");SQLiteDatabase.deleteDatabase(file);
        context=new ContextWrapper(target){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getDatabasePath(String name){return file;}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(file,factory);}
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(file.getPath(),factory,handler);}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return target.getSharedPreferences("card-qa-"+name,mode);}
        };
        context.getSharedPreferences("widget_settings",0).edit().clear().commit();db=new ExpenseDb(context);db.getWritableDatabase();
    }
    void run(){
        if(!"ranchu".equals(android.os.Build.HARDWARE)&&!"goldfish".equals(android.os.Build.HARDWARE))throw new SecurityException("Emulator only");
        StringBuilder report=new StringBuilder();int passed=0,failed=0;
        for(java.lang.reflect.Method method:getClass().getDeclaredMethods())if(method.getName().startsWith("test")){
            try{setup();method.invoke(this);passed++;report.append(method.getName()).append(": passed\n");}
            catch(Throwable error){failed++;Throwable cause=error.getCause()==null?error:error.getCause();report.append(method.getName()).append(": FAILED ").append(cause).append('\n');for(StackTraceElement frame:cause.getStackTrace())if(frame.getClassName().contains("masareef"))report.append(frame).append('\n');}
            finally{if(db!=null)db.close();if(file!=null)SQLiteDatabase.deleteDatabase(file);}
        }
        Bundle result=new Bundle();result.putString("stream",report+"Passed: "+passed+"; failed: "+failed+"\n");runner.finish(failed==0?-1:0,result);
    }
    private long scalar(String query){try(Cursor c=db.getReadableDatabase().rawQuery(query,null)){ok(c.moveToFirst());return c.getLong(0);}}
    private long add(long at,long amount,String merchant,String category,long source,String kind){ok(db.addManualEntry(at,amount,"EGP",merchant,category,source,kind,null,null));return db.latestInserted().id;}
    private long account(){return db.addPaymentSource("BANK","حساب","1111");}
    private long card(){return db.addPaymentSource("BANK","بطاقة ائتمان","2222");}
    private String debit(){return "تم تحويل مبلغ 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222";}
    private String cancel(){return "تم الغاء التحويل بمبلغ 227 جنيه من حساب رقم 1111";}
    private CaptureEvents.Event event(){try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id FROM capture_events ORDER BY id DESC LIMIT 1",null)){ok(c.moveToFirst());return new CaptureEvents(db).byId(c.getLong(0));}}
    public void testCancellationKeepsBothMessagesAndDoesNotErasePurchase(){
        long card=card(),purchase=add(time-172800000,22000,"QA SERVICE","اشتراكات",card,"expense");
        ok(db.addSms("BANK",debit(),time));long payment=db.lastCaptured().id;ok(db.addSms("BANK",cancel(),time+3000));
        ok(db.transactionById(payment)==null);ok(db.transactionById(purchase)!=null);eq(1,db.transactionCount());ok(event().cancelled());eq(2,new CaptureEvents(db).evidence(event()).size());
        eq(22000,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='expense'"));eq(1,scalar("SELECT COUNT(*) FROM transaction_archive"));ok(db.lastCaptured()==null);
        ok(!db.addSms("BANK",debit(),time));ok(!db.addSms("BANK",cancel(),time+3000));ok(!db.addSms("BANK",cancel(),time+4000));eq(1,scalar("SELECT COUNT(*) FROM capture_events"));ok(!db.addNotification("qa.bank.app","BANK",cancel(),time+5000));eq(1,scalar("SELECT COUNT(*) FROM capture_events"));
    }
    public void testCancelledTimelineUsesOriginalMonthAndSource(){
        long boundary=java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        ok(db.addSms("BANK",debit(),boundary-1000));ExpenseDb.Transaction original=db.lastCaptured();
        ok(db.addSms("BANK",cancel(),boundary+1000));CaptureEvents.Event cancelled=event();
        ok(cancelled.original!=null);eq(original.when,cancelled.displayWhen());eq(original.paymentSourceId,cancelled.original.paymentSourceId);
        eq(1,new CaptureEvents(db).timelineBetween(boundary-86400000,boundary).size());
        eq(0,new CaptureEvents(db).timelineBetween(boundary,boundary+86400000).size());eq(0,db.transactionCount());
    }
    public void testAutomaticImportIncludesCancellationAndSkipsDeclinesAndExcludedRows(){
        SmsPreview debit=SmsPreview.parse("BANK",debit(),time);
        SmsPreview cancellation=SmsPreview.parse("BANK",cancel(),time+3000);
        SmsPreview decline=SmsPreview.parse("BANK","تم رفض عملية خصم 15 جنيه من بطاقة رقم 2222",time+4000);
        SmsPreview excluded=SmsPreview.parse("BANK","Card purchase at QA EXCLUDED EGP 99",time);excluded.captureStatus=2;
        java.util.List<SmsPreview> rows=java.util.Arrays.asList(debit,cancellation,decline,excluded);SmsPreview.selectForImport(rows);
        java.util.List<SmsPreview> selected=new java.util.ArrayList<>();for(SmsPreview row:rows)if(row.selected)selected.add(row);
        eq(2,selected.size());eq(1,SmsImportActivity.applySelected(db,selected));eq(1,db.transactionCount());ok(!event().cancelled());ok("needs_confirmation".equals(event().status));eq(1,new CaptureEvents(db).pendingHistory(time-86_400_000L,time+86_400_000L).size());
        eq(0,SmsImportActivity.applySelected(db,selected));eq(1,scalar("SELECT COUNT(*) FROM capture_events"));eq(1,db.transactionCount());
        CaptureEvents store=new CaptureEvents(db);ExpenseDb.Transaction original=store.choices(event()).get(0);store.confirm(event().id,original.id,store.identity(original.id));eq(0,db.transactionCount());ok(event().cancelled());eq(2,store.evidence(event()).size());
    }
    public void testHistoricalCancellationCannotAutoResolveWhenDebitArrivesLater(){
        SmsPreview cancellation=SmsPreview.parse("BANK",cancel(),time+3000),debit=SmsPreview.parse("BANK",debit(),time);
        eq(1,SmsImportActivity.applySelected(db,java.util.Arrays.asList(cancellation,debit)));eq(1,db.transactionCount());ok("needs_confirmation".equals(event().status));
        ok(!db.addSms("BANK",debit(),time));eq(1,db.transactionCount());ok(!event().cancelled());
    }
    public void testHistoricalBalanceReceiptPreventsReapplication(){
        SmsPreview balance=SmsPreview.parse("BANK","Your card **1111 available balance EGP 1000",time);ok(balance!=null);
        eq(0,SmsImportActivity.applySelected(db,java.util.Collections.singletonList(balance)));eq(1,scalar("SELECT COUNT(*) FROM sms_import_receipts"));eq(1,db.smsCaptureStatus(balance.sender,balance.body,balance.receivedAt,balance.captureAt));
        db.getWritableDatabase().execSQL("UPDATE balance_snapshots SET balance_minor=77700");
        eq(0,SmsImportActivity.applySelected(db,java.util.Collections.singletonList(balance)));eq(77700,scalar("SELECT balance_minor FROM balance_snapshots LIMIT 1"));db.clearAllData();eq(0,scalar("SELECT COUNT(*) FROM sms_import_receipts"));
    }
    public void testSkippedMessageClearsCancellationStateBeforeNotification(){
        ok(db.addSms("BANK",debit(),time));ok(db.addSms("BANK",cancel(),time+3000));ok(db.lastCaptureEvent()!=null);
        ok(!db.addSms("BANK","Your OTP is 123456 for payment EGP 227",time+4000));ok(db.lastCaptureEvent()==null);
        ok(!db.addSms("BANK",null,time+5000));ok(db.lastCaptureEvent()==null);
    }
    public void testTransferFeeRejectionExplainsWhyAndPreservesBothRows(){
        long from=account(),card=card(),payment=add(time,22700,"QA PAYMENT","تحويلات",from,"expense"),fee=add(time,550,"QA TRANSFER FEE","رسوم تحويل",from,"expense");
        db.getWritableDatabase().execSQL("UPDATE transactions SET fee_parent_id=? WHERE id=?",new Object[]{payment,fee});
        try{new CardSettlements(db).preview(payment,from,card,java.util.Collections.emptyMap());throw new AssertionError("Fee repayment accepted");}catch(IllegalArgumentException error){ok(error.getMessage().contains("رسوم تحويل"));}
        eq(2,db.transactionCount());eq(550,db.transferFee(payment));ok(!new CardSettlements(db).isSettlement(payment));
    }
    public void testCorruptV21ArchivesDoNotBlockUpgradeOrLoseRawData(){
        ok(db.addSms("BANK",debit(),time));db.archiveEntry(db.lastCaptured().id,"bank confirmed cancellation",true);
        db.getWritableDatabase().execSQL("UPDATE transaction_archive SET payload='{broken JSON'");db.close();
        try(SQLiteDatabase sql=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){sql.execSQL("DROP TABLE capture_events");sql.execSQL("DROP TABLE sms_import_receipts");sql.execSQL("DROP TABLE card_settlements");sql.execSQL("DROP TABLE card_settlement_allocations");sql.setVersion(21);}
        db=new ExpenseDb(context);eq(24,db.getWritableDatabase().getVersion());eq(1,scalar("SELECT COUNT(*) FROM transaction_archive"));ok(event().cancelled());ok(event().amount==null);ok(event().original==null);ok(new CaptureEvents(db).evidence(event()).get(0).body==null);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT payload FROM transaction_archive",null)){ok(c.moveToFirst());ok("{broken JSON".equals(c.getString(0)));}
    }
    public void testV22TimelineUpgradeBackfillsDateAndUsesIndex(){
        ok(db.addSms("BANK",debit(),time));ok(db.addSms("BANK",cancel(),time+3000));long original=event().displayWhen();db.close();
        try(SQLiteDatabase sql=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){
            sql.execSQL("CREATE TABLE old_events(id INTEGER PRIMARY KEY AUTOINCREMENT,event_key TEXT NOT NULL UNIQUE,message_hash TEXT,status TEXT NOT NULL,amount_minor INTEGER,currency TEXT,title TEXT NOT NULL,received_at INTEGER NOT NULL,archive_id INTEGER,original_transaction_id INTEGER,sender TEXT,source TEXT,encrypted_body TEXT)");
            sql.execSQL("INSERT INTO old_events SELECT id,event_key,message_hash,status,amount_minor,currency,title,received_at,archive_id,original_transaction_id,sender,source,encrypted_body FROM capture_events");sql.execSQL("DROP TABLE capture_events");sql.execSQL("ALTER TABLE old_events RENAME TO capture_events");sql.execSQL("DROP TABLE sms_import_receipts");sql.setVersion(22);
        }
        db=new ExpenseDb(context);eq(24,db.getWritableDatabase().getVersion());eq(original,event().displayWhen());eq(1,new CaptureEvents(db).timelineBetween(original,original+1).size());
        try(Cursor c=db.getReadableDatabase().rawQuery("EXPLAIN QUERY PLAN SELECT id FROM capture_events WHERE display_at>=? AND display_at<? ORDER BY display_at DESC,id DESC",new String[]{Long.toString(original),Long.toString(original+1)})){ok(c.moveToFirst());ok(c.getString(3).contains("capture_event_display"));}
    }
    public void testOutOfOrderCancellationSettlesWhenEarlierDebitArrives(){
        ok(db.addSms("BANK",cancel(),time+3000));ok(!event().cancelled());ok(db.addSms("BANK",debit(),time));ok(event().cancelled());eq(0,db.transactionCount());eq(2,new CaptureEvents(db).evidence(event()).size());ok(db.lastCaptured()==null);
    }
    public void testAmbiguousCancellationRetainsBothDebits(){
        account();ok(db.addSms("BANK",debit()+" reference ABCDE1",time));ok(db.addSms("BANK",debit()+" reference ABCDE2",time+1000));
        ok(db.addSms("BANK",cancel(),time+3000));ok(!event().cancelled());eq(2,db.transactionCount());eq(0,scalar("SELECT COUNT(*) FROM transaction_archive"));
    }
    public void testConflictingReferenceAndOldDebitAreNotGuessed(){
        ok(db.addSms("BANK",debit()+" reference ABCDE1",time));ok(db.addSms("BANK",cancel()+" reference ABCDE2",time+3000));ok(!event().cancelled());eq(1,db.transactionCount());
        ok(db.addSms("BANK",cancel(),time+180000));ok(!event().cancelled());eq(1,db.transactionCount());
    }
    public void testEditedDebitIsRetainedForReview(){
        ok(db.addSms("BANK",debit(),time));long id=db.lastCaptured().id;db.getWritableDatabase().execSQL("UPDATE transactions SET user_edited=1 WHERE id=?",new Object[]{id});
        ok(db.addSms("BANK",cancel(),time+3000));ok(!event().cancelled());ok(db.transactionById(id)!=null);
    }
    public void testFeeAndRepaymentDoNotDoubleCountAndUndoSurvivesReopen(){
        long from=account(),card=card(),purchase=add(time-172800000,22000,"QA SERVICE","اشتراكات",card,"expense");CardSettlements store=new CardSettlements(db);
        long fee=store.addStatementFee(purchase,660,"FX markup confirmed in statement");eq(fee,store.addStatementFee(purchase,660,"FX markup confirmed in statement"));eq(22660,store.cost(purchase));
        long payment=add(time,22700,"QA PAYMENT","تحويلات",from,"expense");Map<Long,Long> selected=Collections.singletonMap(purchase,22660L);CardSettlements.Preview preview=store.preview(payment,from,card,selected);eq(40,preview.remainder);store.confirm(preview);
        eq(22660,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='expense'"));eq(22660,store.allocated(purchase));ok("transfer".equals(db.transactionById(payment).kind));eq(3,db.transactionCount());eq(1,db.sourceEvidence(fee).size());ok(db.sourceEvidence(fee).get(0).body.contains("FX markup confirmed"));
        db.close();db=new ExpenseDb(context);store=new CardSettlements(db);eq(22660,store.allocated(purchase));ok(store.undo(payment));eq(0,store.allocated(purchase));ok("expense".equals(db.transactionById(payment).kind));eq(660,db.transferFee(purchase));ok(!store.undo(payment));
    }
    public void testPartialAndMultiplePurchasesRespectOutstanding(){
        long from=account(),card=card(),a=add(time-10000,10000,"QA A","تسوق",card,"expense"),b=add(time-9000,20000,"QA B","تسوق",card,"expense");CardSettlements store=new CardSettlements(db);
        long p1=add(time,12000,"QA PAYMENT","تحويلات",from,"expense");Map<Long,Long> amounts=new LinkedHashMap<>();amounts.put(a,5000L);amounts.put(b,7000L);store.confirm(store.preview(p1,from,card,amounts));eq(5000,store.allocated(a));eq(7000,store.allocated(b));
        long p2=add(time+1,20000,"QA PAYMENT 2","تحويلات",from,"expense");rejects(()->store.preview(p2,from,card,Collections.singletonMap(a,5001L)));store.confirm(store.preview(p2,from,card,Collections.singletonMap(a,5000L)));eq(10000,store.allocated(a));eq(1,store.purchases(card,"EGP",time+2).size());
    }
    public void testStalePreviewAndOverAllocationAreRejectedAtomically(){
        long from=account(),card=card(),purchase=add(time-10000,22000,"QA SERVICE","اشتراكات",card,"expense"),payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);
        rejects(()->store.preview(payment,from,card,Collections.singletonMap(purchase,22700L)));CardSettlements.Preview preview=store.preview(payment,from,card,Collections.singletonMap(purchase,22000L));
        store.addStatementFee(purchase,660,"statement fee");rejects(()->store.confirm(preview));eq(0,store.allocated(purchase));ok("expense".equals(db.transactionById(payment).kind));
    }
    public void testWrongCurrencySourceCardAndFuturePurchaseAreRejected(){
        long from=account(),card=card(),other=db.addPaymentSource("OTHER","حساب","3333"),purchase=add(time+10000,22000,"QA FUTURE","تسوق",card,"expense"),payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);
        rejects(()->store.preview(payment,other,card,Collections.emptyMap()));rejects(()->store.preview(payment,from,other,Collections.emptyMap()));rejects(()->store.preview(payment,from,card,Collections.singletonMap(purchase,22000L)));
        db.getWritableDatabase().execSQL("UPDATE transactions SET occurred_at=?,currency='USD' WHERE id=?",new Object[]{time-10000,purchase});rejects(()->store.preview(payment,from,card,Collections.singletonMap(purchase,22000L)));
    }
    public void testCanceledConfirmedRepaymentReopensPurchaseAndPreservesFees(){
        long from=account(),card=card(),purchase=add(time-172800000,22000,"QA SERVICE","اشتراكات",card,"expense");CardSettlements store=new CardSettlements(db);store.addStatementFee(purchase,660,"statement FX fee");
        ok(db.addSms("BANK",debit(),time));long payment=db.lastCaptured().id;store.confirm(store.preview(payment,from,card,Collections.singletonMap(purchase,22660L)));
        ok(db.addSms("BANK",cancel(),time+3000));ok(event().cancelled());eq(0,store.allocated(purchase));eq(22660,store.cost(purchase));eq(2,db.transactionCount());
        eq(1,store.purchases(card,"EGP",time+4000).size());try(Cursor c=db.getReadableDatabase().rawQuery("SELECT payload FROM transaction_archive WHERE original_id=?",new String[]{Long.toString(payment)})){ok(c.moveToFirst());ok(c.getString(0).contains("card_settlement"));ok(c.getString(0).contains("22660"));}
    }
    public void testFeeCannotBecomeItsOwnPurchaseOrChangeAllocatedCost(){
        long from=account(),card=card(),purchase=add(time-10000,22000,"QA SERVICE","تسوق",card,"expense");CardSettlements store=new CardSettlements(db);long fee=store.addStatementFee(purchase,660,"FX fee");
        rejects(()->store.addStatementFee(fee,100,"fee of fee"));long payment=add(time,22700,"QA PAY","تحويلات",from,"expense");store.confirm(store.preview(payment,from,card,Collections.singletonMap(purchase,22660L)));
        rejects(()->store.addStatementFee(purchase,700,"changed fee"));rejects(()->db.updateTransaction(fee,700,"EGP","QA FEE","تسوق",card));eq(22660,store.cost(purchase));
    }
    public void testManualAmbiguousMatchRequiresUnchangedIdentity(){
        ok(db.addSms("BANK",debit()+" reference ABCDE1",time));long first=db.lastCaptured().id;ok(db.addSms("BANK",debit()+" reference ABCDE2",time+1000));long second=db.lastCaptured().id;
        ok(db.addSms("BANK",cancel(),time+3000));CaptureEvents store=new CaptureEvents(db);CaptureEvents.Event pending=event();eq(2,store.choices(pending).size());
        rejects(()->store.confirm(pending.id,first,"wrong-key"));eq(2,db.transactionCount());CaptureEvents.Event resolved=store.confirm(pending.id,first,store.identity(first));ok(resolved.cancelled());ok(db.transactionById(second)!=null);eq(1,db.transactionCount());eq(2,store.evidence(resolved).size());rejects(()->store.confirm(pending.id,second,store.identity(second)));
    }
    public void testAllocatedRecordsAndSourceCannotBeDeletedMergedOrEdited(){
        long from=account(),card=card(),purchase=add(time-10000,22000,"QA SERVICE","اشتراكات",card,"expense"),payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);long fee=store.addStatementFee(purchase,660,"statement fee");store.confirm(store.preview(payment,from,card,Collections.singletonMap(purchase,22660L)));
        rejects(()->db.deleteTransaction(fee));rejects(()->db.deleteTransaction(purchase));rejects(()->db.deleteTransaction(payment));rejects(()->db.updateTransaction(purchase,20000,"EGP","QA SERVICE","اشتراكات",card));rejects(()->db.deletePaymentSource(card));rejects(()->db.mergeSources(from,card));
        db.markAsTransfer(purchase);ok("expense".equals(db.transactionById(purchase).kind));eq(0,db.updateCategories(java.util.Arrays.asList(purchase,fee),"أخرى"));eq(22660,store.cost(purchase));ok(store.undo(payment));db.deleteTransaction(fee);eq(22000,store.cost(purchase));
    }
    public void testCancellationWithoutAmountIsVisibleButNeverDeletes(){
        ok(db.addSms("BANK",debit(),time));ok(db.addSms("BANK","تم الغاء التحويل من حساب رقم 1111",time+3000));ok(!event().cancelled());ok(event().amount==null);eq(1,db.transactionCount());eq(1,new CaptureEvents(db).evidence(event()).size());
    }
    public void testMissingOriginalProofDoesNotAutoCancelManualRepayment(){
        long from=account(),card=card(),payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);store.confirm(store.preview(payment,from,card,Collections.emptyMap()));
        ok(db.addSms("BANK",cancel(),time+3000));ok(!event().cancelled());ok(db.transactionById(payment)!=null);ok(store.isSettlement(payment));
    }
    public void testRepeatedCancellationAfterASecondRealDebitIsNotSuppressed(){
        ok(db.addSms("BANK",debit(),time));ok(db.addSms("BANK",cancel(),time+3000));ok(event().cancelled());long firstEvent=event().id;
        ok(db.addSms("BANK",debit(),time+5000));ok(db.addSms("BANK",cancel(),time+8000));ok(event().cancelled());ok(event().id!=firstEvent);eq(2,scalar("SELECT COUNT(*) FROM capture_events"));eq(0,db.transactionCount());
    }
    public void testReimportDoesNotRewriteAllocatedPurchaseAndKeepsProof(){
        long from=account(),card=card();String sms="Card purchase EGP 220 at QA SERVICE with credit card ending 2222";ok(db.addSms("BANK",sms,time-172800000));long purchase=db.lastCaptured().id;long payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);store.confirm(store.preview(payment,from,card,Collections.singletonMap(purchase,22000L)));
        ok(!db.addSms("BANK",sms,time-172799000));eq(22000,store.allocated(purchase));ok("expense".equals(db.transactionById(purchase).kind));eq(22000,db.transactionById(purchase).amountMinor);eq(2,db.sourceEvidence(purchase).size());
    }
    public void testCancellationNeverConsumesAnUnrelatedPurchase(){
        String body="تم خصم مبلغ 227 جنيه من حساب رقم 1111 عند QA SHOP";ok(db.addSms("BANK",body,time));long purchase=db.lastCaptured().id;ok(db.addSms("BANK",cancel(),time+3000));ok(!event().cancelled());ok(db.transactionById(purchase)!=null);eq(1,db.transactionCount());
    }
    public void testRenamedCardInvalidatesTheReviewedDestination(){
        long from=account(),card=card(),payment=add(time,22700,"QA PAY","تحويلات",from,"expense");CardSettlements store=new CardSettlements(db);CardSettlements.Preview preview=store.preview(payment,from,card,Collections.emptyMap());db.updateSourceProfile(card,"Changed card",false,true);rejects(()->store.confirm(preview));ok(!store.isSettlement(payment));
    }
    public void testLegacyCancellationMigrationKeepsArchiveAndDistinguishesMissingProof(){
        ok(db.addSms("BANK",debit(),time));long id=db.lastCaptured().id;db.archiveEntry(id,"bank confirmed cancellation",true);long archives=scalar("SELECT COUNT(*) FROM transaction_archive");db.close();
        try(SQLiteDatabase sql=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE)){sql.execSQL("DROP TABLE capture_events");sql.execSQL("DROP TABLE card_settlements");sql.execSQL("DROP TABLE card_settlement_allocations");sql.setVersion(21);}
        db=new ExpenseDb(context);eq(24,db.getWritableDatabase().getVersion());eq(archives,scalar("SELECT COUNT(*) FROM transaction_archive"));ok(event().cancelled());ok(event().body==null);eq(1,new CaptureEvents(db).evidence(event()).size());
        db.close();db=new ExpenseDb(context);eq(1,scalar("SELECT COUNT(*) FROM capture_events"));eq(0,db.transactionCount());
    }
    public void testContactlessLastUseWarningIsOnePurchaseAndDeclineIsZero(){
        String success="تم خصم مبلغ 15 جنيه من بطاقتك رقم 2222 عند QA CAFE";ok(db.addSms("BANK",success,time));
        ok(!db.addSms("BANK","هذه آخر حركة لا تلامسية بقيمة 15 جنيه. استخدم الرقم السري في الحركة القادمة",time+1000));
        ok(!db.addSms("BANK","تم رفضها عملية خصم مبلغ 15 جنيه من بطاقتك رقم 2222 لتخطي عدد المرات اللاتلامسي",time+2000));eq(1,db.transactionCount());eq(1500,db.latestInserted().amountMinor);eq(0,scalar("SELECT COUNT(*) FROM capture_events"));
    }
    public void testCancellationNotificationReplacesRecordedIdAndHidesAmount(){
        ok(db.addSms("BANK",debit(),time));long id=db.lastCaptured().id;ok(db.addSms("BANK",cancel(),time+3000));CaptureEvents.Event event=event();
        eq(3_000_000+id%1_000_000,CaptureNotifier.eventNotificationId(event));Notification notice=CaptureNotifier.buildEvent(context,event);eq(Notification.VISIBILITY_PRIVATE,notice.visibility);ok(notice.actions==null||notice.actions.length==0);ok(notice.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("227"));
        ok(!notice.publicVersion.extras.toString().contains("227"));context.getSharedPreferences("widget_settings",0).edit().putBoolean("hide_amounts",true).commit();ok(!CaptureNotifier.buildEvent(context,event).extras.toString().contains("227"));ok(notice.contentIntent!=null);
    }
}
