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
            assertTrue(output, output.contains("requires an unbound reference"))
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
