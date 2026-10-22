package com.mohamedgamal.masareef;

import java.util.Collections;
import java.util.List;

/**
 * What one approved historical SMS batch actually did, reported by the separately named detailed entry
 * points. The legacy {@code long} return is a ledger row-count delta and keeps its exact value; this result
 * never replaces it.
 *
 * <p>Primary outcomes are mutually exclusive per selected row and always add up to {@link #offered}.
 * Side effects and repair are separate observations and are never added into the primary totals. A row's
 * side effects are read from the stored tables around its own write, so a duplicate that short-circuits
 * before any write reports none, and a matched row that {@code syncAutomatic} left untouched reports none.
 */
final class ImportResult {
    /** Why one selected row ended the way it did; a capture reason is kept verbatim where one applies. */
    enum Reason {
        /** No ledger row and the capture write named the refusal. */
        MISSING_BODY, VERIFICATION, FUTURE_ONLY, UNSUCCESSFUL, INSERT_NOT_APPLIED, NO_LEDGER_CANDIDATE,
        /** The stored merchant rule refused the row; read from the rule, not from the capture write. */
        MERCHANT_IGNORED,
        /** The exact key was already known; {@link Row#knownBy} names the stored table. */
        EXCLUDED, SKIPPED_DUPLICATE,
        /** A ledger row was created, or an existing one was matched and synced. */
        INSERTED, EXISTING_MATCH,
        /** A historical cancellation was recorded and still needs explicit confirmation. */
        REVIEW_REQUIRED, NOTICE_DUPLICATE,
        /** A commitment was matched and its state changed without a ledger row. */
        SERVICE_MATCH_ACCEPTED
    }
    /** The stored identity that already knew a skipped row's key; never guessed from the outcome. */
    enum Known { EXCLUDED, EVIDENCE, RECEIPT, EVENT }
    /** Primary outcome of one selected row. */
    enum Outcome { CREATED, MATCHED_EXISTING, SKIPPED_DUPLICATE, EXCLUDED, REFUSED, NO_LEDGER_CHANGE }

    /** Writes one row actually caused, observed around its own write. */
    static final class Effects {
        final boolean evidenceWritten, receiptWritten, hintWritten, balanceWritten, eventRecorded, linksChanged;
        Effects(boolean evidenceWritten, boolean receiptWritten, boolean hintWritten,
                boolean balanceWritten, boolean eventRecorded, boolean linksChanged) {
            this.evidenceWritten = evidenceWritten; this.receiptWritten = receiptWritten;
            this.hintWritten = hintWritten; this.balanceWritten = balanceWritten;
            this.eventRecorded = eventRecorded; this.linksChanged = linksChanged;
        }
        /** Nothing this row was asked to write actually changed a stored value. */
        boolean none() { return !(evidenceWritten||receiptWritten||hintWritten||balanceWritten||eventRecorded||linksChanged); }
        /** A stored effect other than the receipt: balance, hint, recorded event or commitment link. */
        boolean storedEffect() { return hintWritten||balanceWritten||eventRecorded||linksChanged; }
    }

    /**
     * Repair writes performed inside the batch transaction. Repair reconciles movement in the batch window and
     * may name rows the selection did not name as well as rows it did; it is always reported here and never
     * folded into the selected outcomes.
     */
    static final class Repair {
        final int mergedRows, ownTransfers, feesCreated, archivedRows;
        final long movedMinor, feesMinor;
        Repair(int mergedRows,int ownTransfers,int feesCreated,int archivedRows,long movedMinor,long feesMinor) {
            this.mergedRows = mergedRows; this.ownTransfers = ownTransfers; this.feesCreated = feesCreated;
            this.archivedRows = archivedRows; this.movedMinor = movedMinor; this.feesMinor = feesMinor;
        }
        /** A call that runs no repair (the selected-only entry point). */
        static final Repair NONE = new Repair(0,0,0,0,0,0);
    }

    /** One selected row: its primary outcome, the stored reason, and the writes it actually caused. */
    static final class Row {
        final SmsPreview source;
        final Outcome outcome;
        final Reason reason;
        final Known knownBy;
        /** The row the write produced or matched while the batch ran; repair may archive it afterwards. */
        final Long initialTransactionId;
        /** That row's stored unique key at write time, so a reused row number can never stand in for it. */
        final String identityKey;
        final Effects effects;
        /** The live row carrying this outcome: the same row, or the repair target it was merged into. */
        Long transactionId;
        /** True only while the row the write produced is still that same row, not a row that reused its id. */
        boolean surviving;
        Row(SmsPreview source, Outcome outcome, Reason reason, Known knownBy, Long initialTransactionId,
            String identityKey, Effects effects) {
            this.source = source; this.outcome = outcome; this.reason = reason;
            this.knownBy = knownBy; this.initialTransactionId = initialTransactionId;
            this.identityKey = identityKey; this.effects = effects;
            this.transactionId = initialTransactionId;
        }
    }

    final int offered, created, survivingCreated, matchedExisting, skippedDuplicate, excluded, refused, noLedgerChange;
    final int evidenceWritten, receiptWritten, hintWritten, balanceWritten, eventRecorded, linksChanged;
    final Repair repair;
    final CaptureResult.Completion completion;
    /** True only when this call owned the transaction and closed it successfully, with no outer transaction. */
    final boolean committed;
    /** Observed global revision delta; false for a caller-owned transaction even while its writes are in flight. */
    final boolean dbChanged;
    final long revisionBefore, revisionAfter;
    final List<Row> rows;

