package io.github.composefluent.winrt.runtime

internal object ManifestFreeActivation {
    fun tryGet(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return failure()
        }

        for (dllName in manifestFreeActivationCandidateDllNames(runtimeClassName)) {
            val module = DllModule.tryLoad(dllName) ?: continue
            val activationFactoryResult = module.getActivationFactory(runtimeClassName)
            if (!activationFactoryResult.isSuccess) {
                if (!isActivationClassUnavailable(activationFactoryResult.hResult)) {
                    return activationFactoryResult
                }
                continue
            }

            if (interfaceId == IID.IActivationFactory) {
                return activationFactoryResult
            }

            return try {
                WinRTPlatformApi.queryInterfaceRaw(activationFactoryResult.pointer, interfaceId).toActivationResult()
            } finally {
                WinRTPlatformApi.releaseRaw(activationFactoryResult.pointer)
            }
        }

        return failure()
    }

    internal fun candidateDllNames(runtimeClassName: String): List<String> {
        return manifestFreeActivationCandidateDllNames(runtimeClassName)
    }

    private fun failure(): ActivationResult =
        ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
}
