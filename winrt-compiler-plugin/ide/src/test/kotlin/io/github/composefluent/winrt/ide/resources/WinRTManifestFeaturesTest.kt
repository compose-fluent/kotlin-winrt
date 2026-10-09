package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.templates.*
import io.github.composefluent.winrt.metadata.WindowsSdkRootDiscovery
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Manifest ownership follows the actual Windows SDK schemas; these are PSI,
 * image-family and Windows API validations, not projection/runtime rules. */
class WinRTManifestFeaturesTest : BasePlatformTestCase() {
    private fun manifest() = myFixture.addFileToProject("AppxManifest.xml", WinRTTemplates.module(
        WinRTTemplateOptions("app", "sample.app", WinRTTemplateKind.WinUIApplication))
        .getValue("src/main/appxResources/AppxManifest.xml").toString(Charsets.UTF_8)
        .replace("</Package>", "<!--keep--></Package>")) as XmlFile

    fun testOptionalApplicationFieldsCreateNamespacedNodesOnlyAfterEditing() {
        val xml = manifest()
        fun field(name: String) = WinRTXmlForms.snapshot(xml).fields.single { it.attribute == name }
        assertFalse(xml.text.contains("TrustLevel="))
        val trust = field("TrustLevel")
        assertEquals(WinRTManifestFields.UAP10, trust.attributeNamespace)
        WinRTXmlForms.set(project, xml.virtualFile, trust, "mediumIL")
        val app = xml.rootTag!!.findFirstSubTag("Applications")!!.findFirstSubTag("Application")!!
        assertEquals("mediumIL", app.getAttributeValue("TrustLevel", WinRTManifestFields.UAP10))
        assertEquals("", app.getAttributeValue("TrustLevel").orEmpty())
        WinRTXmlForms.set(project, xml.virtualFile, field("Recurrence"), "hour")
        WinRTXmlForms.set(project, xml.virtualFile, field("UriTemplate"), "https://example.com/tile")
        assertTrue(xml.text.contains("TileUpdate"))
        assertEquals("hour", field("Recurrence").value)
        assertTrue(xml.text.contains("<!--keep-->"))
        try { WinRTXmlForms.set(project, xml.virtualFile, trust, "appContainer"); fail("Stale values must be rejected") } catch (_: IllegalArgumentException) { }
    }

    fun testRotationAndTileNameSelectionsDoNotReplaceOtherEntries() {
        val xml = manifest()
        listOf("landscape", "portrait").forEach { WinRTXmlForms.setSelection(project, xml.virtualFile, 0, true, it, true) }
        listOf("square150x150Logo", "wide310x150Logo").forEach { WinRTXmlForms.setSelection(project, xml.virtualFile, 0, false, it, true) }
        assertEquals(setOf("landscape", "portrait"), WinRTXmlForms.snapshot(xml).fields.filter { it.attribute == "Preference" }.map { it.value }.toSet())
        assertEquals(2, WinRTXmlForms.snapshot(xml).fields.count { it.attribute == "Tile" })
        WinRTXmlForms.setSelection(project, xml.virtualFile, 0, true, "landscape", false)
        assertEquals(listOf("portrait"), WinRTXmlForms.snapshot(xml).fields.filter { it.attribute == "Preference" }.map { it.value })
    }

    fun testSdkCatalogProvidesCapabilitiesAndEditableDeclarationProperties() {
        val sdk = WinRTInstalledSdks.read(WindowsSdkRootDiscovery.candidateRootsWithRegistry()).first()
        val catalog = WinRTManifestCatalog.read(sdk.schemas)
        assertTrue(catalog.capabilities.map { it.name }.containsAll(listOf("documentsLibrary", "backgroundMediaPlayback", "runFullTrust", "location", "webcam", "bluetooth")))
        assertTrue(catalog.declarations.map { it.category }.containsAll(listOf("windows.protocol", "windows.fileTypeAssociation", "windows.appService", "windows.backgroundTasks", "windows.shareTarget")))
        val documents = catalog.capabilities.first { it.name == "documentsLibrary" }
        assertEquals(WinRTXmlForms.UAP, documents.namespace)
        val xml = manifest()
        WinRTXmlForms.addCapability(project, xml.virtualFile, catalog.capabilities.first { it.name == "webcam" })
        WinRTXmlForms.addCapability(project, xml.virtualFile, documents)
        val capability = WinRTXmlForms.snapshot(xml).fields.single { it.value == "documentsLibrary" }
        assertEquals(WinRTXmlForms.UAP, capability.path.last().namespace)
        assertEquals(listOf("Capability", "Capability", "DeviceCapability"), xml.rootTag!!.findFirstSubTag("Capabilities")!!.subTags.map { it.localName })
        val appService = catalog.declarations.first { it.category == "windows.appService" && it.namespace == WinRTXmlForms.UAP }
        WinRTXmlForms.addDeclaration(project, xml.virtualFile, 0, appService)
        var snapshot = WinRTXmlForms.snapshot(xml)
        val extension = snapshot.nodes.single { it.path.last().name == "Extension" }
        val name = catalog.declarationFields(snapshot, appService, extension).single { it.path.last().name == "AppService" && it.attribute == "Name" }
        WinRTXmlForms.set(project, xml.virtualFile, name, "sample.service")
        snapshot = WinRTXmlForms.snapshot(xml)
        assertTrue(snapshot.fields.any { it.attribute == "Name" && it.value == "sample.service" })
        assertTrue(xml.text.contains("<!--keep-->"))
        WinRTXmlForms.removeNode(project, xml.virtualFile, snapshot.nodes.single { it.path.last().name == "Extension" })
        assertFalse(xml.text.contains("windows.appService"))
    }

