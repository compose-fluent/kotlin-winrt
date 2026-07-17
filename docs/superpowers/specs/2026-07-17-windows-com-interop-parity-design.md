# Windows COM Interop Helper Parity Design

## Scope

Complete the Kotlin projection surface corresponding to the public helpers in
`.cswinrt/src/cswinrt/strings/ComInteropHelpers.cs`. The completed Windows SDK
projection exposes all 17 CsWinRT helper types when they are supported by the
declared Windows SDK Universal API Contract (UAC) version.

This slice absorbs the current `InputPaneInterop` work. It also preserves the
already generated `winrt.interop.WindowNative` and
`winrt.interop.InitializeWithWindow` helpers. `Microsoft.UI.Win32Interop` is a
separate Windows App SDK addition and remains unchanged.

## Reference Behavior

CsWinRT combines two reference surfaces:

- `WinRT.Interop.idl` defines internal interop interfaces and their IIDs.
- `ComInteropHelpers.cs` supplies the public, friendly helper classes in the
  projected Windows namespaces.

When a Windows projection is requested, CsWinRT adds its bundled
`WinRT.Interop.winmd` and injects `ComInteropHelpers.cs`. The helper source uses
`UAC_VERSION_*` preprocessor conditions, while the checked-in CsWinRT Windows
projection currently defines `UAC_VERSION_15` directly.

C++/WinRT obtains its platform WinMDs and UM interop headers from the selected
Windows SDK. Its CLI accepts `sdk`, a concrete SDK version, `local`, or explicit
WinMD paths. Its MSBuild integration resolves platform metadata from
`TargetPlatformVersion`; it does not have a public-helper layer equivalent to
`ComInteropHelpers.cs`.

Kotlin/WinRT will preserve the reference API and ABI behavior without copying
CsWinRT's fixed `UAC_VERSION_15` build constant. The UAC version will instead be
read from the Windows SDK that the Kotlin build explicitly declares.

## Windows SDK And UAC Contract

Windows interop additions require an explicit `windowsSdk(...)` projection
input. A Windows namespace discovered only through an arbitrary path or NuGet
input does not implicitly select a Windows SDK or manufacture a UAC version.

The existing Windows SDK resolver already reads
`Platforms/UAP/<sdk-version>/Platform.xml` to locate contract WinMDs. It will
also retain the declared contract versions as structured metadata. The entry
named `Windows.Foundation.UniversalApiContract` supplies the effective UAC
major version.

The resolver retains a structured Windows SDK selection containing the
resolved SDK version and its contract inventory. That selection flows through
`WinRTMetadataCache` into `WinRTMetadataModel`; a same-named contract found only
in an arbitrary path or NuGet input is not treated as proof that a Windows SDK
was declared. Duplicate contract entries from declared Windows SDK sources are
normalized deterministically. The effective model uses the greatest available
major version, matching the union of resolved Windows SDK metadata inputs.

Windows SDK resolution already fails when `Platform.xml`, a referenced
contract, or its WinMD is missing. A Windows interop projection must also fail
closed if the resolved Windows SDK does not declare
`Windows.Foundation.UniversalApiContract`; it must not guess from an SDK build
number.

## Interop Catalog

The Kotlin equivalent of `WinRT.Interop.idl` plus `ComInteropHelpers.cs` is one
central, declarative COM interop adapter catalog owned by `winrt-metadata`.
Each adapter descriptor records:

- public generated type name and owning Windows namespace;
- minimum UAC major version;
- activation/runtime class and internal interop IID;
- IInspectable or IUnknown vtable base shape;
- public method names, ABI slots, parameter types, return type, and result IID
  source;
- projected types that must be available locally or from a projection
  dependency.

The catalog is the only enumeration of this helper family. Metadata inventory,
source-addition ownership, generator rendering, and tests consume the same
descriptors rather than repeating type or method tables.

The public-helper UAC gates mirror `ComInteropHelpers.cs`:

| Minimum UAC | Helpers |
| --- | --- |
| Windows projection | `winrt.interop.WindowNative`, `winrt.interop.InitializeWithWindow` |
| 1 | `DragDropManagerInterop`, `PrintManagerInterop`, `SystemMediaTransportControlsInterop`, `PlayToManagerInterop`, `UserConsentVerifierInterop`, `WebAuthenticationCoreManagerInterop`, `AccountsSettingsPaneInterop`, `InputPaneInterop`, `UIViewSettingsInterop`, `DataTransferManagerInterop` |
| 2 | `SpatialInteractionManagerInterop` |
| 3 | `RadialControllerConfigurationInterop`, `RadialControllerInterop` |
| 4 | `RadialControllerIndependentInputSourceInterop` |
| 15 | `DisplayInformationInterop` |

These are the public source gates from `ComInteropHelpers.cs`, not the separate
preprocessor values used when compiling the internal `WinRT.Interop.idl`.

