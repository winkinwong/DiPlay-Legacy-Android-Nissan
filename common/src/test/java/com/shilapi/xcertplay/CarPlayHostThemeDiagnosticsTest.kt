package com.shilapi.xcertplay

import android.content.res.Configuration
import com.shilapi.xcertplay.airplay.AirPlaySession
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.util.concurrent.PausedExecutorService
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarPlayHostThemeDiagnosticsTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var activity: CarPlayHostActivity
    private lateinit var logFile: File
    private lateinit var commands: PausedExecutorService

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        (getField("airPlayCommandExecutor") as ExecutorService).shutdownNow()
        commands = PausedExecutorService()
        setField("airPlayCommandExecutor", commands)
        logFile = folder.newFile("diplay.log")
        setField("sessionLog", SessionLogFile(logFile))
        setField("darkMode", true)
        setField("lastConfiguration", configuration(Configuration.UI_MODE_NIGHT_YES))
    }

    @After fun tearDown() {
        commands.shutdownNow()
        (getField("teardownExecutor") as ExecutorService).shutdownNow()
        (getField("sessionLog") as SessionLogFile).close()
    }

    @Test fun undefinedSignalKeepsThePreviousModeAndIsRecordedWithoutASend() {
        refresh(Configuration.UI_MODE_TYPE_CAR, ThemeModeDiagnostics.Source.POLL)
        commands.runAll()

        assertEquals(true, getField("darkMode"))
        assertTrue(logFile.readText().contains("reported=undefined applied=dark sessionActive=false"))
        assertTrue(!logFile.readText().contains("THEME_DIAGNOSTIC send"))
    }

    @Test fun changedThemeIsSentOnceAndTheUnavailableEventChannelIsInTheSavedReport() {
        val session = mock(AirPlaySession::class.java)
        setField("activeAirPlaySession", session)
        refresh(Configuration.UI_MODE_NIGHT_NO, ThemeModeDiagnostics.Source.CALLBACK)
        commands.runAll()
        refresh(Configuration.UI_MODE_NIGHT_NO, ThemeModeDiagnostics.Source.POLL)
        commands.runAll()

        assertEquals(false, getField("darkMode"))
        verify(session, times(1)).setNightMode(false)
        val log = logFile.readText()
        assertTrue(log.contains("source=configuration-callback"))
        assertTrue(log.contains("reported=light applied=light sessionActive=true"))
        assertTrue(log.contains("THEME_DIAGNOSTIC send source=configuration-callback applied=light commandWritten=false"))
    }

    @Test fun fixedDayIsNotOverriddenBySystemNightAndDiagnosticsKeepBothStates() {
        val session = mock(AirPlaySession::class.java)
        setField("activeAirPlaySession", session)
        val controller = activity.javaClass.getDeclaredMethod("getNightModeController")
            .apply { isAccessible = true }.invoke(activity) as CarPlayNightModeController
        controller.configure(CarPlayNightMode.DAY, true)
        commands.runAll()
        refresh(Configuration.UI_MODE_NIGHT_NO, ThemeModeDiagnostics.Source.CALLBACK)
        refresh(Configuration.UI_MODE_NIGHT_YES, ThemeModeDiagnostics.Source.CALLBACK)
        commands.runAll()
        assertEquals(false, getField("darkMode"))
        verify(session, times(1)).setNightMode(false)
        verify(session, times(0)).setNightMode(true)
        assertTrue(logFile.readText().contains("reported=dark applied=light sessionActive=true"))
    }

    @Test fun themeSyncDuringShutdownIsSkippedInsteadOfCrashing() {
        // shutdown() terminates the executor while a late onResume can still
        // hold a session reference. The sync must be skipped, not submitted.
        val session = mock(AirPlaySession::class.java)
        setField("activeAirPlaySession", session)
        setField("shuttingDown", java.util.concurrent.atomic.AtomicBoolean(true))
        syncDarkMode(ThemeModeDiagnostics.Source.RESUME)
        val log = logFile.readText()
        assertTrue(log.contains("sessionActive=true"))
        assertTrue(!log.contains("THEME_DIAGNOSTIC send"))
    }

    @Test fun themeSyncOnTerminatedExecutorIsRecordedInsteadOfCrashing() {
        // The executor is terminated but the activity was not marked
        // shutting down: the submit must be caught and recorded — the
        // observed FATAL RejectedExecutionException on resume right
        // after exiting CarPlay must not come back.
        val session = mock(AirPlaySession::class.java)
        setField("activeAirPlaySession", session)
        val terminated = Executors.newSingleThreadExecutor()
        terminated.shutdownNow()
        setField("airPlayCommandExecutor", terminated)
        syncDarkMode(ThemeModeDiagnostics.Source.RESUME)
        val log = logFile.readText()
        assertTrue(log.contains("failureClass=RejectedExecutionException"))
        verify(session, times(0)).setNightMode(true)
        verify(session, times(0)).setNightMode(false)
    }

    private fun syncDarkMode(source: ThemeModeDiagnostics.Source) {
        activity.javaClass.getDeclaredMethod("syncAirPlayDarkMode", ThemeModeDiagnostics.Source::class.java)
            .apply { isAccessible = true }.invoke(activity, source)
    }

    private fun refresh(mode: Int, source: ThemeModeDiagnostics.Source) {
        activity.javaClass.getDeclaredMethod("refreshConfiguration", Configuration::class.java, ThemeModeDiagnostics.Source::class.java)
            .apply { isAccessible = true }.invoke(activity, configuration(mode), source)
    }

    private fun configuration(mode: Int) = Configuration().apply { uiMode = mode }

    private fun getField(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)

    private fun setField(name: String, value: Any) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
