package com.mohamedgamal.masareef;

/**
 * Corpus expectations shared by the pipeline's JVM checks: the standalone decision a row expects, the pipeline run on a
 * row, its secondary fields by expected column, and the held-out sets. (Moved out of ShadowComparison, whose legacy
 * side-by-side report went with the legacy opinion in 0.4.0.)
 */
final class CorpusExpectations {
    static final String[] HELD_OUT_SETS = {"tools/fixtures/held-out-blind.tsv", "tools/fixtures/held-out-blind-2.tsv", "tools/fixtures/held-out-blind-3.tsv", "tools/fixtures/held-out-blind-4.tsv", "tools/fixtures/held-out-blind-5.tsv"};

    /** Standalone expectation: paired-context rows are judged on their isolated decision. */
    static String expectedDecision(ParserCorpusFixture.Row row) {
        return row.get("expected_standalone_decision") != null ? row.get("expected_standalone_decision") : row.get("expected_decision");
    }

    static TransactionExtractor.Extraction extract(ParserCorpusFixture.Row row) {
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(MessageNormalizerTest.raw(row));
        return TransactionExtractor.extract(normalized, MessageClassifier.classify(normalized), Long.parseLong(row.get("received_at_ms")));
    }

    static String str(Object value) { return value == null ? null : String.valueOf(value); }

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

    static ConfidenceScorer.Confidence confidence(MessageNormalizer.Raw raw, long receivedAt, TransactionExtractor.Extraction[] phase3) {
        MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(raw);
        MessageClassifier.Result classified = MessageClassifier.classify(normalized);
        phase3[0] = TransactionExtractor.extract(normalized, classified, receivedAt);
        return ConfidenceScorer.score(classified, phase3[0]);
    }

    private CorpusExpectations() {}
}
