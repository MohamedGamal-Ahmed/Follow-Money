package com.mohamedgamal.masareef;

/**
 * What saving the commitment editor does with the amount field. Pure, so the JVM checks cover it.
 *
 * Review of PR #52 (30 Oct 2026): after "4,439 for the next installment only" the editor showed the plan's 5,000, so a
 * later save (a name change) asked to apply 5,000 to the installment, and typing 4,439 changed the plan without asking.
 * The field now shows the due installment's amount, and the plan's amount is kept unless the user changes the number.
 */
final class CommitmentEdit {
    private CommitmentEdit() {}

    /** Save as typed (new commitment, closed one, or a currency change). */
    static final int PLAIN = 0;
    /** Amount untouched: save everything else and keep the plan's own amount for later installments. */
    static final int KEEP_PLAN = 1;
    /** Amount changed on an open commitment: ask «للقسط الجاي بس» or «للقسط الجاي وكل اللي بعده». */
    static final int ASK_SCOPE = 2;

    /** The number the editor shows: the due installment's amount, the one on the commitment page. */
    static long fieldAmount(long dueAmount) { return dueAmount; }

    static int action(boolean editing, boolean open, boolean sameCurrency, long dueAmount, long typed) {
        if (!editing || !open || !sameCurrency) return PLAIN;
        return typed == dueAmount ? KEEP_PLAN : ASK_SCOPE;
    }
}
