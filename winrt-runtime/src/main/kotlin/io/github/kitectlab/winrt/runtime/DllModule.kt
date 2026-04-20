package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal class DllModule private constructor(
    val fileName: String,
    private val moduleHandle: MemorySegment,
    private val getActivationFactoryPointer: MemorySegment,
) {
    fun getActivationFactory(runtimeClassName: String): ActivationResult {
        HString.create(runtimeClassName).use { classId ->
            val factoryOut = java.lang.foreign.Arena.ofConfined().use { arena ->
                val out = arena.allocate(ValueLayout.ADDRESS)
                val getActivationFactory = Linker.nativeLinker().downcallHandle(
                    getActivationFactoryPointer,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                )
                val hr = getActivationFactory.invokeWithArguments(
                    classId.handle.asMemorySegment(),
                    out,
                ) as Int
                return ActivationResult(HResult(hr), out.get(ValueLayout.ADDRESS, 0))
            }
            return factoryOut
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
            val moduleHandle = WindowsRuntimePlatform.tryLoadLibraryExW(
                WindowsRuntimePlatform.resolveModulePath(fileName),
                loadWithAlteredSearchPath,
            )
            if (moduleHandle == MemorySegment.NULL) {
                return null
            }

            val getActivationFactoryPointer =
                WindowsRuntimePlatform.tryGetProcAddress(moduleHandle, dllGetActivationFactory)
            if (getActivationFactoryPointer == MemorySegment.NULL) {
                WindowsRuntimePlatform.freeLibrary(moduleHandle)
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
