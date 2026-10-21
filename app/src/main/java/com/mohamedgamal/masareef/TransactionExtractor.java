package com.mohamedgamal.masareef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline stage 3: reads the fields of one classified transaction (principal, currency, direction, merchant,
 * balance, fee, limit, original amount, card/account/counterparty, bank time) and derives a provisional decision from
 * what could and could not be read. Nothing ambiguous is guessed: it stays null with a reason. Shadow mode only;
 * scoring belongs to the later confidence stage.
 */
final class TransactionExtractor {
    static final class Extraction {
        String decision, currency, direction, directionCertainty, merchantSurface, merchantKey;
        Long amountMinor, balanceMinor, feeMinor, creditLimitMinor, originalMinor;
        String balanceCurrency, feeCurrency, creditLimitCurrency, originalCurrency;
        String cardLast4, accountLast4, counterparty;
        String datetime, timePrecision = "unknown", timeAmbiguity, yearOrigin, expiryDate, accountingEffect;
        final Set<String> reasons = new LinkedHashSet<>();
    }

    /** Folded text of the same length as the display text, so spans map back to the original merchant spelling. */
    static final class Folded {
        final String text, display;
        final int[] toDisplay;
        Folded(String display) {
            this.display = display;
            StringBuilder out = new StringBuilder(display.length());
            int[] map = new int[display.length() + 1];
            for (int i = 0; i < display.length(); i++) {
                char c = display.charAt(i);
                if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
                map[out.length()] = i;
                out.append(fold(c));
            }
            map[out.length()] = display.length();
            this.text = out.toString();
            this.toDisplay = map;
        }
        String display(int start, int end) { return display.substring(toDisplay[start], end == 0 ? 0 : toDisplay[end - 1] + 1); }
        private static char fold(char c) {
            switch (c) {
                case 'أ': case 'إ': case 'آ': case 'ٱ': return 'ا';
                case 'ؤ': return 'و';
                case 'ئ': case 'ى': return 'ي';
                case 'ة': return 'ه';
                default: return Character.toLowerCase(c);
            }
        }
    }

