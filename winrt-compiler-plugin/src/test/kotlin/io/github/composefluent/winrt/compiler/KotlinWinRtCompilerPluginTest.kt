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
            event-source	io.github.composefluent.winrt.projections.support.WinRTEventProjectionHelpers	event-sources.tsv	3
            """.trimIndent() + "\n",
        )

        val entries = readCompilerSupportManifest(manifest)

        assertEquals(2, entries.size)
        assertEquals("projection-registrar", entries[0].kind)
        assertEquals("io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic", entries[0].className)
        assertEquals("projection-registrar.tsv", entries[0].sourceFile)
        assertEquals(12, entries[0].entries)
        assertEquals("event-source", entries[1].kind)
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
                    kind = "event-source",
                    className = "io.github.composefluent.winrt.projections.support.WinRTEventProjectionHelpers",
                    sourceFile = "event-sources.tsv",
                    entries = 3,
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
            assertEquals(4, klass.getField("ENTRY_COUNT").getInt(null))
            assertEquals(12, klass.getField("PROJECTION_REGISTRAR_ENTRIES").getInt(null))
            assertEquals(3, klass.getField("EVENT_SOURCE_ENTRIES").getInt(null))
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
    fun event_projection_input_writes_registry_class_artifact() {
        val input = Files.createTempFile("kotlin-winrt-event-sources-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("eventType", "ownerType", "sourceClass", "abiEventType", "genericArguments", "usesSharedEventHandlerSource"),
                listOf("Windows.Foundation.EventHandler<Int>", "Sample.Foundation.IWidget", "EventHandlerEventSource", "Windows.Foundation.EventHandler`1", "Int", "true"),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )
        val outputDirectory = Files.createTempDirectory("kotlin-winrt-event-registry-class-")

        val entries = readEventProjectionEntries(input)
        writeEventProjectionRegistryClass(entries, outputDirectory)

        URLClassLoader(arrayOf(outputDirectory.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val klass = Class.forName(
                "io.github.composefluent.winrt.projections.support.WinRTEventProjectionRegistry",
                false,
                classLoader,
            )
            assertEquals("register", klass.getDeclaredMethod("register").name)
        }
        val registryClass = ClassReader(
            Files.readAllBytes(
                outputDirectory.resolve(
                    "io/github/composefluent/winrt/projections/support/WinRTEventProjectionRegistry.class",
                ),
            ),
        )
        val eventSourceRuntimeCalls = mutableListOf<Int>()
        registryClass.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String?,
                            name: String?,
                            descriptor: String?,
                            isInterface: Boolean,
                        ) {
                            if (
                                owner == "io/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime" &&
                                name == "createEventSourceFactory"
                            ) {
                                eventSourceRuntimeCalls.add(opcode)
                            }
                        }
                    }
            },
            0,
        )
        assertEquals(listOf(Opcodes.INVOKEVIRTUAL), eventSourceRuntimeCalls)
        val resource = outputDirectory.resolve("kotlin-winrt/event-sources.tsv")
        assertFalse(Files.exists(resource))
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
    fun interface_native_projection_input_is_compiler_plugin_lowering_plan() {
        val input = Files.createTempFile("kotlin-winrt-interface-native-projections-", ".tsv")
        Files.writeString(
            input,
            listOf(
                listOf("projectedTypeName", "kotlinInterfaceClassName", "implementationClassName", "interfaceId", "memberCount", "members"),
                listOf(
                    "Sample.Foundation.IWidget",
                    "io.github.composefluent.winrt.compiler.CompilerGeneratedSampleInterface",
                    "io.github.composefluent.winrt.compiler.CompilerGeneratedSampleInterfaceNativeProjection",
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "1",
                    "Method|Ping|6|Unit||false",
                ),
            ).joinToString(separator = "\n", postfix = "\n") { row -> row.joinToString("\t") },
        )

        val entries = readInterfaceNativeProjectionEntries(input)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("Sample.Foundation.IWidget", entry.projectedTypeName)
        assertEquals("io.github.composefluent.winrt.compiler.CompilerGeneratedSampleInterface", entry.kotlinInterfaceClassName)
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", entry.interfaceId)
        assertEquals(1, entry.members.size)
        assertEquals("Ping", entry.members.single().jvmName)
    }
}

internal interface CompilerGeneratedSampleInterface
