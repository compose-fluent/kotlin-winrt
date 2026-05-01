package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
