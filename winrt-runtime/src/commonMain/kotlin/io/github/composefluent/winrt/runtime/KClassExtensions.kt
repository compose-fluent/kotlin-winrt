package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
// Registry lookup — replaces the old expect fun platformRegisterWinRtType.
// Annotation scanning has been removed; all types must be registered explicitly
// via WinRtTypeRegistry.register() or the Projections helpers.
// ---------------------------------------------------------------------------

internal fun KClass<*>.registeredWinRtType(): WinRtTypeId<*>? = WinRtTypeRegistry.findByClass(this)

internal fun KClass<*>.typeDisplayName(): String = qualifiedName ?: simpleName ?: "<anonymous>"

internal expect fun platformTryInitializeGeneratedWinRtMetadata(type: KClass<*>)

// ---------------------------------------------------------------------------
// Enum support — driven by WinRtTypeId.enumAbiValue / enumEntries.
// No Java reflection; enums must supply entries at registration time.
// ---------------------------------------------------------------------------

internal fun isEnumType(type: KClass<*>): Boolean =
    type.registeredWinRtType()?.enumAbiValue != null

@Suppress("UNCHECKED_CAST")
internal fun enumConstants(type: KClass<*>): Array<Any>? =
    (type.registeredWinRtType()?.enumEntries as? Array<Any>)

// ---------------------------------------------------------------------------
// Exception / assignability checks — reflection-free.
// "is exception" is stored in WinRtTypeId.isExceptionType at registration.
// Array element type is handled by WinRtTypeClassifier (already in commonMain).
// ---------------------------------------------------------------------------

internal fun isExceptionType(type: KClass<*>): Boolean =
    type.registeredWinRtType()?.isExceptionType == true

/** Returns the array element KClass if [type] is a typed array registered in the registry, else null. */
internal fun arrayElementType(type: KClass<*>): KClass<*>? =
    WinRtTypeClassifier.primitiveArrayElementType(type)
        ?: TypeNameSupport.registeredArrayElementType(type)

/** Cross-platform assignability: target is a supertype of candidate if candidate is registered
 *  as an exception and target is the generic Exception marker, or falls back to identity. */
internal fun isAssignableFrom(targetType: KClass<*>, candidateType: KClass<*>): Boolean =
    when {
        targetType == candidateType -> true
        // Exception hierarchy: check via isExceptionType registration
        targetType == Exception::class -> candidateType.registeredWinRtType()?.isExceptionType == true
        else -> false
    }

// ---------------------------------------------------------------------------
// Primitive / name helpers — pure KClass, no java.* bridge.
// ---------------------------------------------------------------------------

internal fun isPrimitiveWinRtType(type: KClass<*>): Boolean =
    type.registeredWinRtType()?.let { it.isWindowsRuntimeType && !it.isRuntimeClass && it.guid == null } == true

internal fun typeName(type: KClass<*>): String =
    type.qualifiedName ?: type.simpleName ?: "<anonymous>"

internal fun boxedRuntimeClassName(type: KClass<*>): String? =
    WinRtValueBoxing.boxedRuntimeClassNameForType(type)

internal fun runtimeClassNameForNonWinRtType(type: KClass<*>): String? =
    ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type)

// ---------------------------------------------------------------------------
// Projection bootstrap — no longer platform-specific; both targets run the
// same WinRtBuiltInProjectionMappings code.
// ---------------------------------------------------------------------------

private var projectionMappingsRegistered = false
private var projectionMappingsRegistering = false

internal fun ensureProjectionMappingsRegistered() {
    if (projectionMappingsRegistered || projectionMappingsRegistering) return
    projectionMappingsRegistering = true
    try {
        WinRtBuiltInProjectionMappings.register()
        projectionMappingsRegistered = true
    } finally {
        projectionMappingsRegistering = false
    }
}

internal fun clearProjectionMappingsForTests() {
    projectionMappingsRegistered = false
    projectionMappingsRegistering = false
}
