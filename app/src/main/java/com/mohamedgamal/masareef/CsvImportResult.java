package com.mohamedgamal.masareef;

import java.util.Collections;
import java.util.List;

/**
 * What one CSV file actually did, reported by {@link ExpenseDb#importCsvBatchDetailed}. The legacy
 * {@code CsvBatchResult} keeps its documented meaning ({@code duplicates} = already present plus excluded)
 * and is derived from this result instead of being counted a second way.
 */
final class CsvImportResult {
    /** Primary outcome of one parsed CSV row. */
    enum Outcome { CREATED, ALREADY_PRESENT, EXCLUDED }

    /** One parsed row: its primary outcome and the ledger row it names, when one exists. */
    static final class Row {
        final CsvImport.Row source;
        final Outcome outcome;
        final Long transactionId;
        Row(CsvImport.Row source,Outcome outcome,Long transactionId) {
            this.source = source; this.outcome = outcome; this.transactionId = transactionId;
        }
    }

    final int created, alreadyPresent, excluded;
    final CaptureResult.Completion completion;
    /** True only when this call owned the transaction and closed it successfully, with no outer transaction. */
    final boolean committed;
    /** Observed global revision delta; false for a caller-owned transaction even while its writes are in flight. */
    final boolean dbChanged;
    final long revisionBefore, revisionAfter;
    final List<Row> rows;

    CsvImportResult(List<Row> rows,int created,int alreadyPresent,int excluded,boolean owned,
                    long revisionBefore,long revisionAfter) {
        this.rows = Collections.unmodifiableList(rows);
        this.created = created; this.alreadyPresent = alreadyPresent; this.excluded = excluded;
        this.completion = owned ? CaptureResult.Completion.COMMITTED : CaptureResult.Completion.PENDING_CALLER;
        this.committed = owned;
        this.dbChanged = owned && revisionAfter != revisionBefore;
        this.revisionBefore = revisionBefore; this.revisionAfter = revisionAfter;
    }
}
