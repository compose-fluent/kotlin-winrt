package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class InteropRuntimeHooksTest {
    @Test
    fun managed_ccw_exposes_agile_object_and_imarshal() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        ComWrappersSupport.clearRegistriesForTests()

        val target = ManagedInteropTarget("marshal")
        ComWrappersSupport.createCCWForObject(target, IID.IInspectable).use { inspectable ->
            val agile = inspectable.queryInterface(IID.IAgileObject).getOrNull()
            assertNotNull(agile)
            agile.use {
            }

            inspectable.queryInterface(IID.IMarshal).getOrThrow().use { queriedMarshal ->
                MarshalInterfaceReference(queriedMarshal.getRefPointer().asRawAddress(), IID.IMarshal).use { marshal ->
                    assertEquals(
                        FreeThreadedMarshalerSupport.inProcFreeThreadedMarshalerIid(),
                        marshal.getUnmarshalClass(IID.IUnknown),
                    )
                }
            }
        }
    }

    @Test
    fun managed_ccw_exposes_weak_reference_source() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        ComWrappersSupport.clearRegistriesForTests()

        val target = ManagedInteropTarget("weak")
        ComWrappersSupport.createCCWForObject(target, IID.IInspectable).use { inspectable ->
            val weakReference = inspectable.tryGetWeakReference()
            assertNotNull(weakReference)

            weakReference.use { weak ->
                val resolved = weak.resolve(IID.IInspectable)
                assertNotNull(resolved)
                resolved.use {
                    assertSame(target, ComWrappersSupport.findObject(it.pointer.asRawAddress(), ManagedInteropTarget::class))
                }
            }
        }
    }

    private data class ManagedInteropTarget(
        val name: String,
    )
}
