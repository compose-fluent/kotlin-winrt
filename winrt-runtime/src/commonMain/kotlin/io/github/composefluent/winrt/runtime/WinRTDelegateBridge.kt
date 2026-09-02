package io.github.composefluent.winrt.runtime

object WinRTDelegateBridge {
    fun createUnitDelegate(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        callback: (List<Any?>) -> Unit,
    ): WinRTDelegateHandle =
        createDelegate(
            iid = iid,
            parameterKinds = parameterKinds,
            returnKind = WinRTDelegateValueKind.UNIT,
        ) { arguments ->
            callback(arguments)
            Unit
        }

    fun createDelegate(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        returnKind: WinRTDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        callback: (List<Any?>) -> Any?,
    ): WinRTDelegateHandle = createDelegate(
        descriptor = WinRTDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
            returnKind = returnKind,
            parameterStructAdapters = parameterStructAdapters,
            returnStructAdapter = returnStructAdapter,
            runtimeClassName = runtimeClassName,
        ),
        callback = callback,
    )

    /**
     * Creates a delegate from an already metadata-composed closed descriptor.
     *
     * Generated projected delegates keep this descriptor in their companion metadata object.
     * Reusing it avoids rebuilding the parameter-shape lists and closed signature on every
     * managed lambda marshaling operation while preserving the existing CCW/cache contract.
     */
    fun createDelegate(
        descriptor: WinRTDelegateDescriptor,
        callback: (List<Any?>) -> Any?,
    ): WinRTDelegateHandle = createDelegateCore(
        descriptor = descriptor,
        callback = callback,
        rawWordCallback = null,
    )

    /**
     * Creates a delegate whose Invoke slot is a compiler-generated static ABI entry point.
     *
     * The entry point recovers [managedTarget] from the CCW binding, so no per-instance
     * compatibility callback or argument-list closure is needed on the Native hot path. The
     * descriptor remains the single source of ABI shape and ownership metadata.
     */
    fun createDelegateStatic(
        descriptor: WinRTDelegateDescriptor,
        managedTarget: Any,
        abiEntryPoint: RawAddress,
    ): WinRTDelegateHandle = createDelegateCore(
        descriptor = descriptor,
        callback = staticEntryCompatibilityCallback,
        managedTarget = managedTarget,
        abiEntryPoint = abiEntryPoint,
        rawWordCallback = null,
    )

    internal fun createUnitDelegateRaw(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        callback: (List<Any?>) -> Unit,
        rawWordCallback: ComRawWordCallback,
    ): WinRTDelegateHandle = createUnitDelegateRaw(
        descriptor = WinRTDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
            returnKind = WinRTDelegateValueKind.UNIT,
        ),
        callback = callback,
        rawWordCallback = rawWordCallback,
    )

    /**
     * Creates a scalar-carrier delegate from an already closed descriptor.
     *
     * Event sources and other generated helpers keep the descriptor composed from WinMD facts;
     * accepting it here avoids rebuilding the parameter-kind list and closed ABI signature for
     * every subscription while leaving the compatibility path available to runtime-only callers.
     */
    internal fun createUnitDelegateRaw(
        descriptor: WinRTDelegateDescriptor,
        callback: (List<Any?>) -> Unit,
        rawWordCallback: ComRawWordCallback,
    ): WinRTDelegateHandle = createDelegateCore(
        descriptor = descriptor,
        callback = { arguments ->
            callback(arguments)
            Unit
        },
        rawWordCallback = rawWordCallback,
    )

    internal fun createUnitDelegateStatic(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        managedTarget: Any,
        abiEntryPoint: RawAddress,
        callback: (List<Any?>) -> Unit,
    ): WinRTDelegateHandle = createUnitDelegateStatic(
        descriptor = WinRTDelegateDescriptor(
            interfaceId = iid,
            parameterKinds = parameterKinds,
            returnKind = WinRTDelegateValueKind.UNIT,
        ),
        managedTarget = managedTarget,
        abiEntryPoint = abiEntryPoint,
        callback = callback,
    )

    /** Creates a static-entry delegate from a pre-composed closed descriptor. */
    internal fun createUnitDelegateStatic(
        descriptor: WinRTDelegateDescriptor,
        managedTarget: Any,
        abiEntryPoint: RawAddress,
    ): WinRTDelegateHandle = createDelegateCore(
        descriptor = descriptor,
        callback = staticEntryCompatibilityCallback,
        managedTarget = managedTarget,
        abiEntryPoint = abiEntryPoint,
        rawWordCallback = null,
    )

    /**
     * Compatibility overload for runtime-only callers that still need a managed callback for
     * testing or a non-generated fallback. Generated static inbound entries must use the
     * callback-free overload above: the entry point recovers [managedTarget] from the CCW host.
     */
    internal fun createUnitDelegateStatic(
        descriptor: WinRTDelegateDescriptor,
        managedTarget: Any,
        abiEntryPoint: RawAddress,
        callback: (List<Any?>) -> Unit,
    ): WinRTDelegateHandle = createDelegateCore(
        descriptor = descriptor,
        callback = { arguments ->
            callback(arguments)
            Unit
        },
        managedTarget = managedTarget,
        abiEntryPoint = abiEntryPoint,
        rawWordCallback = null,
    )

    private val staticEntryCompatibilityCallback: (List<Any?>) -> Any? = {
        error("Static delegate entry must invoke the compiler-generated ABI entry point.")
    }

    private fun createDelegateCore(
        descriptor: WinRTDelegateDescriptor,
        callback: (List<Any?>) -> Any?,
        managedTarget: Any = callback,
        abiEntryPoint: RawAddress? = null,
        rawWordCallback: ComRawWordCallback?,
    ): WinRTDelegateHandle {
        val definitionTemplate = when {
            abiEntryPoint != null -> WinRTDelegateCcwTemplates.static(
                descriptor = descriptor,
                abiEntryPoint = abiEntryPoint,
            )
            rawWordCallback == null -> WinRTDelegateCcwTemplates.compatibility(descriptor)
            else -> null
        }
        val comObject = WinRTDelegateComObject(
            descriptor = descriptor,
            callback = callback,
            managedTarget = managedTarget,
            abiEntryPoint = abiEntryPoint,
            rawWordCallback = rawWordCallback,
            definitionTemplate = definitionTemplate,
        )
        return WinRTDelegateHandle(
            descriptor = descriptor,
            callback = callback,
            comObject = comObject,
            releaseAction = comObject::releaseManagedReference,
        )
    }

    fun createDelegateArgument(
        iid: Guid,
        parameterKinds: List<WinRTDelegateValueKind>,
        returnKind: WinRTDelegateValueKind,
        parameterStructAdapters: List<NativeStructAdapter<*>?> = emptyList(),
        returnStructAdapter: NativeStructAdapter<*>? = null,
        runtimeClassName: String? = null,
        delegate: Any? = null,
        callback: ((List<Any?>) -> Any?)?,
    ): WinRTDelegateArgumentMarshaler {
        if (delegate is WinRTProjectedDelegate) {
            return createProjectedDelegateArgument(delegate)
        }
        val handle = callback?.let {
            createDelegate(
                iid = iid,
                parameterKinds = parameterKinds,
                returnKind = returnKind,
                parameterStructAdapters = parameterStructAdapters,
                returnStructAdapter = returnStructAdapter,
                runtimeClassName = runtimeClassName,
                callback = it,
            )
        }
        return WinRTDelegateArgumentMarshaler(handle = handle, reference = handle?.createReference())
    }

    fun createProjectedDelegateHandle(delegate: WinRTProjectedDelegate): WinRTDelegateHandle =
        delegate.createWinRTDelegateHandle()

    fun createProjectedDelegateArgument(delegate: WinRTProjectedDelegate?): WinRTDelegateArgumentMarshaler {
        if (delegate == null) {
            return WinRTDelegateArgumentMarshaler(handle = null, reference = null)
        }
        ComWrappersSupport.tryUnwrapObject(delegate)?.let { reference ->
            return WinRTDelegateArgumentMarshaler(handle = null, reference = reference)
        }
        return WinRTDelegateArgumentMarshaler(
            handle = null,
            reference = null,
            callScopedOwnedAbi = ProjectedDelegateCcwCache.acquireMarshalingReference(delegate),
        )
    }
}

