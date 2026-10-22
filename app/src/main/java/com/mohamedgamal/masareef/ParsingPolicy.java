package com.mohamedgamal.masareef;

/**
 * Every threshold, limit and word list of the replacement parsing pipeline, in one place.
 * Stages read these values; they never declare their own. Changing a value changes POLICY_VERSION.
 * The pipeline runs in shadow mode only: the legacy ExpenseParser path remains the sole ledger authority.
 */
final class ParsingPolicy {
    static final String POLICY_VERSION = "2026-10-29.1";
    static final String NORMALIZER_VERSION = "normalization-2";
    static final String CLASSIFIER_VERSION = "classification-3";
    /** Approved corpus v2 contract (tools/fixtures/parser-corpus-v2-draft.tsv, UTF-8/LF SHA-256 prefix). */
    static final String CORPUS_CONTRACT = "v2:3e51e960";

    // Shadow mode.
    static final boolean SHADOW_ENABLED = true;
    /**
     * Step 7 (owner approval, 21 Oct 2026): the new pipeline decides what is recorded in the test build and not in the
     * release build. Advanced settings switch it back at once (DecisionSwitch); only messages after activation follow it.
     */
    static final boolean NEW_PIPELINE_DECIDES_TEST_BUILD = true;
    static final boolean NEW_PIPELINE_DECIDES_RELEASE_BUILD = false;
    /** After the switch, the legacy parser's opinion is still computed (never written) for this many days. */
    static final int LEGACY_OPINION_DAYS = 30;
    /** In-memory observations kept for the current process; nothing is uploaded. */
    static final int SHADOW_BUFFER_SIZE = 50;
    /** Stored observations (ShadowStore, values sealed by EvidenceVault); the oldest are removed first. */
    static final int SHADOW_STORE_MAX_ROWS = 5000;
    /** Stored "messages I did not understand" (distinct message hashes); the least recently seen are removed first. */
    static final int UNUNDERSTOOD_MAX_ROWS = 500;
    /** Shadow re-scan: stored rows at most (later messages are only counted), and inbox rows per write transaction. */
    static final int RESCAN_MAX_ROWS = 10_000;
    static final int RESCAN_BATCH = 200;

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
    /** Removed before a space-separated merchant only: "Kashier Butlers", "Bill Payment Vodafone". A lone token stays (PAYPAL). */
    static final String[] BARE_PROCESSORS = {"KASHIER", "PAYMOB", "BILL PAYMENT"};
    /**
     * Trailing Latin country/city descriptors of card terminals, removed from the end only (CAIRO BOOKS keeps CAIRO).
     * Arabic place names are excluded: they are usually part of the name itself (بنك مصر, جامعة القاهرة).
     */
    static final String[] TRAILING_LOCATIONS = {"EG", "EGY", "EGYPT", "CAIRO", "GIZA", "ALEXANDRIA"};
    /** Company forms: removed only with a one-letter fragment cut after them ("BUTLERS & CO C" → butlers); "& Co" alone stays. */
    static final String[] TRAILING_LEGAL_FORMS = {"CO", "COMPANY", "LTD", "LLC", "INC", "SAE", "CORP"};
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
    /** The account currency of an amount that is labelled ("بمبلغ 500") but carries no currency in a message with none at all. */
    static final String DEFAULT_CURRENCY = "EGP";
    static final String[][] CURRENCY_UNITS = {
        {"GBP", "جنيه\\s+[اإ]سترليني"},
        {"EGP", "EGP|L\\.E\\.?|LE|E£|£E|egyptian\\s+pounds?|ج\\.?\\s?م\\.?|جم|جنيه(?:ات|ا)?(?:\\s+مصري)?"},
        {"GBP", "GBP|£"},
        {"USD", "USD|US\\$|\\$|دولار(?:\\s+[اأ]مريكي)?"},
        {"EUR", "EUR|€|يورو"},
        {"SAR", "SAR|ر\\.\\s?س\\.?|ريال\\s+سعودي"},
        {"AED", "AED|درهم(?:\\s+[اإ]ماراتي)?|د\\.\\s?[اإ]\\.?"},
        {"", "ريال"},
    };

