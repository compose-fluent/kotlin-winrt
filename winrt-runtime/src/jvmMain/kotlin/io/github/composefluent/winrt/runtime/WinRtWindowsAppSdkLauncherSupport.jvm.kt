package io.github.composefluent.winrt.runtime

object WinRtWindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeForUnpackagedApp(): AutoCloseable? =
        WinRtWindowsAppSdkDeployment.initializeForUnpackagedApp()

    @JvmStatic
    fun initializeApplicationHost(unpackaged: Boolean): AutoCloseable =
        WinRtApplicationHostScope.initialize(unpackaged)

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }
}
