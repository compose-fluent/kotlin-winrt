# AGENTS

## Mission

`kotlin-winrt` is a Kotlin language projection for WinRT and WinUI, and its engineering baseline is the local `.cswinrt/` source tree in this repository.

The default expectation is not to invent a new projection model. When implementing runtime behavior, generated bindings, authoring support, activation, marshaling, delegates, generic interface handling, collection projection, or WinUI bootstrap behavior, use `.cswinrt` as the first reference and keep Kotlin behavior structurally aligned with it.

## Primary Reference Source

1. Treat `.cswinrt/` as the primary source of truth for architecture, layering, naming intent, feature slicing, and projection behavior.
2. Before introducing a new runtime abstraction or generator rule, inspect the corresponding area in `.cswinrt/src` and mirror its responsibility split in Kotlin form.
3. If `kotlin-winrt` behavior differs from `.cswinrt`, prefer changing `kotlin-winrt` to match the reference unless there is a Kotlin-specific language or toolchain constraint that makes direct parity impossible.
4. If parity is impossible, document the exact reason in the relevant code or task notes and keep the deviation narrow, explicit, and test-covered.
5. Do not design public APIs, runtime conventions, or generator heuristics from scratch when `.cswinrt` already has a corresponding implementation strategy.

## Required Architecture

Follow a layered module design that mirrors the same separation of concerns visible in `.cswinrt`:

1. ABI layer: low-level COM and WinRT ABI interop, pointer handling, memory ownership, vtable calls, GUID/IID handling, HRESULT translation, and platform call boundaries.
2. Runtime layer: object identity, activation, marshaling, delegate bridges, interface casting, reference tracking, collection adapters, async bridges, and authoring support.
3. Metadata layer: WinMD loading, metadata model construction, symbol analysis, generic instantiation handling, and projection-shape decisions.
4. Generator layer: Kotlin source generation driven by metadata and runtime contracts, without embedding platform-specific implementation details into generated API shape logic.
5. Projection output layer: checked-in generated bindings and projection assets produced by the generator.
6. Sample and validation layer: executable samples and tests that prove the generated projection works end to end.

Do not collapse these concerns into a single module or allow samples to become the place where runtime behavior is implemented.

## Kotlin Module Expectations

When creating or restructuring modules, the Kotlin workspace must use Kotlin-oriented module names while preserving a strict one-to-one responsibility mapping to `.cswinrt/src`:

1. `winrt-runtime`: Kotlin runtime module corresponding directly to `.cswinrt/src/WinRT.Runtime`.
2. `winrt-metadata`: Kotlin metadata analysis module corresponding directly to the metadata-loading and model-building responsibilities inside `.cswinrt/src/cswinrt`.
3. `winrt-authoring`: Kotlin authoring and hosting module corresponding directly to `.cswinrt/src/Authoring`.
4. `winrt-projections`: Kotlin generated projection output module corresponding directly to `.cswinrt/src/Projections`.
5. `winrt-samples`: Kotlin sample aggregation module corresponding directly to `.cswinrt/src/Samples`.

Tests should follow normal Kotlin project structure and live inside the relevant modules instead of being represented as a separate top-level `Tests` module.

If submodules are needed under any of the names above for JVM and later `mingwX64`, keep the parent module name stable and place the split beneath it rather than replacing it with different top-level names.

Do not treat previous module naming or half-finished structure as the long-term design target. If older modules exist, rename them into the exact layout above or delete them once their responsibilities are absorbed.

## Legacy Sample Rule

1. Do not spend current refactor effort on `sample-jvm-winui3` unless the user explicitly asks for it.
2. Treat `sample-jvm-winui3` as a legacy sample surface, not as a required source of truth for the new module layout.
3. Do not block runtime, metadata, authoring, projections, or plan updates on migrating `sample-jvm-winui3`.
4. If functionality from `sample-jvm-winui3` is still useful, reintroduce it later under `winrt-samples` instead of preserving the old module as a first-class target.

## CsWinRT Responsibility Mapping

The required correspondence is strict by responsibility ownership. Use the following mapping as the minimum baseline:

1. `.cswinrt/src/WinRT.Runtime` maps to the Kotlin module `winrt-runtime`.
2. `.cswinrt/src/cswinrt` maps to the Kotlin metadata and generator pipeline, organized under the Kotlin-oriented modules that own the same compiler responsibilities.
3. `.cswinrt/src/Authoring` maps to the Kotlin module `winrt-authoring`.
4. `.cswinrt/src/Projections` maps to the Kotlin module `winrt-projections`.
5. `.cswinrt/src/Samples` maps to the Kotlin module `winrt-samples`.
6. `.cswinrt/src/Tests` maps to tests that live inside the relevant Kotlin modules instead of a separate top-level module.
7. `.cswinrt/build`, `.cswinrt/eng`, and `.cswinrt/nuget` are release, packaging, and infrastructure surfaces; mirror them only when `kotlin-winrt` reaches the equivalent need, not as day-one runtime modules.

