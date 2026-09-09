package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

enum class WinRTApplicationPackageIdentity {
    Packaged,
    Unpackaged,
}

internal val WinRTApplicationPackageIdentity.mddBootstrapInitializeOptions: Int
    get() = if (this == WinRTApplicationPackageIdentity.Packaged) {
        mddBootstrapInitializeOptionsOnPackageIdentityNoop
    } else {
        mddBootstrapInitializeOptionsNone
    }

enum class WinRTWindowsAppSdkDeploymentMode {
    None,
    FrameworkDependent,
    SelfContained,
    ExternallyInitialized,
}

data class WinRTWindowsAppSdkVersion(
    val majorMinorVersion: Int,
    val versionTag: String? = null,
    val minVersion: Long,
) {
    init {
        require(majorMinorVersion > 0) { "Windows App SDK major/minor version must be positive." }
        require(minVersion >= 0) { "Windows App SDK minimum runtime version must not be negative." }
    }
}

data class WinRTWindowsAppSdkDeploymentConfiguration(
    val mode: WinRTWindowsAppSdkDeploymentMode,
    val packageIdentity: WinRTApplicationPackageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
    val runtimeAssetsRoot: Path? = null,
    val windowsAppSdkVersion: WinRTWindowsAppSdkVersion? = null,
)

/** Configuration shared by generated and custom application hosts. */
data class WinRTApplicationHostConfiguration(
    val packageIdentity: WinRTApplicationPackageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
    val windowsAppSdkDeployment: WinRTWindowsAppSdkDeploymentMode = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
    val runtimeAssetsRoot: Path? = null,
    val windowsAppSdkVersion: WinRTWindowsAppSdkVersion? = null,
)

object WinRTWindowsAppSdkDeployment {
    /** Compatibility view of the historical deployment mode property. */
    enum class Mode {
        DynamicDependency,
        SelfContained,
        None,
        ExternallyInitialized,
    }

    class Scope internal constructor(
        val mode: Mode,
        val deploymentMode: WinRTWindowsAppSdkDeploymentMode,
        private val activationContexts: List<AutoCloseable>,
        @Suppress("unused")
        private val loadedModules: List<RawAddress>,
        private val shutdownDynamicDependency: Boolean,
        private val ownerThread: Long,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            // Activation-context cookies and bootstrap shutdown are thread-affine. Do this
            // check before the closed flag so a wrong-thread call cannot consume the owner.
            check(platformCurrentThreadToken() == ownerThread) {
                "Windows App SDK deployment must be closed on its creating thread."
            }
            if (closed) {
                return
            }
            closed = true
            var failure: Throwable? = null
            try {
                if (shutdownDynamicDependency) {
                    platformWindowsAppSdkBootstrapShutdown()
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                activationContexts.asReversed().forEach { context ->
                    try {
                        context.close()
                    } catch (error: Throwable) {
                        failure?.addSuppressed(error) ?: run { failure = error }
                    }
                }
                releaseOwner(this)
            }
            failure?.let { throw it }
        }
    }

    private val ownerLock = PlatformLock()
    private var ownerReserved = false
    private var activeOwner: Scope? = null

    /**
     * Acquires the process deployment owner described by [configuration]. `None` does not
     * touch Windows App SDK files; all other modes fail explicitly when their required inputs
     * are unavailable.
     */
    fun initialize(configuration: WinRTWindowsAppSdkDeploymentConfiguration): Scope? {
        if (configuration.mode == WinRTWindowsAppSdkDeploymentMode.None) {
            return null
        }
        if (!PlatformRuntime.isWindows) {
            throw IllegalStateException("Windows App SDK deployment is only supported on Windows.")
        }
        reserveOwner()
        try {
            val scope = when (configuration.mode) {
                WinRTWindowsAppSdkDeploymentMode.FrameworkDependent ->
                    initializeFrameworkDependent(configuration)
                WinRTWindowsAppSdkDeploymentMode.SelfContained ->
                    initializeSelfContained(configuration)
                WinRTWindowsAppSdkDeploymentMode.ExternallyInitialized ->
                    Scope(
                        mode = Mode.ExternallyInitialized,
                        deploymentMode = configuration.mode,
                        activationContexts = emptyList(),
                        loadedModules = emptyList(),
                        shutdownDynamicDependency = false,
                        ownerThread = platformCurrentThreadToken(),
                    )
                WinRTWindowsAppSdkDeploymentMode.None -> error("None deployment must not reserve an owner.")
            }
            ownerLock.withLock {
                activeOwner = scope
            }
            return scope
        } catch (failure: Throwable) {
            releaseReservedOwner()
            throw failure
        }
    }

    fun initialize(
        mode: WinRTWindowsAppSdkDeploymentMode,
        runtimeAssetsRoot: Path? = null,
        windowsAppSdkVersion: WinRTWindowsAppSdkVersion? = null,
    ): Scope? = initialize(
        WinRTWindowsAppSdkDeploymentConfiguration(
            mode = mode,
            runtimeAssetsRoot = runtimeAssetsRoot,
            windowsAppSdkVersion = windowsAppSdkVersion,
        ),
    )

