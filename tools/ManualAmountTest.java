package com.mohamedgamal.masareef;

/**
 * F-05: a hand-typed amount such as "1.500" or "12.345" is ambiguous, so the manual dialogs
 * reject it and ask the user to re-check instead of guessing. Bank SMS parsing stays lenient
 * and untouched: this test records the exact SMS results so a future change cannot pretend the
 * rule applied everywhere.
 */
public final class ManualAmountTest {
    private static int checked;

    private static void misread(String raw) {
        if (!ExpenseParser.manualAmountMisread(raw))
            throw new AssertionError("Should be rejected as ambiguous: " + raw);
        checked++;
    }

    private static void fine(String raw) {
        if (ExpenseParser.manualAmountMisread(raw))
            throw new AssertionError("Should stay accepted: " + raw);
        checked++;
    }

    private static void minor(String raw, long expected) {
        long actual = ExpenseParser.parseMinor(ExpenseParser.normalizeDigits(raw));
        if (actual != expected)
            throw new AssertionError("parseMinor(" + raw + ") = " + actual + ", expected " + expected);
        checked++;
    }

    public static void main(String[] args) {
        misread("1.500");
        misread("12.345");
        misread("1.234.567");
        misread("1 500.000");
        misread("1,500.000");
        misread("١.٥٠٠");
        misread("1٫500");

        fine("1500");
        fine("250.50");
        fine("1.5");
        fine("1.50");
        fine("1,500");
        fine("1.500,00");
        fine("1٫٥٠٠,٠٠");
        fine("");
        fine("   ");
        fine("1500.");
        fine(null);

        // SMS parsing is intentionally unchanged: the strict rule lives in the manual dialogs only.
        minor("12.345", 1234500L);
        minor("1.500", 150000L);
        minor("1.500,00", 150000L);
        minor("2,500.75", 250075L);

        System.out.println("Manual amount checks passed: " + checked);
    }
}