    private enum Role { PRINCIPAL, BALANCE, LIMIT, FEE, EQUIVALENT, PAREN, TOTAL, FUTURE }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern BALANCE = Pattern.compile(ParsingPolicy.ROLE_BALANCE, FLAGS);
    private static final Pattern LIMIT = Pattern.compile(ParsingPolicy.ROLE_LIMIT, FLAGS);
    private static final Pattern FEE = Pattern.compile(ParsingPolicy.ROLE_FEE, FLAGS);
    private static final Pattern EQUIVALENT_BEFORE = Pattern.compile(ParsingPolicy.ROLE_EQUIVALENT_BEFORE, FLAGS);
    private static final Pattern EQUIVALENT_AFTER = Pattern.compile(ParsingPolicy.ROLE_EQUIVALENT_AFTER, FLAGS);
    private static final Pattern EQUIVALENT_PAREN = Pattern.compile(ParsingPolicy.ROLE_EQUIVALENT_PAREN, FLAGS);
    private static final Pattern TOTAL = Pattern.compile(ParsingPolicy.ROLE_TOTAL, FLAGS);
    private static final Pattern ATM_PHRASE = Pattern.compile(ParsingPolicy.MERCHANT_ATM_PHRASE, FLAGS);
    private static final Pattern PRINCIPAL_MARKER = Pattern.compile(ParsingPolicy.PRINCIPAL_MARKER, FLAGS);
    private static final Pattern CREDIT_CARD = Pattern.compile(ParsingPolicy.CREDIT_CARD, FLAGS);
    private static final Pattern FUTURE = Pattern.compile(ParsingPolicy.FUTURE_OR_REQUEST, FLAGS);
    private static final Pattern REFUND = Pattern.compile(ParsingPolicy.REFUND_OR_REVERSAL, FLAGS);
    private static final Pattern IN = Pattern.compile(ParsingPolicy.DIRECTION_IN, FLAGS);
    private static final Pattern OUT = Pattern.compile(ParsingPolicy.DIRECTION_OUT, FLAGS);
    private static final Pattern PURCHASE = Pattern.compile(ParsingPolicy.PURCHASE, FLAGS);
    private static final Pattern NOT_A_PURCHASE = Pattern.compile(ParsingPolicy.NOT_A_PURCHASE, FLAGS);
    private static final Pattern QUOTED = Pattern.compile(ParsingPolicy.QUOTED_MERCHANT, FLAGS);
    private static final Pattern STRONG = Pattern.compile("(?:" + ParsingPolicy.MERCHANT_MARKER_STRONG + ")\\s*", FLAGS);
    private static final Pattern PARTY = Pattern.compile("(?:" + ParsingPolicy.MERCHANT_MARKER_PARTY + ")\\s*", FLAGS);
    private static final Pattern BILL = Pattern.compile(ParsingPolicy.MERCHANT_BILL, FLAGS);
    private static final Pattern STOP = Pattern.compile(ParsingPolicy.MERCHANT_STOP, FLAGS);
    private static final Pattern GENERIC = Pattern.compile(ParsingPolicy.MERCHANT_GENERIC, FLAGS);
    private static final Pattern INTERNAL = Pattern.compile(ParsingPolicy.INTERNAL_TRANSFER, FLAGS);
    private static final Pattern EXPIRY = Pattern.compile(ParsingPolicy.EXPIRY, FLAGS);
    private static final Pattern HEADER = Pattern.compile("^\\s*from\\s+[^:\\n]{1,30}:", FLAGS);
    private static final Pattern INSTAPAY = Pattern.compile("[a-z0-9._-]+@instapay", FLAGS);
    /** A whole mask token ("****4321", "1234XXXX5678"); last four digits only when the token ends in four digits. */
    private static final String MASK_TOKEN = "((?:\\d*[x*#]+)*\\d{4})(?![\\dx*#])";
    private static final Pattern CARD = Pattern.compile("(?:card|بطاقه|بطاقتك|بطاقتكم|كارت)[^.;\\n]{0,40}?(?:ending(?: with)?\\s*#?|المنتهيه(?: ب)?|رقم|#|(?=[*x]))\\s*" + MASK_TOKEN, FLAGS);
    private static final Pattern ACCOUNT = Pattern.compile("(?:account|acct|(?<!\\p{L})(?:ل|ب)?حساب(?:ك|كم|ي)?)(?:\\s+ال\\p{L}+)?\\s*(?:رقم\\s*)?(?:ending(?: with)?\\s*|المنتهي ب\\s*)?" + MASK_TOKEN, FLAGS);
    private static final Pattern TOWARD = Pattern.compile("(?:\\bto|(?<!\\p{L})الي)\\s+$", FLAGS);
    private static final Pattern IPN_ACCOUNT_IN = Pattern.compile("\\b(?:received|credited)\\b[^\\n]{0,60}? on [*]*(\\d{4})(?!\\d)", FLAGS);
    private static final Pattern IPN_ACCOUNT_OUT = Pattern.compile("\\bsent\\b[^\\n]{0,60}? from [*]*(\\d{4})(?!\\d)", FLAGS);
    private static final Pattern TRUNCATED_CLOCK = Pattern.compile(ParsingPolicy.TRUNCATED_CLOCK, FLAGS);
    private static final Pattern COUNTERPARTY_DIGITS = Pattern.compile("(?:\\bto|(?<!\\p{L})الي)\\s+(?:account\\s+|حساب رقم\\s+|حساب\\s+)?[*x#]*(\\d{4})(?!\\d)", FLAGS);
    private static final Pattern COUNTERPARTY_PHONE = Pattern.compile(ParsingPolicy.COUNTERPARTY_PHONE, FLAGS);
    private static final Pattern COUNTERPARTY_NAME = Pattern.compile(ParsingPolicy.COUNTERPARTY_NAME, FLAGS);
    private static final Pattern EXPENSE_OFFSET = Pattern.compile(ParsingPolicy.EXPENSE_OFFSET, FLAGS);
    private static final String MONTHS = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|يناير|فبراير|مارس|ابريل|مايو|يونيو|يوليو|اغسطس|سبتمبر|اكتوبر|نوفمبر|ديسمبر";
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)" +
            "|(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}|\\d{2})(?!\\d)" +
            "|(?<!\\d)(\\d{1,2})[- ]?(" + MONTHS + ")[a-z]*[- ]?(\\d{4}|\\d{2})(?!\\d)" +
            "|(?<![\\d/-])(\\d{1,2})([/-])(\\d{1,2})(?![\\d/-])", FLAGS);
    private static final Pattern TIME = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)(?:\\s*(am|pm|ص|م)(?!\\p{L}))?", FLAGS);

    static Extraction extract(MessageNormalizer.Normalized message, MessageClassifier.Result classified, long receivedAtMs) {
        Extraction x = new Extraction();
        if (classified.decision != MessageClassifier.Decision.EXTRACT) {
            x.decision = classified.decision.name();
            x.reasons.addAll(classified.reasons);
            return x;
        }
        Folded f = new Folded(message.text);
        String t = f.text;
        amounts(t, x);
        direction(t, x);
        merchant(f, x);
        identities(f, x);
        time(t, receivedAtMs, x);
        decide(t, classified, x);
        return x;
    }

    // ---- Amounts and their roles -------------------------------------------------------------------------------

    private static void amounts(String t, Extraction x) {
        List<AmountScanner.Money> money = AmountScanner.scan(t);
        boolean creditCard = CREDIT_CARD.matcher(t).find();
        AmountScanner.Money principal = null, equivalent = null;
        List<AmountScanner.Money> unassigned = new ArrayList<>();
        for (AmountScanner.Money m : money) {
            Role role = role(t, m, creditCard);
            // A parenthesized amount right after the principal is its equivalent only when the currency differs.
            if (role == Role.PAREN) role = principal != null && equivalent == null && !java.util.Objects.equals(m.currency, principal.currency) ? Role.EQUIVALENT : Role.PRINCIPAL;
            if (role == Role.BALANCE && x.balanceMinor == null && m.minor >= 0) {
                x.balanceMinor = m.leadingMinus ? -m.minor : m.minor; x.balanceCurrency = m.currency;
            } else if (role == Role.LIMIT && x.creditLimitMinor == null && m.minor >= 0) {
                x.creditLimitMinor = m.minor; x.creditLimitCurrency = m.currency;
            } else if (role == Role.FEE && x.feeMinor == null && m.minor >= 0) {
                x.feeMinor = m.minor; x.feeCurrency = m.currency;
            } else if (role == Role.EQUIVALENT && equivalent == null) equivalent = m;
            else if (role == Role.PRINCIPAL) { if (principal == null) principal = m; else unassigned.add(m); }
        }
        if (equivalent != null && principal != null && !java.util.Objects.equals(equivalent.currency, principal.currency)) {
            // Owner decision: the account-currency equivalent is what left the account; the foreign amount is shown only.
            x.originalMinor = principal.minor >= 0 ? principal.minor : null; x.originalCurrency = principal.currency;
            principal = equivalent;
        }
        if (x.feeMinor != null) x.reasons.add("EXPLICIT_FEE");
        if (principal == null) { x.reasons.add("MISSING_AMOUNT"); return; }
        // Safety rule: a second amount in the principal's currency with no role is never silently dropped.
        for (AmountScanner.Money other : unassigned)
            if (java.util.Objects.equals(other.currency, principal.currency)) { x.reasons.add("UNASSIGNED_SECOND_AMOUNT"); break; }
        x.currency = principal.currency;
        if (principal.trailingSign == '+') x.direction = "in";
        if (principal.trailingSign == '-') x.direction = "out";
        if (principal.currency == null) { x.reasons.add("AMBIGUOUS_CURRENCY"); return; }
        if (principal.leadingMinus) { x.reasons.add("INVALID_SIGNED_AMOUNT"); return; }
        if (principal.ambiguousFormat()) { x.reasons.add("AMBIGUOUS_NUMBER_FORMAT"); return; }
        x.amountMinor = principal.minor;
        if (principal.minor <= 0) x.reasons.add("NON_POSITIVE_AMOUNT");
        else x.reasons.add("AMOUNT_UNAMBIGUOUS");
    }

    private static Role role(String t, AmountScanner.Money m, boolean creditCard) {
        int clauseStart = clauseStart(t, m.start);
        String before = t.substring(Math.max(clauseStart, m.start - ParsingPolicy.ROLE_WINDOW_CHARS), m.start);
        String clause = t.substring(clauseStart, clauseEnd(t, m.end));
        if (EQUIVALENT_BEFORE.matcher(before).find() || EQUIVALENT_AFTER.matcher(t.substring(m.end)).find()) return Role.EQUIVALENT;
        if (EQUIVALENT_PAREN.matcher(before).find()) return Role.PAREN;
        // An amount in a future or request clause ("a fee of EGP 5 will apply") belongs to no completed event.
        if (FUTURE.matcher(clause).find()) return Role.FUTURE;
        if (lastMatchWithoutMarker(TOTAL.matcher(before), before)) return Role.TOTAL;
        Matcher fee = FEE.matcher(before);
        if (lastMatchWithoutMarker(fee, before)) return FUTURE.matcher(clause).find() ? Role.FUTURE : Role.FEE;
        if (lastMatchWithoutMarker(LIMIT.matcher(before), before)) return Role.LIMIT;
        if (lastMatchWithoutMarker(BALANCE.matcher(before), before)) return creditCard ? Role.LIMIT : Role.BALANCE;
        return Role.PRINCIPAL;
    }

    /** True when the role word occurs and no principal marker ("بقيمة", "of") follows it before the amount. */
    private static boolean lastMatchWithoutMarker(Matcher role, String before) {
        int last = -1;
        while (role.find()) last = role.end();
        return last >= 0 && !PRINCIPAL_MARKER.matcher(before.substring(last)).find();
    }

    private static int clauseStart(String t, int at) {
        for (int i = at - 1; i >= 0; i--) {
            char c = t.charAt(i);
            if (c == '\n' || c == ';' || c == '؛' || c == '!' || c == '?' || c == '،') return i + 1;
            if (c == '.' && i + 1 < t.length() && Character.isWhitespace(t.charAt(i + 1))) return i + 1;
        }
        return 0;
    }

    private static int clauseEnd(String t, int at) {
        for (int i = at; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == ';' || c == '؛' || c == '!' || c == '?' || c == '،') return i;
            if (c == '.' && (i + 1 == t.length() || Character.isWhitespace(t.charAt(i + 1)))) return i;
        }
        return t.length();
    }

    // ---- Direction ---------------------------------------------------------------------------------------------

    private static void direction(String t, Extraction x) {
        if (x.direction != null) { x.directionCertainty = "explicit"; return; }
        if (REFUND.matcher(t).find()) { x.direction = "in"; x.directionCertainty = "explicit"; return; }
        int in = first(IN, t), out = first(OUT, t);
        if (in >= 0 || out >= 0) {
            x.direction = out < 0 || (in >= 0 && in < out) ? "in" : "out";
            x.directionCertainty = "explicit";
        }
    }

    private static int first(Pattern p, String t) { Matcher m = p.matcher(t); return m.find() ? m.start() : -1; }

    // ---- Merchant ----------------------------------------------------------------------------------------------

    private static void merchant(Folded f, Extraction x) {
        String t = f.text;
        int searchFrom = 0;
        Matcher header = HEADER.matcher(t);
        if (header.find()) searchFrom = header.end();
        Matcher quoted = QUOTED.matcher(t);
        if (quoted.find(searchFrom)) { setMerchant(f, x, quoted.start(1), quoted.end(1)); return; }
        for (Pattern marker : new Pattern[]{STRONG, null, PARTY}) {
            if (marker == null) {
                Matcher bill = BILL.matcher(t);
                if (bill.find(searchFrom)) {
                    for (int g = 1; g <= bill.groupCount(); g++) if (bill.group(g) != null) { setMerchant(f, x, bill.start(g), bill.end(g)); return; }
                }
                continue;
            }
            Matcher m = marker.matcher(t);
            int from = searchFrom;
            while (from <= t.length() && m.find(from)) {
                from = m.end() + 1;
                int[] span = candidate(t, m.end());
                if (span != null) { setMerchant(f, x, span[0], span[1]); return; }
            }
        }
    }

    /** The merchant run after a marker, or null when it is a mask, a number, an InstaPay address or a generic noun. */
    private static int[] candidate(String t, int start) {
        Matcher stop = STOP.matcher(t);
        int end = stop.find(start) ? stop.start() : t.length();
        end = Math.min(end, start + ParsingPolicy.MERCHANT_MAX_CHARS);
        String value = t.substring(start, end).trim();
        String token = t.substring(start).split("\\s", 2)[0];
        if (value.isEmpty() || !value.matches("(?s).*\\p{L}.*") || value.matches("(?s).*[*#].*|(?s).*\\bx{2,}.*") ||
                Character.isDigit(value.charAt(0)) || INSTAPAY.matcher(token).find() || GENERIC.matcher(value).find() ||
                ATM_PHRASE.matcher(value).find()) return null;
        // "From HSBC:" is a sender header, not a counterparty.
        if (end < t.length() && t.charAt(end) == ':') return null;
        int lead = t.indexOf(value, start);
        return new int[]{lead, lead + value.length()};
    }

    private static void setMerchant(Folded f, Extraction x, int start, int end) {
        x.merchantSurface = f.display(start, end).trim();
        x.merchantKey = MerchantNormalizer.key(x.merchantSurface);
        if (x.merchantKey != null && x.merchantKey.isEmpty()) { x.merchantSurface = null; x.merchantKey = null; }
    }

    // ---- Card, account, counterparty ---------------------------------------------------------------------------

    private static void identities(Folded f, Extraction x) {
        String t = f.text;
        Matcher card = CARD.matcher(t);
        if (card.find()) x.cardLast4 = last4(card.group(1));
        // "to account 1234" names the recipient of an outgoing transfer, but the user's own account on an incoming one.
        Matcher account = ACCOUNT.matcher(t);
        boolean found = false;
        while (account.find()) {
            boolean toward = TOWARD.matcher(t.substring(Math.max(0, account.start() - 6), account.start())).find();
            if (toward && "out".equals(x.direction)) continue;
            x.accountLast4 = last4(account.group(1)); found = true; break;
        }
        if (!found) {
            Matcher ipn = ("in".equals(x.direction) ? IPN_ACCOUNT_IN : IPN_ACCOUNT_OUT).matcher(t);
            if (ipn.find()) x.accountLast4 = ipn.group(1);
        }
        Matcher instapay = INSTAPAY.matcher(t);
        Matcher phone = COUNTERPARTY_PHONE.matcher(t);
        Matcher digits = COUNTERPARTY_DIGITS.matcher(t);
        if (instapay.find()) x.counterparty = f.display(instapay.start(), instapay.end());
        else if (phone.find()) x.counterparty = f.display(phone.start(1), phone.end(1));
        else {
            Matcher name = COUNTERPARTY_NAME.matcher(t);
            if (name.find()) x.counterparty = f.display(name.start(1), name.end(1)).trim();
            else if (digits.find()) x.counterparty = digits.group(1);
        }
    }

    // ---- Bank-supplied event time ------------------------------------------------------------------------------

    private static void time(String t, long receivedAtMs, Extraction x) {
        Matcher d = DATE.matcher(t);
        LocalDate received = Instant.ofEpochMilli(receivedAtMs).atZone(ZoneOffset.UTC).toLocalDate();
        boolean ambiguous = false;
        try { while (d.find()) {
            String before = t.substring(Math.max(0, d.start() - ParsingPolicy.EXPIRY_WINDOW_CHARS), d.start());
            LocalDate date;
            boolean expiry = EXPIRY.matcher(before).find();
            try {
                if (d.group(1) != null) date = LocalDate.of(i(d.group(1)), i(d.group(2)), i(d.group(3)));
                else if (d.group(4) != null) date = LocalDate.of(year(d.group(6)), i(d.group(5)), i(d.group(4)));
                else if (d.group(7) != null) date = LocalDate.of(year(d.group(9)), month(d.group(8)), i(d.group(7)));
                else {
                    int first = i(d.group(10)), second = i(d.group(12));
                    if (first <= 12 && second <= 12) { ambiguous |= !expiry; continue; }
                    date = LocalDate.of(received.getYear(), second, first);
                    x.yearOrigin = "received_year";
                    if (date.isAfter(received.plusDays(1))) { date = date.minusYears(1); x.yearOrigin = "adjacent_year"; }
                }
            } catch (RuntimeException invalid) { continue; }
            if (expiry) { x.expiryDate = date.toString(); x.yearOrigin = null; continue; }
            x.datetime = date.toString(); x.timePrecision = "date";
            Matcher clock = TIME.matcher(t);
            if (clock.find(d.end()) && clock.start() - d.end() <= 25) {
                int hour = i(clock.group(1)), minute = i(clock.group(2));
                String half = clock.group(3) == null ? null : clock.group(3).toLowerCase(Locale.ROOT);
                if (half == null && hour >= 1 && hour <= 12 && t.substring(clock.end()).trim().isEmpty() &&
                        TRUNCATED_CLOCK.matcher(t.substring(0, clock.start())).find()) {
                    x.timeAmbiguity = "missing_am_pm"; // A truncated message ending at the hour cannot say AM or PM.
                } else if (hour < 24 && minute < 60) {
                    if (("pm".equals(half) || "م".equals(half)) && hour < 12) hour += 12;
                    if (("am".equals(half) || "ص".equals(half)) && hour == 12) hour = 0;
                    x.datetime = String.format(Locale.ROOT, "%sT%02d:%02d", date, hour, minute);
                    x.timePrecision = "minute";
                }
            }
            return;
        } } finally {
            // A day/month pair that could be either order is reported only when no complete date was found.
            if (ambiguous && x.datetime == null) x.timeAmbiguity = "ambiguous_day_month_missing_year";
        }
    }

    private static String last4(String token) { return token.substring(token.length() - 4); }

    private static int i(String s) { return Integer.parseInt(s); }
    private static int year(String s) { return s.length() == 2 ? 2000 + i(s) : i(s); }
    private static int month(String name) {
        String[] months = MONTHS.split("\\|");
        for (int k = 0; k < months.length; k++) if (months[k].equals(name.toLowerCase(Locale.ROOT))) return k % 12 + 1;
        throw new IllegalArgumentException(name);
    }

    // ---- Provisional decision (rules, no scoring) --------------------------------------------------------------

    private static void decide(String t, MessageClassifier.Result classified, Extraction x) {
        if ("in".equals(x.direction) && EXPENSE_OFFSET.matcher(t).find()) x.accountingEffect = "expense_offset";
        for (String code : new String[]{"MISSING_AMOUNT", "AMBIGUOUS_CURRENCY", "INVALID_SIGNED_AMOUNT", "AMBIGUOUS_NUMBER_FORMAT", "NON_POSITIVE_AMOUNT"})
            if (x.reasons.contains(code)) { x.decision = "UNUNDERSTOOD"; return; }
        if (x.direction == null) {
            if (classified.reasons.contains("CARD_MERCHANT_CONTEXT") && x.merchantKey != null) {
                x.direction = "out"; x.directionCertainty = "likely";
                x.reasons.add("CARD_MERCHANT_CONTEXT"); x.reasons.add("INFERRED_OUTGOING_DIRECTION");
            } else { x.reasons.add("AMBIGUOUS_DIRECTION"); x.decision = "UNUNDERSTOOD"; return; }
        }
        x.reasons.add("COMPLETED_EVENT");
        x.reasons.add("DIRECTION_" + x.direction.toUpperCase(Locale.ROOT));
        boolean review = "likely".equals(x.directionCertainty);
        if (INTERNAL.matcher(t).find()) { x.reasons.add("POSSIBLE_INTERNAL_TRANSFER"); review = true; }
        if (x.expiryDate != null && "in".equals(x.direction)) {
            x.reasons.add("EXPIRING_CREDIT");
            if (classified.reasons.contains("PROMO_SIGNALS")) x.reasons.add("PROMO_SIGNALS");
            review = true;
        }
        if (x.merchantKey == null && PURCHASE.matcher(t).find() && !NOT_A_PURCHASE.matcher(t).find()) { x.reasons.add("MISSING_MERCHANT"); review = true; }
        x.decision = review ? "REVIEW" : "AUTO";
    }

    private TransactionExtractor() {}
}
