package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.compiler.authoring.projectionTypeIndexRecordForSourceType
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.runtime.Guid
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader

@OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)
class KotlinWinRTCompilerPluginTest {
    @Test
    fun command_line_processor_stores_metadata_index_option() {
        val configuration = CompilerConfiguration()
        val processor = KotlinWinRTCommandLineProcessor()
        processor.processOption(
            CliOption(
                optionName = "metadataIndex",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/kotlin-winrt/metadata-index.tsv",
            configuration,
        )

        assertEquals(
            "build/kotlin-winrt/metadata-index.tsv",
            configuration.get(KotlinWinRTCommandLineProcessor.METADATA_INDEX_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "typeIndexOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt/type-index.tsv",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt/type-index.tsv",
            configuration.get(KotlinWinRTCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoredCandidatesOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt/authored-candidates.tsv",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt/authored-candidates.tsv",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_CANDIDATES_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoredMetadataOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt-authoring/authored-metadata.tsv",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt-authoring/authored-metadata.tsv",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_METADATA_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoredWinmdOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt-authoring/sample.winmd",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt-authoring/sample.winmd",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_WINMD_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoredHostManifestOutput",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main/kotlin-winrt-authoring/sample.host.json",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main/kotlin-winrt-authoring/sample.host.json",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_HOST_MANIFEST_OUTPUT_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoringAssemblyName",
                valueDescription = "<name>",
                description = "",
                required = false,
            ),
            "sample",
            configuration,
        )
        assertEquals(
            "sample",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORING_ASSEMBLY_NAME_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "authoringTargetArtifactName",
                valueDescription = "<file>",
                description = "",
                required = false,
            ),
            "sample.jar",
            configuration,
        )
        assertEquals(
            "sample.jar",
            configuration.get(KotlinWinRTCommandLineProcessor.AUTHORING_TARGET_ARTIFACT_NAME_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "compilerSupportManifest",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/generated/kotlin-winrt/kotlin-winrt-support/compiler-support.tsv",
            configuration,
        )
        assertEquals(
            "build/generated/kotlin-winrt/kotlin-winrt-support/compiler-support.tsv",
            configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
        )
        processor.processOption(
            CliOption(
                optionName = "compilerSupportClassOutputDirectory",
                valueDescription = "<path>",
                description = "",
                required = false,
            ),
            "build/classes/kotlin/main",
            configuration,
        )
        assertEquals(
            "build/classes/kotlin/main",
            configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
        )
    }

    @Test
    fun registrar_installs_ir_generation_extension_for_k2_path() {
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        val registrar = KotlinWinRTCompilerPluginRegistrar()

        with(registrar) {
            storage.registerExtensions(CompilerConfiguration())
        }

        assertTrue(registrar.supportsK2)
        assertTrue(storage.registeredExtensions[IrGenerationExtension].orEmpty().any { extension ->
            extension is KotlinWinRTIrGenerationExtension
        })
    }

    @Test
    fun authoring_type_details_dispatches_overrides_through_kotlin_base_cast() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-type-details-")
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRTBaseClassName = "Microsoft.UI.Xaml.Application",
            winRTInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
        )
        val model = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "LaunchActivatedEventArgs",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRTTypeKind.Interface,
                            iid = Guid("a33e81ef-c665-503b-8827-d27ef1720a06"),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "OnLaunched",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        io.github.composefluent.winrt.metadata.WinRTParameterDefinition(
                                            name = "args",
                                            typeName = "Microsoft.UI.Xaml.LaunchActivatedEventArgs",
                                        ),
                                    ),
                                    methodRowId = 6,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(candidate), model, output)

        val contents = output.resolve("sample/WinRT_App_TypeDetails.kt").toFile().readText()
        assertTrue(contents, contents.contains("(value as Application).__winrtAuthoringInvokeOnLaunched(__arg0)"))
        assertFalse(contents, contents.contains("value.winrtAs("))
        assertTrue(contents, contents.contains("private val ccwDefinition: WinRTCcwDefinition = createCcwDefinition()"))
        assertTrue(contents, contents.contains("private val registrationToken: Unit = registerOnce()"))
        assertTrue(contents, contents.contains("private fun registerOnce()"))
        assertTrue(contents, contents.contains("public fun register()"))
        assertTrue(contents, contents.contains("registrationToken"))
        assertTrue(contents, contents.contains("ComWrappersSupport.registerStaticCcwDefinition(App::class, ccwDefinition)"))
        assertFalse(contents, contents.contains("registerAuthoringTypeDetailsFactory"))
        assertFalse(contents, contents.contains("createCcwDefinition(value: Any)"))

        val registrarContents = output
            .resolve("io/github/composefluent/winrt/projections/support/WinRTAuthoringTypeDetailsRegistrar.kt")
            .toFile()
            .readText()
        assertTrue(registrarContents, registrarContents.contains("private val registrationToken: Unit = registerOnce()"))
        assertTrue(registrarContents, registrarContents.contains("private fun registerOnce()"))
        assertTrue(registrarContents, registrarContents.contains("WinRT_App_TypeDetails.register()"))
        assertTrue(registrarContents, registrarContents.contains("public fun register()"))
        assertTrue(registrarContents, registrarContents.contains("registrationToken"))
    }

    @Test
    fun authoring_type_details_emit_semantic_inbound_callsites_only_for_direct_projection_shapes() {
        val callShapes = WinRTTypeDefinition(
            namespace = "Sample.CallSites",
            name = "ICallShapes",
            kind = WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555550"),
            methods = listOf(
                WinRTMethodDefinition(
                    name = "Transform",
                    returnTypeName = "System.Int32",
                    parameters = listOf(WinRTParameterDefinition("enabled", "System.Boolean")),
                    methodRowId = 6,
                ),
                WinRTMethodDefinition(
                    name = "EchoStatus",
                    returnTypeName = "Sample.CallSites.Status",
                    parameters = listOf(WinRTParameterDefinition("value", "Sample.CallSites.Status")),
                    methodRowId = 7,
                ),
                WinRTMethodDefinition(
                    name = "EchoInterface",
                    returnTypeName = "Sample.CallSites.IPlain",
                    parameters = listOf(WinRTParameterDefinition("value", "Sample.CallSites.IPlain")),
                    methodRowId = 8,
                ),
                WinRTMethodDefinition(
                    name = "EchoRuntimeClass",
                    returnTypeName = "Sample.CallSites.ProjectedThing",
                    parameters = listOf(WinRTParameterDefinition("value", "Sample.CallSites.ProjectedThing")),
                    methodRowId = 9,
                ),
                WinRTMethodDefinition(
                    name = "ConsumeAuthored",
                    returnTypeName = "Unit",
                    parameters = listOf(WinRTParameterDefinition("value", "sample.AuthoredPeer")),
                    methodRowId = 10,
                ),
                WinRTMethodDefinition(
                    name = "ConsumeHandler",
                    returnTypeName = "Unit",
                    parameters = listOf(WinRTParameterDefinition("value", "Sample.CallSites.Handler")),
                    methodRowId = 11,
                ),
                WinRTMethodDefinition(
                    name = "ConsumeString",
                    returnTypeName = "Unit",
                    parameters = listOf(WinRTParameterDefinition("value", "String")),
                    methodRowId = 12,
                ),
            ),
        )
        val plainInterface = WinRTTypeDefinition(
            namespace = "Sample.CallSites",
            name = "IPlain",
            kind = WinRTTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555551"),
        )
        val model = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample.CallSites",
                    types = listOf(
                        callShapes,
                        plainInterface,
                        WinRTTypeDefinition(
                            namespace = "Sample.CallSites",
                            name = "Status",
                            kind = WinRTTypeKind.Enum,
                            enumUnderlyingType = WinRTIntegralType.Int32,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample.CallSites",
                            name = "ProjectedThing",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = plainInterface.qualifiedName,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample.CallSites",
                            name = "Handler",
                            kind = WinRTTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(WinRTMethodDefinition("Invoke", "Unit")),
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "sample",
                            name = "AuthoredPeer",
                            kind = WinRTTypeKind.RuntimeClass,
                            defaultInterfaceName = plainInterface.qualifiedName,
                        ),
                    ),
                ),
            ),
        )
        val owner = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "Owner",
            sourceTypeName = "sample.Owner",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf(callShapes.qualifiedName),
            overridableInterfaceNames = emptyList(),
        )
        val authoredPeer = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "AuthoredPeer",
            sourceTypeName = "sample.AuthoredPeer",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf(plainInterface.qualifiedName),
            overridableInterfaceNames = emptyList(),
        )
        val output = Files.createTempDirectory("kotlin-winrt-authoring-inbound-callsites-")

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(owner, authoredPeer), model, output)

        val contents = output.resolve("sample/WinRT_Owner_TypeDetails.kt").toFile().readText()
        assertEquals(
            4,
            Regex("abiEntryPoint\\s*=\\s*winRTProjectionInboundEntryPoint").findAll(contents).count(),
        )
        assertTrue(contents, contents.contains("returnAbiType = \"kotlin.Int\""))
        assertTrue(contents, contents.contains("abiType = \"kotlin.Boolean\""))
        listOf(
            "Sample.CallSites.Status",
            "Sample.CallSites.IPlain",
            "Sample.CallSites.ProjectedThing",
        ).forEach { identity ->
            assertTrue(identity, contents.contains("returnAbiType = \"$identity\""))
            assertTrue(identity, contents.contains("abiType = \"$identity\""))
        }
        listOf("consumeAuthored(__arg0)", "consumeHandler(__arg0)", "consumeString(__arg0)").forEach { invocation ->
            val invocationIndex = contents.indexOf(invocation)
            assertTrue(invocation, invocationIndex >= 0)
            val methodStart = contents.lastIndexOf("WinRTInspectableMethodDefinition(", invocationIndex)
            val fallbackPrefix = contents.substring(methodStart, invocationIndex)
            assertTrue(invocation, fallbackPrefix.contains("rawArgs"))
            assertFalse(invocation, fallbackPrefix.contains("abiEntryPoint"))
        }
        assertTrue(contents, contents.contains("WinRTObjectMarshaller.fromAbi(rawArgs[0] as RawAddress) as AuthoredPeer"))
        assertFalse(contents, contents.contains("returnAbiType = \"sample.AuthoredPeer\""))
        assertFalse(contents, contents.contains("abiType = \"sample.AuthoredPeer\""))
    }

    @Test
    fun projection_type_index_records_map_kotlin_projection_names_to_winrt_metadata() {
        val record = projectionTypeIndexRecordForSourceType(
            sourceTypeName = "microsoft.ui.xaml.Window",
            winRTTypes = mapOf(
                "Microsoft.UI.Xaml.Window" to IndexedWinRTType(
                    qualifiedName = "Microsoft.UI.Xaml.Window",
                    kind = "RuntimeClass",
                    overridableInterfaces = emptyList(),
                    baseTypeName = "Microsoft.UI.Xaml.Controls.ContentControl",
                ),
            ),
        )

        assertEquals(
            "microsoft.ui.xaml.Window\tMicrosoft.UI.Xaml.Window\tRuntimeClass\tMicrosoft.UI.Xaml.Controls.ContentControl",
            record?.render(),
        )
    }

    @Test
    fun generated_source_root_is_derived_from_authoring_metadata_index() {
        val root = generatedSourceRootFromMetadataIndex(
            "C:\\repo\\build\\generated\\kotlin-winrt\\src\\main\\kotlin\\kotlin-winrt-authoring\\metadata-index.tsv",
        )

        assertEquals("c:/repo/build/generated/kotlin-winrt/src/main/kotlin", root)
        assertTrue(isGeneratedSourceFile("C:\\repo\\build\\generated\\kotlin-winrt\\src\\main\\kotlin\\microsoft\\ui\\xaml\\Window.kt", root))
        assertTrue(isGeneratedSourceFile("C:\\repo\\build\\generated\\kotlin-winrt-authoring\\src\\main\\kotlin\\sample\\WinRT_Widget_TypeDetails.kt", root))
        assertFalse(isGeneratedSourceFile("C:\\repo\\build\\generated\\kotlin-winrt-native-authoring-host\\src\\main\\kotlin\\support\\WinRTAuthoringHostExports.native.kt", root))
        assertTrue(
            isGeneratedSourceFile(
                "C:\\repo\\build\\generated\\kotlin-winrt-compiler-authoring\\compileKotlinMingwX64\\src\\commonMain\\kotlin\\sample\\WinRT_Widget_TypeDetails.kt",
                root,
            ),
        )
        val commonRoot = generatedSourceRootFromMetadataIndex(
            "C:\\repo\\build\\generated\\kotlin-winrt\\src\\commonMain\\kotlin\\kotlin-winrt-authoring\\metadata-index.tsv",
        )
        assertEquals("c:/repo/build/generated/kotlin-winrt/src/commonmain/kotlin", commonRoot)
        assertTrue(isGeneratedSourceFile("C:\\repo\\build\\generated\\kotlin-winrt\\src\\commonMain\\kotlin\\microsoft\\ui\\xaml\\Window.kt", commonRoot))
        assertTrue(isGeneratedSourceFile("C:\\repo\\build\\generated\\kotlin-winrt-authoring\\src\\commonMain\\kotlin\\sample\\WinRT_Widget_TypeDetails.kt", commonRoot))
        assertTrue(
            isGeneratedSourceFile(
                "C:\\repo\\build\\generated\\kotlin-winrt-compiler-authoring\\compileKotlinMingwX64\\src\\commonMain\\kotlin\\sample\\WinRT_Widget_TypeDetails.kt",
                commonRoot,
            ),
        )
        assertFalse(isGeneratedSourceFile("C:\\repo\\src\\main\\kotlin\\io\\github\\composefluent\\winrt\\projections\\support\\WinRTAuthoringCcwFactories.kt", root))
        assertFalse(isGeneratedSourceFile("C:\\repo\\src\\main\\kotlin\\sample\\WinRT_Widget_TypeDetails.kt", root))
        assertFalse(isGeneratedSourceFile("C:\\repo\\src\\main\\kotlin\\sample\\App.kt", root))
    }

    @Test
    fun compiler_support_manifest_records_generated_support_tables() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	12
            """.trimIndent() + "\n",
        )

        val entries = readCompilerSupportManifest(manifest)

        assertEquals(1, entries.size)
        assertEquals("projection-registrar", entries[0].kind)
        assertEquals("io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic", entries[0].className)
        assertEquals("projection-registrar.tsv", entries[0].sourceFile)
        assertEquals(12, entries[0].entries)
    }

    @Test
    fun compiler_support_manifest_rejects_unexpected_headers() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-header-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	entries	sourceFile
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	12	projection-registrar.tsv
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin expected compiler support manifest header"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_malformed_rows() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-malformed-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	not-a-number
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_extra_columns() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-extra-column-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1	owner	extra
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_negative_entry_counts() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-negative-count-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	-1
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_blank_required_columns() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-blank-columns-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_unknown_kinds() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-unknown-kind-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            unsupported-kind	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_mismatched_class_names() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-mismatched-class-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WrongIntrinsic	projection-registrar.tsv	1
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_removed_generic_instantiation_kind() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-generic-owner-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            generic-type-instantiation	io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations_sample_lib_jar	generic-instantiations.tsv	2
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("could not parse compiler support manifest row 2"))
    }

    @Test
    fun compiler_support_manifest_allows_artifact_scoped_authoring_type_details_registrar_class_names() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-authoring-registrar-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            authoring-type-details-registrar	io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_sample_lib	authoring-type-details-registrars.tsv	1
            """.trimIndent() + "\n",
        )

        val entries = readCompilerSupportManifest(manifest)

        assertEquals(1, entries.size)
        assertEquals("authoring-type-details-registrar", entries.single().kind)
        assertEquals(
            "io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_sample_lib",
            entries.single().className,
        )
    }

    @Test
    fun compiler_support_manifest_rejects_mismatched_source_files() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-mismatched-source-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	generic-instantiations.tsv	1
            """.trimIndent() + "\n",
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse compiler support manifest row 2"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_duplicate_entries() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-duplicate-", ".tsv")
        Files.writeString(
            manifest,
            listOf(
                listOf("kind", "className", "sourceFile", "entries"),
                listOf(
                    "projection-registrar",
                    "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                    "projection-registrar.tsv",
                    "1",
                ),
                listOf(
                    "projection-registrar",
                    "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                    "projection-registrar.tsv",
                    "1",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readCompilerSupportManifest(manifest) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate compiler support manifest entry"),
        )
    }

    @Test
    fun compiler_support_manifest_option_rejects_missing_file() {
        val missingManifest = Files.createTempDirectory("kotlin-winrt-missing-compiler-support-")
            .resolve("compiler-support.tsv")

        val error = runCatching {
            readCompilerSupportManifestIfConfigured(missingManifest.toString())
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin requires compiler support manifest"),
        )
    }

    @Test
    fun compiler_support_manifest_option_allows_unconfigured_path() {
        assertTrue(readCompilerSupportManifestIfConfigured(null).isEmpty())
        assertTrue(readCompilerSupportManifestIfConfigured("").isEmpty())
    }

    @Test
    fun compiler_support_manifest_writes_class_artifact() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-support-class-")
        val internalName = writeCompilerSupportManifestClass(
            entries = listOf(
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                    sourceFile = "projection-registrar.tsv",
                    entries = 12,
                    owner = "sample-owner",
                ),
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "xaml-component-resource",
                    className = "io.github.composefluent.winrt.projections.support.WinUiXamlComponentResources",
                    sourceFile = "xaml-component-resources.tsv",
                    entries = 5,
                ),
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "authoring-type-details-registrar",
                    className = "io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_sample",
                    sourceFile = "authoring-type-details-registrars.tsv",
                    entries = 4,
                ),
            ),
            outputDirectory = outputDirectory,
        )

        URLClassLoader(arrayOf(outputDirectory.toUri().toURL()), null).use { classLoader ->
            val klass = Class.forName(
                internalName!!.replace('/', '.'),
                false,
                classLoader,
            )
            assertEquals(3, klass.getField("ENTRY_COUNT").getInt(null))
            assertEquals(12, klass.getField("PROJECTION_REGISTRAR_ENTRIES").getInt(null))
            assertEquals(5, klass.getField("XAML_COMPONENT_RESOURCE_ENTRIES").getInt(null))
            assertEquals(4, klass.getField("AUTHORING_TYPE_DETAILS_REGISTRAR_ENTRIES").getInt(null))
        }
        assertFalse(
            Files.exists(
                outputDirectory.resolve(
                    "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest.class",
                ),
            ),
        )
    }

    @Test
    fun compiler_support_manifest_class_is_owner_scoped() {
        val sdkOutput = Files.createTempDirectory("kotlin-winrt-support-class-sdk-")
        val appSdkOutput = Files.createTempDirectory("kotlin-winrt-support-class-app-sdk-")
        val sdkName = writeCompilerSupportManifestClass(
            listOf(
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "sample.Support",
                    sourceFile = "projection-registrar.tsv",
                    entries = 1,
                    owner = "windows-sdk",
                ),
            ),
            sdkOutput,
        )
        val appSdkName = writeCompilerSupportManifestClass(
            listOf(
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "sample.Support",
                    sourceFile = "projection-registrar.tsv",
                    entries = 1,
                    owner = "windows-app-sdk",
                ),
            ),
            appSdkOutput,
        )

        assertNotEquals(sdkName, appSdkName)
        assertTrue(Files.isRegularFile(sdkOutput.resolve("$sdkName.class")))
        assertTrue(Files.isRegularFile(appSdkOutput.resolve("$appSdkName.class")))
    }

    @Test
    fun compiler_support_manifest_deletes_stale_class_artifact_when_entries_are_empty() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-empty-support-class-")
        val manifestInternalName = writeCompilerSupportManifestClass(
            entries = listOf(
                KotlinWinRTCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                    sourceFile = "projection-registrar.tsv",
                    entries = 12,
                ),
            ),
            outputDirectory = outputDirectory,
        )!!
        val manifestClass = outputDirectory.resolve("$manifestInternalName.class")
        assertTrue(Files.isRegularFile(manifestClass))

        writeCompilerSupportManifestClass(emptyList(), outputDirectory)

        assertFalse(Files.exists(manifestClass))
    }

    @Test
    fun projection_support_initializer_input_writes_content_addressed_class_artifact() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName", "interfaceIid"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-class-")

        val entries = readProjectionRegistrarEntries(input)
        val internalName = writeProjectionSupportInitializerClass(entries, outputDirectory)

        assertNotNull(internalName)
        assertTrue(internalName!!.startsWith("io/github/composefluent/winrt/projections/support/WinRTProjectionSupport_"))
        val supportClass = ClassReader(Files.readAllBytes(outputDirectory.resolve("$internalName.class")))
        val methodNames = mutableSetOf<String>()
        supportClass.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    name?.let(methodNames::add)
                    return null
                }
            },
            0,
        )
        assertTrue(methodNames.contains("initialize"))
        assertTrue(methodNames.contains("<clinit>"))
        assertFalse(methodNames.contains("registerChunk000"))
        val chunkClass = outputDirectory.resolve("${internalName}_Chunk000.class")
        assertTrue(Files.isRegularFile(chunkClass))
        val chunkMethodNames = mutableSetOf<String>()
        ClassReader(Files.readAllBytes(chunkClass)).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    name?.let(chunkMethodNames::add)
                    return null
                }
            },
            0,
        )
        assertTrue(chunkMethodNames.contains("register"))
    }

    @Test
    fun projection_support_initializer_input_owner_scopes_content_addressed_class_artifacts() {
        val entries = listOf(
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "java.lang.String",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
                interfaceIid = "",
            ),
        )
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-owner-class-")

        val libraryInternalName = writeProjectionSupportInitializerClass(
            entries = entries,
            outputDirectory = outputDirectory,
            ownerIdentity = "sample-lib.jar",
        )
        val applicationInternalName = writeProjectionSupportInitializerClass(
            entries = entries,
            outputDirectory = outputDirectory,
            ownerIdentity = "sample-app.jar",
        )

        assertNotNull(libraryInternalName)
        assertNotNull(applicationInternalName)
        assertTrue(libraryInternalName != applicationInternalName)
        assertFalse(Files.exists(outputDirectory.resolve("$libraryInternalName.class")))
        assertTrue(Files.isRegularFile(outputDirectory.resolve("$applicationInternalName.class")))
    }

    @Test
    fun projection_support_initializer_input_deletes_stale_content_addressed_class_artifacts() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-stale-class-")
        val firstEntries = listOf(
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "java.lang.String",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
                interfaceIid = "",
            ),
        )
        val secondEntries = firstEntries + KotlinWinRTProjectionRegistrarEntry(
            kotlinClassName = "java.lang.Integer",
            projectedTypeName = "Sample.Foundation.Gadget",
            kind = "RuntimeClass",
            baseTypeName = "Sample.Foundation.GadgetBase",
            metadataClassName = "",
            interfaceIid = "",
        )

        val staleInternalName = writeProjectionSupportInitializerClass(firstEntries, outputDirectory)
        val currentInternalName = writeProjectionSupportInitializerClass(secondEntries, outputDirectory)

        assertNotNull(staleInternalName)
        assertNotNull(currentInternalName)
        assertFalse(Files.exists(outputDirectory.resolve("$staleInternalName.class")))
        assertFalse(Files.exists(outputDirectory.resolve("${staleInternalName}_Chunk000.class")))
        assertTrue(Files.isRegularFile(outputDirectory.resolve("$currentInternalName.class")))
        assertTrue(Files.isRegularFile(outputDirectory.resolve("${currentInternalName}_Chunk000.class")))
    }

    @Test
    fun projection_support_initializer_input_deletes_stale_class_artifacts_when_entries_are_empty() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-empty-stale-class-")
        val entries = listOf(
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "java.lang.String",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
                interfaceIid = "",
            ),
        )

        val staleInternalName = writeProjectionSupportInitializerClass(entries, outputDirectory)
        val currentInternalName = writeProjectionSupportInitializerClass(emptyList(), outputDirectory)

        assertNotNull(staleInternalName)
        assertNull(currentInternalName)
        assertFalse(Files.exists(outputDirectory.resolve("$staleInternalName.class")))
        assertFalse(Files.exists(outputDirectory.resolve("${staleInternalName}_Chunk000.class")))
    }

    @Test
    fun projection_support_initializer_input_rejects_malformed_rows() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-malformed-", ".tsv")
        Files.writeString(
            input,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            java.lang.String	Sample.Foundation.Widget	RuntimeClass
            """.trimIndent() + "\n",
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse projection registrar input row 2"),
        )
    }

    @Test
    fun projection_support_initializer_input_rejects_extra_columns() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-extra-column-", ".tsv")
        Files.writeString(
            input,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            java.lang.String	Sample.Foundation.Widget	RuntimeClass	Sample.Foundation.WidgetBase	Sample.Foundation.Widget.Metadata	11111111-2222-3333-4444-555555555555	extra
            """.trimIndent() + "\n",
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse projection registrar input row 2"),
        )
    }

    @Test
    fun projection_support_initializer_input_rejects_unexpected_headers() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-header-", ".tsv")
        Files.writeString(
            input,
            """
            projectedTypeName	kotlinClassName	kind	baseTypeName	metadataClassName	interfaceIid
            Sample.Foundation.Widget	java.lang.String	RuntimeClass	Sample.Foundation.WidgetBase
            """.trimIndent() + "\n",
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin expected projection registrar input header"),
        )
    }

    @Test
    fun projection_support_initializer_input_rejects_blank_required_columns() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-blank-columns-", ".tsv")
        Files.writeString(
            input,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            	Sample.Foundation.Widget	RuntimeClass	Sample.Foundation.WidgetBase	
            """.trimIndent() + "\n",
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse projection registrar input row 2"),
        )
    }

    @Test
    fun projection_support_initializer_input_rejects_unknown_kinds() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-unknown-kind-", ".tsv")
        Files.writeString(
            input,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            java.lang.String	Sample.Foundation.Widget	Unknown	Sample.Foundation.WidgetBase	
            """.trimIndent() + "\n",
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse projection registrar input row 2"),
        )
    }

    @Test
    fun projection_support_initializer_input_rejects_duplicate_entries() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-duplicate-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName", "interfaceIid"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", "", ""),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readProjectionRegistrarEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate projection registrar input"),
        )
    }

    @Test
    fun projection_support_initializer_rejects_unresolvable_kotlin_class() {
        val entries = listOf(
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "sample.MissingWidget",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
                interfaceIid = "",
            ),
        )

        val error = runCatching {
            resolveProjectionRegistrarClasses(entries) { null }
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertEquals(
            "kotlin-winrt compiler plugin requires projection registrar input for Sample.Foundation.Widget " +
                "to reference resolvable Kotlin class sample.MissingWidget.",
            error!!.message,
        )
    }

    @Test
    fun projection_support_initializer_resolves_classes_in_stable_order() {
        val entries = listOf(
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "sample.WidgetB",
                projectedTypeName = "Sample.Foundation.WidgetB",
                kind = "RuntimeClass",
                baseTypeName = "",
                metadataClassName = "",
                interfaceIid = "",
            ),
            KotlinWinRTProjectionRegistrarEntry(
                kotlinClassName = "sample.WidgetA",
                projectedTypeName = "Sample.Foundation.WidgetA",
                kind = "RuntimeClass",
                baseTypeName = "",
                metadataClassName = "",
                interfaceIid = "",
            ),
        )

        val resolved = resolveProjectionRegistrarClasses(entries) { className -> className.substringAfterLast('.') }

        assertEquals(
            listOf("sample.WidgetA", "sample.WidgetB"),
            resolved.map { (entry, _) -> entry.kotlinClassName },
        )
        assertEquals(listOf("WidgetA", "WidgetB"), resolved.map { (_, klass) -> klass })
    }

    @Test
    fun compiler_support_prerequisite_rejects_missing_helper_symbol() {
        val error = runCatching {
            requireCompilerSupportPrerequisite<String>(
                description = "authoring type-details registrar",
                prerequisite = "class io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_sample",
                value = null,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertEquals(
            "kotlin-winrt compiler plugin requires authoring type-details registrar support input to resolve " +
                "class io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_sample.",
            error!!.message,
        )
    }

    @Test
    fun compiler_support_prerequisite_returns_resolved_helper_symbol() {
        assertEquals(
            "resolved",
            requireCompilerSupportPrerequisite(
                description = "projection registrar",
                prerequisite = "registerGeneratedProjectionTypeIndex function",
                value = "resolved",
            ),
        )
    }

    @Test
    fun projection_registrar_prerequisite_rejects_missing_registration_function() {
        val error = runCatching {
            requireCompilerSupportPrerequisite<String>(
                description = "projection registrar",
                prerequisite = "io.github.composefluent.winrt.runtime.registerGeneratedProjectionTypeIndex with 5 regular parameters",
                value = null,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertEquals(
            "kotlin-winrt compiler plugin requires projection registrar support input to resolve " +
                "io.github.composefluent.winrt.runtime.registerGeneratedProjectionTypeIndex with 5 regular parameters.",
            error!!.message,
        )
    }

    @Test
    fun authoring_registrar_prerequisite_rejects_missing_registration_function() {
        val error = runCatching {
            requireCompilerSupportPrerequisite<String>(
                description = "authoring type-details registrar",
                prerequisite = "WinRTAuthoringTypeDetailsRegistrar.register with no regular parameters",
                value = null,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertEquals(
            "kotlin-winrt compiler plugin requires authoring type-details registrar support input to resolve " +
                "WinRTAuthoringTypeDetailsRegistrar.register with no regular parameters.",
            error!!.message,
        )
    }

    @Test
    fun compiler_support_manifest_rejects_missing_declared_source_file() {
        val manifestDirectory = Files.createTempDirectory("kotlin-winrt-compiler-support-missing-source-")
        val manifest = manifestDirectory.resolve("compiler-support.tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1
            """.trimIndent() + "\n",
        )
        val manifestEntries = readCompilerSupportManifest(manifest)

        val error = runCatching {
            readCompilerSupportInputEntries(
                manifestPath = manifest,
                manifestEntries = manifestEntries,
                kind = "projection-registrar",
                description = "projection registrar input",
                read = ::readProjectionRegistrarEntries,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin requires projection registrar input file"),
        )
    }

    @Test
    fun compiler_support_manifest_without_parent_resolves_declared_source_from_current_directory() {
        val manifest = Path.of("compiler-support.tsv")
        val manifestEntries = listOf(
            KotlinWinRTCompilerSupportManifestEntry(
                kind = "projection-registrar",
                className = "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                sourceFile = "missing-projection-registrar.tsv",
                entries = 1,
            ),
        )

        val error = runCatching {
            readCompilerSupportInputEntries(
                manifestPath = manifest,
                manifestEntries = manifestEntries,
                kind = "projection-registrar",
                description = "projection registrar input",
                read = ::readProjectionRegistrarEntries,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("missing-projection-registrar.tsv"),
        )
    }

    @Test
    fun compiler_support_manifest_rejects_declared_entry_count_mismatches() {
        val manifestDirectory = Files.createTempDirectory("kotlin-winrt-compiler-support-count-mismatch-")
        val manifest = manifestDirectory.resolve("compiler-support.tsv")
        val projectionRegistrar = manifestDirectory.resolve("projection-registrar.tsv")
        Files.writeString(
            projectionRegistrar,
            listOf(
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName", "interfaceIid"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )
        val manifestEntries = listOf(
            KotlinWinRTCompilerSupportManifestEntry(
                kind = "projection-registrar",
                className = "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
                sourceFile = "projection-registrar.tsv",
                entries = 2,
            ),
        )

        val error = runCatching {
            readCompilerSupportInputEntries(
                manifestPath = manifest,
                manifestEntries = manifestEntries,
                kind = "projection-registrar",
                description = "projection registrar input",
                read = ::readProjectionRegistrarEntries,
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("expected 2 projection registrar input entries"),
        )
    }

    @Test
    fun projection_registrar_reads_guid_signatures_and_legacy_rows() {
        val root = Files.createTempDirectory("kotlin-winrt-projection-registrar-guid-signature-")
        val current = root.resolve("current.tsv")
        Files.writeString(
            current,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid	guidSignature
            sample.foundation.IWidget	Sample.Foundation.IWidget	Interface			11111111-2222-3333-4444-555555555555	{11111111-2222-3333-4444-555555555555}
            sample.foundation.Widget	Sample.Foundation.Widget	RuntimeClass		sample.foundation.Widget.Metadata		rc(Sample.Foundation.Widget;{11111111-2222-3333-4444-555555555555})
            """.trimIndent() + "\n",
        )
        val legacy = root.resolve("legacy.tsv")
        Files.writeString(
            legacy,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            sample.foundation.IWidget	Sample.Foundation.IWidget	Interface			11111111-2222-3333-4444-555555555555
            """.trimIndent() + "\n",
        )

        val currentEntries = readProjectionRegistrarEntries(current)
        val legacyEntries = readProjectionRegistrarEntries(legacy)

        assertEquals(2, currentEntries.size)
        assertEquals("{11111111-2222-3333-4444-555555555555}", currentEntries[0].guidSignature)
        assertEquals(
            "rc(Sample.Foundation.Widget;{11111111-2222-3333-4444-555555555555})",
            currentEntries[1].guidSignature,
        )
        assertEquals("", legacyEntries.single().guidSignature)
    }

}
