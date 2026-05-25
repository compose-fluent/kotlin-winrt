# Plan

## Rules

- [ ] `.cswinrt/` is the implementation baseline for runtime, metadata, generator, authoring, projections, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Tests validate `.cswinrt` parity; do not derive runtime/generator rules from sample failures.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.

## Reference Boundaries

- [ ] CsWinRT is the projection-behavior reference only: use `.cswinrt/` to derive WinRT observable behavior, metadata interpretation, generated surface shape, ABI ownership, authoring contracts, and packaging evidence; do not track CsWinRT `netstandard` / modern `.NET` target splits as Kotlin goals.
- [ ] Kotlin has no runtime reflection equivalent for CsWinRT's attribute/source-generator discovery path; any equivalent annotation, metadata, registration, or source-generator behavior must be implemented through `winrt-generator` and `winrt-compiler-plugin`, with runtime discovery paths kept fail-closed.
- [ ] WinMD ingestion targets native WinMD metadata only. Do not add `cswinmd`-specific compatibility as a projection goal.
- [ ] Packaging target is complete Kotlin `appx` / `msix` application packaging, PRI/MRT/resource indexing, manifest processing, dependency payload staging, and runtime asset layout. CsWinRT/MSBuild targets are implementation evidence, not a requirement to clone full MSBuild.
- [ ] Code size is a post-completeness constraint, not a current execution workstream: do not start slimming or deletion-only refactors until runtime, metadata, generator, authoring, projection, and Kotlin appx/msix packaging are functionally complete enough to make removal decisions against a stable target. Current work may only block new avoidable bloat such as duplicated branch tables, unnecessary generated source growth, or handwritten projection expansion.

## Current Focus

- [ ] Functional completeness before slimming 正在做: close the missing `.cswinrt`-behavior and Kotlin packaging gaps first; do not spend current effort on source-count reduction, deletion-only refactors, or broad module shrinking because incomplete runtime/generator/packaging behavior would make the code grow again.
- [ ] Kotlin appx/msix packaging closure 正在做: finish application package/resource/PRI/MRT/MSIX logic using CsWinRT/MSBuild behavior as evidence while keeping the Kotlin Gradle plugin as the owner; current slice adds manifest staging/validation, explicit app package payload staging, packaged/unpackaged mode boundaries, fail-closed makeappx package output, signtool signing hooks, and explicit test-install hooks while preserving Gradle configuration/build cache.
- [ ] Generator projection matrix closure: finish activation/static/factory surfaces, delegates/events, async/collection helpers, mapped types, ABI array/struct/member shapes, and unsupported-shape diagnostics before broadening checked-in projection output.
- [ ] Metadata input expansion and native WinMD fidelity: finish Kotlin-needed SDK/file/directory/reference expansion and harden native WinMD parsing; do not add `cswinmd` compatibility.
- [ ] Interface native projection IR migration: continue descriptor-backed compiler-plugin lowering only where it closes functional projection behavior, not as a source-count reduction task.

## Completed Baseline Summary

- [x] Runtime/ABI JVM baseline: HRESULT/GUID/HSTRING, COM object references, activation, object identity, CCW/RCW ownership, delegate/event/collection/object marshaling, async bridges, WinUI bootstrap, and core vtable call lowering have representative JVM validation.
- [x] Metadata baseline: native WinMD ingestion, deterministic model construction, signatures, semantic helpers, accessors, generic/default-interface metadata, custom attributes, mapped types, and writer-handoff descriptors have targeted coverage.
- [x] Generator/compiler-plugin baseline: deterministic projection generation, support-file handoff, owner-local `NativeProjection` bodies, descriptor intrinsic lowering, compiler-support aggregation, reflection/proxy removal, dead generic ABI registry wrapper removal, and Gradle build/configuration cache compatibility are established.
- [x] Authoring JVM baseline: source scanning, generated TypeDetails, authored WinMD emission, CCW factories, host manifests, activation factory exports, composable WinUI subclasses, receive-array handling, and JVM host bridge slices have landed.
- [x] WinUI/sample validation baseline: `winrt-samples` and the KMP WinUI library/application graph validate representative generated WinUI controls, resources, events, focus, AutomationPeer, dependency properties, authored subclasses, and app lifecycle.
- [x] Packaging/resource baseline: Kotlin Gradle application staging now covers WindowsAppSDK payloads, application PRI/MRT generation, manifest-derived metadata, package item classification, resource inventories, component PRI aggregation, and makepri execution for the current JVM WinUI samples.

## Remaining CsWinRT-Behavior Gaps

