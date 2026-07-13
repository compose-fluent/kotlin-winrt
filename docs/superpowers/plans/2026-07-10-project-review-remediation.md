# Project Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct the runtime, metadata, generator, authoring, publication, Gradle wiring, and sample defects identified by the repository-wide review.

**Architecture:** Fixes follow the repository dependency order. Runtime owns ABI layout and lifetime, metadata owns identity validation, generator/authoring own emitted ABI contracts, projection modules own published FQNs and dependency exposure, the Gradle plugin separately models projection generation and authoring discovery, and samples validate only completed upstream behavior.

**Tech Stack:** Kotlin 2.4, Kotlin Multiplatform JVM and `mingwX64`, Java FFM, Gradle 9.4, JUnit/kotlin.test, WinRT COM ABI, local `.cswinrt` reference sources.

## Global Constraints

- `.cswinrt/` is the source of truth for ABI ownership and observable projection behavior.
- Preserve JVM and `mingwX64` semantic parity.
- Write and run a failing regression test before each production change.
- Update `PLAN.md` in every implementation slice.
- Preserve unrelated working-tree changes and commit each slice atomically.
- Run Gradle from Windows with `./gradlew.bat` and `--max-workers=1` for large gates.

---

### Task 1: Runtime ABI And Lifetime Foundations

**Files:**
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/NativeStructInterop.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/ValueBoxingMetadata.kt`
- Modify: `winrt-runtime/src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WeakReference.kt`
- Test: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/NativeStructInteropTest.kt`
- Test: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/ValueBoxingTest.kt`
- Test: `winrt-runtime/src/commonTest/kotlin/io/github/composefluent/winrt/runtime/WeakReferenceInteropTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `FinalizationHook.register(target, cleanup)`, `NativeWeakReferenceHandle.close()`.
- Produces: GUID layouts aligned to 4 bytes, `Array<Any?>` boxed as `InspectableArray`, and finalized native weak-reference ownership.

- [x] **Step 1: Add GUID layout regressions**

Add assertions that `NativeAbiLayout.GUID.byteAlignment == 4`, `NativeStructScalarKind.GUID.alignmentBytes == 4`, and a sequential `INT8, GUID` struct places the GUID at offset 4 with size 20 and alignment 4.

- [x] **Step 2: Verify the GUID test fails**

Run: `./gradlew.bat :winrt-runtime:jvmTest --tests "*NativeStructInteropTest*" --rerun-tasks --max-workers=1`

Expected: FAIL because GUID alignment and offset are currently `1`.

- [x] **Step 3: Correct both shared GUID descriptors**

Set both GUID alignment constants to `4`; do not add a third GUID classification table.

- [x] **Step 4: Add object-array shape regressions**

Round-trip these declared `Array<Any?>` values through `Marshaler.inspectableAny()`:

```kotlin
arrayOf<Any?>(1, "text")
arrayOf<Any?>(null, "text")
arrayOf<Any?>(1, 2)
```

Assert the values retain inspectable element semantics instead of acquiring the first element's scalar-array IID.

- [x] **Step 5: Verify the object-array test fails**

Run: `./gradlew.bat :winrt-runtime:jvmTest --tests "*ValueBoxingTest*" --rerun-tasks --max-workers=1`

Expected: heterogeneous `Array<Any?>` fails while writing or exposes a scalar array interface.

- [x] **Step 6: Keep declared object arrays inspectable**

Change `normalizeManagedArray` so `componentType == Any::class` selects `objectMetadata` without sampling array contents.

- [x] **Step 7: Add native weak-reference cleanup regression**

Use the existing weak-reference/CCW test support to count the owned `IWeakReference` release after the wrapper becomes unreachable and `PlatformFinalization.drain()` runs. Also assert `setTarget` releases the previous handle exactly once.

- [x] **Step 8: Verify weak-reference cleanup fails**

Run: `./gradlew.bat :winrt-runtime:jvmTest --tests "*WeakReferenceInteropTest*" --rerun-tasks --max-workers=1`

