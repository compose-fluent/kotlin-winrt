package io.github.composefluent.winrt.runtime

/** Keeps a COM owner strongly reachable through the last native-pointer use emitted by lowering. */
internal expect inline fun winRTKeepAlive(owner: Any?)
