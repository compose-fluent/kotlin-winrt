package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteCatalog
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteDeclaration
import io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic

internal object KotlinRuntimeOwnedProjectionCallSites {
    val catalog: WinRTProjectionCallSiteCatalog by lazy(LazyThreadSafetyMode.PUBLICATION) {
        WinRTProjectionCallSiteCatalog.fromJvmClass(WinRTProjectionIntrinsic::class.java)
    }

    fun declarationFor(
        call: KotlinModulePlatformAbiCallSupport.ModulePlatformAbiCall,
    ): WinRTProjectionCallSiteDeclaration? =
        call.canonicalCallSiteDescriptorOrNull()?.let(catalog::get)
}
