package io.github.composefluent.winrt.runtime

import kotlin.test.*

/** Generated typed access mirrors CsWinRT GeneratedCustomPropertyProvider; the
 * runtime owns dispatch and instance lifetime, never SDK-specific serialization. */
class WinUIXamlReloadStateTest {
    private class Control(var text: String)
    @Test fun state_roundtrips_with_bounded_unique_names() {
        val state = listOf(WinUIXamlSavedElement("Input", "TextBox", listOf(WinUIXamlVisualProperty("Text", "输入 🌍"))))
        val request = WinUIXamlStateRequest("Page", "Page.xaml", restore = state)
        val command = WinUIXamlHotReloadCodec.readCommand(WinUIXamlHotReloadCodec.writeStateRequest("token", request))
        assertEquals(request, command.state)
        assertNull(command.patch)
        val reply = WinUIXamlHotReloadReply(WinUIXamlHotReloadProtocol.APPLIED, "saved", savedState = state)
        assertEquals(reply, WinUIXamlHotReloadCodec.readReply(WinUIXamlHotReloadCodec.writeReply(reply)))
        assertFailsWith<IllegalArgumentException> { WinUIXamlHotReloadCodec.writeStateRequest("token", request.copy(restore = state + state)) }
        assertFailsWith<IllegalArgumentException> { validateWinUIXamlSavedState(listOf(state.single().copy(values = listOf(WinUIXamlVisualProperty("Text", "x".repeat(131073)))))) }
    }
    @Test fun restores_only_selected_instance_and_rolls_back_without_replay() {
        val queue = mutableListOf<() -> Unit>()
        val adapter = WinUIXamlStateAdapter({ value -> (value as? Control)?.let {
            WinUIXamlSavedElement("", "Control", listOf(WinUIXamlVisualProperty("Text", it.text)))
        } }, { value, state ->
            (value as Control).text = state.values.single().value
            check(value.text != "reject")
        })
        val registry = WinUIXamlHotReloadRegistry({ { task -> queue += task; true } }, { _, text -> text }, stateAdapter = adapter)
        val owner = Any(); val first = Control("first"); val second = Control("second")
        val hash = "a".repeat(64)
        registry.observe(owner, "Page", "Page.xaml", hash, "First", first)
        registry.observe(owner, "Page", "Page.xaml", hash, "Second", second)
        registry.observe(owner, "Page", "Page.xaml", hash, null, null)
        var reply: WinUIXamlHotReloadReply? = null
        fun transfer(state: List<WinUIXamlSavedElement>? = null) {
            registry.transferState(WinUIXamlStateRequest("Page", "Page.xaml", restore = state)) { reply = it }
            assertTrue(queue.isNotEmpty()); queue.removeAt(0).invoke()
        }
        transfer()
        val saved = assertNotNull(reply?.savedState)
        val invalid = saved.map { it.copy(values = listOf(WinUIXamlVisualProperty("Text", if (it.name == "Second") "reject" else "updated"))) }
        transfer(invalid)
        assertEquals(WinUIXamlHotReloadProtocol.REJECTED, reply?.status)
        assertEquals("first", first.text); assertEquals("second", second.text)
        first.text = "edited"; transfer(saved)
        assertEquals("first", first.text)
        val laterOwner = Any(); val later = Control("fresh")
        registry.observe(laterOwner, "Page", "Page.xaml", hash, "First", later)
        registry.observe(laterOwner, "Page", "Page.xaml", hash, null, null)
        assertEquals("fresh", later.text)
        assertEquals(hash, registry.snapshot().roots.single().sourceHash)
        transfer(saved.map { it.copy(kind = "ChangedType") })
        assertEquals("first", first.text)
    }
}
