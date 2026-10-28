package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class WinRTPreparedDeploymentTest {
    @Test fun cancellation_waits_for_the_writer_and_removes_once() {
        var removed = 0
        val candidate = WinRTPreparedDeployment(Path.of("base"), Path.of("candidate")) { _, _ -> removed++ }
        candidate.abandon()
        assertEquals(0, removed)
        candidate.buildFinished()
        candidate.abandon()
        candidate.buildFinished()
        assertEquals(1, removed)
    }
    @Test fun completed_but_unconsumed_preparation_is_removed() {
        var removed = 0
        val candidate = WinRTPreparedDeployment(Path.of("base"), Path.of("candidate")) { _, _ -> removed++ }
        candidate.buildFinished()
        assertEquals(0, removed)
        candidate.abandon()
        assertEquals(1, removed)
        assertThrows(IllegalStateException::class.java) { candidate.claim() }
    }
    @Test fun running_session_keeps_its_deployment() {
        var removed = 0
        val candidate = WinRTPreparedDeployment(Path.of("base"), Path.of("candidate")) { _, _ -> removed++ }
        assertThrows(IllegalStateException::class.java) { candidate.claim() }
        candidate.buildFinished()
        candidate.claim()
        candidate.abandon()
        assertEquals(0, removed)
    }
}
