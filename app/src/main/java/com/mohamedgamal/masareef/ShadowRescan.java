package com.mohamedgamal.masareef;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Telephony;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shadow re-scan: runs the new pipeline over every old inbox SMS in a period, through the same inbox query and
 * legacy preview (SmsPreview.parse) as "choose the period of old messages", and records no operation. Legacy's side
 * is what that import would capture; its current ledger status (new / recorded / excluded) is noted in the reason.
 * Results go to shadow_observations with origin 'rescan' (the previous re-scan is replaced); a message both sides
 * skip is only counted, except an UNUNDERSTOOD one. Reading and parsing run on the caller's thread; each batch of
 * shadow rows is written on the single capture worker (CaptureQueue) in a short transaction, so live capture is never
 * held behind a re-scan. Nothing is sent anywhere.
 */
final class ShadowRescan {
    static final String PREFS = "shadow_rescan";

    interface Progress { void update(int scanned, int total); }

    static final class Result {
        volatile int scanned, stored;
        volatile long skippedUnstored;
        volatile boolean truncated, cancelled;
    }

    /** One message waiting to be written, with what legacy would have done. */
    private static final class Pending {
        MessageNormalizer.Raw raw;
        ParserShadow.Observation observation;
        String body, sender, legacyReason, legacyCurrency, legacyDirection, legacyMerchant;
        long captureAt;
        boolean legacyRecords;
        Long legacyAmount;
    }

    /** Blocks until every batch is written. Call off the main thread. */
    static Result run(Context app, long since, AtomicBoolean cancelled, Progress progress) {
        Result result = new Result();
        long until = System.currentTimeMillis();
        Set<String> sentKeys = ExpenseDb.smsSentKeys(app, since, until);
        submit(app, sql -> ShadowStore.clearOrigin(sql, "rescan"));
        List<Pending> batch = new ArrayList<>();
        try (ExpenseDb db = new ExpenseDb(app);
             Cursor c = app.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                     new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms._ID},
                     Telephony.Sms.DATE + ">=? AND " + Telephony.Sms.DATE + "<=?", new String[]{Long.toString(since), Long.toString(until)},
                     Telephony.Sms.DATE + " ASC")) {
            if (c == null) throw new IllegalStateException("SMS provider unavailable");
            List<LocalLearning.MerchantRule> rules = new ArrayList<>();
            for (ExpenseDb.MerchantRule rule : db.merchantRules()) rules.add(new LocalLearning.MerchantRule(rule.matchText, rule.category, rule.ignored));
            ParserShadow.learning().setMerchantRules(rules);
            ShadowStore.loadLearningOnce(db.getReadableDatabase(), ParserShadow.learning());
            int total = c.getCount();
            while (c.moveToNext()) {
                if (cancelled.get()) { result.cancelled = true; break; }
                result.scanned++;
                if (result.scanned % 25 == 0) progress.update(result.scanned, total);
                Pending p = observe(db, c.getString(0), c.getString(1), c.getLong(2), c.getLong(3), c.getString(4), sentKeys, result);
                if (p == null) continue;
                batch.add(p);
                if (batch.size() >= ParsingPolicy.RESCAN_BATCH) { write(app, batch); batch = new ArrayList<>(); }
            }
            if (!batch.isEmpty()) write(app, batch);
            progress.update(result.scanned, total);
        }
        // The last job runs after every batch (one worker, in order); its completion means the re-scan is stored.
        CountDownLatch stored = new CountDownLatch(1);
        CaptureQueue.submit(() -> {
            SharedPreferences meta = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            meta.edit().putInt("scanned", result.scanned).putLong("skipped_unstored", result.skippedUnstored).putInt("stored", result.stored)
                    .putBoolean("truncated", result.truncated).putBoolean("cancelled", result.cancelled).putLong("since", since)
                    .putLong("finished_at", System.currentTimeMillis()).putString("policy", ParsingPolicy.POLICY_VERSION).apply();
            stored.countDown();
        });
        try { stored.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        return result;
    }

    /** One inbox SMS: legacy preview beside the new pipeline; kept when either side records it or it was not understood. */
    private static Pending observe(ExpenseDb db, String sender, String body, long date, long sent, String inboxId, Set<String> sentKeys, Result result) {
        if (body == null) { result.skippedUnstored++; return null; }
        SmsPreview preview = SmsPreview.parse(sender, body, date, sent);
        Pending p = new Pending();
        p.sender = sender; p.body = body;
        p.captureAt = CaptureIdentity.smsTimestamp(date, sent);
        p.legacyRecords = preview != null && preview.isTransaction();
        String ledger = "NEW";
        if (preview != null) {
            preview.receiptKeySafe = preview.canUseReceiptKey(sentKeys);
            int status = db.smsCaptureStatus(sender, body, preview.receivedAt, preview.captureAt, preview.receiptKeySafe);
            ledger = status == 1 ? "RECORDED" : status == 2 ? "EXCLUDED" : "NEW";
        }
        p.legacyReason = (preview == null ? "NOT_CANDIDATE" : "PREVIEW_" + preview.kind.toUpperCase(java.util.Locale.ROOT)) + "/LEDGER_" + ledger;
        if (p.legacyRecords) {
            if ("transfer".equals(preview.kind)) {
                ExpenseParser.Transfer transfer = ExpenseParser.parseTransfer(body);
                p.legacyDirection = transfer == null ? "transfer" : transfer.incoming ? "in" : "out";
            } else p.legacyDirection = ShadowStore.legacyDirection(preview.kind);
            p.legacyAmount = preview.amountMinor; p.legacyCurrency = preview.currency; p.legacyMerchant = preview.title;
        }
        p.raw = MessageNormalizer.Raw.sms(sender, body).withIdentity(MessageNormalizer.Raw.smsIdentity(inboxId));
        p.observation = ParserShadow.observe(p.raw, p.legacyReason, p.legacyMerchant, p.captureAt, System.currentTimeMillis());
        if (p.observation == null || !p.legacyRecords && !ShadowDiff.newRecords(p.observation.decision) && !"UNUNDERSTOOD".equals(p.observation.decision)) {
            result.skippedUnstored++; return null;
        }
        if (result.stored >= ParsingPolicy.RESCAN_MAX_ROWS) { result.truncated = true; result.skippedUnstored++; return null; }
        result.stored++;
        return p;
    }

    private static void write(Context app, List<Pending> batch) {
        submit(app, sql -> {
            for (Pending p : batch)
                ShadowStore.record(sql, "rescan", p.raw, p.body, p.sender, p.captureAt, null, p.legacyReason, p.legacyRecords ? "RECORDED" : "NOT_RECORDED",
                        p.legacyAmount, p.legacyCurrency, p.legacyDirection, p.legacyMerchant, p.observation);
        });
    }

    private interface Work { void run(SQLiteDatabase sql); }

    /** A short shadow-only write transaction on the capture worker; a failure loses that batch, never a capture. */
    private static void submit(Context app, Work work) {
        CaptureQueue.submit(() -> {
            try (ExpenseDb db = new ExpenseDb(app)) {
                SQLiteDatabase sql = db.getWritableDatabase();
                sql.beginTransaction();
                try { work.run(sql); sql.setTransactionSuccessful(); }
                finally { sql.endTransaction(); }
            } catch (RuntimeException failed) {
                android.util.Log.w("FollowMoney", "Shadow re-scan batch skipped: " + failed.getClass().getSimpleName());
            }
        });
    }

    private ShadowRescan() {}
}
