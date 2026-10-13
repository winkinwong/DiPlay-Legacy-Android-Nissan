package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Durable crash evidence for issue #134: the app died on the way back to Settings, the reporter
 * restarted it and exported a report, and no crash stack reached that report. The record has to survive
 * the process, be redacted like every other diagnostic, and stay bounded on a head unit with little
 * free space.
 */
class CrashDiagnosticsTest {
    private fun folder() = Files.createTempDirectory("diplay-crash-store").toFile()

    @Test fun aCrashRecordedBeforeRestartIsStillReportedAfterIt() {
        val directory = folder()
        try {
            CrashDiagnostics.record(
                directory,
                Thread.currentThread(),
                IllegalStateException("boom on the way back to settings"),
                nowMillis = 1_760_000_000_000,
            )
            val report = CrashDiagnostics.report(directory)
            assertTrue(report.contains("FATAL EXCEPTION"))
            assertTrue(report.contains("java.lang.IllegalStateException: boom on the way back to settings"))
            assertTrue(report.contains("Thread: "))
        } finally { directory.deleteRecursively() }
    }

    @Test fun crashRecordsAreRedactedBeforeTheyReachDisk() {
        val directory = folder()
        try {
            CrashDiagnostics.record(
                directory,
                Thread.currentThread(),
                IllegalStateException("lost peer=192.168.49.1 mac=C0:A6:00:29:58:0A"),
            )
            val text = File(directory, CrashDiagnostics.FILE_NAME).readText()
            assertFalse("an address must not reach disk", text.contains("192.168.49.1"))
            assertFalse("a mac must not reach disk", text.contains("C0:A6:00:29:58:0A"))
            assertTrue("the failure itself still has to be readable", text.contains("lost peer="))
        } finally { directory.deleteRecursively() }
    }

    @Test fun aCredentialBearingLineIsStillDroppedEntirely() {
        val directory = folder()
        try {
            CrashDiagnostics.record(
                directory,
                Thread.currentThread(),
                IllegalStateException("token=abcd1234"),
            )
            assertFalse(File(directory, CrashDiagnostics.FILE_NAME).readText().contains("abcd1234"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun theNewestCrashSurvivesWhenTheFileWouldGrowPastItsCap() {
        val directory = folder()
        try {
            repeat(40) { index ->
                CrashDiagnostics.record(
                    directory,
                    Thread.currentThread(),
                    IllegalStateException("crash-$index " + "x".repeat(4_000)),
                )
            }
            val file = File(directory, CrashDiagnostics.FILE_NAME)
            assertTrue("crash file must stay bounded, was ${file.length()}", file.length() <= CrashDiagnostics.MAX_BYTES + 8_192)
            assertTrue("the newest crash must remain", file.readText().contains("crash-39"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun anEmptyStoreSaysSoInsteadOfLookingLikeACleanRun() {
        val directory = folder()
        try {
            assertTrue(CrashDiagnostics.report(directory).contains("no crash recorded"))
        } finally { directory.deleteRecursively() }
    }
}
