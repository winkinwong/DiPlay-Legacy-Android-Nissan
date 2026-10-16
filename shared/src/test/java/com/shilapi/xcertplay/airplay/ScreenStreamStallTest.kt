package com.shilapi.xcertplay.airplay

import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A silent iPhone sender must not freeze the screen forever: the stream
 * reports the stall (so the engine can request a keyframe) and eventually
 * resets itself so the iPhone re-SETUPs a fresh screen stream.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenStreamStallTest {
    private class RecordingListener : ScreenStream.Listener {
        @Volatile
        var closedCause: Throwable? = null
        private val closedLatch = CountDownLatch(1)
        private val lock = Any()
        val stallReports = mutableListOf<Long>()

        override fun onReceiveStall(millis: Long) {
            synchronized(lock) { stallReports.add(millis) }
        }

        override fun onClosed(cause: Throwable?) {
            closedCause = cause
            closedLatch.countDown()
        }

        fun awaitClosed(timeoutMillis: Long): Boolean =
            closedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    @Test
    fun silentStreamReportsStallAndResets() {
        val listener = RecordingListener()
        val stream = stalledTestStream()
        val port = stream.listen(listener)
        Socket("127.0.0.1", port).use { client ->
            client.outputStream.write(configMessage())
            client.outputStream.flush()
            assertTrue("stream should end after the reset budget", listener.awaitClosed(5_000))
        }
        assertTrue("stall report expected", listener.stallReports.isNotEmpty())
        assertTrue(
            "stall report should carry the silent duration",
            listener.stallReports.all { it >= STALL_REPORT_MILLIS },
        )
        assertTrue(
            "closed cause should be the receive stall",
            listener.closedCause is ScreenStream.ReceiveStallError,
        )
        stream.close()
    }

    @Test
    fun flowingStreamDoesNotReportStall() {
        val listener = RecordingListener()
        val stream = stalledTestStream()
        val port = stream.listen(listener)
        Socket("127.0.0.1", port).use { client ->
            repeat(40) {
                client.outputStream.write(configMessage())
                client.outputStream.flush()
                Thread.sleep(25)
            }
        }
        // The client EOF ends the stream; no stall report may fire while
        // payloads keep arriving.
        assertTrue(listener.awaitClosed(5_000))
        assertTrue("no stall report expected", listener.stallReports.isEmpty())
        stream.close()
    }

    @Test
    fun stallReportsAreRateLimited() {
        val listener = RecordingListener()
        val stream = ScreenStream(ByteArray(32)).apply {
            readTimeoutMillis = 100
            stallReportMillis = STALL_REPORT_MILLIS
            stallReportIntervalMillis = 400
            stallResetMillis = 5_000
        }
        val port = stream.listen(listener)
        Socket("127.0.0.1", port).use { client ->
            client.outputStream.write(configMessage())
            client.outputStream.flush()
            Thread.sleep(1_400)
        }
        // 1.4 s of silence with a 400 ms interval: at most a handful of
        // reports, never one per 100 ms read timeout.
        assertTrue("stall report expected", listener.stallReports.isNotEmpty())
        assertTrue(
            "reports must be rate-limited to the interval",
            listener.stallReports.size <= 5,
        )
        stream.close()
    }

    private fun stalledTestStream(): ScreenStream =
        ScreenStream(ByteArray(32)).apply {
            readTimeoutMillis = 100
            stallReportMillis = STALL_REPORT_MILLIS
            stallReportIntervalMillis = 100
            stallResetMillis = 600
        }

    private companion object {
        const val HEADER_LEN = 128
        const val OP_VIDEO_CONFIG = 1
        const val STALL_REPORT_MILLIS = 200L

        /** A clear VideoConfig message: a 128-byte header plus an avcC body. */
        fun configMessage(): ByteArray {
            val header = ByteArray(HEADER_LEN)
            val body = ByteArray(20)
            "avcC".toByteArray(Charsets.US_ASCII).copyInto(body, 4)
            header[0] = (body.size and 0xff).toByte()
            header[1] = (body.size ushr 8 and 0xff).toByte()
            header[2] = (body.size ushr 16 and 0xff).toByte()
            header[3] = (body.size ushr 24).toByte()
            header[4] = OP_VIDEO_CONFIG.toByte()
            return header + body
        }
    }
}
