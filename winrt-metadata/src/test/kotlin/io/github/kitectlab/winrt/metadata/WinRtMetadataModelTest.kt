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
}
