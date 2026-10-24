package io.github.composefluent.winrt.ide.preview

/** A previously built Native host can supply the first frame without booting the SDK JVM.
 * The bounded fast-path wait only reveals the fallback; it never cancels the host.
 * Cold builds retain the usable SDK artboard before project compilation starts.
 */
internal suspend fun startPreferredXamlPreview(
    wantsProjectCode: Boolean,
    projectConnected: Boolean,
    projectCandidate: suspend () -> Boolean,
    startProject: () -> Unit,
    awaitProject: suspend () -> Boolean,
    startBasic: suspend () -> Unit,
    continueStartup: () -> Boolean = { true },
) {
    if (!continueStartup() || wantsProjectCode && projectConnected) return
    com.intellij.openapi.diagnostic.Logger.getInstance("WinUIXamlPreviewStartup")
        .info("XAML designer startup requested: projectCode=$wantsProjectCode")
    var startedProject = false
    if (wantsProjectCode && projectCandidate()) {
        if (!continueStartup()) return
        startProject()
        startedProject = true
        if (awaitProject()) return
    }
    if (!continueStartup()) return
    startBasic()
    if (continueStartup() && wantsProjectCode && !startedProject) startProject()
}
