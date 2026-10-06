package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.ICustomProperty
import kotlin.reflect.KClass

/** CsWinRT's generated ICustomProperty accessors and DispatcherQueue.Post own the two boundaries.
 * No reflection, markup parsing or additional WinRT type classification belongs in this engine. */
internal class WinRTXamlHotReloadRegistry(
    private val dispatcherFactory: () -> ((() -> Unit) -> Boolean),
    private val convert: (KClass<*>, String) -> Any?,
) {
    private class Root(owner: Any, val thread: Long, val enqueue: (() -> Unit) -> Boolean) {
        val owner = PlatformManagedWeakReference(owner)
        val elements = mutableMapOf<String, PlatformManagedWeakReference<Any>>()
        var ready = false
    }
    private class Page(val className: String, val path: String, val compiledHash: String) {
        val roots = mutableListOf<Root>()
        var hash = compiledHash
        var version = 0L
        var pending = false
        var restart: String? = null
        val overrides = linkedMapOf<Pair<String, String>, WinRTXamlHotReloadChange>()
    }
    private val lock = PlatformLock()
    private val pages = linkedMapOf<Pair<String, String>, Page>()
    private var closed = false

    fun observe(owner: Any, className: String, path: String, hash: String, name: String?, target: Any?) {
        if (lock.withLock { closed }) return
        val page = lock.withLock {
            pages.getOrPut(className to path) { Page(className, path, hash) }.also {
                if (it.compiledHash != hash) it.restart = "Loaded components have different compilation fingerprints."
            }
        }
        val root = lock.withLock {
            page.roots.removeAll { it.owner.get() == null }
            page.roots.firstOrNull { it.owner.get() === owner }
        } ?: Root(owner, platformCurrentThreadToken(), dispatcherFactory()).also { lock.withLock { page.roots += it } }
        lock.withLock {
            check(root.thread == platformCurrentThreadToken()) { "A XAML component must connect on its owning UI thread." }
            if (name != null && target != null) root.elements[name] = PlatformManagedWeakReference(target)
            if (name == null) {
                if (page.pending) page.restart = "A component was created during an update. Restart to restore a consistent page state."
                root.ready = true
            }
        }
        if (name == null) {
            val overrides = lock.withLock { page.overrides.values.toList() }
            if (overrides.isNotEmpty()) runCatching { apply(listOf(root), overrides) }.onFailure {
                lock.withLock { page.restart = "A new component could not inherit the current property updates: ${it.message}" }
            }
        }
    }

    fun snapshot(): WinRTXamlHotReloadReply = lock.withLock {
        if (closed) return@withLock WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.UNAVAILABLE, "The development session is closed.")
        pages.values.forEach { page -> page.roots.removeAll { it.owner.get() == null } }
        val roots = pages.values.filter { it.roots.any { root -> root.ready } }.map { page ->
            WinRTXamlHotReloadRoot(page.className, page.path, page.hash, page.version,
                page.roots.filter { it.ready }.flatMap { it.elements.keys }.distinct().sorted())
        }
        val error = pages.values.firstNotNullOfOrNull { it.restart }
        WinRTXamlHotReloadReply(if (error == null) WinRTXamlHotReloadProtocol.APPLIED else WinRTXamlHotReloadProtocol.RESTART_REQUIRED,
            error ?: "${roots.size} loaded XAML classes", roots)
    }

    fun submit(patch: WinRTXamlHotReloadPatch, complete: (WinRTXamlHotReloadReply) -> Unit) {
        val selection = lock.withLock {
            val page = pages[patch.className to patch.resourcePath]
            val roots = page?.roots?.filter { it.ready && it.owner.get() != null }.orEmpty()
            val error = when {
                page == null || roots.isEmpty() -> "This XAML class has no live component."
                page.restart != null -> page.restart
                page.compiledHash.isEmpty() -> "Rebuild with a compiler that records XAML source fingerprints."
                page.pending -> "A property update is already pending."
                page.hash != patch.expectedHash || patch.version != page.version + 1 -> "The source fingerprint or update version is stale. Reconnect."
                roots.map { it.thread }.distinct().size != 1 -> "Components on multiple UI threads require restarting."
                patch.changes.size > 512 -> "The update contains too many properties."
                !Regex("[0-9a-f]{64}").matches(patch.sourceHash) -> "Invalid source fingerprint."
                else -> null
            }
            if (error != null) Triple<Page?, Root?, String?>(null, null, error)
            else { page!!.pending = true; Triple(page, roots.first(), null) }
        }
        val page = selection.first
        val root = selection.second
        if (page == null || root == null) { complete(WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.RESTART_REQUIRED, selection.third.orEmpty())); return }
        try {
            if (!root.enqueue {
                val result = runCatching {
                    check(!lock.withLock { closed }) { "The development session is closed." }
                    check(platformCurrentThreadToken() == root.thread) { "The dispatcher did not reach the component's UI thread." }
                    val roots = lock.withLock { page.roots.filter { it.ready && it.owner.get() != null } }
                    check(roots.isNotEmpty() && roots.all { it.thread == root.thread }) { "The component lifetime changed. Reconnect." }
                    val values = apply(roots, patch.changes)
                    lock.withLock {
                        check(page.restart == null) { page.restart.orEmpty() }
                        page.hash = patch.sourceHash; page.version = patch.version
                        patch.changes.forEach { page.overrides[it.element to it.property] = it }
                    }
                    values
                }
                lock.withLock { page.pending = false }
                complete(result.fold({ values -> snapshot().copy(message = "${patch.changes.size} properties updated in place", values = values) },
                    { error -> WinRTXamlHotReloadReply(if (lock.withLock { page.restart != null }) WinRTXamlHotReloadProtocol.RESTART_REQUIRED
                        else WinRTXamlHotReloadProtocol.REJECTED, error.message.orEmpty(), snapshot().roots) }))
            }) {
                lock.withLock { page.pending = false }
                complete(WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.UNAVAILABLE, "The UI dispatcher rejected the update."))
            }
        } catch (error: Exception) {
            lock.withLock { page.pending = false }
            complete(WinRTXamlHotReloadReply(WinRTXamlHotReloadProtocol.UNAVAILABLE, error.message.orEmpty()))
        }
    }

    fun close() = lock.withLock { closed = true; pages.clear() }

    private class Prepared(val target: Any, val member: ICustomProperty, val previous: Any?, val value: Any?,
        val change: WinRTXamlHotReloadChange)

    private fun apply(roots: List<Root>, changes: List<WinRTXamlHotReloadChange>): List<WinRTXamlHotReloadValue> {
        // Prepare every getter and conversion before mutating any live object.
        val prepared = roots.flatMap { root -> changes.map { change ->
            val target = (if (change.element.isEmpty()) root.owner.get() else root.elements[change.element]?.get())
                ?: error("Element '${change.element}' is not connected in the current name scope.")
            val member = WinUiAuthoredTypeMetadata.customProperty(target, change.property)
                ?: error("No compiled XAML accessor for ${change.element}.${change.property}; rebuild.")
            require(member.canRead && member.canWrite) { "${change.property} has no readable/writable XAML accessor." }
            val type = requireNotNull(member.type) { "${change.property} has no compiled value type." }
            Prepared(target, member, member.getValue(target), convertWinRTXamlLiteral(type, change.literal, convert), change)
        } }
        val attempted = mutableListOf<Prepared>()
        try {
            prepared.forEach { attempted += it; it.member.setValue(it.target, it.value) }
            return prepared.take(changes.size).map { WinRTXamlHotReloadValue(it.change.element, it.change.property,
                it.member.getValue(it.target)?.toString().orEmpty()) }
        } catch (failure: Exception) {
            val rollbackFailures = attempted.asReversed().mapNotNull { runCatching { it.member.setValue(it.target, it.previous) }.exceptionOrNull() }
            if (rollbackFailures.isNotEmpty()) {
                lock.withLock { pages.values.filter { page -> page.roots.any { it in roots } }.forEach {
                    it.restart = "Property rollback failed. Restart the application: ${failure.message}"
                } }
                rollbackFailures.forEach(failure::addSuppressed)
            }
            throw failure
        }
    }
}

