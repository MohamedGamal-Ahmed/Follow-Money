package com.mohamedgamal.masareef;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owner decision (21 Oct 2026): money returned minutes after the same charge (a fee taken and given back) is an
 * expense offset linked to that charge, never income. An incoming movement pairs with the latest earlier outgoing
 * movement from the same source, in the same currency and amount, recorded within FEE_REFUND_WINDOW_MS, that no other
 * return has claimed. A plain credit must name no party; a refund or reversal may, and then pairs only with a charge
 * at the same merchant. Pure; ShadowStore supplies the earlier movements.
 */
final class ShadowOffset {
    static final class Movement {
        final long id, receivedAt;
        final String source, direction, currency, merchantKey;
        final Long amountMinor;
        final boolean recorded, hasParty;
        Movement(long id, String source, long receivedAt, String direction, Long amountMinor, String currency, boolean recorded, boolean hasParty) {
            this(id, source, receivedAt, direction, amountMinor, currency, recorded, hasParty, null);
        }
        Movement(long id, String source, long receivedAt, String direction, Long amountMinor, String currency, boolean recorded, boolean hasParty, String merchantKey) {
            this.id = id; this.source = source; this.receivedAt = receivedAt; this.direction = direction; this.amountMinor = amountMinor;
            this.currency = currency; this.recorded = recorded; this.hasParty = hasParty; this.merchantKey = merchantKey;
        }
    }

    /** The earlier charge this return offsets, or -1. claimed: ids already offset by another return. */
    static long match(Movement current, List<Movement> earlier, Set<Long> claimed) { return match(current, false, earlier, claimed); }

    /** refundWording: the return says it is a refund or reversal, so it may name the merchant it returns from. */
    static long match(Movement current, boolean refundWording, List<Movement> earlier, Set<Long> claimed) {
        if (!"in".equals(current.direction) || current.hasParty && !refundWording || current.amountMinor == null || current.amountMinor <= 0) return -1;
        Movement best = null;
        for (Movement m : earlier) {
            long gap = current.receivedAt - m.receivedAt;
            if (!m.recorded || !"out".equals(m.direction) || claimed.contains(m.id) || gap < 0 || gap > ParsingPolicy.FEE_REFUND_WINDOW_MS) continue;
            if (!Objects.equals(m.source, current.source) || !Objects.equals(m.amountMinor, current.amountMinor) || !Objects.equals(m.currency, current.currency)) continue;
            if (current.merchantKey != null && m.merchantKey != null && !current.merchantKey.equals(m.merchantKey)) continue;
            if (best == null || m.receivedAt > best.receivedAt) best = m;
        }
        return best == null ? -1 : best.id;
    }

    private ShadowOffset() {}
}
