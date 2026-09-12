package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

object WindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeApplicationHost(
        packageIdentity: String,
        deploymentMode: String,
        runtimeAssetsRoot: String,
    ): AutoCloseable =
        WindowsAppSdkBootstrap.initializeApplicationHost(
            WinAppHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = parsePackageIdentity(packageIdentity),
                windowsAppSdkDeployment = parseDeploymentMode(deploymentMode),
                runtimeAssetsRoot = Path(runtimeAssetsRoot),
            ),
        )

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }

    private fun parsePackageIdentity(value: String): WinAppPackageIdentity =
        runCatching { WinAppPackageIdentity.valueOf(value) }
            .getOrElse { error("Unknown WinApp package identity '$value'.") }

    private fun parseDeploymentMode(value: String): WindowsAppSdkDeploymentMode =
        runCatching { WindowsAppSdkDeploymentMode.valueOf(value) }
            .getOrElse { error("Unknown Windows App SDK deployment mode '$value'.") }
}
