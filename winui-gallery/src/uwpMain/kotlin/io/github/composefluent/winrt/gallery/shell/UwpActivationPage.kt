package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.await
import windows.applicationmodel.background.*
import windows.foundation.Uri
import windows.storage.*
import windows.system.Launcher
import windows.ui.xaml.RoutedEventArgs
import windows.ui.xaml.controls.Page

@GalleryPage(route = "UwpActivation", title = "UWP activation", group = "Shell", order = 10)
internal class UwpActivationPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private val taskName = "KotlinGallery.ApplicationTrigger"
    private fun Protocol_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        val result = checkNotNull(Launcher.launchUriAsync(Uri("kotlin-winrt-gallery://UwpActivation"))).await()
        Status.text = "Protocol launch requested: $result. See gallery.log for OnActivated."
    } }
    private fun File_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        val folder = checkNotNull(ApplicationData.current?.temporaryFolder)
        val file = checkNotNull(folder.createFileAsync("activation.kwgallery", CreationCollisionOption.ReplaceExisting)).await()
        checkNotNull(FileIO.writeTextAsync(file, "Kotlin UWP file activation sample")).await()
        val result = checkNotNull(Launcher.launchFileAsync(file)).await()
        Status.text = "File launch requested: $result. See gallery.log for OnFileActivated."
    } }
    private fun Background_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        val access = checkNotNull(BackgroundExecutionManager.requestAccessAsync()).await()
        if (access == BackgroundAccessStatus.DeniedByUser || access == BackgroundAccessStatus.DeniedBySystemPolicy || access == BackgroundAccessStatus.Denied || access == BackgroundAccessStatus.Unspecified) {
            Status.text = "Background access: $access"
            return@launch
        }
        BackgroundTaskRegistration.allTasks?.values?.filter { it?.name == taskName }?.forEach { it?.unregister(true) }
        val trigger = ApplicationTrigger()
        val registration = checkNotNull(BackgroundTaskBuilder().apply {
            name = taskName
            // No TaskEntryPoint: UWP invokes Application.OnBackgroundActivated in-process.
            setTrigger(trigger)
        }.register())
        registration.completed.add { _, completed ->
            try {
                completed?.checkResult()
                galleryLog("Background task completed: $taskName")
                galleryDispatcher.tryEnqueue { Status.text = "Background callback completed: ${GalleryPreferences.text("LastBackgroundActivation")}" }
            } catch (error: Exception) {
                galleryLog("Background task failed: $error")
                galleryDispatcher.tryEnqueue { Status.text = "Background task failed: ${error.message}" }
            } finally { registration.unregister(false) }
        }
        Status.text = "Requesting background activation..."
        val result = checkNotNull(trigger.requestAsync()).await()
        galleryLog("Background trigger requested: $result; access=$access")
        if (result != ApplicationTriggerResult.Allowed) {
            Status.text = "Background trigger requested: $result"
            registration.unregister(false)
        }
    } }
}
