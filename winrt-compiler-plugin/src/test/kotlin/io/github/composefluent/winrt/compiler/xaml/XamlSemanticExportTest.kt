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
    fun semantic_pass_exports_private_handler_without_exposing_it_in_winmd() {
        val references = System.getenv("WINRT_TEST_XAMLC_REFERENCES")
        val compiler = System.getenv("WINRT_TEST_XAMLC")
        val genXbf = System.getenv("WINRT_TEST_GENXBF")
        assumeTrue("Windows XamlCompiler, GenXbf and SDK references are required",
            !references.isNullOrBlank() && !compiler.isNullOrBlank() && !genXbf.isNullOrBlank())
        val root = File("build/xaml-semantic-integration").absoluteFile.apply { mkdirs() }
        val xaml = File(root, "MainPage.xaml").apply { writeText("""
            <Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="probe.MainPage">
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
                "probe.MainPage", "Microsoft.UI.Xaml.Controls.Page")), emptyMap(), header.toPath(),
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
                "Microsoft.UI.Xaml.RoutedEventArgs\tRuntimeClass\t\tSystem.Object\n") }
        val source = File(root, "MainPage.kt").apply { writeText("""
            package probe
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
            }
            fun exercise(): String {
                val first = MainPage()
                check(first.initializationCount == 1)
                first.initializeComponent()
                first.myButton.raise()
                first.initializeComponent()
                first.myButton.raise()
                val factory: () -> MainPage = ::MainPage
                val second = factory()
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
            object KotlinXamlApplicationDefinitionsProbe { fun registerAll() {} }
        """.trimIndent()) }
        fun compile(final: Boolean = false): Pair<ExitCode, String> {
            val classpath = listOf(Unit::class.java, WinRTXamlLoadState::class.java).joinToString(File.pathSeparator) {
                File(it.protectionDomain.codeSource.location.toURI()).absolutePath
            }
            val options = mapOf("xamlDeclarations" to input.absolutePath) + if (final)
                mapOf("xamlImplementation" to File(root, "final.output.json").absolutePath)
            else mapOf("metadataIndex" to index.absolutePath, "xamlSemanticOutput" to symbols.absolutePath, "xamlReferencesFile" to referenceFile.absolutePath)
            val arguments = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath,
                "-Xplugin=${System.getProperty("winrt.test.fullPluginJar")}", "-d", File(root, if (final) "final" else "semantic-only").absolutePath,
                source.absolutePath, base.absolutePath, argsType.absolutePath, button.absolutePath, connector.absolutePath,
                uri.absolutePath, registrar.absolutePath) +
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
        val page = WinRTMetadataLoader.load(File(root, "KotlinXaml.winmd").toPath()).namespaces.single().types.single()
        // Application schema includes private methods for XAML access checks; it is
        // distinct from the authored component's exported ABI.
        assertEquals(WinRTMethodVisibility.Private, page.methods.single { it.name == "onClick" }.visibility)
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
            assertEquals("external:reference", loader.loadClass("consumer.ConsumerKt").getMethod("exercise").invoke(null))
        }
        val validSource = source.readText()
        source.writeText("""
            package probe
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
