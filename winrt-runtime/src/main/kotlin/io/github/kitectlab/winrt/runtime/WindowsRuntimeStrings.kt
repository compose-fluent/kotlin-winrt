package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

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

        fun fromHandle(handle: MemorySegment, owner: Boolean): HString = HString(handle, owner)
    }
}
