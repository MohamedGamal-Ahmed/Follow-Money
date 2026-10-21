package com.mohamedgamal.masareef;

/**
 * Every threshold, limit and word list of the replacement parsing pipeline, in one place.
 * Stages read these values; they never declare their own. Changing a value changes POLICY_VERSION.
 * The pipeline runs in shadow mode only: the legacy ExpenseParser path remains the sole ledger authority.
 */
final class ParsingPolicy {
    static final String POLICY_VERSION = "2026-10-22.1";
    static final String NORMALIZER_VERSION = "normalization-1";
    static final String CLASSIFIER_VERSION = "classification-1";
    /** Approved corpus v2 contract (tools/fixtures/parser-corpus-v2-draft.tsv, UTF-8/LF SHA-256 prefix). */
    static final String CORPUS_CONTRACT = "v2:bcf53eb0";

    // Shadow mode.
    static final boolean SHADOW_ENABLED = true;
    /** In-memory observations kept for the current process; nothing is persisted or uploaded. */
    static final int SHADOW_BUFFER_SIZE = 50;

    // Message normalization.
    /** Longer inputs are cut for the shadow stage only; the legacy path keeps the full text. */
    static final int MAX_INPUT_CHARS = 8000;
    /** Notification extras in assembly order; the same order the corpus uses to reconstruct a notification. */
    static final String[] NOTIFICATION_FIELDS = {"android.title", "android.text", "android.bigText",
            "android.textLines", "android.subText", "android.summaryText"};
    /** Android Notification.FLAG_GROUP_SUMMARY; a summary is flagged, never dropped, by normalization. */
    static final int FLAG_GROUP_SUMMARY = 0x200;

    // Merchant normalization. Upper-case processor tokens, matched only as a whole leading token.
    /** Removed before "*" when a merchant follows: KASHIER*BUTLERS, SQ *LUNA CAFE. */
    static final String[] STARRED_PROCESSORS = {"KASHIER", "PAYMOB", "FAWRY", "SQ", "PAYPAL", "GEIDEA", "STRIPE", "PAYSKY"};
    /** Removed before a space-separated merchant only: "Kashier Butlers". A lone token stays (PAYPAL). */
    static final String[] BARE_PROCESSORS = {"KASHIER", "PAYMOB"};
    /**
     * Trailing Latin country/city descriptors of card terminals, removed from the end only (CAIRO BOOKS keeps CAIRO).
     * Arabic place names are excluded: they are usually part of the name itself (بنك مصر, جامعة القاهرة).
     */
    static final String[] TRAILING_LOCATIONS = {"EG", "EGY", "EGYPT", "CAIRO", "GIZA", "ALEXANDRIA"};
    /** A trailing branch descriptor with its number ("Branch 12", "BR 009", "فرع 3"); the number alone is never removed. */
    static final String TRAILING_BRANCH = "\\s+(?:branch|br|فرع)\\s*\\d+$";
    /** A final English "at hh:mm" with no AM/PM and an hour of 1-12 is treated as a cut-off 12-hour time. */
    static final String TRUNCATED_CLOCK = "\\bat\\s+$";

    // Catalog lookup: exact, then branch prefix, then conservative fuzzy.
    /** A known alias accepts a following branch/location suffix only from this length. */
    static final int BRANCH_PREFIX_MIN_ALIAS_CHARS = 4;
    /** Fuzzy matching is never tried on shorter keys. */
    static final int FUZZY_MIN_KEY_CHARS = 6;
    /** Optimal-string-alignment (Damerau) edits allowed; one edit only. */
    static final int FUZZY_MAX_EDITS = 1;

    // Money units: {ISO code or "" for ambiguous, regex}. Order matters: longer spellings first.
    static final String[][] CURRENCY_UNITS = {
        {"GBP", "جنيه\\s+[اإ]سترليني"},
        {"EGP", "EGP|L\\.E\\.?|LE|E£|£E|ج\\.?\\s?م\\.?|جم|جنيه(?:ات|ا)?(?:\\s+مصري)?"},
        {"GBP", "GBP|£"},
        {"USD", "USD|US\\$|\\$|دولار(?:\\s+[اأ]مريكي)?"},
        {"EUR", "EUR|€|يورو"},
        {"SAR", "SAR|ر\\.\\s?س\\.?|ريال\\s+سعودي"},
        {"AED", "AED|درهم(?:\\s+[اإ]ماراتي)?|د\\.\\s?[اإ]\\.?"},
        {"", "ريال"},
    };

