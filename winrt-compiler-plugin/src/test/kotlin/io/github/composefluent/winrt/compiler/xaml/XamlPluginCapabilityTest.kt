package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class XamlPluginCapabilityTest {
    @Test
    fun external_index_adds_members_and_interface_with_private_instance_dispatch() {
        // Toolchain capability gate G1. WinRT identity semantics are deliberately outside this fixture.
        val root = Files.createTempDirectory("winrt-xaml-fir-probe").toFile()
        val previous = System.getProperty("winrt.test.xamlProbeIndex")
        try {
            val index = File(root, "classes.txt").apply { writeText("probe.Page\n") }
            System.setProperty("winrt.test.xamlProbeIndex", index.absolutePath)
            val plugin = File(root, "probe.jar")
            val classes = File(javaClass.protectionDomain.codeSource.location.toURI())
            JarOutputStream(plugin.outputStream()).use { jar ->
                classes.resolve("io/github/composefluent/winrt/compiler/xaml").walkTopDown()
                    .filter { it.isFile && it.extension == "class" }.forEach { file ->
                        jar.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath))
                        jar.write(file.readBytes())
                        jar.closeEntry()
                    }
                jar.putNextEntry(JarEntry("META-INF/services/org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar"))
                jar.write("io.github.composefluent.winrt.compiler.xaml.XamlProbeRegistrar".toByteArray())
                jar.closeEntry()
            }
            val source = File(root, "Page.kt").apply { writeText("""
                package probe
                interface Connector { fun connect(): String }
                class Page {
                    private var clicks = 0
                    private fun onClick(): String = namedElement + (++clicks)
                }
                class Unrelated
                fun exercise(): String {
                    val first = Page()
                    val connector: Connector = first
                    return connector.connect() + ":" + first.connect() + ":" + Page().connect()
                }
            """.trimIndent()) }
            val destination = File(root, "compiled")
            val stdlib = File(Unit::class.java.protectionDomain.codeSource.location.toURI())
            compile(source, destination, stdlib.absolutePath, plugin)
            URLClassLoader(arrayOf(destination.toURI().toURL()), javaClass.classLoader).use { loader ->
                assertEquals("element1:element2:element1", loader.loadClass("probe.PageKt").getMethod("exercise").invoke(null))
                val page = loader.loadClass("probe.Page")
                assertTrue(java.lang.reflect.Modifier.isPrivate(page.getDeclaredMethod("onClick").modifiers))
                assertFalse(java.lang.reflect.Modifier.isStatic(page.getDeclaredField("namedElement").modifiers))
                assertTrue(page.methods.none { it.name == "setNamedElement" })
                assertTrue(loader.loadClass("probe.Unrelated").interfaces.isEmpty())
            }
            // Public generated declarations must survive metadata serialization for a separate consumer.
            val consumer = File(root, "Consumer.kt").apply { writeText("""
                import probe.Page
                import probe.Connector
                fun use(page: Page): String { val connector: Connector = page; return page.namedElement + connector.connect() }
            """.trimIndent()) }
            compile(consumer, File(root, "consumer"), stdlib.absolutePath + File.pathSeparator + destination.absolutePath)
            // An absent index entry must not enhance an unrelated class with the same API.
            index.writeText("probe.OtherPage\n")
            compile(source, File(root, "absent"), stdlib.absolutePath, plugin, ExitCode.COMPILATION_ERROR)
        } finally {
            if (previous == null) System.clearProperty("winrt.test.xamlProbeIndex")
            else System.setProperty("winrt.test.xamlProbeIndex", previous)
            root.deleteRecursively()
        }
    }

    private fun compile(source: File, output: File, classpath: String, plugin: File? = null, expected: ExitCode = ExitCode.OK) {
        val arguments = mutableListOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
            "-d", output.absolutePath, source.absolutePath)
        if (plugin != null) arguments += "-Xplugin=${plugin.absolutePath}"
        val diagnostics = ByteArrayOutputStream()
        val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
        assertEquals(diagnostics.toString(), expected, result)
    }
}
