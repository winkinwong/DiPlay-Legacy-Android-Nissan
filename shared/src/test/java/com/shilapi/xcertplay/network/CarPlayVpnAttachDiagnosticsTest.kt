package com.shilapi.xcertplay.network

import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayVpnAttachDiagnosticsTest {
    @Test fun preservesExceptionTypeNestedCauseAndSource() {
        val failure = IllegalArgumentException("Invalid argument", IllegalStateException("netd rejected"))
        val text = CarPlayVpnService.attachFailure(failure)
        assertTrue(text, text.contains("IllegalArgumentException: Invalid argument"))
        assertTrue(text, text.contains("IllegalStateException: netd rejected"))
        assertTrue(text, text.contains(" at "))
    }

    @Test fun wedgedNetdFwmarkFailureAddsRecoveryHintAndPreservesCause() {
        val root = IllegalStateException("interface fwmark rule add tun0: No such device")
        val original = IllegalArgumentException("VPN failed", root)
        val enriched = CarPlayVpnService.withWedgedVpnHint(original)
        assertTrue(enriched.message.orEmpty().contains("Reboot the head unit"))
        assertTrue(enriched.message.orEmpty().contains("Original error: VPN failed"))
        assertTrue(enriched.cause === original)
        val other = IllegalArgumentException("different failure")
        assertTrue(CarPlayVpnService.withWedgedVpnHint(other) === other)
    }
}