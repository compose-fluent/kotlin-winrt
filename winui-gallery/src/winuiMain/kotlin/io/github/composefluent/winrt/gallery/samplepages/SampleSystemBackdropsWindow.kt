// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*
import microsoft.ui.windowing.*
import windows.graphics.*
import windows.ui.Color
import kotlinx.coroutines.delay

internal class SampleSystemBackdropsWindow : Window() {
    private var ready = false
    private var currentBackdrop = GalleryBackdropType.None
    private fun root(): Grid = checkNotNull(content).asWinRT<Grid>()
    private fun SetNoneBackdropBackground() { root().background = SolidColorBrush(if (currentBackdrop == GalleryBackdropType.None && themeComboBox.selectedIndex != 0) { if (themeComboBox.selectedIndex == 1) rgb(0xFFFFFFu) else rgb(0u) } else Color(0u,0u,0u,0u)) }
    private fun ThemeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        root().requestedTheme = listOf(ElementTheme.Default,ElementTheme.Light,ElementTheme.Dark)[themeComboBox.selectedIndex.coerceIn(0,2)]
        TitleBarHelper.ApplySystemThemeToCaptionButtons(this,root().actualTheme); SetNoneBackdropBackground()
    }

    private var micaController: microsoft.ui.composition.systembackdrops.MicaController? = null
    private var acrylicController: microsoft.ui.composition.systembackdrops.DesktopAcrylicController? = null
    private var configuration: microsoft.ui.composition.systembackdrops.SystemBackdropConfiguration? = null
    private var allowed = emptyList<GalleryBackdropType>()
    var AllowedBackdrops: List<GalleryBackdropType>
        get() = allowed
        set(value) { allowed = value; backdropComboBox.itemsSource = value; backdropComboBox.selectedIndex = 0 }
    override fun initializeComponent() {
        super.initializeComponent(); GalleryWindows.track(this); ready = true; appWindow?.setIcon("Assets/Tiles/GalleryIcon.ico"); extendsContentIntoTitleBar = true; setTitleBar(titleBar)
        root().requestedTheme = when (GalleryPreferences.text("Theme")) { "Light" -> ElementTheme.Light; "Dark" -> ElementTheme.Dark; else -> ElementTheme.Default }
        themeComboBox.selectedIndex = 0
        activated.add { _, args -> configuration?.isInputActive = args.windowActivationState != WindowActivationState.Deactivated }
        closed.add { _, _ -> ResetControllers() }
        root().actualThemeChanged.add { _, _ -> SetConfigurationSourceTheme() }
        val themeSettings = microsoft.ui.system.ThemeSettings.createForWindowId(checkNotNull(appWindow).id)
        val token = themeSettings.changed.add { _, _ -> SetConfigurationSourceTheme() }
        closed.add { _, _ -> themeSettings.changed.remove(token) }
    }
    private fun ResetControllers() { micaController?.close(); micaController = null; acrylicController?.close(); acrylicController = null; configuration = null }
    private fun SetConfigurationSourceTheme() {
        configuration?.let { source -> source.isHighContrast = microsoft.ui.system.ThemeSettings.createForWindowId(checkNotNull(appWindow).id).highContrast; source.theme = if (root().actualTheme == ElementTheme.Dark) microsoft.ui.composition.systembackdrops.SystemBackdropTheme.Dark else microsoft.ui.composition.systembackdrops.SystemBackdropTheme.Light }
    }
    fun SetBackdrop(requested: GalleryBackdropType) {
        ResetControllers(); currentBackdrop = GalleryBackdropType.None; tbChangeStatus.text = ""
        var type = requested
        val config = microsoft.ui.composition.systembackdrops.SystemBackdropConfiguration().apply { isInputActive = true }
        configuration = config; SetConfigurationSourceTheme()
        if (type == GalleryBackdropType.Mica || type == GalleryBackdropType.MicaAlt) {
            if (microsoft.ui.composition.systembackdrops.MicaController.isSupported()) {
                micaController = microsoft.ui.composition.systembackdrops.MicaController().apply { kind = if (type == GalleryBackdropType.MicaAlt) microsoft.ui.composition.systembackdrops.MicaKind.BaseAlt else microsoft.ui.composition.systembackdrops.MicaKind.Base; addSystemBackdropTarget(this@SampleSystemBackdropsWindow.asWinRT<microsoft.ui.composition.ICompositionSupportsSystemBackdrop>()); setSystemBackdropConfiguration(config) }; currentBackdrop = type
            } else { tbChangeStatus.text += "  $type isn't supported. Trying Acrylic."; type = GalleryBackdropType.Acrylic }
        }
        if (type == GalleryBackdropType.Acrylic || type == GalleryBackdropType.AcrylicThin) {
            if (microsoft.ui.composition.systembackdrops.DesktopAcrylicController.isSupported()) {
                acrylicController = microsoft.ui.composition.systembackdrops.DesktopAcrylicController().apply { kind = if (type == GalleryBackdropType.AcrylicThin) microsoft.ui.composition.systembackdrops.DesktopAcrylicKind.Thin else microsoft.ui.composition.systembackdrops.DesktopAcrylicKind.Base; addSystemBackdropTarget(this@SampleSystemBackdropsWindow.asWinRT<microsoft.ui.composition.ICompositionSupportsSystemBackdrop>()); setSystemBackdropConfiguration(config) }; currentBackdrop = type
            } else tbChangeStatus.text += "  $type isn't supported. Switching to default color."
        }
        SetNoneBackdropBackground(); announce(backdropComboBox,"Background changed to $currentBackdrop","BackgroundChangedNotificationActivityId")
    }
    private fun BackdropComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) (backdropComboBox.selectedItem as? GalleryBackdropType)?.let(::SetBackdrop) }

}
