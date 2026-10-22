package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;

/**
 * m6 (0.4.0) and its review: «رسائل من بنوكك ما اتسجلتش» lists a possible miss from a known bank, never an offer, a code,
 * a balance or a reminder (Banque Misr sends many «قرض شخصي» offers with amounts), and never a shape the user dismissed.
 */
public final class MissedMessagesTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();
    private static void check(boolean value, String why) { if (!value) failures.add(why); checks++; }

    private static boolean listed(LocalLearning learning, String sender, String body) {
        MessageNormalizer.Raw raw = MessageNormalizer.Raw.sms(sender, body);
        ParserShadow.Observation o = ParserShadow.observe(raw, "TEST", null, 1792656501960L, 1792656501960L);
        return MissedMessages.listed(sender, o, MessageNormalizer.normalize(raw).text, learning);
    }

    public static void main(String[] args) {
        LocalLearning learning = ParserShadow.learning();
        String unknown = "Payment format SUPPORT_QA ref 12345678 from SECRET PERSON amount EGP 120.00";
        check(listed(learning, "HSBC", unknown), "An unknown format from a known bank naming an amount is listed");
        check(!listed(learning, "QASHOP", "QASHOP payment format ref 12345678 amount EGP 120.00"), "A sender that is not a bank is not");
        check(!listed(learning, "BankMisr", "عميلنا العزيز، تم إضافة الراتب لبطاقتكم اليوم. الان يمكنك الحصول على قرض شخصي خلال ساعة بدون مستندات أو مصاريف حتى 500,000 جنيه"),
                "A loan offer with an amount is not a miss");
        check(!listed(learning, "BankMisr", "احصل على قرض شخصي حتى 1,000,000 جنيه بفائدة مميزة. اتصل الآن 19888"), "An offer is not a miss");
        check(!listed(learning, "HSBC", "OTP verification code 987654 for payment of EGP 120.00"), "A code is not a miss");
        check(!listed(learning, "HSBC", "Account ending 1234 available balance EGP 1000"), "A balance is not a miss");
        check(!listed(learning, "HSBC", "Your card ****4321 statement is issued with total EGP 7000, minimum due is EGP 6000, due before 26-11-2026"),
                "A statement reminder is not a miss");
        // «تجاهل» teaches the shape: the same message delivered again, or another of the same shape, is not listed again.
        learning.recordDismissal("HSBC", MessageNormalizer.normalize(MessageNormalizer.Raw.sms("HSBC", unknown)).text);
        check(!listed(learning, "HSBC", unknown), "A dismissed message does not come back");
        check(!listed(learning, "HSBC", unknown.replace("12345678", "87654321").replace("120.00", "75.50")), "Nor does another of its shape");
        check(listed(learning, "BankMisr", "Payment format SUPPORT_QA ref 12345678 from SECRET PERSON amount EGP 120.00"), "Another sender's shape is still listed");
        learning.restoreShapes(new ArrayList<>());
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " of " + checks + " failed:\n" + String.join("\n", failures));
        System.out.println("Missed messages checks passed: " + checks);
    }
}
