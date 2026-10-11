package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** One definition per number: buckets, periods, medians and the month that rose. */
public final class MoneyFlowTest {
    private static int checked;
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }
    private static long at(String day) { return LocalDate.parse(day).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli(); }
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    public static void main(String[] args) {
        check(MoneyFlow.bucket("expense", "أكل وشرب") == MoneyFlow.Bucket.LIVING, "food is living");
        check(MoneyFlow.bucket("expense", "سحب نقدي") == MoneyFlow.Bucket.LIVING, "cash withdrawal counts as spending");
        check(MoneyFlow.bucket("expense", "رسوم تحويل") == MoneyFlow.Bucket.LIVING, "bank fees are spending");
        check(MoneyFlow.bucket("expense", "أقساط") == MoneyFlow.Bucket.COMMITMENT, "installment");
        check(MoneyFlow.bucket("expense", "تحويلات") == MoneyFlow.Bucket.OPEN_TRANSFER, "open transfer");
        check(MoneyFlow.bucket("transfer", "تحويلات") == MoneyFlow.Bucket.INTERNAL, "own transfer");
        check(MoneyFlow.bucket("income", "دخل") == MoneyFlow.Bucket.INFLOW, "inflow");

        LocalDate today = LocalDate.of(2026, 10, 11);
        LocalDate[] year = MoneyFlow.period(MoneyFlow.Range.YEAR, today, today);
        check(year[0].equals(LocalDate.of(2026, 1, 1)) && year[1].equals(LocalDate.of(2026, 10, 12)), "year starts 1 January");
        LocalDate[] last12 = MoneyFlow.period(MoneyFlow.Range.LAST_12, today, today);
        check(last12[0].equals(LocalDate.of(2025, 11, 1)) && MoneyFlow.months(last12).size() == 12, "last 12 calendar months");
        LocalDate[] pastYear = MoneyFlow.period(MoneyFlow.Range.YEAR, LocalDate.of(2025, 6, 1), today);
        check(pastYear[1].equals(LocalDate.of(2026, 1, 1)), "a past year is complete");
        LocalDate[] month = MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 2, 14), today);
        check(month[0].equals(LocalDate.of(2026, 2, 1)) && month[1].equals(LocalDate.of(2026, 3, 1)), "calendar month");

        List<MoneyFlow.Item> items = new ArrayList<>();
        items.add(new MoneyFlow.Item(1, at("2026-01-31"), 5060000, "EGP", "expense", "أقساط", "قسط الشقة"));
        items.add(new MoneyFlow.Item(2, at("2026-03-01"), 5060000, "EGP", "expense", "أقساط", "قسط الشقة"));
        items.add(new MoneyFlow.Item(3, at("2026-03-02"), 21830, "EGP", "expense", "أكل وشرب", "Cafe"));
        items.add(new MoneyFlow.Item(4, at("2026-03-03"), 10000, "EGP", "expense", "غير مصنف", "X"));
        items.add(new MoneyFlow.Item(5, at("2026-03-04"), 300000, "EGP", "expense", "سحب نقدي", "ATM"));
        items.add(new MoneyFlow.Item(6, at("2026-02-10"), 1300000, "EGP", "expense", "تحويلات", "تحويل لحظي"));
        items.add(new MoneyFlow.Item(7, at("2026-07-30"), 2000000, "EGP", "transfer", "تحويلات", "تحويل بين حساباتي"));
        items.add(new MoneyFlow.Item(8, at("2026-07-30"), 2000, "EGP", "expense", "رسوم تحويل", "رسوم"));
        items.add(new MoneyFlow.Item(9, at("2026-05-01"), 9000000, "EGP", "income", "دخل", "Salary?"));
        items.add(new MoneyFlow.Item(10, at("2026-05-01"), 2280, "USD", "expense", "اشتراكات", "Claude"));
        items.add(new MoneyFlow.Item(11, at("2025-12-31"), 999999, "EGP", "expense", "أكل وشرب", "Last year"));
        Map<String, MoneyFlow.Summary> all = MoneyFlow.summarize(items, year, ZONE);
        MoneyFlow.Summary egp = all.get("EGP");
        check(egp.of(MoneyFlow.Bucket.COMMITMENT) == 10120000, "commitments");
        check(egp.of(MoneyFlow.Bucket.LIVING) == 21830 + 10000 + 300000 + 2000, "living incl. cash and fees, excl. last year");
        check(egp.of(MoneyFlow.Bucket.OPEN_TRANSFER) == 1300000, "open transfers");
        check(egp.of(MoneyFlow.Bucket.INTERNAL) == 2000000, "internal moved once");
        check(egp.of(MoneyFlow.Bucket.INFLOW) == 9000000, "inflow");
        check(egp.totalOut() == 10120000 + 333830 + 1300000, "total out excludes internal");
        check(all.get("USD").of(MoneyFlow.Bucket.COMMITMENT) == 2280 && egp.of(MoneyFlow.Bucket.COMMITMENT) != 10122280, "currencies stay separate");
        check(egp.livingMedian() == (10000 + 21830) / 2, "median of 4 living payments: " + egp.livingMedian());
        check(egp.uncategorized == 1 && egp.uncategorizedAmount == 10000, "uncategorized");
        check(egp.byMonth.size() == 10, "every month of the year appears");
        check(egp.largestOutflows(1).get(0).amountMinor == 5060000, "largest payment");
        MoneyFlow.MonthPeak peak = egp.peak();
        check(peak != null && peak.month.equals(YearMonth.of(2026, 3)) && peak.driver == MoneyFlow.Bucket.COMMITMENT,
                "March peaks because of the installment, not living");

        MoneyTotals daily = new MoneyTotals();
        daily.add("EGP", "expense", "أقساط", 5060000);
        daily.add("EGP", "expense", "أكل وشرب", 25000);
        daily.add("EGP", "expense", "تحويلات", 100000);
        daily.add("EGP", "transfer", "تحويلات", 2000000);
        MoneyTotals.Totals t = daily.currencies.get("EGP");
        check(t.living == 25000 && t.expense == 5185000 && t.transfers == 2000000, "daily budget only sees living spending");
        System.out.println("Money flow checks passed: " + checked);
    }
}
