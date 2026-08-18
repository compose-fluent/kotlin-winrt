package io.github.composefluent.winrt.benchmarks

import benchmarkcomponent.IIntProperties
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRTManagedProjectionStateOwner
import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
import io.github.composefluent.winrt.runtime.tryBorrowWinRTManagedProjectionAbi
import io.github.composefluent.winrt.runtime.winRTProjectionMarshaler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManagedProjectionBorrowingTest {
    @Test
    fun authored_projected_input_borrows_initialized_ccw() {
        RuntimeScope.initializeMultithreaded().use {
            WinRTProjectionSupportIntrinsic.ensureInitialized()
            val value: IIntProperties = ManagedObjectWithInterfaces()
            val typeHandle = IIntProperties.Metadata.TYPE_HANDLE

            val stateOwner = assertIs<WinRTManagedProjectionStateOwner>(value)
            assertNotNull(stateOwner.winRTManagedProjectionState())
            assertTrue(PlatformAbi.isNull(tryBorrowWinRTManagedProjectionAbi(value, typeHandle)))

            winRTProjectionMarshaler(value, typeHandle).close()
            val first = tryBorrowWinRTManagedProjectionAbi(value, typeHandle)
            val second = tryBorrowWinRTManagedProjectionAbi(value, typeHandle)

            assertFalse(PlatformAbi.isNull(first))
            assertEquals(first, second)
        }
    }

}
