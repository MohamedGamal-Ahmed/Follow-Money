package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/** Owns manual source creation, its identity and the user's reviewed profile. */
final class SourceMutation {
    private final ExpenseDb db;

    SourceMutation(ExpenseDb db) { this.db = db; }

    /** Returns the new ID only after this owner's commit; failures keep the form retryable. */
    long createReviewed(String name, String kind, String last4) {
        SQLiteDatabase sql=db.getWritableDatabase();
        if(sql.inTransaction())throw new IllegalStateException("SourceMutation must own the creation transaction");
        sql.beginTransaction();
        try {
            long id=db.addPaymentSource(name,kind,last4);
            if(id<=0)throw new IllegalStateException("Source was not inserted");
            String identity=SourceIdentity.key(name,last4);
            // A duplicate identity keeps its existing owner, as before. A missing identity is a failed write.
            if(identity!=null&&!db.hasSourceAlias(identity))throw new IllegalStateException("Source identity was not saved");
            if(db.updateSourceProfileRow(id,"",false,true)!=1)throw new IllegalStateException("Source profile was not updated");
            sql.setTransactionSuccessful();
            return id;
        } finally {sql.endTransaction();}
    }
}
