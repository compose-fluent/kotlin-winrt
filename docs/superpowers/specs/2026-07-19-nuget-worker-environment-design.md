# NuGet Worker Environment Propagation Design

## Goal

Preserve the existing Microsoft `nuget install` dependency-resolution path while ensuring that projection generation launched through Gradle `processIsolation` writes NuGet's global package cache to the caller's configured `NUGET_PACKAGES` directory.

## Root Cause

`GenerateWinRTProjectionsTask` performs metadata loading and NuGet restore inside a process-isolated Gradle worker. The owning Gradle task sees `NUGET_PACKAGES`, but the isolated worker does not inherit that variable. `NuGetCliSupport` currently forwards only scratch-directory variables, so NuGet falls back to `%USERPROFILE%\.nuget\packages` inside the worker even when the build configured another global package root.

The existing `nugetGlobalPackagesRoots` model remains useful for locating already restored packages, but it does not configure the write destination of a NuGet CLI process. This read/write mismatch is the defect.

## Design

1. Keep `nuget install` as the only restore command used by the current projection-generation path. NuGet remains responsible for source configuration, authentication, dependency closure resolution, version selection, download, and global-cache population.
2. Model the Gradle task process's `NUGET_PACKAGES` value as an optional task input. Resolve it through Gradle's environment-variable provider so configuration-cache tracking remains explicit.
3. Pass that optional value through `GenerateWinRTProjectionsWorkParameters` rather than expecting `processIsolation` to inherit it.
4. Extend `NuGetCliSupport` with a narrow optional global-packages environment override. Immediately before starting either the configured or cached NuGet executable, set `NUGET_PACKAGES` on the child `ProcessBuilder` when the modeled value is nonblank.
5. Continue setting `TEMP`, `TMP`, `TMPDIR`, and `NUGET_SCRATCH` to the task scratch directory. Do not replace `NUGET_PACKAGES` with a task-local cache.
6. Leave non-isolated NuGet callers behaviorally unchanged; the new override is optional and defaults to the inherited process environment.

## Non-Goals

- Do not introduce `packages.config`, MSBuild, `dotnet restore`, or a Kotlin implementation of NuGet dependency-closure solving.
- Do not remove the task-local `-OutputDirectory`; projection metadata continues to consume the isolated legacy install directory while NuGet also populates its global cache.
- Do not reinterpret `nugetGlobalPackagesRoots` as a writable-cache priority list.
- Do not change WinRT metadata selection, generator output, package versions, or public Gradle DSL.

## Error Handling

- An absent or blank `NUGET_PACKAGES` value leaves NuGet's normal default-directory and NuGet.Config behavior intact.
- A configured path is passed verbatim to NuGet. NuGet remains responsible for validating accessibility and reporting restore failures through the existing `NuGetCliSupport` diagnostic.
- The cached CLI fallback receives the same environment override as the configured executable.

## Validation

1. Add a failing regression test using a fake `nuget.cmd` that records its environment, with the ambient worker environment intentionally not relied upon.
2. Verify that an explicit global-packages override reaches the fake NuGet child process while scratch-directory variables remain isolated.
3. Verify that no override preserves the existing inherited-environment behavior.
4. Verify Gradle task wiring carries the environment-provider value into projection worker parameters.
5. Run the focused `winrt-gradle-plugin` NuGet and task-wiring tests on Windows.
