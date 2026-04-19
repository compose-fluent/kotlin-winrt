package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class InspectableReferenceTest {
    @Test
    fun can_read_runtime_class_name_from_activation_factory_result() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeSingleThreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                val inspectable = factory.activateInstance()
                inspectable.use { reference ->
                    val runtimeClass = reference.getRuntimeClassName()
                    assertEquals("Windows.Data.Json.JsonObject", runtimeClass)
                }
            } finally {
                factory.close()
            }
        }
    }
}
