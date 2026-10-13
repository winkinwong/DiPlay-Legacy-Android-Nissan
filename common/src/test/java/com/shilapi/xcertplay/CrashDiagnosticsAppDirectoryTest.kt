package com.shilapi.xcertplay

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The exact path issue #134 walked: DiPlay died while the reporter was leaving CarPlay for Settings,
 * they restarted the app and exported a report, and the crash was nowhere in it.
 *
 * The SDK pin is required, as in every other Robolectric test here: under JDK 25 the default (31+)
 * environment fails in Robolectric's own setup while creating ApplicationSharedMemory. 28 also keeps the
 * legacy public-storage mirror of the crash record under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 30], manifest = Config.NONE)
class CrashDiagnosticsAppDirectoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun aCrashFromTheLastRunIsStillExportableAfterTheAppRestarts() {
        val logs = File(context.filesDir, "logs")
        logs.deleteRecursively()

        CrashDiagnostics.recordInAppDirectories(
            context,
            Thread.currentThread(),
            IllegalStateException("boom while leaving CarPlay for settings"),
        )

        // The export reads the directory again, the way a later process would.
        val report = CrashDiagnostics.report(File(context.filesDir, "logs"))
        assertTrue("The crash header must survive the restart", report.contains("FATAL EXCEPTION"))
        assertTrue("The failure itself must survive the restart", report.contains("boom while leaving CarPlay for settings"))
    }

    @Test fun aFreshInstallReportsThatNoCrashWasRecorded() {
        File(context.filesDir, "logs").deleteRecursively()
        assertTrue(CrashDiagnostics.report(File(context.filesDir, "logs")).contains("no crash recorded"))
    }
}
