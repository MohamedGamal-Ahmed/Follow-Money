package com.mohamedgamal.masareef;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent shadow storage (schema v26). Three tables, none tracked by LedgerRevision, so a write here never looks
 * like a ledger change:
 * shadow_observations: one row per observed message, beside the legacy record it describes (transaction_id /
 * message_hash), with its delivery identity, content hash and ShadowDedupe verdict. Decision, reasons, score and kind
 * are plain; field values (amounts, merchant, party) and the legacy values are one JSON object sealed by EvidenceVault
 * in encrypted_body.
 * ununderstood_messages: "messages I did not understand", one row per distinct message, text sealed in encrypted_body.
 * learning_rules: the local-learning shape memory (hashes and counts, never text) and, later, user corrections.
 * Every write is diagnostics only: callers swallow failures and the legacy decision stays final.
 */
final class ShadowStore {
    static void create(SQLiteDatabase sql) {
        sql.execSQL("CREATE TABLE IF NOT EXISTS shadow_observations(id INTEGER PRIMARY KEY, observed_at INTEGER NOT NULL, " +
                "received_at INTEGER NOT NULL, channel TEXT NOT NULL, source TEXT, origin TEXT NOT NULL DEFAULT 'live', message_hash TEXT, " +
                "delivery_identity TEXT, content_hash TEXT NOT NULL, dedupe TEXT NOT NULL, dedupe_reason TEXT, duplicate_of INTEGER, " +
                "transaction_id INTEGER, legacy_reason TEXT, legacy_decision TEXT NOT NULL, status TEXT NOT NULL, type TEXT, " +
                "stage_decision TEXT, scored_decision TEXT, decision TEXT, score INTEGER NOT NULL DEFAULT -1, movement_kind TEXT, " +
                "reasons TEXT NOT NULL, policy TEXT NOT NULL, encrypted_body TEXT)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS shadow_observed ON shadow_observations(observed_at DESC)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS shadow_transaction ON shadow_observations(transaction_id)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS shadow_content ON shadow_observations(content_hash, received_at)");
        sql.execSQL("CREATE INDEX IF NOT EXISTS shadow_identity ON shadow_observations(delivery_identity)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS ununderstood_messages(message_hash TEXT PRIMARY KEY, first_seen INTEGER NOT NULL, " +
                "last_seen INTEGER NOT NULL, seen_count INTEGER NOT NULL DEFAULT 1, channel TEXT NOT NULL, source TEXT, " +
                "reasons TEXT NOT NULL, policy TEXT NOT NULL, encrypted_body TEXT)");
        sql.execSQL("CREATE TABLE IF NOT EXISTS learning_rules(rule_key TEXT PRIMARY KEY, kind TEXT NOT NULL, source TEXT, " +
                "dismissed INTEGER NOT NULL DEFAULT 0, confirmed INTEGER NOT NULL DEFAULT 0, direction TEXT, " +
                "conflicting INTEGER NOT NULL DEFAULT 0, merchant TEXT, category TEXT, updated_at INTEGER NOT NULL)");
    }



