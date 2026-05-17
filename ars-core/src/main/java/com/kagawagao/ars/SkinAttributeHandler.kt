package com.kagawagao.ars

import android.view.View
import android.content.res.Resources

/**
 * Handler interface for applying a skin resource to a [View].
 *
 * Implement this to support custom View attributes in the skinning system.
 * Register handlers via [ArsSkinEngine.registerAttributeHandler].
 *
 * Example for a library-defined attribute:
 * ```kotlin
 * ArsSkinEngine.registerAttributeHandler("cornerRadius") { view, resId, resources ->
 *     (view as? CustomView)?.cornerRadius = resources.getDimension(resId)
 * }
 * ```
 */
fun interface SkinAttributeHandler {
    /**
     * Apply a resource value to a View.
     *
     * Called during skin switch and View creation when a skin is active.
     *
     * @param view The target View. Cast to the expected type as needed.
     * @param resId The resource ID to resolve from [resources].
     * @param resources The current skin-aware Resources instance. Use this
     *                  for resource resolution to get skin-overridden values.
     */
    fun apply(view: View, resId: Int, resources: Resources)
}
