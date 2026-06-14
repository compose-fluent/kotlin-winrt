package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ConcurrentCacheMap
import io.github.composefluent.winrt.runtime.RawAddress

interface WinRtAuthoringHostExports {
    fun registerActivationFactories()
    fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int
}

object WinRtAuthoringHostExportRegistry {
    private val hostExportsByClassName = ConcurrentCacheMap<String, WinRtAuthoringHostExports>()

    fun registerHostExports(
        hostExportsClass: String,
        exports: WinRtAuthoringHostExports,
    ) {
        require(hostExportsClass.isNotBlank()) { "Host exports class name must not be blank." }
        hostExportsByClassName[hostExportsClass] = exports
    }

    fun hostExports(hostExportsClass: String): WinRtAuthoringHostExports? =
        hostExportsByClassName[hostExportsClass]

    fun clearRegisteredHostExportsForTests() {
        hostExportsByClassName.clear()
    }
}
