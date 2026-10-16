package com.mohamedgamal.masareef;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One worker runs every capture (SMS parts and payment notifications) in order, so two sources never
 * write the same ledger row at once while the broadcast and listener threads stay free.
 */
final class CaptureQueue {
    private CaptureQueue() { }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "capture-worker");
        thread.setDaemon(true);
        return thread;
    });

    static void submit(Runnable work) { WORKER.execute(work); }
}
