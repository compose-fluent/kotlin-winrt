# Plan

## Notes

- [x] Establish the root implementation plan file and integrate it into the repository workflow
- [x] Rewrite AGENTS.md to make `.cswinrt` the primary reference and to define JVM-first execution, dual-target parity, commit discipline, and plan maintenance rules
- [x] Standardize the canonical plan file name to `PLAN.md`
- [x] Clarify in AGENTS.md that `.cswinrt` alignment is responsibility-by-responsibility, with an explicit mapping table rather than a misleading literal name match
- [x] Tighten AGENTS.md to require strict one-to-one module correspondence with `.cswinrt/src`, including matching top-level module names
- [x] Adjust AGENTS.md to use Kotlin-style top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, with tests kept inside each module
- [x] Exclude legacy `sample-jvm-winui3` from the active refactor scope unless explicitly requested
- [x] Clarify in AGENTS.md that implementation is `.cswinrt`-reference-first and design-first rather than test-driven reverse engineering

## Direction

- [x] Define the repository-wide execution rule: inspect the matching `.cswinrt` implementation first, then establish the Kotlin module boundary and runtime/generator contract, then implement, and only then use tests for validation
- [ ] Drive current work in this order: `winrt-runtime` ABI/runtime foundation -> `winrt-metadata` WinMD loading and model construction -> generator pipeline inside the `.cswinrt/src/cswinrt` responsibility split -> deterministic checked-in output in `winrt-projections` -> `winrt-authoring` hosting/authoring support -> `winrt-samples` validation surfaces -> targeted tests that confirm parity instead of defining behavior
- [ ] For each active slice, keep the corresponding `.cswinrt` source area explicit in the task text or WIP note before expanding tests
- [ ] Reject test-driven backfilling when `.cswinrt` parity has not been established yet; if a test passes but the matching `.cswinrt` contract is still unknown, keep the slice open

## Current Focus Queue

- [x] Focus 1: complete Runtime 1.1 first by closing the remaining `.cswinrt/src/WinRT.Runtime` ABI primitive gaps in `winrt-runtime`, especially WinRT string ownership/reference mechanics, low-level memory layout helpers, and reusable raw vtable-call helpers.
- [x] Focus 2: complete Runtime 1.2 next by closing the remaining platform-boundary gaps in `winrt-runtime`, especially COM/WinRT initialization lifetime, Windows entry-point coverage, module/proc loading behavior, and HRESULT/error translation consistency.
- [x] Focus 3: complete Runtime 1.3 next by closing the remaining object-reference gaps in `winrt-runtime`, especially `IUnknown`/`IInspectable` ownership rules, interface query/cast behavior, and runtime class name/object identity semantics.
- [ ] Freeze now: do not expand Runtime 1.6+ delegate/collection/async/WinUI work until Focus 1 through Focus 3 are materially tighter.
- [ ] Freeze now: do not expand `winrt-metadata`, generator breadth, `winrt-projections`, `winrt-authoring`, or `winrt-samples` beyond maintenance needed to support Focus 1 through Focus 3.
- [x] Exit condition: only after Focus 3 is closed or reduced to clearly bounded residual gaps should the active queue move to Metadata 2.1 and subsequent generator work.

## Ordered Implementation Stages

