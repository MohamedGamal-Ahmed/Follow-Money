package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Date bounds are [start, end), so every transaction belongs to one period. */
public final class BudgetPeriod {
    private BudgetPeriod() {}

    public static LocalDate[] bounds(String cycle, LocalDate anchor, int durationDays, LocalDate today) {
        if ("once".equals(cycle)) return new LocalDate[]{anchor, anchor.plusDays(Math.max(1, durationDays))};
        if ("monthly".equals(cycle)) {
            LocalDate start = monthDay(YearMonth.from(today), anchor.getDayOfMonth());
            if (start.isAfter(today)) start = monthDay(YearMonth.from(today).minusMonths(1), anchor.getDayOfMonth());
            return new LocalDate[]{start, monthDay(YearMonth.from(start).plusMonths(1), anchor.getDayOfMonth())};
        }
        int days = "weekly".equals(cycle) ? 7 : Math.max(1, durationDays);
        long elapsed = ChronoUnit.DAYS.between(anchor, today);
        LocalDate start = anchor.plusDays(Math.floorDiv(elapsed, days) * days);
        return new LocalDate[]{start, start.plusDays(days)};
    }

    private static LocalDate monthDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }
}
