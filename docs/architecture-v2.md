# ARS v2 — System Architecture

**Status:** Final
**Author:** ARS Architecture Team
**Date:** 2026-05-17
**Target:** ARS v2.0.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architectural Philosophy](#2-architectural-philosophy)
3. [Tech Stack](#3-tech-stack)
4. [Component Architecture](#4-component-architecture)
5. [Data Flow](#5-data-flow)
6. [API Contracts](#6-api-contracts)
7. [Key Design Decisions](#7-key-design-decisions)
8. [Threading Model](#8-threading-model)
9. [Error Handling](#9-error-handling)
10. [File & Method Structure](#10-file--method-structure)
11. [Migration Notes](#11-migration-notes)

---

## 1. Executive Summary

ARS v2 is a ground-up rebuild of the ARS skinning framework. Its core architecture is a **hybrid ContextWrapper + LayoutInflater.Factory2 approach** (Option C from the technical constraints) that achieves genuine ResourceOverlay behavior without requiring system permissions.

### The Core Insight

Android's ResourceOverlay mechanism (RRO) works by layering an overlay APK's `resources.arsc` over the base APK. System apps can do this via `OverlayManager.setEnabled()`, but third-party apps cannot. ARS v2 **replicates this layering in user space** through two mechanisms:

1. **SkinResources (Resources subclass):** Overrides `getColor()`, `getDrawable()`, `getDimension()`, etc. For every resource lookup, checks the skin APK first, then falls through to the base resources. This means `view.resources.getColor(R.color.primary)` returns the skin's color — no code change needed.

2. **SkinLayoutInflater (Factory2):** Captures the `(View, resId, attribute)` mapping during XML inflation. On skin switch, re-resolves each attribute from the new skin Resources and applies the value to the View via type-safe setters. This handles attributes that Views resolve at construction time (e.g., `TextView` reads `textColor` once and never re-reads it).

The two mechanisms together ensure **every View in the application automatically reflects the active skin** — the core promise of ARS v2.

---

## 2. Architectural Philosophy

### Principles

| Principle | Manifestation |
|---|---|
| **Zero developer intervention** | Extending `ArsActivity` is the only integration step. No manual `applySkin()` calls. |
| **Genuine overlay, not fake** | `getResources().getColor()` returns skin values. No separate `skinManager.getColor()` calls needed. |
| **No recreation** | Skin switch walks the View tree in-place. No `Activity.recreate()`. Scroll position, text input, etc. are preserved. |
| **Fail-safe** | Every error is a `SkinError` sealed type. No uncaught exceptions from the framework. |
| **Thread-safe** | I/O on background, View updates on main. Concurrent skin switches serialize cleanly. |
| **Minimal surface area** | `internal` visibility for everything not in the public API contract. |

### Architecture Style

**Layered architecture** with dependency inversion at the boundaries:

```
┌──────────────────────────────────────────────┐
│  Public API (ArsApplication/Activity/Fragment)│
├──────────────────────────────────────────────┤
│  Engine Layer (ArsSkinEngine)                 │
├──────────────┬───────────────┬────────────────┤
│  Resources   │  Inflation    │  Loading       │
│  Layer       │  Layer        │  Layer         │
├──────────────┴───────────────┴────────────────┤
│  Platform (Android Resources, AssetManager,   │
│            PackageManager, LayoutInflater)    │
└──────────────────────────────────────────────┘
```

---

## 3. Tech Stack

| Technology | Version | Purpose | Rationale |
|---|---|---|---|
| **Kotlin** | 1.9.20 | Primary language | Required by existing project; stable for production |
| **Android Gradle Plugin** | 8.2.2 | Build system | Required by existing project |
| **Gradle Kotlin DSL** | — | Build scripts | Required by existing project |
| **compileSdk / minSdk / targetSdk** | 34 | API target | ResourceOverlay APIs are native to Android 14+; no backport needed |
| **AndroidX Core KTX** | 1.12.0 | Core extensions | `Context.getDrawable()`, `ResourcesCompat` helpers |
| **AndroidX AppCompat** | 1.6.1 | Base classes | `AppCompatActivity`, `AppCompatDelegate`, `LayoutInflater.Factory2` chain |
| **JUnit 4** | 4.13.2 | Unit tests | Required by existing project |
| **AndroidX Test** | 1.1.5 / 3.5.1 | Instrumented tests | ActivityScenario, Espresso |
| **Kotlin Coroutines** | 1.7.x (bundled w/ Kotlin) | Async I/O | Structured concurrency for skin loading; lightweight vs RxJava |
| **Material Components** | 1.11.0 | Demo app only | Material theme for the demo; not a framework dependency |

### Dependency Graph

```
ars-core (library)
├── androidx.core:core-ktx          [API — exposed via Context extension usage]
├── androidx.appcompat:appcompat    [API — base classes extend AppCompat]
├── org.jetbrains.kotlinx:kotlinx-coroutines-android  [implementation — internal async]
│
app (demo)
├── project(":ars-core")
├── com.google.android.material:material
└── androidx.constraintlayout:constraintlayout
```

**No new external dependencies.** The framework uses only existing AndroidX dependencies plus Kotlin coroutines (which ship with the Kotlin stdlib in effect). This keeps the APK footprint minimal.

---

## 4. Component Architecture

### Text-Based Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           HOST APPLICATION                               │
│                                                                          │
│  DemoApplication : ArsApplication                                       │
│  └── MainActivity : ArsActivity                                         │
│      └── setContentView(R.layout.activity_main)                         │
│          └── (Views automatically skin-aware)                           │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ extends
┌──────────────────────────────────▼───────────────────────────────────────┐
│                         PUBLIC API LAYER                                 │
│                                                                          │
│  ArsApplication.kt          ArsActivity.kt          ArsFragment.kt       │
│  ┌──────────────────┐  ┌──────────────────────┐  ┌─────────────────┐    │
│  │ onCreate()        │  │ onCreate()           │  │ onCreateView()   │    │
│  │   → init Engine   │  │   → wrap Context     │  │   → wrap Context │    │
│  │                   │  │   → install Factory2  │  │   → install      │    │
│  │                   │  │ onSkinChanged()      │  │     Factory2     │    │
│  │                   │  │ onSaveInstanceState()│  │ onSkinChanged()  │    │
│  └──────────────────┘  └──────────────────────┘  └─────────────────┘    │
└──────────────────────────────────┬───────────────────────────────────────┘
                                   │ delegates to
┌──────────────────────────────────▼───────────────────────────────────────┐
│                           ENGINE LAYER                                   │
│                                                                          │
│  ArsSkinEngine.kt (object / singleton)                                  │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │  State:                                                             │ │
│  │    - activeSkin: SkinPackage?                                       │ │
│  │    - previousSkin: SkinPackage?                                     │ │
│  │    - themeMode: ThemeMode                                           │ │
│  │    - listeners: MutableSet<SkinChangeListener>                      │ │
│  │    - attributeHandlers: Map<String, SkinAttributeHandler>           │ │
│  │    - viewRegistry: MutableMap<View, SkinViewMeta>                   │ │
│  │    - switchLock: Mutex                                              │ │
│  │                                                                     │ │
│  │  Public:                                                            │ │
│  │    + switchSkin(package: SkinPackage): SkinResult<Unit>             │ │
│  │    + resetToDefault(): SkinResult<Unit>                             │ │
│  │    + getActiveSkin(): SkinPackage?                                  │ │
│  │    + wrapContext(base: Context): Context                            │ │
│  │    + createFactory2(delegate: Factory2): Factory2                   │ │
│  │    + registerListener(listener: SkinChangeListener)                 │ │
│  │    + unregisterListener(listener: SkinChangeListener)               │ │
│  │    + registerAttributeHandler(name: String, handler: ...)           │ │
│  │    + getDiagnostics(): SkinDiagnostics                              │ │
│  │                                                                     │ │
│  │  Internal:                                                          │ │
│  │    - walkViewTree(root: View)                                       │ │
│  │    - notifyListeners(old: SkinPackage?, new: SkinPackage?)          │ │
│  │    - applySkinToView(view: View, meta: SkinViewMeta)                │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└──────┬────────────────────────┬────────────────────────┬─────────────────┘
       │                        │                        │
       │ uses                   │ manages                │ delegates to
       ▼                        ▼                        ▼
┌──────────────┐  ┌────────────────────────┐  ┌──────────────────────────┐
│  RESOURCES   │  │     INFLATION LAYER    │  │      LOADING LAYER       │
│  LAYER       │  │                        │  │                          │
│              │  │  SkinLayoutInflater.kt │  │  ArsSkinLoader.kt        │
│ SkinContext  │  │  ┌──────────────────┐  │  │  ┌────────────────────┐  │
│  Wrapper.kt  │  │  │ Factory2 impl    │  │  │  │ loadFromFile(path) │  │
│ ┌──────────┐ │  │  │ onCreateView()   │  │  │  │   → SkinPackage    │  │
│ │Context   │ │  │  │   → delegate to  │  │  │  │ loadFromAssets()   │  │
│ │Wrapper   │ │  │  │     AppCompat    │  │  │  │ validatePackage()  │  │
│ │override  │ │  │  │   → record View  │  │  │  │ getSkinInfo(path)  │  │
│ │getResour-│ │  │  │     metadata     │  │  │  └────────────────────┘  │
│ │ces() →   │ │  │  │   → return View  │  │  │                          │
│ │SkinRes-  │ │  │  └──────────────────┘  │  │  ArsSkinPackageManager  │
│ │ources    │ │  │                        │  │  ┌────────────────────┐  │
│ └──────────┘ │  │  SkinViewMeta.kt       │  │  │ listInstalled()    │  │
│              │  │  ┌──────────────────┐  │  │  │ deleteSkin(name)   │  │
│ SkinResources│  │  │ WeakRef<View>    │  │  │  │ getSkinDir()       │  │
│  .kt         │  │  │ List<AttrBinding>│  │  │  │ clearAll()         │  │
│ ┌──────────┐ │  │  │   - resId        │  │  │  └────────────────────┘  │
│ │Resources │ │  │  │   - attrName     │  │  │                          │
│ │subclass  │ │  │  │   - attrType     │  │  │  SkinError.kt            │
│ │override  │ │  │  └──────────────────┘  │  │  ┌────────────────────┐  │
│ │getColor()│ │  │                        │  │  │ sealed class:      │  │
│ │getDraw-  │ │  │  ArsViewTreeWalker.kt  │  │  │  CorruptedPackage  │  │
│ │able()    │ │  │  ┌──────────────────┐  │  │  │  IncompatibleVer   │  │
│ │getDimen- │ │  │  │ iterative BFS    │  │  │  │  StorageError      │  │
│ │sion()    │ │  │  │ skip skinned-off │  │  │  │  ParseError        │  │
│ │getString()│ │  │  │ batch apply      │  │  │  │  NotASkinPackage   │  │
│ └──────────┘ │  │  └──────────────────┘  │  │  └────────────────────┘  │
│              │  │                        │  │                          │
│ SkinPackage  │  │  SkinAttributeResolver  │  │  SkinPackage.kt          │
│  .kt         │  │  ┌──────────────────┐  │  │  ┌────────────────────┐  │
│ ┌──────────┐ │  │  │ stripNamespace() │  │  │  │ data class:        │  │
│ │data class│ │  │  │ resolve(attrName,│  │  │  │  name: String      │  │
│ │ name      │ │  │  │   resId) → type │  │  │  │  packageName       │  │
│ │ packageNm │ │  │  │ supportedAttrs  │  │  │  │  version: Int      │  │
│ │ version   │ │  │  │ registerCustom()│  │  │  │  resources         │  │
│ │ resources │ │  │  └──────────────────┘  │  │  │  targetPackage     │  │
│ └──────────┘ │  │                        │  │  │  path: String      │  │
└──────────────┘  └────────────────────────┘  │  └────────────────────┘  │
                                               └──────────────────────────┘
```

### Component Responsibility Summary

| Component | Responsibility | Lifecycle | Thread |
|---|---|---|---|
| `ArsSkinEngine` | Central coordinator; skin state machine; View-tree walk orchestration | Application-scoped singleton | Thread-safe; main-thread View ops |
| `SkinContextWrapper` | Intercepts `getResources()` to return skin-aware `SkinResources` | Per-Activity/Context | Main thread |
| `SkinResources` | Resource lookup interceptor; skin-first, base-fallback | Same as wrapped Context | Any thread (Resources is thread-safe) |
| `SkinLayoutInflater` | `Factory2` that records View-to-resource mappings during XML inflation | Per-LayoutInflater | Main thread |
| `SkinViewMeta` | Stores which resource IDs map to which View attributes for re-application | Per-View; GC'd with View | Main thread |
| `ArsViewTreeWalker` | Iterative BFS over View tree applying skin updates without recursion | Ephemeral (per switch) | Main thread |
| `SkinAttributeResolver` | Maps attribute names to handler types; namespace stripping | Application-scoped; stateless | Any thread |
| `ArsSkinLoader` | Loads APK, validates skin package, creates `SkinPackage` | Ephemeral (per load) | Background (I/O) |
| `ArsSkinPackageManager` | CRUD for skin files on disk | Application-scoped | Background (I/O) |
| `SkinPackage` | Immutable value object representing a loaded skin | Created per load; GC'd when replaced | — |
| `SkinError` | Sealed class hierarchy for all error conditions | Ephemeral; returned, not thrown | Any thread |

---

## 5. Data Flow

### 5.1 Skin Loading Flow

```
User calls ArsSkinEngine.switchSkin(skinPath)
  │
  ▼
[1] ArsSkinEngine.switchSkin() — on caller's thread
  │
  ├── Dispatch to background coroutine
  │
  ▼
[2] ArsSkinLoader.loadFromFile(path) — on Dispatchers.IO
  │
  ├── Validate APK exists and is readable
  ├── PackageManager.getPackageArchiveInfo() → validate package
  ├── Verify ARS metadata (skin name, version, target package)
  ├── AssetManager.addAssetPath() + Resources creation  ← ONLY reflection here
  ├── Create SkinPackage(packageName, resources, ...)
  │
  ▼
[3] Back on Dispatchers.Main
  │
  ├── Acquire switchLock (Mutex)
  ├── Store previousSkin = activeSkin
  ├── Set activeSkin = newSkinPackage
  │
  ▼
[4] Walk View tree (on main thread)
  │
  ├── For each registered Activity (tracked via ActivityLifecycleCallbacks):
  │   └── ArsViewTreeWalker.walk(activity.window.decorView)
  │       ├── Iterative BFS queue
  │       ├── For each View in tree:
  │       │   ├── Check skinned-off flag (skip if set)
  │       │   ├── Lookup SkinViewMeta for this View
  │       │   ├── For each AttrBinding in meta:
  │       │   │   ├── Resolve resource from new activeSkin.resources
  │       │   │   ├── Apply via SkinAttributeHandler (setBackground, setTextColor, etc.)
  │       │   │   └── On failure: log warning, keep old value (graceful degradation)
  │       │   └── Continue to children
  │       └── Complete
  │
  ▼
[5] Post-switch
  │
  ├── Release switchLock
  ├── Release previousSkin.resources if not default
  ├── notifyListeners(oldSkin, newSkin)
  │   ├── Global SkinChangeListener instances
  │   └── Activity/Fragment onSkinChanged() callbacks
  │
  ▼
[DONE] Skin switch complete. All Views updated.
```

### 5.2 View Inflation Flow (how every View becomes skin-aware)

```
Activity.setContentView(R.layout.main)
  │
  ▼
LayoutInflater.inflate() → calls Factory2 chain
  │
  ▼
SkinLayoutInflater.onCreateView(parent, name, context, attrs)
  │
  ├── [1] Delegate to AppCompat's Factory2 first
  │       → Gets AppCompatTextView, AppCompatButton, etc.
  │       → Preserves full AppCompat behavior
  │
  ├── [2] If AppCompat returns null, delegate to parent LayoutInflater
  │       → Standard View inflation
  │
  ├── [3] View created → scan AttributeSet
  │       For each attribute:
  │       │
  │       ├── Extract attribute name (e.g., "android:background")
  │       ├── Strip namespace → "background"
  │       ├── Check against supportedAttrs registry
  │       ├── If supported AND has resource reference (not literal value):
  │       │   └── Create AttrBinding(view, attrName, resId, attrType)
  │       │
  │       └── If literals-only or unsupported: skip
  │
  ├── [4] Create SkinViewMeta(WeakReference(view), listOf(attrBindings))
  ├── [5] Register SkinViewMeta in ArsSkinEngine.viewRegistry
  │
  ├── [6] If activeSkin != null (skin is currently active):
  │       └── Immediately apply skin resources to this View
  │           (handles dynamically-added Views)
  │
  └── [7] Return View
```

### 5.3 Resource Resolution Flow (how getResources() returns skin values)

```
View.getResources().getColor(R.color.primary, theme)
  │
  ▼
SkinResources.getColor(resId, theme)
  │
  ├── [1] Translate resId to (resourceName, resourceType) via base Resources
  │       ResourceName = "primary", ResourceType = "color"
  │
  ├── [2] Look up in skin Resources:
  │       skinRes.getIdentifier("primary", "color", skinPackageName)
  │
  ├── [3] If resolved (resId != 0):
  │       └── Return skinRes.getColor(translatedResId, theme)
  │
  ├── [4] If NOT resolved:
  │       └── Fall through to base Resources:
  │           baseResources.getColor(resId, theme)
  │
  └── [RETURN] Color int (from skin or default)
```

This same pattern applies to `getDrawable()`, `getDimension()`, `getDimensionPixelSize()`, `getString()`, `getColorStateList()`, and `getText()`.

### 5.4 Theme Mode Flow

```
User calls ArsSkinEngine.setThemeMode(DARK)
  │
  ▼
[1] Update themeMode field
  │
  ▼
[2] Re-resolve skin Resources with new Configuration
  │
  ├── Create new Configuration with uiMode set accordingly
  ├── skinResources.updateConfiguration(config, displayMetrics)
  │
  ▼
[3] Trigger View-tree walk (same as skin switch paths [4]-[5] above)
  │
  └── All Views reflect dark-qualified resources (values-night/ in skin APK)
```

---

## 6. API Contracts

### 6.1 ArsSkinEngine

```kotlin
/**
 * Central engine for the ARS skinning framework.
 *
 * Application-scoped singleton. Manages skin lifecycle, View-tree updates,
 * listener dispatch, and Context wrapping.
 */
@RequiresApi(34)
object ArsSkinEngine {

    // ── Initialization ────────────────────────────────────────────────

    /**
     * Initialize the engine. Must be called once during Application.onCreate().
     * Idempotent — subsequent calls are no-ops.
     *
     * @param application The host Application instance.
     */
    fun init(application: Application)

    // ── Skin Control ──────────────────────────────────────────────────

    /**
     * Load and switch to a skin from a file path.
     *
     * Loading happens on a background thread. View updates happen on main.
     *
     * @param skinPath Absolute path to the skin APK file.
     * @return SkinResult.Success on success, SkinResult.Error with details on failure.
     */
    suspend fun switchSkin(skinPath: String): SkinResult<Unit>

    /**
     * Switch to an already-loaded SkinPackage.
     *
     * @param skin The loaded skin package.
     * @return SkinResult.Success on success.
     */
    suspend fun switchSkin(skin: SkinPackage): SkinResult<Unit>

    /**
     * Reset to the default (host app) resources.
     *
     * @return SkinResult.Success on success.
     */
    suspend fun resetToDefault(): SkinResult<Unit>

    /**
     * Set the theme mode (LIGHT/DARK).
     *
     * Updates Configuration on skin Resources and triggers View-tree refresh.
     *
     * @param mode The target theme mode.
     */
    fun setThemeMode(mode: ThemeMode)

    /** The currently active theme mode. */
    val currentThemeMode: ThemeMode

    /** The currently active skin package, or null if default. */
    val activeSkin: SkinPackage?

    // ── Context Wrapping ──────────────────────────────────────────────

    /**
     * Wrap a base Context with skin-aware Resources.
     *
     * Called automatically by ArsActivity/ArsFragment. Library consumers
     * can call this to wrap custom Contexts.
     *
     * @param base The base Context to wrap.
     * @return A ContextWrapper whose getResources() returns SkinResources.
     */
    fun wrapContext(base: Context): Context

    // ── LayoutInflater Integration ────────────────────────────────────

    /**
     * Create a LayoutInflater.Factory2 that intercepts View creation.
     *
     * @param delegate The next Factory2 in the chain (typically AppCompat's).
     * @param context The Context for resource resolution.
     * @return A new Factory2 that records View metadata during inflation.
     */
    fun createSkinFactory(delegate: LayoutInflater.Factory2, context: Context): LayoutInflater.Factory2

    // ── Listener Management ───────────────────────────────────────────

    /**
     * Register a global skin change listener.
     *
     * Called AFTER all Views have been updated. Weakly held — caller must
     * maintain own reference.
     *
     * @param listener The listener to register.
     */
    fun registerSkinChangeListener(listener: SkinChangeListener)

    /**
     * Unregister a global skin change listener.
     *
     * @param listener The listener to unregister.
     */
    fun unregisterSkinChangeListener(listener: SkinChangeListener)

    // ── Custom Attribute Handlers ─────────────────────────────────────

    /**
     * Register a handler for a custom attribute.
     *
     * @param attributeName The attribute name WITHOUT namespace prefix (e.g., "background").
     * @param handler The handler that applies the resource to the View.
     */
    fun registerAttributeHandler(attributeName: String, handler: SkinAttributeHandler)

    /**
     * Unregister a custom attribute handler.
     *
     * @param attributeName The attribute name to unregister.
     */
    fun unregisterAttributeHandler(attributeName: String)

    // ── Diagnostics ───────────────────────────────────────────────────

    /**
     * Get current engine diagnostics for debugging.
     *
     * @return SkinDiagnostics containing active skin info, registered Views count, etc.
     */
    fun getDiagnostics(): SkinDiagnostics

    // ── Lifecycle ─────────────────────────────────────────────────────

    /** Release all skin resources. Called when host Application is terminated. */
    fun dispose()
}
```

### 6.2 SkinPackage

```kotlin
/**
 * Immutable representation of a loaded skin package.
 *
 * Created by ArsSkinLoader. Consumed by ArsSkinEngine and SkinResources.
 */
data class SkinPackage(
    /** Human-readable skin name (from metadata). */
    val name: String,

    /** The Android package name declared in the skin APK's manifest. */
    val packageName: String,

    /** The target host app package name this skin is built for. */
    val targetPackage: String,

    /** Skin format version for compatibility checking. */
    val version: Int,

    /** The Resources instance for the skin APK. */
    val resources: Resources,

    /** File path to the skin APK on disk. */
    val path: String,

    /** Theme hint for the skin (optional). */
    val themeHint: ThemeMode?
) {

    enum class ThemeMode { LIGHT, DARK }

    /**
     * Dispose of the skin's Resources. Must be called when the skin is
     * no longer active AND no longer needed for fallback.
     */
    fun dispose() {
        // Resources does not implement Closeable; this is a no-op hook
        // for potential future native resource cleanup.
    }
}
```

### 6.3 SkinResult & SkinError

```kotlin
/**
 * Result type for skin operations. Never throw — always return.
 */
sealed class SkinResult<out T> {
    data class Success<T>(val value: T) : SkinResult<T>()
    data class Error(val error: SkinError) : SkinResult<Nothing>()
}

/**
 * Sealed hierarchy of all possible skin operation errors.
 */
sealed class SkinError(
    val message: String,
    val cause: Throwable? = null
) {
    /** The APK file does not exist or cannot be read. */
    class FileNotFound(path: String, cause: Throwable? = null)
        : SkinError("Skin file not found: $path", cause)

    /** The APK is corrupted or cannot be parsed. */
    class CorruptedPackage(path: String, cause: Throwable? = null)
        : SkinError("Skin package is corrupted: $path", cause)

    /** The APK is not an ARS skin package (missing metadata). */
    class NotASkinPackage(packageName: String)
        : SkinError("Package '$packageName' is not an ARS skin package")

    /** The skin targets a different host application. */
    class TargetMismatch(skinTarget: String, hostPackage: String)
        : SkinError("Skin targets '$skinTarget' but host is '$hostPackage'")

    /** The skin's format version is incompatible with the framework. */
    class IncompatibleVersion(skinVersion: Int, frameworkVersion: Int)
        : SkinError("Skin version $skinVersion is incompatible with framework version $frameworkVersion")

    /** Storage error — disk full, permission denied, etc. */
    class StorageError(message: String, cause: Throwable? = null)
        : SkinError(message, cause)

    /** Resource not found in skin or base — not an error, just a diagnostic. */
    class ResourceNotFound(resName: String, resType: String)
        : SkinError("Resource '$resName' of type '$resType' not found in skin or base")

    /** A skin switch was requested while another switch is in progress. */
    object SwitchInProgress
        : SkinError("A skin switch is already in progress")
}
```

### 6.4 SkinChangeListener

```kotlin
/**
 * Listener interface for skin change events.
 *
 * Fires AFTER all Views have been updated with the new skin.
 * Implementations are called on the main thread.
 */
interface SkinChangeListener {
    /**
     * Called when the skin has been changed.
     *
     * @param previous The previously active skin, or null if default.
     * @param current The newly active skin, or null if reset to default.
     */
    fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)
}
```

### 6.5 SkinAttributeHandler

```kotlin
/**
 * Handler interface for applying a skin resource to a View.
 *
 * Implement this to support custom View attributes in the skinning system.
 */
interface SkinAttributeHandler {
    /**
     * Apply a resource value to a View.
     *
     * @param view The target View.
     * @param resId The resource ID to resolve.
     * @param resources The current skin-aware Resources (use this for resolution).
     */
    fun apply(view: View, resId: Int, resources: Resources)
}
```

### 6.6 ArsActivity

```kotlin
/**
 * Base Activity for ARS-skinning-enabled applications.
 *
 * Extend this instead of AppCompatActivity. The Activity automatically:
 * - Wraps its base Context with skin-aware Resources
 * - Installs LayoutInflater.Factory2 for automatic View tracking
 * - Responds to skin changes with in-place View updates
 * - Preserves instance state across skin changes
 */
@RequiresApi(34)
open class ArsActivity : AppCompatActivity(), SkinChangeListener {

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?)
    override fun onDestroy()

    // ── Instance State ───────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle)
    override fun onRestoreInstanceState(savedInstanceState: Bundle)

    // ── Skin Integration ─────────────────────────────────────────────

    /**
     * Called after all Views have been updated with the new skin.
     * Override to perform additional post-switch work (e.g., update action bar).
     */
    open fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)

    // SkinChangeListener implementation (internal delegation)
    override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)

    // ── Convenience ──────────────────────────────────────────────────

    /** Load and switch to a skin. Wraps ArsSkinEngine.switchSkin(). */
    protected suspend fun switchSkin(skinPath: String): SkinResult<Unit>

    /** Reset to default skin. Wraps ArsSkinEngine.resetToDefault(). */
    protected suspend fun resetSkin(): SkinResult<Unit>

    /** Set the theme mode. Wraps ArsSkinEngine.setThemeMode(). */
    protected fun setSkinThemeMode(mode: ThemeMode)

    // ── View Skinning Control ────────────────────────────────────────

    /**
     * Re-apply the current skin to this Activity's View tree.
     * Useful after programmatically adding Views or changing layout.
     */
    fun refreshSkin()
}
```

### 6.7 ArsFragment

```kotlin
/**
 * Base Fragment for ARS-skinning-enabled applications.
 *
 * Extend this instead of Fragment. Works identically to ArsActivity
 * but scoped to the Fragment's View lifecycle.
 */
@RequiresApi(34)
open class ArsFragment : Fragment(), SkinChangeListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?

    /**
     * Called after all Views in this Fragment have been updated.
     */
    open fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)

    override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)

    /** Re-apply the current skin to this Fragment's View tree. */
    fun refreshSkin()
}
```

### 6.8 ArsApplication

```kotlin
/**
 * Base Application for ARS-skinning-enabled applications.
 *
 * Extend this instead of Application. Initializes ArsSkinEngine on startup.
 * No other configuration required.
 */
@RequiresApi(34)
open class ArsApplication : Application() {

    override fun onCreate()

    /** Get the skin engine instance. */
    val skinEngine: ArsSkinEngine
}
```

### 6.9 ArsSkinLoader (Internal)

```kotlin
/**
 * Loads and validates skin APK packages. Internal — used by ArsSkinEngine.
 */
internal class ArsSkinLoader(private val context: Context) {

    /**
     * Load a skin package from a file path.
     *
     * @param skinPath Absolute path to the skin APK.
     * @return SkinResult containing SkinPackage or SkinError.
     */
    suspend fun loadFromFile(skinPath: String): SkinResult<SkinPackage>

    /**
     * Load a skin package from app assets (copies to internal storage first).
     *
     * @param assetPath Path within assets/ directory.
     * @return SkinResult containing SkinPackage or SkinError.
     */
    suspend fun loadFromAssets(assetPath: String): SkinResult<SkinPackage>

    /**
     * Validate whether a file is a well-formed ARS skin package.
     * Does NOT load resources — lightweight check.
     *
     * @param skinPath Path to the APK file.
     * @return SkinResult with Unit on success, SkinError on failure.
     */
    fun validateStructure(skinPath: String): SkinResult<Unit>

    /**
     * Read skin metadata without fully loading resources.
     *
     * @param skinPath Path to the APK file.
     * @return SkinResult with SkinPackage (resources = null) or SkinError.
     */
    fun readMetadata(skinPath: String): SkinResult<SkinPackage>
}
```

### 6.10 ArsSkinPackageManager (Internal)

```kotlin
/**
 * Manages skin packages on disk. Internal — used via ArsSkinEngine diagnostics.
 */
internal class ArsSkinPackageManager(private val context: Context) {

    /**
     * List all installed skin files in the skin directory.
     *
     * @return List of File objects representing skin APKs.
     */
    fun listInstalledSkins(): List<File>

    /**
     * Delete a skin package by filename.
     *
     * @param skinFileName The filename (not path) of the skin APK.
     * @return true if deleted, false if not found.
     */
    fun deleteSkin(skinFileName: String): Boolean

    /**
     * Delete all installed skin packages.
     */
    fun clearAllSkins()

    /**
     * Get the directory where skin packages are stored.
     */
    fun getSkinDirectory(): File
}
```

### 6.11 SkinResources (Internal)

```kotlin
/**
 * Resources subclass that intercepts resource lookups.
 *
 * For each resource lookup, checks the skin Resources first, then falls
 * through to the base (host app) Resources. This is the core mechanism
 * that makes getResources().getColor() return skin-aware values.
 */
internal class SkinResources(
    private val baseResources: Resources,
    private val skinResources: Resources?,
    private val skinPackageName: String?,
    private val hostPackageName: String
) : Resources(baseResources.assets, baseResources.displayMetrics, baseResources.configuration) {

    // Override all resource accessors:
    override fun getColor(id: Int, theme: Resources.Theme?): Int
    override fun getColorStateList(id: Int, theme: Resources.Theme?): ColorStateList
    override fun getDrawable(id: Int, theme: Resources.Theme?): Drawable
    override fun getDrawableForDensity(id: Int, density: Int, theme: Resources.Theme?): Drawable
    override fun getDimension(id: Int): Float
    override fun getDimensionPixelOffset(id: Int): Int
    override fun getDimensionPixelSize(id: Int): Int
    override fun getString(id: Int): String
    override fun getText(id: Int): CharSequence
    override fun getValue(id: Int, outValue: TypedValue, resolveRefs: Boolean)
    override fun getValue(name: String, outValue: TypedValue, resolveRefs: Boolean)

    // Package-override: resolves resource names from skin if available
    override fun getResourcePackageName(id: Int): String

    /**
     * Update the skin reference (called on skin switch).
     */
    fun updateSkin(skinResources: Resources?, skinPackageName: String?)
}
```

### 6.12 SkinLayoutInflater (Internal)

```kotlin
/**
 * LayoutInflater.Factory2 that intercepts View creation during XML inflation.
 *
 * Delegates to AppCompat's Factory2 first to preserve AppCompatView substitution.
 * Then scans the AttributeSet to build SkinViewMeta for each View.
 */
internal class SkinLayoutInflater(
    private val delegate: LayoutInflater.Factory2?,
    private val engine: ArsSkinEngine,
    private val context: Context
) : LayoutInflater.Factory2 {

    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View?

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View?
}
```

### 6.13 SkinViewMeta (Internal)

```kotlin
/**
 * Metadata linking a View to the skin resources it uses.
 *
 * Stored in ArsSkinEngine.viewRegistry, keyed by View identity.
 * Uses WeakReference to avoid leaking Views.
 */
internal data class SkinViewMeta(
    val viewRef: WeakReference<View>,
    val attributes: List<AttrBinding>
)

/**
 * A single attribute-to-resource binding for a View.
 */
internal data class AttrBinding(
    /** The resource ID referenced by the XML attribute. */
    val resId: Int,

    /** The attribute name WITHOUT namespace prefix (e.g., "background"). */
    val attributeName: String,

    /** The type of resource (COLOR, DRAWABLE, DIMENSION, etc.). */
    val resourceType: ResourceType
)

internal enum class ResourceType {
    COLOR,
    DRAWABLE,
    DIMENSION,
    STRING,
    COLOR_STATE_LIST,
    TEXT,
    UNKNOWN
}
```

### 6.14 SkinAttributeResolver (Internal)

```kotlin
/**
 * Resolves attribute names and determines resource types.
 *
 * Handles namespace stripping ("android:background" → "background").
 * Maintains the registry of supported attributes and their handler types.
 */
internal object SkinAttributeResolver {

    /** Attributes that ARS can skin by default. */
    val DEFAULT_SUPPORTED_ATTRIBUTES: Set<String>

    /**
     * Strip the namespace prefix from an attribute name.
     * "android:background" → "background"
     * "app:cornerRadius" → "cornerRadius"
     * "background" → "background"
     */
    fun stripNamespace(qualifiedName: String): String

    /**
     * Check if an attribute is supported for skinning.
     */
    fun isSupported(attributeName: String): Boolean

    /**
     * Determine the resource type for an attribute.
     * "background" → DRAWABLE (can also be COLOR)
     * "textColor" → COLOR
     * "src" → DRAWABLE
     */
    fun resolveType(attributeName: String, resId: Int, resources: Resources): ResourceType

    /**
     * Register a custom attribute to be recognized by the resolver.
     */
    fun registerCustom(attributeName: String): Boolean
}
```

### 6.15 ArsViewTreeWalker (Internal)

```kotlin
/**
 * Non-recursive BFS walker for applying skin updates to a View tree.
 *
 * Uses a queue-based approach to avoid stack overflow on deep hierarchies.
 * Skips Views marked with ars_skip_skinning tag.
 */
internal object ArsViewTreeWalker {

    /**
     * Walk the View tree starting from root and apply the current skin.
     *
     * @param root The root View (typically decorView).
     * @param engine The ArsSkinEngine for metadata lookups.
     */
    fun walk(root: View, engine: ArsSkinEngine)
}
```

---

## 7. Key Design Decisions

### DD-01: ContextWrapper over AssetManager.addAssetPath

**Decision:** Use `ContextWrapper.getResources()` override returning `SkinResources`.

**Trade-off analysis:**

| Approach | Pros | Cons |
|---|---|---|
| `AssetManager.addAssetPath()` (reflection) | Closest to true overlay; skin resources merged into single AssetManager | `addAssetPath` is @hide on API 35+ and may be blocked by Google Play policy; fragile |
| `PackageManager.getResourcesForApplication()` | v1 approach; no reflection | Creates isolated Resources — Views can't see it; defeats the purpose |
| **ContextWrapper + SkinResources** (chosen) | No reflection; works entirely within public API; all getResources() calls see skin values | Slightly more indirection; resource-name-based lookup instead of ID-based merging |

**Additional justification:** `SkinResources` resolves by resource name (obtained via `baseResources.getResourceName(resId)`), then looks up the same name in the skin APK. This is a stable, public-API approach that achieves the same result as AssetManager merging.

### DD-02: LayoutInflater.Factory2 (not just ContextWrapper)

**Decision:** Keep both ContextWrapper and Factory2.

**Why Factory2 is still needed even with ContextWrapper:**

Some View subclasses resolve resources during construction/initialization and cache the result. For example:
- `TextView` resolves `textColor` during init and stores it as a field; never calls `getResources().getColor()` again
- `ImageView` resolves `src` during `setImageResource()` and caches the `Drawable`
- `View` resolves `background` during construction

The ContextWrapper approach handles Views that call `getResources()` on every draw, but the Factory2 approach ensures ALL Views update by explicitly pushing new resource values to each View's setter.

### DD-03: Iterative BFS for View-tree Walk

**Decision:** Use a `LinkedList<View>` queue instead of recursive traversal.

**Rationale:**
- Android View trees can be arbitrarily deep (nested ScrollViews, RecyclerViews, etc.)
- Recursive traversal risks `StackOverflowError` on deeply nested hierarchies
- BFS processes Views level-by-level, which is visually consistent (parents update before children)
- Queue-based approach is trivial to implement and well-understood

### DD-04: WeakReference-based View Registry

**Decision:** Store `WeakReference<View>` in `SkinViewMeta`, not strong references.

**Rationale:**
- Views come and go (Fragment transactions, RecyclerView recycling)
- Strong references would leak Views and their entire Context/Activity
- When a View is GC'd, its metadata is lazily cleaned up during the next View-tree walk
- A `ReferenceQueue` + cleanup thread is overkill; check-and-remove on access is sufficient

### DD-05: Resource-Name-Based Lookup (not ID-based)

**Decision:** Resolve skin resources by (name, type) tuple, not by resource ID.

**Rationale:**
- Resource IDs in the skin APK are different from the host APK
- AAPT assigns IDs sequentially; there's no guarantee `R.color.primary == 0x7f060001` in both APKs
- Looking up by name (`getIdentifier("primary", "color", skinPackageName)`) is the only reliable cross-package approach
- Performance: `getIdentifier()` is a hashmap lookup in `resources.arsc` — fast enough for View-tree walks

**Performance note:** To avoid repeated `getIdentifier()` calls, the resolver caches `(hostResId → skinResId)` mappings in a `LruCache` that is invalidated on skin switch.

### DD-06: Coroutines for Async I/O

**Decision:** Use Kotlin coroutines (`suspend` functions + `Dispatchers.IO`) for skin loading.

**Rationale:**
- Skin package loading involves file I/O and APK parsing — must not block main thread
- Coroutines are lighter-weight than RxJava or raw threads
- `suspend` functions compose naturally with the rest of the Kotlin codebase
- The `switchSkin()` function is `suspend` — callers can launch it in their own coroutine scope
- No additional dependency; coroutines ship with Kotlin stdlib

### DD-07: Sealed Class Error Hierarchy

**Decision:** All operations return `SkinResult<T>` with a sealed `SkinError` hierarchy.

**Rationale:**
- Framework must never crash the host app (NFR-03)
- Exceptions are for programming errors; skin failures are expected runtime conditions
- Sealed classes make error handling exhaustive — `when` expressions force handling all cases
- Each error variant carries contextual information (file path, package name, etc.)
- Pairs well with Kotlin's `when` and result type patterns

### DD-08: Skin Package Format

**Decision:** Skin packages are standard APKs with ARS metadata in AndroidManifest.xml.

**Format specification:**
```
AndroidManifest.xml must contain:
├── package="com.example.skin.mytheme"     (unique skin package name)
├── <meta-data
│       android:name="ars-skin-name"
│       android:value="My Theme" />        (human-readable name)
├── <meta-data
│       android:name="ars-skin-version"
│       android:value="1" />               (integer version)
├── <meta-data
│       android:name="ars-target-package"
│       android:value="com.example.app" /> (target host app)
│
res/ directory:
├── values/colors.xml       (skin color overrides)
├── values/drawables.xml    (skin drawable references)
├── values/dimens.xml       (skin dimension overrides)
├── drawable/               (replacement drawables)
├── values-night/           (dark theme overrides)
│
NO classes.dex               (resource-only; no executable code)
```

Metadata is read via `PackageManager.getPackageArchiveInfo()` + `Bundle` from `PackageInfo.applicationInfo.metaData`.

### DD-09: Graceful Degradation on Missing Resources

**Decision:** When a skin doesn't define a resource used by a View, the View retains its default value silently.

**Rationale:**
- A skin author may only want to override colors, leaving drawables and dimensions at defaults
- Partial skin packages are a valid use case (e.g., "dark theme" that only changes colors)
- Warning logs in debug builds help skin authors find missing resources
- Release builds are silent to avoid log spam
- The `SkinError.ResourceNotFound` type exists for programmatic detection if needed

### DD-10: Skin ID Cache

**Decision:** Cache `(hostResId → skinResId)` mappings in an `LruCache` keyed by `Pair<Int, Resources>`.

**Implementation detail:**
```
LruCache<Pair<Int, WeakReference<Resources>>, Int>
```
- Key includes a `WeakReference` to the skin Resources so entries are invalidated when the skin is GC'd
- Cache is cleared entirely on skin switch
- Typical hit rate for a second View-tree walk on the same skin is >95%

---

## 8. Threading Model

```
┌─────────────────────────────────────────────────────────────┐
│                      CALLER'S THREAD                         │
│                                                              │
│  switchSkin(path) ─────────────────────────────┐             │
│                                                │             │
└────────────────────────────────────────────────┼─────────────┘
                                                 │
                    ┌────────────────────────────▼──────────┐
                    │        Dispatchers.IO                  │
                    │                                        │
                    │  [1] ArsSkinLoader.loadFromFile()      │
                    │      ├── validateStructure()            │
                    │      ├── PackageManager query           │
                    │      ├── AssetManager.addAssetPath()    │
                    │      └── Create Resources instance      │
                    │                                        │
                    │  Result: SkinPackage                    │
                    └────────────────┬───────────────────────┘
                                     │
                    ┌────────────────▼───────────────────────┐
                    │        Dispatchers.Main                 │
                    │                                         │
                    │  [2] ArsSkinEngine (with switchLock)    │
                    │      ├── Store activeSkin               │
                    │      ├── Notify SkinResources instances │
                    │      ├── ArsViewTreeWalker.walk()       │
                    │      │   └── Iterate all tracked Views │
                    │      └── notifyListeners()              │
                    │                                         │
                    │  [3] Callbacks                         │
                    │      ├── SkinChangeListener.onChanged() │
                    │      ├── ArsActivity.onSkinChanged()    │
                    │      └── ArsFragment.onSkinChanged()    │
                    └─────────────────────────────────────────┘
```

### Thread Safety Guarantees

| Concern | Mechanism |
|---|---|
| Concurrent `switchSkin()` calls | `Mutex` in `ArsSkinEngine`; second call returns `SkinError.SwitchInProgress` or queues (configurable) |
| Skin switch during View inflation | Inflation is always on main thread; skin switch also dispatches View updates on main — serialized by main thread |
| Skin switch during Activity destruction | ViewRegistry cleanup on `onDestroy()`; WeakReferences prevent leaks |
| Resources access from background thread | `SkinResources` delegates to `Resources` which is thread-safe per Android docs |
| Listener registration during callback dispatch | Copy-on-write snapshot of listener set before dispatch |

---

## 9. Error Handling

### Error Propagation Strategy

```
Public API:    returns SkinResult<T>  —  never throws
Internal API:  returns SkinResult<T>  —  never throws
Platform:      try/catch at boundaries, convert to SkinError
```

Every exception from Android framework APIs is caught at the boundary and wrapped in the appropriate `SkinError` subclass. The framework itself never throws (except Kotlin `require()` for programming errors like null parameters — these indicate caller bugs).

### Error Recovery

| Error | Recovery Behavior |
|---|---|
| `FileNotFound` | Return error; no state change |
| `CorruptedPackage` | Return error; no state change |
| `NotASkinPackage` | Return error; no state change |
| `TargetMismatch` | Return error; no state change |
| `IncompatibleVersion` | Return error; no state change |
| `StorageError` | Return error; no state change |
| `ResourceNotFound` | Log warning (debug); View retains default value |
| `SwitchInProgress` | Return error; caller can retry |

**Key guarantee:** If ANY error occurs during `switchSkin()`, the previous skin remains active. The state machine is transactional — `activeSkin` is only updated after the new skin is fully validated and loaded.

### Debug Diagnostics

```kotlin
data class SkinDiagnostics(
    val activeSkinName: String?,
    val activeSkinPackage: String?,
    val themeMode: SkinPackage.ThemeMode,
    val registeredViewCount: Int,
    val aliveViewCount: Int,
    val registeredAttributeCount: Int,
    val cachedIdMappings: Int,
    val lastSwitchDurationMs: Long,
    val lastError: SkinError?
)
```

---

## 10. File & Method Structure

### Module: `ars-core` (library)

```
ars-core/
├── build.gradle.kts
│   └── (unchanged from v1: AGP 8.2.2, compileSdk/minSdk/targetSdk=34,
│        Kotlin 1.9.20, AndroidX Core KTX 1.12.0, AppCompat 1.6.1,
│        + kotlinx-coroutines-android)
│
└── src/
    ├── main/java/com/kagawagao/ars/
    │   │
    │   ├── ArsSkinEngine.kt              ← REWRITTEN (replaces ArsSkinManager)
    │   │   ├── object ArsSkinEngine
    │   │   │   ├── fun init(application: Application)
    │   │   │   ├── suspend fun switchSkin(path: String): SkinResult<Unit>
    │   │   │   ├── suspend fun switchSkin(skin: SkinPackage): SkinResult<Unit>
    │   │   │   ├── suspend fun resetToDefault(): SkinResult<Unit>
    │   │   │   ├── fun setThemeMode(mode: ThemeMode)
    │   │   │   ├── val currentThemeMode: ThemeMode
    │   │   │   ├── val activeSkin: SkinPackage?
    │   │   │   ├── fun wrapContext(base: Context): Context
    │   │   │   ├── fun createSkinFactory(delegate: Factory2, ctx: Context): Factory2
    │   │   │   ├── fun registerSkinChangeListener(listener: SkinChangeListener)
    │   │   │   ├── fun unregisterSkinChangeListener(listener: SkinChangeListener)
    │   │   │   ├── fun registerAttributeHandler(name: String, handler: SkinAttributeHandler)
    │   │   │   ├── fun unregisterAttributeHandler(name: String)
    │   │   │   ├── fun getDiagnostics(): SkinDiagnostics
    │   │   │   ├── fun dispose()
    │   │   │   ├── internal fun registerView(view: View, meta: SkinViewMeta)
    │   │   │   ├── internal fun unregisterView(view: View)
    │   │   │   ├── internal fun getViewMeta(view: View): SkinViewMeta?
    │   │   │   ├── internal fun applySkinToView(view: View, meta: SkinViewMeta)
    │   │   │   ├── private fun notifyListeners(old: SkinPackage?, new: SkinPackage?)
    │   │   │   └── private val switchLock: Mutex
    │   │   │
    │   │   ├── ArsApplication.kt          ← REWRITTEN
    │   │   │   └── open class ArsApplication : Application()
    │   │   │       ├── override fun onCreate()
    │   │   │       └── val skinEngine: ArsSkinEngine
    │   │   │
    │   │   ├── ArsActivity.kt             ← REWRITTEN
    │   │   │   └── open class ArsActivity : AppCompatActivity(), SkinChangeListener
    │   │   │       ├── override fun onCreate(savedInstanceState: Bundle?)
    │   │   │       ├── override fun onDestroy()
    │   │   │       ├── override fun onSaveInstanceState(outState: Bundle)
    │   │   │       ├── override fun onRestoreInstanceState(savedInstanceState: Bundle)
    │   │   │       ├── open fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)
    │   │   │       ├── protected suspend fun switchSkin(path: String): SkinResult<Unit>
    │   │   │       ├── protected suspend fun resetSkin(): SkinResult<Unit>
    │   │   │       ├── protected fun setSkinThemeMode(mode: ThemeMode)
    │   │   │       ├── fun refreshSkin()
    │   │   │       └── private fun wrapContext()
    │   │   │
    │   │   ├── ArsFragment.kt             ← NEW
    │   │   │   └── open class ArsFragment : Fragment(), SkinChangeListener
    │   │   │       ├── override fun onCreateView(inflater, container, savedState): View?
    │   │   │       ├── open fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)
    │   │   │       ├── fun refreshSkin()
    │   │   │       └── private fun wrapContext()
    │   │   │
    │   │   ├── skin/
    │   │   │   ├── SkinPackage.kt          ← NEW
    │   │   │   │   └── data class SkinPackage(name, packageName, targetPackage, version, resources, path, themeHint)
    │   │   │   │
    │   │   │   ├── SkinResources.kt        ← NEW
    │   │   │   │   └── internal class SkinResources : Resources
    │   │   │   │       ├── constructor(base, skin, skinPkg, hostPkg)
    │   │   │   │       ├── override fun getColor(id, theme): Int
    │   │   │   │       ├── override fun getColorStateList(id, theme): ColorStateList
    │   │   │   │       ├── override fun getDrawable(id, theme): Drawable
    │   │   │   │       ├── override fun getDrawableForDensity(id, density, theme): Drawable
    │   │   │   │       ├── override fun getDimension(id): Float
    │   │   │   │       ├── override fun getDimensionPixelOffset(id): Int
    │   │   │   │       ├── override fun getDimensionPixelSize(id): Int
    │   │   │   │       ├── override fun getString(id): String
    │   │   │   │       ├── override fun getText(id): CharSequence
    │   │   │   │       ├── override fun getValue(id, outValue, resolveRefs)
    │   │   │   │       ├── override fun getValue(name, outValue, resolveRefs)
    │   │   │   │       ├── override fun getResourcePackageName(id): String
    │   │   │   │       ├── fun updateSkin(skinResources, skinPackageName)
    │   │   │   │       └── private fun resolveSkinId(hostResId): Int
    │   │   │   │
    │   │   │   └── SkinContextWrapper.kt   ← NEW
    │   │   │       └── internal class SkinContextWrapper : ContextWrapper
    │   │   │           ├── constructor(base, skinResources)
    │   │   │           ├── override fun getResources(): Resources
    │   │   │           └── override fun getTheme(): Resources.Theme
    │   │   │
    │   │   ├── inflate/
    │   │   │   ├── SkinLayoutInflater.kt   ← NEW
    │   │   │   │   └── internal class SkinLayoutInflater : LayoutInflater.Factory2
    │   │   │   │       ├── constructor(delegate, engine, context)
    │   │   │   │       ├── override fun onCreateView(parent, name, context, attrs): View?
    │   │   │   │       ├── override fun onCreateView(name, context, attrs): View?
    │   │   │   │       └── private fun scanAttributes(attrs, engine): List<AttrBinding>
    │   │   │   │
    │   │   │   ├── SkinViewMeta.kt         ← NEW
    │   │   │   │   ├── internal data class SkinViewMeta(viewRef, attributes)
    │   │   │   │   ├── internal data class AttrBinding(resId, attributeName, resourceType)
    │   │   │   │   └── internal enum class ResourceType { COLOR, DRAWABLE, DIMENSION, STRING, COLOR_STATE_LIST, TEXT, UNKNOWN }
    │   │   │   │
    │   │   │   └── ArsViewTreeWalker.kt    ← NEW
    │   │   │       └── internal object ArsViewTreeWalker
    │   │   │           ├── fun walk(root: View, engine: ArsSkinEngine)
    │   │   │           └── private fun shouldSkip(view: View): Boolean
    │   │   │
    │   │   ├── loader/
    │   │   │   ├── ArsSkinLoader.kt        ← REWRITTEN (replaces SkinLoader)
    │   │   │   │   └── internal class ArsSkinLoader(context: Context)
    │   │   │   │       ├── suspend fun loadFromFile(skinPath: String): SkinResult<SkinPackage>
    │   │   │   │       ├── suspend fun loadFromAssets(assetPath: String): SkinResult<SkinPackage>
    │   │   │   │       ├── fun validateStructure(skinPath: String): SkinResult<Unit>
    │   │   │   │       ├── fun readMetadata(skinPath: String): SkinResult<SkinPackage>
    │   │   │   │       └── private fun createResourcesForPackage(packageInfo, path): Resources
    │   │   │   │
    │   │   │   └── ArsSkinPackageManager.kt ← NEW
    │   │   │       └── internal class ArsSkinPackageManager(context: Context)
    │   │   │           ├── fun listInstalledSkins(): List<File>
    │   │   │           ├── fun deleteSkin(skinFileName: String): Boolean
    │   │   │           ├── fun clearAllSkins()
    │   │   │           └── fun getSkinDirectory(): File
    │   │   │
    │   │   ├── attr/
    │   │   │   ├── SkinAttributeResolver.kt ← NEW
    │   │   │   │   └── internal object SkinAttributeResolver
    │   │   │   │       ├── val DEFAULT_SUPPORTED_ATTRIBUTES: Set<String>
    │   │   │   │       ├── fun stripNamespace(qualifiedName: String): String
    │   │   │   │       ├── fun isSupported(attributeName: String): Boolean
    │   │   │   │       ├── fun resolveType(attributeName, resId, resources): ResourceType
    │   │   │   │       ├── fun registerCustom(attributeName: String): Boolean
    │   │   │   │       └── fun unregisterCustom(attributeName: String): Boolean
    │   │   │   │
    │   │   │   └── SkinAttributeHandler.kt ← NEW
    │   │   │       └── interface SkinAttributeHandler
    │   │   │           └── fun apply(view: View, resId: Int, resources: Resources)
    │   │   │
    │   │   ├── callback/
    │   │   │   ├── SkinChangeListener.kt   ← NEW
    │   │   │   │   └── interface SkinChangeListener
    │   │   │   │       └── fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)
    │   │   │   │
    │   │   │   └── SkinDiagnostics.kt      ← NEW
    │   │   │       └── data class SkinDiagnostics(activeSkinName, activeSkinPackage, themeMode,
    │   │   │           registeredViewCount, aliveViewCount, registeredAttributeCount,
    │   │   │           cachedIdMappings, lastSwitchDurationMs, lastError)
    │   │   │
    │   │   └── error/
    │   │       └── SkinError.kt            ← NEW
    │   │           ├── sealed class SkinError(message, cause)
    │   │           │   ├── class FileNotFound(path, cause)
    │   │           │   ├── class CorruptedPackage(path, cause)
    │   │           │   ├── class NotASkinPackage(packageName)
    │   │           │   ├── class TargetMismatch(skinTarget, hostPackage)
    │   │           │   ├── class IncompatibleVersion(skinVersion, frameworkVersion)
    │   │           │   ├── class StorageError(message, cause)
    │   │           │   ├── class ResourceNotFound(resName, resType)
    │   │           │   └── object SwitchInProgress
    │   │           └── sealed class SkinResult<out T>
    │   │               ├── data class Success<T>(value: T) : SkinResult<T>()
    │   │               └── data class Error(error: SkinError) : SkinResult<Nothing>()
    │   │
    │   └── res/values/
    │       ├── ids.xml                      ← NEW (ars_skip_skinning ID)
    │       └── attrs.xml                    ← NEW (custom attribute declarations if needed)
    │
    └── test/java/com/kagawagao/ars/
        ├── ArsSkinEngineTest.kt             ← REWRITTEN
        │   ├── skinLoading_returnsSuccess_whenValidPackage
        │   ├── skinLoading_returnsError_whenFileNotFound
        │   ├── skinLoading_returnsError_whenCorruptedPackage
        │   ├── switchSkin_updatesActiveSkin
        │   ├── switchingTwice_serializesProperly
        │   └── resetToDefault_clearsActiveSkin
        │
        ├── SkinAttributeResolverTest.kt     ← NEW
        │   ├── stripNamespace_removesAndroidPrefix
        │   ├── stripNamespace_handlesNoNamespace
        │   ├── stripNamespace_handlesOtherPrefixes
        │   ├── isSupported_returnsTrueForKnownAttributes
        │   ├── isSupported_returnsFalseForUnknown
        │   ├── resolveType_identifiesColorAttributes
        │   ├── resolveType_identifiesDrawableAttributes
        │   └── customAttribute_registration
        │
        ├── SkinResourcesTest.kt             ← NEW
        │   ├── getColor_returnsSkinValue_whenAvailable
        │   ├── getColor_fallsBackToBase_whenNotInSkin
        │   ├── getDrawable_returnsSkinDrawable
        │   ├── getDimension_returnsSkinDimension
        │   └── getString_fallsBackToBase
        │
        ├── SkinErrorTest.kt                ← NEW
        │   └── allErrors_haveDescriptiveMessages
        │
        └── ArsViewTreeWalkerTest.kt         ← NEW
            ├── walk_appliesSkinToAllViews
            ├── walk_skipsViewsWithSkipTag
            └── walk_handlesDeepHierarchy
