package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionRegistryJvmTest {
    @Test
    fun generic_array_class_literal_uses_jvm_component_metadata() {
        assertEquals(
            "Windows.Foundation.IReferenceArray`1<String>",
            TypeNameSupport.getNameForType(Array<String>::class, setOf(TypeNameGenerationFlag.GenerateBoxedName)),
        )
    }
}
