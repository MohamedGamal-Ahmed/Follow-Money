package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Numbers behind the analytics summary tiles (owner-approved redesign, 31 Oct 2026). Pure, so the JVM checks cover it.
 * Periods are [start, end) as {@link MoneyFlow#period} returns them.
 */
final class AnalyticsMath {
    private AnalyticsMath() {}

    /** Days of the period that have started by today: a running month counts up to today, a past one in full. */
    static int elapsedDays(LocalDate[] period, LocalDate today) {
        LocalDate end = period[1].isAfter(today.plusDays(1)) ? today.plusDays(1) : period[1];
        return (int) Math.max(1, ChronoUnit.DAYS.between(period[0], end));
    }

    /**
     * The same stretch one month (or year) earlier, so a running month is compared day for day: 1–30 Oct with
     * 1–30 Sep, a finished month with the whole previous one. Null for the rolling 12 months (nothing to compare).
     */
    static LocalDate[] previous(MoneyFlow.Range range, LocalDate[] period, LocalDate today) {
        if (range == MoneyFlow.Range.LAST_12) return null;
        LocalDate start = range == MoneyFlow.Range.YEAR ? period[0].minusYears(1) : period[0].minusMonths(1);
        LocalDate fullEnd = period[0];
        LocalDate end = start.plusDays(elapsedDays(period, today));
        if (end.isAfter(fullEnd)) end = fullEnd;
        boolean running = !period[1].isBefore(today.plusDays(1));
        return new LocalDate[]{start, running ? end : fullEnd};
    }

    /** Whole-percent change from before to now; null when there is nothing before to compare with. */
    static Integer deltaPercent(long now, long before) {
        if (before <= 0) return null;
        return (int) Math.round((now - before) * 100.0 / before);
    }

    /** Average per elapsed day, in minor units. */
    static long perDay(long total, LocalDate[] period, LocalDate today) {
        return Math.round((double) total / elapsedDays(period, today));
    }

    /** Whole units with thousands separators for the big summary numbers: 1230000 → "12,300". */
    static String whole(long minor) {
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(minor / 100.0));
    }

    /** Share of a total as a whole percent, never NaN. */
    static int share(long part, long total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }
}
