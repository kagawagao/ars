package com.kagawagao.ars

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Runtime diagnostics snapshot of the ARS skinning framework.
 *
 * Obtain via [ArsSkinEngine.getDiagnostics].
 *
 * @property activeSkinName The human-readable name of the active skin, or `null` if default.
 * @property activeSkinPackage The Android package name of the active skin, or `null` if default.
 * @property activeSkinVersion The version of the active skin, or `-1` if default.
 * @property themeMode The current theme mode (LIGHT or DARK).
 * @property registeredViewCount Total number of Views ever registered with the engine.
 * @property aliveViewCount Number of registered Views that are still alive (not GC'd).
 * @property listenerCount Number of registered [SkinChangeListener] instances.
 * @property registeredAttributeCount Number of registered custom [SkinAttributeHandler] instances
 *           plus built-in attributes.
 * @property cachedIdMappings Number of cached (hostResId → skinResId) mappings, or `-1` if
 *           caching is not enabled.
 * @property lastSwitchDurationMs Duration of the most recent skin switch in milliseconds,
 *           or `-1` if no switch has occurred.
 * @property lastError The most recent [SkinError], or `null` if no errors have occurred.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
data class SkinDiagnostics(
    val activeSkinName: String?,
    val activeSkinPackage: String?,
    val activeSkinVersion: Int,
    val themeMode: SkinPackage.ThemeMode,
    val registeredViewCount: Int,
    val aliveViewCount: Int,
    val listenerCount: Int,
    val registeredAttributeCount: Int,
    val cachedIdMappings: Int,
    val lastSwitchDurationMs: Long,
    val lastError: SkinError?
)
