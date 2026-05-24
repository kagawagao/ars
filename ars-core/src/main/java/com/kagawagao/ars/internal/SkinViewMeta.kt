package com.kagawagao.ars.internal

import android.view.View
import java.lang.ref.WeakReference

/**
 * Metadata linking a View to the skin resources it uses.
 *
 * Stored in [ArsSkinEngine]'s view registry. Uses [WeakReference]
 * to avoid leaking Views that have been removed from the hierarchy.
 *
 * @property viewRef Weak reference to the View.
 * @property attributes List of resource-to-attribute bindings for this View.
 */
internal data class SkinViewMeta(
    val viewRef: WeakReference<View>,
    val attributes: List<AttrBinding>
)

/**
 * A single attribute-to-resource binding for a View.
 *
 * Captured during XML layout inflation by the [SkinLayoutInflater] (Phase B)
 * and used during skin switch to re-apply resources to Views.
 *
 * @property resId The resource ID referenced by the XML attribute in the host app.
 * @property attributeName The attribute name WITHOUT namespace prefix (e.g., "background").
 * @property resourceType The type of resource (COLOR, DRAWABLE, DIMENSION, etc.).
 */
internal data class AttrBinding(
    val resId: Int,
    val attributeName: String,
    val resourceType: ResourceType
)

/**
 * The type of a skin resource, used to select the correct setter on the View.
 */
internal enum class ResourceType {
    /** Color resource (e.g., `R.color.primary`). */
    COLOR,

    /** Drawable resource (e.g., `R.drawable.logo`). */
    DRAWABLE,

    /** Dimension resource (e.g., `R.dimen.spacing`). */
    DIMENSION,

    /** String resource (e.g., `R.string.title`). */
    STRING,

    /** ColorStateList resource (e.g., selector for text color). */
    COLOR_STATE_LIST,

    /** Text/CharSequence resource. */
    TEXT,

    /** Unknown or unsupported resource type. */
    UNKNOWN
}
