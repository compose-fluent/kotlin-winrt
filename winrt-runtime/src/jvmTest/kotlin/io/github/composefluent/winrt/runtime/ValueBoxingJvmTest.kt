package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueBoxingJvmTest {
    @Test
    fun concrete_reference_array_registration_retains_type_only_metadata() {
        TypeNameSupport.clearRegistriesForTests()
        try {
            val arrayType = emptyArray<String>()::class
            TypeNameSupport.registerReferenceArrayType(String::class, arrayType)
            val elementType = TypeNameSupport.registeredReferenceArrayElementType(arrayType)
                ?: error("Concrete JVM array registration should retain its element type.")

            assertEquals<kotlin.reflect.KClass<*>>(
                String::class,
                elementType,
            )
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun reference_array_component_metadata_is_not_recovered_from_jvm_reflection() {
        TypeNameSupport.clearRegistriesForTests()
        try {
            assertNull(arrayElementType(emptyArray<String>()::class))
            assertNull(arrayElementType(emptyArray<Any?>()::class))
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun declared_object_arrays_round_trip_as_inspectable_arrays() {
        ComWrappersSupport.clearRegistriesForTests()

        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(1, "text"), IID.IReferenceArrayOfInt32)
        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(null, "text"), IID.IReferenceArrayOfString)
        assertInspectableObjectArrayRoundTrip(arrayOf<Any?>(1, 2), IID.IReferenceArrayOfInt32)
    }

    private fun assertInspectableObjectArrayRoundTrip(
        expected: Array<Any?>,
        unexpectedReferenceArrayInterfaceId: Guid,
    ) {
        assertEquals(PropertyType.InspectableArray, WinRTValueBoxing.propertyTypeOf(expected, Any::class))

        val marshaler = Marshaler.inspectableArray<Any>()
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
