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
        check(f.of(MoneyFlow.OUT_FILTER) == 112000 + 25000, "statement «خرج» list header = spending + open transfers + cash withdrawals");
        // Review of PR #53: the analytics total lists exactly what it adds. Spending 100 + salary 1,000 + cash 200 -> 100.
        check(f.of(MoneyFlow.OUTFLOW_FILTER) == f.living + f.commitment + f.openTransfer, "analytics total list header = its rows");
        check(MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.LIVING) && MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.COMMITMENT)
                && MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.OPEN_TRANSFER)
                && !MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.INFLOW) && !MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.INTERNAL)
                && !MoneyFlow.matchesFilter(MoneyFlow.OUTFLOW_FILTER, MoneyFlow.Bucket.CASH_WITHDRAWAL), "analytics total list: no income, own moves or cash");
        MoneyTotals review = new MoneyTotals();
        review.add("EGP", "expense", "بقالة", 10000);
        review.add("EGP", "income", "دخل", 100000);
        review.add("EGP", "expense", "سحب نقدي", 20000);
        check(review.currencies.get("EGP").of(MoneyFlow.OUTFLOW_FILTER) == 10000, "100 spent + 1,000 salary + 200 cash -> the list shows 100");
        check("إجمالي المصروف".equals(MoneyFlow.filterLabel(MoneyFlow.OUTFLOW_FILTER)) && !MoneyFlow.listsCaptureHistory(MoneyFlow.OUTFLOW_FILTER), "label, transactions only");
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

        // 2ج — the month statement: «دخل / خرج / باقي» from the pay day to today, one definition in MoneyFlow.
        LocalDate statementToday = LocalDate.of(2026, 10, 20);
        LocalDate[] fromPayDay = MoneyFlow.statementPeriod(25, statementToday);
        check(fromPayDay[0].equals(LocalDate.of(2026, 9, 25)) && fromPayDay[1].equals(LocalDate.of(2026, 10, 21)), "pay day 25 → from 25 September to today inclusive");
        LocalDate[] afterPayDay = MoneyFlow.statementPeriod(25, LocalDate.of(2026, 10, 26));
        check(afterPayDay[0].equals(LocalDate.of(2026, 10, 25)) && afterPayDay[1].equals(LocalDate.of(2026, 10, 27)), "after the pay day the period restarts");
        LocalDate[] calendar = MoneyFlow.statementPeriod(0, statementToday);
        check(calendar[0].equals(LocalDate.of(2026, 10, 1)) && calendar[1].equals(LocalDate.of(2026, 10, 21)), "no pay day → calendar month to today");
        LocalDate[] monthEnd = MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, statementToday);
        check(monthEnd[0].equals(LocalDate.of(2026, 9, 30)), "«آخر يوم في الشهر» → from 30 September");
        check(monthEnd[0].equals(DailyAllowance.calculate(null, statementToday, 0, 0, DailyAllowance.MONTH_END).cycleStart), "statement period equals the daily allowance cycle");

        // The eight rows: salary, daily spend, subscription, transfer to a person, cash withdrawal, own transfer,
        // a purchase after its linked refund (the refund itself is already offset upstream), and a purchase paid from cash.
        List<MoneyFlow.Item> eight = new ArrayList<>();
        eight.add(new MoneyFlow.Item(1, at("2026-09-25"), 3000000, "EGP", "income", "دخل", "مرتب"));
        eight.add(new MoneyFlow.Item(2, at("2026-10-02"), 150000, "EGP", "expense", "أكل وشرب", "مطعم"));
        eight.add(new MoneyFlow.Item(3, at("2026-10-03"), 30000, "EGP", "expense", "اشتراكات", "Netflix"));
        eight.add(new MoneyFlow.Item(4, at("2026-10-04"), 200000, "EGP", "expense", "تحويلات", "صديق"));
        eight.add(new MoneyFlow.Item(5, at("2026-10-05"), 50000, "EGP", "expense", "سحب نقدي", "ATM"));
        eight.add(new MoneyFlow.Item(6, at("2026-10-06"), 1000000, "EGP", "transfer", "تحويلات", "بين حساباتي"));
        eight.add(new MoneyFlow.Item(7, at("2026-10-07"), 75000, "EGP", "expense", "بقالة", "سوبر ماركت بعد الاسترداد"));
        eight.add(new MoneyFlow.Item(8, at("2026-10-08"), 20000, "EGP", "expense", "أكل وشرب", "قهوة بالكاش", false, true));
        eight.add(new MoneyFlow.Item(9, at("2026-09-20"), 999999, "EGP", "expense", "أكل وشرب", "قبل يوم القبض"));
        MoneyFlow.Statement statement = MoneyFlow.statement(eight, fromPayDay, ZONE, "EGP");
        check(statement.income == 3000000, "income = inflow");
        check(statement.living == 225000 && statement.commitment == 30000 && statement.openTransfer == 200000 && statement.cash == 50000, "components of «خرج»");
        check(statement.out == 505000, "خرج = living + commitments + open transfers + cash withdrawal");
        check(statement.remaining == 3000000 - 505000, "باقي = دخل − خرج");
        check(statement.internal == 1000000 && statement.out + statement.income != 505000 + 3000000 + 1000000, "own transfer is in none of the three");
        check(statement.cashSpending == 20000 && statement.living == 225000, "a purchase paid from cash is detail only: counted when withdrawn");
        // Review of 2ج: the cash rule covers every «خرج» bucket, not only day-to-day spending.
        List<MoneyFlow.Item> cashCommitment = new ArrayList<>(eight);
        cashCommitment.add(new MoneyFlow.Item(10, at("2026-10-09"), 100000, "EGP", "expense", "أقساط", "قسط بالكاش", false, true));
        cashCommitment.add(new MoneyFlow.Item(11, at("2026-10-10"), 40000, "EGP", "expense", "تحويلات", "لشخص بالكاش", false, true));
        MoneyFlow.Statement fromCash = MoneyFlow.statement(cashCommitment, fromPayDay, ZONE, "EGP");
        check(fromCash.commitment == 30000 && fromCash.openTransfer == 200000 && fromCash.out == 505000 && fromCash.outCount == 5,
                "an installment or a transfer paid from cash stays out of «خرج»: " + fromCash.out);
        check(fromCash.cashSpending == 20000 + 100000 + 40000, "all cash-paid rows are detail: " + fromCash.cashSpending);
        check(statement.outCount == 5, "five rows make up «خرج»");
        check(statement.salary == 3000000 && statement.transfersIn == 0 && statement.salaryWhen == at("2026-09-25"), "single inflow on the pay day is the salary");
        check(statement.unlinkedTransfer == 0, "a transfer below a quarter of income raises no warning");
        check(statement.component(MoneyFlow.Bucket.LIVING) == 225000 && statement.component(MoneyFlow.Bucket.CASH_WITHDRAWAL) == 50000
                && statement.component(MoneyFlow.Bucket.INTERNAL) == 0, "component() reads the four «خرج» buckets only");
        check(MoneyFlow.isStatementOut(MoneyFlow.Bucket.CASH_WITHDRAWAL) && MoneyFlow.isStatementOut(MoneyFlow.Bucket.OPEN_TRANSFER)
                && !MoneyFlow.isStatementOut(MoneyFlow.Bucket.INTERNAL) && !MoneyFlow.isStatementOut(MoneyFlow.Bucket.INFLOW), "statement out buckets");
        check(MoneyFlow.matchesFilter(MoneyFlow.OUT_FILTER, MoneyFlow.Bucket.CASH_WITHDRAWAL) && MoneyFlow.matchesFilter(MoneyFlow.OUT_FILTER, MoneyFlow.Bucket.LIVING)
                && !MoneyFlow.matchesFilter(MoneyFlow.OUT_FILTER, MoneyFlow.Bucket.INTERNAL) && !MoneyFlow.matchesFilter(MoneyFlow.OUT_FILTER, MoneyFlow.Bucket.INFLOW), "the «خرج» list filter");
        check("خرج".equals(MoneyFlow.filterLabel(MoneyFlow.OUT_FILTER)) && !MoneyFlow.listsCaptureHistory(MoneyFlow.OUT_FILTER), "«خرج» list label and transactions-only");
        check(MoneyFlow.statement(new ArrayList<>(), fromPayDay, ZONE, "EGP").remaining == 0, "empty statement is zero");

        // A payment to one of the user's own credit cards is internal even before the settlement dialog.
        check(MoneyFlow.bucket("expense", "تحويلات", true) == MoneyFlow.Bucket.INTERNAL && MoneyFlow.bucket("expense", "تحويلات", false) == MoneyFlow.Bucket.OPEN_TRANSFER
                && MoneyFlow.bucket("income", "دخل", true) == MoneyFlow.Bucket.INFLOW, "known-card destination makes a debit internal");

        // The owner's scenario (20 October): salary at month end in bank A, 90% moved to bank B, two credit cards paid from their banks.
        LocalDate[] ownerPeriod = MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, statementToday);
        List<MoneyFlow.Item> owner = new ArrayList<>();
        owner.add(new MoneyFlow.Item(1, at("2026-09-30"), 3000000, "EGP", "income", "دخل", "مرتب"));
        owner.add(new MoneyFlow.Item(2, at("2026-09-30"), 2700000, "EGP", "transfer", "تحويلات", "A → B مربوط"));
        owner.add(new MoneyFlow.Item(3, at("2026-10-02"), 100000, "EGP", "expense", "تسوق", "كارت A 1"));
        owner.add(new MoneyFlow.Item(4, at("2026-10-03"), 200000, "EGP", "expense", "أكل وشرب", "كارت A 2"));
        owner.add(new MoneyFlow.Item(5, at("2026-10-04"), 50000, "EGP", "expense", "بقالة", "كارت A 3"));
        owner.add(new MoneyFlow.Item(6, at("2026-10-05"), 70000, "EGP", "expense", "تسوق", "كارت B 1"));
        owner.add(new MoneyFlow.Item(7, at("2026-10-06"), 30000, "EGP", "expense", "مقاهي", "كارت B 2"));
        owner.add(new MoneyFlow.Item(8, at("2026-10-10"), 400000, "EGP", "expense", "تحويلات", "سداد كارت A", true, false));
        owner.add(new MoneyFlow.Item(9, at("2026-10-11"), 300000, "EGP", "expense", "تحويلات", "سداد كارت B", true, false));
        owner.add(new MoneyFlow.Item(10, at("2026-10-12"), 150000, "EGP", "expense", "فواتير", "كهرباء"));
        owner.add(new MoneyFlow.Item(11, at("2026-10-13"), 50000, "EGP", "expense", "سحب نقدي", "ATM"));
        owner.add(new MoneyFlow.Item(12, at("2026-10-14"), 20000, "EGP", "expense", "أكل وشرب", "كاش", false, true));
        MoneyFlow.Statement linked = MoneyFlow.statement(owner, ownerPeriod, ZONE, "EGP");
        check(linked.income == 3000000 && linked.salary == 3000000 && linked.transfersIn == 0, "salary once; the own transfer is no income");
        check(linked.out == 450000 + 150000 + 50000, "خرج = card purchases + direct spending + cash withdrawal only: " + linked.out);
        check(linked.openTransfer == 0 && linked.internal == 2700000 + 400000 + 300000, "neither settlement nor own transfer is in «خرج»");
        check(linked.remaining == 3000000 - 650000, "باقي = salary − what was really consumed, not the bank balance");
        check(linked.unlinkedTransfer == 0, "a linked transfer raises no warning");
        // Same month without the transfer linked: the debit is an open transfer and the deposit a second inflow.
        List<MoneyFlow.Item> unlinked = new ArrayList<>(owner);
        unlinked.set(1, new MoneyFlow.Item(2, at("2026-09-30"), 2700000, "EGP", "expense", "تحويلات", "تحويل لحظي"));
        unlinked.add(new MoneyFlow.Item(13, at("2026-09-30") + 60000, 2700000, "EGP", "income", "دخل", "تحويل لحظي"));
        MoneyFlow.Statement warned = MoneyFlow.statement(unlinked, ownerPeriod, ZONE, "EGP");
        check(warned.unlinkedTransfer == 2700000 && warned.unlinkedTransferId == 2, "an unlinked transfer ≥ 25% of income is flagged with its amount and row");
        check(warned.openTransfer == 2700000 && warned.out == 650000 + 2700000, "the unlinked amount does enter «خرج», never silently");
        check(warned.income == 5700000 && warned.salary == 3000000 && warned.transfersIn == 2700000, "the deposit counts as a transfer that arrived, not as salary");
        // Income split follows the descriptor: a wire payment is the salary; phone-banking and instant credits are transfers in, even when larger.
        List<MoneyFlow.Item> hsbc = new ArrayList<>();
        hsbc.add(new MoneyFlow.Item(1, at("2026-09-26"), 208385, "USD", "income", "دخل", "TT Payment"));
        hsbc.add(new MoneyFlow.Item(2, at("2026-09-27"), 170000, "USD", "income", "دخل", "Phone Banking Transfer"));
        hsbc.add(new MoneyFlow.Item(3, at("2026-10-01"), 50000, "USD", "income", "دخل", "someone@instapay"));
        MoneyFlow.Statement usd = MoneyFlow.statement(hsbc, fromPayDay, ZONE, "USD");
        check(usd.salary == 208385 && usd.transfersIn == 220000 && usd.income == 428385, "salary by descriptor, transfers in separately");
        check(MoneyFlow.incomingTransfer("Phone Banking Transfer") && MoneyFlow.incomingTransfer("تحويل لحظي") && MoneyFlow.incomingTransfer("x@instapay")
                && !MoneyFlow.incomingTransfer("TT Payment") && !MoneyFlow.incomingTransfer("مرتب"), "incoming-transfer descriptors");
        // Without a pay-day match and without descriptors, the largest inflow is the salary.
        List<MoneyFlow.Item> plain = new ArrayList<>();
        plain.add(new MoneyFlow.Item(1, at("2026-10-03"), 500000, "EGP", "income", "دخل", "شركة"));
        plain.add(new MoneyFlow.Item(2, at("2026-10-09"), 2500000, "EGP", "income", "دخل", "شركة"));
        MoneyFlow.Statement largest = MoneyFlow.statement(plain, fromPayDay, ZONE, "EGP");
        check(largest.salary == 2500000 && largest.salaryWhen == at("2026-10-09") && largest.transfersIn == 500000, "largest inflow is the salary when none sits on the pay day");

        // 20 October (owner's ledger): pay day «آخر يوم في الشهر» but the salary landed on 26 September as an instant transfer.
        check(MoneyFlow.payDayIn(YearMonth.of(2026, 9), DailyAllowance.MONTH_END).equals(LocalDate.of(2026, 9, 30))
                && MoneyFlow.payDayIn(YearMonth.of(2026, 2), 31).equals(LocalDate.of(2026, 2, 28))
                && MoneyFlow.payDayIn(YearMonth.of(2026, 10), 25).equals(LocalDate.of(2026, 10, 25)), "pay day inside a month");
        List<MoneyFlow.Item> early = new ArrayList<>();
        early.add(new MoneyFlow.Item(1, at("2026-09-20"), 150000, "EGP", "income", "دخل", "تحويل لحظي"));
        early.add(new MoneyFlow.Item(2, at("2026-09-26"), 5600000, "EGP", "income", "دخل", "تحويل لحظي"));
        early.add(new MoneyFlow.Item(3, at("2026-09-30"), 5060000, "EGP", "expense", "أقساط", "قسط شقة"));
        early.add(new MoneyFlow.Item(4, at("2026-10-02"), 211300, "EGP", "income", "دخل", "تحويل لحظي"));
        LocalDate nominal = MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, LocalDate.of(2026, 10, 20))[0];
        check(nominal.equals(LocalDate.of(2026, 9, 30)), "nominal month-end start");
        check(LocalDate.of(2026, 9, 26).equals(MoneyFlow.salaryArrival(early, nominal, 2250000, "EGP", ZONE, LocalDate.of(2026, 10, 20))),
                "the salary that landed four days early starts the period");
        check(MoneyFlow.salaryArrival(early, nominal, 0, "EGP", ZONE, LocalDate.of(2026, 10, 20)) == null, "no declared salary: never guessed");
        check(MoneyFlow.salaryArrival(early, nominal, 20000000, "EGP", ZONE, LocalDate.of(2026, 10, 20)) == null, "credits below half the declared salary do not count");
        check(MoneyFlow.salaryArrival(early, nominal, 2250000, "USD", ZONE, LocalDate.of(2026, 10, 20)) == null, "other currency ignored");
        check(MoneyFlow.salaryArrival(early, LocalDate.of(2026, 10, 31), 2250000, "EGP", ZONE, LocalDate.of(2026, 10, 20)) == null,
                "the coming pay day's window is still in the future");
        check(MoneyFlow.salaryArrival(early, nominal, 2250000, "EGP", ZONE, LocalDate.of(2026, 9, 25)) == null, "nothing landed yet by the 25th");
        // With the snapped period the instant transfer on the (actual) pay day is the salary, not a transfer in.
        MoneyFlow.Statement snapped = MoneyFlow.statement(early, new LocalDate[]{LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 21)}, ZONE, "EGP");
        check(snapped.salary == 5600000 && snapped.salaryWhen == at("2026-09-26") && snapped.transfersIn == 211300 && snapped.income == 5811300,
                "a transfer that landed on the pay day is the salary: " + snapped.salary + " / " + snapped.transfersIn);
        check(snapped.out == 5060000 && snapped.remaining == 5811300 - 5060000, "the installment on the 30th is inside the snapped period");
        // The shared period call: snapped to the 26th now; a salary that lands early for the next pay day starts the next period.
        MoneyFlow.ItemsBetween source = (from, to) -> {
            List<MoneyFlow.Item> inside = new ArrayList<>();
            long a = from.atStartOfDay(ZONE).toInstant().toEpochMilli(), b = to.atStartOfDay(ZONE).toInstant().toEpochMilli();
            for (MoneyFlow.Item item : early) if (item.when >= a && item.when < b) inside.add(item);
            return inside;
        };
        LocalDate[] shared = MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, 2250000, "EGP", ZONE, LocalDate.of(2026, 10, 20), source);
        check(shared[0].equals(LocalDate.of(2026, 9, 26)) && shared[1].equals(LocalDate.of(2026, 10, 21)), "shared period snaps to the 26th: " + shared[0]);
        check(MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, 0, "EGP", ZONE, LocalDate.of(2026, 10, 20), source)[0].equals(LocalDate.of(2026, 9, 30)), "no declared salary: nominal");
        check(MoneyFlow.statementPeriod(0, 2250000, "EGP", ZONE, LocalDate.of(2026, 10, 20), source)[0].equals(LocalDate.of(2026, 10, 1)), "no pay day: calendar month");
        early.add(new MoneyFlow.Item(5, at("2026-10-27"), 5600000, "EGP", "income", "دخل", "تحويل لحظي"));
        LocalDate[] rolled = MoneyFlow.statementPeriod(DailyAllowance.MONTH_END, 2250000, "EGP", ZONE, LocalDate.of(2026, 10, 28), source);
        check(rolled[0].equals(LocalDate.of(2026, 10, 27)) && rolled[1].equals(LocalDate.of(2026, 10, 29)), "an early salary for 31 October starts the new period on the 27th: " + rolled[0]);
        // A wage credit on the pay day still beats a larger transfer that landed the same day.
        List<MoneyFlow.Item> sameDay = new ArrayList<>();
        sameDay.add(new MoneyFlow.Item(1, at("2026-09-26"), 3000000, "EGP", "income", "دخل", "مرتب"));
        sameDay.add(new MoneyFlow.Item(2, at("2026-09-26"), 3500000, "EGP", "income", "دخل", "تحويل لحظي"));
        MoneyFlow.Statement wage = MoneyFlow.statement(sameDay, new LocalDate[]{LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 21)}, ZONE, "EGP");
        check(wage.salary == 3000000 && wage.transfersIn == 3500000, "wage descriptor on the pay day outranks a same-day transfer");
        System.out.println("Money flow checks passed: " + checked);
    }
}
