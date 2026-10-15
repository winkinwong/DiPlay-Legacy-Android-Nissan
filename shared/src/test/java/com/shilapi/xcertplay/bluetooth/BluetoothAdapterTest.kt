package com.shilapi.xcertplay.bluetooth

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BluetoothAdapterTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun testCarPlayBluetoothDeviceProperties() {
        val device = CarPlayBluetoothDevice("iPhone 15 Pro", "AA:BB:CC:DD:EE:FF", true)
        assertEquals("iPhone 15 Pro", device.name)
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertTrue(device.isConnected)

        val disconnected = CarPlayBluetoothDevice("iPhone 12", "11:22:33:44:55:66")
        assertFalse(disconnected.isConnected)
    }

    @Test
    fun testUuidConversionRFCAndGUID() {
        val uuidStr = "00000000-deca-fade-deca-deafdecacafe"
        val rfcBytes = HsaeRfcommStream.uuidToBytes(uuidStr)
        assertEquals(16, rfcBytes.size)
        // Standard RFC big-endian order for 00000000-deca-fade-deca-deafdecacafe
        assertEquals(0x00.toByte(), rfcBytes[0])
        assertEquals(0x00.toByte(), rfcBytes[1])
        assertEquals(0x00.toByte(), rfcBytes[2])
        assertEquals(0x00.toByte(), rfcBytes[3])
        assertEquals(0xde.toByte(), rfcBytes[4])
        assertEquals(0xca.toByte(), rfcBytes[5])
        assertEquals(0xfa.toByte(), rfcBytes[6])
        assertEquals(0xde.toByte(), rfcBytes[7])

        val guidBytes = HsaeRfcommStream.uuidToGuidBytes(uuidStr)
        assertEquals(16, guidBytes.size)
        // GUID little-endian: first 4 bytes reversed, next 2 bytes reversed, next 2 bytes reversed
        // First 4 bytes 00 00 00 00 reversed -> 00 00 00 00
        assertEquals(0x00.toByte(), guidBytes[0])
        assertEquals(0x00.toByte(), guidBytes[1])
        assertEquals(0x00.toByte(), guidBytes[2])
        assertEquals(0x00.toByte(), guidBytes[3])
        // Next 2 bytes: deca (de, ca) reversed -> (ca, de)
        assertEquals(0xca.toByte(), guidBytes[4])
        assertEquals(0xde.toByte(), guidBytes[5])
        // Next 2 bytes: fade (fa, de) reversed -> (de, fa)
        assertEquals(0xde.toByte(), guidBytes[6])
        assertEquals(0xfa.toByte(), guidBytes[7])
        // Last 8 bytes unchanged
        assertEquals(0xde.toByte(), guidBytes[8])
        assertEquals(0xca.toByte(), guidBytes[9])
        assertEquals(0xde.toByte(), guidBytes[10])
        assertEquals(0xaf.toByte(), guidBytes[11])
        assertEquals(0xde.toByte(), guidBytes[12])
        assertEquals(0xca.toByte(), guidBytes[13])
        assertEquals(0xca.toByte(), guidBytes[14])
        assertEquals(0xfe.toByte(), guidBytes[15])
    }

    @Test
    fun testHybridAdapterDelegationWhenHsaeNotSupported() {
        val hsae = HsaeBluetoothAdapter(context)
        val anw = AnwPhoneLinkBluetoothAdapter(context)
        val android = AndroidBluetoothAdapter(context)
        val hybrid = HybridBluetoothAdapter(hsae, anw, android)

        // On non-Nissan system or test JVM, HSAE package
        // com.hsae.bluetoothservice and the AnwPhoneLink
        // service do not exist
        assertFalse(hsae.isSupported())
        assertFalse(anw.isSupported())
        assertEquals("AndroidBluetoothAdapter", hybrid.getAdapterName())
    }

    @Test
    fun hybridChainsTheVendorAdapterBeforeAndroid() {
        val hsae = FakeCarPlayBluetooth()
        val anw = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            name = "AnwPhoneLink",
            paired = listOf(CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", true)),
            connected = CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", true),
            localMac = "AA:BB:CC:DD:EE:01",
        )
        val android = FakeCarPlayBluetooth(supported = true, enabled = true, name = "AndroidBluetoothAdapter")
        val hybrid = HybridBluetoothAdapter(hsae, anw, android)

        assertTrue(hybrid.isSupported())
        assertTrue(hybrid.isEnabled())
        hybrid.enable()
        assertEquals(1, anw.enableCalls)
        assertEquals(1, android.enableCalls)
        assertEquals("Hybrid(AnwPhoneLink+Android)", hybrid.getAdapterName())
        assertEquals(1, hybrid.getPairedDevices().size)
        assertEquals("AA:BB:CC:DD:EE:FF", hybrid.getConnectedDevice()?.address)
        assertEquals("AA:BB:CC:DD:EE:01", hybrid.localAddress(context))
        hybrid.openRfcommStream("AA:BB:CC:DD:EE:FF", "uuid")
        assertEquals(1, anw.rfcommStreams)
        assertEquals(0, android.rfcommStreams)
    }

    @Test
    fun hybridFallsBackToAndroidWhenVendorRfcommFails() {
        val hsae = FakeCarPlayBluetooth()
        val anw = FakeCarPlayBluetooth(supported = true, enabled = true, rfcommFails = true)
        val android = FakeCarPlayBluetooth(supported = true, enabled = true, name = "AndroidBluetoothAdapter")
        val hybrid = HybridBluetoothAdapter(hsae, anw, android)

        hybrid.openRfcommStream("AA:BB:CC:DD:EE:FF", "uuid")
        assertEquals(1, anw.rfcommStreams)
        assertEquals(1, android.rfcommStreams)
    }

    @Test
    fun hybridReportsUnsupportedWhenNoStackExists() {
        val hybrid = HybridBluetoothAdapter(
            FakeCarPlayBluetooth(),
            FakeCarPlayBluetooth(),
            FakeCarPlayBluetooth(),
        )
        assertFalse(hybrid.isSupported())
        assertFalse(hybrid.isEnabled())
    }
}
