package io.github.composefluent.winrt.ide.run

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Key
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** The Gradle task releases its build lock after launch. The Run session remains
 * alive across native replacements, until its current application exits or Stop
 * cancels the development session. */
class WinRTDevelopmentProcessHandler(val build: ProcessHandler) : ProcessHandler() {
    private val revision = AtomicLong()
    @Volatile private var application: ProcessHandle? = null
    @Volatile private var started: Instant? = null
    @Volatile private var detached = false
    @Volatile var stopSession: (() -> Unit)? = null

    init {
        build.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                notifyTextAvailable(event.text.orEmpty(), outputType)
            }
            override fun processTerminated(event: ProcessEvent) {
                if (event.exitCode != 0) complete(event.exitCode)
            }
        })
        if (build.isProcessTerminated && build.exitCode != 0) complete(build.exitCode ?: 1)
    }

    override fun startNotify() {
        super.startNotify()
        if (!build.isStartNotified) build.startNotify()
    }

    fun prepareRestart() {
        revision.incrementAndGet()
        application = null
        started = null
    }

    fun bindProcess(process: ProcessHandle) {
        if (detached) return
        val current = revision.incrementAndGet()
        application = process
        started = process.info().startInstant().orElse(null)
        process.onExit().thenRun { if (revision.get() == current) complete(0) }
        if (isProcessTerminating || isProcessTerminated) stopApplication()
    }

    fun complete(exitCode: Int) {
        revision.incrementAndGet()
        notifyProcessTerminated(exitCode)
    }

    /** Also works for an older Run tab after another application became selected.
     * A process handle and creation time prevent stopping a reused PID. */
    fun stopApplication() {
        val process = application ?: return
        if (process.isAlive && started != null && process.info().startInstant().orElse(null) == started) process.destroy()
    }

    override fun destroyProcessImpl() {
        if (!build.isProcessTerminated) build.destroyProcess()
        stopSession?.invoke() ?: stopApplication()
    }
    override fun detachProcessImpl() {
        detached = true
        revision.incrementAndGet()
        stopSession = null
        notifyProcessDetached()
    }
    override fun detachIsDefault() = false
    override fun getProcessInput(): OutputStream? = build.processInput
}
