package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-language findings computed from the same MoneyFlow buckets as every other number in the app.
 * Each finding states its basis (period, count) so it can be checked against the transactions it opens.
 * Pure Java: no Android types, unit-tested in tools/InsightsTest.java.
 */
final class Insights {
    enum Tone { GOOD, WARN, INFO }
    enum Action { NONE, LIVING, COMMITMENT, CATEGORY, MERCHANT, TRANSACTION }

    static final class Insight {
        final String key, title, value, detail;
        final Tone tone;
        final Action action;
        final String target;     // category or merchant for the action
        final long transactionId;
        Insight(String key, String title, String value, String detail, Tone tone, Action action, String target, long transactionId) {
            this.key = key; this.title = title; this.value = value; this.detail = detail; this.tone = tone;
            this.action = action; this.target = target; this.transactionId = transactionId;
        }
    }

    /** An active installment/bill/subscription: what is still owed on it. */
    static final class Plan {
        final String name; final long amountMinor; final Integer remainingPayments; final String kind;
        Plan(String name, long amountMinor, Integer remainingPayments, String kind) {
            this.name = name; this.amountMinor = amountMinor; this.remainingPayments = remainingPayments; this.kind = kind;
        }
    }

    interface Money { String format(long minor); }

    private static final String[] DAY_NAMES = {"الاتنين", "التلات", "الأربع", "الخميس", "الجمعة", "السبت", "الحد"};
    static final long SMALL_PURCHASE_MINOR = 10000; // 100.00

    private Insights() {}

    /**
     * @param history EGP items covering at least the 6 months before period end (more is fine).
     * @param period  the period on screen [start, endExclusive).
     */
    static List<Insight> compute(List<MoneyFlow.Item> history, LocalDate[] period, MoneyFlow.Range range, LocalDate today,
                                 ZoneId zone, List<Plan> plans, Money money) {
        List<Insight> out = new ArrayList<>();
        Map<String, MoneyFlow.Summary> now = MoneyFlow.summarize(history, period, zone);
        MoneyFlow.Summary s = now.get("EGP");
        if (s == null || s.isEmpty()) return out;

        // Monthly living totals for the 6 complete months before the period, for baselines.
        YearMonth firstMonth = YearMonth.from(period[0]);
        Map<YearMonth, Long> livingByMonth = new HashMap<>();
        Map<YearMonth, long[]> bucketsByMonth = new HashMap<>();
        for (MoneyFlow.Item item : history) {
            if (!"EGP".equals(item.currency)) continue;
            YearMonth m = YearMonth.from(Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate());
            bucketsByMonth.computeIfAbsent(m, k -> new long[MoneyFlow.Bucket.values().length])[item.bucket.ordinal()] += item.amountMinor;
            if (item.bucket == MoneyFlow.Bucket.LIVING) livingByMonth.merge(m, item.amountMinor, Long::sum);
        }
        List<Long> baseline = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            YearMonth m = firstMonth.minusMonths(i);
            if (bucketsByMonth.containsKey(m)) baseline.add(livingByMonth.getOrDefault(m, 0L));
        }
        long baselineAverage = baseline.isEmpty() ? 0 : sum(baseline) / baseline.size();

        // 1) Month-end forecast for the running month.
        if (range == MoneyFlow.Range.MONTH && YearMonth.from(period[0]).equals(YearMonth.from(today))) {
            int elapsed = today.getDayOfMonth(), days = YearMonth.from(today).lengthOfMonth();
            long sofar = s.of(MoneyFlow.Bucket.LIVING);
            if (elapsed >= 3 && sofar > 0) {
                long projected = sofar * days / elapsed;
                String detail = "صرفت " + money.format(sofar) + " في " + elapsed + " يوم، يعني " + money.format(sofar / elapsed) + " في اليوم.";
                Tone tone = Tone.INFO;
                if (baselineAverage > 0) {
                    long percent = Math.round(100.0 * (projected - baselineAverage) / baselineAverage);
                    detail += " متوسطك آخر " + baseline.size() + " شهور " + money.format(baselineAverage) +
                            (percent == 0 ? "، يعني ماشي زي العادي." : percent > 0 ? "، يعني أعلى بـ " + percent + "%." : "، يعني أقل بـ " + (-percent) + "%.");
                    tone = percent > 10 ? Tone.WARN : percent < -5 ? Tone.GOOD : Tone.INFO;
                }
                out.add(new Insight("forecast", "على المعدل ده آخر الشهر", "≈ " + money.format(projected), detail, tone, Action.LIVING, null, 0));
            }
        }

