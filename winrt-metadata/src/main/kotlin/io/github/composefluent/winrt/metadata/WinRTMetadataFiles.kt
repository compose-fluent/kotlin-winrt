package io.github.composefluent.winrt.metadata

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

/** Development WinMDs are regenerated while IDE discovery reads them. Metadata
 * ownership stays here, like cswinrt's reader database; JVM file handles have
 * Windows sharing constraints even though our parser owns a byte snapshot. */
object WinRTMetadataFiles {
    fun deleteGenerated(path: Path): Boolean = retrySharingConflict { Files.deleteIfExists(path) }

    private fun <T> retrySharingConflict(operation: () -> T): T {
        var delayMillis = 50L
        repeat(6) { attempt ->
            try { return operation() }
            catch (error: FileSystemException) {
                // Sharing violations arrive as plain FileSystemException. Do not
                // retry typed permission, missing-parent or non-empty-directory errors.
                if (error.javaClass != FileSystemException::class.java || attempt == 5) throw error
                try { Thread.sleep(delayMillis) }
                catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
                delayMillis *= 2
            }
        }
        error("Unreachable metadata file retry")
    }
}
