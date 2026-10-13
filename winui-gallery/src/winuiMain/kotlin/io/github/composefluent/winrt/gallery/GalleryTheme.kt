package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
// #if WINUI3
import microsoft.ui.system.ThemeSettings
// #endif
import microsoft.ui.xaml.media.Brush
import microsoft.ui.xaml.media.SolidColorBrush
import microsoft.ui.xaml.media.AcrylicBrush
import microsoft.ui.xaml.media.LinearGradientBrush
import microsoft.ui.xaml.media.GradientStop
import windows.ui.Color
import windows.ui.viewmanagement.AccessibilitySettings
import windows.ui.viewmanagement.UISettings
import windows.ui.viewmanagement.UIElementType
import windows.ui.viewmanagement.UIColorType

/** Reads SDK resources through their projected WinRT map API. */
internal object GalleryTheme {
    private var root: FrameworkElement? = null
    private val accessibility = AccessibilitySettings()
    private val uiSettings = UISettings()
// #if WINUI3
    private var themeSettings: ThemeSettings? = null
    val highContrast: Boolean get() = themeSettings?.highContrast ?: accessibility.highContrast
// #else
    val highContrast: Boolean get() = accessibility.highContrast
// #endif
    private val changeListeners = linkedSetOf<() -> Unit>()
    private val brushes = mutableMapOf<String, Brush>()
    // WinUI 3 dropped the legacy SystemControl* brush keys used by a few
    // Gallery samples.  Resolve those names to their WinUI 3 design tokens so
    // projection callbacks can construct their visuals without throwing from
    // inside a native ItemsRepeater callback.
    private val legacyResourceAliases = mapOf(
        "SystemControlBackgroundAccentBrush" to "AccentFillColorDefaultBrush",
        "SystemControlBackgroundBaseLowBrush" to "SolidBackgroundFillColorBaseBrush",
        "SystemControlBackgroundChromeLowBrush" to "ControlFillColorDefaultBrush",
        "SystemControlBackgroundChromeMediumBrush" to "ControlFillColorSecondaryBrush",
        "SystemControlBackgroundChromeMediumLowBrush" to "ControlFillColorTertiaryBrush",
        "SystemControlForegroundAltHighBrush" to "TextFillColorPrimaryBrush",
        "SystemControlForegroundBaseHighBrush" to "TextFillColorPrimaryBrush",
        "SystemControlForegroundChromeWhiteBrush" to "TextOnAccentFillColorPrimaryBrush",
    )
    internal class SampleState {
        var sourceRoute: String? = null
        var sourceExampleIndex: Int = 0
        val sampleBodies = mutableListOf<FrameworkElement>()
        val brushes = mutableMapOf<String, Brush>()
        fun actualTheme(element: FrameworkElement): ElementTheme = sampleBodies.firstOrNull()?.actualTheme ?: element.actualTheme
    }
    internal class SamplePage(val element: FrameworkElement, private val state: SampleState) {
        fun toggleTheme() {
            val bodies = state.sampleBodies.ifEmpty { listOf(element) }
            val next = if (bodies.first().actualTheme == ElementTheme.Dark) ElementTheme.Light else ElementTheme.Dark
            bodies.forEach { it.requestedTheme = next }
            refreshBrushes(state.brushes, next)
        }
    }
    private val samples = mutableMapOf<FrameworkElement, SampleState>()
    // Construction bookkeeping for source snippets and manually resolved brushes;
    // RequestedTheme inheritance remains the framework's responsibility.
    internal var sampleBeingConstructed: SampleState? = null
        private set

    fun createSample(factory: () -> UIElement): SamplePage {
        val state = SampleState()
        val previous = sampleBeingConstructed
        sampleBeingConstructed = state
        val element = try {
            factory().asWinRT<FrameworkElement>()
        } finally {
            sampleBeingConstructed = previous
        }
        state.sampleBodies.forEach { body ->
            body.actualThemeChanged.add { _, _ -> refreshBrushes(state.brushes, body.actualTheme) }
        }
        element.loaded.add { _, _ ->
            samples[element] = state
            refreshBrushes(state.brushes, state.actualTheme(element))
        }
        element.actualThemeChanged.add { _, _ -> refreshBrushes(state.brushes, state.actualTheme(element)) }
        element.unloaded.add { _, _ -> samples.remove(element) }
        // The header can change theme before the first Loaded event.
        samples[element] = state
        return SamplePage(element, state)
    }

