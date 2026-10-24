package com.mohamedgamal.masareef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Presentation only: statement amounts remain authoritative, with exact transaction membership for drill-down. */
final class StatementRows {
    static final class Row {
        final String name, detail;
        final long amount;
        final MoneyFlow.Bucket bucket;
        final long[] ids;
        Row(String name, String detail, long amount, MoneyFlow.Bucket bucket, long[] ids) {
            this.name = name; this.detail = detail; this.amount = amount; this.bucket = bucket; this.ids = ids;
        }
    }
    static List<Row> out(MoneyFlow.Statement s) {
        List<Row> rows = new ArrayList<>();
        for (MoneyFlow.Bucket bucket : MoneyFlow.STATEMENT_OUT_BUCKETS) if (s.component(bucket) > 0)
            rows.add(new Row(bucket == MoneyFlow.Bucket.OPEN_TRANSFER ? "تحويلات لناس"
                    : bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL ? "كاش اتسحب" : MoneyFlow.label(bucket),
                    bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL ? "من الحساب للكاش" : "", s.component(bucket), bucket, null));
        rows.sort(Comparator.comparingLong((Row row) -> row.amount).reversed());
        return rows;
    }
    static List<Row> in(MoneyFlow.Statement s, List<MoneyFlow.Item> items, java.time.ZoneId zone) {
        List<Long> salary = new ArrayList<>(), transfers = new ArrayList<>();
        long start = s.start.atStartOfDay(zone).toInstant().toEpochMilli(), end = s.end.atStartOfDay(zone).toInstant().toEpochMilli();
        for (MoneyFlow.Item item : items) if (item.bucket == MoneyFlow.Bucket.INFLOW && s.currency.equals(item.currency)
                && item.when >= start && item.when < end) {
            if (item.id == s.salaryItemId) salary.add(item.id); else transfers.add(item.id);
        }
        return income(s.income, s.salary, s.transfersIn, ids(salary), ids(transfers));
    }
    static List<Row> income(long income, long salary, long transfers, long[] salaryIds, long[] transferIds) {
        List<Row> rows = new ArrayList<>();
        if (transfers > 0) rows.add(new Row("تحويلات وصلتك", transferIds.length + " تحويلات", transfers, MoneyFlow.Bucket.INFLOW, transferIds));
        if (salary > 0) rows.add(new Row("راتب", "", salary, MoneyFlow.Bucket.INFLOW, salaryIds));
        long other = Math.subtractExact(Math.subtractExact(income, salary), transfers);
        if (other > 0) rows.add(new Row("دخل تاني", "", other, MoneyFlow.Bucket.INFLOW, new long[0]));
        return rows;
    }
    private static long[] ids(List<Long> values) { return values.stream().mapToLong(Long::longValue).toArray(); }
    static long[] contributors(MoneyFlow.Statement s, List<MoneyFlow.Item> items, java.time.ZoneId zone) {
        long start = s.start.atStartOfDay(zone).toInstant().toEpochMilli(), end = s.end.atStartOfDay(zone).toInstant().toEpochMilli();
        return items.stream().filter(i -> s.currency.equals(i.currency) && i.when >= start && i.when < end
                && (i.bucket == MoneyFlow.Bucket.INFLOW || MoneyFlow.isStatementOut(i.bucket)
                && (!i.fromCash || i.bucket == MoneyFlow.Bucket.CASH_WITHDRAWAL))).mapToLong(i -> i.id).toArray();
    }
    static double share(long amount, long total) { return total <= 0 ? 0 : (double) amount / total; }
    static String percent(long amount, long total) {
        if (amount > 0 && BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).compareTo(BigDecimal.valueOf(total)) < 0)
            return "أقل من 1%";
        return (total <= 0 ? "0" : BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).toPlainString()) + "%";
    }
    static String verdict(long income, long out) {
        if (income == 0) return out == 0 ? "لسه مفيش حركة" : "مفيش دخل مسجل في الفترة";
        return out > income ? "الخارج أكبر من الداخل" : "اتصرف " + percent(out, income) + " من الداخل";
    }
}
