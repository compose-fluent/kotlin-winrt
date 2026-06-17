package io.github.composefluent.winrt.runtime

/**
 * Explicit Windows App SDK deployment bootstrap for custom unpackaged launchers.
 *
 * Generated application hosts create a broader application-host scope before user code starts.
 * Projection constructors and `Application.Start` remain ordinary WinRT calls.
 */
object WinRtWindowsAppSdkBootstrap {
    fun initialize(): AutoCloseable? =
        WinRtWindowsAppSdkDeployment.initializeForUnpackagedApp()

    fun initializeApplicationHost(unpackaged: Boolean = true): AutoCloseable =
        WinRtApplicationHostScope.initialize(unpackaged)
}
