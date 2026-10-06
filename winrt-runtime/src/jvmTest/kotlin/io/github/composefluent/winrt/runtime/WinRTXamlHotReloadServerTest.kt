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
}
