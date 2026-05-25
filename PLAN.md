# Plan

## Planning Rules

- [ ] `.cswinrt/` is the implementation baseline for runtime, metadata, generator, authoring, projections, packaging evidence, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Tests validate `.cswinrt` parity; do not derive runtime or generator rules from sample failures.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Keep Gradle configuration cache and build cache enabled; cache failures are implementation issues to fix, not flags to bypass.
- [ ] Keep `mingwX64` implementation frozen for the current queue. Shared contracts must stay native-viable, but no native actual work starts until the JVM/generator/packaging focus items are closed.
- [ ] Keep codebase slimming frozen until functional completeness is reached. Current work may prevent avoidable duplication while closing parity gaps, but must not run deletion-only refactors, source-count targets, or module-collapse work before projection behavior is complete.

## Reference Boundaries

- [ ] CsWinRT is the projection-behavior reference only: use `.cswinrt/` to derive WinRT observable behavior, metadata interpretation, generated surface shape, ABI ownership, authoring contracts, and packaging evidence.
- [ ] Do not treat CsWinRT `netstandard` / modern `.NET` target differences as Kotlin goals; those environment splits do not map to Kotlin projection completeness.
- [ ] Kotlin has no runtime reflection equivalent for CsWinRT's attribute/source-generator discovery path. Equivalent annotation, metadata, registration, or source-generator behavior must be implemented through `winrt-generator` and `winrt-compiler-plugin`, with runtime discovery kept fail-closed.
- [ ] WinMD ingestion targets native WinMD metadata only. Do not add `cswinmd`-specific compatibility as a projection goal.
- [ ] Packaging target is complete Kotlin `appx` / `msix` application packaging, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout. CsWinRT/MSBuild targets are implementation evidence, not a requirement to clone full MSBuild.

## Current Focus Queue

- [ ] Generator projection matrix closure 正在做: close ABI/member/metadata fail-closed gaps for activation, static/factory, delegates/events, structs, arrays, custom mapped types, collections, async, generic instantiation, and unsupported shapes before broad projection growth.
- [ ] Metadata input and native WinMD fidelity: finish Kotlin-needed SDK/file/directory/reference expansion and harden native WinMD parsing; do not add `cswinmd` compatibility.
- [ ] Compiler-plugin authoring and projection lowering: continue descriptor-backed K2/IR lowering only where it closes functional projection or authoring behavior, not as a source-count reduction task.
- [ ] Kotlin appx/msix packaging validation: keep completed packaging behavior cache-compatible while closing any remaining package/layout/resource gaps discovered by functional validation.
- [ ] Functional completeness before slimming: close runtime, metadata, generator/compiler-plugin, projection, authoring, and Kotlin appx/msix packaging gaps in this plan before any source-count or structure-shrinking work becomes active.

## Available Capability Baseline

