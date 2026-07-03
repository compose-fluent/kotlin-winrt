# WASDK Interop Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate CsWinRT-style `winrt.interop` and `microsoft.ui.Win32Interop` helper surfaces as owner-scoped projection output, with downstream suppression through dependency identity.

**Architecture:** `.cswinrt/src/cswinrt/WinRT.Interop.idl` and `.cswinrt/src/cswinrt/strings/ComInteropHelpers.cs` define the Windows interop helper shape; Windows App SDK `Microsoft.UI.Interop.h` defines the `Win32Interop` export bridge. Kotlin keeps public helpers in generator/projection output, models helper FQNs in `winrt-metadata`, publishes those FQNs in Gradle identity, and suppresses duplicate generation when a dependency already owns them.

**Tech Stack:** Kotlin/JVM and Kotlin/Native KMP source generation, `winrt-metadata`, `winrt-generator`, Gradle plugin identity JSON, existing `winrt-runtime` ABI primitives only.

---

### Task 1: Source-Addition Ownership Model

**Files:**
- Modify: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataTraversal.kt`
- Test: `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt`

- [x] **Step 1: Add failing metadata coverage**

Add assertions to the namespace-addition inventory test so `WinRT.Interop` owns `winrt.interop.WindowNative` and `winrt.interop.InitializeWithWindow`, while `Microsoft.UI` owns `microsoft.ui.Win32Interop`.

- [x] **Step 2: Run metadata test and verify failure**

Run: `.\gradlew.bat :winrt-metadata:test --tests "io.github.composefluent.winrt.metadata.WinRTMetadataModelTest.projection inventory includes namespace additions for included namespaces"`

Expected: FAIL because `WinRTNamespaceAddition` has no generated FQN ownership and no `Microsoft.UI` addition.

- [x] **Step 3: Implement metadata model**

Extend `WinRTNamespaceAddition` with a deterministic `generatedTypeNames: List<String>`. Add a `WinRTNamespaceAddition("Microsoft.UI", generatedTypeNames = listOf("microsoft.ui.Win32Interop"))`. Set `WinRT.Interop` generated names to `winrt.interop.WindowNative` and `winrt.interop.InitializeWithWindow`, and trigger them from `Windows.*` projection ownership to mirror CsWinRT including `WinRT.Interop.winmd` with the Windows projection.

- [x] **Step 4: Run metadata test and verify pass**

Run the same targeted metadata test.

Expected: PASS.

### Task 2: Generator Output and Suppression

**Files:**
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt`
- Test: `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`

- [x] **Step 1: Add failing generator coverage**

Add tests proving an owner emits `microsoft.ui.Win32Interop` once, `WinRT.Interop` emits the two `winrt.interop` helpers, and a generator constructed with suppressed source-addition FQNs omits those files and manifest rows.

- [x] **Step 2: Run generator tests and verify failure**

Run: `.\gradlew.bat :winrt-generator:test --tests "io.github.composefluent.winrt.projections.generator.KotlinProjectionGeneratorTest.*interop*"`

Expected: FAIL because no public helper files are emitted and source-addition suppression does not exist.

- [x] **Step 3: Implement generator suppression and helper rendering**

Add a `suppressedSourceAdditionTypeNames: Set<String>` constructor parameter to `KotlinProjectionGenerator` and a matching `excludedSourceAdditionTypeNames` parameter to `KotlinProjectionSupportRenderer.render`. Filter `inventory.namespaceAdditions` by `generatedTypeNames`. Render generated helper files for `winrt.interop.WindowNative`, `winrt.interop.InitializeWithWindow`, and `microsoft.ui.Win32Interop` using only existing runtime primitives.

- [x] **Step 4: Run generator tests and verify pass**

Run the targeted generator tests.

Expected: PASS.

### Task 3: Dependency Identity

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTIdentityTask.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt`
- Test: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Add failing Gradle identity coverage**

Add tests that identity JSON writes `sourceAdditions`, downstream dependency identity returns those FQNs for suppression, and duplicate dependency owners for the same source addition fail closed.

- [x] **Step 2: Run Gradle plugin tests and verify failure**

Run: `.\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.*source addition*"`

Expected: FAIL because identity does not publish or consume source additions.

- [x] **Step 3: Implement identity read/write**

Write `"sourceAdditions": [...]` from the actual generated `kotlin-winrt-support/source-additions.tsv` source-addition compiler input. Extend `ProjectionSurfaceIdentity` with `sourceAdditions`. Add `dependencySourceAdditionTypeNames(...)` and pass that set into `KotlinProjectionGenerator`. Reject duplicate source addition claims from multiple dependency identity files with a `GradleException`.

- [x] **Step 4: Run Gradle plugin tests and verify pass**

Run the targeted Gradle plugin tests.

Expected: PASS.

### Task 4: Plan, Validation, Commit

**Files:**
- Modify: `PLAN.md`

- [x] **Step 1: Update repository plan**

Mark the WASDK interop item complete only after metadata, generator, and Gradle identity tests pass. If actual helper bodies remain narrower than the full CsWinRT helper set, keep the non-goal explicit.

- [x] **Step 2: Run focused validation**

Run: `.\gradlew.bat :winrt-metadata:test :winrt-generator:test :winrt-gradle-plugin:test`

Expected: PASS, or report any unrelated pre-existing failure with exact task/test name.

Actual: targeted metadata/generator/Gradle identity tests passed, including manifest-backed `sourceAdditions` publication and the `Windows.*` trigger for `winrt.interop` helpers; `:winrt-runtime:compileKotlinJvm` and `:winrt-runtime:compileKotlinMingwX64` passed; full `:winrt-metadata:test :winrt-generator:test` passed. Actual generated Windows SDK output includes `winrt.interop` helpers and actual generated Windows App SDK output includes `microsoft.ui.Win32Interop`. `:winrt-projections:windows-app-sdk:compileCommonMainKotlinMetadata`, `:winrt-projections:windows-app-sdk:compileKotlinMingwX64 --max-workers=1`, and `:winrt-projections:windows-app-sdk:compileKotlinJvm --max-workers=1` passed. Full `:winrt-gradle-plugin:test --max-workers=1` currently fails outside this slice in `plugin_generates_sources_into_real_gradle_library_artifact`: the real Gradle fixture jar lacks the test-expected `windows/foundation/IStringable.class` and contains the current Foundation contract/metadata output.

- [x] **Step 3: Commit the coherent slice**

Run:

```powershell
git add docs/superpowers/plans/2026-07-03-wasdk-interop-generation.md PLAN.md winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataTraversal.kt winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTIdentityTask.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/NativeExportInvoker.kt winrt-runtime/src/jvmMain/kotlin/io/github/composefluent/winrt/runtime/NativeExportInvoker.jvm.kt winrt-runtime/src/mingwX64Main/kotlin/io/github/composefluent/winrt/runtime/NativeExportInvoker.mingwX64.kt
git commit -m "Generate owner-scoped WASDK interop additions"
```

Expected: one commit containing the source-addition parity slice.
