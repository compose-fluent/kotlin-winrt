@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.posix.getenv
import platform.windows.ACTCTXW
import platform.windows.ActivateActCtx
import platform.windows.CreateActCtxW
import platform.windows.DeactivateActCtx
import platform.windows.GetEnvironmentVariableW
import platform.windows.GetLastError
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.ReleaseActCtx
import platform.windows.SetEnvironmentVariableW
import platform.windows.ULONG_PTRVar

private var bootstrapShutdown: CPointer<CFunction<() -> Unit>>? = null

internal actual fun platformDiscoverWindowsAppSdkRuntimeAssetsRoot(anchorFileName: String): Path? {
    getenv(runtimeAssetsRootEnvironmentVariableName)?.toKString()?.takeIf { it.isNotBlank() }?.let { root ->
        Path(root).takeIf { path -> path.isDirectory() }?.let { return it }
    }
    val bootstrapPath = Path(WinRTPlatformApi.resolveModulePathRaw(anchorFileName))
    return bootstrapPath.parentPath()?.takeIf { it.isDirectory() }
}

internal actual fun platformWindowsApplicationManifestPath(root: Path): Path =
    root.walkFiles()
        .filter { path -> path.fileName.endsWith(".exe.manifest", ignoreCase = true) }
        .sortedBy { path -> path.canonicalString() }
        .firstOrNull()
        ?: Path(root, "app.exe.manifest")

internal actual fun platformActivateWindowsManifest(manifestPath: Path): AutoCloseable =
    PlatformAbi.confinedScope().use { scope ->
        memScoped {
            val source = PlatformAbi.allocateUtf16(scope, manifestPath.canonicalString(), nulTerminated = true)
            val actCtx = alloc<ACTCTXW>()
            actCtx.cbSize = sizeOf<ACTCTXW>().convert()
            actCtx.dwFlags = 0u
            actCtx.lpSource = source.value.toCPointer()
            actCtx.wProcessorArchitecture = 0u
            actCtx.wLangId = 0u
            actCtx.lpAssemblyDirectory = null
            actCtx.lpResourceName = null
            actCtx.lpApplicationName = null
            actCtx.hModule = null
            val handle = CreateActCtxW(actCtx.ptr)
            if (handle == INVALID_HANDLE_VALUE) {
                error("CreateActCtxW failed with GetLastError=${GetLastError()} for ${manifestPath.canonicalString()}")
            }
            val cookieOut = alloc<ULONG_PTRVar>()
            val activated = ActivateActCtx(handle, cookieOut.ptr)
            if (activated == 0) {
                ReleaseActCtx(handle)
                error("ActivateActCtx failed with GetLastError=${GetLastError()} for ${manifestPath.canonicalString()}")
            }
            NativeActivationContextScope(handle.asRawAddress(), RawAddress(cookieOut.value.toLong()))
        }
    }

internal actual fun platformGetWindowsEnvironmentVariable(name: String): String? =
    PlatformAbi.confinedScope().use { scope ->
        var capacity = 256
        while (true) {
            val buffer = PlatformAbi.allocateBytes(scope, capacity.toLong() * 2L)
            val length = GetEnvironmentVariableW(
                name,
                buffer.value.toCPointer(),
                capacity.toUInt(),
            ).toInt()
            if (length == 0) {
                val lastError = GetLastError()
                if (lastError == 203u) {
                    return@use null
                }
                if (lastError == 0u) {
                    return@use ""
                }
                error("GetEnvironmentVariableW failed with GetLastError=$lastError for $name")
            }
            if (length < capacity) {
                return@use PlatformAbi.readUtf16(buffer, length)
            }
            capacity = length + 1
        }
        error("GetEnvironmentVariableW did not return a value.")
    }

internal actual fun platformSetWindowsEnvironmentVariable(name: String, value: String?) {
    val result = SetEnvironmentVariableW(name, value)
    if (result == 0) {
        error("SetEnvironmentVariableW failed with GetLastError=${GetLastError()} for $name")
    }
}

internal actual fun platformTryLoadWindowsLibrary(path: Path): RawAddress? =
    WinRTPlatformApi.tryLoadLibraryExWRaw(path.canonicalString(), 0)
        .takeUnless { handle -> PlatformAbi.isNull(handle) }

