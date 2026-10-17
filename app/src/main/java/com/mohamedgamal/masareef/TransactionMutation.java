package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/** Orders a user-reviewed edit and optional merchant learning on the caller's borrowed connection. */
final class TransactionMutation {
    private final ExpenseDb db;

    TransactionMutation(ExpenseDb db) { this.db = db; }

    /** Keeps the edit and its future-capture rule atomic; validation and UI feedback remain with the caller. */
    void update(long id, long amountMinor, String currency, String merchant, String category,
                Long sourceId, String kind, Long destinationId, String note, Long occurredAt, boolean learn) {
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            db.updateTransaction(id,amountMinor,currency,merchant,category,sourceId,kind,destinationId,note,occurredAt);
            if(learn) db.rememberMerchantCategory(merchant,category);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }
}
