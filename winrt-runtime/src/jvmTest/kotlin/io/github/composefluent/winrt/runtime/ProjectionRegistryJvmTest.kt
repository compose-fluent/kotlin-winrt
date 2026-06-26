package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionRegistryJvmTest {
    @Test
    fun generic_array_class_literal_keeps_jvm_type_name_support_behavior() {
        assertEquals("", TypeNameSupport.getNameForType(Array<String>::class, setOf(TypeNameGenerationFlag.GenerateBoxedName)))
    }

    @Test
    fun type_name_lookup_initializes_generated_runtime_class_metadata() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals(
            "Contoso.SelfRegisteringRuntimeClass",
            TypeNameSupport.getNameForType(TestSelfRegisteringRuntimeClass::class),
        )
        assertEquals(1, TestSelfRegisteringRuntimeClass.Metadata.registerCount)

        assertEquals(
            "Contoso.SelfRegisteringRuntimeClass",
            TypeNameSupport.getNameForType(TestSelfRegisteringRuntimeClass::class),
        )
        assertEquals(1, TestSelfRegisteringRuntimeClass.Metadata.registerCount)
    }
}

private class TestSelfRegisteringRuntimeClass {
    companion object Metadata {
        var registerCount: Int = 0
            private set

        init {
            register()
        }

        fun register() {
            if (registerCount > 0) {
                return
            }
            registerCount += 1
            Projections.registerCustomAbiTypeMapping(
                TestSelfRegisteringRuntimeClass::class,
                TestSelfRegisteringRuntimeClass::class,
                "Contoso.SelfRegisteringRuntimeClass",
                isRuntimeClass = true,
            )
            WinRtTypeRegistry.register<TestSelfRegisteringRuntimeClass>(
                projectedTypeName = "Contoso.SelfRegisteringRuntimeClass",
                runtimeClassName = "Contoso.SelfRegisteringRuntimeClass",
                isRuntimeClass = true,
                isWindowsRuntimeType = true,
            )
        }
    }
}
