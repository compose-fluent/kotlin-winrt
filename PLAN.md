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

- [ ] Generator projection matrix closure 正在做: close ABI/member/metadata fail-closed gaps for activation, static/factory, delegates/events, structs, arrays, custom mapped types, collections, async, generic instantiation, and unsupported shapes before broad projection growth.
- [ ] Metadata input and native WinMD fidelity: finish Kotlin-needed SDK/file/directory/reference expansion and harden native WinMD parsing; do not add `cswinmd` compatibility.
- [ ] Compiler-plugin authoring and projection lowering: continue descriptor-backed K2/IR lowering only where it closes functional projection or authoring behavior, not as a source-count reduction task.
- [ ] Kotlin appx/msix packaging validation: keep completed packaging behavior cache-compatible while closing any remaining package/layout/resource gaps discovered by functional validation.
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
- [ ] Compiler-plugin gaps: move authored type discovery, annotation/source-generator-equivalent behavior, descriptor-backed projection lowering, native-call lowering, and support artifact generation onto compiler-visible K2/IR symbols where runtime reflection would be used in CsWinRT.
- [ ] Projection gaps: generate Windows/WinAppSDK slices through deterministic plugin-owned output only after the owning runtime, metadata, and generator contracts exist; keep checked-in projection breadth gated by functional coverage.
- [ ] Authoring gaps: finish factories, activation/hosting, authored metadata, receive-array variants, inherited/overridable interfaces, ABI marshaling combinations, and validation beyond the current JVM happy path.
- [ ] Packaging gaps: finish Kotlin appx/msix layout, manifest/resource/PRI/MRT details, dependency payload staging, signing/test-install flow, and runtime asset layout without cloning full MSBuild.
- [ ] Validation gaps: keep Windows Gradle configuration cache and build cache enabled for every touched slice, then run representative runtime/metadata/generator/plugin/authoring/packaging/sample checks only after the owning contracts are implemented.
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

- [ ] Metadata input expansion parity: complete file, directory, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache, and deterministic model behavior required by Kotlin builds, using `.cswinrt/src/cswinrt` input expansion as evidence.
- [ ] Native WinMD coverage: harden native WinMD parsing across SDK, WinAppSDK, authored component, and third-party metadata shapes; keep invalid metadata diagnostics fail-closed.
- [ ] Symbol fidelity closure: finish generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, property/event accessor identity, parameter passing semantics, custom attributes, and unsupported metadata diagnostics before broadening projection output.
- [ ] Projection-shape input closure: centralize mapped-type, primitive, inspectable/interface, collection, bindable, async, runtime-class-name, and attribute classification so generator/runtime code does not repeat local branch tables.
- [ ] Metadata cache/model closure: ensure normalized models are deterministic, reusable by Gradle cacheable tasks, and explicit about all inputs that affect generated Kotlin output.

## Phase 3 Generator And Compiler Plugin

