package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValueBoxingNativeTest {
    @Test
    fun reference_array_class_metadata_is_erased() {
        ComWrappersSupport.clearRegistriesForTests()

        assertNull(arrayElementType(emptyArray<String>()::class))
        assertNull(WinRTValueBoxing.boxedRuntimeClassNameForType(emptyArray<String>()::class))
    }

    @Test
    fun erased_reference_arrays_are_classified_from_all_non_null_values() {
        ComWrappersSupport.clearRegistriesForTests()

        val homogeneous = arrayOf("one", "two")
        assertEquals(PropertyType.StringArray, WinRTValueBoxing.propertyTypeOf(homogeneous))
        assertEquals(IID.IReferenceArrayOfString, ValueBoxingMetadata.referenceArrayInterfaceIdForValue(homogeneous))

        val empty = emptyArray<String>()
        assertEquals(PropertyType.InspectableArray, WinRTValueBoxing.propertyTypeOf(empty))
        assertEquals(IID.IReferenceArrayOfObject, ValueBoxingMetadata.referenceArrayInterfaceIdForValue(empty))
    }
}
