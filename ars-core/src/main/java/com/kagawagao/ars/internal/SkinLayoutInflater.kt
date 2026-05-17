package com.kagawagao.ars.internal

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsSkinEngine
import java.lang.ref.WeakReference

/**
 * [LayoutInflater.Factory2] that intercepts View creation during XML inflation.
 *
 * Delegates to AppCompat's Factory2 first to preserve AppCompat View substitution
 * (e.g., automatic `AppCompatTextView` for `<TextView>`). Then scans the
 * [AttributeSet] to build [SkinViewMeta] for each View, recording which
 * attributes reference skin-eligible resources.
 *
 * This is the **key component** that achieves FR-P0-02 (automatic View skinning
 * without manual `applySkin()` calls). Every View inflated from XML is
 * automatically registered with [ArsSkinEngine] for future skin updates.
 *
 * @property delegate The next Factory2 in the chain (typically AppCompat's).
 *                    Called first so AppCompat substitutions happen before we record metadata.
 * @property engine The [ArsSkinEngine] for registering View metadata.
 * @property context The Context for reflection-based View creation fallback.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class SkinLayoutInflater(
    private val delegate: LayoutInflater.Factory2?,
    private val engine: ArsSkinEngine,
    private val context: Context
) : LayoutInflater.Factory2 {

    /**
     * Intercept View creation with parent context.
     *
     * 1. Delegate to the original Factory2 (AppCompat) for View creation and substitution.
     * 2. If delegate returns null, attempt reflection-based creation.
     * 3. Scan the AttributeSet and record skin metadata for the created View.
     *
     * @param parent The parent View that the created View will be attached to, or null.
     * @param name The fully-qualified class name of the View (e.g., "android.widget.TextView").
     * @param context The Context for View creation.
     * @param attrs The XML attributes for this View.
     * @return The created View, or null if creation failed.
     */
    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        // 1. Let the delegate create the View (AppCompat substitutions, etc.)
        val view = delegate?.onCreateView(parent, name, context, attrs)
            ?: createView(name, context, attrs)

        // 2. Record skin metadata for this View from the AttributeSet
        if (view != null) {
            recordViewMeta(view, attrs)
        }

        return view
    }

    /**
     * Intercept View creation without parent context.
     *
     * Delegates to [onCreateView] with parent = null.
     */
    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    /**
     * Scan the [AttributeSet] and register skin metadata for the View.
     *
     * For each attribute in the set:
     * 1. Strip the namespace prefix (e.g., "android:background" → "background").
     * 2. Check if the attribute is supported for skinning.
     * 3. If supported and has a resource reference (not a literal value), record an [AttrBinding].
     *
     * Only Views with at least one skin-eligible binding are registered.
     *
     * @param view The inflated View.
     * @param attrs The XML attributes.
     */
    private fun recordViewMeta(view: View, attrs: AttributeSet) {
        val bindings = mutableListOf<AttrBinding>()

        for (i in 0 until attrs.attributeCount) {
            val qualifiedName = attrs.getAttributeName(i)  // e.g., "android:background"
            val rawAttrName = SkinAttributeResolver.stripNamespace(qualifiedName)  // → "background"

            // Resolve deprecated aliases to their canonical form
            val attrName = SkinAttributeResolver.resolveAlias(rawAttrName)

            if (!SkinAttributeResolver.isSupported(attrName)) continue

            // Only record if the attribute references a resource (not a literal value)
            // Literals like "#FF0000" or "10dp" have resId == 0
            val resId = attrs.getAttributeResourceValue(i, 0)
            if (resId == 0) continue

            val resourceType = SkinAttributeResolver.resolveType(attrName, resId, context.resources)
            bindings.add(AttrBinding(resId, attrName, resourceType))
        }

        if (bindings.isNotEmpty()) {
            val meta = SkinViewMeta(WeakReference(view), bindings)
            engine.registerView(view, meta)

            // Immediately apply the active skin to this newly created View
            // so that dynamically added Views are skin-aware without waiting
            // for the next explicit skin switch or refreshSkin() call.
            if (engine.activeSkin != null) {
                engine.applySkinToView(view, meta)
            }
        }
    }

    /**
     * Fallback View creation via reflection.
     *
     * Used when the delegate Factory2 returns null (e.g., for Views that
     * AppCompat doesn't substitute). Follows the standard LayoutInflater
     * pattern: try the fully-qualified name first, then prepend "android.widget.".
     *
     * @param name The class name of the View.
     * @param context The Context for construction.
     * @param attrs The XML attributes.
     * @return The created View, or null if creation failed.
     */
    @Suppress("SwallowedException")
    private fun createView(name: String, context: Context, attrs: AttributeSet): View? {
        return try {
            val clazz = Class.forName(
                if (name.contains('.')) name else "android.widget.$name",
                false,
                context.classLoader
            )
            val constructor = clazz.getConstructor(Context::class.java, AttributeSet::class.java)
            constructor.newInstance(context, attrs) as View
        } catch (e: Exception) {
            // Graceful fallback — delegate couldn't create and we can't either
            null
        }
    }
}
