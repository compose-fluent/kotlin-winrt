package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValueBoxingJvmTest {
    @Test
    fun reference_array_component_metadata_does_not_depend_on_registration() {
        TypeNameSupport.clearRegistriesForTests()

        assertEquals(String::class, arrayElementType(emptyArray<String>()::class))
        assertEquals(Any::class, arrayElementType(emptyArray<Any?>()::class))
    }

    @Test
    fun declared_object_arrays_round_trip_as_inspectable_arrays() {
        ComWrappersSupport.clearRegistriesForTests()

        val objectArrayClass = emptyArray<Any?>()::class
        assertEquals(Any::class, arrayElementType(objectArrayClass))
        assertEquals(
            "Windows.Foundation.IReferenceArray`1<Object>",
            WinRTValueBoxing.boxedRuntimeClassNameForType(objectArrayClass),
        )
        assertTrue(Projections.isTypeWindowsRuntimeType(objectArrayClass))

        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(1, "text"), IID.IReferenceArrayOfInt32)
        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(null, "text"), IID.IReferenceArrayOfString)
        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(1, 2), IID.IReferenceArrayOfInt32)
    }

    private fun assertInspectableObjectArrayRoundTrip(
        expected: Array<Any?>,
        unexpectedReferenceArrayInterfaceId: Guid,
    ) {
        assertEquals(PropertyType.InspectableArray, WinRTValueBoxing.propertyTypeOf(expected))

        val marshaler = Marshaler.inspectableAny()
        val abi = marshaler.fromManaged(expected) as RawAddress
        try {
            IInspectableReference(abi.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { inspectable ->
                inspectable.queryInterface(IID.IReferenceArrayOfObject).getOrThrow().close()
                assertTrue(inspectable.queryInterface(unexpectedReferenceArrayInterfaceId).isFailure)
            }

            val actual = marshaler.fromAbi(abi) as Array<*>
            assertEquals(expected.toList(), actual.toList())
        } finally {
            marshaler.disposeAbi(abi)
        }
    }
}
