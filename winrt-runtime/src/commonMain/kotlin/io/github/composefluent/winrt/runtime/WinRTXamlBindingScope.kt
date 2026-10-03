package io.github.composefluent.winrt.runtime

/** Typed bodies are generated in the page; template instances own their connections and tracking. */
interface WinRTXamlBindingScopeOwner {
    fun _kotlinXamlUpdateScope(scope: WinRTXamlBindingScope, initial: Boolean)
    fun _kotlinXamlConnectScope(scope: WinRTXamlBindingScope, connectionId: Int, target: Any?)
    fun _kotlinXamlWriteBackScope(scope: WinRTXamlBindingScope, bindingId: Int)
    fun _kotlinXamlCreateScopeConnector(connectionId: Int, target: Any?): Any?
}

fun weakXamlBindingScopeWriteBack(scope: WinRTXamlBindingScope, bindingId: Int): (Any?, Any?) -> Unit {
    val reference = WeakReference(scope)
    return { _, _ -> reference.tryGetTarget()?.writeBack(bindingId) }
}

/** CSharpPagePass2 creates one binding object per DataTemplate/ControlTemplate instance. */
class WinRTXamlBindingScope(owner: WinRTXamlBindingScopeOwner, val scopeId: Int) {
    private val owner = WeakReference(owner)
    private val targets = mutableMapOf<Int, Any>()
    private val phases = mutableMapOf<Int, Int>()
    private var completedPhases = 0
    private var updatingPhases = -1
    val state: WinRTXamlBindingState = WinRTXamlBindingState()
    var dataRoot: Any? = null
        private set

    fun target(connectionId: Int): Any = requireNotNull(targets[connectionId]) {
        "XAML template scope $scopeId has not connected element $connectionId"
    }

    fun targetOrNull(connectionId: Int): Any? = targets[connectionId]

    fun disconnect(connectionId: Int) { targets.remove(connectionId) }

    fun registerPhase(connectionId: Int, phase: Int) { phases[connectionId] = phase }
    fun phaseOf(connectionId: Int): Int = phases[connectionId] ?: 0
    fun phaseTargets(phase: Int): List<Any> = phases.filterValues { it == phase }.keys.mapNotNull(targets::get)
    fun phasedTargets(): List<Any> = phases.filterValues { it != 0 }.keys.mapNotNull(targets::get)
    fun isPhaseActive(phase: Int): Boolean = (updatingPhases and (1 shl phase)) != 0

    fun connect(connectionId: Int, target: Any?) {
        requireNotNull(target) { "XAML template connection $connectionId has a null target" }
        if (FeatureSwitches.traceCcw) {
            println("winrt-xaml-binding: owner=${owner.tryGetTarget()?.let { it::class }} scope=$scopeId connection=$connectionId target=${target::class}")
        }
        targets[connectionId] = target
        owner.tryGetTarget()?._kotlinXamlConnectScope(this, connectionId, target)
        if (state.connected(connectionId)) state.update(::update)
    }

    fun createConnector(connectionId: Int, target: Any?): Any? =
        owner.tryGetTarget()?._kotlinXamlCreateScopeConnector(connectionId, target)

    private fun replaceDataRoot(data: Any?) {
        if (dataRoot !== data) {
            state.stopTracking()
            dataRoot = data
            completedPhases = 0
        }
    }

    fun initialize(data: Any?) {
        replaceDataRoot(data)
        if (data != null) {
            completedPhases = completedPhases or 1
            updatingPhases = if (phases.values.any { it != 0 }) 1 else -1
            try { state.initialize(::update) } finally { updatingPhases = completedPhases }
        }
    }

    /** Mirrors CSharpPagePass2.ProcessBindings and its phase bitmask. */
    fun processBindings(data: Any?, phase: Int): Int {
        require(phase in 0..31) { "Invalid XAML binding phase $phase" }
        replaceDataRoot(data)
        // CSharpPagePass2 updates every requested phase, including phase 0
        // when a container still holds the same item. Initialize's one-shot
        // guard must not suppress that refresh of OneTime bindings.
        if (dataRoot != null) {
            updatingPhases = 1 shl phase
            completedPhases = completedPhases or updatingPhases
            try { state.updatePhase(phase, ::update) } finally { updatingPhases = completedPhases }
        }
        return phases.values.filter { it > phase }.minOrNull() ?: -1
    }

    private fun update(initial: Boolean) {
        if (dataRoot != null) owner.tryGetTarget()?._kotlinXamlUpdateScope(this, initial)
    }

    fun changed(sender: Any?, args: Any?) = state.update(::update)
    fun writeBack(bindingId: Int) = state.changeTarget(
        { owner.tryGetTarget()?._kotlinXamlWriteBackScope(this, bindingId) }, ::update)

    fun recycle() {
        try {
            state.stopTracking()
        } finally {
            dataRoot = null
            completedPhases = 0
            updatingPhases = -1
        }
    }
}
