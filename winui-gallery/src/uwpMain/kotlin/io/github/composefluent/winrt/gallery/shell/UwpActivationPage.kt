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
    private fun Component_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        if (galleryTargetName != "mingw") {
            Status.text = "The background component sample currently targets Kotlin/Native."
            return@launch
        }
        registerScheduledTask("KotlinGallery.Component", SystemTrigger(SystemTriggerType.TimeZoneChange, true),
            "io.github.composefluent.winrt.samples.background.GalleryBackgroundTask")
    } }
    private fun Timer_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        registerScheduledTask("KotlinGallery.TimeTrigger", TimeTrigger(15u, true))
    } }
    private fun System_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        registerScheduledTask("KotlinGallery.SystemTrigger", SystemTrigger(SystemTriggerType.TimeZoneChange, true))
    } }
    private fun Clear_Click(sender: Any?, args: RoutedEventArgs?) {
        BackgroundTaskRegistration.allTasks?.values?.filter { it?.name?.startsWith("KotlinGallery.") == true }
            ?.forEach { it?.unregister(true) }
        Status.text = "Gallery background tasks removed."
    }
    private suspend fun registerScheduledTask(name: String, trigger: IBackgroundTrigger, entryPoint: String? = null) {
        val access = checkNotNull(BackgroundExecutionManager.requestAccessAsync()).await()
        val receiptKey = if (entryPoint == null) "LastBackgroundActivation" else "LastOutOfProcessBackgroundActivation"
        GalleryPreferences.put(receiptKey, "")
        BackgroundTaskRegistration.allTasks?.values?.filter { it?.name == name }?.forEach { it?.unregister(true) }
        val registration = checkNotNull(BackgroundTaskBuilder().apply {
            this.name = name
            if (entryPoint != null) taskEntryPoint = entryPoint
            setTrigger(trigger)
        }.register())
        registration.completed.add { _, completed ->
            try {
                completed?.checkResult()
                galleryLog("Background task completed: $name")
                val receipt = GalleryPreferences.text(receiptKey)
                check(receipt == name) { "Background completion has no matching callback receipt: $receipt" }
                galleryLog("Background task receipt: $name; value=$receipt")
                galleryDispatcher.tryEnqueue { Status.text = "Background callback completed: $name; receipt=$receipt" }
            } catch (error: Exception) {
                galleryLog("Background task failed: $name; $error")
            } finally { registration.unregister(false) }
        }
        galleryLog("Background task registered: $name; id=${registration.taskId}; access=$access")
        Status.text = "$name registered. Timer runs once after 15–30 minutes; system trigger runs on a time-zone change."
    }
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
