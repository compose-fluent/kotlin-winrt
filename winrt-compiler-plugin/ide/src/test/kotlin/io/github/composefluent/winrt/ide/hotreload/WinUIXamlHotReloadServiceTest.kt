package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlCompilationData
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
class WinUIXamlHotReloadServiceTest : BasePlatformTestCase() {
    private val markup = """<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.Window"><TextBlock x:Name="Greeting" Text="Hello"/></Window>"""

    fun testRestartStateNeverUsesProcessLocalInstanceOrdinals() {
        val first = WinUIXamlHotReloadRoot("First", "First.xaml", "hash", 0, emptyList())
        val second = first.copy(className = "Second", resourcePath = "Second.xaml")
        assertEquals(listOf(WinUIXamlStateRequest("First", "First.xaml"),
            WinUIXamlStateRequest("Second", "Second.xaml")), restartStatePlan(listOf(first, second)).requests)
        assertTrue(restartStatePlan(listOf(first.copy(instances = 0))).requests.isEmpty())
        val plan = restartStatePlan(listOf(first, second.copy(instances = 2)))
        assertEquals(listOf(WinUIXamlStateRequest("First", "First.xaml")), plan.requests)
        assertEquals(listOf("Second (Second.xaml)"), plan.skippedComponents)
    }

