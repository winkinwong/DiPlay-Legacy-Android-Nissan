package com.shilapi.xcertplay.bluetooth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Read-only discovery of the phones paired with the vehicle's Bluetooth
 * module through the vendor AnwPhoneLink service.
 *
 * Some head units (this Nissan MCU family) hide the framework
 * [android.bluetooth.BluetoothAdapter], so DiPlay cannot ask Android for
 * the bonded devices. The vendor service still tracks them: a single
 * binder transaction (code 16) answers with the paired names, addresses
 * and class-of-device values. The wire contract below matches the
 * transaction codes of [HsaeRfcommStream]'s service (SPP 62-69,
 * socket 222-233); the paired-list query itself is not part of the
 * generated AIDL interface, so it is issued with a raw
 * [IBinder.transact] call.
 */
class AnwPhoneLinkDiscovery(private val context: Context) {

    /** One MCU-paired phone, as the vendor service reports it. */
    data class PairedDevice(
        val name: String,
        val address: String,
        val classOfDevice: Int,
    )

    /**
     * Binds the vendor AnwPhoneLink service and lists the paired phones.
     * Runs on a worker thread: the bind and the query block until the
     * service answers or [timeoutMs] elapses.
     */
    fun discoverPairedDevices(timeoutMs: Long = BIND_TIMEOUT_MS): List<PairedDevice> {
        val latch = CountDownLatch(1)
        val binderHolder = arrayOfNulls<IBinder>(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                binderHolder[0] = binder
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binderHolder[0] = null
            }
        }

        val intent = Intent(SERVICE_ACTION).apply { setPackage(SERVICE_PACKAGE) }
        val bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.w(TAG, "AnwPhoneLink bind failed", e)
            false
        }
        if (!bound) throw IOException("Could not bind to AnwPhoneLink ($SERVICE_ACTION)")

        return try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "No AnwPhoneLink Binder callback within ${timeoutMs}ms; unbinding")
                throw IOException("Timed out waiting for AnwPhoneLink binding")
            }
            val binder = binderHolder[0]
                ?: throw IOException("AnwPhoneLink interface is null after connect")
            pairedDevices(binder)
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    /**
     * Runs the paired-list query against a bound AnwPhoneLink binder.
     *
     * Request: the interface token plus the capacities of the arrays the
     * caller prepared (count, names, addresses, class-of-device). Reply:
     * a status (negative on failure), a single-element array holding the
     * actual count, then the three device arrays padded to the requested
     * capacities.
     */
    internal fun pairedDevices(binder: IBinder): List<PairedDevice> {
        val descriptor = try {
            binder.interfaceDescriptor
        } catch (e: RemoteException) {
            null
        }
        if (descriptor != DESCRIPTOR) {
            throw IOException("Unexpected AnwPhoneLink Binder descriptor: $descriptor")
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(COUNT_ARRAY_CAPACITY)
            data.writeInt(DEVICE_ARRAY_CAPACITY)
            data.writeInt(DEVICE_ARRAY_CAPACITY)
            data.writeInt(DEVICE_ARRAY_CAPACITY)
            val ok = binder.transact(TX_GET_PAIRED_LIST, data, reply, 0)
            if (!ok) {
                throw RemoteException(
                    "AnwPhoneLink transaction $TX_GET_PAIRED_LIST is unsupported",
                )
            }
            reply.readException()
            val status = reply.readInt()
            if (status < 0) {
                Log.w(TAG, "AnwPhoneLink paired list query failed: $status")
                return emptyList()
            }
            val counts = reply.createIntArray() ?: intArrayOf()
            val names = reply.createStringArray() ?: emptyArray()
            val addresses = reply.createStringArray() ?: emptyArray()
            val classOfDevices = reply.createIntArray() ?: intArrayOf()
            val count = (counts.firstOrNull() ?: 0).coerceIn(0, names.size)
            (0 until count).map { index ->
                PairedDevice(
                    name = names.getOrNull(index) ?: "",
                    address = addresses.getOrNull(index) ?: "",
                    classOfDevice = classOfDevices.getOrNull(index) ?: 0,
                )
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        private const val TAG = "AnwPhoneLinkDiscovery"
        internal const val DESCRIPTOR = "com.anwsdk.service.IAnwPhoneLink"
        private const val SERVICE_ACTION = "com.anwsdk.service.AnwPhoneLink"
        private const val SERVICE_PACKAGE = "com.anwsdk.service"
        private const val TX_GET_PAIRED_LIST = 16
        private const val COUNT_ARRAY_CAPACITY = 1
        private const val DEVICE_ARRAY_CAPACITY = 16
        private const val BIND_TIMEOUT_MS = 5_000L
    }
}
