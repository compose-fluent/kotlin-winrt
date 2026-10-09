package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTXamlNamespaces
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** SDK calls shared by application inspection and the project-independent designer. */
internal fun xamlDevelopmentConfigurationSource(inspector: String): String = """
    io.github.composefluent.winrt.runtime.configureWinRTXamlHotReload(
      dispatcherFactory = {
        val queue = microsoft.ui.dispatching.DispatcherQueue.getForCurrentThread()
        val enqueue: (() -> Unit) -> Boolean = { action ->
          queue.tryEnqueue(microsoft.ui.dispatching.DispatcherQueueHandler { action() })
        }
        enqueue
      },
      sdkConvert = { type, text -> microsoft.ui.xaml.markup.XamlBindingHelper.convertValue(type, text) },
      loadResources = { markup -> requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(markup))
        .asWinRT<microsoft.ui.xaml.ResourceDictionary>() },
      loadElement = { markup -> requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(markup))
        .asWinRT<microsoft.ui.xaml.UIElement>() },
      inspector = $inspector(),
    )
""".trimIndent()

/** The designer needs a connected WinUI visual tree, never a user-facing window.
 * AppWindow.Show(false) initializes it without taking focus; Hide keeps it alive.
 * https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.windowing.appwindow.hide
 */
private fun xamlPreviewWindowSource(): String = """
    Window().apply {
      title = "Kotlin WinRT XAML Preview"
      content = microsoft.ui.xaml.controls.Grid().apply { width = 800.0; height = 600.0 }
      val host = requireNotNull(appWindow)
      host.isShownInSwitchers = false
      // Keep initialization out of the desktop even before Hide runs.
      host.move(windows.graphics.PointInt32(-32000, -32000))
      host.show(false)
      host.hide()
      completeWinRTXamlHotReloadComponent(this, WinRTXamlHotReloadProtocol.PREVIEW_CLASS, "", "0".repeat(64))
    }
""".trimIndent()

/**
 * Uses the same generated SDK projection as CsWinRT's WinUIDesktopSample, with no
 * project declarations, event handlers or user entry point. The fixed designer
 * Application receives OnLaunched through the normal generated authoring ABI,
 * after WinUI has initialized its resources (WinUIDesktopSample/App.xaml.cs).
 */
fun writeXamlSdkPreviewSources(root: Path, model: WinRTMetadataModel) {
    val sdkNamespaces = WinRTXamlNamespaces.namespaces(WinRTXamlNamespaces.PRESENTATION).toSet()
    val definitions = model.namespaces.flatMap { it.types }.associateBy { it.qualifiedName }
    // The property inspector visits visual nodes, not SDK event args or metadata
    // helper classes. Its member tables follow the real UIElement base hierarchy.
    val names = model.namespaces.filter { it.name in sdkNamespaces && it.name.startsWith("Microsoft.UI.Xaml") }
        .flatMap { it.types }.filter { type ->
            generateSequence(type) { definitions[it.baseTypeName] }.any { it.qualifiedName == "Microsoft.UI.Xaml.UIElement" }
        }.map { it.qualifiedName }.toSet()
    val hostPackage = "io.github.composefluent.winrt.generated.xaml"
    val hostAssembly = "SdkPreviewHost"
    val overrides = requireNotNull(definitions["Microsoft.UI.Xaml.Application"])
        .implementedInterfaces.filter { it.isOverridable }.map { it.interfaceName }
    require(overrides.isNotEmpty()) { "The selected WinUI SDK does not expose Application overrides for the design host." }
    KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(KotlinWinRTAuthoredTypeCandidate(
        packageName = hostPackage,
        className = "KotlinWinRTXamlPreviewApplication",
        sourceTypeName = "$hostPackage.KotlinWinRTXamlPreviewApplication",
        winRTBaseClassName = "Microsoft.UI.Xaml.Application",
        winRTInterfaceNames = overrides,
        overridableInterfaceNames = overrides,
        isPublic = false,
    )), model, root.resolve("designer-authoring"), hostAssembly)
    val accessors = writeXamlProjectedTypeRegistrationSource(root, model, names, "SdkPreview")
    writeXamlMemberConversionSource(root)
    val inspector = writeXamlVisualInspectorSource(root, "SdkPreview", false, emptyList())
    root.resolve("io/github/composefluent/winrt/generated/xaml/KotlinWinRTXamlPreviewHost.kt").writeText("""
        package io.github.composefluent.winrt.generated.xaml
        import microsoft.ui.xaml.*
        import io.github.composefluent.winrt.runtime.*
        internal class KotlinWinRTXamlPreviewApplication : Application() {
          override fun onLaunched(args: LaunchActivatedEventArgs) {
            try { KotlinWinRTXamlPreviewHost.showWindow() }
            catch (error: Throwable) { error.printStackTrace(); throw error }
          }
        }
        object KotlinWinRTXamlPreviewHost {
          private var application: Application? = null
          private var window: Window? = null
          @kotlin.jvm.JvmStatic fun main(args: Array<String>) {
            WinRTProjectionSupportIntrinsic.ensureInitialized()
            Application.start {
              ${xamlDevelopmentConfigurationSource(inspector).prependIndent("              ").trimStart()}
              ${accessors?.let { "$it()" }.orEmpty()}
              io.github.composefluent.winrt.projections.support.${authoringTypeDetailsRegistrarName(hostAssembly)}.register()
              application = KotlinWinRTXamlPreviewApplication()
            }
            window = null; application = null
          }
          internal fun showWindow() {
            requireNotNull(application).resources.mergedDictionaries.add(microsoft.ui.xaml.controls.XamlControlsResources())
            window = ${xamlPreviewWindowSource().prependIndent("            ").trimStart()}
          }
        }
    """.trimIndent())
}

