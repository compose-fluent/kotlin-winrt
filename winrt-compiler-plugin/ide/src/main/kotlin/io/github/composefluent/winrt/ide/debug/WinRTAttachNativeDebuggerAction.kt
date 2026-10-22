package io.github.composefluent.winrt.ide.debug

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.settings.WinRTIdeSettings
import java.nio.file.Files
import java.nio.file.Path

/** Attach uses the authenticated development session's process, including AppContainer apps. */
class WinRTAttachNativeDebuggerAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        val project = event.project
        val pid = project?.service<WinUIXamlHotReloadService>()?.state?.value?.pid
        event.presentation.isEnabled = pid != null && !WinRTNativeDebugOwnership.contains(pid)
    }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            val development = project.service<WinUIXamlHotReloadService>()
            val state = development.state.value
            val pid = requireNotNull(state.pid) { "Run a Native application with XAML development support first." }
            val saved = development.getState()
            val module = project.service<WinRTProjectService>().modules.value.singleOrNull { it.projectDirectory == saved.moduleDirectory }
                ?: error("Sync the application project before attaching the Native debugger.")
            val launch = module.hotReloadLaunches.singleOrNull { it.taskName == saved.taskName }
                ?: error("The application's development launch is no longer available. Sync Gradle and run it again.")
            require(launch.nativeCompilerHome.isNotBlank()) { "This application uses the JVM. Use its normal JVM Debug configuration." }
            val process = ProcessHandle.of(pid).orElseThrow { IllegalStateException("The application has exited.") }
            require(process.isAlive && process.info().startInstant().orElse(null)?.toString() == saved.startedUtc) { "The development process has changed. Run the application again." }
            val executable = Path.of(process.info().command().orElseThrow { IllegalStateException("Cannot read the Native executable path.") })
            val lldb = findLldb(service<WinRTIdeSettings>().nativeDebuggerExecutable)
                ?: error("Set a Windows LLDB executable in Settings | Tools | Kotlin WinRT, or install an Android NDK with LLDB.")
            val konan = Path.of(launch.nativeCompilerHome)
            require(Files.isRegularFile(konan.resolve("tools/konan_lldb.py"))) { "The project's Kotlin/Native debugger formatters are missing. Build its Native target first." }
            XDebuggerManager.getInstance(project).startSessionAndShowTab("Kotlin WinRT Native ($pid)", null,
                object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess = WinRTNativeDebugProcess(session, lldb, konan,
                        target = { WinRTNativeDebugTarget(pid, executable, process.info().startInstant().orElseThrow()) }, sourceDirectory = Path.of(module.projectDirectory))
                })
        } catch (error: Exception) {
            Messages.showErrorDialog(project, error.message ?: "Cannot attach the Native debugger.", "Kotlin WinRT Native Debugger")
        }
    }

    companion object {
        internal fun findLldb(configured: String): Path? {
            if (configured.isNotBlank()) return Path.of(configured).takeIf(Files::isRegularFile)
            for (variable in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
                val sdk = System.getenv(variable)?.let(Path::of) ?: continue
                val ndks = sdk.resolve("ndk")
                if (!Files.isDirectory(ndks)) continue
                val candidate = Files.list(ndks).use { directories -> directories.map {
                    it.resolve("toolchains/llvm/prebuilt/windows-x86_64/bin/lldb.exe")
                }.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).findFirst().orElse(null) }
                if (candidate != null) return candidate
            }
            return null
        }
    }
}
