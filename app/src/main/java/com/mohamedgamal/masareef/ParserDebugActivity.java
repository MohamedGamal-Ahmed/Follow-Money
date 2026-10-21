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
 * gesture enabled it; no adb is needed.
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

    /** Everything one screen shows, read off the main thread. */
    private static final class Snapshot {
        List<ShadowStore.Stored> live;
        List<ShadowStore.Ununderstood> ununderstood;
        long[] counts;
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

    @Override protected void onStart() { super.onStart(); appLock.onStart(); }
    @Override protected void onStop() { appLock.onStop(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); appLock.guard(); }
    @Override protected void onDestroy() { appLock.dispose(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("view", view); }

    private void load() {
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Snapshot s = new Snapshot();
            try (ExpenseDb db = new ExpenseDb(app)) {
                android.database.sqlite.SQLiteDatabase sql = db.getReadableDatabase();
                s.live = ShadowStore.recent(sql, "live", LIMIT);
                s.ununderstood = ShadowStore.ununderstood(sql, LIMIT);
                s.counts = ShadowStore.counts(sql, "live");
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
        android.widget.Button b = ui.button(title, key.equals(view), false, () -> { view = key; show(); });
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
}
