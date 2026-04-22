package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test

class InteropRuntimeHooksTest {
    @Test
    fun managed_ccw_exposes_agile_object_and_imarshal() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val target = ManagedInteropTarget("marshal")
        ComWrappersSupport.createCCWForObject(target, IID.IInspectable).use { inspectable ->
            val agile = inspectable.queryInterface(IID.IAgileObject).getOrNull()
            assertNotNull(agile)
            agile!!.use {
            }

            inspectable.queryInterface(IID.IMarshal).getOrThrow().use { queriedMarshal ->
                MarshalInterfaceReference(queriedMarshal.getRefPointer(), IID.IMarshal).use { marshal ->
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
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()

        val target = ManagedInteropTarget("weak")
        ComWrappersSupport.createCCWForObject(target, IID.IInspectable).use { inspectable ->
            val weakReference = inspectable.tryGetWeakReference()
            assertNotNull(weakReference)

            weakReference!!.use { weak ->
                val resolved = weak.resolve(IID.IInspectable)
                assertNotNull(resolved)
                resolved!!.use {
                    assertSame(target, ComWrappersSupport.findObject(it.pointer, ManagedInteropTarget::class))
                }
            }
        }
    }

    private data class ManagedInteropTarget(
        val name: String,
    )
}
