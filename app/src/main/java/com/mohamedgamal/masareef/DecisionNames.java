package com.mohamedgamal.masareef;

/**
 * The ledger name and category of a new-pipeline decision, from the observation's values (no Android, no storage).
 * Names and categories come from the same sources, in the same order, as the screens have always shown them:
 * ExpenseParser.merchantName for the spelling and ExpenseParser.category for the category.
 */
final class DecisionNames {
    /** What a card movement with no readable merchant is called; never the bank that sent the message. */
    static final String UNKNOWN_CARD_PURCHASE = "مشتريات بالكارت";
    /** A bill paid by its number whose sender is no known service. */
    static final String UNKNOWN_BILL = "فاتورة";

    /**
     * The name shown in the ledger: the merchant as written, else the other party (a transfer to a person reads
     * «تحويل لأحمد علي»), else what kind of movement it is. The sender's label is never a name: a bank is not the merchant.
     */
    static String merchant(java.util.Map<String, String> v, String kind, String label) {
        if (v.get("merchant") != null) {
            String name = ExpenseParser.merchantName(v.get("merchant"));
            return transfer(kind) ? transferTo(name) : name;
        }
        if (v.get("counterparty") != null) return transfer(kind) ? transferTo(v.get("counterparty")) : v.get("counterparty");
        if (v.get("description") != null && !purchaseWording(v.get("description"))) return v.get("description"); // an HSBC statement line describes itself
        if (kind != null) switch (kind) {
            case "cash_withdrawal": return "سحب نقدي";
            case "instant_transfer_in": case "instant_transfer_out": return "تحويل لحظي";
            case "salary": return "راتب";
            case "service_topup": return "شحن رصيد";
            case "installment_payment": return "قسط";
            case "bill_payment": return MerchantClassifier.category(label) != null ? MerchantClassifier.displayName(label) : UNKNOWN_BILL;
            default: break;
        }
        if (transfer(kind)) return "تحويل"; // a transfer whose receiver the message does not name
        return "in".equals(v.get("direction")) ? label : UNKNOWN_CARD_PURCHASE;
    }

    /** «تحويل لأحمد علي»: money sent to a person is recorded as a transfer to that person; a business keeps its own name. */
    static String transferTo(String name) {
        if (!isPerson(name) || MerchantClassifier.category(name) != null) return name;
        return name.codePointAt(0) >= 0x600 && name.codePointAt(0) <= 0x6FF ? "تحويل ل" + name : "تحويل إلى " + name;
    }

    /** "Total POS Purchase": the bank's name for the kind of movement, not who was paid. */
    private static boolean purchaseWording(String description) {
        return description.toLowerCase(java.util.Locale.ROOT).matches(".*\\bpurchase\\b.*");
    }

    /** A name, not an InstaPay address, a phone number or an account tail. */
    private static boolean isPerson(String counterparty) {
        return counterparty.matches("(?s)\\p{L}[\\p{L} .'*-]*") && !counterparty.contains("@");
    }

    /**
     * True when the app has to ask: a card purchase whose merchant could not be read. A transfer to a person, a fee, a cash
     * withdrawal or a known kind of movement is never asked about.
     */
    static boolean merchantUnknown(java.util.Map<String, String> v, String kind, String direction) {
        return "out".equals(direction) && kind == null && v.get("merchant") == null && v.get("counterparty") == null &&
                (v.get("description") == null || purchaseWording(v.get("description"))) && v.get("self_kind") == null;
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
