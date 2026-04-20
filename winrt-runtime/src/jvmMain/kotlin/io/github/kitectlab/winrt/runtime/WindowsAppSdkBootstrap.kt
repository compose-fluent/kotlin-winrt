package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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
        val path: Path,
        private val moduleHandle: NativePointer,
        private val initializePointer: NativePointer,
        private val shutdownPointer: NativePointer,
    ) : AutoCloseable {
        @Volatile
        private var closed = false

        fun initialize(versionInfo: BootstrapVersionInfo) {
            val initialize2 = Linker.nativeLinker().downcallHandle(
                initializePointer.asMemorySegment(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                ),
            )
            Arena.ofConfined().use { arena ->
                val tagSegment = if (versionInfo.versionTag.isBlank()) {
                    MemorySegment.NULL
                } else {
                    allocateWideString(arena, versionInfo.versionTag)
                }
                HResult(
                    initialize2.invokeWithArguments(
                        versionInfo.majorMinorVersion,
                        tagSegment,
                        versionInfo.minVersion,
                        0,
                    ) as Int,
                ).requireSuccess("MddBootstrapInitialize2")
            }
        }

        fun shutdown() {
            try {
                Linker.nativeLinker().downcallHandle(
                    shutdownPointer.asMemorySegment(),
                    FunctionDescriptor.ofVoid(),
                ).invokeWithArguments()
            } finally {
                close()
            }
        }

        override fun close() {
            if (closed) {
                return
            }
            synchronized(this) {
                if (closed) {
                    return
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

    fun parseNuGetGlobalPackagesOutput(output: String): List<Path> =
        output.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("global-packages:", ignoreCase = true) }
            .map { it.substringAfter(':').trim().trim('"') }
            .filter(String::isNotEmpty)
            .map(Path::of)
            .toList()

    fun discoverBootstrapLibrary(): BootstrapLibrary? =
        explicitBootstrapCandidates()
            .firstNotNullOfOrNull { path ->
                if (!Files.isRegularFile(path)) {
                    return@firstNotNullOfOrNull null
                }

                val moduleHandle = WinRtPlatformApi.tryLoadLibraryExWRaw(
                    path.toAbsolutePath().toString(),
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
            System.getProperty(windowsAppSdkRootProperty)
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(Path.of(it))) }
            System.getenv("WINAPPSDK_BOOTSTRAP_DLL")
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(Path.of(it))) }
            System.getProperty(bootstrapDllProperty)
                ?.takeIf(String::isNotBlank)
                ?.let { addAll(versionInfoHeaderCandidates(Path.of(it))) }
        }

        val header = candidates.distinct().firstOrNull(Files::isRegularFile) ?: return null
        return parseVersionInfoHeader(Files.readString(header))
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

    private fun explicitBootstrapCandidates(): List<Path> = buildList {
        System.getenv("WINAPPSDK_BOOTSTRAP_DLL")?.let { add(Path.of(it)) }
        System.getProperty(bootstrapDllProperty)?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
        System.getProperty(windowsAppSdkRootProperty)?.takeIf(String::isNotBlank)?.let {
            addAll(bootstrapDllCandidates(Path.of(it)))
        }
    }

    private fun bootstrapDllCandidates(root: Path): List<Path> =
        if (!Files.isDirectory(root)) {
            emptyList()
        } else {
            Files.walk(root).use { stream ->
                stream.filter { file ->
                    Files.isRegularFile(file) && file.fileName.toString().equals(bootstrapDllName, ignoreCase = true)
                }.toList()
            }
        }

    private fun discoverVersionInfo(bootstrapDll: Path): BootstrapVersionInfo? {
        val header = versionInfoHeaderCandidates(bootstrapDll).firstOrNull(Files::isRegularFile) ?: return null
        return parseVersionInfoHeader(Files.readString(header))
    }

    private fun versionInfoHeaderCandidates(location: Path): List<Path> {
        val initial = if (Files.isDirectory(location)) location else location.parent
        return generateSequence(initial) { current -> current.parent }
            .take(8)
            .map { it.resolve(versionInfoHeaderRelativePath) }
            .toList()
    }

    private fun allocateWideString(arena: Arena, value: String): MemorySegment =
        arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE)
}
