package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections
import java.util.Random

/** Family-aware ephemeral socket binding for legacy Android head-unit kernels. */
internal object AirPlaySocketBinder {
    fun wildcard(reference: InetAddress?): InetAddress {
        if (reference is Inet4Address) return InetAddress.getByName("0.0.0.0")
        val v6 = reference as? Inet6Address
        if (v6 != null && v6.isLinkLocalAddress) {
            // Vendor kernels (e.g. rk3188/Android 4.4) reject binding the bare "::" wildcard
            // when the link only carries link-local addresses:
            // "Can't bind to a link-local address without a scope id".
            // Bind the scoped wildcard on the same link instead; it keeps wildcard
            // semantics while giving the kernel the scope id it demands.
            scopedWildcard(v6)?.let { return it }
        }
        return InetAddress.getByName("::")
    }

    /**
     * Attach the link's interface scope id to a link-local IPv6 address so binds and sends
     * work on vendor kernels that require a scope id. Returns the input unchanged when it is
     * not a scopeless link-local address or when no interface scope can be resolved.
     */
    internal fun scopedLinkLocal(address: InetAddress): InetAddress {
        val v6 = address as? Inet6Address ?: return address
        if (!v6.isLinkLocalAddress || v6.scopeId != 0) return address
        val scope = linkScopeOf(v6)
        if (scope == 0) return address
        return runCatching { Inet6Address.getByAddress(null, v6.address, scope) }.getOrDefault(address)
    }

    /** Scoped "::" wildcard for the link that owns [reference], or null when unresolvable. */
    private fun scopedWildcard(reference: Inet6Address): InetAddress? {
        val scope = linkScopeOf(reference)
        if (scope == 0) return null
        return runCatching { Inet6Address.getByAddress(null, ByteArray(16), scope) }.getOrNull()
    }

