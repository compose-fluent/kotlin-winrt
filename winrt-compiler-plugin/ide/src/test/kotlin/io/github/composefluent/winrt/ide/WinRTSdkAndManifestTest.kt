package io.github.composefluent.winrt.ide

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.resources.WinRTManifestSchemaProvider
import io.github.composefluent.winrt.ide.resources.WinRTXmlFormEditorProvider
import io.github.composefluent.winrt.ide.templates.WinRTInstalledSdks
import io.github.composefluent.winrt.ide.templates.WinRTInstalledSdkState
import io.github.composefluent.winrt.metadata.WindowsSdkRootDiscovery
import java.nio.file.Files
import java.nio.file.Path

class WinRTSdkAndManifestTest : BasePlatformTestCase() {
    fun testInstalledSdkInventoryRequiresToolsAndMetadataAndSortsNumerically() {
        // .cswinrt/src/cswinrt/cmd_reader.h: installed KitsRoot10, not a NuGet WinMD package.
        val root = Files.createTempDirectory("winrt-sdk-inventory-")
        try {
            listOf("10.0.9000.0", "10.0.26100.0", "10.0.28000.0").forEach { version ->
                listOf("Include/$version/um/Windows.h", "UnionMetadata/$version/Windows.winmd",
                    "bin/$version/x64/makeappx.exe").forEach { name ->
                    val file = root.resolve(name); Files.createDirectories(file.parent); Files.writeString(file, "")
                }
                Files.createDirectories(root.resolve("Lib/$version"))
            }
            Files.delete(root.resolve("bin/10.0.28000.0/x64/makeappx.exe"))
            assertEquals(listOf("10.0.26100.0", "10.0.9000.0"), WinRTInstalledSdks.read(listOf(root)).map { it.version })
            assertTrue(WinRTInstalledSdks.read(listOf(root.resolve("missing"))).isEmpty())
        } finally { root.toFile().deleteRecursively() }
    }

    fun testManifestFormIsAvailableDuringIndexingAndPrecedesSource() {
        val provider = WinRTXmlFormEditorProvider()
        listOf("AppxManifest.xml", "appxmanifest.xml", "Package.appxmanifest").forEach { name ->
            assertTrue(provider.accept(project, myFixture.addFileToProject(name, "<Package/>").virtualFile))
        }
        assertFalse(provider.accept(project, myFixture.addFileToProject("Other.xml", "<root/>").virtualFile))
        assertEquals(FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR, provider.policy)
        assertTrue(provider is com.intellij.openapi.project.DumbAware)
    }

    fun testRealWindowsSdkSchemasResolveManifestExtensionsAndKeepUnknownAttributeErrors() {
        val sdks = WinRTInstalledSdks.read(WindowsSdkRootDiscovery.candidateRootsWithRegistry())
        assertTrue("This Windows validation needs an installed SDK", sdks.isNotEmpty())
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(testRootDisposable, *sdks.map { it.root.toString() }.toTypedArray())
        sdks.flatMap { it.schemas.values }.forEach { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        val catalog = service<WinRTInstalledSdks>()
        kotlinx.coroutines.runBlocking { catalog.initialization.join() }
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        catalog.state.value = WinRTInstalledSdkState(sdks, false)
        val checkout = Path.of(System.getProperty("winrt.ide.toolchain"))
        val text = Files.readString(checkout.resolve("winui-gallery/src/winuiMain/appxResources/AppxManifest.xml"))
        val xml = myFixture.configureByText("AppxManifest.xml", text) as XmlFile
        val provider = WinRTManifestSchemaProvider()
        assertNotNull(provider.getSchema(xml.rootTag!!.namespace, null, xml))
        listOf("uap", "desktop", "com", "rescap").forEach { prefix ->
            val ns = xml.rootTag!!.getNamespaceByPrefix(prefix)
            assertNotNull("$prefix: $ns; schemas=${sdks.first().schemas.keys}", provider.getSchema(ns, null, xml))
        }
        val errors = myFixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }
        assertEquals("Valid Gallery manifest: ${errors.map { it.description }}", emptyList<Any>(), errors)
        myFixture.configureByText("Package.appxmanifest", text.replace("ProcessorArchitecture=", "UnknownArchitecture="))
        assertTrue("Unknown attributes must still be diagnosed", myFixture.doHighlighting().any { it.severity == HighlightSeverity.ERROR })
    }
}
