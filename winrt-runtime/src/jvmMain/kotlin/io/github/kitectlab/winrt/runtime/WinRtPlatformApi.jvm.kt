package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

/**
 * JVM actual for the WinRT platform boundary.
 *
 * This owner keeps FFM carriers (`MemorySegment`, `Arena`, `SymbolLookup`, `MethodHandle`) on the hot path
 * instead of routing them through wrapper types.
 */
actual object WinRtPlatformApi {
    private const val coinitApartmentThreaded = 0x2
    private const val coinitMultithreaded = 0x0
    private const val roInitSingleThreaded = 0
    private const val roInitMultithreaded = 1
    private const val loadLibrarySearchSystem32 = 0x00000800

    private val linker: Linker by lazy { Linker.nativeLinker() }
    private val kernel32Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("kernel32", Arena.global()) }
    private val combaseLookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("combase", Arena.global()) }
    private val ole32Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("ole32", Arena.global()) }
    private val oleaut32Lookup: SymbolLookup by lazy { SymbolLookup.libraryLookup("oleaut32", Arena.global()) }

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

    private val roGetAgileReferenceHandle: MethodHandle? by lazy {
        optionalDowncall(
            combaseLookup,
            "RoGetAgileReference",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
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

    private val coCreateInstanceHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoCreateInstance",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val coIncrementMtaUsageHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoIncrementMTAUsage",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val coDecrementMtaUsageHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoDecrementMTAUsage",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val coGetContextTokenHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoGetContextToken",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val coGetObjectContextHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoGetObjectContext",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val setErrorInfoHandle: MethodHandle by lazy {
        downcall(
            oleaut32Lookup,
            "SetErrorInfo",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val sysAllocStringLenHandle: MethodHandle by lazy {
        downcall(
            oleaut32Lookup,
            "SysAllocStringLen",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )
    }

    private val sysFreeStringHandle: MethodHandle by lazy {
        downcall(
            oleaut32Lookup,
            "SysFreeString",
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val sysStringLenHandle: MethodHandle by lazy {
        downcall(
            oleaut32Lookup,
            "SysStringLen",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val coCreateFreeThreadedMarshalerHandle: MethodHandle by lazy {
        downcall(
            ole32Lookup,
            "CoCreateFreeThreadedMarshaler",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
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

    private val formatMessageWHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "FormatMessageW",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val localFreeHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "LocalFree",
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val freeLibraryHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "FreeLibrary",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val getLastErrorHandle: MethodHandle by lazy {
        downcall(
            kernel32Lookup,
            "GetLastError",
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
            ),
        )
    }

    private val winRtErrorModuleHandle: MemorySegment? by lazy {
        if (!PlatformRuntime.isWindows) {
            null
        } else {
            sequenceOf(
                "api-ms-win-core-winrt-error-l1-1-1.dll",
                "api-ms-win-core-winrt-error-l1-1-0.dll",
            ).firstNotNullOfOrNull { moduleName ->
                val handle = tryLoadLibraryExW(moduleName, loadLibrarySearchSystem32)
                handle.takeIf { it != MemorySegment.NULL }
            }
        }
    }

    private val getRestrictedErrorInfoHandle: MethodHandle? by lazy {
        optionalDowncall(
            moduleHandle = winRtErrorModuleHandle,
            symbolName = "GetRestrictedErrorInfo",
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val setRestrictedErrorInfoHandle: MethodHandle? by lazy {
        optionalDowncall(
            moduleHandle = winRtErrorModuleHandle,
            symbolName = "SetRestrictedErrorInfo",
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    private val roReportUnhandledErrorHandle: MethodHandle? by lazy {
        optionalDowncall(
            moduleHandle = winRtErrorModuleHandle,
            symbolName = "RoReportUnhandledError",
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
    }

    actual fun roGetActivationFactoryRaw(
        runtimeClassId: NativePointer,
        interfaceId: Guid,
    ): NativePointerResult =
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(interfaceId, iidMemory)
            val factoryOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = roGetActivationFactoryHandle.invokeWithArguments(
                runtimeClassId.asMemorySegment(),
                iidMemory,
                factoryOut,
            ) as Int
            NativePointerResult(hr, factoryOut.get(ValueLayout.ADDRESS, 0).asNativePointer())
        }

    actual fun queryInterfaceRaw(
        unknown: NativePointer,
        interfaceId: Guid,
    ): NativePointerResult {
        ensureWindows()
        if (NativeInterop.isNull(unknown)) {
            return NativePointerResult(KnownHResults.E_POINTER.value, NativeInterop.nullPointer)
        }
        return Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(interfaceId, iidMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val queryInterface = linker.downcallHandle(
                vtableEntry(unknown.asMemorySegment(), IUnknownVftblSlots.QueryInterface),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            )
            val hr = queryInterface.invokeWithArguments(
                unknown.asMemorySegment(),
                iidMemory,
                resultOut,
            ) as Int
            NativePointerResult(hr, resultOut.get(ValueLayout.ADDRESS, 0).asNativePointer())
        }
    }

    actual fun addRefRaw(unknown: NativePointer): UInt =
        invokeUnknownRefCountMethod(unknown, IUnknownVftblSlots.AddRef)

    actual fun releaseRaw(unknown: NativePointer): UInt =
        invokeUnknownRefCountMethod(unknown, IUnknownVftblSlots.Release)

    actual fun dllGetActivationFactoryRaw(
        getActivationFactoryProc: NativePointer,
        runtimeClassId: NativePointer,
    ): NativePointerResult {
        ensureWindows()
        if (NativeInterop.isNull(getActivationFactoryProc) || NativeInterop.isNull(runtimeClassId)) {
            return NativePointerResult(KnownHResults.E_POINTER.value, NativeInterop.nullPointer)
        }
        return Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val getActivationFactory = linker.downcallHandle(
                getActivationFactoryProc.asMemorySegment(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            )
            val hr = getActivationFactory.invokeWithArguments(
                runtimeClassId.asMemorySegment(),
                resultOut,
            ) as Int
            NativePointerResult(hr, resultOut.get(ValueLayout.ADDRESS, 0).asNativePointer())
        }
    }

    actual fun coCreateInstanceRaw(
        classId: Guid,
        interfaceId: Guid,
        classContext: Int,
    ): NativePointerResult =
        coCreateInstance(classId, interfaceId, classContext).toNativePointerResult()

    actual fun coInitializeExRaw(apartmentType: ApartmentType): Int =
        coInitializeEx(apartmentType).value

    actual fun coUninitializeRaw() {
        coUninitialize()
    }

    actual fun roInitializeRaw(apartmentType: ApartmentType): Int =
        roInitialize(apartmentType).value

    actual fun roUninitializeRaw() {
        roUninitialize()
    }

    actual fun coIncrementMtaUsageRaw(): NativePointerResult =
        coIncrementMtaUsage().toNativePointerResult()

    actual fun coDecrementMtaUsageRaw(cookie: NativePointer): Int =
        coDecrementMtaUsage(cookie.asMemorySegment()).value

    actual fun roGetAgileReferenceRaw(
        unknown: NativePointer,
        interfaceId: Guid,
    ): NativePointerResult =
        roGetAgileReference(unknown.asMemorySegment(), interfaceId).toNativePointerResult()

    actual fun coGetContextTokenRaw(): NativePointerResult =
        coGetContextToken().toNativePointerResult()

    actual fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult =
        coGetObjectContext(interfaceId).toNativePointerResult()

    actual fun setErrorInfoRaw(errorInfo: NativePointer): Int =
        setErrorInfo(errorInfo.asMemorySegment()).value

    actual fun borrowRestrictedErrorInfoRaw(): NativePointer? =
        borrowRestrictedErrorInfo()?.asNativePointer()

    actual fun reportUnhandledErrorRaw(errorInfo: NativePointer): Int? =
        reportUnhandledError(errorInfo.asMemorySegment())?.value

    actual fun sysAllocStringRaw(value: String?): NativePointer =
        sysAllocString(value).asNativePointer()

    actual fun sysFreeStringRaw(value: NativePointer) {
        sysFreeString(value.asMemorySegment())
    }

    actual fun readAndFreeBstrRaw(value: NativePointer): String =
        readAndFreeBstr(value.asMemorySegment())

    actual fun coCreateFreeThreadedMarshalerRaw(outer: NativePointer): NativePointerResult =
        coCreateFreeThreadedMarshaler(outer.asMemorySegment()).toNativePointerResult()

    actual fun windowsCreateStringRaw(
        utf16Chars: NativePointer,
        length: Int,
        outHandle: NativePointer,
    ): Int =
        windowsCreateString(utf16Chars.asMemorySegment(), length, outHandle.asMemorySegment())

    actual fun windowsCreateStringReferenceRaw(
        utf16Chars: NativePointer,
        length: Int,
        header: NativePointer,
        outHandle: NativePointer,
    ): Int =
        windowsCreateStringReference(
            utf16Chars.asMemorySegment(),
            length,
            header.asMemorySegment(),
            outHandle.asMemorySegment(),
        )

    actual fun windowsDeleteStringRaw(handle: NativePointer) {
        windowsDeleteString(handle.asMemorySegment())
    }

    actual fun windowsGetStringRawBufferRaw(
        handle: NativePointer,
        lengthOut: NativePointer,
    ): NativePointer =
        windowsGetStringRawBuffer(handle.asMemorySegment(), lengthOut.asMemorySegment()).asNativePointer()

    actual fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer =
        tryLoadLibraryExW(absolutePath, flags).asNativePointer()

    actual fun loadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer =
        loadLibraryExW(absolutePath, flags).asNativePointer()

    actual fun tryGetProcAddressRaw(
        moduleHandle: NativePointer,
        procedureName: String,
    ): NativePointer =
        tryGetProcAddress(moduleHandle.asMemorySegment(), procedureName).asNativePointer()

    actual fun getProcAddressRaw(
        moduleHandle: NativePointer,
        procedureName: String,
    ): NativePointer =
        getProcAddress(moduleHandle.asMemorySegment(), procedureName).asNativePointer()

    actual fun freeLibraryRaw(moduleHandle: NativePointer): Boolean =
        freeLibrary(moduleHandle.asMemorySegment())

    actual fun mddBootstrapInitialize2Raw(
        initializeProc: NativePointer,
        majorMinorVersion: Int,
        versionTag: String,
        minVersion: Long,
    ): Int {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val tag = if (versionTag.isBlank()) {
                MemorySegment.NULL
            } else {
                arena.allocateFrom("$versionTag\u0000", StandardCharsets.UTF_16LE)
            }
            val handle = linker.downcallHandle(
                initializeProc.asMemorySegment(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                ),
            )
            return handle.invokeWithArguments(
                majorMinorVersion,
                tag,
                minVersion,
                0,
            ) as Int
        }
    }

    actual fun mddBootstrapShutdownRaw(shutdownProc: NativePointer) {
        ensureWindows()
        linker.downcallHandle(
            shutdownProc.asMemorySegment(),
            FunctionDescriptor.ofVoid(),
        ).invokeWithArguments()
    }

    actual fun tryFormatMessageRaw(hResultValue: Int): String? =
        tryFormatMessage(HResult(hResultValue))

    actual fun lastErrorAsHResultRaw(): Int =
        lastErrorAsHResult().value

    actual fun checkSucceededRaw(result: Int) {
        checkSucceeded(result)
    }

    actual fun resolveModulePathRaw(fileName: String): String =
        resolveModulePath(fileName)

    internal fun coCreateInstance(
        classId: Guid,
        interfaceId: Guid,
        classContext: Int = 1,
    ): PointerResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val classIdMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(classId, classIdMemory)
            val interfaceIdMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(interfaceId, interfaceIdMemory)
            val instanceOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = coCreateInstanceHandle.invokeWithArguments(
                classIdMemory,
                MemorySegment.NULL,
                classContext,
                interfaceIdMemory,
                instanceOut,
            ) as Int
            return PointerResult(HResult(hr), instanceOut.get(ValueLayout.ADDRESS, 0))
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

    internal fun coIncrementMtaUsage(): PointerResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val cookieOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = coIncrementMtaUsageHandle.invokeWithArguments(cookieOut) as Int
            return PointerResult(HResult(hr), cookieOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun coDecrementMtaUsage(cookie: MemorySegment): HResult {
        ensureWindows()
        return HResult(coDecrementMtaUsageHandle.invokeWithArguments(cookie) as Int)
    }

    internal fun roGetAgileReference(
        unknown: MemorySegment,
        interfaceId: Guid = IID.IUnknown,
    ): PointerResult {
        ensureWindows()
        val handle = roGetAgileReferenceHandle ?: return PointerResult(KnownHResults.E_NOTIMPL, MemorySegment.NULL)
        Arena.ofConfined().use { arena ->
            val interfaceIdMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(interfaceId, interfaceIdMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = handle.invokeWithArguments(
                0,
                interfaceIdMemory,
                unknown,
                resultOut,
            ) as Int
            return PointerResult(HResult(hr), resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    internal fun coGetContextToken(): PointerResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val tokenOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = coGetContextTokenHandle.invokeWithArguments(tokenOut) as Int
            return PointerResult(HResult(hr), tokenOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    internal fun coGetObjectContext(interfaceId: Guid): PointerResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val interfaceIdMemory = arena.allocate(ValueLayout.JAVA_BYTE, 16)
            writeGuidTo(interfaceId, interfaceIdMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = coGetObjectContextHandle.invokeWithArguments(
                interfaceIdMemory,
                resultOut,
            ) as Int
            return PointerResult(HResult(hr), resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun setErrorInfo(errorInfo: MemorySegment): HResult {
        ensureWindows()
        return HResult(setErrorInfoHandle.invokeWithArguments(0, errorInfo) as Int)
    }

    fun borrowRestrictedErrorInfo(): MemorySegment? {
        ensureWindows()
        val handle = getRestrictedErrorInfoHandle ?: return null
        Arena.ofConfined().use { arena ->
            val errorInfoOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = handle.invokeWithArguments(errorInfoOut) as Int
            checkSucceeded(hr)
            val errorInfo = errorInfoOut.get(ValueLayout.ADDRESS, 0)
            if (errorInfo == MemorySegment.NULL) {
                return null
            }
            setRestrictedErrorInfoHandle?.invokeWithArguments(errorInfo)
            return errorInfo
        }
    }

    fun reportUnhandledError(errorInfo: MemorySegment): HResult? {
        ensureWindows()
        val handle = roReportUnhandledErrorHandle ?: return null
        return HResult(handle.invokeWithArguments(errorInfo) as Int)
    }

    fun sysAllocString(value: String?): MemorySegment {
        ensureWindows()
        if (value.isNullOrEmpty()) {
            return sysAllocStringLenHandle.invokeWithArguments(MemorySegment.NULL, 0) as MemorySegment
        }
        Arena.ofConfined().use { arena ->
            val utf16 = arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE)
            return sysAllocStringLenHandle.invokeWithArguments(utf16, value.length) as MemorySegment
        }
    }

    fun sysFreeString(value: MemorySegment) {
        ensureWindows()
        if (value != MemorySegment.NULL) {
            sysFreeStringHandle.invokeWithArguments(value)
        }
    }

    fun readAndFreeBstr(value: MemorySegment): String {
        ensureWindows()
        if (value == MemorySegment.NULL) {
            return ""
        }
        try {
            val charCount = sysStringLenHandle.invokeWithArguments(value) as Int
            return readUtf16Message(value, charCount)
        } finally {
            sysFreeString(value)
        }
    }

    internal fun coCreateFreeThreadedMarshaler(outer: MemorySegment = MemorySegment.NULL): PointerResult {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = coCreateFreeThreadedMarshalerHandle.invokeWithArguments(
                outer,
                resultOut,
            ) as Int
            return PointerResult(HResult(hr), resultOut.get(ValueLayout.ADDRESS, 0))
        }
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

    fun tryLoadLibraryExW(absolutePath: String, flags: Int): MemorySegment {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val path = arena.allocateFrom("$absolutePath\u0000", StandardCharsets.UTF_16LE)
            return loadLibraryExWHandle.invokeWithArguments(
                path,
                MemorySegment.NULL,
                flags,
            ) as MemorySegment
        }
    }

    fun loadLibraryExW(absolutePath: String, flags: Int): MemorySegment {
        val handle = tryLoadLibraryExW(absolutePath, flags)
        if (handle == MemorySegment.NULL) {
            throw WinRtExceptionTranslator.exceptionFor(lastErrorAsHResult(), "LoadLibraryExW($absolutePath)")
        }
        return handle
    }

    fun tryGetProcAddress(moduleHandle: MemorySegment, procedureName: String): MemorySegment {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val name = arena.allocateFrom("$procedureName\u0000", StandardCharsets.UTF_8)
            return getProcAddressHandle.invokeWithArguments(
                moduleHandle,
                name,
            ) as MemorySegment
        }
    }

    fun getProcAddress(moduleHandle: MemorySegment, procedureName: String): MemorySegment {
        val pointer = tryGetProcAddress(moduleHandle, procedureName)
        if (pointer == MemorySegment.NULL) {
            throw WinRtExceptionTranslator.exceptionFor(lastErrorAsHResult(), "GetProcAddress($procedureName)")
        }
        return pointer
    }

    fun freeLibrary(moduleHandle: MemorySegment): Boolean {
        ensureWindows()
        if (moduleHandle == MemorySegment.NULL) {
            return false
        }
        return (freeLibraryHandle.invokeWithArguments(moduleHandle) as Int) != 0
    }

    fun tryFormatMessage(hResult: HResult): String? {
        ensureWindows()
        Arena.ofConfined().use { arena ->
            val messageOut = arena.allocate(ValueLayout.ADDRESS)
            val charCount = formatMessageWHandle.invokeWithArguments(
                0x13FF,
                MemorySegment.NULL,
                hResult.value,
                0,
                messageOut,
                0,
                MemorySegment.NULL,
            ) as Int
            if (charCount <= 0) {
                return null
            }
            val messagePointer = messageOut.get(ValueLayout.ADDRESS, 0)
            if (messagePointer == MemorySegment.NULL) {
                return null
            }
            try {
                return readUtf16Message(messagePointer, charCount)
            } finally {
                localFreeHandle.invokeWithArguments(messagePointer)
            }
        }
    }

    fun lastErrorAsHResult(): HResult {
        ensureWindows()
        val errorCode = getLastErrorHandle.invokeWithArguments() as Int
        return ExceptionHelpers.hResultFromWin32(errorCode)
    }

    fun checkSucceeded(result: Int) {
        val hResult = HResult(result)
        if (hResult.isFailure) {
            throw WinRtExceptionTranslator.exceptionFor(hResult)
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

    private fun optionalDowncall(
        lookup: SymbolLookup,
        symbolName: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle? =
        lookup.find(symbolName)
            .map { linker.downcallHandle(it, descriptor) }
            .orElse(null)

    private fun optionalDowncall(
        moduleHandle: MemorySegment?,
        symbolName: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle? {
        if (moduleHandle == null || moduleHandle == MemorySegment.NULL) {
            return null
        }
        val symbol = tryGetProcAddress(moduleHandle, symbolName)
        if (symbol == MemorySegment.NULL) {
            return null
        }
        return linker.downcallHandle(symbol, descriptor)
    }

    private fun ensureWindows() {
        check(PlatformRuntime.isWindows) {
            "Windows runtime interop is only supported on Windows hosts."
        }
    }

    private fun invokeUnknownRefCountMethod(unknown: NativePointer, slot: Int): UInt {
        ensureWindows()
        if (NativeInterop.isNull(unknown)) {
            return 0u
        }
        val method = linker.downcallHandle(
            vtableEntry(unknown.asMemorySegment(), slot),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        return (method.invokeWithArguments(unknown.asMemorySegment()) as Int).toUInt()
    }

    fun resolveModulePath(fileName: String): String =
        PlatformFileSystem.resolve(
            PlatformFileSystem.systemProperty("user.dir") ?: ".",
            fileName,
        )

    private fun readUtf16Message(pointer: MemorySegment, charCount: Int): String {
        val sized = pointer.reinterpret(charCount.toLong() * ValueLayout.JAVA_CHAR.byteSize())
        val chars = CharArray(charCount)
        var index = 0
        while (index < charCount) {
            chars[index] = sized.get(ValueLayout.JAVA_CHAR, index.toLong() * ValueLayout.JAVA_CHAR.byteSize())
            index += 1
        }
        return String(chars)
    }
}

internal val WindowsRuntimePlatform = WinRtPlatformApi

internal data class PointerResult(
    val hResult: HResult,
    val pointer: MemorySegment,
)

private fun PointerResult.toNativePointerResult(): NativePointerResult =
    NativePointerResult(hResult.value, pointer.asNativePointer())

/** Writes [guid] as 16 little-endian bytes into [destination]. */
private fun writeGuidTo(guid: Guid, destination: MemorySegment) {
    NativeInterop.writeGuid(destination.asNativePointer(), guid)
}
