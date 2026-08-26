package io.github.composefluent.winrt.runtime

@PublishedApi
internal object IUnknownVftblSlots {
    const val QueryInterface = 0
    const val AddRef = 1
    const val Release = 2
}

internal object IInspectableVftblSlots {
    const val GetIids = 3
    const val GetRuntimeClassName = 4
    const val GetTrustLevel = 5
    const val FirstCustom = 6
}

internal object IActivationFactoryVftblSlots {
    const val ActivateInstance = IInspectableVftblSlots.FirstCustom
}

object ReferenceTrackerVftblSlots {
    const val ConnectFromTrackerSource = 3
    const val DisconnectFromTrackerSource = 4
    const val FindTrackerTargets = 5
    const val GetReferenceTrackerManager = 6
    const val AddRefFromTrackerSource = 7
    const val ReleaseFromTrackerSource = 8
    const val PegFromTrackerSource = 9
}

internal object ReferenceTrackerTargetVftblSlots {
    const val AddRefFromReferenceTracker = 3
    const val ReleaseFromReferenceTracker = 4
    const val Peg = 5
    const val Unpeg = 6
}

internal object ReferenceTrackerManagerVftblSlots {
    const val ReferenceTrackingStarted = 3
    const val FindTrackerTargetsCompleted = 4
    const val ReferenceTrackingCompleted = 5
    const val SetReferenceTrackerHost = 6
}

internal object ReferenceTrackerHostVftblSlots {
    const val DisconnectUnusedReferenceSources = 3
    const val ReleaseDisconnectedReferenceSources = 4
    const val NotifyEndOfReferenceTrackingOnThread = 5
    const val GetTrackerTarget = 6
    const val AddMemoryPressure = 7
    const val RemoveMemoryPressure = 8
}

internal object FindReferenceTargetsCallbackVftblSlots {
    const val FoundTrackerTarget = 3
}
