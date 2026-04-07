package dev.winrt.winmd.parser

import dev.winrt.winmd.plugin.WinMdField
import dev.winrt.winmd.plugin.WinMdModel
import dev.winrt.winmd.plugin.WinMdNamespace
import dev.winrt.winmd.plugin.WinMdType
import dev.winrt.winmd.plugin.WinMdTypeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WinRtSignatureMapperTest {
    private val model = WinMdModel(
        files = emptyList(),
        namespaces = listOf(
            WinMdNamespace(
                name = "Windows.Foundation.Collections",
                types = listOf(
                    WinMdType(
                        namespace = "Windows.Foundation.Collections",
                        name = "IVector`1",
                        kind = WinMdTypeKind.Interface,
                        guid = "913337e9-11a1-4345-a3a2-4e7f956e222d",
                        genericParameters = listOf("T"),
                    ),
                    WinMdType(
                        namespace = "Windows.Foundation.Collections",
                        name = "IVectorView`1",
                        kind = WinMdTypeKind.Interface,
                        guid = "bbe1fa4c-b0e3-4583-baef-1f1b2e483e56",
                        genericParameters = listOf("T"),
                    ),
                    WinMdType(
                        namespace = "Windows.Foundation.Collections",
                        name = "IMap`2",
                        kind = WinMdTypeKind.Interface,
                        guid = "fbd6f7c2-0035-4f89-91cb-6b0bf5d8c9d6",
                        genericParameters = listOf("K", "V"),
                    ),
                    WinMdType(
                        namespace = "Windows.Foundation.Collections",
                        name = "IMapView`2",
                        kind = WinMdTypeKind.Interface,
                        guid = "e5f839be-1a86-4e27-b357-f8c0d2d9d0d1",
                        genericParameters = listOf("K", "V"),
                    ),
                    WinMdType(
                        namespace = "Windows.Foundation.Collections",
                        name = "IKeyValuePair`2",
                        kind = WinMdTypeKind.Interface,
                        guid = "8f4cf5d3-0fa1-4c97-aab5-2e6f2d0b5e5e",
                        genericParameters = listOf("K", "V"),
                    ),
                ),
            ),
            WinMdNamespace(
                name = "Microsoft.UI.Xaml",
                types = listOf(
                    WinMdType(
                        namespace = "Microsoft.UI.Xaml",
                        name = "DependencyObject",
                        kind = WinMdTypeKind.RuntimeClass,
                        defaultInterface = "Microsoft.UI.Xaml.IDependencyObject",
                    ),
                    WinMdType(
                        namespace = "Microsoft.UI.Xaml",
                        name = "IDependencyObject",
                        kind = WinMdTypeKind.Interface,
                        guid = "11111111-1111-1111-1111-111111111111",
                    ),
                    WinMdType(
                        namespace = "Microsoft.UI.Xaml",
                        name = "UIElement",
                        kind = WinMdTypeKind.RuntimeClass,
                        baseClass = "Microsoft.UI.Xaml.DependencyObject",
                        defaultInterface = "Microsoft.UI.Xaml.IUIElement",
                    ),
                    WinMdType(
                        namespace = "Microsoft.UI.Xaml",
                        name = "IUIElement",
                        kind = WinMdTypeKind.Interface,
                        guid = "22222222-2222-2222-2222-222222222222",
                    ),
                ),
            ),
            WinMdNamespace(
                name = "Windows.Foundation",
                types = listOf(
                    WinMdType(
                        namespace = "Windows.Foundation",
                        name = "Point",
                        kind = WinMdTypeKind.Struct,
                        fields = listOf(
                            WinMdField("X", "Float64"),
                            WinMdField("Y", "Float64"),
                        ),
                    ),
                ),
            ),
            WinMdNamespace(
                name = "Windows.UI",
                types = listOf(
                    WinMdType(
                        namespace = "Windows.UI",
                        name = "Color",
                        kind = WinMdTypeKind.Struct,
                        fields = listOf(
                            WinMdField("A", "UInt8"),
                            WinMdField("R", "UInt8"),
                            WinMdField("G", "UInt8"),
                            WinMdField("B", "UInt8"),
                        ),
                    ),
                ),
            ),
        ),
    )

    private val mapper = WinRtSignatureMapper(TypeRegistry(model))
    private val projectionTypeMapper = WinRtProjectionTypeMapper()

    @Test
    fun maps_signatures_from_cases() {
        signatureCases().forEach { case ->
            assertEquals(
                case.expected,
                mapper.signatureFor(case.typeName, case.currentNamespace),
            )
        }
    }

    @Test
    fun maps_interface_ids_from_cases() {
        interfaceIdCases().forEach { case ->
            assertEquals(
                case.expected,
                mapper.interfaceIdFor(case.typeName, case.currentNamespace),
            )
        }
    }

    @Test
    fun maps_projection_type_keys_from_cases() {
        projectionTypeKeyCases().forEach { case ->
            assertEquals(
                case.expected,
                projectionTypeMapper.projectionTypeKeyFor(case.typeName, case.currentNamespace),
            )
        }
    }

    private fun signatureCases(): List<SignatureCase> = listOf(
        SignatureCase(
            typeName = "Microsoft.UI.Xaml.UIElement",
            currentNamespace = "Microsoft.UI.Xaml",
            expected = "rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222})",
        ),
        SignatureCase(
            typeName = "Microsoft.UI.Xaml.DependencyObject",
            currentNamespace = "Microsoft.UI.Xaml",
            expected = "rc(Microsoft.UI.Xaml.DependencyObject;{11111111-1111-1111-1111-111111111111})",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IVector`1<Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Microsoft.UI.Xaml.Controls",
            expected = "pinterface({913337e9-11a1-4345-a3a2-4e7f956e222d};rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222}))",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Point",
            currentNamespace = "Windows.Foundation",
            expected = "struct(Windows.Foundation.Point;f4;f4)",
        ),
        SignatureCase(
            typeName = "Windows.UI.Color",
            currentNamespace = "Windows.UI",
            expected = "struct(Windows.UI.Color;u1;u1;u1;u1)",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Rect",
            currentNamespace = "Microsoft.UI.Xaml",
            expected = "struct(Windows.Foundation.Rect;f4;f4;f4;f4)",
        ),
        SignatureCase(
            typeName = "Size",
            currentNamespace = "Windows.Foundation",
            expected = "struct(Windows.Foundation.Size;f4;f4)",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.EventRegistrationToken",
            currentNamespace = "Windows.Foundation",
            expected = "struct(Windows.Foundation.EventRegistrationToken;i8)",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IVectorView`1<Windows.Foundation.Rect>",
            currentNamespace = "Microsoft.UI.Xaml",
            expected = "pinterface({bbe1fa4c-b0e3-4583-baef-1f1b2e483e56};struct(Windows.Foundation.Rect;f4;f4;f4;f4))",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IVectorView`1<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "pinterface({bbe1fa4c-b0e3-4583-baef-1f1b2e483e56};string)",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IMap`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "pinterface({fbd6f7c2-0035-4f89-91cb-6b0bf5d8c9d6};string;rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222}))",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IMapView`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "pinterface({e5f839be-1a86-4e27-b357-f8c0d2d9d0d1};string;rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222}))",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IMapView`2<String, Windows.Foundation.Collections.IVectorView`1<Microsoft.UI.Xaml.UIElement>>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "pinterface({e5f839be-1a86-4e27-b357-f8c0d2d9d0d1};string;pinterface({bbe1fa4c-b0e3-4583-baef-1f1b2e483e56};rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222})))",
        ),
        SignatureCase(
            typeName = "Windows.Foundation.Collections.IKeyValuePair`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "pinterface({8f4cf5d3-0fa1-4c97-aab5-2e6f2d0b5e5e};string;rc(Microsoft.UI.Xaml.UIElement;{22222222-2222-2222-2222-222222222222}))",
        ),
    )

    private fun interfaceIdCases(): List<InterfaceIdCase> = listOf(
        InterfaceIdCase(
            typeName = "Windows.Foundation.Collections.IVector`1<Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Microsoft.UI.Xaml.Controls",
            expected = "344089a3-ea12-587e-aa3f-cfefad439688",
        ),
        InterfaceIdCase(
            typeName = "Windows.Foundation.Collections.IVectorView`1<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "51ceb9ce-8ac1-5bad-9d98-1f197b39a5db",
        ),
    )

    private fun projectionTypeKeyCases(): List<ProjectionTypeKeyCase> = listOf(
        ProjectionTypeKeyCase(
            typeName = "Microsoft.UI.Xaml.Interop.IBindableVector",
            currentNamespace = "Microsoft.UI.Xaml.Interop",
            expected = "kotlin.collections.MutableList",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.UI.Xaml.Interop.IBindableVector",
            currentNamespace = "Windows.UI.Xaml.Interop",
            expected = "kotlin.collections.MutableList",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IVector`1<Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Microsoft.UI.Xaml.Controls",
            expected = "kotlin.collections.MutableList<Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IVectorView`1<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.List<String>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IMapView`2<String, Windows.Foundation.Collections.IVectorView`1<Microsoft.UI.Xaml.UIElement>>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.Map<String, kotlin.collections.List<Microsoft.UI.Xaml.UIElement>>",
        ),
    )

    private data class SignatureCase(
        val typeName: String,
        val currentNamespace: String,
        val expected: String,
    )

    private data class InterfaceIdCase(
        val typeName: String,
        val currentNamespace: String,
        val expected: String,
    )

    private data class ProjectionTypeKeyCase(
        val typeName: String,
        val currentNamespace: String,
        val expected: String,
    )
}
