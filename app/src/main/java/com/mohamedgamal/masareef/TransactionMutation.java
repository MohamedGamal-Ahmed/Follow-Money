package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/** Orders a user-reviewed edit and optional merchant learning on the caller's borrowed connection. */
final class TransactionMutation {
    private final ExpenseDb db;

    TransactionMutation(ExpenseDb db) { this.db = db; }

    /** Primary-row no-ops never teach a rule; the returned outcome follows this owner's commit. */
    MutationResult update(long id, long amountMinor, String currency, String merchant, String category,
                Long sourceId, String kind, Long destinationId, String note, Long occurredAt, boolean learn) {
        SQLiteDatabase sql=db.getWritableDatabase();
        if(sql.inTransaction())throw new IllegalStateException("TransactionMutation must own the edit transaction");
        sql.beginTransaction();
        try {
            if(db.transactionById(id)==null){
                sql.setTransactionSuccessful();
                return new MutationResult(id,MutationResult.Outcome.NOT_FOUND);
            }
            if(db.updateTransactionRow(id,amountMinor,currency,merchant,category,sourceId,kind,destinationId,note,occurredAt)!=1){
                sql.setTransactionSuccessful();
                return new MutationResult(id,MutationResult.Outcome.NOT_APPLIED);
            }
            if(learn) db.rememberMerchantCategory(merchant,category);
            sql.setTransactionSuccessful();
            return new MutationResult(id,MutationResult.Outcome.UPDATED);
        } finally {sql.endTransaction();}
    }
}
