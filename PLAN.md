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
- [x] Generator audit 23.5: `.cswinrt` mapped collection helper parity now keeps mutable collection Kotlin surfaces mutable, suppresses raw collection ABI `Size`/read-only duplicates, and preserves ordinary WinRT members that merely share names such as `Remove`.
- [ ] Queue 24 正在做: close the remaining non-authoring `.cswinrt/src/cswinrt` generator parity gaps found in the full scan.
- [x] 24.1: `write_interface` parity now emits interface metadata companions for native projections, including empty exclusive interfaces, so generated interface wrappers have a `TYPE_HANDLE`/`wrap` owner matching `.cswinrt` interface helper output.
- [x] 24.2: `write_class` default-interface cache parity now uses protected open/override object-reference caches across runtime-class inheritance, matching `.cswinrt` objref ownership enough to clear private/hides/type override blockers.
- [x] 24.3: runtime-owned mapped helper filtering now keeps skipped helper-only interfaces out of class inheritance/cache surfaces, maps only `Windows.Foundation.IClosable.Close()` to Kotlin `close()`, and preserves `AutoCloseable` augmentation.
- [x] 24.4: `IReference<String>` collection ABI adapter now routes through runtime `WinRtReferenceValueAdapters.string` instead of emitting null adapter construction for string collection parameters.
- [ ] 24.5 正在做: complete remaining `write_class` parity for fast-ABI hierarchy dispatch and base constructor/object-reference routing.
- [ ] 24.6: dependency/wrapper closure: generate metadata-only or wrapper-only surfaces for referenced excluded/root-filtered types instead of leaving unresolved `Metadata`, `wrap`, or `TYPE_HANDLE`.
- [ ] 24.7: ABI marshaler closure: align non-string `IReference<T>`, object/interface/delegate returns, nullable ABI pointers, collection return values, and adapter construction with `.cswinrt` `abi_marshaler`.
- [ ] 24.8: generic/interface helper initialization: align derived generic interface impl classes, generic ABI helper init, event generic dependencies, and fallback helper registration.
- [ ] 24.9: mapped runtime-owned helper closure: finish `INotifyDataErrorInfo`, bindable helpers, and helper-only mapped metadata routing.
- [ ] 24.10: namespace additions/runtime support: finish non-authoring additions that Kotlin still needs from `.cswinrt/src/cswinrt/strings`, especially Foundation async helpers plus Storage/Streams adapters.
- [ ] 24.11: keep `write_wrapper_class`, `write_abi_class`, `write_factory_class`, `write_module_activation_factory`, CCW/server activation, and WinRT-exposed authoring output frozen under `winrt-authoring` until `mingwX64` prerequisites close.

## Generator Audit Matrix

- [x] `.cswinrt/src/cswinrt/main.cpp` namespace/type dispatch: Kotlin has matching projection inventory and renderer dispatch for mapped skip, attribute, api contract, runtime class, delegate, enum, interface, struct, ABI companion/support output; filtered WinAppSDK dependency closure now keeps referenced cross-namespace dependencies even when their root namespace is excluded.
- [x] `.cswinrt` mapped type table: metadata mirrors helper-only/runtime-backed mapped entries, generator skips those mapped surfaces, and KMP value structs plus `Windows.UI.Color` stay generated from metadata rather than .NET aliases.
- [x] Type-name/signature writers: projected/nonprojected/ABI name modes, generic arguments, type signatures, GUID/IID signatures, unsigned Kotlin names, `Uri`, `TypeName`, `DateTime`, `TimeSpan`, `HResult`, references, arrays, async, collections, and delegate signatures are closed for the current JVM runtime boundary.
- [x] `write_attribute` and projected custom attributes: generated Kotlin annotations now carry constructor plus optional field/property data, built-in metadata attributes keep runtime annotation mappings, and custom WinRT attributes render through generated projection annotation classes.
- [x] `write_contract`, `write_enum`, `write_struct`, `write_abi_struct`: api contracts, enum ABI values, generated KMP structs, blittable/non-blittable native struct layout, pointer-slot fields, copy/read/dispose, and value-boxing registration are closed.
- [ ] `write_interface`, `write_interface_members`, `write_static_abi_classes`, `write_abi_interface`: interface shells, calls, and native projection metadata exist; generic helper init and metadata-only wrapper surfaces still need Queue 24.
- [ ] `write_class`, `write_static_class`, `write_class_members`, `write_static_members`: class shells and inherited default-interface caches mostly exist; fast-ABI hierarchy dispatch, base constructor/object-reference routing, and wrapper closure still need Queue 24.
- [ ] `write_custom_mapped_type_members` and required mapped helpers: collections and `IClosable` are partly aligned; bindable/data-error helper routing and helper-only metadata still need Queue 24.
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
- [ ] `./.agent_scripts/run_windows_gradle.sh --no-configuration-cache :winrt-samples:run -Dkotlin.winrt.samples.runWinUiSmoke=true -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001` now clears the removed XAML attribute include list, mapped collection mutability/`Size`/`Remove`, interface native metadata companion, `IClosable`, string collection adapter, and inherited `_defaultInterface` blockers; it is still blocked by Queue 24.5-24.10.
- [ ] For new generator work, run targeted generator tests and the affected projection/sample compile path before marking the slice complete.
