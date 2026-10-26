package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUIXamlHotReloadTest {
    private class Element(var text: String = "original", var size: Int = 1, var style: Element? = null, var rejectStyle: Boolean = false)
    private class Caption(val inlines: MutableList<Element> = mutableListOf(Element("caption")))
    private class Page(val resources: MutableMap<Any?, Any?>, val children: MutableList<Element> = mutableListOf())
    private val hash = "a".repeat(64)
    private val next = "b".repeat(64)
    private fun registry(queue: MutableList<() -> Unit>, element: (String) -> Any = { Element(it) },
        load: (String) -> Map<Any?, Any?> = { emptyMap() }): WinUIXamlHotReloadRegistry {
        ComWrappersSupport.clearRegistriesForTests()
        // The source-generated ICustomProperty strategy is owned by .cswinrt's net5 projection;
        // these accessors stand in for CSharpTypeInfoPass2's generated property delegates.
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Element::class, "probe.Element", "System.Object", isWinRTComponent = false,
            members = listOf(
                WinUIXamlMemberDefinition("Text", "String", String::class, { (it as Element).text }, { target, value -> (target as Element).text = value as String }),
                WinUIXamlMemberDefinition("Size", "Int32", Int::class, { (it as Element).size }, { target, value ->
                    require((value as Int) != 13) { "setter failed" }; (target as Element).size = value
                }),
                WinUIXamlMemberDefinition("Style", "probe.Element", Element::class, { (it as Element).style }, { target, value ->
                    val element = target as Element
                    element.style = value as Element
                    if (element.rejectStyle && value.text == "reloaded style") error("style setter failed after mutation")
                }),
            )))
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Page::class, "probe.Page", "System.Object", isWinRTComponent = false, contentProperty = "Children",
            members = listOf(
                WinUIXamlMemberDefinition("Resources", "Map", MutableMap::class, { (it as Page).resources }),
                WinUIXamlMemberDefinition("Children", "List", MutableList::class, { (it as Page).children }),
            )))
        return WinUIXamlHotReloadRegistry({ { task -> queue += task; true } }, { type, text -> if (type == Int::class) text.toInt() else text }, load, element)
    }
    private fun patch(vararg changes: WinUIXamlHotReloadChange) = WinUIXamlHotReloadPatch("probe.Page", "Page.xaml", hash, next, 1, changes.toList())

    @Test fun compiled_event_routes_validate_atomically_and_replay_for_new_instances() {
        // CsWinRT EventSource keeps a stable native invoker while its managed handler
        // state changes. The compiler emits the typed forwarding body, not the IDE.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
        val route = WinUIXamlEventRoute("First", arrayOf("First", "Second"))
        registry.observeEvent(owner, "probe.Page", "Page.xaml", hash, "Greeting", element, "Click", route)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        fun change(name: String) = patch(WinUIXamlHotReloadChange("Greeting", "Text", "updated"))
            .copy(events = listOf(WinUIXamlHotReloadEvent("Greeting", "Click", "First", name)))
        val valid = change("Second")
        assertEquals(valid, WinUIXamlHotReloadCodec.readRequest(WinUIXamlHotReloadCodec.writeRequest("token", valid)).second)
        var reply: WinUIXamlHotReloadReply? = null
        registry.submit(change("Uncompiled")) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertEquals("First", route.handlerName); assertEquals("original", element.text)
        registry.submit(valid) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply?.status)
        assertEquals("Second", route.handlerName); assertEquals("updated", element.text)
        val laterOwner = Any(); val laterElement = Element()
        val later = WinUIXamlEventRoute("First", arrayOf("First", "Second"))
        registry.observeEvent(laterOwner, "probe.Page", "Page.xaml", hash, "Greeting", laterElement, "Click", later)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals("Second", later.handlerName); assertEquals("updated", laterElement.text)
        val disable = valid.copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2, changes = emptyList(),
            events = listOf(WinUIXamlHotReloadEvent("Greeting", "Click", "Second", "")))
        registry.submit(disable) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply?.status)
        assertEquals("", route.handlerName); assertEquals("", later.handlerName)
        val reconnected = WinUIXamlEventRoute("First", arrayOf("First", "Second"))
        registry.observeEvent(owner, "probe.Page", "Page.xaml", hash, "Greeting", Element(), "Click", reconnected)
        assertEquals("", reconnected.handlerName)
    }

    @Test fun event_route_is_restored_when_post_commit_readback_fails() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
        val route = WinUIXamlEventRoute("First", arrayOf("First", "Second"))
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Element::class,
            "probe.Element", "System.Object", isWinRTComponent = false, members = listOf(
                WinUIXamlMemberDefinition("Readback", "String", String::class, {
                    check(route.handlerName == "First") { "readback failed" }; "ready"
                }))))
        registry.observeEvent(owner, "probe.Page", "Page.xaml", hash, "Greeting", element, "Click", route)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        var reply: WinUIXamlHotReloadReply? = null
        registry.submit(patch().copy(events = listOf(WinUIXamlHotReloadEvent("Greeting", "Click", "First", "Second")),
            reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("Greeting"), "Readback")))) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertEquals("First", route.handlerName)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
    }

    @Test fun dependency_property_reset_preserves_local_precedence_on_failure_and_replays() {
        // XAMLC supplies typed DP delegates. ClearValue, not assigning a default,
        // restores SDK styling; the rollback captures ReadLocalValue beforehand.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        WinUIAuthoredTypeMetadata.clearForTests()
        var local = true
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Element::class,
            "probe.Element", "System.Object", isWinRTComponent = false, members = listOf(
                WinUIXamlMemberDefinition("Text", "String", String::class, { (it as Element).text },
                    { target, value -> (target as Element).text = value as String; local = true },
                    isDependencyProperty = true, localValue = { target ->
                        val element = target as Element; val previous = element.text; val wasLocal = local
                        WinUIXamlLocalValue({ element.text = "styled"; local = false },
                            { element.text = previous; local = wasLocal })
                    }),
                WinUIXamlMemberDefinition("Size", "Int32", Int::class, { (it as Element).size },
                    { target, value -> require(value != 13); (target as Element).size = value as Int }))))
        val owner = Any(); val element = Element()
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Greeting", element)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val reset = WinUIXamlHotReloadChange("Greeting", "Text", "", reset = true)
        val encoded = WinUIXamlHotReloadCodec.writeRequest("token", patch(reset))
        assertEquals(patch(reset), WinUIXamlHotReloadCodec.readRequest(encoded).second)
        var reply: WinUIXamlHotReloadReply? = null
        registry.submit(patch(reset, WinUIXamlHotReloadChange("Greeting", "Size", "13"))) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertEquals("original", element.text); assertTrue(local)
        registry.submit(patch(reset)) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply?.status)
        assertEquals("styled", element.text); assertFalse(local)
        val laterOwner = Any(); val later = Element()
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, "Greeting", later)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals("styled", later.text)
    }

    @Test fun attached_accessors_keep_target_identity_rollback_and_replay() {
        // CSharpTypeInfoPass2 attached member delegates invoke the declaring Get/Set pair.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Any::class,
            "probe.Layout", "System.Object", isWinRTComponent = false, members = listOf(
                WinUIXamlMemberDefinition("Row", "Int32", Int::class, { (it as Element).size },
                    { target, value -> require(value != 13); (target as Element).size = value as Int },
                    isAttachable = true, targetType = Element::class, targetTypeName = "probe.Element"))))
        val owner = Any(); val element = Element()
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Greeting", element)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        var result: WinUIXamlHotReloadReply? = null
        registry.submit(patch(WinUIXamlHotReloadChange("Greeting", "Text", "rollback"),
            WinUIXamlHotReloadChange("Greeting", "probe.Layout.Row", "13"))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, result?.status)
        assertEquals("original", element.text); assertEquals(1, element.size)
        registry.submit(patch(WinUIXamlHotReloadChange("Greeting", "probe.Layout.Row", "2"))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, result?.status)
        assertEquals(2, element.size)
        assertEquals("probe.Layout.Row", result?.values?.single()?.property)
        val laterOwner = Any(); val later = Element()
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, "Greeting", later)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals(2, later.size)
        assertNull(WinUIAuthoredTypeMetadata.customProperty(element, "probe.Unknown.Row"))
    }

    @Test fun updates_wait_for_the_ui_dispatcher_and_keep_identity_and_version() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
        assertEquals(PlatformAbi.nullPointer, WinUIAuthoredTypeMetadata.tryCreate("probe.Element") { PlatformAbi.nullPointer })
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Greeting", element)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        var result: WinUIXamlHotReloadReply? = null
        val update = patch(WinUIXamlHotReloadChange("Greeting", "Text", "changed"), WinUIXamlHotReloadChange("Greeting", "Size", "8"))
        registry.submit(update) { result = it }
        assertEquals("original", element.text); assertNull(result)
        assertEquals(WinUIXamlHotReloadProtocol.UNAVAILABLE, registry.snapshot().status)
        queue.removeAt(0).invoke()
        assertEquals("changed", element.text); assertEquals(8, element.size)
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, result?.status)
        assertEquals(next, registry.snapshot().roots.single().sourceHash)
        registry.submit(update) { result = it }
        assertEquals(WinUIXamlHotReloadProtocol.RESTART_REQUIRED, result?.status)
        assertTrue(queue.isEmpty())
        val later = Element(); val newOwner = Any()
        registry.observe(newOwner, "probe.Page", "Page.xaml", hash, "Greeting", later)
        registry.observe(newOwner, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals("changed", later.text); assertEquals(8, later.size)
    }

    @Test fun conversion_and_setter_failures_preserve_previous_values_and_fingerprint() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Greeting", element)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        for (size in listOf("invalid", "13")) {
            var result: WinUIXamlHotReloadReply? = null
            registry.submit(patch(WinUIXamlHotReloadChange("Greeting", "Text", "must roll back"), WinUIXamlHotReloadChange("Greeting", "Size", size))) { result = it }
            queue.removeAt(0).invoke()
            assertEquals(WinUIXamlHotReloadProtocol.REJECTED, result?.status)
            assertEquals("original", element.text); assertEquals(1, element.size)
            assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        }
    }

    @Test fun projected_paths_keep_shared_resource_identity_and_guard_live_collection_shape() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val brush = Element(); val owner = Page(mutableMapOf("Accent" to brush), mutableListOf(brush))
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val path = listOf(WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Key("Accent"))
        var result: WinUIXamlHotReloadReply? = null
        registry.submit(patch(WinUIXamlHotReloadChange("", "Text", "blue", path))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, result?.status)
        assertSame(brush, owner.resources["Accent"]); assertSame(brush, owner.children.single())
        assertEquals("blue", brush.text); assertEquals(path, result?.values?.single()?.path)
        val current = registry.snapshot().roots.single()
        registry.submit(WinUIXamlHotReloadPatch("probe.Page", "Page.xaml", current.sourceHash, "c".repeat(64), 2,
            listOf(WinUIXamlHotReloadChange("", "Text", "must not apply", listOf(
                WinUIXamlHotReloadStep.Property("Children"), WinUIXamlHotReloadStep.Index(0, 2)))))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, result?.status)
        assertEquals("blue", brush.text); assertEquals(current, registry.snapshot().roots.single())
    }

    @Test fun same_size_application_reorder_and_replacement_cannot_retarget_indexed_edits() {
        // CsWinRT IWeakReference + IUnknown identity preserves the compiled target,
        // even when the projected vector still has the expected Count.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val first = Element("first"); val second = Element("second")
        val owner = Page(mutableMapOf(), mutableListOf(first, second))
        val lifetime = WinUIXamlConnectionState()
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null, lifetime)
        assertNotNull(lifetime.hotReloadBaseline)
        val collection = WinUIXamlHotReloadTarget(path = listOf(WinUIXamlHotReloadStep.Property("Children")))
        val edit = patch(WinUIXamlHotReloadChange("", "Text", "changed",
            collection.path + WinUIXamlHotReloadStep.Index(0, 2)))
        fun send(update: WinUIXamlHotReloadPatch): WinUIXamlHotReloadReply {
            var reply: WinUIXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            return requireNotNull(reply)
        }
        owner.children.reverse()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(edit).status)
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(patch().copy(children = listOf(
            WinUIXamlHotReloadChildren(collection, 2, listOf(WinUIXamlHotReloadItem.Existing(0)))))).status)
        assertEquals("first", first.text); assertEquals("second", second.text)
        owner.children.reverse()
        val replacement = Element("first")
        owner.children[0] = replacement
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(edit).status)
        assertEquals("first", replacement.text)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        owner.children[0] = first
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, send(edit).status)
        assertEquals("changed", first.text); assertEquals("second", second.text)
        registry.close()
        assertNull(lifetime.hotReloadBaseline)
    }

    @Test fun non_content_vectors_reload_and_reject_application_replacements() {
        // Runtime2 collection projection + generated custom-property accessors:
        // Grid.RowDefinitions is a vector property, not Grid's content property.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        class Layout(val rows: MutableList<Element>)
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Layout::class,
            "probe.Layout", "System.Object", isWinRTComponent = false, members = listOf(
                WinUIXamlMemberDefinition("RowDefinitions", "probe.Rows", MutableList::class,
                    { (it as Layout).rows }, isCollection = true),
                WinUIXamlMemberDefinition("Unavailable", "probe.Rows", MutableList::class,
                    { error("template not realized") }, isCollection = true),
                WinUIXamlMemberDefinition("Unrelated", "Object", Any::class,
                    { error("unrelated getter must not run") }))))
        val row = Element("row"); val owner = Layout(mutableListOf(row))
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val path = listOf(WinUIXamlHotReloadStep.Property("RowDefinitions"), WinUIXamlHotReloadStep.Index(0, 1))
        fun send(update: WinUIXamlHotReloadPatch): WinUIXamlHotReloadReply {
            var result: WinUIXamlHotReloadReply? = null
            registry.submit(update) { result = it }; queue.removeAt(0).invoke()
            return requireNotNull(result)
        }
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED,
            send(patch(WinUIXamlHotReloadChange("", "Size", "2", path))).status)
        assertEquals(2, row.size)
        owner.rows[0] = Element("replacement")
        val edit = patch(WinUIXamlHotReloadChange("", "Size", "3", path))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2)
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(edit).status)
        assertEquals(1, owner.rows[0].size)
        owner.rows[0] = row
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, send(edit).status)
        assertEquals(3, row.size)
    }

    @Test fun property_side_effects_refresh_only_the_changed_owner_collection_baseline() {
        // TextBlock.Text can replace Inlines through the SDK setter. Refresh that
        // owner without accepting an unrelated application-owned child reorder.
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        WinUIAuthoredTypeMetadata.registerPropertyAccessors(WinUIXamlTypeDefinition(Caption::class,
            "probe.Caption", "System.Object", isWinRTComponent = false, contentProperty = "Inlines", members = listOf(
                WinUIXamlMemberDefinition("Text", "String", String::class, { (it as Caption).inlines.single().text },
                    { target, value -> (target as Caption).inlines.apply { clear(); add(Element(value as String)) } }),
                WinUIXamlMemberDefinition("Inlines", "List", MutableList::class, { (it as Caption).inlines }))))
        val first = Element("first"); val second = Element("second")
        val owner = Page(mutableMapOf(), mutableListOf(first, second)); val caption = Caption()
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Caption", caption)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        owner.children.reverse()
        fun send(update: WinUIXamlHotReloadPatch): WinUIXamlHotReloadReply {
            var reply: WinUIXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            return requireNotNull(reply)
        }
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED,
            send(patch(WinUIXamlHotReloadChange("Caption", "Text", "new text"))).status)
        val nextPatch = patch(WinUIXamlHotReloadChange("Caption", "Text", "inline changed",
            listOf(WinUIXamlHotReloadStep.Property("Inlines"), WinUIXamlHotReloadStep.Index(0, 1))))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2)
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(nextPatch.copy(changes = listOf(
            WinUIXamlHotReloadChange("", "Text", "wrong target", listOf(
                WinUIXamlHotReloadStep.Property("Children"), WinUIXamlHotReloadStep.Index(0, 2)))))).status)
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, send(nextPatch).status)
        assertEquals("inline changed", caption.inlines.single().text)
        assertEquals("first", first.text); assertEquals("second", second.text)
    }

    /** Style.IsSealed requires constructing a replacement; the native parser is
     * supplied by generated SDK glue, as in CsWinRT's XamlReader resource tests. */
    @Test fun resource_replacement_refreshes_consumers_and_rolls_back_failed_setters_and_future_instances() {
        val queue = mutableListOf<() -> Unit>()
        val registry = registry(queue) { mapOf("TitleStyle" to Element("reloaded style")) }
        val original = Element("sealed style")
        val owner = Page(mutableMapOf("TitleStyle" to original))
        val first = Element(style = original); val second = Element(style = original, rejectStyle = true)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "First", first)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Second", second)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val update = WinUIXamlHotReloadResources(WinUIXamlHotReloadTarget(path = listOf(WinUIXamlHotReloadStep.Property("Resources"))),
            "SDK dictionary input", listOf("TitleStyle"), listOf(
                WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("First"), "Style", "TitleStyle"),
                WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("Second"), "Style", "TitleStyle")))
        val patch = patch().copy(resources = listOf(update))
        var reply: WinUIXamlHotReloadReply? = null
        registry.submit(patch) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertSame(original, owner.resources["TitleStyle"]); assertSame(original, first.style); assertSame(original, second.style)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        second.rejectStyle = false
        val stylePath = listOf(WinUIXamlHotReloadStep.Property("Style"))
        registry.submit(patch.copy(reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("First", stylePath), "Text")))) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply?.status)
        assertEquals("reloaded style", first.style?.text)
        assertSame(owner.resources["TitleStyle"], first.style); assertSame(first.style, second.style)
        assertEquals("reloaded style", reply?.values?.single { it.path == stylePath && it.property == "Text" }?.value)
        registry.submit(patch(WinUIXamlHotReloadChange("", "Text", "latest resource", listOf(
            WinUIXamlHotReloadStep.Property("Resources"), WinUIXamlHotReloadStep.Key("TitleStyle"))))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2)) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, reply?.status)
        val laterOriginal = Element("sealed style")
        val laterOwner = Page(mutableMapOf("TitleStyle" to laterOriginal))
        val laterFirst = Element(style = laterOriginal); val laterSecond = Element(style = laterOriginal)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, "First", laterFirst)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, "Second", laterSecond)
        registry.observe(laterOwner, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals("latest resource", laterFirst.style?.text)
        assertSame(laterOwner.resources["TitleStyle"], laterFirst.style); assertSame(laterFirst.style, laterSecond.style)
        assertNotSame(first.style, laterFirst.style)
    }

    @Test fun dictionary_key_changes_rollback_and_replay_in_order_for_new_pages() {
        // CsWinRT InteropServices/Collections/IDictionaryMethods owns Insert/Remove;
        // the development registry owns transactional updates and instance replay.
        val queue = mutableListOf<() -> Unit>()
        val registry = registry(queue) { markup ->
            if (markup == "first") mapOf("Keep" to Element("reloaded style"), "New" to Element("new"))
            else mapOf("Final" to Element("final"))
        }
        val original = Element("original")
        val owner = Page(mutableMapOf("Keep" to original, "Removed" to Element()))
        val consumer = Element(style = original, rejectStyle = true)
        fun connect(page: Page, element: Element) {
            registry.observe(page, "probe.Page", "Page.xaml", hash, "Consumer", element)
            registry.observe(page, "probe.Page", "Page.xaml", hash, null, null)
        }
        connect(owner, consumer)
        val target = WinUIXamlHotReloadTarget(path = listOf(WinUIXamlHotReloadStep.Property("Resources")))
        val update = WinUIXamlHotReloadResources(target, "first", listOf("Keep", "Removed"), listOf(
            WinUIXamlHotReloadResourceReference(WinUIXamlHotReloadTarget("Consumer"), "Style", "Keep")))
        fun send(value: WinUIXamlHotReloadPatch): WinUIXamlHotReloadReply {
            var result: WinUIXamlHotReloadReply? = null
            registry.submit(value) { result = it }; queue.removeAt(0).invoke()
            return requireNotNull(result)
        }
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, send(patch().copy(resources = listOf(update))).status)
        assertEquals(setOf<Any?>("Keep", "Removed"), owner.resources.keys)
        assertSame(original, owner.resources["Keep"])
        consumer.rejectStyle = false
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, send(patch().copy(resources = listOf(update))).status)
        assertEquals(setOf<Any?>("Keep", "New"), owner.resources.keys)
        val second = update.copy(xaml = "second", expectedKeys = listOf("Keep", "New"),
            references = update.references.map { it.copy(key = "Final") })
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, send(patch().copy(expectedHash = next,
            sourceHash = "c".repeat(64), version = 2, resources = listOf(second))).status)
        val later = Page(mutableMapOf("Keep" to Element(), "Removed" to Element()))
        val laterConsumer = Element()
        connect(later, laterConsumer)
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, registry.snapshot().status)
        assertEquals(setOf<Any?>("Final"), later.resources.keys)
        assertEquals("final", laterConsumer.style?.text)
        assertSame(later.resources["Final"], laterConsumer.style)
    }

    /** IList.net5.cs owns IndexOf/Insert/RemoveAt. The loader is supplied by the
     * SDK, and existing indices describe retained instances, never new controls. */
    @Test fun child_updates_keep_objects_and_replay_ordered_history_for_later_components() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val first = Element("first"); val second = Element("second")
        val owner = Page(mutableMapOf(), mutableListOf(first, second))
        fun connect(page: Page) {
            registry.observe(page, "probe.Page", "Page.xaml", hash, "First", page.children[0])
            registry.observe(page, "probe.Page", "Page.xaml", hash, null, null)
        }
        connect(owner)
        fun send(update: WinUIXamlHotReloadPatch): WinUIXamlHotReloadReply {
            var reply: WinUIXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            return requireNotNull(reply).also { assertEquals(WinUIXamlHotReloadProtocol.APPLIED, it.status, it.message) }
        }
        send(patch(WinUIXamlHotReloadChange("First", "Text", "before graph")))
        val collection = WinUIXamlHotReloadTarget(path = listOf(WinUIXamlHotReloadStep.Property("Children")))
        val added = collection.copy(path = collection.path + WinUIXamlHotReloadStep.Index(1, 3))
        val updated = send(patch(WinUIXamlHotReloadChange("", "Text", "new child edited", added.path))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2,
                children = listOf(WinUIXamlHotReloadChildren(collection, 2, listOf(
                    WinUIXamlHotReloadItem.Existing(1), WinUIXamlHotReloadItem.Markup("new child"), WinUIXamlHotReloadItem.Existing(0)))),
                reads = listOf(WinUIXamlHotReloadRead(added, "Text"))))
        assertSame(second, owner.children[0]); assertSame(first, owner.children[2])
        assertEquals("new child edited", updated.values.last().value)
        send(patch().copy(expectedHash = "c".repeat(64), sourceHash = "d".repeat(64), version = 3,
            children = listOf(WinUIXamlHotReloadChildren(collection, 3, listOf(
                WinUIXamlHotReloadItem.Existing(2), WinUIXamlHotReloadItem.Existing(1))))))
        send(patch(WinUIXamlHotReloadChange("First", "Text", "after graph"))
            .copy(expectedHash = "d".repeat(64), sourceHash = "e".repeat(64), version = 4))
        assertSame(first, owner.children[0]); assertEquals("after graph", first.text)
        val laterFirst = Element("first"); val later = Page(mutableMapOf(), mutableListOf(laterFirst, Element("second")))
        connect(later)
        assertEquals(WinUIXamlHotReloadProtocol.APPLIED, registry.snapshot().status)
        assertSame(laterFirst, later.children[0]); assertEquals("after graph", laterFirst.text)
        assertEquals("new child edited", later.children[1].text); assertNotSame(owner.children[1], later.children[1])
    }

    @Test fun child_updates_prepare_every_root_and_roll_back_order_when_a_setter_fails() {
        var loads = 0
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue, element = {
            require(it != "bad markup" && (it != "second-root-failure" || ++loads != 2)); Element(it)
        })
        val first = Element("first"); val second = Element("second")
        val owner = Page(mutableMapOf(), mutableListOf(first, second))
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "First", first)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val otherFirst = Element("other first"); val otherSecond = Element("other second")
        val other = Page(mutableMapOf(), mutableListOf(otherFirst, otherSecond))
        registry.observe(other, "probe.Page", "Page.xaml", hash, "First", otherFirst)
        registry.observe(other, "probe.Page", "Page.xaml", hash, null, null)
        val collection = WinUIXamlHotReloadTarget(path = listOf(WinUIXamlHotReloadStep.Property("Children")))
        val children = WinUIXamlHotReloadChildren(collection, 2, listOf(
            WinUIXamlHotReloadItem.Existing(1), WinUIXamlHotReloadItem.Markup("added"), WinUIXamlHotReloadItem.Existing(0)))
        val candidates = listOf(
            patch(WinUIXamlHotReloadChange("First", "Size", "13")).copy(children = listOf(children)),
            patch().copy(children = listOf(children.copy(expectedSize = 3))),
            patch().copy(children = listOf(children.copy(items = listOf(WinUIXamlHotReloadItem.Existing(0), WinUIXamlHotReloadItem.Existing(0))))),
            patch().copy(children = listOf(children.copy(items = listOf(WinUIXamlHotReloadItem.Markup("bad markup"))))),
            patch().copy(children = listOf(children.copy(items = listOf(WinUIXamlHotReloadItem.Markup("second-root-failure"))))),
        )
        candidates.forEach { update ->
            var reply: WinUIXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
            assertEquals(2, owner.children.size); assertSame(first, owner.children[0]); assertSame(second, owner.children[1])
            assertEquals(2, other.children.size); assertSame(otherFirst, other.children[0]); assertSame(otherSecond, other.children[1])
            assertEquals(1, first.size); assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        }
        // A failed update must not become part of the future-instance history.
        val later = Page(mutableMapOf(), mutableListOf(Element("first"), Element("second")))
        registry.observe(later, "probe.Page", "Page.xaml", hash, null, null)
        assertEquals(listOf("first", "second"), later.children.map { it.text })
    }
}
