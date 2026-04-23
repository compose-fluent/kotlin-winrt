package io.github.kitectlab.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class WinRtMetadataModelTest {
    @Test
    fun preserves_declared_type_order() {
        val namespace = WinRtNamespace(
            name = "Windows.Foundation",
            types = listOf(
                WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IStringable"),
                WinRtTypeDefinition(namespace = "Windows.Foundation", name = "Uri"),
            ),
        )

        assertEquals(listOf("IStringable", "Uri"), namespace.types.map { it.name })
    }

    @Test
    fun normalizes_namespace_and_type_order_deterministically() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValue"),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonArray"),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "Uri"),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IStringable"),
                    ),
                ),
            ),
        )

        val normalized = model.normalized()

        assertEquals(listOf("Windows.Data.Json", "Windows.Foundation"), normalized.namespaces.map { it.name })
        assertEquals(listOf("JsonArray", "JsonValue"), normalized.namespaces[0].types.map { it.name })
        assertEquals(listOf("IStringable", "Uri"), normalized.namespaces[1].types.map { it.name })
    }

    @Test
    fun normalization_merges_duplicate_namespaces_types_and_methods_deterministically() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.Unknown,
                            baseTypeName = " System.Object ",
                            isProjectionInternal = true,
                            isSealedType = true,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(" Sample.Foundation.IWidget ", isProtected = true),
                            ),
                            genericParameterCount = 1,
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = " Sample.Foundation.IWidgetFactory ",
                                staticInterfaceNames = listOf(" Sample.Foundation.IWidgetStatics "),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "zeta",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition(" value ", " String ", WinRtParameterDirection.Ref)),
                                    methodRowId = 20,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = " Name ",
                                    typeName = " String ",
                                    getterMethodName = " get_Name ",
                                    getterMethodRowId = 30,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = " Changed ",
                                    delegateTypeName = " Sample.Foundation.WidgetHandler ",
                                    addMethodName = " add_Changed ",
                                    addMethodRowId = 40,
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isAttributeType = true,
                            isStaticType = true,
                            defaultInterfaceName = " Sample.Foundation.IWidget ",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            genericParameterCount = 1,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = " Sample.Foundation.IWidgetFactory ",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "alpha",
                                    returnTypeName = "Unit",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "zeta",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String", WinRtParameterDirection.Ref)),
                                    methodRowId = 20,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    getterMethodRowId = 30,
                                ),
                                WinRtPropertyDefinition(
                                    name = "Value",
                                    typeName = "Int",
                                    getterMethodName = "get_Value",
                                    setterMethodName = "set_Value",
                                    getterMethodRowId = 31,
                                    setterMethodRowId = 32,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    removeMethodName = "remove_Changed",
                                    removeMethodRowId = 41,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalized = model.normalized()

        assertEquals(listOf("Sample.Foundation"), normalized.namespaces.map { it.name })
        val widget = normalized.namespaces.single().types.single()
        assertEquals(WinRtTypeKind.RuntimeClass, widget.kind)
        assertEquals("System.Object", widget.baseTypeName)
        assertEquals(true, widget.isProjectionInternal)
        assertEquals(true, widget.isAttributeType)
        assertEquals(true, widget.isStaticType)
        assertEquals(true, widget.isSealedType)
        assertEquals("Sample.Foundation.IWidget", widget.defaultInterfaceName)
        assertEquals(listOf("Sample.Foundation.IWidget"), widget.implementedInterfaces.map { it.interfaceName })
        assertEquals(listOf(true), widget.implementedInterfaces.map { it.isDefault })
        assertEquals(1, widget.genericParameterCount)
        assertEquals(true, widget.activation.isActivatable)
        assertEquals("Sample.Foundation.IWidgetFactory", widget.activation.activatableFactoryInterfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), widget.activation.staticInterfaceNames)
        assertEquals("Sample.Foundation.IWidgetFactory", widget.activation.composableFactoryInterfaceName)
        assertEquals(listOf("alpha", "zeta"), widget.methods.map { it.name })
        assertEquals(listOf(10, 20), widget.methods.map { it.methodRowId })
        assertEquals(listOf("value"), widget.methods.last().parameters.map { it.name })
        assertEquals(listOf("String"), widget.methods.last().parameters.map { it.typeName })
        assertEquals(listOf(WinRtParameterDirection.Ref), widget.methods.last().parameters.map { it.direction })
        assertEquals(listOf("Name", "Value"), widget.properties.map { it.name })
        assertEquals(listOf(true, false), widget.properties.map { it.isReadOnly })
        assertEquals(listOf("get_Name", "get_Value"), widget.properties.map { it.getterMethodName })
        assertEquals(listOf(null, "set_Value"), widget.properties.map { it.setterMethodName })
        assertEquals(listOf(30, 31), widget.properties.map { it.getterMethodRowId })
        assertEquals(listOf(null, 32), widget.properties.map { it.setterMethodRowId })
        assertEquals(listOf("Changed"), widget.events.map { it.name })
        assertEquals(listOf("Sample.Foundation.WidgetHandler"), widget.events.map { it.delegateTypeName })
        assertEquals(listOf("add_Changed"), widget.events.map { it.addMethodName })
        assertEquals(listOf("remove_Changed"), widget.events.map { it.removeMethodName })
        assertEquals(listOf(40), widget.events.map { it.addMethodRowId })
        assertEquals(listOf(41), widget.events.map { it.removeMethodRowId })
    }

    @Test
    fun normalization_preserves_enum_underlying_type_and_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Status",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition(" Active ", 1u),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Status",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("None", 0u),
                                WinRtEnumMemberDefinition("Active", 1u),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val status = model.normalized().namespaces.single().types.single()

        assertEquals(WinRtIntegralType.Int32, status.enumUnderlyingType)
        assertEquals(
            listOf(
                WinRtEnumMemberDefinition("Active", 1u),
                WinRtEnumMemberDefinition("None", 0u),
            ),
            status.enumMembers,
        )
    }

    @Test
    fun structured_type_refs_canonicalize_generic_and_array_shapes() {
        val type = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "WidgetHolder",
            baseTypeName = " Sample.Foundation.IBox< Array< String > > ",
            defaultInterfaceName = " Sample.Foundation.IBox< Sample.Foundation.IBox<Int> > ",
            activation = WinRtActivationShape(
                activatableFactoryInterfaceName = " Sample.Foundation.IWidgetFactory ",
                staticInterfaceNames = listOf(" Sample.Foundation.IWidgetStatics ", "Sample.Foundation.IBox< String >"),
            ),
            methods = listOf(
                WinRtMethodDefinition(
                    name = "Transform",
                    returnTypeName = " Sample.Foundation.IBox<Array<T0>> ",
                    parameters = listOf(
                        WinRtParameterDefinition("input", " Sample.Foundation.IBox<T0> "),
                        WinRtParameterDefinition("state", " Sample.Foundation.IBox<Sample.Foundation.IBox<String>> ", WinRtParameterDirection.Ref),
                    ),
                ),
            ),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "PrimaryBox",
                    typeName = " Sample.Foundation.IBox< String > ",
                ),
            ),
            events = listOf(
                WinRtEventDefinition(
                    name = "Changed",
                    delegateTypeName = " Sample.Foundation.IHandler< Array<String> > ",
                ),
            ),
        ).normalized()

        assertEquals("Sample.Foundation.IBox<Array<String>>", type.baseTypeName)
        assertEquals(WinRtTypeRefKind.Named, type.baseType?.kind)
        assertEquals("Sample.Foundation.IBox", type.baseType?.qualifiedName)
        assertEquals("Array<String>", type.baseType?.typeArguments?.single()?.typeName)
        assertEquals(WinRtTypeRefKind.Array, type.baseType?.typeArguments?.single()?.kind)

        assertEquals("Sample.Foundation.IWidgetFactory", type.activation.activatableFactoryInterfaceName)
        assertEquals(
            listOf("Sample.Foundation.IBox<String>", "Sample.Foundation.IWidgetStatics"),
            type.activation.staticInterfaceNames,
        )
        assertEquals(
            listOf("Sample.Foundation.IBox<String>", "Sample.Foundation.IWidgetStatics"),
            type.activation.staticInterfaces.map { it.typeName },
        )

        val method = type.methods.single()
        assertEquals("Sample.Foundation.IBox<Array<T0>>", method.returnTypeName)
        assertEquals("Sample.Foundation.IBox", method.returnType.qualifiedName)
        assertEquals(WinRtTypeRefKind.Array, method.returnType.typeArguments.single().kind)
        assertEquals("T0", method.returnType.typeArguments.single().elementType?.typeName)
        assertEquals("Sample.Foundation.IBox<T0>", method.parameters.first().typeName)
        assertEquals(WinRtTypeRefKind.GenericTypeParameter, method.parameters.first().type.typeArguments.single().kind)
        assertEquals(
            "Sample.Foundation.IBox<Sample.Foundation.IBox<String>>",
            method.parameters.last().typeName,
        )
        assertEquals(
            "String",
            method.parameters.last().type.typeArguments.single().typeArguments.single().typeName,
        )

        val property = type.properties.single()
        assertEquals("Sample.Foundation.IBox<String>", property.typeName)
        assertEquals("String", property.type.typeArguments.single().typeName)

        val event = type.events.single()
        assertEquals("Sample.Foundation.IHandler<Array<String>>", event.delegateTypeName)
        assertEquals(WinRtTypeRefKind.Array, event.delegateType.typeArguments.single().kind)
        assertEquals("String", event.delegateType.typeArguments.single().elementType?.typeName)
    }

    @Test
    fun abi_resolver_exposes_generator_facing_type_and_parameter_categories() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidget", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Widget", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Priority",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                        ),
                    ),
                ),
            ),
        )

        val resolver = model.abiResolver()
        assertEquals(WinRtAbiTypeCategory.Fundamental, resolver.resolveType(WinRtTypeRef.fromDisplayName("Int"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.String, resolver.resolveType(WinRtTypeRef.fromDisplayName("String"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.Interface, resolver.resolveType(WinRtTypeRef.fromDisplayName("IWidget"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.RuntimeClass, resolver.resolveType(WinRtTypeRef.fromDisplayName("Widget"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.Delegate, resolver.resolveType(WinRtTypeRef.fromDisplayName("WidgetHandler"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.Struct, resolver.resolveType(WinRtTypeRef.fromDisplayName("Point"), "Sample.Foundation").category)
        assertEquals(WinRtAbiTypeCategory.Enum, resolver.resolveType(WinRtTypeRef.fromDisplayName("Priority"), "Sample.Foundation").category)
        assertEquals(
            WinRtIntegralType.Int32,
            resolver.resolveType(WinRtTypeRef.fromDisplayName("Priority"), "Sample.Foundation").enumUnderlyingType,
        )
        assertEquals(
            WinRtAbiTypeCategory.GenericTypeParameter,
            resolver.resolveType(WinRtTypeRef.fromDisplayName("T0"), "Sample.Foundation").category,
        )
        val arrayDescriptor = resolver.resolveType(WinRtTypeRef.fromDisplayName("Array<WidgetHandler>"), "Sample.Foundation")
        assertEquals(WinRtAbiTypeCategory.Array, arrayDescriptor.category)
        assertEquals(WinRtAbiTypeCategory.Delegate, arrayDescriptor.elementType?.category)

        val abiMethod = resolver.resolveMethod(
            WinRtMethodDefinition(
                name = "Project",
                returnTypeName = "Array<Widget>",
                parameters = listOf(
                    WinRtParameterDefinition("count", "Int"),
                    WinRtParameterDefinition("name", "String"),
                    WinRtParameterDefinition("widget", "IWidget"),
                    WinRtParameterDefinition("callback", "WidgetHandler"),
                    WinRtParameterDefinition("priority", "Priority"),
                    WinRtParameterDefinition("location", "Point"),
                    WinRtParameterDefinition("genericValue", "T0"),
                    WinRtParameterDefinition("passValues", "Array<Int>", isInParameter = true),
                    WinRtParameterDefinition(
                        "filledValues",
                        "Array<Int>",
                        direction = WinRtParameterDirection.Out,
                        isOutParameter = true,
                    ),
                    WinRtParameterDefinition(
                        "receivedValues",
                        "Array<Int>",
                        direction = WinRtParameterDirection.Out,
                        typeIsByRef = true,
                        isOutParameter = true,
                    ),
                ),
            ),
            currentNamespace = "Sample.Foundation",
        )

        assertEquals(WinRtAbiTypeCategory.Array, abiMethod.returnType.category)
        assertEquals(WinRtAbiTypeCategory.RuntimeClass, abiMethod.returnType.elementType?.category)
        assertEquals(
            listOf(
                WinRtAbiTypeCategory.Fundamental,
                WinRtAbiTypeCategory.String,
                WinRtAbiTypeCategory.Interface,
                WinRtAbiTypeCategory.Delegate,
                WinRtAbiTypeCategory.Enum,
                WinRtAbiTypeCategory.Struct,
                WinRtAbiTypeCategory.GenericTypeParameter,
                WinRtAbiTypeCategory.Array,
                WinRtAbiTypeCategory.Array,
                WinRtAbiTypeCategory.Array,
            ),
            abiMethod.parameters.map { it.type.category },
        )
        assertEquals(
            listOf(
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.In,
                WinRtAbiParameterCategory.PassArray,
                WinRtAbiParameterCategory.FillArray,
                WinRtAbiParameterCategory.ReceiveArray,
            ),
            abiMethod.parameters.map { it.category },
        )
    }

    @Test
    fun closure_resolver_materializes_default_precedence_and_generic_interface_closure() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IIterable", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IIterable<T0>"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetExtra",
                            kind = WinRtTypeKind.Interface,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IIterable<Int>"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            isExclusiveTo = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IIterable<String>"),
                            ),
                        ),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidgetFactory", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IVector<String>",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IVector<String>", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra", isOverridable = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetOverrides", isProtected = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val resolver = model.closureResolver()
        val vectorClosure = resolver.resolveInterface(
            WinRtTypeRef.fromDisplayName("Sample.Foundation.IVector<String>"),
            currentNamespace = "Sample.Foundation",
        )
        assertEquals("Sample.Foundation.IVector<String>", vectorClosure.interfaceName)
        assertEquals(listOf("Sample.Foundation.IIterable<String>"), vectorClosure.baseInterfaces.map { it.interfaceName })

        val widget = model.normalized().namespaces.single().types.first { it.name == "Widget" }
        val widgetClosure = resolver.resolveRuntimeClass(widget)
        assertEquals("Sample.Foundation.IVector<String>", widgetClosure.defaultInterfaceName)
        assertEquals(
            listOf(
                "Sample.Foundation.IVector<String>",
                "Sample.Foundation.IWidgetExtra",
                "Sample.Foundation.IWidgetOverrides",
            ),
            widgetClosure.instanceInterfaces.map { it.interfaceName },
        )
        assertEquals(
            listOf(
                WinRtRuntimeClassInterfaceKind.Default,
                WinRtRuntimeClassInterfaceKind.Implemented,
                WinRtRuntimeClassInterfaceKind.Implemented,
            ),
            widgetClosure.instanceInterfaces.map { it.kind },
        )
        assertEquals(listOf(true, false, false), widgetClosure.instanceInterfaces.map { it.isDefault })
        assertEquals(listOf(false, true, false), widgetClosure.instanceInterfaces.map { it.isOverridable })
        assertEquals(listOf(false, false, true), widgetClosure.instanceInterfaces.map { it.isProtected })
        assertEquals(listOf(false, false, true), widgetClosure.instanceInterfaces.map { it.isExclusiveTo })
        assertEquals(
            listOf(
                "Sample.Foundation.IVector<String>",
                "Sample.Foundation.IIterable<String>",
                "Sample.Foundation.IWidgetExtra",
                "Sample.Foundation.IIterable<Int>",
                "Sample.Foundation.IWidgetOverrides",
            ),
            widgetClosure.instanceInterfaceClosure.map { it.interfaceName },
        )
        assertEquals(true, widgetClosure.activation.isActivatable)
        assertEquals(
            "Sample.Foundation.IWidgetFactory",
            widgetClosure.activation.activatableFactoryInterface?.interfaceName,
        )
        assertEquals(
            listOf("Sample.Foundation.IWidgetStatics"),
            widgetClosure.activation.staticInterfaces.map { it.interfaceName },
        )
        assertEquals(
            listOf("Sample.Foundation.IIterable<String>"),
            widgetClosure.activation.staticInterfaces.single().closure?.baseInterfaces?.map { it.interfaceName },
        )
        assertEquals(
            "Sample.Foundation.IWidgetFactory",
            widgetClosure.activation.composableFactoryInterface?.interfaceName,
        )
    }

    @Test
    fun special_type_resolver_classifies_collection_async_reference_and_event_families() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IAsyncInfo", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IAsyncAction", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IAsyncActionWithProgress", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IAsyncOperation", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IAsyncOperationWithProgress", kind = WinRtTypeKind.Interface, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IReference", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IReferenceArray", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "EventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "TypedEventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "AsyncActionProgressHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "AsyncOperationProgressHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 2),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IIterable", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IIterator", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IVectorView", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IVector", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IMapView", kind = WinRtTypeKind.Interface, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IMap", kind = WinRtTypeKind.Interface, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IKeyValuePair", kind = WinRtTypeKind.Interface, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "VectorChangedEventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "MapChangedEventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 2),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "IBindableIterable", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "IBindableVector", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "NotifyCollectionChangedEventHandler", kind = WinRtTypeKind.Delegate),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Data",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Data", name = "PropertyChangedEventHandler", kind = WinRtTypeKind.Delegate),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Widget", kind = WinRtTypeKind.RuntimeClass),
                    ),
                ),
            ),
        )

        val resolver = model.specialTypeResolver()

        val vector = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.Collections.IVector<String>"),
            "Sample.Foundation",
        ) as WinRtCollectionTypeDescriptor
        assertEquals(WinRtCollectionInterfaceKind.Vector, vector.kind)
        assertEquals("String", vector.elementType?.displayName)

        val map = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.Collections.IMap<String, Int>"),
            "Sample.Foundation",
        ) as WinRtCollectionTypeDescriptor
        assertEquals(WinRtCollectionInterfaceKind.Map, map.kind)
        assertEquals("String", map.keyType?.displayName)
        assertEquals("Int", map.valueType?.displayName)

        val bindable = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.Interop.IBindableVector"),
            "Sample.Foundation",
        ) as WinRtBindableCollectionTypeDescriptor
        assertEquals(WinRtBindableCollectionKind.Vector, bindable.kind)
        assertEquals("System.Object", bindable.elementType?.displayName)

        val async = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.IAsyncOperationWithProgress<Sample.Foundation.Widget, Int>"),
            "Sample.Foundation",
        ) as WinRtAsyncTypeDescriptor
        assertEquals(WinRtAsyncInterfaceKind.OperationWithProgress, async.kind)
        assertEquals("Sample.Foundation.Widget", async.resultType?.displayName)
        assertEquals("Int", async.progressType?.displayName)

        val reference = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.IReferenceArray<String>"),
            "Sample.Foundation",
        ) as WinRtReferenceTypeDescriptor
        assertEquals(WinRtReferenceInterfaceKind.ReferenceArray, reference.kind)
        assertEquals("String", reference.valueType?.displayName)

        val eventHandler = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.EventHandler<String>"),
            "Sample.Foundation",
        ) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.EventHandler, eventHandler.kind)
        assertEquals("String", eventHandler.eventArgsType?.displayName)

        val typedEventHandler = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.TypedEventHandler<Sample.Foundation.Widget, String>"),
            "Sample.Foundation",
        ) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.TypedEventHandler, typedEventHandler.kind)
        assertEquals("Sample.Foundation.Widget", typedEventHandler.senderType?.displayName)
        assertEquals("String", typedEventHandler.eventArgsType?.displayName)

        val progressHandler = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.AsyncOperationProgressHandler<Sample.Foundation.Widget, Int>"),
            "Sample.Foundation",
        ) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.AsyncOperationProgressHandler, progressHandler.kind)
        assertEquals("Sample.Foundation.Widget", progressHandler.resultType?.displayName)
        assertEquals("Int", progressHandler.progressType?.displayName)

        val propertyChanged = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler"),
            "Sample.Foundation",
        ) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.PropertyChangedEventHandler, propertyChanged.kind)

        val collectionChanged = resolver.resolveType(
            WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventHandler"),
            "Sample.Foundation",
        ) as WinRtEventHandlerTypeDescriptor
        assertEquals(WinRtEventHandlerKind.NotifyCollectionChangedEventHandler, collectionChanged.kind)
    }
}
