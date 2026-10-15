package com.mohamedgamal.masareef;

/** Read-only interpretation for historical import. The original body stays in memory until approval. */
final class SmsPreview {
    final String sender, body, title, category, currency, kind;
    final String timePrecision;
    final long receivedAt, captureAt, eventAt, amountMinor;
    final boolean balanceIncluded, commitmentIncluded;
    boolean selected;
    boolean receiptKeySafe;
    int captureStatus;

    private SmsPreview(String sender, String body, long receivedAt, long captureAt, String title, String category,
                       String currency, long amountMinor, String kind, boolean balance, boolean commitment) {
        this.sender=sender; this.body=body; this.receivedAt=receivedAt;
        this.captureAt=captureAt;
        ExpenseParser.EventTime event=ExpenseParser.eventTimeWithPrecision(body,captureAt);
        this.eventAt=event.when;
        this.timePrecision=event.precision;
        this.title=title; this.category=category; this.currency=currency;
        this.amountMinor=amountMinor; this.kind=kind;
        this.balanceIncluded=balance; this.commitmentIncluded=commitment;
    }

    static SmsPreview parse(String sender, String body, long when) {
        return parse(sender, body, when, when);
    }

    static SmsPreview parse(String sender, String body, long when, long sentAt) {
        long captureAt=CaptureIdentity.smsTimestamp(when, sentAt);
        if(body==null || ExpenseParser.isVerificationMessage(body)) return null;
        if(ExpenseParser.isFutureOnly(body)) {
            return ExpenseParser.isFinancialCandidate(body) ? new SmsPreview(sender,body,when,captureAt,
                    sender,"صيغة مستقبل · مراجعة التسجيل السابق فقط","",0,"correction",false,false) : null;
        }
        if(ExpenseParser.isUnsuccessful(body)) {
            return ExpenseParser.isFinancialCandidate(body) ? new SmsPreview(sender,body,when,captureAt,
                    sender,"رسالة رفض/إلغاء · قد تصحح عملية سابقة","",0,"correction",false,false) : null;
        }
        ExpenseParser.Transfer transfer=ExpenseParser.parseTransfer(body);
        ExpenseParser.Parsed parsed=ExpenseParser.parse(sender,body);
        String kind="expense";
        if(parsed==null) { parsed=ExpenseParser.parseIncome(sender,body);kind="income"; }
        ExpenseParser.Balance balance=ExpenseParser.parseBalance(body);
        CommitmentHint hint=CommitmentHint.parse(sender,body,captureAt);
        boolean commitment=hint!=null || CommitmentHint.completedPlanKey(sender,body)!=null ||
                ExpenseParser.paidService(body)!=null;
        if(transfer!=null) return new SmsPreview(sender,body,when,captureAt,transfer.counterpart,
                transfer.incoming?"حوالة واردة":"حوالة صادرة","EGP",transfer.amountMinor,
                "transfer",balance!=null,commitment);
        if(parsed!=null) return new SmsPreview(sender,body,when,captureAt,parsed.merchant,
                "income".equals(kind)?"دخل":parsed.category,parsed.currency,parsed.amountMinor,
                kind,balance!=null,commitment);
        if(hint!=null) return new SmsPreview(sender,body,when,captureAt,hint.merchant,
                "تذكير/خطة · ليس مصروفًا مؤكدًا",hint.currency,hint.amount,"commitment",balance!=null,true);
        if(balance!=null) return new SmsPreview(sender,body,when,captureAt,sender,"رصيد فقط · ليس دخلًا أو مصروفًا",
                balance.currency,balance.amountMinor,"balance",true,commitment);
        if(commitment) return new SmsPreview(sender,body,when,captureAt,sender,"تحديث التزام · بدون تسجيل مصروف",
                "",0,"commitment",false,true);
        return null;
    }

    boolean isTransaction() { return "expense".equals(kind)||"income".equals(kind)||"transfer".equals(kind); }
    boolean canUseReceiptKey(java.util.Set<String> sentKeys) {
        return CaptureIdentity.receiptKeySafe(sender,body,receivedAt,captureAt,sentKeys);
    }
    static void selectAll(java.util.List<SmsPreview> rows, boolean onlyNew) {
        for(SmsPreview row:rows)row.selected=row.captureStatus!=2&&(!onlyNew||row.captureStatus==0)&&!"correction".equals(row.kind);
    }
}
