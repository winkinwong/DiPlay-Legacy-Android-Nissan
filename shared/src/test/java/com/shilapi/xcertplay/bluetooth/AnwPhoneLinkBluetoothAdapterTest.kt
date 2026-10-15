package com.shilapi.xcertplay.bluetooth

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AnwPhoneLinkBluetoothAdapterTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun unsupportedWhenVendorServiceIsMissing() {
        val adapter = AnwPhoneLinkBluetoothAdapter(context)
        assertFalse(adapter.isSupported())
        assertFalse(adapter.isEnabled())
        assertFalse(adapter.enable())
        assertEquals("AnwPhoneLink", adapter.getAdapterName())
        assertEquals(emptyList<CarPlayBluetoothDevice>(), adapter.getPairedDevices())
        assertNull(adapter.getConnectedDevice())
    }

    @Test
    fun supportedWhenVendorServiceResolves() {
        val adapter = AnwPhoneLinkBluetoothAdapter(context) { _ -> true }
        assertTrue(adapter.isSupported())
        // The MCU firmware manages the vendor stack: the service
        // being alive means Bluetooth is on, and software cannot
        // power it, so enable() only reports the state.
        assertTrue(adapter.isEnabled())
        assertTrue(adapter.enable())
    }

    @Test
    fun localAddressReadsTheBluetoothAddressSetting() {
        val adapter = AnwPhoneLinkBluetoothAdapter(context) { _ -> true }
        // Robolectric exposes no bluetooth_address setting, so no
        // local address is reported here.
        assertNull(adapter.localAddress(context))
    }

    @Test
    fun targetPrefersTheManuallyEnteredAddress() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("Pixel", "11:22:33:44:55:66", false),
            ),
        )
        val target = selectVendorWirelessTarget(hybrid, "AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", target?.address)
        // The saved address stands on its own: no paired-list
        // query is needed to honour it.
        assertEquals(0, hybrid.pairedQueryCount)
    }

    @Test
    fun targetIgnoresAnInvalidSavedAddress() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", false),
            ),
        )
        val target = selectVendorWirelessTarget(hybrid, "not-a-mac")
        assertEquals("AA:BB:CC:DD:EE:FF", target?.address)
        assertEquals(1, hybrid.pairedQueryCount)
    }

    @Test
    fun targetUsesTheConnectedDeviceBeforeThePairedList() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", false),
                CarPlayBluetoothDevice("Pixel", "11:22:33:44:55:66", false),
            ),
            connected = CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", true),
        )
        val target = selectVendorWirelessTarget(hybrid, null)
        assertEquals("AA:BB:CC:DD:EE:FF", target?.address)
        assertEquals(0, hybrid.pairedQueryCount)
    }

    @Test
    fun targetUsesTheSinglePairedIphone() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", false),
                CarPlayBluetoothDevice("Pixel", "11:22:33:44:55:66", false),
            ),
        )
        val target = selectVendorWirelessTarget(hybrid, null)
        assertEquals("AA:BB:CC:DD:EE:FF", target?.address)
        assertEquals(1, hybrid.pairedQueryCount)
    }

    @Test
    fun targetFailsWhenSeveralIpHonesArePaired() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("iPhone 12", "AA:BB:CC:DD:EE:FF", false),
                CarPlayBluetoothDevice("iPhone 13", "11:22:33:44:55:66", false),
            ),
        )
        try {
            selectVendorWirelessTarget(hybrid, null)
            throw AssertionError("expected multiple-iPhone failure")
        } catch (expected: IOException) {
            assertTrue(expected.message!!.contains("Multiple paired iPhones"))
        }
    }

    @Test
    fun targetIsNullWhenNoIphoneIsKnown() {
        val hybrid = FakeCarPlayBluetooth(
            supported = true,
            enabled = true,
            paired = listOf(
                CarPlayBluetoothDevice("Pixel", "11:22:33:44:55:66", false),
            ),
        )
        assertNull(selectVendorWirelessTarget(hybrid, null))
    }
}
