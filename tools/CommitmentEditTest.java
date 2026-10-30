package com.mohamedgamal.masareef;

/** Review of PR #52: a second save after an "only this installment" edit must not move the plan's amount. */
public final class CommitmentEditTest {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }

    public static void main(String[] args) {
        // Plan 5,000; the due installment was set to 4,439 "for the next one only".
        long plan = 500000, due = 443900;
        long shown = CommitmentEdit.fieldAmount(due);
        check(shown == due, "the editor shows the due installment, not the plan's later amount");
        // Second save with the number untouched (e.g. a name change): no scope question, the plan keeps 5,000.
        check(CommitmentEdit.action(true, true, true, due, shown) == CommitmentEdit.KEEP_PLAN, "untouched amount keeps the plan");
        // Typing the plan's 5,000 back is a change for this installment: ask the scope.
        check(CommitmentEdit.action(true, true, true, due, plan) == CommitmentEdit.ASK_SCOPE, "a different number asks the scope");
        check(CommitmentEdit.action(true, true, true, due, 420000) == CommitmentEdit.ASK_SCOPE, "a new number asks the scope");
        // New, closed, or currency-changed commitments save as typed.
        check(CommitmentEdit.action(false, true, true, 0, 420000) == CommitmentEdit.PLAIN, "a new commitment saves as typed");
        check(CommitmentEdit.action(true, false, true, due, 420000) == CommitmentEdit.PLAIN, "a closed commitment saves as typed");
        check(CommitmentEdit.action(true, true, false, due, 420000) == CommitmentEdit.PLAIN, "a currency change saves as typed");
        System.out.println("Commitment edit checks: " + checks + " passed");
    }
}
