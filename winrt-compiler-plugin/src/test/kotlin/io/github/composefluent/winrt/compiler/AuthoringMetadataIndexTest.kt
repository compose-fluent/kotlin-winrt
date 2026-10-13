package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.*
import io.github.composefluent.winrt.runtime.XamlFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthoringMetadataIndexTest {
    @Test
    fun xaml_alias_resolution_uses_the_selected_framework_after_ir_typealias_expansion() {
        // CsWinRT Projections.cs registers WUX/MUX aliases for the same managed type.
        // Kotlin typealiases erase their source namespace in IR, so the declaration's
        // framework must determine the ABI entry even when both SDKs are indexed.
        val winui = "Microsoft.UI.Xaml.Data.PropertyChangedEventArgs"
        val uwp = "Windows.UI.Xaml.Data.PropertyChangedEventArgs"
        val index = readAuthoringMetadataIndexRows(listOf("$winui\tRuntimeClass", "$uwp\tRuntimeClass"), "xaml")
        for (types in listOf(index, index.toMap())) {
            assertEquals(uwp, resolveIndexedWinRTTypeByProjectedName(
                "microsoft.ui.xaml.data.PropertyChangedEventArgs", types, XamlFramework.Uwp)?.qualifiedName)
            assertEquals(winui, resolveIndexedWinRTTypeByProjectedName(
                "windows.ui.xaml.data.PropertyChangedEventArgs", types, XamlFramework.WinUi)?.qualifiedName)
            assertNull(resolveIndexedWinRTTypeByProjectedName(
                "microsoft.ui.xaml.data.PropertyChangedEventArgs", types - uwp, XamlFramework.Uwp))
        }
    }

    @Test
    fun indexed_lookup_preserves_exact_names_mapping_and_first_case_insensitive_match() {
        // CsWinRT helpers.h uses the reader's name cache. The Kotlin-specific
        // case-insensitive fallback must retain its existing lookup precedence.
        val names = listOf(
            "Windows.Foundation.IStringable", "WINDOWS.FOUNDATION.ISTRINGABLE",
            "Windows.Foundation.Collections.IIterable`1",
            "Example.Widget", "IO.GITHUB.COMPOSEFLUENT.WINRT.PROJECTIONS.EXAMPLE.WIDGET",
            "Example.İ", "Example.ı", "Example.Σ", "Example.ς",
        )
        val queries = names + listOf(
            "windows.foundation.IStringable", "WINDOWS.foundation.istringable",
            "io.github.composefluent.winrt.projections.example.widget",
            "example.WIDGET", "kotlin.collections.Iterable", "kotlin.Int",
            "example.i", "example.σ", "missing.Type",
        )
        for (orderedNames in listOf(names, names.reversed())) {
            val indexed = readAuthoringMetadataIndexRows(orderedNames.map { "$it\tInterface" }, "test")
            val plain = indexed.toMap()
            assertEquals(plain, indexed)
            assertEquals(plain.keys.toList(), indexed.keys.toList())
            for (query in queries) {
                assertEquals(query,
                    resolveIndexedWinRTTypeByProjectedName(query, plain),
                    resolveIndexedWinRTTypeByProjectedName(query, indexed))
            }
        }
    }

    @Test
    fun indexes_are_scoped_to_each_metadata_snapshot_and_mutable_maps_stay_live() {
        val first = readAuthoringMetadataIndexRows(listOf("Example.Old\tInterface"), "first")
        val second = readAuthoringMetadataIndexRows(listOf("Example.New\tInterface"), "second")
        assertNull(resolveIndexedWinRTTypeByProjectedName("example.old", second))
        assertNull(resolveIndexedWinRTTypeByProjectedName("example.new", first))
        val mutable = first.toMutableMap()
        val replacement = second.getValue("Example.New")
        mutable.clear()
        mutable[replacement.qualifiedName] = replacement
        assertEquals(replacement, resolveIndexedWinRTTypeByProjectedName("example.new", mutable))
        assertNull(resolveIndexedWinRTTypeByProjectedName("example.old", mutable))
    }
}
