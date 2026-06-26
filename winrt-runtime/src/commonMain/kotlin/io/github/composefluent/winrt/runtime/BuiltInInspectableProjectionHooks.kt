package io.github.composefluent.winrt.runtime

private val uriTypeHandle = WinRTTypeHandle("Windows.Foundation.Uri", Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
private val closableTypeHandle = WinRTTypeHandle("Windows.Foundation.IClosable", IID.IDisposable)

internal object WinRTBuiltInProjectionRuntimeHooks {
    fun ensureRegistered() {
        if (!FeatureSwitches.enableDefaultCustomTypeMappings) {
            return
        }
        XamlSystemProjectionRuntimeHooks.ensureRegistered()
        ComWrappersSupport.registerRuntimeClassFactory("Windows.Foundation.Uri") { inspectable ->
            inspectable.use(UriProjection::fromInspectable)
        }
        ComWrappersSupport.registerTypedRcwFactory(uriTypeHandle) { inspectable ->
            inspectable.use(UriProjection::fromInspectable)
        }
        ComWrappersSupport.registerTypedRcwFactory(closableTypeHandle) { inspectable ->
            WinRTClosableObject(inspectable)
        }
    }

    fun tryCreateProjectedReference(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? =
        when (value) {
            is WinRTUri -> UriProjection.createReference(value, interfaceId ?: IID.IInspectable)
            else -> null
        }

    fun createSyntheticCcwDefinition(value: Any): WinRTCcwDefinition? =
        platformCreateSyntheticCcwDefinition(value)

    fun runtimeClassNameFor(value: Any): String? =
        platformRuntimeClassNameFor(value)
}
