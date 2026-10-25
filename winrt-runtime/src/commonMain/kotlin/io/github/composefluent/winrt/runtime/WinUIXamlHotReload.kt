package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.ICustomProperty
import kotlin.reflect.KClass

/** CsWinRT's generated ICustomProperty accessors and DispatcherQueue.Post own the two boundaries.
 * No reflection, markup parsing or additional WinRT type classification belongs in this engine. */
internal class WinUIXamlHotReloadRegistry(
    private val dispatcherFactory: () -> ((() -> Unit) -> Boolean),
    private val convert: (KClass<*>, String) -> Any?,
    private val loadResources: (String) -> Map<Any?, Any?> = { error("Resource loading is not configured; rebuild.") },
    private val loadElement: (String) -> Any = { error("Element loading is not configured; rebuild.") },
    private val inspector: WinUIXamlVisualInspector? = null,
) {
    private class Root(owner: Any, val thread: Long, val enqueue: (() -> Unit) -> Boolean) {
        val owner = PlatformManagedWeakReference(owner)
        val elements = mutableMapOf<String, PlatformManagedWeakReference<Any>>()
        val eventRoutes = mutableMapOf<Pair<String, String>, PlatformManagedWeakReference<WinUIXamlEventRoute>>()
        var ready = false
    }
    private class Page(val className: String, val path: String, val compiledHash: String) {
        val roots = mutableListOf<Root>()
        var hash = compiledHash
        var version = 0L
        var pending = false
        var restart: String? = null
        val overrides = linkedMapOf<Pair<WinUIXamlHotReloadTarget, String>, WinUIXamlHotReloadChange>()
        val resourceOverrides = linkedMapOf<WinUIXamlHotReloadTarget, WinUIXamlHotReloadResources>()
        var graphActive = false
        val history = mutableListOf<WinUIXamlHotReloadPatch>()
    }
    private val lock = PlatformLock()
    private val pages = linkedMapOf<Pair<String, String>, Page>()
    private var closed = false
    private var tracedInspectionDispatch = false

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
            val resources = lock.withLock { page.resourceOverrides.values.toList() }
            val history = lock.withLock { page.history.toList() }
            runCatching {
                if (overrides.isNotEmpty() || resources.isNotEmpty()) apply(listOf(root), overrides, resources)
                history.forEach { apply(listOf(root), it.changes, it.resources, children = it.children, events = it.events) }
            }.onFailure {
                lock.withLock { page.restart = "A new component could not inherit the current XAML updates: ${it.message}" }
            }
        }
    }

    fun observeEvent(owner: Any, className: String, path: String, hash: String, element: String,
        target: Any, event: String, route: WinUIXamlEventRoute) {
        observe(owner, className, path, hash, element, target)
        lock.withLock {
            if (closed) return@withLock
            val page = pages.getValue(className to path)
            val root = page.roots.single { it.owner.get() === owner }
            if (root.ready) page.history.flatMap { it.events }.filter { it.element == element && it.event == event }.forEach {
                route.validate(it.expectedHandler, it.handler)
                route.select(it.handler)
            }
            root.eventRoutes[element to event] = PlatformManagedWeakReference(route)
        }
    }

    fun snapshot(): WinUIXamlHotReloadReply = lock.withLock {
        if (closed) return@withLock WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The development session is closed.")
        pages.values.forEach { page -> page.roots.removeAll { it.owner.get() == null } }
        val roots = pages.values.filter { it.roots.any { root -> root.ready } }.map { page ->
            WinUIXamlHotReloadRoot(page.className, page.path, page.hash, page.version,
                page.roots.filter { it.ready }.flatMap { it.elements.keys }.distinct().sorted(), page.roots.count { it.ready })
        }
        val error = pages.values.firstNotNullOfOrNull { it.restart }
        val pending = pages.values.any { it.pending }
        WinUIXamlHotReloadReply(when {
            error != null -> WinUIXamlHotReloadProtocol.RESTART_REQUIRED
            pending -> WinUIXamlHotReloadProtocol.UNAVAILABLE
            else -> WinUIXamlHotReloadProtocol.APPLIED
        }, error ?: if (pending) "A UI-thread update is pending. Wait before sending another version." else "${roots.size} loaded XAML classes", roots)
    }

    fun submit(patch: WinUIXamlHotReloadPatch, complete: (WinUIXamlHotReloadReply) -> Unit) {
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
                patch.events.size + patch.changes.size + patch.resources.sumOf { 1 + it.references.size } + patch.reads.size +
                    patch.children.sumOf { 1 + it.items.size } > 512 || patch.resources.size > 64 || patch.children.size > 64 -> "The update contains too many changes."
                (page.graphActive || patch.children.isNotEmpty() || patch.resources.isNotEmpty() || patch.events.isNotEmpty()) &&
                    (patch.changes.isNotEmpty() || patch.resources.isNotEmpty() || patch.children.isNotEmpty() || patch.events.isNotEmpty()) &&
                    (page.history.size + (if (!page.graphActive && (page.overrides.isNotEmpty() || page.resourceOverrides.isNotEmpty())) 1 else 0) >= 64 ||
                        page.history.sumOf(::storedSize) + storedSize(patch) +
                        (if (!page.graphActive) storedSize(patch.copy(changes = page.overrides.values.toList(),
                            resources = page.resourceOverrides.values.toList(), children = emptyList(), events = emptyList(), reads = emptyList())) else 0) > 8 * 1024 * 1024) ->
                    "The graph update history reached its limit. Rebuild and restart."
                !Regex("[0-9a-f]{64}").matches(patch.sourceHash) -> "Invalid source fingerprint."
                else -> null
            }
            if (error != null) Triple<Page?, Root?, String?>(null, null, error)
            else { page!!.pending = true; Triple(page, roots.first(), null) }
        }
        val page = selection.first
        val root = selection.second
        if (page == null || root == null) { complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.RESTART_REQUIRED, selection.third.orEmpty())); return }
        try {
            if (!root.enqueue {
                val result = runCatching {
                    check(!lock.withLock { closed }) { "The development session is closed." }
                    check(platformCurrentThreadToken() == root.thread) { "The dispatcher did not reach the component's UI thread." }
                    val roots = lock.withLock { page.roots.filter { it.ready && it.owner.get() != null } }
                    check(roots.isNotEmpty() && roots.all { it.thread == root.thread }) { "The component lifetime changed. Reconnect." }
                    val values = apply(roots, patch.changes, patch.resources, patch.reads, patch.children, patch.events)
                    lock.withLock {
                        check(page.restart == null) { page.restart.orEmpty() }
                        page.hash = patch.sourceHash; page.version = patch.version
                        if (!page.graphActive && (patch.children.isNotEmpty() || patch.resources.isNotEmpty() || patch.events.isNotEmpty())) {
                            if (page.overrides.isNotEmpty() || page.resourceOverrides.isNotEmpty())
                                page.history += patch.copy(changes = page.overrides.values.toList(),
                                    resources = page.resourceOverrides.values.toList(), reads = emptyList(), children = emptyList(), events = emptyList())
                            page.overrides.clear(); page.resourceOverrides.clear(); page.graphActive = true
                        }
                        if (page.graphActive) {
                            if (patch.changes.isNotEmpty() || patch.resources.isNotEmpty() || patch.children.isNotEmpty() || patch.events.isNotEmpty())
                                page.history += patch.copy(reads = emptyList())
                        } else {
                            patch.resources.forEach { update ->
                                page.overrides.keys.removeAll { (target, _) -> contains(update.target, target) }
                                page.resourceOverrides[update.target] = update
                            }
                            patch.changes.forEach { page.overrides[WinUIXamlHotReloadTarget(it.element, it.path) to it.property] = it }
                        }
                    }
                    values
                }
                lock.withLock { page.pending = false }
                complete(result.fold({ values -> snapshot().copy(message = "${patch.changes.size} properties, ${patch.resources.size} resource dictionaries and ${patch.children.size} child collections, ${patch.events.size} events updated", values = values) },
                    { error -> WinUIXamlHotReloadReply(if (lock.withLock { page.restart != null }) WinUIXamlHotReloadProtocol.RESTART_REQUIRED
                        else WinUIXamlHotReloadProtocol.REJECTED, error.message.orEmpty(), snapshot().roots) }))
            }) {
                lock.withLock { page.pending = false }
                complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The UI dispatcher rejected the update."))
            }
        } catch (error: Exception) {
            lock.withLock { page.pending = false }
            complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, error.message.orEmpty()))
        }
    }

    fun close() = lock.withLock { closed = true; pages.clear() }

    fun inspect(request: WinUIXamlInspectionRequest, complete: (WinUIXamlHotReloadReply) -> Unit) {
        if ((request.previewMarkup.isNotEmpty() || request.animation != null) && request.className != WinUIXamlHotReloadProtocol.PREVIEW_CLASS) {
            complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Static preview must use the isolated design host.")); return
        }
        val selection = lock.withLock {
            pages[request.className to request.resourcePath]?.roots?.filter { it.ready && it.owner.get() != null }?.getOrNull(request.instance)
        }
        if (selection == null || inspector == null) {
            complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "No live visual inspector. Rebuild with the current toolchain and select a loaded component.")); return
        }
        val dispatchTiming = lock.withLock {
            if (request.previewMarkup.isNotEmpty() && !tracedInspectionDispatch) {
                tracedInspectionDispatch = true
                kotlin.time.TimeSource.Monotonic.markNow()
            } else null
        }
        try {
            if (!selection.enqueue {
                dispatchTiming?.let { println("XAML preview UI dispatched: " + it.elapsedNow().inWholeMilliseconds + "ms") }
                val owner = selection.owner.get()
                if (owner == null || lock.withLock { closed }) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The component has closed."))
                else if (platformCurrentThreadToken() != selection.thread) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Visual inspection must run on the owning UI thread."))
                else inspector.inspect(owner, request) { result -> complete(result.fold(
                    { view -> snapshot().copy(message = "${view.nodes.size} visual nodes", inspection = view) },
                    { error -> WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, error.message.orEmpty()) })) }
            }) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The UI dispatcher rejected inspection."))
        } catch (error: Exception) { complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, error.message.orEmpty())) }
    }

    private class Prepared(val commit: () -> Unit, val rollback: () -> Unit, val result: () -> WinUIXamlHotReloadValue)

    private fun storedSize(patch: WinUIXamlHotReloadPatch) = 2 * (
        patch.events.sumOf { it.element.length + it.event.length + it.expectedHandler.length + it.handler.length } +
        patch.changes.sumOf { it.literal.length + it.element.length + it.property.length + it.path.toString().length } +
        patch.resources.sumOf { it.xaml.length + it.target.toString().length + it.references.toString().length + it.expectedKeys.toString().length } +
        patch.children.sumOf { it.target.toString().length + it.items.sumOf { item ->
            if (item is WinUIXamlHotReloadItem.Markup) item.xaml.length else 16
        } })

    private fun contains(parent: WinUIXamlHotReloadTarget, child: WinUIXamlHotReloadTarget) =
        parent.element == child.element && child.path.take(parent.path.size) == parent.path

    private fun target(root: Root, address: WinUIXamlHotReloadTarget,
        replacements: Map<WinUIXamlHotReloadTarget, Any> = emptyMap()): Any {
        require(address.path.size <= 64) { "The object path exceeds its limit." }
        var value: Any = (if (address.element.isEmpty()) root.owner.get() else root.elements[address.element]?.get())
            ?: error("Element '${address.element}' is not connected in the current name scope.")
        address.path.forEachIndexed { index, step ->
            value = replacements[address.copy(path = address.path.take(index + 1))] ?: requireNotNull(when (step) {
                is WinUIXamlHotReloadStep.Property -> {
                    val member = WinUIAuthoredTypeMetadata.customProperty(value, step.name)
                        ?: error("No compiled XAML accessor for path property '${step.name}'; rebuild.")
                    require(member.canRead) { "Path property '${step.name}' is not readable." }
                    member.getValue(value)
                }
                is WinUIXamlHotReloadStep.Key -> {
                    val map = value as? Map<*, *> ?: error("Path key '${step.name}' requires a projected dictionary.")
                    require(map.containsKey(step.name)) { "Resource '${step.name}' is missing from the live dictionary." }
                    map[step.name]
                }
                is WinUIXamlHotReloadStep.Index -> {
                    val list = value as? List<*> ?: error("Path index requires a projected collection.")
                    require(step.expectedSize in 0..4096 && list.size == step.expectedSize && step.index in list.indices) {
                        "The live collection no longer matches XAML; rebuild or restart."
                    }
                    list[step.index]
                }
            }) { "The live object path contains a null value; rebuild or restart." }
        }
        return value
    }

    private fun property(target: Any, name: String): ICustomProperty =
        requireNotNull(WinUIAuthoredTypeMetadata.customProperty(target, name)) {
            "No compiled XAML accessor for '$name'; rebuild."
        }.also { require(it.canRead && it.canWrite) { "$name has no readable/writable XAML accessor." } }

    private fun prepareProperty(target: Any, member: ICustomProperty, value: Any?, address: WinUIXamlHotReloadTarget): Prepared {
        val previous = member.getValue(target)
        val local = WinUIAuthoredTypeMetadata.memberDefinition(target, member.name)?.localValue?.invoke(target)
        return Prepared({ member.setValue(target, value) }, { if (local != null) local.restore() else member.setValue(target, previous) }, {
            WinUIXamlHotReloadValue(address.element, member.name, member.getValue(target)?.toString().orEmpty(), address.path)
        })
    }

    private fun apply(roots: List<Root>, changes: List<WinUIXamlHotReloadChange>,
        resources: List<WinUIXamlHotReloadResources> = emptyList(), reads: List<WinUIXamlHotReloadRead> = emptyList(),
        children: List<WinUIXamlHotReloadChildren> = emptyList(),
        events: List<WinUIXamlHotReloadEvent> = emptyList()): List<WinUIXamlHotReloadValue> {
        require(resources.map { it.target }.distinct().size == resources.size) { "A dictionary can be updated only once per transaction." }
        resources.forEach { update ->
            require(resources.none { it !== update && contains(update.target, it.target) }) { "Overlapping resource dictionaries require separate updates." }
        }
        require(children.map { it.target }.distinct().size == children.size && children.none { parent ->
            children.any { it !== parent && contains(parent.target, it.target) }
        }) { "Overlapping child collections require separate updates." }
        require(events.map { it.element to it.event }.distinct().size == events.size) { "An event can be changed only once per transaction." }
        // Prepare every getter and conversion before mutating any live object.
        val overlays = mutableMapOf<Root, Map<WinUIXamlHotReloadTarget, Any>>()
        val prepared = roots.flatMap { root -> buildList {
            val replacements = mutableMapOf<WinUIXamlHotReloadTarget, Any>()
            children.forEach { update ->
                require(update.expectedSize in 0..4096 && update.items.size <= 512) { "The child collection exceeds its update limit." }
                @Suppress("UNCHECKED_CAST")
                val collection = target(root, update.target) as? MutableList<Any?>
                    ?: error("The child target must be a mutable projected list.")
                require(collection.size == update.expectedSize) { "The live child collection no longer matches XAML; rebuild or restart." }
                val previous = collection.toList()
                val reused = update.items.filterIsInstance<WinUIXamlHotReloadItem.Existing>().map { it.index }
                require(reused.distinct().size == reused.size && reused.all { it in previous.indices }) {
                    "A child can be reused only once from its original collection."
                }
                val next = update.items.map { item -> when (item) {
                    is WinUIXamlHotReloadItem.Existing -> previous[item.index]
                    is WinUIXamlHotReloadItem.Markup -> {
                        require(item.xaml.length <= 128 * 1024) { "The new subtree exceeds its update limit." }
                        loadElement(item.xaml)
                    }
                } }
                replacements[update.target] = next
                add(Prepared({ replaceChildren(collection, next) }, { replaceChildren(collection, previous) }, {
                    WinUIXamlHotReloadValue(update.target.element, "Children", "${collection.size} children", update.target.path)
                }))
            }
            resources.forEach { update ->
                require(update.expectedKeys.size <= 4096 && update.references.size <= 512 && update.xaml.length <= 128 * 1024) {
                    "The resource dictionary exceeds the update limit."
                }
                @Suppress("UNCHECKED_CAST")
                val dictionary = target(root, update.target, replacements) as? MutableMap<Any?, Any?>
                    ?: error("The resource target is not a mutable projected dictionary.")
                val expected = update.expectedKeys.toSet()
                require(expected.size == update.expectedKeys.size && dictionary.keys.toSet() == expected) {
                    "The live dictionary's keys no longer match XAML; rebuild or restart."
                }
                val replacement = loadResources(update.xaml)
                require(replacement.size <= 4096 && replacement.keys.all { it is String }) {
                    "The replacement dictionary must contain explicit string keys."
                }
                val previous = expected.associateWith { dictionary[it] }
                val next = replacement.toMap()
                replacements[update.target] = next
                // CsWinRT IDictionaryMethods maps index assignment to IMap.Insert
                // and removal to IMap.Remove. Keep the dictionary identity while
                // replaying the exact key set, including rollback after a setter fails.
                fun replace(values: Map<out Any?, Any?>) {
                    dictionary.keys.toList().filterNot(values::containsKey).forEach(dictionary::remove)
                    values.forEach { (key, value) -> dictionary[key] = value }
                }
                add(Prepared({ replace(next) }, { replace(previous) }, {
                        WinUIXamlHotReloadValue(update.target.element, "Resources", next.keys.map { it as String }.sorted().joinToString(), update.target.path)
                    }))
                update.references.forEach { reference ->
                    require(next.containsKey(reference.key)) { "Resource '${reference.key}' is not part of this dictionary." }
                    val consumer = target(root, reference.target, replacements)
                    add(prepareProperty(consumer, property(consumer, reference.property), next[reference.key], reference.target))
                }
            }
            changes.forEach { change ->
                val address = WinUIXamlHotReloadTarget(change.element, change.path)
                // Future components first load their compiled dictionary. A
                // later literal override must address the prepared replacement,
                // rather than mutate the old resource about to be detached.
                val target = target(root, address, replacements)
                val member = property(target, change.property)
                if (change.reset) {
                    require(change.literal.isEmpty()) { "A property reset cannot carry a literal." }
                    val local = requireNotNull(WinUIAuthoredTypeMetadata.memberDefinition(target, change.property)?.localValue) {
                        "${change.property} has no compiled dependency-property reset accessor; rebuild."
                    }.invoke(target)
                    add(Prepared(local.clear, local.restore, {
                        WinUIXamlHotReloadValue(address.element, member.name, member.getValue(target)?.toString().orEmpty(), address.path)
                    }))
                } else {
                    val type = requireNotNull(member.type) { "${change.property} has no compiled value type." }
                    add(prepareProperty(target, member, convertWinUIXamlLiteral(type, change.literal, convert), address))
                }
            }
            events.forEach { update ->
                val route = root.eventRoutes[update.element to update.event]?.get()
                    ?: error("No compiled event route for '${update.element}.${update.event}'; rebuild.")
                route.validate(update.expectedHandler, update.handler)
                add(Prepared({ route.select(update.handler) }, { route.select(update.expectedHandler) }, {
                    WinUIXamlHotReloadValue(update.element, update.event, route.handlerName)
                }))
            }
            overlays[root] = replacements
        } }
        fun read(root: Root, request: WinUIXamlHotReloadRead, replacements: Map<WinUIXamlHotReloadTarget, Any> = emptyMap()): WinUIXamlHotReloadValue {
            val instance = target(root, request.target, replacements)
            val member = requireNotNull(WinUIAuthoredTypeMetadata.customProperty(instance, request.property)) {
                "No compiled XAML accessor for '${request.property}'; rebuild."
            }
            require(member.canRead) { "${request.property} is not readable." }
            return WinUIXamlHotReloadValue(request.target.element, request.property,
                member.getValue(instance)?.toString().orEmpty(), request.target.path)
        }
        // Validate requested readbacks too; a late failure rolls back the entire update.
        roots.forEach { root -> reads.forEach { read(root, it, overlays.getValue(root)) } }
        val attempted = mutableListOf<Prepared>()
        try {
            prepared.forEach { attempted += it; it.commit() }
            return prepared.take(changes.size + resources.sumOf { 1 + it.references.size } + children.size + events.size).map { it.result() } +
                reads.map { read(roots.first(), it) }
        } catch (failure: Exception) {
            val rollbackFailures = attempted.asReversed().mapNotNull { runCatching { it.rollback() }.exceptionOrNull() }
            if (rollbackFailures.isNotEmpty()) {
                lock.withLock { pages.values.filter { page -> page.roots.any { it in roots } }.forEach {
                    it.restart = "Property rollback failed. Restart the application: ${failure.message}"
                } }
                rollbackFailures.forEach(failure::addSuppressed)
            }
            throw failure
        }
    }

    /** CsWinRT IList maps IndexOf/Insert/RemoveAt to the projected vector. Keep
     * retained child instances; remove/insert only where the order differs. */
    private fun replaceChildren(collection: MutableList<Any?>, desired: List<Any?>) {
        // Projected IndexOf uses the vector's native identity comparison;
        // reading the same COM object need not yield the same Kotlin wrapper.
        val retained = desired.map(collection::indexOf).filter { it >= 0 }.toSet()
        for (index in collection.indices.reversed()) if (index !in retained) collection.removeAt(index)
        desired.forEachIndexed { index, item ->
            val previous = collection.indexOf(item)
            if (previous == index) return@forEachIndexed
            if (previous >= 0) collection.removeAt(previous)
            collection.add(index, item)
        }
        while (collection.size > desired.size) collection.removeAt(collection.lastIndex)
    }
}

