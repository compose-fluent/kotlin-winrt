# Plan

## Operating Rules

- [x] `.cswinrt/` is the implementation baseline for observable WinRT behavior, ABI ownership, metadata interpretation, generated surface shape, authoring contracts, packaging evidence, and validation strategy.
- [x] Keep dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- [x] Tests validate the reference-first design; do not derive runtime, generator, authoring, or packaging rules from sample failures.
- [x] Keep Gradle configuration cache and build cache enabled; cache failures are task modeling bugs to fix.
- [x] Work on Windows. Prefer `.\gradlew.bat`; when running from WSL, use `./.agent_scripts/run_windows_gradle.sh <gradle-args...>`.
- [x] Keep codebase slimming frozen until functional completeness is closed. Remove duplication only when it is necessary for an active parity slice.

## Reference Boundaries

- [x] CsWinRT is projection-behavior evidence, not a mandate to copy `.NET` target splits, MSBuild infrastructure, release tooling, `Perf`, `Benchmarks`, or `cswinmd`.
- [x] Kotlin reflection is not a substitute for CsWinRT source-generator discovery. Equivalent annotation, metadata, registration, and source-generation behavior belongs in `winrt-generator` and `winrt-compiler-plugin`; runtime discovery stays fail-closed.
- [x] WinMD ingestion targets native WinMD metadata. Do not add `cswinmd` compatibility unless a concrete Kotlin projection requirement appears.
- [x] Packaging targets Kotlin `appx` / `msix` applications, PRI/MRT/resource indexing, manifest processing, dependency payload staging, signing/test-install hooks, and runtime asset layout without cloning full MSBuild.
- [x] Use `io.github.composefluent.winrt` as the base package for runtime, metadata, generator support, generated projections, authoring, and samples unless a narrow external compatibility exception is documented.

## Current Focus Queue

- [ ] 正在做 generated projection size reduction: reduce repeated generated `.class` output in prebuilt Windows SDK and Windows App SDK projection artifacts without changing Maven coordinates, projected API compatibility, runtime hot-path behavior, or multi-module classpath safety.
- [x] Completed size-reduction baseline: metadata/accessor helper reduction, owner-qualified event/generic support facades, module-local fixed-shape ABI helpers, projected reference helper regrouping by `IInspectableReference` / `IUnknownReference`, and helper frequency thresholding are in place.
- [x] Completed WinUI sample merge gate: `:winrt-samples:runWinRtApplicationHost` passes with Windows App SDK `2.1.3`, `runWinUiSmoke=true`, `autoExitWinUi=true`, `skipSettingsCard=true`, and `skipShimmer=true`.

## Module Alignment

- [x] `.cswinrt/src/WinRT.Runtime` maps to `winrt-runtime`, which owns ABI primitives, HRESULT/error-info, HSTRING ownership, COM/WinRT initialization, platform calls, object identity, activation, marshaling, delegates/events, collections, async, weak/agile/reference tracking, custom projections, and WinUI runtime hooks.
- [x] `.cswinrt/src/cswinrt` maps to `winrt-metadata`, `winrt-generator`, `winrt-compiler-plugin`, and `winrt-gradle-plugin`, split by metadata normalization, declaration/member planning, generated support emission, compiler-visible lowering, and Gradle orchestration.
- [x] `.cswinrt/src/Projections` maps to `winrt-projections`; checked-in projection output stays narrow while broad Windows SDK and Windows App SDK output is generated deterministically by the plugin.
- [x] `.cswinrt/src/Authoring` maps to `winrt-authoring`, which owns authored type candidates, metadata indexes, authored WinMD/descriptor/host manifest generation, TypeDetails rendering, activation/hosting contracts, and runtime-facing registration helpers.
- [x] `.cswinrt/src/Samples` maps to `winrt-samples`; samples are validation surfaces only and must not contain runtime, generator, authoring, or packaging workarounds.
- [x] `.cswinrt/src/Tests` maps to owning Kotlin module tests instead of a separate top-level test module.

## Completed Baseline

