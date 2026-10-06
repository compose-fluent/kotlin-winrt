package io.github.composefluent.winrt.ide

import com.google.gson.JsonParser
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.nuget.*
import java.net.URI
import java.nio.file.Files

class WinRTNuGetTest : BasePlatformTestCase() {
    fun testDependencyUpdatePreservesOptionsAndUnsavedSourceWithUndo() {
        val original = """
            windows {
                packageReferences {
                    // keep dependency policy
                    nugetPackage("Sample", "1.0.0") { generateProjection = false }
                    namespace("Sample.Controls")
                }
            }
        """.trimIndent()
        val file = myFixture.addFileToProject("build.gradle.kts", original)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        WinRTNuGetDependencies.apply(project, file.virtualFile, "Sample", "2.0.0", true)
        assertTrue(myFixture.editor.document.text.contains("nugetPackage(\"Sample\", \"2.0.0\") { generateProjection = false }"))
        assertTrue(myFixture.editor.document.text.contains("// keep dependency policy"))
        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
        assertEquals(original, myFixture.editor.document.text)
        WinRTNuGetDependencies.apply(project, file.virtualFile, "New.Package", "1.2.3-beta.1", false)
        assertTrue(myFixture.editor.document.text.contains(WinRTNuGetDependencies.declaration("New.Package", "1.2.3-beta.1", false)))
        WinRTNuGetDependencies.apply(project, file.virtualFile, "Sample", null, false)
        assertFalse(myFixture.editor.document.text.contains("nugetPackage(\"Sample\""))
        assertTrue(myFixture.editor.document.text.contains("namespace(\"Sample.Controls\")"))
    }

    fun testComputedAndConditionalDependenciesAreNeverRewritten() {
        listOf(
            "windows { packageReferences { nugetPackage(\"Sample\", versions.sample) } }",
            "windows { packageReferences { if (enabled) nugetPackage(\"Sample\", \"1\") } }",
            "windows { packageReferences { nugetPackage(packageName, \"1\") } }",
        ).forEachIndexed { index, text ->
            val file = myFixture.addFileToProject("module$index/build.gradle.kts", text)
            val failure = runCatching { WinRTNuGetDependencies.apply(project, file.virtualFile, "Sample", "2.0", false) }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(text, file.text)
        }
    }

    fun testAddingPackageReferencesKeepsTheExistingWindowsBlock() {
        val file = myFixture.addFileToProject("build.gradle.kts", "windows { application { /* keep */ } }")
        WinRTNuGetDependencies.apply(project, file.virtualFile, "Sample", "1.0.0", false)
        assertTrue(file.text.contains("application { /* keep */ }"))
        assertTrue(file.text.contains("packageReferences"))
        assertEquals(1, Regex("windows ").findAll(file.text).count())
    }

    fun testNuGetV3DiscoverySearchAndVersionFiltering() {
        val requested = mutableListOf<URI>()
        val browser = WinRTNuGetBrowser(WinRTNuGetSource("test", "https://packages.test/v3/index.json", null)) { uri ->
            requested += uri
            JsonParser.parseString(when {
                uri.path.endsWith("/v3/index.json") -> """{"resources":[{"@type":["SearchQueryService/3.5.0"],"@id":"https://search.test/query"},{"@type":"PackageBaseAddress/3.0.0","@id":"https://packages.test/flat/"}]}"""
                uri.host == "search.test" -> """{"data":[{"id":"Sample.Package","version":"2.0.0","description":"Controls","authors":["Author"],"versions":[{"version":"1.0.0"},{"version":"2.0.0"}]}]}"""
                else -> """{"versions":["1.0.0","2.0.0-beta","2.0.0"]}"""
            }).asJsonObject
        }
        val result = browser.search("Text Box & Grid", false).single()
        assertEquals("Sample.Package", result.id)
        assertEquals("Author", result.authors)
        assertTrue(requested.last().rawQuery.contains("q=Text+Box+%26+Grid"))
        assertTrue(requested.last().rawQuery.contains("semVerLevel=2.0.0"))
        assertEquals(listOf("2.0.0", "1.0.0"), browser.versions(result.id, false))
        assertEquals("/flat/sample.package/index.json", requested.last().path)
        assertEquals(listOf("2.0.0", "2.0.0-beta", "1.0.0"), browser.versions(result.id, true))
    }

