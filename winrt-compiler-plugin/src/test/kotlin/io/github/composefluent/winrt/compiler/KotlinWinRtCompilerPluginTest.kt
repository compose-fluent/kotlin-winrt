package io.github.composefluent.winrt.compiler

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCompilerPluginTest {
    @Test
    fun command_line_processor_stores_metadata_index_option() {
        val configuration = CompilerConfiguration()
        val processor = KotlinWinRtCommandLineProcessor()
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
            configuration.get(KotlinWinRtCommandLineProcessor.METADATA_INDEX_KEY),
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
            configuration.get(KotlinWinRtCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
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
            configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
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
            configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
        )
    }

    @Test
    fun registrar_installs_ir_generation_extension_for_k2_path() {
        val storage = CompilerPluginRegistrar.ExtensionStorage()
        val registrar = KotlinWinRtCompilerPluginRegistrar()

        with(registrar) {
            storage.registerExtensions(CompilerConfiguration())
        }

        assertTrue(registrar.supportsK2)
        assertTrue(storage.registeredExtensions[IrGenerationExtension].orEmpty().any { extension ->
            extension is KotlinWinRtIrGenerationExtension
        })
    }

    @Test
    fun projection_type_index_records_map_kotlin_projection_names_to_winrt_metadata() {
        val record = projectionTypeIndexRecordForSourceType(
            sourceTypeName = "microsoft.ui.xaml.Window",
            winRtTypes = mapOf(
                "Microsoft.UI.Xaml.Window" to IndexedWinRtType(
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
        assertFalse(isGeneratedSourceFile("C:\\repo\\src\\main\\kotlin\\sample\\App.kt", root))
    }

    @Test
    fun projection_intrinsic_matching_requires_intrinsic_owner() {
        assertTrue(
            isProjectionIntrinsicFunction(
                "getString",
                "io.github.composefluent.winrt.runtime.WinRtProjectionIntrinsic",
            ),
        )
        assertFalse(
            isProjectionIntrinsicFunction(
                "getString",
                "windows.data.json.IJsonValue",
            ),
        )
    }

    @Test
    fun compiler_support_manifest_records_generated_support_tables() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	12
            """.trimIndent() + "\n",
        )

        val entries = readCompilerSupportManifest(manifest)

        assertEquals(1, entries.size)
        assertEquals("projection-registrar", entries[0].kind)
        assertEquals("io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic", entries[0].className)
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
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	12	projection-registrar.tsv
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
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	not-a-number
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
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	-1
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
            	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	1
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
            unsupported-kind	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	1
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
    fun compiler_support_manifest_rejects_mismatched_source_files() {
        val manifest = Files.createTempFile("kotlin-winrt-compiler-support-mismatched-source-", ".tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	generic-instantiations.tsv	1
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
                    "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
                    "projection-registrar.tsv",
                    "1",
                ),
                listOf(
                    "projection-registrar",
                    "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
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
        writeCompilerSupportManifestClass(
            entries = listOf(
                KotlinWinRtCompilerSupportManifestEntry(
                    kind = "projection-registrar",
                    className = "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
                    sourceFile = "projection-registrar.tsv",
                    entries = 12,
                ),
                KotlinWinRtCompilerSupportManifestEntry(
                    kind = "generic-type-instantiation",
                    className = "io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations",
                    sourceFile = "generic-instantiations.tsv",
                    entries = 5,
                ),
                KotlinWinRtCompilerSupportManifestEntry(
                    kind = "generic-abi-registry",
                    className = "io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic",
                    sourceFile = "generic-abi-registry.tsv",
                    entries = 4,
                ),
            ),
            outputDirectory = outputDirectory,
        )

        URLClassLoader(arrayOf(outputDirectory.toUri().toURL()), null).use { classLoader ->
            val klass = Class.forName(
                "io.github.composefluent.winrt.projections.support.WinRTCompilerSupportManifest",
                false,
                classLoader,
            )
            assertEquals(3, klass.getField("ENTRY_COUNT").getInt(null))
            assertEquals(12, klass.getField("PROJECTION_REGISTRAR_ENTRIES").getInt(null))
            assertEquals(5, klass.getField("GENERIC_TYPE_INSTANTIATION_ENTRIES").getInt(null))
            assertEquals(4, klass.getField("GENERIC_ABI_REGISTRY_ENTRIES").getInt(null))
        }
    }

    @Test
    fun projection_support_initializer_input_writes_content_addressed_class_artifact() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", ""),
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
        assertTrue(methodNames.contains("registerChunk000"))
    }

    @Test
    fun projection_support_initializer_input_deletes_stale_content_addressed_class_artifacts() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-stale-class-")
        val firstEntries = listOf(
            KotlinWinRtProjectionRegistrarEntry(
                kotlinClassName = "java.lang.String",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
            ),
        )
        val secondEntries = firstEntries + KotlinWinRtProjectionRegistrarEntry(
            kotlinClassName = "java.lang.Integer",
            projectedTypeName = "Sample.Foundation.Gadget",
            kind = "RuntimeClass",
            baseTypeName = "Sample.Foundation.GadgetBase",
            metadataClassName = "",
        )

        val staleInternalName = writeProjectionSupportInitializerClass(firstEntries, outputDirectory)
        val currentInternalName = writeProjectionSupportInitializerClass(secondEntries, outputDirectory)

        assertNotNull(staleInternalName)
        assertNotNull(currentInternalName)
        assertFalse(Files.exists(outputDirectory.resolve("$staleInternalName.class")))
        assertTrue(Files.isRegularFile(outputDirectory.resolve("$currentInternalName.class")))
    }

    @Test
    fun projection_support_initializer_input_deletes_stale_class_artifacts_when_entries_are_empty() {
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-projection-support-empty-stale-class-")
        val entries = listOf(
            KotlinWinRtProjectionRegistrarEntry(
                kotlinClassName = "java.lang.String",
                projectedTypeName = "Sample.Foundation.Widget",
                kind = "RuntimeClass",
                baseTypeName = "Sample.Foundation.WidgetBase",
                metadataClassName = "",
            ),
        )

        val staleInternalName = writeProjectionSupportInitializerClass(entries, outputDirectory)
        val currentInternalName = writeProjectionSupportInitializerClass(emptyList(), outputDirectory)

        assertNotNull(staleInternalName)
        assertNull(currentInternalName)
        assertFalse(Files.exists(outputDirectory.resolve("$staleInternalName.class")))
    }

    @Test
    fun projection_support_initializer_input_rejects_malformed_rows() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-malformed-", ".tsv")
        Files.writeString(
            input,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName
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
    fun projection_support_initializer_input_rejects_unexpected_headers() {
        val input = Files.createTempFile("kotlin-winrt-projection-support-header-", ".tsv")
        Files.writeString(
            input,
            """
            projectedTypeName	kotlinClassName	kind	baseTypeName	metadataClassName
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
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName
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
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName
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
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", ""),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", ""),
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
    fun compiler_support_manifest_rejects_missing_declared_source_file() {
        val manifestDirectory = Files.createTempDirectory("kotlin-winrt-compiler-support-missing-source-")
        val manifest = manifestDirectory.resolve("compiler-support.tsv")
        Files.writeString(
            manifest,
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	1
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
            KotlinWinRtCompilerSupportManifestEntry(
                kind = "projection-registrar",
                className = "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
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
                listOf("kotlinClassName", "projectedTypeName", "kind", "baseTypeName", "metadataClassName"),
                listOf("java.lang.String", "Sample.Foundation.Widget", "RuntimeClass", "Sample.Foundation.WidgetBase", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )
        val manifestEntries = listOf(
            KotlinWinRtCompilerSupportManifestEntry(
                kind = "projection-registrar",
                className = "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
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
    fun generic_abi_registry_input_reads_compile_time_facts() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf("derived-interface", "Windows.Foundation.Collections.IVector", "", "", "", "", ""),
                listOf(
                    "delegate",
                    "_get_Value_Int",
                    "Windows.Foundation.IReference<Int>",
                    "get_Value",
                    "internal unsafe delegate int _get_Value_Int(void*, out int);",
                    "void*\u001Fout int\u001Fint",
                    "void*\u001Fint.MakeByRefType()\u001Fint",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val entries = readGenericAbiRegistryEntries(input)

        assertEquals(2, entries.size)
        assertEquals("derived-interface", entries[0].kind)
        assertEquals("Windows.Foundation.Collections.IVector", entries[0].name)
        assertEquals("delegate", entries[1].kind)
        assertEquals("_get_Value_Int", entries[1].name)
        assertEquals("Windows.Foundation.IReference<Int>", entries[1].sourceGenericType)
        assertEquals(listOf("void*", "out int", "int"), entries[1].abiParameterTypes)
        assertEquals(listOf("void*", "int.MakeByRefType()", "int"), entries[1].typeArrayShape)
    }

    @Test
    fun generic_type_instantiation_input_rejects_malformed_rows() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-malformed-", ".tsv")
        Files.writeString(
            input,
            """
            className	sourceType	isDelegate	rcwFunctions	vtableFunctions	propertyAccessors	genericReturnOnlyRcwFunctions	projectedGenericFallbacks	dependencies
            Windows_Foundation_IReference_Int	Windows.Foundation.IReference<Int>	false
            """.trimIndent() + "\n",
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic type instantiation input row 2"),
        )
    }

    @Test
    fun generic_type_instantiation_input_rejects_unexpected_headers() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-header-", ".tsv")
        Files.writeString(
            input,
            """
            sourceType	className	isDelegate	rcwFunctions	vtableFunctions	propertyAccessors	genericReturnOnlyRcwFunctions	projectedGenericFallbacks	dependencies
            Windows.Foundation.IReference<Int>	Windows_Foundation_IReference_Int	false
            """.trimIndent() + "\n",
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin expected generic type instantiation input header"),
        )
    }

    @Test
    fun generic_type_instantiation_input_rejects_malformed_delegate_flags() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-bool-malformed-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf(
                    "className",
                    "sourceType",
                    "isDelegate",
                    "rcwFunctions",
                    "vtableFunctions",
                    "propertyAccessors",
                    "genericReturnOnlyRcwFunctions",
                    "projectedGenericFallbacks",
                    "dependencies",
                ),
                listOf(
                    "Windows_Foundation_IReference_Int",
                    "Windows.Foundation.IReference<Int>",
                    "not-a-boolean",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic type instantiation input row 2"),
        )
    }

    @Test
    fun generic_type_instantiation_input_rejects_blank_required_columns() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-blank-columns-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf(
                    "className",
                    "sourceType",
                    "isDelegate",
                    "rcwFunctions",
                    "vtableFunctions",
                    "propertyAccessors",
                    "genericReturnOnlyRcwFunctions",
                    "projectedGenericFallbacks",
                    "dependencies",
                ),
                listOf(
                    "",
                    "Windows.Foundation.IReference<Int>",
                    "false",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic type instantiation input row 2"),
        )
    }

    @Test
    fun generic_type_instantiation_input_rejects_blank_list_elements() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-blank-list-element-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf(
                    "className",
                    "sourceType",
                    "isDelegate",
                    "rcwFunctions",
                    "vtableFunctions",
                    "propertyAccessors",
                    "genericReturnOnlyRcwFunctions",
                    "projectedGenericFallbacks",
                    "dependencies",
                ),
                listOf(
                    "Windows_Foundation_IReference_Int",
                    "Windows.Foundation.IReference<Int>",
                    "false",
                    "Create,,CreateFallback",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic type instantiation input row 2"),
        )
    }

    @Test
    fun generic_type_instantiation_input_rejects_duplicate_entries() {
        val input = Files.createTempFile("kotlin-winrt-generic-instantiation-duplicate-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf(
                    "className",
                    "sourceType",
                    "isDelegate",
                    "rcwFunctions",
                    "vtableFunctions",
                    "propertyAccessors",
                    "genericReturnOnlyRcwFunctions",
                    "projectedGenericFallbacks",
                    "dependencies",
                ),
                listOf(
                    "Windows_Foundation_IReference_Int",
                    "Windows.Foundation.IReference<Int>",
                    "false",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
                listOf(
                    "Windows_Foundation_IReference_Int",
                    "Windows.Foundation.IReference<Int>",
                    "false",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericTypeInstantiationEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate generic type instantiation input"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_malformed_rows() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-malformed-", ".tsv")
        Files.writeString(
            input,
            """
            kind	name	sourceGenericType	operation	declaration	abiParameterTypes	typeArrayShape
            delegate	_get_Value_Int	Windows.Foundation.IReference<Int>
            """.trimIndent() + "\n",
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic ABI registry input row 2"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_unexpected_headers() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-header-", ".tsv")
        Files.writeString(
            input,
            """
            kind	name	sourceGenericType	declaration	operation	abiParameterTypes	typeArrayShape
            derived-interface	Windows.Foundation.Collections.IVector
            """.trimIndent() + "\n",
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin expected generic ABI registry input header"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_unknown_kinds() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-unknown-kind-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf("unsupported-kind", "Windows.Foundation.Collections.IVector", "", "", "", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic ABI registry input row 2"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_blank_required_columns() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-blank-columns-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf("delegate", "_get_Value_Int", "", "get_Value", "internal unsafe delegate int _get_Value_Int(void*, out int);", "void*\u001Fout int\u001Fint", "void*\u001Fint.MakeByRefType()\u001Fint"),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic ABI registry input row 2"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_blank_list_elements() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-blank-list-element-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf(
                    "delegate",
                    "_get_Value_Int",
                    "Windows.Foundation.IReference<Int>",
                    "get_Value",
                    "internal unsafe delegate int _get_Value_Int(void*, out int);",
                    "void*\u001F\u001Fint",
                    "void*\u001Fint.MakeByRefType()\u001Fint",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("kotlin-winrt compiler plugin could not parse generic ABI registry input row 2"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_duplicate_derived_interfaces() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-duplicate-derived-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf("derived-interface", "Windows.Foundation.Collections.IVector", "", "", "", "", ""),
                listOf("derived-interface", "Windows.Foundation.Collections.IVector", "", "", "", "", ""),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate generic ABI registry input"),
        )
    }

    @Test
    fun generic_abi_registry_input_rejects_duplicate_delegates() {
        val input = Files.createTempFile("kotlin-winrt-generic-abi-registry-duplicate-delegate-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("kind", "name", "sourceGenericType", "operation", "declaration", "abiParameterTypes", "typeArrayShape"),
                listOf("delegate", "_get_Value_Int", "Windows.Foundation.IReference<Int>", "get_Value", "internal unsafe delegate int _get_Value_Int(void*, out int);", "void*\u001Fout int\u001Fint", "void*\u001Fint.MakeByRefType()\u001Fint"),
                listOf("delegate", "_get_Value_Int", "Windows.Foundation.IReference<Int>", "get_Value", "internal unsafe delegate int _get_Value_Int(void*, out int);", "void*\u001Fout int\u001Fint", "void*\u001Fint.MakeByRefType()\u001Fint"),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val error = runCatching { readGenericAbiRegistryEntries(input) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("duplicate generic ABI registry input"),
        )
    }

}
