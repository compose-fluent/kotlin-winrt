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
import java.io.OutputStream
import java.io.InputStream
import java.io.FilterInputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Credentials remain private and ephemeral; only protocol DTOs enter the Compose display state. */
internal class WinUIXamlHotReloadClient private constructor(
    val sessionFile: Path, val process: ProcessHandle, val started: Instant,
    private val port: Int, private val token: String, private val pipeName: String? = null,
    private val pipeNamespace: String = "LOCAL",
) : AutoCloseable {
    private val connections = ConcurrentHashMap.newKeySet<AutoCloseable>()
    private val tracedImage = AtomicBoolean()
    @Volatile private var closed = false
    val alive get() = !closed && process.isAlive && process.info().startInstant().orElse(null) == started

    fun request(patch: WinUIXamlHotReloadPatch? = null): WinUIXamlHotReloadReply {
        return exchange { output -> WinUIXamlHotReloadWire.writeRequest(output, token, patch) }
    }
    fun inspect(request: WinUIXamlInspectionRequest): WinUIXamlHotReloadReply {
        fun fetch(value: WinUIXamlInspectionRequest) = exchange { output -> WinUIXamlHotReloadWire.writeInspectionRequest(output, token, value) }
        val first = fetch(request)
        val initial = first.inspection ?: return first
        if (initial.nextOffset == 0 || first.status != WinUIXamlHotReloadProtocol.APPLIED) return first
        val nodes = initial.nodes.toMutableList()
        var offset = initial.nextOffset
        while (offset != 0) {
            check(nodes.size == offset && offset < initial.totalNodes && initial.totalNodes <= WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) { "Invalid visual tree continuation." }
            val reply = fetch(request.copy(capture = false, previewMarkup = "", animation = null, readProperties = false,
                readTree = true, treeToken = initial.treeToken, treeOffset = offset))
            check(reply.status == WinUIXamlHotReloadProtocol.APPLIED) { reply.message }
            val page = requireNotNull(reply.inspection)
            check(page.treeToken == initial.treeToken && page.totalNodes == initial.totalNodes && page.nodes.isNotEmpty() &&
                (page.nextOffset == 0 || page.nextOffset > offset)) { "The visual tree generation changed while reading pages." }
            nodes += page.nodes
            offset = page.nextOffset
        }
        check(nodes.size == initial.totalNodes) { "Incomplete visual tree snapshot." }
        return first.copy(inspection = initial.copy(nodes = nodes, nextOffset = 0), message = "${nodes.size} visual nodes")
    }
    fun transferState(request: WinUIXamlStateRequest): WinUIXamlHotReloadReply = exchange { output ->
        WinUIXamlHotReloadWire.writeStateRequest(output, token, request)
    }
    private fun exchange(send: (OutputStream) -> Unit): WinUIXamlHotReloadReply {
        check(alive) { "The application has exited. Start a new development session." }
        if (pipeName != null) return exchangePipe(pipeName, send)
        val started = System.nanoTime()
        return Socket().use { socket ->
            connections += socket
            try {
                check(!closed) { "The development connection is closed." }
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2_000)
                socket.soTimeout = 12_000
                send(socket.getOutputStream())
                readReply(socket.getInputStream(), started)
            } finally { connections -= socket }
        }
    }

    private fun exchangePipe(name: String, send: (OutputStream) -> Unit): WinUIXamlHotReloadReply {
        val started = System.nanoTime()
        val pipe = WinRTDevelopmentPipe("\\\\.\\pipe\\${pipeNamespace.replace('/', '\\')}\\$name", isCancelled = { closed })
        connections += pipe.cancellation
        try {
            check(!closed) { "The development connection is closed." }
            send(pipe.output)
            return readReply(pipe.input, started).also { pipe.output.write(0x6b) }
        } finally {
            connections -= pipe.cancellation
            pipe.dispose()
        }
    }

    /** First-frame diagnostics contain durations/counts only, never session data. */
    private fun readReply(input: InputStream, started: Long): WinUIXamlHotReloadReply {
        if (tracedImage.get()) return WinUIXamlHotReloadWire.readReply(input)
        val reading = System.nanoTime()
        var ioNanos = 0L
        var byteCount = 0L
        var firstReadNanos = -1L
        val measured = object : FilterInputStream(input) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val before = System.nanoTime()
                return try { super.read(bytes, offset, length).also { if (it > 0) byteCount += it } }
                finally {
                    val elapsed = System.nanoTime() - before
                    ioNanos += elapsed
                    if (firstReadNanos < 0) firstReadNanos = elapsed
                }
            }
        }
        return WinUIXamlHotReloadWire.readReply(measured).also { reply ->
            val finished = System.nanoTime()
            if (reply.inspection?.image != null && tracedImage.compareAndSet(false, true)) {
                com.intellij.openapi.diagnostic.Logger.getInstance(WinUIXamlHotReloadClient::class.java).info(
                    "XAML first image transfer: open/send=${(reading - started) / 1_000_000}ms, " +
                        "wait/read=${ioNanos / 1_000_000}ms (header=${firstReadNanos / 1_000_000}ms), decode=${(finished - reading - ioNanos) / 1_000_000}ms, bytes=$byteCount")
            }
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
            val pipeNamespace = properties.getProperty("pipeNamespace")?.takeIf(String::isNotBlank) ?: "LOCAL"
            require(pipeNamespace == "LOCAL" || pipeNamespace.matches(
                Regex("Sessions/[0-9]+/AppContainerNamedObjects/S-1-15-2-(?:[0-9]+-){6}[0-9]+"))) {
                "Invalid development pipe namespace."
            }
            require(if (pipe == null) port in 1..65535 else port == 0 &&
                pipe.matches(Regex("KotlinWinRT\\.$pid\\.[0-9a-f]{32}"))) { "Invalid development endpoint." }
            val process = ProcessHandle.of(pid).orElseThrow { IllegalStateException("The application has exited.") }
            require(process.isAlive) { "The application has exited." }
            if (executable != null) require(process.info().command().orElse("").replace('\\', '/').equals(executable.replace('\\', '/'), true)) {
                "The development process does not match this application host."
            }
            val started = process.info().startInstant().orElseThrow { IllegalStateException("Cannot verify the development process lifetime.") }
            return WinUIXamlHotReloadClient(file, process, started, port, token, pipe, pipeNamespace)
        }
    }
}
