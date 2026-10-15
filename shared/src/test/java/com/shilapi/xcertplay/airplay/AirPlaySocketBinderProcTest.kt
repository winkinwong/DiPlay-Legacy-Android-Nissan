package com.shilapi.xcertplay.airplay

import java.net.InetAddress
import java.net.Inet6Address
import org.junit.Assert.assertEquals
import org.junit.Test

class AirPlaySocketBinderProcTest {
    private fun tunAddress(): Inet6Address =
        InetAddress.getByName("fe80::2") as Inet6Address

    @Test fun resolvesTheTunInterfaceIndexFromProcNetIfInet6() {
        val lines = listOf(
            "Ifindex  IPv6Address  PrefixLen  Scope  Flags  Iface",
            "1        00000000000000000000000000000001  80  00000000  lo",
            "3        fe800000000000000000000000000002  64  20000000  tun0",
        )
        assertEquals(3, AirPlaySocketBinder.procIfIndex(lines, tunAddress()))
    }

    @Test fun returnsNullWhenTheKernelHasNoSuchAddress() {
        val lines = listOf(
            "1  00000000000000000000000000000001  80  00000000  lo",
            "2  fe80000000000000000000000000abcd  64  20000000  wlan0",
        )
        assertEquals(null, AirPlaySocketBinder.procIfIndex(lines, tunAddress()))
    }

    @Test fun matchesUppercaseHexAndSkipsMalformedRows() {
        val lines = listOf(
            "garbage line",
            "",
            "7  FE800000000000000000000000000002  40  20000000  tun1",
        )
        assertEquals(7, AirPlaySocketBinder.procIfIndex(lines, tunAddress()))
    }

    @Test fun rejectsNonPositiveIndices() {
        val lines = listOf("0  fe800000000000000000000000000002  40  20000000  tun0")
        assertEquals(null, AirPlaySocketBinder.procIfIndex(lines, tunAddress()))
    }

    @Test fun scopedLinkLocalUsesTheProcResolvedScope() {
        // Sanity of the public entry point on a host JVM: /proc/net/if_inet6
        // is absent (or has no fe80::2) on the test machine, so the resolver
        // must fall through to the Java paths and return the input unchanged
        // when nothing resolves — never a bogus scope.
        val input = tunAddress()
        val scoped = AirPlaySocketBinder.scopedLinkLocal(input)
        // Host JVMs have no interface owning fe80::2; the address must come
        // back unchanged rather than scoped to some unrelated interface.
        assertEquals(input.hostAddress, scoped.hostAddress)
    }
}
