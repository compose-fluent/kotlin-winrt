package dev.winrt.winmd.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class WinRtProjectionTypeMapperTest {
    private val mapper = WinRtProjectionTypeMapper()

    @Test
    fun maps_projection_type_keys_from_cases() {
        projectionTypeKeyCases().forEach { case ->
            assertEquals(
                case.expected,
                mapper.projectionTypeKeyFor(case.typeName, case.currentNamespace),
            )
        }
    }

    private fun projectionTypeKeyCases(): List<ProjectionTypeKeyCase> = listOf(
        ProjectionTypeKeyCase(
            typeName = "Microsoft.UI.Xaml.UIElement",
            currentNamespace = "Microsoft.UI.Xaml",
            expected = "Microsoft.UI.Xaml.UIElement",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IVector`1<Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Microsoft.UI.Xaml.Controls",
            expected = "kotlin.collections.MutableList<Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IIterable<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.Iterable<String>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IVectorView<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.List<String>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Microsoft.UI.Xaml.Interop.IBindableIterable",
            currentNamespace = "Microsoft.UI.Xaml.Interop",
            expected = "kotlin.collections.Iterable",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.UI.Xaml.Interop.IBindableVectorView",
            currentNamespace = "Windows.UI.Xaml.Interop",
            expected = "kotlin.collections.List",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IVectorView`1<String>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.List<String>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IMap`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.MutableMap<String, Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IMapView`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.Map<String, Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IObservableVector`1<Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.MutableList<Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IObservableMap`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.MutableMap<String, Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IMap`2<String, Windows.Foundation.Collections.IVectorView`1<Microsoft.UI.Xaml.UIElement>>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.MutableMap<String, kotlin.collections.List<Microsoft.UI.Xaml.UIElement>>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IMapView`2<String, Windows.Foundation.Collections.IKeyValuePair`2<String, Microsoft.UI.Xaml.UIElement>>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.Map<String, kotlin.collections.Map.Entry<String, Microsoft.UI.Xaml.UIElement>>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.Collections.IKeyValuePair`2<String, Microsoft.UI.Xaml.UIElement>",
            currentNamespace = "Windows.Foundation.Collections",
            expected = "kotlin.collections.Map.Entry<String, Microsoft.UI.Xaml.UIElement>",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.HResult",
            currentNamespace = "Windows.Foundation",
            expected = "Exception",
        ),
        ProjectionTypeKeyCase(
            typeName = "Windows.Foundation.IReference`1<Windows.Foundation.HResult>",
            currentNamespace = "Windows.Foundation",
            expected = "Windows.Foundation.IReference`1<Exception>",
        ),
    )

    private data class ProjectionTypeKeyCase(
        val typeName: String,
        val currentNamespace: String,
        val expected: String,
    )
}
