package io.github.composefluent.winrt.ide

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelCache
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadClient
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadService
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadLaunchState
import io.github.composefluent.winrt.ide.xaml.WinRTXamlGeneratedNavigation
import io.github.composefluent.winrt.ide.xaml.WinRTXamlAttributeAnalysis
import io.github.composefluent.winrt.ide.xaml.WinRTXamlAttributeNavigation
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadMarkup
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadPatch
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadRead
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadTarget
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Optional native Gradle resolver/import against a template project prepared
 * with analyzeWinRTXaml + generateWinRTProjections, without building its app.
 * No synthetic SDK classes, facets or WinRT model nodes are injected here.
 */
@OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
class WinRTGradleImportTest : BasePlatformTestCase() {
    fun testTemplateGradleImportProvidesRealSdkAndXamlFirBeforeApplicationBuild() {
        val requested = System.getProperty("winrt.ide.importProject")
        assumeTrue("Requires a prepared standalone WinUI template", requested != null)
        val root = Path.of(requested).toAbsolutePath().normalize()
        require(root.startsWith(Path.of(System.getProperty("winrt.ide.toolchain")).resolve(".gradle")))
        require(Files.isRegularFile(root.resolve("settings.gradle.kts")))
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString(), System.getProperty("winrt.ide.toolchain"))
        val phase = System.getProperty("winrt.ide.importPhase", "import-and-reopen")
        require(phase in setOf("import", "reopen", "import-and-reopen"))
        // Use the platform's supported test switch, keeping the production
        // workspace serializer/loader. No cached graph is reimported on reopen.
        WorkspaceModelCacheImpl.forceEnableCaching(testRootDisposable)
        // The composite includes the runtime's Native source set. IDEA's test
        // VFS guard must permit the user's external, read-only dependency caches.
        System.getenv("GRADLE_USER_HOME")?.let { gradleHome ->
            val caches = Path.of(gradleHome).toAbsolutePath().parent
            VfsRootAccess.allowRootAccess(testRootDisposable, caches.resolve("konan").toString(),
                caches.resolve("maven").toString(), caches.resolve("nuget").toString())
        }
        System.getenv("KONAN_DATA_DIR")?.let { VfsRootAccess.allowRootAccess(testRootDisposable, it) }
        VfsRootAccess.allowRootAccess(testRootDisposable, System.getProperty("java.home"))
        val home = System.getProperty("java.home")
        val previousSdks = ProjectJdkTable.getInstance().allJdks.toSet()
        val sdk = JavaSdk.getInstance().createJdk("WinRT import validation ${root.hashCode()}", home, false)
        ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
        val manager = ProjectManagerEx.getInstanceEx()
        var imported = manager.openProject(root, OpenProjectTask {
            isNewProject = phase != "reopen"
            useDefaultProjectAsTemplate = false
            forceOpenInNewFrame = true
            runConfigurators = false
            createModule = false
            projectName = "WinRT import validation"
            beforeInit = { it.putUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED, true) }
        })!!
        try {
            if (phase == "reopen") {
                val previousProcess = Files.readString(root.resolve("app/build/ide-validation/import-process.txt")).trim().toLong()
                assertTrue("Recovery must run in a new IDE host process", previousProcess != ProcessHandle.current().pid())
                assertWorkspaceRecovered(imported, root)
                assertNavigation(imported, root, "Greeting")
                assertCompletion(imported, root, "Greeting")
                assertRunningTemplate(imported, root)
                return
            }
            ApplicationManager.getApplication().runWriteAction {
                ProjectRootManager.getInstance(imported).projectSdk = sdk
            }
            GradleSettings.getInstance(imported).linkProject(GradleProjectSettings().apply {
                externalProjectPath = root.toString().replace('\\', '/')
                distributionType = DistributionType.DEFAULT_WRAPPED
                gradleJvm = sdk.name
            })
            sync(imported, root)
            val service = imported.service<WinRTProjectService>()
            assertTrue(service.modules.value.toString(), service.modules.value.any { it.projectPath == ":app" })
            val app = service.modules.value.single { it.projectPath == ":app" }
            assertEditingReady(imported, root)
            assertLiveEditing(imported, root, phase == "import")
            assertTrue(app.xamlCompilations.toString(), app.xamlCompilations.all { Files.isRegularFile(Path.of(it.declarationsFile)) })
            // A real second synchronization exercises replacement of model nodes,
            // compiler configuration and source roots, not a manual service call.
            sync(imported, root)
            assertEquals(app.projectDirectory, service.modules.value.single { it.projectPath == ":app" }.projectDirectory)
            assertEditingReady(imported, root)
            assertTrue("Native Gradle cache must contain WinRT nodes: ${cacheKeys(imported)}", cacheModules(imported).isNotEmpty())
            ProjectDataManager.getInstance().getExternalProjectsData(imported, GradleConstants.SYSTEM_ID).forEach { info ->
                assertEquals("The native cache validates this exact path equality on reopen", info.externalProjectPath,
                    info.externalProjectStructure?.data?.linkedExternalProjectPath)
            }
            // Optional actual WinUI process: persist the plugin through the
            // platform component store, then reconnect from a new project service.
            val savedLaunch = System.getProperty("winrt.ide.recoveredHotReloadSession")?.let { session ->
                val clients = WinRTHotReloadClient.discover(Path.of(session))
                require(clients.size == 1)
                clients.single().use { client ->
                    // runWindows can alias a variant's launch task for the same host.
                    val launch = app.hotReloadLaunches.first { it.executable.replace('\\', '/').equals(
                        client.process.info().command().orElseThrow().replace('\\', '/'), true) }
                    val saved = WinRTHotReloadLaunchState(app.projectDirectory, launch.taskName, session,
                        client.process.pid(), client.started.toString())
                    val hot = imported.service<WinRTHotReloadService>()
                    hot.automatic.value = false
                    hot.loadState(saved)
                    hot.reconnect()
                    awaitHotReload(hot)
                    assertEquals(saved, hot.getState())
                    saved
                }
            }
            // Unit-test mode suppresses the automatic save scheduler. Persist
            // through the actual platform stores before closing this project.
            PlatformTestUtil.saveProject(imported, true)
            ExternalProjectsDataStorage.getInstance(imported).doSave()
            val workspaceCache = requireNotNull(WorkspaceModelCache.getInstance(imported))
            pooled("Save native workspace cache") { workspaceCache.saveCacheNow() }
            assertTrue(Files.isRegularFile(workspaceCache.cacheFile))
            if (phase == "import") {
                val marker = root.resolve("app/build/ide-validation/import-process.txt")
                Files.createDirectories(marker.parent)
                Files.writeString(marker, ProcessHandle.current().pid().toString())
                return
            }
            assertTrue(manager.saveAndForceCloseProject(imported))
            imported = manager.openProject(root, OpenProjectTask {
                forceOpenInNewFrame = true
                runConfigurators = false
                beforeInit = { it.putUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED, true) }
            })!!
            // Query the service as an editor would after reopening; this must
            // recover persisted native Gradle data without another sync.
            val restored = imported.service<WinRTProjectService>()
            restored.refreshFromGradleCache()
            try {
                PlatformTestUtil.waitWithEventsDispatching("Reopen WinRT model from Gradle cache", {
                    restored.modules.value.any { it.projectPath == ":app" }
                }, 10)
            } catch (error: AssertionError) { throw AssertionError("Restored cache: ${cacheKeys(imported)}", error) }
            assertTrue("Restored cache: ${cacheKeys(imported)}", cacheModules(imported).isNotEmpty())
            assertWorkspaceRecovered(imported, root)
            if (savedLaunch != null) {
                val hot = imported.service<WinRTHotReloadService>()
                assertEquals("Native workspace store must restore the previous development launch", savedLaunch, hot.getState())
                hot.automatic.value = false
                hot.reconnect()
                awaitHotReload(hot)
                val running = hot.state.value.roots.single { it.className == "sample.hello.MainWindow" }
                val source = restored.modules.value.single { it.projectPath == ":app" }.xamlCompilations
                    .map { Json.parseToJsonElement(Files.readString(Path.of(it.inputFile))).jsonObject }
                    .flatMap { it.getValue("XamlPages").jsonArray }.filter {
                        it.jsonObject["MSBuild_Link"]?.jsonPrimitive?.content == running.resourcePath
                    }.map { Path.of(it.jsonObject.getValue("FullPath").jsonPrimitive.content) }.distinct().single()
                val before = Files.readString(source)
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source)!!
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
                try {
                    val xml = PsiManager.getInstance(imported).findFile(virtualFile) as XmlFile
                    fun greeting(tag: XmlTag): XmlTag? = if (tag.getAttributeValue("x:Name") == "Greeting") tag
                        else tag.subTags.firstNotNullOfOrNull(::greeting)
                    WriteCommandAction.runWriteCommandAction(imported) {
                        greeting(xml.rootTag!!)!!.setAttribute("Text", "Reconnected from the restored IDE workspace")
                        PsiDocumentManager.getInstance(imported).doPostponedOperationsAndUnblockDocument(document)
                    }
                    hot.apply()
                    PlatformTestUtil.waitWithEventsDispatching("Restored service applies unsaved XAML to native WinUI", {
                        hot.state.value.values.any { it.element == "Greeting" && it.property == "Text" &&
                            it.value == "Reconnected from the restored IDE workspace" }
                    }, 30)
                    assertTrue(hot.state.value.roots.single { it.className == running.className }.version > running.version)
                    assertEquals(before, Files.readString(source))
                } finally { FileDocumentManager.getInstance().reloadFromDisk(document) }
            }
        } finally {
            manager.saveAndForceCloseProject(imported)
            ApplicationManager.getApplication().runWriteAction {
                // KMP import also creates the platform's Kotlin SDK.
                ProjectJdkTable.getInstance().allJdks.filterNot { it in previousSdks }
                    .forEach { ProjectJdkTable.getInstance().removeJdk(it) }
            }
        }
    }

    private fun pooled(message: String, action: () -> Unit) {
        val complete = AtomicBoolean()
        val failure = AtomicReference<Throwable?>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try { ProgressManager.getInstance().runProcess(action, EmptyProgressIndicator()) }
            catch (error: Throwable) { failure.set(error) }
            finally { complete.set(true) }
        }
        PlatformTestUtil.waitWithEventsDispatching(message, { complete.get() }, 180)
        failure.get()?.let { throw AssertionError(message, it) }
    }

    private fun assertWorkspaceRecovered(project: Project, root: Path) {
        assertTrue("Source roots and facets must be deserialized from the native workspace cache",
            (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache)
        val service = project.service<WinRTProjectService>()
        service.refreshFromGradleCache()
        PlatformTestUtil.waitWithEventsDispatching("Persisted WinRT Gradle model", {
            service.modules.value.any { it.projectPath == ":app" }
        }, 30)
        assertTrue(cacheModules(project).isNotEmpty())
        assertEditingReady(project, root)
    }

    private fun assertRunningTemplate(project: Project, root: Path) {
        val session = System.getProperty("winrt.ide.recoveredHotReloadSession") ?: return
        val model = project.service<WinRTProjectService>().modules.value.single { it.projectPath == ":app" }
        val source = source(root, "app", "sample/hello/MainWindow.xaml")
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source)!!
        val xml = PsiManager.getInstance(project).findFile(file) as XmlFile
        val greeting = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java).single { it.getAttributeValue("x:Name") == "Greeting" }
        val expectedText = greeting.getAttributeValue("Text")!!
        assertEquals("Created and edited by Kotlin WinRT IDE", expectedText)
        pooled("Read the newly built native template's actual WinUI properties") {
            val clients = WinRTHotReloadClient.discover(Path.of(session))
            assertEquals(1, clients.size)
            clients.single().use { client ->
                assertTrue(model.hotReloadLaunches.any { it.executable.replace('\\', '/').equals(
                    client.process.info().command().orElseThrow().replace('\\', '/'), true) })
                val snapshot = client.request()
                val running = snapshot.roots.single { it.className == "sample.hello.MainWindow" }
                assertTrue("The authored control module must also load in the real application", snapshot.roots.any {
                    it.className == "sample.controls.GreetingControl"
                })
                assertEquals(WinRTHotReloadMarkup.parse(Files.readString(source)).hash, running.sourceHash)
                val result = client.request(WinRTXamlHotReloadPatch(running.className, running.resourcePath, running.sourceHash,
                    running.sourceHash, running.version + 1, emptyList(), reads = listOf(
                        WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("Greeting"), "Text"))))
                assertEquals(result.message, WinRTXamlHotReloadProtocol.APPLIED, result.status)
                assertEquals(expectedText, result.values.single { it.element == "Greeting" && it.property == "Text" }.value)
            }
        }
    }

    private fun source(root: Path, module: String, relative: String): Path = sequenceOf("main", "winuiMain")
        .map { root.resolve("$module/src/$it/kotlin/$relative") }.single(Files::isRegularFile)

    private fun kotlinFile(project: Project, root: Path): KtFile = PsiManager.getInstance(project).findFile(
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source(root, "app", "sample/hello/MainWindow.kt"))!!) as KtFile

    private fun assertLiveEditing(project: Project, root: Path, saveEdit: Boolean) {
        val source = source(root, "app", "sample/hello/MainWindow.xaml")
        val xamlFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source)!!
        val documents = FileDocumentManager.getInstance()
        val xaml = documents.getDocument(xamlFile)!!
        val file = kotlinFile(project, root)
        val kotlin = documents.getDocument(file.virtualFile)!!
        val beforeXaml = xaml.text
        var expectedDiskXaml = Files.readString(source)
        val beforeKotlin = kotlin.text
        var saved = false
        val declarationFiles = project.service<WinRTProjectService>().modules.value.single { it.projectPath == ":app" }
            .xamlCompilations.filter { compilation -> compilation.sourceRoots.any { source.startsWith(Path.of(it)) } }
            .map { WinRTXamlSnapshotService.key(it.declarationsFile) }
        assertTrue(declarationFiles.toString(), declarationFiles.isNotEmpty())
        val probe = beforeKotlin.substringBefore(" {").trimEnd() + " {\n" +
            "    override fun initializeComponent() { super.initializeComponent() }\n" +
            "    fun ideElement() = Greeting\n}\n"
        try {
            WriteCommandAction.runWriteCommandAction(project) { kotlin.setText(probe) }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            assertNavigation(project, root, "Greeting")
            assertCompletion(project, root, "Greeting")
            assertTrue(resolutionDiagnostics(file).toString(), resolutionDiagnostics(file).isEmpty())
            fun edit(text: String, name: String, exists: Boolean) {
                WriteCommandAction.runWriteCommandAction(project) { xaml.setText(text) }
                val snapshots = project.service<WinRTXamlSnapshotService>()
                PlatformTestUtil.waitWithEventsDispatching("Live imported XAMLC name $name", {
                    // A shared source is compiled for both JVM and Native. Wait
                    // for every owning input, rather than observing the target
                    // that finishes first while the shared facet uses another.
                    declarationFiles.all { declarations -> snapshots.state.value[declarations]?.let { snapshot ->
                        snapshot.error != null || snapshot.declarations.pages.any { page ->
                            page.className == "sample.hello.MainWindow" && page.connections.any { it.fieldName == name } == exists
                        }
                    } == true }
                }, 60)
                snapshots.state.value.values.forEach { assertNull(it.error, it.error) }
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                awaitSmart(project)
                val names = memberNames(file)
                assertEquals(names.toString(), exists, name in names)
                assertEquals("The XAMLC document producer must not write to source files", expectedDiskXaml, Files.readString(source))
            }
            val renamed = beforeXaml.replace("x:Name=\"Greeting\"", "x:Name=\"RevisedGreeting\"")
            edit(renamed, "RevisedGreeting", true)
            assertFalse("Greeting" in memberNames(file))
            assertTrue(resolutionDiagnostics(file).any { it.contains("Greeting") })
            WriteCommandAction.runWriteCommandAction(project) { kotlin.setText(probe.replace("= Greeting", "= RevisedGreeting")) }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            assertTrue(resolutionDiagnostics(file).isEmpty())
            assertNavigation(project, root, "RevisedGreeting")
            assertCompletion(project, root, "RevisedGreeting")
            val added = renamed.replace("</StackPanel>", "<TextBlock x:Name=\"ExtraGreeting\" Text=\"Added in the IDE\"/>\n    </StackPanel>")
            edit(added, "ExtraGreeting", true)
            edit(added.replace("x:Name=\"ExtraGreeting\"", ""), "ExtraGreeting", false)
            // A real resolver synchronization must retain the edited declarations
            // while replacing roots and facets, including their FIR configuration.
            sync(project, root)
            // Native Gradle synchronization saves documents before resolving.
            // The plugin must preserve their edited semantics across that save.
            expectedDiskXaml = Files.readString(source)
            assertTrue("RevisedGreeting" in memberNames(file))
            assertTrue(resolutionDiagnostics(file).isEmpty())
            edit(beforeXaml, "Greeting", true)
            WriteCommandAction.runWriteCommandAction(project) {
                kotlin.setText(if (saveEdit) probe else beforeKotlin)
                if (saveEdit) xaml.setText(beforeXaml.replace("Hello from Kotlin WinRT", "Created and edited by Kotlin WinRT IDE"))
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            if (saveEdit) { documents.saveDocument(xaml); documents.saveDocument(kotlin); saved = true }
        } finally {
            if (!saved) {
                WriteCommandAction.runWriteCommandAction(project) { xaml.setText(beforeXaml); kotlin.setText(beforeKotlin) }
                PsiDocumentManager.getInstance(project).commitAllDocuments()
                documents.saveDocument(xaml)
                documents.saveDocument(kotlin)
            }
            FileEditorManager.getInstance(project).closeFile(file.virtualFile)
        }
    }

    private fun assertNavigation(project: Project, root: Path, name: String) {
        val file = kotlinFile(project, root)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val expression = PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression::class.java).single { it.getReferencedName() == name }
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file.virtualFile), false)!!
        val target = allowAnalysisOnEdt { WinRTXamlGeneratedNavigation().getGotoDeclarationTargets(expression, expression.textOffset, editor) }!!.single() as XmlAttributeValue
        assertEquals(name, target.value)
        val xml = target.containingFile as XmlFile
        val classValue = xml.rootTag!!.getAttribute("Class", "http://schemas.microsoft.com/winfx/2006/xaml")!!.valueElement!!
        val targets = allowAnalysisOnEdt { PsiReferenceService.getService().getReferences(classValue, PsiReferenceService.Hints.NO_HINTS).mapNotNull { it.resolve() } }
        assertTrue(targets.toString(), targets.any { it.containingFile == file })
        val custom = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java).single { it.localName == "GreetingControl" }
        val control = custom.descriptor!!.declaration!!
        assertEquals(source(root, "controls", "sample/controls/GreetingControl.kt").toString().replace('\\', '/'),
            control.containingFile.virtualFile.path)
    }

    private fun assertCompletion(project: Project, root: Path, name: String) {
        val file = kotlinFile(project, root)
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)!!
        val before = document.text
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file.virtualFile), false)!!
        val prefix = name.take(3)
        val offset = before.indexOf("= $name") + 2
        require(offset >= 2)
        try {
            WriteCommandAction.runWriteCommandAction(project) { document.replaceString(offset, offset + name.length, prefix) }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            editor.caretModel.moveToOffset(offset + prefix.length)
            allowAnalysisOnEdt { CodeCompletionHandlerBase(CompletionType.BASIC, true, false, true).invokeCompletion(project, editor) }
            // A cold IDE can hand the invocation back while its contributor is
            // still running. Wait for the actual lookup or native insertion.
            PlatformTestUtil.waitWithEventsDispatching("Real Kotlin completion for $name", {
                name in LookupManager.getActiveLookup(editor)?.items.orEmpty().map { it.lookupString } ||
                    document.text.substring(offset).startsWith(name)
            }, 60)
        } finally {
            LookupManager.getInstance(project).hideActiveLookup()
            WriteCommandAction.runWriteCommandAction(project) { document.setText(before) }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun memberNames(file: KtFile): Set<String> = allowAnalysisOnEdt { analyze(file) {
        file.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtClass>().single().namedClassSymbol!!.memberScope.callables
            .mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet()
    } }

    private fun awaitSmart(project: Project) {
        PlatformTestUtil.waitWithEventsDispatching("Imported source indexing", { !DumbService.isDumb(project) }, 180)
    }

    private fun resolutionDiagnostics(file: KtFile): List<String> = allowAnalysisOnEdt { analyze(file) {
        file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).filter {
            it.factoryName.contains("UNRESOLVED_REFERENCE") || it.factoryName == "NOTHING_TO_OVERRIDE"
        }
            .map { it.defaultMessage }
    } }

    private fun awaitHotReload(service: WinRTHotReloadService) {
        PlatformTestUtil.waitWithEventsDispatching("Native WinUI development handshake", {
            service.state.value.connected && !service.state.value.busy
        }, 30)
    }

    private fun cacheModules(project: Project) = ProjectDataManager.getInstance()
        .getExternalProjectsData(project, GradleConstants.SYSTEM_ID).flatMap { info ->
            info.externalProjectStructure?.let { ExternalSystemApiUtil.findAllRecursively(it, WinRTModuleData.KEY).map { node -> node.data } }.orEmpty()
        }

    private fun cacheKeys(project: Project) = ProjectDataManager.getInstance()
        .getExternalProjectsData(project, GradleConstants.SYSTEM_ID).map { info ->
            val keys = linkedSetOf<String>()
            fun visit(node: DataNode<*>) { keys += node.key.toString(); node.children.forEach(::visit) }
            info.externalProjectStructure?.let(::visit)
            info.externalProjectPath to keys
        }

    private fun assertEditingReady(project: Project, root: Path) {
        val snapshots = project.service<WinRTXamlSnapshotService>()
        PlatformTestUtil.waitWithEventsDispatching("Imported XAML declarations", {
            snapshots.state.value.values.any { it.declarations.pages.any { page -> page.className == "sample.hello.MainWindow" } }
        }, 60)
        // A platform fixture runs on EDT. Blocking waitForSmartMode prevents
        // indexing completion from publishing its state on that same thread.
        PlatformTestUtil.waitWithEventsDispatching("Imported source indexing", {
            !DumbService.isDumb(project)
        }, 180)
        val sdkClasses = KotlinFullClassNameIndex.Helper.get("microsoft.ui.xaml.controls.TextBlock", project,
            com.intellij.psi.search.GlobalSearchScope.allScope(project))
        assertFalse("Generated SDK source must be imported", sdkClasses.isEmpty())
        val kotlinPath = source(root, "app", "sample/hello/MainWindow.kt")
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(kotlinPath)!!
        val file = PsiManager.getInstance(project).findFile(virtualFile) as KtFile
        val module = requireNotNull(com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement(file))
        val settings = requireNotNull(KotlinFacet.get(module)).configuration.settings
        val arguments = settings.compilerArguments
        if (kotlinPath.toString().replace('\\', '/').contains("/src/winuiMain/")) {
            assertTrue("The actual shared source-set facet must retain its metadata compiler: $arguments",
                arguments is K2MetadataCompilerArguments)
        }
        val names = allowAnalysisOnEdt { analyze(file) {
            file.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtClass>().single().namedClassSymbol!!.memberScope.callables
                .mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet()
        } }
        assertTrue("module=${module.name}, projectSettings=${settings.useProjectSettings}, " +
            "options=${arguments?.pluginOptions?.filter { it.startsWith("plugin:io.github.composefluent.winrt.compiler:") }}, " +
            "members=$names", "Greeting" in names)
        assertTrue(names.toString(), "initializeComponent" in names)
        assertTrue(resolutionDiagnostics(file).toString(), resolutionDiagnostics(file).isEmpty())
        // The real SDK owns the ordinary member and the DP registration; neither
        // destination may fall back to a tag's class or a synthetic fixture type.
        val markup = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(
            source(root, "app", "sample/hello/MainWindow.xaml"))!!
        val xml = PsiManager.getInstance(project).findFile(markup) as XmlFile
        val catalog = project.service<io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService>()
        PlatformTestUtil.waitWithEventsDispatching("Imported WinMD XAML property vocabulary", {
            catalog.forFile(markup.path) != null
        }, 60)
        val block = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java).single { it.getAttributeValue("x:Name") == "Greeting" }
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, markup), false)!!
        // On a cold AS reopen, the startup scanner can enqueue indexing while
        // we dispatch events waiting for WinMD. Navigation requires smart mode.
        awaitSmart(project)
        listOf("Text", "FontSize").forEach { property ->
            val attribute = block.getAttribute(property)!!
            val member = requireNotNull(allowAnalysisOnEdt { WinRTXamlAttributeAnalysis.forAttribute(attribute) }) {
                "$property could not resolve after indexing in ${com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement(xml)?.name}"
            }
            assertNotNull("$property needs its actual projected getter", member.primary)
            assertNotNull("$property needs its actual DependencyProperty registration", member.dependencyProperty)
            val expected = property.replaceFirstChar(Char::lowercaseChar)
            assertEquals(expected, (member.primary as org.jetbrains.kotlin.psi.KtNamedDeclaration).name)
            assertEquals(expected + "Property", (member.dependencyProperty as org.jetbrains.kotlin.psi.KtNamedDeclaration).name)
            val offset = attribute.nameElement!!.textOffset + 1
            val destinations = allowAnalysisOnEdt { WinRTXamlAttributeNavigation().getGotoDeclarationTargets(
                xml.findElementAt(offset), offset, editor) }!!.toList()
            assertEquals(member.targets, destinations)
            destinations.forEach { assertTrue(it.containingFile.virtualFile.path,
                it.containingFile.virtualFile.path.contains("/winrt-projections/build/")) }
        }
        // The actual WinUI generic.xaml exceeds the platform's XML PSI limit.
        // Verify the SDK key and original source location, not a small stand-in.
        val resources = project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceIndex>()
        PlatformTestUtil.waitWithEventsDispatching("Actual SDK resource keys", {
            resources.forFile(markup.path)?.let { lookup ->
                io.github.composefluent.winrt.ide.resources.WinRTResourceReferences.resourceKeys(xml, lookup, block, "SubtleButtonStyle").isNotEmpty()
            } == true
        }, 60)
        val style = io.github.composefluent.winrt.ide.resources.WinRTResourceReferences.resourceKeys(xml,
            resources.forFile(markup.path), block, "SubtleButtonStyle").single().element
        assertTrue(style.containingFile.virtualFile.path, style.containingFile.virtualFile.path.endsWith("/Microsoft.UI/Themes/generic.xaml"))
        val styleDocument = FileDocumentManager.getInstance().getDocument(style.containingFile.virtualFile)!!
        assertEquals("SubtleButtonStyle", styleDocument.text.substring(style.textRange.startOffset, style.textRange.endOffset))
        pooled("Actual SDK source navigation") {
            com.intellij.openapi.application.ReadAction.run<RuntimeException> {
                assertNotNull((style as com.intellij.psi.NavigatablePsiElement).navigationRequest())
            }
        }
    }

    private fun sync(project: Project, root: Path) {
        val done = AtomicBoolean()
        val failure = AtomicReference<String?>()
        ExternalSystemUtil.refreshProject(root.toString().replace('\\', '/'), ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC).dontReportRefreshErrors().dontNavigateToError()
            .callback(object : ExternalProjectRefreshCallback {
                override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                    try {
                        if (externalProject == null) failure.set("Gradle returned no project")
                        else ProjectDataManager.getInstance().importData(externalProject, project)
                    } catch (error: Throwable) { failure.set(error.stackTraceToString()) }
                    finally { done.set(true) }
                }
                override fun onFailure(errorMessage: String, errorDetails: String?) {
                    failure.set("$errorMessage\n${errorDetails.orEmpty()}"); done.set(true)
                }
            }))
        PlatformTestUtil.waitWithEventsDispatching("Native Gradle import", { done.get() }, 600)
        assertNull(failure.get(), failure.get())
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        awaitSmart(project)
    }
}
