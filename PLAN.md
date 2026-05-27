# Plan

## Planning Rules

- [ ] `.cswinrt/` is the implementation baseline for runtime, metadata, generator, authoring, projections, packaging evidence, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Tests validate `.cswinrt` parity; do not derive runtime or generator rules from sample failures.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Keep Gradle configuration cache and build cache enabled; cache failures are implementation issues to fix, not flags to bypass.
- [ ] Keep `mingwX64` implementation frozen for the current queue. Shared contracts must stay native-viable, but no native actual work starts until the JVM/generator/packaging focus items are closed.
- [ ] Keep codebase slimming frozen until functional completeness is reached. Current work may remove duplication only when that is required to implement a parity slice; do not run deletion-only refactors, source-count targets, module-collapse work, or standalone slimming plans before projection behavior is complete.

## Reference Boundaries

- [ ] CsWinRT is the projection-behavior reference only: use `.cswinrt/` to derive WinRT observable behavior, metadata interpretation, generated surface shape, ABI ownership, authoring contracts, and packaging evidence.
- [ ] Do not treat CsWinRT `netstandard` / modern `.NET` target differences as Kotlin goals; those environment splits do not map to Kotlin projection completeness.
- [ ] Kotlin has no runtime reflection equivalent for CsWinRT's attribute/source-generator discovery path. Equivalent annotation, metadata, registration, or source-generator behavior must be implemented through `winrt-generator` and `winrt-compiler-plugin`, with runtime discovery kept fail-closed.
- [ ] WinMD ingestion targets native WinMD metadata only. Do not add `cswinmd`-specific compatibility as a projection goal.
- [ ] Packaging target is complete Kotlin `appx` / `msix` application packaging, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout. CsWinRT/MSBuild targets are implementation evidence, not a requirement to clone full MSBuild.

## Current Focus Queue

- [x] Generator projection matrix closure: close ABI/member/metadata fail-closed gaps for activation, static/factory, delegates/events, structs, arrays, custom mapped types, collections, async, generic instantiation, and unsupported shapes before broad projection growth.
- [x] Metadata input and native WinMD fidelity: finish Kotlin-needed SDK/file/directory/reference expansion and harden native WinMD parsing; do not add `cswinmd` compatibility.
- [x] Compiler-plugin authoring and projection lowering: continue descriptor-backed K2/IR lowering only where it closes functional projection or authoring behavior, not as a source-count reduction task.
- [x] Kotlin appx/msix packaging validation: keep completed packaging behavior cache-compatible while closing any remaining package/layout/resource gaps discovered by functional validation.
- [ ] Phase 5 authoring parity 正在做: replace the current Gradle source-scanner/TSV handoff with compiler-visible K2/IR authored metadata, then close the authoring ABI boundary, factory chaining, receive-array, inherited/overridable interface, and marshaling gaps against `.cswinrt/src/Authoring`.
- [ ] Functional completeness before structure cleanup: close runtime, metadata, generator/compiler-plugin, projection, authoring, and Kotlin appx/msix packaging gaps in this plan before any source-count, module-shrinking, or deletion-driven cleanup becomes eligible.

## Current Completed Baseline

- [x] Runtime JVM baseline: core ABI primitives, object references, activation, CCW/RCW ownership, delegate/event/collection/object marshaling, async bridges, WinUI bootstrap, and vtable-call lowering exist as a usable JVM validation base.
- [x] Metadata baseline: native WinMD ingestion, deterministic model construction, signatures, accessors, generic/default-interface metadata, custom attributes, mapped types, and writer-handoff descriptors exist for the current generator path.
- [x] Generator/compiler-plugin baseline: deterministic projection generation, support-file handoff, owner-local `NativeProjection` bodies, descriptor intrinsic lowering, compiler-support aggregation, reflection/proxy removal, generic ABI registry cleanup, and Gradle cache-compatible task modeling exist for current JVM work.
- [x] Generator fail-closed baseline: missing or incomplete ABI metadata is rejected before rendering for the already-covered activation/static/factory, default/implemented/required interface, delegate/event, enum/struct/array, property, method, factory-create, composable factory shape, authored CCW member, collection owner, mapped collection arity, mapped async arity, and ABI call-plan slices.
- [x] Authoring JVM baseline: generated TypeDetails, authored WinMD emission, CCW factories, host manifests, activation factory exports, composable WinUI subclasses, receive-array handling, and JVM host bridge slices exist for current validation.
- [x] Packaging/resource baseline: Kotlin-owned appx/msix staging, manifest validation, dependency payloads, PRI/MRT/resource processing, signing/test-install hooks, and makeappx verification exist for the current JVM sample surface.

