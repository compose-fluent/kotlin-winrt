# Plan

## Planning Rules

- [ ] `.cswinrt/` is the implementation baseline for runtime, metadata, generator, authoring, projections, and samples.
- [ ] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [ ] Tests validate `.cswinrt` parity; do not derive runtime or generator rules from sample failures.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Keep Gradle configuration cache and build cache enabled; cache failures are implementation issues to fix, not flags to bypass.
- [ ] Keep `mingwX64` implementation frozen for the current queue. Shared contracts must stay native-viable, but no native actual work starts until the JVM/generator/packaging focus items are closed.
- [ ] Keep codebase slimming frozen until functional completeness is reached. Avoid new avoidable bloat, but do not run deletion-only refactors or source-count reduction as current work.

## Reference Boundaries

- [ ] CsWinRT is the projection-behavior reference only: use `.cswinrt/` to derive WinRT observable behavior, metadata interpretation, generated surface shape, ABI ownership, authoring contracts, and packaging evidence.
- [ ] Do not treat CsWinRT `netstandard` / modern `.NET` target differences as Kotlin goals; those environment splits do not map to Kotlin projection completeness.
- [ ] Kotlin has no runtime reflection equivalent for CsWinRT's attribute/source-generator discovery path. Equivalent annotation, metadata, registration, or source-generator behavior must be implemented through `winrt-generator` and `winrt-compiler-plugin`, with runtime discovery kept fail-closed.
- [ ] WinMD ingestion targets native WinMD metadata only. Do not add `cswinmd`-specific compatibility as a projection goal.
- [ ] Packaging target is complete Kotlin `appx` / `msix` application packaging, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout. CsWinRT/MSBuild targets are implementation evidence, not a requirement to clone full MSBuild.

## Completed Capability Baseline

- [x] Runtime/ABI JVM baseline: HRESULT/GUID/HSTRING, COM object references, activation, object identity, CCW/RCW ownership, delegate/event/collection/object marshaling, async bridges, WinUI bootstrap, and core vtable-call lowering have representative JVM validation.
- [x] Metadata baseline: native WinMD ingestion, deterministic model construction, signatures, semantic helpers, accessors, generic/default-interface metadata, custom attributes, mapped types, and writer-handoff descriptors have targeted coverage.
- [x] Generator/compiler-plugin baseline: deterministic projection generation, support-file handoff, owner-local `NativeProjection` bodies, descriptor intrinsic lowering, compiler-support aggregation, reflection/proxy removal, dead generic ABI registry wrapper removal, and Gradle build/configuration-cache compatibility are established.
- [x] Authoring JVM baseline: source scanning, generated TypeDetails, authored WinMD emission, CCW factories, host manifests, activation factory exports, composable WinUI subclasses, receive-array handling, and JVM host bridge slices have landed.
- [x] WinUI/sample validation baseline: `winrt-samples` and the KMP WinUI library/application graph validate representative generated WinUI controls, resources, events, focus, AutomationPeer, dependency properties, authored subclasses, and app lifecycle.
- [x] Packaging/resource baseline: Kotlin Gradle application staging covers WindowsAppSDK payloads, application PRI/MRT generation, manifest-derived metadata, package item classification, resource inventories, component PRI aggregation, makepri execution, cacheable staging, fail-closed tool startup diagnostics, package/sign/verify disabled-output preservation, manifest payload validation, signing input validation, and makeappx verification for the current JVM WinUI samples.

## Current Focus Queue

- [ ] Kotlin appx/msix packaging closure 正在做: close manifest, resource, package, signing, verification, install, and Gradle cache gaps in the Kotlin Gradle plugin while using CsWinRT/MSBuild behavior only as evidence.
- [ ] Gradle cache contract 正在做: keep all touched generation and packaging tasks configuration-cache and build-cache compatible; every cache regression is a tracked implementation defect.
- [ ] Generator projection matrix closure: finish activation/static/factory surfaces, delegates/events, async/collection helpers, mapped types, ABI array/struct/member shapes, and unsupported-shape diagnostics before broadening checked-in projection output.
- [ ] Metadata input expansion and native WinMD fidelity: finish Kotlin-needed SDK/file/directory/reference expansion and harden native WinMD parsing; do not add `cswinmd` compatibility.
- [ ] Interface native projection IR migration: continue descriptor-backed compiler-plugin lowering only where it closes functional projection behavior, not as a source-count reduction task.

