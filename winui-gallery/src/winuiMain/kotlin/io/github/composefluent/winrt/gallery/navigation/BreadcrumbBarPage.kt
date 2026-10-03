package io.github.composefluent.winrt.gallery.navigation

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "BreadcrumbBar", title = "BreadcrumbBar", group = "Navigation", order = 0)
internal class BreadcrumbBarPage : Page() {
    private val defaultFolders: List<Folder> = listOf("Home", "Folder1", "Folder2", "Folder3").map(::Folder)
    val Folders: MutableList<Folder> = WinRTObservableList(defaultFolders)
    val FoldersString: List<String> = listOf("Home", "Documents", "Design", "Northwind", "Images", "Folder1", "Folder2", "Folder3")
    override fun initializeComponent() { super.initializeComponent(); BreadcrumbBar2.itemClicked.add(::BreadcrumbBar2_ItemClicked) }
    private fun BreadcrumbBar2_ItemClicked(sender: BreadcrumbBar, args: BreadcrumbBarItemClickedEventArgs) {
        for (index in Folders.lastIndex downTo args.index.toInt() + 1) Folders.removeAt(index)
    }
    private fun ResetSampleButton_Click(sender: Any?, args: RoutedEventArgs) {
        defaultFolders.filterNot(Folders::contains).forEach(Folders::add)
        announce(ResetSampleBtn, "BreadcrumbBar sample reset successful.", "BreadCrumbBarSampleResetNotificationId")
    }
}
