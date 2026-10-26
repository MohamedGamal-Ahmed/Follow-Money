package com.mohamedgamal.masareef;

import java.util.Map;
import java.util.Objects;

/**
 * Banque Misr credit-card messages glue «في» to a Latin merchant and the merchant to «بتاريخ» ("فيOPENAI *CHATGPTبتاريخ");
 * an Al Baraka ATM charged on a Banque Misr card is named only by its terminal and acquirer codes ("على BRKN0123 BRKA").
 * Synthetic card digits, amounts and balances; the shapes are the real ones (owner report, 26 Oct 2026).
 */
public final class GluedMerchantAtmTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    private static final String BM = "Banque Misr";

    private static String[] decide(String body) {
        long now = 1_793_000_000_000L;
        ParserShadow.Observation o = ParserShadow.observe(MessageNormalizer.Raw.sms(BM, body), "TEST", null, now, now);
        check(o != null && o.decision != null, "Observed: " + body);
        Map<String, String> v = o.values;
        String direction = v.get("direction");
        return new String[]{o.decision, DecisionNames.merchant(v, o.movementKind, BM),
                direction == null ? null : DecisionNames.category(v, o.movementKind, direction, BM), v.get("amount_minor"), v.get("currency"), o.movementKind};
    }

    private static void purchase(String body, String merchant, String category, String amountMinor, String currency) {
        String[] d = decide(body);
        same(d[0], "AUTO", "Recorded without review " + merchant);
        same(d[1], merchant, "Merchant");
        same(d[2], category, "Category " + merchant);
        same(d[3], amountMinor, "Amount " + merchant);
        same(d[4], currency, "Currency " + merchant);
    }

    private static String card(String amount, String merchant, String date) {
        return "عميلناالعزيز،شكرًا لاستخدامكم بطاقة بنك مصر الائتمانية***1111 تم خصم مبلغ " + amount + " في" + merchant +
                "بتاريخ " + date + "،الرصيدالمتاحEGP 5000.5،وحدالاستخدام الدولي المتاحEGP40000للمزيداتصل 19888";
    }

    public static void main(String[] args) {
        purchase(card("EGP 220", "OPENAI *CHATGPT", "13/10/2026"), "ChatGPT", "اشتراكات", "22000", "EGP");
        purchase(card("EGP 144.99", "Google One     ", "18/10/2026"), "Google One", "اشتراكات", "14499", "EGP");
        purchase(card("EGP 22.49", "Google Truecall", "23/10/2026"), "Google Truecall", "اشتراكات", "2249", "EGP");
        purchase(card("USD 25", "Google Play    ", "21/10/2026"), "Google Play", "اشتراكات", "2500", "USD");
        // An unknown Latin merchant still ends where the Arabic starts, and is not mistaken for the bank.
        purchase(card("EGP 75", "SOME SHOP", "20/10/2026"), "SOME SHOP", "غير مصنف", "7500", "EGP");

        // A spaced «في» before an Arabic merchant is unchanged; a word that only begins with في is never a marker.
        String[] spaced = decide("شكرًا لاستخدامكم بطاقة بنك مصر ***1111 تم خصم مبلغ EGP 60.00 في كارفور بتاريخ 20/10/2026");
        same(spaced[1], "Carrefour", "Spaced Arabic merchant");

        // Another bank's ATM named only by codes, no ATM wording: cash, recorded without review (owner report, 26 Oct 2026).
        String[] baraka = decide("شكرًا لاستخدامك بطاقة بنك مصر *2222 ، تم الآن خصم EGP 500.00 على BRKN0123         BRKA يوم 24/10/2026 " +
                "الرصيد المتاح EGP 900.00 لمزيد من المعلومات ، تفضل بزيارة الرابط التالي  https://bnkmsr.com/online");
        same(baraka[0], "AUTO", "ATM codes need no merchant review");
        same(baraka[5], "cash_withdrawal", "Al Baraka terminal is an ATM");
        same(baraka[1], "سحب نقدي", "ATM name");
        same(baraka[2], "سحب نقدي", "ATM category");
        same(baraka[3], "50000", "ATM amount");

        // Banque Misr's own machine («الة رقم») and NBE's glued «عندNBE ATM» stay cash.
        String[] machine = decide("شكرًا لاستخدامك بطاقة بنك مصر ***2222، تم خصم مبلغEGP 1000.00 الة رقم 01234567BM SODIC BR 2 يوم 19/10/2026، الرصيد المتاح EGP1500.00");
        same(machine[2], "سحب نقدي", "Banque Misr machine");
        String[] nbe = decide("تم خصم EGP 300.00 من 3333 عندNBE ATM 0042 يوم19/10/26الساعة14:54المتاح700.00 EGP للمزيد أتصل ب 19623");
        same(nbe[2], "سحب نقدي", "NBE ATM");

        // The rule is the shape, not a bank list: any terminal id + short code(s) with a round EGP amount.
        String[] other = decide("شكرًا لاستخدامك بطاقة بنك مصر *2222 ، تم الآن خصم EGP 1000.00 على QNBA0456 QNBA CAI يوم 24/10/2026 الرصيد المتاح EGP 900.00");
        same(other[0], "AUTO", "Other bank ATM decision");
        same(other[5], "cash_withdrawal", "Other bank ATM codes");
        // Not round, or not EGP: a coded merchant stays a purchase.
        String[] shop = decide(card("EGP 40", "SHOP0123 CAIRO", "20/10/2026"));
        check(!"cash_withdrawal".equals(shop[5]), "Coded merchant with an odd amount is not cash");
        same(shop[1], "SHOP0123 CAIRO", "Coded merchant keeps its name");
        String[] usd = decide(card("USD 100", "SHOP0123 NYC", "20/10/2026"));
        check(!"cash_withdrawal".equals(usd[5]), "A USD charge is not an Egyptian ATM");

        System.out.println("Glued merchant and ATM terminal checks passed: " + checks);
    }
}
