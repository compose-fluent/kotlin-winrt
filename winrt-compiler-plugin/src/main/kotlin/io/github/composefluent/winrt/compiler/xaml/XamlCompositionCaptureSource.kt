package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.xamlKotlinNamespace
import io.github.composefluent.winrt.runtime.XamlFramework

/** Windows.Graphics.Capture owns composed pixels (including swap chains/video).
 * SoftwareBitmap owns surface readback; the runtime bridge owns desktop COM interop.
 * This operates only on the isolated designer's own visual/window. */
internal fun xamlCompositionCaptureSource(framework: XamlFramework): String = """
          suspend fun captureComposition(captureElement: FrameworkElement, region: WinUIXamlVisualBounds,
            viewport: WinUIXamlVisualBounds?, width: Int, height: Int, dpi: Double): WinUIXamlVisualImage {
            check(windows.graphics.capture.GraphicsCaptureSession.isSupported()) { "Windows composition capture is unavailable." }
            val pixelWidth = (width * dpi).toInt().coerceIn(1, WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION)
            val pixelHeight = (height * dpi).toInt().coerceIn(1, WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION)
            val oldTransform = captureElement.renderTransform
            ${if (framework == XamlFramework.WinUI) """val host = requireNotNull(requireNotNull(designWindow).appWindow)
            val oldSize = host.clientSize""" else ""}
            ${if (framework == XamlFramework.UWP) """var visualSurface: windows.ui.composition.CompositionVisualSurface? = null
            var captureBrush: windows.ui.composition.CompositionSurfaceBrush? = null
            var captureVisual: windows.ui.composition.SpriteVisual? = null""" else ""}
            var pool: windows.graphics.capture.Direct3D11CaptureFramePool? = null
            var session: windows.graphics.capture.GraphicsCaptureSession? = null
            var device: windows.graphics.directx.direct3d11.IDirect3DDevice? = null
            try {
              ${if (framework == XamlFramework.WinUI) """val compositionDpi = captureElement.xamlRoot?.rasterizationScale ?: dpi
              captureElement.renderTransform = ${framework.xamlKotlinNamespace}.media.ScaleTransform().apply {
                scaleX = pixelWidth / (region.width * compositionDpi); scaleY = pixelHeight / (region.height * compositionDpi)
              }
              host.resizeClient(windows.graphics.SizeInt32(pixelWidth, pixelHeight))""" else ""}
              captureElement.updateLayout()
              // Rendering is raised before the SDK commits that frame. Await the
              // next rendering boundary too, so WGC cannot return the old camera
              // with the new HWND size. UpdateLayout does not commit composition.
              val rendered = CompletableDeferred<Unit>()
              var renderingTicks = 0
              fun onRendering(sender: Any?, args: Any?) {
                if (++renderingTicks == 2) rendered.complete(Unit)
              }
              val renderingHandler = ::onRendering
              ${framework.xamlKotlinNamespace}.media.CompositionTarget.rendering.add(renderingHandler)
              try { withTimeout(5_000) { rendered.await() } }
              finally { ${framework.xamlKotlinNamespace}.media.CompositionTarget.rendering.remove(renderingHandler) }
              device = createWinUIXamlCaptureDevice().use { it.asWinRT<windows.graphics.directx.direct3d11.IDirect3DDevice>() }
              val item = ${if (framework == XamlFramework.WinUI) """createWinUIXamlWindowCaptureItem(requireNotNull(designWindow).nativeObject).use {
                it.asWinRT<windows.graphics.capture.GraphicsCaptureItem>()
              }""" else """run {
                // XAML's handed-out visual can have zero Size. Own a bounded
                // capture visual instead of changing the SDK-owned visual tree.
                val source = requireNotNull(windows.ui.xaml.hosting.ElementCompositionPreview.getElementVisual(captureElement))
                val compositor = requireNotNull(source.compositor)
                visualSurface = requireNotNull(compositor.createVisualSurface()).apply {
                  sourceVisual = source
                  sourceSize = windows.foundation.numerics.Vector2(region.width.toFloat(), region.height.toFloat())
                }
                captureBrush = requireNotNull(compositor.createSurfaceBrush(visualSurface)).apply {
                  stretch = windows.ui.composition.CompositionStretch.Fill
                }
                captureVisual = requireNotNull(compositor.createSpriteVisual()).apply {
                  size = windows.foundation.numerics.Vector2(pixelWidth.toFloat(), pixelHeight.toFloat())
                  brush = captureBrush
                }
                // Publish the owned visual's size/brush before WGC reads its bounds.
                withTimeout(5_000) { requireNotNull(compositor.requestCommitAsync()).await() }
                requireNotNull(windows.graphics.capture.GraphicsCaptureItem.createFromVisual(captureVisual))
              }"""}
              val client = ${if (framework == XamlFramework.WinUI) "winUIXamlWindowCaptureRegion(requireNotNull(designWindow).nativeObject)" else "WinUIXamlVisualBounds(0.0, 0.0, pixelWidth.toDouble(), pixelHeight.toDouble())"}
              require(client.width >= pixelWidth && client.height >= pixelHeight) { "The SDK has not committed the composition camera size." }
              val frameSize = item.size
              require(frameSize.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION + 256 && frameSize.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION + 256) { "Invalid composition surface size: ${'$'}{frameSize.width} x ${'$'}{frameSize.height}." }
              pool = requireNotNull(windows.graphics.capture.Direct3D11CaptureFramePool.createFreeThreaded(device,
                windows.graphics.directx.DirectXPixelFormat.B8G8R8A8UIntNormalized, 2,
                frameSize))
              session = requireNotNull(pool.createCaptureSession(item))
              session.isCursorCaptureEnabled = false
              session.startCapture()
              // Await a frame of the current camera size, not a stale resize frame.
              // Yield to the UI dispatcher so layout/composition can advance.
              var lastFrame = "no frame"
              return try { withTimeout(5_000) {
                var captured: WinUIXamlVisualImage? = null
                while (captured == null) {
                  val frame = pool.tryGetNextFrame()
                  if (frame == null) { delay(16); continue }
                  try {
                    val size = frame.contentSize
                    lastFrame = "${'$'}{size.width} x ${'$'}{size.height}"
                    if (size.width != frameSize.width || size.height != frameSize.height) { delay(16); continue }
                    val bitmap = requireNotNull(requireNotNull(windows.graphics.imaging.SoftwareBitmap.createCopyFromSurfaceAsync(
                      requireNotNull(frame.surface), windows.graphics.imaging.BitmapAlphaMode.Premultiplied)).await())
                    try {
                      require(bitmap.pixelWidth == frameSize.width && bitmap.pixelHeight == frameSize.height) { "Unexpected composition frame size." }
                      val buffer = windows.storage.streams.Buffer((4 * frameSize.width * frameSize.height).toUInt())
                      bitmap.copyToBuffer(buffer)
                      require(buffer.length.toInt() == 4 * frameSize.width * frameSize.height) { "Incomplete composition frame." }
                      captured = cropWinUIXamlCompositionFrame(frameSize.width, frameSize.height,
                        readWinUIXamlVisualPixels(buffer.nativeObject, buffer.length.toInt()),
                        client.copy(width = pixelWidth.toDouble(), height = pixelHeight.toDouble()), viewport)
                    } finally { bitmap.close() }
                  } finally { frame.close() }
                }
                requireNotNull(captured)
              } } catch (error: TimeoutCancellationException) {
                val size = item.size
                throw IllegalStateException("Composition capture received ${'$'}lastFrame; item ${'$'}{size.width} x ${'$'}{size.height}, expected ${'$'}pixelWidth x ${'$'}pixelHeight.", error)
              }
            } finally {
              try { session?.close() } finally {
                try { pool?.close() } finally {
                  try { device?.close() } finally {
                    captureElement.renderTransform = oldTransform
                    ${if (framework == XamlFramework.WinUI) "host.resizeClient(oldSize)" else """try { captureVisual?.close() } finally {
                      try { captureBrush?.close() } finally { visualSurface?.close() }
                    }"""}
                  }
                }
              }
            }
          }
""".trimIndent()
