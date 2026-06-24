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
                    staticFactoryInterfaceNames = listOf("Sample.Component.IWidgetStatics"),
                ),
            ),
            outputFile = output,
        )

        val model = WinRtMetadataLoader.load(output)
        val runtimeClass = model
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
        assertEquals(listOf("Sample.Component.IWidgetStatics"), runtimeClass.activation.staticInterfaceNames)
        assertEquals(1L, runtimeClass.availability.version)
        assertTrue(runtimeClass.isSealedType)
        assertEquals(
            listOf("Sample.Component.IWidgetStatics"),
            model.semanticHelpers().factorySurfaceDescriptor(runtimeClass).staticMemberTargets,
        )
        assertTrue(
            model.namespaces
                .single { namespace -> namespace.name == "Sample.Component" }
                .types
                .none { type -> type.name == "IWidget" },
        )
    }

    @Test
    fun authored_winmd_references_external_override_interfaces_without_redefining_them() {
        val output = Files.createTempFile("kotlin-winrt-authored-external-interfaces-", ".winmd")

        WinRtPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = "Sample.Component",
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Component.RootContentControl",
                    baseRuntimeClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
                    interfaceNames = listOf(
                        "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                        "Microsoft.UI.Xaml.IUIElementOverrides",
                    ),
                    overridableInterfaceNames = listOf(
                        "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                        "Microsoft.UI.Xaml.IUIElementOverrides",
                    ),
                ),
            ),
            outputFile = output,
        )

        val model = WinRtMetadataLoader.load(output)
        val runtimeClass = model
            .namespaces
            .single { namespace -> namespace.name == "Sample.Component" }
            .types
            .single { type -> type.name == "RootContentControl" }

        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                "Microsoft.UI.Xaml.IUIElementOverrides",
            ),
            runtimeClass.implementedInterfaces.map(WinRtInterfaceImplementationDefinition::interfaceName),
        )
        assertTrue(runtimeClass.implementedInterfaces.all(WinRtInterfaceImplementationDefinition::isOverridable))
        assertTrue(model.namespaces.none { namespace -> namespace.name == "Microsoft.UI.Xaml" })
    }

    @Test
    fun authored_dependency_winmd_does_not_override_real_external_interface_metadata() {
        val dependencyOutput = Files.createTempFile("kotlin-winrt-authored-dependency-old-shape-", ".winmd")
        val baseOutput = Files.createTempFile("kotlin-winrt-base-winui-", ".winmd")

        WinRtPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Sample.Dependency",
            interfaces = listOf(
                WinRtPortableExecutableInterfaceDescriptor(
                    interfaceName = "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    iid = "11111111-2222-3333-4444-555555555555",
                ),
            ),
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Dependency.HostPanel",
                    baseRuntimeClassName = "Microsoft.UI.Xaml.Controls.Grid",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                ),
            ),
            outputFile = dependencyOutput,
        )
        WinRtPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Microsoft.UI.Xaml",
            interfaces = listOf(
                WinRtPortableExecutableInterfaceDescriptor(
                    interfaceName = "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    iid = "ffc6fd98-f38c-5904-9ce4-97a3427cf4ba",
                ),
            ),
            runtimeClasses = listOf(
                WinRtAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Microsoft.UI.Xaml.FrameworkElement",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                ),
            ),
            outputFile = baseOutput,
        )

        val model = WinRtMetadataLoader.loadSources(
            listOf(
                WinRtMetadataSource.path(baseOutput),
                WinRtMetadataSource.path(dependencyOutput),
            ),
        )

        val overrideInterface = model.namespaces
            .single { namespace -> namespace.name == "Microsoft.UI.Xaml" }
            .types
            .single { type -> type.name == "IFrameworkElementOverrides" }
        assertEquals("ffc6fd98-f38c-5904-9ce4-97a3427cf4ba", overrideInterface.iid.toString().lowercase())
    }
}
