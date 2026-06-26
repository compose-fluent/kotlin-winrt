package io.github.composefluent.winrt.samples

internal actual fun winRTSampleOption(name: String): Boolean =
    java.lang.Boolean.getBoolean(name)

internal actual fun winRTSampleOptionConfigured(name: String): Boolean =
    System.getProperty(name) != null
