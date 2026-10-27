package io.github.composefluent.winrt.runtime

/** Desktop capture interop follows CsWinRT Runtime2's activation/object-reference
 * split. The SDK projection owns frame pools, sessions and SoftwareBitmap copying;
 * this bridge owns only the non-WinRT d3d11.dll and IWindowNative ABI.
 * Windows SDK: d3d11.h, windows.graphics.directx.direct3d11.interop.h,
 * windows.graphics.capture.interop.h; WinUI: microsoft.ui.xaml.window.h. */
private object XamlCaptureInterop {
    val library by lazy { WinRTPlatformApi.loadLibraryExWRaw("d3d11.dll", 0x00000800) } // System32; process-lifetime API module.
    val createDevice by lazy { WinRTPlatformApi.getProcAddressRaw(library, "D3D11CreateDevice") }
    val wrapDevice by lazy { WinRTPlatformApi.getProcAddressRaw(library, "CreateDirect3D11DeviceFromDXGIDevice") }
    val user32 by lazy { WinRTPlatformApi.loadLibraryExWRaw("user32.dll", 0x00000800) }
    val dwm by lazy { WinRTPlatformApi.loadLibraryExWRaw("dwmapi.dll", 0x00000800) }
    val clientRect by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "GetClientRect") }
    val clientToScreen by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "ClientToScreen") }
    val setWindowAttribute by lazy { WinRTPlatformApi.getProcAddressRaw(dwm, "DwmSetWindowAttribute") }
    val frameBounds by lazy { WinRTPlatformApi.getProcAddressRaw(dwm, "DwmGetWindowAttribute") }
    val dxgiDevice = guidOf("54EC77FA-1377-44E6-8C32-88FD5F44C84C")
    val windowNative = guidOf("EECDBF0E-BAE9-4CB6-A68E-9598E1CB57BB")
    val captureInterop = guidOf("3628E81B-3CAC-4C60-B7F4-23CE0E0C3356")
    val captureItem = guidOf("79C3F95B-31F7-4EC2-A464-632EF5D30760")
}

/** Returns an owned IInspectable supporting the SDK IDirect3DDevice projection. */
fun createWinUIXamlCaptureDevice(): ComObjectReference = PlatformAbi.confinedScope().use { scope ->
    val output = PlatformAbi.allocatePointerSlot(scope)
    var result = platformCreateXamlD3DDevice(XamlCaptureInterop.createDevice, 1, output)
    if (result < 0) {
        val failed = PlatformAbi.readPointer(output)
        if (!PlatformAbi.isNull(failed)) WinRTPlatformApi.releaseRaw(failed)
        PlatformAbi.writePointer(output, PlatformAbi.nullPointer)
        result = platformCreateXamlD3DDevice(XamlCaptureInterop.createDevice, 5, output) // WARP fallback.
    }
    val pointer = PlatformAbi.readPointer(output)
    if (result < 0) {
        if (!PlatformAbi.isNull(pointer)) WinRTPlatformApi.releaseRaw(pointer)
        ExceptionHelpers.throwExceptionForHR(result, "D3D11CreateDevice")
    }
    check(!PlatformAbi.isNull(pointer)) { "Direct3D returned no capture device." }
    ComObjectReference(pointer.asRawComPtr(), IID.IUnknown).use { device ->
        device.queryInterface(XamlCaptureInterop.dxgiDevice).getOrThrow().use { dxgi ->
            PlatformAbi.writePointer(output, PlatformAbi.nullPointer)
            val status = dxgi.comPtr.withCallPointer { leased ->
                platformWrapXamlD3DDevice(XamlCaptureInterop.wrapDevice, leased.asRawAddress(), output)
            }
            val wrapped = PlatformAbi.readPointer(output)
            if (status < 0) {
                if (!PlatformAbi.isNull(wrapped)) WinRTPlatformApi.releaseRaw(wrapped)
                ExceptionHelpers.throwExceptionForHR(status, "CreateDirect3D11DeviceFromDXGIDevice")
            }
            check(!PlatformAbi.isNull(wrapped)) { "Direct3D returned no WinRT capture device." }
            ComObjectReference(wrapped.asRawComPtr(), IID.IInspectable)
        }
    }
}

/** Captures the provided WinUI window only; no desktop/window enumeration or picker. */
fun createWinUIXamlWindowCaptureItem(window: ComObjectReference): ComObjectReference =
    withXamlCaptureWindow(window) { hwnd ->
        PlatformAbi.confinedScope().use { scope ->
            val output = PlatformAbi.allocatePointerSlot(scope)
            check(!PlatformAbi.isNull(hwnd)) { "The preview window is closed." }
            ActivationFactory.get("Windows.Graphics.Capture.GraphicsCaptureItem", XamlCaptureInterop.captureInterop).use { factory ->
                val iid = PlatformAbi.allocateBytes(scope, 16, 4)
                PlatformAbi.writeGuid(iid, XamlCaptureInterop.captureItem)
                PlatformAbi.writePointer(output, PlatformAbi.nullPointer)
                val result = factory.comPtr.withCallPointer { ComVtableInvoker.invokeArgs(it, 3, hwnd, iid, output) }
                val item = PlatformAbi.readPointer(output)
                if (result < 0) {
                    if (!PlatformAbi.isNull(item)) WinRTPlatformApi.releaseRaw(item)
                    ExceptionHelpers.throwExceptionForHR(result, "IGraphicsCaptureItemInterop.CreateForWindow")
                }
                check(!PlatformAbi.isNull(item)) { "Windows returned no capture item." }
                ComObjectReference(item.asRawComPtr(), XamlCaptureInterop.captureItem)
            }
        }
    }