## Functional Completeness Gaps

- [ ] Runtime gaps: complete CsWinRT-aligned audits and fixes for ABI ownership, activation cache/fallback, object identity, marshaling combinations, delegate/event lifetime, collection adapters, async status/error/cancellation, HRESULT/error-info, and WinUI bootstrap hooks.
- [ ] Metadata gaps: complete Kotlin-needed WinMD input expansion, SDK/reference resolution, native WinMD edge cases, generic/default/static/factory symbol fidelity, accessor identity, parameter semantics, custom attributes, deterministic cache inputs, and unsupported metadata diagnostics.
- [ ] Generator gaps: close remaining declaration/member matrix coverage for activation/static/factory, custom mapped types, nullable/reference ownership, required/default interfaces, generic instantiations, collections, async, delegates/events, structs/enums/arrays, attributes, and unsupported shapes.
- [ ] Compiler-plugin gaps: move authored type discovery, annotation/source-generator-equivalent behavior, descriptor-backed projection lowering, native-call lowering, and support artifact generation onto compiler-visible K2/IR symbols where runtime reflection or Roslyn source-generator symbols would be used in CsWinRT.
- [ ] Projection gaps: generate Windows/WinAppSDK slices through deterministic plugin-owned output only after the owning runtime, metadata, and generator contracts exist; keep checked-in projection breadth gated by functional coverage.
- [ ] Authoring gaps: finish the CsWinRT component-authoring chain: compiler-symbol-authored WinMD/type metadata, generated exposed TypeDetails, dependency activation-factory chaining, activation/hosting, receive-array variants, inherited/overridable interfaces, ABI marshaling combinations, and validation beyond the current JVM WinUI happy path.
- [x] Packaging gaps: finish Kotlin appx/msix layout, manifest/resource/PRI/MRT details, dependency payload staging, signing/test-install flow, and runtime asset layout without cloning full MSBuild.
- [x] Validation gaps: keep Windows Gradle configuration cache and build cache enabled for every touched slice, then run representative runtime/metadata/generator/plugin/authoring/packaging/sample checks only after the owning contracts are implemented.
- [ ] Post-completeness structure cleanup: frozen until the functional gaps above are closed. After completion, audit duplicate type/category tables, obsolete handwritten projections, and module boundaries only as a correctness-preserving cleanup pass, not as a current implementation objective.

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

- [x] Metadata input expansion parity: complete file, directory, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache, and deterministic model behavior required by Kotlin builds, using `.cswinrt/src/cswinrt` input expansion as evidence.
- [x] Native WinMD coverage: harden native WinMD parsing across SDK, WinAppSDK, authored component, and third-party metadata shapes; keep invalid metadata diagnostics fail-closed.
- [x] Symbol fidelity closure: finish generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, property/event accessor identity, parameter passing semantics, custom attributes, and unsupported metadata diagnostics before broadening projection output.
- [x] Projection-shape input closure: centralize mapped-type, primitive, inspectable/interface, collection, bindable, async, runtime-class-name, and attribute classification so generator/runtime code does not repeat local branch tables.
- [x] Metadata cache/model closure: ensure normalized models are deterministic, reusable by Gradle cacheable tasks, and explicit about all inputs that affect generated Kotlin output.

## Phase 3 Generator And Compiler Plugin

