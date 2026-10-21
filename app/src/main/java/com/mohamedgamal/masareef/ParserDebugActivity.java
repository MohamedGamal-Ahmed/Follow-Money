package com.mohamedgamal.masareef;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Hidden parser diagnostics (shadow mode): for each stored message or notification, the legacy decision beside the
 * new pipeline's decision, fields, reasons and score, and the "messages I did not understand" list. Read-only: it
 * never writes the ledger, and nothing it shows leaves the device. Opened from advanced settings once a hidden
 * gesture enabled it; no adb is needed. The re-scan view runs ShadowRescan over old inbox SMS (no operation is
 * recorded) and shows its diff report, which can be saved to a file the user picks on this device, without message text.
 */
public final class ParserDebugActivity extends Activity {
    static final String ENABLED_KEY = "parser_debug_enabled";
    private static final int LIMIT = 300;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private UiTheme theme;
    private UiComponents ui;
    private AppLock appLock;
    private Typeface font;
    private LinearLayout page;
    private String view = "all";
    private volatile Snapshot loaded;
    private static final int EXPORT_REQUEST = 7301;

    /** One re-scan at a time per process; it outlives a screen rotation. */
    private static final class RescanState {
        volatile boolean running;
        volatile int scanned, total;
        volatile String error;
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    }
    private static final RescanState RESCAN = new RescanState();
    private static volatile java.lang.ref.WeakReference<ParserDebugActivity> visible = new java.lang.ref.WeakReference<>(null);

    /** Everything one screen shows, read off the main thread. */
    private static final class Snapshot {
        List<ShadowStore.Stored> live;
        List<ShadowStore.Ununderstood> ununderstood;
        long[] counts;
        /** Only when the re-scan view is open. */
        ShadowReport.Summary rescan;
    }

    @Override public void onCreate(Bundle state) {
        boolean dark = SmsImportActivity.mainPreferences(this).getBoolean("dark_mode", false);
        if (dark) setTheme(R.style.AppTheme_Dark);
        super.onCreate(state);
        appLock = new AppLock(this, this::show);
        theme = new UiTheme(this, dark);
        try { font = new Typeface.Builder(getAssets(), "fonts/ReadexPro.ttf").build(); }
        catch (RuntimeException e) { font = Typeface.DEFAULT; }
        ui = new UiComponents(this, theme, font);
        getWindow().setStatusBarColor(theme.background); getWindow().setNavigationBarColor(theme.background);
        if (state != null) view = state.getString("view", "all");
        show();
        load();
    }

    @Override protected void onStart() { super.onStart(); appLock.onStart(); visible = new java.lang.ref.WeakReference<>(this); }
    @Override protected void onStop() { if (visible.get() == this) visible.clear(); appLock.onStop(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); appLock.guard(); }
    @Override protected void onDestroy() { appLock.dispose(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("view", view); }

    private void load() {
        final android.content.Context app = getApplicationContext();
        final boolean withRescan = view.equals("rescan");
        new Thread(() -> {
            Snapshot s = new Snapshot();
            try (ExpenseDb db = new ExpenseDb(app)) {
                android.database.sqlite.SQLiteDatabase sql = db.getReadableDatabase();
                s.live = ShadowStore.recent(sql, "live", LIMIT);
                s.ununderstood = ShadowStore.ununderstood(sql, LIMIT);
                s.counts = ShadowStore.counts(sql, "live");
                if (withRescan) s.rescan = rescanSummary(app, sql);
            } catch (RuntimeException failed) {
                s = null;
            }
            Snapshot done = s;
            runOnUiThread(() -> { loaded = done; if (!isFinishing() && !isDestroyed()) show(); });
        }, "parser-debug-load").start();
    }

    private void show() {
        if (appLock != null && appLock.guard()) return;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); root.setBackgroundColor(theme.background);
        root.addView(ui.header("تشخيص المحلل", this::finish, null));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(ui.dp(UiTheme.PAGE_PADDING), ui.dp(8), ui.dp(UiTheme.PAGE_PADDING), ui.dp(24));
        scroll.addView(page);
        setContentView(root); SystemUi.apply(this, root);

        Snapshot s = loaded;
        line("وضع الظل: المحلل القديم هو صاحب القرار. لا شيء هنا يغيّر التسجيل، ولا شيء يغادر الجهاز.", UiTheme.CAPTION, theme.muted);
        line("السياسة " + ParsingPolicy.POLICY_VERSION + " · " + ParsingPolicy.EXTRACTOR_VERSION + " · " + ParsingPolicy.CORPUS_CONTRACT, UiTheme.CAPTION, theme.muted);
        if (s == null) { line("جارٍ التحميل…", UiTheme.BODY, theme.ink); return; }
        summary(s);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        tab(tabs, "الكل", "all"); tab(tabs, "الاختلافات", "diff"); tab(tabs, "لم أفهمها (" + s.counts[1] + ")", "ununderstood");
        page.addView(tabs);
        LinearLayout tabs2 = new LinearLayout(this); tabs2.setOrientation(LinearLayout.HORIZONTAL);
        tab(tabs2, "إعادة الفحص (بدون تسجيل)", "rescan");
        page.addView(tabs2);
        if (view.equals("rescan")) { rescanView(s); return; }
        if (view.equals("ununderstood")) { for (ShadowStore.Ununderstood u : s.ununderstood) ununderstoodCard(u); if (s.ununderstood.isEmpty()) line("لا توجد رسائل.", UiTheme.BODY, theme.muted); return; }
        int shown = 0;
        for (ShadowStore.Stored row : s.live) {
            if (view.equals("diff") && row.diff().agrees()) continue;
            observationCard(row); shown++;
        }
        if (shown == 0) line("لا توجد ملاحظات بعد.", UiTheme.BODY, theme.muted);
    }

