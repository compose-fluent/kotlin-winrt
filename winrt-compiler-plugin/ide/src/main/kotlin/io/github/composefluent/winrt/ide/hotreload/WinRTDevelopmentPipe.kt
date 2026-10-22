package io.github.composefluent.winrt.ide.hotreload

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** Windows transport boundary. JDK file-channel close can wait for a synchronous pipe read. */
internal class WinRTDevelopmentPipe(path: String, timeoutMillis: Long = 12_000) {
    private val gate = Any()
    private var cancelled = false
    private var disposed = false
    private val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    private val handle = kernel.getFunction("CreateFileW").invokePointer(arrayOf(
        WString(path), 0xc0000000.toInt(), 0, null, 3, 0x40000000, null))
        .also { if (it == null || Pointer.nativeValue(it) == -1L) throw failure("Open development pipe") }

    val input = object : InputStream() {
        override fun read(): Int = ByteArray(1).let { if (read(it) < 0) -1 else it[0].toInt() and 255 }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = transfer(bytes, offset, length, false)
    }
    val output = object : OutputStream() {
        override fun write(value: Int) = write(byteArrayOf(value.toByte()))
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
            var written = 0
            while (written < length) {
                val count = transfer(bytes, offset + written, length - written, true)
                if (count <= 0) throw IOException("The development pipe stopped accepting data.")
                written += count
            }
        }
    }

    // close() only requests cancellation. The exchange owner releases the handle
    // after GetOverlappedResult acknowledges completion, as required by CancelIoEx.
    val cancellation = AutoCloseable {
        synchronized(gate) {
            if (!disposed) {
                cancelled = true
                call("CancelIoEx", handle, null)
            }
        }
    }

    fun dispose() = synchronized(gate) {
        if (!disposed) { disposed = true; call("CloseHandle", handle) }
    }

    private fun transfer(bytes: ByteArray, offset: Int, length: Int, writing: Boolean): Int {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (length == 0) return 0
        // Bounded unmanaged storage; callers may send a large bitmap frame.
        val size = minOf(length, 64 * 1024)
        val event = kernel.getFunction("CreateEventW").invokePointer(arrayOf(null, 1, 0, null))
            ?: throw failure("Create pipe completion event")
        try {
            Memory(size.toLong()).use { buffer ->
                // OVERLAPPED: two ULONG_PTRs, the eight-byte offset union, HANDLE.
                Memory((Native.POINTER_SIZE * 3 + 8).toLong()).use { overlapped ->
                    overlapped.clear()
                    overlapped.setPointer((Native.POINTER_SIZE * 2 + 8).toLong(), event)
                    if (writing) buffer.write(0, bytes, offset, size)
                    val transferred = IntByReference()
                    val pending = synchronized(gate) {
                        if (cancelled || disposed) throw IOException("The development pipe is closed.")
                        val ok = call(if (writing) "WriteFile" else "ReadFile", handle, buffer, size, transferred, overlapped)
                        if (ok == 0) {
                            val error = Native.getLastError()
                            if (!writing && error == 109) return -1
                            if (error != 997) throw failure("Transfer development data", error)
                        }
                        ok == 0
                    }
                    if (pending) {
                        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                        val wait = call("WaitForSingleObject", event, remaining)
                        if (wait != 0) {
                            call("CancelIoEx", handle, overlapped)
                            // Never free a buffer still owned by an outstanding Windows I/O.
                            call("GetOverlappedResult", handle, overlapped, transferred, 1)
                            if (wait == 258) throw SocketTimeoutException("The development pipe did not respond within the request timeout.")
                            throw IOException("Waiting for the development pipe failed ($wait).")
                        }
                        if (call("GetOverlappedResult", handle, overlapped, transferred, 0) == 0) {
                            val error = Native.getLastError()
                            if (!writing && error == 109) return -1
                            throw failure("Complete development data transfer", error)
                        }
                    }
                    val count = transferred.value
                    if (!writing && count > 0) buffer.read(0, bytes, offset, count)
                    return if (!writing && count == 0) -1 else count
                }
            }
        } finally { call("CloseHandle", event) }
    }

    companion object {
        private val kernel by lazy { NativeLibrary.getInstance("kernel32", mapOf("calling-convention" to Function.ALT_CONVENTION)) }
        private fun call(name: String, vararg args: Any?): Int = kernel.getFunction(name).invokeInt(args)
        private fun failure(operation: String, error: Int = Native.getLastError()) = IOException("$operation failed (Windows error $error).")
    }
}
