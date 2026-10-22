package com.mohamedgamal.masareef;

import java.util.List;
import java.util.Objects;

/**
 * Parity of the new pipeline's ledger names, categories and recording decisions with what the device suites expect
 * (0.4.0: the new pipeline decides every message). Each text is the exact message of a device test that failed when the
 * legacy parser stopped deciding; the names are fixed in the pipeline, never by asking the legacy parser.
 */
public final class DecisionNamesTest {
    private static int checks;
    private static final List<String> failures = new java.util.ArrayList<>();
    private static void check(boolean value, String why) { if (!value) failures.add(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    private static ParserShadow.Observation observe(String sender, String body) {
        return ParserShadow.observe(MessageNormalizer.Raw.sms(sender, body), "TEST", null, 1792656501960L, 1792656501960L);
    }

    /** decision|merchant|category, as PipelineDecider builds the ledger row. */
    private static String row(String sender, String body) {
        ParserShadow.Observation o = observe(sender, body);
        if (!ShadowDiff.newRecords(o.decision)) return o.decision;
        return o.decision + "|" + DecisionNames.merchant(o.values, o.movementKind, sender) + "|" + DecisionNames.category(o.values, o.movementKind, o.values.get("direction"), sender);
    }

    public static void main(String[] args) {
        ParserShadow.learning().setMerchantRules(List.of());
        // Display names: the catalog's spelling, as the legacy screens show it (ReconciliationTest, PhaseOneTest, FeatureAuditTest).
        same(row("Banque Misr", "شكرًا لاستخدامك بطاقة بنك مصر ***5555، تم الآن خصم 255.01 EGPعند ESPRESSO LAB يوم 22/10/2026 ، الرصيد المتاح EGP 500.00"),
                "AUTO|Espresso Lab|مقاهي", "Catalog spelling of a known merchant");
        same(row("HSBC", "تم خصم مبلغ EGP 1897.71 فى Bill Payment Vodafone يوم 17/09/2026 الرصيد المتاح EGP 1497.43"), "AUTO|Vodafone|فواتير", "Bill payment names the biller");
        same(row("TRU", "عميل ترو العزيز، تم سداد مبلغ 1885.00 ج.م بتاريخ 01/10/2026 بنجاح."), "AUTO|TRU|أقساط", "A provider receipt is named after the provider, an installment");
        same(row("ET-Google", "تم شراء G1SK100M - 100 GB (Google One) بنجاح من خلال Google Play Store بقيمة 94.99 جنيه من رصيدكم."),
                "AUTO|Google One|اشتراكات", "The product bought, never «رصيدكم»");
        same(row("BANK", "Card purchase at CARREFOUR EGP 250.00. Available balance EGP 300"), "AUTO|Carrefour|بقالة", "No currency code glued to the merchant");
        same(row("BANK", "Card purchase at SAMEBILL EGP 250.00"), "AUTO|SAMEBILL|غير مصنف", "Merchant before the amount keeps its own name");
        same(row("BANK", "Card purchase EGP 100 at QA provider"), "AUTO|QA provider|غير مصنف", "A card purchase is an outgoing completed purchase");
        // HSBC statement lines: the description between the date token and to/from is the name (FeatureAuditTest).
        same(row("HSBC", "From HSBC: 25SEP26 Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74"),
                "AUTO|Phone Banking Transfer|دخل", "HSBC credit description");
        same(row("HSBC", "From HSBC: 17OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00"),
                "AUTO|ATM Cash Withdrawal|سحب نقدي", "HSBC cash withdrawal description");
        same(row("HSBC", "From HSBC: 24SEP26 TT Payment from 012-345***-679 USD 2,083.85+ Your available balance is USD 2,150.10"),
                "AUTO|TT Payment|دخل", "HSBC transfer in description");
        same(row("HSBC", "From HSBC: 25SEP26 Phone Banking Transfer from 012-345***-679 USD 1,700.00- Your available balance is USD 450.10"),
                "AUTO|Phone Banking Transfer|تحويلات", "HSBC transfer out description");
        same(row("HSBC", "Purchase EGP100 at SHOP ONE"), "AUTO|SHOP ONE|غير مصنف", "HSBC purchase alert");
        // Transfers keep the transfer category; the person is the name even when masked (ReconciliationTest).
        same(row("Banque Misr", "تم تنفيذ تحويل لحظي من حسابكم رقم xxx7392 بمبلغ 150 جم إلى شخص ا******* م*** رقم مرجعي 123456789012 يوم 2026-10-11 الساعه 15:27 للمزيد اتصل ب 19888"),
                "AUTO|شخص ا******* م***|تحويلات", "A masked recipient is still the name");
        // Paying the user's own credit card is a transfer (FeatureAuditTest, CardCancellationTest).
        check(row("QA Bank", "Transfer of EGP 4,000.00 from your account ****5678 to credit card ****1234 on 10/10/2026").endsWith("|تحويلات"),
                "Credit card repayment recorded as a transfer: " + row("QA Bank", "Transfer of EGP 4,000.00 from your account ****5678 to credit card ****1234 on 10/10/2026"));
        check(row("BANK", "تم تحويل مبلغ 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222").endsWith("|تحويلات"),
                "Arabic credit card repayment is a transfer: " + row("BANK", "تم تحويل مبلغ 227 جنيه من حساب رقم 1111 إلى بطاقة البنك الائتمانية رقم 2222"));
        // A transfer stays a transfer whatever the user taught for a merchant of the same name (FeatureAuditTest).
        ParserShadow.learning().setMerchantRules(List.of(new LocalLearning.MerchantRule("Butlers", "مقاهي", false)));
        same(row("BANK", "You sent EGP 42.00 to Butlers"), "AUTO|Butlers|تحويلات", "Sending money is a transfer");
        same(row("BANK", "Card purchase EGP 91.25 at KASHIER*BUTLERS & CO MALL"), "AUTO|Butlers|مقاهي", "The user's rule names the purchase category");
        ParserShadow.learning().setMerchantRules(List.of());
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " of " + checks + " failed:\n" + String.join("\n", failures));
        System.out.println("Decision names checks passed: " + checks);
    }
}
