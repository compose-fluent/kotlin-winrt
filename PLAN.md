# Plan

## Operating Rules

- Use `.cswinrt/` as the primary behavioral and architectural reference.
- Preserve dependency order: `winrt-runtime` -> `winrt-metadata` -> `winrt-generator` / `winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` -> `winrt-samples`.
- Keep runtime mechanics out of generated API-shape policy and keep sample code validation-only.
- Treat a CallSite as WinMD semantic metadata only. Generated declarations expose typed `TODO()` stubs and structured facts; the IR plugin plans marshaling and emits platform code from those facts and exact closed IR types.
- Do not serialize implementation plans, recursive recipes, ABI carriers, codec names, result factories, or transport choices into CallSite metadata, Base64 payloads, opaque sidecars, or projected-type enumerations.
- Keep runtime-owned and compiler-expanded intrinsic calls as peer implementations of the same contract. Do not add, remove, or substitute runtime-owned call sites during performance work.
- Put shared semantics in common code and isolate only platform invocation and memory mechanics in target source sets.
- Work on Windows with `KOTLIN_WINRT_WINDOWS_SDK_ROOT=D:\Windows Kits\10`; use `gradlew.bat` and targeted validation before aggregate gates.
- Preserve unrelated working-tree changes. Do not commit without explicit user authorization.

## Reference Mapping

- `.cswinrt/src/WinRT.Runtime` maps to `winrt-runtime`.
- `.cswinrt/src/cswinrt` maps to `winrt-metadata`, `winrt-generator`, and `winrt-compiler-plugin`.
- `.cswinrt/src/Projections` maps to `winrt-projections`.
- `.cswinrt/src/Authoring` maps to `winrt-authoring`.
- `.cswinrt/src/Samples` maps to `winrt-samples`; tests remain in their owning Kotlin modules.
- Active performance references are `.cswinrt/src/WinRT.Runtime`, `.cswinrt/src/cswinrt/code_writers.h`, and `.cswinrt/src/cswinrt/helpers.h`.

## Completed Baseline

- [x] Implement the runtime foundations: ABI primitives, initialization, COM lifetime and identity, activation, generic IIDs, delegates, events, collections, async, error propagation, weak/agile references, and WinUI hooks.
- [x] Implement WinMD loading and normalized metadata models for types, members, generics, interfaces, factories, parameter directions, layouts, and attributes.
- [x] Implement deterministic declaration/member generation, activation and composable surfaces, closed generics, authoring handoff, and JVM/Native compiler lowering.
- [x] Generate Windows SDK, Windows.UI.Xaml, Windows App SDK, and WebView2 projection artifacts without duplicate projected FQNs.
- [x] Implement authored metadata, TypeDetails, CCW/activation boundaries, native component/consumer fixtures, and HRESULT propagation.
- [x] Implement packaging and JVM/Native WinUI sample hosts, including the Controls Sample and WebView2 validation surface.

## Completed Performance Foundations

- [x] Mirror all 6 families and 97 methods from `.cswinrt/src/Benchmarks` across C++/WinRT, CsWinRT, Kotlin/Native, and Kotlin/JVM with one component, protocol, filter set, warmup policy, and checksum contract.
- [x] Share fixed-shape vtable dispatch, reusable ABI frames, direct Native calls, stable reference caches, and HSTRING access between runtime-owned and compiler-expanded paths.
- [x] Centralize RCW/CCW identity, ownership, call leases, escaped-reference promotion, and managed-projection state in common runtime contracts.
- [x] Restore common generated runtime-class RCW identity and weak-cache cleanup on JVM and `mingwX64`.
- [x] Use immutable update-time event snapshots and root native callback lifetime in event state.
- [x] Use the fixed-carrier JVM vtable invoker for `IUnknown::AddRef` and `IUnknown::Release`.

## Current Focus

