package io.github.composefluent.winrt.projections.fixtures

import windows.foundation.Point
import windows.ui.xaml.DependencyObject

internal data class WindowsUiXamlProjectionConsumer(
    val dependencyObject: DependencyObject,
    val point: Point,
)
