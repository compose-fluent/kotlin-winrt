package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinRTActivationFactorySupportTest {
    @Test
    fun activation_factory_ccw_exposes_activate_instance() {
        ComWrappersSupport.clearRegistriesForTests()
        var activated = false

        ComWrappersSupport.createCCWForObject(
            WinRTActivationFactory {
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
            WinRTActivationFactory {
                ComWrappersSupport.createCCWForObject(Any())
            },
            factoryInterfaces = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = factoryInterfaceId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { args ->
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

    @Test
    fun activation_factory_ccw_returns_hresult_and_error_info_for_factory_exception() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        ComWrappersSupport.clearRegistriesForTests()
        val restrictedErrorInfo = WinRTRestrictedErrorInfo(
            description = "outer activation failure",
            restrictedDescription = "inner activation failure",
            reference = "ACT123",
            capabilitySid = "S-1-15-3-1",
        )

        ComWrappersSupport.createCCWForActivationFactory(
            WinRTActivationFactory {
                throw WinRTAccessDeniedException(
                    message = "denied",
                    hResult = KnownHResults.E_ACCESSDENIED,
                    restrictedErrorInfo = restrictedErrorInfo,
                )
            },
        ).use { factory ->
            PlatformAbi.confinedScope().use { scope ->
                val instanceOut = PlatformAbi.allocatePointerSlot(scope)
                val hResult = HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = factory.pointer,
                        slot = IActivationFactoryVftblSlots.ActivateInstance,
                        arg0 = instanceOut,
                    ),
                )

                assertEquals(KnownHResults.E_ACCESSDENIED, hResult)
                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(instanceOut)))

                val roundTrip = ExceptionHelpers.exceptionFor(
                    KnownHResults.E_ACCESSDENIED,
                    "Activation factory callback",
                )
                assertEquals(restrictedErrorInfo, roundTrip.restrictedErrorInfo)
            }
        }
    }
}
