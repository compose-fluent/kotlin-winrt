package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivationFactoryTest {
    @Test
    fun manifest_free_candidate_names_walk_namespace_prefixes() {
        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.Controls.dll",
                "Microsoft.UI.Xaml.dll",
                "Microsoft.UI.dll",
                "Microsoft.dll",
            ),
            ManifestFreeActivation.candidateDllNames("Microsoft.UI.Xaml.Controls.Button"),
        )
    }

    @Test
    fun non_windows_activation_returns_class_not_registered() {
        if (PlatformRuntime.isWindows) {
            return
        }

        val result = ActivationFactory.tryGet("Windows.Data.Json.JsonObject")
        assertEquals(KnownHResults.REGDB_E_CLASSNOTREG, result.hResult)
        assertFalse(result.isSuccess)
    }

    @Test
    fun activation_factory_iid_is_stable() {
        assertTrue(ActivationFactory.iActivationFactoryIid.toString().isNotBlank())
    }

    @Test
    fun activation_factory_cache_reuses_identity_for_default_factory() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            ActivationFactory.clearCacheForTests()

            val first = ActivationFactory.get("Windows.Data.Json.JsonObject")
            val second = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                assertTrue(first !== second)
                assertTrue(first.sameIdentity(second))
                assertEquals(1, ActivationFactory.cachedFactoryCount())
            } finally {
                first.close()
                second.close()
                ActivationFactory.clearCacheForTests()
            }
        }
    }

    @Test
    fun activation_factory_cache_reuses_identity_for_static_interface_factory() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            ActivationFactory.clearCacheForTests()
            val iidIJsonObjectStatics = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

            val first = ActivationFactory.get("Windows.Data.Json.JsonObject", iidIJsonObjectStatics)
            val second = ActivationFactory.get("Windows.Data.Json.JsonObject", iidIJsonObjectStatics)
            try {
                assertTrue(first !== second)
                assertTrue(first.sameIdentity(second))
                assertEquals(1, ActivationFactory.cachedFactoryCount())
            } finally {
                first.close()
                second.close()
                ActivationFactory.clearCacheForTests()
            }
        }
    }

    @Test
    fun activation_factory_typed_view_can_activate_instance() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            val factory = ActivationFactory.get("Windows.Data.Json.JsonObject")
            try {
                factory.asTypedView().activateInstance().use {
                    assertEquals("Windows.Data.Json.JsonObject", it.getRuntimeClassName())
                }
            } finally {
                factory.close()
            }
        }
    }

    @Test
    fun runtime_class_activation_helper_can_activate_instance() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            val instance = WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow()
            instance.use {
                assertEquals("Windows.Data.Json.JsonObject", it.getRuntimeClassName())
            }
        }
    }
}
