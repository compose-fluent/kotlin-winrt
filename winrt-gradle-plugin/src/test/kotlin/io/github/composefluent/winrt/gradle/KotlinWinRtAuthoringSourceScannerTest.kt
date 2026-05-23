package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class KotlinWinRtAuthoringSourceScannerTest {
    @Test
    fun merges_scanned_authored_runtime_classes_into_projection_metadata_model() {
        val augmented = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            model = model(),
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
        )

        val authoredType = augmented.namespaces.single { namespace -> namespace.name == "sample" }.types.single()
        assertEquals(WinRtTypeKind.RuntimeClass, authoredType.kind)
        assertEquals("sample.App", authoredType.qualifiedName)
        assertEquals("Microsoft.UI.Xaml.Application", authoredType.baseTypeName)
        assertEquals("Microsoft.UI.Xaml.IApplicationOverrides", authoredType.defaultInterfaceName)
        assertTrue(authoredType.activation.isActivatable)
        assertTrue(authoredType.implementedInterfaces.single().isDefault)
        assertTrue(authoredType.implementedInterfaces.single().isOverridable)
    }

    @Test
    fun writes_authored_metadata_descriptor_for_scanned_candidates() {
        val output = Files.createTempFile("kotlin-winrt-authored-metadata-", ".tsv")

        KotlinWinRtAuthoringMetadataModel.writeDescriptor(
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
            outputFile = output,
        )

        assertEquals(
            "sample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\ttrue\n",
            output.readText(),
        )
    }

    @Test
    fun renders_generated_type_details_for_scanned_authored_type() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRtBaseClassName = "Microsoft.UI.Xaml.Application",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = model(),
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_App_TypeDetails.kt").readText()
        assertTrue(generated.contains("object WinRT_App_TypeDetails"))
        assertTrue(generated, generated.contains("fun register()"))
        assertTrue(generated, generated.contains("ComWrappersSupport.registerAuthoringTypeDetailsFactory(App::class, ::createCcwDefinition)"))
        assertTrue(generated, generated.contains("createCcwDefinition"))
        assertTrue(generated.contains("interfaceId = Guid(\"aaaaaaaa-1111-2222-3333-444444444444\")"))
        assertTrue(generated.contains("WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer))"))
        assertTrue(generated.contains("rawArgs"))
        assertTrue(generated.contains("LaunchActivatedEventArgs.Metadata.wrap"))
        assertTrue(generated.contains("(value as Application).__winrtAuthoringInvokeOnLaunched(__arg0)"))
        assertTrue(generated, !generated.contains("findDeclaredMethod"))
        assertTrue(generated, !generated.contains("getDeclaredMethod"))
        assertTrue(generated, !generated.contains("method.invoke"))
        assertTrue(generated, !generated.contains("InvocationTargetException"))
        assertTrue(generated, !generated.contains("E_NOTIMPL"))
        val registrar = output.resolve("io/github/composefluent/winrt/projections/support/WinRTAuthoringTypeDetailsRegistrar.kt").readText()
        assertTrue(registrar.contains("object WinRTAuthoringTypeDetailsRegistrar"))
        assertTrue(registrar.contains("WinRT_App_TypeDetails.register()"))
    }

    @Test
    fun renders_object_alias_override_parameters_through_object_marshaller() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRtBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Controls",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "IContentControlOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9937"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnContentChanged",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition("oldContent", "System.Object"),
                                        WinRtParameterDefinition("newContent", "Any"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtObjectMarshaller.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("WinRtObjectMarshaller.fromAbi(rawArgs[1] as RawAddress)"))
        assertTrue(generated.contains("(value as ContentControl).__winrtAuthoringInvokeOnContentChanged(__arg0, __arg1)"))
        assertTrue(generated, !generated.contains("ContentControl::class.java"))
        assertTrue(generated, !generated.contains("Any::class.java, Any::class.java"))
        assertTrue(generated, !generated.contains("Object.Metadata.wrap"))
    }

    @Test
    fun renders_inherited_override_dispatch_against_declaring_winrt_base_class() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-inherited-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRtBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "UIElement",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IUIElementOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IUIElementOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("bbbbbbbb-1111-2222-3333-444444444444"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(WinRtCustomAttributeValue.TypeValue("Microsoft.UI.Xaml.UIElement")),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnDisconnectVisualChildren",
                                    returnTypeName = "Void",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("(value as UIElement).__winrtAuthoringInvokeOnDisconnectVisualChildren()"))
        assertTrue(generated, !generated.contains("ContentControl::__class.java"))
        assertTrue(generated, !generated.contains("findDeclaredMethod"))
    }

    @Test
    fun renders_authored_collection_returns_through_generic_collection_projection_helpers() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-collection-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalAutomationPeer",
            sourceTypeName = "sample.LocalAutomationPeer",
            winRtBaseClassName = "Microsoft.UI.Xaml.Automation.Peers.AutomationPeer",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Automation.Peers",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "AutomationPeer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Automation.Peers.IAutomationPeer",
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeer",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeerOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetControlledPeersCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetDescribedByCore",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetRawChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Object>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalAutomationPeer_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReadOnlyListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtIterableProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.runtimeClass(AutomationPeer::class"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.object_"))
        assertTrue(generated, !generated.contains("createCCWForObject(__result, IID.IInspectable)"))
    }

    @Test
    fun writes_authoring_host_manifest_for_scanned_authored_types() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-host-")
        val manifest = output.resolve("SampleComponent.host.json")
        KotlinWinRtAuthoringMetadataModel.writeHostManifest(
            assemblyName = "SampleComponent",
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
            outputFile = manifest,
        )

        val json = manifest.readText()
        assertTrue(json.contains("\"model\": \"jvm-authoring-host\""))
        assertTrue(json.contains("\"assemblyName\": \"SampleComponent\""))
        assertTrue(json.contains("\"hostExportsClass\": \"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports\""))
        assertTrue(json.contains("\"targetArtifact\": \"SampleComponent.jar\""))
        assertTrue(json.contains("\"activatableClasses\": [\"sample.App\"]"))
        assertTrue(json.contains("\"activatableClassTargets\": {\"sample.App\": \"SampleComponent.jar\"}"))
    }

    private fun model(): WinRtMetadataModel =
        WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IApplicationOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnLaunched",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "args",
                                            typeName = "Microsoft.UI.Xaml.LaunchActivatedEventArgs",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinRtTypeKind.Interface,
                        ),
                    ),
                ),
            ),
        )
}
