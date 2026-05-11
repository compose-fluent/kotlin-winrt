package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinRtJvmFfmDowncallHandlesTest {
    @Test
    fun caches_hresult_downcall_handles_by_abi_shape() {
        val before = WinRtJvmFfmDowncallHandles.cachedHResultHandleCount()

        val first = WinRtJvmFfmDowncallHandles.hResult("Int32,RawAddress")
        val second = WinRtJvmFfmDowncallHandles.hResult("Int32,RawAddress")
        val different = WinRtJvmFfmDowncallHandles.hResult("Int32,RawAddress,RawAddress")

        assertSame(first, second)
        assertNotSame(first, different)
        assertTrue(WinRtJvmFfmDowncallHandles.cachedHResultHandleCount() >= before + 2)
    }
}
