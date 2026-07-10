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
import io.github.composefluent.winrt.runtime.WinRTCcwDefinition
import io.github.composefluent.winrt.runtime.WinRTComInterfaceBaseKind
import io.github.composefluent.winrt.runtime.WinRTComposableObjectReference
import io.github.composefluent.winrt.runtime.WinRTInspectableInterfaceDefinition
import io.github.composefluent.winrt.runtime.WinRTInspectableMethodDefinition
import io.github.composefluent.winrt.runtime.WinRTUnsupportedOperationException
import kotlin.reflect.KClass

data class WinRTAuthoredTypeDefinition<T : Any>(
    val runtimeClassName: String,
    val defaultInterfaceId: Guid,
    val interfaces: List<WinRTAuthoredInterfaceDefinition<T>>,
    val composableBaseClassName: String? = null,
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        require(interfaces.isNotEmpty()) { "Authored type must expose at least one interface." }
        require(interfaces.any { it.interfaceId == defaultInterfaceId }) {
            "Default authored interface must be present in the interface list."
        }
    }

    internal fun toRuntimeDefinition(value: T): WinRTCcwDefinition =
        WinRTCcwDefinition(
            interfaceDefinitions = interfaces.map { it.toRuntimeDefinition(value) },
            defaultInterfaceId = defaultInterfaceId,
            runtimeClassName = runtimeClassName,
        )
}

data class WinRTAuthoredInterfaceDefinition<T : Any>(
    val interfaceId: Guid,
    val methods: List<WinRTAuthoredMethodDefinition<T>>,
    val baseKind: WinRTComInterfaceBaseKind = WinRTComInterfaceBaseKind.IInspectable,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
) {
    internal fun toRuntimeDefinition(value: T): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            methods = methods.map { it.toRuntimeDefinition(value) },
            baseKind = baseKind,
        )
}

data class WinRTAuthoredMethodDefinition<T : Any>(
    val signature: ComMethodSignature,
    val handler: T.(List<Any?>) -> Int,
) {
    internal fun toRuntimeDefinition(value: T): WinRTInspectableMethodDefinition =
        WinRTInspectableMethodDefinition(signature) { args -> value.handler(args) }
}

data class WinRTAuthoredActivationFactoryDefinition<T : Any>(
    val runtimeClassName: String,
    val implementationType: KClass<T>,
    val createInstance: (() -> T)? = null,
    val factoryInterfaces: List<WinRTAuthoredInterfaceDefinition<WinRTAuthoredActivationFactoryDefinition<T>>> = emptyList(),
    val composableFactories: List<WinRTAuthoredComposableFactoryDefinition> = emptyList(),
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
    }

    internal fun createReference(): ComObjectReference {
        val interfaces = buildList {
            add(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IActivationFactory,
                    baseKind = WinRTComInterfaceBaseKind.IInspectable,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
                            activateInstance(args.single() as RawAddress)
                        },
                    ),
                ),
            )
            addAll(factoryInterfaces.map { it.toRuntimeDefinition(this@WinRTAuthoredActivationFactoryDefinition) })
            addAll(composableFactories.map { it.toRuntimeDefinition() })
        }
        return ComWrappersSupport.createCCWForObject(
            AuthoredActivationFactoryInstance(runtimeClassName, interfaces),
            IID.IActivationFactory,
        )
    }

    private fun activateInstance(instanceOut: RawAddress): Int {
        val create = createInstance
            ?: throw WinRTUnsupportedOperationException(
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

data class WinRTAuthoredComposableFactoryDefinition(
    val interfaceId: Guid,
    val signature: ComMethodSignature,
    val createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
) {
    internal fun toRuntimeDefinition(): WinRTInspectableInterfaceDefinition =
        WinRTInspectableInterfaceDefinition(
            interfaceId = interfaceId,
            baseKind = WinRTComInterfaceBaseKind.IInspectable,
            methods = listOf(
                WinRTInspectableMethodDefinition(signature) { args ->
                    require(args.size >= 3) {
                        "Composable factory method requires baseInterface, innerOut, and instanceOut ABI arguments."
                    }
                    createInstance(
                        args[args.lastIndex - 2] as RawAddress,
                        args[args.lastIndex - 1] as RawAddress,
                        args[args.lastIndex] as RawAddress,
                    )
                },
            ),
        )
}

private data class AuthoredActivationFactoryInstance(
    val runtimeClassName: String,
    val interfaces: List<WinRTInspectableInterfaceDefinition>,
)

object WinRTAuthoring {
    private val activationFactoryFallbacks = mutableListOf<(String, Guid) -> RawAddress>()

    init {
        ensureActivationFactoryHolderRegistered()
    }

    private fun ensureActivationFactoryHolderRegistered() {
        ComWrappersSupport.registerCcwFactory(AuthoredActivationFactoryInstance::class) { value ->
            val factory = value as AuthoredActivationFactoryInstance
            WinRTCcwDefinition(
                interfaceDefinitions = factory.interfaces,
                defaultInterfaceId = IID.IActivationFactory,
                runtimeClassName = factory.runtimeClassName,
            )
        }
    }

    fun registerType(
        implementationType: KClass<*>,
        definition: WinRTAuthoredTypeDefinition<Any>,
    ): Boolean =
        ComWrappersSupport.registerCcwFactory(implementationType) { value ->
            definition.toRuntimeDefinition(value)
        }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerType(
        definition: WinRTAuthoredTypeDefinition<T>,
    ): Boolean =
        registerType(T::class, definition as WinRTAuthoredTypeDefinition<Any>)

    fun createComposableObject(
        value: Any,
        outerInterfaceId: Guid? = null,
        instanceInterfaceId: Guid? = null,
        createInstance: (baseInterface: RawAddress, innerOut: RawAddress, instanceOut: RawAddress) -> Int,
    ): WinRTComposableObjectReference =
        ComWrappersSupport.createComposableCCWForObject(
            value = value,
            outerInterfaceId = outerInterfaceId,
            instanceInterfaceId = instanceInterfaceId,
            createInstance = createInstance,
        )

    fun createComposableObjectWithFactory(
        value: Any,
        outerInterfaceId: Guid? = null,
        composableFactory: ComObjectReference,
        createInstanceSlot: Int,
    ): WinRTComposableObjectReference =
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
        definition: WinRTAuthoredActivationFactoryDefinition<T>,
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
        factoryInterfaces: List<WinRTAuthoredInterfaceDefinition<WinRTAuthoredActivationFactoryDefinition<T>>> = emptyList(),
    ): Boolean =
        registerActivationFactory(
            WinRTAuthoredActivationFactoryDefinition(
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

object WinRTAuthoringHostBridge {
    private val CLASS_E_CLASSNOTAVAILABLE = HResult(0x80040111.toInt())

    fun dllGetActivationFactory(
        activatableClassId: RawAddress,
        factoryOut: RawAddress,
    ): Int {
        if (PlatformAbi.isNull(activatableClassId) || PlatformAbi.isNull(factoryOut)) {
            return KnownHResults.E_INVALIDARG.value
        }

        PlatformAbi.writePointer(factoryOut, PlatformAbi.nullPointer)

        return try {
            val runtimeClassName = NativeStringMarshaller.fromAbi(activatableClassId)
            val factory = WinRTAuthoring.getActivationFactory(runtimeClassName)
            if (PlatformAbi.isNull(factory)) {
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
