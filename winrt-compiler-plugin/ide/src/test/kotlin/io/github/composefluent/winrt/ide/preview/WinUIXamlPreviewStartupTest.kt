package io.github.composefluent.winrt.ide.preview

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class WinUIXamlPreviewStartupTest {
    @Test fun nativeBuildNeverStartsCompetingSdkBuild() = runBlocking {
        val result = CompletableDeferred<Boolean>()
        val events = mutableListOf<String>()
        val startup = launch {
            startPreferredXamlPreview(true, false, { events += "native" },
                { result.await() }, { events += "sdk" })
        }
        yield()
        assertEquals(listOf("native"), events)
        result.complete(true)
        startup.join()
        assertEquals(listOf("native"), events)
    }
    @Test fun failedNativeFallsBackOnce() = runBlocking {
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { events += "native" },
            { false }, { events += "sdk" })
        assertEquals(listOf("native", "sdk"), events)
    }
    @Test fun cancelledNativeNeverStartsFallback() = runBlocking {
        var active = true
        val events = mutableListOf<String>()
        startPreferredXamlPreview(true, false, { events += "native" },
            { active = false; false }, { events += "sdk" }, { active })
        assertEquals(listOf("native"), events)
    }
    @Test fun basicOnlyAndConnectedOrCancelledDoNotLaunchUnneededHosts() = runBlocking {
        val events = mutableListOf<String>()
        for ((wants, connected, active) in listOf(Triple(false, false, true),
                Triple(true, true, true), Triple(true, false, false))) {
            startPreferredXamlPreview(wants, connected, { events += "native" },
                { true }, { events += "sdk" }, { active })
        }
        assertEquals(listOf("sdk"), events)
    }
}
