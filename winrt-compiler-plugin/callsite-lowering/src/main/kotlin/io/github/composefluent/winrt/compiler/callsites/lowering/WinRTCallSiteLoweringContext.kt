package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/** Owned by one generate invocation; IR symbols and caches never escape a compilation. */
internal class WinRTCallSiteLoweringContext(
    module: IrModuleFragment,
    pluginContext: IrPluginContext,
) {
    val directBackend by lazy {
        requireNotNull(WinRTDirectCallBackend.create(pluginContext, module.files.firstOrNull()))
    }
    val projectedTypes by lazy { WinRTProjectedTypeCanonicalizer(pluginContext) }
    val planner by lazy { WinRTProjectionCallSitePlanner(module, pluginContext, projectedTypes) }
    val recipeResolution by lazy {
        runCatching { WinRTCallSiteRecipeLowering.create(pluginContext, module) { directBackend } }
    }
}
