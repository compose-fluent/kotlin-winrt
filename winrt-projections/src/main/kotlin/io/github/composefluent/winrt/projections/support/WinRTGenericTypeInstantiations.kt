package io.github.composefluent.winrt.projections.support

import io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationSupportIntrinsic

// Deterministic generator handoff for .cswinrt WinRTGenericTypeInstantiations writer parity.

internal data class GenericTypeInstantiationEntry(
    val className: String,
    val sourceType: String,
    val isDelegate: Boolean,
    val rcwFunctions: List<String>,
    val vtableFunctions: List<String>,
    val propertyAccessors: List<String>,
    val genericReturnOnlyRcwFunctions: List<String>,
    val projectedGenericFallbacks: List<String>,
    val dependencies: List<String>,
)

internal object WinRTGenericTypeInstantiations {
    fun initializeAll() {
        WinRtGenericTypeInstantiationSupportIntrinsic.initializeAll()
    }

    fun initializeBySourceType(sourceType: String) {
        WinRtGenericTypeInstantiationSupportIntrinsic.initializeBySourceType(sourceType)
    }

    fun initializeEntry(entry: GenericTypeInstantiationEntry) {
        entry.dependencies.forEach(::initializeBySourceType)
    }
}
