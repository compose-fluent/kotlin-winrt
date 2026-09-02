# Plan

## Purpose

This file contains the approved implementation scope, dependency order, and
current status of the repository. Keep measurements, command output, rejected
experiments, and other historical detail in the dedicated reference documents.

## Dependency Order

`winrt-runtime` -> `winrt-metadata` -> `winrt-generator` /
`winrt-compiler-plugin` -> `winrt-projections` -> `winrt-authoring` ->
`winrt-samples`

Use `.cswinrt/` as the behavioral and architectural reference. Keep shared
contracts in common code and isolate only irreducible JVM or `mingwX64` FFI
mechanics in target-specific code.

## Reference Mapping

- `.cswinrt/src/WinRT.Runtime` -> `winrt-runtime`
- `.cswinrt/src/cswinrt` -> `winrt-metadata`, `winrt-generator`, and `winrt-compiler-plugin`
- `.cswinrt/src/Projections` -> `winrt-projections`
- `.cswinrt/src/Authoring` -> `winrt-authoring`
- `.cswinrt/src/Samples` -> `winrt-samples`

## Completed Baseline

- [x] Runtime ABI, initialization, object identity, activation, marshaling, collections, async, delegates, events, and lifetime support.
- [x] WinMD ingestion, normalized metadata, generic/interface/factory modeling, and parameter direction handling.
- [x] Deterministic declaration and member generation, compiler lowering, activation surfaces, and closed generic support.
- [x] Windows SDK, Windows App SDK, XAML, and WebView2 projection artifacts without duplicate projected FQNs.
- [x] Authoring metadata, TypeDetails, CCW/activation boundaries, native fixtures, packaging, and WinUI sample hosts.
- [x] The cross-target benchmark catalog and shared runtime performance foundations.

## Current Queue

- [ ] doing: Profile the Native delegate-marshaling and weak-reference hot paths; admit another optimization only when target-specific evidence identifies removable work and the JVM control remains valid.

## Deferred

- [ ] skipped: Broader event/delegate, feature, packaging, and sample expansion until the current runtime-performance queue is closed.

## Acceptance

- Validate affected modules on Windows for both JVM and `mingwX64` where supported.
- Follow the runtime -> metadata -> generator -> projection -> authoring -> sample dependency order.
- Keep ownership, identity, lifetime, ABI, and public API behavior aligned with `.cswinrt`.
- Require focused tests before broader gates; performance changes also require reproducible target evidence and no material cross-target regression.
- Preserve unrelated working-tree changes and do not commit without explicit user authorization.

## Status Vocabulary

Use `[x]` for completed entries, `[ ]` for incomplete entries, `doing:` for
active work, and `skipped:` for intentionally skipped work. Do not add progress
logs or historical narratives to this file.
