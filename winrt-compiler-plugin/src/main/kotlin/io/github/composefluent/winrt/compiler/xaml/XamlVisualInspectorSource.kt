package io.github.composefluent.winrt.compiler.xaml

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

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
              WinRTXamlVisualProperty("DataContext", it.dataContext?.toString().orEmpty())) }.orEmpty()
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
                val buffer = bitmap.getPixelsAsync().await()
                require(bitmap.pixelWidth in 1..768 && bitmap.pixelHeight in 1..768 && buffer.length.toLong() == 4L * bitmap.pixelWidth * bitmap.pixelHeight) {
                  "WinUI returned an unsupported bitmap size or pixel buffer."
                }
                val bytes = readWinRTXamlVisualPixels((buffer as IWinRTObject).nativeObject, buffer.length.toInt())
                complete(Result.success(WinRTXamlVisualImage(bitmap.pixelWidth, bitmap.pixelHeight, bytes)))
              } catch (error: Exception) { complete(Result.failure(error)) }
              finally { lifetime.cancel() }
            }
          },
          preview = { owner, request ->
            val window = owner.asWinRT<Window>()
            val element = requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(request.previewMarkup)).asWinRT<FrameworkElement>()
            element.width = request.width.toDouble(); element.height = request.height.toDouble()
            element.requestedTheme = when (request.theme) { "Dark" -> ElementTheme.Dark; "Light" -> ElementTheme.Light; else -> ElementTheme.Default }
            window.content = element
            element.updateLayout()
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
                window = Window().apply {
                  title = "Kotlin WinRT XAML Preview"
                  content = microsoft.ui.xaml.controls.Grid().apply { width = 800.0; height = 600.0 }
                  activate()
                  completeWinRTXamlHotReloadComponent(this, WinRTXamlHotReloadProtocol.PREVIEW_CLASS, "", "0".repeat(64))
                }
              }
            }
        """.trimIndent())
    }
    return name
}
