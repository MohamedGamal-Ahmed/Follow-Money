package com.mohamedgamal.masareef;

/** Gross recorded bank movements; reimbursements do not erase actual account inflows/outflows. */
final class AccountMovement {
    long incoming, spent, sent, ownIncoming, ownOutgoing;
    void add(String kind,String category,long amount,boolean from,boolean to) {
        if(amount<=0 || !from && !to) return;
        if("transfer".equals(kind)) {
            // A card and its parent account are the same account, not two transfer endpoints.
            if(from && !to) ownOutgoing+=amount;
            else if(to && !from) ownIncoming+=amount;
        } else if(from && "income".equals(kind)) incoming+=amount;
        else if(from && "expense".equals(kind)) {
            if(MoneyFlow.bucket(kind,category)==MoneyFlow.Bucket.OPEN_TRANSFER) sent+=amount;
            else spent+=amount;
        }
    }
    long outgoing() { return spent+sent; }
}
