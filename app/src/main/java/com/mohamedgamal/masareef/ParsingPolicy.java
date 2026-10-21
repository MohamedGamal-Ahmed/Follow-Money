package com.mohamedgamal.masareef;

/**
 * Every threshold, limit and word list of the replacement parsing pipeline, in one place.
 * Stages read these values; they never declare their own. Changing a value changes POLICY_VERSION.
 * The pipeline runs in shadow mode only: the legacy ExpenseParser path remains the sole ledger authority.
 */
final class ParsingPolicy {
    static final String POLICY_VERSION = "2026-10-21.1";
    static final String NORMALIZER_VERSION = "normalization-1";
    /** Approved corpus v2 contract (tools/fixtures/parser-corpus-v2-draft.tsv, UTF-8/LF SHA-256 prefix). */
    static final String CORPUS_CONTRACT = "v2:cc75ee3c";

    // Shadow mode.
    static final boolean SHADOW_ENABLED = true;
    /** In-memory observations kept for the current process; nothing is persisted or uploaded. */
    static final int SHADOW_BUFFER_SIZE = 50;

    // Message normalization.
    /** Longer inputs are cut for the shadow stage only; the legacy path keeps the full text. */
    static final int MAX_INPUT_CHARS = 8000;
    /** Notification extras in assembly order; the same order the corpus uses to reconstruct a notification. */
    static final String[] NOTIFICATION_FIELDS = {"android.title", "android.text", "android.bigText",
            "android.textLines", "android.subText", "android.summaryText"};
    /** Android Notification.FLAG_GROUP_SUMMARY; a summary is flagged, never dropped, by normalization. */
    static final int FLAG_GROUP_SUMMARY = 0x200;

    // Merchant normalization. Upper-case processor tokens, matched only as a whole leading token.
    /** Removed before "*" when a merchant follows: KASHIER*BUTLERS, SQ *LUNA CAFE. */
    static final String[] STARRED_PROCESSORS = {"KASHIER", "PAYMOB", "FAWRY", "SQ", "PAYPAL", "GEIDEA", "STRIPE", "PAYSKY"};
    /** Removed before a space-separated merchant only: "Kashier Butlers". A lone token stays (PAYPAL). */
    static final String[] BARE_PROCESSORS = {"KASHIER", "PAYMOB"};
    /** Trailing country/city descriptors removed from the end of a merchant, never from its start (CAIRO BOOKS). */
    static final String[] TRAILING_LOCATIONS = {"EG", "EGY", "EGYPT", "CAIRO", "GIZA", "ALEXANDRIA", "مصر", "القاهرة", "الجيزة"};

    // Catalog lookup: exact, then branch prefix, then conservative fuzzy.
    /** A known alias accepts a following branch/location suffix only from this length. */
    static final int BRANCH_PREFIX_MIN_ALIAS_CHARS = 4;
    /** Fuzzy matching is never tried on shorter keys. */
    static final int FUZZY_MIN_KEY_CHARS = 6;
    /** Optimal-string-alignment (Damerau) edits allowed; one edit only. */
    static final int FUZZY_MAX_EDITS = 1;

    // Later stages: defined by the approved corpus, not yet used by any implemented stage.
    /** Identity-less identical messages within this window become possible-duplicate review items. */
    static final long DUPLICATE_CANDIDATE_WINDOW_MS = 60_000L;
    /** Same-source opposite Phone Banking legs within this window are possible internal transfers. */
    static final long INTERNAL_PAIRING_WINDOW_MS = 600_000L;
    /** A reversal offsets an original purchase with the same amount and merchant within this many days. */
    static final int EXPENSE_OFFSET_MATCH_DAYS = 14;

    private ParsingPolicy() {}
}
