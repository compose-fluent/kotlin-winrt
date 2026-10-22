package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

/** Fixed designer package assets; never read application manifests, resources or source. */
@CacheableTask
abstract class PrepareUwpSdkPreviewAssetsTask : DefaultTask() {
    @get:Input abstract val identity: Property<String>
    @get:Input abstract val architecture: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val root = outputDirectory.get().asFile.toPath()
        Files.createDirectories(root.resolve("Assets"))
        for ((name, size) in listOf("StoreLogo" to 50, "Square150x150Logo" to 150, "Square44x44Logo" to 44)) {
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                graphics.color = Color(0x00, 0x6c, 0x67); graphics.fillRect(0, 0, size, size)
                graphics.color = Color.WHITE
                graphics.drawRect(size / 4, size / 4, size / 2, size / 2)
            } finally { graphics.dispose() }
            ImageIO.write(image, "png", root.resolve("Assets/$name.png").toFile())
        }
        require(identity.get().matches(Regex("[A-Za-z0-9.-]+")))
        require(architecture.get() in setOf("x64", "arm64", "x86"))
        Files.writeString(root.resolve("AppxManifest.xml"), """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10" IgnorableNamespaces="uap">
              <Identity Name="${identity.get()}" Publisher="CN=KotlinWinRT Development" Version="1.0.0.0" ProcessorArchitecture="${architecture.get()}"/>
              <Properties><DisplayName>Kotlin WinRT UWP Designer</DisplayName><PublisherDisplayName>Kotlin WinRT</PublisherDisplayName><Logo>Assets\StoreLogo.png</Logo></Properties>
              <Dependencies><TargetDeviceFamily Name="Windows.Universal" MinVersion="10.0.19041.0" MaxVersionTested="10.0.26100.0"/></Dependencies>
              <Resources><Resource Language="en-us"/></Resources>
              <Applications><Application Id="App" Executable="KotlinWinUIXamlPreview.exe" EntryPoint="io.github.composefluent.winrt.generated.xaml.KotlinWinUIXamlPreviewApplication">
                <uap:VisualElements DisplayName="Kotlin WinRT UWP Designer" Description="SDK-only XAML designer" BackgroundColor="#006C67"
                    Square150x150Logo="Assets\Square150x150Logo.png" Square44x44Logo="Assets\Square44x44Logo.png"/>
              </Application></Applications>
              <!-- Kotlin/Native Win64 ABI call stubs use VirtualProtectFromApp. -->
              <Capabilities><Capability Name="codeGeneration" /></Capabilities>
            </Package>
        """.trimIndent())
    }
}
