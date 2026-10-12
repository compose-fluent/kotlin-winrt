package io.github.composefluent.winrt.runtime

/** CSharpPagePass2 keeps the file binding connector separate from ordinary event connections. */
interface WinUiXamlPageBindingOwner {
    fun _kotlinXamlConnectBindings(connectionId: Int, target: Any?)
}

/** Managed lifecycle only; this interface is not a WinRT interface and has no CCW entry. */
interface WinUiXamlComponent {
    /** Override and call super first before accessing connected XAML elements. */
    fun initializeComponent() { _kotlinXamlInitialize() }

    /** Compiler-owned implementation of the guarded markup load. */
    fun _kotlinXamlInitialize()

    /** Compiler-owned once-only dispatch of the complete construction lifecycle. */
    fun _kotlinXamlCompleteConstruction()
}

/** Called by constructor-call lowering after the complete Kotlin constructor returns. */
fun <T : WinUiXamlComponent> initializeWinUiXamlComponent(instance: T): T {
    if (FeatureSwitches.traceCcw) println("winrt-xaml: initialize ${instance::class.qualifiedName}")
    instance._kotlinXamlCompleteConstruction()
    if (FeatureSwitches.traceCcw) println("winrt-xaml: initialized ${instance::class.qualifiedName}")
    return instance
}
