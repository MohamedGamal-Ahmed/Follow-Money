package com.mohamedgamal.masareef;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Patterns taken from real (anonymized) captures: each rule must match exactly one counterpart or nothing. */
public final class RepairRulesTest {
    private static int checked;
    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); checked++; }
    private static long at(String time) { return LocalDateTime.parse(time).atZone(ZONE).toInstant().toEpochMilli(); }

    private static RepairRules.Row row(long id, String time, long amount, String kind, String category, String source,
                                       String direction, Long from, Long to) {
        long when = at(time);
        return new RepairRules.Row(id, when, when, amount, "EGP", kind, category, source, direction, from, to, false);
    }

    public static void main(String[] args) {
        // 1) InstaPay notification + bank SMS of the same incoming 1,200 (22 seconds apart) → one payment.
        List<RepairRules.Row> rows = new ArrayList<>();
        rows.add(row(1286, "2026-10-11T20:23:00", 120000, "income", "دخل", "SMS", "income", 6L, null));
        rows.add(row(1285, "2026-10-11T20:23:22", 120000, "income", "دخل", "إشعار: InstaPay", "income", null, null));
        List<RepairRules.Duplicate> duplicates = RepairRules.crossChannelDuplicates(rows);
        check(duplicates.size() == 1 && duplicates.get(0).keepId == 1286 && duplicates.get(0).removeId == 1285, "notification merged into bank SMS");
        // Two separate 1,200 SMS around one notification is ambiguous → untouched.
        rows.add(row(1290, "2026-10-11T20:24:00", 120000, "income", "دخل", "SMS", "income", 8L, null));
        check(RepairRules.crossChannelDuplicates(rows).isEmpty(), "ambiguous duplicate left alone");
        // Two named shops with the same amount are two purchases, even across channels.
        rows.clear();
        rows.add(new RepairRules.Row(10, at("2026-10-11T12:00:00"), at("2026-10-11T12:00:00"), 10000, "EGP", "expense", "تسوق", "SMS", "expense", 4L, null, false, "SHOP ONE"));
        rows.add(new RepairRules.Row(11, at("2026-10-11T12:00:01"), at("2026-10-11T12:00:01"), 10000, "EGP", "expense", "تسوق", "إشعار: HSBC", "expense", null, null, false, "SHOP TWO"));
        check(RepairRules.crossChannelDuplicates(rows).isEmpty(), "different named merchants never merge");
        check(RepairRules.compatibleMerchants("تحويل لحظي", "hebatallah@instapay") && RepairRules.compatibleMerchants("شخص ا*****", "x@instapay"), "channel vs handle");
        // Tester, 24 Oct 2026: InstaPay "hebatallah.aladly@instapay" +1,000 and the Banque Misr SMS naming the account
        // holder "HEBATALLAH SAMIR ALADLY" +1,000 a minute apart were kept as two incomes. The handle is the person's name.
        rows.clear();
        rows.add(new RepairRules.Row(20, at("2026-10-23T18:27:00"), at("2026-10-23T18:27:00"), 100000, "EGP", "income", "دخل", "SMS", "income", 6L, null, false, "HEBATALLAH SAMIR ALADLY"));
        rows.add(new RepairRules.Row(21, at("2026-10-23T18:28:00"), at("2026-10-23T18:28:00"), 100000, "EGP", "income", "دخل", "إشعار: InstaPay", "income", null, null, false, "hebatallah.aladly@instapay"));
        duplicates = RepairRules.crossChannelDuplicates(rows);
        check(duplicates.size() == 1 && duplicates.get(0).keepId == 20 && duplicates.get(0).removeId == 21, "InstaPay handle merged into the bank SMS naming the same person");
        check(RepairRules.compatibleMerchants("ahmedhamdyac@instapay", "AHMED HAMDY ABDELRAHMAN") && RepairRules.compatibleMerchants("mo.gamalhsbc@instapay", "MOHAMED GAMAL ABDELAZIZ"), "handle built from the name");
        check(RepairRules.keepFirst("SMS", "إشعار: InstaPay") && !RepairRules.keepFirst("إشعار: InstaPay", "SMS") && RepairRules.keepFirst("إشعار: InstaPay", "إشعار: Telda"), "a manual merge keeps the bank message");
        check(!RepairRules.compatibleMerchants("friend@instapay", "HEBATALLAH SAMIR ALADLY") && !RepairRules.compatibleMerchants("pay123@instapay", "SEOUDI MARKET"), "unrelated handle and name stay apart");
        // Different direction never merges.
        rows.clear();
        rows.add(row(1, "2026-10-11T20:23:00", 120000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(2, "2026-10-11T20:23:10", 120000, "income", "دخل", "إشعار: InstaPay", "income", null, null));
        check(RepairRules.crossChannelDuplicates(rows).isEmpty(), "sent and received are different events");

        // 2) Own transfers.
        Map<Long, Long> accountOf = new HashMap<>();
        accountOf.put(3L, 6L); // debit card 4636 belongs to account 7392
        rows.clear();
        // HSBC → Banque Misr: 20,020 left, 20,000 arrived 67 s later (fee 20).
        rows.add(row(783, "2026-07-30T13:30:00", 2002000, "transfer", "تحويلات", "SMS", "expense", 4L, null));
        rows.add(row(782, "2026-07-30T13:31:07", 2000000, "transfer", "دخل", "SMS", "income", null, 6L));
        // Card 4420 → account 7392 by instant transfer, captured as expense + income.
        rows.add(row(1246, "2025-10-29T23:30:00", 1301300, "expense", "تحويلات", "SMS", "expense", 7L, null));
        rows.add(row(459, "2025-10-29T23:30:20", 1300000, "income", "دخل", "SMS", "income", 6L, null));
        // Account 7392 pays the credit card 2700 the same second.
        rows.add(row(736, "2026-07-01T08:46:37", 753258, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(1137, "2026-07-01T08:46:48", 753258, "income", "دخل", "SMS", "income", 1L, null));
        // A real transfer to a person, and an unrelated income ninety minutes later: must stay as they are.
        rows.add(row(900, "2026-08-01T10:00:00", 500000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(901, "2026-08-01T11:30:00", 500000, "income", "دخل", "SMS", "income", 4L, null));
        // The debit card paying into its own account is not a move between accounts.
        rows.add(row(950, "2026-08-02T10:00:00", 10000, "expense", "تحويلات", "SMS", "expense", 3L, null));
        rows.add(row(951, "2026-08-02T10:00:30", 10000, "income", "دخل", "SMS", "income", 6L, null));
        // Fee too large to be a bank fee.
        rows.add(row(960, "2026-08-03T10:00:00", 200000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(961, "2026-08-03T10:00:30", 150000, "income", "دخل", "SMS", "income", 4L, null));
        List<RepairRules.OwnTransfer> own = RepairRules.ownTransfers(rows, accountOf);
        Map<Long, RepairRules.OwnTransfer> byOut = new HashMap<>();
        for (RepairRules.OwnTransfer o : own) byOut.put(o.outId, o);
        check(own.size() == 3, "three own transfers, got " + own.size());
        check(byOut.get(783L).inId == 782 && byOut.get(783L).principal == 2000000 && byOut.get(783L).fee == 2000, "HSBC fee split");
        check(byOut.get(1246L).inId == 459 && byOut.get(1246L).fee == 1300, "card → account with fee");
        check(byOut.get(736L).inId == 1137 && byOut.get(736L).fee == 0 && byOut.get(736L).toAccount == 1, "credit card payment is internal");
        check(!byOut.containsKey(900L) && !byOut.containsKey(950L) && !byOut.containsKey(960L), "people, same account and big gaps stay");
        // Two possible receipts → nothing is paired.
        rows.add(row(784, "2026-07-30T13:32:00", 2000000, "income", "دخل", "SMS", "income", 8L, null));
        boolean pairedAmbiguous = false;
        for (RepairRules.OwnTransfer o : RepairRules.ownTransfers(rows, accountOf)) if (o.outId == 783) pairedAmbiguous = true;
        check(!pairedAmbiguous, "ambiguous receipt not paired");
        // 2b) Step 7 writes a debit whose counterparty digits named one of the user's own accounts as a transfer with
        // that destination already set (HSBC 0001 → Banque Misr 7392, 22 Oct 2026). The receipt must still be paired
        // with it, and only when it landed on that destination.
        rows.clear();
        rows.add(row(2001, "2026-10-22T06:46:00", 450, "transfer", "تحويلات", "SMS", "expense", 4L, 6L));
        rows.add(row(2002, "2026-10-22T06:46:30", 400, "income", "دخل", "SMS", "income", 6L, null));
        List<RepairRules.OwnTransfer> addressed = RepairRules.ownTransfers(rows, accountOf);
        check(addressed.size() == 1 && addressed.get(0).outId == 2001 && addressed.get(0).inId == 2002 && addressed.get(0).fee == 50
                && addressed.get(0).toAccount == 6, "debit with its own destination pairs with the receipt on that account");
        rows.clear();
        rows.add(row(2001, "2026-10-22T06:46:00", 450, "transfer", "تحويلات", "SMS", "expense", 4L, 6L));
        rows.add(row(2003, "2026-10-22T06:46:30", 400, "income", "دخل", "SMS", "income", 8L, null));
        check(RepairRules.ownTransfers(rows, accountOf).isEmpty(), "a receipt on another account is not that transfer");
        // 2c) Owner decision (22 Oct 2026): a wallet/prepaid card may post its receipt well after the bank debit
        // (Telda: 35 left Banque Misr 06:09, arrived 06:33). Within an hour pairs; beyond it stays apart.
        rows.clear();
        rows.add(row(3001, "2026-10-22T06:09:00", 3500, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(3002, "2026-10-22T06:33:00", 3500, "income", "دخل", "إشعار: Telda", "income", 9L, null));
        List<RepairRules.OwnTransfer> late = RepairRules.ownTransfers(rows, accountOf);
        check(late.size() == 1 && late.get(0).outId == 3001 && late.get(0).inId == 3002 && late.get(0).fee == 0, "receipt 24 minutes later is still the same transfer");
        rows.clear();
        rows.add(row(3001, "2026-10-22T06:09:00", 3500, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(3003, "2026-10-22T07:10:00", 3500, "income", "دخل", "إشعار: Telda", "income", 9L, null));
        check(RepairRules.ownTransfers(rows, accountOf).isEmpty(), "a receipt more than an hour later is not paired");
        // 2d) Owner test (22 Oct 2026, second round): after the bank debit and the receipt were reconciled into one transfer,
        // the receipt's app notification arrived again (InstaPay +1.00, Telda +10.00) and became a second income row,
        // because a reconciled transfer is user-edited and no longer an income. A notification income of the same amount
        // within the duplicate window, on the transfer's destination (or with no source), is that receipt seen again.
        rows.clear();
        RepairRules.Row settled = new RepairRules.Row(4001, at("2026-10-22T10:25:00"), at("2026-10-22T10:25:00"), 1000, "EGP", "transfer", "تحويلات", "SMS", "expense", 6L, 9L, true, "تحويل بين حساباتي");
        rows.add(settled);
        rows.add(row(4002, "2026-10-22T10:25:40", 1000, "income", "دخل", "إشعار: Telda", "income", 9L, null));
        rows.add(row(4003, "2026-10-22T10:50:10", 1000, "income", "دخل", "إشعار: InstaPay", "income", null, null)); // the receipt itself was 24 minutes late
        List<RepairRules.Duplicate> again = RepairRules.lateReceiptDuplicates(rows);
        check(again.size() == 2 && again.get(0).keepId == 4001 && again.get(1).keepId == 4001
                && again.get(0).removeId == 4002 && again.get(1).removeId == 4003, "both app copies of the receipt fold into the settled transfer: " + again.size());
        rows.clear(); rows.add(settled);
        rows.add(row(4004, "2026-10-22T10:25:40", 1000, "income", "دخل", "إشعار: Telda", "income", 4L, null)); // another account
        rows.add(row(4005, "2026-10-22T10:25:40", 1500, "income", "دخل", "إشعار: Telda", "income", 9L, null)); // other amount
        rows.add(row(4006, "2026-10-22T11:40:00", 1000, "income", "دخل", "إشعار: Telda", "income", 9L, null)); // after the transfer window
        rows.add(row(4007, "2026-10-22T10:25:40", 1000, "income", "دخل", "SMS", "income", 9L, null)); // a bank SMS is a real credit
        check(RepairRules.lateReceiptDuplicates(rows).isEmpty(), "other account, other amount, late, or a bank SMS stay as they are");

        // 3) A fixed monthly transfer is offered as an installment.
        rows.clear();
        String[] days = {"2025-07-30", "2025-09-01", "2025-10-01", "2025-11-03", "2025-12-02", "2025-12-29", "2026-01-31", "2026-03-01"};
        long id = 1;
        for (String day : days) rows.add(row(id++, day + "T14:00:00", 5060000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(id++, "2026-03-05T10:00:00", 5060000, "expense", "تحويلات", "SMS", "expense", 4L, null)); // other account
        rows.add(row(id++, "2026-03-06T10:00:00", 15000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(id++, "2026-04-06T10:00:00", 15000, "expense", "تحويلات", "SMS", "expense", 6L, null));
        rows.add(row(id++, "2026-05-06T10:00:00", 15000, "expense", "تحويلات", "SMS", "expense", 6L, null)); // below minimum
        List<RepairRules.RepeatingTransfer> repeating = RepairRules.repeatingTransfers(rows, 100000, ZONE);
        check(repeating.size() == 1 && repeating.get(0).ids.size() == 8 && repeating.get(0).sourceId == 6 &&
                repeating.get(0).amountMinor == 5060000, "apartment installment pattern");
        check(RepairRules.monthlyPaymentsThrough(LocalDate.of(2026, 10, 28), LocalDate.of(2030, 12, 1)) == 51, "Oct 2026 → Dec 2030 = 51 payments");
        check(RepairRules.monthlyPaymentsThrough(LocalDate.of(2031, 1, 1), LocalDate.of(2030, 12, 1)) == 0, "already ended");
        check(RepairRules.maxFee(2002000) == 10010 && RepairRules.maxFee(10000) == 500, "fee bound");
        // 4) A subscription paid every month but never saved.
        rows.clear();
        String[] gpt = {"2026-06-13", "2026-07-13", "2026-08-13", "2026-09-13"};
        for (String day : gpt) rows.add(new RepairRules.Row(id++, at(day + "T21:28:00"), at(day + "T21:28:00"), 22000, "EGP", "expense", "اشتراكات", "SMS", "expense", 1L, null, false, "ChatGPT"));
        for (int d = 1; d <= 28; d++) rows.add(new RepairRules.Row(id++, at(String.format(java.util.Locale.ROOT,"2026-09-%02dT09:00:00", d)), at(String.format(java.util.Locale.ROOT,"2026-09-%02dT09:00:00", d)), 3000, "EGP", "expense", "أكل وشرب", "SMS", "expense", 3L, null, false, "Cafe"));
        String[] old = {"2026-02-28", "2026-03-28", "2026-04-28", "2026-05-27"};
        for (String day : old) rows.add(new RepairRules.Row(id++, at(day + "T09:00:00"), at(day + "T09:00:00"), 3900, "EGP", "expense", "اشتراكات", "SMS", "expense", null, null, false, "OneDrive"));
        List<RepairRules.RepeatingMerchant> subs = RepairRules.repeatingMerchants(rows, LocalDate.of(2026, 10, 11), ZONE);
        check(subs.size() == 1 && subs.get(0).merchant.equals("ChatGPT") && subs.get(0).amountMinor == 22000 && subs.get(0).typicalDay == 13,
                "ChatGPT found; a daily café and a stopped OneDrive are not subscriptions");

        // 6) Rows an older parser booked from HSBC's trailing-sign format: "+" was saved as spending, merchant as "HSBC: …".
        String credit = "From HSBC: 25SEP26 Phone Banking Transfer to 012-345***-678 EGP 87,650.00+ Your available balance is EGP 87,767.74";
        String debit = "From HSBC: 17OCT26 ATM Cash Withdrawal from 012-345***-678 EGP 2,000.00- Your available balance is EGP 85,650.00";
        long sep = at("2026-09-25T10:00:00"), oct = at("2026-10-17T10:00:00");
        RepairRules.Row booked = new RepairRules.Row(901, sep, sep, 8765000, "EGP", "expense", "تحويلات", "SMS", "expense", 6L, null, false, "HSBC: 25SEP26 Phone Banking Transfer");
        RepairRules.TrailingSignFix fix = RepairRules.trailingSignFix(booked, "HSBC", credit);
        check(fix != null && fix.toIncome && fix.merchant.equals("Phone Banking Transfer") && fix.category.equals("دخل"), "trailing plus booked as spending becomes income");
        RepairRules.Row edited = new RepairRules.Row(902, sep, sep, 8765000, "EGP", "expense", "تحويلات", "SMS", "expense", 6L, null, true, "HSBC: 25SEP26 Phone Banking Transfer");
        check(RepairRules.trailingSignFix(edited, "HSBC", credit) == null, "user-edited row is left alone");
        RepairRules.Row reconciled = new RepairRules.Row(903, sep, sep, 8765000, "EGP", "transfer", "تحويلات", "SMS", "expense", 6L, 7L, false, "HSBC: 25SEP26 Phone Banking Transfer");
        check(RepairRules.trailingSignFix(reconciled, "HSBC", credit) == null, "reconciled own transfer is left alone");
        RepairRules.Row different = new RepairRules.Row(904, sep, sep, 100000, "EGP", "expense", "تحويلات", "SMS", "expense", 6L, null, false, "HSBC: 25SEP26 Phone Banking Transfer");
        check(RepairRules.trailingSignFix(different, "HSBC", credit) == null, "amount that differs from the evidence is left alone");
        RepairRules.Row cash = new RepairRules.Row(905, oct, oct, 200000, "EGP", "expense", "غير مصنف", "SMS", "expense", 6L, null, false, "HSBC: 17OCT26 ATM Cash Withdrawal fr");
        fix = RepairRules.trailingSignFix(cash, "HSBC", debit);
        check(fix != null && !fix.toIncome && fix.merchant.equals("ATM Cash Withdrawal") && fix.category.equals("سحب نقدي"), "trailing minus stays spending; merchant and cash category are corrected");
        // A category learned onto the bank name (not user-edited) is not trusted when the parser has a definite one.
        RepairRules.Row learned = new RepairRules.Row(908, oct, oct, 200000, "EGP", "expense", "أكل وشرب", "SMS", "expense", 6L, null, false, "HSBC: 17OCT26 ATM Cash Withdrawal fr");
        fix = RepairRules.trailingSignFix(learned, "HSBC", debit);
        check(fix != null && !fix.toIncome && fix.merchant.equals("ATM Cash Withdrawal") && fix.category.equals("سحب نقدي"), "definite parser category replaces a category hung on the bank name");
        RepairRules.Row named = new RepairRules.Row(906, oct, oct, 200000, "EGP", "expense", "سحب نقدي", "SMS", "expense", 6L, null, false, "ATM Cash Withdrawal");
        check(RepairRules.trailingSignFix(named, "HSBC", debit) == null, "already correct row is left alone");
        RepairRules.Row explicit = new RepairRules.Row(907, oct, oct, 10000, "EGP", "expense", "تسوق", "SMS", "expense", 6L, null, false, "STORE");
        check(RepairRules.trailingSignFix(explicit, "BANK", "Your account was debited with EGP 100.00+ at STORE") == null, "explicit debit wording is never flipped");
        // Owner report, 31 Oct 2026: TRU «تم سداد مبلغ 2438.00» + bank «خصم 2465.8 … لدى ترو» four minutes apart.
        RepairRules.ProviderOf providers = m -> m == null ? null : m.matches("(?iu).*\\b(tru)\\b.*|.*ترو.*") ? "TRU" : m.matches("(?iu).*valu.*") ? "valU" : null;
        long tru = at("2026-10-31T05:23:00");
        List<RepairRules.Row> pay = new ArrayList<>();
        pay.add(new RepairRules.Row(10, tru, tru, 246580, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        pay.add(new RepairRules.Row(11, tru + 240000, tru + 240000, 243800, "EGP", "expense", "أقساط", "SMS", "expense", null, null, false, "TRU"));
        List<RepairRules.ProviderPayment> paid = RepairRules.providerReceipts(pay, providers);
        check(paid.size() == 1 && paid.get(0).receiptId == 11 && paid.get(0).debitId == 10 && paid.get(0).fee == 2780, "TRU receipt + bank debit = one payment, 27.80 fee");
        // Same amount: one payment, no fee.
        List<RepairRules.Row> equal = new ArrayList<>();
        equal.add(new RepairRules.Row(20, tru, tru, 243800, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        equal.add(new RepairRules.Row(21, tru, tru, 243800, "EGP", "expense", "أقساط", "SMS", "expense", null, null, false, "TRU"));
        check(RepairRules.providerReceipts(equal, providers).size() == 1 && RepairRules.providerReceipts(equal, providers).get(0).fee == 0, "equal amounts join without a fee");
        // Never: a bigger gap than 3%, another provider, a debit smaller than the receipt, two days apart, two debits.
        List<RepairRules.Row> far = new ArrayList<>(equal); far.set(0, new RepairRules.Row(20, tru, tru, 260000, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        check(RepairRules.providerReceipts(far, providers).isEmpty(), "a 6.6% difference is two payments");
        List<RepairRules.Row> other = new ArrayList<>(equal); other.set(0, new RepairRules.Row(20, tru, tru, 243800, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "valU"));
        check(RepairRules.providerReceipts(other, providers).isEmpty(), "different providers never join");
        List<RepairRules.Row> smaller = new ArrayList<>(equal); smaller.set(0, new RepairRules.Row(20, tru, tru, 240000, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        check(RepairRules.providerReceipts(smaller, providers).isEmpty(), "a debit below the receipt is not this payment");
        List<RepairRules.Row> apart = new ArrayList<>(equal); apart.set(0, new RepairRules.Row(20, tru + 2 * 86_400_000L, tru + 2 * 86_400_000L, 243800, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        check(RepairRules.providerReceipts(apart, providers).isEmpty(), "two days apart never join");
        List<RepairRules.Row> twice = new ArrayList<>(pay);
        twice.add(new RepairRules.Row(12, tru, tru, 245000, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        check(RepairRules.providerReceipts(twice, providers).isEmpty(), "two possible debits: no guess");
        List<RepairRules.Row> bothBank = new ArrayList<>(equal); bothBank.set(1, new RepairRules.Row(21, tru, tru, 243800, "EGP", "expense", "أقساط", "SMS", "expense", 7L, null, false, "TRU"));
        check(RepairRules.providerReceipts(bothBank, providers).isEmpty(), "two account debits are two payments");
        // Review of PR #54: a manual cash payment of 100 to TRU and a separate bank payment of 102 are 202, not 100 + fee.
        List<RepairRules.Row> manual = new ArrayList<>();
        manual.add(new RepairRules.Row(30, tru, tru, 10200, "EGP", "expense", "أقساط", "SMS", "expense", 6L, null, false, "ترو"));
        manual.add(new RepairRules.Row(31, tru, tru, 10000, "EGP", "expense", "أقساط", "يدوي", "expense", null, null, false, "TRU"));
        check(RepairRules.providerReceipts(manual, providers).isEmpty(), "a manual row is never a provider receipt");
        List<RepairRules.Row> csv = new ArrayList<>(manual);
        csv.set(1, new RepairRules.Row(31, tru, tru, 10000, "EGP", "expense", "أقساط", "CSV", "expense", null, null, false, "TRU"));
        check(RepairRules.providerReceipts(csv, providers).isEmpty(), "a CSV row is never a provider receipt");
        List<RepairRules.Row> csvDebit = new ArrayList<>(manual);
        csvDebit.set(0, new RepairRules.Row(30, tru, tru, 10200, "EGP", "expense", "أقساط", "CSV", "expense", 6L, null, false, "ترو"));
        csvDebit.set(1, new RepairRules.Row(31, tru, tru, 10000, "EGP", "expense", "أقساط", "SMS", "expense", null, null, false, "TRU"));
        check(RepairRules.providerReceipts(csvDebit, providers).isEmpty(), "a CSV row is never the bank debit");
        System.out.println("Repair rule checks passed: " + checked);
    }
}
