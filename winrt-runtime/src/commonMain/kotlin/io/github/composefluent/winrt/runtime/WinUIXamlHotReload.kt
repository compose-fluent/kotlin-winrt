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
    private val stateAdapter: WinUIXamlStateAdapter? = null,
) {
    private class Root(owner: Any, val thread: Long, val enqueue: (() -> Unit) -> Boolean) {
        val owner = PlatformManagedWeakReference(owner)
        val elements = mutableMapOf<String, PlatformManagedWeakReference<Any>>()
        val namedComponents = mutableMapOf<String, Root>()
        val compiledBindings = mutableMapOf<String, Set<String>>()
        val eventRoutes = mutableMapOf<Pair<String, String>, PlatformManagedWeakReference<WinUIXamlEventRoute>>()
        var lifetime: PlatformManagedWeakReference<WinUIXamlConnectionState>? = null
        private var legacyBaseline: WinUIXamlCollectionBaseline? = null
        var baseline: WinUIXamlCollectionBaseline?
            get() = if (lifetime == null) legacyBaseline else lifetime?.get()?.hotReloadBaseline
            set(value) {
                if (lifetime == null) legacyBaseline = value else lifetime?.get()?.hotReloadBaseline = value
            }
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

    fun observe(owner: Any, className: String, path: String, hash: String, name: String?, target: Any?,
        lifetime: WinUIXamlConnectionState? = null, compiledBindings: Set<String>? = null) {
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
            if (name != null && target != null) {
                root.elements[name] = PlatformManagedWeakReference(target)
                if (compiledBindings != null) root.compiledBindings[name] = compiledBindings
            }
            if (name == null) {
                if (page.pending) page.restart = "A component was created during an update. Restart to restore a consistent page state."
                root.ready = true
            }
        }
        if (name != null && target != null) {
            // Connect records the compiler's namescope identity on its UI thread.
            // CsWinRT IUnknown identity compares projections of the same COM object;
            // no visual order or process-local instance number establishes a restart key.
            val candidates = lock.withLock { pages.values.flatMap { it.roots }.filter { it !== root && it.thread == root.thread } }
            val child = candidates.filter { candidate -> candidate.owner.get()?.let {
                it === target || inspector?.isSameObject(it, target) == true
            } == true }.singleOrNull()
            lock.withLock {
                if (child != null) root.namedComponents[name] = child else root.namedComponents.remove(name)
            }
        }
        if (name == null) {
            // LoadComponent can connect the parent's field before the child has
            // completed its own InitializeComponent. Resolve that earlier edge
            // when the child becomes ready, still on the owning UI dispatcher.
            val parents = lock.withLock {
                pages.values.flatMap { it.roots }.filter { it !== root && it.thread == root.thread }
                    .flatMap { parent -> parent.elements.mapNotNull { (name, reference) ->
                        reference.get()?.let { Triple(parent, name, it) }
                    } }
            }
            parents.forEach { (parent, element, value) ->
                if (value === owner || inspector?.isSameObject(value, owner) == true) lock.withLock {
                    if (parent.elements[element]?.get() === value) parent.namedComponents[element] = root
                }
            }
            if (lifetime != null) root.lifetime = PlatformManagedWeakReference(lifetime)
            val overrides = lock.withLock { page.overrides.values.toList() }
            val resources = lock.withLock { page.resourceOverrides.values.toList() }
            val history = lock.withLock { page.history.toList() }
            runCatching {
                root.baseline = captureBaseline(root)
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

    /** Called under lock. Only compiler-connected names below an unambiguous
     * component anchor survive process replacement. Incomplete or cyclic paths
     * have no key; user input must never move by an ordinal fallback. */
    private fun componentKeys(): Map<Root, String> {
        val live = pages.values.associateWith { page -> page.roots.filter { it.ready && it.owner.get() != null } }
        val roots = live.values.flatten().toSet()
        val anchors = live.flatMap { (page, instances) ->
            if (instances.size == 1) listOf(instances.single() to "${page.className.length}:${page.className}${page.path.length}:${page.path}") else emptyList()
        }.toMap()
        val parents = mutableMapOf<Root, MutableList<Pair<Root, String>>>()
        roots.forEach { parent -> parent.namedComponents.forEach { (name, child) ->
            if (child in roots) parents.getOrPut(child) { mutableListOf() }.add(parent to name)
        } }
        val keys = mutableMapOf<Root, String>()
        fun key(root: Root, visiting: Set<Root>): String {
            keys[root]?.let { return it }
            if (root in visiting || visiting.size >= 64) return ""
            val value = anchors[root] ?: parents[root]?.singleOrNull()?.let { (parent, name) ->
                key(parent, visiting + root).takeIf { it.isNotEmpty() }?.let { "$it/${name.length}:$name" }
            }.orEmpty()
            return value.takeIf { it.length <= 4096 }.orEmpty().also { keys[root] = it }
        }
        roots.forEach { key(it, emptySet()) }
        val collisions = keys.values.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return keys.mapValues { (_, key) -> key.takeUnless { it in collisions }.orEmpty() }
    }

    fun snapshot(): WinUIXamlHotReloadReply = lock.withLock {
        if (closed) return@withLock WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The development session is closed.")
        pages.values.forEach { page -> page.roots.removeAll { it.owner.get() == null } }
        val keys = componentKeys()
        val roots = pages.values.filter { it.roots.any { root -> root.ready } }.map { page ->
            WinUIXamlHotReloadRoot(page.className, page.path, page.hash, page.version,
                page.roots.filter { it.ready }.flatMap { it.elements.keys }.distinct().sorted(), page.roots.count { it.ready },
                page.roots.filter { it.ready }.map { keys[it].orEmpty() })
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

    fun close() = lock.withLock {
        closed = true
        pages.values.forEach { page -> page.roots.forEach { it.baseline = null } }
        pages.clear()
    }

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
            val enqueued = selection.enqueue {
                dispatchTiming?.let { println("XAML preview UI dispatched: " + it.elapsedNow().inWholeMilliseconds + "ms") }
                val owner = selection.owner.get()
                if (owner == null || lock.withLock { closed }) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The component has closed."))
                else if (platformCurrentThreadToken() != selection.thread) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, "Visual inspection must run on the owning UI thread."))
                else inspector.inspect(owner, request) { result -> complete(result.fold(
                    { view -> snapshot().copy(message = "${view.nodes.size} visual nodes", inspection = view) },
                    { error -> WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, error.message.orEmpty()) })) }
            }
            dispatchTiming?.let { println("XAML preview enqueue returned: " + it.elapsedNow().inWholeMilliseconds + "ms") }
            if (!enqueued) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The UI dispatcher rejected inspection."))
        } catch (error: Exception) { complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, error.message.orEmpty())) }
    }

    /** State is scoped to one compiled component instance and never enters the XAML
     * patch history. A later component must not inherit an earlier user's input. */
    fun transferState(request: WinUIXamlStateRequest, complete: (WinUIXamlHotReloadReply) -> Unit) {
        val adapter = stateAdapter
        val root = lock.withLock {
            val roots = pages[request.className to request.resourcePath]?.roots?.filter { it.ready && it.owner.get() != null }
            if (request.componentKey.isEmpty()) roots?.getOrNull(request.instance)
            else {
                val keys = componentKeys()
                roots?.singleOrNull { keys[it] == request.componentKey }
            }
        }
        if (root == null || adapter == null) {
            complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "No component state adapter. Rebuild with the current toolchain."))
            return
        }
        try {
            val accepted = root.enqueue {
                val result = runCatching {
                    check(!lock.withLock { closed } && root.owner.get() != null) { "The component has closed." }
                    check(platformCurrentThreadToken() == root.thread) { "State transfer requires the owning UI thread." }
                    val owner = requireNotNull(root.owner.get())
                    fun currentElements() = buildMap {
                        putAll(adapter.discover(owner))
                        putAll(lock.withLock { root.elements.toMap() }.mapNotNull { (name, reference) -> reference.get()?.let { name to it } }.toMap())
                        if (owner is WinUIXamlRestartParticipant) put("\$owner", owner)
                    }.toMutableMap().apply {
                        // A model shared by several controls is checkpointed once,
                        // using its first stable lexical identity rather than visual order.
                        val seen = mutableListOf<Any>()
                        toMap().entries.sortedBy { it.key }.forEach { (key, value) ->
                            if (value is WinUIXamlRestartParticipant && seen.none { it === value }) {
                                seen += value
                                this["\$participant/$key"] = value
                            }
                        }
                    }
                    val elements = currentElements()
                    fun bindings(name: String) = root.compiledBindings[name].orEmpty()
                    fun capture(name: String, value: Any) =
                        (if (name.startsWith("\$participant/")) captureWinUIXamlParticipant(value)
                        else adapter.capture(value, bindings(name)))?.copy(name = name)
                    fun restore(value: Any, saved: WinUIXamlSavedElement) {
                        if (saved.name.startsWith("\$participant/"))
                            (value as WinUIXamlRestartParticipant).restoreRestartState(saved.values.associate { it.name to it.value })
                        else adapter.restore(value, saved, bindings(saved.name))
                    }
                    if (request.restore == null) {
                        val state = elements.mapNotNull { (name, value) -> capture(name, value)?.takeIf { it.values.isNotEmpty() } }
                        validateWinUIXamlSavedState(state)
                        snapshot().copy(message = "Captured ${state.size} controls for restart", savedState = state)
                    } else {
                        validateWinUIXamlSavedState(request.restore)
                        val ordered = request.restore.sortedBy { saved -> when {
                            saved.kind == "Frame" -> 0
                            saved.kind.startsWith("Participant:") -> 1
                            saved.values.any { it.name == "Focus" && it.value != "Unfocused" } -> 3
                            else -> 2
                        } }
                        val attempted = mutableListOf<Triple<Any, WinUIXamlSavedElement, WinUIXamlSavedElement>>()
                        try {
                            var targets = elements
                            ordered.forEach { saved ->
                                val value = targets[saved.name] ?: return@forEach
                                val before = capture(saved.name, value) ?: return@forEach
                                if (before.kind != saved.kind) return@forEach
                                val item = Triple(value, before, saved)
                                attempted += item
                                restore(value, saved)
                                // Navigation and application checkpoints may create
                                // new pages/contexts; ordinary inputs reuse one traversal.
                                if (saved.kind == "Frame" || saved.kind.startsWith("Participant:")) targets = currentElements()
                            }
                        } catch (failure: Exception) {
                            attempted.asReversed().forEach { item ->
                                runCatching { restore(item.first, item.second) }.exceptionOrNull()?.let(failure::addSuppressed)
                            }
                            throw failure
                        }
                        snapshot().copy(message = "Restored ${attempted.size} controls; ${request.restore.size - attempted.size} missing or changed controls skipped")
                    }
                }
                complete(result.getOrElse { WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.REJECTED, it.message.orEmpty()) })
            }
            if (!accepted) complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, "The UI dispatcher rejected state transfer."))
        } catch (error: Exception) {
            complete(WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.UNAVAILABLE, error.message.orEmpty()))
        }
    }

    private fun format(value: Any?): String = (inspector?.formatValue?.invoke(value) ?: value?.toString().orEmpty()).take(4096)

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

    private fun captureBaseline(root: Root, previous: WinUIXamlCollectionBaseline? = null,
        objects: List<Any> = emptyList(), collections: List<Any> = emptyList()) = WinUIXamlCollectionBaseline.capture(buildList {
        root.owner.get()?.let { add(it) }
        root.elements.values.forEach { reference -> reference.get()?.let { add(it) } }
    }, previous, objects, collections, retainObjects = root.lifetime != null)

    private fun target(root: Root, address: WinUIXamlHotReloadTarget,
        replacements: Map<WinUIXamlHotReloadTarget, Any> = emptyMap()): Any {
        require(address.path.size <= 64) { "The object path exceeds its limit." }
        var value: Any = (if (address.element.isEmpty()) root.owner.get() else root.elements[address.element]?.get())
            ?: error("Element '${address.element}' is not connected in the current name scope.")
        var replaced = false
        address.path.forEachIndexed { index, step ->
            if (address.copy(path = address.path.take(index + 1)) in replacements) replaced = true
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
                    if (!replaced) root.baseline?.validate(list)
                        ?: error("The component collection baseline is unavailable; rebuild.")
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
            WinUIXamlHotReloadValue(address.element, member.name, format(member.getValue(target)), address.path)
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
        val changedObjects = mutableMapOf<Root, MutableList<Any>>()
        val changedCollections = mutableMapOf<Root, MutableList<Any>>()
        val prepared = roots.flatMap { root -> buildList {
            val replacements = mutableMapOf<WinUIXamlHotReloadTarget, Any>()
            children.forEach { update ->
                require(update.expectedSize in 0..4096 && update.items.size <= 512) { "The child collection exceeds its update limit." }
                @Suppress("UNCHECKED_CAST")
                val collection = target(root, update.target) as? MutableList<Any?>
                    ?: error("The child target must be a mutable projected list.")
                require(collection.size == update.expectedSize) { "The live child collection no longer matches XAML; rebuild or restart." }
                root.baseline?.validate(collection) ?: error("The component collection baseline is unavailable; rebuild.")
                changedCollections.getOrPut(root) { mutableListOf() }.add(collection)
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
                    changedObjects.getOrPut(root) { mutableListOf() } += consumer
                    add(prepareProperty(consumer, property(consumer, reference.property), next[reference.key], reference.target))
                }
            }
            changes.forEach { change ->
                val address = WinUIXamlHotReloadTarget(change.element, change.path)
                // Future components first load their compiled dictionary. A
                // later literal override must address the prepared replacement,
                // rather than mutate the old resource about to be detached.
                val target = target(root, address, replacements)
                changedObjects.getOrPut(root) { mutableListOf() } += target
                val member = property(target, change.property)
                if (change.reset) {
                    require(change.literal.isEmpty()) { "A property reset cannot carry a literal." }
                    val local = requireNotNull(WinUIAuthoredTypeMetadata.memberDefinition(target, change.property)?.localValue) {
                        "${change.property} has no compiled dependency-property reset accessor; rebuild."
                    }.invoke(target)
                    add(Prepared(local.clear, local.restore, {
                        WinUIXamlHotReloadValue(address.element, member.name, format(member.getValue(target)), address.path)
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
                format(member.getValue(instance)), request.target.path)
        }
        // Validate requested readbacks too; a late failure rolls back the entire update.
        roots.forEach { root -> reads.forEach { read(root, it, overlays.getValue(root)) } }
        val previousBaselines = roots.associateWith { it.baseline }
        val attempted = mutableListOf<Prepared>()
        try {
            prepared.forEach { attempted += it; it.commit() }
            if (prepared.isNotEmpty()) roots.forEach { root ->
                root.baseline = captureBaseline(root, root.baseline, changedObjects[root].orEmpty(), changedCollections[root].orEmpty())
            }
            return prepared.take(changes.size + resources.sumOf { 1 + it.references.size } + children.size + events.size).map { it.result() } +
                reads.map { read(roots.first(), it) }
        } catch (failure: Exception) {
            previousBaselines.forEach { (root, baseline) -> root.baseline = baseline }
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
    inspector: WinUIXamlVisualInspector? = null, stateAdapter: WinUIXamlStateAdapter? = null) {
    WinUIXamlHotReloadConfiguration.lock.withLock {
        if (WinUIXamlHotReloadConfiguration.initialized) return
        WinUIXamlHotReloadConfiguration.initialized = true
        val registry = WinUIXamlHotReloadRegistry(dispatcherFactory, sdkConvert, loadResources, loadElement, inspector, stateAdapter)
        platformStartWinUIXamlHotReload(registry)?.let { transport ->
            WinUIXamlHotReloadConfiguration.registry = registry
            WinUIXamlHotReloadConfiguration.transport = transport
            PlatformProcessHooks.registerShutdownHook { transport.close() }
        }
    }
}

fun registerWinUIXamlHotReloadElement(owner: Any, className: String, path: String, sourceHash: String, name: String, target: Any, compiledBindings: String = "") {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, name, target, compiledBindings = compiledBindings.split('\n').filter { it.isNotEmpty() }.toSet())
}

/** Compiler-generated SDK getters/setters supplement the existing member registry in development only. */
fun registerWinUIXamlHotReloadAccessors(definition: WinUIXamlTypeDefinition) {
    if (isWinUIXamlHotReloadEnabled()) WinUIAuthoredTypeMetadata.registerPropertyAccessors(definition)
}

fun isWinUIXamlHotReloadEnabled(): Boolean = WinUIXamlHotReloadConfiguration.lock.withLock {
    WinUIXamlHotReloadConfiguration.registry != null
}
fun completeWinUIXamlHotReloadComponent(owner: Any, className: String, path: String, sourceHash: String,
    lifetime: WinUIXamlConnectionState? = null) {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observe(owner, className, path, sourceHash, null, null, lifetime)
}

internal expect fun platformStartWinUIXamlHotReload(registry: WinUIXamlHotReloadRegistry): AutoCloseable?

/** Called only by compiler-generated event forwarding delegates in development. */
fun registerWinUIXamlHotReloadEvent(owner: Any, className: String, path: String, sourceHash: String,
    element: String, target: Any, event: String, route: WinUIXamlEventRoute) {
    WinUIXamlHotReloadConfiguration.lock.withLock { WinUIXamlHotReloadConfiguration.registry }
        ?.observeEvent(owner, className, path, sourceHash, element, target, event, route)
}
