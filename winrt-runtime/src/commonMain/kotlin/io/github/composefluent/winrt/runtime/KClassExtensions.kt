package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
// Registry lookup — replaces the old expect fun platformRegisterWinRTType.
// Annotation scanning has been removed; all types must be registered explicitly
// via WinRTTypeRegistry.register() or the Projections helpers.
// ---------------------------------------------------------------------------

internal fun KClass<*>.registeredWinRTType(): WinRTTypeId<*>? = WinRTTypeRegistry.findByClass(this)

internal fun KClass<*>.typeDisplayName(): String = qualifiedName ?: simpleName ?: "<anonymous>"

// ---------------------------------------------------------------------------
// Enum support — driven by WinRTTypeId.enumAbiValue / enumEntries.
// No Java reflection; enums must supply entries at registration time.
// ---------------------------------------------------------------------------

internal fun isEnumType(type: KClass<*>): Boolean =
    type.registeredWinRTType()?.enumAbiValue != null

@Suppress("UNCHECKED_CAST")
internal fun enumConstants(type: KClass<*>): Array<Any>? =
    (type.registeredWinRTType()?.enumEntries as? Array<Any>)

// ---------------------------------------------------------------------------
// Exception / assignability checks — reflection-free.
// "is exception" is stored in WinRTTypeId.isExceptionType at registration.
// Array element type is handled by WinRTTypeClassifier (already in commonMain).
// ---------------------------------------------------------------------------

internal fun isExceptionType(type: KClass<*>): Boolean =
    type.registeredWinRTType()?.isExceptionType == true

/** Returns the element type for primitive arrays. Reference arrays require explicit metadata. */
internal fun arrayElementType(type: KClass<*>): KClass<*>? =
    WinRTTypeClassifier.arrayElementType(type)

/** Builder capacity for the existing intrinsic class-key descriptor indexes. */
internal expect fun intrinsicClassKeyMapInitialCapacity(keyCount: Int): Int

/** Array identity only; callers retain explicit array-element metadata precedence. */
internal expect fun isErasedReferenceArrayType(
    type: KClass<*>,
    erasedArrayType: KClass<*>,
): Boolean

/** Cross-platform assignability uses registered exception and projection inheritance metadata. */
internal fun isAssignableFrom(targetType: KClass<*>, candidateType: KClass<*>): Boolean =
    when {
        targetType == candidateType -> true
        // Exception hierarchy: check via isExceptionType registration
        targetType == Exception::class -> candidateType.registeredWinRTType()?.isExceptionType == true
        else -> TypeNameSupport.isProjectionAssignableFrom(targetType, candidateType)
    }

// ---------------------------------------------------------------------------
// Primitive / name helpers — pure KClass, no java.* bridge.
// ---------------------------------------------------------------------------

internal fun isPrimitiveWinRTType(type: KClass<*>): Boolean =
    WinRTTypeClassifier.classify(type)?.isTypeNamePrimitive == true

internal fun typeName(type: KClass<*>): String =
    type.qualifiedName ?: type.simpleName ?: "<anonymous>"

internal fun boxedRuntimeClassName(type: KClass<*>): String? =
    WinRTValueBoxing.boxedRuntimeClassNameForType(type)

internal fun runtimeClassNameForNonWinRTType(type: KClass<*>): String? =
    ComWrappersSupport.getRuntimeClassNameForNonWinRTTypeFromLookupTable(type)

// ---------------------------------------------------------------------------
// Projection bootstrap — no longer platform-specific; both targets run the
// same WinRTBuiltInProjectionMappings code.
// ---------------------------------------------------------------------------

private var projectionMappingsRegistered = false
private var projectionMappingsRegistering = false

internal fun ensureProjectionMappingsRegistered() {
    if (projectionMappingsRegistered || projectionMappingsRegistering) return
    projectionMappingsRegistering = true
    try {
        WinRTBuiltInProjectionMappings.register()
        projectionMappingsRegistered = true
    } finally {
        projectionMappingsRegistering = false
    }
}

internal fun clearProjectionMappingsForTests() {
    projectionMappingsRegistered = false
    projectionMappingsRegistering = false
}
