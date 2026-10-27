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