## Phase 1 Runtime And ABI

- [ ] Interface native projection IR migration: keep public projection behavior source-compatible while moving eligible JVM vtable calls into descriptor-backed compiler-plugin lowering only where needed to close projection behavior; do not add runtime reflection, JVM Proxy, or classpath discovery paths.
- [ ] JVM runtime parity gap: audit remaining activation, object identity, marshaling, delegate/event, collection, async, HRESULT/error-info, and WinUI bootstrap behavior against `.cswinrt/src/WinRT.Runtime`; close gaps in `winrt-runtime` before expanding dependent projections.
- [ ] Generic signature/IID gap: verify parameterized type-signature rendering and IID hashing against CsWinRT behavior for interfaces, delegates, collections, and async helpers before relying on broader generated output.
- [ ] Native runtime actuals for `mingwX64`: frozen for the current queue; later implement real COM initialization, WinRT initialization, HRESULT/error-info translation, dynamic library loading, vtable invocation, callback/upcall plumbing, and pointer ownership in target source sets.
- [ ] Native object identity and activation: frozen for the current queue; later implement IUnknown/IInspectable identity, QueryInterface, runtime-class-name lookup, activation factory lookup/cache, manifest-free fallback, and COM reference ownership for `mingwX64`.
- [ ] Native delegates, events, collections, and async: frozen for the current queue; later port the JVM-validated ownership rules after native ABI primitives and object identity are complete.

## Phase 2 Metadata

- [ ] Metadata input expansion parity: complete file, directory, SDK/platform manifest, extension SDK, NuGet, response-file, include/exclude, cache, and deterministic model behavior required by Kotlin builds, using `.cswinrt/src/cswinrt` input expansion as evidence.
- [ ] Native WinMD coverage: harden native WinMD parsing across SDK, WinAppSDK, authored component, and third-party metadata shapes; keep invalid metadata diagnostics fail-closed.
- [ ] Symbol fidelity closure: finish generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, property/event accessor identity, parameter passing semantics, custom attributes, and unsupported metadata diagnostics before broadening projection output.
- [ ] Projection-shape input gap: centralize mapped-type, primitive, inspectable/interface, collection, bindable, async, runtime-class-name, and attribute classification so generator/runtime code does not repeat local branch tables.
- [ ] Metadata cache/model gap: ensure normalized models are deterministic, reusable by Gradle cacheable tasks, and explicit about all inputs that affect generated Kotlin output.

## Phase 3 Generator And Compiler Plugin

- [ ] Generator declaration planning: keep declaration ownership, namespace/type shells, companion/metadata surfaces, and deterministic ordering as the first generator responsibility before member emission expands.
- [ ] Generator projection matrix closure: finish Kotlin-relevant activation, static, factory, custom mapped type, nullable reference, required/default interface, generic instantiation, collection, async, delegate/event, struct, array, and attribute behavior.
- [ ] Compiler-plugin authoring/annotation transformation: move semantic authored type discovery and annotation/source-generator-equivalent behavior onto K2/IR symbols and generated/lowered support artifacts; Kotlin runtime must not discover this by reflection.
- [ ] Expect/actual closure: remove common fallback gates for async, collections, custom mapped types, static members, events, setter-only properties, and unsupported ABI shapes only after both JVM and later `mingwX64` contracts exist.
- [ ] Unsupported-shape diagnostics: fail closed with stable diagnostics when metadata or generator input requires unsupported ABI, projection, authoring, or platform behavior.
- [ ] Generated support-file gap: verify helper/init/metadata support emission against CsWinRT responsibility split, including activation factories, static interfaces, delegate bridges, generic interface helpers, event tokens, and marshaling support.

## Phase 4 Projections

- [ ] Broad projection generation: generate Windows and WinAppSDK slices through plugin-owned deterministic output instead of hand-maintained checked-in growth.
- [ ] Dependency projection ownership: keep identity/suppression files as the cross-module ownership boundary so application modules do not duplicate dependency-owned projected FQNs.
- [ ] Projection breadth control: expand checked-in generated output only after the corresponding runtime, metadata, and generator contracts exist for the same feature.
- [ ] Generated-output audits: scan generated projection source/classes for forbidden runtime fallback, reflection/proxy, stale support registries, duplicate type/category tables, duplicate FQNs, and unexpected source-size growth before expanding projection breadth.

