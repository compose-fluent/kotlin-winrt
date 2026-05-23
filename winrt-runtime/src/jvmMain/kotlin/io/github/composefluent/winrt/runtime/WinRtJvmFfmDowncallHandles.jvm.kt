package io.github.composefluent.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/**
 * JVM ABI primitive used by compiler-plugin IR lowering.
 *
 * The plugin still owns vtable lookup, carrier conversion, HRESULT handling, and result readback.
 * This cache only owns the stable descriptor-shaped unbound FFM downcall handle.
 */
object WinRtJvmFfmDowncallHandles {
    private val linker: Linker by lazy { Linker.nativeLinker() }
    private val hResultHandles = ConcurrentCacheMap<String, MethodHandle>()

    fun hResult(abiShape: String): MethodHandle =
        hResultHandles.computeIfAbsent(abiShape) { shape ->
            linker.downcallHandle(hResultDescriptor(shape))
        }

    internal fun cachedHResultHandleCount(): Int = hResultHandles.size

    private fun hResultDescriptor(abiShape: String): FunctionDescriptor {
        val argumentLayouts = if (abiShape.isBlank()) {
            emptyArray<MemoryLayout>()
        } else {
            abiShape.split(',').map(::layoutForToken).toTypedArray()
        }
        return FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, *argumentLayouts)
    }

    private fun layoutForToken(token: String): MemoryLayout =
        when (token) {
            "RawAddress",
            "RawComPtr",
            "String",
            "Struct",
            "Object" -> ValueLayout.ADDRESS
            else -> structLayoutForToken(token)
                ?: scalarLayoutForToken(token)
                ?: error("Unsupported WinRT JVM FFM ABI shape token: $token")
        }

    private fun structLayoutForToken(token: String): MemoryLayout? {
        val match = STRUCT_LAYOUT_TOKEN.matchEntire(token) ?: return null
        val size = match.groupValues[1].toLong()
        val alignment = match.groupValues[2].toLong()
        if (size <= 0 || alignment <= 0) {
            return null
        }
        val chunk = when {
            alignment >= Long.SIZE_BYTES.toLong() && size % Long.SIZE_BYTES == 0L -> ValueLayout.JAVA_LONG
            alignment >= Int.SIZE_BYTES.toLong() && size % Int.SIZE_BYTES == 0L -> ValueLayout.JAVA_INT
            alignment >= Short.SIZE_BYTES.toLong() && size % Short.SIZE_BYTES == 0L -> ValueLayout.JAVA_SHORT
            alignment == Byte.SIZE_BYTES.toLong() -> ValueLayout.JAVA_BYTE
            else -> return null
        }
        return MemoryLayout.structLayout(*Array((size / chunk.byteSize()).toInt()) { chunk })
    }

    private val STRUCT_LAYOUT_TOKEN = Regex("""Struct(\d+)_(\d+)""")

    private fun scalarLayoutForToken(token: String): MemoryLayout? =
        when (token) {
            "Byte",
            "Boolean" -> ValueLayout.JAVA_BYTE
            "Int32",
            "UInt32" -> ValueLayout.JAVA_INT
            "Int64",
            "UInt64" -> ValueLayout.JAVA_LONG
            "Float" -> ValueLayout.JAVA_FLOAT
            "Double" -> ValueLayout.JAVA_DOUBLE
            else -> null
        }
}
