package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.net.URLClassLoader
import io.github.composefluent.winrt.runtime.WinUIXamlLoadState

class XamlSemanticExportTest {
    private fun compilerRuntimeClasspath(): String = listOf(
        Unit::class.java, WinUIXamlLoadState::class.java, io.github.composefluent.winrt.runtime.Guid::class.java,
    ).map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
        .distinct().joinToString(File.pathSeparator)

    @Test
    fun semantic_only_compilation_exports_symbols_without_business_class_files() {
        val root = Files.createTempDirectory("xaml-semantic-no-codegen").toFile()
        try {
            val declarations = File(root, "declarations.json").apply {
                writeText(WinUIXamlDeclarations.canonicalText(WinUIXamlDeclarationIndex(
                    WinUIXamlDeclarations.SCHEMA_VERSION, emptyList(), emptyList())))
            }
            val index = File(root, "metadata.tsv").apply { writeText("") }
            val references = File(root, "references.txt").apply { writeText("") }
            val source = File(root, "Business.kt").apply {
                writeText("class Business { fun values() = listOf(1, 2).map { it * 2 } }")
            }
            val output = File(root, "classes")
            val symbols = File(root, "symbols.json")
            val options = mapOf("xamlDeclarations" to declarations.absolutePath, "metadataIndex" to index.absolutePath,
                "xamlSemanticOutput" to symbols.absolutePath, "xamlReferencesFile" to references.absolutePath)
            val args = listOf("-no-stdlib", "-no-reflect", "-classpath",
                File(Unit::class.java.protectionDomain.codeSource.location.toURI()).absolutePath,
                "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}", "-d", output.absolutePath,
                source.absolutePath) + options.flatMap { (key, value) ->
                listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value")
            }
            val diagnostics = ByteArrayOutputStream()
            val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *args.toTypedArray()) }
            assertEquals(diagnostics.toString(), ExitCode.OK, result)
            assertTrue(symbols.isFile)
            assertTrue(File(root, "KotlinXaml.winmd").isFile)
            assertFalse(output.walkTopDown().any { it.extension == "class" })
            // Combined mode exports the same inferred schema while retaining executable IR.
            // No second Kotlin frontend invocation is needed for the business classes.
            val combined = args.toMutableList().apply {
                // Combined mode executes the full plugin, so its ABI check needs both runtime layers.
                this[indexOf("-classpath") + 1] = compilerRuntimeClasspath()
            } + listOf("-P", "plugin:io.github.composefluent.winrt.compiler:xamlCompile=true")
            val compiled = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *combined.toTypedArray()) }
            assertEquals(diagnostics.toString(), ExitCode.OK, compiled)
            URLClassLoader(arrayOf(output.toURI().toURL()), javaClass.classLoader).use { loader ->
                val business = loader.loadClass("Business")
                assertEquals(listOf(2, 4), business.getMethod("values").invoke(business.getConstructor().newInstance()))
            }
            source.writeText("class Business { fun values(): Int = \"wrong type\" }")
            val failure = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *args.toTypedArray()) }
            assertEquals(diagnostics.toString(), ExitCode.COMPILATION_ERROR, failure)
            assertFalse(symbols.exists())
            assertFalse(File(root, "KotlinXaml.winmd").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun semantic_pass_reports_nullable_delegate_mismatch_as_a_source_error() {
        val root = Files.createTempDirectory("xaml-handler-nullability").toFile()
        try {
            val location = WinUIXamlSourceLocation(3, 9)
            val declarations = WinUIXamlDeclarationIndex(WinUIXamlDeclarations.SCHEMA_VERSION, listOf(
                WinUIXamlPageDeclaration("probe.MainPage", "MainPage.xaml", "Microsoft.UI.Xaml.Controls.Page",
                    false, listOf("events"), listOf(WinUIXamlConnectionDeclaration(1,
                        "Microsoft.UI.Xaml.Controls.Page", null, location, listOf(WinUIXamlEventDeclaration(
                            "Changed", "onChanged", "Microsoft.UI.Xaml.Controls.Page", "Microsoft.UI.Xaml.Handler", location)))))
            ), emptyList())
            val input = File(root, "declarations.json").apply { writeText(WinUIXamlDeclarations.canonicalText(declarations)) }
            val index = File(root, "metadata.tsv").apply { writeText("") }
            val references = File(root, "references.txt").apply { writeText("") }
            val symbols = File(root, "symbols.json").apply { writeText("stale") }
            File(root, "MainPage.xaml").writeText("<Page />")
            File(root, "MainPage.kt").writeText("""
                package probe
                class MainPage : microsoft.ui.xaml.controls.Page() {
                    private fun onChanged(sender: Any, args: Any) { }
                }
            """.trimIndent())
            File(root, "UI.kt").writeText("""
                package microsoft.ui.xaml.controls
                fun interface Handler<T> { fun invoke(sender: Any?, args: T) }
                open class Page { fun addChanged(handler: Handler<Any?>?) { } }
            """.trimIndent())
            File(root, "Connector.kt").writeText("""
                package microsoft.ui.xaml.markup
                interface IComponentConnector {
                    fun connect(connectionId: Int, target: Any?)
                    fun getBindingConnector(connectionId: Int, target: Any?): IComponentConnector?
                }
            """.trimIndent())
            val classpath = compilerRuntimeClasspath()
            val options = mapOf("xamlDeclarations" to input.absolutePath, "metadataIndex" to index.absolutePath,
                "xamlSemanticOutput" to symbols.absolutePath, "xamlReferencesFile" to references.absolutePath)
            val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}", "-d", File(root, "output").absolutePath) +
                root.listFiles()!!.filter { it.extension == "kt" }.map { it.absolutePath } +
                options.flatMap { (key, value) -> listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value") }
            val diagnostics = ByteArrayOutputStream()
            val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
            assertEquals(diagnostics.toString(), ExitCode.COMPILATION_ERROR, result)
            assertTrue(diagnostics.toString(), diagnostics.toString().contains("onChanged does not match projected delegate"))
            assertTrue(diagnostics.toString(), diagnostics.toString().contains("nullable arguments"))
            assertTrue(diagnostics.toString(), diagnostics.toString().contains("MainPage.xaml:3:9"))
            assertFalse(symbols.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun development_accessors_use_the_system_type_projection_for_style_target_type() {
        // CsWinRT helpers.h maps Windows.UI.Xaml.Interop.TypeName to System.Type;
        // KotlinProjectionTypeResolver emits KClass<*>?, not a TypeName struct.
        val model = WinRTMetadataModel(emptyList())
        val resolver = model.specialTypeResolver()
        listOf("Windows.UI.Xaml.Interop.TypeName", "System.Type").forEach { name ->
            assertEquals("kotlin.reflect.KClass", xamlTypeClassId(name).asSingleFqName().asString())
            assertEquals("kotlin.reflect.KClass<*>?", xamlProjectedPropertySourceType(WinRTTypeRef.named(name), resolver, model.semanticHelpers()))
        }
        assertEquals("kotlin.collections.MutableList<kotlin.reflect.KClass<*>?>?", xamlProjectedPropertySourceType(
            WinRTTypeRef.fromDisplayName("Windows.Foundation.Collections.IVector<Windows.UI.Xaml.Interop.TypeName>"), resolver, model.semanticHelpers()))
    }

    @Test
    fun sdk_non_content_vectors_emit_collection_shape_for_both_frameworks() {
        for (framework in listOf(io.github.composefluent.winrt.runtime.XamlFramework.WinUI,
            io.github.composefluent.winrt.runtime.XamlFramework.UWP)) {
            val ns = framework.xamlNamespace + ".Controls"
            val rows = WinRTTypeDefinition(ns, "Rows", WinRTTypeKind.RuntimeClass,
                implementedInterfaces = listOf(WinRTInterfaceImplementationDefinition(
                    "Windows.Foundation.Collections.IVector<$ns.Row>")))
            val contract = WinRTTypeDefinition(ns, "ILayout", WinRTTypeKind.Interface,
                methods = listOf(WinRTMethodDefinition("get_RowDefinitions", "$ns.Rows")),
                properties = listOf(WinRTPropertyDefinition("RowDefinitions", "$ns.Rows", getterMethodName = "get_RowDefinitions")))
            val layout = WinRTTypeDefinition(ns, "Layout", WinRTTypeKind.RuntimeClass,
                defaultInterfaceName = contract.qualifiedName,
                implementedInterfaces = listOf(WinRTInterfaceImplementationDefinition(contract.qualifiedName, isDefault = true)))
            val model = WinRTMetadataModel(listOf(WinRTNamespace(ns, listOf(rows, contract, layout))))
            val root = Files.createTempDirectory("xaml-vector-accessors").toFile()
            try {
                assertNotNull(writeXamlProjectedTypeRegistrationSource(root.toPath(), model,
                    setOf(layout.qualifiedName), "Vectors", framework))
                val source = root.walkTopDown().single { it.extension == "kt" }.readText()
                val member = source.substringAfter("name = \"RowDefinitions\"").substringBefore("    ),")
                assertTrue(source, member.contains("isCollection = true"))
            } finally { root.deleteRecursively() }
        }
    }

    @Test
    fun projected_attached_accessors_use_typed_static_calls_and_distinct_member_identity() {
        // CSharpTypeInfoPass2 attached getters/setters remain compiler-generated;
        // runtime lookup must not confuse ScrollViewer's same-named instance member.
        val ns = "Microsoft.UI.Xaml.Controls"
        val target = WinRTParameterDefinition("target", "Microsoft.UI.Xaml.DependencyObject")
        val type = WinRTTypeDefinition(ns, "Layout", WinRTTypeKind.RuntimeClass, baseTypeName = "System.Object",
            properties = listOf(WinRTPropertyDefinition("RowProperty", "Microsoft.UI.Xaml.DependencyProperty", isStatic = true)),
            methods = listOf(
                WinRTMethodDefinition("GetRow", "Int32", listOf(target), isStatic = true),
                WinRTMethodDefinition("SetRow", "Void", listOf(target, WinRTParameterDefinition("value", "Int32")), isStatic = true)))
        val model = WinRTMetadataModel(listOf(WinRTNamespace(ns, listOf(type))))
        val root = Files.createTempDirectory("xaml-attached-accessors").toFile()
        try {
            assertNotNull(writeXamlProjectedTypeRegistrationSource(root.toPath(), model, setOf(type.qualifiedName), "Attached"))
            val source = root.walkTopDown().single { it.extension == "kt" }.readText()
            assertTrue(source, source.contains("name = \"Microsoft.UI.Xaml.Controls.Layout.Row\""))
            assertTrue(source, source.contains("`getRow`"))
            assertTrue(source, source.contains("`setRow`"))
            assertTrue(source, source.contains("isAttachable = true"))
            assertTrue(source, source.contains("target.readLocalValue(property)"))
            assertTrue(source, source.contains("target.clearValue(property)"))
            assertTrue(source, source.contains("Layout.`rowProperty`"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun xaml_sdk_rcw_metadata_is_available_without_hot_reload() {
        // Runtime2 WindowsRuntimeComWrappers.CreateObject uses the external type map
        // even for objects created by XAML rather than projected Kotlin constructors.
        listOf(io.github.composefluent.winrt.runtime.XamlFramework.WinUI,
            io.github.composefluent.winrt.runtime.XamlFramework.UWP).forEach { framework ->
            val namespace = framework.xamlNamespace
            val types = listOf(
                WinRTTypeDefinition(namespace, "UIElement", WinRTTypeKind.RuntimeClass,
                    baseTypeName = "System.Object", customAttributes = listOf(
                        WinRTCustomAttributeDefinition(framework.xamlTypeName("Markup.ContentPropertyAttribute"),
                            namedArguments = listOf(WinRTCustomAttributeNamedArgument("Name",
                                WinRTCustomAttributeValue.StringValue("Content")))))),
                WinRTTypeDefinition("$namespace.Shapes", "Shape", WinRTTypeKind.RuntimeClass,
                    baseTypeName = "$namespace.UIElement"),
                WinRTTypeDefinition("$namespace.Shapes", "Rectangle", WinRTTypeKind.RuntimeClass,
                    baseTypeName = "$namespace.Shapes.Shape"),
                WinRTTypeDefinition("$namespace.Shapes", "Unused", WinRTTypeKind.RuntimeClass,
                    baseTypeName = "$namespace.UIElement"),
            )
            val model = WinRTMetadataModel(types.groupBy { it.namespace }.map { (name, entries) ->
                WinRTNamespace(name, entries)
            })
            val root = Files.createTempDirectory("xaml-sdk-rcw").toFile()
            try {
                assertNotNull(writeXamlProjectedTypeRegistrationSource(root.toPath(), model,
                    setOf("$namespace.Shapes.Rectangle"), "Application", framework))
                val source = root.walkTopDown().single { it.extension == "kt" }.readText()
                assertFalse(source.contains("KOTLIN_WINRT_JVM_XAML_DEVELOPMENT_ACCESSORS"))
                val startup = source.substringAfter("internal fun registerKotlinWinUIXamlProjectedTypes_Application() {")
                    .substringBefore("if (io.github.composefluent.winrt.runtime.isWinUIXamlHotReloadEnabled())")
                listOf("$namespace.UIElement", "$namespace.Shapes.Shape", "$namespace.Shapes.Rectangle").forEach { name ->
                    assertTrue(startup, startup.contains("${xamlTypeClassId(name).asSingleFqName().asString()}::class"))
                }
                assertTrue(source.contains("contentProperty = \"Content\""))
                assertFalse(source.contains("Unused"))
                assertFalse(source.contains("activate ="))
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun referenced_winmd_activation_generates_fallback_without_a_ctor_method() {
        // CSharpTypeInfoPass2 emits XamlUserType.Activator for referenced classes;
        // CsWinRT's write_factory_constructors uses WinMD activation attributes.
        val root = Files.createTempDirectory("xaml-projected-activation").toFile()
        try {
            val reference = root.toPath().resolve("Library.winmd")
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Library", listOf(
                WinUIXamlApplicationTypeDescriptor("library.ToolTip"),
                WinUIXamlApplicationTypeDescriptor("library.NoActivation", isActivatable = false),
                WinUIXamlApplicationTypeDescriptor("library.Unused"),
            ), emptyMap(), reference, mapOf(
                "Windows.Foundation.Metadata.VersionAttribute" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.ActivatableAttribute" to "Windows.Foundation.FoundationContract",
            ))
            val type = WinRTMetadataLoader.load(reference).namespaces.flatMap { it.types }
                .single { it.name == "ToolTip" }
            assertTrue(type.activation.isActivatable)
            assertFalse(type.methods.any { it.name == ".ctor" })
            val output = root.toPath().resolve("generated")
            assertNotNull(writeXamlProjectedTypeRegistrationSource(output, listOf(reference),
                setOf("library.ToolTip", "library.NoActivation"), "Application"))
            val source = output.toFile().walkTopDown().single { it.isFile && it.extension == "kt" }.readText()
            assertTrue(source.contains("registerWinUIXamlProjectedTypeDefinition"))
            assertTrue(source.contains("activate = { library.ToolTip() }"))
            assertFalse(source.contains("NoActivation"))
            assertFalse(source.contains("Unused"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun library_schema_and_compiled_accessors_omit_unavailable_kotlin_declarations() {
        // CsWinRT WinRTTypeWriter filters public exported members. A Kotlin model
        // additionally excludes HIDDEN/ERROR declarations from compiled XAML access.
        val root = Files.createTempDirectory("xaml-library-availability").toFile()
        try {
            val source = File(root, "Models.kt").apply { writeText("""
                @file:Suppress("DEPRECATION_ERROR")
                package probe
                @Deprecated("removed", level = DeprecationLevel.HIDDEN) class Hidden {
                    class Nested
                }
                @Deprecated("removed", level = DeprecationLevel.ERROR) class Removed
                @Deprecated("legacy") class Legacy { var text = "legacy" }
                class NoActivation @Deprecated("removed", level = DeprecationLevel.ERROR) constructor()
                enum class Choice { Current, @Deprecated("removed", level = DeprecationLevel.HIDDEN) Removed }
                @io.github.composefluent.winrt.runtime.WinUIXamlCreateFromString("parse")
                class Model {
                    var current = "current"
                    var optional: Int? = null
                    val values: MutableList<Int?> = mutableListOf()
                    val mapping: MutableMap<String, Int?> = mutableMapOf()
                    var numbers = intArrayOf(1, 2)
                    @Deprecated("removed", level = DeprecationLevel.ERROR) var removed = "removed"
                    @get:Deprecated("removed", level = DeprecationLevel.HIDDEN) val hiddenGetter = "hidden"
                    @set:Deprecated("removed", level = DeprecationLevel.ERROR) var readOnly = "readable"
                    val unavailableType = Removed()
                    @Deprecated("removed", level = DeprecationLevel.HIDDEN) fun hiddenMethod() = "hidden"
                    fun currentMethod() = "current"
                    companion object {
                        fun parse(value: String) = Model().apply { current = value }
                        fun GetTag(target: Model) = target.current
                        fun SetTag(target: Model, value: String) { target.current = value }
                        @Deprecated("removed", level = DeprecationLevel.ERROR) fun overload(value: String) = value
                        fun overload(value: Int) = value
                    }
                }
            """.trimIndent()) }
            val metadata = File(root, "metadata.tsv").apply { writeText("") }
            val attributes = File(root, "Attributes.winmd").toPath()
            // Only declaring-assembly identity is needed from this metadata fixture.
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Attributes", listOf(
                "Windows.Foundation.Metadata.CreateFromStringAttribute", "Windows.Foundation.IReference`1",
                "Windows.Foundation.Collections.IVector`1", "Windows.Foundation.Collections.IMap`2"
            ).map { WinUIXamlApplicationTypeDescriptor(it, isActivatable = false) }, emptyMap(), attributes, emptyMap())
            val references = File(root, "references.txt").apply { writeText(attributes.toString()) }
            val schema = File(root, "schema")
            val classpath = compilerRuntimeClasspath()
            fun compile(sources: List<File>, destination: String, export: Boolean, combined: Boolean = false) {
                val options = if (export) mapOf("metadataIndex" to metadata.absolutePath,
                    "xamlLibraryOutput" to schema.absolutePath, "xamlLibraryAssembly" to "Probe",
                    "xamlLibraryReferences" to references.absolutePath,
                    "xamlLibraryCompile" to combined.toString()) else emptyMap()
                val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                    "-d", File(root, destination).absolutePath) + sources.map { it.absolutePath } +
                    (if (export) listOf("-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}") else emptyList()) +
                    options.flatMap { (key, value) -> listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value") }
                val diagnostics = ByteArrayOutputStream()
                val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
                assertEquals(diagnostics.toString(), ExitCode.OK, result)
            }
            compile(listOf(source), "export", true)
            assertFalse("Schema-only compilation must not emit library bytecode",
                File(root, "export").walkTopDown().any { it.isFile && it.extension == "class" })
            val types = WinRTMetadataLoader.load(File(schema, "KotlinXaml.winmd").toPath()).namespaces.flatMap { it.types }
            assertEquals(setOf("probe.Legacy", "probe.Model", "probe.NoActivation", "probe.Choice"),
                types.map { it.qualifiedName }.toSet())
            val model = types.single { it.name == "Model" }
            assertEquals(setOf("current", "readOnly", "optional", "values", "mapping", "numbers"), model.properties.map { it.name }.toSet())
            assertTrue(model.properties.single { it.name == "readOnly" }.isReadOnly)
            assertTrue(model.methods.none { it.name in setOf("hiddenMethod", "get_hiddenGetter", "get_removed") })
            assertEquals(1, model.methods.count { it.name == "overload" })
            val accessors = schema.resolve("src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            assertEquals(1, accessors.size)
            assertFalse(accessors.single().readText().contains("activate = { probe.NoActivation() }"))
            compile(listOf(source) + accessors, "with-accessors", false)
            assertTrue(File(root, "with-accessors/probe/Model.class").isFile)
            val registrar = File(root, "Registrar.kt").apply { writeText("""
                package io.github.composefluent.winrt.generated.xaml
                object KotlinXamlLibraryDefinitions_Probe {
                    private val registration: Unit = registerTypes()
                    private fun registerTypes() { error("missing compiler body") }
                    fun registerAll() { registration }
                }
            """.trimIndent()) }
            val expectedSchema = File(schema, "KotlinXaml.winmd").readBytes()
            compile(listOf(source, registrar), "combined", true, combined = true)
            assertArrayEquals(expectedSchema, File(schema, "KotlinXaml.winmd").readBytes())
            URLClassLoader(arrayOf(File(root, "combined").toURI().toURL()), javaClass.classLoader).use { loader ->
                val registration = loader.loadClass("io.github.composefluent.winrt.generated.xaml.KotlinXamlLibraryDefinitions_Probe")
                registration.getMethod("registerAll").invoke(registration.getField("INSTANCE").get(null))
                // Inspect the actual runtime registry, then execute the generated typed callbacks.
                val registry = Class.forName("io.github.composefluent.winrt.runtime.WinUIAuthoredTypeMetadata")
                val definitions = registry.getDeclaredField("definitions").apply { isAccessible = true }.get(null)
                val get = definitions.javaClass.getMethod("get", Any::class.java)
                val definition = get.invoke(definitions, "probe.Model") as io.github.composefluent.winrt.runtime.WinUIXamlTypeDefinition
                val instance = requireNotNull(definition.activate).invoke()
                val current = definition.members.getValue("current")
                assertEquals("current", current.get(instance))
                requireNotNull(current.set).invoke(instance, "updated")
                assertEquals("updated", current.get(instance))
                assertNull(definition.members.getValue("readOnly").set)
                val parsed = requireNotNull(definition.createFromString).invoke("parsed")
                assertEquals("parsed", current.get(parsed))
                val attached = definition.members.getValue("Tag")
                assertTrue(attached.isAttachable)
                requireNotNull(attached.set).invoke(instance, "attached")
                assertEquals("attached", attached.get(instance))
                val optional = definition.members.getValue("optional")
                requireNotNull(optional.set).invoke(instance, 42)
                assertEquals(42, optional.get(instance))
                optional.set!!.invoke(instance, null)
                assertNull(optional.get(instance))
                val vector = definition.members.getValue("values")
                val vectorValue = vector.get(instance)!!
                val add = vector.valueTypes.last().addToVector!!
                add(vectorValue, 7); add(vectorValue, null)
                assertEquals(listOf(7, null), vectorValue)
                val map = definition.members.getValue("mapping")
                val mapValue = map.get(instance)!!
                val put = map.valueTypes.last().addToMap!!
                put(mapValue, "number", 9); put(mapValue, "empty", null)
                assertEquals(mapOf("number" to 9, "empty" to null), mapValue)
                val array = definition.members.getValue("numbers")
                assertTrue(array.valueTypes.single().isArray)
                assertArrayEquals(intArrayOf(1, 2), array.get(instance) as IntArray)
                val unavailable = get.invoke(definitions, "probe.NoActivation") as io.github.composefluent.winrt.runtime.WinUIXamlTypeDefinition
                assertNull(unavailable.activate)
                registry.getMethod("clearForTests").invoke(registry.getField("INSTANCE").get(null))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun component_identity_takes_precedence_over_its_own_xaml_schema() {
        // CsWinRT resolves a component TypeRef to its declaring assembly. The extra
        // Kotlin XAML schema describes members without exporting a second component.
        val root = Files.createTempDirectory("xaml-component-identity")
        val schema = root.resolve("Library.KotlinXaml.winmd")
        val component = root.resolve("Library.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Library.KotlinXaml",
            listOf(WinUIXamlApplicationTypeDescriptor("sample.Component"),
                WinUIXamlApplicationTypeDescriptor("sample.Model")), emptyMap(), schema, emptyMap())
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("Library",
            listOf(WinRTAuthoredRuntimeClassDescriptor("sample.Component",
                interfaceNames = listOf("Windows.Foundation.IStringable"))), component,
            mapOf("Windows.Foundation.IStringable" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.VersionAttribute" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.DefaultAttribute" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.ActivatableAttribute" to "Windows.Foundation.FoundationContract"))
        val expected = mapOf("sample.Component" to "Library", "sample.Model" to "Library.KotlinXaml")
        assertEquals(expected, loadXamlReferenceTypeAssemblyNames(listOf(schema, component)))
        assertEquals(expected, loadXamlReferenceTypeAssemblyNames(listOf(component, schema)))
        assertThrows(IllegalArgumentException::class.java) {
            WinRTMetadataLoader.loadTypeAssemblyNames(listOf(schema, component))
        }
    }

    @Test
    fun xaml_schema_does_not_hide_an_unrelated_declaring_assembly() {
        val root = Files.createTempDirectory("xaml-conflicting-identity")
        val schema = root.resolve("Library.KotlinXaml.winmd")
        val component = root.resolve("Other.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Library.KotlinXaml",
            listOf(WinUIXamlApplicationTypeDescriptor("sample.Component")), emptyMap(), schema, emptyMap())
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd("Other",
            listOf(WinRTAuthoredRuntimeClassDescriptor("sample.Component",
                interfaceNames = listOf("Windows.Foundation.IStringable"))), component,
            mapOf("Windows.Foundation.IStringable" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.VersionAttribute" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.DefaultAttribute" to "Windows.Foundation.FoundationContract",
                "Windows.Foundation.Metadata.ActivatableAttribute" to "Windows.Foundation.FoundationContract"))
        assertThrows(IllegalArgumentException::class.java) {
            loadXamlReferenceTypeAssemblyNames(listOf(schema, component))
        }
    }

    @Test
    fun semantic_pass_exports_private_handler_without_exposing_it_in_winmd() {
        val references = System.getenv("WINRT_TEST_XAMLC_REFERENCES")
        val compiler = System.getenv("WINRT_TEST_XAMLC")
        val genXbf = System.getenv("WINRT_TEST_GENXBF")
        assumeTrue("Windows XamlCompiler, GenXbf and SDK references are required",
            !references.isNullOrBlank() && !compiler.isNullOrBlank() && !genXbf.isNullOrBlank())
        val root = File("build/xaml-semantic-integration").absoluteFile.apply { mkdirs() }
        val xaml = File(root, "MainPage.xaml").apply { writeText("""
            <Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" xmlns:local="using:probe" x:Class="probe.MainPage">
                <x:Properties>
                    <x:Property Name="Counter" Type="x:Int32" DefaultValue="2" />
                    <x:Property Name="ReadOnlyCount" Type="x:Int32" IsReadOnly="True" DefaultValue="7" />
                    <x:Property Name="ChoiceValue" Type="local:Choice" />
                    <x:Property Name="ParsedValue" Type="local:ParsedValue" DefaultValue="12" />
                    <x:Property Name="Title" Type="x:String" ChangedHandler="TitleUpdated" DefaultValue="escaped &quot;text&quot;" />
                    <x:Property Name="DefaultButton" Type="Button">
                        <x:Property.DefaultValue><Button Content="Default" /></x:Property.DefaultValue>
                    </x:Property>
                </x:Properties>
                <Button x:Name="myButton" Click="onClick" Content="Click" />
            </Page>
        """.trimIndent()) }
        fun item(file: File) = buildJsonObject {
            put("ItemSpec", file.absolutePath); put("FullPath", file.absolutePath); put("IsSystemReference", true)
        }
        val directories = references!!.split(File.pathSeparator).map(::File)
        val referenceFile = File(root, "references.txt").apply {
            writeText(directories.flatMap { it.listFiles()!!.filter { file -> file.extension == "winmd" } }
                .map { it.absolutePath }.sorted().joinToString("\n"))
        }
        val header = File(root, "Header.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd(
            "Header", listOf(WinUIXamlApplicationTypeDescriptor(
                "probe.MainPage", "Microsoft.UI.Xaml.Controls.Page"),
                WinUIXamlApplicationTypeDescriptor("probe.Choice", "System.Enum", enumEntries = listOf("Zero", "One")),
                WinUIXamlApplicationTypeDescriptor("probe.ParsedValue")), emptyMap(), header.toPath(),
            WinRTMetadataLoader.loadTypeAssemblyNames(directories.flatMap {
                it.listFiles()!!.filter { file -> file.extension == "winmd" }.map { file -> file.toPath() }
            }))
        val compilerInput = buildJsonObject {
            put("ProjectPath", File(root, "probe.proj").absolutePath); put("ProjectName", "probe")
            put("Language", "Kotlin"); put("LanguageSourceExtension", ".kt"); put("IsPass1", true)
            put("OutputPath", File(root, "generated").absolutePath); put("RootNamespace", "probe"); put("OutputType", "WinExe")
            put("TargetPlatformMinVersion", "10.0.19041.0"); put("GenXbfPath", genXbf!!)
            put("SavedStateFile", File(root, "state.xml").absolutePath)
            put("ReferenceAssemblies", JsonArray(directories.flatMap { it.listFiles()!!.filter { it.extension == "winmd" }.sorted() }.map(::item)))
            put("ReferenceAssemblyPaths", JsonArray((directories + File(System.getenv("WINDIR"), "Microsoft.NET/Framework64/v4.0.30319")).map(::item)))
            put("XamlPages", JsonArray(listOf(item(xaml))))
            put("LocalAssembly", JsonArray(listOf(item(header))))
        }
        fun invokeXaml(name: String, input: JsonObject): JsonObject {
            val inputFile = File(root, "$name.input.json").apply { writeText(input.toString()) }
            val outputFile = File(root, "$name.output.json")
            val console = File(root, "$name.console.txt")
            val process = ProcessBuilder(compiler!!, inputFile.absolutePath, outputFile.absolutePath)
                .redirectErrorStream(true).redirectOutput(console).start()
            if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("XamlCompiler timed out") }
            assertEquals(outputFile.takeIf { it.exists() }?.readText() ?: console.readText(), 0, process.exitValue())
            return Json.parseToJsonElement(outputFile.readText()).jsonObject
        }
        invokeXaml("declaration", compilerInput)
        val declarations = WinUIXamlDeclarations.readCompilerOutput(File(root, "declaration.output.json").toPath())
        val input = File(root, "declarations.json").apply { writeText(WinUIXamlDeclarations.canonicalText(declarations)) }
        val index = File(root, "metadata.tsv").apply { writeText(
            "Microsoft.UI.Xaml.Controls.Page\tRuntimeClass\t\tSystem.Object\n" +
                "Microsoft.UI.Xaml.Controls.Button\tRuntimeClass\t\tSystem.Object\n" +
                "Microsoft.UI.Xaml.RoutedEventArgs\tRuntimeClass\t\tSystem.Object\n" +
                "Microsoft.UI.Xaml.Data.PropertyChangedEventArgs\tRuntimeClass\t\tSystem.Object\n" +
                // A real SDK index also recognizes the expanded delegate alias.
                "Windows.Foundation.EventHandler`1\tDelegate\t\tSystem.MulticastDelegate\n") }
        val source = File(root, "MainPage.kt").apply { writeText("""
            package probe
            enum class Choice { Zero, One }
            @io.github.composefluent.winrt.runtime.WinUIXamlCreateFromString("parse")
            class ParsedValue(val value: Int) { companion object { fun parse(text: String) = ParsedValue(text.toInt()) } }
            class MainPage(failConstruction: Boolean = false) : microsoft.ui.xaml.controls.Page() {
                var constructed = false
                var tag = "primary"
                var initializedTag = ""
                var initializationCount = 0
                init { check(!failConstruction) { "constructor failed" }; constructed = true }
                constructor(tag: String) : this() { this.tag = tag }
                override fun initializeComponent() {
                    check(constructed)
                    super.initializeComponent()
                    val connected = myButton
                    super.initializeComponent()
                    check(connected === myButton)
                    initializedTag = tag
                    initializationCount++
                    check(tag != "failHook") { "hook failed" }
                }
                private fun onClick(sender: Any?, args: microsoft.ui.xaml.RoutedEventArgs) { myButton.text += "clicked" }
                private fun onAlternate(sender: Any?, args: microsoft.ui.xaml.RoutedEventArgs) { myButton.text += "alternate" }
                private fun onWrong(sender: String, args: microsoft.ui.xaml.RoutedEventArgs) { error("incompatible handler must never run") }
                fun changeCounter(value: Int) { Counter = value }
            }
            fun createForReload() = MainPage()
            fun exercise(): String {
                val first = MainPage()
                check(first.initializationCount == 1)
                check(first.Counter == 2 && first.ReadOnlyCount == 7 && first.Title == "escaped \"text\"")
                check(first.ChoiceValue == Choice.Zero)
                check(first.ParsedValue!!.value == 12)
                check(first.DefaultButton!!.text == "Default")
                val calls = mutableListOf<String>()
                val handler = microsoft.ui.xaml.data.PropertyChangedEventHandler { sender, args ->
                    check(sender === first); calls += args!!.propertyName!!
                }
                first.addCounterChanged(handler)
                first.Counter = 3
                first.Counter = 3
                first.changeCounter(4)
                check(calls == listOf("Counter", "Counter"))
                first.removeCounterChanged(handler)
                first.changeCounter(5)
                check(calls.size == 2)
                first.initializeComponent()
                check(first.Counter == 5)
                val connected = first.myButton
                first.connect(${declarations.pages.single().connections.single { it.fieldName == "myButton" }.id}, connected)
                check(connected.subscriptionCount == 1)
                val replacement = microsoft.ui.xaml.controls.Button()
                first.connect(${declarations.pages.single().connections.single { it.fieldName == "myButton" }.id}, replacement)
                check(connected.subscriptionCount == 0 && replacement.subscriptionCount == 1)
                connected.raise()
                check(replacement.text.isEmpty())
                first.myButton.raise()
                first.initializeComponent()
                first.myButton.raise()
                val factory: () -> MainPage = ::MainPage
                val second = factory()
                check(second.Counter == 2 && second.DefaultButton !== first.DefaultButton)
                second.initializeComponent()
                second.myButton.raise()
                check(first.getBindingConnector(0, null) == null)
                val secondary = MainPage("secondary")
                check(secondary.initializedTag == "secondary")
                check(secondary.initializationCount == 1)
                check(first.initializationCount == 3 && second.initializationCount == 2)
                val loads = microsoft.ui.xaml.Application.loads
                try { MainPage(true); error("constructor must fail") } catch (error: IllegalStateException) {
                    check(error.message == "constructor failed")
                }
                check(microsoft.ui.xaml.Application.loads == loads)
                try { MainPage("failHook"); error("hook must fail") } catch (error: IllegalStateException) {
                    check(error.message == "hook failed")
                }
                return first.myButton.text + ":" + second.myButton.text
            }
        """.trimIndent()) }
        // Tooling fixture only: the actual delegate and base resolve from SDK WinMD in the loader gate.
        val base = File(root, "Page.kt").apply { writeText("""
            package microsoft.ui.xaml.controls
            open class Page
            class Button : microsoft.ui.xaml.controls.primitives.ButtonBase() { var text = "" }
        """.trimIndent()) }
        val button = File(root, "ButtonBase.kt").apply { writeText("""
            package microsoft.ui.xaml.controls.primitives
            open class ButtonBase {
                private val handlers = mutableMapOf<Long, microsoft.ui.xaml.RoutedEventHandler>()
                private var nextToken = 0L
                val subscriptionCount get() = handlers.size
                fun addClick(handler: microsoft.ui.xaml.RoutedEventHandler): Long = (++nextToken).also { handlers[it] = handler }
                fun removeClick(token: Long) { check(handlers.remove(token) != null) }
                fun raise() { handlers.values.toList().forEach { it.invoke(this, microsoft.ui.xaml.RoutedEventArgs()) } }
            }
        """.trimIndent()) }
        val buttonId = declarations.pages.single().connections.single { it.fieldName == "myButton" }.id
        val argsType = File(root, "Args.kt").apply { writeText("""
            package microsoft.ui.xaml
            class RoutedEventArgs
            fun interface RoutedEventHandler { fun invoke(sender: Any?, args: RoutedEventArgs) }
            class Application { companion object Metadata {
                var loads = 0
                fun loadComponent(page: Any, uri: windows.foundation.Uri) {
                    loads++
                    check(uri.value == "ms-appx:///MainPage.xaml")
                    (page as microsoft.ui.xaml.markup.IComponentConnector).connect($buttonId, microsoft.ui.xaml.controls.Button())
                }
            } }
        """.trimIndent()) }
        val uri = File(root, "Uri.kt").apply { writeText("package windows.foundation; class Uri(val value: String)") }
        val connector = File(root, "Connector.kt").apply { writeText("""
            package microsoft.ui.xaml.markup
            interface IComponentConnector {
                fun connect(connectionId: Int, target: Any?)
                fun getBindingConnector(connectionId: Int, target: Any?): IComponentConnector?
            }
        """.trimIndent()) }
        val symbols = File(root, "symbols.json")
        val registrar = File(root, "Definitions.kt").apply { writeText("""
            package io.github.composefluent.winrt.generated.xaml
            object KotlinXamlApplicationDefinitionsProbe { fun registerAll() {
                io.github.composefluent.winrt.runtime.registerWinUIXamlEnumType(probe.Choice::class, "probe.Choice", probe.Choice.entries.toTypedArray())
                io.github.composefluent.winrt.runtime.registerWinUIXamlTypeDefinition(io.github.composefluent.winrt.runtime.WinUIXamlTypeDefinition(
                    type = probe.ParsedValue::class, name = "probe.ParsedValue", baseName = "System.Object", isWinRTComponent = false,
                    createFromString = { probe.ParsedValue.parse(it) }))
            } }
            internal inline fun <reified T> kotlinWinUIXamlMemberValue(value: Any?): T {
                if (value is T || value !is String) return value as T
                return io.github.composefluent.winrt.runtime.convertWinUIXamlLiteral(T::class, value) { type, text ->
                    check(type == Int::class); text.toInt()
                } as T
            }
        """.trimIndent()) }
        val markup = File(root, "Markup.kt").apply { writeText("""
            package microsoft.ui.xaml.markup
            class XamlReader { companion object Metadata {
                fun load(text: String): Any {
                    check(text.contains("xmlns=") && text.contains("Content=\"Default\""))
                    return microsoft.ui.xaml.controls.Button().apply { this.text = "Default" }
                }
            } }
        """.trimIndent()) }
        fun compile(final: Boolean = false): Pair<ExitCode, String> {
            val classpath = compilerRuntimeClasspath()
            val options = mapOf("xamlDeclarations" to (if (final) File(root, "runtime-declarations.json") else input).absolutePath) + if (final)
                mapOf("xamlImplementation" to File(root, "final.output.json").absolutePath)
            else mapOf("metadataIndex" to index.absolutePath, "xamlSemanticOutput" to symbols.absolutePath, "xamlReferencesFile" to referenceFile.absolutePath,
                "xamlApplicationHeader" to header.absolutePath)
            val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}", "-d", File(root, if (final) "final" else "semantic-only").absolutePath,
                source.absolutePath, base.absolutePath, argsType.absolutePath, button.absolutePath, connector.absolutePath,
                uri.absolutePath, registrar.absolutePath, markup.absolutePath) +
                options.flatMap { (key, value) -> listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value") }
            val diagnostics = ByteArrayOutputStream()
            val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
            return result to diagnostics.toString()
        }
        val (result, diagnostics) = compile()
        assertEquals(diagnostics, ExitCode.OK, result)
        val output = Json.parseToJsonElement(symbols.readText()).jsonObject
        assertEquals(WinUIXamlDeclarations.fingerprint(declarations), output.getValue("DeclarationFingerprint").jsonPrimitive.content)
        val handler = output.getValue("Pages").jsonArray.single().jsonObject.getValue("Handlers").jsonArray.single().jsonObject
        assertEquals("onClick", handler.getValue("Name").jsonPrimitive.content)
        assertEquals(listOf("System.Object", "Microsoft.UI.Xaml.RoutedEventArgs"),
            handler.getValue("ParameterTypeNames").jsonArray.map { it.jsonPrimitive.content })
        val page = WinRTMetadataLoader.load(File(root, "KotlinXaml.winmd").toPath()).namespaces.single().types.single { it.name == "MainPage" }
        // Application schema includes private methods for XAML access checks; it is
        // distinct from the authored component's exported ABI.
        assertEquals(WinRTMethodVisibility.Private, page.methods.single { it.name == "onClick" }.visibility)
        assertEquals(setOf("Counter", "ReadOnlyCount", "Title", "DefaultButton"), page.properties
            .filter { it.name in setOf("Counter", "ReadOnlyCount", "Title", "DefaultButton") }.map { it.name }.toSet())
        assertTrue(page.properties.single { it.name == "ReadOnlyCount" }.isReadOnly)
        assertEquals(1, page.events.count { it.name == "TitleUpdated" })
        val finalOutput = invokeXaml("final", JsonObject(compilerInput + mapOf(
            "IsPass1" to JsonPrimitive(false), "KotlinSymbols" to output,
            "LocalAssembly" to JsonArray(listOf(item(File(root, "KotlinXaml.winmd")))),
        )))
        assertEquals(WinUIXamlDeclarations.fingerprint(declarations), finalOutput.getValue("KotlinImplementation")
            .jsonObject.getValue("DeclarationFingerprint").jsonPrimitive.content)
        val xbf = finalOutput.getValue("GeneratedXbfFiles").jsonArray.single().jsonPrimitive.content
        assertTrue("XBF is missing or empty", Files.size(Path.of(xbf)) > 16)
        val rewritten = File(finalOutput.getValue("GeneratedXamlFiles").jsonArray.single().jsonPrimitive.content).readText()
        assertTrue(rewritten.contains("ConnectionId"))
        assertFalse(rewritten.contains("Click=\"onClick\""))
        // Match CompileWinUIXamlTask: semantic declarations remain compiler-owned,
        // while runtime declarations and the implementation carry the source fingerprint.
        val fingerprinted = declarations.copy(pages = declarations.pages.map {
            it.copy(sourceHash = WinUIXamlDeclarations.sourceFingerprint(xaml.readText()))
        })
        val runtimeDeclarations = WinUIXamlDeclarations.canonicalText(fingerprinted)
        File(root, "runtime-declarations.json").writeText(runtimeDeclarations)
        File(root, "final.output.json").writeText(JsonObject(finalOutput + mapOf(
            "KotlinDeclarations" to Json.parseToJsonElement(runtimeDeclarations),
            "KotlinImplementation" to JsonObject(finalOutput.getValue("KotlinImplementation").jsonObject +
                ("DeclarationFingerprint" to JsonPrimitive(WinUIXamlDeclarations.fingerprint(fingerprinted)))),
        )).toString())
        val (finalResult, finalDiagnostics) = compile(final = true)
        assertEquals(finalDiagnostics, ExitCode.OK, finalResult)
        val consumerSource = File(root, "Consumer.kt").apply { writeText("""
            package consumer
            import probe.MainPage
            fun exercise(): String {
                val direct = probe.MainPage("external")
                val factory: (String) -> probe.MainPage = ::MainPage
                val referenced = factory("reference")
                check(direct.initializationCount == 1 && referenced.initializationCount == 1)
                return direct.initializedTag + ":" + referenced.initializedTag
            }
        """.trimIndent()) }
        // A separate consumer has no XAML declaration input; the serialized managed interface
        // must suffice to preserve construction behavior for direct calls and references.
        val consumerClasspath = File(root, "final").absolutePath + File.pathSeparator + compilerRuntimeClasspath()
        val consumerDiagnostics = ByteArrayOutputStream()
        val consumerResult = PrintStream(consumerDiagnostics).use { stream -> K2JVMCompiler().exec(stream,
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", consumerClasspath,
            "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}", "-d", File(root, "consumer").absolutePath,
            consumerSource.absolutePath)
        }
        assertEquals(consumerDiagnostics.toString(), ExitCode.OK, consumerResult)
        URLClassLoader(arrayOf(File(root, "final").toURI().toURL(), File(root, "consumer").toURI().toURL()), javaClass.classLoader).use { loader ->
            assertEquals("clickedclicked:clicked", loader.loadClass("probe.MainPageKt").getMethod("exercise").invoke(null))
            val pageClass = loader.loadClass("probe.MainPage")
            assertTrue(java.lang.reflect.Modifier.isPrivate(pageClass.getDeclaredMethod("onClick",
                Any::class.java, loader.loadClass("microsoft.ui.xaml.RoutedEventArgs")).modifiers))
            assertFalse(java.lang.reflect.Modifier.isStatic(pageClass.getDeclaredField("myButton").modifiers))
            assertTrue(pageClass.methods.none { it.name == "setMyButton" })
            assertTrue(pageClass.methods.none { it.name == "setReadOnlyCount" })
            assertEquals("external:reference", loader.loadClass("consumer.ConsumerKt").getMethod("exercise").invoke(null))
            // Test-only access installs an in-memory transport registry. Production
            // event forwarding uses only compiler-generated typed calls, never reflection.
            val registryClass = Class.forName("io.github.composefluent.winrt.runtime.WinUIXamlHotReloadRegistry")
            val registry = registryClass.constructors.single { it.parameterCount == 5 }.newInstance(
                { { task: () -> Unit -> task(); true } },
                { _: kotlin.reflect.KClass<*>, text: String -> text },
                { _: String -> emptyMap<Any?, Any?>() }, { _: String -> Any() }, null)
            val configuration = Class.forName("io.github.composefluent.winrt.runtime.WinUIXamlHotReloadConfiguration")
            val instance = configuration.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            val field = configuration.getDeclaredField("registry").apply { isAccessible = true }
            val previous = field.get(instance)
            try {
                field.set(instance, registry)
                val reloaded = loader.loadClass("probe.MainPageKt").getMethod("createForReload").invoke(null)
                val control = pageClass.getMethod("getKotlinWinUIXaml_myButton").invoke(reloaded)
                fun snapshot() = registryClass.getMethod("snapshot").invoke(registry) as io.github.composefluent.winrt.runtime.WinUIXamlHotReloadReply
                fun retarget(expected: String, handler: String): io.github.composefluent.winrt.runtime.WinUIXamlHotReloadReply {
                    val loaded = snapshot().roots.single()
                    val patch = io.github.composefluent.winrt.runtime.WinUIXamlHotReloadPatch(loaded.className, loaded.resourcePath,
                        loaded.sourceHash, "b".repeat(64), loaded.version + 1, emptyList(), events = listOf(
                            io.github.composefluent.winrt.runtime.WinUIXamlHotReloadEvent("myButton", "Click", expected, handler)))
                    var result: io.github.composefluent.winrt.runtime.WinUIXamlHotReloadReply? = null
                    registryClass.getMethod("submit", io.github.composefluent.winrt.runtime.WinUIXamlHotReloadPatch::class.java,
                        kotlin.jvm.functions.Function1::class.java).invoke(registry, patch,
                        { reply: io.github.composefluent.winrt.runtime.WinUIXamlHotReloadReply -> result = reply })
                    return requireNotNull(result)
                }
                assertEquals(1, control.javaClass.getMethod("getSubscriptionCount").invoke(control))
                retarget("onClick", "onAlternate").let { assertEquals(it.message, 0, it.status) }
                control.javaClass.getMethod("raise").invoke(control)
                assertEquals("alternate", control.javaClass.getMethod("getText").invoke(control))
                assertEquals(1, retarget("onAlternate", "onWrong").status)
                retarget("onAlternate", "").let { assertEquals(it.message, 0, it.status) }
                control.javaClass.getMethod("raise").invoke(control)
                assertEquals("alternate", control.javaClass.getMethod("getText").invoke(control))
                assertEquals(1, control.javaClass.getMethod("getSubscriptionCount").invoke(control))
                assertTrue(java.lang.reflect.Modifier.isPrivate(pageClass.getDeclaredMethod("onAlternate",
                    Any::class.java, loader.loadClass("microsoft.ui.xaml.RoutedEventArgs")).modifiers))
            } finally {
                field.set(instance, previous)
                registryClass.getMethod("close").invoke(registry)
            }

        }
        val validSource = source.readText()
        val validArgs = argsType.readText()
        argsType.writeText(validArgs.replace("args: RoutedEventArgs)", "args: RoutedEventArgs?)"))
        val narrowedHandler = compile()
        assertEquals(narrowedHandler.second, ExitCode.COMPILATION_ERROR, narrowedHandler.first)
        assertTrue(narrowedHandler.second, narrowedHandler.second.contains("onClick does not match projected delegate"))
        assertTrue(narrowedHandler.second, narrowedHandler.second.contains("nullable arguments"))
        assertFalse("Rejected handler must invalidate the symbol sidecar", symbols.exists())
        source.writeText(validSource.replace("args: microsoft.ui.xaml.RoutedEventArgs)", "args: microsoft.ui.xaml.RoutedEventArgs?)"))
        val nullableHandler = compile()
        assertEquals(nullableHandler.second, ExitCode.OK, nullableHandler.first)
        argsType.writeText(validArgs)
        source.writeText(validSource)
        source.writeText("""
            package probe
            enum class Choice { Zero, One }
            @io.github.composefluent.winrt.runtime.WinUIXamlCreateFromString("parse")
            class ParsedValue(val value: Int) { companion object { fun parse(text: String) = ParsedValue(text.toInt()) } }
            class MainPage : microsoft.ui.xaml.controls.Page() {
                private fun onClick(sender: Any?, args: microsoft.ui.xaml.RoutedEventArgs) { myButton.text += "clicked" }
            }
            fun exerciseDefault(): String {
                val factory: () -> MainPage = ::MainPage
                val page = factory()
                val button = page.myButton
                page.initializeComponent()
                check(button === page.myButton)
                check(microsoft.ui.xaml.Application.loads == 1)
                button.raise()
                return button.text
            }
        """.trimIndent())
        val (defaultResult, defaultDiagnostics) = compile(final = true)
        assertEquals(defaultDiagnostics, ExitCode.OK, defaultResult)
        URLClassLoader(arrayOf(File(root, "final").toURI().toURL()), javaClass.classLoader).use { loader ->
            assertEquals("clicked", loader.loadClass("probe.MainPageKt").getMethod("exerciseDefault").invoke(null))
        }
        source.writeText(validSource.replace("args: microsoft.ui.xaml.RoutedEventArgs", "args: kotlin.String"))
        val staleFinal = compile(final = true)
        assertNotEquals("A stale sidecar must not allow a changed handler signature", ExitCode.OK, staleFinal.first)
        assertTrue(staleFinal.second, staleFinal.second.contains("no longer matches"))
        source.writeText(validSource)
        val validBase = base.readText()
        base.writeText(validBase.replace("open class Page", "open class Page { open var myButton: Any? = null }"))
        val collision = compile()
        assertEquals(collision.second, ExitCode.COMPILATION_ERROR, collision.first)
        assertTrue(collision.second, collision.second.contains("x:Name 'myButton' conflicts with existing property"))
        assertTrue(collision.second, collision.second.contains("mainPage.xaml:"))
        base.writeText(validBase)
        source.writeText(validSource + "\nfun invalid( =\n")
        assertNotEquals("Frontend errors must fail semantic compilation", ExitCode.OK, compile().first)
        assertFalse("Frontend failure must invalidate a previous sidecar", symbols.exists())
        source.writeText(validSource.replace("private fun onClick", "private suspend fun onClick"))
        assertNotEquals("suspend handlers must fail semantic export", ExitCode.OK, compile().first)
        assertFalse("Failed semantic compilation must not leave a stale sidecar", symbols.exists())
    }
}