    // Classification vocabulary, matched against MessageNormalizer match text (lower case; أإآ->ا, ى->ي, ة->ه, no harakat).
    static final String VERIFICATION = "\\botp\\b|one.time password|verification (?:code|required)|security code|authentication code|" +
            "\\buse code\\b|\\bcode:? ?\\d{4,}|\\benter \\d{4,}|رمز (?:التحقق|تاكيد|الدخول)|كلمه (?:المرور|السر) لمره واحده|" +
            // "Enter the code" with digits is a code message even when an amount is mentioned.
            "(?<!\\p{L})ادخل (?:ال)?(?:رمز|كود)|(?<!\\p{L})(?:الرمز|الكود) \\d{4,}|\\benter (?:the )?(?:code|otp)\\b";
    /** A code message needs a standalone 4-8 digit code; "OTP" naming the payment method of a completed payment is not one. */
    static final String OTP_CODE = "(?<![\\d.,/:#*x-])\\d{4,8}(?![\\d,/:-]|\\.\\d)";
    /** A status update or confirmation of an earlier debit moves no new money. */
    static final String NO_NEW_MOVEMENT = "(?<!\\p{L})(?:تم|تمت)\\s+(?:تفعيل|تنشيط)\\s+(?:ال)?(?:كارت|بطاق)\\p{L}*|\\bcard\\b.{0,20}?\\b(?:has been |was |is )?(?:activated|enabled)\\b|\\b(?:activated|enabled) your\\b.{0,15}?\\bcard\\b|تحديث حاله|لا يوجد خصم جديد|لا يوجد اي خصم جديد|عند انشاء العمليه|\\bstatus (?:was |has been )?updated\\b|\\bno new (?:debit|charge)\\b|(?:تم خصمه|خصم) (?:امس|سابقا)";
    /**
     * A reference to an earlier operation ("الحركة التي تمت ببطاقتكم … هي آخر حركة …", "the transaction made with your
     * card"), usually with instructions. Without a new movement verb of its own it moves no money (NO_NEW_MOVEMENT).
     */
    static final String TRANSACTION_NOUN_AR = "(?:ال)?(?:حرك|معامل|عملي)(?:ه|ات|تك|تكم|اتك|اتكم)";
    static final String PAST_TRANSACTION_REFERENCE = "(?<!\\p{L})" + TRANSACTION_NOUN_AR + "\\s+(?:التي|الذي|اللي)\\s+(?:تمت|تم|اجريت|اجريتها|قمت)(?!\\p{L})|" +
            "(?<!\\p{L})(?:هي\\s+)?(?:اخر|احدث)\\s+" + TRANSACTION_NOUN_AR + "(?!\\p{L})|(?<!\\p{L})" + TRANSACTION_NOUN_AR + "\\s+(?:ال)?(?:سابق|اخير|ماضي)(?:ه)?(?!\\p{L})|" +
            "\\b(?:the|your)\\s+(?:last\\s+|previous\\s+|recent\\s+)?(?:transaction|purchase|payment)s?\\s+(?:that\\s+)?(?:you\\s+)?(?:made|did|done|performed|completed)\\b|" +
            "\\byour\\s+(?:last|previous|recent)\\s+(?:transaction|purchase|payment)\\b";
    static final String NEW_MOVEMENT_VERB = "(?<!\\p{L})تم (?:الان )?(?:خصم|سحب|دفع|تحويل|ايداع|اضافه|شراء|سداد)|" +
            "\\b(?:was|were|has been|have been) (?:debited|charged|credited|deducted|withdrawn|paid|transferred)\\b";
    /**
     * Owner decision (21 Oct 2026): an installment or financed-purchase message from the provider that names no debited
     * bank account, card or wallet ("تم السداد للقسط الشهري … القيمة المستحقة", "اجمالي المبلغ الممول … قسطك") belongs to the
     * commitments path, not the ledger. An installment debited from a named account or card stays a transaction.
     */
    static final String INSTALLMENT_WORDING = "قسط|اقساط|\\binstall?ments?\\b|(?<!\\p{L})(?:ال)?(?:مبلغ )?(?:ال)?ممول|\\bfinanced\\b";
    static final String DEBITED_ACCOUNT_MASK = "[*x#]{2,}\\s*\\d{2,}|(?:المنتهي\\p{L}*|\\bending(?: with)?|\\bno\\.?)\\s*(?:ب\\p{L}?\\s*)?#?[*x#]*\\d{3,}|\\bfrom (?:your )?(?:account|card|wallet)\\b|(?<!\\p{L})من (?:حساب|بطاق|محفظ)\\p{L}*";
    /**
     * A provider subscription or renewal paid from the line's own prepaid credit ("… pound has been deducted from your
     * <line>"), naming no bank, card or wallet. The money left the ledger when the line was topped up, so recording it
     * again may double count: service_charge, PREPAID_CREDIT (review).
     */
    static final String PREPAID_SERVICE = "\\bsubscription\\b|\\brenew(?:ed|al)\\b|\\bbundle\\b|\\bpackage\\b|(?<!\\p{L})(?:ال)?(?:باق|اشتراك)\\p{L}*";
    /** A summary of several operations ("ملخص معاملات اليوم", "3 transactions") is several events. */
    static final String SUMMARY = "(?<!\\p{L})ملخص|\\bsummary\\b|(?<!\\d)\\d+ (?:عمليات|معاملات|transactions)\\b";
    /** A failed operation whose fee was kept: the kept fee is the only movement. */
    static final String RETAINED_FEE = "غير مسترده|غير قابله للاسترداد|\\bnon.?refundable\\b|\\bnot refunded\\b";
    /** An adjusted card operation ("تم تعديل عملية … المبلغ النهائي"): the final amount, for review. */
    static final String ADJUSTED = "(?<!\\p{L})تم تعديل (?:ال)?عمليه|(?:ال)?مبلغ (?:ال)?نهائي|\\bfinal amount\\b|\\bamount (?:was |has been )?adjusted\\b";
    /** Quoted words that name a state, never a merchant. */
    static final String STATUS_WORDS = "^(?:قيد التنفيذ|مكتمله|ناجحه|مرفوضه|معلقه|pending|completed|processing|successful|failed|declined)$";
    /** Money not moved yet (a code or request created, "not withdrawn yet"): ignored with PENDING_EVENT. */
    static final String PENDING_EVENT = "(?<!\\p{L})لم (?:يتم|تتم) (?:ال)?\\p{L}+ بعد|\\bnot (?:yet )?(?:been )?(?:withdrawn|debited|completed|processed) yet\\b|كود سحب|withdrawal code";
    /** A card authorization hold released: no money moved. Ignored with HOLD_RELEASED. */
    static final String HOLD_RELEASED = "(?:ال)?حجز (?:ال)?مؤقت|\\b(?:hold|authori[sz]ation) (?:was |has been )?(?:released|reversed|cancell?ed)\\b";
    /** A confirmed purchase order / buy-now-pay-later plan: a commitment the user must see, never ignored. */
    static final String COMMITMENT = "تاكيد طلب (?:ال)?شراء|\\b(?:purchase )?order (?:has been |was )?confirmed\\b|طلب شراء بالتقسيط";
    static final String SECURITY_REQUEST = "secure key|security key|رمز الامان";
    static final String BALANCE_INQUIRY = "balance inquiry|تم الاستعلام عن الرصيد|استعلام عن الرصيد";
    static final String BALANCE_WORDS = "\\bbalance\\b|\\bavailable\\b|رصيد|الرصيد|المتاح";
    static final String PROMOTION = "احصل|استمتع|كاش ?باك|اربح|(?<!\\p{L})(?:ال)?(?:عرض|عروض)(?!\\p{L})|اشترك|سجل الان|حمل التطبيق|هديه|مجانا|" +
            // A percentage or the word "loan" alone is not a promotion (fees, VAT, tax rates, loan installments).
            "بسعر|السعر الجديد|سعر اشتراك|الحصول علي قرض|قرض (?:شخصي )?خلال|للتقديم|النقاط|ترحيبي|ستحصل|اشتر الان|" +
            "(?:خصم|كاش ?باك|cashback|save) ?\\d+ ?%|\\d+ ?% ?(?:off|خصم|كاش ?باك|cashback|discount)|" +
            "apply now|\\bget\\b|\\bwin\\b|enjoy|cashback|\\boffer\\b|promo|reward|eligible|\\bsave\\b|upgrade|price will change|new benefits|subscription price";
    /** Payment wording about the future or a request; a completed event in the same message wins over it. */
    static final String FUTURE_OR_REQUEST = "(?<!\\p{L})(?:و)?(?:سيتم|سوف يتم|سوف) (?:خصم|تحويل|سداد|دفع|سحب|تنفيذ)|هيتخصم|نذكرك|بنفكرك|" +
            "(?:يرجي|برجاء) (?:دفع|سداد)|طلب سداد|في انتظار موافقتك|" +
            "\\bwill (?:be )?(?:debit|debited|charge|charged|deduct|deducted|processed|paid|transferred|apply)\\b|" +
            "\\bis due\\b|\\bdue (?:on|tomorrow|today|date|before|by)\\b|\\b(?:payment|amount|minimum) due\\b|please pay|" +
            // Statement and due wording: a future obligation, never a movement ("Your minimum payment is … to be paid by …").
            "\\b(?:to|must|should) be (?:paid|settled)\\b|\\bminimum (?:amount|payment|due)\\b|\\b(?:statement|outstanding|closing) balance\\b|" +
            "(?<!\\p{L})(?:ال)?حد (?:ال)?ادني\\s+(?:لل)?(?:سداد|دفع|مطلوب|مستحق)|(?<!\\p{L})(?:تاريخ|موعد)\\s+(?:ال)?(?:استحقاق|سداد)|(?<!\\p{L})(?:ال)?مستحق\\p{L}*\\s+(?:لل)?(?:سداد|دفع)|" +
            "\\bscheduled\\b|\\bpending\\b|payment request|accept or decline|" +
            "(?<!\\p{L})مستحق(?:ه)? (?:غدا|اليوم|في|بتاريخ|خلال)|تاريخ الاستحقاق|(?<!\\p{L})يستحق";
    static final String UNSUCCESSFUL = "declined|\\bfailed\\b|unsuccessful|could not|insufficient funds|\\bcancel(?:l)?ed\\b|" +
            "تم رفض|رفضت|مرفوض|(?<!\\p{L})لم (?:يتم|تتم)|فشل|تعذر|غير ناجح|تم الغاء";
    /** Money returned to the user: a refund or reversal is a transaction even when it names a cancelled operation. */
    static final String REFUND_OR_REVERSAL = "\\brefund(?:ed)?\\b|\\breversal\\b|\\breversed\\b|credited back|تم عكس|اعاده المبلغ|(?:اعاده|اعيد)\s+(?:ال)?قيد|استرداد|(?<!\\p{L})تم رد";
    /**
     * Completion markers, not a verb list: a standalone تم/تمت followed (optionally after one word such as "عملية" or
     * "تنفيذ") by a financial noun, with or without ال; "بنجاح"; or an English completion word. Registration, creation,
     * issuance, confirmation of a request, refusal, cancellation and suspension are not completions.
     */
    static final String NOUNS_IN = "ايداع|اضافه|استلام|رد|عكس|اعاده";
    static final String NOUNS_OUT = "خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|ارسال|شحن";
    /** Settling a debt ("تم تسوية مديونية بطاقتك") is a completed outgoing payment; a credit adjustment ("تسوية دائنة") is not. */
    static final String DEBT_SETTLEMENT = "(?<!\\p{L})(?:تم|تمت)\\s+تسويه\\s+(?:ال)?(?:مديونيه|مستحقات)";
    static final String FINANCIAL_NOUN = NOUNS_IN + "|" + NOUNS_OUT + "|استخدام|تنفيذ";
    /**
     * تم/تمت, optionally one word that is neither a direction noun nor a registration/refusal word ("عملية", "تنفيذ"),
     * then the noun with or without ال and with an optional possessive ("تحويلك"). Used for completion and direction.
     */
    static String completion(String nouns) {
        return "(?<!\\p{L})(?:تم|تمت)\\s+(?:الان\\s+)?(?:(?!(?:ال)?(?:" + NOUNS_IN + "|" + NOUNS_OUT + ")(?!\\p{L})|تسجيل|انشاء|اصدار|تفعيل|تاكيد|طلب|رفض|الغاء|ايقاف)\\p{L}+\\s+)?" +
                "(?:ال)?(?:" + nouns + ")(?:ك|كم)?(?!\\p{L})";
    }
    /**
     * "You have requested a transfer … by: EGP 500": the user submitted a transfer; the bank has not confirmed it was
     * executed. A likely outgoing movement for review (TRANSFER_REQUESTED), never silently ignored or booked.
     */
    static final String REQUESTED_TRANSFER = "\\b(?:you(?: have)?|your)\\s+(?:requested|submitted|initiated|placed)\\b.{0,25}?\\b(?:transfer|payment)\\b|" +
            "\\b(?:transfer|payment) request\\s+(?:has been |was )?(?:received|submitted|placed)\\b|" +
            "(?<!\\p{L})(?:تم\\s+)?(?:استلام|تقديم|تسجيل)\\s+طلب(?:ك|كم)?\\s+(?:ب|ل)?(?:ال)?(?:تحويل|دفع)|(?<!\\p{L})طلب(?:ك|كم)\\s+(?:ب|ل)?(?:ال)?تحويل";
    /** First-person completed verbs, as payment apps write them ("لقد استلمت", "لقد أرسلت", "استلمت", "دفعت"). Owner test, 22 Oct 2026. */
    static final String FIRST_PERSON_IN = "(?<!\\p{L})(?:لقد\\s+)?استلمت(?!\\p{L})";
    static final String FIRST_PERSON_OUT = "(?<!\\p{L})(?:لقد\\s+)?(?:ارسلت|دفعت|حولت|سددت)(?!\\p{L})";
    static final String COMPLETED_EVENT = completion(FINANCIAL_NOUN) + "(?!\\s+(?:الاسعار|السعر))|(?<!\\p{L})بنجاح(?!\\p{L})|" + DEBT_SETTLEMENT + "|" +
            FIRST_PERSON_IN + "|" + FIRST_PERSON_OUT + "|" +
            "\\b(?:paid|purchased|debited|deducted|credited|charged|received|transferred|deposited|withdrawn|sent|refunded|reversed|approved|posted)\\b|" +
            REQUESTED_TRANSFER + "|" + "\\bcash withdrawal\\b|\\bsuccessful(?:ly)?\\b|\\bcompleted\\b|\\bwas used (?:for|at|in)\\b|\\bthank(?:s| you) for (?:your )?(?:payment|paying)\\b|\\bdeposit(?:ed)? of\\b.{0,40}?\\b(?:was|has been|is)\\b|" + COMMITMENT + "|" +
            // Statement-style movement nouns name a completed transfer ("تحويل صادر 5,000 EGP").
            "تحويل (?:صادر|وارد)|حواله (?:صادره|وارده)|\\b(?:outgoing|incoming) (?:transfer|remittance)\\b";
    /** A completed verb inside a condition ("if you pay", "إذا دفعت") is not an event. */
    static final String CONDITIONAL = "\\bif\\b|\\bwhen you\\b|(?<!\\p{L})(?:اذا|لو)(?!\\p{L})";
    /** Explicit completion without an amount still names a transaction (later stages mark the amount missing). */
    static final String COMPLETED_WITHOUT_AMOUNT = "payment completed|purchase completed|payment successful|تمت عمليه (?:الدفع|الشراء)|تم الدفع بنجاح";
    /** Amount followed by a merchant marker, plus a card mention: a terse card alert without a verb. */
    static final String CARD_CONTEXT = "\\bcard\\b|بطاق|كارت";
    static final String MERCHANT_MARKER = "\\bat\\b|@|لدي|عند";
    /** Clause boundaries for counting events: sentence punctuation (not decimals), Arabic comma, "and", و before تم or a number. */
    static final String CLAUSE_BOUNDARY = "(?<!\\d)[.](?!\\d)|[.](?=\\s)|[!?;؛،]|\\band\\b|(?<!\\p{L})و(?=تم)|(?<!\\p{L})و(?=\\s*\\d)";
    /**
     * Safety rule: once a message has one completed event, another clause with its own amount and a merchant marker is a
     * second event ("Paid EGP 60 at A and EGP 90 at B"), unless a balance/fee/limit/total word owns that amount.
     */
    /** A sentence that states the event's amount ("المبلغ 725 جنيه"), so it can pair with a completion in the previous one. */
    static final String AMOUNT_STATEMENT = "(?<!\\p{L})(?:ال)?(?:مبلغ|قيمه)(?!\\p{L})|\\bamount\\b";
    static final String CLAUSE_AMOUNT_ROLE_WORD ="balance|available|رصيد|المتاح|\\bfees?\\b|رسوم|مصاريف|\\blimit\\b|\\btotal\\b(?!\\s+(?:\\p{L}+\\s+)?(?:purchase|transaction|payment|withdrawal|pos)s?\\b)|اجمالي|الاجمالي";
    /** An amount stated as an exchange-rate or equivalent figure is never a second event. */
    static final String CLAUSE_EQUIVALENCE = "\\brate\\b|\\bexchange\\b|\\bequivalent\\b|المعادل|المقيد|سعر (?:ال)?صرف";

