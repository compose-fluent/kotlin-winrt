package io.github.composefluent.windows.toolkit.gradle

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class NativeDebugOutputTest {
    @Test fun captures_unicode_debug_strings_and_preserves_application_exception_handling() {
        assumeTrue(isWindowsHost())
        val root = Files.createTempDirectory("native-debug-output-")
        val script = root.resolve("capture.ps1")
        val launcher = nativeDebugLauncherScript.substringAfter("Add-Type -TypeDefinition @'\n").substringBefore("\n'@")
        val capture = uwpDevelopmentRunnerScript.substringAfter("Add-Type -TypeDefinition @'\n").substringBefore("\n'@")
        Files.writeString(script, """
            ${'$'}ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [Text.UTF8Encoding]::new(${'$'}false)
            Add-Type -OutputAssembly (Join-Path ${'$'}PSScriptRoot 'probe.exe') -OutputType ConsoleApplication -TypeDefinition @'
            using System;
            using System.Runtime.InteropServices;
            public static class Probe {
                [DllImport("kernel32.dll", CharSet=CharSet.Unicode)] static extern void OutputDebugString(string value);
                public static int Main() {
                    OutputDebugString("Unicode preview: \u4e2d\u6587\n");
                    try { throw new InvalidOperationException("handled application exception"); }
                    catch (InvalidOperationException) { OutputDebugString("Exception handler survived\n"); }
                    return 7;
                }
            }
            '@
            Add-Type -TypeDefinition @'
            $launcher
            '@
            Add-Type -TypeDefinition @'
            $capture
            '@
            ${'$'}child = [KotlinWinRT.NativeDebug.SuspendedProcess]::new((Join-Path ${'$'}PSScriptRoot 'probe.exe'), [string[]]@(), ${'$'}PSScriptRoot)
            try {
                [KotlinWinRT.UWP.DebugOutput]::Capture(${'$'}child.ProcessId, ${'$'}child.ThreadId, (Join-Path ${'$'}PSScriptRoot 'output.log'))
            } finally { ${'$'}child.Dispose() }
        """.trimIndent().replace(Regex("(?m)^ +('@)$"), "$1"))
        var process: Process? = null
        try {
            process = ProcessBuilder("powershell.exe", "-NoProfile", "-File", script.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("test.log").toFile()).start()
            assertTrue("Native debug event pump timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals(String(Files.readAllBytes(root.resolve("test.log")), Charsets.UTF_8), 0, process.exitValue())
            val output = Files.readString(root.resolve("output.log"))
            assertTrue(output, output.contains("Unicode preview: 中文"))
            assertTrue(output, output.contains("Exception handler survived"))
            assertTrue(output, output.contains("first chance"))
            assertTrue(output, output.contains("Native process exited: 7"))
        } finally {
            process?.let { if (it.isAlive) { it.descendants().forEach { child -> child.destroyForcibly() }; it.destroyForcibly().waitFor(5, TimeUnit.SECONDS) } }
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } } }
        }
    }
}