/**
 * Builds a closed delegate descriptor from compiler-composed metadata. This is
 * intentionally a tiny common helper: the compiler owns the IID and value-kind
 * composition, while the runtime owns descriptor validation and caching.
 */
fun createWinRTDelegateDescriptor(
    interfaceId: Guid,
    returnKind: WinRTDelegateValueKind,
    vararg parameterKinds: WinRTDelegateValueKind,
): WinRTDelegateDescriptor =
    WinRTDelegateDescriptor(
        interfaceId = interfaceId,
        parameterKinds = parameterKinds.asList(),
        returnKind = returnKind,
    )

class WinRTDelegateArgumentMarshaler internal constructor(
    private val handle: WinRTDelegateHandle?,
    private val reference: ComObjectReference?,
    callScopedOwnedAbi: RawAddress = PlatformAbi.nullPointer,
) : AutoCloseable {
    private var callScopedOwnedAbi = callScopedOwnedAbi

    val abi: RawAddress =
        reference?.pointer?.asRawAddress() ?: callScopedOwnedAbi

    override fun close() {
        try {
            reference?.close()
            val ownedAbi = callScopedOwnedAbi
            callScopedOwnedAbi = PlatformAbi.nullPointer
            if (!PlatformAbi.isNull(ownedAbi)) {
                WinRTPlatformApi.releaseRaw(ownedAbi)
            }
        } finally {
            handle?.close()
        }
    }
}
