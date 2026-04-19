package io.github.kitectlab.winrt.metadata

import io.github.kitectlab.winrt.runtime.Guid

data class WinRtTypeDefinition(
    val namespace: String,
    val name: String,
    val iid: Guid? = null,
)

data class WinRtNamespace(
    val name: String,
    val types: List<WinRtTypeDefinition>,
)
