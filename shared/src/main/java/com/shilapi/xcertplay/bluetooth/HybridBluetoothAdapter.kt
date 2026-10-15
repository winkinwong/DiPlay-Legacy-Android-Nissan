package com.shilapi.xcertplay.bluetooth

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream

/**
 * CarPlayBluetooth that tries every stack this head unit may have:
 * the HSAE Bluetooth service, the vendor AnwPhoneLink service and
 * the Android framework, in that order. Head units report at most
 * one of the vendor services, but the chain keeps the framework as
 * the last resort so a unit that gained a framework adapter still
 * works.
 */
class HybridBluetoothAdapter(
    private val hsae: CarPlayBluetooth,
    private val anw: CarPlayBluetooth,
    private val android: CarPlayBluetooth,
) : CarPlayBluetooth {

    companion object {
        private const val TAG = "HybridBluetoothAdapter"
    }

    override fun isSupported(): Boolean =
        hsae.isSupported() || anw.isSupported() || android.isSupported()

    override fun isEnabled(): Boolean {
        if (hsae.isSupported() && hsae.isEnabled()) return true
        if (anw.isSupported() && anw.isEnabled()) return true
        return android.isEnabled()
    }

    override fun enable(): Boolean {
        var ok = false
        if (hsae.isSupported()) {
            ok = hsae.enable()
        }
        if (anw.isSupported()) {
            ok = anw.enable() || ok
        }
        return android.enable() || ok
    }

    override fun getAdapterName(): String = when {
        hsae.isSupported() -> "Hybrid(HSAE+Android)"
        anw.isSupported() -> "Hybrid(AnwPhoneLink+Android)"
        else -> android.getAdapterName()
    }

    override fun getPairedDevices(): List<CarPlayBluetoothDevice> {
        if (hsae.isSupported()) {
            val hsaeList = hsae.getPairedDevices()
            if (hsaeList.isNotEmpty()) return hsaeList
        }
        if (anw.isSupported()) {
            val anwList = anw.getPairedDevices()
            if (anwList.isNotEmpty()) return anwList
        }
        return android.getPairedDevices()
    }

    override fun getConnectedDevice(): CarPlayBluetoothDevice? {
        if (hsae.isSupported()) {
            val hsaeConn = hsae.getConnectedDevice()
            if (hsaeConn != null) return hsaeConn
        }
        if (anw.isSupported()) {
            val anwConn = anw.getConnectedDevice()
            if (anwConn != null) return anwConn
        }
        return android.getConnectedDevice()
    }

    override fun localAddress(context: Context): String? {
        if (hsae.isSupported()) {
            val hsaeAddr = hsae.localAddress(context)
            if (!hsaeAddr.isNullOrBlank()) return hsaeAddr
        }
        if (anw.isSupported()) {
            val anwAddr = anw.localAddress(context)
            if (!anwAddr.isNullOrBlank()) return anwAddr
        }
        return android.localAddress(context)
    }

    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream {
        if (hsae.isSupported()) {
            val result = runCatching { hsae.openRfcommStream(deviceAddress, uuid) }
            val stream = result.getOrNull()
            if (stream != null) return stream
            val hsaeError = result.exceptionOrNull()!!
            Log.w(TAG, "HSAE RFCOMM stream failed, trying AnwPhoneLink", hsaeError)
            return openVendorOrAndroid(deviceAddress, uuid, hsaeError)
        }
        if (anw.isSupported()) {
            val result = runCatching { anw.openRfcommStream(deviceAddress, uuid) }
            val stream = result.getOrNull()
            if (stream != null) return stream
            val anwError = result.exceptionOrNull()!!
            Log.w(TAG, "AnwPhoneLink RFCOMM stream failed, trying Android", anwError)
            if (android.isSupported() && android.isEnabled()) {
                return android.openRfcommStream(deviceAddress, uuid)
            }
            throw anwError
        }
        return android.openRfcommStream(deviceAddress, uuid)
    }

    private fun openVendorOrAndroid(
        deviceAddress: String,
        uuid: String,
        fallbackError: Throwable,
    ): BlockingDuplexByteStream {
        if (anw.isSupported()) {
            val result = runCatching { anw.openRfcommStream(deviceAddress, uuid) }
            val stream = result.getOrNull()
            if (stream != null) return stream
            Log.w(TAG, "AnwPhoneLink RFCOMM stream failed, trying Android", result.exceptionOrNull()!!)
        }
        if (android.isSupported() && android.isEnabled()) {
            return android.openRfcommStream(deviceAddress, uuid)
        }
        throw fallbackError
    }
}
