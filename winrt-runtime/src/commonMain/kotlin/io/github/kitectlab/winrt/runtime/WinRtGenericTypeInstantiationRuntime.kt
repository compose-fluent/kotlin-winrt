package io.github.kitectlab.winrt.runtime

enum class WinRtGenericTypeInstantiationBindingKind {
    RcwHelpers,
    VtableFunctions,
    PropertyAccessors,
    DelegateCcwInvoke,
}

data class WinRtGenericTypeInstantiationBindingEntry(
    val className: String,
    val sourceType: String,
    val isDelegate: Boolean,
)

data class WinRtGenericTypeInstantiationBinding(
    val entry: WinRtGenericTypeInstantiationBindingEntry,
    val kind: WinRtGenericTypeInstantiationBindingKind,
    val functions: List<String> = emptyList(),
)

object WinRtGenericTypeInstantiationRuntime {
    private val bindings = ConcurrentCacheMap<String, WinRtGenericTypeInstantiationBinding>()

    fun bindRcwHelpers(
        className: String,
        sourceType: String,
        isDelegate: Boolean,
        functions: List<String>,
    ) {
        register(className, sourceType, isDelegate, WinRtGenericTypeInstantiationBindingKind.RcwHelpers, functions)
    }

    fun bindVtableFunctions(
        className: String,
        sourceType: String,
        isDelegate: Boolean,
        functions: List<String>,
    ) {
        register(className, sourceType, isDelegate, WinRtGenericTypeInstantiationBindingKind.VtableFunctions, functions)
    }

    fun bindPropertyAccessors(
        className: String,
        sourceType: String,
        isDelegate: Boolean,
        functions: List<String>,
    ) {
        register(className, sourceType, isDelegate, WinRtGenericTypeInstantiationBindingKind.PropertyAccessors, functions)
    }

    fun bindDelegateCcwInvoke(
        className: String,
        sourceType: String,
        isDelegate: Boolean,
    ) {
        register(className, sourceType, isDelegate, WinRtGenericTypeInstantiationBindingKind.DelegateCcwInvoke, emptyList())
    }

    fun bindingFor(
        className: String,
        kind: WinRtGenericTypeInstantiationBindingKind,
    ): WinRtGenericTypeInstantiationBinding? =
        bindings[bindingKey(className, kind)]

    fun bindingsForClass(className: String): List<WinRtGenericTypeInstantiationBinding> =
        bindings.values
            .filter { binding -> binding.entry.className == className }
            .sortedBy { binding -> binding.kind.ordinal }

    internal fun clearForTests() {
        bindings.clear()
    }

    private fun register(
        className: String,
        sourceType: String,
        isDelegate: Boolean,
        kind: WinRtGenericTypeInstantiationBindingKind,
        functions: List<String>,
    ) {
        bindings[bindingKey(className, kind)] = WinRtGenericTypeInstantiationBinding(
            entry = WinRtGenericTypeInstantiationBindingEntry(
                className = className,
                sourceType = sourceType,
                isDelegate = isDelegate,
            ),
            kind = kind,
            functions = functions.distinct(),
        )
    }

    private fun bindingKey(
        className: String,
        kind: WinRtGenericTypeInstantiationBindingKind,
    ): String = "$className:${kind.name}"
}
