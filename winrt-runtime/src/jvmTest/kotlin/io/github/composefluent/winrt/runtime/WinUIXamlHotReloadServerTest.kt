package io.github.composefluent.winrt.runtime

import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.Properties
import kotlin.test.*

class WinUIXamlHotReloadServerTest {
    @Test fun visual_inspection_paths_and_bgra_frames_round_trip_with_limits() {
        val request = WinUIXamlInspectionRequest("probe.Page", "Page.xaml", selectedPath = listOf(1, 0))
        val encoded = ByteArrayOutputStream().also { WinUIXamlHotReloadWire.writeInspectionRequest(it, "token", request) }.toByteArray()
        assertEquals(request, WinUIXamlHotReloadWire.readCommand(ByteArrayInputStream(encoded)).inspection)
        val image = WinUIXamlVisualImage(2, 1, byteArrayOf(0, 0, -1, -1, 0, -1, 0, -1))
        val view = WinUIXamlVisualSnapshot(listOf(WinUIXamlVisualNode(listOf(1, 0), "TextBlock", "Heading",
            WinUIXamlVisualBounds(1.0, 2.0, 30.0, 40.0))), listOf(WinUIXamlVisualProperty("Text", "hello")), image)
        val response = ByteArrayOutputStream().also { WinUIXamlHotReloadWire.writeReply(it, WinUIXamlHotReloadReply(0, "", inspection = view)) }.toByteArray()
        val decoded = WinUIXamlHotReloadWire.readReply(ByteArrayInputStream(response)).inspection!!
        assertEquals(view.nodes, decoded.nodes); assertEquals(view.properties, decoded.properties); assertContentEquals(image.pixels, decoded.image!!.pixels)
        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadWire.writeReply(ByteArrayOutputStream(), WinUIXamlHotReloadReply(0, "", inspection = view.copy(image = image.copy(width = 769)))) }
    }
    @Test fun development_session_authenticates_loopback_requests_and_removes_its_file_on_close() {
        val root = Files.createTempDirectory("winrt-hot-reload-")
        try {
            val registry = WinUIXamlHotReloadRegistry({ { it(); true } }, { _, text -> text })
            val server = WinUIXamlHotReloadServer(registry, root)
            val session = Files.list(root).use { it.filter { file -> file.toString().endsWith(".session") }.findFirst().orElseThrow() }
            val info = Properties().apply { Files.newInputStream(session).use(::load) }
            fun request(token: String): WinUIXamlHotReloadReply = Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), info.getProperty("port").toInt()), 1000); socket.soTimeout = 1000
                WinUIXamlHotReloadWire.writeRequest(socket.getOutputStream(), token, null)
                WinUIXamlHotReloadWire.readReply(socket.getInputStream())
            }
            try {
                assertEquals(WinUIXamlHotReloadProtocol.REJECTED, request("wrong").status)
                assertEquals(WinUIXamlHotReloadProtocol.APPLIED, request(info.getProperty("token")).status)
            } finally { server.close() }
            assertFalse(Files.exists(session))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun wire_rejects_unbounded_and_truncated_frames() {
        val bad = ByteArrayOutputStream().also { DataOutputStream(it).apply {
            writeInt(WinUIXamlHotReloadProtocol.MAGIC); writeInt(WinUIXamlHotReloadProtocol.VERSION); writeInt(Int.MAX_VALUE)
        } }.toByteArray()
        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadWire.readRequest(ByteArrayInputStream(bad)) }
        val valid = ByteArrayOutputStream().also { WinUIXamlHotReloadWire.writeRequest(it, "test", null) }.toByteArray()
        assertFailsWith<EOFException> { WinUIXamlHotReloadWire.readRequest(ByteArrayInputStream(valid.copyOf(valid.size - 1))) }
    }
    @Test fun resource_transactions_and_object_paths_round_trip_in_bounded_frames() {
        val target = WinUIXamlHotReloadTarget("Container", listOf(WinUIXamlHotReloadStep.Property("Resources"),
            WinUIXamlHotReloadStep.Key("Light"), WinUIXamlHotReloadStep.Index(0, 1)))
        val patch = WinUIXamlHotReloadPatch("probe.Page", "Page.xaml", "a".repeat(64), "b".repeat(64), 1,
            listOf(WinUIXamlHotReloadChange("Container", "Color", "blue", target.path)),
            listOf(WinUIXamlHotReloadResources(target, "<ResourceDictionary/>", listOf("Style"),
                listOf(WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("Heading"), "Style", "Style")))),
            listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Heading"), "FontSize")),
            listOf(WinUIXamlHotReloadChildren(WinUIXamlHotReloadTarget("Container", listOf(WinUIXamlHotReloadStep.Property("Children"))), 1,
                listOf(WinUIXamlHotReloadItem.Markup("<TextBlock Text=\"new\"/>"), WinUIXamlHotReloadItem.Existing(0)))))
        val bytes = ByteArrayOutputStream().also { WinUIXamlHotReloadWire.writeRequest(it, "token", patch) }.toByteArray()
        assertEquals("token" to patch, WinUIXamlHotReloadWire.readRequest(ByteArrayInputStream(bytes)))
        val reply = WinUIXamlHotReloadReply(0, "updated", values = listOf(WinUIXamlHotReloadValue(target.element, "Color", "blue", target.path)))
        val response = ByteArrayOutputStream().also { WinUIXamlHotReloadWire.writeReply(it, reply) }.toByteArray()
        assertEquals(reply, WinUIXamlHotReloadWire.readReply(ByteArrayInputStream(response)))
        assertFailsWith<IllegalArgumentException> {
            WinUIXamlHotReloadWire.writeRequest(ByteArrayOutputStream(), "token", patch.copy(changes =
                listOf(patch.changes.single().copy(path = List(65) { WinUIXamlHotReloadStep.Property("Next") }))))
        }
    }
}
