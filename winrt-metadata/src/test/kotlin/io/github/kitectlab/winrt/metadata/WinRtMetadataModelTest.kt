package io.github.kitectlab.winrt.metadata

import io.github.kitectlab.winrt.runtime.Guid
import java.nio.file.Path
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
                    methodRowId = 10,
                    parameters = listOf(
                        WinRtParameterDefinition("value", "String"),
                    ),
                ),
                WinRtMethodDefinition(
                    name = "UpdateArrays",
                    returnTypeName = "Unit",
                    methodRowId = 11,
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
                    methodRowId = 12,
                ),
                WinRtMethodDefinition(
                    name = "get_Name",
                    returnTypeName = "String",
                    isSpecialName = true,
                    methodRowId = 13,
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
            methods = listOf(
                WinRtMethodDefinition(
                    name = "Sample.Foundation.IWidgetOverrides.get_Mode",
                    returnTypeName = "Int",
                    visibility = WinRtMethodVisibility.Private,
                ),
            ),
            activation = WinRtActivationShape(
                isActivatable = true,
                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                factories = listOf(
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetFactory", WinRtAttributedFactoryKind.Activatable),
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetStatics", WinRtAttributedFactoryKind.Static),
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetFactory", WinRtAttributedFactoryKind.Composable, isVisible = true),
                ),
            ),
        )
        val widgetBase = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "WidgetBase",
            kind = WinRtTypeKind.RuntimeClass,
        )
        val derivedWidget = widget.copy(
            name = "DerivedWidget",
            baseTypeName = "Sample.Foundation.WidgetBase",
            activation = WinRtActivationShape(),
        )
        val composableOnly = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "ComposableOnly",
            kind = WinRtTypeKind.RuntimeClass,
            activation = WinRtActivationShape(
                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                factories = listOf(
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetFactory", WinRtAttributedFactoryKind.Composable, isVisible = true),
                ),
            ),
        )
        val overrides = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidgetOverrides",
            kind = WinRtTypeKind.Interface,
            isExclusiveTo = true,
            customAttributes = listOf(
                WinRtCustomAttributeDefinition(
                    "Windows.Foundation.Metadata.ExclusiveToAttribute",
                    fixedArguments = listOf(WinRtCustomAttributeValue.TypeValue("Sample.Foundation.Widget")),
                ),
            ),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Mode",
                    typeName = "Int",
                    getterMethodName = "get_Mode",
                    setterMethodName = "set_Mode",
                    getterMethodRowId = 20,
                    setterMethodRowId = 21,
                ),
            ),
            methods = listOf(
                WinRtMethodDefinition("get_Mode", "Int", isSpecialName = true, methodRowId = 20),
                WinRtMethodDefinition("set_Mode", "Unit", parameters = listOf(WinRtParameterDefinition("value", "Int")), isSpecialName = true, methodRowId = 21),
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
                    types = listOf(defaultInterface, widgetBase, widget, derivedWidget, composableOnly, overrides, factory, statics, flags),
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
        assertEquals("Sample.Foundation.IWidget", (helpers.getDefaultInterfaceSemantics(widget) as WinRtTypeSemantics.TypeDefinition).type.qualifiedName)
        assertEquals(true, helpers.isPType(WinRtTypeDefinition(namespace = "Sample.Foundation", name = "IBox", kind = WinRtTypeKind.Interface, genericParameterCount = 1)))
        assertEquals("Sample.Foundation.Widget", helpers.getExclusiveToType(overrides)?.qualifiedName)
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
        assertEquals(listOf(true, false), attributedTypes.map { it.visible })
        assertEquals(listOf("Sample.Foundation.Widget"), helpers.componentActivatableClasses().map { it.className })
        val (defaultFastAbi, exclusiveFastAbi) = helpers.getDefaultAndExclusiveInterfaces(widget)
        assertEquals("Sample.Foundation.IWidget", defaultFastAbi?.interfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetOverrides"), exclusiveFastAbi.map { it.interfaceName })
        val fastAbiClass = requireNotNull(helpers.getFastAbiClassForClass(widget))
        assertEquals("Sample.Foundation.IWidget", fastAbiClass.defaultInterface?.interfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetOverrides"), fastAbiClass.otherInterfaces.map { it.interfaceName })
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetOverrides"),
            fastAbiClass.interfaceSlots.map { it.interfaceName },
        )
        assertEquals(listOf(6, 10), fastAbiClass.interfaceSlots.map { it.vtableStartIndex })
        assertEquals(listOf(4, 2), fastAbiClass.interfaceSlots.map { it.methodCount })
        assertEquals(listOf(0, 0), fastAbiClass.interfaceSlots.map { it.hierarchyOffsetAfterDefault })
        assertEquals(listOf(10, 12), fastAbiClass.interfaceSlots.map { it.nextVtableStartIndex })
        assertEquals(listOf("Name", "Mode"), fastAbiClass.propertySlots.map { it.propertyName })
        assertEquals(listOf(6, 10), fastAbiClass.propertySlots.map { it.vtableStartIndex })
        assertEquals(listOf(9, 10), fastAbiClass.propertySlots.map { it.getterVtableIndex })
        assertEquals(listOf(null, 11), fastAbiClass.propertySlots.map { it.setterVtableIndex })
        val derivedFastAbiClass = requireNotNull(helpers.getFastAbiClassForClass(derivedWidget))
        assertEquals(listOf(6, 11), derivedFastAbiClass.interfaceSlots.map { it.vtableStartIndex })
        assertEquals(listOf(1, 0), derivedFastAbiClass.interfaceSlots.map { it.hierarchyOffsetAfterDefault })
        assertEquals(listOf(11, 13), derivedFastAbiClass.interfaceSlots.map { it.nextVtableStartIndex })
        assertEquals(true, fastAbiClass.containsGetter("Name"))
        assertEquals(true, fastAbiClass.containsSetter("Mode"))
        assertEquals(false, fastAbiClass.containsSetter("Name"))
        assertEquals(true, fastAbiClass.containsOtherInterface("Sample.Foundation.IWidgetOverrides"))
        assertEquals(true, helpers.getFastAbiClassForInterface(defaultInterface) != null)
        assertEquals(listOf("Invoke_0", "UpdateArrays_1", "remove_Changed_2", "get_Name_3"), helpers.methodVtableDescriptors(defaultInterface).map { it.vmethodName })
        assertEquals(true, helpers.isImplementedAsPrivateMethod(widget, overrides, overrides.methods.first()).isImplementedAsPrivateMethod)
        assertEquals(true, helpers.isImplementedAsPrivateMappedInterface(widget, overrides).isImplementedAsPrivateMappedInterface)
        val queryInterface = helpers.customQueryInterfaceDescriptor(widget)
        assertEquals(listOf("Sample.Foundation.IWidget"), queryInterface.overridableInterfaceNames)
        assertEquals("protected", queryInterface.visibility)
        assertEquals("virtual", queryInterface.overridableModifier)
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
        val serviceProvider = requireNotNull(helpers.getMappedType("Microsoft.UI.Xaml", "IXamlServiceProvider"))
        assertEquals("System.IServiceProvider", serviceProvider.mappedQualifiedName)
        assertEquals(true, serviceProvider.isXamlAlias)
        assertEquals(null, helpers.getMappedType("Windows.Foundation", "Point"))
        assertEquals(null, helpers.getMappedType("Windows.Foundation.Numerics", "Vector3"))
        assertEquals(null, helpers.getMappedType("Windows.UI", "Color"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml", "CornerRadius"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml", "Duration"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml", "GridLength"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml", "Thickness"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml.Controls.Primitives", "GeneratorPosition"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml.Media", "Matrix"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml.Media.Animation", "KeyTime"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml.Media.Animation", "RepeatBehavior"))
        assertEquals(null, helpers.getMappedType("Microsoft.UI.Xaml.Media.Media3D", "Matrix3D"))
        assertEquals(null, helpers.getMappedType("Windows.UI.Xaml", "GridLength"))
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
        assertEquals(null, pointDescriptor.mappedType)

        val enumDescriptor = helpers.valueTypeDescriptor(mode)
        assertEquals(true, enumDescriptor.isValueType)
        assertEquals(true, enumDescriptor.isBlittable)
        assertEquals(WinRtIntegralType.UInt32, enumDescriptor.enumUnderlyingType)
        assertEquals(listOf("None", "Active"), enumDescriptor.enumMembers.map { it.name })
        assertEquals(true, enumDescriptor.isFlagsEnum)

        val holderDescriptor = helpers.valueTypeDescriptor(holder)
        assertEquals(false, holderDescriptor.isValueType)
        assertEquals(false, holderDescriptor.isBlittable)
        assertEquals(false, holderDescriptor.requiresAbiCompanionShape)
        assertEquals(false, helpers.isTypeBlittable(holder))
        assertEquals(false, helpers.isValueType(WinRtTypeRef.fromDisplayName("String"), "Sample.Foundation"))
        assertEquals(false, helpers.isTypeBlittable(WinRtTypeRef.fromDisplayName("Boolean"), "Sample.Foundation"))
        assertEquals(false, helpers.isTypeBlittable(WinRtTypeRef.fromDisplayName("Sample.Foundation.Mode"), "Sample.Foundation", forArray = true))
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
                            name = "IGenericBase",
                            kind = WinRtTypeKind.Interface,
                            genericParameterCount = 1,
                        ),
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
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IGenericBase<Int>"),
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
                "append",
                "append",
                "get_Current",
                "get_Current",
                "get_Value",
                "get_at",
                "get_at",
                "index_of",
                "index_of",
                "insert",
                "invoke",
                "lookup",
                "set_at",
                "set_at",
            ),
            inventory.genericAbiDelegates.map { it.operationName }.sorted(),
        )
        assertEquals(
            listOf(
                "Sample_Foundation_IGenericBase_Int_",
                "Windows_Foundation_Collections_IMap_String__Int_",
                "Windows_Foundation_Collections_IVector_Sample_Foundation_Point_",
                "Windows_Foundation_EventHandler_Sample_Foundation_Point_",
                "Windows_Foundation_IReference_Int_",
            ),
            inventory.genericTypeInstantiations.map { it.instantiationClassName },
        )
        assertEquals(
            listOf(false, false, false, false, false),
            inventory.genericTypeInstantiations.map { it.implementsCcwInterface },
        )
        assertEquals(listOf("Sample.Foundation.IWidget"), inventory.derivedGenericInterfaces)
        assertEquals(true, model.semanticHelpers().hasDerivedGenericInterface(model.namespaces[2].types.first { it.name == "IWidget" }))
        assertEquals(
            "Windows.Foundation.IReference<Int>",
            model.semanticHelpers().convertGenericTypeInstanceToConcreteType(
                WinRtTypeRef.fromDisplayName("Windows.Foundation.IReference<T0>"),
                listOf(WinRtTypeRef.fromDisplayName("Int")),
            ).typeName,
        )
        assertEquals(
            false,
            model.semanticHelpers().doesAbiInterfaceImplementCcwInterface(
                WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "IWidgetOverrides",
                    kind = WinRtTypeKind.Interface,
                    isExclusiveTo = true,
                ),
            ),
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
                WinRtMetadataProjectionContext(sources = emptyList(), include = setOf("Sample.Foundation"), component = true),
            ),
        )
        val worklist = model.semanticHelpers().genericInstantiationWorklist()
        assertEquals(inventory.genericTypeInstantiations.map { it.instantiationClassName }, worklist.pending.map { it.instantiationClassName })
        assertEquals(
            worklist.pending.drop(1).map { it.instantiationClassName },
            worklist.markWritten(worklist.pending.first().instantiationClassName).pending.map { it.instantiationClassName },
        )
    }

    @Test
    fun semantic_helpers_expose_raw_table_boundaries_and_cswinrt_audit_entries() {
        val helpers = WinRtMetadataModel(emptyList()).semanticHelpers()
        val boundaries = helpers.auxiliaryTableSemanticBoundaries(
            WinRtMetadataAuxiliaryTableInventory(
                files = listOf(
                    WinRtMetadataFileAuxiliaryTableInventory(
                        file = Path.of("sample.winmd"),
                        tables = listOf(
                            WinRtMetadataAuxiliaryTableDescriptor(0x0D, "FieldMarshal", rowCount = 1, rowSize = 4, modeled = false),
                            WinRtMetadataAuxiliaryTableDescriptor(0x19, "MethodImpl", rowCount = 2, rowSize = 6, modeled = true),
                            WinRtMetadataAuxiliaryTableDescriptor(0x28, "ManifestResource", rowCount = 1, rowSize = 12, modeled = true),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("FieldMarshal", "ManifestResource", "MethodImpl"), boundaries.map { it.tableName })
        assertEquals(listOf(true, false, false), boundaries.map { it.projectionAffecting })
        assertEquals(listOf(false, true, true), boundaries.map { it.modeled })
        assertEquals(
            listOf(
                "get_exclusive_to_type",
                "is_ptype",
                "get_default_iface_as_type_sem",
                "does_abi_interface_implement_ccw_interface",
                "componentActivatableClasses pre-scan",
                "is_implemented_as_private_method",
                "is_implemented_as_private_mapped_interface",
                "write_custom_query_interface_impl",
                "generic_type_instances fixed point",
                "auxiliary table semantic boundary",
                "helper output inventory",
                "WinRTAbiDelegateInitializer conditions",
                "WinRTGenericTypeInstantiations/base strings conditions",
                "is_manually_generated_iface",
                "projection context flags",
                "write_class_members property merge",
                "object/class equals/hashcode helpers",
                "generic ABI delegate operation entries",
                "write_generic_type_instantiation descriptor",
                "type-name writer context",
                "event helper subclass descriptors",
                "platform guard/member platform descriptors",
                "projected/ABI signature writer descriptors",
                "event invoke/event-source descriptors",
                "ABI marshaler plan descriptors",
                "static/factory/composable surface descriptors",
                "custom mapped member output descriptors",
                "property-interface/member signature descriptors",
                "GUID/IID signature writer descriptors",
                "vtable delegate/function-pointer descriptors",
                "type declaration writer taxonomy",
                "object-reference/inheritance surface descriptors",
                "managed ABI invoke descriptors",
                "generic ABI class initialization descriptors",
                "required-interface ABI augmentation descriptors",
                "module activation/authoring helper descriptors",
                "metadata/generator/runtime/plugin/authoring classification",
            ),
            helpers.cswinrtMetadataParityAudit().map { it.cswinrtEntryPoint },
        )
        assertEquals(emptyList<WinRtMetadataParityAuditEntry>(), helpers.cswinrtMetadataParityAudit().filterNot { it.closed })
    }

    @Test
    fun projection_inventory_mirrors_cswinrt_namespace_traversal_inputs() {
        val iWidget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            events = listOf(WinRtEventDefinition("Changed", "Sample.Foundation.WidgetHandler")),
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            baseTypeName = "Sample.Foundation.BaseWidget",
            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget")),
            customAttributes = listOf(
                WinRtCustomAttributeDefinition("Windows.Foundation.Metadata.ExperimentalAttribute"),
            ),
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "BaseWidget", kind = WinRtTypeKind.RuntimeClass),
                        iWidget,
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetHandler", kind = WinRtTypeKind.Delegate),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetMode", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetContract", kind = WinRtTypeKind.Struct, isApiContract = true),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "WidgetAttribute", kind = WinRtTypeKind.RuntimeClass, isAttributeType = true),
                        widget,
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Hidden", kind = WinRtTypeKind.Interface),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml.Interop", name = "IBindableVector", kind = WinRtTypeKind.Interface),
                    ),
                ),
            ),
        )
        val context = WinRtMetadataProjectionContext(
            sources = emptyList(),
            include = setOf("Sample.Foundation", "Microsoft.UI.Xaml.Interop"),
            exclude = setOf("Sample.Foundation.Hidden"),
            additionExclude = setOf("Microsoft.UI.Xaml"),
            component = true,
        )

        val inventory = model.projectionInventory(context)
        val sample = inventory.namespaces.first { it.namespace == "Sample.Foundation" }
        val xaml = inventory.namespaces.first { it.namespace == "Microsoft.UI.Xaml.Interop" }

        assertEquals(listOf("BaseWidget", "Widget", "WidgetAttribute"), sample.classes.map { it.name })
        assertEquals(listOf("Hidden", "IWidget"), sample.interfaces.map { it.name })
        assertEquals(listOf("WidgetMode"), sample.enums.map { it.name })
        assertEquals(listOf("WidgetContract"), sample.structs.map { it.name })
        assertEquals(listOf("WidgetHandler"), sample.delegates.map { it.name })
        assertEquals(listOf("BaseWidget", "IWidget", "Widget", "WidgetAttribute", "WidgetContract", "WidgetHandler", "WidgetMode"), sample.projectedTypes.map { it.type.name })
        assertEquals(
            listOf(WinRtSkippedTypeReason.Filtered, WinRtSkippedTypeReason.Attribute, WinRtSkippedTypeReason.ApiContract),
            sample.skippedTypes.map { it.reason },
        )
        assertEquals(true, sample.additionsIncluded)
        assertEquals(true, sample.requiresAbi)
        assertEquals(listOf("Microsoft.UI.Xaml.Interop.IBindableVector"), xaml.skippedTypes.map { it.type.qualifiedName })
        assertEquals(listOf(WinRtSkippedTypeReason.MappedType), xaml.skippedTypes.map { it.reason })
        assertEquals(false, xaml.additionsIncluded)
        assertEquals(listOf(WinRtBaseTypeMapping("Sample.Foundation.Widget", "Sample.Foundation.BaseWidget")), inventory.baseTypeMappings)
        assertEquals(listOf("Sample.Foundation.WidgetHandler"), inventory.eventSourceMappings.map { it.eventTypeName })
        assertEquals(listOf("_EventSource_Sample_Foundation_WidgetHandler"), inventory.eventSourceMappings.map { it.sourceClassName })
        assertEquals(
            listOf(
                "Sample.Foundation.BaseWidget",
                "Sample.Foundation.IWidget",
                "Sample.Foundation.Widget",
                "Sample.Foundation.WidgetHandler",
                "Sample.Foundation.WidgetMode",
            ),
            inventory.authoredMetadataTypeMappings.map { it.projectedTypeName },
        )
        assertEquals(
            listOf(
                "ABI.Sample.Foundation.BaseWidget",
                "ABI.Sample.Foundation.IWidget",
                "ABI.Sample.Foundation.Widget",
                "ABI.Sample.Foundation.WidgetHandler",
                "ABI.Sample.Foundation.WidgetMode",
            ),
            inventory.authoredMetadataTypeMappings.map { it.metadataTypeName },
        )
        assertEquals(
            listOf("Windows.Foundation.Metadata.Experimental"),
            sample.projectedTypes.single { it.type.name == "Widget" }.projectedAttributes.map { it.projectedTypeName },
        )
        assertEquals(true, inventory.projectionFileWritten)
        assertEquals(
            listOf(
                "WinRTEventHelpers.cs",
                "WinRTBaseTypeMappingHelper.cs",
                "AuthoringMetadataTypeMappingHelper.cs",
            ),
            inventory.helperOutputs.requiredHelperFileNames,
        )
        assertEquals(true, inventory.helperOutputs.baseStringHelpersRequired)
        assertEquals(false, inventory.helperOutputs.comInteropHelpersRequired)
        assertEquals(false, inventory.helperOutputs.abiDelegateInitializerRequired)
        assertEquals(false, inventory.helperOutputs.genericTypeInstantiationsHelperRequired)
    }

    @Test
    fun projection_surface_filter_includes_transitive_signature_and_activation_dependencies() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Window",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Microsoft.UI.Xaml.DependencyObject",
                            defaultInterfaceName = "Microsoft.UI.Xaml.IWindow",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.IWindow"),
                            ),
                            activation = WinRtActivationShape(
                                activatableFactoryInterfaceName = "Microsoft.UI.Xaml.IWindowFactory",
                                staticInterfaceNames = listOf("Microsoft.UI.Xaml.IWindowStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetIcon",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "Windows.Foundation.Uri"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml", name = "DependencyObject", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IWindow",
                            kind = WinRtTypeKind.Interface,
                            properties = listOf(WinRtPropertyDefinition("Content", "Windows.Foundation.IInspectable")),
                            events = listOf(
                                WinRtEventDefinition(
                                    "Closed",
                                    "Windows.Foundation.TypedEventHandler<Microsoft.UI.Xaml.Window,Windows.Foundation.IInspectable>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml", name = "IWindowFactory", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Xaml", name = "IWindowStatics", kind = WinRtTypeKind.Interface),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "IInspectable", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "TypedEventHandler", kind = WinRtTypeKind.Delegate, genericParameterCount = 2),
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "Uri", kind = WinRtTypeKind.RuntimeClass),
                    ),
                ),
            ),
        )

        val filtered = model.filterProjectionSurface(namespaces = setOf("Microsoft"))

        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.DependencyObject",
                "Microsoft.UI.Xaml.IWindow",
                "Microsoft.UI.Xaml.IWindowFactory",
                "Microsoft.UI.Xaml.IWindowStatics",
                "Microsoft.UI.Xaml.Window",
                "Windows.Foundation.IInspectable",
                "Windows.Foundation.TypedEventHandler",
                "Windows.Foundation.Uri",
            ),
            filtered.namespaces.flatMap { namespace -> namespace.types.map(WinRtTypeDefinition::qualifiedName) },
        )
    }

    @Test
    fun projection_surface_filter_keeps_explicit_windows_include_inside_excluded_namespace() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Windows.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.UI.Xaml", name = "Hidden", kind = WinRtTypeKind.RuntimeClass),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.UI.Xaml.Interop", name = "Type", kind = WinRtTypeKind.Struct),
                    ),
                ),
            ),
        )

        val filtered = model.filterProjectionSurface(
            namespaces = setOf("Windows.UI.Xaml.Interop"),
            excludedNamespaces = setOf("Windows"),
        )

        assertEquals(
            listOf("Windows.UI.Xaml.Interop.Type"),
            filtered.namespaces.flatMap { namespace -> namespace.types.map(WinRtTypeDefinition::qualifiedName) },
        )
    }

    @Test
    fun projection_surface_filter_keeps_referenced_dependencies_inside_excluded_namespaces() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Window",
                            kind = WinRtTypeKind.RuntimeClass,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetPresenter",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition("presenter", "Microsoft.UI.Composition.Compositor"),
                                        WinRtParameterDefinition("input", "Windows.UI.Input.PointerPoint"),
                                    ),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Scaling", "Windows.UI.ViewManagement.ViewManagementViewScalingContract"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Composition",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Microsoft.UI.Composition", name = "Compositor", kind = WinRtTypeKind.RuntimeClass),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI.Input",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.UI.Input", name = "PointerPoint", kind = WinRtTypeKind.RuntimeClass),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI.ViewManagement",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.UI.ViewManagement",
                            name = "ViewManagementViewScalingContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                        ),
                    ),
                ),
            ),
        )

        val filtered = model.filterProjectionSurface(
            namespaces = setOf("Microsoft"),
            excludedNamespaces = setOf("Windows"),
        )

        assertEquals(
            listOf(
                "Microsoft.UI.Composition.Compositor",
                "Microsoft.UI.Xaml.Window",
                "Windows.UI.Input.PointerPoint",
                "Windows.UI.ViewManagement.ViewManagementViewScalingContract",
            ),
            filtered.namespaces.flatMap { namespace -> namespace.types.map(WinRtTypeDefinition::qualifiedName) },
        )
    }

    @Test
    fun projection_inventory_tracks_cswinrt_namespace_additions_for_generated_namespaces() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "AsyncStatus", kind = WinRtTypeKind.Enum),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media.Animation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Animation",
                            name = "Timeline",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.ApplicationModel.DataTransfer",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "DataTransferManager",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
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

        val inventory = model.projectionInventory(
            WinRtMetadataProjectionContext(
                sources = emptyList(),
                include = setOf("Windows.Foundation", "Microsoft.UI.Xaml", "Windows.ApplicationModel.DataTransfer"),
                additionExclude = setOf("Microsoft.UI.Xaml.Media.Animation"),
            ),
        )

        assertEquals(
            listOf("Windows.ApplicationModel.DataTransfer", "Windows.Foundation"),
            inventory.namespaceAdditions.map { it.namespace },
        )
        assertEquals(WinRtNamespaceAdditionKind.ComInteropAdapter, inventory.namespaceAdditions.first().kind)
        assertEquals(true, inventory.helperOutputs.namespaceAdditionsRequired)
        assertTrue("WinRTNamespaceAdditions.kt" in inventory.helperOutputs.requiredHelperFileNames)
    }

    @Test
    fun projection_helper_outputs_follow_cswinrt_target_and_filter_conditions() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Foundation", name = "AsyncStatus", kind = WinRtTypeKind.Enum),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValues",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Sample.Foundation.Point>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val netstandardInventory = model.projectionInventory(
            WinRtMetadataProjectionContext(
                sources = emptyList(),
                include = setOf("Windows", "Sample.Foundation"),
                target = WinRtMetadataTarget.NetStandard20,
            ),
        )
        val net8Inventory = model.projectionInventory(
            WinRtMetadataProjectionContext(
                sources = emptyList(),
                include = setOf("Windows", "Sample.Foundation"),
                target = WinRtMetadataTarget.Net8,
            ),
        )

        assertEquals(true, netstandardInventory.helperOutputs.abiDelegateInitializerRequired)
        assertEquals(true, netstandardInventory.helperOutputs.abiDelegateAsyncStatusRequired)
        assertEquals(false, netstandardInventory.helperOutputs.genericTypeInstantiationsHelperRequired)
        assertEquals(true, netstandardInventory.helperOutputs.comInteropHelpersRequired)
        assertEquals(true, "WinRTAbiDelegateInitializer.cs" in netstandardInventory.helperOutputs.requiredHelperFileNames)
        assertEquals(false, net8Inventory.helperOutputs.abiDelegateInitializerRequired)
        assertEquals(true, net8Inventory.helperOutputs.genericTypeInstantiationsHelperRequired)
        assertEquals(true, "WinRTGenericTypeInstantiations.cs" in net8Inventory.helperOutputs.requiredHelperFileNames)
    }

    @Test
    fun semantic_helpers_expose_context_manual_interface_and_class_member_merge_descriptors() {
        val getName = WinRtMethodDefinition("get_Name", "String", methodRowId = 10)
        val setName = WinRtMethodDefinition("put_Name", "Void", parameters = listOf(WinRtParameterDefinition("value", "String")), methodRowId = 11)
        val widgetInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            availability = WinRtAvailabilityMetadata(
                contractVersion = WinRtContractVersionMetadata(
                    contractName = "Windows.Foundation.UniversalApiContract",
                    version = 1,
                    platformVersion = "10.0.1.0",
                ),
            ),
            methods = listOf(getName),
            properties = listOf(WinRtPropertyDefinition("Name", "String", getterMethodName = "get_Name")),
        )
        val widgetMutableInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidgetMutable",
            kind = WinRtTypeKind.Interface,
            methods = listOf(setName),
            properties = listOf(WinRtPropertyDefinition("Name", "String", setterMethodName = "put_Name")),
        )
        val exclusiveInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidgetOverrides",
            kind = WinRtTypeKind.Interface,
            isExclusiveTo = true,
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetMutable", isOverridable = true),
                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetOverrides"),
            ),
        )
        val bindableVector = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Interop",
            name = "IBindableVector",
            kind = WinRtTypeKind.Interface,
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace("Sample.Foundation", listOf(widgetInterface, widgetMutableInterface, exclusiveInterface, widget)),
                WinRtNamespace("Microsoft.UI.Xaml.Interop", listOf(bindableVector)),
            ),
        )
        val helpers = model.semanticHelpers()
        val context = WinRtMetadataProjectionContext(
            sources = emptyList(),
            target = WinRtMetadataTarget.Net8,
            internal = true,
            embedded = true,
            publicEnums = true,
            publicExclusiveTo = false,
            idicExclusiveTo = false,
            partialFactory = true,
        )

        assertEquals("internal", helpers.projectionContextSemantics(context).internalAccessibility)
        assertEquals("public", helpers.projectionContextSemantics(context).enumAccessibility)
        assertEquals("GetActivationFactoryPartial(runtimeClassId)", helpers.projectionContextSemantics(context).partialFactoryFallbackExpression)
        assertEquals(true, helpers.isManuallyGeneratedInterface(bindableVector).manuallyGenerated)
        assertEquals(false, helpers.typeProjectionContextDescriptor(exclusiveInterface, context).writesVtablePointer)
        assertEquals(false, helpers.typeProjectionContextDescriptor(exclusiveInterface, context).supportsDynamicInterfaceCastable)

        val merge = helpers.classMemberMergeDescriptor(widget, context)
        assertEquals(
            listOf("Sample.Foundation.IWidget", "Sample.Foundation.IWidgetMutable", "Sample.Foundation.IWidgetOverrides"),
            merge.interfaceDescriptors.map { it.interfaceTypeName },
        )
        val name = merge.mergedProperties.single { it.propertyName == "Name" }
        assertEquals("String", name.propertyTypeName)
        assertEquals("_default", name.getterTarget)
        assertEquals("AsInternal(new InterfaceTag<Sample.Foundation.IWidgetMutable>())", name.setterTarget)
        assertEquals(true, name.isOverridable)
        assertEquals(true, name.isPublic)
        assertEquals(false, name.isPrivate)
        assertEquals("Sample.Foundation.IWidget", name.getterStaticCallTarget)
        assertEquals("Sample.Foundation.IWidgetMutable", name.setterStaticCallTarget)
    }

    @Test
    fun semantic_helpers_expose_cswinrt_writer_exactness_descriptors() {
        val point = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Point",
            kind = WinRtTypeKind.Struct,
            fields = listOf(WinRtFieldDefinition("X", "Single")),
        )
        val eventHandler = WinRtTypeDefinition(
            namespace = "Windows.Foundation",
            name = "EventHandler",
            kind = WinRtTypeKind.Delegate,
            iid = Guid("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            genericParameterCount = 1,
            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
            methods = listOf(
                WinRtMethodDefinition(
                    name = "Invoke",
                    returnTypeName = "Void",
                    parameters = listOf(WinRtParameterDefinition("sender", "Any"), WinRtParameterDefinition("args", "T0")),
                ),
            ),
        )
        val vector = WinRtTypeDefinition(
            namespace = "Windows.Foundation.Collections",
            name = "IVector",
            kind = WinRtTypeKind.Interface,
            iid = Guid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            genericParameterCount = 1,
            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
            methods = listOf(
                WinRtMethodDefinition("GetAt", "T0", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                WinRtMethodDefinition("add_Changed", "Void", isSpecialName = true),
            ),
            properties = listOf(WinRtPropertyDefinition("Current", "T0", getterMethodName = "GetAt")),
            events = listOf(WinRtEventDefinition("Changed", "Windows.Foundation.EventHandler<T0>")),
        )
        val iWidget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            iid = Guid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            methods = listOf(
                WinRtMethodDefinition(
                    name = "GetValue",
                    returnTypeName = "String",
                    parameters = listOf(WinRtParameterDefinition("class", "Array<Int>", isInParameter = true)),
                    methodRowId = 1,
                ),
            ),
            properties = listOf(WinRtPropertyDefinition("Value", "String", getterMethodName = "GetValue")),
            events = listOf(WinRtEventDefinition("RawChanged", "Windows.Foundation.EventHandler", addMethodRowId = 3, removeMethodRowId = 4)),
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            defaultInterfaceName = "Sample.Foundation.IWidget",
            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true)),
            baseTypeName = "Sample.Foundation.BaseWidget",
            activation = WinRtActivationShape(
                factories = listOf(
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetFactory", WinRtAttributedFactoryKind.Activatable),
                    WinRtAttributedFactoryShape("Sample.Foundation.IWidgetStatics", WinRtAttributedFactoryKind.Static),
                ),
            ),
            gcPressureAmount = 64,
            availability = WinRtAvailabilityMetadata(
                contractVersion = WinRtContractVersionMetadata(
                    contractName = "Windows.Foundation.UniversalApiContract",
                    version = 1,
                    platformVersion = "10.0.1.0",
                ),
            ),
            methods = listOf(
                WinRtMethodDefinition("Equals", "Boolean", parameters = listOf(WinRtParameterDefinition("obj", "System.Object"))),
                WinRtMethodDefinition("Equals", "Boolean", parameters = listOf(WinRtParameterDefinition("obj", "Sample.Foundation.Widget"))),
                WinRtMethodDefinition("GetHashCode", "Int"),
            ),
            events = listOf(WinRtEventDefinition("Changed", "Windows.Foundation.EventHandler<Sample.Foundation.Point>")),
        )
        val exclusive = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidgetOverrides`1",
            kind = WinRtTypeKind.Interface,
            isExclusiveTo = true,
            genericParameterCount = 1,
            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
        )
        val bindableVector = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Interop",
            name = "IBindableVector",
            kind = WinRtTypeKind.Interface,
            methods = listOf(WinRtMethodDefinition("GetAt", "Any")),
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace("Windows.Foundation", listOf(eventHandler)),
                WinRtNamespace("Windows.Foundation.Collections", listOf(vector)),
                WinRtNamespace("Microsoft.UI.Xaml.Interop", listOf(bindableVector)),
                WinRtNamespace(
                    "Sample.Foundation",
                    listOf(
                        point,
                        iWidget,
                        widget,
                        exclusive,
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IUsesVector",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    "Values",
                                    "Windows.Foundation.Collections.IVector<Sample.Foundation.Point>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val helpers = model.semanticHelpers()

        val objectMethods = helpers.classObjectMethodDescriptor(widget)
        assertEquals(true, objectMethods.hasObjectEqualsMethod)
        assertEquals(true, objectMethods.hasClassEqualsMethod)
        assertEquals(true, objectMethods.hasObjectHashCodeMethod)
        assertEquals(true, objectMethods.objectEquals?.returnTypeMatches)

        val inventory = helpers.collectGenericAbiInventory(
            WinRtTypeDefinition(
                namespace = "Sample.Foundation",
                name = "IUsesVector",
                kind = WinRtTypeKind.Interface,
                methods = listOf(
                    WinRtMethodDefinition(
                        "Values",
                        "Windows.Foundation.Collections.IVector<Sample.Foundation.Point>",
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("append", "get_Current", "get_at", "index_of", "set_at"),
            inventory.genericAbiDelegates.map { it.operationName }.sorted(),
        )
        assertEquals(
            listOf("void*", "uint", "out Sample.Foundation.Point", "int"),
            inventory.genericAbiDelegates.single { it.operationName == "get_at" }.abiParameterTypeNames,
        )
        assertEquals(
            "Windows_Foundation_Collections_IVector_Sample_Foundation_Point_",
            inventory.genericTypeInstantiations.single().instantiationClassName,
        )

        val writerDescriptor = helpers.genericInstantiationWriterDescriptor(inventory.genericTypeInstantiations.single())
        assertEquals(false, writerDescriptor.isDelegateInstantiation)
        assertEquals(listOf("GetAt"), writerDescriptor.rcwFunctionNames)
        assertEquals(emptyList<String>(), writerDescriptor.vtableFunctionNames)
        assertEquals(listOf("GetAt"), writerDescriptor.propertyAccessorFunctionNames)
        assertEquals(listOf("Windows.Foundation.EventHandler<Sample.Foundation.Point>"), writerDescriptor.initializationDependencies)
        val fixedPointInstantiations = helpers.genericInstantiationWorklist().pending.map { it.instantiationClassName }
        assertTrue("Windows_Foundation_Collections_IVector_Sample_Foundation_Point_" in fixedPointInstantiations)
        assertTrue("Windows_Foundation_EventHandler_Sample_Foundation_Point_" in fixedPointInstantiations)

        val typeName = helpers.typeNameDescriptor(
            exclusive,
            WinRtProjectedNameKind.Projected,
            WinRtTypeNameWriterContext(currentNamespace = "Other.Namespace", inAbiNamespace = true, component = true),
            forceNamespace = false,
        )
        assertEquals(true, typeName.rewritesExclusiveProjectedToCcw)
        assertEquals("IWidgetOverrides", typeName.genericArityStrippedName)
        assertEquals("ABI.Impl.Sample.Foundation.IWidgetOverridesCcw<T>", typeName.renderedName)

        val eventHelper = helpers.eventHelperSubclassDescriptors(widget).single()
        assertEquals("Windows.Foundation.EventHandler<Sample.Foundation.Point>", eventHelper.eventTypeName)
        assertEquals("EventHandlerEventSource", eventHelper.sourceClassName)
        assertEquals(listOf("Sample.Foundation.Point"), eventHelper.genericArgumentTypeNames)
        assertEquals(true, eventHelper.usesSharedEventHandlerSource)

        val platform = helpers.platformGuardDescriptor(widget.qualifiedName, widget.availability)
        assertEquals(true, platform.checkPlatform)
        assertEquals("[SupportedOSPlatform(\"windows10.0.1.0\")]", platform.platformAttribute)
        assertEquals(false, platform.suppressesDuplicatePlatform)
        assertEquals(true, helpers.platformGuardDescriptor(widget.qualifiedName, widget.availability, inheritedPlatform = "10.0.1.0").suppressesDuplicatePlatform)

        val signature = helpers.signatureWriterDescriptor(iWidget.methods.single())
        assertEquals("`class`", signature.parameters.single().escapedName)
        assertEquals(true, signature.parameters.single().expandsArrayLength)
        assertEquals("Array<Int>", signature.parameters.single().projectionTypeName)

        val eventInvoke = helpers.eventInvokeDescriptor(iWidget, iWidget.events.single())
        assertEquals("Invoke", eventInvoke.invokeMethodName)
        assertEquals(3, eventInvoke.sourceTableAddIndex)
        assertEquals(false, eventInvoke.isStatic)

        val marshaler = helpers.abiMarshalerPlanDescriptor(iWidget.methods.single())
        assertEquals("GetValue", marshaler.methodName)
        assertEquals(true, marshaler.requiresDispose)
        assertEquals(listOf("__return_value__", "`class`"), marshaler.marshalers.map { it.name })

        val factory = helpers.factorySurfaceDescriptor(widget)
        assertEquals("Sample.Foundation.IWidget", factory.defaultInterfaceName)
        assertEquals(listOf("Sample.Foundation.IWidgetFactory"), factory.constructorFactories)
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), factory.staticMemberTargets)
        assertEquals(64, factory.gcPressureAmount)

        val mapped = helpers.customMappedMemberOutputDescriptor(bindableVector)
        assertEquals("Microsoft.UI.Xaml.Interop.IBindableVector", mapped?.interfaceTypeName)
        assertEquals("static-abi", mapped?.callMode)
        assertEquals(true, mapped?.emitsExplicitMembers)
        val privateIdicMapped = helpers.customMappedMemberOutputDescriptor(
            bindableVector,
            context = WinRtMetadataProjectionContext(sources = emptyList(), target = WinRtMetadataTarget.NetStandard20),
            isPrivate = true,
        )
        assertEquals("idic", privateIdicMapped?.callMode)
        assertEquals(true, privateIdicMapped?.emitsPrivateMembers)

        val interfaceSignatures = helpers.interfaceMemberSignatureSetDescriptor(iWidget)
        assertEquals(listOf("GetValue"), interfaceSignatures.methodSignatures.map { it.methodName })
        assertEquals(listOf("Value"), interfaceSignatures.propertyNames)
        assertEquals(emptyList<String>(), interfaceSignatures.newPropertyNames)

        val guid = helpers.guidSignatureDescriptor(vector)
        assertEquals("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", guid.guidText)
        assertEquals("{aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa}", guid.signatureFragment)
        assertEquals(16, guid.guidBytes.size)
        assertEquals("delegate({cccccccc-cccc-cccc-cccc-cccccccccccc})", helpers.guidSignatureDescriptor(eventHandler).signatureFragment)
        assertEquals("struct(Sample.Foundation.Point;f4)", helpers.guidSignatureDescriptor(point).signatureFragment)
        assertEquals("rc(Sample.Foundation.Widget;{bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb})", helpers.guidSignatureDescriptor(widget).signatureFragment)

        val vtable = helpers.vtableWriterDescriptor(iWidget)
        assertEquals(listOf("GetValue_0"), vtable.methods.map { it.vmethodName })
        assertEquals(true, vtable.usesFunctionPointers)

        val mixedInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IMixedStatics",
            kind = WinRtTypeKind.Interface,
            methods = listOf(
                WinRtMethodDefinition("Start", "Unit", methodRowId = 101),
            ),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Current",
                    typeName = "Sample.Foundation.Widget",
                    getterMethodName = "get_Current",
                    getterMethodRowId = 100,
                ),
            ),
        )
        val mixedVtable = helpers.vtableWriterDescriptor(mixedInterface)
        assertEquals(listOf("Start_1"), mixedVtable.methods.map { it.vmethodName })
        assertEquals(listOf(7), mixedVtable.methods.map { it.slotIndex })

        val declaration = helpers.typeDeclarationDescriptor(widget)
        assertEquals(true, declaration.writesAbiDeclaration)
        assertEquals(true, declaration.writesWrapperDeclaration)

        val objectReference = helpers.objectReferenceSurfaceDescriptor(widget)
        assertEquals(true, objectReference.hasRcwFactory)
        assertEquals(true, "Sample.Foundation.IWidget" in objectReference.inheritanceTypeNames)

        val invoke = helpers.managedAbiInvokeDescriptor("GetValue", iWidget.methods.single(), "method")
        assertEquals(true, invoke.requiresHelperMethod)
        assertEquals(true, invoke.conversionOperations.any { it.endsWith(":toAbi") })

        val genericAbi = helpers.genericAbiClassInitializationDescriptor(vector)
        assertEquals(true, genericAbi.requiresRcwFallbackInitialization)
        assertEquals(listOf("GetAt_0", "add_Changed_1"), genericAbi.invokeSlotNames)

        val required = helpers.requiredInterfaceAugmentationDescriptor(widget)
        assertEquals(listOf("Sample.Foundation.IWidget"), required.requiredInterfaceNames)

        val module = helpers.moduleActivationAndAuthoringDescriptor(widget)
        assertEquals("WidgetActivationFactory", module.factoryClassName)
        assertEquals("Sample.Foundation.Widget -> Sample.Foundation.BaseWidget", module.baseTypeEntry)
        assertEquals("Sample.Foundation.Widget", module.metadataTypeEntry)
    }

    @Test
    fun object_reference_descriptor_mirrors_cswinrt_class_objref_rules() {
        val bindableVector = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Interop",
            name = "IBindableVector",
            kind = WinRtTypeKind.Interface,
        )
        val defaultInterface = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "IDefault",
            kind = WinRtTypeKind.Interface,
        )
        val genericInterface = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "IGeneric",
            kind = WinRtTypeKind.Interface,
            genericParameterCount = 1,
        )
        val exclusiveInterface = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "IOverrides",
            kind = WinRtTypeKind.Interface,
            isExclusiveTo = true,
            customAttributes = listOf(
                WinRtCustomAttributeDefinition(
                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                    fixedArguments = listOf(WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.Widget")),
                ),
            ),
        )
        val exclusiveDefaultInterface = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "IDefaultExclusive",
            kind = WinRtTypeKind.Interface,
            methods = listOf(
                WinRtMethodDefinition("Start", "Unit", methodRowId = 10),
                WinRtMethodDefinition("Stop", "Unit", methodRowId = 11),
            ),
            isExclusiveTo = true,
            customAttributes = listOf(
                WinRtCustomAttributeDefinition(
                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                    fixedArguments = listOf(WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.FastDefaultExclusiveWidget")),
                ),
            ),
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            isFastAbi = true,
            defaultInterfaceName = "Sample.FastAbi.IDefault",
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IDefault", isDefault = true),
                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IOverrides"),
                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Interop.IBindableVector"),
                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IGeneric<String>"),
            ),
        )
        val fastDefaultExclusiveWidget = WinRtTypeDefinition(
            namespace = "Sample.FastAbi",
            name = "FastDefaultExclusiveWidget",
            kind = WinRtTypeKind.RuntimeClass,
            isFastAbi = true,
            isSealedType = false,
            defaultInterfaceName = "Sample.FastAbi.IDefaultExclusive",
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IDefaultExclusive", isDefault = true),
            ),
        )
        val sealedWidget = widget.copy(
            name = "SealedWidget",
            isFastAbi = false,
            isSealedType = true,
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IDefault", isDefault = true),
            ),
        )
        val helpers = WinRtMetadataModel(
            listOf(
                WinRtNamespace("Microsoft.UI.Xaml.Interop", listOf(bindableVector)),
                WinRtNamespace(
                    "Sample.FastAbi",
                    listOf(
                        defaultInterface,
                        genericInterface,
                        exclusiveInterface,
                        exclusiveDefaultInterface,
                        widget,
                        fastDefaultExclusiveWidget,
                        sealedWidget,
                    ),
                ),
            ),
        ).semanticHelpers()

        val descriptor = helpers.objectReferenceSurfaceDescriptor(widget)
        assertEquals(listOf("Sample_FastAbi_IDefaultCache", "Sample_FastAbi_IGeneric_String_Cache"), descriptor.objectReferenceNames)
        assertEquals(null, descriptor.objectReferencePlans.single { it.interfaceName == "Sample.FastAbi.IDefault" }.skippedReason)
        assertEquals("fast-abi-non-default-exclusive", descriptor.objectReferencePlans.single { it.interfaceName == "Sample.FastAbi.IOverrides" }.skippedReason)
        assertEquals("manual-bindable", descriptor.objectReferencePlans.single { it.interfaceName == "Microsoft.UI.Xaml.Interop.IBindableVector" }.skippedReason)
        assertEquals(true, descriptor.objectReferencePlans.single { it.interfaceName == "Sample.FastAbi.IGeneric<String>" }.requiresGenericInstantiation)

        val defaultExclusiveDescriptor = helpers.objectReferenceSurfaceDescriptor(fastDefaultExclusiveWidget)
        val defaultExclusivePlan = defaultExclusiveDescriptor.objectReferencePlans.single()
        assertEquals(true, defaultExclusivePlan.usesDefaultInterfaceObjRef)
        assertEquals(0, defaultExclusivePlan.defaultInterfaceHierarchyIndex)
        assertEquals(8, defaultExclusivePlan.defaultInterfaceObjRefVtableSlot)
        val sealedDescriptor = helpers.objectReferenceSurfaceDescriptor(sealedWidget)
        assertEquals(true, sealedDescriptor.objectReferencePlans.single().usesInner)
    }

    @Test
    fun object_reference_descriptor_skips_mapped_helper_interfaces() {
        val defaultInterface = WinRtTypeDefinition(
            namespace = "Sample.Xaml",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
        )
        val propertyChanged = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Data",
            name = "INotifyPropertyChanged",
            kind = WinRtTypeKind.Interface,
        )
        val bindableVector = WinRtTypeDefinition(
            namespace = "Windows.UI.Xaml.Interop",
            name = "IBindableVector",
            kind = WinRtTypeKind.Interface,
        )
        val widget = WinRtTypeDefinition(
            namespace = "Sample.Xaml",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
            defaultInterfaceName = "Sample.Xaml.IWidget",
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.Xaml.IWidget", isDefault = true),
                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyPropertyChanged"),
                WinRtInterfaceImplementationDefinition("Windows.UI.Xaml.Interop.IBindableVector"),
            ),
        )
        val helpers = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Xaml",
                    types = listOf(defaultInterface, widget),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Data",
                    types = listOf(propertyChanged),
                ),
                WinRtNamespace(
                    name = "Windows.UI.Xaml.Interop",
                    types = listOf(bindableVector),
                ),
            ),
        ).semanticHelpers()

        val descriptor = helpers.objectReferenceSurfaceDescriptor(widget)

        assertEquals(listOf("Sample_Xaml_IWidgetCache"), descriptor.objectReferenceNames)
        assertEquals(
            listOf("Sample.Xaml.Widget", "Sample.Xaml.IWidget"),
            descriptor.exposedTypeMetadataNames,
        )
        assertEquals(
            "runtime-owned-mapped",
            descriptor.objectReferencePlans.single { it.interfaceName == "Microsoft.UI.Xaml.Data.INotifyPropertyChanged" }.skippedReason,
        )
        assertEquals(
            "runtime-owned-mapped",
            descriptor.objectReferencePlans.single { it.interfaceName == "Windows.UI.Xaml.Interop.IBindableVector" }.skippedReason,
        )
    }

    @Test
    fun required_interface_mapped_helpers_carry_cswinrt_call_mode_and_removal_rules() {
        val iterable = WinRtTypeDefinition(
            namespace = "Windows.Foundation.Collections",
            name = "IIterable",
            kind = WinRtTypeKind.Interface,
            genericParameterCount = 1,
        )
        val vector = WinRtTypeDefinition(
            namespace = "Windows.Foundation.Collections",
            name = "IVector",
            kind = WinRtTypeKind.Interface,
            genericParameterCount = 1,
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<T0>"),
            ),
        )
        val ownerInterface = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IStringVectorOwner",
            kind = WinRtTypeKind.Interface,
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IVector<String>"),
                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo"),
                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyPropertyChanged"),
            ),
        )
        val dataErrorInfo = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Data",
            name = "INotifyDataErrorInfo",
            kind = WinRtTypeKind.Interface,
        )
        val propertyChanged = WinRtTypeDefinition(
            namespace = "Microsoft.UI.Xaml.Data",
            name = "INotifyPropertyChanged",
            kind = WinRtTypeKind.Interface,
        )
        val owner = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "StringVectorOwner",
            kind = WinRtTypeKind.RuntimeClass,
            implementedInterfaces = listOf(
                WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringVectorOwner", isDefault = true),
            ),
        )
        val helpers = WinRtMetadataModel(
            listOf(
                WinRtNamespace("Windows.Foundation.Collections", listOf(iterable, vector)),
                WinRtNamespace("Microsoft.UI.Xaml.Data", listOf(dataErrorInfo, propertyChanged)),
                WinRtNamespace("Sample.Foundation", listOf(ownerInterface, owner)),
            ),
        ).semanticHelpers()

        val descriptor = helpers.requiredInterfaceAugmentationDescriptor(owner)
        val vectorPlan = descriptor.mappedHelperPlans.single { it.memberFamily == "IList" }
        assertEquals("Windows.Foundation.Collections.IVector<String>", vectorPlan.interfaceName)
        assertEquals("System.Collections.Generic.IList`1", vectorPlan.mappedTypeName)
        assertEquals("_vectorToList", vectorPlan.adapterFieldName)
        assertEquals("idic", vectorPlan.callMode)
        assertEquals(false, vectorPlan.emitsMappedTypeHelpers)
        assertEquals(true, vectorPlan.emitsPrivateMembers)
        assertEquals(true, vectorPlan.removesNonGenericEnumerable)
        assertEquals("System.Collections.Generic.IEnumerable<String>", vectorPlan.removesGenericEnumerableName)

        val iterablePlan = descriptor.mappedHelperPlans.single { it.memberFamily == "IEnumerable" }
        assertEquals("Windows.Foundation.Collections.IIterable<String>", iterablePlan.interfaceName)
        assertEquals("_iterableToEnumerable", iterablePlan.adapterFieldName)

        val dataErrorPlan = descriptor.mappedHelperPlans.single { it.memberFamily == "INotifyDataErrorInfo" }
        assertEquals("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", dataErrorPlan.interfaceName)
        assertEquals("System.ComponentModel.INotifyDataErrorInfo", dataErrorPlan.mappedTypeName)
        assertEquals(null, dataErrorPlan.helperWrapperName)
        assertEquals(null, dataErrorPlan.adapterFieldName)

        val propertyChangedPlan = descriptor.mappedHelperPlans.single { it.memberFamily == "INotifyPropertyChanged" }
        assertEquals("Microsoft.UI.Xaml.Data.INotifyPropertyChanged", propertyChangedPlan.interfaceName)
        assertEquals("System.ComponentModel.INotifyPropertyChanged", propertyChangedPlan.mappedTypeName)
        assertEquals(null, propertyChangedPlan.helperWrapperName)
        assertEquals(null, propertyChangedPlan.adapterFieldName)
    }

    @Test
    fun projected_attributes_follow_cswinrt_custom_and_platform_filtering() {
        val type = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "WidgetAttribute",
            availability = WinRtAvailabilityMetadata(
                contractVersion = WinRtContractVersionMetadata(
                    contractName = "Windows.Foundation.UniversalApiContract",
                    version = 0x00030000,
                    platformVersion = "10.0.14393.0",
                ),
            ),
            customAttributes = listOf(
                WinRtCustomAttributeDefinition(
                    typeName = "System.Runtime.InteropServices.GuidAttribute",
                    fixedArguments = listOf(WinRtCustomAttributeValue.StringValue("11111111-1111-1111-1111-111111111111")),
                ),
                WinRtCustomAttributeDefinition(
                    typeName = "Windows.Foundation.Metadata.ContractVersionAttribute",
                    fixedArguments = listOf(
                        WinRtCustomAttributeValue.StringValue("Windows.Foundation.UniversalApiContract"),
                        WinRtCustomAttributeValue.IntegralValue(0x00030000),
                    ),
                ),
                WinRtCustomAttributeDefinition(
                    typeName = "Windows.Foundation.Metadata.AttributeUsageAttribute",
                    fixedArguments = listOf(
                        WinRtCustomAttributeValue.EnumValue("Windows.Foundation.Metadata.AttributeTargets", 4),
                    ),
                ),
                WinRtCustomAttributeDefinition(
                    typeName = "Windows.Foundation.Metadata.AllowMultipleAttribute",
                ),
            ),
        )

        val attributes = type.projectedAttributes()

        assertEquals(
            listOf(
                "System.AttributeUsage",
                "System.Runtime.Versioning.SupportedOSPlatform",
                "Windows.Foundation.Metadata.ContractVersion",
            ),
            attributes.map { it.projectedTypeName },
        )
        assertEquals(
            WinRtCustomAttributeValue.BooleanValue(true),
            attributes.single { it.projectedTypeName == "System.AttributeUsage" }
                .namedArguments.single { it.name == "AllowMultiple" }
                .value,
        )
        assertEquals(
            listOf(WinRtCustomAttributeValue.StringValue("Windows10.0.14393.0")),
            attributes.single { it.projectedTypeName == "System.Runtime.Versioning.SupportedOSPlatform" }.arguments,
        )
        assertEquals(
            listOf("System.AttributeTargets.Event", "AllowMultiple = true"),
            attributes.single { it.projectedTypeName == "System.AttributeUsage" }.renderedArguments,
        )

        val singleUse = type.copy(
            customAttributes = type.customAttributes.filterNot {
                it.typeName == "Windows.Foundation.Metadata.AllowMultipleAttribute"
            },
        ).projectedAttributes().single { it.projectedTypeName == "System.AttributeUsage" }
        assertEquals("AllowMultiple = false", singleUse.renderedArguments.last())
    }

    @Test
    fun metadata_helpers_expose_explicit_implementation_and_generic_writer_scope_inputs() {
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(WinRtMethodDefinition("GetValue", "T0", methodRowId = 1)),
                            genericParameterCount = 1,
                            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Sample.Foundation.IWidget.GetValue",
                                    returnTypeName = "String",
                                    visibility = WinRtMethodVisibility.Private,
                                    methodRowId = 2,
                                ),
                            ),
                            methodImplementations = listOf(
                                WinRtMethodImplementationDefinition(
                                    classTypeName = "Sample.Foundation.Widget",
                                    body = WinRtMethodImplementationMember(
                                        kind = WinRtMethodImplementationMemberKind.MethodDefinition,
                                        rowId = 2,
                                        name = "Sample.Foundation.IWidget.GetValue",
                                        ownerTypeName = "Sample.Foundation.Widget",
                                    ),
                                    declaration = WinRtMethodImplementationMember(
                                        kind = WinRtMethodImplementationMemberKind.MemberReference,
                                        rowId = 3,
                                        name = "GetValue",
                                        ownerTypeName = "Sample.Foundation.IWidget",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).normalized()

        val helpers = model.semanticHelpers()
        val widget = model.namespaces.single().types.single { it.name == "Widget" }
        val explicit = helpers.explicitImplementations(widget).single()
        assertEquals("Sample.Foundation.IWidget", explicit.interfaceTypeName)
        assertEquals("GetValue", explicit.declarationName)
        assertEquals(true, explicit.isPrivateBody)

        val scope = helpers.genericScope(model.namespaces.single().types.single { it.name == "IWidget" })
        assertEquals(listOf("T"), scope.parameters.map { it.projectedName })
        assertEquals(listOf("TAbi"), scope.parameters.map { it.abiName })

        val usage = helpers.genericSignatureUsage(WinRtTypeRef.fromDisplayName("Sample.Foundation.IWidget<T0>"))
        assertEquals(true, usage.containsProjectedGenericParameter)
        assertEquals(true, usage.containsAbiGenericParameter)
    }

    @Test
    fun projection_input_diagnostics_capture_context_resolution_and_kotlin_gap_warnings() {
        val missing = WinRtMetadataProjectionContext(
            sources = listOf(WinRtMetadataSource.path(java.nio.file.Path.of("missing.winmd"))),
        ).validateForProjectionInputs()
        assertEquals(true, missing.hasErrors)
        assertEquals(WinRtMetadataDiagnosticCode.InvalidMetadataSource, missing.errors.single().code)

        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    "Transform",
                                    "M0",
                                    genericParameterCount = 1,
                                    genericParameters = listOf(WinRtGenericParameterDefinition("M0", 0)),
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val report = model.validateForProjection(
            WinRtMetadataValidationOptions(kotlinSpecificGaps = listOf("NuGet resource integration is planned in the Gradle plugin slice.")),
        )
        val codes = report.diagnostics.map(WinRtMetadataDiagnostic::code).toSet()
        assertEquals(false, WinRtMetadataDiagnosticCode.UnsupportedSemanticShape in codes)
        assertEquals(false, WinRtMetadataDiagnosticCode.UnsupportedGenericMethodShape in codes)
        assertEquals(true, WinRtMetadataDiagnosticCode.IntentionalKotlinGap in codes)
        assertEquals(1, report.warnings.count { it.code == WinRtMetadataDiagnosticCode.IntentionalKotlinGap })
        assertEquals(false, report.format().contains("Sample.Foundation.IWidget.Transform metadata row 12"))
    }
}