    fun observe(element: FrameworkElement, changed: () -> Unit) {
        element.loaded.add { _, _ -> changeListeners.add(changed); changed() }
        element.unloaded.add { _, _ -> changeListeners.remove(changed) }
    }

    fun attach(element: FrameworkElement, window: Window) {
        root = element
        element.actualThemeChanged.add { _, _ -> safeRefresh("actualThemeChanged") }
        // AccessibilitySettings.HighContrastChanged and UISettings.ColorValuesChanged
        // require UWP UI infrastructure. Desktop windows use the App SDK contract:
        // https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.system.themesettings
// #if WINUI3
        val settings = ThemeSettings.createForWindowId(checkNotNull(window.appWindow).id)
        themeSettings = settings
        val token = settings!!.changed.add { _, _ -> element.galleryDispatcher?.tryEnqueue { safeRefresh("ThemeSettings.changed") } }
        window.activated.add { _, _ -> safeRefresh("window.activated") }
        window.closed.add { _, _ ->
            settings.changed.remove(token)
            themeSettings = null; changeListeners.clear(); samples.clear(); root = null
        }

// #else
        val contrastToken = accessibility.highContrastChanged.add { _, _ -> element.galleryDispatcher.tryEnqueue { safeRefresh("highContrastChanged") } }
        val colorToken = uiSettings.colorValuesChanged.add { _, _ -> element.galleryDispatcher.tryEnqueue { safeRefresh("colorValuesChanged") } }
        window.closed.add { _, _ ->
            accessibility.highContrastChanged.remove(contrastToken)
            uiSettings.colorValuesChanged.remove(colorToken)
            changeListeners.clear(); samples.clear(); root = null
        }
// #endif
    }

    fun resource(name: String, requestedTheme: ElementTheme? = null): Any {
        val theme = when {
            highContrast -> "HighContrast"
            (requestedTheme ?: root?.actualTheme) == ElementTheme.Dark -> "Dark"
            else -> "Light"
        }
        val appResources = checkNotNull(checkNotNull(Application.current).resources)
        val hasRequestedTheme = requestedTheme != null
        fun resolve(key: String): Any? =
            (if (hasRequestedTheme) lookupThemeResource(appResources, key, theme) else null)
                ?: lookup(appResources, key)

        val value = resolve(name)
            ?: legacyResourceAliases[name]?.let(::resolve)
            ?: systemColor(name)
        return checkNotNull(value) { "Missing WinUI resource: $name ($theme)" }
    }

    private fun lookupThemeResource(dictionary: ResourceDictionary, key: String, theme: String): Any? {
        val variants = dictionary.themeDictionaries
        val selected = if (variants!!.containsKey(theme)) variants[theme] else variants["Default"]
        selected?.asWinRT<ResourceDictionary>()?.let { lookup(it, key) }?.let { return it }
        for (merged in dictionary.mergedDictionaries!!.reversed()) {
            lookupThemeResource(merged!!.asWinRT<ResourceDictionary>(), key, theme)?.let { return it }
        }
        return null
    }

    private fun lookup(dictionary: ResourceDictionary, key: String): Any? {
        return if (dictionary.containsKey(key)) dictionary[key] else null
    }

    private fun systemColor(name: String): Color? = when (name) {
        "SystemColorWindowColor" -> uiSettings.uIElementColor(UIElementType.Window)
        "SystemColorWindowTextColor" -> uiSettings.uIElementColor(UIElementType.WindowText)
        "SystemColorButtonFaceColor" -> uiSettings.uIElementColor(UIElementType.ButtonFace)
        "SystemColorButtonTextColor" -> uiSettings.uIElementColor(UIElementType.ButtonText)
        "SystemColorGrayTextColor" -> uiSettings.uIElementColor(UIElementType.GrayText)
        "SystemColorHighlightColor" -> uiSettings.uIElementColor(UIElementType.Highlight)
        "SystemColorHighlightTextColor" -> uiSettings.uIElementColor(UIElementType.HighlightText)
        "SystemColorHotlightColor" -> uiSettings.uIElementColor(UIElementType.Hotlight)
        "SystemAccentColor" -> uiSettings.getColorValue(UIColorType.Accent)
        "SystemAccentColorLight1" -> uiSettings.getColorValue(UIColorType.AccentLight1)
        "SystemAccentColorLight2" -> uiSettings.getColorValue(UIColorType.AccentLight2)
        "SystemAccentColorLight3" -> uiSettings.getColorValue(UIColorType.AccentLight3)
        "SystemAccentColorDark1" -> uiSettings.getColorValue(UIColorType.AccentDark1)
        "SystemAccentColorDark2" -> uiSettings.getColorValue(UIColorType.AccentDark2)
        "SystemAccentColorDark3" -> uiSettings.getColorValue(UIColorType.AccentDark3)
        else -> null
    }

