package com.mohamedgamal.masareef;

import java.util.Map;

/**
 * PR 1 of the "intelligence" work (5 Nov 2026): shape rules for statement-line purchases and bill payments, the
 * no-merchant fallback, transfers to people, and whole-field-only catalog aliases. Messages are redacted: account
 * masks and line numbers are fixtures, never real.
 */
public final class IntelligenceRulesTest {
    private static int checks;
    private static void same(Object actual, Object expected, String why) {
        if (!java.util.Objects.equals(actual, expected)) throw new AssertionError(why + ": <" + actual + "> != <" + expected + ">");
        checks++;
    }

    /** What the ledger would show for one SMS: {name, category, review}. */
    static String[] ledger(String sender, String text) {
        ParserShadow.Observation o = ParserShadow.observe(MessageNormalizer.Raw.sms(sender, text), "TEST", null, 1762380000000L, 1762380000000L);
        Map<String, String> v = o.values;
        String direction = v.get("direction");
        boolean review = "REVIEW".equals(o.decision) || DecisionNames.merchantUnknown(v, o.movementKind, direction);
        return new String[]{DecisionNames.merchant(v, o.movementKind, sender), DecisionNames.category(v, o.movementKind, direction, sender), String.valueOf(review)};
    }

    private static void ledger(String sender, String text, String name, String category, boolean review) {
        String[] got = ledger(sender, text);
        same(got[0], name, "name of " + text);
        same(got[1], category, "category of " + text);
        same(got[2], String.valueOf(review), "review of " + text);
    }

