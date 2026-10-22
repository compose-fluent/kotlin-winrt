package io.github.composefluent.winrt.ide.debug

import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.*
import io.github.composefluent.winrt.ide.run.WinRTApplicationRunConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CancellationException

internal data class WinRTNativeDebugTarget(val pid: Long, val executable: Path, val started: Instant,
    val startupDirectory: Path? = null, val token: String = "")

internal data class WinRTNativeDebugLaunch(val directory: Path, val executable: Path, val konanHome: Path, val lldb: Path, val sourceDirectory: Path? = null) {
    companion object { val KEY = Key.create<WinRTNativeDebugLaunch>("KotlinWinRT.NativeDebugLaunch") }

    fun awaitTarget(cancelled: () -> Boolean): WinRTNativeDebugTarget {
        val file = directory.resolve("debug.json")
        while (!Files.isRegularFile(file)) {
            if (cancelled()) throw CancellationException("Native debug launch was cancelled before the application started.")
            Thread.sleep(50)
        }
        require(!Files.isSymbolicLink(file) && Files.size(file) <= 16 * 1024) { "Invalid Native debug startup descriptor." }
        val descriptor = JsonParser.parseString(Files.readString(file)).asJsonObject
        val pid = descriptor["pid"].asLong
        val process = ProcessHandle.of(pid).orElseThrow { IllegalStateException("Native application exited before debugger attach.") }
        val actualExecutable = Path.of(process.info().command().orElseThrow())
        val recordedExecutable = Path.of(descriptor["executable"].asString)
        require(Files.isSameFile(executable, actualExecutable) && Files.isSameFile(recordedExecutable, actualExecutable)) { "Native debug process does not match the selected application." }
        val started = process.info().startInstant().orElseThrow()
        require(process.isAlive && kotlin.math.abs(started.toEpochMilli() - descriptor["startedMillis"].asLong) <= 1) { "Native debug process lifetime changed." }
        val token = descriptor["token"].asString
        require(token.matches(Regex("[0-9a-f]{32}"))) { "Invalid Native startup handshake." }
        return WinRTNativeDebugTarget(pid, actualExecutable, started, directory, token)
    }

    fun cancel() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("cancel"), "cancel")
    }
}

/** Build with Gradle's Run state; the executable, not the Gradle JVM, is debugged. */
class WinRTNativeDebugRunner : GenericProgramRunner<RunnerSettings>() {
    override fun getRunnerId() = "KotlinWinRTNativeDebug"
    override fun canRun(executorId: String, profile: RunProfile): Boolean = executorId == DefaultDebugExecutor.EXECUTOR_ID &&
        (profile as? WinRTApplicationRunConfiguration)?.developmentLaunch()?.second?.nativeCompilerHome?.isNotBlank() == true

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor {
        val launch = environment.getUserData(WinRTNativeDebugLaunch.KEY) ?: throw ExecutionException("Native debug startup was not configured.")
        val execution = state.execute(DefaultRunExecutor.getRunExecutorInstance(), this) ?: throw ExecutionException("Gradle did not start the Native application build.")
        try {
            val session = XDebuggerManager.getInstance(environment.project).startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess = WinRTNativeDebugProcess(
                    session, launch.lldb, launch.konanHome,
                    target = { cancelled -> launch.awaitTarget { cancelled() || execution.processHandler.isProcessTerminated } },
                    launchExecution = execution, cancelLaunch = launch::cancel,
                    sourceDirectory = launch.sourceDirectory,
                )
            })
            val descriptor = session.runContentDescriptor
            (state as? com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState)?.setContentDescriptor(descriptor)
            return descriptor
        } catch (error: Exception) {
            launch.cancel()
            execution.processHandler.destroyProcess()
            throw error
        }
    }
}
