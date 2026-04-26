# Plan

## Operating Rules

- [ ] Use `.cswinrt/` as the source of truth before changing runtime, metadata, generator, projections, samples, or authoring behavior.
- [ ] Keep work in dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` -> `winrt-projections` -> `winrt-samples` -> `winrt-authoring`.
- [ ] Do not add duplicate primitive/type/category/projection branch tables; put shared decisions in the Kotlin owner matching the `.cswinrt` responsibility.
- [ ] Keep `winrt-projections` generated or narrowly hand-authored glue only; do not grow handwritten standard WinRT API coverage.
- [ ] Keep `sample-jvm-winui3` legacy-only unless explicitly requested.
- [ ] Update this file with every scope/status change.

## Current State

- [x] Module layout exists: `winrt-runtime`, `winrt-metadata`, `winrt-generator`, `winrt-projections`, `winrt-authoring`, and `winrt-samples`.
- [x] `winrt-runtime` baseline is closed through Runtime 1.20: ABI primitives, activation, object identity, marshaling, delegates/events, collections, async, XAML/system helpers, configuration, and bounded Kotlin-specific deviations.
- [x] `winrt-metadata` is complete for the current `.cswinrt/src/cswinrt` audit: WinMD ingestion, normalized model, semantic helpers, source/cache handling, descriptor handoff, and final writer-handoff audit through Metadata Full-Parity 4.52.
- [x] `winrt-generator` baseline is closed for the current `.cswinrt/src/cswinrt` audit: declarations, ABI-bound members, activation, generic/event/type-shape support helpers, and SDK CLI generation.
- [x] `winrt-projections` compiles plugin-generated Foundation support through the included plugin build.
- [x] `kotlin-winrt` Gradle plugin baseline exists for SDK/NuGet generation inputs, generated-source wiring, NuGet CLI fallback, and `winRt {}` library/application identity handling.
- [ ] `winrt-samples` is intentionally minimal until generator/projection/plugin support expands.
- [ ] `winrt-authoring` remains frozen until generated projection and sample paths are coherent.

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
- [ ] Queue 14 正在做: expand `winrt-projections` only with deterministic generator/plugin-produced output.
- [x] Queue 14.1: plugin TestKit validation now proves a real Gradle library project can apply `io.github.kitectlab.winrt` and generate deterministic WinRT sources from Windows SDK metadata.
- [x] Queue 14.2: remove direct Kotlin Gradle Plugin runtime class dependency from the plugin so generated-source wiring works in published/TestKit plugin classloaders.
- [x] Queue 14.3: `winrt-projections` now consumes `io.github.kitectlab.winrt` from the root `pluginManagement` included build and compiles the plugin-generated `Windows.Foundation.IStringable` slice.
- [ ] Queue 15 正在做: expand `winrt-samples` from cswinrt-aligned API samples, then plugin-driven SDK/NuGet generation, then WinUI bootstrap/resource/message-loop validation.
- [x] Queue 15.1: `winrt-samples` is now a `winRt { application {} }` consumer with a cswinrt `ApiCompatTests`-aligned `Windows.Data.Json.JsonObject.Parse` sample shape; native execution is opt-in until JSON ABI stability, `GetNamedValue("phone")` nullable object-return, and `GetNamedArray("education")` collection parity land upstream.
- [x] Queue 15.2: sample-side plugin graph validation now makes `winrt-samples:check` verify generated application identity includes `winrt-projections` metadata and excludes ordinary runtime implementation dependencies.
- [x] Queue 15.3: `winrt-samples` now has opt-in WindowsAppSDK split-package declarations via `kotlinWinRt.samples.windowsAppSdkWinuiVersion`; default checks stay offline/lightweight while explicit identity validation proves Foundation/InteractiveExperiences/WinUI metadata is recorded.
- [x] Queue 15.4: plugin API is consolidated to `io.github.kitectlab.winrt` plus `winRt {}`; library is the default model and `application {}` selects application identity/resource staging.
- [x] Queue 15.5: move the JSON sample closer to `.cswinrt/src/Tests/UnitTest/ApiCompatTests.cs` by executing `GetNamedValue("phone")` and indexed `GetNamedArray("education")` reads without sample-local substitutes.
- [x] Queue 16: root `validateWinRtQueue16` now runs validation in order: generator regression -> plugin graph tests -> projection compile/integration -> sample smoke.
- [ ] Queue 17: reopen `winrt-authoring` only after Queue 11 through Queue 16 are coherent.

## Generator Follow-Through

- [x] Support helper output now exposes callable Kotlin APIs instead of passive descriptor-only lists.
- [x] Activatable runtime-class `create()` generation now calls the generated activation factory path.
- [x] Plugin/projection integration now wires generated sources into `winrt-projections` through the included plugin build before broad checked-in projection growth.
- [ ] Namespace additions from `.cswinrt/src/cswinrt/strings` remain plugin/projection integration work and should only be generated for surfaces that need them.

## Completed Milestones

- [x] Runtime 1.1-1.20: JVM-first `.cswinrt/src/WinRT.Runtime` baseline closed; remaining differences are explicit Kotlin narrowings.
- [x] Metadata 2.1-2.18: real WinMD loading, deterministic model, type refs, ABI descriptors, closure/special-type lookup, diagnostics, and fixture validation.
- [x] Metadata Full-Parity 3.x-4.52: `.cswinrt/src/cswinrt` metadata-owned helper/writer handoff audit closed.
- [x] Generator 3.1-3.7: declaration planning, declaration shells, member baseline, slot-based ABI cleanup, async/custom mapping, CLI, and first generated projection slice.
- [x] Queue 9.5: async/custom mapping moved out of ad hoc type substitution and backed by runtime async references.
- [x] Queue 10/10.9: representative generated API proof and final metadata audit closed.

## Frozen Until Prerequisites Close

- [ ] `winrt-projections`: no broad checked-in growth until Queue 11 generator support exists for the same surface.
- [ ] `winrt-samples`: no broad WinUI/sample expansion until generated projections and plugin resource handling exist.
- [ ] `winrt-authoring`: no hosting/source-generation work until generator/projection/sample path is stable.
- [ ] `mingwX64`: keep shared contracts viable, but full native parity planning starts after the JVM generator/projection/plugin path is coherent.

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
- [x] Validate touched modules with Windows Gradle via `./.agent_scripts/run_windows_gradle.sh <tasks>`.
- [x] For generator work, run targeted generator tests and projection compile checks before updating checked-in output.
- [x] For plugin work, add task-level tests for SDK source resolution, NuGet graph resolution, generated-source wiring, and application resource staging.
- [x] For samples, keep smoke coverage tied to the currently completed upstream slice.
