package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WinRtCollectionInterfaceIdsTest {
    @Test
    fun iterable_signature_renders_expected_shape() {
        val signature = WinRtCollectionInterfaceIds.iterableSignature(
            WinRtTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90"),
        )

        assertEquals(
            "pinterface({faa585ea-6214-4217-afda-7f46de5869b3};{af86e2e0-b12d-4c6a-9c5a-d7aa65101e90})",
            signature.render(),
        )
    }

    @Test
    fun map_and_map_view_iids_are_distinct_for_same_type_arguments() {
        val key = WinRtTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
        val value = WinRtTypeSignature.guid("00000000-0000-0000-C000-000000000046")

        val map = WinRtCollectionInterfaceIds.map(key, value)
        val mapView = WinRtCollectionInterfaceIds.mapView(key, value)

        assertNotEquals(map, mapView)
    }

    @Test
    fun key_value_pair_iid_is_deterministic() {
        val key = WinRtTypeSignature.guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
        val value = WinRtTypeSignature.object_()

        val first = WinRtCollectionInterfaceIds.keyValuePair(key, value)
        val second = WinRtCollectionInterfaceIds.keyValuePair(key, value)

        assertEquals(first, second)
    }

    @Test
    fun key_value_pair_string_signature_renders_expected_shape() {
        val signature = WinRtCollectionInterfaceIds.keyValuePairSignature(
            WinRtTypeSignature.string(),
            WinRtTypeSignature.string(),
        )

        assertEquals(
            "pinterface({02b51929-c1c4-4a7e-8940-0312b5c18500};string;string)",
            signature.render(),
        )
    }
}
