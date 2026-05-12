package io.github.composefluent.winrt.projections.support

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.registerGeneratedProjectionTypeIndex

class GeneratedRegistrarRuntimeClass

class FallbackIndexedRuntimeClass

class GeneratedRegistrarInterfaceProjection(
    override val nativeObject: ComObjectReference,
) : IWinRTObject

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

object WinRTInterfaceProjectionRegistry {
    fun register() {
        ComWrappersSupport.registerInterfaceProjectionFactory(GENERATED_REGISTRAR_INTERFACE_TYPE_HANDLE) { instance ->
            GeneratedRegistrarInterfaceProjection(instance)
        }
    }
}

val GENERATED_REGISTRAR_INTERFACE_TYPE_HANDLE: WinRtTypeHandle =
    WinRtTypeHandle(
        projectedTypeName = "Contoso.IGeneratedRegistrarInterface",
        interfaceId = Guid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
    )
