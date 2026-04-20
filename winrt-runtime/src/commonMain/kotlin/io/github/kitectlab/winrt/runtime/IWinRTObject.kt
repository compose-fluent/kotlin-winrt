package io.github.kitectlab.winrt.runtime

interface IWinRTObject {
    val nativeObject: ComObjectReference

    /**
     * Kotlin-side equivalent of the "typed RCW" fast-path in cswinrt's `SingleInterfaceOptimizedObject`.
     *
     * When a wrapper already represents a specific projected interface type, later runtime projection code
     * must be able to retrieve that object reference directly instead of redundantly issuing `QueryInterface`.
     */
    val primaryTypeHandle: WinRtTypeHandle?
        get() = null

    val hasUnwrappableNativeObject: Boolean
        get() = true

    val queryInterfaceCache: ConcurrentCacheMap<WinRtTypeHandle, ComObjectReference>
        get() = winRtObjectSupport.queryInterfaceCache(this)

    val additionalTypeData: ConcurrentCacheMap<WinRtTypeHandle, Any>
        get() = winRtObjectSupport.additionalTypeData(this)

    fun isInterfaceImplemented(
        interfaceType: WinRtTypeHandle,
        throwIfNotImplemented: Boolean = false,
    ): Boolean =
        winRtObjectSupport.isInterfaceImplemented(
            instance = this,
            primaryTypeHandle = primaryTypeHandle,
            interfaceType = interfaceType,
            nativeObject = nativeObject,
            throwIfNotImplemented = throwIfNotImplemented,
            tryQueryInterface = nativeObject::tryQueryInterface,
            missingInterfaceError = ::missingInterfaceError,
        )

    fun getObjectReferenceForType(interfaceType: WinRtTypeHandle): ComObjectReference =
        winRtObjectSupport.getObjectReferenceForType(
            instance = this,
            primaryTypeHandle = primaryTypeHandle,
            interfaceType = interfaceType,
            nativeObject = nativeObject,
            tryQueryInterface = nativeObject::tryQueryInterface,
            missingInterfaceError = ::missingInterfaceError,
        )

    fun <T : Any> getOrAddAdditionalTypeData(
        type: WinRtTypeHandle,
        factory: () -> T,
    ): T =
        winRtObjectSupport.getOrAddAdditionalTypeData(
            instance = this,
            type = type,
            factory = factory,
        )
}

private val winRtObjectSupport =
    WinRtObjectSupport<IWinRTObject, ComObjectReference> { reference ->
        reference.close()
    }

private fun missingInterfaceError(interfaceType: WinRtTypeHandle): Throwable =
    WinRtUnsupportedOperationException(
        "Interface '${interfaceType.projectedTypeName}' is not implemented.",
        KnownHResults.E_NOINTERFACE,
    )
