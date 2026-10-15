package com.shilapi.xcertplay.bluetooth

import android.content.Context
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import java.io.IOException

/**
 * Test double for the [CarPlayBluetooth] chains. Answers from the
 * construction arguments and counts the calls the cascade tests
 * assert on.
 */
class FakeCarPlayBluetooth(
    private val supported: Boolean = false,
    private val enabled: Boolean = false,
    private val name: String = "fake",
    private val paired: List<CarPlayBluetoothDevice> = emptyList(),
    private val connected: CarPlayBluetoothDevice? = null,
    private val localMac: String? = null,
    private val rfcommFails: Boolean = false,
) : CarPlayBluetooth {
    var pairedQueryCount = 0
        private set

    var enableCalls = 0
        private set

    var rfcommStreams = 0
        private set

    override fun isSupported(): Boolean = supported

    override fun isEnabled(): Boolean = enabled

    override fun enable(): Boolean {
        enableCalls += 1
        return true
    }

    override fun getAdapterName(): String = name

    override fun getPairedDevices(): List<CarPlayBluetoothDevice> {
        pairedQueryCount += 1
        return paired
    }

    override fun getConnectedDevice(): CarPlayBluetoothDevice? = connected

    override fun localAddress(context: Context): String? = localMac

    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream {
        rfcommStreams += 1
        if (rfcommFails) {
            throw IOException("vendor RFCOMM unavailable")
        }
        return FakeBlockingDuplexByteStream
    }
}

/** Minimal [BlockingDuplexByteStream] double for the hybrid cascade tests. */
private object FakeBlockingDuplexByteStream : BlockingDuplexByteStream {
    override fun send(data: ByteArray) = Unit

    override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? = null

    override fun close() = Unit
}
