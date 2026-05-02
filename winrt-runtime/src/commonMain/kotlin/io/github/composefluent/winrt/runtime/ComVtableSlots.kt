package io.github.composefluent.winrt.runtime

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