- [ ] Native runtime parity: implement real `mingwX64` COM/WinRT initialization, vtable invocation, inspectable interop, ownership, HRESULT/error-info translation, callback, delegate, collection, async, and activation behavior instead of temporary TODO actuals.
- [ ] Native projection generation parity: generate `mingwX64` actuals for the same public projection surface and call-shape model as JVM, including runtime classes, interfaces, structs, enums, delegates, events, arrays, mapped collections, async, statics, factories, and composable/authoring boundaries.
- [ ] Metadata input expansion parity: complete direct file/dir, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache/model, and deterministic normalization behavior required by Kotlin builds, using `.cswinrt/src/cswinrt` input expansion as the reference.
- [ ] Native WinMD dialect coverage: harden the loader against native WinMD edge cases in SDK, WinAppSDK, authored component, and third-party metadata while keeping invalid metadata diagnostics fail-closed.
- [ ] Generator projection matrix closure: finish Kotlin-relevant projection behavior for activation/static/factory surfaces, custom mapped types, nullable references, required/default interfaces, generic instantiations, collection helpers, async helpers, delegates/events, structs, arrays, attributes, and unsupported-shape diagnostics.
- [ ] Compiler-plugin authoring/annotation transformation: replace remaining light-tree/string or runtime-style discovery assumptions with K2/IR symbol extraction and generated/lowered support for authored metadata, registration, and projection initialization.
- [ ] Async/collections/custom-mapped/static/event expect-actual closure: remove remaining common fallback gates only after the matching metadata, runtime, generator, and compiler-plugin contracts exist for both JVM and native.
- [ ] Projection breadth control: generate broad Windows/WinAppSDK slices through plugin-owned deterministic output, avoid hand-maintained projection growth, and keep dependency projection identity/suppression as the ownership boundary.
- [ ] Authoring completeness: extend beyond the current JVM WinUI happy path to full factories/activation/hosting/metadata/receive-array shapes, inherited/overridable interfaces, ABI marshaling combinations, and later native host support.
- [ ] Kotlin appx/msix packaging completeness: finish manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, unpackaged vs packaged modes, and Gradle DSL ergonomics without attempting full MSBuild parity.
- [ ] Validation sweep: run generated-output audits and representative Windows validation for runtime, metadata, generator, compiler-plugin, authoring, packaging, JVM, and native slices; samples remain validation surfaces, not sources of truth.

## Implementation Plan By Phase

### Phase 1 Runtime And ABI

- [ ] Interface native projection IR migration: keep public projection behavior source-compatible while moving eligible JVM vtable calls into descriptor-backed compiler-plugin lowering only where it is needed to close projection behavior; do not add runtime reflection, JVM Proxy, or classpath discovery paths.
- [ ] Native runtime actuals for mingwX64: implement real COM initialization, WinRT initialization, HRESULT/error-info translation, dynamic library loading, vtable invocation, callback/upcall plumbing, and pointer ownership in winrt-runtime target source sets.
- [ ] Native object identity and activation: implement IUnknown/IInspectable identity, QueryInterface, runtime-class-name lookup, activation factory lookup/cache, manifest-free fallback, and COM reference ownership for mingwX64.
- [ ] Native delegates, events, collections, and async: port the JVM-validated ownership rules to native after the native ABI primitives and object identity layer are complete.

### Phase 2 Metadata

- [ ] Metadata input expansion parity: complete file, directory, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache, and deterministic model behavior required by Kotlin builds, using .cswinrt/src/cswinrt input expansion as evidence.
- [ ] Native WinMD coverage: harden native WinMD parsing across SDK, WinAppSDK, authored component, and third-party metadata shapes; keep invalid metadata diagnostics fail-closed.
- [ ] Symbol fidelity closure: finish generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, property/event accessor identity, parameter passing semantics, and custom attributes before broadening projection output.

### Phase 3 Generator And Compiler Plugin

- [ ] Generator projection matrix closure: finish Kotlin-relevant activation, static, factory, custom mapped type, nullable reference, required/default interface, generic instantiation, collection, async, delegate/event, struct, array, and attribute behavior.
- [ ] Compiler-plugin authoring/annotation transformation: move semantic authored type discovery and annotation/source-generator-equivalent behavior onto K2/IR symbols and generated/lowered support artifacts; Kotlin runtime must not discover this by reflection.
- [ ] Expect/actual closure: remove common fallback gates for async, collections, custom mapped types, static members, events, setter-only properties, and unsupported ABI shapes only after both JVM and mingwX64 contracts exist.
- [ ] Functional compiler-plugin closure: finish K2/IR lowering paths needed for generated projection and authoring behavior, including descriptor handoffs, annotation/source-generator equivalents, and fail-closed diagnostics.

### Phase 4 Projections

- [ ] Broad projection generation: generate Windows and WinAppSDK slices through plugin-owned deterministic output instead of hand-maintained checked-in growth.
- [ ] Dependency projection ownership: keep identity/suppression files as the cross-module ownership boundary so application modules do not duplicate dependency-owned projected FQNs.
- [ ] Generated-output audits: scan generated projection source/classes for forbidden runtime fallback, reflection/proxy, stale support registries, duplicate type/category tables, and source-size regressions before expanding checked-in projection breadth.

