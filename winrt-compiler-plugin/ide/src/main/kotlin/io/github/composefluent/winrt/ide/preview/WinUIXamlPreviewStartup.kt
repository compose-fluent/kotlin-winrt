package io.github.composefluent.winrt.ide.preview

/** A validated Native host can supply the first frame without booting the SDK JVM.
 * The bounded fast-path wait only reveals the fallback; it never cancels the host.
 * Cold builds retain the usable SDK artboard before project compilation starts.
 */
internal suspend fun startPreferredXamlPreview(
    wantsProjectCode: Boolean,
    projectConnected: Boolean,
    reusableProject: suspend () -> Boolean,
    startProject: () -> Unit,
    awaitProject: suspend () -> Boolean,
    startBasic: suspend () -> Unit,
) {
    if (wantsProjectCode && projectConnected) return
    com.intellij.openapi.diagnostic.Logger.getInstance("WinUIXamlPreviewStartup")
        .info("XAML designer startup requested: projectCode=$wantsProjectCode")
    var startedProject = false
    if (wantsProjectCode && reusableProject()) {
        startProject()
        startedProject = true
        if (awaitProject()) return
    }
    startBasic()
    if (wantsProjectCode && !startedProject) startProject()
}
