package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/** Owns manual source creation, its identity and the user's reviewed profile. */
final class SourceMutation {
    private final ExpenseDb db;

    SourceMutation(ExpenseDb db) { this.db = db; }

    /** Returns the new ID only after this owner's commit; failures keep the form retryable. */
    long createReviewed(String name, String kind, String last4) {
        return createReviewed(name,kind,last4,"");
    }

    long createReviewed(String bank,String kind,String last4,String displayName) {
        SQLiteDatabase sql=db.getWritableDatabase();
        if(sql.inTransaction())throw new IllegalStateException("SourceMutation must own the creation transaction");
        sql.beginTransaction();
        try {
            long id=db.addPaymentSource(bank,kind,last4);
            if(id<=0)throw new IllegalStateException("Source was not inserted");
            String identity=SourceIdentity.key(bank,last4);
            // A duplicate identity keeps its existing owner, as before. A missing identity is a failed write.
            if(identity!=null&&!db.hasSourceAlias(identity))throw new IllegalStateException("Source identity was not saved");
            if(db.updateSourceProfileRow(id,displayName,false,true)!=1)throw new IllegalStateException("Source profile was not updated");
            sql.setTransactionSuccessful();
            return id;
        } finally {sql.endTransaction();}
    }

    /** Explicit user correction only. Preserve IDs, old aliases, evidence and every financial row. */
    boolean correctBank(long id,String bank,String displayName) {
        if(bank==null||!bank.matches(".*[\\p{L}].*"))throw new IllegalArgumentException("اختار اسم البنك؛ الأرقام مكانها خانة آخر الأرقام");
        SQLiteDatabase sql=db.getWritableDatabase();
        if(sql.inTransaction())throw new IllegalStateException("SourceMutation must own the correction transaction");
        sql.beginTransaction();
        try {
            ExpenseDb.PaymentSource source=db.paymentSource(id);
            if(source==null){sql.setTransactionSuccessful();return false;}
            boolean changed=!bank.equals(source.name);
            if(changed&&(source.parentSourceId!=null||!db.linkedCards(id).isEmpty()||new CardSettlements(db).sourceRelated(id)))
                throw new IllegalArgumentException("الوسيلة مرتبطة؛ راجع وفك الربط قبل تغيير البنك");
            String identity=SourceIdentity.key(bank,source.last4);Long owner=identity==null?null:db.sourceAliasOwner(identity);
            if(changed&&owner!=null&&owner!=id)throw new IllegalArgumentException("البنك وآخر الأرقام مسجلين لوسيلة تانية؛ راجعها من حساباتك قبل التصحيح");
            if(db.updateSourceBankRow(id,bank,displayName)!=1)throw new IllegalStateException("Source was not updated");
            if(identity!=null&&owner==null){db.addSourceAlias(identity,id);if(!java.util.Objects.equals(db.sourceAliasOwner(identity),id))throw new IllegalStateException("Source alias was not saved");}
            sql.setTransactionSuccessful();return true;
        } finally {sql.endTransaction();}
    }
}
