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

    /** A debit whose destination is one of the user's own credit cards pays purchases already counted: it is internal. */
    static Bucket bucket(String kind, String category, boolean toOwnCard) {
        if (toOwnCard && "expense".equals(kind)) return Bucket.INTERNAL;
        return bucket(kind, category);
    }

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

    /** Pseudo-filter for the list behind the statement's «خرج»: the four buckets isStatementOut() names. */
    static final String OUT_FILTER = "OUT";

    /**
     * What left the user's money in the month statement (owner's decision, 20 October): day-to-day spending,
     * commitments, transfers to people and cash withdrawals. Cash taken out counts once, when withdrawn.
     */
    static boolean isStatementOut(Bucket bucket) {
        return isOutflow(bucket) || bucket == Bucket.CASH_WITHDRAWAL;
    }

    /** True when a bucket passes a list filter; an empty filter passes everything. */
    static boolean matchesFilter(String filter, Bucket bucket) {
        if (filter == null || filter.isEmpty()) return true;
        if (SPENDING_FILTER.equals(filter)) return isSpending(bucket);
        if (OUT_FILTER.equals(filter)) return isStatementOut(bucket);
        return bucket.name().equals(filter);
    }

    static String filterLabel(String filter) {
        return SPENDING_FILTER.equals(filter) ? "مصروفات" : OUT_FILTER.equals(filter) ? "خرج" : label(Bucket.valueOf(filter));
    }

    /**
     * Whether a list filter may also show capture history (cancelled or edited attempts) next to the rows.
     * The list behind a home number must add up to that number, so it shows transactions only.
     */
    static boolean listsCaptureHistory(String filter) {
        return !SPENDING_FILTER.equals(filter) && !OUT_FILTER.equals(filter);
    }

    /** The buckets the home card lists under «حركات غير داخلة في المصروفات», in display order. */
    static final Bucket[] MOVEMENT_BUCKETS = {Bucket.OPEN_TRANSFER, Bucket.CASH_WITHDRAWAL, Bucket.INTERNAL};

    static String label(Bucket bucket) {
        switch (bucket) {
            case LIVING: return "مشتريات ورسوم";
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
        /** Paid from the cash wallet: the money was already counted when it was withdrawn. */
        final boolean fromCash;
        Item(long id, long when, long amountMinor, String currency, String kind, String category, String merchant) {
            this(id, when, amountMinor, currency, kind, category, merchant, false, false);
        }
        /** toOwnCard: a debit whose destination is the user's own credit card (see bucket(kind, category, toOwnCard)). */
        Item(long id, long when, long amountMinor, String currency, String kind, String category, String merchant, boolean toOwnCard, boolean fromCash) {
            if (amountMinor < 0) throw new IllegalArgumentException("Negative amount");
            this.id = id; this.when = when; this.amountMinor = amountMinor; this.currency = currency;
            this.kind = kind; this.category = category; this.merchant = merchant; this.fromCash = fromCash;
            this.bucket = bucket(kind, category, toOwnCard);
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

    /**
     * [start, today+1): from the saved pay day (1–31, or DailyAllowance.MONTH_END for the last day of the month)
     * through today, using the same monthly cycle as the daily allowance; without a pay day, the calendar month.
     */
    static LocalDate[] statementPeriod(int salaryDay, LocalDate today) {
        LocalDate start = salaryDay > 0 && salaryDay <= DailyAllowance.MONTH_END
                ? BudgetPeriod.bounds("monthly", LocalDate.of(2000, 1, Math.min(31, salaryDay)), 30, today)[0]
                : today.withDayOfMonth(1);
        return new LocalDate[]{start, today.plusDays(1)};
    }

    /** The pay day inside a month: the last day for DailyAllowance.MONTH_END, otherwise the day clamped to the month's length. */
    static LocalDate payDayIn(YearMonth month, int salaryDay) {
        return salaryDay == DailyAllowance.MONTH_END ? month.atEndOfMonth() : month.atDay(Math.max(1, Math.min(salaryDay, month.lengthOfMonth())));
    }

    /** Days before and after the nominal pay day in which the salary may actually land (a weekend, a bank holiday, an early run). */
    static final int SALARY_EARLY_DAYS = 7, SALARY_LATE_DAYS = 3;

    /**
     * The day the salary really landed around a nominal pay day, or null when no credit in [payDay−7, payDay+3] ∩ [.., today]
     * reaches half the declared monthly salary. Salaries in Egypt often arrive a few days before the month end and as
     * instant transfers, so every inflow qualifies and the amount, not the descriptor, decides. Nothing is guessed when
     * the user never declared a salary (declaredMinor ≤ 0).
     */
    static LocalDate salaryArrival(List<Item> items, LocalDate payDay, long declaredMinor, String currency, ZoneId zone, LocalDate today) {
        if (declaredMinor <= 0 || payDay == null) return null;
        LocalDate from = payDay.minusDays(SALARY_EARLY_DAYS), to = payDay.plusDays(SALARY_LATE_DAYS);
        if (to.isAfter(today)) to = today;
        if (to.isBefore(from)) return null;
        long floor = Math.max(1, declaredMinor / 2);
        Item best = null; LocalDate bestDay = null;
        for (Item item : items) {
            if (item.bucket != Bucket.INFLOW || !currency.equals(item.currency) || item.amountMinor < floor) continue;
            LocalDate day = Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate();
            if (day.isBefore(from) || day.isAfter(to)) continue;
            if (best == null || item.amountMinor > best.amountMinor) { best = item; bestDay = day; }
        }
        return bestDay;
    }

    /** Reads the flow items in [from, to) for the snapped period: ExpenseDb.flowItems on the device, a list filter in tests. */
    interface ItemsBetween { List<Item> read(LocalDate from, LocalDate to); }

    /**
     * The home and widget statement period: statementPeriod(salaryDay, today) snapped to the day the salary really
     * landed (salaryArrival). A salary that already arrived early for the coming pay day starts the new period at once,
     * so two salaries are never added together; without a pay day or a declared salary the nominal period is returned.
     */
    static LocalDate[] statementPeriod(int salaryDay, long declaredMinor, String currency, ZoneId zone, LocalDate today, ItemsBetween items) {
        LocalDate[] nominal = statementPeriod(salaryDay, today);
        if (salaryDay <= 0 || salaryDay > DailyAllowance.MONTH_END || declaredMinor <= 0) return nominal;
        List<Item> around = items.read(nominal[0].minusDays(SALARY_EARLY_DAYS), today.plusDays(1));
        LocalDate next = payDayIn(YearMonth.from(nominal[0]).plusMonths(1), salaryDay);
        LocalDate early = salaryArrival(around, next, declaredMinor, currency, zone, today);
        if (early != null && early.isAfter(nominal[0])) return new LocalDate[]{early, nominal[1]};
        LocalDate landed = salaryArrival(around, nominal[0], declaredMinor, currency, zone, today);
        return landed == null ? nominal : new LocalDate[]{landed, nominal[1]};
    }

    /** A credit whose descriptor says it was moved to the user, not earned: phone-banking, instant transfers, InstaPay handles. */
    static boolean incomingTransfer(String merchant) {
        if (merchant == null) return false;
        String name = merchant.trim().toLowerCase(java.util.Locale.ROOT);
        return name.contains("phone banking transfer") || name.contains("تحويل لحظي") || name.contains("instapay")
                || name.matches("(?s).*\\bipn\\b.*") || name.contains("تحويل وارد");
    }

    /**
     * The month statement for one currency (owner's decision, 20 October): «دخلك كام، خرج منك كام، باقي كام» from the
     * pay day to today. دخل = INFLOW; خرج = LIVING + COMMITMENT + OPEN_TRANSFER + CASH_WITHDRAWAL; باقي = دخل − خرج.
     * INTERNAL moves and linked refunds (already offset upstream) are in none of the three. A purchase paid from the
     * cash wallet is detail only: its money was counted when withdrawn.
     */
    static final class Statement {
        final String currency;
        final LocalDate start, end;
        final long income, salary, transfersIn, living, commitment, openTransfer, cash, out, remaining, internal, cashSpending;
        final int outCount;
        /** The inflow shown as salary: the one on the pay day, else the largest non-transfer credit; -1 / 0 when none. */
        final long salaryItemId, salaryWhen;
        /** The largest transfer to a person at or above a quarter of the period's income: likely between own accounts, not linked. */
        final long unlinkedTransfer, unlinkedTransferId;
        Statement(String currency, LocalDate[] period, long income, long salary, long transfersIn, long salaryItemId, long salaryWhen,
                  long living, long commitment, long openTransfer, long cash, int outCount, long internal, long cashSpending,
                  long unlinkedTransfer, long unlinkedTransferId) {
            this.currency = currency; this.start = period[0]; this.end = period[1];
            this.income = income; this.salary = salary; this.transfersIn = transfersIn; this.salaryItemId = salaryItemId; this.salaryWhen = salaryWhen;
            this.living = living; this.commitment = commitment; this.openTransfer = openTransfer; this.cash = cash; this.outCount = outCount;
            this.out = living + commitment + openTransfer + cash; this.remaining = income - out;
            this.internal = internal; this.cashSpending = cashSpending;
            this.unlinkedTransfer = unlinkedTransfer; this.unlinkedTransferId = unlinkedTransferId;
        }
        /** One component of «خرج»; zero for buckets that are not part of it. */
        long component(Bucket bucket) {
            switch (bucket) {
                case LIVING: return living;
                case COMMITMENT: return commitment;
                case OPEN_TRANSFER: return openTransfer;
                case CASH_WITHDRAWAL: return cash;
                default: return 0;
            }
        }
    }

    /** The buckets that make up «خرج», in the order the statement lists them. */
    static final Bucket[] STATEMENT_OUT_BUCKETS = {Bucket.LIVING, Bucket.COMMITMENT, Bucket.OPEN_TRANSFER, Bucket.CASH_WITHDRAWAL};

    static Statement statement(List<Item> items, LocalDate[] period, ZoneId zone, String currency) {
        long start = period[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = period[1].atStartOfDay(zone).toInstant().toEpochMilli();
        long income = 0, living = 0, commitment = 0, openTransfer = 0, cash = 0, internal = 0, cashSpending = 0;
        int outCount = 0;
        Item salary = null, largestTransfer = null;
        int salaryRank = 0;
        for (Item item : items) {
            if (item.when < start || item.when >= end || !currency.equals(item.currency)) continue;
            // Anything paid from the cash wallet was counted when the cash was withdrawn: detail only, in every «خرج» bucket.
            if (item.fromCash && isStatementOut(item.bucket) && item.bucket != Bucket.CASH_WITHDRAWAL) {
                cashSpending = Math.addExact(cashSpending, item.amountMinor); continue;
            }
            switch (item.bucket) {
                case INFLOW: {
                    income = Math.addExact(income, item.amountMinor);
                    boolean onPayDay = Instant.ofEpochMilli(item.when).atZone(zone).toLocalDate().equals(period[0]);
                    // Rank: a non-transfer credit on the pay day, then a transfer that landed on the pay day (salaries often
                    // arrive as instant transfers), then a non-transfer credit on any day. Transfers elsewhere stay transfers in.
                    int rank = onPayDay ? (incomingTransfer(item.merchant) ? 2 : 3) : (incomingTransfer(item.merchant) ? 0 : 1);
                    if (rank == 0) break;
                    if (salary == null || rank > salaryRank || rank == salaryRank && item.amountMinor > salary.amountMinor) {
                        salary = item; salaryRank = rank;
                    }
                    break;
                }
                case INTERNAL: internal = Math.addExact(internal, item.amountMinor); break;
                case CASH_WITHDRAWAL: cash = Math.addExact(cash, item.amountMinor); outCount++; break;
                case OPEN_TRANSFER:
                    openTransfer = Math.addExact(openTransfer, item.amountMinor); outCount++;
                    if (largestTransfer == null || item.amountMinor > largestTransfer.amountMinor) largestTransfer = item;
                    break;
                case COMMITMENT: commitment = Math.addExact(commitment, item.amountMinor); outCount++; break;
                default: living = Math.addExact(living, item.amountMinor); outCount++;
            }
        }
        // No non-transfer credit at all: the largest credit of any kind is still the period's main income.
        if (salary == null) for (Item item : items)
            if (item.bucket == Bucket.INFLOW && item.when >= start && item.when < end && currency.equals(item.currency)
                    && (salary == null || item.amountMinor > salary.amountMinor)) salary = item;
        long salaryAmount = salary == null ? 0 : salary.amountMinor;
        boolean warn = largestTransfer != null && income > 0 && largestTransfer.amountMinor * 4 >= income;
        return new Statement(currency, period, income, salaryAmount, income - salaryAmount, salary == null ? -1 : salary.id, salary == null ? 0 : salary.when,
                living, commitment, openTransfer, cash, outCount, internal, cashSpending,
                warn ? largestTransfer.amountMinor : 0, warn ? largestTransfer.id : -1);
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