private object WinUIXamlHotReloadConfiguration {
    val lock = PlatformLock()
    var initialized = false
    var registry: WinUIXamlHotReloadRegistry? = null
    var transport: AutoCloseable? = null
}

/** Generated application metadata supplies typed SDK conversion and an owning-thread dispatcher. */
fun configureWinUIXamlHotReload(dispatcherFactory: () -> ((() -> Unit) -> Boolean), sdkConvert: (KClass<*>, String) -> Any?,
    loadResources: (String) -> Map<Any?, Any?> = { error("Resource loading is not configured; rebuild.") },
    loadElement: (String) -> Any = { error("Element loading is not configured; rebuild.") },
    inspector: WinUIXamlVisualInspector? = null) {
    WinUIXamlHotReloadConfiguration.lock.withLock {
        if (WinUIXamlHotReloadConfiguration.initialized) return
        WinUIXamlHotReloadConfiguration.initialized = true
        val registry = WinUIXamlHotReloadRegistry(dispatcherFactory, sdkConvert, loadResources, loadElement, inspector)
        platformStartWinUIXamlHotReload(registry)?.let { transport ->
            WinUIXamlHotReloadConfiguration.registry = registry
            WinUIXamlHotReloadConfiguration.transport = transport
            PlatformProcessHooks.registerShutdownHook { transport.close() }
        }
    }
}

fun registerWinUIXamlHotReloadElement(owner: Any, className: String, path: String, sourceHash: String, name: String, target: Any) {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, name, target)
}

/** Compiler-generated SDK getters/setters supplement the existing member registry in development only. */
fun registerWinUIXamlHotReloadAccessors(definition: WinUIXamlTypeDefinition) {
    if (isWinUIXamlHotReloadEnabled()) WinUIAuthoredTypeMetadata.registerPropertyAccessors(definition)
}

fun isWinUIXamlHotReloadEnabled(): Boolean = WinUIXamlHotReloadConfiguration.lock.withLock {
    WinUIXamlHotReloadConfiguration.registry != null
}
fun completeWinUIXamlHotReloadComponent(owner: Any, className: String, path: String, sourceHash: String) {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, null, null)
}

internal expect fun platformStartWinUIXamlHotReload(registry: WinUIXamlHotReloadRegistry): AutoCloseable?

/** Called only by compiler-generated event forwarding delegates in development. */
fun registerWinUIXamlHotReloadEvent(owner: Any, className: String, path: String, sourceHash: String,
    element: String, target: Any, event: String, route: WinUIXamlEventRoute) {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observeEvent(owner, className, path, sourceHash, element, target, event, route)
}
