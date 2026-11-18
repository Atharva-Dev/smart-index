package com.kuriousdragon.smartindex.perf

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.startup.Initializer
import com.kuriousdragon.smartindex.BuildConfig
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Registers [PerfMarkers] once the Hilt graph exists (first activity). No-op unless BuildConfig.PERF_MARKERS. */
class PerfMarkersInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (!BuildConfig.PERF_MARKERS) return
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var started = false

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (started) return
                    started = true
                    val source = EntryPointAccessors.fromApplication(app, PerfEntryPoint::class.java)
                        .indexStatusSource()
                        .orElse(null)
                    if (source != null) {
                        PerfMarkers.start(source, CoroutineScope(SupervisorJob() + Dispatchers.Default))
                    }
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
