# Plan

## Operating Rules

- [ ] Use `.cswinrt/` as the source of truth before changing runtime, metadata, generator, projections, samples, or authoring behavior.
- [ ] Keep current JVM work in dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-samples`; keep `winrt-authoring` in the native-gated deferred plan until `mingwX64` is ready.
- [ ] Do not add duplicate primitive/type/category/projection branch tables; put shared decisions in the Kotlin owner matching the `.cswinrt` responsibility.
- [ ] Keep `winrt-projections` generated or narrowly hand-authored glue only; do not grow handwritten standard WinRT API coverage.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Update this file with every scope/status change.

## Current State

- [x] Module layout exists: `winrt-runtime`, `winrt-metadata`, `winrt-generator`, `winrt-projections`, `winrt-authoring`, and `winrt-samples`.
- [x] `winrt-runtime` baseline is closed through Runtime 1.20: ABI primitives, activation, object identity, marshaling, delegates/events, collections, async, XAML/system helpers, configuration, and bounded Kotlin-specific deviations.
- [x] Runtime follow-up: vtable invocation now includes the `Double, Double, out Double` ABI shape needed by `.cswinrt/src/Samples/NetProjectionSample` `SimpleMath.add/subtract/multiply/divide`.
- [x] `winrt-metadata` is complete for the current `.cswinrt/src/cswinrt` audit: WinMD ingestion, normalized model, semantic helpers, source/cache handling, descriptor handoff, and final writer-handoff audit through Metadata Full-Parity 4.52.
- [ ] `winrt-generator` remains in `.cswinrt/src/cswinrt` parity follow-through: ABI 8.4-8.8 are closed; the latest audit shows remaining gaps in factory/composable constructors, interface proxy members, class member merging, mapped helper members, fast-ABI class aggregation, and WinUI dependency closure.
- [x] `winrt-projections` compiles plugin-generated Foundation support through the included plugin build.
- [x] `kotlin-winrt` Gradle plugin baseline exists for SDK/NuGet generation inputs, generated-source wiring, NuGet CLI fallback, and `winRt {}` library/application identity handling.
- [x] `winrt-samples` is intentionally minimal and now closed through Sample 10; do not expand samples again until authoring-owned contracts exist.
- [ ] `winrt-authoring` remains frozen for now; AuthoringDemo/BgTask samples are native-gated and blocked until `mingwX64` runtime/generator contracts are coherent enough to plan `.cswinrt/src/Authoring`.

## Active Queue

- [x] Queue 11: resume generator breadth from `.cswinrt/src/cswinrt` using the completed metadata descriptors instead of generator-local classification tables.
- [x] Generator 11.1: planner now carries metadata signature, marshaler, vtable, and event invoke descriptors into method/event ABI emission.
- [x] Generator 11.2: planner/metadata companions now carry declaration, object-reference, factory, GUID, and interface-member descriptors.
- [x] Generator 11.3: planner/metadata companions now carry custom mapped, generic ABI, required-interface, and module activation descriptors.
- [x] Generator 11.4: Windows SDK CLI generation validated for the current `Windows.Data.Json` slice without changing checked-in projections.
- [x] Generator 11.5: close Foundation-level object/array signature lowering, `System.Object` as WinRT reference projection, duplicate collection superinterface merging, and current `Windows.Foundation` SDK CLI generation.
- [x] Generator 11.6: close mapped collection writers: concrete generic substitution plus `IIterable`/`IIterator`/`IKeyValuePair`/map/vector member lowering.
- [x] Generator 11.7: close generic ABI registry: collect required delegates for collections, async, references, events, structs, and arrays.
- [x] Generator 11.8: close generic instantiation output: concrete generic substitution, RCW helper hooks, vtable hooks, and recursive initialization.
- [x] Generator 11.9: close event projection helpers: event-source classes, static/instance event tables, and generic event initialization.
- [x] Generator 11.10: close ABI/interface implementation output: static ABI classes, required interface forwarding, vtables, ABI invoke helpers, and CCW/RCW factories.
- [x] Generator 11.11: close remaining type-shape writers: full mapped-type table parity, projected/nonprojected/CCW name modes, ABI struct helpers, delegate helpers, attributes, and module activation metadata.
- [x] Queue 11.12: compile generated support handoff helpers into `winrt-projections`.
- [x] Queue 11.13: promote support handoffs to callable Kotlin APIs for ABI delegates, generic instantiations, event helpers, ABI plans, base-type/metadata mappings, and module activation entries.
- [x] Queue 12: create the first-class `kotlin-winrt` Gradle plugin module and DSL for SDK/NuGet metadata inputs and generated-source wiring.
- [x] Queue 12.1: add shared NuGet global-packages resolver in `winrt-metadata` so plugin NuGet handling consumes Microsoft NuGet CLI restore/cache output instead of creating a separate cache.
- [x] Queue 12.2: add `kotlin-winrt-gradle-plugin` with plugin ids, DSL, generation task, SDK/NuGet inputs, NuGet CLI global-packages lookup, and JVM generated-source wiring.
- [x] Queue 12.3: plugin now restores missing packages by invoking Microsoft NuGet CLI `install` directly, then feeds installed package roots to `winrt-metadata` without generating temporary project files.
- [x] Queue 12.4: plugin retries failed NuGet CLI commands with a Gradle-user-home cached `NuGet.CommandLine` download.
- [x] Queue 13: plugin roles now flow through one `winRt {}` DSL: default library publishes identity; `application {}` resolves runtime/resource integration.
- [x] Queue 13.1: library model publishes JSON identity metadata while generated projection sources remain part of the library compilation/artifact.
- [x] Queue 13.2: application model resolves transitive `kotlin-winrt` identity JSON artifacts and writes an aggregate for runtime/resource staging.
- [x] Queue 13.3: application model stages NuGet runtime DLLs from package identities and keeps WindowsAppSDK framework PRI/header staging scoped to WindowsAppSDK packages.
- [x] Queue 13.4: plugin application model wires staged runtime assets into Java resources and Gradle application distributions without sample-specific system properties.
- [x] Queue 14: `winrt-projections` expansion stayed plugin-generated and is closed for the current Foundation/sample validation path.
- [x] Queue 14.1: plugin TestKit validation now proves a real Gradle library project can apply `io.github.kitectlab.winrt` and generate deterministic WinRT sources from Windows SDK metadata.
- [x] Queue 14.2: remove direct Kotlin Gradle Plugin runtime class dependency from the plugin so generated-source wiring works in published/TestKit plugin classloaders.
- [x] Queue 14.3: `winrt-projections` now consumes `io.github.kitectlab.winrt` from the root `pluginManagement` included build and compiles the plugin-generated `Windows.Foundation.IStringable` slice.
- [x] Queue 15: samples are validation-only and closed through Sample 10; WindowsAppSDK coverage validates generated/plugin behavior without driving generator design from sample failures.
- [x] Queue 15.1: `winrt-samples` is now a `winRt { application {} }` consumer with a cswinrt `ApiCompatTests`-aligned `Windows.Data.Json.JsonObject.Parse` sample shape; native execution is opt-in until JSON ABI stability, `GetNamedValue("phone")` nullable object-return, and `GetNamedArray("education")` collection parity land upstream.
- [x] Queue 15.2: sample-side plugin graph validation now makes `winrt-samples:check` verify generated application identity includes `winrt-projections` metadata and excludes ordinary runtime implementation dependencies.
- [x] Queue 15.3: `winrt-samples` now has opt-in WindowsAppSDK split-package declarations via `kotlinWinRt.samples.windowsAppSdkWinuiVersion`; default checks stay offline/lightweight while explicit identity validation proves Foundation/InteractiveExperiences/WinUI metadata is recorded.
- [x] Queue 15.4: plugin API is consolidated to `io.github.kitectlab.winrt` plus `winRt {}`; library is the default model and `application {}` selects application identity/resource staging.
- [x] Queue 15.5: move the JSON sample closer to `.cswinrt/src/Tests/UnitTest/ApiCompatTests.cs` by executing `GetNamedValue("phone")` and indexed `GetNamedArray("education")` reads without sample-local substitutes.
- [x] Queue 16: root `validateWinRtQueue16` now runs validation in order: generator regression -> plugin graph tests -> projection compile/integration -> sample smoke.
- [x] Queue 17: plugin generated-source lifecycle and projection publication model are finalized; generated sources compile into library artifacts, while identity JSON remains the only separate WinRT metadata artifact.
- [x] Queue 18: generator single-file risk is reduced by splitting the former monolithic `KotlinProjectionGenerator.kt` into model/mapping, planner, renderer, orchestration, and support-renderer files without changing projection behavior.
- [x] Queue 19: generator renderer is split along `.cswinrt/src/cswinrt/code_writers.h`-style writer responsibilities; main renderer now owns type-shell dispatch while collection, member, ABI, event/companion, and type-resolution writers live in separate files.
- [x] Queue 20: ABI writer is split into marshaling, array/native-struct, async/reference/signature, delegate, and vtable invocation units; no single generator writer file now carries the former 2000+ line ABI surface.
- [x] Queue 21: resumed generator feature work from `.cswinrt/src/cswinrt` mapped-type/event writer parity; generated event add/remove surfaces now use `Windows.Foundation.EventRegistrationToken` instead of Kotlin `Int`.
- [ ] Queue 22 正在做: close remaining `.cswinrt/src/cswinrt/helpers.h` and `code_writers.h` mapped-type/required-interface generator rules before treating WinUI sample output as validation.

## Generator Follow-Through

- [x] Support helper output now exposes callable Kotlin APIs instead of passive descriptor-only lists.
- [x] Activatable runtime-class `create()` generation now calls the generated activation factory path.
- [x] Plugin/projection integration now wires generated sources into `winrt-projections` through the included plugin build before broad checked-in projection growth.
- [x] Generator ABI 1: generator call planning now carries metadata parameter categories and uses the `In/Ref/Out/PassArray/FillArray/ReceiveArray` model as the ABI-shape input.
- [x] Generator ABI 2: `WinRtAbiMarshalerPlanDescriptor` is now threaded into parameter/return call planning; array pass/fill/receive ABI arguments use descriptor categories.
- [x] Generator ABI 3: generic ABI delegate inventory now renders ABI type names and covers `.cswinrt` progress-handler discovery for `IAsyncOperationWithProgress`.
- [x] Generator ABI 4: generated generic type-instantiation support now initializes dependency closures recursively instead of one flat pass.
- [x] Generator ABI 5: delegate emission now covers scalar/enum/object/interface async and non-Unit callback returns through runtime delegate descriptors.
- [x] Generator ABI 6: array and nullable/reference ABI now covers scalar/enum/struct buffers, runtime marshaler-backed string/object/interface arrays, and `IReference/IReferenceArray` PIID generation.
- [x] Generator ABI 7: struct ABI now uses corrected 16-bit layout, mapped Foundation/Numerics struct aliases, nested blittable struct layout, and rejects non-blittable struct metadata companions instead of generating invalid `.Metadata` calls.
- [ ] Generator ABI 8 正在做: WindowsAppSDK generation compiles only after remaining `.cswinrt` dependency-closure generator rules are closed; do not mark WinUI generation complete from sample-driven fixes.
- [x] Generator ABI 8.1: overloaded method slot constants now use the declaring interface method row/slot identity instead of runtime-class method row IDs, matching `.cswinrt` vtable ownership.
- [x] Generator ABI 8.2: runtime classes whose required interfaces map to `IIterable`/`IVector`/`IMap` suppress raw WinRT collection members and redundant inherited enumerable surfaces; `IVector/IMap` project as Kotlin mutable collections, while remaining static-ABI/custom mapped-member call-mode parity stays tracked in 8.16.
- [x] Generator ABI 8.3: unsigned Kotlin built-ins are resolved through centralized generator TypeNames so KotlinPoet does not emit invalid root imports; generated file write-out no longer patches imports as strings.
- [x] Generator ABI 8.4: closed the first `write_required_interface_members_for_abi_type` slice for ABI-callable required interface method/property forwarding; mapped-helper and explicit-interface branches remain tracked in 8.11/8.12.
- [x] Generator ABI 8.5: closed the first mapped collection runtime-class surface slice by replacing constructor-time delegation with explicit Kotlin forwards for current collection owners; full mapped-helper parity remains tracked in 8.12.
- [x] Generator ABI 8.6: closed the first object/interface wrapping slice with generated `Metadata.wrap(IUnknownReference)` and method-only `NativeProjection`; property/event/inherited/generic interface proxy parity remains tracked in 8.10.
- [x] Generator ABI 8.7: closed the static member accessor merge slice for `.cswinrt` `write_static_members`; full activatable/composable constructor overload parity remains tracked in 8.9.
- [x] Generator ABI 8.8: closed the generic ABI delegate inventory collection slice for mapped collection/async/reference/event shapes; fast-path carrier coverage remains limited to runtime-provided invoke shapes and broader fast-ABI class aggregation remains tracked in 8.13.
- [x] Generator ABI 8.9: factory/composable constructor generation now routes activatable factory methods and composable factory methods with leading user parameters through generated factory helpers, instead of only default activation / no-arg `CreateInstance`.
- [x] Generator ABI 8.10: generated interface RCW wrappers now use one `NativeProjection` path with `TYPE_HANDLE`, cover methods/properties/events/inherited interfaces, and emit generic wrapper type parameters instead of duplicate object wrappers.
- [x] Generator ABI 8.11: runtime-class required-interface member generation now mirrors `.cswinrt` property accessor merging by combining getter/setter accessors across implemented interfaces before emitting Kotlin members.
- [x] Generator ABI 8.12: required-interface mapped-helper discovery now walks substituted interface closure, so generic collection/`IBindable*`/`IClosable` mapped helper metadata and member suppression use one mapped-member owner instead of direct-interface-only rules.
- [x] Generator ABI 8.13: close the metadata descriptor slice for `.cswinrt` fast-ABI class aggregation; `FastAbiAttribute` classes now expose default/other interface slots, method counts, and hierarchy offsets, while generated static ABI class folding remains tracked in 8.18.
- [x] Generator ABI 8.14: close the Kotlin mapped-type policy slice; runtime-backed KMP mappings stay explicit while metadata value structs are generated and helper-only XAML/system types are suppressed, while mapped member/helper emission remains tracked in 8.16/8.17.
- [x] Generator ABI 8.15: WinUI dependency-closure filtering is shared in `winrt-metadata`, used by both CLI and Gradle plugin, and validated with WindowsAppSDK sample generation so Microsoft/Windows UI dependencies no longer depend on sample-side include lists.
- [ ] Generator ABI 8.16: close `.cswinrt/src/cswinrt/code_writers.h` `write_custom_mapped_type_members` parity for mapped collection/bindable/`IClosable`/`INotifyDataErrorInfo` members, including public vs explicit/private forwarding and static-ABI vs IDIC call modes.
- [x] Generator ABI 8.17a: required-interface forwarding now walks the substituted generic interface closure, emits member signatures with concrete generic arguments, and uses the actual required-interface cache target for inherited interface slots.
- [x] Generator ABI 8.17b: runtime-class required-interface mapped helper generation now handles runtime-owned `INotifyDataErrorInfo` by projecting `WinRtDataErrorInfo` through the runtime helper, without depending on a generated mapped interface metadata class.
- [x] Generator ABI 8.17c: required bindable collection helpers now project `IBindableIterable`/`IBindableVectorView`/`IBindableVector` to `Iterable<Any?>`/`List<Any?>`/`MutableList<Any?>` through runtime bindable projection helpers, while suppressing redundant bindable enumerable surfaces.
- [x] Generator ABI 8.17d: required `IClosable` mapped helper generation now emits `AutoCloseable` plus runtime `WinRtClosableObject(_inner).close()` and suppresses generated `IClosable.Metadata` cache dependencies.
- [x] Generator ABI 8.17e: required `IIterator<T>` mapped helper generation now emits `Iterator<T>` stateful forwarding through the actual required iterator cache and suppresses raw `Current`/`HasCurrent`/`MoveNext` members.
- [x] Generator ABI 8.17f: required mapped-helper descriptors now carry `.cswinrt` call mode, helper wrapper, adapter, private-member, and enumerable-removal rules as structured metadata and generated companion/support handoff data instead of weak member-name strings.
- [x] Generator ABI 8.17: close current `.cswinrt` `write_required_interface_members_for_abi_type` mapped-helper parity for Kotlin runtime-class projection; static-ABI vs IDIC differences are now explicit descriptor data for the later ABI implementation/static-ABI folding path, while projected runtime classes use Kotlin runtime helpers.
- [x] Generator ABI 8.18a: fast-ABI class descriptors now flow into generated metadata companions as deterministic default/other interface slot and property slot handoff data, matching `.cswinrt` static ABI class folding inputs.
- [x] Generator ABI 8.18b: interface ABI slot constants now collapse duplicate accessor MethodDef rows and use the fast-ABI folded vtable start for exclusive interfaces owned by a fast-ABI runtime class.
- [ ] Generator ABI 8.18: close fast-ABI static ABI class folding from `.cswinrt` `write_static_abi_classes`; generated interface ABI slot tables must use the fast-ABI default + other interface slot sequence when an exclusive interface belongs to a fast-ABI class.
- [x] Generator ABI 8.19a: object-reference descriptors now encode `.cswinrt` `write_class_objrefs_definition` decisions for manual bindable skips, fast-ABI non-default exclusive skips, sealed default `_inner` reuse, generic interface initialization, and unsealed fast-ABI default-interface hierarchy dispatch.
- [x] Generator ABI 8.19b: runtime-class cache generation now consumes object-reference plans, skipping fast-ABI non-default exclusive caches and routing their generated member calls through the folded default-interface cache.
- [x] Generator ABI 8.19c: generated object-reference cache getters now consume fast-ABI default-interface objref plan fields, including the runtime vtable pointer-return boundary used by `.cswinrt` `GetDefaultInterfaceObjRef`; parameterized-interface cache getter PIID emission remains in 8.19.
- [ ] Generator ABI 8.19: close `.cswinrt` object-reference cache rules from `write_class_objrefs_definition`, including manually generated bindable interfaces, fast-ABI non-default exclusive interfaces, unsealed default-interface hierarchy offsets, and generic interface initialization.
- [ ] Generator ABI 8.20: close `.cswinrt` factory/module activation class parity for authored/activatable types; keep authoring runtime work frozen, but generator descriptors must still distinguish projected activation constructors from server activation factory/member metadata.
- [x] Namespace additions from `.cswinrt/src/cswinrt/strings` are now modeled at namespace level, flow through generator support handoffs and Gradle identity metadata, and keep `.cswinrt` `addition_exclude` semantics without treating C# addition files as Kotlin projection inputs.
- [x] Generator memory: CLI and Gradle generation now stream each rendered Kotlin file to disk instead of retaining the full WindowsAppSDK output set in memory.
- [x] Generator WinUI constructors: composable `CreateInstance(System.Object, System.Object)` factories now produce public default constructors for generated WinUI classes such as `Button`, `Page`, and `Window`.
- [x] Generator KMP mappings: `Windows.Foundation.Uri` now maps to common `WinRtUri`, not JVM-only `java.net.URI`.
- [x] Generator event surface: generated events now expose `WinRtEvent<T>` properties with `add/remove` and `+=/-=` while keeping add/remove methods as the low-level ABI-backed entry.
- [x] Generator namespace policy: generated code stays under `io.github.kitectlab.winrt.projections`; any shorter WinRT facade is a separate future layer.
- [x] Generator generic declarations: collection/async generic declarations now emit Kotlin type parameters instead of unresolved `T0/T1` imports.
- [x] Generator structure 19.1: the former 7000-line generator file is split into `KotlinProjectionModel`, `KotlinProjectionPlanner`, `KotlinProjectionRenderer`, `KotlinProjectionGenerator`, and `KotlinProjectionSupportRenderer` ownership files.
- [x] Generator structure 19.2: split `KotlinProjectionRenderer` into focused collection, member, ABI, event/companion, and type-resolver writer files while preserving current behavior and `.cswinrt` writer responsibility mapping.
- [x] Generator structure 20.1: split `KotlinProjectionAbiRenderer` into focused marshaler, array/native-struct, async/reference/signature, delegate, and invocation writers; ABI classification remains centralized in shared model/mapping helpers.
- [x] Generator structure 21.1: audited the split generator writers against `.cswinrt/src/cswinrt/helpers.h`/`code_writers.h` and closed the first mapped-event gap by projecting `EventRegistrationToken` through the shared mapped-type table, planner ABI bindings, runtime `WinRtEvent`, and generator tests.
- [x] Generator structure 22.1: audited the remaining `.cswinrt` mapped-type table and selected the runtime-backed Foundation/XAML system projection name slice.
- [x] Generator structure 22.2: projected `HResult`, `EventHandler<T>`, and runtime-backed XAML system projection names through the shared `MAPPED_TYPES` table; ABI-specific marshaling remains centralized for the next mapped-ABI slice.
- [x] Generator structure 22.3: added centralized mapped-ABI support for custom struct helper mappings and moved `DateTime`/`TimeSpan` to KMP `kotlin.time.Instant`/`Duration`; generated ABI code now calls runtime marshal facades for `DateTime`, `TimeSpan`, and `HResult` instead of generated struct `Metadata`.
- [x] Generator structure 22.4: extended mapped-ABI ownership to `Uri` and runtime-backed XAML interface/runtime-class mappings; generated ABI code now uses the shared object marshal facade instead of requiring mapped public types to implement `IWinRTObject`.
- [x] Generator structure 22.5: removed geometry/numerics WinRT structs from the generator public mapped-type table so `Point`, `Size`, `Rect`, `Vector*`, `Matrix*`, `Plane`, and `Quaternion` are emitted from metadata as projection structs instead of runtime public model aliases.
- [x] Generator/runtime 22.6: moved geometry/numerics `IPropertyValue`/`IReference` support behind generated struct registration; runtime no longer pre-registers `Point`, `Size`, `Rect`, `Vector*`, `Matrix*`, `Plane`, or `Quaternion` as built-in public value-boxing types.
- [x] Generator/runtime 22.7: mapped `Windows.UI.Xaml.Interop.TypeName` to nullable `KClass<*>` through the runtime system marshaler facade, including ABI HSTRING cleanup; verified metadata attributes and XAML helper-only entries remain metadata-owned, not generator-local type tables.
- [x] Generator/runtime fast ABI 22.8: generator now tracks ABI carrier kinds for vtable arguments, converts Kotlin aliases such as `Boolean` to their ABI carrier before calling `invokeArgs`, uses direct carrier fast paths when present, falls back to generic invocation outside that fast-path set, and adds JVM carrier fast paths for `Int16`, `Float`, `Pointer,Int32,Pointer`, and `Int8,Int16,Float`.

## Sample Plan

- [x] Sample 1: establish `winrt-samples` as a plugin-driven application module and keep legacy `sample-jvm-winui3` out of the active path.
- [x] Sample 2: mirror the `.cswinrt/src/Tests/UnitTest/ApiCompatTests.cs` JSON flow with `JsonObject.Parse`, `GetNamedValue("phone")`, boolean reads, and indexed `education` reads.
- [x] Sample 3: validate application identity aggregation from `winrt-projections` and exclude plain runtime implementation dependencies.
- [x] Sample 4: expose opt-in WindowsAppSDK split-package declarations matching `.cswinrt/src/Projections/WinAppSDK`.
- [x] Sample 5: replace checked-in JSON projection reliance with plugin-generated `Windows.Data.Json` output once Queue 14 can compile that namespace deterministically.
- [x] Sample 6: add a `.cswinrt/src/Samples/NetProjectionSample`-style `SimpleMath().add(5.5, 6.5)` sample, generate `SimpleMathComponent` projection from the real component WinMD, and stage the local component DLL from `winrt-projections` library identity.
- [x] Sample 7: added real `.cswinrt/src/Samples/WinUIDesktopSample` smoke surface using generated `Microsoft.UI.Xaml` projections: `Application.Start`, `Window.Activate`, `Button.Click`, `MainPage`, and `UIElement.TappedEvent` no longer use sample-local WinUI fakes.
- [x] Sample 7.1: WindowsAppSDK projection generation now completes from the plugin path with `.cswinrt/src/Projections/WinAppSDK` package/filter shape; remaining Sample 7 work is real generated WinUI API usage, not sample-local fakes.
- [x] Sample 8: WinUI runtime/resource packaging now follows WindowsAppSDK package staging at the application model and distribution layer, including `resources.pri` aliasing without staging native build headers.
- [x] Sample 9: sample validation now checks `installDist` application layout, staged `kotlin-winrt-runtime-assets`, and default `run` bootstrap without opt-in native smoke.
- [x] Sample 10: Sample 5-9 are closed and sample expansion is stopped. Keep `winrt-authoring` frozen for now; do not implement `.cswinrt/src/Samples/AuthoringDemo` or `BgTaskComponent` samples before authoring support is complete.
- [x] Sample 10.1: WinUI smoke execution is opt-in through `kotlin.winrt.samples.runWinUiSmoke`; the sample uses explicit WindowsAppSDK package identities and only the `.cswinrt/src/Samples/WinUIDesktopSample` type surface to avoid compiling the full Microsoft namespace during `run`.
- [ ] Sample 11: resume non-authoring sample validation only when it proves already-completed generator/projection/plugin behavior; keep authoring samples in the deferred native/authoring plan.

## Completed Milestones

- [x] Runtime 1.1-1.20: JVM-first `.cswinrt/src/WinRT.Runtime` baseline closed; remaining differences are explicit Kotlin narrowings.
- [x] Metadata 2.1-2.18: real WinMD loading, deterministic model, type refs, ABI descriptors, closure/special-type lookup, diagnostics, and fixture validation.
- [x] Metadata Full-Parity 3.x-4.52: `.cswinrt/src/cswinrt` metadata-owned helper/writer handoff audit closed.
- [x] Generator 3.1-3.7: declaration planning, declaration shells, member baseline, slot-based ABI cleanup, async/custom mapping, CLI, and first generated projection slice.
- [x] Queue 9.5: async/custom mapping moved out of ad hoc type substitution and backed by runtime async references.
- [x] Queue 10/10.9: representative generated API proof and final metadata audit closed.

## Frozen Until Prerequisites Close

- [x] `winrt-projections`: broad checked-in growth remains avoided; current projection validation uses plugin-generated output after Generator ABI 1-8.
- [x] `winrt-samples`: broad WinUI/sample expansion is stopped after Sample 10; future authoring samples remain blocked on authoring contracts.
- [ ] `mingwX64`: keep shared contracts viable, but full native parity planning starts after the JVM generator/projection/plugin path is coherent.
- [ ] `winrt-authoring`: moved to the native/authoring deferred plan; do not spend current generator/projection/sample cycles on hosting, source-generation, AuthoringDemo, BgTask, or embedded authoring samples.

## Plugin Generated-Source Lifecycle

- [x] Plugin lifecycle 18.1: `generateWinRtProjections` is wired as a pre-`compileKotlin` task, generated sources are added to `main`, and Gradle up-to-date checks now skip unchanged generation.
- [x] Plugin lifecycle 18.2: generator execution is incremental when it does run: it avoids whole-output deletion, rewrites only changed files, and removes only stale generated `.kt` files.
- [x] Plugin lifecycle 18.3: no separate generated-source publication lifecycle is needed for normal library consumption; generated Kotlin is wired into `main`, compiled into the library artifact, and downstream applications consume only the identity JSON for NuGet/runtime/resource staging.
- [x] Plugin lifecycle 18.4: plugin generated-source lifecycle is closed for library/application consumption; authoring-specific generated-source behavior is deferred to the native/authoring plan.

## Native/Authoring Deferred Plan

- [ ] Native 1: complete `mingwX64` ABI/runtime parity for the shared contracts already exercised by JVM generator/projection/sample validation.
- [ ] Native 2: validate generator output shape against both JVM and `mingwX64` ownership constraints before adding authoring-facing generator rules.
- [ ] Authoring 1: after Native 1-2, inspect `.cswinrt/src/Authoring` and map projected-object lifetime, factories, activation/hosting boundaries, and ABI ownership into `winrt-authoring`.
- [ ] Authoring 2: implement `winrt-authoring` only after the native ABI boundary is ready; keep authoring runtime contracts out of samples and plugin resource staging.
- [ ] Authoring Samples: after Authoring 1-2, mirror `.cswinrt/src/Samples/AuthoringDemo`, BgTask hosting, and `.cswinrt/src/Samples/TestEmbedded`; do not track these in the active sample queue before then.

## Plugin Plan

- [x] Plugin 4.1: create plugin module, task model, DSL, source-set wiring, SDK/NuGet metadata inputs, and filters.
- [x] Plugin 4.2 prerequisite: shared metadata resolver now locates Microsoft NuGet global-packages roots, resolves package id/version closure from `.nuspec`, and feeds package roots to `WinRtMetadataSource`.
- [x] Plugin 4.2 implementation: plugin invokes Microsoft NuGet CLI `install` directly when packages are not already present, consumes `nuget locals global-packages -list`, and passes package roots to the shared resolver.
- [x] Plugin 4.2 CLI acquisition: plugin tries configured/system NuGet first, then downloads Apache-2.0 `NuGet.CommandLine` into Gradle user home cache and retries the same command on failure.
- [x] Plugin 4.3: implement library-model JSON artifact metadata for NuGet/WinMD identity; generated sources stay compiled into the library itself.
- [x] Plugin 4.4: implement application-model transitive NuGet runtime/resource staging from identity JSON and local NuGet declarations.
- [x] Plugin 4.5: migrate useful runtime/resource staging and distribution/resource wiring out of legacy `sample-jvm-winui3` into the plugin application model.
- [x] Plugin 4.6: remove unpublished split plugin ids/DSLs; keep only `winRt {}` with `application {}` as the application model switch.
- [x] Plugin 4.7: align WindowsAppSDK handling with `.cswinrt/src/Projections/WinAppSDK`: add `windowsAppSdk(...)` split-package DSL and keep WindowsAppSDK staging limited to runtime framework assets, not native-target internals such as `WindowsAppSDK-VersionInfo.h`.
- [x] Runtime 4.8: remove sample-derived `WindowsAppSdkBootstrap` from `winrt-runtime`; WinUI bootstrap/application packaging belongs to plugin/application integration after matching `.cswinrt` project/package behavior.
- [x] Plugin 4.9: make plugin projection filters match `.cswinrt/src/Projections/WinAppSDK` include/exclude shape for WindowsAppSDK metadata.
- [x] Plugin 4.10: application runtime-asset staging now consumes Microsoft NuGet CLI `global-packages` output like projection generation, so WindowsAppSDK resources are resolved from the real NuGet cache instead of Gradle daemon-relative fallback roots.

## Validation Plan

- [x] Queue 16.1: add root `validateWinRtGenerator` for generator regression validation.
- [x] Queue 16.2: add root `validateWinRtPluginGraph` for included-build plugin TestKit and identity/resource graph validation.
- [x] Queue 16.3: add root `validateWinRtProjectionCompile` for plugin-produced projection compile validation.
- [x] Queue 16.4: add root `validateWinRtSampleSmoke` and `validateWinRtQueue16` for sample smoke and full ordered validation.

## Validation

- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-metadata:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11 --namespace Windows.Data.Json'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11-foundation --namespace Windows.Foundation'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11-support --namespace Windows.Foundation --namespace Windows.Foundation.Collections'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-projections:compileKotlin`
- [x] `./.agent_scripts/run_windows_gradle.sh validateWinRtQueue16 --no-configuration-cache`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test --no-configuration-cache --no-daemon --max-workers=1 -Dkotlin.incremental=false -Dorg.gradle.jvmargs='-Xmx1024m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=2 -XX:-UseCompressedOops -XX:MaxDirectMemorySize=256m -Dfile.encoding=UTF-8'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-runtime:jvmTest :winrt-generator:test --no-configuration-cache --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=2 -XX:-UseCompressedOops -XX:MaxDirectMemorySize=256m -Dfile.encoding=UTF-8'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-samples:generateWinRtProjections -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001 --no-configuration-cache --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx1024m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:CICompilerCount=2 -XX:-UseCompressedOops -XX:MaxDirectMemorySize=256m -Dfile.encoding=UTF-8'`
- [ ] `./.agent_scripts/run_windows_gradle.sh --no-configuration-cache :winrt-samples:run -Dkotlin.winrt.samples.runWinUiSmoke=true -PkotlinWinRt.samples.windowsAppSdkWinuiVersion=1.8.251105000 -PkotlinWinRt.samples.windowsAppSdkFoundationVersion=1.8.251104000 -PkotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion=1.8.251104001` currently blocked by remaining generated WinUI dependency-closure compile gaps (`Microsoft.UI.Composition`, `Windows.UI.Core/Input/Text/ViewManagement`, XAML attribute/type-name structs).
- [x] Validate touched modules with Windows Gradle via `./.agent_scripts/run_windows_gradle.sh <tasks>`.
- [x] For generator work, run targeted generator tests and projection compile checks before updating checked-in output.
- [x] For plugin work, add task-level tests for SDK source resolution, NuGet graph resolution, generated-source wiring, and application resource staging.
- [x] For samples, keep smoke coverage tied to the currently completed upstream slice.
