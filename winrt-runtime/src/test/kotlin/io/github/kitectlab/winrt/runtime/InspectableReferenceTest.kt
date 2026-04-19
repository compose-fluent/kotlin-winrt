package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class InspectableReferenceTest {
    @Test
    fun can_read_runtime_class_name_from_activation_factory_result() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeSingleThreaded()
        val roResult = JvmWinRtRuntime.initializeSingleThreaded()
        val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
        try {
            val inspectable = factory.activateInstance()
            inspectable.use {
                val runtimeClass = it.getRuntimeClassName()
                assertEquals("Windows.Data.Json.JsonObject", runtimeClass)
            }
        } finally {
            factory.close()
            if (roResult.isSuccess) {
                JvmWinRtRuntime.uninitialize()
            }
            if (comResult.isSuccess && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }
}
