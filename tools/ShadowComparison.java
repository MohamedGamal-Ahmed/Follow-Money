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

    static TransactionExtractor.Extraction extract(ParserCorpusFixture.Row row) {
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(MessageNormalizerTest.raw(row));
        return TransactionExtractor.extract(normalized, MessageClassifier.classify(normalized), Long.parseLong(row.get("received_at_ms")));
    }

    static String str(Object value) { return value == null ? null : String.valueOf(value); }

    /** Secondary fields: {label, expected column, compare only when expected is non-null?}. */
    static final String[][] SECONDARY = {
        {"balance", "expected_balance_minor", "all"}, {"balance currency", "expected_balance_currency", "all"},
        {"fee", "expected_fee_minor", "v2"}, {"credit limit", "expected_credit_limit_minor", "v2"},
        {"original amount", "expected_original_amount_minor", "v2"}, {"original currency", "expected_original_currency", "v2"},
        {"card last4", "expected_card_last4", "all"}, {"account last4", "expected_account_last4", "v2"},
        {"counterparty", "expected_counterparty_token", "nonnull"}, {"bank datetime", "expected_datetime", "all"},
        {"time precision", "expected_time_precision", "all"}, {"time ambiguity", "expected_time_ambiguity", "v2"},
        {"year origin", "expected_year_origin", "v2"}, {"expiry date", "expected_expiry_date", "v2"},
        {"accounting effect (expense_offset)", "expected_accounting_effect", "offset"},
    };

    static String secondary(TransactionExtractor.Extraction x, String column) {
        switch (column) {
            case "expected_balance_minor": return str(x.balanceMinor);
            case "expected_balance_currency": return x.balanceCurrency;
            case "expected_fee_minor": return str(x.feeMinor);
            case "expected_credit_limit_minor": return str(x.creditLimitMinor);
            case "expected_original_amount_minor": return str(x.originalMinor);
            case "expected_original_currency": return x.originalCurrency;
            case "expected_card_last4": return x.cardLast4;
            case "expected_account_last4": return x.accountLast4;
            case "expected_counterparty_token": return x.counterparty;
            case "expected_datetime": return x.datetime;
            case "expected_time_precision": return x.timePrecision;
            case "expected_time_ambiguity": return x.timeAmbiguity;
            case "expected_year_origin": return x.yearOrigin;
            case "expected_expiry_date": return x.expiryDate;
            default: return x.accountingEffect;
        }
    }

    /** Phase 3: exact decision plus principal fields; secondary fields new-vs-expected. */
    static List<String> extraction(List<ParserCorpusFixture.Row> rows) {
        int both = 0, newOnly = 0, legacyOnly = 0, neither = 0, newDecision = 0, legacyDecision = 0, legacyStructural = 0;
        String[] core = {"amount", "currency", "direction", "merchant"};
        int[] coreCompared = new int[4], coreNew = new int[4], coreLegacy = new int[4];
        int[][] secondary = new int[SECONDARY.length][2];
        List<String> table = new ArrayList<>(), misses = new ArrayList<>();
        for (ParserCorpusFixture.Row row : rows) {
            Legacy old = legacy(row);
            TransactionExtractor.Extraction now = extract(row);
            String expected = expectedDecision(row);
            boolean transaction = "transaction".equals(row.get("expected_type"));
            if (expected.equals("REVIEW") || expected.equals("UNUNDERSTOOD")) legacyStructural++;
            String[] want = {row.get("expected_amount_minor"), row.get("expected_currency"), row.get("expected_direction"), row.get("expected_merchant_key")};
            String[] got = {str(now.amountMinor), now.currency, now.direction, now.merchantKey};
            String[] had = {"AUTO".equals(old.decision) ? str(old.amount) : null, "AUTO".equals(old.decision) ? old.currency : null,
                    "AUTO".equals(old.decision) ? old.direction : null, "AUTO".equals(old.decision) ? MerchantNormalizer.key(old.merchant) : null};
            boolean newFields = true, legacyFields = true;
            List<String> diffs = new ArrayList<>();
            if (transaction) for (int k = 0; k < 4; k++) {
                coreCompared[k]++;
                if (Objects.equals(got[k], want[k])) coreNew[k]++; else { newFields = false; diffs.add(core[k] + " " + got[k] + "≠" + want[k]); }
                if (Objects.equals(had[k], want[k])) coreLegacy[k]++; else legacyFields = false;
            }
            // Paired-context rows list context-level reasons; a standalone result is not asked for them.
            if (transaction && !expected.equals("UNUNDERSTOOD") && row.get("expected_standalone_decision") == null) {
                for (String reason : row.get("required_reasons").split("\\|"))
                    if (!now.reasons.contains(reason)) diffs.add("missing reason " + reason);
            }
            for (int s = 0; s < SECONDARY.length; s++) {
                String column = SECONDARY[s][1], mode = SECONDARY[s][2], value = row.get(column);
                if (!transaction) continue;
                if (mode.equals("v2") && "legacy-v1".equals(row.get("source_evidence_id"))) continue;
                if (mode.equals("nonnull") && value == null) continue;
                if (mode.equals("offset") && !"expense_offset".equals(value)) continue;
                secondary[s][0]++;
                if (Objects.equals(secondary(now, column), value)) secondary[s][1]++;
                else diffs.add(SECONDARY[s][0] + " " + secondary(now, column) + "≠" + value);
            }
            boolean newRight = now.decision.equals(expected) && newFields;
            boolean legacyRight = old.decision.equals(expected) && legacyFields;
            if (now.decision.equals(expected)) newDecision++;
            if (old.decision.equals(expected)) legacyDecision++;
            if (legacyRight && newRight) both++; else if (newRight) newOnly++; else if (legacyRight) legacyOnly++; else neither++;
            table.add("| " + row.get("id") + " | " + expected + " | " + old.decision + " | " + now.decision + " | " +
                    (legacyRight ? "✓" : "✗") + " | " + (newRight ? "✓" : "✗") + " | " + cell(String.join("; ", diffs)) + " |");
            if (!newRight || !diffs.isEmpty())
                misses.add("| " + row.get("id") + " | " + expected + " | " + now.decision + " | " + cell(String.join("; ", diffs)) + " |");
        }
        List<String> out = new ArrayList<>();
        out.add("Rows: " + rows.size() + ". A row is correct when the decision equals the expected (standalone) decision and, for " +
                "transaction rows, amount, currency, direction and merchant key all equal the expectation (null included). Legacy can " +
                "only output AUTO or IGNORE, so it cannot be right on the " + legacyStructural + " rows expecting REVIEW or UNUNDERSTOOD.");
        out.add("");
        out.add("| Outcome | Rows |");
        out.add("| --- | ---: |");
        out.add("| Both correct | " + both + " |");
        out.add("| **New correct, legacy wrong** | " + newOnly + " |");
        out.add("| **Legacy correct, new wrong** | " + legacyOnly + " |");
        out.add("| Both wrong | " + neither + " |");
        out.add("| Decision only: new / legacy equal to expected | " + newDecision + " / " + legacyDecision + " |");
        out.add("");
        out.add("### Principal fields on transaction rows (" + coreCompared[0] + " rows)");
        out.add("");
        out.add("| Field | New correct | Legacy correct |");
        out.add("| --- | ---: | ---: |");
        for (int k = 0; k < 4; k++) out.add("| " + core[k] + " | " + coreNew[k] + " | " + coreLegacy[k] + " |");
        out.add("");
        out.add("### Secondary fields, new vs expected (transaction rows; v2-only columns skip retained v1 rows)");
        out.add("");
        out.add("| Field | Compared | New correct |");
        out.add("| --- | ---: | ---: |");
        for (int s = 0; s < SECONDARY.length; s++) out.add("| " + SECONDARY[s][0] + " | " + secondary[s][0] + " | " + secondary[s][1] + " |");
        out.add("");
        out.add("### Rows where the new stages differ from the expectation (" + misses.size() + ")");
        out.add("");
        out.add("| Row | Expected decision | New decision | Differences (new≠expected) |");
        out.add("| --- | --- | --- | --- |");
        out.addAll(misses);
        out.add("");
        out.add("### Every corpus row");
        out.add("");
        out.add("| Row | Expected | Legacy | New | Legacy | New | New differences |");
        out.add("| --- | --- | --- | --- | :-: | :-: | --- |");
        out.addAll(table);
        return out;
    }

    static final String[] HELD_OUT_SETS = {"tools/fixtures/held-out-blind.tsv", "tools/fixtures/held-out-blind-2.tsv", "tools/fixtures/held-out-blind-3.tsv"};

    static ConfidenceScorer.Confidence confidence(MessageNormalizer.Raw raw, long receivedAt, TransactionExtractor.Extraction[] phase3) {
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(raw);
        MessageClassifier.Result classified = MessageClassifier.classify(normalized);
        phase3[0] = TransactionExtractor.extract(normalized, classified, receivedAt);
        return ConfidenceScorer.score(classified, phase3[0]);
    }

    static String bucket(ConfidenceScorer.Confidence c) {
        if (c.components.isEmpty()) return "not scored (" + c.decision + ")";
        return c.score >= 100 ? "100" : c.score >= ParsingPolicy.AUTO_THRESHOLD ? "80-99" : c.score >= ParsingPolicy.REVIEW_THRESHOLD ? "50-79" : "0-49";
    }

    static void distribution(List<String> out, String title, String[] labels, List<java.util.Map<String, Integer>> counts) {
        out.add("| " + title + " | " + String.join(" | ", labels) + " |");
        out.add("| --- |" + " ---: |".repeat(labels.length));
        java.util.Set<String> keys = new java.util.TreeSet<>();
        for (var map : counts) keys.addAll(map.keySet());
        for (String key : keys) {
            StringBuilder line = new StringBuilder("| " + key + " |");
            for (var map : counts) line.append(" ").append(map.getOrDefault(key, 0)).append(" |");
            out.add(line.toString());
        }
    }

    /** Phase 4: final scored decision vs phase 3 provisional decision, legacy and expectation; corpus and held-out. */
    static List<String> confidenceReport(List<ParserCorpusFixture.Row> rows) throws Exception {
        int both = 0, newOnly = 0, legacyOnly = 0, neither = 0;
        java.util.Map<String, Integer> expected = new java.util.TreeMap<>(), legacy = new java.util.TreeMap<>(), p3 = new java.util.TreeMap<>(),
                p4 = new java.util.TreeMap<>(), buckets = new java.util.TreeMap<>();
        List<String> table = new ArrayList<>(), changes = new ArrayList<>(), misses = new ArrayList<>();
        for (ParserCorpusFixture.Row row : rows) {
            Legacy old = legacy(row);
            TransactionExtractor.Extraction[] x = new TransactionExtractor.Extraction[1];
            ConfidenceScorer.Confidence c = confidence(MessageNormalizerTest.raw(row), Long.parseLong(row.get("received_at_ms")), x);
            String want = expectedDecision(row);
            expected.merge(want, 1, Integer::sum); legacy.merge(old.decision, 1, Integer::sum);
            p3.merge(x[0].decision, 1, Integer::sum); p4.merge(c.decision, 1, Integer::sum); buckets.merge(bucket(c), 1, Integer::sum);
            boolean transaction = "transaction".equals(row.get("expected_type"));
            boolean fields = !transaction || (Objects.equals(str(x[0].amountMinor), row.get("expected_amount_minor")) &&
                    Objects.equals(x[0].currency, row.get("expected_currency")) && Objects.equals(x[0].direction, row.get("expected_direction")) &&
                    Objects.equals(x[0].merchantKey, row.get("expected_merchant_key")));
            boolean legacyFields = !transaction || ("AUTO".equals(old.decision) && Objects.equals(str(old.amount), row.get("expected_amount_minor")) &&
                    Objects.equals(old.currency, row.get("expected_currency")) && Objects.equals(old.direction, row.get("expected_direction")) &&
                    Objects.equals(MerchantNormalizer.key(old.merchant), row.get("expected_merchant_key")));
            boolean newRight = c.decision.equals(want) && fields, legacyRight = old.decision.equals(want) && (want.equals("IGNORE") || legacyFields);
            if (legacyRight && newRight) both++; else if (newRight) newOnly++; else if (legacyRight) legacyOnly++; else neither++;
            if (!c.decision.equals(x[0].decision))
                changes.add("| " + row.get("id") + " | " + x[0].decision + " | " + c.decision + " | " + c.score + " | " + cell(String.valueOf(c.components)) + " |");
            if (!newRight) misses.add("| " + row.get("id") + " | " + want + " | " + c.decision + " | " + c.score + " |");
            table.add("| " + row.get("id") + " | " + want + " | " + old.decision + " | " + x[0].decision + " | " + c.decision + " | " +
                    (c.components.isEmpty() ? "–" : String.valueOf(c.score)) + " | " + (legacyRight ? "✓" : "✗") + " | " + (newRight ? "✓" : "✗") + " |");
        }
        // Held-out blind sets: decisions only (their own fields are gated by HeldOutBlind).
        List<String> heldSections = new ArrayList<>(), heldChanges = new ArrayList<>();
        long blindReceived = java.time.LocalDateTime.of(2026, 10, 22, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        for (String file : HELD_OUT_SETS) {
            java.util.Map<String, Integer> hExpected = new java.util.TreeMap<>(), hP3 = new java.util.TreeMap<>(), hP4 = new java.util.TreeMap<>(), hBuckets = new java.util.TreeMap<>();
            int heldRight = 0;
            List<java.util.Map<String, String>> held = HeldOutBlind.read(Path.of(file));
            for (java.util.Map<String, String> h : held) {
                MessageNormalizer.Raw raw = "sms".equals(h.get("channel")) ? MessageNormalizer.Raw.sms("held-out", h.get("body")) :
                        MessageNormalizer.Raw.notification("held-out", 0, h.get("android.title"), h.get("android.text"), h.get("android.bigText"), h.get("android.textLines"), null, null);
                TransactionExtractor.Extraction[] x = new TransactionExtractor.Extraction[1];
                ConfidenceScorer.Confidence c = confidence(raw, blindReceived, x);
                hExpected.merge(h.get("expected_decision"), 1, Integer::sum); hP3.merge(x[0].decision, 1, Integer::sum);
                hP4.merge(c.decision, 1, Integer::sum); hBuckets.merge(bucket(c), 1, Integer::sum);
                if (HeldOutBlind.equal("decision", c.decision, h.get("expected_decision"))) heldRight++;
                if (!c.decision.equals(x[0].decision)) heldChanges.add("| " + h.get("id") + " | " + x[0].decision + " | " + c.decision + " | " + c.score + " | " + cell(String.join(",", c.reasons)) + " |");
            }
            heldSections.add("### Decision distribution, " + Path.of(file).getFileName() + " (" + held.size() + " messages)");
            heldSections.add("");
            distribution(heldSections, "Decision", new String[]{"Expected", "Phase 3 (rules)", "Phase 4 (scored)"}, List.of(hExpected, hP3, hP4));
            heldSections.add("");
            heldSections.add("Phase 4 decisions equal to expected: " + heldRight + " / " + held.size() + ". Score buckets: " + hBuckets + ".");
            heldSections.add("");
        }
        List<String> out = new ArrayList<>();
        out.add("Score = event " + ParsingPolicy.SCORE_EVENT_EXPLICIT + " (card+merchant context only " + ParsingPolicy.SCORE_EVENT_CONTEXT + ") + amount " +
                ParsingPolicy.SCORE_AMOUNT + " + currency " + ParsingPolicy.SCORE_CURRENCY + " + direction " + ParsingPolicy.SCORE_DIRECTION_EXPLICIT +
                " (likely " + ParsingPolicy.SCORE_DIRECTION_LIKELY + ") + identity " + ParsingPolicy.SCORE_IDENTITY + " − promo " + ParsingPolicy.PENALTY_PROMO_SIGNALS +
                ". AUTO ≥ " + ParsingPolicy.AUTO_THRESHOLD + ", REVIEW ≥ " + ParsingPolicy.REVIEW_THRESHOLD + "; hard failures are UNUNDERSTOOD and review caps are never AUTO.");
        out.add("");
        out.add("### Corpus (" + rows.size() + " rows): decision and principal fields vs expectation");
        out.add("");
        out.add("| Outcome | Rows |");
        out.add("| --- | ---: |");
        out.add("| Both correct | " + both + " |");
        out.add("| **New correct, legacy wrong** | " + newOnly + " |");
        out.add("| **Legacy correct, new wrong** | " + legacyOnly + " |");
        out.add("| Both wrong | " + neither + " |");
        out.add("");
        out.add("### Decision distribution, corpus");
        out.add("");
        distribution(out, "Decision", new String[]{"Expected", "Legacy", "Phase 3 (rules)", "Phase 4 (scored)"}, List.of(expected, legacy, p3, p4));
        out.add("");
        out.add("Phase 4 score buckets (corpus): " + buckets + ".");
        out.add("");
        out.addAll(heldSections);
        out.add("### Decisions that changed from phase 3 to phase 4");
        out.add("");
        out.add("Corpus: " + changes.size() + ". Held-out: " + heldChanges.size() + ".");
        if (!changes.isEmpty()) { out.add(""); out.add("| Row | Phase 3 | Phase 4 | Score | Components |"); out.add("| --- | --- | --- | ---: | --- |"); out.addAll(changes); }
        if (!heldChanges.isEmpty()) { out.add(""); out.add("| Held-out | Phase 3 | Phase 4 | Score | Reasons |"); out.add("| --- | --- | --- | ---: | --- |"); out.addAll(heldChanges); }
        out.add("");
        out.add("### Rows where phase 4 differs from the expectation (" + misses.size() + ")");
        if (!misses.isEmpty()) { out.add(""); out.add("| Row | Expected | Phase 4 | Score |"); out.add("| --- | --- | --- | ---: |"); out.addAll(misses); }
        out.add("");
        out.add("### Every corpus row");
        out.add("");
        out.add("| Row | Expected | Legacy | Phase 3 | Phase 4 | Score | Legacy | New |");
        out.add("| --- | --- | --- | --- | --- | ---: | :-: | :-: |");
        out.addAll(table);
        return out;
    }

    public static void main(String[] args) throws Exception {
        String phase = args.length == 0 ? "classification" : args[0];
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        List<String> body = phase.equals("classification") ? classification(rows) : phase.equals("extraction") ? extraction(rows) :
                phase.equals("confidence") ? confidenceReport(rows) : null;
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
        Files.writeString(report, String.join("\n", page) + "\n", StandardCharsets.UTF_8);
        for (String line : body.subList(0, 9)) System.out.println(line);
        System.out.println("Full table: " + report);
    }
}
