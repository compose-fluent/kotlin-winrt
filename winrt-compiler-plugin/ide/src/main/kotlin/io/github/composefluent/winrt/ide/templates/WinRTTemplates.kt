package io.github.composefluent.winrt.ide.templates

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

enum class WinRTTemplateKind(val title: String, val application: Boolean = false, val xaml: Boolean = false) {
    ConsoleApplication("WinRT Console Application", application = true),
    WinUIApplication("WinUI XAML Application", application = true, xaml = true),
    WinRTLibrary("WinRT Library"),
    WinUIControlLibrary("WinUI Control Library", xaml = true),
    ResourceLibrary("AppX Resource Library"),
    ProjectionLibrary("Shared SDK Projection Library"),
}

data class WinRTTemplateOptions(
    val name: String,
    val packageName: String,
    val kind: WinRTTemplateKind,
    val sdkVersion: String = "10.0.26100.0",
    val appSdkVersion: String = "2.5.1",
    val packaged: Boolean = true,
    val dependencies: List<String> = emptyList(),
    val displayName: String = name,
    val projectionModule: String = ":winrt-projections",
    val includeWinUI: Boolean = kind.xaml,
) {
    fun validate() {
        require(name.matches(Regex("[A-Za-z](?:[A-Za-z0-9_.-]*[A-Za-z0-9_-])?")) && name.substringBefore('.').uppercase() !in reservedNames) {
            "Use a project/module name starting with a letter and containing letters, digits, dots, hyphens or underscores."
        }
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")) &&
            packageName.split('.').none { it in kotlinKeywords }) { "Enter a qualified Kotlin package name without keywords." }
        require(sdkVersion.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) { "Enter a four-part Windows SDK version." }
        require(appSdkVersion.matches(Regex("[0-9]+(?:\\.[0-9A-Za-z-]+)+"))) { "Enter a Windows App SDK package version." }
        require(dependencies.all { it.matches(Regex(":[A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z][A-Za-z0-9_.-]*)*")) }) {
            "Module dependencies must be Gradle project paths such as :controls."
        }
        require(projectionModule.isEmpty() || projectionModule.matches(Regex(":[A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z][A-Za-z0-9_.-]*)*"))) {
            "Select the Gradle module that owns SDK projections."
        }
        require(!kind.xaml || projectionModule.isNotEmpty()) { "WinUI templates require a shared SDK projection module." }
    }

    companion object {
        private val reservedNames = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
        private val kotlinKeywords = setOf("as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
            "try", "typealias", "typeof", "val", "var", "when", "while")
    }
}

