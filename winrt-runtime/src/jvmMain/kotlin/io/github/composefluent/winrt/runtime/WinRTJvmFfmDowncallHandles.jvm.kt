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
 * This object owns the stable unbound FFM handles shared by compiler-expanded calls and the runtime
 * vtable invoker. Layout-dependent shapes retain the descriptor cache fallback.
 */
object WinRTJvmFfmDowncallHandles {
    private val linker = Linker.nativeLinker()
    private val hResultHandles = ConcurrentCacheMap<String, MethodHandle>()

    @JvmField
    val hResultNoArgs: MethodHandle = createHResultHandle()

    @JvmField
    val hResultAddress: MethodHandle = createHResultHandle(ValueLayout.ADDRESS)

    @JvmField
    val hResultInt32: MethodHandle = createHResultHandle(ValueLayout.JAVA_INT)

    @JvmField
    val hResultInt64: MethodHandle = createHResultHandle(ValueLayout.JAVA_LONG)

    @JvmField
    val hResultAddressAddress: MethodHandle = createHResultHandle(ValueLayout.ADDRESS, ValueLayout.ADDRESS)

    @JvmField
    val hResultInt32Address: MethodHandle = createHResultHandle(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)

    @JvmField
    val hResultInt32Int32: MethodHandle = createHResultHandle(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)

    @JvmField
    val hResultAddressAddressAddress: MethodHandle =
        createHResultHandle(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)

    @JvmField
    val hResultInt32AddressAddress: MethodHandle =
        createHResultHandle(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)

    @JvmField
    val hResultAddressInt32Address: MethodHandle =
        createHResultHandle(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)

    @JvmField
    val hResultInt32Int32AddressAddress: MethodHandle =
        createHResultHandle(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)

    @JvmField
    val hResultAddressAddressAddressAddress: MethodHandle =
        createHResultHandle(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)

    @JvmField
    val hResultAddressAddressInt32Address: MethodHandle =
        createHResultHandle(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)

    @JvmField
    val hResultAddressAddressAddressInt32Address: MethodHandle =
        createHResultHandle(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )

    @JvmField
    val hResultAddressAddressInt32AddressInt32Address: MethodHandle =
        createHResultHandle(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )

    @JvmField
    val hResultAddressAddressAddressInt32AddressInt32: MethodHandle =
        createHResultHandle(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        )

    fun hResult(abiShape: String): MethodHandle =
        hResultHandles.computeIfAbsent(abiShape) { shape ->
            linker.downcallHandle(hResultDescriptor(shape))
        }

    internal fun cachedHResultHandleCount(): Int = hResultHandles.size

    private fun createHResultHandle(vararg explicitParameterLayouts: MemoryLayout): MethodHandle =
        linker.downcallHandle(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, *explicitParameterLayouts),
        )

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
            "Int16" -> ValueLayout.JAVA_SHORT
            "Int32",
            "UInt32" -> ValueLayout.JAVA_INT
            "Int64",
            "UInt64" -> ValueLayout.JAVA_LONG
            "Float" -> ValueLayout.JAVA_FLOAT
            "Double" -> ValueLayout.JAVA_DOUBLE
            else -> null
        }
}
