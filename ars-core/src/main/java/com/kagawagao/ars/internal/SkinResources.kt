package com.kagawagao.ars.internal

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import androidx.annotation.RequiresApi

/**
 * Resources subclass that intercepts resource lookups to provide genuine
 * skin overlay behavior.
 *
 * For each resource lookup, checks the skin [Resources] first, then falls
 * through to the base (host app) Resources. This is the core mechanism
 * that makes `getResources().getColor()` return skin-aware values without
 * any developer intervention.
 *
 * Resource resolution uses a **name-based lookup** strategy:
 * 1. Resolve the resource name from the host resId via [baseResources].
 * 2. Look up the same name in the skin APK via [skinResources].
 * 3. If found in skin, return the skin value. Otherwise, fall through to base.
 *
 * This avoids relying on matching resource IDs between APKs (which AAPT
 * assigns differently) and avoids using reflection on hidden APIs.
 *
 * @param baseResources The host application's Resources.
 * @param skinResources The skin APK's Resources, or `null` if no skin is active.
 * @param skinPackageName The package name of the skin APK, or `null` if no skin.
 * @param hostPackageName The host application's package name (for resource name parsing).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class SkinResources(
    private val baseResources: Resources,
    private var skinResources: Resources?,
    private var skinPackageName: String?,
    private val hostPackageName: String
) : Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {

    // ─── Color ────────────────────────────────────────────────────────

    override fun getColor(id: Int, theme: Theme?): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getColor(skinId, theme)
        } else {
            baseResources.getColor(id, theme)
        }
    }

    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getColorStateList(skinId, theme)
        } else {
            baseResources.getColorStateList(id, theme)
        }
    }

    // ─── Drawable ─────────────────────────────────────────────────────

    override fun getDrawable(id: Int, theme: Theme?): Drawable {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDrawable(skinId, theme)
        } else {
            baseResources.getDrawable(id, theme)
        }
    }

    @Suppress("DEPRECATION")
    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDrawableForDensity(skinId, density, theme)
        } else {
            baseResources.getDrawableForDensity(id, density, theme)
        }
    }

    // ─── Dimension ────────────────────────────────────────────────────

    override fun getDimension(id: Int): Float {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimension(skinId)
        } else {
            baseResources.getDimension(id)
        }
    }

    override fun getDimensionPixelOffset(id: Int): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimensionPixelOffset(skinId)
        } else {
            baseResources.getDimensionPixelOffset(id)
        }
    }

    override fun getDimensionPixelSize(id: Int): Int {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getDimensionPixelSize(skinId)
        } else {
            baseResources.getDimensionPixelSize(id)
        }
    }

    // ─── String / Text ────────────────────────────────────────────────

    override fun getString(id: Int): String {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getString(skinId)
        } else {
            baseResources.getString(id)
        }
    }

    override fun getText(id: Int): CharSequence {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getText(skinId)
        } else {
            baseResources.getText(id)
        }
    }

    // ─── TypedValue ───────────────────────────────────────────────────

    override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean) {
        val skinId = resolveSkinId(id)
        if (skinId != 0) {
            skinResources!!.getValue(skinId, outValue, resolveRefs)
        } else {
            baseResources.getValue(id, outValue, resolveRefs)
        }
    }

    override fun getValue(name: String, outValue: TypedValue, resolveRefs: Boolean) {
        // For name-based lookups, try skin first, then base
        if (skinResources != null && skinPackageName != null) {
            val skinId = skinResources!!.getIdentifier(name, null, skinPackageName)
            if (skinId != 0) {
                skinResources!!.getValue(skinId, outValue, resolveRefs)
                return
            }
        }
        baseResources.getValue(name, outValue, resolveRefs)
    }

    // ─── Package Name ─────────────────────────────────────────────────

    override fun getResourcePackageName(id: Int): String {
        val skinId = resolveSkinId(id)
        return if (skinId != 0) {
            skinResources!!.getResourcePackageName(skinId)
        } else {
            baseResources.getResourcePackageName(id)
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

        return try {
            val resName = baseResources.getResourceEntryName(hostResId)
            val resType = baseResources.getResourceTypeName(hostResId)
            skinRes.getIdentifier(resName, resType, skinPkg)
        } catch (e: Resources.NotFoundException) {
            0
        }
    }
}
