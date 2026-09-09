package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

class StageWinRTApplicationPackagePriTest {
    @Test
    fun runtime_component_payload_is_copied_without_reindexing_its_pri_resources() {
        // Microsoft.WinUI.References.targets supplies component assets as ReferenceCopyLocalPaths,
        // not application Content. Preserve that distinction when merging component PRI files.
        assumeTrue(System.getProperty("os.name").contains("Windows", ignoreCase = true))
        val sdkMakePri = findWindowsSdk()?.tool("makepri.exe", "x64")
        assumeNotNull(sdkMakePri)
        val makePri = requireNotNull(sdkMakePri)
        val project = ProjectBuilder.builder().build()
        val root = project.projectDir.toPath()
        val componentRoot = root.resolve("component-pri")
        val componentImage = componentRoot.resolve("Contoso.Component/Assets/Noise_256X256.png")
        val componentXbf = componentRoot.resolve("embed/Contoso.Component/Themes/Generic.xbf")
        Files.createDirectories(componentImage.parent)
        Files.createDirectories(componentXbf.parent)
        Files.write(componentImage, byteArrayOf(0x50, 0x4e, 0x47))
        Files.write(componentXbf, byteArrayOf(0x58, 0x42, 0x46))
        val componentItems = setOf(
            applicationPackageItem(ApplicationPackageItemKind.Content, componentImage, componentImage),
            applicationPackageItem(ApplicationPackageItemKind.Embed, componentXbf, componentXbf),
        )
        val qualifiers = ProjectPriManifestSupport.fullIndexDefaultQualifiers("en-US", listOf("scale-100"))
        val componentOutput = root.resolve("component-output")
        Files.createDirectories(componentOutput)
        assertTrue(
            ProjectPriGenerator.generateApplicationPri(
                makePri, componentOutput, componentRoot, root.resolve("component-config"),
                "Contoso.Component", qualifiers, componentItems, project.logger,
            ),
        )
        val runtimeRoot = root.resolve("runtime-assets")
        // NuGet copy-local filenames need not have the exact casing stored in the component PRI.
        val runtimeImage = runtimeRoot.resolve("Contoso.Component/Assets/Noise_256x256.png")
        val runtimeXbf = runtimeRoot.resolve("Contoso.Component/Themes/Generic.xbf")
        Files.createDirectories(runtimeImage.parent)
        Files.createDirectories(runtimeXbf.parent)
        Files.copy(componentImage, runtimeImage)
        Files.copy(componentXbf, runtimeXbf)
        Files.copy(componentOutput.resolve("resources.pri"), runtimeRoot.resolve("Contoso.Component.pri"))
        val appxRoot = root.resolve("src/winuiMain/appxResources")
        val appLogo = appxRoot.resolve("Assets/Logo.png")
        Files.createDirectories(appLogo.parent)
        Files.write(appLogo, byteArrayOf(0x50, 0x4e, 0x47))
        Files.writeString(runtimeRoot.resolve("app.exe"), "test executable")
        Files.writeString(appxRoot.resolve("AppxManifest.xml"), """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                     xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" ProcessorArchitecture="x64" />
              <Properties><DisplayName>App</DisplayName><PublisherDisplayName>Contoso</PublisherDisplayName><Logo>Assets/Logo.png</Logo></Properties>
              <Applications><Application Id="App" Executable="app.exe" EntryPoint="Windows.FullTrustApplication">
                <uap:VisualElements DisplayName="App" Description="App" BackgroundColor="transparent"
                                   Square150x150Logo="Assets/Logo.png" Square44x44Logo="Assets/Logo.png" />
              </Application></Applications>
            </Package>
        """.trimIndent())
        val task = project.tasks.register(
            "stageComponentApplication", StageWinRTApplicationPackageTask::class.java,
        ) { registered ->
            registered.runtimeAssetsDirectory.set(runtimeRoot.toFile())
            registered.outputDirectory.set(root.resolve("package").toFile())
            registered.projectPriIndexName.set("Contoso.App")
            registered.projectPriDefaultLanguage.set("en-US")
            registered.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registered.enableDefaultProjectPriResources.set(false)
            registered.defaultAppxResourceRoots.set(listOf(appxRoot.toString()))
            registered.defaultAppxResourceFiles.from(appLogo)
            registered.makePriExecutable.set(makePri.toString())
            registered.runtimeIdentifier.set("win-x64")
            registered.minWindowsVersion.set("10.0.19041.0")
            registered.windowsSdkVersion.set("10.0.26100.0")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertArrayEquals(
            Files.readAllBytes(runtimeImage), Files.readAllBytes(outputRoot.resolve(runtimeRoot.relativize(runtimeImage))),
        )
        assertArrayEquals(
            Files.readAllBytes(runtimeXbf), Files.readAllBytes(outputRoot.resolve(runtimeRoot.relativize(runtimeXbf))),
        )
        val configRoot = task.temporaryDir.toPath().resolve("project-pri-config")
        assertEquals(listOf("Assets/Logo.png"), Files.readAllLines(configRoot.resolve("filtered.layout.resfiles")))
        assertTrue(Files.readAllLines(configRoot.resolve("embed/embed.resfiles")).isEmpty())
        assertEquals(listOf("Contoso.Component.pri"), Files.readAllLines(configRoot.resolve("pri.resfiles")))
        val mappings = PriResourceMapValidator.readDump(task.temporaryDir.toPath().resolve("resources.pri.dump.xml"))
        val imageMapping = mappings.single { it.resourceUri.endsWith("Noise_256X256.png", ignoreCase = true) }
        assertEquals("Path", imageMapping.candidateType)
        assertEquals("Contoso.Component/Assets/Noise_256X256.png", imageMapping.value?.replace('\\', '/'))
        assertEquals("EmbeddedData", mappings.single { it.resourceUri.endsWith("Generic.xbf") }.candidateType)
        assertEquals("Path", mappings.single { it.resourceUri.endsWith("Assets/Logo.png") }.candidateType)
        assertTrue(PriResourceMapValidator.validate(mappings, outputRoot).isEmpty())

        val originalPri = Files.readAllBytes(outputRoot.resolve("resources.pri"))
        val originalManifest = Files.readString(outputRoot.resolve("AppxManifest.xml"))
        val developmentRoot = root.resolve("package-dev")
        task.runtimeAssetsDirectory.set(outputRoot.toFile())
        task.outputDirectory.set(developmentRoot.toFile())
        task.developmentIdentity.set(true)
        task.stage()

        assertArrayEquals(originalPri, Files.readAllBytes(outputRoot.resolve("resources.pri")))
        assertEquals(originalManifest, Files.readString(outputRoot.resolve("AppxManifest.xml")))
        assertTrue(Files.readString(developmentRoot.resolve("AppxManifest.xml")).contains("Name=\"Contoso.App.dev\""))
        val developmentMappings = PriResourceMapValidator.readDump(task.temporaryDir.toPath().resolve("resources.pri.dump.xml"))
        assertTrue(developmentMappings.single { it.resourceUri.endsWith("Assets/Logo.png") }
            .resourceUri.startsWith("ms-resource://Contoso.App.dev/", ignoreCase = true))
        assertEquals("EmbeddedData", developmentMappings.single { it.resourceUri.endsWith("Generic.xbf") }.candidateType)
        assertTrue(PriResourceMapValidator.validate(developmentMappings, developmentRoot).isEmpty())
        assertArrayEquals(
            Files.readAllBytes(outputRoot.resolve("Contoso.Component.pri")),
            Files.readAllBytes(developmentRoot.resolve("Contoso.Component.pri")),
        )
    }
}
