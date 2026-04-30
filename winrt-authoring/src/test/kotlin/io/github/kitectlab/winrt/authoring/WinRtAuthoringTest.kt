package io.github.kitectlab.winrt.authoring

import io.github.kitectlab.winrt.runtime.ComAbiValueKind
import io.github.kitectlab.winrt.runtime.ComMethodSignature
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.ComWrappersSupport
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ActivationFactoryReference
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.WinRtComInterfaceBaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class WinRtAuthoringTest {
    @Test
    fun authored_activation_factory_creates_default_instance_through_activation_factory() {
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
    fun authored_activation_factory_exposes_factory_interface_members() {
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
    fun authored_module_activation_factory_returns_registered_factory_only() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("cccccccc-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<ModuleActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ModuleActivatedComponent",
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
        WinRtAuthoring.registerActivationFactory<ModuleActivatedComponent>(
            runtimeClassName = "Sample.Authoring.ModuleActivatedComponent",
            createInstance = ::ModuleActivatedComponent,
        )

        val missing = WinRtAuthoring.getActivationFactory("Sample.Authoring.MissingComponent")
        assertTrue(PlatformAbi.isNull(missing))
        val pointer = WinRtAuthoring.getActivationFactory("Sample.Authoring.ModuleActivatedComponent")
        assertTrue(!PlatformAbi.isNull(pointer))
        ActivationFactoryReference(PlatformAbi.toRawComPtr(pointer)).use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        ModuleActivatedComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_module_activation_factory_uses_partial_fallback_on_miss() {
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

        val pointer = WinRtAuthoring.getActivationFactory("Sample.Authoring.ForwardedComponent")
        assertTrue(!PlatformAbi.isNull(pointer))
        ActivationFactoryReference(PlatformAbi.toRawComPtr(pointer)).use { factory ->
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
    fun composable_object_forwards_outer_query_interface_to_inner_after_factory_composition() {
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
            val outer = IUnknownReference(PlatformAbi.toRawComPtr(baseInterface), outerIid, preventReleaseOnDispose = true)
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
    fun composable_object_registers_inner_and_result_pointers_for_find_object() {
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
    fun composable_object_detects_aggregated_reference_tracker_inner() {
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
                        interfaceId = io.github.kitectlab.winrt.runtime.IID.IReferenceTracker,
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

    private class ModuleActivatedComponent

    private class FallbackActivatedComponent
}
