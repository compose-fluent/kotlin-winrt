package io.github.composefluent.winrt.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class XamlDataTemplateSelectorNullabilityTest {
    @Test
    fun runtime_class_and_versioned_interfaces_share_the_nullable_selector_contract() {
        // CsWinRT code_writers.h: write_class_members/write_class_method project
        // the implemented interface's signature. MarshalInterface<T>.FromAbi
        // (WinRT.Runtime/Marshalers.cs) preserves a null DataTemplate pointer.
        // The Windows SDK puts the one-argument overload on IDataTemplateSelector2.
        for (namespace in listOf("Windows.UI.Xaml.Controls", "Microsoft.UI.Xaml.Controls")) {
            val templateType = namespace.removeSuffix(".Controls") + ".DataTemplate"
            val select = WinRTMethodDefinition(
                name = "SelectTemplate",
                returnTypeName = templateType,
                parameters = listOf(WinRTParameterDefinition("item", "System.Object")),
            )
            val model = WinRTMetadataModel(
                namespaces = listOf(WinRTNamespace(namespace, listOf(
                    WinRTTypeDefinition(
                        namespace, "IDataTemplateSelector2", WinRTTypeKind.Interface,
                        methods = listOf(select),
                    ),
                    WinRTTypeDefinition(
                        namespace, "DataTemplateSelector", WinRTTypeKind.RuntimeClass,
                        implementedInterfaces = listOf(
                            WinRTInterfaceImplementationDefinition("$namespace.IDataTemplateSelector2"),
                        ),
                        methods = listOf(select),
                    ),
                ))),
            ).normalized()
            val methods = model.namespaces.single().types.map { it.methods.single() }
            assertEquals(listOf("$templateType?", "$templateType?"), methods.map { it.returnTypeName })
            // Kotlin nullability must not alter the WinMD/ABI type signature.
            assertEquals(listOf(select.returnType, select.returnType), methods.map { it.returnType })
            assertEquals(model, model.normalized())
        }
    }
}
