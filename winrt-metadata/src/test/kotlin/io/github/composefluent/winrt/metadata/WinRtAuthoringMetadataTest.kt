package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class WinRtAuthoringMetadataTest {
    @Test
    fun authored_runtime_classes_merge_into_metadata_model_before_projection_generation() {
        val model = WinRtAuthoringMetadata.mergeAuthoredRuntimeClasses(
            model = WinRtMetadataModel(
                namespaces = listOf(
                    WinRtNamespace(
                        name = "Sample",
                        types = listOf(
                            WinRtTypeDefinition(
                                namespace = "Sample",
                                name = "IComponent",
                                kind = WinRtTypeKind.Interface,
                            ),
                        ),
                    ),
                ),
            ),
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Component",
                    baseRuntimeClassName = "Sample.BaseComponent",
                    interfaceNames = listOf("Sample.IComponent"),
                    overridableInterfaceNames = listOf("Sample.IComponent"),
                    activatableFactoryInterfaceName = "Sample.IComponentFactory",
                    staticFactoryInterfaceNames = listOf("Sample.IComponentStatics"),
                ),
            ),
        )

        val runtimeClass = model.namespaces
            .single { namespace -> namespace.name == "Sample" }
            .types
            .single { type -> type.name == "Component" }
        assertEquals(WinRtTypeKind.RuntimeClass, runtimeClass.kind)
        assertEquals("Sample.BaseComponent", runtimeClass.baseTypeName)
        assertEquals("Sample.IComponent", runtimeClass.defaultInterfaceName)
        assertTrue(runtimeClass.activation.isActivatable)
        assertEquals("Sample.IComponentFactory", runtimeClass.activation.activatableFactoryInterfaceName)
        assertEquals(listOf("Sample.IComponentStatics"), runtimeClass.activation.staticInterfaceNames)
        assertTrue(runtimeClass.implementedInterfaces.single().isDefault)
        assertTrue(runtimeClass.implementedInterfaces.single().isOverridable)
    }

    @Test
    fun writes_deterministic_authored_metadata_descriptor() {
        val output = Files.createTempFile("kotlin-winrt-authored-metadata-", ".tsv")

        WinRtAuthoredMetadataDescriptorWriter.write(
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Z",
                    interfaceNames = listOf("Sample.IZ"),
                ),
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.A",
                    baseRuntimeClassName = "Sample.Base",
                    interfaceNames = listOf("Sample.IA", "Sample.IOther"),
                    overridableInterfaceNames = listOf("Sample.IA"),
                    isActivatable = false,
                    activatableFactoryInterfaceName = "Sample.IAFactory",
                    staticFactoryInterfaceNames = listOf("Sample.IAStatics"),
                ),
            ),
            outputFile = output,
        )

        assertEquals(
            listOf(
                "Sample.A\tSample.Base\tSample.IA;Sample.IOther\tSample.IA\tfalse\ttrue\tSample.IAFactory\tSample.IAStatics",
                "Sample.Z\t\tSample.IZ\t\ttrue\ttrue\t\t",
            ).joinToString(separator = "\n", postfix = "\n"),
            output.readText(),
        )
    }

    @Test
    fun writes_minimal_loadable_winmd_pe_shell() {
        val output = Files.createTempFile("kotlin-winrt-empty-", ".winmd")

        WinRtPortableExecutableMetadataWriter.writeEmptyWinmd(
            assemblyName = "Sample.Component",
            outputFile = output,
        )

        val model = WinRtMetadataLoader.load(output)
        assertTrue(model.namespaces.isEmpty())
    }

    @Test
    fun writes_authored_runtime_class_typedefs_into_loadable_winmd() {
        val output = Files.createTempFile("kotlin-winrt-authored-", ".winmd")

        WinRtPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = "Sample.Component",
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Component.Widget",
                    baseRuntimeClassName = "Sample.Component.BaseWidget",
                    interfaceNames = listOf("Sample.Component.IWidget"),
                    overridableInterfaceNames = listOf("Sample.Component.IWidget"),
                ),
            ),
            outputFile = output,
        )

        val runtimeClass = WinRtMetadataLoader.load(output)
            .namespaces
            .single { namespace -> namespace.name == "Sample.Component" }
            .types
            .single { type -> type.name == "Widget" }
        assertEquals("Widget", runtimeClass.name)
        assertEquals(WinRtTypeKind.RuntimeClass, runtimeClass.kind)
        assertEquals("Sample.Component.BaseWidget", runtimeClass.baseTypeName)
        assertEquals("Sample.Component.IWidget", runtimeClass.implementedInterfaces.single().interfaceName)
        assertTrue(runtimeClass.implementedInterfaces.single().isDefault)
        assertTrue(runtimeClass.implementedInterfaces.single().isOverridable)
        assertTrue(runtimeClass.activation.isActivatable)
        assertEquals(1L, runtimeClass.availability.version)
        assertTrue(runtimeClass.isSealedType)

        val defaultInterface = WinRtMetadataLoader.load(output)
            .namespaces
            .single { namespace -> namespace.name == "Sample.Component" }
            .types
            .single { type -> type.name == "IWidget" }
        assertEquals(WinRtTypeKind.Interface, defaultInterface.kind)
        assertEquals(1L, defaultInterface.availability.version)
        assertEquals("11111111-2222-3333-4444-555555555555", defaultInterface.iid?.toString())
    }
}
