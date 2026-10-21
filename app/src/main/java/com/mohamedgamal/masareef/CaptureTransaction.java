package com.mohamedgamal.masareef;

/**
 * Orders live capture, bounded repair, and the resulting snapshot. Borrows the caller's connection:
 * ExpenseDb still owns storage transactions; this use case neither closes it nor adds a transaction.
 * Historical import retains its separate batch/confirmation policy.
 */
final class CaptureTransaction {
    private final ExpenseDb db;

    CaptureTransaction(ExpenseDb db) { this.db = db; }

    CaptureResult execute(CaptureRequest request) {
        boolean pending = db.getWritableDatabase().inTransaction();
        long before = db.changes();
        db.rememberCapture(-1, null);
        // Step 7: the new pipeline decides messages that arrive after it was switched on; a pipeline failure falls back to legacy.
        PipelineDecider.Decided decided = DecisionSwitch.decides(db.appContext(), request.captureAt) ? PipelineDecider.decide(db, request) : null;
        CaptureResult.Write write = db.addAutomaticCapture(request, decided == null ? null : decided.decision);
        long capturedId = write.rowId;
        finish(request, write);
        CaptureResult result = new CaptureResult(write, capturedId,
                write.rowId < 0 ? null : db.transactionById(write.rowId), pending, before, db.changes());
        if (decided != null) recordDecided(db, request, result, decided);
        else shadow(db, request.raw, request.label, request.body, request.captureAt, result, String.valueOf(result.reason));
        return result;
    }

    /**
     * The new pipeline decided: its observation is stored beside the ledger row, with the legacy parser's opinion
     * (computed, never written) for LEGACY_OPINION_DAYS after the first activation, so the debug screen keeps showing
     * the differences as they happen.
     */
    private static void recordDecided(ExpenseDb db, CaptureRequest request, CaptureResult result, PipelineDecider.Decided decided) {
        try {
            String legacyDecision = "LEGACY_OFF", legacyReason = "LEGACY_OPINION_ENDED", legacyCurrency = null, legacyDirection = null, legacyMerchant = null;
            Long legacyAmount = null;
            if (request.body != null && DecisionSwitch.legacyOpinion(db.appContext(), request.captureAt)) {
                SmsPreview preview = SmsPreview.parse(request.label, request.body, request.captureAt, request.captureAt);
                boolean would = preview != null && preview.isTransaction();
                legacyDecision = would ? "WOULD_RECORD" : "WOULD_SKIP";
                legacyReason = "LEGACY_OPINION/" + (preview == null ? "NOT_CANDIDATE" : "PREVIEW_" + preview.kind.toUpperCase(java.util.Locale.ROOT));
                if (would) {
                    legacyAmount = preview.amountMinor; legacyCurrency = preview.currency; legacyMerchant = preview.title;
                    if ("transfer".equals(preview.kind)) {
                        ExpenseParser.Transfer t = ExpenseParser.parseTransfer(request.body);
                        legacyDirection = t == null ? "transfer" : t.incoming ? "in" : "out";
                    } else legacyDirection = ShadowStore.legacyDirection(preview.kind);
                }
            }
            ShadowStore.record(db.getWritableDatabase(), "live", request.raw, request.body, request.label, request.captureAt,
                    result.transactionId, legacyReason, legacyDecision, legacyAmount, legacyCurrency, legacyDirection, legacyMerchant, decided.observation);
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostics only; the decision is already recorded.
        }
    }

    /**
     * A notification the legacy path never captures (a group summary, or text only in extras it does not read) is
     * still observed in shadow. Nothing is recorded; legacyReason says why legacy skipped it.
     */
    static void observeWithoutCapture(ExpenseDb db, MessageNormalizer.Raw raw, String label, long receivedAt, String legacyReason) {
        shadow(db, raw, label, null, receivedAt, null, legacyReason);
    }

    /** Runs after the legacy decision is final; a shadow failure must never change or fail the capture. */
    private static void shadow(ExpenseDb db, MessageNormalizer.Raw raw, String label, String legacyBody, long receivedAt,
                               CaptureResult result, String legacyReason) {
        try {
            // The user's merchant rules, read only, after the legacy decision is final.
            java.util.List<LocalLearning.MerchantRule> rules = new java.util.ArrayList<>();
            for (ExpenseDb.MerchantRule rule : db.merchantRules()) rules.add(new LocalLearning.MerchantRule(rule.matchText, rule.category, rule.ignored));
            ParserShadow.learning().setMerchantRules(rules);
            android.database.sqlite.SQLiteDatabase sql = db.getWritableDatabase();
            ShadowStore.loadLearningOnce(sql, ParserShadow.learning());
            ParserShadow.Observation observation = ParserShadow.observe(raw, legacyReason,
                    result == null || result.transaction == null ? null : result.transaction.merchant, receivedAt, System.currentTimeMillis());
            // Stored beside the legacy record; these tables are outside LedgerRevision, so the ledger sees no change.
            ShadowStore.record(sql, raw, legacyBody, label, receivedAt, result, legacyReason, observation);
        } catch (RuntimeException | LinkageError ignored) {
            // Diagnostics only.
        }
    }

    /** Boolean compatibility path; acceptance keeps its historical meaning. */
    boolean apply(CaptureRequest request) {
        return execute(request).accepted;
    }

    private void finish(CaptureRequest request, CaptureResult.Write write) {
        // A second own-transfer leg or the bank copy of an app notification may have just arrived.
        if (write.added) {
            ExpenseDb.RepairReport repair = db.repairAround(request.captureAt);
            write.rowId = repair.follow(write.rowId);
            write.repairFailed = repair.failed;
        }
        db.rememberCapture(write.inserted ? write.rowId : -1, write.event);
    }
}
