package com.kuriousdragon.smartindex.feature.home

import com.kuriousdragon.smartindex.core.model.PerfTrace

/** Where the ViewModel's trace begin/end calls go. Tests replace [sink] with a recording shim. */
internal object HomeTrace {
    interface Sink {
        fun begin(name: String, cookie: Int)

        fun end(name: String, cookie: Int)
    }

    val PERF: Sink = object : Sink {
        override fun begin(name: String, cookie: Int) = PerfTrace.begin(name, cookie)

        override fun end(name: String, cookie: Int) = PerfTrace.end(name, cookie)
    }

    @Volatile
    var sink: Sink = PERF
}
