package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.io.files.Path

private val arena: Arena = Arena.ofAuto()
private val linker by lazy { java.lang.foreign.Linker.nativeLinker() }
private val kernel32: SymbolLookup by lazy { SymbolLookup.libraryLookup("kernel32", Arena.global()) }
private val platformHandles = ConcurrentHashMap<Long, Any>()
private val nextPlatformHandle = AtomicLong(1)
private var bootstrapShutdown: MethodHandle? = null
private const val applicationManifestNameProperty: String = "kotlin.winrt.applicationManifestName"

private val actCtxLayout: MemoryLayout = MemoryLayout.structLayout(
    ValueLayout.JAVA_INT.withName("cbSize"),
    ValueLayout.JAVA_INT.withName("dwFlags"),
    ValueLayout.ADDRESS.withName("lpSource"),
    ValueLayout.JAVA_SHORT.withName("wProcessorArchitecture"),
    ValueLayout.JAVA_SHORT.withName("wLangId"),
    MemoryLayout.paddingLayout(4),
    ValueLayout.ADDRESS.withName("lpAssemblyDirectory"),
    ValueLayout.ADDRESS.withName("lpResourceName"),
    ValueLayout.ADDRESS.withName("lpApplicationName"),
    ValueLayout.ADDRESS.withName("hModule"),
)

internal actual fun platformDiscoverWindowsAppSdkRuntimeAssetsRoot(anchorFileName: String): Path? =
    WinRTRuntimeAssets.discoverRuntimeAssetsRoot(anchorFileName)?.let { Path(it.toString()) }

internal actual fun platformWindowsApplicationManifestPath(root: Path): Path =
    System.getProperty(applicationManifestNameProperty)
        ?.takeIf { it.isNotBlank() }
        ?.let { name -> java.nio.file.Path.of(root.toString()).resolve(name) }
        ?.takeIf { path -> Files.isRegularFile(path) }
        ?.let { path -> Path(path.toString()) }
        ?: Files.list(java.nio.file.Path.of(root.toString())).use { stream ->
            stream
                .filter { path -> path.fileName.toString().endsWith(".exe.manifest", ignoreCase = true) }
                .sorted()
                .findFirst()
                .orElse(java.nio.file.Path.of(root.toString()).resolve("app.exe.manifest"))
                .let { path -> Path(path.toString()) }
        }

internal actual fun platformActivateWindowsManifest(manifestPath: Path): AutoCloseable =
    Arena.ofConfined().use { callArena ->
        val manifestSegment = allocateWideString(callArena, manifestPath.canonicalString())
        val actCtx = callArena.allocate(actCtxLayout)
        actCtx.set(ValueLayout.JAVA_INT, 0L, actCtxLayout.byteSize().toInt())
        actCtx.set(ValueLayout.JAVA_INT, 4L, 0)
        actCtx.set(ValueLayout.ADDRESS, 8L, manifestSegment)
        actCtx.set(ValueLayout.JAVA_SHORT, 16L, 0)
        actCtx.set(ValueLayout.JAVA_SHORT, 18L, 0)
        actCtx.set(ValueLayout.ADDRESS, 24L, MemorySegment.NULL)
        actCtx.set(ValueLayout.ADDRESS, 32L, MemorySegment.NULL)
        actCtx.set(ValueLayout.ADDRESS, 40L, MemorySegment.NULL)
        actCtx.set(ValueLayout.ADDRESS, 48L, MemorySegment.NULL)
        val handle = downcall("CreateActCtxW", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
            .invokeWithArguments(actCtx) as MemorySegment
        if (handle.address() == -1L) {
            error("CreateActCtxW failed with GetLastError=${getLastError()} for ${manifestPath.canonicalString()}")
        }
        val cookieOut = callArena.allocate(ValueLayout.JAVA_LONG)
        val activated = downcall(
            "ActivateActCtx",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        ).invokeWithArguments(handle, cookieOut) as Int
        if (activated == 0) {
            releaseActCtx(handle)
            error("ActivateActCtx failed with GetLastError=${getLastError()} for ${manifestPath.canonicalString()}")
        }
        JvmActivationContextScope(
            handle = RawAddress(handle.address()),
            cookie = RawAddress(cookieOut.get(ValueLayout.JAVA_LONG, 0L)),
        )
    }

internal actual fun platformGetWindowsEnvironmentVariable(name: String): String? =
    Arena.ofConfined().use { callArena ->
        val nameSegment = allocateWideString(callArena, name)
        var capacity = 256
        while (true) {
            val buffer = callArena.allocate(capacity.toLong() * ValueLayout.JAVA_CHAR.byteSize(), 2L)
            val length = downcall(
                "GetEnvironmentVariableW",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                ),
            ).invokeWithArguments(nameSegment, buffer, capacity) as Int
            if (length == 0) {
                val lastError = getLastError()
                if (lastError == windowsEnvironmentVariableNotFound) {
                    return@use null
                }
                if (lastError == 0) {
                    return@use ""
                }
                error("GetEnvironmentVariableW failed with GetLastError=$lastError for $name")
            }
            if (length < capacity) {
                val chars = buffer.asSlice(0L, length.toLong() * ValueLayout.JAVA_CHAR.byteSize())
                    .toArray(ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN))
                return@use String(chars)
            }
            capacity = length + 1
        }
        error("GetEnvironmentVariableW did not return a value.")
    }

