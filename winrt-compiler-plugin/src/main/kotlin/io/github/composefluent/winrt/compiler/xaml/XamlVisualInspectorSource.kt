package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinUIXamlNamespaces
import io.github.composefluent.winrt.metadata.xamlNamespace
import io.github.composefluent.winrt.metadata.xamlKotlinNamespace
import io.github.composefluent.winrt.metadata.xamlTypeName
import io.github.composefluent.winrt.runtime.XamlFramework
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** SDK calls shared by application inspection and the project-independent designer. */
internal fun xamlDevelopmentConfigurationSource(inspector: String, framework: XamlFramework = XamlFramework.WinUI): String {
    val dispatching = if (framework == XamlFramework.UWP) "windows.system" else "microsoft.ui.dispatching"
    return """
    io.github.composefluent.winrt.runtime.configureWinUIXamlHotReload(
      dispatcherFactory = {
        val queue = requireNotNull(${dispatching}.DispatcherQueue.getForCurrentThread())
        val enqueue: (() -> Unit) -> Boolean = { action ->
          queue.tryEnqueue(${dispatching}.DispatcherQueueHandler { action() })
        }
        enqueue
      },
      sdkConvert = { type, text -> ${framework.xamlKotlinNamespace}.markup.XamlBindingHelper.convertValue(type, text) },
      loadResources = { markup -> requireNotNull(${framework.xamlKotlinNamespace}.markup.XamlReader.load(markup))
        .asWinRT<${framework.xamlKotlinNamespace}.ResourceDictionary>() },
      loadElement = { markup -> requireNotNull(${framework.xamlKotlinNamespace}.markup.XamlReader.load(markup))
        .asWinRT<${framework.xamlKotlinNamespace}.UIElement>() },
      inspector = $inspector(),
    )
""".trimIndent()
}

/** The desktop designer initializes its visual tree offscreen without activation.
 * Keeping the host shown avoids the hidden-window startup rendering delay.
 * UWP uses the activated CoreWindow.
 */
private fun xamlPreviewWindowSource(framework: XamlFramework): String = if (framework == XamlFramework.UWP) """
    requireNotNull(Window.current).apply {
      content = windows.ui.xaml.controls.Grid().apply { width = 800.0; height = 600.0 }
      activate()
      completeWinUIXamlHotReloadComponent(this, WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", "0".repeat(64))
    }
""".trimIndent() else """
    Window().apply {
      val startup = kotlin.time.TimeSource.Monotonic.markNow()
      title = "Kotlin WinRT XAML Preview"
      // The first inspection supplies the sized document. Do not lay out an
      // empty placeholder tree before that document is available.
      val host = requireNotNull(appWindow)
      host.isShownInSwitchers = false
      // Keep the host offscreen and out of task switching without taking focus.
      host.move(windows.graphics.PointInt32(-32000, -32000))
      host.show(false)
      println("XAML preview window shown: " + startup.elapsedNow().inWholeMilliseconds + "ms")
      completeWinUIXamlHotReloadComponent(this, WinUIXamlHotReloadProtocol.PREVIEW_CLASS, "", "0".repeat(64))
      println("XAML preview root registered: " + startup.elapsedNow().inWholeMilliseconds + "ms")
      requireNotNull(microsoft.ui.dispatching.DispatcherQueue.getForCurrentThread()).tryEnqueue(
        microsoft.ui.dispatching.DispatcherQueueHandler {
          println("XAML preview first UI turn: " + startup.elapsedNow().inWholeMilliseconds + "ms")
        })
    }
""".trimIndent()

/**
 * Uses the same generated SDK projection as CsWinRT's WinUIDesktopSample, with no
 * project declarations, event handlers or user entry point. The fixed designer
 * Application receives OnLaunched through the normal generated authoring ABI,
 * after WinUI has initialized its resources (WinUIDesktopSample/App.xaml.cs).
 */
