package io.github.composefluent.winrt.metadata

/**
 * CsWinRT MarshalInterface<T>/MarshalInspectable<T>/delegate FromAbi preserve null
 * COM pointers. Kotlin must express that contract explicitly, unlike C# reference
 * declarations without nullable annotations. This is a projection query: it never
 * modifies WinMD signatures, generic identity, or the metadata model.
 *
 * HSTRING is deliberately excluded (null HSTRING projects to the empty string).
 * Value-type custom mappings such as IReference<T> remain owned by their mapper.
 * An open T keeps the nullability of its eventual type argument.
 */
fun WinRTTypeRef.isNullableReferenceProjection(
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): Boolean {
    if (kind != WinRTTypeRefKind.Named) return false
    val name = qualifiedName ?: return false
    winRTMappedTypeForAbiName(name)?.preservesNullReference?.let { return it }
    if (isWinRTObjectTypeName(name)) return true
    val special = WinRTMetadataSpecialTypeResolver.create(typesByQualifiedName).resolveType(this, currentNamespace)
    if (special != null) return special.family != WinRTSpecialTypeFamily.Reference
    return when (resolveTypeReference(this, currentNamespace, typesByQualifiedName).definitionType?.kind) {
        WinRTTypeKind.Interface, WinRTTypeKind.RuntimeClass, WinRTTypeKind.Delegate -> true
        else -> false
    }
}
