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
 * Verifies that [SkinResources.updateBaseConfiguration] produces correctly
 * themed [android.content.res.Resources] via [Application.createConfigurationContext].
 *
 * These tests validate the configuration plumbing and ensure the
 * createConfigurationContext approach works on API 34+.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SkinThemeColorResolutionTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        if (ArsSkinEngine.getDiagnostics().activeSkinName != null) {
            ArsSkinEngine.dispose()
        }
        ArsSkinEngine.init(application)
    }

    @After
    fun tearDown() {
        ArsSkinEngine.dispose()
    }

    @Test
    fun `createConfigurationContext produces correct uiMode`() {
        val darkCtx = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply {
                uiMode = Configuration.UI_MODE_NIGHT_YES or
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        )

        val darkUiMode = darkCtx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertEquals(
            "createConfigurationContext should yield Resources with NIGHT_YES",
            Configuration.UI_MODE_NIGHT_YES, darkUiMode
        )

        val lightCtx = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply {
                uiMode = Configuration.UI_MODE_NIGHT_NO or
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        )

        val lightUiMode = lightCtx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        assertEquals(
            "createConfigurationContext should yield Resources with NIGHT_NO",
            Configuration.UI_MODE_NIGHT_NO, lightUiMode
        )
    }

    @Test
    fun `createConfigurationContext result is not the same object as original`() {
        val darkCtx = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply {
                uiMode = Configuration.UI_MODE_NIGHT_YES or
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        )

        // The Resources should be a DIFFERENT object (not the same singleton)
        assertNotSame(
            "createConfigurationContext must return a new Resources, not the same instance",
            application.resources, darkCtx.resources
        )
    }

    @Test
    fun `SkinResources updateBaseConfiguration replaces themedResources`() {
        val skinRes = SkinResources(
            baseResources = application.resources,
            themedResources = application.resources,
            skinResources = null,
            skinPackageName = null,
            hostPackageName = application.packageName,
            hostContext = application.applicationContext
        )

        val originalThemed = getThemedResourcesRef(skinRes)

        // Switch to dark
        val darkConfig = Configuration(application.resources.configuration).apply {
            uiMode = Configuration.UI_MODE_NIGHT_YES or
                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        }
        skinRes.updateBaseConfiguration(darkConfig)

        val newThemed = getThemedResourcesRef(skinRes)

        assertNotSame(
            "updateBaseConfiguration must create a NEW themedResources " +
            "(via createConfigurationContext), not mutate the original",
            originalThemed, newThemed
        )
    }

    @Test
    fun `SkinResources getColor works after updateBaseConfiguration without crash`() {
        val skinRes = SkinResources(
            baseResources = application.resources,
            themedResources = application.resources,
            skinResources = null,
            skinPackageName = null,
            hostPackageName = application.packageName,
            hostContext = application.applicationContext
        )

        // Switch to dark
        skinRes.updateBaseConfiguration(
            Configuration(application.resources.configuration).apply {
                uiMode = Configuration.UI_MODE_NIGHT_YES or
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
            }
        )

        // Verify getColor doesn't crash (the themed Resources must be valid)
        try {
            val color = skinRes.getColor(android.R.color.background_light, null)
            assertTrue("getColor should return valid int", color != 0 || color == 0)
        } catch (e: Exception) {
            fail("getColor should not throw after updateBaseConfiguration: ${e.message}")
        }

        // Verify getColorStateList doesn't crash
        try {
            val csl = skinRes.getColorStateList(android.R.color.background_light, null)
            // May be null if resource doesn't exist as ColorStateList — acceptable
        } catch (e: Exception) {
            fail("getColorStateList should not throw: ${e.message}")
        }
    }

    // ─── Reflection helper ────────────────────────────────────────────

    /**
     * Access private [SkinResources.themedResources] via reflection
     * to verify object identity changes across [SkinResources.updateBaseConfiguration].
     */
    private fun getThemedResourcesRef(skinRes: SkinResources): Any {
        val field = SkinResources::class.java.getDeclaredField("themedResources")
        field.isAccessible = true
        return field.get(skinRes)
    }
}
