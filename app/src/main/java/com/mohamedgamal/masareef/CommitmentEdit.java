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
     * Review of PR #57: what is left on a plan, per currency. Each remaining installment counts its stored cycle's
     * remaining amount (expected minus what was paid, so a partial payment is deducted) when the cycle exists, and the
     * plan's own price otherwise. Never the due installment's amount times the count.
     */
    static java.util.Map<String, Long> left(java.util.List<java.time.LocalDate> dues,
                                            java.util.Map<java.time.LocalDate, Price> stored, Price plan) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (java.time.LocalDate due : dues) {
            Price p = stored.containsKey(due) ? stored.get(due) : plan;
            if (p == null || p.amount <= 0) continue;
            out.merge(p.currency, p.amount, Math::addExact);
        }
        return out;
    }
}
