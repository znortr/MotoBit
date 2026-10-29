package com.nos.motobitride.core.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Log in memoria condiviso tra UI e core.
 *
 * Ogni riga viene:
 *  - scritta su logcat (cosi' resta disponibile via `adb logcat`)
 *  - accumulata in un ring buffer mostrato nella finestra di log in-app
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

    private const val MAX_ENTRIES = 800
    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun d(tag: String, message: String) = add("D", tag, message)

    fun i(tag: String, message: String) = add("I", tag, message)

    fun w(tag: String, message: String) = add("W", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val text = if (throwable == null) {
            message
        } else {
            "$message -> ${throwable.javaClass.simpleName}: ${throwable.message}"
        }
        add("E", tag, text)
    }

    private fun add(level: String, tag: String, message: String) {
        when (level) {
            "E" -> Log.e(tag, message)
            "W" -> Log.w(tag, message)
            "D" -> Log.d(tag, message)
            else -> Log.i(tag, message)
        }

        synchronized(lock) {
            buffer.addLast(Entry(System.currentTimeMillis(), level, tag, message))
            while (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
        }
        _version.value = _version.value + 1
    }

    fun entries(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun asText(): String = entries().joinToString(separator = "\n") { it.format() }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        _version.value = _version.value + 1
    }
}
