# Compose WinUI KWINRT 053-058 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the compose-winui KWINRT-053 through KWINRT-058 Gradle plugin ergonomics gaps inside `kotlin-winrt`.

**Architecture:** Keep the fix in `winrt-gradle-plugin`; do not move runtime behavior into samples. The JVM application host path infers a JVM target jar and runtime classpath, while the `mingwX64` path continues to infer native executable outputs and staged runtime assets instead of inheriting JVM classpath assumptions.

**Tech Stack:** Gradle plugin Kotlin, Kotlin Multiplatform Gradle plugin 2.4, Gradle TestKit, existing WinRT task types.

---

### Task 1: Lock Scanner Classpath Defaults

**Files:**
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Read: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`

- [x] **Step 1: Write the failing/confirming test**

Add a ProjectBuilder test named `plugin_configures_authoring_scanner_classpath_without_application_boilerplate` that applies `org.jetbrains.kotlin.multiplatform` and `io.github.composefluent.winrt`, configures `application {}`, and asserts `GenerateWinRTProjectionsTask.authoringScannerClasspath.files` contains the compiler plugin jar/classes plus Kotlin compiler/PSI runtime files without any user-created `winRtAuthoringScannerClasspath` configuration.

- [x] **Step 2: Run test to verify current behavior**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.plugin_configures_authoring_scanner_classpath_without_application_boilerplate --rerun-tasks --max-workers=1
```

Expected: pass if KWINRT-053 is already covered by current defaults, otherwise fail with an empty/missing scanner classpath assertion.

- [x] **Step 3: Implement only if needed**

If the test fails, keep the fix in `configureWinRTGeneration`: always add `kotlinWinRTCompilerPluginClasspath(project)` and `kotlinWinRTAuthoringScannerRuntimeClasspath(project)` to `GenerateWinRTProjectionsTask.authoringScannerClasspath` for JVM and KMP projects.

- [x] **Step 4: Verify**

Run the same targeted test again. Expected: PASS.

### Task 2: Infer JVM Host Runtime Classpath Without Breaking Mingw

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Write the failing KMP JVM target test**

Add a TestKit or ProjectBuilder test named `application_host_infers_kmp_jvm_target_runtime_classpath_and_jar`. The fixture should define `jvm("winuiJvm")`, `mingwX64 { binaries { executable() } }`, `winRT { application { mainClass.set("sample.MainKt") } }`, and no manual `BuildWinRTApplicationHostTask.runtimeClasspath` wiring. Assert `buildWinRTApplicationHost` depends on `winuiJvmJar` and its runtime classpath includes the `winuiJvmRuntimeClasspath` configuration plus the target jar.

- [x] **Step 2: Write the mingw guard assertion**

In the same test or a sibling test, assert `stageWinRTApplicationPackage` still depends on/link-stages the `mingwX64` release executable path through `configureMingwApplicationEntry`, and that no JVM runtime classpath configuration is added to the `mingwX64` package task.

- [x] **Step 3: Run the failing test**

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.application_host_infers_kmp_jvm_target_runtime_classpath_and_jar --rerun-tasks --max-workers=1
```

Expected before implementation: fail because KMP `buildWinRTApplicationHost` has no target jar/runtime classpath unless the sample wires it manually.

- [x] **Step 4: Implement JVM target inference**

Add a helper in `KotlinWinRTPlugin.kt` that, for `KotlinMultiplatformExtension`, selects the single JVM target or a JVM target matching the application target convention (`winuiJvm` preferred when present), wires `buildWinRTApplicationHost.runtimeClasspath` from `<targetName>RuntimeClasspath`, wires the target jar task, and depends on that jar. Keep this helper scoped to JVM targets only.

- [x] **Step 5: Verify**

Run the targeted test again. Expected: PASS, with mingw package behavior unchanged.

### Task 3: Harden Application Task Graph Defaults

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Write task graph test**

Add `application_host_task_graph_includes_generation_support_staging_and_host_prerequisites`. Assert `buildWinRTApplicationHost` depends directly or transitively on `generateWinRTProjections`, `mergeWinRTCompilerSupport`, `stageWinRTApplicationPackage`, `stageWinRTRuntimeAssets`, `buildWinRTAuthoringHost`, and the selected JVM jar task. Assert `runWinRTApplicationHost` depends on `buildWinRTApplicationHost`.

- [x] **Step 2: Run test**

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.application_host_task_graph_includes_generation_support_staging_and_host_prerequisites --rerun-tasks --max-workers=1
```

Expected: fail for whichever dependency is not currently declared.

- [x] **Step 3: Implement missing dependencies**

Add explicit task dependencies only where they represent true task inputs. Do not add sample-local dependencies. Keep `stageWinRTApplicationPackage` on the mingw/native packaging chain and `buildWinRTApplicationHost` on the JVM host chain.

