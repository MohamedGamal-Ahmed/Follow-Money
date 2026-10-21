package com.mohamedgamal.masareef;

import java.util.Objects;

/**
 * Owner request (21 Oct 2026): the wording rules added from phone samples are word/root patterns, not copied
 * sentences. Each rule gets two variants worded differently from the sample it came from, run through the whole
 * pipeline: {source, text, type, decision, direction, amount minor, merchant key, required reason}. null = not checked.
 */
public final class PhrasePatternTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    static final String[][] VARIANTS = {
        // Previous-transaction reference without a new movement verb → NO_NEW_MOVEMENT.
        {"BANK", "المعاملة التي اجريتها ببطاقتك بمبلغ 250 جنيه لدى متجر النور كانت آخر معاملة لا تلامسية، استخدم الرقم السري في المرة القادمة", "OTHER", "IGNORE", null, null, null, "NO_NEW_MOVEMENT"},
        {"BANK", "Your last purchase of EGP 90.00 at CAFE ROMA was contactless; please use your PIN next time.", "OTHER", "IGNORE", null, null, null, "NO_NEW_MOVEMENT"},
        // Card activation notice → NO_NEW_MOVEMENT.
        {"BANK", "تمت تنشيط البطاقة الائتمانية بحد 15,000 جنيه. رسوم الإصدار 150 جنيه تخصم مع أول كشف حساب", "OTHER", "IGNORE", null, null, null, "NO_NEW_MOVEMENT"},
        {"BANK", "Your new card ending 4411 has been activated with a limit of EGP 20,000.00. Annual fee EGP 300.00", "OTHER", "IGNORE", null, null, null, "NO_NEW_MOVEMENT"},
        // Statement and due wording → a future obligation (FUTURE_ONLY).
        {"BANK", "Your card outstanding balance is EGP 3,400.00. Minimum due EGP 170.00 must be settled before 05/11/2026", "REMINDER", "IGNORE", null, null, null, "FUTURE_ONLY"},
        {"BANK", "مستحقات بطاقتك 3,400.00 جنيه، الحد الأدنى للدفع 170.00 جنيه، موعد السداد 05/11/2026", "REMINDER", "IGNORE", null, null, null, "FUTURE_ONLY"},
        // Requested (not confirmed) transfer → REVIEW, TRANSFER_REQUESTED.
        {"BANK", "Your transfer request has been received for EGP 1,500.00 to Mona Ali. Ref 77881", "TRANSACTION", "REVIEW", "out", "150000", null, "TRANSFER_REQUESTED"},
        {"BANK", "تم استلام طلبك لتحويل 1,500.00 جنيه إلى حساب منى علي المنتهي بـ 2233", "TRANSACTION", "REVIEW", "out", "150000", null, "TRANSFER_REQUESTED"},
        // Card repayment ("thanks for paying … to your card") → possible internal transfer.
        {"BANK", "Thanks for paying EGP 2,000.00 to your credit card ending 9911", "TRANSACTION", "REVIEW", "out", "200000", null, "POSSIBLE_INTERNAL_TRANSFER"},
        {"BANK", "Deposited of EGP 750.00 is now applied to your credit card ending 9911. Current balance EGP 250.00", "TRANSACTION", "REVIEW", "in", "75000", null, "POSSIBLE_INTERNAL_TRANSFER"},
        // A card that "made a successful <operation>" / "successful purchase" → outgoing card use.
        {"BANK", "Your debit card ***1234 made a successful payment of EGP 410.00 @ZARA CITYSTARS, available balance EGP 590.00", "TRANSACTION", "AUTO", "out", "41000", "zara citystars", null},
        {"BANK", "Successful purchase of EGP 66.50 @TALABAT with card ***1234", "TRANSACTION", null, "out", "6650", "talabat", null},
        // "<spending verb> in <merchant>".
        {"BANK", "Thanks for using card 1234, EGP 120.00 was debited in UBER on 03/10/2026", "TRANSACTION", null, "out", "12000", "uber", null},
        {"BANK", "Thank you for using card 1234, the amount EGP 45.00 was used in VODAFONE STORE on 03/10/2026", "TRANSACTION", null, "out", "4500", "vodafone store", null},
        // English footer phrases are never merchants.
        {"BANK", "EGP 300.00 was credited to your account ****5678. To find out more, call 19000", "TRANSACTION", "AUTO", "in", "30000", null, null},
        {"BANK", "EGP 300.00 was credited to your account ****5678. To opt out, reply STOP", "TRANSACTION", "AUTO", "in", "30000", null, null},
        // "Thanks to …" is courtesy, not a party.
        {"BANK", "EGP 2,000.00 has been sent from your account ****5678. Thank you to choosing NBE mobile", "TRANSACTION", "AUTO", "out", "200000", null, null},
        {"BANK", "EGP 2,000.00 has been sent from your account ****5678. Thanks a lot to using our app", "TRANSACTION", "AUTO", "out", "200000", null, null},
        // Re-posting (إعادة قيد) → reversal, expense offset.
        {"BANK", "أعيد قيد مبلغ 75.00 جنيه على بطاقتك المنتهية 4455 عن معاملة سابقة لدى أمازون", "TRANSACTION", "AUTO", "in", "7500", null, "COMPLETED_EVENT"},
        {"BANK", "تمت اعادة القيد لمعاملتك بمبلغ EGP 19.99 على البطاقة 4455 عند NETFLIX", "TRANSACTION", "AUTO", "in", "1999", "netflix", "COMPLETED_EVENT"},
    };

    public static void main(String[] args) {
        long received = java.time.LocalDate.of(2026, 10, 21).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        for (String[] v : VARIANTS) {
            MessageNormalizer.Normalized n = MessageNormalizer.normalize(MessageNormalizer.Raw.sms(v[0], v[1]));
            MessageClassifier.Result c = MessageClassifier.classify(n);
            TransactionExtractor.Extraction x = TransactionExtractor.extract(n, c, received);
            ConfidenceScorer.Confidence s = ConfidenceScorer.score(c, x);
            java.util.Set<String> reasons = new java.util.LinkedHashSet<>(c.reasons); reasons.addAll(s.reasons);
            String id = v[1].substring(0, Math.min(40, v[1].length())) + " " + reasons;
            same(c.type.name(), v[2], "Type of " + id);
            if (v[3] != null) same(s.decision, v[3], "Decision of " + id);
            if (v[4] != null) same(x.direction, v[4], "Direction of " + id);
            if (v[5] != null) same(String.valueOf(x.amountMinor), v[5], "Amount of " + id);
            if (v[6] != null) same(x.merchantKey, v[6], "Merchant of " + id);
            if (v[7] != null) check(reasons.contains(v[7]), "Reason " + v[7] + " for " + id);
            if (v[1].contains("قيد")) same(x.accountingEffect, "expense_offset", "Re-posting is an expense offset: " + id);
        }
        System.out.println("Phrase pattern checks passed: " + checks + " (" + VARIANTS.length + " variants, two per generalized rule).");
    }
}
