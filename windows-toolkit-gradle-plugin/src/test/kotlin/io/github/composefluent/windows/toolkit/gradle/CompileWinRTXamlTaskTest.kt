package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor
import kotlinx.serialization.json.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class CompileWinRTXamlTaskTest {
    @Test fun compiled_layout_uses_the_load_component_uri_without_build_or_source_prefixes() {
        val root = Files.createTempDirectory("xaml-pri-")
        val compiled = root.resolve("build/generated/xaml/final/compiled")
        val xbf = compiled.resolve("views/MainPage.xbf")
        Files.createDirectories(xbf.parent)
        Files.write(xbf, byteArrayOf(0x58, 0x42, 0x46))
        Files.writeString(compiled.resolve("views/MainPage.xaml"), "<Page/>")
        val original = root.resolve("src/main/kotlin/views/MainPage.xaml")
        Files.createDirectories(original.parent)
        Files.writeString(original, "<Page/>")
        val priRoot = root.resolve("pri")
        val items = ProjectPriInputStager(priRoot, "", root,
            mapOf(compiled.toString() to ""), setOf(original.toString())).stage(
            componentPriFiles = emptyList(), componentPriBaseRoot = root,
            appxResourceFiles = emptyList(), explicitResourceFiles = emptyList(),
            explicitLayoutFiles = listOf(compiled), explicitContentFiles = emptyList(),
            explicitEmbedFiles = emptyList(), defaultResourceFiles = emptyList(),
            defaultLayoutFiles = listOf(original), defaultContentFiles = emptyList(),
            includeDefaultProjectResources = true,
        )
        val embedded = items.single { it.kind == ApplicationPackageItemKind.Embed }
        assertEquals(priRoot.resolve("embed/views/MainPage.xbf"), embedded.target)
        assertArrayEquals(Files.readAllBytes(xbf), Files.readAllBytes(embedded.target))
        assertTrue(items.none { it.kind == ApplicationPackageItemKind.Layout || it.source == original })
    }

    @Test fun analyzes_nested_resources_and_removes_deleted_page_artifacts() {
        val compiler = System.getenv("WINRT_TEST_XAMLC")
        val genXbf = System.getenv("WINRT_TEST_GENXBF")
        val refs = System.getenv("WINRT_TEST_XAMLC_REFERENCES")
        assumeTrue(compiler != null && genXbf != null && refs != null)
        val root = Files.createTempDirectory("xaml-gradle-").toFile()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val sources = File(root, "src").apply { mkdirs() }
        val page = File(sources, "pages/MainPage.xaml").apply {
            parentFile.mkdirs()
            writeText("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.MainPage"><StackPanel><CheckBox x:Name="checked" IsChecked="True"/><CheckBox IsChecked="False"/><CheckBox IsThreeState="True" IsChecked="{x:Null}"/></StackPanel></Page>""")
        }
        File(page.parentFile, "MainPage.kt").writeText("package sample; class MainPage")
        fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val manifest = File(root, "resolved.tsv").apply {
            writeText("kotlin-winrt-prepared-metadata-v1\n" + refs!!.split(File.pathSeparator).flatMap {
                // Installed SDKs provide split contracts without the Windows.winmd facade.
                // XamlCompiler must still resolve IReference<Boolean> during pass 1.
                File(it).listFiles()!!.filter { file -> file.extension == "winmd" && !file.name.equals("Windows.winmd", true) }
            }.joinToString("\n") { "file\tWindowsSdk\t${encoded("fixture")}\t${encoded(it.absolutePath)}" })
        }
        val task = project.tasks.create("analyze", CompileWinRTXamlTask::class.java)
        task.sourceRoots.from(sources)
        task.preparedMetadataManifest.set(manifest)
        task.compilerDirectory.set(File(compiler!!).parentFile)
        task.genXbfDirectory.set(File(genXbf!!))
        task.projectName.set("fixture")
        task.outputDirectory.set(File(root, "output"))
        val referenceFiles = refs!!.split(File.pathSeparator).flatMap {
            File(it).listFiles()!!.filter { file -> file.extension == "winmd" }
        }
        val header = File(root, "Header.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd(
            "Header", listOf(WinRTXamlApplicationTypeDescriptor(
                "sample.MainPage", "Microsoft.UI.Xaml.Controls.Page")), emptyMap(), header.toPath(),
            WinRTMetadataLoader.loadTypeAssemblyNames(referenceFiles.map { it.toPath() }))
        task.applicationHeaderWinmd.set(header)
        task.compile()
        val index = WinRTXamlDeclarations.parse(task.declarationsFile.get().asFile.readText())
        assertEquals("pages/MainPage.xaml", index.pages.single().resourcePath)
        assertEquals(WinRTXamlDeclarations.sourceFingerprint(page.readText()), index.pages.single().sourceHash)
        assertEquals(index, WinRTXamlDeclarations.readCompilerOutput(task.implementationFile.get().asFile.toPath()))
        val originalMarkup = page.readText()
        // XAMLC's harvester contract stays stable while the runtime/IDE fingerprint tracks
        // the current source. The isolated Kotlin semantic compiler consumes only the former.
        val semanticBefore = task.semanticDeclarationsFile.get().asFile.readText()
        page.writeText(originalMarkup.replace("IsChecked=\"True\"", "IsChecked=\"False\""))
        task.compile()
        assertEquals(semanticBefore, task.semanticDeclarationsFile.get().asFile.readText())
        assertNotEquals(index.pages.single().sourceHash,
            WinRTXamlDeclarations.parse(task.declarationsFile.get().asFile.readText()).pages.single().sourceHash)
        page.writeText(originalMarkup.replace("x:Name=\"checked\"", "x:Name=\"renamedControl\""))
        task.compile()
        val modified = WinRTXamlDeclarations.parse(task.declarationsFile.get().asFile.readText())
        assertEquals(WinRTXamlDeclarations.sourceFingerprint(page.readText()), modified.pages.single().sourceHash)
        assertNotEquals(index.pages.single().sourceHash, modified.pages.single().sourceHash)
        assertTrue(modified.pages.single().connections.any { it.fieldName == "renamedControl" })
        assertTrue(modified.pages.single().connections.none { it.fieldName == "checked" })
        assertNotEquals(semanticBefore, task.semanticDeclarationsFile.get().asFile.readText())
        val renamed = File(page.parentFile, "RenamedPage.xaml")
        page.copyTo(renamed)
        page.delete()
        File(page.parentFile, "MainPage.kt").renameTo(File(page.parentFile, "RenamedPage.kt"))
        task.compile()
        assertEquals("pages/RenamedPage.xaml",
            WinRTXamlDeclarations.parse(task.declarationsFile.get().asFile.readText()).pages.single().resourcePath)
        assertFalse(File(root, "output/compiled/pages/MainPage.xaml").exists())
        assertFalse(File(root, "output/compiled/pages/MainPage.xbf").exists())
        val stale = File(root, "output/compiled/pages/obsolete.xbf").apply { parentFile.mkdirs(); writeText("stale") }
        renamed.delete()
        task.compile()
        assertTrue(WinRTXamlDeclarations.parse(task.declarationsFile.get().asFile.readText()).pages.isEmpty())
        assertFalse(stale.exists())
        page.writeText("<Page invalid")
        assertTrue(runCatching { task.compile() }.isFailure)
        assertFalse(task.declarationsFile.get().asFile.exists())
        assertFalse(task.semanticDeclarationsFile.get().asFile.exists())
    }

    // Native KotlinXamlDeclarationWriter.Create/ValidateSymbols owns the per-class declaration
    // and handler contracts. Exercise both passes against it, including partial final validation.
    @Test fun reuses_unchanged_pages_repairs_corrupt_xbf_and_keeps_native_semantic_validation() {
        val compiler = System.getenv("WINRT_TEST_XAMLC")
        val genXbf = System.getenv("WINRT_TEST_GENXBF")
        val referenceRoots = System.getenv("WINRT_TEST_XAMLC_REFERENCES")
        assumeTrue(compiler != null && genXbf != null && referenceRoots != null)
        val root = Files.createTempDirectory("xaml-pages-").toFile()
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val sources = File(root, "src/pages").apply { mkdirs() }
        val references = referenceRoots!!.split(File.pathSeparator).flatMap {
            File(it).listFiles()!!.filter { file -> file.extension.equals("winmd", true) }
        }
        fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val manifest = File(root, "resolved.tsv").apply {
            writeText("kotlin-winrt-prepared-metadata-v1\n" + references
                .filterNot { it.name.equals("Windows.winmd", true) }.joinToString("\n") {
                    "file\tWindowsSdk\t${encoded("fixture")}\t${encoded(it.absolutePath)}"
                })
        }
        val names = listOf("MainPage", "OtherPage")
        val pages = names.map { name ->
            File(sources, "$name.kt").writeText("package sample; class $name")
            File(sources, "$name.xaml").apply {
                writeText("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.$name"><TextBlock x:Name="label" Text="alpha"/></Page>""")
            }
        }
        val header = File(root, "Header.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Header", names.map {
            WinRTXamlApplicationTypeDescriptor("sample.$it", "Microsoft.UI.Xaml.Controls.Page")
        }, emptyMap(), header.toPath(), WinRTMetadataLoader.loadTypeAssemblyNames(references.map { it.toPath() }))
        fun task(name: String) = project.tasks.create(name, CompileWinRTXamlTask::class.java).apply {
            sourceRoots.from(sources.parentFile)
            preparedMetadataManifest.set(manifest)
            compilerDirectory.set(File(compiler!!).parentFile)
            genXbfDirectory.set(File(genXbf!!))
            projectName.set("fixture")
            outputDirectory.set(File(root, name))
            applicationHeaderWinmd.set(header)
        }
        val analyze = task("analyze")
        val final = task("final")
        val symbols = File(root, "symbols.json")
        fun updateSymbols() {
            val declarations = Json.parseToJsonElement(analyze.semanticDeclarationsFile.get().asFile.readText())
            val plan = WinRTXamlDeclarations.parse(declarations.toString())
            symbols.writeText(buildJsonObject {
                put("SchemaVersion", plan.schemaVersion)
                put("DeclarationFingerprint", WinRTXamlDeclarations.fingerprint(plan))
                put("Declarations", declarations)
                put("Pages", JsonArray(plan.pages.map { page -> buildJsonObject {
                    put("ClassName", page.className); put("Handlers", JsonArray(emptyList()))
                } }))
            }.toString())
        }
        fun invoked(task: CompileWinRTXamlTask) = Json.parseToJsonElement(
            File(task.outputDirectory.get().asFile, "incremental-input.json").readText()).jsonObject
            .getValue("XamlPages").jsonArray.map { it.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content }
        analyze.compile()
        updateSymbols()
        final.semanticSymbols.set(symbols)
        final.semanticWinmd.set(header)
        final.compile()
        assertEquals(listOf("pages/MainPage.xaml", "pages/OtherPage.xaml"), invoked(final))
        val unchangedXbf = File(root, "final/compiled/pages/MainPage.xbf")
        val unchangedTime = Files.getLastModifiedTime(unchangedXbf.toPath())
        val semanticBefore = analyze.semanticDeclarationsFile.get().asFile.readText()
        pages[1].writeText(pages[1].readText().replace("alpha", "gamma"))
        analyze.compile()
        assertEquals(listOf("pages/OtherPage.xaml"), invoked(analyze))
        assertEquals(semanticBefore, analyze.semanticDeclarationsFile.get().asFile.readText())
        final.compile()
        assertEquals(listOf("pages/OtherPage.xaml"), invoked(final))
        assertEquals(unchangedTime, Files.getLastModifiedTime(unchangedXbf.toPath()))
        assertEquals(2, WinRTXamlDeclarations.readCompilerOutput(final.implementationFile.get().asFile.toPath()).pages.size)
        val damaged = final.pageCacheDirectory.get().asFile.walkTopDown().single { it.name == "MainPage.xbf" }
        damaged.writeText("corrupt")
        final.compile()
        assertEquals(listOf("pages/MainPage.xaml"), invoked(final))
        assertArrayEquals(unchangedXbf.readBytes(), damaged.readBytes())
        pages[1].writeText(pages[1].readText().replace("x:Name=\"label\"", "x:Name=\"other\""))
        assertTrue(runCatching { final.compile() }.isFailure)
        assertFalse(final.declarationsFile.get().asFile.exists())
        analyze.compile()
        updateSymbols()
        final.compile()
        assertEquals(2, invoked(final).size)
        // A shared dictionary uses the native full batch until its dependency graph is exported.
        File(sources, "Shared.xaml").writeText("""<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><x:String x:Key="Caption">hello</x:String></ResourceDictionary>""")
        analyze.compile()
        assertEquals(3, invoked(analyze).size)
        pages[1].appendText("\n")
        analyze.compile()
        assertEquals(3, invoked(analyze).size)
    }
}
