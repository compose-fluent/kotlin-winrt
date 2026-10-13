package io.github.composefluent.winrt.samples.uwp

import windows.applicationmodel.activation.LaunchActivatedEventArgs
import windows.ui.xaml.Application
import windows.ui.xaml.Window

// UWP owns Window.Current and delivers activation through Application, as in the
// Windows SDK C++/WinRT UWP template; no desktop Window or WASDK bootstrap is needed.
class UwpApplication : Application() {
    override fun initializeComponent() {
        super.initializeComponent()
        unhandledException.add { _, args -> sampleLog("Unhandled: ${args?.message}") }
    }

    override fun onLaunched(args: LaunchActivatedEventArgs?) {
        try {
            val window = checkNotNull(Window.current)
            window.content = MainPage()
            window.activate()
            sampleLog("Activated; arguments=${args?.arguments.orEmpty()}")
        } catch (error: Throwable) {
            sampleLog(error.stackTraceToString())
            throw error
        }
    }
}
