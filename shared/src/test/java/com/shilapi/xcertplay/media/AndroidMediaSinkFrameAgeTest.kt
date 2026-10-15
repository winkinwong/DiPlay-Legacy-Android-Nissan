package com.shilapi.xcertplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMediaSinkFrameAgeTest {
    private val now = 1_000_000_000_000L

    @Test fun freshFrameIsNeverRecovered() {
        assertFalse(shouldRecoverOnFrameAge(100_000_000L, now, now))
        assertFalse(shouldRecoverOnFrameAge(MAX_FRAME_AGE_NS, now, now))
    }

    @Test fun staleFrameWithNoDecoderRunningRecovers() {
        // decoderReadyNanos = 0: nothing was (re)configured, so a stale frame
        // means a genuinely stuck queue — recover as before.
        assertTrue(shouldRecoverOnFrameAge(MAX_FRAME_AGE_NS + 1, now, 0L))
    }

    @Test fun staleFrameInsideStartupGraceIsFed() {
        // Frames queued while the decoder was configuring are stale by the
        // time the worker polls them. Within the grace window they must be
        // fed (presentation time still paces them), not trigger a keyframe
        // round-trip (the 1.8 s black gap seen at stream start on NTDA3).
        val decoderReady = now - (STARTUP_GRACE_NS - 1_000_000L)
        assertFalse(shouldRecoverOnFrameAge(10_000_000_000L, now, decoderReady))
    }

    @Test fun staleFrameAfterStartupGraceRecovers() {
        // Past the grace window the decoder has had time to keep up; a stale
        // frame means a real backlog — recover.
        val decoderReady = now - (STARTUP_GRACE_NS + 1_000_000L)
        assertTrue(shouldRecoverOnFrameAge(MAX_FRAME_AGE_NS + 1, now, decoderReady))
    }

    @Test fun exactlyAtGraceBoundaryRecovers() {
        val decoderReady = now - STARTUP_GRACE_NS
        assertTrue(shouldRecoverOnFrameAge(MAX_FRAME_AGE_NS + 1, now, decoderReady))
    }
}
