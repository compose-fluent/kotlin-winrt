package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTHotReloadLaunchData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Native project service and workspace serialization, using the actual wire
 * client against a controlled peer. These validate lifecycle/transport behavior,
 * not native WinUI application mutation or full desktop restart acceptance. */
class WinRTHotReloadServiceTest : BasePlatformTestCase() {
    private val markup = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><TextBlock x:Name="Greeting" Text="Hello"/></Window>"""

    fun testIdeLaunchAttachesWithoutAnEarlierToolWindowSessionAndAutomaticallyAppliesEditorChanges() = session { fixture ->
        val service = fixture.service(WinRTHotReloadLaunchState())
        service.attach(fixture.module, fixture.module.hotReloadLaunches.single(), fixture.directory)
        fixture.connected(service)
        assertTrue(service.automatic.value)
        assertEquals("probe.Window", service.state.value.inspectionRoots.single().className)
        assertEquals(fixture.directory.toString(), service.getState().sessionDirectory)
        val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture.source)!!
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText(markup.replace("Hello", "Edited after IDE Run"))
        }
        PlatformTestUtil.waitWithEventsDispatching("Default automatic Hot Reload sends editor changes", {
            service.state.value.roots.singleOrNull()?.version == 1L && !service.state.value.busy
        }, 10)
        assertEquals("Edited after IDE Run", fixture.lastPatch.get()!!.changes.single().literal)
        assertEquals(1, fixture.patches.get())
    }

    fun testFailedIdeLaunchCancelsOnlyItsOwnPendingConnection() = session { fixture ->
        val service = fixture.service(WinRTHotReloadLaunchState())
        val folder = fixture.directory.parent.resolve(UUID.randomUUID().toString())
        service.attach(fixture.module, fixture.module.hotReloadLaunches.single(), folder)
        service.launchFailed(fixture.directory)
        assertTrue(service.state.value.busy)
        service.launchFailed(folder)
        fixture.completedRequests()
        assertFalse(service.state.value.busy)
        assertTrue(service.state.value.message, service.state.value.message.contains("failed or stopped"))
        assertEquals(0, fixture.requests.get())
    }

    fun testInspectionCoalescesNewSelectionsAndKeepsThePendingDesignDocument() = session { fixture ->
        val service = fixture.service()
        service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        fixture.beforeInspection.set { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
        service.inspect(WinRTXamlInspectionRequest("probe.Window", "Window.xaml"))
        fixture.await("First inspection reached peer", entered)
        service.inspect(WinRTXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = "<Grid/>", width = 640))
        service.inspect(WinRTXamlInspectionRequest("probe.Window", "Window.xaml", selectedPath = listOf(0)))
        fixture.beforeInspection.set(null); release.countDown()
        PlatformTestUtil.waitWithEventsDispatching("Latest design inspection completes", {
            fixture.inspections.size == 2 && !service.state.value.inspecting
        }, 10)
        val next = fixture.inspections.last()
        assertEquals("<Grid/>", next.previewMarkup); assertEquals(640, next.width)
        assertEquals(listOf(0), next.selectedPath)
        assertEquals("Greeting", service.state.value.inspection!!.properties.single().value)
        assertEquals(0, fixture.patches.get())
    }

    fun testDisconnectClosesAnInspectionSocketAndDiscardsItsLateResult() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val closed = CountDownLatch(1)
        fixture.beforeInspection.set { socket -> entered.countDown(); assertEquals(-1, socket.getInputStream().read()); closed.countDown() }
        service.inspect(WinRTXamlInspectionRequest("probe.Window", "Window.xaml"))
        fixture.await("Inspection reached peer", entered)
        service.disconnect(); val state = service.state.value
        fixture.await("Disconnect closes inspection socket", closed); fixture.completedRequests()
        assertEquals(state, service.state.value)
        assertNull(service.state.value.inspection)
    }

    fun testRejectedDesignDocumentDoesNotDisplayThePreviousImageAndCanRecover() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        fun inspect(markup: String = "") {
            val count = fixture.inspections.size
            service.inspect(WinRTXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = markup))
            PlatformTestUtil.waitWithEventsDispatching("Design inspection completes", {
                fixture.inspections.size == count + 1 && !service.state.value.inspecting
            }, 10)
        }
        inspect("<Grid/>")
        assertNotNull(service.state.value.inspection!!.image)
        fixture.inspectionReply.set(WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.REJECTED, "Missing theme resource"))
        inspect("<Invalid/>")
        assertNull(service.state.value.inspection)
        assertEquals("Missing theme resource", service.state.value.inspectionError)
        assertTrue(service.state.value.connected)
        fixture.inspectionReply.set(null)
        inspect()
        assertNull("Capturing the old host content must not revive a failed document", service.state.value.inspection)
        assertEquals("Missing theme resource", service.state.value.inspectionError)
        inspect("<Grid/>")
        assertNull(service.state.value.inspectionError)
        assertNotNull(service.state.value.inspection!!.image)
    }

    fun testWorkspaceRoundTripRestoresAnExistingConnectionAndPropertyUpdates() = session { fixture ->
        val first = fixture.service()
        first.reconnect()
        fixture.connected(first)
        val saved = XmlSerializer.serialize(first.getState())
        assertFalse(JDOMUtil.writeElement(saved).contains(fixture.token))
        fixture.dispose(first)

        val reopened = fixture.service(XmlSerializer.deserialize(saved, WinRTHotReloadLaunchState::class.java))
        reopened.reconnect()
        fixture.connected(reopened)
        Files.writeString(fixture.source, markup.replace("Hello", "After reopening"))
        reopened.apply()
        PlatformTestUtil.waitWithEventsDispatching("Restored session applies changed source", {
            reopened.state.value.roots.singleOrNull()?.version == 1L && !reopened.state.value.busy
        }, 10)
        assertEquals("After reopening", fixture.lastPatch.get()!!.changes.single().literal)
        assertEquals(1, fixture.patches.get())
    }

    fun testReconnectReconcilesACommittedPatchWithoutPublishingItsCancelledResponse() = session { fixture ->
        val service = fixture.service()
        service.reconnect()
        fixture.connected(service)
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        fixture.afterPatch.set { socket ->
            entered.countDown()
            assertEquals(-1, socket.getInputStream().read())
            closed.countDown()
        }
        Files.writeString(fixture.source, markup.replace("Hello", "Committed before disconnect"))
        service.apply()
        fixture.await("Patch reached peer", entered)
        assertTrue(service.state.value.busy)
        service.reconnect()
        fixture.await("Reconnect closes cancelled patch socket", closed)
        fixture.connected(service)
        fixture.completedRequests()
        assertEquals(1L, service.state.value.roots.single().version)
        assertTrue(service.state.value.connected)
        assertFalse(service.state.value.busy)
        service.apply()
        PlatformTestUtil.waitWithEventsDispatching("Reconciled source needs no second patch", {
            service.state.value.message == "XAML matches the running components."
        }, 10)
        fixture.completedRequests()
        assertEquals("The reconciled patch must not be sent again", 1, fixture.patches.get())
    }

    fun testDisconnectAndDisposeClosePendingHandshakeWithoutLateStateChanges() = session { fixture ->
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        fixture.beforeHandshake.set { socket ->
            entered.countDown()
            assertEquals(-1, socket.getInputStream().read())
            closed.countDown()
        }
        val service = fixture.service()
        service.reconnect()
        fixture.await("Handshake reached peer", entered)
        service.disconnect()
        val disconnected = service.state.value
        fixture.await("Disconnect closes pending handshake", closed)
        fixture.completedRequests()
        assertEquals(disconnected, service.state.value)

        fixture.beforeHandshake.set(null)
        service.reconnect()
        fixture.connected(service)
        val disposalEntered = CountDownLatch(1)
        val disposalClosed = CountDownLatch(1)
        fixture.beforeHandshake.set { socket ->
            disposalEntered.countDown()
            assertEquals(-1, socket.getInputStream().read())
            disposalClosed.countDown()
        }
        service.reconnect()
        fixture.await("Disposal handshake reached peer", disposalEntered)
        fixture.dispose(service)
        fixture.await("Dispose closes pending handshake", disposalClosed)
        fixture.completedRequests()
        val requests = fixture.requests.get()
        service.reconnect()
        service.apply()
        assertEquals(requests, fixture.requests.get())
    }

    fun testRestoredProcessIdentityAndModuleBoundaryAreCheckedOnEveryAttempt() = session { fixture ->
        val valid = fixture.savedState()
        val service = fixture.service(valid.copy(startedUtc = Instant.parse(valid.startedUtc).minusSeconds(1).toString()))
        repeat(2) {
            service.reconnect()
            PlatformTestUtil.waitWithEventsDispatching("Reject reused process identity", { !service.state.value.busy }, 10)
            assertTrue(service.state.value.message, service.state.value.message.contains("process ID was reused"))
        }
        assertEquals(0, fixture.requests.get())
        service.loadState(valid.copy(sessionDirectory = fixture.root.resolve(UUID.randomUUID().toString()).toString()))
        service.reconnect()
        PlatformTestUtil.waitWithEventsDispatching("Reject session outside module build", { !service.state.value.busy }, 10)
        assertTrue(service.state.value.message, service.state.value.message.contains("does not belong to this module"))
        assertEquals(0, fixture.requests.get())
        service.loadState(valid)
        service.reconnect()
        fixture.connected(service)
    }

    fun testTreeOnlyInspectionPreservesThePreviewImageForTheSameInstance() = session { fixture ->
        val service = fixture.service()
        service.reconnect()
        fixture.connected(service)
        val root = service.state.value.roots.single()
        service.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath))
        PlatformTestUtil.waitWithEventsDispatching("Captured preview", {
            !service.state.value.inspecting && service.state.value.inspection?.image != null
        }, 10)
        val pixels = service.state.value.inspection!!.image!!.pixels
        service.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, selectedPath = listOf(0), capture = false))
        PlatformTestUtil.waitWithEventsDispatching("Tree selection inspected", {
            fixture.inspections.size == 2 && !service.state.value.inspecting
        }, 10)
        assertTrue(pixels.contentEquals(service.state.value.inspection!!.image!!.pixels))
        service.inspect(WinRTXamlInspectionRequest(root.className, root.resourcePath, instance = 1, capture = false))
        PlatformTestUtil.waitWithEventsDispatching("Another instance inspected", {
            fixture.inspections.size == 3 && !service.state.value.inspecting
        }, 10)
        assertNull(service.state.value.inspection!!.image)
    }

    private fun session(action: (Session) -> Unit) {
        val fixture = Session()
        try { action(fixture); fixture.failure.get()?.let { throw AssertionError("Loopback peer failed", it) } }
        finally { fixture.close() }
    }

    private inner class Session : AutoCloseable {
        val root: Path = Files.createTempDirectory("winrt-hot-service-")
        val source = root.resolve("Window.xaml")
        val token = "s".repeat(43)
        val requests = AtomicInteger()
        val patches = AtomicInteger()
        val lastPatch = AtomicReference<WinRTXamlHotReloadPatch?>()
        val failure = AtomicReference<Throwable?>()
        val beforeHandshake = AtomicReference<((java.net.Socket) -> Unit)?>()
        val afterPatch = AtomicReference<((java.net.Socket) -> Unit)?>()
        val beforeInspection = AtomicReference<((java.net.Socket) -> Unit)?>()
        val inspections = java.util.concurrent.CopyOnWriteArrayList<WinRTXamlInspectionRequest>()
        val inspectionReply = AtomicReference<WinRTXamlHotReloadReply?>()
        private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        private val executor = Executors.newCachedThreadPool()
        private val services = linkedMapOf<WinRTHotReloadService, CoroutineScope>()
        private val activeRequests = AtomicInteger()
        private val liveRoot = AtomicReference(WinRTXamlHotReloadRoot("probe.Window", "Window.xaml",
            WinRTHotReloadMarkup.parse(markup).hash, 0, listOf("Greeting")))
        val directory = root.resolve("build/kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}")
        val module = WinRTModuleData(":app", root.toString(), root.resolve("build").toString(), "2.4.0", "",
            emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(WinRTXamlCompilationData("analyzeWinRTXaml", listOf(root.toString()),
                root.resolve("declarations.json").toString(), root.resolve("input.json").toString(), root.toString(), "")),
            hotReloadLaunches = listOf(WinRTHotReloadLaunchData("runWindows", ProcessHandle.current().info().command().orElseThrow(), root.toString())))

        init {
            Files.writeString(source, markup)
            Files.createDirectories(directory)
            Files.newOutputStream(directory.resolve("probe.session")).use { output -> Properties().apply {
                setProperty("protocol", WinRTXamlHotReloadProtocol.VERSION.toString())
                setProperty("pid", ProcessHandle.current().pid().toString())
                setProperty("port", server.localPort.toString()); setProperty("token", token)
            }.store(output, "probe") }
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(module))
            executor.submit {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: SocketException) { break }
                    activeRequests.incrementAndGet()
                    executor.submit {
                        try { socket.use {
                            socket.soTimeout = 10_000
                            val command = WinRTXamlHotReloadWire.readCommand(socket.getInputStream())
                            val patch = command.patch
                            assertEquals(token, command.token); requests.incrementAndGet()
                            if (command.inspection != null) {
                                inspections += command.inspection
                                beforeInspection.get()?.invoke(socket)
                            } else if (patch == null) beforeHandshake.get()?.invoke(socket)
                            else {
                                val before = liveRoot.get()
                                assertEquals(before.sourceHash, patch.expectedHash)
                                assertEquals(before.version + 1, patch.version)
                                liveRoot.set(before.copy(sourceHash = patch.sourceHash, version = patch.version))
                                lastPatch.set(patch); patches.incrementAndGet()
                                afterPatch.get()?.invoke(socket)
                            }
                            runCatching { WinRTXamlHotReloadWire.writeReply(socket.getOutputStream(),
                                (if (command.inspection != null) inspectionReply.get() else null) ?: WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.APPLIED, "connected", listOf(liveRoot.get()),
                                    inspection = command.inspection?.let { WinRTXamlVisualSnapshot(emptyList(), listOf(WinRTXamlVisualProperty("Name", "Greeting")),
                                        if (it.capture) WinRTXamlVisualImage(1, 1, byteArrayOf(0, 0, 0, -1)) else null) })) }
                        } } catch (error: Throwable) { failure.compareAndSet(null, error) }
                        finally { activeRequests.decrementAndGet() }
                    }
                }
            }
        }

        fun savedState() = WinRTHotReloadLaunchState(root.toString(), "runWindows", directory.toString(),
            ProcessHandle.current().pid(), ProcessHandle.current().info().startInstant().orElseThrow().toString())

        fun service(state: WinRTHotReloadLaunchState = savedState()): WinRTHotReloadService {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return WinRTHotReloadService(project, scope).also {
                services[it] = scope
                Disposer.register(testRootDisposable, it)
                it.loadState(state)
            }
        }

        fun connected(service: WinRTHotReloadService) = PlatformTestUtil.waitWithEventsDispatching("Development handshake", {
            service.state.value.connected && !service.state.value.busy
        }, 10)

        fun await(label: String, latch: CountDownLatch) = PlatformTestUtil.waitWithEventsDispatching(label, { latch.count == 0L }, 10)

        fun completedRequests() = PlatformTestUtil.waitWithEventsDispatching("Cancelled requests finish", {
            activeRequests.get() == 0 && services.values.none { scope -> scope.coroutineContext[Job]!!.children.any { it.isCancelled && !it.isCompleted } }
        }, 10)

        fun dispose(service: WinRTHotReloadService) {
            Disposer.dispose(service)
            services.getValue(service).cancel()
        }

        override fun close() {
            services.keys.forEach { if (!Disposer.isDisposed(it)) dispose(it) }
            server.close(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS)
            runBlocking { withTimeout(10_000) { services.values.forEach { it.coroutineContext[Job]!!.join() } } }
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), emptyList())
            check(root.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
            check(root.fileName.toString().startsWith("winrt-hot-service-"))
            FileUtil.delete(root.toFile())
        }
    }
}
