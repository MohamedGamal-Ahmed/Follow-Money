package com.mohamedgamal.masareef;

import java.text.Normalizer;
import java.util.Locale;

/** Local merchant identities, independent of logos, account identity and money amounts. */
final class MerchantClassifier {
    private static final class Entry {
        final String name, category;
        final boolean branches;
        final String[] aliases;
        Entry(String name, String category, boolean branches, String... aliases) {
            this.name=name; this.category=category; this.branches=branches;
            this.aliases=new String[aliases.length];
            for(int i=0;i<aliases.length;i++) this.aliases[i]=textKey(aliases[i]);
        }
    }
    // Specific services precede broader brands. Short aliases never accept branch suffixes.
    private static final Entry[] ENTRIES = {
        new Entry("Uber Eats", "أكل وشرب", true, "Uber Eats"),
        new Entry("Butlers", "أكل وشرب", true, "Butlers & Co", "Butlers and Co", "Butlers"),
        new Entry("Seoudi", "بقالة", true, "Seoudi", "Saudi Market", "سعودي", "سعودى"),
        new Entry("Espresso Lab", "مقاهي", true, "Espresso Lab", "Espressolab", "إسبريسو لاب", "اسبرسو لاب"),
        new Entry("Cleopatra Hospital", "رعاية صحية", true, "Cleopatra Hospital", "Cleopatra Hospitals Group", "مستشفى كليوباترا", "مستشفيات كليوباترا"),
        new Entry("Carrefour", "بقالة", true, "Carrefour", "كارفور"),
        new Entry("Spinneys", "بقالة", true, "Spinneys", "سبينس"),
        new Entry("STARBUCKS", "مقاهي", true, "Starbucks", "ستاربكس"),
        new Entry("Costa Coffee", "مقاهي", true, "Costa Coffee", "كوستا كوفي"),
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

    static String textKey(String raw) {
        if(raw==null) return "";
        return Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("[\\p{M}]", "")
                .toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي')
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll(" +", " ");
    }
    private static Entry find(String raw) {
        if(raw==null) return null;
        // A processor is a prefix, not the merchant. Strip only explicit processor delimiters.
        String value=textKey(withoutProcessor(raw));
        for(Entry entry:ENTRIES) for(String key:entry.aliases) {
            if(value.equals(key) || entry.branches && key.length()>=4 && value.startsWith(key+" ")) return entry;
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
