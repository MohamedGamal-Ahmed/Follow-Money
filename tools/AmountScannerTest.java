package com.mohamedgamal.masareef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Owner rule (21 Oct 2026): a number ends at the first character that is neither a digit nor a separator before a
 * digit; a glued word or currency after it is a separate word, and no fraction is ever lost. Generated glued shapes,
 * then every corpus and held-out text: no scanned amount may stop just before ".digit" or ",digit".
 */
public final class AmountScannerTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    private static final Pattern CUT = Pattern.compile("^[.,]?\\d");

    /** Every money mention in this text ends where its number ends. */
    static int maximal(String text, String id) {
        String folded = MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", text)).matchText;
        int found = 0;
        for (AmountScanner.Money m : AmountScanner.scan(folded)) {
            int numberEnd = folded.indexOf(m.rawNumber, m.start) + m.rawNumber.length();
            check(!CUT.matcher(folded.substring(numberEnd)).find(), "Fraction lost in " + id + ": " + m.rawNumber + " before '" +
                    folded.substring(numberEnd, Math.min(folded.length(), numberEnd + 4)) + "'");
            found++;
        }
        return found;
    }

    public static void main(String[] args) throws Exception {
        String[][] numbers = {{"15", "1500"}, {"15.00", "1500"}, {"149.98", "14998"}, {"1,897.71", "189771"}, {"2000", "200000"}, {"0.50", "50"}, {"12,345.67", "1234567"}};
        String[] units = {"EGP", "ج.م", "جنيه"};
        String[] glued = {"عند", "لدى", "at", "في"};
        int generated = 0;
        for (String[] n : numbers) for (String unit : units) for (String word : glued) {
            String[] forms = {unit + " " + n[0] + word + " متجر", unit + n[0] + word + " متجر", n[0] + unit + " " + word + " متجر", n[0] + " " + unit + " " + word + " متجر",
                    "خصم " + unit + " " + n[0] + word, "تم خصم " + n[0] + " " + unit + word.replaceAll(".*", " ") + word,
                    // A word of the other script glued after the currency ("44.95 EGPعند"); same-script gluing is one word.
                    unit.matches("[A-Z]+") == word.matches("[a-z]+") ? n[0] + " " + unit + " " + word : n[0] + " " + unit + word + " متجر"};
            for (String form : forms) {
                String folded = MessageNormalizer.normalize(MessageNormalizer.Raw.sms("x", form)).matchText;
                List<AmountScanner.Money> money = AmountScanner.scan(folded);
                check(money.size() == 1, "One amount in [" + form + "]: " + money.size());
                same(String.valueOf(money.get(0).minor), n[1], "Exact minor units for [" + form + "]");
                check(money.get(0).currency != null && money.get(0).currency.equals("EGP"), "Currency for [" + form + "]");
                generated++;
            }
        }
        // A bare number glued to a word is still not money (no currency).
        check(AmountScanner.scan("تم خصم 15عند متجر").isEmpty(), "Bare number is never money");

        int texts = 0, amounts = 0;
        for (ParserCorpusFixture.Row row : ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"))) {
            amounts += maximal(MessageNormalizer.normalize(MessageNormalizerTest.raw(row)).text, row.get("id")); texts++;
        }
        for (String set : CorpusExpectations.HELD_OUT_SETS)
            for (String line : Files.readAllLines(Path.of(set))) {
                if (line.startsWith("#") || line.startsWith("id\t")) continue;
                for (String cell : line.split("\t")) if (cell.matches(".*\\d.*") && cell.matches(".*\\p{L}.*")) { amounts += maximal(cell.replace("\\n", "\n"), set); texts++; }
            }
        System.out.println("Amount scanner checks passed: " + checks + " (" + generated + " generated glued shapes, " + texts + " corpus/held-out texts, " + amounts + " amounts never cut).");
    }
}
