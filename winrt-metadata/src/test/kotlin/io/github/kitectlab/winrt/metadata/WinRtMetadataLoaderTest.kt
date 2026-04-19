package io.github.kitectlab.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import io.github.kitectlab.winrt.runtime.Guid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WinRtMetadataLoaderTest {
    @Test
    fun loads_namespace_and_type_kinds_from_real_cli_metadata() {
        val assembly = buildManagedMetadataSample()

        val model = WinRtMetadataLoader.load(assembly).normalized()

        assertEquals(listOf("Sample.Foundation", "Windows.Foundation.Metadata"), model.namespaces.map { it.name })
        val sampleNamespace = model.namespaces.first { it.name == "Sample.Foundation" }
        assertEquals(
            listOf(
                "Color",
                "IBox",
                "IInternalContract",
                "IWidget",
                "IWidgetBase",
                "IWidgetFactory",
                "IWidgetOverrides",
                "IWidgetStatics",
                "Point",
                "Widget",
                "WidgetAttribute",
                "WidgetContract",
                "WidgetHandler",
                "WidgetStaticsClass",
            ),
            sampleNamespace.types.map { it.name },
        )
        assertEquals(
            listOf(
                WinRtTypeKind.Enum,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Struct,
                WinRtTypeKind.RuntimeClass,
                WinRtTypeKind.RuntimeClass,
                WinRtTypeKind.Struct,
                WinRtTypeKind.Delegate,
                WinRtTypeKind.RuntimeClass,
            ),
            sampleNamespace.types.map { it.kind },
        )

        val widget = sampleNamespace.types.first { it.name == "Widget" }
        assertEquals(Guid("33333333-3333-3333-3333-333333333333"), widget.iid)
        assertEquals("System.Object", widget.baseTypeName)
        assertFalse(widget.isProjectionInternal)
        assertFalse(widget.isExclusiveTo)
        assertFalse(widget.isApiContract)
        assertFalse(widget.isAttributeType)
        assertFalse(widget.isSealedType)
        assertNull(widget.defaultInterfaceName)
        assertEquals(listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetBase"), widget.implementedInterfaces.map { it.interfaceName })
        assertEquals(1, widget.genericParameterCount)
        assertEquals(true, widget.activation.isActivatable)
        assertEquals("Sample.Foundation.IWidgetFactory", widget.activation.activatableFactoryInterfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), widget.activation.staticInterfaceNames)
        assertEquals("Sample.Foundation.IWidgetFactory", widget.activation.composableFactoryInterfaceName)

        val iWidget = sampleNamespace.types.first { it.name == "IWidget" }
        assertEquals(Guid("22222222-2222-2222-2222-222222222222"), iWidget.iid)
        assertEquals(listOf("Sample.Foundation.IWidgetBase"), iWidget.implementedInterfaces.map { it.interfaceName })
        assertEquals(listOf("Update"), iWidget.methods.map { it.name })
        assertEquals("Unit", iWidget.methods.single().returnTypeName)
        assertEquals(listOf(4), iWidget.methods.map { it.methodRowId })
        assertEquals(listOf("input", "written", "state"), iWidget.methods.single().parameters.map { it.name })
        assertEquals(listOf("String", "Int", "Int"), iWidget.methods.single().parameters.map { it.typeName })
        assertEquals(
            listOf(WinRtParameterDirection.In, WinRtParameterDirection.Out, WinRtParameterDirection.Ref),
            iWidget.methods.single().parameters.map { it.direction },
        )
        assertEquals(listOf("Name", "Value"), iWidget.properties.map { it.name })
        assertEquals(listOf("String", "Int"), iWidget.properties.map { it.typeName })
        assertEquals(listOf(true, false), iWidget.properties.map { it.isReadOnly })
        assertEquals(listOf("get_Name", "get_Value"), iWidget.properties.map { it.getterMethodName })
        assertEquals(listOf(null, "set_Value"), iWidget.properties.map { it.setterMethodName })
        assertEquals(listOf(5, 7), iWidget.properties.map { it.getterMethodRowId })
        assertEquals(listOf(null, 8), iWidget.properties.map { it.setterMethodRowId })
        assertEquals(listOf("Changed"), iWidget.events.map { it.name })
        assertEquals(listOf("Sample.Foundation.WidgetHandler"), iWidget.events.map { it.delegateTypeName })
        assertEquals(listOf("add_Changed"), iWidget.events.map { it.addMethodName })
        assertEquals(listOf("remove_Changed"), iWidget.events.map { it.removeMethodName })
        assertEquals(listOf(9), iWidget.events.map { it.addMethodRowId })
        assertEquals(listOf(10), iWidget.events.map { it.removeMethodRowId })

        val iBox = sampleNamespace.types.first { it.name == "IBox" }
        assertEquals(1, iBox.genericParameterCount)

        val iWidgetOverrides = sampleNamespace.types.first { it.name == "IWidgetOverrides" }
        assertTrue(iWidgetOverrides.isExclusiveTo)
        assertFalse(iWidgetOverrides.isProjectionInternal)

        val internalContract = sampleNamespace.types.first { it.name == "IInternalContract" }
        assertTrue(internalContract.isProjectionInternal)

        val widgetAttribute = sampleNamespace.types.first { it.name == "WidgetAttribute" }
        assertEquals("System.Attribute", widgetAttribute.baseTypeName)
        assertTrue(widgetAttribute.isAttributeType)
        assertTrue(widgetAttribute.isSealedType)

        val widgetContract = sampleNamespace.types.first { it.name == "WidgetContract" }
        assertTrue(widgetContract.isApiContract)

        val widgetStaticsClass = sampleNamespace.types.first { it.name == "WidgetStaticsClass" }
        assertTrue(widgetStaticsClass.isStaticType)

        val color = sampleNamespace.types.first { it.name == "Color" }
        assertTrue(color.isSealedType)

        assertEquals(listOf("Update"), widget.methods.map { it.name })
        assertEquals(listOf("Name", "Value"), widget.properties.map { it.name })
        assertEquals(listOf("Changed"), widget.events.map { it.name })
        assertFalse(widget.methods.any { it.name.startsWith("get_") || it.name.startsWith("set_") || it.name.startsWith("add_") || it.name.startsWith("remove_") })
    }

    @Test
    fun discovers_metadata_files_from_directory_inputs() {
        val assembly = buildManagedMetadataSample()

        val discovered = WinRtMetadataLoader.discoverMetadataFiles(listOf(assembly.parent))

        assertTrue(discovered.contains(assembly))
    }

    @Test
    fun canonicalizes_duplicate_path_forms_and_keeps_loader_output_stable() {
        val assembly = buildManagedMetadataSample()
        val duplicatePathForm = assembly.parent.resolve(".").resolve(assembly.fileName.toString())

        val discovered = WinRtMetadataLoader.discoverMetadataFiles(
            listOf(assembly, duplicatePathForm, assembly.parent),
        )
        val firstLoad = WinRtMetadataLoader.load(assembly, duplicatePathForm)
        val secondLoad = WinRtMetadataLoader.load(duplicatePathForm, assembly)
        val sampleNamespace = firstLoad.namespaces.first { it.name == "Sample.Foundation" }

        assertEquals(1, discovered.count { it.fileName == assembly.fileName })
        assertEquals(firstLoad, secondLoad)
        assertEquals(
            listOf(
                "Color",
                "IBox",
                "IInternalContract",
                "IWidget",
                "IWidgetBase",
                "IWidgetFactory",
                "IWidgetOverrides",
                "IWidgetStatics",
                "Point",
                "Widget",
                "WidgetAttribute",
                "WidgetContract",
                "WidgetHandler",
                "WidgetStaticsClass",
            ),
            sampleNamespace.types.map { it.name },
        )
    }

    private fun buildManagedMetadataSample(): Path {
        val dotnet = findDotnet()
        assumeTrue("dotnet CLI is required for Metadata 2.1 tests", dotnet != null)
        val tempDir = Files.createTempDirectory("kotlin-winrt-metadata-test")
        val projectDir = tempDir.resolve("SampleMetadata")
        projectDir.createDirectories()

        projectDir.resolve("SampleMetadata.csproj").writeText(
            """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <ImplicitUsings>disable</ImplicitUsings>
                <Nullable>disable</Nullable>
                <GenerateAssemblyInfo>false</GenerateAssemblyInfo>
                <GenerateTargetFrameworkAttribute>false</GenerateTargetFrameworkAttribute>
              </PropertyGroup>
            </Project>
            """.trimIndent(),
        )
        projectDir.resolve("MetadataTypes.cs").writeText(
            """
            namespace Windows.Foundation.Metadata
            {
                using System;

                [AttributeUsage(AttributeTargets.Class | AttributeTargets.Interface, AllowMultiple = false)]
                public sealed class GuidAttribute : Attribute
                {
                    public GuidAttribute(string value) {}
                }

                [AttributeUsage(AttributeTargets.Class, AllowMultiple = true)]
                public sealed class ActivatableAttribute : Attribute
                {
                    public ActivatableAttribute(string factoryInterfaceName) {}
                }

                [AttributeUsage(AttributeTargets.Class, AllowMultiple = true)]
                public sealed class StaticAttribute : Attribute
                {
                    public StaticAttribute(string interfaceName) {}
                }

                [AttributeUsage(AttributeTargets.Class, AllowMultiple = true)]
                public sealed class ComposableAttribute : Attribute
                {
                    public ComposableAttribute(string factoryInterfaceName) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class DefaultAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class OverridableAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class ProtectedAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.Interface, AllowMultiple = false)]
                public sealed class ExclusiveToAttribute : Attribute
                {
                    public ExclusiveToAttribute(Type type) {}
                }

                [AttributeUsage(AttributeTargets.Interface, AllowMultiple = false)]
                public sealed class ProjectionInternalAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.Struct, AllowMultiple = false)]
                public sealed class ApiContractAttribute : Attribute {}
            }

            namespace Sample.Foundation
            {
                [Windows.Foundation.Metadata.Guid("11111111-1111-1111-1111-111111111111")]
                public interface IWidgetBase {}

                [Windows.Foundation.Metadata.Guid("22222222-2222-2222-2222-222222222222")]
                public interface IWidget : IWidgetBase
                {
                    string Name { get; }
                    int Value { get; set; }
                    event WidgetHandler Changed;
                    void Update(string input, out int written, ref int state);
                }

                public interface IWidgetFactory {}

                public interface IWidgetStatics {}

                public interface IBox<T> {}

                [Windows.Foundation.Metadata.ExclusiveTo(typeof(Widget<>))]
                public interface IWidgetOverrides {}

                [WinRT.Interop.ProjectionInternal]
                public interface IInternalContract {}

                [Windows.Foundation.Metadata.Guid("33333333-3333-3333-3333-333333333333")]
                [Windows.Foundation.Metadata.Activatable("Sample.Foundation.IWidgetFactory")]
                [Windows.Foundation.Metadata.Static("Sample.Foundation.IWidgetStatics")]
                [Windows.Foundation.Metadata.Composable("Sample.Foundation.IWidgetFactory")]
                public class Widget<T> : IWidget
                {
                    public string Name => "widget";
                    public int Value { get; set; }
                    public event WidgetHandler Changed;

                    public void Update(string input, out int written, ref int state)
                    {
                        written = input.Length;
                        state += 1;
                        Changed?.Invoke();
                    }
                }

                public enum Color { Red, Blue }

                public struct Point { public int X; public int Y; }

                [Windows.Foundation.Metadata.ApiContract]
                public struct WidgetContract { public int Version; }

                public sealed class WidgetAttribute : System.Attribute {}

                public abstract class WidgetStaticsClass {}

                public delegate void WidgetHandler();
            }

            namespace WinRT.Interop
            {
                using System;

                [AttributeUsage(AttributeTargets.Interface, AllowMultiple = false)]
                public sealed class ProjectionInternalAttribute : Attribute {}
            }
            """.trimIndent(),
        )

        runProcess(
            workingDirectory = projectDir,
            command = listOf(dotnet!!.toString(), "build", "-nologo", "-clp:ErrorsOnly"),
        )
        return projectDir.resolve("bin/Debug/net8.0/SampleMetadata.dll")
    }

    private fun findDotnet(): Path? =
        runCatching {
            val locatorCommand = if (isWindows()) listOf("where", "dotnet") else listOf("which", "dotnet")
            val process = ProcessBuilder(locatorCommand)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                Path.of(output.lineSequence().first())
            } else {
                null
            }
        }.getOrNull()

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private fun runProcess(workingDirectory: Path, command: List<String>) {
        assumeTrue("dotnet CLI is required for Metadata 2.1 tests", command.isNotEmpty())
        val process = ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "Command failed (${command.joinToString(" ")})\n$output"
        }
    }
}
