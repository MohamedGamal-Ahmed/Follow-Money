package com.mohamedgamal.masareef;

/**
 * What saving the commitment editor does with the amount and currency fields. Pure, so the JVM checks cover it.
 *
 * Reviews of PR #52 (30 Oct 2026): the editor must show one price, amount and currency from the same place (the due
 * installment, as on the commitment page), and a save that leaves that price untouched must keep the plan's own amount
 * and currency for later installments, even when they differ (an open 4,439 EGP installment, a 100 USD plan).
 */
final class CommitmentEdit {
    private CommitmentEdit() {}

    /** Save as typed: new commitment, closed one, or a new currency (applies to new installments). */
    static final int PLAIN = 0;
    /** Price untouched: save everything else and keep the plan's own amount and currency. */
    static final int KEEP_PLAN = 1;
    /** Amount changed in the due installment's currency on an open commitment: ask the scope. */
    static final int ASK_SCOPE = 2;

    /** The amount and currency the editor shows: both from the due installment. */
    static final class Price {
        final long amount; final String currency;
        Price(long amount, String currency) { this.amount = amount; this.currency = currency; }
    }
    static Price field(long dueAmount, String dueCurrency) { return new Price(dueAmount, dueCurrency); }

    static int action(boolean editing, boolean open, long dueAmount, String dueCurrency, long typed, String typedCurrency) {
        if (!editing || !open || dueCurrency == null || !dueCurrency.equals(typedCurrency)) return PLAIN;
        return typed == dueAmount ? KEEP_PLAN : ASK_SCOPE;
    }

    /**
     * The plan terms a save writes: the plan's own for KEEP_PLAN and for an "only this installment" edit, the typed
     * price otherwise.
     */
    static Price planTerms(int action, boolean onlyThisInstallment, Price plan, Price typed) {
        if (plan != null && (action == KEEP_PLAN || action == ASK_SCOPE && onlyThisInstallment)) return plan;
        return typed;
    }

    /**
     * Review of PR #57: what is left on a plan, per currency. Walks the schedule from the next due date and counts the
     * {@code open} installments still owed. A stored cycle that is fully paid (paid early or out of order) is skipped and
     * does not use up one of the open installments; a stored cycle with money left counts its remaining amount (so a
     * partial payment is deducted); a date with no stored cycle counts the plan price. Currencies are never added.
     * {@code schedule} must be long enough: open installments plus the stored cycles that may be settled.
     */
    static java.util.Map<String, Long> left(java.util.List<java.time.LocalDate> schedule, int open,
                                            java.util.Map<java.time.LocalDate, Price> stored, Price plan) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        int counted = 0;
        for (java.time.LocalDate due : schedule) {
            if (counted >= open) break;
            Price p = stored.containsKey(due) ? stored.get(due) : plan;
            if (p == null || p.amount <= 0) continue;   // settled cycle: skipped, not counted
            out.merge(p.currency, p.amount, Math::addExact);
            counted++;
        }
        return out;
    }
}
