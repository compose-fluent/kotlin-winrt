package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionRegistryJvmTest {
    @Test
    fun generic_array_class_literal_keeps_jvm_type_name_support_behavior() {
        assertEquals("", TypeNameSupport.getNameForType(Array<String>::class, setOf(TypeNameGenerationFlag.GenerateBoxedName)))
    }

}
