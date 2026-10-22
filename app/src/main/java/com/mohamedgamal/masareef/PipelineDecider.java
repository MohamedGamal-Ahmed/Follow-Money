package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/**
 * Runs the new pipeline for one message and turns its decision into a PipelineDecision for the ledger. Owner rules:
 * AUTO and REVIEW record (REVIEW with the review flag and its reason); IGNORE, UNUNDERSTOOD and INFO_ONLY record
 * nothing. A return (refund, reversal, a same-amount return of a charge) is an expense offset linked to the charge it
 * returns, never plain income; one with no charge to link waits for the user. When the pipeline cannot decide (a
 * failed stage, an exception, or a recording decision with no amount, currency or direction) the result is Failed
 * with its reason, and the legacy parser decides that message.
 */
final class PipelineDecider {
    /** Decided or Failed. */
    abstract static class Result {
        private Result() {}
    }

    /** The decision and the observation it came from (stored beside the ledger row afterwards). */
    static final class Decided extends Result {
        final PipelineDecision decision;
        final ParserShadow.Observation observation;
        Decided(PipelineDecision decision, ParserShadow.Observation observation) { this.decision = decision; this.observation = observation; }
    }

    /** reason: STAGE_FAILED:<stage>, MISSING_AMOUNT, MISSING_CURRENCY, MISSING_DIRECTION or EXCEPTION:<SimpleName>. */
    static final class Failed extends Result {
        final String reason;
        Failed(String reason) { this.reason = reason; }
    }

    /**
     * The user's merchant rules and local learning, loaded once: per message for live capture, once per batch for the
     * import. origin is where this message's observation is stored (live or import); a return looks for its charge there.
     */
    static final class Context {
        final ExpenseDb db;
        final String origin;
        private boolean loaded;
        Context(ExpenseDb db, String origin) { this.db = db; this.origin = origin; }

        private void load() {
            if (loaded) return;
            java.util.List<LocalLearning.MerchantRule> rules = new java.util.ArrayList<>();
            for (ExpenseDb.MerchantRule rule : db.merchantRules()) rules.add(new LocalLearning.MerchantRule(rule.matchText, rule.category, rule.ignored));
            ParserShadow.learning().setMerchantRules(rules);
            ShadowStore.loadLearningOnce(db.getWritableDatabase(), ParserShadow.learning());
            loaded = true;
        }
    }

    static Context context(ExpenseDb db, String origin) { return new Context(db, origin); }

    private static volatile String failNext;

    /** Test builds only: the next message whose text contains this fails inside the pipeline (EXCEPTION:IllegalStateException). */
    static void failNextFor(String containing) { if (BuildConfig.DEBUG) failNext = containing; }

    static Result decide(Context ctx, CaptureRequest request) {
        try {
            String fail = failNext;
            if (BuildConfig.DEBUG && fail != null && request.body != null && request.body.contains(fail)) {
                failNext = null;
                throw new IllegalStateException("failNextFor");
            }
            ctx.load();
            SQLiteDatabase sql = ctx.db.getWritableDatabase();
            ParserShadow.Observation o = ParserShadow.observe(request.raw, "NEW_PIPELINE_DECIDES", null, request.captureAt, System.currentTimeMillis());
            if (o == null) return new Failed("STAGE_FAILED:NO_INPUT");
            if (o.status.startsWith("STAGE_FAILED")) return new Failed(o.status);
            if (o.decision == null) return new Failed("STAGE_FAILED:NO_DECISION");
            if (!ShadowDiff.newRecords(o.decision)) return new Decided(PipelineDecision.skip(o.decision, o.reasons.contains("LEARNED_IGNORED_MERCHANT")), o);
            java.util.Map<String, String> v = o.values;
            Long amount;
            try { amount = Long.valueOf(v.get("amount_minor")); } catch (RuntimeException missing) { return new Failed("MISSING_AMOUNT"); }
            if (amount <= 0) return new Failed("MISSING_AMOUNT");
            if (v.get("currency") == null) return new Failed("MISSING_CURRENCY");
            String direction = v.get("direction");
            if (direction == null) return new Failed("MISSING_DIRECTION");
            boolean offset = "expense_offset".equals(v.get("accounting_effect"));
            Long offsetOf = null;
            if ("in".equals(direction)) {
                long observationId = ShadowStore.offsetOf(sql, ctx.origin, request.label, request.captureAt, o);
                offsetOf = observationId < 0 ? null : ShadowStore.transactionOf(sql, observationId);
                if (offsetOf != null) offset = true;
            }
            boolean review = "REVIEW".equals(o.decision);
            Long balance;
            try { balance = v.get("balance_minor") == null ? null : Long.valueOf(v.get("balance_minor")); } catch (NumberFormatException unreadable) { balance = null; }
            return new Decided(PipelineDecision.record(review, amount, v.get("currency"), direction, DecisionNames.merchant(v, o.movementKind, request.label),
                    DecisionNames.category(v, o.movementKind, direction, request.label), PipelineDecision.sourceLast4(v), v.get("counterparty"), offsetOf, offset && offsetOf == null)
                    .withBalance(balance, v.get("balance_currency")), o);
        } catch (RuntimeException | LinkageError failed) {
            return new Failed("EXCEPTION:" + failed.getClass().getSimpleName()); // the legacy parser decides this message
        }
    }

