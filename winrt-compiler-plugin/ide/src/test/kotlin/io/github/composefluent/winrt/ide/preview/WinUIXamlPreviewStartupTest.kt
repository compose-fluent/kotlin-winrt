package io.github.composefluent.winrt.ide.preview

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WinUIXamlPreviewStartupTest {
    private fun route(wants: Boolean = true, connected: Boolean = false, cached: Boolean = true, ready: Boolean = true) = runBlocking {
        val events = mutableListOf<String>()
        startPreferredXamlPreview(wants, connected,
            { events += "validate"; cached }, { events += "native" },
            { events += "await"; ready }, { events += "sdk" })
        events
    }
    @Test fun cancellingBasicBuildDoesNotAutomaticallyStartNative() = runBlocking {
        var active = true
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { false }, { events += "native" },
            { false }, { events += "sdk"; active = false }, { active })
        assertEquals(listOf("sdk"), events)
    }
    @Test fun cancellationDuringCandidateLookupDoesNotStartEitherHost() = runBlocking {
        var active = true
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { active = false; true }, { events += "native" },
            { false }, { events += "sdk" }, { active })
        assertEquals(emptyList<String>(), events)
    }
    @Test fun validNativeCacheSkipsSdkStartup() {
        assertEquals(listOf("validate", "native", "await"), route())
    }
    @Test fun coldProjectShowsSdkBeforeBuildingNative() {
        assertEquals(listOf("validate", "sdk", "native"), route(cached = false))
    }
    @Test fun slowOrFailedCachedHostRevealsSdkWithoutStartingProjectTwice() {
        assertEquals(listOf("validate", "native", "await", "sdk"), route(ready = false))
    }
    @Test fun basicOnlyAndAlreadyConnectedDoNotLaunchUnneededHosts() {
        assertEquals(listOf("sdk"), route(wants = false))
        assertEquals(emptyList<String>(), route(connected = true))
    }
}
