package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.kagawagao.ars.internal.ArsViewTreeWalker

/**
 * Base Fragment for ARS-skinning-enabled applications.
 *
 * Extend this instead of [Fragment]. Works identically to [ArsActivity]
 * but scoped to the Fragment's View lifecycle.
 *
 * ## Key behaviors:
 *
 * - **Context wrapping** in [onAttach]: all `getResources()` calls
 *   on the Fragment's Context return skin-aware values.
 * - **LayoutInflater interception** in [onCreateView]: all XML-inflated
 *   Views are automatically registered for skin updates.
 * - **View-tree walking** in [onSkinChanged]: applies the new skin to
 *   all registered Views within this Fragment's hierarchy.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyFragment : ArsFragment() {
 *     override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
 *         return inflater.inflate(R.layout.fragment_my, container, false)
 *     }
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsFragment : Fragment(), SkinChangeListener {

    /** The root View of this Fragment. Set in [onCreateView]. */
    private var fragmentRootView: View? = null

    // ─── Context Wrapping ─────────────────────────────────────────────

    override fun onAttach(context: Context) {
        val wrapped = ArsSkinEngine.wrapContext(context)
        super.onAttach(wrapped)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    /**
     * Create and return the Fragment's View hierarchy.
     *
     * Installs [SkinLayoutInflater] as the LayoutInflater's Factory2
     * before inflating, so all Views in the Fragment's layout are
     * automatically registered for skin updates.
     *
     * Override this and call `super.onCreateView()` — OR override and
     * return your own View, then call [wrapInflater] manually.
     */
    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Wrap the provided inflater with our SkinLayoutInflater
        val wrappedInflater = inflater.cloneInContext(requireContext())
        wrapInflater(wrappedInflater)
        return null  // Subclasses override to inflate their own layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentRootView = view
        ArsSkinEngine.registerSkinChangeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ArsSkinEngine.unregisterSkinChangeListener(this)
        fragmentRootView = null
    }

    // ─── Skin Change Handling ─────────────────────────────────────────

    /**
     * Called after the active skin has changed.
     *
     * Walks the Fragment's View tree starting from the root View set
     * in [onCreateView] and applies the new skin to all registered Views.
     *
     * Override this to perform custom post-skin-switch logic.
     *
     * @param previous The previously active skin, or `null` if default was active.
     * @param current The newly active skin, or `null` if reset to default.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom post-skin-switch behavior
    }

    /**
     * SkinChangeListener implementation.
     */
    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        val root = fragmentRootView ?: return
        ArsViewTreeWalker.walk(root, ArsSkinEngine)
        onSkinApplied(previous, current)
    }

    // ─── Convenience Methods ───────────────────────────────────────────

    /**
     * Re-apply the current skin to this Fragment's View tree.
     *
     * Useful after programmatically adding Views or changing the layout.
     */
    fun refreshSkin() {
        val root = fragmentRootView ?: return
        ArsViewTreeWalker.walk(root, ArsSkinEngine)
    }

    /**
     * Install the skin-aware LayoutInflater Factory2 on the given inflater.
     *
     * Call this if you need to use a custom inflater instead of the one
     * provided in [onCreateView].
     *
     * @param inflater The LayoutInflater to wrap.
     */
    protected fun wrapInflater(inflater: LayoutInflater) {
        val originalFactory = inflater.factory2
        val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, requireContext())
        inflater.factory2 = skinFactory
    }
}
