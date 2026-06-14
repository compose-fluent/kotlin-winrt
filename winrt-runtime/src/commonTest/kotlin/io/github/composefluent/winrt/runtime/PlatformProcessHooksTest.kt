package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertNotNull

class PlatformProcessHooksTest {
    @Test
    fun shutdown_hook_registration_returns_closeable_on_windows() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val hook = PlatformProcessHooks.registerShutdownHook {
        }
        assertNotNull(hook)

        hook.close()
        hook.close()
    }
}
