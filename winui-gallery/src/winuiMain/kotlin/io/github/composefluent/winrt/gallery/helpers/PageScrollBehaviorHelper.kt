// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.helpers

import microsoft.ui.xaml.DependencyObject
import microsoft.ui.xaml.DependencyProperty
import microsoft.ui.xaml.PropertyMetadata

/** Gallery's host reads this attached property when a sample manages its own scrolling. */
internal object PageScrollBehaviorHelper {
    private val SuppressHostScrollingProperty: DependencyProperty = DependencyProperty.registerAttached(
        "SuppressHostScrolling", Boolean::class, PageScrollBehaviorHelper::class, PropertyMetadata(false))
    fun GetSuppressHostScrolling(element: DependencyObject): Boolean = element.getValue(SuppressHostScrollingProperty) as Boolean
    fun SetSuppressHostScrolling(element: DependencyObject, value: Boolean) { element.setValue(SuppressHostScrollingProperty, value) }
}
