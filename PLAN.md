# Plan

## Operating Rules

- [ ] `.cswinrt/` is the source of truth for runtime, metadata, generator, projections, authoring, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Do not design from tests or samples; tests validate the `.cswinrt`-mapped contract.
- [ ] Do not duplicate semantic branch tables; put shared decisions in the owner matching `.cswinrt`.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Update this file with every scope/status change.

## Current Focus

- [ ] Authoring 1 正在做: close the JVM composable CCW/runtime-class authoring path needed by cswinrt-style WinUI `Application.Start((e) => new App())`.
- [ ] Sample 11 正在做: validate WinUI through authored `WinUiDesktopApp` plus `IApplicationFactory.CreateInstance`; compile is green, visual run/fail-fast validation is pending.
- [ ] Queue 24.14: generated authoring/server writers are now the active post-runtime authoring target; `mingwX64` authoring stays frozen until JVM contracts are stable.

## Completed Baseline

- [x] Module layout exists: `winrt-runtime`, `winrt-metadata`, `winrt-generator`, `winrt-projections`, `winrt-authoring`, and `winrt-samples`.
- [x] Runtime baseline is closed through ABI primitives, activation, object identity, marshaling, delegates/events, collections, async, XAML/system helpers, configuration, and vtable fast paths.
- [x] Metadata baseline is closed through real WinMD ingestion, deterministic model construction, type refs, ABI descriptors, semantic helpers, source/cache handling, diagnostics, and writer-handoff descriptors.
- [x] Generator app-consumption baseline is closed through declaration/member emission, activation/static/factory surfaces, mapped collections, async/reference/custom mappings, generic ABI inventory, event helpers, ABI invoke planning, support handoff APIs, and method-generic inputs.
- [x] Generator structure is split by `.cswinrt/src/cswinrt` writer responsibility; mapped type decisions are centralized.
- [x] Plugin baseline is closed through `winRt {}` DSL, SDK/NuGet inputs, Microsoft NuGet CLI restore/cache semantics, NuGet dependency closure, incremental generated-source wiring, identity JSON, and application resource/runtime staging.
- [x] WindowsAppSDK consumption uses direct `nugetPackage("Microsoft.WindowsAppSDK", version)` plus generic NuGet dependency closure.

## Authoring Plan

