package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ValueBoxingTest {
    private val boxedReferenceGenericInterface = "61C17706-2D65-11E0-9AE8-D48564015472"

    @Test
    fun inspectable_marshaler_unboxes_property_values_and_arrays() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val marshaler = Marshaler.inspectableAny()
        val dateTime = Instant.parse("2024-05-06T07:08:09Z")
        registerProjectedPointBoxing()

        roundTripInspectable(marshaler, 42) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, "projection-runtime") { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, 5.minutes) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, dateTime) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, ProjectedPoint(1.5f, 2.5f)) { expected, actual ->
            assertEquals(expected, actual)
        }
        roundTripInspectable(marshaler, arrayOf("alpha", "beta")) { expected, actual ->
            assertEquals(expected.toList(), actual.toList())
        }
        roundTripInspectable(marshaler, arrayOf(ProjectedPoint(3f, 4f), ProjectedPoint(5f, 6f))) { expected, actual ->
            assertEquals(expected.toList(), actual.toList())
        }
    }

    @Test
    fun reference_and_reference_array_projections_round_trip_supported_values() {
        ComWrappersSupport.clearRegistriesForTests()
        registerProjectedPointBoxing()

        assertEquals(42, WinRtReferenceProjection.fromAbi(WinRtReferenceProjection.fromManaged(42, IID.NullableInt), IID.NullableInt))
        assertEquals(
            12.seconds,
            WinRtReferenceProjection.fromAbi(
                WinRtReferenceProjection.fromManaged(12.seconds, IID.NullableTimeSpan),
                IID.NullableTimeSpan,
            ),
        )
        assertEquals(
            ProjectedPoint(7f, 8f),
            WinRtReferenceProjection.fromAbi(
                WinRtReferenceProjection.fromManaged(ProjectedPoint(7f, 8f), IID.IReferenceOfPoint),
                IID.IReferenceOfPoint,
            ),
        )

        val points = arrayOf(ProjectedPoint(1f, 2f), ProjectedPoint(3f, 4f))
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
            IInspectableReference(scalarPointer.asRawComPtr(), IID.IInspectable).use { inspectable ->
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().close()
                inspectable.queryInterface(IID.NullableInt).getOrThrow().close()
            }
        } finally {
            IUnknownReference(scalarPointer.asRawComPtr(), IID.IInspectable).close()
        }

        val arrayPointer = ComWrappersSupport.createCCWForObject(arrayOf("one", "two"), IID.IInspectable).useAndGetRef()
        try {
            IInspectableReference(arrayPointer.asRawComPtr(), IID.IInspectable).use { inspectable ->
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().close()
                inspectable.queryInterface(IID.IReferenceArrayOfString).getOrThrow().close()
            }
        } finally {
            IUnknownReference(arrayPointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun boxed_ccw_inspectable_get_iids_matches_cswinrt_order() {
        ComWrappersSupport.clearRegistriesForTests()

        val scalarPointer = ComWrappersSupport.createCCWForObject(123, IID.IInspectable).useAndGetRef()
        try {
            // Mirrors .cswinrt/src/WinRT.Runtime/ComWrappersSupport.cs GetInterfaceTableEntries:
            // IPropertyValue, IReference<T>, then the standard CCW suffix.
            assertInspectableGetIids(
                pointer = scalarPointer,
                interfaceId = IID.IInspectable,
                expected = listOf(
                    IID.IPropertyValue,
                    IID.NullableInt,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IUnknown,
                ),
            )
        } finally {
            IUnknownReference(scalarPointer.asRawComPtr(), IID.IInspectable).close()
        }

        val arrayPointer = ComWrappersSupport.createCCWForObject(arrayOf("one", "two"), IID.IInspectable).useAndGetRef()
        try {
            // Mirrors the same cswinrt array path: IPropertyValue, IReferenceArray<T>, then the suffix.
            assertInspectableGetIids(
                pointer = arrayPointer,
                interfaceId = IID.IInspectable,
                expected = listOf(
                    IID.IPropertyValue,
                    IID.IReferenceArrayOfString,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IUnknown,
                ),
            )
        } finally {
            IUnknownReference(arrayPointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun value_reference_projection_hosts_get_iids_matches_cswinrt_order() {
        ComWrappersSupport.clearRegistriesForTests()

        val referencePointer = WinRtReferenceProjection.fromManaged("projection-runtime", IID.NullableString)
        try {
            assertInspectableGetIids(
                pointer = referencePointer,
                interfaceId = IID.NullableString,
                expected = listOf(
                    IID.IPropertyValue,
                    IID.NullableString,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IUnknown,
                ),
            )
        } finally {
            IUnknownReference(referencePointer.asRawComPtr(), IID.NullableString).close()
        }

        val arrayPointer = WinRtReferenceArrayProjection.fromManaged(arrayOf("one", "two"), IID.IReferenceArrayOfString)
        try {
            assertInspectableGetIids(
                pointer = arrayPointer,
                interfaceId = IID.IReferenceArrayOfString,
                expected = listOf(
                    IID.IPropertyValue,
                    IID.IReferenceArrayOfString,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IUnknown,
                ),
            )
        } finally {
            IUnknownReference(arrayPointer.asRawComPtr(), IID.IReferenceArrayOfString).close()
        }
    }

    @Test
    fun reference_projection_hosts_expose_cswinrt_ccw_suffix_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()

        val pointer = WinRtReferenceProjection.fromManaged("projection-runtime", IID.NullableString)
        try {
            IUnknownReference(pointer.asRawComPtr(), IID.NullableString, preventReleaseOnDispose = true).use { reference ->
                listOf(
                    IID.IPropertyValue,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IReferenceTrackerTarget,
                ).forEach { iid ->
                    reference.queryInterface(iid).getOrThrow().use { queried ->
                        assertTrue(queried.sameIdentity(reference))
                    }
                }
            }
        } finally {
            IUnknownReference(pointer.asRawComPtr(), IID.NullableString).close()
        }
    }

    @Test
    fun property_value_projection_hosts_expose_cswinrt_ccw_suffix_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()

        val pointer = WinRtPropertyValueProjection.fromManaged("projection-runtime")
        try {
            IUnknownReference(pointer.asRawComPtr(), IID.IPropertyValue, preventReleaseOnDispose = true).use { reference ->
                listOf(
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IReferenceTrackerTarget,
                ).forEach { iid ->
                    reference.queryInterface(iid).getOrThrow().use { queried ->
                        assertTrue(queried.sameIdentity(reference))
                    }
                }
            }
        } finally {
            IUnknownReference(pointer.asRawComPtr(), IID.IPropertyValue).close()
        }
    }

    @Test
    fun runtime_hooks_project_uri_and_iclosable_like_cswinrt() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val uri = WinRtUri("https://example.com/runtime-117?value=1")
        val uriPointer = ComWrappersSupport.createCCWForObject(uri, IID.IInspectable).useAndGetRef()
        try {
            assertEquals(uri, ComWrappersSupport.createRcwForComObject(uriPointer))
            IInspectableReference(uriPointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { inspectable ->
                assertEquals("Windows.Foundation.Uri", inspectable.getRuntimeClassName())
            }
        } finally {
            IUnknownReference(uriPointer.asRawComPtr(), IID.IInspectable).close()
        }

        val closeable = TestCloseable()
        val closeablePointer = ComWrappersSupport.createCCWForObject(closeable, IID.IInspectable).useAndGetRef()
        try {
            IInspectableReference(closeablePointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { inspectable ->
                assertEquals("Windows.Foundation.IClosable", inspectable.getRuntimeClassName())
            }
            val projected =
                ComWrappersSupport.createRcwForComObject(
                    closeablePointer,
                    WinRtTypeHandle(TypeNameSupport.getNameForType(AutoCloseable::class), IID.IDisposable),
                ) as AutoCloseable
            projected.close()
            assertTrue(closeable.closed)
        } finally {
            IUnknownReference(closeablePointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun bindable_object_marshaler_unboxes_property_value_boxes() {
        ComWrappersSupport.clearRegistriesForTests()

        val pointer = ComWrappersSupport.createCCWForObject(9.seconds, IID.IInspectable).useAndGetRef()
        try {
            assertEquals(9.seconds, WinRtBindableObjectMarshaller.fromBorrowedAbi(pointer))
        } finally {
            IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun object_marshaler_unboxes_native_like_property_value_strings() {
        ComWrappersSupport.clearRegistriesForTests()

        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(createPropertyValueInterfaceDefinition("button content")),
            defaultInterfaceId = IID.IPropertyValue,
            runtimeClassName = WinRtValueBoxing.boxedRuntimeClassNameForType(String::class),
        )
        val reference = host.createReference(IID.IInspectable)
        try {
            assertEquals("button content", WinRtObjectMarshaller.fromAbi(reference.pointer.asRawAddress()))
        } finally {
            reference.close()
            host.close()
        }
    }

    @Test
    fun boxed_reference_runtime_names_round_trip_type_exception_and_enum_values() {
        ComWrappersSupport.clearRegistriesForTests()
        registerEnumDescriptors()
        ComWrappersSupport.registerProjectionAssembly(TestPriority::class, TestVisibility::class)

        roundTripInspectable(Marshaler.inspectableAny(), String::class) { expected, actual ->
            assertEquals(expected, actual)
        }

        val exceptionPointer = ComWrappersSupport.createCCWForObject(IllegalStateException("boom"), IID.IInspectable).useAndGetRef()
        try {
            val projected = ComWrappersSupport.createRcwForComObject(exceptionPointer) as Exception
            assertEquals(ExceptionProjection.toAbi(IllegalStateException("boom")), ExceptionProjection.toAbi(projected))
        } finally {
            IUnknownReference(exceptionPointer.asRawComPtr(), IID.IInspectable).close()
        }

        val priorityPointer = ComWrappersSupport.createCCWForObject(TestPriority.High, IID.IInspectable).useAndGetRef()
        try {
            IUnknownReference(priorityPointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use { inspectable ->
                inspectable.queryInterface(priorityNullableIid()).getOrThrow().close()
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().use { propertyValue ->
                    val projected = WinRtPropertyValueReference(propertyValue.pointer.asRawAddress(), preventReleaseOnDispose = true)
                    assertEquals(2, projected.getValue())
                }
            }
            assertEquals(TestPriority.High, ComWrappersSupport.createRcwForComObject(priorityPointer))
            assertEquals(TestPriority.High, WinRtBindableObjectMarshaller.fromBorrowedAbi(priorityPointer))
        } finally {
            IUnknownReference(priorityPointer.asRawComPtr(), IID.IInspectable).close()
        }

        val visibilityPointer = ComWrappersSupport.createCCWForObject(TestVisibility.Visible, IID.IInspectable).useAndGetRef()
        try {
            IUnknownReference(visibilityPointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use { inspectable ->
                inspectable.queryInterface(visibilityNullableIid()).getOrThrow().close()
                inspectable.queryInterface(IID.IPropertyValue).getOrThrow().use { propertyValue ->
                    val projected = WinRtPropertyValueReference(propertyValue.pointer.asRawAddress(), preventReleaseOnDispose = true)
                    assertEquals(1u, projected.getValue())
                }
            }
            assertEquals(TestVisibility.Visible, ComWrappersSupport.createRcwForComObject(visibilityPointer))
            assertEquals(TestVisibility.Visible, WinRtBindableObjectMarshaller.fromBorrowedAbi(visibilityPointer))
        } finally {
            IUnknownReference(visibilityPointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    private fun <T : Any> roundTripInspectable(
        marshaler: Marshaler<Any?>,
        value: T,
        assertRoundTrip: (T, T) -> Unit,
    ) {
        val abi = marshaler.fromManaged(value) as NativePointer
        try {
            @Suppress("UNCHECKED_CAST")
            assertRoundTrip(value, marshaler.fromAbi(abi) as T)
        } finally {
            marshaler.disposeAbi(abi)
        }
    }

    private fun assertInspectableGetIids(
        pointer: RawAddress,
        interfaceId: Guid,
        expected: List<Guid>,
    ) {
        IInspectableReference(pointer.asRawComPtr(), interfaceId, preventReleaseOnDispose = true).use { inspectable ->
            PlatformAbi.confinedScope().use { scope ->
                val countOut = PlatformAbi.allocateInt32Slot(scope)
                val idsOut = PlatformAbi.allocatePointerSlot(scope)
                val hr = ComVtableInvoker.invokeArgs(
                    instance = inspectable.pointer,
                    slot = IInspectableVftblSlots.GetIids,
                    arg0 = countOut,
                    arg1 = idsOut,
                )
                HResult(hr).requireSuccess()
                val ids = PlatformAbi.readPointer(idsOut)
                try {
                    val count = PlatformAbi.readInt32(countOut)
                    val actual = (0 until count).map { index ->
                        PlatformAbi.readGuid(
                            PlatformAbi.slice(
                                ids,
                                index.toLong() * Guid.BYTE_SIZE,
                                Guid.BYTE_SIZE.toLong(),
                            ),
                        )
                    }
                    assertEquals(expected, actual)
                } finally {
                    WinRtPlatformApi.coTaskMemFreeRaw(ids)
                }
            }
        }
    }

    private fun registerProjectedPointBoxing() {
        WinRtValueBoxingRegistration.registerStruct(
            type = ProjectedPoint::class,
            projectedTypeName = "Windows.Foundation.Point",
            signature = "struct(Windows.Foundation.Point;f4;f4)",
            adapter = ProjectedPoint.Metadata,
            arrayType = emptyArray<ProjectedPoint>()::class,
        )
    }

    private data class ProjectedPoint(
        val x: Float,
        val y: Float,
    ) {
        companion object Metadata : NativeStructAdapter<ProjectedPoint> {
            override val layout: NativeStructLayout =
                NativeStructLayout.sequential(
                    NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                    NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
                )

            override fun read(source: RawAddress): ProjectedPoint =
                ProjectedPoint(
                    x = PlatformAbi.readFloat(layout.slice(source, "x")),
                    y = PlatformAbi.readFloat(layout.slice(source, "y")),
                )

            override fun write(value: ProjectedPoint, destination: RawAddress) {
                PlatformAbi.writeFloat(layout.slice(destination, "x"), value.x)
                PlatformAbi.writeFloat(layout.slice(destination, "y"), value.y)
            }
        }
    }

    private class TestCloseable : AutoCloseable {
        var closed: Boolean = false

        override fun close() {
            closed = true
        }
    }

    private enum class TestPriority(
        val abiValue: Int,
    ) {
        Low(0),
        High(2),
    }

    private enum class TestVisibility(
        val abiValue: UInt,
    ) {
        Hidden(0u),
        Visible(1u),
        Archived(2u),
    }

    private fun registerEnumDescriptors() {
        WinRtTypeRegistry.register<TestPriority>(
            projectedTypeName = "Contoso.Priority",
            signature = "enum(Contoso.Priority;i4)",
            enumAbiValue = { it.abiValue },
            enumEntries = TestPriority.entries.toTypedArray(),
            isWindowsRuntimeType = true,
        )
        WinRtTypeRegistry.register<TestVisibility>(
            projectedTypeName = "Contoso.Visibility",
            signature = "enum(Contoso.Visibility;u4)",
            enumAbiValue = { it.abiValue.toInt() },
            enumEntries = TestVisibility.entries.toTypedArray(),
            isWindowsRuntimeType = true,
        )
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
