package io.github.composefluent.winrt.runtime

private val uriTypeHandle = WinRtTypeHandle("Windows.Foundation.Uri", Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
private val closableTypeHandle = WinRtTypeHandle("Windows.Foundation.IClosable", IID.IDisposable)

internal object WinRtBuiltInProjectionRuntimeHooks {
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
            WinRtClosableObject(inspectable)
        }
    }

    fun tryCreateProjectedReference(
        value: Any,
        interfaceId: Guid?,
    ): ComObjectReference? =
        when (value) {
            is WinRtUri -> UriProjection.createReference(value, interfaceId ?: IID.IInspectable)
            else -> null
        }

    fun createSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? =
        platformCreateSyntheticCcwDefinition(value)

    fun runtimeClassNameFor(value: Any): String? =
        platformRuntimeClassNameFor(value)
}
