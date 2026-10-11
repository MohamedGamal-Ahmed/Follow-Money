package com.mohamedgamal.masareef;

/** Applies only movements that happened after a bank-confirmed balance. */
final class BalanceTimeline {
    private BalanceTimeline() {}

    static boolean after(long eventAt, long receivedAt, long anchorEventAt, long anchorReceivedAt) {
        return eventAt > anchorEventAt ||
                (eventAt == anchorEventAt && receivedAt > anchorReceivedAt);
    }

    static long delta(String kind, long fromId, long toId, long accountId, long amountMinor) {
        if ("expense".equals(kind) && fromId == accountId) return -amountMinor;
        if ("income".equals(kind) && fromId == accountId) return amountMinor;
        if ("transfer".equals(kind)) {
            if (fromId == accountId && toId == accountId) return 0;
            if (fromId == accountId) return -amountMinor;
            if (toId == accountId) return amountMinor;
        }
        return 0;
    }
}
