package com.mohamedgamal.masareef;

import java.text.Normalizer;
import java.util.Locale;

/** Local merchant identities, independent of logos, account identity and money amounts. */
final class MerchantClassifier {
    private static final class Entry {
        final String name, category;
        final boolean branches;
        final String[] aliases;
        /** Aliases that must equal the whole merchant field: never a prefix of a longer name, never free text. */
        final boolean[] whole;
        Entry(String name, String category, boolean branches, String... aliases) {
            this.name=name; this.category=category; this.branches=branches;
            this.aliases=new String[aliases.length];
            this.whole=new boolean[aliases.length];
            for(int i=0;i<aliases.length;i++) { this.aliases[i]=textKey(aliases[i]); this.whole[i]=!branches || WHOLE_ONLY.contains(this.aliases[i]); }
        }
    }
    /**
     * Short or common-word aliases (owner decision, 5 Nov 2026): matched against the whole merchant field only. «حساب» is in
     * every bank SMS; «WE», «NOON», «TOTAL» and the university acronyms are ordinary words and letters.
     */
    private static final java.util.Set<String> WHOLE_ONLY = new java.util.HashSet<>();
    static {
        for (String alias : new String[]{"حساب", "رعاية", "التعاون", "المختبر", "WE", "GAD", "SIP", "BIM", "2B", "NOON", "TOTAL", "SEIF",
                "AUC", "GUC", "BUE", "MSA", "ASU", "PAUL"}) WHOLE_ONLY.add(textKey(alias));
    }
    // Specific services precede broader brands. Short aliases never accept branch suffixes.
    private static final Entry[] HAND = {
        new Entry("Uber Eats", "أكل وشرب", true, "Uber Eats"),
        new Entry("Butlers", "أكل وشرب", true, "Butlers & Co", "Butlers and Co", "Butlers"),
        new Entry("Seoudi", "بقالة", true, "Seoudi", "Saudi Market", "سعودي", "سعودى"),
        new Entry("Espresso Lab", "مقاهي", true, "Espresso Lab", "Espressolab", "إسبريسو لاب", "اسبرسو لاب"),
        new Entry("Cleopatra Hospital", "صحة", true, "Cleopatra Hospital", "Cleopatra Hospitals Group", "مستشفى كليوباترا", "مستشفيات كليوباترا"),
        new Entry("Carrefour", "بقالة", true, "Carrefour", "كارفور"),
        new Entry("Spinneys", "بقالة", true, "Spinneys", "سبينس"),
        new Entry("STARBUCKS", "مقاهي", true, "Starbucks", "ستاربكس"),
        new Entry("Costa Coffee", "مقاهي", true, "Costa Coffee", "كوستا كوفي"),
        new Entry("Sharaf DG", "تسوق", true, "Sharaf DG", "SharafDG", "شرف دي جي"),
        new Entry("Centrepoint", "تسوق", true, "Centrepoint", "Centre Point", "سنتر بوينت"),
        new Entry("Max", "تسوق", false, "Max", "Max Fashion", "ماكس"),
        new Entry("LC Waikiki", "تسوق", true, "LC Waikiki", "LCW", "إل سي وايكيكي", "ال سي وايكيكي"),
        new Entry("Raya", "تسوق", true, "Raya", "Raya Shop", "راية", "راية شوب"),
        new Entry("illy", "مقاهي", true, "illy", "illy caffe"),
        new Entry("Pizza King", "أكل وشرب", true, "Pizza King", "بيتزا كينج", "بيتزا كنج"),
        new Entry("Etoile", "أكل وشرب", true, "Etoile", "Etoile Patisserie", "إتوال"),
        new Entry("Ted's", "أكل وشرب", true, "Ted's", "Teds"),
        new Entry("Zaher", "بقالة", true, "Zaher", "زاهر"),
        new Entry("El Ezaby", "صحة", true, "El Ezaby", "Ezaby Pharmacy", "العزبي", "صيدلية العزبي"),
        new Entry("McDonald's", "أكل وشرب", true, "McDonald's", "McDonalds", "MCDONALD", "ماكدونالدز", "ماكدونالد"),
        new Entry("KFC", "أكل وشرب", false, "KFC", "كنتاكي"),
        new Entry("Talabat", "أكل وشرب", true, "Talabat", "طلبات"),
        new Entry("Uber", "مواصلات", true, "Uber", "أوبر", "اوبر"),
        new Entry("Careem", "مواصلات", true, "Careem", "كريم"),
        new Entry("IKEA", "تسوق", true, "IKEA", "إيكيا"),
        new Entry("Netflix", "اشتراكات", true, "Netflix", "نتفليكس"),
        new Entry("Spotify", "اشتراكات", true, "Spotify", "سبوتيفاي"),
        new Entry("Vodafone", "فواتير", false, "Vodafone", "فودافون"),
        new Entry("Orange", "فواتير", false, "Orange", "أورنج"),
        new Entry("ChatGPT", "اشتراكات", true, "OpenAI ChatGPT", "ChatGPT", "OpenAI"),
        new Entry("Claude", "اشتراكات", true, "Claude", "Anthropic"),
        new Entry("Truecaller", "اشتراكات", true, "Truecaller"),
        new Entry("OneDrive", "اشتراكات", true, "OneDrive"),
        new Entry("Google One", "اشتراكات", true, "Google One")
    };

