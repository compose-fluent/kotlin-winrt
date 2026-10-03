package io.github.composefluent.winrt.runtime

// Kotlin/JVM 2.4 MapBuilder() delegates to MapBuilder(8); preserve that default.
@Suppress("UNUSED_PARAMETER")
internal actual fun intrinsicClassKeyMapInitialCapacity(keyCount: Int): Int = 8
