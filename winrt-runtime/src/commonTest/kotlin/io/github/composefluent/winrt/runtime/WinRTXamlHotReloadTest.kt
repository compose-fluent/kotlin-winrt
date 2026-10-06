package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinRTXamlHotReloadTest {
    private class Element(var text: String = "original", var size: Int = 1)
    private val hash = "a".repeat(64)
    private val next = "b".repeat(64)
    private fun registry(queue: MutableList<() -> Unit>): WinRTXamlHotReloadRegistry {
        ComWrappersSupport.clearRegistriesForTests()
        // The source-generated ICustomProperty strategy is owned by .cswinrt's net5 projection;
        // these accessors stand in for CSharpTypeInfoPass2's generated property delegates.
        registerWinRTXamlTypeDefinition(WinRTXamlTypeDefinition(Element::class, "probe.Element", "System.Object", isWinRTComponent = false,
            members = listOf(
                WinRTXamlMemberDefinition("Text", "String", String::class, { (it as Element).text }, { target, value -> (target as Element).text = value as String }),
                WinRTXamlMemberDefinition("Size", "Int32", Int::class, { (it as Element).size }, { target, value ->
                    require((value as Int) != 13) { "setter failed" }; (target as Element).size = value
                }),
            )))
        return WinRTXamlHotReloadRegistry({ { task -> queue += task; true } }, { type, text -> if (type == Int::class) text.toInt() else text })
    }
    private fun patch(vararg changes: WinRTXamlHotReloadChange) = WinRTXamlHotReloadPatch("probe.Page", "Page.xaml", hash, next, 1, changes.toList())

    @Test fun updates_wait_for_the_ui_dispatcher_and_keep_identity_and_version() {
        val queue = mutableListOf<() -> Unit>(); val registry = registry(queue)
        val owner = Any(); val element = Element()
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
}