- [ ] Stage 1: `winrt-runtime` first. Mirror the active `.cswinrt/src/WinRT.Runtime` slice, define the ABI/runtime contract, and do not move the primary effort to metadata or projections until the minimum contract for the current slice is in place.
- [ ] Stage 2: `winrt-metadata` second. Implement the WinMD loading and symbol/model layer required by the active projection slice only after the needed runtime contracts are known.
- [ ] Stage 3: generator pipeline third. Implement the matching `.cswinrt/src/cswinrt` generation rules only after Stage 1 and Stage 2 expose the runtime and metadata contracts those rules depend on.
- [ ] Stage 4: `winrt-projections` fourth. Expand deterministic checked-in projection output only after the corresponding generator rule exists; do not use handwritten projection growth as a substitute for missing generator work.
- [ ] Stage 5: `winrt-authoring` fifth. Start the authoring/hosting parity slice only after the runtime and metadata foundations required by `.cswinrt/src/Authoring` are already understood.
- [ ] Stage 6: `winrt-samples` and targeted tests last. Use samples and tests to validate the completed earlier stages, not to define missing runtime, metadata, or generator behavior.
- [ ] Stage gate: do not let `winrt-projections` become the starting point for a new slice. If projections seem blocked, return to the missing runtime, metadata, or generator prerequisite instead of expanding projection code directly.
- [ ] Stage gate: do not expand WinUI samples or broader test matrices until the corresponding runtime/generator slice has a documented `.cswinrt` mapping and an implemented contract.

## Current Ordered Slice Queue

- [ ] Queue 1: `winrt-runtime` ABI foundation from `.cswinrt/src/WinRT.Runtime` (GUID/IID/HRESULT primitives, HSTRING ownership, memory ownership boundaries, reusable vtable-call helpers, and initialization scope) must be completed before higher-level projection work expands.
- [ ] Queue 2: `winrt-runtime` object model and activation foundation from `.cswinrt/src/WinRT.Runtime` (object identity, `IUnknown`/`IInspectable` lifetime, activation factory lookup/caching, runtime-class activation, interface-cast helpers) must be stabilized before metadata-driven projection growth.
- [ ] Queue 3: `winrt-runtime` dependent mechanics from `.cswinrt/src/WinRT.Runtime` (delegate bridges, callback marshaling, parameterized IID/type signatures, collection adapters, async bridges, WinUI-specific runtime hooks) must follow only after Queue 1 and Queue 2 are in place.
- [ ] Queue 4: `winrt-metadata` real WinMD loading from the `.cswinrt/src/cswinrt` metadata responsibilities must replace placeholder modeling before projection planning or output breadth increases.
- [ ] Queue 5: `winrt-metadata` normalized symbol/model construction from `.cswinrt/src/cswinrt` (namespaces, types, generic instantiations, method/property/event shapes, signature normalization) must be completed before generator rules expand beyond the current skeleton.
- [ ] Queue 6: generator planning from `.cswinrt/src/cswinrt` must first cover declaration-shape planning for interfaces, runtime classes, enums, structs, and delegates before member emission breadth grows.
- [ ] Queue 7: generator emission from `.cswinrt/src/cswinrt` must then cover methods, properties, events, default interfaces, factories, custom mappings, collections, async, and WinUI-specific rules in dependency order rather than by whichever generated file currently fails.
- [ ] Queue 8: `winrt-projections` must prove that a representative standard WinRT API slice such as `Windows.Data.Json` can be regenerated through the generator path before any broader checked-in projection surface is added.
- [ ] Queue 9: `winrt-authoring` must follow the `.cswinrt/src/Authoring` hosting/lifetime/activation boundary requirements before broader source-generator or convenience work is expanded.
- [ ] Queue 10: `winrt-samples` must validate the active completed slice with the smallest possible sample first, then expand to WinUI bootstrap, resource, and message-loop validation only after the corresponding runtime/generator support exists.
- [ ] Queue 11: targeted tests must be added in the same dependency order: runtime unit tests -> metadata model tests -> generator regression tests -> projection/sample integration tests.

## Metadata And Generator Subslice Order

