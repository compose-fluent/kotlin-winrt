package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "TreeView", title = "TreeView", group = "Collections", order = 7)
internal class TreeViewPage : Page() {
    val DataSource: MutableList<ExplorerItem> = WinRTObservableList(listOf(
        ExplorerItem("Documents", true, listOf(ExplorerItem("ProjectProposal"), ExplorerItem("BudgetReport"))),
        ExplorerItem("Projects", true, listOf(ExplorerItem("Project Plan")))))
    override fun initializeComponent() { super.initializeComponent(); dataContext = this; InitializeSampleTreeView(sampleTreeView); InitializeSampleTreeView(sampleTreeView2) }
    private fun InitializeSampleTreeView(tree: TreeView) {
        fun node(title: String, children: List<TreeViewNode> = emptyList()) = TreeViewNode().apply { content = title; isExpanded = children.isNotEmpty(); children.forEach { this.children.add(it) } }
        tree.rootNodes.add(node("Work Documents", listOf(node("XYZ Functional Spec"), node("Feature Schedule"))))
        tree.rootNodes.add(node("Personal Documents", listOf(node("Home Remodel", listOf(node("Contractor Contact Info"), node("Paint Color Scheme"))))))
    }
}
