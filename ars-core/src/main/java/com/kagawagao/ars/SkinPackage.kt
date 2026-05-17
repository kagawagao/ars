package com.kagawagao.ars

import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Immutable representation of a loaded skin package.
 *
 * Created by [ArsSkinLoader][com.kagawagao.ars.internal.ArsSkinLoader].
 * Consumed by [ArsSkinEngine] and [SkinResources][com.kagawagao.ars.internal.SkinResources].
 *
 * @property name Human-readable skin name (from ARS metadata).
 * @property packageName The Android package name declared in the skin APK's manifest.
 * @property targetPackage The target host app package name this skin is built for.
 * @property version Skin format version for compatibility checking.
 * @property resources The Resources instance for the skin APK.
 * @property path File path to the skin APK on disk.
 * @property themeHint Theme hint for the skin (optional). If set, the skin prefers this mode.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
data class SkinPackage(
    val name: String,
    val packageName: String,
    val targetPackage: String,
    val version: Int,
    val resources: Resources,
    val path: String,
    val themeHint: ThemeMode? = null
) {

    /**
     * Theme mode — matches the Android UI mode concept.
     */
    enum class ThemeMode {
        /** Light theme (uiMode & UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_NO) */
        LIGHT,

        /** Dark theme (uiMode & UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES) */
        DARK
    }

    /**
     * Dispose of the skin's Resources.
     *
     * Resources does not implement Closeable; this is a hook for potential
     * future native resource cleanup when the skin is no longer needed.
     */
    fun dispose() {
        // Resources does not implement Closeable; this is a no-op hook
        // for potential future native resource cleanup.
    }
}
