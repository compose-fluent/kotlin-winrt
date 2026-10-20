package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION
import io.github.composefluent.winrt.runtime.WINRT_PROJECTION_RUNTIME_ABI_VERSION
import java.io.File
import kotlinx.serialization.json.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectionContractCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun linked_component_payload_does_not_change_the_projection_contract_owner() {
        // CsWinRT's compiled-reference handoff must keep matching the published
        // contract after an implementation binary is added for component hosting.
        val reference = currentIdentity("""{"projectionShapeVersion":1,"projectedTypes":["Example.Button"],
            "authoredTargetArtifactRecords":[]}""")
        val published = identity(reference.readText().replace("\"authoredTargetArtifactRecords\":[]",
            "\"authoredTargetArtifactRecords\":[{\"fileName\":\"implementation.jar\",\"contentBase64\":\"Ym9keQ==\"}]"))
        assertEquals(readProjectionSurfaceIdentity(reference).owner, readProjectionSurfaceIdentity(published).owner)
        val library = identity(reference.readText().replace("test:producer", "test:library")
            .replace("Example.Button", "Library.Widget")
            .replaceFirst("{", """{"dependencyOwners":${projectionDependencyOwners(listOf(reference))},"""))
        validateDependencyProjectionIdentityOwnership(listOf(library, published))
        val changedContract = identity(published.readText().replace("Example.Button", "Example.Other"))
        assertThrows(IllegalStateException::class.java) {
            validateDependencyProjectionIdentityOwnership(listOf(library, changedContract))
        }
    }

    @Test fun projection_without_authored_types_does_not_embed_its_published_binary() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        val binary = temporary.newFile("sdk.jar").apply { writeText("already published SDK implementation") }
        task.authoredTargetArtifactFiles.from(binary)
        task.generate()
        val document = Json.parseToJsonElement(task.outputFile.get().asFile.readText()).jsonObject
        assertTrue(document.getValue("authoredTargetArtifactRecords").jsonArray.isEmpty())
    }

    // Reference: CsWinRT InteropGenerator rejects incompatible runtime assembly references;
    // reference-projection type ownership is not a license to regenerate incompatible types.
    @Test fun explicit_incompatible_versions_fail_before_type_ownership_fallback() {
        for (field in listOf("schemaVersion", "projectionShapeVersion", "projectionRuntimeAbiVersion",
            "projectionCompilerProtocolVersion")) {
            val file = identity("""{"$field":99,"projectedTypes":["Example.Button"]}""")
            val failure = assertThrows(IllegalStateException::class.java) { readProjectionSurfaceIdentity(file) }
            assertTrue(failure.message.orEmpty().contains(field))
            assertTrue(failure.message.orEmpty().contains(file.absolutePath))
        }
    }

    @Test fun invalid_and_partial_versions_are_not_accepted_as_legacy() {
        for (value in listOf("null", "-1", "0", "1.5", "\"1\"", "{}", "true")) {
            val file = identity("""{"projectionShapeVersion":$value}""")
            assertThrows(IllegalStateException::class.java) { readProjectionSurfaceIdentity(file) }
        }
        for (field in listOf("projectionRuntimeAbiVersion", "projectionCompilerProtocolVersion")) {
            val file = identity("""{"projectionShapeVersion":1,"$field":1}""")
            assertThrows(IllegalStateException::class.java) { readProjectionSurfaceIdentity(file) }
        }
    }

    @Test fun unversioned_and_empty_published_identities_require_a_rebuild() {
        for (content in listOf("", "{}", """{"projectionShapeVersion":1,"projectedTypes":["Example.Button"]}""",
            """{"includeTypes":["Example.Button"]}""")) {
            val failure = assertThrows(IllegalStateException::class.java) { readProjectionSurfaceIdentity(identity(content)) }
            assertTrue(failure.message.orEmpty().contains("rebuild"))
        }
        assertEquals(emptyList<String>(), readProjectionSurfaceIdentity(File(temporary.root, "not-produced.json")).includeTypes)
    }

    @Test fun ownership_uses_top_level_json_fields_and_decodes_escaped_names() {
        val file = currentIdentity("""{"projectionShapeVersion":1,
            "extension":{"projectedTypes":["Nested.Wrong"]},
            "projectedTypes":["Example.\u0042utton"]}""")
        assertEquals(listOf("Example.Button"), readProjectionSurfaceIdentity(file).currentShapeProjectedTypes())
        val nestedOnly = currentIdentity("""{"projectionShapeVersion":1,"extension":{"projectedTypes":["Nested.Wrong"]}}""")
        assertEquals(emptyList<String>(), readProjectionSurfaceIdentity(nestedOnly).currentShapeProjectedTypes())
    }

    @Test fun malformed_ownership_lists_never_silently_drop_dependencies() {
        for (name in listOf("includeNamespaces", "includeTypes", "projectedTypes", "sourceAdditions",
            "excludeNamespaces", "excludeTypes", "internalProjectedTypes")) {
            for (value in listOf("null", "{}", "\"Example.Button\"", "[1]", "[null]", "[\"\"]")) {
                val file = currentIdentity("""{"projectionShapeVersion":1,"$name":$value}""")
                val failure = assertThrows(IllegalStateException::class.java) { readProjectionSurfaceIdentity(file) }
                assertTrue(failure.message.orEmpty().contains(name))
                assertTrue(failure.message.orEmpty().contains(file.absolutePath))
            }
        }
    }

    @Test fun support_merge_rejects_duplicate_owners_without_local_projection_generation() {
        // CsWinRT ImplGenerator.EmitTypeForwards assigns each type to a shared projection.
        // Until Kotlin publication supplies that shared owner, conflicting definitions must fail.
        for (field in listOf("projectedTypes", "sourceAdditions")) {
            val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build()
            val task = project.tasks.create("mergeSupport", MergeWinRTCompilerSupportTask::class.java)
            val first = currentIdentity("""{"projectionShapeVersion":1,"$field":["Example.Button"]}""")
            val second = identity(first.readText().replace("test:producer", "test:other"))
            task.dependencyIdentityFiles.from(first, second)
            task.outputDirectory.set(project.layout.buildDirectory.dir("merged"))
            val failure = assertThrows(IllegalStateException::class.java) { task.merge() }
            assertTrue(failure.message.orEmpty().contains("Example.Button"))
            assertTrue(failure.message.orEmpty().contains(first.absolutePath))
            assertTrue(failure.message.orEmpty().contains(second.absolutePath))
        }
    }

    @Test fun published_identity_contains_separate_supported_contract_versions() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.generate()
        val file = task.outputFile.get().asFile
        val content = file.readText()
        assertTrue(content.contains("\"projectionRuntimeAbiVersion\": $WINRT_PROJECTION_RUNTIME_ABI_VERSION"))
        assertTrue(content.contains("\"projectionCompilerProtocolVersion\": $WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION"))
        readProjectionSurfaceIdentity(file)
    }

    @Test fun canonical_owner_reuses_identical_identity_aliases_but_rejects_version_and_shape_conflicts() {
        val first = currentIdentity("""{"projectionShapeVersion":1,"projectedTypes":["Example.Button"]}""")
        val alias = identity(first.readText().replace("\n", " "))
        validateDependencyProjectionIdentityOwnership(listOf(first, alias))
        for (changed in listOf(first.readText().replace("1.0", "2.0"),
            first.readText().replace("Example.Button", "Example.Other"))) {
            val conflict = identity(changed)
            val failure = assertThrows(IllegalStateException::class.java) {
                validateDependencyProjectionIdentityOwnership(listOf(first, conflict))
            }
            assertTrue(failure.message.orEmpty().contains("test:producer"))
            assertTrue(failure.message.orEmpty().contains("conflicting versions or identity contents"))
        }
    }

    @Test fun source_addition_and_projection_share_one_type_ownership_namespace() {
        val first = currentIdentity("""{"projectionShapeVersion":1,"projectedTypes":["Example.Button"]}""")
        val second = identity(first.readText().replace("test:producer", "test:overlay").replace("projectedTypes", "sourceAdditions"))
        assertThrows(IllegalStateException::class.java) { validateDependencyProjectionIdentityOwnership(listOf(first, second)) }
    }

    @Test fun maven_publication_keeps_projection_and_packaging_identities_separate() {
        // CsWinRT forwards reference contracts separately from implementation payloads.
        // XAML compiler support belongs to packaging, not the contract recorded by libraries.
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java).mingwX64()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        val publication = project.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java)
            .publications.getByName("kotlinMultiplatform") as org.gradle.api.publish.maven.MavenPublication
        val identities = publication.artifacts.filter { it.extension == "json" }
        assertEquals(1, identities.count { it.classifier == "winrt-projection-identity" })
        assertEquals(1, identities.count { it.classifier.isNullOrEmpty() })
    }

    @Test fun identity_variants_forward_external_sdk_owners_without_exporting_unverified_projects() {
        // CsWinRT discovery receives the entire transitive reference graph, including
        // SDKs referenced only by an intermediate project library.
        val root = ProjectBuilder.builder().withName("root").build()
        val plain = ProjectBuilder.builder().withName("plain").withParent(root).build()
        plain.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val library = ProjectBuilder.builder().withName("library").withParent(root).build()
        library.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        library.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        library.dependencies.add("commonMainApi", "sample:sdk:1")
        library.dependencies.add("commonMainImplementation", library.dependencies.project(mapOf("path" to ":plain")))
        library.dependencies.add("commonMainImplementation", "io.github.compose-fluent:winrt-runtime:1")
        for (name in listOf(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION, "kotlinWinRTProjectionIdentityElements")) {
            val dependencies = library.configurations.getByName(name).dependencies
            assertEquals(listOf("sample:sdk:1"), dependencies.filterIsInstance<org.gradle.api.artifacts.ExternalModuleDependency>()
                .map { "${it.group}:${it.name}:${it.version}" })
            assertTrue(dependencies.filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().isEmpty())
        }
    }

    @Test fun resolved_sdk_must_match_the_librarys_recorded_projection_dependency() {
        val sdk = currentIdentity("""{"projectionShapeVersion":1,"projectedTypes":["Example.Button"]}""")
        val library = currentIdentity("""{"projectionShapeVersion":1,"projectedTypes":["Library.Widget"],
            "dependencyOwners":${projectionDependencyOwners(listOf(sdk))}}""")
        library.writeText(library.readText().replaceFirst("test:producer", "test:library"))
        validateDependencyProjectionIdentityOwnership(listOf(library, sdk))
        for (changed in listOf(sdk.readText().replace("1.0", "2.0"),
            sdk.readText().replace("Example.Button", "Example.NewButton"))) {
            val selected = identity(changed)
            val failure = assertThrows(IllegalStateException::class.java) {
                validateDependencyProjectionIdentityOwnership(listOf(library, selected))
            }
            assertTrue(failure.message.orEmpty().contains("requires owner 'test:producer'"))
            assertTrue(failure.message.orEmpty().contains("rebuild"))
        }
        assertThrows(IllegalStateException::class.java) {
            validateDependencyProjectionIdentityOwnership(listOf(library))
        }
        // An output not produced yet is only deferred while the task graph is being built.
        validateDependencyProjectionIdentityOwnership(listOf(library, File(temporary.root, "pending-sdk.json")))
    }

    @Test fun compiler_support_merge_rejects_incompatible_dependencies_without_local_sources() {
        val project = ProjectBuilder.builder().withProjectDir(temporary.newFolder()).build()
        val task = project.tasks.create("mergeSupport", MergeWinRTCompilerSupportTask::class.java)
        task.dependencyIdentityFiles.from(identity("""{"projectionCompilerProtocolVersion":99}"""))
        task.outputDirectory.set(project.layout.buildDirectory.dir("merged"))
        val failure = assertThrows(IllegalStateException::class.java) { task.merge() }
        assertTrue(failure.message.orEmpty().contains("projectionCompilerProtocolVersion"))
    }

    private fun identity(content: String): File = temporary.newFile().apply { writeText(content) }
    private fun currentIdentity(content: String): File = identity(content.replaceFirst("{", """{
        "schemaVersion":1,
        "projectionOwner":"test:producer", "projectionOwnerVersion":"1.0",
        "projectionRuntimeAbiVersion":$WINRT_PROJECTION_RUNTIME_ABI_VERSION,
        "projectionCompilerProtocolVersion":$WINRT_PROJECTION_COMPILER_PROTOCOL_VERSION,
    """))
}
