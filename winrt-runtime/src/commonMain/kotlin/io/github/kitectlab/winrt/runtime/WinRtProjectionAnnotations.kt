package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

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
