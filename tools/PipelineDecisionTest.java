package com.mohamedgamal.masareef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The pure part of step 7's ledger write: which digits attach a new-pipeline row to the user's source, and when the
 * other party of a movement is one of the user's own accounts (owner test, 22 Oct 2026: an HSBC debit «to 7392» and a
 * Banque Misr credit «account_last4=7392» were written with no source and counted as income and spending).
 */
public final class PipelineDecisionTest {
    private static int checks;
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    private static void same(Object actual, Object expected, String why) { check(Objects.equals(actual, expected), why + ": <" + actual + "> != <" + expected + ">"); }

    private static Map<String, String> values(String... pairs) {
        Map<String, String> v = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) if (pairs[i + 1] != null) v.put(pairs[i], pairs[i + 1]);
        return v;
    }

    public static void main(String[] args) {
        // A card in the message names the paying source; the account only when there is no card (legacy last4 order).
        same(PipelineDecision.sourceLast4(values("card_last4", "4636", "account_last4", "0001")), "4636", "card before account");
        same(PipelineDecision.sourceLast4(values("account_last4", "0001")), "0001", "HSBC IPN outward: account 0001 is the debited source");
        same(PipelineDecision.sourceLast4(values("account_last4", "7392")), "7392", "Banque Misr IPN inward: account 7392 is the receiving source");
        same(PipelineDecision.sourceLast4(values("merchant", "Kashier Butlers")), null, "no digits, no source guess");

        // The other party written only as an account/card tail: candidate for one of the user's own sources.
        same(PipelineDecision.counterpartyTail("7392"), "7392", "bare tail (HSBC «IPN outward transfer to 7392»)");
        same(PipelineDecision.counterpartyTail("****7392"), "7392", "masked tail");
        same(PipelineDecision.counterpartyTail("xxxx0001"), "0001", "x-masked tail");
        same(PipelineDecision.counterpartyTail("MOHAMED GAMAL ABDELAZIZ"), null, "a name is a person");
        same(PipelineDecision.counterpartyTail("mo.gamalhsbc@instapay"), null, "an InstaPay address is matched by own_handles, not digits");
        same(PipelineDecision.counterpartyTail("01012345678"), null, "a phone number is a person");
        same(PipelineDecision.counterpartyTail(null), null, "no counterparty");
        System.out.println("Pipeline decision checks passed: " + checks);
    }
}
