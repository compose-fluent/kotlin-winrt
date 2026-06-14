package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ActivationFactoryReference
import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtComInterfaceBaseKind
import io.github.composefluent.winrt.runtime.WinRtNotImplementedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WinRtAuthoringCommonTest {
    @Test
    fun authoredActivationFactoryCreatesDefaultInstanceThroughActivationFactory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("aaaaaaaa-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<ActivatableComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ActivatableComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<ActivatableComponent>(
            runtimeClassName = "Sample.Authoring.ActivatableComponent",
            createInstance = ::ActivatableComponent,
        )

        ActivationFactory.get("Sample.Authoring.ActivatableComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        ActivatableComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authoredActivationFactoryExposesFactoryInterfaceMembers() {
        ComWrappersSupport.clearRegistriesForTests()
        val componentInterfaceId = Guid("bbbbbbbb-1111-2222-3333-444444444444")
        val factoryInterfaceId = Guid("bbbbbbbb-1111-2222-3333-444444444445")
        WinRtAuthoring.registerType<FactoryBackedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FactoryBackedComponent",
                defaultInterfaceId = componentInterfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = componentInterfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory(
            WinRtAuthoredActivationFactoryDefinition(
                runtimeClassName = "Sample.Authoring.FactoryBackedComponent",
                implementationType = FactoryBackedComponent::class,
                factoryInterfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = factoryInterfaceId,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                                PlatformAbi.writeInt32(args.single() as RawAddress, 7)
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
            ),
        )

        ActivationFactory.get("Sample.Authoring.FactoryBackedComponent", factoryInterfaceId).use { factory ->
            PlatformAbi.confinedScope().use { scope ->
                val result = PlatformAbi.allocateInt32Slot(scope)
                assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(factory.pointer, 6, result))
                assertEquals(7, PlatformAbi.readInt32(result))
            }
        }
    }

    @Test
    fun authoredActivationFactoryExposesComposableFactoryInterfaceMembers() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("bcbcbcbc-1111-2222-3333-444444444444")
        val innerIid = Guid("bcbcbcbc-1111-2222-3333-444444444445")
        val composableFactoryIid = Guid("bcbcbcbc-1111-2222-3333-444444444446")
        var retainedInner: ComObjectReference? = null

        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ComposableOuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = outerIid,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerType<InnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ComposableInnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = innerIid,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                                PlatformAbi.writeInt32(args.single() as RawAddress, 42)
                                KnownHResults.S_OK.value
                            },
                        ),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory(
            WinRtAuthoredActivationFactoryDefinition(
                runtimeClassName = "Sample.Authoring.ComposableOuterComponent",
                implementationType = OuterComponent::class,
                composableFactories = listOf(
                    WinRtAuthoredComposableFactoryDefinition(
                        interfaceId = composableFactoryIid,
                        signature = ComMethodSignature.of(
                            ComAbiValueKind.Pointer,
                            ComAbiValueKind.Pointer,
                            ComAbiValueKind.Pointer,
                        ),
                    ) { baseInterface, innerOut, instanceOut ->
                        assertNotNull(
                            ComWrappersSupport.findObject(
                                baseInterface,
                                OuterComponent::class,
                            ),
                        )
                        retainedInner = ComWrappersSupport.createCCWForObject(InnerComponent, innerIid)
                        val inner = requireNotNull(retainedInner)
                        PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
                        PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
                        KnownHResults.S_OK.value
                    },
                ),
            ),
        )

        ActivationFactory.get("Sample.Authoring.ComposableOuterComponent", composableFactoryIid).use { factory ->
            WinRtAuthoring.createComposableObject(OuterComponent, outerIid) { baseInterface, innerOut, instanceOut ->
                ComVtableInvoker.invokeArgs(factory.pointer, 6, baseInterface, innerOut, instanceOut)
            }.use { composed ->
                assertEquals(
                    OuterComponent,
                    ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(composed.outer.pointer), OuterComponent::class),
                )
                assertNotNull(composed.inner)
                composed.outer.queryInterface(innerIid).getOrThrow().use { forwarded ->
                    PlatformAbi.confinedScope().use { scope ->
                        val result = PlatformAbi.allocateInt32Slot(scope)
                        assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(forwarded.pointer, 6, result))
                        assertEquals(42, PlatformAbi.readInt32(result))
                    }
                }
            }
        }
        retainedInner?.close()
    }

    @Test
    fun authoredActivationFactoryWithoutDefaultConstructorReportsNotImplemented() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("eeeeeeee-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<FactoryOnlyComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FactoryOnlyComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<FactoryOnlyComponent>(
            runtimeClassName = "Sample.Authoring.FactoryOnlyComponent",
        )

        ActivationFactory.get("Sample.Authoring.FactoryOnlyComponent").use { factory ->
            try {
                factory.activateInstance().close()
                fail("Expected ActivateInstance to report E_NOTIMPL.")
            } catch (failure: WinRtNotImplementedException) {
                assertEquals(KnownHResults.E_NOTIMPL, failure.hResult)
            }
        }
    }

    @Test
    fun authoredModuleActivationFactoryUsesRegisteredFactoryAndFallbacks() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("dddddddd-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<FallbackActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FallbackActivatedComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<FallbackActivatedComponent>(
            runtimeClassName = "Sample.Authoring.FallbackActivatedComponent",
            createInstance = ::FallbackActivatedComponent,
        )
        WinRtAuthoring.registerActivationFactoryFallback { runtimeClassName, requestedInterface ->
            if (runtimeClassName == "Sample.Authoring.ForwardedComponent") {
                ComWrappersSupport.tryGetAuthoringActivationFactory(
                    "Sample.Authoring.FallbackActivatedComponent",
                    requestedInterface,
                ).pointer
            } else {
                PlatformAbi.nullPointer
            }
        }

        val missing = WinRtAuthoring.getActivationFactory("Sample.Authoring.MissingComponent")
        assertTrue(PlatformAbi.isNull(missing))
        val forwarded = WinRtAuthoring.getActivationFactory("Sample.Authoring.ForwardedComponent")
        assertTrue(!PlatformAbi.isNull(forwarded))
        ActivationFactoryReference(PlatformAbi.toRawComPtr(forwarded)).use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        FallbackActivatedComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authoredHostBridgeMatchesReferenceDllGetActivationFactoryShape() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("77777777-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<HostActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.HostActivatedComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<HostActivatedComponent>(
            runtimeClassName = "Sample.Authoring.HostActivatedComponent",
            createInstance = ::HostActivatedComponent,
        )

        PlatformAbi.confinedScope().use { scope ->
            val factoryOut = PlatformAbi.allocatePointerSlot(scope)
            assertEquals(
                KnownHResults.E_INVALIDARG.value,
                WinRtAuthoringHostBridge.dllGetActivationFactory(PlatformAbi.nullPointer, factoryOut),
            )
            HString.createReference("Sample.Authoring.HostActivatedComponent").use { runtimeClass ->
                assertEquals(
                    KnownHResults.E_INVALIDARG.value,
                    WinRtAuthoringHostBridge.dllGetActivationFactory(runtimeClass.handle, PlatformAbi.nullPointer),
                )
            }
            HString.createReference("Sample.Authoring.MissingComponent").use { missingClass ->
                assertEquals(
                    0x80040111.toInt(),
                    WinRtAuthoringHostBridge.dllGetActivationFactory(missingClass.handle, factoryOut),
                )
                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(factoryOut)))
            }
            HString.createReference("Sample.Authoring.HostActivatedComponent").use { runtimeClass ->
                assertEquals(
                    KnownHResults.S_OK.value,
                    WinRtAuthoringHostBridge.dllGetActivationFactory(runtimeClass.handle, factoryOut),
                )
                val factoryPointer = PlatformAbi.readPointer(factoryOut)
                assertTrue(!PlatformAbi.isNull(factoryPointer))
                ActivationFactoryReference(PlatformAbi.toRawComPtr(factoryPointer)).use { factory ->
                    factory.activateInstance().use { instance ->
                        assertNotNull(
                            ComWrappersSupport.findObject(
                                PlatformAbi.fromRawComPtr(instance.pointer),
                                HostActivatedComponent::class,
                            ),
                        )
                    }
                }
            }
            assertEquals(KnownHResults.S_FALSE.value, WinRtAuthoringHostBridge.dllCanUnloadNow())
        }
    }

    @Test
    fun hostExportRegistryStoresEntriesWithoutReplacingExistingRegistrations() {
        WinRtAuthoringHostExportRegistry.clearRegisteredHostExportsForTests()
        try {
            WinRtAuthoringHostExportRegistry.registerHostExports("Sample.Authoring.HostExportsOne", EmptyHostExportsOne)
            WinRtAuthoringHostExportRegistry.registerHostExports("Sample.Authoring.HostExportsTwo", EmptyHostExportsTwo)

            assertEquals(
                EmptyHostExportsOne,
                WinRtAuthoringHostExportRegistry.hostExports("Sample.Authoring.HostExportsOne"),
            )
            assertEquals(
                EmptyHostExportsTwo,
                WinRtAuthoringHostExportRegistry.hostExports("Sample.Authoring.HostExportsTwo"),
            )
        } finally {
            WinRtAuthoringHostExportRegistry.clearRegisteredHostExportsForTests()
        }
    }

    @Test
    fun composableObjectForwardsOuterQueryInterfaceToInnerAfterFactoryComposition() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("11111111-1111-1111-1111-111111111111")
        val innerIid = Guid("22222222-2222-2222-2222-222222222222")
        var outerInvoked = 0

        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = outerIid,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature()) {
                                outerInvoked += 1
                                KnownHResults.S_OK.value
                            },
                        ),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerType<InnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.InnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = innerIid,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                                PlatformAbi.writeInt32(args.single() as RawAddress, 42)
                                KnownHResults.S_OK.value
                            },
                        ),
                        isDefault = true,
                    ),
                ),
            ),
        )

        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(OuterComponent, outerIid) { baseInterface, innerOut, instanceOut ->
            val outer = IUnknownReference(PlatformAbi.toRawComPtr(baseInterface), preventReleaseOnDispose = true)
            outer.queryInterface(outerIid).getOrThrow().use { queried ->
                ComVtableInvoker.invoke(queried.pointer, 6)
            }
            retainedInner = ComWrappersSupport.createCCWForObject(InnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(1, outerInvoked)
            assertNotNull(composed.inner)
            composed.outer.queryInterface(innerIid).getOrThrow().use { forwarded ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocateInt32Slot(scope)
                    assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(forwarded.pointer, 6, result))
                    assertEquals(42, PlatformAbi.readInt32(result))
                }
            }
        }
        retainedInner?.close()
    }

    @Test
    fun composableObjectRegistersInnerAndResultPointersForFindObject() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("33333333-3333-3333-3333-333333333333")
        val innerIid = Guid("44444444-4444-4444-4444-444444444444")
        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = outerIid,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerType<InnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.InnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = innerIid,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )

        val outerComponent = OuterComponent
        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(outerComponent, outerIid) { _, innerOut, instanceOut ->
            retainedInner = ComWrappersSupport.createCCWForObject(InnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(composed.outer.pointer), OuterComponent::class),
            )
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(composed.instance.pointer), OuterComponent::class),
            )
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(requireNotNull(composed.inner).pointer), OuterComponent::class),
            )
        }
        retainedInner?.close()
    }

    @Test
    fun composableObjectDetectsAggregatedReferenceTrackerInner() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("55555555-5555-5555-5555-555555555555")
        val innerIid = Guid("66666666-6666-6666-6666-666666666666")
        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(outerIid, methods = emptyList(), isDefault = true),
                ),
            ),
        )
        WinRtAuthoring.registerType<TrackedInnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.TrackedInnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(innerIid, methods = emptyList(), isDefault = true),
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = io.github.composefluent.winrt.runtime.IID.IReferenceTracker,
                        methods = emptyList(),
                        baseKind = WinRtComInterfaceBaseKind.IUnknown,
                    ),
                ),
            ),
        )

        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(OuterComponent, outerIid) { _, innerOut, instanceOut ->
            retainedInner = ComWrappersSupport.createCCWForObject(TrackedInnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(true, composed.isAggregatedReferenceTrackerObject)
        }
        retainedInner?.close()
    }

    private object OuterComponent

    private object InnerComponent

    private object TrackedInnerComponent

    private class ActivatableComponent

    private class FactoryBackedComponent

    private class FactoryOnlyComponent

    private class FallbackActivatedComponent

    private class HostActivatedComponent

    private object EmptyHostExportsOne : WinRtAuthoringHostExports {
        override fun registerActivationFactories() = Unit

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            KnownHResults.REGDB_E_CLASSNOTREG.value
    }

    private object EmptyHostExportsTwo : WinRtAuthoringHostExports {
        override fun registerActivationFactories() = Unit

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            KnownHResults.REGDB_E_CLASSNOTREG.value
    }
}
