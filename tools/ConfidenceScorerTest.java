package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stage-4 confidence: corpus and held-out decisions, plus the threshold/cap/failure paths the corpus never reaches. */
public final class ConfidenceScorerTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    static MessageClassifier.Result transaction(String... reasons) {
        return new MessageClassifier.Result(MessageClassifier.Type.TRANSACTION, MessageClassifier.Decision.EXTRACT, new LinkedHashSet<>(List.of(reasons)), 1);
    }

    static TransactionExtractor.Extraction full() {
        TransactionExtractor.Extraction x = new TransactionExtractor.Extraction();
        x.amountMinor = 1000L; x.currency = "EGP"; x.direction = "out"; x.directionCertainty = "explicit"; x.merchantKey = "shop";
        x.reasons.add("AMOUNT_UNAMBIGUOUS"); x.reasons.add("COMPLETED_EVENT"); x.reasons.add("DIRECTION_OUT");
        return x;
    }

    public static void main(String[] args) throws Exception {
        // Weights must add up to the 100-point scale the thresholds assume.
        same(ParsingPolicy.SCORE_EVENT_EXPLICIT + ParsingPolicy.SCORE_AMOUNT + ParsingPolicy.SCORE_CURRENCY +
                ParsingPolicy.SCORE_DIRECTION_EXPLICIT + ParsingPolicy.SCORE_IDENTITY, 100, "Full evidence is 100 points");
        check(ParsingPolicy.REVIEW_THRESHOLD < ParsingPolicy.AUTO_THRESHOLD, "Thresholds ordered");

        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        int changed = 0;
        for (ParserCorpusFixture.Row row : rows) {
            TransactionExtractor.Extraction[] x = new TransactionExtractor.Extraction[1];
            ConfidenceScorer.Confidence c = ShadowComparison.confidence(MessageNormalizerTest.raw(row), Long.parseLong(row.get("received_at_ms")), x);
            same(c.decision, ShadowComparison.expectedDecision(row), "Corpus decision " + row.get("id") + " score " + c.score + " " + c.components);
            if (!c.decision.equals(x[0].decision)) changed++;
            if (c.decision.equals("AUTO")) check(c.score >= ParsingPolicy.AUTO_THRESHOLD, "AUTO below threshold " + row.get("id"));
        }
        long received = java.time.LocalDateTime.of(2026, 10, 22, 12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        for (Map<String, String> h : HeldOutBlind.read(Path.of("tools/fixtures/held-out-blind.tsv"))) {
            MessageNormalizer.Raw raw = "sms".equals(h.get("channel")) ? MessageNormalizer.Raw.sms("held-out", h.get("body")) :
                    MessageNormalizer.Raw.notification("held-out", 0, h.get("android.title"), h.get("android.text"), null, h.get("android.textLines"), null, null);
            same(ShadowComparison.confidence(raw, received, new TransactionExtractor.Extraction[1]).decision, h.get("expected_decision"), "Held-out decision " + h.get("id"));
        }

        // Paths the corpus does not reach.
        ConfidenceScorer.Confidence full = ConfidenceScorer.score(transaction("COMPLETED_EVENT"), full());
        same(full.score, 100, "Full evidence"); same(full.decision, "AUTO", "Full evidence is AUTO");

        TransactionExtractor.Extraction noIdentity = full(); noIdentity.merchantKey = null;
        ConfidenceScorer.Confidence promoNoIdentity = ConfidenceScorer.score(transaction("COMPLETED_EVENT", "PROMO_SIGNALS"), noIdentity);
        same(promoNoIdentity.score, 80, "Promo without identity"); same(promoNoIdentity.decision, "AUTO", "Exactly at AUTO threshold");

        TransactionExtractor.Extraction weak = full(); weak.merchantKey = null; weak.directionCertainty = "likely";
        ConfidenceScorer.Confidence low = ConfidenceScorer.score(transaction("CARD_MERCHANT_CONTEXT"), weak);
        same(low.score, 60, "Context-only event, likely direction, no identity");
        same(low.decision, "REVIEW", "Between thresholds is REVIEW"); check(low.reasons.contains("LOW_CONFIDENCE"), "Low confidence reason");

        TransactionExtractor.Extraction bare = full(); bare.merchantKey = null; bare.currency = null; bare.directionCertainty = "likely";
        ConfidenceScorer.Confidence veryLow = ConfidenceScorer.score(transaction("CARD_MERCHANT_CONTEXT", "PROMO_SIGNALS"), bare);
        same(veryLow.score, 40, "Very weak evidence"); same(veryLow.decision, "UNUNDERSTOOD", "Below REVIEW threshold");

        TransactionExtractor.Extraction failure = full(); failure.reasons.add("AMBIGUOUS_NUMBER_FORMAT");
        same(ConfidenceScorer.score(transaction("COMPLETED_EVENT"), failure).decision, "UNUNDERSTOOD", "Hard failure beats a high score");

        for (String cap : ParsingPolicy.REVIEW_CAPS) {
            TransactionExtractor.Extraction capped = full(); capped.reasons.add(cap);
            same(ConfidenceScorer.score(transaction("COMPLETED_EVENT"), capped).decision, "REVIEW", "Review cap " + cap);
        }
        MessageClassifier.Result ignored = new MessageClassifier.Result(MessageClassifier.Type.PROMO, MessageClassifier.Decision.IGNORE, Set.of("PROMOTIONAL_MESSAGE"), 0);
        same(ConfidenceScorer.score(ignored, new TransactionExtractor.Extraction()).decision, "IGNORE", "Classification IGNORE passes through unscored");

        System.out.println("Confidence checks passed: " + checks + " (" + rows.size() + " corpus rows, 25 held-out, " + changed +
                " decisions changed from phase 3).");
    }
}
