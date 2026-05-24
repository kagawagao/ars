package com.kagawagao.ars

import android.content.Context
import android.database.DataSetObserver
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListAdapter
import android.widget.SpinnerAdapter
import androidx.annotation.RequiresApi

/**
 * Skin-aware wrapper for [SpinnerAdapter] and [ListAdapter].
 *
 * Wraps an existing adapter so that dropdown Views use skin-aware resources.
 * Works by wrapping the host Context with [SkinContextWrapper] so all
 * `@color/` and `@drawable/` references in dropdown item layouts resolve
 * to the current skin.
 *
 * ## Usage
 *
 * ```kotlin
 * val originalAdapter = ArrayAdapter(context, R.layout.spinner_item, items)
 * spinner.adapter = ArsSpinnerAdapter.wrap(originalAdapter, context)
 *
 * // For AutoCompleteTextView
 * autoComplete.setAdapter(ArsSpinnerAdapter.wrap(originalAdapter, context))
 * ```
 *
 * ## How it works
 *
 * [getView] and [getDropDownView] are delegated to the wrapped adapter.
 * The wrapped adapter's Views use the host Activity's Context which has
 * been wrapped by [SkinContextWrapper] (via [ArsActivity] / [ArsFragment]).
 * No additional Context wrapping is needed — the adapter delegates directly,
 * and the Views created by the original adapter inherit the skin Context.
 *
 * For skin-change tracking of visible dropdown items, use [trackDropdownRoot]
 * in combination with a [SkinChangeListener].
 *
 * ## Limitations
 *
 * - The Spinner/AutoComplete dropdown is created by the system in a separate
 *   PopupWindow whose LayoutInflater is NOT intercepted by [SkinLayoutInflater].
 *   Only resource lookups via `context.resources.getColor()` etc. will use
 *   skin values — View attributes like `android:textColor` in dropdown item
 *   layouts will only pick up skin values if the adapter explicitly sets them
 *   using `context.resources.getColor()`.
 * - For full skin-change support of dropdown items, use [ArsSpinnerAdapter.wrapWithTracking].
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsSpinnerAdapter(
    private val wrapped: SpinnerAdapter,
    private val skinContext: Context
) : BaseAdapter(), SpinnerAdapter, SkinChangeListener {

    companion object {
        /**
         * Wrap an existing [SpinnerAdapter] for skin-aware rendering.
         *
         * @param adapter The original adapter (must implement SpinnerAdapter).
         * @param baseContext The hosting Activity or Fragment context.
         * @return A skin-aware [ArsSpinnerAdapter].
         */
        @JvmStatic
        fun wrap(adapter: SpinnerAdapter, baseContext: Context): ArsSpinnerAdapter {
            return ArsSpinnerAdapter(adapter, ArsSkinEngine.wrapContext(baseContext))
        }

        /**
         * Wrap an existing [SpinnerAdapter] with skin-change tracking enabled.
         *
         * When the active skin changes, the last known dropdown root View tree
         * will be automatically walked via [ArsViewTreeWalker].
         *
         * The caller is responsible for calling [ArsSpinnerAdapter.startTracking]
         * when the dropdown opens and [ArsSpinnerAdapter.stopTracking] when it closes.
         *
         * @param adapter The original adapter.
         * @param baseContext The hosting Activity or Fragment context.
         * @return An [ArsSpinnerAdapter] with tracking enabled.
         */
        @JvmStatic
        fun wrapWithTracking(adapter: SpinnerAdapter, baseContext: Context): ArsSpinnerAdapter {
            val result = ArsSpinnerAdapter(adapter, ArsSkinEngine.wrapContext(baseContext))
            result.trackingEnabled = true
            return result
        }
    }

    // ─── Skin-Change Tracking ─────────────────────────────────────────

    private var trackingEnabled = false
    private var lastDropdownRoot: java.lang.ref.WeakReference<View>? = null

    /**
     * Remember the dropdown root View for skin-change walking.
     *
     * Call this from the dropdown's visibility listener or adapter's
     * [getDropDownView] to enable skin-change auto-refresh.
     *
     * ```kotlin
     * spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
     *     override fun onItemSelected(...) {
     *         // Walk the dropdown list after it appears
     *         spinner.post {
     *             spinner.selectedView?.let { view ->
     *                 // Walk up to find dropdown root
     *                 var p = view.parent
     *                 while (p?.parent != null) p = p.parent
     *                 (adapter as? ArsSpinnerAdapter)?.trackDropdownRoot(p as? View)
     *             }
     *         }
     *     }
     *     override fun onNothingSelected(...) {}
     * })
     * ```
     */
    fun trackDropdownRoot(root: View?) {
        if (root != null) {
            lastDropdownRoot = java.lang.ref.WeakReference(root)
        }
    }

    /**
     * Start tracking skin changes for visible dropdown items.
     *
     * Registers this adapter as a [SkinChangeListener].
     * Call when the dropdown becomes visible.
     */
    fun startTracking() {
        if (trackingEnabled) {
            ArsSkinEngine.registerSkinChangeListener(this)
        }
    }

    /**
     * Stop tracking skin changes.
     *
     * Unregisters this adapter from skin-change notifications.
     * Call when the dropdown is dismissed.
     */
    fun stopTracking() {
        if (trackingEnabled) {
            ArsSkinEngine.unregisterSkinChangeListener(this)
        }
    }

    // ─── Adapter Delegation ───────────────────────────────────────────

    override fun getCount(): Int = wrapped.getCount()
    override fun getItem(position: Int): Any? = wrapped.getItem(position)
    override fun getItemId(position: Int): Long = wrapped.getItemId(position)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return wrapped.getDropDownView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return wrapped.getView(position, convertView, parent)
    }

    override fun getViewTypeCount(): Int = wrapped.getViewTypeCount()
    override fun getItemViewType(position: Int): Int = wrapped.getItemViewType(position)
    override fun isEmpty(): Boolean = wrapped.isEmpty
    override fun hasStableIds(): Boolean = wrapped.hasStableIds()

    override fun registerDataSetObserver(observer: DataSetObserver) = wrapped.registerDataSetObserver(observer)
    override fun unregisterDataSetObserver(observer: DataSetObserver) = wrapped.unregisterDataSetObserver(observer)

    // ─── Skin Change Handling ─────────────────────────────────────────

    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        lastDropdownRoot?.get()?.let { root ->
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(root, ArsSkinEngine)
        }
        onSkinApplied(previous, current)
    }

    /**
     * Called after the active skin has changed and the dropdown's View tree
     * has been walked (if tracking is enabled and a dropdown root is known).
     *
     * Override this to perform custom post-skin-switch logic.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom behavior
    }
}
