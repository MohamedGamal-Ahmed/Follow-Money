package com.mohamedgamal.masareef;

/** Source identity and text for one live capture; captureAt retains the caller's existing time semantics. */
final class CaptureRequest {
    final String sourceKey, label, body, channel;
    final long captureAt, legacyReceivedAt;

    private CaptureRequest(String sourceKey, String label, String body, long captureAt,
                           String channel, long legacyReceivedAt) {
        this.sourceKey = sourceKey;
        this.label = label;
        this.body = body;
        this.captureAt = captureAt;
        this.channel = channel;
        this.legacyReceivedAt = legacyReceivedAt;
    }

    static CaptureRequest sms(String sender, String body, long when) {
        return new CaptureRequest(sender, sender, body, when, "SMS", when);
    }

    static CaptureRequest inboxSms(String sender, String body, long receivedAt, long sentAt, boolean receiptKeySafe) {
        long when = CaptureIdentity.smsTimestamp(receivedAt, sentAt);
        return new CaptureRequest(sender, sender, body, when, "SMS", receiptKeySafe ? receivedAt : when);
    }

    static CaptureRequest notification(String packageName, String appLabel, String body, long when) {
        return new CaptureRequest(packageName, appLabel, body, when, "إشعار: " + appLabel, when);
    }
}
