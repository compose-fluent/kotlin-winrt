@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class)

package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.runtime.nativeapi.*
import kotlinx.cinterop.*
import kotlin.native.concurrent.Worker
import platform.windows.GetTickCount64
import platform.windows.Sleep

internal actual fun platformStartWinUIXamlHotReload(registry: WinUIXamlHotReloadRegistry): AutoCloseable? {
    initializeNativeXamlDevelopmentArguments()
    val directory = platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.SESSION_DIRECTORY)
        ?.takeIf(String::isNotBlank) ?: return null
    return runCatching { NativeWinUIXamlHotReloadServer(registry, directory) }.getOrElse {
        println("Kotlin WinRT XAML Hot Reload is unavailable: ${it.message}")
        null
    }
}

/** The transport owns no projected objects. As on JVM, only the registry's dispatcher
 * may enter WinUI. CsWinRT has no IDE transport; its object-reference ownership remains
 * in the existing runtime wrappers rather than this development adapter. */
internal class NativeWinUIXamlHotReloadServer(
    private val registry: WinUIXamlHotReloadRegistry,
    directory: String,
) : AutoCloseable {
    private val lock = PlatformLock()
    private var closed = false
    private var tracedPreview = false
    private val server = memScoped {
        requireNotNull(kotlin_winrt_development_open(directory.wcstr.ptr, WinUIXamlHotReloadProtocol.VERSION)) { "Cannot create the native development session." }
    }
    private val worker = try {
        Worker.start(name = "Kotlin WinRT XAML Hot Reload")
    } catch (failure: Throwable) {
        kotlin_winrt_development_destroy(server)
        throw failure
    }

    init {
        try {
            worker.executeAfter { listen() }
        } catch (failure: Throwable) {
            kotlin_winrt_development_destroy(server)
            worker.requestTermination(processScheduledJobs = false)
            throw failure
        }
    }

    private fun listen() {
        try {
            while (kotlin_winrt_development_accept(server) != 0) {
                try { handle() }
                catch (_: Exception) { /* Malformed frames and disconnects cannot affect the application. */ }
                finally { kotlin_winrt_development_disconnect(server) }
            }
        } finally {
            lock.withLock {
                closed = true
                registry.close()
                kotlin_winrt_development_destroy(server)
            }
            worker.requestTermination(processScheduledJobs = false)
        }
    }

    private fun receive(size: Int): ByteArray = ByteArray(size).also { bytes ->
        if (size > 0) bytes.usePinned {
            check(kotlin_winrt_development_read(server, it.addressOf(0), size) != 0) { "Disconnected development client." }
        }
    }

    private fun handle() {
        val header = receive(12)
        val command = WinUIXamlHotReloadCodec.readCommand(header + receive(WinUIXamlHotReloadCodec.frameSize(header)))
        val timing = if (!tracedPreview && command.inspection?.previewMarkup?.isNotEmpty() == true) GetTickCount64() else null
        val token = command.token.encodeToByteArray()
        val authenticated = token.size == 43 && token.usePinned {
            kotlin_winrt_development_authenticate(server, it.addressOf(0), token.size) != 0
        }
        val reply = when {
            !authenticated -> WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Invalid development session.")
            command.patch == null && command.inspection == null -> registry.snapshot()
            else -> {
                // A timed-out UI callback may still complete. Its Kotlin-owned result slot
                // outlives the request; it never retains freed events or socket handles.
                val response = PendingReply()
                if (command.inspection != null) registry.inspect(command.inspection, response::complete)
                else registry.submit(requireNotNull(command.patch), response::complete)
                timing?.let { println("XAML preview request queued: " + (GetTickCount64() - it) + "ms") }
                val deadline = GetTickCount64() + 10_000uL
                var result = response.get()
                while (result == null && GetTickCount64() < deadline && !lock.withLock { closed }) {
                    Sleep(5u)
                    result = response.get()
                }
                result ?: WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE,
                    "The UI update is still pending. Reconnect before sending another version.")
            }
        }
        val trace = timing?.takeIf { reply.inspection?.image != null }
        if (trace != null) {
            tracedPreview = true
            println("XAML preview response ready: " + (GetTickCount64() - trace) + "ms")
        }
        val bytes = WinUIXamlHotReloadCodec.writeReply(reply)
        trace?.let { println("XAML preview response encoded: " + (GetTickCount64() - it) + "ms") }
        bytes.usePinned { kotlin_winrt_development_write(server, it.addressOf(0), bytes.size) }
        trace?.let { println("XAML preview response sent: " + (GetTickCount64() - it) + "ms") }
    }

    override fun close() {
        lock.withLock {
            if (closed) return
            closed = true
            registry.close()
            // Wake accept/recv without blocking the UI thread on its pending dispatcher.
            // The worker frees native storage after leaving its request loop.
            kotlin_winrt_development_stop(server)
        }
    }

    private class PendingReply {
        private val lock = PlatformLock()
        private var reply: WinUIXamlHotReloadReply? = null
        fun complete(value: WinUIXamlHotReloadReply) { lock.withLock { reply = value } }
        fun get(): WinUIXamlHotReloadReply? = lock.withLock { reply }
    }
}
/** Native launcher boundary, called before choosing the generated designer or user main.
 * Packaged activation supplies arguments rather than inheriting the IDE environment. */
fun isNativeWinUIXamlPreviewRequested(): Boolean {
    initializeNativeXamlDevelopmentArguments()
    return platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT) == "1"
}

private fun initializeNativeXamlDevelopmentArguments() = memScoped {
    val count = alloc<IntVar>()
    val arguments = platform.windows.CommandLineToArgvW(platform.windows.GetCommandLineW()?.toKStringFromUtf16(), count.ptr) ?: return@memScoped
    try {
        for (index in 1 until count.value) {
            val argument = arguments[index]?.toKStringFromUtf16() ?: continue
            val prefix = "--kotlin-winrt-hot-reload-directory="
            when {
                argument.startsWith(prefix) -> {
                    val directory = argument.removePrefix(prefix)
                    require(directory.isNotBlank()) { "Empty development session directory." }
                    check(platform.windows.SetEnvironmentVariableW(WinUIXamlHotReloadProtocol.SESSION_DIRECTORY, directory) != 0)
                }
                argument.startsWith(WinUIXamlHotReloadProtocol.PREVIEW_THEME_ARGUMENT) -> {
                    val theme = argument.removePrefix(WinUIXamlHotReloadProtocol.PREVIEW_THEME_ARGUMENT)
                    require(theme in setOf("Default", "Light", "Dark")) { "Invalid preview theme." }
                    check(platform.windows.SetEnvironmentVariableW(WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT, theme) != 0)
                }
                argument == WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT ->
                    check(platform.windows.SetEnvironmentVariableW(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT, "1") != 0)
            }
        }
    } finally { platform.windows.LocalFree(arguments) }
}
