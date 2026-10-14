package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinUIXamlDeclarations
import io.github.composefluent.winrt.runtime.XamlFramework
import kotlinx.serialization.json.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class XamlApplicationItemsTest {
    @Test
    fun uwp_application_resources_use_the_system_startup_path_without_renaming_pages() {
        val root = Files.createTempDirectory("xaml-package-paths-").toFile()
        try {
            for (path in listOf("views/CustomApplication.xaml", "views/CustomApplication.xbf", "views/MainPage.xbf")) {
                root.resolve(path).apply { parentFile.mkdirs(); writeText(path) }
            }
            fun plan(framework: String) = WinUIXamlDeclarations.parse("""{"SchemaVersion":3,"Framework":"$framework","Pages":[{"ClassName":"sample.App","ResourcePath":"views/CustomApplication.xaml","BaseTypeName":"${if (framework == "uwp") "Windows" else "Microsoft"}.UI.Xaml.Application","IsApplication":true,"Features":[],"Connections":[]}],"Resources":[]}""")
            // Windows SDK CppWinRT_AppPass1 leaves InitializeComponent empty;
            // the UWP runtime itself resolves Files/App.xaml during startup.
            assertEquals(setOf("App.xaml", "App.xbf", "views/MainPage.xbf"),
                xamlPackageResources(root, plan("uwp")).values.toSet())
            assertEquals(setOf("views/CustomApplication.xaml", "views/CustomApplication.xbf", "views/MainPage.xbf"),
                xamlPackageResources(root, plan("winui")).values.toSet())
            root.resolve("App.xbf").writeText("another resource")
            assertThrows(IllegalArgumentException::class.java) { xamlPackageResources(root, plan("uwp")) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolved_application_items_are_separate_from_pages_and_keep_resource_paths() {
        // XAMLC SourceFileManager maps XamlApplications to IsApplication; neither
        // the file name App.xaml nor the project's OutputType determines that flag.
        val root = Files.createTempDirectory("xaml-application-items-").toFile()
        val application = root.resolve("views/CustomApplication.xaml")
        val page = root.resolve("views/App.xaml")
        val items = xamlCompilerSourceItems(mapOf("views/App.xaml" to page,
            "views/CustomApplication.xaml" to application), listOf(application))
        assertEquals(listOf("views/CustomApplication.xaml"), items.links("XamlApplications"))
        assertEquals(listOf("views/App.xaml"), items.links("XamlPages"))
        assertEquals(application.absolutePath, items.getValue("XamlApplications").jsonArray.single()
            .jsonObject.getValue("FullPath").jsonPrimitive.content)
        assertTrue(xamlCompilerSourceItems(mapOf("views/App.xaml" to page), emptyList())
            .getValue("XamlApplications").jsonArray.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            xamlCompilerSourceItems(mapOf("views/App.xaml" to page), listOf(application))
        }
    }

    @Test
    fun application_classification_invalidates_old_page_cache_and_keeps_header_producer() {
        val project = ProjectBuilder.builder().build()
        val header = project.tasks.register("header", GenerateWinUIXamlApplicationHeaderTask::class.java) {
            it.sourceOutputDirectory.set(project.layout.buildDirectory.dir("header"))
        }
        val compile = project.tasks.create("compileMarkup", CompileWinUIXamlTask::class.java)
        compile.outputDirectory.set(project.layout.buildDirectory.dir("markup"))
        assertEquals(project.file("build/markup/compiled"), compile.packageResourcesDirectory.get().asFile)
        compile.xamlFramework.set(XamlFramework.UWP)
        assertEquals(project.file("build/markup/resources"), compile.packageResourcesDirectory.get().asFile)
        compile.applicationItemsFile.set(header.flatMap { it.applicationItemsFile })
        assertTrue(project.files(compile.applicationItemsFile).buildDependencies.getDependencies(compile).contains(header.get()))

        val app = project.file("CustomApplication.xaml")
        val sources = mapOf("CustomApplication.xaml" to app)
        val pageInput = xamlCompilerSourceItems(sources, emptyList())
        val appInput = xamlCompilerSourceItems(sources, listOf(app))
        assertNotEquals(WinUIXamlPageCache.context(pageInput, emptyList()),
            WinUIXamlPageCache.context(appInput, emptyList()))
        for ((namespace, framework) in listOf("Windows.UI.Xaml" to ",\"Framework\":\"uwp\"", "Microsoft.UI.Xaml" to "")) {
            val plan = WinUIXamlDeclarations.parse("""{"SchemaVersion":3,"Pages":[{"ClassName":"sample.App","ResourcePath":"CustomApplication.xaml","BaseTypeName":"$namespace.Application","IsApplication":true,"Features":[],"Connections":[]}],"Resources":[]$framework}""")
            assertFalse(WinUIXamlPageCache.canIsolate(plan))
        }
    }

    private fun JsonObject.links(name: String) = getValue(name).jsonArray.map {
        it.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content
    }
}
