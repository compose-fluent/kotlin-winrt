@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.runtime.NativeRuntimeApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.*
import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeComRefCountTest {
    @Test
    fun ref_count_boundary_preserves_null_shape_errors() = memScoped<Unit> {
        // CsWinRT ObjectReference.Release uses Marshal.Release; Native keeps the
        // existing Kotlin diagnostics at this corresponding platform boundary.
        assertFailsWith<IllegalStateException> { WinRTPlatformApi.releaseRaw(RawAddress.Null) }
        val instance = alloc<COpaquePointerVar>()
        val address = RawAddress(instance.ptr.rawValue.toLong())
        instance.value = null
        assertFailsWith<IllegalStateException> { WinRTPlatformApi.releaseRaw(address) }
        val vtable = allocArray<COpaquePointerVar>(3)
        vtable[1] = null
        vtable[2] = null
        instance.value = vtable
        assertFailsWith<IllegalStateException> { WinRTPlatformApi.addRefRaw(address) }
        assertFailsWith<IllegalStateException> { WinRTPlatformApi.releaseRaw(address) }
    }

    @Test
    fun ref_count_boundary_allows_gc_callbacks_and_full_ulong_results() = memScoped {
        val vtable = allocArray<COpaquePointerVar>(3)
        vtable[1] = staticCFunction(::fullRefCount)
        vtable[2] = staticCFunction(::zeroRefCount)
        val instance = alloc<COpaquePointerVar>()
        instance.value = vtable
        val address = RawAddress(instance.ptr.rawValue.toLong())
        assertEquals(UInt.MAX_VALUE, WinRTPlatformApi.addRefRaw(address))
        assertEquals(0u, WinRTPlatformApi.releaseRaw(address))
    }
}

private fun fullRefCount(instance: COpaquePointer?): UInt {
    check(instance != null)
    GC.collect()
    return UInt.MAX_VALUE
}

private fun zeroRefCount(instance: COpaquePointer?): UInt {
    check(instance != null)
    GC.collect()
    return 0u
}
