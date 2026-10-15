package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.Telephony;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public final class ExpenseDb extends SQLiteOpenHelper {
    private static final String[] TRANSACTION_COLUMNS = {
            "id", "occurred_at", "amount_minor", "currency", "merchant", "category", "source", "payment_source_id", "card_last4", "kind", "destination_source_id", "note", "needs_review", "time_precision"
    };

    public ExpenseDb(Context context) {
        super(context, "masareef.db", null, 21);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, unique_key TEXT UNIQUE NOT NULL, " +
                "occurred_at INTEGER NOT NULL, received_at INTEGER NOT NULL, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, " +
                "merchant TEXT NOT NULL, category TEXT NOT NULL, source TEXT NOT NULL, message_hash TEXT, " +
                "payment_source_id INTEGER, card_last4 TEXT, kind TEXT NOT NULL DEFAULT 'expense', " +
                "destination_source_id INTEGER, note TEXT, user_edited INTEGER NOT NULL DEFAULT 0, needs_review INTEGER NOT NULL DEFAULT 0, fee_parent_id INTEGER, " +
                "time_precision TEXT CHECK(time_precision IN ('exact','date_only')))");
        db.execSQL("CREATE INDEX transaction_date ON transactions(occurred_at DESC)");
        db.execSQL("CREATE INDEX transaction_message_hash ON transactions(message_hash, occurred_at)");
        db.execSQL("CREATE TABLE excluded_messages (unique_key TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE recurring (id INTEGER PRIMARY KEY, merchant TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, next_due TEXT NOT NULL, " +
                "last_alert_date TEXT, cycle TEXT NOT NULL DEFAULT 'monthly', " +
                "kind TEXT NOT NULL DEFAULT 'subscription', total_count INTEGER, paid_count INTEGER NOT NULL DEFAULT 0, " +
                "remind_days INTEGER NOT NULL DEFAULT 1, match_text TEXT, payment_source_id INTEGER, last_paid_date TEXT, " +
                "status TEXT NOT NULL DEFAULT 'active', due_day INTEGER, needs_review INTEGER NOT NULL DEFAULT 0, hint_key TEXT, last_completion_hash TEXT, UNIQUE(merchant, amount_minor, currency))");
        db.execSQL("CREATE TABLE payment_sources (id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, last4 TEXT, " +
                "parent_source_id INTEGER, display_name TEXT, archived INTEGER NOT NULL DEFAULT 0, reviewed INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE source_aliases (identity TEXT PRIMARY KEY, payment_source_id INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE budgets (id INTEGER PRIMARY KEY, month TEXT NOT NULL, category TEXT NOT NULL, " +
                "currency TEXT NOT NULL, amount_minor INTEGER NOT NULL, UNIQUE(month, category, currency))");
        db.execSQL("CREATE TABLE balance_snapshots (id INTEGER PRIMARY KEY, payment_source_id INTEGER NOT NULL, " +
                "currency TEXT NOT NULL, balance_minor INTEGER NOT NULL, occurred_at INTEGER NOT NULL, " +
                "received_at INTEGER NOT NULL, source TEXT NOT NULL, unique_key TEXT UNIQUE NOT NULL)");
        db.execSQL("CREATE INDEX balance_snapshot_order ON balance_snapshots(payment_source_id,currency,occurred_at DESC,received_at DESC)");
        db.execSQL("CREATE TABLE merchant_rules (id INTEGER PRIMARY KEY, match_text TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                "category TEXT NOT NULL, ignored INTEGER NOT NULL DEFAULT 0)");
        createBudgetPlans(db);
        createWealthTables(db);
        db.execSQL("CREATE TABLE own_handles (handle TEXT PRIMARY KEY)");
        createEvidenceTables(db);
        db.execSQL("ALTER TABLE recurring ADD COLUMN origin_transaction_id INTEGER");
        createRepairTables(db);
        createReimbursements(db);
        db.execSQL("ALTER TABLE recurring ADD COLUMN last_paid_due TEXT");
    }

    /** v20: an incoming payment that returns money spent on someone else's behalf, linked to that purchase. */
    private static void createReimbursements(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS reimbursements (income_id INTEGER PRIMARY KEY, expense_id INTEGER NOT NULL, amount_minor INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS reimbursement_expense ON reimbursements(expense_id)");
    }

    /** v19: suggestions the user dismissed, and a log of automatic data repairs (aggregates only, no message text). */
    private static void createRepairTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dismissed_patterns (pattern_key TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE IF NOT EXISTS repair_log (id INTEGER PRIMARY KEY, ran_at INTEGER NOT NULL, " +
                "version INTEGER NOT NULL, promotions INTEGER NOT NULL, duplicates INTEGER NOT NULL, own_transfers INTEGER NOT NULL, " +
                "moved_minor INTEGER NOT NULL, fees_minor INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<17) db.execSQL("ALTER TABLE recurring ADD COLUMN origin_transaction_id INTEGER");
        if (oldVersion < 2 && newVersion >= 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN message_hash TEXT");
            db.execSQL("CREATE INDEX transaction_message_hash ON transactions(message_hash, occurred_at)");
        }
        if (oldVersion < 3 && newVersion >= 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN payment_source_id INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN card_last4 TEXT");
            db.execSQL("CREATE TABLE payment_sources (id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, last4 TEXT)");
            db.execSQL("CREATE TABLE budgets (id INTEGER PRIMARY KEY, month TEXT NOT NULL, category TEXT NOT NULL, " +
                    "currency TEXT NOT NULL, amount_minor INTEGER NOT NULL, UNIQUE(month, category, currency))");
        }
        if (oldVersion < 4 && newVersion >= 4) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT 'expense'");
            db.execSQL("ALTER TABLE transactions ADD COLUMN destination_source_id INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT");
            db.execSQL("CREATE TABLE balance_snapshots (id INTEGER PRIMARY KEY, payment_source_id INTEGER NOT NULL, " +
                    "currency TEXT NOT NULL, balance_minor INTEGER NOT NULL, occurred_at INTEGER NOT NULL, " +
                    "received_at INTEGER NOT NULL, source TEXT NOT NULL, unique_key TEXT UNIQUE NOT NULL)");
            db.execSQL("CREATE INDEX balance_snapshot_order ON balance_snapshots(payment_source_id,currency,occurred_at DESC,received_at DESC)");
            db.execSQL("CREATE TABLE merchant_rules (id INTEGER PRIMARY KEY, match_text TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                    "category TEXT NOT NULL, ignored INTEGER NOT NULL DEFAULT 0)");
        }
        if (oldVersion < 5 && newVersion >= 5) {
            createBudgetPlans(db);
            db.execSQL("INSERT INTO budget_plans(name,amount_minor,currency,cycle,anchor_date,duration_days) " +
                    "SELECT category,amount_minor,currency,'once',month || '-01'," +
                    "CAST(strftime('%d',date(month || '-01','+1 month','-1 day')) AS INTEGER) FROM budgets");
            db.execSQL("INSERT INTO budget_plan_categories(plan_id,category) " +
                    "SELECT p.id,b.category FROM budgets b JOIN budget_plans p ON p.name=b.category " +
                    "AND p.currency=b.currency AND p.anchor_date=b.month || '-01'");
        }
        if (oldVersion < 6 && newVersion >= 6) {
            db.execSQL("ALTER TABLE recurring ADD COLUMN cycle TEXT NOT NULL DEFAULT 'monthly'");
            db.execSQL("ALTER TABLE recurring ADD COLUMN kind TEXT NOT NULL DEFAULT 'subscription'");
        }
        if (oldVersion < 7 && newVersion >= 7) createWealthTables(db);
        if (oldVersion < 8 && newVersion >= 8) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN received_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE transactions SET received_at=occurred_at WHERE received_at=0");
            mergeDuplicateAutomaticSources(db);
        }
        if (oldVersion < 9 && newVersion >= 9) {
            db.execSQL("CREATE TABLE source_aliases (identity TEXT PRIMARY KEY, payment_source_id INTEGER NOT NULL)");
            try (Cursor cursor = db.rawQuery("SELECT id,name,last4 FROM payment_sources ORDER BY id", null)) {
                while (cursor.moveToNext()) {
                    String identity = SourceIdentity.key(cursor.getString(1), cursor.getString(2));
                    if (identity == null) continue;
                    ContentValues values = new ContentValues();
                    values.put("identity", identity);
                    values.put("payment_source_id", cursor.getLong(0));
                    db.insertWithOnConflict("source_aliases", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
        }
        if (oldVersion < 10 && newVersion >= 10) {
            db.execSQL("ALTER TABLE recurring ADD COLUMN total_count INTEGER");
            db.execSQL("ALTER TABLE recurring ADD COLUMN paid_count INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE recurring ADD COLUMN remind_days INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE recurring ADD COLUMN match_text TEXT");
            db.execSQL("ALTER TABLE recurring ADD COLUMN payment_source_id INTEGER");
            db.execSQL("ALTER TABLE recurring ADD COLUMN last_paid_date TEXT");
            db.execSQL("ALTER TABLE recurring ADD COLUMN status TEXT NOT NULL DEFAULT 'active'");
            db.execSQL("ALTER TABLE recurring ADD COLUMN due_day INTEGER");
            db.execSQL("UPDATE recurring SET due_day=CAST(strftime('%d', next_due) AS INTEGER)");
            db.execSQL("UPDATE recurring SET kind='installment' WHERE kind='loan'");
            db.execSQL("UPDATE recurring SET kind='bill' WHERE kind='commitment'");
        }
        if (oldVersion < 12 && newVersion >= 12)
            db.execSQL("CREATE TABLE IF NOT EXISTS own_handles (handle TEXT PRIMARY KEY)");
        if (oldVersion < 13 && newVersion >= 13)
            db.execSQL("UPDATE transactions SET merchant='" + ExpenseParser.INSTANT_TRANSFER + "' " +
                    "WHERE merchant LIKE 'حساب رقم%' OR merchant='HSBC' AND category='تحويلات'");
        if (oldVersion < 14) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN user_edited INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE transactions ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE recurring ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0");
            createEvidenceTables(db);
            db.execSQL("INSERT INTO capture_evidence(unique_key,transaction_id,message_hash,source,received_at,direction) " +
                    "SELECT unique_key,id,message_hash,source,received_at,CASE WHEN kind='transfer' AND payment_source_id IS NULL " +
                    "THEN 'income' WHEN kind='transfer' THEN 'expense' ELSE kind END FROM transactions WHERE message_hash IS NOT NULL");
            db.execSQL("UPDATE recurring SET needs_review=1 WHERE merchant='HSBC' OR merchant LIKE 'حساب رقم%' OR merchant='تحويل لحظي'");
        }
        if(oldVersion<15) db.execSQL("ALTER TABLE transactions ADD COLUMN fee_parent_id INTEGER");
        if(oldVersion<16) {
            if(oldVersion>=14) {
            db.execSQL("ALTER TABLE commitment_suggestions ADD COLUMN currency TEXT NOT NULL DEFAULT 'EGP'");
            db.execSQL("ALTER TABLE commitment_suggestions ADD COLUMN cycle TEXT NOT NULL DEFAULT 'monthly'");
            db.execSQL("ALTER TABLE commitment_suggestions ADD COLUMN date_exact INTEGER NOT NULL DEFAULT 0");
            }
            db.execSQL("ALTER TABLE recurring ADD COLUMN hint_key TEXT");
            db.execSQL("ALTER TABLE recurring ADD COLUMN last_completion_hash TEXT");
            createCommitmentObservations(db);
        }
        if (oldVersion < 18) {
            db.execSQL("ALTER TABLE payment_sources ADD COLUMN parent_source_id INTEGER");
            db.execSQL("ALTER TABLE payment_sources ADD COLUMN display_name TEXT");
            db.execSQL("ALTER TABLE payment_sources ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE payment_sources ADD COLUMN reviewed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE payment_sources SET kind='حساب' WHERE kind='حساب بنكي'");
            db.execSQL("UPDATE payment_sources SET kind='بطاقة' WHERE kind='كارت'");
        }
        if (oldVersion < 19) createRepairTables(db);
        if (oldVersion < 20) {
            createReimbursements(db);
            db.execSQL("ALTER TABLE recurring ADD COLUMN last_paid_due TEXT");
        }
        if(oldVersion<21)db.execSQL("ALTER TABLE transactions ADD COLUMN time_precision TEXT CHECK(time_precision IN ('exact','date_only'))");
    }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Guard against a partially migrated database; creating the table is a no-op when it exists.
        if (!db.isReadOnly()) {
            db.execSQL("CREATE TABLE IF NOT EXISTS own_handles (handle TEXT PRIMARY KEY)");
        }
    }

    private static void createWealthTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE assets (id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                "value_minor INTEGER NOT NULL, currency TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE debts (id INTEGER PRIMARY KEY, name TEXT NOT NULL, direction TEXT NOT NULL, " +
                "original_minor INTEGER NOT NULL, remaining_minor INTEGER NOT NULL, currency TEXT NOT NULL, " +
                "due_date TEXT)");
    }

    private static void createEvidenceTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE capture_evidence(unique_key TEXT PRIMARY KEY, transaction_id INTEGER NOT NULL, " +
                "message_hash TEXT, normalized_hash TEXT, reference_hash TEXT, source TEXT NOT NULL, sender TEXT, " +
                "received_at INTEGER NOT NULL, direction TEXT, encrypted_body TEXT)");
        db.execSQL("CREATE INDEX evidence_transaction ON capture_evidence(transaction_id)");
        db.execSQL("CREATE INDEX evidence_hash ON capture_evidence(message_hash,received_at)");
        db.execSQL("CREATE TABLE commitment_suggestions(hint_key TEXT PRIMARY KEY, merchant TEXT NOT NULL, kind TEXT NOT NULL, amount_minor INTEGER NOT NULL, total_count INTEGER, estimated_date TEXT NOT NULL, explanation TEXT NOT NULL, sender TEXT, received_at INTEGER NOT NULL, encrypted_body TEXT, status TEXT NOT NULL DEFAULT 'pending', currency TEXT NOT NULL DEFAULT 'EGP', cycle TEXT NOT NULL DEFAULT 'monthly', date_exact INTEGER NOT NULL DEFAULT 0)");
        createCommitmentObservations(db);
        db.execSQL("CREATE TABLE transaction_archive(id INTEGER PRIMARY KEY, original_id INTEGER NOT NULL, " +
                "reason TEXT NOT NULL, archived_at INTEGER NOT NULL, payload TEXT NOT NULL)");
    }

    private static void createCommitmentObservations(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS commitment_observations(message_key TEXT PRIMARY KEY, hint_key TEXT NOT NULL, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, due_date TEXT NOT NULL, sender TEXT, received_at INTEGER NOT NULL, encrypted_body TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS commitment_observation_order ON commitment_observations(hint_key,received_at DESC)");
    }

    private static void createBudgetPlans(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE budget_plans (id INTEGER PRIMARY KEY, name TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, cycle TEXT NOT NULL, " +
                "anchor_date TEXT NOT NULL, duration_days INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE budget_plan_categories (plan_id INTEGER NOT NULL, category TEXT NOT NULL, " +
                "PRIMARY KEY(plan_id,category))");
    }

    public boolean addSms(String sender, String body, long when) {
        return addAutomatic(sender, sender, body, when, "SMS");
    }

    /** Receipt aliases are safe only if no other inbox SMS has that exact sender/body/sent-time identity. */
    static Set<String> smsSentKeys(Context context, long since, long until) {
        Set<String> keys=new HashSet<>();
        try(Cursor c=context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE_SENT},
                Telephony.Sms.DATE_SENT+">=? AND "+Telephony.Sms.DATE_SENT+"<=?",
                new String[]{Long.toString(since),Long.toString(until)},null)) {
            if(c==null)throw new IllegalStateException("SMS identities unavailable");
            while(c.moveToNext())if(c.getString(1)!=null)keys.add(CaptureIdentity.smsKey(c.getString(0),c.getString(1),c.getLong(2)));
        }
        return keys;
    }

    /** DATE_SENT is the PDU timestamp used by live capture; DATE is only the inbox receipt time. */
    public boolean addSms(String sender, String body, long receivedAt, long sentAt) {
        return addSms(sender, body, receivedAt, sentAt, false);
    }

    public boolean addSms(String sender, String body, long receivedAt, long sentAt, boolean receiptKeySafe) {
        long when=CaptureIdentity.smsTimestamp(receivedAt, sentAt);
        return addAutomatic(sender, sender, body, when, "SMS", receiptKeySafe ? receivedAt : when);
    }

    /** Read-only status of one inbox SMS; used to audit capture coverage on any phone. */
    public int smsCaptureStatus(String sender, String body, long when) {
        return smsCaptureStatus(sender, body, when, when);
    }

    public int smsCaptureStatus(String sender, String body, long receivedAt, long sentAt) {
        return smsCaptureStatus(sender, body, receivedAt, sentAt, false);
    }

    public int smsCaptureStatus(String sender, String body, long receivedAt, long sentAt, boolean receiptKeySafe) {
        if (body == null) return 0;
        String key = sha256(sender + "\n" + CaptureIdentity.smsTimestamp(receivedAt, sentAt) + "\n" + body);
        String legacyKey = receiptKeySafe ? sha256(sender + "\n" + receivedAt + "\n" + body) : key;
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM capture_evidence WHERE unique_key IN (?,?) AND source='SMS'", new String[]{key,legacyKey})) {
            if (c.moveToFirst()) return 1;
        }
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM excluded_messages WHERE unique_key IN (?,?)", new String[]{key,legacyKey})) {
            if (c.moveToFirst()) return 2;
        }
        return 0;
    }

    public boolean addNotification(String packageName, String appLabel, String body, long when) {
        return addAutomatic(packageName, appLabel, body, when, "إشعار: " + appLabel);
    }

    private boolean addAutomatic(String sourceKey, String label, String body, long receivedAt, String channel) {
        return addAutomatic(sourceKey, label, body, receivedAt, channel, receivedAt);
    }

    private boolean addAutomatic(String sourceKey, String label, String body, long receivedAt, String channel, long legacyReceivedAt) {
        boolean added = addAutomaticCapture(sourceKey, label, body, receivedAt, channel, legacyReceivedAt);
        // The other leg of an own transfer, or the bank copy of an app notification, may have just arrived.
        if (added) repairAround(receivedAt);
        return added;
    }

    private long lastCapturedId = -1;

    /** The row the latest automatic capture created, followed through any merge the repair made right after it. */
    public Transaction lastCaptured() { return lastCapturedId < 0 ? null : transactionById(lastCapturedId); }

    private boolean addAutomaticCapture(String sourceKey, String label, String body, long receivedAt, String channel, long legacyReceivedAt) {
        if (body == null || ExpenseParser.isVerificationMessage(body)) return false;
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            String key = sha256(sourceKey + "\n" + receivedAt + "\n" + body);
            String legacyKey = sha256(sourceKey + "\n" + legacyReceivedAt + "\n" + body);
            try(Cursor excluded=writable.rawQuery("SELECT 1 FROM excluded_messages WHERE unique_key IN (?,?)",new String[]{key,legacyKey})) {
                if(excluded.moveToFirst()) {writable.setTransactionSuccessful();return false;}
            }
            String hash = sha256(sourceKey + "\n" + body.trim());
            ExpenseParser.EventTime event=ExpenseParser.eventTimeWithPrecision(body,receivedAt);
            long eventAt = event.when;
            if (ExpenseParser.isFutureOnly(body)) {
                markRejectedCaptureForReview(hash, eventAt);
                writable.setTransactionSuccessful();
                return false;
            }
            if (ExpenseParser.isUnsuccessful(body)) {
                markRejectedCaptureForReview(hash, eventAt);
                boolean cancelled = applyCancellation(label, body, receivedAt);
                writable.setTransactionSuccessful();
                return cancelled;
            }
            recordHint(label,body,receivedAt);
            recordBalanceFromMessage(label, body, receivedAt);
            ExpenseParser.Transfer transfer = ExpenseParser.parseTransfer(body);
            ExpenseParser.Parsed parsed = ExpenseParser.parse(label, body);
            String direction = "expense";
            if (parsed == null) { parsed = ExpenseParser.parseIncome(label, body); direction = "income"; }
            if (transfer != null) {
                direction = transfer.incoming ? "income" : "expense";
                parsed = new ExpenseParser.Parsed(transfer.amountMinor, "EGP", transfer.counterpart,
                        transfer.incoming ? "دخل" : "تحويلات", parsed == null ? null : parsed.cardLast4);
            }
            if (parsed == null) {
                markRejectedCaptureForReview(hash, eventAt);
                String service = ExpenseParser.paidService(body);
                boolean paid = service != null && markServicePaid(service, receivedAt);
                writable.setTransactionSuccessful(); return paid;
            }
            Long account = autoSource(label, parsed.cardLast4, body);
            String normalized = CaptureIdentity.normalized(label, body);
            String reference = CaptureIdentity.reference(label, body);
            long existing = captureRow(key, hash, normalized, reference, parsed, direction, eventAt, account);
            // Upgrade an import saved by an older version without merging unrelated purchases.
            if (existing < 0 && !key.equals(legacyKey)) existing = rowForKey(legacyKey);
            long prefixRow = "SMS".equals(channel) ? legacyTruncatedRow(sourceKey,body,eventAt,parsed,direction,account,reference) : -1;
            if (prefixRow >= 0 && compatibleReference(prefixRow, reference)) {
                if (existing < 0 || existing == prefixRow || mergeEntries(prefixRow, existing, "same SMS: abbreviated and complete source"))
                    existing = prefixRow;
            }
            boolean added = false;
            if (existing >= 0) syncAutomatic(existing, parsed, direction, account, body, eventAt,event.precision);
            else {
                String kind = transfer != null && isOwnHandle(transfer.counterpart) ? "transfer" : direction;
                added = insertEntry(key, eventAt, parsed.amountMinor, parsed.currency, parsed.merchant,
                        "transfer".equals(kind) ? "تحويلات" : parsed.category, channel, hash, parsed.cardLast4,
                        "transfer".equals(kind) && "income".equals(direction) ? null : account, kind,
                        "transfer".equals(kind) && "income".equals(direction) ? account : null, null, receivedAt);
                if (added) {
                    existing = latestInserted().id; lastCapturedId = existing;
                    ContentValues precision=new ContentValues();precision.put("time_precision",event.precision);
                    writable.update("transactions",precision,"id=?",new String[]{Long.toString(existing)});
                }
            }
            if (existing >= 0) {
                ContentValues evidence = new ContentValues();
                evidence.put("unique_key", key); evidence.put("transaction_id", existing); evidence.put("message_hash", hash);
                evidence.put("normalized_hash", normalized); evidence.put("reference_hash", reference);
                evidence.put("source", channel); evidence.put("sender", label); evidence.put("received_at", receivedAt);
                evidence.put("direction", direction); evidence.put("encrypted_body", EvidenceVault.encrypt(body));
                writable.insertWithOnConflict("capture_evidence", null, evidence, SQLiteDatabase.CONFLICT_REPLACE);
                if (added && "expense".equals(direction) && !linkTransferToCommitment(existing))
                    advanceRecurring(parsed.merchant, parsed.amountMinor, parsed.currency, receivedAt);
            }
            writable.setTransactionSuccessful(); return added;
        } finally { writable.endTransaction(); }
    }

    private long rowForHash(String hash, long at) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t.id FROM transactions t LEFT JOIN capture_evidence e ON e.transaction_id=t.id " +
                "WHERE (t.message_hash=? OR e.message_hash=?) AND t.occurred_at BETWEEN ? AND ? ORDER BY t.id LIMIT 1",
                new String[]{hash, hash, Long.toString(at-120000), Long.toString(at+120000)})) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    private long rowForKey(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t.id FROM transactions t LEFT JOIN capture_evidence e ON e.transaction_id=t.id " +
                "WHERE t.unique_key=? OR e.unique_key=? ORDER BY t.id LIMIT 1", new String[]{key,key})) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    private long legacyTruncatedRow(String sender,String body,long at,ExpenseParser.Parsed parsed,String direction,Long account,String reference) {
        if(reference==null || account==null) return -1;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT t.id,t.message_hash FROM transactions t JOIN capture_evidence e ON e.transaction_id=t.id " +
                "WHERE t.source='SMS' AND e.encrypted_body IS NULL AND t.amount_minor=? AND t.currency=? AND e.direction=? " +
                "AND (t.payment_source_id=? OR t.destination_source_id=?) AND t.occurred_at BETWEEN ? AND ? ORDER BY t.id",
                new String[]{Long.toString(parsed.amountMinor),parsed.currency,direction,Long.toString(account),Long.toString(account),Long.toString(at-120000),Long.toString(at+120000)})) {
            while(c.moveToNext()) if(compatibleReference(c.getLong(0),reference) && CaptureIdentity.isTruncatedHash(sender,body,c.getString(1))) return c.getLong(0);
        }
        return -1;
    }

    private boolean compatibleReference(long id, String reference) {
        if (reference == null) return true;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM capture_evidence WHERE transaction_id=? " +
                "AND reference_hash IS NOT NULL AND reference_hash<>? LIMIT 1", new String[]{Long.toString(id),reference})) {
            return !c.moveToFirst();
        }
    }

    private long captureRow(String key, String hash, String normalized, String reference, ExpenseParser.Parsed parsed,
                            String direction, long at, Long account) {
        long keyed = rowForKey(key);
        if (keyed >= 0) return keyed;
        long exact = rowForHash(hash, at);
        if (exact >= 0) return exact;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT t.id FROM transactions t JOIN capture_evidence e ON e.transaction_id=t.id " +
                "WHERE t.currency=? AND t.amount_minor=? AND e.direction=? AND ((e.normalized_hash=? AND t.occurred_at BETWEEN ? AND ?) " +
                "OR (?<>'' AND e.reference_hash=?)) AND (e.reference_hash IS NULL OR ?='' OR e.reference_hash=?) ORDER BY t.id",
                new String[]{parsed.currency,Long.toString(parsed.amountMinor),direction,normalized,Long.toString(at-120000),
                        Long.toString(at+120000),reference==null?"":reference,reference==null?"":reference,
                        reference==null?"":reference,reference==null?"":reference})) {
            if (c.getCount()==1 && c.moveToFirst()) return c.getLong(0);
        }
        // No amount-only or time-only matching: unrelated purchases must not disappear.
        return -1;
    }

    private void syncAutomatic(long id, ExpenseParser.Parsed parsed, String direction, Long account, String body, long eventAt,String timePrecision) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT merchant,amount_minor,currency,kind,user_edited,category,payment_source_id,destination_source_id,time_precision " +
                "FROM transactions WHERE id=?", new String[]{Long.toString(id)})) {
            if (!c.moveToFirst()) return;
            String oldMerchant=c.getString(0), oldKind=c.getString(3); long oldAmount=c.getLong(1);
            ContentValues values=new ContentValues();
            if (c.getInt(4)==0) {
                values.put("occurred_at",eventAt);
                // Never infer precision for legacy rows, even when re-importing their evidence.
                if(!c.isNull(8))values.put("time_precision",timePrecision);
                ExpenseParser.Balance balance=ExpenseParser.parseBalance(body);
                boolean correction = oldAmount==parsed.amountMinor || oldAmount==parsed.amountMinor*10 ||
                        balance!=null && oldAmount==balance.amountMinor || !c.getString(2).equals(parsed.currency);
                if (correction) { values.put("amount_minor",parsed.amountMinor); values.put("currency",parsed.currency); }
                else values.put("needs_review",1);
                boolean generic=ExpenseParser.genericMerchant(oldMerchant);
                if (generic || oldMerchant.equals(parsed.merchant) || oldMerchant.toLowerCase(Locale.ROOT).contains("vodafone")) {
                    values.put("merchant",parsed.merchant);
                    if ("غير مصنف".equals(c.getString(5)) || generic) values.put("category",parsed.category);
                }
                if (!"transfer".equals(oldKind)) values.put("kind",direction);
                if (correction && !ExpenseParser.genericMerchant(parsed.merchant))
                    syncRecurringSource(oldMerchant,oldAmount,c.getString(2),parsed,account);
            }
            if (account!=null) {
                if ("transfer".equals(oldKind) && "income".equals(direction)) {
                    if (c.isNull(7)) values.put("destination_source_id",account);
                    if (!c.isNull(6) && c.getLong(6)==account && c.isNull(7)) values.putNull("payment_source_id");
                } else if (c.isNull(6)) values.put("payment_source_id",account);
            }
            if (values.size()>0) getWritableDatabase().update("transactions",values,"id=?",new String[]{Long.toString(id)});
        }
    }

    private void syncRecurringSource(String oldMerchant,long oldAmount,String currency,ExpenseParser.Parsed parsed,Long account) {
        if (!ExpenseParser.genericMerchant(oldMerchant)) return;
        ContentValues values=new ContentValues(); values.put("merchant",parsed.merchant); values.put("amount_minor",parsed.amountMinor);
        values.put("match_text",parsed.merchant.toLowerCase(Locale.ROOT)); values.put("needs_review",0);
        getWritableDatabase().update("recurring",values,"merchant=? AND amount_minor=? AND currency=? " +
                "AND (payment_source_id IS NULL OR payment_source_id=?)",
                new String[]{oldMerchant,Long.toString(oldAmount),currency,account==null?"-1":Long.toString(account)});
    }

    private void archiveEntry(long id,String reason,boolean exclude) {
        SQLiteDatabase writable=getWritableDatabase();
        try (Cursor c=writable.rawQuery("SELECT * FROM transactions WHERE id=?",new String[]{Long.toString(id)})) {
            if (!c.moveToFirst()) return;
            org.json.JSONObject payload=new org.json.JSONObject();
            try {
                for (int i=0;i<c.getColumnCount();i++) payload.put(c.getColumnName(i),c.isNull(i)?org.json.JSONObject.NULL:c.getString(i));
                if (exclude) {
                    // Keep the (still encrypted) source messages with the archived row, so a correction stays provable.
                    org.json.JSONArray evidence=new org.json.JSONArray();
                    try (Cursor e=writable.rawQuery("SELECT unique_key,source,sender,received_at,direction,encrypted_body FROM capture_evidence WHERE transaction_id=?",new String[]{Long.toString(id)})) {
                        while (e.moveToNext()) {
                            org.json.JSONObject row=new org.json.JSONObject();
                            for (int i=0;i<e.getColumnCount();i++) row.put(e.getColumnName(i),e.isNull(i)?org.json.JSONObject.NULL:e.getString(i));
                            evidence.put(row);
                        }
                    }
                    payload.put("evidence",evidence);
                }
            }
            catch (org.json.JSONException error) { throw new IllegalStateException(error); }
            ContentValues archived=new ContentValues(); archived.put("original_id",id); archived.put("reason",reason);
            archived.put("archived_at",System.currentTimeMillis()); archived.put("payload",payload.toString());
            writable.insertOrThrow("transaction_archive",null,archived);
            if (exclude) {
                ContentValues excluded=new ContentValues(); excluded.put("unique_key",c.getString(c.getColumnIndexOrThrow("unique_key")));
                writable.insertWithOnConflict("excluded_messages",null,excluded,SQLiteDatabase.CONFLICT_IGNORE);
                writable.execSQL("INSERT OR IGNORE INTO excluded_messages SELECT unique_key FROM capture_evidence WHERE transaction_id=?",new Object[]{id});
                writable.delete("capture_evidence","transaction_id=?",new String[]{Long.toString(id)});
            }
            writable.delete("transactions","id=?",new String[]{Long.toString(id)});
        }
    }

    private boolean mergeEntries(long keep,long remove,String reason) {
        if (keep==remove) return true;
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            Transaction kept=transactionById(keep), removed=transactionById(remove);
            if (kept==null || removed==null) {sql.setTransactionSuccessful();return false;}
            boolean linked=hasReimbursement(keep)||hasReimbursement(remove);
            long[] keptLink=reimbursementLink(keep),removedLink=reimbursementLink(remove);
            boolean conflict=linked && (!kept.kind.equals(removed.kind) || !kept.currency.equals(removed.currency) ||
                    "transfer".equals(kept.kind));
            if(keptLink!=null && removedLink!=null && (keptLink[0]!=removedLink[0] || keptLink[1]!=removedLink[1])) conflict=true;
            if(removedLink!=null && removedLink[1]>kept.amountMinor) conflict=true;
            try(Cursor c=sql.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM reimbursements WHERE expense_id IN (?,?)",
                    new String[]{Long.toString(keep),Long.toString(remove)})) {
                if(c.moveToFirst() && c.getLong(0)>kept.amountMinor)conflict=true;
            }
            Long keptParent=feeParent(keep),removedParent=feeParent(remove);
            if(java.util.Objects.equals(keptParent,remove)||java.util.Objects.equals(removedParent,keep)||
                    keptParent!=null&&removedParent!=null&&!keptParent.equals(removedParent))conflict=true;
            if(conflict) {markMergeReview(keep,remove);sql.setTransactionSuccessful();return false;}
            ContentValues values=new ContentValues();
            if (kept.paymentSourceId==null && removed.paymentSourceId!=null) values.put("payment_source_id",removed.paymentSourceId);
            if (kept.destinationSourceId==null && removed.destinationSourceId!=null) values.put("destination_source_id",removed.destinationSourceId);
            if ("transfer".equals(removed.kind)) { values.put("kind","transfer");values.put("category","تحويلات"); }
            if(keptParent==null&&removedParent!=null)values.put("fee_parent_id",removedParent);
            if (values.size()>0) sql.update("transactions",values,"id=?",new String[]{Long.toString(keep)});
            if(removedLink!=null) {
                if(keptLink!=null)sql.delete("reimbursements","income_id=?",new String[]{Long.toString(remove)});
                else sql.execSQL("UPDATE reimbursements SET income_id=? WHERE income_id=?",new Object[]{keep,remove});
            }
            sql.execSQL("UPDATE reimbursements SET expense_id=? WHERE expense_id=?",new Object[]{keep,remove});
            sql.execSQL("UPDATE transactions SET fee_parent_id=? WHERE fee_parent_id=?",new Object[]{keep,remove});
            sql.execSQL("UPDATE recurring SET origin_transaction_id=? WHERE origin_transaction_id=?",new Object[]{keep,remove});
            sql.execSQL("UPDATE capture_evidence SET transaction_id=? WHERE transaction_id=?",new Object[]{keep,remove});
            archiveEntry(remove,reason,false);
            sql.setTransactionSuccessful();
            if(lastCapturedId==remove)lastCapturedId=keep;
            return true;
        }finally {sql.endTransaction();}
    }

    private boolean hasReimbursement(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM reimbursements WHERE income_id=? OR expense_id=? LIMIT 1",
                new String[]{Long.toString(id),Long.toString(id)})) {return c.moveToFirst();}
    }

    private long[] reimbursementLink(long income) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT expense_id,amount_minor FROM reimbursements WHERE income_id=?",
                new String[]{Long.toString(income)})) {return c.moveToFirst()?new long[]{c.getLong(0),c.getLong(1)}:null;}
    }

    private Long feeParent(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",
                new String[]{Long.toString(id)})) {return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):null;}
    }

    private void markMergeReview(long first,long second) {
        getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id IN (?,?)",new Object[]{first,second});
    }

    /** A full SMS may have been captured once as a prefix. Suggest, never merge without confirmation. */
    public List<Transaction> abbreviatedSmsCopies(long completeId) {
        Transaction complete=transactionById(completeId);List<Transaction> matches=new ArrayList<>();
        if(complete==null||!"expense".equals(complete.kind)||complete.paymentSourceId==null)return matches;
        List<SourceEvidence> full=sourceEvidence(completeId);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE id<>? AND kind='expense' AND user_edited=0 AND payment_source_id=? AND currency=? AND amount_minor=? AND fee_parent_id IS NULL",
                new String[]{Long.toString(completeId),Long.toString(complete.paymentSourceId),complete.currency,Long.toString(complete.amountMinor)})) {
            while(c.moveToNext()) {
                long id=c.getLong(0);boolean prefix=false;
                for(SourceEvidence part:sourceEvidence(id))for(SourceEvidence whole:full) {
                    if(!"SMS".equals(part.source)||!"SMS".equals(whole.source)||Math.abs(part.receivedAt-whole.receivedAt)>120000)continue;
                    String partBank=SourceIdentity.bank(part.sender),wholeBank=SourceIdentity.bank(whole.sender);
                    if(!java.util.Objects.equals(partBank==null?part.sender:partBank,wholeBank==null?whole.sender:wholeBank))continue;
                    String shortBody=ExpenseParser.normalizeDigits(part.body).replaceAll("[\\s\\p{Z}]+"," ").trim();
                    String longBody=ExpenseParser.normalizeDigits(whole.body).replaceAll("[\\s\\p{Z}]+"," ").trim();
                    if(shortBody.length()>=64&&longBody.length()>=shortBody.length()+8&&longBody.startsWith(shortBody))prefix=true;
                }
                if(prefix)matches.add(transactionById(id));
            }
        }
        return matches;
    }
    public void confirmAbbreviatedSmsCopy(long completeId,long partialId) {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            boolean found=false;for(Transaction candidate:abbreviatedSmsCopies(completeId))if(candidate.id==partialId)found=true;
            if(!found)throw new IllegalArgumentException("Source copy is not a matching prefix");
            if(!mergeEntries(completeId,partialId,"user confirmed abbreviated/full SMS of one purchase"))
                throw new IllegalArgumentException("Related records conflict; review before merging");
            ContentValues kept=new ContentValues();kept.put("user_edited",1);sql.update("transactions",kept,"id=?",new String[]{Long.toString(completeId)});
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
    }

    /** A stricter parser may flag historical rows, but must never remove them or change user edits. */
    private void markRejectedCaptureForReview(String hash,long at) {
        long id=rowForHash(hash,at);
        if (id<0) return;
        ContentValues values=new ContentValues();values.put("needs_review",1);
        getWritableDatabase().update("transactions",values,"id=? AND user_edited=0",new String[]{Long.toString(id)});
    }

    private boolean applyCancellation(String sender,String body,long receivedAt) {
        if (!body.matches("(?is).*تم\\s*(?:الغاء|إلغاء)\\s*تحويل.*")) return false;
        ExpenseParser.Parsed parsed=ExpenseParser.parse(sender,body.replaceAll("(?:الغاء|إلغاء)",""));
        if (parsed==null || parsed.cardLast4==null) return false;
        Long account=autoSource(sender,parsed.cardLast4,body);
        if (account==null) return false;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE kind='expense' AND user_edited=0 " +
                "AND payment_source_id=? AND amount_minor=? AND currency=? AND received_at BETWEEN ? AND ?",
                new String[]{Long.toString(account),Long.toString(parsed.amountMinor),parsed.currency,
                        Long.toString(receivedAt-120000),Long.toString(receivedAt)})) {
            if (c.getCount()!=1 || !c.moveToFirst()) return false;
            archiveEntry(c.getLong(0),"bank confirmed cancellation",true); return true;
        }
    }

    private void recordHint(String sender,String body,long when) {
        CommitmentHint hint=CommitmentHint.parse(sender,body,when);
        String messageKey=sha256(sender+"\n"+when+"\n"+body);
        if(hint==null) {
            // Re-reading a corrected format retires only an unconfirmed proposal from that source.
            getWritableDatabase().execSQL("UPDATE commitment_suggestions SET status='superseded' WHERE status='pending' AND received_at=? AND hint_key IN (SELECT hint_key FROM commitment_observations WHERE message_key=?) AND NOT EXISTS (SELECT 1 FROM recurring r WHERE r.hint_key=commitment_suggestions.hint_key)",new Object[]{when,messageKey});
            String complete=CommitmentHint.completedPlanKey(sender,body);
            String completionHash=sha256(sender+"\n"+when+"\n"+body);
            if(complete!=null) try(Cursor c=getReadableDatabase().rawQuery("SELECT r.id FROM recurring r JOIN commitment_suggestions s ON s.hint_key=r.hint_key WHERE r.hint_key=? AND r.status='active' AND s.received_at<=? AND (r.last_completion_hash IS NULL OR r.last_completion_hash!=?)",new String[]{complete,Long.toString(when),completionHash})) {
                if(c.getCount()==1 && c.moveToFirst() && finishRecurring(c.getLong(0),true)) {
                    ContentValues values=new ContentValues();values.put("last_completion_hash",completionHash);
                    getWritableDatabase().update("recurring",values,"id=?",new String[]{Long.toString(c.getLong(0))});
                }
            }
            return;
        }
        if(hint.amount==0) try(Cursor prior=getReadableDatabase().rawQuery("SELECT amount_minor,currency FROM commitment_suggestions WHERE hint_key=?",new String[]{hint.key})) {
            if(prior.moveToFirst() && prior.getLong(0)>0)
                hint=new CommitmentHint(hint.key,hint.merchant,hint.kind,prior.getLong(0),hint.count,hint.estimatedDate,
                        hint.explanation+" المبلغ المعروض من رسالة سابقة، وليس تأكيدًا للمبلغ الحالي.",prior.getString(1),hint.cycle,hint.dateExact);
        }
        if("subscription".equals(hint.kind)) hint=canonicalSubscriptionHint(hint,sender,body,when);
        String previousKey=null;
        try(Cursor existing=getReadableDatabase().rawQuery("SELECT hint_key FROM commitment_observations WHERE message_key=?",new String[]{messageKey})) {
            if(existing.moveToFirst()) previousKey=existing.getString(0);
        }
        if(previousKey!=null && !previousKey.equals(hint.key)) {
            ContentValues moved=new ContentValues();moved.put("hint_key",hint.key);
            getWritableDatabase().update("commitment_observations",moved,"message_key=?",new String[]{messageKey});
            getWritableDatabase().execSQL("UPDATE commitment_suggestions SET status='superseded' WHERE hint_key=? AND status='pending' AND NOT EXISTS (SELECT 1 FROM commitment_observations o WHERE o.hint_key=commitment_suggestions.hint_key) AND NOT EXISTS (SELECT 1 FROM recurring r WHERE r.hint_key=commitment_suggestions.hint_key)",new Object[]{previousKey});
        }
        String encrypted=EvidenceVault.encrypt(body);
        ContentValues observation=new ContentValues();
        observation.put("message_key",messageKey);observation.put("hint_key",hint.key);
        observation.put("amount_minor",hint.amount);observation.put("currency",hint.currency);
        observation.put("due_date",hint.estimatedDate.toString());observation.put("sender",sender);
        observation.put("received_at",when);observation.put("encrypted_body",encrypted);
        getWritableDatabase().insertWithOnConflict("commitment_observations",null,observation,SQLiteDatabase.CONFLICT_IGNORE);
        boolean changedAccepted=false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT received_at,status,amount_minor,currency FROM commitment_suggestions WHERE hint_key=?",new String[]{hint.key})) {
            if(c.moveToFirst() && c.getLong(0)>=when) return;
            if(!c.isAfterLast() && c.getCount()>0) changedAccepted="accepted".equals(c.getString(1)) &&
                    (c.getLong(2)!=hint.amount || !hint.currency.equals(c.getString(3)));
        }
        ContentValues values=new ContentValues(); values.put("hint_key",hint.key);values.put("merchant",hint.merchant);
        values.put("kind",hint.kind);values.put("amount_minor",hint.amount);
        if(hint.count==null) values.putNull("total_count"); else values.put("total_count",hint.count);
        values.put("estimated_date",hint.estimatedDate.toString());values.put("explanation",hint.explanation);
        values.put("currency",hint.currency);values.put("cycle",hint.cycle);values.put("date_exact",hint.dateExact?1:0);
        if(changedAccepted) values.put("status","pending");
        values.put("sender",sender);values.put("received_at",when);values.put("encrypted_body",encrypted);
        int changed=getWritableDatabase().update("commitment_suggestions",values,"hint_key=?",new String[]{hint.key});
        if(changed==0) getWritableDatabase().insertOrThrow("commitment_suggestions",null,values);
    }

    /** Keep price changes in one billing schedule; simultaneous charges and different cards remain separate. */
    private CommitmentHint canonicalSubscriptionHint(CommitmentHint hint,String sender,String body,long when) {
        ExpenseParser.Parsed current=ExpenseParser.parse(sender,body);
        if(current==null) return hint;
        String bank=SourceIdentity.bank(sender);
        String source=bank==null?(sender==null?"":sender.toLowerCase(Locale.ROOT)):bank;
        List<String> compatible=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT hint_key,estimated_date,received_at,amount_minor,sender,encrypted_body,status FROM commitment_suggestions WHERE merchant=? AND currency=? AND kind='subscription' AND cycle=? AND status!='superseded' ORDER BY status='dismissed' DESC,status='accepted' DESC,received_at DESC",new String[]{hint.merchant,hint.currency,hint.cycle})) {
            while(c.moveToNext()) {
                LocalDate date=LocalDate.parse(c.getString(1));
                int gap=Math.abs(date.getDayOfMonth()-hint.estimatedDate.getDayOfMonth());
                if(gap>1 && !(date.getDayOfMonth()>=28 && hint.estimatedDate.getDayOfMonth()>=28)) continue;
                if(Math.abs(c.getLong(2)-when)<15L*86400000 && c.getLong(3)!=hint.amount) continue;
                String priorBank=SourceIdentity.bank(c.getString(4));
                String priorSource=priorBank==null?(c.getString(4)==null?"":c.getString(4).toLowerCase(Locale.ROOT)):priorBank;
                if(!source.equals(priorSource)) continue;
                ExpenseParser.Parsed prior=ExpenseParser.parse(c.getString(4),EvidenceVault.decrypt(c.getString(5)));
                if(prior==null || !java.util.Objects.equals(current.cardLast4,prior.cardLast4)) continue;
                compatible.add(c.getString(0));
            }
        }
        if(compatible.isEmpty()) return hint;
        int linkedPlans=0;
        for(String candidate:compatible) {
            try(Cursor linked=getReadableDatabase().rawQuery("SELECT 1 FROM recurring WHERE hint_key=?",new String[]{candidate})) {
                if(linked.moveToFirst()) linkedPlans++;
            }
        }
        // Multiple confirmed plans on the same billing day need the user's choice.
        if(linkedPlans>1) return hint;
        String key=compatible.get(0);
        for(int i=1;i<compatible.size();i++) {
            String old=compatible.get(i);
            try(Cursor linked=getReadableDatabase().rawQuery("SELECT 1 FROM recurring WHERE hint_key=?",new String[]{old})) {
                if(linked.moveToFirst()) continue;
            }
            ContentValues values=new ContentValues();values.put("hint_key",key);
            getWritableDatabase().update("commitment_observations",values,"hint_key=?",new String[]{old});
            values.clear();values.put("status","superseded");
            getWritableDatabase().update("commitment_suggestions",values,"hint_key=? AND status='pending'",new String[]{old});
        }
        return new CommitmentHint(key,hint.merchant,hint.kind,hint.amount,hint.count,hint.estimatedDate,hint.explanation,
                hint.currency,hint.cycle,hint.dateExact);
    }

    public List<Suggestion> commitmentSuggestions() {
        List<Suggestion> result=new ArrayList<>();
        List<Recurring> confirmed=recurring();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT s.hint_key,s.merchant,s.kind,s.amount_minor,s.total_count,s.estimated_date,s.explanation,s.sender,s.received_at,s.encrypted_body,s.currency,s.cycle,s.date_exact,r.id,r.status FROM commitment_suggestions s LEFT JOIN recurring r ON r.hint_key=s.hint_key WHERE s.status='pending' ORDER BY s.received_at DESC",null)) {
            while(c.moveToNext()) {
                if(!c.isNull(13) && "done".equals(c.getString(14))) continue;
                boolean exists=false;
                for(Recurring item:confirmed) if(item.merchant.equalsIgnoreCase(c.getString(1)) &&
                        item.currency.equals(c.getString(10)) && item.amountMinor==c.getLong(3)) { exists=true;break; }
                if(!exists) result.add(new Suggestion(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.isNull(4)?null:c.getInt(4),
                        LocalDate.parse(c.getString(5)),c.getString(6),new SourceEvidence("SMS",c.getString(7),c.getLong(8),EvidenceVault.decrypt(c.getString(9))),
                        c.getString(10),c.getString(11),c.getInt(12)!=0,c.isNull(13)?null:c.getLong(13)));
            }
        }
        return result;
    }
    public void dismissSuggestion(String key) {
        ContentValues values=new ContentValues();values.put("status","dismissed");
        getWritableDatabase().update("commitment_suggestions",values,"hint_key=?",new String[]{key});
    }
    public void acceptSuggestion(String key,long recurringId) {
        ContentValues values=new ContentValues();values.put("status","accepted");
        getWritableDatabase().update("commitment_suggestions",values,"hint_key=?",new String[]{key});
        values.clear();values.put("hint_key",key);
        getWritableDatabase().update("recurring",values,"id=?",new String[]{Long.toString(recurringId)});
    }
    public List<SourceEvidence> commitmentHistory(String key) {
        List<SourceEvidence> rows=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sender,received_at,encrypted_body FROM commitment_observations WHERE hint_key=? ORDER BY received_at DESC LIMIT 12",new String[]{key})) {
            while(c.moveToNext()) rows.add(new SourceEvidence("SMS",c.getString(0),c.getLong(1),EvidenceVault.decrypt(c.getString(2))));
        }
        return rows;
    }
    public List<SourceEvidence> commitmentHistoryFor(long recurringId) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT hint_key FROM recurring WHERE id=?",new String[]{Long.toString(recurringId)})) {
            if(c.moveToFirst() && !c.isNull(0)) return commitmentHistory(c.getString(0));
        }
        return new ArrayList<>();
    }
    public static final class Suggestion {
        public final String key,merchant,kind,explanation,currency,cycle; public final long amount;public final Integer count;
        public final LocalDate date;public final SourceEvidence evidence;
        public final boolean dateExact;public final Long existingId;
        Suggestion(String key,String merchant,String kind,long amount,Integer count,LocalDate date,String explanation,SourceEvidence evidence,
                   String currency,String cycle,boolean dateExact,Long existingId) {
            this.key=key;this.merchant=merchant;this.kind=kind;this.amount=amount;this.count=count;this.date=date;this.explanation=explanation;this.evidence=evidence;
            this.currency=currency;this.cycle=cycle;this.dateExact=dateExact;this.existingId=existingId;
        }
    }

    public List<SourceEvidence> sourceEvidence(long id) {
        List<SourceEvidence> rows=new ArrayList<>();
        try(Cursor parent=getReadableDatabase().rawQuery("SELECT fee_parent_id FROM transactions WHERE id=?",new String[]{Long.toString(id)})) {
            if(parent.moveToFirst() && !parent.isNull(0)) id=parent.getLong(0);
        }
        try (Cursor c=getReadableDatabase().rawQuery("SELECT source,sender,received_at,encrypted_body FROM capture_evidence " +
                "WHERE transaction_id=? ORDER BY encrypted_body IS NULL,received_at DESC",new String[]{Long.toString(id)})) {
            while(c.moveToNext()) {
                String body=EvidenceVault.decrypt(c.getString(3));
                if(body!=null) rows.add(new SourceEvidence(c.getString(0),c.getString(1),c.getLong(2),body));
            }
        }
        return rows;
    }

    public static final class SourceEvidence {
        public final String source,sender,body; public final long receivedAt;
        SourceEvidence(String source,String sender,long receivedAt,String body) {
            this.source=source;this.sender=sender;this.receivedAt=receivedAt;this.body=body;
        }
    }


    public boolean addManual(long when, long amountMinor, String currency, String merchant, String category) {
        return addManual(when, amountMinor, currency, merchant, category, null);
    }

    public boolean addManual(long when, long amountMinor, String currency, String merchant, String category, Long sourceId) {
        return addManualEntry(when, amountMinor, currency, merchant, category, sourceId,
                "expense", null, null);
    }

    public boolean addManualEntry(long when, long amountMinor, String currency, String merchant,
                                  String category, Long sourceId, String kind, Long destinationId, String note) {
        if (!"expense".equals(kind) && !"income".equals(kind) && !"transfer".equals(kind)) return false;
        if ("transfer".equals(kind) && (sourceId == null || destinationId == null || accountId(sourceId)==accountId(destinationId))) return false;
        boolean inserted = insertEntry("manual:" + java.util.UUID.randomUUID(), when, amountMinor, currency,
                merchant.trim(), category, "يدوي", null, null, sourceId, kind, destinationId, note);
        if (inserted && "expense".equals(kind)) advanceRecurring(merchant.trim(), amountMinor, currency, when);
        return inserted;
    }

    /** A split share must not be added on top of an already recorded bill. Check and save atomically. */
    public boolean addSplitShare(long when, long totalMinor, int people, String currency, String merchant,
                                 String category, Long sourceId, String note) {
        if (totalMinor <= 0 || people < 2 || merchant == null || merchant.isBlank()) throw new IllegalArgumentException();
        long share = Math.round(totalMinor / (double) people);
        if (share <= 0) throw new IllegalArgumentException();
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        long start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            try (Cursor c = writable.rawQuery("SELECT 1 FROM transactions WHERE kind='expense' AND currency=? " +
                    "AND amount_minor=? AND lower(trim(merchant))=lower(?) AND occurred_at>=? AND occurred_at<? LIMIT 1",
                    new String[]{currency,Long.toString(totalMinor),merchant.trim(),Long.toString(start),Long.toString(end)})) {
                if (c.moveToFirst()) return false;
            }
            boolean added = addManualEntry(when, share, currency, merchant, category, sourceId, "expense", null, note);
            writable.setTransactionSuccessful();
            return added;
        } finally { writable.endTransaction(); }
    }

    public boolean addCsvEntry(String fileHash, CsvImport.Row row) {
        long when = row.date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            String key="csv:"+fileHash+":"+row.line;
            boolean added=insertEntry(key,when,row.amountMinor,row.currency,row.merchant,row.category,"CSV",null,null,null,row.kind,null,row.note);
            if(added) {
                ContentValues values=new ContentValues();values.put("time_precision","date_only");
                sql.update("transactions",values,"unique_key=?",new String[]{key});
            }
            sql.setTransactionSuccessful();return added;
        }finally {sql.endTransaction();}
    }

    private boolean insertEntry(String key, long when, long amount, String currency,
                                String merchant, String category, String source, String messageHash,
                                String cardLast4, Long sourceId, String kind, Long destinationId, String note) {
        return insertEntry(key, when, amount, currency, merchant, category, source, messageHash,
                cardLast4, sourceId, kind, destinationId, note, when);
    }

    private boolean insertEntry(String key, long when, long amount, String currency,
                                String merchant, String category, String source, String messageHash,
                                String cardLast4, Long sourceId, String kind, Long destinationId, String note,
                                long receivedAt) {
        if (amount <= 0 || merchant.isBlank()) return false;
        try (Cursor excluded = getReadableDatabase().rawQuery(
                "SELECT 1 FROM excluded_messages WHERE unique_key=?", new String[]{key})) {
            if (excluded.moveToFirst()) return false;
        }
        ContentValues values = new ContentValues();
        values.put("unique_key", key);
        values.put("occurred_at", when);
        values.put("received_at", receivedAt);
        values.put("amount_minor", amount);
        values.put("currency", currency);
        values.put("merchant", merchant);
        values.put("category", category);
        values.put("source", source);
        values.put("message_hash", messageHash);
        values.put("card_last4", cardLast4);
        values.put("kind", kind);
        if (destinationId != null) values.put("destination_source_id", destinationId);
        if (note != null) values.put("note", note.trim());
        if (sourceId != null) values.put("payment_source_id", sourceId);
        MerchantRule rule = matchingRule(merchant);
        if (rule != null && !"يدوي".equals(source) && !"CSV".equals(source)) {
            if (rule.ignored) return false;
            if ("expense".equals(kind) && !java.util.Arrays.asList("تحويلات","رسوم تحويل","سحب نقدي").contains(category)
                    && !rule.category.isBlank()) values.put("category", rule.category);
        }
        return getWritableDatabase().insertWithOnConflict("transactions", null, values,
                SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    private void recordBalanceFromMessage(String sender, String body, long receivedAt) {
        ExpenseParser.Balance balance = ExpenseParser.parseBalance(body);
        if (balance == null || balance.cardLast4 == null) return;
        Long sourceId = autoSource(sender, balance.cardLast4, body);
        if (sourceId != null) putBalance(sourceId, balance.currency, balance.amountMinor,
                ExpenseParser.eventTime(body, receivedAt), receivedAt, "SMS",
                sha256("balance:" + sender + "\n" + receivedAt + "\n" + body));
    }

    private Long autoSource(String sender, String last4, String body) {
        String identity = SourceIdentity.key(sender, last4);
        if (identity == null) return null;
        try (Cursor alias = getReadableDatabase().rawQuery(
                "SELECT payment_source_id FROM source_aliases WHERE identity=?", new String[]{identity})) {
            if (alias.moveToFirst()) {
                long id = alias.getLong(0);
                updateAutoSourceKind(id, body);
                return id;
            }
        }
        Long matched = null;
        try (Cursor source = getReadableDatabase().rawQuery(
                "SELECT id,name,last4 FROM payment_sources ORDER BY id", null)) {
            while (source.moveToNext()) {
                if (!identity.equals(SourceIdentity.key(source.getString(1), source.getString(2)))) continue;
                if (matched != null) return null; // An ambiguous existing pair needs manual review.
                matched = source.getLong(0);
            }
        }
        if (matched != null) {
            addSourceAlias(identity, matched);
            updateAutoSourceKind(matched, body);
            return matched;
        }
        String bank = sender == null ? "" : sender.replaceAll("[^\\p{L} ]", " ").trim();
        if (bank.isEmpty()) bank = "حساب أو بطاقة";
        if (bank.length() > 24) bank = bank.substring(0, 24).trim();
        String kind = sourceKind(body);
        long created = addPaymentSource(bank, kind == null ? "مكتشف من الرسائل" : kind, last4);
        if (created > 0) addSourceAlias(identity, created);
        return created > 0 ? created : null;
    }

    private static String sourceKind(String body) {
        if (body == null) return null;
        String value = body.toLowerCase(Locale.ROOT);
        if (value.contains("البطاقة الائتمانية") || value.contains("credit card")) return "بطاقة ائتمان";
        if (value.contains("بطاق") || value.contains("كارت") || value.contains("card")) return "بطاقة";
        if (value.contains("حساب") || value.contains("account")) return "حساب";
        return null;
    }

    private void updateAutoSourceKind(long id, String body) {
        String kind = sourceKind(body);
        if (kind == null) return;
        ContentValues values = new ContentValues();
        values.put("kind", kind);
        getWritableDatabase().update("payment_sources", values,
                "id=? AND (kind='مكتشف من الرسائل' OR (kind='بطاقة' AND ?='بطاقة ائتمان'))",
                new String[]{Long.toString(id), kind});
    }

    private void addSourceAlias(String identity, long sourceId) {
        ContentValues values = new ContentValues();
        values.put("identity", identity);
        values.put("payment_source_id", sourceId);
        getWritableDatabase().insertWithOnConflict("source_aliases", null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void mergeDuplicateAutomaticSources(SQLiteDatabase db) {
        java.util.Map<String, Long> primary = new java.util.HashMap<>();
        List<Long> duplicates = new ArrayList<>();
        List<Long> targets = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id,name,last4 FROM payment_sources WHERE kind='مكتشف من الرسائل' ORDER BY id", null)) {
            while (cursor.moveToNext()) {
                String identity = SourceIdentity.key(cursor.getString(1), cursor.getString(2));
                if (identity == null) continue;
                Long existing = primary.putIfAbsent(identity, cursor.getLong(0));
                if (existing != null) { duplicates.add(cursor.getLong(0)); targets.add(existing); }
            }
        }
        for (int i = 0; i < duplicates.size(); i++) {
            long oldId = duplicates.get(i), newId = targets.get(i);
            db.execSQL("UPDATE transactions SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{newId, oldId});
            db.execSQL("UPDATE transactions SET destination_source_id=? WHERE destination_source_id=?",
                    new Object[]{newId, oldId});
            db.execSQL("UPDATE balance_snapshots SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{newId, oldId});
            db.delete("payment_sources", "id=?", new String[]{Long.toString(oldId)});
        }
    }

    private void linkExistingSource(String key, long sourceId, String lastDigits) {
        ContentValues values = new ContentValues();
        values.put("payment_source_id", sourceId);
        values.put("card_last4", lastDigits);
        getWritableDatabase().update("transactions", values, "unique_key=? AND payment_source_id IS NULL",
                new String[]{key});
    }

    private void linkExistingSourceId(long transactionId, long sourceId, String lastDigits) {
        ContentValues values = new ContentValues();
        values.put("payment_source_id", sourceId);
        values.put("card_last4", lastDigits);
        getWritableDatabase().update("transactions", values, "id=? AND payment_source_id IS NULL",
                new String[]{Long.toString(transactionId)});
    }

    public void putManualBalance(long sourceId, String currency, long balanceMinor, long occurredAt) {
        putBalance(sourceId, currency, balanceMinor, occurredAt, System.currentTimeMillis(),
                "يدوي", "manual-balance:" + java.util.UUID.randomUUID());
    }

    private void putBalance(long sourceId, String currency, long balanceMinor, long occurredAt,
                            long receivedAt, String source, String uniqueKey) {
        if (balanceMinor < 0) return;
        ContentValues values = new ContentValues();
        values.put("payment_source_id", sourceId);
        values.put("currency", currency);
        values.put("balance_minor", balanceMinor);
        values.put("occurred_at", occurredAt);
        values.put("received_at", receivedAt);
        values.put("source", source);
        values.put("unique_key", uniqueKey);
        SQLiteDatabase db = getWritableDatabase();
        if (db.insertWithOnConflict("balance_snapshots", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1) {
            ContentValues corrected = new ContentValues();
            corrected.put("occurred_at", occurredAt);
            corrected.put("received_at", receivedAt);
            corrected.put("balance_minor", balanceMinor);
            corrected.put("payment_source_id", sourceId);
            corrected.put("currency", currency);
            db.update("balance_snapshots", corrected, "unique_key=?", new String[]{uniqueKey});
        }
    }

    public BalanceSnapshot latestBalance(long sourceId, String currency) {
        sourceId = accountId(sourceId);
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT balance_minor,occurred_at,received_at,source,payment_source_id FROM balance_snapshots " +
                "WHERE payment_source_id IN (SELECT id FROM payment_sources WHERE COALESCE(parent_source_id,id)=CAST(? AS INTEGER)) " +
                "AND currency=? ORDER BY occurred_at DESC,received_at DESC,id DESC LIMIT 1",
                new String[]{Long.toString(sourceId), currency})) {
            if (!cursor.moveToFirst()) return null;
            return new BalanceSnapshot(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getString(3),
                    false, cursor.getLong(4), cursor.getLong(1), 0);
        }
    }

    public BalanceSnapshot currentBalance(long sourceId, String currency) {
        sourceId = accountId(sourceId);
        BalanceSnapshot anchor = latestBalance(sourceId, currency);
        if (anchor == null) return null;
        long amount = anchor.amountMinor;
        int movements = 0;
        long updatedAt = anchor.occurredAt;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT t.occurred_at,t.received_at,t.kind,COALESCE(p.parent_source_id,p.id),COALESCE(d.parent_source_id,d.id),t.amount_minor " +
                        "FROM transactions t LEFT JOIN payment_sources p ON p.id=t.payment_source_id " +
                        "LEFT JOIN payment_sources d ON d.id=t.destination_source_id " +
                        "WHERE t.currency=? AND (COALESCE(p.parent_source_id,p.id)=CAST(? AS INTEGER) OR COALESCE(d.parent_source_id,d.id)=CAST(? AS INTEGER)) " +
                        "AND (t.occurred_at>? OR (t.occurred_at=? AND t.received_at>?)) " +
                        "ORDER BY t.occurred_at,t.received_at,t.id",
                new String[]{currency, Long.toString(sourceId), Long.toString(sourceId),
                        Long.toString(anchor.occurredAt), Long.toString(anchor.occurredAt),
                        Long.toString(anchor.receivedAt)})) {
            while (cursor.moveToNext()) {
                if (!BalanceTimeline.after(cursor.getLong(0), cursor.getLong(1),
                        anchor.occurredAt, anchor.receivedAt)) continue;
                long delta = BalanceTimeline.delta(cursor.getString(2),
                        cursor.isNull(3) ? -1 : cursor.getLong(3),
                        cursor.isNull(4) ? -1 : cursor.getLong(4), sourceId, cursor.getLong(5));
                if (delta == 0) continue;
                amount += delta;
                movements++;
                updatedAt = Math.max(updatedAt, cursor.getLong(0));
            }
        }
        return movements == 0 ? anchor : new BalanceSnapshot(amount, anchor.occurredAt,
                anchor.receivedAt, anchor.source, true, anchor.anchorSourceId, updatedAt, movements);
    }

    public List<BalanceSnapshot> balanceHistory(long sourceId, String currency, int limit) {
        sourceId = accountId(sourceId);
        List<BalanceSnapshot> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT balance_minor,occurred_at,received_at,source FROM balance_snapshots " +
                "WHERE payment_source_id IN (SELECT id FROM payment_sources WHERE COALESCE(parent_source_id,id)=CAST(? AS INTEGER)) " +
                "AND currency=? ORDER BY occurred_at DESC,received_at DESC,id DESC LIMIT ?",
                new String[]{Long.toString(sourceId), currency, Integer.toString(limit)})) {
            while (cursor.moveToNext()) result.add(new BalanceSnapshot(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3)));
        }
        return result;
    }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : new String[]{"transactions", "excluded_messages", "recurring", "source_aliases", "payment_sources",
                    "budgets", "balance_snapshots", "merchant_rules", "budget_plan_categories", "budget_plans",
                    "assets", "debts", "own_handles", "capture_evidence", "transaction_archive", "commitment_suggestions", "commitment_observations",
                    "reimbursements", "dismissed_patterns", "repair_log"}) db.delete(table, null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void putMerchantRule(String matchText, String category, boolean ignored) {
        String clean = matchText.trim();
        if (clean.length() < 2) return;
        ContentValues values = new ContentValues();
        values.put("match_text", clean);
        values.put("category", category);
        values.put("ignored", ignored ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("merchant_rules", null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<MerchantRule> merchantRules() {
        List<MerchantRule> rules = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,match_text,category,ignored FROM merchant_rules ORDER BY LENGTH(match_text) DESC,id DESC", null)) {
            while (cursor.moveToNext()) rules.add(new MerchantRule(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getInt(3) != 0));
        }
        return rules;
    }

    public void deleteMerchantRule(long id) {
        getWritableDatabase().delete("merchant_rules", "id=?", new String[]{Long.toString(id)});
    }

    private MerchantRule matchingRule(String merchant) {
        return matchingRule(merchant,merchantRules());
    }

    private MerchantRule matchingRule(String merchant,List<MerchantRule> rules) {
        // A correction saved from a transaction wins over a broad text rule.
        for (MerchantRule rule : rules)
            if (rule.matchText.startsWith("=") && MerchantClassifier.matchesRule(merchant,rule.matchText)) return rule;
        for (MerchantRule rule : rules)
            if (MerchantClassifier.matchesRule(merchant,rule.matchText)) return rule;
        return null;
    }

    public void rememberMerchantCategory(String merchant, String category) {
        if (!canRememberMerchant(merchant,category)) throw new IllegalArgumentException("Not a spending merchant");
        putMerchantRule("="+MerchantClassifier.identity(merchant),category,false);
    }

    static boolean canRememberMerchant(String merchant,String category) {
        String key=MerchantClassifier.identity(merchant);
        return key.length()>=3 && !key.matches("(?:غير معروف|غير مصنف|حساب رقم.*|hsbc|banque misr|bank misr|بنك مصر|bm online|atm|instapay|kashier|paymob|تحويل لحظي|تحويلات)")
                && spendingCategory(category);
    }

    private static boolean spendingCategory(String category) {
        return category!=null && !category.isBlank() && !MoneyFlow.UNCATEGORIZED.equals(category)
                && !MoneyFlow.COMMITMENT_CATEGORIES.contains(category)
                && !java.util.Arrays.asList("تحويلات","رسوم تحويل","سحب نقدي","دخل").contains(category);
    }

    /** Preview and explicit application affect automatic captures only, never user edits or money fields. */
    public List<Long> merchantCategoryCandidates(String merchant,String category) {
        List<Long> ids=new ArrayList<>();
        if(!canRememberMerchant(merchant,category)) return ids;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,merchant,category FROM transactions WHERE kind='expense' " +
                "AND user_edited=0 AND fee_parent_id IS NULL AND source NOT IN ('يدوي','CSV')",null)) {
            while(c.moveToNext()) if(MerchantClassifier.sameMerchant(merchant,c.getString(1))
                    && !category.equals(c.getString(2)) && (spendingCategory(c.getString(2)) || MoneyFlow.UNCATEGORIZED.equals(c.getString(2)))) ids.add(c.getLong(0));
        }
        return ids;
    }

    public int applyMerchantCategory(String merchant,String category) {
        // Recompute on acceptance; edits made since the preview still stay protected.
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            int changed=updateCategories(merchantCategoryCandidates(merchant,category),category);
            sql.setTransactionSuccessful();return changed;
        } finally {sql.endTransaction();}
    }

    /** New catalog labels for untouched automatic purchases; no amount/account/type or source-text changes. */
    public int refreshMerchantCategories() {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();int changed=0;
        List<MerchantRule> rules=merchantRules();
        try(Cursor c=sql.rawQuery("SELECT id,merchant,category FROM transactions WHERE kind='expense' AND user_edited=0 " +
                "AND fee_parent_id IS NULL AND source NOT IN ('يدوي','CSV')",null)) {
            while(c.moveToNext()) {
                String current=c.getString(2);
                if(!spendingCategory(current) && !MoneyFlow.UNCATEGORIZED.equals(current)) continue;
                MerchantRule rule=matchingRule(c.getString(1),rules);
                // Ignore rules never remove historical records here.
                if(rule!=null && rule.ignored) continue;
                String category=rule==null?MerchantClassifier.category(c.getString(1)):rule.category;
                if(category==null || category.equals(current) || !spendingCategory(category)) continue;
                ContentValues values=new ContentValues();values.put("category",category);
                changed+=sql.update("transactions",values,"id=?",new String[]{Long.toString(c.getLong(0))});
            }
            sql.setTransactionSuccessful();return changed;
        } finally {sql.endTransaction();}
    }

    public void addAsset(String name, String kind, long valueMinor, String currency) {
        putAsset(null, name, kind, valueMinor, currency);
    }

    public void putAsset(Long id, String name, String kind, long valueMinor, String currency) {
        if (name.isBlank() || valueMinor < 0) return;
        ContentValues values = new ContentValues();
        values.put("name", name.trim()); values.put("kind", kind);
        values.put("value_minor", valueMinor); values.put("currency", currency);
        values.put("updated_at", System.currentTimeMillis());
        if (id == null) getWritableDatabase().insert("assets", null, values);
        else getWritableDatabase().update("assets", values, "id=?", new String[]{Long.toString(id)});
    }

    public List<Asset> assets() {
        List<Asset> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,kind,value_minor,currency,updated_at FROM assets ORDER BY id DESC", null)) {
            while (cursor.moveToNext()) result.add(new Asset(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3), cursor.getString(4), cursor.getLong(5)));
        }
        return result;
    }

    public void deleteAsset(long id) {
        getWritableDatabase().delete("assets", "id=?", new String[]{Long.toString(id)});
    }

    public void addDebt(String name, String direction, long amountMinor, String currency, String dueDate) {
        if (name.isBlank() || amountMinor <= 0) return;
        ContentValues values = new ContentValues();
        values.put("name", name.trim()); values.put("direction", direction);
        values.put("original_minor", amountMinor); values.put("remaining_minor", amountMinor);
        values.put("currency", currency); values.put("due_date", dueDate);
        getWritableDatabase().insert("debts", null, values);
    }

    public List<Debt> debts() {
        List<Debt> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,direction,original_minor,remaining_minor,currency,due_date FROM debts ORDER BY id DESC", null)) {
            while (cursor.moveToNext()) result.add(new Debt(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3), cursor.getLong(4), cursor.getString(5), cursor.getString(6)));
        }
        return result;
    }

    public void payDebt(long id, long amountMinor) {
        if (amountMinor <= 0) return;
        getWritableDatabase().execSQL("UPDATE debts SET remaining_minor=MAX(0,remaining_minor-?) WHERE id=?",
                new Object[]{amountMinor, id});
    }

    public void deleteDebt(long id) {
        getWritableDatabase().delete("debts", "id=?", new String[]{Long.toString(id)});
    }

    public List<Transaction> transactions() {
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                null, null, null, null, "occurred_at DESC", "500")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    /** Uncapped ledger count; UI lists intentionally show only the latest 500 rows. */
    public long transactionCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM transactions",null)) {
            c.moveToFirst();return c.getLong(0);
        }
    }

    /** The row inserted most recently, used for the instant capture notification. */
    public Transaction latestInserted() {
        return transactionWhere(null, null, "id DESC");
    }

    public Transaction transactionById(long id) {
        return transactionWhere("id=?", new String[]{Long.toString(id)}, null);
    }

    private Transaction transactionWhere(String where, String[] args, String order) {
        try (Cursor cursor = getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                where, args, null, null, order, "1")) {
            if (!cursor.moveToFirst()) return null;
            return new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13));
        }
    }

    public List<Transaction> transactionsForMonth(LocalDate month) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                "occurred_at>=? AND occurred_at<?", new String[]{Long.toString(start), Long.toString(end)},
                null, null, "occurred_at DESC")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    public List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive) {
        return transactionsBetween(startDate,endExclusive,ZoneId.systemDefault());
    }

    public List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive,ZoneId zone) {
        long start = startDate.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli();
        List<Transaction> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("transactions", TRANSACTION_COLUMNS,
                "occurred_at>=? AND occurred_at<?", new String[]{Long.toString(start), Long.toString(end)},
                null, null, "occurred_at DESC,id DESC")) {
            while (cursor.moveToNext()) result.add(new Transaction(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getString(3), cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), cursor.isNull(7) ? null : cursor.getLong(7), cursor.getString(8),
                    cursor.getString(9), cursor.isNull(10) ? null : cursor.getLong(10), cursor.getString(11), cursor.getInt(12)!=0,cursor.getString(13)));
        }
        return result;
    }

    public List<String> months() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT strftime('%Y-%m', occurred_at/1000, 'unixepoch', 'localtime') AS month " +
                "FROM transactions ORDER BY month DESC", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    public java.util.Map<String, Long> monthlyTotals(LocalDate month) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        java.util.Map<String, Long> totals = new java.util.LinkedHashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT currency, SUM(amount_minor) FROM transactions WHERE kind='expense' AND occurred_at>=? AND occurred_at<? GROUP BY currency",
                new String[]{Long.toString(start), Long.toString(end)})) {
            while (cursor.moveToNext()) totals.put(cursor.getString(0), cursor.getLong(1));
        }
        return totals;
    }

    public long monthlyTotalForKind(LocalDate month, String currency, String kind) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE kind=? AND currency=? AND occurred_at>=? AND occurred_at<?",
                new String[]{kind, currency, Long.toString(start), Long.toString(end)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public List<CategoryTotal> monthlyCategoryTotals(LocalDate month) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<CategoryTotal> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT category, currency, SUM(amount_minor) FROM transactions " +
                "WHERE kind='expense' AND occurred_at>=? AND occurred_at<? GROUP BY category, currency ORDER BY currency, SUM(amount_minor) DESC",
                new String[]{Long.toString(start), Long.toString(end)})) {
            while (cursor.moveToNext()) result.add(new CategoryTotal(cursor.getString(0),
                    cursor.getString(1), cursor.getLong(2)));
        }
        return result;
    }

    public void updateCategory(long id, String category) {
        ContentValues values = new ContentValues();
        values.put("category", category);
        getWritableDatabase().update("transactions", values, "id=?", new String[]{Long.toString(id)});
    }

    /** Bulk categorization preserves amounts/accounts and survives subsequent SMS rereads. */
    public int updateCategories(List<Long> ids, String category) {
        if(category==null || category.isBlank())throw new IllegalArgumentException("Empty category");
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();int changed=0;
        try {
            ContentValues values=new ContentValues();values.put("category",category);
            values.put("user_edited",1);values.put("needs_review",0);
            for(Long id:new java.util.LinkedHashSet<>(ids))
                changed+=sql.update("transactions",values,"id=? AND kind='expense'",new String[]{Long.toString(id)});
            sql.setTransactionSuccessful();return changed;
        } finally {sql.endTransaction();}
    }

    public long putCommitmentFromTransaction(long transactionId,String merchant,long amount,String currency,LocalDate due,String kind) {
        if(merchant==null||merchant.isBlank()||amount<=0||due==null||!("subscription".equals(kind)||"bill".equals(kind)))throw new IllegalArgumentException("Invalid commitment");
        Transaction origin=transactionById(transactionId);
        if(origin==null || !"expense".equals(origin.kind))return -1;
        if(!origin.currency.equals(currency))throw new IllegalArgumentException("Currency must match origin");
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            long id=putCommitment(null,merchant,amount,currency,due,"monthly",kind,null,0,1,
                    origin.merchant.toLowerCase(Locale.ROOT),origin.paymentSourceId);
            if(id>=0) {
                ContentValues values=new ContentValues();values.put("origin_transaction_id",transactionId);
                sql.update("recurring",values,"id=?",new String[]{Long.toString(id)});
            }
            sql.setTransactionSuccessful();return id;
        } finally {sql.endTransaction();}
    }

    public Long commitmentOriginId(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT origin_transaction_id FROM recurring WHERE id=?",new String[]{Long.toString(id)})) {
            return c.moveToFirst()&&!c.isNull(0)?c.getLong(0):null;
        }
    }

    public void updateTransaction(long id, long amountMinor, String currency, String merchant, String category, Long sourceId) {
        updateTransaction(id, amountMinor, currency, merchant, category, sourceId, null, null, null, null);
    }

    public void updateTransaction(long id, long amountMinor, String currency, String merchant,
                                  String category, Long sourceId, String kind, Long destinationId,
                                  String note, Long occurredAt) {
        if (amountMinor <= 0 || merchant.isBlank()) return;
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
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            sql.update("transactions", values, "id=?", new String[]{Long.toString(id)});
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }

    public long addPaymentSource(String name, String kind, String last4) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim()); values.put("kind", kind); values.put("last4", last4);
        long id = getWritableDatabase().insert("payment_sources", null, values);
        String identity = SourceIdentity.key(name, last4);
        if (id > 0 && identity != null) addSourceAlias(identity, id);
        return id;
    }

    public int sourceAliasCount(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM source_aliases WHERE payment_source_id=?",
                new String[]{Long.toString(id)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public void mergeSources(long keepId, long removeId) {
        if (keepId == removeId) return;
        if(!linkedCards(keepId).isEmpty() || !linkedCards(removeId).isEmpty() || accountId(keepId)!=keepId || accountId(removeId)!=removeId)
            throw new IllegalArgumentException("Unlink cards before merging message identities");
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            try (Cursor cursor = writable.rawQuery("SELECT id FROM payment_sources WHERE id IN (?,?)",
                    new String[]{Long.toString(keepId), Long.toString(removeId)})) {
                if (cursor.getCount() != 2) return;
            }
            writable.execSQL("UPDATE transactions SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE transactions SET destination_source_id=? WHERE destination_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE balance_snapshots SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.execSQL("UPDATE source_aliases SET payment_source_id=? WHERE payment_source_id=?",
                    new Object[]{keepId, removeId});
            writable.delete("payment_sources", "id=?", new String[]{Long.toString(removeId)});
            writable.setTransactionSuccessful();
        } finally { writable.endTransaction(); }
    }

    public List<PaymentSource> paymentSources() {
        List<PaymentSource> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT p.id,p.name,p.kind,p.last4,p.parent_source_id,p.display_name,p.archived,p.reviewed," +
                "MAX(COALESCE((SELECT MAX(occurred_at) FROM transactions t WHERE t.payment_source_id=p.id OR t.destination_source_id=p.id),0), " +
                "COALESCE((SELECT MAX(occurred_at) FROM balance_snapshots b WHERE b.payment_source_id=p.id),0)) AS activity " +
                "FROM payment_sources p ORDER BY activity DESC,p.id DESC",
                null)) {
            while (cursor.moveToNext()) result.add(new PaymentSource(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
                    cursor.isNull(4) ? null : cursor.getLong(4),cursor.getString(5),cursor.getInt(6)!=0,cursor.getInt(7)!=0,cursor.getLong(8)));
        }
        return result;
    }

    public PaymentSource paymentSource(long id) {
        for (PaymentSource source : paymentSources()) if (source.id == id) return source;
        return null;
    }

    public long accountId(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COALESCE(parent_source_id,id) FROM payment_sources WHERE id=?",
                new String[]{Long.toString(id)})) { return cursor.moveToFirst() ? cursor.getLong(0) : id; }
    }

    /** Read-only grouping by confirmed parent relationships. Same-bank cards are never guessed to be linked. */
    public java.util.Map<Long,List<Transaction>> accountTransactionGroups(LocalDate from,LocalDate to,String currency) {
        java.util.Map<Long,Long> roots=new java.util.HashMap<>();
        for(PaymentSource source:paymentSources()) roots.put(source.id,source.parentSourceId==null?source.id:source.parentSourceId);
        java.util.Map<Long,List<Transaction>> groups=new java.util.LinkedHashMap<>();
        for(Transaction tx:transactionsBetween(from,to)) {
            if(!currency.equals(tx.currency)) continue;
            Long debit=roots.get(tx.paymentSourceId);
            Long credit="transfer".equals(tx.kind)?roots.get(tx.destinationSourceId):null;
            if(debit!=null) groups.computeIfAbsent(debit,k->new ArrayList<>()).add(tx);
            if(credit!=null && !credit.equals(debit)) groups.computeIfAbsent(credit,k->new ArrayList<>()).add(tx);
        }
        return groups;
    }

    /** Keep card/SMS identities and all financial rows intact; only the confirmed relationship changes. */
    public void linkCardToAccount(long cardId, long accountId) {
        PaymentSource card=paymentSource(cardId), account=paymentSource(accountId);
        if (card==null || account==null || cardId==accountId || !"بطاقة".equals(card.kind) ||
                !"حساب".equals(account.kind) || account.parentSourceId!=null || card.archived || account.archived ||
                !java.util.Objects.equals(SourceIdentity.bank(card.name),SourceIdentity.bank(account.name)))
            throw new IllegalArgumentException("Choose a debit card and its account in the same bank");
        SQLiteDatabase writable=getWritableDatabase();writable.beginTransaction();
        try {
            ContentValues cardValues=new ContentValues();cardValues.put("parent_source_id",accountId);cardValues.put("reviewed",1);
            writable.update("payment_sources",cardValues,"id=?",new String[]{Long.toString(cardId)});
            ContentValues accountValues=new ContentValues();accountValues.put("reviewed",1);
            writable.update("payment_sources",accountValues,"id=?",new String[]{Long.toString(accountId)});
            writable.setTransactionSuccessful();
        } finally { writable.endTransaction(); }
    }

    public void unlinkCard(long cardId) {
        ContentValues values=new ContentValues();values.putNull("parent_source_id");values.put("reviewed",0);
        getWritableDatabase().update("payment_sources",values,"id=?",new String[]{Long.toString(cardId)});
    }

    public void updateSourceProfile(long id,String displayName,boolean archived,boolean reviewed) {
        ContentValues values=new ContentValues();values.put("display_name",displayName==null?"":displayName.trim());
        values.put("archived",archived?1:0);values.put("reviewed",reviewed?1:0);
        getWritableDatabase().update("payment_sources",values,"id=?",new String[]{Long.toString(id)});
    }

    public List<PaymentSource> accountSources(boolean includeArchived) {
        List<PaymentSource> result=new ArrayList<>();
        for (PaymentSource source:paymentSources())
            if(source.parentSourceId==null && (includeArchived || !source.archived)) result.add(source);
        return result;
    }

    public List<PaymentSource> selectableSources() {
        List<PaymentSource> result=new ArrayList<>();
        for(PaymentSource source:paymentSources()) {
            PaymentSource account=source.parentSourceId==null?source:paymentSource(source.parentSourceId);
            if(!source.archived && account!=null && !account.archived) result.add(source);
        }
        return result;
    }

    public List<PaymentSource> linkedCards(long accountId) {
        List<PaymentSource> result=new ArrayList<>();
        for(PaymentSource source:paymentSources())
            if(source.parentSourceId!=null && source.parentSourceId==accountId) result.add(source);
        return result;
    }

    public String sourceLabel(long id) {
        PaymentSource source=paymentSource(id);
        if(source==null) return "حساب غير محدد";
        String label=source.name+" — "+(source.displayName==null || source.displayName.isBlank()?source.kind:source.displayName)+
                (source.last4==null?"":" •"+source.last4);
        if(source.parentSourceId!=null) {
            PaymentSource account=paymentSource(source.parentSourceId);
            if(account!=null) label+=" ← حساب •"+(account.last4==null?"بدون رقم":account.last4);
        }
        return label+(source.archived?" · مؤرشف":"");
    }

    public long sourceLastActivity(long sourceId) {
        long root=accountId(sourceId),last=0;
        for(PaymentSource source:paymentSources())
            if(source.id==root || source.parentSourceId!=null && source.parentSourceId==root) last=Math.max(last,source.lastActivityAt);
        return last;
    }

    public void deletePaymentSource(long id) {
        ContentValues values = new ContentValues(); values.putNull("payment_source_id");
        getWritableDatabase().update("transactions", values, "payment_source_id=?", new String[]{Long.toString(id)});
        getWritableDatabase().delete("balance_snapshots", "payment_source_id=?", new String[]{Long.toString(id)});
        getWritableDatabase().delete("source_aliases", "payment_source_id=?", new String[]{Long.toString(id)});
        getWritableDatabase().delete("payment_sources", "id=?", new String[]{Long.toString(id)});
    }

    public void putBudget(LocalDate month, String category, String currency, long amountMinor) {
        ContentValues values = new ContentValues();
        values.put("month", month.toString().substring(0, 7)); values.put("category", category);
        values.put("currency", currency); values.put("amount_minor", amountMinor);
        getWritableDatabase().insertWithOnConflict("budgets", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<Budget> budgets(LocalDate month) {
        List<Budget> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,category,currency,amount_minor FROM budgets WHERE month=? ORDER BY category",
                new String[]{month.toString().substring(0, 7)})) {
            while (cursor.moveToNext()) result.add(new Budget(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)));
        }
        return result;
    }

    public void deleteBudget(long id) { getWritableDatabase().delete("budgets", "id=?", new String[]{Long.toString(id)}); }

    public long putBudgetPlan(Long existingId, String name, long amountMinor, String currency,
                              String cycle, LocalDate anchor, int durationDays, List<String> categories) {
        if (name.isBlank() || amountMinor <= 0 || categories.isEmpty()) return -1;
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("name", name.trim());
            values.put("amount_minor", amountMinor);
            values.put("currency", currency);
            values.put("cycle", cycle);
            values.put("anchor_date", anchor.toString());
            values.put("duration_days", Math.max(1, durationDays));
            long id;
            if (existingId == null) id = writable.insert("budget_plans", null, values);
            else {
                id = existingId;
                writable.update("budget_plans", values, "id=?", new String[]{Long.toString(id)});
                writable.delete("budget_plan_categories", "plan_id=?", new String[]{Long.toString(id)});
            }
            if (id < 0) return -1;
            for (String category : categories) {
                ContentValues link = new ContentValues();
                link.put("plan_id", id);
                link.put("category", category);
                writable.insertWithOnConflict("budget_plan_categories", null, link, SQLiteDatabase.CONFLICT_IGNORE);
            }
            writable.setTransactionSuccessful();
            return id;
        } finally {
            writable.endTransaction();
        }
    }

    public List<BudgetPlan> budgetPlans() {
        List<BudgetPlan> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,name,amount_minor,currency,cycle,anchor_date,duration_days FROM budget_plans ORDER BY id DESC", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                List<String> categories = new ArrayList<>();
                try (Cursor links = getReadableDatabase().rawQuery(
                        "SELECT category FROM budget_plan_categories WHERE plan_id=? ORDER BY category",
                        new String[]{Long.toString(id)})) {
                    while (links.moveToNext()) categories.add(links.getString(0));
                }
                result.add(new BudgetPlan(id, cursor.getString(1), cursor.getLong(2), cursor.getString(3),
                        cursor.getString(4), LocalDate.parse(cursor.getString(5)), cursor.getInt(6), categories));
            }
        }
        return result;
    }

    public long spentForBudget(BudgetPlan plan, LocalDate today) {
        LocalDate[] period = BudgetPeriod.bounds(plan.cycle, plan.anchor, plan.durationDays, today);
        long start = period[0].atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = period[1].atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long spent = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT category,amount_minor FROM transactions WHERE kind='expense' AND currency=? " +
                "AND occurred_at>=? AND occurred_at<?",
                new String[]{plan.currency, Long.toString(start), Long.toString(end)})) {
            while (cursor.moveToNext())
                if (plan.categories.contains(cursor.getString(0))) spent += cursor.getLong(1);
        }
        return spent;
    }

    public void deleteBudgetPlan(long id) {
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            writable.delete("budget_plan_categories", "plan_id=?", new String[]{Long.toString(id)});
            writable.delete("budget_plans", "id=?", new String[]{Long.toString(id)});
            writable.setTransactionSuccessful();
        } finally { writable.endTransaction(); }
    }

    public void deleteTransaction(long id) {
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try (Cursor cursor = writable.rawQuery("SELECT unique_key FROM transactions WHERE id=?",
                new String[]{Long.toString(id)})) {
            if (cursor.moveToFirst() && !cursor.getString(0).startsWith("manual:")) {
                ContentValues values = new ContentValues();
                values.put("unique_key", cursor.getString(0));
                writable.insertWithOnConflict("excluded_messages", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            writable.delete("transactions", "fee_parent_id=?", new String[]{Long.toString(id)});
            writable.delete("reimbursements", "income_id=? OR expense_id=?", new String[]{Long.toString(id), Long.toString(id)});
            writable.delete("transactions", "id=?", new String[]{Long.toString(id)});
            writable.execSQL("INSERT OR IGNORE INTO excluded_messages SELECT unique_key FROM capture_evidence WHERE transaction_id=?", new Object[]{id});
            writable.delete("capture_evidence", "transaction_id=?", new String[]{Long.toString(id)});
            writable.setTransactionSuccessful();
        } finally {
            writable.endTransaction();
        }
    }

    public long transferFee(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE fee_parent_id=?",new String[]{Long.toString(id)})) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /** User-confirmed reconciliation: one principal transfer and a separate expense for its bank fee. */
    public void reconcileOwnTransfer(long debitId,long principal,long fee,long from,long to,List<Long> receiptIds) {
        SQLiteDatabase writable=getWritableDatabase();writable.beginTransaction();
        try {
            Transaction debit=transactionById(debitId);
            if(debit==null || principal<=0 || fee<0 || accountId(from)==accountId(to) || "income".equals(debit.kind)) throw new IllegalArgumentException("Invalid transfer");
            if(hasReimbursement(debitId))throw new IllegalArgumentException("Unlink repayment before changing to own transfer");
            for(long receiptId:receiptIds)if(hasReimbursement(receiptId))
                throw new IllegalArgumentException("Unlink repayment before changing to own transfer");
            long existingFee=0;
            try(Cursor c=writable.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE fee_parent_id=?",new String[]{Long.toString(debitId)})) {if(c.moveToFirst()) existingFee=c.getLong(0);}
            if(existingFee>0 && existingFee!=fee) throw new IllegalArgumentException("Existing fee differs; review its source");
            if(debit.amountMinor!=principal+fee && !(debit.amountMinor==principal && existingFee==fee))
                throw new IllegalArgumentException("Principal and fee must equal the confirmed debit");
            for(long receiptId:receiptIds) {
                if(receiptId==debitId) continue;
                Transaction receipt=transactionById(receiptId);
                if(receipt==null) continue;
                if(!receipt.currency.equals(debit.currency) || receipt.amountMinor!=principal || "expense".equals(receipt.kind))
                    throw new IllegalArgumentException("Receipt does not match principal");
                if(!mergeEntries(debitId,receiptId,"user confirmed receipt of own transfer"))
                    throw new IllegalArgumentException("Related records conflict; review before merging");
            }
            ContentValues values=new ContentValues();values.put("amount_minor",principal);values.put("kind","transfer");values.put("category","تحويلات");
            values.put("payment_source_id",from);values.put("destination_source_id",to);values.put("user_edited",1);values.put("needs_review",0);
            values.put("merchant","تحويل بين حساباتي");writable.update("transactions",values,"id=?",new String[]{Long.toString(debitId)});
            if(fee>0 && existingFee==0) {
                String bank="البنك";for(PaymentSource source:paymentSources()) if(source.id==from) bank=source.name;
                boolean added=insertEntry("fee:"+debitId,debit.when,fee,debit.currency,"رسوم تحويل "+bank,"رسوم تحويل",
                        "رسوم من العملية",null,null,from,"expense",null,"رسوم مؤكدة من فرق الخصم وأصل التحويل");
                if(added) {
                    ContentValues related=new ContentValues();related.put("fee_parent_id",debitId);related.put("user_edited",1);
                    writable.update("transactions",related,"unique_key=?",new String[]{"fee:"+debitId});
                }
            }
            writable.setTransactionSuccessful();
        } finally {writable.endTransaction();}
    }

    /** Consolidates bank and notification copies of an incoming payment after confirmation. */
    public void reconcileIncome(long keepId,List<Long> receiptIds) {
        SQLiteDatabase writable=getWritableDatabase();writable.beginTransaction();
        try {
            Transaction income=transactionById(keepId);
            if(income==null||"expense".equals(income.kind)) throw new IllegalArgumentException("Not an incoming payment");
            Long account=income.paymentSourceId==null?income.destinationSourceId:income.paymentSourceId;
            for(long receiptId:receiptIds) {
                Transaction receipt=transactionById(receiptId);
                if(receipt==null || receiptId==keepId) continue;
                if(!receipt.currency.equals(income.currency) || receipt.amountMinor!=income.amountMinor || "expense".equals(receipt.kind))
                    throw new IllegalArgumentException("Different incoming payment");
                if(!mergeEntries(keepId,receiptId,"user confirmed bank and notification receipt"))
                    throw new IllegalArgumentException("Related records conflict; review before merging");
            }
            ContentValues values=new ContentValues();values.put("kind","income");values.put("category","دخل");
            values.put("merchant",income.merchant.replaceAll("رقم\\s*مرجعي.*","").trim());
            for(SourceEvidence evidence:sourceEvidence(keepId)) if("SMS".equals(evidence.source)) {
                ExpenseParser.Parsed parsed=ExpenseParser.parseIncome(evidence.sender,evidence.body);
                if(parsed!=null && parsed.amountMinor==income.amountMinor && parsed.currency.equals(income.currency)) {
                    ExpenseParser.EventTime event=ExpenseParser.eventTimeWithPrecision(evidence.body,evidence.receivedAt);
                    values.put("occurred_at",event.when);
                    if(income.timePrecision!=null)values.put("time_precision",event.precision);
                    break;
                }
            }
            if(account==null) values.putNull("payment_source_id");else values.put("payment_source_id",account);
            values.putNull("destination_source_id");values.put("user_edited",1);values.put("needs_review",0);
            writable.update("transactions",values,"id=?",new String[]{Long.toString(keepId)});
            writable.setTransactionSuccessful();
        } finally {writable.endTransaction();}
    }

    public boolean addRecurring(String merchant, long amount, String currency, LocalDate nextDue) {
        return addRecurring(merchant, amount, currency, nextDue, "monthly", "subscription");
    }

    public boolean addRecurring(String merchant, long amount, String currency, LocalDate nextDue,
                                String cycle, String kind) {
        return putCommitment(null, merchant, amount, currency, nextDue, cycle, kind, null, 0, 1,
                merchant.toLowerCase(Locale.ROOT), null) != -1;
    }

    /** Inserts or updates an installment, subscription or bill. Returns the row id, or -1 for a duplicate. */
    public long putCommitment(Long id, String merchant, long amount, String currency, LocalDate nextDue,
                              String cycle, String kind, Integer totalCount, int paidCount, int remindDays,
                              String matchText, Long paymentSourceId) {
        ContentValues values = new ContentValues();
        values.put("merchant", merchant.trim());
        values.put("amount_minor", amount);
        values.put("currency", currency);
        values.put("next_due", nextDue.toString());
        values.put("cycle", cycle);
        values.put("kind", kind);
        if (totalCount == null) values.putNull("total_count"); else values.put("total_count", totalCount);
        values.put("paid_count", Math.max(0, paidCount));
        values.put("remind_days", remindDays);
        values.put("match_text", matchText == null || matchText.isBlank() ? merchant.trim().toLowerCase(Locale.ROOT) :
                matchText.trim().toLowerCase(Locale.ROOT));
        if (paymentSourceId == null) values.putNull("payment_source_id"); else values.put("payment_source_id", paymentSourceId);
        Recurring previous = id == null ? null : recurringById(id);
        values.put("status", previous != null && previous.isDone() ||
                totalCount != null && paidCount >= totalCount ? "done" : "active");
        values.put("due_day", nextDue.getDayOfMonth());
        values.put("needs_review", ExpenseParser.genericMerchant(merchant) ? 1 : 0);
        values.putNull("last_alert_date");
        if (id != null) {
            getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
            return id;
        }
        return getWritableDatabase().insertWithOnConflict("recurring", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<Recurring> recurring() {
        List<Recurring> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("recurring",
                new String[]{"id", "merchant", "amount_minor", "currency", "next_due", "last_alert_date", "cycle", "kind",
                        "total_count", "paid_count", "remind_days", "match_text", "payment_source_id", "last_paid_date", "status", "due_day", "needs_review"},
                null, null, null, null, "status='done' ASC, next_due ASC")) {
            while (cursor.moveToNext()) result.add(new Recurring(cursor.getLong(0), cursor.getString(1),
                    cursor.getLong(2), cursor.getString(3), LocalDate.parse(cursor.getString(4)),
                    cursor.getString(5), cursor.getString(6), cursor.getString(7),
                    cursor.isNull(8) ? null : cursor.getInt(8), cursor.getInt(9), cursor.getInt(10),
                    cursor.getString(11), cursor.isNull(12) ? null : cursor.getLong(12),
                    cursor.getString(13), cursor.getString(14), cursor.isNull(15) ? 0 : cursor.getInt(15), cursor.getInt(16)!=0));
        }
        return result;
    }

    public Recurring recurringById(long id) {
        for (Recurring item : recurring()) if (item.id == id) return item;
        return null;
    }

    public void setDueDay(long id, int day) {
        ContentValues values = new ContentValues();
        values.put("due_day", Math.max(1, Math.min(31, day)));
        getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    public void setRemindDays(long id, int days) {
        ContentValues values = new ContentValues();
        values.put("remind_days", days);
        values.putNull("last_alert_date");
        getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    public void removeRecurring(long id) {
        getWritableDatabase().delete("recurring", "id=?", new String[]{Long.toString(id)});
    }

    public void markAlerted(long id, LocalDate today) {
        ContentValues values = new ContentValues();
        values.put("last_alert_date", today.toString());
        getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
    }

    /**
     * A payment made a few days before a due date pays that due date (e.g. the 30 Sep transfer pays the 1 Oct installment).
     * If the stored next due date is still that covered date, move it one cycle on. paid_count already includes the payment.
     * Window: 7 days for monthly, 2 for weekly, 15 for yearly. Returns how many commitments moved.
     */
    public int normalizeEarlyPayments() {
        int moved = 0;
        java.util.Map<Long,String> coveredDue = new java.util.HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,last_paid_due FROM recurring WHERE last_paid_due IS NOT NULL", null)) {
            while (c.moveToNext()) coveredDue.put(c.getLong(0), c.getString(1));
        }
        for (Recurring item : recurring()) {
            if (item.isDone() || item.lastPaidDate == null) continue;
            LocalDate paid;
            try { paid = LocalDate.parse(item.lastPaidDate); } catch (RuntimeException invalid) { continue; }
            LocalDate due = item.nextDue;
            String known = coveredDue.get(item.id);
            boolean covered;
            if (known != null) {
                // We know which due date the last payment settled: the next one must come after it.
                covered = !due.isAfter(LocalDate.parse(known));
            } else {
                // Older rows: a payment shortly before the due date, or earlier in the same month for a monthly bill
                // (e.g. a card statement paid on the 10th for the 26th), settles that due date.
                long window = "weekly".equals(item.cycle) ? 2 : "yearly".equals(item.cycle) ? 15 : 7;
                long early = java.time.temporal.ChronoUnit.DAYS.between(paid, due);
                boolean sameMonth = "monthly".equals(item.cycle) && java.time.YearMonth.from(paid).equals(java.time.YearMonth.from(due));
                covered = early >= 0 && (early <= window || sameMonth);
            }
            if (!covered) continue;
            LocalDate next = item.nextAfter(due);
            ContentValues values = new ContentValues();
            values.put("next_due", next.toString());
            values.put("last_paid_due", due.toString());
            values.putNull("last_alert_date");
            getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(item.id)});
            moved++;
        }
        return moved;
    }

    /** Records one payment of a commitment and moves its due date to the next cycle, at most once per cycle. */
    public boolean markRecurringPaid(long id, LocalDate day) {
        normalizeEarlyPayments();
        Recurring item = recurringById(id);
        if (item == null || item.isDone()) return false;
        if (item.lastPaidDate != null) {
            long gap = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(item.lastPaidDate), day);
            if (gap < ("weekly".equals(item.cycle) ? 4 : 15)) return false;
        }
        LocalDate next = item.nextAfter(item.nextDue);
        while (!next.isAfter(day)) next = item.nextAfter(next);
        int paid = item.paidCount + 1;
        ContentValues values = new ContentValues();
        values.put("last_paid_due", item.nextDue.toString());
        values.put("next_due", next.toString());
        values.put("paid_count", paid);
        values.put("last_paid_date", day.toString());
        values.putNull("last_alert_date");
        if (item.totalCount != null && paid >= item.totalCount) values.put("status", "done");
        getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)});
        return true;
    }

    /** Stops future dues without inventing a payment. Optionally confirms every remaining installment as paid. */
    public boolean finishRecurring(long id, boolean allInstallmentsPaid) {
        Recurring item = recurringById(id);
        if (item == null || item.isDone()) return false;
        ContentValues values = new ContentValues();
        values.put("status", "done");
        values.putNull("last_alert_date");
        if (allInstallmentsPaid && "installment".equals(item.kind) && item.totalCount != null)
            values.put("paid_count", item.totalCount);
        return getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)}) == 1;
    }

    public boolean reopenRecurring(long id) {
        Recurring item = recurringById(id);
        if (item == null || !item.isDone() ||
                item.totalCount != null && item.paidCount >= item.totalCount) return false;
        ContentValues values = new ContentValues();
        values.put("status", "active");
        values.putNull("last_alert_date");
        return getWritableDatabase().update("recurring", values, "id=?", new String[]{Long.toString(id)}) == 1;
    }

    private void advanceRecurring(String merchant, long amount, String currency, long when) {
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        List<Recurring> matches=new ArrayList<>();
        List<Recurring> all=recurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview ||
                    ExpenseParser.genericMerchant(merchant)) continue;
            boolean named = item.merchant.equalsIgnoreCase(merchant) || item.matches(merchant);
            if (!named) continue;
            long daysFromDue = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(item.nextDue, day));
            boolean sameAmount = item.currency.equals(currency) &&
                    Math.abs(item.amountMinor - amount) <= 1;
            if (sameAmount && daysFromDue <= 12) matches.add(item);
        }
        if(matches.size()==1) markRecurringPaid(matches.get(0).id, day);
    }

    private static final java.util.regex.Pattern COMMITMENT_PAID = java.util.regex.Pattern.compile(
            "(?i)(تم\\s*(?:سداد|دفع|استلام|خصم)|سداد\\s*(?:القسط|قسط)|سددت|paid\\s+successfully|successfully\\s+paid|" +
                    "payment\\s+(?:of\\s+.{0,30})?(?:has\\s+been\\s+)?(?:received|successful)|installment\\s+(?:paid|received))");
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]{1,2})?");

    /** Marks an installment paid from a provider message such as valU or a bank, even when it is not a card purchase. */
    private boolean matchCommitmentMessage(String sender, String body, long when) {
        if (body == null) return false;
        String text = ExpenseParser.normalizeDigits(body);
        if (!COMMITMENT_PAID.matcher(text).find()) return false;
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        String haystack = ((sender == null ? "" : sender) + " " + text).toLowerCase(Locale.ROOT);
        boolean changed = false;
        List<Recurring> all=recurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview || !item.matches(haystack)) continue;
            if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(item.nextDue, day)) > 12) continue;
            java.util.regex.Matcher number = NUMBER.matcher(text);
            boolean amountFound = false;
            while (number.find()) {
                long minor = ExpenseParser.parseMinor(number.group().replace(",", ""));
                if (minor > 0 && Math.abs(minor - item.amountMinor) <= Math.max(100, item.amountMinor / 50)) {
                    amountFound = true; break;
                }
            }
            if (amountFound) changed |= markRecurringPaid(item.id, day);
        }
        return changed;
    }

    // ---- Reimbursements: money that came back for something bought on someone else's behalf ----

    /** What each linked row should be reduced by: the purchase by what came back, the incoming payment by the part it repaid. */
    public java.util.Map<Long,Long> reimbursementOffsets() {
        java.util.Map<Long,Long> offsets=new java.util.HashMap<>();
        java.util.Map<Long,Long> remaining=new java.util.HashMap<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT r.income_id,r.expense_id,r.amount_minor,i.amount_minor,e.amount_minor," +
                "i.kind,e.kind,i.currency,e.currency FROM reimbursements r " +
                "LEFT JOIN transactions i ON i.id=r.income_id LEFT JOIN transactions e ON e.id=r.expense_id " +
                "ORDER BY i.occurred_at,r.income_id",null)) {
            while (c.moveToNext()) {
                long incomeId=c.getLong(0),expenseId=c.getLong(1);
                boolean valid="income".equals(c.getString(5)) && "expense".equals(c.getString(6)) &&
                        c.getString(7)!=null && c.getString(7).equals(c.getString(8));
                long left=remaining.getOrDefault(expenseId,c.getLong(4));
                long applied=valid?Math.max(0,Math.min(c.getLong(2),Math.min(c.getLong(3),left))):0;
                if (applied>0) {
                    offsets.merge(incomeId,applied,Long::sum);
                    offsets.merge(expenseId,applied,Long::sum);
                }
                remaining.put(expenseId,Math.max(0,left-applied));
            }
        }
        return offsets;
    }

    /** The amount a row contributes to spending/inflow after reimbursements; never negative. */
    public static long effectiveAmount(Transaction tx,java.util.Map<Long,Long> offsets) {
        Long offset=offsets.get(tx.id);
        return offset==null?tx.amountMinor:Math.max(0,tx.amountMinor-offset);
    }

    public long reimbursedOf(long expenseId) {
        return reimbursementOffsets().getOrDefault(expenseId,0L);
    }

    /** The purchase an incoming payment repaid, or null. */
    public Transaction reimbursedExpense(long incomeId) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT expense_id FROM reimbursements WHERE income_id=?",new String[]{Long.toString(incomeId)})) {
            return c.moveToFirst()?transactionById(c.getLong(0)):null;
        }
    }

    public List<Transaction> reimbursingIncomes(long expenseId) {
        List<Transaction> result=new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT income_id FROM reimbursements WHERE expense_id=?",new String[]{Long.toString(expenseId)})) {
            while (c.moveToNext()) { Transaction tx=transactionById(c.getLong(0)); if (tx!=null) result.add(tx); }
        }
        return result;
    }

    /** Default to the incoming payment's day; older purchases are an explicit second choice. */
    public List<Transaction> reimbursementCandidates(Transaction income) {
        return reimbursementCandidates(income,false);
    }

    public List<Transaction> reimbursementCandidates(Transaction income,boolean older) {
        List<Transaction> result=new ArrayList<>();
        if (income==null || !"income".equals(income.kind)) return result;
        java.util.Map<Long,Long> offsets=reimbursementOffsets();
        LocalDate day=Instant.ofEpochMilli(income.when).atZone(ZoneId.systemDefault()).toLocalDate();
        for (Transaction tx:transactionsBetween(older?LocalDate.of(1970,1,1):day,older?day:day.plusDays(1)))
            if ("expense".equals(tx.kind) && tx.currency.equals(income.currency) && reimbursementPrecedes(tx,income) &&
                    effectiveAmount(tx,offsets)>0 && MoneyFlow.bucket(tx.kind,tx.category)!=MoneyFlow.Bucket.OPEN_TRANSFER) result.add(tx);
        result.sort((a,b)->{
            int byTime=Long.compare(b.when,a.when);
            return byTime!=0?byTime:Long.compare(b.id,a.id);
        });
        return result;
    }

    private static boolean reimbursementPrecedes(Transaction expense,Transaction income) {
        if("date_only".equals(expense.timePrecision)||"date_only".equals(income.timePrecision)) {
            ZoneId zone=ZoneId.systemDefault();
            return !Instant.ofEpochMilli(expense.when).atZone(zone).toLocalDate()
                    .isAfter(Instant.ofEpochMilli(income.when).atZone(zone).toLocalDate());
        }
        return expense.when<=income.when+60_000;
    }

    static final class ReimbursementPreview {
        final Transaction income,expense;
        final LocalDate day;
        final long applied,personal,daySpending;
        final String fingerprint;
        ReimbursementPreview(Transaction income,Transaction expense,long applied,long personal,long daySpending,String fingerprint) {
            this.income=income;this.expense=expense;this.applied=applied;this.personal=personal;this.daySpending=daySpending;this.fingerprint=fingerprint;
            day=Instant.ofEpochMilli(expense.when).atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    ReimbursementPreview previewReimbursement(long incomeId,long expenseId) {
        Transaction income=transactionById(incomeId),expense=transactionById(expenseId);
        if(income==null || expense==null || !"income".equals(income.kind) || !"expense".equals(expense.kind) ||
                !income.currency.equals(expense.currency) || !reimbursementPrecedes(expense,income) || reimbursedExpense(incomeId)!=null ||
                MoneyFlow.bucket(expense.kind,expense.category)==MoneyFlow.Bucket.OPEN_TRANSFER)
            throw new IllegalArgumentException("Purchase or repayment changed");
        java.util.Map<Long,Long> offsets=reimbursementOffsets();
        long left=effectiveAmount(expense,offsets),applied=Math.min(left,income.amountMinor);
        if(applied<=0)throw new IllegalArgumentException("Nothing left to repay");
        LocalDate day=Instant.ofEpochMilli(expense.when).atZone(ZoneId.systemDefault()).toLocalDate();
        MoneyTotals totals=new MoneyTotals();
        for(Transaction tx:transactionsBetween(day,day.plusDays(1)))totals.add(tx.currency,tx.kind,tx.category,effectiveAmount(tx,offsets));
        MoneyTotals.Totals currencyTotals=totals.currencies.get(expense.currency);
        long spending=currencyTotals==null?0:currencyTotals.living;
        String fingerprint=income.when+":"+income.amountMinor+":"+income.currency+":"+income.merchant+":"+
                expense.when+":"+expense.amountMinor+":"+expense.category+":"+expense.merchant+":"+left+":"+spending+":"+
                income.timePrecision+":"+expense.timePrecision;
        return new ReimbursementPreview(income,expense,applied,left-applied,
                spending-(MoneyFlow.bucket(expense.kind,expense.category)==MoneyFlow.Bucket.LIVING?applied:0),fingerprint);
    }

    /** The confirmation must still describe the records the user actually reviewed. */
    long confirmReimbursement(ReimbursementPreview preview) {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            ReimbursementPreview current=previewReimbursement(preview.income.id,preview.expense.id);
            if(!current.fingerprint.equals(preview.fingerprint))throw new IllegalArgumentException("Preview changed");
            long applied=linkReimbursement(current.income.id,current.expense.id);
            sql.setTransactionSuccessful();return applied;
        } finally {sql.endTransaction();}
    }

    /** Undo this exact link only; never erase a replacement or another incoming repayment. */
    boolean undoReimbursement(long incomeId,long expenseId,long amount) {
        return getWritableDatabase().delete("reimbursements","income_id=? AND expense_id=? AND amount_minor=?",
                new String[]{Long.toString(incomeId),Long.toString(expenseId),Long.toString(amount)})==1;
    }

    /** Links an incoming payment to the purchase it repaid. Only the part up to what is left of the purchase counts. */
    public long linkReimbursement(long incomeId,long expenseId) {
        Transaction income=transactionById(incomeId),expense=transactionById(expenseId);
        if (income==null || expense==null || !"income".equals(income.kind) || !"expense".equals(expense.kind) ||
                !income.currency.equals(expense.currency)) throw new IllegalArgumentException("Not a purchase and its repayment");
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            sql.delete("reimbursements","income_id=?",new String[]{Long.toString(incomeId)});
            long left=expense.amountMinor-reimbursedOf(expenseId);
            long applied=Math.min(income.amountMinor,Math.max(0,left));
            if (applied<=0) throw new IllegalArgumentException("Purchase already repaid");
            ContentValues values=new ContentValues();values.put("income_id",incomeId);values.put("expense_id",expenseId);values.put("amount_minor",applied);
            sql.insertOrThrow("reimbursements",null,values);
            sql.setTransactionSuccessful();
            return applied;
        } finally {sql.endTransaction();}
    }

    public void unlinkReimbursement(long transactionId) {
        getWritableDatabase().delete("reimbursements","income_id=? OR expense_id=?",new String[]{Long.toString(transactionId),Long.toString(transactionId)});
    }

    // ---- Data repair: promotions, double captures, own transfers (see RepairRules) ----

    /** Bump when a new rule must re-run once over all existing data. */
    public static final int REPAIR_VERSION = 2;

    public static final class RepairReport {
        public int promotions, duplicates, ownTransfers, commitments, recategorized;
        public long movedMinor, feesMinor;
        public boolean changed() { return promotions + duplicates + ownTransfers + commitments + recategorized > 0; }
    }

    private List<RepairRules.Row> repairRows(long from, long to) {
        List<RepairRules.Row> rows=new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT t.id,t.occurred_at,t.received_at,t.amount_minor,t.currency,t.kind,t.category,t.source," +
                "(SELECT e.direction FROM capture_evidence e WHERE e.transaction_id=t.id ORDER BY e.received_at LIMIT 1)," +
                "t.payment_source_id,t.destination_source_id,t.user_edited,t.merchant FROM transactions t " +
                "WHERE t.occurred_at BETWEEN ? AND ? AND t.fee_parent_id IS NULL",new String[]{Long.toString(from),Long.toString(to)})) {
            while (c.moveToNext()) rows.add(new RepairRules.Row(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getString(4),
                    c.getString(5),c.getString(6),c.getString(7),c.getString(8),c.isNull(9)?null:c.getLong(9),c.isNull(10)?null:c.getLong(10),c.getInt(11)!=0,c.getString(12)));
        }
        return rows;
    }

    private java.util.Map<Long,Long> accountMap() {
        java.util.Map<Long,Long> map=new java.util.HashMap<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id,parent_source_id FROM payment_sources WHERE parent_source_id IS NOT NULL",null)) {
            while (c.moveToNext()) map.put(c.getLong(0),c.getLong(1));
        }
        return map;
    }

    /** Runs every rule over all data. Each change is archived and can be traced from the archive table. */
    public RepairReport repairAll() {
        RepairReport report=new RepairReport();
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            report.promotions=archivePromotions();
            report.recategorized=recategorizeWithCurrentRules();
            repairWindow(Long.MIN_VALUE/2,Long.MAX_VALUE/2,report);
            ContentValues log=new ContentValues();log.put("ran_at",System.currentTimeMillis());log.put("version",REPAIR_VERSION);
            log.put("promotions",report.promotions);log.put("duplicates",report.duplicates);log.put("own_transfers",report.ownTransfers);
            log.put("moved_minor",report.movedMinor);log.put("fees_minor",report.feesMinor);
            sql.insert("repair_log",null,log);
            sql.setTransactionSuccessful();
        } finally {sql.endTransaction();}
        return report;
    }

    /**
     * After each live capture: only rows near that moment are considered. Inside a caller's batch transaction
     * (e.g. importing selected SMS) it does nothing; the caller runs repairBetween once after committing.
     */
    public RepairReport repairAround(long when) {
        if (getWritableDatabase().inTransaction()) return new RepairReport();
        return repairBetween(when-86_400_000L,when+86_400_000L);
    }

    public RepairReport repairBetween(long from,long to) {
        RepairReport report=new RepairReport();
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {repairWindow(from,to,report);sql.setTransactionSuccessful();}
        catch (RuntimeException failed) {android.util.Log.e("FollowMoney","Repair skipped: "+failed.getClass().getSimpleName());/* never lose a capture because a repair failed */}
        finally {sql.endTransaction();}
        return report;
    }

    private void repairWindow(long from,long to,RepairReport report) {
        List<RepairRules.Row> rows=new ArrayList<>();
        for (RepairRules.Row row:repairRows(from,to)) if (!row.userEdited) rows.add(row);
        for (RepairRules.Duplicate duplicate:RepairRules.crossChannelDuplicates(rows)) {
            Transaction keep=transactionById(duplicate.keepId),remove=transactionById(duplicate.removeId);
            if (keep==null || remove==null) continue;
            if (ExpenseParser.genericMerchant(keep.merchant) && !ExpenseParser.genericMerchant(remove.merchant)) {
                ContentValues name=new ContentValues();name.put("merchant",remove.merchant);
                getWritableDatabase().update("transactions",name,"id=?",new String[]{Long.toString(keep.id)});
            }
            if(mergeEntries(duplicate.keepId,duplicate.removeId,"same payment: bank SMS and app notification"))report.duplicates++;
        }
        rows.clear();
        for (RepairRules.Row row:repairRows(from,to)) if (!row.userEdited) rows.add(row);
        java.util.Set<Long> debitAmounts=new java.util.HashSet<>();
        for (RepairRules.OwnTransfer own:RepairRules.ownTransfers(rows,accountMap())) {
            // reconcileOwnTransfer throws inside its own (nested) transaction, which would roll back this whole repair:
            // only call it when every one of its checks already holds.
            if (!canReconcile(own)) continue;
            try {
                long debit=own.principal+own.fee;
                reconcileOwnTransfer(own.outId,own.principal,own.fee,own.fromAccount,own.toAccount,java.util.Collections.singletonList(own.inId));
                ContentValues note=new ContentValues();note.put("note","اتعرف تلقائيًا: خرج من حسابك ووصل حسابك التاني خلال دقايق");
                getWritableDatabase().update("transactions",note,"id=? AND note IS NULL",new String[]{Long.toString(own.outId)});
                debitAmounts.add(debit);
                report.ownTransfers++;report.movedMinor+=own.principal;report.feesMinor+=own.fee;
            } catch (IllegalArgumentException skipped) {/* left for manual review */}
        }
        // Generic "commitments" guessed from these movements (e.g. a monthly top-up between own accounts) are not obligations.
        for (Recurring item:recurring())
            if (item.needsReview && ExpenseParser.genericMerchant(item.merchant) && debitAmounts.contains(item.amountMinor))
                removeRecurring(item.id);
    }

    private boolean canReconcile(RepairRules.OwnTransfer own) {
        Transaction debit=transactionById(own.outId),receipt=transactionById(own.inId);
        if(hasReimbursement(own.outId)||hasReimbursement(own.inId)){markMergeReview(own.outId,own.inId);return false;}
        if (debit==null || receipt==null || own.principal<=0 || own.fee<0 || "income".equals(debit.kind) || "expense".equals(receipt.kind)) return false;
        if (accountId(own.fromAccount)==accountId(own.toAccount)) return false;
        if (!receipt.currency.equals(debit.currency) || receipt.amountMinor!=own.principal) return false;
        long existingFee=transferFee(own.outId);
        if (existingFee>0 && existingFee!=own.fee) return false;
        return debit.amountMinor==own.principal+own.fee || debit.amountMinor==own.principal && existingFee==own.fee;
    }

    /** Uncategorized rows the user never touched get the category today's rules give their merchant (e.g. ChatGPT → اشتراكات). */
    private int recategorizeWithCurrentRules() {
        int changed=0;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id,merchant FROM transactions WHERE kind='expense' AND user_edited=0 AND fee_parent_id IS NULL " +
                "AND source NOT IN ('يدوي','CSV') AND category=?",new String[]{MoneyFlow.UNCATEGORIZED})) {
            while (c.moveToNext()) {
                String merchant=c.getString(1),known=ExpenseParser.normalizeMerchant(merchant);
                boolean brand=known.length()<=20 && !merchant.toUpperCase(Locale.ROOT).startsWith(known.toUpperCase(Locale.ROOT));
                String name=brand?known:merchant;
                MerchantRule rule=matchingRule(merchant);
                if(rule!=null && rule.ignored) continue;
                String category=rule==null?ExpenseParser.category(name):rule.category;
                if (MoneyFlow.UNCATEGORIZED.equals(category)) continue;
                ContentValues values=new ContentValues();values.put("category",category);
                changed+=getWritableDatabase().update("transactions",values,"id=?",new String[]{Long.toString(c.getLong(0))});
            }
        }
        return changed;
    }

    /** Re-checks stored source messages: a promotion that was booked by an older parser is archived with its evidence. */
    private int archivePromotions() {
        java.util.Map<Long,Boolean> verdict=new java.util.LinkedHashMap<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT e.transaction_id,e.encrypted_body FROM capture_evidence e " +
                "JOIN transactions t ON t.id=e.transaction_id WHERE t.user_edited=0 AND t.fee_parent_id IS NULL",null)) {
            while (c.moveToNext()) {
                long id=c.getLong(0);
                String body=c.isNull(1)?null:EvidenceVault.decrypt(c.getString(1));
                // Unknown or unreadable evidence never archives a row.
                boolean promotion=body!=null && ExpenseParser.isPromotion(body);
                verdict.put(id,verdict.getOrDefault(id,true) && promotion);
            }
        }
        int archived=0;
        for (java.util.Map.Entry<Long,Boolean> entry:verdict.entrySet())
            if (entry.getValue()) {archiveEntry(entry.getKey(),"promotional message, not a payment",true);archived++;}
        return archived;
    }

    // ---- Repeating transfers → installments ----

    public List<RepairRules.RepeatingTransfer> repeatingTransferSuggestions() {
        List<RepairRules.Row> rows=repairRows(Long.MIN_VALUE/2,Long.MAX_VALUE/2);
        java.util.Set<String> dismissed=new java.util.HashSet<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT pattern_key FROM dismissed_patterns",null)) {while (c.moveToNext()) dismissed.add(c.getString(0));}
        List<RepairRules.RepeatingTransfer> result=new ArrayList<>();
        for (RepairRules.RepeatingTransfer pattern:RepairRules.repeatingTransfers(rows,100000,ZoneId.systemDefault()))
            if (!dismissed.contains(patternKey(pattern))) result.add(pattern);
        return result;
    }

    static String patternKey(RepairRules.RepeatingTransfer pattern) {
        return "repeat:"+pattern.sourceId+":"+pattern.currency+":"+pattern.amountMinor;
    }

    /** Subscriptions/bills charged most months that have no saved commitment (e.g. ChatGPT). */
    public List<RepairRules.RepeatingMerchant> repeatingMerchantSuggestions() {
        List<RepairRules.Row> rows=repairRows(LocalDate.now().minusMonths(4).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                Long.MAX_VALUE/2);
        java.util.Set<String> dismissed=new java.util.HashSet<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT pattern_key FROM dismissed_patterns",null)) {while (c.moveToNext()) dismissed.add(c.getString(0));}
        List<Recurring> saved=recurring();
        List<RepairRules.RepeatingMerchant> result=new ArrayList<>();
        for (RepairRules.RepeatingMerchant m:RepairRules.repeatingMerchants(rows,LocalDate.now(),ZoneId.systemDefault())) {
            if (dismissed.contains(merchantKey(m))) continue;
            boolean known=false;
            for (Recurring item:saved) if (item.currency.equals(m.currency) && (item.merchant.equalsIgnoreCase(m.merchant) || item.matches(m.merchant))) known=true;
            if (!known) result.add(m);
        }
        return result;
    }

    static String merchantKey(RepairRules.RepeatingMerchant m) { return "merchant:"+m.merchant.toLowerCase(Locale.ROOT)+"|"+m.currency; }

    public void dismissRepeatingMerchant(RepairRules.RepeatingMerchant m) {
        ContentValues values=new ContentValues();values.put("pattern_key",merchantKey(m));
        getWritableDatabase().insertWithOnConflict("dismissed_patterns",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }

    /** Saves the merchant as a monthly subscription (or bill) on its usual day, after its latest charge. */
    public long confirmRepeatingMerchant(RepairRules.RepeatingMerchant m) {
        String kind="فواتير".equals(m.category)?"bill":"subscription";
        LocalDate last=Instant.ofEpochMilli(m.lastAt).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate next=last.plusMonths(1);next=next.withDayOfMonth(Math.min(m.typicalDay,next.lengthOfMonth()));
        while (!next.isAfter(last.plusDays(7))) {next=next.plusMonths(1);next=next.withDayOfMonth(Math.min(m.typicalDay,next.lengthOfMonth()));}
        long id=putCommitment(null,m.merchant,m.amountMinor,m.currency,next,"monthly",kind,null,0,1,m.merchant.toLowerCase(Locale.ROOT),null);
        if (id!=-1) {
            ContentValues values=new ContentValues();values.put("last_paid_date",last.toString());values.put("due_day",m.typicalDay);
            getWritableDatabase().update("recurring",values,"id=?",new String[]{Long.toString(id)});
            ContentValues category=new ContentValues();category.put("category",kindCategory(kind));
            getWritableDatabase().update("transactions",category,"merchant=? AND kind='expense' AND category=?",new String[]{m.merchant,MoneyFlow.UNCATEGORIZED});
        }
        dismissRepeatingMerchant(m);
        return id;
    }

    public void dismissRepeatingTransfer(RepairRules.RepeatingTransfer pattern) {
        ContentValues values=new ContentValues();values.put("pattern_key",patternKey(pattern));
        getWritableDatabase().insertWithOnConflict("dismissed_patterns",null,values,SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * The user confirms a repeating transfer is an installment: its past payments move to «أقساط» under the given name,
     * and a monthly commitment is created that ends on lastDue. Future payments from that account with that exact
     * amount are linked automatically (see linkTransferToCommitment).
     */
    public long confirmRepeatingTransfer(RepairRules.RepeatingTransfer pattern,String name,LocalDate lastDue,int paidSoFar,LocalDate today) {
        if (name==null || name.isBlank() || lastDue==null || paidSoFar<0) throw new IllegalArgumentException("Invalid installment");
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            ContentValues values=new ContentValues();values.put("category","أقساط");values.put("merchant",name.trim());
            values.put("user_edited",1);values.put("needs_review",0);
            for (long id:pattern.ids) sql.update("transactions",values,"id=? AND kind='expense'",new String[]{Long.toString(id)});
            LocalDate last=Instant.ofEpochMilli(pattern.lastAt).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate next=last.plusMonths(1);
            next=next.withDayOfMonth(Math.min(pattern.typicalDay,next.lengthOfMonth()));
            while (next.isBefore(today)) {next=next.plusMonths(1);next=next.withDayOfMonth(Math.min(pattern.typicalDay,next.lengthOfMonth()));}
            int remaining=RepairRules.monthlyPaymentsThrough(next,lastDue);
            // Generic placeholders for the same amount (e.g. "حساب رقم xxx") are replaced by the named commitment.
            for (Recurring item:recurring())
                if (item.needsReview && item.amountMinor==pattern.amountMinor && item.currency.equals(pattern.currency)) removeRecurring(item.id);
            long id=putCommitment(null,name.trim(),pattern.amountMinor,pattern.currency,next,"monthly","installment",
                    paidSoFar+remaining,paidSoFar,1,name.trim(),pattern.sourceId);
            if (id!=-1) {
                ContentValues due=new ContentValues();due.put("due_day",pattern.typicalDay);due.put("needs_review",0);
                if (!pattern.ids.isEmpty()) due.put("last_paid_date",last.toString());
                sql.update("recurring",due,"id=?",new String[]{Long.toString(id)});
            }
            dismissRepeatingTransfer(pattern);
            normalizeEarlyPayments();
            sql.setTransactionSuccessful();
            return id;
        } finally {sql.endTransaction();}
    }

    /** A new outgoing transfer from the account and for the exact amount of one active installment becomes that installment. */
    private boolean linkTransferToCommitment(long transactionId) {
        Transaction tx=transactionById(transactionId);
        if (tx==null || !"expense".equals(tx.kind) || !MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(tx.category) || tx.paymentSourceId==null) return false;
        long account=accountId(tx.paymentSourceId);
        Recurring match=null;int found=0;
        for (Recurring item:recurring()) {
            if (item.isDone() || item.needsReview || item.paymentSourceId==null || item.amountMinor!=tx.amountMinor ||
                    !item.currency.equals(tx.currency) || accountId(item.paymentSourceId)!=account) continue;
            match=item;found++;
        }
        if (found!=1) return false;
        ContentValues values=new ContentValues();values.put("category",kindCategory(match.kind));values.put("merchant",match.merchant);
        getWritableDatabase().update("transactions",values,"id=?",new String[]{Long.toString(transactionId)});
        markRecurringPaid(match.id,Instant.ofEpochMilli(tx.when).atZone(ZoneId.systemDefault()).toLocalDate());
        return true;
    }

    private static String kindCategory(String kind) {
        return "subscription".equals(kind)?"اشتراكات":"bill".equals(kind)?"فواتير":"أقساط";
    }

    public RepairReport lastRepair() {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT promotions,duplicates,own_transfers,moved_minor,fees_minor FROM repair_log ORDER BY id DESC LIMIT 1",null)) {
            if (!c.moveToFirst()) return null;
            RepairReport r=new RepairReport();r.promotions=c.getInt(0);r.duplicates=c.getInt(1);r.ownTransfers=c.getInt(2);
            r.movedMinor=c.getLong(3);r.feesMinor=c.getLong(4);return r;
        }
    }

    // ---- InstaPay transfers and own accounts ----

    private static String handleKey(String handle) {
        return handle == null ? "" : handle.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isOwnHandle(String handle) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM own_handles WHERE handle=?",
                new String[]{handleKey(handle)})) {
            return cursor.moveToFirst();
        }
    }

    public List<String> ownHandles() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT handle FROM own_handles ORDER BY handle", null)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    /** Marks a handle as one of the user's own accounts and turns its past rows into transfers. */
    public void addOwnHandle(String handle) {
        String key = handleKey(handle);
        if (key.length() < 3) return;
        ContentValues values = new ContentValues();
        values.put("handle", key);
        getWritableDatabase().insertWithOnConflict("own_handles", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM transactions WHERE LOWER(TRIM(merchant))=? AND kind IN ('expense','income')", new String[]{key})) {
            while (cursor.moveToNext()) markAsTransfer(cursor.getLong(0));
        }
    }

    public void removeOwnHandle(String handle) {
        getWritableDatabase().delete("own_handles", "handle=?", new String[]{handleKey(handle)});
    }

    /** Re-labels an expense or income as a move between the user's own accounts so it leaves spending totals. */
    public void markAsTransfer(long id) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT kind, payment_source_id FROM transactions WHERE id=?", new String[]{Long.toString(id)})) {
            if (!cursor.moveToFirst()) return;
            String kind = cursor.getString(0);
            if ("transfer".equals(kind)) return;
            ContentValues values = new ContentValues();
            values.put("kind", "transfer");
            values.put("category", "تحويلات");
            if ("income".equals(kind)) {
                // Money arriving: the account it landed in is the destination, not the source.
                if (cursor.isNull(1)) values.putNull("destination_source_id");
                else values.put("destination_source_id", cursor.getLong(1));
                values.putNull("payment_source_id");
            }
            getWritableDatabase().update("transactions", values, "id=?", new String[]{Long.toString(id)});
        }
    }

    /** Only combines already confirmed own-transfer legs with unique matching accounts and amounts.
     * Amount/time alone never establishes ownership. Unconfirmed expenses stay visible for review. */
    public long lastRepairSum;
    public int repairOwnTransfers(boolean apply) {
        int pairs=0; lastRepairSum=0;
        java.util.Set<Long> used=new java.util.HashSet<>();
        List<Transaction> rows=transactionsBetween(LocalDate.of(1970,1,1),LocalDate.now().plusDays(1));
        SQLiteDatabase writable=getWritableDatabase();
        if(apply) writable.beginTransaction();
        try {
            for(Transaction debit:rows) {
                if(!"transfer".equals(debit.kind) || debit.paymentSourceId==null || debit.destinationSourceId!=null || used.contains(debit.id)) continue;
                List<Transaction> candidates=new ArrayList<>();
                for(Transaction credit:rows) {
                    if(used.contains(credit.id) || !"transfer".equals(credit.kind) || credit.paymentSourceId!=null || credit.destinationSourceId==null ||
                            debit.paymentSourceId.equals(credit.destinationSourceId) || debit.amountMinor!=credit.amountMinor ||
                            !debit.currency.equals(credit.currency) || Math.abs(debit.when-credit.when)>120000) continue;
                    candidates.add(credit);
                }
                if(candidates.size()!=1) continue;
                Transaction credit=candidates.get(0);
                int reverse=0;
                for(Transaction other:rows) if("transfer".equals(other.kind) && other.paymentSourceId!=null && other.destinationSourceId==null &&
                        !other.paymentSourceId.equals(credit.destinationSourceId) && other.amountMinor==credit.amountMinor &&
                        other.currency.equals(credit.currency) && Math.abs(other.when-credit.when)<=120000) reverse++;
                if(reverse!=1) continue;
                if(apply&&!mergeEntries(debit.id,credit.id,"confirmed own transfer: debit and credit legs"))continue;
                used.add(debit.id);used.add(credit.id);pairs++;lastRepairSum+=debit.amountMinor;
            }
            if(apply) writable.setTransactionSuccessful();
            return pairs;
        } finally { if(apply) writable.endTransaction(); }
    }

    /** Aggregates only (no message text) that explain where a period's totals come from. */
    public String diagnostics(LocalDate start, LocalDate end) {
        long from = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long to = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String[] range = {Long.toString(from), Long.toString(to)};
        StringBuilder out = new StringBuilder("تشخيص Follow Money · " + start + " → " + end.minusDays(1) + "\n");
        out.append("\n== حسب النوع والتصنيف (EGP) ==\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind, category, COUNT(*), SUM(amount_minor) FROM transactions " +
                "WHERE currency='EGP' AND occurred_at>=? AND occurred_at<? GROUP BY kind, category ORDER BY SUM(amount_minor) DESC", range)) {
            while (c.moveToNext()) out.append(c.getString(0)).append(" · ").append(c.getString(1)).append(" · ")
                    .append(c.getInt(2)).append(" عملية · ").append(String.format(Locale.ROOT, "%,.2f", c.getLong(3) / 100.0)).append("\n");
        }
        out.append("\n== حسب المصدر والحساب ==\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT t.kind, t.source, COALESCE(p.name || ' ' || COALESCE(p.last4,''), 'بدون حساب'), " +
                "COUNT(*), SUM(t.amount_minor) FROM transactions t LEFT JOIN payment_sources p ON p.id=COALESCE(t.payment_source_id, t.destination_source_id) " +
                "WHERE t.currency='EGP' AND t.occurred_at>=? AND t.occurred_at<? GROUP BY 1,2,3 ORDER BY 5 DESC LIMIT 25", range)) {
            while (c.moveToNext()) out.append(c.getString(0)).append(" · ").append(c.getString(1)).append(" · ").append(c.getString(2))
                    .append(" · ").append(c.getInt(3)).append(" · ").append(String.format(Locale.ROOT, "%,.2f", c.getLong(4) / 100.0)).append("\n");
        }
        out.append("\n== حسب الشهر: دخل / خرج / بين حساباتك ==\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT strftime('%Y-%m', occurred_at/1000, 'unixepoch', 'localtime') m, " +
                "SUM(CASE WHEN kind='income' THEN amount_minor ELSE 0 END), SUM(CASE WHEN kind='expense' THEN amount_minor ELSE 0 END), " +
                "SUM(CASE WHEN kind='transfer' THEN amount_minor ELSE 0 END) FROM transactions WHERE currency='EGP' AND occurred_at>=? AND occurred_at<? " +
                "GROUP BY m ORDER BY m", range)) {
            while (c.moveToNext()) out.append(c.getString(0)).append(String.format(Locale.ROOT, " · %,.0f / %,.0f / %,.0f\n",
                    c.getLong(1) / 100.0, c.getLong(2) / 100.0, c.getLong(3) / 100.0));
        }
        out.append("\n== مكرر محتمل: نفس النوع والمبلغ في خلال دقيقتين ==\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT a.kind, a.source, b.source, COUNT(*), SUM(a.amount_minor) FROM transactions a " +
                "JOIN transactions b ON a.id>b.id AND a.amount_minor=b.amount_minor AND a.kind=b.kind AND a.currency=b.currency " +
                "AND ABS(a.occurred_at-b.occurred_at)<=120000 WHERE a.currency='EGP' AND a.occurred_at>=? AND a.occurred_at<? " +
                "GROUP BY 1,2,3 ORDER BY 5 DESC", range)) {
            boolean any = false;
            while (c.moveToNext()) {
                any = true;
                out.append(c.getString(0)).append(" · ").append(c.getString(1)).append(" + ").append(c.getString(2)).append(" · ")
                        .append(c.getInt(3)).append(" زوج · ").append(String.format(Locale.ROOT, "%,.2f", c.getLong(4) / 100.0)).append("\n");
            }
            if (!any) out.append("مفيش\n");
        }
        int pairs = repairOwnTransfers(false);
        out.append("\n== تحويلات بين حساباتك لسه متسجلة صرف ودخل ==\n").append(pairs).append(" زوج · ")
                .append(String.format(Locale.ROOT, "%,.2f", lastRepairSum / 100.0)).append(" (كل السنين)\n");
        out.append("\nعدد الحسابات المكتشفة: ").append(paymentSources().size()).append("\n");
        return out.toString();
    }

    /** InstaPay bill payments name the service without an amount; they can still close a matching commitment. */
    private boolean markServicePaid(String service, long when) {
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        boolean changed = false;
        List<Recurring> all=recurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview ||
                    !(item.matches(service) || item.merchant.equalsIgnoreCase(service))) continue;
            if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(item.nextDue, day)) > 12) continue;
            changed |= markRecurringPaid(item.id, day);
        }
        return changed;
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Transaction {
        public final long id, when, amountMinor;
        public final String currency, merchant, category, source, cardLast4, kind, note, timePrecision;
        public final Long paymentSourceId, destinationSourceId;
        public final boolean needsReview;

        Transaction(long id, long when, long amountMinor, String currency, String merchant,
                    String category, String source, Long paymentSourceId, String cardLast4,
                    String kind, Long destinationSourceId, String note, boolean needsReview) {
            this(id,when,amountMinor,currency,merchant,category,source,paymentSourceId,cardLast4,kind,destinationSourceId,note,needsReview,null);
        }

        Transaction(long id,long when,long amountMinor,String currency,String merchant,String category,String source,
                    Long paymentSourceId,String cardLast4,String kind,Long destinationSourceId,String note,boolean needsReview,String timePrecision) {
            this.id = id;
            this.when = when;
            this.amountMinor = amountMinor;
            this.currency = currency;
            this.merchant = merchant;
            this.category = category;
            this.source = source;
            this.paymentSourceId = paymentSourceId;
            this.cardLast4 = cardLast4;
            this.kind = kind;
            this.destinationSourceId = destinationSourceId;
            this.note = note;
            this.needsReview = needsReview;
            this.timePrecision = timePrecision;
        }
    }

    public static final class BalanceSnapshot {
        public final long amountMinor, occurredAt, receivedAt;
        public final String source;
        public final boolean estimated;
        public final long anchorSourceId,updatedAt;
        public final int movementCount;
        BalanceSnapshot(long amountMinor, long occurredAt, long receivedAt, String source) {
            this(amountMinor, occurredAt, receivedAt, source, false);
        }
        BalanceSnapshot(long amountMinor, long occurredAt, long receivedAt, String source, boolean estimated) {
            this(amountMinor,occurredAt,receivedAt,source,estimated,-1,occurredAt,0);
        }
        BalanceSnapshot(long amountMinor,long occurredAt,long receivedAt,String source,boolean estimated,
                long anchorSourceId,long updatedAt,int movementCount) {
            this.amountMinor = amountMinor;
            this.occurredAt = occurredAt;
            this.receivedAt = receivedAt;
            this.source = source;
            this.estimated = estimated;
            this.anchorSourceId=anchorSourceId;this.updatedAt=updatedAt;this.movementCount=movementCount;
        }
    }

    public static final class MerchantRule {
        public final long id;
        public final String matchText, category;
        public final boolean ignored;
        MerchantRule(long id, String matchText, String category, boolean ignored) {
            this.id = id;
            this.matchText = matchText;
            this.category = category;
            this.ignored = ignored;
        }
    }

    public static final class Asset {
        public final long id, valueMinor, updatedAt;
        public final String name, kind, currency;
        Asset(long id, String name, String kind, long valueMinor, String currency, long updatedAt) {
            this.id = id; this.name = name; this.kind = kind; this.valueMinor = valueMinor;
            this.currency = currency; this.updatedAt = updatedAt;
        }
    }

    public static final class Debt {
        public final long id, originalMinor, remainingMinor;
        public final String name, direction, currency, dueDate;
        Debt(long id, String name, String direction, long originalMinor, long remainingMinor,
             String currency, String dueDate) {
            this.id = id; this.name = name; this.direction = direction; this.originalMinor = originalMinor;
            this.remainingMinor = remainingMinor; this.currency = currency; this.dueDate = dueDate;
        }
    }

    public static final class PaymentSource {
        public final long id;
        public final String name, kind, last4;
        public final Long parentSourceId;
        public final String displayName;
        public final boolean archived,reviewed;
        public final long lastActivityAt;
        PaymentSource(long id, String name, String kind, String last4) {
            this(id,name,kind,last4,null,null,false,false,0);
        }
        PaymentSource(long id,String name,String kind,String last4,Long parentSourceId,String displayName,
                boolean archived,boolean reviewed,long lastActivityAt) {
            this.id = id; this.name = name; this.kind = kind; this.last4 = last4;
            this.parentSourceId=parentSourceId;this.displayName=displayName;this.archived=archived;
            this.reviewed=reviewed;this.lastActivityAt=lastActivityAt;
        }
    }

    public static final class Budget {
        public final long id, amountMinor;
        public final String category, currency;
        Budget(long id, String category, String currency, long amountMinor) {
            this.id = id; this.category = category; this.currency = currency; this.amountMinor = amountMinor;
        }
    }

    public static final class BudgetPlan {
        public final long id, amountMinor;
        public final String name, currency, cycle;
        public final LocalDate anchor;
        public final int durationDays;
        public final List<String> categories;
        BudgetPlan(long id, String name, long amountMinor, String currency, String cycle,
                   LocalDate anchor, int durationDays, List<String> categories) {
            this.id = id; this.name = name; this.amountMinor = amountMinor; this.currency = currency;
            this.cycle = cycle; this.anchor = anchor; this.durationDays = durationDays; this.categories = categories;
        }
    }

    public static final class Recurring {
        public final long id, amountMinor;
        public final String merchant, currency, lastAlertDate, cycle, kind, matchText, lastPaidDate, status;
        public final LocalDate nextDue;
        public final Integer totalCount;
        public final int paidCount, remindDays, dueDay;
        public final boolean needsReview;
        public final Long paymentSourceId;

        Recurring(long id, String merchant, long amountMinor, String currency,
                  LocalDate nextDue, String lastAlertDate, String cycle, String kind,
                  Integer totalCount, int paidCount, int remindDays, String matchText,
                  Long paymentSourceId, String lastPaidDate, String status, int dueDay, boolean needsReview) {
            this.id = id;
            this.needsReview = needsReview;
            this.merchant = merchant;
            this.amountMinor = amountMinor;
            this.currency = currency;
            this.nextDue = nextDue;
            this.lastAlertDate = lastAlertDate;
            this.cycle = cycle;
            this.kind = "loan".equals(kind) ? "installment" : "commitment".equals(kind) ? "bill" : kind;
            this.totalCount = totalCount;
            this.paidCount = paidCount;
            this.remindDays = remindDays;
            this.matchText = matchText == null ? merchant.toLowerCase(Locale.ROOT) : matchText;
            this.paymentSourceId = paymentSourceId;
            this.lastPaidDate = lastPaidDate;
            this.status = status == null ? "active" : status;
            this.dueDay = dueDay > 0 ? dueDay : nextDue.getDayOfMonth();
        }

        public boolean isDone() { return "done".equals(status); }

        public LocalDate nextAfter(LocalDate date) {
            if ("weekly".equals(cycle)) return date.plusWeeks(1);
            if ("yearly".equals(cycle)) return date.plusYears(1);
            LocalDate next = date.plusMonths(1);
            // Return to the chosen day after short months (31 → Feb 28 → Mar 31).
            return next.withDayOfMonth(Math.min(dueDay, next.lengthOfMonth()));
        }

        /** match_text holds keywords separated by | such as "valu|فاليو". */
        public boolean matches(String text) {
            if (text == null) return false;
            String haystack = text.toLowerCase(Locale.ROOT);
            for (String keyword : matchText.split("\\|")) {
                String clean = keyword.trim();
                if (clean.length() >= 3 && haystack.contains(clean)) return true;
            }
            return false;
        }
    }

    public static final class CategoryTotal {
        public final String category, currency;
        public final long amountMinor;

        CategoryTotal(String category, String currency, long amountMinor) {
            this.category = category;
            this.currency = currency;
            this.amountMinor = amountMinor;
        }
    }
}
