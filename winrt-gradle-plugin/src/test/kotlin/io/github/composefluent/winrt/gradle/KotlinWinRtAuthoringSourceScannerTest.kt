package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringCandidateFile
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringMetadataModel
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRtEnumMemberDefinition
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRtAuthoringSourceScannerTest {
    @Test
    fun reads_authored_candidate_file_rows() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-", ".tsv")
        input.writeText(
            "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\n",
        )

        val candidate = KotlinWinRtAuthoringCandidateFile.read(input).single()

        assertEquals("sample", candidate.packageName)
        assertEquals("App", candidate.className)
        assertEquals("sample.App", candidate.sourceTypeName)
        assertEquals("Microsoft.UI.Xaml.Application", candidate.winRtBaseClassName)
        assertEquals(listOf("Microsoft.UI.Xaml.IApplicationOverrides"), candidate.winRtInterfaceNames)
        assertEquals(listOf("Microsoft.UI.Xaml.IApplicationOverrides"), candidate.overridableInterfaceNames)
        assertTrue(candidate.isPublic)
    }

    @Test
    fun rejects_malformed_authored_candidate_file_rows() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-malformed-", ".tsv")
        input.writeText("sample\tApp\tsample.App\n")

        val error = runCatching { KotlinWinRtAuthoringCandidateFile.read(input) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt authored candidate row 1"),
        )
    }

    @Test
    fun rejects_blank_authored_candidate_file_list_elements() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-list-", ".tsv")
        input.writeText(
            "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides;;Other.Interface\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\n",
        )

        val error = runCatching { KotlinWinRtAuthoringCandidateFile.read(input) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt authored candidate row 1"),
        )
    }

    @Test
    fun rejects_malformed_authored_candidate_file_visibility_flags() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-visibility-", ".tsv")
        input.writeText(
            "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\tnot-a-boolean\n",
        )

        val error = runCatching { KotlinWinRtAuthoringCandidateFile.read(input) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt authored candidate row 1"),
        )
    }

    @Test
    fun rejects_authored_candidate_file_rows_with_extra_columns() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-extra-columns-", ".tsv")
        input.writeText(
            "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\textra\n",
        )

        val error = runCatching { KotlinWinRtAuthoringCandidateFile.read(input) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt authored candidate row 1"),
        )
    }

    @Test
    fun validates_matching_scanner_and_compiler_authored_candidate_files() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-candidates-", ".tsv")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-candidates-", ".tsv")
        val candidates = listOf(
            KotlinWinRtAuthoredTypeCandidate(
                packageName = "sample",
                className = "App",
                sourceTypeName = "sample.App",
                winRtBaseClassName = null,
                winRtInterfaceNames = listOf("Windows.Foundation.IStringable"),
                overridableInterfaceNames = emptyList(),
                isPublic = false,
            ),
        )
        KotlinWinRtAuthoringCandidateFile.write(scanner, candidates)
        KotlinWinRtAuthoringCandidateFile.write(compiler, candidates)

        validateAuthoredCandidateHandoff(scanner.toFile(), compiler.toFile())
    }

    @Test
    fun rejects_mismatched_scanner_and_compiler_authored_candidate_files() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-candidates-", ".tsv")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-candidates-", ".tsv")
        KotlinWinRtAuthoringCandidateFile.write(scanner, emptyList())
        KotlinWinRtAuthoringCandidateFile.write(
            compiler,
            listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = null,
                    winRtInterfaceNames = listOf("Windows.Foundation.IStringable"),
                    overridableInterfaceNames = emptyList(),
                    isPublic = false,
                ),
            ),
        )

        val error = runCatching {
            validateAuthoredCandidateHandoff(scanner.toFile(), compiler.toFile())
        }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("Only compiler candidates: sample.App"),
        )
    }

    @Test
    fun reports_changed_scanner_and_compiler_authored_candidate_files() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-candidates-changed-", ".tsv")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-candidates-changed-", ".tsv")
        val scannerCandidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRtBaseClassName = null,
            winRtInterfaceNames = listOf("Windows.Foundation.IStringable"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        KotlinWinRtAuthoringCandidateFile.write(scanner, listOf(scannerCandidate))
        KotlinWinRtAuthoringCandidateFile.write(compiler, listOf(scannerCandidate.copy(isPublic = false)))

        val error = runCatching {
            validateAuthoredCandidateHandoff(scanner.toFile(), compiler.toFile())
        }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("Changed candidates: sample.App"),
        )
    }

    @Test
    fun validates_matching_scanner_and_compiler_authored_support_artifacts() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-authored-metadata-", ".tsv")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-authored-metadata-", ".tsv")
        scanner.writeText("sample.App\tABI.sample.App\n")
        compiler.writeText("sample.App\tABI.sample.App\n")

        validateAuthoredArtifactHandoff(
            description = "authored metadata descriptor",
            scannerArtifact = scanner.toFile(),
            compilerArtifact = compiler.toFile(),
        )
    }

    @Test
    fun rejects_mismatched_scanner_and_compiler_authored_support_artifacts() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-authored-host-", ".json")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-authored-host-", ".json")
        scanner.writeText("""{"activatableClasses":["sample.App"]}""")
        compiler.writeText("""{"activatableClasses":[]}""")

        val error = runCatching {
            validateAuthoredArtifactHandoff(
                description = "authored host manifest",
                scannerArtifact = scanner.toFile(),
                compilerArtifact = compiler.toFile(),
            )
        }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("authored host manifest handoff mismatch"),
        )
    }

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
    fun rejects_authored_type_details_for_missing_winrt_interface_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-missing-interface-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRtBaseClassName = "Microsoft.UI.Xaml.Application",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IMissingOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IMissingOverrides"),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = model(),
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("references missing WinRT interface"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_App_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected missing authored WinRT interface metadata to fail closed.")
    }

    @Test
    fun rejects_authored_type_details_for_non_interface_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-non-interface-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRtBaseClassName = "Microsoft.UI.Xaml.Application",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.Application"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Application"),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = model(),
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("references non-interface WinRT type"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_App_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected non-interface authored WinRT metadata to fail closed.")
    }

    @Test
    fun rejects_authored_type_details_for_interface_without_iid_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-missing-iid-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalStringable",
            sourceTypeName = "sample.LocalStringable",
            winRtBaseClassName = "Windows.Foundation.IStringable",
            winRtInterfaceNames = listOf("Windows.Foundation.IStringable"),
            overridableInterfaceNames = listOf("Windows.Foundation.IStringable"),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = model(),
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("without metadata IID"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalStringable_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected authored WinRT interface metadata without IID to fail closed.")
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
        assertTrue(
            generated,
            Regex("""ComMethodSignature\.of\(\s*ComAbiValueKind\.Pointer,\s*ComAbiValueKind\.Pointer\s*\)""").containsMatchIn(generated),
        )
        assertTrue(generated, !generated.contains("ComMethodSignature.of(ComAbiValueKind.Pointer, , ComAbiValueKind.Pointer)"))
        assertTrue(generated.contains("(value as ContentControl).__winrtAuthoringInvokeOnContentChanged(__arg0, __arg1)"))
        assertTrue(generated, !generated.contains("ContentControl::class.java"))
        assertTrue(generated, !generated.contains("Any::class.java, Any::class.java"))
        assertTrue(generated, !generated.contains("Object.Metadata.wrap"))
    }

    @Test
    fun renders_runtime_mapped_struct_overrides_through_runtime_projection_types() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-mapped-struct-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRtBaseClassName = "Sample.Shape",
            winRtInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Rect",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Size",
                            kind = WinRtTypeKind.Struct,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9940"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "MeasureOverride",
                                    returnTypeName = "Windows.Foundation.Size",
                                    parameters = listOf(
                                        WinRtParameterDefinition("availableSize", "Windows.Foundation.Size"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetPeerFromPointCore",
                                    returnTypeName = "Windows.Foundation.Rect",
                                    parameters = listOf(
                                        WinRtParameterDefinition("point", "Windows.Foundation.Point"),
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

        val generated = output.resolve("sample/WinRT_LocalShape_TypeDetails.kt").readText()
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Point"))
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Rect"))
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Size"))
        assertTrue(generated.contains("ComAbiValueKind.Struct(Size.Metadata.layout.abiLayout)"))
        assertTrue(generated.contains("Size.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Point.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Rect.Metadata.copyTo(__result as Rect"))
        assertTrue(generated, !generated.contains("import windows.foundation.Point"))
        assertTrue(generated, !generated.contains("import windows.foundation.Rect"))
        assertTrue(generated, !generated.contains("import windows.foundation.Size"))
    }

    @Test
    fun renders_runtime_class_override_parameters_through_metadata_wrap() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-parameter-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetPeerCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("peer", "Sample.Peer")),
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

        val generated = output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt").readText()
        assertTrue(generated.contains("Peer.Metadata.wrap"))
        assertTrue(generated.contains("(value as PeerProvider).__winrtAuthoringInvokeSetPeerCore(__arg0)"))
    }

    @Test
    fun renders_authored_runtime_class_override_parameters_through_object_marshaller() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-authored-object-parameter-details-")
        val peerCandidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "Peer",
            sourceTypeName = "sample.Peer",
            winRtBaseClassName = null,
            winRtInterfaceNames = listOf("Sample.IPeer"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        val providerCandidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "sample",
                            name = "Peer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IPeer",
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetPeerCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("peer", "sample.Peer")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(peerCandidate, providerCandidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtObjectMarshaller.fromAbi(rawArgs[0] as RawAddress) as Peer"))
        assertTrue(generated, !generated.contains("Peer.Metadata.wrap"))
        assertTrue(generated.contains("(value as PeerProvider).__winrtAuthoringInvokeSetPeerCore(__arg0)"))
    }

    @Test
    fun rejects_unsupported_object_override_parameters() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-bad-object-parameter-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerCallback",
                            kind = WinRtTypeKind.Delegate,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetPeerCallbackCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("callback", "Sample.PeerCallback")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("unsupported object type 'Sample.PeerCallback'"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected unsupported authored object parameters to fail closed.")
    }

    @Test
    fun renders_system_string_override_parameters_and_returns_through_hstring() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-string-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalStringable",
            sourceTypeName = "sample.LocalStringable",
            winRtBaseClassName = "Sample.IStringableBase",
            winRtInterfaceNames = listOf("Sample.IStringableOverrides"),
            overridableInterfaceNames = listOf("Sample.IStringableOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IStringableOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "System.String",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.String")),
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

        val generated = output.resolve("sample/WinRT_LocalStringable_TypeDetails.kt").readText()
        assertTrue(generated.contains("HString.fromHandle(rawArgs[0] as RawAddress, owner = false)"))
        assertTrue(generated.contains("(value as IStringableBase).__winrtAuthoringInvokeTransform(__arg0)"))
        assertTrue(generated.contains("PlatformAbi.writePointer("))
        assertTrue(generated.contains("rawArgs[1] as RawAddress"))
        assertTrue(generated.contains("HString.create("))
        assertTrue(generated.contains("__result"))
        assertTrue(generated.contains(".handle"))
    }

    @Test
    fun renders_object_override_returns_with_declared_interface_iid() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-return-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IPeer",
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetPeerCore",
                                    returnTypeName = "Sample.Peer",
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

        val generated = output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt").readText()
        assertTrue(generated.contains("detachCCWForObject"))
        assertTrue(generated.contains("Guid(\"11111111-2222-3333-4444-555555555555\")"))
        assertTrue(generated, !generated.contains("detachCCWForObject(__result, IID.IInspectable)"))
    }

    @Test
    fun rejects_runtime_class_object_returns_without_default_interface_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-return-missing-default-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetPeerCore",
                                    returnTypeName = "Sample.Peer",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("without default interface metadata"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected runtime class object returns without default interface metadata to fail closed.")
    }

    @Test
    fun rejects_interface_object_returns_without_iid_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-return-missing-iid-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRtBaseClassName = "Sample.PeerProvider",
            winRtInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRtTypeKind.Interface,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetPeerCore",
                                    returnTypeName = "Sample.IPeer",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("without IID metadata"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected interface object returns without IID metadata to fail closed.")
    }

    @Test
    fun renders_system_fundamental_override_parameters_and_returns_through_scalar_abi_shapes() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-fundamental-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFundamentals",
            sourceTypeName = "sample.LocalFundamentals",
            winRtBaseClassName = "Sample.FundamentalBase",
            winRtInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            overridableInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "FundamentalBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFundamentalOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IFundamentalOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetEnabled",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Boolean")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetSymbol",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Char")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetAmount",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Byte")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetRatio",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Single")),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetEnabled",
                                    returnTypeName = "System.Boolean",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetSymbol",
                                    returnTypeName = "System.Char",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetAmount",
                                    returnTypeName = "System.UInt32",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetRatio",
                                    returnTypeName = "System.Single",
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

        val generated = output.resolve("sample/WinRT_LocalFundamentals_TypeDetails.kt").readText()
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Int8)"))
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Int16)"))
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Float)"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Byte).toInt() != 0"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Short).toInt().toChar()"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Byte).toUByte()"))
        assertTrue(generated.contains("val __arg0 = rawArgs[0] as Float"))
        assertTrue(generated.contains("PlatformAbi.writeInt8("))
        assertTrue(generated.contains("if (__result as Boolean)"))
        assertTrue(generated.contains("PlatformAbi.writeInt16("))
        assertTrue(generated.contains("Char).code.toShort()"))
        assertTrue(generated.contains("(__result as UInt).toInt()"))
        assertTrue(generated.contains("PlatformAbi.writeFloat("))
    }

    @Test
    fun renders_uint32_flags_enum_override_parameters_and_returns_through_unsigned_abi_carrier() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-enum-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFlags",
            sourceTypeName = "sample.LocalFlags",
            winRtBaseClassName = "Sample.FlagsBase",
            winRtInterfaceNames = listOf("Sample.IFlagsOverrides"),
            overridableInterfaceNames = listOf("Sample.IFlagsOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetFlags",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.UInt32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("None", 0u),
                                WinRtEnumMemberDefinition("Enabled", 1u),
                            ),
                            customAttributes = listOf(WinRtCustomAttributeDefinition("System.FlagsAttribute")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "FlagsBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFlagsOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IFlagsOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444445"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetFlags",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("flags", "Sample.WidgetFlags")),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetFlags",
                                    returnTypeName = "Sample.WidgetFlags",
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

        val generated = output.resolve("sample/WinRT_LocalFlags_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32)"))
        assertTrue(generated, generated.contains("val __arg0 = WidgetFlags.Metadata.fromAbi((rawArgs[0] as Int).toUInt())"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(rawArgs[0] as RawAddress,"))
        assertTrue(generated, generated.contains("WidgetFlags.Metadata.toAbi(__result"))
        assertTrue(generated, generated.contains("as WidgetFlags).toInt()"))
        assertTrue(generated, !generated.contains("WidgetFlags.Metadata.fromAbi(rawArgs[0] as Int)"))
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
                                    name = "GetControlledPeerInterfacesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Microsoft.UI.Xaml.Automation.Peers.IAutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetDescribedByCore",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "FindSubElementsForTouchTargeting",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNamedPeersCore",
                                    returnTypeName = "Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetMutableNamesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<System.String, System.String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetPeerGroupsCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetRawChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Object>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNamesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<System.String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "SetNamesCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "names",
                                            typeName = "Windows.Foundation.Collections.IVector<System.String>",
                                            typeSignature = WinRtTypeRef.named(
                                                "Windows.Foundation.Collections.IVector",
                                                typeArguments = listOf(WinRtTypeRef.named("System.String")),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetNamedPeersCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "peers",
                                            typeName = "Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                            typeSignature = WinRtTypeRef.named(
                                                "Windows.Foundation.Collections.IMapView",
                                                typeArguments = listOf(
                                                    WinRtTypeRef.named("System.String"),
                                                    WinRtTypeRef.named("Microsoft.UI.Xaml.Automation.Peers.AutomationPeer"),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetPeerGroupsCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "peerGroups",
                                            typeName = "Windows.Foundation.Collections.IVectorView<Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                            typeSignature = WinRtTypeRef.named(
                                                "Windows.Foundation.Collections.IVectorView",
                                                typeArguments = listOf(
                                                    WinRtTypeRef.named(
                                                        "Windows.Foundation.Collections.IMapView",
                                                        typeArguments = listOf(
                                                            WinRtTypeRef.named("System.String"),
                                                        WinRtTypeRef.named("Microsoft.UI.Xaml.Automation.Peers.AutomationPeer"),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
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

        val generated = output.resolve("sample/WinRT_LocalAutomationPeer_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReadOnlyListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtIterableProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReadOnlyDictionaryProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtDictionaryProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReferenceValueAdapter<Iterable<AutomationPeer>>"))
        assertTrue(generated.contains("WinRtReferenceValueAdapter<Map<String, AutomationPeer>>"))
        assertTrue(generated.contains("WinRtReferenceValueAdapter<IAutomationPeer>"))
        assertTrue(generated.contains("WinRtCollectionInterfaceIds.iterableSignature"))
        assertTrue(generated.contains("WinRtCollectionInterfaceIds.mapViewSignature"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.runtimeClass(AutomationPeer::class"))
        assertTrue(generated.contains("IAutomationPeer.Metadata.wrap(reference!!)"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.object_"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.string"))
        assertTrue(generated.contains("WinRtListProjection.fromAbi(rawArgs[0] as RawAddress"))
        assertTrue(generated.contains("(value as AutomationPeer).__winrtAuthoringInvokeSetNamesCore(__arg0)"))
        assertTrue(generated.contains("WinRtReadOnlyDictionaryProjection.fromAbi(rawArgs[0]"))
        assertTrue(generated.contains("as RawAddress, WinRtReferenceValueAdapters.string"))
        assertTrue(generated.contains("AutomationPeer).__winrtAuthoringInvokeSetNamedPeersCore(__arg0)"))
        assertTrue(generated.contains("WinRtReadOnlyListProjection.fromAbi(rawArgs[0]"))
        assertTrue(generated.contains("WinRtReadOnlyDictionaryProjection.fromAbi(PlatformAbi.fromRawComPtr(reference.pointer)"))
        assertTrue(generated.contains("AutomationPeer).__winrtAuthoringInvokeSetPeerGroupsCore(__arg0)"))
        assertTrue(generated, !generated.contains("createCCWForObject(__result, IID.IInspectable)"))
    }

    @Test
    fun renders_receive_array_returns_through_two_abi_out_slots() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-receive-array-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalBufferOwner",
            sourceTypeName = "sample.LocalBufferOwner",
            winRtBaseClassName = "Sample.BufferOwner",
            winRtInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "BufferOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IThing",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("55555555-3333-2222-1111-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IBufferOwnerOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("44444444-3333-2222-1111-000000000000"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetNumbers",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "numbers",
                                            typeName = "Array<Int32>",
                                            direction = io.github.composefluent.winrt.metadata.WinRtParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Int32"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "names",
                                            typeName = "Array<System.String>",
                                            direction = io.github.composefluent.winrt.metadata.WinRtParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("System.String"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetThings",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "things",
                                            typeName = "Array<Sample.IThing>",
                                            direction = io.github.composefluent.winrt.metadata.WinRtParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Sample.IThing"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetPoints",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "points",
                                            typeName = "Array<Sample.Point>",
                                            direction = io.github.composefluent.winrt.metadata.WinRtParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Sample.Point"), isByRef = true),
                                        ),
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

        val generated = output.resolve("sample/WinRT_LocalBufferOwner_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32"))
        assertTrue(generated, generated.contains("ComAbiValueKind.Pointer)) { rawArgs ->"))
        assertTrue(generated, generated.contains("val __result = (value as BufferOwner).__winrtAuthoringInvokeGetNumbers()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() * 4, 4)"))
        assertTrue(generated, generated.contains("__result.forEachIndexed { __index, __element ->"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(PlatformAbi.slice(__returnArrayData, __index.toLong() * 4"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(rawArgs[0] as RawAddress, __result.size)"))
        assertTrue(generated, generated.contains("PlatformAbi.writePointer(rawArgs[1] as RawAddress, __returnArrayData)"))
        assertTrue(generated, !generated.contains("__arg0"))
        assertTrue(generated, !generated.contains("rawArgs[2]"))
        assertTrue(generated, generated.contains("val __result = (value as BufferOwner).__winrtAuthoringInvokeGetNames()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() * 8"))
        assertTrue(generated, generated.contains("PlatformAbi.writePointer(PlatformAbi.slice(__returnArrayData, __index.toLong() *"))
        assertTrue(generated, generated.contains("8, 8), HString.create(__element).handle)"))
        assertTrue(generated, generated.contains("val __result = (value as BufferOwner).__winrtAuthoringInvokeGetThings()"))
        assertTrue(generated, generated.contains("ComWrappersSupport.detachCCWForObject(__element"))
        assertTrue(generated, generated.contains("Guid(\"55555555-3333-2222-1111-000000000000\")"))
        assertTrue(generated, generated.contains("val __result = (value as BufferOwner).__winrtAuthoringInvokeGetPoints()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() *"))
        assertTrue(generated, generated.contains("Point.Metadata.layout.sizeBytes, Point.Metadata.layout.alignmentBytes)"))
        assertTrue(generated, generated.contains("Point.Metadata.layout.alignmentBytes"))
        assertTrue(generated, generated.contains("Point.Metadata.copyTo(__element"))
        assertTrue(generated, generated.contains("as Point, PlatformAbi.slice(__returnArrayData"))
    }

    @Test
    fun renders_read_only_array_parameters_from_two_abi_slots() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-array-parameter-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalBufferConsumer",
            sourceTypeName = "sample.LocalBufferConsumer",
            winRtBaseClassName = "Sample.BufferConsumer",
            winRtInterfaceNames = listOf("Sample.IBufferConsumerOverrides"),
            overridableInterfaceNames = listOf("Sample.IBufferConsumerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "BufferConsumer",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IThing",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("77777777-3333-2222-1111-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IBufferConsumerOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-3333-2222-1111-000000000000"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetNumbers",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "numbers",
                                            typeName = "Array<Int32>",
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Int32")),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "names",
                                            typeName = "Array<System.String>",
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("System.String")),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetThings",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "things",
                                            typeName = "Array<Sample.IThing>",
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Sample.IThing")),
                                        ),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetPoints",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "points",
                                            typeName = "Array<Sample.Point>",
                                            typeSignature = WinRtTypeRef.array(WinRtTypeRef.named("Sample.Point")),
                                        ),
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

        val generated = output.resolve("sample/WinRT_LocalBufferConsumer_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32"))
        assertTrue(generated, generated.contains("ComAbiValueKind.Pointer)) { rawArgs ->"))
        assertTrue(generated, generated.contains("val __arrayLength = rawArgs[0] as Int"))
        assertTrue(generated, generated.contains("val __arrayData = rawArgs[1] as RawAddress"))
        assertTrue(generated, generated.contains("Array(__arrayLength) { __index -> PlatformAbi.readInt32"))
        assertTrue(generated, generated.contains("(value as BufferConsumer).__winrtAuthoringInvokeSetNumbers(__arg0)"))
        assertTrue(generated, generated.contains("HString.fromHandle(PlatformAbi.readPointer"))
        assertTrue(generated, generated.contains("(value as BufferConsumer).__winrtAuthoringInvokeSetNames(__arg0)"))
        assertTrue(generated, generated.contains("IThing.Metadata.wrap(IInspectableReference"))
        assertTrue(generated, generated.contains("PlatformAbi.toRawComPtr(PlatformAbi.readPointer"))
        assertTrue(generated, generated.contains("(value as BufferConsumer).__winrtAuthoringInvokeSetThings(__arg0)"))
        assertTrue(generated, generated.contains("Point.Metadata.fromAbi(PlatformAbi.slice(__arrayData"))
        assertTrue(generated, generated.contains("(value as BufferConsumer).__winrtAuthoringInvokeSetPoints(__arg0)"))
    }

    @Test
    fun rejects_authored_collection_returns_without_supported_element_adapter() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-bad-collection-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRtBaseClassName = "Sample.Shape",
            winRtInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Value",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValuesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Sample.Value>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("unsupported collection element type 'Sample.Value'"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalShape_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected unsupported authored collection return element adapters to fail closed.")
    }

    @Test
    fun rejects_authored_collection_returns_with_missing_element_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-missing-collection-element-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRtBaseClassName = "Sample.Shape",
            winRtInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValuesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Sample.Value>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("collection element type 'Sample.Value' without metadata"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalShape_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected missing authored collection return element metadata to fail closed.")
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
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "LaunchActivatedEventArgs",
                            kind = WinRtTypeKind.RuntimeClass,
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
