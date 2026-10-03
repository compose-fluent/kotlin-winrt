package io.github.composefluent.winrt.runtime

/** Managed lifecycle only; this interface is not a WinRT interface and has no CCW entry. */
interface WinRTXamlComponent {
    /** Override and call super first before accessing connected XAML elements. */
    fun initializeComponent() { _kotlinXamlInitialize() }

    /** Compiler-owned implementation of the guarded markup load. */
    fun _kotlinXamlInitialize()

    /** Compiler-owned once-only dispatch of the complete construction lifecycle. */
    fun _kotlinXamlCompleteConstruction()
}

/** Called by constructor-call lowering after the complete Kotlin constructor returns. */
fun <T : WinRTXamlComponent> initializeWinRTXamlComponent(instance: T): T {
    if (FeatureSwitches.traceCcw) println("winrt-xaml: initialize ${instance::class.qualifiedName}")
    instance._kotlinXamlCompleteConstruction()
    if (FeatureSwitches.traceCcw) println("winrt-xaml: initialized ${instance::class.qualifiedName}")
    return instance
}
