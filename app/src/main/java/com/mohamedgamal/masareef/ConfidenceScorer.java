package com.mohamedgamal.masareef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline stage 4: turns classification and extraction evidence into a 0..100 confidence score and the final proposed
 * decision. Weights, thresholds, review caps and hard failures live in ParsingPolicy. A hard failure is UNUNDERSTOOD
 * whatever the score; a review cap is never AUTO. Shadow mode only: the legacy path still decides the ledger.
 */
final class ConfidenceScorer {
    static final class Confidence {
        final int score;
        final String decision;
        final Map<String, Integer> components;
        final Set<String> reasons;
        Confidence(int score, String decision, Map<String, Integer> components, Set<String> reasons) {
            this.score = score; this.decision = decision;
            this.components = Collections.unmodifiableMap(components); this.reasons = Collections.unmodifiableSet(reasons);
        }
    }

    static Confidence score(MessageClassifier.Result classified, TransactionExtractor.Extraction x) {
        Set<String> reasons = new LinkedHashSet<>(x.reasons);
        Map<String, Integer> parts = new LinkedHashMap<>();
        if (classified.decision != MessageClassifier.Decision.EXTRACT) return new Confidence(0, classified.decision.name(), parts, reasons);
        boolean contextOnly = classified.reasons.contains("CARD_MERCHANT_CONTEXT");
        parts.put("event", contextOnly ? ParsingPolicy.SCORE_EVENT_CONTEXT : ParsingPolicy.SCORE_EVENT_EXPLICIT);
        parts.put("amount", x.amountMinor != null && x.amountMinor > 0 ? ParsingPolicy.SCORE_AMOUNT : 0);
        parts.put("currency", x.currency != null ? ParsingPolicy.SCORE_CURRENCY : 0);
        parts.put("direction", x.direction == null ? 0 : "likely".equals(x.directionCertainty) ?
                ParsingPolicy.SCORE_DIRECTION_LIKELY : ParsingPolicy.SCORE_DIRECTION_EXPLICIT);
        boolean identity = x.merchantKey != null || x.counterparty != null || x.accountLast4 != null || x.cardLast4 != null;
        parts.put("identity", identity ? ParsingPolicy.SCORE_IDENTITY : 0);
        if (classified.reasons.contains("PROMO_SIGNALS")) parts.put("promo", -ParsingPolicy.PENALTY_PROMO_SIGNALS);
        int score = 0;
        for (int value : parts.values()) score += value;
        score = Math.max(0, Math.min(100, score));

        String decision;
        if (any(reasons, ParsingPolicy.HARD_FAILURES)) decision = "UNUNDERSTOOD";
        else if (score < ParsingPolicy.REVIEW_THRESHOLD) { decision = "UNUNDERSTOOD"; reasons.add("LOW_CONFIDENCE"); }
        else if (score < ParsingPolicy.AUTO_THRESHOLD) { decision = "REVIEW"; reasons.add("LOW_CONFIDENCE"); }
        else decision = "AUTO";
        if (decision.equals("AUTO") && any(reasons, ParsingPolicy.REVIEW_CAPS)) decision = "REVIEW";
        return new Confidence(score, decision, parts, reasons);
    }

    private static boolean any(Set<String> reasons, String[] codes) {
        for (String code : codes) if (reasons.contains(code)) return true;
        return false;
    }

    private ConfidenceScorer() {}
}
