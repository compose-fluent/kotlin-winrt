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
- [x] Generator audit 23.1: projection surface filtering now aligns root include/exclude with `.cswinrt` dispatch while keeping cross-namespace dependencies such as `Microsoft.UI.Composition`, `Windows.UI.Core/Input/Text/ViewManagement`, and XAML attribute/type-name support available through metadata references.
- [x] Generator audit 23.2: `.cswinrt` mapped-type policy is locked for XAML/WinUI helpers and service-provider runtime mappings while Kotlin keeps WinRT value structs/`Windows.UI.Color` as generated KMP structs instead of .NET aliases.
- [x] Generator audit 23.3: `.cswinrt` `write_attribute` is mirrored for Kotlin annotation constraints by generating annotation constructor parameters from attribute constructors plus optional field/property values, and custom WinRT attributes now render through generated annotation types.
- [x] Generator audit 23.4: temporary `winrt-samples` XAML attribute include list is removed; WindowsAppSDK generation now passes the attribute/dependency include boundary and reaches broader generated WinUI compile blockers instead.
- [ ] Generator audit boundary 正在做: the full non-authoring `.cswinrt/src/cswinrt` generator scan is represented by completed 22.x plus 23.1-23.4. Remaining sample-run blockers are generated collection mutability signatures, base/default interface visibility, mapped `IClosable`/runtime-owned helpers, and interface type-handle metadata.

## Generator Audit Matrix

- [x] `.cswinrt/src/cswinrt/main.cpp` namespace/type dispatch: Kotlin has matching projection inventory and renderer dispatch for mapped skip, attribute, api contract, runtime class, delegate, enum, interface, struct, ABI companion/support output; filtered WinAppSDK dependency closure now keeps referenced cross-namespace dependencies even when their root namespace is excluded.
- [x] `.cswinrt` mapped type table: metadata mirrors helper-only/runtime-backed mapped entries, generator skips those mapped surfaces, and KMP value structs plus `Windows.UI.Color` stay generated from metadata rather than .NET aliases.
- [x] Type-name/signature writers: projected/nonprojected/ABI name modes, generic arguments, type signatures, GUID/IID signatures, unsigned Kotlin names, `Uri`, `TypeName`, `DateTime`, `TimeSpan`, `HResult`, references, arrays, async, collections, and delegate signatures are closed for the current JVM runtime boundary.
- [x] `write_attribute` and projected custom attributes: generated Kotlin annotations now carry constructor plus optional field/property data, built-in metadata attributes keep runtime annotation mappings, and custom WinRT attributes render through generated projection annotation classes.
- [x] `write_contract`, `write_enum`, `write_struct`, `write_abi_struct`: api contracts, enum ABI values, generated KMP structs, blittable/non-blittable native struct layout, pointer-slot fields, copy/read/dispose, and value-boxing registration are closed.
- [x] `write_interface`, `write_interface_members`, `write_static_abi_classes`, `write_abi_interface`: interface shells, member signatures, static ABI slot constants, fast-ABI folded slots, vtable call plans, generic RCW/property/delegate binding hooks, and required-interface forwarding are closed for current runtime calls.
- [x] `write_class`, `write_static_class`, `write_class_members`, `write_static_members`: runtime-class shells, activation/static/composable public surfaces, default/static interface caches, base class dispatch, object identity, protected/overridable rules, object method overrides, property/event/method filtering, and static events are closed for non-authoring classes.
- [x] `write_custom_mapped_type_members` and required mapped helpers: collection, bindable, `IClosable`, `INotifyDataErrorInfo`, iterator, map/vector/list forwarding, IDIC/static-ABI call-mode descriptors, and Kotlin runtime helper handoff are closed for projected runtime classes.
- [x] `write_delegate`, `write_abi_delegate`, `add_generic_type_references_in_type`, `write_generic_type_instantiations`: delegate projection, delegate ABI descriptors, generic fixed-point discovery, RCW/vtable/property/delegate initialization, event delegate dependencies, and fallback runtime bindings are closed.
- [x] Event helper generation: event source subclass descriptors, shared `EventHandler<T>` factories, generated event source table, runtime-class/interface/static event routing, and `WinRtEvent<T>` surface are closed.
- [x] Namespace additions/helper outputs: `.cswinrt/src/cswinrt/strings` namespace additions, support handoff files, base-type mapping, generic instantiation support, ABI delegate initialization, event helpers, and plugin identity handoff are modeled; C# addition files are not treated as Kotlin inputs.
- [ ] Authoring-gated writers: `write_wrapper_class`, `write_abi_class`, `write_custom_query_interface_impl`, `write_factory_class`, `write_module_activation_factory`, `write_winrt_exposed_type_class`, CCW/server activation/user-subclassing remain frozen until `winrt-authoring` and `mingwX64` prerequisites are ready.

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

- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-metadata:test --no-configuration-cache --no-daemon`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-projections:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh validateWinRtQueue16 --no-configuration-cache`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-samples:generateWinRtProjections -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001 --no-configuration-cache --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=2 -XX:-UseCompressedOops -XX:MaxDirectMemorySize=256m -Dfile.encoding=UTF-8'`
- [ ] `./.agent_scripts/run_windows_gradle.sh --no-configuration-cache :winrt-samples:run -Dkotlin.winrt.samples.runWinUiSmoke=true -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001` now clears the removed XAML attribute include list and is blocked by generated WinUI compile gaps: collection mutability signatures, base/default interface visibility, mapped `IClosable`/runtime-owned helpers, and interface type-handle metadata.
- [ ] For new generator work, run targeted generator tests and the affected projection/sample compile path before marking the slice complete.
