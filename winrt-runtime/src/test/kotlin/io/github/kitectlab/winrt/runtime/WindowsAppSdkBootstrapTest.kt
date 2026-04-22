package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsAppSdkBootstrapTest {

    @Test
    fun discovers_bootstrap_library_or_reports_absence() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val library = WindowsAppSdkBootstrap.discoverBootstrapLibrary()
        if (library != null) {
            assertTrue(library.path, library.path.endsWith(".dll", ignoreCase = true))
        } else {
            assertTrue(WindowsAppSdkBootstrap.initialize().isFailure)
        }
    }
}