    // Classification vocabulary, matched against MessageNormalizer match text (lower case; أإآ->ا, ى->ي, ة->ه, no harakat).
    static final String VERIFICATION = "\\botp\\b|one.time password|verification (?:code|required)|security code|authentication code|" +
            "\\buse code\\b|\\bcode:? ?\\d{4,}|\\benter \\d{4,}|رمز (?:التحقق|تاكيد|الدخول)|كلمه (?:المرور|السر) لمره واحده";
    static final String SECURITY_REQUEST = "secure key|security key|رمز الامان";
    static final String BALANCE_INQUIRY = "balance inquiry|تم الاستعلام عن الرصيد|استعلام عن الرصيد";
    static final String BALANCE_WORDS = "\\bbalance\\b|\\bavailable\\b|رصيد|الرصيد|المتاح";
    static final String PROMOTION = "احصل|استمتع|كاش ?باك|اربح|(?<!\\p{L})(?:ال)?(?:عرض|عروض)(?!\\p{L})|اشترك|سجل الان|حمل التطبيق|هديه|مجانا|" +
            "بسعر|السعر الجديد|سعر اشتراك|قرض|للتقديم|النقاط|ترحيبي|ستحصل|اشتر الان|\\d+ ?%|" +
            "apply now|\\bget\\b|\\bwin\\b|enjoy|cashback|\\boffer\\b|promo|reward|eligible|\\bsave\\b|upgrade|price will change|new benefits|subscription price";
    /** Payment wording about the future or a request; a completed event in the same message wins over it. */
    static final String FUTURE_OR_REQUEST = "(?<!\\p{L})(?:و)?(?:سيتم|سوف يتم|سوف) (?:خصم|تحويل|سداد|دفع|سحب|تنفيذ)|هيتخصم|نذكرك|بنفكرك|" +
            "(?:يرجي|برجاء) (?:دفع|سداد)|طلب سداد|في انتظار موافقتك|" +
            "\\bwill (?:be )?(?:debit|debited|charge|charged|deduct|deducted|processed|paid|transferred|apply)\\b|" +
            "\\bis due\\b|\\bdue (?:on|tomorrow|today|date|before|by)\\b|\\b(?:payment|amount|minimum) due\\b|please pay|" +
            "\\bscheduled\\b|\\bpending\\b|payment request|accept or decline";
    static final String UNSUCCESSFUL = "declined|\\bfailed\\b|unsuccessful|could not|insufficient funds|\\bcancel(?:l)?ed\\b|" +
            "تم رفض|رفضت|مرفوض|(?<!\\p{L})لم (?:يتم|تتم)|فشل|تعذر|غير ناجح|تم الغاء";
    /** Money returned to the user: a refund or reversal is a transaction even when it names a cancelled operation. */
    static final String REFUND_OR_REVERSAL = "\\brefund(?:ed)?\\b|\\breversal\\b|\\breversed\\b|credited back|تم عكس|اعاده المبلغ|استرداد|(?<!\\p{L})تم رد";
    /** Completed money verbs. Arabic needs a standalone تم/تمت (not سيتم); English needs a past or completion form. */
    static final String COMPLETED_EVENT = "(?<!\\p{L})(?:تم|تمت) (?:الان )?(?:خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|ايداع|اضافه|استلام|ارسال|شحن|عكس|اعاده|رد|استخدام)(?! (?:الاسعار|السعر))|" +
            "\\b(?:paid|purchased|debited|credited|charged|received|transferred|deposited|withdrawn|sent|refunded|reversed)\\b|" +
            "\\bcash withdrawal\\b|\\bsuccessful(?:ly)?\\b|\\bcompleted\\b|" +
            // Statement-style movement nouns name a completed transfer ("تحويل صادر 5,000 EGP").
            "تحويل (?:صادر|وارد)|حواله (?:صادره|وارده)|\\b(?:outgoing|incoming) (?:transfer|remittance)\\b";
    /** A completed verb inside a condition ("if you pay", "إذا دفعت") is not an event. */
    static final String CONDITIONAL = "\\bif\\b|\\bwhen you\\b|(?<!\\p{L})(?:اذا|لو)(?!\\p{L})";
    /** Explicit completion without an amount still names a transaction (later stages mark the amount missing). */
    static final String COMPLETED_WITHOUT_AMOUNT = "payment completed|purchase completed|payment successful|تمت عمليه (?:الدفع|الشراء)|تم الدفع بنجاح";
    /** Amount followed by a merchant marker, plus a card mention: a terse card alert without a verb. */
    static final String CARD_CONTEXT = "\\bcard\\b|بطاقه|كارت";
    static final String MERCHANT_MARKER = "\\bat\\b|@|لدي|عند";
    /** Clause boundaries for counting events: sentence punctuation (not decimals), Arabic comma, "and", و before تم. */
    static final String CLAUSE_BOUNDARY = "(?<!\\d)[.](?!\\d)|[.](?=\\s)|[!?;؛،]|\\band\\b|(?<!\\p{L})و(?=تم)";

