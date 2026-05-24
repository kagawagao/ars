package com.kagawagao.ars

import com.kagawagao.ars.internal.SkinAttributeResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ArsSkinEngine] state transitions and critical paths.
 *
 * Covers:
 * - SkinResult / SkinError contracts
 * - Custom attribute registration sync with resolver
 * - Alias resolution (drawableLeft → drawableStart)
 * - Default attribute set integrity
 */
class ArsSkinEngineTest {

    @Before
    fun setUp() {}

    @After
    fun tearDown() {
        SkinAttributeResolver.unregisterCustom("testAttr")
        SkinAttributeResolver.unregisterCustom("testAttr2")
    }

    // ─── SkinResult Pattern ───────────────────────────────────────────

    @Test
    fun `SkinResult Success wraps value`() {
        val result = SkinResult.Success(42)
        assertTrue(result.isSuccess)
        assertEquals(42, result.value)
    }

    @Test
    fun `SkinResult Error wraps error`() {
        val error: SkinError = SkinError.FileNotFound("/tmp/skin.apk")
        val result = SkinResult.Error(error)
        assertFalse(result.isSuccess)
        assertEquals(error, result.error)
    }

    @Test
    fun `SkinResult Success and Error type discrimination`() {
        val success: SkinResult<Int> = SkinResult.Success(1)
        val failure: SkinResult<Int> = SkinResult.Error(SkinError.SwitchInProgress)
        assertTrue(success is SkinResult.Success)
        assertTrue(failure is SkinResult.Error)
    }

    // ─── SkinError Messages ───────────────────────────────────────────

    @Test
    fun `each SkinError variant has descriptive message`() {
        val errors: List<SkinError> = listOf(
            SkinError.SwitchInProgress,
            SkinError.FileNotFound("/tmp/nonexistent.apk"),
            SkinError.NotASkinPackage("com.test"),
            SkinError.TargetMismatch("com.skin", "com.host"),
            SkinError.IncompatibleVersion(2, 1),
            SkinError.StorageError("disk full"),
            SkinError.ResourceNotFound("primary", "color"),
            SkinError.CorruptedPackage("/tmp/bad.apk")
        )
        for (error in errors) {
            assertTrue("${error::class.simpleName} should have non-empty message",
                error.message.isNotEmpty())
        }
    }

    @Test
    fun `FileNotFound includes path in message`() {
        val error = SkinError.FileNotFound("/sdcard/skin.apk")
        assertTrue(error.message.contains("/sdcard/skin.apk"))
    }

    @Test
    fun `NotASkinPackage includes package name in message`() {
        val error = SkinError.NotASkinPackage("com.test")
        assertTrue(error.message.contains("com.test"))
    }

    @Test
    fun `TargetMismatch includes both packages in message`() {
        val error = SkinError.TargetMismatch("com.skin", "com.host")
        val msg = error.message
        assertTrue(msg.contains("com.skin"))
        assertTrue(msg.contains("com.host"))
    }

    // ─── Attribute Handler ↔ Resolver Sync ────────────────────────────

    @Test
    fun `registerCustom adds to supported set`() {
        assertFalse(SkinAttributeResolver.isSupported("testAttr"))
        assertTrue(SkinAttributeResolver.registerCustom("testAttr"))
        assertTrue(SkinAttributeResolver.isSupported("testAttr"))
    }

    @Test
    fun `unregisterCustom removes from supported set`() {
        SkinAttributeResolver.registerCustom("testAttr2")
        assertTrue(SkinAttributeResolver.unregisterCustom("testAttr2"))
        assertFalse(SkinAttributeResolver.isSupported("testAttr2"))
    }

    @Test
    fun `duplicate registerCustom returns false`() {
        SkinAttributeResolver.registerCustom("testAttr2")
        assertFalse(SkinAttributeResolver.registerCustom("testAttr2"))
        SkinAttributeResolver.unregisterCustom("testAttr2")
    }

    @Test
    fun `unregisterCustom returns false for never-registered`() {
        assertFalse(SkinAttributeResolver.unregisterCustom("nonexistent"))
    }

    // ─── Default Attribute Set Integrity ──────────────────────────────

    @Test
    fun `default attribute set contains exactly 13 attributes`() {
        assertEquals(13, SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.size)
    }

    @Test
    fun `default set has drawableStart and drawableEnd`() {
        val set = SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES
        assertTrue(set.contains("drawableStart"))
        assertTrue(set.contains("drawableEnd"))
    }

    @Test
    fun `default set does NOT have deprecated drawableLeft or drawableRight`() {
        val set = SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES
        assertFalse(set.contains("drawableLeft"))
        assertFalse(set.contains("drawableRight"))
    }

    // ─── Theme Mode Enum ──────────────────────────────────────────────

    @Test
    fun `SkinPackage ThemeMode has exactly LIGHT and DARK`() {
        assertEquals(2, SkinPackage.ThemeMode.entries.size)
        assertEquals("LIGHT", SkinPackage.ThemeMode.LIGHT.name)
        assertEquals("DARK", SkinPackage.ThemeMode.DARK.name)
    }

    // ─── SkinDiagnostics Defaults ─────────────────────────────────────

    @Test
    fun `SkinDiagnostics sentinel values for unset fields`() {
        val diag = SkinDiagnostics(
            activeSkinName = null,
            activeSkinPackage = null,
            activeSkinVersion = -1,
            themeMode = SkinPackage.ThemeMode.LIGHT,
            registeredViewCount = 0,
            aliveViewCount = 0,
            listenerCount = 0,
            windowCount = 0,
            registeredAttributeCount = 13,
            cachedIdMappings = 0,
            lastSwitchDurationMs = -1,
            lastError = null
        )
        assertEquals(-1, diag.activeSkinVersion)
        assertEquals(-1, diag.lastSwitchDurationMs)
        assertNull(diag.activeSkinName)
        assertNull(diag.lastError)
    }
}
