package com.mohamedgamal.masareef;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser: ambiguous messages are skipped for manual entry. */
public final class ExpenseParser {
    private static final Pattern EXPENSE = Pattern.compile(
            "(?i)(purchase|purchased|paid|payment|debit(?:ed)?|withdraw(?:al|n)?|withdrawn|" +
            "spent|charged?|charges|cash\\s+withdrawal|sent|transfer(?:red)?|" +
            "(?<![\\p{L}])تم\\s+(?:(?:الآن|الان)\\s+)?(?:خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|استخدام|(?:تنفيذ|اجراء|إجراء)\\s+(?:عملية|(?:ال)?تحويل))|" +
            "عملية\\s+(?:شراء|خصم|سحب|دفع|تحويل)|مشتريات|مدفوعات|خصم|سحب|دفع|شراء|حولت)");
    private static final Pattern CREDIT_ONLY = Pattern.compile(
            "(?i)(credited|deposit(?:ed)?|received|refund(?:ed)?|cashback|" +
            "تم\\s*(?:ايداع|إيداع|استلام|اضافة|إضافة|رد)|وارد|استرداد)");
    private static final Pattern NOT_COMPLETED = Pattern.compile(
            "(?i)(declined|failed|unsuccessful|reversed|تم\\s*رفض(?:ها)?|رفضت|cancel(?:led|ed|lation)?|تم\\s*(?:الغاء|إلغاء)|" +
                    "لم\\s*(?:يتم|تتم)|غير\\s*ناجح|مرفوض|فشل|تعذر)");
    private static final Pattern FUTURE_PAYMENT = Pattern.compile(
            "(?i)(\\bwill\\s+(?:be\\s+)?(?:processed|paid|debited|charged|deducted|transferred)|" +
            "\\b(?:scheduled|pending)\\s+(?:payment|transfer|transaction)|\\b(?:payment|transfer)\\s+(?:is\\s+)?pending|" +
            "\\b(?:payment|transfer|transaction)\\b[^،;\\n!?]{0,100}?\\b(?:is\\s+)?pending\\b|" +
            "(?<![\\p{L}])و?(?:سيتم|هيتم)\\s+(?:تنفيذ|خصم|سحب|دفع|سداد|تحويل)|" +
            "(?<![\\p{L}])و?هيتخصم|سوف\\s+(?:يتم\\s+)?(?:خصم|سحب|دفع|سداد|تحويل))");
    private static final Pattern OTP = Pattern.compile(
            "(?i)(otp|one.time.password|verification code|كود التحقق|رمز التحقق|الرقم السري)");
    private static final Pattern ACCOUNT_CONTEXT = Pattern.compile(
            "(?i)(بطاق|كارت|حساب|رصيد|محفظ|ماكين|صراف|bank|card|account|atm|pos|balance)");
    private static final Pattern AMOUNT_AFTER_CURRENCY = Pattern.compile(
            "(?i)(?<![A-Za-z])(EGP|LE|E£|ج\\.?م\\.?|جنيه\\s+استرليني|جنيه|USD|EUR|GBP|دولار|يورو)\\s*([0-9][0-9., ]*)");
    private static final Pattern AMOUNT_BEFORE_CURRENCY = Pattern.compile(
            "(?i)([0-9][0-9., ]*)\\s*(EGP|LE|E£|ج\\.?م\\.?|جنيه\\s+استرليني|جنيه|USD|EUR|GBP|دولار|يورو)(?![A-Za-z])");
    private static final Pattern MERCHANT = Pattern.compile(
            "(?i)(?:\\b(?:at|from|to)\\b|لدى|عند|على|لصالح|الى|إلى|من)\\s+([^.,;\\n]{2,45})");
    private static final Pattern DATE_MERCHANT = Pattern.compile(
            "(?i)\\b[0-3]?\\d(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\\d{2,4}\\s+([A-Z][A-Z0-9*& .-]{2,40}?)\\s+(?:purchase|payment|paid)\\b");
    private static final Pattern CARD_TAIL = Pattern.compile("(?i)(?:card|visa|بطاقة|فيزا).*?(?:\\*{2,}|x{2,})\\d{4}");
    private static final Pattern CARD_LAST_FOUR = Pattern.compile(
            "(?i)(?:card|visa|بطاقة|فيزا)[^\\n]{0,35}?(?:\\*{2,}|x{2,}|ending\\s+)(\\d{4})");
    private static final Pattern ACCOUNT_LAST_FOUR = Pattern.compile(
            "(?i)(?:account|acct|a/c|حساب)[^\\n]{0,35}?(?:\\*{2,}|x{2,}|ending\\s+|رقم\\s+)(\\d{3,4})(?!\\d)");
    static String debitAccountLast4(String body){
        if(body==null)return null;String normalized=normalizeDigits(body);
        Matcher account=ACCOUNT_LAST_FOUR.matcher(normalized);if(account.find())return account.group(1);
        Matcher masked=MASKED_ACCOUNT_TAIL.matcher(normalized);return masked.find()?masked.group(1):null;
    }
    private static final Pattern MASKED_ACCOUNT_TAIL = Pattern.compile(
            "(?i)(?:from|من)\\s+\\d{2,4}-\\d{2,4}(?:\\*{2,}|x{2,})-(\\d{3,4})(?!\\d)");
    private static final Pattern IPN_RECEIVING_ACCOUNT = Pattern.compile(
            "(?i)\\bIPN\\s+transfer\\s+received\\b[^\\n]{0,100}?\\bon\\s+(\\d{4})\\s+on\\s+\\d{1,2}[/.-]\\d{1,2}");
    private static final Pattern CARD_INSTANT_TRANSFER = Pattern.compile(
            "تم\\s+تنفيذ\\s+تحويل\\s+لحظي\\s+من\\s+(?:بطاقة|حساب)\\s+رقم\\s+\\S+\\s+بمبلغ\\s+([0-9][0-9.,]*)\\s+رقم\\s+مرجعي");
    private static final Pattern MOBILE_CREDIT_PURCHASE = Pattern.compile(
            "(?i)successfully\\s+purchased\\s+(.{2,120}?)\\s+from\\s+Google\\s+Play\\s+Store\\s+and\\s+([0-9][0-9.,]*)\\s+Egyptian\\s+pounds?\\s+has\\s+been\\s+deducted\\s+from\\s+your\\s+credit");
    private static final Pattern ARABIC_MOBILE_PURCHASE = Pattern.compile(
            "(?i)تم\\s+شراء\\s+(.{2,150}?)\\s+بنجاح\\s+من\\s+خلال\\s+Google\\s+Play\\s+Store\\s+بقيمة\\s+([0-9][0-9.,]*)\\s+جنيه\\s+من\\s+رصيدكم");
    private static final Pattern SENSITIVE_TAIL = Pattern.compile("(?i)(?:\\bref(?:erence)?\\b|رقم\\s*(?:المرجع|مرجعي)|رصيد|balance|available|المتاح).*$");
    private static final Pattern EVENT_DATE = Pattern.compile("(?<!\\d)(?:(20\\d{2})[-/](0?[1-9]|1[0-2])[-/](0?[1-9]|[12]\\d|3[01])|([0-2]?\\d|3[01])[-/](0?[1-9]|1[0-2])[-/](20\\d{2}))(?:[ ,T]+(?:(?:الساعه|الساعة|at)\\s*)?([01]?\\d|2[0-3]):([0-5]\\d))?(?!\\d)");
    /** Marketing wording: offers, prizes and "up to" amounts describe a possibility, never a payment. */
    private static final Pattern PROMOTION = Pattern.compile(
            "(?i)(عروض|عرض\\s+خاص|العرض\\s+سار|استمتع|فرصت|فرصة|للفوز|الفوز|اربح|إربح|جائز|جوائز|كاش\\s*باك|" +
            "يصل\\s+(?:إلى|الى|الي|ل)|حتى\\s+[0-9]+\\s*%|الشروط\\s+و\\s*الأحكام|الشروط\\s+والاحكام|تطبق\\s+الشروط|" +
            "\\boffers?\\b|\\bpromo(?:tion)?\\b|\\bwin\\b|\\bchance\\b|\\bup\\s+to\\b|\\bcash\\s*back\\b|terms\\s+(?:and|&)\\s+conditions|\\bT&Cs?\\b)");
    /** Wording a bank uses only once money actually moved. */
    private static final Pattern COMPLETED_EVENT = Pattern.compile(
            "(?i)((?<![\\p{L}])تم\\s+(?:(?:الآن|الان)\\s+)?(?:خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|تنفيذ|إجراء|اجراء|استخدام|ايداع|إيداع|اضافة|إضافة|استلام|إستلام|رد|إرسال|ارسال|شحن)|" +
            "عملية\\s+(?:شراء|خصم|سحب|دفع|تحويل)\\s+(?:بمبلغ|بقيمة|من|على|ب)|(?:transfer|payment)\\s+(?:was\\s+|has\\s+been\\s+)?received|\\bsuccessful(?:ly)?\\b|" +
            "استلمت|إستلمت|أرسلت|ارسلت|\\bdebited\\b|\\bcredited\\b|has\\s+been\\s+(?:deducted|debited|credited|charged)|" +
            "\\bpurchase\\s+(?:at|from|of)\\b|transaction\\s+of|\\bwas\\s+charged\\b|successfully\\s+purchased|" +
            "\\bwithdrawn\\b|\\b(?:was|been)\\s+used\\b|you(?:\\s+have)?\\s+(?:paid|sent|received|transferred))");

