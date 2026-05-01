# Plan

## Rules

- [ ] `.cswinrt/` is the implementation baseline for runtime, metadata, generator, authoring, projections, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Tests validate `.cswinrt` parity; do not derive runtime/generator rules from sample failures.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.

## Current Focus

- [ ] CCW/object marshaling parity 正在做: close remaining cswinrt `GetInterfaceTableEntries` differences for boxed values/delegates and keep WinUI smoke as validation only.
- [ ] Generator audit closure 正在做: keep non-authoring gaps explicit instead of claiming full parity; authoring writer output is now in scope.

## Completed Baseline

- [x] Modules exist with `.cswinrt` ownership mapping: `winrt-runtime`, `winrt-metadata`, `winrt-generator`, `winrt-projections`, `winrt-authoring`, `winrt-samples`.
- [x] Runtime baseline covers ABI primitives, activation, object identity, marshaling, delegates/events, collections, async, XAML helpers, configuration, and vtable fast paths.
- [x] Metadata baseline covers WinMD ingestion, deterministic model construction, type refs, ABI descriptors, semantic helpers, source/cache handling, diagnostics, and writer-handoff descriptors.
- [x] Generator app-consumption baseline covers declaration/member emission, activation/static/factory surfaces, mapped collections, async/reference/custom mappings, generic ABI inventory, event helpers, ABI invoke planning, support handoff APIs, and method-generic inputs.
- [x] Plugin baseline covers `winRt {}` DSL, SDK/NuGet inputs, Microsoft NuGet CLI restore/cache semantics, NuGet dependency closure, incremental generated-source wiring, identity JSON, and application resource/runtime staging.
- [x] WindowsAppSDK consumption uses direct `nugetPackage("Microsoft.WindowsAppSDK", version)` plus generic NuGet dependency closure, version info staging, lifted WinRT registrations, and framework/runtime assets.

## Recent Runtime Findings

- [x] Runtime-class parameters now follow cswinrt `AsValue(iid)` intent: generated ABI marshaling QIs projected runtime-class arguments to the default-interface IID before passing the raw COM pointer.
- [x] Delegate CCW public suffix matches cswinrt `GetInterfaceTableEntries`: delegate IID, `IStringable`, `IWeakReferenceSource`, `IMarshal`, `IAgileObject`, `IInspectable`, `IUnknown`.
- [x] Managed CCWs now expose hidden `IReferenceTrackerTarget` QI support for WinUI tracker probes without adding it to `IInspectable.GetIids`.
- [x] Delegate-as-object marshaling now exposes `IReference<TDelegate>` using cswinrt parameterized IID rules; `AddHandler(Object handler)` no longer fails on `DEA1E123-12EA-5CB3-B923-ABE74E426D9E`.
- [x] Boxed reference hosts now expose cswinrt-style `IPropertyValue` plus standard CCW suffix interfaces and hidden `IReferenceTrackerTarget`.
- [x] Delegate callback marshaling now aligns generated callback argument value kinds with cswinrt delegate signatures so runtime-class event args decode as `IInspectableReference` instead of `IUnknownReference`.
- [x] JVM WinUI bootstrap now stages WindowsAppSDK assets, creates an activation context from lifted registrations when available, and falls back to `MddBootstrapInitialize2` when needed.
- [x] `System.Object` projections now surface as Kotlin `Any?` with runtime object/delegate CCW marshaling instead of leaking `IInspectableReference` into public APIs.
- [x] WinUI app smoke sample uses generated projections for click/tapped handlers and WinAppSDK startup instead of sample-local delegate/runtime glue.
- [x] Generated WinRT methods now project as Kotlin lower-camel members (`start`, `activate`, `addHandler`, `onLaunched`) while mapped collection ABI members stay hidden behind Kotlin collection APIs.
- [x] Interface slot ordering now accounts for properties/events/methods by metadata row order before method-only wrappers, matching cswinrt ABI slot layout.
- [x] Event-source helper generation now keeps owner mappings separate from reusable source classes, so shared delegate helpers such as `RoutedEventHandler` remain available for `IButtonBase.Click`.
- [x] WinUI desktop sample entry now mirrors `.cswinrt/src/Samples/WinUIDesktopSample` and real WinUI template code: initialize the runtime, call `Application.start { WinUiDesktopApp() }`, and let XAML own the dispatcher loop instead of running a sample-local Win32 message loop.
- [x] Authoring activation factory runtime contract: Kotlin now has a `WinRtActivationFactory` CCW surface for cswinrt-style server factories that implement `IActivationFactory.ActivateInstance`.

## Generator Gaps

- [x] Generic signature/IID parity: metadata writer descriptors now mirror `.cswinrt` `write_guid_signature` fragments with IID-based interface/delegate signatures, recursive runtime-class default-interface signatures, struct field tokens, and flags enum `u4` handling; existing generator/runtime paths cover `IReference<T>`, async, collections, and generic delegate parameterized IID creation.
- [x] Mapped type declaration parity: planner now enters through metadata `getMappedType(ns,name)` and skips mapped declarations such as `IReference<T>`/`IPropertyValue`; Kotlin keeps only support declarations needed for mapped collection/custom-member slot metadata, while KMP value structs such as `Point`/`Vector3` remain generated because they are not .NET aliases.
- [x] Generic event-source parity: confirmed against `.cswinrt/src/cswinrt/code_writers.h` that only `Windows.Foundation/System.EventHandler<T>` share `EventHandlerEventSource`; other generic delegates keep their own source subclasses.
- [x] Support-file defaults: direct `KotlinProjectionGenerator` callers that provide a real projection context must set `emitSupportFiles=true`, so CLI/plugin-style generation cannot silently omit required support files.

## Authoring Plan

