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
                "IConstrainedBox",
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
        assertEquals("Windows.Foundation.UniversalApiContract", widget.availability.contractVersion?.contractName)
        assertEquals(0x00030000L, widget.availability.contractVersion?.version)
        assertEquals(3, widget.availability.contractVersion?.majorVersion)
        assertEquals("10.0.14393.0", widget.availability.contractVersion?.platformVersion)
        assertEquals(0x00020000L, widget.availability.version)
        assertEquals(
            listOf("Windows.Foundation.UniversalApiContract" to 0x00010000L),
            widget.availability.previousContractVersions.map { it.contractName to it.version },
        )
        assertEquals("Use Widget2", widget.availability.deprecations.single().message)
        assertEquals(1L, widget.availability.deprecations.single().kind)
        assertEquals(0x00040000L, widget.availability.deprecations.single().version)
        assertEquals("Windows.Foundation.UniversalApiContract", widget.availability.deprecations.single().contractName)
        assertEquals(2L, widget.availability.threadingModel)
        assertEquals(1L, widget.availability.marshalingBehavior)
        assertTrue(widget.availability.isMuse)
        assertTrue(widget.availability.isWebHostHidden)
        val probeAttribute = widget.customAttributes.first { it.typeName == "Sample.Foundation.AttributeProbeAttribute" }
        assertEquals("widget", (probeAttribute.fixedArguments[0] as WinRtCustomAttributeValue.StringValue).value)
        assertEquals("Sample.Foundation.IWidget", (probeAttribute.fixedArguments[1] as WinRtCustomAttributeValue.TypeValue).typeName)
        assertEquals(
            WinRtCustomAttributeValue.EnumValue("Sample.Foundation.AttributeMode", 1),
            probeAttribute.fixedArguments[2],
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            ((probeAttribute.fixedArguments[3] as WinRtCustomAttributeValue.ArrayValue).values)
                .map { (it as WinRtCustomAttributeValue.IntegralValue).value },
        )
        assertEquals(
            WinRtCustomAttributeValue.BooleanValue(true),
            probeAttribute.namedArguments.first { it.name == "Enabled" }.value,
        )
        assertEquals(
            listOf("alpha", "beta"),
            ((probeAttribute.namedArguments.first { it.name == "Tags" }.value as WinRtCustomAttributeValue.ArrayValue).values)
                .map { (it as WinRtCustomAttributeValue.StringValue).value },
        )

        val iWidget = sampleNamespace.types.first { it.name == "IWidget" }
        assertEquals(Guid("22222222-2222-2222-2222-222222222222"), iWidget.iid)
        assertEquals(listOf("Sample.Foundation.IWidgetBase"), iWidget.implementedInterfaces.map { it.interfaceName })
        assertEquals(listOf("Update", "UpdateArrays", "WithDefault"), iWidget.methods.map { it.name })
        assertEquals("Unit", iWidget.methods.first().returnTypeName)
        assertTrue((iWidget.methods.first().methodRowId ?: 0) > 0)
        assertEquals(WinRtMethodVisibility.Public, iWidget.methods.first().visibility)
        assertEquals("UpdateWithState", iWidget.methods.first().overloadName)
        assertTrue(iWidget.methods.first().isDefaultOverload)
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
        assertTrue(updateArrays.isNoException)
        val withDefault = iWidget.methods.last()
        assertEquals(listOf(true), withDefault.parameters.map { it.hasDefaultValue })
        assertEquals(listOf(7uL), withDefault.parameters.map { it.defaultValueBits })
        assertEquals(listOf(8), withDefault.parameters.map { it.defaultValueElementType })
        assertEquals(listOf("Name", "Value"), iWidget.properties.map { it.name })
        assertTrue(iWidget.properties.first { it.name == "Value" }.isNoException)
        assertTrue(iWidget.properties.all { it.hasValidAccessors })
        assertTrue(iWidget.events.single { it.name == "Changed" }.hasValidAccessors)
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
        assertEquals(listOf("T"), iBox.genericParameters.map { it.name })

        val iConstrainedBox = sampleNamespace.types.first { it.name == "IConstrainedBox" }
        assertEquals(1, iConstrainedBox.genericParameterCount)
        assertEquals(listOf("TItem"), iConstrainedBox.genericParameters.map { it.name })
        assertEquals(listOf("Sample.Foundation.IWidgetBase"), iConstrainedBox.genericParameters.single().constraints)
        assertEquals(listOf("Sample.Foundation.IBox<String>"), iConstrainedBox.implementedInterfaces.map { it.interfaceName })
        assertEquals("T0", iConstrainedBox.properties.single().typeName)

        val iGenericWidget = sampleNamespace.types.first { it.name == "IGenericWidget" }
        assertEquals(Guid("44444444-4444-4444-4444-444444444444"), iGenericWidget.iid)
        assertEquals(1, iGenericWidget.genericParameterCount)
        assertEquals(listOf("T"), iGenericWidget.genericParameters.map { it.name })
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

        val lookupIndex = model.lookupIndex()
        val widgetLookup = lookupIndex.typeLookup("Sample.Foundation.Widget")!!
        assertEquals("Sample.Foundation.Widget", widgetLookup.canonicalType.displayName)
        assertNull(widgetLookup.defaultInterface)
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetBase"),
            widgetLookup.declaredInterfaces.map { it.interfaceName },
        )
        val canonicalGenericWidget = lookupIndex.canonicalType("Widget<Int>", "Sample.Foundation")
        assertEquals("Sample.Foundation.Widget<Int>", canonicalGenericWidget.displayName)
        assertEquals("Sample.Foundation.Widget", canonicalGenericWidget.definitionQualifiedName)
        assertEquals(
            "Sample.Foundation.Widget",
            lookupIndex.typeLookup(WinRtTypeRef.fromDisplayName("Widget<Int>"), "Sample.Foundation")?.qualifiedTypeName,
        )
        val iWidgetLookup = lookupIndex.typeLookup("Sample.Foundation.IWidget")!!
        assertEquals(
            listOf("Sample.Foundation.IWidgetBase"),
            iWidgetLookup.interfaceClosure?.baseInterfaces?.map { it.interfaceName },
        )
        val updateSignatureKey = iWidgetLookup.methodsBySignatureKey.entries.first { it.value.name == "Update" }.key
        assertEquals("Update", iWidgetLookup.method(updateSignatureKey)?.name)
        assertEquals("Name", iWidgetLookup.property("Name")?.name)
        val changedSignatureKey = iWidgetLookup.eventsBySignatureKey.keys.single()
        assertEquals("Changed", iWidgetLookup.eventBySignature(changedSignatureKey)?.name)

        val specialResolver = model.specialTypeResolver()
        val specialShapes = sampleNamespace.types.first { it.name == "ISpecialShapes" }
        val specialShapesLookup = lookupIndex.typeLookup("Sample.Foundation.ISpecialShapes")!!
        val loadAsyncSignatureKey = specialShapesLookup.methodsBySignatureKey.entries.first { it.value.name == "LoadWithProgressAsync" }.key
        assertEquals("LoadWithProgressAsync", specialShapesLookup.method(loadAsyncSignatureKey)?.name)
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
        assertTrue(widgetStaticsClass.isFastAbi)
        assertEquals(120_000, widgetStaticsClass.gcPressureAmount)

        val point = sampleNamespace.types.first { it.name == "Point" }
        assertEquals(WinRtTypeLayoutKind.Explicit, point.layout.kind)
        assertEquals(4, point.layout.packingSize)
        assertEquals(16, point.layout.classSize)
        assertTrue(point.isBlittable)
        assertEquals(16, point.abiSize)
        assertEquals(4, point.abiAlignment)
        assertEquals(listOf("X", "Y", "Magic"), point.fields.map { it.name })
        assertEquals(listOf("Int", "Short", "Int"), point.fields.map { it.typeName })
        assertEquals(listOf(0, 8, null), point.fields.map { it.offset })
        assertEquals(listOf(false, false, true), point.fields.map { it.isStatic })
        assertEquals(listOf(false, false, true), point.fields.map { it.isLiteral })
        assertEquals(42uL, point.fields.single { it.name == "Magic" }.constantValueBits)

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
                "IConstrainedBox",
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

    @Test
    fun resolves_windows_sdk_platform_contract_metadata_sources() {
        val assembly = buildManagedMetadataSample()
        val sdkRoot = buildWindowsSdkMetadataRoot(
            version = "10.0.22621.0",
            platformContracts = listOf("Sample.Foundation" to assembly),
        )

        val cache = WinRtMetadataSourceResolver.resolve(
            WinRtMetadataSource.windowsSdk(version = "10.0.22621.0", sdkRoot = sdkRoot),
        )
        val model = WinRtMetadataLoader.load(
            WinRtMetadataSource.windowsSdk(version = "10.0.22621.0", sdkRoot = sdkRoot),
        )

        assertEquals(listOf("Sample.Foundation.winmd"), cache.files.map { it.fileName.toString() })
        assertTrue(model.namespaces.any { it.name == "Sample.Foundation" })
    }

    @Test
    fun resolves_latest_windows_sdk_version_and_extension_contracts() {
        val assembly = buildManagedMetadataSample()
        val sdkRoot = buildWindowsSdkMetadataRoot(
            version = "10.0.19041.0",
            platformContracts = listOf("Old.Contract" to assembly),
        )
        addWindowsSdkVersion(
            sdkRoot = sdkRoot,
            version = "10.0.22621.0",
            platformContracts = listOf("Sample.Foundation" to assembly),
            extensionContracts = listOf("Sample.Extension" to assembly),
        )

        val withoutExtensions = WinRtMetadataSourceResolver.resolve(
            WinRtMetadataSource.windowsSdk(sdkRoot = sdkRoot),
        )
        val withExtensions = WinRtMetadataSourceResolver.resolve(
            WinRtMetadataSource.windowsSdk(includeExtensions = true, sdkRoot = sdkRoot),
        )

        assertEquals(listOf("Sample.Foundation.winmd"), withoutExtensions.files.map { it.fileName.toString() })
        assertEquals(
            listOf("Sample.Extension.winmd", "Sample.Foundation.winmd"),
            withExtensions.files.map { it.fileName.toString() },
        )
    }

    @Test
    fun expands_response_file_inputs_and_applies_cswinrt_style_filters() {
        val assembly = buildManagedMetadataSample()
        val responseFile = Files.createTempFile("kotlin-winrt-metadata-inputs", ".rsp")
        responseFile.writeText("\"${assembly.parent}\"")
        val context = WinRtMetadataProjectionContext(
            sources = WinRtMetadataSource.parseInputs("@$responseFile"),
            include = setOf("Sample.Foundation"),
            exclude = setOf("Sample.Foundation.IInternal"),
            additionExclude = setOf("Sample.Foundation.Additions"),
            component = true,
            embedded = true,
            publicExclusiveTo = true,
            idicExclusiveTo = true,
            partialFactory = true,
        )

        val cache = context.resolveCache()
        val model = context.load()

        assertEquals(listOf(assembly.fileName.toString()), cache.files.map { it.fileName.toString() })
        assertTrue(context.filter.includes("Sample.Foundation.Widget"))
        assertFalse(context.filter.includes("Windows.Foundation.IStringable"))
        assertFalse(context.filter.includes("Sample.Foundation.IInternalContract"))
        assertTrue(context.additionFilter.includes("Sample.Foundation.Widget"))
        assertFalse(context.additionFilter.includes("Sample.Foundation.Additions.Custom"))
        assertTrue(context.component)
        assertTrue(context.embedded)
        assertTrue(context.publicExclusiveTo)
        assertTrue(context.idicExclusiveTo)
        assertTrue(context.partialFactory)
        assertTrue(model.namespaces.any { it.name == "Sample.Foundation" })
    }

    @Test
    fun loads_cli_metadata_with_auxiliary_tables_used_by_real_winmd_caches() {
        val assembly = buildAuxiliaryTableMetadataSample()

        val model = WinRtMetadataLoader.load(assembly).normalized()

        val namespace = model.namespaces.single { it.name == "Sample.Auxiliary" }
        val container = namespace.types.single { it.name == "Container" }
        assertTrue(container.methods.any { it.name == "Ping" })
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

                public enum DeprecationType { Deprecate, Remove }
                public enum ThreadingModel { STA, MTA, Both }
                public enum MarshalingType { None, Agile, Standard }
                public enum GCPressureAmount { Low, Medium, High }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = true)]
                public sealed class ContractVersionAttribute : Attribute
                {
                    public ContractVersionAttribute(Type contract, uint version) {}
                    public ContractVersionAttribute(string contract, uint version) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class VersionAttribute : Attribute
                {
                    public VersionAttribute(uint version) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = true)]
                public sealed class PreviousContractVersionAttribute : Attribute
                {
                    public PreviousContractVersionAttribute(Type contract, uint version) {}
                    public PreviousContractVersionAttribute(string contract, uint version) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = true)]
                public sealed class DeprecatedAttribute : Attribute
                {
                    public DeprecatedAttribute(string message, DeprecationType type, uint version, string contract) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class ThreadingAttribute : Attribute
                {
                    public ThreadingAttribute(ThreadingModel model) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class MarshalingBehaviorAttribute : Attribute
                {
                    public MarshalingBehaviorAttribute(MarshalingType type) {}
                }

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class MuseAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class WebHostHiddenAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.All, AllowMultiple = false)]
                public sealed class FastAbiAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.Class, AllowMultiple = false)]
                public sealed class GCPressureAttribute : Attribute
                {
                    public GCPressureAmount Amount { get; set; }
                }

                [AttributeUsage(AttributeTargets.Method, AllowMultiple = false)]
                public sealed class OverloadAttribute : Attribute
                {
                    public OverloadAttribute(string method) {}
                }

                [AttributeUsage(AttributeTargets.Method, AllowMultiple = false)]
                public sealed class DefaultOverloadAttribute : Attribute {}

                [AttributeUsage(AttributeTargets.Method | AttributeTargets.Property, AllowMultiple = false)]
                public sealed class NoExceptionAttribute : Attribute {}
            }

            namespace Sample.Foundation
            {
                [Windows.Foundation.Metadata.Guid("11111111-1111-1111-1111-111111111111")]
                public interface IWidgetBase {}

                [Windows.Foundation.Metadata.Guid("22222222-2222-2222-2222-222222222222")]
                public interface IWidget : IWidgetBase
                {
                    string Name { get; }
                    [Windows.Foundation.Metadata.NoException]
                    int Value { get; set; }
                    event WidgetHandler Changed;
                    [Windows.Foundation.Metadata.Overload("UpdateWithState")]
                    [Windows.Foundation.Metadata.DefaultOverload]
                    void Update(string input, out int written, ref int state);
                    [Windows.Foundation.Metadata.NoException]
                    void UpdateArrays(
                        [System.Runtime.InteropServices.In] int[] input,
                        [System.Runtime.InteropServices.Out] int[] filled,
                        out int[] received);
                    int WithDefault(int amount = 7);
                }

                public interface IWidgetFactory {}

                public interface IWidgetStatics {}

                public interface IBox<T> {}

                public interface IConstrainedBox<TItem> : IBox<string> where TItem : IWidgetBase
                {
                    TItem Item { get; }
                }

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

                public enum AttributeMode { Default, Important }

                [System.AttributeUsage(System.AttributeTargets.Class, AllowMultiple = false)]
                public sealed class AttributeProbeAttribute : System.Attribute
                {
                    public AttributeProbeAttribute(string name, System.Type targetType, AttributeMode mode, int[] values) {}
                    public bool Enabled { get; set; }
                    public string[] Tags { get; set; }
                }

                [Windows.Foundation.Metadata.Guid("33333333-3333-3333-3333-333333333333")]
                [Windows.Foundation.Metadata.Activatable("Sample.Foundation.IWidgetFactory")]
                [Windows.Foundation.Metadata.Static("Sample.Foundation.IWidgetStatics")]
                [Windows.Foundation.Metadata.Composable("Sample.Foundation.IWidgetFactory")]
                [Windows.Foundation.Metadata.ContractVersion("Windows.Foundation.UniversalApiContract", 0x00030000)]
                [Windows.Foundation.Metadata.Version(0x00020000)]
                [Windows.Foundation.Metadata.PreviousContractVersion("Windows.Foundation.UniversalApiContract", 0x00010000)]
                [Windows.Foundation.Metadata.Deprecated("Use Widget2", Windows.Foundation.Metadata.DeprecationType.Remove, 0x00040000, "Windows.Foundation.UniversalApiContract")]
                [Windows.Foundation.Metadata.Threading(Windows.Foundation.Metadata.ThreadingModel.Both)]
                [Windows.Foundation.Metadata.MarshalingBehavior(Windows.Foundation.Metadata.MarshalingType.Agile)]
                [Windows.Foundation.Metadata.Muse]
                [Windows.Foundation.Metadata.WebHostHidden]
                [AttributeProbe("widget", typeof(IWidget), AttributeMode.Important, new int[] { 1, 2, 3 }, Enabled = true, Tags = new string[] { "alpha", "beta" })]
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

                    public int WithDefault(int amount = 7) => amount;
                }

                public enum Color : uint { Red, Blue }

                public enum Priority { Low, High }

                [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Explicit, Pack = 4, Size = 16)]
                public struct Point
                {
                    [System.Runtime.InteropServices.FieldOffset(0)]
                    public int X;
                    [System.Runtime.InteropServices.FieldOffset(8)]
                    public short Y;
                    public const int Magic = 42;
                }

                [Windows.Foundation.Metadata.ApiContract]
                public struct WidgetContract { public int Version; }

                public sealed class WidgetAttribute : System.Attribute {}

                [Windows.Foundation.Metadata.FastAbi]
                [Windows.Foundation.Metadata.GCPressure(Amount = Windows.Foundation.Metadata.GCPressureAmount.Medium)]
                public static class WidgetStaticsClass {}

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

    private fun buildAuxiliaryTableMetadataSample(): Path {
        val dotnet = findDotnet()
        assumeTrue("dotnet CLI is required for Metadata 3.2 tests", dotnet != null)
        val tempDir = Files.createTempDirectory("kotlin-winrt-metadata-aux-test")
        val forwardedDir = tempDir.resolve("ForwardedTypes")
        val mainDir = tempDir.resolve("AuxiliaryMetadata")
        forwardedDir.createDirectories()
        mainDir.createDirectories()

        forwardedDir.resolve("ForwardedTypes.csproj").writeText(
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
        forwardedDir.resolve("ForwardedTypes.cs").writeText(
            """
            namespace External.Forwarded
            {
                public class ForwardedWidget {}
            }
            """.trimIndent(),
        )
        runProcess(
            workingDirectory = forwardedDir,
            command = listOf(dotnet!!.toString(), "build", "-nologo", "-clp:ErrorsOnly"),
        )

        mainDir.resolve("Resources").createDirectories()
        mainDir.resolve("Resources").resolve("sample.txt").writeText("resource")
        mainDir.resolve("AuxiliaryMetadata.csproj").writeText(
            """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
                <ImplicitUsings>disable</ImplicitUsings>
                <Nullable>disable</Nullable>
                <GenerateAssemblyInfo>false</GenerateAssemblyInfo>
                <GenerateTargetFrameworkAttribute>false</GenerateTargetFrameworkAttribute>
              </PropertyGroup>
              <ItemGroup>
                <Reference Include="ForwardedTypes">
                  <HintPath>..\ForwardedTypes\bin\Debug\net8.0\ForwardedTypes.dll</HintPath>
                </Reference>
                <EmbeddedResource Include="Resources\sample.txt" LogicalName="Sample.Auxiliary.sample.txt" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        mainDir.resolve("AuxiliaryTypes.cs").writeText(
            """
            [assembly: System.Runtime.CompilerServices.TypeForwardedTo(typeof(External.Forwarded.ForwardedWidget))]

            namespace Sample.Auxiliary
            {
                public class Container
                {
                    public class Nested {}

                    public void Ping() {}
                }
            }
            """.trimIndent(),
        )
        runProcess(
            workingDirectory = mainDir,
            command = listOf(dotnet.toString(), "build", "-nologo", "-clp:ErrorsOnly"),
        )
        return mainDir.resolve("bin/Debug/net8.0/AuxiliaryMetadata.dll")
    }

    private fun buildWindowsSdkMetadataRoot(
        version: String,
        platformContracts: List<Pair<String, Path>>,
        extensionContracts: List<Pair<String, Path>> = emptyList(),
    ): Path {
        val root = Files.createTempDirectory("kotlin-winrt-sdk-test")
        addWindowsSdkVersion(root, version, platformContracts, extensionContracts)
        return root
    }

    private fun addWindowsSdkVersion(
        sdkRoot: Path,
        version: String,
        platformContracts: List<Pair<String, Path>>,
        extensionContracts: List<Pair<String, Path>> = emptyList(),
    ) {
        writeApiContractXml(
            sdkRoot.resolve("Platforms").resolve("UAP").resolve(version).resolve("Platform.xml"),
            platformContracts.map { it.first },
        )
        platformContracts.forEach { (contractName, sourceAssembly) ->
            copyContractWinmd(sdkRoot, version, contractName, sourceAssembly)
        }
        if (extensionContracts.isNotEmpty()) {
            val manifest = sdkRoot
                .resolve("Extension SDKs")
                .resolve("SampleExtension")
                .resolve(version)
                .resolve("SDKManifest.xml")
            writeApiContractXml(manifest, extensionContracts.map { it.first })
            extensionContracts.forEach { (contractName, sourceAssembly) ->
                copyContractWinmd(sdkRoot, version, contractName, sourceAssembly)
            }
        }
    }

    private fun writeApiContractXml(path: Path, contractNames: List<String>) {
        path.parent.createDirectories()
        path.writeText(
            buildString {
                appendLine("""<ApplicationPlatform xmlns="urn:schemas-microsoft-com:platform">""")
                appendLine("  <ContainedApiContracts>")
                contractNames.forEach { contractName ->
                    appendLine("""    <ApiContract name="$contractName" version="1.0.0.0" />""")
                }
                appendLine("  </ContainedApiContracts>")
                appendLine("</ApplicationPlatform>")
            },
        )
    }

    private fun copyContractWinmd(
        sdkRoot: Path,
        sdkVersion: String,
        contractName: String,
        sourceAssembly: Path,
    ) {
        val target = sdkRoot
            .resolve("References")
            .resolve(sdkVersion)
            .resolve(contractName)
            .resolve("1.0.0.0")
            .resolve("$contractName.winmd")
        target.parent.createDirectories()
        Files.copy(sourceAssembly, target)
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
