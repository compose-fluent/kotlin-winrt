package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WinRTHotReloadTest {
    private val source = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><StackPanel><TextBlock x:Name="Greeting" Text="Hello" Width="100"/></StackPanel></Window>"""
    private fun root(text: String = source) = WinRTXamlHotReloadRoot("probe.Window", "Window.xaml", WinRTHotReloadMarkup.parse(text).hash, 0, listOf("Greeting"))

    @Test fun literal_changes_decode_xml_and_keep_compiler_fingerprint_and_version() {
        val before = WinRTHotReloadMarkup.parse(source)
        val after = WinRTHotReloadMarkup.parse(source.replace("Hello", "A &amp; B").replace("100", "140"))
        val patch = after.patch(before, root())
        assertEquals(before.hash, patch.expectedHash)
        assertEquals(after.hash, patch.sourceHash)
        assertEquals(1L, patch.version)
        assertEquals(listOf(WinRTXamlHotReloadChange("Greeting", "Text", "A & B"), WinRTXamlHotReloadChange("Greeting", "Width", "140")), patch.changes.sortedBy { it.property })
        val formatted = WinRTHotReloadMarkup.parse(source.replace("<StackPanel>", "\n  <StackPanel>"))
        assertTrue(formatted.patch(before, root()).changes.isEmpty())
        val escaped = WinRTHotReloadMarkup.parse(source.replace("Hello", "{}{literal}"))
        assertEquals("{literal}", escaped.patch(before, root()).changes.single().literal)
    }

    @Test fun lifetime_and_connection_changes_require_rebuilding() {
        val before = WinRTHotReloadMarkup.parse(source)
        val unsupported = listOf(
            source.replace("TextBlock", "TextBox"), source.replace("Greeting", "Renamed"),
            source.replace("Hello", "{Binding Title}"), source.replace(" Width=\"100\"", ""),
            source.replace("x:Class=\"probe.Window\"", "x:Class=\"probe.Other\""),
            source.replace("<StackPanel>", "<StackPanel><TextBlock/>"), source.replace("Text=\"Hello\"", "Grid.Row=\"2\" Text=\"Hello\""),
        )
        unsupported.forEach { text -> assertTrue(text, runCatching { WinRTHotReloadMarkup.parse(text).patch(before, root()) }.isFailure) }
        assertTrue(runCatching { WinRTHotReloadMarkup.parse(source.replace("Hello", "changed")).patch(before, root().copy(sourceHash = "0".repeat(64))) }.isFailure)
        val template = source.replace("<StackPanel>", "<StackPanel><DataTemplate><TextBlock x:Name=\"Greeting\" Text=\"template\"/></DataTemplate>")
        assertTrue(runCatching { WinRTHotReloadMarkup.parse(template.replace("template", "changed"))
            .patch(WinRTHotReloadMarkup.parse(template), root(template)) }.isFailure)
        assertTrue(runCatching { WinRTHotReloadMarkup.parse("<!DOCTYPE Window [<!ENTITY x SYSTEM 'file:///secret'>]>$source") }.isFailure)
    }

    @Test fun loopback_client_authenticates_and_does_not_expose_credentials() {
        val folder = Files.createTempDirectory("winrt-hot-client-")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        val token = "t".repeat(43)
        val file = folder.resolve("probe.session")
        try {
            Files.newOutputStream(file).use { Properties().apply {
                setProperty("protocol", "1"); setProperty("pid", ProcessHandle.current().pid().toString())
                setProperty("port", server.localPort.toString()); setProperty("token", token)
            }.store(it, "probe") }
            val response = executor.submit {
                server.accept().use { socket ->
                    val (supplied, patch) = WinRTXamlHotReloadWire.readRequest(socket.getInputStream())
                    assertEquals(token, supplied); assertNull(patch)
                    WinRTXamlHotReloadWire.writeReply(socket.getOutputStream(), WinRTXamlHotReloadReply(0, "connected", listOf(root())))
                }
            }
            WinRTHotReloadClient.read(file).use { client ->
                assertFalse(client.toString().contains(token))
                assertEquals("connected", client.request().message)
                assertEquals(ProcessHandle.current().pid(), client.process.pid())
            }
            response.get(5, TimeUnit.SECONDS)
            assertTrue(runCatching { WinRTHotReloadClient.read(file, "C:/not-this-process.exe") }.isFailure)
        } finally { server.close(); executor.shutdownNow(); Files.deleteIfExists(file); Files.deleteIfExists(folder) }
    }

    /** A real compiler-generated WinUI host supplies the session; no test-only XAML runtime glue. */
    @Test fun real_winui_host_updates_text_dimensions_and_brush_on_its_ui_thread() {
        val directory = System.getProperty("winrt.ide.hotReloadSession")
        assumeTrue(directory != null)
        val clients = WinRTHotReloadClient.discover(Path.of(directory))
        assertEquals(1, clients.size)
        clients.single().use { client ->
            val initial = client.request()
            assertEquals(initial.message, WinRTXamlHotReloadProtocol.APPLIED, initial.status)
            val loaded = initial.roots.single { it.className.endsWith(".MainWindow") && "Greeting" in it.elements }
            val change = WinRTXamlHotReloadPatch(loaded.className, loaded.resourcePath, loaded.sourceHash, "b".repeat(64), loaded.version + 1,
                listOf(WinRTXamlHotReloadChange("Greeting", "Text", "Reloaded in place"),
                    WinRTXamlHotReloadChange("Greeting", "Width", "240"),
                    WinRTXamlHotReloadChange("Greeting", "FontSize", "24"),
                    WinRTXamlHotReloadChange("Greeting", "Foreground", "#FF0067C0")))
            val reply = client.request(change)
            assertEquals(reply.message, WinRTXamlHotReloadProtocol.APPLIED, reply.status)
            assertEquals("Reloaded in place", reply.values.single { it.property == "Text" }.value)
            assertEquals(240.0, reply.values.single { it.property == "Width" }.value.toDouble(), 0.0)
            assertEquals(24.0, reply.values.single { it.property == "FontSize" }.value.toDouble(), 0.0)
            assertEquals(4, reply.values.size)
            assertTrue("SDK color conversion must retain a native brush", reply.values.single { it.property == "Foreground" }.value.isNotBlank())
            assertTrue(client.alive)
            val updated = client.request().roots.single { it.className == loaded.className }
            assertEquals(change.version, updated.version); assertEquals(change.sourceHash, updated.sourceHash)
            val invalid = change.copy(expectedHash = updated.sourceHash, sourceHash = "c".repeat(64), version = updated.version + 1,
                changes = listOf(WinRTXamlHotReloadChange("Greeting", "Text", "must roll back"), WinRTXamlHotReloadChange("Greeting", "Width", "-1")))
            val rejected = client.request(invalid)
            assertEquals(rejected.message, WinRTXamlHotReloadProtocol.REJECTED, rejected.status)
            assertEquals(updated.sourceHash, client.request().roots.single { it.className == loaded.className }.sourceHash)
        }
    }
}
