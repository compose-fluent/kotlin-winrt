package io.github.composefluent.winrt.runtime

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal class ProjectionClassLoaderPayload

class ProjectionClassLoaderOwnershipTest {

    @AfterTest fun cleanup() = WinRTTypeRegistry.clearForTests()

    @Test fun identical_binary_names_loaded_twice_are_not_one_projection_type() {
        WinRTTypeRegistry.clearForTests()
        val original = ProjectionClassLoaderPayload::class.java
        val bytes = original.getResourceAsStream("/${original.name.replace('.', '/')}.class")!!.use { it.readBytes() }
        fun isolated() = object : ClassLoader(original.classLoader) {
            fun define() = defineClass(original.name, bytes, 0, bytes.size)
        }.define().kotlin
        val first = isolated()
        val second = isolated()
        assertNotEquals(first, second)
        registerGeneratedProjectionTypeIndex(first, "Ownership.LoadedTwice", "RuntimeClass", "")
        assertFailsWith<IllegalArgumentException> {
            registerGeneratedProjectionTypeIndex(second, "Ownership.LoadedTwice", "RuntimeClass", "")
        }
    }
}
