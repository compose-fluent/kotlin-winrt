package io.github.composefluent.winrt.runtime

object WinRTWindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeForUnpackagedApp(): AutoCloseable =
        WinRTWindowsAppSdkDeployment.initializeForUnpackagedApp()

    @JvmStatic
    fun initializeApplicationHost(packageIdentity: String, deploymentMode: String): AutoCloseable =
        WinRTWindowsAppSdkBootstrap.initializeApplicationHost(
            WinRTApplicationHostConfiguration(
                packageIdentity = parsePackageIdentity(packageIdentity),
                windowsAppSdkDeployment = parseDeploymentMode(deploymentMode),
            ),
        )

    /** Compatibility entry point for older native launchers. */
    @JvmStatic
    @Deprecated("Use the package identity/deployment mode overload.")
    fun initializeApplicationHost(unpackaged: Boolean): AutoCloseable =
        WinRTWindowsAppSdkBootstrap.initializeApplicationHost(unpackaged)

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }

    private fun parsePackageIdentity(value: String): WinRTApplicationPackageIdentity =
        runCatching { WinRTApplicationPackageIdentity.valueOf(value) }
            .getOrElse { error("Unknown WinRT application package identity '$value'.") }

    private fun parseDeploymentMode(value: String): WinRTWindowsAppSdkDeploymentMode =
        runCatching { WinRTWindowsAppSdkDeploymentMode.valueOf(value) }
            .getOrElse { error("Unknown Windows App SDK deployment mode '$value'.") }
}
