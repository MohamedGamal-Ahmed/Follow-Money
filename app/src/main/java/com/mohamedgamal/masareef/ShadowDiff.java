package com.mohamedgamal.masareef;

/**
 * One legacy decision against one shadow decision, for the debug screen and the re-scan report. Pure; no text.
 * Legacy "recorded" means it wrote a ledger row; the new pipeline "records" on AUTO or REVIEW. Two records agree
 * only on the same amount and direction (a legacy own-account transfer has no in/out, so any direction fits it).
 */
final class ShadowDiff {


    static boolean newRecords(String decision) { return "AUTO".equals(decision) || "REVIEW".equals(decision); }



    private ShadowDiff() {}
}
