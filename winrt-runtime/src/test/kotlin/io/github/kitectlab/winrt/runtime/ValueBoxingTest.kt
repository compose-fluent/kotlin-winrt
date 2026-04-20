package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ValueBoxingTest {
    private val boxedReferenceGenericInterface = "61C17706-2D65-11E0-9AE8-D48564015472"

    @Test
    fun inspectable_marshaler_unboxes_property_values_and_arrays() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val marshaler = Marshaler.inspectableAny()
        val dateTime = OffsetDateTime.ofInstant(Instant.parse("2024-05-06T07:08:09Z"), ZoneId.systemDefault())

        roundTripInspectable(marshaler, 42) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, "projection-runtime") { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, Duration.ofMinutes(5)) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, dateTime) { expected, actual ->
            assertEquals(expected.toInstant(), actual.toInstant())
        }
        roundTripInspectable(marshaler, Point(1.5f, 2.5f)) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, arrayOf("alpha", "beta")) { expected, actual ->
            assertEquals(expected.toList(), actual.toList())
        }
        roundTripInspectable(marshaler, arrayOf(Point(3f, 4f), Point(5f, 6f))) { expected, actual ->
            assertEquals(expected.toList(), actual.toList())
        }
    }

    @Test
    fun reference_and_reference_array_projections_round_trip_supported_values() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals(42, WinRtReferenceProjection.fromAbi(WinRtReferenceProjection.fromManaged(42, IID.NullableInt), IID.NullableInt))
        assertEquals(
            Duration.ofSeconds(12),
            WinRtReferenceProjection.fromAbi(
                WinRtReferenceProjection.fromManaged(Duration.ofSeconds(12), IID.NullableTimeSpan),
                IID.NullableTimeSpan,
            ),
        )
        assertEquals(
            Point(7f, 8f),
            WinRtReferenceProjection.fromAbi(
                WinRtReferenceProjection.fromManaged(Point(7f, 8f), IID.IReferenceOfPoint),
                IID.IReferenceOfPoint,
            ),
        )

        val points = arrayOf(Point(1f, 2f), Point(3f, 4f))
        val projectedPoints =
            WinRtReferenceArrayProjection.fromAbi(
                WinRtReferenceArrayProjection.fromManaged(points, IID.IReferenceArrayOfPoint),
                IID.IReferenceArrayOfPoint,
            ) ?: error("Projected array should not be null.")
        assertEquals(points.toList(), projectedPoints.toList())
    }

    @Test
    fun boxed_ccws_expose_reference_and_property_value_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()

        val scalarPointer = ComWrappersSupport.createCCWForObject(123, IID.IInspectable).useAndGetRef()
        try {
            IInspectableReference(scalarPointer, IID.IInspectable).use { inspectable ->
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().close()
                inspectable.queryInterface(IID.NullableInt).getOrThrow().close()
            }
        } finally {
            IUnknownReference(scalarPointer, IID.IInspectable).close()
        }

        val arrayPointer = ComWrappersSupport.createCCWForObject(arrayOf("one", "two"), IID.IInspectable).useAndGetRef()
        try {
            IInspectableReference(arrayPointer, IID.IInspectable).use { inspectable ->
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().close()
                inspectable.queryInterface(IID.IReferenceArrayOfString).getOrThrow().close()
            }
        } finally {
            IUnknownReference(arrayPointer, IID.IInspectable).close()
        }
    }

    @Test
    fun runtime_hooks_project_uri_and_iclosable_like_cswinrt() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val uri = URI("https://example.com/runtime-117?value=1")
        val uriPointer = ComWrappersSupport.createCCWForObject(uri, IID.IInspectable).useAndGetRef()
        try {
            assertEquals(uri, ComWrappersSupport.createRcwForComObject(uriPointer))
            IInspectableReference(uriPointer, IID.IInspectable).use { inspectable ->
                assertEquals("Windows.Foundation.Uri", inspectable.getRuntimeClassName())
            }
        } finally {
            IUnknownReference(uriPointer, IID.IInspectable).close()
        }

        val closeable = TestCloseable()
        val closeablePointer = ComWrappersSupport.createCCWForObject(closeable, IID.IInspectable).useAndGetRef()
        try {
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    closeablePointer,
                    WinRtTypeHandle(AutoCloseable::class.java.name, IID.IDisposable),
                ) as AutoCloseable
            projected.close()
            assertTrue(closeable.closed)
        } finally {
            IUnknownReference(closeablePointer, IID.IInspectable).close()
        }
    }

    @Test
    fun bindable_object_marshaler_unboxes_property_value_boxes() {
        ComWrappersSupport.clearRegistriesForTests()

        val pointer = ComWrappersSupport.createCCWForObject(Duration.ofSeconds(9), IID.IInspectable).useAndGetRef()
        try {
            assertEquals(Duration.ofSeconds(9), WinRtBindableObjectMarshaller.fromBorrowedAbi(pointer))
        } finally {
            IUnknownReference(pointer, IID.IInspectable).close()
        }
    }

    @Test
    fun boxed_reference_runtime_names_round_trip_type_exception_and_enum_values() {
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.registerProjectionAssembly(TestPriority::class.java, TestVisibility::class.java)

        roundTripInspectable(Marshaler.inspectableAny(), String::class.java) { expected, actual ->
            assertEquals(expected, actual)
        }

        val exceptionPointer = ComWrappersSupport.createCCWForObject(IllegalStateException("boom"), IID.IInspectable).useAndGetRef()
        try {
            val projected = ComWrappersSupport.createRcwForComObject(exceptionPointer) as Exception
            assertEquals(ExceptionProjection.toAbi(IllegalStateException("boom")), ExceptionProjection.toAbi(projected))
        } finally {
            IUnknownReference(exceptionPointer, IID.IInspectable).close()
        }

        val priorityPointer = ComWrappersSupport.createCCWForObject(TestPriority.High, IID.IInspectable).useAndGetRef()
        try {
            IUnknownReference(priorityPointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use { inspectable ->
                inspectable.queryInterface(priorityNullableIid()).getOrThrow().close()
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().use { propertyValue ->
                    val projected = WinRtPropertyValueReference(propertyValue.pointer, preventReleaseOnDispose = true)
                    assertEquals(2, projected.getValue())
                }
            }
            assertEquals(TestPriority.High, ComWrappersSupport.createRcwForComObject(priorityPointer))
            assertEquals(TestPriority.High, WinRtBindableObjectMarshaller.fromBorrowedAbi(priorityPointer))
        } finally {
            IUnknownReference(priorityPointer, IID.IInspectable).close()
        }

        val visibilityPointer = ComWrappersSupport.createCCWForObject(TestVisibility.Visible, IID.IInspectable).useAndGetRef()
        try {
            IUnknownReference(visibilityPointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use { inspectable ->
                inspectable.queryInterface(visibilityNullableIid()).getOrThrow().close()
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().use { propertyValue ->
                    val projected = WinRtPropertyValueReference(propertyValue.pointer, preventReleaseOnDispose = true)
                    assertEquals(1u, projected.getValue())
                }
            }
            assertEquals(TestVisibility.Visible, ComWrappersSupport.createRcwForComObject(visibilityPointer))
            assertEquals(TestVisibility.Visible, WinRtBindableObjectMarshaller.fromBorrowedAbi(visibilityPointer))
        } finally {
            IUnknownReference(visibilityPointer, IID.IInspectable).close()
        }
    }

    private fun <T : Any> roundTripInspectable(
        marshaler: Marshaler<Any?>,
        value: T,
        assertRoundTrip: (T, T) -> Unit,
    ) {
        val abi = marshaler.fromManaged(value) as MemorySegment
        try {
            @Suppress("UNCHECKED_CAST")
            assertRoundTrip(value, marshaler.fromAbi(abi) as T)
        } finally {
            marshaler.disposeAbi(abi)
        }
    }

    private class TestCloseable : AutoCloseable {
        var closed: Boolean = false

        override fun close() {
            closed = true
        }
    }

    @WindowsRuntimeType("enum(Contoso.Priority;i4)")
    private enum class TestPriority(
        val abiValue: Int,
    ) {
        Low(0),
        High(2),
    }

    @WindowsRuntimeType("enum(Contoso.Visibility;u4)")
    private enum class TestVisibility(
        val abiValue: UInt,
    ) {
        Hidden(0u),
        Visible(1u),
        Archived(2u),
    }

    private fun priorityNullableIid(): Guid =
        ParameterizedInterfaceId.createFromSignature(
            "pinterface({${boxedReferenceGenericInterface.lowercase()}};enum(Contoso.Priority;i4))",
        )

    private fun visibilityNullableIid(): Guid =
        ParameterizedInterfaceId.createFromSignature(
            "pinterface({${boxedReferenceGenericInterface.lowercase()}};enum(Contoso.Visibility;u4))",
        )
}
