package com.kagawagao.ars

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import com.kagawagao.ars.internal.ArsViewTreeWalker

/**
 * Base DialogFragment for ARS-skinning-enabled applications.
 *
 * Extend this instead of [DialogFragment]. Wraps the dialog's Context with
 * skin-aware [SkinResources], installs [SkinLayoutInflater] on the dialog's
 * LayoutInflater, and automatically walks the dialog's View tree on skin
 * changes.
 *
 * ## Why a separate base class?
 *
 * [DialogFragment] has its own [android.view.Window] and Context —
 * separate from the hosting Activity's. Views inflated inside the dialog
 * won't automatically inherit the Activity's [SkinContextWrapper].
 * [ArsDialogFragment] ensures the dialog's resource lookups are also
 * skin-aware.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyDialog : ArsDialogFragment() {
 *     override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
 *         return inflater.inflate(R.layout.dialog_my, container, false)
 *     }
 * }
 * ```
 *
 * ## Other Dialog Patterns
 *
 * ### AlertDialog / MaterialAlertDialogBuilder
 * ```kotlin
 * val ctx = ArsSkinEngine.wrapContext(requireContext())
 * AlertDialog.Builder(ctx)
 *     .setView(layout)
 *     .show()
 *     .also { dialog ->
 *         // Apply skin after show (dialog's Views are now attached)
 *         dialog.window?.decorView?.let { ArsViewTreeWalker.walk(it, ArsSkinEngine) }
 *     }
 * ```
 *
 * ### BottomSheetDialogFragment
 * Extend [ArsDialogFragment] directly — BottomSheetDialogFragment is a
 * subclass of DialogFragment, so this base class handles both.
 *
 * ### PopupWindow
 * ```kotlin
 * val ctx = ArsSkinEngine.wrapContext(baseContext)
 * val contentView = LayoutInflater.from(ctx).inflate(R.layout.popup, null)
 * PopupWindow(contentView, ...).apply {
 *     showAtLocation(anchor, Gravity.CENTER, 0, 0)
 *     // Content is already skinned — SkinResources handles resource lookups.
 *     // No manual refreshSkin() needed unless the skin switches while showing.
 * }
 * ```
 *
 * ### Manual Dialog
 * Same pattern as AlertDialog — wrap context, inflate with it, show, then
 * walk the decorView once for initial skin application.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsDialogFragment : DialogFragment(), SkinChangeListener {

    companion object {
        private const val TAG = "ARS_DialogFragment"
    }

    private var wrappedContext: Context? = null

    // ─── Context Wrapping ─────────────────────────────────────────────

    override fun onAttach(context: Context) {
        // Wrap the host Activity's Context before the dialog is created
        wrappedContext = ArsSkinEngine.wrapContext(context)
        super.onAttach(wrappedContext!!)
    }

    override fun onDetach() {
        wrappedContext = null
        super.onDetach()
    }

    // ─── Dialog Creation ──────────────────────────────────────────────

    /**
     * Create the dialog with a skin-aware Context.
     *
     * Subclasses that override this must call `super.onCreateDialog(savedInstanceState)`
     * OR manually wrap the dialog's Context and install the SkinLayoutInflater.
     *
     * Sets Factory2 directly on the LayoutInflater that [setContentView]
     * will use — not on a clone. This is critical: a cloned inflater is
     * discarded immediately, leaving the actual inflater untouched and
     * dialog Views without [SkinViewMeta].
     */
    @CallSuper
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = wrappedContext ?: requireContext()
        val dialog = Dialog(ctx, theme)

        // Set Factory2 directly on the LayoutInflater the dialog will use.
        // Do NOT clone — the clone is discarded and has no effect on actual inflation.
        val inflater = LayoutInflater.from(ctx)
        if (inflater.factory2 !is com.kagawagao.ars.internal.SkinLayoutInflater) {
            val originalFactory = inflater.factory2
            val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, ctx)
            inflater.factory2 = skinFactory
        }

        return dialog
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    /**
     * Install the skin-aware LayoutInflater Factory2 on the inflater
     * used by [onCreateView] for content View inflation.
     *
     * Without this, content Views in the dialog are inflated without
     * [SkinLayoutInflater] interception, so they lack [SkinViewMeta]
     * and won't be updated on theme/skin changes.
     */
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState).cloneInContext(requireContext())
        // Idempotency: do not double-wrap
        if (inflater.factory2 !is com.kagawagao.ars.internal.SkinLayoutInflater) {
            val originalFactory = inflater.factory2
            val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, requireContext())
            inflater.factory2 = skinFactory
        }
        return inflater
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ArsSkinEngine.registerSkinChangeListener(this)
        // Apply current theme to newly created Views immediately.
        // This is essential when the dialog is shown after a theme switch
        // has already occurred — Views are inflated but never walked.
        ArsViewTreeWalker.walk(view, ArsSkinEngine)
    }

    override fun onStart() {
        super.onStart()
        // Register the dialog's decorView so the engine walks it
        // on every skin/theme change — no per-class walk needed.
        dialog?.window?.decorView?.let { ArsSkinEngine.registerWindow(it) }
    }

    override fun onStop() {
        super.onStop()
        dialog?.window?.decorView?.let { ArsSkinEngine.unregisterWindow(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ArsSkinEngine.unregisterSkinChangeListener(this)
    }

    // ─── Skin Change Handling ─────────────────────────────────────────

    /**
     * Called after the active skin or theme has changed. Walks the dialog's
     * decorView to update all registered Views and chrome elements.
     *
     * Override to add custom chrome updates (window background, title color).
     * Call `super.onSkinApplied(previous, current)` first.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        dialog?.window?.decorView?.let { decorView ->
            ArsViewTreeWalker.walk(decorView, ArsSkinEngine)
        }
    }

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        Log.d(TAG, "onSkinChanged: ${this.javaClass.simpleName}, " +
            "hasDecor=${dialog?.window?.decorView != null}, " +
            "prev=${previous?.name ?: "null"}, cur=${current?.name ?: "null"}")
        onSkinApplied(previous, current)
    }
}
