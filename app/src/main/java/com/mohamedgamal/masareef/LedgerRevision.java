package com.mohamedgamal.masareef;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** A persistent invalidation token, updated atomically with each tracked row write (not an event count). */
final class LedgerRevision {
    private LedgerRevision(){ }
    private static final String[] TABLES={
            "transactions","excluded_messages","recurring","source_aliases","payment_sources",
            "budgets","balance_snapshots","merchant_rules","budget_plan_categories","budget_plans",
            "assets","debts","own_handles","capture_evidence","transaction_archive","commitment_suggestions",
            "commitment_observations","reimbursements","dismissed_patterns","repair_log","sms_import_receipts",
            "capture_events","card_settlement_allocations","card_settlements","commitment_cycles","commitment_payments"};

    static void create(SQLiteDatabase sql){
        sql.execSQL("CREATE TABLE IF NOT EXISTS ledger_revision(id INTEGER PRIMARY KEY CHECK(id=1),revision INTEGER NOT NULL)");
        sql.execSQL("INSERT OR IGNORE INTO ledger_revision(id,revision) VALUES(1,0)");
        // Only fixed schema identifiers are interpolated; every trigger shares the financial writer's transaction.
        for(String table:TABLES)for(String operation:new String[]{"INSERT","UPDATE","DELETE"})
            sql.execSQL("CREATE TRIGGER IF NOT EXISTS ledger_revision_"+table+"_"+operation+
                    " AFTER "+operation+" ON "+table+" BEGIN UPDATE ledger_revision SET revision=revision+1 WHERE id=1; END");
    }

    static long read(SQLiteDatabase sql){
        try(Cursor cursor=sql.rawQuery("SELECT revision FROM ledger_revision WHERE id=1",null)){
            if(!cursor.moveToFirst())throw new IllegalStateException("Ledger revision is missing");
            return cursor.getLong(0);
        }
    }
}
