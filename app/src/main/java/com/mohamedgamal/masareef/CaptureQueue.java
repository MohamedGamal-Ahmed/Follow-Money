package com.mohamedgamal.masareef;

/**
 * One worker runs every capture (SMS parts and payment notifications) in order, so two sources never
 * write the same ledger row at once while the broadcast and listener threads stay free.
 */
final class CaptureQueue {
    private CaptureQueue() { }

    static void submit(Runnable work) { AppResources.write(work); }
}
