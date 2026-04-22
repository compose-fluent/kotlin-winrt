package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual class NativePointer internal constructor(
    internal val segment: MemorySegment,
)

actual class NativeScope internal constructor(
    internal val arena: Arena,
    private val onClose: () -> Unit,
) : AutoCloseable {
    actual override fun close() {
        onClose()
    }
}

actual class NativeCallbackHandle internal constructor(
    actual val pointer: NativePointer,
    private val onClose: () -> Unit,
) : AutoCloseable {
    actual override fun close() {
        onClose()
    }
}

@OptIn(ExperimentalAtomicApi::class)
actual object NativeInterop {
    private val linker = Linker.nativeLinker()
    private val lookup = MethodHandles.lookup()
    private val sharedArena = Arena.global()
    private val callbacks = ConcurrentCacheMap<Long, RegisteredCallback>()
    private val nextCallbackId = AtomicLong(1)
    private val char16Layout = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)

    actual val nullPointer: NativePointer
        get() = NativePointer(MemorySegment.NULL)

    actual val hStringHeaderSizeBytes: Long
        get() = 24L

    actual fun confinedScope(): NativeScope =
        Arena.ofConfined().let { arena ->
            NativeScope(arena = arena, onClose = arena::close)
        }

    actual fun sharedScope(): NativeScope = NativeScope(arena = Arena.global(), onClose = {})

    actual fun isNull(pointer: NativePointer): Boolean = pointer.segment == MemorySegment.NULL

    actual fun samePointer(first: NativePointer, second: NativePointer): Boolean =
        first.segment == second.segment

    actual fun allocatePointerSlot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.ADDRESS).asNativePointer()

    actual fun allocateInt8Slot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.JAVA_BYTE).asNativePointer()

    actual fun allocateInt32Slot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.JAVA_INT).asNativePointer()

    actual fun allocateInt64Slot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.JAVA_LONG).asNativePointer()

    actual fun allocateDoubleSlot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.JAVA_DOUBLE).asNativePointer()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer =
        scope.arena.allocate(sizeBytes).asNativePointer()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): NativePointer =
        scope.arena.allocate(sizeBytes, alignmentBytes).asNativePointer()

    actual fun allocatePointerArray(scope: NativeScope, size: Int): NativePointer =
        scope.arena.allocate(ValueLayout.ADDRESS, size.toLong()).asNativePointer()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): NativePointer =
        if (nulTerminated) {
            scope.arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE).asNativePointer()
        } else {
            scope.arena.allocateFrom(ValueLayout.JAVA_CHAR, *value.toCharArray()).asNativePointer()
        }

    actual fun slice(pointer: NativePointer, offsetBytes: Long, sizeBytes: Long): NativePointer =
        pointer.segment.asSlice(offsetBytes, sizeBytes).asNativePointer()

    actual fun readPointer(slot: NativePointer): NativePointer =
        slot.segment.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0).asNativePointer()

    actual fun readPointerAt(array: NativePointer, index: Int): NativePointer =
        array.segment.getAtIndex(ValueLayout.ADDRESS, index.toLong()).asNativePointer()

    actual fun readInt8(slot: NativePointer): Byte =
        slot.segment.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).get(ValueLayout.JAVA_BYTE, 0)

    actual fun readInt16(slot: NativePointer): Short =
        slot.segment.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).get(ValueLayout.JAVA_SHORT, 0)

    actual fun readInt32(slot: NativePointer): Int =
        slot.segment.reinterpret(ValueLayout.JAVA_INT.byteSize()).get(ValueLayout.JAVA_INT, 0)

    actual fun readInt64(slot: NativePointer): Long =
        slot.segment.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0)

    actual fun readDouble(slot: NativePointer): Double =
        slot.segment.reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).get(ValueLayout.JAVA_DOUBLE, 0)

    actual fun readFloat(slot: NativePointer): Float =
        slot.segment.reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).get(ValueLayout.JAVA_FLOAT, 0)

    actual fun readChar16(slot: NativePointer): Char =
        slot.segment.reinterpret(char16Layout.byteSize()).get(char16Layout, 0)

    actual fun readUtf16(pointer: NativePointer, length: Int): String {
        if (length == 0) {
            return ""
        }
        val sized = pointer.segment.reinterpret(length.toLong() * ValueLayout.JAVA_CHAR.byteSize())
        val bytes = sized.toArray(ValueLayout.JAVA_BYTE)
        return String(bytes, StandardCharsets.UTF_16LE)
    }

    actual fun readGuid(pointer: NativePointer): Guid {
        val bytes = pointer.segment.reinterpret(Guid.BYTE_SIZE.toLong()).toArray(ValueLayout.JAVA_BYTE)
        return Guid.fromLittleEndianBytes(bytes)
    }

    actual fun writePointer(slot: NativePointer, value: NativePointer) {
        slot.segment.reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, value.segment)
    }

    actual fun writePointer(slot: NativePointer, offsetBytes: Long, value: NativePointer) {
        slot.segment.reinterpret(offsetBytes + ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, offsetBytes, value.segment)
    }

    actual fun writeInt8(slot: NativePointer, value: Byte) {
        slot.segment.reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, value)
    }

    actual fun writeInt16(slot: NativePointer, value: Short) {
        slot.segment.reinterpret(ValueLayout.JAVA_SHORT.byteSize()).set(ValueLayout.JAVA_SHORT, 0, value)
    }

    actual fun writeInt32(slot: NativePointer, value: Int) {
        slot.segment.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value)
    }

    actual fun writeInt32(slot: NativePointer, offsetBytes: Long, value: Int) {
        slot.segment.reinterpret(offsetBytes + ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, offsetBytes, value)
    }

    actual fun writeInt64(slot: NativePointer, value: Long) {
        slot.segment.reinterpret(ValueLayout.JAVA_LONG.byteSize()).set(ValueLayout.JAVA_LONG, 0, value)
    }

    actual fun writeDouble(slot: NativePointer, value: Double) {
        slot.segment.reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).set(ValueLayout.JAVA_DOUBLE, 0, value)
    }

    actual fun writeFloat(slot: NativePointer, value: Float) {
        slot.segment.reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).set(ValueLayout.JAVA_FLOAT, 0, value)
    }

    actual fun writeChar16(slot: NativePointer, value: Char) {
        slot.segment.reinterpret(char16Layout.byteSize()).set(char16Layout, 0, value)
    }

    actual fun writeGuid(pointer: NativePointer, value: Guid) {
        val bytes = value.toLittleEndianBytes()
        pointer.segment.reinterpret(bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes))
    }

    actual fun writeGuid(pointer: NativePointer, offsetBytes: Long, value: Guid) {
        writeGuid(pointer.segment.asSlice(offsetBytes, Guid.BYTE_SIZE.toLong()).asNativePointer(), value)
    }

    actual fun writePointerAt(array: NativePointer, index: Int, value: NativePointer) {
        array.segment.setAtIndex(ValueLayout.ADDRESS, index.toLong(), value.segment)
    }

    actual fun pointerKey(pointer: NativePointer): Long = pointer.segment.address()

    actual fun invokeVtableInt32(
        instance: NativePointer,
        slot: Int,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int {
        val method = linker.downcallHandle(
            vtableEntry(instance.segment, slot),
            descriptor.asJavaFunctionDescriptor(),
        )
        return method.invokeWithArguments(
            listOf(instance.segment) + convertArguments(descriptor.argumentLayouts.drop(1), args.asList()),
        ) as Int
    }

    actual fun invokeFunctionInt32(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int {
        val method = linker.downcallHandle(
            function.segment,
            descriptor.asJavaFunctionDescriptor(),
        )
        return method.invokeWithArguments(
            convertArguments(descriptor.argumentLayouts, args.asList()),
        ) as Int
    }

    actual fun invokeFunctionVoid(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ) {
        val method = linker.downcallHandle(
            function.segment,
            descriptor.asJavaFunctionDescriptor(),
        )
        method.invokeWithArguments(
            convertArguments(descriptor.argumentLayouts, args.asList()),
        )
    }

    actual fun createCallback(
        descriptor: NativeFunctionDescriptor,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle {
        require(descriptor.returnLayout == NativeValueLayout.JAVA_INT) {
            "Only INT32-return callbacks are currently supported."
        }
        val callbackId = nextCallbackId()
        callbacks[callbackId] = RegisteredCallback(descriptor, callback)
        val baseHandle = lookup.findStatic(
            NativeInterop::class.java,
            "invokeCallbackBridge",
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Array<Any?>::class.java,
            ),
        )
        val boundHandle = MethodHandles.insertArguments(baseHandle, 0, callbackId)
        val collectedHandle = boundHandle.asCollector(Array<Any?>::class.java, descriptor.argumentLayouts.size)
        val exactHandle = collectedHandle.asType(
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                descriptor.argumentLayouts.map(::carrierClass),
            ),
        )
        val stub = linker.upcallStub(exactHandle, descriptor.asJavaFunctionDescriptor(), sharedArena)
        return NativeCallbackHandle(
            pointer = stub.asNativePointer(),
            onClose = { callbacks.remove(callbackId) },
        )
    }

    private fun nextCallbackId(): Long {
        while (true) {
            val current = nextCallbackId.load()
            val updated = current + 1
            if (nextCallbackId.compareAndSet(current, updated)) {
                return current
            }
        }
    }

    @JvmStatic
    private fun invokeCallbackBridge(
        callbackId: Long,
        rawArguments: Array<Any?>,
    ): Int {
        val registered = callbacks[callbackId] ?: return KnownHResults.E_POINTER.value
        val converted = registered.descriptor.argumentLayouts.zip(rawArguments.asList()).map { (layout, value) ->
            fromJvmCarrier(layout, value)
        }
        return registered.callback(converted)
    }

    private data class RegisteredCallback(
        val descriptor: NativeFunctionDescriptor,
        val callback: (List<Any?>) -> Int,
    )

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation {
        val arena = Arena.ofShared()
        val pointer = arena.allocate(sizeBytes, alignmentBytes).asNativePointer()
        return OwnedNativeAllocation(pointer = pointer, onClose = arena::close)
    }

    actual fun zeroBytes(pointer: NativePointer, sizeBytes: Long) {
        pointer.segment.reinterpret(sizeBytes).fill(0)
    }
}

