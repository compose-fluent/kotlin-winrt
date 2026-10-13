package io.github.composefluent.winrt.gallery

import windows.applicationmodel.activation.LaunchActivatedEventArgs
import windows.ui.viewmanagement.ApplicationView
import windows.ui.xaml.Application
import windows.ui.xaml.Window

// The Windows SDK UWP activation contract owns Window.Current; the desktop
// AppInstance/AppWindow implementation is compiled only for the WinUI 3 variant.
class GalleryApplication : Application() {
    override fun initializeComponent() {
        super.initializeComponent()
        unhandledException.add { _, args -> galleryLog("Unhandled: ${args?.message}") }
    }

    override fun onLaunched(args: LaunchActivatedEventArgs?) {
        val window = checkNotNull(Window.current)
        val shell = GalleryShell()
        window.content = shell
        checkNotNull(ApplicationView.getForCurrentView()).title = if (galleryTargetName == "mingw") "Kotlin/Native" else "Kotlin/JVM"
        val route = args?.arguments.orEmpty().ifBlank { "Home" }
        if (route == "--validate-controls") {
            var validated = false
            shell.loaded.add { _, _ ->
                if (!validated) {
                    validated = true
                    shell.validateControls()
                }
            }
        }
        window.activate()
        shell.navigate(route)
        galleryLog("Activated; ${winUi2Samples.size} sample pages")
    }
}
