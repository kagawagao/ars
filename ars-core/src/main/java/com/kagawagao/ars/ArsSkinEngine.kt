package com.kagawagao.ars

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import com.kagawagao.ars.internal.ArsSkinLoader
import com.kagawagao.ars.internal.ArsViewTreeWalker
import com.kagawagao.ars.internal.AttrBinding
import com.kagawagao.ars.internal.ResourceType
import com.kagawagao.ars.internal.SkinAttributeResolver
import com.kagawagao.ars.internal.SkinContextWrapper
import com.kagawagao.ars.internal.SkinLayoutInflater
import com.kagawagao.ars.internal.SkinResources
import com.kagawagao.ars.internal.SkinViewMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central engine for the ARS skinning framework.
 *
 * Application-scoped singleton. Manages skin lifecycle, View-tree updates,
 * listener dispatch, Context wrapping, and LayoutInflater integration.
 *
 * ## Usage
 *
 * 1. Initialize once during `Application.onCreate()`:
 *    ```kotlin
 *    ArsSkinEngine.init(application)
 *    ```
 *
 * 2. Load and switch skins:
 *    ```kotlin
 *    scope.launch {
 *        val result = ArsSkinEngine.switchSkin("/path/to/skin.apk")
 *    }
 *    ```
 *
 * 3. Wrap a Context to make it skin-aware:
 *    ```kotlin
 *    val wrapped = ArsSkinEngine.wrapContext(context)
 *    ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object ArsSkinEngine {

    private const val TAG = "ARS_SkinEngine"

    // ─── Coroutine Scope ──────────────────────────────────────────────

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val switchLock = Mutex()
    private val switchInProgress = AtomicBoolean(false)

    // ─── State ────────────────────────────────────────────────────────

    /** The currently active skin package, or `null` if default (host) resources. */
    @Volatile
    var activeSkin: SkinPackage? = null
        private set

    /** The previously active skin (for listener callbacks). */
    private var previousSkin: SkinPackage? = null

    /** The current theme mode. */
    @Volatile
    var currentThemeMode: SkinPackage.ThemeMode = SkinPackage.ThemeMode.LIGHT
        private set

    /** The host application context. Set by [init]. */
    private var appContext: Application? = null

    /** Skin loader instance. Created lazily after [init]. */
    private var skinLoader: ArsSkinLoader? = null

    /** Whether the engine has been initialized. */
    @Volatile
    private var initialized = false

    /** Duration of the most recent skin switch in milliseconds (-1 if none). */
    @Volatile
    var lastSwitchDurationMs: Long = -1
        private set

    /** The most recent skin error, or null. */
    @Volatile
    var lastError: SkinError? = null
        private set

    /** Controls whether debug-level resource resolution logging is enabled. */
    @Volatile
    var debugLogging: Boolean = false

    // ─── Resource ID Cache ─────────────────────────────────────────────

    /**
     * LRU cache for (hostResId → skinResId) mappings per DD-10.
     *
     * Keyed by host resource ID. Each entry maps to the corresponding skin
     * resource ID. Entries are evicted on memory pressure. When the active
     * skin changes, the cache is invalidated.
     *
     * Default capacity: 256 entries.
     */
    private val resourceIdCache = object : android.util.LruCache<Int, Int>(256) {
        override fun sizeOf(key: Int, value: Int): Int = 1
    }

    /**
     * Look up a skin resource ID from the cache, resolving and caching if absent.
     *
     * @param hostResId The host application resource ID.
     * @param resolver A lambda that resolves the skin ID (called on cache miss).
     * @return The skin resource ID, or 0 if not found.
     */
    internal fun cachedResolveSkinId(hostResId: Int, resolver: () -> Int): Int {
        val cached = resourceIdCache.get(hostResId)
        if (cached != null) return cached
        val resolved = resolver()
        if (resolved != 0) {
            resourceIdCache.put(hostResId, resolved)
        }
        return resolved
    }

    /**
     * Invalidate all cached resource ID mappings.
     *
     * Called automatically on skin switch and reset.
     */
    private fun invalidateIdCache() {
        resourceIdCache.evictAll()
    }

    // ─── Listeners ────────────────────────────────────────────────────

    private val skinChangeListeners = java.util.concurrent.ConcurrentHashMap.newKeySet<SkinChangeListener>()

    // ─── Attribute Handlers ───────────────────────────────────────────

    private val attributeHandlers = ConcurrentHashMap<String, SkinAttributeHandler>()

    // ─── View Registry ────────────────────────────────────────────────

    /**
     * Thread-safe registry of View metadata.
     *
     * Keyed by [View.hashCode] (identity-based, not stable across process
     * restarts). Uses [ConcurrentHashMap] for thread-safe reads during
     * concurrent skin switch and View inflation.
     */
    private val viewRegistry = ConcurrentHashMap<Int, SkinViewMeta>()

    // ─── SkinResources Tracking ────────────────────────────────────────

    /**
     * Registry of all active [SkinResources] instances created by [wrapContext].
     *
     * Uses [WeakReference] to avoid leaking SkinResources (and thereby
     * Context/Activity) after their owning component is destroyed. On each
     * skin switch, all alive instances are updated via [SkinResources.updateSkin].
     */
    private val skinResourcesRefs = mutableSetOf<WeakReference<SkinResources>>()

    // ─── Active Activities ─────────────────────────────────────────────

    /**
     * Set of currently active [android.app.Activity] instances that have
     * opted into skinning (typically via extending [ArsActivity]).
     *
     * Populated by [registerActiveActivity] / [unregisterActiveActivity].
     * Used during skin switch to walk each Activity's View tree.
     */
    private val activeActivities = mutableSetOf<WeakReference<android.app.Activity>>()

    // ─── Active Windows (Dialogs, Popups, etc.) ────────────────────────

    /**
     * Registry of View tree roots for non-Activity windows (Dialogs,
     * DialogFragments, PopupWindows, Snackbar overlays, etc.).
     *
     * Each entry is a decorView or content root View. On every skin/theme
     * change, the engine walks ALL windows — Activity decorViews AND
     * registered window roots — in a single pass. This eliminates the
     * need for each UI component to implement [SkinChangeListener] and
     * walk its own tree.
     *
     * Uses [WeakReference] to avoid leaking Views after their window
     * is dismissed without explicit unregistration.
     */
    private val activeWindows = mutableSetOf<WeakReference<View>>()

    // ─── Initialization ───────────────────────────────────────────────

    /**
     * Initialize the engine. Must be called once during [Application.onCreate].
     *
     * Idempotent — subsequent calls are no-ops.
     *
     * @param application The host Application instance.
     */
    fun init(application: Application) {
        if (initialized) return
        appContext = application
        skinLoader = ArsSkinLoader(application)

        // Detect the current system UI mode so that the engine's theme
        // state matches what the user actually sees on screen.
        // This is critical when using Theme.MaterialComponents.DayNight
        // which auto-follows the system dark mode setting — if we always
        // default to LIGHT, the toggle button shows "switch to dark" while
        // the UI is already in dark mode.
        val nightMode = application.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        currentThemeMode = when (nightMode) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> SkinPackage.ThemeMode.DARK
            else -> SkinPackage.ThemeMode.LIGHT
        }

        initialized = true
        Log.i(TAG, "ArsSkinEngine initialized: pkg=${application.packageName}, " +
            "detectedTheme=${currentThemeMode}")
    }

    // ─── Skin Control ─────────────────────────────────────────────────

    /**
     * Load and switch to a skin from a file path.
     *
     * Loading (I/O) happens on [Dispatchers.IO]. View updates happen on
     * [Dispatchers.Main] after the skin is validated and loaded.
     *
     * @param skinPath Absolute path to the skin APK file.
     * @return [SkinResult.Success] on success, [SkinResult.Error] with details on failure.
     */
    suspend fun switchSkin(skinPath: String): SkinResult<Unit> {
        ensureInitialized()
        val loader = skinLoader!!

        // Load skin on IO thread
        val loadResult = loader.load(skinPath)
        if (loadResult is SkinResult.Error) {
            lastError = loadResult.error
            return loadResult
        }

        val skinPackage = (loadResult as SkinResult.Success).value
        return switchSkin(skinPackage)
    }

    /**
     * Switch to an already-loaded [SkinPackage].
     *
     * Applies the skin on the main thread. If called from a background
     * thread, the main-thread work is dispatched automatically.
     *
     * @param skin The loaded skin package.
     * @return [SkinResult.Success] on success.
     */
    suspend fun switchSkin(skin: SkinPackage): SkinResult<Unit> {
        ensureInitialized()

        val startTime = System.currentTimeMillis()

        return switchLock.withLock {
            // Store previous for listener notification
            val oldSkin = activeSkin

            // Apply skin state on main thread
            withContext(Dispatchers.Main) {
                previousSkin = oldSkin
                activeSkin = skin

                // Release previous skin resources to avoid memory leaks
                oldSkin?.dispose()

                // Invalidate resource ID cache since the skin changed
                invalidateIdCache()

                // Update all SkinResources instances so that
                // context.resources.getColor() etc. return skin values
                updateAllSkinResources(skin.resources, skin.packageName)

                // Walk View trees for all active Activities
                walkAllWindows()

                // Track timing
                lastSwitchDurationMs = System.currentTimeMillis() - startTime
                lastError = null

                // Notify listeners (copy-on-iterate to avoid concurrent modification)
                notifySkinChangeListeners(oldSkin, skin)
            }

            SkinResult.Success(Unit)
        }
    }

    /**
     * Non-blocking attempt to switch skins.
     *
     * If a skin switch is already in progress, returns immediately with
     * [SkinError.SwitchInProgress] instead of waiting for the lock.
     * Use [switchSkin] for the blocking (default) behaviour.
     *
     * @param skin The loaded skin package.
     * @return [SkinResult.Success] on success, [SkinResult.Error] with
     *         [SkinError.SwitchInProgress] if another switch is active.
     */
    suspend fun trySwitchSkin(skin: SkinPackage): SkinResult<Unit> {
        ensureInitialized()

        if (!switchInProgress.compareAndSet(false, true)) {
            return SkinResult.Error(SkinError.SwitchInProgress)
        }
        return try {
            switchSkin(skin)
        } finally {
            switchInProgress.set(false)
        }
    }

    /**
     * Reset to the default (host app) resources.
     *
     * @return [SkinResult.Success] on success.
     */
    suspend fun resetToDefault(): SkinResult<Unit> {
        ensureInitialized()

        return switchLock.withLock {
            val oldSkin = activeSkin

            withContext(Dispatchers.Main) {
                previousSkin = oldSkin
                activeSkin = null

                // Release previous skin resources to avoid memory leaks
                oldSkin?.dispose()

                // Reset all SkinResources to default (host) resources
                updateAllSkinResources(null, null)

                // Walk View trees to reset all Views to default
                walkAllWindows()

                // Notify listeners
                notifySkinChangeListeners(oldSkin, null)
            }

            SkinResult.Success(Unit)
        }
    }

    // ─── Skin Preview (FR-P2-04) ───────────────────────────────────────

    /**
     * Apply a skin temporarily without committing the choice.
     *
     * The previous skin is saved and can be restored via [cancelPreview].
     * The preview skin replaces the active skin for all View-tree walks
     * and resource lookups, but the engine does not consider it the
     * \"committed\" active skin — [cancelPreview] will restore the
     * previously active skin.
     *
     * Only one preview can be active at a time. Calling [previewSkin]
     * while a preview is active replaces it.
     *
     * @param skinPath Absolute path to the skin APK file.
     * @return [SkinResult.Success] if the preview was applied.
     */
    suspend fun previewSkin(skinPath: String): SkinResult<Unit> {
        ensureInitialized()
        val loader = skinLoader!!

        val loadResult = loader.load(skinPath)
        if (loadResult is SkinResult.Error) {
            lastError = loadResult.error
            return loadResult
        }

        val skinPackage = (loadResult as SkinResult.Success).value
        return previewSkin(skinPackage)
    }

    /**
     * Apply an already-loaded [SkinPackage] as a preview.
     */
    suspend fun previewSkin(skin: SkinPackage): SkinResult<Unit> {
        ensureInitialized()

        val startTime = System.currentTimeMillis()

        return switchLock.withLock {
            val oldSkin = activeSkin

            withContext(Dispatchers.Main) {
                previousSkin = oldSkin
                activeSkin = skin

                // NOTE: Do NOT dispose oldSkin here — it may be restored by cancelPreview().
                // oldSkin stays alive so that cancelPreview() can call switchSkin(oldSkin)
                // and switchSkin() will handle disposal of the preview skin.

                invalidateIdCache()
                updateAllSkinResources(skin.resources, skin.packageName)
                walkAllWindows()

                lastSwitchDurationMs = System.currentTimeMillis() - startTime
                lastError = null

                notifySkinChangeListeners(oldSkin, skin)
            }

            SkinResult.Success(Unit)
        }
    }

    /**
     * Cancel the current preview and restore the previously active skin.
     *
     * If no preview is active (i.e., the skin was committed via [switchSkin]),
     * this is a no-op. After cancellation, the engine state is as it was
     * before [previewSkin] was called.
     */
    suspend fun cancelPreview(): SkinResult<Unit> {
        ensureInitialized()

        // If there's no previous skin to restore, this is a no-op
        val restored = previousSkin ?: return SkinResult.Success(Unit)

        return switchSkin(restored)
    }

    // ─── Theme Mode ───────────────────────────────────────────────────

    /**
     * Set the theme mode (LIGHT / DARK).
     *
     * Updates the Configuration on active skin Resources (if any) and
     * triggers a View-tree refresh so all Views reflect the new mode's
     * qualified resources (e.g., `values-night/`).
     *
     * @param mode The target theme mode.
     */
    fun setThemeMode(mode: SkinPackage.ThemeMode) {
        ensureInitialized()

        if (currentThemeMode == mode) {
            Log.d(TAG, "setThemeMode: already in ${mode}, skipping")
            return
        }
        val previousMode = currentThemeMode
        currentThemeMode = mode

        val appCtx = appContext ?: return

        // Calculate the target UI mode for the configuration
        val targetUiMode = when (mode) {
            SkinPackage.ThemeMode.DARK ->
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            SkinPackage.ThemeMode.LIGHT ->
                android.content.res.Configuration.UI_MODE_NIGHT_NO
        }

        // Compute the target configuration once.
        // We merge with appCtx.resources.configuration to preserve density,
        // locale, and other non-night-mode settings.
        val targetConfig = android.content.res.Configuration(appCtx.resources.configuration).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or targetUiMode
        }

        Log.i(TAG, "setThemeMode: $previousMode → $mode, " +
            "activeSkin=${activeSkin?.name ?: "null"}, " +
            "skinResourcesRefs=${skinResourcesRefs.size}, " +
            "activeActivities=${activeActivities.size}")

        // Update the skin Resources configuration so that values-night/ etc.
        // are resolved correctly on the next resource lookup
        activeSkin?.resources?.let { skinRes ->
            @Suppress("DEPRECATION")
            skinRes.updateConfiguration(targetConfig, skinRes.displayMetrics)
            Log.d(TAG, "setThemeMode: updated active skin Resources config")
        }

        // Purge GC'd SkinResources references before iterating
        skinResourcesRefs.removeAll { it.get() == null }

        // Update every SkinResources' themedResources via createConfigurationContext.
        // This is the critical path: all View-level resource lookups go through
        // SkinResources → themedResources. Using createConfigurationContext ensures
        // night-qualified resources (values-night/) are resolved without relying on
        // the deprecated Resources.updateConfiguration() API.
        for (ref in skinResourcesRefs) {
            ref.get()?.updateBaseConfiguration(targetConfig)
        }
        Log.i(TAG, "setThemeMode: updated ${skinResourcesRefs.size} SkinResources instances")

        // Walk all View trees so that all Views reflect the new theme
        walkAllWindows()

        // Notify listeners that the effective theme has changed
        notifySkinChangeListeners(activeSkin, activeSkin)
    }

    // ─── Context Wrapping ──────────────────────────────────────────────

    /**
     * Wrap a base [Context] with skin-aware [SkinResources].
     *
     * Called automatically by `ArsActivity` / `ArsFragment`. Library
     * consumers can call this to wrap custom Contexts.
     *
     * @param base The base Context to wrap.
     * @return A ContextWrapper whose [Context.getResources] returns skin-aware values.
     */
    fun wrapContext(base: Context): Context {
        ensureInitialized()

        val skinRes = SkinResources(
            baseResources = base.resources,
            themedResources = base.resources,
            skinResources = activeSkin?.resources,
            skinPackageName = activeSkin?.packageName,
            hostPackageName = base.packageName,
            hostContext = base,
            idCacheResolver = ::cachedResolveSkinId
        )

        // Track this SkinResources instance so it can be updated on skin switch
        skinResourcesRefs.add(WeakReference(skinRes))

        Log.d(TAG, "wrapContext: created SkinResources for ${base.packageName}, " +
            "total tracked=${skinResourcesRefs.size}")

        return SkinContextWrapper(base, skinRes)
    }

    // ─── LayoutInflater Integration ────────────────────────────────────

    /**
     * Create a [LayoutInflater.Factory2] that intercepts View creation.
     *
     * **Phase A stub:** Returns a dummy Factory2 that delegates everything
     * to the provided [delegate] without recording View metadata.
     * Phase B will implement the full [SkinLayoutInflater] with
     * AttributeSet scanning and [SkinViewMeta] recording.
     *
     * @param delegate The next Factory2 in the chain (typically AppCompat's).
     * @param context The Context for resource resolution.
     * @return A new Factory2 (currently a pass-through delegate in Phase A).
     */
    fun createSkinFactory(
        delegate: LayoutInflater.Factory2?,
        context: Context
    ): LayoutInflater.Factory2 {
        ensureInitialized()
        return SkinLayoutInflater(delegate, this, wrapContext(context))
    }

    // ─── Listener Management ───────────────────────────────────────────

    /**
     * Register a global skin change listener.
     *
     * Called **after** all Views have been updated with the new skin.
     * Implementations are called on the main thread.
     *
     * @param listener The listener to register.
     */
    fun registerSkinChangeListener(listener: SkinChangeListener) {
        skinChangeListeners.add(listener)
    }

    /**
     * Unregister a global skin change listener.
     *
     * @param listener The listener to unregister.
     */
    fun unregisterSkinChangeListener(listener: SkinChangeListener) {
        skinChangeListeners.remove(listener)
    }

    // ─── Custom Attribute Handlers ─────────────────────────────────────

    /**
     * Register a handler for a custom View attribute.
     *
     * The attribute name should be provided WITHOUT a namespace prefix
     * (e.g., `"cornerRadius"`, not `"app:cornerRadius"`).
     *
     * @param attributeName The attribute name without namespace prefix.
     * @param handler The handler that applies the resource to the View.
     */
    fun registerAttributeHandler(attributeName: String, handler: SkinAttributeHandler) {
        attributeHandlers[attributeName] = handler
        // Also register in the resolver so XML-inflated Views with this
        // attribute will have their bindings recorded (otherwise
        // SkinLayoutInflater skips unrecognized attributes)
        SkinAttributeResolver.registerCustom(attributeName)
    }

    /**
     * Unregister a custom attribute handler.
     *
     * @param attributeName The attribute name to unregister.
     */
    fun unregisterAttributeHandler(attributeName: String) {
        attributeHandlers.remove(attributeName)
        SkinAttributeResolver.unregisterCustom(attributeName)
    }

    // ─── Diagnostics ───────────────────────────────────────────────────

    /**
     * Get current engine diagnostics for debugging.
     *
     * @return [SkinDiagnostics] containing active skin info, registered View counts, etc.
     */
    fun getDiagnostics(): SkinDiagnostics {
        val aliveViewCount = viewRegistry.values.count { it.viewRef.get() != null }
        val builtInAttrCount = SkinAttributeResolver.DEFAULT_SUPPORTED_ATTRIBUTES.size

        return SkinDiagnostics(
            activeSkinName = activeSkin?.name,
            activeSkinPackage = activeSkin?.packageName,
            activeSkinVersion = activeSkin?.version ?: -1,
            themeMode = currentThemeMode,
            registeredViewCount = viewRegistry.size,
            aliveViewCount = aliveViewCount,
            listenerCount = skinChangeListeners.size,
            windowCount = activeWindows.size,
            registeredAttributeCount = builtInAttrCount + attributeHandlers.size,
            cachedIdMappings = resourceIdCache.snapshot().size,
            lastSwitchDurationMs = lastSwitchDurationMs,
            lastError = lastError
        )
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Register an Activity for automatic View-tree walking during skin switches.
     *
     * Called by [ArsActivity] in [android.app.Activity.onCreate].
     * The engine walks this Activity's decorView on every [switchSkin],
     * [resetToDefault], and [setThemeMode] call.
     *
     * @param activity The Activity to register.
     */
    fun registerActiveActivity(activity: android.app.Activity) {
        activeActivities.add(WeakReference(activity))
    }

    /**
     * Unregister an Activity from automatic View-tree walking.
     *
     * Called by [ArsActivity] in [android.app.Activity.onDestroy].
     *
     * @param activity The Activity to unregister.
     */
    fun unregisterActiveActivity(activity: android.app.Activity) {
        activeActivities.removeAll { it.get() == null || it.get() === activity }
    }

    /**
     * Register a window's root View for automatic skin/theme refresh.
     *
     * Call this when a Dialog, DialogFragment, PopupWindow, or any
     * window with its own decorView/content root is shown. The engine
     * will walk this root on every [switchSkin], [resetToDefault], and
     * [setThemeMode] call — no need to implement [SkinChangeListener]
     * for tree refreshing.
     *
     * @param root The root View to walk (e.g., `dialog.window.decorView`).
     */
    fun registerWindow(root: View) {
        activeWindows.add(WeakReference(root))
        Log.d(TAG, "registerWindow: ${root.javaClass.simpleName}, total=${activeWindows.size}")
    }

    /**
     * Unregister a window's root View.
     *
     * Call this when the window is dismissed/destroyed.
     *
     * @param root The root View previously registered via [registerWindow].
     */
    fun unregisterWindow(root: View) {
        activeWindows.removeAll { it.get() == null || it.get() === root }
        Log.d(TAG, "unregisterWindow: ${root.javaClass.simpleName}, total=${activeWindows.size}")
    }

    /**
     * Walk a View tree and apply the current skin immediately.
     *
     * Convenience for cases where a window was just created and needs
     * an initial skin application before the next theme/skin change.
     * The same as calling [registerWindow] followed by an immediate walk.
     *
     * @param root The root View to walk.
     */
    fun walkViewTree(root: View) {
        registerWindow(root)
        ArsViewTreeWalker.walk(root, this)
    }

    /**
     * Release all skin resources and clear state.
     *
     * Called when the host [Application] is terminated.
     * After calling this, [init] must be called again before any other method.
     */
    fun dispose() {
        // Use runBlocking to bridge the suspend switchLock.withLock from a non-suspend context.
        // dispose() is called during Application.onTerminate() — blocking is acceptable here
        // since the process is shutting down and no other coroutines are active.
        kotlinx.coroutines.runBlocking {
            switchLock.withLock {
                activeSkin?.dispose()
                activeSkin = null
                previousSkin = null
                skinChangeListeners.clear()
                attributeHandlers.clear()
                viewRegistry.clear()
                skinResourcesRefs.clear()
                activeActivities.clear()
                activeWindows.clear()
                skinLoader = null
                initialized = false
            }
        }
    }

    // ─── Private: Skin Update Helpers ───────────────────────────────────

    /**
     * Update all tracked [SkinResources] instances with new skin data.
     *
     * Called during [switchSkin] and [resetToDefault] before walking
     * View trees, so that `context.resources.getColor()` etc. return
     * values from the new skin.
     *
     * @param newResources The new skin's Resources, or `null` to reset to default.
     * @param newPackageName The new skin's package name, or `null` to reset.
     */
    private fun updateAllSkinResources(newResources: android.content.res.Resources?, newPackageName: String?) {
        // Purge GC'd references
        skinResourcesRefs.removeAll { it.get() == null }
        // Update all alive instances
        skinResourcesRefs.forEach { ref ->
            ref.get()?.updateSkin(newResources, newPackageName)
        }
    }

    /**
     * Walk the View tree of every registered window (Activities, Dialogs,
     * PopupWindows, Snackbars, etc.) in a single pass.
     *
     * Called during [switchSkin], [resetToDefault], and [setThemeMode].
     * Applies the current skin/theme to all registered Views.
     */
    private fun walkAllWindows() {
        // Purge GC'd references
        activeActivities.removeAll { it.get() == null }
        activeWindows.removeAll { it.get() == null }

        val activityCount = activeActivities.size
        val windowCount = activeWindows.size
        Log.d(TAG, "walkAllWindows: $activityCount activities + $windowCount windows")

        // Walk Activity decorViews
        activeActivities.forEach { ref ->
            ref.get()?.let { activity ->
                try {
                    ArsViewTreeWalker.walk(activity.window?.decorView ?: return@let, this)
                } catch (_: Exception) {
                    Log.d(TAG, "walkAllWindows: failed to walk ${activity.javaClass.simpleName}")
                }
            }
        }

        // Walk non-Activity window roots (Dialogs, PopupWindows, etc.)
        activeWindows.forEach { ref ->
            ref.get()?.let { root ->
                try {
                    ArsViewTreeWalker.walk(root, this)
                } catch (_: Exception) {
                    Log.d(TAG, "walkAllWindows: failed to walk ${root.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Notify all registered [SkinChangeListener] instances of a skin change.
     *
     * Copy-on-iterate pattern prevents concurrent modification if a listener
     * registers/unregisters during notification. Listener exceptions are
     * swallowed — the framework must not crash due to listener bugs.
     *
     * @param previous The previously active skin, or `null`.
     * @param current The newly active skin, or `null`.
     */
    private fun notifySkinChangeListeners(previous: SkinPackage?, current: SkinPackage?) {
        val listeners = skinChangeListeners.toList()
        for (listener in listeners) {
            try {
                listener.onSkinChanged(previous, current)
            } catch (_: Exception) {
                // Swallow listener exceptions — framework must not crash
            }
        }
    }

    // ─── Internal: View Registry ───────────────────────────────────────

    /**
     * Register a View's skin metadata.
     *
     * Called by [SkinLayoutInflater] (Phase B) during XML inflation.
     * Uses [View.hashCode] as the key (sufficient for runtime tracking
     * within a single process).
     *
     * @param view The View to register.
     * @param meta The View's skin metadata.
     */
    internal fun registerView(view: View, meta: SkinViewMeta) {
        viewRegistry[view.hashCode()] = meta
    }

    /**
     * Unregister a View's skin metadata.
     *
     * Called when a View is detached and should no longer receive
     * skin updates (e.g., Activity destroyed).
     *
     * @param view The View to unregister.
     */
    internal fun unregisterView(view: View) {
        viewRegistry.remove(view.hashCode())
    }

    /**
     * Get the skin metadata for a View.
     *
     * @param view The View to look up.
     * @return The View's [SkinViewMeta], or `null` if not registered.
     */
    internal fun getViewMeta(view: View): SkinViewMeta? {
        return viewRegistry[view.hashCode()]
    }

    /**
     * Apply the current skin to a single View using its metadata.
     *
     * Called during View-tree walks (Phase B: [ArsViewTreeWalker])
     * and when a new View is created while a skin is active.
     *
     * @param view The View to update.
     * @param meta The View's skin metadata.
     */
    internal fun applySkinToView(view: View, meta: SkinViewMeta) {
        // Use the view's own context.resources, which goes through
        // SkinContextWrapper → SkinResources → baseResources chain.
        // This ensures theme mode changes (via updateConfiguration on
        // baseResources) and skin changes both propagate correctly.
        val res = view.context.resources

        val handlerMap = attributeHandlers
        for (binding in meta.attributes) {
            try {
                // Try custom handler first
                val handler = handlerMap[binding.attributeName]
                if (handler != null) {
                    handler.apply(view, binding.resId, res)
                    continue
                }

                // Built-in attribute dispatch
                when (binding.attributeName) {
                    "background" -> {
                        when (binding.resourceType) {
                            ResourceType.COLOR -> view.setBackgroundColor(res.getColor(binding.resId, null))
                            ResourceType.DRAWABLE, ResourceType.COLOR_STATE_LIST ->
                                view.background = res.getDrawable(binding.resId, null)
                            else -> {}
                        }
                    }
                    "textColor" -> {
                        if (view is android.widget.TextView) {
                            view.setTextColor(res.getColorStateList(binding.resId, null))
                        }
                    }
                    "textColorHint" -> {
                        if (view is android.widget.TextView) {
                            view.setHintTextColor(res.getColorStateList(binding.resId, null))
                        }
                    }
                    "src" -> {
                        if (view is android.widget.ImageView) {
                            view.setImageDrawable(res.getDrawable(binding.resId, null))
                        }
                    }
                    "tint" -> {
                        when (view) {
                            is android.widget.ImageView -> view.imageTintList = res.getColorStateList(binding.resId, null)
                        }
                    }
                    "progressTint" -> {
                        if (view is android.widget.ProgressBar) {
                            view.progressTintList = res.getColorStateList(binding.resId, null)
                        }
                    }
                    "thumbTint" -> {
                        if (view is android.widget.AbsSeekBar) {
                            view.thumbTintList = res.getColorStateList(binding.resId, null)
                        }
                    }
                    "buttonTint" -> {
                        if (view is android.widget.CompoundButton) {
                            view.buttonTintList = res.getColorStateList(binding.resId, null)
                        }
                    }
                    "textSize" -> {
                        if (view is android.widget.TextView) {
                            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                                res.getDimension(binding.resId))
                        }
                    }
                    "drawableStart", "drawableEnd", "drawableTop", "drawableBottom" -> {
                        if (view is android.widget.TextView) {
                            val drawables = view.compoundDrawablesRelative
                            val d = res.getDrawable(binding.resId, null)
                            when (binding.attributeName) {
                                "drawableStart" -> view.setCompoundDrawablesRelativeWithIntrinsicBounds(d, drawables[1], drawables[2], drawables[3])
                                "drawableTop" -> view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], d, drawables[2], drawables[3])
                                "drawableEnd" -> view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], drawables[1], d, drawables[3])
                                "drawableBottom" -> view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], drawables[1], drawables[2], d)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Graceful degradation: keep old value, log in debug
                Log.w(TAG, "applySkinToView failed for " +
                    "${view.javaClass.simpleName}.${binding.attributeName}=" +
                    "0x${Integer.toHexString(binding.resId)}: ${e.message}", e)
            }
        }
    }

    // ─── Private Helpers ───────────────────────────────────────────────

    private fun ensureInitialized() {
        check(initialized) {
            "ArsSkinEngine is not initialized. Call ArsSkinEngine.init(application) first."
        }
    }
}