- [x] Runtime JVM baseline: ABI primitives, object references, activation, CCW/RCW ownership, delegate/event/collection/object marshaling, async bridges, WinUI bootstrap, vtable calls, restricted error info, weak/agile references, and custom projections are implemented and validated for the current surface.
- [x] Native `mingwX64` runtime/compiler/projection baseline: promoted common runtime tests pass through real native actuals; generated projection compilation covers the full Windows SDK root KMP surface; fallback audits reject reflection, JVM-only imports, `ComVtableInvoker`, and `invokeGenericArgs` leakage.
- [x] Metadata baseline: native WinMD ingestion, SDK/file/directory/NuGet input expansion, deterministic normalization, signatures, default/static/factory metadata, implemented interfaces, custom attributes, mapped types, and diagnostics are implemented.
- [x] Generator/compiler-plugin baseline: declaration planning, member/property/event/method emission, activation/static/factory/composable surfaces, ABI fail-closed diagnostics, compiler-support handoff, descriptor lowering, authored candidate artifacts, and large projection registrar chunking are implemented.
- [x] Projection baseline: Windows SDK and Windows App SDK projections are generated through deterministic plugin output; prebuilt projection publication, versioning, CI workflow, multi-SDK support, and README baseline badges are in place.
- [x] Authoring baseline: generated TypeDetails, authored WinMD/descriptor/host manifests, CCW factories, activation-factory exports, dependency factory chaining, inherited/overridable interfaces, diagnostics, JVM host bridge, and native exported-DLL host validation are implemented.
- [x] Packaging/resource baseline: Kotlin-owned appx/msix staging, manifests, dependency payloads, PRI/MRT/resource processing, signing/test-install hooks, runtime asset layout, Windows App SDK deployment, XAML metadata provider aggregation, and third-party component resource wiring are implemented.
- [x] Validation baseline: representative Windows gates exist for runtime, metadata, generator, compiler-plugin, projections, authoring, packaging, samples, native runtime, native projection, native authoring, and the broader `validateWinRtQueue16` queue.

## Phase 1 Runtime And ABI

- [x] ABI primitives first: `Guid`, `HResult`, HSTRING/string ownership, `PlatformAbi`, `ComVtableInvoker`, runtime initialization, platform API calls, and low-level COM pointer helpers are centralized in `winrt-runtime`.
- [x] Initialization and platform boundary: COM/WinRT initialization, Windows API loading, HRESULT translation, restricted error info, dynamic library loading, and deployment bootstrap are runtime-owned.
- [x] Object reference and identity: `IUnknown`/`IInspectable` wrappers, CCW/RCW identity caches, query/cast support, composable object lifetime, weak/agile/reference-tracker support, and runtime class name lookup are runtime-owned.
- [x] Activation: `RoGetActivationFactory`, manifest-free fallback, activation factory caching, dependency activation-factory chaining, and runtime-class activation helpers are implemented.
- [x] Parameterized IID/signature support: `WinRtTypeSignature`, `ParameterizedInterfaceId`, collection/async/delegate generic IDs, and generated generic interface acquisition are runtime-owned.
- [x] Delegates, events, collections, async, custom projections, WinUI deployment/resource/XAML hooks, and callback-vtable reuse are implemented after the foundational runtime slices.

## Phase 2 Metadata

- [x] WinMD ingestion, namespace/type discovery, SDK/reference/NuGet input expansion, include/exclude filtering, response files, and cache-stable inputs are implemented.
- [x] Symbol fidelity covers generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, method/property/event signatures, accessor identity, parameter semantics, and custom attributes.
- [x] Projection-shape inputs are centralized for primitive, inspectable/interface, collection, bindable, async, mapped type, runtime-class-name, and attribute classification to avoid duplicated branch tables.
- [x] Unsupported or malformed metadata fails closed before generator output.

## Phase 3 Generator And Compiler Plugin

- [x] Declaration planning and shells are stable for namespaces, enums, structs, delegates, interfaces, runtime classes, companions, and metadata surfaces.
- [x] Member/property/event/method emission, activation/static/factory surfaces, collections, async, custom mappings, WinUI source additions, namespace/type excludes, and unsupported-shape diagnostics are implemented.
- [x] Generated support files remain owner-qualified and deterministic across projection modules, including compiler-support TSVs, projection registrars, generic ABI support, event helpers, TypeDetails, and module-local ABI helpers.
- [x] Compiler-plugin lowering owns descriptor-backed calls, native direct calls, compiler-visible authored candidate artifacts, support initialization, projection registrar initialization, and Kotlin 2.4-compatible IR lookups.
- [x] Large generated projections avoid JVM class limits through registrar/support chunking without adding runtime reflection, classpath scanning, vararg dispatch, or global registries.

## Phase 4 Projections

