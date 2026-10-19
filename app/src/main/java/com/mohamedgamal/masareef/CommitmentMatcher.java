package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mohamedgamal.masareef.ExpenseDb.Recurring;

/** Amountless provider confirmations are status evidence, never monetary transactions. */
final class CommitmentMatcher {
    private final ExpenseDb db;

    CommitmentMatcher(ExpenseDb db) { this.db = db; }

    CaptureResult.Reason recordService(String service,long when,long receivedAt,String sourceKey,String body,boolean automatic) {
        CommitmentPayments payments=new CommitmentPayments(db);
        long notice=payments.recordProvider(service,when,receivedAt,sourceKey,body);
        if(notice<0)return CaptureResult.Reason.NO_LEDGER_CANDIDATE;
        if(!automatic)return CaptureResult.Reason.COMMITMENT_REVIEW_REQUIRED;
        for(Recurring item:db.storedRecurring())if(!item.isDone()&&!item.needsReview&&CommitmentPayments.sameCounterparty(item,service)){
            db.normalizeEarlyPayments();break;
        }
        LocalDate day = Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        List<Recurring> matches=new ArrayList<>();List<LocalDate> dates=new ArrayList<>();
        List<Recurring> all=db.storedRecurring();
        for (Recurring item : all) {
            if (item.isDone() || item.needsReview ||
                    !CommitmentPayments.sameCounterparty(item,service)) continue;
            for(LocalDate due:payments.providerCycles(item,day)){matches.add(item);dates.add(due);}
        }
        return matches.size()==1 && payments.resolveProvider(notice,matches.get(0).id,dates.get(0),payments.key(matches.get(0).id),true)
                ?CaptureResult.Reason.SERVICE_MATCH_ACCEPTED:CaptureResult.Reason.COMMITMENT_REVIEW_REQUIRED;
    }
}
