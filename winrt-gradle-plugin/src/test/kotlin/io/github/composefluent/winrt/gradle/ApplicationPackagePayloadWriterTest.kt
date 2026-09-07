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
    fun application_override_is_resolved_after_conflicting_dependency_candidates() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-dependency-conflict-")
        val firstDependency = root.resolve("dependency-a/Assets/Icon.png")
        val secondDependency = root.resolve("dependency-b/Assets/Icon.png")
        val explicit = root.resolve("app/override.png")
        listOf(firstDependency, secondDependency, explicit).forEach { file ->
            Files.createDirectories(file.parent)
            Files.writeString(file, file.fileName.toString())
        }

        val decisions = ApplicationPackagePayloadWriter.resolvePackagePayloads(
            conventionInputs = emptyList(),
            dependencyInputs = listOf(
                AppxResourceInput(firstDependency, Path.of("Assets/Icon.png")),
                AppxResourceInput(secondDependency, Path.of("assets/icon.PNG")),
            ),
            explicitPayloadFiles = listOf(explicit),
            rootPayloadFiles = emptyList(),
            projectRoot = root,
            targetPaths = mapOf(explicit.toString() to "Assets/Icon.png"),
            excludedPaths = emptySet(),
        )

        assertEquals(explicit.toAbsolutePath().normalize(), decisions.single().source)
        assertEquals("explicit packagePayload", decisions.single().origin)
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
    fun resolution_report_detects_content_replacement_at_the_selected_path() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-report-hash-")
        val source = root.resolve("source.png")
        val packageRoot = root.resolve("package")
        val staged = packageRoot.resolve("Assets/Icon.png")
        Files.writeString(source, "selected")
        Files.createDirectories(staged.parent)
        Files.writeString(staged, "selected")
        val report = root.resolve("report.json")
        ApplicationPackagePayloadWriter.writeResolutionReport(
            report,
            listOf(PackagePayloadDecision(source, Path.of("Assets/Icon.png"), "appxResources")),
        )

        Files.writeString(staged, "wrong-content")

        val errors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(errors.any { it.contains("content hash mismatch") })
    }

    @Test
    fun resolution_report_detects_generated_pri_replacement_and_target_change() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-report-pri-")
        val packageRoot = root.resolve("package")
        Files.createDirectories(packageRoot)
        Files.writeString(packageRoot.resolve("resources.pri"), "correct-pri")
        val report = root.resolve("report.json")
        ApplicationPackagePayloadWriter.writeResolutionReport(report, emptyList())
        ApplicationPackagePayloadWriter.recordGeneratedPri(report, packageRoot)

        assertTrue(ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot).isEmpty())

        Files.writeString(packageRoot.resolve("resources.pri"), "wrong-pri")
        val replacementErrors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(replacementErrors.any { it.contains("generated PRI target has a content hash mismatch") })

        val changedTarget = Files.readString(report).replace("resources.pri", "wrong.pri")
        Files.writeString(report, changedTarget)
        val targetErrors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(targetErrors.any { it.contains("generated PRI target must be resources.pri") })
    }

    @Test
    fun resolution_report_validates_structured_pri_paths_and_xbf_locations() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-report-pri-map-")
        val packageRoot = root.resolve("package")
        Files.createDirectories(packageRoot.resolve("Assets"))
        Files.writeString(packageRoot.resolve("resources.pri"), "correct-pri")
        Files.writeString(packageRoot.resolve("Assets/Icon.png"), "icon")
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" />
            </Package>
            """.trimIndent(),
        )
        val dump = root.resolve("resources.pri.xml")
        Files.writeString(
            dump,
            """
            <PriInfo>
              <ResourceMap name="Contoso.App" uniqueName="Contoso.App">
                <ResourceMapSubtree name="Files">
                  <NamedResource name="Icon.png" uri="ms-resource://Contoso.App/Files/Assets/Icon.png">
                    <Candidate type="Path"><Value>Assets\\Icon.png</Value></Candidate>
                  </NamedResource>
                  <NamedResource name="Page.xbf" uri="ms-resource://Contoso.App/Files/WinUI3Package/Page.xbf">
                    <Candidate type="EmbeddedData"><Base64Value>eA==</Base64Value></Candidate>
                  </NamedResource>
                </ResourceMapSubtree>
              </ResourceMap>
            </PriInfo>
            """.trimIndent(),
        )
        val report = root.resolve("report.json")
        ApplicationPackagePayloadWriter.writeResolutionReport(report, emptyList())
        ApplicationPackagePayloadWriter.recordGeneratedPri(report, packageRoot, dump)

        assertTrue(ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot).isEmpty())

        val invalid = Files.readString(report).replace("Icon.png", "Missing.png")
        Files.writeString(report, invalid)
        val errors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(errors.any { it.contains("Path Value is missing from the package") })
    }

    @Test
    fun resolution_report_requires_package_root_schema_and_content_hashes() {
        val root = Files.createTempDirectory("kotlin-winrt-payload-report-schema-")
        val source = root.resolve("source.png")
        val packageRoot = root.resolve("package")
        val staged = packageRoot.resolve("Assets/Icon.png")
        Files.writeString(source, "selected")
        Files.createDirectories(staged.parent)
        Files.writeString(staged, "selected")
        val report = root.resolve("report.json")
        ApplicationPackagePayloadWriter.writeResolutionReport(
            report,
            listOf(PackagePayloadDecision(source, Path.of("Assets/Icon.png"), "appxResources")),
        )

        val invalid = Files.readString(report)
            .replace("\"packageRootRelative\":true", "\"packageRootRelative\":false")
            .replace(Regex("\"sha256\":\"[0-9a-fA-F]{64}\""), "\"sha256\":\"\"")
        Files.writeString(report, invalid)

        val errors = ApplicationPackagePayloadWriter.validateResolutionReport(report, packageRoot)
        assertTrue(errors.any { it.contains("packageRootRelative must be true") })
        assertTrue(errors.any { it.contains("invalid sha256") })
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