    /**
     * Legacy helper retained for custom launchers. New hosts should pass an explicit mode via
     * [initialize]. This compatibility entry point always uses framework-dependent bootstrap;
     * self-contained launchers must select [WinRTWindowsAppSdkDeploymentMode.SelfContained]
     * explicitly so a manifest cannot silently change the deployment contract.
     */
    @Deprecated("Pass WinRTWindowsAppSdkDeploymentConfiguration to keep deployment explicit.")
    fun initializeForUnpackagedApp(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope {
        check(PlatformRuntime.isWindows) {
            "Windows App SDK deployment is only supported on Windows."
        }
        val root = runtimeAssetsRoot
            ?: error("Windows App SDK deployment requires an explicit runtime assets root.")
        return initialize(
            mode = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
            runtimeAssetsRoot = root,
        ) ?: error("Unpackaged Windows App SDK deployment unexpectedly selected None mode.")
    }

    fun discoverRuntimeAssetsRoot(): Path? =
        platformDiscoverWindowsAppSdkRuntimeAssetsRoot(windowsAppRuntimeBootstrapDllName)

    private fun initializeFrameworkDependent(
        configuration: WinRTWindowsAppSdkDeploymentConfiguration,
    ): Scope {
        val root = requireRuntimeAssetsRoot(configuration)
        val bootstrapDll = findAsset(root, windowsAppRuntimeBootstrapDllName)
            ?: error("Framework-dependent Windows App SDK deployment is missing $windowsAppRuntimeBootstrapDllName under $root.")
        val module = platformTryLoadWindowsLibrary(bootstrapDll)
            ?: error("Unable to load $windowsAppRuntimeBootstrapDllName from $bootstrapDll.")
        var bootstrapInitialized = false
        try {
            val initialize = platformTryGetWindowsProcAddress(module, "MddBootstrapInitialize2")
                ?: error("$windowsAppRuntimeBootstrapDllName does not export MddBootstrapInitialize2.")
            val versionInfo = configuration.windowsAppSdkVersion ?: discoverVersionInfo(root)
                ?: error(
                    "Framework-dependent Windows App SDK deployment requires version information. " +
                        "Provide WinRTWindowsAppSdkVersion or stage $versionInfoPropertiesRelativePath.",
                )
            platformRememberWindowsAppSdkBootstrapShutdown(module)
            platformCallMddBootstrapInitialize2(
                initialize,
                versionInfo.majorMinorVersion,
                versionInfo.versionTag,
                versionInfo.minVersion,
                options = configuration.packageIdentity.mddBootstrapInitializeOptions,
            )
            bootstrapInitialized = true
            return Scope(
                mode = Mode.DynamicDependency,
                deploymentMode = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
                activationContexts = emptyList(),
                // Keep the bootstrap module loaded for the lifetime of the process. The runtime
                // may retain function pointers after MddBootstrapShutdown.
                loadedModules = listOf(module),
                shutdownDynamicDependency = true,
                ownerThread = platformCurrentThreadToken(),
            )
        } catch (failure: Throwable) {
            if (bootstrapInitialized) {
                runCatching { platformWindowsAppSdkBootstrapShutdown() }
                    .onFailure(failure::addSuppressed)
            }
            runCatching { platformFreeWindowsLibrary(module) }
                .onFailure(failure::addSuppressed)
            platformForgetWindowsAppSdkBootstrapShutdown()
            throw failure
        }
    }

    private fun initializeSelfContained(
        configuration: WinRTWindowsAppSdkDeploymentConfiguration,
    ): Scope {
        val root = requireRuntimeAssetsRoot(configuration)
        var activationContext: AutoCloseable? = null
        var module: RawAddress? = null
        try {
            activationContext = activateWindowsAppSdk(root)
            module = loadSelfContainedWindowsAppRuntime(root)
            return Scope(
                mode = Mode.SelfContained,
                deploymentMode = WinRTWindowsAppSdkDeploymentMode.SelfContained,
                activationContexts = listOf(activationContext),
                loadedModules = listOf(module),
                shutdownDynamicDependency = false,
                ownerThread = platformCurrentThreadToken(),
            )
        } catch (failure: Throwable) {
            module?.let { loaded ->
                runCatching { platformFreeWindowsLibrary(loaded) }
                    .onFailure(failure::addSuppressed)
            }
            activationContext?.let { context ->
                runCatching { context.close() }
                    .onFailure(failure::addSuppressed)
            }
            throw failure
        }
    }

    private fun requireRuntimeAssetsRoot(
        configuration: WinRTWindowsAppSdkDeploymentConfiguration,
    ): Path {
        val root = configuration.runtimeAssetsRoot ?: discoverRuntimeAssetsRoot()
            ?: error("Windows App SDK deployment requires an explicit runtime assets root.")
        require(root.isDirectory()) { "Windows App SDK runtime assets root is not a directory: $root" }
        return root
    }

    private fun activateWindowsAppSdk(root: Path): AutoCloseable {
        val manifestPath = platformWindowsApplicationManifestPath(root)
        require(manifestPath.isRegularFile()) {
            "Self-contained Windows App SDK deployment is missing an executable manifest under $root."
        }
        val previousBaseDirectory = platformGetWindowsEnvironmentVariable(windowsAppRuntimeBaseDirectoryVariableName)
        platformSetWindowsEnvironmentVariable(
            windowsAppRuntimeBaseDirectoryVariableName,
            root.canonicalString().let { path -> if (path.endsWith("\\") || path.endsWith("/")) path else "$path\\" },
        )
        return try {
            SelfContainedActivationScope(
                activationContext = platformActivateWindowsManifest(manifestPath),
                previousBaseDirectory = previousBaseDirectory,
            )
        } catch (failure: Throwable) {
            runCatching {
                platformSetWindowsEnvironmentVariable(
                    windowsAppRuntimeBaseDirectoryVariableName,
                    previousBaseDirectory,
                )
            }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun loadSelfContainedWindowsAppRuntime(root: Path): RawAddress {
        val runtimeDll = findAsset(root, windowsAppRuntimeDllName)
            ?: error("Self-contained Windows App SDK deployment is missing $windowsAppRuntimeDllName under $root.")
        val module = platformLoadWindowsLibrary(runtimeDll)
        try {
            val ensureIsLoaded = platformTryGetWindowsProcAddress(module, "WindowsAppRuntime_EnsureIsLoaded")
                ?: error("$windowsAppRuntimeDllName does not export WindowsAppRuntime_EnsureIsLoaded.")
            platformCallWindowsAppRuntimeEnsureIsLoaded(ensureIsLoaded)
            return module
        } catch (failure: Throwable) {
            runCatching { platformFreeWindowsLibrary(module) }
                .onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun reserveOwner() {
        ownerLock.withLock {
            check(!ownerReserved && activeOwner == null) {
                "Only one active Windows App SDK deployment owner is allowed per process."
            }
            ownerReserved = true
        }
    }

    private fun releaseReservedOwner() {
        ownerLock.withLock {
            ownerReserved = false
        }
    }

    private fun releaseOwner(scope: Scope) {
        ownerLock.withLock {
            if (activeOwner === scope) {
                activeOwner = null
                ownerReserved = false
            }
        }
    }

    private fun findAsset(root: Path, fileName: String): Path? =
        root.walkFiles()
            .filter { path -> path.fileName.equals(fileName, ignoreCase = true) }
            .sortedBy { path -> path.canonicalString() }
            .firstOrNull()

    private fun discoverVersionInfo(root: Path): WinRTWindowsAppSdkVersion? {
        val properties = Path(root, versionInfoPropertiesRelativePath)
        return properties.takeIf(Path::isRegularFile)
            ?.let { versionInfo -> parseVersionProperties(versionInfo.readText()) }
    }

    private fun parseVersionProperties(content: String): WinRTWindowsAppSdkVersion? {
        val values = content.lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .toMap()
        val majorMinor = values["majorMinorVersion"]?.toIntOrNull() ?: return null
        val minVersion = values["minVersion"]?.toULongOrNull()?.toLong() ?: return null
        return WinRTWindowsAppSdkVersion(
            majorMinorVersion = majorMinor,
            versionTag = values["versionTag"]?.takeIf(String::isNotEmpty),
            minVersion = minVersion,
        )
    }

    private class SelfContainedActivationScope(
        private val activationContext: AutoCloseable,
        private val previousBaseDirectory: String?,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) {
                return
            }
            closed = true
            var failure: Throwable? = null
            try {
                activationContext.close()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                platformSetWindowsEnvironmentVariable(
                    windowsAppRuntimeBaseDirectoryVariableName,
                    previousBaseDirectory,
                )
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
            failure?.let { throw it }
        }
    }
}

internal expect fun platformDiscoverWindowsAppSdkRuntimeAssetsRoot(anchorFileName: String): Path?
internal expect fun platformWindowsApplicationManifestPath(root: Path): Path
internal expect fun platformActivateWindowsManifest(manifestPath: Path): AutoCloseable
internal expect fun platformGetWindowsEnvironmentVariable(name: String): String?
internal expect fun platformSetWindowsEnvironmentVariable(name: String, value: String?)
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
    options: Int,
)
internal expect fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress)
internal expect fun platformForgetWindowsAppSdkBootstrapShutdown()
internal expect fun platformWindowsAppSdkBootstrapShutdown()

private val versionInfoPropertiesRelativePath = "kotlin-winrt-windows-app-sdk.properties"
private const val windowsAppRuntimeBootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
private const val windowsAppRuntimeDllName = "Microsoft.WindowsAppRuntime.dll"
private const val windowsAppRuntimeBaseDirectoryVariableName = "MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY"
private const val mddBootstrapInitializeOptionsNone = 0
private const val mddBootstrapInitializeOptionsOnPackageIdentityNoop = 0x0010
