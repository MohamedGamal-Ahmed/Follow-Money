package com.mohamedgamal.masareef;

import android.database.sqlite.SQLiteDatabase;

/**
 * Step 7: runs the new pipeline for one live message and turns its decision into a PipelineDecision for the ledger.
 * Owner rules: AUTO and REVIEW record (REVIEW with the review flag and its reason); IGNORE, UNUNDERSTOOD and INFO_ONLY
 * record nothing. A return (refund, reversal, a same-amount return of a charge) is an expense offset linked to the
 * charge it returns, never plain income; one with no charge to link waits for the user. A pipeline failure returns null,
 * and the legacy parser decides that message.
 */
final class PipelineDecider {
    /** The decision and the observation it came from (stored beside the ledger row afterwards). */
    static final class Decided {
        final PipelineDecision decision;
        final ParserShadow.Observation observation;
        Decided(PipelineDecision decision, ParserShadow.Observation observation) { this.decision = decision; this.observation = observation; }
    }

    static Decided decide(ExpenseDb db, CaptureRequest request) {
        try {
            java.util.List<LocalLearning.MerchantRule> rules = new java.util.ArrayList<>();
            for (ExpenseDb.MerchantRule rule : db.merchantRules()) rules.add(new LocalLearning.MerchantRule(rule.matchText, rule.category, rule.ignored));
            ParserShadow.learning().setMerchantRules(rules);
            SQLiteDatabase sql = db.getWritableDatabase();
            ShadowStore.loadLearningOnce(sql, ParserShadow.learning());
            ParserShadow.Observation o = ParserShadow.observe(request.raw, "NEW_PIPELINE_DECIDES", null, request.captureAt, System.currentTimeMillis());
            if (o == null || o.status.startsWith("STAGE_FAILED") || o.decision == null) return null;
            if (!ShadowDiff.newRecords(o.decision)) return new Decided(PipelineDecision.skip(o.decision), o);
            java.util.Map<String, String> v = o.values;
            Long amount;
            try { amount = Long.valueOf(v.get("amount_minor")); } catch (RuntimeException missing) { return null; }
            String direction = v.get("direction");
            if (amount <= 0 || v.get("currency") == null || direction == null) return null;
            boolean offset = "expense_offset".equals(v.get("accounting_effect"));
            Long offsetOf = null;
            if ("in".equals(direction)) {
                long observationId = ShadowStore.offsetOf(sql, "live", request.label, request.captureAt, o);
                offsetOf = observationId < 0 ? null : ShadowStore.transactionOf(sql, observationId);
                if (offsetOf != null) offset = true;
            }
            boolean review = "REVIEW".equals(o.decision);
            return new Decided(PipelineDecision.record(review, amount, v.get("currency"), direction, merchant(v, o.movementKind, request.label),
                    category(v, o.movementKind, direction), PipelineDecision.sourceLast4(v), v.get("counterparty"), offsetOf, offset && offsetOf == null), o);
        } catch (RuntimeException | LinkageError failed) {
            return null; // the legacy parser decides this message
        }
    }

    /** The name shown in the ledger: the merchant as written, else the other party, else what kind of movement it is. */
    static String merchant(java.util.Map<String, String> v, String kind, String label) {
        if (v.get("merchant") != null) return v.get("merchant");
        if (v.get("counterparty") != null) return v.get("counterparty");
        if (kind != null) switch (kind) {
            case "cash_withdrawal": return "سحب نقدي";
            case "instant_transfer_in": case "instant_transfer_out": return "تحويل لحظي";
            case "salary": return "راتب";
            case "service_topup": return "شحن رصيد";
            case "installment_payment": return "قسط";
            default: break;
        }
        return label;
    }

    /** The category the legacy screens already use for this kind of movement; the user's merchant rule wins. */
    static String category(java.util.Map<String, String> v, String kind, String direction) {
        if (v.get("learned_category") != null) return v.get("learned_category");
        if ("in".equals(direction)) return "دخل"; // a return is an income linked to its expense (reimbursement), not new income
        if (kind != null) switch (kind) {
            case "cash_withdrawal": return MoneyFlow.CASH_WITHDRAWAL_CATEGORY;
            case "instant_transfer_out": case "transfer_out": case "wallet_transfer_out": return MoneyFlow.OPEN_TRANSFER_CATEGORY;
            case "bill_payment": case "service_topup": return "فواتير";
            case "installment_payment": return "أقساط";
            default: break;
        }
        String known = MerchantClassifier.category(v.get("merchant") != null ? v.get("merchant") : "");
        return known != null ? known : MoneyFlow.UNCATEGORIZED;
    }

    private PipelineDecider() {}
}
