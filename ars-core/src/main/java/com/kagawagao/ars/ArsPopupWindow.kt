package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi

/**
 * PopupWindow that automatically participates in ARS skinning.
 *
 * The content View's Context is wrapped with [SkinContextWrapper] so all
 * resource lookups return skin-aware values. When the active skin changes,
 * the popup's content View tree is automatically walked and updated —
 * no manual [refreshSkin] call needed.
 *
 * ## Usage
 *
 * ```kotlin
 * val popup = ArsPopupWindow(requireContext()).apply {
 *     contentView = LayoutInflater.from(skinContext).inflate(R.layout.popup_menu, null)
 *     width = WRAP_CONTENT
 *     height = WRAP_CONTENT
 *     isOutsideTouchable = true
 *     showAsDropDown(anchor)
 * }
 * ```
 *
 * ## Comparison with manual wrapping
 *
 * Before (semi-automatic):
 * ```kotlin
 * val ctx = ArsSkinEngine.wrapContext(requireContext())
 * val view = LayoutInflater.from(ctx).inflate(R.layout.popup, null)
 * PopupWindow(view, ...).showAsDropDown(anchor)
 * // ⚠️ Skin change while showing → stale content
 * ```
 *
 * After (automatic):
 * ```kotlin
 * ArsPopupWindow(requireContext()).apply {
 *     contentView = LayoutInflater.from(skinContext).inflate(R.layout.popup, null)
 *     showAsDropDown(anchor)
 *     // ✅ Skin change → auto-refreshed
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsPopupWindow : PopupWindow, SkinChangeListener {

    companion object {
        private const val TAG = "ARS_PopupWindow"
    }

    /**
     * The wrapped Context that returns skin-aware Resources.
     *
     * Use this instead of the raw Context when inflating layouts
     * or constructing Views for this popup's content.
     */
    val skinContext: Context

    private var isShowing = false

    // ─── Constructors ─────────────────────────────────────────────────

    /**
     * Create an [ArsPopupWindow] with skin-aware Context.
     *
     * @param baseContext The context from the hosting Activity or Fragment.
     */
    constructor(baseContext: Context) : super(ArsSkinEngine.wrapContext(baseContext)) {
        skinContext = ArsSkinEngine.wrapContext(baseContext)
    }

    /**
     * Create an [ArsPopupWindow] with explicit content View and skin-aware Context.
     *
     * @param contentView The content View to display.
     * @param baseContext The context from the hosting Activity or Fragment.
     * @param width Desired width, or [WRAP_CONTENT].
     * @param height Desired height, or [WRAP_CONTENT].
     * @param focusable Whether the popup can receive focus.
     */
    constructor(
        contentView: View?,
        baseContext: Context,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        focusable: Boolean = false
    ) : super(contentView, width, height, focusable) {
        skinContext = ArsSkinEngine.wrapContext(baseContext)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    override fun showAsDropDown(anchor: View?) {
        super.showAsDropDown(anchor)
        if (!isShowing) {
            // Register content root so engine walks it on skin/theme changes
            contentView?.let { ArsSkinEngine.registerWindow(it) }
            // Apply initial skin before first show
            contentView?.let { walkContent(it) }
            ArsSkinEngine.registerSkinChangeListener(this)
            isShowing = true
        }
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int) {
        super.showAsDropDown(anchor, xoff, yoff)
        if (!isShowing) {
            contentView?.let { ArsSkinEngine.registerWindow(it) }
            contentView?.let { walkContent(it) }
            ArsSkinEngine.registerSkinChangeListener(this)
            isShowing = true
        }
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        if (!isShowing) {
            contentView?.let { ArsSkinEngine.registerWindow(it) }
            contentView?.let { walkContent(it) }
            ArsSkinEngine.registerSkinChangeListener(this)
            isShowing = true
        }
    }

    override fun showAtLocation(parent: View?, gravity: Int, x: Int, y: Int) {
        super.showAtLocation(parent, gravity, x, y)
        if (!isShowing) {
            contentView?.let { ArsSkinEngine.registerWindow(it) }
            contentView?.let { walkContent(it) }
            ArsSkinEngine.registerSkinChangeListener(this)
            isShowing = true
        }
    }

    override fun dismiss() {
        if (isShowing) {
            contentView?.let { ArsSkinEngine.unregisterWindow(it) }
            ArsSkinEngine.unregisterSkinChangeListener(this)
            isShowing = false
        }
        super.dismiss()
    }

    // ─── Skin Change Handling ─────────────────────────────────────────

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        // Engine walks all windows centrally via walkAllWindows().
        Log.d(TAG, "onSkinChanged: ${this.javaClass.simpleName}, " +
            "isShowing=$isShowing, " +
            "prev=${previous?.name ?: "null"}, cur=${current?.name ?: "null"}")
        onSkinApplied(previous, current)
    }

    /**
     * Called after the active skin has changed and the popup's View tree
     * has been updated.
     *
     * Override this to perform custom post-skin-switch logic.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom post-skin-switch behavior
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    private fun walkContent(root: View) {
        com.kagawagao.ars.internal.ArsViewTreeWalker.walk(root, ArsSkinEngine)
    }
}
