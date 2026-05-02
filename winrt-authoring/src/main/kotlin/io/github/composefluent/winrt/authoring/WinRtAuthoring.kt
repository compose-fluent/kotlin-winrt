package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ComAbiValueKind
import io.github.composefluent.winrt.runtime.ComMethodSignature
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.ExceptionHelpers
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.NativeStringMarshaller
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtCcwDefinition
import io.github.composefluent.winrt.runtime.WinRtComInterfaceBaseKind
import io.github.composefluent.winrt.runtime.WinRtComposableObjectReference
import io.github.composefluent.winrt.runtime.WinRtInspectableInterfaceDefinition
import io.github.composefluent.winrt.runtime.WinRtInspectableMethodDefinition
import io.github.composefluent.winrt.runtime.WinRtUnsupportedOperationException
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

object WinRtAuthoringHostBridge {
    private val CLASS_E_CLASSNOTAVAILABLE = HResult(0x80040111.toInt())

    fun dllGetActivationFactory(
        activatableClassId: RawAddress,
        factoryOut: RawAddress,
    ): Int {
        if (PlatformAbi.isNull(activatableClassId) || PlatformAbi.isNull(factoryOut)) {
            return KnownHResults.E_INVALIDARG.value
        }

        return try {
            val runtimeClassName = NativeStringMarshaller.fromAbi(activatableClassId)
            val factory = WinRtAuthoring.getActivationFactory(runtimeClassName)
            if (PlatformAbi.isNull(factory)) {
                PlatformAbi.writePointer(factoryOut, PlatformAbi.nullPointer)
                CLASS_E_CLASSNOTAVAILABLE.value
            } else {
                PlatformAbi.writePointer(factoryOut, factory)
                KnownHResults.S_OK.value
            }
        } catch (error: Throwable) {
            ExceptionHelpers.setErrorInfo(error)
            ExceptionHelpers.getHRForException(error).value
        }
    }

    fun dllCanUnloadNow(): Int =
        KnownHResults.S_FALSE.value
}
