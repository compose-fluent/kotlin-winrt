package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ValidatePrebuiltProjectionOutputTaskTest {
    @Test
    fun generated_projection_output_audit_rejects_duplicates_fallbacks_and_growth() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-prebuilt-output-limits-")
        val sourceRoot = root.resolve("src")
        val classRoot = root.resolve("classes")
        Files.createDirectories(sourceRoot.resolve("sample"))
        Files.createDirectories(classRoot.resolve("sample"))
        Files.writeString(
            sourceRoot.resolve("sample/WidgetA.kt"),
            """
            package sample

            internal object Duplicate
            internal fun first(sourceType: String) = when (sourceType) {
                "A" -> Unit
                else -> Unit
            }
            internal val stale = "WinRTGenericAbiRegistry"
            """.trimIndent(),
        )
        Files.writeString(
            sourceRoot.resolve("sample/WidgetB.kt"),
            """
            package sample

            internal object Duplicate
            internal fun second(sourceType: String) = when (sourceType) {
                "B" -> Unit
                else -> Unit
            }
            """.trimIndent(),
        )
        Files.write(classRoot.resolve("sample/Large.class"), ByteArray(16))
        val task = project.tasks.create(
            "auditPrebuiltProjectionOutputLimits",
            ValidatePrebuiltProjectionOutputTask::class.java,
        )
        task.generatedSourcesDirectory.set(project.layout.dir(project.provider { sourceRoot.toFile() }))
        task.compiledClassesDirectories.from(classRoot.toFile())
        task.maxTotalKotlinSourceBytes.set(64)
        task.maxKotlinSourceFileLines.set(4)
        task.maxClassFileBytes.set(8)
        task.maxTotalClassBytes.set(8)

        val failure = runCatching { task.validate() }.exceptionOrNull()
        val message = failure?.message.orEmpty()

        assertTrue(failure is IllegalStateException)
        assertTrue(message, message.contains("WinRTGenericAbiRegistry"))
        assertTrue(message, message.contains("duplicate top-level FQN: sample.Duplicate"))
        assertTrue(message, message.contains("repeated type/category branch table"))
        assertTrue(message, message.contains("generated Kotlin source is"))
        assertTrue(message, message.contains("WidgetA.kt has"))
        assertTrue(message, message.contains("Large.class is"))
    }

    @Test
    fun generated_projection_output_audit_reports_duplicate_class_path_and_both_direct_reference_owners() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-prebuilt-output-audit-")
        val sourceRoot = root.resolve("src")
        val sdkClasses = root.resolve("sdk")
        val projectionClasses = root.resolve("projection")
        val duplicatePath = "windows/foundation/IStringable.class"
        Files.createDirectories(sourceRoot)
        Files.createDirectories(sdkClasses.resolve("windows/foundation"))
        Files.createDirectories(projectionClasses.resolve("windows/foundation"))
        Files.write(sdkClasses.resolve(duplicatePath), byteArrayOf(1))
        Files.write(projectionClasses.resolve(duplicatePath), byteArrayOf(2))

        val task = project.tasks.create(
            "auditPrebuiltProjectionOutputUnderTest",
            ValidatePrebuiltProjectionOutputTask::class.java,
        )
        task.generatedSourcesDirectory.set(project.layout.dir(project.provider { sourceRoot.toFile() }))
        task.crossArtifactClassOwners.set(listOf("projection", "sdk"))
        task.crossArtifactClassDirectories.from(projectionClasses.toFile(), sdkClasses.toFile())

        val failure = runCatching { task.validate() }.exceptionOrNull()
        val message = failure?.message.orEmpty()

        assertTrue(failure is IllegalStateException)
        assertTrue(message, message.contains(duplicatePath))
        assertTrue(message, message.contains("projection"))
        assertTrue(message, message.contains("sdk"))
    }
}
