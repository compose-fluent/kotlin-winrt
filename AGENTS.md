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

## Design Direction

1. The implementation direction is reference-first and design-first: inspect the matching `.cswinrt` slice, decide the Kotlin module boundary and runtime contract, then implement the Kotlin version, and only after that add or adjust tests as validation.
2. Tests are validation tools, not design drivers. Do not infer missing architecture, API shape, marshaling rules, activation behavior, or generator policy by iterating on failing tests until something passes.
3. Do not use test failures as the primary source of truth for what the runtime or generator should do. Use `.cswinrt` source and the planned module responsibilities as the primary source of truth, then use tests to confirm parity.
4. If an existing test contradicts `.cswinrt`, fix the implementation or the test so that the repository moves toward `.cswinrt` parity rather than preserving a Kotlin-only behavior.
5. Before writing a new test for a new slice, identify the matching `.cswinrt` source area and encode that mapping in code comments, task notes, or `PLAN.md` status text when it is not already obvious.
6. Do not let ad hoc test scaffolding become the place where design decisions are made. Runtime contracts belong in `winrt-runtime`, metadata/model decisions belong in `winrt-metadata`, generator decisions belong in the generator pipeline, and tests only verify those decisions.

## Execution Cadence

1. Work must advance in explicit phases rather than opportunistic file-by-file edits.
2. Phase 1 is runtime-first: implement the minimum ABI, activation, marshaling, object identity, and interface-call foundations in `winrt-runtime` that are required by the current `.cswinrt` slice.
3. Phase 2 is metadata-second: implement WinMD loading, metadata normalization, symbol modeling, and projection-shape inputs in `winrt-metadata` only after the active runtime contracts are clear enough to support the slice being built.
4. Phase 3 is generator-third: implement generator rules from the `.cswinrt/src/cswinrt` responsibility split only after the required metadata model and runtime contracts exist.
5. Phase 4 is projections-fourth: generate or check in projection output in `winrt-projections` only after the corresponding generator rule is defined and the runtime/metadata contracts it depends on already exist.
6. Phase 5 is authoring-fifth: implement `winrt-authoring` only after the runtime ABI/lifetime and metadata/generator contracts needed by the authoring slice are understood from `.cswinrt/src/Authoring`.
7. Phase 6 is samples-and-validation last: use `winrt-samples` and per-module tests only after the corresponding runtime, metadata, generator, projection, or authoring slice has already been designed and implemented.
8. Do not start from `winrt-projections`, sample code, or test scaffolding just because they are easier to see or quicker to make compile. If a slice appears to require projection output first, stop and fill the missing upstream runtime, metadata, or generator step instead.
9. A later phase may contain temporary smoke coverage for an earlier phase, but it must not become the reason the earlier phase is designed backward from that coverage.
10. If a phase is blocked, record the missing prerequisite in `PLAN.md` and continue with the earliest incomplete prerequisite rather than jumping ahead to a later module.

## Required Slice Ordering

Within the phase order above, the active implementation queue must also move from foundational slices to dependent slices:

1. In `winrt-runtime`, prioritize ABI primitives, HRESULT/GUID/HSTRING ownership, initialization scope, object identity, activation lookup, and reusable vtable-call shapes before delegates, collections, async, or WinUI-specific conveniences.
2. In `winrt-runtime`, land generic interface signature/IID support and delegate/callback plumbing before expecting projected collection or async surfaces to behave correctly.
3. In `winrt-metadata`, land real WinMD loading and normalized symbol/model construction before expanding projection-shape logic or handwritten generated API coverage.
4. In the generator pipeline, land declaration-shape planning first, then member/property/event/method emission, then special-case projection rules such as collections, async, custom mappings, and WinUI-specific behavior.
5. In `winrt-projections`, only check in slices that are already produced by the generator path for the same feature; temporary handwritten projection code may exist only as a narrow smoke surface and must not expand as the primary implementation path.
6. In `winrt-authoring`, start with hosting/projected-object lifetime and ABI boundary requirements from `.cswinrt/src/Authoring` before broader authoring conveniences or sample-driven glue.
7. In `winrt-samples`, start with the smallest runtime-validation surface for the active implemented slice before broader WinUI end-to-end scenarios.
8. Tests must follow the same dependency order: runtime unit coverage first, then metadata coverage, then generator regression coverage, then projection/sample integration coverage.

For the active JVM runtime path, treat the following order inside `winrt-runtime` as the required baseline:

1. ABI primitives first: `Guid`, `HRESULT`, WinRT string ownership/reference mechanics, memory layout helpers, and raw vtable-call shapes.
2. Initialization and platform boundary second: COM/WinRT initialization scope, Windows API entry points, dynamic library loading, and error translation.
3. Object reference and identity third: `IUnknown`/`IInspectable` wrappers, ownership/disposal rules, runtime class name lookup, and interface-cast/query support.
4. Activation fourth: `RoGetActivationFactory`, manifest-free fallback, activation factory caching, and runtime-class activation helpers.
5. Parameterized type-signature and IID support fifth: type-signature rendering and parameterized IID hashing required by later generic collection and delegate slices.
6. Delegates, collections, async, and WinUI-specific runtime hooks only after the preceding runtime slices are coherent enough to support them without guesswork.

