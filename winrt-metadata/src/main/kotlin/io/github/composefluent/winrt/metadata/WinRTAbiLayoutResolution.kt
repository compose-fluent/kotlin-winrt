package io.github.composefluent.winrt.metadata

/** Resolves value-type ABI layout after every WinMD source has been merged. */
internal fun WinRTMetadataModel.withResolvedAbiLayouts(): WinRTMetadataModel {
    val typesByQualifiedName = namespaces
        .flatMap(WinRTNamespace::types)
        .associateBy(WinRTTypeDefinition::qualifiedName)
    val resolver = WinRTAbiLayoutResolver(typesByQualifiedName)
    return copy(
        namespaces = namespaces.map { namespace ->
            namespace.copy(
                types = namespace.types.map { type ->
                    val layout = resolver.resolve(type)
                    var instanceFieldIndex = 0
                    type.copy(
                        fields = type.fields.map { field ->
                            val fieldLayout = resolver.resolve(field.type, type.namespace)
                            val resolvedOffset = if (field.isStatic || field.isLiteral) {
                                field.offset
                            } else {
                                layout?.fieldOffsets?.getOrNull(instanceFieldIndex++ ) ?: field.offset
                            }
                            field.copy(
                                offset = resolvedOffset,
                                abiSize = fieldLayout?.size ?: field.abiSize,
                                abiAlignment = fieldLayout?.alignment ?: field.abiAlignment,
                                isBlittable = fieldLayout?.isBlittable ?: field.isBlittable,
                            )
                        },
                        abiSize = layout?.size ?: type.abiSize,
                        abiAlignment = layout?.alignment ?: type.abiAlignment,
                        isBlittable = layout?.isBlittable ?: type.isBlittable,
                    )
                },
            )
        },
    )
}

private class WinRTAbiLayoutResolver(
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
) {
    private val resolved = mutableMapOf<String, WinRTResolvedAbiLayout?>()
    private val resolving = mutableSetOf<String>()

    fun resolve(type: WinRTTypeDefinition): WinRTResolvedAbiLayout? =
        resolved.getOrPut(type.qualifiedName) {
            if (!resolving.add(type.qualifiedName)) return@getOrPut null
            try {
                when (type.kind) {
                    WinRTTypeKind.Enum -> type.enumUnderlyingType
                        ?.abiLayout()
                        ?: WinRTResolvedAbiLayout(size = 4, alignment = 4, isBlittable = true)
                    WinRTTypeKind.Struct -> resolveStruct(type)
                    else -> null
                }
            } finally {
                resolving.remove(type.qualifiedName)
            }
        }

    fun resolve(type: WinRTTypeRef, currentNamespace: String): WinRTResolvedAbiLayout? {
        val normalized = type.normalized()
        if (normalized.kind != WinRTTypeRefKind.Named) return null
        winRTFundamentalTypeForName(normalized.typeName)?.let { return it.abiLayout() }
        if (isWinRTGuidTypeName(normalized.typeName)) {
            return WinRTResolvedAbiLayout(size = 16, alignment = 4, isBlittable = true)
        }
        if (isWinRTObjectTypeName(normalized.typeName)) {
            return WinRTResolvedAbiLayout.pointer()
        }
        val qualifiedName = normalized.qualifiedName
            ?.let { name -> qualifyTypeName(name, currentNamespace, typesByQualifiedName) ?: name }
            ?: return null
        val definition = typesByQualifiedName[qualifiedName] ?: return null
        return when (definition.kind) {
            WinRTTypeKind.Enum,
            WinRTTypeKind.Struct -> resolve(definition)
            WinRTTypeKind.Interface,
            WinRTTypeKind.RuntimeClass,
            WinRTTypeKind.Delegate -> WinRTResolvedAbiLayout.pointer()
            WinRTTypeKind.Unknown -> null
        }
    }

    private fun resolveStruct(type: WinRTTypeDefinition): WinRTResolvedAbiLayout? {
        val fields = type.fields.filterNot { field -> field.isStatic || field.isLiteral }
        if (fields.isEmpty()) {
            return type.layout.classSize
                ?.takeIf { size -> size > 0 }
                ?.let { size ->
                    WinRTResolvedAbiLayout(
                        size,
                        alignment = 1,
                        isBlittable = true,
                        fieldOffsets = emptyList(),
                    )
                }
        }
        val layouts = fields.map { field -> resolve(field.type, type.namespace) ?: return null }
        val packing = type.layout.packingSize?.takeIf { value -> value > 0 } ?: Int.MAX_VALUE
        val fieldAlignments = layouts.map { layout -> minOf(layout.alignment, packing) }
        val alignment = fieldAlignments.maxOrNull() ?: 1
        val fieldOffsets = when (type.layout.kind) {
            WinRTTypeLayoutKind.Explicit -> fields.map { field -> field.offset ?: return null }
            WinRTTypeLayoutKind.Auto,
            WinRTTypeLayoutKind.Sequential -> {
                var offset = 0
                layouts.zip(fieldAlignments).map { (layout, fieldAlignment) ->
                    offset = alignAbiOffset(offset, fieldAlignment)
                    offset.also { offset += layout.size }
                }
            }
        }
        val fieldExtent = fieldOffsets.zip(layouts).maxOf { (offset, layout) -> offset + layout.size }
        val computedSize = when (type.layout.kind) {
            WinRTTypeLayoutKind.Explicit -> fieldExtent
            WinRTTypeLayoutKind.Auto,
            WinRTTypeLayoutKind.Sequential -> alignAbiOffset(fieldExtent, alignment)
        }
        val size = maxOf(type.layout.classSize ?: 0, computedSize).takeIf { value -> value > 0 } ?: return null
        return WinRTResolvedAbiLayout(
            size = size,
            alignment = alignment,
            isBlittable = layouts.all(WinRTResolvedAbiLayout::isBlittable),
            fieldOffsets = fieldOffsets,
        )
    }
}

