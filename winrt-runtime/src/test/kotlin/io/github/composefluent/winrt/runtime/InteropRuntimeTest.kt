package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class InteropRuntimeJvmTest {
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
}
