package com.mohamedgamal.masareef;

import java.time.LocalDate;

/** Analytics summary tiles (redesign, 31 Oct 2026): comparison period, change, average per day. */
public final class AnalyticsMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 30);
        LocalDate[] october = MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 10, 1), today);
        check(AnalyticsMath.elapsedDays(october, today) == 30, "running month counts up to today");
        LocalDate[] before = AnalyticsMath.previous(MoneyFlow.Range.MONTH, october, today);
        check(before[0].equals(LocalDate.of(2026, 9, 1)) && before[1].equals(LocalDate.of(2026, 10, 1)), "1-30 Oct vs 1-30 Sep");

        LocalDate early = LocalDate.of(2026, 10, 5);
        LocalDate[] first = AnalyticsMath.previous(MoneyFlow.Range.MONTH, october, early);
        check(first[0].equals(LocalDate.of(2026, 9, 1)) && first[1].equals(LocalDate.of(2026, 9, 6)), "day for day early in the month");

        LocalDate[] september = MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 9, 1), today);
        check(AnalyticsMath.elapsedDays(september, today) == 30, "a finished month counts in full");
        LocalDate[] august = AnalyticsMath.previous(MoneyFlow.Range.MONTH, september, today);
        check(august[0].equals(LocalDate.of(2026, 8, 1)) && august[1].equals(LocalDate.of(2026, 9, 1)), "a finished month vs the whole previous one");

        LocalDate[] march = MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 3, 1), today);
        LocalDate[] february = AnalyticsMath.previous(MoneyFlow.Range.MONTH, march, today);
        check(february[1].equals(LocalDate.of(2026, 3, 1)), "never runs past the previous month's end");

        check(AnalyticsMath.previous(MoneyFlow.Range.LAST_12, october, today) == null, "rolling 12 months has no comparison");
        LocalDate[] year = MoneyFlow.period(MoneyFlow.Range.YEAR, LocalDate.of(2026, 10, 1), today);
        LocalDate[] lastYear = AnalyticsMath.previous(MoneyFlow.Range.YEAR, year, today);
        check(lastYear[0].equals(LocalDate.of(2025, 1, 1)) && lastYear[1].equals(LocalDate.of(2025, 10, 31)), "year to date vs the same days last year");

        check(AnalyticsMath.deltaPercent(1230000, 1398000) == -12, "12,300 vs 13,980 is 12% less");
        check(AnalyticsMath.deltaPercent(1398000, 1320000) == 6, "13,980 vs 13,200 is 6% more");
        check(AnalyticsMath.deltaPercent(5000, 0) == null, "nothing before: no change shown");
        check(AnalyticsMath.perDay(1230000, october, today) == 41000, "12,300 over 30 days is 410 a day");
        check("12,300".equals(AnalyticsMath.whole(1230000)), "whole units with separators");
        check(AnalyticsMath.share(420000, 1230000) == 34 && AnalyticsMath.share(1, 0) == 0, "shares");
        System.out.println("Analytics math checks: " + checks + " passed");
    }
}
