package io.github.composefluent.winrt.gallery.collections

import microsoft.ui.xaml.DataTemplate
import microsoft.ui.xaml.controls.DataTemplateSelector

internal class ExplorerItemTemplateSelector : DataTemplateSelector() {
    var FolderTemplate: DataTemplate? = null
    var FileTemplate: DataTemplate? = null
    override fun selectTemplateCore(item: Any?): DataTemplate = checkNotNull(
        if ((item as? ExplorerItem)?.IsFolder == true) FolderTemplate else FileTemplate)
}
