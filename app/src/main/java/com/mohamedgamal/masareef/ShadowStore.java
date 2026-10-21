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

    /** What the legacy path did with the same message, as the shadow store records it. */
    static String legacyDecision(CaptureResult result) {
        if (result.transaction == null) return "NOT_RECORDED";
        return result.transaction.needsReview ? "RECORDED_REVIEW" : "RECORDED";
    }

    /** Legacy kind expense/income → out/in; other kinds (transfer, fee) keep their name. */
    static String legacyDirection(String kind) {
        if ("expense".equals(kind)) return "out";
        if ("income".equals(kind)) return "in";
        return kind;
    }

    /** Stores one live observation beside the legacy capture result; result is null when legacy never captured it. */
    static void record(SQLiteDatabase sql, MessageNormalizer.Raw raw, String legacyBody, String source, long receivedAt,
                       CaptureResult result, String legacyReason, ParserShadow.Observation o) {
        ExpenseDb.Transaction t = result == null ? null : result.transaction;
        record(sql, "live", raw, legacyBody, source, receivedAt, result == null ? null : result.transactionId,
                legacyReason, result == null ? "NOT_RECORDED" : legacyDecision(result), t == null ? null : t.amountMinor, t == null ? null : t.currency,
                t == null ? null : legacyDirection(t.kind), t == null ? null : t.merchant, o);
    }

    static void record(SQLiteDatabase sql, String origin, MessageNormalizer.Raw raw, String legacyBody, String source, long receivedAt,
                       Long transactionId, String legacyReason, String legacyDecision, Long legacyAmount, String legacyCurrency,
                       String legacyDirection, String legacyMerchant, ParserShadow.Observation o) {
        if (o == null) return;
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
        row.put("reasons", String.join(",", o.reasons));
        row.put("policy", o.policy);
        row.put("encrypted_body", EvidenceVault.encrypt(sealed.toString()));
        sql.insert("shadow_observations", null, row);
        sql.execSQL("DELETE FROM shadow_observations WHERE id <= (SELECT MAX(id) FROM shadow_observations) - ?",
                new Object[]{ParsingPolicy.SHADOW_STORE_MAX_ROWS});
        if ("UNUNDERSTOOD".equals(o.decision) && raw != null) rememberUnunderstood(sql, raw, content, source, o);
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