internal expect fun platformCreateXamlD3DDevice(procedure: RawAddress, driver: Int, output: RawAddress): Int
internal expect fun platformWrapXamlD3DDevice(procedure: RawAddress, device: RawAddress, output: RawAddress): Int

/** WGC includes DWM nonclient pixels even for a borderless AppWindow. Resolve the
 * client origin against DWMWA_EXTENDED_FRAME_BOUNDS in the window UI thread's DPI
 * context, instead of guessing title-bar/border sizes or assuming symmetric edges. */
fun winUIXamlWindowCaptureRegion(window: ComObjectReference): WinUIXamlVisualBounds =
    withXamlCaptureWindow(window) { hwnd ->
        PlatformAbi.confinedScope().use { scope ->
            val client = PlatformAbi.allocateBytes(scope, 16, 4)
            val origin = PlatformAbi.allocateBytes(scope, 8, 4)
            val frame = PlatformAbi.allocateBytes(scope, 16, 4)
            PlatformAbi.zeroBytes(origin, 8)
            check(platformReadXamlCaptureWindow(XamlCaptureInterop.clientRect, hwnd, client) != 0) { "Cannot read the preview client size." }
            check(platformReadXamlCaptureWindow(XamlCaptureInterop.clientToScreen, hwnd, origin) != 0) { "Cannot locate the preview client area." }
            ExceptionHelpers.throwExceptionForHR(platformXamlCaptureDwmAttribute(XamlCaptureInterop.frameBounds, hwnd, 9, frame, 16), "DwmGetWindowAttribute")
            fun int(address: RawAddress, offset: Long = 0) = PlatformAbi.readInt32(PlatformAbi.slice(address, offset, 4))
            WinUIXamlVisualBounds((int(origin) - int(frame)).toDouble(), (int(origin, 4) - int(frame, 4)).toDouble(),
                (int(client, 8) - int(client)).toDouble(), (int(client, 12) - int(client, 4)).toDouble())
        }
    }

/** Copy the physical client rectangle without scaling or changing premultiplied BGRA. */
fun cropWinUIXamlCompositionFrame(width: Int, height: Int, pixels: ByteArray,
    client: WinUIXamlVisualBounds, viewport: WinUIXamlVisualBounds? = null): WinUIXamlVisualImage {
    val limit = WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION
    require(width in 1..limit + 256 && height in 1..limit + 256 && pixels.size.toLong() == 4L * width * height)
    require(listOf(client.x, client.y, client.width, client.height).all { it.isFinite() && it == it.toInt().toDouble() })
    val x = client.x.toInt(); val y = client.y.toInt(); val w = client.width.toInt(); val h = client.height.toInt()
    require(x >= 0 && y >= 0 && w in 1..limit && h in 1..limit && x.toLong() + w <= width && y.toLong() + h <= height) {
        "The composition frame does not contain the complete preview client area."
    }
    val result = ByteArray(4 * w * h)
    repeat(h) { row ->
        val start = 4 * ((y + row) * width + x)
        pixels.copyInto(result, 4 * row * w, start, start + 4 * w)
    }
    return WinUIXamlVisualImage(w, h, result, viewport, "Composition")
}

internal expect fun platformReadXamlCaptureWindow(procedure: RawAddress, window: RawAddress, output: RawAddress): Int
internal expect fun platformXamlCaptureDwmAttribute(procedure: RawAddress, window: RawAddress, attribute: Int, output: RawAddress, bytes: Int): Int

private val compositionXamlSurfaces = setOf(
    "Microsoft.UI.Xaml.Controls.MediaPlayerElement", "Windows.UI.Xaml.Controls.MediaPlayerElement",
    "Windows.UI.Xaml.Controls.MediaElement", "Microsoft.UI.Xaml.Controls.SwapChainPanel",
    "Windows.UI.Xaml.Controls.SwapChainPanel", "Windows.UI.Xaml.Controls.SwapChainBackgroundPanel",
)

/** Shared by the SDK adapter and IDE; unused templates and similarly named project
 * classes must not trigger capture or a missing-pixel diagnostic. */
fun winUIXamlNeedsCompositionCapture(typeName: String): Boolean = typeName in compositionXamlSurfaces


private fun <T> withXamlCaptureWindow(window: ComObjectReference, action: (RawAddress) -> T): T =
    window.queryInterface(XamlCaptureInterop.windowNative).getOrThrow().use { native ->
        PlatformAbi.confinedScope().use { scope ->
            val output = PlatformAbi.allocatePointerSlot(scope)
            ExceptionHelpers.throwExceptionForHR(native.comPtr.withCallPointer { ComVtableInvoker.invokeArgs(it, 3, output) }, "IWindowNative.WindowHandle")
            val hwnd = PlatformAbi.readPointer(output)
            check(!PlatformAbi.isNull(hwnd)) { "The preview window is closed." }
            action(hwnd)
        }
    }

/** The isolated artboard is rectangular; Windows 11 DWM corner decoration must
 * not erase its pixels. Windows 10 does not support attribute 33 and has no rounded
 * corners, so E_INVALIDARG is the documented downlevel absence, not a capture error. */
fun configureWinUIXamlCompositionWindow(window: ComObjectReference) = withXamlCaptureWindow(window) { hwnd ->
    PlatformAbi.confinedScope().use { scope ->
        val value = PlatformAbi.allocateBytes(scope, 4, 4)
        PlatformAbi.writeInt32(value, 1) // DWMWCP_DONOTROUND.
        val result = platformXamlCaptureDwmAttribute(XamlCaptureInterop.setWindowAttribute, hwnd, 33, value, 4)
        if (result != 0x80070057.toInt()) ExceptionHelpers.throwExceptionForHR(result, "DwmSetWindowAttribute(WindowCornerPreference)")
    }
}
