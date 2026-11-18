package com.kuriousdragon.smartindex.tools.seeder

import android.os.Bundle
import android.util.Log

/** The harness log tag `SiSeed` (LLD §6.2); used directly so QA can filter it. Never logs real message text. */
object SeedLog {
    const val TAG = "SiSeed"

    fun line(message: String) {
        Log.i(TAG, message)
    }

    fun warn(message: String) {
        Log.w(TAG, "warn $message")
    }

    fun error(message: String) {
        Log.e(TAG, "error $message")
    }
}

/** Intent extras as a plain map for [SeedCommand.parse]. */
@Suppress("DEPRECATION")
fun Bundle?.toExtrasMap(): Map<String, Any?> {
    val bundle = this ?: return emptyMap()
    return bundle.keySet().associateWith { bundle.get(it) }
}
