package io.github.composefluent.winrt.runtime

object WinRtWindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeForUnpackagedApp(): AutoCloseable? =
        WinRtWindowsAppSdkDeployment.initializeForUnpackagedApp()

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }
}
