package io.github.composefluent.winrt.metadata

import java.io.FileInputStream
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Windows tooling adaptation for cswinrt reader database ownership. The actual
 * OS sharing violation matters: an in-memory mock cannot validate this race. */
class WinRTMetadataFilesTest {
    @Test fun regeneration_waits_for_a_temporary_windows_reader() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val file = Files.createTempFile("winmd-sharing", ".winmd")
        val input = FileInputStream(file.toFile())
        val released = CountDownLatch(1)
        try {
            assertThrows(FileSystemException::class.java) { Files.delete(file) }
            val reader = thread {
                try { Thread.sleep(150); input.close() }
                finally { released.countDown() }
            }
            assertTrue(WinRTMetadataFiles.deleteGenerated(file))
            assertTrue(released.await(2, TimeUnit.SECONDS))
            reader.join()
            assertFalse(Files.exists(file))
            assertFalse(WinRTMetadataFiles.deleteGenerated(file))
        } finally { input.close(); Files.deleteIfExists(file) }
    }
}
