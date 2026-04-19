# Plan

## Notes

- [x] Establish the root implementation plan file and integrate it into the repository workflow
- [x] Rewrite AGENTS.md to make `.cswinrt` the primary reference and to define JVM-first execution, dual-target parity, commit discipline, and plan maintenance rules
- [x] Standardize the canonical plan file name to `PLAN.md`
- [x] Clarify in AGENTS.md that `.cswinrt` alignment is responsibility-by-responsibility, with an explicit mapping table rather than a misleading literal name match
- [x] Tighten AGENTS.md to require strict one-to-one module correspondence with `.cswinrt/src`, including matching top-level module names
- [x] Adjust AGENTS.md to use Kotlin-style top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, with tests kept inside each module
- [x] Exclude legacy `sample-jvm-winui3` from the active refactor scope unless explicitly requested

## JVM Primary Path

- [x] Establish the JVM-first Gradle multi-module structure with the Kotlin top-level module names `winrt-runtime`, `winrt-metadata`, `winrt-authoring`, `winrt-projections`, and `winrt-samples`, and wire it into settings.gradle.kts
- [ ] Migrate the existing Kotlin package layout to `io.github.kitectlab.winrt` (正在做: new modules use the canonical package root; legacy `sample-jvm-winui3` is out of current scope unless explicitly requested)
- [ ] Create the `winrt-runtime` module and land the JVM runtime foundation corresponding directly to `.cswinrt/src/WinRT.Runtime` (正在做: module now covers GUID/HRESULT primitives, COM/WinRT init, activation factory lookup, instance activation, minimal `IUnknown` lifetime, `IInspectable.GetRuntimeClassName`, and reusable `string/object/unit` call shapes)
- [ ] Create the `winrt-metadata` module and land the JVM metadata foundation aligned with the metadata-loading responsibilities inside `.cswinrt/src/cswinrt` (正在做: seeded the initial type/namespace model only)
- [ ] Create the generator surface aligned with `.cswinrt/src/cswinrt` while keeping Kotlin tests inside the owning modules
- [ ] Create the `winrt-authoring` module and land the JVM authoring and hosting foundation corresponding directly to `.cswinrt/src/Authoring` (正在做: module exists, host/source-generator parity still missing)
- [ ] Create the `winrt-projections` module and wire deterministic generated output corresponding directly to `.cswinrt/src/Projections` (正在做: module exists, generated bindings not yet migrated)
- [ ] Create the `winrt-samples` module and wire the JVM sample validation surface corresponding directly to `.cswinrt/src/Samples` (正在做: module now contains a real WinRT JSON smoke sample on top of `winrt-runtime`; legacy `sample-jvm-winui3` is not part of the current migration target)
- [ ] Place tests in the relevant module source sets instead of creating a separate top-level tests module
- [ ] Rename or delete legacy top-level modules that do not match the strict `.cswinrt/src` names (正在做: legacy `sample-jvm-winui3` may remain on disk for reference, but it is not part of the active refactor target)
- [ ] Complete WinUI 3 startup, bootstrap, resource, and message-loop validation through the `winrt-samples` module and per-module test layouts

## .cswinrt Alignment Slices

- [ ] Align activation factory lookup and runtime-class activation semantics (正在做: `winrt-runtime` now has JVM-side `RoGetActivationFactory`/`DllGetActivationFactory` scaffolding plus instance activation, but full manifest-free probing and factory caching still need parity work)
- [ ] Align COM/WinRT marshaling, HRESULT translation, and lifetime management (正在做: `winrt-runtime` now covers minimal `IUnknown` lifetime, `IInspectable.GetRuntimeClassName`, initialization scope management, and reusable `string/object/unit/int64` vtable call shapes, but marshaling breadth, ownership policies, and projected ABI coverage are still incomplete)
- [ ] Align delegate bridges, delegate handles, and callback marshaling
- [ ] Align generic interface projection, parameterized IID, and type-signature behavior (正在做: `winrt-runtime` now has initial `WinRtTypeSignature` rendering and parameterized IID hashing, but coverage is still limited to the minimal shapes currently needed by runtime slices)
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