Expected: the abandoned wrapper retains one native weak-reference owner.

- [x] **Step 9: Register idempotent finalization cleanup**

Add a `FinalizationHook` registration whose cleanup closes the current native handle without capturing the public wrapper strongly. Coordinate finalizer and `setTarget` through a small lock-protected owner state so a handle is closed once.

- [x] **Step 10: Validate and commit runtime slice**

Run: `./gradlew.bat :winrt-runtime:jvmTest :winrt-runtime:mingwX64Test --max-workers=1`

Update `PLAN.md`: mark Review-Fix-01 complete and Review-Fix-02 `正在做`.

Commit: `git commit -m "fix(runtime): correct ABI layout and weak ownership"`

### Task 2: Metadata Identity Validation

**Files:**
- Modify: `winrt-metadata/src/main/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModel.kt`
- Test: `winrt-metadata/src/test/kotlin/io/github/composefluent/winrt/metadata/WinRTMetadataModelTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `WinRTTypeDefinition.merge`.
- Produces: deterministic fail-closed IID merge diagnostics.

- [x] **Step 1: Add conflicting IID merge test**

Create two same-name interface definitions with different non-null GUIDs and assert normalization fails with a message containing the qualified name and both GUIDs. Add a control case where one IID is null and the non-null IID is retained.

- [x] **Step 2: Verify the conflict test fails**

Run: `./gradlew.bat :winrt-metadata:test --tests "*WinRTMetadataModelTest*conflicting*" --rerun-tasks --max-workers=1`

Expected: the model currently succeeds and keeps the left IID.

- [x] **Step 3: Add explicit IID merge validation**

Compute the merged IID with a `when`: accept null enrichment and equal values; otherwise throw an error naming the type and both values. Use that value in the copied definition.

- [x] **Step 4: Validate and commit metadata slice**

Run: `./gradlew.bat :winrt-metadata:test --max-workers=1`

Update `PLAN.md`: complete Review-Fix-02 and mark Review-Fix-03 `正在做`.

Commit: `git commit -m "fix(metadata): reject conflicting WinRT identities"`

### Task 3: Generator And Authoring ABI Ownership

**Files:**
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionSupportRenderer.kt`
- Modify: `winrt-generator/src/main/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGenerator.kt`
- Modify: `winrt-authoring/src/commonMain/kotlin/io/github/composefluent/winrt/authoring/WinRTAuthoring.kt`
- Test: `winrt-generator/src/test/kotlin/io/github/composefluent/winrt/projections/generator/KotlinProjectionGeneratorTest.kt`
- Test: `winrt-authoring/src/commonTest/kotlin/io/github/composefluent/winrt/authoring/WinRTAuthoringCommonTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `WinRTPlatformApi.coTaskMemAllocRaw`, generated authored receive-array handlers.
- Produces: caller-owned CoTaskMem arrays, collision-free generation, null-on-failure activation factory outputs.

- [x] **Step 1: Add authored array allocator rendering test**

Render an authored blittable receive-array return and assert generated code contains `WinRTPlatformApi.coTaskMemAllocRaw`, does not contain `PlatformAbi.allocateBytesOwned`, writes null for an empty result, and writes the allocated pointer for non-empty results.

- [x] **Step 2: Verify allocator rendering test fails**

Run: `./gradlew.bat :winrt-generator:test --tests "*authored*array*" --rerun-tasks --max-workers=1`

Expected: rendered support uses `allocateBytesOwned`.

- [x] **Step 3: Emit CoTaskMem ownership transfer**

Generate a conditional allocation:

```kotlin
val data = if (value.isEmpty()) PlatformAbi.nullPointer
else WinRTPlatformApi.coTaskMemAllocRaw(value.size.toLong() * elementSize)
```

Write elements only for non-empty arrays and return the pointer without a Kotlin-owned allocation handle.

- [x] **Step 4: Add duplicate output-path test**

Construct two rendered files whose normalized relative paths collide and assert `generateTo` fails before writing the second file. Verify the message contains the relative path.

- [x] **Step 5: Verify collision test fails**

Run: `./gradlew.bat :winrt-generator:test --tests "*duplicate*path*" --rerun-tasks --max-workers=1`

Expected: generation succeeds and one file overwrites the other.

- [x] **Step 6: Reject duplicate normalized paths**

In `write`, resolve and normalize the output path, require `expectedPaths.add(path)` to succeed, and fail with an owner/path diagnostic before `writeToIfChanged`.

- [x] **Step 7: Add activation factory output test**

Initialize `factoryOut` with a sentinel pointer, pass an invalid HSTRING or a fallback that throws, and assert the returned HRESULT fails while `factoryOut` becomes null.

- [x] **Step 8: Verify activation factory test fails**

Run: `./gradlew.bat :winrt-authoring:jvmTest --tests "*activation*factory*" --rerun-tasks --max-workers=1`

Expected: sentinel remains after the exception.

- [x] **Step 9: Clear output before fallible operations**

After validating the two input addresses, call `PlatformAbi.writePointer(factoryOut, PlatformAbi.nullPointer)` before decoding `activatableClassId`.

- [x] **Step 10: Validate and commit generator/authoring slice**

Run: `./gradlew.bat :winrt-generator:test :winrt-authoring:jvmTest :winrt-authoring:mingwX64Test --max-workers=1`

Update `PLAN.md`: complete Review-Fix-03 and mark Review-Fix-04 `正在做`.

Commit: `git commit -m "fix(authoring): preserve transferred ABI ownership"`

### Task 4: Split Projection Ownership And Publication

**Files:**
- Modify: `winrt-projections/windows-app-sdk/build.gradle.kts`
- Add: `winrt-projections/windows-webview2/build.gradle.kts`
- Modify: `winrt-projections/windows-ui-xaml/build.gradle.kts`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/ValidateGeneratedWinRTProjectionOutputTask.kt`
- Test: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: root `build.gradle.kts`
- Modify: `settings.gradle.kts`
- Test: `winrt-projections/windows-webview2-consumer-fixture`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: dependency identity projected-type/source-addition ownership.
- Produces: one owner per generated FQN and API-visible Windows SDK dependency variants.

