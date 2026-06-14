package io.github.composefluent.winrt.runtime

internal actual fun platformReadFeatureSwitch(propertyName: String): Boolean? =
    parseFeatureSwitchValue(System.getProperty(propertyName))
