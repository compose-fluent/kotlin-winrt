package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinRTXamlHotReloadProtocol {
    const val VERSION = 1
    const val MAGIC = 0x4B585248
    const val SESSION_DIRECTORY = "KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"
    const val APPLIED = 0
    const val REJECTED = 1
    const val UNAVAILABLE = 2
    const val RESTART_REQUIRED = 3
}

data class WinRTXamlHotReloadChange(val element: String, val property: String, val literal: String)
data class WinRTXamlHotReloadPatch(val className: String, val resourcePath: String, val expectedHash: String,
    val sourceHash: String, val version: Long, val changes: List<WinRTXamlHotReloadChange>)
data class WinRTXamlHotReloadRoot(val className: String, val resourcePath: String, val sourceHash: String,
    val version: Long, val elements: List<String>)
data class WinRTXamlHotReloadValue(val element: String, val property: String, val value: String)
data class WinRTXamlHotReloadReply(val status: Int, val message: String, val roots: List<WinRTXamlHotReloadRoot> = emptyList(),
    val values: List<WinRTXamlHotReloadValue> = emptyList())
