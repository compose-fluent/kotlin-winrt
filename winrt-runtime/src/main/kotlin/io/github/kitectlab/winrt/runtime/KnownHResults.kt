package io.github.kitectlab.winrt.runtime

object KnownHResults {
    val S_OK = HResult(0x00000000)
    val S_FALSE = HResult(0x00000001)
    val CO_E_NOTINITIALIZED = HResult(0x800401F0.toInt())
    val REGDB_E_CLASSNOTREG = HResult(0x80040154.toInt())
    val RPC_E_CHANGED_MODE = HResult(0x80010106.toInt())
}
