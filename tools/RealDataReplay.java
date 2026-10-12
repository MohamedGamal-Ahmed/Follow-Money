package com.mohamedgamal.masareef;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays the repair rules and MoneyFlow definitions on a private, local export of the phone database.
 * Usage: java RealDataReplay tx.tsv sources.tsv YYYY-MM-DD [excludeId,...] [installmentSource:amountMinor] [month] [refunds.tsv]
 * refunds.tsv contains income_id, expense_id, originally linked amount_minor; allocations are read-only.
 * The export never belongs in the repository; this only prints aggregates.
 */
public final class RealDataReplay {
    static final class Tx {
        long id, when, received, amount, offset; String currency, kind, category, source, direction, merchant; Long from, to; boolean edited;
        RepairRules.Row row() { return new RepairRules.Row(id, when, received, amount, currency, kind, category, source, direction, from, to, edited, merchant); }
        MoneyFlow.Item item() { return new MoneyFlow.Item(id, when, Math.max(0,amount-offset), currency, kind, category, merchant); }
    }

    public static void main(String[] args) throws Exception {
        ZoneId zone = ZoneId.of("Africa/Cairo");
        List<Tx> txs = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            Tx t = new Tx();
            t.id = Long.parseLong(f[0]); t.when = Long.parseLong(f[1]); t.received = Long.parseLong(f[2]); t.amount = Long.parseLong(f[3]);
            t.currency = f[4]; t.kind = f[5]; t.category = f[6]; t.source = f[7]; t.direction = f[8].isEmpty() ? null : f[8];
            t.from = f[9].isEmpty() ? null : Long.parseLong(f[9]); t.to = f[10].isEmpty() ? null : Long.parseLong(f[10]);
            t.edited = "1".equals(f[11]); t.merchant = f[12];
            txs.add(t);
        }
        Map<Long, Long> accountOf = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1);
            if (!f[1].isEmpty()) accountOf.put(Long.parseLong(f[0]), Long.parseLong(f[1]));
        }
        LocalDate today = LocalDate.parse(args[2]);
        LocalDate[] year = MoneyFlow.period(MoneyFlow.Range.YEAR, today, today);
        List<long[]> refunds=new ArrayList<>();
        if(args.length>6 && !args[6].isEmpty()) for(String line:Files.readAllLines(Path.of(args[6]),StandardCharsets.UTF_8)) {
            if(line.isBlank())continue;String[] f=line.split("\t");
            refunds.add(new long[]{Long.parseLong(f[0]),Long.parseLong(f[1]),Long.parseLong(f[2])});
        }
        applyRefunds(txs,refunds);
        report("BEFORE", txs, year, zone);

        Set<Long> excluded = new HashSet<>();
        if (args.length > 3 && !args[3].isEmpty()) for (String id : args[3].split(",")) excluded.add(Long.parseLong(id));
        txs.removeIf(t -> excluded.contains(t.id));
        System.out.println("promotions excluded: " + excluded.size());

        List<RepairRules.Row> rows = new ArrayList<>();
        for (Tx t : txs) rows.add(t.row());
        List<RepairRules.Duplicate> duplicates = RepairRules.crossChannelDuplicates(rows);
        Set<Long> removed = new HashSet<>();
        for (RepairRules.Duplicate d : duplicates) removed.add(d.removeId);
        txs.removeIf(t -> removed.contains(t.id));
        System.out.println("bank+notification duplicates merged: " + duplicates.size());

        rows.clear();
        for (Tx t : txs) rows.add(t.row());
        List<RepairRules.OwnTransfer> own = RepairRules.ownTransfers(rows, accountOf);
        Map<Long, RepairRules.OwnTransfer> byOut = new HashMap<>();
        Set<Long> inLegs = new HashSet<>();
        long fees = 0, principal = 0, inYear = 0;
        long yStart = year[0].atStartOfDay(zone).toInstant().toEpochMilli();
        for (RepairRules.OwnTransfer o : own) { byOut.put(o.outId, o); inLegs.add(o.inId); }
        List<Tx> feeRows = new ArrayList<>();
        for (Tx t : txs) {
            RepairRules.OwnTransfer o = byOut.get(t.id);
            if (o == null) continue;
            if (t.when >= yStart) { inYear++; principal += o.principal; fees += o.fee; }
            t.kind = "transfer"; t.category = "تحويلات"; t.amount = o.principal; t.to = o.toAccount;
            if (o.fee > 0) {
                Tx fee = new Tx(); fee.id = -t.id; fee.when = t.when; fee.received = t.received; fee.amount = o.fee; fee.currency = t.currency;
                fee.kind = "expense"; fee.category = "رسوم تحويل"; fee.source = "fee"; fee.merchant = "رسوم تحويل"; feeRows.add(fee);
            }
        }
        txs.removeIf(t -> inLegs.contains(t.id));
        txs.addAll(feeRows);
        System.out.println("own transfers paired: " + own.size() + " (this year " + inYear + ", moved " + money(principal) + ", fees " + money(fees) + ")");

        rows.clear();
        for (Tx t : txs) rows.add(t.row());
        for (RepairRules.RepeatingTransfer r : RepairRules.repeatingTransfers(rows, 100000, zone))
            System.out.println("repeating transfer suggestion: source " + r.sourceId + " · " + money(r.amountMinor) + " × " + r.ids.size() + " · day " + r.typicalDay);

        if (args.length > 4 && !args[4].isEmpty()) {
            String[] spec = args[4].split(":");
            long source = Long.parseLong(spec[0]), amount = Long.parseLong(spec[1]);
            int n = 0;
            for (Tx t : txs) if ("expense".equals(t.kind) && "تحويلات".equals(t.category) && t.from != null && t.from == source && t.amount == amount) {
                t.category = "أقساط"; t.merchant = "قسط الشقة"; n++;
            }
            System.out.println("confirmed installment rows: " + n);
        }
        applyRefunds(txs,refunds);
        report("AFTER", txs, year, zone);
        List<MoneyFlow.Item> all = new ArrayList<>();
        for (Tx t : txs) all.add(t.item());
        List<Insights.Plan> plans = new ArrayList<>();
        plans.add(new Insights.Plan("قسط الشقة", 5060000, 51, "installment"));
        for (MoneyFlow.Range range : MoneyFlow.Range.values()) {
            LocalDate anchor = range == MoneyFlow.Range.MONTH && args.length > 5 && !args[5].isEmpty() ? LocalDate.parse(args[5]) : today;
            LocalDate[] period = MoneyFlow.period(range, anchor, today);
            System.out.println("\n== INSIGHTS " + range + " " + period[0] + " → " + period[1].minusDays(1) + " ==");
            for (Insights.Insight i : Insights.compute(all, period, range, today, zone, plans, RealDataReplay::money))
                System.out.println("[" + i.tone + "] " + i.title + " = " + i.value + "\n    " + i.detail);
        }
        report("AFTER · LAST 12 MONTHS", txs, MoneyFlow.period(MoneyFlow.Range.LAST_12, today, today), zone);
    }

    private static void applyRefunds(List<Tx> txs,List<long[]> refunds) {
        Map<Long,Tx> byId=new HashMap<>();for(Tx tx:txs){tx.offset=0;byId.put(tx.id,tx);}
        refunds.sort(java.util.Comparator.<long[]>comparingLong(link->byId.containsKey(link[0])?byId.get(link[0]).when:Long.MIN_VALUE)
                .thenComparingLong(link->link[0]));
        Map<Long,Long> remaining=new HashMap<>();
        for(long[] link:refunds) {
            Tx income=byId.get(link[0]),expense=byId.get(link[1]);
            if(income==null || expense==null || !"income".equals(income.kind) || !"expense".equals(expense.kind) ||
                    !income.currency.equals(expense.currency))continue;
            long left=remaining.getOrDefault(expense.id,expense.amount);
            long applied=Math.max(0,Math.min(link[2],Math.min(income.amount,left)));
            income.offset+=applied;expense.offset+=applied;remaining.put(expense.id,Math.max(0,left-applied));
        }
    }

    private static void report(String title, List<Tx> txs, LocalDate[] period, ZoneId zone) {
        List<MoneyFlow.Item> items = new ArrayList<>();
        for (Tx t : txs) items.add(t.item());
        MoneyFlow.Summary s = MoneyFlow.summarize(items, period, zone).get("EGP");
        System.out.println("\n== " + title + " · " + period[0] + " → " + period[1].minusDays(1) + " · EGP ==");
        for (MoneyFlow.Bucket b : MoneyFlow.Bucket.values())
            System.out.printf("%-22s %16s  (%d)%n", MoneyFlow.label(b), money(s.of(b)), s.countOf(b));
        System.out.printf("%-22s %16s  (%d)%n", "إجمالي الخارج", money(s.totalOut()), s.outCount());
        System.out.println("living average " + money(s.livingAverage()) + " · median " + money(s.livingMedian()) +
                " · uncategorized " + s.uncategorized + " = " + money(s.uncategorizedAmount));
        MoneyFlow.MonthPeak peak = s.peak();
        if (peak != null) System.out.println("peak month " + peak.month + " " + money(peak.total) + " vs avg " + money(peak.othersAverage) +
                " · driver " + (peak.driver == null ? "-" : MoneyFlow.label(peak.driver)) + " +" + money(peak.driverGap));
        StringBuilder months = new StringBuilder("months:");
        for (java.time.YearMonth m : s.byMonth.keySet()) months.append(' ').append(m.getMonthValue()).append('=').append(s.monthOut(m) / 100);
        System.out.println(months);
    }

    private static String money(long minor) { return String.format(java.util.Locale.ROOT, "%,.2f", minor / 100.0); }
}
