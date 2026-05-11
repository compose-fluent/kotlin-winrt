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
            "Byte",
            "Boolean" -> ValueLayout.JAVA_BYTE
            "Int32",
            "UInt32" -> ValueLayout.JAVA_INT
            "Int64",
            "UInt64" -> ValueLayout.JAVA_LONG
            "Float" -> ValueLayout.JAVA_FLOAT
            "Double" -> ValueLayout.JAVA_DOUBLE
            else -> error("Unsupported WinRT JVM FFM ABI shape token: $token")
        }
}
