package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test

class EventRuntimeInfrastructureTest {
    @Test
    fun event_source_registers_once_and_dispatches_current_handlers() {
        EventSourceCache.clearForTests()

        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        var registrations = 0
        var removals = 0
        var activeDelegate: WinRtDelegateReference? = null
        val token = EventRegistrationToken(0x11223344_00000001)
        val received = mutableListOf<String>()
        val source =
            TestIntEventSource(
                owner = owner,
                addHandler = { _, handler ->
                    registrations += 1
                    activeDelegate?.close()
                    activeDelegate = WinRtDelegateReference.fromAbi(handler.getRefPointer().asRawAddress(), testIntEventDescriptor)
                    token
                },
                removeHandler = { _, removedToken ->
                    removals += 1
                    assertEquals(token, removedToken)
                    activeDelegate?.close()
                    activeDelegate = null
                },
            )

        val first: (Any?, Int) -> Unit = { _, value -> received += "first:$value" }
        val second: (Any?, Int) -> Unit = { _, value -> received += "second:$value" }

        try {
            source.subscribe(first)
            source.subscribe(second)

            assertEquals(1, registrations)
            activeDelegate!!.invoke(listOf("sender", 7))
            assertEquals(listOf("first:7", "second:7"), received)

            source.unsubscribe(first)
            assertEquals(0, removals)
            activeDelegate!!.invoke(listOf("sender", 9))
            assertEquals(listOf("first:7", "second:7", "second:9"), received)

            source.unsubscribe(second)
            assertEquals(1, removals)
            assertNull(activeDelegate)
        } finally {
            activeDelegate?.close()
            owner.close()
            EventSourceCache.clearForTests()
        }
    }

    @Test
    fun event_source_reregisters_when_cached_delegate_loses_native_references() {
        EventSourceCache.clearForTests()

        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        var registrations = 0
        var removals = 0
        var activeDelegate: WinRtDelegateReference? = null
        val received = mutableListOf<String>()
        val source =
            TestIntEventSource(
                owner = owner,
                addHandler = { _, handler ->
                    registrations += 1
                    activeDelegate?.close()
                    activeDelegate = WinRtDelegateReference.fromAbi(handler.getRefPointer().asRawAddress(), testIntEventDescriptor)
                    EventRegistrationToken(registrations.toLong())
                },
                removeHandler = { _, _ ->
                    removals += 1
                    activeDelegate?.close()
                    activeDelegate = null
                },
            )

        val first: (Any?, Int) -> Unit = { _, value -> received += "first:$value" }
        val second: (Any?, Int) -> Unit = { _, value -> received += "second:$value" }

        try {
            source.subscribe(first)
            assertEquals(1, registrations)
            activeDelegate!!.close()
            activeDelegate = null

            source.subscribe(second)
            assertEquals(2, registrations)
            assertEquals(0, removals)

            activeDelegate!!.invoke(listOf("sender", 11))
            assertEquals(listOf("second:11"), received)

            source.unsubscribe(second)
            assertEquals(1, removals)
        } finally {
            activeDelegate?.close()
            owner.close()
            EventSourceCache.clearForTests()
        }
    }

    @Test
    fun event_source_shutdown_registry_removes_active_native_registration() {
        EventSourceCache.clearForTests()
        EventSourceShutdownRegistry.clearForTests()

        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        var removals = 0
        var activeDelegate: WinRtDelegateReference? = null
        val token = EventRegistrationToken(0x33445566_00000001)
        val source =
            TestIntEventSource(
                owner = owner,
                addHandler = { _, handler ->
                    activeDelegate?.close()
                    activeDelegate = WinRtDelegateReference.fromAbi(handler.getRefPointer().asRawAddress(), testIntEventDescriptor)
                    token
                },
                removeHandler = { _, removedToken ->
                    removals += 1
                    assertEquals(token, removedToken)
                    activeDelegate?.close()
                    activeDelegate = null
                },
            )

        try {
            source.subscribe { _, _ -> }

            assertNotNull(activeDelegate)

            EventSourceShutdownRegistry.closeAllForTests()

            assertEquals(1, removals)
            assertNull(activeDelegate)
        } finally {
            activeDelegate?.close()
            owner.close()
            EventSourceCache.clearForTests()
            EventSourceShutdownRegistry.clearForTests()
        }
    }

