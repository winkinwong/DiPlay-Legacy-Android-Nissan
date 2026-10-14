package com.shilapi.xcertplay.media

import java.util.concurrent.atomic.AtomicLong

/** Diagnostic link between the touch uplink and the next video frame, read by [VideoStats]. */
internal object TouchLatencyProbe {
    @Volatile private var pendingTouchNs = 0L
    private val maximumSendNs = AtomicLong(0L)

    val maxSendNs: Long get() = maximumSendNs.get()

    fun onTouchSent(sentAtNs: Long, sendDurationNs: Long) {
        if (pendingTouchNs == 0L) pendingTouchNs = sentAtNs
        // Sent on the touch worker, read/reset by the video stats thread.
        maximumSendNs.accumulateAndGet(sendDurationNs, Math::max)
    }

    /** Returns touch-to-frame latency for the first frame after a touch, or -1. */
    fun onFrame(nowNs: Long): Long {
        val touch = pendingTouchNs
        if (touch == 0L) return -1
        pendingTouchNs = 0L
        return nowNs - touch
    }

    /** Clears the largest observed send duration after each stats window has been reported. */
    fun resetMaxSendNs() {
        maximumSendNs.set(0L)
    }
}
