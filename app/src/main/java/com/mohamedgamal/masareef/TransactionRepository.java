package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Transaction storage on the caller's connection; preserves existing guards, queries and transaction policy. */
final class TransactionRepository {
    private final ExpenseDb db;

    TransactionRepository(ExpenseDb db) { this.db = db; }

    private static final String[] TRANSACTION_COLUMNS = {
            "id", "occurred_at", "amount_minor", "currency", "merchant", "category", "source", "payment_source_id", "card_last4", "kind", "destination_source_id", "note", "needs_review", "time_precision"
    };

    List<Transaction> transactions() {
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                null, null, null, null, "occurred_at DESC,id ASC", "500")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    /** The home page uses the same ledger order and 500-row display window. */
    List<Transaction> recentTransactions(int limit) {
        List<Transaction> result = new ArrayList<>();
        if (limit <= 0) return result;
        try (Cursor cursor = db.getReadableDatabase().rawQuery(
                "SELECT " + String.join(",", TRANSACTION_COLUMNS) +
                        " FROM transactions ORDER BY occurred_at DESC,id ASC LIMIT ?",
                new String[]{Integer.toString(Math.min(limit, 500))})) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    List<Transaction> recentSourceTransactions(long sourceId) {
        List<Transaction> result=new ArrayList<>();
        try(Cursor cursor=db.getReadableDatabase().query("transactions",TRANSACTION_COLUMNS,
                "payment_source_id=? OR (kind='transfer' AND destination_source_id=?)",new String[]{Long.toString(sourceId),Long.toString(sourceId)},
                null,null,"occurred_at DESC,id DESC","3")) {
            while(cursor.moveToNext())result.add(new Transaction(cursor.getLong(0),cursor.getLong(1),cursor.getLong(2),cursor.getString(3),cursor.getString(4),
                    cursor.getString(5),cursor.getString(6),cursor.isNull(7)?null:cursor.getLong(7),cursor.getString(8),cursor.getString(9),
                    cursor.isNull(10)?null:cursor.getLong(10),cursor.getString(11),cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    /**
     * Expenses from the last year, newest first: recurring-payment suggestions only compare payments
     * about a month apart, so they never need the whole ledger.
     */
    List<Transaction> recurringCandidateTransactions() {
        List<Transaction> result = new ArrayList<>();
        long since = java.time.LocalDate.now().minusMonths(12).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                "kind='expense' AND occurred_at>=?", new String[]{Long.toString(since)}, null, null, "occurred_at DESC", null)) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    /** Uncapped ledger count; UI lists intentionally show only the latest 500 rows. */
    long transactionCount() {
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transactions",null)) {
            c.moveToFirst();return c.getLong(0);
        }
    }

    /** The row inserted most recently, used for the instant capture notification. */
    Transaction latestInserted() {
        return transactionWhere(null, null, "id DESC");
    }

    Transaction transactionById(long id) {
        return transactionWhere("id=?", new String[]{Long.toString(id)}, null);
    }

    private Transaction transactionWhere(String where, String[] args, String order) {
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                where, args, null, null, order, "1")) {
            if (!cursor.moveToFirst()) return null;
            return new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13));
        }
    }

    List<Transaction> transactionsForMonth(LocalDate month) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                "occurred_at>=? AND occurred_at<?", new String[]{Long.toString(start), Long.toString(end)},
                null, null, "occurred_at DESC")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive) {
        return transactionsBetween(startDate,endExclusive,ZoneId.systemDefault());
    }

    List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive,ZoneId zone) {
        long start = startDate.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                "occurred_at>=? AND occurred_at<?", new String[]{Long.toString(start), Long.toString(end)},
                null, null, "occurred_at DESC,id DESC")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    int update(long id, long amountMinor, String currency, String merchant,
                             String category, Long sourceId, String kind, Long destinationId,
                             String note, Long occurredAt) {
        if (amountMinor <= 0 || merchant.isBlank()) return 0;
        if(new CardSettlements(db).related(id))throw new IllegalArgumentException("Undo card repayment links before editing");
        try(Cursor fee=db.getReadableDatabase().rawQuery("SELECT p.currency,p.payment_source_id FROM transactions f JOIN transactions p ON p.id=f.fee_parent_id WHERE f.id=? AND f.unique_key LIKE 'card-statement-fee:%'",new String[]{Long.toString(id)})){
            if(fee.moveToFirst()&&(!currency.equals(fee.getString(0))||sourceId==null||fee.isNull(1)||sourceId!=fee.getLong(1)||kind!=null&&!"expense".equals(kind)))throw new IllegalArgumentException("Statement fee must keep the purchase currency and card");
        }
        ContentValues values = new ContentValues();
        values.put("amount_minor", amountMinor);
        values.put("currency", currency);
        values.put("merchant", merchant.trim());
        values.put("category", category);
        values.put("user_edited", 1);
        values.put("needs_review", 0);
        if (sourceId == null) values.putNull("payment_source_id"); else values.put("payment_source_id", sourceId);
        if (kind != null) values.put("kind", kind);
        if (destinationId == null) values.putNull("destination_source_id");
        else values.put("destination_source_id", destinationId);
        if (note != null) values.put("note", note.trim());
        if (occurredAt != null) values.put("occurred_at", occurredAt);
        SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try {
            int affected=sql.update("transactions", values, "id=?", new String[]{Long.toString(id)});
            sql.setTransactionSuccessful();
            return affected;
        } finally { sql.endTransaction(); }
    }

    void delete(long id) {
        if(new CardSettlements(db).related(id))throw new IllegalArgumentException("Undo card repayment allocations before deleting");
        SQLiteDatabase writable = db.getWritableDatabase();
        writable.beginTransaction();
        try (Cursor cursor = writable.rawQuery("SELECT unique_key FROM transactions WHERE id=?",
                new String[]{Long.toString(id)})) {
            if (cursor.moveToFirst() && !cursor.getString(0).startsWith("manual:")) {
                ContentValues values = new ContentValues();
                values.put("unique_key", cursor.getString(0));
                writable.insertWithOnConflict("excluded_messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            deleteDependentRows(id);
            writable.delete("transactions", "id=?", new String[]{Long.toString(id)});
            writable.execSQL("INSERT OR IGNORE INTO excluded_messages SELECT unique_key FROM capture_evidence WHERE transaction_id=?", new Object[]{id});
            writable.delete("capture_evidence", "transaction_id=?", new String[]{Long.toString(id)});
            writable.setTransactionSuccessful();
        } finally {
            writable.endTransaction();
        }
    }

    void deleteDependentRows(long id) {
        SQLiteDatabase writable=db.getWritableDatabase();
        CardSettlements.removeRelations(writable,id);
        writable.delete("transactions","fee_parent_id=?",new String[]{Long.toString(id)});
        writable.delete("reimbursements","income_id=? OR expense_id=?",new String[]{Long.toString(id),Long.toString(id)});
    }

}
