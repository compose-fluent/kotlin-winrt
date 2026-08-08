package io.github.composefluent.winrt.compiler.callsites

const val WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTProjectionCallSite"

const val WINRT_PROJECTION_PARAMETER_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTProjectionParameter"

const val WINRT_PROJECTION_ABI_TYPE_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTProjectionAbiType"

const val WINRT_PROJECTION_ABI_CODEC_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTProjectionAbiCodec"

const val WINRT_CALLER_OWNED_RESULT_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTCallerOwnedResult"

const val WINRT_ENUM_CONSTANT_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTEnumConstant"

enum class WinRTProjectionCallSiteHResultPolicy {
    CHECK,
    IGNORE,
    RETURN,
}

enum class WinRTProjectionCallSiteParameterDirection {
    IN,
    REF,
    OUT,
    PASS_ARRAY,
    FILL_ARRAY,
}

enum class WinRTProjectionCallSiteResultKind {
    INFER,
    CALLER_OWNED,
}

data class WinRTProjectionParameterMetadata(
    val direction: WinRTProjectionCallSiteParameterDirection = WinRTProjectionCallSiteParameterDirection.IN,
    val abiType: String = "",
) {
    init {
        require(abiType.none(Char::isWhitespace)) {
            "A WinRT parameter ABI type name cannot contain whitespace."
        }
    }
}

/**
 * WinMD facts that cannot be recovered from a typed Kotlin call-site declaration.
 *
 * This is deliberately not an ABI recipe. Carrier selection, marshaling, cleanup, result
 * construction, and backend transport are compiler-plugin responsibilities.
 */
data class WinRTProjectionCallSiteMetadata(
    val hResultPolicy: WinRTProjectionCallSiteHResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
    val resultKind: WinRTProjectionCallSiteResultKind = WinRTProjectionCallSiteResultKind.INFER,
    val returnAbiType: String = "",
    val parameters: List<WinRTProjectionParameterMetadata> = emptyList(),
) {
    init {
        require(returnAbiType.none(Char::isWhitespace)) {
            "A WinRT return ABI type name cannot contain whitespace."
        }
        require(resultKind != WinRTProjectionCallSiteResultKind.CALLER_OWNED || returnAbiType.isEmpty()) {
            "A caller-owned result is described by its typed result constructor, not a return ABI type name."
        }
    }
}

/** Runtime-owned matching uses readable annotation semantics plus the exact typed JVM signature. */
data class WinRTProjectionCallSiteCatalogKey(
    val metadata: WinRTProjectionCallSiteMetadata,
    val jvmMethodDescriptor: String,
) {
    init {
        require(jvmMethodDescriptor.startsWith('(') && ')' in jvmMethodDescriptor) {
            "A WinRT runtime-owned call-site key requires a JVM method descriptor."
        }
    }
}
