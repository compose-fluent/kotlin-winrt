package io.github.composefluent.winrt.ide

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.project.WinRTRunConfigurationNames
import io.github.composefluent.winrt.ide.run.WinRTApplicationConfigurationType
import io.github.composefluent.winrt.ide.run.WinRTApplicationRunConfiguration
import org.jdom.Element
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants

class WinRTRunConfigurationNamesTest : BasePlatformTestCase() {
    private val root = "E:/winrt-run-name-fixture"
    private val moduleData = WinRTModuleData(":winui-gallery", "$root/winui-gallery", "$root/winui-gallery/build", "2.4.0", "",
        emptyList(), listOf(WinRTTargetData("winuiJvm", "jvm", emptyList()), WinRTTargetData("mingwX64", "native", emptyList())),
        emptyList(), emptyList())

    private fun configuration(task: String) = ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(
        ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = root
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            taskNames = listOf(":winui-gallery:$task")
        }, project, GradleConstants.SYSTEM_ID)!!

    override fun setUp() {
        super.setUp()
        val manager = RunManager.getInstance(project)
        manager.allSettings.toList().forEach(manager::removeConfiguration)
        manager.selectedConfiguration = null
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(moduleData))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun testNativeGradleConfigurationsAreRenamedOnCreationAndKeepTheirTaskAndEnvironment() {
        val settings = configuration("runWinAppPackageWinuiJvmMain")
        val original = (settings.configuration as ExternalSystemRunConfiguration).settings
        original.env = mapOf("WINRT_DEVELOPMENT_SETTING" to "preserved")
        val manager = RunManager.getInstance(project)
        manager.addConfiguration(settings)
        manager.selectedConfiguration = settings
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("winui-gallery:Run[jvm, packaged]", settings.name)
        assertInstanceOf(settings.configuration, WinRTApplicationRunConfiguration::class.java)
        assertEquals(WinRTApplicationConfigurationType.ID, settings.type.id)
        assertSame(settings, manager.selectedConfiguration)
        assertEquals(listOf(":winui-gallery:runWinAppPackageWinuiJvmMain"), original.taskNames)
        assertEquals("preserved", original.env["WINRT_DEVELOPMENT_SETTING"])
        assertEquals("winui-gallery:Run[mingwX64, packaged]", WinRTRunConfigurationNames.taskName(moduleData, "runWinAppPackageMingwX64MainDebugExecutable"))
        assertEquals("winui-gallery:Run[jvm, unpackaged]", WinRTRunConfigurationNames.taskName(moduleData, "runWindows"))
    }

    fun testCustomNamesTestsAndForeignBuildsArePreserved() {
        val names = project.service<WinRTRunConfigurationNames>()
        val custom = configuration("runWinAppPackageWinuiJvmMain")
        custom.configuration.name = "My custom gallery run"
        (custom.configuration as ExternalSystemRunConfiguration).setNameChangedByUser(true)
        assertFalse(names.rename(custom, listOf(moduleData)))
        assertEquals("My custom gallery run", custom.name)
        assertFalse(names.rename(configuration("test"), listOf(moduleData)))
        val foreign = configuration("runWinAppPackageWinuiJvmMain")
        (foreign.configuration as ExternalSystemRunConfiguration).settings.externalProjectPath = "E:/other-build"
        assertFalse(names.rename(foreign, listOf(moduleData)))
        assertNull(WinRTRunConfigurationNames.taskName(moduleData, "runWinAppPackageUnknownTargetMain"))
    }

    fun testImportCreatesRunnableApplicationsOnceAndSelectsPackagedJvm() {
        val imported = moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain", "runDebugExecutableMingwX64",
            "runReleaseExecutableMingwX64", "runWinAppHostWinuiJvmMain", "runWinAppHost"))
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(imported))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val manager = RunManager.getInstance(project)
        assertEquals(setOf("winui-gallery:Run[jvm, packaged]", "winui-gallery:Run[jvm, unpackaged]", "winui-gallery:Run[mingwX64, unpackaged]"), manager.allSettings.map { it.name }.toSet())
        assertEquals("winui-gallery:Run[jvm, packaged]", manager.selectedConfiguration!!.name)
        manager.allSettings.forEach {
            assertInstanceOf(it.configuration, WinRTApplicationRunConfiguration::class.java)
            assertEquals(WinRTApplicationConfigurationType.ID, it.type.id)
            assertFalse(it.isTemporary)
            assertNotNull(ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, it.configuration))
            assertNotNull(ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, it.configuration))
        }
        val native = manager.allSettings.single { it.name.contains("mingwX64") }.configuration as ExternalSystemRunConfiguration
        assertEquals(listOf(":winui-gallery:runDebugExecutableMingwX64"), native.settings.taskNames)
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(imported))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(3, manager.allSettings.size)
        assertEquals(3, manager.allSettings.map { (it.configuration as ExternalSystemRunConfiguration).settings.taskNames }.toSet().size)
    }

    fun testImportKeepsTheExistingCustomRunAndItsSelectionAndEnvironment() {
        val custom = configuration("runWinAppPackageWinuiJvmMain")
        custom.configuration.name = "My gallery"
        (custom.configuration as ExternalSystemRunConfiguration).settings.env = mapOf("MY_OPTION" to "kept")
        val manager = RunManager.getInstance(project)
        manager.addConfiguration(custom); manager.selectedConfiguration = custom
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain"))))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf(custom), manager.allSettings)
        assertSame(custom, manager.selectedConfiguration)
        assertEquals("My gallery", custom.name)
        assertInstanceOf(custom.configuration, WinRTApplicationRunConfiguration::class.java)
        assertEquals("kept", (custom.configuration as ExternalSystemRunConfiguration).settings.env["MY_OPTION"])
    }

    fun testMigrationKeepsRunnerOptionsFoldersSharedStorageAndCustomNames() {
        val custom = configuration("runWinAppPackageWinuiJvmMain") as RunnerAndConfigurationSettingsImpl
        custom.name = "Gallery with my settings"
        custom.folderName = "Windows apps"
        custom.isEditBeforeRun = true
        custom.isActivateToolWindowBeforeRun = false
        custom.isFocusToolWindowBeforeRun = true
        val storage = "${project.basePath}/.run/Gallery.run.xml"
        custom.storeInArbitraryFileInProject(storage)
        val original = custom.configuration as GradleRunConfiguration
        original.setNameChangedByUser(true)
        original.isDebugAllEnabled = true
        original.isReattachDebugProcess = true
        original.isAllowRunningInParallel = true
        original.defaultTargetName = "My local target"
        original.settings.apply {
            scriptParameters = "--offline -Pmy-option=value"
            vmOptions = "-Xmx2g"
            env = mapOf("MY_OPTION" to " value=kept ")
            isPassParentEnvs = false
        }
        val scheme = custom.writeScheme().apply {
            addContent(Element("RunnerSettings").setAttribute("RunnerId", "temporarily.unavailable.runner")
                .addContent(Element("option").setAttribute("name", "MY_RUNNER_OPTION").setAttribute("value", "preserved")))
        }
        custom.readExternal(scheme, true, storage)
        val manager = RunManager.getInstance(project)
        manager.addConfiguration(custom)
        manager.selectedConfiguration = custom
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain"))))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertSame(custom, manager.selectedConfiguration)
        assertEquals(listOf(custom), manager.allSettings)
        val migrated = custom.configuration as WinRTApplicationRunConfiguration
        assertEquals("Gallery with my settings", custom.name)
        assertEquals("Windows apps", custom.folderName)
        assertTrue(custom.isStoredInArbitraryFileInProject)
        assertEquals(storage, custom.pathIfStoredInArbitraryFileInProject)
        assertTrue(custom.isEditBeforeRun)
        assertFalse(custom.isActivateToolWindowBeforeRun)
        assertTrue(custom.isFocusToolWindowBeforeRun)
        assertTrue(migrated.isDebugAllEnabled)
        assertTrue(migrated.isReattachDebugProcess)
        assertTrue(migrated.isAllowRunningInParallel)
        assertEquals("My local target", migrated.defaultTargetName)
        assertEquals("--offline -Pmy-option=value", migrated.settings.scriptParameters)
        assertEquals("-Xmx2g", migrated.settings.vmOptions)
        assertEquals(" value=kept ", migrated.settings.env["MY_OPTION"])
        assertFalse(migrated.settings.isPassParentEnvs)
        assertEquals("preserved", custom.writeScheme().getChild("RunnerSettings").getChild("option").getAttributeValue("value"))
        assertTrue(custom.uniqueID.startsWith("Kotlin WinRT Application."))
    }

    fun testImportedApplicationsReloadAsTheirOwnTypeAndStabilizeExistingTemporaryGradleRuns() {
        val temporary = configuration("runWinAppPackageWinuiJvmMain")
        val manager = RunManager.getInstance(project) as RunManagerImpl
        manager.setTemporaryConfiguration(temporary)
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain"))))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(1, manager.allSettings.size)
        assertInstanceOf(temporary.configuration, WinRTApplicationRunConfiguration::class.java)
        assertFalse(temporary.isTemporary)
        val persisted = (temporary as RunnerAndConfigurationSettingsImpl).writeScheme()
        assertEquals(WinRTApplicationConfigurationType.ID, persisted.getAttributeValue("type"))
        manager.removeConfiguration(temporary)
        val restored = RunnerAndConfigurationSettingsImpl(manager)
        restored.readExternal(persisted, false)
        manager.addConfiguration(restored)
        manager.selectedConfiguration = restored
        project.service<WinRTRunConfigurationNames>().refresh()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf(restored), manager.allSettings)
        assertSame(restored, manager.selectedConfiguration)
        assertInstanceOf(restored.configuration, WinRTApplicationRunConfiguration::class.java)
        assertEquals(listOf(":winui-gallery:runWinAppPackageWinuiJvmMain"), (restored.configuration as WinRTApplicationRunConfiguration).settings.taskNames)
    }

    fun testApplicationTypeIsRegisteredAndItsComposeEditorPreservesSavedOptions() {
        val type = ConfigurationTypeUtil.findConfigurationType(WinRTApplicationConfigurationType::class.java)
        assertEquals("Kotlin WinRT Application", type.displayName)
        assertEquals(16, type.icon.iconWidth)
        assertEquals(16, type.icon.iconHeight)
        assertTrue(type.configurationFactories.single().isEditableInDumbMode)
        val execution = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = root
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            executionName = "Saved application"
            taskNames = listOf(":winui-gallery:runWinAppPackageWinuiJvmMain")
            scriptParameters = "--offline"
            vmOptions = "-Xmx2g"
            env = mapOf("MY_OPTION" to "keep=both=sides", "EMPTY_OPTION" to "")
            isPassParentEnvs = false
        }
        val config = WinRTApplicationConfigurationType.create(project, execution).configuration as WinRTApplicationRunConfiguration
        assertEquals(type.icon, config.icon)
        val editor = config.configurationEditor
        try {
            editor.resetFrom(config)
            assertNotNull(com.intellij.util.ui.UIUtil.findComponentOfType(editor.component, androidx.compose.ui.awt.ComposePanel::class.java))
            editor.applyTo(config)
            assertEquals(execution.taskNames, config.settings.taskNames)
            assertEquals(execution.scriptParameters, config.settings.scriptParameters)
            assertEquals(execution.vmOptions, config.settings.vmOptions)
            assertEquals(execution.env, config.settings.env)
            assertFalse(config.settings.isPassParentEnvs)
            config.checkConfiguration()
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
    }

    fun testMigrationLeavesTestsForeignBuildsAndTaskSequencesAsGradleProfiles() {
        val tests = configuration("test")
        val foreign = configuration("runWinAppPackageWinuiJvmMain")
        (foreign.configuration as GradleRunConfiguration).settings.externalProjectPath = "E:/other-build"
        val sequence = configuration("runWinAppPackageWinuiJvmMain")
        (sequence.configuration as GradleRunConfiguration).settings.taskNames = listOf(":winui-gallery:clean", ":winui-gallery:runWinAppPackageWinuiJvmMain")
        val manager = RunManager.getInstance(project)
        listOf(tests, foreign, sequence).forEach(manager::addConfiguration)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        listOf(tests, foreign, sequence).forEach {
            assertInstanceOf(it.configuration, GradleRunConfiguration::class.java)
            assertFalse(it.configuration is WinRTApplicationRunConfiguration)
        }
    }

    fun testCustomApplicationNameSurvivesEditingAndLaterImports() {
        val config = WinRTApplicationConfigurationType.create(project, ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = root
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            executionName = "My gallery"
            taskNames = listOf(":winui-gallery:runWinAppPackageWinuiJvmMain")
        })
        val application = config.configuration as WinRTApplicationRunConfiguration
        val editor = application.configurationEditor
        try {
            editor.resetFrom(application)
            editor.applyTo(application)
        } finally { com.intellij.openapi.util.Disposer.dispose(editor) }
        val manager = RunManager.getInstance(project)
        manager.addConfiguration(config)
        manager.selectedConfiguration = config
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain"))))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertSame(config, manager.selectedConfiguration)
        assertEquals("My gallery", config.name)
        assertEquals(listOf(config), manager.allSettings)
    }

    fun testLinkedBuildsWithTheSameApplicationPathKeepSeparateProfiles() {
        val otherRoot = "E:/second-winrt-build"
        val imported = moduleData.copy(runTasks = listOf("runWinAppPackageWinuiJvmMain"))
        val other = imported.copy(projectDirectory = "$otherRoot/winui-gallery", buildDirectory = "$otherRoot/winui-gallery/build")
        val service = project.service<WinRTProjectService>()
        service.replaceBuildModels(root, listOf(imported))
        service.replaceBuildModels(otherRoot, listOf(other))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val manager = RunManager.getInstance(project)
        assertEquals(2, manager.allSettings.size)
        assertEquals(2, manager.allSettings.map { it.uniqueID }.distinct().size)
        assertEquals(setOf(root, otherRoot), manager.allSettings.map {
            (it.configuration as WinRTApplicationRunConfiguration).settings.externalProjectPath
        }.toSet())
        service.replaceBuildModels(root, listOf(imported))
        service.replaceBuildModels(otherRoot, listOf(other))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(2, manager.allSettings.size)
        service.replaceBuildModels(otherRoot, emptyList())
    }

    override fun tearDown() {
        try {
            project.service<WinRTProjectService>().replaceBuildModels(root, emptyList())
            val manager = RunManager.getInstance(project)
            manager.allSettings.toList().forEach(manager::removeConfiguration)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        finally { super.tearDown() }
    }
}
