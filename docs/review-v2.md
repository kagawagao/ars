# ARS V2 — Code Review Report

**Reviewed commit:** `c78c27a` (HEAD — includes recent P0 fixes)
**Previous review:** This replaces the stale review at `981f9df`.
**Reviewer:** Code Review Agent
**Date:** 2026-05-17
**Scope:** Full diff (`origin/master..HEAD`) against requirements-v2.md and architecture-v2.md

---

## 1. Summary

ARS V2 is a ground-up rewrite of the Android skinning framework, replacing the broken v1 overlay approach with a genuine **ContextWrapper + LayoutInflater.Factory2** hybrid. The core architecture is sound — `SkinResources` intercepts all resource lookups at the `Resources` level, while `SkinLayoutInflater` records View-to-attribute bindings during XML inflation and `ArsViewTreeWalker` reapplies them on skin switch. The recent fixes (c78c27a) correctly wired up the SkinResources update pipeline, the View-tree walk for all active Activities, and theme-mode switching.

**Overall assessment:** The framework is **on track** but **not production-ready**. All P0 structural components exist and are correctly designed. However, the demo app is non-functional (uses removed v1 API), several P1 features and architecture-specified components are missing, and there are no instrumented tests. The fix in c78c27a resolved the 3 critical TODOs that were the main blockers for the engine pipeline.

---

## 2. Correctness vs Requirements

### P0 — Minimum Viable Overlay Framework

| Requirement | Status | Evidence |
|---|---|---|
| **FR-P0-01** Genuine Resource Overlay | ✅ **PASS** | `SkinResources` overrides `getColor()`, `getDrawable()`, `getDimension()`, `getString()`, `getText()`, `getValue()`, `getColorStateList()`, `getDimensionPixelOffset()`, `getDimensionPixelSize()`, `getResourcePackageName()`. Name-based lookup via `resolveSkinId()` — skin-first, base-fallback. `SkinContextWrapper` injects `SkinResources` via `getResources()`. No `getResourcesForApplication()` as the primary mechanism. |
| **FR-P0-02** Automatic View Skinning | ✅ **PASS** | `SkinLayoutInflater` (Factory2) intercepts View creation, delegates to AppCompat first, scans `AttributeSet`, records `AttrBinding` entries + `SkinViewMeta` in engine registry. Newly inflated Views immediately receive skin values if a skin is active (Phase B behavior at step [6] of inflation flow — though applying to newly-created Views is handled by the BFS walker, not at inflation time; see note below). |
| **FR-P0-03** No Activity Recreation | ✅ **PASS** | `ArsViewTreeWalker` performs BFS queue-based walk of `decorView`. No `recreate()` call. `onSkinChanged` delegates to `onSkinApplied` (subclass hook). Skin switch walks all registered activities via `walkAllActivityTrees()`. |
| **FR-P0-04** Transient State Preservation | ✅ **PASS** | `ArsActivity` overrides `onSaveInstanceState`/`onRestoreInstanceState` with `@CallSuper` + delegates to super. No framework-forced state loss. Scroll position, text input preserved by Android View state mechanism. |
| **FR-P0-05** Correct Attribute Name Resolution | ✅ **PASS** | `SkinAttributeResolver.stripNamespace()` handles `android:`, `app:`, no prefix, and empty prefix cases. `isSupported()` checks against `DEFAULT_SUPPORTED_ATTRIBUTES` + custom set. Comprehensive unit tests validate all cases. |
| **FR-P0-06** RTL-Aware Drawables | ✅ **PASS** | Default attribute set uses `drawableStart`/`drawableEnd`/`drawableTop`/`drawableBottom`. `ATTR_ALIASES` maps deprecated `drawableLeft` → `drawableStart`, `drawableRight` → `drawableEnd`. `ArsViewTreeWalker.applySkinToView` uses `setCompoundDrawablesRelativeWithIntrinsicBounds`. Unit test verifies deprecated names are NOT in default set. |
| **FR-P0-07** Extensible Base Classes | ✅ **PASS** | `ArsApplication` (init engine, ActivityLifecycleCallbacks), `ArsActivity` (attachBaseContext wrap, Factory2 install, listener + activity registration), `ArsFragment` (onAttach wrap, onCreateView inflater setup, listener registration). Extending these is the only integration step required. |
| **FR-P0-08** Skin Package Format | ✅ **PASS** | `SkinPackage` data class with `name`, `packageName`, `targetPackage`, `version`, `resources`, `path`, `themeHint`. `ArsSkinLoader` reads `ars-skin-name`, `ars-skin-version`, `ars-target-package` from manifest metadata. Documented in architecture §DD-08. |
| **FR-P0-09** Skin Loading from Storage | ✅ **PASS** | `ArsSkinLoader.load(skinPath)` validates file existence, readability, APK parse, metadata presence, target match, version compatibility, creates `Resources` via `getResourcesForApplication()`. All errors return `SkinResult.Error`. |
| **FR-P0-10** Working Demo Application | ❌ **FAIL** | **BLOCKER.** `MainActivity.kt` references `ArsSkinManager` (removed in v2!), calls `ArsSkinManager.getInstance()`, uses `ArsSkinManager.ThemeMode` — none of which exist. The "Load Skin" button shows a Toast. No skin packages in assets. No demonstration of actual skin switching. The demo will not compile. |
| **FR-P0-11** Core Test Suite | ⚠️ **PARTIAL** | Unit tests exist for: `SkinAttributeResolver` (12 tests, thorough), `SkinError` (9 tests), `SkinResult` (6 tests). Missing per architecture: `ArsSkinEngineTest`, `SkinResourcesTest`, `ArsViewTreeWalkerTest`. No instrumented tests. No coverage report. Estimated coverage <40% of core logic. |