- [x] Runtime/ABI JVM baseline: representative JVM support exists for HRESULT/GUID/HSTRING, COM object references, activation, object identity, CCW/RCW ownership, delegate/event/collection/object marshaling, async bridges, WinUI bootstrap, and core vtable-call lowering.
- [x] Metadata baseline: native WinMD ingestion, deterministic model construction, signatures, semantic helpers, accessors, generic/default-interface metadata, custom attributes, mapped types, and writer-handoff descriptors are available for the current generator path.
- [x] Generator/compiler-plugin baseline: deterministic projection generation, support-file handoff, owner-local `NativeProjection` bodies, descriptor intrinsic lowering, compiler-support aggregation, reflection/proxy removal, generic ABI registry cleanup, and Gradle build/configuration-cache compatibility are available.
- [x] Generator fail-closed baseline: current generator rejects missing or incomplete metadata for composable factories, activation/static interfaces, default/implemented/required interfaces, event delegates/accessors, delegate/runtime-class/interface ABI bindings, unsupported ABI shapes, enum/struct/array ABI metadata, standalone and member-referenced delegate `Invoke` IID/parameter/return ABI metadata, activatable/composable factory create ABI metadata, instance/static property getter/setter bindings, ordinary instance method bindings, static method bindings, and instance/static method ABI call-plan bindings before rendering; mapped augmentation runtime classes only exempt Kotlin-owned collection/iterator, `IClosable`, and `INotifyDataErrorInfo` members, not unrelated runtime-class methods.
- [x] Authoring JVM baseline: source scanning, generated TypeDetails, authored WinMD emission, CCW factories, host manifests, activation factory exports, composable WinUI subclasses, receive-array handling, and JVM host bridge slices are available for current JVM validation.
- [x] WinUI/sample baseline: `winrt-samples` and the KMP WinUI library/application graph can validate representative generated WinUI controls, resources, events, focus, AutomationPeer, dependency properties, authored subclasses, and app lifecycle after the owning slices are implemented.
- [x] Packaging/resource baseline: Kotlin Gradle application staging covers WindowsAppSDK payloads, application PRI/MRT generation, manifest-derived metadata, package item classification, resource inventories, component PRI aggregation, makepri execution, cacheable staging, fail-closed tool diagnostics, package/sign/verify disabled-output preservation, manifest payload validation, signing input validation, and makeappx verification for the current JVM WinUI samples.

## Phase 1 Runtime And ABI

- [ ] Runtime parity audit: compare activation, object identity, marshaling, delegate/event, collection, async, HRESULT/error-info, and WinUI bootstrap behavior against `.cswinrt/src/WinRT.Runtime`; close gaps in `winrt-runtime` before expanding dependent projections.
- [ ] ABI primitive closure: verify that GUID/HRESULT/HSTRING ownership, memory layout helpers, raw vtable-call shapes, COM initialization, dynamic library loading, and error translation are centralized in runtime-owned abstractions instead of duplicated in generator or sample code.
- [ ] Object identity and activation closure: verify IUnknown/IInspectable wrappers, QueryInterface/interface casting, runtime-class-name lookup, activation factory lookup/cache, manifest-free fallback, and runtime-class activation helpers against `.cswinrt/src/WinRT.Runtime`.
- [ ] Generic signature/IID closure: verify parameterized type-signature rendering and IID hashing for interfaces, delegates, collections, and async helpers before relying on broader generated output.
- [ ] Delegate/event runtime closure: finish delegate callback lifetime, event token handling, add/remove failure behavior, and ABI ownership rules required by generated event/delegate surfaces.
- [ ] Collection and async runtime closure: finish collection adapters, bindable/observable collection behavior, async operation/action bridges, cancellation/status/error propagation, and ABI marshaling combinations required by projected surfaces.
- [ ] WinUI runtime closure: finish bootstrap, dispatcher/window lifetime, resource loading, dependency property and AutomationPeer runtime hooks only after the owning ABI/object/generator contracts are coherent.
- [ ] Native runtime actuals for `mingwX64`: frozen for the current queue; later implement real COM initialization, WinRT initialization, HRESULT/error-info translation, dynamic library loading, vtable invocation, callback/upcall plumbing, pointer ownership, object identity, activation, delegates, events, collections, and async in target source sets.

## Phase 2 Metadata

- [ ] Metadata input expansion parity: complete file, directory, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache, and deterministic model behavior required by Kotlin builds, using `.cswinrt/src/cswinrt` input expansion as evidence.
- [ ] Native WinMD coverage: harden native WinMD parsing across SDK, WinAppSDK, authored component, and third-party metadata shapes; keep invalid metadata diagnostics fail-closed.
- [ ] Symbol fidelity closure: finish generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, property/event accessor identity, parameter passing semantics, custom attributes, and unsupported metadata diagnostics before broadening projection output.
- [ ] Projection-shape input closure: centralize mapped-type, primitive, inspectable/interface, collection, bindable, async, runtime-class-name, and attribute classification so generator/runtime code does not repeat local branch tables.
- [ ] Metadata cache/model closure: ensure normalized models are deterministic, reusable by Gradle cacheable tasks, and explicit about all inputs that affect generated Kotlin output.

