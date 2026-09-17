package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.Guid
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSiteAbiSharingTest {
    @Test
    fun physical_signatures_share_invocations_across_files_without_sharing_typed_bodies() {
        // CsWinRT code_writers.h keeps conversions at the caller and invokes a physical ABI.
        // Kotlin's FFM handle storage must depend on that ABI, not the projected source file.
        val directory = Files.createTempDirectory("winrt-abi-sharing").toFile()
        try {
            val sources = listOf("First" to "RawAddress", "Second" to "RawAddress", "Third" to "Long")
                .map { (name, carrier) ->
                    File(directory, "$name.kt").apply {
                        writeText("""
                            package test.${name.lowercase()}
                            import io.github.composefluent.winrt.runtime.*
                            @WinRTAbiCallSite
                            fun invoke$name(receiver: RawComPtr, slot: Int, value: $carrier): Int = TODO("ABI")
                        """.trimIndent())
                    }
                }
            val classpath = listOf(Guid::class.java, Unit::class.java).map {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
            val pluginPaths = System.getProperty("winrt.test.callsitePluginClasspath")
                .split(File.pathSeparator).joinToString(",")
            val ownersByOrder = listOf(sources, sources.reversed()).mapIndexed { index, ordered ->
                val destination = File(directory, "classes$index")
                val output = ByteArrayOutputStream()
                val exit = PrintStream(output).use { stream ->
                    K2JVMCompiler().exec(stream,
                        "-no-stdlib", "-no-reflect", "-jvm-target", "17",
                        "-classpath", classpath, "-Xplugin=$pluginPaths",
                        "-module-name", "sharing-test", "-d", destination.absolutePath,
                        *ordered.map(File::getAbsolutePath).toTypedArray(),
                    )
                }
                assertEquals(output.toString(), ExitCode.OK, exit)
                val classes = destination.walkTopDown().filter { it.extension == "class" }.map {
                    ClassNode().apply { ClassReader(it.readBytes()).accept(this, 0) }
                }.toList()
                val owners = classes.filter { owner -> owner.fields.any { it.desc == "Ljava/lang/invoke/MethodHandle;" } }
                assertEquals("Address and Long must remain distinct physical carriers", 2, owners.size)
                owners.forEach { owner ->
                    assertTrue(owner.name.startsWith("io/github/composefluent/winrt/generated/abi/"))
                    assertEquals(1, owner.fields.count { it.desc == "Ljava/lang/invoke/MethodHandle;" })
                    assertTrue(owner.fields.single().access and Opcodes.ACC_FINAL != 0)
                }
                val calls = classes.flatMap { it.methods }.filter { it.name.startsWith("invoke") }
                assertEquals(3, calls.size)
                val physicalCalls = owners.flatMap { it.methods }.filter { it.name.startsWith("kotlinWinRTAbiInvoke_") }
                assertEquals(2, physicalCalls.size)
                physicalCalls.forEach { method ->
                    assertEquals(1, method.instructions.toArray().filterIsInstance<MethodInsnNode>()
                        .count { it.owner == "java/lang/invoke/MethodHandle" && it.name == "invokeExact" })
                }
                calls.forEach { method ->
                    assertEquals(1, method.instructions.toArray().filterIsInstance<MethodInsnNode>()
                        .count { it.name.startsWith("kotlinWinRTAbiInvoke_") })
                    assertEquals(0, method.instructions.toArray().filterIsInstance<MethodInsnNode>()
                        .count { it.owner == "java/lang/invoke/MethodHandle" })
                }
                val reads = calls.map { method ->
                    method.instructions.toArray().filterIsInstance<MethodInsnNode>()
                        .single { it.name.startsWith("kotlinWinRTAbiInvoke_") }.owner
                }
                assertEquals(2, reads.toSet().size)
                owners.map { it.name }.toSet()
            }
            assertEquals("Shared owners must not depend on source traversal order", ownersByOrder[0], ownersByOrder[1])
        } finally {
            directory.deleteRecursively()
        }
    }
}
