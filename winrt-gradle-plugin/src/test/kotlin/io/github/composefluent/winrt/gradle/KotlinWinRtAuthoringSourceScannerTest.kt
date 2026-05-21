package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
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
        assertTrue(generated.contains("type.getDeclaredMethod("))
        assertTrue(generated.contains("\"onLaunched\""))
        assertTrue(generated.contains("LaunchActivatedEventArgs::class.java"))
        assertTrue(generated.contains("method.invoke(value, __arg0)"))
        assertTrue(generated.contains("catch (failure: InvocationTargetException)"))
        assertTrue(generated.contains("throw (failure.targetException ?: failure)"))
        val registrar = output.resolve("io/github/composefluent/winrt/projections/support/WinRTAuthoringTypeDetailsRegistrar.kt").readText()
        assertTrue(registrar.contains("object WinRTAuthoringTypeDetailsRegistrar"))
        assertTrue(registrar.contains("WinRT_App_TypeDetails.register()"))
    }

    @Test
    fun renders_system_object_override_parameters_through_object_marshaller() {
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
                                        WinRtParameterDefinition("newContent", "System.Object"),
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
        assertTrue(generated.contains("type.getDeclaredMethod(\"onContentChanged\",\n                Any::class.java, Any::class.java)"))
        assertTrue(generated, !generated.contains("Object.Metadata.wrap"))
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