## Phase 3 Generator And Compiler Plugin

- [ ] Generator declaration planning: keep declaration ownership, namespace/type shells, companion/metadata surfaces, and deterministic ordering as the first generator responsibility before member emission expands.
- [ ] Generator projection matrix closure 正在做: finish Kotlin-relevant activation, static, factory, custom mapped type, nullable reference, required/default interface, generic instantiation, collection, async, delegate/event, struct, array, attribute, and unsupported-shape behavior.
- [x] ABI array contract closure: verify arrays always carry exactly one renderable element ABI binding before projection rendering, including nested generic/array cases and diagnostics that fail before marshaler fallback paths.
- [ ] Member emission closure: finish method, property, event, overload, accessor, out/ref, nullable, static, factory, and activation member rendering against `.cswinrt/src/cswinrt` responsibility split.
- [ ] Custom mapped type closure: align string, object, type-name, date/time, guid, uri, collection, bindable, WinUI-specific mapped types, and ABI/projection conversions with CsWinRT behavior where the Kotlin runtime owns equivalent behavior.
- [ ] Generic instantiation closure: finish projected generic interface/delegate naming, type-signature rendering, parameterized IID use, helper emission, and nested generic ABI ownership.
- [ ] Collection and async projection closure: finish generated collection, observable/bindable, iterable/vector/map, async action/operation/progress surfaces, helper support, and runtime bridge calls after runtime contracts are complete.
- [ ] Delegate and event projection closure: finish delegate `Invoke`, event add/remove, event-source helper support, token ownership, callback marshaling, and fail-closed diagnostics for unsupported delegate shapes.
- [ ] Struct and enum projection closure: finish nested structs, fixed/array fields where supported, enum underlying types, ABI layouts, and unsupported field diagnostics.
- [ ] Activation/static/factory closure: finish runtime class activation, static interface companions, factory/composable factory surfaces, required interface caches, and generated support files.
- [ ] Attribute/source-generator-equivalent closure: implement Kotlin annotation and authored metadata transformation through `winrt-generator` and `winrt-compiler-plugin`; do not add runtime reflection discovery.
- [ ] Compiler-plugin IR closure: move semantic authored type discovery, descriptor-backed projection lowering, native-call lowering, and support artifact generation onto K2/IR symbols where needed for functional parity.
- [ ] Expect/actual closure: remove common fallback gates for async, collections, custom mapped types, static members, events, setter-only properties, and unsupported ABI shapes only after both JVM and later `mingwX64` contracts exist.
- [ ] Unsupported-shape diagnostics: fail closed with stable diagnostics when metadata or generator input requires unsupported ABI, projection, authoring, or platform behavior.
- [ ] Generated support-file closure: verify helper/init/metadata support emission against CsWinRT responsibility split, including activation factories, static interfaces, delegate bridges, generic interface helpers, event tokens, and marshaling support.

## Phase 4 Projections

- [ ] Broad projection generation: generate Windows and WinAppSDK slices through plugin-owned deterministic output instead of hand-maintained checked-in growth.
- [ ] Dependency projection ownership: keep identity/suppression files as the cross-module ownership boundary so application modules do not duplicate dependency-owned projected FQNs.
- [ ] Projection breadth control: expand checked-in generated output only after the corresponding runtime, metadata, and generator contracts exist for the same feature; do not shrink or delete generated surfaces as a substitute for missing generator coverage.
- [ ] Generated-output audits: scan generated projection source/classes for forbidden runtime fallback, reflection/proxy, stale support registries, duplicate type/category tables, duplicate FQNs, and unexpected source-size growth before expanding projection breadth.

## Phase 5 Authoring