fun writeXamlSdkPreviewSources(root: Path, model: WinRTMetadataModel, framework: XamlFramework = XamlFramework.WinUI) {
    val sdkNamespaces = WinUIXamlNamespaces.namespaces(WinUIXamlNamespaces.PRESENTATION, framework).toSet()
    val definitions = model.namespaces.flatMap { it.types }.associateBy { it.qualifiedName }
    // The property inspector visits visual nodes, not SDK event args or metadata
    // helper classes. Its member tables follow the real UIElement base hierarchy.
    val names = model.namespaces.filter { it.name in sdkNamespaces && it.name.startsWith(framework.xamlNamespace) }
        .flatMap { it.types }.filter { type ->
            generateSequence(type) { definitions[it.baseTypeName] }.any { it.qualifiedName == framework.xamlTypeName("UIElement") }
        }.map { it.qualifiedName }.toSet()
    val hostPackage = "io.github.composefluent.winrt.generated.xaml"
    val hostAssembly = "SdkPreviewHost"
    val overrides = requireNotNull(definitions[framework.xamlTypeName("Application")])
        .implementedInterfaces.filter { it.isOverridable }.map { it.interfaceName }
    require(overrides.isNotEmpty()) { "The selected WinUI SDK does not expose Application overrides for the design host." }
    KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(listOf(KotlinWinRTAuthoredTypeCandidate(
        packageName = hostPackage,
        className = "KotlinWinUIXamlPreviewApplication",
        sourceTypeName = "$hostPackage.KotlinWinUIXamlPreviewApplication",
        winRTBaseClassName = framework.xamlTypeName("Application"),
        winRTInterfaceNames = overrides,
        overridableInterfaceNames = overrides,
        isPublic = false,
    )), model, root.resolve("designer-authoring"), hostAssembly)
    val accessors = writeXamlProjectedTypeRegistrationSource(root, model, names, "SdkPreview", framework)
    writeXamlMemberConversionSource(root, framework)
    val inspector = writeXamlVisualInspectorSource(root, "SdkPreview", false, emptyList(), framework)
    root.resolve("io/github/composefluent/winrt/generated/xaml/KotlinWinUIXamlPreviewHost.kt").writeText("""
        package io.github.composefluent.winrt.generated.xaml
        import ${framework.xamlKotlinNamespace}.*
        ${if (framework == XamlFramework.UWP) "import windows.applicationmodel.activation.LaunchActivatedEventArgs" else ""}
        import io.github.composefluent.winrt.runtime.*
        internal class KotlinWinUIXamlPreviewApplication : Application() {
          init {
            when (winUIXamlPreviewStartupTheme()) {
              "Light" -> requestedTheme = ApplicationTheme.Light
              "Dark" -> requestedTheme = ApplicationTheme.Dark
            }
          }
          override fun onLaunched(args: LaunchActivatedEventArgs?) {
            try { KotlinWinUIXamlPreviewHost.showWindow() }
            catch (error: Throwable) { error.printStackTrace(); throw error }
          }
        }
        object KotlinWinUIXamlPreviewHost {
          private var application: Application? = null
          private var window: Window? = null
          @kotlin.jvm.JvmStatic fun main(args: Array<String>) {
            XamlRuntime.initialize(XamlFramework.${framework.name})
            WinRTProjectionSupportIntrinsic.ensureInitialized()
            Application.start {
              ${xamlDevelopmentConfigurationSource(inspector, framework).prependIndent("              ").trimStart()}
              ${accessors?.let { "$it()" }.orEmpty()}
              io.github.composefluent.winrt.projections.support.${authoringTypeDetailsRegistrarName(hostAssembly)}.register()
              application = KotlinWinUIXamlPreviewApplication()
            }
            window = null; application = null
          }
          internal fun showWindow() {
            ${if (framework == XamlFramework.WinUI) "requireNotNull(requireNotNull(requireNotNull(application).resources).mergedDictionaries).add(microsoft.ui.xaml.controls.XamlControlsResources())" else ""}
            window = ${xamlPreviewWindowSource(framework).prependIndent("            ").trimStart()}
          }
        }
    """.trimIndent())
}

/** Typed WinUI SDK adaptation follows CSharpTypeInfoPass2's generated SDK calls.
 * VisualTreeHelper and RenderTargetBitmap own traversal/rendering; no substitute renderer.
 * https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.xaml.media.imaging.rendertargetbitmap */