- [x] **Step 4: Verify**

Run the targeted test again. Expected: PASS.

### Task 4: Add Typed Run Host Task

**Files:**
- Create: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/RunWinRTApplicationHostTask.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Write failing typed task test**

Add `run_host_is_first_class_typed_task`. Assert `runWinRTApplicationHost` is a `RunWinRTApplicationHostTask`, exposes `args`, `jvmArgs`, `environmentVariables`, `workingDirectory`, and optional `outputLog`, and still executes the generated host executable instead of invoking Gradle.

- [x] **Step 2: Run test**

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.run_host_is_first_class_typed_task --rerun-tasks --max-workers=1
```

Expected before implementation: fail because the task is plain `Exec`.

- [x] **Step 3: Implement task**

Create `RunWinRTApplicationHostTask` extending `Exec` or `DefaultTask` with `ExecOperations`. If extending `Exec`, add typed Gradle properties and map `jvmArgs` to `KOTLIN_WINRT_JVM_OPTIONS` before execution. Configure `executable` from `buildWinRTApplicationHost.outputDirectory/<executableBaseName>.exe`, pass `args`, write `outputLog` when configured, and keep Windows-only `onlyIf`.

- [x] **Step 4: Verify**

Run the targeted test again. Expected: PASS.

### Task 5: Make Runtime Assets And PRI Lifecycle First-Class

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Write lifecycle test**

Add `run_host_and_java_exec_own_runtime_assets_and_pri_lifecycle`. Use a fixture with a fake Windows App SDK package and fake `makepri`. Assert `runWinRTApplicationHost` depends on `stageWinRTApplicationPackage`, `stageWinRTRuntimeAssets`, and `buildWinRTApplicationHost`; assert JavaExec tasks continue to receive `-Dkotlin.winrt.runtimeAssetsRoot=...` for unpackaged apps.

- [x] **Step 2: Run test**

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.run_host_and_java_exec_own_runtime_assets_and_pri_lifecycle --rerun-tasks --max-workers=1
```

Expected: pass if existing wiring is sufficient, otherwise fail on missing dependencies.

- [x] **Step 3: Implement missing lifecycle wiring**

Wire missing dependencies in plugin task registration. Do not duplicate assertions in samples.

- [x] **Step 4: Verify**

Run targeted test again. Expected: PASS.

### Task 6: Validate Project Dependency Identity Contract

**Files:**
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`

- [x] **Step 1: Write dependency contract test**

Reuse the existing multi-project TestKit fixture `plugin_validates_multiplatform_winrt_library_consumed_by_multiplatform_winrt_application`, which already validates normal `project(...)` dependencies, application identity aggregation, transitive compiler-support merge, downstream suppression, and compiled support initialization without app-local dependency jar inspection.

- [x] **Step 2: Run test**

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.project_dependency_winrt_identity_contract_is_consumed_without_app_local_jar_checks --rerun-tasks --max-workers=1
```

Expected: pass if current contract is already sufficient, otherwise fail on missing dependency identity/support input.

- [x] **Step 3: Implement missing contract wiring**

Keep fixes in identity publication/consumption helpers. Do not make applications parse dependency jars directly; the plugin must own dependency identity resolution.

- [x] **Step 4: Verify**

Run the targeted test again. Expected: PASS.

### Task 7: Update Samples And Plan, Then Commit

**Files:**
- Modify: `PLAN.md`
- Modify only if needed: `winrt-samples/**/*.gradle.kts`

- [x] **Step 1: Remove sample workarounds made redundant by plugin defaults**

Remove manual `BuildWinRTApplicationHostTask.runtimeClasspath` wiring and explicit task dependencies from samples only after the new plugin tests pass.

- [x] **Step 2: Update PLAN.md**

Add one current-focus item for the compose-winui KWINRT-053 through KWINRT-058 ergonomics slice, with validation commands.

- [x] **Step 3: Run focused verification**

```powershell
.\gradlew.bat :winrt-gradle-plugin:compileKotlin :winrt-gradle-plugin:test --max-workers=1
```

Result: targeted 053-057 tests passed; the existing KMP library-to-application identity/compiler-support TestKit test used for 058 passed; sample task configuration for both edited sample projects passed. A full `:winrt-gradle-plugin:test --max-workers=1` run completed with the known unrelated `plugin_generates_sources_into_real_gradle_library_artifact` failure and an order-sensitive `compiler_plugin_rejects_nothing_authored_runtime_members` failure that passed when rerun targeted.

- [ ] **Step 4: Commit**

```powershell
git add PLAN.md winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt winrt-samples
git commit -m "Close compose WinUI Gradle ergonomics gaps"
```
