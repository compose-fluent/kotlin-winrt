package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.projections.generator.referenceDeclarations
import io.github.composefluent.winrt.runtime.Guid
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthoringReferenceDeclarationsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun authored_mapped_dictionary_uses_runtime_definitions_on_the_same_ccw() {
        // Runtime2 IDictionaryAdapter owns IMap ABI; authored TypeDetails supplies closed adapters.
        val candidate = KotlinWinRTAuthoredTypeCandidate("sample", "Bag", "sample.Bag", null,
            listOf("Sample.IBag"), emptyList())
        val model = WinRTMetadataModel(listOf(
            WinRTNamespace("Sample", listOf(WinRTTypeDefinition(
                namespace = "Sample", name = "IBag", kind = WinRTTypeKind.Interface,
                iid = Guid("a33e81ef-c665-503b-8827-d27ef1720a06"),
                implementedInterfaces = listOf(WinRTInterfaceImplementationDefinition(
                    interfaceName = "Windows.Foundation.Collections.IMap<String, Object>")),
            ))),
            WinRTNamespace("Windows.Foundation.Collections", listOf(WinRTTypeDefinition(
                namespace = "Windows.Foundation.Collections", name = "IMap", kind = WinRTTypeKind.Interface,
                iid = Guid("3c2925fe-8519-45c1-aa79-197b6718c1c1"),
                methods = listOf(WinRTMethodDefinition(name = "Clear", returnTypeName = "Void")),
            ))),
        ))
        val output = temporary.newFolder("mapped-dictionary").toPath()
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(candidate), model, output)
        val code = output.resolve("sample/WinRT_Bag_TypeDetails.kt").toFile().readText()
            .replace(Regex("\\s+"), " ")
        assertTrue(code, code.contains("WinRTDictionaryProjection.ccwInterfaceDefinitions(WinRTReferenceValueAdapters.string, WinRTReferenceValueAdapters.object_)"))
        assertFalse(code, code.contains("__target.clear()"))
    }

    @Test fun authored_factory_and_override_roots_retain_their_signature_closure() {
        // ProjectionGenerator.Generate.cs includes exported types before projection emission;
        // AuthoringExportTypesGenerator keeps their activation factories reachable as well.
        val candidate = KotlinWinRTAuthoredTypeCandidate("sample", "Resource", "sample.Resource", "sample.Base",
            listOf("sample.IDefault"), listOf("sample.IOverrides"),
            activatableFactoryInterfaceName = "sample.IFactory", staticFactoryInterfaceNames = listOf("sample.IStatics"))
        val names = listOf("Chosen", "Base", "IDefault", "IOverrides", "IStatics", "Value", "Unrelated")
        val model = WinRTMetadataModel(listOf(WinRTNamespace("sample",
            names.map { WinRTTypeDefinition(namespace = "sample", name = it, kind = WinRTTypeKind.Interface) } +
                WinRTTypeDefinition(namespace = "sample", name = "IFactory", kind = WinRTTypeKind.Interface,
                    methods = listOf(WinRTMethodDefinition(name = "Create", returnTypeName = "sample.Value"))),
        )))
        val selection = WinRTProjectionSurfaceFilter(types = setOf("sample.Chosen"))
            .withAdditionalTypeRoots(authoringCandidateMetadataRootNames(listOf(candidate)))
        val retained = model.filterProjectionSurface(selection).namespaces.flatMap { it.types }.map { it.qualifiedName }.toSet()
        assertEquals(setOf("sample.Chosen", "sample.Base", "sample.IDefault", "sample.IOverrides",
            "sample.IFactory", "sample.IStatics", "sample.Value"), retained)
        // Automatic roots must not accidentally narrow an unfiltered SDK owner.
        assertEquals(model, model.filterProjectionSurface(WinRTProjectionSurfaceFilter()
            .withAdditionalTypeRoots(authoringCandidateMetadataRootNames(listOf(candidate)))))
    }

    @Test fun reference_type_details_keep_declarations_without_registration_implementation() {
        // CsWinRT AuthoringExportTypesGenerator separates exported type identity from
        // activation implementations. Kotlin uses the shared projection declaration pass.
        val candidate = KotlinWinRTAuthoredTypeCandidate("sample", "Resource", "sample.Resource", null,
            listOf("Windows.Foundation.IClosable"), emptyList(), sourceSetName = "mingwX64Main")
        val model = WinRTMetadataModel(listOf(WinRTNamespace("Windows.Foundation", listOf(
            WinRTTypeDefinition(namespace = "Windows.Foundation", name = "IClosable", kind = WinRTTypeKind.Interface,
                iid = Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                methods = listOf(WinRTMethodDefinition(name = "Close", returnTypeName = "Void"))),
        ))))
        val implementation = temporary.newFolder("implementation").toPath()
        val reference = temporary.newFolder("reference").toPath()
        val expected = mutableMapOf<String, String>()
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(candidate), model, implementation,
            declarationTransform = { file ->
                expected[file.name] = file.referenceDeclarations().toString()
                file
            })
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(candidate), model, reference,
            declarationTransform = { it.referenceDeclarations() })
        val outputs = reference.toFile().walkTopDown().filter { it.extension == "kt" }.toList()
        assertEquals(2, outputs.size)
        outputs.forEach { file ->
            assertEquals(expected.getValue(file.nameWithoutExtension), file.readText())
            assertTrue(file.invariantSeparatorsPath.contains("sourceSets/mingwX64Main/"))
        }
        val full = implementation.toFile().walkTopDown().filter { it.extension == "kt" }.joinToString { it.readText() }
        val declarations = outputs.joinToString { it.readText() }
        assertTrue(full.contains("registerStaticCcwDefinition"))
        assertFalse(declarations.contains("registerStaticCcwDefinition"))
        assertTrue(declarations.contains("fun register()"))
        assertTrue(declarations.contains("WinRT reference projection"))
        // Removing a candidate must remove its former source-set-specific declarations.
        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(emptyList(), model, reference,
            declarationTransform = { it.referenceDeclarations() })
        assertFalse(reference.resolve("sourceSets/mingwX64Main/sample/WinRT_Resource_TypeDetails.kt").toFile().exists())
    }
}