- [x] **Step 1: Add cross-artifact duplicate audit regression**

Configure the validation task with two class roots containing the same relative `.class` path and assert it fails with both owners and the duplicate path.

- [x] **Step 2: Verify duplicate audit regression fails**

Run: `./gradlew.bat -p winrt-gradle-plugin :test --tests "*generated_projection_output_audit*" --rerun-tasks --max-workers=1`

Expected: current task audits one artifact at a time and accepts the duplicate.

- [x] **Step 3: Add cross-owner inputs and wire split artifacts**

Extend the audit task with named dependency class roots or add a dedicated root verification task. Wire Windows App SDK and Windows.UI.Xaml outputs against Windows SDK outputs; fail on every shared relative class path except explicitly documented metadata files.

- [x] **Step 4: Make Windows SDK a public dependency**

Replace both `commonMainImplementation(project(":winrt-projections:windows-sdk"))` declarations with `commonMainApi(...)`.

- [x] **Step 5: Add publication metadata regression**

Generate the JVM and multiplatform POM/module metadata and assert Windows SDK is present on the compile/API surface, not runtime-only or optional for consumers that need public signature types.

- [x] **Step 6: Remove dependency-owned generated output**

Use the existing dependency identity suppression in the split projection builds so App SDK/XAML metadata may resolve Windows references but cannot emit Windows SDK-owned declarations or support additions. Validate the duplicate audit reports zero duplicate class paths.

Keep WebView2 available through a dedicated prebuilt artifact: generate `Microsoft.Web.WebView2.Core` from `Microsoft.Web.WebView2`, exclude that namespace from Windows App SDK output, publish the Core artifact as an App SDK API dependency, and retain the WinUI `Microsoft.UI.Xaml.Controls.WebView2` control in the App SDK artifact. Compile isolated JVM and `mingwX64` consumers for both the standalone Core coordinate and the top-level App SDK coordinate.

