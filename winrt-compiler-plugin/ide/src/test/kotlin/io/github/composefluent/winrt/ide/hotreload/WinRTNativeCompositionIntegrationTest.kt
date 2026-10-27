package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Exercises Windows.Graphics.Capture and SoftwareBitmap through generated SDK projections. */
class WinRTNativeCompositionIntegrationTest {
    @Test fun live_capture_preserves_layout_and_composes_real_swapchains() {
        val configured = System.getProperty("winrt.ide.liveCompositionSession")
        assumeTrue(configured != null)
        val directory = Path.of(requireNotNull(configured))
        WinUIXamlHotReloadClient.discover(directory).single().use { connection ->
            val root = connection.request().roots.single { it.className == "sample.hello.MainWindow" }
            val request = WinUIXamlInspectionRequest(root.className, root.resourcePath, readProperties = false)
            fun inspect(value: WinUIXamlInspectionRequest): WinUIXamlVisualSnapshot {
                val reply = connection.inspect(value)
                assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                return requireNotNull(reply.inspection)
            }
            val before = inspect(request)
            val bounds = before.nodes.first().bounds
            fun colors(image: WinUIXamlVisualImage): Pair<Int, Int> {
                var cyan = 0; var yellow = 0
                for (offset in image.pixels.indices step 4) {
                    val b=image.pixels[offset].toInt() and 255; val g=image.pixels[offset+1].toInt() and 255; val r=image.pixels[offset+2].toInt() and 255
                    if (b > 220 && g > 220 && r < 30) cyan++
                    if (r > 220 && g > 220 && b < 30) yellow++
                }
                return cyan to yellow
            }
            repeat(3) { index ->
                val snapshot=inspect(request.copy(renderScale=if(index==1) 2.0 else 1.0))
                val image=requireNotNull(snapshot.image)
                assertEquals("Composition",image.captureBackend)
                assertEquals("Capture must not resize or reflow the running application",bounds,snapshot.nodes.first().bounds)
                val (cyan,yellow)=colors(image)
                assertTrue("Main SwapChainPanel must contain real cyan DirectX pixels",cyan>1000)
                assertTrue("Popup SwapChainPanel must contain real yellow DirectX pixels",yellow>1000)
            }
            val after=inspect(request.copy(capture=false))
            assertEquals(before.nodes.filter { it.name.isNotEmpty() }.map { it.name to it.bounds },after.nodes.filter { it.name.isNotEmpty() }.map { it.name to it.bounds })
            println("Live Native capture: real main/popup swapchains, 1x/2x camera and unchanged application layout verified.")
        }
    }

