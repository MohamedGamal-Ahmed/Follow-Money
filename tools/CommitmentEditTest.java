package com.mohamedgamal.masareef;

/** Reviews of PR #52: saves after an "only this installment" edit must not move the plan's amount or currency. */
public final class CommitmentEditTest {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private static boolean same(CommitmentEdit.Price a, long amount, String currency) { return a.amount == amount && a.currency.equals(currency); }

    public static void main(String[] args) {
        // Plan 5,000 EGP; the due installment was set to 4,439 EGP "for the next one only".
        CommitmentEdit.Price plan = new CommitmentEdit.Price(500000, "EGP");
        CommitmentEdit.Price shown = CommitmentEdit.field(443900, "EGP");
        check(same(shown, 443900, "EGP"), "the editor shows the due installment's price");
        int untouched = CommitmentEdit.action(true, true, 443900, "EGP", shown.amount, shown.currency);
        check(untouched == CommitmentEdit.KEEP_PLAN, "untouched price keeps the plan");
        check(same(CommitmentEdit.planTerms(untouched, false, plan, shown), 500000, "EGP"), "a name-only save keeps 5,000 EGP");
        int changed = CommitmentEdit.action(true, true, 443900, "EGP", 500000, "EGP");
        check(changed == CommitmentEdit.ASK_SCOPE, "a different number asks the scope");
        check(same(CommitmentEdit.planTerms(changed, true, plan, new CommitmentEdit.Price(420000, "EGP")), 500000, "EGP"), "only this: plan unchanged");
        check(same(CommitmentEdit.planTerms(changed, false, plan, new CommitmentEdit.Price(420000, "EGP")), 420000, "EGP"), "forward: plan follows");

        // Currency mismatch: an open 4,439 EGP installment, a 100 USD plan.
        CommitmentEdit.Price usdPlan = new CommitmentEdit.Price(10000, "USD");
        CommitmentEdit.Price mixed = CommitmentEdit.field(443900, "EGP");
        check(same(mixed, 443900, "EGP"), "amount and currency come from the same installment, never 4,439 USD");
        int nameOnly = CommitmentEdit.action(true, true, 443900, "EGP", mixed.amount, mixed.currency);
        check(nameOnly == CommitmentEdit.KEEP_PLAN, "untouched mixed price keeps the plan");
        check(same(CommitmentEdit.planTerms(nameOnly, false, usdPlan, mixed), 10000, "USD"), "a name-only save keeps 100 USD");
        int newCurrency = CommitmentEdit.action(true, true, 443900, "EGP", 12000, "USD");
        check(newCurrency == CommitmentEdit.PLAIN, "a new currency is a plan change, saved as typed");
        check(same(CommitmentEdit.planTerms(newCurrency, false, usdPlan, new CommitmentEdit.Price(12000, "USD")), 12000, "USD"), "typed price saved");

        check(CommitmentEdit.action(false, true, 0, null, 420000, "EGP") == CommitmentEdit.PLAIN, "a new commitment saves as typed");
        check(CommitmentEdit.action(true, false, 443900, "EGP", 420000, "EGP") == CommitmentEdit.PLAIN, "a closed commitment saves as typed");
        // Review of PR #57: 3 left, the due one 100 and the others 200 → 500, not 3 × 100.
        java.time.LocalDate d1 = java.time.LocalDate.of(2026, 11, 5), d2 = d1.plusMonths(1), d3 = d2.plusMonths(1);
        java.util.List<java.time.LocalDate> dues = java.util.Arrays.asList(d1, d2, d3);
        java.util.Map<java.time.LocalDate, CommitmentEdit.Price> stored = new java.util.HashMap<>();
        stored.put(d1, new CommitmentEdit.Price(10000, "EGP"));
        java.util.Map<String, Long> left = CommitmentEdit.left(dues, stored, new CommitmentEdit.Price(20000, "EGP"));
        check(left.size() == 1 && left.get("EGP") == 50000, "100 + 200 + 200 = 500");
        // A partial payment: the due cycle's remaining (60 of 100) is what counts.
        stored.put(d1, new CommitmentEdit.Price(6000, "EGP"));
        check(CommitmentEdit.left(dues, stored, new CommitmentEdit.Price(20000, "EGP")).get("EGP") == 46000, "partial payment deducted");
        // A settled cycle adds nothing; currencies stay apart.
        stored.put(d1, new CommitmentEdit.Price(0, "EGP"));
        stored.put(d2, new CommitmentEdit.Price(5000, "USD"));
        java.util.Map<String, Long> mixedLeft = CommitmentEdit.left(dues, stored, new CommitmentEdit.Price(20000, "EGP"));
        check(mixedLeft.get("EGP") == 20000 && mixedLeft.get("USD") == 5000 && mixedLeft.size() == 2, "currencies are never added together");
        check(CommitmentEdit.left(java.util.Collections.emptyList(), stored, null).isEmpty(), "nothing left, nothing shown");
        System.out.println("Commitment edit checks: " + checks + " passed");
    }
}
