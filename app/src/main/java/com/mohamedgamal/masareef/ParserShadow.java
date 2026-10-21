package com.mohamedgamal.masareef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shadow mode for the replacement pipeline. Runs the implemented stages beside a finished legacy capture and
 * keeps a bounded in-memory observation. The legacy decision is final: nothing here writes the ledger, changes
 * a CaptureResult, persists, logs message text or uploads anything, and no failure here reaches the caller.
 */
final class ParserShadow {
    /** Stages not implemented yet; every observation says so instead of inventing a decision. */
    static final String NOT_IMPLEMENTED = "CLASSIFICATION|EXTRACTION|CONFIDENCE|LOCAL_LEARNING";

    static final class Observation {
        final long observedAt;
        final String channel, legacyReason, status, merchantProposal, policy;
        final Set<String> reasons;
        final int fragments, normalizedChars;
        final List<String> fields;

        Observation(long observedAt, String channel, String legacyReason, String status, Set<String> reasons,
                    int fragments, int normalizedChars, List<String> fields, String merchantProposal) {
            this.observedAt = observedAt; this.channel = channel; this.legacyReason = legacyReason; this.status = status;
            this.reasons = Collections.unmodifiableSet(reasons); this.fragments = fragments; this.normalizedChars = normalizedChars;
            this.fields = Collections.unmodifiableList(fields); this.merchantProposal = merchantProposal;
            this.policy = ParsingPolicy.POLICY_VERSION + "/" + ParsingPolicy.NORMALIZER_VERSION + "/" + ParsingPolicy.CORPUS_CONTRACT;
        }

        @Override public String toString() {
            return channel + " " + status + " legacy=" + legacyReason + " reasons=" + reasons + " fields=" + fields +
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
        if (!ParsingPolicy.SHADOW_ENABLED || raw == null) return null;
        Observation observation;
        try {
            MessageNormalizer.Normalized normalized = MessageNormalizer.normalize(raw);
            Set<String> reasons = new LinkedHashSet<>(normalized.reasons);
            String proposal = null;
            if (legacyMerchant != null && !legacyMerchant.isBlank()) {
                MerchantCatalogIndex.Match match = MerchantCatalogIndex.standard().lookup(legacyMerchant);
                proposal = match.toString();
                String legacyIdentity = MerchantClassifier.category(legacyMerchant) == null ? null : MerchantClassifier.displayName(legacyMerchant);
                reasons.add(java.util.Objects.equals(match.name, legacyIdentity) ? "MERCHANT_AGREES" : "MERCHANT_DIFFERS");
            }
            observation = new Observation(now, raw.channel.name(), legacyReason, normalized.empty() ? "EMPTY" : "NORMALIZED",
                    reasons, normalized.fragments.size(), normalized.text.length(), new ArrayList<>(normalized.fields), proposal);
        } catch (RuntimeException | StackOverflowError failure) {
            // A shadow failure is itself a diagnostic; it never propagates to legacy capture.
            observation = new Observation(now, raw.channel.name(), legacyReason, "STAGE_FAILED:" + failure.getClass().getSimpleName(),
                    new LinkedHashSet<>(), 0, 0, new ArrayList<>(), null);
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