    // Extraction vocabulary, matched against length-preserving folded text (lower case; أإآٱ->ا, ؤ->و, ئ->ي, ى->ي, ة->ه).
    static final String EXTRACTOR_VERSION = "extraction-1";
    /** How far before an amount a role word (balance, fee, limit, equivalent) may sit inside the same clause. */
    static final int ROLE_WINDOW_CHARS = 30;
    static final String ROLE_BALANCE = "balance|available|رصيد|المتاح";
    static final String ROLE_LIMIT = "\\blimit\\b|الحد المتاح";
    static final String ROLE_FEE = "\\bfees?\\b|رسوم|عموله|commission";
    static final String ROLE_EQUIVALENT_BEFORE = "ما يعادل|يعادل|equivalent (?:to|of)";
    static final String ROLE_EQUIVALENT_AFTER = "^\\s*\\)?\\s*equivalent";
    /** A principal marker after a role word means the amount is the principal ("شحن رصيد اتصالات بقيمة 50"). */
    static final String PRINCIPAL_MARKER = "بقيمه|بمبلغ|مبلغ|\\bof\\b";
    static final String CREDIT_CARD = "credit card|بطاقه الايتمان|بطاقه ايتمان|كارت ايتمان";
    /** Direction words. Arabic verbs need a standalone تم/تمت; English needs past or completion forms. */
    static final String DIRECTION_IN = "(?<!\\p{L})(?:تم|تمت)\\s+(?:ايداع|اضافه|استلام|رد|عكس|اعاده)|اعاده المبلغ|تم\\s+شحن\\s+(?:(?:ال)?محفظ\\p{L}*|حسابك)|" +
            "\\b(?:credited|received|deposited|refunded|refund|reversal|inward)\\b|credited back|" +
            "تحويل وارد|حواله وارده|\\bincoming (?:transfer|remittance)\\b";
    static final String DIRECTION_OUT = "(?<!\\p{L})(?:تم|تمت)\\s+(?:خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|ارسال|شحن\\s+رصيد)|" +
            "\\b(?:paid|purchased|debited|charged|withdrawn|sent|transferred|outward)\\b|\\bcash withdrawal\\b|\\bpayment of\\b|\\btransfer of\\b|" +
            "\\bpayment (?:completed|successful)\\b|تحويل صادر|حواله صادره|\\boutgoing (?:transfer|remittance)\\b";
    /** Purchase wording where a merchant is expected; transfers, cash, installments, bills and refunds are excluded. */
    static final String PURCHASE = "(?<!\\p{L})تم\\s+(?:دفع|شراء|خصم)|\\b(?:paid|purchased|charged)\\b";
    static final String NOT_A_PURCHASE = "تحويل|transfer|سحب|withdraw|قسط|installment|فاتوره|\\bbill\\b|refund|reversal|عكس|اعاده|محفظه|wallet|instapay|ipn";
    static final String QUOTED_MERCHANT = "[\"“«]([^\"”»\\n]{1,60})[\"”»]";
    static final String MERCHANT_MARKER_STRONG = "\\bat\\b|@(?=\\s)|(?<!\\p{L})(?:لدي|عند)";
    static final String MERCHANT_MARKER_PARTY = "\\bfrom\\b|\\bto\\b|(?<!\\p{L})(?:من|الي)(?!\\p{L})|(?<!\\p{L})ل(?= )";
    /** Bill, recharge and subscription names: the billed service is the merchant. Adjectives after اشتراك are not names. */
    static final String MERCHANT_BILL = "فاتوره (\\p{L}+)|\\bfor (\\p{L}+) (?:mobile |internet |landline |electricity )?bill\\b|شحن رصيد (\\p{L}+)|" +
            "اشتراك (?!ال|شهري|سنوي|اسبوعي)(\\p{L}+)|\\bsubscription (?:to|for) (\\p{L}+)";
    /** Where a merchant run ends; includes "و" joined to a following action ("واعادة المبلغ", "وتم"). */
    static final String MERCHANT_STOP = "\\s+(?:on|with|for|using|via|ref|card|has|have|was|were|is|been|completed|تم|تمت|يوم|بتاريخ|في|فى|من|عبر|عن|رقم|بطاقه|باستخدام|المتاح|الرصيد|الساعه)(?!\\p{L})|" +
            "\\s+و(?=(?:تم|تمت|اعاده|استرداد|رد)(?!\\p{L}))|[.,;،؛:!?()\\n]|\\s+\\d|يوم(?=\\d)";
    static final String MERCHANT_GENERIC = "^(?:حساب|حسابك|حسابكم|بطاقه|بطاقتك|بطاقتكم|كارت|محفظه|محفظتك|رقم|ماكينه|account|card|wallet|your|the|you)(?!\\p{L})";
    static final int MERCHANT_MAX_CHARS = 40;
    /** Possibly between the user's own accounts: card/prepaid funding, wallet top-up, paying one's own card dues. */
    static final String INTERNAL_TRANSFER = "(?:الي|to) (?:كارت|بطاقه|credit card|prepaid)|تم\\s+شحن\\s+(?:ال)?محفظ|top.?up (?:your )?wallet|" +
            "سداد (?:مستحقات |مديونيه )?(?:ال)?(?:بطاقه|بطاقتك|بطاقتكم|كارت)|\\bcard (?:bill |balance )?(?:payment|repayment) (?:received|made|of)\\b";
    /** A phone-number counterparty after a direction word ("sent to 01012345678", "من رقم 010XXXXX123"). */
    static final String COUNTERPARTY_PHONE = "(?:من|الي|\\bto|\\bfrom)\\s+(?:رقم\\s+|number\\s+|mobile\\s+)?(\\+?[0-9][0-9x*]{9,14})(?![0-9])";
    /** A beneficiary name set off by dashes after an account mask ("حساب 1234XXXX5678 - محمد سعيد -"). */
    static final String COUNTERPARTY_NAME = "(?:حساب|account)\\s+[0-9x*#]{4,}\\s*[-–]\\s*(\\p{L}[\\p{L} .]{0,40}?)\\s*(?:[-–]|$)";
    /** Money returned to the user is an expense offset, never income (owner rule for الاسترداد and reversals). */
    static final String EXPENSE_OFFSET = "refund|reversal|reversed|credited back|عكس|اعاده المبلغ|استرداد|(?<!\\p{L})تم رد";
    static final String EXPIRY = "صالح حتي|valid until|expires?(?: on)?|تنتهي في";

