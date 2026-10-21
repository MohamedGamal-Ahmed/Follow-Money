package com.mohamedgamal.masareef;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/**
 * The debug screen's "re-scan all messages" on the installed app, for the owner's phones: runs ShadowRescan over the
 * whole inbox (no operation is recorded) and returns the same report the screen exports, which has no message text,
 * merchant or counterparty. Requires the explicit argument rescan_report=owner_phone and the app's own SMS permission.
 */
final class ShadowRescanAudit {
    private final Instrumentation runner;
    private final String consent;
    ShadowRescanAudit(Instrumentation runner, String consent) { this.runner = runner; this.consent = consent; }

    void run() {
        Bundle result = new Bundle(); int status = -1;
        Context target = runner.getTargetContext();
        try {
            if (!"owner_phone".equals(consent)) throw new SecurityException("rescan_report=owner_phone is required");
            if (target.checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new IllegalStateException("The app has no SMS permission; nothing was scanned");
            long before, after;
            try (ExpenseDb db = new ExpenseDb(target)) { before = db.changes(); }
            ShadowRescan.Result scan = ShadowRescan.run(target, 0, new java.util.concurrent.atomic.AtomicBoolean(), (done, total) -> { });
            ShadowReport.Summary summary;
            try (ExpenseDb db = new ExpenseDb(target)) {
                after = db.changes();
                summary = ParserDebugActivity.rescanSummary(target, db.getReadableDatabase());
            }
            if (before != after) throw new AssertionError("Ledger revision changed during the re-scan");
            result.putString("stream", "Shadow re-scan report (" + android.os.Build.MODEL + ", scanned " + scan.scanned + ", stored " + scan.stored +
                    ", ledger revision unchanged)\n" + ShadowReport.text(summary, "Follow Money shadow re-scan (" + android.os.Build.MODEL + ")", java.time.ZoneId.systemDefault()));
        } catch (Throwable failure) {
            status = 0; java.io.StringWriter trace = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream", "Shadow re-scan FAILED: " + trace);
        } finally {
            runner.finish(status, result);
        }
    }
}