Do not introduce unrelated top-level module names. Do not treat samples or tests as substitutes for missing runtime, metadata, generator, authoring, or projections modules.

Do not claim that Kotlin modules are aligned with `.cswinrt` unless the top-level module ownership above is explicitly implemented and the remaining gaps are called out in `PLAN.md`.

## Package Naming

1. Use `io.github.kitectlab.winrt` as the base package for shared Kotlin projection code.
2. Keep module-specific packages underneath that root instead of introducing unrelated top-level package prefixes.
3. Generated projection code, runtime support code, metadata tooling, and samples should follow a consistent package layout derived from `io.github.kitectlab.winrt` unless external API compatibility requires a narrower exception.
4. Any required exception to the package root must be explicit, minimal, and documented in the relevant module or generator rule.

## Platform Support Rules

1. Every runtime or generator feature must be evaluated for both Kotlin/JVM and Kotlin/Native `mingwX64` support.
2. The implementation order is JVM first: establish the full JVM path for ABI interop, runtime behavior, metadata loading, code generation, generated bindings, and WinUI validation before treating the corresponding slice as the baseline for `mingwX64` parity work.
3. Do not land a JVM-only design as the permanent architecture if it blocks an equivalent `mingwX64` implementation.
4. Shared contracts go in common code; platform mechanics go in target-specific source sets or modules.
5. Keep JVM and `mingwX64` behavior semantically aligned even when their interop mechanisms differ.
6. If one target is temporarily incomplete, keep the shared API and tests honest about that gap and record the missing parity explicitly.
7. Do not fake cross-target support by stubbing out permanent TODO implementations without a tracked parity plan.

## Implementation Rules

1. Concrete Kotlin implementation should be fully informed by the matching `.cswinrt` code path, not just by README-level concepts.
2. Match `.cswinrt` feature slicing where practical: runtime support, code generation, projections, samples, and tests should evolve in corresponding slices.
3. Preserve clear ownership boundaries: ABI code must not absorb generator policy; generator code must not reimplement runtime mechanics; samples must not patch around missing runtime behavior.
4. Favor small, composable runtime helpers and generator passes over large monolithic classes.
5. When implementing authoring, delegates, generics, collection projections, activation, or WinUI integration, check `.cswinrt` for both the runtime contract and the generated surface shape before coding.
6. Generated code should be reproducible, deterministic, and derived from metadata plus runtime conventions rather than hand-maintained edits.

## Commit Discipline

1. Every completed feature, completed parity slice, one full set of related completions, or all fixes within a single module must be committed immediately once the slice is coherent.
2. Keep each commit atomic and scoped to one self-contained purpose.
3. Do not mix unrelated runtime, generator, sample, and refactor work in the same commit unless they together form one inseparable feature slice.
4. If a task contains multiple independent fixes or features, split them into separate commits.
5. Do not leave finished work uncommitted while starting the next unrelated slice.
6. Commit messages should describe the completed capability or repaired module boundary, not generic maintenance wording.

## Planning File Rules

1. Maintain a root-level `PLAN.md` file as the canonical implementation plan for this repository.
2. `PLAN.md` must list all implementation items using Markdown task list syntax.
3. Completed items must be marked as `- [x]`.
4. Work-in-progress items must remain task-list items and include the text `正在做` in the item label.
5. Every code change, status change, scope split, or newly discovered implementation slice must update `PLAN.md` in the same change.
6. Do not leave `PLAN.md` stale relative to the current repository state, current branch work, or the latest committed slice.

## Validation Rules

1. This project must be built and validated from Windows.
2. On this repository, prefer PowerShell-based execution paths.
3. When running in WSL, use `./.agent_scripts/run_windows_gradle.sh <gradle-args...>` to invoke Gradle on Windows.
4. When running directly on Windows, call Gradle directly, typically through `./gradlew.bat` or the equivalent Windows entry point.
5. Prefer targeted verification for the touched module, affected tests, or affected sample before considering broader runs.
6. For platform-specific work, validate the corresponding target instead of assuming JVM coverage is enough for `mingwX64`, or vice versa.

## Non-Negotiable Constraints

1. Do not replace `.cswinrt`-aligned projection behavior with Kotlin-only shortcuts just because they are easier to implement.
2. Do not hide missing runtime support inside samples.
3. Do not introduce broad architectural rewrites unless they improve parity with `.cswinrt` and preserve dual-target viability.
4. Do not mark a feature complete until the runtime contract, generation logic, and affected validation surface are updated together.
5. Any intentional gap with `.cswinrt` must be called out explicitly and kept traceable.
