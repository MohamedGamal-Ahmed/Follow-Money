package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Stage-2 classification: every corpus row, plus held-out messages written outside the corpus. */
public final class MessageClassifierTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    /**
     * Rows whose classification differs from the corpus and is left for the owner to decide. Empty now; a new
     * disagreement must be listed here explicitly (and reported), never fixed by editing the corpus.
     */
    static final Set<String> KNOWN_DISAGREEMENTS = Set.of();

    static MessageClassifier.Result classify(String text) {
        return MessageClassifier.classify(MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", text)));
    }

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        int disagreements = 0;
        for (ParserCorpusFixture.Row row : rows) {
            String id = row.get("id");
            MessageClassifier.Result result = MessageClassifier.classify(MessageNormalizer.normalize(MessageNormalizerTest.raw(row)));
            boolean multiple = row.get("required_reasons").contains("MULTIPLE_EVENTS");
            boolean agrees = result.corpusType().equals(row.get("expected_type")) &&
                    (multiple == (result.decision == MessageClassifier.Decision.UNUNDERSTOOD));
            if (!agrees) { check(KNOWN_DISAGREEMENTS.contains(id), "Unlisted classification disagreement " + id + ": " + result.corpusType() + "/" + result.decision + " " + result.reasons); disagreements++; continue; }
            check(!KNOWN_DISAGREEMENTS.contains(id), "Listed disagreement now agrees; remove it: " + id);
            if (!"transaction".equals(row.get("expected_type")))
                for (String reason : row.get("required_reasons").split("\\|")) check(result.reasons.contains(reason), id + " missing reason " + reason + " in " + result.reasons);
            if (multiple) check(result.reasons.contains("MULTIPLE_EVENTS"), id + " multiple events reason");
            check((result.decision == MessageClassifier.Decision.IGNORE) == !"transaction".equals(row.get("expected_type")), id + " stage decision follows type");
        }

        // Held-out messages: not in the corpus, written to probe generality rather than the corpus wording.
        String[][] heldOut = {
            {"You paid EGP 100 to QA SHOP", "TRANSACTION", "EXTRACT"},
            {"تم خصم 75 جنيه من بطاقتك المنتهية 4411 لدى كارفور المعادي", "TRANSACTION", "EXTRACT"},
            {"Your account was credited with EGP 2,000.00 on 05/11/2026.", "TRANSACTION", "EXTRACT"},
            {"تم سحب نقدي 1000 جنيه من ماكينة الصراف. الرصيد 3000 جنيه", "TRANSACTION", "EXTRACT"},
            {"Paid EGP 50 at Cafe Nine. Enjoy 10% cashback on your next order!", "TRANSACTION", "EXTRACT"},
            {"Refund of USD 4.99 from APP STORE has been processed.", "TRANSACTION", "EXTRACT"},
            {"تم دفع 40 جنيه في مطعم. تم دفع 60 جنيه في صيدلية.", "TRANSACTION", "UNUNDERSTOOD"},
            {"Your OTP is 4455. Never share it.", "OTP", "IGNORE"},
            {"كود التفعيل الخاص بك: رمز التحقق 7788", "OTP", "IGNORE"},
            {"Get 50% off all burgers today only!", "PROMO", "IGNORE"},
            {"اشترك في الباقة الجديدة بسعر 99 جنيه فقط", "PROMO", "IGNORE"},
            {"Your electricity bill of EGP 300 is due on 25/10.", "REMINDER", "IGNORE"},
            {"سيتم تحويل 500 جنيه إلى حسابك خلال يومين", "REMINDER", "IGNORE"},
            {"Payment of EGP 99 at Nova failed. Please try again.", "OTHER", "IGNORE"},
            {"عذرا، لم تتم عملية التحويل بمبلغ 300 جنيه", "OTHER", "IGNORE"},
            {"Your available balance is EGP 12,000.00", "BALANCE_INQUIRY", "IGNORE"},
            {"Your package has shipped. Order 77881.", "OTHER", "IGNORE"},
            {"Support case EGP-2211 closed.", "OTHER", "IGNORE"},
        };
        for (String[] probe : heldOut) {
            MessageClassifier.Result result = classify(probe[0]);
            same(result.type.name() + "/" + result.decision, probe[1] + "/" + probe[2], "Held-out: " + probe[0] + " " + result.reasons);
        }

        // Clause splitting keeps abbreviation dots and protects decimals.
        same(MessageClassifier.clauses("تم دفع 15 ج.م. في متجر").size(), 2, "Unit dot may end the sentence, inner dot never");
        same(AmountScanner.scan("from 123-456***-789 egp 12,345.67+ x").get(0).trailingSign, '+', "Masked digits do not steal the unit");
        same(AmountScanner.scan("Support EGP-1234 updated").size(), 0, "Hyphen-joined identifier is not money");
        same(AmountScanner.scan("Paid EGP -120 at X").get(0).leadingMinus, true, "Spaced signed amount is money");
        same(AmountScanner.minor("1.250"), -1L, "Single dot with three digits is ambiguous");
        same(AmountScanner.minor("1.250,50"), 125050L, "European grouping");
        same(AmountScanner.minor("1,250,000"), 125000000L, "Repeated thousands");
        same(AmountScanner.minor(".93"), 93L, "Leading decimal");
        same(AmountScanner.minor("125,50"), 12550L, "Two-place decimal comma");
        same(AmountScanner.scan("تم دفع 100 ريال").get(0).currency, null, "Bare riyal stays ambiguous");
        same(AmountScanner.scan("Paid $12.50").get(0).currency, "USD", "Bare dollar sign is USD (owner decision)");

        System.out.println("Message classifier checks passed: " + checks + " (" + rows.size() + " corpus rows, " + disagreements +
                " listed disagreements, " + heldOut.length + " held-out messages).");
    }
}
