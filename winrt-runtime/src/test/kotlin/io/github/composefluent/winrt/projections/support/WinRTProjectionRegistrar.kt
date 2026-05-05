package io.github.composefluent.winrt.projections.support

import io.github.composefluent.winrt.runtime.registerGeneratedProjectionTypeIndex

class GeneratedRegistrarRuntimeClass

object WinRTProjectionRegistrar {
    fun register() {
        registerGeneratedProjectionTypeIndex(
            kClass = GeneratedRegistrarRuntimeClass::class,
            projectedTypeName = "Contoso.GeneratedRegistrarRuntimeClass",
            kind = "RuntimeClass",
            baseTypeName = "Contoso.GeneratedRegistrarBase",
        )
    }
}