    /** A promotional SMS that only mentions an amount (e.g. "cash back up to 50,000 EGP") is not a transaction. */
    static boolean isPromotion(String body) {
        if (body == null) return false;
        String text = normalizeDigits(body).replaceAll("[\\u064B-\\u0652\\u0640]", "");
        return PROMOTION.matcher(text).find() && !COMPLETED_EVENT.matcher(text).find();
    }

    private static final Pattern EVENT_DATE_TEXT = Pattern.compile(
            "(?i)(?<![0-9A-Z])([0-3]?\\d)(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(20\\d{2}|\\d{2})(?!\\d)");

    private ExpenseParser() {}

    /** Execution verbs, not a free-standing success word or "تم" embedded in "سيتم". */
    private static final Pattern EXECUTED_PAYMENT = Pattern.compile(
            "(?i)((?<![\\p{L}])تم\\s+(?:(?:الآن|الان)\\s+)?(?:خصم|سحب|دفع|سداد|تسديد|شراء|تحويل|استخدام|" +
            "ايداع|إيداع|اضافة|إضافة|استلام|إستلام|رد|إرسال|ارسال|(?:تنفيذ|اجراء|إجراء)\\s+(?:عملية\\s+)?(?:ال)?تحويل)|" +
            "استلمت|إستلمت|أرسلت|ارسلت|\\b(?:debited|credited|withdrawn)\\b|" +
            "\\b(?:was|has\\s+been)\\s+(?:charged|deducted|paid|transferred)|" +
            "\\byou(?:\\s+have)?\\s+(?:paid|sent|received|transferred)|\\bpurchase\\s+(?:at|from|of)\\b|" +
            "successfully\\s+(?:paid|purchased|transferred))");

