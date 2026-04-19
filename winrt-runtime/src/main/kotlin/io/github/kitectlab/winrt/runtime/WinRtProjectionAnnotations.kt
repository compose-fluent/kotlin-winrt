package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

/**
 * JVM-side metadata hooks corresponding to the reflection-based type/projection discovery
 * that `.cswinrt/src/WinRT.Runtime` performs via .NET attributes and `Type.GUID`.
 *
 * The JVM path cannot rely on `Type.GUID` or assembly-name identity in the same way, so these
 * annotations are the narrow runtime contract used by Runtime 1.15 to keep the same responsibility
 * split without pushing projection-registry policy into generator code.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtGuid(
    val value: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WindowsRuntimeType(
    val guidSignature: String = "",
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WindowsRuntimeHelperType(
    val helperType: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtRuntimeClassName(
    val runtimeClassName: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtDefaultInterface(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtDelegateType
