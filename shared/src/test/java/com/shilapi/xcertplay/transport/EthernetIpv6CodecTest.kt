package com.shilapi.xcertplay.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EthernetIpv6CodecTest {
    @Test
    fun `maps IPv6 multicast destination to Ethernet multicast`() {
        val packet = ByteArray(40)
        packet[0] = 0x60
        val destination = byteArrayOf(
            0xff.toByte(), 0x02, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 1, 0xff.toByte(), 0, 0, 2,
        )
        destination.copyInto(packet, 24)

        assertArrayEquals(
            byteArrayOf(0x33, 0x33, 0xff.toByte(), 0, 0, 2),
            EthernetIpv6Codec.multicastDestinationMac(packet),
        )
    }

    @Test
    fun `does not map IPv6 unicast destination`() {
        val packet = ByteArray(40)
        packet[0] = 0x60
        packet[24] = 0xfe.toByte()
        packet[25] = 0x80.toByte()

        assertNull(EthernetIpv6Codec.multicastDestinationMac(packet))
    }

    @Test
    fun `rejects truncated or non IPv6 packets`() {
        assertNull(EthernetIpv6Codec.multicastDestinationMac(ByteArray(39)))
        assertNull(EthernetIpv6Codec.multicastDestinationMac(ByteArray(40)))
    }

    @Test
    fun `adds target link layer option to tun neighbor advertisement`() {
        val packet = ByteArray(64)
        packet[0] = 0x60
        packet[5] = 24
        packet[6] = 58
        byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
            .copyInto(packet, 8)
        byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3)
            .copyInto(packet, 24)
        packet[40] = 136.toByte()
        packet[44] = 0x60
        val mac = byteArrayOf(0x02, 0, 0, 0, 0, 2)

        val result = EthernetIpv6Codec.addNeighborAdvertisementTargetMac(packet, mac)

        assertEquals(72, result.size)
        assertEquals(32, ((result[4].toInt() and 0xff) shl 8) or (result[5].toInt() and 0xff))
        assertEquals(2, result[64].toInt() and 0xff)
        assertEquals(1, result[65].toInt() and 0xff)
        assertArrayEquals(mac, result.copyOfRange(66, 72))
        assertEquals(0xffff, ipv6IcmpChecksumSum(result))
    }

    @Test
    fun `detects matching neighbor solicitation`() {
        val targetIpv6 = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)
        val nsPacket = ByteArray(64)
        nsPacket[0] = 0x60
        nsPacket[5] = 24
        nsPacket[6] = 58 // ICMPv6
        nsPacket[40] = 135.toByte() // Neighbor Solicitation
        targetIpv6.copyInto(nsPacket, 48)

        assertTrue(EthernetIpv6Codec.isNeighborSolicitationFor(nsPacket, 0, 64, targetIpv6))

        val otherTarget = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3)
        assertFalse(EthernetIpv6Codec.isNeighborSolicitationFor(nsPacket, 0, 64, otherTarget))
        assertFalse(EthernetIpv6Codec.isNeighborSolicitationFor(nsPacket, 0, 30, targetIpv6))
    }

    @Test
    fun `builds valid neighbor advertisement with correct checksum`() {
        val hostMac = byteArrayOf(0x02, 0, 0, 0, 0, 1)
        val peerMac = byteArrayOf(0x02, 0, 0, 0, 0, 2)
        val hostIp = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val peerIp = byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)

        val frame = EthernetIpv6Codec.buildNeighborAdvertisement(hostMac, peerMac, hostIp, peerIp, solicited = true)

        assertEquals(86, frame.size) // 14 Ethernet + 40 IPv6 + 32 ICMPv6
        val parsed = EthernetIpv6Codec.parseIpv6(frame)
        org.junit.Assert.assertNotNull(parsed)
        val ipv6 = parsed!!.ipv6
        assertEquals(72, ipv6.size)
        assertEquals(136, ipv6[40].toInt() and 0xff)
        assertEquals(0x60, ipv6[44].toInt() and 0xff) // S=1, O=1
        assertEquals(2, ipv6[64].toInt() and 0xff) // Option type 2
        assertEquals(1, ipv6[65].toInt() and 0xff) // Option length 1
        assertArrayEquals(hostMac, ipv6.copyOfRange(66, 72))
        assertEquals(0xffff, ipv6IcmpChecksumSum(ipv6))
    }

    private fun ipv6IcmpChecksumSum(packet: ByteArray): Int {
        val payloadBytes = ((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff)
        var sum = 0L
        fun words(start: Int, bytes: Int) {
            var offset = start
            val end = start + bytes
            while (offset + 1 < end) {
                sum += (((packet[offset].toInt() and 0xff) shl 8) or
                    (packet[offset + 1].toInt() and 0xff)).toLong()
                offset += 2
            }
        }
        words(8, 32)
        sum += payloadBytes
        sum += 58
        words(40, payloadBytes)
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.toInt()
    }
}
