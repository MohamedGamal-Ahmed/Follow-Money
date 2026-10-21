package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds money mentions (a currency next to a number) in normalized text and reads their value without guessing.
 * A bare number is never money. Used by classification (is there an amount here?) and extraction (which one, what value).
 */
final class AmountScanner {
    /** One currency-tagged number. currency is ISO, or null when the unit is ambiguous (bare ريال). */
    static final class Money {
        final int start, end;
        final String currency, rawNumber, unit;
        final boolean leadingMinus;
        final char trailingSign;
        /** Minor units, or -1 when the number format is ambiguous (single "1.250") or malformed. */
        final long minor;

        Money(int start, int end, String currency, String unit, String rawNumber, boolean leadingMinus, char trailingSign, long minor) {
            this.start = start; this.end = end; this.currency = currency; this.unit = unit; this.rawNumber = rawNumber;
            this.leadingMinus = leadingMinus; this.trailingSign = trailingSign; this.minor = minor;
        }

        boolean ambiguousFormat() { return minor < 0; }
    }

    private static final String NUMBER = "(?:[+-]\\s?)?(?:\\d{1,3}(?:(?:,|\\.| )\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?|[.,]\\d+)[+-]?";
    private static final Pattern MONEY;
    static {
        StringBuilder units = new StringBuilder();
        for (String[] unit : ParsingPolicy.CURRENCY_UNITS) units.append(units.length() == 0 ? "" : "|").append(unit[1]);
        String unit = "(?:" + units + ")";
        // A unit glued to a hyphen and digits ("EGP-1234") is an identifier, not an amount. A number ends at the first
        // character that is neither a digit nor a separator followed by a digit, so a glued word after it ("15.00عند",
        // "149.98EGP") is a separate word and the number is never cut back to fit ("149.98عند" is never 149).
        MONEY = Pattern.compile("(?iu)(?<![\\p{L}])(" + unit + ")(?![\\p{L}])(?!-\\d)\\s{0,2}(" + NUMBER + ")(?![.,]?\\d)" +
                // A number glued to a mask ("***-789", "x1234") is part of an account, not an amount before a unit.
                "|(?<![\\d.,*#xX-])(" + NUMBER + ")\\s{0,2}(" + unit + ")(?![\\p{L}])");
    }

    static List<Money> scan(String text) {
        List<Money> found = new ArrayList<>();
        if (text == null) return found;
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            boolean unitFirst = m.group(1) != null;
            String unit = unitFirst ? m.group(1) : m.group(4), number = unitFirst ? m.group(2) : m.group(3);
            String compact = number.replace(" ", "");
            boolean leadingMinus = compact.startsWith("-");
            char trailing = compact.endsWith("+") ? '+' : compact.endsWith("-") ? '-' : 0;
            String digits = compact.replaceAll("^[+-]|[+-]$", "");
            found.add(new Money(m.start(), m.end(), currency(unit), unit, number.trim(), leadingMinus, trailing, minor(digits)));
        }
        return found;
    }

    /** ISO code for a matched unit, or null for an ambiguous one (bare ريال). */
    static String currency(String unit) {
        for (String[] entry : ParsingPolicy.CURRENCY_UNITS)
            if (Pattern.compile("(?iu)^(?:" + entry[1] + ")$").matcher(unit).matches()) return entry[0].isEmpty() ? null : entry[0];
        return null;
    }

    /**
     * Two-digit currencies. Rules: both separators -> the last is decimal; repeated or 3-digit-grouped commas/spaces are
     * thousands; one comma or dot before exactly 2 digits is decimal; a single dot before exactly 3 digits is ambiguous.
     */
    static long minor(String raw) {
        String s = raw;
        if (s.isEmpty()) return -1;
        int dot = s.lastIndexOf('.'), comma = s.lastIndexOf(',');
        String integer, fraction = "";
        if (dot >= 0 && comma >= 0) {
            int decimal = Math.max(dot, comma);
            integer = s.substring(0, decimal).replaceAll("[.,]", ""); fraction = s.substring(decimal + 1);
        } else if (dot >= 0 || comma >= 0) {
            char separator = dot >= 0 ? '.' : ',';
            int count = s.length() - s.replace(String.valueOf(separator), "").length(), at = s.lastIndexOf(separator);
            String tail = s.substring(at + 1);
            if (count > 1 || (separator == ',' && tail.length() == 3)) {
                if (!s.matches("\\d{1,3}(?:[" + separator + "]\\d{3})+")) return -1;
                integer = s.replace(String.valueOf(separator), "");
            } else if (tail.length() == 3) return -1; // "1.250": thousands or decimal, unknowable without locale evidence.
            else { integer = s.substring(0, at); fraction = tail; }
        } else integer = s;
        if (integer.isEmpty()) integer = "0";
        if (!integer.matches("\\d+") || !fraction.matches("\\d{0,2}")) return -1;
        if (integer.length() > 15) return -1;
        String padded = (fraction + "00").substring(0, 2);
        return Long.parseLong(integer) * 100 + Long.parseLong(padded);
    }

    private AmountScanner() {}
}
