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
    /**
     * The new pipeline's reading, computed once when the import lists the message and written exactly as listed:
     * decision with its observation, or failure (the pipeline could not decide; the legacy parser writes it). Plain
     * types so this class stays free of Android storage.
     */
    PipelineDecision decision;
    ParserShadow.Observation observation;
    String failure;

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

    /**
     * A movement the new pipeline records, listed with its own name, category and amount. legacy, when the legacy
     * reading also saw a balance or a commitment in the text, keeps those flags for the list.
     */
    static SmsPreview fromDecision(String sender, String body, long receivedAt, long sentAt, PipelineDecision decision,
                                   ParserShadow.Observation observation, SmsPreview legacy) {
        SmsPreview row=new SmsPreview(sender,body,receivedAt,CaptureIdentity.smsTimestamp(receivedAt,sentAt),decision.merchant,
                decision.category,decision.currency,decision.amountMinor,decision.kind(),
                legacy!=null&&legacy.balanceIncluded,legacy!=null&&legacy.commitmentIncluded);
        row.decision=decision; row.observation=observation;
        return row;
    }

    boolean isTransaction() { return "expense".equals(kind)||"income".equals(kind)||"transfer".equals(kind); }

    /** A currency next to a number («EGP 1,250.00», «1250 ج.م», «USD 20»): the one thing every transaction alert carries. */
    private static final java.util.regex.Pattern AMOUNT=java.util.regex.Pattern.compile(
            "(?iu)(?:(?:egp|usd|eur|gbp|sar|aed|kwd|le|ج\\.?\\s?م|جنيه|دولار|يورو|ريال|درهم)\\.?\\s*[0-9][0-9,.]*|[0-9][0-9,.]*\\s*(?:egp|usd|eur|gbp|sar|aed|kwd|le|ج\\.?\\s?م|جنيه|دولار|يورو|ريال|درهم)(?![\\p{L}]))");

    static boolean hasAmount(String body) { return body!=null && AMOUNT.matcher(ExpenseParser.normalizeDigits(body)).find(); }
    /** An amount with a non-zero digit («0.00 جنيه» is not one; an unclear «EUR 1.250» is). */
    static boolean hasPositiveAmount(String body) {
        if(body==null)return false;
        java.util.regex.Matcher amount=AMOUNT.matcher(ExpenseParser.normalizeDigits(body));
        while(amount.find())if(amount.group().matches("(?s).*[1-9].*"))return true;
        return false;
    }

    /**
     * Lossy diagnostic grouping only: every variable value becomes the same slot regardless of its length.
     * Never use a shape as transaction identity, a dedupe key or proof that a message is a payment.
     * This is deliberately separate from parsing, evidence storage and financial acceptance.
     */
    static String shape(String text) {
        if(text==null)return "";
        String slot="<slot>";
        String clean=ExpenseParser.normalizeDigits(text).replaceAll("[\\u200e\\u200f\\u202a-\\u202e]","")
                .replaceAll("[\\u064b-\\u065f\\u0670\\u0640]","")
                .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي');
        // Consume complete compound values before replacing bare digit runs or existing redaction hashes.
        clean=clean.replaceAll("(?i)(?:https?://|www\\.)[^\\s\\p{Z}]+",slot);
        clean=clean.replaceAll("(?i)[a-z0-9._-]+@instapay",slot);
        clean=clean.replaceAll("(?iu)((?:\\bref(?:erence)?\\.?|رقم\\s*(?:مرجعي|المرجع|العملية))\\s*#?\\s*[:=]?\\s*)[a-f0-9#-]+","$1"+slot);
        clean=clean.replaceAll("(?i)(?<![\\p{L}\\p{N}])(?=[0-9x*#-]*[x*])(?=[0-9x*#-]*[0-9*#])[0-9x*#]+(?:-[0-9x*#]+)*(?![\\p{L}\\p{N}])",slot);
        String day="[0-9#]{1,}";
        String months="(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|يناير|فبراير|مارس|ابريل|مايو|يونيو|يوليو|اغسطس|سبتمبر|اكتوبر|نوفمبر|ديسمبر)";
        // Existing ###-Oct-#### support drafts and original 21OCT26 dates must have identical length-free structure.
        String numericDay="(?:0?[1-9]|[12][0-9]|3[01]|#+)", numericMonth="(?:0?[1-9]|1[0-2]|#+)", year="(?:[0-9]{4}|[0-9]{2}|#+)";
        String numericDate="(?:"+year+"[-/]"+numericMonth+"[-/]"+numericDay+"|"+numericDay+"[-/]"+numericMonth+"(?:[-/]"+year+")?|"+numericDay+"\\."+numericMonth+"\\."+year+")";
        clean=clean.replaceAll("(?iu)(?<![\\p{L}\\p{N}.,])(?:"+day+"[- /]?"+months+"[- /]?"+day+"|"+numericDate+")(?![\\p{L}\\p{N}]|[.,][0-9])",slot);
        clean=clean.replaceAll("(?iu)(?<![\\p{L}\\p{N}])[0-9#]+:[0-9#]+(?::[0-9#]+)?(?:\\s*(?:[ap]m|ص|م|صباحا|مساء|ظهرا))?(?![\\p{L}\\p{N}])",slot);
        String number="[+-]?[0-9#]+(?:[.,٬٫][0-9#]+|[ \\u00a0\\u202f][0-9#]{3})*[+-]?";
        String currency="(?:egp|usd|eur|gbp|sar|aed|kwd|le|l\\.e\\.|ج\\.?\\s*م\\.?|جنيه|دولار|يورو|ريال|درهم)";
        clean=clean.replaceAll("(?iu)(?<![\\p{L}\\p{N}])(?:"+currency+"\\s*"+number+"|"+number+"\\s*"+currency+")(?![\\p{L}\\p{N}])",slot);
        clean=clean.replaceAll("(?i)(?<![\\p{L}\\p{N}])(?=[0-9x*#-]*[x*#])(?=[0-9x*#-]*[0-9*#])[0-9x*#]+(?:-[0-9x*#]+)*(?![\\p{L}\\p{N}])",slot);
        // Unlabelled mixed hex references are values; ordinary words such as CAFE must survive.
        clean=clean.replaceAll("(?i)(?<![\\p{L}\\p{N}])(?=[a-f0-9]*[a-f])(?=[a-f0-9]*[0-9])[a-f0-9]{4,}(?![\\p{L}\\p{N}])",slot);
        clean=clean.replaceAll("[0-9]+(?:[.,:/٬٫-][0-9]+)*|(?<![\\p{L}])#+",slot);
        clean=clean.replaceAll("<slot>[+-]",slot);
        return clean.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s\\p{Z}]+"," ").trim();
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
