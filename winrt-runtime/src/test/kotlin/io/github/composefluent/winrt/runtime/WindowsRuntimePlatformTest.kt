package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowsRuntimePlatformJvmTest {
    @Test
    fun format_message_uses_windows_system_message_table() {
        assumeTrue(PlatformRuntime.isWindows)

        val message = ExceptionHelpers.formatMessage(KnownHResults.ERROR_FILE_NOT_FOUND)
        assertTrue(!message.isNullOrBlank())
    }

    @Test
    fun set_error_info_accepts_managed_error_info_bridge() {
        assumeTrue(PlatformRuntime.isWindows)

        ExceptionHelpers.setErrorInfo(IllegalStateException("managed failure"))
        assertNotNull(ExceptionHelpers.formatMessage(ExceptionHelpers.E_FAIL))
    }
}