    /** Future wording is a capture filter, never evidence that an earlier payment failed. */
    public static boolean isFutureOnly(String body) {
        if (body == null) return false;
        String text = normalizeDigits(body);
        return FUTURE_PAYMENT.matcher(text).find() && completedPaymentText(text) == null;
    }

    /** In mixed alerts, parse only the completed clause and its nearby amount, not a later scheduled fee. */
    private static String paymentText(String text) {
        return FUTURE_PAYMENT.matcher(text).find() ? completedPaymentText(text) : text;
    }

    private static String completedPaymentText(String text) {
        String[] clauses = text.split("(?i)(?:[،;!?\\n]|(?<![0-9])[.,]|[.,](?![0-9])|\\b(?:and|but)\\b|" +
                "(?<![\\p{L}])و(?=(?:سيتم|هيتم|هيتخصم|سوف|تم\\s)))");
        for (String clause : clauses) {
            Matcher future = FUTURE_PAYMENT.matcher(clause);
            String completed = future.find() ? clause.substring(0, future.start()) : clause;
            completed = completed.replaceFirst("(?i)\\s+(?:installments?|رسوم|قسط)\\s*$", "");
            String transaction = SENSITIVE_TAIL.matcher(completed).replaceFirst("");
            Matcher event = EXECUTED_PAYMENT.matcher(transaction);
            while (event.find()) {
                int start = Math.max(0, event.start() - 100), end = Math.min(transaction.length(), event.end() + 100);
                String nearby = transaction.substring(start, end);
                if (AMOUNT_AFTER_CURRENCY.matcher(nearby).find() || AMOUNT_BEFORE_CURRENCY.matcher(nearby).find() ||
                        Pattern.compile("بمبلغ\\s+[0-9]").matcher(nearby).find()) return completed.trim();
            }
        }
        return null;
    }

