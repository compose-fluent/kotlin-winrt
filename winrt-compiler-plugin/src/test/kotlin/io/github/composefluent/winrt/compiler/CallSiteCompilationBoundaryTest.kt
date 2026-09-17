package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.runtime.Guid
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

class CallSiteCompilationBoundaryTest {
    @Test
    fun dependency_codecs_respect_public_friend_and_private_boundaries() = inDirectory { root ->
        // CsWinRT resolves a projected type's marshaler from its owning assembly. The Kotlin
        // contract uses annotated codecs beside that type, including separately compiled modules.
        for (visibility in listOf("public", "internal", "private")) {
            val library = compile(root, "library-$visibility", """
                package boundary.library
                import io.github.composefluent.winrt.runtime.*
                class Value
                @WinRTProjectionAbiType(name="boundary.library.Value", kind=WinRTProjectionAbiTypeKind.PROJECTION,
                    reference=WinRTProjectionAbiReferenceKind.UNKNOWN)
                $visibility object Codec {
                    @WinRTProjectionAbiCodec(role=WinRTProjectionAbiCodecRole.FROM_ABI, type="boundary.library.Value")
                    fun decode(pointer: RawAddress): Value = Value()
                }
            """.trimIndent())
            for (friend in listOf(false, true)) {
                compile(root, "consumer-$visibility-$friend", """
                    package boundary.consumer
                    import io.github.composefluent.winrt.runtime.*
                    import boundary.library.Value
                    @WinRTProjectionCallSite
                    fun read(receiver: ComObjectReference, slot: Int): Value = TODO()
                """.trimIndent(), library, friend,
                    expected = if (visibility == "public" || visibility == "internal" && friend)
                        ExitCode.OK else ExitCode.COMPILATION_ERROR)
            }
        }
    }

    @Test
    fun top_level_dependency_codec_keeps_friend_visibility() = inDirectory { root ->
        val library = compile(root, "top-library", """
            package boundary.library
            import io.github.composefluent.winrt.runtime.*
            class Value
            @WinRTProjectionAbiType(name="boundary.library.Value", kind=WinRTProjectionAbiTypeKind.PROJECTION,
                reference=WinRTProjectionAbiReferenceKind.UNKNOWN)
            @WinRTProjectionAbiCodec(role=WinRTProjectionAbiCodecRole.FROM_ABI, type="boundary.library.Value")
            internal fun decode(pointer: RawAddress): Value = Value()
        """.trimIndent())
        for (friend in listOf(false, true)) {
            compile(root, "top-consumer-$friend", """
                package boundary.consumer
                import io.github.composefluent.winrt.runtime.*
                import boundary.library.Value
                @WinRTProjectionCallSite
                fun read(receiver: ComObjectReference, slot: Int): Value = TODO()
            """.trimIndent(), library, friend,
                expected = if (friend) ExitCode.OK else ExitCode.COMPILATION_ERROR)
        }
    }

    @Test
    fun bootstrap_and_full_plugin_can_lower_the_same_fixed_stub() = inDirectory { root ->
        compile(root, "dual", """
            package boundary.fixed
            import io.github.composefluent.winrt.runtime.*
            @WinRTAbiCallSite
            fun invoke(receiver: RawComPtr, slot: Int, value: Int): Int = TODO()
        """.trimIndent(), fullPlugin = true)
    }

    private fun compile(root: File, name: String, source: String, library: File? = null,
        friend: Boolean = false, expected: ExitCode = ExitCode.OK, fullPlugin: Boolean = false): File {
        val input = File(root, "$name.kt").apply { writeText(source) }
        val destination = File(root, name)
        val classpath = (listOf(Guid::class.java, Unit::class.java).map {
            File(it.protectionDomain.codeSource.location.toURI())
        } + listOfNotNull(library)).distinct().joinToString(File.pathSeparator) { it.absolutePath }
        val plugins = System.getProperty("winrt.test.callsitePluginClasspath").split(File.pathSeparator) +
            if (fullPlugin) listOf(System.getProperty("winrt.test.fullPluginJar")) else emptyList()
        val arguments = mutableListOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
            "-Xplugin=${plugins.joinToString(",")}", "-d", destination.absolutePath, input.absolutePath)
        if (friend) arguments += "-Xfriend-paths=${library!!.absolutePath}"
        val output = ByteArrayOutputStream()
        val result = PrintStream(output).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
        assertEquals(output.toString(), expected, result)
        return destination
    }

    private fun inDirectory(action: (File) -> Unit) {
        val root = Files.createTempDirectory("winrt-compilation-boundary").toFile()
        try { action(root) } finally { root.deleteRecursively() }
    }
}
