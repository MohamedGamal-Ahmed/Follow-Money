package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stage-3 extraction: every corpus transaction field, listed corpus disagreements, and held-out messages. */
public final class TransactionExtractorTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    /** Fields where the extractor and the corpus disagree, left for the owner; the corpus is never edited to match code. */
    // p011, p146, p136 resolved by the owner on 21 Oct 2026; refunds as expense_offset resolved on 22 Oct 2026.
    static final Map<String, String> KNOWN_DISAGREEMENTS = Map.of();

    static final String[] FIELDS = {"expected_amount_minor", "expected_currency", "expected_direction", "expected_merchant_key",
            "expected_balance_minor", "expected_balance_currency", "expected_card_last4", "expected_datetime", "expected_time_precision"};
    static final String[] V2_FIELDS = {"expected_fee_minor", "expected_credit_limit_minor", "expected_original_amount_minor",
            "expected_original_currency", "expected_account_last4", "expected_time_ambiguity", "expected_year_origin", "expected_expiry_date"};

    static TransactionExtractor.Extraction run(String text, long receivedAt) {
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", text));
        return TransactionExtractor.extract(normalized, MessageClassifier.classify(normalized), receivedAt);
    }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        int compared = 0, listed = 0;
        for (ParserCorpusFixture.Row row : rows) {
            String id = row.get("id");
            TransactionExtractor.Extraction x = ShadowComparison.extract(row);
            same(x.decision, ShadowComparison.expectedDecision(row), "Decision " + id + " " + x.reasons);
            if (!"transaction".equals(row.get("expected_type"))) continue;
            Map<String, String> got = new LinkedHashMap<>();
            for (String field : FIELDS) got.put(field, value(x, field));
            if (!"legacy-v1".equals(row.get("source_evidence_id"))) for (String field : V2_FIELDS) got.put(field, value(x, field));
            // Accounting effect: only expense_offset is extracted so far; pending/pending_internal rows are not compared yet.
            if (!"legacy-v1".equals(row.get("source_evidence_id")) && (row.get("expected_accounting_effect") == null || "expense_offset".equals(row.get("expected_accounting_effect"))))
                got.put("expected_accounting_effect", x.accountingEffect);
            for (var entry : got.entrySet()) {
                boolean equal = Objects.equals(entry.getValue(), row.get(entry.getKey()));
                if (entry.getKey().equals(KNOWN_DISAGREEMENTS.get(id))) { check(!equal, "Listed disagreement now agrees; remove it: " + id); listed++; continue; }
                check(equal, id + " " + entry.getKey() + ": <" + entry.getValue() + "> != <" + row.get(entry.getKey()) + ">");
                compared++;
            }
            if (row.get("expected_counterparty_token") != null) same(x.counterparty, row.get("expected_counterparty_token"), "Counterparty " + id);
            if ("expense_offset".equals(row.get("expected_accounting_effect"))) same(x.accountingEffect, "expense_offset", "Reversal offsets expense " + id);
            if (!ShadowComparison.expectedDecision(row).equals("UNUNDERSTOOD") && row.get("expected_standalone_decision") == null)
                for (String reason : row.get("required_reasons").split("\\|")) check(x.reasons.contains(reason), id + " missing reason " + reason + " " + x.reasons);
        }
        same(listed, KNOWN_DISAGREEMENTS.size(), "Every listed disagreement is still a disagreement");

        // Held-out messages, not in the corpus: {text, decision, amount, currency, direction, merchant key, card, account, balance, datetime}.
        long received = java.time.LocalDate.of(2026, 11, 6).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        String[][] heldOut = {
            {"You paid EGP 100 to QA SHOP", "AUTO", "10000", "EGP", "out", "qa shop", null, null, null, null},
            {"تم خصم 75 جنيه من بطاقتك المنتهية 4411 لدى كارفور المعادي", "AUTO", "7500", "EGP", "out", "كارفور المعادي", "4411", null, null, null},
            {"Your account was credited with EGP 2,000.00 on 05/11/2026.", "AUTO", "200000", "EGP", "in", null, null, null, null, "2026-11-05"},
            {"تم سحب نقدي 1000 جنيه من ماكينة الصراف. الرصيد 3000 جنيه", "AUTO", "100000", "EGP", "out", null, null, null, "300000", null},
            {"Refund of USD 4.99 from APP STORE has been processed.", "AUTO", "499", "USD", "in", "app store", null, null, null, null},
            {"Paid EUR 1.250 at Cafe Nine", "UNUNDERSTOOD", null, "EUR", "out", "cafe nine", null, null, null, null},
            // Owner rule (24 Oct): a missing merchant needs review only on a card purchase.
            {"تم دفع 300 جنيه ببطاقتك المنتهية بـ 1234", "REVIEW", "30000", "EGP", "out", null, "1234", null, null, null},
            {"تم دفع 300 جنيه", "AUTO", "30000", "EGP", "out", null, null, null, null, null},
            {"Charged GBP 12.00 at TESCO STORES on 03/11/26 at 14:05. Available balance GBP 88.00", "AUTO", "1200", "GBP", "out", "tesco stores", null, null, "8800", "2026-11-03T14:05"},
            {"Transfer of EGP 750 to Mona completed.", "AUTO", "75000", "EGP", "out", "mona", null, null, null, null},
            {"EGP 45.50 credited to your account ****9911 from SALARY CO", "AUTO", "4550", "EGP", "in", "salary co", null, "9911", null, null},
        };
        for (String[] probe : heldOut) {
            TransactionExtractor.Extraction x = run(probe[0], received);
            String[] got = {x.decision, x.amountMinor == null ? null : String.valueOf(x.amountMinor), x.currency, x.direction, x.merchantKey,
                    x.cardLast4, x.accountLast4, x.balanceMinor == null ? null : String.valueOf(x.balanceMinor), x.datetime};
            for (int k = 0; k < got.length; k++) same(got[k], probe[k + 1], "Held-out field " + k + ": " + probe[0] + " " + x.reasons);
        }
        // Folding keeps a map to the original spelling, so the merchant surface keeps its letters.
        same(run("تم دفع 50 جنيه لدى \"مَكْتَبَة النور\"", received).merchantSurface, "مَكْتَبَة النور", "Surface keeps harakat");

        System.out.println("Transaction extractor checks passed: " + checks + " (" + compared + " corpus field comparisons, " + listed +
                " listed disagreements, " + heldOut.length + " held-out messages).");
    }

    static String value(TransactionExtractor.Extraction x, String field) {
        switch (field) {
            case "expected_amount_minor": return x.amountMinor == null ? null : String.valueOf(x.amountMinor);
            case "expected_currency": return x.currency;
            case "expected_direction": return x.direction;
            case "expected_merchant_key": return x.merchantKey;
            default: return ShadowComparison.secondary(x, field);
        }
    }
}
