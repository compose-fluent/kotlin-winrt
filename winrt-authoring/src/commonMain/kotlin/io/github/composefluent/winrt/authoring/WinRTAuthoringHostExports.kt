package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ConcurrentCacheMap
import io.github.composefluent.winrt.runtime.RawAddress

interface WinRTAuthoringHostExports {
    fun registerActivationFactories()
    fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int
}

object WinRTAuthoringHostExportRegistry {
    private val hostExportsByClassName = ConcurrentCacheMap<String, WinRTAuthoringHostExports>()

    fun registerHostExports(
        hostExportsClass: String,
        exports: WinRTAuthoringHostExports,
    ) {
        require(hostExportsClass.isNotBlank()) { "Host exports class name must not be blank." }
        hostExportsByClassName[hostExportsClass] = exports
    }

    fun hostExports(hostExportsClass: String): WinRTAuthoringHostExports? =
        hostExportsByClassName[hostExportsClass]

    fun clearRegisteredHostExportsForTests() {
        hostExportsByClassName.clear()
    }
}
