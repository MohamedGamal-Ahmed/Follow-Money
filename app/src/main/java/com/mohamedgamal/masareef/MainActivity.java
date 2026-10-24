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
    private int INK, MUTED, BACKGROUND, ACCENT, BRAND, BRAND_SOFT, EMERALD, LIME, INDIGO;
    private int CORAL, INCOME, GOLD, ACCENT_PALE, BORDER, SURFACE, ORANGE;
    private UiTheme uiTheme;
    private UiComponents ui;
    private UiPages formPages;
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
    private int homeStatementOpen; // Activity lifetime only: 0 closed, 1 income, 2 outflow.
    private long[] transactionStatementIds;
    /** Source id → kind, read once per render for the statement's internal/cash rules on list rows. */
    private Map<Long, String> sourceKindCache;
    private Map<Long, Long> statementOffsetCache;
    private String transactionBucketFilter = "";
    private long flowSourceId = -1;
    private java.time.YearMonth monthlyMonth=java.time.YearMonth.now().minusMonths(1);
    private String monthlyCurrency="EGP";
    private ExpenseDb.Recurring editingCommitment;
    private String transactionKindFilter = "";
    private String transactionCategoryFilter = "";
    /** Pseudo-category behind «عملية محتاجة تصنيف»: uncategorized expenses and every row waiting for review. */
    private static final String NEEDS_ATTENTION_FILTER = "محتاجة تصنيف أو مراجعة";
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
    /**
     * Owner acceptance (0.4.0): «تحويلات» is the open bucket itself (a transfer to a person, purpose unknown); a transfer to
     * one of the user's own accounts leaves it through this choice, which opens the reconcile dialog.
     */
    private static final String OWN_TRANSFER_CHOICE = "تحويل بين حساباتي";
    private static final String OPEN_TRANSFER_HINT = "«تحويلات» يعني تحويل لشخص غرضه مش محدد، فيفضل في «غرضها مش محدد». اختار الغرض، أو «تحويل بين حساباتي» لو راح لحساب من حساباتك.";
    private static final String[] CURRENCIES = {"EGP", "USD", "EUR", "GBP"};
    private ExpenseDb db;
    private AppLock appLock;
    private WhatsNew.Section whatsNew;
    private InAppUpdate updates;
    private boolean initialActionsPending, initialDirect, initialFresh, pendingIntentActions;
    private Intent deferredIncomingIntent;
    private LinearLayout page;
    private LinearLayout settingsGroup;
    private LocalDate selectedMonth = LocalDate.now().withDayOfMonth(1);
    private int selectedTab = 0;
    private String settingsSection = "";
    /** Row tag prefix a settings search hit scrolls to and highlights once; never runs the row's action. */
    private String settingsFocus;
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
        dark = UiTheme.dark(this, getPreferences(MODE_PRIVATE));
        if (dark) {
            setTheme(R.style.AppTheme_Dark);
        }
        super.onCreate(state);
        uiTheme = new UiTheme(this, dark);
        INK=uiTheme.ink; MUTED=uiTheme.muted; BACKGROUND=uiTheme.background; ACCENT=uiTheme.accent;
        BRAND=uiTheme.brand; BRAND_SOFT=uiTheme.brandSoft; EMERALD=uiTheme.emerald; LIME=uiTheme.lime;
        INDIGO=uiTheme.indigo; CORAL=uiTheme.danger; INCOME=uiTheme.income; GOLD=uiTheme.gold;
        ACCENT_PALE=uiTheme.pale; BORDER=uiTheme.border; SURFACE=uiTheme.surface; ORANGE=uiTheme.orange;
        appLock = new AppLock(this, this::onAppUnlocked);
        loadFonts();
        ui = new UiComponents(this, uiTheme, REGULAR);
        formPages = new UiPages(this, uiTheme, REGULAR, this::render);
        if (state != null) formPages.restore(state.getBundle("form_pages"), this::restoreFormPage);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(SURFACE);
        int systemBars = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && Build.VERSION.SDK_INT >= 27) systemBars |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemBars);
        db = AppResources.database(this);
        loadWhatsNew(readAsset("whats-new.txt"));
        updates = new InAppUpdate(this, () -> postUi(() -> { if (selectedTab == 0 && canRedrawNow()) render(); }));
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
            transactionStatementIds=state.getLongArray("statement_ids");
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
        // A recreated activity (theme follows the system, rotation) still holds the shared text; it was offered once.
        if (initialFresh) handleShare(getIntent());
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
        if (pendingIntentActions) {
            pendingIntentActions = false;
            Intent incoming = deferredIncomingIntent;
            deferredIncomingIntent = null;
            processIncomingIntent(incoming == null ? getIntent() : incoming);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (appLock.guard()) return;
        if (db != null) {render();refreshLedger(true);MonthlySummaryReceiver.refresh(this);}
        if (updates != null) updates.refresh();
        if (db != null && !introVisible && hasNotificationAccess()) {
            PaymentNotificationListener.refreshSelectedSources(this);
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
        if (updates != null) updates.dispose();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (appLock.guard()) { pendingIntentActions = true; deferredIncomingIntent = intent; return; }
        processIncomingIntent(intent);
    }

    private void processIncomingIntent(Intent intent) {
        if (formPages != null && formPages.isOpen()) {
            formPages.leaveAll(() -> processIncomingIntent(intent));
            return;
        }
        // A declined navigation must not become this Activity's launch intent after recreation.
        setIntent(intent);
        handleShare(intent);
        openTransactionFromIntent(intent);
        openCaptureEventFromIntent(intent);
        openWidgetAction(intent);
        openMonthlyIntent(intent);
        if(intent.getBooleanExtra("open_transactions",false)){intent.removeExtra("open_transactions");resetTransactionFilters();introVisible=false;selectedTab=6;render();}
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (formPages != null) out.putBundle("form_pages", formPages.snapshot());
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
        out.putLongArray("statement_ids",transactionStatementIds);out.putString("bucket_filter",transactionBucketFilter);out.putBoolean("statement_list",transactionStatementList);out.putString("insights_range",insightsRange.name());
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
            if(bankSource(source.kind)&&usableBank(source.name))banks.add(SourceIdentity.bank(source.name));
            if("حساب".equals(source.kind) || "محفظة".equals(source.kind) || ExpenseDb.APP_SOURCE_KIND.equals(source.kind)) {
                if(source.reviewed)confirmed++;else review++;
            }
        }
        note(confirmed+" حسابات مؤكدة · "+review+" حسابات تحتاج مراجعة · "+banks.size()+" بنوك");
        note("الرصيد المحسوب = آخر رصيد + الوارد − الخارج. يظل تقديريًا حسب تغطية الرسائل؛ افتح الحساب لمراجعة الرصيد والكروت.");
        if (sources.isEmpty()) empty("ضيف أول وسيلة دفع", "حدد نوع الوسيلة والبنك، وسمّيها باسم تعرفه. ربط الكارت بحسابه يحتاج تأكيدك.");
        String[] groups={"حساباتك", "بطاقات ائتمان", "كروت تحتاج ربط بالحساب", "مؤرشف — التاريخ محفوظ"};
        for(int group=0;group<groups.length;group++) {
            boolean heading=false;
            for (ExpenseDb.PaymentSource source : sources) {
            int sourceGroup=source.archived?3:"بطاقة ائتمان".equals(source.kind)?1:
                    ("حساب".equals(source.kind)||"محفظة".equals(source.kind)||appSource(source))?0:2;
            if(sourceGroup!=group)continue;
            if(!heading){section(groups[group],null,null);heading=true;}
            LinearLayout card = card();
            LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
            title.addView(sourceBadge(source,34),new LinearLayout.LayoutParams(dp(34),dp(34)));
            TextView name=label(db.sourceLabel(source.id),16,INK,true);
            LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);nameParams.setMarginStart(dp(10));title.addView(name,nameParams);
            android.widget.ImageButton manage=ui.icon(R.drawable.ic_more,"إدارة "+db.sourceLabel(source.id),()->sourceOptions(source));
            manage.setTag("source_manage_"+source.id);title.addView(manage,new LinearLayout.LayoutParams(dp(UiTheme.TOUCH),dp(UiTheme.TOUCH)));
            card.addView(title);
            card.addView(label(source.archived?"مؤرشف · يمكنك إعادة تفعيله":source.reviewed?"استخدام مؤكد منك":"مكتشف من الرسائل · يحتاج مراجعة",12,MUTED,false));
            if(bankSource(source.kind)&&!usableBank(source.name)&&!appSource(source)){
                card.addView(label("البنك لسه مش محدد؛ ده ممكن يمنع ظهور الكارت والحساب لبعض",12,CORAL,false));
                Button bank=ui.button("تحديد البنك",false,false,()->sourceBankPage(source));
                LinearLayout.LayoutParams bankParams=new LinearLayout.LayoutParams(-2,-2);bankParams.topMargin=dp(6);card.addView(bank,bankParams);
            }
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
            card.addView(label("آخر استخدام: "+sourceDate(db.sourceLastActivity(source.id)),12,MUTED,false));
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
        if(bankSource(source.kind)&&!appSource(source)){labels.add("تحديد البنك وتصحيح بياناته");actions.add(()->sourceBankPage(source));}
        if("بطاقة".equals(source.kind)&&!appSource(source)) {
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
        Bundle draft=new Bundle();draft.putLong("id",source.id);
        draft.putString("name",source.displayName==null?"":source.displayName);draft.putBoolean("reviewed",source.reviewed);
        savePageScroll();formPages.push(new SourceProfilePage(draft,draft));
    }

    private final class SourceProfilePage extends UiPages.Page {
        private TextView error;
        SourceProfilePage(Bundle draft,Bundle original){super("source_profile","تسمية الوسيلة",draft,original);}
        @Override void build(LinearLayout body,UiComponents components){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("id"));
            if(source==null){body.addView(components.label("الوسيلة دي لم تعد موجودة",UiTheme.BODY,CORAL));return;}
            LinearLayout top=new LinearLayout(MainActivity.this);top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(sourceBadge(source,40),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView identity=components.label(source.name+" · "+source.kind+UiKit.last4Suffix(source.last4),UiTheme.BODY,INK);
            identity.setPadding(dp(10),0,dp(10),0);top.addView(identity,new LinearLayout.LayoutParams(0,-2,1));body.addView(top);
            EditText name=components.field("اسم واضح ليك",body);name.setTag("source_profile_name");
            name.setText(draft.getString("name",""));name.setHint("مثال: حساب المصروفات");
            name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){draft.putString("name",s.toString());}
                public void afterTextChanged(Editable text){}});
            components.caption("الاستخدام",body);
            android.widget.CheckBox reviewed=new android.widget.CheckBox(MainActivity.this);reviewed.setText("راجعت الوسيلة وأعرف أني بستخدمها");
            reviewed.setTag("source_profile_reviewed");reviewed.setTypeface(REGULAR);reviewed.setTextSize(UiTheme.BODY);reviewed.setTextColor(INK);
            reviewed.setButtonTintList(ColorStateList.valueOf(ACCENT));reviewed.setMinHeight(dp(UiTheme.TOUCH));
            reviewed.setChecked(draft.getBoolean("reviewed"));reviewed.setOnCheckedChangeListener((b,checked)->draft.putBoolean("reviewed",checked));
            body.addView(reviewed);
            error=components.label("",UiTheme.BODY,CORAL);error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);
        }
        @Override boolean save(){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("id"));
            if(source==null){if(error!=null){error.setText("الوسيلة دي لم تعد موجودة؛ لم نحفظ أي تعديل");error.setVisibility(View.VISIBLE);}return false;}
            db.updateSourceProfile(source.id,draft.getString("name",""),source.archived,draft.getBoolean("reviewed"));return true;
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private void linkSourceDialog(ExpenseDb.PaymentSource source) {
        Bundle draft=new Bundle();draft.putLong("source",source.id);draft.putLong("selected",-1);
        savePageScroll();formPages.push(new SourceLinkPage(draft,draft));
    }

    private final class SourceLinkPage extends UiPages.Page {
        private TextView error;private AlertDialog confirm;
        SourceLinkPage(Bundle draft,Bundle original){super("source_link","الكارت والحساب",draft,original);}
        private String reason(ExpenseDb.PaymentSource source,ExpenseDb.PaymentSource option){
            if(source.archived||option.archived)return "الوسيلة مؤرشفة؛ فعّلها من إدارة الحسابات أولًا";
            if(!usableBank(source.name)||!usableBank(option.name))return "البنك مش محدد؛ حدده لكل وسيلة قبل الربط";
            if(!java.util.Objects.equals(SourceIdentity.bank(source.name),SourceIdentity.bank(option.name)))return "البنك المسجل مختلف؛ راجع بيانات البنك لو مكتوبة غلط";
            return "";
        }
        private void identify(LinearLayout body,ExpenseDb.PaymentSource source){
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(ui.shape(SURFACE,BORDER,UiTheme.BUTTON_RADIUS));
            LinearLayout top=new LinearLayout(MainActivity.this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(sourceBadge(source,40),new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView title=ui.label(db.sourceLabel(source.id),UiTheme.BODY,INK);title.setPadding(dp(10),0,dp(10),0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(top);
            card.addView(ui.label(usableBank(source.name)?"البنك: "+source.name:"البنك غير محدد؛ الاسم السابق مش هو اسم البنك",UiTheme.CAPTION,usableBank(source.name)?MUTED:CORAL));
            List<ExpenseDb.Transaction> recent=db.recentSourceTransactions(source.id);
            card.addView(ui.label(recent.isEmpty()?"لسه مفيش عمليات مسجلة بالوسيلة دي":"آخر عمليات تساعدك تتعرف عليها",UiTheme.CAPTION,MUTED));
            for(ExpenseDb.Transaction tx:recent)card.addView(ui.label(tx.merchant+" · "+money(tx.amountMinor,tx.currency)+"\n"+transactionTimeLabel(tx,true),UiTheme.CAPTION,INK));
            if(source.parentSourceId!=null)card.addView(ui.label("مرتبط حاليًا بـ "+db.sourceLabel(source.parentSourceId),UiTheme.CAPTION,ACCENT));
            Button edit=ui.button("تحديد البنك وتسمية الوسيلة",false,false,()->{ExpenseDb.PaymentSource current=db.paymentSource(source.id);if(current==null)toast("الوسيلة دي لم تعد موجودة");else sourceBankPage(current);});edit.setTag("source_edit_"+source.id);card.addView(edit);
            card.setTag("source_identity_"+source.id);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);body.addView(card,lp);
        }
        @Override void build(LinearLayout body,UiComponents components){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("source"));
            if(source==null){body.addView(components.label("الوسيلة دي لم تعد موجودة",UiTheme.BODY,CORAL));return;}
            boolean card="بطاقة".equals(source.kind);
            components.caption(card?"1 · اعرف الكارت اللي هتربطه":"1 · راجع الحساب اللي هتربط بيه الكارت",body);identify(body,source);
            if(!card&&!"حساب".equals(source.kind)){body.addView(components.label("الربط هنا لكارت خصم وحسابه فقط. كارت الائتمان له رحلة سداد منفصلة.",UiTheme.BODY,MUTED));return;}
            components.caption(card?"2 · اختار الحساب اللي الكارت بيسحب منه":"2 · اختار كارت الخصم التابع للحساب",body);
            int count=0;
            for(ExpenseDb.PaymentSource option:db.paymentSources()){
                if(option.id==source.id||!(card?"حساب".equals(option.kind)&&option.parentSourceId==null:"بطاقة".equals(option.kind)))continue;
                count++;identify(body,option);String blocked=reason(source,option);
                if(blocked.isEmpty()){
                    android.widget.RadioButton choose=new android.widget.RadioButton(MainActivity.this);choose.setText(draft.getLong("selected",-1)==option.id?"تم الاختيار":"اختيار الوسيلة دي");
                    choose.setTypeface(REGULAR);choose.setTextSize(UiTheme.BODY);choose.setTextColor(INK);choose.setMinHeight(dp(UiTheme.TOUCH));choose.setButtonTintList(ColorStateList.valueOf(ACCENT));
                    choose.setContentDescription("اختيار "+db.sourceLabel(option.id));choose.setChecked(draft.getLong("selected",-1)==option.id);
                    choose.setTag("source_link_option_"+option.id);choose.setPadding(dp(14),dp(12),dp(14),dp(12));choose.setBackground(ui.interactive(choose.isChecked()?ACCENT_PALE:SURFACE,uiTheme.fieldOutline,UiTheme.BUTTON_RADIUS));
                    choose.setOnClickListener(v->{draft.putLong("selected",option.id);rebuildSourceChoices();});body.addView(choose);
                }else body.addView(components.label(blocked,UiTheme.CAPTION,CORAL));
            }
            if(count==0){body.addView(components.label(card?"لسه مفيش حساب بنكي مضاف. ضيف حساب وحدد نفس البنك؛ تقدر ترجع تربط بعدين.":"لسه مفيش كارت خصم مضاف. كارت الائتمان مش بيظهر في الربط ده.",UiTheme.BODY,MUTED));body.addView(components.button("إضافة وسيلة دفع",false,false,()->sourceBankPage(null)));}
            components.caption("عرض العمليات للتعرّف على الوسيلة؛ مش دليل تلقائي على العلاقة بين الحساب والكارت.",body);
            body.addView(components.button("لسه مش عارف · أرجع بعدين",false,false,()->formPages.finishThrough("source_link")));
            error=components.label("",UiTheme.BODY,CORAL);error.setTag("source_link_error");error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);
        }
        private void rebuildSourceChoices(){formPages.rebuild();}
        @Override String saveLabel(){return "مراجعة الربط";}
        @Override boolean save(){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("source")),option=db.paymentSource(draft.getLong("selected",-1));
            String blocked=source==null||option==null?"اختار الكارت والحساب الأول؛ لو البنك مش واضح صحح بياناته من نفس الصفحة":reason(source,option);
            if(!blocked.isEmpty()){if(error!=null){error.setText(blocked);error.setVisibility(View.VISIBLE);error.post(()->error.requestRectangleOnScreen(new android.graphics.Rect(0,0,error.getWidth(),error.getHeight()),false));}return false;}
            if(confirm!=null&&confirm.isShowing())return false;
            boolean isCard="بطاقة".equals(source.kind);ExpenseDb.PaymentSource card=isCard?source:option,account=isCard?option:source;
            AlertDialog dialog=confirm=new ChoiceDialogBuilder().setTitle("تأكيد علاقة الكارت بالحساب")
                    .setMessage("الكارت: "+db.sourceLabel(card.id)+"\nبيسحب من الحساب: "+db.sourceLabel(account.id)+
                            (card.parentSourceId!=null&&!card.parentSourceId.equals(account.id)?"\nهيتغير الربط السابق: "+db.sourceLabel(card.parentSourceId):"")+"\n\nأكد فقط لو متأكد من العلاقة. ده هيجمع حركة الكارت ضمن رصيد الحساب مرة واحدة؛ الرسائل والعمليات هتفضل محفوظة.")
                    .setPositiveButton("أيوه، اربط",null).setNegativeButton("رجوع للاختيار",null).create();
            dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{
                    ExpenseDb.PaymentSource current=db.paymentSource(card.id);
                    if(current==null||!java.util.Objects.equals(current.parentSourceId,card.parentSourceId))throw new IllegalArgumentException("الربط اتغير؛ ارجع وراجعه من جديد");
                    db.linkCardToAccount(card.id,account.id);dialog.dismiss();formPages.finishThrough("source_link");toast("اتربط الكارت بالحساب؛ تقدر تراجع العلاقة أو تفكها من إدارة الوسيلة");
                }catch(RuntimeException exception){dialog.setMessage("لم نحفظ الربط. راجع الوسيلتين وحالة البنك والربط الحالي، ثم ارجع للاختيار.");}
            }));dialog.show();return false;
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private void balanceHistoryDialog(ExpenseDb.PaymentSource source) {
        List<ExpenseDb.BalanceSnapshot> history = db.balanceHistory(source.id, "EGP", 50);
        if (history.isEmpty()) { toast("لسه مفيش أرصدة مسجلة للحساب"); return; }
        long accountId = db.accountId(source.id);
        savePageScroll();
        formPages.push(new UiPages.Page("balance_history", "سجل الأرصدة", new Bundle(), new Bundle()) {
            @Override void build(LinearLayout body, UiComponents components) {
                body.addView(components.label(db.sourceLabel(accountId) + " · EGP · أحدث 50", UiTheme.BODY, MUTED));
                LinearLayout list = listCard(body);
                DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
                for (ExpenseDb.BalanceSnapshot snapshot : history) {
                    if (list.getChildCount() > 0) divider(list);
                    LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(10), 0, dp(10)); row.setMinimumHeight(dp(UiTheme.TOUCH));
                    LinearLayout copy = new LinearLayout(MainActivity.this); copy.setOrientation(LinearLayout.VERTICAL);
                    TextView stamp = components.label(Instant.ofEpochMilli(snapshot.occurredAt).atZone(ZoneId.systemDefault()).format(format), UiTheme.BODY, INK);
                    stamp.setTextDirection(View.TEXT_DIRECTION_LTR); copy.addView(stamp);
                    copy.addView(components.label(snapshot.source, UiTheme.CAPTION, MUTED));
                    row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView amount = label(money(snapshot.amountMinor, "EGP"), 15, INK, true); amount.setTextDirection(View.TEXT_DIRECTION_LTR);
                    row.addView(amount); list.addView(row);
                }
            }
            @Override boolean save() { return true; }
            @Override String saveLabel() { return "إغلاق"; }
            @Override boolean readOnly() { return true; }
        });
    }

    private void manualBalanceDialog(ExpenseDb.PaymentSource source) {
        Bundle draft=new Bundle();draft.putLong("id",source.id);draft.putString("amount","");draft.putString("currency",CURRENCIES[0]);
        savePageScroll();formPages.push(new ManualBalancePage(draft,draft));
    }

    private final class ManualBalancePage extends UiPages.Page {
        private EditText amount;private TextView error;
        ManualBalancePage(Bundle draft,Bundle original){super("manual_balance","تحديث الرصيد",draft,original);}
        @Override void build(LinearLayout body,UiComponents components){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("id"));
            if(source==null){body.addView(components.label("الوسيلة دي لم تعد موجودة",UiTheme.BODY,CORAL));return;}
            body.addView(components.label(db.sourceLabel(db.accountId(source.id)),UiTheme.BODY,MUTED));
            amount=components.amount(draft.getString("amount",""),body);amount.setTag("manual_balance_amount");amount.setHint("الرصيد الحالي");
            amount.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){draft.putString("amount",s.toString());amount.setError(null);if(error!=null)error.setVisibility(View.GONE);}
                public void afterTextChanged(Editable text){}});
            components.caption("العملة",body);
            components.choices(CURRENCIES,null,java.util.Arrays.asList(CURRENCIES).indexOf(draft.getString("currency")),i->draft.putString("currency",CURRENCIES[i]),body);
            body.addView(components.label("الرصيد ده هيبقى نقطة البداية للحساب من دلوقتي؛ العمليات والرسائل القديمة مش هتتغير.",UiTheme.CAPTION,MUTED));
            error=components.label("",UiTheme.BODY,CORAL);error.setTag("manual_balance_error");error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);
        }
        private boolean fail(String message){if(error!=null){error.setText(message);error.setVisibility(View.VISIBLE);}if(amount!=null){amount.setError(message);amount.requestFocus();}return false;}
        @Override boolean save(){
            ExpenseDb.PaymentSource source=db.paymentSource(draft.getLong("id"));
            if(source==null)return fail("الوسيلة دي لم تعد موجودة؛ لم نحفظ الرصيد");
            String raw=draft.getString("amount","");
            if(ExpenseParser.manualAmountMisread(raw))return fail("راجع المبلغ");
            long minor=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(raw));
            if(minor<0)return fail("راجع مبلغ الرصيد");
            db.putManualBalance(db.accountId(source.id),draft.getString("currency",CURRENCIES[0]),minor,System.currentTimeMillis());
            return true;
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private void settingsScreen() {
        String title=settingsSection.isEmpty()?"الإعدادات":
                "budget".equals(settingsSection)?"الميزانية والويدجت":
                "capture".equals(settingsSection)?"التقاط العمليات":
                "appearance".equals(settingsSection)?"المظهر والملف الشخصي":
                "alerts".equals(settingsSection)?"الإشعارات والتذكير":
                "data".equals(settingsSection)?"البيانات والخصوصية":
                "missed".equals(settingsSection)?"رسائل من بنوكك ما اتسجلتش":"المساعدة وعن التطبيق";
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
            settingsSearch();
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
                note("العمليات المسجلة تفضل محفوظة بعد مسح الإشعار. لو عملية ما ظهرتش، سيب إشعارها ظاهر وافتح Follow Money لإعادة فحص إشعارات التطبيقات المختارة؛ الإشعارات الممسوحة قبل التقاطها مش متاحة للاسترجاع.");
                setting(hasNotificationAccess()?"الوصول للإشعارات: مفعل":"تفعيل الوصول للإشعارات",this::openNotificationSettings);
                setting("حساباتك على إنستاباي",this::ownHandlesDialog);
                setting("قواعد التجار والتصنيف",()->{selectedTab=7;render();});
                setting("رسائل من بنوكك ما اتسجلتش",()->{settingsSection="missed";render();});
                break;
            case "missed":
                missedBankMessages();
                break;
            case "appearance":
        setting("اسم العرض على الرئيسية", this::displayNamePage);
        SharedPreferences appearance = getPreferences(MODE_PRIVATE);
        int appearanceChoice = !appearance.contains("dark_mode") ? 0 : appearance.getBoolean("dark_mode", false) ? 2 : 1;
        String[] appearanceNames = {"تلقائي مع الجهاز", "فاتح", "ليلي"};
        setting("المظهر: " + appearanceNames[appearanceChoice], () ->
            new ChoiceDialogBuilder().setTitle("مظهر التطبيق").setItems(appearanceNames, (d, w) -> {
                if (w == 0) appearance.edit().remove("dark_mode").apply();
                else appearance.edit().putBoolean("dark_mode", w == 2).apply();
                recreate();
            }).show());
        setting("الصورة الشخصية (اختيارية)",this::chooseProfilePhoto);
        if(new java.io.File(getFilesDir(),"profile-avatar.png").exists())setting("حذف الصورة الشخصية",()->{new java.io.File(getFilesDir(),"profile-avatar.png").delete();render();});
        String openingMode=getPreferences(MODE_PRIVATE).getString("opening_mode","first");
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
                section("قفل التطبيق",null,null);
                setting(appLock.enabled()?"قفل التطبيق: مفعل":"قفل التطبيق: متوقف",()->{
                    if(appLock.enabled()){appLock.disable();render();}else appLock.enable(this::render);
                });
                String timeout=appLock.timeoutSeconds()==0?"فورًا":appLock.timeoutSeconds()==60?"دقيقة":"5 دقائق";
                setting("القفل بعد: "+timeout,()->new ChoiceDialogBuilder().setTitle("اقفل التطبيق بعد")
                        .setItems(new String[]{"فورًا","بعد دقيقة","بعد 5 دقائق"},(d,which)->{
                            appLock.setTimeoutSeconds(new int[]{0,60,300}[which]);render();
                        }).show());
                setting("منع لقطات الشاشة: "+(appLock.securePreview()?"مفعّل":"متوقف"),()->{appLock.setSecurePreview(!appLock.securePreview());render();});
                if (!appLock.deviceSecure()) {
                    for(int i=0;i<settingsGroup.getChildCount();i++){
                        View lockRow=settingsGroup.getChildAt(i);if(!(lockRow.getTag() instanceof String))continue;
                        lockRow.setEnabled(false);lockRow.setAlpha(0.5f);lockRow.setOnClickListener(null);
                    }
                    note("فعّل قفل الشاشة في إعدادات الجهاز أولًا لاستخدام قفل التطبيق.");
                }
                section("الخصوصية والملفات",null,null);
                setting("سياسة الخصوصية",this::privacyPolicyDialog);
                setting("استيراد معاملات CSV",this::chooseCsvImport);
                setting("تصدير معاملات "+selectedMonth.toString().substring(0,7)+" CSV",this::exportCsv);
                setting("فحص البيانات وإصلاحها",()->runRepair(true));
                note("يلاقي الإعلانات اللي اتسجلت عمليات، والعمليات والإيصالات المكررة، والتحويلات بين حساباتك اللي اتسجلت صرف ودخل، ويصلحها.");
                section("نسخة احتياطية",null,null);
                long lastBackup=LocalBackup.lastBackupAt(this);
                note(lastBackup==0?"لسه ما عملتش نسخة. ملف واحد مشفر بكلمة سر تختارها، يتحفظ في المكان اللي تختاره ويرجع على أي جهاز."
                        :"آخر نسخة: "+Instant.ofEpochMilli(lastBackup).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_LABEL));
                setting("نسخة احتياطية",this::backupDialog);
                setting("استعادة نسخة",this::chooseRestore);
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

    private static final class SettingEntry {
        final String title, keywords, section, tag;
        SettingEntry(String title, String keywords, String section, String tag) {
            this.title = title; this.keywords = keywords; this.section = section; this.tag = tag;
        }
    }

    /** Every reachable row by title and Arabic/English keywords; visibility follows settingsScreen's own rules. */
    private List<SettingEntry> settingsIndex() {
        List<SettingEntry> all = new ArrayList<>();
        all.add(new SettingEntry("الميزانية اليومية والراتب", "budget daily salary حد اليوم مرتب", "budget", "الميزانية اليومية"));
        all.add(new SettingEntry("أضف ويدجت الشاشة الرئيسية", "widget home size حجم ويدجيت", "budget", "أضف ويدجت"));
        all.add(new SettingEntry("عملة الويدجت والملخص", "widget currency summary عملة", "budget", "عملة الويدجت"));
        all.add(new SettingEntry("تحديث منتصف الليل", "midnight refresh alarm exact منبه", "budget", "تحديث منتصف الليل"));
        all.add(new SettingEntry("أرقام الويدجت", "widget hide amounts privacy إخفاء", "budget", "أرقام الويدجت"));
        all.add(new SettingEntry("استيراد رسائل SMS القديمة", "sms import messages bank رسائل البنك", "capture", "استيراد رسائل SMS"));
        all.add(new SettingEntry("تطبيقات الدفع والإشعارات", "payment apps notifications instapay", "capture", "تطبيقات الدفع"));
        all.add(new SettingEntry("الوصول للإشعارات", "notification access listener إذن", "capture", hasNotificationAccess() ? "الوصول للإشعارات" : "تفعيل الوصول للإشعارات"));
        all.add(new SettingEntry("حساباتك على إنستاباي", "instapay handles ipa تحويل", "capture", "حساباتك على إنستاباي"));
        all.add(new SettingEntry("قواعد التجار والتصنيف", "merchant rules category تصنيف تاجر", "capture", "قواعد التجار"));
        all.add(new SettingEntry("رسائل من بنوكك ما اتسجلتش", "missed bank messages not recorded report رسائل لم تسجل بلاغ", "capture", "رسائل من بنوكك"));
        all.add(new SettingEntry("اسم العرض على الرئيسية", "name profile display اسم", "appearance", "اسم العرض"));
        all.add(new SettingEntry("الوضع الليلي والفاتح", "dark mode theme night light ليلي", "appearance", "المظهر:"));
        all.add(new SettingEntry("الصورة الشخصية", "photo avatar profile صورة", "appearance", "الصورة الشخصية"));
        all.add(new SettingEntry("افتتاحية التطبيق", "opening intro video splash", "appearance", "الافتتاحية"));
        all.add(new SettingEntry("إشعار فوري بكل عملية", "notification captured transaction إشعار", "alerts", "إشعار فوري"));
        all.add(new SettingEntry("تذكير يومي بالمصاريف", "daily reminder تذكير", "alerts", "تذكير يومي"));
        all.add(new SettingEntry("تنبيه ملخص الشهر", "monthly summary notification شهر", "alerts", "تنبيه ملخص الشهر"));
        all.add(new SettingEntry("إعدادات إشعارات الجهاز", "system notification settings", "alerts", "إعدادات إشعارات الجهاز"));
        all.add(new SettingEntry("قفل التطبيق", "app lock biometric fingerprint pin بصمة", "data", "قفل التطبيق"));
        all.add(new SettingEntry("مدة القفل", "lock timeout minute فورًا", "data", "القفل بعد"));
        all.add(new SettingEntry("منع لقطات الشاشة", "screenshot secure preview privacy", "data", "منع لقطات الشاشة"));
        all.add(new SettingEntry("سياسة الخصوصية", "privacy policy", "data", "سياسة الخصوصية"));
        all.add(new SettingEntry("استيراد معاملات CSV", "csv import", "data", "استيراد معاملات CSV"));
        all.add(new SettingEntry("تصدير معاملات CSV", "csv export excel", "data", "تصدير معاملات"));
        all.add(new SettingEntry("نسخة احتياطية", "backup encrypted", "data", "نسخة احتياطية"));
        all.add(new SettingEntry("استعادة نسخة", "restore backup", "data", "استعادة نسخة"));
        all.add(new SettingEntry("فحص البيانات وإصلاحها", "repair check duplicates ads transfers receipts تصحيح تكرار إعلانات تحويلات", "data", "فحص البيانات وإصلاحها"));
        if (!db.assets().isEmpty()) all.add(new SettingEntry("الأصول المسجلة", "assets", "data", "الأصول المسجلة"));
        if (!db.debts().isEmpty()) all.add(new SettingEntry("الديون المسجلة", "debts", "data", "الديون المسجلة"));
        all.add(new SettingEntry("حذف كل البيانات المحلية", "delete clear reset data مسح", "data", "حذف كل البيانات"));
        all.add(new SettingEntry("ما الجديد", "whats new changelog version", "help", "ما الجديد"));
        all.add(new SettingEntry("الإبلاغ عن مشكلة", "report bug problem support email", "help", "الإبلاغ عن مشكلة"));
        all.add(new SettingEntry("اقتراح تحسين", "suggestion feature idea", "help", "اقتراح تحسين"));
        all.add(new SettingEntry("نسخ بيانات التشخيص", "diagnostics copy support", "help", "نسخ بيانات التشخيص"));
        all.add(new SettingEntry("متابعة على GitHub", "github issues developers", "help", "للمطورين"));
        return all;
    }

    private static String settingsSectionTitle(String section) {
        return "budget".equals(section) ? "الميزانية والويدجت" : "capture".equals(section) ? "التقاط العمليات" :
                "appearance".equals(section) ? "المظهر والملف الشخصي" : "alerts".equals(section) ? "الإشعارات والتذكير" :
                "data".equals(section) ? "البيانات والخصوصية" : "المساعدة وعن التطبيق";
    }

    /** Folds hamza/taa-marbuta/alef-maqsura and digits so «نسخه» and «اشعار» still match. */
    private static String searchable(String value) {
        return ExpenseParser.normalizeDigits(value).toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي');
    }

    /** Results replace only their own container, so typing keeps the field, its focus and the keyboard. */
    private void settingsSearch() {
        EditText search = ui.field("ابحث في الإعدادات", page);
        search.setTag("settings_search"); search.setHint("مثال: قفل، نسخة، رسائل البنك");
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        results.setTag("settings_search_results"); results.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(results);
        List<SettingEntry> index = settingsIndex();
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable text) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                results.removeAllViews();
                String query = searchable(text.toString().trim());
                if (query.isEmpty()) return;
                LinearLayout previous = settingsGroup; settingsGroup = null;
                int found = 0;
                for (SettingEntry entry : index) {
                    if (!searchable(entry.title + " " + entry.keywords + " " + settingsSectionTitle(entry.section)).contains(query)) continue;
                    if (settingsGroup == null) settingsGroup = listCard(results); else divider(settingsGroup);
                    LinearLayout row = new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.VERTICAL);
                    row.setPadding(0, dp(10), 0, dp(10)); row.setMinimumHeight(dp(UiTheme.TOUCH));
                    row.addView(label(entry.title, 14, INK, true)); row.addView(label(settingsSectionTitle(entry.section), 12, MUTED, false));
                    row.setContentDescription(entry.title + "، في " + settingsSectionTitle(entry.section));
                    row.setTag("settings_result:" + entry.tag);
                    row.setOnClickListener(v -> { settingsSection = entry.section; settingsFocus = entry.tag; render(); });
                    settingsGroup.addView(row); found++;
                }
                settingsGroup = previous;
                if (found == 0) {
                    TextView none = label("مفيش إعداد بالاسم ده. جرّب كلمة تانية زي «نسخة» أو «إشعار».", 13, MUTED, false);
                    none.setPadding(0, dp(10), 0, dp(10)); results.addView(none);
                }
            }
        });
    }

    /** Scroll the opened section to the searched row and flash it, without running its action. */
    private void focusSettingRow(ScrollView scroll, String prefix) {
        View row = null;
        java.util.ArrayDeque<View> queue = new java.util.ArrayDeque<>(); queue.add(page);
        while (!queue.isEmpty() && row == null) {
            View view = queue.poll();
            if (view.getTag() instanceof String && ((String) view.getTag()).startsWith("setting:" + prefix)) row = view;
            else if (view instanceof android.view.ViewGroup)
                for (int i = 0; i < ((android.view.ViewGroup) view).getChildCount(); i++) queue.add(((android.view.ViewGroup) view).getChildAt(i));
        }
        if (row == null) return;
        int top = 0;
        for (View view = row; view != null && view != page; view = (View) view.getParent()) top += view.getTop();
        scroll.scrollTo(0, Math.max(0, top - dp(80)));
        View target = row;
        android.graphics.drawable.Drawable before = target.getBackground();
        target.setBackground(rounded(ACCENT_PALE, 12));
        target.setFocusable(true); target.requestFocus();
        target.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
        target.postDelayed(() -> target.setBackground(before), 1600);
    }


    private void backFromSettings() {
        if(settingsSection.isEmpty())selectedTab=0;else settingsSection="missed".equals(settingsSection)?"capture":"";
        render();
    }

    private void reportProblem() {
        supportDialog(false);
    }

    private void privacyPolicyDialog() {
        try (java.io.InputStream stream=getAssets().open("privacy-policy.txt")) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int read;
            while((read=stream.read(buffer))!=-1)bytes.write(buffer,0,read);
            textPage("سياسة الخصوصية · Privacy Policy",bytes.toString("UTF-8"));
        } catch(java.io.IOException error) {
            toast("تعذر فتح السياسة؛ تواصل مع "+EmailSupport.ADDRESS);
        }
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
                        else confirmDelete(rule.matchText, () -> { db.deleteMerchantRule(rule.id); render(); });
                    }).show());
        }
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
                        else confirmDelete(asset.name, () -> { db.deleteAsset(asset.id); render(); });
                    }).show());
        }
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
                        else confirmDelete(debt.name, () -> { db.deleteDebt(debt.id); render(); });
                    }).show());
        }
    }

    /** Shared field bindings for small record forms: every value lives in the draft, errors stay on the page. */
    private abstract class DraftFormPage extends UiPages.Page {
        protected TextView error;
        DraftFormPage(String route, String title, Bundle draft, Bundle original) { super(route, title, draft, original); }
        protected String text(String key) { return draft.getString(key, ""); }
        protected EditText textField(String title, String key, LinearLayout body) {
            EditText field = ui.field(title, body); field.setTag(route + "_" + key); field.setText(text(key));
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    draft.putString(key, s.toString()); field.setError(null); if (error != null) error.setVisibility(View.GONE);
                }
                public void afterTextChanged(Editable s) {}
            });
            return field;
        }
        protected EditText amountField(String hint, String key, LinearLayout body) {
            EditText field = ui.amount(text(key), body); field.setHint(hint);
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    draft.putString(key, s.toString()); field.setError(null); if (error != null) error.setVisibility(View.GONE);
                }
                public void afterTextChanged(Editable s) {}
            });
            field.setTag(route + "_" + key); return field;
        }
        protected void choiceField(String title, String key, String[] labels, String[] values, int[] icons, LinearLayout body) {
            ui.caption(title, body);
            ui.choices(labels, icons, java.util.Arrays.asList(values).indexOf(text(key)), i -> draft.putString(key, values[i]), body)
                    .setTag(route + "_" + key);
        }
        protected EditText dateField(String title, String key, LinearLayout body) {
            EditText date = dateInput(title, body, text(key).isEmpty() ? null : LocalDate.parse(text(key)));
            date.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    LocalDate day = dateValue(date); draft.putString(key, day == null ? "" : day.toString());
                }
                public void afterTextChanged(Editable s) {}
            });
            return date;
        }
        protected CheckBox checkField(String title, String key, LinearLayout body) {
            CheckBox box = new CheckBox(MainActivity.this); box.setText(title); box.setTag(route + "_" + key);
            box.setTextColor(INK); box.setTypeface(REGULAR); box.setTextSize(UiTheme.BODY); box.setMinHeight(dp(UiTheme.TOUCH));
            box.setButtonTintList(ColorStateList.valueOf(ACCENT)); box.setChecked(draft.getBoolean(key));
            box.setOnCheckedChangeListener((v, checked) -> draft.putBoolean(key, checked)); body.addView(box);
            return box;
        }
        protected void errorLabel(LinearLayout body) {
            error = ui.label("", UiTheme.BODY, CORAL); error.setTag(route + "_error"); error.setVisibility(View.GONE);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(error);
        }
        protected boolean fail(String message, String key) {
            if (error != null) { error.setText(message); error.setVisibility(View.VISIBLE); }
            View field = key == null ? null : findViewById(android.R.id.content).findViewWithTag(route + "_" + key);
            if (field instanceof EditText) { ((EditText) field).setError(message); field.requestFocus(); }
            else if (error != null) error.post(() -> error.requestRectangleOnScreen(new android.graphics.Rect(0, 0, error.getWidth(), error.getHeight()), false));
            return false;
        }
        @Override boolean restoreAfterRecreation() { return true; }
    }

    private static String minorText(long minor) { return String.format(Locale.ROOT, "%.2f", minor / 100.0); }

    private void merchantRuleDialog(ExpenseDb.MerchantRule existing) {
        Bundle draft = new Bundle(); draft.putLong("id", existing == null ? -1 : existing.id);
        draft.putString("match", existing == null ? "" : existing.matchText.startsWith("=") ? existing.matchText.substring(1) : existing.matchText);
        draft.putString("category", existing != null && java.util.Arrays.asList(CATEGORIES).contains(existing.category) ? existing.category : CATEGORIES[0]);
        draft.putBoolean("ignored", existing != null && existing.ignored);
        draft.putBoolean("remembered", existing != null && existing.matchText.startsWith("="));
        savePageScroll(); formPages.push(new MerchantRulePage(draft, draft));
    }

    private final class MerchantRulePage extends DraftFormPage {
        MerchantRulePage(Bundle draft, Bundle original) { super("merchant_rule", "قاعدة تاجر", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            textField("اسم التاجر كما يظهر في الرسالة", "match", body);
            int[] icons = new int[CATEGORIES.length];
            for (int i = 0; i < CATEGORIES.length; i++) icons[i] = categoryStyle(CATEGORIES[i])[2];
            choiceField("التصنيف", "category", CATEGORIES, CATEGORIES, icons, body);
            checkField("تجاهل عملياته الجديدة", "ignored", body);
            errorLabel(body);
        }
        @Override boolean save() {
            String match = text("match"), category = text("category");
            boolean ignored = draft.getBoolean("ignored"), remembered = draft.getBoolean("remembered");
            if (match.trim().length() < 2) return fail("اكتب اسم تاجر مميز", "match");
            if (remembered && !ignored && !ExpenseDb.canRememberMerchant(match, category)) return fail("اختار تصنيف مشتريات واسم تاجر مميز", null);
            long id = draft.getLong("id", -1);
            if (id >= 0) db.deleteMerchantRule(id);
            if (remembered && !ignored) db.rememberMerchantCategory(match, category);
            else db.putMerchantRule(match, category, ignored);
            return true;
        }
    }

    private void assetDialog(ExpenseDb.Asset existing) {
        Bundle draft = new Bundle(); draft.putLong("id", existing == null ? -1 : existing.id);
        draft.putString("name", existing == null ? "" : existing.name);
        draft.putString("kind", existing == null ? "ذهب" : existing.kind);
        draft.putString("value", existing == null ? "" : minorText(existing.valueMinor));
        draft.putString("currency", existing == null ? CURRENCIES[0] : existing.currency);
        savePageScroll(); formPages.push(new AssetPage(draft, draft));
    }

    private final class AssetPage extends DraftFormPage {
        AssetPage(Bundle draft, Bundle original) { super("asset", draft.getLong("id", -1) < 0 ? "أصل جديد" : "تحديث الأصل", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            amountField("القيمة الحالية يدويًا", "value", body);
            choiceField("العملة", "currency", CURRENCIES, CURRENCIES, null, body);
            textField("اسم الأصل", "name", body);
            String[] kinds = {"ذهب", "فضة", "أسهم", "عقار", "أخرى"};
            choiceField("النوع", "kind", kinds, kinds, null, body);
            errorLabel(body);
        }
        @Override boolean save() {
            if (ExpenseParser.manualAmountMisread(text("value"))) return fail("راجع المبلغ", "value");
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("value")));
            if (minor < 0) return fail("راجع قيمة الأصل", "value");
            if (text("name").trim().isEmpty()) return fail("اكتب اسم الأصل", "name");
            long id = draft.getLong("id", -1);
            db.putAsset(id < 0 ? null : id, text("name"), text("kind"), minor, text("currency"));
            return true;
        }
    }

    private void debtDialog() {
        Bundle draft = new Bundle(); draft.putString("name", ""); draft.putString("direction", "owed_to_me");
        draft.putString("amount", ""); draft.putString("currency", CURRENCIES[0]); draft.putString("due", "");
        savePageScroll(); formPages.push(new DebtPage(draft, draft));
    }

    private final class DebtPage extends DraftFormPage {
        DebtPage(Bundle draft, Bundle original) { super("debt", "دين جديد", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            choiceField("الاتجاه", "direction", new String[]{"لي عند غيري", "عليّ لغيري"}, new String[]{"owed_to_me", "i_owe"}, null, body);
            amountField("المبلغ", "amount", body);
            choiceField("العملة", "currency", CURRENCIES, CURRENCIES, null, body);
            textField("الاسم أو الجهة", "name", body);
            dateField("تاريخ الاستحقاق (اختياري)", "due", body);
            body.addView(components.label("المبالغ هنا لا تضاف تلقائيًا إلى معاملات البنك.", UiTheme.CAPTION, MUTED));
            errorLabel(body);
        }
        @Override boolean save() {
            if (ExpenseParser.manualAmountMisread(text("amount"))) return fail("راجع المبلغ", "amount");
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if (minor <= 0) return fail("راجع المبلغ", "amount");
            if (text("name").trim().isEmpty()) return fail("اكتب الاسم أو الجهة", "name");
            db.addDebt(text("name"), text("direction"), minor, text("currency"), text("due").isEmpty() ? null : text("due"));
            return true;
        }
    }

    private void debtPaymentDialog(ExpenseDb.Debt debt) {
        Bundle draft = new Bundle(); draft.putLong("id", debt.id); draft.putString("amount", "");
        savePageScroll(); formPages.push(new DebtPaymentPage(draft, draft));
    }

    private final class DebtPaymentPage extends DraftFormPage {
        DebtPaymentPage(Bundle draft, Bundle original) { super("debt_payment", "تسجيل سداد", draft, original); }
        private ExpenseDb.Debt debt() {
            for (ExpenseDb.Debt debt : db.debts()) if (debt.id == draft.getLong("id")) return debt;
            return null;
        }
        @Override void build(LinearLayout body, UiComponents components) {
            ExpenseDb.Debt debt = debt();
            if (debt == null) { body.addView(components.label("الدين ده لم يعد موجودًا", UiTheme.BODY, CORAL)); return; }
            body.addView(components.label(debt.name + " · المتبقي " + money(debt.remainingMinor, debt.currency), UiTheme.BODY, MUTED));
            amountField("المبلغ المسدد", "amount", body);
            errorLabel(body);
        }
        @Override boolean save() {
            if (debt() == null) return fail("الدين ده لم يعد موجودًا؛ لم نسجل السداد", null);
            if (ExpenseParser.manualAmountMisread(text("amount"))) return fail("راجع المبلغ", "amount");
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if (minor <= 0) return fail("راجع المبلغ", "amount");
            db.payDebt(draft.getLong("id"), minor);
            return true;
        }
    }

    private void paymentSourceDialog() { sourceBankPage(null); }

    /** A wallet/prepaid app (Telda): its own source, no bank to set and no account to link (task D1). */
    private static boolean appSource(ExpenseDb.PaymentSource source){
        return ExpenseDb.APP_SOURCE_KIND.equals(source.kind)||SourceIdentity.appKey(source.name)!=null;
    }
    private static boolean bankSource(String kind){return "حساب".equals(kind)||"بطاقة".equals(kind)||"بطاقة ائتمان".equals(kind);}
    private static boolean usableBank(String name){return name!=null&&name.matches(".*[\\p{L}].*");}
    private void sourceBankPage(ExpenseDb.PaymentSource source) {
        Bundle draft=new Bundle();draft.putLong("id",source==null?-1:source.id);
        draft.putString("kind",source==null?"":source.kind);
        draft.putString("bank",source==null||!usableBank(source.name)?"":source.name);
        // A numeric old name was the user's only label; a real bank name must not be copied into the display name.
        draft.putString("name",source==null?"":source.displayName!=null&&!source.displayName.isBlank()?source.displayName:usableBank(source.name)?"":source.name);
        draft.putString("digits",source==null||source.last4==null?"":source.last4);
        savePageScroll();formPages.push(new SourceFormPage(draft,draft));
    }
    private final class SourceFormPage extends UiPages.Page {
        private EditText bank,name,digits;private TextView error,kindHint;private LinearLayout bankFields;
        SourceFormPage(Bundle draft,Bundle original){super("source_form",draft.getLong("id",-1)<0?"وسيلة دفع جديدة":"تحديد البنك والوسيلة",draft,original);}
        private EditText field(String title,String key,LinearLayout body){
            EditText field=ui.field(title,body);field.setTag("source_"+key);field.setText(draft.getString(key,""));
            field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}
                public void onTextChanged(CharSequence s,int st,int before,int count){draft.putString(key,s.toString());field.setError(null);if(error!=null)error.setVisibility(View.GONE);}
                public void afterTextChanged(Editable text){} });return field;
        }
        @Override void build(LinearLayout body,UiComponents components){
            String[] kinds={"بطاقة","بطاقة ائتمان","حساب","محفظة","تطبيق دفع","كاش"};
            components.caption("نوع الوسيلة",body);
            if(draft.getLong("id",-1)<0)components.choices(new String[]{"كارت خصم","كارت ائتمان","حساب بنكي","محفظة","تطبيق دفع","كاش"},null,
                    java.util.Arrays.asList(kinds).indexOf(draft.getString("kind")),i->{draft.putString("kind",kinds[i]);updateKind();},body);
            else body.addView(components.label(draft.getString("kind")+" · آخر الأرقام "+draft.getString("digits"),UiTheme.BODY,INK));
            kindHint=components.label("",UiTheme.CAPTION,MUTED);body.addView(kindHint);
            bankFields=new LinearLayout(MainActivity.this);bankFields.setOrientation(LinearLayout.VERTICAL);body.addView(bankFields);
            bank=field("البنك", "bank",bankFields);
            bankFields.addView(components.button("اختيار البنك",false,false,()->chooseValue("البنك",new String[]{"HSBC","بنك مصر","البنك الأهلي المصري","CIB","QNB","ALEXBANK","ADIB","FABMISR","BDC","SAIB","Bank ABC","BANKNXT","KFH Egypt","Credit Agricole"},draft.getString("bank"),value->bank.setText(value))));
            bankFields.addView(components.label("اكتب البنك لو مش في القائمة. اسم الكارت أو أرقام الحساب ليهم خانات منفصلة تحت.",UiTheme.CAPTION,MUTED));
            name=field("اسم يميّزها ليك","name",body);name.setHint("مثال: كارت المشتريات أو حساب المرتب");
            body.addView(components.label("الاسم اختياري للحسابات والكروت، ومطلوب لباقي الوسائل.",UiTheme.CAPTION,MUTED));
            digits=field("آخر 3 أو 4 أرقام (اختياري)","digits",body);digits.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            if(draft.getLong("id",-1)>=0){digits.setKeyListener(null);body.addView(components.label("تصحيح البنك يحافظ على آخر الأرقام والعمليات والرسائل. الربط يحتاج تأكيدًا منفصلًا.",UiTheme.CAPTION,MUTED));}
            else body.addView(components.label("آخر الأرقام فقط، مش رقم الحساب أو الكارت كامل.",UiTheme.CAPTION,MUTED));
            error=components.label("",UiTheme.BODY,CORAL);error.setTag("source_error");error.setVisibility(View.GONE);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);updateKind();
        }
        private void updateKind(){if(bankFields!=null)bankFields.setVisibility(bankSource(draft.getString("kind"))?View.VISIBLE:View.GONE);
            if(kindHint!=null)kindHint.setText("بطاقة".equals(draft.getString("kind"))?"كارت الخصم بيسحب من حساب بنكي، وتقدر تربطه بحسابه بعد الإضافة.":
                    "بطاقة ائتمان".equals(draft.getString("kind"))?"كارت الائتمان له مديونية وسداد منفصل؛ مش بيتربط ككارت خصم بالحساب.":
                    "حساب".equals(draft.getString("kind"))?"اختار البنك هنا؛ رقم الحساب واسمه الواضح في الخانات اللي تحت.":"اختار نوع الوسيلة عشان نوضح بياناتها.");}
        private boolean fail(String message,EditText field){error.setText(message);error.setVisibility(View.VISIBLE);if(field!=null){field.setError(message);field.requestFocus();}else error.post(()->error.requestRectangleOnScreen(new android.graphics.Rect(0,0,error.getWidth(),error.getHeight()),false));return false;}
        @Override String saveLabel(){return draft.getLong("id",-1)<0?"إضافة":"حفظ بيانات الوسيلة";}
        @Override boolean save(){
            String kind=draft.getString("kind",""),bankName=draft.getString("bank","").trim(),displayName=draft.getString("name","").trim();
            String last=ExpenseParser.normalizeDigits(draft.getString("digits","").trim());
            if(kind.isEmpty())return fail("اختار نوع الوسيلة",null);
            if(bankSource(kind)&&!usableBank(bankName))return fail("اختار اسم البنك؛ الأرقام مكانها خانة آخر الأرقام",bank);
            if(!bankSource(kind)&&displayName.isEmpty())return fail("اكتب اسم الوسيلة",name);
            if(draft.getLong("id",-1)<0&&!last.isEmpty()&&!last.matches("[0-9]{3,4}"))return fail("اكتب آخر 3 أو 4 أرقام فقط",digits);
            try {
                long id=draft.getLong("id",-1);
                if(id<0)new SourceMutation(db).createReviewed(bankSource(kind)?bankName:displayName,kind,last.isEmpty()?null:last,bankSource(kind)?displayName:"");
                else if(!new SourceMutation(db).correctBank(id,bankName,displayName))return fail("الوسيلة دي لم تعد موجودة؛ لم نحفظ أي تعديل",null);
                return true;
            }catch(IllegalArgumentException exception){return fail(exception.getMessage(),null);}
            catch(RuntimeException exception){return fail("تعذر حفظ الوسيلة؛ لم نحفظ بيانات جزئية. راجعها وحاول مرة أخرى.",null);}
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private void budgetDialog() {
        budgetPlanDialog(null);
    }

    private void budgetPlanDialog(ExpenseDb.BudgetPlan existing) {
        Bundle draft = new Bundle(); draft.putLong("id", existing == null ? -1 : existing.id);
        draft.putString("name", existing == null ? "" : existing.name);
        draft.putString("currency", existing == null ? CURRENCIES[0] : existing.currency);
        draft.putString("amount", existing == null ? "" : minorText(existing.amountMinor));
        draft.putString("cycle", existing == null ? "monthly" : existing.cycle);
        draft.putString("anchor", (existing == null || existing.anchor == null ? LocalDate.now() : existing.anchor).toString());
        draft.putString("days", existing == null ? "30" : Integer.toString(existing.durationDays));
        draft.putStringArrayList("categories", existing == null ? new ArrayList<>() : new ArrayList<>(existing.categories));
        savePageScroll(); formPages.push(new BudgetPlanPage(draft, draft));
    }

    private final class BudgetPlanPage extends DraftFormPage {
        private EditText days; private LinearLayout categories;
        BudgetPlanPage(Bundle draft, Bundle original) { super("budget", draft.getLong("id", -1) < 0 ? "ميزانية جديدة" : "تعديل الميزانية", draft, original); }
        private ArrayList<String> chosen() {
            ArrayList<String> list = draft.getStringArrayList("categories"); return list == null ? new ArrayList<>() : list;
        }
        @Override void build(LinearLayout body, UiComponents components) {
            amountField("حد الإنفاق", "amount", body);
            choiceField("العملة", "currency", CURRENCIES, CURRENCIES, null, body);
            textField("اسم الميزانية", "name", body);
            components.caption("دورة التجديد", body);
            String[] cycles = {"monthly", "weekly", "once", "custom"};
            components.choices(new String[]{"شهري", "أسبوعي", "مرة واحدة", "مخصص بالأيام"}, null, java.util.Arrays.asList(cycles).indexOf(text("cycle")),
                    i -> { draft.putString("cycle", cycles[i]); updateCycle(); }, body).setTag("budget_cycle");
            dateField("تاريخ البداية", "anchor", body);
            days = textField("عدد الأيام", "days", body); days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            categories = new LinearLayout(MainActivity.this); categories.setOrientation(LinearLayout.VERTICAL); body.addView(categories);
            drawCategories(); errorLabel(body); updateCycle();
        }
        private void updateCycle() {
            if (days == null) return;
            int visible = "once".equals(text("cycle")) || "custom".equals(text("cycle")) ? View.VISIBLE : View.GONE;
            days.setVisibility(visible);
            android.view.ViewGroup parent = (android.view.ViewGroup) days.getParent();
            int at = parent.indexOfChild(days); if (at > 0) parent.getChildAt(at - 1).setVisibility(visible);
        }
        private void drawCategories() {
            categories.removeAllViews();
            List<String> chosen = chosen();
            ui.choiceCard("التصنيفات للتتبع", chosen.isEmpty() ? "اختار تصنيف واحد على الأقل" : String.join("، ", chosen), null, () -> {
                boolean[] checked = new boolean[CATEGORIES.length];
                for (int i = 0; i < CATEGORIES.length; i++) checked[i] = chosen().contains(CATEGORIES[i]);
                new ChoiceDialogBuilder().setTitle("التصنيفات للتتبع")
                        .setMultiChoiceItems(CATEGORIES, checked, (dialog, which, enabled) -> checked[which] = enabled)
                        .setPositiveButton("تم", (dialog, which) -> {
                            ArrayList<String> picked = new ArrayList<>();
                            for (int i = 0; i < CATEGORIES.length; i++) if (checked[i]) picked.add(CATEGORIES[i]);
                            draft.putStringArrayList("categories", picked); if (error != null) error.setVisibility(View.GONE); drawCategories();
                        }).setNegativeButton("إلغاء", null).show();
            }, categories).setTag("budget_categories");
        }
        @Override boolean save() {
            if (ExpenseParser.manualAmountMisread(text("amount"))) return fail("راجع المبلغ", "amount");
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if (minor <= 0) return fail("اكتب حد الإنفاق", "amount");
            if (text("name").trim().isEmpty()) return fail("اكتب اسم الميزانية", "name");
            int span;
            try { span = Integer.parseInt(ExpenseParser.normalizeDigits(text("days").trim())); } catch (NumberFormatException invalid) { span = 0; }
            if (span <= 0) return fail("اكتب عدد أيام صحيح", "days");
            if (chosen().isEmpty()) return fail("اختار تصنيف واحد على الأقل للتتبع", null);
            if (text("anchor").isEmpty()) return fail("اختار تاريخ البداية", null);
            long id = draft.getLong("id", -1);
            try {
                db.putBudgetPlan(id < 0 ? null : id, text("name"), minor, text("currency"), text("cycle"), LocalDate.parse(text("anchor")), span, chosen());
            } catch (RuntimeException failed) { return fail("لم نحفظ الميزانية؛ راجع البيانات وحاول مرة أخرى", null); }
            return true;
        }
    }

    /** Password pages: secrets live only in their fields, never in the draft, saved state or a restorable stack. */
    private abstract class SecretPage extends DraftFormPage {
        protected final List<EditText> secrets = new ArrayList<>();
        SecretPage(String route, String title) { super(route, title, new Bundle(), new Bundle()); }
        protected EditText secret(String title, String key, LinearLayout body) {
            EditText field = passwordInput(title, body); field.setTag(route + "_" + key); secrets.add(field);
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { field.setError(null); if (error != null) error.setVisibility(View.GONE); }
                public void afterTextChanged(Editable s) {}
            });
            return field;
        }
        @Override boolean dirty() { for (EditText field : secrets) if (field.length() > 0) return true; return false; }
        @Override boolean restoreAfterRecreation() { return false; }
    }

    private void backupDialog() { savePageScroll(); formPages.push(new BackupPage()); }

    private final class BackupPage extends SecretPage {
        private EditText first, second;
        BackupPage() { super("backup", "نسخة احتياطية"); }
        @Override void build(LinearLayout body, UiComponents components) {
            body.addView(components.label("ملف واحد مشفر بكلمة سر إنت اللي تختارها (8 أحرف على الأقل). فيه عملياتك ونصوص رسائل البنك المحفوظة وإعداداتك. لو نسيت كلمة السر مفيش طريقة نفتح النسخة.", UiTheme.BODY, MUTED));
            first = secret("كلمة السر", "password", body); second = secret("تأكيد كلمة السر", "confirm", body);
            errorLabel(body);
        }
        @Override String saveLabel() { return "اختار مكان الحفظ"; }
        @Override boolean save() {
            char[] password = chars(first), confirm = chars(second);
            boolean same = java.util.Arrays.equals(password, confirm);
            BackupVault.wipe(confirm);
            if (password.length < BackupVault.MIN_PASSWORD) { BackupVault.wipe(password); return fail("كلمة السر لازم تكون 8 أحرف على الأقل", "password"); }
            if (!same) { BackupVault.wipe(password); return fail("كلمتا السر مش متطابقتين", "confirm"); }
            BackupVault.wipe(pendingBackupPassword); pendingBackupPassword = password;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, BackupVault.fileName(LocalDate.now()));
            startActivityForResult(intent, BACKUP_CREATE_REQUEST);
            return true;
        }
    }

    private void restorePasswordDialog(Uri uri) { savePageScroll(); formPages.push(new RestorePage(uri)); }

    private final class RestorePage extends SecretPage {
        private final Uri uri; private EditText password;
        RestorePage(Uri uri) { super("restore", "استعادة نسخة"); this.uri = uri; }
        @Override void build(LinearLayout body, UiComponents components) {
            body.addView(components.label("اكتب كلمة سر النسخة. هنعرض لك ملخصها قبل ما نستبدل أي حاجة.", UiTheme.BODY, MUTED));
            password = secret("كلمة السر", "password", body); errorLabel(body);
        }
        @Override String saveLabel() { return "افتح النسخة"; }
        @Override boolean save() {
            if (password.length() == 0) return fail("اكتب كلمة سر النسخة", "password");
            prepareRestore(uri, chars(password)); return true;
        }
    }

    private void supportDialog(boolean suggestion) {
        Bundle draft = new Bundle(); draft.putBoolean("suggestion", suggestion); draft.putString("description", "");
        draft.putBoolean("crash", false);
        savePageScroll(); formPages.push(new SupportPage(draft, draft));
    }

    private final class SupportPage extends DraftFormPage {
        private final String lastCrash;
        SupportPage(Bundle draft, Bundle original) {
            super("support", draft.getBoolean("suggestion") ? "اقتراح تحسين" : "الإبلاغ عن مشكلة", draft, original);
            lastCrash = draft.getBoolean("suggestion") ? null : CrashLog.latest(MainActivity.this);
        }
        private String reviewed() {
            return text("description").trim() + (draft.getBoolean("crash") && lastCrash != null ? "\n\nآخر عطل (محلي):\n" + lastCrash : "");
        }
        @Override void build(LinearLayout body, UiComponents components) {
            boolean suggestion = draft.getBoolean("suggestion");
            body.addView(components.label("إلى: " + EmailSupport.ADDRESS, UiTheme.CAPTION, MUTED));
            EditText description = textField(suggestion ? "وصف الاقتراح والفائدة منه" : "إيه اللي حصل؟ وإيه اللي كنت متوقعه؟", "description", body);
            description.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            description.setSingleLine(false); description.setMinLines(3); description.setGravity(Gravity.TOP | Gravity.START);
            description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
            body.addView(components.label("متكتبش أرقام حسابات أو كروت، ولا تلصق رسائل البنك الأصلية.", UiTheme.CAPTION, CORAL));
            components.caption("بيانات النسخة والجهاز والنظام واللغة", body);
            TextView metadata = components.label(GitHubSupport.details(MainActivity.this), UiTheme.CAPTION, INK);
            metadata.setTextDirection(View.TEXT_DIRECTION_LTR); metadata.setTextIsSelectable(true); body.addView(metadata);
            if (lastCrash != null) {
                CheckBox attach = checkField("إرفاق آخر عطل كنص في المسودة", "crash", body);
                TextView preview = components.label(lastCrash, UiTheme.CAPTION, INK); preview.setTextDirection(View.TEXT_DIRECTION_LTR);
                preview.setTextIsSelectable(true); preview.setVisibility(attach.isChecked() ? View.VISIBLE : View.GONE); body.addView(preview);
                attach.setOnCheckedChangeListener((button, checked) -> { draft.putBoolean("crash", checked); preview.setVisibility(checked ? View.VISIBLE : View.GONE); });
            }
            body.addView(components.label("هيفتح مسودة في تطبيق الإيميل؛ راجعها واضغط إرسال بنفسك. محتاج إيميل متظبط على الموبايل.", UiTheme.CAPTION, MUTED));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12);
            body.addView(components.button("نسخ الطلب", false, false, () -> copySupportRequest(suggestion, reviewed(), "اتنسخ عنوان الدعم والطلب")), lp);
            errorLabel(body);
        }
        @Override String saveLabel() { return "فتح مسودة إيميل"; }
        /** Opening the mail app is not «saved»: the page stays so the user can come back and copy or retry. */
        @Override boolean save() {
            boolean suggestion = draft.getBoolean("suggestion");
            if (text("description").trim().isEmpty()) return fail("اكتب وصف مختصر الأول", "description");
            try { startActivity(EmailSupport.draft(MainActivity.this, suggestion, reviewed())); }
            catch (android.content.ActivityNotFoundException error) { copySupportRequest(suggestion, reviewed(), "مفيش تطبيق إيميل متاح؛ اتنسخ عنوان الدعم والطلب"); }
            return false;
        }
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
        WrapRow row = homeStrip("whats_new_card");
        TextView headline = label("جديد في النسخة " + isolate(whatsNew.version) + " ›", 12, INK, true);
        headline.setMinHeight(dp(UiTheme.TOUCH)); headline.setGravity(Gravity.CENTER_VERTICAL);
        headline.setOnClickListener(v -> whatsNewDialog()); row.addView(headline);
        TextView close = label("✕", 16, MUTED, false); close.setGravity(Gravity.CENTER);
        close.setContentDescription("إخفاء الجديد"); close.setMinWidth(dp(UiTheme.TOUCH)); close.setMinHeight(dp(UiTheme.TOUCH));
        close.setOnClickListener(v -> {
            getSharedPreferences(APP_STATE, MODE_PRIVATE).edit().putInt(LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply();
            whatsNew = null; render();
        });
        row.addView(close);
    }

    private WrapRow homeStrip(String tag) {
        WrapRow row = new WrapRow(this); row.setTag(tag); row.setMinimumHeight(dp(42));
        row.setPadding(dp(6), dp(2), dp(6), dp(2)); row.setBackground(outlined(ACCENT_PALE, BORDER, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); page.addView(row, params);
        return row;
    }

    /** Play owns the update; this is only its compact, wrapping status surface. */
    private void updateCard() {
        UpdatePrompt.Card state = updates == null ? UpdatePrompt.Card.NONE : updates.card();
        if (state == UpdatePrompt.Card.NONE) return;
        updateStrip(state, updates.availableVersion(), updates.downloadedBytes(), updates.totalBytes());
    }

    private void updateStrip(UpdatePrompt.Card state, int version, long downloaded, long total) {
        WrapRow row = homeStrip("in_app_update_card");
        LinearLayout leading = new LinearLayout(this); leading.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(state == UpdatePrompt.Card.RESTART ? "✓" : "↓", 18, LIME, true);
        icon.setGravity(Gravity.CENTER); icon.setBackground(rounded(BRAND, 99));
        icon.setMinHeight(dp(28)); leading.addView(icon, new LinearLayout.LayoutParams(dp(28), -2));
        String title = state == UpdatePrompt.Card.OFFER ? "فيه نسخة جديدة" : state == UpdatePrompt.Card.DOWNLOADING ? "التحديث بيتحمّل…" : "التحديث جاهز";
        TextView copy = label(title + (state == UpdatePrompt.Card.OFFER && version > 0 ? " " + isolate("إصدار " + version) : ""), 12, INK, true);
        if (state == UpdatePrompt.Card.OFFER && version > 0) {
            android.text.SpannableString text = new android.text.SpannableString(copy.getText());
            text.setSpan(new android.text.style.RelativeSizeSpan(0.8f), title.length(), text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); copy.setText(text);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.setMarginStart(dp(6)); leading.addView(copy, cp); row.addView(leading);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        if (state == UpdatePrompt.Card.DOWNLOADING) {
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(total <= 0); progress.setMax(10000);
            progress.setProgress((int)Math.round(StatementRows.share(downloaded, total) * 10000));
            progress.setProgressTintList(ColorStateList.valueOf(EMERALD));
            actions.addView(progress, new LinearLayout.LayoutParams(dp(80), dp(5)));
            if (total > 0) actions.addView(label(StatementRows.percent(downloaded, total), 10, MUTED, false));
        } else {
            if (state == UpdatePrompt.Card.OFFER) actions.addView(stripAction("بعدين", false, () -> { if (updates != null) updates.later(); }));
            actions.addView(stripAction(state == UpdatePrompt.Card.OFFER ? "تحديث" : "إعادة التشغيل", true,
                    () -> { if (updates != null) { if (state == UpdatePrompt.Card.OFFER) updates.start(); else updates.complete(); } }));
        }
        row.addView(actions);
    }

    private TextView stripAction(String title, boolean primary, Runnable action) {
        TextView button = label(title, 12, primary ? LIME : ACCENT, true);
        button.setGravity(Gravity.CENTER); button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setMinHeight(dp(UiTheme.TOUCH)); button.setMinWidth(dp(UiTheme.TOUCH));
        if (primary) button.setBackground(new android.graphics.drawable.InsetDrawable(rounded(BRAND, 99), 0, dp(4), 0, dp(4)));
        button.setOnClickListener(v -> action.run()); return button;
    }

    private ImageView homeIconButton(int resource, String description, Runnable action) {
        ImageView button = iconButton(resource, description, action);
        button.setBackground(new android.graphics.drawable.InsetDrawable(button.getBackground(), dp(4)));
        button.setPadding(dp(14), dp(14), dp(14), dp(14)); return button;
    }

    private void whatsNewDialog() {
        String text = WhatsNew.readable(readAsset("whats-new.txt"));
        if (text.isEmpty()) { toast("لا توجد ملاحظات للنسخة دي"); return; }
        textPage("ما الجديد", text);
    }

    /** Long reading surfaces are pages, not sheets; nothing to save, so back and «إغلاق» just return. */
    private void textPage(String title, String text) {
        savePageScroll();
        formPages.push(new UiPages.Page("text", title, new Bundle(), new Bundle()) {
            @Override void build(LinearLayout body, UiComponents components) {
                TextView copy = components.label(text, UiTheme.BODY, INK); copy.setTextIsSelectable(true);
                copy.setLineSpacing(0, 1.25f);
                body.addView(copy);
            }
            @Override boolean save() { return true; }
            @Override String saveLabel() { return "إغلاق"; }
            @Override boolean readOnly() { return true; }
        });
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
                        transactionPeriod = null; transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionStatementList=false; transactionStatementIds=null;
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
        openTransactionPage(item, null);
    }

    private void refreshMerchantCatalog() {
        SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        // Bumped when the catalog gains merchants, so untouched automatic purchases get the new categories.
        if(prefs.getInt("merchant_catalog_version",0)>=3) return;
        Context app=getApplicationContext();
        CaptureQueue.submit(()->{
            try(ExpenseDb catalogDb=new ExpenseDb(app)) {
                int count=catalogDb.refreshMerchantCategories();
                prefs.edit().putInt("merchant_catalog_version",3).apply();
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

    private void manualDialog(String spoken) { openTransactionPage(null, spoken); }

    private void openTransactionPage(ExpenseDb.Transaction item, String spoken) {
        Bundle draft = new Bundle();
        draft.putLong("id", item == null ? -1 : item.id);
        draft.putLong("when", item == null ? 0 : item.when);
        draft.putString("precision", item == null || item.timePrecision == null ? "" : item.timePrecision);
        draft.putString("kind", item == null ? "expense" : item.kind);
        draft.putString("merchant", item == null ? "" : item.merchant);
        draft.putString("amount", item == null ? "" : String.format(Locale.ROOT,"%.2f",item.amountMinor/100.0));
        draft.putString("currency", item == null ? CURRENCIES[0] : item.currency);
        // Income/transfer categories are derived on save; switching to expense must not inherit «دخل».
        draft.putString("category", item == null || !"expense".equals(item.kind) ? CATEGORIES[0] : item.category);
        draft.putLong("from", item == null || item.paymentSourceId == null ? -1 : item.paymentSourceId);
        draft.putLong("to", item == null || item.destinationSourceId == null ? -1 : item.destinationSourceId);
        draft.putString("day", (item == null ? LocalDate.now() : Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate()).toString());
        draft.putString("note", item == null || item.note == null ? "" : item.note);
        draft.putBoolean("remember",false);
        draft.putString("time","");
        // A spoken draft differs from its blank original, so back asks before discarding it.
        Bundle original = new Bundle(draft);
        if (spoken != null) {
            String normalized = ExpenseParser.normalizeDigits(spoken);
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("[0-9]+(?:[.,][0-9]{1,2})?").matcher(normalized);
            if (number.find()) {
                draft.putString("amount",number.group());
                String description = (normalized.substring(0,number.start())+" "+normalized.substring(number.end()))
                        .replaceAll("(?i)(جنيه|جنيهات|EGP|صرفت|دفعت|اشتريت|من|في)"," ").trim();
                draft.putString("merchant",description.isEmpty()?"عملية بالصوت":description);
            } else draft.putString("merchant",spoken);
            draft.putString("note","مراجعة نص الصوت: "+spoken);
        }
        savePageScroll(); formPages.push(new TransactionFormPage(draft,original));
    }

    /** Add/edit share one draft and one presentation; existing DB mutation rules remain authoritative. */
    private final class TransactionFormPage extends UiPages.Page {
        private EditText amount, merchant;
        private LinearLayout categories, destination;
        private CheckBox remember;
        private TextView error;
        TransactionFormPage(Bundle draft, Bundle original) {
            super("transaction",draft.getLong("id",-1)<0?"عملية جديدة":"تعديل المعاملة",draft,original);
        }
        private String text(String key) { return draft.getString(key,""); }
        private Long source(String key) { long id=draft.getLong(key,-1); return id<0?null:id; }
        private void bind(EditText field,String key) {
            field.setTag("transaction_"+key); field.setText(text(key));
            field.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){draft.putString(key,s.toString());field.setError(null);clearError();if("merchant".equals(key))updateKind();}
                public void afterTextChanged(Editable s){}
            });
        }
        @Override void build(LinearLayout body,UiComponents components) {
            components.caption("نوع العملية",body);
            components.choices(new String[]{"مصروف","دخل","تحويل"},null,
                    "income".equals(text("kind"))?1:"transfer".equals(text("kind"))?2:0,
                    i->{draft.putString("kind",new String[]{"expense","income","transfer"}[i]);updateKind();},body).setTag("transaction_kind");
            amount=components.amount(text("amount"),body);bind(amount,"amount");
            Button currency=components.button(text("currency"),false,false,()->{});currency.setTag("transaction_currency");
            currency.setContentDescription("العملة، "+text("currency"));
            currency.setOnClickListener(v->chooseValue("العملة",CURRENCIES,text("currency"),value->{draft.putString("currency",value);
                currency.setText(value);currency.setContentDescription("العملة، "+value);}));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.gravity=Gravity.CENTER_HORIZONTAL;cp.topMargin=dp(8);body.addView(currency,cp);
            categories=new LinearLayout(MainActivity.this);categories.setOrientation(LinearLayout.VERTICAL);body.addView(categories);
            drawCategories();
            merchant=components.field("الجهة أو وصف العملية",body);bind(merchant,"merchant");
            LinearLayout accounts=new LinearLayout(MainActivity.this);accounts.setOrientation(LinearLayout.VERTICAL);body.addView(accounts);
            drawAccount(accounts,"from","الحساب أو وسيلة الدفع");
            destination=new LinearLayout(MainActivity.this);destination.setOrientation(LinearLayout.VERTICAL);destination.setTag("transaction_destination");
            body.addView(destination);drawAccount(destination,"to","التحويل إلى");
            EditText date=dateInput("التاريخ",body,LocalDate.parse(text("day")));
            // Keep LocalDate in the native date field tag; the page model stores only its ISO value.
            date.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){LocalDate day=dateValue(date);if(day!=null)draft.putString("day",day.toString());}
                public void afterTextChanged(Editable s){}
            });
            if("date_only".equals(text("precision")))body.addView(components.label("تاريخ فقط · المصدر لا يحدد وقت العملية",UiTheme.CAPTION,MUTED));
            else {
                Button time=components.button(timeLabel(),false,false,()->{});time.setTag("transaction_time");
                time.setOnClickListener(v->{LocalTime shown=chosenTime();
                    android.app.TimePickerDialog picker=new android.app.TimePickerDialog(MainActivity.this,(view,hour,minute)->{
                        draft.putString("time",LocalTime.of(hour,minute).toString());time.setText(timeLabel());clearError();
                    },shown.getHour(),shown.getMinute(),false);
                    UiSheets.prepare(MainActivity.this,picker,uiTheme,REGULAR).show();});
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);body.addView(time,tp);
            }
            EditText note=components.field("وصف أو ملاحظة",body);bind(note,"note");note.setSingleLine(false);note.setMinLines(2);
            remember=new CheckBox(MainActivity.this);remember.setText("افتكر التصنيف للتاجر ده في العمليات الجديدة");
            remember.setTextColor(INK);remember.setTypeface(REGULAR);remember.setTextSize(UiTheme.BODY);remember.setMinHeight(dp(UiTheme.TOUCH));
            remember.setButtonTintList(ColorStateList.valueOf(ACCENT));remember.setChecked(draft.getBoolean("remember"));
            remember.setOnCheckedChangeListener((v,checked)->draft.putBoolean("remember",checked));body.addView(remember);
            error=components.label("",UiTheme.BODY,CORAL);error.setTag("transaction_error");error.setVisibility(View.GONE);
            error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(error);
            updateKind();
        }
        private void updateKind() {
            clearError();
            if(categories!=null)categories.setVisibility("expense".equals(text("kind"))?View.VISIBLE:View.GONE);
            if(destination!=null)destination.setVisibility("transfer".equals(text("kind"))?View.VISIBLE:View.GONE);
            if(remember!=null)remember.setVisibility(draft.getLong("id",-1)>=0&&"expense".equals(text("kind"))&&(draft.getBoolean("remember")||ExpenseDb.canRememberMerchant(text("merchant"),"أخرى"))?View.VISIBLE:View.GONE);
        }
        /** The row being edited is a transfer to a person whose purpose is open: «تحويل بين حساباتي» is offered first. */
        private boolean openTransfer(){long id=draft.getLong("id",-1);return id>=0&&openTransferRow(id);}
        private void drawCategories() {
            categories.removeAllViews();ui.caption("التصنيف",categories);
            boolean open=openTransfer();
            String[] names=open?withOwnTransfer(transactionCategories(OWN_TRANSFER_CHOICE.equals(text("category"))?"":text("category"))):transactionCategories(text("category"));
            if(open)ui.caption(OPEN_TRANSFER_HINT,categories);
            int[] icons=new int[names.length];
            for(int i=0;i<names.length;i++)icons[i]=categoryStyle(names[i])[2];
            ui.choices(names,icons,java.util.Arrays.asList(names).indexOf(text("category")),i->draft.putString("category",names[i]),categories);
            categories.addView(ui.button("كل التصنيفات",false,false,()->chooseValue("التصنيف",names,text("category"),value->{draft.putString("category",value);drawCategories();})));
        }
        private void drawAccount(LinearLayout container,String key,String title) {
            container.removeAllViews();List<ExpenseDb.PaymentSource> sources=paymentOptions(source("from"),source("to"));
            ExpenseDb.PaymentSource selected=null;for(ExpenseDb.PaymentSource candidate:sources)if(java.util.Objects.equals(candidate.id,source(key)))selected=candidate;
            String value=selected==null?"بدون وسيلة محددة":db.sourceLabel(selected.id)+(selected.archived?" · مؤرشف":selected.reviewed?"":" · يحتاج مراجعة");
            ui.choiceCard(title,value,selected==null?null:sourceBadge(selected,40),()->{
                String[] names=sourceNames(sources);int at=0;for(int i=0;i<sources.size();i++)if(java.util.Objects.equals(sources.get(i).id,source(key)))at=i+1;
                new ChoiceDialogBuilder().setTitle(title).setSingleChoiceItems(names,at,(d,i)->{
                    draft.putLong(key,i<=0?-1:sources.get(i-1).id);d.dismiss();clearError();drawAccount(container,key,title);
                }).setNegativeButton("إلغاء",null).show();
            },container).setTag("transaction_"+key);
        }
        private boolean fail(String message,EditText field) {
            error.setText(message);error.setVisibility(View.VISIBLE);
            if(field!=null){field.setError(message);field.requestFocus();}else error.post(()->error.requestRectangleOnScreen(new android.graphics.Rect(0,0,error.getWidth(),error.getHeight()),false));
            return false;
        }
        private void clearError(){if(error!=null)error.setVisibility(View.GONE);}
        /** Empty means «unchanged»: now for a new entry, the stored time for an edit. */
        private LocalTime chosenTime(){
            if(!text("time").isEmpty())return LocalTime.parse(text("time"));
            return draft.getLong("id",-1)<0?LocalTime.now():Instant.ofEpochMilli(draft.getLong("when")).atZone(ZoneId.systemDefault()).toLocalTime();
        }
        private String timeLabel(){
            if(text("time").isEmpty()&&draft.getLong("id",-1)<0)return "الوقت: وقت الحفظ";
            return "الوقت: "+chosenTime().format(DateTimeFormatter.ofPattern("h:mm a",new Locale("ar")));
        }
        @Override boolean save() {
            if(ExpenseParser.manualAmountMisread(text("amount")))return fail("راجع المبلغ",amount);
            long minor=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if(minor<=0)return fail("اكتب المبلغ بشكل صحيح",amount);
            if(text("merchant").trim().isEmpty())return fail("اكتب الجهة أو وصف العملية",merchant);
            // «تحويل بين حساباتي»: nothing is written here; the reconcile dialog opens after the page closes.
            if(OWN_TRANSFER_CHOICE.equals(text("category"))&&openTransfer())return true;
            String type=text("kind");Long from=source("from"),to=source("to");
            if("transfer".equals(type)&&(from==null||to==null||from.equals(to)))return fail("اختار حساب المصدر والوجهة للتحويل، ولازم يكونوا مختلفين",null);
            boolean learn=draft.getBoolean("remember")&&"expense".equals(type);
            if(learn&&!ExpenseDb.canRememberMerchant(text("merchant"),text("category")))return fail("حفظ التصنيف متاح للمشتريات، مش التحويلات أو الأقساط",null);
            long id=draft.getLong("id",-1);
            try {
                if(id>=0&&new CardSettlements(db).related(id))return fail("فك توزيع سداد البطاقة قبل تعديل العملية أو رسومها",null);
                LocalDate day=LocalDate.parse(text("day"));
                String category="income".equals(type)?"دخل":"transfer".equals(type)?"تحويلات":text("category");
                if(id<0) {
                    boolean added=db.addManualEntry(day.atTime(chosenTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),minor,text("currency"),
                            text("merchant").trim(),category,from,type,"transfer".equals(type)?to:null,text("note"));
                    if(!added)return fail("لم تُحفظ العملية؛ راجع البيانات وقواعد التجار",null);
                } else {
                    MutationResult result=new TransactionMutation(db).update(id,minor,text("currency"),text("merchant"),category,from,type,
                            "transfer".equals(type)?to:null,text("note"),day.atTime(chosenTime())
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),learn);
                    if(!result.changed())return fail(result.outcome==MutationResult.Outcome.NOT_FOUND?"العملية دي لم تعد موجودة؛ لم نحفظ تعديل أو قاعدة تاجر.":"لم يتم تطبيق التعديل؛ راجع العملية وحاول مرة أخرى.",null);
                    if(selectedTab==10)selectedTransaction=db.transactionById(id);
                }
            } catch(RuntimeException exception){return fail("تعذر الحفظ؛ راجع التاريخ والبيانات",null);}
            return true;
        }
        @Override void onSaved() {
            if(OWN_TRANSFER_CHOICE.equals(text("category"))&&openTransfer()){reconcileTransfers(java.util.Collections.singletonList(draft.getLong("id",-1)));return;}
            if(draft.getLong("id",-1)>=0){toast("اتحفظ تعديل العملية");if(draft.getBoolean("remember")&&"expense".equals(text("kind")))offerPastMerchantCategory(text("merchant"),text("category"));}
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private String[] transactionCategories(String selected) {
        if(selected==null||selected.isEmpty()||java.util.Arrays.asList(CATEGORIES).contains(selected))return CATEGORIES;
        String[] result=java.util.Arrays.copyOf(CATEGORIES,CATEGORIES.length+1);result[CATEGORIES.length]=selected;return result;
    }

    private void chooseValue(String title,String[] values,String selected,java.util.function.Consumer<String> chosen) {
        new ChoiceDialogBuilder().setTitle(title).setSingleChoiceItems(values,java.util.Arrays.asList(values).indexOf(selected),(d,i)->{
            d.dismiss();chosen.accept(values[i]);
        }).setNegativeButton("إلغاء",null).show();
    }

    /**
     * Ids of expenses that look like a monthly commitment: another payment to the same merchant in the
     * same currency about a month earlier. Grouped by merchant so each list is scanned once, not per row.
     */
    static Set<Long> recurringCandidateIds(List<ExpenseDb.Transaction> items, List<ExpenseDb.Recurring> confirmed) {
        return recurringCandidateIds(items, confirmed, java.util.Collections.emptySet());
    }

    /** dismissed: recurring keys the user said are not commitments («مش التزام · تجاهل»); never suggested again. */
    static Set<Long> recurringCandidateIds(List<ExpenseDb.Transaction> items, List<ExpenseDb.Recurring> confirmed, Set<String> dismissed) {
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
                if (dismissed.contains(ExpenseDb.recurringKey(recent.merchant, recent.currency))) continue;
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
                    else {PaymentNotificationListener.refreshSelectedSources(this);render();}
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

    private void displayNamePage() {
        Bundle draft = new Bundle(); draft.putString("name", getPreferences(MODE_PRIVATE).getString("profile_name", ""));
        savePageScroll();
        formPages.push(restoreFormPage("display_name", draft, draft));
    }

    private UiPages.Page restoreFormPage(String route, Bundle draft, Bundle original) {
        if ("source_form".equals(route)) return new SourceFormPage(draft,original);
        if ("source_link".equals(route)) return new SourceLinkPage(draft,original);
        if ("source_profile".equals(route)) return new SourceProfilePage(draft,original);
        if ("manual_balance".equals(route)) return new ManualBalancePage(draft,original);
        if ("merchant_rule".equals(route)) return new MerchantRulePage(draft,original);
        if ("asset".equals(route)) return new AssetPage(draft,original);
        if ("debt".equals(route)) return new DebtPage(draft,original);
        if ("debt_payment".equals(route)) return new DebtPaymentPage(draft,original);
        if ("budget".equals(route)) return new BudgetPlanPage(draft,original);
        if ("support".equals(route)) return new SupportPage(draft,original);
        if ("daily_budget".equals(route)) return new DailyBudgetPage(draft,original);
        if ("commitment_from_transaction".equals(route)) return new CommitmentFromTransactionPage(draft,original);
        if ("split_bill".equals(route)) return new SplitBillPage(draft,original);
        if ("own_handles".equals(route)) return new OwnHandlesPage(draft,original);
        if ("commitment_link".equals(route)) return new CommitmentLinkPage(draft,original);
        if ("transaction".equals(route)) return new TransactionFormPage(draft,original);
        if ("transaction_filters".equals(route)) return new TransactionFilterPage(draft,original);
        if ("transaction_bulk_category".equals(route)) return new BulkCategoryPage(draft,original);
        if (!"display_name".equals(route)) return null;
        return new UiPages.Page(route, "اسم العرض", draft, original) {
            @Override void build(LinearLayout body, UiComponents components) {
                UiPages.Page model = this;
                EditText name = components.field("اسم العرض", body);
                name.setTag("display_name_input"); name.setText(this.draft.getString("name", ""));
                name.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft.putString("name", s.toString()); }
                    public void afterTextChanged(Editable text) {}
                });
            }
            @Override boolean save() {
                getPreferences(MODE_PRIVATE).edit().putString("profile_name", this.draft.getString("name", "").trim()).apply();
                return true;
            }
            @Override boolean restoreAfterRecreation() { return true; }
        };
    }

    private void confirmDelete(String name, Runnable action) {
        new ChoiceDialogBuilder().setTitle("حذف " + name + "؟")
                .setPositiveButton("حذف", (dialog, which) -> action.run()).setNegativeButton("إلغاء", null).show();
    }

    /** Brand controls retain forest; selected choices use accent with the matching onPrimary ink in dark mode. */
    private int primaryFill() { return dark ? ACCENT : BRAND; }
    private int onPrimary() { return uiTheme.onPrimary; }

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
        View logo=sourceLogoBadge(source.name,size);
        return logo!=null?logo:identityBadge(source.name, source.kind, size);
    }

    private View applicationBadge(String packageName,String description,int size) {
        if (packageName != null) {
            try {
                ImageView icon = new ImageView(this);
                android.graphics.drawable.Drawable appIcon = getPackageManager().getApplicationIcon(packageName);
                icon.setImageDrawable(appIcon);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setPadding(dp(2), dp(2), dp(2), dp(2));
                UiKit.appIconPlate(icon, appIcon, dark, SURFACE, dp(size));
                icon.setContentDescription(description);
                return icon;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return null;
    }

    /** A source logo never changes the recorded merchant. */
    private View sourceLogoBadge(String name,int size) {
        String description="مصدر العملية: "+name;
        View appIcon=applicationBadge(BrandCatalog.sourcePackage(name),description,size);
        if(appIcon!=null)return appIcon;
        String brand=BrandCatalog.bank(name);
        if(brand!=null) {
            int res=brandLogo(brand);
            if(res!=0)return UiKit.logoBadge(this,res,brand,description,size,dark?0:BORDER,dark,SURFACE);
        }
        for(String packageName:getSharedPreferences("notification_settings",MODE_PRIVATE)
                .getStringSet("sources",java.util.Collections.emptySet())) {
            try {
                String label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName,0)).toString();
                if(label.equalsIgnoreCase(name))return applicationBadge(packageName,description,size);
            }catch(PackageManager.NameNotFoundException ignored){}
        }
        return null;
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
        field.setShowSoftInputOnFocus(false); field.setCursorVisible(false); field.setLongClickable(false);
        field.setKeyListener(null); field.setFocusable(true);
        field.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
            }
        });
        field.setCompoundDrawablePadding(dp(8));
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.ic_calendar);
        if (icon != null) { icon = icon.mutate(); icon.setTint(ACCENT); icon.setBounds(0, 0, dp(20), dp(20)); field.setCompoundDrawablesRelative(icon, null, null, null); }
        setDateValue(field, initial);
        field.setOnClickListener(v -> {
            LocalDate shown = dateValue(field) == null ? LocalDate.now() : dateValue(field);
            android.app.DatePickerDialog picker = new android.app.DatePickerDialog(this,
                    (view, year, month, day) -> setDateValue(field, LocalDate.of(year, month + 1, day)),
                    shown.getYear(), shown.getMonthValue() - 1, shown.getDayOfMonth());
            UiSheets.prepare(this, picker, uiTheme, REGULAR).show();
        });
        field.setOnKeyListener((v, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_ENTER && key != android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    && key != android.view.KeyEvent.KEYCODE_SPACE) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) field.performClick();
            return true;
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
        return ui.field(hint, parent);
    }

    private Spinner spinner(String label, String[] choices, LinearLayout parent) {
        TextView caption = label(label, 12, MUTED, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        parent.addView(caption, cp);
        Spinner spinner = new AppChoiceSpinner(label, choices);
        spinner.setId(View.generateViewId()); caption.setLabelFor(spinner.getId());
        spinner.setMinimumHeight(dp(UiTheme.TOUCH));
        spinner.setPadding(dp(12), 0, dp(12), 0);
        spinner.setBackground(ui.interactive(SURFACE, uiTheme.fieldOutline, UiTheme.FIELD_RADIUS));
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
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
                    value.setSingleLine(false);
                    value.setPadding(dp(12), dp(12), dp(12), dp(12));
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
            if (entries == null) {
                if (suppliedView != null && !(suppliedView instanceof ScrollView)) {
                    ScrollView scroll = new ScrollView(MainActivity.this); scroll.addView(suppliedView);
                    super.setView(scroll);
                }
                return UiSheets.prepare(MainActivity.this, super.create(), uiTheme, REGULAR);
            }
            LinearLayout content = form(); content.setTag("app_choice_menu");
            content.setPadding(dp(18), dp(16), dp(18), dp(12));
            LinearLayout header = new LinearLayout(MainActivity.this); header.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = label(heading == null ? "اختار الإجراء" : heading.toString(), 19, INK, true);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView close = ui.icon(R.drawable.ic_close, "إغلاق القائمة", () -> {});
            header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48))); content.addView(header);
            // Keep search and actions visible; a mixed form/list body must scroll as one at large font sizes.
            EditText search = entries.length >= 8 ? input("بحث في الخيارات", content) : null;
            if (search != null) { search.setTag("app_choice_search"); search.setContentDescription("بحث في الخيارات"); }
            int limit = Math.max(dp(150), Math.min(dp(420), getResources().getDisplayMetrics().heightPixels * 45 / 100));
            ScrollView scroll = new ScrollView(MainActivity.this) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int available = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED ? limit : Math.min(limit, View.MeasureSpec.getSize(heightSpec));
                    super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
                }
            };
            LinearLayout rows = new LinearLayout(MainActivity.this); rows.setOrientation(LinearLayout.VERTICAL);
            LinearLayout pickerBody = new LinearLayout(MainActivity.this); pickerBody.setOrientation(LinearLayout.VERTICAL);
            if (suppliedView != null) pickerBody.addView(suppliedView);
            pickerBody.addView(rows); scroll.addView(pickerBody);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            content.addView(scroll);
            super.setTitle((CharSequence)null); super.setView(content);
            AlertDialog dialog = UiSheets.prepare(MainActivity.this, super.create(), uiTheme, REGULAR);
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
                        row.setBackground(ui.interactive(row.isChecked() ? ACCENT_PALE : SURFACE, BORDER, 12));
                        row.setOnClickListener(v -> {
                            if (single) { if (picked != null) picked.onClick(dialog, index); }
                            else { checked[index] = row.isChecked(); row.setBackground(ui.interactive(checked[index] ? ACCENT_PALE : SURFACE, BORDER, 12)); if (toggled != null) toggled.onClick(dialog, index, checked[index]); }
                        });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(6); rows.addView(row, lp);
                    } else {
                        LinearLayout row = new LinearLayout(MainActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(12), dp(10), dp(12), dp(10)); row.setMinimumHeight(dp(56));
                        row.setBackground(ui.interactive(SURFACE, BORDER, 12)); row.setFocusable(true);
                        row.setContentDescription(text);
                        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
                            }
                        });
                        ImageView icon = new ImageView(MainActivity.this); icon.setImageResource(menuIcon(text, destructive)); icon.setColorFilter(destructive ? CORAL : ACCENT);
                        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
        Button button = ui.button(title, false, false, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
        page.addView(button, lp);
    }

    private void buttonStatus(String status) { toast(status); }

    private int parserDebugTaps;
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
        return new LedgerSnapshot(recurringCandidateIds(rows, db.recurring(), db.dismissedRecurringKeys()), rows, version, changes, normalized);
    }

    /** A local ledger edit: bump the generation so an in-flight snapshot is rejected, then reload the cache. */
    private void invalidateLedger() {
        ledgerVersion.incrementAndGet();
        refreshLedger(true);
    }

    /** True when redrawing cannot disturb input the user has not saved yet. */
    private boolean canRedrawNow() {
        if (isFinishing() || isDestroyed()) return false;
        if (formPages != null && formPages.isOpen()) return false;
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
        if (formPages != null && formPages.isOpen()) { formPages.show(); return; }
        // Any committed tracked write bumps the persistent revision, so the cached suggestions cannot stay stale
        // behind a page whose only refresh path is this render().
        sourceKindCache = null; statementOffsetCache = null;
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
        if (!fullScreen) {
            LinearLayout nav = bottomNavigation(); root.addView(nav);
            if (selectedTab == 0) nav.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
                // Keep the last home row clear of the navigation, raised add action and system insets.
                int clearance = nav.getHeight() + root.getPaddingBottom() + dp(16);
                if (page.getPaddingBottom() != clearance) page.setPadding(dp(16), dp(14), dp(16), clearance);
            });
        }
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
        if(selectedTab==5&&settingsFocus!=null){String focus=settingsFocus;settingsFocus=null;scroll.post(()->focusSettingRow(scroll,focus));}
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
                transactionCurrencyFilter+":"+transactionSourceFilter+":"+transactionBucketFilter+":"+java.util.Arrays.toString(transactionStatementIds)+":"+transactionExcludeTransfers+":"+
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
        if (formPages != null && formPages.isOpen()) { formPages.back(); return; }
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
        top.addView(homeIconButton(R.drawable.ic_search, "بحث في العمليات", () -> {
            selectedTab = 6; searchOpen = true; render();
        }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.setMarginStart(dp(8));
        top.addView(homeIconButton(R.drawable.ic_settings, "الإعدادات", () -> { settingsSection="";selectedTab = 5; render(); }), settingsParams);
        page.addView(top);

        updateCard();
        whatsNewCard();
        // MVP home: one number, one period, one definition. The daily budget and points keep their data, dialogs,
        // widgets and settings entry («الميزانية اليومية والراتب», «ميزانياتك»); only their home card is off.
        if (HOME_SHOWS_DAILY_BUDGET) dailyHomeCard();
        // One card, one period, one definition (MoneyFlow.statement): «دخل / خرج / باقي» from the pay day to today.
        // Nothing else on the home screen carries a number with another period or definition (lesson of 19 October).
        homeStatementCard();
        monthlyReadyCard();
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

    /** One authoritative statement, with mutually exclusive details and natural-width amounts. */
    private void homeStatementCard() {
        LocalDate[] period = statementPeriod();
        ZoneId zone = ZoneId.systemDefault();
        List<MoneyFlow.Item> items = db.flowItems(period[0], period[1], zone);
        MoneyFlow.Statement s = MoneyFlow.statement(items, period, zone, "EGP");
        DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("d MMMM", new Locale("ar"));
        WrapRow heading = new WrapRow(this);
        heading.addView(label("كشف الفترة", 18, INK, true));
        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        TextView periodChip = label(isolate(period[0].format(dayMonth)) + " – اليوم ⌄", 11, INK, false);
        periodChip.setPadding(dp(8), dp(4), dp(8), dp(4)); periodChip.setMinHeight(dp(UiTheme.TOUCH));
        periodChip.setGravity(Gravity.CENTER); periodChip.setBackground(rounded(ACCENT_PALE, 99));
        periodChip.setContentDescription("الفترة من " + period[0].format(dayMonth) + " لليوم");
        // Home keeps its salary-aligned period. The chip explains it; custom ranges remain on the statement page.
        periodChip.setOnClickListener(v -> statementInfo(s)); tools.addView(periodChip);
        TextView info = label("ⓘ", 20, MUTED, false); info.setGravity(Gravity.CENTER);
        info.setContentDescription("معلومات كشف الفترة"); info.setOnClickListener(v -> statementInfo(s));
        tools.addView(info, new LinearLayout.LayoutParams(dp(UiTheme.TOUCH), -2));
        info.setMinHeight(dp(UiTheme.TOUCH)); heading.addView(tools);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2); hp.topMargin = dp(12); page.addView(heading, hp);

        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setTag("home_statement"); box.setBackground(outlined(SURFACE, BORDER, 22)); box.setClipToOutline(true);
        page.addView(box, new LinearLayout.LayoutParams(-1, -2));
        TextView verdict = label(StatementRows.verdict(s.income, s.out), 13, s.out > s.income ? 0xfff0c987 : 0xffd9ebe3, true);
        verdict.setBackgroundColor(BRAND); verdict.setPadding(dp(14), dp(9), dp(14), dp(9));
        box.addView(verdict, new LinearLayout.LayoutParams(-1, -2));
        statementTotal(box, "فلوس دخلت", "↓", s.income, INCOME, 1);
        if (homeStatementOpen == 1) {
            statementCaption(box, "النسب من إجمالي الداخل");
            for (StatementRows.Row row : StatementRows.in(s, items, zone)) {
                String detail = "راتب".equals(row.name) && s.salaryWhen > 0
                        ? "يوم " + isolate(Instant.ofEpochMilli(s.salaryWhen).atZone(zone).toLocalDate().format(dayMonth)) : row.detail;
                statementComponent(box, row, detail, s.income, INCOME);
            }
        }
        divider(box);
        statementTotal(box, "فلوس خرجت", "↑", s.out, ORANGE, 2);
        if (homeStatementOpen == 2) {
            statementCaption(box, "النسب من إجمالي الخارج");
            for (StatementRows.Row row : StatementRows.out(s)) statementComponent(box, row, row.detail, s.out, ACCENT);
        }
        WrapRow difference = statementRow("الفرق", "مش رصيد حسابك", "=", s.remaining, dark ? 0xfff0c987 : 0xff8a5a00, false);
        difference.setTag("statement_difference"); difference.setBackgroundColor(dark ? 0xff393221 : 0xfffcf2db);
        // All contributing income and outflow rows; the statement page explains their signed difference.
        difference.setOnClickListener(v -> openStatement("", false, StatementRows.contributors(s, items, zone))); box.addView(difference, new LinearLayout.LayoutParams(-1, -2));
        if (s.unlinkedTransfer > 0) {
            TextView warning = label("تحويل غير مربوط بقيمة " + isolate(money(s.unlinkedTransfer, "EGP")) + " قد يكون بين حساباتك — اربطه", 12, CORAL, true);
            warning.setMinHeight(dp(UiTheme.TOUCH)); warning.setPadding(0, dp(8), 0, dp(4));
            warning.setTag("statement_unlinked");
            warning.setOnClickListener(v -> { ExpenseDb.Transaction tx = db.transactionById(s.unlinkedTransferId); if (tx != null) reconcileTransferDialog(tx); });
            page.addView(warning);
        }
    }

    private static String isolate(String text) { return "\u2068" + text + "\u2069"; }

    private void statementInfo(MoneyFlow.Statement s) {
        String text = "الفرق هو الداخل ناقص الخارج في نفس الفترة؛ مش رصيد حسابك.\n" +
                "التحويل بين حساباتك وسداد كروتك مش داخلين؛ الكاش محسوب لما اتسحب";
        if (s.cashSpending > 0) text += "\nمصروفات من الكاش " + isolate(money(s.cashSpending, "EGP")) + " · محسوبة وقت السحب";
        if (s.internal > 0) text += "\nبين حساباتك وسداد كروتك " + isolate(money(s.internal, "EGP")) + " · مش دخل ولا خرج";
        new ChoiceDialogBuilder().setTitle("كشف الفترة").setMessage(text).setPositiveButton("تمام", null).show();
    }

    private void statementCaption(LinearLayout box, String text) {
        TextView caption = label(text, 11, MUTED, false); caption.setPadding(dp(14), dp(4), dp(14), 0); box.addView(caption);
    }

    private void statementTotal(LinearLayout box, String name, String badge, long amount, int tint, int section) {
        WrapRow row = statementRow(name, "", badge, amount, tint, true);
        row.setTag(section == 1 ? "statement_income" : "statement_out");
        boolean expanded = homeStatementOpen == section;
        row.setContentDescription(name + "، " + money(amount, "EGP") + "، " + (expanded ? "مفتوح" : "مقفول"));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
                info.addAction(expanded ? android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                        : android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
                if (Build.VERSION.SDK_INT >= 30) info.setStateDescription(expanded ? "مفتوح" : "مقفول");
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_EXPAND || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_COLLAPSE)
                    return host.performClick();
                return super.performAccessibilityAction(host, action, args);
            }
        });
        row.setOnClickListener(v -> { homeStatementOpen = expanded ? 0 : section; render(); });
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private WrapRow statementRow(String name, String detail, String badge, long amount, int tint, boolean disclosure) {
        WrapRow row = new WrapRow(this); row.setPadding(dp(12), dp(7), dp(12), dp(7)); row.setMinimumHeight(dp(UiTheme.TOUCH));
        LinearLayout leading = new LinearLayout(this); leading.setGravity(Gravity.CENTER_VERTICAL);
        if (!badge.isEmpty()) {
            TextView icon = label(badge, 17, tint, true); icon.setGravity(Gravity.CENTER); icon.setBackground(rounded(badge.equals("↑") ? (dark ? 0xff493425 : 0xffffecd0) : badge.equals("=") ? (dark ? 0xff5d4a26 : 0xfff0c987) : ACCENT_PALE, 99));
            icon.setMinHeight(dp(30)); leading.addView(icon, new LinearLayout.LayoutParams(dp(30), -2));
        }
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(name, 13, INK, true));
        if (!detail.isEmpty()) copy.addView(label(detail, 10, MUTED, false));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.setMarginStart(dp(8)); leading.addView(copy, cp);
        row.addView(leading);
        TextView value = statementAmount(amount, "الفرق".equals(name) ? tint : INK, disclosure ? (homeStatementOpen == ("فلوس دخلت".equals(name) ? 1 : 2) ? "  ⌃" : "  ⌄") : ""); row.addView(value);
        return row;
    }

    private TextView statementAmount(long amount, int tint, String suffix) {
        String number = (amount < 0 ? "−" : "") + String.format(Locale.US, "%,.2f", java.math.BigDecimal.valueOf(amount).abs().movePointLeft(2));
        String text = "\u2066" + number + " ج.م" + suffix + "\u2069";
        android.text.SpannableString span = new android.text.SpannableString(text);
        int currency = text.indexOf("ج.م");
        span.setSpan(new android.text.style.RelativeSizeSpan(0.65f), currency, currency + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView value = label("", 16, tint, true); value.setText(span); value.setTag("statement_amount");
        value.setTextDirection(View.TEXT_DIRECTION_LTR); value.setFontFeatureSettings("tnum");
        // No singleLine, ellipsize, autosizing or fixed height: the enclosing row moves the whole amount below.
        return value;
    }

    private void statementComponent(LinearLayout box, StatementRows.Row item, String detail, long total, int tint) {
        WrapRow row = new WrapRow(this); row.setPadding(dp(14), dp(6), dp(14), dp(6)); row.setMinimumHeight(dp(UiTheme.TOUCH));
        row.setTag("statement_component:" + item.name);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(item.name, 12, INK, true));
        if (!detail.isEmpty()) copy.addView(label(detail, 10, MUTED, false));
        LinearLayout shares = new LinearLayout(this); shares.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(10000); bar.setProgress((int)Math.round(StatementRows.share(item.amount, total) * 10000));
        bar.setProgressTintList(ColorStateList.valueOf(tint)); bar.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shares.addView(bar, new LinearLayout.LayoutParams(dp(90), dp(5)));
        TextView percent = label(StatementRows.percent(item.amount, total), 10, MUTED, false);
        percent.setPadding(dp(5), 0, 0, 0); shares.addView(percent); copy.addView(shares); row.addView(copy);
        row.addView(statementAmount(item.amount, INK, "  ‹"));
        row.setOnClickListener(v -> {
            if (item.bucket == MoneyFlow.Bucket.INFLOW) {
                openStatement(MoneyFlow.Bucket.INFLOW.name(), false, item.ids);
            } else openStatement(item.bucket.name(), item.bucket == MoneyFlow.Bucket.OPEN_TRANSFER);
        });
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
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
        balanceColumn.addView(label(caption, 11, 0xccffffff, false));
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
        TextView type = label(tag, 11, Color.WHITE, true);
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
        }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        filterParams.setMarginStart(dp(8));
        bar.addView(iconButton(R.drawable.ic_filter, "فلترة", this::transactionFilterDialog), filterParams);
        bar.addView(iconButton(bulkMode?R.drawable.ic_close:R.drawable.ic_check,"تحديد العمليات",()->{
            bulkMode=!bulkMode;bulkSelected.clear();render();
        }),new LinearLayout.LayoutParams(dp(44),dp(44)));
        page.addView(bar);
        if (transactionStatementList && transactionBucketFilter.isEmpty() && transactionStatementIds != null) {
            ZoneId zone = ZoneId.systemDefault();
            MoneyFlow.Statement statement = MoneyFlow.statement(db.flowItems(transactionPeriod[0], transactionPeriod[1], zone), transactionPeriod, zone, "EGP");
            page.addView(label("الفرق · " + isolate(money(statement.remaining, "EGP")), 18, INK, true));
            page.addView(label("مش رصيد حسابك · الداخل ناقص الخارج في الفترة دي", 12, MUTED, false));
        }
        bulkChecks.clear();bulkCount=null;bulkApply=null;
        if(bulkMode) {
            LinearLayout tools=card();bulkCount=label("المحدد: "+bulkSelected.size(),14,INK,true);tools.addView(bulkCount);
            LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
            Button all=ui.button("اختيار النتائج كلها",false,false,()->{for(CheckBox c:bulkChecks.values())if(c.isEnabled())c.setChecked(true);});
            actions.addView(all,new LinearLayout.LayoutParams(0,-2,1));
            Button clear=ui.button("إلغاء التحديد",false,false,()->{for(CheckBox c:bulkChecks.values())c.setChecked(false);});
            LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(0,-2,1);clearParams.setMarginStart(dp(8));actions.addView(clear,clearParams);
            LinearLayout.LayoutParams actionsParams=new LinearLayout.LayoutParams(-1,-2);actionsParams.topMargin=dp(10);tools.addView(actions,actionsParams);
            bulkApply=ui.button("تغيير تصنيف المحدد",true,false,this::bulkCategoryDialog);
            LinearLayout.LayoutParams applyParams=new LinearLayout.LayoutParams(-1,-2);applyParams.topMargin=dp(8);tools.addView(bulkApply,applyParams);
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
            if ("expense".equals(item.kind) && "غير مصنف".equals(item.category) || item.needsReview) {
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
                // Uncategorized expenses and everything waiting for review (the new parser's REVIEW with its reason).
                transactionCategoryFilter = NEEDS_ATTENTION_FILTER; transactionKindFilter = "";
                transactionBucketFilter = ""; transactionCurrencyFilter = ""; transactionSourceFilter = null; transactionStatementList=false; transactionStatementIds=null;
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
        statementOffsetCache = dayOffsets;
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
                Button reset=ui.button("مسح البحث والفلاتر",false,false,()->{resetTransactionFilters();render();});
                LinearLayout.LayoutParams resetParams=new LinearLayout.LayoutParams(-2,-2);resetParams.topMargin=dp(10);box.addView(reset,resetParams);
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
        Bundle draft=new Bundle();draft.putString("ids",bulkSelected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        draft.putString("category",CATEGORIES[0]);savePageScroll();formPages.push(new BulkCategoryPage(draft,draft));
    }

    private final class BulkCategoryPage extends UiPages.Page {
        BulkCategoryPage(Bundle draft,Bundle original){super("transaction_bulk_category","تغيير تصنيف المصروفات",draft,original);}
        private List<Long> ids(){List<Long> ids=new ArrayList<>();for(String id:draft.getString("ids","").split(","))if(!id.isEmpty())ids.add(Long.parseLong(id));return ids;}
        @Override void build(LinearLayout body,UiComponents components){
            List<Long> ids=ids();components.caption("مراجعة "+ids.size()+" مصروف · الأرقام والحسابات والمصدر لن تتغير",body);
            boolean open=!ids.isEmpty();for(Long id:ids)open&=openTransferRow(id);
            String[] choices=open?withOwnTransfer(CATEGORIES):CATEGORIES;
            if(open)components.caption(OPEN_TRANSFER_HINT,body);
            Button category=components.button(draft.getString("category"),false,false,()->{});components.caption("التصنيف الجديد",body);body.addView(category);
            category.setOnClickListener(v->chooseValue("التصنيف الجديد",choices,draft.getString("category"),value->{draft.putString("category",value);category.setText(value);}));
            for(Long id:ids){ExpenseDb.Transaction tx=db.transactionById(id);if(tx!=null)components.caption(tx.merchant+" · "+money(tx.amountMinor,tx.currency),body);}
        }
        @Override String saveLabel(){return "اعتماد التصنيف";}
        private List<Long> ownTransfers;
        @Override boolean save(){
            if(OWN_TRANSFER_CHOICE.equals(draft.getString("category"))){ownTransfers=ids();bulkSelected.clear();bulkMode=false;return true;}
            int count=db.updateCategories(ids(),draft.getString("category"));bulkSelected.clear();bulkMode=false;toast("تم تصنيف "+count+" مصروف");return true;
        }
        @Override void onSaved(){if(ownTransfers!=null)reconcileTransfers(ownTransfers);}
        @Override boolean restoreAfterRecreation(){return true;}
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
        transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionStatementList = false; transactionStatementIds = null;
    }

    /** The period chip: pick a month, pick a day-to-day range, or go back to the month view. */
    private void choosePeriodMenu() {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("اختيار شهر"); actions.add(this::chooseMonth);
        labels.add("فترة من يوم لـ يوم"); actions.add(this::chooseTransactionPeriod);
        if (transactionPeriod != null) {
            labels.add("رجوع لعرض " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("ar"))));
            actions.add(() -> { transactionPeriod = null; transactionCurrencyFilter = ""; transactionExcludeTransfers = false; transactionStatementList = false; transactionStatementIds = null; render(); });
        }
        new ChoiceDialogBuilder().setTitle("الفترة").setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton("إلغاء", null).show();
    }

    private void transactionFilterDialog() {
        Bundle draft=new Bundle();draft.putString("kind",transactionKindFilter);draft.putString("category",transactionCategoryFilter);
        draft.putLong("source",transactionSourceFilter==null?-1:transactionSourceFilter);draft.putBoolean("clear",false);
        savePageScroll();formPages.push(new TransactionFilterPage(draft,draft));
    }

    private final class TransactionFilterPage extends UiPages.Page {
        TransactionFilterPage(Bundle draft,Bundle original){super("transaction_filters","فلترة العمليات",draft,original);}
        @Override void build(LinearLayout body,UiComponents components) {
            components.caption("النوع",body);String[] values={"","expense","income","transfer"};
            components.choices(new String[]{"الكل","مصروف","دخل","تحويل"},null,java.util.Arrays.asList(values).indexOf(draft.getString("kind")),
                    i->draft.putString("kind",values[i]),body);
            Button category=components.button(draft.getString("category","").isEmpty()?"كل التصنيفات":draft.getString("category"),false,false,()->{});
            components.caption("التصنيف",body);body.addView(category);
            String[] names=new String[CATEGORIES.length+1];names[0]="كل التصنيفات";System.arraycopy(CATEGORIES,0,names,1,CATEGORIES.length);
            category.setOnClickListener(v->chooseValue("التصنيف",names,draft.getString("category","").isEmpty()?names[0]:draft.getString("category"),value->{
                draft.putString("category",value.equals(names[0])?"":value);category.setText(value);}));
            LinearLayout account=new LinearLayout(MainActivity.this);account.setOrientation(LinearLayout.VERTICAL);body.addView(account);drawAccount(account);
            if(!transactionBucketFilter.isEmpty()||!transactionCurrencyFilter.isEmpty()||transactionExcludeTransfers){
                List<String> fixed=new ArrayList<>();if(!transactionBucketFilter.isEmpty())fixed.add(MoneyFlow.filterLabel(transactionBucketFilter));
                if(!transactionCurrencyFilter.isEmpty())fixed.add(transactionCurrencyFilter);if(transactionExcludeTransfers)fixed.add("بدون تحويلات");
                components.caption("مفتوح من الرئيسية: "+String.join(" · ",fixed)+" — «مسح الفلاتر» يشيله",body);
            }
            components.caption("الفترة والبحث هيفضلوا زي ما هم عند مسح الفلاتر",body);
            body.addView(components.button("مسح الفلاتر",false,false,()->{draft.putBoolean("clear",true);formPages.save();}));
        }
        private void drawAccount(LinearLayout body) {
            body.removeAllViews();List<ExpenseDb.PaymentSource> sources=db.paymentSources();long selected=draft.getLong("source",-1);
            ExpenseDb.PaymentSource picked=null;for(ExpenseDb.PaymentSource source:sources)if(source.id==selected)picked=source;
            ui.choiceCard("الحساب",picked==null?"كل الحسابات":db.sourceLabel(picked.id),picked==null?null:sourceBadge(picked,40),()->{
                String[] labels=new String[sources.size()+1];labels[0]="كل الحسابات";int checked=0;
                for(int i=0;i<sources.size();i++){labels[i+1]=db.sourceLabel(sources.get(i).id);if(sources.get(i).id==selected)checked=i+1;}
                new ChoiceDialogBuilder().setTitle("الحساب").setSingleChoiceItems(labels,checked,(d,i)->{
                    draft.putLong("source",i==0?-1:sources.get(i-1).id);d.dismiss();drawAccount(body);
                }).setNegativeButton("إلغاء",null).show();
            },body).setTag("filter_source");
        }
        @Override String saveLabel(){return "تطبيق";}
        @Override boolean save(){
            if(draft.getBoolean("clear")){clearTransactionFilters();return true;}
            transactionKindFilter=draft.getString("kind","");if(!transactionKindFilter.isEmpty()){transactionBucketFilter="";transactionStatementList=false;transactionStatementIds=null;}
            transactionCategoryFilter=draft.getString("category","");long source=draft.getLong("source",-1);transactionSourceFilter=source<0?null:source;
            return true;
        }
        @Override boolean restoreAfterRecreation(){return true;}
    }

    private void transactionRow(ExpenseDb.Transaction item, boolean suggested) {
        transactionRow(listCard(page), item, suggested);
    }

    private void transactionRow(LinearLayout container, ExpenseDb.Transaction item, boolean suggested) {
        boolean statementRows = selectedTab == 6 && transactionStatementList;
        if (container.getChildCount() > 0) divider(container);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if(bulkMode&&selectedTab==6) {
            CheckBox check=new CheckBox(this);check.setContentDescription("تحديد "+item.merchant);
            check.setEnabled("expense".equals(item.kind));check.setChecked(bulkSelected.contains(item.id));
            check.setOnCheckedChangeListener((b,checked)->{if(checked)bulkSelected.add(item.id);else bulkSelected.remove(item.id);updateBulkCount();});
            bulkChecks.put(item.id,check);line.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));
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
        long repaid = (statementRows || MoneyFlow.SPENDING_FILTER.equals(transactionBucketFilter)) && "expense".equals(item.kind) ? db.reimbursedOf(item.id) : 0;
        String shownAmount = statementRows ? ("income".equals(item.kind) ? "+" : "expense".equals(item.kind) ? "−" : "")
                + money(statementContribution(item), item.currency)
                : repaid > 0 ? "−" + money(Math.max(0, item.amountMinor - repaid), item.currency) : signedMoney(item);
        TextView amount = label(shownAmount, 14, kindColor(item.kind), true);
        if (statementRows) amount.setTag("statement_transaction_amount:" + item.id);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        line.addView(amount);
        row.addView(line);
        if (statementRows && "income".equals(item.kind) && statementContribution(item) < item.amountMinor) {
            row.addView(label("المبلغ المسجل " + isolate(money(item.amountMinor, item.currency)) + " · المحسوب في كشف الفترة "
                    + isolate(money(statementContribution(item), item.currency)), 12, MUTED, false));
        }
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
            confirm.setOnClickListener(v -> commitmentFromTransaction(item));
            row.addView(confirm);
        }
        container.addView(row);
    }

    private void transactionDetailsScreen(ExpenseDb.Transaction item) {
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", () -> {
            returnFromTransaction();
        }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("تفاصيل العملية", UiTheme.TITLE, INK, true); title.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(iconButton(R.drawable.ic_more, "خيارات", () -> transactionOptions(item)),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        if(item.needsReview) {
            // The new parser's own reasons, in the user's language, when it decided this row; otherwise the legacy wording.
            String why=ShadowStore.reasonsOf(db.getReadableDatabase(),item.id);
            if(why!=null&&!why.isEmpty())
                detailRow(details,"حالة المراجعة",label(String.join("\n",ReviewReasons.text(java.util.Arrays.asList(why.split(",")))),12,CORAL,false));
            else detailRow(details,"حالة المراجعة",label("expense".equals(item.kind)&&MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(item.category)?"تحويل محتاج ربط؛ الخصم ما زال محسوبًا لحين مراجعتك":"مصدر العملية يحتاج مراجعة؛ المبلغ ما زال محسوبًا",12,CORAL,false));
        }
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
        Button edit = ui.button("تعديل العملية", true, false, () -> editTransactionDialog(item));
        actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
        android.widget.ImageButton delete = ui.icon(R.drawable.ic_trash, "حذف العملية", () -> confirmDeleteTransaction(item));
        delete.setImageTintList(ColorStateList.valueOf(uiTheme.danger));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(UiTheme.TOUCH + 6), dp(UiTheme.TOUCH + 6)); deleteParams.setMarginStart(dp(10));
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
    /** Same effective contribution used by ExpenseDb.flowItems; cache once per render, never alter the ledger. */
    private long statementContribution(ExpenseDb.Transaction tx) {
        if (statementOffsetCache == null) statementOffsetCache = db.reimbursementOffsets();
        return ExpenseDb.effectiveAmount(tx, statementOffsetCache);
    }

    private boolean matchesTransactionFilters(ExpenseDb.Transaction tx,String text){
        return (transactionKindFilter.isEmpty()||transactionKindFilter.equals(tx.kind))
                &&(transactionCategoryFilter.isEmpty()||transactionCategoryFilter.equals(tx.category)||
                   NEEDS_ATTENTION_FILTER.equals(transactionCategoryFilter)&&("expense".equals(tx.kind)&&MoneyFlow.UNCATEGORIZED.equals(tx.category)||tx.needsReview))
                &&(!transactionExcludeTransfers||!isSentTransfer(tx))
                &&MoneyFlow.matchesFilter(transactionBucketFilter,bucketOf(tx))
                &&(!transactionStatementList || !MoneyFlow.isStatementOut(bucketOf(tx)) || bucketOf(tx)==MoneyFlow.Bucket.CASH_WITHDRAWAL || !ExpenseDb.paidFromCash(tx,sourceKinds()))
                &&(!transactionStatementList || transactionStatementIds==null || java.util.Arrays.stream(transactionStatementIds).anyMatch(id -> id==tx.id))
                &&(!transactionStatementList || statementContribution(tx) > 0)
                &&(transactionCurrencyFilter.isEmpty()||transactionCurrencyFilter.equals(tx.currency))
                &&(transactionSourceFilter==null||transactionSourceFilter.equals(tx.paymentSourceId)||transactionSourceFilter.equals(tx.destinationSourceId))
                &&(text.isEmpty()||(tx.merchant+" "+tx.category+" "+money(tx.amountMinor,tx.currency)).toLowerCase(Locale.ROOT).contains(text));
    }
    private boolean matchesCaptureFilters(CaptureEvents.Event event,String text){
        // Canceled attempts are audit history, not active expenses awaiting classification.
        if ("غير مصنف".equals(transactionCategoryFilter)||NEEDS_ATTENTION_FILTER.equals(transactionCategoryFilter)) return false;
        // The list behind the home number holds only the rows that add up to it; no cancelled attempts beside them.
        if (transactionStatementList || !MoneyFlow.listsCaptureHistory(transactionBucketFilter)) return false;
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
        Button proceed=ui.button(primary,true,false,()->accept.accept(sheet));proceed.setTag("financial_journey_primary");proceed.setMinHeight(dp(52));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(12);content.addView(proceed,pp);proceed.setOnClickListener(v->accept.accept(sheet));
        if(secondary!=null){Button previous=ui.button(secondary,false,false,()->back.accept(sheet));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);content.addView(previous,bp);}
        showJourneyWindow(sheet,content,Math.round(getResources().getDisplayMetrics().heightPixels*0.80f));sheet.setCanceledOnTouchOutside(false);return sheet;
    }
    private void showJourneyWindow(android.app.Dialog sheet,LinearLayout content,int height){
        sheet.setContentView(content);sheet.setCanceledOnTouchOutside(true);android.view.Window window=sheet.getWindow();window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.35f);window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.setNavigationBarColor(SURFACE);window.getDecorView().setPadding(0,0,0,0);sheet.show();window.setLayout(-1,Math.min(height,Math.round(getResources().getDisplayMetrics().heightPixels*0.80f)));SystemUi.apply(window,content);
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

    /** Other incomes of the same amount and currency from the day before to the day after, nearest first. */
    private List<ExpenseDb.Transaction> incomeCopies(ExpenseDb.Transaction item) {
        List<ExpenseDb.Transaction> copies = new ArrayList<>();
        LocalDate day = Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate();
        for (ExpenseDb.Transaction other : db.transactionsBetween(day.minusDays(1), day.plusDays(2)))
            if (other.id != item.id && "income".equals(other.kind) && other.amountMinor == item.amountMinor && other.currency.equals(item.currency))
                copies.add(other);
        copies.sort((a, b) -> Long.compare(Math.abs(a.when - item.when), Math.abs(b.when - item.when)));
        return copies;
    }

    /**
     * Tester, 24 Oct 2026: the same incoming transfer arrived as a bank SMS and an InstaPay notification and was counted
     * twice, with no way to say so. The bank copy is kept (it names the account); both messages stay as evidence.
     */
    private void mergeIncomeCopyDialog(ExpenseDb.Transaction item, List<ExpenseDb.Transaction> copies) {
        String[] names = new String[copies.size()];
        for (int i = 0; i < copies.size(); i++) {
            ExpenseDb.Transaction copy = copies.get(i);
            names[i] = UiKit.displayName(copy.merchant) + "\n" + money(copy.amountMinor, copy.currency) + " · " + sourceDate(copy.when) + " · " + copy.source;
        }
        new ChoiceDialogBuilder().setTitle("أنهي عملية هي نفس الفلوس دي؟").setItems(names, (picker, which) -> {
            ExpenseDb.Transaction copy = copies.get(which);
            boolean keepItem = RepairRules.keepFirst(item.source, copy.source);
            ExpenseDb.Transaction keep = keepItem ? item : copy, drop = keepItem ? copy : item;
            new ChoiceDialogBuilder().setTitle("ادمجهم في عملية واحدة؟")
                    .setMessage("هتفضل «" + UiKit.displayName(keep.merchant) + "» من " + keep.source + "، والنسخة التانية تتشال من الدخل. الرسالتين الأصليتين محفوظين في تفاصيل العملية.")
                    .setPositiveButton("ادمجهم", (confirm, ignored) -> {
                        try {
                            db.reconcileIncome(keep.id, java.util.Collections.singletonList(drop.id));
                            selectedTransaction = db.transactionById(keep.id);
                            toast("اتدمجت؛ الفلوس دي بقت محسوبة مرة واحدة");
                            render();
                        } catch (IllegalArgumentException conflict) {
                            toast("مقدرناش ندمجهم: واحدة منهم مربوطة بحاجة تانية. راجعها الأول");
                        }
                    }).setNegativeButton("إلغاء", null).show();
        }).show();
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
            List<ExpenseDb.Transaction> copies = incomeCopies(item);
            if (!copies.isEmpty()) { labels.add("اتسجلت مرتين؟ ادمجها مع عملية تانية"); actions.add(() -> mergeIncomeCopyDialog(item, copies)); }
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
        Button review=ui.button("راجع الربط",true,false,()->{});
        LinearLayout.LayoutParams reviewParams=new LinearLayout.LayoutParams(-1,-2);reviewParams.topMargin=dp(10);content.addView(review,reviewParams);
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

    private void reconcileTransferDialog(ExpenseDb.Transaction item) { reconcileTransferDialog(item, null); }

    private void reconcileTransferDialog(ExpenseDb.Transaction item, Runnable afterLinked) {
        LinearLayout content=form();
        content.addView(label("أصل التحويل + الرسوم لازم يساوي الخصم. اختار فقط رسائل استلام نفس التحويل؛ راجع المصدر الأصلي عند الشك.",13,MUTED,false));
        EditText principal=input("أصل المبلغ المحوّل",content);
        principal.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        principal.setText(String.format(Locale.ROOT,"%.2f",item.amountMinor/100.0));
        EditText fee=input("رسوم البنك",content);
        fee.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);fee.setText(String.format(Locale.ROOT,"%.2f",db.transferFee(item.id)/100.0));
        List<ExpenseDb.PaymentSource> accounts=new ArrayList<>();
        for(ExpenseDb.PaymentSource source:db.accountSources(false))
            if("حساب".equals(source.kind)||"محفظة".equals(source.kind)||ExpenseDb.APP_SOURCE_KIND.equals(source.kind))accounts.add(source);
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
                        if(afterLinked!=null)afterLinked.run();
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
        Bundle draft = new Bundle(); draft.putString("add", ""); draft.putStringArrayList("remove", new ArrayList<>());
        savePageScroll(); formPages.push(new OwnHandlesPage(draft, draft));
    }

    private final class OwnHandlesPage extends DraftFormPage {
        OwnHandlesPage(Bundle draft, Bundle original) { super("own_handles", "حساباتك على إنستاباي", draft, original); }
        private ArrayList<String> removed() { ArrayList<String> list = draft.getStringArrayList("remove"); return list == null ? new ArrayList<>() : list; }
        @Override void build(LinearLayout body, UiComponents components) {
            List<String> handles = db.ownHandles();
            body.addView(components.label(handles.isEmpty() ? "لسه مفيش حسابات مؤكدة. ضيف حسابك هنا علشان تحويلاتك لنفسك ما تتحسبش صرف." :
                    "التحويلات من وإلى الحسابات دي مش بتتحسب صرف ولا دخل. اختار حساب لو عايز تشيله.", UiTheme.BODY, MUTED));
            EditText add = textField("حساب جديد", "add", body); add.setHint("مثال: name@instapay");
            add.setTextDirection(View.TEXT_DIRECTION_LTR);
            add.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            if (handles.isEmpty()) { errorLabel(body); return; }
            components.caption("شيل حساب", body);
            LinearLayout rows = new LinearLayout(MainActivity.this); rows.setOrientation(LinearLayout.VERTICAL);
            // Same search threshold as the shared pickers; the label is the pickers' too so it reads the same everywhere.
            EditText search = handles.size() >= 8 ? components.field("بحث في الخيارات", body) : null; if (search != null) { search.setTag("app_choice_search"); search.setContentDescription("بحث في الخيارات"); }
            body.addView(rows);
            Runnable draw = () -> {
                rows.removeAllViews();
                String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
                for (String handle : handles) {
                    if (!query.isEmpty() && !handle.toLowerCase(Locale.ROOT).contains(query)) continue;
                    CheckBox box = new CheckBox(MainActivity.this); box.setText(handle); box.setTextDirection(View.TEXT_DIRECTION_LTR);
                    box.setTextColor(INK); box.setTypeface(REGULAR); box.setTextSize(UiTheme.BODY); box.setMinHeight(dp(UiTheme.TOUCH));
                    box.setButtonTintList(ColorStateList.valueOf(ACCENT)); box.setChecked(removed().contains(handle));
                    box.setOnCheckedChangeListener((v, checked) -> {
                        ArrayList<String> next = new ArrayList<>(removed()); next.remove(handle); if (checked) next.add(handle);
                        draft.putStringArrayList("remove", next);
                    });
                    rows.addView(box);
                }
                if (rows.getChildCount() == 0) rows.addView(ui.label("مفيش نتائج للبحث", UiTheme.BODY, MUTED));
            };
            if (search != null) search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { draw.run(); }
                public void afterTextChanged(Editable s) {}
            });
            draw.run(); errorLabel(body);
        }
        @Override boolean save() {
            String value = text("add").trim();
            if (!value.isEmpty() && !value.contains("@")) return fail("اكتب الحساب كامل، مثال name@instapay", "add");
            for (String handle : removed()) db.removeOwnHandle(handle);
            if (!value.isEmpty()) db.addOwnHandle(value);
            return true;
        }
    }

    private void reportTransactionReading(ExpenseDb.Transaction item) {
        List<ExpenseDb.SourceEvidence> messages = db.sourceEvidence(item.id);
        String recorded = ("income".equals(item.kind) ? "دخل" : "transfer".equals(item.kind) ? "تحويل بين حساباتي" : "مصروف") + " · " + money(item.amountMinor, item.currency)
                + " · " + UiKit.displayName(item.merchant) + " · " + item.category + " · " + transactionTimeLabel(item, true);
        StringBuilder redacted = new StringBuilder();
        for (ExpenseDb.SourceEvidence evidence : messages) {
            if (evidence.body == null || evidence.failed) continue;
            if (redacted.length() > 0) redacted.append("\n\n");
            redacted.append("المرسل: ").append(evidence.sender).append("\n").append(Redaction.text(evidence.body));
        }
        reportMessageReading(recorded, redacted.length() == 0 ? "(الرسالة الأصلية غير متاحة؛ اكتب صيغتها هنا لو فاكرها)" : redacted.toString());
    }

    /**
     * «الرسالة اتقرت غلط؟»: the redacted message and what the app did with it, as an e-mail draft the user reviews and
     * sends. From a ledger row (reportTransactionReading) or a message the app did not record (missedBankMessages).
     */
    private void reportMessageReading(String recorded, String redactedBody) {
        Bundle draft = new Bundle();
        draft.putString("body", redactedBody);
        draft.putString("correct", "");
        savePageScroll();
        formPages.push(new DraftFormPage("report_reading", "الرسالة اتقرت غلط؟", draft, new Bundle(draft)) {
            @Override void build(LinearLayout body, UiComponents components) {
                components.caption("التطبيق سجّلها كده", body);
                body.addView(components.label(recorded, UiTheme.BODY, INK));
                body.addView(components.label("حجبنا الأرقام والمبالغ وبعض الأسماء من الرسالة تلقائيًا. راجع النص واكتب تحتها الصح، وهيفتح مسودة إيميل وإنت اللي ترسلها.", UiTheme.CAPTION, MUTED));
                for (String key : new String[]{"body", "correct"}) {
                    EditText field = textField("body".equals(key) ? "نص الرسالة" : "الصح كان إيه؟ (مثلًا: دي إيداع مش خصم)", key, body);
                    field.setSingleLine(false); field.setMinLines("body".equals(key) ? 3 : 2); field.setGravity(Gravity.TOP | Gravity.START);
                    field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                }
                errorLabel(body);
            }
            @Override String saveLabel() { return "فتح مسودة إيميل"; }
            @Override boolean save() {
                String reviewed = "قراءة غلط لرسالة بنك\nالتطبيق سجّلها: " + recorded + "\nالصح: " + text("correct").trim() + "\n\n" + text("body");
                try { startActivity(EmailSupport.draft(MainActivity.this, false, reviewed)); }
                catch (android.content.ActivityNotFoundException unavailable) { copySupportRequest(false, reviewed, "مفيش تطبيق إيميل؛ اتنسخت المسودة وعنوان الدعم"); }
                return true;
            }
            // Message text stays out of saved instance state even after redaction.
            @Override boolean restoreAfterRecreation() { return false; }
        });
    }

    /**
     * Owner decision m6 (0.4.0): messages from a known bank or payment app, naming an amount, that the app did not record
     * (ShadowStore.missedFromBank, or not understood). Text redacted; report one as a misreading or dismiss it.
     */
    private void missedBankMessages() {
        note("رسائل من بنك أو تطبيق دفع فيها مبلغ والتطبيق ما سجّلهاش. لو كانت عملية فعلًا، بلّغ عنها عشان نصلّح قراءتها؛ لو مش عملية، تجاهلها. الأرقام والأسماء محجوبة.");
        int shown = 0;
        for (ShadowStore.Ununderstood message : ShadowStore.ununderstood(db.getReadableDatabase())) {
            if (message.text == null || message.hash == null) continue;
            shown++;
            String redacted = Redaction.text(message.text);
            LinearLayout box = card();
            box.addView(label(message.source + " · " + Instant.ofEpochMilli(message.lastSeen).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_LABEL)
                    + (message.count > 1 ? " · وصلت " + message.count + " مرات" : ""), 12, MUTED, false));
            TextView text = label(redacted, 14, INK, false); text.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); box.addView(text);
            LinearLayout actions = new LinearLayout(this); actions.setPadding(0, dp(8), 0, 0);
            actions.addView(ui.button("بلّغ عنها", false, false, () -> reportMessageReading("ما اتسجلتش", "المرسل: " + message.source + "\n" + redacted)),
                    new LinearLayout.LayoutParams(0, -2, 1));
            actions.addView(ui.button("تجاهل", false, false, () -> { ShadowStore.dismissUnunderstood(db.getWritableDatabase(), message, System.currentTimeMillis()); render(); }),
                    new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(actions);
        }
        if (shown == 0) note("مفيش رسائل من بنوكك محتاجة مراجعة.");
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
            column.addView(label("بطاقة ائتمان".equals(source.kind) ? "رصيد ائتماني مسجل" : balance.estimated?"رصيد محسوب · تقديري":"آخر رصيد مسجل", 11, 0xccffffff, false));
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
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.setMarginStart(dp(10));
        bar.addView(label("الالتزامات", 24, INK, true), titleParams);
        bar.addView(primaryIconButton(R.drawable.ic_plus, "إضافة التزام", () -> openCommitmentEditor(null)),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
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
            // Task D2: dismissed for good under its recurring key; the card and the row hint never offer it again.
            TextView dismiss = label("مش التزام · ما تقترحوش تاني", 12, ACCENT, true);
            dismiss.setMinHeight(dp(UiTheme.TOUCH)); dismiss.setGravity(Gravity.CENTER_VERTICAL);
            dismiss.setOnClickListener(v -> { db.dismissRecurringSuggestion(item); refreshLedger(true); render(); });
            copy.addView(dismiss);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1); copyParams.setMarginStart(dp(10));
            suggestion.addView(copy, copyParams);
            TextView add = label("أضفه", 12, onPrimary(), true);
            add.setGravity(Gravity.CENTER); add.setPadding(dp(14), 0, dp(14), 0);
            add.setBackground(rounded(primaryFill(), 12));
            add.setOnClickListener(v -> commitmentFromTransaction(item));
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
        heading.addView(iconButton(R.drawable.ic_chevron_back,"الشهر السابق",() -> {commitmentMonth=commitmentMonth.minusMonths(1);render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView month=label(commitmentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy",new Locale("ar"))),16,INK,true);
        month.setGravity(Gravity.CENTER);heading.addView(month,new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(iconButton(R.drawable.ic_chevron_forward,"الشهر التالي",() -> {commitmentMonth=commitmentMonth.plusMonths(1);render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
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
                cell.addView(centered(label(due.isEmpty()?"":due.size()+" •",11,today?LIME:ACCENT,true)));
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
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("تفاصيل " + kindLabel(item.kind), 15, INK, true); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(iconButton(R.drawable.ic_edit, "تعديل", () -> openCommitmentEditor(item)),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        commitmentWrite(change,null);
    }
    private void commitmentWrite(CommitmentChange change,java.util.function.Consumer<Boolean> completed){
        Context app=getApplicationContext();
        CaptureQueue.submit(()->{
            final boolean changed;
            final long before=db.changes();
            try{changed=change.apply();}
            catch(RuntimeException failed){postUi(()->{if(completed!=null)completed.accept(false);else toast("تعذر اعتماد التسوية؛ راجع العملية والدورة وحاول تاني");});return;}
            if(changed||db.changes()!=before)try{SmsReceiver.notifyDataChanged(app);}catch(RuntimeException output){postUi(()->toast("التسوية محفوظة؛ تعذر تحديث التنبيهات"));}
            postUi(()->{if(completed!=null)completed.accept(changed);toast(changed?"اتحدثت التسوية بدون مصروف جديد":"ما اتسجلش سداد جديد؛ راجع الحالة الحالية");render();});
        });
    }

    private abstract class CommitmentWritePage extends DraftFormPage {
        private boolean pending;
        CommitmentWritePage(String route,String title,Bundle draft,Bundle original){super(route,title,draft,original);}
        final boolean submit(CommitmentChange change){
            if(pending)return false;
            pending=true;
            commitmentWrite(change,changed->{
                pending=false;
                if(changed)formPages.completeSave(this);
                else fail("لم تُحفظ التسوية؛ راجع العملية والدورة وحاول تاني",null);
            });
            return false;
        }
    }
    private void commitmentLinkDialog(ExpenseDb.Recurring item){
        Bundle draft=new Bundle();draft.putLong("plan",item.id);draft.putString("due",item.nextDue==null?LocalDate.now().toString():item.nextDue.toString());
        draft.putBoolean("searched",false);draft.putLong("transaction",-1);draft.putString("amount","");
        savePageScroll();formPages.push(new CommitmentLinkPage(draft,draft));
    }

    /** Pick the cycle, then one recorded payment, then review the allocation: one page, no new expense at any step. */
    private final class CommitmentLinkPage extends CommitmentWritePage {
        private CommitmentPayments.AllocationPreview preview;
        CommitmentLinkPage(Bundle draft,Bundle original){super("commitment_link","اربط دفع موجود · بدون مصروف إضافي",draft,original);}
        @Override void build(LinearLayout body,UiComponents components){
            ExpenseDb.Recurring item=db.recurringById(draft.getLong("plan"));preview=null;
            if(item==null){body.addView(components.label("الالتزام ده لم يعد موجودًا",UiTheme.BODY,CORAL));return;}
            body.addView(components.label(item.merchant+" · "+money(item.amountMinor,item.currency),UiTheme.BODY,INK));
            components.caption("١ · دورة الاستحقاق",body);
            EditText due=dateField("دورة الاستحقاق","due",body);
            due.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable s){}
                public void onTextChanged(CharSequence s,int a,int b,int c){if(draft.getBoolean("searched")){draft.putBoolean("searched",false);draft.putBoolean("show_all",false);draft.putLong("transaction",-1);formPages.rebuild();}}});
            body.addView(components.label("اختار دورة من الجدول؛ هنجيب العمليات المسجلة في الشهر المحيط بها.",UiTheme.CAPTION,MUTED));
            if(!draft.getBoolean("searched")){
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);
                body.addView(components.button("اختار العملية",false,false,()->{draft.putBoolean("searched",true);formPages.rebuild();}),lp);
                errorLabel(body);return;
            }
            LocalDate selected=LocalDate.parse(text("due"));
            // Tester, 24 Oct 2026: two months of purchases newest first hid the payment; the likely ones come first now.
            java.util.Map<Long,ExpenseDb.Transaction> byId=new java.util.HashMap<>();List<PaymentCandidates.Item> items=new ArrayList<>();
            for(ExpenseDb.Transaction tx:db.commitmentPaymentCandidates(selected)){byId.put(tx.id,tx);items.add(new PaymentCandidates.Item(tx.id,tx.merchant,tx.amountMinor,tx.currency,tx.when));}
            List<PaymentCandidates.Item> ranked=PaymentCandidates.rank(items,item.amountMinor,item.currency,item.merchant,selected);
            components.caption("٢ · اختار الدفعة اللي تخص "+item.merchant,body);
            if(ranked.isEmpty()){body.addView(components.label("مفيش دفعات مؤهلة في الفترة دي. راجع تاريخ الدورة أو فك رابط سابق من التسويات.",UiTheme.BODY,MUTED));errorLabel(body);return;}
            List<PaymentCandidates.Item> likely=new ArrayList<>(),rest=new ArrayList<>();
            for(PaymentCandidates.Item candidate:ranked)(PaymentCandidates.likely(candidate,item.amountMinor,item.currency,item.merchant)?likely:rest).add(candidate);
            boolean showAll=draft.getBoolean("show_all")||likely.isEmpty()||rest.stream().anyMatch(c->c.id==draft.getLong("transaction"));
            body.addView(components.label(likely.isEmpty()?"مفيش دفعة بنفس الاسم أو المبلغ؛ دي العمليات الأقرب لتاريخ الدورة.":
                    "الأقرب بالاسم والمبلغ "+money(item.amountMinor,item.currency)+":",UiTheme.CAPTION,MUTED));
            android.widget.RadioGroup group=new android.widget.RadioGroup(MainActivity.this);body.addView(group);
            List<PaymentCandidates.Item> shown=new ArrayList<>(likely);if(showAll)shown.addAll(rest);
            for(PaymentCandidates.Item candidate:shown){
                ExpenseDb.Transaction tx=byId.get(candidate.id);
                android.widget.RadioButton option=new android.widget.RadioButton(MainActivity.this);option.setId(View.generateViewId());option.setSaveEnabled(false);
                // Two lines, each piece isolated, so a Latin merchant and an Arabic currency never reorder each other.
                option.setText("\u2068"+UiKit.displayName(tx.merchant)+"\u2069\n\u2068"+money(tx.amountMinor,tx.currency)+"\u2069 · "+dayLabel(Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate()));
                option.setTypeface(REGULAR);option.setTextSize(UiTheme.BODY);option.setTextColor(INK);option.setMinHeight(dp(UiTheme.TOUCH));
                option.setPadding(dp(8),dp(8),dp(8),dp(8));
                option.setButtonTintList(ColorStateList.valueOf(ACCENT));option.setChecked(tx.id==draft.getLong("transaction"));
                option.setOnClickListener(v->{if(draft.getLong("transaction")==tx.id)return;draft.putLong("transaction",tx.id);draft.putString("amount","");formPages.rebuild();});
                group.addView(option);
            }
            if(!showAll&&!rest.isEmpty())
                body.addView(components.button("عرض باقي العمليات ("+rest.size()+")",false,false,()->{draft.putBoolean("show_all",true);formPages.rebuild();}));
            long chosen=draft.getLong("transaction");
            if(chosen>=0){
                try{preview=db.commitmentPaymentPreview(item.id,selected,chosen);}
                catch(IllegalArgumentException changed){body.addView(components.label("الدورة مش متاحة بنفس العملة أو العملية اتغيرت؛ راجع الاختيار",UiTheme.BODY,CORAL));}
                if(preview!=null){
                    components.caption("٣ · راجع ربط الدفعة",body);
                    body.addView(components.label(preview.transaction.merchant+" · "+money(preview.transaction.amountMinor,preview.transaction.currency)+"\nدورة "+preview.due+" · المطلوب "+money(preview.expected,preview.currency),UiTheme.BODY,INK));
                    if(text("amount").isEmpty())draft.putString("amount",minorText(preview.max));
                    amountField("المبلغ المخصص للدورة","amount",body);
                    body.addView(components.label("حد الربط "+money(preview.max,preview.currency)+". الربط يستخدم العملية الحالية ولا يضيف مصروفًا.",UiTheme.CAPTION,MUTED));
                    body.addView(components.label("لو الدفعة التلقائية غير مصنفة أو تحويلات، هتاخد تصنيف الالتزام. أي تصنيف عدّلته بنفسك محفوظ.",UiTheme.CAPTION,MUTED));
                }
            }
            errorLabel(body);
        }
        @Override String saveLabel(){return "اعتماد الربط";}
        @Override boolean save(){
            if(preview==null)return fail(draft.getBoolean("searched")?"اختار الدفعة اللي تخص الالتزام":"اختار الدورة واضغط «اختار العملية»",null);
            if(ExpenseParser.manualAmountMisread(text("amount")))return fail("راجع المبلغ","amount");
            long minor=ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if(minor<=0||minor>preview.max)return fail("اكتب مبلغًا موجبًا لحد المبلغ المتاح","amount");
            CommitmentPayments.AllocationPreview approved=preview;
            return submit(()->{db.linkCommitmentPayment(approved,minor);return true;});
        }
        // The preview is recomputed from the database on every build, never restored from a stale copy.
        @Override boolean restoreAfterRecreation(){return true;}
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
                        Bundle draft=new Bundle();draft.putString("due",plan.nextDue==null?LocalDate.now().toString():plan.nextDue.toString());
                        savePageScroll();formPages.push(new CommitmentWritePage("provider_confirmation",plan.merchant+" · تأكيد حالة فقط",draft,new Bundle(draft)){
                            @Override void build(LinearLayout body,UiComponents components){
                                body.addView(components.label("اختار دورة الاستحقاق اللي التأكيد ده بيخصها. التأكيد لا يضيف مصروفًا.",UiTheme.BODY,MUTED));
                                dateField("دورة الاستحقاق","due",body);errorLabel(body);
                            }
                            @Override String saveLabel(){return "اعتماد التأكيد";}
                            @Override boolean save(){
                                if(text("due").isEmpty())return fail("اختار دورة الاستحقاق",null);
                                LocalDate date=LocalDate.parse(text("due"));return submit(()->db.resolveCommitmentConfirmation(notice.id,plan.id,date,key));
                            }
                            // The pending notice is not part of the draft, so this step is not rebuilt after process death.
                            @Override boolean restoreAfterRecreation(){return false;}
                        });
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
        toolbar.addView(iconButton(R.drawable.ic_chevron_back, "رجوع", this::onBackPressed), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(existing == null ? "التزام جديد" : "تعديل الالتزام", 16, INK, true); title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        toolbar.addView(new View(this), new LinearLayout.LayoutParams(dp(48), dp(48)));
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

        if(existing!=null&&!db.commitmentCycles(existing.id).isEmpty())
            note("الدورات التي بدأت تحتفظ بمبلغها وعملتها؛ تعديل المبلغ يخص الدورات الجديدة. تسويات الدفعات محفوظة.");
        LinearLayout amountRow = new LinearLayout(this); amountRow.setGravity(Gravity.BOTTOM);
        page.addView(amountRow, new LinearLayout.LayoutParams(-1, -2));
        EditText amount = input(kind[0].equals("installment") ? "القسط الشهري" : "المبلغ", amountRow);
        amount.setTag("commitment_amount");
        TextView amountCaption = (TextView) ((LinearLayout) amount.getParent()).getChildAt(0);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(suggestion!=null && suggestion.amount>0) amount.setText(String.format(Locale.ROOT,"%.2f",suggestion.amount/100.0));
        else if (futureTerms != null) amount.setText(String.format(Locale.ROOT, "%.2f", futureTerms.amountMinor / 100.0));
        Spinner currency = new AppChoiceSpinner("العملة", CURRENCIES);
        currency.setBackground(outlined(SURFACE, BORDER, 12));
        // Grows with the font (200% clipped "EGP" at a fixed size); never narrower or shorter than a touch target.
        currency.setMinimumWidth(dp(96)); currency.setMinimumHeight(dp(UiTheme.TOUCH));
        LinearLayout.LayoutParams currencyParams = new LinearLayout.LayoutParams(-2, -2);
        currencyParams.setMarginStart(dp(8));
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
        total.setTag("commitment_total");
        total.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText paid = input("اتدفع منهم كام", counts);
        paid.setTag("commitment_paid");
        ((LinearLayout.LayoutParams) ((View) paid.getParent()).getLayoutParams()).setMarginStart(dp(8));
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
        exactDue.setOnClickListener(v -> {
            android.app.DatePickerDialog picker = new android.app.DatePickerDialog(this,(view,year,month,day) -> {
            chosenDue[0]=LocalDate.of(year,month+1,day);dueDay[0]=day;paintDays.run();
            exactDue.setText("موعد الدفعة القادمة: "+chosenDue[0].format(DATE_LABEL)+" · تغيير");
            },chosenDue[0].getYear(),chosenDue[0].getMonthValue()-1,chosenDue[0].getDayOfMonth());
            UiSheets.prepare(this,picker,uiTheme,REGULAR).show();
        });
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
            amountCaption.setText(amount.getHint());
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
        dailyDetailsContent.addView(label("مصروف اليوم = مشتريات ورسوم بس. سحب الكاش اتنقل من حسابك لكاش ومش مصروف. الأقساط والفواتير والتحويلات لناس ليها سطر لوحدها في التحليلات ومش بتاكل من الحد اليومي. الدخل والتحويلات الداخلية لا تزود الحد. المتبقي لا ينتقل لبكرة. كل عملة لها حد منفصل.",12,MUTED,false));
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
        Bundle draft = new Bundle(); fillDailyBudgetDraft(draft, CURRENCIES[0]);
        savePageScroll(); formPages.push(new DailyBudgetPage(draft, draft));
    }

    /** Reads what is stored for one currency, so switching currency shows that currency's own plan. */
    private void fillDailyBudgetDraft(Bundle draft, String code) {
        DailyAllowance.Plan plan = DailySummary.plan(this, code);
        long monthly = DailySummary.monthlySalary(this, code);
        draft.putString("currency", code);
        draft.putString("amount", plan == null ? "" : java.math.BigDecimal.valueOf(plan.limit, 2).toPlainString());
        draft.putString("starts", (plan == null ? LocalDate.now() : plan.starts).toString());
        draft.putString("salary", monthly == 0 ? "" : java.math.BigDecimal.valueOf(monthly, 2).toPlainString());
        draft.putInt("salary_day", salarySelection(getSharedPreferences(DailySummary.SETTINGS, MODE_PRIVATE).getInt("salary_" + code, 0)));
    }

    private final class DailyBudgetPage extends DraftFormPage {
        DailyBudgetPage(Bundle draft, Bundle original) { super("daily_budget", "ميزانية اليوم والراتب", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            body.addView(components.label("الميزانية اليومية ثابتة. الراتب الشهري للتخطيط فقط؛ لا يُسجّل كدخل تلقائيًا. كل عملة منفصلة.", UiTheme.BODY, MUTED));
            components.caption("العملة", body);
            components.choices(CURRENCIES, null, java.util.Arrays.asList(CURRENCIES).indexOf(text("currency")), i -> {
                if (CURRENCIES[i].equals(text("currency"))) return;
                fillDailyBudgetDraft(draft, CURRENCIES[i]); formPages.rebuild();
            }, body).setTag("daily_budget_currency");
            amountField("الحد اليومي (اختياري)", "amount", body);
            dateField("بداية الخطة", "starts", body);
            amountField("الراتب الشهري (اختياري)", "salary", body);
            String[] days = salaryDayLabels();
            LinearLayout salaryDay = new LinearLayout(MainActivity.this); salaryDay.setOrientation(LinearLayout.VERTICAL); body.addView(salaryDay);
            drawSalaryDay(salaryDay, days);
            body.addView(components.label("آخر يوم في الشهر يتبع 28/29/30/31. اليوم المحدد الذي لا يوجد في شهر قصير يُحسب آخر يوم فيه.", UiTheme.CAPTION, MUTED));
            if (DailySummary.plan(MainActivity.this, text("currency")) != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(16);
                Button remove = components.button("حذف الحد اليومي", false, true, () -> new ChoiceDialogBuilder().setTitle("حذف حد " + text("currency") + " اليومي؟")
                        .setMessage("الراتب وميعاده والعمليات محفوظون؛ هنلغي الحد اليومي فقط.")
                        .setPositiveButton("حذف", (confirm, w) -> { DailySummary.remove(MainActivity.this, text("currency")); formPages.finishThrough(route); })
                        .setNegativeButton("إلغاء", null).show());
                remove.setTextColor(uiTheme.danger); remove.setTag("daily_budget_remove"); body.addView(remove, lp);
            }
            errorLabel(body);
        }
        private void drawSalaryDay(LinearLayout container, String[] days) {
            container.removeAllViews();
            ui.choiceCard("ميعاد القبض المتوقع", days[draft.getInt("salary_day")], null, () -> chooseValue("ميعاد القبض المتوقع", days, days[draft.getInt("salary_day")], value -> {
                draft.putInt("salary_day", java.util.Arrays.asList(days).indexOf(value)); drawSalaryDay(container, days);
            }), container).setTag("daily_budget_salary_day");
        }
        @Override boolean save() {
            String daily = text("amount").trim(), monthly = text("salary").trim(), code = text("currency");
            if (ExpenseParser.manualAmountMisread(daily) || ExpenseParser.manualAmountMisread(monthly)) return fail("راجع المبلغ", ExpenseParser.manualAmountMisread(daily) ? "amount" : "salary");
            long limit, salaryValue;
            try { limit = optionalMinor(daily); } catch (IllegalArgumentException invalid) { return fail("راجع الحد اليومي", "amount"); }
            try { salaryValue = optionalMinor(monthly); } catch (IllegalArgumentException invalid) { return fail("راجع الراتب الشهري", "salary"); }
            if (text("starts").isEmpty()) return fail("اختار بداية الخطة", null);
            int day = selectedSalaryDay(draft.getInt("salary_day"));
            if (!daily.isEmpty()) DailySummary.save(MainActivity.this, new DailyAllowance.Plan(code, limit, LocalDate.parse(text("starts")), day));
            else DailySummary.remove(MainActivity.this, code);
            DailySummary.saveSalary(MainActivity.this, code, salaryValue, day);
            return true;
        }
    }

    private void commitmentFromTransaction(ExpenseDb.Transaction item) {
        LocalDate day = Instant.ofEpochMilli(item.when).atZone(ZoneId.systemDefault()).toLocalDate();
        int anchor = day.getDayOfMonth(); LocalDate next = day.plusMonths(1);
        while (next.isBefore(LocalDate.now())) { java.time.YearMonth m = java.time.YearMonth.from(next).plusMonths(1); next = m.atDay(Math.min(anchor, m.lengthOfMonth())); }
        Bundle draft = new Bundle(); draft.putLong("transaction", item.id); draft.putString("currency", item.currency);
        draft.putString("name", item.merchant); draft.putString("amount", minorText(item.amountMinor));
        draft.putString("type", "فواتير".equals(item.category) ? "bill" : "subscription"); draft.putString("due", next.toString());
        savePageScroll(); formPages.push(new CommitmentFromTransactionPage(draft, draft));
    }

    private final class CommitmentFromTransactionPage extends DraftFormPage {
        CommitmentFromTransactionPage(Bundle draft, Bundle original) { super("commitment_from_transaction", "حوّلها لالتزام شهري", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            amountField("المبلغ", "amount", body);
            body.addView(components.label(text("currency"), UiTheme.CAPTION, MUTED));
            textField("اسم الالتزام", "name", body);
            choiceField("النوع", "type", new String[]{"اشتراك", "فاتورة"}, new String[]{"subscription", "bill"}, null, body);
            dateField("موعد الدفعة القادمة", "due", body);
            body.addView(components.label("الموعد مقترح من آخر عملية؛ راجعه. إنشاء الالتزام لا يضيف مصروفًا أو يسجل سدادًا جديدًا.", UiTheme.CAPTION, MUTED));
            errorLabel(body);
        }
        @Override String saveLabel() { return "حفظ الالتزام"; }
        private long created = -1;
        @Override boolean save() {
            if (ExpenseParser.manualAmountMisread(text("amount"))) return fail("راجع المبلغ", "amount");
            long minor = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("amount")));
            if (minor <= 0) return fail("راجع المبلغ", "amount");
            String merchant = text("name").trim();
            if (merchant.isEmpty()) return fail("اكتب اسم الالتزام", "name");
            if (text("due").isEmpty() || LocalDate.parse(text("due")).isBefore(LocalDate.now())) return fail("اختار موعد قادم للدفعة", null);
            long id;
            try { id = db.putCommitmentFromTransaction(draft.getLong("transaction"), merchant, minor, text("currency"), LocalDate.parse(text("due")), text("type")); }
            catch (RuntimeException failed) { return fail("لم نحفظ الالتزام؛ راجع البيانات وحاول مرة أخرى", null); }
            if (id < 0) return fail("التزام بنفس الاسم والمبلغ موجود بالفعل، أو العملية غير متاحة", null);
            created = id; return true;
        }
        @Override void onSaved() {
            selectedCommitmentId = created; selectedTab = 11; ReminderReceiver.schedule(MainActivity.this); askNotificationPermission(); render();
        }
    }

    private void splitBillDialog() {
        Bundle draft = new Bundle(); draft.putString("merchant", ""); draft.putString("total", ""); draft.putString("people", "");
        draft.putString("currency", CURRENCIES[0]); draft.putString("category", CATEGORIES[0]); draft.putLong("source", -1);
        savePageScroll(); formPages.push(new SplitBillPage(draft, draft));
    }

    private final class SplitBillPage extends DraftFormPage {
        SplitBillPage(Bundle draft, Bundle original) { super("split_bill", "قسم فاتورة", draft, original); }
        @Override void build(LinearLayout body, UiComponents components) {
            amountField("إجمالي الفاتورة", "total", body);
            choiceField("العملة", "currency", CURRENCIES, CURRENCIES, null, body);
            textField("المكان أو الفاتورة", "merchant", body);
            EditText people = textField("عدد الأشخاص معاك", "people", body); people.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            int[] icons = new int[CATEGORIES.length];
            for (int i = 0; i < CATEGORIES.length; i++) icons[i] = categoryStyle(CATEGORIES[i])[2];
            choiceField("التصنيف", "category", CATEGORIES, CATEGORIES, icons, body);
            LinearLayout account = new LinearLayout(MainActivity.this); account.setOrientation(LinearLayout.VERTICAL); body.addView(account);
            drawSource(account);
            errorLabel(body);
        }
        private void drawSource(LinearLayout container) {
            container.removeAllViews(); List<ExpenseDb.PaymentSource> sources = db.selectableSources();
            ExpenseDb.PaymentSource picked = null; for (ExpenseDb.PaymentSource source : sources) if (source.id == draft.getLong("source")) picked = source;
            String[] names = sourceNames(sources);
            ui.choiceCard("وسيلة دفع حصتك", picked == null ? names[0] : db.sourceLabel(picked.id), picked == null ? null : sourceBadge(picked, 40), () ->
                    chooseValue("وسيلة دفع حصتك", names, null, value -> {
                        int at = java.util.Arrays.asList(names).indexOf(value);
                        draft.putLong("source", at <= 0 ? -1 : sources.get(at - 1).id); drawSource(container);
                    }), container).setTag("split_bill_source");
        }
        @Override String saveLabel() { return "سجل حصتي"; }
        /** Shown only after a refused duplicate; the page stays so nothing typed is lost. */
        @Override boolean save() {
            if (ExpenseParser.manualAmountMisread(text("total"))) return fail("راجع المبلغ", "total");
            long amount = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(text("total")));
            if (amount <= 0) return fail("راجع إجمالي الفاتورة", "total");
            int count;
            try { count = Integer.parseInt(ExpenseParser.normalizeDigits(text("people").trim())); } catch (NumberFormatException invalid) { count = 0; }
            if (count < 2) return fail("عدد الأشخاص لازم يكون 2 أو أكتر", "people");
            if (text("merchant").trim().isEmpty()) return fail("اكتب المكان أو الفاتورة", "merchant");
            long source = draft.getLong("source", -1);
            boolean added = db.addSplitShare(System.currentTimeMillis(), amount, count, text("currency"), text("merchant"), text("category"),
                    source < 0 ? null : source, "حصتي من فاتورة " + money(amount, text("currency")) + " مقسمة على " + count + " أشخاص");
            if (!added) {
                new ChoiceDialogBuilder().setTitle("الفاتورة دي مسجلة بالفعل")
                        .setMessage("إضافة حصتك هنا هتعمل مصروف مكرر فوق الدفع الأصلي. لم نحفظ أي مبلغ جديد ولم نغير العملية المسجلة. " +
                                "ربط الحصة بالدفع والمستحقات غير متاح حاليًا.")
                        .setPositiveButton("رجوع", null).show();
                return false;
            }
            return true;
        }
    }

    private void confirmInstallmentDialog(RepairRules.RepeatingTransfer pattern) {
        savePageScroll();
        formPages.push(new DraftFormPage("installment", "سجّله كقسط", installmentDraft(pattern), installmentDraft(pattern)) {
            @Override void build(LinearLayout body, UiComponents components) {
                body.addView(components.label(money(pattern.amountMinor, pattern.currency) + " كل شهر من " + db.sourceLabel(pattern.sourceId), UiTheme.BODY, INK));
                textField("اسم القسط (مثلًا: قسط الشقة)", "name", body);
                dateField("تاريخ آخر قسط", "last", body);
                EditText paid = textField("اتدفع كام قسط لحد دلوقتي", "paid", body); paid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                body.addView(components.label("لقينا " + pattern.ids.size() + " دفعة في رسايلك. لو فيه دفعات قبل كده من غير رسايل عدّل الرقم.", UiTheme.CAPTION, MUTED));
                errorLabel(body);
            }
            @Override boolean save() {
                String title = text("name").trim();
                if (title.isEmpty()) return fail("اكتب اسم القسط", "name");
                if (text("last").isEmpty()) return fail("اختار تاريخ آخر قسط", null);
                int paidSoFar;
                try { paidSoFar = Integer.parseInt(ExpenseParser.normalizeDigits(text("paid").trim())); } catch (NumberFormatException invalid) { return fail("اكتب عدد الأقساط المدفوعة", "paid"); }
                try { db.confirmRepeatingTransfer(pattern, title, LocalDate.parse(text("last")), paidSoFar, LocalDate.now()); }
                catch (RuntimeException failed) { return fail("لم نسجل القسط؛ راجع البيانات وحاول مرة أخرى", null); }
                toast("اتسجل " + title + " · دفعاته القديمة اتنقلت للأقساط"); return true;
            }
            // The detected pattern is not part of the draft, so this page is not rebuilt after process death.
            @Override boolean restoreAfterRecreation() { return false; }
        });
    }

    private static Bundle installmentDraft(RepairRules.RepeatingTransfer pattern) {
        Bundle draft = new Bundle(); draft.putString("name", ""); draft.putString("last", ""); draft.putString("paid", Integer.toString(pattern.ids.size()));
        return draft;
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
        if(animationsEnabled()){demo.setAlpha(0);demo.setTranslationY(dp(10));demo.animate().alpha(1).translationY(0).setDuration(240).start();}
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
        return ui.icon(res, description, action);
    }

    private ImageView primaryIconButton(int res, String description, Runnable action) {
        ImageView button = ui.icon(res, description, action);
        button.setImageTintList(ColorStateList.valueOf(LIME));
        button.setBackground(ui.interactive(BRAND, 0, UiTheme.BUTTON_RADIUS));
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
        chip.setBackground(ui.interactive(selected ? primaryFill() : SURFACE, BORDER, 99));
        chip.setFocusable(true); chip.setSelected(selected);
        chip.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(android.widget.RadioButton.class.getName());
                info.setCheckable(true); info.setChecked(host.isSelected());
            }
        });
        chip.setOnClickListener(v -> action.run());
        chip.setMinHeight(dp(UiTheme.TOUCH)); chip.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginEnd(dp(8));
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
        return "income".equals(kind) ? INCOME : "expense".equals(kind) ? INK : MUTED;
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
            case "nbe" -> R.drawable.brand_nbe;
            case "bdc" -> R.drawable.brand_bdc;
            case "cib" -> R.drawable.brand_cib;
            case "sharafdg" -> R.drawable.brand_sharafdg;
            case "centrepoint" -> R.drawable.brand_centrepoint;
            case "max" -> R.drawable.brand_max;
            case "lcwaikiki" -> R.drawable.brand_lcwaikiki;
            case "raya" -> R.drawable.brand_raya;
            case "aman" -> R.drawable.brand_aman;
            case "paymob" -> R.drawable.brand_paymob;
            case "mahalawy" -> R.drawable.brand_mahalawy;
            case "pizzaking" -> R.drawable.brand_pizzaking;
            case "butlers" -> R.drawable.brand_butlers;
            case "seoudi" -> R.drawable.brand_seoudi;
            case "chatgpt" -> R.drawable.brand_chatgpt;
            case "onedrive" -> R.drawable.brand_onedrive;
            case "truecaller" -> R.drawable.brand_truecaller;
            case "googleone" -> R.drawable.brand_googleone;
            case "costa" -> R.drawable.brand_costa;
            case "etoile" -> R.drawable.brand_etoile;
            case "zaher" -> R.drawable.brand_zaher;
            case "teds" -> R.drawable.brand_teds;
            case "ezaby" -> R.drawable.brand_ezaby;
            case "illy" -> R.drawable.brand_illy;
            case "tru" -> R.drawable.brand_tru;
            default -> 0;
        };
    }

    /** Prefer the merchant, then the account/provider behind any unrecognized merchant. */
    private View rowBadge(ExpenseDb.Transaction item, int size) {
        if(brandLogo(BrandCatalog.merchant(item.merchant))!=0)
            return merchantBadge(item.merchant,item.category,size);
        View merchantApp=applicationBadge(BrandCatalog.merchantPackage(item.merchant),item.merchant,size);
        if(merchantApp!=null)return merchantApp;
        Long account=item.paymentSourceId!=null?item.paymentSourceId:item.destinationSourceId;
        ExpenseDb.PaymentSource source=account==null?null:db.paymentSource(account);
        if(source!=null){View logo=sourceLogoBadge(source.name,size);if(logo!=null)return logo;}
        if(item.source!=null&&item.source.startsWith("إشعار: ")){
            View logo=sourceLogoBadge(item.source.substring("إشعار: ".length()).trim(),size);
            if(logo!=null)return logo;
        }
        if(source!=null){View badge=sourceBadge(source,size);badge.setContentDescription("مصدر العملية: "+source.name);return badge;}
        return merchantBadge(item.merchant, item.category, size);
    }

    private View merchantBadge(String name, String category, int size) {
        String id=BrandCatalog.merchant(name);
        int logo=brandLogo(id);
        if (logo != 0) return UiKit.logoBadge(this, logo, id, name, size, dark ? 0 : BORDER, dark, SURFACE);
        View appIcon=applicationBadge(BrandCatalog.merchantPackage(name),name,size);
        if(appIcon!=null)return appIcon;
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
        int hue = UiKit.monogramColor(name);
        TextView badge = label(UiKit.monogram(name), Math.max(12, Math.round(size * 0.42f)), UiKit.monogramInk(hue, INK, dark), true);
        badge.setPadding(0, 0, 0, 0);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(rounded(UiKit.blend(hue, SURFACE, dark ? 0.24f : 0.14f), size / 2));
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
                    new LinearLayout.LayoutParams(dp(48), dp(48)));
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
        // A hairline on top instead of a shadow; the bar sits flat on the page like the cards do.
        android.graphics.drawable.LayerDrawable navBackground = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(BORDER), new android.graphics.drawable.ColorDrawable(SURFACE)});
        navBackground.setLayerInset(1, 0, dp(1), 0, 0);
        nav.setBackground(navBackground);
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
            item.setSelected(active);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf((ACCENT & 0x00ffffff) | 0x24000000), null, rounded(SURFACE, 16)));
            // The active tab: a soft pill behind the icon, the icon in the accent and a bold caption; nothing else changes.
            ImageView icon = tintedIcon(icons[i], active ? ACCENT : MUTED);
            icon.setPadding(dp(16), dp(4), dp(16), dp(4));
            icon.setBackground(active ? rounded(ACCENT_PALE, 99) : null);
            TextView caption = label(labels[i], 11, active ? INK : MUTED, active); caption.setGravity(Gravity.CENTER);
            caption.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(56), dp(32));
            iconSize.bottomMargin = dp(4); item.addView(icon, iconSize); item.addView(caption);
            item.setContentDescription(labels[i]+(active?"، الصفحة الحالية":""));
            item.setMinimumHeight(dp(60));
            LinearLayout.LayoutParams destinationParams=new LinearLayout.LayoutParams(0,-2,1);
            destinationParams.setMargins(dp(3),0,dp(3),0);nav.addView(item,destinationParams);
            item.setOnClickListener(v -> {
                if (tab == 4) insightsSection="";
                transactionsReturnDialog="";transactionsPointsCurrency="";transactionJourney.clear();
                if (tab == 6) { transactionStatementList=false;transactionStatementIds=null;transactionBucketFilter = ""; transactionPeriod = null; transactionCurrencyFilter = ""; transactionsBackTab = 0; }
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
        row.addView(toggle); row.setOnClickListener(view -> toggle.toggle());
        row.setTag("setting:" + title); settingsGroup.addView(row);
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
        row.setMinimumHeight(dp(54)); row.setPadding(0,dp(8),0,dp(8));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1); textParams.setMarginStart(dp(12));
        row.addView(label(name, 14, destructive ? CORAL : INK, false), textParams);
        row.addView(tintedIcon(R.drawable.ic_chevron_forward, MUTED), new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setTag("setting:" + name); settingsGroup.addView(row);
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
    /**
     * Inline text that opens data («عرض الكل», «اربطه») is underlined like a link. Tappable rows and cards already read
     * as tappable (chevron, avatar, card shape), so they get a press ripple clipped to their shape instead of an underline.
     */
    private void markDetailActions(View view) {
        if (view.hasOnClickListeners()) {
            if (view instanceof TextView) {
                if (!(view instanceof EditText) && view.getBackground()==null)
                    ((TextView)view).setPaintFlags(((TextView)view).getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            } else if (view instanceof android.view.ViewGroup && view.getForeground()==null
                    && (view.getBackground()==null || view.getBackground() instanceof GradientDrawable)) {
                view.setForeground(new RippleDrawable(ColorStateList.valueOf((ACCENT & 0x00ffffff) | 0x24000000), null, view.getBackground()));
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for (int i=0;i<group.getChildCount();i++) markDetailActions(group.getChildAt(i));
        }
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
        monthlyMetric(totals,"مشتريات ورسوم",current.of(MoneyFlow.Bucket.LIVING),CORAL,()->openMonthlyTransactions(MoneyFlow.Bucket.LIVING,"",""));
        monthlyMetric(totals,"التزامات مدفوعة",current.of(MoneyFlow.Bucket.COMMITMENT),dark?0xffa0aafc:INDIGO,()->openMonthlyTransactions(MoneyFlow.Bucket.COMMITMENT,"",""));
        monthlyMetric(totals,"تحويلات لناس غير محددة الغرض",current.of(MoneyFlow.Bucket.OPEN_TRANSFER),dark?0xfff2ac65:ORANGE,()->openMonthlyTransactions(MoneyFlow.Bucket.OPEN_TRANSFER,"",""));
        monthlyMetric(totals,"وارد مسجل",current.of(MoneyFlow.Bucket.INFLOW),INCOME,()->openMonthlyTransactions(MoneyFlow.Bucket.INFLOW,"",""));
        monthlyMetric(totals,"تحويل بين حساباتك · منفصل",current.of(MoneyFlow.Bucket.INTERNAL),MUTED,()->openMonthlyTransactions(MoneyFlow.Bucket.INTERNAL,"",""));
        monthlyMetric(totals,"سحب نقدي · من حسابك لكاش",current.of(MoneyFlow.Bucket.CASH_WITHDRAWAL),MUTED,()->openMonthlyTransactions(MoneyFlow.Bucket.CASH_WITHDRAWAL,"",""));
        monthlyMetric(totals,"صافي الصرف والدخل المسجل",current.of(MoneyFlow.Bucket.INFLOW)-current.totalOut(),INK,null);
        note("العملات منفصلة. الصافي مش رصيد بنك أو وفر مؤكد. الصرف اليومي يشمل الشراء والرسوم؛ سحب الكاش اتنقل من حسابك لكاش ومش مصروف. الاسترداد المرتبط يُخصم من العملية الأصلية والوارد زي باقي التحليلات.");

        section("مشترياتك ورسومك راحت فين؟",null,null);LinearLayout categories=listCard(page);long living=current.of(MoneyFlow.Bucket.LIVING);
        for(Map.Entry<String,Long> entry:current.livingCategories()) {
            String name=entry.getKey();monthlyMetric(categories,name,entry.getValue(),categoryStyle(name)[1],()->openMonthlyTransactions(MoneyFlow.Bucket.LIVING,name,""));
            TextView share=pill(Math.round(100.0*entry.getValue()/Math.max(1,living))+"%",BORDER,MUTED);share.setTextDirection(View.TEXT_DIRECTION_LTR);
            LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(-2,-2);shareParams.setMarginStart(dp(8));
            ((LinearLayout)categories.getChildAt(categories.getChildCount()-1)).addView(share,shareParams);
        }
        if(living==0)categories.addView(label("مفيش مشتريات ورسوم مسجلة بالعملة دي",13,MUTED,false));
        section("سلوك الصرف",null,null);LinearLayout habits=card();
        if("EGP".equals(monthlyCurrency))monthlyMetric(habits,"مدفوعات حتى 100 جنيه · "+data.smallCount+" عمليات",data.smallTotal,INK,null);
        habits.addView(label("متوسط العملية اليومية: "+money(current.livingAverage(),monthlyCurrency)+" · عددها "+current.countOf(MoneyFlow.Bucket.LIVING),13,MUTED,false));
        habits.addView(label("أكتر جهات الصرف اليومي",12,MUTED,false));
        for(Map.Entry<String,Long> entry:data.topMerchants())monthlyMetric(habits,entry.getKey(),entry.getValue(),ACCENT,null);
        if(current.uncategorized>0)note(current.uncategorized+" عمليات غير مصنفة بقيمة "+money(current.uncategorizedAmount,monthlyCurrency)+"؛ التصنيفات ونِسبها تعتمد على التسجيل المتاح.");

        section("مقارنة بالشهر السابق",null,null);LinearLayout comparison=card();
        monthlyMetric(comparison,"مشتريات ورسوم · "+monthlyName(monthlyMonth.minusMonths(1)),data.previous.of(MoneyFlow.Bucket.LIVING),MUTED,null);
        monthlyMetric(comparison,"مشتريات ورسوم · "+monthlyName(monthlyMonth),living,INK,null);
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
        hero.addView(label("مشتريات ورسوم · " + insightsPeriodName(), 13, 0xffb9d3c8, false));
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
        totalCopy.addView(label("مشتريات ورسوم + أقساط + تحويلات لناس · " + s.outCount() + " عملية", 11, MUTED, false));
        total.addView(totalCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView totalAmount = label(money(s.totalOut(), "EGP"), 14, MUTED, true);
        totalAmount.setTextDirection(View.TEXT_DIRECTION_LTR);
        total.addView(totalAmount);
        buckets.addView(total);

        }
        if(livingReport && !overview) {
            section("تكلفة المعيشة المسجلة",null,null);
            LinearLayout cost=card();cost.addView(label(money(s.of(MoneyFlow.Bucket.LIVING)+s.of(MoneyFlow.Bucket.COMMITMENT),"EGP"),28,INK,true));
            cost.addView(label("مشتريات ورسوم "+money(s.of(MoneyFlow.Bucket.LIVING),"EGP")+" · التزامات "+money(s.of(MoneyFlow.Bucket.COMMITMENT),"EGP"),13,MUTED,false));
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
            others.append(others.length() == 0 ? "" : "\n").append(entry.getKey()).append(": مشتريات ورسوم ")
                    .append(money(other.of(MoneyFlow.Bucket.LIVING), entry.getKey())).append(" · التزامات ")
                    .append(money(other.of(MoneyFlow.Bucket.COMMITMENT), entry.getKey())).append(" · دخلت ")
                    .append(money(other.of(MoneyFlow.Bucket.INFLOW), entry.getKey()));
        }
        if (others.length() > 0) note("عملات تانية (مش مجموعة مع الجنيه):\n" + others);
        note("نفس التعريفات في كل الصفحات: «مشتريات ورسوم» شراء ورسوم. الأقساط والفواتير والاشتراكات سطر لوحدها. " +
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
            legend.addView(label(bucket == MoneyFlow.Bucket.OPEN_TRANSFER ? "تحويلات لناس" : bucket == MoneyFlow.Bucket.COMMITMENT ? "أقساط والتزامات" : "مشتريات ورسوم", 11, MUTED, false));
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
            transactionPeriod = new LocalDate[]{day, day.plusDays(1)}; transactionStatementList=false; transactionStatementIds=null;
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

    private void purposeDialog(List<Long> ids) {
        String[] purposes = withOwnTransfer(PURPOSES);
        new ChoiceDialogBuilder().setTitle("غرض التحويلات دي").setItems(purposes, (d, which) -> {
            if (which == 0) { reconcileTransfers(ids); return; }
            int changed = db.updateCategories(ids, purposes[which]);
            toast("اتحدد غرض " + changed + " تحويل"); render();
        }).setNegativeButton("إلغاء", null).show();
    }

    private static String[] withOwnTransfer(String[] choices) {
        String[] result = new String[choices.length + 1]; result[0] = OWN_TRANSFER_CHOICE;
        System.arraycopy(choices, 0, result, 1, choices.length);
        return result;
    }

    /** One reconcile dialog after another: each transfer chooses its own accounts and receipt. */
    private void reconcileTransfers(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            ExpenseDb.Transaction tx = db.transactionById(ids.get(i));
            if (tx == null || !"expense".equals(tx.kind)) continue;
            List<Long> rest = new ArrayList<>(ids.subList(i + 1, ids.size()));
            reconcileTransferDialog(tx, rest.isEmpty() ? null : () -> reconcileTransfers(rest));
            return;
        }
    }

    private boolean openTransferRow(long id) {
        ExpenseDb.Transaction tx = db.transactionById(id);
        return tx != null && MoneyFlow.bucket(tx.kind, tx.category) == MoneyFlow.Bucket.OPEN_TRANSFER;
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
                        else confirmDelete(budget.name, () -> { db.deleteBudgetPlan(budget.id); render(); });
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
            else if("بطاقة".equals(source.kind)&&!appSource(source)) copy.addView(label("كارت غير مرتبط · حركته وحده",11,ORANGE,false));
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
        openStatement(bucketFilter, review, null);
    }

    private void openStatement(String bucketFilter, boolean review, long[] ids) {
        resetTransactionFilters();
        transactionStatementIds = ids;
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
        transactionsReturnDialog="";transactionsPointsCurrency="";transactionStatementList=false;transactionStatementIds=null;
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
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.setMarginStart(dp(10));
        TextView accountTitle = label(("بطاقة".equals(account.kind)?"حركة الكارت · ":"حركة الحساب · ") + flowPeriodName(), UiTheme.TITLE, INK, true);
        if (Build.VERSION.SDK_INT >= 28) accountTitle.setAccessibilityHeading(true);
        bar.addView(accountTitle, titleParams);
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
        LinearLayout accountActions = new LinearLayout(this); accountActions.setGravity(Gravity.CENTER_VERTICAL);
        accountActions.addView(ui.button("تحديث الرصيد", false, false, () -> manualBalanceDialog(selectedAccount)), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(0, -2, 1); optionsParams.setMarginStart(dp(8));
        accountActions.addView(ui.button("إدارة الحساب", false, false, () -> sourceOptions(selectedAccount)), optionsParams);
        LinearLayout.LayoutParams accountActionsParams = new LinearLayout.LayoutParams(-1, -2); accountActionsParams.topMargin = dp(10);
        page.addView(accountActions, accountActionsParams);
        if(totals.withdrawn>0) note("سحب نقدي: "+money(totals.withdrawn,"EGP")+" · اتنقل من الحساب لكاش ومش مصروف.");
        List<ExpenseDb.PaymentSource> linked=db.linkedCards(account.id);
        if(!linked.isEmpty()) {
            StringBuilder names=new StringBuilder();for(ExpenseDb.PaymentSource card:linked) names.append(names.length()==0?"":"، ").append("•").append(card.last4);
            note("حركة الحساب تشمل الكروت المرتبطة: "+names);
        } else if("بطاقة".equals(account.kind)&&!appSource(account)) {
            note("الكارت ده مش مرتبط بحساب. الوارد والتحويلات الظاهرة تخص رقم الكارت فقط؛ صفر مسجل مش معناه إن الحساب البنكي مفيهوش حركة.");
            Button link=ui.button("اربط الكارت بحسابه",true,false,()->linkSourceDialog(selectedAccount));
            LinearLayout.LayoutParams linkParams=new LinearLayout.LayoutParams(-1,-2);linkParams.topMargin=dp(8);page.addView(link,linkParams);
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
        // Wraps instead of truncating so three tiles still show whole amounts at large font sizes.
        TextView amount = label(money(value, "EGP"), 13, color, true);
        amount.setTextDirection(View.TEXT_DIRECTION_LTR);
        box.addView(centered(amount));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) params.setMarginStart(dp(8));
        row.addView(box, params);
    }








    /**
     * The brand opening (res/raw/app_intro.mp4, the same 5-second intro as the promo video) in a full-screen dialog,
     * so later render() calls cannot remove it. First launch plays it whole; later cold starts stop once the logo and
     * name are on screen (~3 s). A tap skips it; Settings can limit it to the first launch or turn it off.
     */
    private android.app.Dialog openingDialog;
    /** Off when the system's remove-animations setting zeroes the animator scale; the opening and its fades step aside. */
    private boolean animationsEnabled() {
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
    }
    /** A message that arrived while the opening was playing (e.g. the repair summary) waits until it closes. */
    private Runnable afterOpening;

    private void playOpening(boolean forced) {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String mode = prefs.getString("opening_mode", "first");
        boolean first = !prefs.getBoolean("opening_seen", false);
        if (!forced && ("off".equals(mode) || "first".equals(mode) && !first || !animationsEnabled())) return;
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
            stage.animate().alpha(0f).setDuration(animationsEnabled() ? 260 : 0).withEndAction(() -> {
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
        // Task D1: a phone that already ran this repair version still moves an older Telda card source to a payment app
        // (cheap and idempotent, every launch).
        if (!manual) CaptureQueue.submit(() -> { try { db.migrateAppSources(); } catch (RuntimeException ignored) { } });
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
                if (report.sourcesInferred > 0) text.append("• ").append(report.sourcesInferred)
                        .append(" عملية قديمة اتعرف حسابها من رسالتها، فاتربطت تحويلاتها بين حساباتك.\n");
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


}
