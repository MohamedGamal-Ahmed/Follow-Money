package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalized merchant key from the merchant's surface text, as defined by the corpus: folded letters, no
 * punctuation, a leading payment-processor descriptor removed when a merchant follows it, and trailing
 * country/city descriptors removed. A key is surface text, not a catalog identity, logo or transliteration.
 */
final class MerchantNormalizer {
    private static final Pattern QUOTES = Pattern.compile("^[\"'“”«»]+|[\"'“”«»]+$");
    private static final Pattern STARRED = processorPattern(ParsingPolicy.STARRED_PROCESSORS, "\\s*\\*\\s*(?=\\S)");
    private static final Pattern BARE = processorPattern(ParsingPolicy.BARE_PROCESSORS, "\\s+(?=\\p{L})");

    /** Null for null input; "" when nothing but punctuation or a lone descriptor remains. */
    static String key(String surface) {
        if (surface == null) return null;
        String value = QUOTES.matcher(MessageNormalizer.text(surface).replace('\n', ' ').trim()).replaceAll("").trim();
        String stripped = STARRED.matcher(value).replaceFirst("");
        if (stripped.equals(value)) stripped = BARE.matcher(value).replaceFirst("");
        String folded = MessageNormalizer.fold(stripped).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (folded.isEmpty()) return "";
        List<String> tokens = new ArrayList<>(Arrays.asList(folded.split(" ")));
        // Only from the end, and never the whole name: CAIRO BOOKS keeps CAIRO; a lone "EGYPT" stays.
        while (tokens.size() > 1 && trailingLocation(tokens.get(tokens.size() - 1))) tokens.remove(tokens.size() - 1);
        return String.join(" ", tokens);
    }

    private static boolean trailingLocation(String token) {
        for (String location : ParsingPolicy.TRAILING_LOCATIONS)
            if (MessageNormalizer.fold(location).equals(token)) return true;
        return false;
    }

    private static Pattern processorPattern(String[] tokens, String separator) {
        StringBuilder names = new StringBuilder();
        for (String token : tokens) names.append(names.length() == 0 ? "" : "|").append(Pattern.quote(token.toLowerCase(Locale.ROOT)));
        return Pattern.compile("(?iu)^(?:" + names + ")" + separator);
    }

    private MerchantNormalizer() {}
}
