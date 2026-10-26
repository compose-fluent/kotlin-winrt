package io.github.composefluent.winrt.ide.run

import com.intellij.execution.process.ProcessHandler
import org.junit.Assert.*
import org.junit.Test
import java.io.OutputStream
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class WinRTDevelopmentProcessHandlerTest {
    @Test fun successfulBuildKeepsRunAliveAcrossReplacementUntilNewApplicationExits() {
        val build = Build()
        val run = WinRTDevelopmentProcessHandler(build)
        run.startNotify()
        build.finish(0)
        assertFalse(run.isProcessTerminated)
        val first = Application(1)
        run.bindProcess(first)
        run.prepareRestart()
        first.destroy()
        assertFalse(run.isProcessTerminated)
        val second = Application(2)
        run.bindProcess(second)
        second.destroy()
        assertTrue(run.isProcessTerminated)
    }

    @Test fun stopCancelsSessionAndDoesNotBecomeDisabledWhenGradleReturns() {
        val build = Build()
        val run = WinRTDevelopmentProcessHandler(build)
        run.startNotify()
        build.finish(0)
        var stops = 0
        run.stopSession = { stops++; run.complete(0) }
        run.destroyProcess()
        assertEquals(1, stops)
        assertTrue(run.isProcessTerminated)
    }

    @Test fun failedBuildTerminatesRunAndLatePublicationCannotLeaveAnOrphan() {
        val build = Build()
        val run = WinRTDevelopmentProcessHandler(build)
        run.startNotify()
        build.finish(1)
        assertTrue(run.isProcessTerminated)
        val late = Application(3)
        run.bindProcess(late)
        assertFalse(late.isAlive)
    }

    @Test fun detachAllowsTheApplicationToContinueEvenWhenItPublishesLater() {
        val build = Build()
        val run = WinRTDevelopmentProcessHandler(build)
        run.startNotify()
        build.finish(0)
        run.detachProcess()
        val application = Application(4)
        run.bindProcess(application)
        assertTrue(application.isAlive)
        assertTrue(run.isProcessTerminated)
    }

    private class Build : ProcessHandler() {
        fun finish(code: Int) = notifyProcessTerminated(code)
        override fun destroyProcessImpl() = finish(130)
        override fun detachProcessImpl() = notifyProcessDetached()
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null
    }
    private class Application(private val id: Long) : ProcessHandle {
        private var alive = true
        private val ended = CompletableFuture<ProcessHandle>()
        private val created = Instant.now()
        override fun pid() = id
        override fun isAlive() = alive
        override fun supportsNormalTermination() = true
        override fun destroy(): Boolean { alive = false; ended.complete(this); return true }
        override fun destroyForcibly() = destroy()
        override fun onExit() = ended
        override fun parent(): Optional<ProcessHandle> = Optional.empty()
        override fun children(): Stream<ProcessHandle> = Stream.empty()
        override fun descendants(): Stream<ProcessHandle> = Stream.empty()
        override fun compareTo(other: ProcessHandle) = id.compareTo(other.pid())
        override fun info() = object : ProcessHandle.Info {
            override fun startInstant() = Optional.of(created)
            override fun command(): Optional<String> = Optional.empty()
            override fun commandLine(): Optional<String> = Optional.empty()
            override fun arguments(): Optional<Array<String>> = Optional.empty()
            override fun totalCpuDuration(): Optional<java.time.Duration> = Optional.empty()
            override fun user(): Optional<String> = Optional.empty()
        }
    }
}
