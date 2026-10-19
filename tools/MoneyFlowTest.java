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
        AccountMovement bank = new AccountMovement();
        bank.add("expense", "بقالة", 10000, true, false);
        bank.add("expense", "رسوم تحويل", 150, true, false);
        bank.add("expense", "سحب نقدي", 70000, true, false);
        bank.add("expense", "تحويلات", 50000, true, false);
        bank.add("income", "دخل", 120000, true, false);
        bank.add("transfer", "تحويلات", 20000, false, true);
        bank.add("transfer", "تحويلات", 30000, true, false);
        bank.add("transfer", "تحويلات", 99999, true, true);
        bank.add("income", "دخل", 99999, false, false);
        check(bank.spent == 10150, "bank purchases and fees remain gross movements");
        check(bank.withdrawn == 70000, "cash withdrawal is recorded as a cash move, not as spending");
        check(bank.sent == 50000 && bank.outgoing() == 130150, "external transfers and withdrawals leave the account once");
        check(bank.incoming == 120000, "recorded inflow, not a guessed salary");
        check(bank.ownIncoming == 20000 && bank.ownOutgoing == 30000, "own transfers retain direction and exclude same-root endpoints");
        AccountMovement purposeful = new AccountMovement();
        purposeful.add("expense", "أقساط", 70000, true, false);
        check(purposeful.spent == 70000 && purposeful.sent == 0, "known-purpose transfers follow existing expense categories");
        check(MoneyFlow.bucket("expense", "أكل وشرب") == MoneyFlow.Bucket.LIVING, "food is living");
        check(MoneyFlow.bucket("expense", "سحب نقدي") == MoneyFlow.Bucket.CASH_WITHDRAWAL, "cash withdrawal is its own non-spending bucket");
        check(!MoneyFlow.isOutflow(MoneyFlow.Bucket.CASH_WITHDRAWAL), "cash withdrawal is not an outflow");
        check("سحب نقدي".equals(MoneyFlow.label(MoneyFlow.Bucket.CASH_WITHDRAWAL)), "cash withdrawal keeps its Arabic label");
        check(MoneyFlow.bucket("expense", "رسوم تحويل") == MoneyFlow.Bucket.LIVING, "bank fees are spending");
        check(MoneyFlow.bucket("expense", "رسوم بنكية") == MoneyFlow.Bucket.LIVING, "standalone bank fees are spending");
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
        check(egp.of(MoneyFlow.Bucket.CASH_WITHDRAWAL) == 300000, "withdrawal principal stays visible but separate");
        check(egp.of(MoneyFlow.Bucket.LIVING) == 21830 + 10000 + 2000, "living excludes cash withdrawals and last year, keeps fees");
        check(egp.of(MoneyFlow.Bucket.OPEN_TRANSFER) == 1300000, "open transfers");
        check(egp.of(MoneyFlow.Bucket.INTERNAL) == 2000000, "internal moved once");
        check(egp.of(MoneyFlow.Bucket.INFLOW) == 9000000, "inflow");
        check(egp.totalOut() == 10120000 + 33830 + 1300000, "total out excludes internal and cash withdrawals");
        check(all.get("USD").of(MoneyFlow.Bucket.COMMITMENT) == 2280 && egp.of(MoneyFlow.Bucket.COMMITMENT) != 10122280, "currencies stay separate");
        check(egp.livingMedian() == 10000, "median ignores the withdrawal: " + egp.livingMedian());
        check(egp.uncategorized == 1 && egp.uncategorizedAmount == 10000, "uncategorized");
        check(egp.byMonth.size() == 10, "every month of the year appears");
        check(egp.byDay.get(LocalDate.of(2026, 3, 4))[MoneyFlow.Bucket.CASH_WITHDRAWAL.ordinal()] == 300000, "history keeps the withdrawal amount on its day");
        check(egp.largestOutflows(1).get(0).amountMinor == 5060000, "largest payment ignores withdrawals");
        MoneyFlow.MonthPeak peak = egp.peak();
        check(peak != null && peak.month.equals(YearMonth.of(2026, 3)) && peak.driver == MoneyFlow.Bucket.COMMITMENT,
                "March peaks because of the installment, not living");

        // R1: a cash principal and an explicit standalone fee stay separate in the same historical projection.
        List<MoneyFlow.Item> mixed = new ArrayList<>();
        mixed.add(new MoneyFlow.Item(21, at("2026-10-05"), 200000, "EGP", "expense", "سحب نقدي", "ATM"));
        mixed.add(new MoneyFlow.Item(22, at("2026-10-05"), 500, "EGP", "expense", "رسوم بنكية", "ATM fee"));
        mixed.add(new MoneyFlow.Item(23, at("2026-10-06"), 50000, "EGP", "income", "دخل", "Refund"));
        MoneyFlow.Summary cashAndFee = MoneyFlow.summarize(mixed,
                MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 10, 1), today), ZONE).get("EGP");
        check(cashAndFee.of(MoneyFlow.Bucket.CASH_WITHDRAWAL) == 200000, "withdrawal principal stays out of spending");
        check(cashAndFee.of(MoneyFlow.Bucket.LIVING) == 500 && cashAndFee.totalOut() == 500,
                "the standalone fee remains the only spending");
        check(cashAndFee.of(MoneyFlow.Bucket.INFLOW) == 50000, "an incoming row keeps its own amount");

        MoneyTotals daily = new MoneyTotals();
        daily.add("EGP", "expense", "أقساط", 5060000);
        daily.add("EGP", "expense", "أكل وشرب", 25000);
        daily.add("EGP", "expense", "تحويلات", 100000);
        daily.add("EGP", "expense", "سحب نقدي", 70000);
        daily.add("EGP", "transfer", "تحويلات", 2000000);
        MoneyTotals.Totals t = daily.currencies.get("EGP");
        check(t.living == 25000 && t.expense == 5185000 && t.transfers == 2000000, "daily budget and day headers ignore cash withdrawals");
        // A filtered list's day header shows that filter's own total (the B3 sample: cash 200 + 50, own move 300).
        MoneyTotals filtered = new MoneyTotals();
        filtered.add("EGP", "expense", "سحب نقدي", 20000);
        filtered.add("EGP", "expense", "سحب نقدي", 5000);
        filtered.add("EGP", "transfer", "تحويلات", 30000);
        filtered.add("EGP", "expense", "تحويلات", 55000);
        filtered.add("EGP", "expense", "فواتير", 50000);
        filtered.add("EGP", "expense", "بقالة", 7000);
        filtered.add("EGP", "income", "دخل", 109000);
        MoneyTotals.Totals f = filtered.currencies.get("EGP");
        check(f.of("CASH_WITHDRAWAL") == 25000 && f.cash == 25000 && f.expense == 112000, "cash list header = 250, still outside «خرج»");
        check(f.of("INTERNAL") == 30000, "own-account list header = 300");
        check(f.of("OPEN_TRANSFER") == 55000 && f.of("COMMITMENT") == 50000 && f.of("LIVING") == 7000 && f.of("INFLOW") == 109000, "one header figure per bucket");
        check(f.of(MoneyFlow.SPENDING_FILTER) == 57000 && f.of("") == f.expense, "spending list header = living + commitments; plain list keeps «خرج»");
        // Home «مصروفاتك آخر 7 أيام»: grocery 100 (30 refunded -> 70 effective) + fee 20 + paid bill 500 = 590.
        LocalDate weekToday = LocalDate.of(2026, 10, 19);
        List<MoneyFlow.Item> wk = new ArrayList<>();
        wk.add(new MoneyFlow.Item(1, at("2026-10-19"), 7000, "EGP", "expense", "بقالة", "هايبر"));
        wk.add(new MoneyFlow.Item(2, at("2026-10-18"), 50000, "EGP", "expense", "فواتير", "كهرباء"));
        wk.add(new MoneyFlow.Item(3, at("2026-10-18"), 2000, "EGP", "expense", "رسوم بنكية", "بنك"));
        wk.add(new MoneyFlow.Item(4, at("2026-10-17"), 40000, "EGP", "expense", "تحويلات", "صديق"));
        wk.add(new MoneyFlow.Item(5, at("2026-10-17"), 20000, "EGP", "expense", "سحب نقدي", "ATM"));
        wk.add(new MoneyFlow.Item(6, at("2026-10-16"), 30000, "EGP", "transfer", "تحويلات", "بين حساباتي"));
        wk.add(new MoneyFlow.Item(7, at("2026-10-15"), 100000, "EGP", "income", "دخل", "مرتب"));
        wk.add(new MoneyFlow.Item(8, at("2026-10-12"), 99900, "EGP", "expense", "فواتير", "خارج الفترة"));
        wk.add(new MoneyFlow.Item(9, at("2026-10-19"), 5000, "USD", "expense", "بقالة", "عملة تانية"));
        MoneyFlow.Week week = MoneyFlow.week(wk, weekToday, ZONE, "EGP");
        check(week.spending() == 59000, "week spending = daily + commitments after refund = 590");
        check(week.living == 9000 && week.commitment == 50000, "split: daily 90, commitments 500");
        check(week.openTransfer == 40000, "open transfer 400 stays out of the number");
        check(week.spending() + week.openTransfer == 99000 && week.spending() != 59000 + 20000 + 30000, "cash and own moves never join spending");
        check(week.spendingCount == 3 && week.income == 100000, "count and income");
        // The bars use the number's own definition: grocery 70 on the 19th, bill 500 + fee 20 on the 18th; nothing on the 13th.
        check(week.daily[6] == 7000 && week.daily[5] == 52000 && week.daily[0] == 0, "bars = spending per day, bill included, cash/transfers excluded");
        long barsTotal = 0; for (long day : week.daily) barsTotal += day;
        check(barsTotal == week.spending(), "the seven bars add up to the number");
        check(week.living == 9000, "day-to-day part (the daily budget's definition) still available on its own");
        // Movements shown beside the number, never added to it.
        check(week.cash == 20000 && week.internal == 30000 && week.openTransfer == 40000, "cash, own-account and open-transfer movements carried separately");
        check(week.movement(MoneyFlow.Bucket.CASH_WITHDRAWAL) == 20000 && week.movement(MoneyFlow.Bucket.INTERNAL) == 30000
                && week.movement(MoneyFlow.Bucket.OPEN_TRANSFER) == 40000 && week.movement(MoneyFlow.Bucket.LIVING) == 0, "movement() reads the three movement buckets only");
        check(MoneyFlow.MOVEMENT_BUCKETS.length == 3 && !MoneyFlow.isSpending(MoneyFlow.MOVEMENT_BUCKETS[0])
                && !MoneyFlow.isSpending(MoneyFlow.MOVEMENT_BUCKETS[1]) && !MoneyFlow.isSpending(MoneyFlow.MOVEMENT_BUCKETS[2]), "no movement bucket is spending");
        // The category split uses the same definition and period, so it adds up to the number too.
        long categoriesTotal = 0; boolean billListed = false;
        for (Map.Entry<String, Long> entry : week.categories) { categoriesTotal += entry.getValue(); if ("فواتير".equals(entry.getKey()) && entry.getValue() == 50000) billListed = true; }
        check(categoriesTotal == week.spending() && billListed, "home categories add up to the number and include commitments");
        check(week.categories.get(0).getKey().equals("فواتير") && week.categories.size() == 3, "categories sorted largest first: bills, grocery, fee");
        check(!MoneyFlow.listsCaptureHistory(MoneyFlow.SPENDING_FILTER) && MoneyFlow.listsCaptureHistory("") && MoneyFlow.listsCaptureHistory("LIVING"),
                "the list behind the number shows transactions only; other lists keep cancelled attempts");
        // The list filter behind the number returns exactly the rows that add up to it.
        long listed = 0;
        for (MoneyFlow.Item item : wk) {
            if (!"EGP".equals(item.currency) || item.when < at("2026-10-13") || item.when >= at("2026-10-20")) continue;
            if (MoneyFlow.matchesFilter(MoneyFlow.SPENDING_FILTER, item.bucket)) listed += item.amountMinor;
        }
        check(listed == week.spending(), "tapping the number lists rows summing to it");
        check(!MoneyFlow.matchesFilter(MoneyFlow.SPENDING_FILTER, MoneyFlow.Bucket.OPEN_TRANSFER)
                && !MoneyFlow.matchesFilter(MoneyFlow.SPENDING_FILTER, MoneyFlow.Bucket.CASH_WITHDRAWAL)
                && !MoneyFlow.matchesFilter(MoneyFlow.SPENDING_FILTER, MoneyFlow.Bucket.INTERNAL)
                && !MoneyFlow.matchesFilter(MoneyFlow.SPENDING_FILTER, MoneyFlow.Bucket.INFLOW), "filter excludes transfers, cash, internal and income");
        check(MoneyFlow.matchesFilter("", MoneyFlow.Bucket.INTERNAL) && MoneyFlow.matchesFilter("COMMITMENT", MoneyFlow.Bucket.COMMITMENT)
                && !MoneyFlow.matchesFilter("COMMITMENT", MoneyFlow.Bucket.LIVING), "existing single-bucket filters unchanged");
        check("مصروفات".equals(MoneyFlow.filterLabel(MoneyFlow.SPENDING_FILTER)) && "سحب نقدي".equals(MoneyFlow.filterLabel("CASH_WITHDRAWAL")), "filter labels");
        check(MoneyFlow.week(new ArrayList<>(), weekToday, ZONE, "EGP").spending() == 0, "empty week is zero");
        System.out.println("Money flow checks passed: " + checked);
    }
}
