package com.mohamedgamal.masareef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shadow mode for the replacement pipeline. Runs the implemented stages beside a finished legacy capture and
 * keeps a bounded in-memory observation; ShadowStore persists it, field values sealed by EvidenceVault. The legacy
 * decision is final: nothing here writes the ledger, changes a CaptureResult, logs message text or uploads anything,
 * and no failure here reaches the caller.
 */
final class ParserShadow {
    /** Stages not implemented yet; every observation says so instead of inventing a decision. */
    static final String NOT_IMPLEMENTED = "LEARNING_FEEDBACK_UI";
    /** Process-level learning model: fed by the user's merchant rules now; dismissals/confirmations need the pending UI. */
    private static final LocalLearning LEARNING = new LocalLearning();

    static LocalLearning learning() { return LEARNING; }

    static final class Observation {
        final long observedAt;
        final String channel, legacyReason, status, merchantProposal, policy, type, stageDecision, decision;
        /** The stage-4 decision before local learning; equal to decision unless the user's own data changed it. */
        String scoredDecision;
        final Set<String> reasons;
        final int fragments, normalizedChars;
        /** Stage-4 confidence 0..100, or -1 when the message was not scored (IGNORE / multiple events / failure). */
        int score = -1;
        /** Corpus movement kind, or null. */
        String movementKind;
        /** Extracted values by field name, for the encrypted store only; never printed by toString. */
        final java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        /** Notification extras used, then the names (never values) of the transaction fields extraction found. */
        final List<String> fields, extracted;

        Observation(long observedAt, String channel, String legacyReason, String status, Set<String> reasons,
                    int fragments, int normalizedChars, List<String> fields, String merchantProposal, String type, String stageDecision,
                    String decision, List<String> extracted) {
            this.observedAt = observedAt; this.channel = channel; this.legacyReason = legacyReason; this.status = status;
            this.reasons = Collections.unmodifiableSet(reasons); this.fragments = fragments; this.normalizedChars = normalizedChars;
            this.fields = Collections.unmodifiableList(fields); this.merchantProposal = merchantProposal;
            this.type = type; this.stageDecision = stageDecision; this.decision = decision;
            this.extracted = Collections.unmodifiableList(extracted);
            this.policy = ParsingPolicy.POLICY_VERSION + "/" + ParsingPolicy.NORMALIZER_VERSION + "/" + ParsingPolicy.CLASSIFIER_VERSION +
                    "/" + ParsingPolicy.EXTRACTOR_VERSION + "/" + ParsingPolicy.CONFIDENCE_VERSION + "/" + ParsingPolicy.LEARNING_VERSION +
                    "/" + ParsingPolicy.CORPUS_CONTRACT;
        }

        @Override public String toString() {
            return channel + " " + status + (type == null ? "" : " type=" + type + "/" + stageDecision) +
                    (decision == null ? "" : " decision=" + decision + (decision.equals(scoredDecision) ? "" : " (scored " + scoredDecision + ")") +
                            (score < 0 ? "" : " score=" + score) + " found=" + extracted) +
                    " legacy=" + legacyReason + " reasons=" + reasons + " fields=" + fields +
                    " chars=" + normalizedChars + (merchantProposal == null ? "" : " merchant=" + merchantProposal) +
                    " notImplemented=" + NOT_IMPLEMENTED + " policy=" + policy;
        }
    }

    private static final ArrayDeque<Observation> RECENT = new ArrayDeque<>();

    /**
     * Observes one message after the legacy path decided it. legacyMerchant is the merchant the legacy path stored,
     * or null; it is compared with the normalized catalog index, never written back.
     */
    static Observation observe(MessageNormalizer.Raw raw, String legacyReason, String legacyMerchant, long now) {
        return observe(raw, legacyReason, legacyMerchant, now, now);
    }

