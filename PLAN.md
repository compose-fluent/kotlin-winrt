# Plan

## Operating Rules

- [x] Use `.cswinrt/` as the primary behavioral and architectural reference.
- [x] Preserve dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [x] Keep runtime mechanics out of generated API-shape policy and keep sample code validation-only.
- [x] Treat a CallSite as WinMD semantic metadata only: generated declarations expose typed `TODO()` stubs and structured facts, while the IR plugin alone plans marshaling and emits platform code from those facts, exact closed IR types, and declaration-owned ABI facts. Module support must not expand per-closed-type codec implementations or metadata-holder objects.
- [x] Do not place recursive recipes, ABI carriers, codec/callable names, result factories, transport choices, or other implementation plans in CallSite metadata through Base64, opaque payloads, or sidecars; do not reconstruct them through projected-type or call-family enumeration either.
- [x] Keep JVM and `mingwX64` contracts equivalent, with platform mechanics isolated in target source sets.
- [x] Work on Windows with `KOTLIN_WINRT_WINDOWS_SDK_ROOT=D:\Windows Kits\10`; prefer `gradlew.bat` and targeted tests before aggregate gates.
- [x] Keep Gradle configuration-cache and build-cache compatibility as required behavior. Use `--no-build-cache` only to isolate cache corruption or concurrent-output diagnostics.
- [x] Preserve unrelated working-tree changes and the local `.gradle-review/`, `.worktrees/`, and `docs/` directories.
- [x] Commit coherent optimization slices only after explicit user authorization.

## Reference Mapping

- [x] `.cswinrt/src/WinRT.Runtime` maps to `winrt-runtime`: ABI primitives, HRESULT/HSTRING ownership, COM lifetime and identity, activation, marshaling, delegates, collections, async, and authoring runtime support.
- [x] `.cswinrt/src/cswinrt` maps to `winrt-metadata`, `winrt-generator`, and `winrt-compiler-plugin`: WinMD normalization, declaration/member planning, recursive ABI recipes, generated support, and static IR lowering.
- [x] `.cswinrt/src/Projections` maps to `winrt-projections`; checked-in and prebuilt output must be deterministic products of the generator.
- [x] `.cswinrt/src/Authoring` maps to `winrt-authoring`: authored metadata, CCW/activation boundaries, hosting, and lifetime contracts.
- [x] `.cswinrt/src/Samples` maps to `winrt-samples`; tests live with their owning Kotlin modules rather than in a separate top-level test module.
- [x] Active performance reference points are `.cswinrt/src/cswinrt/code_writers.h` marshaler planning/direct ABI calls and `.cswinrt/src/cswinrt/helpers.h` centralized mapped-type decisions.

## Completed Baseline

- [x] Runtime baseline: ABI primitives, initialization/platform calls, RCW/CCW identity, activation, parameterized IID/signatures, delegates/events, collections, async, restricted error info, weak/agile references, WinUI hooks, and JVM/Native direct vtable calls are implemented in `winrt-runtime`.
- [x] Metadata baseline: native WinMD loading, SDK/file/directory/NuGet inputs, deterministic normalization, generic substitution, default/implemented interfaces, factories, methods/properties/events, parameter directions, layouts, custom attributes, and fail-closed diagnostics are implemented in `winrt-metadata`.
- [x] Generator/compiler baseline: declaration and member planning, activation/static/composable surfaces, closed generic interfaces, source additions, owner-scoped support, authoring handoff, deterministic output, and JVM/Native compile gates are implemented.
- [x] Projection baseline: Windows SDK, Windows.UI.Xaml, Windows App SDK, and WebView2 artifacts use dependency identity and prebuilt/local ownership rules without duplicate projected FQNs.
- [x] Authoring baseline: TypeDetails, authored WinMD/descriptors/manifests, CCW factories, activation exports, inherited/overridable interfaces, native component/consumer fixtures, arrays, delegates, collections, async, and HRESULT propagation are implemented.
- [x] Packaging/sample baseline: appx/msix staging, PRI/MRT/resources, dependency payloads, signing/test-install hooks, JVM/Native WinUI hosts, Controls Sample, authored-control validation, and the programmatic WebView2 sample are implemented.
- [x] Windows COM interop helpers and source additions are implemented and validated; the stale historical commit-handoff task is removed, and coherent work is committed only after explicit user authorization.

## Benchmark Optimizations Completed

- [x] Benchmark parity: `winrt-benchmarks` runs the same six `Windows.Data.Json` scenarios for Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT with common warmup/measurement rules, checksum validation, and JSON/Markdown reports.
- [x] Optimizations 01-18: direct fixed-shape vtable dispatch, reusable scalar/struct/HSTRING frames, Native direct typed calls, bulk clearing, stable reference caches, and direct HSTRING header access are shared by runtime-owned and compiler-expanded paths without changing the fixed runtime-owned extraction set.

## Current Focus

- [ ] Performance optimization is in progress: compare Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT from the same benchmark run; prioritize hot paths shared by runtime-owned and module/call-site lowering; keep artifact-size work and final WinUI validation frozen until performance acceptance closes. <!-- 正在做 -->

## Deferred

- [ ] Generated projection size reduction: reduce repeated generated class output in prebuilt Windows SDK and Windows App SDK artifacts without changing coordinates, API compatibility, hot paths, or multi-module ownership. Keep frozen until performance acceptance completes.
- [ ] Final WinUI validation: after performance and artifact-size acceptance, rerun the Controls Sample on JVM and `mingwX64` without moving missing behavior into samples.
- [x] Broad new runtime, authoring, projection, packaging, and sample features remain frozen while the benchmark/call-site queue is active; regression fixes stay in the earliest owning module.

## Validation Gates

- [x] Validate CallSite contract, compiler-plugin integration, generator composition, and absence of serialized implementation payloads for every lowering change.
- [x] Validate JVM and `mingwX64` runtime tests, generated projection compilation, binary-marker checks, and runtime/projection lowering gates before accepting a performance slice.
- [x] Validate plugin-free published runtime consumption whenever runtime-owned lowering or published inline bodies change.
- [x] Require checksum-matched, serialized A/B benchmark evidence before claiming a performance gain.
- [x] Run `git diff --check` and inspect the exact staged scope before every commit.
