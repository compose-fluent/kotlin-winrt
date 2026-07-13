package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateExpectedFailureTaskTest {
    @Test
    fun expected_failure_task_requires_the_configured_diagnostic() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create(
            "validateExpectedFailureUnderTest",
            ValidateExpectedFailureTask::class.java,
        )
        task.commandLine.set(listOf("cmd", "/c", "echo duplicate identity owner & exit /b 1"))
        task.expectedDiagnostic.set("duplicate identity owner")

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun expected_failure_task_defaults_java_home_to_the_current_gradle_jvm() {
        val project = ProjectBuilder.builder().build()
        val currentJavaHome = System.getProperty("java.home")
        val task = project.tasks.create(
            "validateExpectedFailureJavaHomeUnderTest",
            ValidateExpectedFailureTask::class.java,
        )
        task.commandLine.set(listOf("cmd", "/c", "echo %JAVA_HOME% & exit /b 1"))
        task.expectedDiagnostic.set(currentJavaHome)

        val failure = runCatching { task.validate() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }
}