    fun testGeneratorProducesExactWindowsScaleSizesAndGuardsOverwrite() {
        val root = Files.createTempDirectory("winrt-manifest-generator-")
        try {
            val source = root.resolve("source.png")
            val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().let { graphics -> graphics.color = Color.RED; graphics.fillRect(0, 0, 400, 400); graphics.dispose() }
            ImageIO.write(image, "png", source.toFile())
            val kind = WinRTManifestAssetKind.Small
            val field = WinRTManifestFields.fields(1).single { it.attribute == "Square71x71Logo" }
            val request = WinRTManifestAssetRequest(0, source.toString(), "Assets", setOf(kind), setOf(125, 400), "Bicubic", true, false, false)
            val (files, edits) = WinRTManifestAssets.generate(root, request, mapOf(kind to field))
            assertEquals("Assets\\Square71x71Logo.png", edits[field])
            val scaled = ImageIO.read(ByteArrayInputStream(files.getValue("Assets/Square71x71Logo.scale-125.png")))
            assertEquals(89, scaled.width); assertEquals(89, scaled.height)
            assertEquals(0, scaled.getRGB(0, 0) ushr 24)
            assertEquals(Color.RED.rgb, scaled.getRGB(44, 44))
            Files.createDirectories(root.resolve("Assets"))
            Files.write(root.resolve(files.keys.first()), files.values.first())
            try { WinRTManifestAssets.generate(root, request, mapOf(kind to field)); fail("Existing images require explicit replacement") } catch (_: IllegalArgumentException) { }
            try { WinRTManifestAssets.generate(root, request.copy(target = "../outside"), mapOf(kind to field)); fail("Assets stay inside the package") } catch (_: IllegalArgumentException) { }
        } finally { root.toFile().deleteRecursively() }
    }

    fun testWindowsComputesThePackageFamilyName() {
        val publisher = "CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, S=Washington, C=US"
        assertEquals("SamplePackage_8wekyb3d8bbwe", WinRTPackageIdentity.familyName("SamplePackage", publisher))
        assertNull(WinRTPackageIdentity.familyName("", publisher))
    }

    fun testCertificateSelectionReadsThePublicSubjectWithWindowsSpelling() {
        // Exercise a public system root certificate, without generating,
        // exporting or accessing any private key material.
        val crypto = com.sun.jna.NativeLibrary.getInstance("crypt32")
        val convention = com.sun.jna.Function.ALT_CONVENTION
        val store = crypto.getFunction("CertOpenSystemStoreW", convention).invokePointer(arrayOf(com.sun.jna.Pointer.NULL, com.sun.jna.WString("ROOT")))!!
        val context = crypto.getFunction("CertEnumCertificatesInStore", convention).invokePointer(arrayOf(store, com.sun.jna.Pointer.NULL))!!
        val path = Files.createTempFile("winrt-public-certificate-", ".cer")
        try {
            val offset = if (com.sun.jna.Native.POINTER_SIZE == 8) 8L else 4L
            val encoded = context.getPointer(offset)
            val bytes = encoded.getByteArray(0, context.getInt(offset + com.sun.jna.Native.POINTER_SIZE))
            Files.write(path, bytes)
            val expected = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as java.security.cert.X509Certificate
            val subject = WinRTPackageIdentity.certificateSubject(path)
            assertEquals(expected.subjectX500Principal, javax.security.auth.x500.X500Principal(subject, mapOf("S" to "2.5.4.8", "E" to "1.2.840.113549.1.9.1")))
        } finally {
            Files.deleteIfExists(path)
            crypto.getFunction("CertFreeCertificateContext", convention).invokeInt(arrayOf(context))
            crypto.getFunction("CertCloseStore", convention).invokeInt(arrayOf(store, 0))
        }
    }
}
