package io.github.composefluent.winrt.runtime

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
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
    fun registry_read_failure_is_treated_as_activation_class_unavailable_for_fallbacks() {
        assertTrue(isActivationClassUnavailable(KnownHResults.REGDB_E_CLASSNOTREG))
        assertTrue(isActivationClassUnavailable(KnownHResults.REGDB_E_READREGDB))
        assertFalse(isActivationClassUnavailable(KnownHResults.E_FAIL))
    }

    @Test
    fun module_path_resolves_working_directory_runtime_assets() {
        val fileName = "kotlin-winrt-runtime-assets-resolve-test.dll"
        val runtimeAssetsRoot = Path("build/kotlin-winrt/runtime-assets")
        val asset = Path("build/kotlin-winrt/runtime-assets/$fileName")
        SystemFileSystem.createDirectories(runtimeAssetsRoot)
        SystemFileSystem.sink(asset).close()
        try {
            assertEquals(
                absolutePath(asset.toString()),
                absolutePath(WinRtPlatformApi.resolveModulePathRaw(fileName)),
            )
        } finally {
            runCatching { SystemFileSystem.delete(asset) }
        }
    }

    @Test
    fun authoring_host_manifest_activation_maps_runtime_classes_to_native_dll_targets() {
        val runtimeAssetsRoot = Path("build/kotlin-winrt/runtime-assets")
        val manifest = Path("build/kotlin-winrt/runtime-assets/runtime-test-authoring-host.host.json")
        SystemFileSystem.createDirectories(runtimeAssetsRoot)
        SystemFileSystem.sink(manifest).buffered().use { sink ->
            sink.writeString(
                """
                {
                  "assemblyName": "runtime-test-authoring-host",
                  "hostExportsClass": "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_runtime_test",
                  "targetArtifact": "runtime_test_authoring_host.dll",
                  "activatableClasses": ["test.RuntimeDefaultTargetThing"],
                  "activatableClassTargets": {
                    "test.RuntimeExplicitTargetThing": "runtime_test_authoring_host.dll",
                    "test.RuntimeJvmOnlyThing": "runtime-test-authoring-host.jar"
                  }
                }
                """.trimIndent(),
            )
        }
        try {
            assertEquals(
                listOf("runtime_test_authoring_host.dll"),
                AuthoringHostManifestActivation.targetDllNamesFor("test.RuntimeDefaultTargetThing"),
            )
            assertEquals(
                listOf("runtime_test_authoring_host.dll"),
                AuthoringHostManifestActivation.targetDllNamesFor("test.RuntimeExplicitTargetThing"),
            )
            assertEquals(emptyList(), AuthoringHostManifestActivation.targetDllNamesFor("test.RuntimeJvmOnlyThing"))
            assertEquals(emptyList(), AuthoringHostManifestActivation.targetDllNamesFor("test.RuntimeMissingThing"))
        } finally {
            runCatching { SystemFileSystem.delete(manifest) }
        }
    }

    @Test
    fun manifest_free_loader_does_not_cache_missing_modules() {
        if (PlatformRuntime.isWindows) {
            DllModule.clearCacheForTests()
            DllModule.tryLoad("Definitely.Missing.Module.dll")
            assertEquals(0, DllModule.cachedModuleCount())
            return
        }

        assertEquals(0, DllModule.cachedModuleCount())
    }

    @Test
    fun manifest_free_loader_uses_default_dll_search_directories() {
        assertEquals(0x00001000, DllModule.loadLibrarySearchDefaultDirs)
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
