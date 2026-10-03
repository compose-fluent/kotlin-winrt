package io.github.composefluent.winrt.runtime

interface IWinRTObject {
    val nativeObject: ComObjectReference

    /**
     * Kotlin-side equivalent of the "typed RCW" fast-path in cswinrt's `SingleInterfaceOptimizedObject`.
     *
     * When a wrapper already represents a specific projected interface type, later runtime projection code
     * must be able to retrieve that object reference directly instead of redundantly issuing `QueryInterface`.
     */
    val primaryTypeHandle: WinRTTypeHandle?
        get() = null

    val hasUnwrappableNativeObject: Boolean
        get() = true

    val queryInterfaceCache: ConcurrentCacheMap<WinRTTypeHandle, ComObjectReference>
        get() = winRTObjectSupport.queryInterfaceCache(this)

    val additionalTypeData: ConcurrentCacheMap<WinRTTypeHandle, Any>
        get() = winRTObjectSupport.additionalTypeData(this)

    fun isInterfaceImplemented(
        interfaceType: WinRTTypeHandle,
        throwIfNotImplemented: Boolean = false,
    ): Boolean =
        winRTObjectSupport.isInterfaceImplemented(
            instance = this,
            primaryTypeHandle = nativePrimaryTypeHandle(),
            interfaceType = interfaceType,
            nativeObject = nativeObject,
            throwIfNotImplemented = throwIfNotImplemented,
            tryQueryInterface = nativeObject::tryQueryInterface,
            missingInterfaceError = ::missingInterfaceError,
        )

    fun getObjectReferenceForType(interfaceType: WinRTTypeHandle): ComObjectReference =
        winRTObjectSupport.getObjectReferenceForType(
            instance = this,
            primaryTypeHandle = nativePrimaryTypeHandle(),
            interfaceType = interfaceType,
            nativeObject = nativeObject,
            tryQueryInterface = nativeObject::tryQueryInterface,
            missingInterfaceError = ::missingInterfaceError,
        )

    fun <T : Any> getOrAddAdditionalTypeData(
        type: WinRTTypeHandle,
        factory: () -> T,
    ): T =
        winRTObjectSupport.getOrAddAdditionalTypeData(
            instance = this,
            type = type,
            factory = factory,
        )
}

// CsWinRT MarshalInspectable.CreateMarshaler2 uses objRef.AsValue(iid). A runtime-class
// wrapper can hold IInspectable (for example a XAML connector target), even though
// its projected primary type describes the default interface. Only bypass QI when
// the stored native reference actually has that interface's ABI layout.
private fun IWinRTObject.nativePrimaryTypeHandle(): WinRTTypeHandle? =
    primaryTypeHandle?.takeIf { it.interfaceId == nativeObject.interfaceId }

abstract class WinRTObjectBase<T : ComObjectReference>(
    nativeObject: T?,
    primaryTypeHandle: WinRTTypeHandle?,
) : IWinRTObject {
    final override lateinit var nativeObject: T
        protected set

    /**
     * Composable constructors may publish their identity before assigning the native reference.
     * Cache metadata must be able to observe that lifecycle state without triggering the lateinit
     * getter; callers fall back to the regular IWinRTObject validation until it is initialized.
     */
    internal fun tryGetInitializedNativeObject(): T? =
        if (this::nativeObject.isInitialized) nativeObject else null

    final override var primaryTypeHandle: WinRTTypeHandle? = primaryTypeHandle
        protected set

    init {
        nativeObject?.let { this.nativeObject = it }
    }
}

private val winRTObjectSupport =
    WinRTObjectSupport<IWinRTObject, ComObjectReference> { reference ->
        reference.close()
    }

private fun missingInterfaceError(interfaceType: WinRTTypeHandle): Throwable =
    WinRTUnsupportedOperationException(
        "Interface '${interfaceType.projectedTypeName}' is not implemented.",
        KnownHResults.E_NOINTERFACE,
    )
