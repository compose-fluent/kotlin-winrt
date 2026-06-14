package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeakReferenceInteropTest {
    @Test
    fun native_weak_reference_resolves_ccw_backed_object_identity() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        ComWrappersSupport.clearRegistriesForTests()

        val managed = ManagedWeakReferenceTarget("weak")
        val ccw = ComWrappersSupport.createCCWForObject(managed, IID.IInspectable)
        val target = ProjectedWeakReferenceTarget(PlatformAbi.fromRawComPtr(ccw.getRefPointer()))

        try {
            val weakReference = WeakReferenceInterop.tryCreateNativeWeakReference(target)
            assertNotNull(weakReference)

            weakReference.use { reference ->
                val resolved = WeakReferenceInterop.resolveNativeWeakReference(reference) as? IWinRTObject
                assertNotNull(resolved)
                try {
                    assertTrue(resolved.nativeObject.sameIdentity(target.nativeObject))
                } finally {
                    if (resolved !== target) {
                        resolved.nativeObject.close()
                    }
                }
            }
        } finally {
            target.nativeObject.close()
            ccw.close()
        }
    }

    private data class ManagedWeakReferenceTarget(
        val name: String,
    )

    private class ProjectedWeakReferenceTarget(
        pointer: RawAddress,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
    }
}
