package io.github.kitectlab.winrt.runtime

/**
 * Attribute annotation messages corresponding to `.cswinrt/src/WinRT.Runtime/AttributeMessages.net5.cs`.
 */
internal object AttributeMessages {
    const val GenericDeprecatedMessage: String =
        "This method is deprecated and will be removed in a future release."
    const val GenericRequiresUnreferencedCodeMessage: String =
        "This method is not trim-safe, and is only supported for use when not using trimming (or AOT)."
    const val MarshallingOrGenericInstantiationsRequiresDynamicCode: String =
        "The necessary marshalling code or generic instantiations might not be available."
    const val NotSupportedIfDynamicCodeIsNotAvailable: String =
        "The annotated API is not supported when dynamic code is not available (ie. in AOT environments)."
    const val AbiTypesNeverHaveConstructors: String =
        "All ABI types never have a constructor that would need to be accessed via reflection."
}
