package io.github.composefluent.winrt.ide.debug

import com.google.gson.JsonObject
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadService
import io.github.composefluent.winrt.ide.hotreload.WinRTStaticPreviewService
import com.intellij.openapi.components.service
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class WinRTNativeBreakpointProperties : XBreakpointProperties<WinRTNativeBreakpointProperties>() {
    override fun getState() = this
    override fun loadState(state: WinRTNativeBreakpointProperties) = Unit
}

class WinRTNativeLineBreakpointType : XLineBreakpointType<WinRTNativeBreakpointProperties>("kotlin-winrt-native", "Kotlin WinRT Native Line") {
    override fun createBreakpointProperties(file: VirtualFile, line: Int) = WinRTNativeBreakpointProperties()
    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean = file.extension == "kt" &&
        project.service<WinRTProjectService>().modules.value.any { module ->
            module.targets.any { it.platform.contains("native", true) || it.name.contains("mingw", true) } &&
                module.sourceSets.flatMap { it.kotlinRoots }.any { FileUtil.isAncestor(it, file.path, false) }
        }
}

internal class WinRTNativeDebugProcess(
    session: XDebugSession, private val lldb: Path, private val konanHome: Path,
    private val executable: Path, private val pid: Long,
) : XDebugProcess(session) {
    private val ready = CompletableFuture<WinRTNativeDebuggerBackend>()
    private val stopped = AtomicBoolean()
    private val breakpoints = ConcurrentHashMap<XLineBreakpoint<WinRTNativeBreakpointProperties>, Int>()
    private val registrations = ConcurrentHashMap<XLineBreakpoint<WinRTNativeBreakpointProperties>, Any>()
    private var activeThread = 0L
    private val handler = object : ProcessHandler() {
        override fun destroyProcessImpl() = stop()
        override fun detachProcessImpl() = stop()
        override fun detachIsDefault() = true
        override fun getProcessInput(): OutputStream? = null
        fun finished() { if (!isProcessTerminated) notifyProcessDetached() }
    }

    override fun doGetProcessHandler(): ProcessHandler = handler
    override fun getEditorsProvider() = object : XDebuggerEditorsProvider() {
        override fun getFileType(): FileType = PlainTextFileType.INSTANCE
        override fun createDocument(project: Project, text: String, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document =
            EditorFactory.getInstance().createDocument(text)
    }

    override fun sessionInitialized() {
        session.setPauseActionSupported(true)
        AppExecutorUtil.getAppExecutorService().execute {
            var backend: WinRTNativeDebuggerBackend? = null
            try {
                val connected = WinRTNativeDebuggerBackend.start(lldb, ::onEvent) { text ->
                    ui { session.consoleView.print("$text\n", ConsoleViewContentType.SYSTEM_OUTPUT) }
                }
                backend = connected
                connected.request("configure", "konanHome" to konanHome.toString(), "executable" to executable.toString()).join()
                if (stopped.get()) { connected.close(); ready.completeExceptionally(IllegalStateException("Debug session closed.")); return@execute }
                // Queued breakpoint registrations are written before attaching.
                ready.complete(connected)
                session.project.service<WinUIXamlHotReloadService>().suspendForDebugger(pid).join()
                session.project.service<WinRTStaticPreviewService>().suspendForDebugger(pid).join()
                connected.request("attach", "pid" to pid).exceptionally { failure(it); stop(); null }
            } catch (error: Exception) { backend?.close(); ready.completeExceptionally(error); failure(error); stop() }
        }
    }

    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(object : XBreakpointHandler<XLineBreakpoint<WinRTNativeBreakpointProperties>>(WinRTNativeLineBreakpointType::class.java) {
        override fun registerBreakpoint(breakpoint: XLineBreakpoint<WinRTNativeBreakpointProperties>) {
            val file = breakpoint.sourcePosition?.file ?: return
            val registration = Any()
            registrations[breakpoint] = registration
            breakpoints.remove(breakpoint)?.let { request("removeBreakpoint", "breakpoint" to it) }
            request("breakpoint", "file" to file.path, "line" to breakpoint.line + 1).thenAccept { result ->
                if (registrations[breakpoint] === registration && breakpoint.isEnabled) {
                    breakpoints[breakpoint] = result["id"].asInt
                    ui {
                        if (result["locations"].asInt > 0) session.setBreakpointVerified(breakpoint)
                        else session.setBreakpointInvalid(breakpoint, "No native code exists at this source line in the current executable.")
                    }
                } else request("removeBreakpoint", "breakpoint" to result["id"].asInt)
            }
        }
        override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<WinRTNativeBreakpointProperties>, temporary: Boolean) {
            registrations.remove(breakpoint)
            breakpoints.remove(breakpoint)?.let { request("removeBreakpoint", "breakpoint" to it) }
        }
    })

    private fun request(command: String, vararg fields: Pair<String, Any?>): CompletableFuture<JsonObject> =
        ready.thenCompose { it.request(command, *fields) }.whenComplete { _, error -> if (error != null && !stopped.get()) failure(error) }

    override fun startPausing() { request("pause") }
    override fun resume(context: XSuspendContext?) { request("resume") }
    private fun selectedThread() = (session.currentStackFrame as? NativeFrame)?.stack?.thread ?: activeThread
    override fun startStepOver(context: XSuspendContext?) { request("stepOver", "thread" to selectedThread()) }
    override fun startStepInto(context: XSuspendContext?) { request("stepInto", "thread" to selectedThread()) }
    override fun startStepOut(context: XSuspendContext?) { request("stepOut", "thread" to selectedThread()) }
    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        request("breakpoint", "file" to position.file.path, "line" to position.line + 1, "once" to true)
            .thenCompose {
                if (it["locations"].asInt == 0) {
                    request("removeBreakpoint", "breakpoint" to it["id"].asInt)
                    error("No native code exists at the requested source line.")
                }
                request("resume")
            }.exceptionally { failure(it); null }
    }
    override fun stop() {
        if (stopped.compareAndSet(false, true)) {
            ready.thenAccept { backend -> backend.request("detach").whenComplete { _, _ -> backend.close() } }
            ui { handler.finished() }
        }
    }

    private fun onEvent(event: JsonObject) {
        when (event["event"].asString) {
            "stopped" -> {
                activeThread = event["thread"].asLong
                val generation = event["generation"].asInt
                val stacks = event.getAsJsonArray("threads").map { value ->
                    val thread = value.asJsonObject
                    NativeStack(thread["id"].asLong, thread["name"].asString, generation)
                }
                val active = stacks.firstOrNull { it.thread == activeThread } ?: return
                active.load(0).thenAccept {
                    ui {
                        if (!stopped.get()) session.positionReached(object : XSuspendContext() {
                            override fun getActiveExecutionStack(): XExecutionStack = active
                            override fun getExecutionStacks(): Array<XExecutionStack> = stacks.toTypedArray()
                        })
                    }
                }
            }
            "running" -> ui { if (!stopped.get()) session.sessionResumed() }
            "output" -> ui { session.consoleView.print(event["text"].asString, if (event["stream"].asString == "stderr") ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT) }
            "error" -> failure(IllegalStateException(event["message"].asString))
            "terminated", "closed" -> {
                stopped.set(true)
                ui {
                    session.project.service<WinUIXamlHotReloadService>().resumeAfterDebugger(pid)
                    session.project.service<WinRTStaticPreviewService>().resumeAfterDebugger(pid)
                    handler.finished()
                }
            }
        }
    }

    private inner class NativeStack(val thread: Long, name: String, val generation: Int) : XExecutionStack(name) {
        private var top: NativeFrame? = null
        fun load(start: Int) = request("stack", "thread" to thread, "start" to start).thenApply { result ->
            result.getAsJsonArray("frames").map { NativeFrame(this, it.asJsonObject) }.also { if (start == 0) top = it.firstOrNull() }
        }
        override fun getTopFrame(): XStackFrame? = top
        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
            load(firstFrameIndex).thenAccept { if (!container.isObsolete) container.addStackFrames(it, it.size < 100) }
                .exceptionally { container.errorOccurred(it.message ?: "Cannot read native frames"); null }
        }
    }

    private inner class NativeFrame(val stack: NativeStack, val data: JsonObject) : XStackFrame() {
        val index = data["index"].asInt
        fun fields() = arrayOf("thread" to stack.thread, "frame" to index, "generation" to stack.generation)
        override fun getEqualityObject(): Any = Triple(stack.thread, index, stack.generation)
        override fun getSourcePosition(): XSourcePosition? {
            val file = LocalFileSystem.getInstance().findFileByPath(data["file"].asString.replace('\\', '/')) ?: return null
            val line = data["line"].asInt
            return if (line > 0) XDebuggerUtil.getInstance().createPosition(file, line - 1) else null
        }
        override fun customizePresentation(component: ColoredTextContainer) {
            component.append(data["name"].asString, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            component.setIcon(AllIcons.Debugger.Frame)
        }
        override fun computeChildren(node: XCompositeNode) {
            request("variables", *fields()).thenAccept { addValues(node, this, it) }
                .exceptionally { node.setErrorMessage(it.message ?: "Cannot read native variables"); null }
        }
    }

    private fun addValues(node: XCompositeNode, frame: NativeFrame, result: JsonObject) {
        if (node.isObsolete) return
        val values = XValueChildrenList()
        result.getAsJsonArray("values").forEach { entry ->
            val value = entry.asJsonObject
            values.add(value["name"].asString.removePrefix("*"), NativeValue(frame, value))
        }
        node.addChildren(values, true)
    }
    private inner class NativeValue(val frame: NativeFrame, val data: JsonObject) : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(AllIcons.Debugger.Value, data["type"].asString, data["value"].asString, data["children"].asBoolean)
        }
        override fun computeChildren(node: XCompositeNode) {
            request("children", *frame.fields(), "value" to data["id"].asInt).thenAccept { addValues(node, frame, it) }
                .exceptionally { node.setErrorMessage(it.message ?: "Cannot read native fields"); null }
        }
    }

    private fun failure(error: Throwable) = ui { session.reportError(error.cause?.message ?: error.message ?: "Native debugging failed.") }
    private fun ui(action: () -> Unit) { ApplicationManager.getApplication().invokeLater { if (!session.project.isDisposed) action() } }
}