    /** The hand-written entries first (they carry reviewed branch rules), then the generated catalog; the first match wins. */
    private static final Entry[] ENTRIES = withCatalog();

    private static Entry[] withCatalog() {
        java.util.List<Entry> all = new java.util.ArrayList<>(java.util.Arrays.asList(HAND));
        for (String[] row : MerchantCatalogData.CATALOG)
            all.add(new Entry(row[0], row[2], true, java.util.Arrays.copyOfRange(row, 3, row.length)));
        return all.toArray(new Entry[0]);
    }

    /** Keyword rules from the owner's file, applied only when no catalog entry matched. */
    private static final String[][] KEYWORD_RULES = MerchantCatalogData.KEYWORDS;

    /** Folded for keyword comparison only: letters, no punctuation, ة as ه. */
    private static String keywordKey(String raw) { return textKey(raw).replace('ة','ه'); }

    /**
     * The category a merchant NAME implies by whole words or phrases it contains («صيدلية الأمل», "City Pharmacy"); null when
     * a catalog entry matched (that entry decides) or no keyword is present. The longest matching keyword wins
     * ("GAS STATION" over "GAS"), then the earlier row.
     */
    static String keywordCategory(String raw) {
        if (raw == null || find(raw) != null) return null;
        String name = " " + keywordKey(raw) + " ", best = null;
        int length = 0;
        for (String[] rule : KEYWORD_RULES) for (int i = 1; i < rule.length; i++) {
            String word = keywordKey(rule[i]);
            if (word.length() > length && name.contains(" " + word + " ")) { best = rule[0]; length = word.length(); }
        }
        return best;
    }

    static String textKey(String raw) {
        if(raw==null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("[\\p{M}]", "")
                .toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي')
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
    }
    private static Entry find(String raw) {
        if(raw==null) return null;
        // Bare processor prefixes are accepted only when the remainder is a known merchant.
        String value=textKey(withoutProcessor(raw));
        Entry direct=findKey(value);
        if(direct!=null)return direct;
        for(String processor:new String[]{"kashier ","paymob "})
            if(value.startsWith(processor))return findKey(value.substring(processor.length()));
        return null;
    }
    private static Entry findKey(String value) {
        for(Entry entry:ENTRIES) for(int i=0;i<entry.aliases.length;i++) {
            String key=entry.aliases[i];
            if(value.equals(key) || !entry.whole[i] && key.length()>=4 && value.startsWith(key+" ")) return entry;
        }
        return null;
    }
    static String displayName(String raw) {
        Entry entry=find(raw);
        return entry==null ? withoutProcessor(raw).replaceAll("\\s+", " ") : entry.name;
    }
    static boolean processorDescriptor(String raw) {
        return raw!=null && raw.matches("(?is)^(?:KASHIER|PAYMOB)\\s*\\*\\s*[\\p{L}].*");
    }
    private static String withoutProcessor(String raw) {
        return raw==null?"":raw.trim().replaceFirst("(?i)^(?:KASHIER|PAYMOB)\\s*\\*\\s*", "");
    }
    /** Read-only catalog view for the shadow MerchantCatalogIndex: {name, "branches"|"whole", textKey aliases...}. */
    static java.util.List<String[]> catalog() {
        java.util.List<String[]> rows=new java.util.ArrayList<>();
        for(Entry entry:ENTRIES) for(boolean whole:new boolean[]{false,true}){
            java.util.List<String> keys=new java.util.ArrayList<>(java.util.Arrays.asList(entry.name,whole?"whole":"branches"));
            for(int i=0;i<entry.aliases.length;i++) if(entry.whole[i]==whole) keys.add(entry.aliases[i]);
            if(keys.size()>2) rows.add(keys.toArray(new String[0]));
        }
        return rows;
    }
    static String category(String raw) { Entry entry=find(raw); return entry==null?null:entry.category; }
    static String identity(String raw) { return textKey(displayName(raw)); }
    static boolean sameMerchant(String first, String second) {
        String key=identity(first); return !key.isEmpty() && key.equals(identity(second));
    }
    static boolean matchesRule(String merchant, String rule) {
        if(rule!=null && rule.startsWith("=")) return identity(merchant).equals(rule.substring(1));
        String name=textKey(merchant), match=textKey(rule);
        // Complete words, not "gas" inside "Vegas" or "Uber" inside an unrelated word.
        return !match.isEmpty() && ((" "+name+" ").contains(" "+match+" ") || sameMerchant(merchant,rule));
    }
    private MerchantClassifier() {}
}
