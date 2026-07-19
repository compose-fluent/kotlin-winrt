# NuGet Worker Environment Propagation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the official `nuget install` restore path while ensuring projection-generation workers explicitly pass the Gradle build's `NUGET_PACKAGES` value to every configured or cached NuGet CLI child process.

**Architecture:** Model the complete owning Gradle environment on `GenerateWinRTProjectionsTask`, apply it to `ProcessWorkerSpec.forkOptions.environment(...)`, and let the Worker and its child tools inherit it. The original narrow `NUGET_PACKAGES` transport is retained below as the completed investigation slice; Task 3 supersedes it at the environment boundary. The existing package lookup roots and task-local install output remain unchanged.

**Tech Stack:** Kotlin/JVM, Gradle Worker API, Gradle Provider API, Microsoft NuGet CLI, JUnit 4, Gradle ProjectBuilder.

## Global Constraints

- Keep `nuget install`; do not add `packages.config`, MSBuild, `dotnet restore`, or a Kotlin-owned NuGet dependency solver.
- Preserve the task-local `-OutputDirectory` used for projection metadata isolation.
- Forward only the modeled `NUGET_PACKAGES` value; do not replace it with the task scratch directory.
- Leave all non-isolated NuGet callers unchanged when no explicit override is supplied.
- Do not add a public Gradle DSL property.
- Update root `PLAN.md` in the implementation commit.
- Run focused validation on Windows through `.\gradlew.bat`.

---

### Task 1: Make the NuGet launcher accept an explicit global-packages directory

**Files:**
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupportTest.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupport.kt`

**Interfaces:**
- Consumes: an optional `String?` containing the caller's effective `NUGET_PACKAGES` value.
- Produces: `NuGetCliSupport(..., nugetPackagesDirectory: String? = null, ...)`, which applies the override to both configured and cached CLI attempts.

- [x] **Step 1: Write the failing explicit-environment regression test**

Add a Windows-only test that creates a fake `nuget.cmd`, records `%NUGET_PACKAGES%`, supplies an override that differs from the ambient process value, and requires the recorded line to equal the override:

```kotlin
@Test
fun install_applies_explicit_global_packages_directory() {
    assumeTrue(System.getProperty("os.name").contains("Windows", ignoreCase = true))
    val root = Files.createTempDirectory("kotlin-winrt-nuget-cli-explicit-cache-")
    val logFile = root.resolve("nuget-invocation.txt")
    val executable = root.resolve("nuget.cmd")
    Files.writeString(
        executable,
        """
        @echo off
        >>"$logFile" echo NUGET_PACKAGES=%NUGET_PACKAGES%
        exit /b 0
        """.trimIndent(),
    )
    val expectedPackagesDirectory = root.resolve("global-packages").toString()

    NuGetCliSupport(
        executable = executable.toString(),
        cliVersion = "7.3.1",
        cliCacheDirectory = root.resolve("cli-cache"),
        nugetPackagesDirectory = expectedPackagesDirectory,
        logger = Logging.getLogger(NuGetCliSupportTest::class.java),
    ).run(
        arguments = listOf(
            "install",
            "Microsoft.WindowsAppSDK.Base",
            "-Version",
            "1.8.251216001",
            "-NonInteractive",
            "-OutputDirectory",
            root.resolve("install").toString(),
        ),
        description = "install Microsoft.WindowsAppSDK.Base",
    )

    assertTrue(
        Files.readAllLines(logFile).contains("NUGET_PACKAGES=$expectedPackagesDirectory"),
    )
}
```

Also strengthen the existing `install_keeps_global_package_cache_and_disables_nuget_internal_parallelism` test so the no-override path proves that `NuGetCliSupport` preserves the ambient value:

```kotlin
assertTrue(
    invocation.lineSequence().any { line ->
        line == "NUGET_PACKAGES=${System.getenv("NUGET_PACKAGES").orEmpty()}"
    },
)
```

- [x] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.NuGetCliSupportTest.install_applies_explicit_global_packages_directory" --no-build-cache --max-workers=1 --console=plain
```

Expected: test compilation fails because `NuGetCliSupport` has no `nugetPackagesDirectory` constructor parameter.

- [x] **Step 3: Implement the narrow ProcessBuilder override**

Add the optional constructor parameter and apply it before starting the process:

```kotlin
internal class NuGetCliSupport(
    private val executable: String,
    private val cliVersion: String,
    private val cliCacheDirectory: Path,
    private val scratchDirectory: Path? = null,
    private val nugetPackagesDirectory: String? = null,
    private val logger: Logger,
) {
```

```kotlin
nugetPackagesDirectory
    ?.takeIf(String::isNotBlank)
    ?.let { packagesDirectory ->
        processBuilder.environment()["NUGET_PACKAGES"] = packagesDirectory
    }
```

Keep the existing `TEMP`, `TMP`, `TMPDIR`, and `NUGET_SCRATCH` assignments unchanged.

- [x] **Step 4: Run the test and verify GREEN**

Run the same focused command from Step 2.

Expected: `install_applies_explicit_global_packages_directory` passes.

### Task 2: Carry the Gradle environment value through projection process isolation

**Files:**
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `project.providers.environmentVariable("NUGET_PACKAGES")` in the owning Gradle process.
- Produces: optional `GenerateWinRTProjectionsTask.nugetPackagesDirectory: Property<String>` and matching `GenerateWinRTProjectionsWorkParameters.nugetPackagesDirectory`, passed to `NuGetCliSupport`.

- [x] **Step 1: Write the failing task-wiring assertion**

In `plugin_wires_extension_inputs_to_generation_task`, add:

