package io.github.composefluent.winrt.runtime

/**
 * Per-instance tracking owned by generated compiled bindings. XamlCompiler's
 * CSharpPagePass2 Initialize/Update/StopTracking owns the same lifecycle;
 * CsWinRT EventSource owns the actual projected delegate and token mechanics.
 * The generated code supplies typed accessors and subscriptions, never paths.
 */
class WinRTXamlBindingState {
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
        pending.keys.forEach(deferredAssignments::remove)
        pending.values.forEach { it() }
        return pending.isNotEmpty()
    }

    /** Target changes must not write back values while a source update is running. */
    fun changeTarget(change: () -> Unit, update: (Boolean) -> Unit) {
        if (!active || updating) return
        updating = true
        try { change() } finally { updating = false }
        refresh(false, update)
    }

    private fun refresh(initial: Boolean, update: (Boolean) -> Unit, phase: Int? = null) {
        if (updating) return
        updating = true
        try {
            disconnect(phase)
            update(initial)
        } catch (error: Throwable) {
            active = false
            deferredAssignments.clear()
            try { disconnect() } catch (cleanupError: Throwable) { error.addSuppressed(cleanupError) }
            throw error
        } finally {
            updating = false
        }
    }

    fun stopTracking() {
        active = false
        deferredAssignments.clear()
        disconnect()
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
