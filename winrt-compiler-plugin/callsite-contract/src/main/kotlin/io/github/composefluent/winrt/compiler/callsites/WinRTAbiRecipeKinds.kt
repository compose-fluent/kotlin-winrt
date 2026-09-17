package io.github.composefluent.winrt.compiler.callsites

/** Shared vocabulary for generator planning and compiler-owned fallback recipes. */
enum class WinRTProjectionCallSiteAbiCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
}

enum class WinRTProjectionCallSiteRecipeKind {
    VALUE,
    HSTRING,
    GUID,
    ENUM,
    STRUCT,
    COM_REFERENCE,
    ARRAY,
    PROJECTION,
}

enum class WinRTProjectionCallSiteValueTransform {
    IDENTITY,
    BOOLEAN,
    UNSIGNED,
    CHAR16,
}

enum class WinRTProjectionCallSiteSlotDirection {
    IN,
    REF,
    OUT,
    CALLER_OUT,
    PASS_ARRAY,
    FILL_ARRAY,
    RECEIVE_ARRAY,
    RETURN,
}

enum class WinRTProjectionCallSiteOwnership {
    NONE,
    BORROWED,
    OWNED,
}
