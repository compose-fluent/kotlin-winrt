package io.github.kitectlab.winrt.runtime

internal class DllModule private constructor(
    val fileName: String,
    private val moduleHandle: NativePointer,
    private val getActivationFactoryPointer: NativePointer,
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
                loadWithAlteredSearchPath,
            )
            if (NativeInterop.isNull(moduleHandle)) {
                return null
            }

            val getActivationFactoryPointer =
                WinRtPlatformApi.tryGetProcAddressRaw(moduleHandle, dllGetActivationFactory)
            if (NativeInterop.isNull(getActivationFactoryPointer)) {
                WinRtPlatformApi.freeLibraryRaw(moduleHandle)
                return null
            }

            return DllModule(fileName, moduleHandle, getActivationFactoryPointer)
        }

        internal fun cachedModuleCount(): Int = cache.size

        internal fun clearCacheForTests() {
            cache.clear()
        }

        private const val loadWithAlteredSearchPath = 0x00000008
        private const val dllGetActivationFactory = "DllGetActivationFactory"
    }
}