- [ ] A1 Runtime aggregation parity 正在做: mirror `.cswinrt/src/WinRT.Runtime/ComWrappersSupport.net5.cs` `ComWrappersHelper.Init` for JVM. Kotlin owner: `winrt-runtime` `ComWrappersSupport`/`WinRtInspectableComObject`. Current state: outer CCW owns authored interfaces, native composable factory receives the base pointer, inner/result pointers are retained, outer QI fallback registers forwarded pointers, and aggregation cleanup distinguishes tracker objects. Remaining: validate against real WinUI run.
- [x] A2 Reference tracker parity: mirrors `.cswinrt` `IReferenceTracker` handling in `ComWrappersHelper.Init` for the JVM authoring boundary. Kotlin owner: `winrt-runtime` `ComObjectReference`/reference tracker support. Composition probes the inner for `IReferenceTracker`, immediately releases the probed tracker pointer like cswinrt aggregation, records aggregated tracker objects, and suppresses normal inner/instance release for that case.
- [x] A3 Object registration and identity: mirrors `.cswinrt` `RegisterObjectForInterface`, `FindObject<T>`, and RCW cache behavior for the JVM authoring boundary. Kotlin owner: `winrt-runtime` object registry. Authored Kotlin objects are registered through their own CCW pointers plus external inner/result/fallback aliases, aggregation aliases override the native inner registration as cswinrt does, and RCW cache keys use COM `IUnknown` identity across interface pointers.
- [x] A4 Authoring API surface: mirrors cswinrt's modern `[WinRTExposedType]` metadata intent without copying C# attributes. Kotlin owner: `winrt-authoring`. Authored runtime classes now register through authoring-owned type/interface/method definitions, with runtime CCW conversion kept inside `winrt-authoring`; the WinUI sample uses this authored model for its composable `Application` override surface.
- [x] A5 Generated authoring metadata mapping: mirrors `.cswinrt/src/cswinrt/main.cpp` `AuthoringMetadataTypeInitializer` and `RegisterAuthoringMetadataTypeLookup`. Kotlin owner: `winrt-generator` plus `winrt-runtime`. Component support generation now emits `AuthoringMetadataTypeMappingHelper.kt`, and runtime exposes authoring metadata mapping registration/lookup without sample-side hand registration.
- [x] A6 Wrapper class writer handoff: mirrors `.cswinrt/src/cswinrt/code_writers.h` `write_wrapper_class` responsibility by generating `WinRTAuthoringWrapperPlan.kt`. Kotlin owner: `winrt-generator`. The plan centralizes projected type, metadata carrier, default interface, implemented interfaces, factory members, and composable base inputs for the later ABI/custom-QI writers.
- [x] A7 ABI class writer handoff: mirrors `write_abi_class` for authored runtime classes by generating `WinRTAuthoringAbiClassPlan.kt`. Kotlin owner: `winrt-generator`. The plan captures `GetAbi`, `FromAbi`, `FromManaged`, marshaler creation, array, dispose, default-interface, and marshaler-family decisions for later runtime-backed emission.
- [x] A8 Custom QI writer handoff: mirrors `write_custom_query_interface_impl` by generating `WinRTAuthoringCustomQueryInterfacePlan.kt`. Kotlin owner: `winrt-generator`. The plan captures overridable interface IIDs, base fallback, `IInspectable`/`IWeakReferenceSource` NotHandled policy, and native-object forwarding target for later runtime-backed emission.
- [x] A8.1 Support handoff rendering: generated support files that declare handoff tables, registries, event helpers, ABI/type-shape plans, namespace additions, and authoring mapping now use KotlinPoet for file/class/function/property structure instead of hand-written Kotlin templates.
- [x] A8.2 Generator KotlinPoet audit: full-file generation now routes through KotlinPoet; the remaining generator `buildString` usages are planner signature/cache keys, and expression-body fragments remain `CodeBlock` based.
- [x] A9.1 Activation factory writer handoff: mirrors `write_factory_class` inputs by generating `WinRTAuthoringActivationFactoryPlan.kt`. Kotlin owner: `winrt-generator`. The plan captures server factory type names, unconditional `IActivationFactory` participation, activatable/static/composable factory interface names, expanded factory member references, `Make` marshaler intent, default-constructor-gated `ActivateInstance` behavior, and class-constructor handoff.
- [x] A9.2 Runtime authoring activation registry: mirrors the `ServerActivationFactory.Make()` handoff by letting authored factories register runtime-class-name lookups before `RoGetActivationFactory`. Kotlin owner: `winrt-runtime`/`winrt-authoring`. Registered factories expose `IActivationFactory`, `ActivateInstance` creates and marshals Kotlin authored instances when a default constructor is supplied, and otherwise keeps the cswinrt not-implemented behavior.
- [x] A9.3 Factory interface forwarding contract: mirrors `write_factory_class_inheritance`/`write_factory_class_members` by allowing authored activation factories to expose additional factory interfaces whose members run on the server factory object. Kotlin owner: `winrt-authoring`; generator hookup remains in A9.
- [x] A9.4 Non-default activation behavior: mirrors `write_factory_class` by keeping `IActivationFactory` available for authored factory-only classes while `ActivateInstance` reports `E_NOTIMPL` when no default constructor is supplied. Kotlin owner: `winrt-authoring`.
- [ ] A9 Activation factory writer: mirror `write_factory_class`. Kotlin owner: `winrt-generator`/`winrt-authoring`. Required contract: activatable authored classes produce factories that create Kotlin instances and marshal them through CCW; static/factory interfaces forward to authored companion/factory implementations.
- [x] A10.1 JVM module activation lookup: mirrors `write_module_activation_factory` switch semantics for in-proc JVM authoring. Kotlin owner: `winrt-authoring`. `WinRtAuthoring.getActivationFactory` returns an owned authored activation factory pointer for registered runtime class names and null for misses, without falling through to OS activation.
- [x] A10.2 Module activation partial fallback: mirrors `GetActivationFactoryPartial` by allowing authoring modules to register fallback activation-factory lookups for dependency/partial factory chains. Kotlin owner: `winrt-authoring`; plugin packaging will wire dependency modules later.
- [x] A10.3 Module activation writer handoff: mirrors `write_module_activation_factory` dispatch inputs by generating `WinRTAuthoringModuleActivationFactoryPlan.kt`. Kotlin owner: `winrt-generator`. The plan maps runtime class names to server factory entries and exposes lambda-based factory/fallback dispatch without forcing `winrt-projections` to depend on `winrt-authoring`.
- [ ] A10 Module activation factory: mirror `write_module_activation_factory`. Kotlin owner: `winrt-authoring` plus plugin packaging. Required contract: runtime class name lookup returns activation factories for authored classes, with partial/dependency factory handoff planned before server packaging.
- [x] A10.4 Composable factory invocation owner: moves WinUI composable `CreateInstance` ABI invocation out of samples and into `winrt-authoring` as `createComposableObjectWithFactory`, keeping samples focused on selecting the projected factory/slot rather than owning runtime vtable mechanics.
- [ ] A11 Authoring WinMD generation: mirror `.cswinrt/src/Authoring/WinRT.SourceGenerator/Generator.cs` flow. Kotlin owner: `winrt-authoring`/plugin. Required contract: scan authored public Kotlin declarations, produce WinMD metadata, feed generated metadata back into projection generation, and keep diagnostics separate from runtime behavior.
- [ ] A12 Authoring validation: mirror `.cswinrt/src/Samples/WinUIDesktopSample` first, then minimal authored component activation. Kotlin owner: `winrt-samples`. Required contract: WinUI app starts as authored `Application` subclass equivalent, `OnLaunched` is invoked through `IApplicationOverrides`, and no sample-local workaround owns runtime behavior.
- [ ] A13 Host/server activation frozen: mirror `.cswinrt/src/Authoring/WinRT.Host` only after JVM in-proc authoring is coherent. Kotlin owner: future `winrt-authoring` host/plugin packaging. Required contract: runtimeconfig/hostfxr-like loading, class-to-assembly mapping, and `DllGetActivationFactory` are planned separately; not required for current WinUI app-consumption sample.
- [ ] A14 `mingwX64` authoring frozen: keep shared contracts native-viable, but implement native CCW/host support only after JVM authoring contracts and validation are stable.

