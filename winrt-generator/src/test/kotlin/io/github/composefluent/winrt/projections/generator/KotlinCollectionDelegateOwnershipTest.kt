package io.github.composefluent.winrt.projections.generator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinCollectionDelegateOwnershipTest {
    @Test
    fun nested_collection_adapters_retain_borrowed_callback_references() {
        val renderer = KotlinProjectionRenderer()
        val string = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String")
        val kinds = listOf(
            KotlinProjectionAbiValueKind.MappedIterable to "Windows.Foundation.Collections.IIterable`1",
            KotlinProjectionAbiValueKind.MappedVectorView to "Windows.Foundation.Collections.IVectorView`1",
            KotlinProjectionAbiValueKind.MappedVector to "Windows.Foundation.Collections.IVector`1",
            KotlinProjectionAbiValueKind.MappedMapView to "Windows.Foundation.Collections.IMapView`2",
            KotlinProjectionAbiValueKind.MappedMap to "Windows.Foundation.Collections.IMap`2",
        )
        kinds.forEach { (kind, name) ->
            val binding = KotlinProjectionAbiTypeBinding(
                kind, name, typeArguments = List(if (name.endsWith("`2")) 2 else 1) { string },
            )
            val source = requireNotNull(renderer.collectionReferenceAdapterCode(binding)).toString()
            assertTrue(source, source.contains("reference.acquireOwnedPointer()"))
            assertFalse(source, source.contains("reference.pointer"))
        }
    }

    @Test
    fun collection_facades_retain_the_cached_interface_before_transferring_ownership() {
        // CsWinRT IList.net5.cs keeps its facade's object reference alive independently.
        // Kotlin collection fromAbi consumes its pointer; the owner's cache remains borrowed.
        val renderer = KotlinProjectionRenderer()
        val string = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.String, "String")
        val initializers = KotlinProjectionMutableCollectionKind.entries.map { kind ->
            renderer.renderMutableCollectionDelegateInitializer(KotlinProjectionMutableCollectionBinding(
                kind, "Sample.Collection", "_owner", "Sample.Collection", "delegate",
                elementBinding = string, keyBinding = string, valueBinding = string,
            )).toString()
        } + KotlinProjectionReadOnlyCollectionKind.entries.map { kind ->
            renderer.renderReadOnlyCollectionDelegateInitializer(KotlinProjectionReadOnlyCollectionBinding(
                kind, "Sample.Collection", "_owner", "Sample.Collection", "delegate",
                elementBinding = string, keyBinding = string, valueBinding = string,
            )).toString()
        }
        initializers.forEach { source ->
            assertTrue(source, source.contains("fromAbi(") && source.contains("_owner.acquireOwnedPointer()"))
            assertFalse(source, source.contains("_owner.pointer"))
        }
    }
}
