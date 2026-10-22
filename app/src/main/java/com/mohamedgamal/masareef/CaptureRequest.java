package com.mohamedgamal.masareef;

/** Source identity and text for one live capture; captureAt retains the caller's existing time semantics. */
final class CaptureRequest {
    final String sourceKey, label, body, channel;
    final long captureAt, legacyReceivedAt;
    /** Undigested input for the shadow pipeline only; legacy capture reads body. */
    final MessageNormalizer.Raw raw;

    private CaptureRequest(String sourceKey, String label, String body, long captureAt,
                           String channel, long legacyReceivedAt, MessageNormalizer.Raw raw) {
        this.sourceKey = sourceKey;
        this.label = label;
        this.body = body;
        this.captureAt = captureAt;
        this.channel = channel;
        this.legacyReceivedAt = legacyReceivedAt;
        this.raw = raw;
    }

    static CaptureRequest sms(String sender, String body, long when) {
        return new CaptureRequest(sender, sender, body, when, "SMS", when, MessageNormalizer.Raw.sms(sender, body));
    }

    static CaptureRequest inboxSms(String sender, String body, long receivedAt, long sentAt, boolean receiptKeySafe) {
        long when = CaptureIdentity.smsTimestamp(receivedAt, sentAt);
        return new CaptureRequest(sender, sender, body, when, "SMS", receiptKeySafe ? receivedAt : when,
                MessageNormalizer.Raw.sms(sender, body));
    }

    static CaptureRequest notification(String packageName, String appLabel, String body, long when) {
        return notification(packageName, appLabel, body, when, null);
    }

    /** raw carries the six separate extras; without it the shadow stage sees the legacy-assembled body as text. */
    static CaptureRequest notification(String packageName, String appLabel, String body, long when, MessageNormalizer.Raw raw) {
        return new CaptureRequest(packageName, appLabel, body, when, "إشعار: " + appLabel, when,
                raw != null ? raw : MessageNormalizer.Raw.notification(packageName, 0, null, body, null, null, null, null));
    }
}
