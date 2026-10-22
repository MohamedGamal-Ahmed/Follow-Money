package com.mohamedgamal.masareef;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pipeline stage 2: what kind of message is this? Non-transactions stop here (IGNORE); several completed events in
 * one message stop as UNUNDERSTOOD; everything else continues to extraction. Generic wording rules only: no
 * per-bank templates, and the sender never decides the type. Shadow mode only.
 */
final class MessageClassifier {
    enum Type { TRANSACTION, PROMO, OTP, VERIFICATION, BALANCE_INQUIRY, REMINDER, OTHER }
    /** Stage decision: EXTRACT continues to extraction; the others are final for this message. */
    enum Decision { EXTRACT, IGNORE, UNUNDERSTOOD }

    static final class Result {
        final Type type;
        final Decision decision;
        final Set<String> reasons;
        /** Completed money events found; more than one is MULTIPLE_EVENTS. */
        final int events;
        Result(Type type, Decision decision, Set<String> reasons, int events) {
            this.type = type; this.decision = decision; this.reasons = Collections.unmodifiableSet(reasons); this.events = events;
        }
        /** Corpus spelling of the type. */
        String corpusType() { return type.name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static final Pattern VERIFICATION = compile(ParsingPolicy.VERIFICATION);
    private static final Pattern SECURITY_REQUEST = compile(ParsingPolicy.SECURITY_REQUEST);
    private static final Pattern BALANCE_INQUIRY = compile(ParsingPolicy.BALANCE_INQUIRY);
    private static final Pattern BALANCE_WORDS = compile(ParsingPolicy.BALANCE_WORDS);
    private static final Pattern PROMOTION = compile(ParsingPolicy.PROMOTION);
    private static final Pattern FUTURE = compile(ParsingPolicy.FUTURE_OR_REQUEST);
    private static final Pattern UNSUCCESSFUL = compile(ParsingPolicy.UNSUCCESSFUL);
    private static final Pattern REFUND = compile(ParsingPolicy.REFUND_OR_REVERSAL);
    private static final Pattern COMPLETED = compile(ParsingPolicy.COMPLETED_EVENT);
    private static final Pattern CONDITIONAL = compile(ParsingPolicy.CONDITIONAL);
    private static final Pattern COMPLETED_WITHOUT_AMOUNT = compile(ParsingPolicy.COMPLETED_WITHOUT_AMOUNT);
    private static final Pattern CARD = compile(ParsingPolicy.CARD_CONTEXT);
    private static final Pattern MERCHANT_MARKER = compile(ParsingPolicy.MERCHANT_MARKER);
    private static final Pattern CLAUSE = compile(ParsingPolicy.CLAUSE_BOUNDARY);
    private static final Pattern AMOUNT_ROLE_WORD = compile(ParsingPolicy.CLAUSE_AMOUNT_ROLE_WORD);
    private static final Pattern PENDING = compile(ParsingPolicy.PENDING_EVENT);
    private static final Pattern OTP_CODE = compile(ParsingPolicy.OTP_CODE);
    private static final Pattern NO_NEW_MOVEMENT = compile(ParsingPolicy.NO_NEW_MOVEMENT);
    private static final Pattern PAST_REFERENCE = compile(ParsingPolicy.PAST_TRANSACTION_REFERENCE);
    private static final Pattern RETURNED_MONEY = compile(ParsingPolicy.REFUND_OR_REVERSAL);
    private static final Pattern BALANCE_STATEMENT = compile("(?<!\\p{L})(?:ال)?رصيد(?:ك|كم)?\\s+(?:ال)?(?:حالي|متاح)|\\b(?:your |current |available )balance\\b");
    private static final Pattern INSTALLMENT_WORDING = compile(ParsingPolicy.INSTALLMENT_WORDING);
    private static final Pattern DEBITED_ACCOUNT_MASK = compile(ParsingPolicy.DEBITED_ACCOUNT_MASK);
    private static final Pattern NEW_MOVEMENT_VERB = compile(ParsingPolicy.NEW_MOVEMENT_VERB);
    private static final Pattern SUMMARY = compile(ParsingPolicy.SUMMARY);
    private static final Pattern RETAINED = compile(ParsingPolicy.RETAINED_FEE);
    private static final Pattern ADJUSTED = compile(ParsingPolicy.ADJUSTED);
    private static final Pattern EQUIVALENCE = compile(ParsingPolicy.CLAUSE_EQUIVALENCE);
    private static final Pattern LINE_PARTY = compile(ParsingPolicy.MERCHANT_MARKER + "|(?<!\\p{L})من(?!\\p{L})|\\bfrom\\b");

    /** Lines that each carry an amount and a party ("شراء لدى NOLA: 185 جنيه"): two or more mean several events. */
    private static int partyLines(String text) {
        int lines = 0;
        for (String line : MessageNormalizer.fold(text).split("\n"))
            if (!AmountScanner.scan(line).isEmpty() && LINE_PARTY.matcher(line).find()) lines++;
        return lines;
    }
    private static final Pattern AMOUNT_STATEMENT = compile(ParsingPolicy.AMOUNT_STATEMENT);
    private static final Pattern HOLD_RELEASED = compile(ParsingPolicy.HOLD_RELEASED);
    private static final Pattern COMMITMENT = compile(ParsingPolicy.COMMITMENT);

    static Result classify(MessageNormalizer.Normalized message) {
        Set<String> reasons = new LinkedHashSet<>();
        String text = message.matchText;
        if (message.empty()) return result(Type.OTHER, reasons, 0, "EMPTY_MESSAGE");
        boolean anyAmount = !AmountScanner.scan(text).isEmpty();
        if (!anyAmount) reasons.add("MISSING_AMOUNT");
        if (SECURITY_REQUEST.matcher(text).find() && !VERIFICATION.matcher(text).find())
            return result(Type.VERIFICATION, reasons, 0, "VERIFICATION_MESSAGE");
        // A code message needs a standalone code; "OTP" naming how a completed payment was authorised is not one.
        if (VERIFICATION.matcher(text).find() && OTP_CODE.matcher(text).find()) return result(Type.OTP, reasons, 0, "VERIFICATION_MESSAGE");
        if (BALANCE_INQUIRY.matcher(text).find()) return result(Type.BALANCE_INQUIRY, reasons, 0, "BALANCE_ONLY");
        // A status update about an earlier debit moves no new money.
        if (NO_NEW_MOVEMENT.matcher(text).find()) return result(Type.OTHER, reasons, 0, "NO_NEW_MOVEMENT");
        // A reference to an earlier operation without a new movement verb of its own is not a new event.
        // Inside a balance statement ("رصيدك الحالي … آخر عملية: …") the reference is part of a balance message.
        // Money returned (refund, reversal, re-posting) is itself a new movement, even when it names the earlier one.
        if (PAST_REFERENCE.matcher(text).find() && !NEW_MOVEMENT_VERB.matcher(text).find() && !RETURNED_MONEY.matcher(text).find())
            return BALANCE_STATEMENT.matcher(text).find() ? result(Type.BALANCE_INQUIRY, reasons, 0, "BALANCE_ONLY") : result(Type.OTHER, reasons, 0, "NO_NEW_MOVEMENT");
        // An installment confirmation naming no debited account or card goes to the commitments path, not the ledger.
        if (INSTALLMENT_WORDING.matcher(text).find() && !DEBITED_ACCOUNT_MASK.matcher(text).find() && COMPLETED.matcher(text).find() &&
                !FUTURE.matcher(text).find()) return result(Type.OTHER, reasons, 0, "COMMITMENT_PATH");
        // A summary of several operations, or two lines each with an amount and a party, is several events.
        if (anyAmount && (SUMMARY.matcher(text).find() && AmountScanner.scan(text).size() >= 2 || partyLines(message.text) >= 2)) {
            reasons.add("MULTIPLE_EVENTS");
            return new Result(Type.TRANSACTION, Decision.UNUNDERSTOOD, reasons, 2);
        }
        // A failed operation that kept its fee: the fee is the one movement.
        if (UNSUCCESSFUL.matcher(text).find() && RETAINED.matcher(text).find() && anyAmount) {
            reasons.add("RETAINED_FEE"); reasons.add("COMPLETED_EVENT");
            return new Result(Type.TRANSACTION, Decision.EXTRACT, reasons, 1);
        }
        // An adjusted operation (final amount after a hold) is one event, for review, whatever else it mentions.
        if (anyAmount && ADJUSTED.matcher(text).find()) {
            reasons.add("ADJUSTED_EVENT"); reasons.add("COMPLETED_EVENT");
            return new Result(Type.TRANSACTION, Decision.EXTRACT, reasons, 1);
        }

        int events = 0;
        boolean signed = false, refund = REFUND.matcher(text).find();
        // One event per clause that pairs an amount with a completed verb, a refund, or a signed (+/-) amount.
        int elided = 0, bareAmounts = 0;
        boolean verbWithoutAmount = false;
        for (String clause : clauses(text)) {
            java.util.List<AmountScanner.Money> amounts = AmountScanner.scan(clause);
            // Conditional or future clauses ("will be charged next month") never describe a completed event.
            if (CONDITIONAL.matcher(clause).find() || FUTURE.matcher(clause).find()) continue;
            if (amounts.isEmpty()) { verbWithoutAmount |= COMPLETED.matcher(clause).find(); continue; }
            boolean clauseSigned = false;
            for (AmountScanner.Money money : amounts) clauseSigned |= money.trailingSign != 0;
            signed |= clauseSigned;
            java.util.regex.Matcher verb = COMPLETED.matcher(clause), role = AMOUNT_ROLE_WORD.matcher(clause);
            boolean completedHere = verb.find();
            // "Total posted: EGP 1,281.84", "رسوم التحويل … (تم خصم 50)": a role word before the verb owns the amount.
            boolean roleFirst = completedHere && role.find() && role.start() < verb.start();
            // After the first event, an exchange-rate/equivalent restatement ("EGP 903.54 credited at the rate") is not another.
            boolean restatement = events > 0 && EQUIVALENCE.matcher(clause).find();
            if (roleFirst || restatement) continue;
            if (clauseSigned || completedHere || REFUND.matcher(clause).find()) events++;
            else if (MERCHANT_MARKER.matcher(clause).find() && !AMOUNT_ROLE_WORD.matcher(clause).find()) elided++;
            else if (!AMOUNT_ROLE_WORD.matcher(clause).find() && AMOUNT_STATEMENT.matcher(clause).find()) bareAmounts++;
        }
        // A completion stated in one sentence and its amount in the next ("تم دفع طلب X بنجاح. المبلغ 725 جنيه") is one event.
        if (events == 0 && verbWithoutAmount && bareAmounts > 0) events = 1;
        // Safety rule: after one completed event, a further "amount at merchant" clause is a second event, never ignored.
        if (events > 0) events += elided;
        boolean completed = events > 0 || COMPLETED_WITHOUT_AMOUNT.matcher(text).find();
        boolean promo = PROMOTION.matcher(text).find();
        if (promo && !completed) return result(Type.PROMO, reasons, 0, "PROMOTIONAL_MESSAGE");
        if (FUTURE.matcher(text).find() && !completed) return result(Type.REMINDER, reasons, 0, "FUTURE_ONLY");
        // A released hold or a not-yet-executed operation moved no money; neither is a failure.
        if (HOLD_RELEASED.matcher(text).find() && !refund) return result(Type.OTHER, reasons, 0, "HOLD_RELEASED");
        if (PENDING.matcher(text).find() && !completed) return result(Type.OTHER, reasons, 0, "PENDING_EVENT");
        if (UNSUCCESSFUL.matcher(text).find() && !refund) return result(Type.OTHER, reasons, events, "UNSUCCESSFUL_EVENT");
        if (!completed && anyAmount && CARD.matcher(text).find() && MERCHANT_MARKER.matcher(text).find()) {
            reasons.add("CARD_MERCHANT_CONTEXT");
            completed = true;
        }
        if (completed) {
            if (promo) reasons.add("PROMO_SIGNALS");
            if (refund) reasons.add("REFUND_OR_REVERSAL");
            if (signed) reasons.add("SIGNED_AMOUNT");
            if (message.groupSummary) reasons.add("GROUP_SUMMARY");
            if (COMMITMENT.matcher(text).find()) reasons.add("COMMITMENT_EVENT");
            if (events > 1) { reasons.add("MULTIPLE_EVENTS"); return new Result(Type.TRANSACTION, Decision.UNUNDERSTOOD, reasons, events); }
            reasons.add("COMPLETED_EVENT");
            return new Result(Type.TRANSACTION, Decision.EXTRACT, reasons, events);
        }
        if (anyAmount && BALANCE_WORDS.matcher(text).find()) return result(Type.BALANCE_INQUIRY, reasons, 0, "BALANCE_ONLY");
        return result(Type.OTHER, reasons, 0, "NO_COMPLETED_EVENT");
    }

    /** Splits at clause boundaries, except inside a money mention ("15 ج.م." and "L.E. 250" keep their dots). */
    static java.util.List<String> clauses(String text) {
        java.util.List<AmountScanner.Money> money = AmountScanner.scan(text);
        java.util.List<String> parts = new java.util.ArrayList<>();
        java.util.regex.Matcher boundary = CLAUSE.matcher(text);
        int from = 0;
        while (boundary.find()) {
            boolean inside = false;
            // The closing dot of "ج.م." may also end the sentence; only dots strictly inside a mention are protected.
            for (AmountScanner.Money m : money) inside |= boundary.start() >= m.start && boundary.start() < m.end - 1;
            if (inside) continue;
            parts.add(text.substring(from, boundary.start()));
            from = boundary.end();
        }
        parts.add(text.substring(from));
        return parts;
    }

    private static Result result(Type type, Set<String> reasons, int events, String reason) {
        reasons.add(reason);
        return new Result(type, Decision.IGNORE, reasons, events);
    }

    private static Pattern compile(String regex) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE); }

    private MessageClassifier() {}
}
