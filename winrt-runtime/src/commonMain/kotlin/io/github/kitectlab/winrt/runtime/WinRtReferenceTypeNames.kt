package io.github.kitectlab.winrt.runtime

/**
 * Shared `IReference<T>` / `IReferenceArray<T>` runtime-name owner corresponding to the
 * boxed-name handling split across `.cswinrt/src/WinRT.Runtime/TypeNameSupport.cs`
 * and `.cswinrt/src/WinRT.Runtime/Projections/Nullable.cs`.
 */
internal object WinRtReferenceTypeNames {
    private const val REFERENCE_RUNTIME_NAME_PREFIX = "Windows.Foundation.IReference`1<"
    private const val REFERENCE_ARRAY_RUNTIME_NAME_PREFIX = "Windows.Foundation.IReferenceArray`1<"

    fun boxedReference(projectedTypeName: String): String =
        "$REFERENCE_RUNTIME_NAME_PREFIX$projectedTypeName>"

    fun boxedReferenceArray(projectedTypeName: String): String =
        "$REFERENCE_ARRAY_RUNTIME_NAME_PREFIX$projectedTypeName>"

    fun parseReferenceElement(runtimeClassName: String): String? =
        parseSingleGenericArgument(runtimeClassName, REFERENCE_RUNTIME_NAME_PREFIX)

    fun parseReferenceArrayElement(runtimeClassName: String): String? =
        parseSingleGenericArgument(runtimeClassName, REFERENCE_ARRAY_RUNTIME_NAME_PREFIX)

    private fun parseSingleGenericArgument(
        runtimeClassName: String,
        prefix: String,
    ): String? =
        if (runtimeClassName.startsWith(prefix) && runtimeClassName.endsWith(">")) {
            runtimeClassName.substring(prefix.length, runtimeClassName.length - 1)
        } else {
            null
        }
}
