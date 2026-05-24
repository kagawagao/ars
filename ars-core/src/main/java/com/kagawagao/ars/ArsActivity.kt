package com.kagawagao.ars

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.kagawagao.ars.internal.ArsViewTreeWalker

/**
 * Base Activity for ARS-skinning-enabled applications.
 *
 * Extend this instead of [AppCompatActivity]. Provides automatic View skinning,
 * skin switch handling, and transient state preservation — with **zero**
 * additional developer intervention beyond changing the parent class.
 *
 * ## Key behaviors (FR-P0-03, FR-P0-04, FR-P0-07):
 *
 * - **Context wrapping** in [attachBaseContext]: all `getResources()` calls
 *   on the Activity Context return skin-aware values.
 * - **LayoutInflater interception** in [onCreate]: all XML-inflated Views
 *   are automatically registered for skin updates via [SkinLayoutInflater].
 * - **No recreation** on skin switch: [onSkinChanged] walks the View tree
 *   in-place, preserving scroll position, text input, etc.
 * - **State preservation**: [onSaveInstanceState] / [onRestoreInstanceState]
 *   are overridden for proper lifecycle handling.
 *
 * ## Usage
 *
 * ```kotlin
 * class MainActivity : ArsActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.main)
 *     }
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsActivity : AppCompatActivity(), SkinChangeListener {

    companion object {
        private const val TAG = "ARS_Activity"
    }

    // ─── Context Wrapping ─────────────────────────────────────────────

    /**
     * Wrap the base Context with skin-aware [SkinResources].
     *
     * This is called by the framework before [onCreate]. After this,
     * `this.resources.getColor(R.color.primary)` returns the skin's
     * color when a skin is active.
     */
    override fun attachBaseContext(newBase: Context) {
        val wrapped = ArsSkinEngine.wrapContext(newBase)
        super.attachBaseContext(wrapped)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────

    /**
     * Initialize skinning support.
     *
     * 1. Installs [SkinLayoutInflater] as the LayoutInflater's Factory2
     *    BEFORE super.onCreate() so AppCompat chains on top of it.
     * 2. Registers this Activity as a global skin change listener.
     */
    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install SkinLayoutInflater BEFORE super.onCreate().
        // AppCompat sets its own Factory2 during super.onCreate() —
        // we must be first so AppCompat wraps ours rather than
        // trying to replace AppCompat's (which throws "factory already set").
        val inflater = LayoutInflater.from(this)
        val originalFactory = inflater.factory2
        val skinFactory = ArsSkinEngine.createSkinFactory(originalFactory, this)
        inflater.factory2 = skinFactory

        super.onCreate(savedInstanceState)

        // Register for skin change notifications
        ArsSkinEngine.registerSkinChangeListener(this)

        // Register for automatic View-tree walking during skin switches
        ArsSkinEngine.registerActiveActivity(this)

        Log.d(TAG, "onCreate: ${this.javaClass.simpleName}, " +
            "theme=${ArsSkinEngine.currentThemeMode}, " +
            "skin=${ArsSkinEngine.activeSkin?.name ?: "default"}")
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        ArsSkinEngine.unregisterSkinChangeListener(this)
        ArsSkinEngine.unregisterActiveActivity(this)
        Log.d(TAG, "onDestroy: ${this.javaClass.simpleName}")
    }

    // ─── State Preservation (FR-P0-04) ────────────────────────────────

    /**
     * Save Activity instance state.
     *
     * Overridden to ensure proper state preservation when the system
     * kills and restores this Activity (e.g., during configuration changes
     * coinciding with a skin switch).
     */
    @CallSuper
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    /**
     * Restore Activity instance state.
     *
     * Overridden to ensure proper state restoration after system-initiated
     * process death.
     */
    @CallSuper
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
    }

    // ─── Skin Change Handling (FR-P0-03) ──────────────────────────────

    /**
     * Called after the active skin has changed.
     *
     * Walks the View tree starting from [android.view.Window.getDecorView]
     * and applies the new skin to all registered Views — without recreating
     * the Activity. Scroll position, text input, and other transient state
     * are preserved.
     *
     * Override this to perform custom post-skin-switch logic.
     *
     * @param previous The previously active skin, or `null` if default was active.
     * @param current The newly active skin, or `null` if reset to default.
     */
    /**
     * Called after the active skin has changed and the View tree has been updated.
     *
     * Override this to perform custom post-skin-switch logic (e.g., update
     * non-View UI elements, refresh custom components). The View tree has
     * already been walked and updated before this is called.
     *
     * @param previous The previously active skin, or `null` if default was active.
     * @param current The newly active skin, or `null` if reset to default.
     */
    open fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // Subclasses override to add custom post-skin-switch behavior
    }

    /**
     * SkinChangeListener implementation.
     *
     * The engine has already walked the View tree and applied the skin before
     * this callback fires. This method delegates to [onSkinApplied] for
     * subclass-level customization.
     *
     * This is `final` — override [onSkinApplied] instead.
     */
    final override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
        // The engine has already walked the View tree.
        // Only notify the subclass for custom post-skin-switch behavior.
        onSkinApplied(previous, current)
    }

    // ─── Convenience Methods ───────────────────────────────────────────

    /**
     * Load and switch to a skin from a file path.
     *
     * Convenience wrapper around [ArsSkinEngine.switchSkin].
     * Loading (I/O) is performed on a background thread.
     *
     * @param skinPath Absolute path to the skin APK file.
     * @return [SkinResult.Success] on success, [SkinResult.Error] on failure.
     */
    protected suspend fun switchSkin(skinPath: String): SkinResult<Unit> {
        return ArsSkinEngine.switchSkin(skinPath)
    }

    /**
     * Reset to the default (host app) resources.
     *
     * @return [SkinResult.Success] on success.
     */
    protected suspend fun resetSkin(): SkinResult<Unit> {
        return ArsSkinEngine.resetToDefault()
    }

    /**
     * Set the theme mode for the current skin.
     *
     * Triggers a View-tree refresh so all Views reflect the new theme's
     * qualified resources (e.g., `values-night/`).
     *
     * @param mode The target theme mode.
     */
    protected fun setSkinThemeMode(mode: SkinPackage.ThemeMode) {
        ArsSkinEngine.setThemeMode(mode)
    }

    /**
     * Re-apply the current skin to this Activity's View tree.
     *
     * Useful after programmatically adding Views or changing the layout.
     * Note: This only walks *this* Activity's tree — it does not update
     * SkinResources or notify other listeners. For a full skin switch,
     * use [ArsSkinEngine.switchSkin] instead.
     */
    fun refreshSkin() {
        ArsViewTreeWalker.walk(window.decorView, ArsSkinEngine)
    }
}