### Phase 5 Authoring

- [ ] Authoring completeness: finish factories, activation, hosting, metadata, receive-array variants, inherited/overridable interfaces, ABI marshaling combinations, and validation beyond the current JVM WinUI happy path.
- [ ] Native authoring host: defer native CCW/host implementation until native runtime ABI/object identity and JVM authoring contracts are stable enough to port directly.

### Phase 6 Packaging And Validation

- [ ] Kotlin appx/msix packaging completeness 正在做: finish manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, unpackaged vs packaged modes, and Gradle DSL ergonomics without cloning full MSBuild; current slice makes explicit signing and test-install failures fail closed and keeps install package input selection provider-based.
- [ ] Manifest closure: validate packaged-mode `Applications/Application` presence, executable/entry-point consistency, app identity, visual elements when present, and fail-closed diagnostics before package creation; current baseline validates application presence, executable, entry point, declared visual element attributes, and staged/package-root payload references for manifest executables/logos before makeappx packaging or verification.
- [ ] Package verification closure: add a cache-compatible package verification task using Windows packaging tools as evidence, and make it fail when package output is missing, malformed, or not installable enough for the current smoke target; current baseline uses `verifyWinRtApplicationPackage` with `makeappx unpack`, validates the unpacked manifest and manifest payload references, and writes a stable cache marker.
- [ ] Payload/resource closure 正在做: keep explicit package payload staging, resource inventories, PRI/MRT generation, and dependency layout deterministic and owned by the Kotlin Gradle plugin; current slice makes missing explicit package payloads, missing explicit PRI inputs, and missing/failing packaged application PRI generation fail closed.
- [ ] Gradle cache contract 正在做: keep configuration cache and build cache enabled for WinRT generation and packaging tasks; cache failures are issues to fix, not flags to bypass. Application package staging is cacheable with output-affecting resource-root path modeled as an input; runtime asset staging is cacheable after moving NuGet restore / CLI-discovered package resolution into `resolveWinRtRuntimeNuGetPackages` and consuming only modeled runtime assets, dependency identity files, NuGet package content roots, and the resolver package-root manifest.
- [ ] Representative Windows validation: validate touched runtime, metadata, generator, compiler-plugin, authoring, packaging, JVM, and native slices on Windows; samples remain validation surfaces, not design sources.

## Frozen

- [ ] `winrt-samples`: only validate completed runtime/generator/authoring slices; no sample-local runtime workarounds.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output.
- [ ] `mingwX64`: full native runtime/projection parity is deliberately deferred for the current work queue; keep shared contracts native-viable, but do not implement native actuals until the JVM/generator/packaging focus items are closed.
- [ ] Codebase slimming: frozen as an implementation target until the feature-completeness gates above are closed. Do not spend current work on deletion/refactor-only slimming because incomplete runtime/generator/packaging behavior would make the code grow again. Keep deletion candidates, source-count targets, and slimming execution details out of `PLAN.md`; if they are needed later, they belong in the untracked `SLIMMING_PLAN.md` after functional closure.

## Validation Plan

- [ ] Windows-first validation: run targeted Windows Gradle validation for the module touched by each slice before broader sample or integration runs.
- [ ] Cache validation gate: keep configuration cache and build cache enabled for WinRT generation and packaging checks; failures must be fixed in task inputs/actions or Gradle service ownership.
- [ ] Runtime gate: validate ABI, activation, object identity, marshaling, delegate/event, collection, async, and WinUI bootstrap behavior in winrt-runtime before expanding dependent generator/projection slices.
- [ ] Metadata gate: validate native WinMD ingestion, normalized symbols, signatures, accessors, custom attributes, and diagnostics in winrt-metadata before using those facts in generator output.
- [ ] Generator/compiler-plugin gate: validate deterministic output, descriptor intrinsic lowering, compiler-support aggregation, no reflection/proxy fallback, and no stale support registries before checking generated projection growth.
- [ ] Authoring gate: validate generated TypeDetails, authored WinMD, CCW factories, activation/hosting, receive-array handling, and WinUI composable boundaries before broadening authoring samples.
- [ ] Packaging gate: validate PRI/MRT/resource/package staging and appx/msix layout through Kotlin Gradle tasks, with CsWinRT/MSBuild behavior used only as reference evidence.
- [ ] Generated-output audit: scan generated source/classes for ComVtableInvoker fallback, invokeGenericArgs, Class.forName, Proxy.newProxyInstance, java.lang.reflect, stale registry artifacts, duplicate FQNs, and unexpected source-size growth.
- [ ] Sample role: use winrt-samples and KMP WinUI app runs only as representative end-to-end validation after the owning runtime, metadata, generator, authoring, or packaging slice is designed.
