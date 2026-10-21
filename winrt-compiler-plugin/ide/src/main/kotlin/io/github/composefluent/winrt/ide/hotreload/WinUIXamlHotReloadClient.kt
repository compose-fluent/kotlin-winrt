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
import java.util.concurrent.TimeUnit
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import com.intellij.util.concurrency.AppExecutorUtil

/** Credentials remain private and ephemeral; only protocol DTOs enter the Compose display state. */
internal class WinUIXamlHotReloadClient private constructor(
    val sessionFile: Path, val process: ProcessHandle, val started: Instant,
    private val port: Int, private val token: String, private val pipeName: String? = null,
) : AutoCloseable {
    private val connections = ConcurrentHashMap.newKeySet<AutoCloseable>()
    @Volatile private var closed = false
    val alive get() = !closed && process.isAlive && process.info().startInstant().orElse(null) == started

    fun request(patch: WinUIXamlHotReloadPatch? = null): WinUIXamlHotReloadReply {
        return exchange { output -> WinUIXamlHotReloadWire.writeRequest(output, token, patch) }
    }
    fun inspect(request: WinUIXamlInspectionRequest): WinUIXamlHotReloadReply = exchange { output ->
        WinUIXamlHotReloadWire.writeInspectionRequest(output, token, request)
    }
    private fun exchange(send: (OutputStream) -> Unit): WinUIXamlHotReloadReply {
        check(alive) { "The application has exited. Start a new development session." }
        if (pipeName != null) return exchangePipe(pipeName, send)
        return Socket().use { socket ->
            connections += socket
            try {
                check(!closed) { "The development connection is closed." }
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                socket.soTimeout = 12_000
                send(socket.getOutputStream())
                WinUIXamlHotReloadWire.readReply(socket.getInputStream())
            } finally { connections -= socket }
        }
    }

    private fun exchangePipe(name: String, send: (OutputStream) -> Unit): WinUIXamlHotReloadReply =
        RandomAccessFile("\\\\.\\pipe\\LOCAL\\$name", "rw").use { pipe ->
            connections += pipe
            val timeout = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                { runCatching { pipe.close() } }, 12, TimeUnit.SECONDS)
            try {
                check(!closed) { "The development connection is closed." }
                val input = object : InputStream() {
                    override fun read(): Int = pipe.read()
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int = pipe.read(bytes, offset, length)
                }
                val output = object : OutputStream() {
                    override fun write(value: Int) = pipe.write(value)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = pipe.write(bytes, offset, length)
                }
                send(output)
                WinUIXamlHotReloadWire.readReply(input).also { output.write(0x6b) }
            } finally {
                timeout.cancel(false)
                connections -= pipe
            }
        }

    override fun close() { closed = true; connections.forEach { runCatching { it.close() } } }

    companion object {
        fun discover(directory: Path, executable: String? = null): List<WinUIXamlHotReloadClient> {
            if (!Files.isDirectory(directory)) return emptyList()
            return Files.newDirectoryStream(directory, "*.session").use { files -> files.take(32).mapNotNull { file ->
                runCatching { read(file, executable) }.getOrNull()
            } }
        }

        fun read(file: Path, executable: String? = null): WinUIXamlHotReloadClient {
            require(!Files.isSymbolicLink(file) && Files.size(file) <= 16 * 1024) { "Invalid development session file." }
            val properties = Properties().apply { Files.newInputStream(file).use(::load) }
            require(properties.getProperty("protocol") == WinUIXamlHotReloadProtocol.VERSION.toString()) { "Unsupported development protocol." }
            val port = properties.getProperty("port").toInt()
            val token = properties.getProperty("token").also { require(it.matches(Regex("[A-Za-z0-9_-]{43}"))) }
            val pid = properties.getProperty("pid").toLong()
            val pipe = properties.getProperty("pipe")?.takeIf(String::isNotBlank)
            require(if (pipe == null) port in 1..65535 else port == 0 &&
                pipe.matches(Regex("KotlinWinRT\\.$pid\\.[0-9a-f]{32}"))) { "Invalid development endpoint." }
            val process = ProcessHandle.of(pid).orElseThrow { IllegalStateException("The application has exited.") }
            require(process.isAlive) { "The application has exited." }
            if (executable != null) require(process.info().command().orElse("").replace('\\', '/').equals(executable.replace('\\', '/'), true)) {
                "The development process does not match this application host."
            }
            val started = process.info().startInstant().orElseThrow { IllegalStateException("Cannot verify the development process lifetime.") }
            return WinUIXamlHotReloadClient(file, process, started, port, token, pipe)
        }
    }
}