    // Confidence (stage 4): additive evidence score 0..100, thresholds, review caps and hard failures.
    static final String CONFIDENCE_VERSION = "confidence-1";
    static final int SCORE_EVENT_EXPLICIT = 30;    // completed verb, refund/reversal, or signed amount in the event clause
    static final int SCORE_EVENT_CONTEXT = 15;     // only a terse card + merchant alert, no verb
    static final int SCORE_AMOUNT = 30;            // one unambiguous positive principal
    static final int SCORE_CURRENCY = 10;          // explicit currency unit
    static final int SCORE_DIRECTION_EXPLICIT = 20;
    static final int SCORE_DIRECTION_LIKELY = 5;
    static final int SCORE_IDENTITY = 10;          // merchant, counterparty, account or card is known
    static final int PENALTY_PROMO_SIGNALS = 10;   // promotional wording inside a completed event
    static final int AUTO_THRESHOLD = 80;
    static final int REVIEW_THRESHOLD = 50;
    /** Any of these makes the message UNUNDERSTOOD whatever the score: nothing ambiguous is booked. */
    static final String[] HARD_FAILURES = {"MISSING_AMOUNT", "AMBIGUOUS_CURRENCY", "INVALID_SIGNED_AMOUNT", "AMBIGUOUS_NUMBER_FORMAT",
            "NON_POSITIVE_AMOUNT", "AMBIGUOUS_DIRECTION"};
    /** Any of these caps the decision at REVIEW: the user must confirm ownership, expiry, merchant or direction. */
    static final String[] REVIEW_CAPS = {"POSSIBLE_INTERNAL_TRANSFER", "EXPIRING_CREDIT", "MISSING_MERCHANT", "INFERRED_OUTGOING_DIRECTION"};

    // Later stages: defined by the approved corpus, not yet used by any implemented stage.
    /** Identity-less identical messages within this window become possible-duplicate review items. */
    static final long DUPLICATE_CANDIDATE_WINDOW_MS = 60_000L;
    /** Same-source opposite Phone Banking legs within this window are possible internal transfers. */
    static final long INTERNAL_PAIRING_WINDOW_MS = 600_000L;
    /** A reversal offsets an original purchase with the same amount and merchant within this many days. */
    static final int EXPENSE_OFFSET_MATCH_DAYS = 14;

    private ParsingPolicy() {}
}
