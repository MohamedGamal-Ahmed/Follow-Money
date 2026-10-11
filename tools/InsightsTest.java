package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Findings are computed from MoneyFlow buckets and say what they are based on. */
public final class InsightsTest {
    private static int checked;
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }
    private static long at(LocalDate day) { return day.atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli(); }

    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 10, 11);
        List<MoneyFlow.Item> items = new ArrayList<>();
        long id = 1;
        // Six past months: 300/day food on weekdays, 900 on Saturdays, a monthly Netflix and an installment.
        for (LocalDate d = LocalDate.of(2026, 4, 1); d.isBefore(today.plusDays(1)); d = d.plusDays(1)) {
            long food = d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY ? 90000 : 30000;
            items.add(new MoneyFlow.Item(id++, at(d), food, "EGP", "expense", "أكل وشرب", "Cafe"));
            if (d.getDayOfMonth() == 5) {
                items.add(new MoneyFlow.Item(id++, at(d), 20000, "EGP", "expense", "اشتراكات", "Netflix"));
                items.add(new MoneyFlow.Item(id++, at(d), 5060000, "EGP", "expense", "أقساط", "قسط الشقة"));
                items.add(new MoneyFlow.Item(id++, at(d), 8000000, "EGP", "income", "دخل", "راتب"));
            }
            if (d.getDayOfMonth() % 3 == 0) items.add(new MoneyFlow.Item(id++, at(d), 5000, "EGP", "expense", "بقالة", "Kiosk"));
        }
        items.add(new MoneyFlow.Item(999, at(LocalDate.of(2026, 9, 20)), 600000, "EGP", "expense", "أكل وشرب", "Big Dinner"));
        List<Insights.Plan> plans = new ArrayList<>();
        plans.add(new Insights.Plan("قسط الشقة", 5060000, 51, "installment"));
        Insights.Money money = minor -> String.format(java.util.Locale.ROOT, "%,.2f", minor / 100.0);

        Map<String, Insights.Insight> month = byKey(Insights.compute(items, MoneyFlow.period(MoneyFlow.Range.MONTH, today, today),
                MoneyFlow.Range.MONTH, today, ZONE, plans, money));
        check(month.containsKey("forecast"), "running month has a forecast");
        check(month.get("forecast").value.contains("≈"), "forecast is labelled as an estimate");
        check(month.containsKey("owed") && month.get("owed").value.equals("2,580,600.00"), "51 × 50,600 still owed");
        check(month.get("net").title.contains("لحد النهارده"), "running period says so");

        Map<String, Insights.Insight> september = byKey(Insights.compute(items, MoneyFlow.period(MoneyFlow.Range.MONTH, LocalDate.of(2026, 9, 1), today),
                MoneyFlow.Range.MONTH, today, ZONE, plans, money));
        check(!september.containsKey("forecast"), "no forecast for a finished month");
        check(september.containsKey("unusual") && september.get("unusual").transactionId == 999, "the 6,000 dinner is unusual");
        check(september.containsKey("burden") && september.get("burden").value.equals("64%"), "(50,600 + 200) / 80,000 = 64%, got " +
                (september.containsKey("burden") ? september.get("burden").value : "none"));

        Map<String, Insights.Insight> year = byKey(Insights.compute(items, MoneyFlow.period(MoneyFlow.Range.YEAR, today, today),
                MoneyFlow.Range.YEAR, today, ZONE, plans, money));
        check(year.containsKey("weekday") && year.get("weekday").value.contains("السبت"), "Saturday is the priciest day");
        check(year.containsKey("habits") && !year.get("habits").detail.contains("قسط الشقة"), "habits exclude installments");
        check(year.containsKey("usual"), "usual month over a year");
        check(Insights.median(java.util.Arrays.asList(1L, 9L, 3L, 7L)) == 5, "median");
        System.out.println("Insight checks passed: " + checked);
    }

    private static Map<String, Insights.Insight> byKey(List<Insights.Insight> list) {
        Map<String, Insights.Insight> map = new HashMap<>();
        for (Insights.Insight i : list) map.put(i.key, i);
        return map;
    }
}
