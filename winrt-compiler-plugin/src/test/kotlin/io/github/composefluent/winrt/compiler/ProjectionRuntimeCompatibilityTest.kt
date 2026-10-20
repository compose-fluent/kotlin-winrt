package io.github.composefluent.winrt.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectionRuntimeCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun checks_resolved_runtime_binaries_instead_of_embedded_tool_versions() {
        // InteropGenerator.Emit.CreateInteropReferences: runtime assembly major/minor validation.
        for ((contracts, implementation) in listOf(1 to 1, 99 to 1, 1 to 99, 1 to null)) {
            val root = temporary.newFolder()
            val library = root.resolve("Runtime.kt").apply {
                writeText("package io.github.composefluent.winrt.runtime\n" +
                    "const val WINRT_PROJECTION_RUNTIME_ABI_VERSION: Int = $contracts\n" +
                    implementation?.let { "const val WINRT_RUNTIME_IMPLEMENTATION_ABI_VERSION: Int = $it\n" }.orEmpty())
            }
            val stdlib = File(Unit::class.java.protectionDomain.codeSource.location.toURI()).absolutePath
            val libraryOutput = root.resolve("runtime")
            assertEquals(ExitCode.OK, compile(listOf("-classpath", stdlib, "-d", libraryOutput.path, library.path)).first)
            val source = root.resolve("Consumer.kt").apply { writeText("fun answer(): Int = 42") }
            val manifest = root.resolve("compiler-support.tsv").apply { writeText("kind\tclassName\tsourceFile\tentries\n") }
            val plugins = System.getProperty("winrt.test.callsitePluginClasspath").split(File.pathSeparator) +
                System.getProperty("winrt.test.fullPluginJar")
            val (exit, diagnostics) = compile(listOf("-classpath", listOf(stdlib, libraryOutput.path).joinToString(File.pathSeparator),
                "-Xplugin=${plugins.joinToString(",")}",
                "-P", "plugin:io.github.composefluent.winrt.compiler:compilerSupportManifest=${manifest.path}",
                "-d", root.resolve("consumer").path, source.path))
            val compatible = contracts == 1 && implementation == 1
            assertEquals(diagnostics, if (compatible) ExitCode.OK else ExitCode.COMPILATION_ERROR, exit)
            if (!compatible) assertTrue(diagnostics, diagnostics.contains("Incompatible resolved Kotlin/WinRT runtime", ignoreCase = true))
        }
    }

    private fun compile(arguments: List<String>): Pair<ExitCode, String> {
        val diagnostics = ByteArrayOutputStream()
        val exit = PrintStream(diagnostics).use { output ->
            K2JVMCompiler().exec(output, *(listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17") + arguments).toTypedArray())
        }
        return exit to diagnostics.toString(Charsets.UTF_8.name())
    }
}
