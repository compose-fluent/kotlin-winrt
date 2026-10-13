package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

/** JNI entry points shared by desktop and AppContainer application launchers. */
object WinAppLauncherSupport {
    @JvmStatic
    fun initializeApplicationHost(runtimeAssetsRoot: String): AutoCloseable =
        WinAppHostScope.initialize(WinAppHostConfiguration.fromStagedRuntimeAssets(Path(runtimeAssetsRoot)))

    @JvmStatic
    fun close(scope: AutoCloseable?) {
        scope?.close()
    }
}
