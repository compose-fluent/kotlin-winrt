package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringMetadataModel
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRTCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRTEnumMemberDefinition
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlinWinRTAuthoringSourceScannerTest {
    @Test
    fun reads_authored_candidate_file_rows() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-", ".tsv")
        input.writeText(
            "sample\tApp\tsample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\n",
        )

        val candidate = KotlinWinRTAuthoringCandidateFile.read(input).single()

        assertEquals("sample", candidate.packageName)
        assertEquals("App", candidate.className)
        assertEquals("sample.App", candidate.sourceTypeName)
        assertEquals("Microsoft.UI.Xaml.Application", candidate.winRTBaseClassName)
        assertEquals(listOf("Microsoft.UI.Xaml.IApplicationOverrides"), candidate.winRTInterfaceNames)
        assertEquals(listOf("Microsoft.UI.Xaml.IApplicationOverrides"), candidate.overridableInterfaceNames)
        assertTrue(candidate.isPublic)
    }

    @Test
    fun rejects_malformed_authored_candidate_file_rows() {
        val input = Files.createTempFile("kotlin-winrt-authored-candidates-malformed-", ".tsv")
        input.writeText("sample\tApp\tsample.App\n")

        val error = runCatching { KotlinWinRTAuthoringCandidateFile.read(input) }.exceptionOrNull()

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

        val error = runCatching { KotlinWinRTAuthoringCandidateFile.read(input) }.exceptionOrNull()

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

        val error = runCatching { KotlinWinRTAuthoringCandidateFile.read(input) }.exceptionOrNull()

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

        val error = runCatching { KotlinWinRTAuthoringCandidateFile.read(input) }.exceptionOrNull()

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
            KotlinWinRTAuthoredTypeCandidate(
                packageName = "sample",
                className = "App",
                sourceTypeName = "sample.App",
                winRTBaseClassName = null,
                winRTInterfaceNames = listOf("Windows.Foundation.IStringable"),
                overridableInterfaceNames = emptyList(),
                isPublic = false,
            ),
        )
        KotlinWinRTAuthoringCandidateFile.write(scanner, candidates)
        KotlinWinRTAuthoringCandidateFile.write(compiler, candidates)

        validateAuthoredCandidateHandoff(scanner.toFile(), compiler.toFile())
    }

    @Test
    fun rejects_mismatched_scanner_and_compiler_authored_candidate_files() {
        val scanner = Files.createTempFile("kotlin-winrt-scanner-candidates-", ".tsv")
        val compiler = Files.createTempFile("kotlin-winrt-compiler-candidates-", ".tsv")
        KotlinWinRTAuthoringCandidateFile.write(scanner, emptyList())
        KotlinWinRTAuthoringCandidateFile.write(
            compiler,
            listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRTBaseClassName = null,
                    winRTInterfaceNames = listOf("Windows.Foundation.IStringable"),
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
        val scannerCandidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf("Windows.Foundation.IStringable"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        KotlinWinRTAuthoringCandidateFile.write(scanner, listOf(scannerCandidate))
        KotlinWinRTAuthoringCandidateFile.write(compiler, listOf(scannerCandidate.copy(isPublic = false)))

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
    fun validates_type_details_handoff_when_only_formatting_differs() {
        val scanner = Files.createTempDirectory("kotlin-winrt-scanner-typedetails-")
        val compiler = Files.createTempDirectory("kotlin-winrt-compiler-typedetails-")
        val relativePath = Path.of("sample/WinRT_App_TypeDetails.kt")
        Files.createDirectories(scanner.resolve(relativePath).parent)
        Files.createDirectories(compiler.resolve(relativePath).parent)
        scanner.resolve(relativePath).writeText(
            """
            object WinRT_App_TypeDetails {
                val names = listOf("A", "B")
            }
            """.trimIndent(),
        )
        compiler.resolve(relativePath).writeText(
            """
            object WinRT_App_TypeDetails
            {
                val names =
                    listOf(
                        "A",
                        "B"
                    )
            }
            """.trimIndent(),
        )

        validateAuthoredDirectoryHandoff(
            description = "authored TypeDetails",
            scannerDirectory = scanner.toFile(),
            compilerDirectory = compiler.toFile(),
        )
    }

    @Test
    fun rejects_type_details_handoff_when_tokens_differ() {
        val scanner = Files.createTempDirectory("kotlin-winrt-scanner-typedetails-")
        val compiler = Files.createTempDirectory("kotlin-winrt-compiler-typedetails-")
        val relativePath = Path.of("sample/WinRT_App_TypeDetails.kt")
        Files.createDirectories(scanner.resolve(relativePath).parent)
        Files.createDirectories(compiler.resolve(relativePath).parent)
        scanner.resolve(relativePath).writeText("object WinRT_App_TypeDetails { val name = \"A\" }")
        compiler.resolve(relativePath).writeText("object WinRT_App_TypeDetails { val name = \"B\" }")

        val error = runCatching {
            validateAuthoredDirectoryHandoff(
                description = "authored TypeDetails",
                scannerDirectory = scanner.toFile(),
                compilerDirectory = compiler.toFile(),
            )
        }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(error!!.message.orEmpty(), error.message.orEmpty().contains("Changed files: sample/WinRT_App_TypeDetails.kt"))
    }

    @Test
    fun merges_scanned_authored_runtime_classes_into_projection_metadata_model() {
        val augmented = KotlinWinRTAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            model = model(),
            candidates = listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRTBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
        )

        val authoredType = augmented.namespaces.single { namespace -> namespace.name == "sample" }.types.single()
        assertEquals(WinRTTypeKind.RuntimeClass, authoredType.kind)
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

        KotlinWinRTAuthoringMetadataModel.writeDescriptor(
            candidates = listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRTBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
            outputFile = output,
        )

        assertEquals(
            "sample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\ttrue\t\t\n",
            output.readText(),
        )
    }

    @Test
    fun renders_generated_type_details_for_scanned_authored_type() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRTBaseClassName = "Microsoft.UI.Xaml.Application",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        assertTrue(generated.contains("WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer))"))
        assertTrue(generated.contains("rawArgs"))
        assertTrue(generated.contains("LaunchActivatedEventArgs.Metadata.wrap"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("Guid(\"aaaaaaaa-1111-2222-3333-444444444444\")"))
        assertTrue(generated.contains("__winrtAuthoringInvokeOnLaunched(__arg0)"))
        assertTrue(generated.contains("try {"))
        assertTrue(generated.contains("ExceptionHelpers.setErrorInfo(__exception)"))
        assertTrue(generated.contains("ExceptionHelpers.getHRForException(__exception).value"))
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
    fun renders_plain_authored_interface_methods_against_source_class() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-plain-interface-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "NativeClosableThing",
            sourceTypeName = "sample.NativeClosableThing",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf("Windows.Foundation.IClosable"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IClosable",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Close",
                                    returnTypeName = "Void",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_NativeClosableThing_TypeDetails.kt").readText()
        assertTrue(generated.contains("(value as NativeClosableThing).close()"))
        assertFalse(generated.contains("__winrtAuthoringInvokeClose"))
        assertFalse(generated.contains("Authored WinRT override"))
    }

    @Test
    fun rejects_authored_type_details_for_missing_winrt_interface_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-missing-interface-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRTBaseClassName = "Microsoft.UI.Xaml.Application",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IMissingOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IMissingOverrides"),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRTBaseClassName = "Microsoft.UI.Xaml.Application",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.Application"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Application"),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalStringable",
            sourceTypeName = "sample.LocalStringable",
            winRTBaseClassName = "Windows.Foundation.IStringable",
            winRTInterfaceNames = listOf("Windows.Foundation.IStringable"),
            overridableInterfaceNames = listOf("Windows.Foundation.IStringable"),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
    fun renders_authored_type_details_for_interface_events_without_explicit_accessor_methods() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-event-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalWidget",
            sourceTypeName = "sample.LocalWidget",
            winRTBaseClassName = "Sample.Widget",
            winRTInterfaceNames = listOf("Sample.IWidgetOverrides"),
            overridableInterfaceNames = listOf("Sample.IWidgetOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Widget",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetChangedHandler",
                            kind = WinRTTypeKind.Delegate,
                            iid = io.github.composefluent.winrt.runtime.Guid("44444444-1111-2222-4444-555555555555"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(name = "sender", typeName = "Sample.Widget"),
                                    ),
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-1111-2222-4444-555555555555"),
                            events = listOf(
                                WinRTEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.WidgetChangedHandler",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventRegistrationToken",
                            kind = WinRTTypeKind.Struct,
                            fields = listOf(
                                WinRTFieldDefinition(name = "Value", typeName = "Int64"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalWidget_TypeDetails.kt").readText()
        assertTrue(generated.contains("WidgetChangedHandler.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeadd_Changed(__arg0)"))
        assertTrue(generated.contains("EventRegistrationToken.Metadata.copyTo(__result as EventRegistrationToken"))
        assertTrue(generated.contains("WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Int64))"))
        assertTrue(generated.contains("val __arg0 = EventRegistrationToken(rawArgs[0] as Long)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeremove_Changed(__arg0)"))
    }

    @Test
    fun renders_authored_type_details_for_event_accessor_methods() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-event-accessor-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalWidget",
            sourceTypeName = "sample.LocalWidget",
            winRTBaseClassName = "Sample.Widget",
            winRTInterfaceNames = listOf("Sample.IWidgetOverrides"),
            overridableInterfaceNames = listOf("Sample.IWidgetOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Widget",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetChangedHandler",
                            kind = WinRTTypeKind.Delegate,
                            iid = io.github.composefluent.winrt.runtime.Guid("44444444-1111-2222-4444-555555555555"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(name = "sender", typeName = "Sample.Widget"),
                                    ),
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-1111-2222-4444-555555555555"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "add_Changed",
                                    returnTypeName = "Windows.Foundation.EventRegistrationToken",
                                    parameters = listOf(
                                        WinRTParameterDefinition(name = "handler", typeName = "Sample.WidgetChangedHandler"),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "remove_Changed",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(name = "token", typeName = "Windows.Foundation.EventRegistrationToken"),
                                    ),
                                ),
                            ),
                            events = listOf(
                                WinRTEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.WidgetChangedHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventRegistrationToken",
                            kind = WinRTTypeKind.Struct,
                            fields = listOf(
                                WinRTFieldDefinition(name = "Value", typeName = "Int64"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalWidget_TypeDetails.kt").readText()
        assertTrue(generated.contains("WidgetChangedHandler.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeadd_Changed(__arg0)"))
        assertTrue(generated.contains("EventRegistrationToken.Metadata.copyTo(__result as EventRegistrationToken"))
        assertTrue(generated.contains("WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Int64))"))
        assertTrue(generated.contains("val __arg0 = EventRegistrationToken(rawArgs[0] as Long)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeremove_Changed(__arg0)"))
    }

    @Test
    fun rejects_authored_type_details_for_static_interface_members() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-static-interface-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalWidget",
            sourceTypeName = "sample.LocalWidget",
            winRTBaseClassName = "Sample.Widget",
            winRTInterfaceNames = listOf("Sample.IWidgetStatics"),
            overridableInterfaceNames = listOf("Sample.IWidgetStatics"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Widget",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetStatics",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-1111-2222-4444-666666666666"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "CreateDefault",
                                    returnTypeName = "Sample.Widget",
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("static method 'CreateDefault'"))
            assertTrue(error.message.orEmpty().contains("TypeDetails instance CCW generation cannot expose static interface members"))
            assertFalse(Files.exists(output.resolve("sample/WinRT_LocalWidget_TypeDetails.kt")))
            return
        }

        throw AssertionError("Expected authored WinRT static interface members to fail closed.")
    }

    @Test
    fun renders_object_alias_override_parameters_through_object_marshaller() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRTBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml.Controls",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "ContentControl",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "IContentControlOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9937"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "OnContentChanged",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRTParameterDefinition("oldContent", "System.Object"),
                                        WinRTParameterDefinition("newContent", "Any"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRTObjectMarshaller.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("WinRTObjectMarshaller.fromAbi(rawArgs[1] as RawAddress)"))
        assertTrue(
            generated,
            Regex("""ComMethodSignature\.of\(\s*ComAbiValueKind\.Pointer,\s*ComAbiValueKind\.Pointer\s*\)""").containsMatchIn(generated),
        )
        assertTrue(generated, !generated.contains("ComMethodSignature.of(ComAbiValueKind.Pointer, , ComAbiValueKind.Pointer)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("Guid(\"2504174a-017e-5a2d-9c28-d97c66ae9937\")"))
        assertTrue(generated.contains("__winrtAuthoringInvokeOnContentChanged(__arg0, __arg1)"))
        assertTrue(generated.contains("(value as ContentControl).__winrtAuthoringInvokeOnContentChanged"))
        assertTrue(generated, !generated.contains("ContentControl::class.java"))
        assertTrue(generated, !generated.contains("Any::class.java, Any::class.java"))
        assertTrue(generated, !generated.contains("Object.Metadata.wrap"))
    }

    @Test
    fun renders_projected_struct_overrides_through_projection_metadata_types() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-mapped-struct-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRTBaseClassName = "Sample.Shape",
            winRTInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRTTypeKind.Struct,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Rect",
                            kind = WinRTTypeKind.Struct,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Size",
                            kind = WinRTTypeKind.Struct,
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9940"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "MeasureOverride",
                                    returnTypeName = "Windows.Foundation.Size",
                                    parameters = listOf(
                                        WinRTParameterDefinition("availableSize", "Windows.Foundation.Size"),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetPeerFromPointCore",
                                    returnTypeName = "Windows.Foundation.Rect",
                                    parameters = listOf(
                                        WinRTParameterDefinition("point", "Windows.Foundation.Point"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalShape_TypeDetails.kt").readText()
        assertTrue(generated.contains("import windows.foundation.Point"))
        assertTrue(generated.contains("import windows.foundation.Rect"))
        assertTrue(generated.contains("import windows.foundation.Size"))
        assertTrue(generated.contains("ComAbiValueKind.Struct(Size.Metadata.layout.abiLayout)"))
        assertTrue(generated.contains("Size.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Point.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Rect.Metadata.copyTo(__result as Rect"))
        assertTrue(generated, !generated.contains("import io.github.composefluent.winrt.runtime.Point"))
        assertTrue(generated, !generated.contains("import io.github.composefluent.winrt.runtime.Rect"))
        assertTrue(generated, !generated.contains("import io.github.composefluent.winrt.runtime.Size"))
    }

    @Test
    fun renders_runtime_class_override_parameters_through_metadata_wrap() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-parameter-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetPeerCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("peer", "Sample.Peer")),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetPeerInterfaceCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("peer", "Sample.IPeer")),
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("77777777-7777-8888-9999-aaaaaaaaaaaa"),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt").readText()
        assertTrue(generated.contains("Peer.Metadata.wrap"))
        assertTrue(generated.contains("Peer.Metadata.wrap(IInspectableReference"))
        assertTrue(generated.contains("IPeer.Metadata.wrap(IUnknownReference"))
        assertFalse(generated.contains("IPeer.Metadata.wrap(IInspectableReference"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetPeerCore(__arg0)"))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetPeerInterfaceCore(__arg0)"))
    }

    @Test
    fun renders_authored_runtime_class_override_parameters_through_object_marshaller() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-authored-object-parameter-details-")
        val peerCandidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "Peer",
            sourceTypeName = "sample.Peer",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf("Sample.IPeer"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        val providerCandidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "sample",
                            name = "Peer",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IPeer",
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetPeerCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("peer", "sample.Peer")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(peerCandidate, providerCandidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalPeerProvider_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRTObjectMarshaller.fromAbi(rawArgs[0] as RawAddress) as Peer"))
        assertTrue(generated, !generated.contains("Peer.Metadata.wrap"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetPeerCore(__arg0)"))
    }

    @Test
    fun rejects_unsupported_object_override_parameters() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-bad-object-parameter-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerCallback",
                            kind = WinRTTypeKind.Unknown,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetPeerCallbackCore",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("callback", "Sample.PeerCallback")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalStringable",
            sourceTypeName = "sample.LocalStringable",
            winRTBaseClassName = "Sample.IStringableBase",
            winRTInterfaceNames = listOf("Sample.IStringableOverrides"),
            overridableInterfaceNames = listOf("Sample.IStringableOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableBase",
                            kind = WinRTTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IStringableOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "System.String",
                                    parameters = listOf(WinRTParameterDefinition("value", "System.String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalStringable_TypeDetails.kt").readText()
        assertTrue(generated.contains("HString.fromHandle(rawArgs[0] as RawAddress, owner = false)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeTransform(__arg0)"))
        assertTrue(generated.contains("PlatformAbi.writePointer("))
        assertTrue(generated.contains("rawArgs[1] as RawAddress"))
        assertTrue(generated.contains("HString.create("))
        assertTrue(generated.contains("__result"))
        assertTrue(generated.contains(".handle"))
    }

    @Test
    fun renders_object_override_returns_with_declared_interface_iid() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-return-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IPeer",
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetPeerCore",
                                    returnTypeName = "Sample.Peer",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Peer",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
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
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalPeerProvider",
            sourceTypeName = "sample.LocalPeerProvider",
            winRTBaseClassName = "Sample.PeerProvider",
            winRTInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            overridableInterfaceNames = listOf("Sample.IPeerProviderOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "PeerProvider",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeer",
                            kind = WinRTTypeKind.Interface,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IPeerProviderOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                            methods = listOf(
                                WinRTMethodDefinition(
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
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFundamentals",
            sourceTypeName = "sample.LocalFundamentals",
            winRTBaseClassName = "Sample.FundamentalBase",
            winRTInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            overridableInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "FundamentalBase",
                            kind = WinRTTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFundamentalOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IFundamentalOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetEnabled",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("value", "System.Boolean")),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetSymbol",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("value", "System.Char")),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetAmount",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("value", "System.Byte")),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetRatio",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("value", "System.Single")),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetEnabled",
                                    returnTypeName = "System.Boolean",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetSymbol",
                                    returnTypeName = "System.Char",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetAmount",
                                    returnTypeName = "System.UInt32",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetRatio",
                                    returnTypeName = "System.Single",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
    fun renders_authored_interface_property_accessors_in_vtable_order() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-property-accessor-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalJsonValue",
            sourceTypeName = "sample.LocalJsonValue",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf("Sample.IJsonValue"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "JsonValueType",
                            kind = WinRTTypeKind.Enum,
                            enumUnderlyingType = WinRTIntegralType.Int32,
                            enumMembers = listOf(
                                WinRTEnumMemberDefinition("String", 3u),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IJsonValue",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                            properties = listOf(
                                WinRTPropertyDefinition(
                                    name = "ValueType",
                                    typeName = "Sample.JsonValueType",
                                    getterMethodName = "get_ValueType",
                                    getterMethodRowId = 6,
                                ),
                            ),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Stringify",
                                    returnTypeName = "System.String",
                                    methodRowId = 7,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalJsonValue_TypeDetails.kt").readText()
        val getterIndex = generated.indexOf("(value as LocalJsonValue).valueType")
        val methodIndex = generated.indexOf("(value as LocalJsonValue).stringify()")
        assertTrue(generated, getterIndex >= 0)
        assertTrue(generated, methodIndex > getterIndex)
        assertTrue(generated, generated.contains("JsonValueType.Metadata.toAbi(__result"))
        assertTrue(generated, generated.contains("as JsonValueType)"))
        assertFalse(generated, generated.contains("(value as LocalJsonValue).get_ValueType()"))
    }

    @Test
    fun renders_uint32_flags_enum_override_parameters_and_returns_through_unsigned_abi_carrier() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-enum-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFlags",
            sourceTypeName = "sample.LocalFlags",
            winRTBaseClassName = "Sample.FlagsBase",
            winRTInterfaceNames = listOf("Sample.IFlagsOverrides"),
            overridableInterfaceNames = listOf("Sample.IFlagsOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetFlags",
                            kind = WinRTTypeKind.Enum,
                            enumUnderlyingType = WinRTIntegralType.UInt32,
                            enumMembers = listOf(
                                WinRTEnumMemberDefinition("None", 0u),
                                WinRTEnumMemberDefinition("Enabled", 1u),
                            ),
                            customAttributes = listOf(WinRTCustomAttributeDefinition("System.FlagsAttribute")),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "FlagsBase",
                            kind = WinRTTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFlagsOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IFlagsOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444445"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetFlags",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRTParameterDefinition("flags", "Sample.WidgetFlags")),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetFlags",
                                    returnTypeName = "Sample.WidgetFlags",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRTBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "UIElement",
                            kind = WinRTTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IUIElementOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IUIElementOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("bbbbbbbb-1111-2222-3333-444444444444"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                WinRTCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(WinRTCustomAttributeValue.TypeValue("Microsoft.UI.Xaml.UIElement")),
                                ),
                            ),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "OnDisconnectVisualChildren",
                                    returnTypeName = "Void",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("(value as UIElement).__winrtAuthoringInvokeOnDisconnectVisualChildren"))
        assertTrue(generated.contains("__winrtAuthoringInvokeOnDisconnectVisualChildren()"))
        assertFalse(generated.contains("(value.winrtAs("))
        assertTrue(generated, !generated.contains("ContentControl::__class.java"))
        assertTrue(generated, !generated.contains("findDeclaredMethod"))
    }

    @Test
    fun renders_authored_nullable_reference_parameters_and_returns_through_reference_projection_helpers() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-reference-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalSettingsOwner",
            sourceTypeName = "sample.LocalSettingsOwner",
            winRTBaseClassName = "Sample.SettingsOwner",
            winRTInterfaceNames = listOf("Sample.ISettingsOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.ISettingsOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "SettingsOwner",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.ISettingsOwner",
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "ISettingsOwner",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IReference",
                            kind = WinRTTypeKind.Interface,
                            genericParameterCount = 1,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "ISettingsOwnerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetRetryCountCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "retryCount",
                                            typeName = "Windows.Foundation.IReference<System.Int32>",
                                            typeSignature = WinRTTypeRef.named(
                                                "Windows.Foundation.IReference",
                                                typeArguments = listOf(WinRTTypeRef.named("System.Int32")),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetDisplayNameCore",
                                    returnTypeName = "Windows.Foundation.IReference<System.String>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalSettingsOwner_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRTReferenceProjection.fromAbi(rawArgs[0] as RawAddress"))
        assertTrue(generated.contains("ParameterizedInterfaceId.createFromParameterizedInterface("))
        assertTrue(generated.contains("IID.IReference"))
        assertTrue(generated.contains("WinRTTypeSignature.int32()"))
        assertTrue(generated.contains("as Int?"))
        assertTrue(generated.contains("(value as SettingsOwner).__winrtAuthoringInvokeSetRetryCountCore(__arg0)"))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetRetryCountCore(__arg0)"))
        assertTrue(generated.contains("WinRTReferenceProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTTypeSignature.string()"))
        assertFalse(generated.contains("winrtAs(WinRTTypeHandle("))
        assertTrue(generated, !generated.contains("detachCCWForObject(__result"))
    }

    @Test
    fun renders_authored_delegate_parameters_and_returns_through_delegate_projection_helpers() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-delegate-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalWidgetOwner",
            sourceTypeName = "sample.LocalWidgetOwner",
            winRTBaseClassName = "Sample.WidgetOwner",
            winRTInterfaceNames = listOf("Sample.IWidgetOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.IWidgetOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetOwner",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IWidgetOwner",
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOwner",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetHandler",
                            kind = WinRTTypeKind.Delegate,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "name",
                                            typeName = "System.String",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOwnerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetWidgetHandlerCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "handler",
                                            typeName = "Sample.WidgetHandler",
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetWidgetHandlerCore",
                                    returnTypeName = "Sample.WidgetHandler",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalWidgetOwner_TypeDetails.kt").readText()
        assertTrue(generated.contains("WidgetHandler.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetWidgetHandlerCore(__arg0)"))
        assertTrue(generated.contains("MarshalDelegate.fromProjected(__result as WinRTProjectedDelegate)"))
        assertTrue(generated, !generated.contains("detachCCWForObject(__result"))
    }

    @Test
    fun rejects_authored_delegate_parameter_without_iid_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-delegate-missing-iid-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalWidgetOwner",
            sourceTypeName = "sample.LocalWidgetOwner",
            winRTBaseClassName = "Sample.WidgetOwner",
            winRTInterfaceNames = listOf("Sample.IWidgetOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.IWidgetOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetOwner",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.IWidgetOwner",
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOwner",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetHandler",
                            kind = WinRTTypeKind.Delegate,
                            methods = listOf(WinRTMethodDefinition(name = "Invoke", returnTypeName = "Unit")),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidgetOwnerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetWidgetHandlerCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "handler",
                                            typeName = "Sample.WidgetHandler",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
                candidates = listOf(candidate),
                metadataModel = metadataModel,
                outputDirectory = output,
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("delegate 'Sample.WidgetHandler' used by 'handler' has no IID metadata"))
            return
        }

        throw AssertionError("Expected authored delegate parameter without IID metadata to fail closed.")
    }

    @Test
    fun renders_authored_collection_returns_through_generic_collection_projection_helpers() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-collection-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalAutomationPeer",
            sourceTypeName = "sample.LocalAutomationPeer",
            winRTBaseClassName = "Microsoft.UI.Xaml.Automation.Peers.AutomationPeer",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml.Automation.Peers",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "AutomationPeer",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Automation.Peers.IAutomationPeer",
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeer",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetControlledPeersCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetControlledPeerInterfacesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Microsoft.UI.Xaml.Automation.Peers.IAutomationPeer>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetDescribedByCore",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRTMethodDefinition(
                                    name = "FindSubElementsForTouchTargeting",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetNamedPeersCore",
                                    returnTypeName = "Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetMutableNamesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<System.String, System.String>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetPeerGroupsCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetRawChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Object>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetNamesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<System.String>",
                                ),
                                WinRTMethodDefinition(
                                    name = "SetNamesCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "names",
                                            typeName = "Windows.Foundation.Collections.IVector<System.String>",
                                            typeSignature = WinRTTypeRef.named(
                                                "Windows.Foundation.Collections.IVector",
                                                typeArguments = listOf(WinRTTypeRef.named("System.String")),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetNamedPeersCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "peers",
                                            typeName = "Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                            typeSignature = WinRTTypeRef.named(
                                                "Windows.Foundation.Collections.IMapView",
                                                typeArguments = listOf(
                                                    WinRTTypeRef.named("System.String"),
                                                    WinRTTypeRef.named("Microsoft.UI.Xaml.Automation.Peers.AutomationPeer"),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetPeerGroupsCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "peerGroups",
                                            typeName = "Windows.Foundation.Collections.IVectorView<Windows.Foundation.Collections.IMapView<System.String, Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>>",
                                            typeSignature = WinRTTypeRef.named(
                                                "Windows.Foundation.Collections.IVectorView",
                                                typeArguments = listOf(
                                                    WinRTTypeRef.named(
                                                        "Windows.Foundation.Collections.IMapView",
                                                        typeArguments = listOf(
                                                            WinRTTypeRef.named("System.String"),
                                                        WinRTTypeRef.named("Microsoft.UI.Xaml.Automation.Peers.AutomationPeer"),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "StartBackgroundWorkCore",
                                    returnTypeName = "Windows.Foundation.IAsyncAction",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetDeferredNameCore",
                                    returnTypeName = "Windows.Foundation.IAsyncOperation<System.String>",
                                ),
                                WinRTMethodDefinition(
                                    name = "StartBackgroundWorkWithProgressCore",
                                    returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<System.UInt32>",
                                ),
                                WinRTMethodDefinition(
                                    name = "GetDeferredNameWithProgressCore",
                                    returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<System.String, System.UInt32>",
                                ),
                                WinRTMethodDefinition(
                                    name = "SetDeferredNameCore",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "deferredName",
                                            typeName = "Windows.Foundation.IAsyncOperation<System.String>",
                                            typeSignature = WinRTTypeRef.named(
                                                "Windows.Foundation.IAsyncOperation",
                                                typeArguments = listOf(WinRTTypeRef.named("System.String")),
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

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalAutomationPeer_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRTListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTReadOnlyListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTIterableProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTReadOnlyDictionaryProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTDictionaryProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRTReferenceValueAdapter<Iterable<AutomationPeer>>"))
        assertTrue(generated.contains("WinRTReferenceValueAdapter<Map<String, AutomationPeer>>"))
        assertTrue(generated.contains("WinRTReferenceValueAdapter<IAutomationPeer>"))
        assertTrue(generated.contains("WinRTCollectionInterfaceIds.iterableSignature"))
        assertTrue(generated.contains("WinRTCollectionInterfaceIds.mapViewSignature"))
        assertTrue(generated.contains("WinRTReferenceValueAdapters.runtimeClass(AutomationPeer::class"))
        assertTrue(generated.contains("IAutomationPeer.Metadata.wrap(reference!!)"))
        assertTrue(generated.contains("WinRTReferenceValueAdapters.object_"))
        assertTrue(generated.contains("WinRTReferenceValueAdapters.string"))
        assertTrue(generated.contains("WinRTListProjection.fromAbi(rawArgs[0] as RawAddress"))
        assertTrue(generated.contains("(value as "))
        assertTrue(generated.contains("__winrtAuthoringInvokeSetNamesCore(__arg0)"))
        assertTrue(generated.contains("WinRTReadOnlyDictionaryProjection.fromAbi(rawArgs[0]"))
        assertTrue(generated.contains("as RawAddress"))
        assertTrue(generated.contains("WinRTReferenceValueAdapters.string"))
        assertTrue(generated.contains("AutomationPeer).__winrtAuthoringInvokeSetNamedPeersCore(__arg0)"))
        assertTrue(generated.contains("WinRTReadOnlyListProjection.fromAbi(rawArgs[0]"))
        assertTrue(generated.contains("WinRTReadOnlyDictionaryProjection.fromAbi(PlatformAbi.fromRawComPtr(reference.pointer)"))
        assertTrue(generated.contains("AutomationPeer).__winrtAuthoringInvokeSetPeerGroupsCore(__arg0)"))
        assertTrue(generated.contains("WinRTAsyncInterfaceIds.IAsyncAction"))
        assertTrue(generated.contains("WinRTAsyncInterfaceIds.IAsyncOperationGeneric"))
        assertTrue(generated.contains("WinRTAsyncInterfaceIds.IAsyncActionWithProgressGeneric"))
        assertTrue(generated.contains("WinRTAsyncInterfaceIds.IAsyncOperationWithProgressGeneric"))
        assertTrue(generated.contains("WinRTTypeSignature.uint32()"))
        assertTrue(generated.contains("WinRTAsyncOperationReference<String>"))
        assertTrue(generated.contains("WinRTObjectMarshaller.fromAbi(rawArgs[0]"))
        assertTrue(generated, !generated.contains("createCCWForObject(__result, IID.IInspectable)"))
    }

    @Test
    fun renders_receive_array_returns_through_two_abi_out_slots() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-receive-array-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalBufferOwner",
            sourceTypeName = "sample.LocalBufferOwner",
            winRTBaseClassName = "Sample.BufferOwner",
            winRTInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "BufferOwner",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IThing",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("55555555-3333-2222-1111-000000000000"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Point",
                            kind = WinRTTypeKind.Struct,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IBufferOwnerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("44444444-3333-2222-1111-000000000000"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetNumbers",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "numbers",
                                            typeName = "Array<Int32>",
                                            direction = io.github.composefluent.winrt.metadata.WinRTParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Int32"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "names",
                                            typeName = "Array<System.String>",
                                            direction = io.github.composefluent.winrt.metadata.WinRTParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("System.String"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetThings",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "things",
                                            typeName = "Array<Sample.IThing>",
                                            direction = io.github.composefluent.winrt.metadata.WinRTParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Sample.IThing"), isByRef = true),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "GetPoints",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "points",
                                            typeName = "Array<Sample.Point>",
                                            direction = io.github.composefluent.winrt.metadata.WinRTParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Sample.Point"), isByRef = true),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalBufferOwner_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32"))
        assertTrue(generated, generated.contains("ComAbiValueKind.Pointer)) { rawArgs ->"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeGetNumbers()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() *"))
        assertTrue(generated, generated.contains("4)"))
        assertTrue(generated, generated.contains("__result.forEachIndexed { __index, __element ->"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(PlatformAbi.slice(__returnArrayData, __index.toLong() *"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(rawArgs[0] as RawAddress, __result.size)"))
        assertTrue(generated, generated.contains("PlatformAbi.writePointer(rawArgs[1] as RawAddress, __returnArrayData)"))
        assertTrue(generated, !generated.contains("__arg0"))
        assertTrue(generated, !generated.contains("rawArgs[2]"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeGetNames()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() * 8"))
        assertTrue(generated, generated.contains("PlatformAbi.writePointer(PlatformAbi.slice(__returnArrayData, __index.toLong() *"))
        assertTrue(generated, generated.contains("8, 8), HString.create(__element).handle)"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeGetThings()"))
        assertTrue(generated, generated.contains("ComWrappersSupport.detachCCWForObject(__element"))
        assertTrue(generated, generated.contains("Guid(\"55555555-3333-2222-1111-000000000000\")"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeGetPoints()"))
        assertTrue(generated, generated.contains("PlatformAbi.allocateBytesOwned(__result.size.toLong() *"))
        assertTrue(generated, generated.contains("Point.Metadata.layout.sizeBytes, Point.Metadata.layout.alignmentBytes)"))
        assertTrue(generated, generated.contains("Point.Metadata.layout.alignmentBytes"))
        assertTrue(generated, generated.contains("Point.Metadata.copyTo(__element"))
        assertTrue(generated, generated.contains("Point, PlatformAbi.slice(__returnArrayData"))
    }

    @Test
    fun renders_non_trailing_receive_array_returns() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-non-trailing-receive-array-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalBufferOwner",
            sourceTypeName = "sample.LocalBufferOwner",
            winRTBaseClassName = "Sample.BufferOwner",
            winRTInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            overridableInterfaceNames = listOf("Sample.IBufferOwnerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "BufferOwner",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IBufferOwnerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("55555555-3333-2222-1111-999999999999"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetNumbers",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "numbers",
                                            typeName = "Array<Int32>",
                                            direction = io.github.composefluent.winrt.metadata.WinRTParameterDirection.Out,
                                            typeIsByRef = true,
                                            isOutParameter = true,
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Int32"), isByRef = true),
                                        ),
                                        WinRTParameterDefinition(
                                            name = "kind",
                                            typeName = "Int32",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalBufferOwner_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("val __arg1 = rawArgs[2] as Int"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeGetNumbers(__arg1)"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(rawArgs[0] as RawAddress, __result.size)"))
        assertTrue(generated, generated.contains("PlatformAbi.writePointer(rawArgs[1] as RawAddress, __returnArrayData)"))
    }

    @Test
    fun renders_read_only_array_parameters_from_two_abi_slots() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-array-parameter-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalBufferConsumer",
            sourceTypeName = "sample.LocalBufferConsumer",
            winRTBaseClassName = "Sample.BufferConsumer",
            winRTInterfaceNames = listOf("Sample.IBufferConsumerOverrides"),
            overridableInterfaceNames = listOf("Sample.IBufferConsumerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "BufferConsumer",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IThing",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("77777777-3333-2222-1111-000000000000"),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Point",
                            kind = WinRTTypeKind.Struct,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IBufferConsumerOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("66666666-3333-2222-1111-000000000000"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "SetNumbers",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "numbers",
                                            typeName = "Array<Int32>",
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Int32")),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "names",
                                            typeName = "Array<System.String>",
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("System.String")),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetThings",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "things",
                                            typeName = "Array<Sample.IThing>",
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Sample.IThing")),
                                        ),
                                    ),
                                ),
                                WinRTMethodDefinition(
                                    name = "SetPoints",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "points",
                                            typeName = "Array<Sample.Point>",
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Sample.Point")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalBufferConsumer_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32"))
        assertTrue(generated, generated.contains("ComAbiValueKind.Pointer)) { rawArgs ->"))
        assertTrue(generated, generated.contains("val __arrayLength = rawArgs[0] as Int"))
        assertTrue(generated, generated.contains("val __arrayData = rawArgs[1] as RawAddress"))
        assertTrue(generated, generated.contains("Array(__arrayLength) { __index ->"))
        assertTrue(generated, generated.contains("PlatformAbi.readInt32"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeSetNumbers(__arg0)"))
        assertTrue(generated, generated.contains("HString.fromHandle(PlatformAbi.readPointer"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeSetNames(__arg0)"))
        assertTrue(generated, generated.contains("IThing.Metadata.wrap(IInspectableReference"))
        assertTrue(generated, generated.contains("PlatformAbi.toRawComPtr(PlatformAbi.readPointer"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeSetThings(__arg0)"))
        assertTrue(generated, generated.contains("Point.Metadata.fromAbi(PlatformAbi.slice(__arrayData"))
        assertTrue(generated, generated.contains("(value as "))
        assertTrue(generated, generated.contains("__winrtAuthoringInvokeSetPoints(__arg0)"))
    }

    @Test
    fun supports_authored_collection_returns_with_struct_element_adapter() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-bad-collection-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRTBaseClassName = "Sample.Shape",
            winRTInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Value",
                            kind = WinRTTypeKind.Struct,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetValuesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Sample.Value>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalShape_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("WinRTReferenceValueAdapters.valueType("))
        assertTrue(generated, generated.contains("Value::class"))
        assertTrue(generated, generated.contains("WinRTTypeSignature.struct(\"Sample.Value\")"))
        assertTrue(generated, generated.contains("WinRTListProjection.fromManaged("))
    }

    @Test
    fun rejects_authored_collection_returns_with_missing_element_metadata() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-missing-collection-element-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRTBaseClassName = "Sample.Shape",
            winRTInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRTMethodDefinition(
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
            KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
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
        KotlinWinRTAuthoringMetadataModel.writeHostManifest(
            assemblyName = "SampleComponent",
            candidates = listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRTBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
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

    private fun model(): WinRTMetadataModel =
        WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRTTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IApplicationOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "OnLaunched",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "args",
                                            typeName = "Microsoft.UI.Xaml.LaunchActivatedEventArgs",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "LaunchActivatedEventArgs",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinRTTypeKind.Interface,
                        ),
                    ),
                ),
            ),
        )
}
