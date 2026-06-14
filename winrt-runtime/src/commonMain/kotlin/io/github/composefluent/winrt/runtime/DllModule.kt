package io.github.composefluent.winrt.runtime

internal class DllModule private constructor(
    val fileName: String,
    private val moduleHandle: RawAddress,
    private val getActivationFactoryPointer: RawAddress,
) {
    fun getActivationFactory(runtimeClassName: String): ActivationResult {
        HString.create(runtimeClassName).use { classId ->
            return WinRtPlatformApi.dllGetActivationFactoryRaw(
                getActivationFactoryPointer,
                classId.handle,
            ).toActivationResult()
        }
    }

    companion object {
        private val cache = ConcurrentCacheMap<String, DllModule>()

        fun tryLoad(fileName: String): DllModule? {
            if (!PlatformRuntime.isWindows) {
                return null
            }

            cache[fileName]?.let { return it }

            val created = create(fileName) ?: return null
            val existing = cache.putIfAbsent(fileName, created)
            return existing ?: created
        }

        private fun create(fileName: String): DllModule? {
            val moduleHandle = WinRtPlatformApi.tryLoadLibraryExWRaw(
                WinRtPlatformApi.resolveModulePathRaw(fileName),
                loadLibrarySearchDllLoadDir or loadLibrarySearchDefaultDirs,
            )
            if (PlatformAbi.isNull(moduleHandle)) {
                return null
            }

            val getActivationFactoryPointer =
                WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, dllGetActivationFactory)
            if (PlatformAbi.isNull(getActivationFactoryPointer)) {
                WinRtPlatformApi.freeLibraryRaw(moduleHandle)
                return null
            }

            return DllModule(fileName, moduleHandle, getActivationFactoryPointer)
        }

        internal fun cachedModuleCount(): Int = cache.size

        internal fun clearCacheForTests() {
            cache.clear()
        }

        internal const val loadLibrarySearchDefaultDirs = 0x00001000
        internal const val loadLibrarySearchDllLoadDir = 0x00000100
        private const val dllGetActivationFactory = "DllGetActivationFactory"
    }
}
