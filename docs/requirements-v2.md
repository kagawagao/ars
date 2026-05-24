# ARS v2 — Product Requirements Document

**Status:** Draft v1.0
**Author:** Product Manager
**Date:** 2026-05-17
**Target Release:** ARS v2.0.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [User Personas](#3-user-personas)
4. [Functional Requirements](#4-functional-requirements)
   - [P0 — Minimum Viable Overlay Framework](#p0--minimum-viable-overlay-framework)
   - [P1 — Polish & Production Readiness](#p1--polish--production-readiness)
   - [P2 — Ecosystem & Futures](#p2--ecosystem--futures)
5. [Non-Functional Requirements](#5-non-functional-requirements)
6. [Use Cases & User Stories](#6-use-cases--user-stories)
7. [Success Metrics](#7-success-metrics)
8. [Out of Scope](#8-out-of-scope)
9. [Assumptions & Dependencies](#9-assumptions--dependencies)
10. [Glossary](#10-glossary)

---

## 1. Executive Summary

ARS v2 is a ground-up rebuild of the Android Resource Switch skinning framework. Its core mission: **make every View in an Android application automatically reflect the active skin without developer intervention.** Unlike v1, which used a fake "overlay" approach where skin resources lived in a separate `Resources` instance invisible to Views, v2 will use genuine Android ResourceOverlay mechanisms so that `getResources().getColor()` and `getDrawable()` on any View transparently returns skin-aware values.

The framework targets Android 14+ (API 34+) applications and provides base `Activity`, `Application`, and `Fragment` classes that developers extend to opt into automatic skinning.

---

## 2. Problem Statement

### Current State (v1)

| Problem | Impact |
|---|---|
| Skin resources are loaded into an isolated `Resources` instance via `PackageManager.getResourcesForApplication()` | Views never see skin resources; developers must manually call `SkinAttribute.applySkin()` on every single View |
| No `LayoutInflater.Factory2` integration | Views inflated from XML are invisible to the skinning system |
| `ResourceOverlayHelper.enableOverlay()` throws `UnsupportedOperationException` | The advertised "overlay" mechanism is non-functional; requires unattainable system permissions |
| `ArsActivity.onThemeChanged()` calls `recreate()` | Full Activity destruction/recreation on every theme change; slow, flickers, loses transient state |
| `ArsActivity` does not override `onSaveInstanceState` | Scroll position, text input, and other transient state is permanently lost on theme change |
| `SkinAttribute.getAttributeName()` returns namespaced names (`"android:background"`) but `SUPPORTED_ATTRS` uses short names (`"background"`) | **Attribute matching is broken by design** — zero attributes are ever applied |
| Deprecated `drawableLeft`/`drawableRight` used instead of `drawableStart`/`drawableEnd` | Incorrect layout direction support (RTL broken) |
| Single trivial test (enum value check) | No confidence in framework correctness |
| Demo app "Load Skin" button shows only a Toast | No demonstration of actual skinning capability |

### Vision for v2

An Android developer adds `ars-core` to their project, extends `ArsActivity` instead of `AppCompatActivity`, drops a skin APK into a designated directory or loads it from network, and calls `ArsSkinManager.switchSkin("skin-name")`. **Every View in the app — existing and newly inflated — immediately reflects the new skin.** No manual calls. No recreation. No lost state.

---

## 3. User Personas

### P-A: Android App Developer (Primary)

- Builds consumer-facing apps requiring dark/light theme support or branded skin variants
- Wants zero-code-change skinning after initial framework integration
- Values reliability and minimal APK size overhead
- Familiar with Android resource system but not with AOSP internals

### P-B: SDK/Tooling Developer

- Builds UI component libraries that need to respect host app skinning
- Needs clear extension points and lifecycle hooks in the skinning framework
- May need to register custom attribute handlers

### P-C: Design System Maintainer

- Creates and distributes skin packages as APKs
- Needs a well-defined skin package format specification
- May use tooling to preview skins before distribution

---

## 4. Functional Requirements

### P0 — Minimum Viable Overlay Framework

*These requirements define the smallest shippable v2 that delivers genuine overlay-based skinning.*

#### FR-P0-01: Genuine Resource Overlay Loading

The framework MUST load skin resource packages using Android's ResourceOverlay mechanism so that `Resources.getColor()`, `Resources.getDrawable()`, `Resources.getDimension()`, and related calls on any View or Context within the application return skin-overridden values.

**Acceptance criteria:**
- A View inflated from XML with `android:background="@color/primary"` displays the skin's `primary` color after a skin switch, without any manual `setBackgroundColor()` call
- The behavior is identical to Android's built-in dark theme resource qualification (`values-night/`), but switchable at runtime
- No use of `PackageManager.getResourcesForApplication()` as the primary skin-loading mechanism

#### FR-P0-02: Automatic View Skinning via LayoutInflater Interception

The framework MUST automatically intercept View creation during XML layout inflation so that no manual `SkinAttribute.applySkin()` call is required.

**Acceptance criteria:**
- When `ArsActivity.setContentView(R.layout.main)` is called, all Views in the inflated hierarchy are automatically skin-aware
- Dynamically added Views (e.g., `Button(ctx)`, `View.inflate(ctx, ...)`) are also skin-aware without manual intervention
- The interception does not break AppCompat's own `LayoutInflater.Factory2` (e.g., automatic `AppCompatTextView` substitution)

#### FR-P0-03: Skin Switching Without Activity Recreation

The framework MUST apply a new skin to the currently visible View hierarchy without calling `Activity.recreate()`.

**Acceptance criteria:**
- Calling `switchSkin("new-skin")` updates all visible Views within 500ms on a device with 100+ visible Views
- No Activity restart occurs during the switch
- No visible flicker or blank screen during the transition
- View state (scroll position, text selection, expanded/collapsed state, user input) is preserved

#### FR-P0-04: Transient State Preservation

The framework MUST preserve transient UI state when a skin switch coincides with a configuration change (e.g., device rotation during skin switch) or when the system kills and restores the Activity.

**Acceptance criteria:**
- `ArsActivity` properly saves and restores its instance state via `onSaveInstanceState`/`onRestoreInstanceState`
- After a skin switch + configuration change, Views retain scroll position, text input content, and selection state
- The framework does not require the host app developer to manually save/restore skin-related state

#### FR-P0-05: Correct Attribute Name Resolution

The framework MUST correctly match View attributes between the XML namespace and the internal attribute registry.

**Acceptance criteria:**
- `android:background`, `android:textColor`, `android:src`, and all supported attributes are correctly identified regardless of whether they are specified with or without the `android:` namespace prefix in the attribute registry
- A comprehensive attribute test suite validates every supported attribute against real inflated Views

#### FR-P0-06: RTL-Aware Drawable Attributes

The framework MUST use direction-aware drawable attributes (`drawableStart`/`drawableEnd`) instead of deprecated `drawableLeft`/`drawableRight`.

**Acceptance criteria:**
- `TextView` compound drawables are correctly positioned in RTL layouts
- No usage of `drawableLeft`, `drawableRight`, `drawableTop`, or `drawableBottom` in the framework code

#### FR-P0-07: Extensible Base Classes

The framework MUST provide base classes that host apps extend to enable skinning with zero additional configuration beyond the class hierarchy change.

**Acceptance criteria:**
- `ArsApplication` — initializes the skinning engine; host app extends this instead of `Application`
- `ArsActivity` — enables automatic View skinning and skin switch handling; host app extends this instead of `AppCompatActivity`
- `ArsFragment` — enables skinning within Fragment-hosted View hierarchies; host app extends this instead of `Fragment`
- Extending these base classes is the *only* requirement for a host Activity to be fully skinnable

#### FR-P0-08: Skin Package Format

The framework MUST define a clear, versioned format for skin packages.

**Acceptance criteria:**
- Skin packages are standard Android APK files containing only resources (no DEX code)
- A required metadata file or manifest entry identifies the package as an ARS skin and specifies the skin name, version, and target app package
- The format specification is documented for skin creators (design system maintainers)

#### FR-P0-09: Skin Package Loading from Storage

The framework MUST load skin packages from device-local storage.

**Acceptance criteria:**
- Skins can be loaded from a designated directory on external or internal storage
- A skin package is validated before loading (correct format, compatible version, not corrupted)
- Loading failure produces a clear, actionable error (log + optional callback) without crashing the host app

#### FR-P0-10: Working Demo Application

The demo application MUST demonstrate actual skin switching with visible, correct results.

**Acceptance criteria:**
- The demo app includes at least 2 distinct skin packages (e.g., "Light" and "Dark" or "Default" and "Branded")
- Tapping a "Switch Skin" button immediately changes colors, drawables, and dimensions across all visible Views
- The demo showcases at minimum: TextView colors, Button backgrounds, ImageView drawables, and View dimensions
- The demo demonstrates that scroll position and text input are preserved across skin switches

#### FR-P0-11: Core Test Suite

The framework MUST have a test suite that validates all P0 functional requirements.

**Acceptance criteria:**
- Unit tests cover `ArsSkinManager` skin loading/unloading logic
- Unit tests cover attribute name resolution for all supported attributes
- Integration tests (instrumented) verify that inflated Views display skin resources after a switch
- Test coverage on core logic is ≥80%

---

### P1 — Polish & Production Readiness

*These requirements make the framework trustworthy for production use.*

#### FR-P1-01: Lightweight View-Tree Walk

The framework MUST apply skin changes by walking the current View tree and notifying each View, rather than reloading the entire Activity.

**Acceptance criteria:**
- Skin switch time is proportional to the number of visible Views, not the total Views in the application
- Views in the back stack (non-visible Activities/Fragments) are skinned lazily when they become visible
- The walk is non-recursive for deeply nested hierarchies to avoid stack overflow

#### FR-P1-02: Skin Change Lifecycle Callbacks

The framework MUST provide hooks so host apps and third-party libraries can react to skin changes.

**Acceptance criteria:**
- `onSkinChanged(oldSkin, newSkin)` callback on `ArsActivity` and `ArsFragment`
- A global listener interface (`SkinChangeListener`) that any component can register
- Callbacks fire *after* all Views have been updated, not during the update

#### FR-P1-03: Custom Attribute Handler Registration

The framework MUST allow host apps and library developers to register handlers for custom attributes not in the default supported set.

**Acceptance criteria:**
- `ArsSkinManager.registerAttributeHandler(attributeName, handler)` API
- Handlers receive the View, attribute name, and resolved resource ID/value
- Third-party UI libraries can ship their own attribute handler registrations

#### FR-P1-04: Skin Invalidation Control

The framework MUST allow Views to opt out of skinning or defer skin application for performance-critical scenarios.

**Acceptance criteria:**
- `View.setSkinningEnabled(false)` prevents the View and its subtree from being updated on skin switch
- An annotation or tag (`R.id.ars_skip_skinning`) excludes a View from the view-tree walk
- The host app can manually trigger skin re-application on excluded Views

#### FR-P1-05: Graceful Degradation on Unsupported Resources

The framework MUST handle missing skin resources gracefully.

**Acceptance criteria:**
- If a skin package does not define a color used by a View, the View retains its default (app-bundled) resource value
- If an entire resource type is missing from the skin, Views using that type are unaffected
- Warnings are logged for missing resources in debug builds; silent in release builds

#### FR-P1-06: Error Handling & Diagnostics

The framework MUST provide clear diagnostics when skin-related operations fail.

**Acceptance criteria:**
- Every public API call returns a result type (success/error) rather than throwing uncaught exceptions
- A `SkinError` sealed class hierarchy encodes all possible failure modes: corrupted package, incompatible version, missing permission, storage error, parse error
- Debug mode logs detailed skin resource resolution traces
- A `ArsSkinManager.getDiagnostics()` method returns current skin state for debugging

#### FR-P1-07: Thread Safety

The framework MUST be safe to use from any thread, with skin switches handled correctly regardless of calling thread.

**Acceptance criteria:**
- `switchSkin()` can be called from a background thread; the View-tree walk always executes on the main thread
- Concurrent calls to `switchSkin()` are serialized; the last call wins
- Skin package loading (I/O) happens off the main thread

#### FR-P1-08: Memory Management

The framework MUST not leak memory during skin switches or when Activities are destroyed.

**Acceptance criteria:**
- Previous skin resources are released when a new skin is loaded (except the default skin)
- No Activity or View references are retained after `onDestroy()`
- Memory usage of the framework itself is <5MB in a typical app

#### FR-P1-09: Comprehensive Instrumented Tests

The framework MUST have instrumented tests that verify real on-device behavior.

**Acceptance criteria:**
- Tests run on API 34 emulator in CI
- Tests verify: View appearance after skin switch, state preservation, LayoutInflater interception, RTL drawable positioning
- A test skin APK is built as part of the test suite

#### FR-P1-10: API Documentation

The framework MUST have complete public API documentation.

**Acceptance criteria:**
- All public classes, methods, and properties have KDoc comments
- A getting-started guide explains integration in ≤5 minutes
- Skin package format specification is published separately

---

### P2 — Ecosystem & Futures

*These are aspirational features that expand the framework's reach.*

#### FR-P2-01: Network Skin Download

The framework SHOULD support downloading skin packages from a remote URL.

**Acceptance criteria:**
- `ArsSkinManager.downloadSkin(url, callback)` API
- Download progress reporting
- Automatic integrity verification (checksum) after download
- Downloaded skins are cached locally and usable offline

#### FR-P2-02: Skin Package Encryption

The framework SHOULD support encrypted skin packages to protect branded/paid skin assets.

**Acceptance criteria:**
- Skin packages can be encrypted at rest
- The framework decrypts them in memory during loading
- Encryption keys are managed through a pluggable key provider interface (not hardcoded)

#### FR-P2-03: Incremental Skin Updates

The framework SHOULD support delta updates to skin packages to minimize download size.

**Acceptance criteria:**
- Only changed resources are downloaded when updating from one skin version to another
- Delta is applied to the local skin package to produce the updated version
- Fallback to full download if delta application fails

#### FR-P2-04: Skin Preview Mode

The framework SHOULD support applying a skin temporarily without committing it.

**Acceptance criteria:**
- `ArsSkinManager.previewSkin(skinName)` applies the skin but does not persist the choice
- `ArsSkinManager.cancelPreview()` reverts to the previously active skin
- Preview mode is visually distinguishable (optional badge/indicator)

#### FR-P2-05: Visual Skin Editor

The framework MAY ship with or integrate with a visual editor for creating and modifying skin packages.

**Acceptance criteria:**
- A companion tool (desktop or web) loads a base APK and allows overriding colors, drawables, and dimensions
- The tool produces a valid ARS skin APK
- Real-time preview on a connected device or emulator

#### FR-P2-06: Plugin Architecture

The framework MAY support third-party plugins that extend skinning behavior.

**Acceptance criteria:**
- A defined plugin interface for resource transformation during skin loading
- Plugins can add support for custom resource types
- Plugins are discovered and loaded at runtime via a service loader or similar mechanism

#### FR-P2-07: Multi-Skin Simultaneous Support

The framework MAY support different skins for different parts of the application.

**Acceptance criteria:**
- A `SkinScope` API allows applying a skin to a specific Activity or View subtree without affecting the rest of the app
- Multiple skins can be active simultaneously in different scopes

#### FR-P2-08: Skin Analytics

The framework MAY emit analytics events for skin usage to help product teams understand which skins are popular.

**Acceptance criteria:**
- Events: skin_loaded, skin_switched, skin_downloaded, skin_load_failed
- Opt-in; disabled by default
- Pluggable analytics backend

---

## 5. Non-Functional Requirements

### NFR-01: Performance

| Metric | Target |
|---|---|
| Skin switch latency (100 visible Views) | <500ms |
| Skin package load time (1MB skin APK) | <200ms |
| Layout inflation overhead (vs. no framework) | <5% |
| Memory overhead (framework, idle) | <5MB |
| APK size overhead (ars-core library) | <200KB |

### NFR-02: Compatibility

- **Minimum SDK:** Android 14 (API 34)
- **Target SDK:** Matches latest stable release
- **Kotlin:** 1.9.x+
- **Build System:** Gradle with Kotlin DSL
- **AGP:** 8.2.x+
- **Dependencies:** AndroidX Core KTX, AppCompat (version constraints documented)
- **No conflicts** with Jetpack Compose (Compose UIs should not crash; full Compose skinning is out of scope for v2)

### NFR-03: Reliability

- The framework MUST NOT crash the host application under any circumstances
- Skin switching errors MUST be recoverable — the previous skin must remain active
- The framework MUST survive and correctly handle concurrent configuration changes during skin switch

### NFR-04: Security

- Skin packages MUST NOT execute arbitrary code (no DEX loading from skins)
- Skin packages are subject to standard Android package signature verification
- No runtime reflection on private Android framework APIs (do not break Google Play policy)

### NFR-05: Maintainability

- Public API surface is clearly separated from internal implementation (`internal` visibility where possible)
- Core logic is in pure Kotlin modules testable without Android dependencies where feasible
- CI pipeline (GitHub Actions) runs all tests on every PR

---

## 6. Use Cases & User Stories

### UC-01: Dark/Light Theme Toggle

**As a** user of a reading app,
**I want** to switch between dark and light themes instantly,
**So that** I can read comfortably in different lighting conditions without losing my place in the article.

**Flow:**
1. User taps the theme toggle in the app toolbar
2. All text colors, backgrounds, and icons update to the selected theme
3. Scroll position in the article is preserved
4. The choice persists across app restarts

### UC-02: Branded Skin for Partner Apps

**As a** product manager,
**I want** to distribute a partner-branded version of our app as a skin package instead of a separate APK,
**So that** we maintain a single codebase while offering customized experiences.

**Flow:**
1. Designer creates a skin APK with partner colors, logos, and typography
2. Skin is loaded from assets or downloaded on first launch
3. The entire app reflects the partner brand without any code changes
4. Default branding can be restored at any time

### UC-03: Seasonal/Holiday Themes

**As a** marketing manager,
**I want** to push a holiday-themed skin to all users during a promotion,
**So that** the app feels festive and drives engagement.

**Flow:**
1. Holiday skin package is uploaded to a CDN
2. App detects available seasonal skin on launch
3. Skin is downloaded and applied automatically (with user consent)
4. After the promotion ends, the app reverts to the default skin

### UC-04: Third-Party Library Integration

**As a** library author,
**I want** my custom UI components to respect the host app's ARS skin,
**So that** my library integrates seamlessly.

**Flow:**
1. Library developer registers custom attribute handlers for their View subclass
2. When the host app switches skins, the library's Views update automatically
3. The library does not need a direct dependency on ars-core (optional dependency or interface-based)

### UC-05: Debug/QA Skin Testing

**As a** QA engineer,
**I want** to rapidly switch between skin variants during testing,
**So that** I can verify all skins render correctly across the app.

**Flow:**
1. QA engineer loads multiple skin packages onto the test device
2. A debug drawer (FloatingActionButton or shake gesture) shows available skins
3. Tapping a skin previews it instantly
4. Screenshots can be captured for each skin for visual regression testing

---

## 7. Success Metrics

| Metric | Target | Measurement |
|---|---|---|
| Time to integrate (new project) | ≤5 minutes | Timed walkthrough with sample app |
| Skin switch latency (P50) | <300ms | Instrumented benchmark on API 34 emulator |
| Crash-free sessions | 99.9% | Firebase/Crashlytics or equivalent |
| Manual skin-apply calls required | 0 (for standard Views) | Code review of demo app |
| Test coverage (core module) | ≥80% line coverage | JaCoCo in CI |
| P0 bugs at release | 0 | Issue tracker |
| Demo app correctly switches skins | Yes | Manual QA checklist |

---

## 8. Out of Scope

The following are explicitly excluded from ARS v2:

- **Jetpack Compose skinning:** v2 focuses on the View system. Compose uses a fundamentally different rendering pipeline. A separate `ars-compose` module may be considered for a future release.
- **Android API < 34 support:** ResourceOverlay APIs are native to Android 14+. Backporting to older APIs is not feasible.
- **Dynamic DEX/code loading from skins:** Skins are resource-only APKs. Running arbitrary code from skins is a security non-starter.
- **Server-side skin management platform:** A companion backend service for hosting, versioning, and delivering skins is a separate product.
- **Runtime font loading:** While font resources in skin packages are desirable, the primary focus is colors, drawables, and dimensions. Fonts are P2 at earliest.
- **Skin package creation GUI:** The visual editor (FR-P2-05) is aspirational. Skin packages can be created with standard Android build tooling (AAPT2).
- **Cross-platform skinning (iOS, Flutter, React Native):** ARS is Android-only.
- **Gradle plugin for skin package generation:** Out of scope for v2; skin packages are built manually or with a simple script.

---

## 9. Assumptions & Dependencies

### Assumptions

1. **ResourceOverlay APIs are stable on API 34+.** Android 14's overlay system (via `OverlayManager` or equivalent mechanism) provides sufficient public API surface for a third-party framework to load and activate overlay packages without system-level permissions.
2. **Skin packages are resource-only APKs.** They contain `resources.arsc` and asset files but no DEX bytecode. Android's package manager can parse them.
3. **Host apps use standard Android Views and resource references.** Custom drawing logic that bypasses `Resources` (e.g., loading drawables from raw InputStream) cannot be skinned automatically.
4. **One active skin at a time (per scope).** P0 assumes a single global skin. Multi-skin scoping is P2.
5. **Host app targets API 34+.** The framework provides no backward compatibility shims.

### Dependencies

| Dependency | Purpose | Risk |
|---|---|---|
| Android ResourceOverlay APIs (API 34) | Core skinning mechanism | API may be restricted or require permissions not available to third-party apps. Mitigation: Spike/PoC in first sprint to validate feasibility. |
| AndroidX AppCompat | Base Activity/Fragment classes | Well-maintained; low risk. |
| AAPT2 | Skin package creation tooling | Bundled with Android SDK; stable. |
| Kotlin 1.9.x | Language runtime | Mature; low risk. |

---

## 10. Glossary

| Term | Definition |
|---|---|
| **Skin** | A complete set of visual overrides (colors, drawables, dimensions, etc.) packaged as a resource APK. |
| **Skin Package** | A resource-only APK file conforming to the ARS skin format specification. |
| **ResourceOverlay** | Android's native mechanism for replacing app resources at runtime by layering an overlay package over the base APK. Also known as RRO (Runtime Resource Overlay). |
| **Default Skin** | The resources bundled in the host application's APK. Always available as a fallback. |
| **Skin Switch** | The operation of changing the active skin at runtime. Must update all visible Views. |
| **View-Tree Walk** | Traversing the currently visible View hierarchy to notify each View of a skin change. |
| **Attribute Handler** | A callback invoked when a specific View attribute needs to be updated during a skin switch. |
| **LayoutInflater.Factory2** | Android's hook for intercepting View creation during XML layout inflation. Used by AppCompat and ARS v2. |
| **P0/P1/P2** | Priority levels: P0 = must-have for MVP, P1 = should-have for production readiness, P2 = could-have / aspirational. |

---

*End of document.*
