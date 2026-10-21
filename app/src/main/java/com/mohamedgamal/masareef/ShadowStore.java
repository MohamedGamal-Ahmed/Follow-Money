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
        // Owner decision: a return of the same charge minutes later is an expense offset linked to it, never income.
        String reasons = String.join(",", o.reasons);
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
        // Live rows are bounded here; a re-scan bounds itself (RESCAN_MAX_ROWS) and replaces the previous one.
        if ("live".equals(origin)) sql.execSQL("DELETE FROM shadow_observations WHERE origin='live' AND id < " +
                "(SELECT id FROM shadow_observations WHERE origin='live' ORDER BY id DESC LIMIT 1 OFFSET ?)", new Object[]{ParsingPolicy.SHADOW_STORE_MAX_ROWS - 1});
        if ("UNUNDERSTOOD".equals(o.decision) && raw != null) rememberUnunderstood(sql, raw, content, source, o);
    }

    /** The earlier recorded charge from the same source this return offsets (ShadowOffset), or -1. Opens only rows in the window. */
    private static long offsetOf(SQLiteDatabase sql, String origin, String source, long receivedAt, ParserShadow.Observation o) {
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
        ParserShadow.clear();
    }

    /** Removes every stored row of one origin (the previous re-scan before a new one). */
    static void clearOrigin(SQLiteDatabase sql, String origin) {
        sql.delete("shadow_observations", "origin=?", new String[]{origin});
    }

    /** One stored observation, its sealed values opened on this device. */
    static final class Stored {
        long id, observedAt, receivedAt;
        String channel, source, origin, legacyReason, legacyDecision, status, type, decision, scoredDecision, movementKind, reasons, dedupe, dedupeReason;
        int score;
        /** True when the sealed values exist but this device can no longer open them. */
        boolean unreadable;
        final java.util.Map<String, String> values = new java.util.LinkedHashMap<>(), legacy = new java.util.LinkedHashMap<>();

        Long amount(java.util.Map<String, String> from) {
            try { return from.containsKey("amount_minor") ? Long.valueOf(from.get("amount_minor")) : null; }
            catch (NumberFormatException e) { return null; }
        }

        ShadowDiff.Result diff() {
            return ShadowDiff.compare(ShadowDiff.legacyRecorded(legacyDecision), amount(legacy), legacy.get("direction"), decision, amount(values), values.get("direction"));
        }
    }

    /** Newest first, at most limit rows of one origin ("live" or "rescan"). */
    static List<Stored> recent(SQLiteDatabase sql, String origin, int limit) {
        List<Stored> rows = new ArrayList<>();
        try (Cursor c = sql.rawQuery("SELECT id, observed_at, received_at, channel, source, origin, legacy_reason, legacy_decision, status, type, " +
                "decision, scored_decision, score, movement_kind, reasons, dedupe, dedupe_reason, encrypted_body FROM shadow_observations " +
                "WHERE origin=? ORDER BY received_at DESC, id DESC LIMIT ?", new String[]{origin, Integer.toString(limit)})) {
            while (c.moveToNext()) {
                Stored s = new Stored();
                s.id = c.getLong(0); s.observedAt = c.getLong(1); s.receivedAt = c.getLong(2); s.channel = c.getString(3); s.source = c.getString(4);
                s.origin = c.getString(5); s.legacyReason = c.getString(6); s.legacyDecision = c.getString(7); s.status = c.getString(8);
                s.type = c.getString(9); s.decision = c.getString(10); s.scoredDecision = c.getString(11); s.score = c.getInt(12);
                s.movementKind = c.getString(13); s.reasons = c.getString(14); s.dedupe = c.getString(15); s.dedupeReason = c.getString(16);
                EvidenceVault.Read body = EvidenceVault.read(c.getString(17));
                s.unreadable = body.failed;
                if (body.body != null) try {
                    org.json.JSONObject json = new org.json.JSONObject(body.body);
                    for (java.util.Iterator<String> k = json.keys(); k.hasNext(); ) {
                        String key = k.next();
                        if (!key.equals("legacy")) s.values.put(key, json.getString(key));
                    }
                    org.json.JSONObject legacy = json.optJSONObject("legacy");
                    if (legacy != null) for (java.util.Iterator<String> k = legacy.keys(); k.hasNext(); ) {
                        String key = k.next();
                        s.legacy.put(key, legacy.getString(key));
                    }
                } catch (org.json.JSONException malformed) { s.unreadable = true; }
                rows.add(s);
            }
        }
        return rows;
    }

    /** A message the pipeline did not understand, its text opened on this device (null when unreadable). */
    static final class Ununderstood {
        long firstSeen, lastSeen;
        int count;
        String channel, source, reasons, text;
    }

    static List<Ununderstood> ununderstood(SQLiteDatabase sql, int limit) {
        List<Ununderstood> rows = new ArrayList<>();
        try (Cursor c = sql.rawQuery("SELECT first_seen, last_seen, seen_count, channel, source, reasons, encrypted_body FROM ununderstood_messages " +
                "ORDER BY last_seen DESC LIMIT ?", new String[]{Integer.toString(limit)})) {
            while (c.moveToNext()) {
                Ununderstood u = new Ununderstood();
                u.firstSeen = c.getLong(0); u.lastSeen = c.getLong(1); u.count = c.getInt(2); u.channel = c.getString(3);
                u.source = c.getString(4); u.reasons = c.getString(5); u.text = EvidenceVault.decrypt(c.getString(6));
                rows.add(u);
            }
        }
        return rows;
    }

    /** Row counts: observations of the origin, ununderstood messages, learned shapes. */
    static long[] counts(SQLiteDatabase sql, String origin) {
        long[] out = new long[3];
        String[] queries = {"SELECT COUNT(*) FROM shadow_observations WHERE origin=?", "SELECT COUNT(*) FROM ununderstood_messages",
                "SELECT COUNT(*) FROM learning_rules"};
        for (int i = 0; i < queries.length; i++)
            try (Cursor c = sql.rawQuery(queries[i], i == 0 ? new String[]{origin} : null)) { if (c.moveToFirst()) out[i] = c.getLong(0); }
        return out;
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
