package io.github.composefluent.winrt.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Expected-failure validation has no outputs.")
abstract class ValidateExpectedFailureTask : DefaultTask() {
    @get:Input
    abstract val commandLine: ListProperty<String>

    @get:Input
    abstract val expectedDiagnostic: Property<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:Input
    abstract val defaultJavaHome: Property<String>

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    init {
        workingDirectory.convention(project.layout.projectDirectory)
        environment.convention(emptyMap())
        defaultJavaHome.convention(project.providers.systemProperty("java.home"))
    }

    @TaskAction
    fun validate() {
        val process = ProcessBuilder(commandLine.get())
            .directory(workingDirectory.get().asFile)
            .redirectErrorStream(true)
        process.environment()["JAVA_HOME"] = defaultJavaHome.get()
        process.environment().putAll(environment.get())
        val runningProcess = process
            .start()
        val output = runningProcess.inputStream.bufferedReader().use { it.readText() }
        val exitCode = runningProcess.waitFor()

        check(exitCode != 0) {
            "Expected nested validation to fail with '${expectedDiagnostic.get()}'."
        }
        check(output.contains(expectedDiagnostic.get())) {
            "Expected diagnostic '${expectedDiagnostic.get()}', but nested validation failed differently:$output"
        }
    }
}
