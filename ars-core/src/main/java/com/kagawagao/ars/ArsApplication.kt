package com.kagawagao.ars

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Base Application for ARS-skinning-enabled applications.
 *
 * Extend this instead of [Application]. Initializes [ArsSkinEngine] on startup.
 * Activity lifecycle tracking is handled by [ArsActivity] registering itself
 * directly with the engine via [ArsSkinEngine.registerActiveActivity].
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
    }
}
