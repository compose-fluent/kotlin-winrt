package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch
import java.nio.file.Path

/** A cancelled preparation may still be writing files. Release its directory only
 * after Gradle finishes, unless the running session has taken ownership. */
internal class WinRTPreparedDeployment(
    private val base: Path,
    val executable: Path,
    private val discard: (Path, Path) -> Unit = WinRTPreparedLaunch::discard,
) {
    private var finished = false
    private var abandoned = false
    private var claimed = false
    private var removed = false

    @Synchronized fun buildFinished() { finished = true; removeIfUnused() }
    @Synchronized fun abandon() { abandoned = true; removeIfUnused() }
    @Synchronized fun claim() {
        check(finished && !abandoned && !removed) { "The prepared deployment is unavailable." }
        claimed = true
    }
    private fun removeIfUnused() {
        if (finished && abandoned && !claimed && !removed) {
            discard(base, executable)
            removed = true
        }
    }
}
