package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi

/**
 * Skin-aware Toast helpers.
 *
 * Standard [Toast.makeText] uses system-controlled Context that cannot
 * be wrapped with [SkinContextWrapper]. Use these helpers to create Toasts
 * with a custom layout whose resource lookups are skin-aware.
 *
 * ## Usage
 *
 * ### Simple text Toast (uses skin colors for background/text)
 * ```kotlin
 * showSkinnedToast(requireContext(), "操作成功", Toast.LENGTH_SHORT)
 * ```
 *
 * ### Custom layout Toast
 * ```kotlin
 * showSkinnedToast(requireContext(), R.layout.toast_custom, Toast.LENGTH_SHORT)
 * ```
 *
 * ## Limitations
 *
 * - The Toast window is system-managed and NOT part of any Activity's View tree.
 *   It will NOT be automatically updated if the skin changes while the Toast
 *   is showing. For short-lived Toasts this is rarely an issue.
 * - Android 11+ (API 30) restricts custom Toast views on some devices.
 * - [Snackbar] is preferred for user-visible feedback in Material Design apps.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object ArsToast {

    /**
     * Show a simple text Toast with skin-aware styling.
     *
     * The Toast is created with a skin-wrapped Context, which means
     * the system style (colors, typography) will reflect the current skin
     * or theme mode rather than the host app's defaults.
     *
     * Note: On API 30+, custom views on Toasts are partially restricted.
     * This method falls back to a standard [Toast.makeText] on those versions.
     *
     * @param baseContext The hosting Activity or Fragment context.
     * @param text The message text.
     * @param duration One of [Toast.LENGTH_SHORT] or [Toast.LENGTH_LONG].
     * @return The [Toast] instance (already shown).
     */
    @JvmStatic
    fun showText(baseContext: Context, text: CharSequence, duration: Int = Toast.LENGTH_SHORT): Toast {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ restricts custom Toast views;
            // use standard Toast — the system theme handles colors
            return Toast.makeText(baseContext, text, duration).also { it.show() }
        }

        val ctx = ArsSkinEngine.wrapContext(baseContext)

        @Suppress("DEPRECATION")
        val view = android.widget.TextView(ctx).apply {
            this.text = text
            setTextAppearance(android.R.style.TextAppearance_Material_Body2)
            setPadding(48, 24, 48, 24)
            // Background color from skin resources
            setBackgroundColor(ctx.resources.getColor(android.R.color.background_light, null))
            setTextColor(ctx.resources.getColor(android.R.color.primary_text_light, null))
        }

        val toast = Toast(ctx)
        toast.duration = duration
        @Suppress("DEPRECATION")
        toast.view = view
        toast.show()
        return toast
    }

    /**
     * Show a custom-layout Toast with skin-aware resource resolution.
     *
     * The layout is inflated with a skin-wrapped Context so all
     * `@color/` and `@drawable/` references resolve to the current skin.
     *
     * Note: On API 30+, custom Toast views are restricted and this
     * method returns null. Use [android.widget.Snackbar] or the
     * [ArsSnackbar] helpers for a skin-aware alternative.
     *
     * @param baseContext The hosting Activity or Fragment context.
     * @param layoutResId The layout resource ID to inflate.
     * @param duration One of [Toast.LENGTH_SHORT] or [Toast.LENGTH_LONG].
     * @return The [Toast] instance, or null if custom views are restricted.
     */
    @JvmStatic
    fun showCustom(baseContext: Context, @LayoutRes layoutResId: Int, duration: Int = Toast.LENGTH_SHORT): Toast? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Custom Toast views restricted on API 30+
            return null
        }

        val ctx = ArsSkinEngine.wrapContext(baseContext)
        val view = LayoutInflater.from(ctx).inflate(layoutResId, null)

        val toast = Toast(ctx)
        toast.duration = duration
        @Suppress("DEPRECATION")
        toast.view = view
        toast.show()
        return toast
    }
}
