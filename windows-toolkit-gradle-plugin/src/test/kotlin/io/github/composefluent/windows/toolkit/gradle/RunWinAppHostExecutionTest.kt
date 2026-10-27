package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.*
import org.junit.Test

class RunWinAppHostExecutionTest {
    @Test fun unchanged_host_runs_again_with_and_without_an_output_log() {
        val root = Files.createTempDirectory("winrt-repeat-launch-")
        Files.createDirectories(root.resolve("work"))
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'repeat-launch'")
        Files.writeString(root.resolve("build.gradle"), """
            plugins { id 'io.github.compose-fluent.windows-toolkit' apply false }
            ['launch', 'loggedLaunch'].each { taskName ->
                tasks.register(taskName, io.github.composefluent.windows.toolkit.gradle.RunWinAppHostTask) {
                    hostExecutable.set(new File(System.getProperty('java.home'), 'bin/java.exe'))
                    workingDirectory.set(layout.projectDirectory.dir('work'))
                    args.set(['-version'])
                    noLaunch.set(false)
                    detach.set(false)
                    designPreview.set(false)
                    if (taskName == 'loggedLaunch') outputLog.set(layout.projectDirectory.file('logs/application.log'))
                }
            }
        """.trimIndent())
        fun run() = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments("launch", "loggedLaunch", "--offline", "--max-workers=2", "--stacktrace").build()
        val first = run()
        val second = run()
        for (name in listOf(":launch", ":loggedLaunch")) {
            assertEquals(first.output, TaskOutcome.SUCCESS, first.task(name)?.outcome)
            assertEquals(second.output, TaskOutcome.SUCCESS, second.task(name)?.outcome)
        }
        assertTrue(Files.readString(root.resolve("logs/application.log")).contains("version"))
    }
}
