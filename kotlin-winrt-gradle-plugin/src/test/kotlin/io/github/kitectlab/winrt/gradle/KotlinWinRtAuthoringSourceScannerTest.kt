package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtParameterDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
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
        assertTrue(generated, generated.contains("createCcwDefinition"))
        assertTrue(generated.contains("interfaceId = Guid(\"aaaaaaaa-1111-2222-3333-444444444444\")"))
        assertTrue(generated.contains("WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer))"))
        assertTrue(generated.contains("rawArgs"))
        assertTrue(generated.contains("LaunchActivatedEventArgs.Metadata.wrap"))
        assertTrue(generated.contains("type.getDeclaredMethod("))
        assertTrue(generated.contains("\"onLaunched\""))
        assertTrue(generated.contains("LaunchActivatedEventArgs::class.java"))
        assertTrue(generated.contains("method.invoke(value, __arg0)"))
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
                            iid = io.github.kitectlab.winrt.runtime.Guid("aaaaaaaa-1111-2222-3333-444444444444"),
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
