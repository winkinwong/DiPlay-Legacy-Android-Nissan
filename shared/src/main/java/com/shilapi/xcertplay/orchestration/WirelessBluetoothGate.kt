package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.bluetooth.CarPlayBluetooth

/** How the wireless Bluetooth gate ruled on starting wireless CarPlay. */
internal sealed interface WirelessBluetoothGate {
    /** The hybrid adapter is on; wireless CarPlay can start. */
    data object Proceed : WirelessBluetoothGate

    /**
     * Bluetooth reports off, but a vendor stack can keep it on outside
     * the Android framework and the driver already entered the iPhone
     * address manually, so discovery is not required to start.
     */
    data object ProceedWithManualAddress : WirelessBluetoothGate

    data class Fail(val message: String) : WirelessBluetoothGate
}

/** Bound for the single enable() attempt before the gate gives up. */
private const val WIRELESS_BT_ENABLE_WAIT_MILLIS = 3000L

/**
 * Decides whether wireless CarPlay may start on this head unit.
 *
 * Issue #138: the gate consults the hybrid adapter (vendor Bluetooth
 * service + Android framework), not `BluetoothAdapter.getDefaultAdapter()`
 * alone — on some head units (the Nissan MCU family, ECARX) Bluetooth
 * only works through the vendor service while the framework adapter
 * reports null or disabled, and a raw-adapter check would refuse to
 * start wireless on a head unit that can actually run it.
 *
 * Issue #165: an adapter that starts off gets one enable() attempt
 * with a bounded wait, so head units without a Bluetooth switch in
 * Android settings can still start wireless.
 *
 * Issue #157: a vendor stack can keep Bluetooth on outside the Android
 * framework; when the driver already entered the iPhone address
 * manually, discovery is not needed, so the gate proceeds with a
 * warning instead of failing.
 */
internal fun wirelessBluetoothGate(
    hybrid: CarPlayBluetooth,
    savedPhoneAddress: String?,
    nowMillis: () -> Long,
    sleepMillis: (Long) -> Unit,
): WirelessBluetoothGate {
    if (!hybrid.isSupported()) {
        return WirelessBluetoothGate.Fail(
            "No Bluetooth adapter on this head unit; wireless CarPlay requires Bluetooth " +
                "for iPhone discovery. Use wired USB instead.",
        )
    }
    if (!hybrid.isEnabled()) {
        runCatching { hybrid.enable() }
        val enableDeadline = nowMillis() + WIRELESS_BT_ENABLE_WAIT_MILLIS
        while (!hybrid.isEnabled() && nowMillis() < enableDeadline) {
            sleepMillis(200L)
        }
    }
    if (!hybrid.isEnabled()) {
        if (savedPhoneAddress.isNullOrBlank()) {
            return WirelessBluetoothGate.Fail(
                "Bluetooth is turned off on this head unit; wireless CarPlay requires Bluetooth " +
                    "for iPhone discovery. Turn on Bluetooth in the head unit system settings " +
                    "and retry, or use wired USB instead.",
            )
        }
        return WirelessBluetoothGate.ProceedWithManualAddress
    }
    return WirelessBluetoothGate.Proceed
}
