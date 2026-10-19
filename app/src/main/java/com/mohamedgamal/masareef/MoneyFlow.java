package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The single set of money definitions shared by every screen, widget and notification.
 *
 * Every transaction falls in exactly one bucket:
 *  LIVING          day-to-day spending and bank fees («صرف»)
 *  COMMITMENT      installments, bills and subscriptions
 *  OPEN_TRANSFER   money sent to other people whose purpose is not set yet
 *  INFLOW          money that came in (not necessarily earned income)
 *  INTERNAL        moves between the user's own accounts; never spending or income
 *  CASH_WITHDRAWAL cash taken out of a tracked account; money moved, never spending
 */
final class MoneyFlow {
    enum Bucket { LIVING, COMMITMENT, OPEN_TRANSFER, INFLOW, INTERNAL, CASH_WITHDRAWAL }
    enum Range { MONTH, YEAR, LAST_12 }

    static final String OPEN_TRANSFER_CATEGORY = "تحويلات";
    static final String CASH_WITHDRAWAL_CATEGORY = "سحب نقدي";
    /** A separately stated bank fee; it stays a real expense even when it mentions an ATM. */
    static final String BANK_FEE_CATEGORY = "رسوم بنكية";
    static final Set<String> COMMITMENT_CATEGORIES = Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList("أقساط", "فواتير", "اشتراكات")));
    static final String UNCATEGORIZED = "غير مصنف";

    private MoneyFlow() {}

    static Bucket bucket(String kind, String category) {
        if ("transfer".equals(kind)) return Bucket.INTERNAL;
        if ("income".equals(kind)) return Bucket.INFLOW;
        if (OPEN_TRANSFER_CATEGORY.equals(category)) return Bucket.OPEN_TRANSFER;
        if (CASH_WITHDRAWAL_CATEGORY.equals(category)) return Bucket.CASH_WITHDRAWAL;
        if (category != null && COMMITMENT_CATEGORIES.contains(category)) return Bucket.COMMITMENT;
        return Bucket.LIVING;
    }

    /** Only real spending buckets; a cash withdrawal moves money to cash and is not an outflow. */
    static boolean isOutflow(Bucket bucket) {
        return bucket == Bucket.LIVING || bucket == Bucket.COMMITMENT || bucket == Bucket.OPEN_TRANSFER;
    }

    /** Cash moved out of a tracked account. A fee is not a withdrawal, even when it mentions one. */
    static boolean isCashWithdrawal(String kind, String category) {
        return bucket(kind, category) == Bucket.CASH_WITHDRAWAL;
    }

    /** Pseudo-filter for the list: everything the home «مصروفاتك» number adds up (day-to-day spending plus commitments). */
    static final String SPENDING_FILTER = "SPENDING";

    static boolean isSpending(Bucket bucket) {
        return bucket == Bucket.LIVING || bucket == Bucket.COMMITMENT;
    }

    /** True when a bucket passes a list filter; an empty filter passes everything. */
    static boolean matchesFilter(String filter, Bucket bucket) {
        if (filter == null || filter.isEmpty()) return true;
        if (SPENDING_FILTER.equals(filter)) return isSpending(bucket);
        return bucket.name().equals(filter);
    }

    static String filterLabel(String filter) {
        return SPENDING_FILTER.equals(filter) ? "مصروفات" : label(Bucket.valueOf(filter));
    }

    /**
     * Whether a list filter may also show capture history (cancelled or edited attempts) next to the rows.
     * The list behind a home number must add up to that number, so it shows transactions only.
     */
    static boolean listsCaptureHistory(String filter) {
        return !SPENDING_FILTER.equals(filter);
    }

    /** The buckets the home card lists under «حركات غير داخلة في المصروفات», in display order. */
    static final Bucket[] MOVEMENT_BUCKETS = {Bucket.OPEN_TRANSFER, Bucket.CASH_WITHDRAWAL, Bucket.INTERNAL};

    static String label(Bucket bucket) {
        switch (bucket) {
            case LIVING: return "صرف يومي";
            case COMMITMENT: return "أقساط والتزامات";
            case OPEN_TRANSFER: return "تحويلات غرضها مش محدد";
            case INFLOW: return "فلوس دخلت";
            case CASH_WITHDRAWAL: return "سحب نقدي";
            default: return "بين حساباتك";
        }
    }

    /** [start, endExclusive). MONTH is the anchor's calendar month; YEAR starts on 1 January; LAST_12 is 12 calendar months ending this month. */
    static LocalDate[] period(Range range, LocalDate anchor, LocalDate today) {
        switch (range) {
            case YEAR: {
                LocalDate start = anchor.withDayOfYear(1);
                LocalDate end = start.plusYears(1);
                return new LocalDate[]{start, end.isAfter(today.plusDays(1)) ? today.plusDays(1) : end};
            }
            case LAST_12: {
                LocalDate start = today.withDayOfMonth(1).minusMonths(11);
                return new LocalDate[]{start, today.plusDays(1)};
            }
            default: {
                LocalDate start = anchor.withDayOfMonth(1);
                return new LocalDate[]{start, start.plusMonths(1)};
            }
        }
    }

    static List<YearMonth> months(LocalDate[] period) {
        List<YearMonth> result = new ArrayList<>();
        YearMonth last = YearMonth.from(period[1].minusDays(1));
        for (YearMonth m = YearMonth.from(period[0]); !m.isAfter(last); m = m.plusMonths(1)) result.add(m);
        return result;
    }

    static final class Item {
        final long id, when, amountMinor;
        final String currency, kind, category, merchant;
        final Bucket bucket;
        Item(long id, long when, long amountMinor, String currency, String kind, String category, String merchant) {
            if (amountMinor < 0) throw new IllegalArgumentException("Negative amount");
            this.id = id; this.when = when; this.amountMinor = amountMinor; this.currency = currency;
            this.kind = kind; this.category = category; this.merchant = merchant;
            this.bucket = bucket(kind, category);
        }
    }

    /** Totals for one currency over one period. Currencies are never added together. */
    static final class Summary {
        final String currency;
        final long[] total = new long[Bucket.values().length];
        final int[] count = new int[Bucket.values().length];
        final Map<String, Long> livingByCategory = new LinkedHashMap<>();
        /** Day-to-day spending and commitments together, by category: the split of the home «مصروفاتك» number. */
        final Map<String, Long> spendingByCategory = new LinkedHashMap<>();
        final Map<String, Long> commitmentByMerchant = new LinkedHashMap<>();
        final TreeMap<YearMonth, long[]> byMonth = new TreeMap<>();
        final Map<LocalDate, long[]> byDay = new TreeMap<>();
        final List<Item> outflows = new ArrayList<>();
        int uncategorized;
        long uncategorizedAmount;

        Summary(String currency) { this.currency = currency; }

        long of(Bucket bucket) { return total[bucket.ordinal()]; }
        int countOf(Bucket bucket) { return count[bucket.ordinal()]; }
        long totalOut() { return of(Bucket.LIVING) + of(Bucket.COMMITMENT) + of(Bucket.OPEN_TRANSFER); }
        int outCount() { return countOf(Bucket.LIVING) + countOf(Bucket.COMMITMENT) + countOf(Bucket.OPEN_TRANSFER); }
        boolean isEmpty() { for (int c : count) if (c > 0) return false; return true; }

        /** Median of day-to-day spending payments: half of them are at or below this amount. */
        long livingMedian() {
            List<Long> values = new ArrayList<>();
            for (Item item : outflows) if (item.bucket == Bucket.LIVING) values.add(item.amountMinor);
            if (values.isEmpty()) return 0;
            Collections.sort(values);
            int middle = values.size() / 2;
            return values.size() % 2 == 1 ? values.get(middle) : (values.get(middle - 1) + values.get(middle)) / 2;
        }

        long livingAverage() {
            int n = countOf(Bucket.LIVING);
            return n == 0 ? 0 : of(Bucket.LIVING) / n;
        }

        List<Item> largestOutflows(int limit) {
            List<Item> sorted = new ArrayList<>(outflows);
            sorted.sort(Comparator.comparingLong((Item i) -> i.amountMinor).reversed().thenComparingLong(i -> -i.when));
            return sorted.subList(0, Math.min(limit, sorted.size()));
        }

        List<Map.Entry<String, Long>> livingCategories() {
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(livingByCategory.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            return sorted;
        }

        long spending() { return of(Bucket.LIVING) + of(Bucket.COMMITMENT); }

        /** Categories of the spending number, largest first; their values add up to spending(). */
        List<Map.Entry<String, Long>> spendingCategories() {
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(spendingByCategory.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            return sorted;
        }

        long monthOut(YearMonth month) {
            long[] values = byMonth.get(month);
            return values == null ? 0 : values[Bucket.LIVING.ordinal()] + values[Bucket.COMMITMENT.ordinal()] + values[Bucket.OPEN_TRANSFER.ordinal()];
        }

        /** The month with the largest total out, and which bucket explains most of its gap above the other months' average. */
        MonthPeak peak() {
            if (byMonth.size() < 2) return null;
            YearMonth top = null; long topValue = -1;
            for (YearMonth month : byMonth.keySet()) {
                long value = monthOut(month);
                if (value > topValue) { topValue = value; top = month; }
            }
            if (top == null || topValue <= 0) return null;
            int others = byMonth.size() - 1;
            long[] average = new long[Bucket.values().length];
            for (Map.Entry<YearMonth, long[]> entry : byMonth.entrySet()) {
                if (entry.getKey().equals(top)) continue;
                for (int i = 0; i < average.length; i++) average[i] += entry.getValue()[i];
            }
            for (int i = 0; i < average.length; i++) average[i] /= others;
            long[] peak = byMonth.get(top);
            Bucket driver = null; long driverGap = 0;
            for (Bucket bucket : new Bucket[]{Bucket.LIVING, Bucket.COMMITMENT, Bucket.OPEN_TRANSFER}) {
                long gap = peak[bucket.ordinal()] - average[bucket.ordinal()];
                if (gap > driverGap) { driverGap = gap; driver = bucket; }
            }
            long averageOut = average[Bucket.LIVING.ordinal()] + average[Bucket.COMMITMENT.ordinal()] + average[Bucket.OPEN_TRANSFER.ordinal()];
            return new MonthPeak(top, topValue, averageOut, driver, driverGap);
        }
    }

    /**
     * The rolling seven days behind the home card, for one currency.
     * Spending = day-to-day + commitments (after linked refunds); that is the number, the bars and the category split.
     * Open transfers, cash withdrawals and own-account moves are listed apart and never added to it.
     */
    static final class Week {
        final long living, commitment, openTransfer, cash, internal, income;
        final int spendingCount;
        /** Spending per day, oldest first; the seven values add up to spending(). */
        final long[] daily = new long[7];
        final List<Map.Entry<String, Long>> categories;
        Week(Summary s, LocalDate start) {
            living = s == null ? 0 : s.of(Bucket.LIVING);
            commitment = s == null ? 0 : s.of(Bucket.COMMITMENT);
            openTransfer = s == null ? 0 : s.of(Bucket.OPEN_TRANSFER);
            cash = s == null ? 0 : s.of(Bucket.CASH_WITHDRAWAL);
            internal = s == null ? 0 : s.of(Bucket.INTERNAL);
            income = s == null ? 0 : s.of(Bucket.INFLOW);
            spendingCount = s == null ? 0 : s.countOf(Bucket.LIVING) + s.countOf(Bucket.COMMITMENT);
            categories = s == null ? new ArrayList<>() : s.spendingCategories();
            if (s != null) for (int i = 0; i < 7; i++) {
                long[] day = s.byDay.get(start.plusDays(i));
                if (day != null) daily[i] = day[Bucket.LIVING.ordinal()] + day[Bucket.COMMITMENT.ordinal()];
            }
        }
        long spending() { return living + commitment; }
        /** Amount of one movement bucket shown outside the number. */
        long movement(Bucket bucket) {
            switch (bucket) {
                case OPEN_TRANSFER: return openTransfer;
                case CASH_WITHDRAWAL: return cash;
                case INTERNAL: return internal;
                default: return 0;
            }
        }
    }

    /** [today-6, today+1) in the given zone. */
    static LocalDate[] rollingWeek(LocalDate today) {
        return new LocalDate[]{today.minusDays(6), today.plusDays(1)};
    }

    static Week week(List<Item> items, LocalDate today, ZoneId zone, String currency) {
        LocalDate[] period = rollingWeek(today);
        return new Week(summarize(items, period, zone).get(currency), period[0]);
    }

    static final class MonthPeak {
        final YearMonth month;
        final long total, othersAverage, driverGap;
        final Bucket driver;
        MonthPeak(YearMonth month, long total, long othersAverage, Bucket driver, long driverGap) {
            this.month = month; this.total = total; this.othersAverage = othersAverage; this.driver = driver; this.driverGap = driverGap;
        }
    }

    /** Builds per-currency summaries; items outside [start,end) are ignored. */
    static Map<String, Summary> summarize(List<Item> items, LocalDate[] period, ZoneId zone) {
        long start = period[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = period[1].atStartOfDay(zone).toInstant().toEpochMilli();
        Map<String, Summary> result = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.when < start || item.when >= end) continue;
            Summary s = result.computeIfAbsent(item.currency, Summary::new);
            int b = item.bucket.ordinal();
            s.total[b] = Math.addExact(s.total[b], item.amountMinor);
            s.count[b]++;
            LocalDate day = Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate();
            s.byMonth.computeIfAbsent(YearMonth.from(day), k -> new long[Bucket.values().length])[b] += item.amountMinor;
            s.byDay.computeIfAbsent(day, k -> new long[Bucket.values().length])[b] += item.amountMinor;
            if (isOutflow(item.bucket)) s.outflows.add(item);
            if (isSpending(item.bucket)) s.spendingByCategory.merge(item.category == null ? UNCATEGORIZED : item.category, item.amountMinor, Long::sum);
            if (item.bucket == Bucket.LIVING) {
                s.livingByCategory.merge(item.category, item.amountMinor, Long::sum);
                if (UNCATEGORIZED.equals(item.category)) { s.uncategorized++; s.uncategorizedAmount += item.amountMinor; }
            }
            if (item.bucket == Bucket.COMMITMENT) s.commitmentByMerchant.merge(item.merchant, item.amountMinor, Long::sum);
        }
        // Months without activity still appear so yearly bars keep their calendar order.
        for (Summary s : result.values()) for (YearMonth m : months(period)) s.byMonth.computeIfAbsent(m, k -> new long[Bucket.values().length]);
        return result;
    }
}
