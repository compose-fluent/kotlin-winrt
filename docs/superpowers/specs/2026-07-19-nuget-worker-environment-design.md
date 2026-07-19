# NuGet Worker Environment Propagation Design

## Goal

Preserve the existing Microsoft `nuget install` dependency-resolution path while ensuring that projection generation launched through Gradle `processIsolation` observes the complete environment of the owning Gradle build, including NuGet cache, SDK, toolchain, and proxy settings.

## Root Cause

`GenerateWinRTProjectionsTask` performs metadata loading and NuGet restore inside a process-isolated Gradle worker. Gradle's `DefaultProcessWorkerSpec` intentionally sanitizes process environments; on Windows it initializes the fork environment as an empty map. The owning Gradle task sees `NUGET_PACKAGES`, `WINDIR`, `ProgramFiles(x86)`, `KOTLIN_WINRT_WINDOWS_SDK_ROOT`, and tool/proxy variables, but the worker does not. The worker implementation and the tools it launches read those values directly, so forwarding only one NuGet variable leaves the process boundary semantically incomplete.

The existing `nugetGlobalPackagesRoots` model remains useful for locating already restored packages, but it does not configure the write destination of a NuGet CLI process. This read/write mismatch is the defect.

## Design

1. Keep `nuget install` as the only restore command used by the current projection-generation path. NuGet remains responsible for source configuration, authentication, dependency closure resolution, version selection, download, and global-cache population.
2. Model the owning Gradle process's complete environment as an input using `project.providers.environmentVariablesPrefixedBy("")`, so configuration-cache tracking and task invalidation see the same environment that drives the worker.
3. Apply that map to `ProcessWorkerSpec.forkOptions.environment(...)` before submitting `GenerateWinRTProjectionsWorkAction`. This opts this environment-dependent generator worker into the normal build environment while retaining Gradle's isolated classpath and JVM boundary.
4. Let `NuGetCliSupport` and the authoring/metadata subprocesses inherit the Worker environment naturally. Do not carry a second, single-variable `NUGET_PACKAGES` parameter through `WorkParameters`.
5. Continue setting `TEMP`, `TMP`, `TMPDIR`, and `NUGET_SCRATCH` to the task scratch directory for NuGet child processes. Do not replace `NUGET_PACKAGES` with a task-local cache.
6. Leave non-isolated NuGet callers behaviorally unchanged; `ProcessBuilder` continues to inherit their owning process environment.

## Tradeoff

Gradle sanitizes process-isolated environments intentionally for hermeticity and worker reuse. This generator Worker is different: its metadata discovery, SDK selection, external tool lookup, NuGet configuration, and proxy/authentication behavior are explicitly environment-dependent. Treating the complete map as a task input makes that dependency visible to Gradle and invalidates cached generation when the environment changes, which is preferable to silently generating against a different SDK or package cache. No public DSL is added; the map is sourced only from the owning Gradle process.

## Non-Goals

- Do not introduce `packages.config`, MSBuild, `dotnet restore`, or a Kotlin implementation of NuGet dependency-closure solving.
- Do not remove the task-local `-OutputDirectory`; projection metadata continues to consume the isolated legacy install directory while NuGet also populates its global cache.
- Do not reinterpret `nugetGlobalPackagesRoots` as a writable-cache priority list.
- Do not change WinRT metadata selection, generator output, package versions, or public Gradle DSL.

## Error Handling

- An absent or blank environment variable remains absent in the Worker, preserving each tool's normal default/configuration behavior.
- Environment values are passed verbatim to the Worker. NuGet and the metadata/generator code remain responsible for validating paths and reporting failures through their existing diagnostics.
- The configured and cached NuGet CLI fallbacks inherit the same Worker environment.

## Validation

1. Add a failing regression test using a fake `nuget.cmd` that records an arbitrary environment variable after a real `processIsolation` generation.
2. Verify that the full environment map is wired to the task and reaches both the Worker and its NuGet child process while scratch-directory variables remain isolated.
3. Verify that ordinary non-isolated NuGet callers continue to inherit their process environment.
4. Run the focused `winrt-gradle-plugin` NuGet, task-wiring, and real Worker-boundary tests on Windows.
