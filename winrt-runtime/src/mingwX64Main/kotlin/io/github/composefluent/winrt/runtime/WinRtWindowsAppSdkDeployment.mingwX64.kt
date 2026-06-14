@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.CFunction
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
    val bootstrapPath = Path(WinRtPlatformApi.resolveModulePathRaw(anchorFileName))
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

internal actual fun platformSetWindowsEnvironmentVariable(name: String, value: String) {
    val result = SetEnvironmentVariableW(name, value)
    if (result == 0) {
        error("SetEnvironmentVariableW failed with GetLastError=${GetLastError()} for $name")
    }
}

internal actual fun platformTryLoadWindowsLibrary(path: Path): RawAddress? =
    WinRtPlatformApi.tryLoadLibraryExWRaw(path.canonicalString(), 0)
        .takeUnless { handle -> PlatformAbi.isNull(handle) }

internal actual fun platformLoadWindowsLibrary(path: Path): RawAddress =
    WinRtPlatformApi.loadLibraryExWRaw(path.canonicalString(), 0)

internal actual fun platformFreeWindowsLibrary(module: RawAddress) {
    WinRtPlatformApi.freeLibraryRaw(module)
}

internal actual fun platformTryGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress? =
    WinRtPlatformApi.tryGetProcAddressRaw(module, procedureName)
        .takeUnless { address -> PlatformAbi.isNull(address) }

internal actual fun platformGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress =
    WinRtPlatformApi.getProcAddressRaw(module, procedureName)

internal actual fun platformCallWindowsAppRuntimeEnsureIsLoaded(procedure: RawAddress) {
    HResult(procedure.asEnsureIsLoaded().invoke()).requireSuccess("WindowsAppRuntime_EnsureIsLoaded")
}

internal actual fun platformCallMddBootstrapInitialize2(
    procedure: RawAddress,
    majorMinorVersion: Int,
    versionTag: String?,
    minVersion: Long,
) {
    val hResult = procedure.asBootstrapInitialize2().invoke(
        majorMinorVersion,
        null,
        minVersion,
        0,
    )
    HResult(hResult).requireSuccess("MddBootstrapInitialize2")
}

internal actual fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress) {
    bootstrapShutdown = WinRtPlatformApi.tryGetProcAddressRaw(module, "MddBootstrapShutdown")
        .takeUnless { address -> PlatformAbi.isNull(address) }
        ?.asBootstrapShutdown()
}

internal actual fun platformWindowsAppSdkBootstrapShutdown() {
    bootstrapShutdown?.invoke()
}

private class NativeActivationContextScope(
    private val handle: RawAddress,
    private val cookie: RawAddress,
) : AutoCloseable {
    override fun close() {
        DeactivateActCtx(0u, cookie.value.toULong())
        ReleaseActCtx(handle.value.toCPointer() ?: return)
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

private fun Path.parentPath(): Path? {
    val text = toString().trimEnd('\\', '/')
    val index = maxOf(text.lastIndexOf('\\'), text.lastIndexOf('/'))
    return if (index <= 0) null else Path(text.substring(0, index))
}

private const val runtimeAssetsRootEnvironmentVariableName = "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"