internal actual fun platformSetWindowsEnvironmentVariable(name: String, value: String?) {
    Arena.ofConfined().use { callArena ->
        val result = downcall(
            "SetEnvironmentVariableW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
        ).invokeWithArguments(
            allocateWideString(callArena, name),
            value?.let { allocateWideString(callArena, it) } ?: MemorySegment.NULL,
        ) as Int
        if (result == 0) {
            error("SetEnvironmentVariableW failed with GetLastError=${getLastError()} for $name")
        }
    }
}

internal actual fun platformTryLoadWindowsLibrary(path: Path): RawAddress? =
    runCatching { platformLoadWindowsLibrary(path) }.getOrNull()

internal actual fun platformLoadWindowsLibrary(path: Path): RawAddress =
    storePlatformHandle(SymbolLookup.libraryLookup(java.nio.file.Path.of(path.canonicalString()), arena))

internal actual fun platformFreeWindowsLibrary(module: RawAddress) {
    // FFM keeps libraries loaded by the process-wide arena. Remove only the lookup handle here;
    // unloading the DLL would invalidate retained function pointers used by the runtime.
    platformHandles.remove(module.value)
}

internal actual fun platformTryGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress? =
    lookup(module).find(procedureName).orElse(null)?.let(::storePlatformHandle)

internal actual fun platformGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress =
    lookup(module).find(procedureName).orElseThrow().let(::storePlatformHandle)

internal actual fun platformCallWindowsAppRuntimeEnsureIsLoaded(procedure: RawAddress) {
    val handle = linker.downcallHandle(symbol(procedure), FunctionDescriptor.of(ValueLayout.JAVA_INT))
    HResult(handle.invokeWithArguments() as Int).requireSuccess("WindowsAppRuntime_EnsureIsLoaded")
}

internal actual fun platformCallMddBootstrapInitialize2(
    procedure: RawAddress,
    majorMinorVersion: Int,
    versionTag: String?,
    minVersion: Long,
    options: Int,
) {
    val handle = linker.downcallHandle(
        symbol(procedure),
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
        ),
    )
    Arena.ofConfined().use { callArena ->
        val tag = versionTag?.takeIf { it.isNotEmpty() }?.let { allocateWideString(callArena, it) } ?: MemorySegment.NULL
        HResult(handle.invokeWithArguments(majorMinorVersion, tag, minVersion, options) as Int)
            .requireSuccess("MddBootstrapInitialize2")
    }
}

internal actual fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress) {
    bootstrapShutdown = lookup(module).find("MddBootstrapShutdown").orElse(null)?.let { symbol ->
        linker.downcallHandle(symbol, FunctionDescriptor.ofVoid())
    } ?: error("Microsoft.WindowsAppRuntime.Bootstrap.dll does not export MddBootstrapShutdown.")
}

internal actual fun platformForgetWindowsAppSdkBootstrapShutdown() {
    bootstrapShutdown = null
}

internal actual fun platformWindowsAppSdkBootstrapShutdown() {
    val shutdown = bootstrapShutdown ?: error("Windows App SDK bootstrap shutdown was not initialized.")
    bootstrapShutdown = null
    shutdown.invokeWithArguments()
}

private class JvmActivationContextScope(
    private val handle: RawAddress,
    private val cookie: RawAddress,
) : AutoCloseable {
    override fun close() {
        var failure: Throwable? = null
        try {
            val result = downcall(
                "DeactivateActCtx",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG),
            ).invokeWithArguments(0, cookie.value) as Int
            if (result == 0) {
                error("DeactivateActCtx failed with GetLastError=${getLastError()}")
            }
        } catch (error: Throwable) {
            failure = error
        }
        try {
            releaseActCtx(handle)
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { throw it }
    }
}

private fun storePlatformHandle(value: Any): RawAddress {
    val key = nextPlatformHandle.getAndIncrement()
    platformHandles[key] = value
    return RawAddress(key)
}

private fun lookup(handle: RawAddress): SymbolLookup =
    platformHandles[handle.value] as? SymbolLookup
        ?: error("Windows library handle is no longer valid.")

private fun symbol(handle: RawAddress): MemorySegment =
    platformHandles[handle.value] as? MemorySegment
        ?: error("Windows procedure handle is no longer valid.")

private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
    linker.downcallHandle(kernel32.find(name).orElseThrow(), descriptor)

private fun getLastError(): Int =
    downcall("GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT)).invokeWithArguments() as Int

private fun releaseActCtx(handle: MemorySegment) {
    downcall("ReleaseActCtx", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)).invokeWithArguments(handle)
}

private fun releaseActCtx(handle: RawAddress) {
    releaseActCtx(MemorySegment.ofAddress(handle.value))
}

private fun allocateWideString(arena: Arena, value: String): MemorySegment {
    val bytes = (value + '\u0000').toByteArray(StandardCharsets.UTF_16LE)
    return arena.allocate(bytes.size.toLong(), 2).copyFrom(MemorySegment.ofArray(bytes))
}

private const val windowsEnvironmentVariableNotFound = 203
