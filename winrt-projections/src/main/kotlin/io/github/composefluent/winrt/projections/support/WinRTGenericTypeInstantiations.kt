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

internal data class GenericTypeInstantiationRuntimeBinding(
    val initRcwHelpers: (GenericTypeInstantiationEntry, List<String>) -> Unit = { _, _ -> },
    val initVtableFunctions: (GenericTypeInstantiationEntry, List<String>) -> Unit = { _, _ -> },
    val initPropertyAccessors: (GenericTypeInstantiationEntry, List<String>) -> Unit = { _, _ -> },
    val initDelegateCcwInvoke: (GenericTypeInstantiationEntry) -> Unit = {},
    val initGenericReturnOnlyRcwHelpers: (GenericTypeInstantiationEntry, List<String>) -> Unit = { _, _ -> },
    val initProjectedGenericFallbacks: (GenericTypeInstantiationEntry, List<String>) -> Unit = { _, _ -> },
)

internal object WinRTGenericTypeInstantiations {
    private val INITIALIZED_CLASS_NAMES: MutableSet<String> = linkedSetOf()

    private var runtimeBinding: GenericTypeInstantiationRuntimeBinding = GenericTypeInstantiationRuntimeBinding(
        initRcwHelpers = { entry, functions ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindRcwHelpers(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
                functions = functions,
            )
        },
        initVtableFunctions = { entry, functions ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindVtableFunctions(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
                functions = functions,
            )
        },
        initPropertyAccessors = { entry, accessors ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindPropertyAccessors(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
                functions = accessors,
            )
        },
        initDelegateCcwInvoke = { entry ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindDelegateCcwInvoke(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
            )
        },
        initGenericReturnOnlyRcwHelpers = { entry, functions ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindGenericReturnOnlyRcwHelpers(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
                functions = functions,
            )
        },
        initProjectedGenericFallbacks = { entry, functions ->
            io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindProjectedGenericFallbacks(
                className = entry.className,
                sourceType = entry.sourceType,
                isDelegate = entry.isDelegate,
                functions = functions,
            )
        },
    )

    fun installRuntimeBinding(binding: GenericTypeInstantiationRuntimeBinding) {
        runtimeBinding = binding
    }

    fun isInitialized(entry: GenericTypeInstantiationEntry): Boolean =
        entry.className in INITIALIZED_CLASS_NAMES

    fun initializeAll() {
        WinRtGenericTypeInstantiationSupportIntrinsic.initializeAll()
    }

    fun initializeBySourceType(sourceType: String) {
        WinRtGenericTypeInstantiationSupportIntrinsic.initializeBySourceType(sourceType)
    }

    fun initializeEntry(entry: GenericTypeInstantiationEntry) {
        entry.dependencies.forEach(::initializeBySourceType)
        registerGenericInstantiation(entry)
    }

    fun initializeDependencies(
        entry: GenericTypeInstantiationEntry,
        initialize: (GenericTypeInstantiationEntry) -> Unit,
    ) {
        entry.dependencies.forEach(::initializeBySourceType)
    }

    fun initializeDependencies(entry: GenericTypeInstantiationEntry) {
        entry.dependencies.forEach(::initializeBySourceType)
    }

    private fun registerGenericInstantiation(entry: GenericTypeInstantiationEntry) {
        if (!INITIALIZED_CLASS_NAMES.add(entry.className)) return
        if (entry.rcwFunctions.isNotEmpty() || !entry.isDelegate) {
            runtimeBinding.initRcwHelpers(entry, entry.rcwFunctions)
        }
        if (entry.vtableFunctions.isNotEmpty()) {
            runtimeBinding.initVtableFunctions(entry, entry.vtableFunctions)
        }
        if (entry.propertyAccessors.isNotEmpty()) {
            runtimeBinding.initPropertyAccessors(entry, entry.propertyAccessors)
        }
        if (entry.genericReturnOnlyRcwFunctions.isNotEmpty()) {
            runtimeBinding.initGenericReturnOnlyRcwHelpers(entry, entry.genericReturnOnlyRcwFunctions)
        }
        if (entry.projectedGenericFallbacks.isNotEmpty()) {
            runtimeBinding.initProjectedGenericFallbacks(entry, entry.projectedGenericFallbacks)
        }
        if (entry.isDelegate) {
            runtimeBinding.initDelegateCcwInvoke(entry)
        }
    }
}
