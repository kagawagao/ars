package com.kagawagao.ars.internal

import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Resolves attribute names and determines resource types.
 *
 * Handles namespace stripping ("android:background" → "background").
 * Maintains the registry of supported attributes and their handler types.
 *
 * ## Attribute Name Resolution (FR-P0-05)
 *
 * XML attributes come with namespace prefixes (e.g., "android:background").
 * The resolver strips the prefix and checks the attribute name against the
 * supported set. Legacy deprecated names like "drawableLeft" are mapped to
 * their direction-aware equivalents ("drawableStart") for RTL compliance
 * (FR-P0-06).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object SkinAttributeResolver {

    /**
     * Attributes that ARS can skin by default.
     *
     * Uses direction-aware versions (drawableStart/drawableEnd) instead of
     * deprecated drawableLeft/drawableRight per FR-P0-06.
     */
    val DEFAULT_SUPPORTED_ATTRIBUTES: Set<String> = setOf(
        "background",
        "src",
        "textColor",
        "textColorHint",
        "textSize",
        "tint",
        "progressTint",
        "thumbTint",
        "trackTint",
        "buttonTint",
        "drawableStart",
        "drawableEnd",
        "drawableTop",
        "drawableBottom"
    )

    /** Custom attributes registered by the host app or library modules. */
    private val customAttributes = mutableSetOf<String>()

    /**
     * Map deprecated attribute names to their direction-aware equivalents.
     *
     * "drawableLeft" → "drawableStart" (RTL-aware)
     * "drawableRight" → "drawableEnd" (RTL-aware)
     */
    private val ATTR_ALIASES: Map<String, String> = mapOf(
        "drawableLeft" to "drawableStart",
        "drawableRight" to "drawableEnd"
    )

    // ─── Namespace Stripping ──────────────────────────────────────────

    /**
     * Strip the namespace prefix from an attribute name.
     *
     * "android:background" → "background"
     * "app:cornerRadius" → "cornerRadius"
     * "background" → "background"
     *
     * @param qualifiedName The fully-qualified attribute name (may include namespace prefix).
     * @return The attribute name without namespace.
     */
    fun stripNamespace(qualifiedName: String): String {
        val colonIndex = qualifiedName.indexOf(':')
        return if (colonIndex >= 0) qualifiedName.substring(colonIndex + 1) else qualifiedName
    }

    // ─── Support Checks ───────────────────────────────────────────────

    /**
     * Check if an attribute is supported for skinning.
     *
     * Resolves aliases first (e.g., "drawableLeft" → "drawableStart"),
     * then checks the default and custom attribute sets.
     *
     * @param attributeName The attribute name WITHOUT namespace prefix.
     * @return `true` if this attribute can be skinned.
     */
    fun isSupported(attributeName: String): Boolean {
        val resolved = ATTR_ALIASES[attributeName] ?: attributeName
        return resolved in DEFAULT_SUPPORTED_ATTRIBUTES || resolved in customAttributes
    }

    /**
     * Resolve any deprecated alias to its canonical form.
     *
     * "drawableLeft" → "drawableStart"
     * "drawableRight" → "drawableEnd"
     * "background" → "background" (unchanged)
     *
     * @param attributeName The raw attribute name.
     * @return The canonical (direction-aware) attribute name.
     */
    fun resolveAlias(attributeName: String): String {
        return ATTR_ALIASES[attributeName] ?: attributeName
    }

    // ─── Resource Type Resolution ─────────────────────────────────────

    /**
     * Determine the [ResourceType] for an attribute.
     *
     * Uses a two-step approach:
     * 1. Check the attribute name against known conventions (textColor → COLOR, etc.)
     * 2. If ambiguous (e.g., "background" could be COLOR or DRAWABLE), inspect
     *    the actual resource type via [Resources.getResourceTypeName].
     *
     * @param attributeName The attribute name WITHOUT namespace prefix.
     * @param resId The resource ID referenced in the XML attribute.
     * @param resources The Resources instance for type resolution.
     * @return The [ResourceType] for this attribute binding.
     */
    fun resolveType(attributeName: String, resId: Int, resources: Resources): ResourceType {
        // Step 1: Attribute-name-based heuristics
        when (attributeName) {
            "textColor" -> return ResourceType.COLOR_STATE_LIST
            "textColorHint" -> return ResourceType.COLOR_STATE_LIST
            "tint" -> return ResourceType.COLOR_STATE_LIST
            "progressTint" -> return ResourceType.COLOR_STATE_LIST
            "thumbTint" -> return ResourceType.COLOR_STATE_LIST
            "trackTint" -> return ResourceType.COLOR_STATE_LIST
            "buttonTint" -> return ResourceType.COLOR_STATE_LIST
            "textSize" -> return ResourceType.DIMENSION
            "src" -> return ResourceType.DRAWABLE
            "drawableStart", "drawableEnd", "drawableTop", "drawableBottom" -> return ResourceType.DRAWABLE
        }

        // Step 2: Inspect the actual resource type for ambiguous attributes
        return try {
            val typeName = resources.getResourceTypeName(resId)
            when (typeName) {
                "color" -> ResourceType.COLOR
                "drawable", "mipmap" -> ResourceType.DRAWABLE
                "dimen" -> ResourceType.DIMENSION
                "string" -> ResourceType.STRING
                else -> ResourceType.UNKNOWN
            }
        } catch (e: Resources.NotFoundException) {
            ResourceType.UNKNOWN
        }
    }

    // ─── Custom Attribute Registration ────────────────────────────────

    /**
     * Register a custom attribute to be recognized by the resolver.
     *
     * The attribute name should be provided WITHOUT a namespace prefix
     * (e.g., `"cornerRadius"`, not `"app:cornerRadius"`).
     *
     * @param attributeName The attribute name without namespace prefix.
     * @return `true` if newly registered, `false` if already registered.
     */
    fun registerCustom(attributeName: String): Boolean {
        return customAttributes.add(attributeName)
    }

    /**
     * Unregister a previously registered custom attribute.
     *
     * @param attributeName The attribute name to unregister.
     * @return `true` if the attribute was registered and removed.
     */
    fun unregisterCustom(attributeName: String): Boolean {
        return customAttributes.remove(attributeName)
    }
}
