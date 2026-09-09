package io.github.composefluent.winrt.runtime

/**
 * Explicit Windows App SDK deployment bootstrap for custom unpackaged launchers.
 *
 * Generated application hosts create a broader application-host scope before user code starts.
 * Projection constructors and `Application.Start` remain ordinary WinRT calls.
 */
object WinRTWindowsAppSdkBootstrap {
    @Deprecated("Pass WinRTWindowsAppSdkDeploymentConfiguration to keep deployment explicit.")
    fun initialize(): AutoCloseable? =
        WinRTWindowsAppSdkDeployment.initializeForUnpackagedApp()

    fun initializeApplicationHost(configuration: WinRTApplicationHostConfiguration): WinRTApplicationHostScope.Scope =
        WinRTApplicationHostScope.initialize(configuration)

    @Deprecated("Pass WinRTApplicationHostConfiguration to keep package identity and deployment explicit.")
    fun initializeApplicationHost(unpackaged: Boolean = true): AutoCloseable =
        WinRTApplicationHostScope.initialize(unpackaged)
}
