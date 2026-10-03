package io.github.composefluent.winrt.gallery.text

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "NumberBox", title = "NumberBox", group = "Text", order = 1)
internal class NumberBoxPage : Page() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent()
        val rounder = windows.globalization.numberformatting.IncrementNumberRounder().apply {
            increment = 0.25; roundingAlgorithm = windows.globalization.numberformatting.RoundingAlgorithm.RoundHalfUp
        }
        FormattedNumberBox.numberFormatter = windows.globalization.numberformatting.DecimalFormatter().apply {
            integerDigits = 1; fractionDigits = 2; numberRounder = rounder
        }
        ready = true; applyPlacement()
    }
    private fun applyPlacement() { NumberBoxSpinButtonPlacementExample.spinButtonPlacementMode =
        if (SpinButtonPlacementGroup.selectedIndex == 0) NumberBoxSpinButtonPlacementMode.Inline else NumberBoxSpinButtonPlacementMode.Compact }
    private fun SpinButtonPlacementGroup_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) applyPlacement() }
}