    @Test fun composition_capture_preserves_pixels_scale_and_viewport() {
        val uwpSession = System.getProperty("winrt.ide.uwpCompositionSession")?.let(Path::of)
        val configured = System.getProperty("winrt.ide.nativeRestartProject")
        assumeTrue(configured != null || uwpSession != null)
        val project = configured?.let { Path.of(it).toAbsolutePath().normalize() }
        val executable = project?.resolve("app/build/kotlin-winrt/application-layout/mingwX64_main_debugExecutable/package/native-project-preview.exe")
        if (uwpSession == null) require(Files.isRegularFile(requireNotNull(executable)))
        val directory = uwpSession ?: Files.createTempDirectory(requireNotNull(project).resolve("app/build"), "composition-acceptance-")
        val process = if (uwpSession != null) null else ProcessBuilder(requireNotNull(executable).toString(), "--kotlin-winrt-xaml-preview").directory(executable.parent.toFile()).apply {
            environment()["KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"] = directory.toString()
            environment()["KOTLIN_WINRT_XAML_PREVIEW"] = "1"
            environment()["KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"] = executable.parent.toString()
            redirectErrorStream(true); redirectOutput(directory.resolve("host.log").toFile())
        }.start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
            var client: WinUIXamlHotReloadClient? = null
            while (client == null) {
                check(process?.isAlive != false && System.nanoTime() < deadline) { "The Native preview did not publish its endpoint." }
                client = WinUIXamlHotReloadClient.discover(directory, if (uwpSession == null) executable.toString() else null).singleOrNull()
                if (client == null) Thread.sleep(50)
            }
            client.use { connection ->
                while (connection.request().roots.none { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }) {
                    check(System.nanoTime() < deadline); Thread.sleep(50)
                }
                val markup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="Red">
                  <SwapChainPanel Width="20" Height="20" HorizontalAlignment="Left" VerticalAlignment="Top"/>
                  <Border Width="100" Height="100" Background="Blue" HorizontalAlignment="Left" VerticalAlignment="Top" Margin="200,100,0,0"/>
                </Grid>"""
                val request = WinUIXamlInspectionRequest(WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", width = 400, height = 300, readProperties = false)
                fun inspect(value: WinUIXamlInspectionRequest): WinUIXamlVisualSnapshot {
                    val reply = connection.inspect(value)
                    assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                    return requireNotNull(reply.inspection)
                }
                val tree = inspect(request.copy(previewMarkup = markup))
                assertTrue(tree.nodes.any { it.typeName.endsWith(".SwapChainPanel") })
                fun frame(scale: Double = 1.0, bounds: WinUIXamlVisualBounds? = null): WinUIXamlVisualImage {
                    val image = requireNotNull(inspect(request.copy(readTree = false, renderScale = scale, captureBounds = bounds)).image)
                    assertEquals("Composition", image.captureBackend)
                    return image
                }
                fun save(image: WinUIXamlVisualImage, name: String) {
                    val bitmap = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until image.height) for (x in 0 until image.width) {
                        val offset = 4 * (y * image.width + x)
                        val bgra = image.pixels
                        bitmap.setRGB(x, y, ((bgra[offset + 3].toInt() and 255) shl 24) or
                            ((bgra[offset + 2].toInt() and 255) shl 16) or ((bgra[offset + 1].toInt() and 255) shl 8) or (bgra[offset].toInt() and 255))
                    }
                    javax.imageio.ImageIO.write(bitmap, "png", directory.resolve(name + ".png").toFile())
                }
                fun color(image: WinUIXamlVisualImage, x: Int, y: Int, red: Int, blue: Int) {
                    val offset = 4 * (y * image.width + x)
                    assertEquals("red at $x,$y", red, image.pixels[offset + 2].toInt() and 255)
                    assertEquals("blue at $x,$y", blue, image.pixels[offset].toInt() and 255)
                }
                save(requireNotNull(tree.image), "initial-xaml")
                repeat(3) {
                    val full = frame(); save(full, "full")
                    assertEquals(400, full.width); assertEquals(300, full.height)
                    color(full, 0, 0, 255, 0); color(full, 399, 299, 255, 0); color(full, 80, 60, 255, 0); color(full, 250, 150, 0, 255)
                    val zoomed = frame(2.0); save(zoomed, "zoomed")
                    assertEquals(800, zoomed.width); assertEquals(600, zoomed.height)
                    color(zoomed, 160, 120, 255, 0); color(zoomed, 500, 300, 0, 255)
                    val region = WinUIXamlVisualBounds(200.0, 100.0, 100.0, 100.0)
                    val cropped = frame(2.0, region); save(cropped, "cropped")
                    assertEquals(region, cropped.bounds); assertEquals(200, cropped.width)
                    color(cropped, 100, 100, 0, 255)
                    val restored = frame()
                    color(restored, 80, 60, 255, 0); color(restored, 250, 150, 0, 255)
                }
                // RenderTargetBitmap owns its asynchronous draw completion; the
                // ordinary XAML path must remain accurate without WGC's extra commit.
                val ordinary = markup.replace(Regex("<SwapChainPanel[^>]*/>"), "")
                val ordinaryFirst = requireNotNull(inspect(request.copy(previewMarkup = ordinary)).image)
                assertEquals("Xaml", ordinaryFirst.captureBackend)
                color(ordinaryFirst, 80, 60, 255, 0)
                color(ordinaryFirst, 250, 150, 0, 255)
                for (scale in listOf(1.0, 2.0, 1.0)) {
                    val image = requireNotNull(inspect(request.copy(readTree = false, renderScale = scale)).image)
                    assertEquals("Xaml", image.captureBackend)
                    color(image, (80 * scale).toInt(), (60 * scale).toInt(), 255, 0)
                    color(image, (250 * scale).toInt(), (150 * scale).toInt(), 0, 255)
                }
                val video = uwpSession?.parent?.resolve("composition-green.mp4")
                    ?: requireNotNull(project).parent.resolve("xaml-reload-validation/composition-green.mp4")
                require(Files.isRegularFile(video)) { "Generate a 160x120 H.264 fixture alternating lime/blue every 5 seconds at $video before acceptance." }
                val videoSource = if (uwpSession != null) "ms-appx:///composition-green.mp4" else video.toUri().toString()
                inspect(request.copy(previewMarkup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" Background="Red"><MediaPlayerElement Width="160" Height="120" HorizontalAlignment="Left" VerticalAlignment="Top" Source="${videoSource}" AutoPlay="True"/></Grid>"""))
                var videoFrame: WinUIXamlVisualImage? = null
                val videoDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (videoFrame == null && System.nanoTime() < videoDeadline) {
                    val candidate = frame()
                    val offset = 4 * (60 * candidate.width + 80)
                    if ((candidate.pixels[offset + 1].toInt() and 255) > 200 && (candidate.pixels[offset + 2].toInt() and 255) < 30) videoFrame = candidate
                    else Thread.sleep(100)
                }
                assertNotNull("Decoded video pixels must be present, not a black or transparent placeholder.", videoFrame)
                save(requireNotNull(videoFrame), "video")
                var advanced = false
                val playbackDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
                while (!advanced && System.nanoTime() < playbackDeadline) {
                    val candidate = frame()
                    val offset = 4 * (60 * candidate.width + 80)
                    advanced = (candidate.pixels[offset].toInt() and 255) > 200 && (candidate.pixels[offset + 1].toInt() and 255) < 30
                    if (advanced) save(candidate, "video-advanced") else Thread.sleep(150)
                }
                assertTrue("Video capture must advance to a later decoded frame.", advanced)
                // The video is only in a separate popup; this must independently
                // trigger composition and retain real decoded pixels at every camera.
                val popupAttribute = if (uwpSession == null) "ShouldConstrainToRootBounds=\"False\"" else ""
                inspect(request.copy(previewMarkup = """<Grid xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" Background="Red">
                  <Popup xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" $popupAttribute HorizontalOffset="80" VerticalOffset="60" IsOpen="False">
                    <Popup.Resources><x:String x:Key="${WinUIXamlHotReloadProtocol.PREVIEW_POPUP_OPEN_KEY}">True</x:String></Popup.Resources>
                    <MediaPlayerElement Width="160" Height="120" Source="$videoSource" AutoPlay="True"/>
                  </Popup></Grid>"""))
                var popupReady = false
                val popupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!popupReady && System.nanoTime() < popupDeadline) {
                    val candidate = frame()
                    val offset = 4 * (120 * candidate.width + 160)
                    popupReady = (candidate.pixels[offset + 1].toInt() and 255) > 200 || (candidate.pixels[offset].toInt() and 255) > 200
                    if (popupReady) save(candidate, "popup-video") else Thread.sleep(100)
                }
                assertTrue("Popup must contain decoded video pixels.", popupReady)
                for ((scale, bounds) in listOf(2.0 to null, 2.0 to WinUIXamlVisualBounds(80.0, 60.0, 160.0, 120.0), 1.0 to null)) {
                    val candidate = frame(scale, bounds)
                    val x = if (bounds == null) (160 * scale).toInt() else candidate.width / 2
                    val y = if (bounds == null) (120 * scale).toInt() else candidate.height / 2
                    val offset = 4 * (y * candidate.width + x)
                    assertTrue("Popup video survives scale/crop", (candidate.pixels[offset + 1].toInt() and 255) > 200 || (candidate.pixels[offset].toInt() and 255) > 200)
                    save(candidate, "popup-video-${scale}-${bounds != null}")
                }
                println("Native video capture: decoded H.264 green and later blue frames verified.")
                println("Native composition capture: physical pixels, 2x scale, crop and camera restoration verified.")
            }
        } finally {
            process?.let {
                it.destroy()
                if (!it.waitFor(10, TimeUnit.SECONDS)) it.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
            }
        }
    }
}
