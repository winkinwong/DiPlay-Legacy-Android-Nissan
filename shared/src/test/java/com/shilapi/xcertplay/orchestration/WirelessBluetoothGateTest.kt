package com.shilapi.xcertplay.orchestration

import android.content.Context
import com.shilapi.xcertplay.bluetooth.CarPlayBluetooth
import com.shilapi.xcertplay.bluetooth.CarPlayBluetoothDevice
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

private class FakeHybridAdapter(
    private val supported: Boolean,
    private var powered: Boolean,
    private val enableSucceeds: Boolean = true,
) : CarPlayBluetooth {
    var enableCalls = 0
        private set

    override fun isSupported(): Boolean = supported

    override fun isEnabled(): Boolean = powered

    override fun enable(): Boolean {
        enableCalls += 1
        if (enableSucceeds) {
            powered = true
        }
        return enableSucceeds
    }

    override fun getAdapterName(): String = "fake"

    override fun getPairedDevices(): List<CarPlayBluetoothDevice> = emptyList()

    override fun getConnectedDevice(): CarPlayBluetoothDevice? = null

    override fun localAddress(context: Context): String? = null

    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream =
        throw UnsupportedOperationException("not under test")
}

class WirelessBluetoothGateTest {
    private val clock = AtomicLong(0)
    private val sleeps = mutableListOf<Long>()
    private val nowMillis = { clock.get() }
    private val sleepMillis: (Long) -> Unit = { millis ->
        sleeps.add(millis)
        clock.addAndGet(millis)
    }

    @Test
    fun failsWhenNoAdapterIsSupported() {
        val gate = wirelessBluetoothGate(
            hybrid = FakeHybridAdapter(supported = false, powered = false),
            savedPhoneAddress = null,
            nowMillis = nowMillis,
            sleepMillis = sleepMillis,
        )
        assertTrue(gate is WirelessBluetoothGate.Fail)
        assertEquals(
            "No Bluetooth adapter on this head unit; wireless CarPlay requires Bluetooth " +
                "for iPhone discovery. Use wired USB instead.",
            (gate as WirelessBluetoothGate.Fail).message,
        )
    }

    @Test
    fun proceedsWhenHybridAdapterIsPowered() {
        val gate = wirelessBluetoothGate(
            hybrid = FakeHybridAdapter(supported = true, powered = true),
            savedPhoneAddress = null,
            nowMillis = nowMillis,
            sleepMillis = sleepMillis,
        )
        assertEquals(WirelessBluetoothGate.Proceed, gate)
        assertEquals(0, sleeps.size)
    }

    @Test
    fun enablesAnAdapterThatStartsOff() {
        val adapter = FakeHybridAdapter(supported = true, powered = false)
        val gate = wirelessBluetoothGate(
            hybrid = adapter,
            savedPhoneAddress = null,
            nowMillis = nowMillis,
            sleepMillis = sleepMillis,
        )
        assertEquals(WirelessBluetoothGate.Proceed, gate)
        assertEquals(1, adapter.enableCalls)
        assertEquals(0, sleeps.size)
    }

    @Test
    fun failsWhenBluetoothStaysOffWithoutSavedAddress() {
        val adapter = FakeHybridAdapter(supported = true, powered = false, enableSucceeds = false)
        val gate = wirelessBluetoothGate(
            hybrid = adapter,
            savedPhoneAddress = null,
            nowMillis = nowMillis,
            sleepMillis = sleepMillis,
        )
        assertTrue(gate is WirelessBluetoothGate.Fail)
        assertEquals(
            "Bluetooth is turned off on this head unit; wireless CarPlay requires Bluetooth " +
                "for iPhone discovery. Turn on Bluetooth in the head unit system settings " +
                "and retry, or use wired USB instead.",
            (gate as WirelessBluetoothGate.Fail).message,
        )
        assertEquals(1, adapter.enableCalls)
        // The enable wait is bounded: fifteen 200ms sleeps reach the 3000ms deadline.
        assertEquals(15, sleeps.size)
    }

    @Test
    fun proceedsWithManualAddressWhenBluetoothStaysOff() {
        val adapter = FakeHybridAdapter(supported = true, powered = false, enableSucceeds = false)
        val gate = wirelessBluetoothGate(
            hybrid = adapter,
            savedPhoneAddress = "AA:BB:CC:DD:EE:FF",
            nowMillis = nowMillis,
            sleepMillis = sleepMillis,
        )
        assertEquals(WirelessBluetoothGate.ProceedWithManualAddress, gate)
        assertEquals(1, adapter.enableCalls)
        assertEquals(15, sleeps.size)
    }
}
