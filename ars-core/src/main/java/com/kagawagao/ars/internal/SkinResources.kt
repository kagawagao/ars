package com.kagawagao.ars.internal

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import androidx.annotation.RequiresApi

/**
 * Resources subclass that intercepts resource lookups to provide genuine
 * skin overlay behavior.
 *
 * For each resource lookup, checks the skin [Resources] first, then falls
 * through to the theme-aware host [Resources] (managed via [themedResources]).
 * This is the core mechanism that makes `getResources().getColor()` return
 * skin-aware values without any developer intervention.
 *
 * Resource resolution uses a **name-based lookup** strategy:
 * 1. Resolve the resource name from the host resId via [baseResources].
 * 2. Look up the same name in the skin APK via [skinResources].
 * 3. If found in skin, return the skin value. Otherwise, fall through to [themedResources].
 *
 * This avoids relying on matching resource IDs between APKs (which AAPT
 * assigns differently) and avoids using reflection on hidden APIs.
 *
 * ## Theme Mode Support
 *
 * On API 34+, [Resources.updateConfiguration] is deprecated and may not
 * propagate uiMode changes to the native AssetManager layer. Instead of
 * calling updateConfiguration on a live Resources object, we use
 * [Context.createConfigurationContext] to create a fresh Resources with
 * the desired uiMode baked in. This ensures that night-qualified resources
 * (e.g. `values-night/colors.xml`) are resolved correctly without relying
 * on deprecated APIs.
 *
 * @param baseResources The host application's Resources (for metadata lookups only).
 * @param themedResources The current theme-aware Resources (for value lookups).
 * @param skinResources The skin APK's Resources, or `null` if no skin is active.
 * @param skinPackageName The package name of the skin APK, or `null` if no skin.
 * @param hostPackageName The host application's package name (for resource name parsing).
 * @param hostContext The base (unwrapped) Context, used for createConfigurationContext on theme change.
 * @param idCacheResolver Optional LRU cache callback for skin ID resolution.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Suppress("DEPRECATION")  // getDrawable(int, Theme) deprecated API 34, removed API 36
internal class SkinResources(
    private val baseResources: Resources,
    private var themedResources: Resources,
    private var skinResources: Resources?,
    private var skinPackageName: String?,
    private val hostPackageName: String,
    private val hostContext: Context,
    private val idCacheResolver: ((Int, () -> Int) -> Int)? = null
) : Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {

    companion object {
        private const val TAG = "ARS_SkinResources"
    }

    // ─── Color ────────────────────────────────────────────────────────

    override fun getColor(id: Int, theme: Theme?): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getColor(skinId, theme)
        } else {
            themedResources.getColor(id, theme)
        }
    }

    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getColorStateList(skinId, theme)
        } else {
            themedResources.getColorStateList(id, theme)
        }
    }

    // ─── Drawable ─────────────────────────────────────────────────────

    override fun getDrawable(id: Int, theme: Theme?): Drawable {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDrawable(skinId, theme)
        } else {
            themedResources.getDrawable(id, theme)
        }
    }

    @Suppress("DEPRECATION")
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDrawableForDensity(skinId, density, theme)
                ?: throw Resources.NotFoundException("Skin drawable resource ID #0x${Integer.toHexString(skinId)}")
        } else {
            themedResources.getDrawableForDensity(id, density, theme)
                ?: throw Resources.NotFoundException("Drawable resource ID #0x${Integer.toHexString(id)}")
        }
    }

    // ─── Dimension ────────────────────────────────────────────────────

    override fun getDimension(id: Int): Float {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimension(skinId)
        } else {
            themedResources.getDimension(id)
        }
    }

    override fun getDimensionPixelOffset(id: Int): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimensionPixelOffset(skinId)
        } else {
            themedResources.getDimensionPixelOffset(id)
        }
    }

    override fun getDimensionPixelSize(id: Int): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimensionPixelSize(skinId)
        } else {
            themedResources.getDimensionPixelSize(id)
        }
    }

    // ─── String / Text ────────────────────────────────────────────────

    override fun getString(id: Int): String {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getString(skinId)
        } else {
            themedResources.getString(id)
        }
    }

    override fun getText(id: Int): CharSequence {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getText(skinId)
        } else {
            themedResources.getText(id)
        }
    }

    // ─── TypedValue ───────────────────────────────────────────────────

    override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
        val skinId = resolveSkinId(id)
        if (skinId != 0) {
            skinResources!!.getValue(skinId, outValue, resolveRefs)
        } else {
            themedResources.getValue(id, outValue, resolveRefs)
        }
    }

    override fun getValue(name: String, outValue: TypedValue, resolveRefs: Boolean) {
        // For name-based lookups, try skin first, then themed
        if (skinResources != null && skinPackageName != null) {
            val skinId = skinResources!!.getIdentifier(name, null, skinPackageName)
            if (skinId != 0) {
                skinResources!!.getValue(skinId, outValue, resolveRefs)
                return
            }
        }
        themedResources.getValue(name, outValue, resolveRefs)
    }

    // ─── Package Name ─────────────────────────────────────────────────

    override fun getResourcePackageName(id: Int): String {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getResourcePackageName(skinId)
        } else {
            themedResources.getResourcePackageName(id)
        }
    }

    // ─── Skin Update ──────────────────────────────────────────────────

    /**
     * Update the active skin reference. Called by [ArsSkinEngine] on skin switch.
     *
     * @param skinResources The new skin's Resources, or `null` to reset to default.
     * @param skinPackageName The new skin's package name, or `null` to reset.
     */
    fun updateSkin(skinResources: Resources?, skinPackageName: String?) {
        this.skinResources = skinResources
        this.skinPackageName = skinPackageName
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Skin updated: pkg=${skinPackageName ?: "null"}, " +
                "hasResources=${skinResources != null}")
        }
    }

    /**
     * Update the theme configuration of [themedResources].
     *
     * Called by [ArsSkinEngine.setThemeMode] when the user toggles between
     * light/dark mode. Uses [Context.createConfigurationContext] to create a
     * fresh [Resources] object with the target [Configuration.uiMode] baked in.
     *
     * This is the **critical path** for theme-only switches (no skin loaded):
     * all View-level resource lookups go through [themedResources], and without
     * this update, night-qualified resources like `values-night/colors.xml`
     * are never resolved.
     *
     * **API 34+ safety**: On API 34+, [Resources.updateConfiguration] is
     * deprecated and may be a no-op when `compat` is null. Using
     * [createConfigurationContext] avoids relying on deprecated APIs.
     *
     * @param config The new [Configuration] with the desired uiMode.
     */
    fun updateBaseConfiguration(config: Configuration) {
        val oldUiMode = themedResources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val newUiMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK

        themedResources = hostContext.createConfigurationContext(config).resources

        Log.i(TAG, "Theme mode updated: " +
            "uiMode=${if (oldUiMode == Configuration.UI_MODE_NIGHT_YES) "DARK" else "LIGHT"} → " +
            "${if (newUiMode == Configuration.UI_MODE_NIGHT_YES) "DARK" else "LIGHT"}, " +
            "pkg=${hostPackageName}")
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    /**
     * Resolve a host resource ID to the corresponding skin resource ID.
     *
     * Uses name-based lookup: extracts the resource entry name and type from
     * the host resId, then looks up the same name+type in the skin Resources.
     *
     * @param hostResId The resource ID from the host application.
     * @return The skin resource ID, or 0 if not found in the skin.
     */
    private fun resolveSkinId(hostResId: Int): Int {
        val skinRes = skinResources ?: return 0
        val skinPkg = skinPackageName ?: return 0

        val resolver = {
            try {
                val resName = baseResources.getResourceEntryName(hostResId)
                val resType = baseResources.getResourceTypeName(hostResId)
                skinRes.getIdentifier(resName, resType, skinPkg)
            } catch (e: Resources.NotFoundException) {
                0
            }
        }

        // Use the engine's LRU cache if available, otherwise resolve directly
        val result = idCacheResolver?.invoke(hostResId, resolver) ?: resolver()

        // Debug logging for missing resources
        if (result == 0 && Log.isLoggable(TAG, Log.DEBUG)) {
            try {
                val resName = baseResources.getResourceEntryName(hostResId)
                val resType = baseResources.getResourceTypeName(hostResId)
                Log.d(TAG, "Resource '$resName' ($resType) not found in skin package '$skinPkg'")
            } catch (_: Resources.NotFoundException) { }
        }

        return result
    }
}
