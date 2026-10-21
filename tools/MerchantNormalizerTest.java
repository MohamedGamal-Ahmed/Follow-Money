package com.mohamedgamal.masareef;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Merchant keys against corpus v2 oracles, and the shadow catalog index (exact, branch, conservative fuzzy). */
public final class MerchantNormalizerTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    /** Corpus row -> merchant surface text exactly as written in that row's message. */
    private static final String[][] SURFACES = {
        {"p001", "Kashier Butlers"}, {"p002", "KASHIER*BUTLERS"}, {"p004", "Nova Market"}, {"p008", "سعودي"},
        {"p011", "عمر علي"}, {"p097", "\"سعودى\""}, {"p098", "KASHIER*BUTLERS"}, {"p101", "FAWRY*BLUE BAKERY EGYPT"},
        {"p102", "SQ *LUNA CAFE EG"}, {"p103", "PAYPAL *ORBIT LABS CAIRO"}, {"p104", "GEIDEA*GREEN LEAF EGYPT"},
        {"p107", "\"مَكْتَبَة النُّور\""}, {"p110", "إيـكيا"}, {"p112", "متجر الهدى"}, {"p113", "مكتبة أمان"},
        {"p116", "Paper Moon"}, {"p117", "Butler's & Co.! EG"}, {"p118", "Studio 54"}, {"p119", "CAIRO BOOKS"},
        {"p120", "PAYPAL"}, {"p129", "بوتلرز"}, {"p134", "Mona Ali"}, {"p145", "أحمد علي"}, {"p147", "salma@newbank"},
        {"p148", "omar@unknownwallet"}, {"p149", "ATM RIVER"}, {"p152", "NOVA BUILD"}, {"p153", "منى سامي"},
        {"p159", "BLUE BAKERY"}, {"pub-cib-ar", "SAMPLE COFFEE STORE"}, {"pub-cib-refund", "SAMPLE CLOUD"},
        {"pub-nbe-debit", "NBE ATM546"}, {"pub-nbe-card", "KASHIERFast"}, {"pub-nbe-instant", "اسم تجريبي"},
        {"syn-cib-dear-en", "SAMPLE PHARMACY"}, {"syn-cib-dear-ar", "صيدلية النور"}, {"syn-nbe-dear-credit", "هايبر وان"},
        {"syn-nbe-dear-debit", "مخبز الأمانة"}, {"syn-dual-en", "GOOGLE PLAY"}, {"syn-dual-ar", "جوجل بلاي"},
        {"syn-reversal-ar", "متجر الهدى"}, {"syn-reversal-en", "SAMPLE STORE"}, {"syn-fawry-ar", "الكهرباء"},
        {"syn-fawry-recharge-ar", "اتصالات"}, {"syn-fawry-en", "Vodafone"}, {"syn-vfcash-pay", "سوبر ماركت الرحمة"},
    };

    public static void main(String[] args) throws Exception {
        List<ParserCorpusFixture.Row> rows = ParserCorpusFixture.readV2(Path.of("tools/fixtures/parser-corpus-v2-draft.tsv"));
        Map<String, ParserCorpusFixture.Row> ids = new LinkedHashMap<>();
        for (ParserCorpusFixture.Row row : rows) ids.put(row.get("id"), row);
        List<String> mismatches = new ArrayList<>();
        for (String[] pair : SURFACES) {
            ParserCorpusFixture.Row row = ids.get(pair[0]);
            check(row != null, "Corpus row " + pair[0]);
            check(MessageNormalizer.text(row.inputText()).contains(MessageNormalizer.text(pair[1]).replace("\"", "")), "Surface occurs in its message " + pair[0]);
            String key = MerchantNormalizer.key(pair[1]);
            if (!Objects.equals(key, row.get("expected_merchant_key"))) mismatches.add(pair[0] + ": " + key + " != " + row.get("expected_merchant_key"));
            checks++;
        }
        check(mismatches.isEmpty(), "Merchant keys differ from corpus oracles: " + mismatches);

        // Policy edges from the corpus README.
        same(MerchantNormalizer.key(null), null, "Null merchant");
        same(MerchantNormalizer.key("\"  \""), "", "Punctuation only");
        same(MerchantNormalizer.key("EGYPT"), "egypt", "Lone location token is the name");
        same(MerchantNormalizer.key("بنك مصر"), "بنك مصر", "Arabic place name stays");
        same(MerchantNormalizer.key("Paymob Talabat"), "talabat", "Bare processor before a merchant");
        same(MerchantNormalizer.key("SQ"), "sq", "Lone processor token stays");
        same(MerchantNormalizer.key("١٢٣ Store"), "123 store", "Digits in names are kept, not deleted");

        // Catalog index.
        MerchantCatalogIndex index = MerchantCatalogIndex.standard();
        String[][] lookups = {
            {"Carrefour", "EXACT:Carrefour"}, {"كارفور", "EXACT:Carrefour"}, {"KASHIER*BUTLERS", "EXACT:Butlers"},
            {"\"سعودى\"", "EXACT:Seoudi"}, {"MCDONALD", "EXACT:McDonald's"}, {"Uber Eats Cairo", "EXACT:Uber Eats"},
            {"Carrefour Maadi", "BRANCH_PREFIX:Carrefour"}, {"Uber Eats Maadi", "BRANCH_PREFIX:Uber Eats"},
            {"Starbuks", "FUZZY:STARBUCKS~1"}, {"CARREFOR", "FUZZY:Carrefour~1"}, {"Spinnys", "FUZZY:Spinneys~1"},
            {"Netlfix", "FUZZY:Netflix~1"},
            {"KFC Maadi", "NONE"}, {"Max Mall", "NONE"}, {"Ubar", "NONE"}, {"Netflix2", "NONE"}, {"Starbcks Cafe", "NONE"},
            {"Orbit Labs", "NONE"}, {"Xarrefour", "NONE"}, {"Carrefourxx", "NONE"},
        };
        for (String[] lookup : lookups) same(index.lookup(lookup[0]).toString(), lookup[1], "Catalog lookup " + lookup[0]);
        MerchantCatalogIndex ambiguous = new MerchantCatalogIndex(List.of(new String[]{"Alpha One", "whole", "alphaone"}, new String[]{"Alpha Two", "whole", "alphaono"}));
        same(ambiguous.lookup("alphaonx").method, MerchantCatalogIndex.Method.NONE, "Two equally close merchants are never guessed");
        same(MerchantCatalogIndex.edits("starbucks", "starbukcs"), 1, "Adjacent transposition is one edit");

        // Exact path parity with the legacy classifier for every catalog alias.
        int parity = 0;
        for (String[] entry : MerchantClassifier.catalog()) for (int i = 2; i < entry.length; i++) {
            MerchantCatalogIndex.Match match = index.lookup(entry[i]);
            same(match.method, MerchantCatalogIndex.Method.EXACT, "Alias is exact " + entry[i]);
            same(match.name, MerchantClassifier.displayName(entry[i]), "Same identity as legacy for " + entry[i]);
            parity++;
        }

        // False-positive guard: the only corpus merchant reaching the catalog by fuzzy matching is the intended one
        // ("Butler's & Co." is one edit from the alias "Butlers & Co"). Its key stays surface text; identity is separate.
        Map<String, String> allowedFuzzy = Map.of("butler s co", "FUZZY:Butlers~1");
        TreeSet<String> corpusKeys = new TreeSet<>();
        for (ParserCorpusFixture.Row row : rows) if (row.get("expected_merchant_key") != null) corpusKeys.add(row.get("expected_merchant_key"));
        int known = 0, none = 0, fuzzy = 0;
        for (String key : corpusKeys) {
            MerchantCatalogIndex.Match match = index.lookupKey(key);
            if (match.method == MerchantCatalogIndex.Method.FUZZY) { same(match.toString(), allowedFuzzy.get(key), "Unexpected fuzzy corpus match " + key); fuzzy++; }
            else if (match.found()) known++; else none++;
        }
        same(fuzzy, allowedFuzzy.size(), "Intended fuzzy corpus match present");
        System.out.println("Merchant normalizer checks passed: " + checks + "; " + SURFACES.length + " corpus surfaces; " + parity +
                " catalog aliases match legacy; " + corpusKeys.size() + " corpus merchants: " + known + " exact/branch, " + fuzzy + " fuzzy (intended), " + none + " unknown.");
    }
}
