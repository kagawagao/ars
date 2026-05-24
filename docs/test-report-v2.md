# ARS V2 — Test Report

**Date:** 2026-05-17
**Commit:** `c78c27a` (post-P0 fixes) → subsequent fixes for B-02/B-03/B-04
**Tester:** Hermes Agent (QA)
**Target:** ARS v2.0.0

---

## Test Execution Summary

| Category | Status | Count |
|----------|--------|-------|
| Unit tests (JUnit) | ✅ All passing | 27 tests in 3 files |
| Compilation (debug) | ✅ Success | 5 warnings (deprecation) |
| Compilation (release) | ✅ Success | 5 warnings (deprecation) |
| Instrumented tests | ❌ Not run | Requires Android emulator (API 34) |

## Test Files

| File | Tests | Status |
|------|-------|--------|
| `SkinAttributeResolverTest.kt` | 15 | ✅ All pass |
| `SkinErrorTest.kt` | 9 | ✅ All pass |
| `SkinResultTest.kt` | 6 | ✅ All pass (1 unreachable-code warning) |

## Compilation Warnings (non-blocking)

| Level | File | Message |
|-------|------|---------|
| WARN | `ArsSkinEngine.kt:270` | `updateConfiguration` deprecated |
| WARN | `ArsSkinEngine.kt:278` | `updateConfiguration` deprecated |
| WARN | `ArsSkinLoader.kt:133` | `updateConfiguration` deprecated |
| WARN | `ArsViewTreeWalker.kt:52` | `getChildAt()` returns nullable (!! needed) |
| WARN | `ArsViewTreeWalker.kt:55` | same |
| WARN | `ArsViewTreeWalker.kt:57` | same |
| WARN | `SkinResultTest.kt:39` | Unreachable code in `getOrThrow` test |

## Bugs Found During Testing

### P0 — Critical (blocks release)
- **None found** — compilation passes, all unit tests pass.

### P1 — High (should fix)

- **BUG-P1-01**: `ArsViewTreeWalker.walk()` has 3 type-mismatch warnings — `view.getChildAt(i)` returns `View?` but `Queue<View>` expects non-null. The `?.let { queue.add(it) }` pattern on line 63 is correct but the variable on line 52 (`view = queue.poll()`) also returns nullable. This is safe in practice (queue is never empty inside the loop) but triggers Kotlin warnings.
  - **Location**: `ArsViewTreeWalker.kt:52, 55, 57`
  - **Fix**: Add `?: continue` after `queue.poll()` on line 52, and `?: continue` after `getChildAt(i)` inside the loop.

- **BUG-P1-02**: `SkinResources.getColor(id, null)` in `applySkinToView()` — `getColor(id, Theme?)` is deprecated in API 34, removed in API 36. Should use `getColor(id)` (non-deprecated overload).
  - **Location**: `ArsSkinEngine.kt:573`
  - **Related**: Same issue for `getDrawable(id, null)` on line 574 — already suppressed at class level.

### P2 — Medium (should fix)

- No P2 bugs found in unit test execution.

### P3 — Low (nice to fix)

- **BUG-P3-01**: `SkinResultTest.kt:39` has unreachable code — the `fail("Should have thrown")` call after `result.getOrThrow()` in a try/catch is flagged by the compiler. The intent is correct (verify an exception is thrown), but the code structure triggers a warning.
  - **Location**: `SkinResultTest.kt:39`

## Requirements Coverage (Tested vs Untested)

| FR-P0-# | Requirement | Tested? |
|---------|-------------|---------|
| P0-01 | Genuine Resource Overlay | ❌ No resource interception tests |
| P0-02 | Automatic View Skinning | ❌ No LayoutInflater tests |
| P0-03 | No Activity Recreation | ❌ No View-tree walk tests |
| P0-04 | Transient State Preservation | ❌ No state tests |
| P0-05 | Correct Attribute Name Resolution | ✅ 15 tests in SkinAttributeResolverTest |
| P0-06 | RTL-Aware Drawables | ✅ Covered by P0-05 alias tests |
| P0-07 | Extensible Base Classes | ❌ No integration tests |
| P0-08 | Skin Package Format | ❌ No format validation tests |
| P0-09 | Skin Loading from Storage | ❌ No loader tests |
| P0-10 | Working Demo Application | ❌ Demo uses v1 API (will not compile) |
| P0-11 | Core Test Suite (≥80%) | ❌ Only ~27% of architecture test files exist |

**Estimated code coverage:** <40% of core logic.

## Regression Check

- ✅ All previously passing tests still pass
- ✅ No previously fixed bugs reopened
- ✅ All compilation errors from the previous build (B-02, SkinResources nullability) are fixed
- ✅ `dispose()` now uses `runBlocking` — compiles and is correct for teardown

## Verdict

**NEEDS FIXES** — The unit tests pass, but the test suite covers only attribute resolution, error types, and result types. The core engine (`ArsSkinEngine`), resource interception (`SkinResources`), View-tree walker (`ArsViewTreeWalker`), and layout inflater (`SkinLayoutInflater`) have **zero test coverage**. Instrumented tests are required to validate genuine overlay behavior on a real device.

### Blockers resolved since last review:
- B-02: `dispose()` suspend bug → fixed with `runBlocking`
- B-03: Memory leak (old skin not released) → fixed with `oldSkin?.dispose()`
- B-04: Missing ArsActivity convenience methods → added `switchSkin()`, `resetSkin()`, `setSkinThemeMode()`

### Remaining blocker:
- B-01: Demo app still uses v1 API — needs rewrite before the demo can demonstrate the framework.

### Recommended next steps:
1. Rewrite demo app with v2 API (B-01)
2. Add unit/instrumented tests for core engine components
3. Fix compilation warnings in ArsViewTreeWalker
