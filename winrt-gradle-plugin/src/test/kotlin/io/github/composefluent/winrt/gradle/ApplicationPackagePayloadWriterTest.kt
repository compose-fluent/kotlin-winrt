package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationPackagePayloadWriterTest {
    @Test
    fun resolves_dependency_convention_and_explicit_override_in_priority_order() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-resolution-")
        val dependency = root.resolve("dependency/Assets/Icon.png")
        val convention = root.resolve("src/winuiMain/appxResources/Assets/Icon.png")
        val explicit = root.resolve("override/Icon.png")
        listOf(dependency, convention, explicit).forEach { file ->
            Files.createDirectories(file.parent)
            Files.writeString(file, file.toString())
        }

        val decisions = ApplicationPackagePayloadWriter.resolvePackagePayloads(
            conventionInputs = listOf(AppxResourceInput(convention, Path.of("Assets/Icon.png"))),
            dependencyInputs = listOf(AppxResourceInput(dependency, Path.of("Assets/Icon.png"))),
            explicitPayloadFiles = listOf(explicit),
            rootPayloadFiles = emptyList(),
            projectRoot = root,
            targetPaths = mapOf(explicit.toAbsolutePath().normalize().toString() to "Assets/Icon.png"),
            excludedPaths = emptySet(),
        )

        assertEquals(1, decisions.size)
        assertEquals(explicit.toAbsolutePath().normalize(), decisions.single().source)
        assertEquals(Path.of("Assets/Icon.png"), decisions.single().target)
        assertEquals("explicit packagePayload", decisions.single().origin)
        assertEquals(convention.toAbsolutePath().normalize(), decisions.single().overriddenSource)
    }

    @Test
    fun rejects_same_level_case_insensitive_conflict() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-conflict-")
        val first = root.resolve("one.png")
        val second = root.resolve("two.png")
        Files.writeString(first, "one")
        Files.writeString(second, "two")

        val error = runCatching {
            ApplicationPackagePayloadWriter.resolvePackagePayloads(
                conventionInputs = listOf(
                    AppxResourceInput(first, Path.of("Assets/Icon.png")),
                    AppxResourceInput(second, Path.of("assets/icon.PNG")),
                ),
                explicitPayloadFiles = emptyList(),
                rootPayloadFiles = emptyList(),
                projectRoot = root,
                targetPaths = emptyMap(),
                excludedPaths = emptySet(),
            )
        }.exceptionOrNull()

        assertTrue(error is org.gradle.api.GradleException)
        assertTrue(error!!.message.orEmpty().contains("Conflicting appxResources files"))
    }

    @Test
    fun single_file_payload_defaults_to_its_file_name() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-single-")
        val payload = root.resolve("payload.bin")
        Files.writeString(payload, "payload")

        val decisions = ApplicationPackagePayloadWriter.resolvePackagePayloads(
            conventionInputs = emptyList(),
            explicitPayloadFiles = listOf(payload),
            rootPayloadFiles = emptyList(),
            projectRoot = null,
            targetPaths = emptyMap(),
            excludedPaths = emptySet(),
        )

        assertEquals(Path.of("payload.bin"), decisions.single().target)
    }

    @Test
    fun rejects_unsafe_and_reserved_targets() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-safety-")
        val payload = root.resolve("payload.bin")
        Files.writeString(payload, "payload")

        val unsafe = runCatching {
            ApplicationPackagePayloadWriter.resolvePackagePayloads(
                conventionInputs = listOf(AppxResourceInput(payload, Path.of("../escape.bin"))),
                explicitPayloadFiles = emptyList(),
                rootPayloadFiles = emptyList(),
                projectRoot = root,
                targetPaths = emptyMap(),
                excludedPaths = emptySet(),
            )
        }.exceptionOrNull()
        assertTrue(unsafe is IllegalArgumentException)

        val reserved = runCatching {
            ApplicationPackagePayloadWriter.resolvePackagePayloads(
                conventionInputs = emptyList(),
                explicitPayloadFiles = listOf(payload),
                rootPayloadFiles = emptyList(),
                projectRoot = root,
                targetPaths = mapOf(payload.toString() to "AppxManifest.xml"),
                excludedPaths = emptySet(),
            )
        }.exceptionOrNull()
        assertTrue(reserved is org.gradle.api.GradleException)
        assertTrue(reserved!!.message.orEmpty().contains("reserved AppxManifest.xml"))
    }

    @Test
    fun resolution_report_requires_every_selected_file_in_unpacked_package() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-report-")
        val source = root.resolve("source.png")
        val packageRoot = root.resolve("package")
        val staged = packageRoot.resolve("Assets/Icon.png")
        Files.writeString(source, "source")
        Files.createDirectories(staged.parent)
        Files.writeString(staged, "source")
        val report = root.resolve("report.json")
        ApplicationPackagePayloadWriter.writeResolutionReport(
            report,
            listOf(PackagePayloadDecision(source, Path.of("Assets/Icon.png"), "appxResources")),
        )

        assertTrue(ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot).isEmpty())
        Files.delete(staged)
        val errors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(errors.any { it.contains("Assets/Icon.png") && it.contains("missing") })
    }

    @Test
    fun explicit_payload_mapping_uses_case_insensitive_source_paths() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-case-")
        val payload = root.resolve("Assets/Icon.png")
        Files.createDirectories(payload.parent)
        Files.writeString(payload, "icon")

        val decisions = ApplicationPackagePayloadWriter.resolvePackagePayloads(
            conventionInputs = emptyList(),
            explicitPayloadFiles = listOf(payload),
            rootPayloadFiles = emptyList(),
            projectRoot = root,
            targetPaths = mapOf(
                payload.toString().replace("Assets", "assets") to "Resources/Icon.png",
            ),
            excludedPaths = emptySet(),
        )

        assertEquals(Path.of("Resources/Icon.png"), decisions.single().target)
    }

    @Test
    fun pri_mapping_uses_case_insensitive_source_paths() {
        val root = Files.createTempDirectory("kotlin-winrt-pri-case-")
        val source = root.resolve("Resources/Strings.resw")
        val priRoot = root.resolve("pri")
        Files.createDirectories(source.parent)
        Files.writeString(source, "strings")

        ProjectPriInputStager(
            projectPriRoot = priRoot,
            projectPriInitialPath = "",
            defaultProjectResourceRoot = root,
            targetPaths = mapOf(
                source.toString().replace("Resources", "resources") to "Localized/Strings.resw",
            ),
            excludedFromBuildPaths = emptySet(),
        ).stage(
            componentPriFiles = emptyList(),
            componentPriBaseRoot = root,
            appxResourceFiles = emptyList(),
            explicitResourceFiles = listOf(source),
            explicitLayoutFiles = emptyList(),
            explicitContentFiles = emptyList(),
            explicitEmbedFiles = emptyList(),
            defaultResourceFiles = emptyList(),
            defaultLayoutFiles = emptyList(),
            defaultContentFiles = emptyList(),
            includeDefaultProjectResources = false,
        )

        assertTrue(Files.isRegularFile(priRoot.resolve("Localized/Strings.resw")))
    }
}