- [x] Metadata 2.1: replace placeholder modeling in `winrt-metadata` with real WinMD ingestion aligned with the metadata-reading responsibilities inside `.cswinrt/src/cswinrt`.
- [x] Metadata 2.2: stabilize deterministic namespace/type normalization in `winrt-metadata` so the same metadata input always produces the same ordered model before generator breadth expands.
- [x] Metadata 2.3: add core shape fidelity next: kind classification, GUID/default-interface data, base/implemented interface relationships, generic parameter shapes, and runtime-class activation/static/factory metadata.
- [x] Metadata 2.4: add member/signature fidelity next: method/property/event descriptions, parameter direction/pass-by semantics, and the normalized signature inputs needed by the generator.
- [ ] Metadata gate: do not grow projection breadth or handwritten generated API coverage to compensate for missing WinMD ingestion or incomplete model fidelity in `winrt-metadata`.
- [x] Generator 3.1: keep the Kotlin generator behind completed Metadata 2.1 through Metadata 2.4 prerequisites and derive its behavior from `.cswinrt/src/cswinrt` responsibilities rather than from existing handwritten projection files.
- [x] Generator 3.2: implement declaration planning first: determine the Kotlin declaration set for enums, structs, delegates, interfaces, runtime classes, and metadata companions before detailed member emission grows.
- [x] Generator 3.3: implement deterministic declaration-shell emission next: namespace layout, type declarations, interface/runtime-class scaffolding, companion metadata surfaces, and stable file/text ordering.
- [x] Generator 3.4: implement member emission next: methods, properties, events, interface inheritance handling, default-interface/runtime-class composition, and activation/static/factory surfaces.
- [x] Generator 3.5: only after Generator 3.1 through Generator 3.4 are coherent, add special-case rules for collections, async, custom type mappings, and WinUI-specific projection behavior.
- [ ] Generator gate: the current `KotlinProjectionGenerator` skeleton and `Windows.Data.Json` regression fixture are validation scaffolding only; do not treat them as permission to add handwritten standard WinRT projection files before the metadata and generator prerequisites are closed.

## Authoring And Sample Subslice Order

- [ ] Authoring 5.1: define the minimum `.cswinrt/src/Authoring`-aligned hosting boundary first: authored runtime object lifetime, ABI-facing identity/ownership, and activation/factory expectations.
- [ ] Authoring 5.2: define how authoring depends on the existing runtime and metadata/generator contracts next, including what generated metadata or helper code must exist before authored components can be surfaced safely.
- [ ] Authoring 5.3: only after Authoring 5.1 and 5.2 are explicit, expand `winrt-authoring` with source-generator or convenience surfaces.
- [ ] Authoring gate: the current `winrt-authoring` module is still a placeholder; do not let sample or test pressure invent its contract before the `.cswinrt/src/Authoring` boundary is mapped.
- [ ] Sample 6.1: keep `winrt-samples` on the smallest smoke scenario for the currently completed slice first; do not let samples depend on handwritten standard WinRT projection files.
- [ ] Sample 6.2: use samples to validate completed upstream work in dependency order: runtime smoke first, generated projection smoke next, authoring/hosting validation after that, and WinUI end-to-end scenarios last.
- [ ] Sample 6.3: only after runtime, metadata, generator, and authoring prerequisites are documented and implemented, expand to WinUI bootstrap, resources, window/message-loop, and broader desktop validation.
- [ ] Sample gate: if a sample requires new handwritten projection logic, authoring glue, or runtime workarounds, stop and move that missing behavior back into `winrt-runtime`, `winrt-metadata`, `winrt-projections`, or `winrt-authoring` instead of embedding it in `winrt-samples`.

## Runtime Subslice Order