- [x] JVM authoring runtime foundation: composable CCW path, authoring activation registry, metadata type lookup, generated type-details lookup, compiler-plugin scanner split, and K2/IR extension entrypoint exist.
- [x] Generator authoring handoff plans exist for wrapper class, ABI class, custom QI, activation factory, module activation factory, exposed type details, and metadata type mapping.
- [x] Authoring source discovery now mirrors `.cswinrt/src/Authoring/WinRT.SourceGenerator/Generator.cs`: light-tree scanning only collects candidates, K2/IR diagnostics validate effective public accessibility, non-generic/non-inner/final class shape, generated projection sources are excluded, and Gradle wires the shared metadata index into the compiler plugin.
- [x] Authoring WinMD runtime-class shell emission: authored descriptors are owned by `winrt-metadata`, merge into the projection model before source generation, emit deterministic descriptor output, write loadable TypeDef/base TypeRef/InterfaceImpl rows plus Default/Overridable/Activatable/Version attributes, and Gradle now writes the project-named `.winmd` artifact.
- [ ] Authoring writer output 正在做: generated support now covers metadata mapping, wrapper shells, ABI class facades, custom QI, server factories, module activation registration, and CCW method tables for the current scalar/String/Guid/struct/System.Object/object-reference/delegate set; next is array/generic member marshaling coverage.
- [ ] Server/host activation frozen: implement `DllGetActivationFactory`/host-style packaging only after JVM in-proc authoring and WinMD emission are coherent.
- [ ] `mingwX64` authoring frozen: keep shared contracts native-viable, but defer native CCW/host implementation until JVM authoring stabilizes.

## Frozen

- [ ] `winrt-samples`: only validate completed runtime/generator/authoring slices; no sample-local runtime workarounds.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output.
- [ ] `mingwX64`: full native parity starts after JVM authoring and WinUI app-consumption validation are stable.

## Validation

- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache ... :winrt-runtime:jvmTest --tests WinRtDelegateBridgeTest.delegate_reference_supports_hidden_reference_tracker_target_query_interface --tests WinRtDelegateBridgeTest.delegate_inspectable_get_iids_returns_com_task_allocated_interfaces --tests WindowsRuntimePlatformTest.iid_catalog_matches_cswinrt_reference_values`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache -PkotlinWinRt.samples.windowsAppSdkVersion=1.8.260416003 ... :winrt-samples:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-configuration-cache :winrt-runtime:jvmTest --tests ValueBoxingTest.reference_projection_hosts_expose_cswinrt_ccw_suffix_interfaces --tests ValueBoxingTest.boxed_ccws_expose_reference_and_property_value_interfaces --tests WinRtDelegateBridgeTest.delegate_reference_supports_nullable_delegate_reference_query_interface`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache -PkotlinWinRt.samples.windowsAppSdkVersion=1.8.260416003 -DKOTLIN_WINRT_TRACE_CCW=true -Dkotlin.winrt.samples.runWinUiSmoke=true -Dkotlin.winrt.samples.autoNavigateWinUi=true ... :winrt-samples:run` reached the WinUI message loop and survived click validation after delegate callback marshaling fix.
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache -PkotlinWinRt.samples.windowsAppSdkVersion=1.8.260416003 -DKOTLIN_WINRT_TRACE_CCW=true -Dkotlin.winrt.samples.runWinUiSmoke=true -Dkotlin.winrt.samples.autoNavigateWinUi=true ... :winrt-samples:run` reached `button.click.add`, window activation, page creation, tapped handler registration, and content replacement with the process still alive before manual cleanup.
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-generator:test --tests KotlinProjectionGeneratorTest.generator_skips_cswinrt_mapped_declarations_without_kotlin_support_surfaces --tests KotlinProjectionGeneratorTest.generator_uses_runtime_backed_cswinrt_system_mapped_type_names --tests KotlinProjectionGeneratorTest.generator_substitutes_generic_collection_closure_arguments`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-metadata:test --tests WinRtMetadataModelTest.semantic_helpers_expose_cswinrt_writer_exactness_descriptors`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-runtime:jvmTest --tests ParameterizedInterfaceIdTest --tests WinRtCollectionInterfaceIdsTest`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :kotlin-winrt-compiler-plugin:test --tests KotlinWinRtAuthoringScannerCliTest --tests KotlinWinRtCompilerPluginTest :kotlin-winrt-gradle-plugin:test --tests KotlinWinRtPluginTest.plugin_wires_extension_inputs_to_generation_task --tests KotlinWinRtPluginTest.plugin_generates_sources_into_real_gradle_library_artifact`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :kotlin-winrt-gradle-plugin:test --tests KotlinWinRtAuthoringSourceScannerTest --tests KotlinWinRtPluginTest.plugin_generates_sources_into_real_gradle_library_artifact`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-metadata:test --tests WinRtAuthoringMetadataTest :kotlin-winrt-gradle-plugin:test --tests KotlinWinRtAuthoringSourceScannerTest --tests KotlinWinRtPluginTest.plugin_generates_sources_into_real_gradle_library_artifact`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-metadata:test --tests WinRtAuthoringMetadataTest`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :kotlin-winrt-gradle-plugin:test --tests KotlinWinRtPluginTest.plugin_wires_extension_inputs_to_generation_task --tests KotlinWinRtPluginTest.plugin_generates_sources_into_real_gradle_library_artifact`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-generator:test --tests KotlinProjectionGeneratorTest.generator_emits_cswinrt_writer_support_handoffs_when_enabled`
- [x] `./.agent_scripts/run_windows_gradle.sh --no-daemon --no-build-cache --no-configuration-cache ... :winrt-runtime:jvmTest --tests WinRtActivationFactorySupportTest`