internal actual fun platformLoadWindowsLibrary(path: Path): RawAddress =
    WinRTPlatformApi.loadLibraryExWRaw(path.canonicalString(), 0)

internal actual fun platformFreeWindowsLibrary(module: RawAddress) {
    if (!WinRTPlatformApi.freeLibraryRaw(module)) {
        error(
            "FreeLibrary failed for module ${module.value} " +
                "with HRESULT ${HResult(WinRTPlatformApi.lastErrorAsHResultRaw())}.",
        )
    }
}

internal actual fun platformTryGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress? =
    WinRTPlatformApi.tryGetProcAddressRaw(module, procedureName)
        .takeUnless { address -> PlatformAbi.isNull(address) }

internal actual fun platformGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress =
    WinRTPlatformApi.getProcAddressRaw(module, procedureName)

internal actual fun platformCallWindowsAppRuntimeEnsureIsLoaded(procedure: RawAddress) {
    HResult(procedure.asEnsureIsLoaded().invoke()).requireSuccess("WindowsAppRuntime_EnsureIsLoaded")
}

internal actual fun platformCallMddBootstrapInitialize2(
    procedure: RawAddress,
    majorMinorVersion: Int,
    versionTag: String?,
    minVersion: Long,
    options: Int,
) {
    PlatformAbi.confinedScope().use { scope ->
        val versionTagPointer = versionTag
            ?.takeIf(String::isNotEmpty)
            ?.let { value ->
                PlatformAbi.allocateUtf16(scope, value, nulTerminated = true).value.toCPointer<COpaque>()
            }
        val hResult = procedure.asBootstrapInitialize2().invoke(
            majorMinorVersion,
            versionTagPointer,
            minVersion,
            options,
        )
        HResult(hResult).requireSuccess("MddBootstrapInitialize2")
    }
}

internal actual fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress) {
    bootstrapShutdown = WinRTPlatformApi.tryGetProcAddressRaw(module, "MddBootstrapShutdown")
        .takeUnless { address -> PlatformAbi.isNull(address) }
        ?.asBootstrapShutdown()
        ?: error("Microsoft.WindowsAppRuntime.Bootstrap.dll does not export MddBootstrapShutdown.")
}

internal actual fun platformForgetWindowsAppSdkBootstrapShutdown() {
    bootstrapShutdown = null
}

internal actual fun platformWindowsAppSdkBootstrapShutdown() {
    val shutdown = bootstrapShutdown ?: error("Windows App SDK bootstrap shutdown was not initialized.")
    bootstrapShutdown = null
    shutdown.invoke()
}

private class NativeActivationContextScope(
    private val handle: RawAddress,
    private val cookie: RawAddress,
) : AutoCloseable {
    override fun close() {
        var failure: Throwable? = null
        try {
            if (DeactivateActCtx(0u, cookie.value.toULong()) == 0) {
                error("DeactivateActCtx failed with GetLastError=${GetLastError()}")
            }
        } catch (error: Throwable) {
            failure = error
        }
        try {
            ReleaseActCtx(handle.value.toCPointer() ?: error("Activation context handle is null."))
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { throw it }
    }
}

private fun RawAddress.asEnsureIsLoaded(): CPointer<CFunction<() -> Int>> =
    value.toCPointer<CFunction<() -> Int>>()
        ?: error("WindowsAppRuntime_EnsureIsLoaded resolved to a null function pointer.")

private fun RawAddress.asBootstrapInitialize2(): CPointer<CFunction<(Int, COpaquePointer?, Long, Int) -> Int>> =
    value.toCPointer<CFunction<(Int, COpaquePointer?, Long, Int) -> Int>>()
        ?: error("MddBootstrapInitialize2 resolved to a null function pointer.")

private fun RawAddress.asBootstrapShutdown(): CPointer<CFunction<() -> Unit>> =
    value.toCPointer<CFunction<() -> Unit>>()
        ?: error("MddBootstrapShutdown resolved to a null function pointer.")

private fun COpaquePointer?.asRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)

private const val runtimeAssetsRootEnvironmentVariableName = "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"
