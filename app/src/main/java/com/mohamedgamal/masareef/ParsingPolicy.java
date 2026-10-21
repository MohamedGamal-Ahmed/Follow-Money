package com.mohamedgamal.masareef;

/**
 * Every threshold, limit and word list of the replacement parsing pipeline, in one place.
 * Stages read these values; they never declare their own. Changing a value changes POLICY_VERSION.
 * The pipeline runs in shadow mode only: the legacy ExpenseParser path remains the sole ledger authority.
 */
final class ParsingPolicy {
    static final String POLICY_VERSION = "2026-10-21.2";
    static final String NORMALIZER_VERSION = "normalization-1";
    static final String CLASSIFIER_VERSION = "classification-1";
    /** Approved corpus v2 contract (tools/fixtures/parser-corpus-v2-draft.tsv, UTF-8/LF SHA-256 prefix). */
    static final String CORPUS_CONTRACT = "v2:6c8b865e";

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
            "\\bcash withdrawal\\b|\\bsuccessful(?:ly)?\\b|\\bcompleted\\b";
    /** A completed verb inside a condition ("if you pay", "إذا دفعت") is not an event. */
    static final String CONDITIONAL = "\\bif\\b|\\bwhen you\\b|(?<!\\p{L})(?:اذا|لو)(?!\\p{L})";
    /** Explicit completion without an amount still names a transaction (later stages mark the amount missing). */
    static final String COMPLETED_WITHOUT_AMOUNT = "payment completed|purchase completed|payment successful|تمت عمليه (?:الدفع|الشراء)|تم الدفع بنجاح";
    /** Amount followed by a merchant marker, plus a card mention: a terse card alert without a verb. */
    static final String CARD_CONTEXT = "\\bcard\\b|بطاقه|كارت";
    static final String MERCHANT_MARKER = "\\bat\\b|@|لدي|عند";
    /** Clause boundaries for counting events: sentence punctuation (not decimals), Arabic comma, "and", و before تم. */
    static final String CLAUSE_BOUNDARY = "(?<!\\d)[.](?!\\d)|[.](?=\\s)|[!?;؛،]|\\band\\b|(?<!\\p{L})و(?=تم)";

    // Later stages: defined by the approved corpus, not yet used by any implemented stage.
    /** Identity-less identical messages within this window become possible-duplicate review items. */
    static final long DUPLICATE_CANDIDATE_WINDOW_MS = 60_000L;
    /** Same-source opposite Phone Banking legs within this window are possible internal transfers. */
    static final long INTERNAL_PAIRING_WINDOW_MS = 600_000L;
    /** A reversal offsets an original purchase with the same amount and merchant within this many days. */
    static final int EXPENSE_OFFSET_MATCH_DAYS = 14;

    private ParsingPolicy() {}
}
