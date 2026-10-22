package io.github.composefluent.winrt.ide.debug

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real SB API and Kotlin 2.4 KDebug.cpp contract, not simulated debugger responses. */
class WinRTNativeDebuggerBackendTest {
    @Test fun launches_hits_source_breakpoints_expands_fields_and_steps() {
        val lldbOption = System.getProperty("winrt.ide.nativeDebugLldb")
        val konanOption = System.getProperty("winrt.ide.nativeDebugKonanHome")
        assumeTrue("Opt-in Windows LLDB integration", lldbOption != null && konanOption != null)
        val lldb = Path.of(lldbOption)
        val konan = Path.of(konanOption)
        val directory = Files.createTempDirectory("winrt-native-debug-test-")
        try {
            val source = directory.resolve("NativeDebuggerFixture.kt")
            javaClass.getResourceAsStream("/native-debug/NativeDebuggerFixture.kt").use { Files.copy(requireNotNull(it), source) }
            val lines = Files.readAllLines(source)
            val breakpointLine = lines.indexOfFirst { "// BREAKPOINT" in it } + 1
            val stepLine = lines.indexOfFirst { "// STEP" in it } + 1
            Files.copy(Path.of(System.getProperty("winrt.ide.nativeDebugHeader")), directory.resolve("kotlin_winrt_debug.h"))
            val native = directory.resolve("anchor.c")
            Files.writeString(native, "#include \"kotlin_winrt_debug.h\"\nvoid kotlin_winrt_debug_anchor(void) { kotlin_winrt_retain_debug_api(); }\n")
            val bitcode = directory.resolve("anchor.bc")
            run(directory, lldb.parent.resolve("clang.exe").toString(), "--target=x86_64-pc-windows-gnu", "-ffreestanding",
                "-emit-llvm", "-c", native.toString(), "-o", bitcode.toString())
            val executable = directory.resolve("fixture.exe")
            val arguments = directory.resolve("compile.args")
            Files.writeString(arguments, listOf(source.toString(), "-g", "-native-library", bitcode.toString(), "-o", executable.toString())
                .joinToString("\n") { "\"${it.replace('\\', '/')}\"" })
            run(directory, konan.resolve("bin/konanc.bat").toString(), "@$arguments")
            val events = LinkedBlockingQueue<JsonObject>()
            WinRTNativeDebuggerBackend.start(lldb, events::offer).use { backend ->
                fun request(command: String, vararg fields: Pair<String, Any?>) = backend.request(command, *fields).get(25, TimeUnit.SECONDS)
                fun stopped(): JsonObject {
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                    while (System.nanoTime() < deadline) {
                        val event = events.poll(1, TimeUnit.SECONDS) ?: continue
                        if (event["event"].asString == "stopped") return event
                        assertFalse(event.toString(), event["event"].asString in setOf("closed", "error", "terminated"))
                    }
                    error("LLDB did not stop at the source location")
                }
                request("configure", "konanHome" to konan.toString(), "executable" to executable.toString())
                val breakpoint = request("breakpoint", "file" to source.toString(), "line" to breakpointLine)
                assertTrue(breakpoint["locations"].asInt > 0)
                request("launch", "directory" to directory.toString())
                val stop = stopped()
                val thread = stop["thread"].asLong
                val generation = stop["generation"].asInt
                val stack = request("stack", "thread" to thread)
                assertEquals(breakpointLine, stack.getAsJsonArray("frames")[0].asJsonObject["line"].asInt)
                val values = request("variables", "thread" to thread, "frame" to 0, "generation" to generation).getAsJsonArray("values")
                val record = values.map { it.asJsonObject }.single { it["name"].asString == "record" }
                val children = request("children", "thread" to thread, "frame" to 0, "generation" to generation, "value" to record["id"].asInt)
                    .getAsJsonArray("values").map { it.asJsonObject }.associate { it["name"].asString.removePrefix("*") to it["value"].asString }
                assertEquals("native fields", children["text"])
                assertEquals("42", children["count"])
                request("stepOver", "thread" to thread)
                val stepped = stopped()
                val after = request("stack", "thread" to stepped["thread"].asLong)
                assertEquals(stepLine, after.getAsJsonArray("frames")[0].asJsonObject["line"].asInt)
                request("detach")
            }
        } finally {
            // Only the fresh test-owned directory is traversed.
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } } }
        }
    }

    private fun run(directory: Path, vararg arguments: String) {
        val log = directory.resolve("build.log")
        val process = ProcessBuilder(*arguments).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Native fixture build timed out") }
        assertEquals(Files.readString(log), 0, process.exitValue())
    }
}
