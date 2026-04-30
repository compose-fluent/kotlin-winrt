package io.github.kitectlab.winrt.authoring

import io.github.kitectlab.winrt.runtime.ComAbiValueKind
import io.github.kitectlab.winrt.runtime.ComMethodSignature
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.ComWrappersSupport
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.IID
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.WinRtCcwDefinition
import io.github.kitectlab.winrt.runtime.WinRtComInterfaceBaseKind
import io.github.kitectlab.winrt.runtime.WinRtComposableObjectReference
import io.github.kitectlab.winrt.runtime.WinRtInspectableInterfaceDefinition
import io.github.kitectlab.winrt.runtime.WinRtInspectableMethodDefinition
import io.github.kitectlab.winrt.runtime.WinRtUnsupportedOperationException
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

data class WinRtAuthoredActivationFactoryDefinition<T : Any>(
    val runtimeClassName: String,
    val implementationType: KClass<T>,
    val createInstance: (() -> T)? = null,
    val factoryInterfaces: List<WinRtAuthoredInterfaceDefinition<WinRtAuthoredActivationFactoryDefinition<T>>> = emptyList(),
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
    }

    internal fun createReference(): ComObjectReference {
        val interfaces = buildList {
            add(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = IID.IActivationFactory,
                    baseKind = WinRtComInterfaceBaseKind.IInspectable,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                            activateInstance(args.single() as RawAddress)
                        },
                    ),
                ),
            )
            addAll(factoryInterfaces.map { it.toRuntimeDefinition(this@WinRtAuthoredActivationFactoryDefinition) })
        }
        return ComWrappersSupport.createCCWForObject(
            AuthoredActivationFactoryInstance(runtimeClassName, interfaces),
            IID.IActivationFactory,
        )
    }

    private fun activateInstance(instanceOut: RawAddress): Int {
        val create = createInstance
            ?: throw WinRtUnsupportedOperationException(
                "Authored runtime class '$runtimeClassName' does not provide a default activation constructor.",
                KnownHResults.E_NOTIMPL,
            )
        val instance = create()
        ComWrappersSupport.createCCWForObject(instance).use { reference ->
            PlatformAbi.writePointer(instanceOut, PlatformAbi.fromRawComPtr(reference.getRefPointer()))
        }
        return KnownHResults.S_OK.value
    }
}

data class WinRtComposableOverrideDefinition<T : Any>(
    val runtimeClassName: String,
    val composableBaseClassName: String,
    val overrideInterfaceId: Guid,
    val methods: List<WinRtAuthoredMethodDefinition<T>>,
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        require(composableBaseClassName.isNotBlank()) { "Composable base class name must not be blank." }
        require(methods.isNotEmpty()) { "Composable override type must expose at least one method." }
    }

    internal fun toTypeDefinition(): WinRtAuthoredTypeDefinition<T> =
        WinRtAuthoredTypeDefinition(
            runtimeClassName = runtimeClassName,
            defaultInterfaceId = overrideInterfaceId,
            composableBaseClassName = composableBaseClassName,
            interfaces = listOf(
                WinRtAuthoredInterfaceDefinition(
                    interfaceId = overrideInterfaceId,
                    methods = methods,
                    isDefault = true,
                    isOverridable = true,
                ),
            ),
        )
}

private data class AuthoredActivationFactoryInstance(
    val runtimeClassName: String,
    val interfaces: List<WinRtInspectableInterfaceDefinition>,
)

object WinRtAuthoring {
    private val activationFactoryFallbacks = mutableListOf<(String, Guid) -> RawAddress>()

    init {
        ensureActivationFactoryHolderRegistered()
    }

    private fun ensureActivationFactoryHolderRegistered() {
        ComWrappersSupport.registerCcwFactory(AuthoredActivationFactoryInstance::class) { value ->
            val factory = value as AuthoredActivationFactoryInstance
            WinRtCcwDefinition(
                interfaceDefinitions = factory.interfaces,
                defaultInterfaceId = IID.IActivationFactory,
                runtimeClassName = factory.runtimeClassName,
            )
        }
    }

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

    fun registerComposableOverrideType(
        implementationType: KClass<*>,
        definition: WinRtComposableOverrideDefinition<Any>,
    ): Boolean =
        registerType(implementationType, definition.toTypeDefinition())

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerComposableOverrideType(
        definition: WinRtComposableOverrideDefinition<T>,
    ): Boolean =
        registerComposableOverrideType(T::class, definition as WinRtComposableOverrideDefinition<Any>)

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

    fun createComposableObjectWithFactory(
        value: Any,
        outerInterfaceId: Guid? = null,
        composableFactory: ComObjectReference,
        createInstanceSlot: Int,
    ): WinRtComposableObjectReference =
        createComposableObject(
            value = value,
            outerInterfaceId = outerInterfaceId,
        ) { baseInterface, innerOut, instanceOut ->
            val hResult = ComVtableInvoker.invokeArgs(
                instance = composableFactory.pointer,
                slot = createInstanceSlot,
                arg0 = baseInterface,
                arg1 = innerOut,
                arg2 = instanceOut,
            )
            HResult(hResult).requireSuccess()
            hResult
        }

    fun <T : Any> registerActivationFactory(
        definition: WinRtAuthoredActivationFactoryDefinition<T>,
    ): Boolean {
        ensureActivationFactoryHolderRegistered()
        return ComWrappersSupport.registerAuthoringActivationFactory(definition.runtimeClassName) {
            definition.createReference()
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerActivationFactory(
        runtimeClassName: String,
        noinline createInstance: (() -> T)? = null,
        factoryInterfaces: List<WinRtAuthoredInterfaceDefinition<WinRtAuthoredActivationFactoryDefinition<T>>> = emptyList(),
    ): Boolean =
        registerActivationFactory(
            WinRtAuthoredActivationFactoryDefinition(
                runtimeClassName = runtimeClassName,
                implementationType = T::class,
                createInstance = createInstance,
                factoryInterfaces = factoryInterfaces,
            ),
        )

    fun getActivationFactory(runtimeClassName: String): RawAddress =
        getActivationFactory(runtimeClassName, IID.IActivationFactory)

    fun getActivationFactory(
        runtimeClassName: String,
        interfaceId: Guid,
    ): RawAddress {
        val result = ComWrappersSupport.tryGetAuthoringActivationFactory(runtimeClassName, interfaceId)
        if (result.isSuccess) {
            return result.pointer
        }
        return activationFactoryFallbacks.firstNotNullOfOrNull { fallback ->
            fallback(runtimeClassName, interfaceId).takeUnless(PlatformAbi::isNull)
        } ?: PlatformAbi.nullPointer
    }

    fun registerActivationFactoryFallback(
        lookup: (runtimeClassName: String, interfaceId: Guid) -> RawAddress,
    ) {
        activationFactoryFallbacks.add(lookup)
    }

    fun clearActivationFactoryFallbacksForTests() {
        activationFactoryFallbacks.clear()
    }
}