    fun testIdeLaunchAttachesWithoutAnEarlierToolWindowSessionAndAutomaticallyAppliesEditorChanges() = session { fixture ->
        val service = fixture.service(WinUIXamlHotReloadLaunchState())
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

    fun testExternalLoadedXamlChangesApplyWithoutAnOpenEditor() = session { fixture ->
        val service = fixture.service()
        service.reconnect(); fixture.connected(service)
        Files.writeString(fixture.source, markup.replace("Hello", "External edit"))
        service.markXamlChanged(fixture.source.toString())
        PlatformTestUtil.waitWithEventsDispatching("External XAML edit reaches the live root", {
            fixture.lastPatch.get()?.changes?.singleOrNull()?.literal == "External edit"
        }, 10)
        assertFalse(service.state.value.projectCodeChanged)
    }

    fun testDictionaryChangesRequireBuildAndRespectManualMode() = session { fixture ->
        val service = fixture.service()
        service.reconnect(); fixture.connected(service)
        service.setAutomatic(false)
        val dictionary = fixture.source.parent.resolve("Resources.xaml")
        Files.writeString(dictionary, """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"/>""")
        service.markXamlChanged(dictionary.toString())
        assertTrue(service.state.value.projectCodeChanged)
        assertTrue(service.state.value.connected)
        assertFalse(service.state.value.rebuildingCode)
        assertEquals(0, fixture.patches.get())
    }

    fun testCompiledPreviewBecomesOutdatedAfterDependencyKotlinEditAndStaysOutdatedAfterRender() = session { fixture ->
        val dependency = fixture.root.resolve("controls")
        val generated = dependency.resolve("generated")
        val dependencyModule = fixture.module.copy(projectDirectory = dependency.toString(),
            sourceSets = listOf(io.github.composefluent.winrt.ide.gradle.WinRTSourceSetData("main",
                listOf(dependency.toString()), emptyList(), emptyList(), generatedKotlinRoots = listOf(generated.toString()))))
        project.service<WinRTProjectService>().replaceBuildModels(fixture.root.toString(), listOf(fixture.module, dependencyModule))
        val service = fixture.previewService()
        service.reconnect(); fixture.connected(service)
        service.markProjectCodeChanged(generated.resolve("Generated.kt").toString())
        service.markProjectCodeChanged(fixture.root.resolve("build/Generated.kt").toString())
        service.markProjectCodeChanged(fixture.root.resolve("Page.xaml").toString())
        service.markProjectCodeChanged(fixture.root.resolveSibling("outside/Other.kt").toString())
        assertFalse(service.state.value.projectCodeChanged)
        service.markProjectCodeChanged(dependency.resolve("Control.kt").toString())
        assertTrue(service.state.value.projectCodeChanged)
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", capture = true))
        PlatformTestUtil.waitWithEventsDispatching("Inspection completes", { service.state.value.inspection != null }, 10)
        assertTrue("A successful XAML render does not update compiled Kotlin", service.state.value.projectCodeChanged)
        val live = fixture.service()
        live.reconnect(); fixture.connected(live)
        live.markProjectCodeChanged(dependency.resolve("Control.kt").toString())
        assertTrue("Live applications must also report stale compiled Kotlin", live.state.value.projectCodeChanged)
        assertTrue("Tracking Kotlin edits must not disconnect the live application", live.state.value.connected)
        val process = live.state.value.pid
        live.apply()
        PlatformTestUtil.waitWithEventsDispatching("The XAML check completes", {
            live.state.value.message == "XAML matches the running components."
        }, 10)
        assertTrue("Matching XAML does not certify Kotlin changes", live.state.value.projectCodeChanged)
        assertEquals("Tracking edits must not restart the application", process, live.state.value.pid)
        live.disconnect()
        assertTrue(live.state.value.projectCodeChanged)
        live.reconnect(); fixture.connected(live)
        assertTrue("Reconnection does not replace compiled code", live.state.value.projectCodeChanged)
        val restored = fixture.service(live.getState())
        restored.reconnect(); fixture.connected(restored)
        assertTrue("Restoring the same process must retain the source warning", restored.state.value.projectCodeChanged)
    }

    fun testLoadedSourceReusesOnlyTheConnectedHostThatExposesItsType() = session { fixture ->
        val unknown = fixture.root.resolve("Unknown.xaml")
        Files.writeString(unknown, markup.replace("probe.Window", "dependency.Unknown"))
        val service = fixture.service()
        assertNull(service.loadedSourceLaunch(fixture.source.toString()))
        service.reconnect(); fixture.connected(service)
        assertEquals(fixture.module to fixture.module.hotReloadLaunches.single(),
            service.loadedSourceLaunch(fixture.source.toString()))
        assertNull("An indexed source does not prove the running host contains its type",
            service.loadedSourceLaunch(unknown.toString()))
        service.disconnect()
        assertNull(service.loadedSourceLaunch(fixture.source.toString()))
    }

    fun testConnectedHostMatchesItsApplicationAndLaunchOnly() = session { fixture ->
        val service = fixture.service()
        val launch = fixture.module.hotReloadLaunches.single()
        assertFalse(service.usesLaunch(fixture.module, launch))
        service.reconnect(); fixture.connected(service)
        assertTrue(service.usesLaunch(fixture.module, launch))
        assertFalse(service.usesLaunch(fixture.module.copy(projectDirectory = fixture.root.resolve("other-app").toString()), launch))
        assertFalse(service.usesLaunch(fixture.module, launch.copy(taskName = "otherLaunch")))
    }

    fun testLeavingTheEditorCancelsPendingApplicationSwitch() = session { fixture ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.beforeHandshake.set { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        val service = fixture.service()
        service.reconnect()
        fixture.await("Original host is connecting", entered)
        try {
            runBlocking {
                val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                    service.ensurePreviewReady(fixture.module.copy(projectDirectory = fixture.root.resolve("other").toString()),
                        fixture.module.hotReloadLaunches.single())
                    fail("A busy other host must not be treated as the requested application")
                }
                assertTrue(pending.isActive)
                pending.cancelAndJoin()
            }
        } finally { release.countDown() }
        fixture.connected(service)
        assertTrue(service.usesLaunch(fixture.module, fixture.module.hotReloadLaunches.single()))
        assertEquals(fixture.directory.toString(), service.getState().sessionDirectory)
    }

    fun testProvenSourceHostSurvivesWorkspaceReloadButNotRemovedGradleModels() = session { fixture ->
        val service = fixture.service()
        assertNull(service.sourcePreviewLaunch(fixture.source.toString()))
        service.reconnect(); fixture.connected(service)
        val expected = fixture.module to fixture.module.hotReloadLaunches.single()
        assertEquals(expected, service.sourcePreviewLaunch(fixture.source.toString()))
        val hosts = project.service<WinRTPreviewSourceHosts>()
        val xml = XmlSerializer.serialize(hosts.getState())
        val restored = WinRTPreviewSourceHosts()
        restored.loadState(XmlSerializer.deserialize(xml, WinRTPreviewSourceHosts.State::class.java))
        service.disconnect()
        assertEquals(expected, restored.resolve(fixture.source.toString(), listOf(fixture.module)))
        assertNull(restored.resolve(fixture.source.toString(), emptyList()))
        assertNull(restored.resolve(fixture.source.toString(), listOf(fixture.module.copy(hotReloadLaunches = emptyList()))))
        assertNull(restored.resolve(fixture.source.toString(), listOf(fixture.module.copy(xamlCompilations = emptyList()))))
        assertNull(restored.resolve(fixture.root.resolve("Unknown.xaml").toString(), listOf(fixture.module)))
    }

    fun testFailedIdeLaunchCancelsOnlyItsOwnPendingConnection() = session { fixture ->
        val service = fixture.service(WinUIXamlHotReloadLaunchState())
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

    fun testNativeDebuggerDrainsInspectionAndResumesEditorUpdatesAfterDetach() = session { fixture ->
        val service = fixture.service()
        service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        fixture.beforeInspection.set { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
        fixture.await("Inspection is in progress", entered)
        val pid = ProcessHandle.current().pid()
        val drained = service.suspendForDebugger(pid)
        assertFalse("Do not attach LLDB in the middle of a UI transaction", drained.isDone)
        release.countDown()
        PlatformTestUtil.waitWithEventsDispatching("Drain before attach", { drained.isDone }, 10)
        drained.get(1, TimeUnit.SECONDS)
        val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture.source)!!
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.setText(markup.replace("Hello", "Edited while debugging"))
        }
        service.apply()
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
        assertEquals(0, fixture.patches.get())
        assertEquals(1, fixture.inspections.size)
        service.resumeAfterDebugger(pid + 1)
        assertEquals(0, fixture.patches.get())
        service.resumeAfterDebugger(pid)
        PlatformTestUtil.waitWithEventsDispatching("Apply after debugger detaches", { fixture.patches.get() == 1 }, 10)
        assertEquals("Edited while debugging", fixture.lastPatch.get()!!.changes.single().literal)
    }

    fun testInspectionCoalescesNewSelectionsAndKeepsThePendingDesignDocument() = session { fixture ->
        val service = fixture.service()
        service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        fixture.beforeInspection.set { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
        fixture.await("First inspection reached peer", entered)
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = "<Grid/>", width = 640, renderScale = 2.5))
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", selectedPath = listOf(0), capture = false))
        fixture.beforeInspection.set(null); release.countDown()
        PlatformTestUtil.waitWithEventsDispatching("Latest design inspection completes", {
            fixture.inspections.size == 2 && !service.state.value.inspecting
        }, 10)
        val next = fixture.inspections.last()
        assertEquals("<Grid/>", next.previewMarkup); assertEquals(640, next.width); assertEquals(2.5, next.renderScale)
        assertEquals(listOf(0), next.selectedPath)
        assertTrue("Selection must not discard the pending high-density capture", next.capture)
        assertEquals("Greeting", service.state.value.inspection!!.properties.single().value)
        assertEquals(0, fixture.patches.get())
    }