- [ ] Authoring completeness: finish factories, activation, hosting, metadata, receive-array variants, inherited/overridable interfaces, ABI marshaling combinations, and validation beyond the current JVM WinUI happy path.
- [ ] Compiler-plugin-backed authoring metadata: ensure authored metadata and registration are generated from compiler-visible symbols rather than runtime reflection or string-only source scanning.
- [ ] Authoring ABI boundary: define what authored types expose across the ABI boundary, how factories are surfaced, and how generated metadata connects to runtime ownership before expanding samples.
- [ ] Native authoring host: frozen for the current queue; later implement native CCW/host behavior only after native runtime ABI/object identity and JVM authoring contracts are stable enough to port directly.

## Phase 6 Packaging And Validation

- [x] Kotlin appx/msix packaging baseline: Kotlin-owned manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, packaged/unpackaged modes, disabled-generation input skipping/output preservation, and Gradle DSL ergonomics are complete for the current JVM packaging surface without cloning full MSBuild.
- [ ] Packaging functional closure: verify remaining appx/msix layout, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout against Kotlin application needs, using CsWinRT/MSBuild behavior only as implementation evidence.
- [ ] Gradle cache closure: keep configuration cache and build cache enabled for touched runtime, metadata, generator, authoring, packaging, and sample tasks; any cache miss/error caused by task modeling is a bug to fix.
- [ ] Representative Windows validation: validate touched runtime, metadata, generator, compiler-plugin, authoring, packaging, and JVM slices on Windows; samples remain validation surfaces, not design sources.
- [ ] Native validation: frozen for the current queue; later add `mingwX64` validation only after native runtime/projection contracts are implemented.

## Frozen Until Prerequisites Close

- [ ] `winrt-samples`: only validate completed runtime/generator/authoring slices; no sample-local runtime workarounds.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output gated by completed upstream contracts.
- [ ] `mingwX64`: full native runtime/projection parity is deliberately deferred for the current work queue; shared contracts must remain native-viable.
- [ ] Codebase slimming: frozen as an implementation target until runtime, metadata, generator/compiler-plugin, projection, authoring, and Kotlin appx/msix packaging are functionally complete. Do not spend current work on source-count reduction, deletion-only refactors, or module-collapse cleanup; premature slimming would hide missing behavior and will likely re-expand when the remaining parity gaps are implemented.

## Validation Gates

- [ ] Windows-first validation: run targeted Windows Gradle validation for the module touched by each slice before broader sample or integration runs.
- [ ] Cache validation gate: keep configuration cache and build cache enabled for WinRT generation and packaging checks; failures must be fixed in task inputs/actions or Gradle service ownership.
- [ ] Runtime gate: validate ABI, activation, object identity, marshaling, delegate/event, collection, async, and WinUI bootstrap behavior in `winrt-runtime` before expanding dependent generator/projection slices.
- [ ] Metadata gate: validate native WinMD ingestion, normalized symbols, signatures, accessors, custom attributes, and diagnostics in `winrt-metadata` before using those facts in generator output.
- [ ] Generator/compiler-plugin gate: validate deterministic output, descriptor intrinsic lowering, compiler-support aggregation, no reflection/proxy fallback, and no stale support registries before checking generated projection growth.
- [ ] Authoring gate: validate generated TypeDetails, authored WinMD, CCW factories, activation/hosting, receive-array handling, and WinUI composable boundaries before broadening authoring samples.
- [ ] Packaging gate: validate PRI/MRT/resource/package staging and appx/msix layout through Kotlin Gradle tasks, with CsWinRT/MSBuild behavior used only as reference evidence.
- [ ] Generated-output audit: scan generated source/classes for `ComVtableInvoker` fallback, `invokeGenericArgs`, `Class.forName`, `Proxy.newProxyInstance`, `java.lang.reflect`, stale registry artifacts, duplicate FQNs, duplicate type/category branch tables, and unexpected source-size growth.
- [ ] Sample role: use `winrt-samples` and KMP WinUI app runs only as representative end-to-end validation after the owning runtime, metadata, generator, authoring, or packaging slice is designed.
