package io.github.composefluent.winrt.runtime

internal actual inline fun winRTKeepAlive(owner: Any?) {
    java.lang.ref.Reference.reachabilityFence(owner)
}
