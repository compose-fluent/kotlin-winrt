package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.Guid
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallSiteHelperResolutionTest {
    @Test
    fun same_arity_source_overloads_do_not_hide_runtime_helper_signatures() {
        val directory = Files.createTempDirectory("winrt-helper-signatures").toFile()
        try {
            val source = File(directory, "HelperOverloads.kt").apply {
                writeText("""
                    package io.github.composefluent.winrt.runtime
                    fun winRTStringLength(value: Int): Int = error("wrong parameter")
                    fun Any.winRTStringLength(value: String): Int = error("wrong receiver")
                    fun winRTKeepAlive(value: String?): Unit = error("wrong parameter")
                    @WinRTProjectionCallSite
                    fun consumeString(receiver: ComObjectReference, slot: Int, value: String): Unit = TODO("lower")
                """.trimIndent())
            }
            val classpath = listOf(Guid::class.java, Unit::class.java).map {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
            val plugins = System.getProperty("winrt.test.callsitePluginClasspath")
                .split(File.pathSeparator).joinToString(",")
            val destination = File(directory, "classes")
            val output = ByteArrayOutputStream()
            val exit = PrintStream(output).use { stream ->
                K2JVMCompiler().exec(stream, "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                    "-classpath", classpath, "-Xplugin=$plugins", "-d", destination.absolutePath,
                    source.absolutePath)
            }
            assertEquals(output.toString(), ExitCode.OK, exit)
            val owner = ClassNode().apply {
                ClassReader(File(destination,
                    "io/github/composefluent/winrt/runtime/HelperOverloadsKt.class").readBytes()).accept(this, 0)
            }
            val calls = owner.methods.single { it.name == "consumeString" }.instructions.toArray()
                .filterIsInstance<MethodInsnNode>()
            assertFalse(calls.any { it.owner == owner.name && it.name.startsWith("winRT") })
            assertEquals(1, calls.count { it.name.startsWith("kotlinWinRTAbiInvoke_") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
