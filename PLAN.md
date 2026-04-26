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
- [x] `winrt-generator` has declaration planning/shells, representative `Windows.Data.Json` generation, ABI cleanup, async/custom mapping supplement, and a Windows SDK WinMD-driven CLI.
- [x] `winrt-projections` contains the generated representative slice (`Windows.Data.Json` plus `Windows.Foundation.IStringable`) produced by the generator path.
- [ ] `kotlin-winrt` Gradle plugin is not implemented yet.
- [ ] `winrt-samples` is intentionally minimal until generator/projection/plugin support expands.
- [ ] `winrt-authoring` remains frozen until generated projection and sample paths are coherent.

## Active Queue

- [ ] Queue 11: resume generator breadth from `.cswinrt/src/cswinrt` using the completed metadata descriptors instead of generator-local classification tables.
- [ ] Generator 11.1 正在做: consume `WinRtSignatureWriterDescriptor`, `WinRtAbiMarshalerPlanDescriptor`, `WinRtVtableWriterDescriptor`, and related metadata handoff descriptors for method/property/event ABI emission.
- [ ] Generator 11.2: expand interface, runtime-class, delegate, enum, struct, and ABI companion emission from metadata declaration/object-reference/factory descriptors.
- [ ] Generator 11.3: generate required-interface, mapped collection/system, generic ABI, event-source, and module-helper surfaces from metadata descriptors.
- [ ] Generator 11.4: regenerate a broader Windows SDK slice through the CLI and keep checked-in projection output deterministic.
- [ ] Queue 12: create the first-class `kotlin-winrt` Gradle plugin module and DSL for SDK/NuGet metadata inputs and generated-source wiring.
- [ ] Queue 13: implement plugin roles: `kotlin-winrt-library` carries generated sources plus NuGet/WinMD identity metadata; `kotlin-winrt-application` resolves transitive runtime/resource integration.
- [ ] Queue 14: expand `winrt-projections` only with deterministic generator/plugin-produced output.
- [ ] Queue 15: expand `winrt-samples` from the smallest generated-projection smoke path, then plugin-driven SDK/NuGet generation, then WinUI bootstrap/resource/message-loop validation.
- [ ] Queue 16: add validation in order: generator regression -> plugin graph tests -> projection compile/integration -> sample smoke.
- [ ] Queue 17: reopen `winrt-authoring` only after Queue 11 through Queue 16 are coherent.

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
- [ ] Validate touched modules with Windows Gradle via `./.agent_scripts/run_windows_gradle.sh <tasks>`.
- [ ] For generator work, run targeted generator tests and projection compile checks before updating checked-in output.
- [ ] For plugin work, add task-level tests for SDK source resolution, NuGet graph resolution, generated-source wiring, and application resource staging.
- [ ] For samples, keep smoke coverage tied to the currently completed upstream slice.
