import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
    id("com.google.devtools.ksp") version "2.3.10"
}

// KSP owns the source viewer, while XamlCompiler owns compilation. Include markup
// bytes in processor options so a XAML-only edit also invalidates KSP's own cache.
abstract class GalleryXamlSourceArguments : org.gradle.process.CommandLineArgumentProvider {
    @get:org.gradle.api.tasks.Internal
    abstract val sourceRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sources: org.gradle.api.file.ConfigurableFileCollection

    override fun asArguments(): Iterable<String> {
        val root = sourceRoot.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        sources.files.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.forEach { file ->
            digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(file.readBytes())
            digest.update(0.toByte())
        }
        return listOf("gallery.xamlSourceFingerprint=" + digest.digest().joinToString("") { "%02x".format(it) })
    }
}

val galleryXamlSourceArguments = objects.newInstance(GalleryXamlSourceArguments::class.java).apply {
    sourceRoot.set(layout.projectDirectory.dir("src/winuiMain/kotlin"))
    sources.from(sourceRoot.map { it.asFileTree.matching { include("**/*.xaml", "**/SampleDefinitions/**/*.txt") } })
}

val gallerySigningCertificateThumbprint =
    providers.environmentVariable("WINUI_GALLERY_SIGNING_CERTIFICATE_THUMBPRINT").orNull

// Navigation output contains data and factory calls, never authored WinRT classes.
// Keep authoring/projection scans on authored source so they do not depend on KSP.
tasks.named<io.github.composefluent.windows.toolkit.gradle.GenerateWinRTAuthoringCandidatesTask>("generateWinRTAuthoringCandidates") {
    sourceRoots.setFrom(layout.projectDirectory.dir("src/winuiMain/kotlin"))
}
tasks.named<io.github.composefluent.windows.toolkit.gradle.GenerateWinRTProjectionsTask>("generateWinRTProjections") {
    sourceRoots.setFrom(layout.projectDirectory.dir("src/winuiMain/kotlin"))
}

