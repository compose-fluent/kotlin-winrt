package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page
import windows.foundation.Uri
import windows.ui.startscreen.JumpList
import windows.ui.startscreen.JumpListItem

@GalleryPage(route = "JumpList", title = "JumpList", group = "Shell", order = 2)
internal class JumpListPage : Page() {
    private val tasks = GalleryPageTasks(this)

    override fun initializeComponent() {
        super.initializeComponent()
        val supported = JumpList.isSupported()
        PackageWarning.isOpen = !supported
        AddTasksButton.isEnabled = supported
        ClearTasksButton.isEnabled = supported
        AddCustomGroupButton.isEnabled = supported
    }

    private fun AddTasksButton_Click(sender: Any?, args: RoutedEventArgs) {
        if (!JumpList.isSupported()) return
        tasks.launch {
            val list = JumpList.loadCurrentAsync().await()
            list.items.add(JumpListItem.createWithArguments("/compose", "New Message").apply {
                description = "Compose a new message"
                logo = Uri("ms-appx:///Assets/AppList.targetsize-48.png")
            })
            list.items.add(JumpListItem.createWithArguments("/search", "Search").apply {
                description = "Search for items"
                logo = Uri("ms-appx:///Assets/AppList.targetsize-48.png")
            })
            list.saveAsync().await()
        }
    }

    private fun ClearTasksButton_Click(sender: Any?, args: RoutedEventArgs) {
        if (!JumpList.isSupported()) return
        tasks.launch {
            val list = JumpList.loadCurrentAsync().await()
            list.items.clear()
            list.saveAsync().await()
        }
    }

    private fun AddCustomGroupButton_Click(sender: Any?, args: RoutedEventArgs) {
        if (!JumpList.isSupported()) return
        tasks.launch {
            val list = JumpList.loadCurrentAsync().await()
            list.items.add(JumpListItem.createWithArguments("/project-alpha", "Project Alpha").apply {
                groupName = "Projects"
                description = "Open Project Alpha"
                logo = Uri("ms-appx:///Assets/AppList.targetsize-48.png")
            })
            list.items.add(JumpListItem.createWithArguments("/project-beta", "Project Beta").apply {
                groupName = "Projects"
                description = "Open Project Beta"
                logo = Uri("ms-appx:///Assets/AppList.targetsize-48.png")
            })
            list.saveAsync().await()
        }
    }
}
