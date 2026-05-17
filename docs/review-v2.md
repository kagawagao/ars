# ARS v2 — Code Review: Implementation vs. Requirements & Architecture

**Reviewer:** Hermes Agent (Code Reviewer)
**Date:** 2026-05-17
**Commit:** `981f9df` — `feat(ars-v2): complete rebuild — genuine ResourceOverlay skinning framework`
**Status:** Action Required (issues found)

---

## Executive Summary

ARS v2 is a substantial improvement over v1, with a well-structured architecture that correctly implements the **ContextWrapper + LayoutInflater.Factory2 hybrid** approach. The core design decisions (name-based lookup, ContextWrapper over reflection, iterative BFS walker, WeakReference registry) are all properly realized in code.

However, the implementation is **incomplete** — several P0 requirements are only partially implemented (`TODO Phase B` markers), there are thread-safety gaps, the API surface has drifted from the architecture contract, and one resource-loading pattern (use of `getResourcesForApplication`) contradicts the stated architecture goals.

**Overall verdict:** Architecture-compliant but not yet production-ready. **10 issues found (3 HIGH, 5 MEDIUM, 2 LOW).**

---

## 1. Requirements Compliance Matrix

### P0 Requirements

| Req | Description | Status | Evidence |
|-----|-------------|--------|----------|
| **FR-P0-01** | Genuine Resource Overlay Loading | ⚠️ PARTIAL | `SkinResources` correctly intercepts `getColor()`/`getDrawable()`/etc. via name-based lookup. **BUT** the skin's `Resources` is created via `PackageManager.getResourcesForApplication()` (the very v1 pattern flagged as "defeats the purpose"), rather than a genuinely merged AssetManager. See Issue #1. |
| **FR-P0-02** | Automatic View Skinning via LayoutInflater | ✅ PASS | `SkinLayoutInflater` (Factory2) intercepts View creation, records `SkinViewMeta`, delegates to AppCompat first. Correctly installed in `ArsActivity.onCreate()`. |
| **FR-P0-03** | No Activity Recreation | ✅ PASS | No `recreate()` call anywhere in the codebase. `onSkinChanged()` walks the View tree in-place via `ArsViewTreeWalker`. |
| **FR-P0-04** | Transient State Preservation | ⚠️ PARTIAL | `onSaveInstanceState`/`onRestoreInstanceState` are overridden with `@CallSuper` but are **empty pass-throughs** — no skin-specific state is saved. See Issue #2. |
| **FR-P0-05** | Correct Attribute Name Resolution | ✅ PASS | `SkinAttributeResolver.stripNamespace()` correctly handles `"android:background"`, `"app:cornerRadius"`, and unprefixed names. Comprehensive test coverage (15 tests). |
| **FR-P0-06** | RTL-Aware Drawable Attributes | ✅ PASS | `drawableLeft`/`drawableRight` aliased to `drawableStart`/`drawableEnd`. `applySkinToView()` uses `setCompoundDrawablesRelativeWithIntrinsicBounds()`. Tests verify alias mapping. |
| **FR-P0-07** | Extensible Base Classes | ✅ PASS | `ArsApplication`, `ArsActivity`, `ArsFragment` all present and functional. |
| **FR-P0-08** | Skin Package Format | ✅ PASS | Defined via ARS metadata in AndroidManifest.xml (`ars-skin-name`, `ars-skin-version`, `ars-target-package`). `ArsSkinLoader` validates all three. |
| **FR-P0-09** | Skin Loading from Storage | ✅ PASS | `ArsSkinLoader.load(skinPath)` validates existence, readability, parses via PackageManager, verifies metadata, checks target/version. |
| **FR-P0-10** | Working Demo Application | ❌ NOT VERIFIED | Demo app not reviewed in this pass. |
| **FR-P0-11** | Core Test Suite | ⚠️ PARTIAL | Three test files with good coverage on `SkinAttributeResolver`, `SkinError`, `SkinResult`. **Missing:** no tests for `ArsSkinEngine`, `SkinResources`, `ArsSkinLoader`, `ArsViewTreeWalker`, or `SkinLayoutInflater`. Coverage likely well below 80%. See Issue #3. |

---

## 2. Architecture Compliance

### Design Decision Verification

