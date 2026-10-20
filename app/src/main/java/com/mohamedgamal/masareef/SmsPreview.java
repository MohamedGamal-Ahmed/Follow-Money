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

    /** A currency next to a number («EGP 1,250.00», «1250 ج.م», «USD 20»): the one thing every transaction alert carries. */
    private static final java.util.regex.Pattern AMOUNT=java.util.regex.Pattern.compile(
            "(?iu)(?:(?:egp|usd|eur|gbp|sar|aed|kwd|le|ج\\.?\\s?م|جنيه|دولار|يورو|ريال|درهم)\\.?\\s*[0-9][0-9,.]*|[0-9][0-9,.]*\\s*(?:egp|usd|eur|gbp|sar|aed|kwd|le|ج\\.?\\s?م|جنيه|دولار|يورو|ريال|درهم)(?![\\p{L}]))");

    /**
     * Conservative support-only selection; never changes parser acceptance or books an operation. A known bank sender,
     * financial wording, an amount in the text (card-delivery notices, system-maintenance notes and «تم إضافة الراتب»
     * teasers carry none and are not formats to fix), not an OTP, not a promotion, and refused by the parser.
     */
    static boolean canReport(String sender,String body) {
        return BrandCatalog.bank(sender)!=null && ExpenseParser.isFinancialCandidate(body) && hasAmount(body)
                && !ExpenseParser.isVerificationMessage(body) && !ExpenseParser.isPromotion(body)
                && parse(sender,body,0)==null;
    }
    static boolean hasAmount(String body) { return body!=null && AMOUNT.matcher(ExpenseParser.normalizeDigits(body)).find(); }

    /**
     * The masked shape of a message for the samples tool: digits become #, a run of digits with its separators
     * (1,234.56 or 12:30) one #, a token mixing digits and letters (a reference such as 3a7f9c) ###, InstaPay handles
     * xxx@instapay and English month names MMM — so one bank template is one shape. Without the run collapse, 279 HSBC
     * messages gave 249 shapes (20 October).
     */
    static String shape(String text) {
        if(text==null)return "";
        String clean=ExpenseParser.normalizeDigits(text);
        clean=clean.replaceAll("(?i)[a-z0-9._-]+@instapay","xxx@instapay");
        // A reference has letters after digits (1a2b3c4d, 1234fc56); a masked account (xxx1234), a date token (05JUL26)
        // and an amount glued to its currency (56000EGP, EGP1250) are not references and keep their letters.
        clean=clean.replaceAll("(?i)(?<![\\p{L}0-9])(?![0-9]{1,2}(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[0-9]{2,4}(?![a-z0-9]))"
                +"(?![0-9][0-9,.]*(?:egp|usd|eur|gbp|sar|aed|kwd|le)(?![a-z0-9]))(?!(?:egp|usd|eur|gbp|sar|aed|kwd|le)[0-9])"
                +"(?=[a-z0-9]*[0-9][a-z0-9]*[a-z])[a-z0-9]{4,}(?![\\p{L}0-9])","###");
        clean=clean.replaceAll("(?i)(?<![a-z])(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)(?![a-z])","MMM");
        clean=clean.replaceAll("[0-9]+(?:[.,:/٬٫][0-9]+)*","#");
        clean=clean.replaceAll("[*]{2,}","**");
        return clean.replaceAll("\\s+"," ").trim();
    }
    boolean canUseReceiptKey(java.util.Set<String> sentKeys) {
        return CaptureIdentity.receiptKeySafe(sender,body,receivedAt,captureAt,sentKeys);
    }
    /** Explicit cancellation uses the same conservative matcher as live capture; declines/future notices stay out. */
    static void selectForImport(java.util.List<SmsPreview> rows){
        for(SmsPreview row:rows)row.selected=row.captureStatus==0&&(!"correction".equals(row.kind)||CaptureNotice.parse(row.sender,row.body)!=null);
    }
    static void selectAll(java.util.List<SmsPreview> rows, boolean onlyNew) {
        for(SmsPreview row:rows)row.selected=row.captureStatus!=2&&(!onlyNew||row.captureStatus==0)&&!"correction".equals(row.kind);
    }
}