/** Typed WinUI SDK adaptation follows CSharpTypeInfoPass2's generated SDK calls.
 * VisualTreeHelper and RenderTargetBitmap own traversal/rendering; no substitute renderer.
 * https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.xaml.media.imaging.rendertargetbitmap */
internal fun writeXamlVisualInspectorSource(root: Path, assemblyName: String?, application: Boolean, registrars: List<String>): String {
    val suffix = assemblyName.orEmpty().replace(Regex("[^A-Za-z0-9_]"), "_")
    val name = "kotlinWinRTXamlVisualInspector_$suffix"
    val file = root.resolve("io/github/composefluent/winrt/generated/xaml/KotlinXamlVisualInspector_$suffix.kt")
    Files.createDirectories(file.parent)
    file.writeText("""
        @file:Suppress("DEPRECATION")
        package io.github.composefluent.winrt.generated.xaml
        import io.github.composefluent.winrt.runtime.*
        import kotlinx.coroutines.*
        import kotlin.coroutines.CoroutineContext
        import microsoft.ui.xaml.*
        import microsoft.ui.xaml.media.VisualTreeHelper
        import microsoft.ui.xaml.media.imaging.RenderTargetBitmap
        internal fun $name(): WinRTXamlVisualInspector = WinRTXamlVisualInspector(
          visualRoot = { owner -> if (owner is Window) owner.content else owner },
          children = { value ->
            val element = value.asWinRT<DependencyObject>()
            val count = VisualTreeHelper.getChildrenCount(element)
            require(count in 0..2048) { "Too many visual children" }
            List(count) { VisualTreeHelper.getChild(element, it) }
          },
          describe = { value, root ->
            val element = runCatching { value.asWinRT<FrameworkElement>() }.getOrNull()
            val bounds = element?.let { item -> runCatching {
              val rect = item.transformToVisual(root.asWinRT<UIElement>()).transformBounds(
                windows.foundation.Rect(0f, 0f, item.actualWidth.toFloat(), item.actualHeight.toFloat()))
              WinRTXamlVisualBounds(rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
            }.getOrNull() } ?: WinRTXamlVisualBounds(0.0, 0.0, 0.0, 0.0)
            val type = (value as IWinRTObject).nativeObject.asInspectable().use { it.getRuntimeClassName(true) }.orEmpty()
            WinRTXamlVisualNode(emptyList(), type, element?.name.orEmpty(), bounds)
          },
          properties = { value ->
            val element = runCatching { value.asWinRT<FrameworkElement>() }.getOrNull()
            val basic = element?.let { listOf(WinRTXamlVisualProperty("Name", it.name),
              WinRTXamlVisualProperty("ActualWidth", it.actualWidth.toString()), WinRTXamlVisualProperty("ActualHeight", it.actualHeight.toString()),
              WinRTXamlVisualProperty("Visibility", it.visibility.toString()), WinRTXamlVisualProperty("Opacity", it.opacity.toString()),
              WinRTXamlVisualProperty("DataContext", it.dataContext?.toString().orEmpty()),
              WinRTXamlVisualProperty("XamlRoot.IsHostVisible", it.xamlRoot?.isHostVisible?.toString().orEmpty())) }.orEmpty()
            (basic + winRTXamlInspectionProperties(value)).distinctBy { it.name }
          },
          capture = { value, complete ->
            val element = value.asWinRT<FrameworkElement>()
            val queue = requireNotNull(element.dispatcherQueue)
            val dispatcher = object : CoroutineDispatcher() {
              override fun dispatch(context: CoroutineContext, block: Runnable) {
                check(queue.tryEnqueue { block.run() }) { "The visual dispatcher has closed" }
              }
            }
            val lifetime = Job()
            CoroutineScope(lifetime + dispatcher).launch {
              var stage = "render the visual tree"
              try {
                val bitmap = RenderTargetBitmap()
                // RenderAsync's requested size is in view pixels. PixelWidth and
                // the BGRA buffer include XamlRoot's physical DPI scale.
                val dpi = element.xamlRoot?.rasterizationScale ?: 1.0
                require(dpi.isFinite() && dpi > 0) { "The visual root has an invalid DPI scale." }
                val scale = minOf(1.0, 768.0 / (maxOf(element.actualWidth, element.actualHeight, 1.0) * dpi))
                val width = (element.actualWidth * scale).toInt().coerceIn(1, 768)
                val height = (element.actualHeight * scale).toInt().coerceIn(1, 768)
                bitmap.renderAsync(element, width, height).await()
                stage = "read the rendered pixels"
                val buffer = bitmap.getPixelsAsync().await()
                require(bitmap.pixelWidth in 1..768 && bitmap.pixelHeight in 1..768 && buffer.length.toLong() == 4L * bitmap.pixelWidth * bitmap.pixelHeight) {
                  "WinUI returned an unsupported bitmap size or pixel buffer."
                }
                val bytes = readWinRTXamlVisualPixels((buffer as IWinRTObject).nativeObject, buffer.length.toInt())
                complete(Result.success(WinRTXamlVisualImage(bitmap.pixelWidth, bitmap.pixelHeight, bytes)))
              } catch (error: Exception) { complete(Result.failure(IllegalStateException("WinUI could not ${'$'}stage: ${'$'}{error.message}", error))) }
              finally { lifetime.cancel() }
            }
          },
          preview = { owner, request ->
            val window = owner.asWinRT<Window>()
            var stage = "load the XAML"
            try {
              val element = requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(request.previewMarkup)).asWinRT<FrameworkElement>()
              element.width = request.width.toDouble(); element.height = request.height.toDouble()
              element.requestedTheme = when (request.theme) { "Dark" -> ElementTheme.Dark; "Light" -> ElementTheme.Light; else -> ElementTheme.Default }
              stage = "size the design surface"
              // Use the real client area so window-aware SDK controls (TitleBar)
              // see the same layout as the artboard, even while it is hidden.
              val dpi = window.content?.asWinRT<FrameworkElement>()?.xamlRoot?.rasterizationScale ?: 1.0
              require(dpi.isFinite() && dpi > 0) { "The visual root has an invalid DPI scale." }
              requireNotNull(window.appWindow).resizeClient(windows.graphics.SizeInt32(
                (request.width * dpi).toInt(), (request.height * dpi).toInt()))
              stage = "attach the visual tree"
              window.content = element
              stage = "lay out the visual tree"
              element.updateLayout()
            } catch (error: Exception) { throw IllegalStateException("WinUI could not ${'$'}stage: ${'$'}{error.message}", error) }
          },
        )
    """.trimIndent())
    if (application) {
        file.parent.resolve("KotlinWinRTXamlPreviewHost.kt").writeText("""
            package io.github.composefluent.winrt.generated.xaml
            import microsoft.ui.xaml.*
            import io.github.composefluent.winrt.runtime.*
            // The authoring scanner emits ApplicationOverrides for internal classes.
            // A private subclass would never receive WinUI's OnLaunched callback.
            internal class KotlinWinRTXamlPreviewApplication : Application() {
              override fun onLaunched(args: LaunchActivatedEventArgs) { KotlinWinRTXamlPreviewHost.showWindow() }
            }
            /** Isolated design host: the user's application main/constructor is not invoked. */
            object KotlinWinRTXamlPreviewHost {
              private var application: Application? = null
              private var window: Window? = null
              @kotlin.jvm.JvmStatic fun main(args: Array<String>) {
                Application.start {
                  ${registrars.joinToString("\n                  ") { "$it.registerAll()" }}
                  application = KotlinWinRTXamlPreviewApplication()
                }
                window = null; application = null
              }
              internal fun showWindow() {
                // The native Application resource dictionary is ready after Start's
                // initialization callback returns, when OnLaunched is dispatched.
                requireNotNull(application).resources.mergedDictionaries.add(microsoft.ui.xaml.controls.XamlControlsResources())
                window = ${xamlPreviewWindowSource().prependIndent("                ").trimStart()}
              }
            }
        """.trimIndent())
    }
    return name
}
