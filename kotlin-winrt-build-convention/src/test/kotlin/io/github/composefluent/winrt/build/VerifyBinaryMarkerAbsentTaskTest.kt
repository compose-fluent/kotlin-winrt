package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class VerifyBinaryMarkerAbsentTaskTest {
    @Test
    fun accepts_binary_artifacts_without_the_marker() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-absent-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifact = projectDirectory.resolve("projection.bin")
        Files.write(artifact, byteArrayOf(0, 1, 2, 3))
        val task = project.tasks.create("verifyMarkerAbsent", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(setOf("module call-site placeholder", "confinedScope"))
        task.artifactDescription.set("test projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun rejects_a_marker_nested_in_a_binary_artifact_directory() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-present-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifactDirectory = projectDirectory.resolve("klib")
        val artifact = artifactDirectory.resolve("linkdata/module")
        Files.createDirectories(artifact.parent)
        Files.writeString(artifact, "prefix module call-site placeholder suffix")
        val task = project.tasks.create("verifyMarkerPresent", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifactDirectory.toFile())
        task.markers.set(setOf("confinedScope", "module call-site placeholder"))
        task.artifactDescription.set("test projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains(artifact.toString()))
    }
}