/** Templates consume the Gradle/runtime/compiler owners; no generated projection is maintained here. */
object WinRTTemplates {
    /** Normal projects resolve the packaged Maven toolchain without a source checkout. */
    fun project(options: WinRTTemplateOptions): Map<String, ByteArray> {
        options.validate()
        val module = primaryModule(options.kind)
        val repository = "${WinRTBundledToolchain.DIRECTORY}/repository"
        val groups = listOf("io.github.compose-fluent", "io.github.compose-fluent.windows-toolkit", "io.github.composefluent.winrt")
            .joinToString("; ") { "includeGroup(${kotlinString(it)})" }
        return buildMap {
            putAll(WinRTBundledToolchain.files())
            text("settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        maven(url = uri("$repository")) { content { $groups } }
                        mavenCentral()
                        gradlePluginPortal()
                    }
                    plugins {
                        kotlin("jvm") version "2.4.0"
                        id("io.github.compose-fluent.windows-toolkit") version "${WinRTBundledToolchain.VERSION}"
                    }
                }
                rootProject.name = ${kotlinString(options.name)}
                include(":$module")
                ${if (module != "winrt-projections") "include(\":winrt-projections\")" else ""}
            """.trimIndent())
            text("build.gradle.kts", """
                allprojects {
                    repositories {
                        exclusiveContent {
                            forRepository { maven(url = rootProject.uri("$repository")) }
                            filter { $groups }
                        }
                        mavenCentral()
                    }
                }
            """.trimIndent())
            text("gradle.properties", "org.gradle.jvmargs=-Xmx4g\nkotlin.compiler.execution.strategy=in-process")
            text(".gitignore", ".gradle/\n.kotlin/\n.idea/\n**/build/\n*.iml")
            module(options.copy(name = module, projectionModule = ":winrt-projections")).forEach { (path, bytes) -> put("$module/$path", bytes) }
            if (module != "winrt-projections") module(options.copy(name = "winrt-projections", kind = WinRTTemplateKind.ProjectionLibrary,
                dependencies = emptyList(), projectionModule = "", includeWinUI = options.kind.xaml)).forEach { (path, bytes) -> put("winrt-projections/$path", bytes) }
        }
    }

    fun validateToolchain(checkout: Path) {
        listOf("windows-toolkit-gradle-plugin/settings.gradle.kts", "gradlew.bat", "gradlew",
            "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties").forEach {
            require(Files.isRegularFile(checkout.resolve(it))) { "Select a Kotlin WinRT checkout containing $it." }
        }
    }

    fun project(options: WinRTTemplateOptions, checkout: Path): Map<String, ByteArray> {
        options.validate()
        validateToolchain(checkout)
        val toolkit = kotlinString(checkout.toAbsolutePath().normalize().resolve("windows-toolkit-gradle-plugin").toString().replace('\\', '/'))
        val module = primaryModule(options.kind)
        return buildMap {
            text("settings.gradle.kts", """
                pluginManagement {
                    includeBuild($toolkit)
                    repositories { mavenCentral(); gradlePluginPortal() }
                    plugins { kotlin("jvm") version "2.4.0" }
                }
                rootProject.name = ${kotlinString(options.name)}
                include(":$module")
                ${if (module != "winrt-projections") "include(\":winrt-projections\")" else ""}
                includeBuild($toolkit) {
                    name = "winrt-toolchain"
                    dependencySubstitution {
                        substitute(module("io.github.compose-fluent:winrt-runtime")).using(project(":winrt-runtime"))
                        substitute(module("io.github.compose-fluent:winrt-authoring")).using(project(":winrt-authoring"))
                    }
                }
            """.trimIndent())
            text("build.gradle.kts", "allprojects { repositories { mavenCentral() } }")
            text("gradle.properties", "org.gradle.jvmargs=-Xmx4g\nkotlin.compiler.execution.strategy=in-process")
            text(".gitignore", ".gradle/\n.kotlin/\n.idea/\n**/build/\n*.iml")
            listOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
                .forEach { put(it, Files.readAllBytes(checkout.resolve(it))) }
            module(options.copy(name = module, projectionModule = ":winrt-projections")).forEach { (path, bytes) -> put("$module/$path", bytes) }
            if (module != "winrt-projections") module(options.copy(name = "winrt-projections", kind = WinRTTemplateKind.ProjectionLibrary,
                dependencies = emptyList(), projectionModule = "", includeWinUI = options.kind.xaml)).forEach { (path, bytes) -> put("winrt-projections/$path", bytes) }
        }
    }

    fun module(options: WinRTTemplateOptions): Map<String, ByteArray> {
        options.validate()
        val root = "src/main/kotlin/${options.packageName.replace('.', '/')}"
        return buildMap {
            text("build.gradle.kts", buildScript(options))
            when (options.kind) {
                WinRTTemplateKind.ConsoleApplication -> text("$root/Main.kt", """
                    package ${options.packageName}

                    import io.github.composefluent.winrt.runtime.HString
                    import io.github.composefluent.winrt.runtime.RuntimeScope

                    fun main() {
                        RuntimeScope.initializeSingleThreaded().use {
                            HString.create("Hello from Kotlin WinRT").use { message -> println(message.toKString()) }
                        }
                    }
                """.trimIndent())
                WinRTTemplateKind.WinUIApplication -> {
                    // .cswinrt/src/Samples/WinUIDesktopSample/App.xaml.cs owns this startup/window shape.
                    text("$root/Main.kt", """
                        package ${options.packageName}

                        import microsoft.ui.xaml.Application

                        private var application: App? = null

                        fun main() {
                            Application.start { application = App() }
                            application = null
                        }
                    """.trimIndent())
                    text("$root/App.kt", """
                        package ${options.packageName}

                        import microsoft.ui.xaml.Application
                        import microsoft.ui.xaml.LaunchActivatedEventArgs

                        class App : Application() {
                            private var window: MainWindow? = null

                            override fun onLaunched(args: LaunchActivatedEventArgs) {
                                window = MainWindow().also { it.activate() }
                            }
                        }
                    """.trimIndent())
                    text("$root/MainWindow.kt", """
                        package ${options.packageName}

                        import microsoft.ui.xaml.Window

                        class MainWindow : Window()
                    """.trimIndent())
                    text("$root/MainWindow.xaml", """
                        <Window x:Class="${options.packageName}.MainWindow"
                                xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                                xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
                            <StackPanel HorizontalAlignment="Center" VerticalAlignment="Center" Spacing="12">
                                <TextBlock x:Name="Greeting" Text="Hello from Kotlin WinRT" FontSize="28" />
                            </StackPanel>
                        </Window>
                    """.trimIndent())
                }
                WinRTTemplateKind.WinRTLibrary -> text("$root/Library.kt", """
                    package ${options.packageName}

                    object Library {
                        fun greeting(): String = "Hello from Kotlin WinRT"
                    }
                """.trimIndent())
                WinRTTemplateKind.WinUIControlLibrary -> {
                    text("$root/GreetingControl.kt", """
                        package ${options.packageName}

                        import microsoft.ui.xaml.controls.UserControl

                        class GreetingControl : UserControl()
                    """.trimIndent())
                    text("$root/GreetingControl.xaml", """
                        <UserControl x:Class="${options.packageName}.GreetingControl"
                                     xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                                     xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
                            <TextBlock x:Name="Greeting" Text="Hello from Kotlin WinRT" />
                        </UserControl>
                    """.trimIndent())
                }
                WinRTTemplateKind.ResourceLibrary -> text("src/main/appxResources/Strings/en-US/Resources.resw", """
                    <?xml version="1.0" encoding="utf-8"?>
                    <root>
                        <resheader name="resmimetype"><value>text/microsoft-resx</value></resheader>
                        <resheader name="version"><value>2.0</value></resheader>
                        <resheader name="reader"><value>System.Resources.ResXResourceReader, System.Windows.Forms</value></resheader>
                        <resheader name="writer"><value>System.Resources.ResXResourceWriter, System.Windows.Forms</value></resheader>
                        <data name="Greeting" xml:space="preserve"><value>Hello from Kotlin WinRT</value></data>
                    </root>
                """.trimIndent())
                WinRTTemplateKind.ProjectionLibrary -> Unit
            }
            if (options.kind.application) {
                val assets = applicationAssets()
                assets.forEach { (path, bytes) -> put("src/main/appxResources/$path", bytes) }
                put("src/main/appxResources/Assets/Application.ico", launcherIcon(assets))
                text("src/main/appxResources/AppxManifest.xml", manifest(options))
            }
        }
    }

    private fun buildScript(options: WinRTTemplateOptions): String = buildString {
        appendLine("import io.github.composefluent.windows.toolkit.gradle.WindowsPackageType")
        if (options.kind == WinRTTemplateKind.WinUIApplication && !options.packaged) {
            appendLine("import io.github.composefluent.windows.toolkit.gradle.WindowsAppSdkDeployment")
        }
        appendLine()
        appendLine("plugins {")
        appendLine("    kotlin(\"jvm\")")
        appendLine("    id(\"io.github.compose-fluent.windows-toolkit\")")
        appendLine("}")
        appendLine()
        appendLine("kotlin {")
        appendLine("    jvmToolchain(25)")
        if (options.kind == WinRTTemplateKind.ProjectionLibrary) appendLine("    compilerOptions { freeCompilerArgs.add(\"-Xno-optimize\") }")
        appendLine("}")
        val dependencies = options.dependencies + listOfNotNull(options.projectionModule.takeIf {
            it.isNotEmpty() && options.kind != WinRTTemplateKind.ProjectionLibrary
        })
        if (dependencies.isNotEmpty()) {
            appendLine("dependencies {")
            dependencies.distinct().forEach { appendLine("    implementation(project(${kotlinString(it)}))") }
            appendLine("}")
        }
        appendLine()
        appendLine("windows {")
        if (options.kind.application) {
            appendLine("    application {")
            appendLine("        mainClass = ${kotlinString("${options.packageName}.MainKt")}")
            appendLine("        minWindowsVersion = \"10.0.19041.0\"")
            appendLine("        packageType = WindowsPackageType.${if (options.packaged) "Packaged" else "None"}")
            if (options.kind == WinRTTemplateKind.WinUIApplication && !options.packaged) {
                appendLine("        windowsAppSdkDeployment = WindowsAppSdkDeployment.SelfContained")
            }
            appendLine("        launcherIcon = layout.projectDirectory.file(\"src/main/appxResources/Assets/Application.ico\")")
            if (options.kind == WinRTTemplateKind.ConsoleApplication) appendLine("        console = true")
            appendLine("        runTask(\"runWindows\")")
            appendLine("    }")
        }
        if (!options.kind.xaml) appendLine("    xaml { exportLibrarySchema = false }")
        appendLine("    packageReferences {")
        val ownsProjections = options.kind == WinRTTemplateKind.ProjectionLibrary
        appendLine("        windowsSdk(${kotlinString(options.sdkVersion)}, includeExtensions = false, generateProjection = $ownsProjections)")
        if (!ownsProjections && options.projectionModule.isNotEmpty()) {
            // Like CsWinRT's component -> SDK projection ProjectReferences, consumers
            // own their authored declarations, while SDK additions stay with the SDK owner.
            appendLine("        // SDK declarations and interop additions belong to ${options.projectionModule}.")
            listOf("Windows", "Microsoft", "WinRT").forEach {
                appendLine("        excludeAdditionNamespace(${kotlinString(it)})")
            }
        }
        if (options.kind.xaml || (ownsProjections && options.includeWinUI)) {
            appendLine("        nugetPackage(\"Microsoft.WindowsAppSDK\", ${kotlinString(options.appSdkVersion)}) { generateProjection = $ownsProjections }")
            appendLine("        namespace(\"Microsoft.UI.Xaml\")")
            appendLine("        namespace(\"Microsoft.UI.Xaml.Controls\")")
        }
        if (ownsProjections || options.projectionModule.isEmpty()) appendLine("        type(\"Windows.Foundation.Uri\")")
        appendLine("    }")
        appendLine("}")
    }

    private fun manifest(options: WinRTTemplateOptions) = """
        <?xml version="1.0" encoding="utf-8"?>
        <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                 xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
                 xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
                 IgnorableNamespaces="uap rescap">
            <Identity Name="${options.packageName}" Publisher="CN=Developer" Version="1.0.0.0" ProcessorArchitecture="x64" />
            <Properties>
                <DisplayName>${xml(options.displayName)}</DisplayName>
                <PublisherDisplayName>Developer</PublisherDisplayName>
                <Logo>Assets\StoreLogo.png</Logo>
            </Properties>
            <Resources><Resource Language="en-US" /></Resources>
            <Dependencies><TargetDeviceFamily Name="Windows.Desktop" /></Dependencies>
            <Applications>
                <Application Id="App" Executable="${options.name}.exe" EntryPoint="Windows.FullTrustApplication">
                    <uap:VisualElements DisplayName="${xml(options.displayName)}" Description="${xml(options.displayName)}"
                                        BackgroundColor="transparent" Square150x150Logo="Assets\MedTile.png" Square44x44Logo="Assets\AppList.png">
                        <uap:DefaultTile Wide310x150Logo="Assets\WideTile.png" Square71x71Logo="Assets\SmallTile.png" Square310x310Logo="Assets\LargeTile.png" />
                        <uap:SplashScreen Image="Assets\SplashScreen.png" />
                    </uap:VisualElements>
                </Application>
            </Applications>
            <Capabilities><rescap:Capability Name="runFullTrust" /></Capabilities>
        </Package>
    """.trimIndent()

    private fun applicationAssets(): Map<String, ByteArray> = buildMap {
        val resource = requireNotNull(javaClass.getResourceAsStream("/templates/application-assets.zip"))
        ZipInputStream(resource).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name.matches(Regex("Assets/[^/]+\\.png"))) { "Invalid template asset: ${entry.name}" }
                put(entry.name, zip.readAllBytes())
            }
        }
    }

    /** Wrap original PNG variants in ICO without resizing/re-encoding user-supplied pixels. */
    private fun launcherIcon(assets: Map<String, ByteArray>): ByteArray {
        val variants = listOf(16, 24, 32, 48, 64, 256).map { size -> size to assets.getValue("Assets/AppList.targetsize-$size.png") }
        val directorySize = 6 + 16 * variants.size
        val buffer = ByteBuffer.allocate(directorySize + variants.sumOf { it.second.size }).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0).putShort(1).putShort(variants.size.toShort())
        var offset = directorySize
        variants.forEach { (size, png) ->
            buffer.put(if (size == 256) 0 else size.toByte()).put(if (size == 256) 0 else size.toByte())
                .put(0).put(0).putShort(1).putShort(32).putInt(png.size).putInt(offset)
            offset += png.size
        }
        variants.forEach { buffer.put(it.second) }
        return buffer.array()
    }

    private fun MutableMap<String, ByteArray>.text(path: String, value: String) { put(path, "$value\n".toByteArray(Charsets.UTF_8)) }
    fun primaryModule(kind: WinRTTemplateKind) = when {
        kind == WinRTTemplateKind.ProjectionLibrary -> "winrt-projections"
        kind.application -> "app"
        else -> "library"
    }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
    internal fun kotlinString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("$", "\\$").replace("\r", "\\r").replace("\n", "\\n") + "\""
}
