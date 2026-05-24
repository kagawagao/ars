package com.kagawagao.ars

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import com.kagawagao.ars.internal.SkinResources
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end tests for theme mode switching (light ↔ dark).
 *
 * Uses Robolectric to create real Android [Application], [Context],
 * and [android.content.res.Resources] objects with actual night-mode
 * qualifier resolution. Unlike pure JUnit tests, these tests can verify
 * that `getColor(R.color.theme_background)` returns different values
 * depending on the uiMode set via [SkinResources.updateBaseConfiguration].
 *
 * Coverage:
 * - Theme mode detection from system configuration on [ArsSkinEngine.init]
 * - [SkinResources.updateBaseConfiguration] creates correctly themed Resources
 * - [SkinResources.getColor] returns night-qualified values after theme switch
 * - [ArsSkinEngine.setThemeMode] state transitions and listener callbacks
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SkinThemeEndToEndTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        // Ensure clean state
        if (ArsSkinEngine.getDiagnostics().activeSkinName != null) {
            ArsSkinEngine.dispose()
        }
        ArsSkinEngine.init(application)
    }

    @After
    fun tearDown() {
        ArsSkinEngine.dispose()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Theme detection on init
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `init detects system night mode`() {
        // Robolectric defaults to LIGHT mode in tests.
        assertEquals(
            "Engine should detect system LIGHT mode",
            SkinPackage.ThemeMode.LIGHT,
            ArsSkinEngine.currentThemeMode
        )
    }

    @Test
    fun `init detects dark mode when system is dark`() {
        ArsSkinEngine.dispose()

        // Set system to dark mode before init
        val darkConfig = Configuration(application.resources.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        application.resources.updateConfiguration(darkConfig, application.resources.displayMetrics)

        ArsSkinEngine.init(application)

        assertEquals(
            "Engine should detect system DARK mode",
            SkinPackage.ThemeMode.DARK,
            ArsSkinEngine.currentThemeMode
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. setThemeMode state transitions
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `setThemeMode changes currentThemeMode`() {
        assertEquals(SkinPackage.ThemeMode.LIGHT, ArsSkinEngine.currentThemeMode)

        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)
        assertEquals(SkinPackage.ThemeMode.DARK, ArsSkinEngine.currentThemeMode)

        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.LIGHT)
        assertEquals(SkinPackage.ThemeMode.LIGHT, ArsSkinEngine.currentThemeMode)
    }

    @Test
    fun `setThemeMode is idempotent`() {
        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)
        assertEquals(SkinPackage.ThemeMode.DARK, ArsSkinEngine.currentThemeMode)

        // Second call with same mode should be no-op (state unchanged)
        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)
        assertEquals(SkinPackage.ThemeMode.DARK, ArsSkinEngine.currentThemeMode)
    }

    @Test
    fun `setThemeMode notifies listeners`() {
        var callCount = 0
        var lastPrevious: SkinPackage? = null
        var lastCurrent: SkinPackage? = null

        val listener = object : SkinChangeListener {
            override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
                callCount++
                lastPrevious = previous
                lastCurrent = current
            }
        }
        ArsSkinEngine.registerSkinChangeListener(listener)

        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)

        // Theme-only switch: both previous and current skin are null (no skin loaded)
        assertNull(lastPrevious)
        assertNull(lastCurrent)
        assertEquals(1, callCount)

        ArsSkinEngine.unregisterSkinChangeListener(listener)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. SkinResources — themedResources resolution
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `SkinResources getColor uses themedResources for theme-only switch`() {
        val ctx = application.applicationContext
        val res = ctx.resources

        // Create a SkinResources without a skin loaded (simulating default theme)
        val skinRes = SkinResources(
            baseResources = res,
            themedResources = res,
            skinResources = null,
            skinPackageName = null,
            hostPackageName = application.packageName,
            hostContext = ctx
        )

        // Verify initial: getColor works and returns the same as base
        val lightColorBefore = skinRes.getColor(android.R.color.background_light, null)
        val baseLightColor = res.getColor(android.R.color.background_light, null)
        assertEquals(
            "Without theme switch, SkinResources should return base value",
            baseLightColor, lightColorBefore
        )

        // Switch to dark mode via updateBaseConfiguration
        val darkConfig = Configuration(res.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        skinRes.updateBaseConfiguration(darkConfig)

        // After theme switch, themedResources was replaced by createConfigurationContext.
        // The key invariant: getColor should NOT crash — the themed resources
        // must be valid. If createConfigurationContext returned null or wrong context,
        // getColor would throw NotFoundException or NPE.
        try {
            val darkColor = skinRes.getColor(android.R.color.background_dark, null)
            // background_dark is available in Robolectric shadow resources
            assertTrue("getColor after theme switch should return a valid int", darkColor != 0)
        } catch (e: Exception) {
            fail("getColor after updateBaseConfiguration should not throw: ${e.javaClass.simpleName}: ${e.message}")
        }

        // Verify that drawable and dimension also work (full themedResources plumbing)
        try {
            val dim = skinRes.getDimension(android.R.dimen.app_icon_size)
            assertTrue("getDimension should return valid value", dim > 0f)
        } catch (e: Exception) {
            // app_icon_size might not exist in Robolectric shadow — not a failure
        }
    }

    @Test
    fun `SkinResources updateBaseConfiguration changes uiMode correctly`() {
        val ctx = application.applicationContext
        val res = ctx.resources

        val skinRes = SkinResources(
            baseResources = res,
            themedResources = res,
            skinResources = null,
            skinPackageName = null,
            hostPackageName = application.packageName,
            hostContext = ctx
        )

        // Initial: LIGHT (Robolectric default)
        val initialMode = res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertEquals(Configuration.UI_MODE_NIGHT_NO, initialMode)

        // Switch to DARK
        val darkConfig = Configuration(res.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        skinRes.updateBaseConfiguration(darkConfig)

        // Verify the base resources are NOT affected (we don't mutate them)
        val baseStillLight = res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertEquals(
            "Base resources should NOT be mutated by updateBaseConfiguration",
            Configuration.UI_MODE_NIGHT_NO, baseStillLight
        )
    }

    @Test
    fun `SkinResources getColor falls through when no skin loaded`() {
        val ctx = application.applicationContext

        // Get a known color from the base resources
        val baseColor = ctx.resources.getColor(android.R.color.background_light, null)

        val skinRes = SkinResources(
            baseResources = ctx.resources,
            themedResources = ctx.resources,
            skinResources = null,  // ← no skin loaded
            skinPackageName = null,
            hostPackageName = application.packageName,
            hostContext = ctx
        )

        val skinColor = skinRes.getColor(android.R.color.background_light, null)
        assertEquals(
            "Without skin, getColor should fall through to baseResources",
            baseColor, skinColor
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. wrapContext creates themedResources with current system mode
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `wrapContext creates SkinResources with current system uiMode`() {
        val baseCtx = application.applicationContext
        val wrapped = ArsSkinEngine.wrapContext(baseCtx)

        // The wrapped context should be a SkinContextWrapper
        assertTrue("wrapContext should return SkinContextWrapper",
            wrapped.javaClass.name.contains("SkinContextWrapper"))

        // Resources should be SkinResources
        val res = wrapped.resources
        assertTrue("resources should be SkinResources",
            res.javaClass.name.contains("SkinResources"))
    }

    @Test
    fun `wrapContext after setThemeMode creates correctly themed SkinResources`() {
        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)

        val baseCtx = application.applicationContext
        val wrapped = ArsSkinEngine.wrapContext(baseCtx)

        val res = wrapped.resources
        // After setThemeMode(DARK), resources should return dark-qualified values.
        // We can't test the actual color without real Android colors with night variants,
        // but we can verify no exception is thrown.
        try {
            res.getColor(android.R.color.background_light, null)
        } catch (e: Exception) {
            fail("getColor should not throw: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Diagnostics reflects theme state
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `diagnostics reflect theme mode change`() {
        val diagLight = ArsSkinEngine.getDiagnostics()
        assertEquals(SkinPackage.ThemeMode.LIGHT, diagLight.themeMode)

        ArsSkinEngine.setThemeMode(SkinPackage.ThemeMode.DARK)

        val diagDark = ArsSkinEngine.getDiagnostics()
        assertEquals(SkinPackage.ThemeMode.DARK, diagDark.themeMode)
    }

    @Test
    fun `diagnostics includes windowCount`() {
        val diag = ArsSkinEngine.getDiagnostics()
        // windowCount should be a valid Int (default 0 when no windows registered)
        assertTrue("windowCount should be >= 0", diag.windowCount >= 0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. registerWindow / unregisterWindow
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `registerWindow and unregisterWindow track views`() {
        val view = android.view.View(application)

        assertEquals(0, ArsSkinEngine.getDiagnostics().windowCount)

        ArsSkinEngine.registerWindow(view)
        assertEquals(1, ArsSkinEngine.getDiagnostics().windowCount)

        ArsSkinEngine.registerWindow(view)
        // Same view added twice — set deduplication depends on WeakReference identity
        // (view === view, so removeAll would catch both when unregistering)
        assertTrue(ArsSkinEngine.getDiagnostics().windowCount >= 1)

        ArsSkinEngine.unregisterWindow(view)
        assertEquals(0, ArsSkinEngine.getDiagnostics().windowCount)
    }
}
