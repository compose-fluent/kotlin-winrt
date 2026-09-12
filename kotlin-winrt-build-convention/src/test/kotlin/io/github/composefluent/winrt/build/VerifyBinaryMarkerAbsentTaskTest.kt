package io.github.composefluent.winrt.build

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    fun preserves_an_unchanged_success_report_when_verification_runs_again() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-report-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifact = projectDirectory.resolve("projection.bin")
        val report = projectDirectory.resolve("verification.report")
        Files.write(artifact, byteArrayOf(0, 1, 2, 3))
        val task = project.tasks.create("verifyMarkerReport", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(setOf("forbidden"))
        task.artifactDescription.set("test projection")
        task.verificationReport.set(report.toFile())

        task.verifyMarkerIsAbsent()
        val expectedTime = FileTime.fromMillis(123456789L)
        Files.setLastModifiedTime(report, expectedTime)
        task.verifyMarkerIsAbsent()

        assertTrue(Files.exists(report))
        assertTrue(Files.getLastModifiedTime(report) == expectedTime)
    }

    @Test
    fun removes_a_previous_success_report_when_verification_fails() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-failed-report-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifact = projectDirectory.resolve("projection.bin")
        val report = projectDirectory.resolve("verification.report")
        Files.writeString(artifact, "contains forbidden marker")
        Files.writeString(report, "verified=true\nartifactDescription=test projection\n")
        val task = project.tasks.create("verifyMarkerFailedReport", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(setOf("forbidden marker"))
        task.artifactDescription.set("test projection")
        task.verificationReport.set(report.toFile())

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("Forbidden WinRT call-site marker"))
        assertTrue(!Files.exists(report))
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

    @Test
    fun requires_each_configured_marker_in_the_binary_artifacts() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-required-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifact = projectDirectory.resolve("klib/linkdata/module")
        Files.createDirectories(artifact.parent)
        Files.writeString(artifact, "prefix emitted-thunk suffix")
        val task = project.tasks.create("verifyRequiredMarker", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(emptySet())
        task.requiredMarkers.set(setOf("emitted-thunk"))
        task.artifactDescription.set("test projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun reads_required_markers_from_a_packed_klib() {
        val projectDirectory = Files.createTempDirectory("verify-binary-marker-klib-")
        val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
        val artifact = projectDirectory.resolve("projection.klib")
        ZipOutputStream(Files.newOutputStream(artifact)).use { archive ->
            archive.putNextEntry(ZipEntry("default/ir/debugInfo.knd"))
            archive.write("kotlinWinRTNativeHResultThunk".encodeToByteArray())
            archive.closeEntry()
            archive.putNextEntry(ZipEntry("default/linkdata/module"))
            archive.write("forbidden".encodeToByteArray())
            archive.closeEntry()
        }
        val task = project.tasks.create("verifyPackedKlib", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.archiveEntryPrefixes.set(setOf("default/ir/"))
        task.markers.set(setOf("forbidden"))
        task.requiredMarkers.set(setOf("kotlinWinRTNativeHResultThunk"))
        task.artifactDescription.set("packed projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun scoped_marker_check_ignores_legal_codec_methods() {
        val artifact = copyFixtureClass("ScopedMarkerFixture")
        val project = ProjectBuilder.builder().withProjectDir(artifact.parent.toFile()).build()
        val task = project.tasks.create("verifyScopedMarker", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(setOf("allocateBytes"))
        task.methodNamePrefixes.set(setOf("callSite_"))
        task.artifactDescription.set("scoped projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure == null)
    }

    @Test
    fun scoped_marker_check_reports_the_call_site_method() {
        val artifact = copyFixtureClass("ScopedMarkerCallSiteFixture")
        val project = ProjectBuilder.builder().withProjectDir(artifact.parent.toFile()).build()
        val task = project.tasks.create("verifyScopedMarkerFailure", VerifyBinaryMarkerAbsentTask::class.java)
        task.binaryArtifacts.from(artifact.toFile())
        task.markers.set(setOf("allocateBytes"))
        task.methodNamePrefixes.set(setOf("callSite_"))
        task.artifactDescription.set("scoped projection")

        val failure = runCatching { task.verifyMarkerIsAbsent() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("method 'callSite_bad'"))
    }

    private fun copyFixtureClass(simpleName: String): Path {
        val binaryName = "${VerifyBinaryMarkerAbsentTaskTest::class.java.name.substringBeforeLast('$')}$" +
            simpleName
        val resourceName = binaryName.replace('.', '/') + ".class"
        val resource = VerifyBinaryMarkerAbsentTaskTest::class.java.classLoader
            .getResourceAsStream(resourceName)
            ?: error("Missing test fixture class $resourceName")
        val directory = Files.createTempDirectory("verify-binary-marker-scoped-")
        return directory.resolve("$simpleName.class").also { target ->
            resource.use { input -> Files.copy(input, target) }
        }
    }

    private class ScopedMarkerFixture {
        fun callSite_clean() = Unit

        fun codec_allocateBytes() = "allocateBytes"
    }

    private class ScopedMarkerCallSiteFixture {
        fun callSite_bad() = "allocateBytes"
    }
}