    private void summary(Snapshot s) {
        java.util.Map<ShadowDiff.Outcome, Integer> by = new java.util.EnumMap<>(ShadowDiff.Outcome.class);
        int agree = 0;
        for (ShadowStore.Stored row : s.live) {
            ShadowDiff.Result r = row.diff();
            by.merge(r.outcome, 1, Integer::sum);
            if (r.agrees()) agree++;
        }
        LinearLayout card = card();
        cardLine(card, "رسائل مسجّلة في الظل: " + s.counts[0] + " (المعروض آخر " + s.live.size() + ")", true);
        cardLine(card, "اتفاق: " + agree + " · اختلاف: " + (s.live.size() - agree), false);
        StringBuilder detail = new StringBuilder();
        for (java.util.Map.Entry<ShadowDiff.Outcome, Integer> e : by.entrySet()) detail.append(e.getKey()).append(' ').append(e.getValue()).append("  ");
        if (detail.length() > 0) cardLine(card, detail.toString().trim(), false);
        cardLine(card, "قواعد التعلّم المحلي: " + s.counts[2], false);
    }

    private void observationCard(ShadowStore.Stored r) {
        LinearLayout card = card();
        ShadowDiff.Result d = r.diff();
        cardLine(card, (d.agrees() ? "✓ " : "✗ ") + d + " · " + when(r.receivedAt) + " · " + r.source + " · " + r.channel, true);
        cardLine(card, "القديم: " + r.legacyDecision + " (" + r.legacyReason + ")" + money(r.legacy), false);
        String score = r.score < 0 ? "–" : String.valueOf(r.score);
        cardLine(card, "الجديد: " + r.decision + " · الدرجة " + score + " · " + r.type + (r.movementKind == null ? "" : " · " + r.movementKind) +
                (r.decision != null && !r.decision.equals(r.scoredDecision) ? " (قبل التعلّم " + r.scoredDecision + ")" : "") + money(r.values), false);
        StringBuilder fields = new StringBuilder();
        for (String key : new String[]{"merchant", "counterparty", "balance_minor", "fee_minor", "card_last4", "account_last4", "datetime", "accounting_effect", "learned_category"})
            if (r.values.containsKey(key)) fields.append(key).append('=').append(r.values.get(key)).append("  ");
        if (fields.length() > 0) cardLine(card, fields.toString().trim(), false);
        cardLine(card, "الأسباب: " + r.reasons, false);
        if (!"NEW".equals(r.dedupe)) cardLine(card, "التكرار: " + r.dedupe + (r.dedupeReason == null ? "" : "/" + r.dedupeReason), false);
        if (r.unreadable) cardLine(card, "القيم المشفّرة لم تعد قابلة للقراءة على هذا الجهاز.", false);
    }

    private void ununderstoodCard(ShadowStore.Ununderstood u) {
        LinearLayout card = card();
        cardLine(card, when(u.lastSeen) + " · " + u.source + " · " + u.channel + " · ×" + u.count, true);
        cardLine(card, u.text == null ? "(النص غير قابل للقراءة على هذا الجهاز)" : u.text, false);
        cardLine(card, "الأسباب: " + u.reasons, false);
    }

    private static String money(java.util.Map<String, String> v) {
        if (!v.containsKey("amount_minor")) return "";
        long minor;
        try { minor = Long.parseLong(v.get("amount_minor")); } catch (NumberFormatException e) { return " · " + v.get("amount_minor"); }
        return String.format(Locale.ROOT, " · %d.%02d %s %s%s", minor / 100, Math.abs(minor % 100), v.getOrDefault("currency", ""),
                v.getOrDefault("direction", ""), v.containsKey("merchant") ? " · " + v.get("merchant") : "");
    }

