package com.mohamedgamal.masareef;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private int INK = 0xff0d1f1a;
    private int MUTED = 0xff5e6b66;
    private int BACKGROUND = 0xfff4f5f1;
    private int ACCENT = 0xff0b3d2e;
    private static final int BRAND = 0xff0b3d2e;
    private static final int BRAND_SOFT = 0xff1c5443;
    private static final int EMERALD = 0xff12a06e;
    private static final int LIME = 0xffc8f25a;
    private static final int INDIGO = 0xff3b4ba8;
    private int CORAL = 0xffc23a3a;
    private int INCOME = 0xff138a5c;
    private static final int GOLD = 0xffd9a21b;
    private int ACCENT_PALE = 0xffe3f1e9;
    private int BORDER = 0xffe3e6df;
    private int SURFACE = Color.WHITE;
    private static final int ORANGE = 0xffc25e0b;
    private Typeface REGULAR = Typeface.DEFAULT;
    private Typeface BOLD = Typeface.DEFAULT_BOLD;
    private boolean dark;
    private String commitmentFilter = "";
    private LocalDate commitmentMonth = LocalDate.now().withDayOfMonth(1);
    private ExpenseDb.Suggestion editingSuggestion;
    private LocalDate[] transactionPeriod;
    private boolean transactionExcludeTransfers;
    private String transactionCurrencyFilter = "";
    private long selectedCommitmentId = -1;
    private MoneyFlow.Range insightsRange = MoneyFlow.Range.MONTH;
    /** MVP home shows one number («مصروفاتك آخر 7 أيام»); the daily budget/points card returns later from here. */
    private static final boolean HOME_SHOWS_DAILY_BUDGET = false;
    /** Largest categories shown on the home card before the «باقي التصنيفات» row. */
    /** The list was opened from the home statement: rows paid from the cash wallet stay out, as in the statement. */
    private boolean transactionStatementList;
    /** Source id → kind, read once per render for the statement's internal/cash rules on list rows. */
    private Map<Long, String> sourceKindCache;
    private String transactionBucketFilter = "";
    private long flowSourceId = -1;
    private java.time.YearMonth monthlyMonth=java.time.YearMonth.now().minusMonths(1);
    private String monthlyCurrency="EGP";
    private ExpenseDb.Recurring editingCommitment;
    private String transactionKindFilter = "";
    private String transactionCategoryFilter = "";
    private Long transactionSourceFilter;
    private String transactionSearch = "";
    private boolean searchOpen;
    private boolean introVisible;
    private int introStep;
    private boolean bulkMode;
    private final Set<Long> bulkSelected=new HashSet<>();
    private final Map<Long,CheckBox> bulkChecks=new LinkedHashMap<>();
    private TextView bulkCount;
    private Button bulkApply;
    private AlertDialog dailyDetails;
    private LinearLayout dailyDetailsContent;
    private AlertDialog pointsSheet;
    private String pointsSheetCurrency;
    private boolean pointsSheetAll;
    private long pointsSheetRevision;
    private LocalDate pointsSheetDate;
    private static final int PROFILE_PHOTO_REQUEST=12;
    private String introName="",introDaily="",introSalary="";
    private int introCurrency,introPayDay;
    private boolean introDraftLoaded;

    private static final int SMS_REQUEST = 7;
    private static final int NOTIFICATION_REQUEST = 8;
    private static final int CSV_REQUEST = 9;
    private static final int VOICE_REQUEST = 10;
    private static final int CSV_IMPORT_REQUEST = 11;
    private static final int BACKUP_CREATE_REQUEST = 13;
    private static final int BACKUP_OPEN_REQUEST = 14;
    /** The password typed for a backup, held only between the dialog and the document picker's answer, then wiped. */
    private char[] pendingBackupPassword;
    private static final String[] CATEGORIES = {
            "غير مصنف", "أكل وشرب", "مقاهي", "بقالة", "مواصلات", "فواتير", "اشتراكات", "أقساط", "تسوق", "رعاية صحية", "صحة", "عائلة ومساعدات", "سكن وإيجار",
            "تحويلات", "رسوم تحويل", "رسوم بنكية", "سحب نقدي", "أخرى"
    };
    /** What a transfer to another person was for; picking one moves it out of «تحويلات غرضها مش محدد». */
    private static final String[] PURPOSES = {
            "عائلة ومساعدات", "سكن وإيجار", "أقساط", "فواتير", "أكل وشرب", "مقاهي", "بقالة", "تسوق", "رعاية صحية", "صحة", "مواصلات", "أخرى"
    };
    private static final String[] CURRENCIES = {"EGP", "USD", "EUR", "GBP"};
    private ExpenseDb db;
    private AppLock appLock;
    private WhatsNew.Section whatsNew;
    private boolean initialActionsPending, initialDirect, initialFresh, pendingIntentActions;
    private LinearLayout page;
    private LinearLayout settingsGroup;
    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private int selectedTab = 0;
    private String settingsSection = "";
    private String insightsSection = "";
    private int transactionReturnTab = 6;
    /** Where Back goes from العمليات: التحليلات when a number was opened from there. */
    private int transactionsBackTab = 0;
    private String transactionsReturnDialog="",transactionsPointsCurrency="";
    private ExpenseDb.Transaction selectedTransaction;
    private final TransactionJourney transactionJourney=new TransactionJourney();
    private final Map<String,Integer> scrollPositions=new LinkedHashMap<>();
    private ScrollView pageScroll;
    private String renderedPageKey;
    /** Suggestion source for the commitment screens. render() only reads it; the capture worker recomputes it. */
    private Set<Long> recurringCandidates = new HashSet<>();
    private List<ExpenseDb.Transaction> recurringCandidateRows = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean refreshingLedger = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean ledgerRefreshPending;
    /** A financial broadcast needs a redraw even when the expense-only suggestion cache is unchanged. */
    private boolean ledgerRedrawPending;
    /**
     * Generation counter, bumped by every local ledger edit. A snapshot read under an older generation is
     * discarded instead of applied, so a redraw can never re-advertise a row the user just deleted.
     */
    private final java.util.concurrent.atomic.AtomicLong ledgerVersion = new java.util.concurrent.atomic.AtomicLong();
    /** The change counter the applied snapshot was read at; render() reloads once the ledger moves past it. */
    private Long ledgerChanges;
    private final BroadcastReceiver dataChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshPointsSheet();
            ledgerRedrawPending = true;
            boolean visible = selectedTab == 0 || selectedTab == 1 || selectedTab == 2 || selectedTab == 6 || selectedTab == 11 || selectedTab == 15;
            refreshLedger(visible);
        }
    };

    @Override protected void onCreate(Bundle state) {
        dark = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
        if (dark) {
            setTheme(R.style.AppTheme_Dark);
            INK = 0xffeef3ef; MUTED = 0xff9aaaa2; BACKGROUND = 0xff0d1a16; ACCENT = 0xff2bbf85;
            ACCENT_PALE = 0xff1c3a2e; BORDER = 0xff2a3b33; SURFACE = 0xff16261f; CORAL = 0xfff07070; INCOME = 0xff3ccb8b;
        }
        super.onCreate(state);
        appLock = new AppLock(this, this::onAppUnlocked);
        loadFonts();
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(SURFACE);
        int systemBars = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= 27) systemBars |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemBars);
        db = AppResources.database(this);
        loadWhatsNew(readAsset("whats-new.txt"));
        if (state == null) AppResources.read(() -> LocalBackup.removeLeftovers(this));
        boolean direct=Intent.ACTION_SEND.equals(getIntent().getAction()) || getIntent().hasExtra(CaptureNotifier.EXTRA_TRANSACTION) || getIntent().hasExtra(CaptureNotifier.EXTRA_CAPTURE_EVENT) || getIntent().getBooleanExtra("open_transactions",false) || getIntent().getBooleanExtra("open_add_transaction",false) || getIntent().getBooleanExtra("open_daily_budget",false) || getIntent().getBooleanExtra("open_commitments",false) || getIntent().hasExtra(MonthlySummaryReceiver.EXTRA_MONTH);
        introVisible=state==null?!direct&&!getPreferences(MODE_PRIVATE).getBoolean("intro_completed",false):state.getBoolean("intro_visible",false);
        introStep=state==null?0:Math.min(3,state.getInt("intro_step",0));
        if(state!=null){introName=state.getString("intro_name","");introDaily=state.getString("intro_daily","");introSalary=state.getString("intro_salary","");introCurrency=state.getInt("intro_currency",0);introPayDay=state.getInt("intro_payday",0);introDraftLoaded=state.getBoolean("intro_loaded",false);}
        if(state!=null) {
            bulkMode=state.getBoolean("bulk_mode",false);
            long[] ids=state.getLongArray("bulk_ids");if(ids!=null)for(long id:ids)bulkSelected.add(id);
            selectedTab=state.getInt("tab",0);
            settingsSection=state.getString("settings_section","");
            flowSourceId=state.getLong("flow_source_id",-1);
            monthlyMonth=MonthlyReport.parseMonth(state.getString("monthly_month"),java.time.YearMonth.now().minusMonths(1));
            monthlyCurrency=state.getString("monthly_currency","EGP");
            transactionsBackTab=state.getInt("transactions_back",0);
            transactionsReturnDialog=state.getString("transactions_return_dialog","");transactionsPointsCurrency=state.getString("transactions_points_currency","");
            insightsSection=state.getString("insights_section","");
            selectedMonth=LocalDate.parse(state.getString("month",LocalDate.now().withDayOfMonth(1).toString()));
            long restored=state.getLong("detail_id",-1);if(restored>=0)selectedTransaction=db.transactionById(restored);
            selectedCommitmentId=state.getLong("commitment_id",-1);
            transactionReturnTab=state.getInt("transaction_return",6);
            transactionJourney.restore(state.getLongArray("detail_parents"));
            Bundle positions=state.getBundle("scroll_positions");
            if(positions!=null)for(String key:positions.keySet())scrollPositions.put(key,positions.getInt(key));
            transactionSearch=state.getString("search","");searchOpen=state.getBoolean("search_open",false);
            transactionKindFilter=state.getString("kind_filter","");transactionCategoryFilter=state.getString("category_filter","");
            transactionCurrencyFilter=state.getString("currency_filter","");
            if(state.containsKey("source_filter"))transactionSourceFilter=state.getLong("source_filter");
            String from=state.getString("period_from");if(from!=null)transactionPeriod=new LocalDate[]{LocalDate.parse(from),LocalDate.parse(state.getString("period_to"))};
            transactionExcludeTransfers=state.getBoolean("exclude_transfers",false);
            transactionBucketFilter=state.getString("bucket_filter","");
            transactionStatementList=state.getBoolean("statement_list",false);
            try{insightsRange=MoneyFlow.Range.valueOf(state.getString("insights_range","MONTH"));}catch(IllegalArgumentException ignored){insightsRange=MoneyFlow.Range.MONTH;}
            if(selectedTab==12)selectedTab=2; // Unsaved existing editor forms do not restore empty details.
            if(selectedTab==10&&selectedTransaction==null){selectedTab=transactionReturnTab;transactionJourney.clear();}

        }
        if(getIntent().getBooleanExtra("open_transactions",false)){resetTransactionFilters();selectedTab=6;getIntent().removeExtra("open_transactions");}
        ReminderReceiver.schedule(this);
        MonthlySummaryReceiver.initialize(this,java.time.YearMonth.now());MonthlySummaryReceiver.schedule(this);
        render();
        refreshLedger(true);
        refreshMerchantCatalog();
        runRepair(false);
        initialActionsPending = true;
        initialDirect = direct;
        initialFresh = state == null;
        if (!appLock.locked()) runInitialActions();
    }

    private void runInitialActions() {
        if (!initialActionsPending || appLock.guard()) return;
        initialActionsPending = false;
        if (initialFresh && !initialDirect) playOpening(false);
        handleShare(getIntent());
        openTransactionFromIntent(getIntent());
        openCaptureEventFromIntent(getIntent());
        openWidgetAction(getIntent());
        openMonthlyIntent(getIntent());
        if (!introVisible && initialFresh && !initialDirect) {
            requestSmsAtStartup();
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                requestNotificationAtStartup();
                // Without this permission Android silently drops the "recorded" and due-date notifications.
                askNotificationPermission();
            }
        }
    }

    private void onAppUnlocked() {
        if (db == null || isFinishing() || isDestroyed()) return;
        render();
        if (initialActionsPending) runInitialActions();
        else if (pendingIntentActions) {
            pendingIntentActions = false;
            processIncomingIntent(getIntent());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (appLock.guard()) return;
        if (db != null) {render();refreshLedger(true);MonthlySummaryReceiver.refresh(this);}
        if (db != null && !introVisible && hasNotificationAccess()) {
            SharedPreferences settings = getSharedPreferences("notification_settings", MODE_PRIVATE);
            if (!settings.getBoolean("source_picker_shown", false) &&
                    settings.getStringSet("sources", java.util.Collections.emptySet()).isEmpty()) {
                settings.edit().putBoolean("source_picker_shown", true).apply();
                chooseNotificationApps();
            }
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // API 26–32 uses a signature permission below.
    @Override protected void onStart() {
        super.onStart();
        appLock.onStart();
        IntentFilter filter = new IntentFilter(SmsReceiver.ACTION_DATA_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dataChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dataChanged, filter, getPackageName() + ".INTERNAL_BROADCAST", null);
    }

    @Override protected void onStop() {
        appLock.onStop();
        unregisterReceiver(dataChanged);
        super.onStop();
    }

    @Override protected void onDestroy() {
        appLock.dispose();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (appLock.guard()) { pendingIntentActions = true; return; }
        processIncomingIntent(intent);
    }

    private void processIncomingIntent(Intent intent) {
        handleShare(intent);
        openTransactionFromIntent(intent);
        openCaptureEventFromIntent(intent);
        openWidgetAction(intent);
        openMonthlyIntent(intent);
        if(intent.getBooleanExtra("open_transactions",false)){intent.removeExtra("open_transactions");resetTransactionFilters();introVisible=false;selectedTab=6;render();}
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);out.putBoolean("intro_visible",introVisible);out.putInt("intro_step",introStep);
        out.putString("intro_name",introName);out.putString("intro_daily",introDaily);out.putString("intro_salary",introSalary);
        out.putInt("intro_currency",introCurrency);out.putInt("intro_payday",introPayDay);out.putBoolean("intro_loaded",introDraftLoaded);
        out.putBoolean("bulk_mode",bulkMode);out.putInt("tab",selectedTab);out.putString("settings_section",settingsSection);
        out.putString("insights_section",insightsSection);out.putLong("flow_source_id",flowSourceId);
        out.putString("monthly_month",monthlyMonth.toString());out.putString("monthly_currency",monthlyCurrency);out.putInt("transactions_back",transactionsBackTab);
        out.putString("transactions_return_dialog",transactionsReturnDialog);out.putString("transactions_points_currency",transactionsPointsCurrency);
        out.putString("month",selectedMonth.toString());out.putLong("detail_id",selectedTransaction==null?-1:selectedTransaction.id);
        out.putLong("commitment_id",selectedCommitmentId);out.putInt("transaction_return",transactionReturnTab);
        savePageScroll();out.putLongArray("detail_parents",transactionJourney.snapshot());
        Bundle positions=new Bundle();for(Map.Entry<String,Integer> e:scrollPositions.entrySet())positions.putInt(e.getKey(),e.getValue());
        out.putBundle("scroll_positions",positions);
        out.putString("search",transactionSearch);out.putBoolean("search_open",searchOpen);out.putString("kind_filter",transactionKindFilter);
        out.putString("category_filter",transactionCategoryFilter);out.putString("currency_filter",transactionCurrencyFilter);
        if(transactionSourceFilter!=null)out.putLong("source_filter",transactionSourceFilter);
        if(transactionPeriod!=null){out.putString("period_from",transactionPeriod[0].toString());out.putString("period_to",transactionPeriod[1].toString());}
        out.putBoolean("exclude_transfers",transactionExcludeTransfers);
        out.putString("bucket_filter",transactionBucketFilter);out.putBoolean("statement_list",transactionStatementList);out.putString("insights_range",insightsRange.name());
        long[] ids=new long[bulkSelected.size()];int i=0;for(long id:bulkSelected)ids[i++]=id;out.putLongArray("bulk_ids",ids);
    }

    /** A completed job may outlive this screen; only a live owner can receive its UI result. */
    private void postUi(Runnable work) {
        runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed())work.run();});
    }

    private void sourcesScreen() {
        header("حساباتك", "الأرصدة", "+", this::paymentSourceDialog);
        List<ExpenseDb.PaymentSource> sources = db.accountSources(true);
        int confirmed=0,review=0;java.util.Set<String> banks=new java.util.HashSet<>();
        for(ExpenseDb.PaymentSource source:sources) {
            if(source.archived)continue;
            banks.add(SourceIdentity.bank(source.name));
            if("حساب".equals(source.kind) || "محفظة".equals(source.kind)) {
                if(source.reviewed)confirmed++;else review++;
            }
        }
        note(confirmed+" حسابات مؤكدة · "+review+" حسابات تحتاج مراجعة · "+banks.size()+" بنوك");
        note("الرصيد المحسوب = آخر رصيد + الوارد − الخارج. يظل تقديريًا حسب تغطية الرسائل؛ افتح الحساب لمراجعة الرصيد والكروت.");
        if (sources.isEmpty()) empty("ضيف أول وسيلة دفع", "اكتب اسم الكارت وآخر 4 أرقام علشان نربط رسائله تلقائيًا");
        String[] groups={"حساباتك", "بطاقات ائتمان", "كروت تحتاج ربط بالحساب", "مؤرشف — التاريخ محفوظ"};
        for(int group=0;group<groups.length;group++) {
            boolean heading=false;
            for (ExpenseDb.PaymentSource source : sources) {
            int sourceGroup=source.archived?3:"بطاقة ائتمان".equals(source.kind)?1:
                    ("حساب".equals(source.kind)||"محفظة".equals(source.kind))?0:2;
            if(sourceGroup!=group)continue;
            if(!heading){section(groups[group],null,null);heading=true;}
            LinearLayout card = card();
            LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
            title.addView(sourceBadge(source,34),new LinearLayout.LayoutParams(dp(34),dp(34)));
            TextView name=label(db.sourceLabel(source.id),16,INK,true);
            LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.setMarginStart(dp(10));title.addView(name,nameParams);
            card.addView(title);
            card.addView(label(source.archived?"مؤرشف · يمكنك إعادة تفعيله":source.reviewed?"استخدام مؤكد منك":"مكتشف من الرسائل · يحتاج مراجعة",12,MUTED,false));
            boolean knownBalance=false;
            for(String currency:CURRENCIES) {
                ExpenseDb.BalanceSnapshot balance = db.currentBalance(source.id, currency);
                if(balance==null)continue;knownBalance=true;
                card.addView(label((balance.estimated?"رصيد محسوب · تقديري":"آخر رصيد مسجل")+" · "+currency,12,MUTED,false));
                card.addView(label(money(balance.amountMinor, currency),24,INK,true));
                String stamp=sourceDate(balance.occurredAt);
                ExpenseDb.PaymentSource origin=db.paymentSource(balance.anchorSourceId);
                card.addView(label("رصيد البداية: "+stamp+" · "+balance.source+
                        (origin==null?"":" · "+origin.kind+UiKit.last4Suffix(origin.last4)),12,MUTED,false));
                if(balance.estimated)card.addView(label(balance.movementCount+" حركات بعده · حتى "+sourceDate(balance.updatedAt),12,MUTED,false));
            }
            if(!knownBalance)card.addView(label("الرصيد غير متاح · أضف رصيد بداية",16,MUTED,true));
            if("بطاقة ائتمان".equals(source.kind))card.addView(label("رصيد ائتماني منفصل عن فلوس الحسابات؛ معنى المتاح أو المستحق يعتمد على رسالة البنك.",12,MUTED,false));
            for(ExpenseDb.PaymentSource linked:db.linkedCards(source.id)) {
                TextView linkedRow=label("كارت مرتبط •"+linked.last4+(linked.archived?" · مؤرشف":"")+" · آخر استخدام "+sourceDate(linked.lastActivityAt),13,ACCENT,true);
                linkedRow.setPadding(0,dp(10),0,dp(10));card.addView(linkedRow);
                linkedRow.setOnClickListener(v->sourceOptions(linked));
            }
            java.util.Set<Long> members=new java.util.HashSet<>();members.add(source.id);
            for(ExpenseDb.PaymentSource linked:db.linkedCards(source.id))members.add(linked.id);
            long total = 0;
            for (ExpenseDb.Transaction tx : db.transactionsForMonth(selectedMonth))
                if (tx.paymentSourceId != null && members.contains(tx.paymentSourceId) &&
                        "EGP".equals(tx.currency) && "expense".equals(tx.kind)) total += tx.amountMinor;
            card.addView(label("خرج منه الشهر ده  " + money(total, "EGP"), 16, ACCENT, true));
            card.addView(label("آخر استخدام: "+sourceDate(db.sourceLastActivity(source.id))+" · اضغط للإدارة",12,MUTED,false));
            card.setOnClickListener(v->sourceOptions(source));
            }
        }
    }

    private String sourceDate(long time) {
        return time<=0?"غير متاح":Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm",Locale.ROOT));
    }

    private void sourceOptions(ExpenseDb.PaymentSource source) {
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add("سجل الأرصدة");actions.add(()->balanceHistoryDialog(source));
        labels.add("تحديث الرصيد الحالي");actions.add(()->manualBalanceDialog(source));
        labels.add("تسمية وتأكيد الاستخدام");actions.add(()->sourceProfileDialog(source));
        if("بطاقة".equals(source.kind)) {
            labels.add("ربط الكارت بحسابه");actions.add(()->linkSourceDialog(source));
        } else if("حساب".equals(source.kind)) {
            labels.add("ربط كارت بالحساب");actions.add(()->linkSourceDialog(source));
        }
        if(source.parentSourceId!=null) {
            labels.add("فك ربط الكارت");actions.add(()->new ChoiceDialogBuilder().setTitle("فك ربط الكارت؟")
                    .setMessage("كل الرسائل والعمليات تفضل محفوظة. الرصيد سيحسب لكل معرف منفصلًا.")
                    .setPositiveButton("فك الربط",(d,w)->{db.unlinkCard(source.id);render();}).setNegativeButton("إلغاء",null).show());
        }
        labels.add(source.archived?"إعادة تفعيل الوسيلة":"أرشفة الوسيلة القديمة");
        actions.add(()->new ChoiceDialogBuilder().setTitle(source.archived?"إعادة التفعيل؟":"أرشفة الوسيلة؟")
                .setMessage("الرسائل والعمليات والأرصدة والربط محفوظة. المؤرشف لا يظهر في اختيارات العمليات الجديدة؛ يمكن إعادة تفعيله.")
                .setPositiveButton(source.archived?"تفعيل":"أرشفة",(d,w)->{
                    db.updateSourceProfile(source.id,source.displayName,!source.archived,source.reviewed);render();
                }).setNegativeButton("إلغاء",null).show());
        labels.add("إعادة فحص رسائل SMS");actions.add(this::requestSmsImport);
        new ChoiceDialogBuilder().setTitle(db.sourceLabel(source.id)).setItems(labels.toArray(new String[0]),
                (d,which)->actions.get(which).run()).show();
    }

    private void sourceProfileDialog(ExpenseDb.PaymentSource source) {
        LinearLayout content=form();content.addView(label(source.name+" · "+source.kind+UiKit.last4Suffix(source.last4),14,MUTED,false));
        EditText name=input("اسم واضح ليك، مثل حساب المصروفات",content);name.setText(source.displayName==null?"":source.displayName);
        android.widget.CheckBox reviewed=new android.widget.CheckBox(this);reviewed.setText("راجعت الوسيلة وأعرف أني بستخدمها");
        reviewed.setTextColor(INK);reviewed.setChecked(source.reviewed);content.addView(reviewed);
        new ChoiceDialogBuilder().setTitle("تسمية الوسيلة").setView(content).setPositiveButton("حفظ",(d,w)->{
            db.updateSourceProfile(source.id,name.getText().toString(),source.archived,reviewed.isChecked());render();
        }).setNegativeButton("إلغاء",null).show();
    }

    private void linkSourceDialog(ExpenseDb.PaymentSource source) {
        boolean card="بطاقة".equals(source.kind);List<ExpenseDb.PaymentSource> choices=new ArrayList<>();
        for(ExpenseDb.PaymentSource option:db.selectableSources())
            if(option.id!=source.id && SourceIdentity.bank(source.name).equals(SourceIdentity.bank(option.name)) &&
                    (card?"حساب".equals(option.kind)&&option.parentSourceId==null:"بطاقة".equals(option.kind)))choices.add(option);
        if(choices.isEmpty()){toast(card?"أضف حساب البنك أولًا":"مفيش كروت لهذا البنك متاحة للربط");return;}
        String[] labels=new String[choices.size()];for(int i=0;i<choices.size();i++)labels[i]=db.sourceLabel(choices.get(i).id);
        new ChoiceDialogBuilder().setTitle(card?"الكارت بيسحب من أي حساب؟":"اختار الكارت التابع للحساب")
                .setItems(labels,(dialog,index)->{
                    ExpenseDb.PaymentSource option=choices.get(index);
                    new ChoiceDialogBuilder().setTitle("تأكيد علاقة الكارت بالحساب")
                            .setMessage(db.sourceLabel(source.id)+"\n"+db.sourceLabel(option.id)+"\n\nأكد فقط لو الكارت بيسحب من الحساب ده فعلًا. الرسائل والعمليات تفضل بهويتها، والرصيد يجمع الحركات بعد آخر رصيد مرة واحدة.")
                            .setPositiveButton("أيوه، اربط",(d,w)->{
                                try{db.linkCardToAccount(card?source.id:option.id,card?option.id:source.id);render();}
                                catch(IllegalArgumentException error){toast("راجع الحساب والكارت؛ لا نربط حسابات مختلفة أو بطاقة ائتمان");}
                            }).setNegativeButton("إلغاء",null).show();
                }).show();
    }

    private void mergeSourceDialog(ExpenseDb.PaymentSource keep) {
        if(new CardSettlements(db).sourceRelated(keep.id)){toast("فك ربط سداد البطاقة قبل دمج الحساب");return;}
        List<ExpenseDb.PaymentSource> options = new ArrayList<>();
        String bank = SourceIdentity.bank(keep.name);
        for (ExpenseDb.PaymentSource source : db.paymentSources())
            if (source.id != keep.id && bank != null && bank.equals(SourceIdentity.bank(source.name))&&!new CardSettlements(db).sourceRelated(source.id))
                options.add(source);
        if (options.isEmpty()) { toast("مفيش معرفات تانية لنفس البنك"); return; }
        String[] labels = new String[options.size()];
        boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++)
            labels[i] = options.get(i).name + "  •  " +
                    (options.get(i).last4 == null ? "بدون رقم" : "•••• " + options.get(i).last4);
        new ChoiceDialogBuilder().setTitle("اختار معرفات نفس الحساب")
                .setMultiChoiceItems(labels, checked, (dialog, which, selected) -> checked[which] = selected)
                .setPositiveButton("التالي", (dialog, which) -> {
                    int count = 0;
                    for (boolean selected : checked) if (selected) count++;
                    if (count == 0) { toast("اختار معرف واحد على الأقل"); return; }
                    int selectedCount = count;
                    new ChoiceDialogBuilder().setTitle("دمج الحسابات؟")
                            .setMessage("هتتجمع " + selectedCount + " معرفات تحت " + keep.name +
                                    ". المعاملات والأرصدة هتفضل محفوظة، وآخر رصيد مؤكد زمنيًا هيبقى نقطة البداية.")
                            .setPositiveButton("دمج", (confirm, button) -> {
                                for (int i = 0; i < options.size(); i++)
                                    if (checked[i]){try{db.mergeSources(keep.id, options.get(i).id);}catch(IllegalArgumentException error){toast("فك ربط البطاقات أو سدادها قبل دمج المعرفات");}}
                                render();
                            }).setNegativeButton("إلغاء", null).show();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void balanceHistoryDialog(ExpenseDb.PaymentSource source) {
        List<ExpenseDb.BalanceSnapshot> history = db.balanceHistory(source.id, "EGP", 50);
        if (history.isEmpty()) { toast("لسه مفيش أرصدة مسجلة للحساب"); return; }
        StringBuilder body = new StringBuilder();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
        for (ExpenseDb.BalanceSnapshot snapshot : history) {
            body.append(Instant.ofEpochMilli(snapshot.occurredAt).atZone(ZoneId.systemDefault()).format(format))
                    .append("   ·   ").append(money(snapshot.amountMinor, "EGP"))
                    .append("   ·   ").append(snapshot.source).append("\n\n");
        }
        new ChoiceDialogBuilder().setTitle("سجل أرصدة " + db.sourceLabel(db.accountId(source.id)))
                .setMessage(body.toString().trim()).setPositiveButton("تمام", null).show();
    }

    private void manualBalanceDialog(ExpenseDb.PaymentSource source) {
        LinearLayout form = form();
        EditText amount = input("الرصيد الحالي", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        new ChoiceDialogBuilder().setTitle("رصيد " + db.sourceLabel(db.accountId(source.id))).setView(form)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    if (rejectMisreadAmount(amount)) return;
                    long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                    if (minor < 0) { toast("راجع مبلغ الرصيد"); return; }
                    db.putManualBalance(db.accountId(source.id), currency.getSelectedItem().toString(), minor, System.currentTimeMillis());
                    render();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void settingsScreen() {
        String title=settingsSection.isEmpty()?"الإعدادات":
                "budget".equals(settingsSection)?"الميزانية والويدجت":
                "capture".equals(settingsSection)?"التقاط العمليات":
                "appearance".equals(settingsSection)?"المظهر والملف الشخصي":
                "alerts".equals(settingsSection)?"الإشعارات والتذكير":
                "data".equals(settingsSection)?"البيانات والخصوصية":"المساعدة وعن التطبيق";
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(iconButton(R.drawable.ic_chevron_back,"رجوع",this::backFromSettings),new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout.LayoutParams heading=new LinearLayout.LayoutParams(0,-2,1);heading.setMarginStart(dp(10));
        bar.addView(label(title,22,INK,true),heading);page.addView(bar);settingsGroup=null;
        if(settingsSection.isEmpty()) {
            LinearLayout profile=card();
            LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);
            identity.addView(profileAvatar(48),new LinearLayout.LayoutParams(dp(48),dp(48)));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
            String name=getPreferences(MODE_PRIVATE).getString("profile_name","").trim();
            copy.addView(label(name.isEmpty()?"ملفك الشخصي":name,18,INK,true));
            copy.addView(label("محفوظ على جهازك · اضغط لتخصيصه",12,MUTED,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(12));identity.addView(copy,cp);
            profile.addView(identity);profile.setOnClickListener(v->{settingsSection="appearance";render();});
            settingsGroup=null;
            note("بياناتك المالية محفوظة محليًا. اختار المجموعة اللي محتاجها.");
            settingsCategory("الميزانية والويدجت","حد اليوم، العملة، ومقاسات الشاشة الرئيسية",R.drawable.ic_nav_budget,"budget");
            settingsCategory("التقاط العمليات","رسائل البنك، تطبيقات الدفع، والتصنيف",R.drawable.ic_message,"capture");
            settingsCategory("المظهر والملف الشخصي","الوضع الليلي، الاسم، وافتتاحية التطبيق",R.drawable.ic_settings,"appearance");
            settingsCategory("الإشعارات والتذكير","إشعار العملية والتذكير اليومي",R.drawable.ic_bell,"alerts");
            settingsCategory("البيانات والخصوصية","CSV، مراجعة البيانات، وخيارات الحذف",R.drawable.ic_filter,"data");
            settingsCategory("المساعدة وعن التطبيق","الإبلاغ عن مشكلة، الاقتراحات، والإصدار",R.drawable.ic_cat_other,"help");
            LinearLayout signature=new LinearLayout(this);signature.setOrientation(LinearLayout.VERTICAL);
            signature.setGravity(Gravity.CENTER_HORIZONTAL);signature.setPadding(0,dp(28),0,dp(18));
            signature.addView(centered(label("بكل فخر، صُنع في مصر 🇪🇬",14,ACCENT,true)));
            TextView credit=label("Developed by Mohamed Gamal",12,MUTED,false);
            credit.setTextDirection(View.TEXT_DIRECTION_LTR);signature.addView(centered(credit));
            signature.addView(centered(label("Follow Money · "+GitHubSupport.version(this),11,MUTED,false)));
            page.addView(signature);return;
        }
        section("الخيارات",null,null);
        switch(settingsSection) {
            case "budget":
        setting("الميزانية اليومية والراتب",this::dailyBudgetDialog);
        setting("أضف ويدجت الشاشة الرئيسية", this::pinWidget);
        String widgetCurrency=getSharedPreferences("widget_settings",MODE_PRIVATE).getString("currency","EGP");
        setting("عملة الويدجت والملخص: "+widgetCurrency,()->new ChoiceDialogBuilder().setTitle("اختر عملة واحدة للملخص")
                .setItems(CURRENCIES,(d,w)->{getSharedPreferences("widget_settings",MODE_PRIVATE).edit().putString("currency",CURRENCIES[w]).apply();render();}).show());
        setting(DailyRefreshReceiver.precise(this)?"تحديث منتصف الليل: دقيق":"تحديث منتصف الليل: قد يتأخر",()->{
            if(Build.VERSION.SDK_INT>=31&&!DailyRefreshReceiver.precise(this))new ChoiceDialogBuilder().setTitle("تحديث اليوم في موعده")
                    .setMessage("اسمح بالمنبهات والتذكيرات لتحديث الويدجت عند منتصف الليل. من غير الإذن Android ممكن يؤخر التحديث.")
                    .setPositiveButton("افتح إعدادات الإذن",(d,w)->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))))
                    .setNegativeButton("لاحقًا",null).show();else toast("التحديث المجدول عند منتصف الليل بالتوقيت المحلي");
        });
        boolean widgetHidden=getSharedPreferences("widget_settings",MODE_PRIVATE).getBoolean("hide_amounts",false);
        setting(widgetHidden?"أرقام الويدجت: مخفية":"أرقام الويدجت: ظاهرة",()->{
            getSharedPreferences("widget_settings",MODE_PRIVATE).edit().putBoolean("hide_amounts",!widgetHidden).apply();render();
        });
        note(getString(R.string.widget_lock_privacy_notice));
                break;
            case "capture":
                note("الرسائل والإشعارات الجديدة تُقرأ بعد منح الإذن. اختيار فترة الرسائل القديمة يبدأ الاستيراد مباشرة، والمراجعة قبل الحفظ اختيارية.");
                android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
                if(power!=null&&!power.isIgnoringBatteryOptimizations(getPackageName())) {
                    settingsGroup=null;
                    tipCard("⚡","التقاط العمليات في الخلفية",
                            "بعض الأجهزة، زي Samsung وXiaomi، ممكن توقف الالتقاط في الخلفية لتوفير البطارية. لو رسائل جديدة مش بتتسجل، راجع استثناء Follow Money من تحسين البطارية.",
                            "إعدادات البطارية",()->{
                                try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
                                catch(android.content.ActivityNotFoundException unavailable){toast("إعدادات تحسين البطارية غير متاحة على الجهاز");}
                            });
                }
                setting("استيراد رسائل SMS القديمة",this::requestSmsImport);
                setting("تطبيقات الدفع والإشعارات",this::chooseNotificationApps);
                setting(hasNotificationAccess()?"الوصول للإشعارات: مفعل":"تفعيل الوصول للإشعارات",this::openNotificationSettings);
                setting("حساباتك على إنستاباي",this::ownHandlesDialog);
                setting("قواعد التجار والتصنيف",()->{selectedTab=7;render();});
                break;
            case "appearance":
        setting("اسم العرض على الرئيسية", () -> {
            EditText name = new EditText(this);
            name.setSingleLine(true);
            name.setText(getPreferences(MODE_PRIVATE).getString("profile_name", ""));
            new ChoiceDialogBuilder().setTitle("اسم العرض").setView(name)
                    .setPositiveButton("حفظ", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit().putString("profile_name", name.getText().toString().trim()).apply();
                        render();
                    }).setNegativeButton("إلغاء", null).show();
        });
        setting(getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false) ? "الوضع: ليلي" : "الوضع: فاتح", () -> {
            boolean enabled = getPreferences(MODE_PRIVATE).getBoolean("dark_mode", false);
            getPreferences(MODE_PRIVATE).edit().putBoolean("dark_mode", !enabled).apply();
            recreate();
        });
        setting("الصورة الشخصية (اختيارية)",this::chooseProfilePhoto);
        if(new java.io.File(getFilesDir(),"profile-avatar.png").exists())setting("حذف الصورة الشخصية",()->{new java.io.File(getFilesDir(),"profile-avatar.png").delete();render();});
        String openingMode=getPreferences(MODE_PRIVATE).getString("opening_mode","every");
        setting("الافتتاحية: "+("off".equals(openingMode)?"متوقفة":"first".equals(openingMode)?"أول مرة بس":"مع كل فتح"),()->{
            String[] modes={"every","first","off"};
            new ChoiceDialogBuilder().setTitle("افتتاحية التطبيق").setItems(new String[]{"مع كل فتح (٣ ثواني)","أول مرة بس","متوقفة"},(d,w)->{
                getPreferences(MODE_PRIVATE).edit().putString("opening_mode",modes[w]).apply();render();
            }).setNeutralButton("شغّلها دلوقتي",(d,w)->playOpening(true)).show();
        });
                break;
            case "alerts":
        notificationSetting("إشعار فوري بكل عملية", "عند تسجيل عملية جديدة أو إلغائها", CaptureNotifier.enabled(this), "captured_transactions", enable -> {
            CaptureNotifier.setEnabled(this, enable);
            if (enable) askNotificationPermission();
            render();
        });
        notificationSetting("تذكير يومي بالمصاريف", "ملخص مصروف اليوم والميزانية", getSharedPreferences("reminder_settings", MODE_PRIVATE).getBoolean("daily_enabled", false), "daily_summary", this::setDailyReminder);
        boolean monthlyEnabled=MonthlySummaryReceiver.settings(this).getBoolean("enabled",true);
        notificationSetting("تنبيه ملخص الشهر", "أول الشهر من 9 صباحًا", monthlyEnabled, MonthlySummaryReceiver.CHANNEL, enable -> {
            MonthlySummaryReceiver.settings(this).edit().putBoolean("enabled",enable).apply();
            if(enable)askNotificationPermission();MonthlySummaryReceiver.refresh(this);render();
        });
        setting("إعدادات إشعارات الجهاز", () -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        note("تنبيه واحد أول الشهر من 9 صباحًا؛ ممكن يتأخر بسبب البطارية أو إغلاق الجهاز. الملخص يفضل متاح في التحليلات.");
                break;
            case "data":
                note("مصادر العمليات محفوظة مشفرة. CSV يحتوي على معاملاتك، ويحتاج مشاركة واعية منك.");
                setting(appLock.enabled()?"قفل التطبيق: مفعل":"قفل التطبيق: متوقف",this::appLockSettingsDialog);
                if (!appLock.deviceSecure()) {
                    View lockRow=settingsGroup.getChildAt(settingsGroup.getChildCount()-1);
                    lockRow.setEnabled(false);lockRow.setAlpha(0.5f);lockRow.setOnClickListener(null);
                    note("فعّل قفل الشاشة في إعدادات الجهاز أولًا لاستخدام قفل التطبيق.");
                }
                setting("سياسة الخصوصية",this::privacyPolicyDialog);
                setting("استيراد معاملات CSV",this::chooseCsvImport);
                setting("تصدير معاملات "+selectedMonth.toString().substring(0,7)+" CSV",this::exportCsv);
                section("نسخة احتياطية",null,null);
                long lastBackup=LocalBackup.lastBackupAt(this);
                note(lastBackup==0?"لسه ما عملتش نسخة. ملف واحد مشفر بكلمة سر تختارها، يتحفظ في المكان اللي تختاره ويرجع على أي جهاز."
                        :"آخر نسخة: "+Instant.ofEpochMilli(lastBackup).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_LABEL));
                setting("نسخة احتياطية",this::backupDialog);
                setting("استعادة نسخة",this::chooseRestore);
                if(BuildConfig.DEBUG) {
                    section("أدوات متقدمة",null,null);
                    setting("أدوات المراجعة",this::reviewToolsDialog);
                }
                // Keep access to existing records without promoting unused financial modules.
                if(!db.assets().isEmpty())setting("الأصول المسجلة",()->{selectedTab=8;render();});
                if(!db.debts().isEmpty())setting("الديون المسجلة",()->{selectedTab=9;render();});
                section("حذف البيانات",null,null);
        setting("حذف كل البيانات المحلية", () -> new ChoiceDialogBuilder()
                .setTitle("حذف كل البيانات؟")
                .setMessage("سيتم حذف العمليات والأرصدة والحسابات والميزانيات والديون والأصول والقواعد. لا يمكن التراجع. رسائل SMS على الهاتف لن تُحذف، ولن تُستورد تلقائيًا من جديد.")
                .setPositiveButton("حذف البيانات", (dialog, which) -> {
                    db.clearAllData();
                    getSharedPreferences(DailySummary.SETTINGS,MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences(DailyPointsStore.SETTINGS,MODE_PRIVATE).edit().clear().apply();
                    getPreferences(MODE_PRIVATE).edit().putBoolean("initial_sms_import_done", true).apply();
                    selectedTab = 0; render();
                    toast("تم حذف البيانات المحلية");
                }).setNegativeButton("إلغاء", null).show());
                break;
            default:
                note("Follow Money · "+GitHubSupport.version(this));
                setting("ما الجديد",this::whatsNewDialog);
                setting("سياسة الخصوصية",this::privacyPolicyDialog);
                setting("الإبلاغ عن مشكلة",this::reportProblem);
                setting("اقتراح تحسين",()->supportDialog(true));
                setting("نسخ بيانات التشخيص",this::copySupportDetails);
                setting("للمطورين: متابعة على GitHub",()->openGitHub(Uri.parse(GitHubSupport.REPOSITORY+"/issues")));
                note("الدعم بالإيميل: "+EmailSupport.ADDRESS+". التطبيق بيضيف بيانات النسخة والجهاز والنظام واللغة، وتقدر تختار إرفاق آخر عطل بعد معاينته؛ إنت بتراجع المسودة وبتضغط إرسال في تطبيق الإيميل. بلاغات GitHub عامة وتحتاج حسابًا.");
                break;
        }
    }

    private void settingsCategory(String title,String subtitle,int icon,String section) {
        if(settingsGroup==null)settingsGroup=listCard(page);else divider(settingsGroup);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));
        ImageView image=tintedIcon(icon,ACCENT);image.setPadding(dp(7),dp(7),dp(7),dp(7));image.setBackground(rounded(ACCENT_PALE,10));
        row.addView(image,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title,15,INK,true));copy.addView(label(subtitle,11,MUTED,false));
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-2,1);space.setMarginStart(dp(12));row.addView(copy,space);
        row.addView(tintedIcon(R.drawable.ic_chevron_forward,MUTED),new LinearLayout.LayoutParams(dp(18),dp(18)));
        row.setContentDescription(title+"، "+subtitle);row.setOnClickListener(v->{settingsSection=section;render();});settingsGroup.addView(row);
    }

    private void backFromSettings() {
        if(settingsSection.isEmpty())selectedTab=0;else settingsSection="";
        render();
    }

    private void reportProblem() {
        supportDialog(false);
    }

    private void appLockSettingsDialog() {
        String timeout=appLock.timeoutSeconds()==0?"فورًا":appLock.timeoutSeconds()==60?"دقيقة":"5 دقائق";
        String[] choices={appLock.enabled()?"إيقاف قفل التطبيق":"تشغيل قفل التطبيق",
                "القفل بعد: "+timeout,
                "منع لقطات الشاشة: "+(appLock.securePreview()?"مفعّل":"متوقف")};
        new ChoiceDialogBuilder().setTitle("قفل التطبيق")
                .setItems(choices,(dialog,index)->{
                    if(index==0) {
                        if(appLock.enabled()){appLock.disable();render();}
                        else appLock.enable(this::render);
                    } else if(index==1) {
                        new ChoiceDialogBuilder().setTitle("اقفل التطبيق بعد")
                                .setItems(new String[]{"فورًا","بعد دقيقة","بعد 5 دقائق"},(d,which)->{
                                    appLock.setTimeoutSeconds(new int[]{0,60,300}[which]);render();
                                }).show();
                    } else {
                        appLock.setSecurePreview(!appLock.securePreview());render();
                    }
                }).show();
    }

    private void privacyPolicyDialog() {
        try (java.io.InputStream stream=getAssets().open("privacy-policy.txt")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int read;
            while((read=stream.read(buffer))!=-1)bytes.write(buffer,0,read);
            LinearLayout policy=form();
            TextView text=label(bytes.toString("UTF-8"),14,INK,false);
            text.setTextIsSelectable(true);policy.addView(text);
            new ChoiceDialogBuilder().setTitle("سياسة الخصوصية · Privacy Policy")
                    .setView(scrollForm(policy)).setPositiveButton("إغلاق",null).show();
        } catch(java.io.IOException error) {
            toast("تعذر فتح السياسة؛ تواصل مع "+EmailSupport.ADDRESS);
        }
    }

    private void supportDialog(boolean suggestion) {
        LinearLayout form=form();
        form.addView(label("إلى: "+EmailSupport.ADDRESS,12,MUTED,false));
        EditText description=input(suggestion?"وصف الاقتراح والفائدة منه":"إيه اللي حصل؟ وإيه اللي كنت متوقعه؟",form);
        description.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setSingleLine(false);description.setMinLines(3);description.setGravity(Gravity.TOP|Gravity.START);
        description.getLayoutParams().height=android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        description.setPadding(dp(14),dp(10),dp(14),dp(10));
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        form.addView(label("متكتبش أرقام حسابات أو كروت، ولا تلصق رسائل البنك الأصلية.",12,CORAL,false));
        form.addView(label("بيانات النسخة والجهاز والنظام واللغة:",12,MUTED,false));
        TextView metadata=label(GitHubSupport.details(this),12,INK,false);
        metadata.setTextDirection(View.TEXT_DIRECTION_LTR);metadata.setTextIsSelectable(true);form.addView(metadata);
        String lastCrash=suggestion?null:CrashLog.latest(this);
        CheckBox attachCrash=new CheckBox(this);
        if(lastCrash!=null) {
            attachCrash.setText("إرفاق آخر عطل كنص في المسودة");attachCrash.setTypeface(REGULAR);form.addView(attachCrash);
            TextView preview=label(lastCrash,11,INK,false);preview.setTextDirection(View.TEXT_DIRECTION_LTR);
            preview.setTextIsSelectable(true);preview.setVisibility(View.GONE);form.addView(preview);
            attachCrash.setOnCheckedChangeListener((button,checked)->preview.setVisibility(checked?View.VISIBLE:View.GONE));
        }
        java.util.function.Supplier<String> reviewed=()->description.getText().toString().trim()
                +(attachCrash.isChecked()&&lastCrash!=null?"\n\nآخر عطل (محلي):\n"+lastCrash:"");
        form.addView(label("هيفتح مسودة في تطبيق الإيميل؛ راجعها واضغط إرسال بنفسك. محتاج إيميل متظبط على الموبايل.",12,MUTED,false));
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle(suggestion?"اقتراح تحسين":"الإبلاغ عن مشكلة")
                .setView(scrollForm(form)).setPositiveButton("فتح مسودة إيميل",null)
                .setNeutralButton("نسخ الطلب",null).setNegativeButton("إلغاء",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String text=description.getText().toString().trim();
                if(text.isEmpty()){description.setError("اكتب وصف مختصر الأول");return;}
                try {startActivity(EmailSupport.draft(this,suggestion,reviewed.get()));}
                catch(android.content.ActivityNotFoundException error){copySupportRequest(suggestion,reviewed.get(),"مفيش تطبيق إيميل متاح؛ اتنسخ عنوان الدعم والطلب");}
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->copySupportRequest(suggestion,reviewed.get(),"اتنسخ عنوان الدعم والطلب"));
        });
        dialog.show();
    }

    private void copySupportRequest(boolean suggestion,String description,String message) {
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Follow Money support",EmailSupport.copyText(this,suggestion,description)));toast(message);}
        else toast("تعذر النسخ؛ عنوان الدعم: "+EmailSupport.ADDRESS);
    }

    private void copySupportDetails() {
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Follow Money diagnostics",GitHubSupport.details(this)));toast("تم نسخ بيانات النسخة والجهاز");}
    }

    private void openGitHub(Uri uri) {
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        catch(android.content.ActivityNotFoundException error){toast("مفيش متصفح متاح لفتح GitHub");}
    }

    private void merchantRulesScreen() {
        header("الاستيراد الذكي", "قواعد التجار", "+", () -> merchantRuleDialog(null));
        note("التصحيح المحفوظ للتاجر له الأولوية، ويشمل اختلاف كتابة الاسم والفروع المعروفة. تقدر تحفظه مباشرة من تعديل العملية.");
        List<ExpenseDb.MerchantRule> rules = db.merchantRules();
        if (rules.isEmpty()) empty("مفيش قواعد لسه", "حدد اسم تاجر لتصنيفه تلقائيًا أو تجاهل عملياته");
        for (ExpenseDb.MerchantRule rule : rules) {
            LinearLayout card = card();
            card.addView(label(rule.matchText.startsWith("=")?rule.matchText.substring(1):rule.matchText, 17, INK, true));
            card.addView(label(rule.ignored ? "تجاهل الرسائل الجديدة" : "تصنيف: " + rule.category,
                    13, rule.ignored ? 0xffd84d55 : ACCENT, false));
            card.setOnClickListener(v -> new ChoiceDialogBuilder().setTitle(rule.matchText)
                    .setItems(new String[]{"تعديل", "حذف"}, (dialog, which) -> {
                        if (which == 0) merchantRuleDialog(rule);
                        else { db.deleteMerchantRule(rule.id); render(); }
                    }).show());
        }
    }

    private void merchantRuleDialog(ExpenseDb.MerchantRule existing) {
        LinearLayout form = form();
        EditText match = input("اسم التاجر كما يظهر في الرسالة", form);
        Spinner category = spinner("التصنيف", CATEGORIES, form);
        CheckBox ignored = new CheckBox(this);
        ignored.setText("تجاهل عملياته الجديدة");
        form.addView(ignored);
        if (existing != null) {
            match.setText(existing.matchText.startsWith("=")?existing.matchText.substring(1):existing.matchText);
            ignored.setChecked(existing.ignored);
            for (int i = 0; i < CATEGORIES.length; i++)
                if (CATEGORIES[i].equals(existing.category)) category.setSelection(i);
        }
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("قاعدة تاجر").setView(form)
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignoredDialog -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (match.getText().toString().trim().length() < 2) { toast("اكتب اسم تاجر مميز"); return; }
            if(existing!=null && existing.matchText.startsWith("=") && !ignored.isChecked() &&
                    !ExpenseDb.canRememberMerchant(match.getText().toString(),category.getSelectedItem().toString())) {
                toast("اختار تصنيف مشتريات واسم تاجر مميز");return;
            }
            if (existing != null) db.deleteMerchantRule(existing.id);
            if(existing!=null && existing.matchText.startsWith("=") && !ignored.isChecked())
                db.rememberMerchantCategory(match.getText().toString(),category.getSelectedItem().toString());
            else db.putMerchantRule(match.getText().toString(), category.getSelectedItem().toString(), ignored.isChecked());
            dialog.dismiss(); render();
        }));
        dialog.show();
    }

    private void assetsScreen() {
        header("متابعة الأموال", "الأصول وصافي الثروة", "+", () -> assetDialog(null));
        note("صافي القيمة يجمع أرصدة الحسابات والمحافظ والأصول المسجلة فقط. أرصدة البطاقات تظهر في صفحة الحسابات ولا تُجمع هنا، لتجنب عدّ نفس المال مرتين. الرصيد المحسوب من العمليات تقديري.");
        for (String currency : CURRENCIES) {
            long accounts = 0, assets = 0, owedToMe = 0, iOwe = 0;
            int knownAccounts = 0;
            for (ExpenseDb.PaymentSource source : db.accountSources(false)) {
                if (!"حساب".equals(source.kind) && !"محفظة".equals(source.kind)) continue;
                ExpenseDb.BalanceSnapshot balance = db.currentBalance(source.id, currency);
                if (balance != null) { accounts += balance.amountMinor; knownAccounts++; }
            }
            for (ExpenseDb.Asset asset : db.assets()) if (currency.equals(asset.currency)) assets += asset.valueMinor;
            for (ExpenseDb.Debt debt : db.debts()) if (currency.equals(debt.currency)) {
                if ("owed_to_me".equals(debt.direction)) owedToMe += debt.remainingMinor;
                else iOwe += debt.remainingMinor;
            }
            if (knownAccounts == 0 && assets == 0 && owedToMe == 0 && iOwe == 0) continue;
            summaryCard("صافي القيمة المسجلة · " + currency,
                    money(accounts + assets + owedToMe - iOwe, currency),
                    "حسابات " + money(accounts, currency) + " + أصول " + money(assets, currency) +
                            " + لك " + money(owedToMe, currency) + " − عليك " + money(iOwe, currency));
        }
        section("الأصول", null, null);
        if (db.assets().isEmpty()) empty("مفيش أصول مسجلة", "أضف ذهب أو فضة أو أسهم أو أصل آخر بقيمته الحالية");
        for (ExpenseDb.Asset asset : db.assets()) {
            LinearLayout card = card();
            card.addView(label(asset.name + "  •  " + asset.kind, 17, INK, true));
            card.addView(label(money(asset.valueMinor, asset.currency), 18, ACCENT, true));
            card.addView(label("تقييم يدوي · " + Instant.ofEpochMilli(asset.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                    12, MUTED, false));
            card.setOnClickListener(v -> new ChoiceDialogBuilder().setTitle(asset.name)
                    .setItems(new String[]{"تحديث القيمة", "حذف"}, (dialog, which) -> {
                        if (which == 0) assetDialog(asset);
                        else { db.deleteAsset(asset.id); render(); }
                    }).show());
        }
    }

    private void assetDialog(ExpenseDb.Asset existing) {
        LinearLayout form = form();
        EditText name = input("اسم الأصل", form);
        Spinner kind = spinner("النوع", new String[]{"ذهب", "فضة", "أسهم", "عقار", "أخرى"}, form);
        EditText value = input("القيمة الحالية يدويًا", form);
        value.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        if (existing != null) {
            name.setText(existing.name);
            value.setText(String.format(Locale.ROOT, "%.2f", existing.valueMinor / 100.0));
            for (int i = 0; i < CURRENCIES.length; i++) if (CURRENCIES[i].equals(existing.currency)) currency.setSelection(i);
            for (int i = 0; i < kind.getCount(); i++) if (kind.getItemAtPosition(i).equals(existing.kind)) kind.setSelection(i);
        }
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle(existing == null ? "أصل جديد" : "تحديث الأصل").setView(form)
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (rejectMisreadAmount(value)) return;
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(value.getText().toString()));
            if (minor < 0 || name.getText().toString().trim().isEmpty()) { toast("راجع اسم الأصل وقيمته"); return; }
            db.putAsset(existing == null ? null : existing.id, name.getText().toString(),
                    kind.getSelectedItem().toString(), minor, currency.getSelectedItem().toString());
            dialog.dismiss(); render();
        }));
        dialog.show();
    }

    private void debtsScreen() {
        header("متابعة الأموال", "الديون", "+", this::debtDialog);
        note("سجل ما لك وما عليك، وحدّث المدفوعات. المبالغ هنا لا تضاف تلقائيًا إلى معاملات البنك.");
        if (db.debts().isEmpty()) empty("مفيش ديون مسجلة", "أضف أول دين وحدد إذا كان لك أو عليك");
        for (ExpenseDb.Debt debt : db.debts()) {
            LinearLayout card = card();
            card.addView(label(debt.name + "  •  " + ("owed_to_me".equals(debt.direction) ? "لي" : "عليّ"),
                    17, INK, true));
            card.addView(label("المتبقي " + money(debt.remainingMinor, debt.currency) + " من " +
                    money(debt.originalMinor, debt.currency), 15, ACCENT, true));
            if (debt.dueDate != null) card.addView(label("الموعد " + debt.dueDate, 12, MUTED, false));
            progress(card, debt.originalMinor - debt.remainingMinor, debt.originalMinor);
            card.setOnClickListener(v -> new ChoiceDialogBuilder().setTitle(debt.name)
                    .setItems(new String[]{"تسجيل سداد", "حذف"}, (dialog, which) -> {
                        if (which == 0) debtPaymentDialog(debt);
                        else { db.deleteDebt(debt.id); render(); }
                    }).show());
        }
    }

    private void debtDialog() {
        LinearLayout form = form();
        EditText name = input("الاسم أو الجهة", form);
        Spinner direction = spinner("الاتجاه", new String[]{"لي عند غيري", "عليّ لغيري"}, form);
        EditText amount = input("المبلغ", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        EditText due = dateInput("تاريخ الاستحقاق (اختياري)", form, null);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("دين جديد").setView(form)
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (rejectMisreadAmount(amount)) return;
                long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                String dueText = dateValue(due) == null ? "" : dateValue(due).toString();
                if (minor <= 0 || name.getText().toString().trim().isEmpty()) throw new IllegalArgumentException();
                db.addDebt(name.getText().toString(), direction.getSelectedItemPosition() == 0 ? "owed_to_me" : "i_owe",
                        minor, currency.getSelectedItem().toString(), dueText.isEmpty() ? null : dueText);
                dialog.dismiss(); render();
            } catch (RuntimeException error) { toast("راجع الاسم والمبلغ والتاريخ"); }
        }));
        dialog.show();
    }

    private void debtPaymentDialog(ExpenseDb.Debt debt) {
        LinearLayout form = form();
        EditText amount = input("المبلغ المسدد", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        new ChoiceDialogBuilder().setTitle("سداد " + debt.name).setView(form)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    if (rejectMisreadAmount(amount)) return;
                    long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                    if (minor <= 0) { toast("راجع المبلغ"); return; }
                    db.payDebt(debt.id, minor); render();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void paymentSourceDialog() {
        LinearLayout form = form();
        EditText name = input("اسم الوسيلة، مثال: فيزا البنك الأهلي", form);
        Spinner kind = spinner("نوع الوسيلة", new String[]{"بطاقة", "بطاقة ائتمان", "حساب", "محفظة", "تطبيق دفع", "كاش"}, form);
        EditText last4 = input("آخر 3 أو 4 أرقام للكارت أو الحساب (اختياري)", form);
        last4.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("وسيلة دفع جديدة").setView(form)
                .setPositiveButton("إضافة", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String digits = ExpenseParser.normalizeDigits(last4.getText().toString().trim());
                    if (name.getText().toString().trim().isEmpty() || (!digits.isEmpty() && !digits.matches("[0-9]{3,4}"))) {
                        toast("اكتب الاسم وآخر 4 أرقام صحيحين"); return;
                    }
                    try {
                        new SourceMutation(db).createReviewed(name.getText().toString(), kind.getSelectedItem().toString(), digits.isEmpty() ? null : digits);
                    } catch(RuntimeException error) {
                        toast("تعذر إضافة وسيلة الدفع؛ راجع البيانات وحاول مرة أخرى."); return;
                    }
                    dialog.dismiss();
                    render();
                }));
        dialog.show();
    }

    private void budgetDialog() {
        budgetPlanDialog(null);
    }

    private void budgetPlanDialog(ExpenseDb.BudgetPlan existing) {
        LinearLayout form = form();
        EditText name = input("اسم الميزانية", form);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        EditText amount = input("حد الإنفاق، مثال 5000", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner cycle = spinner("دورة التجديد", new String[]{"شهري", "أسبوعي", "مرة واحدة", "مخصص بالأيام"}, form);
        EditText anchor = dateInput("تاريخ البداية", form, LocalDate.now());
        EditText duration = input("عدد الأيام للمرة الواحدة أو المخصص", form);
        duration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        duration.setText("30");
        List<String> chosen = new ArrayList<>();
        TextView categories = label("حدد التصنيفات للتتبع  ⌄", 15, ACCENT, true);
        categories.setPadding(dp(12), dp(16), dp(12), dp(16));
        form.addView(categories);
        categories.setOnClickListener(v -> {
            boolean[] checked = new boolean[CATEGORIES.length];
            for (int i = 0; i < CATEGORIES.length; i++) checked[i] = chosen.contains(CATEGORIES[i]);
            new ChoiceDialogBuilder().setTitle("التصنيفات للتتبع")
                    .setMultiChoiceItems(CATEGORIES, checked, (dialog, which, enabled) -> {
                        if (enabled && !chosen.contains(CATEGORIES[which])) chosen.add(CATEGORIES[which]);
                        else if (!enabled) chosen.remove(CATEGORIES[which]);
                    }).setPositiveButton("تم", (dialog, which) ->
                            categories.setText("التصنيفات: " + String.join("، ", chosen))).show();
        });
        if (existing != null) {
            name.setText(existing.name);
            for (int i = 0; i < CURRENCIES.length; i++) if (CURRENCIES[i].equals(existing.currency)) currency.setSelection(i);
            amount.setText(String.format(Locale.ROOT, "%.2f", existing.amountMinor / 100.0));
            cycle.setSelection("weekly".equals(existing.cycle) ? 1 : "once".equals(existing.cycle) ? 2 :
                    "custom".equals(existing.cycle) ? 3 : 0);
            setDateValue(anchor, existing.anchor);
            duration.setText(Integer.toString(existing.durationDays));
            chosen.addAll(existing.categories);
            categories.setText("التصنيفات: " + String.join("، ", chosen));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle(existing == null ? "ميزانية جديدة" : "تعديل الميزانية").setView(scroll)
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        if (rejectMisreadAmount(amount)) return;
                        long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                        LocalDate start = java.util.Objects.requireNonNull(dateValue(anchor));
                        int days = Integer.parseInt(ExpenseParser.normalizeDigits(duration.getText().toString().trim()));
                        if (minor <= 0 || days <= 0 || name.getText().toString().trim().isEmpty() || chosen.isEmpty())
                            throw new IllegalArgumentException();
                        String type = new String[]{"monthly", "weekly", "once", "custom"}[cycle.getSelectedItemPosition()];
                        db.putBudgetPlan(existing == null ? null : existing.id, name.getText().toString(), minor,
                                currency.getSelectedItem().toString(), type, start, days, chosen);
                        dialog.dismiss(); render();
                    } catch (RuntimeException error) { toast("راجع الاسم والمبلغ والتاريخ والتصنيفات"); }
                }));
        dialog.show();
    }

    private void exportCsv() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "follow-money-" + selectedMonth.toString().substring(0, 7) + ".csv");
        startActivityForResult(intent, CSV_REQUEST);
    }

    private void chooseCsvImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, CSV_IMPORT_REQUEST);
    }

    // ---- Local backup and restore (LocalBackup + BackupVault): one password-encrypted file through the document picker ----

    private EditText passwordInput(String hint, LinearLayout parent) {
        EditText field = input(hint, parent);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setTypeface(REGULAR);
        return field;
    }

    private static char[] chars(EditText field) {
        int length = field.getText().length(); char[] out = new char[length];
        field.getText().getChars(0, length, out, 0); return out;
    }

    private void backupDialog() {
        LinearLayout form = form();
        form.addView(label("ملف واحد مشفر بكلمة سر إنت اللي تختارها (8 أحرف على الأقل). فيه عملياتك ونصوص رسائل البنك المحفوظة وإعداداتك. لو نسيت كلمة السر مفيش طريقة نفتح النسخة.", 13, MUTED, false));
        EditText first = passwordInput("كلمة السر", form);
        EditText second = passwordInput("تأكيد كلمة السر", form);
        new ChoiceDialogBuilder().setTitle("نسخة احتياطية").setView(form)
                .setPositiveButton("اختار مكان الحفظ", (dialog, which) -> {
                    char[] password = chars(first), confirm = chars(second);
                    boolean same = java.util.Arrays.equals(password, confirm);
                    BackupVault.wipe(confirm);
                    if (password.length < BackupVault.MIN_PASSWORD) { BackupVault.wipe(password); toast("كلمة السر لازم تكون 8 أحرف على الأقل"); return; }
                    if (!same) { BackupVault.wipe(password); toast("كلمتا السر مش متطابقتين"); return; }
                    BackupVault.wipe(pendingBackupPassword); pendingBackupPassword = password;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, BackupVault.fileName(LocalDate.now()));
                    startActivityForResult(intent, BACKUP_CREATE_REQUEST);
                }).setNegativeButton("إلغاء", null).show();
    }

    /** Builds the file on the capture worker (the database is read there) and streams it to the chosen document. */
    private void writeBackup(Uri uri, char[] password) {
        toast("بنجهز النسخة...");
        CaptureQueue.submit(() -> {
            String failure = null;
            try {
                byte[] file = LocalBackup.export(this, db, password);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new java.io.IOException("No output stream");
                    out.write(file);
                }
                LocalBackup.recordBackup(this);
            } catch (Exception error) {
                android.util.Log.e("FollowMoney", "Backup failed: " + error.getClass().getSimpleName());
                failure = "فشل حفظ النسخة. جرّب مكانًا آخر.";
            }
            String message = failure == null ? "اتحفظت النسخة. احتفظ بكلمة السر في مكان آمن." : failure;
            postUi(() -> { if (isFinishing() || isDestroyed()) return; toast(message); render(); });
        });
    }

    private void chooseRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, BACKUP_OPEN_REQUEST);
    }

    private void restorePasswordDialog(Uri uri) {
        LinearLayout form = form();
        form.addView(label("اكتب كلمة سر النسخة. هنعرض لك ملخصها قبل ما نستبدل أي حاجة.", 13, MUTED, false));
        EditText password = passwordInput("كلمة السر", form);
        new ChoiceDialogBuilder().setTitle("استعادة نسخة").setView(form)
                .setPositiveButton("افتح النسخة", (dialog, which) -> prepareRestore(uri, chars(password)))
                .setNegativeButton("إلغاء", null).show();
    }

    /** Reads and decrypts off the main thread; a refusal is one clear sentence and the current data is untouched. */
    private void prepareRestore(Uri uri, char[] password) {
        toast("بنفتح النسخة...");
        CaptureQueue.submit(() -> {
            LocalBackup.Prepared prepared = null; String failure = null;
            try {
                byte[] file;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("No input stream");
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    for (int read; (read = in.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
                    file = bytes.toByteArray();
                }
                prepared = LocalBackup.prepare(this, file, password);
            } catch (BackupVault.Rejected rejected) {
                failure = rejected.getMessage();
            } catch (Exception error) {
                BackupVault.wipe(password);
                android.util.Log.e("FollowMoney", "Restore preparation failed: " + error.getClass().getSimpleName());
                failure = "تعذر قراءة الملف. ما اتغيرش أي حاجة في بياناتك.";
            }
            final LocalBackup.Prepared ready = prepared; final String message = failure;
            postUi(() -> {
                if (isFinishing() || isDestroyed()) { if (ready != null) ready.discard(); return; }
                if (ready == null) { toast(message); return; }
                restoreSummaryDialog(ready);
            });
        });
    }

    private void restoreSummaryDialog(LocalBackup.Prepared prepared) {
        LocalBackup.Summary s = prepared.summary;
        String created = Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_LABEL);
        String text = "نسخة من " + created + "\n" + s.transactions + " عملية · " + s.sources + " حساب أو كارت\n\n" +
                "ستُستبدل كل البيانات الحالية بالنسخة دي: العمليات والحسابات والالتزامات والإعدادات. لا يمكن التراجع.";
        new ChoiceDialogBuilder().setTitle("استعادة النسخة؟").setMessage(text)
                .setPositiveButton("استبدل بياناتي بالنسخة", (dialog, which) -> applyRestore(prepared))
                .setNegativeButton("إلغاء", (dialog, which) -> prepared.discard())
                .setOnCancelListener(dialog -> prepared.discard()).show();
    }

    private void applyRestore(LocalBackup.Prepared prepared) {
        toast("بنستعيد النسخة...");
        CaptureQueue.submit(() -> {
            ExpenseDb restored = null; String failure = null;
            try { restored = prepared.apply(this); }
            catch (LocalBackup.RestoreFailed error) {
                android.util.Log.e("FollowMoney", "Restore failed: " + (error.getCause() == null ? "" : error.getCause().getClass().getSimpleName()) + (error.rolledBack ? ", previous data back" : ", rollback pending"));
                failure = error.rolledBack ? "فشلت الاستعادة ورجعت بياناتك السابقة كما كانت." : "فشلت الاستعادة. افتح التطبيق من جديد وراجع بياناتك.";
            } catch (Exception error) {
                android.util.Log.e("FollowMoney", "Restore failed: " + error.getClass().getSimpleName());
                failure = "فشلت الاستعادة. افتح التطبيق من جديد وراجع بياناتك.";
            }
            final ExpenseDb fresh = restored; final String message = failure;
            postUi(() -> {
                if (isFinishing() || isDestroyed()) return;
                // Success or rollback, the shared helper was reopened: point at it and forget cached rows.
                db = fresh != null ? fresh : AppResources.database(this); invalidateLedger(); ledgerChanges = null;
                selectedTab = 0; render();
                toast(message == null ? "تمت الاستعادة. راجع عملياتك وحساباتك." : message);
            });
        });
    }

    /** Thirty days without a backup: one dismissible card, no numbers, pointing to the settings entry. */
    private static final int WHATS_NEW_CARD_POINTS = 2;
    private android.widget.DatePicker periodPicker;
    private static final String APP_STATE = "app_state", LAST_SEEN_VERSION = "last_seen_version_code";

    private String readAsset(String name) {
        try (java.io.InputStream stream = getAssets().open(name)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int read;
            while ((read = stream.read(buffer)) != -1) bytes.write(buffer, 0, read);
            return bytes.toString("UTF-8");
        } catch (java.io.IOException unavailable) { return ""; }
    }

    /** Runs once per activity: decides from the stored version code whether this launch follows an update. */
    private void loadWhatsNew(String text) {
        SharedPreferences state = getSharedPreferences(APP_STATE, MODE_PRIVATE);
        int lastSeen = WhatsNew.lastSeen(state.getInt(LAST_SEEN_VERSION, WhatsNew.ABSENT),
                getPreferences(MODE_PRIVATE).getBoolean("intro_completed", false), db.transactionCount() > 0);
        whatsNew = WhatsNew.due(BuildConfig.VERSION_CODE, lastSeen) ? WhatsNew.forVersion(text, BuildConfig.VERSION_NAME) : null;
        if (whatsNew == null && lastSeen != BuildConfig.VERSION_CODE) state.edit().putInt(LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply();
    }

    private void whatsNewCard() {
        if (whatsNew == null) return;
        LinearLayout box = card();
        box.addView(label("جديد في النسخة " + whatsNew.version, 15, INK, true));
        // The card is a headline: two points at most. The rest is one tap away in the same dialog as the settings row.
        for (int i = 0; i < Math.min(WHATS_NEW_CARD_POINTS, whatsNew.points.size()); i++) box.addView(label("• " + whatsNew.points.get(i), 12, MUTED, false));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView done = label("تمام", 13, ACCENT, true); done.setPadding(0, dp(8), 0, 0);
        done.setOnClickListener(v -> {
            getSharedPreferences(APP_STATE, MODE_PRIVATE).edit().putInt(LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply();
            whatsNew = null; render();
        });
        actions.addView(done);
        if (whatsNew.points.size() > WHATS_NEW_CARD_POINTS) {
            TextView more = label("كل التفاصيل", 13, ACCENT, true); more.setPadding(0, dp(8), dp(24), 0);
            more.setOnClickListener(v -> whatsNewDialog());
            actions.addView(more);
        }
        box.addView(actions);
    }

    private void whatsNewDialog() {
        String text = WhatsNew.readable(readAsset("whats-new.txt"));
        if (text.isEmpty()) { toast("لا توجد ملاحظات للنسخة دي"); return; }
        LinearLayout notes = form();
        TextView body = label(text, 14, INK, false);
        body.setTextIsSelectable(true); notes.addView(body);
        new ChoiceDialogBuilder().setTitle("ما الجديد").setView(scrollForm(notes)).setPositiveButton("إغلاق", null).show();
    }

    private void backupReminderCard() {
        if (!LocalBackup.reminderDue(this, db.transactionCount(), System.currentTimeMillis())) return;
        LinearLayout box = card();
        box.addView(label("خد نسخة احتياطية", 15, INK, true));
        box.addView(label("ملف واحد مشفر بكلمة سرك يرجّع عملياتك وإعداداتك على أي جهاز. مفيش سحابة.", 12, MUTED, false));
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView go = label("اعمل نسخة دلوقتي", 13, ACCENT, true); go.setPadding(0, dp(8), dp(16), 0);
        go.setOnClickListener(v -> backupDialog());
        TextView later = label("مش دلوقتي", 13, MUTED, true); later.setPadding(0, dp(8), 0, 0);
        later.setOnClickListener(v -> { LocalBackup.dismissReminder(this); render(); });
        actions.addView(go); actions.addView(later);
        box.addView(actions);
    }

    private void pinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, SummaryWidget.class);
        if (manager.isRequestPinAppWidgetSupported()) new ChoiceDialogBuilder().setTitle("حجم الويدجت")
                .setItems(new String[]{"صغير ٢×١ · المتبقي والصرف","متوسط ٤×١ · ميزانية اليوم والعمليات","كبير ٤×٢ · اليوم والأسبوع والشهر"},(d,w)->manager.requestPinAppWidget(w==0?new ComponentName(this,SmallSummaryWidget.class):w==2?new ComponentName(this,LargeSummaryWidget.class):provider,null,null)).show();
        else toast("أضف Follow Money من قائمة Widgets في الشاشة الرئيسية");
    }

    private void refreshWidgets() {
        DailyRefreshReceiver.refresh(this);
        if(dailyDetails!=null&&dailyDetails.isShowing())fillDailyDetails();
        refreshPointsSheet();
    }
    private void refreshPointsSheet(){
        if(pointsSheet==null||!pointsSheet.isShowing()||isFinishing()||isDestroyed())return;
        if(pointsSheetRevision==db.changes()&&LocalDate.now().equals(pointsSheetDate))return;
        String code=pointsSheetCurrency;boolean all=pointsSheetAll;
        pointsSheet.dismiss();pointsDialog(code,all);
    }

    private void previewCsvImport(Uri uri) {
        AppResources.read(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("تعذر فتح الملف");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192]; int size;
                while ((size = input.read(chunk)) >= 0) {
                    if (buffer.size() + size > 10 * 1024 * 1024) throw new IllegalArgumentException("ملف CSV أكبر من 10MB");
                    buffer.write(chunk, 0, size);
                }
                byte[] raw = buffer.toByteArray();
                List<CsvImport.Row> rows = CsvImport.parse(new String(raw, StandardCharsets.UTF_8));
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw);
                StringBuilder fileHash = new StringBuilder();
                for (byte value : hash) fileHash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
                StringBuilder preview = new StringBuilder(rows.size() + " عملية جاهزة للمراجعة\n");
                for (int i = 0; i < Math.min(3, rows.size()); i++) {
                    CsvImport.Row row = rows.get(i);
                    preview.append("\n").append(row.date).append(" · ").append(row.merchant)
                            .append(" · ").append(money(row.amountMinor, row.currency));
                }
                postUi(() -> new ChoiceDialogBuilder().setTitle("استيراد CSV")
                        .setMessage(preview.toString())
                        .setPositiveButton("استيراد", (dialog, which) -> CaptureQueue.submit(() -> {
                            try {
                                CsvImportResult result = db.importCsvBatchDetailed(fileHash.toString(), rows);
                                // A caller-owned transaction is not a committed import: never report success first.
                                if (!result.committed) {
                                    postUi(() -> new ChoiceDialogBuilder().setTitle("لم تُعتمد العملية بعد")
                                            .setMessage("لن نعرض نجاحًا قبل تأكيد الحفظ. أعد المحاولة.")
                                            .setPositiveButton("تمام", null).show());
                                    return;
                                }
                                StringBuilder message = new StringBuilder("تم استيراد " + result.created + " عملية جديدة");
                                if (result.alreadyPresent > 0)
                                    message.append("، وتخطينا ").append(result.alreadyPresent).append(" سطرًا مسجلًا من قبل");
                                if (result.excluded > 0)
                                    message.append("، و").append(result.excluded).append(" سطرًا مستبعدًا عندك");
                                String summary = message.toString();
                                postUi(() -> { toast(summary); render(); });
                            } catch (RuntimeException failed) {
                                postUi(() -> new ChoiceDialogBuilder().setTitle("تعذر استيراد CSV")
                                        .setMessage("لم نحفظ أي سطر من الملف، وبياناتك زي ما كانت. أعد المحاولة، ولو تكررت المشكلة صدّر الملف تاني.")
                                        .setPositiveButton("تمام", null).show());
                            }
                        }))
                        .setNegativeButton("إلغاء", null).show());
            } catch (Exception error) {
                postUi(() -> new ChoiceDialogBuilder().setTitle("تعذر استيراد CSV")
                        .setMessage(error.getMessage() == null ? "راجع صيغة الملف" : error.getMessage())
                        .setPositiveButton("تمام", null).show());
            }
        });
    }

    @Override public void startActivityForResult(Intent intent, int requestCode) {
        if (appLock != null) appLock.beginOwnResult(requestCode);
        try { super.startActivityForResult(intent, requestCode); }
        catch (RuntimeException error) {
            if (appLock != null) appLock.endOwnResult(requestCode);
            throw error;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (appLock != null && appLock.onActivityResult(requestCode,resultCode)) return;
        if(requestCode==PROFILE_PHOTO_REQUEST){if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)saveProfilePhoto(data.getData());return;}
        if (requestCode == VOICE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> phrases = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (phrases != null && !phrases.isEmpty()) manualDialog(phrases.get(0));
            }
            return;
        }
        if (requestCode == CSV_IMPORT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) previewCsvImport(data.getData());
            return;
        }
        if (requestCode == BACKUP_CREATE_REQUEST) {
            char[] password = pendingBackupPassword; pendingBackupPassword = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && password != null) writeBackup(data.getData(), password);
            else BackupVault.wipe(password);
            return;
        }
        if (requestCode == BACKUP_OPEN_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) restorePasswordDialog(data.getData());
            return;
        }
        if (requestCode != CSV_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        StringBuilder csv = new StringBuilder("Date,Type,Merchant,Amount,Currency,Category,Source,Payment method,Destination,Note\r\n");
        for (ExpenseDb.Transaction tx : db.transactionsForMonth(selectedMonth)) {
            String date = Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate().toString();
            String sourceName = "";
            String destinationName = "";
            for (ExpenseDb.PaymentSource source : db.paymentSources())
                if (tx.paymentSourceId != null && tx.paymentSourceId == source.id) sourceName = source.name;
            for (ExpenseDb.PaymentSource source : db.paymentSources())
                if (tx.destinationSourceId != null && tx.destinationSourceId == source.id) destinationName = source.name;
            csv.append(csvCell(date)).append(',').append(csvCell(tx.kind)).append(',').append(csvCell(tx.merchant)).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", tx.amountMinor / 100.0)).append(',')
                    .append(csvCell(tx.currency)).append(',').append(csvCell(tx.category)).append(',')
                    .append(csvCell(tx.source)).append(',').append(csvCell(sourceName)).append(',')
                    .append(csvCell(destinationName)).append(',').append(csvCell(tx.note == null ? "" : tx.note)).append("\r\n");
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("No output stream");
            out.write(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            toast("تم تصدير معاملات الشهر");
        } catch (Exception error) { toast("فشل تصدير الملف"); }
    }

    static String csvCell(String value) {
        // Text columns only. Numeric amount cells retain their number type.
        String start=value.stripLeading();
        if((!start.isEmpty() && "=+-@＝＋－＠".indexOf(start.charAt(0))>=0) ||
                value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n"))value="'"+value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void setDailyReminder(boolean enable) {
        SharedPreferences prefs = getSharedPreferences("reminder_settings", MODE_PRIVATE);
        prefs.edit().putBoolean("daily_enabled", enable).apply();
        if (enable) askNotificationPermission();
        else {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.cancel(DailySummaryNotifier.ID);
        }
        toast(enable ? "التذكير اليومي اتفعل" : "التذكير اليومي اتوقف");
        render();
    }

    /** Year, then a 12-month grid; months without any transaction are disabled. */
    private void chooseMonth() {
        java.util.Set<String> withData = new java.util.HashSet<>(db.months());
        java.util.TreeSet<Integer> years = new java.util.TreeSet<>();
        for (String month : withData) years.add(Integer.parseInt(month.substring(0, 4)));
        LocalDate today = LocalDate.now();
        years.add(today.getYear());
        int[] year = {selectedMonth.getYear()};
        LinearLayout box = form();
        LinearLayout yearsRow = new LinearLayout(this); yearsRow.setGravity(Gravity.CENTER);
        box.addView(yearsRow);
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(-1, -2); gridParams.topMargin = dp(12);
        box.addView(grid, gridParams);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("اختار الشهر").setView(box).setNegativeButton("إلغاء", null).create();
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            yearsRow.removeAllViews();
            Integer previous = years.lower(year[0]), next = years.higher(year[0]);
            if (previous != null) addChip(yearsRow, Integer.toString(previous), false, () -> { year[0] = previous; draw[0].run(); });
            addChip(yearsRow, Integer.toString(year[0]), true, () -> {});
            if (next != null) addChip(yearsRow, Integer.toString(next), false, () -> { year[0] = next; draw[0].run(); });
            grid.removeAllViews();
            for (int r = 0; r < 4; r++) {
                LinearLayout row = new LinearLayout(this);
                for (int c = 0; c < 3; c++) {
                    int m = r * 3 + c + 1;
                    boolean current = year[0] == today.getYear() && m == today.getMonthValue();
                    boolean enabled = withData.contains(String.format(Locale.ROOT, "%04d-%02d", year[0], m)) || current;
                    boolean selected = selectedMonth.getYear() == year[0] && selectedMonth.getMonthValue() == m;
                    TextView cell = label(MONTH_NAMES[m - 1], 14, selected ? onPrimary() : enabled ? INK : MUTED, selected);
                    cell.setGravity(Gravity.CENTER);
                    cell.setPadding(0, dp(12), 0, dp(12));
                    cell.setBackground(selected ? rounded(primaryFill(), 14) : outlined(SURFACE, BORDER, 14));
                    cell.setAlpha(enabled ? 1f : 0.35f);
                    cell.setEnabled(enabled);
                    cell.setContentDescription(MONTH_NAMES[m - 1] + " " + year[0] + (enabled ? "" : "، مفيش عمليات"));
                    cell.setOnClickListener(v -> {
                        selectedMonth = LocalDate.of(year[0], m, 1);
                        transactionPeriod = null; transactionCurrencyFilter = ""; transactionExcludeTransfers = false;
                        if (selectedTab == 4 || selectedTab == 3 || selectedTab == 13) insightsRange = MoneyFlow.Range.MONTH;
                        dialog.dismiss(); render();
                    });
                    LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, -2, 1);
                    cellParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                    row.addView(cell, cellParams);
                }
                grid.addView(row);
            }
        };
        draw[0].run();
        box.addView(label("الشهور الباهتة مفيهاش عمليات مسجلة.", 12, MUTED, false));
        dialog.show();
    }

    private void confirmDeleteTransaction(ExpenseDb.Transaction item) {
        if(new CardSettlements(db).related(item.id)){toast("فك توزيع سداد البطاقة قبل حذف العملية أو رسومها");return;}
        new ChoiceDialogBuilder().setTitle("حذف العملية؟")
                .setMessage(item.merchant + " · " + money(item.amountMinor, item.currency))
                .setPositiveButton("حذف", (dialog, which) -> {
                    db.deleteTransaction(item.id);
                    returnFromTransaction();toast("اتحذفت العملية ورجعت لمكانك");
                }).setNegativeButton("إلغاء", null).show();
    }

    private void editTransactionDialog(ExpenseDb.Transaction item) {
        if(new CardSettlements(db).related(item.id)){toast("فك توزيع سداد البطاقة قبل تعديل العملية أو رسومها");return;}
        LinearLayout form = form();
        Spinner kind = spinner("نوع العملية", new String[]{"مصروف", "دخل", "تحويل"}, form);
        kind.setSelection("income".equals(item.kind) ? 1 : "transfer".equals(item.kind) ? 2 : 0);
        EditText merchant = input("الجهة", form);
        merchant.setText(item.merchant);
        EditText amount = input("المبلغ", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.format(Locale.ROOT, "%.2f", item.amountMinor / 100.0));
        Spinner currency = spinner("العملة", CURRENCIES, form);
        for (int i = 0; i < CURRENCIES.length; i++)
            if (CURRENCIES[i].equals(item.currency)) currency.setSelection(i);
        Spinner category = spinner("التصنيف", CATEGORIES, form);
        for (int i = 0; i < CATEGORIES.length; i++)
            if (CATEGORIES[i].equals(item.category)) category.setSelection(i);
        List<ExpenseDb.PaymentSource> sources = paymentOptions(item.paymentSourceId,item.destinationSourceId);
        Spinner payment = spinner("وسيلة الدفع", sourceNames(sources), form);
        Spinner destination = spinner("التحويل إلى", sourceNames(sources), form);
        for (int i = 0; i < sources.size(); i++)
            if (item.paymentSourceId != null && item.paymentSourceId == sources.get(i).id) payment.setSelection(i + 1);
        for (int i = 0; i < sources.size(); i++)
            if (item.destinationSourceId != null && item.destinationSourceId == sources.get(i).id) destination.setSelection(i + 1);
        EditText date = dateInput("التاريخ", form, Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate());
        EditText note = input("وصف أو ملاحظة", form);
        if (item.note != null) note.setText(item.note);
        CheckBox remember=new CheckBox(this);remember.setText("افتكر التصنيف للتاجر ده في العمليات الجديدة");
        remember.setTextColor(INK);remember.setTypeface(REGULAR);remember.setChecked(false);
        remember.setVisibility("expense".equals(item.kind) && ExpenseDb.canRememberMerchant(item.merchant,"أخرى") ? View.VISIBLE : View.GONE);
        form.addView(remember);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("تعديل المعاملة")
                .setView(scrollForm(form)).setPositiveButton("حفظ", null)
                .setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (rejectMisreadAmount(amount)) return;
                long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                if (minor <= 0 || merchant.getText().toString().trim().isEmpty()) {
                    toast("راجع الجهة والمبلغ");
                    return;
                }
                String type = new String[]{"expense", "income", "transfer"}[kind.getSelectedItemPosition()];
                Long from = selectedSource(payment, sources);
                Long to = selectedSource(destination, sources);
                if ("transfer".equals(type) && (from == null || to == null || from.equals(to))) {
                    toast("اختار حساب المصدر والوجهة للتحويل"); return;
                }
                try {
                    LocalDate day = java.util.Objects.requireNonNull(dateValue(date));
                    boolean learn=remember.isChecked() && "expense".equals(type);
                    if(learn && !ExpenseDb.canRememberMerchant(merchant.getText().toString(),category.getSelectedItem().toString())) {
                        toast("حفظ التصنيف متاح للمشتريات، مش التحويلات أو الأقساط");return;
                    }
                    MutationResult result=new TransactionMutation(db).update(item.id, minor, currency.getSelectedItem().toString(),
                            merchant.getText().toString(), "income".equals(type) ? "دخل" :
                                    "transfer".equals(type) ? "تحويلات" : category.getSelectedItem().toString(),
                            from, type, "transfer".equals(type) ? to : null, note.getText().toString(),
                            day.atTime(Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalTime())
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),learn);
                    if(!result.changed()){
                        toast(result.outcome==MutationResult.Outcome.NOT_FOUND
                                ? "العملية دي لم تعد موجودة؛ لم نحفظ تعديل أو قاعدة تاجر."
                                : "لم يتم تطبيق التعديل؛ راجع العملية وحاول مرة أخرى.");
                        return;
                    }
                } catch (RuntimeException error) { toast("تعذر الحفظ؛ راجع التاريخ والبيانات"); return; }
                dialog.dismiss();
                if (selectedTab == 10) selectedTransaction=db.transactionById(item.id);
                render();
                toast("اتحفظ تعديل العملية");
                if(remember.isChecked() && "expense".equals(type)) offerPastMerchantCategory(merchant.getText().toString(),category.getSelectedItem().toString());
            });
        });
        dialog.show();
    }

    private void refreshMerchantCatalog() {
        SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        if(prefs.getInt("merchant_catalog_version",0)>=1) return;
        Context app=getApplicationContext();
        CaptureQueue.submit(()->{
            try(ExpenseDb catalogDb=new ExpenseDb(app)) {
                int count=catalogDb.refreshMerchantCategories();
                prefs.edit().putInt("merchant_catalog_version",1).apply();
                postUi(()->{if(!isFinishing() && !isDestroyed()){render();if(count>0)toast("اتحسن تصنيف "+count+" عمليات تلقائية");}});
            } catch(RuntimeException error) {android.util.Log.e("FollowMoney","Merchant categories skipped: "+error.getClass().getSimpleName());}
        });
    }

    private void offerPastMerchantCategory(String merchant,String category) {
        int count=db.merchantCategoryCandidates(merchant,category).size();
        if(count==0){toast("اتحفظ التصنيف للعمليات الجديدة");return;}
        new ChoiceDialogBuilder().setTitle("نطبق التصنيف على السابق؟")
                .setMessage(count+" عمليات تلقائية لنفس التاجر ممكن تتصنف «"+category+"». العمليات اللي عدلتها قبل كده مش هتتغير.")
                .setPositiveButton("طبق على "+count+" عمليات",(d,w)->{int changed=db.applyMerchantCategory(merchant,category);render();toast("اتحدث تصنيف "+changed+" عمليات");})
                .setNegativeButton("الجديد فقط",null).show();
    }

    private void quickAddChooser() {
        new ChoiceDialogBuilder().setTitle("إضافة عملية")
                .setItems(new String[]{"يدوي", "تسجيل بالصوت", "قسم فاتورة"}, (dialog, which) -> {
                    if (which == 0) manualDialog();
                    else if (which == 1) voiceInput();
                    else splitBillDialog();
                }).show();
    }

    private void voiceInput() {
        new ChoiceDialogBuilder().setTitle("الإدخال بالصوت")
                .setMessage("التعرف على الصوت بيتم من خلال خدمة النظام على جهازك. مزود الخدمة قد يرسل التسجيل لسيرفراته حسب إعداداته وسياسة خصوصيته. Follow Money يستقبل النص الناتج لتراجع العملية قبل حفظها، ولا يحفظ التسجيل الصوتي. تقدر تختار الإدخال اليدوي بدل الصوت.")
                .setPositiveButton("موافق، افتح خدمة الصوت",(dialog,which)->startVoiceInput())
                .setNegativeButton("إلغاء",null).show();
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "قول العملية والمبلغ، وراجعها قبل الحفظ");
        try { startActivityForResult(intent, VOICE_REQUEST); }
        catch (Exception error) { toast("خدمة التعرف على الصوت غير متاحة على الجهاز"); }
    }

    private void manualDialog() { manualDialog(null); }

    private void manualDialog(String spoken) {
        LinearLayout form = form();
        Spinner kind = spinner("نوع العملية", new String[]{"مصروف", "دخل", "تحويل"}, form);
        EditText merchant = input("الجهة أو وصف العملية", form);
        EditText amount = input("المبلغ، مثال 250.00", form);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        Spinner category = spinner("التصنيف", CATEGORIES, form);
        List<ExpenseDb.PaymentSource> sources = db.selectableSources();
        Spinner payment = spinner("وسيلة الدفع", sourceNames(sources), form);
        Spinner destination = spinner("التحويل إلى", sourceNames(sources), form);
        EditText date = dateInput("التاريخ", form, LocalDate.now());
        EditText note = input("وصف أو ملاحظة", form);
        if (spoken != null) {
            String normalized = ExpenseParser.normalizeDigits(spoken);
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("[0-9]+(?:[.,][0-9]{1,2})?").matcher(normalized);
            if (number.find()) {
                amount.setText(number.group());
                String description = (normalized.substring(0, number.start()) + " " + normalized.substring(number.end()))
                        .replaceAll("(?i)(جنيه|جنيهات|EGP|صرفت|دفعت|اشتريت|من|في)", " ").trim();
                merchant.setText(description.isEmpty() ? "عملية بالصوت" : description);
            } else merchant.setText(spoken);
            note.setText("مراجعة نص الصوت: " + spoken);
        }
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("عملية جديدة").setView(scrollForm(form))
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (rejectMisreadAmount(amount)) return;
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
            String name = merchant.getText().toString().trim();
            if (minor <= 0 || name.isEmpty()) {
                toast("اكتب الجهة والمبلغ بشكل صحيح");
                return;
            }
            String type = new String[]{"expense", "income", "transfer"}[kind.getSelectedItemPosition()];
            Long from = selectedSource(payment, sources);
            Long to = selectedSource(destination, sources);
            if ("transfer".equals(type) && (from == null || to == null || from.equals(to))) {
                toast("اختار حساب المصدر والوجهة للتحويل"); return;
            }
            try {
                LocalDate day = java.util.Objects.requireNonNull(dateValue(date));
                boolean added = db.addManualEntry(day.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        minor, currency.getSelectedItem().toString(), name,
                        "income".equals(type) ? "دخل" : "transfer".equals(type) ? "تحويلات" : category.getSelectedItem().toString(),
                        from, type, "transfer".equals(type) ? to : null, note.getText().toString());
                if (!added) { toast("لم تُحفظ العملية؛ راجع البيانات وقواعد التجار"); return; }
            } catch (RuntimeException error) { toast("اختار التاريخ من الكالندر"); return; }
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void splitBillDialog() {
        LinearLayout form = form();
        EditText merchant = input("المكان أو الفاتورة", form);
        EditText total = input("إجمالي الفاتورة", form);
        total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText people = input("عدد الأشخاص معاك", form);
        people.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Spinner currency = spinner("العملة", CURRENCIES, form);
        Spinner category = spinner("التصنيف", CATEGORIES, form);
        List<ExpenseDb.PaymentSource> sources = db.selectableSources();
        Spinner payment = spinner("وسيلة دفع حصتك", sourceNames(sources), form);
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("قسم فاتورة").setView(form)
                .setPositiveButton("سجل حصتي", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (rejectMisreadAmount(total)) return;
                long amount = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(total.getText().toString()));
                int count = Integer.parseInt(ExpenseParser.normalizeDigits(people.getText().toString().trim()));
                if (amount <= 0 || count < 2 || merchant.getText().toString().trim().isEmpty()) throw new IllegalArgumentException();
                boolean added = db.addSplitShare(System.currentTimeMillis(), amount, count, currency.getSelectedItem().toString(),
                        merchant.getText().toString(), category.getSelectedItem().toString(), selectedSource(payment, sources),
                        "حصتي من فاتورة " + money(amount, currency.getSelectedItem().toString()) +
                                " مقسمة على " + count + " أشخاص");
                if (!added) {
                    new ChoiceDialogBuilder().setTitle("الفاتورة دي مسجلة بالفعل")
                            .setMessage("إضافة حصتك هنا هتعمل مصروف مكرر فوق الدفع الأصلي. لم نحفظ أي مبلغ جديد ولم نغير العملية المسجلة. " +
                                    "ربط الحصة بالدفع والمستحقات غير متاح حاليًا.")
                            .setPositiveButton("رجوع", null).show();
                    return;
                }
                dialog.dismiss(); render();
            } catch (RuntimeException error) { toast("راجع إجمالي الفاتورة وعدد الأشخاص"); }
        }));
        dialog.show();
    }

    /**
     * Ids of expenses that look like a monthly commitment: another payment to the same merchant in the
     * same currency about a month earlier. Grouped by merchant so each list is scanned once, not per row.
     */
    static Set<Long> recurringCandidateIds(List<ExpenseDb.Transaction> items, List<ExpenseDb.Recurring> confirmed) {
        Set<Long> ids = new HashSet<>();
        Set<String> confirmedKeys = new HashSet<>();
        for (ExpenseDb.Recurring r : confirmed)
            if (r.merchant != null) confirmedKeys.add(r.merchant.toLowerCase(Locale.ROOT) + "\n" + r.currency);
        Map<String, List<ExpenseDb.Transaction>> groups = new LinkedHashMap<>();
        for (ExpenseDb.Transaction item : items) {
            if (!"expense".equals(item.kind)) continue;
            groups.computeIfAbsent(item.merchant.toLowerCase(Locale.ROOT) + "\n" + item.currency, key -> new ArrayList<>()).add(item);
        }
        for (List<ExpenseDb.Transaction> group : groups.values()) {
            group.sort((first, second) -> Long.compare(second.when, first.when));
            for (int i = 0; i < group.size(); i++) {
                ExpenseDb.Transaction recent = group.get(i);
                if (ExpenseParser.genericMerchant(recent.merchant) || confirmedKeys.contains(recent.merchant.toLowerCase(Locale.ROOT) + "\n" + recent.currency) ||
                        "تحويلات".equals(recent.category) || "رسوم تحويل".equals(recent.category) || "رسوم بنكية".equals(recent.category) || "سحب نقدي".equals(recent.category)) continue;
                LocalDate current = Instant.ofEpochMilli(recent.when).atZone(ZoneId.systemDefault()).toLocalDate();
                for (int j = i + 1; j < group.size(); j++) {
                    ExpenseDb.Transaction older = group.get(j);
                    long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(older.when).atZone(ZoneId.systemDefault()).toLocalDate(), current);
                    if (days > 35) break; // newest first: everything further along is even older
                    if (days < 25) continue;
                    long difference = Math.abs(recent.amountMinor - older.amountMinor);
                    if ("فواتير".equals(recent.category) || "أقساط".equals(recent.category) || difference <= Math.max(100, recent.amountMinor / 20)) {
                        ids.add(recent.id);
                        break;
                    }
                }
            }
        }
        return ids;
    }

    /**
     * The «لقينا خصم متكرر» cards: newest candidate row per merchant, at most three different merchants.
     * The rows come from the twelve-month candidate scan, never from the capped page list, so a payment
     * older than the newest 500 rows still produces its suggestion.
     */
    static List<ExpenseDb.Transaction> recurringSuggestions(Set<Long> candidates, List<ExpenseDb.Transaction> rows) {
        List<ExpenseDb.Transaction> result = new ArrayList<>();
        Set<String> suggested = new HashSet<>();
        for (ExpenseDb.Transaction item : rows) {
            if (!candidates.contains(item.id) || !suggested.add(item.merchant.toLowerCase(Locale.ROOT))) continue;
            if (result.size() == 3) break;
            result.add(item);
        }
        return result;
    }

    private void requestSmsImport() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) importSms();
        else smsDisclosure(null);
    }

    private void smsDisclosure(Runnable declined) {
        new ChoiceDialogBuilder().setTitle("قراءة رسائل المعاملات")
                .setMessage("Follow Money يطلب قراءة رسائل SMS لاستيراد المعاملات المالية القديمة للفترة اللي تختارها، مع مراجعة اختيارية قبل الحفظ، والتقاط الرسائل المالية الجديدة تلقائيًا حتى والتطبيق مقفول. " +
                        "يحلل الرسائل محليًا ويسجل العمليات والأرصدة والالتزامات المدعومة. يحفظ نص مصدر المعاملة وأدلتها المؤرشفة مشفّرًا على جهازك للمراجعة، ويستبعد رسائل التحقق اللي يتعرف عليها ولا يرسل الرسائل لسيرفر. " +
                        "الإذن اختياري؛ لو رفضت تقدر تدخل العمليات يدويًا أو تشارك رسالة محددة. تقدر تلغي الإذن من إعدادات Android.")
                .setPositiveButton("موافق، اطلب الإذن",(dialog,which)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("sms_permission_prompted",true).apply();
                    requestPermissions(new String[]{Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS},SMS_REQUEST);
                }).setNegativeButton("لاحقًا",(dialog,which)->{
                    getPreferences(MODE_PRIVATE).edit().putBoolean("sms_permission_prompted",true).apply();
                    if(declined!=null)declined.run();
                }).show();
    }

    private void requestSmsAtStartup() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            if (!getPreferences(MODE_PRIVATE).getBoolean("initial_import_prompted",false) &&
                    !getPreferences(MODE_PRIVATE).getBoolean("initial_sms_import_done",false)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("initial_import_prompted",true).apply();
                new ChoiceDialogBuilder().setTitle("راجع رسائلك القديمة")
                        .setMessage("الجديد بيتسجل تلقائيًا. القديم تختار فترته ويبدأ استيراده مباشرة، أو تفعّل المراجعة الاختيارية قبل الحفظ.")
                        .setPositiveButton("اختيار فترة الاستيراد",(d,w)->importSms()).setNegativeButton("لاحقًا",null).show();
            }
        } else {
            if (getPreferences(MODE_PRIVATE).getBoolean("sms_permission_prompted", false)) return;
            smsDisclosure(this::requestNotificationAtStartup);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == NOTIFICATION_REQUEST && selectedTab == 5 && "alerts".equals(settingsSection)) render();
        if (requestCode == SMS_REQUEST) {
            if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                importSms();
            }
            else new ChoiceDialogBuilder().setMessage("إذن SMS غير متاح على الجهاز. تقدر تضيف المصروف يدويًا أو تعمل Share لرسالة محددة للتطبيق.")
                    .setPositiveButton("تمام", null).show();
        }
    }

    private void requestNotificationAtStartup() {
        SharedPreferences settings = getSharedPreferences("notification_settings", MODE_PRIVATE);
        if (hasNotificationAccess() || settings.getBoolean("access_prompted", false)) return;
        settings.edit().putBoolean("access_prompted", true).apply();
        openNotificationSettings();
    }

    private boolean hasNotificationAccess() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        ComponentName component = new ComponentName(this, PaymentNotificationListener.class);
        if (Build.VERSION.SDK_INT >= 27) return manager != null && manager.isNotificationListenerAccessGranted(component);
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(component.flattenToString());
    }

    private void openNotificationSettings() {
        if(hasNotificationAccess()) { launchNotificationSettings();return; }
        new ChoiceDialogBuilder().setTitle("إشعارات تطبيقات الدفع")
                .setMessage("Notification Access يتيح للنظام إرسال الإشعارات إلى Follow Money حتى والتطبيق مقفول. التطبيق يعالج محتوى التطبيقات اللي تختارها فقط لاستخراج معاملات مالية وحفظ مصدرها مشفّرًا على الجهاز للمراجعة. اختار تطبيقات الدفع فقط. " +
                        "لا يرسل الإشعارات لسيرفر. رسائل تطبيق SMS وإشعارات Truecaller مستبعدة. الإشعارات النشطة قد تُقرأ عند الاتصال؛ سجل الإشعارات القديم غير متاح. " +
                        "الإذن اختياري وتقدر تلغيه أو تغير التطبيقات المختارة من الإعدادات.")
                .setPositiveButton("موافق، افتح إعدادات الإذن",(dialog,which)->launchNotificationSettings())
                .setNegativeButton("لاحقًا",null).show();
    }

    private void launchNotificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (Exception error) { toast("افتح إعدادات الجهاز ثم Notification Access وفعّل Follow Money"); }
    }

    private void chooseNotificationApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> installed = getPackageManager().queryIntentActivities(launcher, 0);
        String smsApp = Telephony.Sms.getDefaultSmsPackage(this);
        Map<String, String> labels = new LinkedHashMap<>();
        for (ResolveInfo app : installed) {
            String packageName = app.activityInfo.packageName;
            if (packageName.equals(getPackageName()) || packageName.equals(smsApp) ||
                    packageName.toLowerCase(Locale.ROOT).contains("truecaller")) continue;
            labels.put(packageName, app.loadLabel(getPackageManager()).toString());
        }
        List<String> packages = new ArrayList<>(labels.keySet());
        packages.sort(Comparator.comparing(labels::get, String.CASE_INSENSITIVE_ORDER));
        if (packages.isEmpty()) { toast("مفيش تطبيقات ظاهرة للاختيار حاليًا"); return; }
        String[] names = new String[packages.size()];
        boolean[] checked = new boolean[packages.size()];
        Set<String> selected = new HashSet<>(getSharedPreferences("notification_settings", MODE_PRIVATE)
                .getStringSet("sources", java.util.Collections.emptySet()));
        for (int i = 0; i < packages.size(); i++) {
            names[i] = labels.get(packages.get(i));
            checked[i] = selected.contains(packages.get(i));
        }
        new ChoiceDialogBuilder().setTitle("اختار تطبيقات الدفع فقط")
                .setMultiChoiceItems(names, checked, (dialog, which, enabled) -> {
                    if (enabled) selected.add(packages.get(which));
                    else selected.remove(packages.get(which));
                }).setPositiveButton("حفظ", (dialog, which) -> {
                    getSharedPreferences("notification_settings", MODE_PRIVATE).edit()
                            .putStringSet("sources", selected).apply();
                    if (!hasNotificationAccess()) openNotificationSettings();
                    else render();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void importSms() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("initial_import_prompted",true).apply();
        startActivity(new Intent(this,SmsImportActivity.class));
    }

    private void openTransactionFromIntent(Intent intent) {
        long id = intent == null ? -1 : intent.getLongExtra(CaptureNotifier.EXTRA_TRANSACTION, -1);
        if (id < 0) return;
        intent.removeExtra(CaptureNotifier.EXTRA_TRANSACTION);
        ExpenseDb.Transaction item = db.transactionById(id);
        if (item == null) return;
        transactionReturnTab = 6;
        transactionJourney.clear();
        transactionsBackTab=0;
        resetTransactionFilters();selectedMonth=Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1);
        selectedTransaction = item;
        selectedTab = 10;
        render();
        if (intent.getBooleanExtra(CaptureNotifier.EXTRA_EDIT, false)) {
            intent.removeExtra(CaptureNotifier.EXTRA_EDIT);
            editTransactionDialog(item);
        }
    }

    private void handleShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String body = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (body == null) return;
        ExpenseParser.Parsed parsed = ExpenseParser.parse("رسالة مشتركة", body);
        if (parsed == null) { toast("مش قادر أتعرف على مصروف في النص. ضيفه يدويًا."); return; }
        new ChoiceDialogBuilder().setTitle("إضافة معاملة من رسالة؟")
                .setMessage(parsed.merchant + " · " + money(parsed.amountMinor, parsed.currency) +
                        "\n" + parsed.category)
                .setPositiveButton("إضافة", (dialog, which) -> {
                    db.addSms("رسالة مشتركة", body, System.currentTimeMillis());
                    render();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
    }

    /**
     * Fill for a selected choice or a filled call-to-action with white text. The forest brand color almost
     * disappears on the dark surface, so dark mode uses the accent, like the dialog primary button.
     * Lime-on-forest brand elements (navigation, intro, hero cards) intentionally keep BRAND.
     */
    private int primaryFill() { return dark ? ACCENT : BRAND; }
    private int onPrimary() { return dark ? 0xff0d1a16 : Color.WHITE; }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius));
        return shape;
    }

    private GradientDrawable outlined(int color, int border, int radius) {
        GradientDrawable shape = rounded(color, radius);
        shape.setStroke(dp(1), border);
        return shape;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        shape.setCornerRadius(dp(radius)); return shape;
    }

    private View sourceBadge(ExpenseDb.PaymentSource source, int size) {
        String bank = SourceIdentity.bank(source.name);
        String packageName = "BANKMISR".equals(bank) ? "com.BanqueMisr.MobileBanking" :
                "HSBC".equals(bank) ? "com.htsu.hsbcpersonalbanking" : null;
        if (packageName != null) {
            try {
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(getPackageManager().getApplicationIcon(packageName));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setBackground(rounded(Color.WHITE, size / 2));
                icon.setPadding(dp(2), dp(2), dp(2), dp(2));
                return icon;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        String brand=BrandCatalog.bank(source.name);
        if(brand!=null) {
            int res=brandLogo(brand);
            if(res!=0)return UiKit.logoBadge(this,res,brand,source.name,size,dark?0:BORDER);
        }
        return identityBadge(source.name, source.kind, size);
    }

    private void metric(LinearLayout parent, String heading, String value, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(SURFACE, 18));
        box.addView(label(heading, 12, MUTED, false));
        box.addView(label(value, 17, INK, true));
        box.addView(label(detail, 11, MUTED, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        parent.addView(box, lp);
    }

    private void tipCard(String glyph, String heading, String detail, String action, Runnable onClick) {
        int color = heading.contains("ميزانية") ? INDIGO : heading.contains("دفعات") ? CORAL :
                heading.contains("الأصول") ? GOLD : heading.contains("ديون") ? ACCENT :
                heading.contains("رسائل") ? INDIGO : ACCENT;
        int pale = dark ? ACCENT_PALE : color == INDIGO ? 0xffedeff9 : color == CORAL ? 0xfffde8e8 :
                color == GOLD ? 0xfffff7d6 : ACCENT_PALE;
        int iconId = heading.contains("ميزانية") ? R.drawable.ic_nav_budget :
                heading.contains("ديون") || heading.contains("دفعات") || heading.contains("رسائل") ?
                        R.drawable.ic_nav_transactions : R.drawable.ic_nav_insights;
        LinearLayout box = card();
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(heading, 15, INK, true));
        copy.addView(label(detail, 12, MUTED, false));
        line.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView icon = new ImageView(this); icon.setImageResource(iconId);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(rounded(pale, 12));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(42), dp(42)); ip.rightMargin = dp(10);
        line.addView(icon, ip); box.addView(line);
        TextView link = label(action + "  ←", 12, color, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.topMargin = dp(8);
        box.addView(link, lp); box.setOnClickListener(v -> onClick.run());
    }

    private void progress(LinearLayout parent, long value, long maximum) {
        progress(parent, value, maximum, ACCENT);
    }

    private void progress(LinearLayout parent, long value, long maximum, int color) {
        View track = new View(this); track.setBackground(rounded(BORDER, 5));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(7)); tp.topMargin = dp(10); parent.addView(track, tp);
        // Use a clipped foreground so the length follows the real amount without a chart dependency.
        track.post(() -> {
            int width = track.getWidth();
            if (width <= 0) return;
            float ratio = maximum <= 0 ? 0 : Math.min(1f, (float) value / maximum);
            GradientDrawable fill = rounded(value > maximum ? CORAL : color, 5);
            android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{rounded(BORDER, 5), clip});
            track.setBackground(layers); clip.setLevel((int) (ratio * 10000));
        });
    }

    private void actionTile(LinearLayout parent, String glyph, String caption, Runnable action) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER); tile.setPadding(dp(6), dp(8), dp(6), dp(8));
        tile.setBackground(rounded(SURFACE, 18)); tile.setElevation(dp(2));
        TextView icon = label(glyph, 24, ACCENT, true); icon.setGravity(Gravity.CENTER);
        TextView title = label(caption, 11, INK, true); title.setGravity(Gravity.CENTER);
        tile.addView(icon); tile.addView(title);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(86), 1); p.setMargins(dp(4), 0, dp(4), 0);
        parent.addView(tile, p); tile.setOnClickListener(v -> action.run());
    }

    private String[] sourceNames(List<ExpenseDb.PaymentSource> sources) {
        String[] names = new String[sources.size() + 1]; names[0] = "بدون وسيلة محددة";
        for (int i = 0; i < sources.size(); i++) {
            ExpenseDb.PaymentSource source=sources.get(i);
            names[i + 1] = db.sourceLabel(source.id)+(source.reviewed?"":" · يحتاج مراجعة");
        }
        return names;
    }

    private List<ExpenseDb.PaymentSource> paymentOptions(Long from,Long to) {
        List<ExpenseDb.PaymentSource> result=db.selectableSources();
        for(ExpenseDb.PaymentSource source:db.paymentSources())
            if((from!=null && source.id==from || to!=null && source.id==to) &&
                    result.stream().noneMatch(option->option.id==source.id))result.add(source);
        return result;
    }

    private Long selectedSource(Spinner spinner, List<ExpenseDb.PaymentSource> sources) {
        int index = spinner.getSelectedItemPosition();
        return index <= 0 || index > sources.size() ? null : sources.get(index - 1).id;
    }

    private ScrollView scrollForm(LinearLayout form) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(form);
        return scroll;
    }

    private LinearLayout form() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(4), dp(20), dp(4));
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return layout;
    }

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", new Locale("ar"));

    /** A date field that opens the calendar instead of the keyboard. The chosen date lives in the tag; the text is for reading. */
    private EditText dateInput(String hint, LinearLayout parent, LocalDate initial) {
        EditText field = input(hint, parent);
        field.setFocusable(false); field.setCursorVisible(false); field.setLongClickable(false);
        field.setCompoundDrawablePadding(dp(8));
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ic_calendar);
        if (icon != null) { icon = icon.mutate(); icon.setTint(ACCENT); icon.setBounds(0, 0, dp(20), dp(20)); field.setCompoundDrawablesRelative(icon, null, null, null); }
        setDateValue(field, initial);
        field.setOnClickListener(v -> {
            LocalDate shown = dateValue(field) == null ? LocalDate.now() : dateValue(field);
            new android.app.DatePickerDialog(this, (picker, year, month, day) -> setDateValue(field, LocalDate.of(year, month + 1, day)),
                    shown.getYear(), shown.getMonthValue() - 1, shown.getDayOfMonth()).show();
        });
        return field;
    }

    private void setDateValue(EditText field, LocalDate date) {
        field.setTag(date);
        field.setText(date == null ? "" : date.format(DATE_LABEL));
    }

    /** The picked date, or null when an optional date was left empty. */
    private LocalDate dateValue(EditText field) {
        Object tag = field.getTag();
        return tag instanceof LocalDate ? (LocalDate) tag : null;
    }

    private EditText input(String hint, LinearLayout parent) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(15);
        edit.setTypeface(REGULAR);
        edit.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(outlined(SURFACE, BORDER, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(10);
        parent.addView(edit, lp);
        return edit;
    }

    private Spinner spinner(String label, String[] choices, LinearLayout parent) {
        TextView caption = label(label, 12, MUTED, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        parent.addView(caption, cp);
        Spinner spinner = new AppChoiceSpinner(label, choices);
        spinner.setPadding(dp(12), 0, dp(12), 0);
        spinner.setBackground(outlined(SURFACE, BORDER, 12));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(48)));
        return spinner;
    }

    /** One selector for every form; adapter positions and selection callbacks remain unchanged. */
    private final class AppChoiceSpinner extends Spinner {
        private final String caption;
        AppChoiceSpinner(String caption, String[] values) {
            super(MainActivity.this, Spinner.MODE_DIALOG);
            this.caption = caption;
            setContentDescription(caption);
            setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, values) {
                @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                    TextView value = label(getItem(position), 15, INK, false);
                    value.setSingleLine(true);
                    value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    value.setPadding(dp(12), 0, dp(12), 0);
                    android.graphics.drawable.Drawable arrow = getDrawable(R.drawable.ic_chevron_down).mutate();
                    arrow.setTint(MUTED); arrow.setBounds(0, 0, dp(18), dp(18));
                    value.setCompoundDrawablesRelative(null, null, arrow, null);
                    value.setCompoundDrawablePadding(dp(6));
                    return value;
                }
            });
        }
        // Spinner.super opens its platform popup. Keep the native accessible click action,
        // but render only our themed menu; ACTION_CLICK is exercised by SelectionUiTest.
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        @Override public boolean performClick() {
            if (!isEnabled()) return false;
            playSoundEffect(android.view.SoundEffectConstants.CLICK);
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
            CharSequence[] values = new CharSequence[getCount()];
            for (int i = 0; i < values.length; i++) values[i] = String.valueOf(getItemAtPosition(i));
            new ChoiceDialogBuilder().setTitle(caption)
                    .setSingleChoiceItems(values, getSelectedItemPosition(), (dialog, index) -> {
                        setSelection(index); dialog.dismiss();
                    }).show();
            return true;
        }
    }

    /** App-owned menus share colors, typography, search and accessible selection states. */
    private final class ChoiceDialogBuilder extends AlertDialog.Builder {
        private CharSequence heading;
        private CharSequence[] entries;
        private boolean[] checked;
        private int selected = -1;
        private boolean single;
        private View suppliedView;
        private android.content.DialogInterface.OnClickListener picked;
        private android.content.DialogInterface.OnMultiChoiceClickListener toggled;
        ChoiceDialogBuilder() { super(MainActivity.this); }
        @Override public AlertDialog.Builder setTitle(CharSequence title) { heading = title; return super.setTitle(title); }
        @Override public AlertDialog.Builder setView(View view) { suppliedView = view; return super.setView(view); }
        @Override public AlertDialog.Builder setItems(CharSequence[] items, android.content.DialogInterface.OnClickListener listener) {
            entries = items; picked = listener; return this;
        }
        @Override public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int index, android.content.DialogInterface.OnClickListener listener) {
            entries = items; selected = index; single = true; picked = listener; return this;
        }
        @Override public AlertDialog.Builder setMultiChoiceItems(CharSequence[] items, boolean[] states, android.content.DialogInterface.OnMultiChoiceClickListener listener) {
            entries = items; checked = states == null ? new boolean[items.length] : states; toggled = listener; return this;
        }
        @Override public AlertDialog create() {
            if (entries == null) return super.create();
            LinearLayout content = form(); content.setTag("app_choice_menu");
            content.setPadding(dp(18), dp(16), dp(18), dp(12));
            LinearLayout header = new LinearLayout(MainActivity.this); header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = label(heading == null ? "اختار الإجراء" : heading.toString(), 19, INK, true);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView close = new ImageView(MainActivity.this); close.setImageResource(R.drawable.ic_close); close.setColorFilter(MUTED);
            close.setPadding(dp(12), dp(12), dp(12), dp(12)); close.setContentDescription("إغلاق القائمة");
            header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(header);
            // Some multi-select menus also contain an input form (e.g. InstaPay handles).
            if (suppliedView != null) content.addView(suppliedView);
            EditText search = entries.length >= 8 ? input("بحث في الخيارات", content) : null;
            if (search != null) { search.setTag("app_choice_search"); search.setContentDescription("بحث في الخيارات"); }
            int limit = Math.max(dp(150), Math.min(dp(420), getResources().getDisplayMetrics().heightPixels * 45 / 100));
            ScrollView scroll = new ScrollView(MainActivity.this) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int available = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED ? limit : Math.min(limit, View.MeasureSpec.getSize(heightSpec));
                    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
                }
            };
            LinearLayout rows = new LinearLayout(MainActivity.this); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            content.addView(scroll);
            super.setTitle((CharSequence)null); super.setView(content);
            AlertDialog dialog = super.create();
            close.setOnClickListener(v -> dialog.cancel());
            Runnable renderRows = () -> {
                rows.removeAllViews(); String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < entries.length; i++) {
                    final int index = i; String text = entries[i].toString();
                    if (!text.toLowerCase(Locale.ROOT).contains(query)) continue;
                    boolean destructive = checked == null && !single && (text.startsWith("حذف") || text.startsWith("أرشفة") || text.startsWith("فك ربط"));
                    int color = destructive ? CORAL : INK;
                    if (single || checked != null) {
                        android.widget.CompoundButton row = single ? new android.widget.RadioButton(MainActivity.this) : new CheckBox(MainActivity.this);
                        row.setText(text); row.setTextSize(15); row.setTypeface(REGULAR); row.setTextColor(INK);
                        row.setButtonTintList(ColorStateList.valueOf(ACCENT)); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                        row.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                        row.setPadding(dp(12), dp(10), dp(12), dp(10)); row.setMinHeight(dp(56));
                        row.setChecked(single ? index == selected : checked[index]);
                        row.setBackground(outlined(row.isChecked() ? ACCENT_PALE : SURFACE, BORDER, 12));
                        row.setOnClickListener(v -> {
                            if (single) { if (picked != null) picked.onClick(dialog, index); }
                            else { checked[index] = row.isChecked(); row.setBackground(outlined(checked[index] ? ACCENT_PALE : SURFACE, BORDER, 12)); if (toggled != null) toggled.onClick(dialog, index, checked[index]); }
                        });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(6); rows.addView(row, lp);
                    } else {
                        LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(12), dp(10), dp(12), dp(10)); row.setMinimumHeight(dp(56));
                        row.setBackground(outlined(SURFACE, BORDER, 12));
                        ImageView icon = new ImageView(MainActivity.this); icon.setImageResource(menuIcon(text, destructive)); icon.setColorFilter(destructive ? CORAL : ACCENT);
                        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
                        TextView name = label(text, 15, color, false); name.setPadding(dp(12), 0, dp(12), 0);
                        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                        row.setOnClickListener(v -> { if (picked != null) picked.onClick(dialog, index); dialog.dismiss(); });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(6); rows.addView(row, lp);
                    }
                }
                if (rows.getChildCount() == 0) rows.addView(label("مفيش نتائج للبحث", 15, MUTED, false));
            };
            if (search != null) search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { renderRows.run(); }
                public void afterTextChanged(Editable e) {}
            });
            renderRows.run(); return dialog;
        }
    }

    private int menuIcon(String text, boolean destructive) {
        if (text.startsWith("أرشفة")) return R.drawable.ic_archive;
        if (text.startsWith("فك ربط")) return R.drawable.ic_unlink;
        if (destructive) return R.drawable.ic_trash;
        if (text.contains("رسائل") || text.contains("المصدر")) return R.drawable.ic_message;
        if (text.contains("رصيد") || text.contains("أرصدة") || text.contains("ملخص")) return R.drawable.ic_nav_insights;
        if (text.contains("تسمية") || text.contains("تعديل") || text.contains("تحديث")) return R.drawable.ic_edit;
        if (text.contains("كارت") || text.contains("بطاقة") || text.contains("حساب")) return R.drawable.ic_cat_transfer;
        if (text.contains("إضافة") || text.contains("يدوي")) return R.drawable.ic_plus;
        return R.drawable.ic_chevron_forward;
    }

    /**
     * Lines up text with the screen direction (right in Arabic) even when the text itself is Latin,
     * like "HSBC" or an InstaPay handle; explicit center/end gravity still wins.
     */
    private static final class AlignedText extends TextView {
        AlignedText(Context context) {
            super(context);
            setTextAlignment(TEXT_ALIGNMENT_VIEW_START);
        }

        @Override public void setGravity(int gravity) {
            super.setGravity(gravity);
            int horizontal = gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK;
            boolean explicit = horizontal == Gravity.CENTER_HORIZONTAL || horizontal == Gravity.END ||
                    horizontal == Gravity.RIGHT || horizontal == Gravity.LEFT;
            setTextAlignment(explicit ? TEXT_ALIGNMENT_GRAVITY : TEXT_ALIGNMENT_VIEW_START);
        }
    }

    private TextView text(String value, int sp) {
        TextView view = new AlignedText(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(4), 0, dp(4));
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setTypeface(REGULAR);
        return view;
    }

    private void title(String value, int sp) {
        TextView view = text(value, sp);
        view.setTextColor(INK);
        view.setPadding(0, dp(14), 0, dp(4));
        page.addView(view);
    }

    private void note(String value) {
        TextView view = label(value, 13, MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(16);
        page.addView(view, lp);
    }

    private void button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(v -> action.run());
        page.addView(button);
    }

    private void buttonStatus(String status) { toast(status); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public static String money(long minor, String currency) {
        String amount = String.format(Locale.ROOT, "%,.2f", minor / 100.0);
        if ("EGP".equals(currency)) return amount + " ج.م";
        if ("USD".equals(currency)) return "$" + amount;
        if ("EUR".equals(currency)) return "€" + amount;
        return amount + " " + currency;
    }

    /**
     * Early-payment normalization is a ledger write. Nothing else may run it off the UI thread, so the
     * activity and the daily receiver both hand it to the single capture worker that serialises captures.
     */
    static int normalizeEarlyPaymentsOnWorker(ExpenseDb db) {
        try { return db.normalizeEarlyPayments(); }
        catch (RuntimeException ignored) { return 0; }
    }

    /** What the commitment screens need from the ledger, read once on the capture worker. */
    static final class LedgerSnapshot {
        final Set<Long> candidates;
        final List<ExpenseDb.Transaction> rows;
        /** The ledger generation this snapshot was read under. */
        final long version;
        /** SQLite's change counter after the read, so the next render can tell the ledger moved. */
        final long changes;
        /** Installments this pass settled; a non-zero count means the pages must be redrawn. */
        final int normalized;
        LedgerSnapshot(Set<Long> candidates, List<ExpenseDb.Transaction> rows, long version, long changes, int normalized) {
            this.candidates = candidates; this.rows = rows;
            this.version = version; this.changes = changes; this.normalized = normalized;
        }
    }

    /** The commitment editor page. Mid-typing, so a delayed redraw must not rebuild it. */
    private static final int EDITOR_TAB = 12;

    /**
     * Whether the screen in front of the user holds unsaved input. Dialogs are separate windows and survive a
     * redraw, but the editor page and the intro forms are part of this activity's view tree.
     */
    static boolean isEditingSurface(int tab, boolean introVisible, boolean focusedInput) {
        return tab == EDITOR_TAB || introVisible || focusedInput;
    }

    /** A snapshot read before a local edit landed must be discarded instead of applied. */
    static boolean ledgerSnapshotIsCurrent(long readVersion, long currentVersion) {
        return readVersion == currentVersion;
    }

    /** A different committed revision means this cached snapshot aged, including writes by other helpers. */
    static boolean ledgerChanged(long observedChanges, long snapshotChanges) {
        return observedChanges != snapshotChanges;
    }

    /** Cheap identity of the applied rows: a different size, id, amount or merchant needs a redraw. */
    static boolean sameRows(List<ExpenseDb.Transaction> first, List<ExpenseDb.Transaction> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            ExpenseDb.Transaction left = first.get(index), right = second.get(index);
            if (left.id != right.id || left.amountMinor != right.amountMinor
                    || !left.currency.equals(right.currency) || !left.merchant.equals(right.merchant)) return false;
        }
        return true;
    }

    /**
     * One capture-worker pass: settle early payments, then read the twelve-month candidate scan. This is the
     * only place the suggestion query runs, so no render ever spans a year of expenses on the UI thread.
     */
    static LedgerSnapshot readLedgerSnapshot(ExpenseDb db, long version) {
        int normalized = normalizeEarlyPaymentsOnWorker(db);
        long changes = db.changes();
        List<ExpenseDb.Transaction> rows = db.recurringCandidateTransactions();
        return new LedgerSnapshot(recurringCandidateIds(rows, db.recurring()), rows, version, changes, normalized);
    }

    /** A local ledger edit: bump the generation so an in-flight snapshot is rejected, then reload the cache. */
    private void invalidateLedger() {
        ledgerVersion.incrementAndGet();
        refreshLedger(true);
    }

    /** True when redrawing cannot disturb input the user has not saved yet. */
    private boolean canRedrawNow() {
        if (isFinishing() || isDestroyed()) return false;
        return !isEditingSurface(selectedTab, introVisible, getCurrentFocus() instanceof android.widget.EditText);
    }

    /** The suggestion data a redraw would show is different from what the page is currently drawn with. */
    private boolean cacheDiffers(LedgerSnapshot snapshot) {
        return ledgerChanges==null || ledgerChanged(snapshot.changes,ledgerChanges)
                || !snapshot.candidates.equals(recurringCandidates) || !sameRows(snapshot.rows, recurringCandidateRows);
    }

    /**
     * An installment paid a few days early (30 Sep for 1 Oct) must not stay "overdue", and the commitment
     * suggestions must be current. Both go through the capture worker; the page only reads the cache.
     */
    private void refreshLedger(final boolean rerender) {
        if (!refreshingLedger.compareAndSet(false, true)) { ledgerRefreshPending = ledgerRefreshPending || rerender; return; }
        final long version = ledgerVersion.get();
        CaptureQueue.submit(() -> {
            LedgerSnapshot snapshot = null;
            try { snapshot = readLedgerSnapshot(db, version); }
            catch (RuntimeException ignored) { }
            final LedgerSnapshot ready = snapshot;
            postUi(() -> {
                boolean repeat = ledgerRefreshPending;
                ledgerRefreshPending = false;
                refreshingLedger.set(false);
                if (ready == null) { if (repeat) refreshLedger(true); return; }
                long committed;
                try {committed=db.changes();} catch(RuntimeException unavailable) {return;}
                if (!ledgerSnapshotIsCurrent(ready.version, ledgerVersion.get()) || ledgerChanged(committed,ready.changes)) {
                    // A local edit or external commit landed while reading/delivering this snapshot.
                    ledgerRefreshPending = repeat;
                    refreshLedger(true);
                    return;
                }
                boolean changed = ledgerRedrawPending || ready.normalized > 0 || cacheDiffers(ready);
                recurringCandidates = ready.candidates;
                recurringCandidateRows = ready.rows;
                ledgerChanges = ready.changes;
                // Never rebuild the screen while the user is typing in it; the cache is already current, so the
                // next render (leaving the editor, opening a page) shows the settled data.
                if (rerender && changed && canRedrawNow()) {
                    ledgerRedrawPending = false;
                    render();
                }
                if (repeat) refreshLedger(true);
            });
        });
    }

    private void render() {
        if (appLock != null && appLock.guard()) return;
        // Any committed tracked write bumps the persistent revision, so the cached suggestions cannot stay stale
        // behind a page whose only refresh path is this render().
        sourceKindCache = null;
        if (db != null) {
            long changes = db.changes();
            if (ledgerChanges == null) ledgerChanges = changes;
            else if (ledgerChanged(changes, ledgerChanges)) invalidateLedger();
        }
        savePageScroll();
        DailyPointsStore.observeToday(this);
        settingsGroup = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        pageScroll=scroll;renderedPageKey=pageContextKey();
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(14), dp(16), dp(28));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        boolean fullScreen = introVisible || selectedTab >= 10 && selectedTab <= 12 || selectedTab == 14;
        if (!fullScreen) root.addView(bottomNavigation());
        setContentView(root);
        SystemUi.apply(this,root);
        if(introVisible) introScreen();
        else if (selectedTab == 0) homeScreen();
        else if (selectedTab == 1) sourcesScreen();
        else if (selectedTab == 2) recurringScreen();
        else if (selectedTab == 3) insightsScreen(); // budgets now live inside التحليلات
        else if (selectedTab == 4) insightsScreen();
        else if (selectedTab == 5) settingsScreen();
        else if (selectedTab == 6) transactionsScreen();
        else if (selectedTab == 7) merchantRulesScreen();
        else if (selectedTab == 8) assetsScreen();
        else if (selectedTab == 9) debtsScreen();
        else if (selectedTab == 10 && selectedTransaction != null) transactionDetailsScreen(selectedTransaction);
        else if (selectedTab == 11) commitmentDetailsScreen();
        else if (selectedTab == 12) commitmentEditorScreen();
        else if (selectedTab == 13) insightsScreen(); // حركة الفلوس merged into التحليلات
        else if (selectedTab == 14) flowAccountScreen();
        else if (selectedTab == 15) monthlyReportScreen();
        else transactionsScreen();
        markDetailActions(page);
        int restoreY=scrollPositions.getOrDefault(renderedPageKey,0);
        scroll.post(()->scroll.scrollTo(0,restoreY));
        refreshWidgets();
    }

    private void savePageScroll() {
        if(pageScroll!=null&&renderedPageKey!=null) {
            scrollPositions.put(renderedPageKey,pageScroll.getScrollY());
            if(scrollPositions.size()>60)scrollPositions.remove(scrollPositions.keySet().iterator().next());
        }
    }

    private String pageContextKey() {
        if(selectedTab==10)return "detail:"+(selectedTransaction==null?-1:selectedTransaction.id);
        if(selectedTab==6)return "list:"+selectedMonth+":"+transactionSearch+":"+transactionKindFilter+":"+transactionCategoryFilter+":"+
                transactionCurrencyFilter+":"+transactionSourceFilter+":"+transactionBucketFilter+":"+transactionExcludeTransfers+":"+
                (transactionPeriod==null?"":transactionPeriod[0]+"/"+transactionPeriod[1]);
        if(selectedTab==2)return "commitments:"+commitmentFilter+":"+commitmentMonth;
        if(selectedTab==4||selectedTab==3||selectedTab==13)return "analytics:"+insightsSection+":"+insightsRange+":"+selectedMonth;
        if(selectedTab==5)return "settings:"+settingsSection;
        if(selectedTab==11)return "commitment:"+selectedCommitmentId;
        if(selectedTab==14)return "account:"+flowSourceId;
        if(selectedTab==15)return "monthly:"+monthlyMonth+":"+monthlyCurrency;
        return "screen:"+selectedTab;
    }

    private void openRelatedTransaction(ExpenseDb.Transaction item) {
        if(selectedTransaction!=null)transactionJourney.visit(selectedTransaction.id,item.id);
        selectedTransaction=item;selectedTab=10;render();
    }

    private void returnFromTransaction() {
        Long id;while((id=transactionJourney.back())!=null) {
            ExpenseDb.Transaction parent=db.transactionById(id);
            if(parent!=null){selectedTransaction=parent;render();return;}
        }
        selectedTab=transactionReturnTab;selectedTransaction=null;render();
    }

    @Override public void onBackPressed() {
        if (appLock != null && appLock.locked()) { finish(); return; }
        if(introVisible){if(introStep>0){introStep--;render();}else finishIntro(false);return;}
        if(bulkMode){bulkMode=false;bulkSelected.clear();render();return;}
        if(selectedTab==4&&!insightsSection.isEmpty()){insightsSection="";render();return;}
        if(selectedTab==5&&!settingsSection.isEmpty()){settingsSection="";render();return;}
        if (selectedTab == 10) {
            returnFromTransaction();
        } else if (selectedTab == 11) {
            selectedTab = 2; render();
        } else if (selectedTab == 15) {
            selectedTab=4;insightsSection="";render();
        } else if (selectedTab == 14) {
            selectedTab = 4; render();
        } else if (selectedTab == 12) {
            selectedTab = editingCommitment != null ? 11 : 2; editingCommitment = null; render();
        } else if(selectedTab==6&&!transactionsReturnDialog.isEmpty()) {
            String destination=transactionsReturnDialog,code=transactionsPointsCurrency;
            selectedTab=transactionsBackTab==6?0:transactionsBackTab;transactionsBackTab=0;transactionsReturnDialog="";transactionsPointsCurrency="";render();
            if("points".equals(destination))pointsDialog(code);else dailyDetailsDialog();
        } else if (selectedTab == 6 && transactionsBackTab != 0) {
            selectedTab = transactionsBackTab; transactionsBackTab = 0; bulkMode = false; bulkSelected.clear(); render();
        } else if(selectedTab==7){selectedTab=5;settingsSection="capture";render();
        } else if (selectedTab != 0) {
            selectedTab = 0; render();
        } else super.onBackPressed();
    }

    private void loadFonts() {
        try {
            REGULAR = new Typeface.Builder(getAssets(), "fonts/ReadexPro.ttf")
                    .setFontVariationSettings("'wght' 400").build();
            BOLD = new Typeface.Builder(getAssets(), "fonts/ReadexPro.ttf")
                    .setFontVariationSettings("'wght' 600").build();
            if (REGULAR == null || BOLD == null) throw new IllegalStateException("font");
        } catch (RuntimeException error) {
            REGULAR = Typeface.DEFAULT; BOLD = Typeface.DEFAULT_BOLD;
        }
    }

    private void homeScreen() {
        String profile = getPreferences(MODE_PRIVATE).getString("profile_name", "").trim();
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = profileAvatar(42);
        logo.setContentDescription("Follow Money");
        top.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout greeting = new LinearLayout(this); greeting.setOrientation(LinearLayout.VERTICAL);
        greeting.addView(label(LocalTime.now().getHour() < 12 ? "صباح الخير" : "مساء الخير", 12, MUTED, false));
        greeting.addView(label(profile.isEmpty() ? "Follow Money" : profile, 17, INK, true));
        LinearLayout.LayoutParams greetingParams = new LinearLayout.LayoutParams(0, -2, 1);
        greetingParams.setMarginStart(dp(10)); top.addView(greeting, greetingParams);
        top.addView(iconButton(R.drawable.ic_search, "بحث في العمليات", () -> {
            selectedTab = 6; searchOpen = true; render();
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        settingsParams.setMarginStart(dp(8));
        top.addView(iconButton(R.drawable.ic_settings, "الإعدادات", () -> { settingsSection="";selectedTab = 5; render(); }), settingsParams);
        page.addView(top);

        whatsNewCard();
        monthlyReadyCard();
        // MVP home: one number, one period, one definition. The daily budget and points keep their data, dialogs,
        // widgets and settings entry («الميزانية اليومية والراتب», «ميزانياتك»); only their home card is off.
        if (HOME_SHOWS_DAILY_BUDGET) dailyHomeCard();
        // One card, one period, one definition (MoneyFlow.statement): «دخل / خرج / باقي» from the pay day to today.
        // Nothing else on the home screen carries a number with another period or definition (lesson of 19 October).
        homeStatementCard();
        backupReminderCard();
        nextCommitmentCard();
        section("كروتك وحساباتك", "عرض الكل", () -> { selectedTab = 1; render(); });
        homeSourceCards();
        if (db.paymentSources().isEmpty())
            tipCard("✉", "ربط الحسابات من الرسائل", "امسح رسائل البنوك لتكتشف البطاقات والأرصدة تلقائيًا", "فحص الرسائل", this::requestSmsImport);

        section("آخر العمليات", "عرض الكل", () -> { selectedTab = 6; render(); });
        List<ExpenseDb.Transaction> all = db.recentTransactions(5);
        if (all.isEmpty()) empty("لسه مفيش عمليات", "ابدأ بإضافة مصروف أو استيراد رسائل البنك");
        else {
            Set<Long> candidates = recurringCandidates;
            LinearLayout list = listCard(page);
            for (int i = 0; i < Math.min(5, all.size()); i++) transactionRow(list, all.get(i), candidates.contains(all.get(i).id));
        }
        // The home screen ends here: month overview and reviews live in التحليلات and العمليات.
    }

    /** The pay day saved by the intro/daily budget for a currency: 0 when none, DailyAllowance.MONTH_END for «آخر يوم في الشهر». */
    private int salaryDay(String currency) {
        return getSharedPreferences(DailySummary.SETTINGS, MODE_PRIVATE).getInt("salary_" + currency, 0);
    }

    /**
     * The statement period for EGP on the home screen: from the pay day to today, snapped to the day the salary really
     * landed (MoneyFlow.statementPeriod with items). The widget uses the same call, so both show one period.
     */
    private LocalDate[] statementPeriod() {
        ZoneId zone = ZoneId.systemDefault();
        return MoneyFlow.statementPeriod(salaryDay("EGP"), DailySummary.monthlySalary(this, "EGP"), "EGP", zone, LocalDate.now(), (from, to) -> db.flowItems(from, to, zone));
    }

    /**
     * The month statement (owner's decision, 20 October): «يوم القبض دخلك X · خرج منك حتى الآن Y · باقي Z».
     * One MoneyFlow.statement call feeds the three numbers and the component lines, so every line opens exactly
     * the rows that add up to it (same period, EGP, same bucket). Own-account moves and card repayments to the
     * user's own cards are internal; a purchase paid from cash is detail only, counted when the cash was withdrawn.
     */
    private void homeStatementCard() {
        LocalDate[] period = statementPeriod();
        MoneyFlow.Statement s = MoneyFlow.statement(db.flowItems(period[0], period[1], ZoneId.systemDefault()), period, ZoneId.systemDefault(), "EGP");
        DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("d MMMM", new Locale("ar"));
        final int softInk = 0xffb9d3c8, softCoral = 0xffffb4a8, softSky = 0xffa9d6ff, track = 0x2effffff;
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(14), dp(18), dp(14));
        hero.setBackground(rounded(BRAND, 26));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(16); page.addView(hero, hp);
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label(salaryDay("EGP") > 0 ? "كشف الفترة من يوم القبض" : "كشف الشهر", 13, softInk, false), new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(label(period[0].format(dayMonth) + " – اليوم", 12, softInk, false));
        hero.addView(heading);

        // Compact body: the ring on one side, the three numbers stacked beside it, so the card stays about a third of
        // the screen. The ring shows how much of the income has gone out; full (and coral) when more went out than came in.
        boolean over = s.remaining < 0;
        float share = s.income > 0 ? (float) ((double) s.out / s.income) : (s.out > 0 ? 1f : 0f);
        LinearLayout body = new LinearLayout(this); body.setGravity(Gravity.CENTER_VERTICAL); body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        FrameLayout dial = new FrameLayout(this);
        UiKit.Donut ring = new UiKit.Donut(this);
        ring.set(share, track, over ? softCoral : LIME, 10);
        ring.setContentDescription("خرج " + Math.round(Math.min(1f, share) * 100) + "% من الدخل");
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(132), dp(132)); ringParams.gravity = Gravity.CENTER;
        dial.addView(ring, ringParams);
        LinearLayout centre = new LinearLayout(this); centre.setOrientation(LinearLayout.VERTICAL); centre.setGravity(Gravity.CENTER);
        centre.setPadding(dp(14), 0, dp(14), 0);
        centre.addView(centered(label("باقي من دخل الفترة", 9, softInk, false)));
        TextView remaining = label((over ? "−" : "") + money(Math.abs(s.remaining), "EGP"), 16, over ? softCoral : Color.WHITE, true);
        remaining.setTextDirection(View.TEXT_DIRECTION_LTR); remaining.setSingleLine(true);
        remaining.setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        centre.addView(centered(remaining), new LinearLayout.LayoutParams(-1, -2));
        String hint = s.income <= 0 ? (s.out > 0 ? "مفيش دخل مسجل" : "لسه مفيش حركة") : over ? "خرج أكتر من الدخل" : "اتصرف " + Math.round(share * 100) + "%";
        centre.addView(centered(label(hint, 9, over ? softCoral : softInk, false)));
        FrameLayout.LayoutParams centreParams = new FrameLayout.LayoutParams(dp(132), -2); centreParams.gravity = Gravity.CENTER;
        dial.addView(centre, centreParams);
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(dp(132), dp(132)); dialParams.setMarginEnd(dp(14));
        body.addView(dial, dialParams);

        // Three rows beside the ring: دخل, خرج, صرف يومي. Each opens exactly the rows that add up to its number.
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.VERTICAL);
        String incomeDate = s.salaryWhen > 0 ? "يوم " + Instant.ofEpochMilli(s.salaryWhen).atZone(ZoneId.systemDefault()).toLocalDate().format(dayMonth) : "الفترة";
        stats.addView(statementOrb(R.drawable.ic_cat_income, 0, LIME, "دخل", "+" + money(s.income, "EGP"), incomeDate,
                () -> openStatement(MoneyFlow.Bucket.INFLOW.name(), false)));
        stats.addView(statementOrb(R.drawable.ic_cat_income, 180, softCoral, "خرج", "−" + money(s.out, "EGP"), s.outCount + " عملية",
                () -> openStatement(MoneyFlow.OUT_FILTER, false)));
        stats.addView(statementOrb(R.drawable.ic_cat_other, 0, softSky, "صرف يومي", money(s.living, "EGP"), "من خرج",
                () -> openStatement(MoneyFlow.Bucket.LIVING.name(), false)));
        body.addView(stats, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2); bodyParams.topMargin = dp(10);
        hero.addView(body, bodyParams);

        if (s.transfersIn > 0) {
            TextView split = label("راتب " + money(s.salary, "EGP") + " · تحويلات وصلتك " + money(s.transfersIn, "EGP"), 11, softInk, false);
            split.setTextDirection(View.TEXT_DIRECTION_RTL); split.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(-1, -2); splitParams.topMargin = dp(8);
            hero.addView(split, splitParams);
        }
        TextView foot = label("التحويل بين حساباتك وسداد كروتك مش داخلين؛ الكاش محسوب لما اتسحب", 10, 0xff8fb0a3, false);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(-1, -2); footParams.topMargin = dp(s.transfersIn > 0 ? 2 : 8);
        hero.addView(foot, footParams);
        hero.setOnClickListener(v -> openStatement(MoneyFlow.OUT_FILTER, false));

        boolean any = s.cashSpending > 0 || s.internal > 0 || s.unlinkedTransfer > 0;
        for (MoneyFlow.Bucket bucket : MoneyFlow.STATEMENT_OUT_BUCKETS) if (s.component(bucket) > 0) any = true;
        if (!any) return;
        section("راح فين؟", null, null);
        LinearLayout box = card(); box.addView(label("نفس الفترة · EGP · كل سطر يفتح عملياته ومجموعها يساوي رقمه", 12, MUTED, false));
        for (MoneyFlow.Bucket bucket : MoneyFlow.STATEMENT_OUT_BUCKETS) {
            long amount = s.component(bucket);
            if (amount <= 0) continue;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(10), 0, dp(6));
            int icon = bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL ? R.drawable.ic_cat_atm : bucket == MoneyFlow.Bucket.OPEN_TRANSFER ? R.drawable.ic_cat_transfer : R.drawable.ic_cat_other;
            row.addView(tintedIcon(icon, bucketColor(bucket)), new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView title = label(MoneyFlow.label(bucket), 14, INK, true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.setMarginStart(dp(10)); row.addView(title, tp);
            TextView amountView = label(money(amount, "EGP"), 13, INK, true); amountView.setTextDirection(View.TEXT_DIRECTION_LTR); row.addView(amountView);
            row.addView(tintedIcon(R.drawable.ic_chevron_forward, MUTED), new LinearLayout.LayoutParams(dp(18), dp(18)));
            final MoneyFlow.Bucket target = bucket;
            row.setOnClickListener(v -> openStatement(target.name(), target == MoneyFlow.Bucket.OPEN_TRANSFER));
            box.addView(row);
        }
        if (s.unlinkedTransfer > 0) {
            TextView warning = label("تحويل غير مربوط بقيمة " + money(s.unlinkedTransfer, "EGP") + " قد يكون بين حساباتك — اربطه", 12, CORAL, true);
            warning.setPadding(0, dp(8), 0, dp(4));
            final long id = s.unlinkedTransferId;
            warning.setOnClickListener(v -> { ExpenseDb.Transaction tx = db.transactionById(id); if (tx != null) reconcileTransferDialog(tx); });
            box.addView(warning);
        }
        if (s.cashSpending > 0) box.addView(label("مصروفات من الكاش " + money(s.cashSpending, "EGP") + " · محسوبة وقت السحب، مش مضافة لـ«خرج»", 11, MUTED, false));
        if (s.internal > 0) box.addView(label("بين حساباتك وسداد كروتك " + money(s.internal, "EGP") + " · مش دخل ولا خرج", 11, MUTED, false));
    }

    /** One stat row beside the ring: a small tinted circle with an icon, then «name · detail» over the amount, nothing wrapping. */
    private LinearLayout statementOrb(int icon, int rotation, int tint, String name, String amount, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(3), dp(2), dp(3));
        ImageView glyph = tintedIcon(icon, tint);
        glyph.setRotation(rotation);
        glyph.setPadding(dp(7), dp(7), dp(7), dp(7));
        glyph.setBackground(rounded((tint & 0x00ffffff) | 0x2e000000, 99));
        row.addView(glyph, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = label(name + " · " + detail, 11, 0xffb9d3c8, false); title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView value = label(amount, 14, tint == LIME ? LIME : Color.WHITE, true);
        value.setTextDirection(View.TEXT_DIRECTION_LTR); value.setSingleLine(true);
        value.setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        copy.addView(value, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(10));
        row.addView(copy, copyParams);
        row.setContentDescription(name + " " + amount);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void openInsights(String section,LocalDate month) {
        insightsSection=section;insightsRange=MoneyFlow.Range.MONTH;selectedMonth=month;selectedTab=4;render();
    }

    private void nextCommitmentCard() {
        List<ExpenseDb.Recurring> active = activeCommitments();
        ExpenseDb.Recurring next = active.isEmpty()?null:active.get(0);
        LocalDate today = LocalDate.now();
        int week = 0;
        for (ExpenseDb.Recurring item : active) if (!item.nextDue.isAfter(today.plusDays(7))) week++;
        LinearLayout box = new LinearLayout(this); box.setGravity(Gravity.CENTER_VERTICAL);
        box.setContentDescription("الالتزامات");
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(outlined(SURFACE, BORDER, 18));
        ImageView icon = tintedIcon(R.drawable.ic_calendar, ORANGE);
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(rounded(dark ? 0x40c25e0b : 0xfffff1e3, 14));
        box.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label("الالتزامات",16,INK,true));
        if(next==null) {
            int discovered=db.commitmentSuggestions().size();
            copy.addView(label(discovered>0?discovered+" مقترحات من رسائلك · راجعها":"أقساط · اشتراكات · فواتير",12,MUTED,false));
        } else {
            copy.addView(label(dueWord(next.nextDue)+": "+commitmentTitle(next),12,MUTED,false));
            if(week>1) copy.addView(label("و"+(week-1)+" التزامات تانية خلال 7 أيام",11,MUTED,false));
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(12));
        box.addView(copy, copyParams);
        if(next!=null) {
            TextView amount = label(money(next.amountMinor, next.currency), 14, INK, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            box.addView(amount);
        }
        ImageView chevron = tintedIcon(R.drawable.ic_chevron_forward, MUTED);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(18), dp(18)); chevronParams.setMarginStart(dp(6));
        box.addView(chevron, chevronParams);
        box.setOnClickListener(v -> { selectedTab = 2; render(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(14);
        page.addView(box, params);
    }

    private void homeSourceCards() {
        List<ExpenseDb.PaymentSource> sources = db.accountSources(false);
        if (sources.isEmpty()) return;
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(0, dp(4), 0, dp(10)); scroll.addView(row);
        int index = 0;
        for (ExpenseDb.PaymentSource source : sources) {
            if (index++ >= 4) break;
            LinearLayout card = bankCard(source);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(262), dp(164));
            params.setMarginEnd(dp(12));
            row.addView(card, params);
            card.setOnClickListener(v -> { selectedTab = 1; render(); });
        }
        page.addView(scroll);
    }

    private LinearLayout bankCard(ExpenseDb.PaymentSource source) {
        UiKit.CardFace face = UiKit.cardFace(source.name);
        boolean credit = "بطاقة ائتمان".equals(source.kind);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(14));
        GradientDrawable face1 = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{face.start, face.end});
        face1.setCornerRadius(dp(20));
        GradientDrawable ring = new GradientDrawable(); ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT); ring.setStroke(dp(26), 0x12ffffff);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{face1, ring});
        layers.setLayerInset(1, dp(150), -dp(70), -dp(60), dp(60));
        card.setBackground(layers);
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        card.setElevation(dp(5));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView bank = label(source.displayName==null || source.displayName.isBlank()?face.name:source.displayName, 15, Color.WHITE, true);
        bank.setSingleLine(true); bank.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(bank, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView contactless = tintedIcon(R.drawable.ic_contactless, 0xccffffff);
        top.addView(contactless, new LinearLayout.LayoutParams(dp(22), dp(22)));
        card.addView(top);
        LinearLayout middle = new LinearLayout(this); middle.setGravity(Gravity.CENTER_VERTICAL);
        ImageView chip = new ImageView(this); chip.setImageResource(R.drawable.ic_card_chip);
        middle.addView(chip, new LinearLayout.LayoutParams(dp(38), dp(28)));
        View spacer = new View(this); middle.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout balanceColumn = new LinearLayout(this); balanceColumn.setOrientation(LinearLayout.VERTICAL);
        balanceColumn.setGravity(Gravity.END);
        ExpenseDb.BalanceSnapshot balance = db.currentBalance(source.id, "EGP");
        String caption = credit ? "رصيد ائتماني مسجل" + (balance != null && balance.estimated ? " · تقديري" : "") :
                balance != null && balance.estimated ? "رصيد تقديري" : "آخر رصيد مؤكد";
        balanceColumn.addView(label(caption, 10, 0xccffffff, false));
        balanceColumn.addView(label(balance == null ? "غير متاح" : money(balance.amountMinor, "EGP"), 18, Color.WHITE, true));
        middle.addView(balanceColumn);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(-1, 0, 1);
        card.addView(middle, middleParams);
        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView digits = label(source.last4 == null ? source.kind : "••••  " + source.last4, 14, Color.WHITE, false);
        digits.setLetterSpacing(0.12f); digits.setTextDirection(View.TEXT_DIRECTION_LTR);
        bottom.addView(digits, new LinearLayout.LayoutParams(0, -2, 1));
        String tag = credit ? "CREDIT" : source.kind.contains("حساب") ? "ACCOUNT" :
                source.kind.contains("محفظة") ? "WALLET" : "DEBIT";
        TextView type = label(tag, 10, Color.WHITE, true);
        type.setLetterSpacing(0.1f);
        type.setPadding(dp(8), dp(2), dp(8), dp(2));
        type.setBackground(rounded(0x29ffffff, 6));
        bottom.addView(type);
        card.addView(bottom);
        card.setContentDescription(face.name + " " + (source.last4 == null ? "" : source.last4));
        return card;
    }

    private void transactionsScreen() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(label("العمليات", 26, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(iconButton(R.drawable.ic_search, "بحث", () -> {
            searchOpen = !searchOpen;
            if (!searchOpen) transactionSearch = "";
            render();
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        filterParams.setMarginStart(dp(8));
        bar.addView(iconButton(R.drawable.ic_filter, "فلترة", this::transactionFilterDialog), filterParams);
        bar.addView(iconButton(bulkMode?R.drawable.ic_close:R.drawable.ic_check,"تحديد العمليات",()->{
            bulkMode=!bulkMode;bulkSelected.clear();render();
        }),new LinearLayout.LayoutParams(dp(44),dp(44)));
        page.addView(bar);
        bulkChecks.clear();bulkCount=null;bulkApply=null;
        if(bulkMode) {
            LinearLayout tools=card();bulkCount=label("المحدد: "+bulkSelected.size(),14,INK,true);tools.addView(bulkCount);
            LinearLayout actions=new LinearLayout(this);
            Button all=new Button(this);all.setText("اختيار النتائج كلها");all.setTypeface(REGULAR);
            all.setOnClickListener(v->{for(CheckBox c:bulkChecks.values())if(c.isEnabled())c.setChecked(true);});
            actions.addView(all,new LinearLayout.LayoutParams(0,-2,1));
            Button clear=new Button(this);clear.setText("إلغاء التحديد");clear.setTypeface(REGULAR);
            clear.setOnClickListener(v->{for(CheckBox c:bulkChecks.values())c.setChecked(false);});actions.addView(clear,new LinearLayout.LayoutParams(0,-2,1));tools.addView(actions);
            bulkApply=new Button(this);bulkApply.setText("تغيير تصنيف المحدد");bulkApply.setTypeface(REGULAR);bulkApply.setOnClickListener(v->bulkCategoryDialog());tools.addView(bulkApply);
            tools.addView(label("التعديل الجماعي للمصروفات فقط؛ الدخل والتحويلات الداخلية لا تتغير.",12,MUTED,false));
        }
        EditText search = null;
        if (searchOpen) {
            search = input("ابحث باسم التاجر أو المبلغ", page);
            search.setText(transactionSearch);
            search.setSelection(transactionSearch.length());
            search.requestFocus();
        }

        // One row: the period chip (month, or the custom from–to range) and the filters chip with the count of active
        // filters; one muted line under them names what is active. Nothing else sits between the title and the list.
        android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this); chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); chips.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chips);
        String periodText = transactionPeriod == null ? selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ar"))) : periodLabel(transactionPeriod);
        TextView periodChip = addChip(chips, periodText + "  ⌄", transactionPeriod != null, this::choosePeriodMenu);
        if (transactionPeriod == null) { periodChip.setBackground(rounded(ACCENT_PALE, 99)); periodChip.setTextColor(ACCENT); }
        periodChip.setContentDescription("الفترة · " + periodText);
        int active = activeFilterCount();
        TextView filterChip = addChip(chips, (active > 0 ? "فلاتر · " + active : "فلاتر") + "  ⌄", active > 0, this::transactionFilterDialog);
        filterChip.setContentDescription("فلترة");
        if (active > 0) {
            TextView clear = addChip(chips, "✕", false, () -> { clearTransactionFilters(); render(); });
            clear.setPadding(dp(12), 0, dp(12), 0); clear.setContentDescription("مسح الفلاتر");
        }
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, -2); chipParams.topMargin = dp(14);
        page.addView(chipScroll, chipParams);
        String summary = filterSummary();
        if (summary != null) {
            TextView line = label(summary, 12, MUTED, false);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2); lineParams.topMargin = dp(6);
            page.addView(line, lineParams);
        }

        List<ExpenseDb.Transaction> month = transactionPeriod==null ? db.transactionsForMonth(selectedMonth) : db.transactionsBetween(transactionPeriod[0],transactionPeriod[1]);
        int uncategorized = 0;
        for (ExpenseDb.Transaction item : month)
            if ("expense".equals(item.kind) && "غير مصنف".equals(item.category)) {
                uncategorized++;
            }
        if (uncategorized > 0) {
            LinearLayout banner = new LinearLayout(this); banner.setGravity(Gravity.CENTER_VERTICAL);
            banner.setPadding(dp(14), dp(12), dp(14), dp(12));
            banner.setBackground(rounded(dark ? 0x33d9a21b : 0xfffff6db, 16));
            int ink = dark ? 0xfff3d98a : 0xff5c4700;
            banner.addView(tintedIcon(R.drawable.ic_cat_other, ink), new LinearLayout.LayoutParams(dp(20), dp(20)));
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1); textParams.setMarginStart(dp(10));
            banner.addView(label(uncategorized == 1 ? "عملية محتاجة تصنيف" : uncategorized + " عمليات محتاجة تصنيف", 13, ink, false), textParams);
            banner.addView(label("عرض الكل", 13, ink, true));
            banner.setOnClickListener(v -> {
                transactionCategoryFilter = "غير مصنف"; transactionKindFilter = "expense";
                transactionBucketFilter = ""; transactionCurrencyFilter = ""; transactionSourceFilter = null;
                transactionExcludeTransfers = false; transactionSearch = ""; searchOpen = false;
                bulkMode = false; bulkSelected.clear(); render();
            });
            LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(-1, -2); bannerParams.topMargin = dp(12);
            page.addView(banner, bannerParams);
        }

        int openTransfers = 0; long openAmount = 0;
        for (ExpenseDb.Transaction item : month)
            if (isSentTransfer(item) && "EGP".equals(item.currency)) { openTransfers++; openAmount += item.amountMinor; }
        if (openTransfers > 0 && transactionBucketFilter.isEmpty() && !bulkMode) {
            LinearLayout banner = new LinearLayout(this); banner.setGravity(Gravity.CENTER_VERTICAL);
            banner.setPadding(dp(14), dp(12), dp(14), dp(12));
            banner.setBackground(rounded(dark ? 0x40c25e0b : 0xfffff1e3, 16));
            banner.addView(tintedIcon(R.drawable.ic_cat_transfer, ORANGE), new LinearLayout.LayoutParams(dp(20), dp(20)));
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1); textParams.setMarginStart(dp(10));
            banner.addView(label(openTransfers + " تحويل لناس غرضها مش محدد · " + money(openAmount, "EGP"), 13, ORANGE, false), textParams);
            banner.addView(label("حدد", 13, ORANGE, true));
            banner.setOnClickListener(v -> { transactionBucketFilter = MoneyFlow.Bucket.OPEN_TRANSFER.name(); bulkMode = true; bulkSelected.clear(); render(); });
            LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(-1, -2); bannerParams.topMargin = dp(8);
            page.addView(banner, bannerParams);
        }

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        Set<Long> candidates = recurringCandidates;
        Map<Long, Long> dayOffsets = db.reimbursementOffsets();
        List<TransactionTimelineRow> timeline=transactionTimeline(month);
        Runnable refresh = () -> {
            results.removeAllViews();bulkChecks.clear();Set<Long> visible=new HashSet<>();
            String text = transactionSearch.trim().toLowerCase(Locale.ROOT);
            LocalDate currentDay = null;
            LinearLayout group = null;
            TextView dayTotal = null;
            MoneyTotals net = new MoneyTotals();
            int count = 0;
            for (TransactionTimelineRow entry : timeline) {
                ExpenseDb.Transaction tx=entry.transaction;
                if(tx==null){if(!matchesCaptureFilters(entry.event,text))continue;}
                else if(!matchesTransactionFilters(tx,text))continue;
                // Rows fully refunded contribute nothing to the home «مصروفاتك» number, so they stay out of its list too.
                if(tx!=null&&MoneyFlow.SPENDING_FILTER.equals(transactionBucketFilter)&&ExpenseDb.effectiveAmount(tx,dayOffsets)==0)continue;
                LocalDate day = Instant.ofEpochMilli(entry.when()).atZone(ZoneId.systemDefault()).toLocalDate();
                if (!day.equals(currentDay)) {
                    if (dayTotal != null) setDayTotal(dayTotal, net);
                    currentDay = day; net = new MoneyTotals();
                    LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
                    header.setPadding(dp(4), 0, dp(4), 0);
                    header.addView(label(dayLabel(day), 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
                    dayTotal = label("", 12, MUTED, false);
                    dayTotal.setTextDirection(View.TEXT_DIRECTION_LTR);
                    header.addView(dayTotal);
                    LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
                    headerParams.topMargin = dp(16); headerParams.bottomMargin = dp(4);
                    results.addView(header, headerParams);
                    group = listCard(results);
                    ((LinearLayout.LayoutParams) group.getLayoutParams()).topMargin = 0;
                }
                if(entry.event!=null)captureEventRow(group,entry.event);
                else{
                    net.add(tx.currency,tx.kind,tx.category,ExpenseDb.effectiveAmount(tx,dayOffsets));
                    if("expense".equals(tx.kind))visible.add(tx.id);
                    transactionRow(group, tx, candidates.contains(tx.id));
                }
                count++;
            }

            if (dayTotal != null) setDayTotal(dayTotal, net);
            bulkSelected.retainAll(visible);updateBulkCount();
            if (count == 0) {
                LinearLayout box = card(results);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.addView(label("مفيش عمليات تطابق البحث والفلاتر", 14, MUTED, false));
                Button reset=new Button(this);reset.setText("مسح البحث والفلاتر");reset.setTypeface(REGULAR);
                reset.setOnClickListener(v->{resetTransactionFilters();render();});box.addView(reset);
            }
        };
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                savePageScroll();transactionSearch = s.toString();renderedPageKey=pageContextKey();refresh.run();pageScroll.scrollTo(0,0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        refresh.run();
    }

    private void setDayTotal(TextView view,MoneyTotals totals) {
        StringBuilder text=new StringBuilder();
        for(Map.Entry<String,MoneyTotals.Totals> e:totals.currencies.entrySet()) {
            if(text.length()>0)text.append("\n");
            // A filtered list (the rows behind one home number or movement line) is headed by that bucket's own
            // total, e.g. «سحب نقدي 250» or «بين حساباتك 300»; the plain list keeps «دخل · خرج».
            if(!transactionBucketFilter.isEmpty())
                text.append(MoneyFlow.filterLabel(transactionBucketFilter)).append(" ").append(money(e.getValue().of(transactionBucketFilter),e.getKey()));
            else text.append("دخل ").append(money(e.getValue().income,e.getKey())).append(" · خرج ").append(money(e.getValue().expense,e.getKey()));
        }
        view.setText(text.toString());view.setMaxWidth(dp(235));view.setSingleLine(false);
        view.setContentDescription(text+" · التحويلات الداخلية لا تدخل الدخل أو المصروف");
    }

    private void updateBulkCount() {
        if(bulkCount!=null)bulkCount.setText("المحدد: "+bulkSelected.size()+" مصروف");
        if(bulkApply!=null)bulkApply.setEnabled(!bulkSelected.isEmpty());
    }

    private void bulkCategoryDialog() {
        if(bulkSelected.isEmpty())return;
        List<Long> ids=new ArrayList<>(bulkSelected);
        LinearLayout form=form();Spinner category=spinner("التصنيف الجديد",CATEGORIES,form);
        StringBuilder preview=new StringBuilder("الأرقام والحسابات والمصدر لن تتغير.\n");int n=0;
        for(Long id:ids){ExpenseDb.Transaction tx=db.transactionById(id);if(tx!=null&&n++<5)preview.append(tx.merchant).append(" · ").append(money(tx.amountMinor,tx.currency)).append("\n");}
        form.addView(label(preview.toString(),13,MUTED,false));
        new ChoiceDialogBuilder().setTitle("تغيير تصنيف "+ids.size()+" مصروف؟").setView(form)
                .setPositiveButton("اعتماد التصنيف",(d,w)->{
                    int count=db.updateCategories(ids,category.getSelectedItem().toString());
                    bulkSelected.clear();bulkMode=false;toast("تم تصنيف "+count+" مصروف");render();
                }).setNegativeButton("رجوع",null).show();
    }

    private void chooseTransactionPeriod() {
        LocalDate shownStart=transactionPeriod==null?selectedMonth:transactionPeriod[0];
        showTransactionDatePicker("من يوم",shownStart,start->{
            LocalDate shownEnd=transactionPeriod==null?selectedMonth.plusMonths(1).minusDays(1):transactionPeriod[1].minusDays(1);
            if(shownEnd.isBefore(start))shownEnd=start;
            showTransactionDatePicker("إلى يوم",shownEnd,end->{
                if(end.isBefore(start)){toast("تاريخ النهاية لازم يكون بعد البداية أو يساويه");return;}
                transactionPeriod=new LocalDate[]{start,end.plusDays(1)};
                render();
            });
        });
    }

    private void showTransactionDatePicker(String heading,LocalDate shown,java.util.function.Consumer<LocalDate> selected) {
        android.content.res.Configuration arabic=new android.content.res.Configuration(getResources().getConfiguration());
        arabic.setLocale(new Locale("ar"));
        // The spinner reads the default locale when it is built, so month names would follow the phone's language.
        Locale previous=Locale.getDefault();
        android.widget.DatePicker picker;
        try {Locale.setDefault(new Locale("ar"));picker=new android.widget.DatePicker(new android.view.ContextThemeWrapper(createConfigurationContext(arabic),getTheme()));}
        finally {Locale.setDefault(previous);}
        picker.updateDate(shown.getYear(),shown.getMonthValue()-1,shown.getDayOfMonth());
        periodPicker=picker;
        new ChoiceDialogBuilder().setCustomTitle(periodPickerTitle(heading)).setView(picker)
                .setPositiveButton("موافق",(dialog,which)->selected.accept(LocalDate.of(picker.getYear(),picker.getMonth()+1,picker.getDayOfMonth())))
                .setNegativeButton("إلغاء",null).show();
    }

    private TextView periodPickerTitle(String text) {
        TextView title=label(text,16,INK,true);
        title.setPadding(dp(20),dp(12),dp(20),dp(8));
        return title;
    }

    /** «7 أكتوبر → 13 أكتوبر» for an exclusive-end period; the year is added when the two ends differ in year. */
    static String periodLabel(LocalDate[] period) {
        LocalDate from = period[0], to = period[1].minusDays(1);
        DateTimeFormatter day = DateTimeFormatter.ofPattern(from.getYear() == to.getYear() ? "d MMMM" : "d MMMM yyyy", new Locale("ar"));
        return from.equals(to) ? from.format(day) : from.format(day) + " → " + to.format(day);
    }

    /** How many list filters are active, not counting the period: the number on the «فلاتر» chip. */
    private int activeFilterCount() {
        int n = 0;
        if (!transactionKindFilter.isEmpty()) n++;
        if (!transactionCategoryFilter.isEmpty()) n++;
        if (transactionSourceFilter != null) n++;
        if (!transactionBucketFilter.isEmpty()) n++;
        if (!transactionCurrencyFilter.isEmpty()) n++;
        if (transactionExcludeTransfers) n++;
        return n;
    }

    /** One line naming the active filters («خرج · EGP · كشف الفترة»), or null when the list is unfiltered. */
    private String filterSummary() {
        List<String> parts = new ArrayList<>();
        if (!transactionBucketFilter.isEmpty()) parts.add(MoneyFlow.filterLabel(transactionBucketFilter));
        if (!transactionKindFilter.isEmpty()) parts.add("expense".equals(transactionKindFilter) ? "مصروف" : "income".equals(transactionKindFilter) ? "دخل" : "تحويل");
        if (!transactionCategoryFilter.isEmpty()) parts.add(transactionCategoryFilter);
        if (transactionSourceFilter != null)
            for (ExpenseDb.PaymentSource source : db.paymentSources())
                if (source.id == transactionSourceFilter) parts.add(source.name + (source.last4 == null ? "" : " •••• " + source.last4));
        if (!transactionCurrencyFilter.isEmpty()) parts.add(transactionCurrencyFilter);
        if (transactionExcludeTransfers) parts.add("بدون تحويلات");
        if (transactionStatementList) parts.add("كشف الفترة");
        if (parts.isEmpty()) return null;
        return String.join(" · ", parts);
    }

    /** Clears the list filters but keeps the chosen period and the search text. */
    private void clearTransactionFilters() {
        transactionCategoryFilter = ""; transactionSourceFilter = null; transactionKindFilter = ""; transactionBucketFilter = "";
        transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionStatementList = false;
    }

    /** The period chip: pick a month, pick a day-to-day range, or go back to the month view. */
    private void choosePeriodMenu() {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("اختيار شهر"); actions.add(this::chooseMonth);
        labels.add("فترة من يوم لـ يوم"); actions.add(this::chooseTransactionPeriod);
        if (transactionPeriod != null) {
            labels.add("رجوع لعرض " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ar"))));
            actions.add(() -> { transactionPeriod = null; transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionStatementList = false; render(); });
        }
        new ChoiceDialogBuilder().setTitle("الفترة").setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("إلغاء", null).show();
    }

    private void transactionFilterDialog() {
        LinearLayout form = form();
        form.addView(label("النوع", 12, MUTED, true));
        LinearLayout kindsRow = new LinearLayout(this); kindsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams kindsParams = new LinearLayout.LayoutParams(-1, -2); kindsParams.topMargin = dp(6); kindsParams.bottomMargin = dp(10);
        form.addView(kindsRow, kindsParams);
        String[][] kinds = {{"الكل", ""}, {"مصروف", "expense"}, {"دخل", "income"}, {"تحويل", "transfer"}};
        String[] chosenKind = {transactionKindFilter};
        Runnable[] drawKinds = new Runnable[1];
        drawKinds[0] = () -> {
            kindsRow.removeAllViews();
            for (String[] kind : kinds) addChip(kindsRow, kind[0], chosenKind[0].equals(kind[1]), () -> { chosenKind[0] = kind[1]; drawKinds[0].run(); });
        };
        drawKinds[0].run();
        String[] categories = new String[CATEGORIES.length + 1];
        categories[0] = "كل التصنيفات";
        System.arraycopy(CATEGORIES, 0, categories, 1, CATEGORIES.length);
        Spinner category = spinner("التصنيف", categories, form);
        for (int i = 1; i < categories.length; i++) if (categories[i].equals(transactionCategoryFilter)) category.setSelection(i);
        List<ExpenseDb.PaymentSource> sources = db.paymentSources();
        String[] sourceLabels = new String[sources.size() + 1];
        sourceLabels[0] = "كل الحسابات";
        for (int i = 0; i < sources.size(); i++) {
            ExpenseDb.PaymentSource source = sources.get(i);
            sourceLabels[i + 1] = source.name + (source.last4 == null ? "" : "  •••• " + source.last4);
        }
        Spinner source = spinner("الحساب", sourceLabels, form);
        for (int i = 0; i < sources.size(); i++)
            if (transactionSourceFilter != null && transactionSourceFilter == sources.get(i).id) source.setSelection(i + 1);
        if (!transactionBucketFilter.isEmpty() || !transactionCurrencyFilter.isEmpty() || transactionExcludeTransfers) {
            List<String> fixed = new ArrayList<>();
            if (!transactionBucketFilter.isEmpty()) fixed.add(MoneyFlow.filterLabel(transactionBucketFilter));
            if (!transactionCurrencyFilter.isEmpty()) fixed.add(transactionCurrencyFilter);
            if (transactionExcludeTransfers) fixed.add("بدون تحويلات");
            TextView note = label("مفتوح من الرئيسية: " + String.join(" · ", fixed) + " — «مسح الفلاتر» يشيله", 12, MUTED, false);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2); noteParams.topMargin = dp(8);
            form.addView(note, noteParams);
        }
        new ChoiceDialogBuilder().setTitle("فلترة العمليات").setView(form)
                .setPositiveButton("تطبيق", (dialog, which) -> {
                    int c = category.getSelectedItemPosition();
                    int s = source.getSelectedItemPosition();
                    transactionKindFilter = chosenKind[0];
                    if (!transactionKindFilter.isEmpty()) transactionBucketFilter = "";
                    transactionCategoryFilter = c <= 0 ? "" : categories[c];
                    transactionSourceFilter = s <= 0 ? null : sources.get(s - 1).id;
                    render();
                })
                .setNeutralButton("مسح الفلاتر", (dialog, which) -> { clearTransactionFilters(); render(); })
                .setNegativeButton("إلغاء", null).show();
    }

    private void transactionRow(ExpenseDb.Transaction item, boolean suggested) {
        transactionRow(listCard(page), item, suggested);
    }

    private void transactionRow(LinearLayout container, ExpenseDb.Transaction item, boolean suggested) {
        if (container.getChildCount() > 0) divider(container);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if(bulkMode&&selectedTab==6) {
            CheckBox check=new CheckBox(this);check.setContentDescription("تحديد "+item.merchant);
            check.setEnabled("expense".equals(item.kind));check.setChecked(bulkSelected.contains(item.id));
            check.setOnCheckedChangeListener((b,checked)->{if(checked)bulkSelected.add(item.id);else bulkSelected.remove(item.id);updateBulkCount();});
            bulkChecks.put(item.id,check);line.addView(check,new LinearLayout.LayoutParams(dp(40),dp(48)));
        }
        line.addView(rowBadge(item, 42), new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView merchantName = label(UiKit.displayName(item.merchant), 14, INK, true);
        merchantName.setSingleLine(true); merchantName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(merchantName);
        LinearLayout meta = new LinearLayout(this); meta.setGravity(Gravity.CENTER_VERTICAL);
        String categoryName = "income".equals(item.kind) ? "دخل" : "transfer".equals(item.kind) ? "تحويلات" : item.category;
        meta.addView(categoryPill(categoryName));
        if(item.needsReview) meta.addView(label("expense".equals(item.kind)&&MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(item.category)?" · تحويل محتاج ربط":" · للمراجعة",11,CORAL,true));
        if (UiKit.isInstaPay(item.merchant)) {
            LinearLayout.LayoutParams ipaParams = new LinearLayout.LayoutParams(-2, -2); ipaParams.setMarginStart(dp(4));
            meta.addView(instaPayPill(), ipaParams);
        }
        String time = transactionTimeLabel(item,false);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2); timeParams.setMarginStart(dp(6));
        meta.addView(label(time, 11, MUTED, false), timeParams);
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(12));
        line.addView(copy, copyParams);
        // In the home «مصروفات» list a refunded purchase shows what it contributes (net), so the rows add up to the header.
        long repaid = MoneyFlow.SPENDING_FILTER.equals(transactionBucketFilter) && "expense".equals(item.kind) ? db.reimbursedOf(item.id) : 0;
        TextView amount = label(repaid > 0 ? "−" + money(Math.max(0, item.amountMinor - repaid), item.currency) : signedMoney(item), 14, kindColor(item.kind), true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(amount);
        row.addView(line);
        if (repaid > 0) {
            TextView net = label("الشراء " + money(item.amountMinor, item.currency) + " − استرداد " + money(repaid, item.currency) + " = نصيبك", 12, INCOME, true);
            net.setPadding(dp(8), dp(6), dp(54), 0); row.addView(net);
        }
        String share=reimbursementShare(item);
        if(share!=null) {
            TextView personal=label(share,12,INCOME,true);
            personal.setPadding(dp(8),dp(6),dp(54),0);row.addView(personal);
        }
        if (item.note != null && !item.note.isBlank()) {
            TextView note = label(item.note, 12, MUTED, false);
            note.setPadding(dp(54), 0, 0, 0);
            row.addView(note);
        }
        row.setOnClickListener(v -> {
            if(bulkMode&&selectedTab==6){CheckBox c=bulkChecks.get(item.id);if(c!=null&&c.isEnabled())c.toggle();return;}
            transactionReturnTab = selectedTab;
            transactionJourney.clear();
            selectedTransaction = item;
            selectedTab = 10;
            render();
        });
        if (suggested && "expense".equals(item.kind)) {
            TextView confirm = label("↻  بيتكرر كل شهر؟ سجّله التزام", 12, ACCENT, true);
            confirm.setPadding(dp(54), dp(4), 0, 0);
            confirm.setOnClickListener(v -> confirmRecurring(item));
            row.addView(confirm);
        }
        container.addView(row);
    }

    private void transactionDetailsScreen(ExpenseDb.Transaction item) {
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", () -> {
            returnFromTransaction();
        }), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("تفاصيل العملية", 15, INK, true); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(iconButton(R.drawable.ic_more, "خيارات", () -> transactionOptions(item)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(toolbar);
        page.addView(label(transactionJourney.snapshot().length>0?"عملية مرتبطة · رجوع يرجعك للعملية اللي فتحتها منها":
                "رجوع إلى "+(transactionReturnTab==0?"الرئيسية":transactionReturnTab==4?"التحليلات":transactionReturnTab==11?"تفاصيل الالتزام":"العمليات والبحث"),12,MUTED,false));

        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2); heroParams.topMargin = dp(14);
        page.addView(hero, heroParams);
        View badge = merchantBadge(item.merchant, item.category, 80);
        badge.setElevation(dp(6));
        hero.addView(badge, new LinearLayout.LayoutParams(dp(80), dp(80)));
        TextView merchant = label(UiKit.displayName(item.merchant), 20, INK, true); merchant.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2); nameParams.topMargin = dp(10);
        hero.addView(merchant, nameParams);
        if (UiKit.isInstaPay(item.merchant)) {
            TextView handle = label(item.merchant, 12, MUTED, false); handle.setGravity(Gravity.CENTER);
            handle.setTextDirection(View.TEXT_DIRECTION_LTR);
            hero.addView(handle, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView amount = label(signedMoney(item), 36, kindColor(item.kind), true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR); amount.setGravity(Gravity.CENTER);
        hero.addView(amount, new LinearLayout.LayoutParams(-1, -2));
        String share=reimbursementShare(item);
        if(share!=null) {
            TextView personal=label(share,14,INCOME,true);personal.setGravity(Gravity.CENTER);
            hero.addView(personal,new LinearLayout.LayoutParams(-1,-2));
        }
        String when = transactionTimeLabel(item,true);
        TextView date = label(when, 13, MUTED, false); date.setGravity(Gravity.CENTER);
        hero.addView(date, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout details = card();
        ((LinearLayout.LayoutParams) details.getLayoutParams()).topMargin = dp(20);
        String categoryName = "income".equals(item.kind) ? "دخل" : "transfer".equals(item.kind) ? "تحويلات" : item.category;
        TextView categoryValue = categoryPill(categoryName);
        categoryValue.setOnClickListener(v -> editTransactionDialog(item));
        detailRow(details, "التصنيف", categoryValue);
        String type = "income".equals(item.kind) ? "دخل" : "expense".equals(item.kind) ? "مصروف" : "تحويل";
        detailRow(details, "النوع", label(type, 14, INK, true));
        String origin = "SMS".equals(item.source) ? "رسالة البنك تلقائيًا" : "يدوي".equals(item.source) ? "إضافة يدوية" :
                "CSV".equals(item.source) ? "ملف CSV" : item.source;
        detailRow(details, "اتسجلت من", label(origin, 14, INK, true));
        if(item.needsReview) detailRow(details,"حالة المراجعة",label("expense".equals(item.kind)&&MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(item.category)?"تحويل محتاج ربط؛ الخصم ما زال محسوبًا لحين مراجعتك":"مصدر العملية يحتاج مراجعة؛ المبلغ ما زال محسوبًا",12,CORAL,false));
        TextView original=label("عرض الرسالة الأصلية",14,ACCENT,true);
        original.setOnClickListener(v -> showOriginalMessages(db.sourceEvidence(item.id)));
        detailRow(details,"مصدر الرقم",original);
        ExpenseDb.PaymentSource paidFrom = null;
        String destination = null;
        for (ExpenseDb.PaymentSource source : db.paymentSources()) {
            if (item.paymentSourceId != null && item.paymentSourceId == source.id) paidFrom = source;
            if (item.destinationSourceId != null && item.destinationSourceId == source.id)
                destination = source.name + (source.last4 == null ? "" : "  •••• " + source.last4);
        }
        if (destination != null) detailRow(details, "التحويل إلى", label(destination, 14, INDIGO, true));
        cardPaymentDetails(details,item);
        if ("expense".equals(item.kind)) {
            long repaid = db.reimbursedOf(item.id);
            if (repaid > 0) {
                detailRow(details, "رجعلك منها", label(money(repaid, item.currency), 14, INCOME, true));
                detailRow(details, "نصيبك الشخصي", label(money(Math.max(0, item.amountMinor - repaid), item.currency), 14, INK, true));
                for(ExpenseDb.Transaction back:db.reimbursingIncomes(item.id)) {
                    TextView incoming=label(UiKit.displayName(back.merchant)+" · "+money(back.amountMinor,back.currency),13,ACCENT,true);
                    incoming.setOnClickListener(v->openRelatedTransaction(back));detailRow(details,"عرض الفلوس الراجعة",incoming);
                }
            } else if (MoneyFlow.isCashWithdrawal(item.kind, item.category) && !db.reimbursingIncomes(item.id).isEmpty()) {
                // A link created before withdrawals were excluded is kept for review, not applied.
                TextView link = label("استرداد قديم مش بيتحسب — راجعه", 14, ACCENT, true);
                link.setOnClickListener(v -> unlinkReimbursementDialog(item));
                detailRow(details, "استرداد", link);
            }
        }
        if ("income".equals(item.kind)) {
            ExpenseDb.Transaction repaidFor = db.reimbursedExpense(item.id);
            if (repaidFor == null) {
                TextView link = label("دي فلوس رجعتلي عن مشتريات؟", 14, ACCENT, true);
                link.setOnClickListener(v -> reimbursementDialog(item));
                detailRow(details, "استرداد", link);
            } else if (db.ignoredReimbursement(item.id) != null) {
                TextView link = label("مرتبط بسحب نقدي · مش بيتحسب — راجعه", 14, ACCENT, true);
                link.setOnClickListener(v -> unlinkReimbursementDialog(item));
                detailRow(details, "استرداد", link);
            } else {
                TextView link = label(UiKit.displayName(repaidFor.merchant) + " · " + money(repaidFor.amountMinor, repaidFor.currency), 14, ACCENT, true);
                link.setOnClickListener(v -> openRelatedTransaction(repaidFor));
                detailRow(details, "رجعتلك عن", link);
            }
        }
        if (item.note != null && !item.note.isBlank()) detailRow(details, "الوصف", label(item.note, 14, INK, false));
        if (paidFrom == null && item.cardLast4 != null) detailRow(details, "رقم الربط", label("•••• " + item.cardLast4, 14, MUTED, true));

        if (paidFrom != null) {
            TextView caption = label("transfer".equals(item.kind) ? "اتحولت من" : "income".equals(item.kind) ? "دخلت على" : "اتدفعت من", 13, MUTED, false);
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
            captionParams.topMargin = dp(18); captionParams.bottomMargin = dp(8);
            page.addView(caption, captionParams);
            page.addView(cardStrip(paidFrom), new LinearLayout.LayoutParams(-1, dp(84)));
        }

        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView edit = label("تعديل العملية", 15, onPrimary(), true);
        edit.setGravity(Gravity.CENTER); edit.setBackground(rounded(primaryFill(), 16));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(54), 1));
        edit.setOnClickListener(v -> editTransactionDialog(item));
        ImageView delete = tintedIcon(R.drawable.ic_trash, CORAL);
        delete.setPadding(dp(15), dp(15), dp(15), dp(15));
        delete.setBackground(outlined(SURFACE, dark ? 0xff5a2a2a : 0xfff1d3d3, 16));
        delete.setContentDescription("حذف العملية");
        delete.setOnClickListener(v -> confirmDeleteTransaction(item));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(54), dp(54)); deleteParams.setMarginStart(dp(10));
        actions.addView(delete, deleteParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2); actionParams.topMargin = dp(24);
        page.addView(actions, actionParams);
    }

    /** Evidence this device can no longer open is shown as a notice instead of silently disappearing. */
    static String originalMessageText(ExpenseDb.SourceEvidence evidence) {
        if (evidence.body != null) return evidence.body;
        return "النص الأصلي غير متاح على هذا الجهاز";
    }

    private void openCaptureEventFromIntent(Intent intent){
        long id=intent==null?-1:intent.getLongExtra(CaptureNotifier.EXTRA_CAPTURE_EVENT,-1);
        if(id<0)return;intent.removeExtra(CaptureNotifier.EXTRA_CAPTURE_EVENT);
        CaptureEvents.Event event=new CaptureEvents(db).byId(id);if(event==null)return;
        selectedTab=6;resetTransactionFilters();selectedMonth=Instant.ofEpochMilli(event.displayWhen()).atZone(ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1);render();showCaptureEvent(event);
    }
    private static final class TransactionTimelineRow{
        final ExpenseDb.Transaction transaction;final CaptureEvents.Event event;
        TransactionTimelineRow(ExpenseDb.Transaction transaction,CaptureEvents.Event event){this.transaction=transaction;this.event=event;}
        long when(){return event==null?transaction.when:event.displayWhen();}
    }
    private List<TransactionTimelineRow> transactionTimeline(List<ExpenseDb.Transaction> active){
        List<TransactionTimelineRow> rows=new ArrayList<>();for(ExpenseDb.Transaction tx:active)rows.add(new TransactionTimelineRow(tx,null));
        LocalDate start=transactionPeriod==null?selectedMonth:transactionPeriod[0],end=transactionPeriod==null?selectedMonth.plusMonths(1):transactionPeriod[1];
        for(CaptureEvents.Event event:new CaptureEvents(db).timelineBetween(start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))rows.add(new TransactionTimelineRow(null,event));
        rows.sort((a,b)->Long.compare(b.when(),a.when()));return rows;
    }
    private boolean matchesTransactionFilters(ExpenseDb.Transaction tx,String text){
        return (transactionKindFilter.isEmpty()||transactionKindFilter.equals(tx.kind))
                &&(transactionCategoryFilter.isEmpty()||transactionCategoryFilter.equals(tx.category))
                &&(!transactionExcludeTransfers||!isSentTransfer(tx))
                &&MoneyFlow.matchesFilter(transactionBucketFilter,bucketOf(tx))
                &&(!transactionStatementList||!ExpenseDb.paidFromCash(tx,sourceKinds()))
                &&(transactionCurrencyFilter.isEmpty()||transactionCurrencyFilter.equals(tx.currency))
                &&(transactionSourceFilter==null||transactionSourceFilter.equals(tx.paymentSourceId)||transactionSourceFilter.equals(tx.destinationSourceId))
                &&(text.isEmpty()||(tx.merchant+" "+tx.category+" "+money(tx.amountMinor,tx.currency)).toLowerCase(Locale.ROOT).contains(text));
    }
    private boolean matchesCaptureFilters(CaptureEvents.Event event,String text){
        // Canceled attempts are audit history, not active expenses awaiting classification.
        if ("غير مصنف".equals(transactionCategoryFilter)) return false;
        // The list behind the home number holds only the rows that add up to it; no cancelled attempts beside them.
        if (!MoneyFlow.listsCaptureHistory(transactionBucketFilter)) return false;
        if(event.original!=null){
            String query=text.replace("ملغاة", "").trim();return matchesTransactionFilters(event.original,query);
        }
        if(!transactionKindFilter.isEmpty()||!transactionCategoryFilter.isEmpty()||!transactionBucketFilter.isEmpty()||transactionSourceFilter!=null||transactionExcludeTransfers)return false;
        return (transactionCurrencyFilter.isEmpty()||transactionCurrencyFilter.equals(event.currency))
                &&(text.isEmpty()||(event.title+" "+(event.cancelled()?"ملغاة":"تحتاج مراجعة")+" "+(event.amount==null?"":money(event.amount,event.currency))).toLowerCase(Locale.ROOT).contains(text));
    }
    private void captureEventRow(LinearLayout container,CaptureEvents.Event event){
        if(container.getChildCount()>0)divider(container);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,dp(10),0,dp(10));container.addView(row);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(merchantBadge(event.title,event.original==null?"تحويلات":event.original.category,42),new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView title=label(event.title,14,INK,true);title.setMaxLines(2);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMarginStart(dp(10));heading.addView(title,tp);
        LinearLayout badge=new LinearLayout(this);badge.setGravity(Gravity.CENTER_VERTICAL);badge.setPadding(dp(8),dp(5),dp(8),dp(5));badge.setBackground(outlined(SURFACE,BORDER,99));
        int color=event.cancelled()?CORAL:MUTED;badge.addView(tintedIcon(R.drawable.ic_close,color),new LinearLayout.LayoutParams(dp(18),dp(18)));badge.addView(label(event.cancelled()?"ملغاة":"تحتاج مراجعة",12,color,true));heading.addView(badge);row.addView(heading);
        String amount=event.amount==null?"المبلغ غير مؤكد":money(event.amount,event.currency);
        TextView value=label(amount,17,MUTED,true);if(event.cancelled())value.setPaintFlags(value.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);row.addView(value);
        row.addView(label(event.cancelled()?"ملغاة · مش محسوبة في الإجماليات":"العملية الأصلية لم تتغير",12,MUTED,false));
        if(event.original!=null)row.addView(label(transactionTimeLabel(event.original,false),12,MUTED,false));
        row.addView(label((event.body==null?"وقت الأرشفة: ":"استلام رسالة الإلغاء: ")+sourceDate(event.when),12,MUTED,false));
        row.setContentDescription(event.title+" · "+(event.cancelled()?"ملغاة":"تحتاج مراجعة")+" · "+amount+" · عرض المصدر");row.setOnClickListener(v->showCaptureEvent(event));
    }
    private void showCaptureEvent(CaptureEvents.Event event){
        String message=event.cancelled()?"المحاولة ملغاة ومش محسوبة في الإجماليات. إلغاء السداد لا يلغي المشتريات الأصلية أو رسومها.":"لم نجد عملية واحدة مؤكدة لنربطها برسالة الإلغاء. راجع المصدر والعمليات؛ لن نحذف عملية بالتخمين.";
        if("needs_confirmation".equals(event.status))message="دي رسالة إلغاء قديمة من الاستيراد. التحويل الأصلي لم يتغير؛ راجع رسالة البنك واختر المطابقة ثم أكد الإلغاء بنفسك.";
        if(event.body==null)message+=event.original==null?"\nتفاصيل الأرشيف أو الرسالة غير متاحة للقراءة؛ الأصل المحفوظ لم يتغير. وقت العرض هو وقت الأرشفة.":"\nهذا إلغاء محفوظ من إصدار سابق؛ رسالة الإلغاء نفسها لم تكن محفوظة. وقت العرض هو وقت الأرشفة.";
        AlertDialog.Builder dialog=new ChoiceDialogBuilder().setTitle(event.cancelled()?"محاولة ملغاة":"إلغاء يحتاج مراجعة").setMessage(message)
                .setPositiveButton("عرض المصدر المحفوظ",(d,w)->showOriginalMessages(new CaptureEvents(db).evidence(event))).setNegativeButton("إغلاق",null);
        if(!new CaptureEvents(db).choices(event).isEmpty())dialog.setNeutralButton("مراجعة المطابقة",(d,w)->reviewCancellationMatch(event));dialog.show();
    }
    private void reviewCancellationMatch(CaptureEvents.Event event){
        CaptureEvents store=new CaptureEvents(db);List<ExpenseDb.Transaction> choices=store.choices(event);if(choices.isEmpty()){toast("لا توجد مطابقة آمنة الآن؛ راجع الرسالة");return;}
        String[] names=new String[choices.size()];for(int i=0;i<choices.size();i++){ExpenseDb.Transaction row=choices.get(i);names[i]=row.merchant+" · "+money(row.amountMinor,row.currency)+" · "+sourceDate(row.when)+" · #"+row.id;}
        new ChoiceDialogBuilder().setTitle("اختار العملية التي ألغيت").setItems(names,(d,w)->{
            ExpenseDb.Transaction chosen=choices.get(w);String identity=store.identity(chosen.id);
            new ChoiceDialogBuilder().setTitle("تأكيد مطابقة الإلغاء؟").setMessage(names[w]+"\nهنحفظ العملية مع رسالتي الخصم والإلغاء في الأرشيف ونخرجها من الإجماليات. تأكد من المصدر قبل الاختيار.")
                    .setPositiveButton("تأكيد الإلغاء",(a,b)->{try{CaptureEvents.Event resolved=store.confirm(event.id,chosen.id,identity);CaptureNotifier.notifyEvent(this,resolved);render();showCaptureEvent(resolved);}catch(IllegalArgumentException error){toast("البيانات اتغيرت؛ راجع المطابقة من جديد");}})
                    .setNeutralButton("عرض رسالة الخصم",(a,b)->showOriginalMessages(db.sourceEvidence(chosen.id))).setNegativeButton("إلغاء",null).show();
        }).setNegativeButton("إلغاء",null).show();
    }
    private boolean isCreditPurchase(ExpenseDb.Transaction item){
        if(!"expense".equals(item.kind)||item.paymentSourceId==null)return false;
        ExpenseDb.PaymentSource source=db.paymentSource(item.paymentSourceId);
        try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",new String[]{Long.toString(item.id)})){
            return source!=null&&"بطاقة ائتمان".equals(source.kind)&&c.moveToFirst()&&c.isNull(0);
        }
    }
    private void cardPaymentDetails(LinearLayout details,ExpenseDb.Transaction item){
        CardSettlements store=new CardSettlements(db);
        if(store.isSettlement(item.id)){
            detailRow(details,"سداد البطاقة",label("تحويل بين حساباتك؛ ليس مصروفًا جديدًا",13,INK,true));
            TextView link=label("الموزّع "+money(store.allocatedBy(item.id),item.currency)+" · غير موزّع "+money(item.amountMinor-store.allocatedBy(item.id),item.currency)+" · التفاصيل / التراجع",13,ACCENT,true);
            link.setOnClickListener(v->cardSettlementResult(item.id));detailRow(details,"ربط المشتريات",link);
        }else if(isCreditPurchase(item)){
            detailRow(details,"سداد مرتبط بالمشتريات",label(money(store.allocated(item.id),item.currency)+" من "+money(store.cost(item.id),item.currency),13,INK,true));
            TextView fee=label("رسوم كشف البطاقة: "+money(db.transferFee(item.id),item.currency)+" · إضافة رسوم مؤكدة",13,ACCENT,true);
            fee.setOnClickListener(v->statementFeeDialog(item));detailRow(details,"رسوم البطاقة",fee);
        }
    }
    /** Uses the same bottom-sheet window, typography, surfaces and controls as the reimbursement journey. */
    private android.app.Dialog financialJourneySheet(String title,LinearLayout body,String primary,java.util.function.Consumer<android.app.Dialog> accept,String secondary,java.util.function.Consumer<android.app.Dialog> back){
        android.app.Dialog sheet=new android.app.Dialog(this);sheet.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout content=form();content.setPadding(dp(18),dp(10),dp(18),dp(12));content.setBackground(rounded(SURFACE,26));
        View handle=new View(this);handle.setBackground(rounded(BORDER,99));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(42),dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(12);content.addView(handle,hp);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);TextView headingText=label(title,20,INK,true);headingText.setTextDirection(View.TEXT_DIRECTION_RTL);headingText.setMaxLines(3);heading.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));heading.addView(iconButton(R.drawable.ic_close,"إغلاق",sheet::cancel),new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(heading);
        ScrollView scroller=scrollForm(body);scroller.setVerticalScrollBarEnabled(false);content.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        Button proceed=new Button(this);proceed.setTag("financial_journey_primary");proceed.setText(primary);proceed.setTypeface(BOLD);proceed.setTextColor(dark?0xff0d1f1a:Color.WHITE);proceed.setBackground(rounded(dark?ACCENT:BRAND,14));proceed.setAllCaps(false);proceed.setMinHeight(dp(52));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(12);content.addView(proceed,pp);proceed.setOnClickListener(v->accept.accept(sheet));
        if(secondary!=null){Button previous=new Button(this);previous.setText(secondary);previous.setTypeface(BOLD);previous.setTextColor(INK);previous.setBackground(outlined(SURFACE,BORDER,14));previous.setAllCaps(false);previous.setMinHeight(dp(48));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);content.addView(previous,bp);previous.setOnClickListener(v->back.accept(sheet));}
        showJourneyWindow(sheet,content,Math.round(getResources().getDisplayMetrics().heightPixels*0.80f));sheet.setCanceledOnTouchOutside(false);return sheet;
    }
    private void showJourneyWindow(android.app.Dialog sheet,LinearLayout content,int height){
        sheet.setContentView(content);sheet.setCanceledOnTouchOutside(true);android.view.Window window=sheet.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.35f);window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.setNavigationBarColor(SURFACE);window.getDecorView().setPadding(0,0,0,0);sheet.show();window.setLayout(-1,Math.min(height,Math.round(getResources().getDisplayMetrics().heightPixels*0.80f)));
    }
    private LinearLayout journeyAmountCard(LinearLayout parent,String title,String value,String caption){
        LinearLayout box=card(parent);box.setBackground(outlined(ACCENT_PALE,BORDER,18));box.addView(label(title,13,MUTED,false));TextView amount=label(value,28,INK,true);amount.setTextDirection(View.TEXT_DIRECTION_LTR);box.addView(amount);if(caption!=null)box.addView(label(caption,12,MUTED,false));return box;
    }
    private void repaymentSourceChoices(LinearLayout parent,String title,List<ExpenseDb.PaymentSource> sources,Long[] selected,int slot,Runnable changed){
        parent.addView(label(title,15,INK,true));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);parent.addView(list,new LinearLayout.LayoutParams(-1,-2));
        Runnable[] draw=new Runnable[1];draw[0]=()->{
            list.removeAllViews();
            for(ExpenseDb.PaymentSource source:sources){
                boolean chosen=selected[slot]!=null&&selected[slot]==source.id;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinimumHeight(dp(84));row.setBackground(outlined(chosen?ACCENT_PALE:BACKGROUND,chosen?ACCENT:BORDER,16));row.setSelected(chosen);
                row.addView(sourceBadge(source,42),new LinearLayout.LayoutParams(dp(42),dp(42)));
                LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(label(source.displayName==null||source.displayName.isBlank()?source.name:source.displayName,15,INK,true));copy.addView(label(source.kind,12,MUTED,false));
                TextView digits=label(source.last4==null?"الرقم غير مسجل":"•••• "+source.last4,13,INK,false);digits.setTextDirection(View.TEXT_DIRECTION_LTR);copy.addView(digits);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(12));row.addView(copy,cp);
                FrameLayout check=new FrameLayout(this);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);View circle=new View(this);circle.setBackground(chosen?rounded(ACCENT,99):outlined(SURFACE,ACCENT,99));check.addView(circle,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
                if(chosen){ImageView tick=tintedIcon(R.drawable.ic_check,Color.WHITE);tick.setPadding(dp(4),dp(4),dp(4),dp(4));check.addView(tick,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));}row.addView(check,new LinearLayout.LayoutParams(dp(38),dp(48)));
                row.setContentDescription("اختيار "+title+" · "+db.sourceLabel(source.id));row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(android.widget.RadioButton.class.getName());info.setCheckable(true);info.setChecked(chosen);}});
                row.setOnClickListener(v->{selected[slot]=source.id;draw[0].run();changed.run();});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);rp.bottomMargin=dp(8);list.addView(row,rp);
            }
            if(sources.isEmpty())list.addView(label("مفيش وسيلة متاحة. أضف الحساب أو بطاقة الائتمان من شاشة الحسابات أولًا.",13,MUTED,false));
        };draw[0].run();
    }
    private void cardRepaymentDialog(ExpenseDb.Transaction item){
        long fee=db.transferFee(item.id);if(fee>0){new ChoiceDialogBuilder().setTitle("السداد عليه رسوم تحويل").setMessage("الربط غير متاح حاليًا للتحويل المرتبط برسوم مقدارها "+money(fee,item.currency)+". أصل التحويل ورسومه محفوظين؛ راجعهم من تفاصيل العملية. لن نحول الرسوم لسداد أو نسجلها مرتين.").setPositiveButton("فهمت",null).show();return;}
        LinearLayout content=form();journeyAmountCard(content,"قيمة السداد",money(item.amountMinor,item.currency),"اختار حساب الخصم وبطاقة الائتمان الخاصة بك");
        content.addView(label("المشتريات تفضل محسوبة في يومها. تأكيد السداد يحوّل الخصم إلى تحويل بين حساباتك.",14,MUTED,false));
        List<ExpenseDb.PaymentSource> accounts=new ArrayList<>(),cards=new ArrayList<>();
        for(ExpenseDb.PaymentSource source:db.paymentSources())if(!source.archived){if("حساب".equals(source.kind)||"محفظة".equals(source.kind))accounts.add(source);if("بطاقة ائتمان".equals(source.kind))cards.add(source);}
        Long[] chosen={null,null};for(ExpenseDb.PaymentSource source:accounts)if(item.paymentSourceId!=null&&db.accountId(item.paymentSourceId)==source.id)chosen[0]=source.id;
        android.app.Dialog[] current={null};Runnable update=()->{if(current[0]!=null){Button next=current[0].getWindow().getDecorView().findViewWithTag("financial_journey_primary");boolean ready=chosen[0]!=null&&chosen[1]!=null;next.setEnabled(ready);next.setAlpha(ready?1f:0.45f);}};
        repaymentSourceChoices(content,"حساب الخصم",accounts,chosen,0,update);repaymentSourceChoices(content,"بطاقة الائتمان",cards,chosen,1,update);
        current[0]=financialJourneySheet("اختيار حسابات السداد",content,"اختيار المشتريات",sheet->{
            if(chosen[0]==null||chosen[1]==null){toast("حدد الحساب والبطاقة");return;}sheet.dismiss();cardPurchaseSelection(item,chosen[0],chosen[1]);
        },"إلغاء",android.app.Dialog::dismiss);update.run();
    }
    private void cardPurchaseSelection(ExpenseDb.Transaction item,long from,long card){cardPurchaseSelection(item,from,card,java.util.Collections.emptyMap());}
    private void cardPurchaseSelection(ExpenseDb.Transaction item,long from,long card,Map<Long,Long> previous){
        CardSettlements store=new CardSettlements(db);LinearLayout content=form();journeyAmountCard(content,"السداد المتاح للتوزيع",money(item.amountMinor,item.currency),"الربط اختياري؛ الباقي يفضل غير موزّع ولن نسجله رسومًا");
        content.addView(label("اختار المشتريات التي يغطيها هذا السداد وحدد نصيب كل عملية.",14,MUTED,false));
        long through=item.when;if("date_only".equals(item.timePrecision))through=Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()-1;
        List<ExpenseDb.Transaction> purchases=store.purchases(card,item.currency,through);List<CheckBox> checks=new ArrayList<>();List<EditText> amounts=new ArrayList<>();
        for(ExpenseDb.Transaction purchase:purchases){
            LinearLayout row=card(content);boolean selected=previous.containsKey(purchase.id);row.setBackground(outlined(selected?ACCENT_PALE:BACKGROUND,selected?ACCENT:BORDER,16));
            CheckBox check=new CheckBox(this);check.setText(UiKit.displayName(purchase.merchant));check.setTypeface(BOLD);check.setTextColor(INK);check.setMinHeight(dp(48));check.setChecked(selected);row.addView(check);checks.add(check);
            LocalDate day=Instant.ofEpochMilli(purchase.when).atZone(ZoneId.systemDefault()).toLocalDate();row.addView(label(purchase.category+" · "+dayLabel(day)+" · "+transactionTimeLabel(purchase,false),12,MUTED,false));
            row.addView(label("المتبقي من الشراء ورسومه: "+money(store.cost(purchase.id)-store.allocated(purchase.id),purchase.currency),14,INK,true));
            EditText amount=input("نصيب العملية من السداد",row);amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);amount.setText(String.format(Locale.ROOT,"%.2f",previous.getOrDefault(purchase.id,store.cost(purchase.id)-store.allocated(purchase.id))/100.0));amount.setEnabled(selected);amounts.add(amount);
            check.setOnCheckedChangeListener((button,chosen)->{amount.setEnabled(chosen);row.setBackground(outlined(chosen?ACCENT_PALE:BACKGROUND,chosen?ACCENT:BORDER,16));});
        }
        if(purchases.isEmpty())content.addView(label("لا توجد مشتريات مفتوحة بنفس العملة على هذه البطاقة قبل السداد. يمكنك اعتماد السداد كله غير موزّع.",13,MUTED,false));
        financialJourneySheet("١ من ٣ · ربط المشتريات بالسداد",content,"مراجعة",sheet->{try{
            Map<Long,Long> selected=new LinkedHashMap<>();for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked())selected.put(purchases.get(i).id,optionalMinor(amounts.get(i).getText().toString()));
            CardSettlements.Preview preview=store.preview(item.id,from,card,selected);sheet.dismiss();confirmCardRepayment(preview);
        }catch(IllegalArgumentException|ArithmeticException error){toast("راجع الحساب والبطاقة ومبالغ المشتريات؛ قد تكون البيانات اتغيرت");}},"إلغاء",android.app.Dialog::dismiss);
    }
    private void confirmCardRepayment(CardSettlements.Preview preview){
        LinearLayout content=form();String code=preview.payment.currency;journeyAmountCard(content,"قيمة السداد",money(preview.payment.amountMinor,code),"تحويل بين حساباتك؛ المشتريات لا تتسجل مرة ثانية");
        LinearLayout accounts=card(content);accounts.addView(label("من حساب",12,MUTED,false));accounts.addView(label(db.sourceLabel(preview.from),14,INK,true),new LinearLayout.LayoutParams(-1,-2));divider(accounts);accounts.addView(label("إلى بطاقة",12,MUTED,false));accounts.addView(label(db.sourceLabel(preview.card),14,INK,true),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout totals=card(content);detailRow(totals,"الموزّع",label(money(preview.allocated,code),16,INK,true));detailRow(totals,"غير موزّع",label(money(preview.remainder,code),16,INK,true));
        if(preview.remainder>0)totals.addView(label("الجزء ده سداد على البطاقة من غير ربط بمشتريات؛ مش رسوم أو مصروف جديد.",13,MUTED,false));
        if(!preview.allocations.isEmpty())content.addView(label("المشتريات المختارة",15,INK,true));
        for(Map.Entry<Long,Long> allocation:preview.allocations.entrySet()){
            ExpenseDb.Transaction purchase=db.transactionById(allocation.getKey());LinearLayout row=card(content);row.addView(label(purchase==null?"عملية تغيرت":UiKit.displayName(purchase.merchant),16,INK,true));
            row.addView(label(money(allocation.getValue(),code),20,INK,true));if(purchase!=null)row.addView(label(purchase.category+" · "+dayLabel(Instant.ofEpochMilli(purchase.when).atZone(ZoneId.systemDefault()).toLocalDate()),12,MUTED,false));
        }
        content.addView(label("حركة البنك الأصلية محفوظة. لسه محفظناش الربط؛ راجع كشف البنك قبل التأكيد.",13,MUTED,false));
        android.app.Dialog review=financialJourneySheet("٢ من ٣ · مراجعة سداد البطاقة",content,"تأكيد السداد",sheet->{
            try{new CardSettlements(db).confirm(preview);sheet.dismiss();selectedTransaction=db.transactionById(preview.payment.id);render();cardSettlementResult(preview.payment.id);}
            catch(IllegalArgumentException|ArithmeticException error){sheet.dismiss();toast("البيانات اتغيرت؛ راجع الاختيارات من جديد");cardPurchaseSelection(preview.payment,preview.from,preview.card,preview.allocations);}
        },"رجوع للاختيار",sheet->{sheet.dismiss();cardPurchaseSelection(preview.payment,preview.from,preview.card,preview.allocations);});
        review.setOnCancelListener(d->cardPurchaseSelection(preview.payment,preview.from,preview.card,preview.allocations));
    }
    private void cardSettlementResult(long id){
        CardSettlements store=new CardSettlements(db);ExpenseDb.Transaction item=db.transactionById(id);if(item==null||!store.isSettlement(id)){toast("السداد لم يعد متاحًا؛ راجع سجل الإلغاء");return;}
        final android.app.Dialog[] resultSheet={null};
        LinearLayout content=form();journeyAmountCard(content,"تم تأكيد سداد البطاقة",money(item.amountMinor,item.currency),"تحويل بين حساباتك؛ مش مصروف جديد");
        LinearLayout totals=card(content);detailRow(totals,"الموزّع",label(money(store.allocatedBy(id),item.currency),16,INK,true));detailRow(totals,"غير موزّع",label(money(item.amountMinor-store.allocatedBy(id),item.currency),16,INK,true));
        try{org.json.JSONArray allocations=store.history(id).getJSONArray("allocations");for(int i=0;i<allocations.length();i++){org.json.JSONObject row=allocations.getJSONObject(i);ExpenseDb.Transaction purchase=db.transactionById(row.getLong("purchase_id"));TextView link=label((purchase==null?"عملية محذوفة":UiKit.displayName(purchase.merchant))+" · "+money(row.getLong("amount_minor"),item.currency),14,ACCENT,true);LinearLayout box=card(content);box.addView(link);if(purchase!=null)link.setOnClickListener(v->{if(resultSheet[0]!=null)resultSheet[0].dismiss();openRelatedTransaction(purchase);});}}catch(org.json.JSONException error){throw new IllegalStateException(error);}
        resultSheet[0]=financialJourneySheet("٣ من ٣ · تفاصيل سداد البطاقة",content,"تم",android.app.Dialog::dismiss,"التراجع عن الربط",sheet->new ChoiceDialogBuilder().setTitle("التراجع عن سداد البطاقة؟").setMessage("هنرجّع تصنيف الخصم الأصلي ونفك توزيعه. المشتريات ورسومها محفوظة.")
                .setPositiveButton("تراجع",(a,b)->{if(store.undo(id)){sheet.dismiss();selectedTransaction=db.transactionById(id);render();toast("اتلغى الربط ورجع الخصم لتصنيفه الأصلي");}else toast("السداد اتغير؛ راجع العملية");}).setNegativeButton("إلغاء",null).show());
    }
    private void statementFeeDialog(ExpenseDb.Transaction purchase){
        LinearLayout content=form();content.addView(label("أدخل فقط رسومًا مؤكدة من كشف البطاقة وبنفس عملة الشراء. هتتسجل كمصروف مرتبط بيوم الشراء وتصنيفه؛ مش رسوم على مبلغ السداد. لا تستخدم فرق السداد كتقدير.",14,MUTED,false));
        EditText amount=input("قيمة الرسوم · "+purchase.currency,content),description=input("وصف الرسوم كما هو في كشف البنك",content);amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        financialJourneySheet("رسوم كشف البطاقة",content,"مراجعة الرسوم",sheet->{try{
            long minor=optionalMinor(amount.getText().toString());String note=description.getText().toString().trim();if(minor<=0||note.isEmpty()){toast("اكتب المبلغ ووصف كشف البنك");return;}
            LinearLayout review=form();journeyAmountCard(review,"رسوم مؤكدة من كشف البطاقة",money(minor,purchase.currency),UiKit.displayName(purchase.merchant));review.addView(label(note,14,INK,false));review.addView(label("هتظهر في يوم الشراء وتصنيفه. راجع التاريخ والعملة في الكشف.",13,MUTED,false));
            financialJourneySheet("تأكيد رسوم البطاقة",review,"تأكيد الرسوم",confirmation->{try{new CardSettlements(db).addStatementFee(purchase.id,minor,note);confirmation.dismiss();sheet.dismiss();selectedTransaction=db.transactionById(purchase.id);render();toast("اتسجلت الرسوم بالمصدر اليدوي ووصف الكشف");}catch(IllegalArgumentException error){toast("راجع الرسوم الموجودة أو فك توزيع السداد أولًا");}},"رجوع",android.app.Dialog::dismiss);
        }catch(IllegalArgumentException error){toast("راجع مبلغ الرسوم");}},"إلغاء",android.app.Dialog::dismiss);
    }

    /**
     * «الرسالة اتقرت غلط؟»: a support draft for a row the parser read wrongly (sign, amount, merchant or category).
     * The original message is redacted (Redaction.text) and shown editable beside what the app recorded; the user
     * reviews the text and sends the e-mail themselves. Nothing leaves the device otherwise (same policy as the
     * unrecognised-format draft in the import preview).
     */
    private void reportTransactionReading(ExpenseDb.Transaction item) {
        List<ExpenseDb.SourceEvidence> messages = db.sourceEvidence(item.id);
        String recorded = ("income".equals(item.kind) ? "دخل" : "transfer".equals(item.kind) ? "تحويل بين حساباتي" : "مصروف") + " · " + money(item.amountMinor, item.currency)
                + " · " + UiKit.displayName(item.merchant) + " · " + item.category + " · " + transactionTimeLabel(item, true);
        LinearLayout form = form();
        form.addView(label("التطبيق سجّلها كده:", 12, MUTED, true));
        TextView summary = label(recorded, 13, INK, false); summary.setPadding(0, dp(2), 0, dp(10)); form.addView(summary);
        form.addView(label("حجبنا الأرقام والمبالغ وبعض الأسماء من الرسالة تلقائيًا. راجع النص واكتب تحتها الصح، وهيفتح مسودة إيميل وإنت اللي ترسلها.", 12, MUTED, false));
        EditText body = new EditText(this); body.setTypeface(REGULAR); body.setMinLines(3); body.setGravity(Gravity.TOP | Gravity.START);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        StringBuilder redacted = new StringBuilder();
        for (ExpenseDb.SourceEvidence evidence : messages) {
            if (evidence.body == null || evidence.failed) continue;
            if (redacted.length() > 0) redacted.append("\n\n");
            redacted.append("المرسل: ").append(evidence.sender).append("\n").append(Redaction.text(evidence.body));
        }
        body.setText(redacted.length() == 0 ? "(الرسالة الأصلية غير متاحة؛ اكتب صيغتها هنا لو فاكرها)" : redacted.toString());
        form.addView(body);
        EditText correct = new EditText(this); correct.setTypeface(REGULAR); correct.setHint("الصح كان إيه؟ (مثلًا: دي إيداع مش خصم)");
        correct.setMinLines(2); correct.setGravity(Gravity.TOP | Gravity.START);
        correct.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(correct);
        ScrollView preview = new ScrollView(this); preview.addView(form);
        new ChoiceDialogBuilder().setTitle("الرسالة اتقرت غلط؟").setView(preview)
                .setPositiveButton("فتح مسودة إيميل", (dialog, which) -> {
                    String reviewed = "قراءة غلط لرسالة بنك\nالتطبيق سجّلها: " + recorded + "\nالصح: " + correct.getText().toString().trim() + "\n\n" + body.getText().toString();
                    try { startActivity(EmailSupport.draft(this, false, reviewed)); }
                    catch (android.content.ActivityNotFoundException unavailable) { copySupportRequest(false, reviewed, "مفيش تطبيق إيميل؛ اتنسخت المسودة وعنوان الدعم"); }
                })
                .setNegativeButton("إلغاء", null).show();
    }

    private void showOriginalMessages(List<ExpenseDb.SourceEvidence> messages) {
        LinearLayout content=form();
        if(messages.isEmpty()) content.addView(label("مصدر نصي غير متاح لهذه العملية. الإشعارات القديمة تحتاج إعادة وصول؛ والإدخال اليدوي وCSV ليس لهما رسالة أصلية.",14,MUTED,false));
        for(ExpenseDb.SourceEvidence evidence:messages) {
            LinearLayout message=card(content);message.setBackground(outlined(BACKGROUND,BORDER,16));
            LinearLayout sender=new LinearLayout(this);sender.setGravity(Gravity.CENTER_VERTICAL);
            sender.addView(tintedIcon(R.drawable.ic_message,ACCENT),new LinearLayout.LayoutParams(dp(22),dp(22)));
            TextView name=label(evidence.source+" · "+evidence.sender,14,INK,true);name.setPadding(dp(10),0,dp(10),0);
            sender.addView(name,new LinearLayout.LayoutParams(0,-2,1));message.addView(sender);
            message.addView(label(Instant.ofEpochMilli(evidence.receivedAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a",new Locale("ar"))),12,MUTED,false));
            TextView body=label(originalMessageText(evidence),14,INK,false);
            body.setTextIsSelectable(true);body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            body.setPadding(0,dp(12),0,dp(8));message.addView(body);
        }
        financialJourneySheet("الرسالة الأصلية",content,"إغلاق",android.app.Dialog::dismiss,null,null);
    }

    private void transactionOptions(ExpenseDb.Transaction item) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if(new CardSettlements(db).isSettlement(item.id)){
            labels.add("تفاصيل سداد البطاقة / التراجع");actions.add(()->cardSettlementResult(item.id));
        }else if(!"income".equals(item.kind)&&("transfer".equals(item.kind)||"تحويلات".equals(item.category))){
            labels.add("ده سداد لبطاقة ائتمان");actions.add(()->cardRepaymentDialog(item));
        }
        if(isCreditPurchase(item)){labels.add("إضافة رسوم مؤكدة من كشف البطاقة");actions.add(()->statementFeeDialog(item));}
        labels.add("تعديل العملية"); actions.add(() -> editTransactionDialog(item));
        if("SMS".equals(item.source)||item.source.startsWith("إشعار")){labels.add("الرسالة اتقرت غلط؟ بلّغ عنها");actions.add(()->reportTransactionReading(item));}
        if("expense".equals(item.kind)){labels.add("حوّلها لالتزام شهري");actions.add(()->commitmentFromTransaction(item));}
        if("expense".equals(item.kind)){labels.add("راجع نسخة SMS مختصرة مكررة");actions.add(()->reviewAbbreviatedSms(item));}
        if(!"income".equals(item.kind) && ("تحويلات".equals(item.category) || "transfer".equals(item.kind))) {
            labels.add("مطابقة التحويل وفصل الرسوم");actions.add(() -> reconcileTransferDialog(item));
        }
        boolean handle = item.merchant.contains("@");
        if (!"transfer".equals(item.kind)) {
            if (handle) {
                labels.add("ده حساب تاني ليا");
                actions.add(() -> new ChoiceDialogBuilder().setTitle("حساب ليك؟")
                        .setMessage(item.merchant + "\n\nكل التحويلات من وإلى الحساب ده هتتسجل تحويل بين حساباتك، ومش هتتحسب صرف أو دخل.")
                        .setPositiveButton("أيوه، حسابي", (dialog, which) -> {
                            db.addOwnHandle(item.merchant);
                            selectedTransaction = db.transactionById(item.id);
                            render();
                        }).setNegativeButton("إلغاء", null).show());
            }
            labels.add("ده تحويل بين حساباتي");
            actions.add(() -> {
                db.markAsTransfer(item.id);
                selectedTransaction = db.transactionById(item.id);
                toast("اتسجلت تحويل، ومش هتتحسب في الصرف");
                render();
            });
        }
        if ("income".equals(item.kind)) {
            if (db.reimbursedExpense(item.id) == null) { labels.add("دي فلوس رجعتلي عن مشتريات"); actions.add(() -> reimbursementDialog(item)); }
            else {
                labels.add(db.ignoredReimbursement(item.id) != null ? "فك ربط استرداد غير محتسب" : "فك ربط الاسترداد");
                actions.add(() -> unlinkReimbursementDialog(item));
            }
        }
        if ("expense".equals(item.kind) && db.reimbursedOf(item.id) > 0) {
            labels.add("فك ربط الاسترداد"); actions.add(() -> unlinkReimbursementDialog(item));
        }
        labels.add("حذف العملية"); actions.add(() -> confirmDeleteTransaction(item));
        new ChoiceDialogBuilder().setItems(labels.toArray(new String[0]),
                (dialog, which) -> actions.get(which).run()).show();
    }

    private void unlinkReimbursementDialog(ExpenseDb.Transaction item) {
        boolean ignored = "expense".equals(item.kind)
                ? MoneyFlow.isCashWithdrawal(item.kind, item.category) && !db.reimbursingIncomes(item.id).isEmpty()
                : db.ignoredReimbursement(item.id) != null;
        new ChoiceDialogBuilder()
                .setTitle(ignored ? "فك ربط استرداد غير محتسب؟" : "فك ربط الفلوس الراجعة؟")
                .setMessage(ignored
                        ? "الرابط القديم محفوظ للمراجعة فقط: السحب ظاهر بمبلغه الكامل والوارد بحسابه الكامل. فكه هيمسح الربط فقط؛ حركات البنك الأصلية محفوظة."
                        : "الشراء هيتحسب على حسابك من جديد، والفلوس الراجعة هتظهر كفلوس دخلت. حركات البنك الأصلية محفوظة."+
                        ("expense".equals(item.kind)?"\nده هيفك كل الاستردادات المرتبطة بالشراء ده.":""))
                .setPositiveButton("فك الربط",(d,w)->{db.unlinkReimbursement(item.id);selectedTransaction=db.transactionById(item.id);render();toast("اتفك الربط واتحدث حساب يوم الشراء");})
                .setNegativeButton("إلغاء",null).show();
    }

    /** Choose, review, confirm and finish; choosing a row alone never writes a link. */
    private void reimbursementDialog(ExpenseDb.Transaction income) {
        reimbursementPicker(income,false,"");
    }

    private void reimbursementPicker(ExpenseDb.Transaction income,boolean older,String query) {
        reimbursementPicker(income,older,query,-1);
    }

    private String reimbursementShare(ExpenseDb.Transaction income) {
        if(!"income".equals(income.kind))return null;
        Map<Long,Long> offsets=db.reimbursementOffsets();
        if(ExpenseDb.effectiveAmount(income,offsets)>=income.amountMinor)return null;
        ExpenseDb.Transaction purchase=db.reimbursedExpense(income.id);
        return purchase==null?null:"نصيبك في "+UiKit.displayName(purchase.merchant)+": "+money(ExpenseDb.effectiveAmount(purchase,offsets),purchase.currency);
    }

    private void reimbursementPicker(ExpenseDb.Transaction income,boolean older,String query,long previousSelection) {
        ExpenseDb.Transaction fresh=db.transactionById(income.id);
        if(fresh==null || !"income".equals(fresh.kind) || db.reimbursedExpense(income.id)!=null) {
            toast("التحويل اتغير؛ افتح تفاصيله وراجعه من جديد");return;
        }
        android.app.Dialog picker=new android.app.Dialog(this);
        picker.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout content=form();content.setPadding(dp(18),dp(10),dp(18),dp(12));content.setBackground(rounded(SURFACE,26));
        View handle=new View(this);handle.setBackground(rounded(BORDER,99));
        LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(42),dp(4));handleParams.gravity=Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin=dp(12);content.addView(handle,handleParams);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("رجعتلك عن أنهي عملية؟",20,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(iconButton(R.drawable.ic_close,"إغلاق اختيار الشراء",picker::dismiss),new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(heading);
        content.addView(label("تحويل وارد: "+money(fresh.amountMinor,fresh.currency)+" · اختار الشراء",13,MUTED,false));
        LinearLayout scope=new LinearLayout(this);scope.setGravity(Gravity.CENTER_VERTICAL);
        addChip(scope,"مشتريات يوم التحويل",!older,()->{if(older){picker.dismiss();reimbursementPicker(fresh,false,"");}});
        addChip(scope,"عن عملية أقدم",older,()->{if(!older){picker.dismiss();reimbursementPicker(fresh,true,"");}});content.addView(scope);
        EditText search=input("ابحث باسم الشراء أو المبلغ",content);search.setText(query);search.setSingleLine(true);
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.setVerticalScrollBarEnabled(false);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroller.addView(list);
        content.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        List<ExpenseDb.Transaction> candidates=db.reimbursementCandidates(fresh,older);
        long[] selection={previousSelection};
        Button review=new Button(this);review.setText("راجع الربط");review.setTypeface(BOLD);review.setTextColor(onPrimary());
        review.setBackground(rounded(primaryFill(),14));review.setAllCaps(false);review.setMinHeight(dp(50));
        LinearLayout.LayoutParams reviewParams=new LinearLayout.LayoutParams(-1,dp(52));reviewParams.topMargin=dp(10);content.addView(review,reviewParams);
        Runnable[] draw=new Runnable[1];draw[0]=()->{
            list.removeAllViews();String text=search.getText().toString().trim().toLowerCase(Locale.ROOT);int found=0;
            boolean selectedVisible=false;
            for(ExpenseDb.Transaction tx:candidates) {
                if(!text.isEmpty() && !tx.merchant.toLowerCase(Locale.ROOT).contains(text) &&
                        !money(tx.amountMinor,tx.currency).toLowerCase(Locale.ROOT).contains(text))continue;
                boolean chosen=selection[0]==tx.id;if(chosen)selectedVisible=true;
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));row.setMinimumHeight(dp(80));
                row.setBackground(outlined(chosen?ACCENT_PALE:BACKGROUND,chosen?ACCENT:BACKGROUND,16));row.setSelected(chosen);
                LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
                TextView name=label(UiKit.displayName(tx.merchant),15,INK,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(name);
                LocalDate day=Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate();
                String time=transactionTimeLabel(tx,false);
                copy.addView(label(tx.category+" · "+dayLabel(day)+" · "+time,11,MUTED,false));
                row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
                TextView amount=label("−"+money(tx.amountMinor,tx.currency),14,INK,true);amount.setTextDirection(View.TEXT_DIRECTION_LTR);amount.setMaxWidth(dp(145));
                LinearLayout.LayoutParams amountParams=new LinearLayout.LayoutParams(-2,-2);amountParams.setMarginStart(dp(10));row.addView(amount,amountParams);
                FrameLayout check=new FrameLayout(this);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                View circle=new View(this);circle.setBackground(chosen?rounded(ACCENT,99):outlined(SURFACE,ACCENT,99));
                check.addView(circle,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
                if(chosen){ImageView tick=tintedIcon(R.drawable.ic_check,onPrimary());tick.setPadding(dp(4),dp(4),dp(4),dp(4));
                    check.addView(tick,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));}
                row.addView(check,new LinearLayout.LayoutParams(dp(38),dp(48)));
                row.setContentDescription("اختيار "+UiKit.displayName(tx.merchant)+"، "+money(tx.amountMinor,tx.currency)+"، "+dayLabel(day));
                row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){
                    super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(android.widget.RadioButton.class.getName());info.setCheckable(true);info.setChecked(chosen);
                }});
                row.setOnClickListener(v->{selection[0]=tx.id;draw[0].run();});
                LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(8);list.addView(row,rowParams);found++;
            }
            if(!selectedVisible)selection[0]=-1;
            review.setEnabled(selection[0]>=0);review.setAlpha(selection[0]>=0?1f:0.45f);
            if(found==0)list.addView(label(text.isEmpty()?(older?"مفيش مشتريات أقدم مؤهلة للربط.":
                    "مفيش مشتريات مؤهلة في يوم التحويل. لو رجعت عن شراء سابق، اختار «عن عملية أقدم»."):
                    "مفيش نتيجة للبحث. جرّب اسم تاني أو امسح البحث.",14,MUTED,false));
        };
        review.setOnClickListener(v->{
            for(ExpenseDb.Transaction tx:candidates)if(tx.id==selection[0]) {
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
                picker.dismiss();reimbursementPreview(fresh,tx,older,search.getText().toString());return;
            }
        });
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){draw[0].run();}
            @Override public void afterTextChanged(Editable s){}
        });draw[0].run();int desiredHeight=dp(300+90*Math.min(4,Math.max(1,candidates.size())));
        showJourneyWindow(picker,content,desiredHeight);
    }

    private String reimbursementEffect(ExpenseDb.ReimbursementPreview preview) {
        String code=preview.expense.currency;
        String text="يوم الشراء: "+dayLabel(preview.day)+"\nصافي صرف اليوم ده بعد الربط: "+money(preview.daySpending,code);
        DailyAllowance.Result result=DailyAllowance.calculate(DailySummary.plan(this,code),preview.day,preview.daySpending,0);
        if(result.valid)text+="\n"+(result.remaining<0?"تجاوز الحد: "+money(-result.remaining,code):"المتبقي من الحد: "+money(result.remaining,code));
        if(!preview.day.equals(LocalDate.now()))text+="\nالاسترداد يخص يوم الشراء؛ مش بيقلل صرف النهارده.";
        if(MoneyFlow.bucket(preview.expense.kind,preview.expense.category)!=MoneyFlow.Bucket.LIVING)
            text+="\nالعملية دي مش ضمن الصرف اليومي؛ مش داخلة في الحد اليومي.";
        return text;
    }

    private void reimbursementPreview(ExpenseDb.Transaction income,ExpenseDb.Transaction expense,boolean older,String query) {
        final ExpenseDb.ReimbursementPreview preview;
        try{preview=db.previewReimbursement(income.id,expense.id);}
        catch(IllegalArgumentException changed){toast("الشراء أو التحويل اتغير؛ اختار من القائمة المحدثة");reimbursementPicker(income,older,query);return;}
        LinearLayout content=form();String code=preview.expense.currency;
        content.addView(label(UiKit.displayName(preview.expense.merchant),18,INK,true));
        content.addView(label("دفعت: "+money(preview.expense.amountMinor,code)+"\nهنربط من الفلوس الراجعة: "+money(preview.applied,code)+
                "\nنصيبك بعد الربط: "+money(preview.personal,code),15,INK,false));
        long extra=preview.income.amountMinor-preview.applied;
        if(extra>0)content.addView(label("الباقي من التحويل "+money(extra,code)+" هيظل فلوس دخلت، مش استرداد للشراء ده.",13,MUTED,false));
        content.addView(label(reimbursementEffect(preview),14,ACCENT,true));
        content.addView(label("حركة البنك الأصلية محفوظة. لسه محفظناش الربط.",12,MUTED,false));
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("٢ من ٣ · راجع النتيجة")
                .setView(scrollForm(content)).setPositiveButton("تأكيد الربط",null)
                .setNegativeButton("رجوع للاختيار",(d,w)->reimbursementPicker(income,older,query,expense.id)).create();
        dialog.setOnCancelListener(d->reimbursementPicker(income,older,query,expense.id));
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{db.confirmReimbursement(preview);}
            catch(IllegalArgumentException changed){dialog.dismiss();toast("البيانات اتغيرت؛ راجع المعاينة الجديدة قبل التأكيد");reimbursementPicker(income,older,query);return;}
            dialog.dismiss();selectedTransaction=db.transactionById(income.id);render();
            new ChoiceDialogBuilder().setTitle("٣ من ٣ · تم ربط الفلوس الراجعة")
                    .setMessage("نصيبك من "+UiKit.displayName(preview.expense.merchant)+": "+money(preview.personal,code)+"\n\n"+reimbursementEffect(preview))
                    .setPositiveButton("تم",null)
                    .setNeutralButton("عرض الشراء",(done,w)->{ExpenseDb.Transaction purchase=db.transactionById(expense.id);if(purchase!=null)openRelatedTransaction(purchase);})
                    .setNegativeButton("تراجع عن الربط",(done,w)->{
                        boolean undone=db.undoReimbursement(income.id,expense.id,preview.applied);
                        selectedTransaction=db.transactionById(income.id);render();toast(undone?"اتلغى الربط ورجع الحساب زي ما كان":"الربط اتغير؛ راجعه من تفاصيل العملية");
                    }).show();
        }));dialog.show();
    }

    private void reviewAbbreviatedSms(ExpenseDb.Transaction item) {
        List<ExpenseDb.Transaction> copies=db.abbreviatedSmsCopies(item.id);
        if(copies.isEmpty()){toast("مفيش نسخة مختصرة مطابقة لمصدر العملية دي");return;}
        String[] choices=new String[copies.size()];for(int i=0;i<copies.size();i++)choices[i]=copies.get(i).merchant+" · "+money(copies.get(i).amountMinor,copies.get(i).currency);
        new ChoiceDialogBuilder().setTitle("راجع النسخة المختصرة").setItems(choices,(choose,index)->{
            ExpenseDb.Transaction copy=copies.get(index);
            new ChoiceDialogBuilder().setTitle("نفس الشراء في الرسالتين؟")
                    .setMessage(copy.merchant+" ← "+item.merchant+"\n"+money(item.amountMinor,item.currency)+"\n\nنص الرسالة القصيرة بداية حرفية من الكاملة، ونفس البطاقة والعملة والمبلغ. الدمج يحتفظ بالمصدرين وبأرشيف الصف المختصر، ويحسب مصروفًا واحدًا. لو عمليتان منفصلتان اختار إلغاء.")
                    .setPositiveButton("دمج نفس العملية",(confirm,which)->{try{db.confirmAbbreviatedSmsCopy(item.id,copy.id);selectedTransaction=db.transactionById(item.id);render();toast("اتحسبت مرة واحدة والمصدران محفوظان");}catch(RuntimeException changed){toast("البيانات اتغيرت؛ راجع المصدر من جديد");}})
                    .setNegativeButton("إلغاء",null).show();
        }).show();
    }

    private void reconcileTransferDialog(ExpenseDb.Transaction item) {
        LinearLayout content=form();
        content.addView(label("أصل التحويل + الرسوم لازم يساوي الخصم. اختار فقط رسائل استلام نفس التحويل؛ راجع المصدر الأصلي عند الشك.",13,MUTED,false));
        EditText principal=input("أصل المبلغ المحوّل",content);
        principal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        principal.setText(String.format(Locale.ROOT,"%.2f",item.amountMinor/100.0));
        EditText fee=input("رسوم البنك",content);
        fee.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);fee.setText(String.format(Locale.ROOT,"%.2f",db.transferFee(item.id)/100.0));
        List<ExpenseDb.PaymentSource> accounts=new ArrayList<>();
        for(ExpenseDb.PaymentSource source:db.accountSources(false))
            if("حساب".equals(source.kind)||"محفظة".equals(source.kind))accounts.add(source);
        Spinner from=spinner("من حساب",sourceNames(accounts),content),to=spinner("إلى حسابك",sourceNames(accounts),content);
        for(int i=0;i<accounts.size();i++) {
            if(item.paymentSourceId!=null && accounts.get(i).id==db.accountId(item.paymentSourceId)) from.setSelection(i+1);
            if(item.destinationSourceId!=null && accounts.get(i).id==db.accountId(item.destinationSourceId)) to.setSelection(i+1);
        }
        List<ExpenseDb.Transaction> candidates=new ArrayList<>();List<android.widget.CheckBox> checks=new ArrayList<>();
        LocalDate date=Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate();
        for(ExpenseDb.Transaction receipt:db.transactionsBetween(date,date.plusDays(1))) {
            if(receipt.id==item.id || "expense".equals(receipt.kind) || !item.currency.equals(receipt.currency) || receipt.amountMinor>item.amountMinor || Math.abs(receipt.when-item.when)>60L*60*1000) continue;
            android.widget.CheckBox check=new android.widget.CheckBox(this);
            Long receivedOn=receipt.destinationSourceId!=null?receipt.destinationSourceId:receipt.paymentSourceId;
            check.setText(receipt.merchant+" · "+money(receipt.amountMinor,receipt.currency)+"\n"+
                    (receivedOn==null?"حساب الاستلام غير محدد — راجع الرسالة الأصلية":db.sourceLabel(db.accountId(receivedOn)))+
                    "\n"+sourceDate(receipt.when)+" · "+receipt.source);
            check.setTextColor(INK);content.addView(check);candidates.add(receipt);checks.add(check);
        }
        TextView feeFailure=label("",13,CORAL,false);content.addView(feeFailure);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("مطابقة تحويل بين حساباتك").setView(scroll)
                .setPositiveButton("اربط المصادر وافصل الرسوم",null).setNeutralButton("اربط بدون رسوم",null).setNegativeButton("إلغاء",null).create();
        java.util.function.Consumer<Boolean> submit=omitFee -> {
            try {
                Long source=selectedSource(from,accounts),destination=selectedSource(to,accounts);
                if(source==null || destination==null) {toast("حدد حسابي التحويل");return;}
                List<Long> receipts=new ArrayList<>();for(int i=0;i<checks.size();i++) if(checks.get(i).isChecked()) receipts.add(candidates.get(i).id);
                if(rejectMisreadAmount(principal)||rejectMisreadAmount(fee))return;
                long amount=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(principal.getText().toString()));
                long bankFee=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(fee.getText().toString()));
                Runnable apply=()->{
                    try {
                        if(omitFee)db.reconcileOwnTransferWithoutFee(item.id,amount,bankFee,source,destination,receipts);
                        else db.reconcileOwnTransfer(item.id,amount,bankFee,source,destination,receipts);
                        dialog.dismiss();selectedTransaction=db.transactionById(item.id);render();
                    } catch(ExpenseDb.TransferFeeUnavailable error) {
                        feeFailure.setText(error.mayOmit()?"الرسوم دي محذوفة أو عليها تجاهل، فمش هنقدر نسجلها. تقدر تربط بدون رسوم بعد مراجعة المبلغ المستبعد.":"الرسوم دي مسجلة قبل كده؛ راجع العملية الأصلية قبل الربط.");
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(error.mayOmit()?View.VISIBLE:View.GONE);
                        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
                    } catch(IllegalArgumentException error) {toast("راجع أصل التحويل والرسوم ورسائل الاستلام المختارة");}
                };
                if(omitFee)new ChoiceDialogBuilder().setTitle("تأكيد الربط بدون رسوم")
                        .setMessage("هتربط أصل التحويل "+money(amount,item.currency)+". الـ "+money(bankFee,item.currency)+" رسوم مش هتتحسب في مصروفاتك. القرار هيتسجل في ملاحظة العملية.")
                        .setPositiveButton("تأكيد الربط بدون رسوم",(d,w)->apply.run()).setNegativeButton("إلغاء",null).show();
                else apply.run();
            } catch(IllegalArgumentException error) {toast("راجع أصل التحويل والرسوم ورسائل الاستلام المختارة");}
        };
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(View.GONE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->submit.accept(false));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->submit.accept(true));
        });dialog.show();
    }

    private void ownHandlesDialog() {
        List<String> handles = db.ownHandles();
        LinearLayout form = form();
        form.addView(label(handles.isEmpty() ? "لسه مفيش حسابات مؤكدة. ضيف حسابك هنا علشان تحويلاتك لنفسك ما تتحسبش صرف." :
                "التحويلات من وإلى الحسابات دي مش بتتحسب صرف ولا دخل. اختار حساب لو عايز تشيله.", 13, MUTED, false));
        EditText add = input("مثال: name@instapay", form);
        String[] items = handles.toArray(new String[0]);
        boolean[] remove = new boolean[items.length];
        AlertDialog.Builder builder = new ChoiceDialogBuilder().setTitle("حساباتك على إنستاباي").setView(form);
        if (items.length > 0) builder.setMultiChoiceItems(items, remove, (dialog, which, checked) -> remove[which] = checked);
        builder.setPositiveButton("حفظ", (dialog, which) -> {
            for (int i = 0; i < items.length; i++) if (remove[i]) db.removeOwnHandle(items[i]);
            String value = add.getText().toString().trim();
            if (value.contains("@")) db.addOwnHandle(value);
            render();
        }).setNegativeButton("إلغاء", null).show();
    }

    private LinearLayout cardStrip(ExpenseDb.PaymentSource source) {
        UiKit.CardFace face = UiKit.cardFace(source.name);
        LinearLayout strip = new LinearLayout(this); strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{face.start, face.end});
        background.setCornerRadius(dp(18));
        strip.setBackground(background);
        ImageView chip = new ImageView(this); chip.setImageResource(R.drawable.ic_card_chip);
        strip.addView(chip, new LinearLayout.LayoutParams(dp(34), dp(25)));
        LinearLayout name = new LinearLayout(this); name.setOrientation(LinearLayout.VERTICAL);
        name.addView(label(face.name, 14, Color.WHITE, true));
        TextView digits = label(source.last4 == null ? source.kind : "•••• " + source.last4, 12, 0xccffffff, false);
        digits.setTextDirection(View.TEXT_DIRECTION_LTR); name.addView(digits);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1); nameParams.setMarginStart(dp(12));
        strip.addView(name, nameParams);
        ExpenseDb.BalanceSnapshot balance = db.currentBalance(source.id, "EGP");
        if (balance != null) {
            LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.END);
            column.addView(label("بطاقة ائتمان".equals(source.kind) ? "رصيد ائتماني مسجل" : balance.estimated?"رصيد محسوب · تقديري":"آخر رصيد مسجل", 10, 0xccffffff, false));
            column.addView(label(money(balance.amountMinor, "EGP"), 15, Color.WHITE, true));
            strip.addView(column);
        }
        strip.setOnClickListener(v -> { selectedTab = 1; render(); });
        return strip;
    }

    private void detailRow(LinearLayout container, String title, View value) {
        if (container.getChildCount() > 0) divider(container);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(label(title, 13, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(value);
        container.addView(row);
    }

    private void recurringScreen() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", () -> { selectedTab = 0; render(); }),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.setMarginStart(dp(10));
        bar.addView(label("الالتزامات", 24, INK, true), titleParams);
        bar.addView(primaryIconButton(R.drawable.ic_plus, "إضافة التزام", () -> openCommitmentEditor(null)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(bar);
        // Fixed monthly transfers (e.g. an apartment installment paid by instant transfer) are offered as installments.
        List<CommitmentPayments.ProviderNotice> pendingPayments=db.pendingCommitmentConfirmations();
        if(!pendingPayments.isEmpty()){
            LinearLayout reviews=card();reviews.addView(label(pendingPayments.size()+" تأكيد سداد محتاج تحديد دورة",14,ORANGE,true));
            TextView review=label("راجع التأكيدات",13,ACCENT,true);reviews.addView(review);
            review.setOnClickListener(v->{String[] titles=new String[pendingPayments.size()];for(int i=0;i<titles.length;i++)titles[i]=pendingPayments.get(i).service+" · تاريخ الرسالة "+pendingPayments.get(i).date;
                new ChoiceDialogBuilder().setTitle("تأكيدات بدون مبلغ · لا تضيف مصروفًا").setItems(titles,(d,i)->providerPaymentDialog(pendingPayments.get(i))).show();});
        }
        List<RepairRules.RepeatingTransfer> repeating = db.repeatingTransferSuggestions();
        for (int i = 0; i < Math.min(2, repeating.size()); i++) repeatingCard(repeating.get(i));
        // Subscriptions charged every month but never saved (e.g. ChatGPT).
        for (RepairRules.RepeatingMerchant m : db.repeatingMerchantSuggestions()) {
            LinearLayout box = card();
            box.addView(label("بتدفعه كل شهر ومش متسجل · " + UiKit.displayName(m.merchant), 15, INK, true));
            box.addView(label(money(m.amountMinor, m.currency) + " آخر مرة · اتدفع في " + m.months + " شهور من آخر 4 · غالبًا يوم " + m.typicalDay,
                    13, MUTED, false));
            LinearLayout actions = new LinearLayout(this);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2); actionsParams.topMargin = dp(8);
            box.addView(actions, actionsParams);
            addChip(actions, "سجّله اشتراك", true, () -> { db.confirmRepeatingMerchant(m); toast("اتسجل " + UiKit.displayName(m.merchant)); render(); });
            addChip(actions, "لأ", false, () -> { db.dismissRepeatingMerchant(m); render(); });
        }

        List<ExpenseDb.Recurring> all = db.recurring();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = commitmentMonth;
        LocalDate monthEnd = monthStart.plusMonths(1);
        Map<String, long[]> sums = new LinkedHashMap<>();
        int activeCount = 0, monthCount = 0;
        int reviewCount = 0;
        for (ExpenseDb.Recurring item : all) {
            if(!commitmentFilter.isEmpty() && !commitmentFilter.equals(item.kind)) continue;
            if(item.needsReview) {
                if(!item.isDone()) reviewCount++;
                continue;
            }
            long[] sum = sums.computeIfAbsent(item.currency, ignored -> new long[2]);
            boolean paidThisMonth = false;
            if (item.lastPaidDate != null) {
                LocalDate paid = LocalDate.parse(item.lastPaidDate);
                paidThisMonth = !paid.isBefore(monthStart) && paid.isBefore(monthEnd);
            }
            if (paidThisMonth) { sum[0] += item.amountMinor; sum[1] += item.amountMinor; }
            boolean inMonth = paidThisMonth;
            if (!item.isDone()) {
                activeCount++;
                LocalDate due=item.nextDue;
                int remaining=item.totalCount==null?Integer.MAX_VALUE:item.totalCount-item.paidCount;
                // A monthly commitment already paid this month is not due again in the same month.
                boolean monthlyPaid = paidThisMonth && "monthly".equals(item.cycle);
                for(int payment=0;payment<remaining && due.isBefore(monthEnd);payment++,due=item.nextAfter(due))
                    if(!due.isBefore(monthStart) && !monthlyPaid) { sum[0]+=item.amountMinor; inMonth = true; }
            }
            if (inMonth) monthCount++;
        }
        long[] egp = sums.getOrDefault("EGP", new long[2]);
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(rounded(BRAND, 24));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("مخطط في " + monthStart.format(DateTimeFormatter.ofPattern("MMMM", new Locale("ar"))), 13, 0xffb9d3c8, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        String what = "installment".equals(commitmentFilter) ? "أقساط" : "subscription".equals(commitmentFilter) ? "اشتراكات" :
                "bill".equals(commitmentFilter) ? "فواتير" : "التزامات";
        heading.addView(pill(monthCount + " من " + activeCount + " " + what + " في الشهر ده", BRAND_SOFT, 0xffe6efea));
        hero.addView(heading);
        hero.addView(label(money(egp[0], "EGP"), 30, Color.WHITE, true));
        StringBuilder others = new StringBuilder();
        for (Map.Entry<String, long[]> entry : sums.entrySet())
            if (!"EGP".equals(entry.getKey()) && entry.getValue()[0] > 0)
                others.append(others.length() == 0 ? "+ " : "  ·  ").append(money(entry.getValue()[0], entry.getKey()));
        if (others.length() > 0) {
            TextView other = label(others.toString(), 14, LIME, true);
            other.setTextDirection(View.TEXT_DIRECTION_LTR); other.setGravity(Gravity.START);
            hero.addView(other);
        }
        View track = new View(this);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(8)); trackParams.topMargin = dp(10);
        hero.addView(track, trackParams);
        fillBar(track, egp[1], egp[0], LIME, BRAND_SOFT);
        LinearLayout split = new LinearLayout(this);
        split.addView(label("اتدفع " + money(egp[1], "EGP"), 12, 0xffb9d3c8, false), new LinearLayout.LayoutParams(0, -2, 1));
        split.addView(label("باقي " + money(Math.max(0, egp[0] - egp[1]), "EGP"), 12, 0xffb9d3c8, false));
        hero.addView(split);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2); heroParams.topMargin = dp(14);
        page.addView(hero, heroParams);
        if(reviewCount>0) note(reviewCount+" التزامات تحتاج مراجعة الجهة والمبلغ قبل دخول الحساب والكالندر والتنبيهات.");

        LinearLayout segments = new LinearLayout(this);
        segments.setPadding(dp(4), dp(4), dp(4), dp(4));
        segments.setBackground(rounded(dark ? SURFACE : 0xffe8ebe4, 14));
        String[][] filters = {{"الكل", ""}, {"أقساط", "installment"}, {"اشتراكات", "subscription"}, {"فواتير", "bill"}};
        for (String[] filter : filters) {
            boolean selected = commitmentFilter.equals(filter[1]);
            TextView segment = label(filter[0], 13, selected ? INK : MUTED, selected);
            segment.setGravity(Gravity.CENTER);
            if (selected) segment.setBackground(rounded(dark ? BACKGROUND : Color.WHITE, 11));
            segment.setOnClickListener(v -> { commitmentFilter = filter[1]; render(); });
            segments.addView(segment, new LinearLayout.LayoutParams(0, dp(38), 1));
        }
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, -2); segmentParams.topMargin = dp(14);
        page.addView(segments, segmentParams);

        if(!all.isEmpty()) commitmentCalendar(all);
        if(!db.commitmentSuggestions().isEmpty()) section("مكتشفة من رسائلك · تحتاج تأكيدك",null,null);
        int proposalCount=0;
        for(ExpenseDb.Suggestion suggestion:db.commitmentSuggestions()) {
            if(++proposalCount>3) break;
            LinearLayout found=card();
            found.addView(label("من رسائلك · "+suggestion.merchant,14,INK,true));
            found.addView(label((suggestion.amount==0?"المبلغ غير مذكور":money(suggestion.amount,suggestion.currency))+(suggestion.count==null?"":" × "+suggestion.count+" شهر"),16,ACCENT,true));
            found.addView(label(suggestion.explanation,12,MUTED,false));
            long staleAfter="yearly".equals(suggestion.cycle)?400L:70L;
            if(System.currentTimeMillis()-suggestion.evidence.receivedAt>staleAfter*86400000L)
                found.addView(label("آخر رسالة قديمة؛ راجع السعر والموعد وهل الالتزام لسه مستمر.",12,GOLD,false));
            found.addView(label("آخر رسالة: "+Instant.ofEpochMilli(suggestion.evidence.receivedAt).atZone(ZoneId.systemDefault()).toLocalDate()+
                    " · "+(suggestion.dateExact?"استحقاق ":"موعد تقديري ")+suggestion.date,11,MUTED,false));
            if(suggestion.existingId!=null) found.addView(label("مبلغ جديد لالتزام محفوظ · راجعه قبل التحديث",12,ORANGE,true));
            TextView review=label("راجع المصدر وأضفه للكالندر",13,ACCENT,true);
            review.setPadding(0,dp(10),0,dp(4));
            review.setOnClickListener(v -> openCommitmentSuggestion(suggestion));
            found.addView(review);
            TextView dismiss=label("الخطة دي انتهت أو مش تخصني · تجاهل",12,MUTED,false);
            dismiss.setPadding(0,dp(8),0,dp(2));
            dismiss.setOnClickListener(v -> dismissCommitmentSuggestion(suggestion));
            found.addView(dismiss);
        }

        if(db.commitmentSuggestions().size()>3) {
            TextView older=label("عرض كل الالتزامات المكتشفة ("+db.commitmentSuggestions().size()+")",13,ACCENT,true);page.addView(older);
            older.setOnClickListener(v -> {
                List<ExpenseDb.Suggestion> suggestions=db.commitmentSuggestions();String[] names=new String[suggestions.size()];
                for(int i=0;i<names.length;i++) names[i]=suggestions.get(i).merchant+" · "+(suggestions.get(i).amount==0?"المبلغ غير مذكور":money(suggestions.get(i).amount,suggestions.get(i).currency));
                new ChoiceDialogBuilder().setTitle("كل المقترحات — راجع المصدر والميعاد").setItems(names,(dialog,which) -> {
                    ExpenseDb.Suggestion selected=suggestions.get(which);
                    new ChoiceDialogBuilder().setTitle(selected.merchant)
                            .setItems(new String[]{"راجع المصدر وأضفه", "الخطة انتهت أو مش تخصني · تجاهل"}, (choice, action) -> {
                                if(action==0) openCommitmentSuggestion(selected);
                                else dismissCommitmentSuggestion(selected);
                            }).show();
                }).show();
            });
        }
        section("الجاية", null, null);
        LinearLayout list = null;
        int shown = 0;
        for (ExpenseDb.Recurring item : all) {
            if (item.isDone() || !commitmentFilter.isEmpty() && !commitmentFilter.equals(item.kind)) continue;
            if (list == null) list = listCard(page);
            commitmentRow(list, item);
            shown++;
        }
        if (shown == 0) empty(all.isEmpty() ? "سجّل أول التزام" : "مفيش التزامات في النوع ده",
                "أقساط فاليو أو ترو أو البنك، واشتراكات زي كلود وجوجل ون، وفواتير الموبايل. هنفكّرك قبل الميعاد بيوم.");

        // F-07: read the suggestions from the twelve-month candidate scan, not from the capped 500-row page list,
        // so a payment older than the newest 500 rows still produces its suggestion. Both come from the cache.
        for (ExpenseDb.Transaction item : recurringSuggestions(recurringCandidates, recurringCandidateRows)) {
            LinearLayout suggestion = new LinearLayout(this); suggestion.setGravity(Gravity.CENTER_VERTICAL);
            suggestion.setPadding(dp(14), dp(12), dp(14), dp(12));
            suggestion.setBackground(outlined(dark ? 0x2212a06e : 0xffeaf7df, dark ? 0x5512a06e : 0xffcdebb4, 18));
            suggestion.addView(merchantBadge(item.merchant, item.category, 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(label("لقينا خصم متكرر من " + item.merchant, 13, INK, true));
            copy.addView(label("سداد شهري متكرر · " + money(item.amountMinor, item.currency), 11, MUTED, false));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(10));
            suggestion.addView(copy, copyParams);
            TextView add = label("أضفه", 12, onPrimary(), true);
            add.setGravity(Gravity.CENTER); add.setPadding(dp(14), 0, dp(14), 0);
            add.setBackground(rounded(primaryFill(), 12));
            add.setOnClickListener(v -> confirmRecurring(item));
            suggestion.addView(add, new LinearLayout.LayoutParams(-2, dp(36)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12);
            page.addView(suggestion, params);
        }

        boolean anyDone = false;
        for (ExpenseDb.Recurring item : all) if (item.isDone()) { anyDone = true; break; }
        if (anyDone) {
            section("خلصت", null, null);
            LinearLayout done = listCard(page);
            for (ExpenseDb.Recurring item : all) if (item.isDone()) commitmentRow(done, item);
        }
    }

    private void dismissCommitmentSuggestion(ExpenseDb.Suggestion suggestion) {
        new ChoiceDialogBuilder().setTitle("تجاهل المقترح؟")
                .setMessage("هيختفي من الالتزامات حتى لو الرسالة اتقرت تاني. الرسالة الأصلية هتفضل محفوظة.")
                .setPositiveButton("تجاهل", (dialog, which) -> {
                    db.dismissSuggestion(suggestion.key);
                    editingSuggestion=null;
                    render();
                }).setNegativeButton("إلغاء",null).show();
    }

    private void openCommitmentSuggestion(ExpenseDb.Suggestion suggestion) {
        if(suggestion.existingId==null) {
            List<ExpenseDb.Recurring> matches=new ArrayList<>();
            for(ExpenseDb.Recurring item:db.recurring())
                if(!item.isDone() && item.amountMinor==suggestion.amount && item.currency.equals(suggestion.currency) &&
                        item.kind.equals(suggestion.kind) && item.matches(suggestion.evidence.sender)) matches.add(item);
            if(matches.size()==1) {
                ExpenseDb.Recurring match=matches.get(0);
                new ChoiceDialogBuilder().setTitle("في التزام بنفس الجهة والمبلغ")
                        .setMessage(match.merchant+" · "+money(match.amountMinor,match.currency)+". هل الرسالة تخص نفس الخطة؟")
                        .setPositiveButton("راجع نفس الخطة",(dialog,which) -> {editingCommitment=match;editingSuggestion=suggestion;selectedTab=12;render();})
                        .setNeutralButton("خطة مختلفة",(dialog,which) -> {editingCommitment=null;editingSuggestion=suggestion;selectedTab=12;render();})
                        .setNegativeButton("إلغاء",null).show();
                return;
            }
        }
        editingCommitment=suggestion.existingId==null?null:db.recurringById(suggestion.existingId);
        editingSuggestion=suggestion;selectedTab=12;render();
    }

    private List<ExpenseDb.Recurring> commitmentsOn(List<ExpenseDb.Recurring> all,LocalDate date) {
        List<ExpenseDb.Recurring> result=new ArrayList<>();
        for(ExpenseDb.Recurring item:all) {
            if(item.isDone() || item.needsReview ||
                    !commitmentFilter.isEmpty() && !commitmentFilter.equals(item.kind)) continue;
            LocalDate due=item.nextDue;
            int remaining=item.totalCount==null?Integer.MAX_VALUE:item.totalCount-item.paidCount;
            for(int i=0;i<remaining && !due.isAfter(date);i++,due=item.nextAfter(due))
                if(due.equals(date)) { result.add(item);break; }
        }
        return result;
    }

    private void commitmentCalendar(List<ExpenseDb.Recurring> all) {
        LinearLayout box=card();
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(iconButton(R.drawable.ic_chevron_back,"الشهر السابق",() -> {commitmentMonth=commitmentMonth.minusMonths(1);render();}),new LinearLayout.LayoutParams(dp(40),dp(40)));
        TextView month=label(commitmentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy",new Locale("ar"))),16,INK,true);
        month.setGravity(Gravity.CENTER);heading.addView(month,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(iconButton(R.drawable.ic_chevron_forward,"الشهر التالي",() -> {commitmentMonth=commitmentMonth.plusMonths(1);render();}),new LinearLayout.LayoutParams(dp(40),dp(40)));
        box.addView(heading);
        LinearLayout names=new LinearLayout(this);
        for(String name:new String[]{"س","ح","ن","ث","ر","خ","ج"}) names.addView(centered(label(name,12,MUTED,false)),new LinearLayout.LayoutParams(0,dp(32),1));
        box.addView(names);
        int offset=(commitmentMonth.getDayOfWeek().getValue()+1)%7;
        int cells=((offset+commitmentMonth.lengthOfMonth()+6)/7)*7;
        LinearLayout row=null;
        for(int index=0;index<cells;index++) {
            if(index%7==0) { row=new LinearLayout(this);box.addView(row); }
            int number=index-offset+1;
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
            if(number>0 && number<=commitmentMonth.lengthOfMonth()) {
                LocalDate date=commitmentMonth.withDayOfMonth(number);
                boolean today=date.equals(LocalDate.now());
                List<ExpenseDb.Recurring> due=commitmentsOn(all,date);
                cell.setBackground(rounded(today?BRAND:due.isEmpty()?SURFACE:ACCENT_PALE,12));
                cell.addView(centered(label(Integer.toString(number),14,today?LIME:INK,!due.isEmpty()||today)));
                cell.addView(centered(label(due.isEmpty()?"":due.size()+" •",10,today?LIME:ACCENT,true)));
                cell.setContentDescription(date+" · "+due.size()+" التزامات");
                cell.setOnClickListener(v -> showCommitmentsOn(date, due));
            }
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(52),1);params.setMargins(dp(2),dp(2),dp(2),dp(2));
            row.addView(cell,params);
        }
        box.addView(label("اضغط يوم لعرض التزاماته · المقترحات تحتاج تأكيدًا قبل دخول الكالندر",11,MUTED,false));
    }

    private void showCommitmentsOn(LocalDate date, List<ExpenseDb.Recurring> due) {
        LinearLayout list = form();
        AlertDialog dialog = new ChoiceDialogBuilder()
                .setTitle(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("ar"))))
                .setView(scrollForm(list)).setPositiveButton("إغلاق", null).create();
        if (due.isEmpty()) list.addView(label("مفيش التزامات مؤكدة في اليوم ده", 14, MUTED, false));
        for (ExpenseDb.Recurring item : due) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(14), dp(12), dp(14));
            row.setBackground(outlined(SURFACE, BORDER, 14));
            LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(label(item.merchant, 15, INK, true));
            copy.addView(label(money(item.amountMinor, item.currency), 13, MUTED, false));
            copy.addView(label("عرض التفاصيل", 13, ACCENT, true));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(tintedIcon(R.drawable.ic_chevron_forward, ACCENT), new LinearLayout.LayoutParams(dp(22), dp(22)));
            row.setContentDescription("عرض تفاصيل " + item.merchant);
            row.setOnClickListener(view -> {
                dialog.dismiss(); selectedCommitmentId = item.id; selectedTab = 11; render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8);
            list.addView(row, params);
        }
        dialog.show();
    }

    private void commitmentRow(LinearLayout container, ExpenseDb.Recurring item) {
        if (container.getChildCount() > 0) divider(container);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(merchantBadge(item.merchant, kindCategory(item.kind), 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(item.merchant, 14, INK, true);
        name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name);
        if(item.needsReview) copy.addView(label("راجع الجهة والمبلغ · اسم البنك غير كافٍ",11,ORANGE,true));
        LinearLayout meta = new LinearLayout(this); meta.setGravity(Gravity.CENTER_VERTICAL);
        if ("installment".equals(item.kind) && item.totalCount != null && item.totalCount > 0) {
            View bar = new View(this);
            meta.addView(bar, new LinearLayout.LayoutParams(dp(60), dp(5)));
            fillBar(bar, item.paidCount, item.totalCount, EMERALD, BORDER);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2); textParams.setMarginStart(dp(8));
            meta.addView(label(item.isDone() ? "انتهى · اتدفع " + item.paidCount + " من " + item.totalCount :
                    "اتدفع " + item.paidCount + " من " + item.totalCount + " · الجاي " + (item.paidCount + 1), 11, MUTED, false), textParams);
        } else {
            meta.addView(label(kindLabel(item.kind) + " " + cycleLabel(item.cycle) + " · " +
                    item.nextDue.format(DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("ar"))), 11, MUTED, false));
        }
        copy.addView(meta);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(12));
        row.addView(copy, copyParams);
        LinearLayout end = new LinearLayout(this); end.setOrientation(LinearLayout.VERTICAL); end.setGravity(Gravity.END);
        TextView amount = label(money(item.amountMinor, item.currency), 14, INK, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        end.addView(amount);
        if (!item.isDone()) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), item.nextDue);
            if (days <= 1) end.addView(pill(dueWord(item.nextDue), dark ? 0x55a82c2c : 0xfffde8e8, dark ? 0xffffb4b4 : 0xffa82c2c));
            else end.addView(label(dueWord(item.nextDue), 11, MUTED, false));
        }
        row.addView(end);
        row.setOnClickListener(v -> { selectedCommitmentId = item.id; selectedTab = 11; render(); });
        container.addView(row);
    }

    private void commitmentDetailsScreen() {
        ExpenseDb.Recurring item = db.recurringById(selectedCommitmentId);
        if (item == null) { selectedTab = 2; recurringScreen(); return; }
        boolean installment = "installment".equals(item.kind);
        if(item.needsReview) note("الالتزام محفوظ باختيارك. راجع الاسم والمبلغ قبل الاعتماد على المطابقة التلقائية.");
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", () -> { selectedTab = 2; render(); }),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("تفاصيل " + kindLabel(item.kind), 15, INK, true); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(iconButton(R.drawable.ic_edit, "تعديل", () -> openCommitmentEditor(item)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(toolbar);

        LinearLayout summary = card();
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(18), dp(18), dp(18), dp(18));
        ((LinearLayout.LayoutParams) summary.getLayoutParams()).topMargin = dp(16);
        if (installment && item.totalCount != null && item.totalCount > 0) {
            FrameLayout ringBox = new FrameLayout(this);
            UiKit.Ring ring = new UiKit.Ring(this, BORDER, EMERALD);
            ring.setFraction(item.paidCount / (float) item.totalCount);
            ring.setContentDescription("اتدفع " + item.paidCount + " من " + item.totalCount);
            ringBox.addView(ring, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            TextView count = label(item.paidCount + "/" + item.totalCount, 24, INK, true);
            count.setTextDirection(View.TEXT_DIRECTION_LTR);
            count.setGravity(Gravity.CENTER);
            center.addView(count,new LinearLayout.LayoutParams(-1,-2));
            TextView paidCaption=label("قسط مدفوع",11,MUTED,false);
            paidCaption.setGravity(Gravity.CENTER);
            center.addView(paidCaption,new LinearLayout.LayoutParams(-1,-2));
            ringBox.addView(center, new FrameLayout.LayoutParams(-1, -1));
            summary.addView(ringBox, new LinearLayout.LayoutParams(dp(118), dp(118)));
        } else {
            summary.addView(merchantBadge(item.merchant, kindCategory(item.kind), 64), new LinearLayout.LayoutParams(dp(64), dp(64)));
        }
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout name = new LinearLayout(this); name.setGravity(Gravity.CENTER_VERTICAL);
        if (installment) {
            name.addView(merchantBadge(item.merchant, kindCategory(item.kind), 30), new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout.LayoutParams nameText = new LinearLayout.LayoutParams(0, -2, 1); nameText.setMarginStart(dp(8));
            name.addView(label(item.merchant + " · " + money(item.amountMinor, item.currency), 17, INK, true), nameText);
        } else name.addView(label(item.merchant, 18, INK, true));
        info.addView(name);
        info.addView(label(kindLabel(item.kind) + " " + cycleLabel(item.cycle), 13, MUTED, false));
        if (installment && item.totalCount != null) {
            info.addView(label("اتدفع " + item.paidCount + " قسط · باقي " + Math.max(0,item.totalCount-item.paidCount)+" قسط",13,INK,false));
            if (!item.isDone()) {
                LocalDate last = item.nextDue;
                for (int i = item.paidCount + 1; i < item.totalCount; i++) last = item.nextAfter(last);
                info.addView(label("آخر قسط: " + last.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ar"))), 12, MUTED, false));
            }
        }
        if (item.paymentSourceId != null)
            for (ExpenseDb.PaymentSource source : db.paymentSources())
                if (source.id == item.paymentSourceId)
                    info.addView(label("من " + source.name + (source.last4 == null ? "" : " •••• " + source.last4), 12, MUTED, false));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1); infoParams.setMarginStart(dp(16));
        summary.addView(info, infoParams);

        LinearLayout next = new LinearLayout(this); next.setOrientation(LinearLayout.VERTICAL);
        next.setPadding(dp(18), dp(16), dp(18), dp(16));
        next.setBackground(rounded(BRAND, 22));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2); nextParams.topMargin = dp(12);
        page.addView(next, nextParams);
        if (item.isDone()) {
            next.addView(label(installment && item.totalCount != null && item.paidCount >= item.totalCount ?
                    "خلصت كل الأقساط" : "الالتزام ده انتهى", 17, Color.WHITE, true));
            next.addView(label(item.lastPaidDate == null ? "" : "آخر دفعة " + item.lastPaidDate, 12, 0xffb9d3c8, false));
        } else {
            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(label(installment ? "القسط الجاي" : "الدفعة الجاية", 13, 0xffb9d3c8, false), new LinearLayout.LayoutParams(0, -2, 1));
            heading.addView(pill(dueWord(item.nextDue), LIME, BRAND));
            next.addView(heading);
            LinearLayout amountRow = new LinearLayout(this); amountRow.setGravity(Gravity.CENTER_VERTICAL);
            amountRow.addView(label(money(item.amountMinor, item.currency), 26, Color.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
            amountRow.addView(label(item.nextDue.format(DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("ar"))), 13, 0xffe6efea, false));
            next.addView(amountRow);
            TextView paid = label("علّمه مدفوع", 14, Color.WHITE, true);
            paid.setGravity(Gravity.CENTER); paid.setBackground(rounded(0x1fffffff, 14));
            paid.setOnClickListener(v -> {
                String key=db.commitmentIdentity(item.id);
                new ChoiceDialogBuilder().setTitle("سداد دورة "+item.nextDue)
                        .setItems(new String[]{"اربط عملية مسجلة · يشمل الدفع الجزئي","تأكيد يدوي فقط · بدون مصروف جديد"},(d,choice)->{
                            if(choice==0)commitmentLinkDialog(item);
                            else new ChoiceDialogBuilder().setTitle("تأكيد سداد بدون قيد مالي")
                                    .setMessage(item.merchant+" · دورة "+item.nextDue+"\nالتأكيد يعلّم المتبقي مدفوعًا ولا يضيف مصروفًا. تقدر تتراجع من التسويات.")
                                    .setPositiveButton("تأكيد",(a,b)->commitmentWrite(()->db.confirmCommitmentCycle(item.id,item.nextDue,key,LocalDate.now())))
                                    .setNegativeButton("إلغاء",null).show();
                        }).show();
            });
            LinearLayout.LayoutParams paidParams = new LinearLayout.LayoutParams(-1, dp(46)); paidParams.topMargin = dp(10);
            next.addView(paid, paidParams);
        }

        if (!item.isDone()) finishCommitmentAction(item, installment);
        else if (item.totalCount == null || item.paidCount < item.totalCount) {
            TextView reopen = label("إعادة تفعيل الالتزام", 14, ACCENT, true);
            reopen.setGravity(Gravity.CENTER);
            reopen.setPadding(0, dp(12), 0, dp(12));
            reopen.setOnClickListener(v -> new ChoiceDialogBuilder()
                    .setTitle("إعادة تفعيل " + item.merchant + "؟")
                    .setMessage("هيرجع للكالندر والتنبيهات من موعد الاستحقاق المحفوظ. راجع الموعد لو اتغير.")
                    .setPositiveButton("تفعيل", (dialog, which) -> { db.reopenRecurring(item.id); render(); })
                    .setNegativeButton("إلغاء", null).show());
            page.addView(reopen);
        }

        LinearLayout settlements=card();
        settlements.addView(label("السداد بيتحدّث تلقائيًا عند تطابق الجهة والمبلغ والعملة والدورة. الاختلاف فقط يحتاج مراجعة.",12,MUTED,false));
        for(CommitmentPayments.Cycle cycle:db.commitmentCycles(item.id))if(cycle.due.equals(item.nextDue)&&cycle.remaining()>0){
            settlements.addView(label("دورة "+cycle.due+" · المسدد "+money(cycle.paid,cycle.currency)+" · المتبقي "+money(cycle.remaining(),cycle.currency),13,INK,true));
            if(cycle.invalid>0)settlements.addView(label("دفع مرتبط اتغيّر أو اتحذف أو رجع منه مبلغ؛ راجع التسويات.",12,ORANGE,false));
        }
        TextView settlementHistory=label("عرض التسويات وفك الربط",13,ACCENT,true);settlements.addView(settlementHistory);
        settlementHistory.setOnClickListener(v->commitmentPaymentsDialog(item.id));
        LinearLayout settings = card();
        LinearLayout reminder = new LinearLayout(this); reminder.setGravity(Gravity.CENTER_VERTICAL);
        reminder.setPadding(0, dp(4), 0, dp(4));
        reminder.addView(tintedIcon(R.drawable.ic_bell, INK), new LinearLayout.LayoutParams(dp(20), dp(20)));
        String reminderText = item.remindDays < 0 ? "من غير تنبيه" : item.remindDays == 0 ? "فكّرني يوم الاستحقاق" :
                item.remindDays == 1 ? "فكّرني قبلها بيوم" : "فكّرني قبلها بـ" + item.remindDays + " أيام";
        LinearLayout.LayoutParams reminderText1 = new LinearLayout.LayoutParams(0, -2, 1); reminderText1.setMarginStart(dp(10));
        reminder.addView(label(reminderText, 14, INK, false), reminderText1);
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(item.remindDays >= 0);
        toggle.setEnabled(!item.isDone());
        toggle.setContentDescription("تنبيه قبل الاستحقاق");
        toggle.setOnCheckedChangeListener((button, checked) -> {
            db.setRemindDays(item.id, checked ? 1 : -1);
            if (checked) askNotificationPermission();
            render();
        });
        reminder.addView(toggle);
        settings.addView(reminder);
        divider(settings);
        LinearLayout source = new LinearLayout(this); source.setGravity(Gravity.CENTER_VERTICAL);
        source.setPadding(0, dp(8), 0, dp(4));
        source.addView(tintedIcon(R.drawable.ic_message, INK), new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout.LayoutParams sourceText = new LinearLayout.LayoutParams(0, -2, 1); sourceText.setMarginStart(dp(10));
        source.addView(label("بيتعلم مدفوع لوحده من رسائل " + item.merchant, 13, INK, false), sourceText);
        source.addView(label(item.isDone() || item.needsReview?"متوقف":"شغال",12,item.isDone() || item.needsReview?MUTED:INCOME,true));
        settings.addView(source);
        List<ExpenseDb.SourceEvidence> commitmentHistory=db.commitmentHistoryFor(item.id);
        if(!commitmentHistory.isEmpty()) {
            TextView history=label("عرض رسائل الالتزام ومبالغ الشهور السابقة",13,ACCENT,true);
            history.setPadding(0,dp(12),0,dp(4));
            history.setOnClickListener(v -> showOriginalMessages(commitmentHistory));
            settings.addView(history);
        }

        Long originId=db.commitmentOriginId(item.id);
        if(originId!=null) {
            ExpenseDb.Transaction origin=db.transactionById(originId);
            TextView original=label(origin==null?"العملية الأصلية حُذفت؛ الالتزام محفوظ":"عرض العملية التي أنشأت الالتزام",13,ACCENT,true);
            original.setPadding(0,dp(12),0,dp(12));page.addView(original);
            if(origin!=null)original.setOnClickListener(v->{transactionJourney.clear();selectedTransaction=origin;transactionReturnTab=11;selectedTab=10;render();});
        }
        section("الجدول", null, null);
        LinearLayout schedule = listCard(page);
        if (item.lastPaidDate != null)
            scheduleRow(schedule, true, (installment && item.paidCount > 0 ? "قسط " + item.paidCount + " · " : "آخر دفعة · ") +
                    LocalDate.parse(item.lastPaidDate).format(DateTimeFormatter.ofPattern("d MMMM", new Locale("ar"))), "اتدفع");
        if (!item.isDone()) {
            LocalDate due = item.nextDue;
            int remaining = installment && item.totalCount != null ? item.totalCount - item.paidCount : 3;
            for (int i = 0; i < Math.min(3, remaining); i++) {
                String name1 = (installment ? "قسط " + (item.paidCount + 1 + i) + " · " : "") +
                        due.format(DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ar")));
                scheduleRow(schedule, false, name1, i == 0 ? dueWord(due) : money(item.amountMinor, item.currency));
                due = item.nextAfter(due);
            }
        }

        TextView delete = label("حذف الالتزام", 14, CORAL, true);
        delete.setGravity(Gravity.CENTER);
        delete.setOnClickListener(v -> new ChoiceDialogBuilder().setTitle("حذف " + item.merchant + "؟")
                .setMessage("التنبيهات هتقف، والعمليات المسجلة هتفضل زي ما هي.")
                .setPositiveButton("حذف", (dialog, which) -> { db.removeRecurring(item.id); selectedTab = 2; render(); })
                .setNegativeButton("إلغاء", null).show());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(50)); deleteParams.topMargin = dp(18);
        page.addView(delete, deleteParams);
    }

    private interface CommitmentChange{boolean apply();}
    private void commitmentWrite(CommitmentChange change){
        Context app=getApplicationContext();
        CaptureQueue.submit(()->{
            final boolean changed;
            final long before=db.changes();
            try{changed=change.apply();}
            catch(RuntimeException failed){postUi(()->toast("تعذر اعتماد التسوية؛ راجع العملية والدورة وحاول تاني"));return;}
            if(changed||db.changes()!=before)try{SmsReceiver.notifyDataChanged(app);}catch(RuntimeException output){postUi(()->toast("التسوية محفوظة؛ تعذر تحديث التنبيهات"));}
            postUi(()->{toast(changed?"اتحدثت التسوية بدون مصروف جديد":"ما اتسجلش سداد جديد؛ راجع الحالة الحالية");render();});
        });
    }
    private void commitmentLinkDialog(ExpenseDb.Recurring item){
        LinearLayout form=form();EditText due=dateInput("دورة الاستحقاق",form,item.nextDue);
        form.addView(label("اختار دورة من الجدول؛ هنجيب العمليات المسجلة في الشهر المحيط بها.",12,MUTED,false));
        new ChoiceDialogBuilder().setTitle("اربط دفع موجود · بدون مصروف إضافي").setView(form)
                .setPositiveButton("اختار العملية",(d,w)->{
                    LocalDate selected=dateValue(due);if(selected==null)return;
                    List<ExpenseDb.Transaction> candidates=db.commitmentPaymentCandidates(selected);
                    if(candidates.isEmpty()){toast("مفيش دفعات مؤهلة في الفترة دي. راجع تاريخ الدورة أو فك رابط سابق من التسويات.");return;}
                    String[] titles=new String[candidates.size()];for(int i=0;i<titles.length;i++){
                        ExpenseDb.Transaction tx=candidates.get(i);titles[i]=tx.merchant+" · "+money(tx.amountMinor,tx.currency)+" · "+Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate();
                    }
                    new ChoiceDialogBuilder().setTitle("اختار الدفعة اللي تخص "+item.merchant).setItems(titles,(a,i)->{
                        try{commitmentAllocationDialog(db.commitmentPaymentPreview(item.id,selected,candidates.get(i).id));}
                        catch(IllegalArgumentException changed){toast("الدورة مش متاحة بنفس العملة أو العملية اتغيرت؛ راجع الاختيار");}
                    }).show();
                }).setNegativeButton("إلغاء",null).show();
    }
    private void commitmentAllocationDialog(CommitmentPayments.AllocationPreview preview){
        LinearLayout form=form();
        form.addView(label(preview.transaction.merchant+" · "+money(preview.transaction.amountMinor,preview.transaction.currency)+"\nدورة "+preview.due+" · المطلوب "+money(preview.expected,preview.currency),14,INK,true));
        EditText amount=input("المبلغ المخصص للدورة",form);decimalInput(amount);
        amount.setText(String.format(Locale.ROOT,"%.2f",preview.max/100.0));
        form.addView(label("حد الربط "+money(preview.max,preview.currency)+". الربط يستخدم العملية الحالية ولا يضيف مصروفًا.",12,MUTED,false));
        form.addView(label("لو الدفعة التلقائية غير مصنفة أو تحويلات، هتاخد تصنيف الالتزام. أي تصنيف عدّلته بنفسك محفوظ.",12,MUTED,false));
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("راجع ربط الدفعة").setView(form).setPositiveButton("اعتماد الربط",null).setNegativeButton("إلغاء",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(rejectMisreadAmount(amount))return;
            long minor=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
            if(minor<=0||minor>preview.max){toast("اكتب مبلغًا موجبًا لحد المبلغ المتاح");return;}
            dialog.dismiss();commitmentWrite(()->{db.linkCommitmentPayment(preview,minor);return true;});
        }));dialog.show();
    }
    private void commitmentPaymentsDialog(long id){
        List<CommitmentPayments.Payment> payments=db.commitmentPayments(id);
        if(payments.isEmpty()){toast("مفيش تسويات مرتبطة؛ العدد السابق إقرار محفوظ بدون روابط دفعات");return;}
        String[] titles=new String[payments.size()];for(int i=0;i<titles.length;i++){
            CommitmentPayments.Payment p=payments.get(i);
            String origin="manual".equals(p.origin)?"تأكيد يدوي":"provider".equals(p.origin)?"تأكيد مقدم الخدمة":"automatic".equals(p.origin)?"ربط تلقائي":"ربط راجعته";
            titles[i]=p.due+" · "+origin+" · "+(!p.active?p.amount==0?"مصدر تأكيد إضافي بدون مبلغ":"اتلغى الربط":!p.valid?"غير مطبق · العملية اتغيرت أو اتحذفت":money(p.applied,p.currency)+" من "+money(p.amount,p.currency));
        }
        new ChoiceDialogBuilder().setTitle("تسويات الدورات").setItems(titles,(d,i)->{
            CommitmentPayments.Payment p=payments.get(i);
            AlertDialog.Builder detail=new ChoiceDialogBuilder().setTitle("دورة "+p.due)
                    .setMessage(titles[i]+"\nفك الربط يعيد حساب الدورة ويحافظ على العملية المالية وتصنيفها والمصدر.").setNegativeButton("إغلاق",null);
            if(p.active)detail.setPositiveButton("فك الربط",(a,b)->commitmentWrite(()->db.undoCommitmentPayment(p.id)));
            if(p.transactionId!=null)detail.setNeutralButton("عرض العملية",(a,b)->{selectedTransaction=db.transactionById(p.transactionId);if(selectedTransaction!=null){transactionReturnTab=11;selectedTab=10;render();}});
            else if("provider".equals(p.origin))detail.setNeutralButton("عرض رسالة التأكيد",(a,b)->{
                EvidenceVault.Read evidence=db.commitmentPaymentEvidence(p.id);
                new ChoiceDialogBuilder().setTitle("المصدر المحفوظ").setMessage(evidence.body==null?"المصدر المشفر غير متاح":evidence.body).setPositiveButton("إغلاق",null).show();
            });
            detail.show();
        }).show();
    }
    private void providerPaymentDialog(CommitmentPayments.ProviderNotice notice){
        new ChoiceDialogBuilder().setTitle(notice.service+" · تأكيد بدون مبلغ")
                .setMessage((notice.failed||notice.body==null?"المصدر المشفر غير متاح":notice.body)+"\nتاريخ المصدر: "+notice.date+"\nاختار الالتزام ودورة الاستحقاق. التأكيد لا يضيف مصروفًا.")
                .setPositiveButton("اختار الالتزام",(d,w)->{
                    List<ExpenseDb.Recurring> plans=new ArrayList<>();for(ExpenseDb.Recurring plan:db.recurring())if(!plan.isDone())plans.add(plan);
                    if(plans.isEmpty()){toast("مفيش التزام نشط؛ التأكيد محفوظ لحد ما تضيف الالتزام");return;}
                    String[] titles=new String[plans.size()];for(int i=0;i<titles.length;i++)titles[i]=plans.get(i).merchant+" · "+money(plans.get(i).amountMinor,plans.get(i).currency);
                    new ChoiceDialogBuilder().setTitle("التزام التأكيد").setItems(titles,(a,i)->{
                        ExpenseDb.Recurring plan=plans.get(i);String key=db.commitmentIdentity(plan.id);
                        LinearLayout form=form();EditText due=dateInput("دورة الاستحقاق",form,plan.nextDue);
                        new ChoiceDialogBuilder().setTitle(plan.merchant+" · تأكيد حالة فقط").setView(form)
                                .setPositiveButton("اعتماد التأكيد",(b,c)->{LocalDate date=dateValue(due);if(date!=null)commitmentWrite(()->db.resolveCommitmentConfirmation(notice.id,plan.id,date,key));})
                                .setNegativeButton("إلغاء",null).show();
                    }).show();
                }).setNeutralButton("مش متعلق بالتزاماتي",(d,w)->commitmentWrite(()->db.dismissCommitmentConfirmation(notice.id)))
                .setNegativeButton("لاحقًا",null).show();
    }

    private void finishCommitmentAction(ExpenseDb.Recurring item, boolean installment) {
        TextView finish = label("إنهاء الالتزام", 14, ACCENT, true);
        finish.setGravity(Gravity.CENTER);
        finish.setPadding(0, dp(12), 0, dp(12));
        finish.setOnClickListener(v -> {
            if (installment && item.totalCount != null && item.paidCount < item.totalCount) {
                new ChoiceDialogBuilder().setTitle("إنهاء " + item.merchant + "؟")
                        .setMessage("المسجل مدفوع: " + item.paidCount + " من " + item.totalCount + ". اختار حسب اللي حصل فعلاً؛ العمليات المالية مش هتتغير.")
                        .setItems(new String[]{"انتهت الخطة · احتفظ بالعدد المدفوع", "كل الأقساط اتدفعت"}, (dialog, choice) -> {
                            db.finishRecurring(item.id, choice == 1);
                            render();
                        }).setNegativeButton("إلغاء", null).show();
            } else new ChoiceDialogBuilder().setTitle("إنهاء " + item.merchant + "؟")
                    .setMessage("هيتوقف من الكالندر والتنبيهات، والعمليات المالية هتفضل زي ما هي.")
                    .setPositiveButton("إنهاء", (dialog, which) -> { db.finishRecurring(item.id, false); render(); })
                    .setNegativeButton("إلغاء", null).show();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        page.addView(finish, params);
    }

    private void scheduleRow(LinearLayout container, boolean done, String text, String end) {
        if (container.getChildCount() > 0) divider(container);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        View mark;
        if (done) {
            ImageView check = tintedIcon(R.drawable.ic_check, INCOME);
            check.setPadding(dp(4), dp(4), dp(4), dp(4));
            check.setBackground(rounded(dark ? 0x33138a5c : 0xffe7f5ec, 12));
            mark = check;
        } else {
            mark = new View(this);
            mark.setBackground(outlined(Color.TRANSPARENT, EMERALD, 12));
        }
        row.addView(mark, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1); textParams.setMarginStart(dp(10));
        row.addView(label(text, 13, INK, !done), textParams);
        TextView endLabel = label(end, 12, done ? INCOME : MUTED, false);
        endLabel.setTextDirection(View.TEXT_DIRECTION_LTR);
        row.addView(endLabel);
        container.addView(row);
    }

    private void openCommitmentEditor(ExpenseDb.Recurring existing) {
        editingCommitment = existing;
        editingSuggestion=null;
        selectedTab = 12;
        render();
    }

    private void commitmentEditorScreen() {
        ExpenseDb.Recurring existing = editingCommitment;
        ExpenseDb.Recurring futureTerms=existing==null?null:db.storedRecurringById(existing.id);
        ExpenseDb.Suggestion suggestion=editingSuggestion;
        LocalDate[] chosenDue={suggestion!=null?suggestion.date:existing!=null?existing.nextDue:nextDueForDay(LocalDate.now().getDayOfMonth())};
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", this::onBackPressed), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label(existing == null ? "التزام جديد" : "تعديل الالتزام", 16, INK, true); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(44)));
        page.addView(toolbar);

        String[] kind = {existing == null ? suggestion==null?"installment":suggestion.kind : existing.kind};
        int[] dueDay = {existing==null?chosenDue[0].getDayOfMonth():existing.dueDay};
        int[] remind = {existing == null ? 1 : existing.remindDays};

        LinearLayout segments = new LinearLayout(this);
        segments.setPadding(dp(4), dp(4), dp(4), dp(4));
        segments.setBackground(rounded(dark ? SURFACE : 0xffe8ebe4, 14));
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, -2); segmentParams.topMargin = dp(14);
        page.addView(segments, segmentParams);

        TextView providerCaption = caption("الجهة");
        LinearLayout providers = new LinearLayout(this); providers.setOrientation(LinearLayout.VERTICAL);
        page.addView(providers);
        EditText name = input("اسم الجهة، مثال: valU أو Claude", page);
        if (existing != null) name.setText(existing.merchant);
        else if(suggestion!=null) name.setText(suggestion.merchant);
        if(suggestion!=null) {
            note(suggestion.explanation);
            TextView source=label("عرض الرسالة الأصلية",14,ACCENT,true);
            source.setText("عرض الرسائل ومبالغ الشهور السابقة");
            source.setOnClickListener(v -> {
                List<ExpenseDb.SourceEvidence> history=db.commitmentHistory(suggestion.key);
                showOriginalMessages(history.isEmpty()?java.util.Collections.singletonList(suggestion.evidence):history);
            });
            page.addView(source);
        }

        caption("المبلغ");
        if(existing!=null&&!db.commitmentCycles(existing.id).isEmpty())
            note("الدورات التي بدأت تحتفظ بمبلغها وعملتها؛ تعديل المبلغ يخص الدورات الجديدة. تسويات الدفعات محفوظة.");
        LinearLayout amountRow = new LinearLayout(this); amountRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(amountRow, new LinearLayout.LayoutParams(-1, -2));
        EditText amount = input(kind[0].equals("installment") ? "القسط الشهري" : "المبلغ", amountRow);
        ((LinearLayout.LayoutParams) amount.getLayoutParams()).width = 0;
        ((LinearLayout.LayoutParams) amount.getLayoutParams()).weight = 1;
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(suggestion!=null && suggestion.amount>0) amount.setText(String.format(Locale.ROOT,"%.2f",suggestion.amount/100.0));
        else if (futureTerms != null) amount.setText(String.format(Locale.ROOT, "%.2f", futureTerms.amountMinor / 100.0));
        Spinner currency = new AppChoiceSpinner("العملة", CURRENCIES);
        currency.setBackground(outlined(SURFACE, BORDER, 12));
        LinearLayout.LayoutParams currencyParams = new LinearLayout.LayoutParams(dp(96), dp(50));
        currencyParams.topMargin = dp(10); currencyParams.setMarginStart(dp(8));
        amountRow.addView(currency, currencyParams);
        if (futureTerms != null) for (int i = 0; i < CURRENCIES.length; i++) if (CURRENCIES[i].equals(futureTerms.currency)) currency.setSelection(i);
        if (suggestion != null) for (int i=0;i<CURRENCIES.length;i++) if(CURRENCIES[i].equals(suggestion.currency)) currency.setSelection(i);

        LinearLayout installmentBox = new LinearLayout(this); installmentBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(installmentBox);
        TextView countCaption = label("عدد الأقساط", 12, MUTED, true);
        LinearLayout.LayoutParams countCaptionParams = new LinearLayout.LayoutParams(-1, -2); countCaptionParams.topMargin = dp(14);
        installmentBox.addView(countCaption, countCaptionParams);
        LinearLayout counts = new LinearLayout(this);
        installmentBox.addView(counts);
        EditText total = input("الإجمالي، مثال 12", counts);
        ((LinearLayout.LayoutParams) total.getLayoutParams()).width = 0;
        ((LinearLayout.LayoutParams) total.getLayoutParams()).weight = 1;
        total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText paid = input("اتدفع منهم كام", counts);
        ((LinearLayout.LayoutParams) paid.getLayoutParams()).width = 0;
        ((LinearLayout.LayoutParams) paid.getLayoutParams()).weight = 1;
        ((LinearLayout.LayoutParams) paid.getLayoutParams()).setMarginStart(dp(8));
        paid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (existing != null && existing.totalCount != null) total.setText(Integer.toString(existing.totalCount));
        else if(suggestion!=null && suggestion.count!=null) total.setText(suggestion.count.toString());
        if (existing != null) paid.setText(Integer.toString(existing.paidCount));

        caption("يوم الاستحقاق كل شهر");
        android.widget.HorizontalScrollView dayScroll = new android.widget.HorizontalScrollView(this);
        dayScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout days = new LinearLayout(this); days.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        dayScroll.addView(days);
        page.addView(dayScroll);
        List<TextView> dayViews = new ArrayList<>();
        TextView exactDue=label("موعد الدفعة القادمة: "+chosenDue[0].format(DATE_LABEL)+" · تغيير",14,ACCENT,true);
        Runnable paintDays = () -> {
            for (int i = 0; i < dayViews.size(); i++) {
                boolean selected = i + 1 == dueDay[0];
                TextView day = dayViews.get(i);
                day.setTextColor(selected ? onPrimary() : INK);
                day.setTypeface(selected ? BOLD : REGULAR);
                day.setBackground(selected ? rounded(primaryFill(), 14) : outlined(SURFACE, BORDER, 14));
            }
        };
        for (int d = 1; d <= 31; d++) {
            int value = d;
            TextView day = label(Integer.toString(d), 15, INK, false);
            day.setGravity(Gravity.CENTER);
            day.setOnClickListener(v -> {
                dueDay[0]=value;
                chosenDue[0]=chosenDue[0].withDayOfMonth(Math.min(value,chosenDue[0].lengthOfMonth()));
                exactDue.setText("موعد الدفعة القادمة: "+chosenDue[0].format(DATE_LABEL)+" · تغيير");
                paintDays.run();
            });
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(dp(48), dp(48)); dayParams.setMarginEnd(dp(8));
            days.addView(day, dayParams);
            dayViews.add(day);
        }
        paintDays.run();
        exactDue.setPadding(0,dp(12),0,dp(10));
        exactDue.setOnClickListener(v -> new android.app.DatePickerDialog(this,(picker,year,month,day) -> {
            chosenDue[0]=LocalDate.of(year,month+1,day);dueDay[0]=day;paintDays.run();
            exactDue.setText("موعد الدفعة القادمة: "+chosenDue[0].format(DATE_LABEL)+" · تغيير");
        },chosenDue[0].getYear(),chosenDue[0].getMonthValue()-1,chosenDue[0].getDayOfMonth()).show());
        page.addView(exactDue);
        dayScroll.post(() -> {
            View selected = dayViews.get(Math.max(0, Math.min(30, dueDay[0] - 1)));
            dayScroll.scrollTo(Math.max(0, selected.getLeft() - dp(120)), 0);
        });

        TextView cycleCaption = caption("التكرار");
        Spinner cycle = new AppChoiceSpinner("التكرار", new String[]{"شهري", "سنوي", "أسبوعي"});
        cycle.setPadding(dp(12), 0, dp(12), 0);
        cycle.setBackground(outlined(SURFACE, BORDER, 12));
        page.addView(cycle, new LinearLayout.LayoutParams(-1, dp(48)));
        if (existing != null) cycle.setSelection("yearly".equals(existing.cycle) ? 1 : "weekly".equals(existing.cycle) ? 2 : 0);
        if (suggestion != null) cycle.setSelection("yearly".equals(suggestion.cycle)?1:0);

        caption("التنبيه");
        LinearLayout reminders = new LinearLayout(this);
        page.addView(reminders);
        int[] reminderValues = {1, 3, 0, -1};
        String[] reminderLabels = {"قبلها بيوم", "قبلها بـ3 أيام", "يومها", "بدون"};
        List<TextView> reminderViews = new ArrayList<>();
        Runnable paintReminders = () -> {
            for (int i = 0; i < reminderViews.size(); i++) {
                boolean selected = reminderValues[i] == remind[0];
                TextView option = reminderViews.get(i);
                option.setTextColor(selected ? onPrimary() : INK);
                option.setTypeface(selected ? BOLD : REGULAR);
                option.setBackground(selected ? rounded(primaryFill(), 14) : outlined(SURFACE, BORDER, 14));
            }
        };
        for (int i = 0; i < reminderValues.length; i++) {
            int value = reminderValues[i];
            TextView option = label(reminderLabels[i], 13, INK, false);
            option.setGravity(Gravity.CENTER);
            option.setOnClickListener(v -> { remind[0] = value; paintReminders.run(); });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) optionParams.setMarginStart(dp(6));
            reminders.addView(option, optionParams);
            reminderViews.add(option);
        }
        paintReminders.run();

        List<ExpenseDb.PaymentSource> sources = db.selectableSources();
        Spinner account = spinner("بيتدفع من", sourceNames(sources), page);
        if (existing != null && existing.paymentSourceId != null)
            for (int i = 0; i < sources.size(); i++) if (sources.get(i).id == existing.paymentSourceId) account.setSelection(i + 1);

        LinearLayout hint = new LinearLayout(this); hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(14), dp(12), dp(14), dp(12));
        hint.setBackground(outlined(SURFACE, BORDER, 16));
        hint.addView(tintedIcon(R.drawable.ic_message, EMERALD), new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout.LayoutParams hintText = new LinearLayout.LayoutParams(0, -2, 1); hintText.setMarginStart(dp(10));
        hint.addView(label("لما رسالة الدفع من الجهة دي توصل، حتى لو بتفتحها من تروكولر، الدفعة هتتعلم مدفوعة لوحدها.", 12, MUTED, false), hintText);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2); hintParams.topMargin = dp(16);
        page.addView(hint, hintParams);

        TextView save = label("احفظ الالتزام", 16, onPrimary(), true);
        save.setGravity(Gravity.CENTER); save.setBackground(rounded(primaryFill(), 18));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56)); saveParams.topMargin = dp(20);
        page.addView(save, saveParams);

        String[][] kinds = {{"قسط", "installment"}, {"اشتراك", "subscription"}, {"فاتورة", "bill"}};
        Runnable[] paintKind = new Runnable[1];
        paintKind[0] = () -> {
            segments.removeAllViews();
            for (String[] option : kinds) {
                boolean selected = kind[0].equals(option[1]);
                TextView segment = label(option[0], 13, selected ? INK : MUTED, selected);
                segment.setGravity(Gravity.CENTER);
                if (selected) segment.setBackground(rounded(dark ? BACKGROUND : Color.WHITE, 11));
                segment.setOnClickListener(v -> { kind[0] = option[1]; paintKind[0].run(); });
                segments.addView(segment, new LinearLayout.LayoutParams(0, dp(38), 1));
            }
            installmentBox.setVisibility("installment".equals(kind[0]) ? View.VISIBLE : View.GONE);
            cycle.setVisibility("installment".equals(kind[0]) ? View.GONE : View.VISIBLE);
            cycleCaption.setVisibility(cycle.getVisibility());
            amount.setHint("installment".equals(kind[0]) ? "القسط الشهري" : "المبلغ");
            providers.removeAllViews();
            LinearLayout row = null;
            int index = 0;
            for (UiKit.Provider provider : UiKit.PROVIDERS) {
                if (!provider.kind.equals(kind[0])) continue;
                if (index % 4 == 0) {
                    row = new LinearLayout(this);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.topMargin = dp(8);
                    providers.addView(row, rowParams);
                }
                boolean selected = provider.name.equalsIgnoreCase(name.getText().toString().trim());
                LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER);
                tile.setBackground(selected ? outlined(SURFACE, EMERALD, 16) : outlined(SURFACE, BORDER, 16));
                if (selected) ((GradientDrawable) tile.getBackground()).setStroke(dp(2), EMERALD);
                tile.addView(merchantBadge(provider.name, kindCategory(provider.kind), 32), new LinearLayout.LayoutParams(dp(32), dp(32)));
                TextView caption = label(provider.name, 11, INK, false); caption.setGravity(Gravity.CENTER);
                caption.setSingleLine(true);
                tile.addView(caption);
                tile.setOnClickListener(v -> {
                    name.setText(provider.name);
                    paintKind[0].run();
                });
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(78), 1);
                if (index % 4 > 0) tileParams.setMarginStart(dp(8));
                row.addView(tile, tileParams);
                index++;
            }
            while (row != null && index % 4 != 0) {
                LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, dp(78), 1);
                fillParams.setMarginStart(dp(8));
                row.addView(new View(this), fillParams);
                index++;
            }
            providerCaption.setVisibility(providers.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        };
        paintKind[0].run();

        save.setOnClickListener(v -> {
            if (rejectMisreadAmount(amount)) return;
            String merchant = name.getText().toString().trim();
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
            if (merchant.isEmpty() || minor <= 0) { toast("اكتب اسم الجهة والمبلغ"); return; }
            Integer totalCount = null;
            int paidCount = existing==null?0:existing.paidCount;
            if ("installment".equals(kind[0])) {
                try {
                    String totalText = ExpenseParser.normalizeDigits(total.getText().toString().trim());
                    String paidText = ExpenseParser.normalizeDigits(paid.getText().toString().trim());
                    if (!totalText.isEmpty()) totalCount = Integer.parseInt(totalText);
                    if(suggestion!=null && suggestion.count!=null && paidText.isEmpty()) {toast("حدد كام قسط اتدفع قبل حفظ الخطة المستخرجة");return;}
                    if (!paidText.isEmpty()) paidCount = Integer.parseInt(paidText);
                } catch (NumberFormatException error) { toast("راجع عدد الأقساط"); return; }
                if (paidCount < 0 || totalCount != null && (totalCount <= 0 || paidCount > totalCount)) {
                    toast("الأقساط المدفوعة لازم تكون من صفر لإجمالي الأقساط"); return;
                }
            }
            LocalDate due = chosenDue[0];
            String cycleValue = "installment".equals(kind[0]) ? "monthly" :
                    new String[]{"monthly", "yearly", "weekly"}[Math.max(0, cycle.getSelectedItemPosition())];
            UiKit.Provider provider = UiKit.provider(merchant);
            String match = merchant.startsWith("TRU") ? "tru|ترو" : provider != null ? provider.keywords :
                    existing != null && merchant.equalsIgnoreCase(existing.merchant) ? existing.matchText :
                            merchant.toLowerCase(Locale.ROOT);
            long id;
            try{id = db.putCommitment(existing == null ? null : existing.id, merchant, minor,
                    currency.getSelectedItem().toString(), due, cycleValue, kind[0], totalCount, paidCount, remind[0],
                    match, selectedSource(account, sources));}
            catch(IllegalArgumentException changed){toast("راجع تسويات الدورات قبل تغيير تاريخ الجدول؛ الدفعات والعمليات محفوظة");return;}
            if (id == -1) { toast("الالتزام ده متسجل بالفعل"); return; }
            if (!"weekly".equals(cycleValue)) db.setDueDay(id, dueDay[0]);
            if (remind[0] >= 0) askNotificationPermission();
            if(suggestion!=null) db.acceptSuggestion(suggestion.key,id);
            editingSuggestion=null;
            editingCommitment = null;
            selectedCommitmentId = id;
            selectedTab = 11;
            render();
        });
    }

    private LocalDate nextDueForDay(int day) {
        LocalDate today = LocalDate.now();
        LocalDate candidate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            LocalDate next = today.plusMonths(1).withDayOfMonth(1);
            candidate = next.withDayOfMonth(Math.min(day, next.lengthOfMonth()));
        }
        return candidate;
    }

    private void recurringDialog() {
        openCommitmentEditor(null);
    }

    private void commitmentFromTransaction(ExpenseDb.Transaction item) {
        LinearLayout form=form();EditText name=input("اسم الالتزام",form);name.setText(item.merchant);
        EditText amount=input("المبلغ",form);amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.format(Locale.ROOT,"%.2f",item.amountMinor/100.0));
        Spinner type=spinner("النوع",new String[]{"اشتراك","فاتورة"},form);if("فواتير".equals(item.category))type.setSelection(1);
        LocalDate day=Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate();
        int anchor=day.getDayOfMonth();LocalDate next=day.plusMonths(1);
        while(next.isBefore(LocalDate.now())){java.time.YearMonth m=java.time.YearMonth.from(next).plusMonths(1);next=m.atDay(Math.min(anchor,m.lengthOfMonth()));}
        EditText date=dateInput("موعد الدفعة القادمة",form,next);
        form.addView(label("الموعد مقترح من آخر عملية؛ راجعه. إنشاء الالتزام لا يضيف مصروفًا أو يسجل سدادًا جديدًا.",13,MUTED,false));
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("حوّلها لالتزام شهري").setView(form).setPositiveButton("حفظ الالتزام",null).setNegativeButton("إلغاء",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                if(rejectMisreadAmount(amount))return;
                String merchant=name.getText().toString().trim();long minor=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(amount.getText().toString()));
                LocalDate due=java.util.Objects.requireNonNull(dateValue(date));
                if(merchant.isEmpty()||minor<=0||due.isBefore(LocalDate.now()))throw new IllegalArgumentException();
                long id=db.putCommitmentFromTransaction(item.id,merchant,minor,item.currency,due,type.getSelectedItemPosition()==0?"subscription":"bill");
                if(id<0){toast("التزام بنفس الاسم والمبلغ موجود بالفعل، أو العملية غير متاحة");return;}
                dialog.dismiss();selectedCommitmentId=id;selectedTab=11;ReminderReceiver.schedule(this);askNotificationPermission();render();
            }catch(RuntimeException error){toast("راجع الاسم والمبلغ وموعد الاستحقاق");}
        }));dialog.show();
    }

    private void openWidgetAction(Intent intent) {
        if(intent.getBooleanExtra("open_add_transaction",false)){intent.removeExtra("open_add_transaction");introVisible=false;selectedTab=6;render();manualDialog(null);}
        if(intent.getBooleanExtra("open_daily_budget",false)){intent.removeExtra("open_daily_budget");introVisible=false;selectedTab=0;render();dailyBudgetDialog();}
        if(intent.getBooleanExtra("open_commitments",false)){intent.removeExtra("open_commitments");introVisible=false;selectedTab=2;render();}
    }

    private void dailyHomeCard() {
        DailySummary summary=DailySummary.load(this,db);
        String code=getSharedPreferences("widget_settings",MODE_PRIVATE).getString("currency","EGP");
        DailyAllowance.Result result=summary.currencies.get(code);
        LinearLayout box=card();box.setPadding(dp(14),dp(9),dp(14),dp(9));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("ميزانية اليوم · "+code,12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        String salary=result==null||result.daysToSalary<0?"حدد موعد الراتب":result.daysToSalary==0?"الراتب النهارده":result.daysToSalary+" يوم للراتب";
        heading.addView(label(salary,12,ACCENT,true));box.addView(heading);
        box.addView(label(summary.remaining(code,false),20,result!=null&&result.valid&&result.remaining<0?CORAL:ACCENT,true));
        if(result!=null&&result.valid){View track=new View(this);box.addView(track,new LinearLayout.LayoutParams(-1,dp(4)));fillBar(track,result.expense,result.plan.limit,ACCENT,BORDER);}
        box.addView(label("مصروف اليوم "+money(result==null?0:result.expense,code)+" · التفاصيل والعملات ←",11,MUTED,false));
        TextView points=label("نقاط الالتزام · اختياري ←",12,ACCENT,true);
        if(DailyPointsStore.enabled(this,code)) {
            List<DailyPoints.Day> days=DailyPointsStore.load(this,db,code,LocalDate.now());
            String balance=pointsNumber(DailyPoints.total(days));
            String pointsCaption="رصيد النقاط "+balance+" ←";
            for(DailyPoints.Day day:days)if(day.date.equals(LocalDate.now()))pointsCaption="رصيدك "+balance+" · النهارده "+pointsNumber(day.points())+" مؤقتة ←";
            points.setText(pointsCaption);
        }
        points.setPadding(0,dp(8),0,dp(4));points.setOnClickListener(v->pointsDialog(code));box.addView(points);
        box.setOnClickListener(v->dailyDetailsDialog());
    }

    private void dailyDetailsDialog() {
        dailyDetailsContent=form();fillDailyDetails();
        dailyDetails=new ChoiceDialogBuilder().setTitle("ميزانية النهارده")
                .setView(scrollForm(dailyDetailsContent)).setPositiveButton("تعديل الخطة",(d,w)->dailyBudgetDialog())
                .setNeutralButton("صرف النهارده",(d,w)->openDailyTransactions()).setNegativeButton("إغلاق",null).create();
        dailyDetails.setOnDismissListener(d->{dailyDetails=null;dailyDetailsContent=null;});dailyDetails.show();
    }

    private void openDailyTransactions() {
        int origin=selectedTab;resetTransactionFilters();LocalDate today=LocalDate.now();
        selectedMonth=today.withDayOfMonth(1);transactionPeriod=new LocalDate[]{today,today.plusDays(1)};
        transactionBucketFilter=MoneyFlow.Bucket.LIVING.name();
        transactionCurrencyFilter=getSharedPreferences("widget_settings",MODE_PRIVATE).getString("currency","EGP");
        transactionsBackTab=origin;transactionsReturnDialog="daily";selectedTab=6;render();
    }

    private void fillDailyDetails() {
        if(dailyDetailsContent==null)return;dailyDetailsContent.removeAllViews();DailySummary summary=DailySummary.load(this,db);
        for(Map.Entry<String,DailyAllowance.Result> e:summary.currencies.entrySet()) {
            String code=e.getKey();DailyAllowance.Result r=e.getValue();
            dailyDetailsContent.addView(label(summary.remaining(code,false)+" · "+code,18,INK,true));
            dailyDetailsContent.addView(label("مصروف اليوم: "+money(r.expense,code)+"\nدخل اليوم: "+money(r.income,code),14,MUTED,false));
            if(r.valid) {
                dailyDetailsContent.addView(label("الحد اليومي: "+money(r.plan.limit,code),14,ACCENT,true));
                progress(dailyDetailsContent,r.expense,r.plan.limit);
                dailyDetailsContent.addView(label(r.daysToSalary<0?"موعد الراتب غير محدد":r.daysToSalary==0?"موعد الراتب المتوقع النهارده":r.daysToSalary+" يوم حتى الراتب المتوقع · "+r.nextSalary,13,MUTED,false));
                if(r.cycleStart!=null)dailyDetailsContent.addView(label("دورة الراتب: "+r.cycleStart+" إلى "+r.cycleEnd.minusDays(1)+" (اليوم الأخير شامل)",12,MUTED,false));
            }else {if(r.plan!=null)dailyDetailsContent.addView(label("الخطة تبدأ "+r.plan.starts,12,MUTED,false));
                if(r.daysToSalary>=0)dailyDetailsContent.addView(label(r.daysToSalary+" يوم حتى الراتب المتوقع · "+r.nextSalary,13,MUTED,false));}
            long monthly=DailySummary.monthlySalary(this,code);if(monthly>0)dailyDetailsContent.addView(label("الراتب الشهري المخطط: "+money(monthly,code)+" · ليس دخلًا مسجلًا",13,MUTED,false));
            TextView points=label("نقاط الالتزام · "+code+" ←",13,ACCENT,true);
            points.setPadding(0,dp(8),0,dp(12));points.setOnClickListener(v->{if(dailyDetails!=null)dailyDetails.dismiss();pointsDialog(code);});dailyDetailsContent.addView(points);
        }
        dailyDetailsContent.addView(label("مصروف اليوم = صرف يومي بس (شراء ورسوم). سحب الكاش اتنقل من حسابك لكاش ومش مصروف. الأقساط والفواتير والتحويلات لناس ليها سطر لوحدها في التحليلات ومش بتاكل من الحد اليومي. الدخل والتحويلات الداخلية لا تزود الحد. المتبقي لا ينتقل لبكرة. كل عملة لها حد منفصل.",12,MUTED,false));
        dailyDetailsContent.addView(label("اليوم من 00:00 حتى 00:00 التالي حسب "+summary.zone+" · تحديث "+summary.date+" "+summary.updated.atZone(summary.zone).toLocalTime().withNano(0),11,MUTED,false));
    }

    private void pointsDialog(String code) {pointsDialog(code,false);}

    private String pointsNumber(java.math.BigDecimal points) {
        return android.text.BidiFormatter.getInstance(true).unicodeWrap(DailyPoints.format(points),android.text.TextDirectionHeuristics.LTR);
    }

    private void pointsDialog(String code,boolean all) {
        DailyPointsStore.observeToday(this);LocalDate today=LocalDate.now();boolean enabled=DailyPointsStore.enabled(this,code);
        long revision=db.changes();
        List<DailyPoints.Day> days=DailyPointsStore.load(this,db,code,today);
        DailyPoints.Day current=null,yesterday=null;int waiting=0,confirmed=0;
        for(DailyPoints.Day day:days) {
            if(day.date.equals(today))current=day;
            if(day.date.equals(today.minusDays(1)))yesterday=day;
            if(day.finalized)confirmed++;
            else if(day.date.isBefore(today))waiting++;
        }
        java.math.BigDecimal balance=DailyPoints.total(days);
        LinearLayout content=form();
        LinearLayout hero=card(content);hero.setBackground(rounded(BRAND,24));
        hero.addView(label("★  رصيدك المعتمد",14,LIME,true));
        TextView score=label(pointsNumber(balance)+" نقطة",40,Color.WHITE,true);hero.addView(score);
        hero.addView(label(confirmed==0?"أول يوم مكتمل هو بداية رصيدك":confirmed+" يوم مكتمل · نقاط النهارده لسه برّه الرصيد",12,Color.WHITE,false));
        java.math.BigDecimal hundred=java.math.BigDecimal.valueOf(100);
        java.math.BigDecimal positive=balance.max(java.math.BigDecimal.ZERO);
        java.math.BigDecimal base=positive.divideToIntegralValue(hundred).multiply(hundred);
        java.math.BigDecimal target=base.add(hundred);
        hero.addView(label("محطتك الجاية: "+target.toPlainString()+" نقطة",14,LIME,true));
        progress(hero,positive.subtract(base).longValueExact(),100,LIME);
        hero.addView(label("فاضلك "+target.subtract(balance).toPlainString()+" نقطة · كل يوم ملتزم يقرّبك",12,Color.WHITE,false));
        if(!enabled)content.addView(label("النقاط متوقفة · رصيدك محفوظ",13,MUTED,true));

        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout todayTile=pointsSummaryTile("النهارده",current,true,enabled);
        LinearLayout yesterdayTile=pointsSummaryTile("امبارح",yesterday,false,enabled);
        LinearLayout.LayoutParams todayParams=new LinearLayout.LayoutParams(0,-1,1);todayParams.setMarginEnd(dp(8));
        summary.addView(todayTile,todayParams);summary.addView(yesterdayTile,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams summaryParams=new LinearLayout.LayoutParams(-1,-2);summaryParams.topMargin=dp(12);content.addView(summary,summaryParams);
        if(current!=null)content.addView(label((current.difference<0?"فوق حدك النهارده: ":"المتبقي من حدك النهارده: ")+money(Math.abs(current.difference),code),13,current.difference<0?CORAL:MUTED,false));
        if(waiting>0)content.addView(label(waiting+" يوم برّه الرصيد بسبب عمليات محتاجة مراجعة",13,MUTED,true));
        long unknown=DailyPointsStore.unknownDays(this,code,today);
        if(unknown>0)content.addView(label(unknown+" يوم قديم خارج الحساب لأن الحد التاريخي مش معروف؛ مش هنفترض له حد النهارده.",13,MUTED,true));
        content.addView(label("النقاط تعتمد على العمليات المسجلة؛ لو رسالة وصلت متأخر أو عملية اتصححت، يومها بيتحسب من جديد تلقائيًا.",12,MUTED,false));

        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("نقاط الالتزام · "+code).setView(scrollForm(content))
                .setPositiveButton("إغلاق",null).setNeutralButton(all?"رجوع للملخص":"سجل الأيام",(d,w)->pointsDialog(code,!all))
                .setNegativeButton(enabled?"إيقاف النقاط":"تفعيل النقاط",(d,w)->{
                    if(enabled){DailyPointsStore.disable(this,code);render();toast("اتوقفت النقاط؛ السجل محفوظ");}
                    else {try{DailyPointsStore.enable(this,code,today);render();pointsDialog(code);}
                        catch(IllegalArgumentException noPlan){toast("حدد حد يومي ساري للعملة دي الأول");dailyBudgetDialog();}}
                }).create();
        if(!all) {
            if(yesterday!=null)pointsDayActions(content,yesterday,code,today,dialog,false);
            if(current!=null)pointsDayActions(content,current,code,today,dialog,false);
        }else {
            content.addView(label("سجل الأيام · "+code,16,INK,true));
            for(int i=days.size()-1;i>=0;i--) {
                DailyPoints.Day day=days.get(i);LinearLayout row=card(content);
                row.addView(label(dayLabel(day.date)+" · "+pointsNumber(day.points())+" نقطة",16,day.difference<0?CORAL:ACCENT,true));
                row.addView(label("حد اليوم "+money(day.limit,code)+" · صافي الصرف "+money(day.spending,code),12,MUTED,false));
                row.addView(label(day.finalized?"معتمدة · ضمن رصيدك":day.date.equals(today)?"مؤقتة · لسه برّه الرصيد":day.needsReview?"راجع العمليات أولًا · برّه الرصيد":"الحد التاريخي غير معروف · برّه الرصيد",12,MUTED,false));
                pointsDayActions(row,day,code,today,dialog,true);
            }
        }
        TextView help=label("النقاط بتتحسب إزاي؟",13,ACCENT,true);help.setPadding(0,dp(12),0,dp(8));
        help.setOnClickListener(v->new ChoiceDialogBuilder().setTitle("طريقة حساب النقاط")
                .setMessage("كل 10 وحدات من "+code+" تحت حد اليوم = نقطة، والزيادة تخصم بنفس النسبة. نتيجة كل يوم بتتقرب لأقرب عدد صحيح؛ نصف نقطة يتقرب بعيدًا عن الصفر. رصيدك هو مجموع نتائج الأيام المعتمدة.\n\nالنهارده مؤقت. اليوم يدخل الرصيد تلقائيًا بعد نهايته حسب العمليات المسجلة، من غير قفل أو تأكيد يومي. سحب الكاش مش مصروف ومش داخل في الحساب. العمليات المحتاجة مراجعة تمنع اعتماد اليوم.\n\nحد النقاط بيتثبت أول مرة في اليوم؛ تعديل الميزانية يسري على حد النقاط من بكرة. الاسترداد يصحح يوم الشراء. لو التطبيق ما اتفتحش، الأيام بتتلحق من سجل الحد الساري وقتها. الأيام القديمة ذات الحد غير المعروف تفضل خارج الرصيد.\n\nمحطات الـ100 نقطة للتشجيع فقط. النقاط مش فلوس أو مكافآت نقدية، وكل عملة لها رصيد منفصل.")
                .setPositiveButton("تمام",null).show());content.addView(help);
        if(days.isEmpty())content.addView(label("فعّل النقاط وابدأ بالنهارده. أول رصيد معتمد يظهر تلقائيًا بعد نهاية اليوم.",13,MUTED,false));
        dialog.setOnDismissListener(d->{if(pointsSheet==dialog)pointsSheet=null;});
        pointsSheet=dialog;pointsSheetCurrency=code;pointsSheetAll=all;pointsSheetRevision=revision;pointsSheetDate=today;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(ACCENT);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(enabled?MUTED:ACCENT);
    }

    private LinearLayout pointsSummaryTile(String title,DailyPoints.Day day,boolean current,boolean enabled) {
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(12),dp(12),dp(12),dp(12));
        tile.setBackground(rounded(current?ACCENT_PALE:SURFACE,18));
        tile.addView(label(title,14,INK,true));
        tile.addView(label(day==null?"—":pointsNumber(day.points())+" نقطة",24,day!=null&&day.difference<0?CORAL:ACCENT,true));
        String status=day==null?(current&&!enabled?"فعّل النقاط لتبدأ":"مفيش يوم مسجل"):current?"مؤقتة · برّه الرصيد":day.finalized?"معتمدة · ضمن رصيدك":day.needsReview?"محتاج مراجعة":"الحد التاريخي غير معروف";
        tile.addView(label(status,12,MUTED,false));return tile;
    }

    private void pointsDayActions(LinearLayout parent,DailyPoints.Day day,String code,LocalDate today,AlertDialog dialog,boolean all) {
        TextView operations=label(all?"راجع عمليات اليوم ←":day.date.equals(today)?"راجع عمليات اليوم ←":"راجع عمليات امبارح ←",13,ACCENT,true);
        operations.setPadding(0,dp(10),0,dp(8));operations.setMinHeight(dp(48));
        operations.setOnClickListener(v->{
            dialog.dismiss();int origin=selectedTab;resetTransactionFilters();selectedMonth=day.date.withDayOfMonth(1);
            transactionPeriod=new LocalDate[]{day.date,day.date.plusDays(1)};transactionCurrencyFilter=code;
            transactionsBackTab=origin;transactionsReturnDialog="points";transactionsPointsCurrency=code;selectedTab=6;render();
        });parent.addView(operations);
    }

    private void dailyBudgetDialog() {
        LinearLayout form=form();form.addView(label("الميزانية اليومية ثابتة. الراتب الشهري للتخطيط فقط؛ لا يُسجّل كدخل تلقائيًا. كل عملة منفصلة.",13,MUTED,false));
        Spinner currency=spinner("العملة",CURRENCIES,form);EditText amount=input("الحد اليومي (اختياري)",form);decimalInput(amount);
        EditText starts=dateInput("بداية الخطة",form,LocalDate.now());EditText salaryAmount=input("الراتب الشهري (اختياري)",form);decimalInput(salaryAmount);
        Spinner salary=spinner("ميعاد القبض المتوقع",salaryDayLabels(),form);
        form.addView(label("آخر يوم في الشهر يتبع 28/29/30/31. اليوم المحدد الذي لا يوجد في شهر قصير يُحسب آخر يوم فيه.",12,MUTED,false));
        java.util.function.Consumer<String> populate=code->{DailyAllowance.Plan plan=DailySummary.plan(this,code);
            amount.setText(plan==null?"":java.math.BigDecimal.valueOf(plan.limit,2).toPlainString());
            setDateValue(starts,plan==null?LocalDate.now():plan.starts);
            long monthly=DailySummary.monthlySalary(this,code);salaryAmount.setText(monthly==0?"":java.math.BigDecimal.valueOf(monthly,2).toPlainString());
            salary.setSelection(salarySelection(getSharedPreferences(DailySummary.SETTINGS,MODE_PRIVATE).getInt("salary_"+code,0)));};
        currency.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){populate.accept(CURRENCIES[position]);}});
        populate.accept(currency.getSelectedItem().toString());
        AlertDialog dialog=new ChoiceDialogBuilder().setTitle("ميزانية اليوم والراتب").setView(scrollForm(form))
                .setPositiveButton("حفظ",null).setNegativeButton("إلغاء",null).setNeutralButton("حذف الحد اليومي",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {String daily=amount.getText().toString().trim(),monthly=salaryAmount.getText().toString().trim(),code=currency.getSelectedItem().toString();
                if(rejectMisreadAmount(daily)||rejectMisreadAmount(monthly))return;
                long limit=optionalMinor(daily),salaryValue=optionalMinor(monthly);int day=selectedSalaryDay(salary.getSelectedItemPosition());
                LocalDate start=java.util.Objects.requireNonNull(dateValue(starts));
                if(!daily.isEmpty())DailySummary.save(this,new DailyAllowance.Plan(code,limit,start,day));else DailySummary.remove(this,code);
                DailySummary.saveSalary(this,code,salaryValue,day);dialog.dismiss();render();
            }catch(RuntimeException invalid){toast("راجع المبلغ اليومي والراتب وتاريخ البداية");}
        });dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new ChoiceDialogBuilder().setTitle("حذف حد "+currency.getSelectedItem()+" اليومي؟")
                .setMessage("الراتب وميعاده والعمليات محفوظون؛ هنلغي الحد اليومي فقط.").setPositiveButton("حذف",(confirm,w)->{DailySummary.remove(this,currency.getSelectedItem().toString());dialog.dismiss();render();})
                .setNegativeButton("إلغاء",null).show());});dialog.show();
    }

    private String[] salaryDayLabels() {
        String[] choices=new String[33];choices[0]="حدد لاحقًا";choices[1]="آخر يوم في الشهر";for(int i=1;i<=31;i++)choices[i+1]="يوم "+i+" من كل شهر";return choices;
    }
    private int selectedSalaryDay(int selection){return selection==1?DailyAllowance.MONTH_END:selection>1?selection-1:0;}
    private int salarySelection(int day){return day==DailyAllowance.MONTH_END?1:day>0?day+1:0;}
    private void decimalInput(EditText view){view.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);}
    private boolean rejectMisreadAmount(String raw){if(!ExpenseParser.manualAmountMisread(raw))return false;toast("راجع المبلغ");return true;}
    private boolean rejectMisreadAmount(EditText field){return rejectMisreadAmount(field.getText().toString());}
    private long optionalMinor(String raw){if(raw.isBlank())return 0;if(ExpenseParser.manualAmountMisread(raw))throw new IllegalArgumentException("Ambiguous amount");long value=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(raw));if(value<=0)throw new IllegalArgumentException("Invalid amount");return value;}
    private void bindIntro(EditText input,java.util.function.Consumer<String> update) {
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void afterTextChanged(Editable text){}public void onTextChanged(CharSequence text,int start,int before,int count){update.accept(text.toString());}});
    }
    private ImageView profileAvatar(int size) {
        ImageView image=new ImageView(this);java.io.File photo=new java.io.File(getFilesDir(),"profile-avatar.png");
        android.graphics.Bitmap bitmap=photo.exists()?android.graphics.BitmapFactory.decodeFile(photo.getAbsolutePath()):null;
        if(bitmap==null)image.setImageResource(R.drawable.ic_launcher);else {image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(rounded(BORDER,size/2));image.setClipToOutline(true);}
        image.setContentDescription("الصورة الشخصية");return image;
    }
    private void chooseProfilePhoto() {
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(pick,PROFILE_PHOTO_REQUEST);
    }
    private void saveProfilePhoto(Uri uri) {
        try {
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
            try(InputStream stream=getContentResolver().openInputStream(uri)){android.graphics.BitmapFactory.decodeStream(stream,null,options);}
            if(options.outWidth<=0||options.outHeight<=0)throw new IllegalArgumentException("Not an image");
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;android.graphics.Bitmap bitmap;
            try(InputStream stream=getContentResolver().openInputStream(uri)){bitmap=android.graphics.BitmapFactory.decodeStream(stream,null,options);}
            if(bitmap==null)throw new IllegalArgumentException("Invalid image");
            try {
                int orientation;
                try(InputStream stream=getContentResolver().openInputStream(uri)){orientation=new android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}
                android.graphics.Matrix matrix=new android.graphics.Matrix();
                if(orientation==2)matrix.setScale(-1,1);else if(orientation==3)matrix.setRotate(180);else if(orientation==4){matrix.setRotate(180);matrix.postScale(-1,1);}
                else if(orientation==5){matrix.setRotate(90);matrix.postScale(-1,1);}else if(orientation==6)matrix.setRotate(90);else if(orientation==7){matrix.setRotate(-90);matrix.postScale(-1,1);}else if(orientation==8)matrix.setRotate(-90);
                if(!matrix.isIdentity()){android.graphics.Bitmap rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
            }catch(java.io.IOException ignored){}
            java.io.File temp=new java.io.File(getFilesDir(),"profile-avatar.tmp"),photo=new java.io.File(getFilesDir(),"profile-avatar.png");
            try(OutputStream out=new java.io.FileOutputStream(temp)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Image save failed");}finally{bitmap.recycle();}
            java.nio.file.Files.move(temp.toPath(),photo.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);render();
        }catch(Exception invalid){toast("تعذر قراءة الصورة. اختر صورة أخرى.");}
    }
    private void introProfileScreen() {
        if(!introDraftLoaded){introDraftLoaded=true;introName=getPreferences(MODE_PRIVATE).getString("profile_name","");String code=CURRENCIES[introCurrency];DailyAllowance.Plan plan=DailySummary.plan(this,code);
            introDaily=plan==null?"":java.math.BigDecimal.valueOf(plan.limit,2).toPlainString();long monthly=DailySummary.monthlySalary(this,code);
            introSalary=monthly==0?"":java.math.BigDecimal.valueOf(monthly,2).toPlainString();introPayDay=getSharedPreferences(DailySummary.SETTINGS,MODE_PRIVATE).getInt("salary_"+code,0);}
        page.addView(label("خلي التطبيق على مقاسك",24,INK,true));page.addView(label("كل الحقول اختيارية. تقدر تتخطى وتعدلها من الإعدادات.",13,MUTED,false));
        LinearLayout form=card();LinearLayout photo=new LinearLayout(this);photo.setGravity(Gravity.CENTER_VERTICAL);
        photo.addView(profileAvatar(48),new LinearLayout.LayoutParams(dp(48),dp(48)));TextView pick=label("اختار صورتك · اختياري",14,ACCENT,true);
        pick.setPadding(dp(14),dp(8),dp(14),dp(8));pick.setOnClickListener(v->chooseProfilePhoto());photo.addView(pick);form.addView(photo);
        EditText name=input("اسمك (اختياري)",form);name.setText(introName);bindIntro(name,value->introName=value);
        Spinner code=spinner("العملة",CURRENCIES,form);code.setSelection(introCurrency);
        code.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){introCurrency=position;}});
        form.addView(label("ميزانية اليوم · اختياري",13,MUTED,true));
        EditText daily=input("ميزانية اليوم (اختياري)",form);decimalInput(daily);daily.setText(introDaily);bindIntro(daily,value->introDaily=value);
        form.addView(label("الراتب الشهري · اختياري",13,MUTED,true));
        EditText salary=input("الراتب الشهري (اختياري)",form);decimalInput(salary);salary.setText(introSalary);bindIntro(salary,value->introSalary=value);
        Spinner payday=spinner("ميعاد القبض المتوقع",salaryDayLabels(),form);payday.setSelection(salarySelection(introPayDay));
        payday.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){introPayDay=selectedSalaryDay(position);}});
        form.addView(label("آخر الشهر حسب 28/29/30/31 يوم. الراتب للتخطيط؛ لا نضيفه كدخل. الحد اليومي ثابت ولا يترحّل.",12,MUTED,false));
        TextView begin=label("حفظ وابدأ",17,LIME,true);begin.setGravity(Gravity.CENTER);begin.setPadding(dp(16),dp(14),dp(16),dp(14));begin.setBackground(rounded(BRAND,16));
        begin.setOnClickListener(v->{try{if(rejectMisreadAmount(introDaily)||rejectMisreadAmount(introSalary))return;long limit=optionalMinor(introDaily),monthly=optionalMinor(introSalary);String currency=CURRENCIES[introCurrency];
            if(limit>0)DailySummary.save(this,new DailyAllowance.Plan(currency,limit,LocalDate.now(),introPayDay));
            DailySummary.saveSalary(this,currency,monthly,introPayDay);getPreferences(MODE_PRIVATE).edit().putString("profile_name",introName.trim()).apply();
            getSharedPreferences("widget_settings",MODE_PRIVATE).edit().putString("currency",currency).apply();finishIntro(true);
        }catch(RuntimeException invalid){toast("راجع ميزانية اليوم والراتب، أو تخطى الإعداد");}});page.addView(begin,new LinearLayout.LayoutParams(-1,-2));
    }

    private void introScreen() {
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);top.addView(logo,new LinearLayout.LayoutParams(dp(36),dp(36)));
        top.addView(label("Follow Money",20,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView skip=label("تخطي",14,ACCENT,true);skip.setPadding(dp(12),dp(12),dp(12),dp(12));skip.setOnClickListener(v->finishIntro(false));top.addView(skip);page.addView(top);
        page.addView(centered(label("بكل فخر، صُنع في مصر 🇪🇬",12,MUTED,false)));
        LinearLayout trail=new LinearLayout(this);
        for(int i=0;i<4;i++){View step=new View(this);step.setBackground(rounded(i<=introStep?ACCENT:BORDER,4));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(4),1);sp.setMargins(dp(3),dp(12),dp(3),dp(8));trail.addView(step,sp);}page.addView(trail);
        TextView steps=label((introStep+1)+" / 4",13,MUTED,false);steps.setGravity(Gravity.CENTER);page.addView(steps);
        if(introStep==3){introProfileScreen();return;}
        String[] titles={"عملياتك بتتسجل لوحدها","اعرف فلوسك راحت فين","راجع قبل ما تضيف"};
        String[] copy={"اختار SMS أو إشعارات تطبيقات الدفع. الجديد يتسجل على موبايلك، وتقدر تراجع مصدر كل عملية.","دخل ومصروف اليوم، اسم التاجر وتصنيفه، والعملة واضحة في مكان واحد.","اختار فترة الرسائل القديمة، شوف النتائج، وحدد اللي عايز تحفظه. الفحص وحده لا يغيّر بياناتك."};
        LinearLayout demo=card();demo.setGravity(Gravity.CENTER);demo.setPadding(dp(20),dp(30),dp(20),dp(30));
        demo.addView(centered(label("مثال توضيحي · بيانات تجريبية",11,MUTED,false)));
        if(introStep==0) {
            demo.setBackground(rounded(BRAND,18));
            ((TextView)demo.getChildAt(0)).setTextColor(LIME);
            demo.addView(tintedIcon(R.drawable.ic_message,LIME),new LinearLayout.LayoutParams(-1,dp(60)));
            demo.addView(centered(label("شراء 185.50 ج.م لدى كارفور",16,Color.WHITE,true)));
            demo.addView(centered(label("SMS  ←  عملية مصنفة",14,LIME,true)));
        } else if(introStep==1) {
            demo.addView(centered(label("النهارده",16,INK,true)));
            demo.addView(centered(label("دخل 2,000 ج.م · صرف 185.50 ج.م",16,INCOME,true)));
            demo.addView(merchantBadge("Carrefour","بقالة",64),new LinearLayout.LayoutParams(dp(64),dp(64)));
            demo.addView(centered(label("كارفور · بقالة",15,INK,true)));
        } else {
            demo.addView(centered(label("وجدنا 12 عملية للمراجعة",19,INK,true)));
            demo.addView(centered(label("✓  شراء كارفور\n✓  اشتراك Netflix\n○  عملية غير محددة",16,ACCENT,false)));
            demo.addView(centered(label("الحفظ بعد اختيارك وتأكيدك",13,MUTED,false)));
        }
        page.addView(centered(label(titles[introStep],26,INK,true)));
        page.addView(centered(label(copy[introStep],16,MUTED,false)));
        TextView next=label("التالي",17,LIME,true);next.setGravity(Gravity.CENTER);next.setPadding(dp(18),dp(17),dp(18),dp(17));next.setBackground(rounded(BRAND,16));
        next.setOnClickListener(v->{introStep++;render();});
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(24);page.addView(next,params);
        page.addView(centered(label("بياناتك محلية · تقدر تستخدم التطبيق يدويًا",12,MUTED,false)));
        float animations=Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);
        if(animations>0){demo.setAlpha(0);demo.setTranslationY(dp(10));demo.animate().alpha(1).translationY(0).setDuration(240).start();}
    }

    private void finishIntro(boolean choose) {
        getPreferences(MODE_PRIVATE).edit().putBoolean("intro_completed",true).putBoolean("sms_permission_prompted",true).apply();
        introVisible=false;selectedTab=0;render();
        if(!choose)return;
        new ChoiceDialogBuilder().setTitle("تبدأ تسجيل عملياتك إزاي؟")
                .setMessage("الراتب وإعداد الميزانية اختياريان. اختر مصدر التسجيل؛ تقدر تضيف مصدرًا آخر من الإعدادات.")
                .setPositiveButton("رسائل SMS",(d,w)->requestSmsImport())
                .setNeutralButton("إشعارات الدفع",(d,w)->openNotificationSettings())
                .setNegativeButton("إدخال يدوي",null).show();
    }

    private void confirmRecurring(ExpenseDb.Transaction item) {
        LocalDate last = Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate due = last.plusMonths(1);
        while (due.isBefore(LocalDate.now())) due = due.plusMonths(1);
        LocalDate nextDue = due;
        String kind = "أقساط".equals(item.category) ? "installment" : "فواتير".equals(item.category) ? "bill" : "subscription";
        new ChoiceDialogBuilder().setTitle("نسجّله التزام شهري؟")
                .setMessage(item.merchant + " · " + money(item.amountMinor, item.currency) +
                        "\nموعد تقديري من آخر سداد: " + nextDue.format(DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("ar"))) +
                        "\n\nهنفكّرك قبلها بيوم، وتقدر تعدل أو تلغي في أي وقت.")
                .setPositiveButton("سجّله", (dialog, which) -> {
                    UiKit.Provider provider = UiKit.provider(item.merchant);
                    long id = db.putCommitment(null, item.merchant, item.amountMinor, item.currency, nextDue, "monthly",
                            provider != null ? provider.kind : kind, null, 0, 1,
                            provider != null ? provider.keywords : item.merchant.toLowerCase(Locale.ROOT), item.paymentSourceId);
                    if (id == -1) toast("الالتزام ده متسجل بالفعل");
                    askNotificationPermission();
                    render();
                    refreshLedger(true); // the new commitment removes its merchant from the suggestions
                }).setNegativeButton("إلغاء", null).show();
    }

    private LinearLayout card() { return card(page); }

    private LinearLayout card(LinearLayout container) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        row.setBackground(rounded(SURFACE, 20));
        container.addView(row, params);
        return row;
    }

    /** A white rounded group whose children are separated by hairlines, like a settings list. */
    private LinearLayout listCard(LinearLayout container) {
        LinearLayout list = card(container);
        list.setPadding(dp(14), dp(2), dp(14), dp(2));
        return list;
    }

    private void divider(LinearLayout container) {
        View line = new View(this); line.setBackgroundColor(BORDER);
        container.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2 + 1)));
    }

    private View centered(TextView view) { view.setGravity(Gravity.CENTER); return view; }

    private TextView caption(String text) {
        TextView caption = label(text, 12, MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(16);
        page.addView(caption, params);
        return caption;
    }

    private ImageView tintedIcon(int res, int color) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(res);
        icon.setImageTintList(ColorStateList.valueOf(color));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return icon;
    }

    private ImageView iconButton(int res, String description, Runnable action) {
        ImageView button = tintedIcon(res, INK);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackground(outlined(SURFACE, BORDER, 14));
        button.setContentDescription(description);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private ImageView primaryIconButton(int res, String description, Runnable action) {
        ImageView button = tintedIcon(res, LIME);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setBackground(rounded(BRAND, 14));
        button.setContentDescription(description);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView pill(String text, int background, int color) {
        TextView pill = label(text, 11, color, true);
        pill.setPadding(dp(9), dp(2), dp(9), dp(2));
        pill.setBackground(rounded(background, 99));
        pill.setGravity(Gravity.CENTER);
        return pill;
    }

    private TextView addChip(LinearLayout row, String text, boolean selected, Runnable action) {
        TextView chip = label(text, 13, selected ? onPrimary() : INK, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(selected ? rounded(primaryFill(), 99) : outlined(SURFACE, BORDER, 99));
        chip.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(38)); params.setMarginEnd(dp(8));
        row.addView(chip, params);
        return chip;
    }

    /** Background, ink and icon for a category, shared by pills and badges. */
    private int[] categoryStyle(String category) {
        int background, ink, icon;
        switch (category == null ? "" : category) {
            case "مقاهي": background = 0xfff2eadc; ink = 0xff795638; icon = R.drawable.ic_cat_cafe; break;
            case "أكل وشرب": background = 0xfffff1e3; ink = 0xff9a4a08; icon = R.drawable.ic_cat_food; break;
            case "تسوق": background = 0xfffce8f1; ink = 0xff962a5e; icon = R.drawable.ic_cat_shop; break;
            case "بقالة": background = 0xffe7f5ec; ink = 0xff1e7a45; icon = R.drawable.ic_cat_grocery; break;
            case "مواصلات": background = 0xffe6f0fb; ink = 0xff2563a8; icon = R.drawable.ic_cat_transport; break;
            case "فواتير": background = 0xfffff7d6; ink = 0xff7a5c00; icon = R.drawable.ic_cat_bills; break;
            case "اشتراكات": background = 0xffefeafb; ink = 0xff5b3fb0; icon = R.drawable.ic_cat_subscription; break;
            case "أقساط": background = 0xffe3ebfb; ink = 0xff1f3a93; icon = R.drawable.ic_cat_installment; break;
            case "رعاية صحية":
            case "صحة": background = 0xfffde8e8; ink = 0xffa82c2c; icon = R.drawable.ic_cat_health; break;
            case "رسوم تحويل":
            case "تحويلات": background = 0xffeef0ed; ink = 0xff4a5551; icon = R.drawable.ic_cat_transfer; break;
            case "رسوم بنكية": background = 0xfffff7d6; ink = 0xff7a5c00; icon = R.drawable.ic_cat_bills; break;
            case "سحب نقدي": background = 0xffedeff9; ink = 0xff3b4ba8; icon = R.drawable.ic_cat_atm; break;
            case "سكن وإيجار": background = 0xffe8eef1; ink = 0xff3d5a66; icon = R.drawable.ic_cat_home; break;
            case "عائلة ومساعدات": background = 0xfff4ecf4; ink = 0xff7a3d6b; icon = R.drawable.ic_cat_family; break;
            case "دخل": background = 0xffe7f5ec; ink = 0xff106b47; icon = R.drawable.ic_cat_income; break;
            default: background = 0xffeef0ed; ink = 0xff4a5551; icon = R.drawable.ic_cat_other;
        }
        if (dark) { background = (ink & 0x00ffffff) | 0x4d000000; ink = 0xffeef3ef; }
        return new int[]{background, ink, icon};
    }

    private TextView instaPayPill() {
        TextView pill = label("إنستاباي", 11, dark ? 0xffe0d4ff : 0xff5b21b6, false);
        pill.setPadding(dp(8), dp(1), dp(8), dp(1));
        pill.setBackground(rounded(dark ? 0x4d7c3aed : 0xffede4ff, 99));
        return pill;
    }

    private TextView categoryPill(String category) {
        int[] style = categoryStyle(category);
        TextView pill = label(category, 11, style[1], false);
        pill.setPadding(dp(8), dp(1), dp(8), dp(1));
        pill.setBackground(rounded(style[0], 99));
        return pill;
    }

    private int categoryColor(String category) {
        int[] style = categoryStyle(category);
        return dark ? (style[0] | 0xff000000) : style[1];
    }

    private int kindColor(String kind) {
        return "income".equals(kind) ? INCOME : "expense".equals(kind) ? CORAL : MUTED;
    }

    private int kindColor(ExpenseDb.Recurring item) {
        return "installment".equals(item.kind) ? 0xff1f3a93 : "bill".equals(item.kind) ? CORAL : 0xff5b3fb0;
    }

    private String signedMoney(ExpenseDb.Transaction item) {
        String sign = "income".equals(item.kind) ? "+" : "expense".equals(item.kind) ? "−" : "";
        return sign + money(item.amountMinor, item.currency);
    }

    static String dueWord(LocalDate due) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), due);
        if (days == 0) return "النهارده";
        if (days == 1) return "بكرة";
        if (days < 0) return "متأخر " + (-days) + " يوم";
        if (days < 7) return "بعد " + days + " أيام";
        return due.format(DateTimeFormatter.ofPattern("d MMMM", new Locale("ar")));
    }

    static String transactionTimeLabel(ExpenseDb.Transaction item,boolean fullDate) {
        boolean dateOnly="date_only".equals(item.timePrecision);
        if(dateOnly&&!fullDate)return "تاريخ فقط";
        String pattern=fullDate?(dateOnly?"EEEE d MMMM yyyy":"EEEE d MMMM yyyy · h:mm a"):"h:mm a";
        String value=Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(pattern,new Locale("ar")));
        return dateOnly?value+" · تاريخ فقط":value;
    }

    private String dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        String date = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", new Locale("ar")));
        if (day.equals(today)) return "النهارده · " + date;
        if (day.equals(today.minusDays(1))) return "أمس · " + date;
        return date;
    }

    private static String dayLetter(java.time.DayOfWeek day) {
        switch (day) {
            case SATURDAY: return "س";
            case SUNDAY: return "ح";
            case MONDAY: return "ن";
            case TUESDAY: return "ث";
            case WEDNESDAY: return "ر";
            case THURSDAY: return "خ";
            default: return "ج";
        }
    }

    private String commitmentTitle(ExpenseDb.Recurring item) {
        return "installment".equals(item.kind) ? "قسط " + item.merchant : item.merchant;
    }

    private static String kindLabel(String kind) {
        return "installment".equals(kind) ? "قسط" : "bill".equals(kind) ? "فاتورة" : "اشتراك";
    }

    private static String cycleLabel(String cycle) {
        return "yearly".equals(cycle) ? "سنوي" : "weekly".equals(cycle) ? "أسبوعي" :
                "once".equals(cycle) ? "مرة واحدة" : "custom".equals(cycle) ? "مخصص بالأيام" : "شهري";
    }

    private static String kindCategory(String kind) {
        return "installment".equals(kind) ? "أقساط" : "bill".equals(kind) ? "فواتير" : "اشتراكات";
    }

    private List<ExpenseDb.Recurring> activeCommitments() {
        List<ExpenseDb.Recurring> active = new ArrayList<>();
        List<ExpenseDb.Recurring> all=db.recurring();
        for (ExpenseDb.Recurring item : all)
            if (!item.isDone() && !item.needsReview) active.add(item);
        active.sort(Comparator.comparing(item -> item.nextDue));
        return active;
    }

    private void fillBar(View track, long value, long maximum, int color, int trackColor) {
        float ratio = maximum <= 0 ? 0 : Math.min(1f, (float) value / maximum);
        GradientDrawable fill = rounded(color, 5);
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(fill,
                Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{rounded(trackColor, 5), clip});
        track.setBackground(layers);
        clip.setLevel((int) (ratio * 10000));
    }

    static int brandLogo(String id) {
        if(id==null)return 0;
        return switch(id) {
            case "bank_misr" -> R.drawable.brand_bank_misr;
            case "qnb" -> R.drawable.brand_qnb;
            case "fabmisr" -> R.drawable.brand_fabmisr;
            case "hsbc" -> R.drawable.brand_hsbc;
            case "alexbank" -> R.drawable.brand_alexbank;
            case "adib" -> R.drawable.brand_adib;
            case "saib" -> R.drawable.brand_saib;
            case "abc" -> R.drawable.brand_abc;
            case "banknxt" -> R.drawable.brand_banknxt;
            case "kfh" -> R.drawable.brand_kfh;
            case "ca" -> R.drawable.brand_ca;
            case "starbucks" -> R.drawable.brand_starbucks;
            case "carrefour" -> R.drawable.brand_carrefour;
            case "mcdonalds" -> R.drawable.brand_mcdonalds;
            case "kfc" -> R.drawable.brand_kfc;
            case "uber" -> R.drawable.brand_uber;
            case "ubereats" -> R.drawable.brand_ubereats;
            case "claude" -> R.drawable.brand_claude;
            case "netflix" -> R.drawable.brand_netflix;
            case "spotify" -> R.drawable.brand_spotify;
            case "ikea" -> R.drawable.brand_ikea;
            case "shell" -> R.drawable.brand_shell;
            case "airbnb" -> R.drawable.brand_airbnb;
            case "bookingdotcom" -> R.drawable.brand_bookingdotcom;
            case "apple" -> R.drawable.brand_apple;
            case "samsung" -> R.drawable.brand_samsung;
            case "youtube" -> R.drawable.brand_youtube;
            case "vodafone" -> R.drawable.brand_vodafone;
            case "orange" -> R.drawable.brand_orange;
            case "paypal" -> R.drawable.brand_paypal;
            case "visa" -> R.drawable.brand_visa;
            case "mastercard" -> R.drawable.brand_mastercard;
            case "talabat" -> R.drawable.brand_talabat;
            case "cleopatra" -> R.drawable.brand_cleopatra;
            case "espressolab" -> R.drawable.brand_espressolab;
            case "fawry" -> R.drawable.brand_fawry;
            case "instapay" -> R.drawable.brand_instapay;
            default -> 0;
        };
    }

    /** A bank's own operation (TT Payment, ATM Cash Withdrawal…) shows the bank behind the row, not a monogram of the words. */
    private View rowBadge(ExpenseDb.Transaction item, int size) {
        if (ExpenseParser.bankOperation(item.merchant)) {
            Long account = item.paymentSourceId != null ? item.paymentSourceId : item.destinationSourceId;
            ExpenseDb.PaymentSource source = account == null ? null : db.paymentSource(account);
            if (source != null && BrandCatalog.bank(source.name) != null) return sourceBadge(source, size);
        }
        return merchantBadge(item.merchant, item.category, size);
    }

    private View merchantBadge(String name, String category, int size) {
        String id=BrandCatalog.merchant(name);
        int logo=brandLogo(id);
        if (logo != 0) return UiKit.logoBadge(this, logo, id, name, size, dark ? 0 : BORDER);
        if ("سحب نقدي".equals(category) || "رسوم بنكية".equals(category) || "تحويلات".equals(category) ||
                ("دخل".equals(category) && UiKit.provider(name) == null)) {
            int[] style = categoryStyle(category);
            ImageView icon = tintedIcon(style[2], style[1]);
            int inset = dp(Math.max(8, size / 4));
            icon.setPadding(inset, inset, inset, inset);
            icon.setBackground(rounded(style[0], size / 2));
            icon.setContentDescription(category);
            return icon;
        }
        return identityBadge(name == null ? "" : name, category, size);
    }

    private TextView identityBadge(String name, String category, int size) {
        TextView badge = label(UiKit.monogram(name), Math.max(12, Math.round(size * 0.42f)), Color.WHITE, true);
        badge.setPadding(0, 0, 0, 0);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(rounded(UiKit.monogramColor(name), size / 2));
        badge.setContentDescription(name);
        return badge;
    }

    private void header(String eyebrow, String heading, String icon, Runnable action) {
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        if (eyebrow != null && !eyebrow.isEmpty()) titles.addView(label(eyebrow, 12, MUTED, false));
        titles.addView(label(heading, 26, INK, true));
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            boolean add = "+".equals(icon) || "＋".equals(icon);
            int res = add ? R.drawable.ic_plus : "↥".equals(icon) ? R.drawable.ic_cat_income : R.drawable.ic_more;
            String description = add ? "إضافة" : "↥".equals(icon) ? "تصدير" : "خيارات";
            bar.addView(add ? primaryIconButton(res, description, action) : iconButton(res, description, action),
                    new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        page.addView(bar);
    }

    private void section(String name, String link, Runnable action) {
        if (selectedTab == 5) settingsGroup = null;
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(22); lp.bottomMargin = dp(2);
        TextView title = label(name, 16, INK, true); line.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (link != null) {
            TextView more = label(link, 13, ACCENT, true); line.addView(more);
            more.setOnClickListener(v -> action.run());
        }
        page.addView(line, lp);
    }

    private void empty(String heading, String detail) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(26), dp(20), dp(26));
        ImageView logo = profileAvatar(42);
        logo.setAlpha(0.9f);
        box.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(heading, 16, INK, true); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(10);
        box.addView(title, titleParams);
        TextView body = label(detail, 13, MUTED, false); body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.2f);
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
    }

    private void summaryCard(String heading, String amount, String detail) {
        LinearLayout box = card();
        box.setBackground(rounded(BRAND, 22));
        box.addView(label(heading, 13, 0xffb9d3c8, false));
        box.addView(label(amount, 27, Color.WHITE, true));
        box.addView(label(detail, 12, 0xffb9d3c8, false));
    }

    private LinearLayout bottomNavigation() {
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(SURFACE);
        nav.setElevation(dp(12));
        String[] labels = {"الرئيسية", "العمليات", "", "الالتزامات", "التحليلات"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_transactions, R.drawable.ic_plus,
                R.drawable.ic_calendar, R.drawable.ic_nav_insights};
        int[] screens = {0, 6, -1, 2, 4};
        for (int i = 0; i < labels.length; i++) {
            final int tab = screens[i];
            if (tab == -1) {
                FrameLayout cell = new FrameLayout(this);
                ImageView add = primaryIconButton(R.drawable.ic_plus, "إضافة عملية", this::quickAddChooser);
                add.setPadding(dp(15), dp(15), dp(15), dp(15));
                add.setBackground(rounded(BRAND, 18));
                add.setElevation(dp(6));
                cell.addView(add, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
                nav.addView(cell, new LinearLayout.LayoutParams(0, dp(60), 1));
                continue;
            }
            boolean active = NavigationState.destination(selectedTab,transactionReturnTab) == tab;
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
            item.setSelected(active);item.setBackground(active?rounded(BRAND,14):null);
            ImageView icon = tintedIcon(icons[i], active ? LIME : MUTED);
            TextView caption = label(labels[i], 11, active ? Color.WHITE : MUTED, active); caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(24), dp(24));
            iconSize.bottomMargin = dp(3); item.addView(icon, iconSize); item.addView(caption);
            item.setContentDescription(labels[i]+(active?"، الصفحة الحالية":""));
            LinearLayout.LayoutParams destinationParams=new LinearLayout.LayoutParams(0,dp(60),1);
            destinationParams.setMargins(dp(3),0,dp(3),0);nav.addView(item,destinationParams);
            item.setOnClickListener(v -> {
                if (tab == 4) insightsSection="";
                transactionsReturnDialog="";transactionsPointsCurrency="";transactionJourney.clear();
                if (tab == 6) { transactionBucketFilter = ""; transactionPeriod = null; transactionCurrencyFilter = ""; transactionsBackTab = 0; }
                selectedTab = tab; render();
            });
        }
        return nav;
    }

    private String notificationStatus(boolean enabled, String channelId) {
        if (!enabled) return "متوقف";
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled() || Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return "مفعّل بالتطبيق · موقوف من إعدادات الجهاز";
        android.app.NotificationChannel channel = manager.getNotificationChannel(channelId);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)
            return "مفعّل بالتطبيق · موقوف من إعدادات نوع الإشعار";
        return "شغال";
    }

    private void notificationSetting(String title, String detail, boolean enabled, String channel,
                                     java.util.function.Consumer<Boolean> change) {
        if (settingsGroup == null) settingsGroup = listCard(page); else divider(settingsGroup);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); row.setPadding(0, dp(12), 0, dp(12));
        ImageView icon = tintedIcon(R.drawable.ic_bell, ACCENT);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7)); icon.setBackground(rounded(ACCENT_PALE, 10));
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 14, INK, true));
        String status = notificationStatus(enabled, channel);
        copy.addView(label(status, 12, "شغال".equals(status) ? INCOME : MUTED, false));
        copy.addView(label(detail, 12, MUTED, false));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.setMarginStart(dp(12)); copyParams.setMarginEnd(dp(10)); row.addView(copy, copyParams);
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(enabled); toggle.setShowText(false); toggle.setMinHeight(dp(48));
        toggle.setContentDescription(title);
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        toggle.setTrackTintList(new ColorStateList(states, new int[]{primaryFill(), BORDER}));
        toggle.setThumbTintList(new ColorStateList(states, new int[]{onPrimary(), MUTED}));
        toggle.setOnCheckedChangeListener((button, checked) -> change.accept(checked));
        row.addView(toggle); row.setOnClickListener(view -> toggle.toggle()); settingsGroup.addView(row);
    }

    private void setting(String name, Runnable action) {
        if (settingsGroup == null) settingsGroup = listCard(page);
        else divider(settingsGroup);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        boolean destructive = name.startsWith("حذف كل");
        int iconId = destructive ? R.drawable.ic_trash :
                name.contains("SMS") || name.contains("رسائل") ? R.drawable.ic_message :
                name.contains("تطبيقات") || name.contains("Notification") || name.contains("تذكير") || name.contains("إشعار") ? R.drawable.ic_bell :
                name.contains("قواعد") || name.contains("عينات") || name.contains("تشخيص") ? R.drawable.ic_filter :
                name.contains("إنستاباي") || name.contains("التحويلات") ? R.drawable.ic_cat_transfer :
                name.contains("حسابات") ? R.drawable.ic_cat_atm :
                name.contains("الالتزامات") || name.contains("أقساط") ? R.drawable.ic_calendar :
                name.contains("الأصول") ? R.drawable.ic_nav_insights :
                name.contains("الديون") ? R.drawable.ic_cat_transfer :
                name.contains("اسم") ? R.drawable.ic_edit :
                name.contains("الوضع") ? R.drawable.ic_settings :
                name.contains("ويدجت") ? R.drawable.ic_nav_home :
                name.contains("CSV") || name.contains("تصدير") ? R.drawable.ic_cat_income : R.drawable.ic_cat_other;
        ImageView icon = tintedIcon(iconId, destructive ? CORAL : ACCENT);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(rounded(destructive ? (dark ? 0x40c23a3a : 0xfffde8e8) : ACCENT_PALE, 10));
        row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, dp(54), 1); textParams.setMarginStart(dp(12));
        row.addView(label(name, 14, destructive ? CORAL : INK, false), textParams);
        row.addView(tintedIcon(R.drawable.ic_chevron_forward, MUTED), new LinearLayout.LayoutParams(dp(18), dp(18)));
        settingsGroup.addView(row);
        row.setOnClickListener(v -> action.run());
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView view = text(value, sp);
        view.setTextColor(color);
        view.setTypeface(bold ? BOLD : REGULAR);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    /** Plain text that opens data gets the same visible affordance; filled buttons already have one. */
    private void markDetailActions(View view) {
        if (view.hasOnClickListeners()) {
            TextView title=view instanceof TextView?(TextView)view:firstPlainLabel(view);
            if (title!=null && !(title instanceof EditText) && title.getBackground()==null)
                title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) markDetailActions(group.getChildAt(i));
        }
    }

    private TextView firstPlainLabel(View view) {
        if (view instanceof TextView && !(view instanceof EditText) && view.getBackground()==null) return (TextView)view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) {
                TextView found=firstPlainLabel(group.getChildAt(i));if(found!=null)return found;
            }
        }
        return null;
    }

    // ---- التحليلات: one screen that answers, in order: how much, on what, which month rose and why, what needs review ----

    private static final String[] MONTH_NAMES = {"يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};
    private static final String[] MONTH_SHORT = {"ينا","فبر","مار","أبر","ماي","يون","يول","أغس","سبت","أكت","نوف","ديس"};

    private LocalDate[] insightsPeriod() { return MoneyFlow.period(insightsRange, selectedMonth, LocalDate.now()); }

    private String insightsPeriodName() {
        LocalDate[] period = insightsPeriod();
        switch (insightsRange) {
            case YEAR: return "سنة " + period[0].getYear() + (period[0].getYear() == LocalDate.now().getYear() ? " لحد النهارده" : "");
            case LAST_12: return "آخر 12 شهر";
            default: return MONTH_NAMES[selectedMonth.getMonthValue() - 1] + " " + selectedMonth.getYear();
        }
    }

    private String monthlyName(java.time.YearMonth month){return MONTH_NAMES[month.getMonthValue()-1]+" "+month.getYear();}

    private void openMonthly(java.time.YearMonth month) {
        monthlyMonth=month;introVisible=false;selectedTab=15;render();
    }

    private void openMonthlyIntent(Intent intent) {
        if(!intent.hasExtra(MonthlySummaryReceiver.EXTRA_MONTH))return;
        java.time.YearMonth month=MonthlyReport.parseMonth(intent.getStringExtra(MonthlySummaryReceiver.EXTRA_MONTH),java.time.YearMonth.now().minusMonths(1));
        intent.removeExtra(MonthlySummaryReceiver.EXTRA_MONTH);openMonthly(month);
    }

    private void monthlyReadyCard() {
        java.time.YearMonth month=java.time.YearMonth.now().minusMonths(1);
        java.time.YearMonth seen=MonthlyReport.parseMonth(MonthlySummaryReceiver.settings(this).getString("seen_month",null),null);
        if(seen!=null && !seen.isBefore(month) || !db.months().contains(month.toString()))return;
        LinearLayout box=card();box.addView(label("ملخص "+monthlyName(month)+" جاهز  ←",15,ACCENT,true));
        box.addView(label("راجع صرفك والتزاماتك وسلوك الإنفاق المسجل",12,MUTED,false));box.setOnClickListener(v->openMonthly(month));
    }

    private void chooseMonthlyMonth() {
        java.util.TreeSet<java.time.YearMonth> months=new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        months.add(java.time.YearMonth.now().minusMonths(1));
        for(String value:db.months()) {
            java.time.YearMonth month=MonthlyReport.parseMonth(value,null);
            if(month!=null && month.isBefore(java.time.YearMonth.now()) && month.getYear()>=1900)months.add(month);
        }
        List<java.time.YearMonth> choices=new ArrayList<>(months);String[] names=new String[choices.size()];
        for(int i=0;i<names.length;i++)names[i]=monthlyName(choices.get(i));
        new ChoiceDialogBuilder().setTitle("ملخص شهر مكتمل").setItems(names,(d,w)->openMonthly(choices.get(w))).setNegativeButton("إلغاء",null).show();
    }

    private void openMonthlyTransactions(MoneyFlow.Bucket bucket,String category,String merchant) {
        resetTransactionFilters();transactionKindFilter=bucket==null?"expense":"";transactionBucketFilter=bucket==null?"":bucket.name();transactionCategoryFilter=category;
        transactionCurrencyFilter=monthlyCurrency;transactionPeriod=new LocalDate[]{monthlyMonth.atDay(1),monthlyMonth.plusMonths(1).atDay(1)};
        transactionSearch=merchant;searchOpen=!merchant.isEmpty();transactionsBackTab=15;selectedTab=6;render();
    }

    private void monthlyMetric(LinearLayout box,String title,long amount,int color,Runnable action) {
        if(box.getChildCount()>0)divider(box);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));
        row.addView(label(title,14,INK,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView value=label(money(amount,monthlyCurrency),15,color,true);value.setTextDirection(View.TEXT_DIRECTION_LTR);row.addView(value);
        if(action!=null){row.setOnClickListener(v->action.run());row.setContentDescription(title+"، "+money(amount,monthlyCurrency)+"، عرض العمليات");}
        box.addView(row);
    }

    private void monthlyReportScreen() {
        java.time.YearMonth latest=java.time.YearMonth.now().minusMonths(1);
        if(monthlyMonth.isAfter(latest) || monthlyMonth.getYear()<1900)monthlyMonth=latest;
        header("شهر مكتمل · حسب الحركة المسجلة","ملخصك الشهري","",this::chooseMonthlyMonth);
        LinearLayout months=new LinearLayout(this);months.setGravity(Gravity.CENTER_VERTICAL);
        addChip(months,"السابق",false,()->openMonthly(monthlyMonth.minusMonths(1)));
        addChip(months,monthlyName(monthlyMonth),true,this::chooseMonthlyMonth);
        if(monthlyMonth.isBefore(latest))addChip(months,"التالي",false,()->openMonthly(monthlyMonth.plusMonths(1)));
        page.addView(months);
        List<MoneyFlow.Item> history=flowItems(new LocalDate[]{monthlyMonth.minusMonths(1).atDay(1),monthlyMonth.plusMonths(1).atDay(1)});
        MonthlyReport report=MonthlyReport.build(history,monthlyMonth,ZoneId.systemDefault());
        MonthlySummaryReceiver.markSeen(this,monthlyMonth);
        if(report.currencies.isEmpty()) {
            boolean rawExists=!db.transactionsBetween(monthlyMonth.atDay(1),monthlyMonth.plusMonths(1).atDay(1)).isEmpty();
            empty(rawExists?"مفيش أثر مالي بعد الاستردادات":"مفيش حركة مسجلة في الشهر ده",
                    rawExists?"العمليات موجودة، لكن أثرها اتعوض بالكامل حسب الروابط المسجلة.":"اختار شهر تاني؛ غياب التسجيل مش معناه إنك ما صرفتش.");return;
        }
        if(!report.currencies.containsKey(monthlyCurrency))monthlyCurrency=report.currencies.keySet().iterator().next();
        LinearLayout currencies=new LinearLayout(this);
        for(String currency:report.currencies.keySet())addChip(currencies,currency,currency.equals(monthlyCurrency),()->{monthlyCurrency=currency;render();});
        page.addView(currencies);
        MonthlyReport.Currency data=report.currencies.get(monthlyCurrency);MoneyFlow.Summary current=data.current;
        LinearLayout hero=card();hero.setBackground(rounded(BRAND,22));hero.addView(label("إجمالي المصروف · "+monthlyName(monthlyMonth),13,0xffb9d3c8,false));
        TextView outgoing=label(money(current.totalOut(),monthlyCurrency),30,Color.WHITE,true);outgoing.setTextDirection(View.TEXT_DIRECTION_LTR);hero.addView(outgoing);
        hero.addView(label(current.outCount()+" عملية · "+current.byDay.size()+" يوم فيه حركة مسجلة",12,0xffb9d3c8,false));
        hero.setOnClickListener(v->openMonthlyTransactions(null,"",""));
        section("حركة الشهر",null,null);LinearLayout totals=listCard(page);
        monthlyMetric(totals,"صرف يومي",current.of(MoneyFlow.Bucket.LIVING),CORAL,()->openMonthlyTransactions(MoneyFlow.Bucket.LIVING,"",""));
        monthlyMetric(totals,"التزامات مدفوعة",current.of(MoneyFlow.Bucket.COMMITMENT),dark?0xffa0aafc:INDIGO,()->openMonthlyTransactions(MoneyFlow.Bucket.COMMITMENT,"",""));
        monthlyMetric(totals,"تحويلات لناس غير محددة الغرض",current.of(MoneyFlow.Bucket.OPEN_TRANSFER),dark?0xfff2ac65:ORANGE,()->openMonthlyTransactions(MoneyFlow.Bucket.OPEN_TRANSFER,"",""));
        monthlyMetric(totals,"وارد مسجل",current.of(MoneyFlow.Bucket.INFLOW),INCOME,()->openMonthlyTransactions(MoneyFlow.Bucket.INFLOW,"",""));
        monthlyMetric(totals,"تحويل بين حساباتك · منفصل",current.of(MoneyFlow.Bucket.INTERNAL),MUTED,()->openMonthlyTransactions(MoneyFlow.Bucket.INTERNAL,"",""));
        monthlyMetric(totals,"سحب نقدي · من حسابك لكاش",current.of(MoneyFlow.Bucket.CASH_WITHDRAWAL),MUTED,()->openMonthlyTransactions(MoneyFlow.Bucket.CASH_WITHDRAWAL,"",""));
        monthlyMetric(totals,"صافي الصرف والدخل المسجل",current.of(MoneyFlow.Bucket.INFLOW)-current.totalOut(),INK,null);
        note("العملات منفصلة. الصافي مش رصيد بنك أو وفر مؤكد. الصرف اليومي يشمل الشراء والرسوم؛ سحب الكاش اتنقل من حسابك لكاش ومش مصروف. الاسترداد المرتبط يُخصم من العملية الأصلية والوارد زي باقي التحليلات.");

        section("صرفك اليومي راح فين؟",null,null);LinearLayout categories=listCard(page);long living=current.of(MoneyFlow.Bucket.LIVING);
        for(Map.Entry<String,Long> entry:current.livingCategories()) {
            String name=entry.getKey();monthlyMetric(categories,name,entry.getValue(),categoryStyle(name)[1],()->openMonthlyTransactions(MoneyFlow.Bucket.LIVING,name,""));
            TextView share=pill(Math.round(100.0*entry.getValue()/Math.max(1,living))+"%",BORDER,MUTED);share.setTextDirection(View.TEXT_DIRECTION_LTR);
            LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(-2,-2);shareParams.setMarginStart(dp(8));
            ((LinearLayout)categories.getChildAt(categories.getChildCount()-1)).addView(share,shareParams);
        }
        if(living==0)categories.addView(label("مفيش صرف يومي مسجل بالعملة دي",13,MUTED,false));
        section("سلوك الصرف",null,null);LinearLayout habits=card();
        if("EGP".equals(monthlyCurrency))monthlyMetric(habits,"مدفوعات حتى 100 جنيه · "+data.smallCount+" عمليات",data.smallTotal,INK,null);
        habits.addView(label("متوسط العملية اليومية: "+money(current.livingAverage(),monthlyCurrency)+" · عددها "+current.countOf(MoneyFlow.Bucket.LIVING),13,MUTED,false));
        habits.addView(label("أكتر جهات الصرف اليومي",12,MUTED,false));
        for(Map.Entry<String,Long> entry:data.topMerchants())monthlyMetric(habits,entry.getKey(),entry.getValue(),ACCENT,null);
        if(current.uncategorized>0)note(current.uncategorized+" عمليات غير مصنفة بقيمة "+money(current.uncategorizedAmount,monthlyCurrency)+"؛ التصنيفات ونِسبها تعتمد على التسجيل المتاح.");

        section("مقارنة بالشهر السابق",null,null);LinearLayout comparison=card();
        monthlyMetric(comparison,"صرف يومي · "+monthlyName(monthlyMonth.minusMonths(1)),data.previous.of(MoneyFlow.Bucket.LIVING),MUTED,null);
        monthlyMetric(comparison,"صرف يومي · "+monthlyName(monthlyMonth),living,INK,null);
        SharedPreferences prefs=MonthlySummaryReceiver.settings(this);
        String nowKey="coverage_"+monthlyMonth+"_"+monthlyCurrency,previousKey="coverage_"+monthlyMonth.minusMonths(1)+"_"+monthlyCurrency;
        boolean confirmed=data.currentFingerprint.equals(prefs.getString(nowKey,"")) && data.previousFingerprint.equals(prefs.getString(previousKey,""));
        Long change=data.livingChangePercent(confirmed);
        if(change!=null) {
            LinearLayout changeRow=new LinearLayout(this);changeRow.setGravity(Gravity.CENTER_VERTICAL);
            String detail=change==0?"الصرف اليومي المسجل ثابت":change>0?"الصرف اليومي المسجل زاد":"الصرف اليومي المسجل قل";
            changeRow.addView(label(detail,17,change>0?(dark?0xfff2ac65:ORANGE):INCOME,true),new LinearLayout.LayoutParams(0,-2,1));
            if(change!=0){TextView share=pill(Math.abs(change)+"%",ACCENT_PALE,change>0?(dark?0xfff2ac65:ORANGE):INCOME);share.setTextDirection(View.TEXT_DIRECTION_LTR);changeRow.addView(share);}
            changeRow.setContentDescription(detail+(change==0?"":" "+Math.abs(change)+"%"));comparison.addView(changeRow);
        }
        else comparison.addView(label(confirmed?"مفيش أساس سابق موجب لحساب نسبة تغير.":"النسبة متوقفة لحد تأكيد اكتمال تسجيل الشهرين؛ وجود رسائل قديمة وحده مش كفاية.",13,MUTED,false));
        TextView confirm=label(confirmed?"إلغاء تأكيد اكتمال التسجيل":"تأكيد اكتمال تسجيل الشهرين",13,ACCENT,true);confirm.setPadding(0,dp(12),0,dp(6));comparison.addView(confirm);
        confirm.setOnClickListener(v->{if(confirmed){prefs.edit().remove(nowKey).remove(previousKey).apply();render();}else new ChoiceDialogBuilder()
                .setTitle("هل تسجيل الشهرين كامل؟").setMessage("أكد فقط لو حركة "+monthlyName(monthlyMonth)+" و"+monthlyName(monthlyMonth.minusMonths(1))+" بالعملة "+monthlyCurrency+" كاملة بالشكل اللي عايز تقارنه. الاستيراد أو التعديل اللي يغير الأرقام يلغي التأكيد تلقائيًا.")
                .setPositiveButton("التسجيل كامل",(d,w)->{prefs.edit().putString(nowKey,data.currentFingerprint).putString(previousKey,data.previousFingerprint).apply();render();}).setNegativeButton("لسه ناقص",null).show();});
        note("الملخص بيتحدث لما تضيف أو تعدل عمليات قديمة. تنبيه الشهر من الإعدادات ← الإشعارات والتذكير.");
    }

    private List<MoneyFlow.Item> flowItems(LocalDate[] period) {
        return db.flowItems(period[0], period[1], ZoneId.systemDefault());
    }

    private Map<Long, String> sourceKinds() {
        if (sourceKindCache == null) sourceKindCache = db.sourceKinds();
        return sourceKindCache;
    }

    /** The bucket a list row falls in, with a repayment to the user's own credit card read as internal (as in the statement). */
    private MoneyFlow.Bucket bucketOf(ExpenseDb.Transaction tx) {
        return MoneyFlow.bucket(tx.kind, tx.category, ExpenseDb.paidToOwnCard(tx, sourceKinds()));
    }

    private int bucketColor(MoneyFlow.Bucket bucket) {
        switch (bucket) {
            case LIVING: return CORAL;
            case COMMITMENT: return INDIGO;
            case OPEN_TRANSFER: return ORANGE;
            case INFLOW: return INCOME;
            case CASH_WITHDRAWAL: return 0xff3b4ba8;
            default: return MUTED;
        }
    }

    private String bucketDetail(MoneyFlow.Bucket bucket, MoneyFlow.Summary s) {
        int n = s.countOf(bucket);
        switch (bucket) {
            case LIVING: return n + " عملية: شراء ورسوم";
            case COMMITMENT: return n + " دفعة: أقساط وفواتير واشتراكات";
            case OPEN_TRANSFER: return n == 0 ? "كل التحويلات متحدد غرضها" : n + " تحويل لناس · حدد غرضها";
            case INFLOW: return n + " عملية · مش شرط تكون دخل مكتسب";
            case CASH_WITHDRAWAL: return n + " سحب من الحساب · مش مصروف";
            default: return n + " حركة · مش محسوبة صرف ولا دخل";
        }
    }

    private void analyticsLink(LinearLayout box,String title,String detail,int iconId,Runnable open) {
        if(box.getChildCount()>0) divider(box);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(16),0,dp(16));
        ImageView icon=tintedIcon(iconId,ACCENT);icon.setPadding(dp(9),dp(9),dp(9),dp(9));icon.setBackground(rounded(ACCENT_PALE,12));
        row.addView(icon,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title,16,INK,true));copy.addView(label(detail,12,MUTED,false));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(12));row.addView(copy,cp);
        row.addView(tintedIcon(R.drawable.ic_chevron_back,MUTED),new LinearLayout.LayoutParams(dp(20),dp(20)));
        row.setContentDescription(title+"، "+detail);row.setOnClickListener(v->open.run());box.addView(row);
    }

    private void analyticsHub() {
        note("كل تقرير يعرض الفترة المختارة. العملات منفصلة والتحويل بين حساباتك مش مصروف.");
        LinearLayout box=listCard(page);
        analyticsLink(box,"ملخصك الشهري","الشهر اللي خلص، سلوك الصرف والمقارنة",R.drawable.ic_nav_budget,()->openMonthly(java.time.YearMonth.now().minusMonths(1)));
        analyticsLink(box,"تحليل الإنفاق","اتجاه الصرف اليومي وأكبر المدفوعات",R.drawable.ic_nav_insights,()->{insightsSection="spending";render();});
        analyticsLink(box,"تحليل التصنيفات","نسب التصنيفات والعمليات وراء كل رقم",R.drawable.ic_cat_shop,()->{insightsSection="categories";render();});
        analyticsLink(box,"الفلوس دخلت وخرجت","الإنفاق والالتزامات والتحويلات كل واحد واضح",R.drawable.ic_cat_transfer,()->{insightsSection="flow";render();});
        analyticsLink(box,"تكلفة المعيشة","الصرف اليومي والالتزامات المسجلة للفترة",R.drawable.ic_cat_bills,()->{insightsSection="living";render();});
        analyticsLink(box,"صافي الثروة","الحسابات والأصول والديون المسجلة · الحالة الحالية",R.drawable.ic_cat_income,()->{selectedTab=8;render();});
        analyticsLink(box,"الاشتراكات والفواتير","مواعيد الدفعات والخطط المؤكدة · الحالة الحالية",R.drawable.ic_cat_subscription,()->{selectedTab=2;render();});
        TextView overview=label("عرض الملخص الكامل  ←",13,ACCENT,true);overview.setPadding(0,dp(18),0,dp(14));box.addView(overview);
        overview.setOnClickListener(v->{insightsSection="overview";render();});
    }

    private void insightsScreen() {
        String title="spending".equals(insightsSection)?"تحليل الإنفاق":"categories".equals(insightsSection)?"تحليل التصنيفات":
                "flow".equals(insightsSection)?"الفلوس دخلت وخرجت":"living".equals(insightsSection)?"تكلفة المعيشة":
                "overview".equals(insightsSection)?"فلوسك راحت فين":"تقاريرك المالية";
        header("التحليلات", title, "↥", this::exportCsv);
        if(!insightsSection.isEmpty()) {
            TextView back=label("← كل التقارير",13,ACCENT,true);back.setPadding(0,dp(10),0,dp(10));page.addView(back);
            back.setOnClickListener(v->{insightsSection="";render();});
        }
        boolean overview="overview".equals(insightsSection),spending=overview||"spending".equals(insightsSection);
        boolean categoryReport=overview||"categories".equals(insightsSection),flow=overview||"flow".equals(insightsSection);
        boolean livingReport=overview||"living".equals(insightsSection);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this); chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(chips);
        String[] names = {"الشهر", "السنة", "آخر 12 شهر"};
        MoneyFlow.Range[] ranges = MoneyFlow.Range.values();
        for (int i = 0; i < ranges.length; i++) {
            MoneyFlow.Range range = ranges[i];
            addChip(chips, names[i], insightsRange == range, () -> { insightsRange = range; render(); });
        }
        if (insightsRange != MoneyFlow.Range.LAST_12) {
            boolean month = insightsRange == MoneyFlow.Range.MONTH;
            TextView pick = addChip(chips, (month ? MONTH_NAMES[selectedMonth.getMonthValue() - 1] + " " + selectedMonth.getYear() :
                    Integer.toString(selectedMonth.getYear())) + "  ⌄", false, month ? this::chooseMonth : this::chooseYear);
            pick.setBackground(rounded(ACCENT_PALE, 99)); pick.setTextColor(ACCENT);
        }
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-1, -2); chipParams.topMargin = dp(12);
        page.addView(scroll, chipParams);

        LocalDate[] period = insightsPeriod();
        Map<String, MoneyFlow.Summary> all = MoneyFlow.summarize(flowItems(period), period, ZoneId.systemDefault());
        MoneyFlow.Summary s = all.get("EGP");
        if(insightsSection.isEmpty()) { analyticsHub();return; }
        if (s == null || s.isEmpty()) {
            empty("مفيش حركة في " + insightsPeriodName(), "جرّب فترة تانية أو استورد رسائل البنك");
            budgetsSection();
            return;
        }

        // 1) صرفت كام؟
        if(spending || flow) {
        section("صرفت كام؟", null, null);
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(rounded(BRAND, 24));
        hero.addView(label("صرف يومي · " + insightsPeriodName(), 13, 0xffb9d3c8, false));
        TextView living = label(money(s.of(MoneyFlow.Bucket.LIVING), "EGP"), 30, Color.WHITE, true);
        living.setTextDirection(View.TEXT_DIRECTION_LTR);
        hero.addView(living);
        hero.addView(label(s.countOf(MoneyFlow.Bucket.LIVING) + " عملية · نصها " + money(s.livingMedian(), "EGP") +
                " أو أقل · المتوسط " + money(s.livingAverage(), "EGP"), 12, 0xffb9d3c8, false));
        hero.setOnClickListener(v -> openBucket(MoneyFlow.Bucket.LIVING, "", false));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2); heroParams.topMargin = dp(4);
        page.addView(hero, heroParams);

        LinearLayout buckets = listCard(page);
        for (MoneyFlow.Bucket bucket : new MoneyFlow.Bucket[]{MoneyFlow.Bucket.COMMITMENT, MoneyFlow.Bucket.OPEN_TRANSFER,
                MoneyFlow.Bucket.INFLOW, MoneyFlow.Bucket.INTERNAL, MoneyFlow.Bucket.CASH_WITHDRAWAL}) {
            if (buckets.getChildCount() > 0) divider(buckets);
            bucketRow(buckets, bucket, s);
        }
        divider(buckets);
        LinearLayout total = new LinearLayout(this); total.setGravity(Gravity.CENTER_VERTICAL);
        total.setPadding(0, dp(12), 0, dp(12));
        LinearLayout totalCopy = new LinearLayout(this); totalCopy.setOrientation(LinearLayout.VERTICAL);
        totalCopy.addView(label("إجمالي المصروف", 13, MUTED, false));
        totalCopy.addView(label("صرف يومي + أقساط + تحويلات لناس · " + s.outCount() + " عملية", 11, MUTED, false));
        total.addView(totalCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView totalAmount = label(money(s.totalOut(), "EGP"), 14, MUTED, true);
        totalAmount.setTextDirection(View.TEXT_DIRECTION_LTR);
        total.addView(totalAmount);
        buckets.addView(total);

        }
        if(livingReport && !overview) {
            section("تكلفة المعيشة المسجلة",null,null);
            LinearLayout cost=card();cost.addView(label(money(s.of(MoneyFlow.Bucket.LIVING)+s.of(MoneyFlow.Bucket.COMMITMENT),"EGP"),28,INK,true));
            cost.addView(label("صرف يومي "+money(s.of(MoneyFlow.Bucket.LIVING),"EGP")+" · التزامات "+money(s.of(MoneyFlow.Bucket.COMMITMENT),"EGP"),13,MUTED,false));
            cost.addView(label("للفترة المختارة، حسب العمليات المسجلة. التحويلات مجهولة الغرض وبين حساباتك مستبعدة، وسحب الكاش اتنقل من حسابك لكاش ومش مصروف.",12,MUTED,false));
        }
        // Findings: forecast, what is left, commitment burden, habits, unusual payments...
        if(spending) insightCards(period);

        // 2) صرفت في إيه؟
        if(categoryReport) {
        section("صرفت في إيه؟", null, null);
        LinearLayout categories = card();
        long livingTotal = Math.max(1, s.of(MoneyFlow.Bucket.LIVING));
        int shown = 0;
        for (Map.Entry<String, Long> entry : s.livingCategories()) {
            if (shown++ == 8) break;
            String name = entry.getKey();
            boolean open = MoneyFlow.UNCATEGORIZED.equals(name);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(2));
            row.addView(label(open ? name + " · " + s.uncategorized + " عملية" : name, 14, INK, !open), new LinearLayout.LayoutParams(0, -2, 1));
            if (open) {
                TextView fix = pill("صنّفها", dark ? 0x33d9a21b : 0xfffff6db, dark ? 0xfff3d98a : 0xff5c4700);
                LinearLayout.LayoutParams fixParams = new LinearLayout.LayoutParams(-2, -2); fixParams.setMarginEnd(dp(8));
                row.addView(fix, fixParams);
            }
            LinearLayout figures=new LinearLayout(this);figures.setGravity(Gravity.CENTER_VERTICAL);
            figures.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            TextView value = label(money(entry.getValue(), "EGP"), 13, MUTED, false);
            value.setTextDirection(View.TEXT_DIRECTION_LTR);
            figures.addView(value);
            TextView share=pill(Math.round(100.0 * entry.getValue() / livingTotal)+"%",BORDER,MUTED);
            share.setTextDirection(View.TEXT_DIRECTION_LTR);
            LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(-2,-2);shareParams.setMarginStart(dp(8));
            figures.addView(share,shareParams);row.addView(figures);
            categories.addView(row);
            View track = new View(this);
            categories.addView(track, new LinearLayout.LayoutParams(-1, dp(6)));
            fillBar(track, entry.getValue(), livingTotal, open ? GOLD : categoryColor(name), BORDER);
            row.setOnClickListener(v -> openBucket(MoneyFlow.Bucket.LIVING, name, open));
            track.setOnClickListener(v -> openBucket(MoneyFlow.Bucket.LIVING, name, open));
        }
        }
        if ((categoryReport || livingReport) && !s.commitmentByMerchant.isEmpty()) {
            LinearLayout commitments = card();
            commitments.addView(label("الأقساط والالتزامات", 15, INK, true));
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(s.commitmentByMerchant.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(5, sorted.size()); i++) {
                LinearLayout row = new LinearLayout(this); row.setPadding(0, dp(6), 0, 0);
                String merchant=sorted.get(i).getKey();
                row.addView(label(UiKit.displayName(merchant), 13, INK, false), new LinearLayout.LayoutParams(0, -2, 1));
                TextView value = label(money(sorted.get(i).getValue(), "EGP"), 13, INDIGO, true);
                value.setTextDirection(View.TEXT_DIRECTION_LTR);
                row.addView(value);
                commitments.addView(row);
                row.setOnClickListener(v -> {openBucket(MoneyFlow.Bucket.COMMITMENT,"",false);transactionSearch=merchant;searchOpen=true;render();});
            }
            commitments.setOnClickListener(v -> openBucket(MoneyFlow.Bucket.COMMITMENT, "", false));
        }

        // 3) أي شهر زاد وليه؟ / كل يوم في الشهر
        if(spending) {
            if (insightsRange == MoneyFlow.Range.MONTH) monthChart(s, period);
            else yearChart(s);
        }

        // 4) أكبر المدفوعات
        if(spending) {
        section("أكبر المدفوعات", null, null);
        LinearLayout largest = listCard(page);
        for (MoneyFlow.Item item : s.largestOutflows(5)) {
            ExpenseDb.Transaction tx = db.transactionById(item.id);
            if (tx != null) transactionRow(largest, tx, false);
        }

        }
        // 5) محتاج مراجعة
        if(overview || categoryReport) reviewSection(s);
        // 6) الميزانيات
        if(livingReport) budgetsSection();
        // 7) حسب الحساب
        if(flow) accountsSection(period);

        StringBuilder others = new StringBuilder();
        for (Map.Entry<String, MoneyFlow.Summary> entry : all.entrySet()) {
            if ("EGP".equals(entry.getKey())) continue;
            MoneyFlow.Summary other = entry.getValue();
            others.append(others.length() == 0 ? "" : "\n").append(entry.getKey()).append(": صرف يومي ")
                    .append(money(other.of(MoneyFlow.Bucket.LIVING), entry.getKey())).append(" · التزامات ")
                    .append(money(other.of(MoneyFlow.Bucket.COMMITMENT), entry.getKey())).append(" · دخلت ")
                    .append(money(other.of(MoneyFlow.Bucket.INFLOW), entry.getKey()));
        }
        if (others.length() > 0) note("عملات تانية (مش مجموعة مع الجنيه):\n" + others);
        note("نفس التعريفات في كل الصفحات: «صرف يومي» شراء ورسوم. الأقساط والفواتير والاشتراكات سطر لوحدها. " +
                "التحويلات لناس لحد ما تحدد غرضها سطر لوحدها. التحويل بين حساباتك وسحب الكاش مش صرف ولا دخل. الأرقام بالجنيه.");
    }

    private void insightCards(LocalDate[] period) {
        LocalDate historyStart = period[1].minusMonths(13).withDayOfMonth(1);
        LocalDate from = historyStart.isBefore(period[0]) ? historyStart : period[0];
        List<MoneyFlow.Item> history = new ArrayList<>();
        for (MoneyFlow.Item item : flowItems(new LocalDate[]{from, period[1]})) if ("EGP".equals(item.currency)) history.add(item);
        List<Insights.Plan> plans = new ArrayList<>();
        for (ExpenseDb.Recurring item : activeCommitments())
            if ("EGP".equals(item.currency)) plans.add(new Insights.Plan(item.merchant, item.amountMinor,
                    item.totalCount == null ? null : Math.max(0, item.totalCount - item.paidCount), item.kind));
        List<Insights.Insight> insights = Insights.compute(history, period, insightsRange, LocalDate.now(), ZoneId.systemDefault(),
                plans, minor -> money(minor, "EGP"));
        if (insights.isEmpty()) return;
        section("رؤى عن فلوسك", null, null);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false); scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(0, dp(2), 0, dp(6));
        scroll.addView(row);
        for (Insights.Insight insight : insights) {
            int tone = insight.tone == Insights.Tone.GOOD ? INCOME : insight.tone == Insights.Tone.WARN ? ORANGE : INDIGO;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(outlined(SURFACE, BORDER, 20));
            View accent = new View(this); accent.setBackground(rounded(tone, 3));
            card.addView(accent, new LinearLayout.LayoutParams(dp(28), dp(4)));
            TextView title = label(insight.title, 12, MUTED, false);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.topMargin = dp(8);
            card.addView(title, titleParams);
            TextView value = label(insight.value, 20, tone == INDIGO ? INK : tone, true);
            value.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
            card.addView(value);
            TextView detail = label(insight.detail, 12, INK, false);
            detail.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2); detailParams.topMargin = dp(4);
            card.addView(detail, detailParams);
            if (insight.action != Insights.Action.NONE) {
                TextView more = label("التفاصيل ←", 12, ACCENT, true);
                LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(-2, -2); moreParams.topMargin = dp(8);
                card.addView(more, moreParams);
                card.setOnClickListener(v -> openInsight(insight, period));
            }
            card.setContentDescription(insight.title + ": " + insight.value + ". " + insight.detail);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(268), -1); cardParams.setMarginEnd(dp(10));
            row.addView(card, cardParams);
        }
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    private void openInsight(Insights.Insight insight, LocalDate[] period) {
        if ("owed".equals(insight.key)) {showRemainingInstallments();return;}
        switch (insight.action) {
            case LIVING: openBucket(MoneyFlow.Bucket.LIVING, "", false); break;
            case COMMITMENT: openBucket(MoneyFlow.Bucket.COMMITMENT, "", false); break;
            case CATEGORY: openBucket(MoneyFlow.Bucket.LIVING, insight.target, false); break;
            case MERCHANT:
                openBucket(null, "", false);
                transactionPeriod = new LocalDate[]{period[1].minusMonths(12), period[1]};
                transactionSearch = insight.target; searchOpen = true; render(); break;
            case TRANSACTION: {
                ExpenseDb.Transaction tx = db.transactionById(insight.transactionId);
                if (tx != null) { transactionJourney.clear();selectedTransaction = tx; transactionReturnTab = 4; selectedTab = 10; render(); }
                break;
            }
            default: break;
        }
    }

    private void showRemainingInstallments() {
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20),dp(8),dp(20),dp(12));scroll.addView(list);
        android.app.AlertDialog dialog=new ChoiceDialogBuilder().setTitle("تفاصيل الأقساط المتبقية").setView(scroll).setPositiveButton("إغلاق",null).create();
        long total=0;
        for(ExpenseDb.Recurring item:activeCommitments()) {
            if(!"EGP".equals(item.currency)||item.totalCount==null)continue;
            int remaining=Math.max(0,item.totalCount-item.paidCount);if(remaining==0)continue;
            long amount=item.amountMinor*remaining;total+=amount;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,dp(12),0,dp(12));
            row.addView(label(item.merchant+"  ←",15,ACCENT,true));
            row.addView(label(remaining+" قسط × "+money(item.amountMinor,"EGP"),13,MUTED,false));
            row.addView(label("المتبقي "+money(amount,"EGP")+" · القسط القادم "+item.nextDue,13,INK,true));
            row.setOnClickListener(v->{dialog.dismiss();selectedCommitmentId=item.id;selectedTab=11;render();});
            list.addView(row);divider(list);
        }
        list.addView(label("الإجمالي "+money(total,"EGP"),17,INK,true));markDetailActions(list);dialog.show();
    }

    private void bucketRow(LinearLayout list, MoneyFlow.Bucket bucket, MoneyFlow.Summary s) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        View dot = new View(this); dot.setBackground(rounded(bucketColor(bucket), 5));
        row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(MoneyFlow.label(bucket), 14, INK, true));
        copy.addView(label(bucketDetail(bucket, s), 11, bucket == MoneyFlow.Bucket.OPEN_TRANSFER && s.countOf(bucket) > 0 ? ORANGE : MUTED, false));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(10));
        row.addView(copy, copyParams);
        String sign = bucket == MoneyFlow.Bucket.INFLOW ? "+" : bucket == MoneyFlow.Bucket.INTERNAL || bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL ? "" : "−";
        TextView amount = label(sign + money(s.of(bucket), "EGP"), 14, bucket == MoneyFlow.Bucket.INTERNAL || bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL ? MUTED : bucketColor(bucket), true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        row.addView(amount);
        row.setOnClickListener(v -> openBucket(bucket, "", bucket == MoneyFlow.Bucket.OPEN_TRANSFER));
        list.addView(row);
    }

    private void legend(LinearLayout box) {
        LinearLayout legend = new LinearLayout(this); legend.setGravity(Gravity.CENTER_VERTICAL);
        for (MoneyFlow.Bucket bucket : new MoneyFlow.Bucket[]{MoneyFlow.Bucket.LIVING, MoneyFlow.Bucket.COMMITMENT, MoneyFlow.Bucket.OPEN_TRANSFER}) {
            View dot = new View(this); dot.setBackground(rounded(bucketColor(bucket), 4));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8)); dotParams.setMarginEnd(dp(4));
            if (legend.getChildCount() > 0) dotParams.setMarginStart(dp(10));
            legend.addView(dot, dotParams);
            legend.addView(label(bucket == MoneyFlow.Bucket.OPEN_TRANSFER ? "تحويلات لناس" : bucket == MoneyFlow.Bucket.COMMITMENT ? "أقساط والتزامات" : "صرف يومي", 11, MUTED, false));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6);
        box.addView(legend, params);
    }

    private static final MoneyFlow.Bucket[] OUT_BUCKETS = {MoneyFlow.Bucket.LIVING, MoneyFlow.Bucket.COMMITMENT, MoneyFlow.Bucket.OPEN_TRANSFER};

    private void yearChart(MoneyFlow.Summary s) {
        section("أي شهر زاد وليه؟", null, null);
        LinearLayout box = card();
        List<java.time.YearMonth> months = new ArrayList<>(s.byMonth.keySet());
        long[][] values = new long[months.size()][OUT_BUCKETS.length];
        String[] labels = new String[months.size()];
        MoneyFlow.MonthPeak peak = s.peak();
        int highlight = -1;
        for (int i = 0; i < months.size(); i++) {
            long[] month = s.byMonth.get(months.get(i));
            for (int b = 0; b < OUT_BUCKETS.length; b++) values[i][b] = month[OUT_BUCKETS[b].ordinal()];
            labels[i] = MONTH_SHORT[months.get(i).getMonthValue() - 1];
            if (peak != null && months.get(i).equals(peak.month)) highlight = i;
        }
        int[] colors = new int[OUT_BUCKETS.length];
        for (int b = 0; b < OUT_BUCKETS.length; b++) colors[b] = bucketColor(OUT_BUCKETS[b]);
        UiKit.StackedBars chart = new UiKit.StackedBars(this);
        chart.setData(values, labels, colors, highlight, MUTED, BORDER, REGULAR,
                "اللي خرج كل شهر في " + insightsPeriodName() + "، دوس على شهر لتفاصيله", index -> {
                    selectedMonth = months.get(index).atDay(1); insightsRange = MoneyFlow.Range.MONTH; render();
                });
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(170)); chartParams.topMargin = dp(4);
        box.addView(chart, chartParams);
        legend(box);
        if (peak != null) {
            String sentence = "أعلى شهر: " + MONTH_NAMES[peak.month.getMonthValue() - 1] + " " + money(peak.total, "EGP") +
                    "، أعلى من متوسط باقي الشهور (" + money(peak.othersAverage, "EGP") + ") بـ " + money(peak.total - peak.othersAverage, "EGP") + ".";
            if (peak.driver != null)
                sentence += " أغلب الزيادة من " + MoneyFlow.label(peak.driver) + " (+" + money(peak.driverGap, "EGP") + ").";
            TextView explain = label(sentence, 13, INK, false);
            LinearLayout.LayoutParams explainParams = new LinearLayout.LayoutParams(-1, -2); explainParams.topMargin = dp(10);
            box.addView(explain, explainParams);
        }
        box.addView(label("دوس على أي شهر يفتح تفاصيله.", 11, MUTED, false));
        LinearLayout list = listCard(page);
        for (int i = months.size() - 1; i >= 0; i--) {
            java.time.YearMonth month = months.get(i);
            long out = s.monthOut(month);
            if (list.getChildCount() > 0) divider(list);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(9), 0, dp(9));
            row.addView(label(MONTH_NAMES[month.getMonthValue() - 1] + " " + month.getYear(), 13, INK, i == highlight), new LinearLayout.LayoutParams(0, -2, 1));
            long[] parts = s.byMonth.get(month);
            TextView detail = label("صرف " + shortMoney(parts[MoneyFlow.Bucket.LIVING.ordinal()]) + " · التزامات " +
                    shortMoney(parts[MoneyFlow.Bucket.COMMITMENT.ordinal()]), 11, MUTED, false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-2, -2); detailParams.setMarginEnd(dp(8));
            row.addView(detail, detailParams);
            TextView amount = label(money(out, "EGP"), 13, INK, true);
            amount.setTextDirection(View.TEXT_DIRECTION_LTR);
            row.addView(amount);
            row.setOnClickListener(v -> { selectedMonth = month.atDay(1); insightsRange = MoneyFlow.Range.MONTH; render(); });
            list.addView(row);
        }
    }

    /** Rounded thousands for secondary detail lines only; headline amounts always use money(). */
    private static String shortMoney(long minor) {
        long pounds = Math.round(minor / 100.0);
        return pounds >= 1000 ? String.format(Locale.ROOT, "%,.1fK", pounds / 1000.0) : Long.toString(pounds);
    }

    private void monthChart(MoneyFlow.Summary s, LocalDate[] period) {
        section("كل يوم في الشهر", null, null);
        LinearLayout box = card();
        int days = (int) ChronoUnit.DAYS.between(period[0], period[1]);
        long[][] values = new long[days][OUT_BUCKETS.length];
        String[] labels = new String[days];
        for (int i = 0; i < days; i++) {
            LocalDate day = period[0].plusDays(i);
            long[] parts = s.byDay.get(day);
            if (parts != null) for (int b = 0; b < OUT_BUCKETS.length; b++) values[i][b] = parts[OUT_BUCKETS[b].ordinal()];
            labels[i] = Integer.toString(day.getDayOfMonth());
        }
        int[] colors = new int[OUT_BUCKETS.length];
        for (int b = 0; b < OUT_BUCKETS.length; b++) colors[b] = bucketColor(OUT_BUCKETS[b]);
        UiKit.StackedBars chart = new UiKit.StackedBars(this);
        chart.setData(values, labels, colors, -1, MUTED, BORDER, REGULAR, "اللي خرج كل يوم في " + insightsPeriodName(), index -> {
            LocalDate day = period[0].plusDays(index);
            transactionPeriod = new LocalDate[]{day, day.plusDays(1)};
            transactionKindFilter = ""; transactionCategoryFilter = ""; transactionBucketFilter = ""; transactionSourceFilter = null;
            transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionSearch = ""; searchOpen = false;
            selectedTab = 6; render();
        });
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(150)); chartParams.topMargin = dp(4);
        box.addView(chart, chartParams);
        legend(box);
        // Compare with the previous calendar month, bucket by bucket.
        LocalDate previousStart = period[0].minusMonths(1);
        LocalDate[] previousPeriod = {previousStart, period[0]};
        MoneyFlow.Summary previous = MoneyFlow.summarize(flowItems(previousPeriod), previousPeriod, ZoneId.systemDefault()).get("EGP");
        if (previous != null && !previous.isEmpty()) {
            String name = MONTH_NAMES[previousStart.getMonthValue() - 1];
            StringBuilder text = new StringBuilder("مقارنة بـ" + name + ":");
            for (MoneyFlow.Bucket bucket : OUT_BUCKETS) {
                long delta = s.of(bucket) - previous.of(bucket);
                text.append("\n").append(MoneyFlow.label(bucket)).append(" ")
                        .append(delta == 0 ? "زي ما هو" : (delta > 0 ? "زاد " : "قل ") + money(Math.abs(delta), "EGP"));
            }
            TextView compare = label(text.toString(), 13, INK, false);
            LinearLayout.LayoutParams compareParams = new LinearLayout.LayoutParams(-1, -2); compareParams.topMargin = dp(10);
            box.addView(compare, compareParams);
        }
        box.addView(label("دوس على أي يوم يفتح عملياته.", 11, MUTED, false));
    }

    private void reviewSection(MoneyFlow.Summary s) {
        List<RepairRules.RepeatingTransfer> repeating = db.repeatingTransferSuggestions();
        int open = s.countOf(MoneyFlow.Bucket.OPEN_TRANSFER);
        if (s.uncategorized == 0 && open == 0 && repeating.isEmpty()) return;
        section("محتاج مراجعة", null, null);
        for (int i = 0; i < Math.min(3, repeating.size()); i++) repeatingCard(repeating.get(i));
        if (open > 0)
            tipCard("⇄", open + " تحويل لناس غرضها مش محدد", "إجمالي " + money(s.of(MoneyFlow.Bucket.OPEN_TRANSFER), "EGP") +
                    ". حدد غرضها (أكل، إيجار، عائلة...) علشان تتحسب في مكانها الصح.", "حدد الغرض",
                    () -> openBucket(MoneyFlow.Bucket.OPEN_TRANSFER, "", true));
        if (s.uncategorized > 0)
            tipCard("✦", s.uncategorized + " عملية صرف غير مصنفة", "إجمالي " + money(s.uncategorizedAmount, "EGP") +
                    ". صنّفها مرة واحدة بالتحديد الجماعي.", "صنّفها", () -> openBucket(MoneyFlow.Bucket.LIVING, MoneyFlow.UNCATEGORIZED, true));
    }

    private void repeatingCard(RepairRules.RepeatingTransfer pattern) {
        LinearLayout box = card();
        box.addView(label("تحويل ثابت كل شهر · ده قسط؟", 15, INK, true));
        box.addView(label(money(pattern.amountMinor, pattern.currency) + " من " + db.sourceLabel(pattern.sourceId) + " · اتكرر " +
                pattern.ids.size() + " مرة · غالبًا يوم " + pattern.typicalDay + " · آخر مرة " +
                Instant.ofEpochMilli(pattern.lastAt).atZone(ZoneId.systemDefault()).toLocalDate(), 13, MUTED, false));
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2); actionsParams.topMargin = dp(8);
        box.addView(actions, actionsParams);
        addChip(actions, "أيوه، قسط", true, () -> confirmInstallmentDialog(pattern));
        addChip(actions, "حدد الغرض بس", false, () -> purposeDialog(pattern.ids));
        addChip(actions, "مش قسط", false, () -> { db.dismissRepeatingTransfer(pattern); render(); });
    }

    private void confirmInstallmentDialog(RepairRules.RepeatingTransfer pattern) {
        LinearLayout form = form();
        form.addView(label(money(pattern.amountMinor, pattern.currency) + " كل شهر من " + db.sourceLabel(pattern.sourceId), 14, INK, true));
        EditText name = input("اسم القسط (مثلًا: قسط الشقة)", form);
        EditText last = dateInput("تاريخ آخر قسط", form, null);
        EditText paid = input("اتدفع كام قسط لحد دلوقتي", form);
        paid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        paid.setText(Integer.toString(pattern.ids.size()));
        form.addView(label("لقينا " + pattern.ids.size() + " دفعة في رسايلك. لو فيه دفعات قبل كده من غير رسايل عدّل الرقم.", 12, MUTED, false));
        AlertDialog dialog = new ChoiceDialogBuilder().setTitle("سجّله كقسط").setView(scrollForm(form))
                .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                LocalDate lastDue = java.util.Objects.requireNonNull(dateValue(last));
                int paidSoFar = Integer.parseInt(ExpenseParser.normalizeDigits(paid.getText().toString().trim()));
                String title = name.getText().toString().trim();
                if (title.isEmpty()) throw new IllegalArgumentException("name");
                db.confirmRepeatingTransfer(pattern, title, lastDue, paidSoFar, LocalDate.now());
                dialog.dismiss(); toast("اتسجل " + title + " · دفعاته القديمة اتنقلت للأقساط"); render();
            } catch (RuntimeException invalid) { toast("اكتب الاسم واختار تاريخ آخر قسط"); }
        }));
        dialog.show();
    }

    private void purposeDialog(List<Long> ids) {
        String[] purposes = PURPOSES;
        new ChoiceDialogBuilder().setTitle("غرض التحويلات دي").setItems(purposes, (d, which) -> {
            int changed = db.updateCategories(ids, purposes[which]);
            toast("اتحدد غرض " + changed + " تحويل"); render();
        }).setNegativeButton("إلغاء", null).show();
    }

    private void budgetsSection() {
        section("ميزانياتك", "+ خطة", this::budgetDialog);
        DailySummary summary = DailySummary.load(this, db);
        String code = getSharedPreferences("widget_settings", MODE_PRIVATE).getString("currency", "EGP");
        DailyAllowance.Result daily = summary.currencies.get(code);
        tipCard("☀", "ميزانية اليوم", daily != null && daily.valid ? summary.remaining(code, false) + " · الحد " + money(daily.plan.limit, code) :
                "حد ثابت للصرف اليومي؛ الأقساط والتحويلات مش داخلة فيه", "عدّلها", this::dailyBudgetDialog);
        tipCard("☆","نقاط الالتزام","نقاط على صافي الصرف اليومي بعد الاسترداد؛ كل عملة منفصلة","راجع النقاط",()->pointsDialog(code));
        for (ExpenseDb.BudgetPlan budget : db.budgetPlans()) {
            LocalDate[] period = BudgetPeriod.bounds(budget.cycle, budget.anchor, budget.durationDays, LocalDate.now());
            long spent = db.spentForBudget(budget, LocalDate.now());
            LinearLayout card = card();
            LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(label(budget.name, 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(label(cycleLabel(budget.cycle), 11, MUTED, false));
            card.addView(head);
            card.addView(label(money(spent, budget.currency) + " من " + money(budget.amountMinor, budget.currency) + " · " +
                    period[0] + " — " + period[1].minusDays(1), 12, MUTED, false));
            progress(card, spent, budget.amountMinor);
            card.addView(label(spent > budget.amountMinor ? "متجاوز بـ " + money(spent - budget.amountMinor, budget.currency) :
                    "متبقي " + money(budget.amountMinor - spent, budget.currency), 12, spent > budget.amountMinor ? CORAL : ACCENT, true));
            card.setOnClickListener(v -> new ChoiceDialogBuilder().setTitle(budget.name)
                    .setItems(new String[]{"تعديل", "حذف"}, (d, which) -> {
                        if (which == 0) budgetPlanDialog(budget);
                        else { db.deleteBudgetPlan(budget.id); render(); }
                    }).show());
        }
    }

    private AccountMovement accountMovement(long account,List<ExpenseDb.Transaction> rows) {
        AccountMovement totals=new AccountMovement();
        Map<Long,Long> roots=new java.util.HashMap<>();
        for(ExpenseDb.PaymentSource source:db.paymentSources()) roots.put(source.id,source.parentSourceId==null?source.id:source.parentSourceId);
        for(ExpenseDb.Transaction tx:rows) totals.add(tx.kind,tx.category,tx.amountMinor,
                tx.paymentSourceId!=null && Long.valueOf(account).equals(roots.get(tx.paymentSourceId)),
                tx.destinationSourceId!=null && Long.valueOf(account).equals(roots.get(tx.destinationSourceId)));
        return totals;
    }

    private void accountsSection(LocalDate[] period) {
        Map<Long,List<ExpenseDb.Transaction>> groups=db.accountTransactionGroups(period[0],period[1],"EGP");
        if(groups.isEmpty()) return;
        Map<Long,AccountMovement> totals=new LinkedHashMap<>();long max=1;
        for(Map.Entry<Long,List<ExpenseDb.Transaction>> group:groups.entrySet()) {
            AccountMovement movement=accountMovement(group.getKey(),group.getValue());totals.put(group.getKey(),movement);
            max=Math.max(max,Math.max(movement.incoming,movement.outgoing()));
        }
        section("حسب الحساب ووسيلة الدفع",null,null);
        note("الحساب يجمع كروته المرتبطة فقط. الوارد والخارج حركة مسجلة بالجنيه؛ التحويل بين حساباتك منفصل، ومش كشف بنك كامل.");
        LinearLayout list=listCard(page);
        for(ExpenseDb.PaymentSource source:db.accountSources(true)) {
            AccountMovement movement=totals.get(source.id);if(movement==null) continue;
            if(list.getChildCount()>0) divider(list);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(12),0,dp(12));
            row.addView(sourceBadge(source,38),new LinearLayout.LayoutParams(dp(38),dp(38)));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(label(db.sourceLabel(source.id),14,INK,true));
            List<ExpenseDb.PaymentSource> cards=db.linkedCards(source.id);
            if(!cards.isEmpty()) copy.addView(label("يشمل "+cards.size()+" كروت مرتبطة بالحساب",11,MUTED,false));
            else if("بطاقة".equals(source.kind)) copy.addView(label("كارت غير مرتبط · حركته وحده",11,ORANGE,false));
            copy.addView(flowLine(movement.incoming,max,EMERALD,"+",INCOME));
            copy.addView(flowLine(movement.outgoing(),max,0xffe46a6a,"−",CORAL));
            if(movement.ownIncoming+movement.ownOutgoing>0) copy.addView(label("بين حساباتك: وارد "+money(movement.ownIncoming,"EGP")+" · خارج "+money(movement.ownOutgoing,"EGP"),11,MUTED,false));
            if(movement.withdrawn>0) copy.addView(label("سحب نقدي: "+money(movement.withdrawn,"EGP")+" · مش مصروف",11,MUTED,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMarginStart(dp(12));row.addView(copy,cp);
            row.addView(tintedIcon(R.drawable.ic_chevron_forward,MUTED),new LinearLayout.LayoutParams(dp(18),dp(18)));
            row.setOnClickListener(v->{flowSourceId=source.id;selectedTab=14;render();});list.addView(row);
        }
        int unknown=0;
        for(ExpenseDb.Transaction tx:db.transactionsBetween(period[0],period[1]))
            if("EGP".equals(tx.currency) && tx.paymentSourceId==null && !("transfer".equals(tx.kind)&&tx.destinationSourceId!=null)) unknown++;
        if(unknown>0) note(unknown+" عمليات بلا مصدر محدد مش داخلة في توزيع الحسابات.");
    }

    private View flowLine(long value, long max, int color, String sign, int textColor) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(3), 0, 0);
        View bar = new View(this);
        bar.setBackground(rounded(color, 3));
        float weight = Math.max(0.02f, value / (float) max);
        line.addView(bar, new LinearLayout.LayoutParams(0, dp(6), weight * 0.75f));
        View rest = new View(this);
        line.addView(rest, new LinearLayout.LayoutParams(0, dp(6), 0.75f - weight * 0.75f + 0.25f));
        TextView amount = label(sign + money(value, "EGP"), 11, textColor, false);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(-2, -2); amountParams.setMarginStart(dp(6));
        line.addView(amount, amountParams);
        return line;
    }

    /** Opens exactly the transactions behind a number: same period, currency and bucket. */
    private void openBucket(MoneyFlow.Bucket bucket, String category, boolean review) {
        transactionBucketFilter = bucket == null ? "" : bucket.name();
        transactionCategoryFilter = category;
        transactionKindFilter = "";
        transactionSourceFilter = null;
        transactionPeriod = insightsPeriod(); transactionCurrencyFilter = "EGP";
        transactionExcludeTransfers = false;
        transactionSearch = ""; searchOpen = false;
        bulkMode = review; bulkSelected.clear();
        transactionsBackTab = 4;
        selectedTab = 6;
        render();
    }

    /** Opens exactly the rows behind a home statement number: same period (pay day → today), EGP, and bucket filter. */
    private void openStatement(String bucketFilter, boolean review) {
        resetTransactionFilters();
        transactionBucketFilter = bucketFilter;
        transactionPeriod = statementPeriod();
        transactionCurrencyFilter = "EGP";
        transactionStatementList = true;
        bulkMode = review;
        transactionsBackTab = 0;
        selectedTab = 6;
        render();
    }

    private void resetTransactionFilters() {
        transactionsReturnDialog="";transactionsPointsCurrency="";transactionStatementList=false;
        transactionBucketFilter="";transactionCategoryFilter="";transactionKindFilter="";transactionCurrencyFilter="";
        transactionSourceFilter=null;transactionPeriod=null;transactionExcludeTransfers=false;transactionSearch="";searchOpen=false;
        bulkMode=false;bulkSelected.clear();transactionsBackTab=0;
    }

    private void chooseYear() {
        java.util.TreeSet<Integer> years = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        for (String month : db.months()) years.add(Integer.parseInt(month.substring(0, 4)));
        years.add(LocalDate.now().getYear());
        List<Integer> list = new ArrayList<>(years);
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = Integer.toString(list.get(i));
        new ChoiceDialogBuilder().setTitle("اختار السنة").setItems(names, (d, which) -> {
            int year = list.get(which);
            selectedMonth = year == LocalDate.now().getYear() ? LocalDate.now().withDayOfMonth(1) : LocalDate.of(year, 12, 1);
            render();
        }).setNegativeButton("إلغاء", null).show();
    }

    // ---- حركة الحساب (opened from «حسب الحساب») ----
    private LocalDate[] flowPeriod() { return insightsPeriod(); }

    private String flowPeriodName() { return insightsPeriodName(); }

    /** A sent transfer to someone else whose purpose is not set yet (MoneyFlow OPEN_TRANSFER). */
    private static boolean isSentTransfer(ExpenseDb.Transaction tx) {
        return MoneyFlow.bucket(tx.kind, tx.category) == MoneyFlow.Bucket.OPEN_TRANSFER;
    }

    private void flowAccountScreen() {
        ExpenseDb.PaymentSource source = null;
        for (ExpenseDb.PaymentSource item : db.paymentSources()) if (item.id == flowSourceId) source = item;
        if (source == null) { selectedTab = 4; insightsScreen(); return; }
        ExpenseDb.PaymentSource account = db.paymentSource(db.accountId(source.id));
        if(account==null){selectedTab=4;insightsScreen();return;}
        final ExpenseDb.PaymentSource selectedAccount=account;
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", () -> { selectedTab = 4; render(); }),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.setMarginStart(dp(10));
        bar.addView(label(("بطاقة".equals(account.kind)?"حركة الكارت · ":"حركة الحساب · ") + flowPeriodName(), 18, INK, true), titleParams);
        page.addView(bar);
        LinearLayout face = bankCard(account);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(-1, dp(176)); faceParams.topMargin = dp(14);
        page.addView(face, faceParams);

        LocalDate[] period = flowPeriod();
        int days = (int) Math.min(31, ChronoUnit.DAYS.between(period[0], period[1]));
        LocalDate chartStart = period[1].minusDays(days);
        long[] in = new long[days], out = new long[days], own = new long[days];
        List<ExpenseDb.Transaction> rows = db.accountTransactionGroups(period[0],period[1],"EGP").getOrDefault(account.id,new ArrayList<>());
        AccountMovement totals=accountMovement(account.id,rows);
        Map<Long,Long> roots=new java.util.HashMap<>();
        for(ExpenseDb.PaymentSource item:db.paymentSources())roots.put(item.id,item.parentSourceId==null?item.id:item.parentSourceId);
        for (ExpenseDb.Transaction tx : rows) {
            boolean from = tx.paymentSourceId != null && Long.valueOf(account.id).equals(roots.get(tx.paymentSourceId));
            boolean to = tx.destinationSourceId != null && Long.valueOf(account.id).equals(roots.get(tx.destinationSourceId));
            if (!"EGP".equals(tx.currency)) continue;
            int day = (int) ChronoUnit.DAYS.between(chartStart, Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate());
            boolean inChart = day >= 0 && day < days;
            if ("transfer".equals(tx.kind)) { if (inChart && from!=to) own[day] += tx.amountMinor; }
            else if ("income".equals(tx.kind)) { if (inChart) in[day] += tx.amountMinor; }
            else if ("expense".equals(tx.kind)) {
                if (inChart) out[day] += tx.amountMinor;
            }
        }
        LinearLayout stats = new LinearLayout(this);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-1, -2); statsParams.topMargin = dp(12);
        page.addView(stats, statsParams);
        miniStat(stats, "مصروف مسجل", totals.spent, CORAL);
        miniStat(stats, "تحويلات لناس", totals.sent, ORANGE);
        miniStat(stats, "وارد مسجل", totals.incoming, INCOME);
        if(totals.withdrawn>0) note("سحب نقدي: "+money(totals.withdrawn,"EGP")+" · اتنقل من الحساب لكاش ومش مصروف.");
        List<ExpenseDb.PaymentSource> linked=db.linkedCards(account.id);
        if(!linked.isEmpty()) {
            StringBuilder names=new StringBuilder();for(ExpenseDb.PaymentSource card:linked) names.append(names.length()==0?"":"، ").append("•").append(card.last4);
            note("حركة الحساب تشمل الكروت المرتبطة: "+names);
        } else if("بطاقة".equals(account.kind)) {
            note("الكارت ده مش مرتبط بحساب. الوارد والتحويلات الظاهرة تخص رقم الكارت فقط؛ صفر مسجل مش معناه إن الحساب البنكي مفيهوش حركة.");
            TextView link=label("اربط الكارت بحسابه",13,ACCENT,true);link.setPadding(0,dp(12),0,dp(12));page.addView(link);
            link.setOnClickListener(v->linkSourceDialog(selectedAccount));
        }
        if(totals.ownIncoming+totals.ownOutgoing>0) note("تحويلات بين حساباتك: وارد "+money(totals.ownIncoming,"EGP")+" · خارج "+money(totals.ownOutgoing,"EGP")+"؛ مش محسوبة دخل أو مصروف.");
        note("الأرقام حركات البنك المسجلة؛ تشمل المشتريات والالتزامات والسحب والرسوم، ولا تخصم استردادًا من الحركة الأصلية. تحويلات الناس المحدد غرضها تظهر ضمن المصروف.");

        LinearLayout chartCard = card();
        LinearLayout chartHead = new LinearLayout(this); chartHead.setGravity(Gravity.CENTER_VERTICAL);
        chartHead.addView(label("كل يوم", 14, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        chartHead.addView(label("فوق وارد · تحت خارج", 11, MUTED, false));
        chartCard.addView(chartHead);
        UiKit.FlowBars chart = new UiKit.FlowBars(this);
        chart.setData(in, out, own, EMERALD, 0xffe46a6a, dark ? 0xff5a6a63 : 0xffc9cfc6, BORDER);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, dp(120)); chartParams.topMargin = dp(8);
        chartCard.addView(chart, chartParams);
        chartCard.addView(label("الرمادي تحويلات بين حساباتك · "+chartStart+" إلى "+period[1].minusDays(1)+" · EGP", 11, MUTED, false));

        section("عمليات الحساب", null, null);
        if (rows.isEmpty()) empty("مفيش عمليات على الحساب ده", "في الفترة اللي اخترتها");
        else {
            LinearLayout list = listCard(page);
            for (int i = 0; i < Math.min(40, rows.size()); i++) transactionRow(list, rows.get(i), false);
            if(rows.size()>40) note("أحدث 40 من "+rows.size()+" عملية؛ الإجماليات تشمل الفترة كاملة.");
        }
    }

    private void miniStat(LinearLayout row, String title, long value, int color) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(6), dp(10), dp(6), dp(10));
        box.setBackground(rounded(SURFACE, 16));
        box.addView(centered(label(title, 11, MUTED, false)));
        TextView amount = label(money(value, "EGP"), 13, color, true);
        amount.setSingleLine(true);
        box.addView(centered(amount));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) params.setMarginStart(dp(8));
        row.addView(box, params);
    }

    /**
     * Collects the shapes of bank messages with every digit, month and handle masked, grouped by sender, so the user
     * can review them and share them to improve the parser. Three sections: income read, outgoing transfers read, and
     * — the useful one — messages from a known bank that the parser did not read (SmsPreview.canReport). Shapes are
     * listed per sender (top three each), so a bank with one fixed template cannot crowd the others out of the list.
     * Nothing is sent automatically.
     */
    private void messageSamples() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            toast("محتاج إذن قراءة الرسائل الأول"); return;
        }
        toast("بجهّز العينات...");
        AppResources.read(() -> {
            Map<String, Map<String, int[]>> income = new LinkedHashMap<>();
            Map<String, Map<String, int[]>> sent = new LinkedHashMap<>();
            Map<String, Map<String, int[]>> unread = new LinkedHashMap<>();
            try (Cursor cursor = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY}, null, null, Telephony.Sms.DATE + " DESC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String sender = cursor.getString(0) == null ? "" : cursor.getString(0);
                    String body = cursor.getString(1);
                    if (body == null) continue;
                    Map<String, Map<String, int[]>> bucket = null;
                    if (ExpenseParser.parseIncome(sender, body) != null) bucket = income;
                    else {
                        ExpenseParser.Parsed parsed = ExpenseParser.parse(sender, body);
                        if (parsed != null && "تحويلات".equals(parsed.category)) bucket = sent;
                        else if (parsed == null && SmsPreview.canReport(sender, body)) bucket = unread;
                    }
                    if (bucket == null) continue;
                    String bank = BrandCatalog.bank(sender);
                    String group = (bank == null ? maskMessage(sender) : bank) + " · " + maskMessage(sender);
                    bucket.computeIfAbsent(group, k -> new LinkedHashMap<>()).computeIfAbsent(maskMessage(body), k -> new int[1])[0]++;
                }
            } catch (Exception error) {
                postUi(() -> toast("تعذر قراءة الرسائل"));
                return;
            }
            StringBuilder text = new StringBuilder("عينات Follow Money (الأرقام والشهور مخفية)\n");
            appendSamples(text, "رسائل من بنك معروف ما اتقرتش", unread);
            appendSamples(text, "رسائل دخل اتقرت", income);
            appendSamples(text, "تحويلات خارجة اتقرت", sent);
            String result = text.toString();
            postUi(() -> new ChoiceDialogBuilder().setTitle("راجعها قبل ما تبعتها")
                    .setMessage(result)
                    .setPositiveButton("مشاركة", (dialog, which) -> {
                        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result);
                        startActivity(Intent.createChooser(share, "مشاركة العينات"));
                    })
                    .setNeutralButton("نسخ", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("samples", result));
                        toast("اتنسخت");
                    })
                    .setNegativeButton("إغلاق", null).show());
        });
    }

    /** The masked shape of a message (SmsPreview.shape): one bank template → one shape. */
    static String maskMessage(String text) { return SmsPreview.shape(text); }

    /** Up to three shapes per sender, senders ordered by message count, so every bank is represented. */
    private static void appendSamples(StringBuilder text, String title, Map<String, Map<String, int[]>> groups) {
        int shapes = 0;
        for (Map<String, int[]> bucket : groups.values()) shapes += bucket.size();
        text.append("\n== ").append(title).append(" (").append(groups.size()).append(" مرسل · ").append(shapes).append(" شكل) ==\n");
        if (groups.isEmpty()) { text.append("لا شيء\n"); return; }
        List<Map.Entry<String, Map<String, int[]>>> senders = new ArrayList<>(groups.entrySet());
        senders.sort((a, b) -> Integer.compare(total(b.getValue()), total(a.getValue())));
        for (Map.Entry<String, Map<String, int[]>> sender : senders) {
            List<Map.Entry<String, int[]>> rows = new ArrayList<>(sender.getValue().entrySet());
            rows.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
            text.append("\n-- ").append(sender.getKey()).append(" · ").append(total(sender.getValue())).append(" رسالة · ").append(rows.size()).append(" شكل --\n");
            for (int i = 0; i < Math.min(3, rows.size()); i++)
                text.append("[").append(rows.get(i).getValue()[0]).append("×] ").append(rows.get(i).getKey()).append("\n");
        }
    }

    /** Flat shapes (the SMS coverage report): the ten most frequent. */
    private static void appendFlatSamples(StringBuilder text, String title, Map<String, int[]> bucket) {
        List<Map.Entry<String, int[]>> rows = new ArrayList<>(bucket.entrySet());
        rows.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        text.append("\n== ").append(title).append(" (").append(rows.size()).append(" شكل) ==\n");
        for (int i = 0; i < Math.min(10, rows.size()); i++)
            text.append("\n[").append(rows.get(i).getValue()[0]).append("×] ").append(rows.get(i).getKey()).append("\n");
    }

    private static int total(Map<String, int[]> bucket) {
        int n = 0;
        for (int[] count : bucket.values()) n += count[0];
        return n;
    }

    private void repairTransfersDialog() {
        CaptureQueue.submit(() -> {
            int found = db.repairOwnTransfers(false);
            postUi(() -> {
                if (found == 0) { toast("مفيش تحويلات قديمة بين حساباتك محتاجة تتصلح"); return; }
                new ChoiceDialogBuilder().setTitle("لقينا " + found + " تحويل بين حساباتك")
                        .setMessage("كل واحد منهم مبلغ خرج من حساب ودخل حساب تاني ليك في خلال 10 دقايق. " +
                                "اتسجلوا قبل كده صرف ودخل، فكانوا بيكبّروا الأرقام. نعلّمهم تحويل بين حساباتك؟")
                        .setPositiveButton("أيوه، صلّحهم", (dialog, which) -> CaptureQueue.submit(() -> {
                            int fixed = db.repairOwnTransfers(true);
                            postUi(() -> { toast("اتصلّح " + fixed + " تحويل"); render(); });
                        }))
                        .setNegativeButton("لأ", null).show();
            });
        });
    }

    private void diagnosticsDialog() {
        toast("بجهّز التقرير...");
        AppResources.read(() -> {
            LocalDate start = LocalDate.now().withDayOfYear(1);
            String report;
            try { report = db.diagnostics(start, LocalDate.now().plusDays(1)); }
            catch (RuntimeException error) { report = "تعذر عمل التقرير: " + error.getMessage(); }
            String result = report;
            postUi(() -> new ChoiceDialogBuilder().setTitle("تقرير الأرقام")
                    .setMessage(result)
                    .setPositiveButton("مشاركة", (dialog, which) -> {
                        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result);
                        startActivity(Intent.createChooser(share, "مشاركة التقرير"));
                    })
                    .setNeutralButton("نسخ", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("report", result));
                        toast("اتنسخ");
                    })
                    .setNegativeButton("إغلاق", null).show());
        });
    }

    /**
     * The brand opening (res/raw/app_intro.mp4, the same 5-second intro as the promo video) in a full-screen dialog,
     * so later render() calls cannot remove it. First launch plays it whole; later cold starts stop once the logo and
     * name are on screen (~3 s). A tap skips it; Settings can limit it to the first launch or turn it off.
     */
    private android.app.Dialog openingDialog;
    /** A message that arrived while the opening was playing (e.g. the repair summary) waits until it closes. */
    private Runnable afterOpening;

    private void playOpening(boolean forced) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String mode = prefs.getString("opening_mode", "every");
        boolean first = !prefs.getBoolean("opening_seen", false);
        if (!forced && ("off".equals(mode) || "first".equals(mode) && !first)) return;
        prefs.edit().putBoolean("opening_seen", true).apply();
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout stage = new FrameLayout(this);
        stage.setBackgroundColor(0xff042118);
        android.widget.VideoView video = new android.widget.VideoView(this);
        // Muting alone still lets VideoView take focus and pause background music.
        video.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);
        video.setContentDescription("فولو موني · تابع فلوسك من غير مجهود");
        stage.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        TextView skip = label("تخطي", 13, 0xffb9d3c8, false);
        skip.setPadding(dp(18), dp(10), dp(18), dp(10));
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        skipParams.bottomMargin = dp(76);
        stage.addView(skip, skipParams);
        TextView origin=label("بكل فخر، صُنع في مصر 🇪🇬",13,0xffb9d3c8,false);
        origin.setGravity(Gravity.CENTER);origin.setPadding(dp(18),dp(6),dp(18),dp(6));
        FrameLayout.LayoutParams originParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        originParams.bottomMargin=dp(34);stage.addView(origin,originParams);
        dialog.setContentView(stage);
        dialog.setCancelable(true);
        openingDialog = dialog;
        dialog.setOnDismissListener(d -> {
            openingDialog = null;
            Runnable next = afterOpening; afterOpening = null;
            if (next != null && !isFinishing()) next.run();
        });
        Runnable close = () -> {
            if (!dialog.isShowing() || isFinishing() || isDestroyed()) return;
            stage.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                video.stopPlayback();
                try { dialog.dismiss(); } catch (IllegalArgumentException ignored) {}
            }).start();
        };
        stage.setOnClickListener(v -> close.run());
        video.setOnClickListener(v -> close.run());
        video.setOnTouchListener((v, event) -> { if (event.getAction() == android.view.MotionEvent.ACTION_UP) v.performClick(); return true; });
        video.setOnCompletionListener(mp -> close.run());
        video.setOnErrorListener((mp, what, extra) -> { close.run(); return true; });
        video.setOnPreparedListener(mp -> {
            mp.setVolume(0f, 0f);
            // Full intro the first time (or on request); afterwards stop once the logo and name have landed.
            if (!first && !forced) stage.postDelayed(close, 3100);
        });
        video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.app_intro));
        dialog.show();
        video.start();
    }

    /**
     * Runs the data repair once per ExpenseDb.REPAIR_VERSION (or on demand): promotions booked as payments,
     * bank + notification copies of one payment, and money moved between the user's own accounts.
     */
    private void runRepair(boolean manual) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (!manual && prefs.getInt("repair_version", 0) >= ExpenseDb.REPAIR_VERSION) return;
        if (manual) toast("بفحص البيانات...");
        CaptureQueue.submit(() -> {
            ExpenseDb.RepairReport report;
            try { report = db.repairAll(); }
            catch (RuntimeException error) {
                android.util.Log.e("FollowMoney", "Data repair failed: " + error.getClass().getSimpleName());
                if (manual) postUi(() -> toast("تعذر فحص البيانات"));
                return;
            }
            prefs.edit().putInt("repair_version", ExpenseDb.REPAIR_VERSION).apply();
            postUi(() -> {
                if (isFinishing() || isDestroyed()) return;
                render();
                if (!report.changed()) { if (manual) toast("البيانات سليمة، مفيش حاجة تتصلح"); return; }
                if (introVisible) return;
                StringBuilder text = new StringBuilder();
                if (report.recategorized > 0) text.append("• ").append(report.recategorized)
                        .append(" عملية كانت غير مصنفة واتعرف تصنيفها (زي اشتراك ChatGPT).\n");
                if (report.promotions > 0) text.append("• ").append(report.promotions)
                        .append(" رسالة إعلان كانت متسجلة مصروف: اتشالت، ومتأرشفة بدليلها.\n");
                if (report.trailingSigns > 0) text.append("• ").append(report.trailingSigns)
                        .append(" عملية من رسائل HSBC (الإشارة بعد المبلغ) كانت مقلوبة أو باسم البنك: الإيداع بقى دخل، والوصف بقى اسم العملية.\n");
                if (report.duplicates > 0) text.append("• ").append(report.duplicates)
                        .append(" عملية كانت متسجلة مرتين (رسالة البنك + إشعار التطبيق): اتدمجت.\n");
                if (report.ownTransfers > 0) text.append("• ").append(report.ownTransfers).append(" تحويل بين حساباتك (")
                        .append(money(report.movedMinor, "EGP")).append(") كان متحسب صرف ودخل: بقى تحويل داخلي، والرسوم (")
                        .append(money(report.feesMinor, "EGP")).append(") مصروف لوحدها.\n");
                Runnable show = () -> new ChoiceDialogBuilder().setTitle("صححنا أرقامك").setMessage(text.toString().trim())
                        .setPositiveButton("تمام", null)
                        .setNeutralButton("افتح التحليلات", (d, w) -> { selectedTab = 4; render(); }).show();
                if (openingDialog != null && openingDialog.isShowing()) afterOpening = show; else show.run();
            });
        });
    }

    private void reviewToolsDialog() {
        if(!BuildConfig.DEBUG)return;
        String[] options = {"تصحيح البيانات: إعلانات وتكرار وتحويلات داخلية", "فحص تغطية رسائل SMS", "تقرير تشخيص الأرقام",
                "مراجعة التحويلات بين حساباتك", "عينات رسائل البنوك", "عطل تجريبي"};
        new ChoiceDialogBuilder().setTitle("أدوات المراجعة")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: runRepair(true); break;
                        case 1: smsCoverageDialog(); break;
                        case 2: diagnosticsDialog(); break;
                        case 3: repairTransfersDialog(); break;
                        case 4: messageSamples(); break;
                        case 5: debugCrashDialog(); break;
                        default: break;
                    }
                }).setNegativeButton("إغلاق", null).show();
    }

    private void debugCrashDialog() {
        if(!BuildConfig.DEBUG)return;
        new ChoiceDialogBuilder().setTitle("عطل تجريبي")
                .setMessage("هيقفل نسخة التطوير عشان تختبر حفظ آخر عطل. استخدم بيانات مصطنعة فقط.")
                .setPositiveButton("إحداث العطل",(dialog,which)->{
                    throw new IllegalStateException("Synthetic debug crash");
                }).setNegativeButton("إلغاء",null).show();
    }

    private void smsCoverageDialog() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            toast("محتاج إذن قراءة الرسائل الأول"); return;
        }
        toast("براجع الرسائل المسجلة...");
        AppResources.read(() -> {
            int scanned = 0, captured = 0, excluded = 0, missing = 0, completedReview = 0, otherReview = 0;
            Map<String, int[]> missingSamples = new LinkedHashMap<>();
            Map<String, int[]> reviewSamples = new LinkedHashMap<>();
            String error = null;
            try (ExpenseDb auditDb = new ExpenseDb(getApplicationContext());
                 Cursor cursor = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                         new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT},
                         null, null, Telephony.Sms.DATE + " DESC")) {
                Set<String> sentKeys=ExpenseDb.smsSentKeys(getApplicationContext(),0,Long.MAX_VALUE);
                if (cursor != null) while (cursor.moveToNext()) {
                    String sender = cursor.getString(0), body = cursor.getString(1);
                    if (body == null) continue;
                    scanned++;
                    boolean receiptKeySafe=CaptureIdentity.receiptKeySafe(sender,body,cursor.getLong(2),cursor.getLong(3),sentKeys);
                    int status = auditDb.smsCaptureStatus(sender, body, cursor.getLong(2), cursor.getLong(3),receiptKeySafe);
                    if (status == 1) { captured++; continue; }
                    if (status == 2) { excluded++; continue; }
                    boolean parsed = ExpenseParser.parse(sender, body) != null ||
                            ExpenseParser.parseIncome(sender, body) != null ||
                            ExpenseParser.parseTransfer(body) != null;
                    if (parsed) {
                        missing++;
                        String shape = maskMessage(sender == null ? "" : sender) + ": " + maskMessage(body);
                        missingSamples.computeIfAbsent(shape, key -> new int[1])[0]++;
                    } else if (ExpenseParser.isCompletedFinancialCandidate(body)) {
                        completedReview++;
                        String shape = maskMessage(sender == null ? "" : sender) + ": " + maskMessage(body);
                        reviewSamples.computeIfAbsent(shape, key -> new int[1])[0]++;
                    } else if (ExpenseParser.isFinancialCandidate(body)) {
                        otherReview++;
                    }
                }
            } catch (Exception failure) {
                error = "تعذر فحص صندوق SMS على هذا الجهاز";
            }
            String result;
            if (error != null) result = error;
            else {
                StringBuilder report = new StringBuilder("فحص تغطية SMS · كل الرسائل الموجودة حاليًا على الجهاز\n\n");
                report.append("اتقرت: ").append(scanned).append(" · لها مصدر مسجل: ").append(captured)
                        .append(" · مستبعدة يدويًا: ").append(excluded).append("\n");
                report.append("قابلة للتحليل لكن بلا مصدر مسجل: ").append(missing).append("\n");
                report.append("تبدو عملية مكتملة وتحتاج مراجعة: ").append(completedReview).append("\n");
                report.append("إشارات مالية أخرى (عروض/تذكيرات/أرصدة): ").append(otherReview).append("\n");
                report.append("المراجعة لا تثبت مصروفًا؛ شحن الرصيد قد يكون له خصم بنكي مسجل.\n");
                appendFlatSamples(report, "قابلة للتحليل وغير مسجلة", missingSamples);
                appendFlatSamples(report, "عمليات محتملة للمراجعة", reviewSamples);
                result = report.toString();
            }
            postUi(() -> new ChoiceDialogBuilder().setTitle("تغطية رسائل SMS")
                    .setMessage(result)
                    .setPositiveButton("نسخ", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("sms coverage", result));
                        toast("اتنسخ؛ راجع العينات قبل مشاركتها");
                    }).setNegativeButton("إغلاق", null).show());
        });
    }
}