internal fun NativePointer.asMemorySegment(): MemorySegment = segment

internal const val IUNKNOWN_VFTBL_SIZE_BYTES: Long = 24

internal fun vtableEntry(pointer: MemorySegment, slot: Int): MemorySegment {
    val objectMemory = pointer.reinterpret(ValueLayout.ADDRESS.byteSize())
    val vtable = objectMemory.get(ValueLayout.ADDRESS, 0)
    val requiredBytes = maxOf(IUNKNOWN_VFTBL_SIZE_BYTES, (slot + 1L) * ValueLayout.ADDRESS.byteSize())
    return vtable.reinterpret(requiredBytes).getAtIndex(ValueLayout.ADDRESS, slot.toLong())
}

internal fun MemorySegment.asNativePointer(): NativePointer = NativePointer(this)

private fun NativeFunctionDescriptor.asJavaFunctionDescriptor(): FunctionDescriptor =
    if (returnLayout == null) {
        FunctionDescriptor.ofVoid(*argumentLayouts.map(::toJavaLayout).toTypedArray())
    } else {
        FunctionDescriptor.of(
            toJavaLayout(returnLayout),
            *argumentLayouts.map(::toJavaLayout).toTypedArray(),
        )
    }

private fun toJavaLayout(layout: NativeValueLayout) =
    when (layout) {
        NativeValueLayout.ADDRESS -> ValueLayout.ADDRESS
        NativeValueLayout.JAVA_BYTE -> ValueLayout.JAVA_BYTE
        NativeValueLayout.JAVA_INT -> ValueLayout.JAVA_INT
        NativeValueLayout.JAVA_LONG -> ValueLayout.JAVA_LONG
        NativeValueLayout.JAVA_DOUBLE -> ValueLayout.JAVA_DOUBLE
    }

