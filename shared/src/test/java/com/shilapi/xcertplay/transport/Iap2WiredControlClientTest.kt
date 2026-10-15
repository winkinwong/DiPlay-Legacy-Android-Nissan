package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2WiredControlClientTest {
    /**
     * Issue #134: on the NTDA3 the iPhone sent no iAP2 frame for 90 s while AirPlay kept streaming
     * (`19:44:40 Receive: video packets=77 ...` immediately before `19:44:41 peer silent for 90006ms`).
     * The link reset may only fire when AirPlay is quiet too, so a working session is not torn down.
     */
    @Test
    fun peerSilenceResetsTheLinkOnlyWhenAirPlayMediaIsAlsoQuiet() {
        assertTrue(
            "no liveness information keeps the previous behaviour",
            shouldResetLinkOnPeerSilence(peerSilent = true, airPlayDataRecent = null),
        )
        assertFalse(
            "video still arriving means the session is alive and must be kept",
            shouldResetLinkOnPeerSilence(peerSilent = true, airPlayDataRecent = true),
        )
        assertTrue(
            "silent iAP2 and silent AirPlay is a dead link and still resets",
            shouldResetLinkOnPeerSilence(peerSilent = true, airPlayDataRecent = false),
        )
        assertFalse(
            "a live iAP2 link is never reset",
            shouldResetLinkOnPeerSilence(peerSilent = false, airPlayDataRecent = false),
        )
    }

    @Test
    fun carPlayStartSessionNestsAddressesInsideWiredAttributes() {
        val frame = Iap2WiredControlClient.carPlayStartSession(
            Iap2WiredCarPlayEndpoint(
                ipv6Addresses = listOf("fe80::2"),
                airPlayPort = 7000,
                deviceIdentifier = "02:00:00:00:00:02",
                publicKey = "abcd",
                sourceVersion = "1.0",
            ),
        )

        assertEquals(0x4301, frame.messageId)
        val outer = Iap2BodyReader.of(frame).list()
        val wired = outer.single { it.id == 0 }
        val addresses = Iap2ParameterList.parse(wired.payload).asList()
        assertEquals(1, addresses.size)
        assertEquals(0, addresses.single().id)
        assertArrayEquals("fe80::2\u0000".encodeToByteArray(), addresses.single().payload)
    }

    /**
     * t32.2 field report 2026-10-15: after exiting and re-entering CarPlay the
     * iPhone can still dial the previous session's TUN endpoint, so a StartSession
     * the iPhone missed gets exactly one re-advertisement (which also makes the
     * controller re-send the unsolicited neighbor advertisement) before the link
     * is declared dead and the driver has to replug the cable.
     */
    @Test
    fun missedAirPlayTcpReAdvertisesStartSessionOnceThenFails() {
        assertEquals(
            "before the first-TCP window the loop keeps waiting",
            WiredFirstTcpDecision.KeepWaiting,
            wiredFirstTcpDecision(startSessionsSent = 1, airPlayTcpArrived = false, elapsedSinceStartSessionMs = 29_999, retryAlreadySent = false),
        )
        assertEquals(
            "a missed StartSession is re-advertised once",
            WiredFirstTcpDecision.ReAdvertiseStartSession,
            wiredFirstTcpDecision(startSessionsSent = 1, airPlayTcpArrived = false, elapsedSinceStartSessionMs = 30_000, retryAlreadySent = false),
        )
        assertEquals(
            "the re-advertisement window elapsed with no AirPlay TCP: the link is dead",
            WiredFirstTcpDecision.FailNoAirPlayTcp,
            wiredFirstTcpDecision(startSessionsSent = 1, airPlayTcpArrived = false, elapsedSinceStartSessionMs = 30_000, retryAlreadySent = true),
        )
        assertEquals(
            "the iPhone dialed during the retry window: the session recovered",
            WiredFirstTcpDecision.KeepWaiting,
            wiredFirstTcpDecision(startSessionsSent = 1, airPlayTcpArrived = true, elapsedSinceStartSessionMs = 45_000, retryAlreadySent = true),
        )
        assertEquals(
            "the watchdog never arms before the first StartSession",
            WiredFirstTcpDecision.KeepWaiting,
            wiredFirstTcpDecision(startSessionsSent = 0, airPlayTcpArrived = false, elapsedSinceStartSessionMs = 60_000, retryAlreadySent = false),
        )
    }
}
