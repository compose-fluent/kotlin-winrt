@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

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
    val framework by lazy { WinRTPlatformApi.loadLibraryExWRaw("Microsoft.Internal.FrameworkUdk.dll", 0) }
    val windowFromId by lazy { WinRTPlatformApi.getProcAddressRaw(framework, "Windowing_GetWindowFromWindowId") }
    val getWindow by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "GetWindow") }
    val threadId by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "GetWindowThreadProcessId") }
    val visible by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "IsWindowVisible") }
    val className by lazy { WinRTPlatformApi.getProcAddressRaw(user32, "GetClassNameW") }
    val dxgiDevice = guidOf("54EC77FA-1377-44E6-8C32-88FD5F44C84C")
    val windowNative = guidOf("EECDBF0E-BAE9-4CB6-A68E-9598E1CB57BB")
    val captureInterop = guidOf("3628E81B-3CAC-4C60-B7F4-23CE0E0C3356")
    val captureItem = guidOf("79C3F95B-31F7-4EC2-A464-632EF5D30760")
}

/** Returns an owned IInspectable supporting the SDK IDirect3DDevice projection. */
@InternalWinRTApi
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
@InternalWinRTApi
fun createWinUIXamlWindowCaptureItem(window: ComObjectReference): ComObjectReference =
    withXamlCaptureWindow(window) { createWinUIXamlCaptureItem(it) }

@InternalWinRTApi
fun createWinUIXamlCaptureItem(hwnd: RawAddress): ComObjectReference =
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

internal expect fun platformCreateXamlD3DDevice(procedure: RawAddress, driver: Int, output: RawAddress): Int
internal expect fun platformWrapXamlD3DDevice(procedure: RawAddress, device: RawAddress, output: RawAddress): Int

/** WGC includes DWM nonclient pixels even for a borderless AppWindow. Resolve the
 * client origin against DWMWA_EXTENDED_FRAME_BOUNDS in the window UI thread's DPI
 * context, instead of guessing title-bar/border sizes or assuming symmetric edges. */
@InternalWinRTApi
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
@InternalWinRTApi
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
@InternalWinRTApi
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
@InternalWinRTApi
fun configureWinUIXamlCompositionWindow(window: ComObjectReference) = withXamlCaptureWindow(window) { hwnd ->
    PlatformAbi.confinedScope().use { scope ->
        val value = PlatformAbi.allocateBytes(scope, 4, 4)
        PlatformAbi.writeInt32(value, 1) // DWMWCP_DONOTROUND.
        val result = platformXamlCaptureDwmAttribute(XamlCaptureInterop.setWindowAttribute, hwnd, 33, value, 4)
        if (result != 0x80070057.toInt()) ExceptionHelpers.throwExceptionForHR(result, "DwmSetWindowAttribute(WindowCornerPreference)")
    }
}

/** Microsoft.UI.Interop.h owns WindowId conversion; never reinterpret the ID as HWND. */
@InternalWinRTApi
fun winUIXamlCaptureWindowFromId(id: ULong): RawAddress = PlatformAbi.confinedScope().use { scope ->
    val output = PlatformAbi.allocatePointerSlot(scope)
    ExceptionHelpers.throwExceptionForHR(platformXamlCaptureWindowId(XamlCaptureInterop.windowFromId, id.toLong(), output), "GetWindowFromWindowId")
    PlatformAbi.readPointer(output).also { check(!PlatformAbi.isNull(it)) { "The captured window is closed." } }
}

/** Screen-pixel bounds let separate Popup HWNDs compose without changing application layout. */
@InternalWinRTApi
fun winUIXamlCaptureBounds(hwnd: RawAddress, client: Boolean = false): WinUIXamlVisualBounds = PlatformAbi.confinedScope().use { scope ->
    val rect = PlatformAbi.allocateBytes(scope, 16, 4)
    val origin = PlatformAbi.allocateBytes(scope, 8, 4)
    PlatformAbi.zeroBytes(origin, 8)
    if (client) {
        check(platformReadXamlCaptureWindow(XamlCaptureInterop.clientRect, hwnd, rect) != 0)
        check(platformReadXamlCaptureWindow(XamlCaptureInterop.clientToScreen, hwnd, origin) != 0)
    } else ExceptionHelpers.throwExceptionForHR(platformXamlCaptureDwmAttribute(XamlCaptureInterop.frameBounds, hwnd, 9, rect, 16), "DwmGetWindowAttribute")
    fun int(address: RawAddress, offset: Long = 0) = PlatformAbi.readInt32(PlatformAbi.slice(address, offset, 4))
    WinUIXamlVisualBounds((if(client) int(origin) else int(rect)).toDouble(),
        (if(client) int(origin, 4) else int(rect, 4)).toDouble(),
        (int(rect, 8)-int(rect)).toDouble(), (int(rect, 12)-int(rect, 4)).toDouble())
}

/** WinUI's legacy PopupWindowSiteBridge is absent from ContentIsland.Popups.
 * Enumerate only visible SDK popup hosts on the target window's thread and owner
 * chain. GetWindow traversal preserves native z-order; no desktop pixels are read. */
@InternalWinRTApi
fun winUIXamlCapturePopupWindows(main: RawAddress): List<RawAddress> = PlatformAbi.confinedScope().use { scope ->
    val thread = platformReadXamlCaptureWindow(XamlCaptureInterop.threadId, main, PlatformAbi.nullPointer)
    check(thread != 0) { "The captured window is closed." }
    val result = mutableListOf<RawAddress>()
    val name = PlatformAbi.allocateBytes(scope, 512, 2)
    fun ownedByTarget(window: RawAddress): Boolean {
        var owner = platformXamlCaptureWindowCommand(XamlCaptureInterop.getWindow, window, 4) // GW_OWNER
        repeat(64) {
            if (owner == main) return true
            if (PlatformAbi.isNull(owner)) return false
            owner = platformXamlCaptureWindowCommand(XamlCaptureInterop.getWindow, owner, 4)
        }
        return false
    }
    var current = platformXamlCaptureWindowCommand(XamlCaptureInterop.getWindow, main, 0) // GW_HWNDFIRST
    val visited = mutableSetOf<RawAddress>()
    while (!PlatformAbi.isNull(current) && visited.add(current)) {
        check(visited.size <= 4096) { "Too many windows in the capture owner chain." }
        if (current != main && platformReadXamlCaptureWindow(XamlCaptureInterop.threadId, current, PlatformAbi.nullPointer) == thread &&
            ownedByTarget(current) &&
            platformXamlCaptureWindowVisible(XamlCaptureInterop.visible, current)) {
            val count = platformXamlCaptureClassName(XamlCaptureInterop.className, current, name, 256)
            val text = buildString { repeat(count.coerceIn(0,255)) { append(PlatformAbi.readInt16(PlatformAbi.slice(name, it.toLong()*2, 2)).toInt().toChar()) } }
            if (text == "Microsoft.UI.Content.PopupWindowSiteBridge") result.add(current)
        }
        current = platformXamlCaptureWindowCommand(XamlCaptureInterop.getWindow, current, 2) // GW_HWNDNEXT
    }
    result.asReversed()
}

internal expect fun platformXamlCaptureWindowId(procedure: RawAddress, id: Long, output: RawAddress): Int
internal expect fun platformXamlCaptureWindowCommand(procedure: RawAddress, window: RawAddress, command: Int): RawAddress
internal expect fun platformXamlCaptureWindowVisible(procedure: RawAddress, window: RawAddress): Boolean
internal expect fun platformXamlCaptureClassName(procedure: RawAddress, window: RawAddress, output: RawAddress, count: Int): Int
