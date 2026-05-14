package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object WinRtWindowsAppSdkBootstrap {
    private const val defaultMajorMinorVersion = 0x00010008
    private const val defaultMinVersion = 0x1F40032608CC0000L
    private const val bootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
    private const val versionInfoHeaderRelativePath = "include/WindowsAppSDK-VersionInfo.h"
    private val releaseMajorMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJORMINOR\s+(0x[0-9A-Fa-f]+)""")
    private val releaseVersionTagRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_VERSION_TAG_W\s+L"([^"]*)"""")
    private val runtimeVersionRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_VERSION_UINT64\s+(0x[0-9A-Fa-f]+)u""")
    private val arena: Arena = Arena.ofAuto()
    private val linker by lazy { java.lang.foreign.Linker.nativeLinker() }

    class Scope internal constructor(
        val bootstrapDll: Path,
        private val activationContexts: List<WinRtWindowsActivationContext.Scope>,
        private val lookup: SymbolLookup?,
    ) : AutoCloseable {
        override fun close() {
            EventSourceShutdownRegistry.closeAllActiveRegistrations()
            lookup?.let(::shutdown)
            activationContexts.asReversed().forEach { context ->
                context.close()
            }
        }
    }

    private data class BootstrapVersionInfo(
        val majorMinorVersion: Int,
        val versionTag: String,
        val minVersion: Long,
    )

    fun initialize(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        val root = runtimeAssetsRoot ?: return null
        val bootstrapDll = bootstrapDllCandidates(root)
            .firstOrNull { it.isRegularFile() }
            ?: return null
        val processCompatibilityContext = WinRtWindowsActivationContext.activateProcessCompatibility(root)
        val activationContext = WinRtWindowsActivationContext.activate(root)
        if (activationContext != null) {
            return Scope(
                bootstrapDll = bootstrapDll,
                activationContexts = listOfNotNull(processCompatibilityContext, activationContext),
                lookup = null,
            )
        }
        val lookup = SymbolLookup.libraryLookup(bootstrapDll, arena)
        val versionInfo = discoverVersionInfo(root) ?: BootstrapVersionInfo(
            majorMinorVersion = defaultMajorMinorVersion,
            versionTag = "",
            minVersion = defaultMinVersion,
        )
        val initialize2 = linker.downcallHandle(
            lookup.find("MddBootstrapInitialize2").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
            ),
        )
        Arena.ofConfined().use { callArena ->
            val tag = if (versionInfo.versionTag.isEmpty()) {
                MemorySegment.NULL
            } else {
                allocateWideString(callArena, versionInfo.versionTag)
            }
            HResult(
                initialize2.invokeWithArguments(
                    versionInfo.majorMinorVersion,
                    tag,
                    versionInfo.minVersion,
                    0,
                ) as Int,
            ).requireSuccess("MddBootstrapInitialize2")
        }
        return Scope(
            bootstrapDll = bootstrapDll,
            activationContexts = listOfNotNull(processCompatibilityContext),
            lookup = lookup,
        )
    }

    fun discoverRuntimeAssetsRoot(): Path? {
        return WinRtRuntimeAssets.discoverRuntimeAssetsRoot(bootstrapDllName)
    }

    private fun shutdown(lookup: SymbolLookup) {
        runCatching {
            val shutdown = linker.downcallHandle(
                lookup.find("MddBootstrapShutdown").orElseThrow(),
                FunctionDescriptor.ofVoid(),
            )
            shutdown.invokeWithArguments()
        }
    }

    private fun bootstrapDllCandidates(root: Path): List<Path> =
        if (root.isDirectory()) {
            Files.walk(root).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.name.equals(bootstrapDllName, ignoreCase = true) }
                    .sorted()
                    .toList()
            }
        } else {
            emptyList()
        }

    private fun discoverVersionInfo(root: Path): BootstrapVersionInfo? {
        val header = generateSequence(root) { it.parent }
            .take(5)
            .map { it.resolve(versionInfoHeaderRelativePath) }
            .firstOrNull { it.isRegularFile() }
            ?: return null
        val content = Files.readString(header)
        val majorMinor = releaseMajorMinorRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.toInt(16)
            ?: return null
        val versionTag = releaseVersionTagRegex.find(content)?.groupValues?.get(1) ?: ""
        val minVersion = runtimeVersionRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.removeSuffix("u")
            ?.toULong(16)
            ?.toLong()
            ?: return null
        return BootstrapVersionInfo(majorMinor, versionTag, minVersion)
    }

    private fun allocateWideString(arena: Arena, value: String): MemorySegment {
        val bytes = (value + '\u0000').toByteArray(StandardCharsets.UTF_16LE)
        return arena.allocate(bytes.size.toLong(), 2).copyFrom(MemorySegment.ofArray(bytes))
    }
}
