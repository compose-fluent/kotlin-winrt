package dev.winrt.kom

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

internal object Jdk22Foreign {
    val linker: Linker = Linker.nativeLinker()
    val arena: Arena = Arena.ofAuto()
    private val addressLayout = ValueLayout.ADDRESS
    private val intLayout = ValueLayout.JAVA_INT
    private val longLayout = ValueLayout.JAVA_LONG
    private val downcallHandleCache = ConcurrentHashMap<String, MethodHandle>()

    val windowsLookups: List<SymbolLookup> by lazy {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            emptyList()
        } else {
            sequenceOf("ole32", "combase")
                .mapNotNull { library -> runCatching { SymbolLookup.libraryLookup(library, arena) }.getOrNull() }
                .toList()
        }
    }

    fun pointerOf(value: ComPtr): MemorySegment = MemorySegment.ofAddress(value.value.rawValue)

    fun guidSegment(guid: Guid, arena: Arena): MemorySegment {
        val segment = arena.allocate(16)
        segment.set(intLayout, 0, guid.data1)
        segment.set(ValueLayout.JAVA_SHORT, 4, guid.data2)
        segment.set(ValueLayout.JAVA_SHORT, 6, guid.data3)
        guid.data4.forEachIndexed { index, byte ->
            segment.set(ValueLayout.JAVA_BYTE, 8L + index, byte)
        }
        return segment
    }

    fun dereferencePointer(pointer: ComPtr): MemorySegment {
        return pointerOf(pointer).reinterpret(addressLayout.byteSize())
    }

    fun vtableEntry(instance: ComPtr, index: Int): MemorySegment {
        val objectPointer = dereferencePointer(instance)
        val vtablePointer = objectPointer.get(addressLayout, 0)
        return vtablePointer
            .reinterpret((index + 1L) * addressLayout.byteSize())
            .getAtIndex(addressLayout, index.toLong())
    }

    fun downcall(symbolName: String, descriptor: FunctionDescriptor): MethodHandle {
        val symbol = windowsLookups
            .asSequence()
            .mapNotNull { lookup -> lookup.find(symbolName).orElse(null) }
            .firstOrNull()

        requireNotNull(symbol) {
            "Windows symbol not found: $symbolName"
        }
        return linker.downcallHandle(symbol, descriptor)
    }

    fun downcallHandle(descriptor: FunctionDescriptor): MethodHandle {
        val cacheKey = descriptor.toString()
        return downcallHandleCache.getOrPut(cacheKey) {
            linker.downcallHandle(descriptor)
        }
    }

    fun unitMethodWithTwoInputsHandle(
        firstLayout: MemoryLayout,
        secondLayout: MemoryLayout,
    ): MethodHandle = downcallHandle(
        FunctionDescriptor.of(intLayout, addressLayout, firstLayout, secondLayout),
    )

    fun methodWithTwoInputsHandle(
        firstLayout: MemoryLayout,
        secondLayout: MemoryLayout,
    ): MethodHandle = downcallHandle(
        FunctionDescriptor.of(intLayout, addressLayout, firstLayout, secondLayout, addressLayout),
    )

    fun unitMethodWithArgumentsHandle(arguments: List<MemoryLayout>): MethodHandle = downcallHandle(
        FunctionDescriptor.of(
            intLayout,
            addressLayout,
            *arguments.toTypedArray(),
        ),
    )

    fun methodWithArgumentsHandle(arguments: List<MemoryLayout>): MethodHandle = downcallHandle(
        FunctionDescriptor.of(
            intLayout,
            addressLayout,
            *arguments.toTypedArray(),
            addressLayout,
        ),
    )

    fun methodWithArgumentsAndTwoOutHandles(arguments: List<MemoryLayout>): MethodHandle = downcallHandle(
        FunctionDescriptor.of(
            intLayout,
            addressLayout,
            *arguments.toTypedArray(),
            addressLayout,
            addressLayout,
        ),
    )

    fun composableMethodWithArgumentsHandle(arguments: List<MemoryLayout>): MethodHandle = downcallHandle(
        FunctionDescriptor.of(
            intLayout,
            addressLayout,
            *arguments.toTypedArray(),
            addressLayout,
            addressLayout,
        ),
    )

    fun structLayout(layout: ComStructLayout): MemoryLayout =
        MemoryLayout.structLayout(*layout.fields.map(::fieldLayout).toTypedArray())

    private fun fieldLayout(fieldKind: ComStructFieldKind): MemoryLayout =
        when (fieldKind) {
            ComStructFieldKind.BOOLEAN,
            ComStructFieldKind.INT8,
            ComStructFieldKind.UINT8 -> ValueLayout.JAVA_BYTE
            ComStructFieldKind.INT16,
            ComStructFieldKind.UINT16 -> ValueLayout.JAVA_SHORT
            ComStructFieldKind.CHAR16 -> ValueLayout.JAVA_CHAR
            ComStructFieldKind.INT32,
            ComStructFieldKind.UINT32 -> ValueLayout.JAVA_INT
            ComStructFieldKind.INT64,
            ComStructFieldKind.UINT64 -> ValueLayout.JAVA_LONG
            ComStructFieldKind.FLOAT32 -> ValueLayout.JAVA_FLOAT
            ComStructFieldKind.FLOAT64 -> ValueLayout.JAVA_DOUBLE
            ComStructFieldKind.HSTRING,
            ComStructFieldKind.OBJECT,
            -> ValueLayout.ADDRESS
            ComStructFieldKind.GUID -> MemoryLayout.structLayout(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
                ValueLayout.JAVA_BYTE,
            )
        }

    val queryInterfaceHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val addRefHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout),
        )
    }

    val releaseHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout),
        )
    }

    val unitMethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout),
        )
    }

    val coInitializeExHandle: MethodHandle by lazy {
        downcall(
            "CoInitializeEx",
            FunctionDescriptor.of(intLayout, addressLayout, intLayout),
        )
    }

    val coUninitializeHandle: MethodHandle by lazy {
        downcall(
            "CoUninitialize",
            FunctionDescriptor.ofVoid(),
        )
    }

    val roInitializeHandle: MethodHandle by lazy {
        downcall(
            "RoInitialize",
            FunctionDescriptor.of(intLayout, intLayout),
        )
    }

    val roUninitializeHandle: MethodHandle by lazy {
        downcall(
            "RoUninitialize",
            FunctionDescriptor.ofVoid(),
        )
    }

    val windowsCreateStringHandle: MethodHandle by lazy {
        downcall(
            "WindowsCreateString",
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val windowsDeleteStringHandle: MethodHandle by lazy {
        downcall(
            "WindowsDeleteString",
            FunctionDescriptor.of(intLayout, addressLayout),
        )
    }

    val windowsGetStringRawBufferHandle: MethodHandle by lazy {
        downcall(
            "WindowsGetStringRawBuffer",
            FunctionDescriptor.of(addressLayout, addressLayout, addressLayout),
        )
    }

    val roGetActivationFactoryHandle: MethodHandle by lazy {
        downcall(
            "RoGetActivationFactory",
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val activateInstanceHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val hstringMethodWithInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val objectMethodWithInputHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val int64MethodWithObjectHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val int64MethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val int64MethodWithStringHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val int64MethodWithInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val objectMethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, longLayout, addressLayout),
        )
    }

    val objectMethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val twoObjectMethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val hstringSetterHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val objectSetterHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val float32SetterHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, ValueLayout.JAVA_FLOAT),
        )
    }

    val float64SetterHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, ValueLayout.JAVA_DOUBLE),
        )
    }

    val int32MethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val int32MethodWithStringHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val int32MethodWithInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val int32MethodWithObjectHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val int32MethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, longLayout, addressLayout),
        )
    }

    val unitMethodWithInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout),
        )
    }

    val unitMethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, ValueLayout.JAVA_LONG),
        )
    }

    val float64MethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val float32MethodHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    val float32MethodWithInputHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val float32MethodWithUInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val float32MethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, longLayout, addressLayout),
        )
    }

    val float64MethodWithInputHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val float64MethodWithUInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val float64MethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, longLayout, addressLayout),
        )
    }

    val guidMethodWithInputHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout, addressLayout),
        )
    }

    val guidMethodWithInt32Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, intLayout, addressLayout),
        )
    }

    val guidMethodWithInt64Handle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, longLayout, addressLayout),
        )
    }

    val guidGetterHandle: MethodHandle by lazy {
        downcallHandle(
            FunctionDescriptor.of(intLayout, addressLayout, addressLayout),
        )
    }

    fun guidFromSegment(segment: MemorySegment): Guid {
        val data4 = ByteArray(8) { index ->
            segment.get(ValueLayout.JAVA_BYTE, 8L + index)
        }
        return Guid(
            data1 = segment.get(intLayout, 0L),
            data2 = segment.get(ValueLayout.JAVA_SHORT, 4L),
            data3 = segment.get(ValueLayout.JAVA_SHORT, 6L),
            data4 = data4,
        )
    }

    fun addressResult(segment: MemorySegment): ComPtr = ComPtr(AbiIntPtr(segment.address()))

    fun longToUInt(value: Int): UInt = value.toUInt()
}