private fun carrierClass(layout: NativeValueLayout): Class<*> =
    when (layout) {
        NativeValueLayout.ADDRESS -> MemorySegment::class.java
        NativeValueLayout.JAVA_BYTE -> Byte::class.javaPrimitiveType!!
        NativeValueLayout.JAVA_INT -> Int::class.javaPrimitiveType!!
        NativeValueLayout.JAVA_LONG -> Long::class.javaPrimitiveType!!
        NativeValueLayout.JAVA_DOUBLE -> Double::class.javaPrimitiveType!!
    }

private fun convertArguments(
    layouts: List<NativeValueLayout>,
    args: List<Any?>,
): List<Any?> {
    require(layouts.size == args.size) {
        "Argument count ${args.size} must match descriptor arity ${layouts.size}."
    }
    return layouts.zip(args).map { (layout, value) ->
        toJvmCarrier(layout, value)
    }
}

private fun toJvmCarrier(
    layout: NativeValueLayout,
    value: Any?,
): Any? =
    when (layout) {
        NativeValueLayout.ADDRESS ->
            when (value) {
                null -> MemorySegment.NULL
                is NativePointer -> value.segment
                is MemorySegment -> value
                else -> error("Expected NativePointer-compatible value, got '${value::class.qualifiedName}'.")
            }

        NativeValueLayout.JAVA_BYTE ->
            when (value) {
                is Byte -> value
                is Int -> value.toByte()
                else -> error("Expected byte-compatible value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
            }

        NativeValueLayout.JAVA_INT ->
            when (value) {
                is Int -> value
                is UInt -> value.toInt()
                else -> error("Expected int-compatible value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
            }

        NativeValueLayout.JAVA_LONG ->
            when (value) {
                is Long -> value
                is ULong -> value.toLong()
                else -> error("Expected long-compatible value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
            }

        NativeValueLayout.JAVA_DOUBLE ->
            when (value) {
                is Double -> value
                is Float -> value.toDouble()
                else -> error("Expected double-compatible value, got '${value?.let { it::class.qualifiedName } ?: "null"}'.")
            }
    }

private fun fromJvmCarrier(
    layout: NativeValueLayout,
    value: Any?,
): Any? =
    when (layout) {
        NativeValueLayout.ADDRESS -> (value as MemorySegment).reinterpret(Long.MAX_VALUE).asNativePointer()
        NativeValueLayout.JAVA_BYTE,
        NativeValueLayout.JAVA_INT,
        NativeValueLayout.JAVA_LONG,
        NativeValueLayout.JAVA_DOUBLE,
        -> value
    }
