package com.shilapi.xcertplay

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Durable crash evidence.
 *
 * The record is redacted and stored a line at a time: [DiagnosticRedactor] drops any single line that
 * still carries a newline, so handing it a whole stack trace discarded the entire record. Keeping it on
 * disk means a reporter can restart the app and still export the crash that killed the previous run,
 * which is exactly the stack issue #134 lost when DiPlay died on the way back to Settings.
 */
object CrashDiagnostics {
    const val FILE_NAME = "diplay_crash.log"
    const val MAX_BYTES = 128 * 1024L
    private const val REPORT_MAX_BYTES = 64 * 1024
    private const val MAX_FRAMES = 60
    private const val TRIMMED = "… (earlier records trimmed)\n"
    private const val LEGACY_PUBLIC_STORAGE_API = 28

    private val lock = Any()

    fun record(
        directory: File,
        thread: Thread,
        error: Throwable,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val entry = entryFor(thread, error, nowMillis)
        synchronized(lock) {
            runCatching {
                directory.mkdirs()
                val file = File(directory, FILE_NAME)
                trim(file, entry.length.toLong())
                file.appendText(entry)
            }
        }
    }

    /**
     * Records into the app's own log directory (which the exported report reads back) plus the mirrors a
     * head-unit user can reach without adb. Resolving the directories here rather than in the application
     * class is what lets a test cover the whole way from "the app died" to "the next run exports it".
     */
    fun recordInAppDirectories(context: Context, thread: Thread, error: Throwable) {
        record(File(context.filesDir, "logs"), thread, error)
        runCatching { context.getExternalFilesDir("logs")?.let { record(it, thread, error) } }
        if (Build.VERSION.SDK_INT <= LEGACY_PUBLIC_STORAGE_API) {
            runCatching {
                @Suppress("DEPRECATION")
                Environment.getExternalStorageDirectory()
                    ?.takeIf { it.canWrite() }
                    ?.let { record(it, thread, error) }
            }
        }
    }

    /** The recorded crash history, bounded, or an explicit note that nothing was ever recorded. */
    fun report(directory: File): String = synchronized(lock) {        val file = File(directory, FILE_NAME)
        val text = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        when {
            text.isBlank() -> "no crash recorded"
            text.length <= REPORT_MAX_BYTES -> text
            else -> TRIMMED + text.takeLast(REPORT_MAX_BYTES)
        }
    }

    /** Keeps at most [MAX_BYTES] of history plus the incoming record, so a crash loop cannot fill storage. */
    private fun trim(file: File, incoming: Long) {
        if (!file.isFile) return
        val allowed = (MAX_BYTES - incoming).coerceAtLeast(0L)
        if (file.length() <= allowed) return
        val kept = runCatching { file.readText().takeLast(allowed.toInt()) }.getOrDefault("")
        val fromWholeLine = kept.indexOf('\n').let { if (it >= 0) kept.substring(it + 1) else kept }
        file.writeText(TRIMMED + fromWholeLine)
    }

    private fun entryFor(thread: Thread, error: Throwable, nowMillis: Long): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val raw = buildList {
            add("================ FATAL EXCEPTION ================")
            add("Time: ${timestamp(nowMillis)}")
            add("Thread: ${thread.name} (id=${thread.id})")
            add("Exception: ${error.javaClass.name}: ${error.message}")
            addAll(writer.toString().lineSequence().filter { it.isNotBlank() }.take(MAX_FRAMES))
            add("=================================================")
        }
        // Every line, the header included, goes through the redactor: an exception message can carry a
        // credential just as easily as a payload line can.
        return buildString {
            for (line in raw) DiagnosticRedactor.redact(line)?.let { appendLine(it) }
        }
    }

    private fun timestamp(nowMillis: Long) =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(nowMillis))
}