### P1 — Polish & Production Readiness

| Requirement | Status | Evidence |
|---|---|---|
| **FR-P1-01** Lightweight View-Tree Walk | ⚠️ **PARTIAL** | BFS walk via `LinkedList<View>` queue — non-recursive, avoids stack overflow. ✅ Walks `decorView` of active activities. ❌ No lazy skinning for back-stack Views. All registered activities are walked regardless of visibility. The engine tracks `activeActivities` but does not distinguish foreground vs. background. |
| **FR-P1-02** Skin Change Lifecycle Callbacks | ✅ **PASS** | `SkinChangeListener` interface with `onSkinChanged(previous, current)`. `ArsActivity`/`ArsFragment` implement it, fire `onSkinApplied()` after engine walk. Global listener set on `ArsSkinEngine`. Callbacks fire after View updates (listeners notified after walk). |
| **FR-P1-03** Custom Attribute Handler | ✅ **PASS** | `SkinAttributeHandler` fun interface with `apply(view, resId, resources)`. `ArsSkinEngine.registerAttributeHandler(name, handler)`. `SkinAttributeResolver.registerCustom(name)`. Handler lookup takes priority over built-in dispatch in `applySkinToView()`. |
| **FR-P1-04** Skin Invalidation Control | ⚠️ **PARTIAL** | ✅ Tag-based exclusion: `R.id.ars_skip_skinning` tag checked in `ArsViewTreeWalker.shouldSkip()`. ❌ No `View.setSkinningEnabled(false)` extension function. The architecture specified both mechanisms; only the tag exists. |
| **FR-P1-05** Graceful Degradation | ✅ **PASS** | `SkinResources.resolveSkinId()` returns 0 for missing resources, causing fallback to base. `applySkinToView()` catches exceptions per-binding and continues silently. No crash on missing skin resources. |
| **FR-P1-06** Error Handling & Diagnostics | ⚠️ **PARTIAL** | ✅ `SkinError` sealed class with 8 error types. ✅ `SkinResult` sealed class with `isSuccess`/`isError`/`getOrNull()`/`getOrThrow()`. ✅ `getDiagnostics()` returns `SkinDiagnostics`. ❌ No debug-mode conditional logging for resource resolution traces. ❌ `ensureInitialized()` throws `IllegalStateException` — contradicts "never throw" principle for public API calls before init. |
| **FR-P1-07** Thread Safety | ⚠️ **PARTIAL** | ✅ `switchSkin()` is `suspend` — callable from any thread. ✅ `Mutex` serializes concurrent switches. ✅ `ConcurrentHashMap` for viewRegistry. ✅ `@Volatile` on `activeSkin`, `currentThemeMode`, `initialized`. ✅ I/O on `Dispatchers.IO`. ❌ `skinChangeListeners` is `mutableSetOf` (not thread-safe) — accessed from main thread only but not documented/documented as invariant. ❌ `skinResourcesRefs` and `activeActivities` are `mutableSetOf` — not thread-safe, cleared only on main thread. |
| **FR-P1-08** Memory Management | ❌ **FAIL** | ❌ Previous skin resources are **not released** on switch. `switchSkin(skin: SkinPackage)` stores `oldSkin` but never calls `oldSkin?.dispose()`. `resetToDefault()` has the same issue. Resources accumulate on every switch. ❌ `SkinPackage.dispose()` is a documented no-op — `Resources` objects open native file descriptors on the skin APK; these should be cleaned up. ✅ WeakReferences used for View registry, SkinResources tracking, and activity tracking. ✅ `dispose()` cleans up all engine state. |
| **FR-P1-09** Instrumented Tests | ❌ **FAIL** | No instrumented tests found. The architecture specifies tests verifying View appearance after skin switch, state preservation, LayoutInflater interception, and RTL drawable positioning on API 34 emulator. None implemented. |
| **FR-P1-10** API Documentation | ⚠️ **PARTIAL** | ✅ All public classes and methods have KDoc. ✅ Usage examples in KDoc for ArsSkinEngine, ArsActivity, ArsFragment. ❌ No separate getting-started guide. ❌ No standalone skin package format specification document. |