ksp {
    arg("gallery.repositoryRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
    arg(galleryXamlSourceArguments)
}
tasks.matching { it.name.startsWith("ksp") }.configureEach {
    dependsOn("generateWinRTProjections", "mergeWinRTCompilerSupport")
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm") {
        // Keep the generated full WinUI projection compile practical on the JVM.
        // This is the same setting used by the standalone projection module.
        compilerOptions {
            freeCompilerArgs.add("-Xno-optimize")
        }
    }
    mingwX64 { binaries { executable { entryPoint = "io.github.composefluent.winrt.gallery.main" } } }
    sourceSets {
        getByName("winuiMain").dependencies {
            implementation(project(":winui-gallery:code-document"))
            implementation(project(":winui-gallery:models"))
            implementation(project(":winui-gallery:resources"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

// The Gallery owns a full WinUI projection compile in addition to its app
// compile. Apply the same JVM optimizer bypass used by winrt-projections to
// every JVM compiler task, including the generated projection task.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xno-optimize")
}

dependencies {
    add("kspWinuiJvm", project(":winui-gallery:processor"))
    add("kspMingwX64", project(":winui-gallery:processor"))
}

// Resolve semantic colors in the same K2 session as the application, on both targets.
val galleryHighlightingPlugin by configurations.creating { isTransitive = false }
dependencies {
    galleryHighlightingPlugin(project(":winui-gallery:processor"))
    galleryHighlightingPlugin(project(mapOf(
        "path" to ":winui-gallery:code-document",
        "configuration" to "jvmRuntimeElements",
    )))
}
// Highlight application sources only. Raw -Xplugin arguments are inherited by
// isolated projection/XAML semantic compilations, without the producer tasks.
// KGP's classpath properties retain the JAR build dependencies and keep this
// application processor out of the SDK projection boundary (as with the separate
// projection ProjectReferences in .cswinrt/src/Samples/WinUIDesktopSample).
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>("compileKotlinWinuiJvm") {
    pluginClasspath.from(galleryHighlightingPlugin)
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>("compileKotlinMingwX64") {
    compilerPluginClasspath = files(compilerPluginClasspath, galleryHighlightingPlugin)
}
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlinWinuiJvm") {
    // Preview and original file must share a FIR session, including rebuilds after
    // a processor/palette change. Projection compilation remains incremental.
    incremental = false
}

windows {
    application {
        mainClass = "io.github.composefluent.winrt.gallery.MainKt"
        launcherIcon = layout.projectDirectory.file("src/winuiMain/appxResources/Assets/Tiles/GalleryIcon.ico")
        minWindowsVersion = "10.0.19041.0"
        if (!gallerySigningCertificateThumbprint.isNullOrBlank()) {
            signPackage.set(true)
            signingCertificateThumbprint.set(gallerySigningCertificateThumbprint)
        }
        packagePayload("licenses/WinUI-Gallery-LICENSE.txt", "licenses/WinUI-Gallery-LICENSE.txt")
        packagePayload("licenses/JetBrains-Apache-2.0.txt", "licenses/JetBrains-Apache-2.0.txt")
        packagePayload("licenses/WinUI-Essential-LICENSE.txt", "licenses/WinUI-Essential-LICENSE.txt")
        packagePayload("THIRD-PARTY-NOTICES.md", "THIRD-PARTY-NOTICES.md")
    }
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false, generateProjection = true)
        // Match the installed WinUI 3 Gallery's Windows App SDK 2.5 runtime.
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1")
        nugetPackage("Microsoft.Graphics.Win2D", "1.4.0")
        nugetPackage("WinUIEssential.WinUI3", "1.8.2")
        // Gallery covers the control families, including programmatic templates,
        // layout overrides and composition animations. Generate these namespaces
        // directly rather than maintaining a second, incomplete control catalog.
        listOf(
            "Microsoft.UI.Xaml", "Microsoft.UI.Xaml.Controls",
            "Microsoft.UI.Xaml.Controls.Primitives", "Microsoft.UI.Xaml.Controls.AnimatedVisuals",
            "Microsoft.UI.Xaml.Documents", "Microsoft.UI.Xaml.Data",
            "Microsoft.UI.Xaml.Input", "Microsoft.UI.Xaml.Shapes",
            "Microsoft.UI.Xaml.Media", "Microsoft.UI.Xaml.Media.Imaging",
            "Microsoft.UI.Xaml.Media.Animation", "Microsoft.UI.Xaml.Hosting",
            "Microsoft.UI.Xaml.Automation", "Microsoft.UI.Xaml.Automation.Peers",
            "Microsoft.UI.Composition", "Microsoft.UI.Composition.SystemBackdrops",
            "Microsoft.UI.Windowing", "Microsoft.UI.Text", "Microsoft.UI.System", "Microsoft.UI.Content", "Microsoft.UI.Input",
            "Microsoft.Graphics.Canvas.Geometry",
            "Microsoft.Windows.Storage", "Microsoft.Windows.Storage.Pickers",
            "Microsoft.Windows.AppNotifications", "Microsoft.Windows.AppNotifications.Builder",
            "Microsoft.Windows.AppLifecycle", "Windows.ApplicationModel.Activation",
            "Microsoft.Windows.BadgeNotifications",
            "Windows.ApplicationModel", "Windows.ApplicationModel.DataTransfer", "Windows.UI.StartScreen",
            "Windows.Media.Capture", "Windows.Media.Capture.Frames",
            "Windows.Media.Core", "Windows.Media.Playback", "Windows.Media.MediaProperties",
            "Windows.Storage", "Windows.Storage.Streams", "Windows.Storage.FileProperties",
            "Windows.Devices.Geolocation", "Windows.Globalization", "Windows.Data.Xml.Dom",
            "Windows.Globalization.NumberFormatting",
            "Windows.Globalization.DateTimeFormatting", "Windows.UI.ViewManagement",
        ).forEach(::namespace)
        listOf(
            "Microsoft.UI.Xaml.Application", "Microsoft.UI.Xaml.Window",
            "Microsoft.UI.Xaml.Controls.XamlControlsResources",
            "Microsoft.UI.Xaml.Controls.NavigationView", "Microsoft.UI.Xaml.Controls.NavigationViewItem",
            "Microsoft.UI.Xaml.Controls.TitleBar", "Microsoft.UI.Xaml.Controls.AutoSuggestBox",
            "Microsoft.UI.Xaml.Controls.Frame", "Microsoft.UI.Xaml.Controls.Grid",
            "Microsoft.UI.Xaml.Controls.StackPanel", "Microsoft.UI.Xaml.Controls.Button",
            "Microsoft.UI.Xaml.Controls.TextBlock", "Microsoft.UI.Xaml.Controls.CheckBox",
            "Microsoft.UI.Xaml.Controls.ComboBox", "Microsoft.UI.Xaml.Controls.ListView",
            "Microsoft.UI.Xaml.Controls.GridView", "Microsoft.UI.Xaml.Controls.SelectorBar",
            "Microsoft.UI.Xaml.Controls.ToggleSwitch", "Microsoft.UI.Xaml.Controls.Slider",
            "Microsoft.UI.Xaml.Controls.TextBox", "Microsoft.UI.Xaml.Controls.ContentDialog",
            "Microsoft.UI.Xaml.Controls.TextBlock", "Microsoft.UI.Xaml.Controls.Image",
            "Microsoft.UI.Xaml.Controls.Border", "Microsoft.UI.Xaml.Controls.FontIcon",
            "Microsoft.UI.Xaml.Controls.SymbolIcon", "Microsoft.UI.Xaml.Controls.ImageIconSource",
            "Microsoft.UI.Xaml.Controls.HyperlinkButton", "Microsoft.UI.Xaml.Controls.Expander",
            "Microsoft.UI.Xaml.Controls.DropDownButton", "Microsoft.UI.Xaml.Controls.Flyout",
            "Microsoft.UI.Xaml.Controls.NumberBox", "Microsoft.UI.Xaml.Controls.ProgressRing",
            "Microsoft.UI.Xaml.Controls.RadioButtons", "Microsoft.UI.Xaml.Shapes.Rectangle",
            "Windows.Globalization.NumberFormatting.DecimalFormatter",
            "Windows.Globalization.NumberFormatting.IncrementNumberRounder",
            "Microsoft.UI.Xaml.Controls.NavigationViewItemHeader", "Microsoft.UI.Xaml.Controls.ScrollViewer",
            "Microsoft.UI.Xaml.Controls.RowDefinition", "Microsoft.UI.Xaml.Controls.ColumnDefinition",
            "Microsoft.UI.Xaml.Media.Imaging.BitmapImage", "Microsoft.UI.Xaml.Media.SolidColorBrush",
            "Microsoft.UI.Xaml.Media.MicaBackdrop", "Microsoft.UI.Xaml.Automation.AutomationProperties",
            "Microsoft.UI.Windowing.AppWindow", "Windows.Graphics.SizeInt32",
            "Windows.Storage.ApplicationData", "Windows.System.Launcher",
            "WinUI3Package.SettingsCard", "WinUI3Package.SettingsExpander",
            "WinUI3Package.WindowEx", "WinUI3Package.TenMicaBackdrop",
            "WinUI3Package.MicaBackdropWithFallback", "WinUI3Package.ModernStandardWindowContextMenu",
            "WinUI3Package.WindowCaptionButtonThemeWorkaround", "WinUI3Package.ModernWindowCaptionButtonToolTip",
        ).forEach(::type)
    }
}