private data class WinRTResolvedAbiLayout(
    val size: Int,
    val alignment: Int,
    val isBlittable: Boolean,
    val fieldOffsets: List<Int> = emptyList(),
) {
    companion object {
        fun pointer(): WinRTResolvedAbiLayout =
            WinRTResolvedAbiLayout(size = 8, alignment = 8, isBlittable = false)
    }
}

private fun WinRTFundamentalType.abiLayout(): WinRTResolvedAbiLayout =
    when (this) {
        WinRTFundamentalType.Boolean -> WinRTResolvedAbiLayout(1, 1, isBlittable = false)
        WinRTFundamentalType.Char -> WinRTResolvedAbiLayout(2, 2, isBlittable = false)
        WinRTFundamentalType.String -> WinRTResolvedAbiLayout.pointer()
        else -> WinRTResolvedAbiLayout(
            size = requireNotNull(blittableAbiSizeBytes),
            alignment = requireNotNull(blittableAbiAlignmentBytes),
            isBlittable = true,
        )
    }

private fun WinRTIntegralType.abiLayout(): WinRTResolvedAbiLayout =
    when (this) {
        WinRTIntegralType.Int8,
        WinRTIntegralType.UInt8 -> WinRTResolvedAbiLayout(1, 1, isBlittable = true)
        WinRTIntegralType.Int16,
        WinRTIntegralType.UInt16 -> WinRTResolvedAbiLayout(2, 2, isBlittable = true)
        WinRTIntegralType.Int32,
        WinRTIntegralType.UInt32 -> WinRTResolvedAbiLayout(4, 4, isBlittable = true)
        WinRTIntegralType.Int64,
        WinRTIntegralType.UInt64 -> WinRTResolvedAbiLayout(8, 8, isBlittable = true)
    }

private fun alignAbiOffset(value: Int, alignment: Int): Int {
    val remainder = value % alignment
    return if (remainder == 0) value else value + alignment - remainder
}