### P2 — Ecosystem & Futures

All P2 requirements (FR-P2-01 through FR-P2-08) are **not implemented**, as expected for an MVP-targeted release. This is by design and not a concern.

---

## 3. Architecture Compliance

### API Contract Compliance

| Architecture Component | Implementation | Match? |
|---|---|---|
| `ArsSkinEngine.init(Application)` | ✅ Exists, idempotent | ✅ Match |
| `ArsSkinEngine.switchSkin(skinPath: String): SkinResult<Unit>` | ✅ Exists as `suspend` | ✅ Match |
| `ArsSkinEngine.switchSkin(skin: SkinPackage): SkinResult<Unit>` | ✅ Exists as `suspend` | ✅ Match |
| `ArsSkinEngine.resetToDefault(): SkinResult<Unit>` | ✅ Exists as `suspend` | ✅ Match |
| `ArsSkinEngine.setThemeMode(mode: ThemeMode)` | ✅ Exists, non-suspend, updates Configuration + walks trees | ✅ Match |
| `ArsSkinEngine.wrapContext(base: Context): Context` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.createSkinFactory(delegate, context)` | ✅ Exists, returns `SkinLayoutInflater` | ✅ Match |
| `ArsSkinEngine.registerSkinChangeListener` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.unregisterSkinChangeListener` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.registerAttributeHandler` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.unregisterAttributeHandler` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.getDiagnostics(): SkinDiagnostics` | ✅ Exists, but missing fields (see below) | ⚠️ Partial |
| `ArsSkinEngine.dispose()` | ✅ Exists, but calls `switchLock.withLock` (suspend) from non-suspend | ❌ Bug |
| `ArsSkinEngine.currentThemeMode: ThemeMode` | ✅ Exists | ✅ Match |
| `ArsSkinEngine.activeSkin: SkinPackage?` | ✅ Exists (`var` with private set, not `val`) | ✅ Match |
| `ArsActivity` convenience methods (`switchSkin`, `resetSkin`, `setSkinThemeMode`) | ❌ **Missing** — architecture §6.6 specifies these as `protected` methods on `ArsActivity` | ❌ Missing |
| `ArsFragment.refreshSkin()` | ✅ Exists | ✅ Match |
| `ArsActivity.refreshSkin()` | ✅ Exists | ✅ Match |
| `SkinPackage.ThemeMode` | ✅ Exists (nested enum) | ✅ Match |
| `SkinPackage.dispose()` | ✅ Exists (no-op) | ✅ Match |
| `SkinError` hierarchy | ✅ All 8 variants exist | ✅ Match |
| `SkinResult<out T>` | ✅ Exists with `Success`, `Error`, helpers | ✅ Match |
| `SkinChangeListener` | ✅ Exists | ✅ Match |
| `SkinAttributeHandler` | ✅ Fun interface with 3 params | ✅ Match |
| `SkinDiagnostics` | ⚠️ Missing fields: `registeredAttributeCount`, `cachedIdMappings`, `lastSwitchDurationMs`, `lastError`. Has `activeSkinVersion` (not in arch spec) | ⚠️ Divergent |
| `ArsSkinLoader.loadFromFile(skinPath)` | ✅ Implemented as `load(skinPath)` | ⚠️ Minor (name) |
| `ArsSkinLoader.loadFromAssets(assetPath)` | ❌ **Missing** | ❌ Missing |
| `ArsSkinLoader.validateStructure(skinPath)` | ❌ **Missing** | ❌ Missing |
| `ArsSkinLoader.readMetadata(skinPath)` | ❌ **Missing** | ❌ Missing |
| `ArsSkinPackageManager` | ❌ **Missing entirely** | ❌ Missing |
| `SkinContextWrapper` | ✅ Exists, overrides `getResources()` | ✅ Match |
| `SkinResources` | ✅ Overrides all specified resource accessors | ✅ Match |
| `SkinResources.updateSkin()` | ✅ Exists | ✅ Match |
| `SkinLayoutInflater` | ✅ Exists, delegates to AppCompat, records metadata | ✅ Match |
| `SkinViewMeta` / `AttrBinding` / `ResourceType` | ✅ All exist | ✅ Match |
| `SkinAttributeResolver` | ✅ All specified methods exist + `resolveAlias()` (bonus) | ✅ Match |
| `ArsViewTreeWalker` | ✅ BFS with queue, skip-tag support | ✅ Match |

### Key Design Decision Compliance

| Decision | Compliance | Notes |
|---|---|---|
| **DD-01** ContextWrapper over AssetManager | ✅ | `SkinContextWrapper` + `SkinResources` — no reflection, public API only. |
| **DD-02** Factory2 retained alongside ContextWrapper | ✅ | `SkinLayoutInflater` captures View-to-resource bindings for re-application. |
| **DD-03** Iterative BFS | ✅ | `LinkedList<View>` queue in `ArsViewTreeWalker`. |
| **DD-04** WeakReference View Registry | ✅ | `SkinViewMeta.viewRef` is `WeakReference<View>`. |
| **DD-05** Resource-Name-Based Lookup | ✅ | `resolveSkinId()` uses `getResourceEntryName()` + `getResourceTypeName()` + `getIdentifier()`. |
| **DD-06** Coroutines for Async I/O | ✅ | `ArsSkinLoader.load()` uses `withContext(Dispatchers.IO)`. |
| **DD-07** Sealed Class Error Hierarchy | ✅ | `SkinError` + `SkinResult` fully implemented. |
| **DD-08** Skin Package Format | ✅ | Manifest metadata keys match: `ars-skin-name`, `ars-skin-version`, `ars-target-package`. |
| **DD-09** Graceful Degradation | ✅ | Missing resources silently fall back to default. |
| **DD-10** Skin ID Cache (LruCache) | ❌ **Missing** | Architecture specifies an `LruCache<Pair<Int, WeakReference<Resources>>, Int>` for `(hostResId → skinResId)` mappings. `resolveSkinId()` calls `getIdentifier()` on every lookup — no caching. Performance impact on large View trees with repeated resource lookups. |
| **Callback Order** | ✅ | Fixed in c78c27a: SkinResources updated → View trees walked → Listeners notified → onSkinApplied(). |

### File Structure vs Architecture

The architecture (§10) specifies subdirectories: `skin/`, `inflate/`, `loader/`, `attr/`, `callback/`, `error/`. The implementation places all internal classes in a flat `internal/` package. This is a **reasonable simplification** (avoids excessive package nesting for a small library), but the architecture document should be updated or a decision recorded.

---

## 4. Security

| Concern | Status | Detail |
|---|---|---|
| **NFR-04: No DEX loading from skins** | ✅ | `ArsSkinLoader` loads only resources via `getResourcesForApplication()`. No DEX loading. |
| **NFR-04: Package signature verification** | ⚠️ | `getPackageArchiveInfo()` parses the APK but does not verify signatures. The architecture references "standard Android package signature verification" — this requires `PackageManager.GET_SIGNATURES` flag (not used) or explicit `PackageManager.checkSignatures()`. Skin APK signatures are not verified. |
| **NFR-04: No reflection on private APIs** | ✅ | No reflection on `@hide` APIs. Uses `getResourcesForApplication()` (public, deprecated but functional). |
| **Skin package path validation** | ✅ | `ArsSkinLoader` validates file existence, readability, and APK structure before creating Resources. |
| **Listener exception isolation** | ✅ | `notifySkinChangeListeners()` wraps each listener call in try/catch. |

---

## 5. Maintainability

| Concern | Status | Detail |
|---|---|---|
| **Public API surface** | ✅ | Clean separation: public classes in `com.kagawagao.ars`, internal classes in `com.kagawagao.ars.internal`. |
| **`internal` visibility** | ✅ | All non-public components are `internal`. |
| **KDoc coverage** | ✅ | All public methods documented with usage examples. |
| **Code organization** | ✅ | Single-responsibility: engine, loader, walker, resolver, inflater each in dedicated files. |
| **Testability** | ⚠️ | `ArsSkinEngine` is an `object` singleton — hard to mock/test in isolation. Architecture shows `ArsSkinEngineTest` with specific test methods, but none exist. `SkinAttributeResolver` is also an `object` but is stateless enough for unit testing (existing tests prove this). |
| **Hardcoded constants** | ⚠️ | ARS metadata keys (`ars-skin-name`, etc.) are private constants in `ArsSkinLoader`. OK but could be extracted to a shared constants file for documentation. |
| **Deprecated API usage** | ⚠️ | `getDrawable(int, Theme)` deprecated in API 34; `getResourcesForApplication(ApplicationInfo)` deprecated. Both annotated with `@Suppress("DEPRECATION")`. Acceptable for API 34 target but will need migration before API 36 where these are removed. |
| **`factory2` property** | ⚠️ | `LayoutInflater.factory2` is deprecated since API 29 in favor of explicit `setFactory2()`. The code reads `inflater.factory2` and sets `inflater.factory2 = skinFactory` — works but triggers deprecation warnings. |
| **`ensureInitialized()` throws** | ⚠️ | Uses `check()` which throws `IllegalStateException` — violates the "never throw" principle. However, this only fires on programmer error (calling before init), which is arguably in the "programming errors throw" bucket per the architecture. Borderline. |

---

## 6. Issues

### 🔴 Blocker

| ID | Issue | Requirement | Location |
|---|---|---|---|
| **B-01** | **Demo app uses removed v1 API.** `MainActivity.kt` references `ArsSkinManager`, `ArsSkinManager.getInstance()`, `ArsSkinManager.ThemeMode` — all removed. The demo will not compile. | FR-P0-10 | `app/.../MainActivity.kt` |
| **B-02** | **`ArsSkinEngine.dispose()` calls `switchLock.withLock` — a `suspend` function — from a non-suspend context.** This will fail to compile or crash at runtime. `Mutex.withLock` is a `suspend` function. | Architecture §6.1 (dispose is non-suspend) | `ArsSkinEngine.kt:432` |
| **B-03** | **Previous skin Resources never released.** `switchSkin()` and `resetToDefault()` store `oldSkin` but never call `oldSkin?.dispose()` or release the Resources. Over multiple switches, `Resources` objects (which hold native file descriptors) accumulate until process death. | FR-P1-08 (Memory) | `ArsSkinEngine.kt:178-196`, `207-226` |
| **B-04** | **`ArsActivity` missing convenience methods.** Architecture §6.6 specifies `protected suspend fun switchSkin()`, `protected suspend fun resetSkin()`, `protected fun setSkinThemeMode()` on `ArsActivity`. None are implemented. Host app developers must call `ArsSkinEngine` directly (coupling). | FR-P0-07 (extensible base classes) | `ArsActivity.kt` |

### 🟡 Important

| ID | Issue | Requirement | Location |
|---|---|---|---|
| **I-01** | **No instrumented tests.** Zero Android instrumented tests exist. The architecture specifies `ArsSkinEngineTest`, `SkinResourcesTest`, `ArsViewTreeWalkerTest`. Test coverage on core logic is far below 80%. | FR-P0-11, FR-P1-09 | `ars-core/src/test/` (only unit tests present) |
| **I-02** | **`ArsSkinLoader` missing methods.** Architecture §6.9 specifies `loadFromAssets()`, `validateStructure()`, `readMetadata()`. Only `load()` (equivalent to `loadFromFile`) is implemented. | FR-P0-09 | `ArsSkinLoader.kt` |
| **I-03** | **`ArsSkinPackageManager` missing entirely.** Architecture §6.10 specifies CRUD for skin files on disk: `listInstalledSkins()`, `deleteSkin()`, `clearAllSkins()`, `getSkinDirectory()`. Not implemented. | Architecture §6.10 | N/A (not created) |
| **I-04** | **No resource ID caching (DD-10).** Architecture specifies an `LruCache` for `(hostResId → skinResId)` mappings to avoid repeated `getIdentifier()` calls. `resolveSkinId()` calls `getIdentifier()` on every single resource lookup — significant performance cost for View-tree walks with many Views. | DD-10 | `SkinResources.kt:189-200` |
| **I-05** | **`SkinDiagnostics` missing fields.** Architecture specifies `registeredAttributeCount`, `cachedIdMappings`, `lastSwitchDurationMs`, `lastError`. Implementation has `activeSkinVersion` instead. | Architecture §6.15 | `SkinDiagnostics.kt`, `ArsSkinEngine.kt:384-396` |
| **I-06** | **No debug-mode resource resolution logging.** Architecture §DD-09 and FR-P1-06 require warning logs for missing resources in debug builds. Neither `SkinResources.resolveSkinId()` nor `applySkinToView()` logs resource misses. | FR-P1-06, DD-09 | `SkinResources.kt`, `ArsSkinEngine.kt:555-615` |
| **I-07** | **`View.setSkinningEnabled(false)` not implemented.** Architecture §6.1 and FR-P1-04 specify an extension function. Only the `ars_skip_skinning` tag exists as a per-View opt-out mechanism. | FR-P1-04 | N/A (not created) |
| **I-08** | **No lazy skinning for back-stack Activities.** `walkAllActivityTrees()` walks all registered activities regardless of visibility. Architecture §FR-P1-01 specifies "Views in the back stack (non-visible Activities/Fragments) are skinned lazily when they become visible." | FR-P1-01 | `ArsSkinEngine.kt:473-485` |
| **I-09** | **`skinChangeListeners` mutableSetOf not thread-safe.** While listeners are currently only registered/unregistered from the main thread, this is an undocumented invariant. If a background thread registers a listener, it could cause `ConcurrentModificationException` during notification. Use `ConcurrentHashMap.newKeySet()` or document the main-thread-only invariant. | FR-P1-07 | `ArsSkinEngine.kt:86` |
| **I-10** | **`SkinError.SwitchInProgress` never returned.** The `Mutex.withLock` blocks until the previous switch completes instead of returning `SwitchInProgress`. The architecture threading model (§8) says "second call returns `SkinError.SwitchInProgress` or queues (configurable)." Currently blocks. | FR-P1-07, Architecture §8 | `ArsSkinEngine.kt:57, 178` |

### 🔵 Minor

| ID | Issue | Requirement | Location |
|---|---|---|---|
| **M-01** | **No getting-started guide.** Architecture §FR-P1-10 specifies "A getting-started guide explains integration in ≤5 minutes." The KDoc is thorough but there's no standalone guide. | FR-P1-10 | N/A |
| **M-02** | **No skin package format specification document.** Architecture §FR-P0-08 and §DD-08 describe the format but only within the architecture doc. A separate, skin-author-facing format spec is not provided. | FR-P0-08, FR-P1-10 | N/A |
| **M-03** | **`ArsSkinEngine.switchSkin(skinPath)` does not dispatch to IO explicitly.** Relies on `loader.load()` internally using `withContext(Dispatchers.IO)`. The architecture shows explicit IO dispatch in the engine's `switchSkin()`. Minor — works correctly, but implicit. | Architecture §5.1 | `ArsSkinEngine.kt:152-164` |
| **M-04** | **Newly inflated Views don't immediately get skin applied.** `SkinLayoutInflater.recordViewMeta()` records metadata but does not apply the current skin to the newly created View. The architecture inflation flow (§5.2 step [6]) says "If activeSkin != null, immediately apply skin resources to this View." Currently, dynamically added Views only get skinned on the next explicit `switchSkin()` or `refreshSkin()` call. | Architecture §5.2 | `SkinLayoutInflater.kt:86-111` |
| **M-05** | **`ThemeMode` nested in `SkinPackage`.** Architecture §6.2 shows `ThemeMode` as a nested enum inside `SkinPackage`, which the implementation matches. However, many components need to reference `ThemeMode` independently (e.g., `setThemeMode()`) — this creates verbose references like `SkinPackage.ThemeMode.DARK`. A top-level `ThemeMode` might be cleaner. | Style | `SkinPackage.kt:35-41` |
| **M-06** | **`ArsApplication.ActivityTracker` unused.** The `ActivityTracker` in `ArsApplication` tracks `activeActivity` but the field is private to the inner class and never read. The actual activity registration happens via `ArsActivity` calling `registerActiveActivity()`. The `ActivityTracker` appears to be leftover code. | Code hygiene | `ArsApplication.kt:56-76` |
| **M-07** | **`ArsFragment.onCreateView()` returns `null`.** Subclasses that override `onCreateView` and call `super.onCreateView()` will get `null` back. The KDoc says "Override this and call `super.onCreateView()` — OR override and return your own View, then call `wrapInflater` manually." This is a fragile contract. A better approach would be to use `onViewCreated` + automatically wrap the parent LayoutInflater or provide a `wrapInflater` helper that returns the wrapped inflater. | Design | `ArsFragment.kt:64-74` |

---

## 7. Overall Verdict

### Verdict: **NOT READY — BLOCKERS MUST BE RESOLVED**

The ARS V2 core engine is **architecturally sound** and the recent fixes (c78c27a) correctly resolved the 3 critical integration TODOs. The ContextWrapper + Factory2 hybrid approach correctly implements the genuine overlay promise from the requirements. Resource interception, attribute resolution, View-tree walking, error handling, and base class design are all well-implemented.

However, **4 blockers and 10 important issues** prevent this from being shippable:

1. **The demo app does not compile** (uses removed v1 API). This is a critical demo-gating issue for FR-P0-10.
2. **`dispose()` has a compile/runtime bug** with suspend function usage.
3. **Memory leak** from never releasing previous skin Resources.
4. **Missing convenience methods** on `ArsActivity` bridge the developer from Activity to engine.

The P1 feature set is only ~40% complete. Most concerningly, there are **zero instrumented tests** and the test suite covers only ~27% of the architecture-specified test files (3 of 11 planned test classes). The missing `ArsSkinPackageManager`, `LruCache` for resource IDs, and debug logging are significant gaps between the architecture and implementation.

### Recommended Priority Order

1. **Fix B-01** — Rewrite demo app to use v2 API (ArsSkinEngine, SkinPackage, etc.). Add 2+ skin APKs to assets. Demonstrate real skin switching.
2. **Fix B-02** — Make `dispose()` use `runBlocking { switchLock.withLock { ... } }` or restructure to avoid the suspend call.
3. **Fix B-03** — Call `oldSkin?.dispose()` in `switchSkin()` and `resetToDefault()`. Consider closing the underlying `AssetManager` or at minimum nulling the reference.
4. **Fix B-04** — Add `switchSkin()`, `resetSkin()`, `setSkinThemeMode()` convenience methods to `ArsActivity`.
5. **Implement I-01** — Write instrumented tests for SkinResources resource resolution, ArsViewTreeWalker BFS, and LayoutInflater interception.
6. **Implement I-02, I-03** — Add missing ArsSkinLoader methods and ArsSkinPackageManager.
7. **Implement I-04** — Add resource ID cache per DD-10.
8. **Address I-05 through I-10** — Diagnostics fields, debug logging, lazy back-stack, thread safety documentation, SwitchInProgress behavior.
9. **Address M-01 through M-07** — Guides, immediate-skin-on-inflate, cleanup.

### What's Working Well

- **Attribute resolution is bulletproof** — namespace stripping, alias mapping, RTL compliance, all unit tested.
- **Error handling type system** is comprehensive — sealed classes, contextual error messages, clean result wrapping.
- **Thread safety architecture** is correct — Mutex serialization, ConcurrentHashMap, @Volatile, Dispatchers.IO for I/O.
- **SkinResources resource interception** is the right design — name-based lookup avoids ID conflicts between APKs.
- **BFS View-tree walk** is the right approach for deep hierarchies.
- **WeakReference usage** throughout prevents memory leaks in the View registry and SkinResources tracking.
- **KDoc quality** is excellent — every public method has clear docs, usage examples, and requirement references.
