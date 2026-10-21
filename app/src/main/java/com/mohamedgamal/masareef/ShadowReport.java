package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The shadow re-scan's diff report: totals, agreement by source and by type, and every difference with source, date,
 * legacy decision/amount/direction and new decision/amount/direction/reasons. Pure. The text export carries no
 * message text, merchant or counterparty, and nothing here sends it anywhere.
 */
final class ShadowReport {
    /** One compared message; amounts in minor units, null when absent. */
    static final class Row {
        final String source, legacyDecision, legacyReason, legacyCurrency, legacyDirection, newDecision, newCurrency, newDirection, type, kind, reasons;
        final long receivedAt;
        final Long legacyAmount, newAmount;
        final int score;
        Row(String source, long receivedAt, String legacyDecision, String legacyReason, Long legacyAmount, String legacyCurrency, String legacyDirection,
            String newDecision, Long newAmount, String newCurrency, String newDirection, String type, String kind, String reasons, int score) {
            this.source = source; this.receivedAt = receivedAt; this.legacyDecision = legacyDecision; this.legacyReason = legacyReason;
            this.legacyAmount = legacyAmount; this.legacyCurrency = legacyCurrency; this.legacyDirection = legacyDirection;
            this.newDecision = newDecision; this.newAmount = newAmount; this.newCurrency = newCurrency; this.newDirection = newDirection;
            this.type = type; this.kind = kind; this.reasons = reasons; this.score = score;
        }

        ShadowDiff.Result diff() {
            return ShadowDiff.compare(ShadowDiff.legacyRecorded(legacyDecision), legacyAmount, legacyDirection, newDecision, newAmount, newDirection);
        }

        /** The report's type bucket: the movement kind when there is one, else the message type. */
        String bucket() { return kind != null ? kind : type == null ? "unknown" : type.toLowerCase(Locale.ROOT); }
    }

    static final class Summary {
        /** Messages read from the inbox, and those both sides skipped without a stored row. */
        long scanned, skippedUnstored;
        int agree, disagree;
        final Map<ShadowDiff.Outcome, Integer> byOutcome = new java.util.EnumMap<>(ShadowDiff.Outcome.class);
        /** key → {agree, disagree} */
        final Map<String, int[]> bySource = new TreeMap<>(), byType = new TreeMap<>();
        final List<Row> differences = new ArrayList<>();

        long compared() { return agree + disagree + skippedUnstored; }
        long agreed() { return agree + skippedUnstored; }
    }

    /** rows: the stored rows; skippedUnstored: messages both sides skipped that were counted but not stored. */
    static Summary build(List<Row> rows, long scanned, long skippedUnstored) {
        Summary s = new Summary();
        s.scanned = scanned; s.skippedUnstored = skippedUnstored;
        if (skippedUnstored > 0) s.byOutcome.put(ShadowDiff.Outcome.BOTH_SKIP, (int) skippedUnstored);
        for (Row row : rows) {
            ShadowDiff.Result r = row.diff();
            s.byOutcome.merge(r.outcome, 1, Integer::sum);
            int slot = r.agrees() ? 0 : 1;
            s.bySource.computeIfAbsent(String.valueOf(row.source), k -> new int[2])[slot]++;
            s.byType.computeIfAbsent(row.bucket(), k -> new int[2])[slot]++;
            if (r.agrees()) s.agree++;
            else { s.disagree++; s.differences.add(row); }
        }
        s.differences.sort((a, b) -> Long.compare(b.receivedAt, a.receivedAt));
        return s;
    }

    static String money(Long minor, String currency) {
        if (minor == null) return "\\N";
        return String.format(Locale.ROOT, "%s%d.%02d%s", minor < 0 ? "-" : "", Math.abs(minor) / 100, Math.abs(minor % 100),
                currency == null || currency.isEmpty() ? "" : " " + currency);
    }

    private static String v(String s) { return s == null ? "\\N" : s.replace("|", "/").replace("\n", " "); }

    /** Plain-text (Markdown) export; no message text, merchant or counterparty. */
    static String text(Summary s, String title, ZoneId zone) {
        DateTimeFormatter when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n");
        out.append("Policy ").append(ParsingPolicy.POLICY_VERSION).append(" / ").append(ParsingPolicy.EXTRACTOR_VERSION).append(" / ")
                .append(ParsingPolicy.CORPUS_CONTRACT).append(". Legacy decided; nothing was recorded. No message text, merchant or counterparty in this file.\n\n");
        out.append("| Measure | Count |\n| --- | ---: |\n");
        out.append("| Messages scanned | ").append(s.scanned).append(" |\n");
        out.append("| Compared | ").append(s.compared()).append(" |\n");
        out.append("| Agree | ").append(s.agreed()).append(" |\n");
        out.append("| Disagree | ").append(s.disagree).append(" |\n");
        for (Map.Entry<ShadowDiff.Outcome, Integer> e : s.byOutcome.entrySet()) out.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        out.append("\n## By source (stored rows)\n\n| Source | Agree | Disagree |\n| --- | ---: | ---: |\n");
        for (Map.Entry<String, int[]> e : s.bySource.entrySet()) out.append("| ").append(v(e.getKey())).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        out.append("\n## By type (stored rows)\n\n| Type | Agree | Disagree |\n| --- | ---: | ---: |\n");
        for (Map.Entry<String, int[]> e : s.byType.entrySet()) out.append("| ").append(v(e.getKey())).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        out.append("\n## Differences (").append(s.differences.size()).append(")\n\n");
        out.append("| Source | Date | Outcome | Legacy decision | Legacy amount | Legacy direction | New decision | New amount | New direction | Score | Type | Reasons |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- |\n");
        for (Row r : s.differences)
            out.append("| ").append(v(r.source)).append(" | ").append(Instant.ofEpochMilli(r.receivedAt).atZone(zone).format(when)).append(" | ")
                    .append(r.diff()).append(" | ").append(v(r.legacyDecision)).append(" (").append(v(r.legacyReason)).append(") | ")
                    .append(money(r.legacyAmount, r.legacyCurrency)).append(" | ").append(v(r.legacyDirection)).append(" | ")
                    .append(v(r.newDecision)).append(" | ").append(money(r.newAmount, r.newCurrency)).append(" | ").append(v(r.newDirection)).append(" | ")
                    .append(r.score < 0 ? "–" : String.valueOf(r.score)).append(" | ").append(v(r.bucket())).append(" | ").append(v(r.reasons)).append(" |\n");
        return out.toString();
    }

    private ShadowReport() {}
}
