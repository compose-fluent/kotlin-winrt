package io.github.kitectlab.winrt.metadata

enum class WinRtTypeRefKind {
    Named,
    Array,
    GenericTypeParameter,
    MethodTypeParameter,
    Unknown,
}

data class WinRtTypeRef(
    val kind: WinRtTypeRefKind = WinRtTypeRefKind.Unknown,
    val qualifiedName: String? = null,
    val typeArguments: List<WinRtTypeRef> = emptyList(),
    val elementType: WinRtTypeRef? = null,
    val arrayRank: Int = 0,
    val genericParameterIndex: Int? = null,
    val isByRef: Boolean = false,
) {
    val typeName: String
        get() = when (kind) {
            WinRtTypeRefKind.Named -> {
                val name = qualifiedName?.trim().takeUnless(String?::isNullOrEmpty) ?: "Any"
                if (typeArguments.isEmpty()) {
                    name
                } else {
                    "$name<${typeArguments.joinToString(", ") { it.typeName }}>"
                }
            }

            WinRtTypeRefKind.Array -> "Array<${(elementType ?: unknown()).typeName}>"
            WinRtTypeRefKind.GenericTypeParameter -> "T${genericParameterIndex ?: 0}"
            WinRtTypeRefKind.MethodTypeParameter -> "M${genericParameterIndex ?: 0}"
            WinRtTypeRefKind.Unknown -> "Any"
        }

    fun normalized(): WinRtTypeRef =
        when (kind) {
            WinRtTypeRefKind.Named ->
                named(
                    qualifiedName = qualifiedName?.trim(),
                    typeArguments = typeArguments.map(WinRtTypeRef::normalized),
                    isByRef = isByRef,
                )

            WinRtTypeRefKind.Array ->
                array(
                    elementType = (elementType ?: unknown()).normalized(),
                    rank = arrayRank.coerceAtLeast(1),
                    isByRef = isByRef,
                )

            WinRtTypeRefKind.GenericTypeParameter ->
                genericTypeParameter(
                    index = (genericParameterIndex ?: 0).coerceAtLeast(0),
                    isByRef = isByRef,
                )

            WinRtTypeRefKind.MethodTypeParameter ->
                methodTypeParameter(
                    index = (genericParameterIndex ?: 0).coerceAtLeast(0),
                    isByRef = isByRef,
                )

            WinRtTypeRefKind.Unknown -> unknown(isByRef = isByRef)
        }

    fun withByRef(value: Boolean = true): WinRtTypeRef = copy(isByRef = value)

    companion object {
        fun named(
            qualifiedName: String?,
            typeArguments: List<WinRtTypeRef> = emptyList(),
            isByRef: Boolean = false,
        ): WinRtTypeRef {
            val normalizedName = qualifiedName?.trim().takeUnless(String?::isNullOrEmpty)
            if (normalizedName == null || normalizedName == "Any") {
                return unknown(isByRef = isByRef)
            }
            return WinRtTypeRef(
                kind = WinRtTypeRefKind.Named,
                qualifiedName = normalizedName,
                typeArguments = typeArguments,
                isByRef = isByRef,
            )
        }

        fun array(
            elementType: WinRtTypeRef,
            rank: Int = 1,
            isByRef: Boolean = false,
        ): WinRtTypeRef =
            WinRtTypeRef(
                kind = WinRtTypeRefKind.Array,
                elementType = elementType,
                arrayRank = rank.coerceAtLeast(1),
                isByRef = isByRef,
            )

        fun genericTypeParameter(
            index: Int,
            isByRef: Boolean = false,
        ): WinRtTypeRef =
            WinRtTypeRef(
                kind = WinRtTypeRefKind.GenericTypeParameter,
                genericParameterIndex = index.coerceAtLeast(0),
                isByRef = isByRef,
            )

        fun methodTypeParameter(
            index: Int,
            isByRef: Boolean = false,
        ): WinRtTypeRef =
            WinRtTypeRef(
                kind = WinRtTypeRefKind.MethodTypeParameter,
                genericParameterIndex = index.coerceAtLeast(0),
                isByRef = isByRef,
            )

        fun unknown(isByRef: Boolean = false): WinRtTypeRef =
            WinRtTypeRef(
                kind = WinRtTypeRefKind.Unknown,
                isByRef = isByRef,
            )

        fun fromDisplayName(typeName: String?): WinRtTypeRef {
            val trimmed = typeName?.trim().takeUnless(String?::isNullOrEmpty) ?: return unknown()
            val genericParameterIndex = trimmed.removePrefix("T").toIntOrNull()
            if (trimmed.startsWith("T") && genericParameterIndex != null) {
                return genericTypeParameter(genericParameterIndex)
            }
            val methodTypeParameterIndex = trimmed.removePrefix("M").toIntOrNull()
            if (trimmed.startsWith("M") && methodTypeParameterIndex != null) {
                return methodTypeParameter(methodTypeParameterIndex)
            }
            if (trimmed.startsWith("Array<") && trimmed.endsWith(">")) {
                return array(fromDisplayName(trimmed.substring(6, trimmed.length - 1)))
            }
            val genericStart = trimmed.indexOf('<')
            if (genericStart > 0 && trimmed.endsWith(">")) {
                return named(
                    qualifiedName = trimmed.substring(0, genericStart).trim(),
                    typeArguments =
                        splitGenericTypeArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
                            .map(Companion::fromDisplayName),
                )
            }
            return named(trimmed)
        }
    }
}

private fun splitGenericTypeArguments(arguments: String): List<String> {
    if (arguments.isBlank()) {
        return emptyList()
    }
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    arguments.forEachIndexed { index, character ->
        when (character) {
            '<' -> depth += 1
            '>' -> depth -= 1
            ',' ->
                if (depth == 0) {
                    result += arguments.substring(start, index).trim()
                    start = index + 1
                }
        }
    }
    result += arguments.substring(start).trim()
    return result.filter(String::isNotEmpty)
}
