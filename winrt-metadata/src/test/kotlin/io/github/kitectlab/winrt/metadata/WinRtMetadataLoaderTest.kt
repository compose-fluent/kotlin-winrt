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

        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.Data",
                "Microsoft.UI.Xaml.Interop",
                "Sample.Foundation",
                "WinRT.Interop",
                "Windows.Foundation",
                "Windows.Foundation.Collections",
                "Windows.Foundation.Metadata",
            ),
            model.namespaces.map { it.name },
        )
        val sampleNamespace = model.namespaces.first { it.name == "Sample.Foundation" }
        assertEquals(
            listOf(
                "Color",
                "IBox",
                "IGenericWidget",
                "IInternalContract",
                "ISpecialShapes",
                "IWidget",
                "IWidgetBase",
                "IWidgetFactory",
                "IWidgetOverrides",
                "IWidgetStatics",
                "Point",
                "Priority",
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
                WinRtTypeKind.Interface,
                WinRtTypeKind.Interface,
                WinRtTypeKind.Struct,
                WinRtTypeKind.Enum,
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
        assertEquals(listOf("Update", "UpdateArrays"), iWidget.methods.map { it.name })
        assertEquals("Unit", iWidget.methods.first().returnTypeName)
        assertTrue((iWidget.methods.first().methodRowId ?: 0) > 0)
        assertEquals(listOf("input", "written", "state"), iWidget.methods.first().parameters.map { it.name })
        assertEquals(listOf("String", "Int", "Int"), iWidget.methods.first().parameters.map { it.typeName })
        assertEquals(
            listOf(WinRtParameterDirection.In, WinRtParameterDirection.Out, WinRtParameterDirection.Ref),
            iWidget.methods.first().parameters.map { it.direction },
        )
        val updateArrays = iWidget.methods.last()
        assertEquals(listOf("input", "filled", "received"), updateArrays.parameters.map { it.name })
        assertEquals(listOf("Array<Int>", "Array<Int>", "Array<Int>"), updateArrays.parameters.map { it.typeName })
        assertEquals(
            listOf(WinRtParameterDirection.In, WinRtParameterDirection.Out, WinRtParameterDirection.Out),
            updateArrays.parameters.map { it.direction },
        )
        assertEquals(listOf(false, false, true), updateArrays.parameters.map { it.typeIsByRef })
        assertEquals(listOf(true, false, false), updateArrays.parameters.map { it.isInParameter })
        assertEquals(listOf(false, true, true), updateArrays.parameters.map { it.isOutParameter })
        assertEquals(listOf("Name", "Value"), iWidget.properties.map { it.name })
        assertEquals(listOf("String", "Int"), iWidget.properties.map { it.typeName })
        assertEquals(listOf(true, false), iWidget.properties.map { it.isReadOnly })
        assertEquals(listOf("get_Name", "get_Value"), iWidget.properties.map { it.getterMethodName })
        assertEquals(listOf(null, "set_Value"), iWidget.properties.map { it.setterMethodName })
        assertTrue((iWidget.properties[0].getterMethodRowId ?: 0) > 0)
        assertTrue((iWidget.properties[1].getterMethodRowId ?: 0) > (iWidget.properties[0].getterMethodRowId ?: 0))
        assertNull(iWidget.properties[0].setterMethodRowId)
        assertTrue((iWidget.properties[1].setterMethodRowId ?: 0) > (iWidget.properties[1].getterMethodRowId ?: 0))
        assertEquals(listOf("Changed"), iWidget.events.map { it.name })
        assertEquals(listOf("Sample.Foundation.WidgetHandler"), iWidget.events.map { it.delegateTypeName })
        assertEquals(listOf("add_Changed"), iWidget.events.map { it.addMethodName })
        assertEquals(listOf("remove_Changed"), iWidget.events.map { it.removeMethodName })
        assertTrue((iWidget.events.single().addMethodRowId ?: 0) > 0)
        assertTrue((iWidget.events.single().removeMethodRowId ?: 0) > (iWidget.events.single().addMethodRowId ?: 0))

        val iBox = sampleNamespace.types.first { it.name == "IBox" }
        assertEquals(1, iBox.genericParameterCount)

        val iGenericWidget = sampleNamespace.types.first { it.name == "IGenericWidget" }
        assertEquals(Guid("44444444-4444-4444-4444-444444444444"), iGenericWidget.iid)
        assertEquals(1, iGenericWidget.genericParameterCount)
        assertEquals("Sample.Foundation.IBox<String>", iGenericWidget.properties.single().typeName)
        assertEquals("Sample.Foundation.IBox", iGenericWidget.properties.single().type.qualifiedName)
        assertEquals(listOf("String"), iGenericWidget.properties.single().type.typeArguments.map { it.typeName })
        assertEquals("Sample.Foundation.IBox<Array<T0>>", iGenericWidget.methods.single().returnTypeName)
        assertEquals("Sample.Foundation.IBox", iGenericWidget.methods.single().returnType.qualifiedName)
        assertEquals(WinRtTypeRefKind.Array, iGenericWidget.methods.single().returnType.typeArguments.single().kind)
        assertEquals("T0", iGenericWidget.methods.single().returnType.typeArguments.single().elementType?.typeName)
        assertEquals(
            listOf(
                "Sample.Foundation.IBox<T0>",
                "Sample.Foundation.IBox<Int>",
                "Sample.Foundation.IBox<Sample.Foundation.IBox<String>>",
            ),
            iGenericWidget.methods.single().parameters.map { it.typeName },
        )
        assertEquals(
            listOf(
                "T0",
                "Int",
                "String",
            ),
            listOf(
                iGenericWidget.methods.single().parameters[0].type.typeArguments.single().typeName,
                iGenericWidget.methods.single().parameters[1].type.typeArguments.single().typeName,
                iGenericWidget.methods.single().parameters[2].type.typeArguments.single().typeArguments.single().typeName,
            ),
        )
        assertEquals(
            WinRtTypeRefKind.Named,
            iGenericWidget.methods.single().parameters[2].type.typeArguments.single().kind,
        )

        val abiResolver = model.abiResolver()
        val updateArraysAbi = abiResolver.resolveMethod(updateArrays, iWidget.namespace)
        assertEquals(WinRtAbiTypeCategory.Unit, updateArraysAbi.returnType.category)
        assertEquals(
            listOf(
                WinRtAbiParameterCategory.PassArray,
                WinRtAbiParameterCategory.FillArray,
                WinRtAbiParameterCategory.ReceiveArray,
            ),
            updateArraysAbi.parameters.map { it.category },
        )
        assertEquals(
            listOf(
                WinRtAbiTypeCategory.Fundamental,
                WinRtAbiTypeCategory.Fundamental,
                WinRtAbiTypeCategory.Fundamental,
            ),
            updateArraysAbi.parameters.map { it.type.elementType?.category },
        )

        val transformAbi = abiResolver.resolveMethod(iGenericWidget.methods.single(), iGenericWidget.namespace)
        assertEquals(WinRtAbiTypeCategory.Interface, transformAbi.returnType.category)
        assertEquals(WinRtAbiTypeCategory.Array, transformAbi.returnType.typeArguments.single().category)
        assertEquals(
            WinRtAbiTypeCategory.GenericTypeParameter,
            transformAbi.returnType.typeArguments.single().elementType?.category,
        )
        assertEquals(
            listOf(
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.Out,
                WinRtAbiParameterCategory.Ref,
            ),
            transformAbi.parameters.map { it.category },
        )
        assertEquals(WinRtAbiTypeCategory.Interface, transformAbi.parameters[0].type.category)
        assertEquals(WinRtAbiTypeCategory.GenericTypeParameter, transformAbi.parameters[0].type.typeArguments.single().category)
        assertEquals(WinRtAbiTypeCategory.Interface, transformAbi.parameters[2].type.typeArguments.single().category)
        assertEquals(
            WinRtAbiTypeCategory.String,
            transformAbi.parameters[2].type.typeArguments.single().typeArguments.single().category,
        )

        val closureResolver = model.closureResolver()
        val widgetClosure = closureResolver.resolveRuntimeClass(widget)
        assertNull(widgetClosure.defaultInterfaceName)
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetBase"),
            widgetClosure.instanceInterfaceClosure.map { it.interfaceName },
        )
        assertEquals(
            "Sample.Foundation.IWidgetFactory",
            widgetClosure.activation.activatableFactoryInterface?.interfaceName,
        )
        assertEquals(
            listOf("Sample.Foundation.IWidgetStatics"),
            widgetClosure.activation.staticInterfaces.map { it.interfaceName },
        )
        val iWidgetClosure = closureResolver.resolveInterface(iWidget)
        assertEquals(listOf("Sample.Foundation.IWidgetBase"), iWidgetClosure.baseInterfaces.map { it.interfaceName })

        val specialResolver = model.specialTypeResolver()
        val specialShapes = sampleNamespace.types.first { it.name == "ISpecialShapes" }
        val itemsDescriptor =
            specialResolver.resolveType(specialShapes.properties.first { it.name == "Items" }.type, specialShapes.namespace) as WinRtCollectionTypeDescriptor
        assertEquals(WinRtCollectionInterfaceKind.Vector, itemsDescriptor.kind)
        assertEquals("String", itemsDescriptor.elementType?.displayName)
        val lookupDescriptor =
            specialResolver.resolveType(specialShapes.properties.first { it.name == "Lookup" }.type, specialShapes.namespace) as WinRtCollectionTypeDescriptor
        assertEquals(WinRtCollectionInterfaceKind.Map, lookupDescriptor.kind)
        assertEquals("String", lookupDescriptor.keyType?.displayName)
        assertEquals("Int", lookupDescriptor.valueType?.displayName)
        val bindableDescriptor =
            specialResolver.resolveType(specialShapes.properties.first { it.name == "BindableVector" }.type, specialShapes.namespace) as WinRtBindableCollectionTypeDescriptor
        assertEquals(WinRtBindableCollectionKind.Vector, bindableDescriptor.kind)
        assertEquals("System.Object", bindableDescriptor.elementType?.displayName)
        val referenceDescriptor =
            specialResolver.resolveType(specialShapes.properties.first { it.name == "MaybeNames" }.type, specialShapes.namespace) as WinRtReferenceTypeDescriptor
        assertEquals(WinRtReferenceInterfaceKind.ReferenceArray, referenceDescriptor.kind)
        assertEquals("String", referenceDescriptor.valueType?.displayName)
        val asyncDescriptor =
            specialResolver.resolveType(specialShapes.methods.first { it.name == "LoadWithProgressAsync" }.returnType, specialShapes.namespace) as WinRtAsyncTypeDescriptor
        assertEquals(WinRtAsyncInterfaceKind.OperationWithProgress, asyncDescriptor.kind)
        assertEquals("String", asyncDescriptor.resultType?.displayName)
        assertEquals("Int", asyncDescriptor.progressType?.displayName)
        val eventDescriptor =
            specialResolver.resolveType(specialShapes.events.first { it.name == "WidgetChanged" }.delegateType, specialShapes.namespace) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.TypedEventHandler, eventDescriptor.kind)
        assertEquals("Sample.Foundation.Widget<Int>", eventDescriptor.senderType?.displayName)
        assertEquals("String", eventDescriptor.eventArgsType?.displayName)
        val vectorChangedDescriptor =
            specialResolver.resolveType(specialShapes.events.first { it.name == "ItemsChanged" }.delegateType, specialShapes.namespace) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.VectorChangedEventHandler, vectorChangedDescriptor.kind)
        assertEquals("String", vectorChangedDescriptor.elementType?.displayName)
        val propertyChangedDescriptor =
            specialResolver.resolveType(specialShapes.events.first { it.name == "PropertyChanged" }.delegateType, specialShapes.namespace) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.PropertyChangedEventHandler, propertyChangedDescriptor.kind)
        val collectionChangedDescriptor =
            specialResolver.resolveType(specialShapes.events.first { it.name == "CollectionChanged" }.delegateType, specialShapes.namespace) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.NotifyCollectionChangedEventHandler, collectionChangedDescriptor.kind)

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
        assertEquals(WinRtIntegralType.UInt32, color.enumUnderlyingType)
        assertEquals(
            listOf(
                WinRtEnumMemberDefinition("Red", 0u),
                WinRtEnumMemberDefinition("Blue", 1u),
            ),
            color.enumMembers,
        )

        val priority = sampleNamespace.types.first { it.name == "Priority" }
        assertEquals(WinRtIntegralType.Int32, priority.enumUnderlyingType)
        assertEquals(
            listOf(
                WinRtEnumMemberDefinition("Low", 0u),
                WinRtEnumMemberDefinition("High", 1u),
            ),
            priority.enumMembers,
        )

        assertEquals(listOf("Update", "UpdateArrays"), widget.methods.map { it.name })
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
                "IGenericWidget",
                "IInternalContract",
                "ISpecialShapes",
                "IWidget",
                "IWidgetBase",
                "IWidgetFactory",
                "IWidgetOverrides",
                "IWidgetStatics",
                "Point",
                "Priority",
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
                    void UpdateArrays(
                        [System.Runtime.InteropServices.In] int[] input,
                        [System.Runtime.InteropServices.Out] int[] filled,
                        out int[] received);
                }

                public interface IWidgetFactory {}

                public interface IWidgetStatics {}

                public interface IBox<T> {}

                public interface ISpecialShapes
                {
                    Windows.Foundation.Collections.IVector<string> Items { get; }
                    Windows.Foundation.Collections.IMap<string, int> Lookup { get; }
                    Microsoft.UI.Xaml.Interop.IBindableIterable BindableItems { get; }
                    Microsoft.UI.Xaml.Interop.IBindableVector BindableVector { get; }
                    Windows.Foundation.IReference<int> MaybeValue { get; }
                    Windows.Foundation.IReferenceArray<string> MaybeNames { get; }
                    Windows.Foundation.IAsyncAction StartAsync();
                    Windows.Foundation.IAsyncActionWithProgress<int> StartWithProgressAsync();
                    Windows.Foundation.IAsyncOperation<string> LoadAsync();
                    Windows.Foundation.IAsyncOperationWithProgress<string, int> LoadWithProgressAsync();
                    event Windows.Foundation.EventHandler<string> StatusChanged;
                    event Windows.Foundation.TypedEventHandler<Widget<int>, string> WidgetChanged;
                    event Windows.Foundation.Collections.VectorChangedEventHandler<string> ItemsChanged;
                    event Windows.Foundation.Collections.MapChangedEventHandler<string, int> LookupChanged;
                    event Microsoft.UI.Xaml.Data.PropertyChangedEventHandler PropertyChanged;
                    event Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler CollectionChanged;
                }

                [Windows.Foundation.Metadata.Guid("44444444-4444-4444-4444-444444444444")]
                public interface IGenericWidget<T>
                {
                    IBox<string> PrimaryBox { get; }
                    IBox<T[]> Transform(IBox<T> input, out IBox<int> written, ref IBox<IBox<string>> state);
                }

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

                    public void UpdateArrays(
                        [System.Runtime.InteropServices.In] int[] input,
                        [System.Runtime.InteropServices.Out] int[] filled,
                        out int[] received)
                    {
                        filled = input;
                        received = input;
                    }
                }

                public enum Color : uint { Red, Blue }

                public enum Priority { Low, High }

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

            namespace Windows.Foundation
            {
                public interface IAsyncInfo {}

                public interface IAsyncAction : IAsyncInfo {}

                public interface IAsyncActionWithProgress<TProgress> : IAsyncInfo {}

                public interface IAsyncOperation<TResult> : IAsyncInfo {}

                public interface IAsyncOperationWithProgress<TResult, TProgress> : IAsyncInfo {}

                public interface IReference<T> {}

                public interface IReferenceArray<T> {}

                public delegate void EventHandler<T>(object sender, T args);

                public delegate void TypedEventHandler<TSender, TResult>(TSender sender, TResult args);

                public delegate void AsyncActionProgressHandler<TProgress>(IAsyncActionWithProgress<TProgress> asyncInfo, TProgress progressInfo);

                public delegate void AsyncOperationProgressHandler<TResult, TProgress>(IAsyncOperationWithProgress<TResult, TProgress> asyncInfo, TProgress progressInfo);
            }

            namespace Windows.Foundation.Collections
            {
                public interface IIterable<T> {}

                public interface IIterator<T> {}

                public interface IVectorView<T> : IIterable<T> {}

                public interface IVector<T> : IIterable<T> {}

                public interface IMapView<TKey, TValue> : IIterable<IKeyValuePair<TKey, TValue>> {}

                public interface IMap<TKey, TValue> : IIterable<IKeyValuePair<TKey, TValue>> {}

                public interface IKeyValuePair<TKey, TValue> {}

                public delegate void VectorChangedEventHandler<T>(IVector<T> sender, object args);

                public delegate void MapChangedEventHandler<TKey, TValue>(IMap<TKey, TValue> sender, object args);
            }

            namespace Microsoft.UI.Xaml.Data
            {
                public delegate void PropertyChangedEventHandler(object sender, object args);
            }

            namespace Microsoft.UI.Xaml.Interop
            {
                public interface IBindableIterable {}

                public interface IBindableVector {}

                public delegate void NotifyCollectionChangedEventHandler(object sender, object args);
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
