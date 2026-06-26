package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.PlatformRuntime
import io.github.composefluent.winrt.runtime.RawAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class WinRTAuthoringTest {
    @Test
    fun authored_host_manifest_loader_reads_reference_style_target_mappings() {
        val directory = Files.createTempDirectory("kotlin-winrt-authoring-host-schema-")
        val manifestPath = directory.resolve("MappedComponent.host.json")
        Files.writeString(
            manifestPath,
            """
            {
              "schemaVersion": 1,
              "model": "jvm-authoring-host",
              "assemblyName": "MappedComponent",
              "hostExportsClass": "sample.MappedHostExports",
              "targetArtifact": "MappedComponent.jar",
              "activatableClassTargets": {
                "Sample.Authoring.MappedComponent": "MappedComponent.jar"
              }
            }
            """.trimIndent(),
        )

        val manifest = WinRTAuthoringHostManifestLoader.read(manifestPath)

        assertEquals("MappedComponent", manifest.assemblyName)
        assertEquals("sample.MappedHostExports", manifest.hostExportsClass)
        assertEquals("MappedComponent.jar", manifest.targetArtifact)
        assertEquals(
            mapOf("Sample.Authoring.MappedComponent" to "MappedComponent.jar"),
            manifest.activatableClassTargets,
        )
        assertEquals(directory, manifest.sourceDirectory)
    }

    @Test
    fun authored_host_manifest_loader_installs_activation_factory_fallback() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.clearAuthoringActivationFactoryFallbacksForTests()
        WinRTAuthoring.clearActivationFactoryFallbacksForTests()
        WinRTAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRTAuthoringHostManifestLoader.registerHostExports(HostManifestExports::class.java.name, HostManifestExports)

        val directory = Files.createTempDirectory("kotlin-winrt-authoring-host-")
        Files.writeString(
            directory.resolve("HostManifestComponent.host.json"),
            """
            {
              "schemaVersion": 1,
              "model": "jvm-authoring-host",
              "assemblyName": "HostManifestComponent",
              "hostExportsClass": "${HostManifestExports::class.java.name}",
              "activatableClasses": ["Sample.Authoring.HostManifestComponent"]
            }
            """.trimIndent(),
        )

        WinRTAuthoringHostManifestLoader.installFromDirectory(directory)
        ActivationFactory.get("Sample.Authoring.HostManifestComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        HostManifestComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_host_manifest_loader_chains_duplicate_dependency_activation_factories() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.clearAuthoringActivationFactoryFallbacksForTests()
        WinRTAuthoring.clearActivationFactoryFallbacksForTests()
        WinRTAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRTAuthoringHostManifestLoader.registerHostExports(EmptyHostExports::class.java.name, EmptyHostExports)
        WinRTAuthoringHostManifestLoader.registerHostExports(HostManifestExports::class.java.name, HostManifestExports)

        val directory = Files.createTempDirectory("kotlin-winrt-authoring-host-chain-")
        Files.writeString(
            directory.resolve("EmptyHostManifestComponent.host.json"),
            """
            {
              "schemaVersion": 1,
              "model": "jvm-authoring-host",
              "assemblyName": "EmptyHostManifestComponent",
              "hostExportsClass": "${EmptyHostExports::class.java.name}",
              "activatableClasses": ["Sample.Authoring.HostManifestComponent"]
            }
            """.trimIndent(),
        )
        Files.writeString(
            directory.resolve("HostManifestComponent.host.json"),
            """
            {
              "schemaVersion": 1,
              "model": "jvm-authoring-host",
              "assemblyName": "HostManifestComponent",
              "hostExportsClass": "${HostManifestExports::class.java.name}",
              "activatableClasses": ["Sample.Authoring.HostManifestComponent"]
            }
            """.trimIndent(),
        )

        WinRTAuthoringHostManifestLoader.installFromDirectory(directory)
        ActivationFactory.get("Sample.Authoring.HostManifestComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        HostManifestComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_host_manifest_loader_installs_from_plugin_runtime_assets_directory() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.clearAuthoringActivationFactoryFallbacksForTests()
        WinRTAuthoring.clearActivationFactoryFallbacksForTests()
        WinRTAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRTAuthoringHostManifestLoader.registerHostExports(RuntimeAssetsHostExports::class.java.name, RuntimeAssetsHostExports)

        val root = Files.createTempDirectory("kotlin-winrt-authoring-runtime-assets-")
        val assets = root.resolve("kotlin-winrt-runtime-assets")
        Files.createDirectories(assets)
        Files.writeString(
            assets.resolve("RuntimeAssetsHostComponent.host.json"),
            """
            {
              "schemaVersion": 1,
              "model": "jvm-authoring-host",
              "assemblyName": "RuntimeAssetsHostComponent",
              "hostExportsClass": "${RuntimeAssetsHostExports::class.java.name}",
              "targetArtifact": "RuntimeAssetsHostComponent.jar",
              "activatableClassTargets": {
                "Sample.Authoring.RuntimeAssetsHostComponent": "RuntimeAssetsHostComponent.jar"
              }
            }
            """.trimIndent(),
        )

        URLClassLoader(arrayOf(root.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            WinRTAuthoringHostManifestLoader.installFromRuntimeAssets(classLoader)
        }
        ActivationFactory.get("Sample.Authoring.RuntimeAssetsHostComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        RuntimeAssetsHostComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_host_manifest_loader_installs_from_plugin_runtime_assets_jar() {
        assumeTrue(PlatformRuntime.isWindows)
        ComWrappersSupport.clearRegistriesForTests()
        ComWrappersSupport.clearAuthoringActivationFactoryFallbacksForTests()
        WinRTAuthoring.clearActivationFactoryFallbacksForTests()
        WinRTAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRTAuthoringHostManifestLoader.registerHostExports(RuntimeAssetsHostExports::class.java.name, RuntimeAssetsHostExports)

        val jarPath = Files.createTempDirectory("kotlin-winrt-authoring-runtime-assets-").resolve("runtime-assets.jar")
        JarOutputStream(Files.newOutputStream(jarPath)).use { jar ->
            jar.putNextEntry(JarEntry("kotlin-winrt-runtime-assets/"))
            jar.closeEntry()
            jar.putNextEntry(JarEntry("kotlin-winrt-runtime-assets/RuntimeAssetsHostComponent.host.json"))
            jar.write(
                """
                {
                  "schemaVersion": 1,
                  "model": "jvm-authoring-host",
                  "assemblyName": "RuntimeAssetsHostComponent",
                  "hostExportsClass": "${RuntimeAssetsHostExports::class.java.name}",
                  "targetArtifact": "RuntimeAssetsHostComponent.jar",
                  "activatableClassTargets": {
                    "Sample.Authoring.RuntimeAssetsHostComponent": "RuntimeAssetsHostComponent.jar"
                  }
                }
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            jar.closeEntry()
        }

        URLClassLoader(arrayOf(jarPath.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            WinRTAuthoringHostManifestLoader.installFromRuntimeAssets(classLoader)
        }
        ActivationFactory.get("Sample.Authoring.RuntimeAssetsHostComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        RuntimeAssetsHostComponent::class,
                    ),
                )
            }
        }
    }

    private class HostManifestComponent

    private class RuntimeAssetsHostComponent

    object EmptyHostExports : WinRTAuthoringHostExports {
        override fun registerActivationFactories() = Unit

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            WinRTAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)
    }

    object HostManifestExports : WinRTAuthoringHostExports {
        override fun registerActivationFactories() {
            val interfaceId = Guid("88888888-1111-2222-3333-444444444444")
            WinRTAuthoring.registerType<HostManifestComponent>(
                WinRTAuthoredTypeDefinition(
                    runtimeClassName = "Sample.Authoring.HostManifestComponent",
                    defaultInterfaceId = interfaceId,
                    interfaces = listOf(
                        WinRTAuthoredInterfaceDefinition(
                            interfaceId = interfaceId,
                            methods = emptyList(),
                            isDefault = true,
                        ),
                    ),
                ),
            )
            WinRTAuthoring.registerActivationFactory<HostManifestComponent>(
                runtimeClassName = "Sample.Authoring.HostManifestComponent",
                createInstance = ::HostManifestComponent,
            )
        }

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            WinRTAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)
    }

    object RuntimeAssetsHostExports : WinRTAuthoringHostExports {
        override fun registerActivationFactories() {
            val interfaceId = Guid("99999999-1111-2222-3333-444444444444")
            WinRTAuthoring.registerType<RuntimeAssetsHostComponent>(
                WinRTAuthoredTypeDefinition(
                    runtimeClassName = "Sample.Authoring.RuntimeAssetsHostComponent",
                    defaultInterfaceId = interfaceId,
                    interfaces = listOf(
                        WinRTAuthoredInterfaceDefinition(
                            interfaceId = interfaceId,
                            methods = emptyList(),
                            isDefault = true,
                        ),
                    ),
                ),
            )
            WinRTAuthoring.registerActivationFactory<RuntimeAssetsHostComponent>(
                runtimeClassName = "Sample.Authoring.RuntimeAssetsHostComponent",
                createInstance = ::RuntimeAssetsHostComponent,
            )
        }

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            WinRTAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)
    }
}
