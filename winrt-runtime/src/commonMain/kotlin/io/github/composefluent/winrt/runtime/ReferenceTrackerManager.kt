package io.github.composefluent.winrt.runtime

/**
 * Common owner of the XAML reference-tracker manager protocol.
 *
 * CsWinRT delegates this responsibility to CLR `ComWrappers`/`TrackerObjectManager`. Kotlin's
 * collectors do not expose CLR dependent handles or GC callouts. During a XAML-requested walk,
 * each RCW therefore owns ordinary managed references to its discovered targets. Global tracker
 * pins are removed only after these edges are published, while XAML still locks its native graph.
 * Pins are restored before unlocking; ordinary collections outside this protocol stay conservative.
 * Native collection waits for cleaners, so COM releases are deferred until the XAML lock is gone.
 */
internal object ReferenceTrackerManager {
    private val lock = PlatformLock()
    private val trackers = linkedMapOf<Long, TrackerEntry>()
    private val registrations = mutableMapOf<Long, TrackerEntry>()
    private var nextRegistrationKey = 1L
    private val targets = mutableSetOf<ManagedComHostState>()
    private val disconnectedReleases = mutableListOf<() -> Unit>()
    private val finalizerReleases = mutableListOf<() -> Unit>()
    private var collecting = false
    private var currentSources: List<ReferenceTrackerSource>? = null
    private var managerPointer: RawComPtr = PlatformAbi.nullComPtr

    @kotlin.concurrent.Volatile
    internal var isGlobalPeggingEnabled: Boolean = true
        private set

    internal fun registerTarget(target: ManagedComHostState) {
        lock.withLock { targets += target }
    }

    internal fun unregisterTarget(target: ManagedComHostState) {
        lock.withLock { targets -= target }
    }

    fun attach(
        trackerPointer: RawComPtr,
        source: PlatformManagedWeakReference<ReferenceTrackerSource>,
    ): Long {
        ensureManager(trackerPointer)
        val key = PlatformAbi.pointerKey(PlatformAbi.fromRawComPtr(trackerPointer))
        var connect = false
        val registrationKey = lock.withLock {
            val entry = trackers.getOrPut(key) {
                TrackerEntry(pointer = trackerPointer, contextTokenKey = currentContextTokenKey())
            }
            if (!entry.connected) {
                entry.connected = true
                connect = true
            }
            val registration = nextRegistrationKey++
            entry.sources[registration] = source
            registrations[registration] = entry
            registration
        }
        if (connect) invokeTracker(trackerPointer, ReferenceTrackerVftblSlots.ConnectFromTrackerSource)
        return registrationKey
    }

    fun detach(
        registrationKey: Long,
        disconnectedRelease: () -> Unit,
    ): Boolean {
        if (registrationKey == 0L) {
            return false
        }
        val entry = lock.withLock {
            val entry = registrations.remove(registrationKey) ?: return@withLock null
            entry.sources.remove(registrationKey)
            if (entry.sources.isNotEmpty()) {
                return@withLock null
            }
            trackers.remove(PlatformAbi.pointerKey(PlatformAbi.fromRawComPtr(entry.pointer)))
            disconnectedReleases += disconnectedRelease
            entry
        } ?: return false
        if (entry.connected) {
            invokeTracker(entry.pointer, ReferenceTrackerVftblSlots.DisconnectFromTrackerSource)
        }
        return true
    }

    fun releaseDisconnectedReferenceSources() {
        val releases = lock.withLock {
            disconnectedReleases.toList().also { disconnectedReleases.clear() }
        }
        releaseAll(releases)
    }