- [x] **Step 7: Validate and commit projection slice**

Run the split JVM and `mingwX64` compile tasks, generated-output audits, publication metadata generation, and isolated consumer compile tasks with `--max-workers=1`.

Update `PLAN.md`: complete Review-Fix-04 and mark Review-Fix-05 `正在做`.

Commit: `git commit -m "fix(projections): enforce split artifact ownership"`

### Task 5: Separate Gradle Projection And Authoring Decisions

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: existing `kotlinWinRTLocalGenerationRequired` projection inventory decision.
- Produces: independent projection-generation and authoring-discovery/validation task decisions.

- [x] **Step 1: Add authoring-only TestKit regression**

Create a JVM/KMP fixture with no local projection request and one public `@WinRTAuthoredRuntimeClass`. Assert compilation receives compiler-plugin authoring options and produces authored candidates, WinMD, and host manifest; `check`/`jar` must depend on authored validation.

- [x] **Step 2: Verify authoring-only fixture fails**

Run the targeted plugin test with `--rerun-tasks --max-workers=1`.

Expected: compiler options are absent and authored outputs are missing.

- [x] **Step 3: Split decision providers**

Retain `kotlinWinRTLocalGenerationRequired` only for generated projection/source roots and projection-support merge tasks. Add an authoring-capable decision that keeps compiler plugin options and authored validation active for Kotlin compilations without forcing dependency-owned projection generation or identity cycles.

- [x] **Step 4: Preserve runtime-only and forwarding-library behavior**

Update existing tests so runtime-only libraries still avoid projection generation, dependency-owned exact type requests remain suppressed, and identity generation does not depend on target jars. Empty authoring outputs are acceptable; silent discovery disablement is not.

- [x] **Step 5: Validate and commit Gradle slice**

Run targeted ProjectBuilder/TestKit tests, then `./gradlew.bat -p winrt-gradle-plugin :test --max-workers=1` with a documented timeout large enough for the suite.

Update `PLAN.md`: complete Review-Fix-05 and mark Review-Fix-06 `正在做`.

Commit only the plugin changes belonging to this slice, preserving any unrelated pre-existing edits.

Commit: `git commit -m "fix(gradle): keep authoring discovery independent"`

### Task 6: Sample Reachability And Final Gates

**Files:**
- Modify: `winrt-samples/build.gradle.kts`
- Test: add or extend the owning Gradle configuration test in `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: root `build.gradle.kts`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: completed runtime/generator/projection/Gradle contracts.
- Produces: explicit WinUI sample enablement and final review remediation validation gate.

- [x] **Step 1: Add sample-mode configuration regression**

Assert default configuration selects WinUI and an explicit property such as `kotlinWinRT.samples.enableWinUI=false` selects `noWinuiMain` without restoring Windows App SDK packages.

- [x] **Step 2: Verify disabled mode is currently unreachable**

Run the targeted configuration test and a sample `tasks`/compile invocation with the disable property.

Expected: current `orElse("2.1.3")` keeps WinUI enabled.

- [x] **Step 3: Add explicit sample enable property**

Keep default `true`; conditionally define the Windows App SDK version and WinUI dependencies only when enabled. Remove the null check that can never be false under the default provider.

- [x] **Step 4: Add final review remediation gate**

Register a root verification task depending on the focused runtime, metadata, generator, authoring, Gradle, projection ownership/publication, and sample-mode checks. Keep external packaging/install smoke tasks separate when they require machine state.

- [x] **Step 5: Run completion audit**

Run every command named by the six tasks, inspect generated POM/module metadata, confirm duplicate class count is zero, verify `git diff --check`, and confirm `PLAN.md` has all Review-Fix items checked with no `正在做` item remaining.

- [x] **Step 6: Commit final validation slice**

Commit: `git commit -m "test: close project review remediation gates"`
