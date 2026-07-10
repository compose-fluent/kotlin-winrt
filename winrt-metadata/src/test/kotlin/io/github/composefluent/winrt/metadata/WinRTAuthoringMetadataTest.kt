package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class WinRTAuthoringMetadataTest {
    @Test
    fun authored_runtime_classes_merge_into_metadata_model_before_projection_generation() {
        val model = WinRTAuthoringMetadata.mergeAuthoredRuntimeClasses(
            model = WinRTMetadataModel(
                namespaces = listOf(
                    WinRTNamespace(
                        name = "Sample",
                        types = listOf(
                            WinRTTypeDefinition(
                                namespace = "Sample",
                                name = "IComponent",
                                kind = WinRTTypeKind.Interface,
                            ),
                        ),
                    ),
                ),
            ),
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
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
        assertEquals(WinRTTypeKind.RuntimeClass, runtimeClass.kind)
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

        WinRTAuthoredMetadataDescriptorWriter.write(
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Z",
                    interfaceNames = listOf("Sample.IZ"),
                ),
                WinRTAuthoredRuntimeClassDescriptor(
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

        WinRTPortableExecutableMetadataWriter.writeEmptyWinmd(
            assemblyName = "Sample.Component",
            outputFile = output,
        )

        val model = WinRTMetadataLoader.load(output)
        assertTrue(model.namespaces.isEmpty())
    }

    @Test
    fun writes_authored_runtime_class_typedefs_into_loadable_winmd() {
        val output = Files.createTempFile("kotlin-winrt-authored-", ".winmd")

        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = "Sample.Component",
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Component.Widget",
                    baseRuntimeClassName = "Sample.Component.BaseWidget",
                    interfaceNames = listOf("Sample.Component.IWidget"),
                    overridableInterfaceNames = listOf("Sample.Component.IWidget"),
                    staticFactoryInterfaceNames = listOf("Sample.Component.IWidgetStatics"),
                ),
            ),
            outputFile = output,
        )

        val model = WinRTMetadataLoader.load(output)
        val runtimeClass = model
            .namespaces
            .single { namespace -> namespace.name == "Sample.Component" }
            .types
            .single { type -> type.name == "Widget" }
        assertEquals("Widget", runtimeClass.name)
        assertEquals(WinRTTypeKind.RuntimeClass, runtimeClass.kind)
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

        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = "Sample.Component",
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
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

        val model = WinRTMetadataLoader.load(output)
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
            runtimeClass.implementedInterfaces.map(WinRTInterfaceImplementationDefinition::interfaceName),
        )
        assertTrue(runtimeClass.implementedInterfaces.all(WinRTInterfaceImplementationDefinition::isOverridable))
        assertTrue(model.namespaces.none { namespace -> namespace.name == "Microsoft.UI.Xaml" })
    }

    @Test
    fun authored_dependency_winmd_with_conflicting_external_interface_identity_is_rejected() {
        val dependencyOutput = Files.createTempFile("kotlin-winrt-authored-dependency-old-shape-", ".winmd")
        val baseOutput = Files.createTempFile("kotlin-winrt-base-winui-", ".winmd")

        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Sample.Dependency",
            interfaces = listOf(
                WinRTPortableExecutableInterfaceDescriptor(
                    interfaceName = "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    iid = "11111111-2222-3333-4444-555555555555",
                ),
            ),
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Sample.Dependency.HostPanel",
                    baseRuntimeClassName = "Microsoft.UI.Xaml.Controls.Grid",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                ),
            ),
            outputFile = dependencyOutput,
        )
        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Microsoft.UI.Xaml",
            interfaces = listOf(
                WinRTPortableExecutableInterfaceDescriptor(
                    interfaceName = "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    iid = "ffc6fd98-f38c-5904-9ce4-97a3427cf4ba",
                ),
            ),
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Microsoft.UI.Xaml.FrameworkElement",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IFrameworkElementOverrides"),
                ),
            ),
            outputFile = baseOutput,
        )

        val failure = runCatching {
            WinRTMetadataLoader.loadSources(
                listOf(
                    WinRTMetadataSource.path(baseOutput),
                    WinRTMetadataSource.path(dependencyOutput),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("Microsoft.UI.Xaml.IFrameworkElementOverrides"))
        assertTrue(failure?.message.orEmpty().contains("FFC6FD98-F38C-5904-9CE4-97A3427CF4BA"))
        assertTrue(failure?.message.orEmpty().contains("11111111-2222-3333-4444-555555555555"))
    }
}