    public static Parsed parse(String sender, String body) {
        if (body == null || body.trim().isEmpty()) return null;
        String normalized = normalizeDigits(body);
        if (normalized.matches("(?is).*(minimum\\s+payment|balance\\s+statement|المبلغ\\s+الممول|قسطك\\s+بقيمة).*") ||
                normalized.matches("(?is).*(بنفكرك|نذكرك|يرجى\\s*(?:سداد|دفع)|برجاء\\s*(?:سداد|دفع)|payment\\s+due|minimum\\s+due|amount\\s+due|due\\s+(?:before|on)|to\\s+be\\s+paid).*") &&
                        !normalized.matches("(?is).*(تم\\s*(?:سداد|خصم|دفع)|paid\\s+successfully).*")) return null;
        if (OTP.matcher(normalized).find() || isUnsuccessful(normalized) || isPromotion(normalized)) return null;
        normalized = paymentText(normalized);
        if (normalized == null) return null;
        Matcher cardTransfer = CARD_INSTANT_TRANSFER.matcher(normalized);
        if (cardTransfer.find()) {
            long amount = parseMinor(cardTransfer.group(1));
            if (amount > 0) return new Parsed(amount, "EGP", INSTANT_TRANSFER, "تحويلات", last4(normalized));
        }
        Matcher mobilePurchase = MOBILE_CREDIT_PURCHASE.matcher(normalized);
        boolean mobileMatched=mobilePurchase.find();
        if(!mobileMatched) {mobilePurchase=ARABIC_MOBILE_PURCHASE.matcher(normalized);mobileMatched=mobilePurchase.find();}
        if (mobileMatched) {
            long amount = parseMinor(mobilePurchase.group(2));
            if (amount > 0) {
                String product = mobilePurchase.group(1).trim();
                String upper = product.toUpperCase(Locale.ROOT);
                String merchant = upper.contains("TRUECALLER") ? "Truecaller" :
                        upper.contains("ONEDRIVE") ? "OneDrive" :
                        upper.contains("GOOGLE ONE") ? "Google One" : merchantName(product);
                boolean subscription = upper.matches("(?s).*(SUBSCRIPTION|PREMIUM|MONTHLY|GOOGLE ONE|ONEDRIVE).*");
                return new Parsed(amount, "EGP", merchant, subscription ? "اشتراكات" : "تسوق", null);
            }
        }
        Matcher event = EXPENSE.matcher(normalized);
        if (!event.find()) return null;
        if (event.group().matches("خصم|سحب|دفع|شراء") &&
                !ACCOUNT_CONTEXT.matcher(normalized).find()) return null;
        // Credit alerts often contain a prior balance or a fee. Skip unless explicitly a debit too.
        if (CREDIT_ONLY.matcher(normalized).find() &&
                !Pattern.compile("(?i)(debit|خصم|purchase|شراء|paid|دفع)").matcher(normalized).find()) return null;

        // A stated balance is not the payment amount, even when it has the first currency-before-number form.
        String transaction = SENSITIVE_TAIL.matcher(normalized).replaceFirst("");

        String currency = null;
        long amount = -1;
        int bestRank = Integer.MAX_VALUE;
        int bestPosition = Integer.MAX_VALUE;
        Matcher after = AMOUNT_AFTER_CURRENCY.matcher(transaction);
        Matcher before = AMOUNT_BEFORE_CURRENCY.matcher(transaction);
        while (after.find()) {
            long candidate = parseMinor(after.group(2));
            int rank = after.start() >= event.end() ? 0 : 1;
            if (candidate > 0 && (rank < bestRank || rank == bestRank && after.start() < bestPosition)) {
                amount = candidate; currency = currency(after.group(1));
                bestRank = rank; bestPosition = after.start();
            }
        }
        while (before.find()) {
            if (before.start() > 0 && "-*xX".indexOf(transaction.charAt(before.start() - 1)) >= 0)
                continue;
            long candidate = parseMinor(before.group(1));
            int rank = before.start() >= event.end() ? 0 : 1;
            if (candidate > 0 && (rank < bestRank || rank == bestRank && before.start() < bestPosition)) {
                amount = candidate; currency = currency(before.group(2));
                bestRank = rank; bestPosition = before.start();
            }
        }
        if (amount <= 0) return null;

        String merchant = merchant(transaction, sender);
        if (Pattern.compile("(?i)\\bAAIBN\\d+\\b").matcher(transaction).find())
            merchant = "سحب نقدي";
        String category = category(merchant);
        if ("سحب نقدي".equals(merchant)) category = "سحب نقدي";
        else if (normalized.matches("(?is).*(installment|قسط|اقساط|أقساط).*") && !category.equals("اشتراكات"))
            category = "أقساط";
        else if (normalized.matches("(?is).*(transfer|تحويل|حولت).*") &&
                !java.util.Arrays.asList("أقساط","فواتير","اشتراكات").contains(category))
            category = "تحويلات";
        return new Parsed(amount, currency, merchant, category, last4(normalized));
    }

    /** A credit is kept separate from expenses so it never inflates spending totals. */
    public static Parsed parseIncome(String sender, String body) {
        if (body == null) return null;
        String normalized = normalizeDigits(body);
        normalized = paymentText(normalized);
        if (normalized == null) return null;
        // "عن طريق التحويل اللحظي" names a channel; it does not make an incoming credit a debit.
        if (OTP.matcher(normalized).find() || !CREDIT_ONLY.matcher(normalized).find() ||
                isUnsuccessful(normalized) || isPromotion(normalized) ||
                Pattern.compile("(?i)(\\bdebit(?:ed)?\\b|خصم|\\bpurchase\\b|شراء|\\bpaid\\b|دفع)").matcher(normalized).find()) return null;
        String transaction = SENSITIVE_TAIL.matcher(normalized).replaceFirst("");
        Matcher after = AMOUNT_AFTER_CURRENCY.matcher(transaction);
        Matcher before = AMOUNT_BEFORE_CURRENCY.matcher(transaction);
        String rawAmount;
        String foundCurrency;
        if (after.find()) {
            foundCurrency = currency(after.group(1)); rawAmount = after.group(2);
        } else if (before.find()) {
            foundCurrency = currency(before.group(2)); rawAmount = before.group(1);
        } else return null;
        long amount = parseMinor(rawAmount);
        if (amount <= 0) return null;
        return new Parsed(amount, foundCurrency, merchant(transaction, sender), "دخل",
                last4(normalized));
    }

