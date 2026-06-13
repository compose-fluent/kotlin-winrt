package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.RawAddress

interface WinRtAuthoringHostExports {
    fun registerActivationFactories()
    fun dllGetActivationFactory(activatableClassId: RawAddress, factoryOut: RawAddress): Int
}

object WinRtAuthoringHostExportRegistry {
    private var hostExportsByClassName: Map<String, WinRtAuthoringHostExports> = emptyMap()

    fun registerHostExports(
        hostExportsClass: String,
        exports: WinRtAuthoringHostExports,
    ) {
        require(hostExportsClass.isNotBlank()) { "Host exports class name must not be blank." }
        hostExportsByClassName = hostExportsByClassName + (hostExportsClass to exports)
    }

    fun hostExports(hostExportsClass: String): WinRtAuthoringHostExports? =
        hostExportsByClassName[hostExportsClass]

    fun clearRegisteredHostExportsForTests() {
        hostExportsByClassName = emptyMap()
    }
}