- [x] Generator declaration planning: keep declaration ownership, namespace/type shells, companion/metadata surfaces, and deterministic ordering as the first generator responsibility before member emission expands.
- [x] Generator projection matrix closure: finish Kotlin-relevant member/property/event emission, activation/static/factory/composable surfaces, custom mapped types, nullable/reference ownership, required/default interfaces, generics, collections, async, delegates/events, structs/enums/arrays, attributes, and unsupported-shape diagnostics.
- [x] ABI metadata contract closure: fail closed before rendering when ABI arrays, generic arity, mapped collection/reference/key-value/async/delegate shapes, type signatures, event accessor pairs, static accessors, factory create methods, or ABI call plans cannot be represented by the runtime/generator contract.
- [x] Generic identity closure: render projected generic interface/delegate naming, nested generic signatures, runtime-class required-interface caches, collection helper IIDs, authored CCW generic interface IDs, and object-reference cache acquisition through `WinRtTypeSignature` and `ParameterizedInterfaceId` instead of raw generic IID metadata.
- [x] Collection/async/delegate/event closure: finish mapped collection and async projection helpers, event-source support, delegate `Invoke`, event token ownership, callback marshaling, reference adapters, and fail-closed diagnostics for malformed or unsupported helper shapes.
- [x] Authoring support-renderer closure: fail closed before rendering authored activation factory plans, TypeDetails, CCW factories, metadata type mappings, interface plans, slot metadata, member bodies, factory member references, and host/runtime asset manifests when required metadata is missing or malformed.
- [x] Compiler-support handoff closure: validate compiler-support manifests, projection registrar rows, generic instantiation rows, generic ABI registry rows, authored candidate TSV, authoring metadata index rows, dependency merge inputs, stale class cleanup, helper-symbol resolution, duplicate rows, required columns, schemas, list fields, and declared files before compiler-plugin lowering consumes generated support artifacts.
- [x] Compiler-plugin IR closure: use K2/IR for descriptor-backed projection lowering, native-call lowering, support artifact initialization, projection registrar initialization, generic support registration, and authored constructor/type-details registration where functional parity requires compiler-visible symbols.
- [ ] Expect/actual closure: remove common fallback gates for async, collections, custom mapped types, static members, events, setter-only properties, and unsupported ABI shapes only after both JVM and later `mingwX64` contracts exist.
- [x] Generated support-file closure: verify helper/init/metadata support emission against CsWinRT responsibility split, including activation factories, static interfaces, delegate bridges, generic interface helpers, event tokens, and marshaling support.

## Phase 4 Projections

- [ ] Broad projection generation: generate Windows and WinAppSDK slices through plugin-owned deterministic output instead of hand-maintained checked-in growth.
- [x] Dependency projection ownership: keep identity/suppression files as the cross-module ownership boundary so application modules do not duplicate dependency-owned projected FQNs.
- [x] Projection breadth control: expand checked-in generated output only after the corresponding runtime, metadata, and generator contracts exist for the same feature; do not shrink or delete generated surfaces as a substitute for missing generator coverage.
- [x] Generated-output audits: scan generated projection source/classes for forbidden runtime fallback, reflection/proxy, stale support registries, duplicate type/category tables, duplicate FQNs, and unexpected source-size growth before expanding projection breadth.

## Phase 5 Authoring

- [x] JVM authoring skeleton: generated TypeDetails, authored WinMD emission, CCW factories, host manifests, activation factory exports, composable WinUI subclasses, receive-array happy-path handling, and JNI-backed host DLL generation exist as a usable validation base.
- [ ] Compiler-symbol authored metadata 正在做: move authored runtime-class discovery, interface/base/factory/static/composable metadata, inherited/overridable interface closure, visibility, constructor/member validation, and authored WinMD generation from Gradle source scanning plus TSV handoff into compiler-visible K2/IR symbols, matching CsWinRT's Roslyn `WinRT.SourceGenerator` responsibility.
- [ ] Authoring module ownership: move the authoring contract center into `winrt-authoring` so the CsWinRT `.cswinrt/src/Authoring` responsibility is not split implicitly across `winrt-gradle-plugin`, `winrt-generator`, `winrt-runtime`, and `winrt-compiler-plugin`.
- [ ] Authoring ABI boundary: define and validate what authored Kotlin types expose as CCWs, how generated TypeDetails map implementation types to metadata types, how factories/static/composable factories are surfaced, how object lifetime/identity is owned, and how generated metadata connects to runtime ownership before expanding samples.
- [ ] Dependency activation-factory chaining: implement the CsWinRT-equivalent of referenced component activation-factory merge so authored components can delegate `DllGetActivationFactory` lookups across dependency exports instead of only relying on local host manifests.
- [ ] Authoring marshaling matrix: finish receive-array variants, object/interface/runtime-class parameters and returns, structs, arrays, delegates/events, collections, async, nullable/reference shapes, HRESULT/error propagation, and stable fail-closed diagnostics beyond the current JVM WinUI happy path.
- [ ] Inherited and overridable interface parity: validate base runtime-class metadata, default/required/overridable interface exposure, composable class inheritance, and generic interface IDs against `.cswinrt/src/Authoring` and `.cswinrt/src/WinRT.Runtime` before treating authored WinUI subclasses as complete.
- [ ] Authoring analyzers and diagnostics: add Kotlin compiler-plugin diagnostics equivalent to CsWinRT authoring analyzers for unsupported exposed shapes, invalid runtime-class casts, invalid collection/array authoring patterns, and source-generator contract violations.
- [ ] Native authoring host: frozen for the current queue; later implement native CCW/host behavior only after native runtime ABI/object identity and JVM authoring contracts are stable enough to port directly.

