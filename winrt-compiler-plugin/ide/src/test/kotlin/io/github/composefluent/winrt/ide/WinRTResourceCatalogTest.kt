package io.github.composefluent.winrt.ide

import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.resources.WinRTResourceCatalog
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WinRTResourceCatalogTest {
    @Test fun stagedInventoryRetainsArchiveOwnerOverridesAndActualPriQualifiers() {
        val root = Files.createTempDirectory("winrt-resource-catalog-")
        val app = WinRTModuleData(":app", root.resolve("app").toString(), root.resolve("app/build").toString(), "", "", emptyList(), emptyList(), emptyList(), emptyList())
        val library = app.copy(projectPath = ":icons", projectDirectory = root.resolve("icons").toString(), buildDirectory = root.resolve("icons/build").toString())
        val report = root.resolve("report.json")
        val archive = library.buildDirectory.replace('\\', '/') + "/icons.appxresources.zip"
        Files.writeString(report, """{"schemaVersion":1,"packageRootRelative":true,"entries":[{"target":"Assets/Logo.scale-200.png","source":"temporary/logo.png","origin":"dependency AppX resource","sourceArchive":"$archive","overriddenSources":["base/logo.png"]}],"generatedPri":{"priMappings":[{"resourceUri":"ms-resource:///Files/Assets/Logo.png","candidateType":"Path","value":"Assets/Logo.scale-200.png","qualifiers":"Scale=200"}]}}""")
        val layout = WinRTPackageLayoutData("stage", "jvm_main", root.resolve("package").toString(), report.toString(), "", "")
        val inventory = WinRTResourceCatalog.staged(app, layout, listOf(app, library))
        assertNull(inventory.error)
        assertEquals(":icons", inventory.entries.single().owner)
        assertEquals(listOf("base/logo.png"), inventory.entries.single().overrides)
        assertEquals("Scale=200", inventory.candidates.single().qualifiers)
        assertEquals("Assets/Logo.png", WinRTResourceCatalog.family(inventory.entries.single().target))
        Files.writeString(report, Files.readString(report).replace("Assets/Logo.scale-200.png", "../outside.png"))
        assertNotNull(WinRTResourceCatalog.staged(app, layout, listOf(app, library)).error)
    }
}