    // Extraction vocabulary, matched against length-preserving folded text (lower case; أإآٱ->ا, ؤ->و, ئ->ي, ى->ي, ة->ه).
    static final String EXTRACTOR_VERSION = "extraction-4";
    /** How far before an amount a role word (balance, fee, limit, equivalent) may sit inside the same clause. */
    static final int ROLE_WINDOW_CHARS = 60;
    /** "شحن رصيدك" / "recharge balance" names what was topped up, not a stated balance. */
    static final String ROLE_BALANCE = "(?<!recharge )(?<!top up )balance|available|(?<!شحن )(?<!شحن ال)رصيد|المتاح|(?<!\\p{L})متاح(?=\\s+(?:الان|حاليا)(?!\\p{L}))";
    /** Book/ledger balance, earlier hold, released difference: known, never principal or the reported balance. */
    static final String ROLE_HOLD_OR_BOOK = "(?:ال)?رصيد (?:ال)?دفتري|\\b(?:ledger|book) balance\\b|حجز|محجوز|\\bhold\\b|الفرق|\\bdifference\\b|تحرير|رصيد دائن|\\bcredit balance\\b";
    /** Earlier or original operation amounts are references. */
    static final String ROLE_REFERENCE = "سابق|\\boriginal\\b|(?<!\\p{L})اصل(?!\\p{L})|\\bpreviously\\b|(?<!thank you )(?<!thanks )\\bfor your\\b";
    /** Rules, percentages bases and exchange rates: "بحد أدنى 3 جنيهات", "rate: EGP 49.80/USD". */
    static final String ROLE_RULE = "بحد (?:ادني|اقصي)|\\b(?:minimum|maximum|min|max) of\\b|\\brate\\b|سعر (?:ال)?(?:صرف|تحويل)";
    static final String ROLE_RATE_AFTER = "^\\s*/\\s*[a-z]{3}\\b";
    /** Future installment amounts ("قيمة القسط 800", "القسط الشهري الجديد 950"). */
    static final String ROLE_FUTURE_AMOUNT = "قيمه القسط|القسط الشهري|\\binstall?ment amount\\b|\\bnext (?:month|install?ment)\\b";
    /** The final amount of an adjusted operation. */
    static final String ROLE_FINAL = "(?:ال)?مبلغ (?:ال)?نهائي|\\bfinal amount\\b";
    /** An account-currency amount "credited/debited/posted at the rate" is the equivalent of the foreign one. */
    static final String ROLE_BOOKED_AFTER = "^\\s*(?:credited|debited|charged|posted|booked)\\b";
    /** "خُصم 1,600 من حسابك" after a colon or comma: the amount actually debited (passive, no تم). */
    static final String ROLE_DEBITED_PASSIVE = "[:،,]\\s*خصم\\s*$";
    static final String ROLE_LIMIT = "\\blimit\\b|الحد المتاح|available credit|credit limit|(?:ال)?حد (?:ال)?ايتماني";
    /** The amount booked in the account currency ("القيمة المقيدة"): the accounting amount, like a stated equivalent. */
    static final String ROLE_BOOKED = "القيمه المقيده|المبلغ المقيد|booked amount|amount booked|المعادل|\\bequivalent:?|(?:ب)?(?:ال)?قيمه (?:ال)?اجماليه|\\bposted amount\\b";
    /** The amount actually taken ("المبلغ المخصوم", "صافي الخصم"): replaces the nominal principal when no fee explains the gap. */
    static final String ROLE_DEBITED = "(?:اجمالي )?المبلغ المخصوم|اجمالي الخصم|صافي الخصم|\\bnet debit\\b|\\btotal debit(?:ed)?\\b|\\bamount debited\\b|" +
            "صافي المبلغ(?: المضاف| المستلم)?|\\bnet (?:amount|credit)\\b";
    /** Other explicitly named amounts: never principal, never "unassigned". */
    static final String ROLE_KNOWN = "قيمه العمليه الاصليه|المبلغ الاصلي|\\boriginal transaction(?: amount)?\\b|القيمه الاسميه|\\bface value\\b|\\bnominal (?:value|amount)\\b";
    static final String ROLE_FEE = "\\bfees?\\b|رسوم|مصاريف|عموله|commission|غرامه|ضريبه|\\bpenalty\\b|\\btax\\b";
    static final String ROLE_EQUIVALENT_BEFORE = "ما يعادل|يعادل|equivalent (?:to|of)";
    static final String ROLE_EQUIVALENT_AFTER = "^\\s*\\)?\\s*equivalent";
    /** An amount opening a parenthesis right after another amount in a different currency is that amount's equivalent. */
    static final String ROLE_EQUIVALENT_PAREN = "\\(\\s*$";
    /** A stated total (principal plus fee) is never the principal and never a balance; "Total POS Purchase" is a description. */
    static final String ROLE_TOTAL = "\\btotal\\b(?!\\s+(?:\\p{L}+\\s+)?(?:purchase|transaction|payment|withdrawal|pos)s?\\b)|الاجمالي|اجمالي|المجموع";
    /** A principal marker after a role word means the amount is the principal ("شحن رصيد اتصالات بقيمة 50"). */
    static final String PRINCIPAL_MARKER = "بقيمه|بمبلغ|مبلغ|\\bof\\b";
    /** Credit-card wording, including the adjective after the bank name ("بطاقة بنك مصر الائتمانية"). */
    static final String CREDIT_CARD = "credit card|بطاقه الايتمان|بطاقه ايتمان|كارت ايتمان|بطاق\\p{L}*(?:\\s+\\p{L}+){0,3}\\s+الايتمانيه";
    /** Direction words. Arabic verbs need a standalone تم/تمت; English needs past or completion forms. */
    static final String DIRECTION_IN = completion(NOUNS_IN) + "|" + FIRST_PERSON_IN + "|اعاده المبلغ|تم\\s+شحن\\s+(?:(?:ال)?محفظ\\p{L}*|حسابك)|" +
            "\\b(?:credited|received|deposited|refunded|refund|reversal|inward)\\b|\\bdeposit of\\b|credited back|" +
            "تحويل وارد|حواله وارده|\\bincoming (?:transfer|remittance)\\b";
    /** Strong outgoing words: they beat the role of the user's account in the sentence. */
    static final String DIRECTION_OUT = completion("خصم|سحب|دفع|سداد|تسديد|شراء|ارسال") + "|" + FIRST_PERSON_OUT + "|" + DEBT_SETTLEMENT + "|(?<!\\p{L})(?:تم|تمت)\\s+شحن\\s+(?!(?:ال)?(?:محفظ|حساب|بطاق|كارت))|" +
            "\\b(?:paid|paying|purchased|debited|deducted|charged|withdrawn|sent)\\b|\\bcash withdrawal\\b|\\bpayment of\\b|" +
            "\\bpayment (?:completed|successful)\\b|تحويل صادر|حواله صادره|\\boutgoing (?:transfer|remittance)\\b|\\bwas used (?:for|at|in)\\b|" +
            "\\bpurchase (?:approved|completed|of)\\b|\\bcard\\b.{0,40}?\\b(?:had|made|completed) a successful\\b|\\bsuccessful (?:card )?(?:purchase|transaction|payment|withdrawal)\\b|" + REQUESTED_TRANSFER + "|" + COMMITMENT + "|" + ADJUSTED;
    /** Neutral transfer words: they decide only when no strong word and no account role does. */
    static final String DIRECTION_NEUTRAL_OUT = completion("تحويل") + "|\\btransferred\\b|\\btransfer of\\b|\\boutward\\b";
    /**
     * The user's own account's role decides direction before any verb: "إلى حسابك/بطاقتك/محفظتك" (and "شحن محفظتك") is
     * incoming, "من حسابك/بطاقتك/محفظتك" is outgoing. Both together are a move between the user's own accounts.
     */
    static final String ROLE_IN = "(?<!\\p{L})(?:الي|ل)\\s*(?:حساب|بطاقت|محفظت)(?:ك|كم)(?!\\p{L})|(?<!\\p{L})الي\\s+(?:حساب|بطاقه|محفظه)(?:\\s+ال\\p{L}+)?\\s+الخاص(?:ه)? بك(?:م)?|" +
            "(?<!\\p{L})شحن\\s+(?:ال)?محفظ\\p{L}*|\\b(?:to|into) your (?:\\w+ )?(?:account|card|wallet)\\b";
    static final String ROLE_OUT = "(?<!\\p{L})من\\s*(?:حساب|بطاقت|محفظت)(?:ك|كم)(?!\\p{L})|(?<!\\p{L})من\\s+(?:حساب|بطاقه|محفظه)(?:\\s+ال\\p{L}+)?\\s+الخاص(?:ه)? بك(?:م)?|" +
            "\\bfrom your (?:\\w+ )?(?:account|card|wallet)\\b";
    /** Self-describing movement kinds: no merchant is expected for these. */
    static final String KIND_FEE = "(?<!\\p{L})(?:رسوم|مصاريف|عموله)(?!\\p{L})|غرامه|ضريبه|\\bpenalty\\b|\\bfees?\\b|\\bcharges?\\b(?! for)";
    static final String KIND_CHEQUE = "(?<!\\p{L})شيك|\\bcheque\\b|\\bcheck (?:no|number)\\b";
    static final String KIND_INTEREST = "\\binterest\\b|(?<!\\p{L})(?:فوائد|فائده|عائد)(?!\\p{L})";
    static final String KIND_INSTALLMENT = "قسط|اقساط|\\binstall?ments?\\b";
    static final String KIND_INVESTMENT = "شهاده (?:ال)?ادخار|شهادات|\\bcertificates? of deposit\\b|(?<!\\p{L})وديعه";
    /**
     * Movement kind (corpus vocabulary), decided after direction, most specific first. A kind with an _in/_out pair
     * takes the extracted direction; no matching wording leaves the kind empty.
     */
    static final String[] MOVEMENT_KINDS = {"refund", "reversal", "expiring_cashback", "wallet_topup", "possible_internal_transfer",
            "wallet_payment", "wallet_transfer_in", "wallet_transfer_out", "service_topup", "service_charge", "bill_payment", "installment_payment", "cash_withdrawal", "salary",
            "inward_remittance", "instant_transfer_in", "instant_transfer_out", "transfer_in", "transfer_out", "card_purchase"};
    static final String MOVEMENT_REFUND = "\\brefund(?:ed)?\\b|استرداد|مسترد|(?<!\\p{L})تم رد";
    static final String MOVEMENT_CASHBACK = "كاش ?باك|\\bcash ?back\\b";
    static final String MOVEMENT_WALLET = "محفظ|\\bwallet\\b|فودافون كاش|\\bvodafone cash\\b|اورانج كاش|اتصالات كاش|\\b(?:orange|etisalat|we) cash\\b";
    static final String MOVEMENT_WALLET_TOPUP = "(?<!\\p{L})شحن\\s+(?:ال)?محفظ|\\btop.?up\\b";
    /**
     * A line or service top-up ("شحن رصيدك على الرقم", "شحن الإنترنت الأرضي", "recharge"). When the confirmation names no
     * paying account, card, wallet or channel it comes from the service provider: service_topup, PAYER_UNKNOWN (the same
     * payment may already be recorded from the bank or wallet message).
     */
    static final String MOVEMENT_SERVICE_TOPUP = "(?<!\\p{L})شحن (?:ال)?(?:رصيد|انترنت|باق)|\\brecharge\\b|\\btop.?up\\b";
    static final String PAYER_NAMED = "(?<!\\p{L})(?:من|عن طريق|بواسطه|باستخدام|عبر)\\s+(?:ال)?(?:حساب|بطاق|محفظ|كارت|بنك)|" +
            "\\b(?:from|via|using|with) (?:your )?(?:account|card|wallet|bank)\\b|(?<!\\p{L})فوري(?!\\p{L})|\\bfawry\\b|محفظ|\\bwallet\\b|انستا ?باي|instapay";
    static final String MOVEMENT_BILL = "فاتوره|\\bbill\\b|شحن رصيد|\\brecharge\\b";
    static final String MOVEMENT_CASH = "\\batm\\d*\\b|صراف|سحب نقدي|\\bcash withdrawal\\b|(?<!\\p{L})ماكينه|(?<!\\p{L})اله (?:رقم|صراف)";
    static final String MOVEMENT_SALARY = "\\bsalary\\b|\\bpayroll\\b|(?<!\\p{L})(?:راتب|مرتب)";
    static final String MOVEMENT_REMITTANCE = "\\btt payment\\b|\\bremittance\\b|\\bswift\\b|حواله (?:خارجيه|دوليه|وارده من الخارج)";
    static final String MOVEMENT_INSTANT = "\\bipn\\b|instapay|انستا ?باي|(?:ال)?تحويل (?:ال)?لحظي|شبكه (?:ال)?مدفوعات (?:ال)?لحظي[هة]?|instant payment network";
    static final String MOVEMENT_TRANSFER = "تحويل|حواله|\\btransfer(?:red)?\\b";
    /** Expiring credit wording, with or without a date ("صالحة لمدة أسبوعين"). */
    static final String EXPIRY_PHRASE = "صالح(?:ه)? (?:لمده|حتي|لل?استخدام)|\\bvalid (?:for|until|till)\\b|\\bexpires?\\b|(?<!\\p{L})تنتهي";
    /** Purchase wording where a merchant is expected; transfers, cash, installments, bills and refunds are excluded. */
    static final String PURCHASE = "(?<!\\p{L})تم\\s+(?:الان\\s+)?(?:ال)?(?:دفع|شراء|خصم)|\\b(?:paid|purchased|charged)\\b|\\bwas used (?:for|at|in)\\b";
    static final String NOT_A_PURCHASE = "تحويل|transfer|سحب|withdraw|\\batm\\b|اله رقم|صراف|قسط|installment|فاتوره|\\bbill\\b|refund|reversal|عكس|اعاده|محفظه|wallet|instapay|ipn";
    static final String QUOTED_MERCHANT = "[\"“«]([^\"”»\\n]{1,60})[\"”»]";
    static final String MERCHANT_MARKER_STRONG = "\\bat\\b|(?<=deducted |debited |spent |paid |charged |used )in\\b|@(?=\\s)|(?<=[\\s\\d])@(?=\\p{L})|(?<!\\p{IsArabic})(?:لدي|عند)|(?<=\\d)\\s+علي(?=\\s)|(?<!\\p{L})(?:في|فى)(?!\\p{L})";
    /** A glued لـ after a payment noun names the payee ("قيمة مصروفات مدرسية لمدارس النيل"). */
    static final String MERCHANT_GLUED_LAM = "(?<!\\p{L})(?:قيمه|مصروفات|ثمن|مستحقات)(?:\\s+\\p{L}+){0,2}\\s+ل(?=[^\\sل]\\p{L})";
    static final String MERCHANT_MARKER_PARTY = "\\bfrom\\b|(?<!thanks )(?<!thank you )(?<!thanks a lot )\\bto\\b|(?<!\\p{L})(?:من|الي)(?!\\p{L})|(?<!\\p{L})ل(?= )|(?<!\\p{L})لصالح|(?<!\\p{L})المستفيد:?|\\bbeneficiary:?";
    /** Bill, recharge and subscription names: the billed service is the merchant. Adjectives after اشتراك are not names. */
    static final String MERCHANT_BILL = "فاتوره (?:(?:ال)?(?:انترنت|موبايل|محمول|تليفون|هاتف|ارضي) (?!بمبلغ|بقيمه|مبلغ|قيمه)(\\p{L}+)|(\\p{L}+))|" +
            "\\bfor (\\p{L}+) (?:mobile |internet |landline |electricity )?bill\\b|" +
            "شحن رصيد (?:(?:موبايل|محمول|هاتف)\\s+(?!لل?رقم|رقم|بقيمه|بمبلغ)(\\p{L}+)|(?!موبايل|محمول|هاتف)(\\p{L}+))|" +
            "اشتراك (?!ال|شهري|سنوي|اسبوعي)(\\p{L}+)|\\bsubscription (?:to|for) (\\p{L}+)|" +
            "(?:تعديل|تسويه) عمليه (\\p{L}[\\p{L} ]{1,40}?)\\s+(?:رقم|#)";
    /**
     * Where a merchant run ends; includes "و" joined to a following action ("واعادة المبلغ", "وتم"). A number ends it,
     * except a 1-3 digit store number right before the date word ("ZAHER-AIN SHAMS 1 يوم 20/10"), which is part of the name.
     */
    static final String MERCHANT_STOP = "\\s+(?:on|and|with|for|using|via|ref|card|has|have|was|were|is|been|completed|تم|تمت|يوم|بتاريخ|في|فى|من|عبر|عن|رقم|بطاقه|باستخدام|المتاح|الرصيد|الساعه|" +
            "ببطاق\\p{L}*|بطاقتك|بطاقتكم|بمبلغ|بقيمه|مقابل|ثم|المنتهي\\p{L}*|الي|رسوم)(?!\\p{L})|" +
            "\\s+و(?=(?:تم|تمت|اعاده|استرداد|رد)(?!\\p{L}))|[.,;،؛:!?()\\n]|\\s+\\d(?!\\d{0,2}\\s+(?:يوم|بتاريخ|on)(?!\\p{L}))|يوم(?=\\d)";
    static final String MERCHANT_GENERIC = "^(?:خلال|عبر|(?:know|learn|find out|read|see) more|unsubscribe|opt.?out|(?:more|further) (?:details|info(?:rmation)?)|(?:ال)?حساب|حسابك|حسابكم|(?:ال)?بطاقه|بطاقتك|بطاقتكم|(?:ال)?كارت|(?:ال)?محفظه|محفظتك|رقم|ماكينه|الفرع|فرع|(?:ال)?معلومات|account|card|wallet|your|the|you|branch)(?!\\p{L})";
    static final int MERCHANT_MAX_CHARS = 40;
    /** A bare ATM/cash-machine phrase is a place, not a merchant ("another bank's ATM"); a named terminal ("NBE ATM546") stays. */
    static final String MERCHANT_ATM_PHRASE = "(?<![\\p{L}\\d])(?:atm|ماكينه|الصراف)(?![\\p{L}\\d])";
    /** Possibly between the user's own accounts: card/prepaid funding, wallet top-up, paying one's own card dues. */
    static final String INTERNAL_TRANSFER = "(?:الي|to) (?:كارت|بطاقه|credit card|prepaid)|تم\\s+شحن\\s+(?:ال)?محفظ|top.?up (?:your )?wallet|" +
            "سداد (?:مستحقات |مديونيه )?(?:ال)?(?:بطاقه|بطاقتك|بطاقتكم|كارت)|\\bcard (?:bill |balance )?(?:payment|repayment) (?:received|made|of)\\b|" +
            "\\bbetween your (?:own )?accounts\\b|تسويه (?:ال)?مديونيه|\\b(?:deposit(?:ed)?|payment|paying|paid|repayment)s?\\b(?: of)?.{0,50}?\\b(?:to|into|towards?) (?:your )?(?:credit )?card\\b|" + KIND_INVESTMENT;
    /** Self-account markers count as an internal transfer only together with a transfer verb ("savings" alone does not). */
    static final String OWN_ACCOUNT_MARKER = "(?:حساب|محفظه|بطاقه)\\p{L}*(?: ال\\p{L}+)? الخاص(?:ه)? بك(?:م)?|(?:حساب|حسابك|حسابكم) (?:ال)?(?:توفير|اخر)|حسابك(?:م)? الاخر|" +
            "\\byour (?:own |other |savings )account\\b|\\bsavings account\\b|حسابك(?:م)? ببنك اخر|\\byour account (?:at|with) another bank\\b";
    static final String TRANSFER_VERB = "تحويل|حواله|\\btransfer(?:red)?\\b|(?<!\\p{L})حول";
    /** A payment request paid ("تم دفع طلب كريم سمير") names its counterparty. */
    static final String COUNTERPARTY_REQUEST = "(?:دفع|سداد|قبول) طلب (?:الدفع )?(?:من )?(\\p{L}[\\p{L} ]{1,40}?)(?=\\s+(?:بنجاح|بقيمه|بمبلغ)|[.,،]|$)";
    /** An account holder's name after "to account" ("إلى حساب نورهان محمود المنتهي بـ 7731"); not the user's own account. */
    static final String COUNTERPARTY_ACCOUNT_HOLDER = "(?:(?<!\\p{L})الي|\\bto) (?:حساب|account) (?!رقم|ال|number)(\\p{L}[\\p{L} ]{1,40}?)\\s+(?:المنتهي|رقم|ending|no\\b)";
    /** A phone-number counterparty after a direction word ("sent to 01012345678", "من رقم 010XXXXX123"). */
    static final String COUNTERPARTY_PHONE = "(?:(?:من|الي|\\bto|\\bfrom)\\s+(?:حساب\\s+)?(?:رقم\\s+|number\\s+|mobile\\s+)?|(?<!\\p{L})لل?رقم\\s+)(\\+?[0-9][0-9x*]{9,14})(?![0-9])";
    /** A beneficiary name set off by dashes after an account mask ("حساب 1234XXXX5678 - محمد سعيد -"). */
    static final String COUNTERPARTY_NAME = "(?:حساب|account)\\s+[0-9x*#]{4,}\\s*[-–]\\s*(\\p{L}[\\p{L} .]{0,40}?)\\s*(?:[-–]|$)";
    /** Money returned to the user is an expense offset, never income (owner rule for الاسترداد and reversals). */
    static final String EXPENSE_OFFSET = "refund|reversal|reversed|credited back|عكس|اعاده المبلغ|(?:اعاده|اعيد)\s+(?:ال)?قيد|استرداد|(?<!\\p{L})تم رد";
    static final String EXPIRY = "صالح(?:ه)?(?: لل?استخدام)? حتي|valid (?:for use )?(?:until|till)|expires?(?: on)?|تنتهي في";
    /** How far before a date an expiry phrase may sit. */
    static final int EXPIRY_WINDOW_CHARS = 25;

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
    static final String[] REVIEW_CAPS = {"TRANSFER_REQUESTED", "CURRENCY_INFERRED", "PAYER_UNKNOWN", "POSSIBLE_INTERNAL_TRANSFER", "EXPIRING_CREDIT", "MISSING_MERCHANT", "INFERRED_OUTGOING_DIRECTION",
            "UNASSIGNED_SECOND_AMOUNT", "COMMITMENT_EVENT", "RETAINED_FEE", "ADJUSTED_EVENT"};

