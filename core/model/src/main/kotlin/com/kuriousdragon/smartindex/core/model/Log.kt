package com.kuriousdragon.smartindex.core.model

import android.util.Log

/**
 * The only logging API of Smart Index (HLD §7 Logging, NFR-006). Owner: SIDX-6.
 *
 * Rules (binding for every module):
 * - Log ids, counts, modes, durations and exception **class names** only. Never message text, phone
 *   numbers, contact names, file names, folders, URIs with ids, query text or `Throwable.message`.
 * - [d] and [i] are dropped in release builds; [w] and [e] are always written (release logs WARN+).
 * - Tags are short component names (`"Index"`, `"Sync"`, `"Tel.Sms"`); they are written as `SI.<tag>`.
 */
object SiLog {
    /** Where log lines go. Tests may replace it (for example with a recording sink). */
    fun interface Sink {
        fun write(priority: Int, tag: String, message: String)
    }

    /** Writes to logcat through `android.util.Log` (a no-op in plain JVM unit tests). */
    val LOGCAT: Sink = Sink { priority, tag, message -> Log.println(priority, tag, message) }

    /** Current sink. */
    @Volatile
    var sink: Sink = LOGCAT

    /** Whether [d] and [i] are written: true in debug builds only. */
    @Volatile
    var verbose: Boolean = BuildConfig.DEBUG

    fun d(tag: String, msg: String) {
        if (verbose) sink.write(Log.DEBUG, prefixed(tag), msg)
    }

    fun i(tag: String, msg: String) {
        if (verbose) sink.write(Log.INFO, prefixed(tag), msg)
    }

    fun w(tag: String, msg: String) {
        sink.write(Log.WARN, prefixed(tag), msg)
    }

    /** Appends only the class name of [t] (never its message, which can contain content). */
    fun w(tag: String, msg: String, t: Throwable) {
        sink.write(Log.WARN, prefixed(tag), "$msg class=${t.javaClass.simpleName}")
    }

    fun e(tag: String, msg: String) {
        sink.write(Log.ERROR, prefixed(tag), msg)
    }

    /** Appends only the class name of [t] (never its message, which can contain content). */
    fun e(tag: String, msg: String, t: Throwable) {
        sink.write(Log.ERROR, prefixed(tag), "$msg class=${t.javaClass.simpleName}")
    }

    private fun prefixed(tag: String): String = "SI.$tag"
}
