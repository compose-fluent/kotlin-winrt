package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComWrappersSupportTest {
    @Test
    fun authoring_metadata_type_lookup_returns_registered_mapping() {
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.registerAuthoringMetadataTypeMappings(
            mapOf("Sample.Foundation.Widget" to "ABI.Sample.Foundation.Widget"),
        )

        assertEquals(
            "ABI.Sample.Foundation.Widget",
            ComWrappersSupport.getAuthoringMetadataTypeName("Sample.Foundation.Widget"),
        )
        assertEquals(null, ComWrappersSupport.getAuthoringMetadataTypeName("Sample.Foundation.Missing"))
    }

    @Test
    fun create_rcw_uses_runtime_class_factory_and_caches_wrapper_identity() {
        ComWrappersSupport.clearRegistriesForTests()
        val created = mutableListOf<TestRuntimeClassWrapper>()
        ComWrappersSupport.registerRuntimeClassFactory("test.RuntimeClass") { inspectable ->
            TestRuntimeClassWrapper(inspectable).also(created::add)
        }

        val host = WinRtInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = "test.RuntimeClass",
        )
        val pointer = host.detachReference(IID.IInspectable)

        val first = ComWrappersSupport.createRcwForComObject(pointer) as TestRuntimeClassWrapper
        val second = ComWrappersSupport.createRcwForComObject(pointer) as TestRuntimeClassWrapper

        assertSame(first, second)
        assertEquals(1, created.size)
        assertEquals("test.RuntimeClass", first.nativeObject.asInspectable().use { it.getRuntimeClassName() })
        first.nativeObject.close()
    }

    @Test
    fun create_ccw_reuses_identity_for_same_managed_object() {
        ComWrappersSupport.clearRegistriesForTests()
        val value = Any()

        val first = ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
        val second = ComWrappersSupport.createCCWForObject(value, IID.IInspectable)

        try {
            assertEquals(
                PlatformAbi.pointerKey(first.pointer),
                PlatformAbi.pointerKey(second.pointer),
            )
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun detached_ccw_keeps_same_managed_object_identity_cached_until_native_release() {
        ComWrappersSupport.clearRegistriesForTests()
        val value = Any()
        val detached = ComWrappersSupport.detachCCWForObject(value, IID.IInspectable)

        try {
            ComWrappersSupport.createCCWForObject(value, IID.IInspectable).use { reference ->
                assertEquals(
                    PlatformAbi.pointerKey(detached),
                    PlatformAbi.pointerKey(reference.pointer),
                )
            }
        } finally {
            ComObjectReference(detached.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun create_rcw_cache_uses_com_identity_across_interface_pointers() {
        ComWrappersSupport.clearRegistriesForTests()
        val defaultInterfaceId = Guid("66666666-6666-6666-6666-666666666666")
        val secondaryInterfaceId = Guid("77777777-7777-7777-7777-777777777777")
        val created = mutableListOf<TestRuntimeClassWrapper>()
        ComWrappersSupport.registerRuntimeClassFactory("test.RuntimeClass") { inspectable ->
            TestRuntimeClassWrapper(inspectable).also(created::add)
        }
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRtInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
            ),
            runtimeClassName = "test.RuntimeClass",
        )
        val defaultPointer = host.detachReference(defaultInterfaceId)
        val secondaryPointer = IUnknownReference(defaultPointer.asRawComPtr(), defaultInterfaceId, preventReleaseOnDispose = true)
            .queryInterface(secondaryInterfaceId)
            .getOrThrow()

        val first = ComWrappersSupport.createRcwForComObject(defaultPointer) as TestRuntimeClassWrapper
        val second = ComWrappersSupport.createRcwForComObject(PlatformAbi.fromRawComPtr(secondaryPointer.pointer)) as TestRuntimeClassWrapper

        assertSame(first, second)
        assertEquals(1, created.size)
        secondaryPointer.close()
        first.nativeObject.close()
    }

    @Test
    fun registered_com_interface_object_wins_rcw_identity_lookup() {
        ComWrappersSupport.clearRegistriesForTests()
        val defaultInterfaceId = Guid("46464646-4646-4646-4646-464646464646")
        val secondaryInterfaceId = Guid("47474747-4747-4747-4747-474747474747")
        val managed = TestManagedType("registered")
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRtInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
            ),
            runtimeClassName = "test.Registered",
        )
        val defaultPointer = host.detachReference(defaultInterfaceId)
        val secondaryPointer = IUnknownReference(defaultPointer.asRawComPtr(), defaultInterfaceId, preventReleaseOnDispose = true)
            .queryInterface(secondaryInterfaceId)
            .getOrThrow()

        ComWrappersSupport.registerObjectForComInterface(managed, defaultPointer)

        assertSame(managed, ComWrappersSupport.createRcwForComObject(defaultPointer))
        assertSame(managed, ComWrappersSupport.createRcwForComObject(PlatformAbi.fromRawComPtr(secondaryPointer.pointer)))
        secondaryPointer.close()
        IUnknownReference(defaultPointer.asRawComPtr(), defaultInterfaceId).close()
    }

    @Test
    fun detach_ccw_for_object_returns_owned_abi_pointer() {
        val managed = TestManagedType("detached")

        val pointer = ComWrappersSupport.detachCCWForObject(managed, IID.IInspectable)
        assertFalse(PlatformAbi.isNull(pointer))

        IUnknownReference(pointer.asRawComPtr(), IID.IInspectable).close()
    }

    @Test
    fun detach_ccw_for_object_returns_null_pointer_for_null_value() {
        assertTrue(PlatformAbi.isNull(ComWrappersSupport.detachCCWForObject(null, IID.IInspectable)))
    }

    @Test
    fun projection_marshaler_creates_ccw_for_non_unwrappable_winrt_object() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("48484848-4848-4848-4848-484848484848")
        ComWrappersSupport.registerAuthoringTypeDetailsFactory(NonUnwrappableProjectionWrapper::class) {
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = interfaceId,
                runtimeClassName = "test.AuthoredProjectionWrapper",
            )
        }
        val nativeHost = WinRtInspectableComObject.inspectableBox(
            value = "native",
            runtimeClassName = "test.Native",
        )
        val value = NonUnwrappableProjectionWrapper(nativeHost.detachReference(IID.IInspectable))

        winRtProjectionMarshaler(value, "test.AuthoredProjectionWrapper", interfaceId).use { marshaler ->
            assertFalse(PlatformAbi.isNull(marshaler.abi))
        }
        value.nativeObject.close()
    }

    @Test
    fun aggregated_reference_query_interface_releases_temporary_qi_reference_and_wraps_borrowed_pointer() {
        val defaultInterfaceId = Guid("26262626-2626-2626-2626-262626262626")
        val secondaryInterfaceId = Guid("27272727-2727-2727-2727-272727272727")
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRtInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = "test.Aggregated",
        )
        val pointer = host.detachReference(defaultInterfaceId)
        val source = IInspectableReference(
            pointer = pointer.asRawComPtr(),
            interfaceId = defaultInterfaceId,
            preventReleaseOnDispose = true,
            isAggregated = true,
        )

        val secondary = source.queryInterface(secondaryInterfaceId).getOrThrow()

        assertTrue(secondary.isAggregated)
        secondary.close()
        assertEquals(0u, source.release())
        source.close()
    }

    @Test
    fun create_rcw_uses_static_type_and_helper_type_registration() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceType = WinRtTypeHandle("test.IFoo", Guid("11111111-1111-1111-1111-111111111111"))
        val helperType = WinRtTypeHandle("test.IFoo.Helper", Guid("22222222-2222-2222-2222-222222222222"))
        ComWrappersSupport.registerHelperType(interfaceType, helperType)
        ComWrappersSupport.registerTypedRcwFactory(helperType) { inspectable ->
            TestTypedWrapper(helperType, inspectable)
        }

        val host = WinRtInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = "test.RuntimeClass",
        )
        val pointer = host.detachReference(IID.IInspectable)
        val wrapper = ComWrappersSupport.createRcwForComObject(pointer, interfaceType) as TestTypedWrapper

        assertEquals(helperType, wrapper.primaryTypeHandle)
        assertEquals("test.RuntimeClass", wrapper.nativeObject.asInspectable().use { it.getRuntimeClassName() })
        wrapper.nativeObject.close()
    }

    @Test
    fun create_rcw_falls_back_to_single_interface_optimized_object_for_inspectable_ptr_without_registered_factory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("34343434-3434-3434-3434-343434343434")
        val typeHandle = WinRtTypeHandle("test.IFallback", interfaceId)
        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = emptyList(),
                ),
            ),
            runtimeClassName = "test.Fallback",
        )

        val pointer = host.detachReference(interfaceId)
        val wrapper = ComWrappersSupport.createRcwForComObject(pointer, typeHandle) as SingleInterfaceOptimizedObject

        assertEquals(typeHandle, wrapper.primaryTypeHandle)
        assertEquals(interfaceId, wrapper.nativeObject.interfaceId)
        assertFalse(wrapper.hasUnwrappableNativeObject)
        assertSame(wrapper.nativeObject, wrapper.getObjectReferenceForType(typeHandle))
        wrapper.nativeObject.close()
    }

    @Test
    fun create_ccw_and_find_object_use_registered_factory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("44444444-4444-4444-4444-444444444444")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) { value ->
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = interfaceId,
                runtimeClassName = "test.ManagedType",
            )
        }

        val managed = TestManagedType("payload")
        ComWrappersSupport.createCCWForObject(managed, interfaceId).use { ccw ->
            val pointer = PlatformAbi.fromRawComPtr(ccw.pointer)
            val found = ComWrappersSupport.findObject(pointer, TestManagedType::class)
            val info = ComWrappersSupport.getInspectableInfo(pointer) ?: error("Expected inspectable info for managed type CCW.")

            assertSame(managed, found)
            assertEquals("test.ManagedType", info.runtimeClassName)
            assertTrue(info.interfaceIds.contains(interfaceId))
        }
    }

    @Test
    fun composable_ccw_resolves_requested_base_interface_from_inner_after_factory_returns() {
        ComWrappersSupport.clearRegistriesForTests()
        val overrideInterfaceId = Guid("31313131-3131-3131-3131-313131313131")
        val baseDefaultInterfaceId = Guid("32323232-3232-3232-3232-323232323232")
        ComWrappersSupport.registerCcwFactory(TestComposableManagedType::class) {
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = overrideInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = overrideInterfaceId,
                runtimeClassName = "test.ComposableDerived",
            )
        }
        val innerHost = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = baseDefaultInterfaceId,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = baseDefaultInterfaceId,
            runtimeClassName = "test.ComposableBase",
        )
        val managed = TestComposableManagedType("derived")

        ComWrappersSupport.createComposableCCWForObject(
            value = managed,
            outerInterfaceId = baseDefaultInterfaceId,
        ) { baseInterface, innerOut, instanceOut ->
            IInspectableReference(baseInterface.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { base ->
                assertEquals("test.ComposableDerived", base.getRuntimeClassName())
            }
            PlatformAbi.writePointer(innerOut, innerHost.detachReference(IID.IInspectable))
            PlatformAbi.writePointer(instanceOut, innerHost.detachReference(baseDefaultInterfaceId))
            KnownHResults.S_OK.value
        }.use { composed ->
            managed.composableReference = composed
            assertEquals(overrideInterfaceId, composed.outer.interfaceId)
            assertSame(
                managed,
                ComWrappersSupport.findObject(
                    PlatformAbi.fromRawComPtr(composed.outer.pointer),
                    TestComposableManagedType::class,
                ),
            )
            ComWrappersSupport.createCCWForObject(managed, baseDefaultInterfaceId).use { marshaled ->
                assertTrue(marshaled.sameIdentity(composed.inner ?: error("Expected aggregated inner reference.")))
            }
        }
    }

    @Test
    fun composable_ccw_initializes_reference_tracker_for_non_aggregated_factory_instance() {
        ComWrappersSupport.clearRegistriesForTests()
        val primaryInspectableId = Guid("33333333-3333-3333-3333-333333333333")
        val defaultInterfaceId = Guid("34343434-3434-3434-3434-343434343434")
        ComWrappersSupport.registerCcwFactory(TestComposableManagedType::class) {
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = defaultInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = defaultInterfaceId,
                runtimeClassName = "test.ReferenceTrackedComposable",
            )
        }
        val managed = TestComposableManagedType("tracked")
        val instanceHost = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = primaryInspectableId,
                    methods = emptyList(),
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = defaultInterfaceId,
                    methods = emptyList(),
                ),
            ),
            hiddenInterfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IReferenceTracker,
                    methods = List(9) { WinRtInspectableMethodDefinition(ComMethodSignatures.HResult) { 1 } },
                ),
            ),
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = "test.ReferenceTrackedInstance",
        )
        var returnedInstancePointerKey: Long? = null

        ComWrappersSupport.createComposableCCWForObject(
            value = managed,
            outerInterfaceId = null,
            instanceInterfaceId = defaultInterfaceId,
        ) { _, innerOut, instanceOut ->
            PlatformAbi.writePointer(innerOut, PlatformAbi.nullPointer)
            val returnedInstancePointer = instanceHost.detachReference(IID.IInspectable)
            returnedInstancePointerKey = PlatformAbi.pointerKey(returnedInstancePointer)
            PlatformAbi.writePointer(instanceOut, returnedInstancePointer)
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(defaultInterfaceId, composed.instance.interfaceId)
            assertNotEquals(returnedInstancePointerKey, PlatformAbi.pointerKey(PlatformAbi.fromRawComPtr(composed.instance.pointer)))
            assertTrue(composed.instance.hasReferenceTracker)
        }
    }

    @Test
    fun ccw_augmentation_preserves_existing_hidden_interfaces_and_appends_reference_tracker_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()
        val publicInterfaceId = Guid("67676767-6767-6767-6767-676767676767")
        val hiddenInterfaceId = Guid("68686868-6868-6868-6868-686868686868")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) {
            WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = publicInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                hiddenInterfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = hiddenInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = publicInterfaceId,
                runtimeClassName = "test.ManagedType",
            )
        }

        ComWrappersSupport.createCCWForObject(TestManagedType("payload"), publicInterfaceId).use { ccw ->
            ccw.queryInterface(hiddenInterfaceId).getOrThrow().use { hidden ->
                assertTrue(hidden.sameIdentity(ccw))
            }
            ccw.queryInterface(IID.IReferenceTrackerTarget).getOrThrow().use { trackerTarget ->
                assertTrue(trackerTarget.sameIdentity(ccw))
            }
            ccw.queryInterface(IID.IReferenceTrackerExtension).getOrThrow().use { trackerExtension ->
                assertTrue(trackerExtension.sameIdentity(ccw))
            }
            val info = ComWrappersSupport.getInspectableInfo(PlatformAbi.fromRawComPtr(ccw.pointer))
            assertEquals(
                listOf(
                    publicInterfaceId,
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IUnknown,
                ),
                info?.interfaceIds,
            )
        }
    }

    @Test
    fun cast_extension_rehydrates_registered_typed_wrapper() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceType = WinRtTypeHandle("test.IFoo", Guid("55555555-5555-5555-5555-555555555555"))
        ComWrappersSupport.registerTypedRcwFactory(interfaceType) { inspectable ->
            TestTypedWrapper(interfaceType, inspectable)
        }

        val projected = ProjectedInspectableObject(
            pointer = WinRtInspectableComObject.inspectableBox("payload", "test.RuntimeClass")
                .detachReference(IID.IInspectable)
        )

        val cast = projected.winrtAs(interfaceType) as TestTypedWrapper
        assertEquals(interfaceType, cast.primaryTypeHandle)
        cast.nativeObject.close()
        projected.nativeObject.close()
    }

    private data class TestManagedType(val name: String)

    private class TestComposableManagedType(val name: String) : WinRtComposableObject {
        var composableReference: WinRtComposableObjectReference? = null

        override val winRtComposableObjectReference: WinRtComposableObjectReference?
            get() = composableReference
    }

    private class ProjectedInspectableObject(
        pointer: RawAddress,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
    }

    private class NonUnwrappableProjectionWrapper(
        pointer: RawAddress,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
        override val hasUnwrappableNativeObject: Boolean = false
    }

    private class TestRuntimeClassWrapper(
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private class TestTypedWrapper(
        override val primaryTypeHandle: WinRtTypeHandle,
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }
}
