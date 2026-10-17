package com.mohamedgamal.masareef;

import android.database.Cursor;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Transaction;

/** Read queries on the caller's connection; preserves existing bounds, ordering, row mapping and list limits. */
final class TransactionRepository {
    private final ExpenseDb db;

    TransactionRepository(ExpenseDb db) { this.db = db; }

    private static final String[] TRANSACTION_COLUMNS = {
            "id", "occurred_at", "amount_minor", "currency", "merchant", "category", "source", "payment_source_id", "card_last4", "kind", "destination_source_id", "note", "needs_review", "time_precision"
    };

    List<Transaction> transactions() {
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = db.getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                null, null, null, null, "occurred_at DESC", "500")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
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

}