```kotlin
assertEquals(
    System.getenv("NUGET_PACKAGES"),
    task.nugetPackagesDirectory.orNull,
)
```

- [x] **Step 2: Run the task-wiring test and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.plugin_wires_extension_inputs_to_generation_task" --no-build-cache --max-workers=1 --console=plain
```

Expected: test compilation fails because `GenerateWinRTProjectionsTask` has no `nugetPackagesDirectory` property.

- [x] **Step 3: Add the task and worker parameter**

In `GenerateWinRTProjectionsTask`, add:

```kotlin
@get:Input
@get:Optional
abstract val nugetPackagesDirectory: Property<String>
```

Pass it when submitting work:

```kotlin
parameters.nugetPackagesDirectory.set(nugetPackagesDirectory)
```

Add the matching worker parameter:

```kotlin
val nugetPackagesDirectory: Property<String>
```

Pass it to the launcher:

```kotlin
nugetPackagesDirectory = parameters.nugetPackagesDirectory.orNull,
```

- [x] **Step 4: Wire the Gradle environment provider**

In `KotlinWinRTPlugin.configureWinRTGeneration`, set:

```kotlin
task.nugetPackagesDirectory.set(
    project.providers.environmentVariable("NUGET_PACKAGES"),
)
```

Do not expose this property through `WinRTExtension`.

- [x] **Step 5: Run both focused regressions and verify GREEN**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test `
  --tests "io.github.composefluent.winrt.gradle.NuGetCliSupportTest.install_applies_explicit_global_packages_directory" `
  --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.plugin_wires_extension_inputs_to_generation_task" `
  --no-build-cache --max-workers=1 --console=plain
```

Expected: both tests pass with zero failures. The final three-test Windows run completed with `BUILD SUCCESSFUL` and zero failures/errors.

- [x] **Step 6: Update the canonical plan**

Mark `NuGet-Worker-Environment` complete in `PLAN.md`, record that `nuget install` remains the resolver, record the focused test command and result, and link this implementation plan:

```markdown
- [x] NuGet-Worker-Environment: preserved the official `nuget install` dependency-closure path and explicitly propagated the owning Gradle process's optional `NUGET_PACKAGES` value through projection worker parameters into both configured and cached NuGet CLI child processes. Process-isolated generation now populates the configured global cache while retaining its clean task-local metadata install root. No `packages.config`, MSBuild, custom NuGet solver, projection-shape change, or public DSL was introduced. Validation: the focused NuGet launcher and projection task-wiring regressions pass on Windows. Design: `docs/superpowers/specs/2026-07-19-nuget-worker-environment-design.md`. Plan: `docs/superpowers/plans/2026-07-19-nuget-worker-environment.md`.
```

- [x] **Step 7: Review and commit the atomic fix**

Run:

```powershell
git diff --check
git status --short
```

Confirm only the planned plugin, tests, `PLAN.md`, and implementation-plan files are involved. Then commit:

```powershell
git add -- PLAN.md winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupport.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupportTest.kt winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt
git commit -m "fix(gradle): propagate NuGet packages into projection worker"
```

---

### Task 3: Propagate the complete build environment at the Worker boundary

**Files:**
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPluginTest.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/GenerateWinRTProjectionsTask.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/KotlinWinRTPlugin.kt`
- Modify: `winrt-gradle-plugin/src/main/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupport.kt`
- Modify: `winrt-gradle-plugin/src/test/kotlin/io/github/composefluent/winrt/gradle/NuGetCliSupportTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `project.providers.environmentVariablesPrefixedBy("")` from the owning Gradle process.
- Produces: `GenerateWinRTProjectionsTask.workerEnvironment: MapProperty<String, String>`, applied to `ProcessWorkerSpec.forkOptions.environment` before submitting the action.
- Removes: the Worker-parameter and `NuGetCliSupport` special case that carried only `NUGET_PACKAGES`; child NuGet processes inherit the complete Worker environment naturally.

- [x] **Step 1: Add the failing full-environment wiring assertion**

`KotlinWinRTPluginTest.plugin_wires_extension_inputs_to_generation_task` now requires the task's environment map to equal `System.getenv()`, and `generation_worker_forwards_complete_environment_to_nuget_child_process` exercises the complete Worker-to-NuGet boundary with a fake CLI and minimal WinMD package.

- [x] **Step 2: Run the regression and verify RED**

Run the focused task-wiring test. It failed to compile with `Unresolved reference 'workerEnvironment'`, confirming the assertion was RED for the expected missing input.

- [x] **Step 3: Apply the environment at process isolation**

Add the task input, wire the empty-prefix environment provider, and call `spec.forkOptions.environment(workerEnvironment.get())` before submitting the projection action. Remove the redundant `nugetPackagesDirectory` Worker parameter and launcher override; preserve the task-local `TEMP`, `TMP`, `TMPDIR`, and `NUGET_SCRATCH` overrides.

- [x] **Step 4: Update regressions and verify GREEN**

Keep the NuGet launcher test focused on inherited environment and add coverage that a fake NuGet CLI receives an arbitrary environment value through a real isolated generation Worker. The focused wiring/launcher tests and the real Worker-boundary test passed on Windows with `BUILD SUCCESSFUL`; the temporary removal of `forkOptions.environment(...)` made the Worker-boundary test record an empty value and fail.

- [x] **Step 5: Update the design/plan and commit the follow-up**

Document Gradle's intentional environment sanitization and the generator Worker's complete-environment requirement, run `git diff --check`, and commit the follow-up as one atomic change. The unrelated UAC15 producer/consumer fixture remains a pre-existing fixture-model failure (missing projected generic `IAsyncOperation` prerequisite) and is not part of this environment slice.
