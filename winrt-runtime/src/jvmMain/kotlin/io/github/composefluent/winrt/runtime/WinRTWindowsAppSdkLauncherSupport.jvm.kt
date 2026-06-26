package io.github.composefluent.winrt.runtime

object WinRTWindowsAppSdkLauncherSupport {
    @JvmStatic
    fun initializeForUnpackagedApp(): AutoCloseable? =
        WinRTWindowsAppSdkDeployment.initializeForUnpackagedApp()

    @JvmStatic
    fun initializeApplicationHost(unpackaged: Boolean): AutoCloseable =
        WinRTApplicationHostScope.initialize(unpackaged)

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }
}
