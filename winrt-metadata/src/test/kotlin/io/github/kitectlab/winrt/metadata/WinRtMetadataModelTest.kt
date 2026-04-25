package io.github.kitectlab.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun type_classifier_converges_mapped_types_special_shapes_and_projection_categories() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IReference", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IVector", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "IBindableVector", kind = WinRtTypeKind.Interface),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "UniversalApiContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                            isBlittable = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            isAttributeType = true,
                            isProjectionInternal = true,
                        ),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidget", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Widget", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct, isBlittable = true),
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

        val classifier = model.typeClassifier()
        assertEquals(
            listOf("IBindableIterable", "IBindableVector", "INotifyCollectionChanged", "NotifyCollectionChangedAction", "NotifyCollectionChangedEventArgs", "NotifyCollectionChangedEventHandler"),
            classifier.mappedTypesInNamespace("Microsoft.UI.Xaml.Interop").map { it.abiName },
        )
        assertEquals(
            "System.Collections.IList",
            classifier.mappedType("Microsoft.UI.Xaml.Interop", "IBindableVector")?.mappedQualifiedName,
        )

        val vector = classifier.classify(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.Collections.IVector<String>"),
            "Sample.Foundation",
        )
        assertEquals(WinRtProjectionCategory.Interface, vector.projectionCategory)
        assertEquals(WinRtAbiTypeCategory.Interface, vector.abiCategory)
        assertEquals("Windows.Foundation.Collections.IVector", vector.mappedType?.abiQualifiedName)
        assertEquals("System.Collections.Generic.IList`1", vector.mappedType?.mappedQualifiedName)
        assertEquals(true, vector.requiresMarshaling)
        assertEquals(true, vector.hasCustomMembersOutput)
        assertEquals(WinRtSpecialTypeFamily.Collection, vector.specialType?.family)
        assertEquals(WinRtCollectionInterfaceKind.Vector, (vector.specialType as WinRtCollectionTypeDescriptor).kind)

        val bindable = classifier.classify(
            WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.Interop.IBindableVector"),
            "Sample.Foundation",
        )
        assertEquals("System.Collections.IList", bindable.mappedType?.mappedQualifiedName)
        assertEquals(true, bindable.requiresMarshaling)
        assertEquals(true, bindable.hasCustomMembersOutput)
        assertEquals(true, bindable.isXamlAlias)
        assertEquals(WinRtSpecialTypeFamily.BindableCollection, bindable.specialType?.family)

        val reference = classifier.classify(
            WinRtTypeRef.fromDisplayName("Windows.Foundation.IReference<Int>"),
            "Sample.Foundation",
        )
        assertEquals("System.Nullable", reference.mappedType?.mappedQualifiedName)
        assertEquals(true, reference.requiresMarshaling)
        assertEquals(WinRtSpecialTypeFamily.Reference, reference.specialType?.family)
        assertEquals(WinRtReferenceInterfaceKind.Reference, (reference.specialType as WinRtReferenceTypeDescriptor).kind)

        val xamlHelper = classifier.classify(
            WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.GridLengthHelper"),
            "Sample.Foundation",
        )
        assertEquals(true, xamlHelper.isMappedType)
        assertEquals(null, xamlHelper.mappedType?.mappedQualifiedName)
        assertEquals(true, xamlHelper.isXamlAlias)

        val apiContract = classifier.classify(WinRtTypeRef.fromDisplayName("UniversalApiContract"), "Sample.Foundation")
        assertEquals(WinRtProjectionCategory.ApiContract, apiContract.projectionCategory)
        assertEquals(WinRtAbiTypeCategory.Struct, apiContract.abiCategory)
        assertEquals(true, apiContract.isApiContract)
        assertEquals(true, apiContract.isBlittable)

        val attribute = classifier.classify(WinRtTypeRef.fromDisplayName("WidgetAttribute"), "Sample.Foundation")
        assertEquals(WinRtProjectionCategory.Attribute, attribute.projectionCategory)
        assertEquals(WinRtAbiTypeCategory.RuntimeClass, attribute.abiCategory)
        assertEquals(true, attribute.isAttributeType)
        assertEquals(true, attribute.isProjectionInternal)

        assertEquals(
            WinRtProjectionCategory.Fundamental,
            classifier.classify(WinRtTypeRef.fromDisplayName("Int"), "Sample.Foundation").projectionCategory,
        )
        assertEquals(
            WinRtProjectionCategory.Enum,
            classifier.classify(WinRtTypeRef.fromDisplayName("Priority"), "Sample.Foundation").projectionCategory,
        )
        assertEquals(
            WinRtProjectionCategory.Struct,
            classifier.classify(WinRtTypeRef.fromDisplayName("Point"), "Sample.Foundation").projectionCategory,
        )
        assertEquals(
            WinRtProjectionCategory.Interface,
            classifier.classify(WinRtTypeRef.fromDisplayName("IWidget"), "Sample.Foundation").projectionCategory,
        )
        assertEquals(
            WinRtProjectionCategory.Delegate,
            classifier.classify(WinRtTypeRef.fromDisplayName("WidgetHandler"), "Sample.Foundation").projectionCategory,
        )
        assertEquals(
            WinRtProjectionCategory.RuntimeClass,
            classifier.classify(WinRtTypeRef.fromDisplayName("Widget"), "Sample.Foundation").projectionCategory,
        )
    }

    @Test
    fun metadata_validator_reports_projection_blocking_diagnostics_before_generator_emission() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "M0",
                                    parameters = listOf(
                                        WinRtParameterDefinition("missing", "Missing.Foundation.IMissing"),
                                    ),
                                    returnParameterAttributes = listOf(
                                        WinRtCustomAttributeDefinition(
                                            typeName = "Missing.Foundation.ReturnAttribute",
                                            fixedArguments = listOf(WinRtCustomAttributeValue.NullValue),
                                        ),
                                    ),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "BrokenProperty",
                                    typeName = "Missing.Foundation.Value",
                                    hasValidAccessors = false,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "BrokenEvent",
                                    delegateTypeName = "Missing.Foundation.Handler",
                                    hasValidAccessors = false,
                                ),
                            ),
                            customAttributes = listOf(
                                WinRtCustomAttributeDefinition(
                                    typeName = "Missing.Foundation.TypeAttribute",
                                    fixedArguments = listOf(
                                        WinRtCustomAttributeValue.TypeValue("Missing.Foundation.TypeArgument"),
                                        WinRtCustomAttributeValue.EnumValue("Missing.Foundation.Mode", 1),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "DoWork", returnTypeName = "Unit"),
                                WinRtMethodDefinition(name = "Create", returnTypeName = "Unit", isStatic = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val report = model.validateForProjection(
            WinRtMetadataValidationOptions(validateCustomAttributeTypeReferences = true),
        )
        val codes = report.errors.map(WinRtMetadataDiagnostic::code).toSet()

        assertTrue(report.hasErrors)
        assertTrue(WinRtMetadataDiagnosticCode.MissingInterfaceIid in codes)
        assertTrue(WinRtMetadataDiagnosticCode.UnsupportedGenericMethodShape in codes)
        assertTrue(WinRtMetadataDiagnosticCode.UnresolvedTypeReference in codes)
        assertTrue(WinRtMetadataDiagnosticCode.InvalidPropertyAccessors in codes)
        assertTrue(WinRtMetadataDiagnosticCode.InvalidEventAccessors in codes)
        assertTrue(WinRtMetadataDiagnosticCode.UnknownCustomAttributeBlob in codes)
        assertTrue(WinRtMetadataDiagnosticCode.MissingRuntimeClassDefaultInterface in codes)
        assertTrue(WinRtMetadataDiagnosticCode.MissingRuntimeClassStaticInterface in codes)
        assertTrue(WinRtMetadataDiagnosticCode.MissingActivationFactoryMetadata in codes)

        val error = runCatching {
            model.requireValidForProjection(
                WinRtMetadataValidationOptions(validateCustomAttributeTypeReferences = true),
            )
        }.exceptionOrNull()
        assertTrue(error is WinRtMetadataDiagnosticException)
        assertTrue(error?.message?.contains("Sample.Foundation.IWidget.Transform") == true)
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
                            availability = WinRtAvailabilityMetadata(
                                contractVersion = WinRtContractVersionMetadata(
                                    contractName = "Windows.Foundation.UniversalApiContract",
                                    version = 0x00030000,
                                    platformVersion = "10.0.14393.0",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetLegacy",
                            kind = WinRtTypeKind.Interface,
                            isExclusiveTo = true,
                            availability = WinRtAvailabilityMetadata(
                                contractVersion = WinRtContractVersionMetadata(
                                    contractName = "Windows.Foundation.UniversalApiContract",
                                    version = 0x00020000,
                                    platformVersion = "10.0.10586.0",
                                ),
                                previousContractVersions = listOf(
                                    WinRtContractVersionMetadata(
                                        contractName = "Windows.Foundation.UniversalApiContract",
                                        version = 0x00010000,
                                    ),
                                ),
                            ),
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
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "BaseWidget", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.BaseWidget",
                            isFastAbi = true,
                            gcPressureAmount = 120_000,
                            defaultInterfaceName = "Sample.Foundation.IVector<String>",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IVector<String>", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra", isOverridable = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetOverrides", isProtected = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetLegacy"),
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
        assertEquals(1, widgetClosure.classHierarchyIndex)
        assertEquals(true, widgetClosure.isFastAbi)
        assertEquals(120_000, widgetClosure.gcPressureAmount)
        assertEquals(
            listOf(
                "Sample.Foundation.IVector<String>",
                "Sample.Foundation.IWidgetExtra",
                "Sample.Foundation.IWidgetLegacy",
                "Sample.Foundation.IWidgetOverrides",
            ),
            widgetClosure.instanceInterfaces.map { it.interfaceName },
        )
        assertEquals(
            listOf(
                WinRtRuntimeClassInterfaceKind.Default,
                WinRtRuntimeClassInterfaceKind.Implemented,
                WinRtRuntimeClassInterfaceKind.Implemented,
                WinRtRuntimeClassInterfaceKind.Implemented,
            ),
            widgetClosure.instanceInterfaces.map { it.kind },
        )
        assertEquals(listOf(true, false, false, false), widgetClosure.instanceInterfaces.map { it.isDefault })
        assertEquals(listOf(false, true, false, false), widgetClosure.instanceInterfaces.map { it.isOverridable })
        assertEquals(listOf(false, false, false, true), widgetClosure.instanceInterfaces.map { it.isProtected })
        assertEquals(listOf(false, false, true, true), widgetClosure.instanceInterfaces.map { it.isExclusiveTo })
        assertEquals(
            listOf(
                "Sample.Foundation.IVector<String>",
                "Sample.Foundation.IWidgetLegacy",
                "Sample.Foundation.IWidgetOverrides",
            ),
            widgetClosure.fastAbiInterfaces.map { it.interfaceName },
        )
        assertEquals(
            listOf(
                "Sample.Foundation.IVector<String>",
                "Sample.Foundation.IIterable<String>",
                "Sample.Foundation.IWidgetExtra",
                "Sample.Foundation.IIterable<Int>",
                "Sample.Foundation.IWidgetLegacy",
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
    fun lookup_index_materializes_canonical_types_member_signatures_and_interface_queries() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidgetBase", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Open",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("id", "Int")),
                                ),
                                WinRtMethodDefinition(
                                    name = "Open",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler"),
                            ),
                        ),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidgetStatics", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            genericParameterCount = 1,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        ).normalized()

        val index = model.lookupIndex()
        val canonicalLocal = index.canonicalType("Widget", "Sample.Foundation")
        assertEquals("Sample.Foundation.Widget", canonicalLocal.displayName)
        assertEquals("Sample.Foundation.Widget", canonicalLocal.definitionQualifiedName)
        val canonicalGeneric = index.canonicalType("Widget<String>", "Sample.Foundation")
        assertEquals("Sample.Foundation.Widget<String>", canonicalGeneric.displayName)
        assertEquals("Sample.Foundation.Widget", canonicalGeneric.definitionQualifiedName)

        val widgetLookup = index.typeLookup("Sample.Foundation.Widget")!!
        assertEquals("Sample.Foundation.IWidget", widgetLookup.defaultInterface?.interfaceName)
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetBase"),
            widgetLookup.declaredInterfaces.map { it.interfaceName },
        )
        assertEquals(true, widgetLookup.declaredInterface("Sample.Foundation.IWidget")?.isDefault)
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetBase"),
            widgetLookup.runtimeClassClosure?.instanceInterfaceClosure?.map { it.interfaceName },
        )

        val iWidgetLookup = index.typeLookup(WinRtTypeRef.fromDisplayName("IWidget"), "Sample.Foundation")!!
        assertEquals("Sample.Foundation.IWidget", iWidgetLookup.qualifiedTypeName)
        assertEquals(
            listOf("Sample.Foundation.IWidgetBase"),
            iWidgetLookup.interfaceClosure?.baseInterfaces?.map { it.interfaceName },
        )
        assertEquals(2, iWidgetLookup.methodOverloads("Open").size)
        val intOverload = iWidgetLookup.methodOverloads("Open").first { it.parameters.single().typeName == "Int" }
        val intOverloadKey = iWidgetLookup.methodsBySignatureKey.entries.first { it.value == intOverload }.key
        assertEquals(intOverload, iWidgetLookup.method(intOverloadKey))
        assertEquals("Name", iWidgetLookup.property("Name")?.name)
        val propertySignatureKey = iWidgetLookup.propertiesBySignatureKey.keys.single()
        assertEquals("Name", iWidgetLookup.propertyBySignature(propertySignatureKey)?.name)
        assertEquals("Changed", iWidgetLookup.event("Changed")?.name)
        val eventSignatureKey = iWidgetLookup.eventsBySignatureKey.keys.single()
        assertEquals("Changed", iWidgetLookup.eventBySignature(eventSignatureKey)?.name)
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
    @Test
    fun resolves_type_semantics_like_cswinrt_helper_kernel() {
        val iBox = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IBox",
            kind = WinRtTypeKind.Interface,
            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(iBox, widget),
                ),
            ),
        )
        val resolver = model.typeSemanticsResolver()

        assertEquals(
            WinRtTypeSemantics.Fundamental(WinRtFundamentalType.String),
            resolver.resolve(WinRtTypeRef.named("System.String"), "Sample.Foundation"),
        )
        assertEquals(WinRtTypeSemantics.Object, resolver.resolve(WinRtTypeRef.named("System.Object"), "Sample.Foundation"))
        assertEquals(WinRtTypeSemantics.Guid, resolver.resolve(WinRtTypeRef.named("System.Guid"), "Sample.Foundation"))
        assertEquals(WinRtTypeSemantics.Type, resolver.resolve(WinRtTypeRef.named("System.Type"), "Sample.Foundation"))
        assertEquals(
            WinRtTypeSemantics.TypeDefinition(widget),
            resolver.resolve(WinRtTypeRef.named("Widget"), "Sample.Foundation"),
        )

        val generic = resolver.resolve(
            WinRtTypeRef.named(
                "Sample.Foundation.IBox",
                listOf(WinRtTypeRef.genericTypeParameter(0)),
            ),
            currentNamespace = "Sample.Foundation",
            genericParameters = iBox.genericParameters,
        ) as WinRtTypeSemantics.GenericTypeInstance

        assertEquals(iBox.qualifiedName, generic.genericType.qualifiedName)
        assertEquals(
            WinRtTypeSemantics.GenericTypeParameter(iBox.genericParameters.single()),
            generic.genericArguments.single(),
        )
    }

    @Test
    fun semantic_helpers_mirror_cswinrt_method_type_and_accessor_helpers() {
        val defaultInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            availability = WinRtAvailabilityMetadata(
                contractVersion = WinRtContractVersionMetadata(
                    contractName = "Windows.Foundation.UniversalApiContract",
                    version = 0x00030000,
                    platformVersion = "10.0.14393.0",
                ),
                version = 0x00020000,
            ),
            customAttributes = listOf(
                WinRtCustomAttributeDefinition(
                    typeName = "Sample.Foundation.MarkerAttribute",
                    fixedArguments = listOf(WinRtCustomAttributeValue.StringValue("marked")),
                ),
            ),
            methods = listOf(
                WinRtMethodDefinition(
                    name = "Invoke",
                    returnTypeName = "Unit",
                    isSpecialName = true,
                    parameters = listOf(
                        WinRtParameterDefinition("value", "String"),
                    ),
                ),
                WinRtMethodDefinition(
                    name = "UpdateArrays",
                    returnTypeName = "Unit",
                    parameters = listOf(
                        WinRtParameterDefinition("input", "Array<Int>", isInParameter = true),
                        WinRtParameterDefinition("filled", "Array<Int>", direction = WinRtParameterDirection.Out, isOutParameter = true),
                        WinRtParameterDefinition("received", "Array<Int>", direction = WinRtParameterDirection.Out, typeIsByRef = true, isOutParameter = true),
                    ),
                ),
                WinRtMethodDefinition(
                    name = "remove_Changed",
                    returnTypeName = "Unit",
                    isSpecialName = true,
                    isRemoveOverload = true,
                ),
            ),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Name",
                    typeName = "String",
                    getterMethodName = "get_Name",
                    getterMethodRowId = 10,
                ),
            ),
            events = listOf(
                WinRtEventDefinition(
                    name = "Changed",
                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                    addMethodName = "add_Changed",
                    removeMethodName = "remove_Changed",
                    addMethodRowId = 20,
                    removeMethodRowId = 21,
                ),
            ),
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            isStaticType = true,
            isFastAbi = true,
            gcPressureAmount = 120_000,
            defaultInterfaceName = "Sample.Foundation.IWidget",
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition(
                    interfaceName = "Sample.Foundation.IWidget",
                    isDefault = true,
                    isOverridable = true,
                ),
                WinRtInterfaceImplementationDefinition(
                    interfaceName = "Sample.Foundation.IWidgetOverrides",
                ),
            ),
            activation = WinRtActivationShape(
                isActivatable = true,
                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
            ),
        )
        val overrides = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidgetOverrides",
            kind = WinRtTypeKind.Interface,
            isExclusiveTo = true,
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Mode",
                    typeName = "Int",
                    getterMethodName = "get_Mode",
                    setterMethodName = "set_Mode",
                ),
            ),
        )
        val factory = WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidgetFactory", kind = WinRtTypeKind.Interface)
        val statics = WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IWidgetStatics", kind = WinRtTypeKind.Interface)
        val flags = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "WidgetOptions",
            kind = WinRtTypeKind.Enum,
            customAttributes = listOf(WinRtCustomAttributeDefinition("System.FlagsAttribute")),
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(defaultInterface, widget, overrides, factory, statics, flags),
                ),
            ),
        )
        val helpers = model.semanticHelpers()

        val signature = helpers.methodSignature(defaultInterface.methods[1])
        assertEquals("Unit", signature.returnType.typeName)
        assertEquals(true, signature.hasParams())
        assertEquals(
            listOf(
                WinRtMetadataParameterCategory.PassArray,
                WinRtMetadataParameterCategory.FillArray,
                WinRtMetadataParameterCategory.ReceiveArray,
            ),
            signature.parameters.map { it.category },
        )
        assertEquals(true, helpers.isRemoveOverload(defaultInterface.methods[2]))
        assertEquals(true, helpers.isNoException(defaultInterface.methods[2]))
        assertEquals(true, helpers.isFlagsEnum(flags))
        assertEquals(true, helpers.isStatic(widget))
        assertEquals(true, helpers.isOverridable(widget.implementedInterfaces.first()))
        assertEquals("Invoke", helpers.getDelegateInvoke(defaultInterface)?.name)
        assertEquals("Sample.Foundation.IWidget", helpers.getDefaultInterface(widget)?.typeName)
        assertEquals(true, helpers.hasAttribute(defaultInterface, "Sample.Foundation.MarkerAttribute"))
        assertEquals(1, helpers.getNumberOfAttributes(defaultInterface, "Sample.Foundation.MarkerAttribute"))
        assertEquals(
            WinRtCustomAttributeValue.StringValue("marked"),
            helpers.getAttributeValue(
                requireNotNull(helpers.getAttribute(defaultInterface, "Sample.Foundation.MarkerAttribute")),
                0,
            ),
        )
        assertEquals(0x00030000L, helpers.getContractVersion(defaultInterface))
        assertEquals(0x00020000L, helpers.getVersion(defaultInterface))
        assertEquals("10.0.14393.0", helpers.getContractPlatform(defaultInterface))

        val propertyAccessors = helpers.getPropertyMethods(defaultInterface.properties.single())
        assertEquals("get_Name", propertyAccessors.getterMethodName)
        assertEquals(null, propertyAccessors.setterMethodName)

        val eventAccessors = helpers.getEventMethods(defaultInterface.events.single())
        assertEquals("add_Changed", eventAccessors.addMethodName)
        assertEquals("remove_Changed", eventAccessors.removeMethodName)

        val attributedTypes = helpers.getAttributedTypes(widget)
        assertEquals(listOf("Sample.Foundation.IWidgetFactory", "Sample.Foundation.IWidgetStatics"), attributedTypes.map { it.interfaceName })
        assertEquals(listOf(true, false), attributedTypes.map { it.activatable })
        assertEquals(listOf(false, true), attributedTypes.map { it.statics })
        assertEquals(listOf(true, false), attributedTypes.map { it.composable })
        val (defaultFastAbi, exclusiveFastAbi) = helpers.getDefaultAndExclusiveInterfaces(widget)
        assertEquals("Sample.Foundation.IWidget", defaultFastAbi?.interfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetOverrides"), exclusiveFastAbi.map { it.interfaceName })
        val fastAbiClass = requireNotNull(helpers.getFastAbiClassForClass(widget))
        assertEquals("Sample.Foundation.IWidget", fastAbiClass.defaultInterface?.interfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetOverrides"), fastAbiClass.otherInterfaces.map { it.interfaceName })
        assertEquals(listOf("Name", "Mode"), fastAbiClass.propertySlots.map { it.propertyName })
        assertEquals(120_000, helpers.getGcPressureAmount(widget))
        assertEquals("Sample.Foundation.Widget", helpers.getFastAbiClassForInterface(overrides)?.classType?.qualifiedName)
    }

    @Test
    fun semantic_helpers_own_mapped_projection_type_lookup() {
        val helpers = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "IBindableVector", kind = WinRtTypeKind.Interface),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.UI.Xaml", name = "IGridLengthHelperStatics", kind = WinRtTypeKind.Interface),
                    ),
                ),
            ),
        ).semanticHelpers()

        val muxBindableVector = requireNotNull(helpers.getMappedType("Microsoft.UI.Xaml.Interop", "IBindableVector"))
        assertEquals("System.Collections.IList", muxBindableVector.mappedQualifiedName)
        assertEquals(true, muxBindableVector.requiresMarshaling)
        assertEquals(true, muxBindableVector.hasCustomMembersOutput)
        assertEquals(true, muxBindableVector.isXamlAlias)

        val wuxBindableVector = requireNotNull(helpers.getMappedType("Windows.UI.Xaml.Interop", "IBindableVector"))
        assertEquals("System.Collections.IList", wuxBindableVector.mappedQualifiedName)
        assertEquals(true, wuxBindableVector.requiresMarshaling)
        assertEquals(true, wuxBindableVector.hasCustomMembersOutput)

        val helperOnly = requireNotNull(helpers.getMappedType("Windows.UI.Xaml", "IGridLengthHelperStatics"))
        assertEquals(null, helperOnly.mappedQualifiedName)
        assertEquals(true, helperOnly.isXamlAlias)
        assertEquals(
            listOf("IBindableIterable", "IBindableVector", "INotifyCollectionChanged", "NotifyCollectionChangedAction", "NotifyCollectionChangedEventArgs", "NotifyCollectionChangedEventHandler"),
            helpers.getMappedTypesInNamespace("Microsoft.UI.Xaml.Interop").map { it.abiName },
        )
        assertEquals(
            "System.Collections.IList",
            helpers.getMappedType(
                WinRtTypeRef.fromDisplayName("Microsoft.UI.Xaml.Interop.IBindableVector"),
                "Sample.Foundation",
            )?.mappedQualifiedName,
        )
    }

    @Test
    fun semantic_helpers_describe_value_type_layout_and_blittability() {
        val point = WinRtTypeDefinition(
            namespace = "Windows.Foundation",
            name = "Point",
            kind = WinRtTypeKind.Struct,
            fields = listOf(
                WinRtFieldDefinition("X", "Float", rowId = 1, offset = 0, abiSize = 4, abiAlignment = 4, isBlittable = true),
                WinRtFieldDefinition("Y", "Float", rowId = 2, offset = 4, abiSize = 4, abiAlignment = 4, isBlittable = true),
            ),
            layout = WinRtTypeLayout(kind = WinRtTypeLayoutKind.Sequential, packingSize = 4, classSize = 8),
            isBlittable = true,
            abiSize = 8,
            abiAlignment = 4,
        )
        val mode = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "WidgetMode",
            kind = WinRtTypeKind.Enum,
            enumUnderlyingType = WinRtIntegralType.UInt32,
            enumMembers = listOf(
                WinRtEnumMemberDefinition("None", 0u),
                WinRtEnumMemberDefinition("Active", 1u),
            ),
            customAttributes = listOf(WinRtCustomAttributeDefinition("System.FlagsAttribute")),
        )
        val holder = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Holder",
            kind = WinRtTypeKind.Struct,
            fields = listOf(
                WinRtFieldDefinition("Name", "String", rowId = 1, isBlittable = false),
            ),
            isBlittable = false,
        )
        val helpers = WinRtMetadataModel(
            listOf(
                WinRtNamespace("Windows.Foundation", listOf(point)),
                WinRtNamespace("Sample.Foundation", listOf(mode, holder)),
            ),
        ).semanticHelpers()

        val pointDescriptor = helpers.valueTypeDescriptor(point)
        assertEquals(true, pointDescriptor.isValueType)
        assertEquals(true, pointDescriptor.isBlittable)
        assertEquals(false, pointDescriptor.requiresAbiCompanionShape)
        assertEquals(8, pointDescriptor.abiSize)
        assertEquals(4, pointDescriptor.abiAlignment)
        assertEquals(WinRtTypeLayoutKind.Sequential, pointDescriptor.layout.kind)
        assertEquals(listOf("X", "Y"), pointDescriptor.fields.map { it.field.name })
        assertEquals(listOf(0, 4), pointDescriptor.fields.map { it.offset })
        assertEquals("Windows.Foundation.Point", pointDescriptor.mappedType?.abiQualifiedName)

        val enumDescriptor = helpers.valueTypeDescriptor(mode)
        assertEquals(true, enumDescriptor.isValueType)
        assertEquals(true, enumDescriptor.isBlittable)
        assertEquals(WinRtIntegralType.UInt32, enumDescriptor.enumUnderlyingType)
        assertEquals(listOf("None", "Active"), enumDescriptor.enumMembers.map { it.name })
        assertEquals(true, enumDescriptor.isFlagsEnum)

        val holderDescriptor = helpers.valueTypeDescriptor(holder)
        assertEquals(true, holderDescriptor.isValueType)
        assertEquals(false, holderDescriptor.isBlittable)
        assertEquals(true, holderDescriptor.requiresAbiCompanionShape)
        assertEquals(false, helpers.isTypeBlittable(holder))
    }

    @Test
    fun semantic_helpers_collect_generic_abi_inventory_from_cached_type_shapes() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "EventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IReference", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IVector", kind = WinRtTypeKind.Interface, genericParameterCount = 1),
                        WinRtTypeDefinition(namespace = "Windows.Foundation.Collections", name = "IMap", kind = WinRtTypeKind.Interface, genericParameterCount = 2),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("Reference", "Windows.Foundation.IReference<Int>", rowId = 1),
                            ),
                            isBlittable = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            isExclusiveTo = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetOverrides"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValues",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Sample.Foundation.Point>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("values", "Array<Int>", isInParameter = true),
                                        WinRtParameterDefinition("map", "Windows.Foundation.Collections.IMap<String, Int>"),
                                    ),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Current", "Windows.Foundation.IReference<Int>"),
                            ),
                            events = listOf(
                                WinRtEventDefinition("Changed", "Windows.Foundation.EventHandler<Sample.Foundation.Point>"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val inventory = model.semanticHelpers().genericAbiInventory()

        assertEquals(
            listOf(
                "Int",
                "Sample.Foundation.Point",
            ),
            inventory.genericAbiDelegates.map { it.abiDelegateTypesKey },
        )
        assertEquals(
            listOf(
                "Windows_Foundation_Collections_IMap_String__Int_",
                "Windows_Foundation_Collections_IVector_Sample_Foundation_Point_",
                "Windows_Foundation_EventHandler_Sample_Foundation_Point_",
                "Windows_Foundation_IReference_Int_",
            ),
            inventory.genericTypeInstantiations.map { it.instantiationClassName },
        )
        assertEquals(
            listOf(false, false, false, false),
            inventory.genericTypeInstantiations.map { it.implementsCcwInterface },
        )
        assertEquals(
            true,
            model.semanticHelpers().doesAbiInterfaceImplementCcwInterface(
                WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "IWidgetOverrides",
                    kind = WinRtTypeKind.Interface,
                    isExclusiveTo = true,
                ),
            ),
        )
    }
}
