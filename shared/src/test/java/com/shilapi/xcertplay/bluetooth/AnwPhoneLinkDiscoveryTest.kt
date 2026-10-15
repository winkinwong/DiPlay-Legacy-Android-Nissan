package com.shilapi.xcertplay.bluetooth

import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import com.shilapi.xcertplay.bluetooth.AnwPhoneLinkDiscovery.PairedDevice
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Wire contract of the vendor AnwPhoneLink paired-list query, extracted
 * from the vendor service's transaction codes: transaction 16 takes the
 * capacities of the arrays the caller prepared (count, names, addresses,
 * class-of-device) and answers with a status plus those four arrays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnwPhoneLinkDiscoveryTest {
    private val discovery = AnwPhoneLinkDiscovery(RuntimeEnvironment.getApplication())

    @Test fun pairedListQueryUsesTheDocumentedTransactionAndCapacities() {
        val binder = recordingBinder {
            writeNoException()
            writeInt(0)
            writeIntArray(intArrayOf(0))
            writeStringArray(arrayOfNulls(16))
            writeStringArray(arrayOfNulls(16))
            writeIntArray(intArrayOf(16))
        }

        discovery.pairedDevices(binder)

        assertEquals(16, binder.recordedCode)
        assertEquals(listOf(1, 16, 16, 16), binder.recordedCapacities)
    }

    @Test fun pairedListParsesNamesAddressesAndClassOfDevice() {
        val binder = recordingBinder {
            writeNoException()
            writeInt(0)
            writeIntArray(intArrayOf(2))
            writeStringArray(arrayOf("iPhone", "Other phone", *arrayOfNulls(14)))
            writeStringArray(
                arrayOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", *arrayOfNulls(14)),
            )
            writeIntArray(intArrayOf(0x5A02, 0x5A03, *IntArray(14)))
        }

        val devices = discovery.pairedDevices(binder)

        assertEquals(
            listOf(
                PairedDevice("iPhone", "AA:BB:CC:DD:EE:01", 0x5A02),
                PairedDevice("Other phone", "AA:BB:CC:DD:EE:02", 0x5A03),
            ),
            devices,
        )
    }

    @Test fun pairedListIsEmptyWhenTheServiceReportsFailure() {
        val binder = recordingBinder {
            writeNoException()
            writeInt(-1)
        }

        assertEquals(emptyList<PairedDevice>(), discovery.pairedDevices(binder))
    }

    @Test fun pairedListClampsTheCountToTheArraySizes() {
        val binder = recordingBinder {
            writeNoException()
            writeInt(0)
            writeIntArray(intArrayOf(99))
            writeStringArray(arrayOf("A", "B", "C"))
            writeStringArray(arrayOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:03"))
            writeIntArray(intArrayOf(1, 2, 3))
        }

        val devices = discovery.pairedDevices(binder)

        assertEquals(3, devices.size)
        assertEquals("A", devices[0].name)
        assertEquals("C", devices[2].name)
    }

    @Test fun pairedListDefaultsNullEntriesToEmptyStrings() {
        val binder = recordingBinder {
            writeNoException()
            writeInt(0)
            writeIntArray(intArrayOf(1))
            writeStringArray(arrayOfNulls<String>(16))
            writeStringArray(arrayOfNulls<String>(16))
            writeIntArray(IntArray(16))
        }

        assertEquals(
            listOf(PairedDevice("", "", 0)),
            discovery.pairedDevices(binder),
        )
    }

    @Test fun pairedListRejectsAnUnexpectedBinderDescriptor() {
        val binder = recordingBinder(descriptor = "com.example.WrongInterface") {
            writeNoException()
            writeInt(0)
            writeIntArray(intArrayOf(0))
            writeStringArray(arrayOfNulls(16))
            writeStringArray(arrayOfNulls(16))
            writeIntArray(intArrayOf(16))
        }

        assertThrows(IOException::class.java) { discovery.pairedDevices(binder) }
    }

    private fun recordingBinder(
        descriptor: String = AnwPhoneLinkDiscovery.DESCRIPTOR,
        replyWriter: Parcel.() -> Unit,
    ) = RecordingBinder(descriptor, replyWriter)

    /** Answers a paired-list query; records the request it received. */
    private class RecordingBinder(
        private val descriptor: String,
        private val replyWriter: Parcel.() -> Unit,
    ) : IBinder {
        var recordedCode = -1
        var recordedCapacities: List<Int> = emptyList()

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            recordedCode = code
            // The binder driver hands the service a request parcel at
            // position 0 and returns a reply parcel at position 0.
            data.setDataPosition(0)
            data.enforceInterface(AnwPhoneLinkDiscovery.DESCRIPTOR)
            recordedCapacities = List(4) { data.readInt() }
            if (reply != null) {
                replyWriter(reply)
                reply.setDataPosition(0)
            }
            return true
        }

        override fun pingBinder(): Boolean = true
        override fun isBinderAlive(): Boolean = true
        override fun queryLocalInterface(descriptor: String): IInterface? = null
        override fun getInterfaceDescriptor(): String = descriptor
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {}
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
        override fun dump(fd: java.io.FileDescriptor, args: Array<out String>?) {}
        override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<out String>?) {}
    }
}
