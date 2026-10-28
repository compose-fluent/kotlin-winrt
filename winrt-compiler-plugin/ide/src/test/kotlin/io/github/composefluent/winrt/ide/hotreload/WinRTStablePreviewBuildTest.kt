package io.github.composefluent.winrt.ide.hotreload

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class WinRTStablePreviewBuildTest {
    @Test fun changedSourceRetriesFailedAndSuccessfulObsoleteBuilds() = runBlocking {
        for (firstSucceeded in listOf(false, true)) {
            var source = "before"
            var builds = 0
            var retries = 0
            val result = buildStableWinRTPreview({ source }, {
                builds++
                if (builds == 1) { source = "after"; firstSucceeded } else true
            }, { retries++ })
            assertEquals("after", result)
            assertEquals(2, builds)
            assertEquals(1, retries)
        }
    }
    @Test fun stableCompilationFailureDoesNotLoop() = runBlocking {
        var builds = 0
        assertTrue(runCatching { buildStableWinRTPreview({ "same" }, { builds++; false }) }.isFailure)
        assertEquals(1, builds)
    }
    @Test fun cancelledPreparationDoesNotRetryChangedSources() = runBlocking {
        var builds = 0
        var source = "before"
        val ready = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            buildStableWinRTPreview({ source }, {
                builds++; source = "after"; ready.complete(Unit); release.await(); true
            })
        }
        ready.await()
        job.cancelAndJoin()
        assertEquals(1, builds)
    }
}
