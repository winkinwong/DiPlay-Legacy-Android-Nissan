package com.shilapi.xcertplay.bluetooth

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import java.io.IOException

/**
 * CarPlayBluetooth backed by the vendor AnwPhoneLink service.
 *
 * Some head units (this Nissan MCU family) hide the framework
 * BluetoothAdapter and ship no HSAE Bluetooth service, but
 * still run the vendor AnwPhoneLink service: it answers the
 * paired-phone list (AnwPhoneLinkDiscovery) and opens SPP and
 * socket channels (HsaeRfcommStream) through the MCU's own
 * Bluetooth firmware. The car system manages that firmware's
 * power and pairing, so this adapter cannot enable Bluetooth or
 * discover new phones — it reports the stack as enabled whenever
 * the service resolves, and pairing is limited to the phones the
 * car already paired.
 */
class AnwPhoneLinkBluetoothAdapter(
    private val context: Context,
    private val serviceResolver: (PackageManager) -> Boolean =
        Companion::resolvesAnwPhoneLink,
) : CarPlayBluetooth {

    override fun isSupported(): Boolean = serviceResolver(context.packageManager)

    override fun isEnabled(): Boolean = isSupported()

    override fun enable(): Boolean = isSupported()

    override fun getAdapterName(): String = "AnwPhoneLink"

    override fun getPairedDevices(): List<CarPlayBluetoothDevice> {
        if (!isSupported()) return emptyList()
        return runCatching {
            AnwPhoneLinkDiscovery(context).discoverPairedDevices().map { phone ->
                CarPlayBluetoothDevice(
                    name = phone.name.ifBlank { "iPhone" },
                    address = phone.address,
                    isConnected = false,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** The vendor paired list does not report which phone is connected. */
    override fun getConnectedDevice(): CarPlayBluetoothDevice? = null

    override fun localAddress(context: Context): String? {
        val secureAddr = runCatching {
            Settings.Secure.getString(context.contentResolver, BLUETOOTH_ADDRESS_SETTING)
        }.getOrNull()
        if (secureAddr != null && isValidMac(secureAddr)) {
            return secureAddr
        }
        val globalAddr = runCatching {
            Settings.Global.getString(context.contentResolver, BLUETOOTH_ADDRESS_SETTING)
        }.getOrNull()
        if (globalAddr != null && isValidMac(globalAddr)) {
            return globalAddr
        }
        return null
    }

    override fun openRfcommStream(deviceAddress: String, uuid: String): BlockingDuplexByteStream =
        HsaeRfcommStream.connect(context, deviceAddress, uuid)

    companion object {
        private const val SERVICE_ACTION = "com.anwsdk.service.AnwPhoneLink"
        private const val SERVICE_PACKAGE = "com.anwsdk.service"
        private const val BLUETOOTH_ADDRESS_SETTING = "bluetooth_address"

        /** True when the vendor AnwPhoneLink service resolves for binding. */
        internal fun resolvesAnwPhoneLink(packageManager: PackageManager): Boolean =
            runCatching {
                packageManager.queryIntentServices(
                    Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE),
                    0,
                ).isNotEmpty()
            }.getOrDefault(false)
    }
}

private fun isValidMac(mac: String): Boolean =
    Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(mac) &&
        !mac.startsWith("02:00:00:00:00:") &&
        mac != "00:00:00:00:00:00"

/**
 * Picks the iPhone for the wireless iAP2 bootstrap on head units
 * whose Bluetooth lives only in a vendor service: the framework
 * adapter is null there, so the framework bonded-device selection
 * cannot run. The manually entered address wins — the driver named
 * the iPhone explicitly — then the vendor-reported connected phone,
 * then a single paired iPhone, mirroring the framework path's order
 * and its multiple-iPhone failure.
 */
internal fun selectVendorWirelessTarget(
    hybrid: CarPlayBluetooth,
    savedPhoneAddress: String?,
): CarPlayBluetoothDevice? {
    val saved = savedPhoneAddress?.takeIf { android.bluetooth.BluetoothAdapter.checkBluetoothAddress(it) }
    if (saved != null) {
        return CarPlayBluetoothDevice(name = "iPhone", address = saved, isConnected = false)
    }
    hybrid.getConnectedDevice()?.let { return it }
    val iPhones = hybrid.getPairedDevices().filter { device ->
        device.name.contains("iPhone", ignoreCase = true)
    }
    if (iPhones.size == 1) return iPhones.single()
    if (iPhones.size > 1) {
        throw IOException(
            "Multiple paired iPhones found and none is currently connected; " +
                "connect one iPhone and retry",
        )
    }
    return null
}
