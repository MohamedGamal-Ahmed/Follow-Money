package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.SourceEvidence;

/** Source evidence on the caller's connection; retains the existing vault and source/fee/history policies. */
final class EvidenceRepository {
    private final ExpenseDb db;

    EvidenceRepository(ExpenseDb db) { this.db = db; }

    /** Runs within the capture owner's transaction; adds no transaction and never closes the helper. */
    void record(String key, long existing, String hash, String normalized, String reference,
                String channel, String label, long receivedAt, String direction, String body) {
        ContentValues evidence = new ContentValues();
        evidence.put("unique_key", key); evidence.put("transaction_id", existing); evidence.put("message_hash", hash);
        evidence.put("normalized_hash", normalized); evidence.put("reference_hash", reference);
        evidence.put("source", channel); evidence.put("sender", label); evidence.put("received_at", receivedAt);
        evidence.put("direction", direction); evidence.put("encrypted_body", EvidenceVault.encrypt(body));
        db.getWritableDatabase().insertWithOnConflict("capture_evidence", null, evidence, SQLiteDatabase.CONFLICT_REPLACE);
    }

    List<SourceEvidence> commitmentHistory(String key) {
        List<SourceEvidence> rows=new ArrayList<>();
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT sender,received_at,encrypted_body FROM commitment_observations WHERE hint_key=? ORDER BY received_at DESC LIMIT 12",new String[]{key})) {
            while(c.moveToNext()) rows.add(new SourceEvidence("SMS",c.getString(0),c.getLong(1),EvidenceVault.decrypt(c.getString(2))));
        }
        return rows;
    }
    List<SourceEvidence> commitmentHistoryFor(long recurringId) {
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT hint_key FROM recurring WHERE id=?",new String[]{Long.toString(recurringId)})) {
            if(c.moveToFirst() && !c.isNull(0)) return commitmentHistory(c.getString(0));
        }
        return new ArrayList<>();
    }
    List<SourceEvidence> sourceEvidence(long id) {
        List<SourceEvidence> rows=new ArrayList<>();
        try(Cursor manual=db.getReadableDatabase().rawQuery("SELECT note,received_at FROM transactions WHERE id=? AND unique_key LIKE 'card-statement-fee:%'",new String[]{Long.toString(id)})){
            if(manual.moveToFirst()){rows.add(new SourceEvidence("وصف يدوي من كشف البطاقة","تأكيد المستخدم",manual.getLong(1),manual.getString(0)));return rows;}
        }
        try(Cursor parent=db.getReadableDatabase().rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",new String[]{Long.toString(id)})) {
            if(parent.moveToFirst() && !parent.isNull(0)) id=parent.getLong(0);
        }
        try (Cursor c=db.getReadableDatabase().rawQuery("SELECT source,sender,received_at,encrypted_body FROM capture_evidence " +
                "WHERE transaction_id=? ORDER BY encrypted_body IS NULL,received_at DESC",new String[]{Long.toString(id)})) {
            while(c.moveToNext()) {
                EvidenceVault.Read body=EvidenceVault.read(c.getString(3));
                if(body.body!=null || body.failed) rows.add(new SourceEvidence(c.getString(0),c.getString(1),c.getLong(2),body.body,body.failed));
            }
        }
        return rows;
    }

}
