package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merchant catalog indexed by MerchantNormalizer keys: exact key, then a known alias followed by a branch
 * suffix, then a conservative one-edit fuzzy match that must be unique. Shadow-mode proposals only.
 */
final class MerchantCatalogIndex {
    enum Method { EXACT, BRANCH_PREFIX, FUZZY, NONE }

    static final class Match {
        final String name, key;
        final Method method;
        final int edits;
        Match(String name, String key, Method method, int edits) { this.name = name; this.key = key; this.method = method; this.edits = edits; }
        boolean found() { return method != Method.NONE; }
        @Override public String toString() { return method + (name == null ? "" : ":" + name) + (method == Method.FUZZY ? "~" + edits : ""); }
    }

    private static final class Alias {
        final String name, key;
        final boolean branches;
        Alias(String name, String key, boolean branches) { this.name = name; this.key = key; this.branches = branches; }
    }

    private static MerchantCatalogIndex standard;
    private final Map<String, Alias> exact = new HashMap<>();
    private final List<Alias> aliases = new ArrayList<>();

    /** Rows of {name, "branches"|"whole", alias...}; an earlier row wins an alias collision, as in the legacy catalog. */
    MerchantCatalogIndex(List<String[]> rows) {
        for (String[] row : rows) for (int i = 2; i < row.length; i++) {
            String key = MerchantNormalizer.key(row[i]);
            if (key == null || key.isEmpty()) continue;
            Alias alias = new Alias(row[0], key, "branches".equals(row[1]));
            aliases.add(alias);
            exact.putIfAbsent(key, alias);
        }
    }

    static synchronized MerchantCatalogIndex standard() {
        if (standard == null) standard = new MerchantCatalogIndex(MerchantClassifier.catalog());
        return standard;
    }

    Match lookup(String surface) { return lookupKey(MerchantNormalizer.key(surface)); }

    Match lookupKey(String key) {
        if (key == null || key.isEmpty()) return new Match(null, key, Method.NONE, 0);
        Alias direct = exact.get(key);
        if (direct != null) return new Match(direct.name, key, Method.EXACT, 0);
        // The longest known alias owns a branch suffix ("uber eats maadi" is Uber Eats, not Uber).
        Alias branch = null;
        for (Alias alias : aliases)
            if (alias.branches && alias.key.length() >= ParsingPolicy.BRANCH_PREFIX_MIN_ALIAS_CHARS &&
                    key.startsWith(alias.key + " ") && (branch == null || alias.key.length() > branch.key.length())) branch = alias;
        if (branch != null) return new Match(branch.name, key, Method.BRANCH_PREFIX, 0);
        return fuzzy(key);
    }

    /** One edit at most, same first letter, no digits, long keys only, and a single candidate merchant. */
    private Match fuzzy(String key) {
        if (key.length() < ParsingPolicy.FUZZY_MIN_KEY_CHARS || key.matches(".*\\p{N}.*")) return new Match(null, key, Method.NONE, 0);
        Set<String> names = new LinkedHashSet<>();
        int best = Integer.MAX_VALUE;
        for (Alias alias : aliases) {
            if (alias.key.length() < ParsingPolicy.FUZZY_MIN_KEY_CHARS || alias.key.charAt(0) != key.charAt(0) ||
                    Math.abs(alias.key.length() - key.length()) > ParsingPolicy.FUZZY_MAX_EDITS) continue;
            int edits = edits(key, alias.key);
            if (edits > ParsingPolicy.FUZZY_MAX_EDITS) continue;
            if (edits < best) { best = edits; names.clear(); }
            if (edits == best) names.add(alias.name);
        }
        return names.size() == 1 ? new Match(names.iterator().next(), key, Method.FUZZY, best) : new Match(null, key, Method.NONE, 0);
    }

    /** Optimal string alignment distance: insertions, deletions, substitutions and adjacent transpositions. */
    static int edits(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
        return d[a.length()][b.length()];
    }
}
