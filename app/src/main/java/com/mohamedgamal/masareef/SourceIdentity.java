package com.mohamedgamal.masareef;

import java.util.Locale;

/** Matches bank identifiers without treating zero padding as a second account. */
final class SourceIdentity {
    private SourceIdentity() {}

    static String key(String bank, String digits) {
        if (digits == null || !digits.matches("[0-9]{3,4}")) return null;
        String name = bank(bank);
        if (name == null) return null;
        return name + ":" + Integer.parseInt(digits);
    }

    /**
     * A wallet or prepaid-card app whose notifications never carry card digits (Telda) is one source per app, so its
     * movements can be paired with the bank leg of a top-up. A bank or a channel (InstaPay) gives null.
     */
    static String appKey(String sender) {
        String name = bank(sender);
        return "TELDA".equals(name) ? name + ":APP" : null;
    }

    static String bank(String bank) {
        String name = bank == null ? "" : bank.toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}0-9]", "");
        if (name.equals("BM") || name.startsWith("BMONLINE") ||
                name.contains("BANQUEMISR") || name.contains("BANKMISR") ||
                name.contains("بنك مصر".replace(" ", ""))) name = "BANKMISR";
        else if (name.startsWith("HSBC")) name = "HSBC";
        else if (name.startsWith("TELDA") || name.startsWith("تيلدا")) name = "TELDA";
        else if (name.startsWith("NBE") || name.contains("NATIONALBANKOFEGYPT") ||
                name.contains("البنكالأهلي")) name = "NBE";
        return name.isEmpty() ? null : name;
    }
}
