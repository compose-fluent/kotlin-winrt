package io.github.composefluent.winrt.runtime

expect object WinRTNativeExportInvoker {
    fun invokeHResultAddressAddress(
        function: RawAddress,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int

    fun invokeHResultStruct8Address(
        function: RawAddress,
        structValue: RawAddress,
        arg1: RawAddress,
    ): Int
}
