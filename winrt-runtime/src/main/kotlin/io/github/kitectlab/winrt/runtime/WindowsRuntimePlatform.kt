package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

internal object WindowsRuntimePlatform {
    private const val coinitApartmentThreaded = 0x2
    private const val coinitMultithreaded = 0x0
    private const val roInitSingleThreaded = 0
    private const val roInitMultithreaded = 1

    private val linker: Linker by lazy { Linker.nativeLinker() }
    private val kernel32Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("kernel32", Arena.global()) }
    private val combaseLookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("combase", Arena.global()) }
    private val ole32Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("ole32", Arena.global()) }

    private val coInitializeExHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoInitializeEx",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )
    }

    private val coUninitializeHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoUninitialize",
            FunctionDescriptor.ofVoid(),
        )
    }

    private val roInitializeHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "RoInitialize",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
            ),
        )
    }

    private val roUninitializeHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "RoUninitialize",
            FunctionDescriptor.ofVoid(),
        )
    }

    private val roGetActivationFactoryHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "RoGetActivationFactory",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val windowsCreateStringHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "WindowsCreateString",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val windowsCreateStringReferenceHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "WindowsCreateStringReference",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val windowsDeleteStringHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "WindowsDeleteString",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val windowsGetStringRawBufferHandle: MethodHandle by lazy {
        downcall(
            combaseLookup,
            "WindowsGetStringRawBuffer",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val loadLibraryExWHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "LoadLibraryExW",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )
    }

    private val getProcAddressHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "GetProcAddress",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    fun roGetActivationFactory(
        runtimeClassId: HString,
        interfaceId: Guid,
    ): ActivationResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            interfaceId.writeTo(iidMemory)
            val factoryOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = roGetActivationFactoryHandle.invokeWithArguments(
                runtimeClassId.handle,
                iidMemory,
                factoryOut,
            ) as Int
            val pointer = factoryOut.get(ValueLayout.ADDRESS, 0)
            return ActivationResult(HResult(hr), pointer)
        }
    }

    fun coInitializeEx(apartmentType: ApartmentType): HResult {
        ensureWindows()
        val flags = when (apartmentType) {
            ApartmentType.SingleThreaded -> coinitApartmentThreaded
            ApartmentType.MultiThreaded -> coinitMultithreaded
        }
        return HResult(coInitializeExHandle.invokeWithArguments(MemorySegment.NULL, flags) as Int)
    }

    fun coUninitialize() {
        ensureWindows()
        coUninitializeHandle.invokeWithArguments()
    }

    fun roInitialize(apartmentType: ApartmentType): HResult {
        ensureWindows()
        val initType = when (apartmentType) {
            ApartmentType.SingleThreaded -> roInitSingleThreaded
            ApartmentType.MultiThreaded -> roInitMultithreaded
        }
        return HResult(roInitializeHandle.invokeWithArguments(initType) as Int)
    }

    fun roUninitialize() {
        ensureWindows()
        roUninitializeHandle.invokeWithArguments()
    }

    fun windowsCreateString(
        utf16Chars: MemorySegment,
        length: Int,
        outHandle: MemorySegment,
    ): Int {
        ensureWindows()
        return windowsCreateStringHandle.invokeWithArguments(
            utf16Chars,
            length,
            outHandle,
        ) as Int
    }

    fun windowsCreateStringReference(
        utf16Chars: MemorySegment,
        length: Int,
        header: MemorySegment,
        outHandle: MemorySegment,
    ): Int {
        ensureWindows()
        return windowsCreateStringReferenceHandle.invokeWithArguments(
            utf16Chars,
            length,
            header,
            outHandle,
        ) as Int
    }

    fun windowsDeleteString(handle: MemorySegment) {
        ensureWindows()
        windowsDeleteStringHandle.invokeWithArguments(handle)
    }

    fun windowsGetStringRawBuffer(handle: MemorySegment, lengthOut: MemorySegment): MemorySegment {
        ensureWindows()
        return windowsGetStringRawBufferHandle.invokeWithArguments(handle, lengthOut) as MemorySegment
    }

    fun loadLibraryExW(absolutePath: String, flags: Int): MemorySegment {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val path = arena.allocateFrom(absolutePath, StandardCharsets.UTF_16LE)
            return loadLibraryExWHandle.invokeWithArguments(
                path,
                MemorySegment.NULL,
                flags,
            ) as MemorySegment
        }
    }

    fun getProcAddress(moduleHandle: MemorySegment, procedureName: String): MemorySegment {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val name = arena.allocateFrom("$procedureName\u0000", StandardCharsets.UTF_8)
            return getProcAddressHandle.invokeWithArguments(
                moduleHandle,
                name,
            ) as MemorySegment
        }
    }

    fun checkSucceeded(result: Int) {
        val hResult = HResult(result)
        if (hResult.isFailure) {
            throw WinRtRuntimeException("WinRT call failed with $hResult", hResult)
        }
    }

    private fun downcall(
        lookup: SymbolLookup,
        symbolName: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle {
        ensureWindows()
        val symbol = lookup.find(symbolName).orElseThrow {
            IllegalStateException("Missing Win32 symbol: $symbolName")
        }
        return linker.downcallHandle(symbol, descriptor)
    }

    private fun ensureWindows() {
        check(PlatformRuntime.isWindows) {
            "Windows runtime interop is only supported on Windows hosts."
        }
    }

    fun resolveModulePath(fileName: String): String =
        java.nio.file.Path.of(System.getProperty("user.dir"), fileName).toString()
}