    public static void main(String[] args) throws Exception {
        // 1. A statement-line purchase names its merchant before "Purchase"; the processor prefix goes.
        ledger("HSBC", "From HSBC: 05NOV26 KASHIER*BUTLERS Purchase from 036-137***-001 EGP 15.00- Your available balance is EGP 955.42",
                "Butlers", "أكل وشرب", false);
        TransactionExtractor.Extraction x = TransactionExtractorTest.run(
                "From HSBC: 05NOV26 KASHIER*BUTLERS Purchase from 036-137***-001 EGP 15.00- Your available balance is EGP 955.42", 1762380000000L);
        same(x.merchantKey, "butlers", "merchant key");
        same(x.accountPartial, "001", "partial account of the user's own account");
        same(x.direction, "out", "direction");
        // Same shape, no "from <account>", another name with a star and a digit: the shape decides, not the bank.
        x = TransactionExtractorTest.run("From HSBC: 05NOV26 SQ *LUNA CAFE 2 Purchase EGP 90.00- Your available balance is EGP 800.00", 1762380000000L);
        same(x.merchantKey, "luna cafe 2", "starred processor and digit");
        // A statement-line transfer still describes itself and names no merchant.
        x = TransactionExtractorTest.run("From HSBC: 21OCT26 Transfer to 123-456***-789 EGP 50.00+ Your available balance is EGP 20,000.00", 1762380000000L);
        same(x.merchantKey, null, "transfer line names no merchant");
        same(x.description, "Transfer", "transfer line description");

        // 2. A service line paid by its number is a bill, whoever sent it.
        ledger("we-Landline", "تم سداد 5 ج.م. قيمة استخدام الراوتر على الخط رقم 0200000000", "WE", "فواتير", false);
        ledger("SomeService", "تم سداد 80 ج.م. قيمة الاستهلاك على الخط رقم 0200000000", "فاتورة", "فواتير", false);

        // 3. No merchant found: never the bank's name; the app asks.
        ledger("HSBC", "From HSBC: 05NOV26 Purchase EGP 15.00- Your available balance is EGP 955.42", "مشتريات بالكارت", "غير مصنف", true);

        // 4. Money sent to a person is a transfer to that person, in «تحويلات», never asked about.
        ledger("Telda", "تم تحويل 200 ج.م. إلى أحمد علي", "تحويل لأحمد علي", "تحويلات", false);
        ledger("CIB", "Transfer of EGP 500.00 from your account ending 1234 to Ahmed Ali has been completed", "تحويل إلى Ahmed Ali", "تحويلات", false);
        // A transfer never goes through the merchant catalog: a person named like a shop is still a person.
        for (String person : new String[]{"كريم محمد", "مؤمن أحمد", "سيف", "جاد", "أم حسن", "رعاية", "كريم"})
            ledger("Telda", "تم تحويل 200 ج.م. إلى " + person, "تحويل ل" + person, "تحويلات", false);
        for (String person : new String[]{"Careem Ali", "Mo'men Ahmed", "Gad Hassan", "Seif Adel", "Noon Samir"})
            ledger("CIB", "Transfer of EGP 500.00 from your account ending 1234 to " + person + " has been completed", "تحويل إلى " + person, "تحويلات", false);
        // A transfer that names nobody is still a transfer, not a card purchase, and is not asked about.
        ledger("BM-Online", "تم تحويل مبلغ 500.00 جنيه من حسابك رقم ***1234 الى احمد علي ***** بتاريخ 05/11/2026", "تحويل", "تحويلات", false);
        // A statement-line transfer keeps its own description.
        ledger("HSBC", "From HSBC: 05NOV26 Phone Banking Transfer to 036-137***-001 EGP 500.00- Your available balance is EGP 955.42",
                "Phone Banking Transfer", "تحويلات", false);

        // The bank's own terminal wording is no merchant either.
        ledger("HSBC", "From HSBC: 21MAR26 Total POS Purchase from 123-456***-789 EGP 280.00- Your available balance is EGP 1,720.00",
                "مشتريات بالكارت", "غير مصنف", true);

        // 5. Whole-field aliases: only the merchant field itself, never words inside another name.
        same(MerchantClassifier.category("WE"), "فواتير", "WE as the whole field");
        same(MerchantClassifier.category("WE STORE"), null, "WE inside a longer name");
        same(MerchantClassifier.category("Telecom Egypt"), "فواتير", "Telecom Egypt");
        same(MerchantClassifier.category("GALAXY CINEMA"), "ترفيه", "Galaxy Cinema");
        same(MerchantClassifier.category("GALAXY"), null, "Galaxy alone is a phone brand");
        same(MerchantClassifier.category("SAMSUNG GALAXY"), null, "Samsung Galaxy");

        // 7. «رعاية صحية» is «صحة» now.
        same(MerchantClassifier.category("El Ezaby"), "صحة", "pharmacy category");
        same(ExpenseParser.category("City Pharmacy"), "صحة", "keyword health category");

        // 6. The reviewed catalog: every CSV row is loaded, nothing else.
        java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Path.of("tools/fixtures/merchant-catalog-2026-11-05.csv"));
        same(MerchantCatalogData.CATALOG.length, lines.size() - 1, "catalog rows loaded");
        same(MerchantCatalogData.CATALOG.length, 156, "catalog size");
        for (String[] row : MerchantCatalogData.CATALOG) {
            same(MerchantClassifier.category(row[3]) != null, true, "first alias of " + row[0] + " is known");
        }
        same(MerchantClassifier.category("Hyper One"), "بقالة", "catalog merchant");
        same(MerchantClassifier.displayName("AMAZON"), "Amazon", "catalog display name");
        same(MerchantClassifier.category("صيدليات سيف"), "صحة", "catalog health category by its full Arabic name");
        // Short and common-word aliases: the whole merchant field only, never a prefix and never free text.
        for (String alias : new String[]{"NOON", "SEIF", "GAD", "BIM", "SIP", "AUC", "GUC", "BUE", "MSA", "ASU", "PAUL", "TOTAL"}) {
            same(MerchantClassifier.category(alias + " BRANCH 7 CAIRO") , null, alias + " followed by other words");
        }
        same(MerchantClassifier.category("NOON"), "تسوق", "NOON as the whole field");
        same(MerchantClassifier.category("SEIF"), "صحة", "SEIF as the whole field");
        same(MerchantClassifier.category("حساب"), null, "«حساب» alone is not a merchant");
        same(MerchantClassifier.category("حساب رقم 1234"), null, "«حساب» inside a bank phrase");
        // Arabic first names are never merchants; only English spellings and full shop names are.
        for (String person : new String[]{"كريم", "مؤمن", "جاد", "سيف", "حساب", "رعاية", "التعاون", "المختبر", "أم حسن", "ام حسن",
                "كريم محمد", "مؤمن أحمد", "سيف علي"})
            same(MerchantClassifier.category(person), null, person + " is a person's name, not a merchant");
        same(MerchantClassifier.category("CAREEM"), "مواصلات", "CAREEM in English");
        same(MerchantClassifier.category("معامل حساب"), "صحة", "full Arabic shop name");
        same(MerchantClassifier.category("صيدليات رعاية"), "صحة", "full Arabic pharmacy name");
        // A longer alias still takes a branch suffix: "UBER" is short, "HYPER ONE" is not.
        same(MerchantClassifier.category("HYPER ONE MAADI"), "بقالة", "branch suffix on a long alias");

        // Keyword rules: whole words in the name, only when no catalog entry matched, longest keyword first.
        same(MerchantClassifier.keywordCategory("Al Amal Pharmacy"), "صحة", "pharmacy keyword");
        same(MerchantClassifier.keywordCategory("صيدلية الامل"), "صحة", "Arabic pharmacy keyword");
        same(MerchantClassifier.keywordCategory("Downtown Cinema"), "ترفيه", "cinema keyword");
        same(MerchantClassifier.keywordCategory("Iron Gym"), "رياضة", "gym keyword");
        same(MerchantClassifier.keywordCategory("Future Academy"), "تعليم", "academy keyword");
        same(MerchantClassifier.keywordCategory("Pharmacyland"), null, "a keyword inside a longer word");
        same(MerchantClassifier.keywordCategory("AMAZON"), null, "a catalog entry decides, not keywords");
        same(ExpenseParser.category("Al Amal Pharmacy"), "صحة", "keyword category reaches the ledger");
        same(ExpenseParser.category("Downtown Cinema"), "ترفيه", "new category reaches the ledger");

        System.out.println("IntelligenceRulesTest passed: " + checks + " checks");
    }
}
