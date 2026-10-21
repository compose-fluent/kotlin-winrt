@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.*
import io.github.composefluent.winrt.runtime.nativeapi.kotlin_winrt_development_socket_timeout
import platform.windows.*
import platform.posix.sockaddr_in
import platform.posix.send
import platform.posix.INVALID_SOCKET
import kotlin.test.*

/** Native transport parity with the JVM session test. The registry remains the
 * shared UI dispatcher/transaction owner; no COM objects cross this socket. */
class WinUIXamlHotReloadNativeTest {
    private class Element(var text: String = "before")
    @Test fun authenticates_loopback_and_removes_session_on_close() = exerciseTransport(false)
    @Test fun authenticates_named_pipe_and_removes_session_on_close() = exerciseTransport(true)

    private fun exerciseTransport(pipeMode: Boolean) = memScoped {
        val temporary = allocArray<UShortVar>(32768)
        assertTrue(GetTempPathW(32768u, temporary) > 0u)
        val directory = temporary.toKStringFromUtf16() + "winrt-reload-${GetCurrentProcessId()}-${GetTickCount64()}"
        // Runtime startup can precede the IDE listener creating its session folder.
        val queueLock = PlatformLock()
        val queue = mutableListOf<() -> Unit>()
        val registry = WinUIXamlHotReloadRegistry({ { task -> queueLock.withLock { queue += task }; true } }, { _, text -> text })
        val element = Element()
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(
            Element::class, "native.ReloadElement", "System.Object", isWinRTComponent = false,
            members = listOf(WinUIXamlMemberDefinition("Text", "String", String::class,
                { (it as Element).text }, { target, value -> (target as Element).text = value as String }))))
        registry.observe(element, "native.Page", "Page.xaml", "a".repeat(64), "Greeting", element)
        registry.observe(element, "native.Page", "Page.xaml", "a".repeat(64), null, null)
        val transportVariable = "KOTLIN_WINRT_DEVELOPMENT_TRANSPORT"
        val previousTransport = platformGetWindowsEnvironmentVariable(transportVariable)
        val server = try {
            SetEnvironmentVariableW(transportVariable, if (pipeMode) "pipe" else null)
            NativeWinUIXamlHotReloadServer(registry, directory)
        } finally { SetEnvironmentVariableW(transportVariable, previousTransport) }
        var session = ""
        try {
            val data = alloc<WIN32_FIND_DATAW>()
            val search = FindFirstFileW("$directory\\*.session", data.ptr)
            assertTrue(search != INVALID_HANDLE_VALUE)
            session = "$directory\\${data.cFileName.toKStringFromUtf16()}"
            FindClose(search)
            val file = CreateFileW(session, GENERIC_READ.toUInt(), FILE_SHARE_READ.toUInt(), null,
                OPEN_EXISTING.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null)
            assertTrue(file != INVALID_HANDLE_VALUE)
            val properties = try {
                val buffer = allocArray<ByteVar>(512)
                val size = alloc<UIntVar>()
                assertTrue(ReadFile(file, buffer, 512u, size.ptr, null) != 0)
                buffer.readBytes(size.value.toInt()).decodeToString().lineSequence().filter { '=' in it }
                    .associate { it.substringBefore('=') to it.substringAfter('=') }
            } finally { CloseHandle(file) }
            assertEquals(WinUIXamlHotReloadProtocol.VERSION.toString(), properties["protocol"])
            assertTrue(properties.getValue("token").matches(Regex("[A-Za-z0-9_-]{43}")))
            val pipePath = "\\\\.\\pipe\\LOCAL\\${properties.getValue("pipe")}"
            if (pipeMode) {
                assertEquals("0", properties["port"])
                assertTrue(properties.getValue("pipe").startsWith("KotlinWinRT.${GetCurrentProcessId()}."))
            }
            fun request(token: String, patch: WinUIXamlHotReloadPatch? = null): WinUIXamlHotReloadReply {
                val client = if (pipeMode) INVALID_SOCKET else socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
                val pipe = if (!pipeMode) INVALID_HANDLE_VALUE else {
                    assertTrue(WaitNamedPipeW(pipePath, 3000u) != 0)
                    CreateFileW(pipePath, GENERIC_READ.toUInt() or GENERIC_WRITE.toUInt(), 0u, null,
                        OPEN_EXISTING.toUInt(), 0u, null).also { assertTrue(it != INVALID_HANDLE_VALUE) }
                }
                try {
                    if (!pipeMode) {
                        assertTrue(client != INVALID_SOCKET)
                        val address = alloc<sockaddr_in>()
                        address.sin_family = AF_INET.toShort()
                        address.sin_addr.S_un.S_addr = htonl(INADDR_LOOPBACK.toUInt())
                        address.sin_port = htons(properties.getValue("port").toUShort())
                        assertEquals(0, connect(client, address.ptr.reinterpret(), sizeOf<sockaddr_in>().toInt()))
                        assertEquals(1, kotlin_winrt_development_socket_timeout(client, 3000u))
                    }

                    fun transmit(bytes: ByteArray) = bytes.usePinned { pinned ->
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = if (!pipeMode) send(client, pinned.addressOf(offset), bytes.size - offset, 0) else {
                                val written = alloc<UIntVar>()
                                assertTrue(WriteFile(pipe, pinned.addressOf(offset), (bytes.size - offset).toUInt(), written.ptr, null) != 0)
                                written.value.toInt()
                            }
                            assertTrue(count > 0); offset += count
                        }
                    }
                    transmit(WinUIXamlHotReloadCodec.writeRequest(token, patch))
                    fun receive(length: Int) = ByteArray(length).also { buffer ->
                        buffer.usePinned { pinned ->
                            var offset = 0
                            while (offset < length) {
                                val count = if (!pipeMode) recv(client, pinned.addressOf(offset), length - offset, 0) else {
                                    val read = alloc<UIntVar>()
                                    assertTrue(ReadFile(pipe, pinned.addressOf(offset), (length - offset).toUInt(), read.ptr, null) != 0)
                                    read.value.toInt()
                                }
                                assertTrue(count > 0); offset += count
                            }
                        }
                    }
                    if (patch != null) {
                        val deadline = GetTickCount64() + 3000uL
                        var action: (() -> Unit)? = null
                        while (action == null && GetTickCount64() < deadline) {
                            action = queueLock.withLock { if (queue.isEmpty()) null else queue.removeAt(0) }
                            if (action == null) Sleep(5u)
                        }
                        assertNotNull(action, "Native transport must enqueue on the owning UI thread").invoke()
                    }
                    val header = receive(12)
                    return WinUIXamlHotReloadCodec.readReply(header + receive(WinUIXamlHotReloadCodec.frameSize(header))).also {
                        if (pipeMode) transmit(byteArrayOf(0x6b))
                    }
                } finally { if (pipeMode) CloseHandle(pipe) else closesocket(client) }
            }
            assertEquals(WinUIXamlHotReloadProtocol.REJECTED, request("wrong").status)
            assertEquals(WinUIXamlHotReloadProtocol.APPLIED, request(properties.getValue("token")).status)
            val update = WinUIXamlHotReloadPatch("native.Page", "Page.xaml", "a".repeat(64), "b".repeat(64), 1,
                listOf(WinUIXamlHotReloadChange("Greeting", "Text", "after")))
            assertEquals(WinUIXamlHotReloadProtocol.APPLIED, request(properties.getValue("token"), update).status)
            assertEquals("after", element.text)
            assertEquals(1L, registry.snapshot().roots.single().version)
            server.close()
            assertEquals(INVALID_FILE_ATTRIBUTES, GetFileAttributesW(session))
            server.close()
        } finally {
            server.close()
            if (session.isNotEmpty()) DeleteFileW(session)
            RemoveDirectoryW(directory)
        }
    }
}
