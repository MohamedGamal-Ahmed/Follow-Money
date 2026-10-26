package com.mohamedgamal.masareef;

/**
 * Owner, 24 Oct 2026: a flexible in-app update (a small card, the download runs while the app is used). Owner, 26 Oct
 * 2026: the card asks as soon as Play reports the update (no two-day wait); «بعدين» quiets the same version for three
 * days, and a downloaded update always asks for the restart.
 */
public final class UpdatePromptTest {
    private static int checks;
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private static final long DAY = 24L * 60 * 60 * 1000, NOW = 1_800_000_000_000L;

    private static UpdatePrompt.Card offer(Integer staleness, int snoozedVersion, long snoozedAt) {
        return UpdatePrompt.card(true, true, staleness, UpdatePrompt.Status.NONE, 13, snoozedVersion, snoozedAt, NOW);
    }

    public static void main(String[] args) {
        check(offer(0, 0, 0) == UpdatePrompt.Card.OFFER, "Offered the day Play reports it");
        check(offer(2, 0, 0) == UpdatePrompt.Card.OFFER, "…and later");
        check(offer(null, 0, 0) == UpdatePrompt.Card.OFFER, "Play has not dated it yet: still offered");
        check(offer(5, 13, NOW - DAY) == UpdatePrompt.Card.NONE, "«بعدين» quiets the same version");
        check(offer(5, 13, NOW - 3 * DAY) == UpdatePrompt.Card.OFFER, "…for three days");
        check(offer(5, 12, NOW - DAY) == UpdatePrompt.Card.OFFER, "A newer version than the one dismissed asks again");
        check(UpdatePrompt.card(true, false, 9, UpdatePrompt.Status.NONE, 13, 0, 0, NOW) == UpdatePrompt.Card.NONE, "Play does not allow a flexible update");
        check(UpdatePrompt.card(false, true, 9, UpdatePrompt.Status.NONE, 13, 0, 0, NOW) == UpdatePrompt.Card.NONE, "Nothing available");
        check(UpdatePrompt.card(false, false, null, UpdatePrompt.Status.DOWNLOADING, 13, 13, NOW, NOW) == UpdatePrompt.Card.DOWNLOADING, "A running download shows quietly, even after «بعدين»");
        check(UpdatePrompt.card(false, false, null, UpdatePrompt.Status.DOWNLOADED, 13, 13, NOW, NOW) == UpdatePrompt.Card.RESTART, "A downloaded update always asks for the restart");
        System.out.println("Update prompt checks passed: " + checks);
    }
}