```

### Module: `app` (demo)

```
app/
├── build.gradle.kts
│   └── (unchanged structure; may add coroutines dependency for demo)
│
└── src/main/java/com/kagawagao/ars/demo/
    ├── DemoApplication.kt                  ← MINOR UPDATE
    │   └── class DemoApplication : ArsApplication()
    │
    ├── MainActivity.kt                     ← REWRITTEN
    │   └── class MainActivity : ArsActivity()
    │       ├── Demonstrates: switchSkin(), resetSkin(), setSkinThemeMode()
    │       ├── Shows: TextViews, Buttons, ImageViews, compound drawables
    │       ├── Preserves: EditText input, ScrollView position on skin switch
    │       └── Includes: 2+ built-in skin packages in assets/
    │
    └── (additional demo Fragments as needed)
```

---

## 11. Migration Notes

### What v1 Code Is REMOVED

| v1 File | Disposition | Reason |
|---|---|---|
| `ArsSkinManager.kt` | **Removed** | Replaced by `ArsSkinEngine.kt` — fundamentally different skin approach; v1's singleton + isolated Resources pattern is the root cause of all v1 defects |
| `SkinLoader.kt` | **Removed** | Replaced by `ArsSkinLoader.kt` + `ArsSkinPackageManager.kt` — new loader validates ARS metadata, returns sealed `SkinResult` types, and is internal-only |
| `ResourceOverlayHelper.kt` | **Removed** | v1's `enableOverlay()` threw `UnsupportedOperationException` — completely non-functional. The genuine overlay mechanism in v2 (ContextWrapper + SkinResources) needs no system-level OverlayManager calls |
| `SkinAttribute.kt` | **Removed** | Replaced by `SkinLayoutInflater.kt` + `SkinAttributeResolver.kt` + `ArsViewTreeWalker.kt` — v1's manual `applySkin(view, attrs)` approach is replaced by automatic Factory2-based interception |

### What v1 Code Is REPLACED/REWRITTEN

| v1 File | v2 Replacement | Changes |
|---|---|---|
| `ArsApplication.kt` | `ArsApplication.kt` | Now initializes `ArsSkinEngine` (not `ArsSkinManager`). Adds `skinEngine` property. Registers `ActivityLifecycleCallbacks` for View-tree tracking. |
| `ArsActivity.kt` | `ArsActivity.kt` | No longer implements `ThemeChangeListener`. Context wrapping in `attachBaseContext()`. Installs `SkinLayoutInflater` via `onCreate()`. `onSkinChanged()` replaces `onThemeChanged()`. No `recreate()` call. Adds `onSaveInstanceState`/`onRestoreInstanceState`. Removes `loadSkin()`/`resetSkin()`/`switchTheme()` convenience methods (use `switchSkin()`/`resetSkin()`/`setSkinThemeMode()` instead). |

### What Is NEW in v2

| v2 File | Purpose |
|---|---|
| `ArsSkinEngine.kt` (object) | Central coordinator replacing `ArsSkinManager` |
| `SkinPackage.kt` (data class) | Immutable skin package representation |
| `SkinResources.kt` (Resources subclass) | Resource lookup interceptor — core of genuine overlay |
| `SkinContextWrapper.kt` (ContextWrapper) | Context wrapping that injects SkinResources |
| `SkinLayoutInflater.kt` (Factory2) | Automatic View interception during XML inflation |
| `SkinViewMeta.kt` (data class) | View-to-resource binding metadata |
| `ArsViewTreeWalker.kt` (object) | Non-recursive BFS View-tree skin applicator |
| `ArsSkinLoader.kt` (class) | APK loading with validation and metadata reading |
| `ArsSkinPackageManager.kt` (class) | CRUD for skin packages on disk |
| `SkinAttributeResolver.kt` (object) | Attribute name resolution with namespace stripping |
| `SkinAttributeHandler.kt` (interface) | Custom attribute handler contract |
| `SkinChangeListener.kt` (interface) | Skin change callback contract |
| `SkinDiagnostics.kt` (data class) | Runtime diagnostics snapshot |
| `SkinError.kt` (sealed class) | Error type hierarchy |
| `SkinResult.kt` (sealed class) | Result wrapper for all operations |
| `ArsFragment.kt` (class) | Fragment base class for skinning |

### Migration Path for v1 Users

Since v2 is a ground-up rebuild with incompatible APIs, there is no incremental upgrade path. Users should:

1. Remove the v1 `ars-core` dependency entirely
2. Add v2 `ars-core` dependency
3. Change `ArsApplication` → `ArsApplication` (same name, new package behavior)
4. Change `ArsActivity` → `ArsActivity` (same name, completely new implementation)
5. Add `ArsFragment` for any Fragment-based screens
6. Remove all manual `SkinAttribute.applySkin()` calls — no longer needed
7. Remove all `ArsSkinManager.getInstance()` calls — use `skinEngine` property on `ArsApplication` instead
8. Replace `loadSkin()` with `switchSkin()` (now a `suspend` function)
9. Replace `ThemeChangeListener` with `SkinChangeListener`
10. Remove any `Activity.recreate()` workarounds — v2 handles state preservation automatically

---

*End of architecture document.*