- [ ] Common WinRT dictionary indexer performance is 正在做: keep `Map.get` as one `IMap.Lookup`/`IMapView.Lookup` ABI operation, with common code owning `E_BOUNDS`, nullable success, result ownership, metadata-composed shapes, and RCW identity for both call-site paths.
  - [x] Cache closed interface views by COM identity and IID without changing projected-object identity or lifetime.
  - [x] Inline the existing `releaseRaw` contract through the shared `IUnknown` slot table on JVM and `mingwX64` without changing CallSite metadata or runtime-owned membership.
  - [ ] Profile generated Native `ExistingDictionaryLookup2` and `ExistingDictionaryLookup3` after ABI return to identify remaining allocations, cache probes, wrapper creation, and ownership transitions.
  - [ ] Implement the next measured optimization in the common runtime contract; keep target code limited to invocation and memory adaptation.
  - [ ] Run the dictionary acceptance gates and either accept or revert the candidate.

## Next Queue

- [ ] Complete common managed-CCW construction parity with CsWinRT's once-per-type exposed-interface table and module-once TypeDetails registration.
  - [x] Compose one immutable metadata-driven `WinRTCcwDefinition` per authored type and keep value-dependent factories separate.
  - [x] Use cached `HeapAlloc`/`HeapFree` entry points for JVM native storage.
  - [ ] Profile remaining per-instance host allocation, interface-table setup, and lifetime initialization on both targets.
  - [ ] Remove the next common per-instance cost without retaining managed instances in static caches.
  - [ ] Run construction, lifetime, compiler, benchmark, and memory gates.
- [ ] Complete value-reference host construction for `IReference<T>`, `IReferenceArray<T>`, and `IPropertyValue`.
  - [x] Share immutable metadata-composed closed-value shapes and pass temporary borrowed interface pointers directly to synchronous setters.
  - [x] Cache exact inbound decode plans while preserving compatibility for unknown external metadata.
  - [ ] Remove the per-host weak-shape lookup for retained definitions without caching boxed values or changing COM identity.
  - [ ] Validate scalar, array, enum, struct, delegate, nullable, lifetime, and long-run memory behavior on both targets.
- [ ] Complete generated delegate RCW performance while preserving owned-return identity, borrowed authoring inputs, and metadata-composed generic IIDs.
  - [x] Use one weak hot RCW lookup, publish one shared weak-reference handle, cache delegate `IReference<T>` IIDs, and reuse output scratch frames.
  - [ ] Profile remaining Native nullable/new delegate allocation and cleanup costs with existing-delegate controls.
  - [ ] Implement only a common ownership-preserving fast path that also works for runtime-only consumers.
  - [ ] Run identity, compiler, benchmark, and long-run memory gates.
- [ ] Complete event callback and authored event-accessor performance.
  - [ ] Validate the existing-managed-object lease probe on `EventPerf.InvokeNativeIntEvent`; accept or revert it from serialized A/B evidence.
  - [ ] Align authored event raises with `.cswinrt/src/Benchmarks/EventPerf.cs` by using the update-time `EventRegistrationTokenTable` snapshot.
  - [ ] Validate single-handler and immutable multi-handler snapshots for ordering, duplicate registration, removal, mutation during invoke, and lifetime.
  - [ ] Carry typed callback context into compiler-lowered callbacks only after the common event semantics are accepted.

## Deferred

- [ ] Reduce generated projection size only after the performance queue is accepted; preserve coordinates, public API, hot paths, and multi-module ownership.
- [ ] Run the WinUI Controls Sample on JVM and `mingwX64` after performance and size work is complete; do not move missing behavior into samples.
- [ ] Keep broad runtime, authoring, projection, packaging, and sample features frozen while the performance queue is active.

## Acceptance Gates

Every active performance slice must pass all applicable gates before completion:

- Targeted and full JVM/`mingwX64` runtime tests.
- CallSite, compiler-plugin, generator, projection compilation, and direct-lowering checks.
- Plugin-free published-runtime consumers whenever a public inline or runtime-owned implementation changes.
- Checksum-matched serialized A/B plus a same-session C++/WinRT, CsWinRT, Kotlin/Native, and Kotlin/JVM comparison.
- A long-run memory gate for ownership-sensitive paths.
- `git diff --check` and exact commit-scope inspection.
