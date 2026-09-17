package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.Guid
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundEntryResolutionTest {
    @Test
    fun inbound_rejects_mismatched_types_and_unsupported_directions() {
        for (annotation in listOf("abiType = \"System.Double\"", "direction = WinRTCallSiteParameterDirection.OUT")) {
            compile("""
                package test.inbound.contract
                import io.github.composefluent.winrt.runtime.*
                class Target
                @WinRTProjectionInboundCallSite
                private fun consume(target: Target, @WinRTProjectionParameter($annotation) value: Int) { TODO() }
                fun entry() = winRTProjectionInboundEntryPoint(::consume)
            """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
                assertTrue(output, output.contains("has invalid inbound ABI metadata"))
            }
        }
    }

    @Test
    fun invalid_layout_is_a_compilation_diagnostic() {
        compile("""
            package test.inbound.contract
            import io.github.composefluent.winrt.runtime.*
            @WinRTProjectionAbiType(name="test.inbound.contract.Bad", kind=WinRTProjectionAbiTypeKind.STRUCT, size=-1, alignment=8)
            object Bad
            @WinRTProjectionCallSite
            fun invoke(receiver: ComObjectReference, slot: Int): Unit = TODO()
        """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
            assertTrue(output, output.contains("has invalid ABI metadata"))
            assertTrue(output, output.contains("layout cannot be negative"))
        }
    }

    @Test
    fun inbound_rejects_incompatible_return_metadata() {
        for ((type, body) in listOf("Int" to "1.also { TODO() }", "Unit" to "TODO()")) {
            compile("""
                package test.inbound.result
                import io.github.composefluent.winrt.runtime.*
                class Target
                @WinRTProjectionInboundCallSite(returnAbiType = "System.Double")
                private fun produce(target: Target): $type = $body
                fun entry() = winRTProjectionInboundEntryPoint(::produce)
            """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
                assertTrue(output, !output.contains("IrGenerationExtensionException"))
            }
        }
    }

    @Test
    fun jvm_entry_lookup_uses_actual_file_and_multifile_part_names() {
        for ((packageSuffix, annotations, owner) in listOf(
            Triple("plain", "", "EntryKt"),
            Triple("renamed", "@file:JvmName(\"RenamedEntry\")", "RenamedEntry"),
            Triple("multifile", "@file:JvmName(\"SharedEntry\")\n@file:JvmMultifileClass", "SharedEntry"),
        )) {
            compile("""
                $annotations
                package test.inbound.$packageSuffix
                import io.github.composefluent.winrt.runtime.*
                class Target
                @WinRTProjectionInboundCallSite
                private fun consume(target: Target, @WinRTProjectionParameter(abiType = "System.Int32") value: Int) {
                    TODO("inbound")
                }
                fun entry(): RawAddress {
                    val callback = ::consume
                    val alias = callback
                    return winRTProjectionInboundEntryPoint(alias)
                }
            """.trimIndent(), ExitCode.OK) { directory, _ ->
                assertTrue("Marker-only aliases must not emit function-reference classes",
                    directory.walkTopDown().none { it.name.contains("\$entry\$") })
                assertNotEquals(0L, invokeEntry(directory, "test.inbound.$packageSuffix.$owner"))
            }
        }
    }

    @Test
    fun aliases_used_by_managed_code_keep_their_initializer() {
        compile("""
            package test.inbound.retained
            import io.github.composefluent.winrt.runtime.*
            class Target { var value = 0 }
            @WinRTProjectionInboundCallSite
            private fun consume(target: Target, @WinRTProjectionParameter(abiType = "System.Int32") value: Int) {
                target.value = value
                TODO("inbound")
            }
            fun entry(): Int {
                val target = Target()
                val callback = ::consume
                winRTProjectionInboundEntryPoint(callback)
                callback(target, 7)
                return target.value
            }
        """.trimIndent(), ExitCode.OK) { directory, _ ->
            assertEquals(7, invokeEntry(directory, "test.inbound.retained.EntryKt"))
        }
    }

    @Test
    fun bound_receivers_are_rejected_before_backend_compilation() {
        compile("""
            package test.inbound.bound
            import io.github.composefluent.winrt.runtime.*
            class Target
            object Handler {
                @WinRTProjectionInboundCallSite
                fun consume(target: Target) { TODO("inbound") }
            }
            fun entry(): RawAddress = winRTProjectionInboundEntryPoint(Handler::consume)
        """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
            assertTrue(output, output.contains("top-level function without receivers"))
        }
    }

    private fun invokeEntry(directory: File, owner: String): Any? =
        URLClassLoader(arrayOf(directory.toURI().toURL()), javaClass.classLoader).use { loader ->
            loader.loadClass(owner).getDeclaredMethod("entry").invoke(null)
        }

    @Test
    fun entry_cache_distinguishes_classes_loaded_by_isolated_loaders() {
        compile("""
            package test.inbound.loaders
            import io.github.composefluent.winrt.runtime.*
            class Target
            @WinRTProjectionInboundCallSite
            private fun consume(target: Target) { TODO("inbound") }
            fun entry(): RawAddress = winRTProjectionInboundEntryPoint(::consume)
        """.trimIndent(), ExitCode.OK) { directory, _ ->
            val urls = arrayOf(directory.toURI().toURL())
            URLClassLoader(urls, javaClass.classLoader).use { first ->
                URLClassLoader(urls, javaClass.classLoader).use { second ->
                    val a = first.loadClass("test.inbound.loaders.EntryKt").getDeclaredMethod("entry")
                    val b = second.loadClass("test.inbound.loaders.EntryKt").getDeclaredMethod("entry")
                    val address = a.invoke(null)
                    assertNotEquals(0L, address)
                    assertEquals(address, a.invoke(null))
                    assertNotEquals(address, b.invoke(null))
                }
            }
        }
    }

    @Test
    fun dynamic_and_mutable_references_fail_during_compilation() {
        for (expression in listOf(
            "var callback = ::consume; return winRTProjectionInboundEntryPoint(callback)",
            "return winRTProjectionInboundEntryPoint(dynamic)",
        )) {
            compile("""
                package test.inbound.invalid
                import io.github.composefluent.winrt.runtime.*
                class Target
                @WinRTProjectionInboundCallSite
                private fun consume(target: Target) { TODO("inbound") }
                fun entry(dynamic: Any): RawAddress { $expression }
            """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
                assertTrue(output, output.contains("only direct references and immutable local aliases are supported"))
            }
        }
    }

    @Test
    fun marker_without_local_semantic_entries_fails_during_compilation() {
        compile("""
            package test.inbound.missing
            import io.github.composefluent.winrt.runtime.*
            fun ordinary() {}
            fun entry(): RawAddress = winRTProjectionInboundEntryPoint(::ordinary)
        """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
            assertTrue(output, output.contains("requires a reference to a local @WinRTProjectionInboundCallSite function"))
        }
    }

    @Test
    fun unsupported_declaration_shapes_report_compiler_errors() {
        for (declaration in listOf(
            "class Handler { @WinRTProjectionInboundCallSite fun consume(target: Target) { TODO() } }",
            "@WinRTProjectionInboundCallSite fun String.consume(target: Target) { TODO() }",
            "@WinRTProjectionInboundCallSite suspend fun consume(target: Target) { TODO() }",
            "fun outer() { @WinRTProjectionInboundCallSite fun consume(target: Target) { TODO() } }",
        )) {
            compile("""
                package test.inbound.declarations
                import io.github.composefluent.winrt.runtime.*
                class Target
                $declaration
            """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
                assertTrue(output, output.contains("non-suspend top-level function without receivers"))
            }
        }
    }

    @Test
    fun unused_entry_is_not_emitted_but_managed_body_is_lowered() {
        compile("""
            package test.inbound.unused
            import io.github.composefluent.winrt.runtime.*
            class Target { var value = 0 }
            @WinRTProjectionInboundCallSite
            private fun consume(target: Target) { target.value = 7; TODO() }
            fun entry(): Int { val target = Target(); consume(target); return target.value }
        """.trimIndent(), ExitCode.OK) { directory, _ ->
            assertEquals(7, invokeEntry(directory, "test.inbound.unused.EntryKt"))
            URLClassLoader(arrayOf(directory.toURI().toURL()), javaClass.classLoader).use { loader ->
                assertTrue(loader.loadClass("test.inbound.unused.EntryKt").declaredMethods.none {
                    it.name.startsWith("kotlinWinRTInbound_")
                })
            }
        }
    }

    @Test
    fun nonterminal_and_nested_placeholders_are_rejected() {
        for (body in listOf(
            "val action = { TODO() }; action()",
            "TODO(); println(target)",
            "if (target.hashCode() == 0) TODO()",
            "target.also { println(it); TODO() }",
        )) {
            compile("""
                package test.inbound.placeholder
                import io.github.composefluent.winrt.runtime.*
                class Target
                @WinRTProjectionInboundCallSite
                private fun consume(target: Target) { $body }
            """.trimIndent(), ExitCode.COMPILATION_ERROR) { _, output ->
                assertTrue(output, output.contains("must end with a Unit TODO() statement"))
            }
        }
    }

    @Test
    fun unrelated_nested_todo_is_preserved_when_terminal_marker_exists() {
        compile("""
            package test.inbound.business
            import io.github.composefluent.winrt.runtime.*
            class Target
            @WinRTProjectionInboundCallSite
            private fun consume(target: Target) {
                val action = { TODO("business failure") }
                action()
                TODO("inbound")
            }
            fun entry() { consume(Target()) }
        """.trimIndent(), ExitCode.OK) { directory, _ ->
            try {
                invokeEntry(directory, "test.inbound.business.EntryKt")
                throw AssertionError("The business TODO must still throw")
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                assertTrue(failure.cause is NotImplementedError)
                assertTrue(failure.cause!!.message!!.contains("business failure"))
            }
        }
    }

    private fun compile(source: String, expected: ExitCode, verify: (File, String) -> Unit) {
        val directory = Files.createTempDirectory("winrt-inbound-resolution").toFile()
        try {
            val input = File(directory, "Entry.kt").apply { writeText(source) }
            val destination = File(directory, "classes")
            val classpath = listOf(Guid::class.java, Unit::class.java).map {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
            val plugins = System.getProperty("winrt.test.callsitePluginClasspath")
                .split(File.pathSeparator).joinToString(",")
            val output = ByteArrayOutputStream()
            val exit = PrintStream(output).use { stream ->
                K2JVMCompiler().exec(stream, "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                    "-classpath", classpath, "-Xplugin=$plugins", "-d", destination.absolutePath, input.absolutePath)
            }
            assertEquals(output.toString(), expected, exit)
            verify(destination, output.toString())
        } finally {
            directory.deleteRecursively()
        }
    }
}
