package io.github.kitectlab.winrt.runtime

import kotlinx.io.files.Path

object WindowsAppSdkBootstrap {
    private const val defaultMajorMinorVersion = 0x00010008
    private const val defaultMinVersion = 0x1F40032608CC0000L
    private const val versionTag = ""
    private const val bootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
    private const val loadWithAlteredSearchPath = 0x00000008
    private const val bootstrapInitialize2 = "MddBootstrapInitialize2"
    private const val bootstrapShutdown = "MddBootstrapShutdown"

    class BootstrapLibrary internal constructor(
        val path: String,
        private val moduleHandle: RawAddress,
        private val initializePointer: RawAddress,
        private val shutdownPointer: RawAddress,
    ) : AutoCloseable {
        private val lock = PlatformLock()
        private var closed = false

        fun initialize(versionInfo: BootstrapVersionInfo) {
            HResult(
                WinRtPlatformApi.mddBootstrapInitialize2Raw(
                    initializeProc = initializePointer,
                    majorMinorVersion = versionInfo.majorMinorVersion,
                    versionTag = versionInfo.versionTag,
                    minVersion = versionInfo.minVersion,
                ),
            ).requireSuccess("MddBootstrapInitialize2")
        }

        fun shutdown() {
            try {
                WinRtPlatformApi.mddBootstrapShutdownRaw(shutdownPointer)
            } finally {
                close()
            }
        }

        override fun close() {
            lock.withLock {
                if (closed) {
                    return@withLock
                }
                WinRtPlatformApi.freeLibraryRaw(moduleHandle)
                closed = true
            }
        }
    }

    data class BootstrapVersionInfo(
        val majorMinorVersion: Int,
        val versionTag: String,
        val minVersion: Long,
    )

    fun discoverBootstrapLibrary(): BootstrapLibrary? =
        bootstrapDllCandidates()
            .firstNotNullOfOrNull { path ->
                if (!Path(path).isRegularFile()) {
                    return@firstNotNullOfOrNull null
                }

                val moduleHandle = WinRtPlatformApi.tryLoadLibraryExWRaw(
                    absolutePath(path),
                    loadWithAlteredSearchPath,
                )
                if (PlatformAbi.isNull(moduleHandle)) {
                    return@firstNotNullOfOrNull null
                }

                val initializePointer = WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, bootstrapInitialize2)
                val shutdownPointer = WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, bootstrapShutdown)
                if (PlatformAbi.isNull(initializePointer) || PlatformAbi.isNull(shutdownPointer)) {
                    WinRtPlatformApi.freeLibraryRaw(moduleHandle)
                    return@firstNotNullOfOrNull null
                }

                BootstrapLibrary(
                    path = path,
                    moduleHandle = moduleHandle,
                    initializePointer = initializePointer,
                    shutdownPointer = shutdownPointer,
                )
            }

    fun initialize(majorMinorVersion: Int = defaultMajorMinorVersion): Result<BootstrapLibrary> =
        runCatching {
            val library = discoverBootstrapLibrary()
                ?: error("$bootstrapDllName was not found")
            try {
                val versionInfo = BootstrapVersionInfo(
                    majorMinorVersion = majorMinorVersion,
                    versionTag = versionTag,
                    minVersion = defaultMinVersion,
                )
                library.initialize(versionInfo)
                library
            } catch (t: Throwable) {
                runCatching {
                    library.close()
                }
                throw t
            }
        }

    fun shutdown(library: BootstrapLibrary): Result<Unit> =
        runCatching {
            library.shutdown()
        }

    private fun bootstrapDllCandidates(): List<String> = listOf(
        WinRtPlatformApi.resolveModulePathRaw(bootstrapDllName),
    )
}
