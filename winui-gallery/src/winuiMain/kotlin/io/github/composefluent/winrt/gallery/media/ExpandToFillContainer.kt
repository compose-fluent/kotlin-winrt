package io.github.composefluent.winrt.gallery.media

import microsoft.ui.xaml.controls.Grid
import windows.foundation.Size

// Original WinUI Gallery capture sample: let snapshots fill the camera's height.
internal class ExpandToFillContainer : Grid() {
    override fun measureOverride(availableSize: Size): Size = super.measureOverride(Size(availableSize.width, 100f))
}
