package io.github.composefluent.winrt.ide.debug

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.BufferedReader
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** The SB API lives in the LLDB process; no IDE-specific native debugger plugin is required. */
internal class WinRTNativeDebuggerBackend private constructor(
    private val process: Process,
    private val connection: Socket,
    private val reader: BufferedReader,
    private val directory: Path,
    private val event: (JsonObject) -> Unit,
) : AutoCloseable {
    private val writer = connection.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val sequence = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonObject>>()
    private val closed = AtomicBoolean()

    init {
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                while (!closed.get()) {
                    val line = readMessage(reader) ?: break
                    val message = JsonParser.parseString(line).asJsonObject
                    if (message.has("event")) event(message)
                    else pending.remove(message["id"].asLong)?.let { future ->
                        if (message.has("error")) future.completeExceptionally(IOException(message["error"].asString))
                        else future.complete(message.getAsJsonObject("result"))
                    }
                }
            } catch (error: Exception) {
                if (!closed.get()) event(json("event" to "error", "message" to (error.message ?: "Native debugger disconnected.")))
            } finally {
                close()
                event(json("event" to "closed"))
            }
        }
        process.onExit().thenRun { close(); cleanup() }
    }

    fun request(command: String, vararg fields: Pair<String, Any?>): CompletableFuture<JsonObject> {
        val id = sequence.incrementAndGet()
        val future = CompletableFuture<JsonObject>()
        pending[id] = future
        try {
            synchronized(writer) {
                check(!closed.get()) { "The native debugger is closed." }
                writer.write(json("id" to id, "command" to command, *fields).toString())
                writer.newLine()
                writer.flush()
            }
        } catch (error: Exception) { pending.remove(id); future.completeExceptionally(error) }
        return future.orTimeout(20, TimeUnit.SECONDS).whenComplete { _, _ -> pending.remove(id) }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            // EOF makes the bridge detach, restoring a paused application. Do not
            // terminate an attached process merely because an IDE tab was closed.
            runCatching { connection.close() }
            val error = IOException("The native debugger connection closed.")
            pending.values.forEach { it.completeExceptionally(error) }
            pending.clear()
        }
    }

    private fun cleanup() {
        listOf("winrt_lldb.py", "winrt_debug_bridge.py", "start.lldb").forEach { runCatching { Files.deleteIfExists(directory.resolve(it)) } }
        runCatching { Files.deleteIfExists(directory) }
    }

    companion object {
        fun start(lldb: Path, event: (JsonObject) -> Unit, output: (String) -> Unit = {}): WinRTNativeDebuggerBackend {
            require(Files.isRegularFile(lldb)) { "Select a Windows LLDB executable in Kotlin WinRT settings." }
            val directory = Files.createTempDirectory("kotlin-winrt-debug-")
            var child: Process? = null
            var socket: Socket? = null
            try {
                for (name in listOf("winrt_lldb.py", "winrt_debug_bridge.py")) {
                    WinRTNativeDebuggerBackend::class.java.getResourceAsStream("/native-debug/$name").use { resource ->
                        requireNotNull(resource) { "Missing Native debugger resource: $name" }
                        Files.copy(resource, directory.resolve(name))
                    }
                }
                val script = directory.resolve("winrt_debug_bridge.py").toString().replace('\\', '/').replace("\"", "\\\"")
                val commands = directory.resolve("start.lldb")
                Files.writeString(commands, "command script import \"$script\"\nscript winrt_debug_bridge.run(lldb.debugger)\nquit\n")
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                    server.soTimeout = 15_000
                    val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
                    val builder = ProcessBuilder(lldb.toString(), "--no-lldbinit", "--batch", "--source", commands.toString()).redirectErrorStream(true)
                    builder.environment()["KOTLIN_WINRT_DEBUG_BRIDGE"] = json("port" to server.localPort, "token" to token).toString()
                    builder.environment()["PYTHONDONTWRITEBYTECODE"] = "1"
                    // Android's Windows LLDB ships Python beside bin; mirror lldb.cmd.
                    val python = lldb.parent.parent.resolve("python3")
                    if (Files.isDirectory(python)) {
                        builder.environment()["PYTHONHOME"] = python.toString()
                        builder.environment()["PATH"] = "$python;${builder.environment()["PATH"].orEmpty()}"
                    }
                    val process = builder.start().also { child = it }
                    AppExecutorUtil.getAppExecutorService().execute {
                        process.inputStream.bufferedReader().useLines { lines -> lines.forEach(output) }
                    }
                    val connection = server.accept().also { socket = it; it.soTimeout = 10_000 }
                    val reader = connection.getInputStream().bufferedReader(Charsets.UTF_8)
                    val hello = JsonParser.parseString(readMessage(reader) ?: throw IOException("Native debugger closed during startup.")).asJsonObject
                    require(MessageDigest.isEqual(token.toByteArray(), hello["token"].asString.toByteArray())) { "Native debugger authentication failed." }
                    connection.soTimeout = 0
                    return WinRTNativeDebuggerBackend(process, connection, reader, directory, event)
                }
            } catch (error: Exception) {
                socket?.close()
                // No attach request can have been sent before the handshake succeeds.
                child?.destroyForcibly()
                listOf("winrt_lldb.py", "winrt_debug_bridge.py", "start.lldb").forEach { runCatching { Files.deleteIfExists(directory.resolve(it)) } }
                runCatching { Files.deleteIfExists(directory) }
                throw error
            }
        }

        internal fun json(vararg fields: Pair<String, Any?>) = JsonObject().apply {
            fields.forEach { (name, value) -> when (value) {
                is Number -> addProperty(name, value)
                is Boolean -> addProperty(name, value)
                null -> add(name, null)
                else -> addProperty(name, value.toString())
            } }
        }

        private fun readMessage(reader: BufferedReader): String? {
            val line = StringBuilder()
            while (line.length <= 1024 * 1024) {
                val character = reader.read()
                if (character < 0) return if (line.isEmpty()) null else throw IOException("Truncated native debugger message.")
                if (character == 10) return line.toString()
                line.append(character.toChar())
            }
            throw IOException("Native debugger message exceeds the message limit.")
        }
    }
}