## Phase 6 Packaging And Validation

- [x] Kotlin appx/msix packaging baseline: Kotlin-owned manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, packaged/unpackaged modes, disabled-generation input skipping/output preservation, and Gradle DSL ergonomics are complete for the current JVM packaging surface without cloning full MSBuild.
- [x] Packaging functional closure: verify remaining appx/msix layout, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout against Kotlin application needs, using CsWinRT/MSBuild behavior only as implementation evidence.
- [x] Gradle cache closure: keep configuration cache and build cache enabled for touched runtime, metadata, generator, authoring, packaging, and sample tasks; any cache miss/error caused by task modeling is a bug to fix.
- [x] Representative Windows validation: validate touched runtime, metadata, generator, compiler-plugin, authoring, packaging, and JVM slices on Windows; samples remain validation surfaces, not design sources.
- [ ] Native validation: frozen for the current queue; later add `mingwX64` validation only after native runtime/projection contracts are implemented.

## Frozen Until Prerequisites Close

- [ ] `winrt-samples`: only validate completed runtime/generator/authoring slices; no sample-local runtime workarounds.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output gated by completed upstream contracts.
- [ ] `mingwX64`: full native runtime/projection parity is deliberately deferred for the current work queue; shared contracts must remain native-viable.
- [ ] Codebase slimming and standalone structure reduction: frozen as an implementation target until runtime, metadata, generator/compiler-plugin, projection, authoring, and Kotlin appx/msix packaging are functionally complete. Current work must not spend effort on source-count reduction, deletion-only refactors, or module-collapse cleanup; premature slimming would hide missing behavior and will likely re-expand when the remaining parity gaps are implemented.

## Validation Gates

- [x] Windows-first validation: run targeted Windows Gradle validation for the module touched by each slice before broader sample or integration runs.
- [x] Cache validation gate: keep configuration cache and build cache enabled for WinRT generation and packaging checks; failures must be fixed in task inputs/actions or Gradle service ownership.
- [ ] Runtime gate: validate ABI, activation, object identity, marshaling, delegate/event, collection, async, and WinUI bootstrap behavior in `winrt-runtime` before expanding dependent generator/projection slices.
- [ ] Metadata gate: validate native WinMD ingestion, normalized symbols, signatures, accessors, custom attributes, and diagnostics in `winrt-metadata` before using those facts in generator output.
- [ ] Generator/compiler-plugin gate: validate deterministic output, descriptor intrinsic lowering, compiler-support aggregation, no reflection/proxy fallback, and no stale support registries before checking generated projection growth.
- [ ] Authoring gate: validate generated TypeDetails, authored WinMD, CCW factories, activation/hosting, receive-array handling, and WinUI composable boundaries before broadening authoring samples.
- [x] Packaging gate: validate PRI/MRT/resource/package staging and appx/msix layout through Kotlin Gradle tasks, with CsWinRT/MSBuild behavior used only as reference evidence.
- [x] Generated-output audit: scan generated source/classes for `ComVtableInvoker` fallback, `invokeGenericArgs`, `Class.forName`, `Proxy.newProxyInstance`, `java.lang.reflect`, stale registry artifacts, duplicate FQNs, duplicate type/category branch tables, and unexpected source-size growth.
- [ ] Sample role: use `winrt-samples` and KMP WinUI app runs only as representative end-to-end validation after the owning runtime, metadata, generator, authoring, or packaging slice is designed.
