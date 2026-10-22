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
    private final CaptureTransaction capture = new CaptureTransaction(this);
    private final TransactionRepository transactionRows = new TransactionRepository(this);
    private final EvidenceRepository captureEvidence = new EvidenceRepository(this);
    private final AccountRepository accounts = new AccountRepository(this);
    private final CommitmentRepository commitments = new CommitmentRepository(this);
    private final CommitmentMatcher commitmentMatcher = new CommitmentMatcher(this);
    private final CommitmentPayments commitmentPayments = new CommitmentPayments(this);
    private final ReimbursementRepository reimbursements = new ReimbursementRepository(this);
    private final ReimbursementUseCase reimbursementFlow = new ReimbursementUseCase(this);
    static final String DATABASE_NAME = "masareef.db";
    /** The schema a backup file records; a backup from a newer schema is refused on restore. */
    static final int SCHEMA_VERSION = 26;

    public ExpenseDb(Context context) {
        super(context, DATABASE_NAME, null, SCHEMA_VERSION);
    }

    /**
     * Backup: writes a consistent copy of the whole database to a temporary file (VACUUM INTO where SQLite supports it,
     * otherwise a checkpoint and a file copy under a write lock). The copy still holds Keystore ciphertext; the caller
     * decrypts it inside the copy only.
     */
    void exportSnapshot(java.io.File target) throws java.io.IOException {
        SQLiteDatabase sql = getWritableDatabase();
        if (target.exists() && !target.delete()) throw new java.io.IOException("Could not replace " + target.getName());
        try {
            sql.execSQL("VACUUM INTO ?", new Object[]{target.getAbsolutePath()});
            return;
        } catch (android.database.SQLException unsupported) {
            if (target.exists()) target.delete();
        }
        try (Cursor c = sql.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) { c.moveToFirst(); }
        sql.beginTransaction();
        try {
            java.nio.file.Files.copy(new java.io.File(sql.getPath()).toPath(), target.toPath());
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }

    /** The restored file waiting beside the live one, and the live one kept until the restore has completed. */
    static final String INCOMING_SUFFIX = ".new", KEPT_SUFFIX = ".restore-bak";
    private static final String[] JOURNAL_SUFFIXES = {"-wal", "-shm", "-journal"};

    /**
     * Restore, step one: swaps the database file while no helper is open (AppResources.closeDatabase() first). The
     * restored copy is first brought into the database folder as masareef.db.new, the live file is then renamed to
     * masareef.db.restore-bak (its journal files removed so neither file opens with a stale journal), and finally .new
     * takes the live name. Nothing is deleted: a failure at any step leaves the old data in place or, after the swap,
     * recoverable through rollbackRestore(); the next ExpenseDb runs onUpgrade for an older schema.
     */
    static void replaceDatabase(Context context, java.io.File snapshot) throws java.io.IOException {
        java.io.File live = context.getDatabasePath(DATABASE_NAME);
        java.io.File parent = live.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Database folder unavailable");
        java.io.File incoming = new java.io.File(live.getPath() + INCOMING_SUFFIX), kept = new java.io.File(live.getPath() + KEPT_SUFFIX);
        java.nio.file.Files.move(snapshot.toPath(), incoming.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            removeJournals(live);
            if (live.exists()) java.nio.file.Files.move(live.toPath(), kept.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            else java.nio.file.Files.write(kept.toPath(), new byte[0]); // no data before: a rollback brings back an empty database, which SQLite opens as new
            try {
                java.nio.file.Files.move(incoming.toPath(), live.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException failed) {
                if (kept.exists()) java.nio.file.Files.move(kept.toPath(), live.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                throw failed;
            }
        } finally { incoming.delete(); /* only still there when a step above failed */ }
    }

    /** Restore, last step: the old database is no longer needed. */
    static void finishRestore(Context context) {
        new java.io.File(context.getDatabasePath(DATABASE_NAME).getPath() + KEPT_SUFFIX).delete();
    }

    /**
     * Restore failed after the swap (the restored file would not open, its text could not be re-encrypted, the settings
     * were unreadable): puts the old database back exactly as it was. Called with no helper open.
     */
    static void rollbackRestore(Context context) throws java.io.IOException {
        java.io.File live = context.getDatabasePath(DATABASE_NAME), kept = new java.io.File(live.getPath() + KEPT_SUFFIX);
        if (!kept.exists()) throw new java.io.IOException("No previous database to restore");
        removeJournals(live);
        java.nio.file.Files.move(kept.toPath(), live.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * App start: a masareef.db.restore-bak that still exists means a restore was interrupted after the swap and before
     * it completed (the process died, or the rollback itself failed), so the live file may hold restored text that was
     * never re-encrypted. The old database comes back and the half-restored one is discarded; a stray .new is removed.
     * Returns true when something was repaired.
     */
    static boolean recoverInterruptedRestore(Context context) {
        java.io.File live = context.getDatabasePath(DATABASE_NAME);
        java.io.File kept = new java.io.File(live.getPath() + KEPT_SUFFIX), incoming = new java.io.File(live.getPath() + INCOMING_SUFFIX);
        boolean repaired = false;
        if (kept.exists()) {
            try { rollbackRestore(context); repaired = true; }
            catch (java.io.IOException failed) { android.util.Log.e("FollowMoney", "Could not recover the previous database: " + failed.getClass().getSimpleName()); }
        }
        if (incoming.exists() && incoming.delete()) repaired = true;
        return repaired;
    }

    private static void removeJournals(java.io.File database) throws java.io.IOException {
        for (String suffix : JOURNAL_SUFFIXES) {
            java.io.File file = new java.io.File(database.getPath() + suffix);
            if (file.exists() && !file.delete()) throw new java.io.IOException("Could not remove " + file.getName());
        }
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
        CardSettlements.create(db);CaptureEvents.create(db);createImportReceipts(db);
        CommitmentPayments.create(db);LedgerRevision.create(db);
        ShadowStore.create(db);
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
        if(oldVersion<22){CardSettlements.create(db);CaptureEvents.create(db);}
        if(oldVersion<23){CaptureEvents.ensureTimeline(db);createImportReceipts(db);}
        if(oldVersion<25)CommitmentPayments.create(db);
        if(oldVersion<24){
            // Retain the existing own_handles recovery guard before installing its revision triggers.
            db.execSQL("CREATE TABLE IF NOT EXISTS own_handles (handle TEXT PRIMARY KEY)");
            LedgerRevision.create(db);
        }
        if(oldVersion==24)LedgerRevision.create(db);
        if(oldVersion<26)ShadowStore.create(db);
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

    private static void createImportReceipts(SQLiteDatabase sql){sql.execSQL("CREATE TABLE IF NOT EXISTS sms_import_receipts(unique_key TEXT PRIMARY KEY)");}
    /** Historical cancellation is evidence for explicit review; it must not archive a debit unattended. */
    boolean addHistoricalSms(SmsPreview row){
        CaptureResult.Write write=addHistoricalSmsObserved(row);
        return write!=null&&write.added;
    }

    /**
     * The same historical write as {@link #addHistoricalSms}, with the capture observation kept for the import
     * contract that has to report it. Returns null when the row short-circuits before capture because its
     * identity was already known; {@link #smsCaptureStatus} names the status and {@link #historicalKnown} the table.
     */
    CaptureResult.Write addHistoricalSmsObserved(SmsPreview row){
        lastCaptureEvent=null;lastCapturedId=-1;
        // Known identities (capture evidence, import receipts, exclusions) are skipped before anything is decided or written.
        if(smsCaptureStatus(row.sender,row.body,row.receivedAt,row.captureAt,row.receiptKeySafe)!=0)return null;
        // The new pipeline's reading, as the row was listed; null when it failed and the legacy parser writes the row.
        PipelineDecider.Result decided=PipelineDecider.forImport(this,row);
        PipelineDecision decision=PipelineDecider.importDecision(this,row,decided);
        // automaticCancellation=false: a historical cancellation waits for review and never archives a debit unattended.
        CaptureResult.Write write=addAutomaticCapture(row.sender,row.sender,row.body,row.captureAt,"SMS",row.receiptKeySafe?row.receivedAt:row.captureAt,false,decision);
        rememberCapture(write.inserted?write.rowId:-1,write.event);
        ContentValues receipt=new ContentValues();receipt.put("unique_key",sha256(row.sender+"\n"+row.captureAt+"\n"+row.body));getWritableDatabase().insertWithOnConflict("sms_import_receipts",null,receipt,SQLiteDatabase.CONFLICT_IGNORE);
        PipelineDecider.storeImport(this,row,decided,write);
        return write;
    }

    /** Which stored table already knew this row's identity, read with the same keys and order as smsCaptureStatus. */
    ImportResult.Known historicalKnown(SmsPreview row) {
        String key=sha256(row.sender+"\n"+CaptureIdentity.smsTimestamp(row.receivedAt,row.captureAt)+"\n"+row.body);
        String legacyKey=row.receiptKeySafe?sha256(row.sender+"\n"+row.receivedAt+"\n"+row.body):key;
        String[] keys=new String[]{key,legacyKey};
        if(storedIdentity("SELECT 1 FROM capture_evidence WHERE unique_key IN (?,?) AND source='SMS' LIMIT 1",keys))return ImportResult.Known.EVIDENCE;
        if(storedIdentity("SELECT 1 FROM excluded_messages WHERE unique_key IN (?,?) LIMIT 1",keys))return ImportResult.Known.EXCLUDED;
        if(storedIdentity("SELECT 1 FROM sms_import_receipts WHERE unique_key IN (?,?) LIMIT 1",keys))return ImportResult.Known.RECEIPT;
        if(storedIdentity("SELECT 1 FROM capture_events WHERE event_key IN (?,?) AND source='SMS' LIMIT 1",keys))return ImportResult.Known.EVENT;
        return null;
    }

    private boolean storedIdentity(String sql,String[] keys) {
        try(Cursor c=getReadableDatabase().rawQuery(sql,keys)){return c.moveToFirst();}
    }
    public boolean addSms(String sender, String body, long when) {
        return captureSms(sender, body, when).accepted;
    }

    CaptureResult captureSms(String sender, String body, long when) {
        return capture.execute(CaptureRequest.sms(sender, body, when));
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
        return capture.apply(CaptureRequest.inboxSms(sender, body, receivedAt, sentAt, receiptKeySafe));
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
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM sms_import_receipts WHERE unique_key IN (?,?) UNION ALL SELECT 1 FROM capture_events WHERE event_key IN (?,?) AND source='SMS' LIMIT 1",new String[]{key,legacyKey,key,legacyKey})){if(c.moveToFirst())return 1;}
        return 0;
    }

    public boolean addNotification(String packageName, String appLabel, String body, long when) {
        return captureNotification(packageName, appLabel, body, when).accepted;
    }

    CaptureResult captureNotification(String packageName, String appLabel, String body, long when) {
        return capture.execute(CaptureRequest.notification(packageName, appLabel, body, when));
    }

    /** Same legacy capture; shadow carries the separate extras for the shadow pipeline only. */
    CaptureResult captureNotification(String packageName, String appLabel, String body, long when, MessageNormalizer.Raw shadow) {
        return capture.execute(CaptureRequest.notification(packageName, appLabel, body, when, shadow));
    }

    /** Storage seam for CaptureTransaction; the connection and transaction policy stay here. */
    CaptureResult.Write addAutomaticCapture(CaptureRequest request) {
        return addAutomaticCapture(request, null);
    }

    /**
     * decided: the new pipeline's decision, or null when the pipeline failed and the legacy parser decides. With a
     * decision, the ledger row and its fields come from it; the legacy
     * non-ledger paths (excluded messages, notices, hints, balances, commitments) still run when it records nothing.
     */
    CaptureResult.Write addAutomaticCapture(CaptureRequest request, PipelineDecision decided) {
        return addAutomaticCapture(request.sourceKey, request.label, request.body, request.captureAt,
                request.channel, request.legacyReceivedAt, true, decided);
    }

    private long lastCapturedId = -1;
    private CaptureEvents.Event lastCaptureEvent;
    CaptureEvents.Event lastCaptureEvent(){return lastCaptureEvent;}
    void rememberCapture(long id,CaptureEvents.Event event){lastCapturedId=id;lastCaptureEvent=event;}

    /** The row the latest automatic capture created, followed through any merge the repair made right after it. */
    public Transaction lastCaptured() { return lastCapturedId < 0 ? null : transactionById(lastCapturedId); }

    private CaptureResult.Write addAutomaticCapture(String sourceKey,String label,String body,long receivedAt,String channel,long legacyReceivedAt,boolean automaticCancellation){
        return addAutomaticCapture(sourceKey,label,body,receivedAt,channel,legacyReceivedAt,automaticCancellation,null);
    }

    private CaptureResult.Write addAutomaticCapture(String sourceKey,String label,String body,long receivedAt,String channel,long legacyReceivedAt,boolean automaticCancellation,PipelineDecision decided){
        CaptureResult.Write result=new CaptureResult.Write();
        // With a new-pipeline decision that records, its reading wins over the legacy skip/notice checks below.
        boolean newRecords = decided != null && decided.records;
        if (body == null) {result.reason=CaptureResult.Reason.MISSING_BODY;return result;}
        if (!newRecords && ExpenseParser.isVerificationMessage(body)) {result.reason=CaptureResult.Reason.VERIFICATION;return result;}
        SQLiteDatabase writable = getWritableDatabase();
        writable.beginTransaction();
        try {
            String key = sha256(sourceKey + "\n" + receivedAt + "\n" + body);
            String legacyKey = sha256(sourceKey + "\n" + legacyReceivedAt + "\n" + body);
            try(Cursor excluded=writable.rawQuery("SELECT 1 FROM excluded_messages WHERE unique_key IN (?,?)",new String[]{key,legacyKey})) {
                if(excluded.moveToFirst()) {result.reason=CaptureResult.Reason.EXCLUDED;writable.setTransactionSuccessful();return result;}
            }
            String hash = sha256(sourceKey + "\n" + body.trim());
            ExpenseParser.EventTime event=ExpenseParser.eventTimeWithPrecision(body,receivedAt);
            long eventAt = event.when;
            // Legacy guards stand even when the new pipeline records: a future-only alert, an unsuccessful attempt or a
            // notice (cancellation, reversal) never becomes a ledger row; CaptureTransaction notes LEGACY_GUARD on it.
            if (ExpenseParser.isFutureOnly(body)) {
                result.reason=CaptureResult.Reason.FUTURE_ONLY;
                result.rowId=markRejectedCaptureForReview(hash, eventAt);
                writable.setTransactionSuccessful();
                return result;
            }
            if (ExpenseParser.isUnsuccessful(body) || CaptureNotice.parse(label,body)!=null) {
                result.rowId=markRejectedCaptureForReview(hash, eventAt);
                CaptureNotice notice=CaptureNotice.parse(label,body);
                if(notice!=null)result.event=new CaptureEvents(this).record(label,channel,body,receivedAt,key,notice,automaticCancellation);
                result.added=result.event!=null;
                result.reason=notice==null?CaptureResult.Reason.UNSUCCESSFUL:
                        result.event==null?CaptureResult.Reason.NOTICE_DUPLICATE:CaptureResult.Reason.NOTICE_RECORDED;
                if(result.event!=null)result.evidence=CaptureResult.Evidence.EVENT_BODY;
                writable.setTransactionSuccessful();
                return result;
            }
            recordHint(label,body,receivedAt);
            recordBalanceFromMessage(label, body, receivedAt);
            ExpenseParser.Transfer transfer = null;
            ExpenseParser.Parsed parsed;
            String direction = "expense";
            if (decided != null) {
                // The new pipeline decides whether there is a ledger row and what it says.
                parsed = decided.records ? new ExpenseParser.Parsed(decided.amountMinor, decided.currency, decided.merchant, decided.category, decided.cardLast4) : null;
                if (decided.records) direction = decided.kind();
            } else {
                // The pipeline failed on this message: the legacy parser decides it.
                transfer = ExpenseParser.parseTransfer(body);
                parsed = ExpenseParser.parse(label, body);
                if (parsed == null) { parsed = ExpenseParser.parseIncome(label, body); direction = "income"; }
            }
            if (transfer != null) {
                direction = transfer.incoming ? "income" : "expense";
                parsed = new ExpenseParser.Parsed(transfer.amountMinor, "EGP", transfer.counterpart,
                        transfer.incoming ? "دخل" : "تحويلات", parsed == null ? null : parsed.cardLast4);
            }
            if (parsed == null) {
                result.rowId=markRejectedCaptureForReview(hash, eventAt);
                String service = ExpenseParser.paidService(body);
                if(service!=null){
                    result.reason=commitmentMatcher.recordService(service,eventAt,receivedAt,key,body,automaticCancellation);
                    result.added=result.reason==CaptureResult.Reason.SERVICE_MATCH_ACCEPTED||result.reason==CaptureResult.Reason.COMMITMENT_REVIEW_REQUIRED;
                    if(result.added)result.evidence=CaptureResult.Evidence.PROVIDER_BODY;
                }
                // The user's ignore rule refused the row (as insertEntry refuses it for legacy); otherwise the pipeline skipped it.
                if (decided != null && result.reason == CaptureResult.Reason.NO_LEDGER_CANDIDATE)
                    result.reason=decided.ignoredByUser?CaptureResult.Reason.INSERT_NOT_APPLIED:CaptureResult.Reason.NEW_PIPELINE_SKIPPED;
                writable.setTransactionSuccessful(); return result;
            }
            // A repayment of the user's own credit card: the debit account is the source, the card the destination, whoever decided.
            if(CaptureNotice.cardRepayment(body))parsed=new ExpenseParser.Parsed(parsed.amountMinor,parsed.currency,parsed.merchant,parsed.category,ExpenseParser.debitAccountLast4(body));
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
            // Nothing recorded earlier is changed when the new pipeline decides: a match only gains this evidence.
            if (existing >= 0) { if (decided == null) syncAutomatic(existing, parsed, direction, account, body, eventAt,event.precision); }
            else {
                // Step 7: a debit whose other party is only an account/card tail that belongs to exactly one of the user's
                // own sources went between the user's accounts (owner test, 22 Oct 2026: HSBC «to 7392» = Banque Misr 7392).
                Long ownDestination = decided != null && "expense".equals(direction) ? ownSourceByTail(PipelineDecision.counterpartyTail(decided.counterparty), account) : null;
                boolean ownHandle = transfer != null ? isOwnHandle(transfer.counterpart) :
                        decided != null && decided.counterparty != null && decided.counterparty.contains("@") && isOwnHandle(decided.counterparty);
                String kind = ownHandle || ownDestination != null ? "transfer" : direction;
                if (ownDestination != null) parsed = new ExpenseParser.Parsed(parsed.amountMinor, parsed.currency, "تحويل بين حساباتي", parsed.category, parsed.cardLast4);
                // A debit to one of the user's own credit cards keeps that card as destination (the statement treats it as
                // internal); the settlement dialog stays optional for allocating it to purchases.
                Long destination = ownDestination != null ? ownDestination : "transfer".equals(kind) && "income".equals(direction) ? account :
                        "expense".equals(kind) && CaptureNotice.cardRepayment(body) ? ownCreditCard(ExpenseParser.cardLast4(body)) : null;
                added = insertEntry(key, eventAt, parsed.amountMinor, parsed.currency, parsed.merchant,
                        "transfer".equals(kind) ? "تحويلات" : parsed.category, channel, hash, parsed.cardLast4,
                        "transfer".equals(kind) && "income".equals(direction) ? null : account, kind,
                        destination, null, receivedAt);
                if (added) {
                    existing = latestInserted().id;
                    ContentValues precision=new ContentValues();precision.put("time_precision",event.precision);
                    // A new-pipeline REVIEW (or a return with no expense to link) waits for the user; its reason is on the observation.
                    if (decided != null && decided.review) precision.put("needs_review",1);
                    writable.update("transactions",precision,"id=?",new String[]{Long.toString(existing)});
                    // A return of an earlier charge is an expense offset: an income linked to that expense.
                    if (decided != null && decided.offsetOfTransactionId != null && "income".equals(kind)) linkReimbursement(existing, decided.offsetOfTransactionId);
                }
            }
            // Task D1: the pipeline read a balance and the row has its source (a Telda notification names no digits, so
            // the legacy balance reading never finds its source): the source's last balance at the message's time.
            if (existing >= 0 && account != null && decided != null && decided.balanceMinor != null)
                putBalance(account, decided.balanceCurrency != null ? decided.balanceCurrency : decided.currency, decided.balanceMinor,
                        eventAt, receivedAt, channel, sha256("balance:" + label + "\n" + receivedAt + "\n" + body));
            if (existing >= 0) {
                result.evidence=captureEvidence.record(key,existing,hash,normalized,reference,channel,label,receivedAt,direction,body);
                if (automaticCancellation && added && !CaptureNotice.cardRepayment(body) && "expense".equals(direction))
                    commitmentPayments.automatic(existing);
            }
            if(automaticCancellation&&existing>=0){CaptureEvents.Event resolved=new CaptureEvents(this).reconcilePending(existing);if(resolved!=null)result.event=resolved;}
            result.added=added;result.inserted=added;result.rowId=existing;
            result.reason=added?CaptureResult.Reason.INSERTED:existing>=0?CaptureResult.Reason.EXISTING_MATCH:CaptureResult.Reason.INSERT_NOT_APPLIED;
            writable.setTransactionSuccessful(); return result;
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
        if(new CardSettlements(this).related(id)) return;
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

    /** A row's live dependents share its life: bank-fee children and reimbursement links must go with it. */
    private void deleteDependentRows(long id) {
        transactionRows.deleteDependentRows(id);
    }

    void archiveEntry(long id,String reason,boolean exclude) {
        SQLiteDatabase writable=getWritableDatabase();
        boolean own=!writable.inTransaction();
        if(own) writable.beginTransaction();
        try {
            // Bank-fee children share the parent's life. They are archived with it, never hard-deleted,
            // so the archive keeps the whole story in the same transaction.
            java.util.List<Long> fees=new java.util.ArrayList<>();
            try(Cursor c=writable.rawQuery("SELECT id FROM transactions WHERE fee_parent_id=? ORDER BY id",
                    new String[]{Long.toString(id)})) {
                while(c.moveToNext()) fees.add(c.getLong(0));
            }
            for(long fee:fees) archiveRow(writable,fee,reason,exclude);
            archiveRow(writable,id,reason,exclude);
            if(own) writable.setTransactionSuccessful();
        } finally {
            if(own) writable.endTransaction();
        }
    }

    private void archiveRow(SQLiteDatabase writable,long id,String reason,boolean exclude) {
        try (Cursor c=writable.rawQuery("SELECT * FROM transactions WHERE id=?",new String[]{Long.toString(id)})) {
            if (!c.moveToFirst()) return;
            org.json.JSONObject payload=new org.json.JSONObject();
            try {
                for (int i=0;i<c.getColumnCount();i++) payload.put(c.getColumnName(i),c.isNull(i)?org.json.JSONObject.NULL:c.getString(i));
                // The row's live reimbursement links disappear with it; keep them in the payload for review.
                payload.put("removed_reimbursements",removedReimbursements(writable,id));
                payload.put("card_settlement",new CardSettlements(this).history(id));
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
            deleteDependentRows(id);
            writable.delete("transactions","id=?",new String[]{Long.toString(id)});
        }
    }

    /** The reimbursement links that vanish with the row, captured before deleteDependentRows removes them. */
    private static org.json.JSONArray removedReimbursements(SQLiteDatabase writable,long id) {
        org.json.JSONArray rows=new org.json.JSONArray();
        try(Cursor c=writable.rawQuery("SELECT income_id,expense_id,amount_minor FROM reimbursements WHERE income_id=? OR expense_id=? ORDER BY income_id",
                new String[]{Long.toString(id),Long.toString(id)})) {
            while(c.moveToNext()) {
                org.json.JSONObject row=new org.json.JSONObject();
                try {
                    row.put("income_id",c.getLong(0)); row.put("expense_id",c.getLong(1)); row.put("amount_minor",c.getLong(2));
                } catch(org.json.JSONException error) { throw new IllegalStateException(error); }
                rows.put(row);
            }
        }
        return rows;
    }

    private boolean mergeEntries(long keep,long remove,String reason) {
        if (keep==remove) return true;
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {
            Transaction kept=transactionById(keep), removed=transactionById(remove);
            if (kept==null || removed==null) {sql.setTransactionSuccessful();return false;}
            if(new CardSettlements(this).related(keep)||new CardSettlements(this).related(remove)){markMergeReview(keep,remove);sql.setTransactionSuccessful();return false;}
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

    private boolean hasReimbursement(long id) { return reimbursements.hasReimbursement(id); }

    private long[] reimbursementLink(long income) { return reimbursements.reimbursementLink(income); }

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
                    if(part.body==null||whole.body==null)continue;
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
    private long markRejectedCaptureForReview(String hash,long at) {
        long id=rowForHash(hash,at);
        if (id<0) return id;
        ContentValues values=new ContentValues();values.put("needs_review",1);
        getWritableDatabase().update("transactions",values,"id=? AND user_edited=0",new String[]{Long.toString(id)});
        return id;
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
    /** Task D2: the key a «لقينا خصم متكرر» card is dismissed under: recurring:<merchant>|<amount_minor>|<currency>. */
    static String recurringKey(String merchant, long amountMinor, String currency) {
        return "recurring:" + (merchant == null ? "" : merchant.trim().toLowerCase(Locale.ROOT)) + "|" + amountMinor + "|" + currency;
    }

    /**
     * «مش التزام · تجاهل» on a recurring card: kept as a dismissed suggestion under its recurring key (the card comes from
     * ledger rows, not from a stored suggestion, so the row is written here), never offered again.
     */
    public void dismissRecurringSuggestion(Transaction item) {
        String key = recurringKey(item.merchant, item.amountMinor, item.currency);
        ContentValues row = new ContentValues();
        row.put("hint_key", key); row.put("merchant", item.merchant); row.put("kind", "subscription");
        row.put("amount_minor", item.amountMinor); row.put("currency", item.currency); row.put("cycle", "monthly");
        row.put("estimated_date", LocalDate.now().toString()); row.put("explanation", "مش التزام");
        row.put("received_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("commitment_suggestions", null, row, SQLiteDatabase.CONFLICT_IGNORE);
        dismissSuggestion(key);
    }

    /** Recurring keys the user dismissed («مش التزام · تجاهل»). */
    public java.util.Set<String> dismissedRecurringKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT hint_key FROM commitment_suggestions WHERE status='dismissed' AND hint_key LIKE 'recurring:%'", null)) {
            while (c.moveToNext()) keys.add(c.getString(0));
        }
        return keys;
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
    public List<SourceEvidence> commitmentHistory(String key) { return captureEvidence.commitmentHistory(key); }

    public List<SourceEvidence> commitmentHistoryFor(long recurringId) { return captureEvidence.commitmentHistoryFor(recurringId); }

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

    public List<SourceEvidence> sourceEvidence(long id) { return captureEvidence.sourceEvidence(id); }

    public static final class SourceEvidence {
        public final String source,sender,body; public final long receivedAt; public final boolean failed;
        SourceEvidence(String source,String sender,long receivedAt,String body) {
            this(source,sender,receivedAt,body,false);
        }
        SourceEvidence(String source,String sender,long receivedAt,String body,boolean failed) {
            this.source=source;this.sender=sender;this.receivedAt=receivedAt;this.body=body;this.failed=failed;
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
        SQLiteDatabase sql=getWritableDatabase();boolean own=!sql.inTransaction();if(own)sql.beginTransaction();
        try {
            boolean inserted = insertEntry("manual:" + java.util.UUID.randomUUID(), when, amountMinor, currency,
                    merchant.trim(), category, "يدوي", null, null, sourceId, kind, destinationId, note);
            if(inserted && "expense".equals(kind))commitmentPayments.automatic(latestInserted().id);
            if(own)sql.setTransactionSuccessful();return inserted;
        }finally{if(own)sql.endTransaction();}
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

    /** What one CSV import did: new rows and rows that were already there. */
    public static final class CsvBatchResult {
        public final int saved, duplicates;
        CsvBatchResult(int saved, int duplicates) { this.saved=saved; this.duplicates=duplicates; }
    }

    /**
     * One CSV file, one transaction: any failing row throws and rolls back every row of the file.
     * A row that was not inserted must already exist or be excluded; anything else is a failure, not a duplicate.
     */
    public CsvBatchResult importCsvBatch(String fileHash, List<CsvImport.Row> rows) {
        CsvImportResult detailed=importCsvBatchDetailed(fileHash,rows);
        // The legacy number keeps its documented meaning: rows already present plus rows the user excluded.
        return new CsvBatchResult(detailed.created,detailed.alreadyPresent+detailed.excluded);
    }

    /** The same file and transaction as {@link #importCsvBatch}, with created / already present / excluded split. */
    public CsvImportResult importCsvBatchDetailed(String fileHash, List<CsvImport.Row> rows) {
        SQLiteDatabase sql=getWritableDatabase();
        boolean owned=!sql.inTransaction();
        sql.beginTransaction();
        try {
            long revisionBefore=changes();
            List<CsvImportResult.Row> outcomes=new ArrayList<>();
            int created=0, alreadyPresent=0, excluded=0;
            for (CsvImport.Row row : rows) {
                String key="csv:"+fileHash+":"+row.line;
                if (addCsvEntry(fileHash, row)) {
                    created++;
                    outcomes.add(new CsvImportResult.Row(row,CsvImportResult.Outcome.CREATED,rowIdForKey(key)));
                } else if (excludedKey(key)) {
                    excluded++;
                    outcomes.add(new CsvImportResult.Row(row,CsvImportResult.Outcome.EXCLUDED,null));
                } else if (csvRowKnown(key)) {
                    alreadyPresent++;
                    outcomes.add(new CsvImportResult.Row(row,CsvImportResult.Outcome.ALREADY_PRESENT,rowIdForKey(key)));
                } else throw new IllegalStateException("CSV row "+row.line+" was not saved");
            }
            long revisionAfter=changes();
            sql.setTransactionSuccessful();
            return new CsvImportResult(outcomes,created,alreadyPresent,excluded,owned,revisionBefore,revisionAfter);
        } finally { sql.endTransaction(); }
    }

    private boolean excludedKey(String key) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM excluded_messages WHERE unique_key=? LIMIT 1",new String[]{key})) {
            return c.moveToFirst();
        }
    }

    private Long rowIdForKey(String key) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id FROM transactions WHERE unique_key=? LIMIT 1",new String[]{key})) {
            return c.moveToFirst()?c.getLong(0):null;
        }
    }

    private boolean csvRowKnown(String key) {
        try (Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM transactions WHERE unique_key=? UNION ALL SELECT 1 FROM excluded_messages WHERE unique_key=? LIMIT 1",new String[]{key,key})) {
            return c.moveToFirst();
        }
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
            if ("expense".equals(kind) && !java.util.Arrays.asList("تحويلات","رسوم تحويل","سحب نقدي",MoneyFlow.BANK_FEE_CATEGORY).contains(category)
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

    /** Source id → kind («حساب», «بطاقة ائتمان», «كاش»…) for the statement's internal/cash rules; one query per call. */
    java.util.Map<Long,String> sourceKinds() {
        java.util.Map<Long,String> kinds=new java.util.HashMap<>();
        for (PaymentSource source : paymentSources()) kinds.put(source.id, source.kind);
        return kinds;
    }

    /** A debit whose destination is one of the user's own credit cards: it pays purchases already counted. */
    static boolean paidToOwnCard(Transaction tx, java.util.Map<Long,String> kinds) {
        return "expense".equals(tx.kind) && tx.destinationSourceId != null && "بطاقة ائتمان".equals(kinds.get(tx.destinationSourceId));
    }
    boolean paidToOwnCard(Transaction tx) { return paidToOwnCard(tx, sourceKinds()); }

    /** A purchase paid from the cash wallet: its money was counted when it was withdrawn. */
    static boolean paidFromCash(Transaction tx, java.util.Map<Long,String> kinds) {
        return "expense".equals(tx.kind) && tx.paymentSourceId != null && "كاش".equals(kinds.get(tx.paymentSourceId));
    }
    boolean paidFromCash(Transaction tx) { return paidFromCash(tx, sourceKinds()); }

    /**
     * The rows MoneyFlow works on for [from, to): linked refunds offset on both sides, a repayment to the user's own
     * credit card flagged internal, and a purchase from the cash wallet flagged as already counted. Read-only.
     */
    public List<MoneyFlow.Item> flowItems(LocalDate from, LocalDate to, ZoneId zone) {
        List<MoneyFlow.Item> items=new ArrayList<>();
        java.util.Map<Long,Long> offsets=reimbursementOffsets();
        java.util.Map<Long,String> kinds=sourceKinds();
        for (Transaction tx : transactionsBetween(from, to, zone)) {
            long amount=effectiveAmount(tx, offsets);
            if (amount > 0) items.add(new MoneyFlow.Item(tx.id, tx.when, amount, tx.currency, tx.kind, tx.category, tx.merchant,
                    paidToOwnCard(tx, kinds), paidFromCash(tx, kinds)));
        }
        return items;
    }

    /**
     * The one active source (account or card) whose last digits are this tail and that is not the paying source itself;
     * two sources with the same digits, or none, give null (no guess). Digits compare without leading zeros, as
     * SourceIdentity does («001» and «0001» are the same tail).
     */
    Long ownSourceByTail(String tail, Long payingSource) {
        if (tail == null) return null;
        String wanted = tail.replaceFirst("^0+(?=\\d)", "");
        Long found = null;
        for (PaymentSource source : paymentSources()) {
            if (source.archived || source.last4 == null || !source.last4.replaceFirst("^0+(?=\\d)", "").equals(wanted)) continue;
            if (payingSource != null && accountId(source.id) == accountId(payingSource)) continue;
            if (found != null) return null;
            found = source.id;
        }
        return found;
    }

    /** The user's live credit card with these last four digits, or null when none (or more than one) matches. */
    Long ownCreditCard(String last4) {
        if (last4 == null) return null;
        Long found = null;
        for (PaymentSource source : paymentSources()) {
            if (source.archived || !"بطاقة ائتمان".equals(source.kind) || source.last4 == null || !source.last4.equals(last4)) continue;
            if (found != null) return null;
            found = source.id;
        }
        return found;
    }

    private Long autoSource(String sender, String last4, String body) {
        String identity = SourceIdentity.key(sender, last4);
        if (identity == null) return appSource(sender, body);
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

    /**
     * A wallet/prepaid app (Telda) writes no digits, so its movements had no source and the bank leg of a top-up could
     * never be paired with them (owner test, 22 Oct 2026). One source per app: an alias once learned, else the single
     * live source the user already named after the app (any digits), else a new «Telda» payment-app source (task D1: an
     * app is not a card waiting for its bank account). Two such sources: no guess.
     */
    private Long appSource(String sender, String body) {
        String identity = SourceIdentity.appKey(sender);
        if (identity == null) return null;
        try (Cursor alias = getReadableDatabase().rawQuery(
                "SELECT payment_source_id FROM source_aliases WHERE identity=?", new String[]{identity})) {
            if (alias.moveToFirst()) return alias.getLong(0);
        }
        String app = SourceIdentity.bank(sender);
        Long matched = null;
        for (PaymentSource source : paymentSources()) {
            if (source.archived || !app.equals(SourceIdentity.bank(source.name))) continue;
            if (matched != null) return null;
            matched = source.id;
        }
        if (matched == null) {
            String name = sender == null ? "" : sender.replaceAll("[^\\p{L} ]", " ").trim();
            if (name.isEmpty() || name.length() > 24) name = app;
            long created = addPaymentSource(name, APP_SOURCE_KIND, null);
            if (created <= 0) return null;
            matched = created;
        }
        addSourceAlias(identity, matched);
        return matched;
    }

    /** The kind of a wallet/prepaid app source (Telda): no bank to set, no account to link. */
    static final String APP_SOURCE_KIND = "تطبيق دفع";

    /**
     * Task D1, once: a Telda source created as an unlinked «بطاقة» before 0.4.0 becomes a payment app. Kind only; its rows,
     * aliases and balances stay. Returns the number of sources moved.
     */
    int migrateAppSources() {
        int moved = 0;
        for (PaymentSource source : paymentSources()) {
            if (!"بطاقة".equals(source.kind) || source.parentSourceId != null || SourceIdentity.appKey(source.name) == null) continue;
            ContentValues kind = new ContentValues(); kind.put("kind", APP_SOURCE_KIND);
            moved += getWritableDatabase().update("payment_sources", kind, "id=?", new String[]{Long.toString(source.id)});
        }
        return moved;
    }

    private static String sourceKind(String body) {
        if (body == null) return null;
        String value = body.toLowerCase(Locale.ROOT);
        if(CaptureNotice.cardRepayment(body))return "حساب";
        if (value.matches("(?s).*(?:بطاق|كارت).{0,45}ائتمان.*") || value.contains("credit card")) return "بطاقة ائتمان";
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

    void addSourceAlias(String identity, long sourceId) { accounts.addSourceAlias(identity,sourceId); }

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

    public void putManualBalance(long sourceId, String currency, long balanceMinor, long occurredAt) { accounts.putManualBalance(sourceId,currency,balanceMinor,occurredAt); }

    private void putBalance(long sourceId, String currency, long balanceMinor, long occurredAt,
                            long receivedAt, String source, String uniqueKey) { accounts.putBalance(sourceId,currency,balanceMinor,occurredAt,receivedAt,source,uniqueKey); }

    public BalanceSnapshot latestBalance(long sourceId, String currency) { return accounts.latestBalance(sourceId,currency); }

    public BalanceSnapshot currentBalance(long sourceId, String currency) { return accounts.currentBalance(sourceId,currency); }

    public List<BalanceSnapshot> balanceHistory(long sourceId, String currency, int limit) { return accounts.balanceHistory(sourceId,currency,limit); }

    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : new String[]{"transactions", "excluded_messages", "recurring", "source_aliases", "payment_sources",
                    "budgets", "balance_snapshots", "merchant_rules", "budget_plan_categories", "budget_plans",
                    "assets", "debts", "own_handles", "capture_evidence", "transaction_archive", "commitment_suggestions", "commitment_observations",
                    "reimbursements", "dismissed_patterns", "repair_log", "sms_import_receipts", "capture_events", "card_settlement_allocations", "card_settlements", "commitment_payments", "commitment_cycles"}) db.delete(table, null, null);
            ShadowStore.clearAll(db);
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

    /** The stored rule that refuses this merchant; used to name a refusal the capture write cannot name. */
    boolean merchantIgnored(String merchant) {
        if (merchant==null || merchant.isBlank()) return false;
        MerchantRule rule=matchingRule(merchant);
        return rule!=null && rule.ignored;
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

    public List<Transaction> transactions() { return transactionRows.transactions(); }

    public List<Transaction> recentTransactions(int limit) { return transactionRows.recentTransactions(limit); }
    public List<Transaction> recentSourceTransactions(long sourceId) { return transactionRows.recentSourceTransactions(sourceId); }

    public List<Transaction> recurringCandidateTransactions() { return transactionRows.recurringCandidateTransactions(); }

    /** Uncapped ledger count; UI lists intentionally show only the latest 500 rows. */
    public long transactionCount() { return transactionRows.transactionCount(); }

    /**
     * Persistent database-wide invalidation token. Rolled-back writes do not move it; reads and reopen
     * preserve it. The caller's own uncommitted changes are visible only inside that transaction.
     */
    long changes() {
        return LedgerRevision.read(getReadableDatabase());
    }

    /**
     * Read-only stored observation for one historical row. Its own keys are primary-key probes, and the two
     * commitment tables are small, so reporting side effects never scans the ledger-sized evidence or balance
     * tables. The commitment and hint tables are compared by value, not by row count, so an in-place update
     * (the next cycle's payment, a superseded suggestion) is reported while nothing observable is claimed.
     */
    static final class RowWrites {
        final boolean evidence, receipt, event, balance;
        final String hints, commitments;
        RowWrites(boolean evidence,boolean receipt,boolean event,boolean balance,String hints,String commitments) {
            this.evidence=evidence; this.receipt=receipt; this.event=event; this.balance=balance;
            this.hints=hints; this.commitments=commitments;
        }
    }

    RowWrites rowWritesFor(SmsPreview row) {
        String key=sha256(row.sender+"\n"+row.captureAt+"\n"+row.body);
        String legacyKey=sha256(row.sender+"\n"+(row.receiptKeySafe?row.receivedAt:row.captureAt)+"\n"+row.body);
        String[] keys=new String[]{key,legacyKey};
        return new RowWrites(
                storedIdentity("SELECT 1 FROM capture_evidence WHERE unique_key IN (?,?) LIMIT 1",keys),
                storedIdentity("SELECT 1 FROM sms_import_receipts WHERE unique_key=? LIMIT 1",new String[]{key}),
                storedIdentity("SELECT 1 FROM capture_events WHERE event_key IN (?,?) LIMIT 1",keys),
                storedIdentity("SELECT 1 FROM balance_snapshots WHERE unique_key=? LIMIT 1",
                        new String[]{sha256("balance:"+row.sender+"\n"+row.captureAt+"\n"+row.body)}),
                hintFingerprint(),
                commitmentFingerprint()+storedValue("SELECT GROUP_CONCAT(x) FROM (SELECT (id||'|'||COALESCE(cycle_id,'-')||'|'||amount_minor||'|'||active||'|'||review_status) x FROM commitment_payments ORDER BY id)"));
    }

    /**
     * Values of the two small hint tables, keyed and ordered, so a status change (a superseded proposal) or a
     * proposal that moved to another key is a change even though the row count stayed the same.
     */
    private String hintFingerprint() {
        return storedValue("SELECT (SELECT GROUP_CONCAT(x) FROM (SELECT (hint_key||'|'||status||'|'||amount_minor||'|'||received_at) x " +
                "FROM commitment_suggestions ORDER BY hint_key))||'#'||(SELECT GROUP_CONCAT(y) FROM (SELECT " +
                "(message_key||'|'||hint_key||'|'||amount_minor||'|'||received_at) y FROM commitment_observations ORDER BY message_key))");
    }

    /** Values of the small commitment table, so a next-cycle payment on an already-paid item is still a change. */
    private String commitmentFingerprint() {
        return storedValue("SELECT GROUP_CONCAT(z) FROM (SELECT (id||'|'||next_due||'|'||paid_count||'|'||COALESCE(last_paid_date,'-')||'|'||" +
                "status||'|'||COALESCE(last_paid_due,'-')) z FROM recurring ORDER BY id)");
    }

    private String storedValue(String sql) {
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)) {
            return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";
        }
    }

    /** The stored unique key of a live transaction; null when no such row is live. */
    String rowIdentityKey(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT unique_key FROM transactions WHERE id=?",new String[]{Long.toString(id)})) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }

    /**
     * True only while the exact row is still live. Identity is the stored key, never the row number: SQLite
     * reuses an id after a delete, so a new row can inherit an archived row's id while being a different row.
     */
    boolean rowHasIdentity(long id,String identityKey) {
        try(Cursor c=getReadableDatabase().rawQuery(identityKey==null
                ?"SELECT 1 FROM transactions WHERE id=? AND unique_key IS NULL LIMIT 1"
                :"SELECT 1 FROM transactions WHERE id=? AND unique_key=? LIMIT 1",
                identityKey==null?new String[]{Long.toString(id)}:new String[]{Long.toString(id),identityKey})) {
            return c.moveToFirst();
        }
    }

    /** Repair-level observation: rows this batch archived and fee rows it created, both counted at batch scope. */
    static final class RepairWrites {
        final long archives, feeRows;
        RepairWrites(long archives,long feeRows) { this.archives=archives; this.feeRows=feeRows; }
    }

    RepairWrites repairWrites() {
        return new RepairWrites(countOf("transaction_archive",null),
                countOf("transactions","fee_parent_id IS NOT NULL"));
    }

    private long countOf(String table,String where) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table+(where==null?"":" WHERE "+where),null)) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    public Transaction latestInserted() { return transactionRows.latestInserted(); }

    public Transaction transactionById(long id) { return transactionRows.transactionById(id); }

    public List<Transaction> transactionsForMonth(LocalDate month) { return transactionRows.transactionsForMonth(month); }

    public List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive) {
        return transactionRows.transactionsBetween(startDate,endExclusive);
    }

    public List<Transaction> transactionsBetween(LocalDate startDate, LocalDate endExclusive,ZoneId zone) {
        return transactionRows.transactionsBetween(startDate,endExclusive,zone);
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
                "SELECT currency, SUM(amount_minor) FROM transactions WHERE kind='expense' AND category<>? AND occurred_at>=? AND occurred_at<? GROUP BY currency",
                new String[]{MoneyFlow.CASH_WITHDRAWAL_CATEGORY, Long.toString(start), Long.toString(end)})) {
            while (cursor.moveToNext()) totals.put(cursor.getString(0), cursor.getLong(1));
        }
        return totals;
    }

    public long monthlyTotalForKind(LocalDate month, String currency, String kind) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE kind=? AND currency=? AND NOT(kind='expense' AND category=?) AND occurred_at>=? AND occurred_at<?",
                new String[]{kind, currency, MoneyFlow.CASH_WITHDRAWAL_CATEGORY, Long.toString(start), Long.toString(end)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public List<CategoryTotal> monthlyCategoryTotals(LocalDate month) {
        long start = month.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = month.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<CategoryTotal> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT category, currency, SUM(amount_minor) FROM transactions " +
                "WHERE kind='expense' AND category<>? AND occurred_at>=? AND occurred_at<? GROUP BY category, currency ORDER BY currency, SUM(amount_minor) DESC",
                new String[]{MoneyFlow.CASH_WITHDRAWAL_CATEGORY, Long.toString(start), Long.toString(end)})) {
            while (cursor.moveToNext()) result.add(new CategoryTotal(cursor.getString(0),
                    cursor.getString(1), cursor.getLong(2)));
        }
        return result;
    }

    public void updateCategory(long id, String category) {
        if(new CardSettlements(this).isSettlement(id))return;
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
                if(!new CardSettlements(this).related(id))changed+=sql.update("transactions",values,"id=? AND kind='expense'",new String[]{Long.toString(id)});
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
        updateTransactionRow(id,amountMinor,currency,merchant,category,sourceId,kind,destinationId,note,occurredAt);
    }

    /** Affected primary rows; the public compatibility API still ignores this count. */
    int updateTransactionRow(long id, long amountMinor, String currency, String merchant,
                             String category, Long sourceId, String kind, Long destinationId,
                             String note, Long occurredAt) {
        return transactionRows.update(id,amountMinor,currency,merchant,category,sourceId,kind,destinationId,note,occurredAt);
    }

    public long addPaymentSource(String name, String kind, String last4) { return accounts.addPaymentSource(name,kind,last4); }

    public int sourceAliasCount(long id) { return accounts.sourceAliasCount(id); }

    public void mergeSources(long keepId, long removeId) { accounts.mergeSources(keepId,removeId); }

    public List<PaymentSource> paymentSources() { return accounts.paymentSources(); }

    public PaymentSource paymentSource(long id) { return accounts.paymentSource(id); }

    public long accountId(long id) { return accounts.accountId(id); }

    /** Read-only grouping by confirmed parent relationships. Same-bank cards are never guessed to be linked. */
    public java.util.Map<Long,List<Transaction>> accountTransactionGroups(LocalDate from,LocalDate to,String currency) { return accounts.accountTransactionGroups(from,to,currency); }

    /** Keep card/SMS identities and all financial rows intact; only the confirmed relationship changes. */
    public void linkCardToAccount(long cardId, long accountId) { accounts.linkCardToAccount(cardId,accountId); }

    public void unlinkCard(long cardId) { accounts.unlinkCard(cardId); }

    public void updateSourceProfile(long id,String displayName,boolean archived,boolean reviewed) { accounts.updateSourceProfile(id,displayName,archived,reviewed); }

    int updateSourceProfileRow(long id,String displayName,boolean archived,boolean reviewed) { return accounts.updateSourceProfileRow(id,displayName,archived,reviewed); }

    boolean hasSourceAlias(String identity) { return accounts.hasSourceAlias(identity); }
    Long sourceAliasOwner(String identity) { return accounts.sourceAliasOwner(identity); }
    int updateSourceBankRow(long id,String bank,String displayName) { return accounts.updateSourceBankRow(id,bank,displayName); }

    public List<PaymentSource> accountSources(boolean includeArchived) { return accounts.accountSources(includeArchived); }

    public List<PaymentSource> selectableSources() { return accounts.selectableSources(); }

    public List<PaymentSource> linkedCards(long accountId) { return accounts.linkedCards(accountId); }

    public String sourceLabel(long id) { return accounts.sourceLabel(id); }

    public long sourceLastActivity(long sourceId) { return accounts.sourceLastActivity(sourceId); }

    public void deletePaymentSource(long id) { accounts.deletePaymentSource(id); }

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
                if (plan.categories.contains(cursor.getString(0)) &&
                        MoneyFlow.bucket("expense", cursor.getString(0)) != MoneyFlow.Bucket.CASH_WITHDRAWAL)
                    spent += cursor.getLong(1);
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
        transactionRows.delete(id);
    }

    public long transferFee(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE fee_parent_id=?",new String[]{Long.toString(id)})) {
            return c.moveToFirst()?c.getLong(0):0;
        }
    }

    /** User-confirmed reconciliation: one principal transfer and a separate expense for its bank fee. */
    public void reconcileOwnTransfer(long debitId,long principal,long fee,long from,long to,List<Long> receiptIds) {
        reconcileOwnTransfer(debitId,principal,fee,from,to,receiptIds,false);
    }

    /** Explicit opt-in: the confirmed fee is deliberately omitted from spending, never silently discarded. */
    public void reconcileOwnTransferWithoutFee(long debitId,long principal,long omittedFee,long from,long to,List<Long> receiptIds) {
        reconcileOwnTransfer(debitId,principal,omittedFee,from,to,receiptIds,true);
    }

    enum FeeBlockReason { EXCLUDED, RECORDED, IGNORED }
    static final class TransferFeeUnavailable extends IllegalArgumentException {
        final FeeBlockReason reason;
        TransferFeeUnavailable(FeeBlockReason reason) {super("Transfer fee is unavailable: "+reason);this.reason=reason;}
        boolean mayOmit() {return reason!=FeeBlockReason.RECORDED;}
    }

    private void reconcileOwnTransfer(long debitId,long principal,long fee,long from,long to,List<Long> receiptIds,boolean omitFee) {
        SQLiteDatabase writable=getWritableDatabase();writable.beginTransaction();
        try {
            Transaction debit=transactionById(debitId);
            if(debit==null || principal<=0 || fee<0 || accountId(from)==accountId(to) || "income".equals(debit.kind)) throw new IllegalArgumentException("Invalid transfer");
            if(new CardSettlements(this).related(debitId))throw new IllegalArgumentException("Undo card repayment links first");
            if(hasReimbursement(debitId))throw new IllegalArgumentException("Unlink repayment before changing to own transfer");
            for(long receiptId:receiptIds)if(hasReimbursement(receiptId))
                throw new IllegalArgumentException("Unlink repayment before changing to own transfer");
            long existingFee=0;
            try(Cursor c=writable.rawQuery("SELECT COALESCE(SUM(amount_minor),0) FROM transactions WHERE fee_parent_id=?",new String[]{Long.toString(debitId)})) {if(c.moveToFirst()) existingFee=c.getLong(0);}
            if(existingFee>0 && existingFee!=fee) throw new IllegalArgumentException("Existing fee differs; review its source");
            if(debit.amountMinor!=principal+fee && !(debit.amountMinor==principal && existingFee==fee))
                throw new IllegalArgumentException("Principal and fee must equal the confirmed debit");
            FeeBlockReason blocked=fee>0&&existingFee==0?feeRowBlockReason(debitId,from):null;
            if(omitFee) {
                if(fee<=0||existingFee!=0||(blocked!=FeeBlockReason.EXCLUDED&&blocked!=FeeBlockReason.IGNORED))
                    throw new IllegalArgumentException("Fee omission is no longer applicable; review the transfer again");
            } else if(blocked!=null) throw new TransferFeeUnavailable(blocked);
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
            values.put("merchant","تحويل بين حساباتي");
            if(omitFee)values.put("note",(debit.note==null||debit.note.isBlank()?"":debit.note+"\n")+
                    "تم الربط بدون رسوم بموافقتك: "+java.math.BigDecimal.valueOf(fee,2).toPlainString()+" "+debit.currency+" رسوم مش هتتحسب في مصروفاتك");
            if(writable.update("transactions",values,"id=?",new String[]{Long.toString(debitId)})!=1)
                throw new IllegalArgumentException("Transfer was not updated");
            if(fee>0 && existingFee==0 && !omitFee) {
                String bank="البنك";for(PaymentSource source:paymentSources()) if(source.id==from) bank=source.name;
                boolean added=insertEntry("fee:"+debitId,debit.when,fee,debit.currency,"رسوم تحويل "+bank,"رسوم تحويل",
                        "رسوم من العملية",null,null,from,"expense",null,"رسوم مؤكدة من فرق الخصم وأصل التحويل");
                // The debit was already rewritten to the principal: a fee that is not saved must undo the whole split.
                if(!added) throw new IllegalArgumentException("Fee could not be saved; transfer left unchanged");
                ContentValues related=new ContentValues();related.put("fee_parent_id",debitId);related.put("user_edited",1);
                if(writable.update("transactions",related,"unique_key=?",new String[]{"fee:"+debitId})!=1)
                    throw new IllegalArgumentException("Fee could not be linked; transfer left unchanged");
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
            for(SourceEvidence evidence:sourceEvidence(keepId)) if("SMS".equals(evidence.source) && evidence.body!=null) {
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
        return commitments.addRecurring(merchant,amount,currency,nextDue);
    }

    public boolean addRecurring(String merchant, long amount, String currency, LocalDate nextDue,
                                String cycle, String kind) {
        return commitments.addRecurring(merchant,amount,currency,nextDue,cycle,kind);
    }

    /** Inserts or updates an installment, subscription or bill. Returns the row id, or -1 for a duplicate. */
    public long putCommitment(Long id, String merchant, long amount, String currency, LocalDate nextDue,
                              String cycle, String kind, Integer totalCount, int paidCount, int remindDays,
                              String matchText, Long paymentSourceId) {
        return commitments.putCommitment(id,merchant,amount,currency,nextDue,cycle,kind,totalCount,paidCount,remindDays,matchText,paymentSourceId);
    }

    public List<Recurring> recurring() { return commitments.recurring(); }

    public Recurring recurringById(long id) { return commitments.recurringById(id); }
    List<Recurring> storedRecurring(){return commitments.storedRecurring();}
    Recurring storedRecurringById(long id){for(Recurring row:storedRecurring())if(row.id==id)return row;return null;}
    List<CommitmentPayments.Cycle> commitmentCycles(long id){return commitmentPayments.cycles(id);}
    List<CommitmentPayments.Payment> commitmentPayments(long id){return commitmentPayments.history(id);}
    long linkCommitmentPayment(long id,LocalDate due,long transactionId,long amount){return commitmentPayments.link(id,due,transactionId,amount);}
    boolean undoCommitmentPayment(long paymentId){return commitmentPayments.undo(paymentId);}
    EvidenceVault.Read commitmentPaymentEvidence(long id){return commitmentPayments.evidence(id);}
    CommitmentPayments.AllocationPreview commitmentPaymentPreview(long id,LocalDate due,long transactionId){return commitmentPayments.preview(id,due,transactionId);}
    long linkCommitmentPayment(CommitmentPayments.AllocationPreview preview,long amount){return commitmentPayments.link(preview,amount);}
    List<Transaction> commitmentPaymentCandidates(LocalDate due){return commitmentPayments.candidates(due);}
    String commitmentIdentity(long id){return commitmentPayments.key(id);}
    boolean confirmCommitmentCycle(long id,LocalDate due,String key,LocalDate day){return commitmentPayments.confirmCycle(id,due,key,day);}
    List<CommitmentPayments.ProviderNotice> pendingCommitmentConfirmations(){return commitmentPayments.pendingProviders();}
    boolean resolveCommitmentConfirmation(long notice,long id,LocalDate due){return commitmentPayments.resolveProvider(notice,id,due);}
    boolean resolveCommitmentConfirmation(long notice,long id,LocalDate due,String key){return commitmentPayments.resolveProvider(notice,id,due,key);}
    boolean dismissCommitmentConfirmation(long notice){return commitmentPayments.dismissProvider(notice);}

    public void setDueDay(long id, int day) { commitments.setDueDay(id,day); }

    public void setRemindDays(long id, int days) { commitments.setRemindDays(id,days); }

    public void removeRecurring(long id) { commitments.removeRecurring(id); }

    public void markAlerted(long id, LocalDate today) { commitments.markAlerted(id,today); }

    /**
     * A payment made a few days before a due date pays that due date (e.g. the 30 Sep transfer pays the 1 Oct installment).
     * If the stored next due date is still that covered date, move it one cycle on. paid_count already includes the payment.
     * Window: 7 days for monthly, 2 for weekly, 15 for yearly. Returns how many commitments moved.
     */
    public int normalizeEarlyPayments() { return commitments.normalizeEarlyPayments(); }

    /** Records one payment of a commitment and moves its due date to the next cycle, at most once per cycle. */
    public boolean markRecurringPaid(long id, LocalDate day) { return commitments.markRecurringPaid(id,day); }

    /** Stops future dues without inventing a payment. Optionally confirms every remaining installment as paid. */
    public boolean finishRecurring(long id, boolean allInstallmentsPaid) { return commitments.finishRecurring(id,allInstallmentsPaid); }

    public boolean reopenRecurring(long id) { return commitments.reopenRecurring(id); }

    // ---- Reimbursements: money that came back for something bought on someone else's behalf ----

    /** What each linked row should be reduced by: the purchase by what came back, the incoming payment by the part it repaid. */
    public java.util.Map<Long,Long> reimbursementOffsets() { return reimbursements.reimbursementOffsets(); }

    /** The amount a row contributes to spending/inflow after reimbursements; never negative. */
    public static long effectiveAmount(Transaction tx,java.util.Map<Long,Long> offsets) {
        Long offset=offsets.get(tx.id);
        return offset==null?tx.amountMinor:Math.max(0,tx.amountMinor-offset);
    }

    public long reimbursedOf(long expenseId) { return reimbursements.reimbursedOf(expenseId); }

    /** The purchase an incoming payment repaid, or null. */
    public Transaction reimbursedExpense(long incomeId) { return reimbursements.reimbursedExpense(incomeId); }

    /** A stored link that can no longer apply (the purchase became a cash withdrawal); kept for audit and undo. */
    public Transaction ignoredReimbursement(long incomeId) { return reimbursements.ignoredLinkedExpense(incomeId); }

    public List<Transaction> reimbursingIncomes(long expenseId) { return reimbursements.reimbursingIncomes(expenseId); }

    /** Default to the incoming payment's day; older purchases are an explicit second choice. */
    public List<Transaction> reimbursementCandidates(Transaction income) {
        return reimbursementCandidates(income,false);
    }

    public List<Transaction> reimbursementCandidates(Transaction income,boolean older) { return reimbursementFlow.reimbursementCandidates(income,older); }


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

    ReimbursementPreview previewReimbursement(long incomeId,long expenseId) { return reimbursementFlow.previewReimbursement(incomeId,expenseId); }

    /** The confirmation must still describe the records the user actually reviewed. */
    long confirmReimbursement(ReimbursementPreview preview) { return reimbursementFlow.confirmReimbursement(preview); }

    /** Undo this exact link only; never erase a replacement or another incoming repayment. */
    boolean undoReimbursement(long incomeId,long expenseId,long amount) { return reimbursements.undoReimbursement(incomeId,expenseId,amount); }

    /** Links an incoming payment to the purchase it repaid. Only the part up to what is left of the purchase counts. */
    public long linkReimbursement(long incomeId,long expenseId) { return reimbursements.linkReimbursement(incomeId,expenseId); }

    public void unlinkReimbursement(long transactionId) { reimbursements.unlinkReimbursement(transactionId); }

    // ---- Data repair: promotions, double captures, own transfers (see RepairRules) ----

    /** Bump when a new rule must re-run once over all existing data. */
    public static final int REPAIR_VERSION = 3;

    public static final class RepairReport {
        boolean failed;
        private final java.util.Map<Long,Long> mergedRows=new java.util.HashMap<>();
        long follow(long id){while(mergedRows.containsKey(id))id=mergedRows.get(id);return id;}
        public int promotions, duplicates, ownTransfers, commitments, recategorized, evidenceFailures, trailingSigns;
        public long movedMinor, feesMinor;
        public boolean changed() { return promotions + duplicates + ownTransfers + commitments + recategorized + trailingSigns > 0; }
        /** Rows this repair moved into an archive, counted for the import contract that reports them. */
        int mergedRowCount() { return mergedRows.size(); }
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
            report.promotions=archivePromotions(report);
            migrateAppSources();
            report.trailingSigns=repairTrailingSigns();
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

    /**
     * For a caller that already owns the transaction (a batch import): repairs inside it and lets any failure reach that caller,
     * which rolls the whole batch back. A swallowed failure here would doom the outer transaction while it still looked successful.
     */
    public RepairReport repairBetweenInCallerTransaction(long from,long to) {
        if (!getWritableDatabase().inTransaction()) throw new IllegalStateException("Caller must own the transaction");
        RepairReport report=new RepairReport();
        repairWindow(from,to,report);
        return report;
    }

    public RepairReport repairBetween(long from,long to) {
        RepairReport report=new RepairReport();
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();
        try {repairWindow(from,to,report);sql.setTransactionSuccessful();}
        catch (RuntimeException failed) {report.failed=true;report.mergedRows.clear();android.util.Log.e("FollowMoney","Repair skipped: "+failed.getClass().getSimpleName());/* never lose a capture because a repair failed */}
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
            if(mergeEntries(duplicate.keepId,duplicate.removeId,"same payment: bank SMS and app notification")){
                report.mergedRows.put(duplicate.removeId,duplicate.keepId);report.duplicates++;
            }
        }
        // The receipt of an already settled own transfer, delivered again by a payment app: the settled transfer is
        // user-edited (reconciliation marks it so), so it is read here with every row and only the copy is removed.
        for (RepairRules.Duplicate again:RepairRules.lateReceiptDuplicates(repairRows(from,to))) {
            if(mergeEntries(again.keepId,again.removeId,"receipt of an own transfer seen again")){
                report.mergedRows.put(again.removeId,again.keepId);report.duplicates++;
            }
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
                report.mergedRows.put(own.inId,own.outId);
                ContentValues note=new ContentValues();note.put("note","اتعرف تلقائيًا: خرج من حسابك ووصل حسابك التاني خلال دقايق");
                getWritableDatabase().update("transactions",note,"id=? AND note IS NULL",new String[]{Long.toString(own.outId)});
                debitAmounts.add(debit);
                report.ownTransfers++;report.movedMinor+=own.principal;report.feesMinor+=own.fee;
            } catch (IllegalArgumentException unexpected) {
                // canReconcile already screened every expected refusal. A throw here has doomed the surrounding
                // transaction, so it must reach the owner (an import batch) instead of looking like a skipped pair.
                throw new IllegalStateException("Own-transfer repair failed unexpectedly",unexpected);
            }
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
        if (!(debit.amountMinor==own.principal+own.fee || debit.amountMinor==own.principal && existingFee==own.fee)) return false;
        if (own.fee>0 && existingFee==0 && !feeRowAvailable(own.outId,own.fromAccount)) {
            // A matched same-value UPDATE would advance the durable revision on every repair pass.
            getWritableDatabase().execSQL("UPDATE transactions SET needs_review=1 WHERE id=? AND needs_review=0",new Object[]{own.outId});
            return false;
        }
        return true;
    }

    /** The separate fee row reconcileOwnTransfer will insert must not be refused, or the pair is left for manual review. */
    private boolean feeRowAvailable(long debitId,long fromAccount) {
        return feeRowBlockReason(debitId,fromAccount)==null;
    }

    private FeeBlockReason feeRowBlockReason(long debitId,long fromAccount) {
        String key="fee:"+debitId;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT 'recorded' FROM transactions WHERE unique_key=? UNION ALL SELECT 'excluded' FROM excluded_messages WHERE unique_key=? LIMIT 1",new String[]{key,key})) {
            if (c.moveToFirst()) return "excluded".equals(c.getString(0))?FeeBlockReason.EXCLUDED:FeeBlockReason.RECORDED;
        }
        String bank="البنك";for(PaymentSource source:paymentSources()) if(source.id==fromAccount) bank=source.name;
        MerchantRule rule=matchingRule("رسوم تحويل "+bank);
        return rule==null || !rule.ignored?null:FeeBlockReason.IGNORED;
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
    private int archivePromotions(RepairReport report) {
        java.util.Map<Long,Boolean> verdict=new java.util.LinkedHashMap<>();
        java.util.Set<Long> unreadable=new java.util.HashSet<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT e.transaction_id,e.encrypted_body FROM capture_evidence e " +
                "JOIN transactions t ON t.id=e.transaction_id WHERE t.user_edited=0 AND t.fee_parent_id IS NULL " +
                "AND NOT EXISTS (SELECT 1 FROM reimbursements r WHERE r.income_id=t.id OR r.expense_id=t.id)",null)) {
            while (c.moveToNext()) {
                long id=c.getLong(0);
                EvidenceVault.Read body=EvidenceVault.read(c.isNull(1)?null:c.getString(1));
                // Evidence this device cannot open is reported, never used to delete a row.
                if(body.failed) unreadable.add(id);
                boolean promotion=body.body!=null && ExpenseParser.isPromotion(body.body);
                verdict.put(id,verdict.getOrDefault(id,true) && promotion);
            }
        }
        int archived=0;
        for (java.util.Map.Entry<Long,Boolean> entry:verdict.entrySet()) {
            if(unreadable.contains(entry.getKey())) {report.evidenceFailures++;continue;}
            if (entry.getValue()) {archiveEntry(entry.getKey(),"promotional message, not a payment",true);archived++;}
        }
        return archived;
    }

    /**
     * Rows an older parser booked from HSBC's trailing-sign alerts: a "+" credit saved as spending, or the bank name
     * saved as the merchant. Reads the stored source message; user edits, linked rows (refund, card settlement,
     * commitment payment, reconciled transfer) and unreadable evidence are left alone.
     * m7 (0.4.0): kept with REPAIR_VERSION 3 for one more round. Rows written by the legacy parser before 0.3.1 on
     * Beta 0.2.x phones have not run this repair yet; remove it in 0.4.x once every tester has upgraded.
     */
    private int repairTrailingSigns() {
        java.util.Map<Long,String[]> evidence=new java.util.LinkedHashMap<>();
        java.util.Set<Long> skip=new java.util.HashSet<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT e.transaction_id,e.sender,e.encrypted_body FROM capture_evidence e " +
                "JOIN transactions t ON t.id=e.transaction_id WHERE t.kind='expense' AND t.user_edited=0 AND t.fee_parent_id IS NULL " +
                "AND t.destination_source_id IS NULL AND NOT EXISTS (SELECT 1 FROM reimbursements r WHERE r.income_id=t.id OR r.expense_id=t.id) " +
                "ORDER BY e.received_at",null)) {
            while (c.moveToNext()) {
                long id=c.getLong(0);
                EvidenceVault.Read body=EvidenceVault.read(c.isNull(2)?null:c.getString(2));
                if (body.failed || body.body==null || !ExpenseParser.trailingSignFormat(body.body)) { skip.add(id); continue; }
                evidence.putIfAbsent(id,new String[]{c.getString(1),body.body});
            }
        }
        int fixed=0;
        CardSettlements settlements=new CardSettlements(this);
        for (java.util.Map.Entry<Long,String[]> entry:evidence.entrySet()) {
            long id=entry.getKey();
            if (skip.contains(id) || settlements.related(id) || commitmentLinked(id)) continue;
            Transaction tx=transactionById(id);
            if (tx==null) continue;
            RepairRules.Row row=new RepairRules.Row(tx.id,tx.when,tx.when,tx.amountMinor,tx.currency,tx.kind,tx.category,tx.source,
                    "expense",tx.paymentSourceId,tx.destinationSourceId,false,tx.merchant);
            RepairRules.TrailingSignFix fix=RepairRules.trailingSignFix(row,entry.getValue()[0],entry.getValue()[1]);
            if (fix==null) continue;
            ContentValues values=new ContentValues();values.put("merchant",fix.merchant);values.put("category",fix.category);
            if (fix.toIncome) values.put("kind","income");
            if (getWritableDatabase().update("transactions",values,"id=? AND user_edited=0",new String[]{Long.toString(id)})==0) continue;
            if (fix.toIncome) {
                ContentValues direction=new ContentValues();direction.put("direction","income");
                getWritableDatabase().update("capture_evidence",direction,"transaction_id=?",new String[]{Long.toString(id)});
            }
            fixed++;
        }
        return fixed;
    }

    private boolean commitmentLinked(long id) {
        String identity=rowIdentityKey(id);
        if (identity==null) return false;
        try (Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM commitment_payments WHERE transaction_key=? AND active=1 LIMIT 1",new String[]{identity})) {
            return c.moveToFirst();
        }
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
     * amount require named counterparty evidence or an explicit reviewed cycle allocation.
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
        if(new CardSettlements(this).related(id))return;
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



    /** InstaPay bill payments name the service without an amount; they can still close a matching commitment. */


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
