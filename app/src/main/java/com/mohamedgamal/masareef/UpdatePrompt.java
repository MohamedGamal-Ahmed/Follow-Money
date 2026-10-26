package com.mohamedgamal.masareef;

/**
 * When the home screen shows the in-app update card. Pure logic; InAppUpdate feeds it what Google Play reports.
 * Play's automatic update gets two days first, «بعدين» quiets the same version for three days, and an update that
 * finished downloading always asks for the restart (it otherwise waits on the phone, using storage).
 */
final class UpdatePrompt {
    private UpdatePrompt() {}

    static final int MIN_STALENESS_DAYS = 2;
    static final long SNOOZE_MS = 3L * 24 * 60 * 60 * 1000;

    enum Status { NONE, DOWNLOADING, DOWNLOADED }
    enum Card { NONE, OFFER, DOWNLOADING, RESTART }

    static Card card(boolean available, boolean flexibleAllowed, Integer stalenessDays, Status status,
                     int availableVersion, int snoozedVersion, long snoozedAt, long now) {
        if (status == Status.DOWNLOADED) return Card.RESTART;
        if (status == Status.DOWNLOADING) return Card.DOWNLOADING;
        if (!available || !flexibleAllowed || stalenessDays == null || stalenessDays < MIN_STALENESS_DAYS) return Card.NONE;
        if (snoozedVersion == availableVersion && now - snoozedAt < SNOOZE_MS) return Card.NONE;
        return Card.OFFER;
    }
}