    /**
     * Stores one observation beside the ledger row it decided (transactionId, or null when it recorded nothing). The
     * legacy columns stay for older rows; the new pipeline decides, so they are written as LEGACY_OFF.
     */
    static void record(SQLiteDatabase sql, String origin, MessageNormalizer.Raw raw, String legacyBody, String source, long receivedAt,
                       Long transactionId, String legacyReason, String legacyDecision, Long legacyAmount, String legacyCurrency,
                       String legacyDirection, String legacyMerchant, ParserShadow.Observation o, String extraReason) {
        if (o == null) return;
        // Owner decision: a return of the same charge minutes later is an expense offset linked to it, never income.
        String reasons = String.join(",", o.reasons);
        // A legacy guard kept a recording decision out of the ledger (LEGACY_GUARD:UNSUCCESSFUL|FUTURE_ONLY|NOTICE).
        if (extraReason != null) reasons += (reasons.isEmpty() ? "" : ",") + extraReason;
        String effect = o.values.get("accounting_effect");
        long offsetOf = ShadowDiff.newRecords(o.decision) && (effect == null || effect.equals("expense_offset")) ? offsetOf(sql, origin, source, receivedAt, o) : -1;
        if (offsetOf >= 0) {
            o.values.put("accounting_effect", "expense_offset");
            o.values.put("offset_of", String.valueOf(offsetOf));
            reasons += ",LINKED_EXPENSE_OFFSET";
        }
        org.json.JSONObject sealed = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<String, String> e : o.values.entrySet()) sealed.put(e.getKey(), e.getValue());
            org.json.JSONObject legacy = new org.json.JSONObject();
            if (legacyAmount != null) legacy.put("amount_minor", String.valueOf(legacyAmount));
            if (legacyCurrency != null) legacy.put("currency", legacyCurrency);
            if (legacyDirection != null) legacy.put("direction", legacyDirection);
            if (legacyMerchant != null) legacy.put("merchant", legacyMerchant);
            sealed.put("legacy", legacy);
        } catch (org.json.JSONException impossible) { /* string keys and values only */ }
        String hash = legacyBody == null ? null : CaptureIdentity.hash(legacyBody);
        String content = ShadowDedupe.contentHash(raw == null ? source : raw.source, raw == null ? String.valueOf(legacyBody) : MessageNormalizer.normalize(raw).text);
        String identity = raw == null ? null : raw.identity;
        ShadowDedupe.Verdict dedupe = ShadowDedupe.decide(new ShadowDedupe.Delivery(-1, source, identity, receivedAt, content),
                earlier(sql, origin, identity, content, receivedAt));
        ContentValues row = new ContentValues();
        row.put("observed_at", o.observedAt);
        row.put("received_at", receivedAt);
        row.put("channel", o.channel);
        row.put("source", source);
        row.put("origin", origin);
        row.put("message_hash", hash);
        row.put("delivery_identity", identity);
        row.put("content_hash", content);
        row.put("dedupe", dedupe.capture);
        row.put("dedupe_reason", dedupe.reason);
        if (dedupe.duplicateOf >= 0) row.put("duplicate_of", dedupe.duplicateOf);
        if (transactionId != null) row.put("transaction_id", transactionId);
        row.put("legacy_reason", legacyReason);
        row.put("legacy_decision", legacyDecision);
        row.put("status", o.status);
        row.put("type", o.type);
        row.put("stage_decision", o.stageDecision);
        row.put("scored_decision", o.scoredDecision);
        row.put("decision", o.decision);
        row.put("score", o.score);
        row.put("movement_kind", o.movementKind);
        row.put("reasons", reasons);
        row.put("policy", o.policy);
        row.put("encrypted_body", EvidenceVault.encrypt(sealed.toString()));
        sql.insert("shadow_observations", null, row);
        bound(sql, origin);
        if (raw != null && ("UNUNDERSTOOD".equals(o.decision) || missedFromBank(source, raw, o))) rememberUnunderstood(sql, raw, content, source, o);
    }

    /**
     * Owner decision m6 (0.4.0): a message the pipeline ignored from a known bank or a payment app, naming an amount,
     * may be a movement it missed; it waits on «رسائل من بنوكك ما اتسجلتش». The user's own ignore rule or dismissal is
     * not a miss.
     */
    static boolean missedFromBank(String source, MessageNormalizer.Raw raw, ParserShadow.Observation o) {
        if (!"IGNORE".equals(o.decision) || o.reasons.contains("LEARNED_IGNORED_MERCHANT") || o.reasons.contains("LEARNED_DISMISSED_SHAPE")) return false;
        if (BrandCatalog.bank(source) == null && SourceIdentity.appKey(source) == null) return false;
        return SmsPreview.hasAmount(MessageNormalizer.normalize(raw).text);
    }

    /** «تجاهل» on the missed-messages page: the message leaves the list (it returns only if delivered again). */
    static void dismissUnunderstood(SQLiteDatabase sql, String messageHash) {
        sql.delete("ununderstood_messages", "message_hash=?", new String[]{messageHash});
    }

    /**
     * The pipeline could not decide this message (PipelineDecider.Failed), so the legacy parser did. A note with the
     * reason and what legacy did, no observation and no values; the pipeline is not run again. Count them with
     * SELECT COUNT(*) FROM shadow_observations WHERE decision='PIPELINE_FAILED'.
     */
    static void recordFailure(SQLiteDatabase sql, String origin, String body, String source, long receivedAt, Long transactionId, String reason) {
        ContentValues row = new ContentValues();
        row.put("observed_at", System.currentTimeMillis());
        row.put("received_at", receivedAt);
        row.put("channel", "UNKNOWN");
        row.put("source", source);
        row.put("origin", origin);
        row.put("message_hash", body == null ? null : CaptureIdentity.hash(body));
        row.put("content_hash", ShadowDedupe.contentHash(source, String.valueOf(body)));
        row.put("dedupe", "NEW");
        if (transactionId != null) row.put("transaction_id", transactionId);
        row.put("legacy_decision", transactionId != null ? "RECORDED" : "SKIPPED");
        row.put("status", reason);
        row.put("decision", "PIPELINE_FAILED");
        row.put("reasons", reason);
        row.put("policy", ParsingPolicy.POLICY_VERSION);
        sql.insert("shadow_observations", null, row);
        bound(sql, origin);
    }

    /** Each origin keeps its newest rows only: live SHADOW_STORE_MAX_ROWS, import IMPORT_STORE_MAX_ROWS. */
    private static void bound(SQLiteDatabase sql, String origin) {
        int max = "import".equals(origin) ? ParsingPolicy.IMPORT_STORE_MAX_ROWS : ParsingPolicy.SHADOW_STORE_MAX_ROWS;
        sql.execSQL("DELETE FROM shadow_observations WHERE origin=? AND id < " +
                "(SELECT id FROM shadow_observations WHERE origin=? ORDER BY id DESC LIMIT 1 OFFSET ?)", new Object[]{origin, origin, max - 1});
    }

    /** The earlier recorded charge from the same source this return offsets (ShadowOffset), or -1. Opens only rows in the window. */
    static long offsetOf(SQLiteDatabase sql, String origin, String source, long receivedAt, ParserShadow.Observation o) {
        Long amount;
        try { amount = o.values.containsKey("amount_minor") ? Long.valueOf(o.values.get("amount_minor")) : null; } catch (NumberFormatException e) { return -1; }
        boolean party = o.values.containsKey("merchant_key") || o.values.containsKey("counterparty");
        boolean refund = "expense_offset".equals(o.values.get("accounting_effect"));
        ShadowOffset.Movement current = new ShadowOffset.Movement(-1, source, receivedAt, o.values.get("direction"), amount, o.values.get("currency"), true, party,
                o.values.get("merchant_key"));
        if (!"in".equals(current.direction) || party && !refund || amount == null) return -1;
        List<ShadowOffset.Movement> earlier = new ArrayList<>();
        java.util.Set<Long> claimed = new java.util.HashSet<>();
        try (Cursor c = sql.rawQuery("SELECT id, received_at, decision, encrypted_body FROM shadow_observations WHERE origin=? AND source=? AND received_at BETWEEN ? AND ?",
                new String[]{origin, source, Long.toString(receivedAt - ParsingPolicy.FEE_REFUND_WINDOW_MS), Long.toString(receivedAt)})) {
            while (c.moveToNext()) {
                String body = EvidenceVault.decrypt(c.getString(3));
                if (body == null) continue;
                try {
                    org.json.JSONObject v = new org.json.JSONObject(body);
                    if (v.has("offset_of")) claimed.add(Long.valueOf(v.getString("offset_of")));
                    Long minor = v.has("amount_minor") ? Long.valueOf(v.getString("amount_minor")) : null;
                    earlier.add(new ShadowOffset.Movement(c.getLong(0), source, c.getLong(1), v.optString("direction", null), minor, v.optString("currency", null),
                            ShadowDiff.newRecords(c.getString(2)), v.has("merchant_key") || v.has("counterparty"), v.optString("merchant_key", null)));
                } catch (org.json.JSONException | NumberFormatException malformed) { /* skip an unreadable row */ }
            }
        }
        return ShadowOffset.match(current, refund, earlier, claimed);
    }

    /** The ledger row an observation was stored beside, or null. */
    static Long transactionOf(SQLiteDatabase sql, long observationId) {
        try (Cursor c = sql.rawQuery("SELECT transaction_id FROM shadow_observations WHERE id=? AND transaction_id IS NOT NULL", new String[]{Long.toString(observationId)})) {
            return c.moveToFirst() ? c.getLong(0) : null;
        }
    }

    /** The reasons of the latest observation stored beside this ledger row (for its review explanation), or null. */
    static String reasonsOf(SQLiteDatabase sql, long transactionId) {
        try (Cursor c = sql.rawQuery("SELECT reasons FROM shadow_observations WHERE transaction_id=? ORDER BY id DESC LIMIT 1", new String[]{Long.toString(transactionId)})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Earlier deliveries of the same origin that could matter: the same identity, or the same content near in time. */
    private static List<ShadowDedupe.Delivery> earlier(SQLiteDatabase sql, String origin, String identity, String content, long receivedAt) {
        List<ShadowDedupe.Delivery> found = new ArrayList<>();
        long window = ParsingPolicy.DUPLICATE_CANDIDATE_WINDOW_MS;
        try (Cursor c = sql.rawQuery("SELECT id, source, delivery_identity, received_at, content_hash FROM shadow_observations WHERE origin=? AND " +
                        "(delivery_identity=? OR (content_hash=? AND received_at BETWEEN ? AND ?))",
                new String[]{origin, identity == null ? "" : identity, content, Long.toString(receivedAt - window), Long.toString(receivedAt + window)})) {
            while (c.moveToNext()) found.add(new ShadowDedupe.Delivery(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4)));
        }
        return found;
    }

    private static void rememberUnunderstood(SQLiteDatabase sql, MessageNormalizer.Raw raw, String hash, String source, ParserShadow.Observation o) {
        String reasons = String.join(",", o.reasons);
        try (Cursor c = sql.rawQuery("SELECT 1 FROM ununderstood_messages WHERE message_hash=?", new String[]{hash})) {
            if (c.moveToFirst()) {
                sql.execSQL("UPDATE ununderstood_messages SET last_seen=?, seen_count=seen_count+1, reasons=?, policy=? WHERE message_hash=?",
                        new Object[]{o.observedAt, reasons, o.policy, hash});
                return;
            }
        }
        ContentValues row = new ContentValues();
        row.put("message_hash", hash);
        row.put("first_seen", o.observedAt);
        row.put("last_seen", o.observedAt);
        row.put("channel", o.channel);
        row.put("source", source);
        row.put("reasons", reasons);
        row.put("policy", o.policy);
        row.put("encrypted_body", EvidenceVault.encrypt(MessageNormalizer.normalize(raw).text));
        sql.insert("ununderstood_messages", null, row);
        sql.execSQL("DELETE FROM ununderstood_messages WHERE message_hash NOT IN " +
                "(SELECT message_hash FROM ununderstood_messages ORDER BY last_seen DESC LIMIT ?)", new Object[]{ParsingPolicy.UNUNDERSTOOD_MAX_ROWS});
    }

    /** "Delete all local data": every shadow row, ununderstood message and learned rule, and the in-memory copies. */
    static void clearAll(SQLiteDatabase sql) {
        for (String table : new String[]{"shadow_observations", "ununderstood_messages", "learning_rules"}) sql.delete(table, null, null);
        ParserShadow.learning().restoreShapes(new ArrayList<>());
    }




    /** A message the pipeline did not understand, its text opened on this device (null when unreadable). */
    static final class Ununderstood {
        long firstSeen, lastSeen;
        int count;
        String channel, source, reasons, text, hash;
    }

    /** Every kept message, newest first (at most UNUNDERSTOOD_MAX_ROWS are kept). */
    static List<Ununderstood> ununderstood(SQLiteDatabase sql) { return ununderstood(sql, ParsingPolicy.UNUNDERSTOOD_MAX_ROWS); }

    static List<Ununderstood> ununderstood(SQLiteDatabase sql, int limit) {
        List<Ununderstood> rows = new ArrayList<>();
        try (Cursor c = sql.rawQuery("SELECT first_seen, last_seen, seen_count, channel, source, reasons, encrypted_body, message_hash FROM ununderstood_messages " +
                "ORDER BY last_seen DESC LIMIT ?", new String[]{Integer.toString(limit)})) {
            while (c.moveToNext()) {
                Ununderstood u = new Ununderstood();
                u.firstSeen = c.getLong(0); u.lastSeen = c.getLong(1); u.count = c.getInt(2); u.channel = c.getString(3);
                u.source = c.getString(4); u.reasons = c.getString(5); u.text = EvidenceVault.decrypt(c.getString(6)); u.hash = c.getString(7);
                rows.add(u);
            }
        }
        return rows;
    }


    private static volatile boolean learningLoaded;

    /** Loads the stored shape memory into the process model once; later calls do nothing. */
    static void loadLearningOnce(SQLiteDatabase sql, LocalLearning learning) {
        if (learningLoaded) return;
        synchronized (ShadowStore.class) {
            if (learningLoaded) return;
            List<LocalLearning.ShapeRule> rules = new ArrayList<>();
            try (Cursor c = sql.rawQuery("SELECT rule_key, source, dismissed, confirmed, direction, conflicting FROM learning_rules " +
                    "WHERE kind='shape' ORDER BY updated_at, rowid", null)) {
                while (c.moveToNext()) rules.add(new LocalLearning.ShapeRule(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3),
                        c.getString(4), c.getInt(5) != 0));
            }
            learning.restoreShapes(rules);
            learningLoaded = true;
        }
    }

    /** Writes the whole shape memory back (bounded by LEARN_MAX_SHAPES); called after a dismissal or confirmation. */
    static void saveLearning(SQLiteDatabase sql, LocalLearning learning, long now) {
        List<LocalLearning.ShapeRule> rules = learning.exportShapes();
        sql.beginTransaction();
        try {
            sql.delete("learning_rules", "kind='shape'", null);
            long order = 0;
            for (LocalLearning.ShapeRule rule : rules) {
                ContentValues row = new ContentValues();
                row.put("rule_key", rule.key);
                row.put("kind", "shape");
                row.put("source", rule.source);
                row.put("dismissed", rule.dismissed);
                row.put("confirmed", rule.confirmed);
                row.put("direction", rule.direction);
                row.put("conflicting", rule.conflicting ? 1 : 0);
                row.put("updated_at", now - rules.size() + order++); // keeps least-recently-updated order on reload
                sql.insert("learning_rules", null, row);
            }
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }

    private ShadowStore() {}
}
