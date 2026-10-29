package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.4.5: after an update that improved reading, the messages the app kept on «رسائل من بنوكك ما اتسجلتش» are read again
 * with the current rules; those the app would now record come back as a suggestion the user reviews (never written to
 * the ledger here). Pure: no Android, no storage. The source of truth is the kept row (its sealed text, its source).
 */
final class MissedReevaluation {
    /** SharedPreferences value (app_state) that means «never ran». */
    static final int NEVER = -1;

    /** One kept message as the missed-messages table stores it, its text already opened (null when unreadable). */
    static final class Row {
        final String hash, channel, source, sourceKey, text;
        final long firstSeen, recoveredAt;
        Row(String hash, String channel, String source, String sourceKey, String text, long firstSeen, long recoveredAt) {
            this.hash = hash; this.channel = channel; this.source = source; this.sourceKey = sourceKey; this.text = text;
            this.firstSeen = firstSeen; this.recoveredAt = recoveredAt;
        }
    }

    /** What the current rules read from a kept message. */
    static final class Reading {
        final long amountMinor;
        final String currency, direction, merchant;
        Reading(long amountMinor, String currency, String direction, String merchant) {
            this.amountMinor = amountMinor; this.currency = currency; this.direction = direction; this.merchant = merchant;
        }
    }

    /** A row that is now understood, with the source key it must be captured under. */
    static final class Recovered {
        final String hash, sourceKey;
        final Reading reading;
        Recovered(String hash, String sourceKey, Reading reading) { this.hash = hash; this.sourceKey = sourceKey; this.reading = reading; }
    }

    /** Runs once per app version: when the stored number differs from the running one (also the first run). */
    static boolean due(int stored, int current) { return stored != current; }

    /**
     * The key a kept message is captured under: the package of a notification, the sender of an SMS. Rows stored by an
     * older version have none: an SMS row's source is its sender; a notification row's source is the app's label, which
     * names a package only when exactly one selected app has that label (labels maps selected package to its label).
     */
    static String sourceKey(Row row, Map<String, String> labels) {
        if (row.sourceKey != null && !row.sourceKey.isEmpty()) return row.sourceKey;
        if (!"NOTIFICATION".equals(row.channel)) return row.source == null || row.source.isEmpty() ? null : row.source;
        if (row.source == null) return null;
        String found = null;
        for (Map.Entry<String, String> app : labels.entrySet()) {
            if (!row.source.equals(app.getValue())) continue;
            if (found != null) return null; // two selected apps with one label: not decidable
            found = app.getKey();
        }
        return found;
    }

    /** The shadow-pipeline input for a kept message: its normalized text under its source key. */
    static MessageNormalizer.Raw raw(Row row, String sourceKey) {
        return "NOTIFICATION".equals(row.channel)
                ? MessageNormalizer.Raw.notification(sourceKey, 0, null, row.text, null, null, null, null)
                : MessageNormalizer.Raw.sms(sourceKey, row.text);
    }

    /** The capture request that records this message through the live path (dedupe, exclusions and settlement apply there). */
    static CaptureRequest request(Row row, String sourceKey) {
        return "NOTIFICATION".equals(row.channel)
                ? CaptureRequest.notification(sourceKey, row.source == null ? sourceKey : row.source, row.text, row.firstSeen, raw(row, sourceKey))
                : CaptureRequest.sms(sourceKey, row.text, row.firstSeen);
    }

    /**
     * Reads one kept message again. Null when it is not a candidate (no readable text, already recovered, no known
     * source, a shape the user dismissed) or when the current rules still do not record it.
     */
    static Recovered reevaluate(Row row, Map<String, String> labels, LocalLearning learning, long now) {
        if (row.text == null || row.recoveredAt > 0) return null;
        String key = sourceKey(row, labels);
        if (key == null) return null;
        if (learning.dismissedShape(row.source, row.text)) return null;
        Reading reading = read(row, key, now);
        return reading == null ? null : new Recovered(row.hash, key, reading);
    }

    /** What the current rules read from this message when they would record it, else null. */
    static Reading read(Row row, String key, long now) {
        if (row.text == null) return null;
        ParserShadow.Observation o = ParserShadow.observe(raw(row, key), "MISSED_REEVAL", null, row.firstSeen, now);
        if (o == null || !"EXTRACTED".equals(o.status) || !ShadowDiff.newRecords(o.decision)) return null;
        Long amount;
        try { amount = Long.valueOf(o.values.get("amount_minor")); } catch (RuntimeException missing) { return null; }
        // The same completeness the live path asks before it records (PipelineDecider.Failed otherwise).
        if (amount <= 0 || o.values.get("currency") == null || o.values.get("direction") == null) return null;
        String merchant = o.values.get("merchant");
        if (merchant == null) merchant = o.values.get("counterparty");
        return new Reading(amount, o.values.get("currency"), o.values.get("direction"), merchant);
    }

    /** Every kept message the current rules now record. Rows are never changed here; the caller stores the outcome. */
    static List<Recovered> reevaluate(List<Row> rows, Map<String, String> labels, LocalLearning learning, long now) {
        List<Recovered> found = new ArrayList<>();
        for (Row row : rows) {
            Recovered recovered = reevaluate(row, labels, learning, now);
            if (recovered != null) found.add(recovered);
        }
        return found;
    }

    /**
     * Whether a ledger row of the same amount and direction sits near the message's time: the user may have recorded it
     * by hand or the bank's SMS may have. A hint on the suggestion only; the suggestion is never hidden by it.
     */
    static boolean possiblyRecorded(long messageAt, long ledgerAt, boolean ledgerDateOnly, java.time.ZoneId zone) {
        if (Math.abs(messageAt - ledgerAt) <= ParsingPolicy.DUPLICATE_CANDIDATE_WINDOW_MS) return true;
        // A hand-entered row may carry the date only.
        return ledgerDateOnly && java.time.Instant.ofEpochMilli(messageAt).atZone(zone).toLocalDate()
                .equals(java.time.Instant.ofEpochMilli(ledgerAt).atZone(zone).toLocalDate());
    }

    /** The result of registering a suggestion through the live capture path. */
    enum Outcome { RECORDED, ALREADY_RECORDED, EXCLUDED, NOT_RECORDED }

    /** reason is CaptureResult.Reason.name(): EXISTING_MATCH means the ledger already holds it; EXCLUDED, the user removed it before. */
    static Outcome outcome(boolean accepted, String reason) {
        if (accepted) return Outcome.RECORDED;
        if ("EXISTING_MATCH".equals(reason)) return Outcome.ALREADY_RECORDED;
        if ("EXCLUDED".equals(reason)) return Outcome.EXCLUDED;
        return Outcome.NOT_RECORDED;
    }

    /** The suggestion row leaves the list once the ledger holds the message or the user removed it earlier. */
    static boolean removesSuggestion(Outcome outcome) { return outcome != Outcome.NOT_RECORDED; }

    private MissedReevaluation() {}
}