    private ImportResult(List<Row> rows,int offered,int created,int survivingCreated,int matchedExisting,
                         int skippedDuplicate,int excluded,int refused,int noLedgerChange,int evidenceWritten,
                         int receiptWritten,int hintWritten,int balanceWritten,int eventRecorded,int linksChanged,
                         Repair repair,CaptureResult.Completion completion,boolean committed,
                         long revisionBefore,long revisionAfter) {
        this.rows = Collections.unmodifiableList(rows);
        this.offered = offered; this.created = created; this.survivingCreated = survivingCreated;
        this.matchedExisting = matchedExisting; this.skippedDuplicate = skippedDuplicate;
        this.excluded = excluded; this.refused = refused; this.noLedgerChange = noLedgerChange;
        this.evidenceWritten = evidenceWritten; this.receiptWritten = receiptWritten;
        this.hintWritten = hintWritten; this.balanceWritten = balanceWritten;
        this.eventRecorded = eventRecorded; this.linksChanged = linksChanged;
        this.repair = repair; this.completion = completion; this.committed = committed;
        this.dbChanged = committed && revisionAfter != revisionBefore;
        this.revisionBefore = revisionBefore; this.revisionAfter = revisionAfter;
    }

    /** Totals are derived from the rows, so the primary invariant holds by construction. */
    static ImportResult of(List<Row> rows,boolean owned,long revisionBefore,long revisionAfter,Repair repair) {
        int offered=0,created=0,survivingCreated=0,matchedExisting=0,skippedDuplicate=0,excluded=0,refused=0,noLedgerChange=0;
        int evidence=0,receipts=0,hints=0,balance=0,events=0,links=0;
        for (Row row : rows) {
            offered++;
            if (row.outcome==Outcome.CREATED) { created++; if (row.surviving) survivingCreated++; }
            else if (row.outcome==Outcome.MATCHED_EXISTING) matchedExisting++;
            else if (row.outcome==Outcome.SKIPPED_DUPLICATE) skippedDuplicate++;
            else if (row.outcome==Outcome.EXCLUDED) excluded++;
            else if (row.outcome==Outcome.REFUSED) refused++;
            else noLedgerChange++;
            if (row.effects.evidenceWritten) evidence++;
            if (row.effects.receiptWritten) receipts++;
            if (row.effects.hintWritten) hints++;
            if (row.effects.balanceWritten) balance++;
            if (row.effects.eventRecorded) events++;
            if (row.effects.linksChanged) links++;
        }
        return new ImportResult(rows,offered,created,survivingCreated,matchedExisting,skippedDuplicate,excluded,refused,
                noLedgerChange,evidence,receipts,hints,balance,events,links,repair,
                owned?CaptureResult.Completion.COMMITTED:CaptureResult.Completion.PENDING_CALLER,owned,
                revisionBefore,revisionAfter);
    }

    /**
     * Selected rows whose own ledger row is no longer live after the batch, because a merge archived it:
     * repair reconciles the batch window, and a row's own write can also absorb an abbreviated copy.
     * Repair's own archive writes are counted separately as {@link Repair#archivedRows}, which may also
     * include rows the selection never named.
     */
    int archivedSelectedRows() {
        int archived=0;
        for (Row row : rows) if (row.initialTransactionId!=null && !row.surviving) archived++;
        return archived;
    }

    /**
     * The writes between two stored observations of one row; a write already stored before it is not its own.
     * The hint and commitment tables are compared by value, so an in-place update is seen.
     */
    static Effects effectsBetween(ExpenseDb.RowWrites before,ExpenseDb.RowWrites after) {
        return new Effects(after.evidence&&!before.evidence,after.receipt&&!before.receipt,!after.hints.equals(before.hints),
                after.balance&&!before.balance,after.event&&!before.event,!after.commitments.equals(before.commitments));
    }

    /** Keeps the capture vocabulary where the capture write names a reason; never invents one it cannot read. */
    static Reason reasonFrom(CaptureResult.Reason reason) {
        switch (reason) {
            case MISSING_BODY: return Reason.MISSING_BODY;
            case VERIFICATION: return Reason.VERIFICATION;
            case FUTURE_ONLY: return Reason.FUTURE_ONLY;
            case UNSUCCESSFUL: return Reason.UNSUCCESSFUL;
            case INSERT_NOT_APPLIED: return Reason.INSERT_NOT_APPLIED;
            // The new pipeline records nothing for it: for the import, the same "no ledger candidate".
            case NO_LEDGER_CANDIDATE: case NEW_PIPELINE_SKIPPED: return Reason.NO_LEDGER_CANDIDATE;
            case SERVICE_MATCH_ACCEPTED: return Reason.SERVICE_MATCH_ACCEPTED;
            case COMMITMENT_REVIEW_REQUIRED: return Reason.REVIEW_REQUIRED;
            case NOTICE_RECORDED: return Reason.REVIEW_REQUIRED;
            case NOTICE_DUPLICATE: return Reason.NOTICE_DUPLICATE;
            case EXCLUDED: return Reason.EXCLUDED;
            case INSERTED: return Reason.INSERTED;
            case EXISTING_MATCH: return Reason.EXISTING_MATCH;
        }
        throw new IllegalStateException("Unmapped capture reason: "+reason);
    }
}
