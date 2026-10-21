package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Shadow comparison over the approved corpus: legacy decision, new decision and the corpus expectation per row.
 * Legacy is replayed with the same pure functions and order as ExpenseDb.addAutomaticCapture (without the database:
 * duplicate/exclusion/commitment state is out of scope). Expectations are read, never changed.
 */
public final class ShadowComparison {
    static final class Legacy {
        final String decision, outcome, currency, direction, merchant;
        final long amount;
        Legacy(String decision, String outcome, long amount, String currency, String direction, String merchant) {
            this.decision = decision; this.outcome = outcome; this.amount = amount; this.currency = currency; this.direction = direction; this.merchant = merchant;
        }
    }

    /** The body the legacy listener builds: title plus bigText (or text); group summaries are dropped. */
    static String legacyBody(ParserCorpusFixture.Row row) {
        if ("sms".equals(row.get("channel"))) return row.get("body");
        if ((Integer.parseInt(row.get("notification_flags")) & 0x200) != 0) return null;
        String title = trim(row.get("android.title")), text = trim(row.get("android.bigText"));
        if (text.isEmpty()) text = trim(row.get("android.text"));
        if (title.isEmpty()) return text.isEmpty() ? null : text;
        return text.isEmpty() || title.equals(text) ? title : title + " " + text;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }

    static Legacy legacy(ParserCorpusFixture.Row row) {
        String body = legacyBody(row), label = row.get("source");
        if (body == null) return new Legacy("IGNORE", "MISSING_BODY", -1, null, null, null);
        if (ExpenseParser.isVerificationMessage(body)) return new Legacy("IGNORE", "VERIFICATION", -1, null, null, null);
        if (ExpenseParser.isFutureOnly(body)) return new Legacy("IGNORE", "FUTURE_ONLY", -1, null, null, null);
        if (ExpenseParser.isUnsuccessful(body) || CaptureNotice.parse(label, body) != null)
            return new Legacy("IGNORE", CaptureNotice.parse(label, body) != null ? "NOTICE" : "UNSUCCESSFUL", -1, null, null, null);
        ExpenseParser.Transfer transfer = ExpenseParser.parseTransfer(body);
        ExpenseParser.Parsed parsed = ExpenseParser.parse(label, body);
        String direction = "out";
        if (parsed == null) { parsed = ExpenseParser.parseIncome(label, body); direction = "in"; }
        if (transfer != null) return new Legacy("AUTO", "INSERTED", transfer.amountMinor, "EGP", transfer.incoming ? "in" : "out", transfer.counterpart);
        if (parsed == null) return new Legacy("IGNORE", ExpenseParser.paidService(body) != null ? "SERVICE_NEEDS_COMMITMENT" : "NO_LEDGER_CANDIDATE", -1, null, null, null);
        return new Legacy("AUTO", "INSERTED", parsed.amountMinor, parsed.currency, direction, parsed.merchant);
    }

    /** Standalone expectation: paired-context rows are judged on their isolated decision. */
    static String expectedDecision(ParserCorpusFixture.Row row) {
        return row.get("expected_standalone_decision") != null ? row.get("expected_standalone_decision") : row.get("expected_decision");
    }

    static boolean expectedGateOpen(ParserCorpusFixture.Row row) {
        return "transaction".equals(row.get("expected_type")) && !row.get("required_reasons").contains("MULTIPLE_EVENTS");
    }

    static String cell(String value) { return value == null ? "" : value.replace("|", "\\|"); }

