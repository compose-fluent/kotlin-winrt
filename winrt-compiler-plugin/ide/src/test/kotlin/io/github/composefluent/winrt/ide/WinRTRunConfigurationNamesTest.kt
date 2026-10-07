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

    override fun tearDown() {
        try { project.service<WinRTProjectService>().replaceBuildModels(root, emptyList()) }
        finally { super.tearDown() }
    }
}
