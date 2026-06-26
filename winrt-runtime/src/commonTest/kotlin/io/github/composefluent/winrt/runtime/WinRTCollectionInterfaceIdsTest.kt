package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WinRTCollectionInterfaceIdsTest {
    @Test
    fun iterable_signature_renders_expected_shape() {
        val signature = WinRTCollectionInterfaceIds.iterableSignature(
            WinRTTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90"),
        )

        assertEquals(
            "pinterface({faa585ea-6214-4217-afda-7f46de5869b3};{af86e2e0-b12d-4c6a-9c5a-d7aa65101e90})",
            signature.render(),
        )
    }

    @Test
    fun map_and_map_view_iids_are_distinct_for_same_type_arguments() {
        val key = WinRTTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
        val value = WinRTTypeSignature.guid("00000000-0000-0000-C000-000000000046")

        val map = WinRTCollectionInterfaceIds.map(key, value)
        val mapView = WinRTCollectionInterfaceIds.mapView(key, value)

        assertNotEquals(map, mapView)
    }

    @Test
    fun key_value_pair_iid_is_deterministic() {
        val key = WinRTTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
        val value = WinRTTypeSignature.object_()

        val first = WinRTCollectionInterfaceIds.keyValuePair(key, value)
        val second = WinRTCollectionInterfaceIds.keyValuePair(key, value)

        assertEquals(first, second)
    }

    @Test
    fun key_value_pair_string_signature_renders_expected_shape() {
        val signature = WinRTCollectionInterfaceIds.keyValuePairSignature(
            WinRTTypeSignature.string(),
            WinRTTypeSignature.string(),
        )

        assertEquals(
            "pinterface({02b51929-c1c4-4a7e-8940-0312b5c18500};string;string)",
            signature.render(),
        )
    }
}
