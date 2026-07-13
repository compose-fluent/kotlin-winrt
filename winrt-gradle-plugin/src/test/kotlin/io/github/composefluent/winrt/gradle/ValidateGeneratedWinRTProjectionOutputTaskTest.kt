package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidateGeneratedWinRTProjectionOutputTaskTest {
    @Test
    fun generated_projection_output_audit_reports_duplicate_class_path_and_both_owners() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-cross-artifact-audit-")
        val sourceRoot = root.resolve("src")
        val windowsSdkClasses = root.resolve("windows-sdk")
        val appSdkClasses = root.resolve("windows-app-sdk")
        val duplicatePath = "windows/foundation/IStringable.class"
        Files.createDirectories(sourceRoot)
        Files.createDirectories(windowsSdkClasses.resolve("windows/foundation"))
        Files.createDirectories(appSdkClasses.resolve("windows/foundation"))
        Files.write(windowsSdkClasses.resolve(duplicatePath), byteArrayOf(1))
        Files.write(appSdkClasses.resolve(duplicatePath), byteArrayOf(2))

        val task = project.tasks.register(
            "auditGeneratedProjectionOutputUnderTest",
            ValidateGeneratedWinRTProjectionOutputTask::class.java,
        ) { registeredTask ->
            registeredTask.generatedSourcesDirectory.set(project.layout.dir(project.provider { sourceRoot.toFile() }))
            registeredTask.crossArtifactClassOwners.set(listOf("windows-sdk", "windows-app-sdk"))
            registeredTask.crossArtifactClassDirectories.from(windowsSdkClasses.toFile(), appSdkClasses.toFile())
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()
        val message = failure?.message.orEmpty()

        assertTrue(failure is IllegalStateException)
        assertTrue(message, message.contains(duplicatePath))
        assertTrue(message, message.contains("windows-sdk"))
        assertTrue(message, message.contains("windows-app-sdk"))
    }

    @Test
    fun generated_projection_output_audit_rejects_duplicate_compiler_support_manifest_class() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-cross-artifact-manifest-audit-")
        val sourceRoot = root.resolve("src")
        val windowsSdkClasses = root.resolve("windows-sdk")
        val appSdkClasses = root.resolve("windows-app-sdk")
        val duplicatePath = "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest.class"
        Files.createDirectories(sourceRoot)
        Files.createDirectories(windowsSdkClasses.resolve(duplicatePath).parent)
        Files.createDirectories(appSdkClasses.resolve(duplicatePath).parent)
        Files.write(windowsSdkClasses.resolve(duplicatePath), byteArrayOf(1))
        Files.write(appSdkClasses.resolve(duplicatePath), byteArrayOf(2))

        val task = project.tasks.register(
            "auditDuplicateCompilerSupportManifest",
            ValidateGeneratedWinRTProjectionOutputTask::class.java,
        ) { registeredTask ->
            registeredTask.generatedSourcesDirectory.set(project.layout.dir(project.provider { sourceRoot.toFile() }))
            registeredTask.crossArtifactClassOwners.set(listOf("windows-sdk", "windows-app-sdk"))
            registeredTask.crossArtifactClassDirectories.from(windowsSdkClasses.toFile(), appSdkClasses.toFile())
        }.get()

        val failure = runCatching { task.validate() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains(duplicatePath))
    }
}