    private static String when(long ms) { return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(WHEN); }

    private void tab(LinearLayout parent, String title, String key) {
        android.widget.Button b = ui.button(title, key.equals(view), false, () -> {
            boolean reload = key.equals("rescan") != view.equals("rescan");
            view = key; show();
            if (reload) load();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMarginEnd(ui.dp(6)); lp.topMargin = ui.dp(10);
        parent.addView(b, lp);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        card.setBackground(ui.shape(theme.surface, theme.border, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = ui.dp(10);
        page.addView(card, lp);
        return card;
    }

    private void cardLine(LinearLayout card, String text, boolean strong) {
        TextView t = ui.label(text, strong ? UiTheme.BODY : UiTheme.CAPTION, strong ? theme.ink : theme.muted);
        if (strong) t.setTypeface(font, Typeface.BOLD);
        t.setTextIsSelectable(true); t.setPadding(0, ui.dp(2), 0, ui.dp(2));
        card.addView(t);
    }

    private void line(String text, int size, int color) {
        TextView t = ui.label(text, size, color); t.setPadding(0, ui.dp(6), 0, ui.dp(6));
        page.addView(t);
    }

    // ---- Shadow re-scan ------------------------------------------------------------------------------------------

    /** The stored re-scan rows as report rows, with the counts the re-scan kept for messages it did not store. */
    private static ShadowReport.Summary rescanSummary(android.content.Context app, android.database.sqlite.SQLiteDatabase sql) {
        android.content.SharedPreferences meta = app.getSharedPreferences(ShadowRescan.PREFS, MODE_PRIVATE);
        if (!meta.contains("finished_at")) return null;
        java.util.List<ShadowReport.Row> rows = new java.util.ArrayList<>();
        for (ShadowStore.Stored r : ShadowStore.recent(sql, "rescan", ParsingPolicy.RESCAN_MAX_ROWS))
            rows.add(new ShadowReport.Row(r.source, r.receivedAt, r.legacyDecision, r.legacyReason, r.amount(r.legacy), r.legacy.get("currency"),
                    r.legacy.get("direction"), r.decision, r.amount(r.values), r.values.get("currency"), r.values.get("direction"), r.type,
                    r.movementKind, r.reasons, r.score));
        return ShadowReport.build(rows, meta.getInt("scanned", 0), meta.getLong("skipped_unstored", 0));
    }

    private void rescanView(Snapshot s) {
        line("يشغّل المسار الجديد على رسائل SMS القديمة بنفس مسار «اختيار فترة الرسائل القديمة». لا تُسجَّل أي عملية، ولا يغادر شيء الجهاز.", UiTheme.CAPTION, theme.muted);
        if (RESCAN.running) {
            line("جارٍ الفحص: " + RESCAN.scanned + " / " + RESCAN.total, UiTheme.BODY, theme.ink);
            page.addView(ui.button("إيقاف الفحص", false, true, () -> RESCAN.cancelled.set(true)));
            return;
        }
        if (RESCAN.error != null) line(RESCAN.error, UiTheme.BODY, theme.danger);
        LinearLayout periods = new LinearLayout(this); periods.setOrientation(LinearLayout.HORIZONTAL);
        period(periods, "آخر 30 يوم", 29); period(periods, "آخر 90 يوم", 89);
        page.addView(periods);
        LinearLayout periods2 = new LinearLayout(this); periods2.setOrientation(LinearLayout.HORIZONTAL);
        period(periods2, "آخر سنة", 364); period(periods2, "كل الرسائل", -1);
        page.addView(periods2);
        android.content.SharedPreferences meta = getSharedPreferences(ShadowRescan.PREFS, MODE_PRIVATE);
        ShadowReport.Summary r = s.rescan;
        if (r == null) { line(meta.contains("finished_at") ? "جارٍ تحميل التقرير…" : "لم يُجرَ فحص بعد.", UiTheme.BODY, theme.muted); return; }
        LinearLayout card = card();
        cardLine(card, "آخر فحص: " + when(meta.getLong("finished_at", 0)) + " · من " + (meta.getLong("since", 0) == 0 ? "البداية" : when(meta.getLong("since", 0))) +
                (meta.getBoolean("cancelled", false) ? " · أُوقف قبل النهاية" : "") + (meta.getBoolean("truncated", false) ? " · تجاوز حد الصفوف المحفوظة" : ""), true);
        cardLine(card, "الرسائل المفحوصة: " + r.scanned + " · المقارنة: " + r.compared(), false);
        cardLine(card, "اتفاق: " + r.agreed() + " · اختلاف: " + r.disagree, true);
        StringBuilder outcomes = new StringBuilder();
        for (java.util.Map.Entry<ShadowDiff.Outcome, Integer> e : r.byOutcome.entrySet()) outcomes.append(e.getKey()).append(' ').append(e.getValue()).append("  ");
        cardLine(card, outcomes.toString().trim(), false);
        page.addView(ui.button("حفظ التقرير في ملف (بدون نص الرسائل)", false, false, this::exportReport));
        table("حسب المصدر", r.bySource);
        table("حسب النوع", r.byType);
        line("الاختلافات (" + r.differences.size() + ")", UiTheme.BODY, theme.ink);
        int shown = 0;
        for (ShadowReport.Row d : r.differences) {
            if (shown++ >= LIMIT) { line("يظهر أول " + LIMIT + " اختلاف؛ الملف المحفوظ يحتوي الكل.", UiTheme.CAPTION, theme.muted); break; }
            LinearLayout dc = card();
            cardLine(dc, d.diff() + " · " + d.source + " · " + when(d.receivedAt), true);
            cardLine(dc, "القديم: " + d.legacyDecision + " (" + d.legacyReason + ") · " + ShadowReport.money(d.legacyAmount, d.legacyCurrency) + " · " + d.legacyDirection, false);
            cardLine(dc, "الجديد: " + d.newDecision + " · " + ShadowReport.money(d.newAmount, d.newCurrency) + " · " + d.newDirection +
                    " · الدرجة " + (d.score < 0 ? "–" : d.score) + " · " + d.bucket(), false);
            cardLine(dc, "الأسباب: " + d.reasons, false);
        }
    }

    private void table(String title, java.util.Map<String, int[]> rows) {
        LinearLayout card = card();
        cardLine(card, title + " (اتفاق / اختلاف)", true);
        for (java.util.Map.Entry<String, int[]> e : rows.entrySet()) cardLine(card, e.getKey() + ": " + e.getValue()[0] + " / " + e.getValue()[1], false);
    }

    private void period(LinearLayout parent, String title, int days) {
        android.widget.Button b = ui.button(title, false, false, () -> startRescan(days));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMarginEnd(ui.dp(6)); lp.topMargin = ui.dp(8);
        parent.addView(b, lp);
    }

    private void startRescan(int days) {
        if (RESCAN.running) return;
        if (checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            RESCAN.error = "إذن قراءة الرسائل غير مفعّل؛ فعّله من شاشة استيراد الرسائل أولًا."; show(); return;
        }
        final long since = days < 0 ? 0 : java.time.LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        final android.content.Context app = getApplicationContext();
        RESCAN.running = true; RESCAN.error = null; RESCAN.scanned = 0; RESCAN.total = 0; RESCAN.cancelled.set(false);
        show();
        new Thread(() -> {
            try {
                ShadowRescan.run(app, since, RESCAN.cancelled, (scanned, total) -> {
                    RESCAN.scanned = scanned; RESCAN.total = total;
                    ParserDebugActivity a = visible.get();
                    if (a != null) a.runOnUiThread(() -> { if (a.view.equals("rescan") && !a.isFinishing()) a.show(); });
                });
            } catch (RuntimeException failed) {
                RESCAN.error = "تعذر الفحص (" + failed.getClass().getSimpleName() + "). لم يُسجَّل شيء.";
            } finally {
                RESCAN.running = false;
                ParserDebugActivity a = visible.get();
                if (a != null) a.runOnUiThread(() -> { if (!a.isFinishing()) { a.show(); a.load(); } });
            }
        }, "shadow-rescan").start();
    }

    private void exportReport() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(android.content.Intent.EXTRA_TITLE, "follow-money-shadow-rescan-" + java.time.LocalDate.now() + ".md");
        appLock.beginOwnResult(EXPORT_REQUEST);
        try { startActivityForResult(intent, EXPORT_REQUEST); }
        catch (RuntimeException unavailable) { appLock.endOwnResult(EXPORT_REQUEST); RESCAN.error = "لا يوجد تطبيق ملفات لحفظ التقرير."; show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        appLock.onActivityResult(requestCode, resultCode);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Snapshot s = loaded;
        if (s == null || s.rescan == null) return;
        String text = ShadowReport.text(s.rescan, "Follow Money shadow re-scan", ZoneId.systemDefault());
        try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "wt")) {
            if (out == null) throw new java.io.IOException("no stream");
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            android.widget.Toast.makeText(this, "تم حفظ التقرير على الجهاز", android.widget.Toast.LENGTH_LONG).show();
        } catch (java.io.IOException | RuntimeException failed) {
            android.widget.Toast.makeText(this, "تعذر حفظ التقرير", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}