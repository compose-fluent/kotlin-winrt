package io.github.composefluent.winrt.runtime

import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.Properties
import kotlin.test.*

class WinRTXamlHotReloadServerTest {
    @Test fun development_session_authenticates_loopback_requests_and_removes_its_file_on_close() {
        val root = Files.createTempDirectory("winrt-hot-reload-")
        try {
            val registry = WinRTXamlHotReloadRegistry({ { it(); true } }, { _, text -> text })
            val server = WinRTXamlHotReloadServer(registry, root)
            val session = Files.list(root).use { it.filter { file -> file.toString().endsWith(".session") }.findFirst().orElseThrow() }
            val info = Properties().apply { Files.newInputStream(session).use(::load) }
            fun request(token: String): WinRTXamlHotReloadReply = Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), info.getProperty("port").toInt()), 1000); socket.soTimeout = 1000
                WinRTXamlHotReloadWire.writeRequest(socket.getOutputStream(), token, null)
                WinRTXamlHotReloadWire.readReply(socket.getInputStream())
            }
            try {
                assertEquals(WinRTXamlHotReloadProtocol.REJECTED, request("wrong").status)
                assertEquals(WinRTXamlHotReloadProtocol.APPLIED, request(info.getProperty("token")).status)
            } finally { server.close() }
            assertFalse(Files.exists(session))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun wire_rejects_unbounded_and_truncated_frames() {
        val bad = ByteArrayOutputStream().also { DataOutputStream(it).apply {
            writeInt(WinRTXamlHotReloadProtocol.MAGIC); writeInt(WinRTXamlHotReloadProtocol.VERSION); writeInt(Int.MAX_VALUE)
        } }.toByteArray()
        assertFailsWith<IllegalArgumentException> { WinRTXamlHotReloadWire.readRequest(ByteArrayInputStream(bad)) }
        val valid = ByteArrayOutputStream().also { WinRTXamlHotReloadWire.writeRequest(it, "test", null) }.toByteArray()
        assertFailsWith<EOFException> { WinRTXamlHotReloadWire.readRequest(ByteArrayInputStream(valid.copyOf(valid.size - 1))) }
    }
    @Test fun resource_transactions_and_object_paths_round_trip_in_bounded_frames() {
        val target = WinRTXamlHotReloadTarget("Container", listOf(WinRTXamlHotReloadStep.Property("Resources"),
            WinRTXamlHotReloadStep.Key("Light"), WinRTXamlHotReloadStep.Index(0, 1)))
        val patch = WinRTXamlHotReloadPatch("probe.Page", "Page.xaml", "a".repeat(64), "b".repeat(64), 1,
            listOf(WinRTXamlHotReloadChange("Container", "Color", "blue", target.path)),
            listOf(WinRTXamlHotReloadResources(target, "<ResourceDictionary/>", listOf("Style"),
                listOf(WinRTXamlHotReloadResourceReference(WinRTXamlHotReloadTarget("Heading"), "Style", "Style")))),
            listOf(WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Heading"), "FontSize")))
        val bytes = ByteArrayOutputStream().also { WinRTXamlHotReloadWire.writeRequest(it, "token", patch) }.toByteArray()
        assertEquals("token" to patch, WinRTXamlHotReloadWire.readRequest(ByteArrayInputStream(bytes)))
        val reply = WinRTXamlHotReloadReply(0, "updated", values = listOf(WinRTXamlHotReloadValue(target.element, "Color", "blue", target.path)))
        val response = ByteArrayOutputStream().also { WinRTXamlHotReloadWire.writeReply(it, reply) }.toByteArray()
        assertEquals(reply, WinRTXamlHotReloadWire.readReply(ByteArrayInputStream(response)))
        assertFailsWith<IllegalArgumentException> {
            WinRTXamlHotReloadWire.writeRequest(ByteArrayOutputStream(), "token", patch.copy(changes =
                listOf(patch.changes.single().copy(path = List(65) { WinRTXamlHotReloadStep.Property("Next") }))))
        }
    }
}
