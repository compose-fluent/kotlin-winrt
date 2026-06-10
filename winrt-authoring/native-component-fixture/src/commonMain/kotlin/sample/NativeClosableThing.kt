package sample

import io.github.composefluent.winrt.runtime.WinRtAuthoredRuntimeClass

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IClosable"])
class NativeClosableThing {
    fun close() = Unit
}

@WinRtAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
class NativeStringableThing {
    override fun toString(): String = "NativeStringableThing"
}
