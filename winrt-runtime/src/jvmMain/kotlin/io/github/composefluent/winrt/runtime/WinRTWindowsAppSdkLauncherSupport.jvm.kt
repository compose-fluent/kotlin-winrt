package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

object WinRTWindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeApplicationHost(
        packageIdentity: String,
        deploymentMode: String,
        runtimeAssetsRoot: String,
    ): AutoCloseable =
        WinRTWindowsAppSdkBootstrap.initializeApplicationHost(
            WinRTApplicationHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = parsePackageIdentity(packageIdentity),
                windowsAppSdkDeployment = parseDeploymentMode(deploymentMode),
                runtimeAssetsRoot = Path(runtimeAssetsRoot),
            ),
        )

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