| DD | Description | Status | Notes |
|----|-------------|--------|-------|
| **DD-01** | ContextWrapper over AssetManager reflection | ⚠️ PARTIAL | `SkinContextWrapper` + `getResources()` override is correct. **BUT** `ArsSkinLoader.createResourcesForPackage()` uses `PackageManager.getResourcesForApplication()` (v1's deprecated approach) to load skin Resources. The architecture doc explicitly calls this out as a con that "creates isolated Resources — Views can't see it; defeats the purpose." While the skin Resources is only used internally by `SkinResources` as a lookup source (not exposed directly to Views), this is a deviation from the "no `getResourcesForApplication`" design intent. See Issue #4. |
| **DD-02** | Factory2 chaining with AppCompat | ✅ PASS | `SkinLayoutInflater` delegates to AppCompat's Factory2 first, then scans attributes on the created View. `ArsActivity` correctly captures the original `factory2` after `super.onCreate()` and chains. |
| **DD-03** | Iterative BFS for View-tree walk | ✅ PASS | `ArsViewTreeWalker.walk()` uses `LinkedList<View>` queue, BFS loop, skips tagged Views, enqueues children. |
| **DD-04** | WeakReference-based View registry | ✅ PASS | `SkinViewMeta.viewRef` is `WeakReference<View>`. Used correctly in `getDiagnostics()` to count alive views. |
| **DD-05** | Name-based lookup (not ID-based) | ✅ PASS | `SkinResources.resolveSkinId()` uses `getResourceEntryName` + `getResourceTypeName` + `getIdentifier` — strictly name-based. No ID assumptions between APKs. |
| **DD-06** | Coroutines for async I/O | ✅ PASS | `ArsSkinLoader.load()` uses `withContext(Dispatchers.IO)`. `ArsSkinEngine.switchSkin()` is `suspend` and uses `Mutex` + `withContext(Dispatchers.Main)` for View updates. `kotlinx-coroutines-android:1.7.3` in build.gradle.kts. |
| **DD-07** | Sealed class error hierarchy | ✅ PASS | `SkinError` sealed class with 8 variants, `SkinResult<T>` sealed class with `Success`/`Error`. All operations return `SkinResult`. |
| **DD-08** | Skin package format | ✅ PASS | Three metadata keys verified by loader: `ars-skin-name`, `ars-skin-version`, `ars-target-package`. |
| **DD-09** | Graceful degradation on missing resources | ✅ PASS | `resolveSkinId()` returns 0 on `NotFoundException` → falls through to base. `applySkinToView()` catches exceptions silently. |
| **DD-10** | Skin ID cache (LruCache) | ❌ MISSING | Architecture specifies an `LruCache<Pair<Int, WeakReference<Resources>>, Int>` for hostResId→skinResId mappings. **Not implemented.** Every resource lookup re-executes `getResourceEntryName` + `getResourceTypeName` + `getIdentifier`. See Issue #5. |

### API Contract Drift

The implementation diverges from the architecture's API contracts in several places:

1. **`ArsSkinEngine.createSkinFactory()`** — architecture specifies `createSkinFactory(delegate: LayoutInflater.Factory2, context: Context)` — **MATCH** (present at line 272).

2. **`ArsActivity` convenience methods** — architecture specifies `protected suspend fun switchSkin(path)`, `protected suspend fun resetSkin()`, `protected fun setSkinThemeMode(mode)`. **NONE are implemented** in `ArsActivity`. Only `refreshSkin()` exists. See Issue #6.

3. **`ArsActivity.onSkinChanged()` naming** — architecture specifies `open fun onSkinChanged(previous, current)` as the overridable method. Implementation uses `open fun onSkinApplied(previous, current)` and makes `onSkinChanged()` `final`. This API renaming is undocumented. See Issue #7.

4. **`ArsFragment.wrapInflater()`** — architecture doesn't mention this method, but it exists and the Fragment's `onCreateView` returns `null` and relies on subclasses calling `wrapInflater()`. This is a usability concern — subclasses MUST remember to call `wrapInflater()` or skinning won't work. See Issue #8.

---

## 3. Detailed Issue Report

### Issue #1 — HIGH: `getResourcesForApplication()` persists from v1
- **File:** `ars-core/src/main/java/com/kagawagao/ars/internal/ArsSkinLoader.kt:170-181`
- **Severity:** HIGH
- **Category:** Architecture Compliance
- **Description:** The skin's `Resources` instance is created via `PackageManager.getResourcesForApplication()`, which was flagged in the architecture doc (DD-01 trade-off table) as a v1 pattern that "creates isolated Resources — Views can't see it." The architecture explicitly states this should NOT be the primary skin-loading mechanism.
- **Impact:** While `SkinResources` wraps this and uses it only as a lookup source (not exposing it directly to Views), the architecture intended an AssetManager-level merge approach. The current approach is functionally equivalent to v1's `getResourcesForApplication()` with a `SkinResources` wrapper on top.
- **Recommendation:** Either (a) update DD-01 to clarify that `getResourcesForApplication()` is acceptable as the skin-Resources source when wrapped by `SkinResources`, or (b) implement the AssetManager-based approach (which may require `addAssetPath` reflection or a custom AssetManager).

### Issue #2 — MEDIUM: Empty `onSaveInstanceState`/`onRestoreInstanceState` overrides
- **File:** `ars-core/src/main/java/com/kagawagao/ars/ArsActivity.kt:99-113`
- **Severity:** MEDIUM
- **Category:** Completeness (FR-P0-04)
- **Description:** Both overrides are annotated `@CallSuper` but contain only `super` calls with no framework-specific state preservation. The KDoc claims these "ensure proper state preservation" but they add zero functionality beyond what `AppCompatActivity` already provides.
- **Impact:** FR-P0-04 acceptance criteria states "The framework does not require the host app developer to manually save/restore skin-related state." Currently, no skin-related state is saved by the framework — the active skin name is NOT saved/restored, so after process death, the app reverts to the default skin.
- **Recommendation:** Save the active skin identifier in `onSaveInstanceState` and re-apply it in `onRestoreInstanceState`.

### Issue #3 — HIGH: Missing test coverage for core components
- **Files:** `ars-core/src/test/java/com/kagawagao/ars/`
- **Severity:** HIGH
- **Category:** Completeness (FR-P0-11)
- **Description:** Only 3 test files exist (total ~7KB). Missing tests for:
  - `ArsSkinEngine` (switchSkin, resetToDefault, listener management, View registry)
  - `SkinResources` (all override methods, fallback behavior, edge cases)
  - `ArsSkinLoader` (validation flows, error conditions)
  - `SkinLayoutInflater` (attribute scanning, AppCompat delegation)
  - `ArsViewTreeWalker` (BFS correctness, skip logic, deep hierarchies)
  - `SkinContextWrapper` (getResources delegation)
- **Architecture lists** 6 test files expected; only 3 exist. Coverage is likely <30%.
- **Recommendation:** Implement the missing test files as specified in architecture section 10.

### Issue #4 — HIGH: `ArsSkinEngine.switchSkin()` is a `suspend` function that blocks callers without coroutine context
- **File:** `ars-core/src/main/java/com/kagawagao/ars/ArsSkinEngine.kt:128-181`
- **Severity:** HIGH
- **Category:** Thread Safety (FR-P1-07)
- **Description:** `switchSkin(skinPath)` calls `loader.load(skinPath)` directly (line 133), which internally uses `withContext(Dispatchers.IO)`. But `switchSkin(skinPackage)` at line 151 uses `switchLock.withLock` + `withContext(Dispatchers.Main)`. The `withLock` is a `suspend` function. If `switchSkin` is called from the main thread without a coroutine scope, it won't compile — but if called from `runBlocking`, it blocks the main thread.
- **Additionally:** The `skinChangeListeners` set (line 84) is a plain `mutableSetOf<>()` with no synchronization. `registerSkinChangeListener` (line 290) and `unregisterSkinChangeListener` (line 299) add/remove without the mutex. If a listener is registered/unregistered during a notification (which iterates via `.toList()` inside the mutex), a `ConcurrentModificationException` is possible since the `.toList()` creates a snapshot, but the registration itself isn't synchronized with the snapshot time.
- **Recommendation:** (a) Move listener registration/unregistration inside `switchLock` to guarantee consistency, or (b) use `ConcurrentHashMap.newKeySet()` or `CopyOnWriteArraySet`.

### Issue #5 — MEDIUM: Missing resource ID cache (DD-10)
- **File:** `ars-core/src/main/java/com/kagawagao/ars/internal/SkinResources.kt:188-199`
- **Severity:** MEDIUM
- **Category:** Performance (DD-10)
- **Description:** `resolveSkinId()` performs `getResourceEntryName()` + `getResourceTypeName()` + `getIdentifier()` on every single resource lookup. Architecture DD-10 specifies an `LruCache` with `WeakReference<Resources>` key for caching. During a View-tree walk with 100+ Views, each having 3-5 attributes, this means 300-500 uncached lookups per skin switch.
- **Impact:** Could push skin switch latency beyond the 500ms target (NFR-01) for large hierarchies.
- **Recommendation:** Implement the `LruCache<Pair<Int, WeakReference<Resources>>, Int>` as specified in DD-10. Clear on skin switch.

### Issue #6 — MEDIUM: Missing ArsActivity convenience methods
- **File:** `ars-core/src/main/java/com/kagawagao/ars/ArsActivity.kt`
- **Severity:** MEDIUM
- **Category:** API Completeness
- **Description:** Architecture API contract (section 6.6) specifies:
  - `protected suspend fun switchSkin(skinPath: String): SkinResult<Unit>` — **MISSING**
  - `protected suspend fun resetSkin(): SkinResult<Unit>` — **MISSING**
  - `protected fun setSkinThemeMode(mode: ThemeMode)` — **MISSING**
- Only `fun refreshSkin()` exists.
- **Impact:** Host app developers must call `ArsSkinEngine.switchSkin()` directly instead of using Activity-scoped convenience methods.
- **Recommendation:** Add the three missing convenience methods that delegate to `ArsSkinEngine`.

### Issue #7 — LOW: API naming mismatch — `onSkinApplied` vs `onSkinChanged`
- **File:** `ars-core/src/main/java/com/kagawagao/ars/ArsActivity.kt:140-141`
- **Severity:** LOW
- **Category:** API Contract
- **Description:** Architecture specifies `open fun onSkinChanged(previous, current)` as the overridable callback. Implementation makes `onSkinChanged()` `final` and introduces `open fun onSkinApplied(previous, current)` as the overridable hook. This works correctly but deviates from the documented API.
- **Same pattern in `ArsFragment.kt:101`**.
- **Recommendation:** Either update the architecture docs to match, or rename to match the architecture.

### Issue #8 — MEDIUM: `ArsFragment.onCreateView()` returns `null`, delegates inflation to subclass
- **File:** `ars-core/src/main/java/com/kagawagao/ars/ArsFragment.kt:64-74`
- **Severity:** MEDIUM
- **Category:** Usability
- **Description:** `onCreateView()` clones the inflater, wraps it, then **returns `null`**. The subclass MUST override `onCreateView`, call `wrapInflater()` on their own inflater, and return a View. If a developer calls `super.onCreateView()` then inflates with the *original* (unwrapped) inflater returned by the super call, or uses the fragment's original inflater from `onCreateView` parameters, the skinning won't work for that fragment.
- **Impact:** Silent failure — Views inflate but aren't skin-aware. The `@CallSuper` annotation with a `null` return is confusing.
- **Recommendation:** Follow the same pattern as `ArsActivity` — wrap the inflater automatically and provide the wrapped inflater. Consider overriding `onGetLayoutInflater()` to return a wrapped inflater, or make `onCreateView` wrap the inflater so subclasses don't have to.

### Issue #9 — LOW: `ArsViewTreeWalker.shouldSkip()` swallows all exceptions
- **File:** `ars-core/src/main/java/com/kagawagao/ars/internal/ArsViewTreeWalker.kt:81-88`
- **Severity:** LOW
- **Category:** Error Handling
- **Description:** `shouldSkip()` catches `Exception` and returns `false`. While defensively correct, a more precise catch (e.g., `Resources.NotFoundException` or checking if `R.id.ars_skip_skinning` exists) would be better.
- **Note:** The KDoc says "If the ID resource isn't available (e.g., in tests), don't skip" — this is a reasonable justification for the broad catch.

### Issue #10 — LOW: `ArsSkinPackageManager` not implemented
- **File:** Not found
- **Severity:** LOW (P1 feature per architecture)
- **Description:** Architecture sections 6.10 and 10 list `ArsSkinPackageManager` with `listInstalledSkins()`, `deleteSkin()`, `clearAllSkins()`, `getSkinDirectory()`. This component is not implemented.
- **Impact:** No programmatic way to list/delete installed skins. The demo app would need this for a complete UX.
- **Recommendation:** Implement for P1 milestone.

---

## 4. Strengths

1. **Clean separation of concerns:** Engine, Resources, Inflation, and Loading layers are well-separated. Each component has a single responsibility.
2. **Correct namespace stripping:** `SkinAttributeResolver.stripNamespace()` with comprehensive edge-case tests (empty prefix, multiple colons).
3. **RTL compliance:** `drawableLeft`→`drawableStart` aliasing with `setCompoundDrawablesRelativeWithIntrinsicBounds()` is fully correct.
4. **Error handling architecture:** `SkinResult` + `SkinError` sealed hierarchy is well-designed. All loader errors carry contextual information.
5. **KDoc coverage:** Every public class, method, and property has KDoc comments with usage examples. Internal classes are also documented.
6. **No reflection on hidden APIs:** Only standard `Class.forName()` + `getConstructor()` used for LayoutInflater fallback — Google Play policy compliant.
7. **`MutableSet` → `.toList()` snapshot pattern** for safe iteration during listener notification (inside mutex).

---

## 5. v1 File Cleanup Verification

| v1 File | Status |
|---------|--------|
| `ArsSkinManager.kt` | ✅ DELETED |
| `SkinLoader.kt` | ✅ DELETED |
| `ResourceOverlayHelper.kt` | ✅ DELETED |
| `SkinAttribute.kt` | ✅ DELETED |
| `ArsSkinManagerTest.kt` | ✅ DELETED |

**All v1 files confirmed deleted.** No stale references remain.

---

## 6. Security Review

| Concern | Status | Notes |
|---------|--------|-------|
| Hidden API reflection | ✅ PASS | No `@hide` API access. `Class.forName()` only for standard View inflation. |
| DEX loading from skins | ✅ PASS | `ArsSkinLoader` only validates metadata and creates Resources — no class loading from skin APKs. |
| Package signature verification | ⚠️ N/A | `PackageManager.getPackageArchiveInfo()` is called but no signature verification logic is present. Architecture mentions "standard Android package signature verification" but this is not implemented. |
| `sourceDir`/`publicSourceDir` manipulation | ⚠️ INFO | `ArsSkinLoader.createResourcesForPackage()` sets `appInfo.sourceDir = skinPath` (line 177-178) — this is a standard pattern for `getResourcesForApplication()` but modifies `ApplicationInfo` fields. Not a security concern in itself. |

---

## 7. Thread Safety & Memory Summary

| Concern | Status | Notes |
|---------|--------|-------|
| `switchLock` (Mutex) | ✅ GOOD | Serializes skin switches correctly. |
| `ConcurrentHashMap` for `viewRegistry` | ✅ GOOD | Thread-safe reads during concurrent inflation + switch. |
| `@Volatile` on `activeSkin`, `currentThemeMode`, `initialized` | ✅ GOOD | Visibility guarantees for cross-thread state. |
| `skinChangeListeners` (plain `MutableSet`) | ⚠️ CONCERN | Registration/unregistration NOT synchronized with notification loop. See Issue #4. |
| `attributeHandlers` (ConcurrentHashMap) | ✅ GOOD | Thread-safe. |
| WeakReference for Views | ✅ GOOD | Prevents View/Activity leaks. `getDiagnostics()` correctly counts alive views. |
| Previous skin cleanup | ⚠️ PARTIAL | `resetToDefault()` sets `previousSkin` to old but doesn't call `dispose()` on it. `switchSkin()` stores `previousSkin` but doesn't call `dispose()`. `SkinPackage.dispose()` is documented as a no-op anyway. |

---

## 8. Summary & Recommendations

### Must-Fix Before Ship (P0)
1. **Issue #1**: Reconcile the `getResourcesForApplication()` usage with DD-01 architecture claim, or update the architecture.
2. **Issue #3**: Add missing test coverage for `ArsSkinEngine`, `SkinResources`, `ArsSkinLoader`, `SkinLayoutInflater`.
3. **Issue #4**: Fix listener set thread safety — wrap registration in mutex or use `CopyOnWriteArraySet`.

### Should-Fix Before Ship (P0)
4. **Issue #2**: Save/restore active skin identifier in `onSaveInstanceState`/`onRestoreInstanceState`.
5. **Issue #5**: Implement `LruCache` for resource ID lookups per DD-10.
6. **Issue #6**: Add missing `ArsActivity` convenience methods (`switchSkin`, `resetSkin`, `setSkinThemeMode`).
7. **Issue #8**: Redesign `ArsFragment.onCreateView()` to automatically wrap the inflater.

### Nice-to-Have (P1+)
8. **Issue #7**: Align `onSkinApplied` naming with architecture docs.
9. **Issue #9**: Tighten exception scope in `shouldSkip()`.
10. **Issue #10**: Implement `ArsSkinPackageManager`.

---

*End of review.*