- [ ] Generator declaration planning: keep declaration ownership, namespace/type shells, companion/metadata surfaces, and deterministic ordering as the first generator responsibility before member emission expands.
- [ ] Generator projection matrix closure 正在做: finish Kotlin-relevant activation, static, factory, custom mapped type, nullable reference, required/default interface, generic instantiation, collection, async, delegate/event, struct, array, attribute, and unsupported-shape behavior.
- [x] ABI array contract closure: verify arrays always carry exactly one renderable element ABI binding before projection rendering, including nested generic/array cases and diagnostics that fail before marshaler fallback paths.
- [x] Authored CCW fail-closed closure: validate unsupported authored CCW ABI member bindings before support-file rendering instead of emitting generated `E_NOTIMPL` fallback handlers for shapes the authoring ABI cannot marshal.
- [x] Collection owner fail-closed closure: reject mapped collection owner interfaces whose element, key, or value ABI shape cannot be projected before silently dropping the Kotlin collection bridge.
- [x] Mapped collection arity fail-closed closure: reject malformed `IIterable`, `IVector`, `IVectorView`, `IMap`, `IMapView`, and non-generic WinUI bindable collection ABI bindings before collection projection rendering.
- [x] Mapped async arity fail-closed closure: reject malformed `IAsyncActionWithProgress`, `IAsyncOperation`, and `IAsyncOperationWithProgress` ABI bindings before async projection rendering.
- [x] Composable factory shape fail-closed closure: reject malformed composable factory create methods that do not end with `baseInterface` and `innerInterface` object ABI parameters before constructor/support rendering.
- [x] Static property accessor fail-closed closure: validate static getter and setter ABI bindings independently so setter-only metadata cannot bypass `STATIC_*_SETTER_SLOT` contract checks before projection rendering.
- [x] Composable factory create filtering closure: render composable constructors and factory helpers only from factory methods returning the current runtime class, matching the generator contract validator.
- [x] Projected attribute fail-closed closure: reject projected attributes whose metadata arguments cannot render as Kotlin annotations instead of silently dropping them with `mapNotNull`.
- [x] Projected generic arity fail-closed closure: reject projected interface and delegate ABI bindings whose generic argument count does not match metadata before generic IID/signature rendering.
- [x] Mapped key-value pair arity fail-closed closure: reject malformed `IKeyValuePair` / `Map.Entry` ABI bindings without exactly two type arguments before projection rendering.
- [x] Mapped reference arity fail-closed closure: reject malformed `IReference` / `IReferenceArray` ABI bindings without exactly one type argument and parameterized-interface IID before projection rendering.
- [x] Mapped event delegate arity fail-closed closure: reject malformed mapped event delegates such as `EventHandler`, `TypedEventHandler`, collection-change, progress, property-change, and notify-collection-change handlers with the wrong generic argument count before event-source or add/remove projection rendering.
- [x] Event-source helper fail-closed closure: reject event projections whose generated event property would reference a `WinRTEventProjectionHelpers` helper that cannot be emitted for the delegate invoke shape before support-file rendering silently skips it.
- [x] Generic type-signature fail-closed closure: reject projected generic interface type signatures whose nested ABI arguments cannot render instead of falling back to a non-parameterized IID signature.
- [x] Authoring activation factory support fail-closed closure: reject authored activation factory support plans whose activatable/static/composable factory interfaces are missing before `WinRTAuthoringActivationFactoryPlan` silently drops factory member references.
- [x] Authoring activation factory IID fail-closed closure: reject authored activation factory support plans whose descriptor-only factory/static/composable interfaces lack metadata IID before server factory/support rendering depends on those interfaces.
- [x] Authored CCW method slot metadata closure: reject authored CCW support for implemented interface methods whose ABI slot metadata is missing before `WinRTAuthoringCcwFactories` emits handlers with unknown vtable ordering.
- [x] Authored CCW accessor slot metadata closure: reject authored CCW support for implemented interface property/event accessors whose ABI slot metadata is missing before `WinRTAuthoringCcwFactories` emits handlers with unknown vtable ordering.
- [x] Authored CCW interface plan closure: reject authored runtime classes whose implemented CCW interfaces do not have projection plans before `WinRTAuthoringCcwFactories` silently emits empty method tables.
- [x] Authoring activation factory generic member-reference closure: resolve generic factory interface references through their raw metadata type before rendering `WinRTAuthoringActivationFactoryPlan` member lists.
- [x] Authoring activation factory member-reference fail-closed closure: reject support rendering when factory member references name a missing factory interface instead of emitting empty member lists.
- [x] Authored CCW support-renderer interface plan closure: reject support rendering when authored CCW interface definitions lack projection plans instead of emitting empty method tables.
- [x] Authored CCW support-renderer slot metadata closure: reject support rendering when authored CCW method tables lack ABI slot metadata instead of sorting unsupported members last.
- [x] Authored CCW generic interface IID closure: render authored CCW implemented/default generic interface IDs through `WinRtTypeSignature` and `ParameterizedInterfaceId` instead of collapsing `IBox<T>` instances to raw interface IID metadata.
- [x] Runtime-class required generic interface IID closure: render runtime-class required-interface cache acquisition for substituted generic closure interfaces through `WinRtTypeSignature` and `ParameterizedInterfaceId` instead of querying raw required interface IIDs.
- [x] Interface native collection generic IID closure: render interface `NativeProjection` collection-owner cache acquisition through generic collection signatures and `ParameterizedInterfaceId` instead of querying raw collection interface IIDs.
- [x] Required iterator helper IID closure: render runtime-class required `IIterator<T>` helper cache acquisition through `WinRtCollectionInterfaceIds.iteratorSignature` and `ParameterizedInterfaceId` instead of querying raw iterator IID metadata.
- [x] Runtime-class collection generic IID closure: render runtime-class mapped collection cache acquisition through generic collection signatures and `ParameterizedInterfaceId` instead of querying raw `IVector`/`IMap` IID metadata.
- [x] Runtime-class generic object-reference cache coverage: verify descriptor-driven generic object-reference caches initialize generic instantiations and acquire interfaces through `ParameterizedInterfaceId` instead of raw generic interface IID metadata.
- [x] Runtime-class generic object-reference fail-closed closure: reject descriptor-driven generic object-reference caches whose interface instance cannot render a WinRT type signature instead of falling back to raw generic interface IID metadata.
- [x] Async result type-signature fail-closed closure: reject mapped async operation/progress result or progress arguments whose WinRT type signature cannot render instead of silently falling back from async projection helpers.
- [x] Collection reference-adapter fail-closed closure: reject mapped collection parameters and returns whose element, key, or value binding cannot produce a runtime `WinRtReferenceValueAdapter` before renderer paths silently fall back from collection projection helpers.
- [x] Event accessor pair fail-closed closure: reject projected instance/static events without complete add/remove accessor metadata, and only plan static event bindings from the real accessor side present in metadata before event-source helper rendering.
- [x] Reference type-signature fail-closed closure: reject `IReference<T>` / `IReferenceArray<T>` bindings whose element argument cannot render a WinRT type signature before reference marshaler or readback helpers silently fall back.
- [x] Key-value pair type-signature fail-closed closure: reject `IKeyValuePair<K, V>` bindings whose key or value argument cannot render a WinRT type signature before collection adapter or generic signature helpers silently fall back.
- [x] Generic delegate type-signature fail-closed closure: reject parameterized delegate bindings whose generic arguments cannot render WinRT type signatures before delegate parameterized IID helper rendering silently falls back.
- [x] ABI call-plan fail-closed closure: validate instance and static member ABI call plans before member rendering so unsupported marshaler shapes fail with stable diagnostics instead of surfacing from renderer fallback paths.
- [x] Static property ABI call-plan fail-closed closure: validate static property getter/setter ABI call plans through their `STATIC_*_GETTER/SETTER_SLOT` contracts before static property rendering can surface unsupported marshaler fallbacks.
- [x] Static event accessor ABI contract closure: validate static event add/remove bindings through their `STATIC_*_ADD/REMOVE_SLOT` contracts before event-source helper or renderer fallback paths can surface raw static-interface diagnostics.
- [x] Event accessor ABI call-plan closure: validate instance and static event add/remove bindings through their accessor slot contracts before event-source helper or renderer fallback paths can surface unsupported delegate marshaler diagnostics.
- [x] Authoring metadata projection-plan fail-closed closure: reject support rendering when authored metadata type mappings do not have matching projection plans instead of silently emitting incomplete authoring support files.
- [x] Compiler support handoff fail-closed closure: reject malformed compiler-support, projection registrar, generic instantiation, and generic ABI registry TSV rows instead of silently dropping generator handoff entries in the compiler plugin.
- [x] Compiler support manifest source fail-closed closure: reject compiler-support manifest entries whose declared projection registrar, generic instantiation, or generic ABI registry source files are missing instead of silently skipping those generator handoff inputs.
- [x] Compiler support manifest option fail-closed closure: reject explicitly configured compiler-support manifest paths that do not exist instead of treating a broken generator handoff configuration as no compiler support input.
- [x] Compiler support relative-manifest fail-closed closure: resolve parent-less compiler support manifests against the current directory so declared source inputs cannot be silently skipped.
- [x] Compiler support manifest entry-count fail-closed closure: reject generator/compiler-plugin support TSV files whose actual row count does not match the manifest-declared entry count.
- [x] Compiler support manifest negative-count fail-closed closure: reject negative manifest-declared support entry counts instead of accepting impossible generator handoff totals.
- [x] Compiler support manifest required-column fail-closed closure: reject blank manifest `kind`, `className`, or `sourceFile` columns instead of letting invalid support declarations be filtered, resolved as directories, or emitted into support classes.
- [x] Compiler support manifest kind fail-closed closure: reject manifest `kind` values outside the generator-emitted support table set instead of recording handoff rows that no compiler-plugin reader consumes.
- [x] Compiler support manifest duplicate fail-closed closure: reject duplicate manifest `kind`/`className`/`sourceFile` rows instead of consuming the same generator handoff source more than once.
- [x] Compiler support manifest mapping fail-closed closure: reject manifest `className` or `sourceFile` values that do not match the generator-owned support table mapping for the declared `kind`.
- [x] Compiler support TSV header fail-closed closure: reject compiler-support, projection registrar, generic instantiation, and generic ABI registry TSV files whose headers do not match the generator-owned schema before parsing positional rows.
- [x] Projection support initializer stale-class closure: delete stale content-addressed `WinRTProjectionSupport_*.class` artifacts when compiler support registrar rows change so old generated support initializers cannot remain on the classpath.
- [x] Projection support initializer empty-input stale-class closure: delete stale content-addressed `WinRTProjectionSupport_*.class` artifacts when compiler support registrar rows disappear entirely instead of leaving an old initializer on the classpath.
- [x] Compiler support manifest stale-class closure: delete stale `WinRTCompilerSupportManifest.class` artifacts when compiler support manifest entries disappear entirely instead of leaving an old support manifest on the classpath.
- [x] Projection registrar class-resolution fail-closed closure: reject projection registrar support rows whose Kotlin class cannot be resolved by the compiler plugin instead of silently dropping generated projection type registrations.
- [x] Projection registrar helper-symbol fail-closed closure: reject non-empty projection registrar support input when the compiler plugin cannot resolve its module file or `registerGeneratedProjectionTypeIndex` runtime helper instead of lowering projection support initialization to `Unit`.
- [x] Generic support helper-symbol fail-closed closure: reject non-empty generic instantiation or generic ABI registry support inputs when their compiler-visible helper classes, constructors, functions, or module file cannot be resolved instead of lowering support intrinsics to `Unit`.
- [x] Authoring registrar helper-symbol fail-closed closure: reject authoring support initialization and authored constructor lowering when the generated TypeDetails registrar helper cannot be resolved instead of silently dropping authoring registration.
- [x] Projection registrar required-column fail-closed closure: reject blank projection registrar `kotlinClassName`, `projectedTypeName`, or `kind` columns instead of generating invalid projection support initializer registrations.
- [x] Projection registrar kind fail-closed closure: reject projection registrar `kind` values outside the generator-emitted non-`Unknown` `WinRtTypeKind` set instead of writing invalid support registrations.
- [x] Projection registrar duplicate fail-closed closure: reject duplicate projection registrar `kotlinClassName`/`projectedTypeName` rows instead of generating repeated support initializer registrations.
- [x] Generic type-instantiation delegate-flag fail-closed closure: reject malformed generic instantiation `isDelegate` flags instead of silently treating invalid generator handoff values as interface instantiations.
- [x] Generic type-instantiation required-column fail-closed closure: reject blank generic instantiation `className` or `sourceType` columns instead of accepting invalid generator handoff rows.
- [x] Compiler support list-field fail-closed closure: reject blank elements inside generic instantiation and generic ABI registry support-list columns instead of silently compacting malformed generator handoff lists.
- [x] Generic type-instantiation duplicate fail-closed closure: reject duplicate generic instantiation `sourceType`/`className` rows instead of generating repeated initialization calls from ambiguous handoff data.
- [x] Generic ABI registry kind/column fail-closed closure: reject unknown generic ABI registry `kind` values and blank delegate support columns instead of silently dropping invalid generator handoff rows.
- [x] Generic ABI registry duplicate fail-closed closure: reject duplicate derived-interface and delegate registry rows instead of silently distincting or repeatedly registering generic ABI support entries.
- [x] Authoring metadata index fail-closed closure: reject missing or malformed authored metadata index rows instead of silently disabling authored type discovery in the compiler plugin.
- [x] Authoring scanner source-root fail-closed closure: reject missing authored source roots instead of silently producing an empty compiler-plugin authored type discovery result.
- [x] Authoring scanner argument fail-closed closure: reject scanner path options without values using stable diagnostics instead of surfacing low-level argument indexing failures.
- [x] Authoring metadata index duplicate fail-closed closure: reject duplicate authored metadata index type rows instead of allowing later rows to silently overwrite compiler-plugin discovery inputs.
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
- [ ] Codebase slimming and standalone structure reduction: frozen as an implementation target until runtime, metadata, generator/compiler-plugin, projection, authoring, and Kotlin appx/msix packaging are functionally complete. Current work must not spend effort on source-count reduction, deletion-only refactors, or module-collapse cleanup; premature slimming would hide missing behavior and will likely re-expand when the remaining parity gaps are implemented.

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
