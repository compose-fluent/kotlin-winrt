package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinRTXamlHotReloadTest {
    private class Element(var text: String = "original", var size: Int = 1, var style: Element? = null, var rejectStyle: Boolean = false)
    private class Page(val resources: MutableMap<Any?, Any?>, val children: MutableList<Element> = mutableListOf())
    private val hash = "a".repeat(64)
    private val next = "b".repeat(64)
    private fun registry(queue: MutableList<() -> Unit>, element: (String) -> Any = { Element(it) },
        load: (String) -> Map<Any?, Any?> = { emptyMap() }): WinRTXamlHotReloadRegistry {
        ComWrappersSupport.clearRegistriesForTests()
        // The source-generated ICustomProperty strategy is owned by .cswinrt's net5 projection;
        // these accessors stand in for CSharpTypeInfoPass2's generated property delegates.
        WinUiAuthoredTypeMetadata.registerPropertyAccessors(WinRTXamlTypeDefinition(Element::class, "probe.Element", "System.Object", isWinRTComponent = false,
            members = listOf(
                WinRTXamlMemberDefinition("Text", "String", String::class, { (it as Element).text }, { target, value -> (target as Element).text = value as String }),
                WinRTXamlMemberDefinition("Size", "Int32", Int::class, { (it as Element).size }, { target, value ->
                    require((value as Int) != 13) { "setter failed" }; (target as Element).size = value
                }),
                WinRTXamlMemberDefinition("Style", "probe.Element", Element::class, { (it as Element).style }, { target, value ->
                    val element = target as Element
                    element.style = value as Element
                    if (element.rejectStyle && value.text == "reloaded style") error("style setter failed after mutation")
                }),
            )))
        WinUiAuthoredTypeMetadata.registerPropertyAccessors(WinRTXamlTypeDefinition(Page::class, "probe.Page", "System.Object", isWinRTComponent = false,
            members = listOf(
                WinRTXamlMemberDefinition("Resources", "Map", MutableMap::class, { (it as Page).resources }),
                WinRTXamlMemberDefinition("Children", "List", MutableList::class, { (it as Page).children }),
            )))
        return WinRTXamlHotReloadRegistry({ { task -> queue += task; true } }, { type, text -> if (type == Int::class) text.toInt() else text }, load, element)
    }
    private fun patch(vararg changes: WinRTXamlHotReloadChange) = WinRTXamlHotReloadPatch("probe.Page", "Page.xaml", hash, next, 1, changes.toList())

    @Test fun updates_wait_for_the_ui_dispatcher_and_keep_identity_and_version() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
        assertEquals(PlatformAbi.nullPointer, WinUiAuthoredTypeMetadata.tryCreate("probe.Element") { PlatformAbi.nullPointer })
        registry.observe(owner, "probe.Page", "Page.xaml", hash, "Greeting", element)
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        var result: WinRTXamlHotReloadReply? = null
        val update = patch(WinRTXamlHotReloadChange("Greeting", "Text", "changed"), WinRTXamlHotReloadChange("Greeting", "Size", "8"))
        registry.submit(update) { result = it }
        assertEquals("original", element.text); assertNull(result)
        assertEquals(WinRTXamlHotReloadProtocol.UNAVAILABLE, registry.snapshot().status)
        queue.removeAt(0).invoke()
        assertEquals("changed", element.text); assertEquals(8, element.size)
        assertEquals(WinRTXamlHotReloadProtocol.APPLIED, result?.status)
        assertEquals(next, registry.snapshot().roots.single().sourceHash)
        registry.submit(update) { result = it }
        assertEquals(WinRTXamlHotReloadProtocol.RESTART_REQUIRED, result?.status)
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
            var result: WinRTXamlHotReloadReply? = null
            registry.submit(patch(WinRTXamlHotReloadChange("Greeting", "Text", "must roll back"), WinRTXamlHotReloadChange("Greeting", "Size", size))) { result = it }
            queue.removeAt(0).invoke()
            assertEquals(WinRTXamlHotReloadProtocol.REJECTED, result?.status)
            assertEquals("original", element.text); assertEquals(1, element.size)
            assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        }
    }

    @Test fun projected_paths_keep_shared_resource_identity_and_guard_live_collection_shape() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val brush = Element(); val owner = Page(mutableMapOf("Accent" to brush), mutableListOf(brush))
        registry.observe(owner, "probe.Page", "Page.xaml", hash, null, null)
        val path = listOf(WinRTXamlHotReloadStep.Property("Resources"), WinRTXamlHotReloadStep.Key("Accent"))
        var result: WinRTXamlHotReloadReply? = null
        registry.submit(patch(WinRTXamlHotReloadChange("", "Text", "blue", path))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinRTXamlHotReloadProtocol.APPLIED, result?.status)
        assertSame(brush, owner.resources["Accent"]); assertSame(brush, owner.children.single())
        assertEquals("blue", brush.text); assertEquals(path, result?.values?.single()?.path)
        val current = registry.snapshot().roots.single()
        registry.submit(WinRTXamlHotReloadPatch("probe.Page", "Page.xaml", current.sourceHash, "c".repeat(64), 2,
            listOf(WinRTXamlHotReloadChange("", "Text", "must not apply", listOf(
                WinRTXamlHotReloadStep.Property("Children"), WinRTXamlHotReloadStep.Index(0, 2)))))) { result = it }
        queue.removeAt(0).invoke()
        assertEquals(WinRTXamlHotReloadProtocol.REJECTED, result?.status)
        assertEquals("blue", brush.text); assertEquals(current, registry.snapshot().roots.single())
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
        val update = WinRTXamlHotReloadResources(WinRTXamlHotReloadTarget(path = listOf(WinRTXamlHotReloadStep.Property("Resources"))),
            "SDK dictionary input", listOf("TitleStyle"), listOf(
                WinRTXamlHotReloadResourceReference(WinRTXamlHotReloadTarget("First"), "Style", "TitleStyle"),
                WinRTXamlHotReloadResourceReference(WinRTXamlHotReloadTarget("Second"), "Style", "TitleStyle")))
        val patch = patch().copy(resources = listOf(update))
        var reply: WinRTXamlHotReloadReply? = null
        registry.submit(patch) { reply = it }; queue.removeAt(0).invoke()
        assertEquals(WinRTXamlHotReloadProtocol.REJECTED, reply?.status)
        assertSame(original, owner.resources["TitleStyle"]); assertSame(original, first.style); assertSame(original, second.style)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        second.rejectStyle = false
        val stylePath = listOf(WinRTXamlHotReloadStep.Property("Style"))
        registry.submit(patch.copy(reads = listOf(WinRTXamlHotReloadRead(WinRTXamlHotReloadTarget("First", stylePath), "Text")))) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinRTXamlHotReloadProtocol.APPLIED, reply?.status)
        assertEquals("reloaded style", first.style?.text)
        assertSame(owner.resources["TitleStyle"], first.style); assertSame(first.style, second.style)
        assertEquals("reloaded style", reply?.values?.single { it.path == stylePath && it.property == "Text" }?.value)
        registry.submit(patch(WinRTXamlHotReloadChange("", "Text", "latest resource", listOf(
            WinRTXamlHotReloadStep.Property("Resources"), WinRTXamlHotReloadStep.Key("TitleStyle"))))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2)) { reply = it }
        queue.removeAt(0).invoke()
        assertEquals(WinRTXamlHotReloadProtocol.APPLIED, reply?.status)
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
        fun send(update: WinRTXamlHotReloadPatch): WinRTXamlHotReloadReply {
            var reply: WinRTXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            return requireNotNull(reply).also { assertEquals(WinRTXamlHotReloadProtocol.APPLIED, it.status, it.message) }
        }
        send(patch(WinRTXamlHotReloadChange("First", "Text", "before graph")))
        val collection = WinRTXamlHotReloadTarget(path = listOf(WinRTXamlHotReloadStep.Property("Children")))
        val added = collection.copy(path = collection.path + WinRTXamlHotReloadStep.Index(1, 3))
        val updated = send(patch(WinRTXamlHotReloadChange("", "Text", "new child edited", added.path))
            .copy(expectedHash = next, sourceHash = "c".repeat(64), version = 2,
                children = listOf(WinRTXamlHotReloadChildren(collection, 2, listOf(
                    WinRTXamlHotReloadItem.Existing(1), WinRTXamlHotReloadItem.Markup("new child"), WinRTXamlHotReloadItem.Existing(0)))),
                reads = listOf(WinRTXamlHotReloadRead(added, "Text"))))
        assertSame(second, owner.children[0]); assertSame(first, owner.children[2])
        assertEquals("new child edited", updated.values.last().value)
        send(patch().copy(expectedHash = "c".repeat(64), sourceHash = "d".repeat(64), version = 3,
            children = listOf(WinRTXamlHotReloadChildren(collection, 3, listOf(
                WinRTXamlHotReloadItem.Existing(2), WinRTXamlHotReloadItem.Existing(1))))))
        send(patch(WinRTXamlHotReloadChange("First", "Text", "after graph"))
            .copy(expectedHash = "d".repeat(64), sourceHash = "e".repeat(64), version = 4))
        assertSame(first, owner.children[0]); assertEquals("after graph", first.text)
        val laterFirst = Element("first"); val later = Page(mutableMapOf(), mutableListOf(laterFirst, Element("second")))
        connect(later)
        assertEquals(WinRTXamlHotReloadProtocol.APPLIED, registry.snapshot().status)
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
        val collection = WinRTXamlHotReloadTarget(path = listOf(WinRTXamlHotReloadStep.Property("Children")))
        val children = WinRTXamlHotReloadChildren(collection, 2, listOf(
            WinRTXamlHotReloadItem.Existing(1), WinRTXamlHotReloadItem.Markup("added"), WinRTXamlHotReloadItem.Existing(0)))
        val candidates = listOf(
            patch(WinRTXamlHotReloadChange("First", "Size", "13")).copy(children = listOf(children)),
            patch().copy(children = listOf(children.copy(expectedSize = 3))),
            patch().copy(children = listOf(children.copy(items = listOf(WinRTXamlHotReloadItem.Existing(0), WinRTXamlHotReloadItem.Existing(0))))),
            patch().copy(children = listOf(children.copy(items = listOf(WinRTXamlHotReloadItem.Markup("bad markup"))))),
            patch().copy(children = listOf(children.copy(items = listOf(WinRTXamlHotReloadItem.Markup("second-root-failure"))))),
        )
        candidates.forEach { update ->
            var reply: WinRTXamlHotReloadReply? = null
            registry.submit(update) { reply = it }; queue.removeAt(0).invoke()
            assertEquals(WinRTXamlHotReloadProtocol.REJECTED, reply?.status)
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
