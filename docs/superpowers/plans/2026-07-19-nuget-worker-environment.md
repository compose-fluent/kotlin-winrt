# NuGet Worker Environment Propagation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the official `nuget install` restore path while ensuring projection-generation workers explicitly pass the Gradle build's `NUGET_PACKAGES` value to every configured or cached NuGet CLI child process.

**Architecture:** Model `NUGET_PACKAGES` on `GenerateWinRTProjectionsTask`, forward it through `GenerateWinRTProjectionsWorkParameters`, and apply it inside `NuGetCliSupport` immediately before `ProcessBuilder.start()`. The existing package lookup roots and task-local install output remain unchanged.

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

- [ ] **Step 1: Write the failing explicit-environment regression test**

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

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.NuGetCliSupportTest.install_applies_explicit_global_packages_directory" --no-build-cache --max-workers=1 --console=plain
```

Expected: test compilation fails because `NuGetCliSupport` has no `nugetPackagesDirectory` constructor parameter.

- [ ] **Step 3: Implement the narrow ProcessBuilder override**

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

- [ ] **Step 4: Run the test and verify GREEN**

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

- [ ] **Step 1: Write the failing task-wiring assertion**

In `plugin_wires_extension_inputs_to_generation_task`, add:

```kotlin
assertEquals(
    System.getenv("NUGET_PACKAGES"),
    task.nugetPackagesDirectory.orNull,
)
```

- [ ] **Step 2: Run the task-wiring test and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.plugin_wires_extension_inputs_to_generation_task" --no-build-cache --max-workers=1 --console=plain
```

Expected: test compilation fails because `GenerateWinRTProjectionsTask` has no `nugetPackagesDirectory` property.

- [ ] **Step 3: Add the task and worker parameter**

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

- [ ] **Step 4: Wire the Gradle environment provider**

In `KotlinWinRTPlugin.configureWinRTGeneration`, set:

```kotlin
task.nugetPackagesDirectory.set(
    project.providers.environmentVariable("NUGET_PACKAGES"),
)
```

Do not expose this property through `WinRTExtension`.

- [ ] **Step 5: Run both focused regressions and verify GREEN**

Run:

```powershell
.\gradlew.bat :winrt-gradle-plugin:test `
  --tests "io.github.composefluent.winrt.gradle.NuGetCliSupportTest.install_applies_explicit_global_packages_directory" `
  --tests "io.github.composefluent.winrt.gradle.KotlinWinRTPluginTest.plugin_wires_extension_inputs_to_generation_task" `
  --no-build-cache --max-workers=1 --console=plain
```

Expected: both tests pass with zero failures.

- [ ] **Step 6: Update the canonical plan**

Mark `NuGet-Worker-Environment` complete in `PLAN.md`, record that `nuget install` remains the resolver, record the focused test command and result, and link this implementation plan:

```markdown
- [x] NuGet-Worker-Environment: preserved the official `nuget install` dependency-closure path and explicitly propagated the owning Gradle process's optional `NUGET_PACKAGES` value through projection worker parameters into both configured and cached NuGet CLI child processes. Process-isolated generation now populates the configured global cache while retaining its clean task-local metadata install root. No `packages.config`, MSBuild, custom NuGet solver, projection-shape change, or public DSL was introduced. Validation: the focused NuGet launcher and projection task-wiring regressions pass on Windows. Design: `docs/superpowers/specs/2026-07-19-nuget-worker-environment-design.md`. Plan: `docs/superpowers/plans/2026-07-19-nuget-worker-environment.md`.
```

- [ ] **Step 7: Review and commit the atomic fix**

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