    /** Phase 2: is this a transaction message that may proceed (gate), and is the new type right? */
    static List<String> classification(List<ParserCorpusFixture.Row> rows) {
        int both = 0, newOnly = 0, legacyOnly = 0, neither = 0, typeRight = 0;
        List<String> table = new ArrayList<>(), misses = new ArrayList<>();
        java.util.Map<String, Integer> legacyMisses = new java.util.TreeMap<>();
        for (ParserCorpusFixture.Row row : rows) {
            Legacy old = legacy(row);
            MessageClassifier.Result now = MessageClassifier.classify(MessageNormalizer.normalize(MessageNormalizerTest.raw(row)));
            // Expected AUTO/REVIEW must proceed toward a transaction; IGNORE must stop; UNUNDERSTOOD must not be booked
            // (legacy) and must not be discarded (new: UNUNDERSTOOD now, or EXTRACT for extraction to reject).
            String expected = expectedDecision(row);
            boolean typeOk = now.corpusType().equals(row.get("expected_type"));
            boolean multipleOk = row.get("required_reasons").contains("MULTIPLE_EVENTS") == (now.decision == MessageClassifier.Decision.UNUNDERSTOOD);
            boolean legacyRight = expected.equals("UNUNDERSTOOD") ? "IGNORE".equals(old.decision) : "AUTO".equals(old.decision) == !expected.equals("IGNORE");
            boolean newRight = typeOk && multipleOk && (expected.equals("IGNORE") ? now.decision == MessageClassifier.Decision.IGNORE :
                    now.decision != MessageClassifier.Decision.IGNORE);
            if (typeOk) typeRight++;
            if (!legacyRight) legacyMisses.merge("expected " + row.get("expected_type") + " / " + expectedDecision(row) + "; legacy " + old.decision + " (" + old.outcome + ")", 1, Integer::sum);
            if (legacyRight && newRight) both++; else if (newRight) newOnly++; else if (legacyRight) legacyOnly++; else neither++;
            table.add("| " + row.get("id") + " | " + row.get("expected_type") + " / " + expectedDecision(row) + " | " + old.decision + " (" + old.outcome + ") | " +
                    now.corpusType() + " / " + now.decision + " | " + (legacyRight ? "✓" : "✗") + " | " + (newRight ? "✓" : "✗") + " |");
            if (!newRight) misses.add("| " + row.get("id") + " | " + row.get("expected_type") + " / " + expectedDecision(row) + " | " + now.corpusType() + " / " + now.decision +
                    " | " + cell(String.join(",", now.reasons)) + " |");
        }
        List<String> out = new ArrayList<>();
        out.add("Rows: " + rows.size() + ". Phase-2 correctness: expected AUTO/REVIEW must proceed toward a transaction (legacy inserts; new EXTRACT); " +
                "expected IGNORE must stop; expected UNUNDERSTOOD must not be booked by legacy and must not be discarded by the new stage " +
                "(UNUNDERSTOOD now, or EXTRACT so extraction can reject it). The new stage must also give the expected type. " +
                "Final AUTO/REVIEW/UNUNDERSTOOD decisions are compared in the extraction report.");
        out.add("");
        out.add("| Outcome | Rows |");
        out.add("| --- | ---: |");
        out.add("| Both correct | " + both + " |");
        out.add("| **New correct, legacy wrong** | " + newOnly + " |");
        out.add("| **Legacy correct, new wrong** | " + legacyOnly + " |");
        out.add("| Both wrong | " + neither + " |");
        out.add(String.format(Locale.ROOT, "| New type exactly as expected | %d / %d |", typeRight, rows.size()));
        out.add("");
        out.add("### Where legacy is wrong, by legacy outcome");
        out.add("");
        out.add("| Expected; legacy outcome | Rows |");
        out.add("| --- | ---: |");
        for (var entry : legacyMisses.entrySet()) out.add("| " + entry.getKey() + " | " + entry.getValue() + " |");
        out.add("");
        out.add("### Rows the new stage gets wrong (" + misses.size() + ")");
        out.add("");
        out.add("| Row | Expected type / decision | New type / stage decision | New reasons |");
        out.add("| --- | --- | --- | --- |");
        out.addAll(misses);
        out.add("");
        out.add("### Every corpus row");
        out.add("");
        out.add("| Row | Expected type / decision | Legacy decision (outcome) | New type / stage decision | Legacy | New |");
        out.add("| --- | --- | --- | --- | :-: | :-: |");
        out.addAll(table);
        return out;
    }

    public static void main(String[] args) throws Exception {
        String phase = args.length == 0 ? "classification" : args[0];
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        List<String> body = phase.equals("classification") ? classification(rows) : null;
        Objects.requireNonNull(body, "Unknown phase " + phase);
        List<String> page = new ArrayList<>();
        page.add("# Shadow comparison: " + phase);
        page.add("");
        page.add("Generated by `tools/ShadowComparison.java " + phase + "` from the approved corpus (" + ParsingPolicy.CORPUS_CONTRACT +
                ", policy " + ParsingPolicy.POLICY_VERSION + "). Expectations are read, never changed. Paired-context rows are judged on " +
                "their standalone decision. Legacy is replayed with the pure ExpenseParser/CaptureNotice functions in the order of " +
                "`ExpenseDb.addAutomaticCapture`, on the body the notification listener builds (title + bigText/text; group summaries " +
                "dropped). Database state (duplicates, exclusions, commitments) is outside the replay. Legacy can only insert (AUTO) or " +
                "create nothing (IGNORE); it has no REVIEW or UNUNDERSTOOD.");
        page.add("");
        page.addAll(body);
        Path report = Path.of("tools/fixtures/shadow-comparison-" + phase + ".md");
        Files.write(report, page, StandardCharsets.UTF_8);
        for (String line : body.subList(0, 9)) System.out.println(line);
        System.out.println("Full table: " + report);
    }
}
