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
 * vtable invoker.
 */
object WinRTJvmFfmDowncallHandles {
    private val linker = Linker.nativeLinker()

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

    /** Initializes one compiler-synthesized static handle from its already closed carrier vector. */
    @PublishedApi
    internal fun createExactHResultHandle(vararg explicitParameterLayouts: MemoryLayout): MethodHandle =
        createHResultHandle(*explicitParameterLayouts)

    /**
     * Compiler-lowered WinRT calls use raw 64-bit address words for pointer carriers. On the
     * Windows x64 ABI they have the same register/stack classification as native pointers,
     * while avoiding one FFM MemorySegment wrapper for every explicit address argument.
     */
    @PublishedApi
    internal fun createExactHResultWordHandle(vararg explicitParameterLayouts: MemoryLayout): MethodHandle {
        check(ValueLayout.ADDRESS.byteSize() == ValueLayout.JAVA_LONG.byteSize()) {
            "Raw-word WinRT downcalls require a 64-bit native address ABI."
        }
        return linker.downcallHandle(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, *explicitParameterLayouts),
        )
    }

    private fun createHResultHandle(vararg explicitParameterLayouts: MemoryLayout): MethodHandle =
        linker.downcallHandle(
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, *explicitParameterLayouts),
        )


}
