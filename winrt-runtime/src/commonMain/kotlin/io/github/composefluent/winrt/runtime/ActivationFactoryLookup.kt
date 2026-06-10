package io.github.composefluent.winrt.runtime

internal object RawActivationFactoryLookup {
    fun tryGet(
        runtimeClassName: String,
        interfaceId: Guid = IID.IActivationFactory,
    ): ActivationResult {
        val authoringFactory = ComWrappersSupport.tryGetAuthoringActivationFactory(runtimeClassName, interfaceId)
        if (authoringFactory.isSuccess || authoringFactory.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
            return authoringFactory
        }

        val authoringFallbackFactory = ComWrappersSupport.tryGetAuthoringActivationFactoryFallback(runtimeClassName, interfaceId)
        if (authoringFallbackFactory.isSuccess || authoringFallbackFactory.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
            return authoringFallbackFactory
        }

        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
        }

        WinRtModule.ensureInitialized()
        HString.create(runtimeClassName).use { classId ->
            val activationResult =
                WinRtPlatformApi.roGetActivationFactoryRaw(classId.handle, interfaceId).toActivationResult()
            if (activationResult.isSuccess || activationResult.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
                return activationResult
            }
            if (!FeatureSwitches.enableManifestFreeActivation) {
                return activationResult
            }
        }

        val hostManifestFactory = AuthoringHostManifestActivation.tryGet(runtimeClassName, interfaceId)
        if (hostManifestFactory.isSuccess || hostManifestFactory.hResult != KnownHResults.REGDB_E_CLASSNOTREG) {
            return hostManifestFactory
        }

        return ManifestFreeActivation.tryGet(runtimeClassName, interfaceId)
    }
}

internal object CachedActivationFactoryPointers {
    private data class CacheKey(
        val runtimeClassName: String,
        val interfaceId: Guid,
    )

    private val cache = ConcurrentCacheMap<CacheKey, RawAddress>()

    fun get(runtimeClassName: String, interfaceId: Guid): ActivationResult {
        val key = CacheKey(runtimeClassName, interfaceId)
        cache[key]?.let { cached ->
            WinRtPlatformApi.addRefRaw(cached)
            return ActivationResult(KnownHResults.S_OK, cached)
        }

        val created = RawActivationFactoryLookup.tryGet(runtimeClassName, interfaceId)
        if (!created.isSuccess) {
            return created
        }
        if (PlatformAbi.isNull(created.pointer)) {
            return ActivationResult(KnownHResults.E_POINTER, PlatformAbi.nullPointer)
        }

        val existing = cache.putIfAbsent(key, created.pointer)
        if (existing != null) {
            WinRtPlatformApi.releaseRaw(created.pointer)
            WinRtPlatformApi.addRefRaw(existing)
            return ActivationResult(KnownHResults.S_OK, existing)
        }

        WinRtPlatformApi.addRefRaw(created.pointer)
        return ActivationResult(KnownHResults.S_OK, created.pointer)
    }

    fun cachedFactoryCount(): Int = cache.size

    fun clearRuntimeCache() {
        cache.values.forEach { pointer ->
            if (!PlatformAbi.isNull(pointer)) {
                WinRtPlatformApi.releaseRaw(pointer)
            }
        }
        cache.clear()
    }
}
