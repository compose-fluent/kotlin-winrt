package io.github.composefluent.winrt.gallery.fundamentals

import microsoft.ui.xaml.DependencyObject
import microsoft.ui.xaml.markup.IXamlCondition

internal class FeatureFlagCondition : DependencyObject(), IXamlCondition {
    override fun evaluate(argument: String): Boolean = FeatureFlags[argument] == true
    companion object { val FeatureFlags: MutableMap<String, Boolean> = mutableMapOf("NewExperience" to true, "LegacyMode" to false) }
}