    /** receivedAt is the capture time the legacy path used; it resolves yearless bank dates. */
    static Observation observe(MessageNormalizer.Raw raw, String legacyReason, String legacyMerchant, long receivedAt, long now) {
        if (raw == null) return null;
        Observation observation;
        try {
            MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(raw);
            Set<String> reasons = new LinkedHashSet<>(normalized.reasons);
            MessageClassifier.Result classified = MessageClassifier.classify(normalized);
            TransactionExtractor.Extraction extraction = TransactionExtractor.extract(normalized, classified, receivedAt);
            ConfidenceScorer.Confidence confidence = ConfidenceScorer.score(classified, extraction);
            LocalLearning.Learned learned = LEARNING.apply(raw.source, normalized.text, confidence, extraction);
            reasons.addAll(learned.reasons);
            List<String> extracted = new ArrayList<>();
            String[] names = {"AMOUNT", "CURRENCY", "DIRECTION", "MERCHANT", "BALANCE", "FEE", "CREDIT_LIMIT", "ORIGINAL_AMOUNT", "CARD", "ACCOUNT", "COUNTERPARTY", "DATETIME"};
            Object[] values = {extraction.amountMinor, extraction.currency, extraction.direction, extraction.merchantKey, extraction.balanceMinor,
                    extraction.feeMinor, extraction.creditLimitMinor, extraction.originalMinor, extraction.cardLast4, extraction.accountLast4,
                    extraction.counterparty, extraction.datetime};
            for (int i = 0; i < names.length; i++) if (values[i] != null) extracted.add(names[i]);
            String proposal = null;
            if (legacyMerchant != null && !legacyMerchant.isBlank()) {
                MerchantCatalogIndex.Match match = MerchantCatalogIndex.standard().lookup(legacyMerchant);
                proposal = match.toString();
                String legacyIdentity = MerchantClassifier.category(legacyMerchant) == null ? null : MerchantClassifier.displayName(legacyMerchant);
                reasons.add(java.util.Objects.equals(match.name, legacyIdentity) ? "MERCHANT_AGREES" : "MERCHANT_DIFFERS");
            }
            observation = new Observation(now, raw.channel.name(), legacyReason, normalized.empty() ? "EMPTY" : "EXTRACTED",
                    reasons, normalized.fragments.size(), normalized.text.length(), new ArrayList<>(normalized.fields), proposal,
                    classified.type.name(), classified.decision.name(), learned.decision, extracted);
            observation.scoredDecision = confidence.decision;
            if (!confidence.components.isEmpty()) observation.score = confidence.score;
            observation.movementKind = extraction.movementKind;
            String[] keys = {"amount_minor", "currency", "direction", "merchant", "merchant_key", "counterparty", "balance_minor", "fee_minor",
                    "card_last4", "account_last4", "datetime", "accounting_effect", "learned_category", "description", "account_partial"};
            Object[] found = {extraction.amountMinor, extraction.currency, extraction.direction, extraction.merchantSurface, extraction.merchantKey,
                    extraction.counterparty, extraction.balanceMinor, extraction.feeMinor, extraction.cardLast4, extraction.accountLast4,
                    extraction.datetime, extraction.accountingEffect, learned.category, extraction.description, extraction.accountPartial};
            for (int i = 0; i < keys.length; i++) if (found[i] != null) observation.values.put(keys[i], String.valueOf(found[i]));
        } catch (RuntimeException | StackOverflowError failure) {
            // A shadow failure is itself a diagnostic; it never propagates to legacy capture.
            observation = new Observation(now, raw.channel.name(), legacyReason, "STAGE_FAILED:" + failure.getClass().getSimpleName(),
                    new LinkedHashSet<>(), 0, 0, new ArrayList<>(), null, null, null, null, new ArrayList<>());
        }
        synchronized (RECENT) {
            RECENT.addLast(observation);
            while (RECENT.size() > ParsingPolicy.SHADOW_BUFFER_SIZE) RECENT.removeFirst();
        }
        return observation;
    }

    /** Newest last; a copy, for a future local debug screen. */
    static List<Observation> recent() {
        synchronized (RECENT) { return new ArrayList<>(RECENT); }
    }

    static void clear() {
        synchronized (RECENT) { RECENT.clear(); }
    }

    private ParserShadow() {}
}