    private fun releaseAll(releases: List<() -> Unit>) {
        var failure: Throwable? = null
        releases.forEach { release ->
            runCatching(release).onFailure { error ->
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        failure?.let { throw it }
    }

    internal fun deferFinalizerRelease(release: () -> Unit): Boolean = lock.withLock {
        if (!collecting) return@withLock false
        finalizerReleases += release
        true
    }

    internal fun clearForTests() {
        releaseDisconnectedReferenceSources()
        lock.withLock {
            trackers.values.forEach { entry ->
                if (entry.connected) {
                    invokeTracker(entry.pointer, ReferenceTrackerVftblSlots.DisconnectFromTrackerSource)
                }
            }
            trackers.clear()
            registrations.clear()
            if (!PlatformAbi.isNull(managerPointer)) {
                WinRTPlatformApi.releaseRaw(PlatformAbi.fromRawComPtr(managerPointer))
                managerPointer = PlatformAbi.nullComPtr
            }
        }
    }

    private fun ensureManager(trackerPointer: RawComPtr) {
        lock.withLock {
            if (!PlatformAbi.isNull(managerPointer)) {
                return@withLock
            }
            val candidate = queryTrackerManager(trackerPointer)
            try {
                referenceTrackerHost.createReference(IID.IReferenceTrackerHost).use { host ->
                    checkSucceeded(
                        ComVtableInvoker.invokeArgs(
                            candidate,
                            ReferenceTrackerManagerVftblSlots.SetReferenceTrackerHost,
                            host.pointer,
                        ),
                    )
                }
                managerPointer = candidate
            } catch (failure: Throwable) {
                WinRTPlatformApi.releaseRaw(PlatformAbi.fromRawComPtr(candidate))
                throw failure
            }
        }
    }

    private fun queryTrackerManager(trackerPointer: RawComPtr): RawComPtr =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            checkSucceeded(
                ComVtableInvoker.invokeArgs(
                    trackerPointer,
                    ReferenceTrackerVftblSlots.GetReferenceTrackerManager,
                    resultOut,
                ),
            )
            PlatformAbi.readPointer(resultOut)
                .takeUnless(PlatformAbi::isNull)
                ?.let(PlatformAbi::toRawComPtr)
                ?: throw WinRTNullReferenceException(
                    "IReferenceTracker.GetReferenceTrackerManager returned a null pointer.",
                    KnownHResults.E_POINTER,
                )
        }

    internal fun collect() {
        val manager = lock.withLock {
            if (collecting || PlatformAbi.isNull(managerPointer)) return
            collecting = true
            managerPointer
        }
        if (PlatformAbi.isNull(manager)) {
            return
        }
        try {
            checkSucceeded(
                ComVtableInvoker.invoke(manager, ReferenceTrackerManagerVftblSlots.ReferenceTrackingStarted),
            )
            try {
                var walkFailed = true
                try {
                    walkFailed = !walkTrackerSources()
                } finally {
                    checkSucceeded(
                        ComVtableInvoker.invokeArgs(
                            manager,
                            ReferenceTrackerManagerVftblSlots.FindTrackerTargetsCompleted,
                            if (walkFailed) 1 else 0,
                        ),
                    )
                }
                if (!walkFailed) {
                    // Unlike CLR dependent handles, Kotlin owns the edges on each RCW. Only
                    // remove global pins after every edge and every XAML root peg is published.
                    setGlobalPegging(false)
                    PlatformFinalization.collectForReferenceTracking()
                    disconnectCollectedSources()
                }
            } finally {
                try {
                    setGlobalPegging(true)
                } finally {
                    checkSucceeded(
                        ComVtableInvoker.invoke(manager, ReferenceTrackerManagerVftblSlots.ReferenceTrackingCompleted),
                    )
                }
            }
        } finally {
            val releases = lock.withLock {
                collecting = false
                finalizerReleases.toList().also { finalizerReleases.clear() }
            }
            // A failed release must not strand the other references queued by this collection.
            releaseAll(releases)
        }
    }

    private fun setGlobalPegging(enabled: Boolean) {
        isGlobalPeggingEnabled = enabled
        val snapshot = lock.withLock { targets.toList() }
        snapshot.forEach { it.updateTrackerRoot() }
    }

    private fun walkTrackerSources(): Boolean {
        val snapshot = lock.withLock { trackers.values.filter(TrackerEntry::connected) }
        findReferenceTargetsCallback.createReference(IID.IFindReferenceTargetsCallback).use { callback ->
            for (entry in snapshot) {
                val sources = lock.withLock { entry.sources.values.mapNotNull { it.get() } }
                sources.forEach { it.targets.clear() }
                currentSources = sources
                try {
                    if (ComVtableInvoker.invokeArgs(
                            entry.pointer,
                            ReferenceTrackerVftblSlots.FindTrackerTargets,
                            callback.pointer,
                        ) < 0
                    ) return false
                } finally {
                    currentSources = null
                }
            }
        }
        // No local owning source or target may remain in the collecting stack frame.
        return true
    }

    private fun disconnectCollectedSources() {
        val snapshot = lock.withLock {
            trackers.values.filter { entry -> entry.connected && entry.sources.values.none { it.get() != null } }
        }
        for (entry in snapshot) {
            invokeTracker(entry.pointer, ReferenceTrackerVftblSlots.DisconnectFromTrackerSource)
            entry.connected = false
        }
    }

    private fun disconnectCurrentThread() {
        val contextTokenKey = currentContextTokenKey()
        lock.withLock {
            trackers.values.forEach { entry ->
                if (entry.connected && entry.contextTokenKey == contextTokenKey) {
                    invokeTracker(entry.pointer, ReferenceTrackerVftblSlots.DisconnectFromTrackerSource)
                    entry.connected = false
                }
            }
        }
    }

    private fun getTrackerTarget(
        unknown: RawAddress,
        resultOut: RawAddress,
    ): Int {
        PlatformAbi.writePointer(resultOut, PlatformAbi.nullPointer)
        if (PlatformAbi.isNull(unknown)) {
            return KnownHResults.E_INVALIDARG.value
        }

        platformTryWinRTProjectionInboundBinding(unknown.value)?.host?.let { host ->
            host.createReference(IID.IReferenceTrackerTarget).use { target ->
                PlatformAbi.writePointer(resultOut, target.getRefPointer().asRawAddress())
            }
            return KnownHResults.S_OK.value
        }

        val identityResult = WinRTPlatformApi.queryInterfaceRaw(unknown, IID.IUnknown)
        if (identityResult.hResultValue < 0 || PlatformAbi.isNull(identityResult.pointer)) {
            return identityResult.hResultValue.takeIf { it < 0 } ?: KnownHResults.E_NOINTERFACE.value
        }
        return try {
            val proxy = ComWrappersSupport.createRcwForComObject(identityResult.pointer)
                ?: return KnownHResults.E_NOINTERFACE.value
            ComWrappersSupport.createReferenceTrackerTargetForObject(proxy).use { target ->
                PlatformAbi.writePointer(resultOut, target.getRefPointer().asRawAddress())
            }
            KnownHResults.S_OK.value
        } finally {
            WinRTPlatformApi.releaseRaw(identityResult.pointer)
        }
    }

    private fun invokeTracker(
        trackerPointer: RawComPtr,
        slot: Int,
    ) {
        checkSucceeded(ComVtableInvoker.invoke(trackerPointer, slot))
    }

    private fun checkSucceeded(hResultValue: Int) {
        WinRTPlatformApi.checkSucceededRaw(hResultValue)
    }

    private fun currentContextTokenKey(): Long =
        runCatching { PlatformAbi.pointerKey(Context.getContextToken()) }.getOrDefault(0L)

    private val referenceTrackerHost: WinRTInspectableComObject by lazy {
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IReferenceTrackerHost,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int32) { _, _ ->
                            collect()
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ ->
                            releaseDisconnectedReferenceSources()
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult) { _, _ ->
                            disconnectCurrentThread()
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr_Ptr) { _, args ->
                            getTrackerTarget(args[0] as RawAddress, args[1] as RawAddress)
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int64) { _, _ ->
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Int64) { _, _ ->
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
                unknownInterfaceDefinition,
            ),
            defaultInterfaceId = IID.IReferenceTrackerHost,
        )
    }

    private val findReferenceTargetsCallback: WinRTInspectableComObject by lazy {
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IFindReferenceTargetsCallback,
                    baseKind = WinRTComInterfaceBaseKind.IUnknown,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(ComMethodSignatures.HResult_Ptr) { _, args ->
                            val pointer = args[0] as RawAddress
                            if (PlatformAbi.isNull(pointer)) {
                                KnownHResults.E_POINTER.value
                            } else {
                                val target = WinRTInspectableComObject.findManagedValue(pointer)
                                if (target == null) {
                                    KnownHResults.S_FALSE.value
                                } else {
                                    currentSources?.forEach { it.targets += target }
                                    KnownHResults.S_OK.value
                                }
                            }
                        },
                    ),
                ),
                unknownInterfaceDefinition,
            ),
            defaultInterfaceId = IID.IFindReferenceTargetsCallback,
        )
    }

    private val unknownInterfaceDefinition = WinRTInspectableInterfaceDefinition(
        interfaceId = IID.IUnknown,
        baseKind = WinRTComInterfaceBaseKind.IUnknown,
        methods = emptyList(),
    )

    private class TrackerEntry(
        val pointer: RawComPtr,
        val contextTokenKey: Long,
        var connected: Boolean = false,
    ) {
        val sources = linkedMapOf<Long, PlatformManagedWeakReference<ReferenceTrackerSource>>()
    }
}

/** Kotlin counterpart of a ComWrappers dependent-handle source, owned only by its RCW. */
internal class ReferenceTrackerSource {
    val targets = mutableListOf<Any>()
    // One weak registration identity per strong RCW source; neither support nor its cleaner
    // owns the graph edges. CsWinRT's CLR ComWrappers owns the corresponding dependent handles.
    val weakReference = PlatformManagedWeakReference(this)
}
