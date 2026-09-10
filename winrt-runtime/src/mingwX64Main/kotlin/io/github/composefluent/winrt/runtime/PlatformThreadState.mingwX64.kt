package io.github.composefluent.winrt.runtime

import platform.windows.GetCurrentThreadId

internal actual fun platformCurrentThreadToken(): Long =
    GetCurrentThreadId().toLong()

internal actual fun platformCurrentThreadIsVirtual(): Boolean = false