        // 2) Compared with your usual month (past periods and longer ranges).
        if (range != MoneyFlow.Range.MONTH || !YearMonth.from(period[0]).equals(YearMonth.from(today))) {
            List<YearMonth> months = MoneyFlow.months(period);
            int full = 0; long total = 0;
            for (YearMonth m : months) if (!m.equals(YearMonth.from(today))) { full++; total += livingByMonth.getOrDefault(m, 0L); }
            if (range != MoneyFlow.Range.MONTH && full >= 2) {
                long average = total / full;
                YearMonth cheapest = null, priciest = null;
                for (YearMonth m : months) {
                    if (m.equals(YearMonth.from(today)) || !bucketsByMonth.containsKey(m)) continue;
                    long v = livingByMonth.getOrDefault(m, 0L);
                    if (cheapest == null || v < livingByMonth.getOrDefault(cheapest, 0L)) cheapest = m;
                    if (priciest == null || v > livingByMonth.getOrDefault(priciest, 0L)) priciest = m;
                }
                if (cheapest != null && priciest != null && !cheapest.equals(priciest))
                    out.add(new Insight("usual", "شهرك العادي في الصرف اليومي", money.format(average),
                            "متوسط " + full + " شهر كامل. أقل شهر " + monthName(cheapest) + " (" + money.format(livingByMonth.getOrDefault(cheapest, 0L)) +
                                    ")، وأعلى شهر " + monthName(priciest) + " (" + money.format(livingByMonth.getOrDefault(priciest, 0L)) + ").",
                            Tone.INFO, Action.LIVING, null, 0));
            }
        }

        // 3) What is left after everything that went out.
        long inflow = s.of(MoneyFlow.Bucket.INFLOW), totalOut = s.totalOut();
        boolean running = !today.isBefore(period[0]) && today.isBefore(period[1].minusDays(1)) || period[1].equals(today.plusDays(1));
        if (inflow > 0) {
            long left = inflow - totalOut;
            long rate = Math.round(100.0 * left / inflow);
            out.add(new Insight("net", (left >= 0 ? "فاضل من اللي دخل" : "خرج أكتر من اللي دخل") + (running ? " لحد النهارده" : ""),
                    (left >= 0 ? "" : "−") + money.format(Math.abs(left)),
                    "دخل " + money.format(inflow) + " وخرج " + money.format(totalOut) + (left >= 0 ? "، يعني فاضل " + rate + "% منه." :
                            "، والفرق اتغطى من رصيد قديم، أو فيه دخل مش متسجل من رسايل البنك.") +
                            (s.countOf(MoneyFlow.Bucket.OPEN_TRANSFER) > 0 ? " الحساب ده هيدق أكتر لما تحدد غرض التحويلات." : ""),
                    left >= 0 ? (rate >= 15 ? Tone.GOOD : Tone.INFO) : Tone.WARN, Action.NONE, null, 0));
        }

        // 4) Commitment burden and what is still owed.
        long commitments = s.of(MoneyFlow.Bucket.COMMITMENT);
        if (commitments > 0 && inflow > 0) {
            long share = Math.round(100.0 * commitments / inflow);
            out.add(new Insight("burden", "الأقساط والالتزامات من اللي دخل", share + "%",
                    money.format(commitments) + " التزامات من " + money.format(inflow) + " دخلوا في نفس الفترة." +
                            (share >= 40 ? " النسبة عالية؛ أي قسط جديد هيضغط على الصرف اليومي." : ""),
                    share >= 40 ? Tone.WARN : share <= 25 ? Tone.GOOD : Tone.INFO, Action.COMMITMENT, null, 0));
        }
        long owed = 0; int counted = 0; Plan longest = null;
        for (Plan plan : plans) {
            if (plan.remainingPayments == null || plan.remainingPayments <= 0) continue;
            owed += plan.amountMinor * plan.remainingPayments; counted++;
            if (longest == null || plan.amountMinor * plan.remainingPayments > longest.amountMinor * longest.remainingPayments) longest = plan;
        }
        if (owed > 0)
            out.add(new Insight("owed", "لسه عليك في الأقساط", money.format(owed),
                    counted + " خطة بعدد أقساط معروف." + (longest == null ? "" : " أكبرها " + longest.name + ": " + longest.remainingPayments +
                            " قسط × " + money.format(longest.amountMinor) + "."), Tone.INFO, Action.COMMITMENT, null, 0));

