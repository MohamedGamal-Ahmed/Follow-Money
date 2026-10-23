package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Orders the existing payments offered for one commitment cycle: a payment named like the commitment first, then the
 * exact amount, then a near amount (within 5%), then the rest; the distance from the due date orders equals.
 * Another currency always goes last. Pure logic; CommitmentPayments decides which rows are eligible at all.
 */
final class PaymentCandidates {
    private PaymentCandidates() {}

    static final class Item {
        final long id, amountMinor, when;
        final String merchant, currency;
        Item(long id, String merchant, long amountMinor, String currency, long when) {
            this.id = id; this.merchant = merchant; this.amountMinor = amountMinor; this.currency = currency; this.when = when;
        }
    }

    static List<Item> rank(List<Item> items, long expectedMinor, String currency, String commitment, LocalDate due) {
        return rank(items, expectedMinor, currency, commitment, due, ZoneId.systemDefault());
    }

    static List<Item> rank(List<Item> items, long expectedMinor, String currency, String commitment, LocalDate due, ZoneId zone) {
        List<Item> ranked = new ArrayList<>(items);
        ranked.sort(Comparator.<Item>comparingInt(item -> tier(item, expectedMinor, currency, commitment))
                .thenComparingLong(item -> Math.abs(ChronoUnit.DAYS.between(due, Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate())))
                .thenComparingLong(item -> -item.when));
        return ranked;
    }

    /** Worth showing before the rest: named like the commitment, or the same currency within 5% of the amount. */
    static boolean likely(Item item, long expectedMinor, String currency, String commitment) {
        return tier(item, expectedMinor, currency, commitment) <= 2;
    }

    private static int tier(Item item, long expectedMinor, String currency, String commitment) {
        if (currency != null && !currency.equals(item.currency)) return 4;
        if (nameMatches(commitment, item.merchant)) return 0;
        long gap = Math.abs(item.amountMinor - expectedMinor);
        if (gap == 0) return 1;
        if (expectedMinor > 0 && gap * 20 <= expectedMinor) return 2;
        return 3;
    }

    /** One word of 3+ letters of either name inside the other, ignoring case and punctuation ("OneDrive" / "MICROSOFT*ONEDRIVE"). */
    static boolean nameMatches(String a, String b) {
        if (a == null || b == null) return false;
        return wordInside(a, b) || wordInside(b, a);
    }

    private static boolean wordInside(String words, String other) {
        String flat = letters(other);
        if (flat.isEmpty()) return false;
        for (String word : words.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            if (word.length() >= 3 && !word.matches("\\p{N}+") && flat.contains(word)) return true;
        return false;
    }

    private static String letters(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", ""); }
}