## Phase 5 Authoring

- [ ] Authoring completeness: finish factories, activation, hosting, metadata, receive-array variants, inherited/overridable interfaces, ABI marshaling combinations, and validation beyond the current JVM WinUI happy path.
- [ ] Compiler-plugin-backed authoring metadata: ensure authored metadata and registration are generated from compiler-visible symbols rather than runtime reflection or string-only source scanning.
- [ ] Authoring ABI boundary: define what authored types expose across the ABI boundary, how factories are surfaced, and how generated metadata connects to runtime ownership before expanding samples.
- [ ] Native authoring host: frozen for the current queue; later implement native CCW/host behavior only after native runtime ABI/object identity and JVM authoring contracts are stable enough to port directly.

## Phase 6 Packaging And Validation

- [ ] Kotlin appx/msix packaging completeness 正在做: finish Kotlin-owned manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, packaged/unpackaged modes, disabled-generation input skipping/output preservation, and Gradle DSL ergonomics without cloning full MSBuild.
- [ ] Manifest closure 正在做: validate packaged-mode package `Properties`, `Applications/Application` presence, application id, executable/entry-point consistency, identity, required visual elements, package/verify manifest payload references, and fail-closed diagnostics before package creation and verification.
- [x] Package output safety: reject package outputs that alias signing inputs or are placed where they can be self-included in staged package payloads.
- [x] Package file type safety: reject non-`.appx` / non-`.msix` package inputs and outputs before package, verify, sign, or install tasks invoke external Windows tools.
- [x] Package verification missing-input safety: fail closed before invoking `makeappx` when verification is enabled and the appx/msix package file is missing, without writing marker or unpack outputs.
- [x] Package verification unpack failure safety: fail closed without writing verification markers when `makeappx unpack` starts but rejects a malformed or non-unpackable appx/msix package.
- [x] Package verification closure: keep `verifyWinRtApplicationPackage` cache-compatible, bind verification markers to the verified package name/content hash, skip missing inputs when verification is disabled, and fail closed when package output is missing, malformed, cannot be unpacked, or fails current manifest/payload smoke validation.
- [ ] Payload/resource closure 正在做: keep explicit package payload staging, resource inventories, PRI/MRT generation including `resources.language-*` outputs, dependency layout, makepri inputs, package-root path sensitivity, absolute/escaping path rejection across PRI, package-payload, and manifest-payload inputs, and caller/manifest-field-specific relative-path diagnostics deterministic and owned by the Kotlin Gradle plugin.
- [x] Signing and install closure: keep signtool certificate/password/hash and PowerShell install/test-install inputs provider-modeled, fail closed on missing tools or invalid packages, select unsigned package output for install when signing is disabled, honor explicit install package file overrides, skip missing sign/install inputs when those operations are disabled, and preserve disabled task outputs without deleting previous artifacts.
- [x] Signing certificate input safety: fail closed when an explicit signing certificate file is configured but missing, and pass existing certificate files through signtool `/f` and `/p` instead of falling back to thumbprint or automatic certificate selection.
- [ ] Representative Windows validation: validate touched runtime, metadata, generator, compiler-plugin, authoring, packaging, and JVM slices on Windows; samples remain validation surfaces, not design sources.
- [ ] Native validation: frozen for the current queue; later add `mingwX64` validation only after native runtime/projection contracts are implemented.

## Frozen Until Prerequisites Close

- [ ] `winrt-samples`: only validate completed runtime/generator/authoring slices; no sample-local runtime workarounds.
- [ ] `winrt-projections`: avoid broad checked-in projection growth; prefer plugin-generated output gated by completed upstream contracts.
- [ ] `mingwX64`: full native runtime/projection parity is deliberately deferred for the current work queue; shared contracts must remain native-viable.
- [ ] Codebase slimming: frozen as an implementation target until runtime, metadata, generator, authoring, projection, and Kotlin appx/msix packaging are functionally complete. Keep deletion candidates, source-count targets, and slimming execution details out of `PLAN.md`; if needed later, they belong in untracked `SLIMMING_PLAN.md`.

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