private object WinRTXamlHotReloadConfiguration {
    val lock = PlatformLock()
    var initialized = false
    var registry: WinRTXamlHotReloadRegistry? = null
    var transport: AutoCloseable? = null
}

/** Generated application metadata supplies typed SDK conversion and an owning-thread dispatcher. */
fun configureWinRTXamlHotReload(dispatcherFactory: () -> ((() -> Unit) -> Boolean), sdkConvert: (KClass<*>, String) -> Any?) {
    WinRTXamlHotReloadConfiguration.lock.withLock {
        if (WinRTXamlHotReloadConfiguration.initialized) return
        WinRTXamlHotReloadConfiguration.initialized = true
        val registry = WinRTXamlHotReloadRegistry(dispatcherFactory, sdkConvert)
        platformStartWinRTXamlHotReload(registry)?.let { transport ->
            WinRTXamlHotReloadConfiguration.registry = registry
            WinRTXamlHotReloadConfiguration.transport = transport
            PlatformProcessHooks.registerShutdownHook { transport.close() }
        }
    }
}

fun registerWinRTXamlHotReloadElement(owner: Any, className: String, path: String, sourceHash: String, name: String, target: Any) {
    WinRTXamlHotReloadConfiguration.lock.withLock { WinRTXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, name, target)
}
fun completeWinRTXamlHotReloadComponent(owner: Any, className: String, path: String, sourceHash: String) {
    WinRTXamlHotReloadConfiguration.lock.withLock { WinRTXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, null, null)
}

internal expect fun platformStartWinRTXamlHotReload(registry: WinRTXamlHotReloadRegistry): AutoCloseable?