    @Test
    fun event_source_shutdown_registry_closes_state_when_native_removal_fails() {
        EventSourceCache.clearForTests()
        EventSourceShutdownRegistry.clearForTests()

        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        var activeDelegate: WinRtDelegateReference? = null
        val received = mutableListOf<Int>()
        val source =
            TestIntEventSource(
                owner = owner,
                addHandler = { _, handler ->
                    activeDelegate?.close()
                    activeDelegate = WinRtDelegateReference.fromAbi(handler.getRefPointer().asRawAddress(), testIntEventDescriptor)
                    EventRegistrationToken(0x77889900_00000001)
                },
                removeHandler = { _, _ ->
                    throw IllegalStateException("native event source is already shutting down")
                },
            )

        try {
            source.subscribe { _, value -> received += value }

            EventSourceShutdownRegistry.closeAllForTests()

            activeDelegate!!.invoke(listOf("sender", 17))
            assertEquals(emptyList<Int>(), received)
            assertNull(EventSourceCache.getState(owner, 41))
        } finally {
            activeDelegate?.close()
            owner.close()
            EventSourceCache.clearForTests()
            EventSourceShutdownRegistry.clearForTests()
        }
    }

    @Test
    fun event_source_state_treats_reference_tracker_refs_as_native_refs() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = testEventInterfaceId,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { }
        val state =
            object : EventSourceState<(Any?, Int) -> Unit>(PlatformAbi.nullPointer, 61) {
                override fun createEventInvoke(): (Any?, Int) -> Unit = { _, _ -> }
            }