internal fun writeXamlVisualInspectorSource(root: Path, assemblyName: String?, application: Boolean, registrars: List<String>, framework: XamlFramework = XamlFramework.WinUI): String {
    val dispatching = if (framework == XamlFramework.UWP) "windows.system" else "microsoft.ui.dispatching"
    val suffix = assemblyName.orEmpty().replace(Regex("[^A-Za-z0-9_]"), "_")
    val name = "kotlinWinUIXamlVisualInspector_$suffix"
    val file = root.resolve("io/github/composefluent/winrt/generated/xaml/KotlinXamlVisualInspector_$suffix.kt")
    Files.createDirectories(file.parent)
    file.writeText("""
        @file:Suppress("DEPRECATION")
        package io.github.composefluent.winrt.generated.xaml
        import io.github.composefluent.winrt.runtime.*
        import kotlinx.coroutines.*
        import kotlin.coroutines.CoroutineContext
        import ${framework.xamlKotlinNamespace}.*
        ${if (framework == XamlFramework.UWP) "import windows.applicationmodel.activation.LaunchActivatedEventArgs" else ""}
        import ${framework.xamlKotlinNamespace}.media.VisualTreeHelper
        import ${framework.xamlKotlinNamespace}.media.imaging.RenderTargetBitmap
        import ${framework.xamlKotlinNamespace}.media.animation.Storyboard
        import kotlin.time.Duration.Companion.milliseconds
        internal fun $name(): WinUIXamlVisualInspector {
          // CsWinRT strings/additions/*/Thickness.cs and CornerRadius.cs use
          // invariant component order. SDK-specific display stays in this adapter,
          // while the runtime owns getter failure isolation and output limits.
          fun number(value: Double): String = if (value.isNaN()) "Auto" else value.toString().removeSuffix(".0")
          fun components(vararg values: Double): String = values.joinToString(",") { number(it) }
          fun color(value: windows.ui.Color): String = "#" + listOf(value.a, value.r, value.g, value.b)
            .joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
          fun display(value: Any?): String = when (value) {
            null -> ""
            is Thickness -> components(value.left, value.top, value.right, value.bottom)
            is CornerRadius -> components(value.topLeft, value.topRight, value.bottomRight, value.bottomLeft)
            is windows.foundation.Point -> components(value.x.toDouble(), value.y.toDouble())
            is windows.foundation.Size -> components(value.width.toDouble(), value.height.toDouble())
            is windows.foundation.Rect -> components(value.x.toDouble(), value.y.toDouble(), value.width.toDouble(), value.height.toDouble())
            is GridLength -> when (value.gridUnitType) {
              GridUnitType.Auto -> "Auto"
              GridUnitType.Star -> number(value.value) + "*"
              else -> number(value.value)
            }
            is windows.ui.text.FontWeight -> value.weight.toString()
            is windows.ui.Color -> color(value)
            is ${framework.xamlKotlinNamespace}.media.FontFamily -> value.source
            is ${framework.xamlKotlinNamespace}.media.Brush -> {
              val reference = value.nativeObject
              if (reference.tryQueryInterface(${framework.xamlKotlinNamespace}.media.SolidColorBrush.DEFAULT_INTERFACE_IID)?.use { true } == true)
                color(value.asWinRT<${framework.xamlKotlinNamespace}.media.SolidColorBrush>().color)
              else reference.asInspectable().use { it.getRuntimeClassName(true) }.orEmpty()
            }
            is IWinRTObject -> value.nativeObject.asInspectable().use { it.getRuntimeClassName(true) }.orEmpty()
            else -> value.toString()
          }
          var activeStoryboard: Storyboard? = null
          var playing = false
          // Report only the first isolated designer frame, never live inspection
          // or animation frames. The IDE owns the redirected preview-host log.
          var tracedPreview = false
          var traceCapture = false
          return WinUIXamlVisualInspector(
          visualRoot = { owner -> if (owner is Window) owner.content else owner },
          children = { value ->
            val element = value.asWinRT<DependencyObject>()
            val count = VisualTreeHelper.getChildrenCount(element)
            require(count in 0..2048) { "Too many visual children" }
            List(count) { requireNotNull(VisualTreeHelper.getChild(element, it)) }
          },
          describe = { value, root ->
            val element = runCatching { value.asWinRT<FrameworkElement>() }.getOrNull()
            val bounds = element?.let { item -> runCatching {
              // UIElement.GetRenderSize uses layout storage; TextBlock.ActualWidth/Height describe text ink.
              val layoutSize = item.renderSize
              val rect = requireNotNull(item.transformToVisual(root.asWinRT<UIElement>())).transformBounds(
                windows.foundation.Rect(0f, 0f, layoutSize.width, layoutSize.height))
              WinUIXamlVisualBounds(rect.x.toDouble(), rect.y.toDouble(), rect.width.toDouble(), rect.height.toDouble())
            }.getOrNull() } ?: WinUIXamlVisualBounds(0.0, 0.0, 0.0, 0.0)
            val type = winUIXamlInspectionTypeName(value) ?: (value as IWinRTObject).nativeObject.asInspectable().use { it.getRuntimeClassName(true) }.orEmpty()
            WinUIXamlVisualNode(emptyList(), type, element?.name.orEmpty(), bounds)
          },
          // SDK ContentTemplateRoot is the realization boundary. Match the actual
          // DataTemplate COM identity before attributing an item container to a list.
          template = template@ { value ->
            // Projection wrappers may defer QI until the first member call.
            // Probe the generated default IID before constructing an optional view.
            val reference = (value as? IWinRTObject)?.nativeObject ?: return@template null
            if (reference.tryQueryInterface(${framework.xamlKotlinNamespace}.controls.ContentControl.DEFAULT_INTERFACE_IID)?.use { true } != true) return@template null
            val control = ${framework.xamlKotlinNamespace}.controls.ContentControl.wrap(reference.asInspectable())
            val realized = control.contentTemplateRoot ?: return@template null
            val dataTemplate = control.contentTemplate ?: return@template null
            var parent = VisualTreeHelper.getParent(control)
            var depth = 0
            while (parent != null && depth++ < 64) {
              val ancestor = parent!!
              val items = (ancestor as? IWinRTObject)?.nativeObject
                ?.tryQueryInterface(${framework.xamlKotlinNamespace}.controls.ItemsControl.DEFAULT_INTERFACE_IID)
                ?.use { ${framework.xamlKotlinNamespace}.controls.ItemsControl.wrap((ancestor as IWinRTObject).nativeObject.asInspectable()) }
              if (items != null) {
                if (items.itemTemplate?.nativeObject?.sameIdentity(dataTemplate.nativeObject) == true) {
                  return@template WinUIXamlTemplateReference(realized, items, "ItemTemplate")
                }
                break
              }
              parent = VisualTreeHelper.getParent(parent!!)
            }
            WinUIXamlTemplateReference(realized, control, "ContentTemplate")
          },
          sameObject = { first, second ->
            first === second || (first as? IWinRTObject)?.nativeObject?.let { reference ->
              (second as? IWinRTObject)?.nativeObject?.let(reference::sameIdentity)
            } == true
          },
          properties = { value ->
            val element = runCatching { value.asWinRT<FrameworkElement>() }.getOrNull()
            val basic = element?.let { listOf(WinUIXamlVisualProperty("Name", it.name),
              WinUIXamlVisualProperty("ActualWidth", it.actualWidth.toString()), WinUIXamlVisualProperty("ActualHeight", it.actualHeight.toString()),
              WinUIXamlVisualProperty("Visibility", it.visibility.toString()), WinUIXamlVisualProperty("Opacity", it.opacity.toString()),
              WinUIXamlVisualProperty("DataContext", display(it.dataContext)),
              WinUIXamlVisualProperty("XamlRoot.IsHostVisible", it.xamlRoot?.isHostVisible?.toString().orEmpty())) }.orEmpty()
            // Keep inherited layout editors populated even on controls with many
            // declared properties. Runtime lookup/getter limits stay unchanged.
            (basic + winUIXamlInspectionProperties(value, listOf(
              "Width", "Height", "MinWidth", "MinHeight", "MaxWidth", "MaxHeight",
              "HorizontalAlignment", "VerticalAlignment", "Margin", "Padding"), ::display)).distinctBy { it.name }
          },
          capture = { value, renderScale, complete ->
            val timing = if (traceCapture) kotlin.time.TimeSource.Monotonic.markNow() else null
            traceCapture = false
            val element = value.asWinRT<FrameworkElement>()
            val queue = requireNotNull(${dispatching}.DispatcherQueue.getForCurrentThread())
            val dispatcher = object : CoroutineDispatcher() {
              override fun dispatch(context: CoroutineContext, block: Runnable) {
                check(queue.tryEnqueue { block.run() }) { "The visual dispatcher has closed" }
              }
            }
            val lifetime = Job()
            CoroutineScope(lifetime + dispatcher).launch {
              var stage = "render the visual tree"
              timing?.let { println("XAML preview capture queued: " + it.elapsedNow().inWholeMilliseconds + "ms") }
              try {
                val bitmap = RenderTargetBitmap()
                // RenderAsync's requested size is in view pixels. PixelWidth and
                // the BGRA buffer include XamlRoot's physical DPI scale.
                val dpi = element.xamlRoot?.rasterizationScale ?: 1.0
                require(dpi.isFinite() && dpi > 0) { "The visual root has an invalid DPI scale." }
                // Replacing Window.Content can invalidate ActualWidth/Height
                // before this queued capture runs. RenderAsync completes layout;
                // use the explicit artboard dimensions while that layout is pending.
                val logicalWidth = element.actualWidth.takeIf { it.isFinite() && it > 0 }
                  ?: element.width.takeIf { it.isFinite() && it > 0 } ?: 1.0
                val logicalHeight = element.actualHeight.takeIf { it.isFinite() && it > 0 }
                  ?: element.height.takeIf { it.isFinite() && it > 0 } ?: 1.0
                val (width, height) = winUIXamlCaptureSize(logicalWidth, logicalHeight, dpi, renderScale)
                ${if (framework == XamlFramework.WinUI) """// WinUI hwwalk.cpp propagates UIElement.RasterizationScale through
                // the subtree's realization transform, without changing DIP layout.
                // RenderAsync's output size alone can enlarge existing glyph textures.
                val previousRasterizationScale = element.rasterizationScale
                try {
                  element.rasterizationScale = maxOf(previousRasterizationScale, width / logicalWidth, height / logicalHeight)
                  requireNotNull(bitmap.renderAsync(element, width, height)).await()
                } finally { element.rasterizationScale = previousRasterizationScale }""" else """// Windows.UI.Xaml has no public UIElement.RasterizationScale setter.
                // UWP retains RenderAsync density scaling without changing its layout.
                requireNotNull(bitmap.renderAsync(element, width, height)).await()"""}
                timing?.let { println("XAML preview bitmap rendered: " + it.elapsedNow().inWholeMilliseconds + "ms") }
                stage = "read the rendered pixels"
                val buffer = requireNotNull(requireNotNull(bitmap.getPixelsAsync()).await())
                require(bitmap.pixelWidth in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && bitmap.pixelHeight in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && buffer.length.toLong() == 4L * bitmap.pixelWidth * bitmap.pixelHeight) {
                  "WinUI returned an unsupported bitmap size or pixel buffer."
                }
                timing?.let { println("XAML preview pixels available: " + it.elapsedNow().inWholeMilliseconds + "ms") }
                val bytes = readWinUIXamlVisualPixels((buffer as IWinRTObject).nativeObject, buffer.length.toInt())
                timing?.let { println("XAML preview pixels copied: " + it.elapsedNow().inWholeMilliseconds + "ms") }
                complete(Result.success(WinUIXamlVisualImage(bitmap.pixelWidth, bitmap.pixelHeight, bytes)))
                timing?.let { println("XAML preview snapshot delivered: " + it.elapsedNow().inWholeMilliseconds + "ms") }
              } catch (error: Exception) { complete(Result.failure(IllegalStateException("WinUI could not ${'$'}stage: ${'$'}{error.message}", error))) }
              finally { lifetime.cancel() }
            }
          },
          preview = { owner, request ->
            val timing = if (!tracedPreview) kotlin.time.TimeSource.Monotonic.markNow() else null
            val window = owner.asWinRT<Window>()
            activeStoryboard?.stop(); activeStoryboard = null; playing = false
            val previousContent = window.content
            var attached = false
            var stage = "load the XAML"
            try {
              val element = requireNotNull(${framework.xamlKotlinNamespace}.markup.XamlReader.load(request.previewMarkup)).asWinRT<FrameworkElement>()
              timing?.let { println("XAML preview markup loaded: " + it.elapsedNow().inWholeMilliseconds + "ms") }
              element.width = request.width.toDouble(); element.height = request.height.toDouble()
              element.requestedTheme = when (request.theme) { "Dark" -> ElementTheme.Dark; "Light" -> ElementTheme.Light; else -> ElementTheme.Default }
              stage = "attach the visual tree"
              window.content = element
              attached = true
              stage = "size the design surface"
              // Read DPI from the attached document, including the first frame
              // when the isolated desktop window has no placeholder content.
              val dpi = element.xamlRoot?.rasterizationScale ?: 1.0
              require(dpi.isFinite() && dpi > 0) { "The visual root has an invalid DPI scale." }
              ${if (framework == XamlFramework.WinUI) """requireNotNull(window.appWindow).resizeClient(windows.graphics.SizeInt32(
                (request.width * dpi).toInt(), (request.height * dpi).toInt()))""" else """windows.ui.viewmanagement.ApplicationView.getForCurrentView()?.tryResizeView(
                windows.foundation.Size(request.width.toFloat(), request.height.toFloat()))"""}
              stage = "lay out the visual tree"
              // UWP defers the first Window content layout until rendering. Establish
              // the requested artboard before capture chooses its bounded pixel size.
              element.measure(windows.foundation.Size(request.width.toFloat(), request.height.toFloat()))
              element.arrange(windows.foundation.Rect(0f, 0f, request.width.toFloat(), request.height.toFloat()))
              element.updateLayout()
              timing?.let { println("XAML preview layout completed: " + it.elapsedNow().inWholeMilliseconds + "ms") }
              traceCapture = !tracedPreview
              tracedPreview = true
            } catch (error: Exception) {
              // Deferred DataTemplate loading can fail during Measure/Arrange after
              // attachment. Detach that poisoned tree before returning to the SDK
              // message loop, which would otherwise retry layout and terminate.
              if (attached) try { window.content = previousContent }
              catch (rollback: Exception) { error.addSuppressed(rollback) }
              throw IllegalStateException("WinUI could not ${'$'}stage: ${'$'}{error.message}", error)
            }
          },
          clock = { activeStoryboard?.let { storyboard ->
            WinUIXamlPreviewClock(storyboard.getCurrentTime().inWholeMilliseconds.coerceIn(0, 86_400_000).toInt(),
              playing && storyboard.getCurrentState() == ${framework.xamlKotlinNamespace}.media.animation.ClockState.Active)
          } },
          animate = { root, command ->
            // The SDK owns interpolation, easing, dependent animations and clock state.
            // Resolve before stopping the previous clock, so a stale key is harmless.
            val element = root.asWinRT<FrameworkElement>()
            val owner = if (command.owner.isEmpty()) element else
              requireNotNull(element.findName(command.owner)) { "The storyboard resource owner no longer exists." }.asWinRT<FrameworkElement>()
            val storyboard = requireNotNull(owner.resources?.get(command.key)) { "The storyboard resource no longer exists." }.asWinRT<Storyboard>()
            activeStoryboard?.stop(); activeStoryboard = null; playing = false
            if (command.action != "Stop") {
              try {
                storyboard.begin()
                storyboard.pause()
                storyboard.seekAlignedToLastTick(command.positionMillis.milliseconds)
                if (command.action == "Play") storyboard.resume()
                activeStoryboard = storyboard
                playing = command.action == "Play"
              } catch (error: Exception) { storyboard.stop(); throw error }
            }
          },
        )
        }
    """.trimIndent())
    if (application) {
        file.parent.resolve("KotlinWinUIXamlPreviewHost.kt").writeText("""
            package io.github.composefluent.winrt.generated.xaml
            import ${framework.xamlKotlinNamespace}.*
        ${if (framework == XamlFramework.UWP) "import windows.applicationmodel.activation.LaunchActivatedEventArgs" else ""}
            import io.github.composefluent.winrt.runtime.*
            // The authoring scanner emits ApplicationOverrides for internal classes.
            // A private subclass would never receive WinUI's OnLaunched callback.
            internal class KotlinWinUIXamlPreviewApplication : Application() {
          init {
            when (winUIXamlPreviewStartupTheme()) {
              "Light" -> requestedTheme = ApplicationTheme.Light
              "Dark" -> requestedTheme = ApplicationTheme.Dark
            }
          }
              override fun onLaunched(args: LaunchActivatedEventArgs?) { KotlinWinUIXamlPreviewHost.showWindow() }
            }
            /** Isolated design host: the user's application main/constructor is not invoked. */
            object KotlinWinUIXamlPreviewHost {
              private var application: Application? = null
              private var window: Window? = null
              @kotlin.jvm.JvmStatic fun main(args: Array<String>) {
                XamlRuntime.initialize(XamlFramework.${framework.name})
                Application.start {
                  ${registrars.joinToString("\n                  ") { "$it.registerAll()" }}
                  application = KotlinWinUIXamlPreviewApplication()
                }
                window = null; application = null
              }
              internal fun showWindow() {
                // The native Application resource dictionary is ready after Start's
                // initialization callback returns, when OnLaunched is dispatched.
                ${if (framework == XamlFramework.WinUI) "requireNotNull(requireNotNull(requireNotNull(application).resources).mergedDictionaries).add(microsoft.ui.xaml.controls.XamlControlsResources())" else ""}
                window = ${xamlPreviewWindowSource(framework).prependIndent("                ").trimStart()}
              }
            }
        """.trimIndent())
    }
    return name
}
