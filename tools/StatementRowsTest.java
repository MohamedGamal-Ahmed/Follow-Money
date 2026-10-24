package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StatementRowsTest {
    private static int checks;
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); checks++; }
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Africa/Cairo"); LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate[] period = {start, start.plusMonths(1)};
        long when = start.atStartOfDay(zone).toInstant().toEpochMilli();
        List<MoneyFlow.Item> items = new ArrayList<>(Arrays.asList(
                new MoneyFlow.Item(1, when, 150000, "EGP", "income", "دخل", "راتب"),
                new MoneyFlow.Item(2, when + 86400000, 832000, "EGP", "income", "دخل", "InstaPay"),
                new MoneyFlow.Item(3, when, 4513548, "EGP", "expense", "تحويلات", "تحويل"),
                new MoneyFlow.Item(4, when, 3100000, "EGP", "expense", "سحب نقدي", "ATM"),
                new MoneyFlow.Item(5, when, 1420934, "EGP", "expense", "مشتريات", "HSBC Market"),
                new MoneyFlow.Item(6, when, 9499, "EGP", "expense", "أقساط", "قسط"),
                new MoneyFlow.Item(7, when, 900, "EGP", "expense", "مشتريات", "كاش", false, true),
                new MoneyFlow.Item(8, when, 100000, "EGP", "transfer", "تحويلات", "بين حساباتي")));
        MoneyFlow.Statement s = MoneyFlow.statement(items, period, zone, "EGP");
        List<StatementRows.Row> out = StatementRows.out(s);
        check(out.size() == 4, "Four positive components");
        long sum = 0, previous = Long.MAX_VALUE;
        for (StatementRows.Row row : out) { check(row.amount <= previous, "Largest first"); previous = row.amount; sum = Math.addExact(sum, row.amount); }
        check(sum == s.out && sum == 9043981, "Out components sum exactly in piasters; cash spending excluded");
        check(out.get(0).bucket == MoneyFlow.Bucket.OPEN_TRANSFER && out.get(1).bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL, "Sorted membership retained");
        List<StatementRows.Row> in = StatementRows.in(s, items, zone); sum = 0;
        for (StatementRows.Row row : in) {
            long rows = items.stream().filter(i -> Arrays.stream(row.ids).anyMatch(id -> id == i.id)).mapToLong(i -> i.amountMinor).sum();
            check(rows == row.amount, "Income drill-down equals its component"); sum = Math.addExact(sum, row.amount);
        }
        check(sum == s.income && in.get(0).ids.length == 1, "Income components and transfer count");
        check(StatementRows.income(10000, 3000, 2000, new long[]{1}, new long[]{2}).get(2).amount == 5000, "Other income residual");
        check(StatementRows.income(5000, 3000, 2000, new long[]{1}, new long[]{2}).size() == 2, "No zero residual");
        check("أقل من 1%".equals(StatementRows.percent(1, 101)), "Below one percent");
        check("1%".equals(StatementRows.percent(1, 100)), "Exactly one percent");
        check("50%".equals(StatementRows.percent(1, 2)), "Percentage rounding");
        check("0%".equals(StatementRows.percent(0, 0)) && StatementRows.share(1, 0) == 0, "No division by zero");
        check(StatementRows.share(50, 100) == .5, "Common share scale");
        check("لسه مفيش حركة".equals(StatementRows.verdict(0, 0)), "No movements");
        check("مفيش دخل مسجل في الفترة".equals(StatementRows.verdict(0, 100)), "No income");
        check("الخارج أكبر من الداخل".equals(StatementRows.verdict(100, 101)), "Out exceeds in");
        check("اتصرف 72% من الداخل".equals(StatementRows.verdict(100, 72)), "Normal verdict");
        check("اتصرف 0% من الداخل".equals(StatementRows.verdict(100, 0)), "Income only");
        check("اتصرف 100% من الداخل".equals(StatementRows.verdict(100, 100)), "Equal totals");
        long[] ids = StatementRows.contributors(s, items, zone);
        long signed = items.stream().filter(i -> Arrays.stream(ids).anyMatch(id -> id == i.id))
                .mapToLong(i -> i.bucket == MoneyFlow.Bucket.INFLOW ? i.amountMinor : -i.amountMinor).sum();
        check(signed == s.remaining, "Difference drill-down adds up with signs");
        MoneyFlow.Statement empty = MoneyFlow.statement(new ArrayList<>(), period, zone, "EGP");
        check(StatementRows.out(empty).isEmpty() && StatementRows.in(empty, items, zone).isEmpty(), "No phantom components");
        List<MoneyFlow.Item> single = Arrays.asList(new MoneyFlow.Item(9, when, 123456789, "EGP", "expense", "رسوم", "English Bank"));
        MoneyFlow.Statement big = MoneyFlow.statement(single, period, zone, "EGP");
        check(StatementRows.out(big).size() == 1 && StatementRows.out(big).get(0).amount == 123456789, "Single large amount unchanged");
        check(s.unlinkedTransfer > 0, "Unlinked transfer warning input retained");
        check("أقل من 1%".equals(StatementRows.percent(1, Long.MAX_VALUE)), "Large totals do not overflow percent comparison");
        check("تحويل واحد".equals(StatementRows.transfers(1)), "One transfer");
        check("تحويلين".equals(StatementRows.transfers(2)), "Dual transfers");
        check("3 تحويلات".equals(StatementRows.transfers(3)) && "10 تحويلات".equals(StatementRows.transfers(10)), "3–10 plural");
        check("11 تحويل".equals(StatementRows.transfers(11)) && "100 تحويل".equals(StatementRows.transfers(100)), "11+ singular");
        check("0 تحويل".equals(StatementRows.transfers(0)), "Zero never reads as plural");
        check("تحويل واحد".equals(in.get(0).detail), "Transfer component detail uses the counted noun");
        check("عملية واحدة".equals(StatementRows.operations(1)) && "عمليتين".equals(StatementRows.operations(2))
                && "5 عمليات".equals(StatementRows.operations(5)) && "12 عملية".equals(StatementRows.operations(12)), "Operation counts");
        check("اتصنفت 3 عمليات، واتعلّمت إن عملية واحدة اتراجعت".equals(StatementRows.classified(3, 1, 0)), "Toast keeps non-zero parts only");
        check("اتصنفت عمليتين، واتعلّمت إن 3 عمليات اتراجعوا، وواحدة ما اتغيرتش".equals(StatementRows.classified(2, 3, 1)), "Toast with all parts");
        check("اتصنفت عملية واحدة".equals(StatementRows.classified(1, 0, 0)), "Toast with one part");
        check("عمليتين ما اتغيروش".equals(StatementRows.classified(0, 0, 2)), "Only unchanged rows");
        check("اتصنفت 11 عملية، واتنين ما اتغيروش".equals(StatementRows.classified(11, 0, 2)), "Unchanged pair");
        check("مفيش عمليات اتغيرت".equals(StatementRows.classified(0, 0, 0)), "Nothing happened");
        check(!StatementRows.classified(4, 0, 0).contains("0"), "No zero counts in the toast");
        System.out.println("Statement rows checks passed: " + checks);
    }
}