    fun testSourceHierarchyClearDisableAndCredentialIsolation() {
        val root = Files.createTempDirectory("winrt-ide-nuget-")
        try {
            val user = Files.createDirectories(root.resolve("user"))
            val base = Files.createDirectories(root.resolve("project/sub"))
            Files.writeString(user.resolve("NuGet.Config"), """<configuration><packageSources><add key="old" value="https://old.test/v3/index.json"/></packageSources></configuration>""")
            Files.writeString(base.parent.resolve("NuGet.Config"), """<configuration><packageSources><clear/><add key="private" value="https://private.test/v3/index.json"/><add key="disabled" value="https://disabled.test/v3/index.json"/></packageSources><disabledPackageSources><add key="disabled" value="true"/></disabledPackageSources><packageSourceCredentials><private><add key="Username" value="test-user"/><add key="ClearTextPassword" value="test-password"/></private></packageSourceCredentials></configuration>""")
            val source = WinRTNuGetSources.read(base, user, null).single()
            assertEquals("private", source.name)
            assertNotNull(source.authorize(URI("https://private.test/search")))
            assertNull(source.authorize(URI("https://other.test/search")))
            assertNull(source.authorize(URI("http://private.test/search")))
            assertFalse(source.toString().contains("password"))
        } finally { root.toFile().deleteRecursively() }
    }

    fun testBrowserUsesTheIdeHttpClientWithoutFollowingCredentialRedirects() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0)
        val address = "http://localhost:${server.address.port}"
        fun respond(path: String, text: String) = server.createContext(path) { exchange ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        respond("/index.json", """{"resources":[{"@type":"SearchQueryService/3.5.0","@id":"$address/query"}]}""")
        respond("/query", """{"data":[{"id":"Sample","version":"1.0.0","authors":[],"description":"Local HTTP"}]}""")
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "$address/index.json")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.start()
        try {
            assertEquals("Sample", WinRTNuGetBrowser(WinRTNuGetSource("local", "$address/index.json", null)).search("Sample", false).single().id)
            assertNotNull(runCatching { WinRTNuGetBrowser(WinRTNuGetSource("local", "$address/redirect", null)).search("Sample", false) }.exceptionOrNull())
        }
        finally { server.stop(0) }
    }

    fun testRestoreInventoryUsesTheSharedLockAndReportsStaleVersions() {
        val root = Files.createTempDirectory("winrt-ide-restored-")
        try {
            val cache = Files.createDirectories(root.resolve("cache"))
            val pkg = Files.createDirectories(cache.resolve("sample/1.0.0"))
            Files.writeString(pkg.resolve(".nupkg.metadata"), """{"source":"https://packages.test/v3/index.json"}""")
            val lock = root.resolve("winmds.lock.json")
            Files.writeString(lock, """{"schema":3,"nuget_cache_dir":${com.google.gson.Gson().toJson(cache.toString())},"packages":[{"name":"Sample","version":"1.0.0","winmds":[]}]}""")
            val module = io.github.composefluent.winrt.ide.gradle.WinRTModuleData(":app", root.toString(), root.resolve("build").toString(),
                "2.4.0", "", emptyList(), emptyList(), listOf(io.github.composefluent.winrt.ide.gradle.WinRTNuGetData("Sample", "2.0.0", false)),
                emptyList(), restoreLockFiles = listOf(lock.toString()))
            val status = WinRTNuGetInventoryReader.read(module).packages.single()
            assertEquals(pkg, status.root)
            assertTrue(status.direct); assertEquals(false, status.projection)
            assertTrue(status.problems.single().contains("Declared 2.0.0"))
            assertEquals("https://packages.test/v3/index.json", status.source)
        } finally { root.toFile().deleteRecursively() }
    }
}
