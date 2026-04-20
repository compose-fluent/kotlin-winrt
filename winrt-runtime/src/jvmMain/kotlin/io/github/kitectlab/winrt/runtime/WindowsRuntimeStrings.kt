package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets

class HString private constructor(
    val handle: MemorySegment,
    private val owner: Boolean,
) : AutoCloseable {
    fun toKString(): String {
        Arena.ofConfined().use { arena ->
            val lengthOut = arena.allocate(ValueLayout.JAVA_INT)
            val buffer = WindowsRuntimePlatform.windowsGetStringRawBuffer(handle, lengthOut)
            val length = lengthOut.get(ValueLayout.JAVA_INT, 0)
            return buffer.reinterpret((length * 2).toLong()).toArray(ValueLayout.JAVA_CHAR).concatToString()
        }
    }

    override fun close() {
        if (owner && handle != MemorySegment.NULL) {
            WindowsRuntimePlatform.windowsDeleteString(handle)
        }
    }

    companion object {
        fun create(value: String): HString {
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }

            Arena.ofConfined().use { arena ->
                val utf16 = arena.allocateFrom(ValueLayout.JAVA_CHAR, *value.toCharArray())
                val out = arena.allocate(ValueLayout.ADDRESS)
                WindowsRuntimePlatform.checkSucceeded(
                    WindowsRuntimePlatform.windowsCreateString(
                        utf16,
                        value.length,
                        out,
                    ),
                )
                return HString(out.get(ValueLayout.ADDRESS, 0), owner = true)
            }
        }

        fun createReference(value: String): ReferencedHString {
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }
            if (value.isEmpty()) {
                return ReferencedHString(
                    handle = MemorySegment.NULL,
                    lifetime = null,
                )
            }

            val arena = Arena.ofConfined()
            try {
                val utf16 = arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE)
                val header = arena.allocate(AbiLayouts.HSTRING_HEADER)
                val out = arena.allocate(ValueLayout.ADDRESS)
                WindowsRuntimePlatform.checkSucceeded(
                    WindowsRuntimePlatform.windowsCreateStringReference(
                        utf16Chars = utf16,
                        length = value.length,
                        header = header,
                        outHandle = out,
                    ),
                )
                return ReferencedHString(
                    handle = out.get(ValueLayout.ADDRESS, 0),
                    lifetime = arena,
                )
            } catch (error: Throwable) {
                arena.close()
                throw error
            }
        }

        fun fromHandle(handle: MemorySegment, owner: Boolean): HString = HString(handle, owner)
    }
}

class ReferencedHString internal constructor(
    val handle: MemorySegment,
    private val lifetime: AutoCloseable?,
) : AutoCloseable {
    fun toKString(): String =
        if (handle == MemorySegment.NULL) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    override fun close() {
        lifetime?.close()
    }
}