    // Local learning (stage 5): on-device only, from the user's own actions; never uploaded.
    static final String LEARNING_VERSION = "learning-1";
    /** Dismissals of the same source + message shape before that shape is ignored. */
    static final int LEARN_DISMISS_MIN = 2;
    /** Confirmations of the same source + shape + direction before a learnable REVIEW cap is lifted. */
    static final int LEARN_CONFIRM_MIN = 3;
    /** Shapes remembered; the least recently updated is forgotten first. */
    static final int LEARN_MAX_SHAPES = 500;
    /** Review caps the user's own confirmation can resolve (ownership of the other account, the direction). */
    /**
     * Owner decision (21 Oct 2026): kept as information only, never the ledger or review; the observation and its reason
     * stay in the log of unrecorded messages. A subscription paid from prepaid line credit (PREPAID_CREDIT) is one.
     */
    static final String[] INFO_ONLY_REASONS = {"INFO_ONLY"};
    static final String[] LEARNABLE_CAPS = {"POSSIBLE_INTERNAL_TRANSFER", "INFERRED_OUTGOING_DIRECTION", "CURRENCY_INFERRED"};

    // Later stages: defined by the approved corpus, not yet used by any implemented stage.
    /** Identity-less identical messages within this window become possible-duplicate review items. */
    static final long DUPLICATE_CANDIDATE_WINDOW_MS = 60_000L;
    /** Same-source opposite Phone Banking legs within this window are possible internal transfers. */
    static final long INTERNAL_PAIRING_WINDOW_MS = 600_000L;
    /** A reversal offsets an original purchase with the same amount and merchant within this many days. */
    static final int EXPENSE_OFFSET_MATCH_DAYS = 14;
    /** A same-amount return from the same source within this window after a charge offsets that charge (ShadowOffset). */
    static final long FEE_REFUND_WINDOW_MS = 30 * 60_000L;

    private ParsingPolicy() {}
}
