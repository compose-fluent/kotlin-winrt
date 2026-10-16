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
                MarshalInterfaceReference(queriedMarshal.acquireOwnedPointer().asRawAddress(), IID.IMarshal).use { marshal ->
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

    @Test
    fun weak_reference_callbacks_resolve_their_own_managed_instance() {
        if (!PlatformRuntime.isWindows) return
        ComWrappersSupport.clearRegistriesForTests()

        // CsWinRT 3 IWeakReferenceSourceImpl.GetWeakReference / ManagedWeakReference.Resolve:
        // shared ABI entry points must recover state from thisPtr, not the first host.
        val first = ManagedInteropTarget("first")
        val second = ManagedInteropTarget("second")
        ComWrappersSupport.createCCWForObject(first, IID.IInspectable).use { firstHost ->
            ComWrappersSupport.createCCWForObject(second, IID.IInspectable).use { secondHost ->
                assertNotNull(firstHost.tryGetWeakReference()).use { firstWeak ->
                    assertNotNull(secondHost.tryGetWeakReference()).use { secondWeak ->
                        repeat(3) {
                            assertNotNull(firstWeak.resolve(IID.IUnknown)).use { resolved ->
                                assertSame(first, ComWrappersSupport.findObject(resolved.pointer.asRawAddress(), ManagedInteropTarget::class))
                            }
                            assertNotNull(secondWeak.resolve(IID.IInspectable)).use { resolved ->
                                assertSame(second, ComWrappersSupport.findObject(resolved.pointer.asRawAddress(), ManagedInteropTarget::class))
                            }
                        }
                    }
                }
            }
        }
    }

    private data class ManagedInteropTarget(
        val name: String,
    )
}
