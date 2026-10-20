package io.github.composefluent.winrt.compiler

import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionSupportReferenceTest {
    @Test fun reference_matches_implementation_identity_without_running_registration() {
        // CsWinRT code_writers.h reference_projection: preserve signatures, emit throw null.
        val entries = listOf(KotlinWinRTProjectionRegistrarEntry(
            kotlinClassName = "sample.Widget", projectedTypeName = "Sample.Widget",
            kind = "RuntimeClass", baseTypeName = "", metadataClassName = "", interfaceIid = "",
        ))
        val implementation = Files.createTempDirectory("winrt-support-implementation-")
        val reference = Files.createTempDirectory("winrt-support-reference-")
        val name = requireNotNull(writeProjectionSupportInitializerClass(entries, implementation, "projection"))
        assertEquals(name, writeProjectionSupportInitializerReferenceClass(entries, reference, "projection"))
        URLClassLoader(arrayOf(reference.toUri().toURL()), javaClass.classLoader).use { loader ->
            // The referenced Widget does not exist; loading the contract must not register it.
            val type = Class.forName(name.replace('/', '.'), true, loader)
            assertEquals(Void.TYPE, type.getDeclaredMethod("initialize").returnType)
            val failure = assertThrows(InvocationTargetException::class.java) {
                type.getDeclaredMethod("initialize").invoke(null)
            }
            assertTrue(failure.cause is NullPointerException)
        }
        Files.walk(reference).use { paths ->
            assertEquals(1L, paths.filter { Files.isRegularFile(it) }.count())
        }
        writeProjectionSupportInitializerReferenceClass(emptyList(), reference, "projection")
        assertFalse(Files.exists(reference.resolve("$name.class")))
    }
}
