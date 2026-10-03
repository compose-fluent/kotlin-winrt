// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.system

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.gallery.controls.ColorSelector
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.windowing.*
import windows.ui.Color

@GalleryPage(route = "ContentIsland", title = "ContentIsland", group = "System", order = 1)
internal class ContentIslandPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private val entries = mutableListOf<IslandEntry>()
    private var nextHostElementIndex = 0
    private class IslandEntry(val link: microsoft.ui.content.ChildSiteLink, val rect: microsoft.ui.xaml.shapes.Rectangle,
        val placement: microsoft.ui.composition.ContainerVisual, val handler: windows.foundation.EventHandler<Any?>) {
        var island: microsoft.ui.content.ContentIsland? = null
        var model: microsoft.ui.composition.ContainerVisual? = null
    }
    override fun initializeComponent() { super.initializeComponent(); unloaded.add(::OnUnloaded) }
    private fun LoadModel_Click(sender: Any?, args: RoutedEventArgs) { _rectanglePanel.visibility = Visibility.Visible; tasks.launch { LoadModel() } }
    suspend fun LoadModel() {
        val rect = _rectanglePanel.children.getOrNull(nextHostElementIndex++)?.asWinRT<microsoft.ui.xaml.shapes.Rectangle>() ?: return
        val root = checkNotNull(xamlRoot)
        val compositor = checkNotNull(microsoft.ui.xaml.hosting.ElementCompositionPreview.getElementVisual(rect).compositor)
        val placement = compositor.createContainerVisual()
        microsoft.ui.xaml.hosting.ElementCompositionPreview.setElementChildVisual(rect,placement)
        val link = microsoft.ui.content.ChildSiteLink.create(checkNotNull(root.contentIsland),placement)
        val handler = windows.foundation.EventHandler<Any?> { _, _ ->
            val point = rect.transformToVisual(null).transformPoint(windows.foundation.Point(0f,0f))
            link.localToParentTransformMatrix = windows.foundation.numerics.Matrix4x4(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,point.x,point.y,0f,1f)
            placement.size = rect.actualSize; link.actualSize = rect.actualSize
        }
        val entry = IslandEntry(link,rect,placement,handler); entries.add(entry)
        rect.layoutUpdated.add(handler); handler(null,null)
        try {
            val model = proceduralHelmet(compositor); entry.model = model
            val island = microsoft.ui.content.ContentIsland.create(model)
            if (!link.isClosed) { entry.island = island; link.connect(island) } else { island.close(); model.close() }
        } catch (failure: Throwable) { DisposeEntry(entry); entries.remove(entry); throw failure }
    }
    private fun DisposeEntry(entry: IslandEntry) {
        entry.rect.layoutUpdated.remove(entry.handler)
        microsoft.ui.xaml.hosting.ElementCompositionPreview.setElementChildVisual(entry.rect,null)
        try { entry.link.close() } finally { try { entry.island?.close() } finally { entry.model?.close(); entry.placement.close() } }
    }
    private fun OnUnloaded(sender: Any?, args: RoutedEventArgs) { entries.toList().forEach(::DisposeEntry); entries.clear(); nextHostElementIndex = 0 }

}
