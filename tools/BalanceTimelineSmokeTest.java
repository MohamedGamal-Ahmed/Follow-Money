package com.mohamedgamal.masareef;

public final class BalanceTimelineSmokeTest {
    private static int checked;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checked++;
    }

    public static void main(String[] args) {
        check(SourceIdentity.key("BM-Online", "001").equals(SourceIdentity.key("Bank Misr", "0001")),
                "Padded Bank Misr account should match");
        check(!SourceIdentity.key("HSBC", "0001").equals(SourceIdentity.key("Bank Misr", "0001")),
                "Different banks must stay separate");
        check(SourceIdentity.key("Bank Misr", "1001")
                .equals(SourceIdentity.key("BM", "1001")), "Bank alias should match");
        check(!BalanceTimeline.after(1000, 5000, 1000, 5000),
                "Transaction and balance in one SMS must not be counted twice");
        check(BalanceTimeline.after(1000, 5001, 1000, 5000),
                "Later received event on same date must count");
        check(!BalanceTimeline.after(999, 8000, 1000, 5000),
                "Late SMS for an older event must not change current balance");
        long account = 7;
        long result = 100_00;
        result += BalanceTimeline.delta("expense", account, -1, account, 20_00);
        result += BalanceTimeline.delta("income", account, -1, account, 5_00);
        result += BalanceTimeline.delta("transfer", account, 8, account, 10_00);
        result += BalanceTimeline.delta("transfer", 8, account, account, 3_00);
        check(result == 78_00, "Chronological movements must update one balance");
        check(BalanceTimeline.delta("expense", 8, -1, account, 20_00) == 0,
                "Other account must not affect this balance");
        check(BalanceTimeline.delta("transfer",account,account,account,20_00)==0,
                "A card and its parent account are one balance, not two transfer legs");
        System.out.println("Balance timeline smoke tests passed: " + checked);
    }
}
