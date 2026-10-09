package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.runtime.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Credentials remain private and ephemeral; only protocol DTOs enter the Compose display state. */
internal class WinRTHotReloadClient private constructor(
    val sessionFile: Path, val process: ProcessHandle, val started: Instant,
    private val port: Int, private val token: String,
) : AutoCloseable {
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var closed = false
    val alive get() = !closed && process.isAlive && process.info().startInstant().orElse(null) == started

    fun request(patch: WinRTXamlHotReloadPatch? = null): WinRTXamlHotReloadReply {
        return exchange { socket -> WinRTXamlHotReloadWire.writeRequest(socket.getOutputStream(), token, patch) }
    }
    fun inspect(request: WinRTXamlInspectionRequest): WinRTXamlHotReloadReply = exchange { socket ->
        WinRTXamlHotReloadWire.writeInspectionRequest(socket.getOutputStream(), token, request)
    }
    private fun exchange(send: (Socket) -> Unit): WinRTXamlHotReloadReply {
        check(alive) { "The application has exited. Start a new development session." }
        return Socket().use { socket ->
            sockets += socket
            try {
                check(!closed) { "The development connection is closed." }
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                socket.soTimeout = 12_000
                send(socket)
                WinRTXamlHotReloadWire.readReply(socket.getInputStream())
            } finally { sockets -= socket }
        }
    }

    override fun close() { closed = true; sockets.forEach { runCatching { it.close() } } }

    companion object {
        fun discover(directory: Path, executable: String? = null): List<WinRTHotReloadClient> {
            if (!Files.isDirectory(directory)) return emptyList()
            return Files.newDirectoryStream(directory, "*.session").use { files -> files.take(32).mapNotNull { file ->
                runCatching { read(file, executable) }.getOrNull()
            } }
        }

        fun read(file: Path, executable: String? = null): WinRTHotReloadClient {
            require(!Files.isSymbolicLink(file) && Files.size(file) <= 16 * 1024) { "Invalid development session file." }
            val properties = Properties().apply { Files.newInputStream(file).use(::load) }
            require(properties.getProperty("protocol") == WinRTXamlHotReloadProtocol.VERSION.toString()) { "Unsupported development protocol." }
            val port = properties.getProperty("port").toInt().also { require(it in 1..65535) }
            val token = properties.getProperty("token").also { require(it.matches(Regex("[A-Za-z0-9_-]{43}"))) }
            val pid = properties.getProperty("pid").toLong()
            val process = ProcessHandle.of(pid).orElseThrow { IllegalStateException("The application has exited.") }
            require(process.isAlive) { "The application has exited." }
            if (executable != null) require(process.info().command().orElse("").replace('\\', '/').equals(executable.replace('\\', '/'), true)) {
                "The development process does not match this application host."
            }
            val started = process.info().startInstant().orElseThrow { IllegalStateException("Cannot verify the development process lifetime.") }
            return WinRTHotReloadClient(file, process, started, port, token)
        }
    }
}
