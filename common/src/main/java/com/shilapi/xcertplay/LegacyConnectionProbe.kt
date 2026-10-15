package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import javax.net.ssl.SSLContext

/** Read-only diagnostic: never logs identities, network addresses, credentials or USB serials. */
internal object LegacyConnectionProbe {

    private const val ANW_PHONE_LINK_PACKAGE = "com.anwsdk.service"
    private const val ANW_PHONE_LINK_ACTION = "com.anwsdk.service.AnwPhoneLink"
    private const val HSAE_BLUETOOTH_SERVICE_PACKAGE = "com.hsae.bluetoothservice"

    fun report(context: Context): String = buildString {
        appendLine("Android API=${Build.VERSION.SDK_INT} release=${Build.VERSION.RELEASE}")
        appendLine(runCatching {
            val provider = org.conscrypt.Conscrypt.newProvider()
            val ssl = SSLContext.getInstance("TLS", provider).apply { init(null, null, null) }
            val engine = ssl.createSSLEngine()
            "Bundled Conscrypt provider=${provider.name} supported=${engine.supportedProtocols.joinToString()} enabled=${engine.enabledProtocols.joinToString()}"
        }.getOrElse { "Bundled Conscrypt unavailable=${it.javaClass.simpleName}: ${it.message?.take(80)}" })
        for (name in listOf("TLSv1.2", "TLS")) {
            appendLine(runCatching {
                val ssl = SSLContext.getInstance(name).apply { init(null, null, null) }
                val engine = ssl.createSSLEngine()
                "TLS context=$name provider=${ssl.provider.name} supported=${engine.supportedProtocols.joinToString()} enabled=${engine.enabledProtocols.joinToString()}"
            }.getOrElse { "TLS context=$name unavailable=${it.javaClass.simpleName}" })
        }
        appendLine(runCatching {
            val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            val descriptions = usb?.deviceList?.values?.map { device ->
                "vid=${device.vendorId} pid=${device.productId} class=${device.deviceClass} " +
                    "interfaces=${device.interfaceCount} permission=${usb.hasPermission(device)}"
            }.orEmpty()
            "USB Host feature=${context.packageManager.hasSystemFeature("android.hardware.usb.host")} service=${usb != null} devices=${descriptions.size}" +
                if (descriptions.isEmpty()) "" else " [${descriptions.joinToString(" | ")}]"
        }.getOrElse { "USB Host probe failed=${it.javaClass.simpleName}" })
        appendLine(runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            "WiFi service=${wifi != null} enabled=${wifi?.isWifiEnabled} p2pFeature=${context.packageManager.hasSystemFeature("android.hardware.wifi.direct")}"
        }.getOrElse { "WiFi probe failed=${it.javaClass.simpleName}" })
        appendLine(runCatching {
            @Suppress("DEPRECATION")
            val bluetooth = BluetoothAdapter.getDefaultAdapter()
            val hybrid = com.shilapi.xcertplay.bluetooth.CarPlayBluetoothManager.get(context)
            "Bluetooth adapter=${bluetooth != null} enabled=${bluetooth?.isEnabled} " +
                "hybridSupported=${hybrid.isSupported()} hybridEnabled=${hybrid.isEnabled()} " +
                "hybridAdapter=${hybrid.getAdapterName()}"
        }.getOrElse { "Bluetooth probe failed=${it.javaClass.simpleName}" })
        appendLine(runCatching { vendorBtServicesLine(context.packageManager) }
            .getOrElse { "Vendor BT service probe failed=${it.javaClass.simpleName}" })
    }

    /**
     * Vendor Bluetooth stack availability, as booleans only. The Nissan/HSAE
     * wireless CarPlay fallback rides the MCU's own Bluetooth firmware through
     * the vendor AIDL service (IAnwPhoneLink), which the framework-level
     * probe above cannot see: a head unit can report no framework adapter
     * yet still host the vendor service. Resolution count > 0 means the
     * AnwPhoneLink service resolves for binding; the HSAE line reports
     * package presence of the sibling HSAE Bluetooth service.
     */
    internal fun vendorBtServicesLine(packageManager: PackageManager): String {
        val anwResolved = runCatching {
            packageManager.queryIntentServices(
                Intent(ANW_PHONE_LINK_ACTION).setPackage(ANW_PHONE_LINK_PACKAGE),
                0,
            ).size
        }.getOrDefault(-1)
        return "Vendor BT services anwPhoneLinkResolved=$anwResolved " +
            "hsaeBluetoothService=${packagePresent(packageManager, HSAE_BLUETOOTH_SERVICE_PACKAGE)}"
    }

    private fun packagePresent(packageManager: PackageManager, name: String): Boolean =
        runCatching { packageManager.getPackageInfo(name, 0) != null }.getOrDefault(false)

    fun log(context: Context) {
        report(context).lineSequence().forEach { if (it.isNotBlank()) Log.i("DiPlayLegacyProbe", it) }
    }
}