- [x] Runtime 1.1: close `.cswinrt/src/WinRT.Runtime` ABI primitive gaps first: `Guid`, `HRESULT`, low-level memory layout helpers, raw vtable-call helpers, and WinRT string ownership/reference mechanics.
- [x] Runtime 1.2: close platform-boundary gaps next: COM/WinRT initialization lifetime, Windows API bindings, module loading, proc lookup, and HRESULT-to-exception/error translation.
- [x] Runtime 1.3: close object-reference gaps next: `IUnknown`/`IInspectable` wrappers, ownership/dispose semantics, interface query/cast helpers, runtime class name access, and object identity behavior.
- [x] Runtime 1.4: close activation gaps next: `RoGetActivationFactory`, manifest-free activation fallback, activation factory caching, and runtime-class activation helpers.
- [x] Runtime 1.5: close generic-signature prerequisites next: type-signature rendering and parameterized IID hashing needed by later collection/delegate/generic projection slices.
- [x] Runtime 1.6: only after Runtime 1.1 through Runtime 1.5 are coherent, expand delegate and callback marshaling support.
- [x] Runtime 1.7: only after Runtime 1.1 through Runtime 1.6 are coherent, expand collection adapters and projected collection interop breadth.
- [x] Runtime 1.8: only after Runtime 1.1 through Runtime 1.7 are coherent, expand async bridges and completion callback semantics.
- [x] Runtime 1.9: only after Runtime 1.1 through Runtime 1.8 are coherent, expand WinUI-specific runtime hooks, Xaml-specific mappings, and reference-tracking behavior.
- [ ] Runtime gate: the existing delegate/collection/generic helpers already present in `winrt-runtime` are provisional support surfaces; do not use their mere existence as justification to skip unfinished Runtime 1.1 through Runtime 1.5 prerequisites.

## JVM Primary Path

