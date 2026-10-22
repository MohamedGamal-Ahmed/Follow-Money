package com.mohamedgamal.masareef;

import java.util.List;

/** The approved import as the screen runs it (ImportBatch.executeDetailed), reported as the ledger rows it added. */
final class ImportRun {
    static long added(ExpenseDb db, List<SmsPreview> rows) {
        long before = db.transactionCount();
        new ImportBatch(db).executeDetailed(rows);
        return db.transactionCount() - before;
    }

    private ImportRun() {}
}
