package com.shilapi.xcertplay.airplay

/**
 * When the last AirPlay media payload arrived, so a link can be judged alive from its own media
 * instead of from iAP2 chatter alone.
 *
 * Issue #134: on the Nissan NTDA3 the iPhone sends no iAP2 control frame for long stretches while
 * video keeps streaming. The wired control loop reset the link on iAP2 silence alone after 90 s, and
 * that tore down a working session: the log shows a normal video window
 * (`19:44:40 Receive: video packets=77 bytes=2389082`) a second before
 * `19:44:41 peer silent for 90006ms; resetting link`, then a minute of missing video.
 *
 * Every screen, audio and buffered-audio payload reports here; nothing in this object holds payload
 * bytes.
 */
internal object AirPlayMediaActivity {
    /** Judge an idle control channel dead once no media payload arrived within this window. */
    const val RECENT_WINDOW_MILLIS = 15_000L

    private const val NANOS_PER_MILLIS = 1_000_000L

    @Volatile
    private var lastNanos: Long = 0L

    /** Monotonic clock; a test seam, as in [StreamReceiveStats]. */
    @Volatile
    internal var clock: () -> Long = System::nanoTime

    fun onPayload() {
        lastNanos = clock()
    }

    /** True while a payload arrived within [windowMillis]; false until any payload ever arrived. */
    fun recentFor(windowMillis: Long): Boolean {
        val last = lastNanos
        return last != 0L && clock() - last < windowMillis * NANOS_PER_MILLIS
    }

    /** True while media arrived inside [RECENT_WINDOW_MILLIS]. */
    fun recent(): Boolean = recentFor(RECENT_WINDOW_MILLIS)

    /** Test seam: forget the recorded payload. */
    internal fun resetForTest() {
        lastNanos = 0L
    }
}
