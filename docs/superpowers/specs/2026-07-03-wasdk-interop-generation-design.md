# Windows App SDK Interop Generation Design

## Scope

Implement the Kotlin projection behavior for `WinRT.Interop` helpers and `Microsoft.UI.Win32Interop` using `.cswinrt` as the reference. This slice is projection/generator owned. It must not add public helper APIs to `winrt-runtime`.

The initial usable surface is:

- `winrt.interop.WindowNative.getWindowHandle(target)`
- `winrt.interop.InitializeWithWindow.initialize(target, hwnd)`
- `microsoft.ui.Win32Interop` conversions between HWND/HMONITOR/HICON-style handles and `WindowId`, `DisplayId`, and `IconId`

Broader Windows `I*Interop` wrappers from `ComInteropHelpers.cs` remain out of scope unless needed by a later projection slice.

## CsWinRT Reference

`WinRT.Interop` follows these CsWinRT areas:

- `.cswinrt/src/cswinrt/WinRT.Interop.idl` defines internal interop interfaces and `HWND`.
- `.cswinrt/src/cswinrt/strings/ComInteropHelpers.cs` adds friendly public helper wrappers such as `WindowNative` and `InitializeWithWindow`.
- `.cswinrt/nuget/Microsoft.Windows.CsWinRT.targets` includes `WinRT.Interop.winmd` only when Windows projection input is included.

`Microsoft.UI.Win32Interop` follows Windows App SDK interop behavior:

- Windows App SDK `Microsoft.UI.Interop.h` dynamically loads `Microsoft.Internal.FrameworkUdk.dll`.
- It resolves exports such as `Windowing_GetWindowIdFromWindow`, `Windowing_GetWindowFromWindowId`, `Windowing_GetDisplayIdFromMonitor`, `Windowing_GetMonitorFromDisplayId`, `Windowing_GetIconIdFromIcon`, and `Windowing_GetIconFromIconId`.
- The value types `Microsoft.UI.WindowId`, `Microsoft.UI.DisplayId`, and `Microsoft.UI.IconId` remain normal projection-owned value types from WASDK metadata.

## Ownership Model

The generated interop helpers are projection surface owned by the module that owns the corresponding projected namespace.

- A module that owns the Windows projection emits `winrt.interop` additions.
- A module that owns the `Microsoft.UI` WASDK projection emits `microsoft.ui.Win32Interop`.
- Downstream modules that also configure the same WASDK metadata must consume dependency identity and suppress duplicate helper output.
- If two dependencies claim the same helper FQN or claim overlapping WASDK projection ownership with incompatible metadata baselines, generation must fail closed instead of relying on classpath order.

This matches the existing owner-scoped identity direction in the repository and prevents duplicate FQNs when `lib A` projects WASDK and `lib B` depends on `lib A` while also listing WASDK inputs.

## Architecture

`winrt-runtime` remains limited to reusable ABI/platform primitives:

- `QueryInterface`
- raw vtable invocation support
- native library loading and `GetProcAddress`
- HRESULT translation

The generator owns source additions:

- Add a source-addition model for CsWinRT-style namespace helper output.
- Treat additions as owner-scoped generated FQNs, not as untracked support files.
- Publish addition ownership in dependency identity beside projected type ownership.
- Suppress addition rendering when a dependency identity already owns that addition.

The projection output owns public package shape:

- `winrt.interop` package for `WindowNative` and `InitializeWithWindow`.
- `microsoft.ui` package for `Win32Interop`.
- Handle parameters use the existing pointer-shaped mapping for `HWND`/native handles.

## Error Handling

Generated helpers call existing runtime primitives and translate HRESULT failures through the same runtime error path as ordinary ABI calls.

`Microsoft.UI.Win32Interop` mirrors the native header behavior:

- Try to get or load `Microsoft.Internal.FrameworkUdk.dll`.
- Resolve every required export for the helper surface.
- Fail closed when the DLL or an export is unavailable.
- Do not silently return zero handles for missing framework exports.

## Testing

Add targeted coverage before broad sample validation:

- Generator regression: WASDK owner emits exactly one `microsoft.ui.Win32Interop` addition.
- Dependency identity regression: downstream WASDK generation suppresses additions already owned by a dependency.
- Duplicate-owner regression: two incompatible owners for the same addition fail closed.
- Runtime/platform smoke where practical: `FrameworkUdk` export resolution is modeled through existing `WinRTPlatformApi` seams without adding public runtime helpers.
- Projection compile gates for Windows App SDK JVM and `mingwX64` remain the final validation surface.

## Non-Goals

- Do not move public interop helper APIs into `winrt-runtime`.
- Do not hand-maintain projection files in `winrt-projections`.
- Do not expand all `ComInteropHelpers.cs` wrappers in this slice.
- Do not infer helper behavior from samples; use `.cswinrt` and Windows App SDK interop headers as the reference.
