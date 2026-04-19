# Plan

## Notes

- [x] Establish the root implementation plan file and integrate it into the repository workflow
- [x] Rewrite AGENTS.md to make `.cswinrt` the primary reference and to define JVM-first execution, dual-target parity, commit discipline, and plan maintenance rules
- [x] Standardize the canonical plan file name to `PLAN.md`
- [x] Clarify in AGENTS.md that `.cswinrt` alignment is responsibility-by-responsibility, with an explicit mapping table rather than a misleading literal name match
- [x] Tighten AGENTS.md to require strict one-to-one module correspondence with `.cswinrt/src`, including matching top-level module names
- [x] Adjust AGENTS.md to use Kotlin-style top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, with tests kept inside each module

## JVM Primary Path

- [ ] Establish the JVM-first Gradle multi-module structure with the Kotlin top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, and wire it into settings.gradle.kts (正在做)
- [ ] Migrate the existing Kotlin package layout to `io.github.kitectlab.winrt`
- [ ] Create the `winrt-runtime` module and land the JVM runtime foundation corresponding directly to `.cswinrt/src/WinRT.Runtime`
- [ ] Create the `winrt-metadata` module and land the JVM metadata foundation aligned with the metadata-loading responsibilities inside `.cswinrt/src/cswinrt`
- [ ] Create the generator surface aligned with `.cswinrt/src/cswinrt` while keeping Kotlin tests inside the owning modules
- [ ] Create the `winrt-authoring` module and land the JVM authoring and hosting foundation corresponding directly to `.cswinrt/src/Authoring`
- [ ] Create the `winrt-projections` module and wire deterministic generated output corresponding directly to `.cswinrt/src/Projections`
- [ ] Create the `winrt-samples` module and wire the JVM sample validation surface corresponding directly to `.cswinrt/src/Samples`
- [ ] Place tests in the relevant module source sets instead of creating a separate top-level tests module
- [ ] Rename or delete legacy top-level modules that do not match the strict `.cswinrt/src` names
- [ ] Complete WinUI 3 startup, bootstrap, resource, and message-loop validation through the `winrt-samples` module and per-module test layouts

## .cswinrt Alignment Slices

- [ ] Align activation factory lookup and runtime-class activation semantics
- [ ] Align COM/WinRT marshaling, HRESULT translation, and lifetime management
- [ ] Align delegate bridges, delegate handles, and callback marshaling
- [ ] Align generic interface projection, parameterized IID, and type-signature behavior
- [ ] Align collection projection for iterable/list/map/view adapters
- [ ] Align async projection, task bridging, and completion-callback semantics
- [ ] Align authoring support, projected-object bridges, and hosting support
- [ ] Align WinUI-specific type mappings, Xaml metadata, and bootstrap behavior

## Generation And Validation

- [ ] Establish deterministic generator-output validation and regression checks
- [ ] Establish a targeted JVM unit-test and integration-test matrix
- [ ] Establish a Windows-first verification flow that prioritizes affected modules

## mingwX64 Parity

- [ ] After the matching JVM slices are complete, define the runtime parity plan for `mingwX64` under the shared contracts
- [ ] Create `sample-mingw-winui3` or an equivalent native validation module
- [ ] Add validation coverage that proves `mingwX64` behavior matches JVM semantics