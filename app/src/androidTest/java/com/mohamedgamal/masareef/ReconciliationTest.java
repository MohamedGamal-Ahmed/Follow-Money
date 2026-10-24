package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Tests use isolated database files. Explicit confirmed_plan mode applies a reviewed user plan. */
public final class ReconciliationTest extends Instrumentation {
    private boolean realFixture;
    private String featureAudit;
    private boolean journeyUi;
    private boolean phaseOne,phaseCapture;
    private String auditSources;
    private boolean auditDaily;
    private String inspectIds;
    private String confirmedPlan;
    private String confirmedCardLink;
    private boolean captureBalances;
    private boolean captureRing;
    @Override public void onCreate(Bundle args) { super.onCreate(args);journeyUi="true".equals(args.getString("journey_ui"));featureAudit=args.getString("feature_audit");realFixture="true".equals(args.getString("fixture"));inspectIds=args.getString("inspect");confirmedPlan=args.getString("confirmed_plan");confirmedCardLink=args.getString("confirmed_card_link");captureBalances="true".equals(args.getString("capture_balances"));captureRing="true".equals(args.getString("capture_ring"));phaseOne="true".equals(args.getString("phase_one"));phaseCapture="true".equals(args.getString("phase_capture"));auditSources=args.getString("audit_sources");auditDaily="true".equals(args.getString("audit_daily"));start(); }
    @Override public void onStart() {
        if("classification_lag".equals(featureAudit)){new ClassificationLagTest(this).run();return;}
        if(featureAudit!=null&&featureAudit.startsWith("source_link_")){new SourceLinkUiTest(this).run(!"source_link_light".equals(featureAudit),"source_link_max".equals(featureAudit)?2f:1f);return;}
        if(featureAudit!=null&&featureAudit.startsWith("transactions_")){new TransactionPagesUiTest(this).run(!"transactions_light".equals(featureAudit),"transactions_max".equals(featureAudit)?2f:1f);return;}
        if(featureAudit!=null&&featureAudit.startsWith("unified_")){
            new UnifiedUiTest(this).run(!"unified_light".equals(featureAudit),"unified_max".equals(featureAudit)?2f:1f);return;
        }
        if("points_ui".equals(featureAudit)){new DailyPointsUiTest(this).run();return;}
        if("notification_capture".equals(featureAudit)){new NotificationCaptureTest(this).run();return;}
        if("shadow_store".equals(featureAudit)){new ShadowStoreTest(this).run();return;}
        if("merchant_badges".equals(featureAudit)){new MerchantBadgeUiTest(this).run();return;}
        if("commitment_ui".equals(featureAudit)){new CommitmentPaymentUiTest(this).run();return;}
        if("commitment_cycle_ui".equals(featureAudit)){new CommitmentCycleUiTest(this).run();return;}
        if("period_ui".equals(featureAudit)){new PeriodFilterUiTest(this).run();return;}
        if("hsbc_ui".equals(featureAudit)){new HsbcFormatUiTest(this).run();return;}
        if("statement_ui".equals(featureAudit)){new StatementUiTest(this).run();return;}
        if("app_lock".equals(featureAudit)||"app_lock_no_secure".equals(featureAudit)){
            new AppLockUiTest(this,featureAudit).run();return;
        }
        if("whats_new".equals(featureAudit)){new WhatsNewUiTest(this).run();return;}
        if(featureAudit!=null&&featureAudit.startsWith("support_")){new SupportAidsUiTest(this).run(featureAudit);return;}
        if(featureAudit!=null&&featureAudit.startsWith("backup_")){new BackupRestoreTest(this,featureAudit).run();return;}
        if("lifetime".equals(featureAudit)){new ResourceLifetimeTest(this).run();return;}
        if(featureAudit!=null&&featureAudit.startsWith("review_alerts_")){new ReviewAlertsUiTest(this).run(!"review_alerts_light".equals(featureAudit),"review_alerts_large".equals(featureAudit)?1.3f:1f);return;}
        if(featureAudit!=null&&featureAudit.startsWith("sms_ui_")){new SmsImportUiTest(this).run(!"sms_ui_light".equals(featureAudit),"sms_ui_large".equals(featureAudit)?1.3f:1f);return;}
        if(featureAudit!=null&&featureAudit.startsWith("menus_")){new SelectionUiTest(this).run(!"menus_light".equals(featureAudit),"menus_max".equals(featureAudit)?2f:"menus_large".equals(featureAudit)?1.3f:1f);return;}
        if("card_ui_large".equals(featureAudit)){new CardJourneyUiTest(this).run(true,1.3f);return;}
        if("card_ui_light".equals(featureAudit)||"card_ui_dark".equals(featureAudit)){new CardJourneyUiTest(this).run("card_ui_dark".equals(featureAudit));return;}
        if("card".equals(featureAudit)){new CardCancellationTest(this).run();return;}
        if("visual".equals(featureAudit)){new ReleaseVisualTest(this).run();return;}
        if("regressions".equals(featureAudit)){new AuditRegressionTest(this).run();return;}
        if(journeyUi){new JourneyUiTest(this).run();return;}
        if (featureAudit != null) { new FeatureAuditTest(this).run(featureAudit); return; }
        if(captureBalances) {
            Bundle result=new Bundle();
            try{setUp();captureBalancesFixture();result.putString("stream","Isolated emulator balances preview saved; user ledger unchanged\n");finish(-1,result);}
            catch(Throwable error){result.putString("stream",error.toString());finish(0,result);}
            finally{try{tearDown();}catch(Exception ignored){}}
            return;
        }
        if(confirmedCardLink!=null){applyConfirmedCardLink();return;}
        if(auditDaily){auditDaily();return;}
        if(auditSources!=null){auditSources();return;}
        if(phaseOne){new PhaseOneTest(this,phaseCapture).run();return;}
        if(captureRing) {
            Bundle result=new Bundle();
            try {setUp();captureRingFixture();result.putString("stream","Ring centered in RTL; isolated fixture capture saved\n");finish(-1,result);}
            catch(Throwable error){result.putString("stream",error.toString());finish(0,result);}
            finally {try{tearDown();}catch(Exception ignored){}}
            return;
        }
        if(confirmedPlan!=null) {
            Bundle result=new Bundle();
            try(ExpenseDb live=new ExpenseDb(getTargetContext())) {
                File file=new File(getTargetContext().getCacheDir(),"audit-fixture/"+confirmedPlan);
                applyPlan(live,new org.json.JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)));
                result.putString("stream","User-confirmed reconciliation applied\n");finish(-1,result);
            }catch(Exception error){result.putString("stream",error.toString());finish(0,result);}
            return;
        }
        if(inspectIds!=null) {
            Bundle result=new Bundle();
            try {
                setUp();db.close();
                File fixture=new File(getTargetContext().getCacheDir(),"audit-fixture");
                Files.copy(new File(fixture,"live-sources.db").toPath(),isolated.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);db=new ExpenseDb(context);
                StringBuilder sources=new StringBuilder();
                for(String id:inspectIds.split(",")) for(ExpenseDb.SourceEvidence e:db.sourceEvidence(Long.parseLong(id))) {
                    sources.append(id).append('\t').append(e.source).append('\t').append(e.sender).append('\t').append(e.receivedAt).append('\t')
                            .append(Base64.encodeToString(e.body.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP)).append('\n');
                }
                Files.write(new File(fixture,"selected-sources.tsv").toPath(),sources.toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream","Selected sources exported from a private isolated snapshot\n");finish(-1,result);
            } catch(Exception error) {result.putString("stream",error.toString());finish(0,result);}
            finally {try{tearDown();}catch(Exception ignored){}}
            return;
        }
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();int passed=0,failed=0;
        for(java.lang.reflect.Method method:getClass().getDeclaredMethods()) {
            if(!method.getName().startsWith("test")) continue;
            if((method.getName().equals("testRealSnapshotReplay") || method.getName().equals("testConfirmedTodayPlan")) && !realFixture) {report.append(method.getName()+": skipped (no private fixture requested)\n");continue;}
            try { setUp();method.invoke(this);passed++;report.append(method.getName()).append(": passed\n"); }
            catch(Throwable error) { failed++;Throwable cause=error.getCause()==null?error:error.getCause();
                report.append(method.getName()).append(": FAILED ").append(cause).append("\n");
                for(StackTraceElement frame:cause.getStackTrace()) if(frame.getClassName().contains("masareef")) report.append(frame).append("\n");
            }
            finally { try { tearDown(); } catch(Exception ignored) {} }
            Bundle progress=new Bundle();progress.putString("stream",method.getName()+" completed\n");sendStatus(0,progress);
        }
        result.putString("stream",report+"\nPassed: "+passed+"; failed: "+failed+"\n");finish(failed==0?-1:0,result);
    }
    private static void assertTrue(boolean value) { if(!value) throw new AssertionError("expected true"); }
    /** Explicit, user-confirmed relationship only; this mode never creates or edits financial rows. */
    private void applyConfirmedCardLink() {
        Bundle result=new Bundle();
        try(ExpenseDb live=new ExpenseDb(getTargetContext())) {
            String[] ids=confirmedCardLink.split(",");if(ids.length!=2)throw new IllegalArgumentException("card,account required");
            long card=Long.parseLong(ids[0]),account=Long.parseLong(ids[1]);live.linkCardToAccount(card,account);
            ExpenseDb.BalanceSnapshot balance=live.currentBalance(account,"EGP");
            SmsReceiver.notifyDataChanged(getTargetContext());
            result.putString("stream","Confirmed card/account relationship saved; account="+live.sourceLabel(account)+
                    "; card="+live.sourceLabel(card)+"; balance_minor="+(balance==null?"unavailable":balance.amountMinor)+
                    "; estimated="+(balance!=null && balance.estimated)+"; financial rows unchanged by this operation\n");
            finish(-1,result);
        }catch(Exception error){result.putString("stream",error.toString());finish(0,result);}
    }
    /** Explicit diagnostic: read sources for named IDs, write a private cache artifact, no ledger edits. */
    private void auditSources() {
        Bundle result=new Bundle();
        try(ExpenseDb live=new ExpenseDb(getTargetContext())) {
            org.json.JSONArray records=new org.json.JSONArray();
            String[] ids=auditSources.split(",");if(ids.length>20)throw new IllegalArgumentException("At most20 IDs");
            for(String id:ids)for(ExpenseDb.SourceEvidence source:live.sourceEvidence(Long.parseLong(id))) {
                if(ExpenseParser.isVerificationMessage(source.body))continue;
                org.json.JSONObject row=new org.json.JSONObject();row.put("id",Long.parseLong(id));row.put("sender",source.sender);row.put("received_at",source.receivedAt);row.put("source",source.source);row.put("body",source.body);records.put(row);
            }
            File output=new File(getTargetContext().getCacheDir(),"private-source-audit.json");
            Files.write(output.toPath(),records.toString().getBytes(StandardCharsets.UTF_8));
            result.putString("stream","Read-only audit saved privately; records: "+records.length()+"; ledger unchanged\n");finish(-1,result);
        }catch(Exception error){result.putString("stream",error.toString());finish(0,result);}
    }
    private void auditDaily() {
        Bundle result=new Bundle();
        try(ExpenseDb live=new ExpenseDb(getTargetContext())) {
            DailySummary summary=DailySummary.load(getTargetContext(),live);StringBuilder text=new StringBuilder();
            text.append("Local day: ").append(summary.date).append("; zone: ").append(summary.zone).append('\n');
            for(java.util.Map.Entry<String,DailyAllowance.Result> entry:summary.currencies.entrySet()) {
                DailyAllowance.Result day=entry.getValue();text.append(entry.getKey()).append(" expense_minor=").append(day.expense).append(" income_minor=").append(day.income)
                        .append(" limit_minor=").append(day.valid?day.plan.limit:0).append(" remaining_minor=").append(day.remaining).append(" days_to_salary=").append(day.daysToSalary).append('\n');
            }
            result.putString("stream",text+"Read only; no ledger/settings writes\n");finish(-1,result);
        }catch(Exception error){result.putString("stream",error.toString());finish(0,result);}
    }
    private static void assertFalse(boolean value) { if(value) throw new AssertionError("expected false"); }
    private static void assertNull(Object value) { if(value!=null) throw new AssertionError("expected null"); }
    private static void assertNotNull(Object value) { if(value==null) throw new AssertionError("expected non-null"); }
    private static void assertEquals(long expected,long actual) { if(expected!=actual) throw new AssertionError("expected "+expected+", actual "+actual); }
    private static void assertEquals(Object expected,Object actual) { if(!java.util.Objects.equals(expected,actual)) throw new AssertionError("expected "+expected+", actual "+actual); }
    private ExpenseDb db;
    private Context context;
    private File isolated;
    private static final long AT=LocalDate.of(2026,10,10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    private void setUp() throws Exception {
        Context target=getTargetContext();
        isolated=new File(target.getCacheDir(),"reconciliation-test.db");
        SQLiteDatabase.deleteDatabase(isolated);
        context=new ContextWrapper(target) {
            @Override public File getDatabasePath(String name) { return isolated; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(isolated,factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(isolated.getPath(),factory,handler);
            }
        };
        db=new ExpenseDb(context);
    }
    private void tearDown() throws Exception {
        db.close();SQLiteDatabase.deleteDatabase(isolated);
    }
    private long scalar(String sql) {
        try(Cursor c=db.getReadableDatabase().rawQuery(sql,null)) { assertTrue(c.moveToFirst());return c.getLong(0); }
    }
    private void captureBalancesFixture() throws Exception {
        if(!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE))
            throw new SecurityException("Synthetic balances preview is emulator-only");
        long account=db.addPaymentSource("Banque Misr","حساب","7392");
        db.addSms("Banque Misr","Card xxx4636 purchase EGP15 at TEST SHOP. Available balance EGP7240.01",AT+8L*3600000);
        long card=db.latestInserted().paymentSourceId;db.linkCardToAccount(card,account);db.updateSourceProfile(account,"حساب المصروفات",false,true);
        db.addManual(AT+15L*3600000,15000,"EGP","شخص آخر","تحويلات",account);
        long other=db.addPaymentSource("HSBC","حساب","001");db.putManualBalance(other,"EGP",3032,AT+7L*3600000);
        long credit=db.addPaymentSource("Banque Misr","بطاقة ائتمان","2700");db.putManualBalance(credit,"EGP",1189170,AT);
        long old=db.addPaymentSource("Banque Misr","بطاقة","4700");db.updateSourceProfile(old,"كارت قديم",true,false);
        MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("db");field.setAccessible(true);ExpenseDb original=(ExpenseDb)field.get(activity);
        try {
            runOnMainSync(()->{
                try{field.set(activity,db);java.lang.reflect.Field intro=MainActivity.class.getDeclaredField("introVisible");intro.setAccessible(true);intro.setBoolean(activity,false);
                    java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,1);
                    java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
                }catch(Exception e){throw new RuntimeException(e);}
            });waitForIdleSync();
            runOnMainSync(()->{
                try{
                    android.view.View decor=activity.getWindow().getDecorView();
                    assertNotNull(findText(decor,"كارت مرتبط •4636 · آخر استخدام "+java.time.Instant.ofEpochMilli(AT+8L*3600000).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",java.util.Locale.ROOT))));
                    android.graphics.Bitmap capture=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);decor.draw(new android.graphics.Canvas(capture));
                    try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(getTargetContext().getCacheDir(),"balances-fixture.png"))){capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{capture.recycle();}
                }catch(Exception e){throw new RuntimeException(e);}
            });
        }finally{runOnMainSync(()->{try{field.set(activity,original);activity.finish();}catch(Exception ignored){}});}
    }
    /** Renders the real screen with an isolated plan; the user's saved commitments are never edited. */
    private void captureRingFixture() throws Exception {
        long id=db.putCommitment(null,"TRU — إجمالي الأقساط",159300,"EGP",LocalDate.of(2026,11,1),"monthly","installment",24,14,1,"tru",null);
        MainActivity activity=(MainActivity)startActivitySync(new android.content.Intent(getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("db");field.setAccessible(true);
        ExpenseDb original=(ExpenseDb)field.get(activity);
        runOnMainSync(() -> {
            try {
                field.set(activity,db);
                java.lang.reflect.Field tab=MainActivity.class.getDeclaredField("selectedTab");tab.setAccessible(true);tab.setInt(activity,11);
                java.lang.reflect.Field selected=MainActivity.class.getDeclaredField("selectedCommitmentId");selected.setAccessible(true);selected.setLong(activity,id);
                java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);render.invoke(activity);
            } catch(Exception error) {throw new RuntimeException(error);}
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            try {
                android.view.View decor=activity.getWindow().getDecorView();
                android.widget.TextView count=findText(decor,"14/24");assertNotNull(count);
                assertEquals(android.view.Gravity.CENTER_HORIZONTAL,count.getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK);
                android.view.View parent=(android.view.View)count.getParent();
                assertEquals(parent.getWidth(),count.getWidth());
                android.graphics.Bitmap capture=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                decor.draw(new android.graphics.Canvas(capture));
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(getTargetContext().getCacheDir(),"ring-fixture.png"))) {
                    capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                }
                capture.recycle();
            } catch(Exception error) {throw new RuntimeException(error);}
            finally {
                try {field.set(activity,original);activity.finish();}catch(Exception ignored){}
            }
        });
    }
    private static android.widget.TextView findText(android.view.View view,String text) {
        if(view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)view).getText())) return (android.widget.TextView)view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {android.widget.TextView found=findText(group.getChildAt(i),text);if(found!=null)return found;}
        }
        return null;
    }
    public void testCompletedAccountTransferAndReplay() {
        String body="تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم إلى شخص ا******* م*** رقم مرجعي 123456789012 يوم 2026-10-11 الساعه 15:27 للمزيد اتصل ب 19888";
        long received=java.time.LocalDateTime.of(2026,10,11,15,27,31).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long account=db.addPaymentSource("Banque Misr","حساب","7392");
        assertTrue(db.addSms("Banque Misr",body,received));
        ExpenseDb.Transaction row=db.latestInserted();
        assertEquals(15000,row.amountMinor);assertEquals("EGP",row.currency);
        assertEquals("expense",row.kind);assertEquals("تحويلات",row.category);
        assertEquals("شخص ا******* م***",row.merchant);assertEquals(Long.valueOf(account),row.paymentSourceId);
        assertEquals(received-31000,row.when);assertEquals(1,db.sourceEvidence(row.id).size());
        assertFalse(db.addSms("Banque Misr",body,received));
        assertEquals(1,db.transactions().size());assertEquals(1,db.sourceEvidence(row.id).size());
    }
    public void testLinkedCardBalanceTimelineAndSeparateCurrency() {
        long account=db.addPaymentSource("Banque Misr","حساب","7392");
        String anchor="Card xxx4636 purchase EGP15 at TEST SHOP. Available balance EGP7240.01";
        assertTrue(db.addSms("Banque Misr",anchor,AT));
        long card=db.latestInserted().paymentSourceId;
        assertTrue(db.addSms("Banque Misr","تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم إلى شخص رقم مرجعي 123456789012",AT+10000));
        long rows=scalar("SELECT COUNT(*) FROM transactions"),evidence=scalar("SELECT COUNT(*) FROM capture_evidence");
        assertNull(db.currentBalance(account,"EGP"));
        db.linkCardToAccount(card,account);
        assertEquals(rows,scalar("SELECT COUNT(*) FROM transactions"));assertEquals(evidence,scalar("SELECT COUNT(*) FROM capture_evidence"));
        assertEquals(Long.valueOf(account),db.paymentSource(card).parentSourceId);
        ExpenseDb.BalanceSnapshot balance=db.currentBalance(account,"EGP");
        assertEquals(709001,balance.amountMinor);assertEquals(card,balance.anchorSourceId);assertEquals(1,balance.movementCount);
        assertTrue(balance.estimated);assertEquals(balance.amountMinor,db.currentBalance(card,"EGP").amountMinor);
        assertTrue(db.addManual(AT+20000,1000,"EGP","later card payment","أخرى",card));
        assertTrue(db.addManualEntry(AT+30000,2000,"EGP","deposit","دخل",account,"income",null,null));
        assertTrue(db.addManual(AT+40000,500,"USD","other currency","أخرى",card));
        assertEquals(710001,db.currentBalance(account,"EGP").amountMinor);assertNull(db.currentBalance(account,"USD"));
        assertFalse(db.addManualEntry(AT+50000,1000,"EGP","same balance","تحويلات",card,"transfer",account,null));
        assertFalse(db.addSms("Banque Misr",anchor,AT));assertEquals(710001,db.currentBalance(account,"EGP").amountMinor);
        assertEquals(1,db.balanceHistory(account,"EGP",10).size());
        db.unlinkCard(card);assertNull(db.currentBalance(account,"EGP"));assertEquals(723001,db.currentBalance(card,"EGP").amountMinor);
    }
    public void testManualBalanceHistoryKeepsZeroAndDeterministicAnchorOrder() {
        long account=db.addPaymentSource("BM","حساب","7392"),card=db.addPaymentSource("BM","بطاقة","4636");
        db.linkCardToAccount(card,account);
        assertNull(db.latestBalance(card,"EGP"));assertNull(db.currentBalance(account,"USD"));
        db.putManualBalance(account,"EGP",10000,AT);
        db.putManualBalance(card,"EGP",0,AT);
        db.putManualBalance(account,"USD",900,AT);
        // Deliberately equal bank/receipt clocks: insertion order must resolve the two EGP anchors.
        db.getWritableDatabase().execSQL("UPDATE balance_snapshots SET received_at=?",new Object[]{AT+100});
        db.putManualBalance(card,"EGP",-1,AT+1000);
        List<ExpenseDb.BalanceSnapshot> history=db.balanceHistory(account,"EGP",1);
        assertEquals(1,history.size());assertEquals(0,history.get(0).amountMinor);
        assertEquals(2,db.balanceHistory(card,"EGP",10).size());
        ExpenseDb.BalanceSnapshot latest=db.latestBalance(account,"EGP");
        assertEquals(0,latest.amountMinor);assertEquals(card,latest.anchorSourceId);
        assertEquals(AT,latest.occurredAt);assertEquals(AT+100,latest.receivedAt);
        assertEquals("يدوي",latest.source);assertFalse(latest.estimated);assertEquals(0,latest.movementCount);
        assertEquals(0,db.currentBalance(card,"EGP").amountMinor);
        assertEquals(900,db.currentBalance(account,"USD").amountMinor);
        assertEquals(3,scalar("SELECT COUNT(*) FROM balance_snapshots"));assertEquals(0,db.transactionCount());
    }

    public void testBalanceCutoffUsesEventTimeThenReceiptWithoutCrossCurrencyLeak() {
        long account=db.addPaymentSource("BM","حساب","7392"),card=db.addPaymentSource("BM","بطاقة","4636");
        long other=db.addPaymentSource("HSBC","حساب","001");db.linkCardToAccount(card,account);
        db.putManualBalance(card,"EGP",10000,AT);
        long receipt=db.latestBalance(account,"EGP").receivedAt;
        long[] clocks={receipt-1,receipt,receipt+1};long[] amounts={100,200,300};
        for(int i=0;i<clocks.length;i++) {
            assertTrue(db.addManualEntry(AT,amounts[i],"EGP","QA cutoff "+i,"تسوق",card,"expense",null,null));
            db.getWritableDatabase().execSQL("UPDATE transactions SET received_at=? WHERE id=?",new Object[]{clocks[i],db.latestInserted().id});
        }
        assertTrue(db.addManualEntry(AT+2000,500,"EGP","QA later event","دخل",account,"income",null,null));
        db.getWritableDatabase().execSQL("UPDATE transactions SET received_at=? WHERE id=?",new Object[]{receipt-1,db.latestInserted().id});
        assertTrue(db.addManualEntry(AT+3000,700,"USD","QA other currency","تسوق",card,"expense",null,null));
        assertTrue(db.addManualEntry(AT+4000,900,"EGP","QA other account","تسوق",other,"expense",null,null));
        ExpenseDb.BalanceSnapshot current=db.currentBalance(card,"EGP");
        assertEquals(10200,current.amountMinor);assertTrue(current.estimated);assertEquals(2,current.movementCount);
        assertEquals(AT,current.occurredAt);assertEquals(receipt,current.receivedAt);assertEquals(AT+2000,current.updatedAt);
        assertEquals(card,current.anchorSourceId);assertEquals("يدوي",current.source);
        assertEquals(10000,db.latestBalance(account,"EGP").amountMinor);assertNull(db.currentBalance(card,"USD"));
        assertEquals(6,db.transactionCount());assertEquals(1,db.balanceHistory(account,"EGP",10).size());
    }

    public void testBalanceWritesBorrowOuterTransactionAndRollBackWithItsLedger() {
        long account=db.addPaymentSource("BM","حساب","7392");db.putManualBalance(account,"EGP",10000,AT);
        SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try {
            db.putManualBalance(account,"EGP",20000,AT+1000);
            assertTrue(db.addManualEntry(AT+2000,1000,"EGP","QA rollback","تسوق",account,"expense",null,null));
            assertEquals(19000,db.currentBalance(account,"EGP").amountMinor);
            // The caller deliberately does not commit either its balance or its ledger row.
        } finally {connection.endTransaction();}
        assertTrue(connection.isOpen());assertFalse(connection.inTransaction());
        assertEquals(10000,db.currentBalance(account,"EGP").amountMinor);assertFalse(db.currentBalance(account,"EGP").estimated);
        assertEquals(1,db.balanceHistory(account,"EGP",10).size());assertEquals(0,db.transactionCount());
    }

    public void testCommitmentStorageRoundTripPreservesFieldsAndReminderChanges() {
        long source=db.addPaymentSource("BM","حساب","7392");LocalDate due=LocalDate.of(2026,11,30);
        long id=db.putCommitment(null,"  QA Plan  ",12345,"USD",due,"weekly","installment",8,2,5," QA Match ",source);
        assertTrue(id>0);ExpenseDb.Recurring row=db.recurringById(id);
        assertEquals("QA Plan",row.merchant);assertEquals(12345,row.amountMinor);assertEquals("USD",row.currency);
        assertEquals(due,row.nextDue);assertEquals("weekly",row.cycle);assertEquals("installment",row.kind);
        assertEquals(Integer.valueOf(8),row.totalCount);assertEquals(2,row.paidCount);assertEquals(5,row.remindDays);
        assertEquals("qa match",row.matchText);assertEquals(Long.valueOf(source),row.paymentSourceId);
        assertEquals(30,row.dueDay);assertFalse(row.isDone());assertFalse(row.needsReview);
        assertNull(row.lastAlertDate);assertNull(row.lastPaidDate);
        db.markAlerted(id,LocalDate.of(2026,11,25));assertEquals("2026-11-25",db.recurringById(id).lastAlertDate);
        db.setRemindDays(id,7);assertEquals(7,db.recurringById(id).remindDays);assertNull(db.recurringById(id).lastAlertDate);
        db.setDueDay(id,90);assertEquals(31,db.recurringById(id).dueDay);
        db.setDueDay(id,-1);assertEquals(1,db.recurringById(id).dueDay);
        db.markAlerted(id,LocalDate.of(2026,11,26));
        assertEquals(id,db.putCommitment(id,"QA Updated",54321,"EGP",due.plusMonths(1),"monthly","bill",null,-2,3,"  ",null));
        row=db.recurringById(id);assertEquals("QA Updated",row.merchant);assertEquals(54321,row.amountMinor);
        assertEquals("EGP",row.currency);assertEquals("monthly",row.cycle);assertEquals("bill",row.kind);
        assertEquals(due.plusMonths(1),row.nextDue);assertEquals(30,row.dueDay);assertEquals(0,row.paidCount);
        assertEquals("qa updated",row.matchText);assertNull(row.totalCount);assertNull(row.paymentSourceId);assertNull(row.lastAlertDate);
        assertEquals(1,db.recurring().size());assertEquals(0,db.transactionCount());
    }

    public void testCommitmentStorageOrderingDuplicatesAndOuterRollback() {
        LocalDate due=LocalDate.of(2026,11,1);
        assertTrue(db.addRecurring("QA later",2000,"EGP",due.plusMonths(2)));
        long later=db.recurring().get(0).id;
        assertTrue(db.addRecurring("QA earlier",1000,"USD",due,"weekly","bill"));
        long earlier=db.recurring().get(0).id;
        long done=db.putCommitment(null,"QA done",3000,"EGP",due.minusMonths(1),"monthly","installment",1,1,1,null,null);
        assertTrue(done>0);List<ExpenseDb.Recurring> ordered=db.recurring();
        assertEquals(3,ordered.size());assertEquals(earlier,ordered.get(0).id);assertEquals(later,ordered.get(1).id);assertEquals(done,ordered.get(2).id);
        assertTrue(ordered.get(2).isDone());assertNull(ordered.get(0).totalCount);assertNull(ordered.get(0).paymentSourceId);
        assertFalse(db.addRecurring("QA later",2000,"EGP",due.plusMonths(3)));assertEquals(3,db.recurring().size());
        SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try {
            db.removeRecurring(earlier);assertNull(db.recurringById(earlier));
            db.markAlerted(later,due);db.setDueDay(later,15);db.setRemindDays(later,9);
            assertTrue(db.addRecurring("QA rollback",4000,"EGP",due));
            assertEquals(15,db.recurringById(later).dueDay);assertEquals(9,db.recurringById(later).remindDays);
            // The storage repository must not commit or close the caller's transaction/helper.
        } finally {connection.endTransaction();}
        assertTrue(connection.isOpen());assertFalse(connection.inTransaction());assertEquals(3,db.recurring().size());
        assertEquals(earlier,db.recurring().get(0).id);assertEquals(1,db.recurringById(later).dueDay);
        assertEquals(1,db.recurringById(later).remindDays);assertNull(db.recurringById(later).lastAlertDate);
        db.removeRecurring(done);assertNull(db.recurringById(done));assertEquals(2,db.recurring().size());assertEquals(0,db.transactionCount());
    }

    public void testLinkGuardsAndReversibleArchive() {
        long account=db.addPaymentSource("Banque Misr","حساب","7392"),card=db.addPaymentSource("Banque Misr","بطاقة","4636");
        long other=db.addPaymentSource("HSBC","حساب","7392"),credit=db.addPaymentSource("Banque Misr","بطاقة ائتمان","2700");
        for(long[] invalid:new long[][]{{card,other},{credit,account},{account,card},{card,card}}) {
            try{db.linkCardToAccount(invalid[0],invalid[1]);throw new AssertionError("Invalid card relationship accepted");}
            catch(IllegalArgumentException expected){}
        }
        db.linkCardToAccount(card,account);db.updateSourceProfile(account,"حساب المصروفات",true,true);
        assertEquals("Banque Misr",db.paymentSource(account).name);assertTrue(db.sourceLabel(account).contains("حساب المصروفات"));
        assertFalse(db.accountSources(false).stream().anyMatch(s->s.id==account));
        assertFalse(db.selectableSources().stream().anyMatch(s->s.id==account || s.id==card));
        assertTrue(db.addSms("Banque Misr","Card xxx4636 purchase EGP10 at TEST SHOP. Available balance EGP500",AT));
        assertEquals(card,db.latestInserted().paymentSourceId.longValue());assertEquals(50000,db.currentBalance(account,"EGP").amountMinor);
        assertEquals(4,db.paymentSources().size());
        db.updateSourceProfile(account,"حساب المصروفات",false,true);
        assertTrue(db.selectableSources().stream().anyMatch(s->s.id==card));assertEquals(3,db.accountSources(false).size());
        assertEquals(1,db.linkedCards(account).size());assertEquals(1,scalar("SELECT COUNT(*) FROM transactions"));
    }
    public void testNewBalanceAnchorAndOwnTransferAfterCardLink() {
        long account=db.addPaymentSource("BM","حساب","7392"),card=db.addPaymentSource("BM","بطاقة","4636");
        long other=db.addPaymentSource("HSBC","حساب","001");db.linkCardToAccount(card,account);
        db.putManualBalance(card,"EGP",10000,AT);db.putManualBalance(other,"EGP",2000,AT);
        assertTrue(db.addManualEntry(AT+1000,3000,"EGP","own","تحويلات",card,"transfer",other,null));
        assertEquals(7000,db.currentBalance(account,"EGP").amountMinor);assertEquals(5000,db.currentBalance(other,"EGP").amountMinor);
        assertTrue(db.addManual(AT+2000,500,"EGP","fee","رسوم تحويل",account));
        assertEquals(6500,db.currentBalance(account,"EGP").amountMinor);
        db.putManualBalance(account,"EGP",6500,AT+3000);
        assertEquals(6500,db.currentBalance(card,"EGP").amountMinor);assertFalse(db.currentBalance(account,"EGP").estimated);
        assertEquals(2,db.balanceHistory(card,"EGP",10).size());
    }
    public void testVersion17SourceMigrationPreservesFinancialRows() {
        long account=db.addPaymentSource("BM","حساب","7392"),card=db.addPaymentSource("BM","بطاقة","4636");
        assertTrue(db.addSms("BM","Card xxx4636 purchase EGP15 at TEST SHOP. Available balance EGP7240.01",AT));
        long tx=db.latestInserted().id;String key=db.sourceEvidence(tx).get(0).body;
        SQLiteDatabase writable=db.getWritableDatabase();
        writable.execSQL("CREATE TABLE legacy_sources (id INTEGER PRIMARY KEY,name TEXT NOT NULL,kind TEXT NOT NULL,last4 TEXT)");
        writable.execSQL("INSERT INTO legacy_sources SELECT id,name,kind,last4 FROM payment_sources");
        writable.execSQL("DROP TABLE payment_sources");writable.execSQL("ALTER TABLE legacy_sources RENAME TO payment_sources");
        writable.execSQL("ALTER TABLE recurring DROP COLUMN last_paid_due");
        writable.execSQL("DROP TABLE reimbursements");writable.execSQL("DROP TABLE dismissed_patterns");writable.execSQL("DROP TABLE repair_log");
        writable.execSQL("ALTER TABLE transactions DROP COLUMN time_precision");
        writable.setVersion(17);db.close();db=new ExpenseDb(context);
        assertEquals(ExpenseDb.SCHEMA_VERSION,db.getReadableDatabase().getVersion());assertEquals(2,db.paymentSources().size());
        assertNull(db.transactionById(tx).timePrecision);
        assertEquals(1500,db.transactionById(tx).amountMinor);assertEquals(key,db.sourceEvidence(tx).get(0).body);
        assertEquals(724001,db.currentBalance(card,"EGP").amountMinor);assertNull(db.paymentSource(card).parentSourceId);
        assertFalse(db.paymentSource(account).reviewed);db.linkCardToAccount(card,account);
        assertEquals(724001,db.currentBalance(account,"EGP").amountMinor);assertEquals(1,scalar("SELECT COUNT(*) FROM source_aliases WHERE payment_source_id="+card));
    }
    public void testSourceIdentityAndEditedValues() {
        String body="Your HSBC Account ********0001 was debited with IPN outward transfer for EGP1.50 on 10-10-2026 00:26 to other@instapay with reference ABC123456";
        assertTrue(db.addSms("HSBC",body,AT+1000));
        long id=db.transactions().get(0).id;
        assertFalse(db.addSms("HSBC",body,AT+3200));
        assertFalse(db.addNotification("bank.app","HSBC",body,AT+3400));
        assertEquals(1,db.transactions().size());
        assertTrue(db.addSms("HSBC",body.replace("ABC123456","DEF123456"),AT+5000));
        assertEquals(2,db.transactions().size());
        assertTrue(db.addSms("HSBC",body.replace("10-10-2026","10-11-2026").replace("ABC123456","GHI123456"),AT+31L*86400000));
        ExpenseDb.Transaction tx=db.transactionById(id);
        db.updateTransaction(id,200,"EGP","my correction","أخرى",tx.paymentSourceId,tx.kind,null,"reviewed",null);
        db.addSms("HSBC",body,AT+1000);
        assertEquals(200,db.transactionById(id).amountMinor);
        assertEquals("my correction",db.transactionById(id).merchant);
        assertEquals(body,db.sourceEvidence(id).get(0).body);
        db.deleteTransaction(id);
        db.addSms("HSBC",body,AT+1000);
        db.addSms("HSBC",body,AT+3200);
        db.addNotification("bank.app","HSBC",body,AT+3400);
        assertNull(db.transactionById(id));
        assertEquals(2,db.transactions().size());
    }
    public void testIncomingAndRejectedPayments() {
        assertTrue(db.addSms("BankMisr","تم اضافة مبلغ 500 EGP لحساب رقم 1234 عن طريق التحويل اللحظي",AT));
        assertEquals("income",db.transactions().get(0).kind);
        assertFalse(db.addSms("HSBC","Purchase of EGP60 at Amazon declined",AT));
        assertFalse(db.addSms("Amazon","Cable 60W payment declined",AT));
        assertFalse(db.addSms("BDC","Balance statement EGP10.14 minimum payment to be paid",AT));
        assertFalse(db.addSms("TRU","إجمالي المبلغ الممول 9953.00 ج.م، قسطك بقيمة 553.00 ج.م شهريًا، لمدة 18 شهر",AT));
        assertEquals(1,db.transactions().size());
        assertEquals(1,db.commitmentSuggestions().size());
        assertEquals(55300,db.commitmentSuggestions().get(0).amount);
        assertEquals(Integer.valueOf(18),db.commitmentSuggestions().get(0).count);
    }

    public void testInstallmentProgressAndCompletion() {
        LocalDate due=LocalDate.of(2026,11,1);
        long first=db.putCommitment(null,"TRU · خطة 553",55300,"EGP",due,"monthly","installment",18,0,1,"tru|ترو",null);
        long second=db.putCommitment(null,"TRU · إجمالي أقساط مختلف",159300,"EGP",due,"monthly","installment",24,14,1,"tru|ترو",null);
        assertEquals(0,db.recurringById(first).paidCount);
        assertEquals(14,db.recurringById(second).paidCount);
        assertTrue(db.finishRecurring(second,false));
        assertTrue(db.recurringById(second).isDone());
        assertEquals(14,db.recurringById(second).paidCount);
        db.putCommitment(second,"TRU · إجمالي أقساط مختلف",159300,"EGP",due,"monthly","installment",24,14,1,"tru|ترو",null);
        assertTrue(db.recurringById(second).isDone());
        assertTrue(db.reopenRecurring(second));
        assertFalse(db.recurringById(second).isDone());
        assertEquals(14,db.recurringById(second).paidCount);
        assertTrue(db.finishRecurring(second,false));
        assertFalse(db.recurringById(first).isDone());
        assertEquals(0,db.recurringById(first).paidCount);
        assertFalse(db.finishRecurring(second,true));
        assertEquals(0,db.transactions().size());
        long complete=db.putCommitment(null,"TRU · مكتملة",33400,"EGP",due,"monthly","installment",12,11,1,"tru|ترو",null);
        assertTrue(db.finishRecurring(complete,true));
        assertEquals(12,db.recurringById(complete).paidCount);
        assertTrue(db.recurringById(complete).isDone());
        long alreadyPaid=db.putCommitment(null,"TRU · مدفوع سابقًا",10000,"EGP",due,"monthly","installment",6,6,1,"tru|ترو",null);
        assertTrue(db.recurringById(alreadyPaid).isDone());
        assertEquals(6,db.recurringById(alreadyPaid).paidCount);
        assertEquals(0,db.transactions().size());
    }

    public void testVariableDueHistoryAndReviewedUpdate() {
        String old="Your card ****1234 statement is issued with total EGP 7000, minimum due is EGP 6000, due before 26-09-2026";
        String latest="Your card ****1234 statement is issued with total EGP 6500, minimum due is EGP 4900.50, due before 26-10-2026";
        assertFalse(db.addSms("DifferentBank",old,AT-31L*86400000));
        ExpenseDb.Suggestion hint=db.commitmentSuggestions().get(0);
        assertEquals("bill",hint.kind);assertEquals(600000,hint.amount);
        long id=db.putCommitment(null,hint.merchant,hint.amount,hint.currency,hint.date,"monthly",hint.kind,null,0,1,"differentbank",null);
        db.acceptSuggestion(hint.key,id);
        assertEquals(0,db.commitmentSuggestions().size());
        assertFalse(db.addSms("DifferentBank",latest,AT));
        hint=db.commitmentSuggestions().get(0);
        assertEquals(Long.valueOf(id),hint.existingId);
        assertEquals(490050,hint.amount);assertEquals(LocalDate.of(2026,10,26),hint.date);
        assertEquals(2,db.commitmentHistory(hint.key).size());
        assertEquals(600000,db.recurringById(id).amountMinor); // A reminder requires review before changing a saved plan.
        assertEquals(0,db.recurringById(id).paidCount);
        assertFalse(db.addSms("DifferentBank",old,AT-31L*86400000));
        assertEquals(490050,db.commitmentSuggestions().get(0).amount);
        assertEquals(2,db.commitmentHistory(hint.key).size());
        assertEquals(0,db.transactions().size());
    }

    public void testSubscriptionDiscoveryAndSeparatePlans() {
        String one="Dear customer, you have successfully purchased Microsoft Apps - Basic Subscription - 1 month (Microsoft OneDrive) from Google Play Store and 39 Egyptian pound has been deducted from your credit";
        // Owner decision (21 Oct 2026): a subscription paid from prepaid line credit is information only, never the
        // ledger (the money left at the top-up); its commitment suggestion is still proposed.
        db.addSms("7030",one,AT);
        assertTrue(db.addSms("AnyBank","تم خصم مبلغ EGP 89.99 فيGoogle One بتاريخ 10/10/2026",AT));
        assertTrue(db.addSms("AnyBank","تم خصم مبلغ EGP 179.99 فيGoogle One بتاريخ 10/10/2026",AT+1000));
        assertEquals(3,db.commitmentSuggestions().size());
        ExpenseDb.Suggestion hint=null;
        for(ExpenseDb.Suggestion s:db.commitmentSuggestions()) if(s.merchant.equals("OneDrive")) hint=s;
        assertNotNull(hint);assertEquals("subscription",hint.kind);assertEquals(3900,hint.amount);
        assertEquals(2,db.transactions().size()); // the two bank debits; the prepaid-credit purchase is information only
        long id=db.putCommitment(null,hint.merchant,hint.amount,hint.currency,hint.date,hint.cycle,hint.kind,null,0,1,"onedrive",null);
        db.acceptSuggestion(hint.key,id);
        assertEquals(2,db.commitmentSuggestions().size());
    }

    public void testValuReminderAndPendingPaymentRequest() {
        String reminder="عميلنا العزيز، يرجى سداد قسط 1 أكتوبر. برجاء تجاهل الرسالة عند السداد.";
        String request="تلقينا طلب سداد القسط الشهري بقيمة 1378 سيتم تحديث التطبيق في 48 ساعة";
        assertFalse(db.addSms("Valu",reminder,AT));
        assertEquals(0,db.commitmentSuggestions().get(0).amount);
        assertEquals(LocalDate.of(2026,10,1),db.commitmentSuggestions().get(0).date);
        assertFalse(db.addSms("Valu",request,AT+1000));
        assertEquals(1,db.commitmentSuggestions().size());
        assertEquals(137800,db.commitmentSuggestions().get(0).amount);
        assertEquals(2,db.commitmentHistory(db.commitmentSuggestions().get(0).key).size());
        assertFalse(db.addSms("Valu",reminder,AT+32L*86400000));
        assertEquals(137800,db.commitmentSuggestions().get(0).amount);
        assertTrue(db.commitmentSuggestions().get(0).explanation.contains("رسالة سابقة"));
        assertEquals(0,db.transactions().size());
        assertTrue(db.addSms("ET-Google","تم شراء G1SK100M - 100 GB (Google One) بنجاح من خلال Google Play Store بقيمة 94.99 جنيه من رصيدكم.",AT));
        assertEquals("Google One",db.transactions().get(0).merchant);
        assertEquals(9499,db.transactions().get(0).amountMinor);
    }

    public void testSubscriptionPriceHistoryAndCompletionReference() {
        long september=LocalDate.of(2026,9,10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // Prepaid-credit subscriptions are information only (owner decision, 21 Oct 2026); their price history still builds.
        db.addSms("7030","You have successfully purchased Cloud Basic Subscription - 1 month from Google Play Store and 30 Egyptian pound has been deducted from your credit",september);
        db.addSms("7030","You have successfully purchased Cloud Basic Subscription - 1 month from Google Play Store and 39 Egyptian pound has been deducted from your credit",AT);
        assertEquals(1,db.commitmentSuggestions().size());
        assertEquals(3900,db.commitmentSuggestions().get(0).amount);
        assertEquals(2,db.commitmentHistory(db.commitmentSuggestions().get(0).key).size());
        String plan="تم تعديل خطة أقساطك بنجاح لعملية الشراء رقم 123456/789012. أصبح قسطك الآن بقيمة 1593 ج.م شهريًا لمدة 17 شهور.";
        assertFalse(db.addSms("OtherProvider",plan,AT));
        ExpenseDb.Suggestion hint=null;
        for(ExpenseDb.Suggestion s:db.commitmentSuggestions()) if(s.kind.equals("installment")) hint=s;
        assertNotNull(hint);
        long id=db.putCommitment(null,hint.merchant,hint.amount,hint.currency,hint.date,"monthly","installment",17,0,1,"otherprovider",null);
        db.acceptSuggestion(hint.key,id);
        String completion="تم سداد جميع الأقساط لعملية الشراء رقم 123456/789012";
        assertFalse(db.addSms("OtherProvider",completion.replace("789012","789999"),AT+86400000));
        assertFalse(db.recurringById(id).isDone());
        db.addSms("OtherProvider",completion,AT+86400000);
        assertTrue(db.recurringById(id).isDone());assertEquals(17,db.recurringById(id).paidCount);
        db.putCommitment(id,hint.merchant,hint.amount,hint.currency,hint.date,"monthly","installment",17,14,1,"otherprovider",null);
        assertTrue(db.reopenRecurring(id));
        db.addSms("OtherProvider",completion,AT+86400000);
        assertFalse(db.recurringById(id).isDone());assertEquals(14,db.recurringById(id).paidCount);
        assertEquals(0,db.transactions().size()); // The plan, its completion and the prepaid-credit purchases are not expenses.
    }

    public void testDismissedOldSuggestionStaysDismissed() {
        String body="إجمالي المبلغ الممول 4008.00 ج.م، قسطك بقيمة 334.00 ج.م شهريًا، لمدة 12 شهر";
        assertFalse(db.addSms("TRU",body,AT));
        assertEquals(1,db.commitmentSuggestions().size());
        db.dismissSuggestion(db.commitmentSuggestions().get(0).key);
        assertEquals(0,db.commitmentSuggestions().size());
        assertFalse(db.addSms("TRU",body,AT));
        assertEquals(0,db.commitmentSuggestions().size());
    }
    public void testSameAmountDifferentPurchasesAndMonths() {
        assertTrue(db.addSms("HSBC","Purchase EGP100 at SHOP ONE",AT));
        assertTrue(db.addNotification("bank.app","HSBC","Purchase EGP100 at SHOP TWO",AT+1000));
        assertTrue(db.addNotification("bank.app","HSBC","You sent EGP100 to someone@instapay",AT+1000));
        assertTrue(db.addNotification("bank.app","HSBC","You sent EGP100 to someone@instapay",AT+31L*86400000));
        assertEquals(4,db.transactions().size());
    }
    public void testProviderReceiptsAndSuggestions() {
        String tru="عميل ترو العزيز، تم سداد مبلغ 1885.00 ج.م بتاريخ 01/10/2026 بنجاح.";
        assertTrue(db.addSms("TRU",tru,AT));
        assertEquals("TRU",db.transactions().get(0).merchant);
        assertEquals(188500,db.transactions().get(0).amountMinor);
        assertEquals(tru,db.sourceEvidence(db.transactions().get(0).id).get(0).body);
        String vf="تم خصم مبلغ EGP 1897.71 فى Bill Payment Vodafone يوم 17/09/2026 الرصيد المتاح EGP 1497.43";
        assertTrue(db.addSms("HSBC",vf,AT));
        assertEquals(1,db.commitmentSuggestions().size());
        ExpenseDb.Transaction vodafone=null;
        for(ExpenseDb.Transaction tx:db.transactions()) if("Vodafone".equals(tx.merchant)) vodafone=tx;
        assertNotNull(vodafone);assertEquals(189771,vodafone.amountMinor);
        assertEquals(0,db.recurring().size()); // A proposal cannot create a reminder without confirmation.
        db.clearAllData();
        assertEquals(0,scalar("SELECT COUNT(*) FROM capture_evidence"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM commitment_suggestions"));
    }
    public void testRealSnapshotReplay() throws Exception {
        File fixture=new File(getTargetContext().getCacheDir(),"audit-fixture");
        assertTrue(new File(fixture,"before.db").isFile()); // Private fixture, never committed.
        db.close();
        Files.copy(new File(fixture,"before.db").toPath(),isolated.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        db=new ExpenseDb(context);
        assertEquals(1072,scalar("SELECT COUNT(*) FROM transactions"));
        List<String> lines=Files.readAllLines(new File(fixture,"messages.tsv").toPath(),StandardCharsets.UTF_8);
        replay(lines);db.repairAll();
        assertNull(db.transactionById(1072));assertNull(db.transactionById(1073));
        assertNotNull(db.transactionById(1069));assertNotNull(db.transactionById(1071));
        assertEquals(Long.valueOf(3),db.transactionById(1071).destinationSourceId);
        assertEquals(189771,db.transactionById(843).amountMinor);
        assertEquals("Vodafone",db.transactionById(843).merchant);
        assertEquals("فواتير",db.transactionById(843).category);
        assertNull(db.transactionById(1045));assertNull(db.transactionById(846));
        assertEquals("income",db.transactionById(756).kind);
        assertEquals("income",db.transactionById(763).kind);
        assertEquals("Claude",db.recurringById(1).merchant);
        assertEquals(2280,db.recurringById(1).amountMinor);
        assertEquals("Truecaller",db.recurringById(4).merchant);
        assertTrue(db.recurringById(2).needsReview);assertTrue(db.recurringById(3).needsReview);
        assertFalse(db.sourceEvidence(843).isEmpty());
        long count=scalar("SELECT COUNT(*) FROM transactions"),sum=scalar("SELECT SUM(amount_minor) FROM transactions WHERE currency='EGP'"),
                evidence=scalar("SELECT COUNT(*) FROM capture_evidence"),paid=scalar("SELECT SUM(paid_count) FROM recurring");
        exportSnapshot(new File(fixture,"first.db"));
        replay(lines);db.repairAll();
        exportSnapshot(new File(fixture,"second.db"));
        assertEquals(count,scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(sum,scalar("SELECT SUM(amount_minor) FROM transactions WHERE currency='EGP'"));
        assertEquals(evidence,scalar("SELECT COUNT(*) FROM capture_evidence"));
        assertEquals(paid,scalar("SELECT SUM(paid_count) FROM recurring"));
        try(Cursor integrity=db.getReadableDatabase().rawQuery("PRAGMA integrity_check",null)) { assertTrue(integrity.moveToFirst());assertEquals("ok",integrity.getString(0)); }
        // Private result for offline aggregate verification; no raw SMS is exported.
        try(Cursor checkpoint=db.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)",null)) {checkpoint.moveToFirst();}
        db.close();
        Files.copy(isolated.toPath(),new File(fixture,"after.db").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private static void applyPlan(ExpenseDb target,org.json.JSONObject plan) throws Exception {
        SQLiteDatabase writable=target.getWritableDatabase();writable.beginTransaction();
        try {
            org.json.JSONArray handles=plan.getJSONArray("remove_handles");
            for(int i=0;i<handles.length();i++) target.removeOwnHandle(handles.getString(i));
            org.json.JSONArray own=plan.getJSONArray("own");
            for(int i=0;i<own.length();i++) {
                org.json.JSONObject item=own.getJSONObject(i);java.util.ArrayList<Long> sources=new java.util.ArrayList<>();
                if(item.has("expected_debit")) {
                    ExpenseDb.Transaction debit=target.transactionById(item.getLong("id"));
                    if(debit==null || debit.amountMinor!=item.getLong("expected_debit"))throw new IllegalArgumentException("Reviewed debit changed; no plan applied");
                }
                org.json.JSONArray ids=item.getJSONArray("sources");for(int j=0;j<ids.length();j++) sources.add(ids.getLong(j));
                target.reconcileOwnTransfer(item.getLong("id"),item.getLong("principal"),item.getLong("fee"),item.getLong("from"),item.getLong("to"),sources);
            }
            org.json.JSONArray income=plan.getJSONArray("income");
            for(int i=0;i<income.length();i++) {
                org.json.JSONObject item=income.getJSONObject(i);java.util.ArrayList<Long> sources=new java.util.ArrayList<>();
                org.json.JSONArray ids=item.getJSONArray("sources");for(int j=0;j<ids.length();j++) sources.add(ids.getLong(j));
                target.reconcileIncome(item.getLong("id"),sources);
            }
            if(plan.optBoolean("repair_other_transfers",true))target.repairAll();
            writable.setTransactionSuccessful();
        } finally {writable.endTransaction();}
    }

    public void testReimbursedPurchaseLeavesSpending() {
        assertTrue(db.addManual(AT+1000,111288,"EGP","SEOUDI","أكل وشرب"));long buy=db.latestInserted().id;
        assertTrue(db.addManualEntry(AT+5000,120000,"EGP","friend","دخل",null,"income",null,null));long back=db.latestInserted().id;
        assertEquals(111288,db.linkReimbursement(back,buy));
        java.util.Map<Long,Long> offsets=db.reimbursementOffsets();
        assertEquals(0,ExpenseDb.effectiveAmount(db.transactionById(buy),offsets));assertEquals(8712,ExpenseDb.effectiveAmount(db.transactionById(back),offsets));
        assertEquals(111288,db.reimbursedOf(buy));assertEquals(buy,db.reimbursedExpense(back).id);
        db.unlinkReimbursement(back);assertEquals(0,db.reimbursedOf(buy));
        db.linkReimbursement(back,buy);db.deleteTransaction(buy);assertNull(db.reimbursedExpense(back));
    }
    public void testNamedCommitmentMatchingKeepsAmbiguityAndAmountDateCurrencyBounds() {
        LocalDate day=LocalDate.of(2026,10,10);long when=AT+12*3600000;
        long a=db.putCommitment(null,"QA ambiguous A",10000,"EGP",day.plusDays(12),"monthly","bill",null,0,1,"qa merchant",null);
        long b=db.putCommitment(null,"QA ambiguous B",10000,"EGP",day.plusDays(12),"monthly","bill",null,0,1,"qa merchant",null);
        assertTrue(db.addManual(when,10000,"EGP","QA merchant","فواتير"));long first=db.latestInserted().id;
        assertEquals(0,db.recurringById(a).paidCount);assertEquals(0,db.recurringById(b).paidCount);
        assertTrue(db.finishRecurring(b,false));
        assertTrue(db.addManual(when+1000,10000,"EGP","QA merchant","فواتير"));
        assertEquals(1,db.recurringById(a).paidCount);assertEquals(day.plusDays(12).plusMonths(1),db.recurringById(a).nextDue);
        assertEquals(0,db.recurringById(b).paidCount);
        long far=db.putCommitment(null,"QA far plan",10000,"EGP",day.plusDays(13),"monthly","bill",null,0,1,"qa far vendor",null);
        assertTrue(db.addManual(when+2000,10000,"EGP","QA far vendor","فواتير"));assertEquals(0,db.recurringById(far).paidCount);
        long usd=db.putCommitment(null,"QA dollars",10000,"USD",day,"monthly","bill",null,0,1,"qa currency vendor",null);
        assertTrue(db.addManual(when+3000,10000,"EGP","QA currency vendor","فواتير"));assertEquals(0,db.recurringById(usd).paidCount);
        assertEquals(4,db.transactionCount());assertEquals(10000,db.transactionById(first).amountMinor);
    }

    public void testServiceCommitmentMatchingSkipsIneligiblePlansAndBorrowsCaptureRollback() {
        LocalDate day=LocalDate.of(2026,10,10);String body="تم دفع خدمة QA SERVICE بنجاح";
        long active=db.putCommitment(null,"QA active service",3200,"EGP",day,"monthly","bill",null,0,1,"qa service",null);
        long far=db.putCommitment(null,"QA far service",4500,"EGP",day.plusDays(13),"monthly","bill",null,0,1,"qa service",null);
        long review=db.putCommitment(null,"QA review service",5600,"EGP",day,"monthly","bill",null,0,1,"qa service",null);
        long done=db.putCommitment(null,"QA done service",6700,"EGP",day,"monthly","installment",1,1,1,"qa service",null);
        android.content.ContentValues flagged=new android.content.ContentValues();flagged.put("needs_review",1);
        db.getWritableDatabase().update("recurring",flagged,"id=?",new String[]{Long.toString(review)});
        long revision=db.changes();SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try{assertTrue(db.captureSms("InstaPay",body,AT).accepted);assertEquals(1,db.recurringById(active).paidCount);}
        finally{connection.endTransaction();} // Successful inner capture is still owned by this outer caller.
        assertEquals(revision,db.changes());assertEquals(0,db.recurringById(active).paidCount);assertTrue(connection.isOpen()&&!connection.inTransaction());
        assertTrue(db.captureSms("InstaPay",body,AT).accepted);assertEquals(1,db.recurringById(active).paidCount);
        assertEquals(day.plusMonths(1),db.recurringById(active).nextDue);
        assertEquals(0,db.recurringById(far).paidCount);assertEquals(0,db.recurringById(review).paidCount);assertEquals(1,db.recurringById(done).paidCount);
        assertFalse(db.captureSms("InstaPay",body,AT+1000).accepted);assertEquals(1,db.recurringById(active).paidCount);
        assertEquals(0,db.transactionCount()); // A receipt without an amount must not invent a second debit.
    }

    public void testCommitmentStateKeepsMonthEndAnchorAndCompletionGuards() {
        LocalDate january=LocalDate.of(2026,1,31),february=LocalDate.of(2026,2,28),march=LocalDate.of(2026,3,31);
        long id=db.putCommitment(null,"QA month end",1200,"EGP",january,"monthly","installment",3,0,1,"QA month end",null);
        db.markAlerted(id,january);long rows=db.transactionCount();
        assertTrue(db.markRecurringPaid(id,january));
        ExpenseDb.Recurring paid=db.recurringById(id);assertEquals(february,paid.nextDue);assertEquals(1,paid.paidCount);
        assertEquals(31,paid.dueDay);assertEquals(january.toString(),paid.lastPaidDate);assertNull(paid.lastAlertDate);
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT last_paid_due FROM recurring WHERE id=?",new String[]{Long.toString(id)})){
            assertTrue(c.moveToFirst());assertNull(c.getString(0)); // Legacy baseline is not rewritten by a new confirmation.
        }
        assertEquals(january,db.commitmentCycles(id).get(0).due);assertEquals(1,db.commitmentPayments(id).size());
        long revision=db.changes();assertFalse(db.markRecurringPaid(id,january.plusDays(1)));assertEquals(revision,db.changes());
        assertTrue(db.markRecurringPaid(id,february));assertEquals(march,db.recurringById(id).nextDue);
        assertTrue(db.markRecurringPaid(id,march));assertEquals(3,db.recurringById(id).paidCount);assertTrue(db.recurringById(id).isDone());
        assertFalse(db.reopenRecurring(id));assertFalse(db.finishRecurring(id,false));assertFalse(db.markRecurringPaid(id,march.plusMonths(1)));
        assertFalse(db.markRecurringPaid(999999,january));assertFalse(db.finishRecurring(999999,true));assertFalse(db.reopenRecurring(999999));
        assertEquals(rows,db.transactionCount());
    }

    public void testCommitmentStateNormalizationAndTransitionsBorrowOuterRollback() {
        LocalDate due=LocalDate.of(2026,1,31),paidDay=due.minusDays(1),next=LocalDate.of(2026,2,28);
        long id=db.putCommitment(null,"QA legacy early",2500,"EGP",due,"monthly","installment",4,1,1,"QA legacy early",null);
        android.content.ContentValues legacy=new android.content.ContentValues();legacy.put("last_paid_date",paidDay.toString());
        legacy.put("last_alert_date",paidDay.toString());db.getWritableDatabase().update("recurring",legacy,"id=?",new String[]{Long.toString(id)});
        long revision=db.changes();SQLiteDatabase connection=db.getWritableDatabase();connection.beginTransaction();
        try{
            assertEquals(1,db.normalizeEarlyPayments());assertEquals(next,db.recurringById(id).nextDue);assertEquals(1,db.recurringById(id).paidCount);
            assertEquals(0,db.normalizeEarlyPayments());assertTrue(db.markRecurringPaid(id,next));assertEquals(2,db.recurringById(id).paidCount);
            assertTrue(db.finishRecurring(id,false));assertTrue(db.recurringById(id).isDone());assertEquals(2,db.recurringById(id).paidCount);
            assertTrue(db.reopenRecurring(id));assertFalse(db.recurringById(id).isDone());
        }finally{connection.endTransaction();} // Caller deliberately rolls back every transition.
        assertTrue(connection.isOpen());assertFalse(connection.inTransaction());assertEquals(revision,db.changes());
        ExpenseDb.Recurring original=db.recurringById(id);assertEquals(due,original.nextDue);assertEquals(1,original.paidCount);
        assertEquals(paidDay.toString(),original.lastPaidDate);assertEquals(paidDay.toString(),original.lastAlertDate);assertFalse(original.isDone());
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT last_paid_due FROM recurring WHERE id=?",new String[]{Long.toString(id)})){
            assertTrue(c.moveToFirst());assertTrue(c.isNull(0));
        }
        assertEquals(0,db.transactionCount());
    }

    public void testEarlyPaymentCoversNextDue() {
        long id=db.putCommitment(null,"قسط الشقة",5060000,"EGP",LocalDate.of(2026,10,1),"monthly","installment",66,15,1,"قسط الشقة",null);
        android.content.ContentValues paid=new android.content.ContentValues();paid.put("last_paid_date","2026-09-30");
        db.getWritableDatabase().update("recurring",paid,"id=?",new String[]{Long.toString(id)});
        assertEquals(1,db.normalizeEarlyPayments());
        assertEquals(LocalDate.of(2026,11,1),db.recurringById(id).nextDue);assertEquals(15,db.recurringById(id).paidCount);
        assertEquals(0,db.normalizeEarlyPayments());
        assertFalse(db.markRecurringPaid(id,LocalDate.of(2026,10,11)));assertEquals(15,db.recurringById(id).paidCount);
        assertTrue(db.markRecurringPaid(id,LocalDate.of(2026,10,29)));
        assertEquals(LocalDate.of(2026,12,1),db.recurringById(id).nextDue);assertEquals(16,db.recurringById(id).paidCount);
    }
    public void testFeeLedgerAndSourceReplay() {
        db.addSms("HSBC","Your HSBC Account ********0001 was debited with IPN outward transfer for EGP1.50 on 10-10-2026 00:26 to 7392 with reference ABC123456",AT+1000);
        long debit=db.transactions().get(0).id;
        long from=db.transactions().get(0).paymentSourceId;
        long to=db.addPaymentSource("Banque Misr","حساب","7392");
        db.addSms("Banque Misr","تم إضافة تحويل لحظي لحسابكم رقم xxx7392 بمبلغ 1 جم من TEST PERSON رقم مرجعي 12345678 يوم 2026-10-10 الساعه 00:26",AT+2000);
        // Arrival of the credit now automatically reconciles the pair; verify that path and its replay.
        assertEquals("transfer",db.transactionById(debit).kind);
        assertEquals(2,db.transactions().size());assertEquals(100,db.transactionById(debit).amountMinor);
        assertEquals(50,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='expense'"));
        assertEquals(100,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='transfer'"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM transactions WHERE kind='income'"));
        assertFalse(db.sourceEvidence(db.latestInserted().id).isEmpty());
        db.addSms("HSBC","Your HSBC Account ********0001 was debited with IPN outward transfer for EGP1.50 on 10-10-2026 00:26 to 7392 with reference ABC123456",AT+1000);
        db.addSms("Banque Misr","تم إضافة تحويل لحظي لحسابكم رقم xxx7392 بمبلغ 1 جم من TEST PERSON رقم مرجعي 12345678 يوم 2026-10-10 الساعه 00:26",AT+2000);
        assertEquals(2,db.transactions().size());assertEquals(100,db.transactionById(debit).amountMinor);
        db.deleteTransaction(debit);assertEquals(0,db.transactions().size());
    }

    public void testConfirmedTodayPlan() throws Exception {
        if(!realFixture) return;
        File fixture=new File(getTargetContext().getCacheDir(),"audit-fixture");
        db.close();Files.copy(new File(fixture,"live-sources.db").toPath(),isolated.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);db=new ExpenseDb(context);
        org.json.JSONObject plan=new org.json.JSONObject(new String(Files.readAllBytes(new File(fixture,"confirmed-today-plan.json").toPath()),StandardCharsets.UTF_8));
        applyPlan(db,plan);
        long start=LocalDate.of(2026,10,10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(200,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='transfer' AND occurred_at>="+start));
        assertEquals(100,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='expense' AND occurred_at>="+start));
        assertEquals(100,scalar("SELECT SUM(amount_minor) FROM transactions WHERE kind='income' AND occurred_at>="+start));
        assertEquals(Long.valueOf(6),db.transactionById(1069).destinationSourceId);
        assertEquals(Long.valueOf(6),db.transactionById(1071).destinationSourceId);
        assertEquals("income",db.transactionById(1163).kind);
        assertNull(db.transactionById(1070));assertNull(db.transactionById(1074));assertNull(db.transactionById(1162));
        assertEquals(3,db.sourceEvidence(1071).size());assertEquals(2,db.sourceEvidence(1163).size());
        long count=scalar("SELECT COUNT(*) FROM transactions");
        applyPlan(db,plan);assertEquals(count,scalar("SELECT COUNT(*) FROM transactions"));
        replay(Files.readAllLines(new File(fixture,"messages.tsv").toPath(),StandardCharsets.UTF_8));
        assertEquals(count,scalar("SELECT COUNT(*) FROM transactions"));
        exportSnapshot(new File(fixture,"today-after.db"));
    }
    private void exportSnapshot(File file) throws Exception {
        try(Cursor checkpoint=db.getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(FULL)",null)) {checkpoint.moveToFirst();}
        db.close();Files.copy(isolated.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);db=new ExpenseDb(context);
    }
    private void replay(List<String> lines) {
        for(String line:lines) {
            String[] cells=line.split("\t",-1);
            db.addSms(new String(Base64.decode(cells[1],Base64.NO_WRAP),StandardCharsets.UTF_8),
                    new String(Base64.decode(cells[3],Base64.NO_WRAP),StandardCharsets.UTF_8),Long.parseLong(cells[2]));
        }
    }
}
