package com.shilapi.xcertplay.media

import android.Manifest
import android.media.AudioManager
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The audio renderer must not open the audio output until PCM actually
 * arrives. On the NTDA3 head unit a Siri speech-recognition stream that
 * never carries PCM (the phone routes Siri voice itself) held a
 * created-but-silent AudioTrack on the legacy stream, which wedged the
 * whole head-unit output until a routing change (the reverse camera)
 * reset it — field report 2026-10-15, v0.2.46-t32.2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class AudioRendererLazyTrackTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val diagnostics = CopyOnWriteArrayList<String>()
    private val streamStarted = CountDownLatch(1)
    private val firstPcmWrite = CountDownLatch(1)
    private val finalStats = CountDownLatch(1)
    private lateinit var sink: AndroidMediaSink

    @Before fun setUp() {
        shadowOf(context).grantPermissions(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
        )
        context.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        sink = AndroidMediaSink(context = context, onAudioDiagnostic = { line ->
            diagnostics += line
            if (line.startsWith("Audio: starting")) streamStarted.countDown()
            if (line.startsWith("Audio: first PCM write")) firstPcmWrite.countDown()
            if (line.startsWith("audio stats") && line.endsWith("ended=true")) finalStats.countDown()
        })
    }

    @After fun tearDown() {
        sink.close()
    }

    @Test fun speechRecognitionStreamWithoutPcmNeverOpensTheAudioOutput() {
        val id = AudioStreamId(100, "speechrecognition")
        sink.onAudioStarted(id, AudioFormat(AudioCodecKind.LPCM, 24_000, 1, id.type, "speechrecognition"), 0)
        assertTrue("renderer worker started", streamStarted.await(5, TimeUnit.SECONDS))
        sink.onAudioStopped(id)
        assertTrue("renderer final stats", finalStats.await(5, TimeUnit.SECONDS))

        assertFalse(
            "no PCM write without packets",
            diagnostics.any { it.startsWith("Audio: first PCM write") },
        )
        assertTrue(
            "no AudioTrack was created for a PCM-less stream",
            diagnostics.any { it.contains("audio stats audioType=speechrecognition") && it.contains("trackState=-1") },
        )
    }

    @Test fun lpcmStreamOpensTheAudioOutputOnTheFirstPcmWrite() {
        val id = AudioStreamId(100, "media")
        val format = AudioFormat(AudioCodecKind.LPCM, 48_000, 2, id.type, "media")
        sink.onAudioStarted(id, format, 0)
        assertTrue("renderer worker started", streamStarted.await(5, TimeUnit.SECONDS))
        // 12-byte RTP header + one 20 ms stereo frame (1920 bytes).
        val packet = ByteArray(12 + 1920)
        repeat(10) { sink.onAudioRtp(id, format, packet, it) }
        assertTrue("first PCM write happened", firstPcmWrite.await(5, TimeUnit.SECONDS))
        sink.onAudioStopped(id)
        assertTrue("renderer final stats", finalStats.await(5, TimeUnit.SECONDS))

        assertTrue(
            "first PCM write reported",
            diagnostics.any { it.startsWith("Audio: first PCM write") },
        )
        assertTrue(
            "AudioTrack was created for a live stream",
            diagnostics.any { it.contains("audio stats audioType=media") && it.contains("trackState=1") },
        )
    }
}
