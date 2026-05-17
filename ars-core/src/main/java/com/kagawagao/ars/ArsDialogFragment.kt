package com.kagawagao.ars

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment

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

    private var wrappedContext: Context? = null

    // ─── Context Wrapping ─────────────────────────────────────────────

    override fun onAttach(context: Context) {
        // Wrap the host Activity's Context before the dialog is created
        wrappedContext = ArsSkinEngine.wrapContext(context)
        super.onAttach(wrappedContext!!)
    }

    // ─── Dialog Creation ──────────────────────────────────────────────

    /**
     * Create the dialog with a skin-aware Context.
     *
     * Subclasses that override this must call `super.onCreateDialog(savedInstanceState)`
     * OR manually wrap the dialog's Context and install the SkinLayoutInflater.
     */
    @CallSuper
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = wrappedContext ?: requireContext()
        val dialog = Dialog(ctx, theme)

        // Install SkinLayoutInflater on the dialog's LayoutInflater
        // so Views inflated from dialog layouts are automatically registered
        val inflater = LayoutInflater.from(ctx)
        val originalFactory = inflater.factory2
        val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, ctx)
        inflater.factory2 = skinFactory

        return dialog
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ArsSkinEngine.registerSkinChangeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ArsSkinEngine.unregisterSkinChangeListener(this)
    }

    // ─── Skin Change Handling ─────────────────────────────────────────

    /**
     * Called after the active skin has changed and the dialog's View tree
     * has been updated.
     *
     * Override this to perform custom post-skin-switch logic.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom behavior
    }

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        // Walk the dialog's window decorView to apply the new skin
        dialog?.window?.decorView?.let { decorView ->
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(decorView, ArsSkinEngine)
        }
        onSkinApplied(previous, current)
    }
}
