package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

object WinRtWindowsAppSdkDeployment {
    enum class Mode {
        DynamicDependency,
        SelfContained,
    }

    class Scope internal constructor(
        val mode: Mode,
        private val activationContexts: List<AutoCloseable>,
        @Suppress("unused")
        private val loadedModules: List<RawAddress>,
        private val shutdownDynamicDependency: Boolean,
    ) : AutoCloseable {
        override fun close() {
            if (shutdownDynamicDependency) {
                platformWindowsAppSdkBootstrapShutdown()
            }
            activationContexts.asReversed().forEach { it.close() }
        }
    }

    private var processScope: Scope? = null

    fun initializeForUnpackagedApp(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        processScope?.let { return it }
        val root = runtimeAssetsRoot ?: return null
        val activationContext = activateWindowsAppSdk(root)
        if (activationContext != null) {
            val module = loadSelfContainedWindowsAppRuntime(root)
            return Scope(
                mode = Mode.SelfContained,
                activationContexts = listOf(activationContext),
                loadedModules = listOfNotNull(module),
                shutdownDynamicDependency = false,
            ).also { processScope = it }
        }
        return initializeDynamicDependency(root)
            ?.also { processScope = it }
    }

    fun discoverRuntimeAssetsRoot(): Path? =
        platformDiscoverWindowsAppSdkRuntimeAssetsRoot(windowsAppRuntimeBootstrapDllName)

    private fun activateWindowsAppSdk(root: Path): AutoCloseable? {
        if (!root.isDirectory()) {
            return null
        }
        val manifestPath = platformWindowsApplicationManifestPath(root)
        if (!manifestPath.isRegularFile()) {
            return null
        }
        platformSetWindowsEnvironmentVariable(
            windowsAppRuntimeBaseDirectoryVariableName,
            root.canonicalString().let { path -> if (path.endsWith("\\") || path.endsWith("/")) path else "$path\\" },
        )
        return platformActivateWindowsManifest(manifestPath)
    }

    private fun loadSelfContainedWindowsAppRuntime(root: Path): RawAddress? {
        val runtimeDll = findAsset(root, windowsAppRuntimeDllName) ?: return null
        val module = platformLoadWindowsLibrary(runtimeDll)
        val ensureIsLoaded = platformGetWindowsProcAddress(module, "WindowsAppRuntime_EnsureIsLoaded")
        platformCallWindowsAppRuntimeEnsureIsLoaded(ensureIsLoaded)
        return module
    }

    private fun initializeDynamicDependency(root: Path): Scope? {
        val bootstrapDll = findAsset(root, windowsAppRuntimeBootstrapDllName) ?: return null
        val module = platformTryLoadWindowsLibrary(bootstrapDll) ?: return null
        val initialize = platformTryGetWindowsProcAddress(module, "MddBootstrapInitialize2")
        if (initialize == null) {
            platformFreeWindowsLibrary(module)
            return null
        }
        val versionInfo = discoverVersionInfo(root) ?: BootstrapVersionInfo(
            majorMinorVersion = defaultWindowsAppSdkMajorMinorVersion,
            versionTag = null,
            minVersion = defaultWindowsAppSdkMinVersion,
        )
        platformCallMddBootstrapInitialize2(
            initialize,
            versionInfo.majorMinorVersion,
            versionInfo.versionTag,
            versionInfo.minVersion,
        )
        platformRememberWindowsAppSdkBootstrapShutdown(module)
        return Scope(
            mode = Mode.DynamicDependency,
            activationContexts = emptyList(),
            loadedModules = listOf(module),
            shutdownDynamicDependency = true,
        )
    }

    private fun findAsset(root: Path, fileName: String): Path? =
        root.walkFiles()
            .filter { path -> path.fileName.equals(fileName, ignoreCase = true) }
            .sortedBy { path -> path.canonicalString() }
            .firstOrNull()

    private fun discoverVersionInfo(root: Path): BootstrapVersionInfo? {
        val header = root.parentSequence()
            .take(5)
            .map { path -> Path(path, versionInfoHeaderRelativePath) }
            .firstOrNull { path -> path.isRegularFile() }
            ?: return null
        val content = header.readText()
        val majorMinor = releaseMajorMinorRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.toInt(16)
            ?: return null
        val versionTag = releaseVersionTagRegex.find(content)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        val minVersion = runtimeVersionRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.removeSuffix("u")
            ?.toULong(16)
            ?.toLong()
            ?: return null
        return BootstrapVersionInfo(majorMinor, versionTag, minVersion)
    }

    private fun Path.parentSequence(): Sequence<Path> = sequence {
        var current: Path? = this@parentSequence
        while (current != null) {
            yield(current)
            current = current.parentPath()
        }
    }

    private fun Path.parentPath(): Path? {
        val text = toString().trimEnd('\\', '/')
        val index = maxOf(text.lastIndexOf('\\'), text.lastIndexOf('/'))
        return if (index <= 0) null else Path(text.substring(0, index))
    }
}

internal expect fun platformDiscoverWindowsAppSdkRuntimeAssetsRoot(anchorFileName: String): Path?
internal expect fun platformWindowsApplicationManifestPath(root: Path): Path
internal expect fun platformActivateWindowsManifest(manifestPath: Path): AutoCloseable
internal expect fun platformSetWindowsEnvironmentVariable(name: String, value: String)
internal expect fun platformTryLoadWindowsLibrary(path: Path): RawAddress?
internal expect fun platformLoadWindowsLibrary(path: Path): RawAddress
internal expect fun platformFreeWindowsLibrary(module: RawAddress)
internal expect fun platformTryGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress?
internal expect fun platformGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress
internal expect fun platformCallWindowsAppRuntimeEnsureIsLoaded(procedure: RawAddress)
internal expect fun platformCallMddBootstrapInitialize2(
    procedure: RawAddress,
    majorMinorVersion: Int,
    versionTag: String?,
    minVersion: Long,
)
internal expect fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress)
internal expect fun platformWindowsAppSdkBootstrapShutdown()

private data class BootstrapVersionInfo(
    val majorMinorVersion: Int,
    val versionTag: String?,
    val minVersion: Long,
)
private val releaseMajorMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJORMINOR\s+(0x[0-9A-Fa-f]+)""")
private val releaseVersionTagRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_VERSION_TAG_W\s+L"([^"]*)"""")
private val runtimeVersionRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_VERSION_UINT64\s+(0x[0-9A-Fa-f]+)u""")

private const val defaultWindowsAppSdkMajorMinorVersion: Int = 0x00010008
private const val defaultWindowsAppSdkMinVersion: Long = 0x1F40032608CC0000L
private const val versionInfoHeaderRelativePath = "include/WindowsAppSDK-VersionInfo.h"
private const val windowsAppRuntimeBootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
private const val windowsAppRuntimeDllName = "Microsoft.WindowsAppRuntime.dll"
private const val windowsAppRuntimeBaseDirectoryVariableName = "MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY"
