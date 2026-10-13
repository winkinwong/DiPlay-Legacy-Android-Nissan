package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #134: the wired control loop's iAP2 peer-silence reset has to be able to tell
 * "control quiet, AirPlay streaming" apart from "link dead". On the NTDA3 the iPhone sent no iAP2
 * frame for 90 s while video kept arriving, and the reset tore down that working session.
 */
class AirPlayMediaActivityTest {
    private var nowNanos = 1_000_000_000L

    private fun installClock() {
        AirPlayMediaActivity.resetForTest()
        nowNanos = 1_000_000_000L
        AirPlayMediaActivity.clock = { nowNanos }
    }

    @Test fun noMediaDataEverIsNotRecent() {
        installClock()
        assertFalse(AirPlayMediaActivity.recentFor(15_000))
    }

    @Test fun dataThatJustArrivedIsRecent() {
        installClock()
        AirPlayMediaActivity.onPayload()
        assertTrue(AirPlayMediaActivity.recentFor(15_000))
    }

    @Test fun mediaThatStoppedIsNoLongerRecentOnceTheWindowPasses() {
        installClock()
        AirPlayMediaActivity.onPayload()
        nowNanos += 15_000L * 1_000_000L
        assertFalse(
            "an older-than-window payload must look dead so a dead link still resets",
            AirPlayMediaActivity.recentFor(15_000),
        )
    }

    @Test fun theWindowIsMeasuredFromTheNewestPayloadNotTheOldest() {
        installClock()
        AirPlayMediaActivity.onPayload()
        nowNanos += 10_000L * 1_000_000L
        AirPlayMediaActivity.onPayload()
        nowNanos += 10_000L * 1_000_000L
        assertTrue("still streaming inside the window", AirPlayMediaActivity.recentFor(15_000))
    }
}
