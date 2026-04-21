package io.github.kitectlab.winrt.runtime

private val uriTypeHandle = WinRtTypeHandle(WinRtUri::class.typeDisplayName(), Guid("9E365E57-48B2-4160-956F-C7385120BBFC"))
private val closableTypeHandle = WinRtTypeHandle(AutoCloseable::class.typeDisplayName(), IID.IDisposable)

internal val TYPE_PROJECTION_ABI_LAYOUT = NativeLayoutsJvmCompat.TYPE_NAME

internal object WinRtBuiltInProjectionRuntimeHooks {
    fun ensureRegistered() {
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

    fun createSyntheticCcwDefinition(value: Any): WinRtCcwDefinition? {
        WinRtValueBoxing.createInspectableBoxDefinition(value)?.let { return it }
        if (value is AutoCloseable) {
            return WinRtCcwDefinition(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = IID.IDisposable,
                        methods = listOf(
                            WinRtInspectableMethodDefinition(
                                descriptor = NativeFunctionDescriptor.of(
                                    NativeValueLayout.JAVA_INT,
                                    NativeValueLayout.ADDRESS,
                                ),
                            ) { _ ->
                                value.close()
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
                defaultInterfaceId = IID.IDisposable,
                runtimeClassName = runtimeClassNameFor(value),
            )
        }
        return null
    }

    fun runtimeClassNameFor(value: Any): String? {
        WinRtValueBoxing.boxedRuntimeClassNameForType(value::class)?.let { return it }
        val lookupName =
            TypeNameSupport.getNameForType(
                value::class,
                setOf(TypeNameGenerationFlag.ForGetRuntimeClassName),
            )
        return lookupName.takeIf(String::isNotBlank) ?: value::class.qualifiedName ?: value::class.toString()
    }
}
