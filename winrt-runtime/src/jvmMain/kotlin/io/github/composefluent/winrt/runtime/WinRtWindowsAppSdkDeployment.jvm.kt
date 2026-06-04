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

/**
 * Windows App SDK deployment visibility for JVM WinUI apps.
 *
 * This deliberately mirrors the C++/WinRT split between normal WinRT apartment/runtime
 * initialization and package deployment. Packaged apps already have identity and manifest
 * registration, so Kotlin code starts XAML directly just like C++/WinRT packaged apps.
 * Unpackaged apps need either self-contained activation context registration or the
 * Windows App Runtime Dynamic Dependency bootstrap before WinUI activation.
 */
object WinRtWindowsAppSdkDeployment {
    private const val defaultMajorMinorVersion = 0x00010008
    private const val defaultMinVersion = 0x1F40032608CC0000L
    private const val bootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
    private const val runtimeDllName = "Microsoft.WindowsAppRuntime.dll"
    private const val versionInfoHeaderRelativePath = "include/WindowsAppSDK-VersionInfo.h"
    private val releaseMajorMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJORMINOR\s+(0x[0-9A-Fa-f]+)""")
    private val releaseVersionTagRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_VERSION_TAG_W\s+L"([^"]*)"""")
    private val runtimeVersionRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_VERSION_UINT64\s+(0x[0-9A-Fa-f]+)u""")
    private val arena: Arena = Arena.ofAuto()
    private val linker by lazy { java.lang.foreign.Linker.nativeLinker() }

    enum class Mode {
        DynamicDependency,
        SelfContained,
    }

    class Scope internal constructor(
        val mode: Mode,
        val bootstrapDll: Path?,
        private val activationContexts: List<WinRtWindowsActivationContext.Scope>,
        private val bootstrapLookup: SymbolLookup?,
        @Suppress("unused")
        private val windowsAppRuntimeLookup: SymbolLookup?,
    ) : AutoCloseable {
        override fun close() {
            bootstrapLookup?.let(::shutdown)
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

    /**
     * Make Windows App SDK classes visible for an unpackaged app.
     *
     * Self-contained Windows App SDK layouts are handled through an activation context and
     * `WindowsAppRuntime_EnsureIsLoaded`. Otherwise this falls back to the Dynamic Dependency
     * bootstrap API (`MddBootstrapInitialize2` / `MddBootstrapShutdown`).
     */
    fun initializeForUnpackagedApp(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope? {
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
            val runtimeLookup = loadSelfContainedWindowsAppRuntime(root)
            return Scope(
                mode = Mode.SelfContained,
                bootstrapDll = bootstrapDll,
                activationContexts = listOfNotNull(processCompatibilityContext, activationContext),
                bootstrapLookup = null,
                windowsAppRuntimeLookup = runtimeLookup,
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
            mode = Mode.DynamicDependency,
            bootstrapDll = bootstrapDll,
            activationContexts = listOfNotNull(processCompatibilityContext),
            bootstrapLookup = lookup,
            windowsAppRuntimeLookup = null,
        )
    }

    @Deprecated(
        message = "Use initializeForUnpackagedApp only for unpackaged apps. Packaged apps should start XAML directly.",
        replaceWith = ReplaceWith("initializeForUnpackagedApp(runtimeAssetsRoot)"),
    )
    fun initialize(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope? =
        initializeForUnpackagedApp(runtimeAssetsRoot)

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

    private fun loadSelfContainedWindowsAppRuntime(root: Path): SymbolLookup {
        val runtimeDll = runtimeDllCandidates(root)
            .firstOrNull { it.isRegularFile() }
            ?: error("WindowsAppSDK self-contained activation found no $runtimeDllName under $root")
        val lookup = SymbolLookup.libraryLookup(runtimeDll, arena)
        val ensureIsLoaded = linker.downcallHandle(
            lookup.find("WindowsAppRuntime_EnsureIsLoaded").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT),
        )
        HResult(ensureIsLoaded.invokeWithArguments() as Int).requireSuccess("WindowsAppRuntime_EnsureIsLoaded")
        return lookup
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

    private fun runtimeDllCandidates(root: Path): List<Path> =
        if (root.isDirectory()) {
            Files.walk(root).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.name.equals(runtimeDllName, ignoreCase = true) }
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

@Deprecated(
    message = "Use WinRtWindowsAppSdkDeployment. Bootstrap is only one unpackaged deployment mode; packaged apps should not bootstrap.",
    replaceWith = ReplaceWith("WinRtWindowsAppSdkDeployment"),
)
object WinRtWindowsAppSdkBootstrap {
    fun initialize(runtimeAssetsRoot: Path? = WinRtWindowsAppSdkDeployment.discoverRuntimeAssetsRoot()): WinRtWindowsAppSdkDeployment.Scope? =
        WinRtWindowsAppSdkDeployment.initializeForUnpackagedApp(runtimeAssetsRoot)

    fun discoverRuntimeAssetsRoot(): Path? =
        WinRtWindowsAppSdkDeployment.discoverRuntimeAssetsRoot()
}
