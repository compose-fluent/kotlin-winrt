package io.github.kitectlab.winrt.runtime

object WindowsAppSdkBootstrap {
    private const val defaultMajorMinorVersion = 0x00010008
    private const val defaultMinVersion = 0x1F40032608CC0000L
    private const val versionInfoHeaderRelativePath = "include/WindowsAppSDK-VersionInfo.h"
    private const val versionTag = ""
    private const val bootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
    private const val bootstrapDllProperty = "io.github.kitectlab.winrt.bootstrapDll"
    private const val windowsAppSdkRootProperty = "io.github.kitectlab.winrt.windowsAppSdkRoot"
    private const val loadWithAlteredSearchPath = 0x00000008
    private const val bootstrapInitialize2 = "MddBootstrapInitialize2"
    private const val bootstrapShutdown = "MddBootstrapShutdown"
    private val releaseMajorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJOR\s+(\d+)""")
    private val releaseMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MINOR\s+(\d+)""")
    private val releaseMajorMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJORMINOR\s+(0x[0-9A-Fa-f]+)""")
    private val releaseVersionTagRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_VERSION_TAG_W\s+L"([^"]*)"""")
    private val runtimeVersionRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_VERSION_UINT64\s+(0x[0-9A-Fa-f]+)u""")
    private val frameworkPackageFamilyNameRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_PACKAGE_FRAMEWORK_PACKAGEFAMILYNAME\s+"([^"]+)"""")
    private val mainPackageFamilyNameRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_PACKAGE_MAIN_PACKAGEFAMILYNAME\s+"([^"]+)"""")
    private val singletonPackageFamilyNameRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_PACKAGE_SINGLETON_PACKAGEFAMILYNAME\s+"([^"]+)"""")

    class BootstrapLibrary internal constructor(
        val path: String,
        private val moduleHandle: NativePointer,
        private val initializePointer: NativePointer,
        private val shutdownPointer: NativePointer,
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
        val releaseMajor: Int?,
        val releaseMinor: Int?,
        val majorMinorVersion: Int,
        val versionTag: String,
        val minVersion: Long,
        val frameworkPackageFamilyName: String?,
        val mainPackageFamilyName: String?,
        val singletonPackageFamilyName: String?,
    )

    fun parseNuGetGlobalPackagesOutput(output: String): List<String> =
        output.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("global-packages:", ignoreCase = true) }
            .map { it.substringAfter(':').trim().trim('"') }
            .filter(String::isNotEmpty)
            .toList()

    fun discoverBootstrapLibrary(): BootstrapLibrary? =
        explicitBootstrapCandidates()
            .firstNotNullOfOrNull { path ->
                if (!PlatformFileSystem.isRegularFile(path)) {
                    return@firstNotNullOfOrNull null
                }

                val moduleHandle = WinRtPlatformApi.tryLoadLibraryExWRaw(
                    PlatformFileSystem.absolutePath(path),
                    loadWithAlteredSearchPath,
                )
                if (NativeInterop.isNull(moduleHandle)) {
                    return@firstNotNullOfOrNull null
                }

                val initializePointer = WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, bootstrapInitialize2)
                val shutdownPointer = WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, bootstrapShutdown)
                if (NativeInterop.isNull(initializePointer) || NativeInterop.isNull(shutdownPointer)) {
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

    fun discoverConfiguredVersionInfo(): BootstrapVersionInfo? {
        val candidates = buildList {
            PlatformFileSystem.systemProperty(windowsAppSdkRootProperty)
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(it)) }
            PlatformFileSystem.environmentVariable("WINAPPSDK_BOOTSTRAP_DLL")
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(it)) }
            PlatformFileSystem.systemProperty(bootstrapDllProperty)
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(it)) }
        }

        val header = candidates.distinct().firstOrNull(PlatformFileSystem::isRegularFile) ?: return null
        return parseVersionInfoHeader(PlatformFileSystem.readText(header))
    }

    fun initialize(majorMinorVersion: Int = defaultMajorMinorVersion): Result<BootstrapLibrary> =
        runCatching {
            val library = discoverBootstrapLibrary()
                ?: error("$bootstrapDllName was not found")
            try {
                val versionInfo = discoverVersionInfo(library.path)
                    ?: discoverConfiguredVersionInfo()
                    ?: BootstrapVersionInfo(
                        releaseMajor = 1,
                        releaseMinor = 8,
                        majorMinorVersion = majorMinorVersion,
                        versionTag = versionTag,
                        minVersion = defaultMinVersion,
                        frameworkPackageFamilyName = null,
                        mainPackageFamilyName = null,
                        singletonPackageFamilyName = null,
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

    internal fun parseVersionInfoHeader(content: String): BootstrapVersionInfo {
        val majorMinor = releaseMajorMinorRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.toInt(16)
            ?: error("WINDOWSAPPSDK_RELEASE_MAJORMINOR is missing")
        val minVersion = runtimeVersionRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.removeSuffix("u")
            ?.toULong(16)
            ?.toLong()
            ?: error("WINDOWSAPPSDK_RUNTIME_VERSION_UINT64 is missing")
        return BootstrapVersionInfo(
            releaseMajor = releaseMajorRegex.find(content)?.groupValues?.get(1)?.toIntOrNull(),
            releaseMinor = releaseMinorRegex.find(content)?.groupValues?.get(1)?.toIntOrNull(),
            majorMinorVersion = majorMinor,
            versionTag = releaseVersionTagRegex.find(content)?.groupValues?.get(1) ?: "",
            minVersion = minVersion,
            frameworkPackageFamilyName = frameworkPackageFamilyNameRegex.find(content)?.groupValues?.get(1),
            mainPackageFamilyName = mainPackageFamilyNameRegex.find(content)?.groupValues?.get(1),
            singletonPackageFamilyName = singletonPackageFamilyNameRegex.find(content)?.groupValues?.get(1),
        )
    }

    private fun explicitBootstrapCandidates(): List<String> = buildList {
        PlatformFileSystem.environmentVariable("WINAPPSDK_BOOTSTRAP_DLL")?.let(::add)
        PlatformFileSystem.systemProperty(bootstrapDllProperty)?.takeIf(String::isNotBlank)?.let(::add)
        PlatformFileSystem.systemProperty(windowsAppSdkRootProperty)?.takeIf(String::isNotBlank)?.let {
            addAll(bootstrapDllCandidates(it))
        }
    }

    private fun bootstrapDllCandidates(root: String): List<String> =
        if (!PlatformFileSystem.isDirectory(root)) {
            emptyList()
        } else {
            PlatformFileSystem.walkFiles(root).filter { path ->
                PlatformFileSystem.isRegularFile(path) &&
                    PlatformFileSystem.fileName(path).equals(bootstrapDllName, ignoreCase = true)
            }
        }

    private fun discoverVersionInfo(bootstrapDll: String): BootstrapVersionInfo? {
        val header = versionInfoHeaderCandidates(bootstrapDll).firstOrNull(PlatformFileSystem::isRegularFile) ?: return null
        return parseVersionInfoHeader(PlatformFileSystem.readText(header))
    }

    private fun versionInfoHeaderCandidates(location: String): List<String> {
        val initial = if (PlatformFileSystem.isDirectory(location)) {
            location
        } else {
            PlatformFileSystem.parent(location) ?: return emptyList()
        }
        return generateSequence(initial) { current -> PlatformFileSystem.parent(current) }
            .take(8)
            .map { PlatformFileSystem.resolve(it, versionInfoHeaderRelativePath) }
            .toList()
    }
}
