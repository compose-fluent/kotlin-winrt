package io.github.composefluent.winrt.runtime

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.*
import java.nio.file.attribute.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.*

internal actual fun platformStartWinRTXamlHotReload(registry: WinRTXamlHotReloadRegistry): AutoCloseable? {
    val directory = System.getenv(WinRTXamlHotReloadProtocol.SESSION_DIRECTORY)?.takeIf(String::isNotBlank) ?: return null
    return runCatching { WinRTXamlHotReloadServer(registry, Path.of(directory)) }.getOrElse {
        System.err.println("Kotlin WinRT XAML Hot Reload is unavailable: ${it.message}")
        null
    }
}

internal class WinRTXamlHotReloadServer(private val registry: WinRTXamlHotReloadRegistry, directory: Path) : AutoCloseable {
    private val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private val server = ServerSocket()
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
        { task -> Thread(task, "Kotlin WinRT XAML Hot Reload client").apply { isDaemon = true } })
    private val session = directory.resolve("${ProcessHandle.current().pid()}-${UUID.randomUUID()}.session")
    private var ownsSession = false
    @Volatile private var closed = false

    init {
        try {
            require(directory.isAbsolute) { "The development session directory must be absolute." }
            Files.createDirectories(directory); restrict(directory)
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16)
            val temporary = Files.createTempFile(directory, ".session-", ".tmp")
            try {
                restrict(temporary)
                Files.newOutputStream(temporary).use { Properties().apply {
                    setProperty("protocol", WinRTXamlHotReloadProtocol.VERSION.toString())
                    setProperty("port", server.localPort.toString()); setProperty("token", token)
                    setProperty("pid", ProcessHandle.current().pid().toString())
                }.store(it, "Kotlin WinRT development session") }
                Files.move(temporary, session, StandardCopyOption.ATOMIC_MOVE)
                ownsSession = true
            } finally { Files.deleteIfExists(temporary) }
            Thread(::accept, "Kotlin WinRT XAML Hot Reload listener").apply { isDaemon = true; start() }
        } catch (failure: Exception) { close(); throw failure }
    }

    private fun accept() {
        while (!closed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            clients += socket
            try { workers.execute { handle(socket) } }
            catch (_: RejectedExecutionException) { clients -= socket; socket.close() }
        }
    }
    private fun handle(socket: Socket) {
        try {
            socket.use {
                require(it.inetAddress.isLoopbackAddress) { "Only loopback development clients are accepted." }
                it.soTimeout = 3_000
                val request = WinRTXamlHotReloadWire.readCommand(it.getInputStream())
                val supplied = request.token; val patch = request.patch
                if (!MessageDigest.isEqual(supplied.toByteArray(Charsets.UTF_8), token.toByteArray(Charsets.UTF_8))) {
                    WinRTXamlHotReloadWire.writeReply(it.getOutputStream(), WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.REJECTED, "Invalid development session."))
                    return
                }
                val reply = if (patch == null && request.inspection == null) registry.snapshot() else {
                    val latch = CountDownLatch(1)
                    var response: WinRTXamlHotReloadReply? = null
                    val complete: (WinRTXamlHotReloadReply) -> Unit = { result -> response = result; latch.countDown() }
                    if (request.inspection != null) registry.inspect(request.inspection, complete) else registry.submit(requireNotNull(patch), complete)
                    if (latch.await(10, TimeUnit.SECONDS)) requireNotNull(response)
                    else WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.UNAVAILABLE, "The UI update is still pending. Reconnect before sending another version.")
                }
                WinRTXamlHotReloadWire.writeReply(it.getOutputStream(), reply)
            }
        } catch (_: Exception) {
            // Invalid/truncated or disconnected clients must not affect the application.
        } finally { clients -= socket }
    }
    override fun close() {
        if (closed) return
        closed = true
        registry.close()
        server.close(); clients.forEach { runCatching { it.close() } }; workers.shutdownNow()
        if (ownsSession) Files.deleteIfExists(session)
    }
    private fun restrict(path: Path) {
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
        if (acl != null) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
            .setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
        else Files.setPosixFilePermissions(path, if (Files.isDirectory(path)) PosixFilePermissions.fromString("rwx------") else PosixFilePermissions.fromString("rw-------"))
    }
}
