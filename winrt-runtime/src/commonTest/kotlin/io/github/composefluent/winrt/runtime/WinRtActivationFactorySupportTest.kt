package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinRtActivationFactorySupportTest {
    @Test
    fun activation_factory_ccw_exposes_activate_instance() {
        ComWrappersSupport.clearRegistriesForTests()
        var activated = false

        ComWrappersSupport.createCCWForObject(
            WinRtActivationFactory {
                activated = true
                ComWrappersSupport.createCCWForObject(Any())
            },
            IID.IActivationFactory,
        ).use { factory ->
            val instancePointer = PlatformAbi.confinedScope().use { scope ->
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = factory.pointer,
                        slot = IActivationFactoryVftblSlots.ActivateInstance,
                        arg0 = instanceOut,
                    ),
                ).requireSuccess()
                PlatformAbi.readPointer(instanceOut)
            }

            assertTrue(activated)
            assertFalse(PlatformAbi.isNull(instancePointer))
            IInspectableReference(instancePointer.asRawComPtr(), IID.IInspectable).close()
        }
    }

    @Test
    fun activation_factory_ccw_exposes_additional_factory_interfaces() {
        ComWrappersSupport.clearRegistriesForTests()
        val factoryInterfaceId = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        ComWrappersSupport.createCCWForActivationFactory(
            WinRtActivationFactory {
                ComWrappersSupport.createCCWForObject(Any())
            },
            factoryInterfaces = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = factoryInterfaceId,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
                            PlatformAbi.writeInt32(args.single() as RawAddress, 42)
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        ).use { factory ->
            factory.queryInterface(factoryInterfaceId).getOrThrow().use { customFactory ->
                PlatformAbi.confinedScope().use { scope ->
                    val valueOut = PlatformAbi.allocateInt32Slot(scope)
                    HResult(
                        ComVtableInvoker.invokeArgs(
                            instance = customFactory.pointer,
                            slot = 6,
                            arg0 = valueOut,
                        ),
                    ).requireSuccess()

                    assertEquals(42, PlatformAbi.readInt32(valueOut))
                }
            }
        }
    }
}
