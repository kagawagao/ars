# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ARS (Android Resource Switch) v2 — an Android 14+ skinning/theming framework that achieves genuine ResourceOverlay behavior without system permissions. The framework intercepts `getResources()` calls at the Context level so Views automatically reflect the active skin, requiring zero code changes beyond extending `ArsActivity` / `ArsFragment` / `ArsApplication`.

## Commands

```bash
# Build everything
./gradlew build

# Run all tests (unit tests on host JVM)
./gradlew test

# Run a single test class or method
./gradlew :ars-core:test --tests "com.kagawagao.ars.ArsSkinEngineTest"
./gradlew :ars-core:test --tests "com.kagawagao.ars.SkinThemeEndToEndTest"

# Run Robolectric tests (Android-dependent tests that use RuntimeEnvironment)
./gradlew :ars-core:test --tests "com.kagawagao.ars.SkinThemeEndToEndTest"

# Build only the core library
./gradlew :ars-core:build

# Build only the demo app
./gradlew :app:build
```

## Architecture

ARS v2 is a **hybrid ContextWrapper + LayoutInflater.Factory2** architecture. Two mechanisms work together to achieve automatic skinning:

### The Two-Mechanism Design

1. **SkinResources (Resources subclass)** — Overrides `getColor()`, `getDrawable()`, `getDimension()`, etc. For every resource lookup, resolves the resource by name in the skin APK first, then falls through to base (host) Resources. Uses name-based lookup (`getIdentifier("primary", "color", skinPkg)`) rather than ID-based because AAPT assigns different IDs per APK. Caches (hostResId → skinResId) in an LRU cache invalidated on skin switch.

2. **SkinLayoutInflater (Factory2)** — Intercepts View creation during XML inflation. Delegates to AppCompat's Factory2 first (preserving automatic `AppCompatTextView` substitution), then scans the `AttributeSet` for skin-eligible attributes and records `(View, resId, attributeName, resourceType)` bindings as `SkinViewMeta`. On skin switch, the engine re-applies resources to each View via type-safe setters. This handles Views that cache resource values at construction time (e.g., `TextView` reads `textColor` once).

### Core Component Layers

```
Public API:  ArsActivity, ArsFragment, ArsDialog, ArsDialogFragment,
             ArsPopupWindow, ArsApplication, ArsToast, ArsOverlaySkin,
             ArsSpinnerAdapter
                  │
Engine:      ArsSkinEngine (object/singleton)
             — Skin state machine, View-tree walk orchestration, listener dispatch
             — Thread-safe via Mutex for skin switches, @Volatile for state reads
             — skin switch: I/O on Dispatchers.IO, View updates on Dispatchers.Main
                  │
Internal:    SkinResources (Resources subclass — name-based resource interception)
             SkinContextWrapper (ContextWrapper — injects SkinResources into Context)
             SkinLayoutInflater (Factory2 — captures View-to-resource bindings)
             ArsViewTreeWalker (BFS walker — queue-based, avoids stack overflow)
             SkinAttributeResolver (namespace stripping, supported attribute registry)
             ArsSkinLoader (APK validation, metadata extraction, Resources creation)
```

### Key Design Decisions

- **No reflection** for resource loading — uses `PackageManager.getResourcesForApplication()` with `sourceDir` set to the skin path (deprecated but public API), NOT `AssetManager.addAssetPath()` (hidden API).
- **No Activity recreation** on skin switch — Views are walked and re-applied in-place, preserving scroll position and text input.
- **Sealed `SkinResult<T>` / `SkinError`** — all public APIs return results, never throw from expected runtime conditions. Errors include `FileNotFound`, `CorruptedPackage`, `NotASkinPackage`, `TargetMismatch`, `IncompatibleVersion`, `StorageError`, `ResourceNotFound`, `SwitchInProgress`.
- **Theme mode (light/dark)** uses `Context.createConfigurationContext()` on API 34+ rather than the deprecated `Resources.updateConfiguration()`. Skin theme changes never recreate Activities.
- **WeakReference-based registries** for Views, Activities, and SkinResources instances — prevents leaks when components are destroyed without explicit unregistration.

### Skin Package Format

Skin packages are standard resource-only APKs with ARS metadata in `AndroidManifest.xml`:
- `ars-skin-name` (string) — human-readable name
- `ars-skin-version` (int) — must equal `ArsSkinLoader.FRAMEWORK_VERSION` (currently 1)
- `ars-target-package` (string) — must match the host app's package name

Resource names must match the host app exactly (name-based lookup). No `classes.dex` needed.

### Public API Surface (all in `com.kagawagao.ars`)

- `ArsSkinEngine` — singleton; `init()`, `switchSkin()` (suspend), `resetToDefault()` (suspend), `setThemeMode()`, `wrapContext()`, `createSkinFactory()`, listener/handler registration, `getDiagnostics()`
- `ArsApplication` — base Application, call `ArsSkinEngine.init(this)` in `onCreate()`
- `ArsActivity` / `ArsFragment` — base Activity/Fragment; auto-wrap context, install Factory2, no-recreation skin refresh
- `ArsDialog` / `ArsDialogFragment` / `ArsPopupWindow` — skin-aware window wrappers
- `SkinChangeListener` — `onSkinChanged(previous: SkinPackage?, current: SkinPackage?)`; fires AFTER all Views are updated
- `SkinAttributeHandler` — `fun interface` for custom View attribute skinning
- `SkinPackage` — data class (name, packageName, targetPackage, version, resources, path, themeHint)

### Internal Package (`com.kagawagao.ars.internal`)

All classes are `internal` — not part of the public API contract. Key classes: `SkinResources`, `SkinContextWrapper`, `SkinLayoutInflater`, `ArsViewTreeWalker`, `SkinAttributeResolver`, `SkinViewMeta`/`AttrBinding`/`ResourceType`, `ArsSkinLoader`.

### Module Structure

- **`ars-core/`** — library module (compileSdk=34, minSdk=34). Depends on `androidx.core:core-ktx`, `androidx.appcompat:appcompat`, `kotlinx-coroutines-android`
- **`app/`** — demo app. Demonstrates skin switching, theme toggling, all UI patterns (Activity, Fragment, Dialog, PopupWindow, Toast, Snackbar, RecyclerView with ViewBinding)

### Tests

Tests live in `ars-core/src/test/`. Uses JUnit 4 + Robolectric 4.12 for tests that need real Android `Context`, `Resources`, and `Configuration` objects (e.g., `SkinThemeEndToEndTest`, `SkinThemeColorResolutionTest`). Pure unit tests (`ArsSkinEngineTest`, `SkinResultTest`, `SkinErrorTest`) run without Robolectric.

To add a new Robolectric test, annotate with `@RunWith(RobolectricTestRunner::class)` and optionally `@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])`.

### Documentation vs. Code Reality

The root-level `API.md`, `IMPLEMENTATION.md`, `SUMMARY.md`, and `PROJECT_STRUCTURE.txt` describe the **v1 API** (classes like `ArsSkinManager`, `SkinLoader`, `ResourceOverlayHelper`, `SkinAttribute`). These have all been removed/replaced in v2. The authoritative up-to-date docs are in `docs/` (especially `docs/architecture-v2.md` and `docs/getting-started.md`).