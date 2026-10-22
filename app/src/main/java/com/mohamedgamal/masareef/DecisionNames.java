package com.mohamedgamal.masareef;

/**
 * The ledger name and category of a new-pipeline decision, from the observation's values (no Android, no storage).
 * Names and categories come from the same sources, in the same order, as the screens have always shown them:
 * ExpenseParser.merchantName for the spelling and ExpenseParser.category for the category.
 */
final class DecisionNames {
    /** The name shown in the ledger: the merchant as written, else the other party, else what kind of movement it is. */
    static String merchant(java.util.Map<String, String> v, String kind, String label) {
        if (v.get("merchant") != null) return ExpenseParser.merchantName(v.get("merchant"));
        if (v.get("counterparty") != null) return v.get("counterparty");
        if (v.get("description") != null) return v.get("description"); // an HSBC statement line describes itself
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

    /**
     * The category the legacy screens already use for this kind of movement. A transfer keeps the transfer category; for
     * anything else the user's merchant rule wins, then the kind, then the shown name's category.
     */
    static String category(java.util.Map<String, String> v, String kind, String direction, String label) {
        if ("in".equals(direction)) return "دخل"; // a return is an income linked to its expense (reimbursement), not new income
        if (transfer(kind)) return MoneyFlow.OPEN_TRANSFER_CATEGORY;
        if (v.get("learned_category") != null) return v.get("learned_category");
        if (kind != null) switch (kind) {
            case "cash_withdrawal": return MoneyFlow.CASH_WITHDRAWAL_CATEGORY;
            case "bill_payment": case "service_topup": return "فواتير";
            case "installment_payment": return "أقساط";
            default: break;
        }
        // The principal is itself a fee (a charge, a withdrawal fee): spending on bank fees.
        if (kind == null && "bank_fee".equals(v.get("self_kind"))) return MoneyFlow.BANK_FEE_CATEGORY;
        return ExpenseParser.category(merchant(v, kind, label));
    }

    /** Money sent to another party or to one of the user's own accounts. */
    private static boolean transfer(String kind) {
        if (kind == null) return false;
        switch (kind) {
            case "instant_transfer_out": case "transfer_out": case "wallet_transfer_out": case "possible_internal_transfer": return true;
            default: return false;
        }
    }

    private DecisionNames() {}
}
