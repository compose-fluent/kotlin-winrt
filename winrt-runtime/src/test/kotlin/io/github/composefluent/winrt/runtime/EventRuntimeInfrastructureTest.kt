package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

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
    fun windows_app_sdk_bootstrap_scope_close_removes_active_event_source_registration() {
        EventSourceCache.clearForTests()
        EventSourceShutdownRegistry.clearForTests()

        val owner = WinRtInspectableComObject.inspectableBox("owner", "test.Owner").createPrimaryReference()
        val bootstrapDll = Files.createTempFile("kotlin-winrt-bootstrap", ".dll")
        var removals = 0
        var activeDelegate: WinRtDelegateReference? = null
        val token = EventRegistrationToken(0x44556677_00000002)
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

            WinRtWindowsAppSdkBootstrap.Scope(
                bootstrapDll = bootstrapDll,
                activationContexts = emptyList(),
                bootstrapLookup = null,
                windowsAppRuntimeLookup = null,
            ).close()

            assertEquals(1, removals)
            assertNull(activeDelegate)
        } finally {
            activeDelegate?.close()
            owner.close()
            EventSourceCache.clearForTests()
            EventSourceShutdownRegistry.clearForTests()
            Files.deleteIfExists(bootstrapDll)
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
    fun generated_event_source_projects_object_arguments_with_marshal_inspectable_object_semantics() {
        EventSourceCache.clearForTests()

        var activeDelegate: WinRtDelegateReference? = null
        val token = EventRegistrationToken(0x22446688_00000001)
        val ownerHost = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = testEventOwnerInterfaceId,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(addEventHandlerTestDescriptor) { args ->
                            activeDelegate?.close()
                            WinRtPlatformApi.addRefRaw(args[0] as NativePointer)
                            activeDelegate = WinRtDelegateReference.fromAbi(
                                args[0] as NativePointer,
                                generatedObjectEventDescriptor,
                            )
                            EventRegistrationToken.copyTo(token, args[1] as NativePointer)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(removeEventHandlerTestDescriptor) { args ->
                            assertEquals(token, EventRegistrationToken(args[0] as Long))
                            activeDelegate?.close()
                            activeDelegate = null
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
            runtimeClassName = "test.Owner",
        )
        val owner = ownerHost.createPrimaryReference()
        val sender = WinRtInspectableComObject.inspectableBox("sender-object", "test.Sender").createPrimaryReference()
        var receivedSender: Any? = null
        var receivedValue: Int? = null
        val source = WinRtGeneratedEventSourceRuntime.createEventSourceFactory(
            WinRtEventSourceDescriptor(
                eventType = GeneratedObjectEventHandler::class.qualifiedName!!,
                ownerType = "test.Owner",
                sourceClass = "GeneratedObjectEventSource",
                abiEventType = GeneratedObjectEventHandler::class.qualifiedName!!,
                genericArguments = emptyList(),
                interfaceId = generatedObjectEventInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
                returnKind = WinRtDelegateValueKind.UNIT,
                parameterTypeNames = listOf("System.Object", "Int32"),
            ),
        )!!(owner, IInspectableVftblSlots.FirstCustom) as EventSource<Any>
        val handler = object : GeneratedObjectEventHandler {
            override fun invoke(sender: Any?, value: Int) {
                receivedSender = sender
                receivedValue = value
            }
        }

        try {
            source.subscribe(handler)
            activeDelegate!!.invoke(listOf(sender, 7))

            assertEquals("sender-object", receivedSender)
            assertEquals(7, receivedValue)
            source.unsubscribe(handler)
        } finally {
            activeDelegate?.close()
            owner.close()
            ownerHost.close()
            sender.close()
            EventSourceCache.clearForTests()
        }
    }

    @Test
    fun generated_event_source_runtime_uses_shared_event_handler_source_for_event_handler_descriptors() {
        EventSourceCache.clearForTests()

        var activeDelegate: WinRtDelegateReference? = null
        val token = EventRegistrationToken(0x22446688_00000005)
        val ownerHost = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = testEventOwnerInterfaceId,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(addEventHandlerTestDescriptor) { args ->
                            activeDelegate?.close()
                            WinRtPlatformApi.addRefRaw(args[0] as NativePointer)
                            activeDelegate = WinRtDelegateReference.fromAbi(
                                args[0] as NativePointer,
                                eventHandlerObjectDescriptor,
                            )
                            EventRegistrationToken.copyTo(token, args[1] as NativePointer)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(removeEventHandlerTestDescriptor) { args ->
                            assertEquals(token, EventRegistrationToken(args[0] as Long))
                            activeDelegate?.close()
                            activeDelegate = null
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
            runtimeClassName = "test.Owner",
        )
        val owner = ownerHost.createPrimaryReference()
        val source = WinRtGeneratedEventSourceRuntime.createEventSourceFactory(
            WinRtEventSourceDescriptor(
                eventType = "Windows.Foundation.EventHandler<System.Object>",
                ownerType = "test.Owner",
                sourceClass = "EventHandlerEventSource",
                abiEventType = "Windows.Foundation.EventHandler<System.Object>",
                genericArguments = listOf("System.Object"),
                usesSharedEventHandlerSource = true,
                interfaceId = eventHandlerObjectInterfaceId,
                parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
                returnKind = WinRtDelegateValueKind.UNIT,
                parameterTypeNames = listOf("System.Object", "System.Object"),
            ),
        )!!(owner, IInspectableVftblSlots.FirstCustom) as EventSource<EventHandlerCallback<Any?>>
        val received = mutableListOf<Pair<Any?, Any?>>()
        val handler: EventHandlerCallback<Any?> = { sender, args -> received += sender to args }

        try {
            source.subscribe(handler)
            activeDelegate!!.invoke(listOf("sender", "args"))

            assertEquals(listOf("sender" to "args"), received)
            source.unsubscribe(handler)
            assertNull(activeDelegate)
        } finally {
            activeDelegate?.close()
            owner.close()
            ownerHost.close()
            EventSourceCache.clearForTests()
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
