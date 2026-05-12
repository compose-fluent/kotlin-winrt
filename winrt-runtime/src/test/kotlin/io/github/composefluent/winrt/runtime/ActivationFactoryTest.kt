package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

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
    fun jvm_module_path_prefers_runtime_assets_root_property() {
        val root = Files.createTempDirectory("kotlin-winrt-runtime-assets-")
        val asset = root.resolve("SimpleMathComponent.dll")
        Files.write(asset, byteArrayOf(0))
        withSystemProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName, root.toString()) {
            assertEquals(asset.toString(), WinRtPlatformApi.resolveModulePathRaw(asset.fileName.toString()))
        }
    }

    @Test
    fun jvm_module_path_uses_working_directory_runtime_assets() {
        val workingDirectory = Files.createTempDirectory("kotlin-winrt-working-dir-")
        val assetsRoot = workingDirectory.resolve(WinRtRuntimeAssets.runtimeAssetsDirectoryName)
        Files.createDirectories(assetsRoot)
        val asset = assetsRoot.resolve("SimpleMathComponent.dll")
        Files.write(asset, byteArrayOf(0))
        withSystemProperty("user.dir", workingDirectory.toString()) {
            withSystemProperty(WinRtRuntimeAssets.runtimeAssetsRootPropertyName, null) {
                assertEquals(asset.toString(), WinRtPlatformApi.resolveModulePathRaw(asset.fileName.toString()))
            }
        }
    }

    @Test
    fun activation_factory_cache_reuses_identity_for_default_factory() {
        assumeTrue(PlatformRuntime.isWindows)

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
        assumeTrue(PlatformRuntime.isWindows)

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
        assumeTrue(PlatformRuntime.isWindows)

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
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeMultithreaded().use {
            val instance = WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow()
            instance.use {
                assertEquals("Windows.Data.Json.JsonObject", it.getRuntimeClassName())
            }
        }
    }
}

private fun withSystemProperty(
    name: String,
    value: String?,
    block: () -> Unit,
) {
    val previous = System.getProperty(name)
    try {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
        block()
    } finally {
        if (previous == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, previous)
        }
    }
}
