package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.ContentDialog
import microsoft.windows.appnotifications.AppNotificationManager
import microsoft.windows.appnotifications.AppNotificationActivatedEventArgs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import windows.foundation.Uri
import windows.ui.startscreen.JumpList
import windows.ui.startscreen.JumpListItem
import windows.ui.startscreen.JumpListSystemGroupKind

private val jumpListUpdates = Mutex()

internal suspend fun updateGalleryJumpList() {
    if (!GalleryPreferences.packaged || !JumpList.isSupported()) return
    // Loading the shell and navigating to the initial route can request updates
    // together. Keep the whole load/save transaction exclusive across windows.
    jumpListUpdates.withLock {
        val list = checkNotNull(checkNotNull(JumpList.loadCurrentAsync()).await())
        list.items!!.clear(); list.systemGroupKind = JumpListSystemGroupKind.None
        for (group in listOf("Recent", "Favorites")) {
            GalleryPreferences.routes(group).forEach { id ->
                val page = GalleryCatalog.pages.firstOrNull { it.id == id } ?: return@forEach
                list.items!!.add(JumpListItem.createWithArguments(id, page.title)!!.apply {
                    groupName = group; description = "Go to ${page.title}"
                    logo = Uri(page.image.ifBlank { "ms-appx:///Assets/AppList.png" })
                })
            }
        }
        list.saveAsync()!!.await()
    }
}

// Activation ordering: https://learn.microsoft.com/windows/apps/develop/notifications/app-notifications/app-notifications-quickstart
internal class GalleryNotifications(private val window: Window, private val shell: MainWindow) {
    private val manager = checkNotNull(AppNotificationManager.default)
    private val tasks = GalleryPageTasks(shell.root)
    private val dialogs = Mutex()
    private val pending = mutableListOf<String>()
    private val token = manager.notificationInvoked.add { _, args -> handle(checkNotNull(args)) }
    private val loadedToken = shell.root.loaded.add { _, _ -> showPending() }
    fun handle(args: AppNotificationActivatedEventArgs) {
        val arguments = args.argument
        val input = args.userInput!!.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        shell.root.dispatcherQueue?.tryEnqueue {
            window.activate()
            GalleryNavigationHost.navigate("AppNotification")
            pending += listOf(arguments, input).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "You selected the notification." }
            if (shell.root.isLoaded) showPending()
        }
    }
    private fun showPending() {
        val messages = pending.toList(); pending.clear()
        messages.forEach { message -> tasks.launch {
            dialogs.withLock {
                val dialog = ContentDialog().apply {
                    xamlRoot = shell.root.xamlRoot; title = "Notification activated"
                    content = message; closeButtonText = "Close"
                }
                try { dialog.showAsync()!!.await() } finally { dialog.hide() }
            }
        } }
    }
    init { manager.register() }
    fun close() {
        manager.notificationInvoked.remove(token); shell.root.loaded.remove(loadedToken)
        pending.clear(); manager.unregister()
    }
}