    /**
     * One inbox message as the import screen lists it, or null when it is not one to import, decided once with the
     * batch's context (spec m3/m4). A movement the pipeline records is listed from its decision. A message it records
     * nothing for is listed only when the legacy reading finds non-ledger work in it (a balance, a commitment update, a
     * cancellation to review); the decision still goes with it, so it never becomes a ledger row. When the pipeline
     * fails, the legacy reading is listed and the legacy parser writes it.
     */
    static SmsPreview importRow(Context ctx, String sender, String body, long receivedAt, long sentAt) {
        if (body == null) return null;
        Result decided = decide(ctx, CaptureRequest.inboxSms(sender, body, receivedAt, sentAt, false));
        SmsPreview legacy = SmsPreview.parse(sender, body, receivedAt, sentAt);
        if (decided instanceof Failed) {
            if (legacy != null) legacy.failure = ((Failed) decided).reason;
            return legacy;
        }
        Decided d = (Decided) decided;
        if (d.decision.records) return SmsPreview.fromDecision(sender, body, receivedAt, sentAt, d.decision, d.observation, legacy);
        if (legacy == null || legacy.isTransaction()) return null;
        legacy.decision = d.decision; legacy.observation = d.observation;
        return legacy;
    }

    /** The reading an import row is written with: the one it was listed with, or (a row built elsewhere) decided now. */
    static Result forImport(ExpenseDb db, SmsPreview row) {
        if (row.failure != null) return new Failed(row.failure);
        if (row.decision != null && row.observation != null) return new Decided(row.decision, row.observation);
        return decide(context(db, "import"), CaptureRequest.sms(row.sender, row.body, row.captureAt));
    }

    /**
     * The decision an import row is written with, or null when the legacy parser writes it. A return listed before its
     * charge was written finds that charge now, among the batch's own rows (origin import); nothing else is re-decided.
     */
    static PipelineDecision importDecision(ExpenseDb db, SmsPreview row, Result decided) {
        if (!(decided instanceof Decided)) return null;
        Decided d = (Decided) decided;
        if (!d.decision.records || !"in".equals(d.decision.direction) || d.decision.offsetOfTransactionId != null) return d.decision;
        try {
            SQLiteDatabase sql = db.getWritableDatabase();
            long observationId = ShadowStore.offsetOf(sql, "import", row.sender, row.captureAt, d.observation);
            Long charge = observationId < 0 ? null : ShadowStore.transactionOf(sql, observationId);
            return charge == null ? d.decision : d.decision.withOffset(charge, "REVIEW".equals(d.observation.decision));
        } catch (RuntimeException | LinkageError unreadable) {
            return d.decision;
        }
    }

    /** Stores the import row's observation (origin import) or its failure note beside what was written. */
    static void storeImport(ExpenseDb db, SmsPreview row, Result decided, CaptureResult.Write write) {
        CaptureTransaction.store(db, "import", CaptureRequest.sms(row.sender, row.body, row.captureAt), decided,
                write.reason, write.rowId >= 0 ? write.rowId : null);
    }

    private PipelineDecider() {}
}