- [x] Establish the JVM-first Gradle multi-module structure with the Kotlin top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, and wire it into settings.gradle.kts
- [ ] Migrate the existing Kotlin package layout to `io.github.kitectlab.winrt` (正在做: new modules use the canonical package root; legacy `sample-jvm-winui3` is out of current scope unless explicitly requested)
- [ ] Create the `winrt-runtime` module and land the JVM runtime foundation corresponding directly to `.cswinrt/src/WinRT.Runtime` (正在做: Runtime 1.1 through Runtime 1.9 are now in place with GUID/HRESULT primitives, shared ABI layout constants, raw vtable entry helpers, cswinrt-style HSTRING marshalling/reference mechanics, COM/WinRT init lifecycle, Windows loader/proc bindings with try/throwing paths, semantic HRESULT translation, object-reference semantics, activation semantics, generic-signature prerequisites, delegate callback marshaling, collection projection/runtime adapters, async info/action/operation wrappers with completed-callback registration plus `CompletableFuture` bridging, and initial WinUI-specific bootstrap/Xaml metadata/reference-tracker hooks; next work can move to metadata-first slices in order.)
- [ ] Finish the earliest `winrt-runtime` prerequisite slices in queue order before taking on broader metadata or projection work (正在做: Runtime 1.1 through Runtime 1.9 are closed; metadata/generator work is now the active next dependency queue while later WinUI/sample breadth remains frozen behind those upstream contracts.)
- [x] Freeze expansion of later runtime slices until the earliest runtime prerequisites are explicitly closed (正在做: Runtime 1.1 through Runtime 1.5 are now closed, so later runtime slices may proceed in dependency order instead of as provisional stopgaps.)
- [ ] Create the `winrt-metadata` module and land the JVM metadata foundation aligned with the metadata-loading responsibilities inside `.cswinrt/src/cswinrt` (正在做: Metadata 2.1 through Metadata 2.4 are now closed with a JVM-side PE/CLI metadata reader, canonical path deduplication, deterministic normalization, core type-shape fidelity, and member/signature modeling for methods, properties, events, and parameter direction semantics; current work is preserving ABI-facing method/accessor row ids through normalization so later generator/runtime slot binding can consume stable metadata instead of signature-sorted lists.)
- [ ] Keep `winrt-metadata` work behind the active runtime prerequisite queue and focus it on real WinMD ingestion before richer projection-shape expansion
- [ ] Keep `winrt-metadata` in ingestion-first then fidelity-second order (正在做: ingestion, deterministic normalization, core type-shape fidelity, and member/signature completeness are now in place, so the next work must stay on generator consumption rather than richer projection helpers)
- [ ] Create the generator surface aligned with `.cswinrt/src/cswinrt` while keeping Kotlin tests inside the owning modules (正在做: the metadata -> projection-plan -> KotlinPoet generator now enforces Generator 3.1 metadata contract guards, completes Generator 3.2 declaration planning, completes Generator 3.3 deterministic shell emission, completes Generator 3.4 member emission, and completes Generator 3.5 special-case type mapping from `.cswinrt/src/cswinrt/helpers.h` for collections, async, custom mapped types, and WinUI bindable surfaces; next work should move out of baseline generator coverage and into broader projection regeneration/consumption work instead of adding new handwritten shells.)
- [ ] Keep generator work in declaration-first then member-emission order rather than expanding whichever projection file currently exists in `winrt-projections`
- [ ] Freeze handwritten projection growth until the existing generator skeleton can reproduce a representative standard WinRT API slice such as `Windows.Data.Json` through the planned metadata/generator path
- [ ] Create the `winrt-authoring` module and land the JVM authoring and hosting foundation corresponding directly to `.cswinrt/src/Authoring` (正在做: module exists, host/source-generator parity still missing; do not design this from sample pressure or failing tests)
- [ ] Keep `winrt-authoring` on boundary-first design (正在做: the module is still only a placeholder, so the next work must map hosting/lifetime/activation boundaries from `.cswinrt/src/Authoring` before any convenience API or sample glue appears)
- [ ] Create the `winrt-projections` module and wire deterministic generated output corresponding directly to `.cswinrt/src/Projections` (正在做: module is wired into the root Gradle build; `KotlinProjectionGeneratorTest` carries a `Windows.Data.Json` generator regression fixture for a representative standard WinRT API declaration set, and the current work is to move generated output toward real runtime-backed consumption by emitting runtime-binding descriptors, companion-level `ActivationFactory`/static-interface accessors, interface-owned ABI slot constants, runtime-class RCW shells with internal inspectable wrapping, cached objref accessors for implemented interfaces, runtime-member owner descriptors that map instance members onto the correct interface cache/slot-owner pair, and a `.cswinrt`-style signature-driven binder selection layer for the first runtime-backed member calls, including the initial single-parameter `String`/`UInt32`/`Boolean`/`Double` method and setter bindings, instead of growing handwritten standard API projection files or per-method special cases in the module.)
- [ ] Limit `winrt-projections` work to generator-produced output for already-implemented slices; do not grow handwritten projection breadth
- [x] Delete handwritten standard WinRT API projection files such as `Windows.Data.Json` when they drift into `winrt-projections`; only generator output or the narrow `.cswinrt/src/Projections`-style handwritten glue slices may remain there
- [ ] Create the `winrt-samples` module and wire the JVM sample validation surface corresponding directly to `.cswinrt/src/Samples` (正在做: module is wired into the root Gradle build; sample work is intentionally held to bootstrap-level scaffolding until generator-produced standard API projections exist, so the module does not keep handwritten standard WinRT projection-driven smoke code.)
- [ ] Keep `winrt-samples` on smoke-first validation only until the upstream runtime/generator/authoring prerequisites are ready (正在做: the current module is intentionally minimal and should not expand into broader WinUI, hosting, or standard API smoke scenarios until the upstream generated projection contracts exist)
- [ ] Keep sample work to the smallest validation surface for the currently completed slice before any broader WinUI sample expansion
- [ ] Place tests in the relevant module source sets instead of creating a separate top-level tests module
- [ ] Rename or delete legacy top-level modules that do not match the strict `.cswinrt/src` names (正在做: legacy `sample-jvm-winui3` may remain on disk for reference, but it is not part of the active refactor target)
- [ ] Complete WinUI 3 startup, bootstrap, resource, and message-loop validation through the `winrt-samples` module and per-module test layouts

## .cswinrt Alignment Slices

