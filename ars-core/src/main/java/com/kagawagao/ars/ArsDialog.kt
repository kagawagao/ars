package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDialog
import com.kagawagao.ars.internal.SkinLayoutInflater

/**
 * Base Dialog for ARS-skinning-enabled applications.
 *
 * Extend this instead of [android.app.Dialog] or [AppCompatDialog].
 * Automatically wraps the dialog's Context with skin-aware [SkinResources],
 * installs a [SkinLayoutInflater] Factory2, and listens for skin changes
 * to refresh the dialog's View tree in-place.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyDialog(context: Context) : ArsDialog(context) {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.dialog_my)
 *     }
 * }
 * ```
 *
 * ## AlertDialog Alternative
 *
 * For AlertDialog-style dialogs (title, buttons), prefer the extension:
 * ```kotlin
 * val dialog = requireContext().createSkinnedAlertDialog {
 *     setTitle("标题")
 *     setView(R.layout.dialog_content)
 *     setPositiveButton("确定", null)
 * }
 * dialog.show()
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsDialog : AppCompatDialog, SkinChangeListener {

    companion object {
        private const val TAG = "ARS_Dialog"
    }

    private var isSkinned = false

    // ─── Constructors ─────────────────────────────────────────────────

    constructor(context: Context) : super(ArsSkinEngine.wrapContext(context))
    constructor(context: Context, themeResId: Int) : super(ArsSkinEngine.wrapContext(context), themeResId)

    // ─── Lifecycle ────────────────────────────────────────────────────

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Factory2 is now installed in setContentView overrides,
        // which guarantees it's present on the exact LayoutInflater
        // instance used during inflation — not a potentially stale
        // instance captured at onCreate time.
        isSkinned = true
    }

    override fun onStart() {
        super.onStart()
        // Register the dialog's decorView so the engine walks it centrally
        window?.decorView?.let { ArsSkinEngine.registerWindow(it) }
        // Register for skin change notifications (for onSkinApplied callback)
        ArsSkinEngine.registerSkinChangeListener(this)
        // Apply initial skin to Views inflated before listener was registered
        applySkinNow()
    }

    override fun onStop() {
        super.onStop()
        window?.decorView?.let { ArsSkinEngine.unregisterWindow(it) }
        ArsSkinEngine.unregisterSkinChangeListener(this)
    }

    // ─── SkinFactory Installation ─────────────────────────────────────

    /**
     * Install the skin-aware LayoutInflater Factory2 on the LayoutInflater
     * that [setContentView] will use. Called immediately before each
     * inflation to guarantee Factory2 is present regardless of whether
     * [LayoutInflater.from] returns a cached or new instance.
     */
    private fun ensureSkinFactory() {
        val inflater = LayoutInflater.from(context)
        if (inflater.factory2 is SkinLayoutInflater) return
        val originalFactory = inflater.factory2
        val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, context)
        inflater.factory2 = skinFactory
    }

    // ─── setContentView Overrides ─────────────────────────────────────

    override fun setContentView(layoutResID: Int) {
        ensureSkinFactory()
        super.setContentView(layoutResID)
    }

    override fun setContentView(view: View) {
        ensureSkinFactory()
        super.setContentView(view)
    }

    // ─── Skin Application ─────────────────────────────────────────────

    /**
     * Immediately apply the current skin to this dialog's View tree.
     *
     * Called in [onStart] to catch Views that were created before
     * the skin-change listener was registered.
     */
    private fun applySkinNow() {
        window?.decorView?.let { decorView ->
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(decorView, ArsSkinEngine)
        }
    }

    // ─── Window Override (for AlertDialog-style) ──────────────────────

    /**
     * Override [getWindow] to allow subclasses like [ArsAlertDialogBuilder]
     * to customize the Window before it's attached.
     *
     * This is a no-op for [ArsDialog] but provided for consistency
     * with the AlertDialog pattern.
     */
    protected open fun configureWindow(window: Window) {
        // Subclasses can override to set window features, flags, etc.
    }

    // ─── Skin Change Handling ─────────────────────────────────────────

    /**
     * Called after the active skin or theme has changed. Walks the dialog's
     * decorView to update all registered Views with the current Resources.
     *
     * Override to add custom chrome updates (window background, title color).
     * Call `super.onSkinApplied(previous, current)` to ensure the tree-walk
     * runs before your custom logic.
     *
     * ```kotlin
     * override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
     *     super.onSkinApplied(previous, current)
     *     setWindowBackground(R.color.theme_background)
     * }
     * ```
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        refreshChrome()
    }

    /**
     * Walk the dialog's decorView and apply current skin/theme to all
     * registered Views. Called by [onSkinApplied] on every change.
     */
    private fun refreshChrome() {
        window?.decorView?.let { decorView ->
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(decorView, ArsSkinEngine)
        }
    }

    // ─── Chrome Helpers ────────────────────────────────────────────────

    /**
     * Set the dialog window's background drawable from a color resource.
     *
     * Uses skin-aware [Resources] so the color respects the active skin
     * and current theme mode. Call from [onSkinApplied].
     *
     * @param colorResId A `R.color.*` resource ID (e.g., `R.color.theme_background`).
     */
    protected fun setWindowBackgroundColor(colorResId: Int) {
        val res = context.resources
        window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(res.getColor(colorResId, null))
        )
    }

    /**
     * Set the title text color from a color resource.
     *
     * Uses skin-aware [Resources]. Call from [onSkinApplied].
     * Works with AppCompat's title bar (finds `androidx.appcompat.R.id.title`).
     *
     * @param colorResId A `R.color.*` resource ID (e.g., `R.color.theme_text`).
     */
    protected fun setTitleTextColor(colorResId: Int) {
        val titleView = window?.findViewById<android.widget.TextView>(
            getTitleViewId()
        ) ?: return
        val res = context.resources
        titleView.setTextColor(res.getColor(colorResId, null))
    }

    /**
     * Resolve the title view ID. AppCompat uses `support_action_bar_title`
     * or a TextView with id `title` in the decor.
     */
    private fun getTitleViewId(): Int {
        return try {
            val field = androidx.appcompat.R.id::class.java.getField("title")
            field.getInt(null)
        } catch (_: Exception) {
            // Fallback: search the decorView for the title TextView
            android.R.id.title
        }
    }

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        Log.d(TAG, "onSkinChanged: ${this.javaClass.simpleName}, " +
            "hasDecor=${window?.decorView != null}, " +
            "prev=${previous?.name ?: "null"}, cur=${current?.name ?: "null"}")
        onSkinApplied(previous, current)
    }
}
