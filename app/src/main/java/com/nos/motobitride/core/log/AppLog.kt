package com.nos.motobitride.core.log

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Shared app log. Writes to logcat, keeps a bounded UI buffer, and persists the
 * complete process history to numbered 3,000-line files in Documents.
 *
 * Files use `log.<segment>_<session timestamp>.txt`; completed segments are
 * immutable. The current, incomplete segment is periodically replaced with its
 * latest snapshot so a crash still leaves recent diagnostics on disk.
 */
object AppLog {

    data class Entry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = synchronized(TIME_FORMAT) { TIME_FORMAT.format(Date(timestamp)) }
            return "$time $level/$tag: $message"
        }
    }

    private const val MAX_ENTRIES = 4_000
    private const val SEGMENT_LINES = 3_000
    private const val PARTIAL_AUTOSAVE_EVERY = 300
    private const val AUTOSAVE_MIN_INTERVAL_MS = 5_000L
    private const val UI_PUBLISH_MIN_INTERVAL_MS = 150L
    private const val TAG_FILE = "AppLog"

    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()

    // Durable log collector: independent of the bounded UI ring buffer.
    private val currentSegment = ArrayList<Entry>(SEGMENT_LINES)
    private var segmentNumber = 1
    private var linesSincePartialSave = 0
    private var lastPartialSaveAtMs = 0L
    private var lastPublishAtMs = 0L

    private data class SegmentSnapshot(
        val number: Int,
        val text: String,
        val complete: Boolean
    )

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    // All storage operations are serialized and kept off the logging/UI thread.
    private val saver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLog-saver").apply { isDaemon = true }
    }

    @Volatile
    private var appContext: Context? = null

    private val sessionTimestamp: String by lazy {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }

    private fun segmentFileName(number: Int): String = "log.${number}_${sessionTimestamp}.txt"

    /** File che riceverà le prossime righe del log. */
    fun currentLogFileName(): String = synchronized(lock) {
        segmentFileName(segmentNumber)
    }

    // Accessed only by the single-threaded saver.
    private val documentUris = mutableMapOf<Int, Uri>()
    private var documentsFailed = false

    @Volatile
    var lastSavedPath: String? = null
        private set

    fun attach(context: Context) {
        val app = context.applicationContext
        if (appContext == null) appContext = app

        // Capture startup entries emitted before MainActivity attached the logger.
        val pending = synchronized(lock) {
            if (currentSegment.isEmpty()) null else snapshotCurrentSegment(complete = false)
        }
        if (pending != null) enqueueSnapshot(app, pending)
    }

    fun d(tag: String, message: String) = add("D", tag, message)
    fun i(tag: String, message: String) = add("I", tag, message)
    fun w(tag: String, message: String) = add("W", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val text = if (throwable == null) message else
            "$message -> ${throwable.javaClass.simpleName}: ${throwable.message}"
        add("E", tag, text)
    }

    /**
     * Gate del log di diagnostica (Impostazioni -> Diagnostica, default OFF):
     * disabilitato, niente buffer UI, niente file, niente snapshot: il costo
     * si limita alla riga su logcat. Abilitato, comportamento completo:
     * buffer UI + salvataggio automatico dei segmenti in Documenti.
     */
    @Volatile
    private var diagnosticsEnabled = false

    /** Chiamato da AppSettings: sincronizza il gate con la preferenza. */
    fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsEnabled = enabled
    }

    /** Il log diagnostica completo e' attivo? (per gate esterni) */
    fun isDiagnosticsEnabled(): Boolean = diagnosticsEnabled

    private fun add(level: String, tag: String, message: String) {
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "D" -> Log.d(tag, message)
            else -> Log.i(tag, message)
        }

        // Diagnostica disabilitata: logcat basta, il resto non si fa proprio.
        if (!diagnosticsEnabled) return

        val entry = Entry(System.currentTimeMillis(), level, tag, message)
        var snapshot: SegmentSnapshot? = null
        val now = System.currentTimeMillis()

        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()

            currentSegment.add(entry)
            linesSincePartialSave++

            if (currentSegment.size == SEGMENT_LINES) {
                snapshot = snapshotCurrentSegment(complete = true)
                segmentNumber++
                currentSegment.clear()
                linesSincePartialSave = 0
                lastPartialSaveAtMs = now
            } else if (
                linesSincePartialSave >= PARTIAL_AUTOSAVE_EVERY &&
                now - lastPartialSaveAtMs >= AUTOSAVE_MIN_INTERVAL_MS
            ) {
                snapshot = snapshotCurrentSegment(complete = false)
                linesSincePartialSave = 0
                lastPartialSaveAtMs = now
            }
        }

        // Throttle Compose list recomposition even if log producers get noisy.
        if (now - lastPublishAtMs >= UI_PUBLISH_MIN_INTERVAL_MS) {
            lastPublishAtMs = now
            _version.value = _version.value + 1
        }

        val context = appContext
        if (context != null && snapshot != null) enqueueSnapshot(context, snapshot!!)
    }

    private fun snapshotCurrentSegment(complete: Boolean): SegmentSnapshot =
        SegmentSnapshot(
            number = segmentNumber,
            text = currentSegment.joinToString(separator = "\n") { it.format() },
            complete = complete
        )

    private fun enqueueSnapshot(context: Context, snapshot: SegmentSnapshot) {
        saver.execute { writeSnapshot(context, snapshot) }
    }

    private fun writeSnapshot(context: Context, snapshot: SegmentSnapshot) {
        val name = segmentFileName(snapshot.number)

        if (!documentsFailed) {
            try {
                val uri = documentUris[snapshot.number] ?: run {
                    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                    }
                    context.contentResolver.insert(collection, values)?.also {
                        documentUris[snapshot.number] = it
                    }
                }

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(snapshot.text.toByteArray(Charsets.UTF_8))
                    } ?: error("MediaStore returned no output stream")
                    lastSavedPath = "Documenti/$name"
                    return
                }
                documentsFailed = true
            } catch (t: Throwable) {
                documentsFailed = true
                Log.e(TAG_FILE, "salvataggio in Documenti fallito: ${t.message}")
            }
        }

        // Scoped-storage fallback. Still creates one file per 3,000-line segment.
        try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            file.writeText(snapshot.text)
            lastSavedPath = file.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG_FILE, "salvataggio segmento ${snapshot.number} fallito: ${t.message}")
        }
    }

    fun entries(): List<Entry> = synchronized(lock) { buffer.toList() }
    fun asText(): String = entries().joinToString(separator = "\n") { it.format() }

    /** Clears only the in-app view; durable diagnostic history remains intact. */
    fun clear() {
        synchronized(lock) { buffer.clear() }
        _version.value = _version.value + 1
    }
}
