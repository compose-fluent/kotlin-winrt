package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class WinRTJvmFfmDowncallHandlesTest {
    @Test
    fun fixed_hresult_downcall_handles_are_stable_and_shape_specific() {
        val first = WinRTJvmFfmDowncallHandles.hResultInt32Address
        val second = WinRTJvmFfmDowncallHandles.hResultInt32Address
        val different = WinRTJvmFfmDowncallHandles.hResultAddressAddress

        assertSame(first, second)
        assertNotSame(first, different)
    }
}
