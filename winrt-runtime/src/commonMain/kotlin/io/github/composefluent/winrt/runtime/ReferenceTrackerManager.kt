package io.github.composefluent.winrt.runtime

/**
 * Common owner of the XAML reference-tracker manager protocol.
 *
 * CsWinRT delegates this responsibility to CLR `ComWrappers`. Kotlin keeps the same COM protocol
 * here and uses the existing common CCW/RCW identity infrastructure; only GC collection remains a
 * platform operation.
 */
internal object ReferenceTrackerManager {
    private val lock = PlatformLock()
    private val trackers = linkedMapOf<Long, TrackerEntry>()
    private val disconnectedReleases = mutableListOf<() -> Unit>()
    private var managerPointer: RawComPtr = PlatformAbi.nullComPtr

    fun attach(trackerPointer: RawComPtr): Long {
        ensureManager(trackerPointer)
        val key = PlatformAbi.pointerKey(PlatformAbi.fromRawComPtr(trackerPointer))
        lock.withLock {
            val existing = trackers[key]
            if (existing != null) {
                existing.referenceCount += 1
                if (!existing.connected) {
                    invokeTracker(trackerPointer, ReferenceTrackerVftblSlots.ConnectFromTrackerSource)
                    existing.connected = true
                }
                return@withLock
            }

            invokeTracker(trackerPointer, ReferenceTrackerVftblSlots.ConnectFromTrackerSource)
            trackers[key] = TrackerEntry(
                pointer = trackerPointer,
                contextTokenKey = currentContextTokenKey(),
            )
        }
        return key
    }

    fun detach(
        registrationKey: Long,
        disconnectedRelease: () -> Unit,
    ): Boolean {
        if (registrationKey == 0L) {
            return false
        }
        val entry = lock.withLock {
            val entry = trackers[registrationKey] ?: return@withLock null
            entry.referenceCount -= 1
            if (entry.referenceCount > 0) {
                return@withLock null
            }
            trackers.remove(registrationKey)
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
        var failure: Throwable? = null
        releases.forEach { release ->
            runCatching(release).onFailure { error ->
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        failure?.let { throw it }
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
        val manager = lock.withLock { managerPointer }
        if (PlatformAbi.isNull(manager)) {
            return
        }

        checkSucceeded(
            ComVtableInvoker.invoke(
                manager,
                ReferenceTrackerManagerVftblSlots.ReferenceTrackingStarted,
            ),
        )
        var walkFailed = false
        try {
            val snapshot = lock.withLock {
                trackers.values.filter(TrackerEntry::connected).map(TrackerEntry::pointer)
            }
            findReferenceTargetsCallback.createReference(IID.IFindReferenceTargetsCallback).use { callback ->
                for (tracker in snapshot) {
                    val hResult = ComVtableInvoker.invokeArgs(
                        tracker,
                        ReferenceTrackerVftblSlots.FindTrackerTargets,
                        callback.pointer,
                    )
                    if (hResult < 0) {
                        walkFailed = true
                        break
                    }
                }
            }
        } catch (failure: Throwable) {
            walkFailed = true
            throw failure
        } finally {
            try {
                checkSucceeded(
                    ComVtableInvoker.invokeArgs(
                        manager,
                        ReferenceTrackerManagerVftblSlots.FindTrackerTargetsCompleted,
                        if (walkFailed) 1 else 0,
                    ),
                )
                PlatformFinalization.collect()
            } finally {
                checkSucceeded(
                    ComVtableInvoker.invoke(
                        manager,
                        ReferenceTrackerManagerVftblSlots.ReferenceTrackingCompleted,
                    ),
                )
            }
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
                            if (PlatformAbi.isNull(args[0] as RawAddress)) {
                                KnownHResults.E_POINTER.value
                            } else {
                                KnownHResults.S_OK.value
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
        var referenceCount: Int = 1,
        var connected: Boolean = true,
    )
}