    /** Interface index owning a link-local address; 0 when it cannot be determined. */
    private fun linkScopeOf(address: Inet6Address): Int {
        if (address.scopeId != 0) return address.scopeId
        // Kernel truth first: /proc/net/if_inet6 maps every kernel IPv6
        // address to its interface index. Java's NetworkInterface view can
        // stay stale after a VPN TUN is destroyed and recreated between
        // reconnect attempts, which leaves the AirPlay listener scopeless
        // and inbound SYNs silently dropped (the issue #133 mechanism).
        // The proc file is read straight from the kernel, immune to that cache.
        runCatching { File(PROC_INET6).readLines() }
            .getOrNull()
            ?.let { procIfIndex(it, address) }
            ?.let { return it }
        runCatching {
            NetworkInterface.getByInetAddress(address)?.index?.takeIf { it > 0 }
        }.getOrNull()?.let { return it }
        // Some vendor ROMs don't resolve getByInetAddress for VPN/tun addresses; scan manually.
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .firstOrNull { ni -> Collections.list(ni.inetAddresses).any { it == address } }
                ?.index?.takeIf { it > 0 }
        }.getOrNull()?.let { return it }
        return 0
    }

    /**
     * Parses `/proc/net/if_inet6` rows ("Ifindex IPv6Address PrefixLen Scope
     * Flags Iface") for the interface index owning [address]; null when the
     * kernel has no such address. Pure so the parsing is unit-testable
     * without a device.
     */
    internal fun procIfIndex(lines: List<String>, address: Inet6Address): Int? {
        val target = address.address.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        for (line in lines) {
            val columns = line.trim().split(WHITESPACE)
            if (columns.size < 2) continue
            if (columns[1].equals(target, ignoreCase = true)) {
                columns[0].toIntOrNull()?.let { if (it > 0) return it }
            }
        }
        return null
    }

    fun datagram(reference: InetAddress?, stage: String): DatagramSocket =
        bindAcrossWildcards(reference, stage, { DatagramSocket(null) }) { socket, endpoint ->
            socket.reuseAddress = false
            socket.bind(endpoint)
        }

    fun server(reference: InetAddress?, stage: String): ServerSocket =
        bindAcrossWildcards(reference, stage, { ServerSocket() }) { socket, endpoint ->
            socket.reuseAddress = false
            socket.bind(endpoint)
        }

    fun serverAt(address: InetAddress, stage: String): ServerSocket = bindEphemeral(
        stage, address, { ServerSocket() },
        { socket, endpoint ->
            socket.reuseAddress = false
            socket.bind(endpoint)
        },
    )

    /**
     * Android 4.x head-unit builds disagree about the IPv6 wildcard literal. Vendor libcores on
     * some units (e.g. API 19 Hangsheng/freescale) reject `DatagramSocket.bind("::")` with
     * "Can't bind to a link-local address without a scope id" while the same literal binds fine
     * for TCP, so a single wildcard choice is not portable. Walk increasingly concrete candidates
     * and keep the first address that binds.
     */
    private fun <T : Closeable> bindAcrossWildcards(
        reference: InetAddress?,
        stage: String,
        create: () -> T,
        bind: (T, InetSocketAddress) -> Unit,
    ): T = bindAcrossAddresses(stage, wildcardCandidates(reference), create, bind)

    internal fun <T : Closeable> bindAcrossAddresses(
        stage: String,
        candidates: List<InetAddress>,
        create: () -> T,
        bind: (T, InetSocketAddress) -> Unit,
        nextPort: () -> Int = { 49152 + random.nextInt(16384) },
    ): T {
        var last: IOException? = null
        for (address in candidates.distinctBy { it.hostAddress }) {
            try {
                return bindEphemeral(stage, address, create, bind, nextPort)
            } catch (error: IOException) {
                last = IOException("$stage bind failed at ${address.hostAddress}: ${error.message}", error)
            }
        }
        throw IOException("$stage could not bind at any wildcard address: ${last?.message}", last)
    }

    /** Wildcard candidates for the reference family, most- to least-robust construction. */
    internal fun wildcardCandidates(reference: InetAddress?): List<InetAddress> {
        if (reference is Inet4Address) return listOf(wildcard(reference))
        val candidates = mutableListOf<InetAddress>()
        if (reference is Inet6Address && reference.isLinkLocalAddress && reference.scopeId == 0) {
            // Strict vendor kernels (e.g. Android 4.4 on Hangsheng NTDA3 / Allwinner T3) reject
            // the bare "::" wildcard when the link only carries link-local addresses:
            // "Can't bind to a link-local address without a scope id" (issue #124, AirPlay
            // timing bind during SETUP). Prefer the scoped wildcard on the same link; it keeps
            // wildcard semantics while giving the kernel the scope id it demands.
            scopedWildcard(reference)?.let { candidates.add(it) }
        }
        // Raw any-address bytes bypass the platform literal parser and any resolver detour.
        runCatching { candidates.add(InetAddress.getByAddress(ByteArray(16))) }
        if (reference is Inet6Address) candidates.add(reference)
        runCatching { candidates.add(InetAddress.getByName("::")) }
        runCatching { candidates.add(InetAddress.getByName("0.0.0.0")) }
        return candidates
    }

    internal fun <T : Closeable> bindEphemeral(
        stage: String,
        address: InetAddress,
        create: () -> T,
        bind: (T, InetSocketAddress) -> Unit,
        nextPort: () -> Int = { 49152 + random.nextInt(16384) },
    ): T {
        var last: IOException? = null
        repeat(EXPLICIT_BIND_ATTEMPTS + 1) { attempt ->
            var socket: T? = null
            try {
                socket = create()
                // Port zero can fail repeatedly on vendor kernels. Still bind atomically:
                // never probe/close a port and assume it remains available for this socket.
                bind(socket, InetSocketAddress(address, if (attempt == 0) 0 else nextPort()))
                return socket
            } catch (error: Exception) {
                runCatching { socket?.close() }
                if (error !is IOException) throw error
                last = error
                if (!isAddressInUse(error)) {
                    throw IOException("$stage bind failed at ${address.hostAddress}: ${error.message}", error)
                }
            }
        }
        throw IOException(
            "$stage could not bind a free port at ${address.hostAddress}: " +
                (last?.message ?: "unknown bind error"),
            last,
        )
    }

    private val random = Random()
    private const val EXPLICIT_BIND_ATTEMPTS = 16
    private const val PROC_INET6 = "/proc/net/if_inet6"
    private val WHITESPACE = Regex("\\s+")
}

internal fun isAddressInUse(error: Throwable): Boolean =
    generateSequence(error) { it.cause }.any {
        it.message?.contains("EADDRINUSE", ignoreCase = true) == true ||
            it.message?.contains("Address already in use", ignoreCase = true) == true
    }
