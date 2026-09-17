package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.Guid
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSiteDiagnosticsTest {
    @Test
    fun fixed_abi_placeholder_cannot_discard_user_logic() {
        compileFailure(
            """
                @WinRTAbiCallSite
                fun invalid(receiver: RawComPtr, slot: Int): Int { println(slot); return TODO() }
            """.trimIndent(),
            "must contain only a TODO() placeholder body",
        )
    }

    @Test
    fun projection_placeholders_cannot_discard_surrounding_logic() {
        listOf(
            "{ println(slot); return TODO() }",
            "= if (slot > 0) TODO() else 42",
            "{\n fun nested(): Int = TODO()\n return slot\n }",
            "= TODO(slot.toString())",
        ).forEach { implementation ->
            compileFailure(
                "@WinRTProjectionCallSite fun invalid(receiver: ComObjectReference, slot: Int): Int $implementation",
                "must contain only a TODO() placeholder body",
            )
        }
    }

    @Test
    fun implicit_local_placeholder_cannot_discard_surrounding_logic() {
        compileFailure(
            """
                fun invalid(receiver: ComObjectReference, slot: Int): Int {
                    val __winrtCallSiteArgument0 = receiver
                    val __winrtCallSiteArgument1 = slot
                    @WinRTProjectionCallSite
                    val __winrtCallSiteResult: Int = run { println(slot); TODO() }
                    return __winrtCallSiteResult
                }
            """.trimIndent(),
            "must contain only a TODO() placeholder initializer",
        )
    }

    @Test
    fun invalid_fixed_signature_reports_source_and_stage() {
        compileFailure(
            """
                @WinRTAbiCallSite
                fun invalid(receiver: Int, slot: Int): Int = 0
            """.trimIndent(),
            "cannot lower fixed ABI signature: receiver must be a non-null RawComPtr",
        )
    }

    @Test
    fun explicit_argument_expressions_are_rejected_at_the_marker() {
        compileFailure(
            """
                fun invalid(receiver: ComObjectReference, slot: Int): Int {
                    @WinRTProjectionCallSite
                    val result: Int = winRTProjectionCallSiteArguments(receiver, slot, 42)
                    return result
                }
            """.trimIndent(),
            "cannot bind explicit arguments: argument 0 must reference a local variable",
        )
    }

    private fun compileFailure(body: String, expected: String) {
        val directory = Files.createTempDirectory("winrt-callsite-diagnostic").toFile()
        try {
            val source = File(directory, "InvalidCallSite.kt").apply {
                writeText("import io.github.composefluent.winrt.runtime.*\n$body\n")
            }
            val classpath = listOf(Guid::class.java, Unit::class.java).map {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
            val pluginPaths = System.getProperty("winrt.test.callsitePluginClasspath")
                .split(File.pathSeparator).joinToString(",")
            val output = ByteArrayOutputStream()
            val exit = PrintStream(output).use { stream ->
                K2JVMCompiler().exec(stream,
                    "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                    "-classpath", classpath, "-Xplugin=$pluginPaths",
                    "-d", File(directory, "classes").absolutePath, source.absolutePath,
                )
            }
            val diagnostics = output.toString(Charsets.UTF_8.name())
            assertEquals(diagnostics, ExitCode.COMPILATION_ERROR, exit)
            assertTrue(diagnostics, diagnostics.contains(expected))
            assertTrue(diagnostics, Regex("InvalidCallSite\\.kt:\\d+:\\d+").containsMatchIn(diagnostics))
        } finally {
            directory.deleteRecursively()
        }
    }
}
