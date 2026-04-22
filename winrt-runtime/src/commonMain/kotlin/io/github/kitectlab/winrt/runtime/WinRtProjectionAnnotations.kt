package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtGuid(
    val value: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ProjectedRuntimeClass(
    val defaultInterfaceProperty: String = "",
    val defaultInterface: KClass<*> = Any::class,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ObjectReferenceWrapper(
    val objectReferenceField: String,
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
annotation class WinRtAssemblyExportsType(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtDefaultInterface(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRtDelegateType

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedBindableCustomProperty(
    val propertyNames: Array<String> = [],
    val indexerPropertyTypes: Array<KClass<*>> = [],
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedWinRtExposedType

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedWinRtExposedExternalType(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class DynamicWindowsRuntimeCast(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WuxMuxProjectedType
