package io.github.composefluent.winrt.runtime

import java.io.File
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.module.ModuleFinder
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class WinRTJvmInboundLifetimeTest {
    @Test
    fun closed_named_module_uses_caller_lookup_and_releases_scoped_loader() = fixture { classes ->
        val (reference, closedScope) = invokeNamedModule(classes)
        repeat(100) {
            if (reference.get() == null) return@repeat
            System.gc()
            Thread.sleep(50)
        }
        assertNull(reference.get(), "A closed inbound scope must not retain its projection loader")
        closedScope.close() // Keeping the closed scope itself alive must not retain the loader either.
    }

    private fun invokeNamedModule(classes: File): Pair<WeakReference<ClassLoader>, WinRTJvmProjectionInboundScope> {
        val finder = ModuleFinder.of(classes.toPath())
        val configuration = ModuleLayer.boot().configuration().resolve(finder, ModuleFinder.of(), setOf("boundary.callbacks"))
        val controller = ModuleLayer.defineModulesWithOneLoader(configuration, listOf(ModuleLayer.boot()), javaClass.classLoader)
        val module = controller.layer().findModule("boundary.callbacks").orElseThrow()
        controller.addReads(module, WinRTJvmProjectionInboundScope::class.java.module)
        val loader = controller.layer().findLoader("boundary.callbacks")
        val scope = WinRTJvmProjectionInboundScope(loader)
        val entry = loader.loadClass("boundary.callback.Callback").getMethod("entry")
        try {
            val pointer = entry.invoke(null) as Long
            assertEquals(pointer, entry.invoke(null))
            assertEquals(73, Linker.nativeLinker().downcallHandle(MemorySegment.ofAddress(pointer),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)).invokeWithArguments(0L))
            assertFailsWith<IllegalStateException> { WinRTJvmProjectionInboundScope(loader) }
        } finally { scope.close() }
        assertIs<IllegalStateException>(assertFailsWith<InvocationTargetException> { entry.invoke(null) }.cause)
        return WeakReference(loader) to scope
    }

    @Test
    fun legacy_two_argument_descriptor_resolves_the_callers_loader() = fixture { classes ->
        URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader).use { loader ->
            WinRTJvmProjectionInboundScope(loader).use {
                val owner = loader.loadClass("boundary.callback.Callback")
                val legacy = owner.getMethod("legacy").invoke(null)
                assertEquals(legacy, owner.getMethod("entry").invoke(null))
            }
        }
    }

    private fun fixture(action: (File) -> Unit) {
        val root = Files.createTempDirectory("winrt-jvm-inbound").toFile()
        try {
            val module = File(root, "module-info.java").apply {
                writeText("module boundary.callbacks { exports boundary.callback; }")
            }
            val source = File(root, "Callback.java").apply { writeText("""
                package boundary.callback;
                import java.lang.invoke.MethodHandles;
                import io.github.composefluent.winrt.runtime.WinRTProjectionInboundEntryPoint_jvmKt;
                public class Callback {
                    private static int invoke(long self) { return 73; }
                    public static long entry() {
                        return WinRTProjectionInboundEntryPoint_jvmKt.winRTJvmProjectionInboundEntryPoint(
                            Callback.class.getName(), "invoke", MethodHandles.lookup());
                    }
                    public static long legacy() {
                        return WinRTProjectionInboundEntryPoint_jvmKt.winRTJvmProjectionInboundEntryPoint(
                            Callback.class.getName(), "invoke");
                    }
                }
            """.trimIndent()) }
            val classes = File(root, "classes")
            val runtime = File(WinRTJvmProjectionInboundScope::class.java.protectionDomain.codeSource.location.toURI())
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", runtime.absolutePath,
                "--add-reads", "boundary.callbacks=ALL-UNNAMED", "-d", classes.absolutePath,
                module.absolutePath, source.absolutePath))
            action(classes)
        } finally { root.deleteRecursively() }
    }
}
