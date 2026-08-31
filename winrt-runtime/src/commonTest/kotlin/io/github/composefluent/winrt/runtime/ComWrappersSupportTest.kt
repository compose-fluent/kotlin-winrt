package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComWrappersSupportTest {
    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun managed_projection_state_binding_reuses_last_abi_and_invalidates_it() {
        val expectedInterfaceId = Guid("11111111-1111-1111-1111-111111111111")
        val otherInterfaceId = Guid("22222222-2222-2222-2222-222222222222")
        val expectedAbi = RawAddress(0x1234L)
        var sourceCalls = 0
        val source = object : WinRTManagedProjectionAbiSource {
            override fun tryBorrowAbi(interfaceId: Guid): RawAddress {
                sourceCalls += 1
                return if (interfaceId == expectedInterfaceId) expectedAbi else RawAddress.Null
            }

            override fun tryAcquireCallLease(
                knownManagedValue: Any,
                interfaceId: Guid,
            ): WinRTProjectionMarshaler? = null
        }
        val state = WinRTManagedProjectionState()
        state.binding.store(WinRTManagedProjectionStateBinding(generation = 0, cache = source))

        assertEquals(expectedAbi, state.tryBorrowAbi(expectedInterfaceId))
        assertEquals(expectedAbi, state.tryBorrowAbi(expectedInterfaceId))
        assertEquals(1, sourceCalls)

        assertEquals(RawAddress.Null, state.tryBorrowAbi(otherInterfaceId))
        assertEquals(2, sourceCalls)
        assertEquals(expectedAbi, state.tryBorrowAbi(expectedInterfaceId))
        assertEquals(2, sourceCalls)

        state.invalidateBorrowedAbi(source)
        assertEquals(expectedAbi, state.tryBorrowAbi(expectedInterfaceId))
        assertEquals(3, sourceCalls)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun managed_projection_state_binding_reuses_call_guard_and_invalidates_it() {
        val interfaceId = Guid("33333333-3333-3333-3333-333333333333")
        val managedValue = Any()
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = interfaceId,
            managedValue = managedValue,
            weakManagedValue = true,
        )
        var sourceCalls = 0
        val source = object : WinRTManagedProjectionAbiSource {
            override fun tryBorrowAbi(interfaceId: Guid): RawAddress = RawAddress.Null

            override fun tryAcquireCallLease(
                knownManagedValue: Any,
                interfaceId: Guid,
            ): WinRTProjectionMarshaler? {
                sourceCalls += 1
                return host.tryAcquireStaticCallLease(interfaceId, knownManagedValue)
            }
        }
        val state = WinRTManagedProjectionState()
        state.binding.store(WinRTManagedProjectionStateBinding(generation = 0, cache = source))

        val first = state.tryAcquireCallLease(managedValue, interfaceId)
        assertEquals(1, sourceCalls)
        assertEquals(host.tryBorrowCachedInterfacePointer(interfaceId), first?.abi)
        first?.releaseManagedCall(managedValue)

        val second = state.tryAcquireCallLease(managedValue, interfaceId)
        assertSame(first, second)
        assertEquals(1, sourceCalls)
        WinRTPlatformApi.addRefRaw(second?.abi ?: error("Expected the cached call guard."))
        second?.releaseManagedCall(managedValue)
        assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(second.abi))
        WinRTPlatformApi.releaseRaw(second.abi)
        assertEquals(1u, WinRTInspectableComObject.tryProbeReferenceCount(second.abi))

        state.invalidateBorrowedAbi(source)
        val third = state.tryAcquireCallLease(managedValue, interfaceId)
        assertSame(first, third)
        assertEquals(2, sourceCalls)
        third?.releaseManagedCall(managedValue)
        host.close()
    }

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

        val host = WinRTInspectableComObject.inspectableBox(
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
    fun owned_delegate_rcw_reuses_projection_identity_and_consumes_duplicate_abi_reference() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("45454545-4545-4545-4545-454545454545")
        val typeHandle = WinRTTypeHandle("test.TestDelegate", interfaceId)
        val descriptor = WinRTDelegateDescriptor(
            interfaceId = interfaceId,
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.UNIT,
        )
        val handle = WinRTDelegateBridge.createUnitDelegate(interfaceId, emptyList()) { }
        var wrappersCreated = 0
        val factory: (IUnknownReference, WinRTTypeHandle) -> TestDelegateWrapper = { reference, projectedType ->
            wrappersCreated += 1
            TestDelegateWrapper(
                primaryTypeHandle = projectedType,
                delegateReference = WinRTDelegateReference.fromOwnedReference(reference, descriptor),
            )
        }
        fun detachDelegateReference(): RawAddress =
            handle.createReference().use { reference ->
                PlatformAbi.fromRawComPtr(reference.getRefPointer())
            }

        var projected: TestDelegateWrapper? = null
        try {
            val first = requireNotNull(
                ComWrappersSupport.createRcwForOwnedComObject(
                    pointer = detachDelegateReference(),
                    staticallyDeterminedType = typeHandle,
                    factory = factory,
                ),
            )
            projected = first
            val canonicalPointer = PlatformAbi.fromRawComPtr(first.nativeObject.pointer)
            val retainedReferenceCount = requireNotNull(
                WinRTInspectableComObject.tryProbeReferenceCount(canonicalPointer),
            )
            val hotProbePointer = detachDelegateReference()
            assertEquals(
                retainedReferenceCount + 1u,
                WinRTInspectableComObject.tryProbeReferenceCount(hotProbePointer),
            )

            val hotProbe = requireNotNull(
                ComWrappersSupport.tryConsumeCachedRcwForOwnedComObject<TestDelegateWrapper>(
                    pointer = hotProbePointer,
                    staticallyDeterminedType = typeHandle,
                ),
            )

            assertSame(first, hotProbe)
            assertEquals(
                retainedReferenceCount,
                WinRTInspectableComObject.tryProbeReferenceCount(canonicalPointer),
            )

            val duplicatePointer = detachDelegateReference()
            assertEquals(
                retainedReferenceCount + 1u,
                WinRTInspectableComObject.tryProbeReferenceCount(duplicatePointer),
            )

            val second = requireNotNull(
                ComWrappersSupport.createRcwForOwnedComObject(
                    pointer = duplicatePointer,
                    staticallyDeterminedType = typeHandle,
                    factory = factory,
                ),
            )

            assertSame(first, second)
            assertEquals(1, wrappersCreated)
            assertEquals(
                retainedReferenceCount,
                WinRTInspectableComObject.tryProbeReferenceCount(canonicalPointer),
            )

            first.nativeObject.close()
            val replacement = requireNotNull(
                ComWrappersSupport.createRcwForOwnedComObject(
                    pointer = detachDelegateReference(),
                    staticallyDeterminedType = typeHandle,
                    factory = factory,
                ),
            )
            projected = replacement

            assertNotSame(first, replacement)
            assertEquals(2, wrappersCreated)
            assertFalse(replacement.nativeObject.isDisposed)
        } finally {
            projected?.nativeObject?.close()
            handle.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun typed_owned_rcw_does_not_reuse_incompatible_registered_object() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("49494949-4949-4949-4949-494949494949")
        val typeHandle = WinRTTypeHandle("test.ITypedOwned", interfaceId)
        val managed = TestManagedType("registered")
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = interfaceId,
        )
        val pointer = host.detachReference(interfaceId)
        ComWrappersSupport.registerObjectForComInterface(managed, pointer)

        val wrapper = requireNotNull(
            ComWrappersSupport.createRcwForOwnedComObject(
                pointer = pointer,
                staticallyDeterminedType = typeHandle,
                factory = { reference, projectedType ->
                    SingleInterfaceOptimizedObject(projectedType, reference)
                },
            ),
        )

        try {
            assertNotSame(managed as Any, wrapper as Any)
            assertEquals(typeHandle, wrapper.primaryTypeHandle)
            assertFalse(wrapper.nativeObject.isDisposed)
        } finally {
            wrapper.nativeObject.close()
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun directly_constructed_runtime_class_wrapper_can_register_its_com_identity() {
        ComWrappersSupport.clearRegistriesForTests()
        val host = WinRTInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = "test.DirectRuntimeClass",
        )
        val wrapper = TestRuntimeClassWrapper(
            IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr(), IID.IInspectable),
        )

        try {
            ComWrappersSupport.registerRuntimeClassWrapper(wrapper, wrapper.nativeObject)

            assertSame(
                wrapper,
                ComWrappersSupport.createRcwForComObject(
                    PlatformAbi.fromRawComPtr(wrapper.nativeObject.pointer),
                ),
            )
        } finally {
            wrapper.nativeObject.close()
        }
    }

    @Test
    fun generated_wrap_replaces_disposed_cached_runtime_class() {
        ComWrappersSupport.clearRegistriesForTests()
        val runtimeClassName = "test.DisposedCachedRuntimeClass"
        val wrap: (IInspectableReference) -> TestRuntimeClassWrapper = { instance ->
            val cached = ComWrappersSupport.findObject(
                PlatformAbi.fromRawComPtr(instance.pointer),
                TestRuntimeClassWrapper::class,
            )
            if (cached != null) {
                instance.close()
                cached
            } else {
                TestRuntimeClassWrapper(instance)
            }
        }
        ComWrappersSupport.registerRuntimeClassFactory(runtimeClassName, wrap)

        val host = WinRTInspectableComObject.inspectableBox(
            value = "payload",
            runtimeClassName = runtimeClassName,
        )
        val cached = ComWrappersSupport.createRcwForComObject(
            host.detachReference(IID.IInspectable),
        ) as TestRuntimeClassWrapper
        val freshReference = IInspectableReference(cached.nativeObject.getRefPointer(), IID.IInspectable)
        cached.nativeObject.close()
        var wrappedAgain: TestRuntimeClassWrapper? = null

        try {
            wrappedAgain = wrap(freshReference)

            assertFalse(wrappedAgain === cached)
            assertSame(freshReference, wrappedAgain.nativeObject)
            assertFalse(wrappedAgain.nativeObject.isDisposed)
        } finally {
            wrappedAgain?.nativeObject?.close()
            if (!freshReference.isDisposed) {
                freshReference.close()
            }
            cached.nativeObject.close()
        }
    }

    @Test
    fun create_ccw_reuses_identity_after_all_external_references_are_closed() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("43434343-4343-4343-4343-434343434343")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = interfaceId,
            )
        }
        val value = TestManagedType("cached")

        val firstPointer = ComWrappersSupport.createCCWForObject(value, interfaceId).use { first ->
            PlatformAbi.pointerKey(first.pointer)
        }
        val secondPointer = ComWrappersSupport.createCCWForObject(value, interfaceId).use { second ->
            PlatformAbi.pointerKey(second.pointer)
        }

        assertEquals(firstPointer, secondPointer)
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
    fun detached_ccw_pins_managed_value_while_native_reference_exists() {
        ComWrappersSupport.clearRegistriesForTests()
        val retained = createDetachedManagedValue()

        try {
            repeat(3) {
                PlatformFinalization.drain()
                assertTrue(retained.value.get() != null)
            }
        } finally {
            ComObjectReference(retained.pointer.asRawComPtr(), retained.interfaceId).close()
        }
    }

    @Test
    fun cached_ccw_does_not_permanently_retain_managed_value() {
        ComWrappersSupport.clearRegistriesForTests()
        val value = cacheManagedValueWithoutExternalReference()

        drainUntilCleared(value)
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
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
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
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
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
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = interfaceId,
                runtimeClassName = "test.AuthoredProjectionWrapper",
            )
        }
        val nativeHost = WinRTInspectableComObject.inspectableBox(
            value = "native",
            runtimeClassName = "test.Native",
        )
        val value = NonUnwrappableProjectionWrapper(nativeHost.detachReference(IID.IInspectable))

        val marshaler = winRTManagedProjectionMarshalerOrNull(
            value,
            WinRTTypeHandle("test.AuthoredProjectionWrapper", interfaceId),
        )
        assertTrue(marshaler != null)
        marshaler.use {
            assertFalse(PlatformAbi.isNull(marshaler.abi))
        }
        value.nativeObject.close()
    }

    @Test
    fun projection_marshaler_uses_common_call_lease_for_rooted_cached_ccw() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("49494949-4949-4949-4949-494949494949")
        registerWeakCachedTestManagedType(interfaceId)
        val value = TestManagedType("marshaling-owned")
        val first = winRTProjectionMarshaler(
            value,
            WinRTTypeHandle("test.IManagedMarshaling", interfaceId),
        )
        val second = winRTProjectionMarshaler(
            value,
            WinRTTypeHandle("test.IManagedMarshaling", interfaceId),
        )
        val abi = first.abi
        val typeHandle = WinRTTypeHandle("test.IManagedMarshaling", interfaceId)

        assertNotSame(first, second)
        assertEquals(first.abi, second.abi)
        val expectedLeasedCount = 1u
        assertEquals(expectedLeasedCount, WinRTInspectableComObject.tryProbeReferenceCount(abi))
        assertEquals(abi, tryBorrowWinRTManagedProjectionAbi(value, typeHandle))
        assertEquals(expectedLeasedCount, WinRTInspectableComObject.tryProbeReferenceCount(abi))
        WinRTPlatformApi.addRefRaw(abi)
        assertEquals(expectedLeasedCount + 1u, WinRTInspectableComObject.tryProbeReferenceCount(abi))

        first.close()
        second.close()
        try {
            assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(abi))
            assertSame(value, ComWrappersSupport.findObject(abi, TestManagedType::class))
        } finally {
            WinRTPlatformApi.releaseRaw(abi)
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun projected_call_lease_reuses_cached_definition_without_a_per_call_facade() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("4E4E4E4E-4E4E-4E4E-4E4E-4E4E4E4E4E4E")
        registerWeakCachedTestManagedType(interfaceId)
        val value = TestManagedType("compiler-call-lease")
        val typeHandle = WinRTTypeHandle("test.IManagedCallLease", interfaceId)
        ComWrappersSupport.createCCWForObject(value, interfaceId).close()

        val first = tryAcquireWinRTManagedProjectionCallLease(value, typeHandle)
            ?: error("Expected the warmed managed CCW call lease.")
        val second = tryAcquireWinRTManagedProjectionCallLease(value, typeHandle)
            ?: error("Expected the cached managed CCW call lease.")
        val abi = first.abi

        assertSame(first, second)
        assertEquals(abi, second.abi)
        assertEquals(1u, WinRTInspectableComObject.tryProbeReferenceCount(abi))
        releaseWinRTManagedProjectionCallLease(first, value)
        releaseWinRTManagedProjectionCallLease(second, value)
        assertEquals(1u, WinRTInspectableComObject.tryProbeReferenceCount(abi))
        ComWrappersSupport.clearRegistriesForTests()
    }

    @Test
    fun compiler_injected_projection_state_shares_ccw_identity_and_rebinds_after_reset() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("4C4C4C4C-4C4C-4C4C-4C4C-4C4C4C4C4C4C")
        var definitionsCreated = 0
        fun registerFactory() {
            ComWrappersSupport.registerCcwFactory(CompilerProjectedManagedType::class) {
                definitionsCreated += 1
                WinRTCcwDefinition(
                    interfaceDefinitions = listOf(
                        WinRTInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
                    ),
                    defaultInterfaceId = interfaceId,
                )
            }
        }
        registerFactory()
        val value: Any = CompilerProjectedManagedType("compiler-owned-state")
        assertTrue(value is WinRTManagedProjectionStateOwner)
        val owner = value as WinRTManagedProjectionStateOwner
        assertSame(owner.winRTManagedProjectionState(), owner.winRTManagedProjectionState())
        val typeHandle = WinRTTypeHandle("test.ICompilerProjectedManaged", interfaceId)

        val directAbi = ComWrappersSupport.createCCWForObject(value, interfaceId).use { reference ->
            PlatformAbi.fromRawComPtr(reference.pointer)
        }
        val directPointer = PlatformAbi.pointerKey(directAbi)
        val borrowedReferenceCount = WinRTInspectableComObject.tryProbeReferenceCount(directAbi)
        repeat(2) {
            assertEquals(directAbi, tryBorrowWinRTManagedProjectionAbi(value, typeHandle))
            assertEquals(borrowedReferenceCount, WinRTInspectableComObject.tryProbeReferenceCount(directAbi))
        }
        val firstMarshaler = winRTProjectionMarshaler(value, typeHandle)
        val secondMarshaler = winRTProjectionMarshaler(value, typeHandle)
        assertNotSame(firstMarshaler, secondMarshaler)
        assertEquals(directPointer, PlatformAbi.pointerKey(firstMarshaler.abi))
        assertEquals(directPointer, PlatformAbi.pointerKey(secondMarshaler.abi))
        firstMarshaler.close()
        secondMarshaler.close()
        assertEquals(1, definitionsCreated)

        ComWrappersSupport.clearRegistriesForTests()
        assertTrue(PlatformAbi.isNull(tryBorrowWinRTManagedProjectionAbi(value, typeHandle)))
        registerFactory()
        winRTProjectionMarshaler(value, typeHandle).close()
        assertFalse(PlatformAbi.isNull(tryBorrowWinRTManagedProjectionAbi(value, typeHandle)))
        assertEquals(2, definitionsCreated)
        ComWrappersSupport.clearRegistriesForTests()
    }

    @Test
    fun compiler_injected_projection_state_does_not_pin_managed_ccw_target() {
        val value = cacheCompilerProjectedManagedValueWithoutExternalReference()

        drainUntilCleared(value)
    }

    @Test
    fun failed_projection_marshaling_does_not_pin_managed_ccw_target() {
        val value = failProjectionMarshalingWithMissingInterface()

        drainUntilCleared(value)
    }

    @Test
    fun aggregated_reference_query_interface_releases_temporary_qi_reference_and_wraps_borrowed_pointer() {
        val defaultInterfaceId = Guid("26262626-2626-2626-2626-262626262626")
        val secondaryInterfaceId = Guid("27272727-2727-2727-2727-272727272727")
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(defaultInterfaceId, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
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
        val interfaceType = WinRTTypeHandle("test.IFoo", Guid("11111111-1111-1111-1111-111111111111"))
        val helperType = WinRTTypeHandle("test.IFoo.Helper", Guid("22222222-2222-2222-2222-222222222222"))
        ComWrappersSupport.registerHelperType(interfaceType, helperType)
        ComWrappersSupport.registerTypedRcwFactory(helperType) { inspectable ->
            TestTypedWrapper(helperType, inspectable)
        }

        val host = WinRTInspectableComObject.inspectableBox(
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
        val typeHandle = WinRTTypeHandle("test.IFallback", interfaceId)
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
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
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
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
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = overrideInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = overrideInterfaceId,
                runtimeClassName = "test.ComposableDerived",
            )
        }
        val innerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
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
            val outerPointer = PlatformAbi.fromRawComPtr(composed.outer.pointer)
            val initialOuterCount = WinRTInspectableComObject.tryProbeReferenceCount(outerPointer)
            val firstMarshaler = ComWrappersSupport.createCCWForObjectForMarshaling(
                managed,
                overrideInterfaceId,
            )
            assertEquals(outerPointer, firstMarshaler.abi)
            assertEquals(initialOuterCount?.plus(1u), WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))
            firstMarshaler.close()
            assertEquals(initialOuterCount, WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))

            val borrowedAbi = tryBorrowWinRTManagedProjectionAbi(
                managed,
                WinRTTypeHandle("test.IComposableOverride", overrideInterfaceId),
            )
            assertEquals(outerPointer, borrowedAbi)
            assertEquals(initialOuterCount, WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))

            val secondMarshaler = ComWrappersSupport.createCCWForObjectForMarshaling(
                managed,
                overrideInterfaceId,
            )
            assertNotSame(firstMarshaler, secondMarshaler)
            secondMarshaler.close()
            assertEquals(initialOuterCount, WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))

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
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = defaultInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = defaultInterfaceId,
                runtimeClassName = "test.ReferenceTrackedComposable",
            )
        }
        val managed = TestComposableManagedType("tracked")
        var managerHostWasSet = false
        val managerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IReferenceTrackerManager,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int32) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) {
                            managerHostWasSet = true
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
            defaultInterfaceId = IID.IReferenceTrackerManager,
        )
        val instanceHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = primaryInspectableId,
                    methods = emptyList(),
                ),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = defaultInterfaceId,
                    methods = emptyList(),
                ),
            ),
            hiddenInterfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IReferenceTracker,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { arguments ->
                            managerHost.createReference(IID.IReferenceTrackerManager).use { managerReference ->
                                PlatformAbi.writePointer(
                                    arguments.single() as RawAddress,
                                    PlatformAbi.fromRawComPtr(managerReference.getRefPointer()),
                                )
                            }
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { KnownHResults.S_OK.value },
                    ),
                ),
            ),
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = "test.ReferenceTrackedInstance",
        )
        var returnedInstancePointerKey: Long? = null

        try {
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
                assertTrue(managerHostWasSet)
            }
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
            instanceHost.close()
            managerHost.close()
        }
    }

    @Test
    fun ccw_augmentation_preserves_existing_hidden_interfaces_and_appends_reference_tracker_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()
        val publicInterfaceId = Guid("67676767-6767-6767-6767-676767676767")
        val hiddenInterfaceId = Guid("68686868-6868-6868-6868-686868686868")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = publicInterfaceId,
                        methods = emptyList(),
                    ),
                ),
                hiddenInterfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
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
                    IID.ICustomPropertyProvider,
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
    fun ccw_augmentation_reuses_type_level_entries_while_tracker_state_remains_host_scoped() {
        val publicInterfaceId = Guid("68686868-6868-6868-6868-686868686869")
        val definition = WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = publicInterfaceId,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = publicInterfaceId,
        )

        // CsWinRT caches PregenerateNativeTypeInformation per managed type; only host state stays per CCW.
        assertSame(definition, XamlSystemProjectionRuntimeHooks.augmentInspectableDefinition(definition))
        val first = InteropRuntimeHooks.augmentInspectableDefinition(definition)
        val second = InteropRuntimeHooks.augmentInspectableDefinition(definition)
        val firstById = (first.interfaceDefinitions + first.hiddenInterfaceDefinitions).associateBy { it.interfaceId }
        val secondById = (second.interfaceDefinitions + second.hiddenInterfaceDefinitions).associateBy { it.interfaceId }

        listOf(
            IID.IStringable,
            IID.IWeakReferenceSource,
            IID.IMarshal,
            IID.IAgileObject,
            IID.IInspectable,
            IID.IUnknown,
            IID.IReferenceTrackerExtension,
        ).forEach { interfaceId ->
            assertSame(firstById.getValue(interfaceId), secondById.getValue(interfaceId))
        }
        assertSame(
            firstById.getValue(IID.IReferenceTrackerTarget),
            secondById.getValue(IID.IReferenceTrackerTarget),
        )
    }

    @Test
    fun ccw_augmentation_preserves_authored_marshal_interface_order() {
        ComWrappersSupport.clearRegistriesForTests()
        val publicInterfaceId = Guid("69696969-6969-6969-6969-696969696969")
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = publicInterfaceId,
                        methods = emptyList(),
                    ),
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = IID.IMarshal,
                        baseKind = WinRTComInterfaceBaseKind.IUnknown,
                        methods = emptyList(),
                    ),
                ),
                defaultInterfaceId = publicInterfaceId,
                runtimeClassName = "test.ManagedType",
            )
        }

        ComWrappersSupport.createCCWForObject(TestManagedType("payload"), publicInterfaceId).use { ccw ->
            ccw.queryInterface(IID.IMarshal).getOrThrow().use { marshal ->
                assertTrue(marshal.sameIdentity(ccw))
            }
            val info = ComWrappersSupport.getInspectableInfo(PlatformAbi.fromRawComPtr(ccw.pointer))
            assertEquals(
                listOf(
                    publicInterfaceId,
                    IID.IMarshal,
                    IID.IStringable,
                    IID.ICustomPropertyProvider,
                    IID.IWeakReferenceSource,
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
        val sourceInterfaceType = WinRTTypeHandle("test.ISource", Guid("55555555-5555-5555-5555-555555555551"))
        val targetInterfaceType = WinRTTypeHandle("test.ITarget", Guid("55555555-5555-5555-5555-555555555552"))
        ComWrappersSupport.registerTypedRcwFactory(sourceInterfaceType) { inspectable ->
            TestSourceWrapper(sourceInterfaceType, inspectable)
        }
        ComWrappersSupport.registerTypedRcwFactory(targetInterfaceType) { inspectable ->
            TestTargetWrapper(targetInterfaceType, inspectable)
        }
        WinRTTypeRegistry.register<TestProjectedInterface>(
            projectedTypeName = sourceInterfaceType.projectedTypeName,
            iid = sourceInterfaceType.interfaceId,
            isWindowsRuntimeType = true,
        )
        WinRTTypeRegistry.register<TargetProjectedInterface>(
            projectedTypeName = targetInterfaceType.projectedTypeName,
            iid = targetInterfaceType.interfaceId,
            isWindowsRuntimeType = true,
        )

        val projected = ProjectedInspectableObject(
            pointer = WinRTInspectableComObject.inspectableBox("payload", "test.RuntimeClass")
                .detachReference(IID.IInspectable)
        )

        val source = projected.asWinRT<TestProjectedInterface>() as TestSourceWrapper
        val interfaceTypedSource: TestProjectedInterface = source
        val target = interfaceTypedSource.asWinRT<TargetProjectedInterface>() as TestTargetWrapper
        assertEquals(sourceInterfaceType, source.primaryTypeHandle)
        assertEquals(targetInterfaceType, target.primaryTypeHandle)
        target.nativeObject.close()
        source.nativeObject.close()
        projected.nativeObject.close()
    }

    @Test
    fun cast_extension_returns_existing_projected_kotlin_subclass() {
        val projected = TestDerivedRuntimeClassWrapper(
            pointer = WinRTInspectableComObject.inspectableBox("payload", "test.DerivedRuntimeClass")
                .detachReference(IID.IInspectable),
        )

        val cast = projected.asWinRT<TestBaseRuntimeClassWrapper>()

        assertSame(projected, cast)
        projected.nativeObject.close()
    }

    @Test
    fun generic_cast_rejects_targets_without_registered_winrt_interface_iid() {
        ComWrappersSupport.clearRegistriesForTests()
        val projected = ProjectedInspectableObject(
            pointer = WinRTInspectableComObject.inspectableBox("payload", "test.RuntimeClass")
                .detachReference(IID.IInspectable)
        )

        val error = assertFailsWith<IllegalArgumentException> {
            projected.asWinRT<UnregisteredProjectedInterface>()
        }

        assertTrue(error.message.orEmpty().contains("not a registered WinRT interface type"))
        projected.nativeObject.close()
    }

    private data class TestManagedType(val name: String)

    private data class DetachedManagedValue(
        val value: PlatformManagedWeakReference<TestManagedType>,
        val pointer: RawAddress,
        val interfaceId: Guid,
    )

    private fun createDetachedManagedValue(): DetachedManagedValue {
        val interfaceId = Guid("45454545-4545-4545-4545-454545454545")
        registerWeakCachedTestManagedType(interfaceId)
        val value = TestManagedType("native-owned")
        val weakValue = PlatformManagedWeakReference(value)
        return DetachedManagedValue(
            value = weakValue,
            pointer = ComWrappersSupport.detachCCWForObject(value, interfaceId),
            interfaceId = interfaceId,
        )
    }

    private fun cacheManagedValueWithoutExternalReference(): PlatformManagedWeakReference<TestManagedType> {
        val interfaceId = Guid("45454545-4545-4545-4545-454545454546")
        registerWeakCachedTestManagedType(interfaceId)
        val value = TestManagedType("cache-only")
        val weakValue = PlatformManagedWeakReference(value)
        ComWrappersSupport.createCCWForObject(value, interfaceId).close()
        return weakValue
    }

    private fun cacheCompilerProjectedManagedValueWithoutExternalReference():
        PlatformManagedWeakReference<CompilerProjectedManagedType> {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("4D4D4D4D-4D4D-4D4D-4D4D-4D4D4D4D4D4D")
        ComWrappersSupport.registerCcwFactory(CompilerProjectedManagedType::class) {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(interfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = interfaceId,
            )
        }
        val value = CompilerProjectedManagedType("compiler-owned-cache-only")
        val valueAsAny: Any = value
        check(valueAsAny is WinRTManagedProjectionStateOwner)
        val weakValue = PlatformManagedWeakReference(value)
        ComWrappersSupport.createCCWForObject(value, interfaceId).close()
        return weakValue
    }

    private fun failProjectionMarshalingWithMissingInterface(): PlatformManagedWeakReference<TestManagedType> {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("4A4A4A4A-4A4A-4A4A-4A4A-4A4A4A4A4A4A")
        val missingInterfaceId = Guid("4B4B4B4B-4B4B-4B4B-4B4B-4B4B4B4B4B4B")
        registerWeakCachedTestManagedType(interfaceId)
        val value = TestManagedType("failed-marshaling")
        val weakValue = PlatformManagedWeakReference(value)

        assertFailsWith<WinRTUnsupportedOperationException> {
            winRTProjectionMarshaler(
                value,
                WinRTTypeHandle("test.IMissingManagedMarshaling", missingInterfaceId),
            )
        }
        ComWrappersSupport.clearRegistriesForTests()
        return weakValue
    }

    private fun registerWeakCachedTestManagedType(interfaceId: Guid) {
        ComWrappersSupport.registerCcwFactory(TestManagedType::class) {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = listOf(
                            WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ ->
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
                defaultInterfaceId = interfaceId,
            )
        }
    }

    @Test
    fun ccw_factory_resolution_is_cached_by_concrete_type_and_invalidated_by_late_registration() {
        ComWrappersSupport.clearRegistriesForTests()
        val firstInterfaceId = Guid("46464646-4646-4646-4646-464646464646")
        val secondInterfaceId = Guid("47474747-4747-4747-4747-474747474747")
        val firstFactory: (Any) -> WinRTCcwDefinition = {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(firstInterfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = firstInterfaceId,
            )
        }
        val secondFactory: (Any) -> WinRTCcwDefinition = {
            WinRTCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(secondInterfaceId, methods = emptyList()),
                ),
                defaultInterfaceId = secondInterfaceId,
            )
        }
        val value = MultiInterfaceManagedType()

        try {
            assertTrue(ComWrappersSupport.registerCcwFactory(FirstManagedInterface::class, firstFactory))
            val firstResolution = CcwFactoryRegistry.findFactories(value)
            assertEquals(1, firstResolution.size)
            assertSame(firstFactory, firstResolution.single())
            assertSame(firstResolution, CcwFactoryRegistry.findFactories(value))

            assertTrue(ComWrappersSupport.registerCcwFactory(SecondManagedInterface::class, secondFactory))
            val secondResolution = CcwFactoryRegistry.findFactories(value)
            assertNotSame(firstResolution, secondResolution)
            assertEquals(2, secondResolution.size)
            assertTrue(secondResolution.any { factory -> factory === firstFactory })
            assertTrue(secondResolution.any { factory -> factory === secondFactory })
            assertSame(secondResolution, CcwFactoryRegistry.findFactories(value))
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun static_ccw_definition_resolution_precomputes_augmentation_and_invalidates_late_entries() {
        ComWrappersSupport.clearRegistriesForTests()
        val firstInterfaceId = Guid("48484848-4848-4848-4848-484848484848")
        val secondInterfaceId = Guid("49494949-4949-4949-4949-494949494949")
        val firstDefinition = WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(firstInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = firstInterfaceId,
        )
        val secondDefinition = WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(secondInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = secondInterfaceId,
        )
        val value = MultiInterfaceManagedType()

        try {
            assertTrue(
                ComWrappersSupport.registerStaticCcwDefinition(
                    FirstManagedInterface::class,
                    firstDefinition,
                ),
            )
            val firstResolution = CcwFactoryRegistry.findStaticDefinitions(value)
            assertEquals(1, firstResolution.size)
            assertSame(firstDefinition, firstResolution.single())
            assertSame(firstResolution, CcwFactoryRegistry.findStaticDefinitions(value))
            val firstAugmented = requireNotNull(CcwFactoryRegistry.findRegistration(value).augmentedStaticDefinition)
            assertSame(firstAugmented, CcwFactoryRegistry.findRegistration(value).augmentedStaticDefinition)
            assertTrue(firstAugmented.interfaceDefinitions.any { it.interfaceId == firstInterfaceId })
            assertTrue(firstAugmented.interfaceDefinitions.any { it.interfaceId == IID.IStringable })

            assertTrue(
                ComWrappersSupport.registerStaticCcwDefinition(
                    SecondManagedInterface::class,
                    secondDefinition,
                ),
            )
            val secondResolution = CcwFactoryRegistry.findStaticDefinitions(value)
            assertNotSame(firstResolution, secondResolution)
            assertEquals(2, secondResolution.size)
            assertTrue(secondResolution.any { definition -> definition === firstDefinition })
            assertTrue(secondResolution.any { definition -> definition === secondDefinition })
            assertSame(secondResolution, CcwFactoryRegistry.findStaticDefinitions(value))
            val secondAugmented = requireNotNull(CcwFactoryRegistry.findRegistration(value).augmentedStaticDefinition)
            assertNotSame(firstAugmented, secondAugmented)
            assertSame(secondAugmented, CcwFactoryRegistry.findRegistration(value).augmentedStaticDefinition)
            assertTrue(secondAugmented.interfaceDefinitions.any { it.interfaceId == firstInterfaceId })
            assertTrue(secondAugmented.interfaceDefinitions.any { it.interfaceId == secondInterfaceId })

            ComWrappersSupport.createCCWForObject(value, firstInterfaceId).use { first ->
                first.queryInterface(secondInterfaceId).getOrThrow().use { second ->
                    assertTrue(first.sameIdentity(second))
                }
            }
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }

    @Test
    fun custom_query_interface_results_are_not_cached_as_host_owned_entries() {
        val primaryInterfaceId = Guid("35353535-3535-3535-3535-353535353535")
        val fallbackInterfaceId = Guid("36363636-3636-3636-3636-363636363636")
        var fallbackCalls = 0
        lateinit var host: WinRTInspectableComObject
        host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = primaryInterfaceId,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = primaryInterfaceId,
            queryInterfaceFallback = { requestedInterfaceId ->
                if (requestedInterfaceId != fallbackInterfaceId) {
                    null
                } else {
                    fallbackCalls += 1
                    host.acquireReference(primaryInterfaceId)
                }
            },
        )

        try {
            host.createPrimaryReference().use { primary ->
                repeat(2) {
                    primary.queryInterface(fallbackInterfaceId).getOrThrow().use { fallback ->
                        assertTrue(fallback.sameIdentity(primary))
                    }
                }
            }
            assertEquals(2, fallbackCalls)
        } finally {
            host.close()
        }
    }

    @Test
    fun host_created_local_references_share_the_canonical_managed_release_identity() {
        val primaryInterfaceId = Guid("34343434-3434-3434-3434-343434343434")
        val secondaryInterfaceId = Guid("34343434-3434-3434-3434-343434343435")
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(primaryInterfaceId, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = primaryInterfaceId,
        ).use { host ->
            host.createReference(primaryInterfaceId).use { primary ->
                host.createReference(secondaryInterfaceId).use { secondary ->
                    assertFalse(PlatformAbi.isNull(primary.comPtr.support.managedCcwReleaseIdentity))
                    assertEquals(
                        primary.comPtr.support.managedCcwReleaseIdentity,
                        secondary.comPtr.support.managedCcwReleaseIdentity,
                    )
                }
            }
        }
    }

    @Test
    fun query_interface_forwarding_uses_the_forward_target_reference_policy() {
        val outerInterfaceId = Guid("37373737-3737-3737-3737-373737373737")
        val innerInterfaceId = Guid("38383838-3838-3838-3838-383838383838")
        val innerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(innerInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = innerInterfaceId,
        )
        val innerReference = innerHost.createPrimaryReference()
        val innerPointer = PlatformAbi.fromRawComPtr(innerReference.pointer)
        val outerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(outerInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = outerInterfaceId,
        )

        try {
            outerHost.setQueryInterfaceForwardTarget(innerReference)
            outerHost.createPrimaryReference().use { outerReference ->
                val outerPointer = PlatformAbi.fromRawComPtr(outerReference.pointer)
                assertFalse(PlatformAbi.isNull(outerReference.comPtr.support.managedCcwReleaseIdentity))
                assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))
                assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(innerPointer))

                repeat(2) {
                    outerReference.queryInterface(innerInterfaceId).getOrThrow().use { forwarded ->
                        assertEquals(innerPointer, PlatformAbi.fromRawComPtr(forwarded.pointer))
                        assertTrue(PlatformAbi.isNull(forwarded.comPtr.support.managedCcwReleaseIdentity))
                    }
                    assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(outerPointer))
                    assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(innerPointer))
                }
            }
            outerHost.createReference(innerInterfaceId).use { forwarded ->
                assertEquals(innerPointer, PlatformAbi.fromRawComPtr(forwarded.pointer))
                assertTrue(PlatformAbi.isNull(forwarded.comPtr.support.managedCcwReleaseIdentity))
            }
        } finally {
            outerHost.close()
            innerReference.close()
            innerHost.close()
        }
    }

    @Test
    fun query_interface_forwarding_preserves_non_nointerface_failures() {
        val outerInterfaceId = Guid("39393939-3939-3939-3939-393939393939")
        val innerInterfaceId = Guid("3A3A3A3A-3A3A-3A3A-3A3A-3A3A3A3A3A3A")
        val missingInterfaceId = Guid("3B3B3B3B-3B3B-3B3B-3B3B-3B3B3B3B3B3B")
        val innerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(innerInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = innerInterfaceId,
            queryInterfaceFallback = {
                throw WinRTIllegalStateException("Synthetic inner QI failure.", KnownHResults.E_FAIL)
            },
        )
        val innerReference = innerHost.createPrimaryReference()
        val outerHost = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(outerInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = outerInterfaceId,
            initialQueryInterfaceForwardTarget = innerReference,
        )

        try {
            val failure = assertFailsWith<WinRTRuntimeException> {
                outerHost.createReference(missingInterfaceId)
            }
            assertEquals(KnownHResults.E_FAIL, failure.hResult)
        } finally {
            outerHost.close()
            innerReference.close()
            innerHost.close()
        }
    }

    private fun <T : Any> drainUntilCleared(reference: PlatformManagedWeakReference<T>) {
        repeat(10) {
            PlatformFinalization.drain()
            if (reference.get() == null) {
                return
            }
            val pressure = List(128) { ByteArray(1024) }
            assertEquals(128, pressure.size)
        }
        assertEquals(null, reference.get())
    }

    private class TestComposableManagedType(val name: String) : WinRTComposableObject {
        var composableReference: WinRTComposableObjectReference? = null

        override val winRTComposableObjectReference: WinRTComposableObjectReference?
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

    private class TestDelegateWrapper(
        override val primaryTypeHandle: WinRTTypeHandle,
        private val delegateReference: WinRTDelegateReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = delegateReference
    }

    private class TestTypedWrapper(
        override val primaryTypeHandle: WinRTTypeHandle,
        private val inspectable: IInspectableReference,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private class TestSourceWrapper(
        override val primaryTypeHandle: WinRTTypeHandle,
        private val inspectable: IInspectableReference,
    ) : TestProjectedInterface, IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private class TestTargetWrapper(
        override val primaryTypeHandle: WinRTTypeHandle,
        private val inspectable: IInspectableReference,
    ) : TargetProjectedInterface, IWinRTObject {
        override val nativeObject: ComObjectReference
            get() = inspectable
    }

    private open class TestBaseRuntimeClassWrapper(
        pointer: RawAddress,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
    }

    private class TestDerivedRuntimeClassWrapper(
        pointer: RawAddress,
    ) : TestBaseRuntimeClassWrapper(pointer)

    @WinRTProjectedInterface
    private interface CompilerProjectedInterface

    private class CompilerProjectedManagedType(
        val name: String,
    ) : CompilerProjectedInterface

    private interface TestProjectedInterface

    private interface TargetProjectedInterface

    private interface FirstManagedInterface

    private interface SecondManagedInterface

    private class MultiInterfaceManagedType : FirstManagedInterface, SecondManagedInterface

    private interface UnregisteredProjectedInterface
}
