package io.github.composefluent.winrt.metadata

enum class WinRTTypeRefKind {
    Named,
    Array,
    GenericTypeParameter,
    MethodTypeParameter,
    Unknown,
}

data class WinRTTypeRef(
    val kind: WinRTTypeRefKind = WinRTTypeRefKind.Unknown,
    val qualifiedName: String? = null,
    val typeArguments: List<WinRTTypeRef> = emptyList(),
    val elementType: WinRTTypeRef? = null,
    val arrayRank: Int = 0,
    val genericParameterIndex: Int? = null,
    val isByRef: Boolean = false,
    val rawSignature: String? = null,
    val requiredModifiers: List<String> = emptyList(),
    val optionalModifiers: List<String> = emptyList(),
) {
    val typeName: String
        get() = when (kind) {
            WinRTTypeRefKind.Named -> {
                val name = qualifiedName?.trim().takeUnless(String?::isNullOrEmpty) ?: "Any"
                if (typeArguments.isEmpty()) {
                    name
                } else {
                    "$name<${typeArguments.joinToString(", ") { it.typeName }}>"
                }
            }

            WinRTTypeRefKind.Array -> "Array<${(elementType ?: unknown()).typeName}>"
            WinRTTypeRefKind.GenericTypeParameter -> "T${genericParameterIndex ?: 0}"
            WinRTTypeRefKind.MethodTypeParameter -> "M${genericParameterIndex ?: 0}"
            WinRTTypeRefKind.Unknown -> "Any"
        }

    fun normalized(): WinRTTypeRef =
        when (kind) {
            WinRTTypeRefKind.Named ->
                named(
                    qualifiedName = qualifiedName?.trim(),
                    typeArguments = typeArguments.map(WinRTTypeRef::normalized),
                    isByRef = isByRef,
                    rawSignature = rawSignature?.trim()?.takeIf(String::isNotEmpty),
                    requiredModifiers = requiredModifiers.normalizedModifiers(),
                    optionalModifiers = optionalModifiers.normalizedModifiers(),
                )

            WinRTTypeRefKind.Array ->
                array(
                    elementType = (elementType ?: unknown()).normalized(),
                    rank = arrayRank.coerceAtLeast(1),
                    isByRef = isByRef,
                    rawSignature = rawSignature?.trim()?.takeIf(String::isNotEmpty),
                    requiredModifiers = requiredModifiers.normalizedModifiers(),
                    optionalModifiers = optionalModifiers.normalizedModifiers(),
                )

            WinRTTypeRefKind.GenericTypeParameter ->
                genericTypeParameter(
                    index = (genericParameterIndex ?: 0).coerceAtLeast(0),
                    isByRef = isByRef,
                    rawSignature = rawSignature?.trim()?.takeIf(String::isNotEmpty),
                    requiredModifiers = requiredModifiers.normalizedModifiers(),
                    optionalModifiers = optionalModifiers.normalizedModifiers(),
                )

            WinRTTypeRefKind.MethodTypeParameter ->
                methodTypeParameter(
                    index = (genericParameterIndex ?: 0).coerceAtLeast(0),
                    isByRef = isByRef,
                    rawSignature = rawSignature?.trim()?.takeIf(String::isNotEmpty),
                    requiredModifiers = requiredModifiers.normalizedModifiers(),
                    optionalModifiers = optionalModifiers.normalizedModifiers(),
                )

            WinRTTypeRefKind.Unknown ->
                unknown(
                    isByRef = isByRef,
                    rawSignature = rawSignature?.trim()?.takeIf(String::isNotEmpty),
                    requiredModifiers = requiredModifiers.normalizedModifiers(),
                    optionalModifiers = optionalModifiers.normalizedModifiers(),
                )
        }

    fun withByRef(value: Boolean = true): WinRTTypeRef = copy(isByRef = value)

    fun withSignatureFidelity(
        rawSignature: String? = this.rawSignature,
        requiredModifiers: List<String> = this.requiredModifiers,
        optionalModifiers: List<String> = this.optionalModifiers,
    ): WinRTTypeRef =
        copy(
            rawSignature = rawSignature,
            requiredModifiers = requiredModifiers,
            optionalModifiers = optionalModifiers,
        ).normalized()

    fun substituteTypeParameters(
        genericTypeArguments: List<WinRTTypeRef>,
        methodTypeArguments: List<WinRTTypeRef> = emptyList(),
    ): WinRTTypeRef =
        when (kind) {
            WinRTTypeRefKind.Named ->
                named(
                    qualifiedName = qualifiedName,
                    typeArguments = typeArguments.map { argument ->
                        argument.substituteTypeParameters(genericTypeArguments, methodTypeArguments)
                    },
                    isByRef = isByRef,
                    rawSignature = rawSignature,
                    requiredModifiers = requiredModifiers,
                    optionalModifiers = optionalModifiers,
                )

            WinRTTypeRefKind.Array ->
                array(
                    elementType = (elementType ?: unknown()).substituteTypeParameters(genericTypeArguments, methodTypeArguments),
                    rank = arrayRank,
                    isByRef = isByRef,
                    rawSignature = rawSignature,
                    requiredModifiers = requiredModifiers,
                    optionalModifiers = optionalModifiers,
                )

            WinRTTypeRefKind.GenericTypeParameter ->
                genericTypeArguments.getOrNull(genericParameterIndex ?: 0)
                    ?.let { substituted -> if (isByRef) substituted.withByRef() else substituted }
                    ?: this

            WinRTTypeRefKind.MethodTypeParameter ->
                methodTypeArguments.getOrNull(genericParameterIndex ?: 0)
                    ?.let { substituted -> if (isByRef) substituted.withByRef() else substituted }
                    ?: this

            WinRTTypeRefKind.Unknown -> this
        }

    companion object {
        fun named(
            qualifiedName: String?,
            typeArguments: List<WinRTTypeRef> = emptyList(),
            isByRef: Boolean = false,
            rawSignature: String? = null,
            requiredModifiers: List<String> = emptyList(),
            optionalModifiers: List<String> = emptyList(),
        ): WinRTTypeRef {
            val normalizedName = qualifiedName?.trim().takeUnless(String?::isNullOrEmpty)
            if (normalizedName == null || normalizedName == "Any") {
                return unknown(
                    isByRef = isByRef,
                    rawSignature = rawSignature,
                    requiredModifiers = requiredModifiers,
                    optionalModifiers = optionalModifiers,
                )
            }
            return WinRTTypeRef(
                kind = WinRTTypeRefKind.Named,
                qualifiedName = normalizedName,
                typeArguments = typeArguments,
                isByRef = isByRef,
                rawSignature = rawSignature,
                requiredModifiers = requiredModifiers,
                optionalModifiers = optionalModifiers,
            )
        }

        fun array(
            elementType: WinRTTypeRef,
            rank: Int = 1,
            isByRef: Boolean = false,
            rawSignature: String? = null,
            requiredModifiers: List<String> = emptyList(),
            optionalModifiers: List<String> = emptyList(),
        ): WinRTTypeRef =
            WinRTTypeRef(
                kind = WinRTTypeRefKind.Array,
                elementType = elementType,
                arrayRank = rank.coerceAtLeast(1),
                isByRef = isByRef,
                rawSignature = rawSignature,
                requiredModifiers = requiredModifiers,
                optionalModifiers = optionalModifiers,
            )

        fun genericTypeParameter(
            index: Int,
            isByRef: Boolean = false,
            rawSignature: String? = null,
            requiredModifiers: List<String> = emptyList(),
            optionalModifiers: List<String> = emptyList(),
        ): WinRTTypeRef =
            WinRTTypeRef(
                kind = WinRTTypeRefKind.GenericTypeParameter,
                genericParameterIndex = index.coerceAtLeast(0),
                isByRef = isByRef,
                rawSignature = rawSignature,
                requiredModifiers = requiredModifiers,
                optionalModifiers = optionalModifiers,
            )

        fun methodTypeParameter(
            index: Int,
            isByRef: Boolean = false,
            rawSignature: String? = null,
            requiredModifiers: List<String> = emptyList(),
            optionalModifiers: List<String> = emptyList(),
        ): WinRTTypeRef =
            WinRTTypeRef(
                kind = WinRTTypeRefKind.MethodTypeParameter,
                genericParameterIndex = index.coerceAtLeast(0),
                isByRef = isByRef,
                rawSignature = rawSignature,
                requiredModifiers = requiredModifiers,
                optionalModifiers = optionalModifiers,
            )

        fun unknown(
            isByRef: Boolean = false,
            rawSignature: String? = null,
            requiredModifiers: List<String> = emptyList(),
            optionalModifiers: List<String> = emptyList(),
        ): WinRTTypeRef =
            WinRTTypeRef(
                kind = WinRTTypeRefKind.Unknown,
                isByRef = isByRef,
                rawSignature = rawSignature,
                requiredModifiers = requiredModifiers,
                optionalModifiers = optionalModifiers,
            )

        fun fromDisplayName(typeName: String?): WinRTTypeRef {
            val trimmed = typeName?.trim()
                ?.removeSuffix("?")
                ?.trim()
                .takeUnless(String?::isNullOrEmpty) ?: return unknown()
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
                    qualifiedName = trimmed.substring(0, genericStart).trim().withoutGenericAritySuffix(),
                    typeArguments =
                        splitGenericTypeArguments(trimmed.substring(genericStart + 1, trimmed.length - 1))
                            .map(Companion::fromDisplayName),
                )
            }
            return named(trimmed)
        }
    }
}

private fun String.withoutGenericAritySuffix(): String {
    val backtick = lastIndexOf('`')
    if (backtick <= 0 || backtick == lastIndex) {
        return this
    }
    return if (substring(backtick + 1).all(Char::isDigit)) {
        substring(0, backtick)
    } else {
        this
    }
}

private fun List<String>.normalizedModifiers(): List<String> =
    map(String::trim).filter(String::isNotEmpty).distinct().sorted()

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
