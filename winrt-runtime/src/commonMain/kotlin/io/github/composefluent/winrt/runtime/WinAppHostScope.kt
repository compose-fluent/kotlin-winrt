package io.github.composefluent.winrt.runtime

/**
 * Owns the process-level Windows application environment and its entry-thread apartment.
 *
 * An application host is intentionally unique per process because its close operation drains
 * global projection state. Ordinary [RuntimeScope] instances remain nestable and independent.
 */
object WinAppHostScope {
    class Scope internal constructor(
        private val runtime: RuntimeScope,
        private val deployment: WindowsAppSdkDeployment.Scope?,
        private val ownerThread: Long,
    ) : AutoCloseable {
        private var closed = false

        override fun close() {
            check(platformCurrentThreadToken() == ownerThread) {
                "WinApp host must be closed on its creating thread."
            }
            if (closed) {
                return
            }
            closed = true
            var failure: Throwable? = null
            try {
                // Global caches and composable references must be drained while the apartment and
                // any Windows App SDK dynamic dependency are still active.
                PlatformRuntimeInitialization.cleanupApplicationHostRuntime()
            } catch (error: Throwable) {
                failure = error
            }
            try {
                runtime.close()
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
            try {
                deployment?.close()
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
            releaseHost(this)
            failure?.let { throw it }
        }
    }

    private val ownerLock = PlatformLock()
    private var ownerReserved = false
    private var activeOwner: Scope? = null

    fun initialize(configuration: WinAppHostConfiguration): Scope {
        if (!PlatformRuntime.isWindows) {
            throw IllegalStateException("WinApp hosts are only supported on Windows.")
        }
        reserveHost()
        var deployment: WindowsAppSdkDeployment.Scope? = null
        var runtime: RuntimeScope? = null
        try {
            // Corresponds to CsWinRT's host-selected UseWindowsUIXamlProjections switch.
            // Select before any projection initializer can publish framework-specific IIDs.
            XamlRuntime.initialize(configuration.xamlFramework)
            deployment = WindowsAppSdkDeployment.initialize(
                WindowsAppSdkDeploymentConfiguration(
                    mode = configuration.windowsAppSdkDeployment,
                    packageIdentity = configuration.packageIdentity,
                    runtimeAssetsRoot = configuration.runtimeAssetsRoot,
                    windowsAppSdkVersion = configuration.windowsAppSdkVersion,
                ),
            )
            // Microsoft's XamlCompiler CppWinRT_AppPass2 initializes desktop entries as
            // STA and UWP entries as MTA; UWP Application.Start owns the view's UI thread.
            // Select by application model: a desktop XAML island may also use system XAML.
            val acquiredRuntime = when (configuration.applicationModel) {
                WinAppApplicationModel.Desktop -> RuntimeScope.initializeSingleThreaded()
                WinAppApplicationModel.UWP -> RuntimeScope.initializeMultithreaded()
            }
            runtime = acquiredRuntime
            val scope = Scope(
                runtime = acquiredRuntime,
                deployment = deployment,
                ownerThread = platformCurrentThreadToken(),
            )
            ownerLock.withLock {
                activeOwner = scope
            }
            return scope
        } catch (failure: Throwable) {
            // Initialization has not transferred ownership to a Scope yet. Release every
            // partially acquired resource and make a later host attempt possible.
            runtime?.let { acquired ->
                runCatching { acquired.close() }
                    .onFailure(failure::addSuppressed)
            }
            deployment?.let { acquired ->
                runCatching { acquired.close() }
                    .onFailure(failure::addSuppressed)
            }
            releaseReservedHost()
            throw failure
        }
    }

    private fun reserveHost() {
        ownerLock.withLock {
            check(!ownerReserved && activeOwner == null) {
                "Only one active WinApp host is allowed per process."
            }
            ownerReserved = true
        }
    }

    private fun releaseReservedHost() {
        ownerLock.withLock {
            ownerReserved = false
        }
    }

    private fun releaseHost(scope: Scope) {
        ownerLock.withLock {
            if (activeOwner === scope) {
                activeOwner = null
                ownerReserved = false
            }
        }
    }
}