For the active metadata and generator path, treat the following order as the required baseline:

1. In `winrt-metadata`, start with real WinMD source ingestion, namespace/type discovery, and deterministic normalization before enriching the model with projection-specific conveniences.
2. In `winrt-metadata`, add symbol-shape fidelity next: generic parameters, default interfaces, implemented interfaces, activatable/static/factory metadata, method/property/event signatures, and parameter passing semantics.
3. In the generator pipeline, start with declaration planning: decide what declarations exist and how they map to Kotlin ownership before emitting detailed members.
4. In the generator pipeline, emit declaration shells next: namespaces, enums, structs, delegates, interfaces, runtime classes, and companion/metadata surfaces in deterministic order.
5. Only after declaration shells are stable, add member/property/event/method emission, then activation/static/factory surfaces, then special rules for collections, async, custom mappings, and WinUI-specific behavior.
6. Do not use handwritten projection files in `winrt-projections` as the source of truth for generator behavior. Generator rules must come from `.cswinrt/src/cswinrt` responsibilities plus the metadata/runtime contracts they depend on.

For the active authoring and validation path, treat the following order as the required baseline:

1. In `winrt-authoring`, start with projected-object lifetime, activation/hosting boundaries, and ABI-facing authoring requirements derived from `.cswinrt/src/Authoring` before any convenience layer or sample-driven glue.
2. In `winrt-authoring`, establish the minimum hosting contract next: what authored types expose across the ABI boundary, how factories are surfaced, and how generated/metadata assumptions connect to runtime ownership.
3. In `winrt-samples`, start with the smallest smoke surface that validates the currently completed runtime/generator slice; keep the sample narrow enough that failures still point to the owning upstream module.
4. Only after the corresponding runtime, metadata, generator, and authoring prerequisites exist, expand samples to WinUI bootstrap, resource loading, window lifetime, and message-loop behavior.
5. Do not use samples as a substitute for missing runtime, generator, or authoring design. If a sample needs ad hoc glue to work, stop and move that behavior into the owning upstream module instead.
6. Do not let sample breadth outrun authoring/runtime/generator parity. A broader sample is not progress if the underlying contracts are still provisional.

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
7. When a test fails, do not start by changing the design to satisfy the test. First compare the failing area against `.cswinrt`, confirm the intended contract, and only then decide whether the implementation, generator logic, or the test expectation is wrong.
8. Do not treat green tests as proof that a slice is correctly designed if the corresponding `.cswinrt` reference path has not yet been inspected and mapped.
9. Do not repeatedly enumerate the same runtime type families, projection categories, or ABI shape decisions across multiple Kotlin files when `.cswinrt` already centralizes that decision in a smaller set of helpers, registries, marshalers, or type-name/signature utilities.
10. Before adding a new runtime `when`/`if` ladder over primitive kinds, inspectable/interface categories, projection mappings, collection shapes, bindable shapes, or runtime-class-name rules, first identify where `.cswinrt` owns that decision and prefer adding to the corresponding Kotlin shared abstraction instead of duplicating the branch table locally.
11. If Kotlin/JVM forces a narrower implementation than `.cswinrt`, keep the deviation at the final platform adaptation point only. Do not let that platform-specific adaptation justify copying the same type/category enumeration into `GuidGenerator`, `TypeNameSupport`, `Projections`, collection helpers, bindable helpers, generator emitters, or tests.
12. Treat repeated local branch tables for the same semantic classification as a design bug, not as harmless duplication. Either the responsibility belongs in one shared runtime abstraction or the `.cswinrt` ownership mapping has not been identified correctly yet.

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
7. `PLAN.md` must describe the intended design and implementation order clearly enough that another agent can continue the work without defaulting to test-driven reverse engineering.
8. For each active slice, `PLAN.md` should state the matching `.cswinrt` responsibility area and the intended Kotlin ownership so the development direction is explicit before tests are extended.
9. `PLAN.md` must encode the current implementation cadence in phase order, including which prerequisites must be complete before later modules such as `winrt-projections` or `winrt-samples` are allowed to expand.
10. `PLAN.md` must keep a short current-focus queue that identifies the next few slices to finish immediately and the major slices that are explicitly frozen until those focus items are closed.

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
6. Do not write new runtime or generator code that duplicates an existing type/category/shape enumeration when the corresponding `.cswinrt` slice already converges that decision into a shared abstraction. Matching `.cswinrt` responsibility convergence is mandatory, not optional cleanup.
