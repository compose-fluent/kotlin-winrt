package io.github.composefluent.winrt.gallery

import windows.applicationmodel.activation.*
import io.github.composefluent.winrt.runtime.asWinRT
import windows.ui.viewmanagement.ApplicationView
import windows.ui.xaml.Application
import windows.ui.xaml.ApplicationTheme
import windows.ui.xaml.Window

// The Windows SDK UWP activation contract owns Window.Current; the desktop
// AppInstance/AppWindow implementation is compiled only for the WinUI 3 variant.
class GalleryApplication : Application() {
    private var currentShell: MainWindow? = null
    override fun onActivated(args: IActivatedEventArgs?) {
        when (args?.kind) {
            ActivationKind.Protocol -> {
                val protocol = args.asWinRT<ProtocolActivatedEventArgs>()
                val uri = protocol.uri
                galleryLog("Activation: protocol; uri=${uri?.absoluteUri}; previous=${protocol.previousExecutionState}")
                val route = uri?.host?.let(GalleryNavigationHost::resolveRoute)
                showShell(route ?: "UWPActivation")
            }
            ActivationKind.ToastNotification -> {
                val toast = args.asWinRT<ToastNotificationActivatedEventArgs>()
                galleryLog("Activation: toast; arguments=${toast.argument}; inputs=${toast.userInput?.size}")
                showShell("AppNotification")
            }
            else -> super.onActivated(args)
        }
    }
    override fun onFileActivated(args: FileActivatedEventArgs?) = try {
        galleryLog("Activation: file callback entered")
        galleryLog("Activation: file; names=${args?.files?.map { it?.name }}; previous=${args?.previousExecutionState}")
        showShell("UWPActivation")
    } catch (error: Throwable) {
        galleryLog("File activation failed: ${error.stackTraceToString()}")
        throw error
    }
    override fun onBackgroundActivated(args: BackgroundActivatedEventArgs?) {
        val instance = checkNotNull(args?.taskInstance)
        val deferral = instance.getDeferral()
        try {
            galleryLog("Activation: background; task=${instance.task?.name}")
            GalleryPreferences.put("LastBackgroundActivation", instance.task?.name.orEmpty())
        } finally { deferral?.complete() }
    }
    override fun initializeComponent() {
        // Set the startup palette before UWP creates application resources.
        // Setting only the shell's RequestedTheme leaves cold-start resource
        // brushes in the system palette until the first live theme change.
        when (GalleryPreferences.text("Theme")) {
            "Dark" -> requestedTheme = ApplicationTheme.Dark
            "Light" -> requestedTheme = ApplicationTheme.Light
        }
        super.initializeComponent()
        unhandledException.add { _, args -> galleryLog("Unhandled: ${args?.message}") }
        enteredBackground.add { _, _ -> galleryLog("Lifecycle: entered background") }
        leavingBackground.add { _, _ -> galleryLog("Lifecycle: leaving background") }
        suspending.add { _, _ -> galleryLog("Lifecycle: suspending") }
        resuming.add { _, _ -> galleryLog("Lifecycle: resuming") }
    }

    override fun onLaunched(args: LaunchActivatedEventArgs?) = try {
        galleryLog("Launch: previous state=${args?.previousExecutionState}; arguments=${args?.arguments.orEmpty()}")
        showShell(args?.arguments.orEmpty().ifBlank { "Home" })
    } catch (error: Throwable) {
        galleryLog("Launch failed: ${error.stackTraceToString()}")
        throw error
    }

    private fun showShell(route: String) {
        val window = checkNotNull(Window.current)
        val shell = currentShell ?: MainWindow().also {
            currentShell = it
            window.content = it
            // SetTitleBar needs the drag element to belong to this window's visual tree.
            it.configureUWPTitleBar()
            GalleryWindows.track(window)
        }
        checkNotNull(ApplicationView.getForCurrentView()).title = if (galleryTargetName == "mingw") "Kotlin/Native" else "Kotlin/JVM"
        if (route == "--validate-controls") {
            if (shell.isLoaded) {
                shell.galleryDispatcher.tryEnqueue { validatePages(shell, 0) }
            } else {
                var started = false
                shell.loaded.add { _, _ ->
                    if (!started) { started = true; validatePages(shell, 0) }
                }
            }
        }
        window.activate()
        GalleryNavigationHost.navigate(GalleryXamlValidation.routeArgument(if (route == "--validate-controls") "Home" else route))
        galleryLog("Activated; ${GalleryCatalog.pages.size} shared sample pages")
    }

    // Explicit construction/layout smoke; no Loaded handlers, camera or picker actions are invoked.
    private fun validatePages(shell: MainWindow, index: Int, failures: Int = 0) {
        val page = GalleryCatalog.pages.getOrNull(index) ?: run {
            galleryLog("Shared catalog validation finished: ${GalleryCatalog.pages.size - failures} passed, $failures failed")
            return
        }
        var nextFailures = failures
        galleryLog("Validating ${page.id}")
        try {
            val element = checkNotNull(GalleryPageFactories.create(page.id))
            element.measure(windows.foundation.Size(1000f, 700f))
            element.arrange(windows.foundation.Rect(0f, 0f, 1000f, 700f))
            galleryLog("PASS ${page.id}")
        } catch (error: Throwable) {
            nextFailures++
            galleryLog("FAIL ${page.id}: ${error.stackTraceToString()}")
        }
        check(shell.galleryDispatcher.tryEnqueue { validatePages(shell, index + 1, nextFailures) })
    }
}
