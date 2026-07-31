package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinRTJvmFfmDowncallHandlesTest {
    @Test
    fun fixed_hresult_downcall_handles_bypass_the_dynamic_shape_cache() {
        val before = WinRTJvmFfmDowncallHandles.cachedHResultHandleCount()

        val first = WinRTJvmFfmDowncallHandles.hResultInt32Address
        val second = WinRTJvmFfmDowncallHandles.hResultInt32Address
        val different = WinRTJvmFfmDowncallHandles.hResultAddressAddress

        assertSame(first, second)
        assertNotSame(first, different)
        assertEquals(before, WinRTJvmFfmDowncallHandles.cachedHResultHandleCount())
    }

    @Test
    fun caches_layout_dependent_by_value_struct_downcall_shapes() {
        val before = WinRTJvmFfmDowncallHandles.cachedHResultHandleCount()
        val first = WinRTJvmFfmDowncallHandles.hResult("Struct8_4,RawAddress")
        val second = WinRTJvmFfmDowncallHandles.hResult("Struct8_4,RawAddress")
        val wider = WinRTJvmFfmDowncallHandles.hResult("Struct16_8,RawAddress")

        assertSame(first, second)
        assertNotSame(first, wider)
        assertTrue(WinRTJvmFfmDowncallHandles.cachedHResultHandleCount() >= before + 2)
    }
}