    fun testSelectionCannotDiscardPendingClockCommandAndClockFailureKeepsTheCanvas() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        fixture.beforeInspection.set { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
        fixture.await("Capture reached peer", entered)
        val clock = WinUIXamlPreviewAnimation("", "Motion", 2000)
        try {
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", animation = clock))
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", selectedPath = listOf(0), capture = false))
        } finally { fixture.beforeInspection.set(null); release.countDown() }
        PlatformTestUtil.waitWithEventsDispatching("Pending clock completes", { fixture.inspections.size == 2 && !service.state.value.inspecting }, 10)
        assertEquals(clock, fixture.inspections.last().animation)
        assertTrue(fixture.inspections.last().capture)
        val previous = service.state.value.inspection
        fixture.inspectionReply.set(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Missing storyboard"))
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", animation = clock))
        PlatformTestUtil.waitWithEventsDispatching("Clock failure displayed", { service.state.value.animationError != null && !service.state.value.inspecting }, 10)
        assertEquals("Missing storyboard", service.state.value.animationError)
        assertSame(previous, service.state.value.inspection)
        assertNull(service.state.value.inspectionError)
        fixture.inspectionReply.set(null)
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", animation = clock))
        PlatformTestUtil.waitWithEventsDispatching("Clock retry recovers", { service.state.value.animationError == null && !service.state.value.inspecting }, 10)
    }

    fun testDisconnectClosesAnInspectionSocketAndDiscardsItsLateResult() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        val entered = CountDownLatch(1); val closed = CountDownLatch(1)
        fixture.beforeInspection.set { socket -> entered.countDown(); assertEquals(-1, socket.getInputStream().read()); closed.countDown() }
        service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
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
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = markup))
            PlatformTestUtil.waitWithEventsDispatching("Design inspection completes", {
                fixture.inspections.size == count + 1 && !service.state.value.inspecting
            }, 10)
        }
        inspect("<Grid/>")
        assertNotNull(service.state.value.inspection!!.image)
        val renderedDocument = service.state.value.inspectionDocument
        assertEquals("<Grid/>", renderedDocument?.previewMarkup)
        inspect()
        assertEquals("Selection-only replies retain the image's actual document", renderedDocument, service.state.value.inspectionDocument)
        fixture.inspectionReply.set(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Missing theme resource"))
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

    fun testLostDesignReplyInvalidatesOldImageUntilAFullDocumentRecovers() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        fun inspect(markup: String) {
            val count = fixture.inspections.size
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = markup))
            PlatformTestUtil.waitWithEventsDispatching("Inspection completes", {
                fixture.inspections.size == count + 1 && !service.state.value.inspecting
            }, 10)
        }
        inspect("<Grid/>")
        assertNotNull(service.state.value.inspection!!.image)
        fixture.beforeInspection.set { it.close() }
        inspect("<Border/>")
        assertNotNull(service.state.value.inspectionError)
        assertNull(service.state.value.inspection)
        fixture.beforeInspection.set(null)
        inspect("")
        assertNotNull("A selection cannot certify which document was committed", service.state.value.inspectionError)
        assertNull(service.state.value.inspection)
        inspect("<Border/>")
        assertNull(service.state.value.inspectionError)
        assertNotNull(service.state.value.inspection!!.image)
    }

    fun testRejectedSelectionIsReportedAndSuccessfulInspectionCanRecover() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        fun inspect() {
            val count = fixture.inspections.size
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml"))
            PlatformTestUtil.waitWithEventsDispatching("Selection completes", {
                fixture.inspections.size == count + 1 && !service.state.value.inspecting
            }, 10)
        }
        inspect()
        val previous = service.state.value.inspection
        fixture.inspectionReply.set(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Selected visual was removed"))
        inspect()
        assertEquals("Selected visual was removed", service.state.value.inspectionError)
        assertSame(previous, service.state.value.inspection)
        assertFalse(service.state.value.inspectionPropertiesRead)
        fixture.inspectionReply.set(null)
        inspect()
        assertNull(service.state.value.inspectionError)
        assertTrue(service.state.value.inspectionPropertiesRead)
    }

    fun testSdkReceiptWaitsForSuccessfulDocumentCaptureAfterAssetsAreStaged() = session { fixture ->
        val service = fixture.service(); service.reconnect(); fixture.connected(service)
        val host = Files.createDirectories(Path.of(fixture.module.buildDirectory, "sdk-host"))
        val executable = Files.write(host.resolve("preview.exe"), byteArrayOf(1))
        val references = Files.writeString(host.resolve("references.json"), "{}")
        val sdk = io.github.composefluent.winrt.ide.gradle.WinRTStaticPreviewData(
            "runWinRTXamlSdkPreview", executable.toString(), host.toString(), references.toString())
        val module = fixture.module.copy(staticPreview = sdk)
        val cache = WinRTPreviewLaunchCache(fixture.root, listOf(module), module, sdk.launch())
        val inputs = cache.inputs()
        cache.remember(inputs)
        // The SDK host connected before the document staged its project assets.
        val asset = Files.writeString(host.resolve("Logo.png"), "project asset")
        assertFalse(cache.reusable(inputs))
        WinRTDevelopmentSession::class.java.getDeclaredField("pendingPreviewCache").apply { isAccessible = true }
            .set(service, cache to inputs)
        fun inspect(markup: String) {
            val count = fixture.inspections.size
            service.inspect(WinUIXamlInspectionRequest("probe.Window", "Window.xaml", previewMarkup = markup))
            PlatformTestUtil.waitWithEventsDispatching("SDK document capture completes", {
                fixture.inspections.size == count + 1 && !service.state.value.inspecting
            }, 10)
        }
        fixture.inspectionReply.set(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Invalid document"))
        inspect("<Invalid/>")
        assertFalse("A failed capture must not bless the deployment", cache.reusable(cache.inputs()))
        fixture.inspectionReply.set(null)
        inspect("<Grid/>")
        assertTrue("Successful capture records the staged deployment", cache.reusable(cache.inputs()))
        Files.writeString(asset, "a later deployment change")
        assertFalse("Subsequent deployment changes still require validation", cache.reusable(cache.inputs()))
    }

    fun testWorkspaceRoundTripRestoresAnExistingConnectionAndPropertyUpdates() = session { fixture ->
        val first = fixture.service()
        first.reconnect()
        fixture.connected(first)
        val saved = XmlSerializer.serialize(first.getState())
        assertFalse(JDOMUtil.writeElement(saved).contains(fixture.token))
        fixture.dispose(first)

        val reopened = fixture.service(XmlSerializer.deserialize(saved, WinUIXamlHotReloadLaunchState::class.java))
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
        service.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath))
        PlatformTestUtil.waitWithEventsDispatching("Captured preview", {
            !service.state.value.inspecting && service.state.value.inspection?.image != null
        }, 10)
        val pixels = service.state.value.inspection!!.image!!.pixels
        service.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, selectedPath = listOf(0), capture = false))
        PlatformTestUtil.waitWithEventsDispatching("Tree selection inspected", {
            fixture.inspections.size == 2 && !service.state.value.inspecting
        }, 10)
        assertTrue(pixels.contentEquals(service.state.value.inspection!!.image!!.pixels))
        service.inspect(WinUIXamlInspectionRequest(root.className, root.resourcePath, instance = 1, capture = false))
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
        val lastPatch = AtomicReference<WinUIXamlHotReloadPatch?>()
        val failure = AtomicReference<Throwable?>()
        val beforeHandshake = AtomicReference<((java.net.Socket) -> Unit)?>()
        val afterPatch = AtomicReference<((java.net.Socket) -> Unit)?>()
        val beforeInspection = AtomicReference<((java.net.Socket) -> Unit)?>()
        val inspections = java.util.concurrent.CopyOnWriteArrayList<WinUIXamlInspectionRequest>()
        val inspectionReply = AtomicReference<WinUIXamlHotReloadReply?>()
        private val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        private val executor = Executors.newCachedThreadPool()
        private val services = linkedMapOf<WinRTDevelopmentSession, CoroutineScope>()
        private val activeRequests = AtomicInteger()
        private val liveRoot = AtomicReference(WinUIXamlHotReloadRoot("probe.Window", "Window.xaml",
            WinUIXamlHotReloadMarkup.parse(markup).hash, 0, listOf("Greeting")))
        val directory = root.resolve("build/kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}")
        val module = WinRTModuleData(":app", root.toString(), root.resolve("build").toString(), "2.4.0", "",
            emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(WinUIXamlCompilationData("analyzeWinRTXaml", listOf(root.toString()),
                root.resolve("declarations.json").toString(), root.resolve("input.json").toString(), root.toString(), "")),
            hotReloadLaunches = listOf(WinUIXamlHotReloadLaunchData("runWindows", ProcessHandle.current().info().command().orElseThrow(), root.toString())))

        init {
            Files.writeString(source, markup)
            Files.createDirectories(directory)
            Files.newOutputStream(directory.resolve("probe.session")).use { output -> Properties().apply {
                setProperty("protocol", WinUIXamlHotReloadProtocol.VERSION.toString())
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
                            val command = WinUIXamlHotReloadWire.readCommand(socket.getInputStream())
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
                            runCatching { WinUIXamlHotReloadWire.writeReply(socket.getOutputStream(),
                                (if (command.inspection != null) inspectionReply.get() else null) ?: WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "connected", listOf(liveRoot.get()),
                                    inspection = command.inspection?.let { WinUIXamlVisualSnapshot(emptyList(), listOf(WinUIXamlVisualProperty("Name", "Greeting")),
                                        if (it.capture) WinUIXamlVisualImage(1, 1, byteArrayOf(0, 0, 0, -1)) else null) })) }
                        } } catch (error: Throwable) { failure.compareAndSet(null, error) }
                        finally { activeRequests.decrementAndGet() }
                    }
                }
            }
        }

        fun savedState() = WinUIXamlHotReloadLaunchState(root.toString(), "runWindows", directory.toString(),
            ProcessHandle.current().pid(), ProcessHandle.current().info().startInstant().orElseThrow().toString())

        fun service(state: WinUIXamlHotReloadLaunchState = savedState()): WinUIXamlHotReloadService {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return WinUIXamlHotReloadService(project, scope).also {
                services[it] = scope
                Disposer.register(testRootDisposable, it)
                it.loadState(state)
            }
        }

        fun previewService(): WinRTDevelopmentSession {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return WinRTDevelopmentSession(project, scope, isolatedPreview = true).also {
                services[it] = scope
                Disposer.register(testRootDisposable, it)
                it.loadState(savedState())
            }
        }

        fun connected(service: WinRTDevelopmentSession) = PlatformTestUtil.waitWithEventsDispatching("Development handshake", {
            service.state.value.connected && !service.state.value.busy
        }, 10)

        fun await(label: String, latch: CountDownLatch) = PlatformTestUtil.waitWithEventsDispatching(label, { latch.count == 0L }, 10)

        fun completedRequests() = PlatformTestUtil.waitWithEventsDispatching("Cancelled requests finish", {
            activeRequests.get() == 0 && services.values.none { scope -> scope.coroutineContext[Job]!!.children.any { it.isCancelled && !it.isCompleted } }
        }, 10)

        fun dispose(service: WinRTDevelopmentSession) {
            if (service !is WinUIXamlHotReloadService) {
                // This wire peer runs in the test JVM, not an owned designer child.
                service.disconnect()
                WinRTDevelopmentSession::class.java.getDeclaredField("owned").apply { isAccessible = true }.set(service, null)
            }
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