    fun brush(name: String): Brush {
        val source = resource(name)
        return (sampleBeingConstructed?.brushes ?: brushes).getOrPut(name) {
            if (name.contains("Acrylic", ignoreCase = true)) AcrylicBrush().apply { updateAcrylic(source, this) }
            else if (name.contains("ElevationBorder")) LinearGradientBrush().apply { updateGradient(source, this) }
            else when (source) {
                is Color -> SolidColorBrush(source)
                is SolidColorBrush -> SolidColorBrush(source.color)
                is AcrylicBrush -> AcrylicBrush().apply { copyAcrylic(source, this) }
                is LinearGradientBrush -> LinearGradientBrush().apply { updateGradient(source, this) }
                else -> source.asWinRT<Brush>()
            }
        }
    }

    private fun refresh() {
        refreshBrushes(brushes, root?.actualTheme ?: ElementTheme.Light)
        samples.forEach { (element, state) -> refreshBrushes(state.brushes, state.actualTheme(element)) }
        changeListeners.toList().forEach { it() }
    }

    private fun safeRefresh(source: String) {
        try {
            refresh()
        } catch (error: Exception) {
            println("Kotlin WinUI Gallery: theme refresh failed ($source): ${error.message}")
        }
    }

    // Programmatically assigned brushes are static references: RequestedTheme
    // updates control templates, while custom surfaces need their
    // existing brush values refreshed for the owning visual subtree.
    private fun refreshBrushes(brushes: Map<String, Brush>, theme: ElementTheme) {
        brushes.forEach { (key, brush) ->
            val value = resource(key, theme)
            when (brush) {
                is SolidColorBrush -> brush.color = if (value is Color) value else value.asWinRT<SolidColorBrush>().color
                is AcrylicBrush -> updateAcrylic(value, brush)
                is LinearGradientBrush -> updateGradient(value, brush)
            }
        }
    }

    private fun updateGradient(source: Any, destination: LinearGradientBrush) {
        val solid = when (source) { is Color -> source; is SolidColorBrush -> source.color; else -> null }
        destination.gradientStops!!.clear()
        if (solid != null) {
            destination.gradientStops!!.add(GradientStop().apply { color = solid; offset = 0.0 })
            destination.gradientStops!!.add(GradientStop().apply { color = solid; offset = 1.0 })
        } else {
            val gradient = source.asWinRT<LinearGradientBrush>()
            destination.startPoint = gradient.startPoint; destination.endPoint = gradient.endPoint
            destination.mappingMode = gradient.mappingMode; destination.spreadMethod = gradient.spreadMethod
            destination.colorInterpolationMode = gradient.colorInterpolationMode; destination.opacity = gradient.opacity
            gradient.gradientStops!!.forEach { stop -> destination.gradientStops!!.add(GradientStop().apply { color = stop!!.color; offset = stop.offset }) }
        }
    }

    private fun updateAcrylic(source: Any, destination: AcrylicBrush) {
        when (source) {
            is Color -> { destination.fallbackColor = source; destination.alwaysUseFallback = true }
            is SolidColorBrush -> { destination.fallbackColor = source.color; destination.alwaysUseFallback = true }
            else -> copyAcrylic(source.asWinRT(), destination)
        }
    }

    private fun copyAcrylic(source: AcrylicBrush, destination: AcrylicBrush) {
        destination.tintColor = source.tintColor; destination.tintOpacity = source.tintOpacity
        destination.tintLuminosityOpacity = source.tintLuminosityOpacity
        destination.fallbackColor = source.fallbackColor; destination.alwaysUseFallback = source.alwaysUseFallback
        destination.opacity = source.opacity
    }
}
