package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDialog

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

    private var isSkinned = false

    // ─── Constructors ─────────────────────────────────────────────────

    constructor(context: Context) : super(ArsSkinEngine.wrapContext(context))
    constructor(context: Context, themeResId: Int) : super(ArsSkinEngine.wrapContext(context), themeResId)

    // ─── Lifecycle ────────────────────────────────────────────────────

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSkinFactory()
        isSkinned = true
    }

    override fun onStart() {
        super.onStart()
        // Register after the window is attached so onSkinChanged
        // can safely walk the decorView
        ArsSkinEngine.registerSkinChangeListener(this)
        // Apply initial skin to Views that may have been inflated
        // before the listener was registered
        applySkinNow()
    }

    override fun onStop() {
        super.onStop()
        ArsSkinEngine.unregisterSkinChangeListener(this)
    }

    // ─── SkinFactory Installation ─────────────────────────────────────

    /**
     * Install the skin-aware LayoutInflater Factory2 on this dialog's
     * LayoutInflater so that all XML-inflated Views are automatically
     * registered for skin updates.
     */
    private fun installSkinFactory() {
        val inflater = LayoutInflater.from(context)
        val originalFactory = inflater.factory2
        val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, context)
        inflater.factory2 = skinFactory
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
     * Called after the active skin has changed and the dialog's View tree
     * has been updated.
     *
     * Override this to perform custom post-skin-switch logic.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom post-skin-switch behavior
    }

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        // Walk the dialog's decorView to apply the new skin
        window?.decorView?.let { decorView ->
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(decorView, ArsSkinEngine)
        }
        onSkinApplied(previous, current)
    }
}
