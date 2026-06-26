package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRTGuid(
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
annotation class WinRTRuntimeClassName(
    val runtimeClassName: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRTAssemblyExportsType(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRTDefaultInterface(
    val type: KClass<*>,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRTDelegateType

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedBindableCustomProperty(
    val propertyNames: Array<String> = [],
    val indexerPropertyTypes: Array<KClass<*>> = [],
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedWinRTExposedType

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class WinRTAuthoredRuntimeClass(
    val baseClassName: String = "",
    val interfaceNames: Array<String> = [],
    val overridableInterfaceNames: Array<String> = [],
    val activatableFactoryInterfaceName: String = "",
    val staticFactoryInterfaceNames: Array<String> = [],
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class GeneratedWinRTExposedExternalType(
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

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class WinRTSupportedOSPlatform(
    val value: String,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class WinRTContractVersion(
    val contract: String,
    val version: Long,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class WinRTExperimental

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class WinRTAttributeUsage(
    val targets: Long,
    val allowMultiple: Boolean = false,
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class WinRTDefaultOverload

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class WinRTOverload(
    val name: String,
)