- [x] Broad Windows SDK and Windows App SDK projection output is generated only after upstream runtime, metadata, and generator contracts exist.
- [x] Dependency identity files define cross-module projection ownership so application modules do not duplicate dependency-owned projected FQNs.
- [x] NuGet projection defaults distinguish preprojected baselines from third-party packages, with opt-in local generation for Windows SDK / Windows App SDK packages when needed.
- [x] Generated-output audits scan for forbidden fallback paths, JVM-only leakage, stale support registries, duplicate FQNs, duplicate type/category tables, and unexpected source-size growth.
- [x] Prebuilt projection artifacts preserve metadata-baseline versioning, snapshot suffixing, publication workflows, SDK installation reliability, and package-coordinate stability.

## Phase 5 Authoring

- [x] Authoring contracts live in `winrt-authoring`; Gradle, generator, compiler-plugin, and runtime consume those contracts instead of defining parallel models.
- [x] Compiler-symbol authored metadata replaces source-scanner-derived behavior where parity requires K2/IR-visible symbols, while scanner artifacts remain parity inputs until fully retired.
- [x] Authored ABI boundary covers factories, static members, composable factories, TypeDetails, metadata type mapping, object lifetime/identity, CCW/RCW ownership, and host manifests.
- [x] Authoring marshaling covers arrays, structs, delegates/events, object/interface/runtime-class parameters and returns, collections, async, nullable/reference shapes, HRESULT/error propagation, and fail-closed diagnostics.
- [x] Native authoring host covers `DllGetActivationFactory`, `DllCanUnloadNow`, export-table validation, activation lookup/query, dependency factory chaining, native component/consumer fixtures, and common-owned KMP generated support.

## Phase 6 Packaging And Validation

- [x] Kotlin appx/msix packaging covers manifest generation/validation, appx/msix layout, dependency payload resolution, resources/PRI/MRT, signing/test-install hooks, packaged/unpackaged modes, and disabled-generation output preservation.
- [x] Gradle identity propagation publishes and consumes `kotlin-winrt-identity` metadata variants for reusable WinRT libraries and final application modules.
- [x] Gradle plugin publication uses the shared Maven Central publishing convention, signing policy, snapshot/release versioning, and root/included-build publication flows.
- [x] Configuration-cache and build-cache compatibility are required for touched generation, packaging, runtime asset, authoring, projection, and sample tasks.
- [x] WinUI sample validation covers generated app hosts, Windows App SDK deployment, XAML metadata providers, WinUIEssential component resources, `SettingsCard`, stabilized `Shimmer` loading, and `MenuFlyout.ShowAt(Point)` lifetime behavior.

## Frozen Until Prerequisites Close

- [x] `winrt-samples`: keep validation-only; expand only for completed runtime/generator/authoring/packaging slices with no sample-local runtime workarounds.
- [x] `winrt-projections`: avoid broad checked-in generated growth; prefer plugin-generated output gated by completed upstream contracts.
- [x] `mingwX64`: runtime/compiler/projection parity is open only for regression fixes unless a new native parity slice is explicitly planned; sample expansion remains gated by owner-module contracts.
- [x] Codebase slimming and standalone source-count reduction: frozen until runtime, metadata, generator/compiler-plugin, projections, authoring, and packaging are functionally complete.

## Validation Gates

- [x] Runtime gate: targeted Windows `:winrt-runtime:jvmTest`, native runtime compile/test gates, and common-test promotion for platform-neutral contracts.
- [x] Metadata gate: Windows `:winrt-metadata:test` for native WinMD ingestion, normalized symbols, signatures, accessors, custom attributes, and diagnostics.
- [x] Generator/compiler-plugin gate: Windows `:winrt-generator:test`, `:winrt-compiler-plugin:test`, generated projection compile, support-file validation, and stale artifact cleanup.
- [x] Projection gate: generated-output audit plus JVM and `mingwX64` compile gates, including lightweight and full Windows SDK root KMP validation.
- [x] Authoring gate: generated TypeDetails, authored WinMD, CCW factories, activation/hosting, native exported-DLL fixtures, receive-array handling, and WinUI composable boundaries.
- [x] Packaging gate: PRI/MRT/resource/package staging, appx/msix layout, runtime assets, signing/test-install hooks, and Windows App SDK deployment validation.
- [x] Sample gate: use `winrt-samples` and KMP WinUI runs only as representative end-to-end validation after owning contracts are implemented.
