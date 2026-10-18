package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Recurring;

/** Existing named-payment and amountless service matching on the caller's borrowed helper. */
final class CommitmentMatcher {
    private final ExpenseDb db;

    CommitmentMatcher(ExpenseDb db) { this.db = db; }

    void advanceRecurring(String merchant, long amount, String currency, long when) {
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        List<Recurring> matches=new ArrayList<>();
        List<Recurring> all=db.recurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview ||
                    ExpenseParser.genericMerchant(merchant)) continue;
            boolean named = item.merchant.equalsIgnoreCase(merchant) || item.matches(merchant);
            if (!named) continue;
            long daysFromDue = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(item.nextDue, day));
            boolean sameAmount = item.currency.equals(currency) &&
                    Math.abs(item.amountMinor - amount) <= 1;
            if (sameAmount && daysFromDue <= 12) matches.add(item);
        }
        if(matches.size()==1) db.markRecurringPaid(matches.get(0).id, day);
    }

    boolean markServicePaid(String service, long when) {
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        boolean changed = false;
        List<Recurring> all=db.recurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview ||
                    !(item.matches(service) || item.merchant.equalsIgnoreCase(service))) continue;
            if (Math.abs(java.time.temporal.ChronoUnit.DAYS.between(item.nextDue, day)) > 12) continue;
            changed |= db.markRecurringPaid(item.id, day);
        }
        return changed;
    }
}
