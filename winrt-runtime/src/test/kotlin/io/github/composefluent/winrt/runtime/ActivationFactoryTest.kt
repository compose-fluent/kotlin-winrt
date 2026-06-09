package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ActivationFactoryJvmTest {
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
