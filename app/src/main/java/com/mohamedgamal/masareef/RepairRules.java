package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure matching rules for cleaning captured data. They only ever propose a match when exactly
 * one candidate exists in both directions; anything ambiguous is left for the user.
 */
final class RepairRules {
    private RepairRules() {}

    /** A wallet or prepaid card may post its receipt long after the bank debit (owner decision, 22 Oct 2026: Telda, 24 minutes). */
    static final long OWN_TRANSFER_WINDOW_MS = 60 * 60 * 1000L;
    static final long OWN_TRANSFER_EARLY_MS = 2 * 60 * 1000L;
    static final long DUPLICATE_WINDOW_MS = 3 * 60 * 1000L;

    static final class Row {
        final long id, when, receivedAt, amountMinor;
        final String currency, kind, category, source, direction, merchant;
        final Long from, to;
        final boolean userEdited;
        Row(long id, long when, long receivedAt, long amountMinor, String currency, String kind, String category,
            String source, String direction, Long from, Long to, boolean userEdited) {
            this(id, when, receivedAt, amountMinor, currency, kind, category, source, direction, from, to, userEdited, null);
        }
        Row(long id, long when, long receivedAt, long amountMinor, String currency, String kind, String category,
            String source, String direction, Long from, Long to, boolean userEdited, String merchant) {
            this.id = id; this.when = when; this.receivedAt = receivedAt; this.amountMinor = amountMinor;
            this.currency = currency; this.kind = kind; this.category = category; this.source = source;
            this.direction = direction; this.from = from; this.to = to; this.userEdited = userEdited; this.merchant = merchant;
        }
    }

    static boolean isNotification(String source) { return source != null && source.startsWith("إشعار"); }

    /** Bank fee allowed between what left one account and what reached the other: 0.5% or 5.00, whichever is larger. */
    static long maxFee(long amountMinor) { return Math.max(amountMinor / 200, 500); }

    // ---- Same payment seen twice: bank SMS + payment-app notification ----

    static final class Duplicate {
        final long keepId, removeId;
        Duplicate(long keepId, long removeId) { this.keepId = keepId; this.removeId = removeId; }
    }

