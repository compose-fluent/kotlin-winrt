package io.github.kitectlab.winrt.authoring

import io.github.kitectlab.winrt.runtime.ComWrappersSupport
import io.github.kitectlab.winrt.runtime.ComMethodSignature
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.WinRtCcwDefinition
import io.github.kitectlab.winrt.runtime.WinRtComInterfaceBaseKind
import io.github.kitectlab.winrt.runtime.WinRtComposableObjectReference
import io.github.kitectlab.winrt.runtime.WinRtInspectableInterfaceDefinition
import io.github.kitectlab.winrt.runtime.WinRtInspectableMethodDefinition
import kotlin.reflect.KClass

data class WinRtAuthoredTypeDefinition<T : Any>(
    val runtimeClassName: String,
    val defaultInterfaceId: Guid,
    val interfaces: List<WinRtAuthoredInterfaceDefinition<T>>,
    val composableBaseClassName: String? = null,
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        require(interfaces.isNotEmpty()) { "Authored type must expose at least one interface." }
        require(interfaces.any { it.interfaceId == defaultInterfaceId }) {
            "Default authored interface must be present in the interface list."
        }
    }

    internal fun toRuntimeDefinition(value: T): WinRtCcwDefinition =
        WinRtCcwDefinition(
            interfaceDefinitions = interfaces.map { it.toRuntimeDefinition(value) },
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = runtimeClassName,
        )
}

data class WinRtAuthoredInterfaceDefinition<T : Any>(
    val interfaceId: Guid,
    val methods: List<WinRtAuthoredMethodDefinition<T>>,
    val baseKind: WinRtComInterfaceBaseKind = WinRtComInterfaceBaseKind.IInspectable,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
) {
    internal fun toRuntimeDefinition(value: T): WinRtInspectableInterfaceDefinition =
        WinRtInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = methods.map { it.toRuntimeDefinition(value) },
            baseKind = baseKind,
        )
}

data class WinRtAuthoredMethodDefinition<T : Any>(
    val signature: ComMethodSignature,
    val handler: T.(List<Any?>) -> Int,
) {
    internal fun toRuntimeDefinition(value: T): WinRtInspectableMethodDefinition =
        WinRtInspectableMethodDefinition(signature) { args -> value.handler(args) }
}

object WinRtAuthoring {
    fun registerType(
        implementationType: KClass<*>,
        definition: WinRtAuthoredTypeDefinition<Any>,
    ): Boolean =
        ComWrappersSupport.registerCcwFactory(implementationType) { value ->
            definition.toRuntimeDefinition(value)
        }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerType(
        definition: WinRtAuthoredTypeDefinition<T>,
    ): Boolean =
        registerType(T::class, definition as WinRtAuthoredTypeDefinition<Any>)

    fun createComposableObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRtComposableObjectReference =
        ComWrappersSupport.createComposableCCWForObject(
            value = value,
            outerInterfaceId = outerInterfaceId,
            createInstance = createInstance,
        )
}
