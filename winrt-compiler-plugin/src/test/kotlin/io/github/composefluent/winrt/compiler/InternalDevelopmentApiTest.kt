package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.WinUIXamlLoadState
import io.github.composefluent.winrt.runtime.Guid
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

class InternalDevelopmentApiTest {
    @Test
    fun development_bridges_require_explicit_consumer_opt_in() {
        // CsWinRT Runtime2 public infrastructure uses PrivateImplementationDetail
        // diagnostics. Kotlin enforces the equivalent boundary in consumer compilation.
        val root = Files.createTempDirectory("winrt-internal-api-").toFile()
        try {
            val classpath = listOf(Unit::class.java, WinUIXamlLoadState::class.java, Guid::class.java)
                .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
                .distinct().joinToString(File.pathSeparator)
            val source = File(root, "Consumer.kt")
            fun compile(text: String): Pair<ExitCode, String> {
                source.writeText(text)
                val diagnostics = ByteArrayOutputStream()
                val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it,
                    "-no-stdlib", "-no-reflect", "-classpath", classpath,
                    "-d", File(root, "classes").absolutePath, source.absolutePath) }
                return result to diagnostics.toString()
            }
            val bridges = listOf(
                "fun probe() = isWinUIXamlDevelopmentBuild()",
                "fun probe() = WinUIXamlHotReloadProtocol.VERSION",
                "fun probe() = WinUIXamlVisualBounds(0.0, 0.0, 1.0, 1.0)",
                "fun probe() = WinUIXamlDesignBindingSession.create(\"probe.Page\")",
                "fun probe(value: WinUIXamlRestartParticipant) = value.saveRestartState()",
                "fun probe(value: WinUIXamlBindingState) = value.stopDesignBindings()",
            )
            for (bridge in bridges) {
                val text = "import io.github.composefluent.winrt.runtime.*\n$bridge"
                val rejected = compile(text)
                assertEquals(rejected.second, ExitCode.COMPILATION_ERROR, rejected.first)
                assertTrue(rejected.second, rejected.second.contains("Internal Kotlin WinRT infrastructure API", ignoreCase = true))
                val allowed = compile("@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)\n$text")
                assertEquals(allowed.second, ExitCode.OK, allowed.first)
            }
            val ordinary = compile("import io.github.composefluent.winrt.runtime.*\nfun probe() = WinUIXamlLoadState()")
            assertEquals(ordinary.second, ExitCode.OK, ordinary.first)
        } finally {
            root.deleteRecursively()
        }
    }
}
