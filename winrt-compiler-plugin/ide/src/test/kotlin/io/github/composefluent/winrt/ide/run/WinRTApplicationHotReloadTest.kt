package io.github.composefluent.winrt.ide.run

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.settings.WinRTIdeConfigurable
import io.github.composefluent.winrt.ide.settings.WinRTIdeSettings
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol
import java.nio.file.Files
import java.nio.file.Path

/** Exercises the native Gradle Run/Debug state, including its actual settings
 * object, rather than replacing the platform runner with a test executor. */
class WinRTApplicationHotReloadTest : BasePlatformTestCase() {
    private lateinit var root: String
    private var previousEnabled = true
    private val settings get() = service<WinRTIdeSettings>()

    override fun setUp() {
        super.setUp()
        root = project.basePath!!
        previousEnabled = settings.hotReloadEnabled
        settings.hotReloadEnabled = true
        val module = WinRTModuleData(":app", "$root/app", "$root/app/build", "2.4.0", "", emptyList(),
            listOf(WinRTTargetData("jvm", "jvm", emptyList()), WinRTTargetData("mingwX64", "native", emptyList())),
            emptyList(), emptyList(), hotReloadLaunches = listOf(
                WinUIXamlHotReloadLaunchData("runWinAppHostJvmMain", "$root/app.exe", "$root/app"),
                WinUIXamlHotReloadLaunchData("runWinAppPackageJvmMain", "$root/app.exe", "$root/app"),
                WinUIXamlHotReloadLaunchData("runWinAppNativeMingwX64DebugExecutable", "$root/native.exe", "$root/app", ""),
                WinUIXamlHotReloadLaunchData("runWinAppPackageMingwX64DebugExecutable", "$root/native.exe", "$root/app", ""),
                WinUIXamlHotReloadLaunchData("runWinAppNativeMingwX64ReleaseExecutable", "$root/native-release.exe", "$root/app", "")))
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(module))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun execution(task: String, executor: Executor = DefaultRunExecutor.getRunExecutorInstance()) =
        WinRTApplicationConfigurationType.create(project, ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = root
            externalSystemIdString = "GRADLE"
            executionName = "WinRT validation $task"
            taskNames = listOf(":app:$task")
            env = mapOf("USER_OPTION" to "preserved")
            isPassParentEnvs = false
            scriptParameters = "--args=example"
        }).let { saved ->
            val configuration = saved.configuration as WinRTApplicationRunConfiguration
            val environment = ExecutionEnvironmentBuilder.create(executor, saved).build()
            val prepared = configuration.getState(executor, environment)!!
            val state = (prepared as? WinRTDevelopmentRunState)?.delegate ?: prepared
            assertEquals("ExternalSystemRunnableState", state.javaClass.simpleName)
            val nativeSettings = state.javaClass.getDeclaredField("mySettings").apply { isAccessible = true }.get(state)
                as ExternalSystemTaskExecutionSettings
            Triple(configuration, environment, nativeSettings)
        }

    fun testRunAndDebugInjectFreshHotReloadSessionsForPackagedAndUnpackagedAliases() {
        val directories = mutableSetOf<Path>()
        listOf(DefaultRunExecutor.getRunExecutorInstance(), DefaultDebugExecutor.getDebugExecutorInstance()).forEach { executor ->
            listOf("runWindows", "runWinAppHostJvmMain", "runWinAppPackage", "runWinAppPackageJvmMain", "runWinAppNativeMingwX64DebugExecutable",
                "runDebugExecutableMingwX64", "runWinAppPackageMingwX64DebugExecutable").forEach { task ->
                val (configuration, environment, nativeSettings) = execution(task, executor)
                val launch = environment.getUserData(WinRTApplicationHotReload.KEY)!!
                assertEquals(Path.of(root, "app/build/kotlin-winrt/ide-hot-reload"), launch.directory.parent)
                assertTrue(directories.add(launch.directory))
                assertEquals(mapOf("USER_OPTION" to "preserved"), launch.launchEnvironment)
                assertEquals("--args=example", launch.launchArguments)
                assertFalse(launch.passParentEnvironment)
                assertFalse(Files.exists(launch.directory)) // State preparation does not launch or create files.
                assertEquals(launch.directory.toString(), nativeSettings.env[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY])
                assertEquals("preserved", nativeSettings.env["USER_OPTION"])
                assertFalse(nativeSettings.isPassParentEnvs)
                assertEquals(listOf(":app:$task"), nativeSettings.taskNames)
                assertEquals(mapOf("USER_OPTION" to "preserved"), configuration.settings.env)
                assertNotSame(configuration.settings, nativeSettings)
            }
        }
    }

    fun testStagedNativeRunAliasUsesIsolatedLauncherWithoutChangingSavedConfiguration() {
        val models = project.service<WinRTProjectService>()
        val module = models.modules.value.single()
        models.replaceBuildModels(root, listOf(module.copy(hotReloadLaunches = module.hotReloadLaunches.map {
            if (it.taskName == "runWinAppNativeMingwX64DebugExecutable") it.copy(developmentBuildTask = "stageNative") else it
        })))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val (configuration, _, nativeSettings) = execution("runDebugExecutableMingwX64")
        assertEquals(listOf(":app:runWinAppNativeMingwX64DebugExecutable"), nativeSettings.taskNames)
        assertEquals("1", nativeSettings.env["KOTLIN_WINRT_RELEASE_BUILD_LOCK"])
        assertEquals(listOf(":app:runDebugExecutableMingwX64"), configuration.settings.taskNames)
        assertEquals("--args=example", nativeSettings.scriptParameters)
    }

    fun testPlatformGradleRunnerExecutesTheDevelopmentRunState() {
        val (configuration, environment, _) = execution("runDebugExecutableMingwX64")
        var executed = false
        val wrapper = WinRTDevelopmentRunState(com.intellij.execution.configurations.RunProfileState { _, _ ->
            executed = true
            null // No external build is needed to validate the platform runner's dispatch contract.
        }, configuration.settings, project, configuration, environment)
        val runner = Class.forName("com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskRunner")
            .getDeclaredConstructor().newInstance()
        val execute = runner.javaClass.getDeclaredMethod("doExecute",
            com.intellij.execution.configurations.RunProfileState::class.java,
            com.intellij.execution.runners.ExecutionEnvironment::class.java).apply { isAccessible = true }
        assertNull(execute.invoke(runner, wrapper, environment))
        assertTrue("ASC's Gradle runner must not silently ignore the development session", executed)
    }

    fun testNativeReleaseAliasesDiscoverTheReleaseExecutable() {
        listOf("runReleaseExecutableMingwX64", "runWinAppNativeMingwX64ReleaseExecutable").forEach { task ->
            val (_, environment, _) = execution(task)
            assertEquals("$root/native-release.exe", environment.getUserData(WinRTApplicationHotReload.KEY)!!.launch.executable)
        }
    }
    fun testDisabledSettingAndUnknownLaunchKeepOrdinaryGradleExecution() {
        settings.hotReloadEnabled = false
        val (_, disabled, nativeSettings) = execution("runWinAppPackageJvmMain")
        assertNull(disabled.getUserData(WinRTApplicationHotReload.KEY))
        assertEquals(mapOf("USER_OPTION" to "preserved"), nativeSettings.env)
        settings.hotReloadEnabled = true
        val (_, native, unchanged) = execution("runDebugExecutableUnknownTarget")
        assertNull(native.getUserData(WinRTApplicationHotReload.KEY))
        assertEquals(mapOf("USER_OPTION" to "preserved"), unchanged.env)
    }

    fun testHotReloadSettingDefaultsToEnabledAndPersistsTheUsersChoice() {
        val defaults = WinRTIdeSettings()
        assertTrue(defaults.hotReloadEnabled)
        defaults.hotReloadEnabled = false
        val restored = WinRTIdeSettings()
        restored.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(defaults.state), WinRTIdeSettings.State::class.java))
        assertFalse(restored.hotReloadEnabled)
        val configurable = WinRTIdeConfigurable()
        settings.hotReloadEnabled = false
        configurable.reset()
        assertFalse(configurable.isModified)
        settings.hotReloadEnabled = true
        assertTrue(configurable.isModified)
        configurable.apply()
        assertFalse(settings.hotReloadEnabled)
    }

    override fun tearDown() {
        try { settings.hotReloadEnabled = previousEnabled } finally { super.tearDown() }
    }
}