    static List<Duplicate> crossChannelDuplicates(List<Row> rows) {
        List<Duplicate> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Row note : rows) {
            if (!isNotification(note.source) || used.contains(note.id)) continue;
            List<Row> sms = new ArrayList<>();
            for (Row other : rows) if (sameEvent(note, other) && !isNotification(other.source)) sms.add(other);
            if (sms.size() != 1 || used.contains(sms.get(0).id)) continue;
            Row bank = sms.get(0);
            int notes = 0;
            for (Row other : rows) if (isNotification(other.source) && sameEvent(bank, other)) notes++;
            if (notes != 1) continue;
            used.add(note.id); used.add(bank.id);
            result.add(new Duplicate(bank.id, note.id));
        }
        return result;
    }

    private static boolean sameEvent(Row a, Row b) {
        return a.id != b.id && a.amountMinor == b.amountMinor && a.currency.equals(b.currency) &&
                a.direction != null && a.direction.equals(b.direction) &&
                isOutgoingTransfer(a) == isOutgoingTransfer(b) &&
                (a.from == null || b.from == null || a.from.equals(b.from)) &&
                Math.abs(a.receivedAt - b.receivedAt) <= DUPLICATE_WINDOW_MS && compatibleMerchants(a.merchant, b.merchant);
    }

    private static boolean isOutgoingTransfer(Row row) {
        return !"income".equals(row.direction) && ("transfer".equals(row.kind) || MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(row.category));
    }

    /** Two named, different merchants are two purchases. A channel name, a masked person or an InstaPay handle can be either side. */
    static boolean compatibleMerchants(String a, String b) {
        if (a == null || b == null || ExpenseParser.genericMerchant(a) || ExpenseParser.genericMerchant(b)) return true;
        if (a.trim().equalsIgnoreCase(b.trim())) return true;
        return a.contains("*") || b.contains("*");
    }

    // ---- Money moved between two of the user's own accounts ----

    static final class OwnTransfer {
        final long outId, inId, fromAccount, toAccount, principal, fee;
        OwnTransfer(long outId, long inId, long fromAccount, long toAccount, long principal, long fee) {
            this.outId = outId; this.inId = inId; this.fromAccount = fromAccount; this.toAccount = toAccount;
            this.principal = principal; this.fee = fee;
        }
    }

    /** The account money left; null when the row is not an outgoing transfer leg. */
    static Long outAccount(Row r) {
        // A transfer written with its own destination (the message named one of the user's accounts) still waits for
        // its receipt; a plain expense with a destination is a card repayment, not an open transfer.
        if (r.from == null || r.to != null && !"transfer".equals(r.kind)) return null;
        if ("transfer".equals(r.kind)) return r.from;
        if ("expense".equals(r.kind) && MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(r.category)) return r.from;
        return null;
    }

    /** The account money reached; income rows keep their account in payment_source_id. */
    static Long inAccount(Row r) {
        if ("transfer".equals(r.kind) && r.to != null && r.from == null) return r.to;
        if ("income".equals(r.kind) && r.from != null && r.to == null) return r.from;
        return null;
    }

    /**
     * Pairs an outgoing leg from own account A with an incoming leg into own account B (B ≠ A) of the same
     * amount minus at most a bank fee, arriving within minutes. Both legs must be each other's only candidate.
     * accountOf maps a card to the account it belongs to, so a card paying its own account is not a transfer.
     */
    static List<OwnTransfer> ownTransfers(List<Row> rows, Map<Long, Long> accountOf) {
        Map<Long, List<Row>> inCandidates = new HashMap<>();
        Map<Long, List<Row>> outCandidates = new HashMap<>();
        for (Row out : rows) {
            Long a = outAccount(out);
            if (a == null) continue;
            for (Row in : rows) {
                Long b = inAccount(in);
                if (b == null || in.id == out.id || !matches(out, a, in, b, accountOf)) continue;
                inCandidates.computeIfAbsent(out.id, k -> new ArrayList<>()).add(in);
                outCandidates.computeIfAbsent(in.id, k -> new ArrayList<>()).add(out);
            }
        }
        List<OwnTransfer> result = new ArrayList<>();
        for (Row out : rows) {
            List<Row> ins = inCandidates.get(out.id);
            if (ins == null || ins.size() != 1) continue;
            Row in = ins.get(0);
            List<Row> outs = outCandidates.get(in.id);
            if (outs == null || outs.size() != 1) continue;
            result.add(new OwnTransfer(out.id, in.id, outAccount(out), inAccount(in), in.amountMinor, out.amountMinor - in.amountMinor));
        }
        return result;
    }

    private static boolean matches(Row out, long a, Row in, long b, Map<Long, Long> accountOf) {
        long fromAccount = accountOf.getOrDefault(a, a), toAccount = accountOf.getOrDefault(b, b);
        if (fromAccount == toAccount || !out.currency.equals(in.currency)) return false;
        // The debit already names where it went: only a receipt on that account is its other half.
        if (out.to != null && accountOf.getOrDefault(out.to, out.to) != toAccount) return false;
        long fee = out.amountMinor - in.amountMinor;
        if (fee < 0 || fee > maxFee(out.amountMinor)) return false;
        long gap = in.when - out.when;
        return gap >= -OWN_TRANSFER_EARLY_MS && gap <= OWN_TRANSFER_WINDOW_MS;
    }

    // ---- A fixed amount sent from the same account every month: likely an installment ----

    static final class RepeatingTransfer {
        final long sourceId, amountMinor;
        final String currency;
        final List<Long> ids;
        final int typicalDay;
        final long firstAt, lastAt;
        RepeatingTransfer(long sourceId, long amountMinor, String currency, List<Long> ids, int typicalDay, long firstAt, long lastAt) {
            this.sourceId = sourceId; this.amountMinor = amountMinor; this.currency = currency; this.ids = ids;
            this.typicalDay = typicalDay; this.firstAt = firstAt; this.lastAt = lastAt;
        }
    }

    /** Groups open transfers by (account, exact amount); keeps groups seen in at least 3 different months, 20–40 days apart on average. */
    static List<RepeatingTransfer> repeatingTransfers(List<Row> rows, long minimumMinor, ZoneId zone) {
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row r : rows) {
            if (!"expense".equals(r.kind) || !MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(r.category) || r.from == null ||
                    r.amountMinor < minimumMinor) continue;
            groups.computeIfAbsent(r.from + "|" + r.currency + "|" + r.amountMinor, k -> new ArrayList<>()).add(r);
        }
        List<RepeatingTransfer> result = new ArrayList<>();
        for (List<Row> group : groups.values()) {
            group.sort((x, y) -> Long.compare(x.when, y.when));
            Set<YearMonth> months = new HashSet<>();
            List<Integer> days = new ArrayList<>();
            for (Row r : group) {
                java.time.LocalDate d = Instant.ofEpochMilli(r.when).atZone(zone).toLocalDate();
                months.add(YearMonth.from(d)); days.add(d.getDayOfMonth());
            }
            if (months.size() < 3) continue;
            double spanDays = (group.get(group.size() - 1).when - group.get(0).when) / 86_400_000.0;
            double averageGap = spanDays / (group.size() - 1);
            if (averageGap < 20 || averageGap > 40) continue;
            Collections.sort(days);
            List<Long> ids = new ArrayList<>();
            for (Row r : group) ids.add(r.id);
            Row first = group.get(0);
            result.add(new RepeatingTransfer(first.from, first.amountMinor, first.currency, ids, days.get(days.size() / 2),
                    first.when, group.get(group.size() - 1).when));
        }
        result.sort((x, y) -> Long.compare(y.amountMinor * y.ids.size(), x.amountMinor * x.ids.size()));
        return result;
    }

    /** A merchant charged about once a month (a subscription or a bill) that has no saved commitment yet. */
    static final class RepeatingMerchant {
        final String merchant, currency, category;
        final long amountMinor, lastAt;
        final int months, typicalDay;
        RepeatingMerchant(String merchant, String currency, String category, long amountMinor, long lastAt, int months, int typicalDay) {
            this.merchant = merchant; this.currency = currency; this.category = category; this.amountMinor = amountMinor;
            this.lastAt = lastAt; this.months = months; this.typicalDay = typicalDay;
        }
    }

    /**
     * Same named merchant, charged in at least 3 of the last 4 calendar months, last charge within 45 days of today.
     * The suggested amount is the latest charge (prices change); installments and transfers are handled elsewhere.
     */
    static List<RepeatingMerchant> repeatingMerchants(List<Row> rows, java.time.LocalDate today, ZoneId zone) {
        Map<String, List<Row>> byMerchant = new LinkedHashMap<>();
        YearMonth current = YearMonth.from(today);
        for (Row r : rows) {
            if (!"expense".equals(r.kind) || r.merchant == null || ExpenseParser.genericMerchant(r.merchant)) continue;
            MoneyFlow.Bucket bucket = MoneyFlow.bucket(r.kind, r.category);
            if (bucket == MoneyFlow.Bucket.OPEN_TRANSFER || "أقساط".equals(r.category) || "سحب نقدي".equals(r.category) || "رسوم تحويل".equals(r.category)) continue;
            YearMonth m = YearMonth.from(Instant.ofEpochMilli(r.when).atZone(zone).toLocalDate());
            if (m.isBefore(current.minusMonths(3)) || m.isAfter(current)) continue;
            byMerchant.computeIfAbsent(r.merchant.trim() + "|" + r.currency, k -> new ArrayList<>()).add(r);
        }
        List<RepeatingMerchant> result = new ArrayList<>();
        long recent = today.minusDays(45).atStartOfDay(zone).toInstant().toEpochMilli();
        for (List<Row> group : byMerchant.values()) {
            group.sort((x, y) -> Long.compare(x.when, y.when));
            Set<YearMonth> months = new HashSet<>();
            List<Integer> days = new ArrayList<>();
            for (Row r : group) {
                java.time.LocalDate d = Instant.ofEpochMilli(r.when).atZone(zone).toLocalDate();
                months.add(YearMonth.from(d)); days.add(d.getDayOfMonth());
            }
            Row last = group.get(group.size() - 1);
            // Monthly-ish: no more than two charges per month on average (not a café visited daily).
            if (months.size() < 3 || last.when < recent || group.size() > months.size() * 2) continue;
            Collections.sort(days);
            result.add(new RepeatingMerchant(last.merchant.trim(), last.currency, last.category, last.amountMinor, last.when, months.size(), days.get(days.size() / 2)));
        }
        result.sort((x, y) -> Long.compare(y.amountMinor, x.amountMinor));
        return result;
    }

    // ---- Rows an older parser booked from HSBC's trailing-sign alerts ----

    /** What a stored row should become once its source message is read with the trailing sign (see ExpenseParser.trailingSign). */
    static final class TrailingSignFix {
        final boolean toIncome;
        final String merchant, category;
        TrailingSignFix(boolean toIncome, String merchant, String category) {
            this.toIncome = toIncome; this.merchant = merchant; this.category = category;
        }
    }

    /**
     * A spending row whose message ends its amount with "+" (and names no debit) is money that reached the account:
     * it becomes income with the descriptor as merchant. A "-" row only gets its merchant corrected when the old
     * parser stored the bank name ("HSBC: …"). User-edited rows, reconciled transfers and amounts that no longer
     * match the evidence are never touched.
     */
    static TrailingSignFix trailingSignFix(Row row, String sender, String body) {
        if (row == null || body == null || row.userEdited || !"expense".equals(row.kind) || row.to != null) return null;
        if (!ExpenseParser.trailingSignFormat(body)) return null;
        if (ExpenseParser.trailingSign(body) == '+') {
            ExpenseParser.Parsed income = ExpenseParser.parseIncome(sender, body);
            if (income == null || income.amountMinor != row.amountMinor || !income.currency.equals(row.currency)) return null;
            return new TrailingSignFix(true, income.merchant, income.category);
        }
        if (row.merchant == null || !row.merchant.matches("(?is)HSBC\\s*:.*")) return null;
        ExpenseParser.Parsed expense = ExpenseParser.parse(sender, body);
        if (expense == null || expense.amountMinor != row.amountMinor || !expense.currency.equals(row.currency) ||
                expense.merchant.equals(row.merchant)) return null;
        // A category hung on the bank name (learned from another "HSBC:" row) is not trusted: the parser's definite
        // category (cash withdrawal, bank fee…) wins; only an uncategorized parse keeps a category the row already has.
        boolean parserCategory = MoneyFlow.UNCATEGORIZED.equals(row.category) || MoneyFlow.OPEN_TRANSFER_CATEGORY.equals(row.category) ||
                !MoneyFlow.UNCATEGORIZED.equals(expense.category);
        return new TrailingSignFix(false, expense.merchant, parserCategory ? expense.category : row.category);
    }

    /** Installments remaining from the next due date through the last one (inclusive), monthly. */
    static int monthlyPaymentsThrough(java.time.LocalDate nextDue, java.time.LocalDate lastDue) {
        if (lastDue.isBefore(nextDue)) return 0;
        return (int) java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(nextDue), YearMonth.from(lastDue)) + 1;
    }
}
