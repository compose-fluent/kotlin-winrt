package io.github.composefluent.winrt.ide.preview

/** Project previews own the build queue until they connect or fail. A slow build
 * is not a failure: starting the SDK compiler alongside it delays the first frame.
 */
internal suspend fun startPreferredXamlPreview(
    wantsProjectCode: Boolean,
    projectConnected: Boolean,
    startProject: () -> Unit,
    awaitProject: suspend () -> Boolean,
    startBasic: suspend () -> Unit,
    continueStartup: () -> Boolean = { true },
) {
    if (!continueStartup() || wantsProjectCode && projectConnected) return
    com.intellij.openapi.diagnostic.Logger.getInstance("WinUIXamlPreviewStartup")
        .info("XAML designer startup requested: projectCode=$wantsProjectCode")
    if (wantsProjectCode) {
        startProject()
        if (awaitProject()) return
    }
    if (continueStartup()) startBasic()
}
