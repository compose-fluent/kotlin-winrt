package io.github.kitectlab.winrt.metadata

import io.github.kitectlab.winrt.runtime.Guid

enum class WinRtTypeKind {
    Unknown,
    Interface,
    RuntimeClass,
    Enum,
    Struct,
    Delegate,
}

data class WinRtParameterDefinition(
    val name: String,
    val typeName: String,
)

data class WinRtMethodDefinition(
    val name: String,
    val returnTypeName: String,
    val parameters: List<WinRtParameterDefinition> = emptyList(),
    val isStatic: Boolean = false,
)

data class WinRtTypeDefinition(
    val namespace: String,
    val name: String,
    val kind: WinRtTypeKind = WinRtTypeKind.Unknown,
    val iid: Guid? = null,
    val defaultInterfaceName: String? = null,
    val methods: List<WinRtMethodDefinition> = emptyList(),
)

data class WinRtNamespace(
    val name: String,
    val types: List<WinRtTypeDefinition>,
) {
    fun normalized(): WinRtNamespace =
        copy(types = types.sortedBy { it.name })
}

data class WinRtMetadataModel(
    val namespaces: List<WinRtNamespace>,
) {
    fun normalized(): WinRtMetadataModel =
        copy(namespaces = namespaces.sortedBy { it.name }.map { it.normalized() })
}
