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
import io.github.composefluent.winrt.runtime.WinRTXamlLoadState

class XamlSemanticExportTest {
    @Test
    fun development_accessors_use_the_system_type_projection_for_style_target_type() {
        // CsWinRT helpers.h maps Windows.UI.Xaml.Interop.TypeName to System.Type;
        // KotlinProjectionTypeResolver emits KClass<*>?, not a TypeName struct.
        val resolver = WinRTMetadataModel(emptyList()).specialTypeResolver()
        listOf("Windows.UI.Xaml.Interop.TypeName", "System.Type").forEach { name ->
            assertEquals("kotlin.reflect.KClass", xamlTypeClassId(name).asSingleFqName().asString())
            assertEquals("kotlin.reflect.KClass<*>?", xamlProjectedPropertySourceType(WinRTTypeRef.named(name), resolver))
        }
        assertEquals("kotlin.collections.MutableList<kotlin.reflect.KClass<*>?>", xamlProjectedPropertySourceType(
            WinRTTypeRef.fromDisplayName("Windows.Foundation.Collections.IVector<Windows.UI.Xaml.Interop.TypeName>"), resolver))
    }

    @Test
    fun referenced_winmd_activation_generates_fallback_without_a_ctor_method() {
        // CSharpTypeInfoPass2 emits XamlUserType.Activator for referenced classes;
        // CsWinRT's write_factory_constructors uses WinMD activation attributes.
        val root = Files.createTempDirectory("xaml-projected-activation").toFile()
        try {
            val reference = root.toPath().resolve("Library.winmd")
            WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Library", listOf(
                WinRTXamlApplicationTypeDescriptor("library.ToolTip"),
                WinRTXamlApplicationTypeDescriptor("library.NoActivation", isActivatable = false),
                WinRTXamlApplicationTypeDescriptor("library.Unused"),
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
            assertTrue(source.contains("registerWinRTXamlProjectedTypeDefinition"))
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
                class Model {
                    var current = "current"
                    @Deprecated("removed", level = DeprecationLevel.ERROR) var removed = "removed"
                    @get:Deprecated("removed", level = DeprecationLevel.HIDDEN) val hiddenGetter = "hidden"
                    @set:Deprecated("removed", level = DeprecationLevel.ERROR) var readOnly = "readable"
                    val unavailableType = Removed()
                    @Deprecated("removed", level = DeprecationLevel.HIDDEN) fun hiddenMethod() = "hidden"
                    fun currentMethod() = "current"
                    companion object {
                        @Deprecated("removed", level = DeprecationLevel.ERROR) fun overload(value: String) = value
                        fun overload(value: Int) = value
                    }
                }
            """.trimIndent()) }
            val metadata = File(root, "metadata.tsv").apply { writeText("") }
            val references = File(root, "references.txt").apply { writeText("") }
            val schema = File(root, "schema")
            val classpath = listOf(Unit::class.java, WinRTXamlLoadState::class.java).joinToString(File.pathSeparator) {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }
            fun compile(sources: List<File>, destination: String, export: Boolean) {
                val options = if (export) mapOf("metadataIndex" to metadata.absolutePath,
                    "xamlLibraryOutput" to schema.absolutePath, "xamlLibraryAssembly" to "Probe",
                    "xamlLibraryReferences" to references.absolutePath) else emptyMap()
                val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                    "-d", File(root, destination).absolutePath) + sources.map { it.absolutePath } +
                    (if (export) listOf("-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}") else emptyList()) +
                    options.flatMap { (key, value) -> listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value") }
                val diagnostics = ByteArrayOutputStream()
                val result = PrintStream(diagnostics).use { K2JVMCompiler().exec(it, *arguments.toTypedArray()) }
                assertEquals(diagnostics.toString(), ExitCode.OK, result)
            }
            compile(listOf(source), "export", true)
            val types = WinRTMetadataLoader.load(File(schema, "KotlinXaml.winmd").toPath()).namespaces.flatMap { it.types }
            assertEquals(setOf("probe.Legacy", "probe.Model", "probe.NoActivation", "probe.Choice"),
                types.map { it.qualifiedName }.toSet())
            val model = types.single { it.name == "Model" }
            assertEquals(setOf("current", "readOnly"), model.properties.map { it.name }.toSet())
            assertTrue(model.properties.single { it.name == "readOnly" }.isReadOnly)
            assertTrue(model.methods.none { it.name in setOf("hiddenMethod", "get_hiddenGetter", "get_removed") })
            assertEquals(1, model.methods.count { it.name == "overload" })
            val accessors = schema.resolve("src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            assertEquals(1, accessors.size)
            assertFalse(accessors.single().readText().contains("activate = { probe.NoActivation() }"))
            compile(listOf(source) + accessors, "with-accessors", false)
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
            listOf(WinRTXamlApplicationTypeDescriptor("sample.Component"),
                WinRTXamlApplicationTypeDescriptor("sample.Model")), emptyMap(), schema, emptyMap())
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
            listOf(WinRTXamlApplicationTypeDescriptor("sample.Component")), emptyMap(), schema, emptyMap())
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
            "Header", listOf(WinRTXamlApplicationTypeDescriptor(
                "probe.MainPage", "Microsoft.UI.Xaml.Controls.Page"),
                WinRTXamlApplicationTypeDescriptor("probe.Choice", "System.Enum", enumEntries = listOf("Zero", "One")),
                WinRTXamlApplicationTypeDescriptor("probe.ParsedValue")), emptyMap(), header.toPath(),
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
        val declarations = WinRTXamlDeclarations.readCompilerOutput(File(root, "declaration.output.json").toPath())
        val input = File(root, "declarations.json").apply { writeText(WinRTXamlDeclarations.canonicalText(declarations)) }
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
            @io.github.composefluent.winrt.runtime.WinRTXamlCreateFromString("parse")
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
                fun changeCounter(value: Int) { Counter = value }
            }
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
                private val handlers = mutableListOf<microsoft.ui.xaml.RoutedEventHandler>()
                fun addClick(handler: microsoft.ui.xaml.RoutedEventHandler) { handlers.add(handler) }
                fun raise() { handlers.forEach { it.invoke(this, microsoft.ui.xaml.RoutedEventArgs()) } }
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
                io.github.composefluent.winrt.runtime.registerWinRTXamlEnumType(probe.Choice::class, "probe.Choice", probe.Choice.entries.toTypedArray())
                io.github.composefluent.winrt.runtime.registerWinRTXamlTypeDefinition(io.github.composefluent.winrt.runtime.WinRTXamlTypeDefinition(
                    type = probe.ParsedValue::class, name = "probe.ParsedValue", baseName = "System.Object", isWinRTComponent = false,
                    createFromString = { probe.ParsedValue.parse(it) }))
            } }
            internal inline fun <reified T> kotlinWinRTXamlMemberValue(value: Any?): T {
                if (value is T || value !is String) return value as T
                return io.github.composefluent.winrt.runtime.convertWinRTXamlLiteral(T::class, value) { type, text ->
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
            val classpath = listOf(Unit::class.java, WinRTXamlLoadState::class.java).joinToString(File.pathSeparator) {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }
            val options = mapOf("xamlDeclarations" to input.absolutePath) + if (final)
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
        assertEquals(WinRTXamlDeclarations.fingerprint(declarations), output.getValue("DeclarationFingerprint").jsonPrimitive.content)
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
        assertEquals(WinRTXamlDeclarations.fingerprint(declarations), finalOutput.getValue("KotlinImplementation")
            .jsonObject.getValue("DeclarationFingerprint").jsonPrimitive.content)
        val xbf = finalOutput.getValue("GeneratedXbfFiles").jsonArray.single().jsonPrimitive.content
        assertTrue("XBF is missing or empty", Files.size(Path.of(xbf)) > 16)
        val rewritten = File(finalOutput.getValue("GeneratedXamlFiles").jsonArray.single().jsonPrimitive.content).readText()
        assertTrue(rewritten.contains("ConnectionId"))
        assertFalse(rewritten.contains("Click=\"onClick\""))
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
        val consumerClasspath = listOf(File(root, "final")) + listOf(Unit::class.java, WinRTXamlLoadState::class.java).map {
            File(it.protectionDomain.codeSource.location.toURI())
        }
        val consumerDiagnostics = ByteArrayOutputStream()
        val consumerResult = PrintStream(consumerDiagnostics).use { stream -> K2JVMCompiler().exec(stream,
            "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", consumerClasspath.joinToString(File.pathSeparator),
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
        }
        val validSource = source.readText()
        source.writeText("""
            package probe
            enum class Choice { Zero, One }
            @io.github.composefluent.winrt.runtime.WinRTXamlCreateFromString("parse")
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
