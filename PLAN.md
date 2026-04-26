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
- [x] `winrt-projections` compiles generated support handoff helpers for the current Foundation/Collections generator slice.
- [ ] `kotlin-winrt` Gradle plugin is not implemented yet.
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
- [ ] Queue 12 正在做: create the first-class `kotlin-winrt` Gradle plugin module and DSL for SDK/NuGet metadata inputs and generated-source wiring.
- [ ] Queue 13: implement plugin roles: `kotlin-winrt-library` carries generated sources plus NuGet/WinMD identity metadata; `kotlin-winrt-application` resolves transitive runtime/resource integration.
- [ ] Queue 14: expand `winrt-projections` only with deterministic generator/plugin-produced output.
- [ ] Queue 15: expand `winrt-samples` from the smallest generated-projection smoke path, then plugin-driven SDK/NuGet generation, then WinUI bootstrap/resource/message-loop validation.
- [ ] Queue 16: add validation in order: generator regression -> plugin graph tests -> projection compile/integration -> sample smoke.
- [ ] Queue 17: reopen `winrt-authoring` only after Queue 11 through Queue 16 are coherent.

## Generator Follow-Through

- [x] Support helper output now exposes callable Kotlin APIs instead of passive descriptor-only lists.
- [x] Activatable runtime-class `create()` generation now calls the generated activation factory path.
- [ ] Plugin/projection integration must wire these generated sources into Gradle SDK/NuGet inputs before broad checked-in projection growth.
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

- [ ] Plugin 4.1: create plugin module, task model, DSL, source-set wiring, SDK/NuGet metadata inputs, and filters.
- [ ] Plugin 4.2: resolve NuGet packages, parse `.nuspec` dependency closure, discover WinMD files, and feed `WinRtMetadataSource`.
- [ ] Plugin 4.3: implement `kotlin-winrt-library` artifact metadata for generated projections plus NuGet/WinMD identity.
- [ ] Plugin 4.4: implement `kotlin-winrt-application` transitive NuGet runtime/resource staging.
- [ ] Plugin 4.5: migrate useful runtime/resource staging logic out of legacy `sample-jvm-winui3` into the plugin application model.

## Validation

- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-metadata:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:test`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11 --namespace Windows.Data.Json'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11-foundation --namespace Windows.Foundation'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-generator:run --args='--output /tmp/kotlin-winrt-generator-11-support --namespace Windows.Foundation --namespace Windows.Foundation.Collections'`
- [x] `./.agent_scripts/run_windows_gradle.sh :winrt-projections:compileKotlin`
- [ ] Validate touched modules with Windows Gradle via `./.agent_scripts/run_windows_gradle.sh <tasks>`.
- [ ] For generator work, run targeted generator tests and projection compile checks before updating checked-in output.
- [ ] For plugin work, add task-level tests for SDK source resolution, NuGet graph resolution, generated-source wiring, and application resource staging.
- [ ] For samples, keep smoke coverage tied to the currently completed upstream slice.
