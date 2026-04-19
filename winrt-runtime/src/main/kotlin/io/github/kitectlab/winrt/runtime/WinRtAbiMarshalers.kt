package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout

object WinRtAbiMarshalers {
    fun invokeUnit(target: ComObjectReference, slot: Int) {
        WindowsRuntimePlatform.checkSucceeded(
            target.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
            ),
        )
    }

    fun invokeString(target: ComObjectReference, slot: Int): HString =
        invokeHStringOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeBoolean(target: ComObjectReference, slot: Int): Boolean =
        invokeBooleanOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeInt32(target: ComObjectReference, slot: Int): Int =
        invokeInt32Out(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeUInt32(target: ComObjectReference, slot: Int): UInt =
        invokeUInt32Out(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeDouble(target: ComObjectReference, slot: Int): Double =
        invokeDoubleOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeObject(target: ComObjectReference, slot: Int): IUnknownReference =
        invokeObjectOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )

    fun invokeUnitWithStringArg(target: ComObjectReference, slot: Int, value: String) {
        HString.create(value).use { hString ->
            WindowsRuntimePlatform.checkSucceeded(
                target.invokeAbi(
                    slot = slot,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    hString.handle,
                ),
            )
        }
    }

    fun invokeStringWithStringArg(target: ComObjectReference, slot: Int, value: String): HString =
        withHString(value) { handle ->
            invokeHStringOut(
                target = target,
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                handle,
            )
        }

    fun invokeBooleanWithStringArg(target: ComObjectReference, slot: Int, value: String): Boolean =
        withHString(value) { handle ->
            invokeBooleanOut(
                target = target,
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                handle,
            )
        }

    fun invokeDoubleWithStringArg(target: ComObjectReference, slot: Int, value: String): Double =
        withHString(value) { handle ->
            invokeDoubleOut(
                target = target,
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                handle,
            )
        }

    fun invokeObjectWithStringArg(target: ComObjectReference, slot: Int, value: String): IUnknownReference =
        withHString(value) { handle ->
            invokeObjectOut(
                target = target,
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                handle,
            )
        }

    fun invokeUnitWithUInt32Arg(target: ComObjectReference, slot: Int, value: UInt) {
        WindowsRuntimePlatform.checkSucceeded(
            target.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                ),
                value.toInt(),
            ),
        )
    }

    fun invokeStringWithUInt32Arg(target: ComObjectReference, slot: Int, value: UInt): HString =
        invokeHStringOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            value.toInt(),
        )

    fun invokeBooleanWithUInt32Arg(target: ComObjectReference, slot: Int, value: UInt): Boolean =
        invokeBooleanOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            value.toInt(),
        )

    fun invokeDoubleWithUInt32Arg(target: ComObjectReference, slot: Int, value: UInt): Double =
        invokeDoubleOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            value.toInt(),
        )

    fun invokeObjectWithUInt32Arg(target: ComObjectReference, slot: Int, value: UInt): IUnknownReference =
        invokeObjectOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            value.toInt(),
        )

    fun invokeUnitWithBooleanArg(target: ComObjectReference, slot: Int, value: Boolean) {
        WindowsRuntimePlatform.checkSucceeded(
            target.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_BYTE,
                ),
                if (value) 1.toByte() else 0.toByte(),
            ),
        )
    }

    fun invokeObjectWithBooleanArg(target: ComObjectReference, slot: Int, value: Boolean): IUnknownReference =
        invokeObjectOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_BYTE,
                ValueLayout.ADDRESS,
            ),
            if (value) 1.toByte() else 0.toByte(),
        )

    fun invokeUnitWithDoubleArg(target: ComObjectReference, slot: Int, value: Double) {
        WindowsRuntimePlatform.checkSucceeded(
            target.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE,
                ),
                value,
            ),
        )
    }

    fun invokeObjectWithDoubleArg(target: ComObjectReference, slot: Int, value: Double): IUnknownReference =
        invokeObjectOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_DOUBLE,
                ValueLayout.ADDRESS,
            ),
            value,
        )

    fun invokeUnitWithObjectArg(target: ComObjectReference, slot: Int, value: ComObjectReference) {
        WindowsRuntimePlatform.checkSucceeded(
            target.invokeAbi(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                value.pointer,
            ),
        )
    }

    fun invokeBooleanWithObjectArg(target: ComObjectReference, slot: Int, value: ComObjectReference): Boolean =
        invokeBooleanOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            value.pointer,
        )

    fun invokeObjectWithObjectArg(target: ComObjectReference, slot: Int, value: ComObjectReference): IUnknownReference =
        invokeObjectOut(
            target = target,
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            value.pointer,
        )

    private inline fun <T> withHString(value: String, block: (Any) -> T): T =
        HString.create(value).use { hString -> block(hString.handle) }

    private fun invokeObjectOut(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): IUnknownReference =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }

    private fun invokeHStringOut(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): HString =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0), owner = true)
        }

    private fun invokeBooleanOut(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Boolean =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }

    private fun invokeInt32Out(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Int =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            resultOut.get(ValueLayout.JAVA_INT, 0)
        }

    private fun invokeUInt32Out(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): UInt =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            resultOut.get(ValueLayout.JAVA_INT, 0).toUInt()
        }

    private fun invokeDoubleOut(
        target: ComObjectReference,
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Double =
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_DOUBLE)
            val hr = target.invokeAbi(slot, descriptor, *args, resultOut)
            WindowsRuntimePlatform.checkSucceeded(hr)
            resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
        }
}
