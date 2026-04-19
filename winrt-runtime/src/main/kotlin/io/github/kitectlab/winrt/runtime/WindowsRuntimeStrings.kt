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

        fun createReference(value: String): ReferencedHString {
            // Narrow temporary deviation from CsWinRT:
            // on the JVM we don't yet have a stable zero-copy HSTRING-reference path,
            // so this currently falls back to an owned HSTRING wrapper until the
            // WindowsCreateStringReference header handling is implemented correctly.
            return ReferencedHString(
                backing = create(value),
            )
        }

        fun fromHandle(handle: MemorySegment, owner: Boolean): HString = HString(handle, owner)
    }
}

class ReferencedHString internal constructor(
    private val backing: HString,
) : AutoCloseable {
    val handle: MemorySegment
        get() = backing.handle

    fun toKString(): String = HString.fromHandle(handle, owner = false).toKString()

    override fun close() {
        backing.close()
    }
}
