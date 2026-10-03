package io.github.composefluent.winrt.runtime

// Reserve Native class-key collision headroom from the existing descriptor count.
internal actual fun intrinsicClassKeyMapInitialCapacity(keyCount: Int): Int = keyCount * 2
