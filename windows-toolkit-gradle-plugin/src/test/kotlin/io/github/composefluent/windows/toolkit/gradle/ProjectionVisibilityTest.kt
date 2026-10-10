package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRTCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.Guid
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProjectionVisibilityTest {
    @Test
    fun identity_preserves_internal_ownership_and_records_visibility() {
        // CsWinRT code_writers.h write_interface keeps exclusive interfaces internal.
        val producer = KotlinProjectionGenerator(emitSupportFiles = true).generate(projectionVisibilityModel())
        val shapes = producer.single { it.relativePath.endsWith("type-shape-descriptors.tsv") }
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val descriptor = project.layout.buildDirectory.file("shapes.tsv").get().asFile
        descriptor.parentFile.mkdirs()
        descriptor.writeText(shapes.contents)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.typeShapeDescriptorFiles.from(descriptor)
        task.generate()

        val identity = readProjectionSurfaceIdentity(task.outputFile.get().asFile)
        assertTrue(identity.projectedTypes.orEmpty().contains(HIDDEN_QUERY_INTERFACE))
        assertEquals(listOf(HIDDEN_QUERY_INTERFACE), identity.internalProjectedTypes)
        assertEquals(setOf(HIDDEN_QUERY_INTERFACE), dependencyInternalProjectedTypeNames(
            projectionVisibilityModel(), listOf(task.outputFile.get().asFile),
        ))
    }

    @Test
    fun downstream_shell_uses_internal_dependency_interface_through_abi_only() {
        val files = KotlinProjectionGenerator(
            projectionContext = WinRTMetadataProjectionContext(
                sources = emptyList(), inaccessibleDependencyTypes = setOf(HIDDEN_QUERY_INTERFACE),
            ),
            suppressedProjectionTypeNames = setOf(HIDDEN_QUERY_INTERFACE),
        ).generate(projectionVisibilityModel())
        assertFalse(files.any { it.relativePath.endsWith("IQueryOptionsAdditionalSearchSources.kt") })
        val source = files.single { it.relativePath.substringAfterLast('/') == "QueryOptions.kt" }.contents
        assertFalse(source, source.contains("IQueryOptionsAdditionalSearchSources.Metadata"))
        assertFalse(source, source.contains("import sample.search.IQueryOptionsAdditionalSearchSources"))
        assertTrue(source, source.contains("public val additionalSearchSourcesCount: Int"))
        assertTrue(source, source.contains(HIDDEN_QUERY_IID))
    }

    @Test
    fun legacy_identity_uses_metadata_visibility_and_explicit_public_visibility_wins() {
        val identity = Files.createTempFile("winrt-visibility-", ".json").toFile()
        identity.writeText("""{"projectionShapeVersion":1,"projectedTypes":["$HIDDEN_QUERY_INTERFACE"]}""")
        assertEquals(setOf(HIDDEN_QUERY_INTERFACE), dependencyInternalProjectedTypeNames(projectionVisibilityModel(), listOf(identity)))
        identity.writeText("""{"projectionShapeVersion":1,"projectedTypes":["$HIDDEN_QUERY_INTERFACE"],"internalProjectedTypes":[]}""")
        assertEquals(emptySet<String>(), dependencyInternalProjectedTypeNames(projectionVisibilityModel(), listOf(identity)))
    }
}

internal const val HIDDEN_QUERY_INTERFACE = "Sample.Search.IQueryOptionsAdditionalSearchSources"
internal const val HIDDEN_QUERY_IID = "11111111-2222-3333-4444-555555555555"

internal fun projectionVisibilityModel(): WinRTMetadataModel {
    val namespace = "Sample.Search"
    val hidden = WinRTTypeDefinition(
        namespace, "IQueryOptionsAdditionalSearchSources", WinRTTypeKind.Interface,
        iid = Guid(HIDDEN_QUERY_IID), isExclusiveTo = true,
        customAttributes = listOf(WinRTCustomAttributeDefinition(
            "Windows.Foundation.Metadata.ExclusiveToAttribute",
            fixedArguments = listOf(WinRTCustomAttributeValue.TypeValue("$namespace.QueryOptions")),
        )),
        properties = listOf(WinRTPropertyDefinition(
            "AdditionalSearchSourcesCount", "Int32",
            getterMethodName = "get_AdditionalSearchSourcesCount", getterMethodRowId = 1,
        )),
    )
    val visible = WinRTTypeDefinition(namespace, "IQueryOptions", WinRTTypeKind.Interface,
        iid = Guid("22222222-2222-3333-4444-555555555555"))
    val runtimeClass = WinRTTypeDefinition(namespace, "QueryOptions", WinRTTypeKind.RuntimeClass,
        defaultInterfaceName = "$namespace.IQueryOptions", isSealedType = true,
        implementedInterfaces = listOf(
            WinRTInterfaceImplementationDefinition("$namespace.IQueryOptions", isDefault = true),
            WinRTInterfaceImplementationDefinition(HIDDEN_QUERY_INTERFACE),
        ))
    return WinRTMetadataModel(listOf(WinRTNamespace(namespace, listOf(hidden, visible, runtimeClass))))
}
