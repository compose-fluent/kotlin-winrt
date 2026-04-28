# Plan

## Operating Rules

- [ ] Use `.cswinrt/` as the source of truth before changing runtime, metadata, generator, projections, samples, or authoring behavior.
- [ ] Keep current JVM work in dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-samples`; keep `winrt-authoring` native-gated until `mingwX64` is ready.
- [ ] Do not add duplicate primitive/type/category/projection branch tables; put shared decisions in the Kotlin owner matching the `.cswinrt` responsibility.
- [ ] Keep `winrt-projections` generated or narrowly hand-authored glue only; do not grow handwritten standard WinRT API coverage.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Update this file with every scope/status change.

## Current Focus

- [ ] Queue 23 正在做: close the remaining `.cswinrt/src/cswinrt/main.cpp` generation-dispatch parity gaps that block broad WindowsAppSDK/WinUI generation without sample-side include lists.
- [ ] Generator audit 23.1 正在做: align projection surface filtering with `.cswinrt` dispatch so cross-namespace dependencies such as `Microsoft.UI.Composition`, `Windows.UI.Core/Input/Text/ViewManagement`, and XAML attribute/type-name support are pulled by metadata references.
- [ ] Generator audit 23.2: align `.cswinrt` `get_mapped_type` skip policy for XAML/WinUI helper-only mapped types (`*Helper`, `I*Helper*`, `IXamlServiceProvider`) while preserving the Kotlin-specific decision to generate KMP metadata structs for WinRT value structs instead of .NET value aliases.
- [ ] Generator audit 23.3: align `.cswinrt` `write_attribute` for Kotlin annotation constraints so generated attribute classes carry usable constructor/property data and attribute dependencies do not need manual sample includes.
- [ ] Generator audit 23.4: remove the temporary `winrt-samples` XAML attribute include list once 23.1-23.3 are closed, then validate the WindowsAppSDK WinUI generation/run command below.
- [ ] Generator audit boundary: the full non-authoring `.cswinrt/src/cswinrt` generator scan is represented by completed 22.x plus 23.1-23.4. Do not add new top-level generator audit items from sample failures; classify them under 23.1 dependency closure, 23.2 mapped skip policy, 23.3 attribute shape, or authoring-gated 22.14.

## Completed Summary

- [x] Module layout exists: `winrt-runtime`, `winrt-metadata`, `winrt-generator`, `winrt-projections`, `winrt-authoring`, and `winrt-samples`.
- [x] Runtime baseline is closed through Runtime 1.20: ABI primitives, activation, object identity, marshaling, delegates/events, collections, async, XAML/system helpers, configuration, vtable fast paths, and bounded Kotlin-specific deviations.
- [x] Metadata baseline is closed through Metadata Full-Parity 4.52: real WinMD ingestion, deterministic model, type refs, ABI descriptors, semantic helpers, source/cache handling, diagnostics, fixture validation, and `.cswinrt/src/cswinrt` writer-handoff descriptors.
- [x] Generator declaration/member baseline is closed through Queues 3-11: declaration planning/shells, member/property/event/method emission, activation/static/factory surfaces, mapped collections, async/reference/custom mappings, generic ABI inventory, event helpers, ABI invoke planning, and support handoff APIs.
- [x] Generator structure is closed through Queues 18-22: monolithic generator files were split by `.cswinrt` writer responsibility, mapped type decisions were centralized, ABI/runtime-call parity was audited through 22.20, and authoring/server activation remains explicitly deferred in 22.14.
- [x] Generator KMP policy is settled for current JVM work: `Uri` maps to common `WinRtUri`, XAML `TypeName` maps through `KClass<*>`, events expose `WinRtEvent<T>`, WinRT value structs are generated from metadata instead of projected as .NET-style runtime aliases, and generated code stays under `io.github.kitectlab.winrt.projections`.
- [x] Plugin baseline is closed through Plugin 4.10 and lifecycle 18.4: `winRt {}` DSL, SDK/NuGet metadata inputs, Microsoft NuGet CLI restore/cache semantics, cached NuGet CLI fallback, incremental generated-source wiring, library identity JSON, application resource/runtime staging, WindowsAppSDK split-package filters, and application distributions.
- [x] `winrt-projections` consumes plugin-generated output through the included plugin build; generated Kotlin is compiled into library artifacts, while identity JSON is the separate dependency metadata used by applications.
- [x] Samples are closed through Sample 10: JSON API compat shape, plugin graph validation, NetProjectionSample-style `SimpleMath`, real generated WinUI smoke surface, WindowsAppSDK package/resource staging, and opt-in WinUI execution are present without using samples as generator design input.
- [x] Validation wiring is present: targeted module tests plus root `validateWinRtGenerator`, `validateWinRtPluginGraph`, `validateWinRtProjectionCompile`, `validateWinRtSampleSmoke`, and ordered `validateWinRtQueue16`.

## Frozen Until Prerequisites Close

- [ ] `winrt-authoring`: keep `.cswinrt/src/Authoring`, `write_wrapper_class`, `write_abi_class`, `write_custom_query_interface_impl`, `write_factory_class`, `write_module_activation_factory`, `write_winrt_exposed_type_class`, AuthoringDemo, BgTask, and embedded authoring samples frozen until `mingwX64` ABI/runtime boundaries are ready.
- [ ] `mingwX64`: keep shared contracts viable during JVM work, but full native parity planning starts only after the JVM generator/projection/plugin path is coherent.
- [ ] `winrt-samples`: do not expand samples again unless they validate already-completed runtime/metadata/generator/plugin behavior; authoring samples remain blocked by `winrt-authoring`.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output for validation.

## Validation

- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-metadata:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-projections:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh validateWinRtQueue16 --no-configuration-cache`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-samples:generateWinRtProjections -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001 --no-configuration-cache --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=2 -XX:-UseCompressedOops -XX:MaxDirectMemorySize=256m -Dfile.encoding=UTF-8'`
- [ ] `./.agent_scripts/run_windows_gradle.sh --no-configuration-cache :winrt-samples:run -Dkotlin.winrt.samples.runWinUiSmoke=true -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001` is blocked by Queue 23 generated WinUI dependency-closure gaps.
- [ ] For new generator work, run targeted generator tests and the affected projection/sample compile path before marking the slice complete.