        // 5) Repeating payments to the same merchant: the yearly cost of subscriptions and habits.
        Map<String, Set<YearMonth>> merchantMonths = new HashMap<>();
        Map<String, Long> merchantTotal = new HashMap<>();
        LocalDate yearAgo = period[1].minusMonths(12);
        long yearAgoMs = yearAgo.atStartOfDay(zone).toInstant().toEpochMilli(), endMs = period[1].atStartOfDay(zone).toInstant().toEpochMilli();
        for (MoneyFlow.Item item : history) {
            if (!"EGP".equals(item.currency) || item.bucket != MoneyFlow.Bucket.LIVING && item.bucket != MoneyFlow.Bucket.COMMITMENT) continue;
            if (item.when < yearAgoMs || item.when >= endMs || ExpenseParser.genericMerchant(item.merchant) || "سحب نقدي".equals(item.category) ||
                    "أقساط".equals(item.category)) continue; // installments have their own line above
            String key = item.merchant.trim();
            merchantMonths.computeIfAbsent(key, k -> new HashSet<>()).add(YearMonth.from(Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate()));
            merchantTotal.merge(key, item.amountMinor, Long::sum);
        }
        List<String> habits = new ArrayList<>();
        long habitsTotal = 0;
        for (Map.Entry<String, Set<YearMonth>> e : merchantMonths.entrySet())
            if (e.getValue().size() >= 4) { habits.add(e.getKey()); habitsTotal += merchantTotal.get(e.getKey()); }
        habits.sort((a, b) -> Long.compare(merchantTotal.get(b), merchantTotal.get(a)));
        if (habits.size() >= 2) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(3, habits.size()); i++)
                names.append(i == 0 ? "" : "، ").append(UiNames.short_(habits.get(i))).append(" ").append(money.format(merchantTotal.get(habits.get(i))));
            out.add(new Insight("habits", "حاجات بتدفعها كل شهر تقريبًا", money.format(habitsTotal) + " في السنة",
                    habits.size() + " جهة غير الأقساط اتدفع لها في 4 شهور أو أكتر خلال آخر 12 شهر: " + names + ".", Tone.INFO, Action.MERCHANT, habits.get(0), 0));
        }

        // 6) Unusual payments: far above what that category normally costs.
        Map<String, List<Long>> categoryAmounts = new HashMap<>();
        long sixMonthsMs = period[1].minusMonths(6).atStartOfDay(zone).toInstant().toEpochMilli();
        for (MoneyFlow.Item item : history)
            if ("EGP".equals(item.currency) && item.bucket == MoneyFlow.Bucket.LIVING && item.when >= sixMonthsMs && item.when < endMs)
                categoryAmounts.computeIfAbsent(item.category, k -> new ArrayList<>()).add(item.amountMinor);
        MoneyFlow.Item unusual = null; long unusualMedian = 0;
        for (MoneyFlow.Item item : s.outflows) {
            if (item.bucket != MoneyFlow.Bucket.LIVING || MoneyFlow.UNCATEGORIZED.equals(item.category) || "سحب نقدي".equals(item.category)) continue;
            List<Long> values = categoryAmounts.get(item.category);
            if (values == null || values.size() < 5) continue;
            long median = median(values);
            if (median > 0 && item.amountMinor >= 4 * median && item.amountMinor >= 50000 &&
                    (unusual == null || item.amountMinor * 1.0 / median > unusual.amountMinor * 1.0 / unusualMedian)) { unusual = item; unusualMedian = median; }
        }
        if (unusual != null)
            out.add(new Insight("unusual", "صرف مش معتاد", money.format(unusual.amountMinor),
                    UiNames.short_(unusual.merchant) + " في " + unusual.category + "، حوالي " + Math.round(unusual.amountMinor * 1.0 / unusualMedian) +
                            " أضعاف المعتاد في التصنيف ده (" + money.format(unusualMedian) + ").", Tone.WARN, Action.TRANSACTION, null, unusual.id));

        // 7) Small purchases add up.
        long small = 0; int smallCount = 0;
        for (MoneyFlow.Item item : s.outflows)
            if (item.bucket == MoneyFlow.Bucket.LIVING && item.amountMinor < SMALL_PURCHASE_MINOR && !"رسوم تحويل".equals(item.category)) { small += item.amountMinor; smallCount++; }
        long living = s.of(MoneyFlow.Bucket.LIVING);
        if (smallCount >= 10 && living > 0)
            out.add(new Insight("small", "المصاريف الصغيرة", money.format(small),
                    smallCount + " عملية أقل من " + money.format(SMALL_PURCHASE_MINOR) + "، يعني " + Math.round(100.0 * small / living) +
                            "% من الصرف اليومي و" + Math.round(100.0 * smallCount / Math.max(1, s.countOf(MoneyFlow.Bucket.LIVING))) + "% من عدد العمليات.",
                    Tone.INFO, Action.LIVING, null, 0));

        // 8) Cash: spent, but on what is unknown.
        long cash = s.livingByCategory.getOrDefault("سحب نقدي", 0L);
        if (cash > 0 && living > 0) {
            long share = Math.round(100.0 * cash / living);
            if (share >= 10)
                out.add(new Insight("cash", "سحب كاش", share + "% من صرفك",
                        money.format(cash) + " اتسحبوا كاش، فمش باين اتصرفوا في إيه. لو بتسجل المهم منهم يدوي التحليل هيبقى أدق.",
                        share >= 30 ? Tone.WARN : Tone.INFO, Action.CATEGORY, "سحب نقدي", 0));
        }

        // 9) Which day of the week costs the most.
        long[] byWeekday = new long[7]; Set<LocalDate> activeDays = new HashSet<>(); int[] daysPerWeekday = new int[7];
        for (MoneyFlow.Item item : s.outflows) {
            if (item.bucket != MoneyFlow.Bucket.LIVING) continue;
            LocalDate d = Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate();
            byWeekday[d.getDayOfWeek().getValue() - 1] += item.amountMinor;
        }
        for (LocalDate d = period[0]; d.isBefore(period[1]) && !d.isAfter(today); d = d.plusDays(1)) { daysPerWeekday[d.getDayOfWeek().getValue() - 1]++; activeDays.add(d); }
        if (activeDays.size() >= 21) {
            int top = 0; long topAverage = 0, all = 0;
            for (int i = 0; i < 7; i++) {
                long average = daysPerWeekday[i] == 0 ? 0 : byWeekday[i] / daysPerWeekday[i];
                all += byWeekday[i];
                if (average > topAverage) { topAverage = average; top = i; }
            }
            long dayAverage = all / activeDays.size();
            if (dayAverage > 0 && topAverage >= dayAverage * 13 / 10)
                out.add(new Insight("weekday", "أغلى يوم في الأسبوع", "يوم " + DAY_NAMES[top],
                        "متوسط " + money.format(topAverage) + " مقابل " + money.format(dayAverage) + " لليوم العادي (" +
                                Math.round(100.0 * (topAverage - dayAverage) / dayAverage) + "% أكتر).", Tone.INFO, Action.LIVING, null, 0));
        }

        // 10) The category that grew the most versus the previous period of the same length.
        if (range == MoneyFlow.Range.MONTH) {
            LocalDate[] previous = {period[0].minusMonths(1), period[0]};
            MoneyFlow.Summary p = MoneyFlow.summarize(history, previous, zone).get("EGP");
            boolean current = YearMonth.from(period[0]).equals(YearMonth.from(today));
            if (p != null && !current) {
                String grew = null; long grewBy = 0;
                for (Map.Entry<String, Long> e : s.livingByCategory.entrySet()) {
                    if (MoneyFlow.UNCATEGORIZED.equals(e.getKey())) continue;
                    long delta = e.getValue() - p.livingByCategory.getOrDefault(e.getKey(), 0L);
                    if (delta > grewBy) { grewBy = delta; grew = e.getKey(); }
                }
                if (grew != null && grewBy >= 50000)
                    out.add(new Insight("grew", "أكتر تصنيف زاد عن الشهر اللي فات", grew,
                            "زاد " + money.format(grewBy) + " (من " + money.format(p.livingByCategory.getOrDefault(grew, 0L)) + " لـ " +
                                    money.format(s.livingByCategory.get(grew)) + ").", Tone.WARN, Action.CATEGORY, grew, 0));
            }
        }

        // 11) How reliable these numbers are.
        int open = s.countOf(MoneyFlow.Bucket.OPEN_TRANSFER);
        if (s.uncategorized + open > 0) {
            long unknown = s.uncategorizedAmount + s.of(MoneyFlow.Bucket.OPEN_TRANSFER);
            long base = Math.max(1, totalOut);
            long known = Math.max(0, Math.round(100.0 * (base - unknown) / base));
            out.add(new Insight("coverage", "دقة التحليل", known + "% معروف",
                    money.format(unknown) + " من اللي خرج لسه من غير تصنيف أو غرض (" + (s.uncategorized + open) + " عملية). كل ما تصنّف أكتر الرؤى تبقى أدق.",
                    known >= 85 ? Tone.GOOD : Tone.WARN, Action.NONE, null, 0));
        }
        return out;
    }

    private static long sum(List<Long> values) { long t = 0; for (long v : values) t += v; return t; }

    static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values); Collections.sort(sorted);
        int m = sorted.size() / 2;
        return sorted.isEmpty() ? 0 : sorted.size() % 2 == 1 ? sorted.get(m) : (sorted.get(m - 1) + sorted.get(m)) / 2;
    }

    private static final String[] MONTHS = {"يناير","فبراير","مارس","أبريل","مايو","يونيو","يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"};
    static String monthName(YearMonth m) { return MONTHS[m.getMonthValue() - 1] + " " + m.getYear(); }

    /** Short display names for merchants inside sentences. */
    static final class UiNames {
        static String short_(String name) {
            String n = name == null ? "" : name.trim();
            return n.length() > 22 ? n.substring(0, 22).trim() + "…" : n;
        }
    }
}