## Generator Audit Summary

- [x] `.cswinrt/src/cswinrt/main.cpp` dispatch, mapped type table, type/signature writers, attributes, contracts, enums, structs, interfaces, classes, mapped helpers, delegates, generics, event helpers, namespace additions, and method-generic inputs are closed for the current JVM app-consumption boundary.
- [ ] Authoring-gated writers remain: `write_wrapper_class`, `write_abi_class`, `write_custom_query_interface_impl`, `write_factory_class`, `write_module_activation_factory`, `write_winrt_exposed_type_class`, CCW/server activation, and user-subclassing.

## Frozen

- [ ] `mingwX64`: keep shared APIs viable, but full native runtime/authoring parity starts after JVM authoring is coherent.
- [ ] `winrt-samples`: do not expand beyond validating completed runtime/generator/authoring slices.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output.

## Validation

- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-metadata:test --no-configuration-cache --no-daemon`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-projections:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache :winrt-samples:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache :winrt-runtime:jvmTest :winrt-authoring:test --tests io.github.kitectlab.winrt.authoring.WinRtAuthoringTest`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache :winrt-runtime:jvmTest :winrt-authoring:test --tests io.github.kitectlab.winrt.runtime.ComWrappersSupportTest --tests io.github.kitectlab.winrt.authoring.WinRtAuthoringTest`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache :winrt-samples:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache -Dorg.gradle.jvmargs='-Xmx2048m -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8' -Dorg.gradle.workers.max=2 :winrt-runtime:jvmTest :winrt-authoring:test --tests io.github.kitectlab.winrt.runtime.ComWrappersSupportTest --tests io.github.kitectlab.winrt.authoring.WinRtAuthoringTest`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache -Dorg.gradle.jvmargs='-Xmx2048m -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8' -Dorg.gradle.workers.max=2 :winrt-samples:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh validateWinRtQueue16 --no-configuration-cache`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache -Dorg.gradle.jvmargs='-Xmx2048m -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m -Dfile.encoding=UTF-8' -Dorg.gradle.workers.max=2 :winrt-generator:test --tests io.github.kitectlab.winrt.projections.generator.KotlinProjectionGeneratorTest.generator_emits_cswinrt_writer_support_handoffs_when_enabled`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache -Dorg.gradle.jvmargs='-Xmx4096m -XX:MaxDirectMemorySize=1024m -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m -Dfile.encoding=UTF-8' -Dorg.gradle.workers.max=1 :winrt-generator:compileKotlin`
- [ ] Current generator validation attempt: `:winrt-generator:test --tests ...generator_emits_cswinrt_writer_support_handoffs_when_enabled` compiles main/test sources after the KotlinPoet migration, but the Windows test JVM ended with direct-memory/JVM-load failure during repeated runs.
- [ ] Next authoring validation: targeted runtime/authoring tests, then `winrt-samples:compileKotlin`, then opt-in WinUI visual run.