    /** Reads the stated balance only; an expense cannot be used to infer an account balance. */
    public static Balance parseBalance(String body) {
        if (body == null) return null;
        String normalized = normalizeDigits(body);
        Matcher keyword = Pattern.compile("(?i)(available balance|current balance|balance|رصيدك|رصيد الحساب|الرصيد المتاح|الرصيد)")
                .matcher(normalized);
        if (!keyword.find()) return null;
        String afterKeyword = normalized.substring(keyword.end(), Math.min(normalized.length(), keyword.end() + 70));
        Matcher after = AMOUNT_AFTER_CURRENCY.matcher(afterKeyword);
        Matcher before = AMOUNT_BEFORE_CURRENCY.matcher(afterKeyword);
        String rawAmount;
        String foundCurrency;
        if (after.find()) {
            foundCurrency = currency(after.group(1)); rawAmount = after.group(2);
        } else if (before.find()) {
            foundCurrency = currency(before.group(2)); rawAmount = before.group(1);
        } else return null;
        long amount = parseMinor(rawAmount);
        if (amount < 0) return null;
        return new Balance(amount, foundCurrency, last4(normalized));
    }

    private static String last4(String body) {
        Matcher card = CARD_LAST_FOUR.matcher(body);
        if (card.find()) return card.group(1);
        Matcher account = ACCOUNT_LAST_FOUR.matcher(body);
        if (account.find()) return account.group(1);
        Matcher masked = MASKED_ACCOUNT_TAIL.matcher(body);
        if (masked.find()) return masked.group(1);
        Matcher ipnAccount = IPN_RECEIVING_ACCOUNT.matcher(body);
        return ipnAccount.find() ? ipnAccount.group(1) : null;
    }

    /** Broad review signal only. It must never create a transaction by itself. */
    public static boolean isFinancialCandidate(String body) {
        if (body == null || isVerificationMessage(body)) return false;
        String text = normalizeDigits(body);
        if (!Pattern.compile("[0-9]").matcher(text).find()) return false;
        return Pattern.compile("(?i)(transfer|received|payment|paid|purchase|debit|credit|balance|recharg|" +
                "تحويل|استلام|اضافة|إضافة|خصم|دفع|سداد|قسط|اقساط|أقساط|شراء|شحن|رصيد|اشتراك)")
                .matcher(text).find();
    }

    /** Prioritizes completed-looking messages for review without booking them as expenses. */
    public static boolean isCompletedFinancialCandidate(String body) {
        if (!isFinancialCandidate(body) || isPromotion(body) || isUnsuccessful(body) || isFutureOnly(body)) return false;
        String text = normalizeDigits(body);
        if (Pattern.compile("(?i)(نذكرك|بنفكرك|يرجى|جاهز[ة]?\\s+للدفع|طلب\\s+سداد|" +
                "للتقديم|خصومات|خصم\\s+على|العرض\\s+سار|offer\\s+valid|payment\\s+due)")
                .matcher(text).find()) return false;
        return Pattern.compile("(?i)(successfully\\s+purchased|has\\s+been\\s+deducted|" +
                "you\\s+have\\s+added\\s+balance|تم\\s+تنفيذ\\s+تحويل|تم\\s+شحن|" +
                "تم\\s+(?:خصم|دفع|سداد)|successful\\s+transaction)")
                .matcher(text).find();
    }

    /** Uses a stated transaction date/time when available; otherwise keeps the message timestamp. */
    public static long eventTime(String body, long receivedAt) {
        return eventTimeWithPrecision(body,receivedAt).when;
    }

    public static final class EventTime {
        public final long when;
        public final String precision;
        EventTime(long when,String precision){this.when=when;this.precision=precision;}
    }

    /** Precision of the chosen timestamp, not a claim that receipt time was stated by the bank. */
    public static EventTime eventTimeWithPrecision(String body,long receivedAt) {
        if (body == null) return new EventTime(receivedAt,"exact");
        Matcher match = EVENT_DATE.matcher(normalizeDigits(body));
        while (match.find()) {
            try {
                int year = Integer.parseInt(match.group(1) != null ? match.group(1) : match.group(6));
                int month = Integer.parseInt(match.group(1) != null ? match.group(2) : match.group(5));
                int day = Integer.parseInt(match.group(1) != null ? match.group(3) : match.group(4));
                LocalDate date = LocalDate.of(year, month, day);
                LocalTime time = match.group(7) == null
                        ? Instant.ofEpochMilli(receivedAt).atZone(ZoneId.systemDefault()).toLocalDate().equals(date)
                            ? Instant.ofEpochMilli(receivedAt).atZone(ZoneId.systemDefault()).toLocalTime()
                            : LocalTime.NOON
                        : LocalTime.of(Integer.parseInt(match.group(7)), Integer.parseInt(match.group(8)));
                long event = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (event <= receivedAt + 24L * 60 * 60 * 1000 &&
                        event >= receivedAt - 5L * 365 * 24 * 60 * 60 * 1000)
                    return new EventTime(event,match.group(7)==null?"date_only":"exact");
            } catch (NumberFormatException | java.time.DateTimeException ignored) { }
        }
        Matcher textDate = EVENT_DATE_TEXT.matcher(normalizeDigits(body));
        if (textDate.find()) {
            try {
                int day = Integer.parseInt(textDate.group(1));
                int month = ("JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC".indexOf(textDate.group(2).toUpperCase(Locale.ROOT)) / 4) + 1;
                int year = Integer.parseInt(textDate.group(3));
                if (year < 100) year += 2000;
                long event = LocalDate.of(year, month, day).atTime(12, 0)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (event <= receivedAt + 24L * 60 * 60 * 1000 &&
                        event >= receivedAt - 5L * 365 * 24 * 60 * 60 * 1000) return new EventTime(event,"date_only");
            } catch (NumberFormatException | java.time.DateTimeException ignored) { }
        }
        return new EventTime(receivedAt,"exact");
    }