        handle.use {
            it.createReference().use { reference ->
                state.initializeReferenceTracking(PlatformAbi.fromRawComPtr(reference.pointer))
                reference.queryInterface(IID.IReferenceTrackerTarget).getOrThrow().use { trackerTarget ->
                    val trackerPointer = trackerTarget.pointer
                    trackerTarget.close()
                    reference.close()

                    assertEquals(false, state.hasComReferences())
                    ComVtableInvoker.invoke(trackerPointer, ReferenceTrackerTargetVftblSlots.AddRefFromReferenceTracker)
                    assertEquals(true, state.hasComReferences())
                    ComVtableInvoker.invoke(trackerPointer, ReferenceTrackerTargetVftblSlots.ReleaseFromReferenceTracker)
                    assertEquals(false, state.hasComReferences())
                }
            }
        }
    }

    @Test
    fun dispatcher_queue_handler_uses_standard_delegate_failure_hresult_in_runtime() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = IID.DispatcherQueueHandler,
            parameterKinds = emptyList(),
        ) {
            throw IllegalStateException("dispatcher callback failed")
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals(ExceptionHelpers.E_FAIL, reference.invokeAbi(emptyList()))
            }
        }
    }

    @Test
    fun non_dispatcher_delegate_failures_return_error_hresult() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = testEventInterfaceId,
            parameterKinds = emptyList(),
        ) {
            throw IllegalStateException("delegate callback failed")
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals(ExceptionHelpers.E_FAIL, reference.invokeAbi(emptyList()))
            }
        }
    }

    @Test
    fun standard_delegates_round_trip_event_token_through_vtable_slots() {
        TestEventHost().use { host ->
            host.createReference().use { owner ->
                val handle = WinRtDelegateBridge.createUnitDelegate(testEventInterfaceId, listOf(WinRtDelegateValueKind.INT32)) { }
                handle.use {
                    it.createReference().use { handler ->
                        val token =
                            StandardDelegates.addEventHandler(
                                objectReference = owner,
                                addHandlerSlot = IInspectableVftblSlots.FirstCustom,
                                handler = handler,
                            )

                        assertEquals(host.token, token)
                        assertNotNull(host.lastAddedHandlerPointerKey)

                        StandardDelegates.removeEventHandler(
                            objectReference = owner,
                            removeHandlerSlot = IInspectableVftblSlots.FirstCustom + 1,
                            token = token,
                        )

                        assertEquals(host.token, host.lastRemovedToken)
                    }
                }
            }
        }
    }

    @Test
    fun generic_delegate_helper_caches_delegate_per_reference_and_slot() {
        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        try {
            val first = GenericDelegateHelper.createDelegate(owner, 6) { Any() }
            val second = GenericDelegateHelper.createDelegate(owner, 6) { Any() }
            val third = GenericDelegateHelper.createDelegate(owner, 7) { Any() }

            assertSame(first, second)
            assertNotNull(third)
        } finally {
            owner.close()
        }
    }

    @Test
    fun event_source_cache_tracks_weak_reference_source_objects() {
        assumeTrue(PlatformRuntime.isWindows)
        EventSourceCache.clearForTests()

        RuntimeScope.initializeMultithreaded().use {
            WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
                val sentinel = Any()
                val state = WeakReference<Any>(sentinel)

                EventSourceCache.create(instance, 41, state)
                assertSame(state, EventSourceCache.getState(instance, 41))

                EventSourceCache.remove(PlatformAbi.pointerKey(instance.pointer), 41, state)
                assertNull(EventSourceCache.getState(instance, 41))
            }
        }
    }

    private class TestIntEventSource(
        owner: ComObjectReference,
        addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
    ) : EventSource<(Any?, Int) -> Unit>(
            objectReference = owner,
            addHandler = addHandler,
            removeHandler = removeHandler,
            index = 41,
        ) {
        override fun createMarshaler(handler: (Any?, Int) -> Unit): WinRtDelegateHandle =
            WinRtDelegateBridge.createUnitDelegate(
                iid = testEventInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            ) { args ->
                handler(args[0], args[1] as Int)
            }

        override fun createEventSourceState(): EventSourceState<(Any?, Int) -> Unit> =
            object : EventSourceState<(Any?, Int) -> Unit>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
                override fun createEventInvoke(): (Any?, Int) -> Unit =
                    { sender, value ->
                        snapshotHandlers().forEach { handler -> handler(sender, value) }
                    }
            }
    }

    private class TestEventHost : AutoCloseable {
        val token = EventRegistrationToken(0x55667788_00000002)
        var lastAddedHandlerPointerKey: Long? = null
        var lastRemovedToken: EventRegistrationToken? = null

        private val host =
            WinRtInspectableComObject(
                interfaceDefinitions =
                    listOf(
                        WinRtInspectableInterfaceDefinition(
                            interfaceId = testEventOwnerInterfaceId,
                            methods =
                                listOf(
                                    WinRtInspectableMethodDefinition(addEventHandlerTestDescriptor) { args ->
                                        lastAddedHandlerPointerKey = PlatformAbi.pointerKey(args[0] as NativePointer)
                                        EventRegistrationToken.copyTo(token, args[1] as NativePointer)
                                        KnownHResults.S_OK.value
                                    },
                                    WinRtInspectableMethodDefinition(removeEventHandlerTestDescriptor) { args ->
                                        lastRemovedToken = EventRegistrationToken(args[0] as Long)
                                        KnownHResults.S_OK.value
                                    },
                                ),
                        ),
                    ),
                runtimeClassName = "test.EventHost",
            )

        fun createReference(): ComObjectReference = host.createPrimaryReference()

        override fun close() {
            host.close()
        }
    }

    companion object {
        private val testEventInterfaceId = Guid("0f0f0f0f-1111-2222-3333-444444444444")
        private val testEventOwnerInterfaceId = Guid("10101010-1111-2222-3333-555555555555")
        private val testIntEventDescriptor =
            WinRtDelegateDescriptor(
                interfaceId = testEventInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            )
        private val generatedObjectEventInterfaceId = Guid("20202020-1111-2222-3333-666666666666")
        private val generatedObjectEventDescriptor =
            WinRtDelegateDescriptor(
                interfaceId = generatedObjectEventInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
            )
        private val eventHandlerObjectInterfaceId = Guid("C50898F6-C536-5F47-8583-8B2C2438A13B")
        private val eventHandlerObjectDescriptor =
            WinRtDelegateDescriptor(
                interfaceId = eventHandlerObjectInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
            )
        private val addEventHandlerTestDescriptor = AbiFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.ADDRESS,
            NativeValueLayout.ADDRESS,
        )
        private val removeEventHandlerTestDescriptor = AbiFunctionDescriptor.of(
            NativeValueLayout.JAVA_INT,
            NativeValueLayout.JAVA_LONG,
        )
    }
}

interface GeneratedObjectEventHandler {
    fun invoke(sender: Any?, value: Int)
}
