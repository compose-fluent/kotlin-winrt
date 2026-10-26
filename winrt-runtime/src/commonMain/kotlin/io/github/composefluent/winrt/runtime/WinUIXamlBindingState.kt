package io.github.composefluent.winrt.runtime

/**
 * Per-instance tracking owned by generated compiled bindings. XamlCompiler's
 * CSharpPagePass2 Initialize/Update/StopTracking owns the same lifecycle;
 * CsWinRT EventSource owns the actual projected delegate and token mechanics.
 * The generated code supplies typed accessors and subscriptions, never paths.
 */
class WinUIXamlBindingState {
    // Event x:Bind connections outlive a tracking refresh and template recycling.
    // DisconnectUnloadedObject, rather than StopTracking, owns their removal.
    val eventConnections = WinUIXamlConnectionState()
    private var designSuppressed = emptySet<String>()
    private val designTargets = mutableMapOf<Int, Any>()
    fun registerDesignTarget(connectionId: Int, target: Any?) { if (target != null) designTargets[connectionId] = target }
    fun designTarget(connectionId: Int): Any? = designTargets[connectionId]
    fun stopDesignBindings() { stopTracking(); designTargets.clear() }
    fun configureDesignBindings(suppressed: String) {
        stopTracking()
        designSuppressed = suppressed.lineSequence().filter { it.isNotBlank() }.toSet()
    }
    fun addDesignSuppression(values: List<String>) { designSuppressed = designSuppressed + values }
    fun isDesignBindingEnabled(connectionId: Int, member: String): Boolean =
        designSuppressed.isEmpty() || "$connectionId:$member" !in designSuppressed
    private var active = false
    private var updating = false
    private data class Subscription(val phase: Int, val remove: () -> Unit)
    private val subscriptions = mutableListOf<Subscription>()
    private var trackingPhase = 0
    private val deferredAssignments = mutableMapOf<Pair<Int, String>, () -> Unit>()

    fun initialize(update: (Boolean) -> Unit) {
        if (active) return
        active = true
        refresh(true, update)
    }

    fun update(update: (Boolean) -> Unit) {
        if (active) refresh(false, update)
    }

    /** Explicit Bindings.Update refreshes OneTime bindings too, as in CSharpPagePass2. */
    fun updateAll(update: (Boolean) -> Unit) {
        active = true
        refresh(true, update)
    }

    /** A later template phase keeps listeners installed by earlier phases. */
    fun updatePhase(phase: Int, update: (Boolean) -> Unit) {
        active = true
        refresh(true, update, phase)
    }

    fun trackPhase(phase: Int) { trackingPhase = phase }

    /** XamlCompiler stores typed values until a deferred element is connected. */
    fun defer(connectionId: Int, member: String, assignment: () -> Unit) {
        if (active) deferredAssignments[connectionId to member] = assignment
    }

    fun connected(connectionId: Int): Boolean {
        val pending = deferredAssignments.filterKeys { it.first == connectionId }
        if (pending.isEmpty()) return false
        pending.keys.forEach(deferredAssignments::remove)
        // CSharpPagePass2.Connect applies deferred values before installing
        // TwoWay listeners. Reconnection is still a source-to-target update.
        val wasUpdating = updating
        updating = true
        try {
            pending.values.forEach { it() }
        } catch (error: Throwable) {
            failUpdate(error)
        } finally {
            updating = wasUpdating
        }
        return true
    }

    /** Target changes must not write back values while a source update is running. */
    fun changeTarget(change: () -> Unit, update: (Boolean) -> Unit) {
        if (!active || updating) return
        updating = true
        try { change() } catch (error: Throwable) { failUpdate(error) } finally { updating = false }
        refresh(false, update)
    }

    private fun refresh(initial: Boolean, update: (Boolean) -> Unit, phase: Int? = null) {
        if (updating) return
        updating = true
        try {
            disconnect(phase)
            update(initial)
        } catch (error: Throwable) {
            failUpdate(error)
        } finally {
            updating = false
        }
    }

    fun stopTracking() {
        active = false
        deferredAssignments.clear()
        disconnect()
    }

    private fun failUpdate(error: Throwable): Nothing {
        try { stopTracking() } catch (cleanupError: Throwable) {
            if (cleanupError !== error) error.addSuppressed(cleanupError)
        }
        throw error
    }

    private fun disconnect(phase: Int? = null) {
        val pending = subscriptions.filter { phase == null || it.phase == phase }
        subscriptions.removeAll(pending.toSet())
        var failure: Throwable? = null
        pending.asReversed().forEach { subscription ->
            try { subscription.remove() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun track(remove: () -> Unit) {
        if (active) subscriptions += Subscription(trackingPhase, remove) else remove()
    }

    fun <Source : Any, Property : Any> trackProperty(
        source: Source,
        property: Property,
        token: Long,
        remove: (Source, Property, Long) -> Unit,
    ) = track { remove(source, property, token) }

    fun <Source : Any, Handler : Any> trackEvent(
        source: Source,
        handler: Handler,
        remove: (Source, Handler) -> Unit,
    ) = track { remove(source, handler) }
}

/** Mirrors the weak binding receiver used by XamlCompiler's generated tracking callbacks. */
fun <Target : Any, A, B> weakXamlBindingCallback(
    target: Target,
    invoke: (Target, A, B) -> Unit,
): (A, B) -> Unit {
    val reference = WeakReference(target)
    return { first, second -> reference.tryGetTarget()?.let { invoke(it, first, second) } }
}
