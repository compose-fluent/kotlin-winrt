package io.github.composefluent.winrt.runtime

import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.Properties
import kotlin.test.*

class WinUiXamlHotReloadServerTest {
    @Test fun visual_inspection_paths_and_bgra_frames_round_trip_with_limits() {
        val request = WinUiXamlInspectionRequest("probe.Page", "Page.xaml", selectedPath = listOf(1, 0))
        val encoded = ByteArrayOutputStream().also { WinUiXamlHotReloadWire.writeInspectionRequest(it, "token", request) }.toByteArray()
        assertEquals(request, WinUiXamlHotReloadWire.readCommand(ByteArrayInputStream(encoded)).inspection)
        val image = WinUiXamlVisualImage(2, 1, byteArrayOf(0, 0, -1, -1, 0, -1, 0, -1))
        val view = WinUiXamlVisualSnapshot(listOf(WinUiXamlVisualNode(listOf(1, 0), "TextBlock", "Heading",
            WinUiXamlVisualBounds(1.0, 2.0, 30.0, 40.0))), listOf(WinUiXamlVisualProperty("Text", "hello")), image)
        val response = ByteArrayOutputStream().also { WinUiXamlHotReloadWire.writeReply(it, WinUiXamlHotReloadReply(0, "", inspection = view)) }.toByteArray()
        val decoded = WinUiXamlHotReloadWire.readReply(ByteArrayInputStream(response)).inspection!!
        assertEquals(view.nodes, decoded.nodes); assertEquals(view.properties, decoded.properties); assertContentEquals(image.pixels, decoded.image!!.pixels)
        assertFailsWith<IllegalArgumentException> { WinUiXamlHotReloadWire.writeReply(ByteArrayOutputStream(), WinUiXamlHotReloadReply(0, "", inspection = view.copy(image = image.copy(width = 769)))) }
    }
    @Test fun development_session_authenticates_loopback_requests_and_removes_its_file_on_close() {
        val root = Files.createTempDirectory("winrt-hot-reload-")
        try {
            val registry = WinUiXamlHotReloadRegistry({ { it(); true } }, { _, text -> text })
            val server = WinUiXamlHotReloadServer(registry, root)
            val session = Files.list(root).use { it.filter { file -> file.toString().endsWith(".session") }.findFirst().orElseThrow() }
            val info = Properties().apply { Files.newInputStream(session).use(::load) }
            fun request(token: String): WinUiXamlHotReloadReply = Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), info.getProperty("port").toInt()), 1000); socket.soTimeout = 1000
                WinUiXamlHotReloadWire.writeRequest(socket.getOutputStream(), token, null)
                WinUiXamlHotReloadWire.readReply(socket.getInputStream())
            }
            try {
                assertEquals(WinUiXamlHotReloadProtocol.REJECTED, request("wrong").status)
                assertEquals(WinUiXamlHotReloadProtocol.APPLIED, request(info.getProperty("token")).status)
            } finally { server.close() }
            assertFalse(Files.exists(session))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun wire_rejects_unbounded_and_truncated_frames() {
        val bad = ByteArrayOutputStream().also { DataOutputStream(it).apply {
            writeInt(WinUiXamlHotReloadProtocol.MAGIC); writeInt(WinUiXamlHotReloadProtocol.VERSION); writeInt(Int.MAX_VALUE)
        } }.toByteArray()
        assertFailsWith<IllegalArgumentException> { WinUiXamlHotReloadWire.readRequest(ByteArrayInputStream(bad)) }
        val valid = ByteArrayOutputStream().also { WinUiXamlHotReloadWire.writeRequest(it, "test", null) }.toByteArray()
        assertFailsWith<EOFException> { WinUiXamlHotReloadWire.readRequest(ByteArrayInputStream(valid.copyOf(valid.size - 1))) }
    }
    @Test fun resource_transactions_and_object_paths_round_trip_in_bounded_frames() {
        val target = WinUiXamlHotReloadTarget("Container", listOf(WinUiXamlHotReloadStep.Property("Resources"),
            WinUiXamlHotReloadStep.Key("Light"), WinUiXamlHotReloadStep.Index(0, 1)))
        val patch = WinUiXamlHotReloadPatch("probe.Page", "Page.xaml", "a".repeat(64), "b".repeat(64), 1,
            listOf(WinUiXamlHotReloadChange("Container", "Color", "blue", target.path)),
            listOf(WinUiXamlHotReloadResources(target, "<ResourceDictionary/>", listOf("Style"),
                listOf(WinUiXamlHotReloadResourceReference(WinUiXamlHotReloadTarget("Heading"), "Style", "Style")))),
            listOf(WinUiXamlHotReloadRead(WinUiXamlHotReloadTarget("Heading"), "FontSize")),
            listOf(WinUiXamlHotReloadChildren(WinUiXamlHotReloadTarget("Container", listOf(WinUiXamlHotReloadStep.Property("Children"))), 1,
                listOf(WinUiXamlHotReloadItem.Markup("<TextBlock Text=\"new\"/>"), WinUiXamlHotReloadItem.Existing(0)))))
        val bytes = ByteArrayOutputStream().also { WinUiXamlHotReloadWire.writeRequest(it, "token", patch) }.toByteArray()
        assertEquals("token" to patch, WinUiXamlHotReloadWire.readRequest(ByteArrayInputStream(bytes)))
        val reply = WinUiXamlHotReloadReply(0, "updated", values = listOf(WinUiXamlHotReloadValue(target.element, "Color", "blue", target.path)))
        val response = ByteArrayOutputStream().also { WinUiXamlHotReloadWire.writeReply(it, reply) }.toByteArray()
        assertEquals(reply, WinUiXamlHotReloadWire.readReply(ByteArrayInputStream(response)))
        assertFailsWith<IllegalArgumentException> {
            WinUiXamlHotReloadWire.writeRequest(ByteArrayOutputStream(), "token", patch.copy(changes =
                listOf(patch.changes.single().copy(path = List(65) { WinUiXamlHotReloadStep.Property("Next") }))))
        }
    }
}
