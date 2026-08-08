package io.github.composefluent.winrt.runtime

/** HRESULT handling is a WinMD method fact; the lowering selects the platform implementation. */
enum class WinRTCallSiteHResultPolicy {
    CHECK,
    IGNORE,
    RETURN,
}

/** One projected parameter's WinMD passing semantics. */
enum class WinRTCallSiteParameterDirection {
    IN,
    REF,
    OUT,
    PASS_ARRAY,
    FILL_ARRAY,
}

/** Result shapes that cannot be inferred from the typed Kotlin return alone. */
enum class WinRTCallSiteResultKind {
    INFER,
    CALLER_OWNED,
}

/**
 * Marks a typed projection call-site stub. The annotation contains only WinMD facts that are not
 * represented by the Kotlin declaration; the compiler plugin owns ABI planning and code emission.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTProjectionCallSite(
    val hResult: WinRTCallSiteHResultPolicy = WinRTCallSiteHResultPolicy.CHECK,
    val result: WinRTCallSiteResultKind = WinRTCallSiteResultKind.INFER,
    val returnAbiType: String = "",
)

/** Adds WinMD direction or an otherwise ambiguous WinMD ABI type to one typed parameter. */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTProjectionParameter(
    val direction: WinRTCallSiteParameterDirection = WinRTCallSiteParameterDirection.IN,
    val abiType: String = "",
)

/** ABI facts attached once to a generated type or closed type helper, never to each call site. */
enum class WinRTProjectionAbiTypeKind {
    ENUM,
    STRUCT,
    COM_REFERENCE,
    PROJECTION,
    ARRAY,
}

enum class WinRTProjectionAbiCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
}

enum class WinRTProjectionAbiValueTransform {
    IDENTITY,
    BOOLEAN,
    UNSIGNED,
    CHAR16,
}

enum class WinRTProjectionAbiReferenceKind {
    NONE,
    UNKNOWN,
    INSPECTABLE,
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTProjectionAbiType(
    val name: String = "",
    val kind: WinRTProjectionAbiTypeKind,
    val carrier: WinRTProjectionAbiCarrier = WinRTProjectionAbiCarrier.ADDRESS,
    val transform: WinRTProjectionAbiValueTransform = WinRTProjectionAbiValueTransform.IDENTITY,
    val size: Int = 0,
    val alignment: Int = 0,
    val reference: WinRTProjectionAbiReferenceKind = WinRTProjectionAbiReferenceKind.NONE,
)

enum class WinRTProjectionAbiCodecRole {
    TO_ABI,
    FROM_ABI,
    CREATE_MARSHALER,
    COPY_TO_ABI,
    COPY_FROM_ABI,
    DISPOSE_ABI,
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTProjectionAbiCodec(
    val role: WinRTProjectionAbiCodecRole,
    val type: String = "",
)

/** Exact WinMD enum-member bits retained on the compatibility getter for use-site folding. */
@Target(AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTEnumConstant(
    val valueBits: Long,
)

/** The primary constructor parameters are ordered caller-owned ABI outputs. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class WinRTCallerOwnedResult
