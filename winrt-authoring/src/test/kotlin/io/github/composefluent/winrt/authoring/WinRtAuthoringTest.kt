package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ActivationFactoryReference
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.PlatformRuntime
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtComInterfaceBaseKind
import io.github.composefluent.winrt.runtime.WinRtNotImplementedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class WinRtAuthoringTest {
    @Test
    fun authored_activation_factory_creates_default_instance_through_activation_factory() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("aaaaaaaa-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<ActivatableComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ActivatableComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<ActivatableComponent>(
            runtimeClassName = "Sample.Authoring.ActivatableComponent",
            createInstance = ::ActivatableComponent,
        )

        ActivationFactory.get("Sample.Authoring.ActivatableComponent").use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        ActivatableComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_activation_factory_exposes_factory_interface_members() {
        ComWrappersSupport.clearRegistriesForTests()
        val componentInterfaceId = Guid("bbbbbbbb-1111-2222-3333-444444444444")
        val factoryInterfaceId = Guid("bbbbbbbb-1111-2222-3333-444444444445")
        WinRtAuthoring.registerType<FactoryBackedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FactoryBackedComponent",
                defaultInterfaceId = componentInterfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = componentInterfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory(
            WinRtAuthoredActivationFactoryDefinition(
                runtimeClassName = "Sample.Authoring.FactoryBackedComponent",
                implementationType = FactoryBackedComponent::class,
                factoryInterfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = factoryInterfaceId,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                                PlatformAbi.writeInt32(args.single() as RawAddress, 7)
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
            ),
        )

        ActivationFactory.get("Sample.Authoring.FactoryBackedComponent", factoryInterfaceId).use { factory ->
            PlatformAbi.confinedScope().use { scope ->
                val result = PlatformAbi.allocateInt32Slot(scope)
                assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(factory.pointer, 6, result))
                assertEquals(7, PlatformAbi.readInt32(result))
            }
        }
    }

    @Test
    fun authored_activation_factory_without_default_constructor_reports_not_implemented() {
        ComWrappersSupport.clearRegistriesForTests()
        val interfaceId = Guid("eeeeeeee-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<FactoryOnlyComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FactoryOnlyComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<FactoryOnlyComponent>(
            runtimeClassName = "Sample.Authoring.FactoryOnlyComponent",
        )

        ActivationFactory.get("Sample.Authoring.FactoryOnlyComponent").use { factory ->
            try {
                factory.activateInstance().close()
                fail("Expected ActivateInstance to report E_NOTIMPL.")
            } catch (failure: WinRtNotImplementedException) {
                assertEquals(KnownHResults.E_NOTIMPL, failure.hResult)
            }
        }
    }

    @Test
    fun authored_module_activation_factory_returns_registered_factory_only() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("cccccccc-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<ModuleActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.ModuleActivatedComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<ModuleActivatedComponent>(
            runtimeClassName = "Sample.Authoring.ModuleActivatedComponent",
            createInstance = ::ModuleActivatedComponent,
        )

        val missing = WinRtAuthoring.getActivationFactory("Sample.Authoring.MissingComponent")
        assertTrue(PlatformAbi.isNull(missing))
        val pointer = WinRtAuthoring.getActivationFactory("Sample.Authoring.ModuleActivatedComponent")
        assertTrue(!PlatformAbi.isNull(pointer))
        ActivationFactoryReference(PlatformAbi.toRawComPtr(pointer)).use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        ModuleActivatedComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_module_activation_factory_uses_partial_fallback_on_miss() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("dddddddd-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<FallbackActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.FallbackActivatedComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<FallbackActivatedComponent>(
            runtimeClassName = "Sample.Authoring.FallbackActivatedComponent",
            createInstance = ::FallbackActivatedComponent,
        )
        WinRtAuthoring.registerActivationFactoryFallback { runtimeClassName, requestedInterface ->
            if (runtimeClassName == "Sample.Authoring.ForwardedComponent") {
                ComWrappersSupport.tryGetAuthoringActivationFactory(
                    "Sample.Authoring.FallbackActivatedComponent",
                    requestedInterface,
                ).pointer
            } else {
                PlatformAbi.nullPointer
            }
        }

        val pointer = WinRtAuthoring.getActivationFactory("Sample.Authoring.ForwardedComponent")
        assertTrue(!PlatformAbi.isNull(pointer))
        ActivationFactoryReference(PlatformAbi.toRawComPtr(pointer)).use { factory ->
            factory.activateInstance().use { instance ->
                assertNotNull(
                    ComWrappersSupport.findObject(
                        PlatformAbi.fromRawComPtr(instance.pointer),
                        FallbackActivatedComponent::class,
                    ),
                )
            }
        }
    }

    @Test
    fun authored_host_bridge_matches_reference_dll_get_activation_factory_shape() {
        ComWrappersSupport.clearRegistriesForTests()
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        val interfaceId = Guid("77777777-1111-2222-3333-444444444444")
        WinRtAuthoring.registerType<HostActivatedComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.HostActivatedComponent",
                defaultInterfaceId = interfaceId,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerActivationFactory<HostActivatedComponent>(
            runtimeClassName = "Sample.Authoring.HostActivatedComponent",
            createInstance = ::HostActivatedComponent,
        )

        PlatformAbi.confinedScope().use { scope ->
            val factoryOut = PlatformAbi.allocatePointerSlot(scope)
            assertEquals(
                KnownHResults.E_INVALIDARG.value,
                WinRtAuthoringHostBridge.dllGetActivationFactory(PlatformAbi.nullPointer, factoryOut),
            )
            HString.createReference("Sample.Authoring.HostActivatedComponent").use { runtimeClass ->
                assertEquals(
                    KnownHResults.E_INVALIDARG.value,
                    WinRtAuthoringHostBridge.dllGetActivationFactory(runtimeClass.handle, PlatformAbi.nullPointer),
                )
            }
            HString.createReference("Sample.Authoring.MissingComponent").use { missingClass ->
                assertEquals(
                    0x80040111.toInt(),
                    WinRtAuthoringHostBridge.dllGetActivationFactory(missingClass.handle, factoryOut),
                )
                assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(factoryOut)))
            }
            HString.createReference("Sample.Authoring.HostActivatedComponent").use { runtimeClass ->
                assertEquals(
                    KnownHResults.S_OK.value,
                    WinRtAuthoringHostBridge.dllGetActivationFactory(runtimeClass.handle, factoryOut),
                )
                val factoryPointer = PlatformAbi.readPointer(factoryOut)
                assertTrue(!PlatformAbi.isNull(factoryPointer))
                ActivationFactoryReference(PlatformAbi.toRawComPtr(factoryPointer)).use { factory ->
                    factory.activateInstance().use { instance ->
                        assertNotNull(
                            ComWrappersSupport.findObject(
                                PlatformAbi.fromRawComPtr(instance.pointer),
                                HostActivatedComponent::class,
                            ),
                        )
                    }
                }
            }
            assertEquals(KnownHResults.S_FALSE.value, WinRtAuthoringHostBridge.dllCanUnloadNow())
        }
    }

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

        val manifest = WinRtAuthoringHostManifestLoader.read(manifestPath)

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
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        WinRtAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRtAuthoringHostManifestLoader.registerHostExports(HostManifestExports::class.java.name, HostManifestExports)

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

        WinRtAuthoringHostManifestLoader.installFromDirectory(directory)
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
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        WinRtAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRtAuthoringHostManifestLoader.registerHostExports(RuntimeAssetsHostExports::class.java.name, RuntimeAssetsHostExports)

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
            WinRtAuthoringHostManifestLoader.installFromRuntimeAssets(classLoader)
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
        WinRtAuthoring.clearActivationFactoryFallbacksForTests()
        WinRtAuthoringHostManifestLoader.clearRegisteredHostExportsForTests()
        WinRtAuthoringHostManifestLoader.registerHostExports(RuntimeAssetsHostExports::class.java.name, RuntimeAssetsHostExports)

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
            WinRtAuthoringHostManifestLoader.installFromRuntimeAssets(classLoader)
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
    fun composable_object_forwards_outer_query_interface_to_inner_after_factory_composition() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("11111111-1111-1111-1111-111111111111")
        val innerIid = Guid("22222222-2222-2222-2222-222222222222")
        var outerInvoked = 0

        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = outerIid,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature()) {
                                outerInvoked += 1
                                KnownHResults.S_OK.value
                            },
                        ),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerType<InnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.InnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = innerIid,
                        methods = listOf(
                            WinRtAuthoredMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                                PlatformAbi.writeInt32(args.single() as RawAddress, 42)
                                KnownHResults.S_OK.value
                            },
                        ),
                        isDefault = true,
                    ),
                ),
            ),
        )

        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(OuterComponent, outerIid) { baseInterface, innerOut, instanceOut ->
            val outer = IUnknownReference(PlatformAbi.toRawComPtr(baseInterface), IID.IInspectable, preventReleaseOnDispose = true)
            outer.queryInterface(outerIid).getOrThrow().use { queried ->
                ComVtableInvoker.invoke(queried.pointer, 6)
            }
            retainedInner = ComWrappersSupport.createCCWForObject(InnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(1, outerInvoked)
            assertNotNull(composed.inner)
            composed.outer.queryInterface(innerIid).getOrThrow().use { forwarded ->
                PlatformAbi.confinedScope().use { scope ->
                    val result = PlatformAbi.allocateInt32Slot(scope)
                    assertEquals(KnownHResults.S_OK.value, ComVtableInvoker.invokeArgs(forwarded.pointer, 6, result))
                    assertEquals(42, PlatformAbi.readInt32(result))
                }
            }
        }
        retainedInner?.close()
    }

    @Test
    fun composable_object_registers_inner_and_result_pointers_for_find_object() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("33333333-3333-3333-3333-333333333333")
        val innerIid = Guid("44444444-4444-4444-4444-444444444444")
        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = outerIid,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )
        WinRtAuthoring.registerType<InnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.InnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = innerIid,
                        methods = emptyList(),
                        isDefault = true,
                    ),
                ),
            ),
        )

        val outerComponent = OuterComponent
        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(outerComponent, outerIid) { _, innerOut, instanceOut ->
            retainedInner = ComWrappersSupport.createCCWForObject(InnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(composed.outer.pointer), OuterComponent::class),
            )
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(composed.instance.pointer), OuterComponent::class),
            )
            assertEquals(
                outerComponent,
                ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(requireNotNull(composed.inner).pointer), OuterComponent::class),
            )
        }
        retainedInner?.close()
    }

    @Test
    fun composable_object_detects_aggregated_reference_tracker_inner() {
        ComWrappersSupport.clearRegistriesForTests()
        val outerIid = Guid("55555555-5555-5555-5555-555555555555")
        val innerIid = Guid("66666666-6666-6666-6666-666666666666")
        WinRtAuthoring.registerType<OuterComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.OuterComponent",
                defaultInterfaceId = outerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(outerIid, methods = emptyList(), isDefault = true),
                ),
            ),
        )
        WinRtAuthoring.registerType<TrackedInnerComponent>(
            WinRtAuthoredTypeDefinition(
                runtimeClassName = "Sample.Authoring.TrackedInnerComponent",
                defaultInterfaceId = innerIid,
                interfaces = listOf(
                    WinRtAuthoredInterfaceDefinition(innerIid, methods = emptyList(), isDefault = true),
                    WinRtAuthoredInterfaceDefinition(
                        interfaceId = io.github.composefluent.winrt.runtime.IID.IReferenceTracker,
                        methods = emptyList(),
                        baseKind = WinRtComInterfaceBaseKind.IUnknown,
                    ),
                ),
            ),
        )

        var retainedInner: ComObjectReference? = null
        WinRtAuthoring.createComposableObject(OuterComponent, outerIid) { _, innerOut, instanceOut ->
            retainedInner = ComWrappersSupport.createCCWForObject(TrackedInnerComponent, innerIid)
            val inner = requireNotNull(retainedInner)
            PlatformAbi.writePointer(innerOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(inner.getRefPointer()))
            KnownHResults.S_OK.value
        }.use { composed ->
            assertEquals(true, composed.isAggregatedReferenceTrackerObject)
        }
        retainedInner?.close()
    }

    private object OuterComponent

    private object InnerComponent

    private object TrackedInnerComponent

    private class ActivatableComponent

    private class FactoryBackedComponent

    private class FactoryOnlyComponent

    private class ModuleActivatedComponent

    private class FallbackActivatedComponent

    private class HostActivatedComponent

    private class HostManifestComponent

    private class RuntimeAssetsHostComponent

    object HostManifestExports : WinRtAuthoringHostExports {
        override fun registerActivationFactories() {
            val interfaceId = Guid("88888888-1111-2222-3333-444444444444")
            WinRtAuthoring.registerType<HostManifestComponent>(
                WinRtAuthoredTypeDefinition(
                    runtimeClassName = "Sample.Authoring.HostManifestComponent",
                    defaultInterfaceId = interfaceId,
                    interfaces = listOf(
                        WinRtAuthoredInterfaceDefinition(
                            interfaceId = interfaceId,
                            methods = emptyList(),
                            isDefault = true,
                        ),
                    ),
                ),
            )
            WinRtAuthoring.registerActivationFactory<HostManifestComponent>(
                runtimeClassName = "Sample.Authoring.HostManifestComponent",
                createInstance = ::HostManifestComponent,
            )
        }

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            WinRtAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)
    }

    object RuntimeAssetsHostExports : WinRtAuthoringHostExports {
        override fun registerActivationFactories() {
            val interfaceId = Guid("99999999-1111-2222-3333-444444444444")
            WinRtAuthoring.registerType<RuntimeAssetsHostComponent>(
                WinRtAuthoredTypeDefinition(
                    runtimeClassName = "Sample.Authoring.RuntimeAssetsHostComponent",
                    defaultInterfaceId = interfaceId,
                    interfaces = listOf(
                        WinRtAuthoredInterfaceDefinition(
                            interfaceId = interfaceId,
                            methods = emptyList(),
                            isDefault = true,
                        ),
                    ),
                ),
            )
            WinRtAuthoring.registerActivationFactory<RuntimeAssetsHostComponent>(
                runtimeClassName = "Sample.Authoring.RuntimeAssetsHostComponent",
                createInstance = ::RuntimeAssetsHostComponent,
            )
        }

        override fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int =
            WinRtAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)
    }
}