## Generator Architecture

Metadata projection inventory selects catalog entries by all of the following:

1. A Windows SDK source was explicitly declared.
2. The owning namespace passes include and addition-exclude filters.
3. The resolved UAC version meets the descriptor's minimum.
4. Required projected types are present in the resolved metadata model.

The generator then removes helper FQNs already owned by dependency identity,
using the existing source-addition suppression input. Metadata does not absorb
Gradle dependency policy.

The filtered descriptors determine `generatedTypeNames` and
`source-additions.tsv`. Therefore dependency identity publishes only helpers
that were actually generated. Existing duplicate-owner rejection and
downstream suppression remain unchanged.

Kotlin requires one package per source file, so the generator emits one public
helper file per descriptor instead of copying CsWinRT's single multi-namespace
C# file. A common renderer handles reusable ABI strategies:

- projected runtime-class return;
- projected interface/async return;
- unit return;
- RawAddress, HSTRING, and projected object inputs;
- default-interface and parameterized-interface result IIDs.

The renderer reuses existing projection type resolution, marshalling, and
`WinRTProjectionIntrinsic` lowering. It must not introduce a second primitive,
object, async, or parameter branch table. Generated helpers must not call
`ComVtableInvoker` or generic vararg fallback paths.

`DataTransferManagerInterop` remains a narrow catalog specialization because
CsWinRT also implements it outside `WinRT.Interop.idl`: it queries IID
`3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8` and calls IUnknown slots 3 and 4.

Public helper APIs remain projection-owned. `winrt-runtime` may only gain a
reusable ABI primitive if an existing intrinsic cannot represent a required
shape, and such a primitive must support both JVM and `mingwX64` without
exposing a Windows-helper-specific API.

## Data Flow

1. Gradle resolves the explicitly declared Windows SDK version.
2. `winrt-metadata` parses `Platform.xml`, resolves its contract WinMDs, and
   retains the contract-version inventory.
3. Metadata loading builds a normalized model carrying the effective UAC
   version.
4. Projection inventory selects catalog descriptors by namespace, UAC,
   required types, and dependency ownership.
5. The generator renders selected helper files and writes their exact FQNs to
   `source-additions.tsv`.
6. Gradle identity publishes those FQNs; downstream generation suppresses
   dependency-owned helpers.
7. The compiler plugin lowers helper intrinsics for JVM and `mingwX64` using
   the same ABI shapes as ordinary generated members.

## Error Handling

- Missing or malformed Windows SDK `Platform.xml` remains a metadata-source
  error.
- A declared Windows SDK without a Universal API Contract entry is a
  fail-closed metadata diagnostic when Windows interop additions are needed.
- A catalog entry whose required projected type is unavailable is omitted only
  when its namespace/type was intentionally filtered or dependency-owned;
  otherwise generation reports the missing prerequisite.
- Unsupported ABI shapes fail during generator planning, before partial helper
  files or identity manifests are written.
- HRESULT failures continue through the existing runtime error translation.
- Returned COM pointers are wrapped with the existing projected runtime-class,
  interface, and async ownership rules.

## Validation

Tests follow the repository phase order.

1. Metadata resolver tests build SDK fixtures whose `Platform.xml` declares
   UAC boundary versions 1, 2, 3, 4, 14, and 15, then assert the retained
   contract inventory.
2. Metadata inventory tests assert the exact helper set at every boundary and
   prove that non-WindowsSdk metadata does not trigger Windows helpers.
3. Generator regression tests assert all 17 helper FQNs and public method
   signatures at UAC 15 or later, plus boundary omissions at lower UAC levels.
4. Generator body tests cover runtime-class, unit, async, HSTRING, projected
   object, and DataTransfer IUnknown call shapes, and reject
   `ComVtableInvoker` or generic fallback calls.
5. Gradle identity tests prove that only emitted helpers are published and that
   dependency ownership suppresses the same FQNs downstream.
6. Actual Windows SDK projection generation verifies at least 10.0.19041.0,
   10.0.22000.0, 10.0.22621.0, and 10.0.26100.0 boundaries where installed or
   available in CI.
7. Windows SDK projection compilation passes for JVM and `mingwX64`; the
   broader metadata, generator, Gradle plugin, and projection gates run after
   targeted tests.

## Non-Goals

- Do not add helpers absent from the local CsWinRT `ComInteropHelpers.cs`, such
  as unrelated SDK-header-only interop interfaces.
- Do not expose the internal `WinRT.Interop.I*Interop` types as public Kotlin
  API.
- Do not move helper APIs into `winrt-runtime`.
- Do not add a guessed or manually overridden UAC version for builds that did
  not declare a Windows SDK.
- Do not change `Microsoft.UI.Win32Interop` behavior in this slice.
