package com.kagawagao.ars

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresApi
import com.kagawagao.ars.internal.ArsSkinLoader
import com.kagawagao.ars.internal.AttrBinding
import com.kagawagao.ars.internal.ResourceType
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
import java.util.concurrent.ConcurrentHashMap

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

    // ─── Coroutine Scope ──────────────────────────────────────────────

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val switchLock = Mutex()

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

    // ─── Listeners ────────────────────────────────────────────────────

    private val skinChangeListeners = mutableSetOf<SkinChangeListener>()

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
        initialized = true
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

        return switchLock.withLock {
            // Store previous for listener notification
            val oldSkin = activeSkin

            // Apply skin state on main thread
            withContext(Dispatchers.Main) {
                previousSkin = oldSkin
                activeSkin = skin

                // Update all SkinResources instances with the new skin
                // (This will be done via registered SkinResources references in a full implementation)

                // TODO Phase B: Walk View tree via ArsViewTreeWalker
                // ArsViewTreeWalker.walk(decorView, this@ArsSkinEngine)

                // Notify listeners (copy-on-iterate to avoid concurrent modification)
                val listeners = skinChangeListeners.toList()
                for (listener in listeners) {
                    try {
                        listener.onSkinChanged(oldSkin, skin)
                    } catch (e: Exception) {
                        // Swallow listener exceptions — framework must not crash
                    }
                }
            }

            SkinResult.Success(Unit)
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

                // TODO Phase B: Walk View tree to reset all Views to default

                val listeners = skinChangeListeners.toList()
                for (listener in listeners) {
                    try {
                        listener.onSkinChanged(oldSkin, null)
                    } catch (_: Exception) { }
                }
            }

            SkinResult.Success(Unit)
        }
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

        if (currentThemeMode == mode) return
        currentThemeMode = mode

        // TODO Phase B: Re-apply configuration and walk View tree
        // If activeSkin is set, update its Resources configuration and re-walk
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
            skinResources = activeSkin?.resources,
            skinPackageName = activeSkin?.packageName,
            hostPackageName = base.packageName
        )

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
    }

    /**
     * Unregister a custom attribute handler.
     *
     * @param attributeName The attribute name to unregister.
     */
    fun unregisterAttributeHandler(attributeName: String) {
        attributeHandlers.remove(attributeName)
    }

    // ─── Diagnostics ───────────────────────────────────────────────────

    /**
     * Get current engine diagnostics for debugging.
     *
     * @return [SkinDiagnostics] containing active skin info, registered View counts, etc.
     */
    fun getDiagnostics(): SkinDiagnostics {
        val aliveViewCount = viewRegistry.values.count { it.viewRef.get() != null }

        return SkinDiagnostics(
            activeSkinName = activeSkin?.name,
            activeSkinPackage = activeSkin?.packageName,
            activeSkinVersion = activeSkin?.version ?: -1,
            themeMode = currentThemeMode,
            registeredViewCount = viewRegistry.size,
            aliveViewCount = aliveViewCount,
            listenerCount = skinChangeListeners.size,
            handlerCount = attributeHandlers.size
        )
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Release all skin resources and clear state.
     *
     * Called when the host [Application] is terminated.
     * After calling this, [init] must be called again before any other method.
     */
    fun dispose() {
        switchLock.withLock {
            activeSkin?.dispose()
            activeSkin = null
            previousSkin = null
            skinChangeListeners.clear()
            attributeHandlers.clear()
            viewRegistry.clear()
            skinLoader = null
            initialized = false
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
        val skinRes = activeSkin?.resources ?: return

        val handlerMap = attributeHandlers
        for (binding in meta.attributes) {
            try {
                // Try custom handler first
                val handler = handlerMap[binding.attributeName]
                if (handler != null) {
                    handler.apply(view, binding.resId, skinRes)
                    continue
                }

                // Built-in attribute dispatch
                when (binding.attributeName) {
                    "background" -> {
                        when (binding.resourceType) {
                            ResourceType.COLOR -> view.setBackgroundColor(skinRes.getColor(binding.resId, null))
                            ResourceType.DRAWABLE, ResourceType.COLOR_STATE_LIST ->
                                view.background = skinRes.getDrawable(binding.resId, null)
                            else -> {} // unsupported type for background
                        }
                    }
                    "textColor" -> {
                        if (view is android.widget.TextView) {
                            view.setTextColor(skinRes.getColorStateList(binding.resId, null))
                        }
                    }
                    "src" -> {
                        if (view is android.widget.ImageView) {
                            view.setImageDrawable(skinRes.getDrawable(binding.resId, null))
                        }
                    }
                    "tint" -> {
                        if (view is android.widget.ImageView) {
                            view.imageTintList = skinRes.getColorStateList(binding.resId, null)
                        }
                    }
                    "textSize" -> {
                        if (view is android.widget.TextView) {
                            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                                skinRes.getDimension(binding.resId))
                        }
                    }
                    "drawableStart", "drawableEnd", "drawableTop", "drawableBottom" -> {
                        if (view is android.widget.TextView) {
                            val drawables = view.compoundDrawablesRelative
                            val d = skinRes.getDrawable(binding.resId, null)
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
