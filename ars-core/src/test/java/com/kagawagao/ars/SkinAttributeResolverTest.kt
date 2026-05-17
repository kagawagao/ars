package com.kagawagao.ars

import com.kagawagao.ars.internal.ResourceType
import com.kagawagao.ars.internal.SkinAttributeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SkinAttributeResolver].
 *
 * Covers namespace stripping, attribute support checks, alias resolution,
 * resource type detection, and custom attribute registration.
 */
class SkinAttributeResolverTest {

    // ─── Namespace Stripping (FR-P0-05) ──────────────────────────────

    @Test
    fun stripNamespace_removesAndroidPrefix() {
        val result = SkinAttributeResolver.stripNamespace("android:background")
        assertEquals("background", result)
    }

    @Test
    fun stripNamespace_handlesNoNamespace() {
        val result = SkinAttributeResolver.stripNamespace("background")
        assertEquals("background", result)
    }

    @Test
    fun stripNamespace_handlesOtherPrefixes() {
        val result = SkinAttributeResolver.stripNamespace("app:cornerRadius")
        assertEquals("cornerRadius", result)
    }

    @Test
    fun stripNamespace_handlesEmptyPrefix() {
        val result = SkinAttributeResolver.stripNamespace(":background")
        assertEquals("background", result)
    }

    @Test
    fun stripNamespace_handlesMultipleColons() {
        // Only the first colon is used as the namespace separator
        val result = SkinAttributeResolver.stripNamespace("http:scheme:name")
        assertEquals("scheme:name", result)
    }

    // ─── Supported Attribute Checks ──────────────────────────────────

    @Test
    fun isSupported_returnsTrueForKnownAttributes() {
        // All default attributes should be recognized
        assertTrue(SkinAttributeResolver.isSupported("background"))
        assertTrue(SkinAttributeResolver.isSupported("src"))
        assertTrue(SkinAttributeResolver.isSupported("textColor"))
        assertTrue(SkinAttributeResolver.isSupported("textColorHint"))
        assertTrue(SkinAttributeResolver.isSupported("textSize"))
        assertTrue(SkinAttributeResolver.isSupported("tint"))
        assertTrue(SkinAttributeResolver.isSupported("progressTint"))
        assertTrue(SkinAttributeResolver.isSupported("thumbTint"))
        assertTrue(SkinAttributeResolver.isSupported("trackTint"))
        assertTrue(SkinAttributeResolver.isSupported("buttonTint"))
        assertTrue(SkinAttributeResolver.isSupported("drawableStart"))
        assertTrue(SkinAttributeResolver.isSupported("drawableEnd"))
        assertTrue(SkinAttributeResolver.isSupported("drawableTop"))
        assertTrue(SkinAttributeResolver.isSupported("drawableBottom"))
    }

    @Test
    fun isSupported_returnsFalseForUnknown() {
        assertFalse(SkinAttributeResolver.isSupported("unknownAttribute"))
        assertFalse(SkinAttributeResolver.isSupported("padding"))
        assertFalse(SkinAttributeResolver.isSupported("margin"))
        assertFalse(SkinAttributeResolver.isSupported("gravity"))
    }

    @Test
    fun isSupported_resolvesAliases() {
        // drawableLeft should map to drawableStart and be supported
        assertTrue(SkinAttributeResolver.isSupported("drawableLeft"))

        // drawableRight should map to drawableEnd and be supported
        assertTrue(SkinAttributeResolver.isSupported("drawableRight"))
    }

    @Test
    fun resolveAlias_mapsDeprecatedNames() {
        assertEquals("drawableStart", SkinAttributeResolver.resolveAlias("drawableLeft"))
        assertEquals("drawableEnd", SkinAttributeResolver.resolveAlias("drawableRight"))
    }

    @Test
    fun resolveAlias_returnsUnchangedForCanonicalNames() {
        assertEquals("background", SkinAttributeResolver.resolveAlias("background"))
        assertEquals("src", SkinAttributeResolver.resolveAlias("src"))
        assertEquals("drawableStart", SkinAttributeResolver.resolveAlias("drawableStart"))
    }

    // ─── Custom Attribute Registration ───────────────────────────────

    @Test
    fun registerCustom_addsAttributeToSupportedSet() {
        assertFalse(SkinAttributeResolver.isSupported("cornerRadius"))
        SkinAttributeResolver.registerCustom("cornerRadius")
        assertTrue(SkinAttributeResolver.isSupported("cornerRadius"))
        // Cleanup
        SkinAttributeResolver.unregisterCustom("cornerRadius")
    }

    @Test
    fun registerCustom_returnsTrue_whenNewAttribute() {
        assertTrue(SkinAttributeResolver.registerCustom("myCustomAttr"))
        // Cleanup
        SkinAttributeResolver.unregisterCustom("myCustomAttr")
    }

    @Test
    fun registerCustom_returnsFalse_whenDuplicate() {
        SkinAttributeResolver.registerCustom("myCustomAttr")
        assertFalse(SkinAttributeResolver.registerCustom("myCustomAttr"))
        // Cleanup
        SkinAttributeResolver.unregisterCustom("myCustomAttr")
    }

    @Test
    fun unregisterCustom_removesAttributeFromSupportedSet() {
        SkinAttributeResolver.registerCustom("myCustomAttr")
        assertTrue(SkinAttributeResolver.isSupported("myCustomAttr"))
        assertTrue(SkinAttributeResolver.unregisterCustom("myCustomAttr"))
        assertFalse(SkinAttributeResolver.isSupported("myCustomAttr"))
    }

    @Test
    fun unregisterCustom_returnsFalse_whenNotRegistered() {
        assertFalse(SkinAttributeResolver.unregisterCustom("nonexistent"))
    }

    // ─── Default Attribute Set Integrity ─────────────────────────────

    @Test
    fun defaultSupportedAttributes_isNotEmpty() {
        assertTrue(SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.isNotEmpty())
    }

    @Test
    fun defaultSupportedAttributes_containsDirectionAwareDrawables() {
        assertTrue(SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.contains("drawableStart"))
        assertTrue(SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.contains("drawableEnd"))
        // Deprecated names should NOT be in the default set
        assertFalse(SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.contains("drawableLeft"))
        assertFalse(SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.contains("drawableRight"))
    }
}
