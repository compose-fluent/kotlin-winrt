package io.github.composefluent.winrt.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.impl.stores.IProjectStore
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
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.psi.KtFile
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
        val manager = ProjectManagerEx.getInstanceEx()
        var imported = manager.openProject(root, OpenProjectTask {
            isNewProject = true
            useDefaultProjectAsTemplate = false
            forceOpenInNewFrame = true
            runConfigurators = false
            createModule = false
            projectName = "WinRT import validation"
            beforeInit = { it.putUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED, true) }
        })!!
        try {
            ApplicationManager.getApplication().runWriteAction {
                ProjectJdkTable.getInstance().addJdk(sdk)
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
            // Unit-test mode suppresses the automatic save scheduler. Persist
            // through the actual platform stores before closing this project.
            PlatformTestUtil.saveProject(imported, true)
            ExternalProjectsDataStorage.getInstance(imported).doSave()
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
            // Platform unit tests disable automatic workspace-model cache
            // persistence. Reapply the actually deserialized native Gradle graph
            // through its importer to recover source roots/facets for analysis.
            // This does not resolve Gradle again or inject any model/test types.
            val cached = ProjectDataManager.getInstance().getExternalProjectsData(imported, GradleConstants.SYSTEM_ID)
                .single { it.externalProjectPath == root.toString().replace('\\', '/') }.externalProjectStructure!!
            val reopened = imported
            val complete = AtomicBoolean()
            val importFailure = AtomicReference<Throwable?>()
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProgressManager.getInstance().runProcess({ ProjectDataManager.getInstance().importData(cached, reopened) }, EmptyProgressIndicator())
                } catch (error: Throwable) { importFailure.set(error) }
                finally { complete.set(true) }
            }
            PlatformTestUtil.waitWithEventsDispatching("Import deserialized Gradle graph", { complete.get() }, 180)
            importFailure.get()?.let { throw AssertionError("Cached Gradle import failed", it) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertEditingReady(imported, root)
        } finally {
            manager.saveAndForceCloseProject(imported)
            ApplicationManager.getApplication().runWriteAction {
                // KMP import also creates the platform's Kotlin SDK.
                ProjectJdkTable.getInstance().allJdks.filterNot { it in previousSdks }
                    .forEach { ProjectJdkTable.getInstance().removeJdk(it) }
            }
        }
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
        val source = root.resolve("app/src/main/kotlin/sample/hello/MainWindow.kt")
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source)!!
        val file = PsiManager.getInstance(project).findFile(virtualFile) as KtFile
        val names = allowAnalysisOnEdt { analyze(file) {
            file.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtClass>().single().namedClassSymbol!!.memberScope.callables
                .mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet()
        } }
        assertTrue(names.toString(), "Greeting" in names)
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
    }
}
