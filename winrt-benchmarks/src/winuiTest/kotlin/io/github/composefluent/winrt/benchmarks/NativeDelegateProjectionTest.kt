package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.ClassWithMarshalingRoutines
import benchmarkcomponent.ProvideInt
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.MarshalDelegate
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRTObjectDisposedException
import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class NativeDelegateProjectionTest {
    // CsWinRT 3 write_delegate_comwrappers_callback binds typed invocation to the RCW reference;
    // WindowsRuntimeDelegateMarshaller independently retains that reference when passing it out.
    @Test
    fun native_delegate_preserves_identity_and_independent_abi_ownership() {
        RuntimeScope.initializeMultithreaded().use {
            WinRTProjectionSupportIntrinsic.ensureInitialized()
            val component = ClassWithMarshalingRoutines()
            try {
                val projected = assertNotNull(component.existingIntDelegate)
                val reference = assertIs<IWinRTObject>(projected).nativeObject
                try {
                    assertEquals(4, projected())
                    assertSame(projected, component.existingIntDelegate)
                    val ownedPointer = MarshalDelegate.fromProjected(projected)
                    try {
                        assertEquals(reference.pointer.value, ownedPointer.value)
                        reference.close()
                        assertFailsWith<WinRTObjectDisposedException> { projected() }

                        assertNotNull(MarshalDelegate.fromAbi(ownedPointer, ProvideInt.Metadata.DESCRIPTOR)).use {
                            assertEquals(4, it.invoke(emptyList()))
                        }
                    } finally {
                        MarshalDelegate.disposeAbi(ownedPointer, ProvideInt.Metadata.DESCRIPTOR)
                    }
                } finally {
                    reference.close()
                }
            } finally {
                (component as IWinRTObject).nativeObject.close()
            }
        }
    }
}
