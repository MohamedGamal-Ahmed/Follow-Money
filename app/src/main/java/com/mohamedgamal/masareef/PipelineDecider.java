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
            return new Decided(PipelineDecision.record(review, amount, v.get("currency"), direction, DecisionNames.merchant(v, o.movementKind, request.label),
                    DecisionNames.category(v, o.movementKind, direction, request.label), PipelineDecision.sourceLast4(v), v.get("counterparty"), offsetOf, offset && offsetOf == null), o);
        } catch (RuntimeException | LinkageError failed) {
            return new Failed("EXCEPTION:" + failed.getClass().getSimpleName()); // the legacy parser decides this message
        }
    }

    private PipelineDecider() {}
}
