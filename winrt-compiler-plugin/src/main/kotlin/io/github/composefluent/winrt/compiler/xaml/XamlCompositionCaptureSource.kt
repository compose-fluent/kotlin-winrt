package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.runtime.XamlFramework

/** SDK projections own WGC frames; runtime owns HWND interop and pixel composition.
 * Capture source windows at their natural size: live layout and popup placement must
 * never be changed to implement the designer camera. */
internal fun xamlCompositionCaptureSource(framework: XamlFramework): String = """
          suspend fun captureCompositionItem(item: windows.graphics.capture.GraphicsCaptureItem): WinUIXamlVisualImage {
            check(windows.graphics.capture.GraphicsCaptureSession.isSupported()) { "Windows composition capture is unavailable." }
            var pool: windows.graphics.capture.Direct3D11CaptureFramePool? = null
            var session: windows.graphics.capture.GraphicsCaptureSession? = null
            val device = createWinUIXamlCaptureDevice().use { it.asWinRT<windows.graphics.directx.direct3d11.IDirect3DDevice>() }
            try {
              val frameSize = item.size
              require(frameSize.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION + 256 && frameSize.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION + 256) { "Invalid composition surface size." }
              pool = requireNotNull(windows.graphics.capture.Direct3D11CaptureFramePool.createFreeThreaded(device,
                windows.graphics.directx.DirectXPixelFormat.B8G8R8A8UIntNormalized, 2, frameSize))
              session = requireNotNull(pool.createCaptureSession(item))
              session.isCursorCaptureEnabled = false
              session.startCapture()
              return withTimeout(5_000) {
                var captured: WinUIXamlVisualImage? = null
                while (captured == null) {
                  val frame = pool.tryGetNextFrame()
                  if (frame == null) { delay(16); continue }
                  try {
                    val size = frame.contentSize
                    if (size.width != frameSize.width || size.height != frameSize.height) { delay(16); continue }
                    val bitmap = requireNotNull(requireNotNull(windows.graphics.imaging.SoftwareBitmap.createCopyFromSurfaceAsync(
                      requireNotNull(frame.surface), windows.graphics.imaging.BitmapAlphaMode.Premultiplied)).await())
                    try {
                      require(bitmap.pixelWidth == frameSize.width && bitmap.pixelHeight == frameSize.height) { "Unexpected composition frame size." }
                      val buffer = windows.storage.streams.Buffer((4 * frameSize.width * frameSize.height).toUInt())
                      bitmap.copyToBuffer(buffer)
                      require(buffer.length.toInt() == 4 * frameSize.width * frameSize.height) { "Incomplete composition frame." }
                      captured = WinUIXamlVisualImage(frameSize.width, frameSize.height,
                        readWinUIXamlVisualPixels(buffer.nativeObject, buffer.length.toInt()), captureBackend = "Composition")
                    } finally { bitmap.close() }
                  } finally { frame.close() }
                }
                requireNotNull(captured)
              }
            } finally {
              try { session?.close() } finally { try { pool?.close() } finally { device.close() } }
            }
          }

          suspend fun captureComposition(captureElement: FrameworkElement, region: WinUIXamlVisualBounds,
            viewport: WinUIXamlVisualBounds?, width: Int, height: Int, dpi: Double): WinUIXamlVisualImage {
            val pixelWidth = (width * dpi).toInt().coerceIn(1, WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION)
            val pixelHeight = (height * dpi).toInt().coerceIn(1, WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION)
            ${if (framework == XamlFramework.WinUI) """
            val xamlRoot = requireNotNull(captureElement.xamlRoot)
            val main = winUIXamlCaptureWindowFromId(requireNotNull(xamlRoot.contentIslandEnvironment).appWindowId.value)
            val client = winUIXamlCaptureBounds(main, client = true)
            val origin = requireNotNull(captureElement.transformToVisual(requireNotNull(xamlRoot.content))).transformPoint(windows.foundation.Point(0f, 0f))
            val screenRegion = WinUIXamlVisualBounds(client.x + (origin.x + region.x) * dpi,
              client.y + (origin.y + region.y) * dpi, region.width * dpi, region.height * dpi)
            val result = WinUIXamlVisualImage(pixelWidth, pixelHeight, ByteArray(4 * pixelWidth * pixelHeight), viewport, "Composition")
            // The main frame already includes inline popups. Separate HWND popups
            // must be composited exactly once, in native bottom-to-top order.
            for (window in listOf(main) + winUIXamlCapturePopupWindows(main)) {
              val bounds = winUIXamlCaptureBounds(window)
              if (bounds.x + bounds.width <= screenRegion.x || bounds.y + bounds.height <= screenRegion.y ||
                  bounds.x >= screenRegion.x + screenRegion.width || bounds.y >= screenRegion.y + screenRegion.height) continue
              val item = createWinUIXamlCaptureItem(window).use { it.asWinRT<windows.graphics.capture.GraphicsCaptureItem>() }
              val layer = captureCompositionItem(item)
              check(winUIXamlCaptureBounds(window) == bounds) { "The captured window moved; retry the frame." }
              compositeWinUIXamlOverlay(result, screenRegion, layer, bounds)
            }
            return result
            """ else """
            var visualSurface: windows.ui.composition.CompositionVisualSurface? = null
            var captureBrush: windows.ui.composition.CompositionSurfaceBrush? = null
            var captureVisual: windows.ui.composition.SpriteVisual? = null
            try {
              val source = requireNotNull(windows.ui.xaml.hosting.ElementCompositionPreview.getElementVisual(captureElement))
              val compositor = requireNotNull(source.compositor)
              visualSurface = requireNotNull(compositor.createVisualSurface()).apply {
                sourceVisual = source
                sourceOffset = windows.foundation.numerics.Vector2(region.x.toFloat(), region.y.toFloat())
                sourceSize = windows.foundation.numerics.Vector2(region.width.toFloat(), region.height.toFloat())
              }
              captureBrush = requireNotNull(compositor.createSurfaceBrush(visualSurface)).apply { stretch = windows.ui.composition.CompositionStretch.Fill }
              captureVisual = requireNotNull(compositor.createSpriteVisual()).apply {
                size = windows.foundation.numerics.Vector2(pixelWidth.toFloat(), pixelHeight.toFloat())
                brush = captureBrush
              }
              withTimeout(5_000) { requireNotNull(compositor.requestCommitAsync()).await() }
              val image = captureCompositionItem(requireNotNull(windows.graphics.capture.GraphicsCaptureItem.createFromVisual(captureVisual)))
              return image.copy(bounds = viewport)
            } finally {
              try { captureVisual?.close() } finally { try { captureBrush?.close() } finally { visualSurface?.close() } }
            }
            """}
          }
""".trimIndent()
