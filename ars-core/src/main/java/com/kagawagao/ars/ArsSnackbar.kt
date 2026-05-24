package com.kagawagao.ars

import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Skin-aware helpers for overlay UI components (Snackbar, custom overlays, etc.).
 *
 * Overlay components (Snackbar, custom floating views) use their own Window,
 * separate from the Activity's decorView tree. When the active skin changes,
 * these overlays are NOT automatically walked by the engine.
 *
 * This utility provides general-purpose methods to refresh overlays.
 *
 * ## Usage
 *
 * ### Snackbar (Material Components)
 *
 * ```kotlin
 * // Show with auto-refresh on skin change
 * val snackbar = Snackbar.make(view, "消息", Snackbar.LENGTH_SHORT)
 * ArsOverlaySkin.showWithAutoRefresh(snackbar)
 * ```
 *
 * Or manually:
 * ```kotlin
 * val snackbar = Snackbar.make(view, "消息", Snackbar.LENGTH_SHORT).apply { show() }
 * // ... after skin change
 * val contentView = (snackbar.view as? ViewGroup)?.getChildAt(0) ?: snackbar.view
 * ArsOverlaySkin.refresh(contentView)
 * ```
 *
 * ### Any custom overlay
 *
 * ```kotlin
 * class MyFloatingView(context: Context) : FrameLayout(context) {
 *     init {
 *         // Walk this View's tree whenever the skin changes
 *         val listener = SkinChangeListener { _, _ ->
 *             ArsOverlaySkin.refresh(this)
 *         }
 *         addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
 *             override fun onViewAttachedToWindow(v: View) {
 *                 ArsSkinEngine.registerSkinChangeListener(listener)
 *             }
 *             override fun onViewDetachedFromWindow(v: View) {
 *                 ArsSkinEngine.unregisterSkinChangeListener(listener)
 *             }
 *         })
 *     }
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object ArsOverlaySkin {

    /**
     * Walk a View tree and apply the current skin.
     *
     * Use this to refresh any View hierarchy that is NOT part of an
     * Activity's decorView tree (Snackbar content, PopupWindow not using
     * [ArsPopupWindow], custom floating views, etc.).
     *
     * @param root The root View of the overlay to refresh.
     */
    fun refresh(root: View) {
        com.kagawagao.ars.internal.ArsViewTreeWalker.walk(root, ArsSkinEngine)
    }

    /**
     * Register a [SkinChangeListener] that refreshes the overlay View
     * whenever the skin changes.
     *
     * Returns the listener so the caller can unregister it when the overlay
     * is dismissed.
     *
     * ```kotlin
     * val listener = ArsOverlaySkin.autoRefresh(overlayView)
     * // ... when done:
     * ArsSkinEngine.unregisterSkinChangeListener(listener)
     * ```
     *
     * @param overlayRoot The root View of the overlay to auto-refresh.
     * @return The registered [SkinChangeListener].
     */
    fun autoRefresh(overlayRoot: View): SkinChangeListener {
        val listener = object : SkinChangeListener {
            override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
                refresh(overlayRoot)
            }
        }
        ArsSkinEngine.registerSkinChangeListener(listener)
        return listener
    }

    /**
     * Walk the content View of a [com.google.android.material.snackbar.Snackbar]
     * and apply the current skin.
     *
     * The Snackbar's View hierarchy is:
     * ```
     * Snackbar.SnackbarLayout (root)
     *   └── SnackbarContentLayout (content)
     * ```
     *
     * This method walks the content child (or the root if no children exist).
     *
     * @param snackbarView The result of `snackbar.view`.
     */
    fun refreshSnackbarContent(snackbarView: View) {
        val root = if (snackbarView is android.view.ViewGroup && snackbarView.childCount > 0) {
            snackbarView.getChildAt(0)
        } else {
            snackbarView
        }
        refresh(root)
    }
}
