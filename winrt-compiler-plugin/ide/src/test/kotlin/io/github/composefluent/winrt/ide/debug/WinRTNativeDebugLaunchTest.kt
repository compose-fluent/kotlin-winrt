package io.github.composefluent.winrt.ide.debug

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

class WinRTNativeDebugLaunchTest {
    @Test fun module_relative_source_navigation_does_not_choose_an_ambiguous_dependency() {
        val root = Files.createTempDirectory("native-sources-")
        try {
            val first = Files.createDirectories(root.resolve("app/src"))
            val second = Files.createDirectories(root.resolve("dependency/src"))
            val file = Files.writeString(first.resolve("Main.kt"), "fun main() {}")
            val directories = listOf(first.parent, second.parent)
            assertEquals(file, resolveNativeSource("src\\Main.kt", directories))
            assertEquals(file, resolveNativeSource(file.toString(), directories))
            Files.writeString(second.resolve("Main.kt"), "fun main() {}")
            assertNull(resolveNativeSource("src/Main.kt", directories))
            assertNull(resolveNativeSource("src/Missing.kt", directories))
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    @Test fun startup_target_requires_matching_executable_and_process_lifetime() {
        val directory = Files.createTempDirectory("native-debug-target-")
        val process = ProcessHandle.current()
        val executable = Path.of(process.info().command().orElseThrow())
        val started = process.info().startInstant().orElseThrow()
        val launch = WinRTNativeDebugLaunch(directory, executable, directory, executable)
        try {
            val descriptor = WinRTNativeDebuggerBackend.json("pid" to process.pid(), "executable" to executable.toString(),
                "startedMillis" to started.toEpochMilli(), "token" to "a".repeat(32))
            val file = directory.resolve("debug.json")
            Files.writeString(file, descriptor.toString())
            assertEquals(process.pid(), launch.awaitTarget { false }.pid)
            descriptor.addProperty("startedMillis", started.toEpochMilli() - 1000)
            Files.writeString(file, descriptor.toString())
            try { launch.awaitTarget { false }; fail("Reused process IDs must be rejected") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("lifetime")) }
            descriptor.addProperty("startedMillis", started.toEpochMilli())
            descriptor.addProperty("executable", directory.toString())
            Files.writeString(file, descriptor.toString())
            try { launch.awaitTarget { false }; fail("A different executable must be rejected") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("selected application")) }
        } finally { Files.deleteIfExists(directory.resolve("debug.json")); Files.delete(directory) }
    }

    @Test fun cancellation_does_not_wait_for_a_build_to_publish_a_process() {
        val directory = Files.createTempDirectory("native-debug-cancel-")
        try {
            val launch = WinRTNativeDebugLaunch(directory, directory, directory, directory)
            launch.cancel()
            assertTrue(Files.isRegularFile(directory.resolve("cancel")))
            try { launch.awaitTarget { true }; fail("Cancelled build must not keep waiting") }
            catch (_: CancellationException) { }
        } finally { Files.deleteIfExists(directory.resolve("cancel")); Files.delete(directory) }
    }
}
