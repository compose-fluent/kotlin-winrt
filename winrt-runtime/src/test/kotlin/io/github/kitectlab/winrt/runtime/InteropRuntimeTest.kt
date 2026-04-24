package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class InteropRuntimeTest {
    @Test
    fun agile_reference_resolves_same_identity_for_winrt_object() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeMultithreaded().use {
            WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
                AgileReference(instance).use { agileReference ->
                    val resolved = agileReference.get()
                    assertNotNull(resolved)
                    resolved!!.use {
                        assertTrue(it.sameIdentity(instance))
                    }
                }
            }
        }
    }

    @Test
    fun weak_reference_source_resolves_same_identity_for_winrt_object() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeMultithreaded().use {
            WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
                instance.queryInterface(IID.IWeakReferenceSource).getOrThrow().use { weakReferenceSource ->
                    val weakReference = WeakReferenceSourceReference(
                        weakReferenceSource.pointer.asRawAddress(),
                        IID.IWeakReferenceSource,
                    ).getWeakReference()
                    assertNotNull(weakReference)
                    weakReference!!.use {
                        val resolved = it.resolve(IID.IUnknown)
                        assertNotNull(resolved)
                        resolved!!.use { resolvedReference ->
                            assertTrue(resolvedReference.sameIdentity(instance))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun context_owner_can_reenter_current_context_without_throwing() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeMultithreaded().use {
            val contextToken = Context.getContextToken()
            val contextCallback = Context.getContextCallback()
            assertNotNull(contextCallback)
            contextCallback!!.use {
                var invoked = false
                Context.callInContext(
                    contextCallback = it,
                    contextToken = contextToken,
                    callback = { _: Unit -> invoked = true },
                    state = Unit,
                )
                assertTrue(invoked)
            }
        }
    }
}
