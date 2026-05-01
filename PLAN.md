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

## Generator Gaps

- [ ] Mapped type parity: cswinrt skips all `get_mapped_type(ns,name)` projected/ABI generation; Kotlin still has narrower skip rules and split metadata/generator mapped tables.
- [ ] Generic signature/IID parity: verify and close type-parameter signatures for `IReference<T>`, async, collections, and generic delegates against cswinrt `write_guid_signature`.
- [ ] Generic event-source parity: broaden non-`EventHandler<T>` generic delegate event-source generation if cswinrt emits helper/source tables for those shapes.
- [ ] Support-file defaults: ensure direct `KotlinProjectionGenerator` callers cannot silently omit required support files when CLI/plugin paths need them.

## Authoring Plan

- [x] JVM authoring runtime foundation: composable CCW path, authoring activation registry, metadata type lookup, generated type-details lookup, compiler-plugin scanner split, and K2/IR extension entrypoint exist.
- [x] Generator authoring handoff plans exist for wrapper class, ABI class, custom QI, activation factory, module activation factory, exposed type details, and metadata type mapping.
- [ ] Source discovery: replace light-tree-only candidate collection with K2 semantic validation/diagnostics for public authored class/interface/enum/delegate/struct shapes, matching `.cswinrt/src/Authoring/WinRT.SourceGenerator/Generator.cs`.
- [ ] Authoring WinMD emission: generate WinMD metadata from Kotlin authored declarations, then feed it back into projection generation before expanding samples.
- [ ] Authoring writer output: turn current plan files into actual generated wrapper/ABI/custom-QI/factory/module-activation support code.
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