- [ ] Align COM/WinRT marshaling, HRESULT translation, and lifetime management (正在做: Runtime 1.1 through Runtime 1.3 are closed for ABI primitives, COM/WinRT init lifecycle, Windows loader/proc bindings, semantic HRESULT translation, and baseline object-reference ownership/query/identity behavior; the remaining work in this parity slice now shifts to later activation, generic, delegate, and collection-dependent mechanics.)
- [ ] Align activation factory lookup and runtime-class activation semantics (正在做: Runtime 1.4 is closed for JVM-side `RoGetActivationFactory`, manifest-free fallback probing, activation factory caching, and direct runtime-class activation helpers; Runtime 1.9 also adds WinUI-specific activation consumers for Xaml metadata provider initialization, while broader authoring/reference-tracking parity still remains for later slices.)
- [ ] Align generic interface projection, parameterized IID, and type-signature behavior (正在做: Runtime 1.5 is closed for primitive/enum/struct/delegate/runtime-class/parameterized signature rendering and parameterized IID hashing with cswinrt parity samples; remaining work now shifts to consuming those signatures in later delegate, collection, and generator slices rather than to the signature primitives themselves.)
- [ ] Align delegate bridges, delegate handles, and callback marshaling (正在做: Runtime 1.6 is closed for JVM-side delegate descriptors, parameter-kind decoding, real `IUnknown + Invoke` ABI stub generation, AddRef/Release/QueryInterface lifetime behavior, and HRESULT-safe callback dispatch; remaining work now shifts to consuming these delegate primitives in later collection, async, and generator slices rather than to the delegate ABI boundary itself.)
- [ ] Align collection projection for iterable/list/map/view adapters (正在做: Runtime 1.7 is closed for corrected WinRT collection slot layouts, `IKeyValuePair`/`IIterable`/`IIterator`/`IVectorView`/`IVector`/`IMapView`/`IMap` wrapper coverage, `GetMany`/`IndexOf`/`Split`/`ReplaceAll` support, and Kotlin-facing vector/map adapter surfaces; remaining work now shifts to consuming these collection primitives in later async, generator, and projection slices rather than to the collection runtime boundary itself.)
- [ ] Align async projection, task bridging, and completion-callback semantics (正在做: Runtime 1.8 is closed for `IAsyncInfo`/`IAsyncAction`/`IAsyncOperation<T>` wrapper coverage, async status/error/cancel/close semantics, completed-handler registration via delegate ABI stubs, parameterized async interface/handler IID helpers, and JVM-side `CompletableFuture` bridging; later work can consume these runtime primitives for generator-authored await/task surfaces and progress-enabled async variants.)
- [ ] Align authoring support, projected-object bridges, and hosting support
- [ ] Align WinUI-specific type mappings, Xaml metadata, and bootstrap behavior (正在做: Runtime 1.9 is closed for Windows App SDK bootstrap discovery/initialize/shutdown hooks, `Microsoft.UI.Xaml.XamlTypeInfo.XamlControlsXamlMetaDataProvider` activation/initialize helpers, `IXamlMetadataProvider` full-name lookup access, and baseline `IReferenceTracker` lifecycle integration on `ComObjectReference`; later work can consume these hooks from authoring/generator/sample layers rather than reimplementing them there.)

## Generation And Validation

- [ ] Establish deterministic generator-output validation and regression checks after the corresponding `.cswinrt`-derived generator contracts are explicit
- [ ] Establish a targeted JVM unit-test and integration-test matrix that validates agreed runtime/generator contracts instead of defining them
- [ ] Establish a Windows-first verification flow that prioritizes affected modules
- [ ] Keep validation breadth aligned with the ordered slice queue so that smoke, unit, generator-regression, authoring, and sample coverage expand only after their owning contracts are explicit

## mingwX64 Parity

- [ ] After the matching JVM slices are complete, define the runtime parity plan for `mingwX64` under the shared contracts
- [ ] Create `sample-mingw-winui3` or an equivalent native validation module
- [ ] Add validation coverage that proves `mingwX64` behavior matches JVM semantics
