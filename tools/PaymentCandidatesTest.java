package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Tester, 24 Oct 2026: «اربط دفع موجود» for OneDrive 179.99 (cycle 22 Sep) listed two months of purchases newest first,
 * so the payment had to be hunted for. The likely payment comes first: the commitment's name, then the amount, then the date.
 */
public final class PaymentCandidatesTest {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static long at(String day) { return LocalDate.parse(day).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli(); }
    private static PaymentCandidates.Item item(long id, String merchant, long minor, String currency, String day) {
        return new PaymentCandidates.Item(id, merchant, minor, currency, at(day));
    }

    public static void main(String[] args) {
        LocalDate due = LocalDate.parse("2026-09-22");
        List<PaymentCandidates.Item> items = new ArrayList<>();
        items.add(item(1, "Seoudi", 11831, "EGP", "2026-10-22"));
        items.add(item(2, "DOCTOR M", 5500, "EGP", "2026-10-22"));
        items.add(item(3, "talda", 3500, "EGP", "2026-10-22"));
        items.add(item(4, "Butlers", 2500, "EGP", "2026-10-20"));
        items.add(item(5, "MICROSOFT*ONEDRIVE", 17999, "EGP", "2026-09-23"));
        items.add(item(6, "Google Play", 17999, "EGP", "2026-10-02"));
        items.add(item(7, "Seoudi", 18000, "EGP", "2026-09-22"));
        items.add(item(8, "Netflix", 17999, "USD", "2026-09-22"));

        List<PaymentCandidates.Item> ranked = PaymentCandidates.rank(items, 17999, "EGP", "OneDrive", due);
        check(ranked.size() == items.size(), "Ranking never drops a candidate");
        check(ranked.get(0).id == 5, "The payment named like the commitment comes first");
        check(ranked.get(1).id == 6 && ranked.get(2).id == 7, "Then the exact amount, then a near amount; the date only orders equals");
        check(PaymentCandidates.likely(ranked.get(0), 17999, "EGP", "OneDrive") && PaymentCandidates.likely(ranked.get(2), 17999, "EGP", "OneDrive"),
                "Name or near amount is likely");
        check(!PaymentCandidates.likely(items.get(0), 17999, "EGP", "OneDrive") && !PaymentCandidates.likely(items.get(7), 17999, "EGP", "OneDrive"),
                "A different amount or another currency is not likely");
        check(PaymentCandidates.nameMatches("Microsoft", "MICROSOFT*ONEDRIVE") && PaymentCandidates.nameMatches("ONEDRIVE", "OneDrive")
                && !PaymentCandidates.nameMatches("TRU", "Seoudi") && !PaymentCandidates.nameMatches("TRU · Sharaf DG · 2026-09-26", "REF 2026 Seoudi") && !PaymentCandidates.nameMatches("", "Seoudi"), "Name match by word, case-insensitive");
        check(ranked.get(ranked.size() - 1).id == 8, "Another currency goes last");
        check(ranked.get(3).id == 4 || ranked.get(3).id == 1, "Unrelated purchases follow, nearest to the due date first");
        check(ranked.indexOf(items.get(3)) < ranked.indexOf(items.get(0)), "20 Oct before 22 Oct for a 22 Sep cycle");
        System.out.println("Payment candidate checks passed: " + checks);
    }
}
