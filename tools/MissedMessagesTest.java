package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * m6 (0.4.0), its review and the owner's acceptance: «رسائل من بنوكك ما اتسجلتش» lists a possible miss from a bank the
 * user holds an account with (or a selected payment app's notification), naming an amount above zero; never an offer,
 * a code, a balance or a reminder, never a sender the user has no account with (we-Landline, VFCashGifts, B.TECH), and
 * never a shape the user dismissed.
 */
public final class MissedMessagesTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();
    private static void check(boolean value, String why) { if (!value) failures.add(why); checks++; }

    /** The owner's sources: HSBC and Banque Misr. */
    private static final Set<String> BANKS = Set.of("HSBC", "BANKMISR");

    private static boolean listed(LocalLearning learning, String sender, String body) {
        MessageNormalizer.Raw raw = MessageNormalizer.Raw.sms(sender, body);
        ParserShadow.Observation o = ParserShadow.observe(raw, "TEST", null, 1792656501960L, 1792656501960L);
        return MissedMessages.listed(sender, o, MessageNormalizer.normalize(raw).text, learning, BANKS);
    }

    public static void main(String[] args) {
        LocalLearning learning = ParserShadow.learning();
        String unknown = "Payment format SUPPORT_QA ref 12345678 from SECRET PERSON amount EGP 120.00";
        check(listed(learning, "HSBC", unknown), "An unknown format from the user's bank naming an amount is listed");
        check(listed(learning, "BankMisr", "عملية رقم 99881 بقيمة 250.00 جنيه على حسابكم xxx7392 بمرجع AB12"), "An unread Banque Misr message is listed");
        check(!listed(learning, "QASHOP", "QASHOP payment format ref 12345678 amount EGP 120.00"), "A sender that is not a bank is not");
        check(!listed(learning, "NBE", unknown), "A bank the user has no account with is not");
        // Owner acceptance, 22 Oct 2026: senders the user has no account with, and amounts of zero.
        check(!listed(learning, "we-Landline", "تم الاشتراك بنجاح في WE Home Top-up 140 جيجا لمدة شهر. رصيدك الحالى 0.00 جنيه"), "we-Landline is not a bank of the user");
        check(!listed(learning, "VFCashGifts", "مبروك كسبت 0.00 جنيه كاش باك على فودافون كاش! استخدمه قبل 30/11/2026"), "VFCashGifts is not a bank of the user");
        check(!listed(learning, "B.TECH", "استلمنا طلبك رقم 4471-2210 بنجاح وجاري تجهيزه للشحن. شكرا لاختيارك B.TECH"), "B.TECH is not a bank of the user");
        check(!listed(learning, "HSBC", "Payment format SUPPORT_QA ref 12345678 amount EGP 0.00"), "0.00 is not an amount");
        // Not understood (UNUNDERSTOOD) needs the same known sender: the screen showed these from any sender.
        ParserShadow.Observation unclear = ParserShadow.observe(MessageNormalizer.Raw.sms("we-Landline", "Paid EUR 1.250 at Cafe Nine"), "TEST", null, 1792656501960L, 1792656501960L);
        check("UNUNDERSTOOD".equals(unclear.decision) && !listed(learning, "we-Landline", "Paid EUR 1.250 at Cafe Nine"), "A not-understood message from a non-bank is not listed");
        check(listed(learning, "HSBC", "Paid EUR 1.250 at Cafe Nine"), "A not-understood message from the user's bank is listed");
        check(!listed(learning, "BankMisr", "عميلنا العزيز، تم إضافة الراتب لبطاقتكم اليوم. الان يمكنك الحصول على قرض شخصي خلال ساعة بدون مستندات أو مصاريف حتى 500,000 جنيه"),
                "A loan offer with an amount is not a miss");
        check(!listed(learning, "HSBC", "OTP verification code 987654 for payment of EGP 120.00"), "A code is not a miss");
        check(!listed(learning, "HSBC", "Account ending 1234 available balance EGP 1000"), "A balance is not a miss");
        check(!listed(learning, "HSBC", "Your card ****4321 statement is issued with total EGP 7000, minimum due is EGP 6000, due before 26-11-2026"),
                "A statement reminder is not a miss");
        // «تجاهل» teaches the shape: the same message delivered again, or another of the same shape, is not listed again.
        learning.recordDismissal("HSBC", MessageNormalizer.normalize(MessageNormalizer.Raw.sms("HSBC", unknown)).text);
        check(!listed(learning, "HSBC", unknown), "A dismissed message does not come back");
        check(!listed(learning, "HSBC", unknown.replace("12345678", "87654321").replace("120.00", "75.50")), "Nor does another of its shape");
        check(listed(learning, "BankMisr", unknown), "Another sender's shape is still listed");
        learning.restoreShapes(new ArrayList<>());
        // A selected payment app's notification is a known sender (only selected apps' notifications reach capture).
        MessageNormalizer.Raw app = MessageNormalizer.Raw.notification("io.telda.app", 0, "Telda", "رمز العملية 77120 بقيمة 45.00 ج.م. مرجع QX", null, null, null, null);
        ParserShadow.Observation seen = ParserShadow.observe(app, "TEST", null, 1792656501960L, 1792656501960L);
        check(!ShadowDiff.newRecords(seen.decision) && MissedMessages.listed("Telda", seen, MessageNormalizer.normalize(app).text, learning, Set.of()),
                "A selected app's unread notification is listed: " + seen.decision + "/" + seen.type);
        // 0.4.1 review: a message a legacy guard kept out of the ledger (LEGACY_GUARD:*) is a possible miss, with the same
        // sender and amount conditions; the same message recorded is not.
        String guarded = "Transfer EGP 331 from account ending 1111 will be reversed";
        ParserShadow.Observation held = ParserShadow.observe(MessageNormalizer.Raw.sms("HSBC", guarded), "TEST", null, 1792656501960L, 1792656501960L);
        String heldText = MessageNormalizer.normalize(MessageNormalizer.Raw.sms("HSBC", guarded)).text;
        check(ShadowDiff.newRecords(held.decision), "The guarded fixture is a recording decision: " + held.decision);
        check(MissedMessages.listed("HSBC", held, heldText, learning, BANKS, true), "A guarded message from the user's bank is listed");
        check(!MissedMessages.listed("HSBC", held, heldText, learning, BANKS, false), "The same message, recorded, is not");
        check(!MissedMessages.listed("NBE", held, heldText, learning, BANKS, true), "A guarded message from a bank the user has no account with is not");
        String zero = "Transfer EGP 0.00 from account ending 1111 will be reversed";
        check(!MissedMessages.listed("HSBC", held, MessageNormalizer.normalize(MessageNormalizer.Raw.sms("HSBC", zero)).text, learning, BANKS, true),
                "A guarded message naming no amount above zero is not");
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " of " + checks + " failed:\n" + String.join("\n", failures));
        System.out.println("Missed messages checks passed: " + checks);
    }
}
