# Windows App SDK 2.2.0 Default Baseline Design

## Goal

Make `Microsoft.WindowsAppSDK` `2.2.0` the active default across Kotlin/WinRT projection builds, samples, documentation, CI, and projection publication inputs without changing the existing override mechanisms or projection architecture.

## Reference Boundary

This is a version-baseline update for the `.cswinrt/src/Projections/WinAppSDK`-mapped Kotlin projection and validation surfaces. It does not introduce a new projection rule, runtime abstraction, metadata heuristic, or handwritten projection. Existing metadata-driven generation and the current `winrt-runtime` / `winrt-metadata` / generator ownership boundaries remain unchanged.

## Active Baseline

- `kotlinWinRT.projections.windowsAppSdkVersion` defaults to `2.2.0`.
- `kotlinWinRT.samples.windowsAppSdkVersion` defaults to `2.2.0`.
- Root publication/consumer validation derives the Windows App SDK projection artifact version from `2.2.0` when no override is supplied.
- CI restore inputs, NuGet cache keys, README examples, and the manual projection publication workflow use `2.2.0` as their current baseline.
- Gradle properties and workflow inputs continue to override the default exactly as before.

## Compatibility Constraints

- Keep the Windows SDK projection baseline at `10.0.26100.0`.
- Keep the WebView2 projection baseline at `1.0.3719.77`; `Microsoft.WindowsAppSDK.WinUI` `2.2.1`, selected by `Microsoft.WindowsAppSDK` `2.2.0`, still depends on `Microsoft.Web.WebView2` `1.0.3719.77`.
- Do not rewrite historical design/implementation records that accurately state validation previously performed with `2.1.3`.
- Update test fixtures that model the current default; retain an older version only where a test explicitly validates old-version compatibility.

## Validation

1. Search active configuration, sample, README, test, and workflow files for stale `2.1.3` defaults.
2. Resolve and compile the Windows App SDK prebuilt projection with the repository defaults on Windows.
3. Run both JVM and `mingwX64` projection compilation when the local toolchain permits it.
4. Confirm unrelated untracked workspace directories remain untouched.

