package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

class NativeDebugLauncherTest {
    @Test fun application_waits_for_debugger_and_preserves_arguments() = withLaunch { directory, process, descriptor, output, expected ->
        assertFalse("CREATE_SUSPENDED must stop application code before main", Files.exists(output))
        Files.writeString(directory.resolve("ready"), descriptor["token"]!!.jsonPrimitive.content)
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        assertEquals(Files.readString(directory.resolve("launcher.log")), 0, process.exitValue())
        assertEquals(expected, Files.readAllLines(output).map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) })
        assertFalse(Files.exists(directory.resolve("debug.json")))
        assertFalse(Files.exists(directory.resolve("ready")))
    }

    @Test fun cancelled_handoff_terminates_only_its_suspended_child() = withLaunch { directory, process, descriptor, output, _ ->
        val pid = descriptor["pid"]!!.jsonPrimitive.long
        assertTrue(ProcessHandle.of(pid).orElseThrow().isAlive)
        Files.writeString(directory.resolve("cancel"), "cancel")
        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        assertNotEquals(0, process.exitValue())
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
        assertFalse(Files.exists(output))
        assertFalse(Files.exists(directory.resolve("debug.json")))
    }

    private fun withLaunch(action: (Path, Process, JsonObject, Path, List<String>) -> Unit) {
        assumeTrue(isWindowsHost())
        val directory = Files.createTempDirectory("native debug launch ")
        var childPid: Long? = null
        var launch: Process? = null
        try {
            val executable = directory.resolve("probe.exe")
            val build = directory.resolve("build.ps1")
            Files.writeString(build, """
                ${'$'}ErrorActionPreference = 'Stop'
                Add-Type -OutputAssembly '${executable.toString().replace("'", "''")}' -OutputType ConsoleApplication -TypeDefinition @'
                using System;
                using System.IO;
                using System.Text;
                public static class Probe {
                    public static void Main(string[] args) {
                        var result = new string[args.Length - 1];
                        for (int i = 1; i < args.Length; i++) result[i-1] = Convert.ToBase64String(Encoding.UTF8.GetBytes(args[i]));
                        File.WriteAllLines(args[0], result);
                    }
                }
                '@
            """.trimIndent())
            val compiler = ProcessBuilder("powershell.exe", "-NoProfile", "-File", build.toString()).redirectErrorStream(true)
                .redirectOutput(directory.resolve("build.log").toFile()).start()
            assertTrue(compiler.waitFor(20, TimeUnit.SECONDS))
            assertEquals(Files.readString(directory.resolve("build.log")), 0, compiler.exitValue())
            val output = directory.resolve("arguments.txt")
            val expected = listOf("one two", "", "quoted \"value\"", "C:\\trailing\\", "中文")
            val config = directory.resolve("launch.json")
            Files.writeString(config, buildJsonObject {
                put("executable", executable.toString()); put("directory", directory.toString()); put("debugDirectory", directory.toString())
                put("arguments", JsonArray((listOf(output.toString()) + expected).map(::JsonPrimitive)))
            }.toString())
            val script = directory.resolve("launch.ps1")
            Files.writeString(script, nativeDebugLauncherScript)
            launch = ProcessBuilder("powershell.exe", "-NoProfile", "-File", script.toString(), "-Configuration", config.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("launcher.log").toFile()).start()
            val descriptorFile = directory.resolve("debug.json")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (!Files.exists(descriptorFile) && launch.isAlive && System.nanoTime() < deadline) Thread.sleep(25)
            assertTrue(Files.readString(directory.resolve("launcher.log")), Files.exists(descriptorFile))
            val descriptor = Json.parseToJsonElement(Files.readString(descriptorFile)).jsonObject
            childPid = descriptor["pid"]!!.jsonPrimitive.long
            action(directory, launch, descriptor, output, expected)
        } finally {
            childPid?.let { ProcessHandle.of(it).ifPresent { child -> if (child.isAlive) child.destroyForcibly() } }
            launch?.let { if (it.isAlive) it.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } } }
        }
    }
}
