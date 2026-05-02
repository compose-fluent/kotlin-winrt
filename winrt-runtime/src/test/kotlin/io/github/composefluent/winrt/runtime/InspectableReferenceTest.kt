package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun inspectable_reference_can_compare_identity_across_iunknown_query() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeSingleThreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                val inspectable = factory.activateInstance()
                inspectable.use { reference ->
                    reference.queryInterface(IID.IUnknown).getOrThrow().use { unknown ->
                        assertTrue(reference.sameIdentity(unknown))
                        assertTrue(unknown.sameIdentity(reference))
                    }
                }
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun inspectable_typed_view_matches_legacy_runtime_class_name_lookup() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeSingleThreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                val inspectable = factory.activateInstance()
                inspectable.use { reference ->
                    val typedView = reference.asTypedView()
                    assertEquals("Windows.Data.Json.JsonObject", typedView.getRuntimeClassName())
                    assertEquals(reference.getRuntimeClassName(), typedView.getRuntimeClassName())
                }
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun inspectable_reference_returns_null_for_missing_interface_query() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeSingleThreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                val inspectable = factory.activateInstance()
                inspectable.use { reference ->
                    val missing = reference.tryQueryInterface(Guid("00000000-0000-0000-0000-00000000DEAD"))
                    assertEquals(null, missing)
                }
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun inspectable_reference_is_idempotently_disposable_and_rejects_late_calls() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeSingleThreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                val inspectable = factory.activateInstance()
                assertFalse(inspectable.isDisposed)
                inspectable.close()
                inspectable.close()
                assertTrue(inspectable.isDisposed)

                try {
                    inspectable.tryGetRuntimeClassName()
                    throw AssertionError("Expected disposed reference to reject calls")
                } catch (error: WinRtObjectDisposedException) {
                    assertTrue(error.message!!.contains("disposed"))
                }
            } finally {
                factory.close()
            }
        }
    }
}
