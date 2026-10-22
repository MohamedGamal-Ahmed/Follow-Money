package com.mohamedgamal.masareef;

import java.util.List;

/**
 * Shadow dedupe, the corpus contract (tools/fixtures/README.md, "identity first, 60 seconds only for candidates"):
 * 1. Different delivery identities (inbox IDs, Android key + original post time) are distinct deliveries.
 * 2. The same identity with the same normalized content is a replay.
 * 3. The same identity with other content is conflicting evidence: review, never a guessed merge.
 * 4. Without identity, the same source and normalized content within DUPLICATE_CANDIDATE_WINDOW_MS of any earlier
 *    identity-less delivery is a possible duplicate for review, never dropped or merged.
 * 5. Outside that window text alone never suppresses a payment. Financial fields are never identity.
 * Pure logic; ShadowStore supplies the earlier deliveries. Shadow mode only.
 */
final class ShadowDedupe {
    static final class Delivery {
        final long id, receivedAt;
        final String source, identity, contentHash;
        Delivery(long id, String source, String identity, long receivedAt, String contentHash) {
            this.id = id; this.source = source; this.identity = identity; this.receivedAt = receivedAt; this.contentHash = contentHash;
        }
    }

    static final class Verdict {
        /** NEW, REPLAY or REVIEW_DUPLICATE (the corpus expected_capture values). */
        final String capture;
        /** DELIVERY_REPLAY, IDENTITY_CONFLICT, IDENTITY_UNAVAILABLE, or null for NEW. */
        final String reason;
        /** The earlier delivery this one repeats or may repeat, or -1. */
        final long duplicateOf;
        Verdict(String capture, String reason, long duplicateOf) { this.capture = capture; this.reason = reason; this.duplicateOf = duplicateOf; }
        @Override public String toString() { return capture + (reason == null ? "" : "/" + reason) + (duplicateOf < 0 ? "" : "@" + duplicateOf); }
    }

    static final Verdict NEW = new Verdict("NEW", null, -1);

    /** earlier: deliveries seen before this one, any order. */
    static Verdict decide(Delivery current, List<Delivery> earlier) {
        if (current.identity != null) {
            Delivery conflict = null;
            for (Delivery d : earlier) {
                if (!current.identity.equals(d.identity)) continue;
                if (current.contentHash.equals(d.contentHash)) return new Verdict("REPLAY", "DELIVERY_REPLAY", d.id);
                conflict = d;
            }
            return conflict == null ? NEW : new Verdict("REVIEW_DUPLICATE", "IDENTITY_CONFLICT", conflict.id);
        }
        Delivery nearest = null;
        for (Delivery d : earlier) {
            if (d.identity != null || !current.contentHash.equals(d.contentHash) || !java.util.Objects.equals(current.source, d.source)) continue;
            long gap = Math.abs(current.receivedAt - d.receivedAt);
            if (gap > ParsingPolicy.DUPLICATE_CANDIDATE_WINDOW_MS) continue;
            if (nearest == null || gap < Math.abs(current.receivedAt - nearest.receivedAt)) nearest = d;
        }
        return nearest == null ? NEW : new Verdict("REVIEW_DUPLICATE", "IDENTITY_UNAVAILABLE", nearest.id);
    }

    /** Exact normalized content of one source: the replay and candidate key, never an event ID on its own. */
    static String contentHash(String source, String normalizedText) {
        return CaptureIdentity.hash(String.valueOf(source) + "\n" + normalizedText);
    }

    private ShadowDedupe() {}
}
