package io.github.composefluent.winrt.ide

import com.intellij.execution.RunManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.project.WinRTRunConfigurationNames
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
        assertEquals("kept", (custom.configuration as ExternalSystemRunConfiguration).settings.env["MY_OPTION"])
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
