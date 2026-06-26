package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinRTJvmFfmDowncallHandlesTest {
    @Test
    fun caches_hresult_downcall_handles_by_abi_shape() {
        val before = WinRTJvmFfmDowncallHandles.cachedHResultHandleCount()

        val first = WinRTJvmFfmDowncallHandles.hResult("Int32,RawAddress")
        val second = WinRTJvmFfmDowncallHandles.hResult("Int32,RawAddress")
        val different = WinRTJvmFfmDowncallHandles.hResult("Int32,RawAddress,RawAddress")

        assertSame(first, second)
        assertNotSame(first, different)
        assertTrue(WinRTJvmFfmDowncallHandles.cachedHResultHandleCount() >= before + 2)
    }

    @Test
    fun supports_by_value_struct_downcall_shapes_from_layout_tokens() {
        val first = WinRTJvmFfmDowncallHandles.hResult("Struct8_4,RawAddress")
        val second = WinRTJvmFfmDowncallHandles.hResult("Struct8_4,RawAddress")
        val wider = WinRTJvmFfmDowncallHandles.hResult("Struct16_8,RawAddress")

        assertSame(first, second)
        assertNotSame(first, wider)
    }
}
