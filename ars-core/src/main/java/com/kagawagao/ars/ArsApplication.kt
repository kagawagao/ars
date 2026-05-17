package com.kagawagao.ars

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

/**
 * Base Application for ARS-skinning-enabled applications.
 *
 * Extend this instead of [Application]. Initializes [ArsSkinEngine] on startup
 * and registers [ActivityLifecycleCallbacks] for tracking active activities.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyApp : ArsApplication() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         // Custom initialization
 *     }
 * }
 * ```
 *
 * The [skinEngine] property provides access to the central engine for
 * skin loading and management.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsApplication : Application() {

    /**
     * The application-scoped skin engine instance.
     *
     * Available after [onCreate] completes. Use this to load skins,
     * register listeners, and query diagnostics.
     */
    val skinEngine: ArsSkinEngine
        get() = ArsSkinEngine

    override fun onCreate() {
        super.onCreate()

        // Initialize the skin engine — must happen before any Activity starts
        ArsSkinEngine.init(this)

        // Track active activities for View-tree walking context
        registerActivityLifecycleCallbacks(ActivityTracker())
    }

    /**
     * Internal ActivityLifecycleCallbacks that maintains a weak reference
     * to the most recently resumed Activity. Used by the View-tree walker
     * to know which decorView to process when a skin switch occurs.
     */
    private class ActivityTracker : ActivityLifecycleCallbacks {

        @Volatile
        private var activeActivity: Activity? = null

        override fun onActivityResumed(activity: Activity) {
            activeActivity = activity
        }

        override fun onActivityPaused(activity: Activity) {
            if (activeActivity === activity) {
                activeActivity = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