    static String normalizeDigits(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            int digit = Character.digit(c, 10);
            out.append(digit >= 0 ? (char) ('0' + digit) : c);
        }
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFKC)
                .replace('٫', '.').replace('٬', ',');
    }

    static long parseMinor(String input) {
        String s = input.replace(" ", "").trim().replaceAll("[.,]+$", "");
        if (s.isEmpty()) return -1;
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        int last = Math.max(dot, comma);
        String decimal;
        if (last >= 0 && (s.length() - last - 1 == 1 || s.length() - last - 1 == 2)) {
            decimal = s.substring(0, last).replaceAll("[.,]", "") + "." + s.substring(last + 1);
        } else {
            decimal = s.replaceAll("[.,]", "");
        }
        try {
            return new BigDecimal(decimal).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    /**
     * True when a hand-typed amount is ambiguous in a way {@link #parseMinor(String)} would
     * silently absorb: a dot as the last separator with three or more digits after it, such as
     * {@code 1.500} or {@code 12.345}. A thousands dot that is followed by a decimal part
     * ({@code 1.500,00}) stays valid. Bank SMS parsing is deliberately left untouched.
     */
    static boolean manualAmountMisread(String raw) {
        if (raw == null) return false;
        String text = normalizeDigits(raw).replaceAll("\\s+", "").replaceAll("[.,]+$", "");
        int dot = text.lastIndexOf('.');
        if (dot < 0 || dot < text.lastIndexOf(',')) return false;
        if (text.length() - dot - 1 < 3) return false;
        for (int i = dot + 1; i < text.length(); i++) if (!Character.isDigit(text.charAt(i))) return false;
        return true;
    }

    private static String currency(String raw) {
        String value = raw.toUpperCase(Locale.ROOT);
        if (value.contains("USD") || value.contains("دولار")) return "USD";
        if (value.contains("EUR") || value.contains("يورو")) return "EUR";
        if (value.contains("GBP") || value.contains("استرليني")) return "GBP";
        return "EGP";
    }

    static final String INSTANT_TRANSFER = "تحويل لحظي";
    private static final Pattern INSTANT_TRANSFER_CONTEXT = Pattern.compile(
            "(?i)(?:ال)?تحويل\\s+(?:ال)?لحظي|\\bIPN\\b");

    public static boolean isUnsuccessful(String body) {
        if (body == null) return false;
        String normalized = normalizeDigits(body);
        return NOT_COMPLETED.matcher(normalized).find();
    }
    static boolean isVerificationMessage(String body) {
        return body!=null && OTP.matcher(normalizeDigits(body)).find();
    }

    public static boolean genericMerchant(String merchant) {
        if (merchant == null) return true;
        String name = merchant.trim().toLowerCase(Locale.ROOT);
        return name.isEmpty() || name.equals("غير معروف") || name.equals(INSTANT_TRANSFER) ||
                name.startsWith("حساب رقم") || name.equals("hsbc") || name.equals("banque misr") ||
                name.equals("bank misr") || name.equals("بنك مصر") || name.equals("bm-online") ||
                name.startsWith("المعلومات عن الحساب") || name.startsWith("لمزيد من المعلومات") ||
                name.startsWith("تفضل بزيارة الرابط");
    }

    private static String merchant(String body, String sender) {
        String senderName = sender == null ? "" : sender.trim();
        if (senderName.equalsIgnoreCase("TRU") || senderName.equals("ترو")) return "TRU";
        // Read the recipient role independently of the bank, account wording and amount position.
        // Some instant-transfer alerts name only the account; keep their channel as the fallback.
        if (INSTANT_TRANSFER_CONTEXT.matcher(body).find()) {
            Matcher recipient = Pattern.compile("(?i)(?:إلى|الى|الي|\\bto\\b)\\s+([^\\n,;]{2,100})").matcher(body);
            if (recipient.find()) {
                String name = recipient.group(1).split(
                        "(?i)\\s+(?:يوم|بتاريخ|on|from|من\\s+(?:حساب|بطاقة|account|card))(?=\\s|$)", 2)[0].trim();
                if (Pattern.compile("[\\p{L}]").matcher(name).find() &&
                        !name.matches("(?i)^(?:حساب|بطاقة|كارت|account|card)(?:\\s.*|$)")) return merchantName(name);
            }
            return INSTANT_TRANSFER;
        }
        Matcher arabic = Pattern.compile("(?:تم\\s*خصم\\s*مبلغ).*?(?:EGP|USD|EUR|GBP|جنيه|ج\\.?م\\.?)\\s*[0-9., ]+\\s*(?:في|فى)\\s*(.+?)\\s*(?:بتاريخ|يوم)", Pattern.CASE_INSENSITIVE).matcher(body);
        if (arabic.find()) return merchantName(arabic.group(1));
        Matcher english = Pattern.compile("(?i)\\bdebited\\s+by\\s+(?:EGP|USD|EUR|LE)\\s*[0-9., ]+\\s+on\\s+(.+?)\\s+on\\s+\\d").matcher(body);
        if (english.find()) return merchantName(english.group(1));
        Matcher dated = DATE_MERCHANT.matcher(body);
        if (dated.find()) {
            if (MerchantClassifier.category(dated.group(1)) != null) return merchantName(dated.group(1));
            if (MerchantClassifier.processorDescriptor(dated.group(1))) return MerchantClassifier.displayName(dated.group(1));
            String found = dated.group(1).split("\\*")[0].trim();
            if (found.length() >= 2) return found.length() > 36 ? found.substring(0, 36).trim() : found;
        }
        Matcher matcher = MERCHANT.matcher(body);
        String candidate = matcher.find() ? matcher.group(1).trim() : "";
        candidate = SENSITIVE_TAIL.matcher(candidate).replaceFirst("").trim();
        candidate = CARD_TAIL.matcher(candidate).replaceAll("").trim();
        candidate = candidate.replaceAll("(?i)(?:\\bon\\b|في|بتاريخ|يوم).*$", "").trim();
        if (MerchantClassifier.category(candidate) != null) return merchantName(candidate);
        if (MerchantClassifier.processorDescriptor(candidate)) {
            // Keep the unknown shop, rather than collapsing every processor payment to "KASHIER".
            String shop=candidate.replaceFirst("(?i)\\s+(?:EGP|LE|USD|EUR|GBP)\\s*[0-9].*$", "");
            return MerchantClassifier.displayName(shop);
        }
        candidate = candidate.replaceAll("[0-9*#].*$", "").trim();
        candidate = candidate.replaceAll("(?i)\\s*(?:EGP|LE|E£|USD|EUR|ج\\.?م\\.?|جنيه|دولار|يورو)$", "").trim();
        candidate = candidate.replaceAll("(?i)\\s*(?:بنجاح|successfully)\\.?$", "").trim();
        if (candidate.length() < 2) return sender == null || sender.isBlank() ? "غير معروف" : sender.trim();
        if (candidate.toUpperCase(Locale.ROOT).startsWith("STARBUCKS")) return "STARBUCKS";
        if (candidate.toLowerCase(Locale.ROOT).contains("vodafone")) return "Vodafone";
        String known = merchantName(candidate);
        // Only a recognized brand (Claude, ChatGPT, OneDrive...) replaces the raw descriptor; other names keep the old 36-char rule.
        if (known.length() <= 20 && !candidate.toUpperCase(Locale.ROOT).startsWith(known.toUpperCase(Locale.ROOT))) return known;
        return candidate.length() > 36 ? candidate.substring(0, 36).trim() : candidate;
    }

    /** The display name current rules give a stored merchant (e.g. "OPENAI *CHATGPT SUBSCR" → "ChatGPT"). */
    static String normalizeMerchant(String raw) { return raw == null ? "" : merchantName(raw); }

    private static String merchantName(String raw) {
        if (MerchantClassifier.category(raw) != null) return MerchantClassifier.displayName(raw);
        String name = raw.trim().replaceAll("\\s+", " ");
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("CLAUDE") || upper.contains("ANTHROPIC")) return "Claude";
        if (upper.contains("CHATGPT") || upper.contains("OPENAI")) return "ChatGPT";
        if (upper.contains("TRUECALLER")) return "Truecaller";
        if (upper.contains("ONEDRIVE")) return "OneDrive";
        if (upper.contains("GOOGLE ONE")) return "Google One";
        if (upper.contains("VODAFONE")) return "Vodafone";
        if (upper.startsWith("STARBUCKS")) return "STARBUCKS";
        return name.length() > 45 ? name.substring(0, 45).trim() : name;
    }

    static String category(String merchant) {
        String known = MerchantClassifier.category(merchant);
        if (known != null) return known;
        if (merchant == null) return "غير مصنف";
        String value = merchant.toLowerCase(Locale.ROOT);
        if (value.equals("tru") || value.equals("ترو")) return "أقساط";
        if (value.contains("vodafone") || value.contains("فودافون")) return "فواتير";
        if (value.contains("atm") || value.contains("سحب نقدي")) return "سحب نقدي";
        if (value.contains("claude") || value.contains("anthropic") || value.contains("truecaller") || value.contains("onedrive") ||
                value.contains("chatgpt") || value.contains("openai")) return "اشتراكات";
        if (value.matches(".*(uber|careem|كريم|اوبر|fuel|petrol|بنزين).*")) return "مواصلات";
        if (value.matches(".*(netflix|spotify|youtube|google|apple|شاهد|انترنت|internet).*")) return "اشتراكات";
        if (value.matches(".*(market|grocery|carrefour|سبينس|سوبر|ماركت).*")) return "بقالة";
        if (value.matches(".*(\\bcafe\\b|\\bcoffee\\b|\\bstarbucks\\b|مقاهي|كافيه).*")) return "مقاهي";
        if (value.matches(".*(restaurant|مطعم).*")) return "أكل وشرب";
        if (value.matches(".*(\\bhospital\\b|\\bclinic\\b|\\bpharmacy\\b|مستشفى|صيدلية|عيادة).*")) return "رعاية صحية";
        if (value.matches(".*(electric|water|gas|كهرباء|مياه|غاز).*")) return "فواتير";
        return "غير مصنف";
    }

    private static final Pattern IPA_IN = Pattern.compile(
            "(?i)(?:لقد\\s+)?(?:إستلمت|استلمت|تم\\s+استلام|تم\\s+إستلام|you(?:\\s+have)?\\s+received)\\s+(?:(?:EGP|LE)\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه|ج\\.?\\s?م\\.?|EGP|LE)?\\s*(?:من|from)\\s+(\\S+)");
    private static final Pattern IPA_OUT = Pattern.compile(
            "(?i)(?:لقد\\s+)?(?:أرسلت|ارسلت|تم\\s+إرسال|تم\\s+ارسال|you(?:\\s+have)?\\s+sent)\\s+(?:(?:EGP|LE)\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه|ج\\.?\\s?م\\.?|EGP|LE)?\\s*(?:إلى|الى|الي|to)\\s+(\\S+)");
    private static final Pattern SERVICE_PAID = Pattern.compile(
            "(?i)(?:تم\\s+دفع\\s+(?:خدمة|فاتورة)\\s+(.+?)\\s+بنجاح|(.+?)\\s+(?:bill\\s+)?(?:was\\s+)?paid\\s+successfully)");

    /** An InstaPay-style person-to-person transfer, read from the InstaPay app or a bank message. */
    public static final class Transfer {
        public final long amountMinor;
        public final String counterpart;
        public final boolean incoming;
        Transfer(long amountMinor, String counterpart, boolean incoming) {
            this.amountMinor = amountMinor; this.counterpart = counterpart; this.incoming = incoming;
        }
    }

    public static Transfer parseTransfer(String body) {
        if (body == null) return null;
        String text = normalizeDigits(body);
        if (OTP.matcher(text).find() || isUnsuccessful(text) || isPromotion(text)) return null;
        text = paymentText(text);
        if (text == null) return null;
        for (int pass = 0; pass < 2; pass++) {
            Matcher matcher = (pass == 0 ? IPA_IN : IPA_OUT).matcher(text);
            if (!matcher.find()) continue;
            long amount = parseMinor(matcher.group(1));
            String who = matcher.group(2).replaceAll("[.,،]+$", "").trim();
            if (amount <= 0 || who.isEmpty()) continue;
            return new Transfer(amount, who, pass == 0);
        }
        return null;
    }

    /** "تم دفع خدمة TRU بنجاح": a bill paid through InstaPay, which names the service but not the amount. */
    public static String paidService(String body) {
        if (body == null) return null;
        if (isUnsuccessful(body)) return null;
        String text = paymentText(normalizeDigits(body));
        if (text == null) return null;
        Matcher matcher = SERVICE_PAID.matcher(text);
        if (!matcher.find()) return null;
        String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        name = name == null ? "" : name.trim();
        return name.length() < 2 || name.length() > 40 ? null : name;
    }

    public static final class Parsed {
        public final long amountMinor;
        public final String currency;
        public final String merchant;
        public final String category;
        public final String cardLast4;

        Parsed(long amountMinor, String currency, String merchant, String category, String cardLast4) {
            this.amountMinor = amountMinor;
            this.currency = currency;
            this.merchant = merchant;
            this.category = category;
            this.cardLast4 = cardLast4;
        }
    }

    public static final class Balance {
        public final long amountMinor;
        public final String currency, cardLast4;

        Balance(long amountMinor, String currency, String cardLast4) {
            this.amountMinor = amountMinor;
            this.currency = currency;
            this.cardLast4 = cardLast4;
        }
    }
}
