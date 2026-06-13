package io.github.composefluent.winrt.samples

internal actual fun winRtSampleOption(name: String): Boolean =
    java.lang.Boolean.getBoolean(name)
